<a id="back-to-top"></a>

# Dữ liệu nhị phân

## Menu
- [DataInput, DataOutput và định dạng dữ liệu nguyên thủy](#binary-data-001)
- [DataInputStream và DataOutputStream](#binary-data-002)
- [Thứ tự ghi phải tương ứng với thứ tự đọc](#binary-data-003)
- [Big-endian, signed byte và các thao tác đọc unsigned](#binary-data-004)
- [readFully khác read và cách xử lý EOFException](#binary-data-005)
- [readUTF, writeUTF và modified UTF-8](#binary-data-006)
- [Giới hạn độ dài của writeUTF](#binary-data-007)
- [Thiết kế bản ghi có magic number, version và length](#binary-data-008)
- [Kiểm tra độ dài trước khi cấp phát hoặc đọc dữ liệu](#binary-data-009)
- [Phân biệt định dạng nhị phân tự thiết kế với Java serialization](#binary-data-010)

## <a id="binary-data-001">DataInput, DataOutput và định dạng dữ liệu nguyên thủy</a>

<details>
<summary>Xem chi tiết</summary>

# DataInput, DataOutput và định dạng dữ liệu nguyên thủy trong Java I/O

## 1. Từ giá trị Java đến dữ liệu nhị phân

Một file hoặc kết nối mạng chỉ truyền **byte**. Khi cần lưu `int`, `long`, `boolean`, `float`, `double`, `char` hay chuỗi ký tự, chương trình phải quy định **bao nhiêu byte, theo thứ tự nào và cách diễn giải ra sao**.

Ví dụ cùng một giá trị 300:

- Ghi như **văn bản UTF-8** `"300"`: các byte hex `33 30 30`, dài 3 byte, phía đọc phải parse chuỗi số.
- Ghi như **nhị phân bằng `writeInt(300)`**: các byte hex `00 00 01 2C`, dài 4 byte; `readInt()` tái tạo số 300 theo quy tắc Java.

**`java.io.DataOutput`** là interface quy định cách viết các giá trị Java kiểu nguyên thủy và một dạng chuỗi vào luồng byte. **`java.io.DataInput`** là interface quy định cách đọc theo cùng hợp đồng byte.

Hai lớp triển khai điển hình là **`DataOutputStream`**, bọc một `OutputStream`, và **`DataInputStream`**, bọc một `InputStream`. Chúng chuyển giữa giá trị Java và byte có cấu trúc; chúng không tự biết tên trường, schema bản ghi, số lượng bản ghi hoặc phiên bản file.

~~~text
Bên ghi                                   Bên đọc
int, double, boolean, String             byte trong file/network
           |                                      |
           v                                      v
     DataOutputStream                        DataInputStream
           |                                      |
           +---- byte layout phải khớp ----------+
~~~

**Nguyên tắc cốt lõi:** phía đọc phải biết đúng **thứ tự và kiểu** đã được phía ghi sử dụng. Nếu ghi `int` rồi `double` mà đọc `short` rồi `double`, dữ liệu sẽ lệch ranh giới byte; lỗi đôi khi âm thầm tạo giá trị sai trước khi xảy ra EOF.

## 2. Kích thước và thứ tự byte của primitive

Theo hợp đồng `DataInput`/`DataOutput` của Java:

| Ghi / đọc | Byte | Diễn giải |
| --- | ---: | --- |
| `writeBoolean` / `readBoolean` | 1 | 0 = false, khác 0 = true khi đọc; writer ghi 0 hoặc 1 |
| `writeByte` / `readByte` | 1 | Đọc dạng byte có dấu (-128 đến 127) |
| `writeByte` / `readUnsignedByte` | 1 | Đọc cùng 8 bit dưới dạng int 0–255 |
| `writeShort` / `readShort` | 2 | Số signed 16 bit |
| `writeShort` / `readUnsignedShort` | 2 | Cùng 16 bit, trả int 0–65535 |
| `writeChar` / `readChar` | 2 | Một đơn vị mã UTF-16 của Java |
| `writeInt` / `readInt` | 4 | Số signed 32 bit |
| `writeLong` / `readLong` | 8 | Số signed 64 bit |
| `writeFloat` / `readFloat` | 4 | Bit float qua Float.floatToIntBits |
| `writeDouble` / `readDouble` | 8 | Bit double qua Double.doubleToLongBits |
| `writeUTF` / `readUTF` | 2 + N | Độ dài unsigned 16 bit + N byte **modified UTF-8** |

Với những kiểu nhiều byte, **byte cao (most significant byte) đi trước**: đây là **big-endian**, được định nghĩa bởi DataInput/DataOutput và **không phụ thuộc CPU** đang chạy big-endian hay little-endian.

~~~text
writeInt(0x01020304) -> 01 02 03 04
writeShort(300)      -> 01 2C
writeChar('A')       -> 00 41
writeLong(1)         -> 00 00 00 00 00 00 00 01
~~~

Không nhầm với little-endian: `0x01020304` ở little-endian thường là `04 03 02 01`. Nếu giao thức ngoài Java yêu cầu little-endian, có thể dùng **`ByteBuffer.order(ByteOrder.LITTLE_ENDIAN)`** hoặc parser thích hợp; **`DataInputStream.readInt()` không có tùy chọn little-endian**.

Đối số dạng `int` truyền vào `writeByte`, `writeShort`, `writeChar` bị lấy **8 hoặc 16 bit thấp** tương ứng. Không được coi `writeByte(300)` là cách ghi nguyên số 300. Muốn giữ đúng giá trị phải chọn kích thước và signed/unsigned của trường trước khi ghi.

## 3. Ví dụ Java 17+: ghi rồi đọc một bản ghi binary

Tạo file **PrimitiveBinaryDemo.java** (lưu mã nguồn bằng UTF-8):

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HexFormat;

public class PrimitiveBinaryDemo {
    public static void main(String[] args) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeInt(0x01020304);
            out.writeShort(300);
            out.writeBoolean(true);
            out.writeDouble(1.5);
            out.writeUTF("Hi");
        }

        byte[] data = buffer.toByteArray();
        System.out.println("bytes=" + data.length);
        System.out.println("prefix="
                + HexFormat.of().formatHex(Arrays.copyOf(data, 7)));

        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(data))) {
            System.out.printf("id=%08X%n", in.readInt());
            System.out.println("count=" + in.readUnsignedShort());
            System.out.println("enabled=" + in.readBoolean());
            System.out.println("ratio=" + in.readDouble());
            System.out.println("label=" + in.readUTF());
            System.out.println("eof=" + in.read());
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 PrimitiveBinaryDemo.java
java PrimitiveBinaryDemo
~~~

Kết quả:

~~~text
bytes=19
prefix=01020304012c01
id=01020304
count=300
enabled=true
ratio=1.5
label=Hi
eof=-1
~~~

Giải thích bố cục 19 byte:

| Trường | Byte (hex) | Dài |
| --- | --- | ---: |
| id, `writeInt` | 01 02 03 04 | 4 |
| count, `writeShort` | 01 2C | 2 |
| enabled, `writeBoolean` | 01 | 1 |
| ratio, `writeDouble(1.5)` | 3F F8 00 00 00 00 00 00 | 8 |
| label, `writeUTF("Hi")` | 00 02 48 69 | 4 |
| **Tổng** | | **19** |

**`HexFormat` có sẵn từ Java 17**. `Arrays.copyOf(data, 7)` chỉ lấy bảy byte đầu để thấy byte của int, short và boolean. `readUnsignedShort()` phục hồi count=300, còn `in.read()` sau cùng trả `-1` vì toàn bộ bản ghi đã được tiêu thụ.

Dùng **try-with-resources** để đóng wrapper. Trong ví dụ, ByteArrayOutputStream giữ dữ liệu trong bộ nhớ, không cần file vật lý; cùng logic có thể bọc Files.newOutputStream/Files.newInputStream để ghi/đọc file.

## 4. Vì sao DataInput/DataOutput chưa phải một file format hoàn chỉnh?

`writeInt` chỉ ghi bốn byte. Không có tên trường `id`, không có số thứ tự field, không tự lưu version, checksum hoặc dấu tách record. Vì thế hai chương trình có thể đọc cùng dãy byte ra kết quả khác nhau nếu schema của chúng khác nhau.

Giả sử một format quy định:

~~~text
record = [id:int, 4 bytes][score:double, 8 bytes]
record = [id:int, 4 bytes][score:double, 8 bytes]
~~~

Mỗi record dài 12 byte. Nếu file có hai record, tổng dữ liệu record là 24 byte. Reader phải biết có hai record, hoặc có header `count`, hoặc một quy tắc kết thúc đã được định nghĩa.

Một format nhị phân ổn định nên có sơ đồ rõ ràng:

~~~text
+---------------+---------+--------------+------------------+
| MAGIC (4 byte)| VERSION | RECORD_COUNT | RECORDS ...      |
+---------------+---------+--------------+------------------+
~~~

- **Magic** xác định loại file, giúp từ chối dữ liệu không cùng định dạng.
- **Version** cho reader biết layout được dùng.
- **Record count** hoặc **length prefix** cho phép xác định ranh giới dữ liệu, không phải đoán bằng dấu EOF tùy tiện.
- **Schema cho từng field** mô tả kích thước, endian, dấu, charset chuỗi, thứ tự và phạm vi hợp lệ.

Nếu phiên bản 1 có `int id` rồi `double score`, còn phiên bản 2 chèn thêm `boolean active` ở giữa, reader v1 sẽ đọc lệch byte của score. Tạo version mới và parser tương thích là giải pháp đúng; không coi việc chèn thêm một byte là tương thích tự động.

**DataOutputStream không phải ObjectOutputStream.** Nó không tự ghi đồ thị object hay metadata class; ứng dụng chủ động chuyển object thành các trường primitive theo schema riêng. Java Object Serialization là cơ chế khác, có định dạng và yêu cầu an toàn riêng.

## 5. writeUTF/readUTF: modified UTF-8 với tiền tố độ dài

Đây là điểm rất quan trọng khi làm việc giữa các ngôn ngữ.

**`DataOutput.writeUTF(String)` không ghi UTF-8 chuẩn thuần túy**. Nó ghi:

1. Hai byte big-endian chứa **độ dài payload tính bằng byte**, trong khoảng 0–65535.
2. N byte mã hóa theo **modified UTF-8** của Java.

**`DataInput.readUTF()`** đọc đúng cấu trúc này: đọc length unsigned 16 bit, sau đó giải mã N byte theo modified UTF-8.

Khác biệt với UTF-8 chuẩn:

- Ký tự NUL `U+0000` ghi thành `C0 80` trong modified UTF-8, thay vì byte `00` theo UTF-8 chuẩn.
- Code point ngoài BMP (ví dụ một số emoji) được thể hiện bằng hai đơn vị UTF-16 surrogate và mỗi đơn vị được mã hóa riêng, thường tổng cộng 6 byte, trong khi UTF-8 chuẩn mã hóa trực tiếp code point đó thành 4 byte.
- Giá trị length là **số byte modified UTF-8 thực tế**, không phải số ký tự String hay `String.length()`.
- Nếu payload modified UTF-8 cần hơn **65.535 byte**, `writeUTF` ném `UTFDataFormatException`.

Các ví dụ byte:

~~~text
writeUTF("Hi")    -> 00 02 48 69
writeUTF("")      -> 00 00
writeUTF(U+0000)  -> 00 02 C0 80
~~~

Điều đó có nghĩa một file TXT UTF-8 thông thường **không thể đọc bằng `readUTF`** nếu file không hề có length prefix và modified UTF-8 theo quy ước trên. Hai byte đầu file sẽ bị hiểu nhầm thành length, gây lỗi, đợi thêm dữ liệu hoặc cho kết quả sai.

Đối với giao thức ngoài Java yêu cầu **UTF-8 chuẩn**, nên viết byte UTF-8 bằng `String.getBytes(StandardCharsets.UTF_8)`, đồng thời định nghĩa **length prefix hoặc delimiter** theo hợp đồng riêng.

## 6. Ví dụ Java 17+: chuỗi UTF-8 chuẩn có chiều dài 4 byte

Tạo file **LengthPrefixedUtf8Demo.java**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public class LengthPrefixedUtf8Demo {
    public static void main(String[] args) throws IOException {
        String original = "café";
        byte[] payload = original.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeInt(payload.length);
            out.write(payload);
        }

        byte[] wire = buffer.toByteArray();
        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(wire))) {
            int length = in.readInt();
            if (length < 0 || length > 1_000_000) {
                throw new IOException("Invalid length: " + length);
            }

            byte[] content = new byte[length];
            in.readFully(content);

            String restored = new String(
                    content, StandardCharsets.UTF_8);
            System.out.println("length=" + length);
            System.out.println("hex=" + HexFormat.of().formatHex(content));
            System.out.println("text=" + restored);
            System.out.println("wireBytes=" + wire.length);
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac -encoding UTF-8 LengthPrefixedUtf8Demo.java
java LengthPrefixedUtf8Demo
~~~

Stdout:

~~~text
length=5
hex=636166c3a9
text=café
wireBytes=9
~~~

Bố cục trên wire:

~~~text
00 00 00 05 | 63 61 66 C3 A9
  4 byte    |    5 byte UTF-8 chuẩn
 length=5   |    "café"
~~~

Bên đọc biết bốn byte đầu là length kiểu int big-endian, rồi cần đọc **chính xác length byte**. Vì hai phía cùng thỏa thuận layout và UTF-8 chuẩn, một chương trình Python/Go/C khác cũng có thể đọc nếu triển khai đúng đặc tả; không cần gọi Java `readUTF`.

**`readFully(content)` khác `read(content)`**: `readFully` đọc đủ số byte yêu cầu hoặc ném `EOFException` nếu luồng kết thúc trước; `read(byte[])` có thể đọc ít byte hơn mà không phải lỗi. Trong một frame có độ dài định trước, `readFully` giúp phát hiện payload bị cắt cụt.

Trong ví dụ, ngưỡng `1_000_000` chỉ là **giới hạn của ứng dụng minh họa**, không phải chuẩn chung của Java. Nếu nhận length/count từ đầu vào không đáng tin, cần giới hạn trước khi cấp phát mảng, tính toán dung lượng, hoặc lặp đọc nhiều record.

## 7. Signed/unsigned và sự khác nhau giữa dữ liệu FF với EOF

Java `byte` là kiểu **signed**. Giá trị hex `FF` biểu diễn 8 bit toàn 1:

- `DataInput.readByte()` trả `byte -1`.
- `DataInput.readUnsignedByte()` trả `int 255`.
- `InputStream.read()` đọc byte thô và trả `int 255`, còn **`-1` riêng biệt** để báo đã EOF.

Do đó không được đánh đồng `readByte() == -1` với EOF: **-1 có thể là giá trị byte hợp lệ** khi đọc có dấu. Khi thiếu byte, `readByte()` ném **EOFException**.

Tương tự, `readShort()` trả số signed trong khoảng -32768..32767, còn `readUnsignedShort()` trả int từ 0..65535. Writer dùng `writeShort` ghi 16 bit; reader chọn signed/unsigned theo hợp đồng trường.

Một chi tiết với boolean: `writeBoolean(true)` ghi byte `01`; `writeBoolean(false)` ghi `00`. `readBoolean()` chấp nhận **bất cứ byte khác 0 là true**. Nếu giao thức của ứng dụng chỉ cho phép 0 hoặc 1, reader cần tự kiểm tra byte để từ chối giá trị 2, 3... thay vì dựa duy nhất vào readBoolean.

## 8. EOF và file bị cắt cụt

Có khác biệt giữa **đọc một byte đến hết** và **đọc một trường cố định nhưng không đủ byte**:

- `InputStream.read()` trả **-1** nếu ở EOF.
- `DataInputStream.readInt()` cần đủ 4 byte. Nếu chỉ có 2 byte trước EOF, nó ném **EOFException**, không tự bù 0.
- `DataInputStream.readDouble()` cần 8 byte; nếu thiếu cũng ném EOFException.
- `DataInputStream.readFully(byte[])` yêu cầu đúng chiều dài và ném EOFException nếu thiếu.
- `DataInputStream.readUTF()` cần đủ length prefix cùng payload đúng encoding; có thể ném EOFException hoặc UTFDataFormatException khi lỗi.

**Không được coi mọi EOFException là kết thúc thành công.** Nếu giao thức yêu cầu 10 record nhưng record thứ 9 mới được một nửa thì EOFException là bằng chứng bị cắt cụt, cần báo lỗi. Nếu giao thức chủ động dùng EOF để kết thúc danh sách các bản ghi hoàn chỉnh, phải có logic phân biệt EOF tại **ranh giới bản ghi** với EOF giữa bản ghi.

Đặc biệt, **không viết vòng lặp `while (in.readInt() != -1)`** để tìm EOF. Giá trị số nguyên -1 hoàn toàn hợp lệ và có thể nằm trong dữ liệu; hơn nữa `readInt()` báo thiếu byte bằng exception. Dùng count, length-prefixed records hoặc một marker được định nghĩa rõ và không nhập nhằng.

**`available()` không phải số byte còn lại và không phải kiểm tra EOF** cho mọi nguồn: với network/pipe, nó thường chỉ ước lượng số byte có thể đọc ngay mà không chặn. `available() == 0` không chứng minh rằng luồng đã kết thúc.

## 9. Buffering, flush và đóng luồng

Có thể ghép buffer với data stream:

~~~java
// Mảnh code trong phương thức đã có Path path,
// cùng import Java và xử lý IOException thích hợp.
try (DataOutputStream out = new DataOutputStream(
        new java.io.BufferedOutputStream(
                java.nio.file.Files.newOutputStream(path)))) {
    out.writeInt(123);
    out.writeDouble(4.5);
}
~~~

Ở chiều đọc:

~~~java
try (DataInputStream in = new DataInputStream(
        new java.io.BufferedInputStream(
                java.nio.file.Files.newInputStream(path)))) {
    int id = in.readInt();
    double score = in.readDouble();
    // Xử lý hai trường theo schema của file.
}
~~~

`BufferedOutputStream` gom dữ liệu để giảm thao tác I/O. Nó **không đổi byte order** hay format của DataOutputStream. Đóng qua try-with-resources sẽ đóng wrapper và tầng stream bên dưới, đồng thời đẩy dữ liệu còn đệm nếu quá trình đóng thành công. Khi giao thức cần bên nhận đọc dữ liệu **trong lúc stream vẫn mở**, gọi `flush()` đúng điểm thích hợp; flush không đồng nghĩa đồng bộ bền vững dữ liệu xuống đĩa.

Không đọc và ghi đồng thời trên cùng một file bằng nhiều wrapper thiếu phối hợp rồi mong thứ tự byte ổn định. Không dùng Reader/Writer ở giữa dữ liệu binary vì bước decode/encode ký tự có thể biến đổi byte.

## 10. So sánh với các API khác trong Java I/O

| API | Dùng cho | Lưu ý |
| --- | --- | --- |
| `InputStream`/`OutputStream` | Byte thô | Không biết field kiểu int/double |
| `DataInput`/`DataOutput` | Hợp đồng binary cho các primitive | Không tự quản schema toàn file |
| `DataInputStream`/`DataOutputStream` | Thực thi DataInput/DataOutput bằng stream | Ghi/đọc theo thứ tự xác định |
| `BufferedInputStream`/`BufferedOutputStream` | Giảm lần gọi I/O | Chỉ tối ưu buffering, không đổi format |
| `Reader`/`Writer` | Ký tự/charset | Không dùng để bảo toàn byte binary tùy ý |
| `ObjectInputStream`/`ObjectOutputStream` | Java object serialization | Có định dạng riêng, không tương đương primitive record |
| `ByteBuffer` | Buffer byte và primitive có tùy chọn endian | Dùng khi format ngoài Java yêu cầu little-endian |

**DataOutputStream không tự làm file “portable” theo nghĩa mọi ứng dụng đều đọc được.** Nó cung cấp layout độc lập máy cho từng phép ghi. Muốn tương tác giữa nhiều ngôn ngữ, phải chia sẻ chính xác **bản đặc tả cấu trúc**: magic, version, số byte cho mỗi trường, byte order, signed/unsigned, cách encode chuỗi, cách biểu diễn null, length, checksum nếu có.

## 11. Các lỗi phổ biến và hướng tránh

| Sai lầm | Hậu quả | Cách làm đúng |
| --- | --- | --- |
| Ghi int, đọc short | Lệch vị trí những trường sau | Ghép cặp ghi/đọc theo schema |
| Nghĩ DataInput tự phát hiện endian | Đọc sai file little-endian | Chọn parser/ByteBuffer đúng byte order |
| Gọi readUTF cho file TXT UTF-8 | Hai byte đầu bị hiểu thành độ dài | Chỉ dùng cho payload writeUTF |
| Nhầm modified UTF-8 với UTF-8 chuẩn | Sai ký tự NUL/emoji, sai giao thức | Định nghĩa charset và framing rõ |
| Tưởng String.length() bằng số byte | Length prefix sai cho ký tự đa byte | Dùng encodedBytes.length |
| Vượt giới hạn writeUTF | UTFDataFormatException | Dùng length prefix/payload khác |
| Chỉ gọi read(byte[]) một lần | Có thể thiếu payload | Dùng readFully hoặc lặp đến đủ |
| Bỏ qua EOFException khi đọc giữa record | Chấp nhận file bị cắt cụt | Xử lý truncated như lỗi |
| Tin count/length tùy ý | Cấp phát hoặc xử lý quá lớn | Kiểm tra giới hạn trước |
| Dùng available() đo kích thước toàn stream | Dừng quá sớm hoặc sai EOF | Dựa vào count, length, read/EOF |
| Không version khi đổi schema | File cũ/mới không tương thích | Ghi version và parse có nhánh |
| Không đóng/flush bộ đệm | Thiếu dữ liệu hoặc rò tài nguyên | try-with-resources, flush khi cần |

## 12. Câu hỏi tự kiểm tra

**Câu 1.** Cùng số 300, tại sao writeInt ghi 4 byte còn text UTF-8 “300” ghi 3 byte?  
**Trả lời:** writeInt ghi biểu diễn 32 bit của số nguyên; text ghi ký tự chữ số và phải parse mới ra int.

**Câu 2.** DataInputStream.readInt dùng big-endian hay little-endian?  
**Trả lời:** Big-endian theo hợp đồng Java, độc lập với kiến trúc CPU.

**Câu 3.** writeUTF("Hi") ghi mấy byte?  
**Trả lời:** Bốn byte: 00 02 là độ dài payload, 48 69 là nội dung modified UTF-8 của hai ký tự ASCII.

**Câu 4.** Có thể đọc một file TXT UTF-8 bất kỳ bằng readUTF không?  
**Trả lời:** Không. readUTF đòi hỏi tiền tố độ dài hai byte và modified UTF-8.

**Câu 5.** writeUTF giới hạn 65.535 ký tự hay 65.535 byte?  
**Trả lời:** 65.535 **byte mã hóa modified UTF-8**.

**Câu 6.** Nếu dữ liệu định dạng int + double chỉ còn 6 byte, readDouble sau readInt sẽ làm gì?  
**Trả lời:** Chỉ còn 2 byte mà double cần 8; readDouble ném EOFException.

**Câu 7.** Vì sao cần readFully với length-prefixed record?  
**Trả lời:** Nó đọc đủ số byte đã khai báo hoặc báo EOFException; read(byte[]) có thể trả một phần mà không báo lỗi.

**Câu 8.** Một byte FF trả bao nhiêu khi gọi readByte và readUnsignedByte?  
**Trả lời:** Lần lượt là -1 và 255, nếu hai lần đọc được đặt trước một byte FF tương ứng.

**Câu 9.** Tại sao đổi layout từ int+double sang int+boolean+double cần version?  
**Trả lời:** Trường boolean chèn thêm một byte, khiến phía đọc cũ giải mã các byte của double sai vị trí.

## 13. Tài liệu API Java SE 17

- [DataInput](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataInput.html): primitive, signed/unsigned, big-endian, readFully, modified UTF-8.
- [DataOutput](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataOutput.html): layout byte của phương thức ghi.
- [DataInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataInputStream.html): đọc primitive từ InputStream.
- [DataOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataOutputStream.html): ghi primitive vào OutputStream.
- [ByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html): primitive trong byte buffer, có tùy chọn byte order.

**Ghi nhớ:** DataInput/DataOutput giải quyết **cách mã hóa và giải mã mỗi giá trị nguyên thủy thành byte**. Để tạo một định dạng dữ liệu có thể đọc lại và trao đổi lâu dài, vẫn phải quy định **thứ tự trường, kiểu, độ dài, byte order, charset, version và cách xử lý file bị cắt cụt**. Đặc biệt, writeUTF/readUTF dùng **modified UTF-8 với tiền tố 2 byte**, khác với UTF-8 chuẩn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="binary-data-002">DataInputStream và DataOutputStream</a>

<details>
<summary>Xem chi tiết</summary>

# DataInputStream và DataOutputStream: đọc, ghi dữ liệu nhị phân có kiểu trong Java

Khi một chương trình cần ghi nhiều giá trị như mã phiên bản, số lượng, cờ trạng thái hoặc số thực vào một tệp nhị phân, việc tự biến đổi từng giá trị thành byte rất dễ sai. Java cung cấp hai lớp **`DataOutputStream`** và **`DataInputStream`** để ghi/đọc các kiểu dữ liệu nguyên thủy theo một **định dạng nhị phân xác định**.

Hai lớp này hoạt động trên các **byte stream**, thường bọc một `OutputStream` hoặc `InputStream` có sẵn. Chúng hữu ích khi hai phía đã thống nhất **thứ tự trường, kiểu trường và cách biểu diễn byte**. Chúng **không tự biết cấu trúc bản ghi** và không tự tái tạo một đối tượng Java tùy ý.

## 1. Hai lớp làm nhiệm vụ gì?

- **`java.io.DataOutputStream`**: nhận giá trị Java như `int`, `long`, `boolean`, `double`, `char` và một số dạng chuỗi, sau đó ghi chúng xuống một `OutputStream` dưới dạng byte.
- **`java.io.DataInputStream`**: lấy byte từ `InputStream` và giải mã theo các phương thức `readInt()`, `readLong()`, `readBoolean()`, `readDouble()`, `readChar()`, `readUTF()`...
- **`DataOutput` và `DataInput`**: hai giao diện mà các lớp này triển khai, quy định hợp đồng cho việc ghi/đọc dữ liệu có kiểu.

Về quan hệ lớp:

~~~text
OutputStream
  └── FilterOutputStream
       └── DataOutputStream    (implements DataOutput)

InputStream
  └── FilterInputStream
       └── DataInputStream     (implements DataInput)
~~~

Một pipeline điển hình:

~~~text
                   GHI
giá trị Java --DataOutputStream--> BufferedOutputStream --> FileOutputStream --> file.bin
                   ĐỌC
giá trị Java <--DataInputStream--- BufferedInputStream <-- FileInputStream <-- file.bin
~~~

**Điểm quyết định:** `DataOutputStream` không thêm metadata tự mô tả kiểu dữ liệu. Nếu ghi lần lượt `int`, `boolean`, `long`, thì khi đọc phải biết để gọi `readInt()`, `readBoolean()`, `readLong()` **đúng thứ tự**. Đọc bằng kiểu hoặc thứ tự khác có thể sinh giá trị sai hoặc gặp hết dữ liệu.

## 2. Định dạng nhị phân của các kiểu nguyên thủy

Các phương thức `DataOutput` và `DataInput` có quy tắc biểu diễn cụ thể, không tùy tiện phụ thuộc vào charset hay byte order của CPU. Với các số có nhiều byte, dữ liệu được ghi theo thứ tự **big-endian** (byte có ý nghĩa lớn nhất trước).

| Ghi | Đọc đối ứng | Số byte | Ghi chú |
|---|---|---:|---|
| `writeBoolean(v)` | `readBoolean()` | 1 | `false` ghi byte 0; `true` ghi byte 1; đọc giá trị khác 0 là `true` |
| `writeByte(v)` | `readByte()` / `readUnsignedByte()` | 1 | Ghi 8 bit thấp; đọc có dấu hoặc không dấu |
| `writeShort(v)` | `readShort()` / `readUnsignedShort()` | 2 | Số nguyên 16 bit, big-endian |
| `writeChar(v)` | `readChar()` | 2 | Một **UTF-16 code unit** 16 bit, không phải chuỗi UTF-8 |
| `writeInt(v)` | `readInt()` | 4 | Số nguyên 32 bit, big-endian |
| `writeLong(v)` | `readLong()` | 8 | Số nguyên 64 bit, big-endian |
| `writeFloat(v)` | `readFloat()` | 4 | Theo các bit IEEE 754 được Java quy định |
| `writeDouble(v)` | `readDouble()` | 8 | Theo các bit IEEE 754 được Java quy định |
| `writeUTF(text)` | `readUTF()` | 2 + N | 2 byte độ dài không dấu, sau đó N byte **modified UTF-8** |

Ví dụ:

~~~text
writeInt(0x01020304)  -> 01 02 03 04
writeShort(300)       -> 01 2C
writeBoolean(true)    -> 01
~~~

Các byte hiển thị dưới dạng **hexadecimal (hệ 16)**. Ghi số `0x01020304` bằng `writeInt` không tạo bốn ký tự ASCII `"0102"`; nó tạo đúng **bốn byte** `01 02 03 04`. Nếu dùng `new String(bytes, UTF_8)` để cố xem dữ liệu nhị phân như văn bản, kết quả không phản ánh được cấu trúc của các trường.

Đối với một `int` âm, `writeInt` ghi biểu diễn 32 bit (two's complement) theo big-endian. `readInt` khôi phục lại cùng giá trị `int` nếu bốn byte gốc đầy đủ và chưa bị sửa.

### Đọc có dấu và không dấu

`writeByte(255)` ghi đúng byte `FF`, nhưng `readByte()` trả về `-1` vì diễn giải nó như số 8 bit có dấu. Nếu đặc tả bản ghi quy định đây là giá trị từ 0 đến 255, hãy dùng `readUnsignedByte()` để nhận `255`. Tương tự, `readUnsignedShort()` trả giá trị từ 0 đến 65535. Không được tự thay đổi phương thức đọc chỉ dựa trên “giá trị trông lạ”; cần tuân thủ **schema của dữ liệu**.

### `writeChar` không phải “ghi ký tự UTF-8”

`writeChar('A')` ghi hai byte `00 41`, trong khi mã hóa chuỗi `"A"` bằng UTF-8 cho một byte `41`. Một ký tự Unicode ngoài BMP được biểu diễn bằng **cặp surrogate** trong Java `String`, nên không thể giả định “một ký tự người dùng nhìn thấy = một lần `writeChar`”. Khi trao đổi văn bản nói chung, hãy xác định rõ encoding và quy tắc ghi độ dài thay vì dùng `writeChar` như một format chuỗi chung.

## 3. Quy tắc ghép stream và đóng tài nguyên

Thông thường code tự mở file bằng `Files.newOutputStream(path)`, rồi bọc `BufferedOutputStream` để giảm thao tác ghi xuống tầng dưới, và cuối cùng dùng `DataOutputStream` để viết giá trị có kiểu:

~~~java
try (DataOutputStream out = new DataOutputStream(
        new BufferedOutputStream(Files.newOutputStream(path)))) {
    out.writeInt(42);
    out.writeBoolean(true);
}
~~~

Đây là **đoạn minh họa**, cần import `java.io.*` và `java.nio.file.*`, có `Path path` và xử lý/khai báo `IOException`. Nếu code tự mở stream nguồn/đích, nó **sở hữu** tài nguyên và dùng `try-with-resources` cho lớp bọc ngoài cùng là thích hợp. `close()` ở decorator ngoài cùng đóng các tầng bên trong theo hợp đồng lớp JDK thông thường; `BufferedOutputStream` đẩy phần dữ liệu còn đệm xuống tầng sau khi đóng.

Đọc lại:

~~~java
try (DataInputStream in = new DataInputStream(
        new BufferedInputStream(Files.newInputStream(path)))) {
    int count = in.readInt();
    boolean enabled = in.readBoolean();
}
~~~

Cách đọc khớp **hai trường** đã ghi. Tuy nhiên, một helper chỉ **mượn** `InputStream` hoặc `OutputStream` từ caller không nên tùy ý đóng wrapper của nó, vì đóng wrapper có thể đóng luôn tài nguyên caller sở hữu. Hợp đồng API phải quy định rõ ai đóng và khi nào.

**Chú ý:** `DataOutputStream.flush()` chuyển yêu cầu flush xuống output bên trong; nhưng flush không có nghĩa dữ liệu đã được bảo đảm lưu bền vững trên ổ đĩa. Không gọi `close` chỉ để giải quyết nhu cầu flush trên stream được mượn.

## 4. Ví dụ Java 17 hoàn chỉnh: ghi và đọc cùng định dạng

Ví dụ sau dùng `ByteArrayOutputStream` và `ByteArrayInputStream` để dễ chạy độc lập, nhưng kiến trúc wrapper giống khi ghi/đọc tệp. Bản ghi gồm:

1. Một trường `int` **magic**: `0x01020304`, chiếm 4 byte.
2. Một trường `short` với giá trị `300`, chiếm 2 byte.
3. Một trường `boolean` là `true`, chiếm 1 byte.
4. Một trường `String` bằng `writeUTF`, có 2 byte tiền tố độ dài và phần văn bản theo **modified UTF-8**.

Lưu thành **`BinaryDataStreamsDemo.java`**:

~~~java
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class BinaryDataStreamsDemo {
    public static void main(String[] args) throws IOException {
        ByteArrayOutputStream storage = new ByteArrayOutputStream();
        String message = "A" + '\0' + "é";

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(storage))) {
            out.writeInt(0x01020304);
            out.writeShort(300);
            out.writeBoolean(true);
            out.writeUTF(message);
        }

        byte[] encoded = storage.toByteArray();
        System.out.println("Total bytes: " + encoded.length);

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new ByteArrayInputStream(encoded)))) {
            int magic = in.readInt();
            int count = in.readUnsignedShort();
            boolean enabled = in.readBoolean();
            String decoded = in.readUTF();

            System.out.printf("Magic: 0x%08X%n", magic);
            System.out.println("Count: " + count);
            System.out.println("Enabled: " + enabled);
            System.out.println("Text equal: " + message.equals(decoded));
            System.out.println("Text length: " + decoded.length());
            System.out.println("Next byte: " + in.read());
        }
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp mã nguồn:

~~~bash
javac --release 17 -encoding UTF-8 BinaryDataStreamsDemo.java
java BinaryDataStreamsDemo
~~~

Kết quả chuẩn:

~~~text
Total bytes: 14
Magic: 0x01020304
Count: 300
Enabled: true
Text equal: true
Text length: 3
Next byte: -1
~~~

Giải thích chính xác kích thước **14 byte**:

- `writeInt` ghi 4 byte `01 02 03 04`.
- `writeShort(300)` ghi 2 byte `01 2C`.
- `writeBoolean(true)` ghi 1 byte `01`.
- `writeUTF("A" + '\0' + "é")` ghi 2 byte độ dài **`00 05`**, theo sau là 5 byte `41 C0 80 C3 A9`.
- Tổng cộng **4 + 2 + 1 + 2 + 5 = 14 byte**.
- `DataInputStream.readUnsignedShort()` đọc lại trường 2 byte và trả `300`. `readUTF()` biết đọc đúng tiền tố độ dài và 5 byte của chính chuỗi đó.
- `decoded.length()` là `3`: ký tự `A`, ký tự NUL `\0` và ký tự `é`, mỗi ký tự là một UTF-16 code unit. Trong `System.out` không in trực tiếp chuỗi này vì ký tự NUL không thích hợp để quan sát trên terminal.
- Sau khi đọc đúng bốn trường, `in.read()` trả về `-1` vì không còn byte. Điều đó không có nghĩa `readInt()` tại EOF sẽ trả `-1`: `readInt()` **ném `EOFException`** khi không đủ bốn byte.

`ByteArrayOutputStream` giữ các byte đã ghi ngay cả khi wrapper `DataOutputStream` được đóng. Điều này là đặc tính của stream bộ nhớ; với file/socket, việc đóng wrapper vẫn có thể giải phóng hoặc kết thúc tài nguyên bên dưới.

## 5. `writeUTF` và `readUTF`: modified UTF-8 không phải UTF-8 thông thường

Tên `writeUTF` dễ dẫn đến kết luận sai. Phương thức này dùng **modified UTF-8**, có đặc điểm khác UTF-8 chuẩn:

- Ghi **hai byte big-endian** mô tả độ dài phần dữ liệu encoded phía sau, vì thế chiều dài encoded tối đa là **65535 byte** (không phải tối đa 65535 ký tự Java).
- Ký tự `U+0000` (NUL) được mã hóa thành **`C0 80`** thay vì byte `00` như UTF-8 chuẩn.
- Ký tự BMP khác trong các trường hợp thích hợp dùng một, hai hoặc ba byte. Các ký tự ngoài BMP được thể hiện bằng hai **UTF-16 surrogate code unit**, mỗi đơn vị được mã hóa riêng thành ba byte (tổng sáu byte), khác cơ chế bốn byte của UTF-8 chuẩn.
- `readUTF()` nhận định dạng đối ứng. Nếu đọc bằng decoder UTF-8 thông thường như `new String(payload, UTF_8)` thì không bảo đảm đúng, đặc biệt với NUL và ký tự ngoài BMP.
- `writeUTF` có thể ném `UTFDataFormatException` nếu độ dài modified UTF-8 cần ghi vượt giới hạn; `readUTF` có thể ném lỗi khi định dạng phần UTF không hợp lệ.

Vì thế, **`writeUTF`/`readUTF` phù hợp khi cả hai phía đều hiểu chính xác định dạng Java DataInput/DataOutput**. Chúng không phải lựa chọn mặc định khi cần sinh UTF-8 chuẩn để hệ thống khác đọc.

Bảng so sánh:

| Cách ghi | Cấu trúc byte | Phía đọc đúng |
|---|---|---|
| `out.writeUTF(text)` | 2 byte độ dài + modified UTF-8 | `in.readUTF()` |
| `out.write(text.getBytes(UTF_8))` | Chỉ có byte UTF-8, không có chiều dài | Cần biết ranh giới từ bên ngoài |
| `out.writeInt(bytes.length); out.write(bytes)` | 4 byte độ dài + byte UTF-8 chuẩn | `readInt()` rồi `readFully`, sau đó decode UTF-8 |
| `Files.writeString(path, text, UTF_8)` | Byte UTF-8 của văn bản | `Files.readString(path, UTF_8)` hoặc Reader phù hợp |

**Đừng thay `readUTF()` bằng `readLine()`.** `DataInputStream.readLine()` đã bị deprecated vì chuyển byte thành ký tự không đúng cho các charset phổ biến. Với dữ liệu văn bản theo dòng, hãy dùng `BufferedReader` với charset tường minh, ví dụ UTF-8.

## 6. Muốn lưu chuỗi UTF-8 chuẩn thì tự định nghĩa độ dài

Giả sử ứng dụng cần giao tiếp với một hệ thống ngoài Java và đặc tả của bạn yêu cầu **length-prefixed UTF-8**: bốn byte đầu là độ dài payload ở dạng `int` big-endian, tiếp theo là đúng N byte UTF-8.

Mã **minh họa phương thức**, dùng các import `java.io.*` và `java.nio.charset.StandardCharsets`:

~~~java
static void writeUtf8(DataOutputStream out, String text)
        throws IOException {
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    out.writeInt(bytes.length);
    out.write(bytes);
}

static String readUtf8(DataInputStream in) throws IOException {
    int length = in.readInt();
    final int MAX_TEXT_BYTES = 1_048_576; // Giới hạn do ứng dụng đặt.
    if (length < 0 || length > MAX_TEXT_BYTES) {
        throw new IOException("Độ dài chuỗi không hợp lệ: " + length);
    }
    byte[] bytes = new byte[length];
    in.readFully(bytes);
    return new String(bytes, StandardCharsets.UTF_8);
}
~~~

Khác `writeUTF`, cách này chủ động quy định `UTF-8` chuẩn và tiền tố độ dài 4 byte. Khi đọc, **kiểm tra chiều dài trước khi cấp phát** để tránh nhận dữ liệu sai định dạng hoặc dữ liệu không đáng tin gây sử dụng bộ nhớ quá mức.

Trong ví dụ, `new String(bytes, UTF_8)` có thể **thay thế byte UTF-8 bị lỗi** theo chính sách decoder của constructor. Nếu giao thức yêu cầu từ chối dữ liệu không hợp lệ, dùng `CharsetDecoder` cùng `CodingErrorAction.REPORT` để báo lỗi rõ ràng. Ngoài ra, mã minh họa chưa phải hệ giao thức có đầy đủ versioning, checksum hoặc thông điệp xác nhận; những tính năng đó tùy yêu cầu ứng dụng.

### Vì sao phải dùng `readFully`?

Đoạn sau **không đủ an toàn** khi cần nhận đúng N byte:

~~~java
byte[] buffer = new byte[length];
in.read(buffer); // Sai nếu giả định lần đọc này luôn trả đủ length byte.
~~~

Một lời gọi `read(byte[])` có thể trả ít hơn số byte yêu cầu, nhất là khi đọc mạng. `DataInputStream.readFully(buffer)` lặp cho đến khi đọc đủ buffer hoặc ném `EOFException` nếu nguồn kết thúc trước. Với file, cũng nên dùng API đúng hợp đồng thay vì dựa trên may mắn.

Trong một bản ghi phức tạp, mọi trường có kích thước thay đổi (chuỗi, mảng, danh sách) cần có **quy tắc ranh giới**: tiền tố độ dài, dấu phân cách phù hợp hoặc số lượng phần tử biết trước. `DataInputStream` chỉ cung cấp công cụ đọc byte/kiểu; nó **không tự suy ra ranh giới bản ghi**.

## 7. Lỗi đọc sai thứ tự, sai kiểu và trường thiếu

Giả sử phía ghi:

~~~java
out.writeInt(1000);      // 4 byte
out.writeBoolean(true);  // 1 byte
out.writeLong(9000L);    // 8 byte
~~~

Phía đọc đúng:

~~~java
int number = in.readInt();
boolean flag = in.readBoolean();
long id = in.readLong();
~~~

Đây là **hai đoạn minh họa** với `out` là `DataOutputStream` và `in` là `DataInputStream`, không phải chương trình chạy riêng. Nếu phía đọc gọi `readBoolean()` đầu tiên, nó lấy byte có giá trị cao nhất của `int 1000`, không phải cờ đã ghi; vị trí các trường phía sau bị lệch. Kết quả có thể **trông hợp lệ nhưng sai**, hoặc phát sinh `EOFException`.

### Khi nào `EOFException` xảy ra?

- `readInt()` cần đủ 4 byte, `readLong()` cần đủ 8 byte, `readDouble()` cần đủ 8 byte. Khi không đủ byte, những phương thức này ném `EOFException` (một subtype của `IOException`).
- `readFully(byte[])` ném `EOFException` nếu nguồn kết thúc trước khi đầy mảng.
- `read()` được thừa kế theo hợp đồng byte stream trả `-1` khi hết luồng, vì nó chỉ yêu cầu **một byte** hoặc dấu EOF.
- `readUTF()` có thể ném `EOFException` nếu dữ liệu kết thúc giữa chừng hoặc `UTFDataFormatException` nếu encoded data không hợp lệ.

Dùng ngoại lệ một cách có ý nghĩa: **file bị cắt giữa một bản ghi** là dấu hiệu dữ liệu không đầy đủ. Đừng bắt `EOFException` rồi coi đó là “đọc bản ghi thành công” khi cấu trúc yêu cầu phải có đủ trường.

### `available()` không phải độ dài file hay số byte chắc chắn còn lại

`DataInputStream.available()` phản ánh số byte có thể đọc mà không chặn theo hợp đồng luồng bên dưới, không phải một cách đáng tin để dự đoán toàn bộ phần còn lại của mạng/file. Đừng dùng điều kiện `available() >= 4` như phương pháp chung để quyết định có đọc được một `int` hay không. Khi biết schema, hãy đọc bằng `readInt`/`readFully` và xử lý `EOFException` đúng cách.

## 8. Ghi file thật: vòng đời, ghi đè và buffer

Ví dụ code tự mở và sở hữu tệp nhị phân:

~~~java
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class BinaryFileExample {
    static void write(Path file, int version, long timestamp)
            throws IOException {
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(version);
            out.writeLong(timestamp);
        }
    }

    static long readTimestamp(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(file)))) {
            int version = in.readInt();
            if (version != 1) {
                throw new IOException("Unsupported version: " + version);
            }
            return in.readLong();
        }
    }
}
~~~

Đây là một **lớp tiện ích biên dịch được**, không có phương thức `main`. Caller có thể gọi `write(file, 1, someTimestamp)` rồi `readTimestamp(file)` để nhận lại `someTimestamp`.

Lưu ý:

1. `Files.newOutputStream(file)` theo tùy chọn mặc định có thể **tạo mới hoặc cắt ngắn tệp đã có**. Nếu muốn tránh ghi đè, chọn tùy chọn như `StandardOpenOption.CREATE_NEW` và xác định chiến lược xử lý lỗi.
2. `try-with-resources` giúp đóng stream do code **tự mở**, kể cả khi thao tác đọc/ghi ném lỗi. Không nhất thiết đóng từng wrapper bằng tay trong khối.
3. `BufferedOutputStream` có thể giữ byte trong buffer, nên cần flush/close đúng vòng đời. Đóng đúng lúc không tự bảo đảm dữ liệu đã được ghi bền vững trên thiết bị.
4. Kiểm tra trường **version** khi đọc giúp tránh áp dụng sai schema khi định dạng phát triển. Nếu file có nhiều bản ghi, phải thêm quy tắc đếm bản ghi/độ dài record hoặc xác định điểm kết thúc.
5. Với dữ liệu quan trọng, việc ghi bị gián đoạn có thể để lại tệp **chỉ có một phần byte**. Khi cần bảo toàn file cũ, có thể ghi ra tệp tạm và thay thế theo chiến lược cập nhật an toàn phù hợp hệ thống tệp.

## 9. Phân biệt với các API I/O khác

| Công cụ | Dùng khi nào? | Không tự làm được gì? |
|---|---|---|
| `DataInputStream/DataOutputStream` | Đọc/ghi primitive có kiểu theo nhị phân xác định | Không định nghĩa schema đối tượng, không tự tạo format tự mô tả |
| `InputStream/OutputStream` | Đọc/ghi byte nói chung | Không có `readInt`, `writeDouble` trực tiếp |
| `BufferedInputStream/BufferedOutputStream` | Thêm buffer để giảm tương tác với tầng nền | Không tự hiểu kiểu dữ liệu |
| `Reader/Writer` | Đọc/ghi ký tự và văn bản theo charset | Không phù hợp để tự ý diễn giải mọi byte nhị phân |
| `ObjectInputStream/ObjectOutputStream` | Java object serialization theo cơ chế riêng | Không cùng format với `DataInputStream`; không nên nhầm hai loại |
| `ByteBuffer` | Biểu diễn byte và đọc/ghi primitive trong buffer, cấu hình byte order | Không tự định nghĩa hoặc truyền một giao thức |
| `Files.copy` / `InputStream.transferTo` | Sao chép dữ liệu byte nguyên trạng | Không tự phân tích các trường primitive |

`DataInputStream` và `DataOutputStream` rất hữu ích cho format nhỏ, ổn định và được kiểm soát. Nhưng khi cần giao tiếp nhiều ngôn ngữ, thay đổi schema phức tạp hoặc tương thích lâu dài, nên cân nhắc định dạng/giao thức có đặc tả rõ ràng. Dù chọn công cụ nào, phải ghi chính xác **field order**, **field width**, **endianness**, **charset** và **version**.

## 10. Lỗi thường gặp

| Nhầm lẫn | Hậu quả | Cách đúng |
|---|---|---|
| Ghi `writeInt` rồi đọc `readShort` | Đọc thiếu byte, trường kế tiếp bị lệch | Đọc đúng thứ tự và đúng loại đã ghi |
| Tưởng số được ghi bằng chuỗi chữ số | Cố decode file nhị phân thành text, dữ liệu “khó đọc” | Hiểu primitive được ghi bằng byte nhị phân |
| Quên big-endian | Tự dựng parser phía khác đảo byte | Ghi vào đặc tả thứ tự byte lớn trước |
| Tưởng `writeUTF` là UTF-8 chuẩn | Bộ đọc bên ngoài nhận sai payload | Dùng `readUTF` đối ứng hoặc tự định nghĩa UTF-8 chuẩn |
| Đọc một lần `read(byte[])` rồi coi là đủ | Có thể mất phần byte còn lại | `readFully` hoặc vòng đọc đúng hợp đồng |
| Đọc length từ file rồi cấp phát không giới hạn | Có thể lỗi bộ nhớ hoặc bị dữ liệu giả mạo tác động | Xác thực length trước khi cấp phát |
| Dùng `available()` như tổng byte còn lại | Lỗi với các stream chỉ báo byte hiện đọc không chặn | Dùng schema và xử lý EOF rõ ràng |
| Không đóng wrapper do mình sở hữu | Mất byte trong buffer hoặc rò rỉ tài nguyên | `try-with-resources` tại tầng sở hữu |
| Đóng wrapper trên stream do caller cấp | Có thể đóng mất tài nguyên dùng chung | Quy định rõ ownership, helper chỉ mượn thì không đóng |
| Ghi vào file tồn tại mà không xét tùy chọn mở | File cũ có thể bị cắt ngắn | Chọn `StandardOpenOption` đúng nhu cầu |
| Nghĩ `DataInputStream` tự hiểu bất kỳ binary format | Giá trị đọc sai hoặc exception | Đọc theo schema do ứng dụng/giao thức định nghĩa |

## 11. Tự kiểm tra

**Câu 1.** Code ghi `writeInt(0x01020304)` thì tệp có bốn byte hex nào?

> **Đáp án:** `01 02 03 04` vì `DataOutputStream` dùng **big-endian** cho số nguyên nhiều byte.

**Câu 2.** Ghi `writeByte(255)`, đọc bằng `readByte()` được bao nhiêu? Nếu muốn 255 thì dùng gì?

> **Đáp án:** `readByte()` trả `-1` do diễn giải 8 bit có dấu. Dùng `readUnsignedByte()` để nhận `255`.

**Câu 3.** Tại sao `writeUTF("A" + '\0' + "é")` cần 7 byte, không phải 5 byte?

> **Đáp án:** Modified UTF-8 của chuỗi có **5 byte** (`41 C0 80 C3 A9`), cộng **2 byte tiền tố độ dài** thành **7 byte**.

**Câu 4.** `DataInputStream.readInt()` gặp EOF chỉ còn hai byte: trả `-1` hay ném ngoại lệ?

> **Đáp án:** Ném **`EOFException`** vì không đủ bốn byte để tạo `int`. `-1` là giá trị báo EOF của `InputStream.read()`.

**Câu 5.** Có nên đọc chuỗi dài N byte bằng một lần `in.read(bytes)` rồi mặc định đã đủ dữ liệu?

> **Đáp án:** Không. Một lần đọc có thể nhận ít hơn N byte. Dùng **`readFully(bytes)`** sau khi kiểm tra N hợp lệ hoặc vòng đọc bảo đảm đủ N byte.

**Câu 6.** Có thể dùng `readUTF()` để đọc file UTF-8 thông thường không?

> **Đáp án:** Không theo cách chung. `readUTF()` đòi **hai byte độ dài** và phần dữ liệu **modified UTF-8** theo `DataInput`. File text UTF-8 thông thường không mặc nhiên có cấu trúc đó.

**Câu 7.** Code mượn `OutputStream` của framework rồi tạo `DataOutputStream`. Có nên đóng `DataOutputStream` ở cuối helper?

> **Đáp án:** Chỉ khi hợp đồng giao quyền đóng stream cho helper. Nếu helper chỉ mượn, đóng wrapper có thể đóng luôn output framework. Caller/tầng sở hữu quyết định vòng đời.

## Ghi nhớ

**`DataOutputStream` mã hóa primitive thành byte; `DataInputStream` giải mã byte thành primitive.** Hai phía phải thống nhất thứ tự trường, chiều rộng trường, big-endian và định dạng chuỗi. Nhớ ba điểm đặc biệt: **`writeUTF/readUTF` dùng modified UTF-8 kèm 2 byte độ dài**, **`readFully` bảo đảm lấy đủ payload hoặc báo EOF**, và **việc đóng wrapper phải tuân theo quyền sở hữu stream**.

**Tài liệu tham khảo:** [Java SE 17 — `DataInputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataInputStream.html), [`DataOutputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataOutputStream.html), [`DataInput`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataInput.html), [`DataOutput`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataOutput.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="binary-data-003">Thứ tự ghi phải tương ứng với thứ tự đọc</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự ghi phải tương ứng với thứ tự đọc trong Java I/O nhị phân

## 1. Nguyên tắc cốt lõi: dữ liệu nhị phân không tự mang tên và kiểu trường

Trong I/O nhị phân, `OutputStream` ghi các **byte liên tiếp**. Các byte không tự cho biết đoạn nào là số nguyên, số thực, chuỗi, số phần tử hoặc điểm kết thúc một bản ghi. Phía nhận phải biết trước **giao thức/định dạng**: các trường có thứ tự ra sao, mỗi trường dùng bao nhiêu byte, kiểu mã hóa gì và được giới hạn như thế nào.

Ví dụ nếu phía ghi làm:

```java
out.writeInt(42);
out.writeBoolean(true);
out.writeDouble(3.5);
```

thì phía đọc tương ứng phải làm:

```java
int id = in.readInt();
boolean active = in.readBoolean();
double score = in.readDouble();
```

Hai đoạn trên chỉ minh họa; `out` phải là `DataOutputStream`, `in` là `DataInputStream` cùng đọc đúng luồng byte đã ghi, và cần xử lý `IOException`. Đọc `double` trước `int` hoặc dùng `readShort()` thay `readInt()` sẽ tiêu thụ sai số byte; từ đó các trường phía sau có thể bị hiểu sai hoặc gặp EOF.

**Quy tắc:** Với mỗi trường được ghi, bên đọc phải dùng phương thức giải mã **tương ứng về định dạng**, theo **đúng thứ tự**, đồng thời tuân thủ mọi quy ước độ dài, endian, charset, phiên bản và ranh giới bản ghi.

## 2. Vì sao thứ tự quan trọng?

Một luồng byte thông thường chỉ có vị trí đọc hiện tại (cursor). Mỗi lần đọc:

1. Lấy một số byte từ vị trí hiện tại.
2. Diễn giải số byte đó theo phương thức đã gọi.
3. Tiến cursor đúng số byte phương thức tiêu thụ.

`readInt()` cần 4 byte; `readShort()` cần 2 byte; `readLong()` cần 8 byte. Nếu ghi một `int` 4 byte nhưng lại đọc một `short` 2 byte, còn **2 byte của trường cũ** nằm phía trước trường tiếp theo. Tất cả những lần đọc sau có thể bị lệch.

Hình dung định dạng có ba trường:

```text
Ghi:  [int id: 4 byte][boolean active: 1 byte][double score: 8 byte]
        └── 4 byte ──┘ └─ 1 byte ─┘ └────── 8 byte ──────┘

Đọc đúng:
       readInt()       readBoolean()           readDouble()

Đọc sai:
       readShort()     readBoolean()           readDouble()
       └─ chỉ 2 byte ─┘
                        ↑ cursor giờ nằm giữa trường id
```

Dòng dữ liệu ở đây mô tả một chuỗi byte thuần. Không có dấu phân cách ngầm giữa các lời gọi `writeInt`/`writeDouble`; việc gọi `flush()` hoặc `close()` không tự thêm tên trường.

Đặc biệt, **giá trị đôi khi nhìn có vẻ hợp lệ dù đọc sai**: 2 hoặc 4 byte lệch nhau vẫn tạo được một số nguyên Java bất kỳ. Chỉ dựa vào “không ném ngoại lệ” là chưa đủ để xác nhận dữ liệu đã được giải mã đúng.

## 3. Cặp API ghi/đọc tương ứng của `DataOutputStream` và `DataInputStream`

`java.io.DataOutputStream`/`DataInputStream` cung cấp cách biểu diễn một số kiểu nguyên thủy theo hợp đồng **binary** của Java:

| Ghi | Đọc phù hợp | Số byte / cách diễn giải |
| --- | --- | --- |
| `writeBoolean(boolean)` | `readBoolean()` | 1 byte; ghi `1` cho `true`, `0` cho `false` |
| `writeByte(int)` | `readByte()` hoặc `readUnsignedByte()` | 1 byte; đọc signed `-128..127` hoặc unsigned `0..255` |
| `writeShort(int)` | `readShort()` hoặc `readUnsignedShort()` | 2 byte, big-endian; signed/unsigned |
| `writeChar(int)` | `readChar()` | 2 byte, một UTF-16 code unit, big-endian |
| `writeInt(int)` | `readInt()` | 4 byte, big-endian, số nguyên signed |
| `writeLong(long)` | `readLong()` | 8 byte, big-endian, số nguyên signed |
| `writeFloat(float)` | `readFloat()` | 4 byte, bit IEEE 754 theo đặc tả của DataOutput |
| `writeDouble(double)` | `readDouble()` | 8 byte, bit IEEE 754 theo đặc tả của DataOutput |
| `writeUTF(String)` | `readUTF()` | 2 byte ghi **độ dài số byte**, rồi nội dung **modified UTF-8** |
| `write(byte[])` | `readFully(byte[])` | Đúng số byte trong mảng; phải biết trước độ dài |

Các phương thức `writeByte`/`writeShort` chỉ ghi những byte thấp của số được truyền vào, không phải bất kỳ giá trị `int` nào cũng được giữ nguyên khi thu hẹp. `readUnsignedByte`/`readUnsignedShort` dùng khi giao thức định nghĩa trường là không dấu; **cùng một byte** có thể được diễn giải khác nhau tùy API đọc.

Đối với các kiểu nhiều byte của `DataOutputStream`, thứ tự là **big-endian** (byte có trọng số lớn nhất trước). `DataInputStream` đọc theo chính quy ước đó. Nếu tương tác với phần mềm dùng little-endian hoặc cấu trúc tùy chỉnh, phải thực hiện đúng đặc tả của bên kia, ví dụ qua `ByteBuffer.order(ByteOrder.LITTLE_ENDIAN)` hoặc API phù hợp; không mặc định mọi định dạng nhị phân của mọi hệ thống giống Data Streams.

### Phân biệt `read()`, `readFully()` và `readInt()`

- `InputStream.read()` lấy **một byte** và trả số nguyên `0..255` hoặc `-1` khi EOF.
- `InputStream.read(byte[])` được phép trả **ít byte hơn kích thước mảng** ngay cả khi chưa EOF. Nó không bảo đảm một lần đọc sẽ đầy mảng.
- `DataInputStream.readFully(byte[])` cố đọc đủ toàn bộ số byte cần thiết; nếu EOF xảy ra trước thì ném `EOFException`.
- `DataInputStream.readInt()` đọc chính xác 4 byte và diễn giải thành một `int`; không giống gọi `read()` rồi mặc định đã có đủ dữ liệu của một trường.

Một lệnh đọc thành công chỉ có nghĩa là phương thức ấy đã đọc theo **quy tắc riêng của nó**; chương trình vẫn chịu trách nhiệm bảo đảm quy tắc ấy khớp schema của tệp.

## 4. Ví dụ chạy được: ghi và đọc đúng thứ tự

Tạo file **`BinaryWriteReadOrderDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;

public class BinaryWriteReadOrderDemo {
    public static void main(String[] args) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(42);
            out.writeBoolean(true);
            out.writeDouble(3.5);
            out.writeUTF("Cà phê");
        }

        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            int id = in.readInt();
            boolean active = in.readBoolean();
            double score = in.readDouble();
            String name = in.readUTF();

            System.out.println("id=" + id);
            System.out.println("active=" + active);
            System.out.printf(Locale.ROOT, "score=%.1f%n", score);
            System.out.println("name=" + name);
            System.out.println("remaining=" + in.available());
        }
    }
}
```

Biên dịch và chạy trên JDK 17+:

```bash
javac -encoding UTF-8 BinaryWriteReadOrderDemo.java
java BinaryWriteReadOrderDemo
```

Đầu ra:

```text
id=42
active=true
score=3.5
name=Cà phê
remaining=0
```

Các điểm cần hiểu:

1. `DataOutputStream` ghi lần lượt **`int` → `boolean` → `double` → chuỗi có tiền tố độ dài**. Không thêm tên trường hoặc dấu ngăn cách.
2. `DataInputStream` đọc **đúng thứ tự và đúng cặp API**, nên khôi phục được bốn giá trị ban đầu.
3. `writeUTF`/`readUTF` là cặp tương ứng với *modified UTF-8 của Data Streams*, không phải ghi byte UTF-8 thô. Chữ có dấu trong ví dụ vẫn round-trip đúng.
4. `DataOutputStream` được đóng trước khi `toByteArray()` được gọi để mọi tầng writer hoàn tất. Ở ví dụ chỉ có bộ đệm trong RAM, nhưng nguyên tắc hoàn tất/đóng quan trọng khi kết hợp file hoặc các wrapper khác.
5. `available() == 0` ở **`ByteArrayInputStream` cụ thể này** cho thấy không còn byte trong mảng. **Không dùng `available() == 0` làm phép kiểm tra EOF tổng quát cho mọi `InputStream`**, nhất là socket hay stream phức tạp.

Đây là ví dụ nhị phân thuần trong RAM; nếu đổi nguồn/đích thành file, thứ tự các lệnh vẫn phải được giữ nguyên.

## 5. Ví dụ đọc sai thứ tự: chương trình vẫn chạy nhưng dữ liệu sai

Một trường `int` gồm 4 byte và một trường `short` gồm 2 byte. Cho phép minh họa chính xác mà không phải dựa vào hành vi lỗi ngẫu nhiên.

Tạo file **`WrongBinaryOrderDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;

public class WrongBinaryOrderDemo {
    public static void main(String[] args) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(0x01020304);
            out.writeShort(0x0506);
        }

        byte[] content = bytes.toByteArray();

        try (DataInputStream right = new DataInputStream(
                new ByteArrayInputStream(content))) {
            System.out.printf(Locale.ROOT, "correct=%08X,%04X%n",
                    right.readInt(), right.readUnsignedShort());
        }

        try (DataInputStream wrong = new DataInputStream(
                new ByteArrayInputStream(content))) {
            System.out.printf(Locale.ROOT, "wrong=%04X,%08X%n",
                    wrong.readUnsignedShort(), wrong.readInt());
        }
    }
}
```

Chạy:

```bash
javac WrongBinaryOrderDemo.java
java WrongBinaryOrderDemo
```

Đầu ra:

```text
correct=01020304,0506
wrong=0102,03040506
```

Giải thích theo byte:

```text
Thực tế đã ghi: 01 02 03 04 | 05 06
                 └── int ───┘   short

Đọc đúng:        01 02 03 04 | 05 06
                 readInt        readUnsignedShort

Đọc sai:         01 02 | 03 04 05 06
                 short   int (đã trượt ranh giới)
```

Điều đáng chú ý là cả hai lần đọc sai **đều có đủ byte để hoàn thành**, nên không nhất thiết có ngoại lệ. Nếu muốn phân tích lỗi, kiểm tra schema và offset, hoặc so byte ở dạng hex; không chỉ bắt `EOFException` là đủ.

**Lưu ý:** Nếu số byte còn lại không đủ cho `readInt()`, Java sẽ ném `EOFException`. Nếu còn đủ nhưng không đúng trường, Java vẫn tạo ra một giá trị từ những byte đó. Hai tình huống này cần được chẩn đoán khác nhau.

## 6. Trường có độ dài thay đổi: phải thống nhất quy tắc “framing”

`int`, `long` và `double` có độ dài cố định, nhưng `String` và dữ liệu mảng thường **không có độ dài cố định**. Nếu ghi nhiều chuỗi liên tiếp như byte UTF-8 thô mà không có thông tin ranh giới, bên đọc không biết byte của chuỗi thứ nhất dừng ở đâu.

Một cách phổ biến là **length prefix**: ghi trước độ dài dưới dạng một `int`, rồi mới ghi đúng số byte. Phía đọc cần gọi `readInt()` để nhận chiều dài, xác thực chiều dài, rồi `readFully(...)` để lấy chính xác payload.

Định dạng hai bản ghi ví dụ:

```text
[int count]
    [int id][int nameByteLength][nameUtf8Bytes]
    [int id][int nameByteLength][nameUtf8Bytes]
```

Ở đây độ dài chuỗi được tính bằng **số byte UTF-8**, không phải `String.length()` (số đơn vị mã UTF-16). Ví dụ tên chứa dấu tiếng Việt có thể chiếm nhiều byte hơn số ký tự hiển thị.

Ví dụ Java 17+ chạy được **`LengthPrefixedRecordsDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class LengthPrefixedRecordsDemo {
    private static void writeRecord(
            DataOutputStream out, int id, String name) throws IOException {
        byte[] utf8 = name.getBytes(StandardCharsets.UTF_8);
        out.writeInt(id);
        out.writeInt(utf8.length);
        out.write(utf8);
    }

    public static void main(String[] args) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(2); // số bản ghi
            writeRecord(out, 7, "An");
            writeRecord(out, 8, "Bình");
        }

        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            int count = in.readInt();
            if (count < 0 || count > 100) {
                throw new IOException("Invalid record count: " + count);
            }

            for (int i = 0; i < count; i++) {
                int id = in.readInt();
                int length = in.readInt();

                if (length < 0 || length > 1024) {
                    throw new IOException("Invalid name length: " + length);
                }

                byte[] payload = new byte[length];
                in.readFully(payload);
                String name = new String(payload, StandardCharsets.UTF_8);

                System.out.println("id=" + id + ", name=" + name);
            }
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 LengthPrefixedRecordsDemo.java
java LengthPrefixedRecordsDemo
```

Đầu ra:

```text
id=7, name=An
id=8, name=Bình
```

Các bước tương ứng rất rõ:

| Thứ tự | Ghi | Đọc |
| ---: | --- | --- |
| 1 | `out.writeInt(2)` | `int count = in.readInt()` |
| 2 | `out.writeInt(id)` | `int id = in.readInt()` |
| 3 | `out.writeInt(utf8.length)` | `int length = in.readInt()` |
| 4 | `out.write(utf8)` | `in.readFully(payload)` |
| 5 | Lặp lại bước 2–4 cho record tiếp theo | Lặp đúng `count` record |

Nếu bên ghi dùng byte UTF-8 nhưng bên đọc lại gọi `readUTF()`, dữ liệu không tương thích: `readUTF()` kỳ vọng **prefix 2 byte và modified UTF-8**, trong khi ví dụ dùng **prefix 4 byte và UTF-8 chuẩn**. Cùng nói là “chuỗi” không đủ; phải thống nhất **từng byte của định dạng chuỗi**.

**Bảo vệ khi đọc dữ liệu không tin cậy:** Kiểm tra `count` và `length` trước khi cấp phát `byte[]`. Giới hạn tối đa phụ thuộc nghiệp vụ; `1024` trong ví dụ chỉ là giới hạn giả định. Đừng cho dữ liệu đầu vào tự cấp phát mảng hàng gigabyte hoặc lặp vô hạn bằng cách khai số bản ghi cực lớn.

## 7. EOF, bản ghi cắt cụt và việc đọc có đủ byte hay không

`EOFException` thường báo rằng một phương thức như `readInt()`, `readLong()` hoặc `readFully()` không lấy đủ byte cần thiết. Với giao thức cố định, điều này thường chỉ ra tệp/luồng bị **cắt cụt**, quá trình truyền chưa đủ, hoặc schema hai phía không đồng nhất.

Phân biệt:

- **Kết thúc hợp lệ:** đã đọc đủ số bản ghi được khai báo, hoặc gặp marker kết thúc được giao thức quy định.
- **Kết thúc bất ngờ:** schema đòi hỏi thêm trường hoặc byte nhưng byte đã hết, dẫn tới `EOFException`.
- **Giá trị sai nhưng không EOF:** đọc lạc ranh giới tạo số vẫn hợp lệ về kiểu Java nhưng không hợp lệ theo nghiệp vụ.
- **Nguồn chưa có dữ liệu ngay:** stream mạng có thể chờ, không có nghĩa là EOF. `available()` không cung cấp ranh giới bản ghi đáng tin cậy.

Không nên dùng `catch (EOFException e) { /* bỏ qua */ }` như một quy tắc chung. Chỉ xem EOF là kết thúc bình thường nếu **định dạng cho phép EOF làm dấu kết thúc** và không có bản ghi nào đang đọc dở. Với số bản ghi/độ dài đã được khai báo, EOF giữa một bản ghi là lỗi.

Nếu định dạng phải phát hiện byte dư ngoài dữ liệu hợp lệ, sau khi đọc đủ record có thể kiểm tra một byte tiếp theo:

```java
int extra = in.read();
if (extra != -1) {
    throw new IOException("Unexpected trailing bytes");
}
```

Đây là **đoạn minh họa** có `DataInputStream in` đã đọc xong phần hợp lệ, cần import `IOException`. Phép đọc có thể chờ với một nguồn mạng còn mở; nó phù hợp khi giao thức/tệp dự kiến đã kết thúc hoặc có cách truyền đạt kết thúc rõ ràng, không nên áp dụng vô điều kiện cho một kết nối streaming dài hạn.

## 8. Chuỗi trong định dạng nhị phân: `writeUTF` không đồng nghĩa UTF-8 thông thường

Java Data Streams có cặp:

```java
out.writeUTF(text);
String restored = in.readUTF();
```

Đây là **đoạn mã minh họa**, cần `out`/`in` tương ứng. `writeUTF` ghi **2 byte độ dài không dấu** (tối đa 65.535 byte payload mã hóa), rồi dữ liệu theo **modified UTF-8**. `readUTF` đọc đúng quy ước này. Với chuỗi được mã hóa quá dài, `writeUTF` có thể ném `UTFDataFormatException`.

Modified UTF-8 của `DataOutputStream` không đồng nhất với UTF-8 chuẩn:

- Ký tự NUL `U+0000` được biểu diễn khác dạng một byte `00` của UTF-8 chuẩn.
- Ký tự Unicode ngoài BMP đi qua các UTF-16 surrogate code unit và có thể dùng **6 byte** trong modified UTF-8, thay vì 4 byte UTF-8 chuẩn.
- Tiền tố độ dài của `writeUTF` chỉ 2 byte; với length prefix tùy chỉnh `writeInt(length)` thì tiền tố dài 4 byte.

Vì vậy trước khi đọc **bất cứ chuỗi nào**, cần trả lời: byte này do `writeUTF` tạo ra, hay là UTF-8/UTF-16/charset khác theo định dạng đã công bố? Không dùng `readUTF()` để đọc dữ liệu do `OutputStreamWriter(UTF_8)` ghi, trừ khi phía ghi đã chủ động xây đúng tiền tố và modified UTF-8.

Tương tự, `writeChars(String)` ghi **mỗi `char` thành 2 byte** theo big-endian, không kèm độ dài. `writeBytes(String)` ghi 8 bit thấp của từng `char` và có thể mất dữ liệu Unicode. Cả hai không tương đương `writeUTF` hoặc mã hóa UTF-8 chuẩn.

## 9. Những quy tắc khác ngoài thứ tự phải được thống nhất

Thứ tự là điều kiện cần, nhưng chưa đủ. Một “hợp đồng dữ liệu” nhị phân nên ghi rõ:

1. **Magic/header:** có hay không chữ ký nhận dạng định dạng; ví dụ 4 byte hằng để tránh đọc nhầm tệp.
2. **Version:** phiên bản schema; bên đọc biết cách diễn giải phần sau theo version nào.
3. **Field order:** trường nào trước, trường nào sau; trường bắt buộc và tùy chọn.
4. **Primitive encoding:** độ rộng, signed/unsigned, endian, biểu diễn boolean và floating point.
5. **String encoding:** UTF-8 chuẩn, modified UTF-8, UTF-16..., và cách giới hạn độ dài.
6. **Framing:** số bản ghi, prefix độ dài, delimiter, sentinel hoặc EOF có ý nghĩa gì.
7. **Bounds và validation:** giới hạn count, length, miền giá trị, checksum nếu cần.
8. **Compatibility:** quy tắc khi thêm trường, bỏ trường hoặc thay đổi kiểu.

### Ví dụ thêm phiên bản vào đầu tệp

Một schema có thể được thiết kế:

```text
[int magic = 0x4A494F31]
[short version = 1]
[int recordCount]
[record 1]
[record 2]
...
```

Phía ghi sẽ gọi `writeInt(magic)` → `writeShort(version)` → `writeInt(recordCount)` → ghi records. Phía đọc phải theo đúng thứ tự `readInt` → `readShort` → `readInt` → đọc records, và kiểm tra giá trị magic/version trước khi đi tiếp. Đây là **sơ đồ định dạng minh họa**, không phải tệp chuẩn chung của Java.

Khi nâng version, không chỉ “ghi thêm một trường cuối” rồi mong reader cũ hiểu được. Nếu reader cũ còn đọc các bản ghi tiếp theo, trường thêm có thể bị xem là phần mở đầu của record kế tiếp. Cần định nghĩa cơ chế tương thích, như header/version, bản ghi có length prefix để bỏ qua trường chưa biết, hoặc định dạng tự mô tả như Protocol Buffers. Không nhầm bản ghi nhị phân tự thiết kế với Java Object Serialization (`ObjectOutputStream`/`ObjectInputStream`), vốn có định dạng và quy tắc riêng.

## 10. Trường hợp nhiều lớp stream, file và buffer

Có thể kết hợp buffer để giảm lệnh I/O vật lý mà **không đổi thứ tự trường**:

```java
try (DataOutputStream out = new DataOutputStream(
        new BufferedOutputStream(
                Files.newOutputStream(path)))) {
    out.writeInt(42);
    out.writeLong(123L);
}
```

Đây là **đoạn minh họa**, cần `Path path`, import `java.nio.file.Files`, `java.io.BufferedOutputStream`, `java.io.DataOutputStream` và xử lý `IOException`. `Files.newOutputStream(path)` mặc định có thể **tạo hoặc cắt ngắn tệp**. Đối với dữ liệu quan trọng, chọn đường dẫn/chính sách mở tệp có chủ đích.

Đọc tương ứng:

```java
try (DataInputStream in = new DataInputStream(
        new BufferedInputStream(
                Files.newInputStream(path)))) {
    int id = in.readInt();
    long value = in.readLong();
}
```

Đây là **đoạn minh họa**, cần import các lớp thích hợp và xử lý `IOException`.

Luồng ghi dữ liệu chạy từ ứng dụng qua `DataOutputStream` → `BufferedOutputStream` → đích byte. Bên đọc đi từ nguồn byte → `BufferedInputStream` → `DataInputStream` → ứng dụng. Buffer tăng hiệu quả truyền byte nhưng không tự chèn delimiter hoặc sửa schema.

Với file, hãy đóng `DataOutputStream` để flush bộ đệm theo dây chuyền. Với socket, `flush()` có thể giúp đẩy dữ liệu đang giữ trong buffer, nhưng **không đồng nghĩa phía nhận đã đọc hay xử lý thành công**. Nếu giao thức cần xác nhận, phải thiết kế ACK/response ở lớp ứng dụng.

Không đóng wrapper khi còn cần dùng luồng nền bên dưới; đóng lớp ngoài cùng thường kéo theo đóng các lớp mà nó sở hữu. Thiết kế rõ trách nhiệm đóng của caller/callee.

## 11. Kiểm thử tính tương ứng giữa ghi và đọc

Tốt nhất kiểm tra định dạng nhị phân bằng cả **round-trip** và **golden bytes**.

### Round-trip

Viết một record vào `ByteArrayOutputStream`, đọc bằng `DataInputStream` trên `ByteArrayInputStream` rồi so từng trường. Các ví dụ chạy được ở trên đã dùng kỹ thuật này. Hãy thử thêm:

- Các giá trị biên: `Integer.MIN_VALUE`, `Integer.MAX_VALUE`, `Long.MAX_VALUE`, số âm, zero, boolean false.
- Chuỗi rỗng, chuỗi có dấu tiếng Việt, emoji và ký tự NUL.
- Mảng byte rỗng, payload lớn hơn kích thước buffer đọc.
- Nhiều bản ghi nối liên tiếp, count đúng và count sai.
- Dữ liệu bị cắt ngay giữa một trường 2/4/8 byte hoặc giữa payload của chuỗi.

### Golden bytes

Kiểm tra chính byte được ghi ra theo định dạng công bố. Ví dụ `writeInt(0x01020304)` phải ghi `01 02 03 04` theo big-endian; `writeShort(0x0506)` tiếp sau phải tạo `05 06`. Golden bytes giúp phát hiện trường hợp cả writer và reader cùng mắc **một lỗi giống nhau**: bài round-trip vẫn có thể đi qua, nhưng dữ liệu không tương thích với ngôn ngữ/phần mềm khác.

### Thử đọc sai có chủ đích

Tạo một bản ghi hợp lệ, cố tình đọc `readShort` thay `readInt` hoặc đảo vị trí hai trường. Kỳ vọng **giá trị sai, EOFException hoặc vi phạm validation** chứ không mặc định chỉ một dạng lỗi duy nhất. Kiểm tra thông báo và hành vi phục hồi: có bỏ bản ghi, dừng toàn bộ tệp hay yêu cầu gửi lại dữ liệu?

Một parser tốt nên giữ **offset/bối cảnh bản ghi** khi báo lỗi, ví dụ “record 12, field nameLength không hợp lệ”, thay vì chỉ “không đọc được file”.

## 12. Lỗi thường gặp và checklist

| Lỗi | Hậu quả | Cách làm đúng |
| --- | --- | --- |
| Ghi `int` nhưng đọc `short` | Cursor trượt ranh giới 2 byte | Dùng `readInt` khớp `writeInt` |
| Ghi các trường theo A → B → C nhưng đọc A → C → B | Giá trị bị diễn giải sai | Tạo schema trường có thứ tự rõ |
| Ghi byte chuỗi UTF-8 nhưng đọc bằng `readUTF` | Sai tiền tố/charset, có thể EOF hoặc lỗi Unicode | Thống nhất cặp encode/decode và framing |
| Dùng `String.length()` làm số byte UTF-8 | Đọc thiếu/thừa khi gặp chữ có dấu/emoji | Tính độ dài từ `getBytes(UTF_8).length` |
| Đọc `read(byte[])` một lần và tin đã đủ | Payload có thể bị đọc thiếu | `readFully` hoặc vòng lặp xử lý số byte trả về |
| Dùng `available()` để tính độ dài bản ghi | Không phản ánh ranh giới dữ liệu ở mọi stream | Prefix độ dài hoặc schema/framing |
| Không kiểm tra chiều dài do input khai báo | Có thể cấp phát quá lớn hoặc lỗi | Giới hạn count/length trước khi cấp phát |
| Nuốt mọi `EOFException` | Bỏ qua dữ liệu cắt cụt | Chỉ xem EOF hợp lệ nếu schema nói như vậy |
| Đổi byte order mà không đổi decoder | Giá trị số sai | Thống nhất big-/little-endian |
| Ghi thêm field không tăng version | Reader cũ mất đồng bộ | Version/header và quy tắc tương thích |
| Đọc bytes bằng `Reader` tùy tiện | Charset decode có thể thay đổi byte tùy ý | Với binary dùng `InputStream`/`DataInputStream` |
| Ghi xong chưa flush/close rồi cho reader mở đọc | Dữ liệu chưa được đẩy đủ qua buffer | Hoàn tất writer trước khi reader đọc |

**Checklist trước khi lưu hoặc gửi một giao thức nhị phân:**

- [ ] Viết thứ tự các trường từ byte đầu đến byte cuối.
- [ ] Định nghĩa kích thước, signedness, endian và kiểu dữ liệu của từng trường.
- [ ] Định nghĩa chuỗi theo charset nào, prefix độ dài bao nhiêu byte, độ dài tính theo đơn vị gì.
- [ ] Ghi rõ cách xác định số record hoặc dấu kết thúc.
- [ ] Xác minh writer/reader dùng đúng cặp API và cùng schema/version.
- [ ] Chặn chiều dài và số lượng bất hợp lý trước khi cấp phát.
- [ ] Kiểm thử dữ liệu hợp lệ, cắt cụt, sai thứ tự và byte dư.
- [ ] Với định dạng giao tiếp nhiều ngôn ngữ, kiểm tra byte thực tế thay vì chỉ round-trip trong Java.

**Bài tự luyện:** Thiết kế định dạng `[int count][int id][short age][int utf8Length][utf8NameBytes]` cho hai người. Viết bằng `DataOutputStream` và đọc lại bằng `DataInputStream`. Sau đó cố tình đảo thứ tự đọc `age` với `utf8Length`, quan sát vị trí bắt đầu sai và giải thích vì sao lỗi có thể không xuất hiện ngay ở trường đầu tiên.

**Ghi nhớ:** **Thứ tự ghi là một phần của định dạng dữ liệu nhị phân.** Người đọc phải tuân thủ cùng thứ tự, cùng kích thước và cách mã hóa. `DataOutputStream`/`DataInputStream` giúp đọc/ghi byte có cấu trúc, nhưng chính **schema được hai phía thống nhất** mới bảo đảm dữ liệu được hiểu đúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="binary-data-004">Big-endian, signed byte và các thao tác đọc unsigned</a>

<details>
<summary>Xem chi tiết</summary>

# Big-endian, signed byte và đọc giá trị unsigned trong Java

Khi xử lý tệp nhị phân hoặc một giao thức mạng, chương trình không chỉ phải đọc đủ số byte mà còn phải **diễn giải đúng ý nghĩa các bit**. Hai vấn đề dễ gây lỗi là **thứ tự byte** (*endianness*) của số nhiều byte và **cách biểu diễn số có dấu/không dấu** (*signed/unsigned*) trong Java.

Java dùng `byte` có dấu từ -128 đến 127, nhưng một byte vật lý có thể chứa mọi mẫu 8 bit từ `00` đến `FF`. Java cũng cung cấp những API đọc unsigned như `DataInputStream.readUnsignedByte()`, `readUnsignedShort()` và hàm chuyển kiểu như `Byte.toUnsignedInt(...)` hoặc `Integer.toUnsignedLong(...)`. Đồng thời, `DataInputStream` đọc số nguyên nhiều byte theo **big-endian**; còn `ByteBuffer` cho phép chọn big-endian hay little-endian.

Bài này giải thích cơ chế, các thao tác bit an toàn, cách kiểm tra dữ liệu thiếu byte và ví dụ chạy được với Java 17+.

## 1. Một byte thô khác với giá trị byte có dấu

Một byte chứa 8 bit, có 256 mẫu bit từ `00000000` đến `11111111`, tương ứng `0x00` đến `0xFF`. **Mẫu bit không tự mang khái niệm số âm**; đó là ý nghĩa do chương trình gán khi diễn giải.

Trong Java, kiểu nguyên thủy `byte` luôn là **signed 8-bit** theo biểu diễn bù hai (*two's complement*), trong khoảng **-128..127**.

| Mẫu bit / hex | Xem như unsigned 8-bit | Xem như `byte` Java |
|---|---:|---:|
| `00000000` / `0x00` | 0 | 0 |
| `01111111` / `0x7F` | 127 | 127 |
| `10000000` / `0x80` | 128 | -128 |
| `11111111` / `0xFF` | 255 | -1 |

Từ 0 đến 127, signed và unsigned cho cùng giá trị. Từ 128 đến 255, bit cao nhất bằng 1 nên khi giữ trong `byte`, giá trị được biểu diễn là một số âm. Điều này **không có nghĩa các bit đã bị hỏng**: `(byte) 0xFF` vẫn có 8 bit `11111111`.

Để chuyển **mẫu bit của `byte` thành số unsigned 0..255**, có hai cách tương đương:

~~~java
// Mảnh mã minh họa: b là biến byte đã có.
int unsigned1 = b & 0xFF;
int unsigned2 = Byte.toUnsignedInt(b);
~~~

Toán tử `& 0xFF` giữ lại 8 bit thấp sau khi `byte` được mở rộng sang `int`. Ví dụ `b = (byte) 0xFF` có giá trị `-1` khi xem là `byte`, nhưng cả hai biểu thức đều cho `255`.

**Không dùng `Math.abs(b)` để chuyển unsigned**. Với `0xFF`, `Math.abs((int) b)` chỉ được `1`, không phải `255`.

## 2. Vì sao phép chuyển kiểu và dịch bit có thể gây lỗi?

Trong biểu thức Java, `byte` và `short` thường được **nâng kiểu lên `int`** trước khi tính toán. Khi mở rộng một giá trị signed âm, Java thực hiện *sign extension*: bit dấu được kéo dài sang các bit cao.

Ví dụ:

~~~java
byte b = (byte) 0x80;   // -128
int signed = b;         // -128
int unsigned = b & 0xFF; // 128
~~~

Biểu diễn khái niệm:

~~~text
Byte gốc:              1000 0000
Mở rộng có dấu sang int: 11111111 11111111 11111111 10000000
Sau phép AND 0xFF:      00000000 00000000 00000000 10000000
~~~

Nếu ghép nhiều byte bằng toán tử dịch trái mà **không mask**, các bit dấu có thể lan vào phần cao hơn và làm sai kết quả:

~~~java
// Sai khi high có bit 7 bằng 1:
int wrong = (high << 8) | low;

// Đúng cho hai byte nguồn theo big-endian:
int unsigned16 = ((high & 0xFF) << 8) | (low & 0xFF);
~~~

Đây là mảnh mã minh họa, với `high` và `low` là `byte`. Trong `wrong`, nếu `high` âm thì bit cao của `int` có thể bị bật sai; nếu `low` âm, phép OR cũng có thể kéo các bit 1 ra khỏi 8 bit thấp. Mask từng byte **trước khi dịch/OR** làm rõ rằng hai byte nguồn là các giá trị 0..255.

### Khi ghép bốn byte thành unsigned 32-bit

Một giá trị unsigned 32-bit có thể lên đến **4.294.967.295**, lớn hơn giới hạn `int` là 2.147.483.647. Muốn kết quả số học luôn không âm, dùng `long` và mask từng byte:

~~~java
// Mảnh mã minh họa: a, b, c, d là bốn biến byte theo thứ tự big-endian.
long unsigned32 =
        ((long) (a & 0xFF) << 24)
      | ((long) (b & 0xFF) << 16)
      | ((long) (c & 0xFF) << 8)
      |  (long) (d & 0xFF);
~~~

Hãy cast sang `long` trước phép dịch khi cần biểu diễn số unsigned 32-bit dưới dạng số không âm; nếu ghép vào `int`, mẫu bit vẫn đúng nhưng số Java có thể âm. Có thể chuyển một `int` đã đọc thành unsigned 32-bit bằng `Integer.toUnsignedLong(value)`.

## 3. Big-endian và little-endian là thứ tự lưu byte

**Endianness** quy định thứ tự các **byte** trong một giá trị nhiều byte (như `short`, `int`, `long`). Nó không thay đổi thứ tự bit bên trong từng byte.

Giả sử giá trị 32-bit là `0x12345678`:

| Kiểu | Các byte theo thứ tự xuất hiện trong file/network |
|---|---|
| **Big-endian** | `12 34 56 78` |
| **Little-endian** | `78 56 34 12` |

- **Big-endian**: byte có trọng số lớn nhất (*most significant byte*) được ghi/đọc trước.
- **Little-endian**: byte có trọng số nhỏ nhất (*least significant byte*) được ghi/đọc trước.

Với hai byte `12 34`, nếu đọc unsigned 16-bit theo big-endian thì giá trị là `0x1234 = 4660`; nếu hiểu theo little-endian thì là `0x3412 = 13330`.

**Signedness và endianness là hai vấn đề độc lập**:

- Signed/unsigned quy định cách diễn giải bit cao nhất và miền giá trị.
- Big/little-endian quy định thứ tự byte khi dựng một giá trị **nhiều byte**.
- Một byte đơn lẻ không có vấn đề endianness; chỉ có mẫu 8 bit và cách diễn giải signed/unsigned.

Khi đọc file nhị phân, **không được tự suy đoán** endianness từ máy đang chạy. Quy tắc đúng phải lấy từ định dạng file hoặc giao thức. Trong nhiều giao thức mạng, thứ tự big-endian còn được gọi là *network byte order*, nhưng không phải giao thức nào cũng mặc nhiên dùng big-endian.

## 4. InputStream.read() đã trả về unsigned 8-bit — cần phân biệt EOF

`InputStream.read()` trả về kiểu `int`, có hai loại kết quả:

- `0..255`: giá trị của một byte vừa đọc thành công, đã hiểu theo dạng **unsigned**.
- `-1`: không còn byte (EOF, *end of file/stream*).

Do đó, khi đọc byte `0xFF`, `read()` trả **255**, không trả `-1`. Nếu ép ngay kết quả vào `byte` rồi so sánh với `-1`, chương trình có thể **nhầm dữ liệu hợp lệ `0xFF` là EOF**.

### Ví dụ Java 17+: signed byte, unsigned byte và EOF

Tạo file **`SignedUnsignedByteDemo.java`**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class SignedUnsignedByteDemo {
    public static void main(String[] args) throws IOException {
        byte[] data = {0x7F, (byte) 0x80, (byte) 0xFF};

        for (byte value : data) {
            System.out.printf("signed=%d unsigned=%d%n",
                    value, Byte.toUnsignedInt(value));
        }

        try (InputStream in = new ByteArrayInputStream(data)) {
            int value;
            while ((value = in.read()) != -1) {
                System.out.println("read()=" + value);
            }
            System.out.println("EOF=" + in.read());
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac -encoding UTF-8 SignedUnsignedByteDemo.java
java SignedUnsignedByteDemo
~~~

Stdout:

~~~text
signed=127 unsigned=127
signed=-128 unsigned=128
signed=-1 unsigned=255
read()=127
read()=128
read()=255
EOF=-1
~~~

Điểm chính:

1. Trong mảng `byte[]`, hai byte `0x80` và `0xFF` có giá trị signed âm.
2. `Byte.toUnsignedInt(...)` trả giá trị tương ứng 128 và 255, giữ nguyên mẫu bit.
3. `InputStream.read()` đã trả unsigned 8-bit dạng `int`; `-1` chỉ là EOF.
4. Vòng lặp so sánh `-1` **trước khi ép kiểu hoặc diễn giải**. Không dùng `byte value = (byte) in.read()` làm điều kiện hết dữ liệu.

Với `InputStream.read(byte[] buffer)`, **giá trị trả về** là số byte đọc được (hoặc `-1` khi EOF), còn **từng phần tử `buffer[i]` vẫn là `byte` có dấu**. Muốn tính unsigned từ các phần tử mảng, vẫn dùng `buffer[i] & 0xFF`.

## 5. DataInputStream đọc số nhiều byte theo big-endian

`java.io.DataInputStream` bọc một `InputStream` để đọc dữ liệu nhị phân có kiểu. Theo hợp đồng của các phương thức đọc số nhiều byte, nó sử dụng **big-endian**, độc lập với CPU và hệ điều hành.

| Phương thức | Số byte đọc | Kiểu kết quả | Miền giá trị hoặc ý nghĩa |
|---|---:|---|---|
| `readByte()` | 1 | `byte` | signed -128..127 |
| `readUnsignedByte()` | 1 | `int` | unsigned 0..255 |
| `readShort()` | 2 | `short` | signed -32768..32767, big-endian |
| `readUnsignedShort()` | 2 | `int` | unsigned 0..65535, big-endian |
| `readChar()` | 2 | `char` | đơn vị UTF-16 không dấu 0..65535, big-endian |
| `readInt()` | 4 | `int` | signed 32-bit, big-endian |
| `readLong()` | 8 | `long` | signed 64-bit, big-endian |
| `readFloat()` | 4 | `float` | mẫu bit IEEE 754 được đọc theo big-endian |
| `readDouble()` | 8 | `double` | mẫu bit IEEE 754 được đọc theo big-endian |

`DataInputStream` **không** có phương thức `readUnsignedInt()` riêng trong Java SE. Cách chuẩn là:

~~~java
// Mảnh mã minh họa: dataIn là DataInputStream đã mở.
long unsigned32 = Integer.toUnsignedLong(dataIn.readInt());
~~~

Mặc dù `readInt()` có thể cho số âm, **32 bit thu được vẫn đúng**. `Integer.toUnsignedLong(...)` diễn giải mẫu 32 bit đó thành giá trị `0..4294967295` trong kiểu `long`.

Tương tự, với một `short` đã có, `Short.toUnsignedInt(value)` chuyển sang `int` không âm trong khoảng `0..65535`.

### Không đọc số nhiều byte như một lần đọc đơn lẻ

Lệnh `readUnsignedShort()` phải lấy đủ **hai byte** theo thứ tự big-endian. Nếu luồng kết thúc sau một byte, phương thức ném `EOFException` (lớp con của `IOException`), thay vì âm thầm trả một số sai. `readInt()` tương tự đòi hỏi đủ bốn byte.

Một thông điệp nhị phân chỉ đọc đúng khi **hai phía đồng ý** về số byte, thứ tự byte, signedness và ngữ nghĩa từng trường; dùng đúng method nhưng sai schema vẫn giải mã sai.

## 6. Ví dụ Java 17+: DataOutputStream + DataInputStream

`DataOutputStream` ghi các kiểu nguyên thủy ra byte theo thứ tự tương thích với `DataInputStream` (big-endian cho số nhiều byte). Ví dụ sau dùng luồng trong bộ nhớ để quan sát chính xác từng byte.

Tạo file **`BigEndianDataDemo.java`**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;

public class BigEndianDataDemo {
    public static void main(String[] args) throws IOException {
        byte[] bytes;

        try (ByteArrayOutputStream memory = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(memory)) {
            out.writeShort(0x1234);
            out.writeInt(0x89ABCDEF);
            out.writeByte(0xFF);
            out.flush();
            bytes = memory.toByteArray();
        }

        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            if (hex.length() > 0) {
                hex.append(' ');
            }
            hex.append(String.format(Locale.ROOT, "%02X", b & 0xFF));
        }
        System.out.println("bytes=" + hex);

        try (DataInputStream in =
                     new DataInputStream(new ByteArrayInputStream(bytes))) {
            int unsigned16 = in.readUnsignedShort();
            int signed32 = in.readInt();
            int unsigned8 = in.readUnsignedByte();

            System.out.println("u16=" + unsigned16);
            System.out.println("i32=" + signed32);
            System.out.println("u32=" + Integer.toUnsignedLong(signed32));
            System.out.println("u8=" + unsigned8);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 BigEndianDataDemo.java
java BigEndianDataDemo
~~~

Stdout chính xác:

~~~text
bytes=12 34 89 AB CD EF FF
u16=4660
i32=-1985229329
u32=2309737967
u8=255
~~~

Phân tích:

- `writeShort(0x1234)` tạo hai byte `12 34`. `readUnsignedShort()` trả `4660`.
- `writeInt(0x89ABCDEF)` tạo `89 AB CD EF`. Bit dấu 32-bit là 1 nên `readInt()` trả số signed âm, nhưng `Integer.toUnsignedLong(...)` cho số unsigned `2309737967`.
- `writeByte(0xFF)` chỉ ghi 8 bit thấp. `readUnsignedByte()` trả `255`.
- `String.format("%02X", b & 0xFF)` in byte ở dạng hex hai chữ số; `& 0xFF` giúp tránh nhầm sign extension.
- `ByteArrayOutputStream` giữ dữ liệu trong RAM; ví dụ này phục vụ học tập. Với file hoặc socket thật, hãy quản lý vòng đời stream, kích thước dữ liệu và lỗi I/O tương ứng.

**Không ghi và đọc cùng đối tượng stream theo kiểu tự đoán định dạng**: `DataOutputStream.writeUTF/readUTF` có quy ước modified UTF-8 và tiền tố độ dài riêng, **không** tương đương với việc ghi một chuỗi UTF-8 thô. Học endianness qua các trường số không có nghĩa văn bản nào cũng theo cùng kiểu mã hóa.

## 7. ByteBuffer: mặc định big-endian nhưng có thể đổi thứ tự

`java.nio.ByteBuffer` cung cấp phương thức `getShort()`, `getInt()`, `getLong()` và các phương thức `put...` tương ứng. Nó có một thuộc tính `ByteOrder` để quy định thứ tự byte của các phép đọc/ghi **nhiều byte**.

- Một `ByteBuffer` mới có byte order mặc định là **`ByteOrder.BIG_ENDIAN`**.
- Có thể đặt lại bằng `buffer.order(ByteOrder.LITTLE_ENDIAN)`.
- Thuộc tính order áp dụng cho các phép truy cập đa byte như `getShort/getInt/putInt`; nó **không** tự đảo byte khi gọi `get()` lấy một byte.
- `getInt()` vẫn trả về **signed `int`** dù thứ tự byte nào; nếu giá trị cần hiểu unsigned, dùng `Integer.toUnsignedLong(...)`.

### Ví dụ Java 17+: cùng bốn byte, hai giá trị khác nhau

Tạo file **`ByteOrderDemo.java`**:

~~~java
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ByteOrderDemo {
    public static void main(String[] args) {
        byte[] data = {0x01, 0x02, 0x03, 0x04};

        int big = ByteBuffer.wrap(data)
                .order(ByteOrder.BIG_ENDIAN)
                .getInt();

        int little = ByteBuffer.wrap(data)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt();

        System.out.println("big=" + big);
        System.out.println("little=" + little);
    }
}
~~~

~~~bash
javac -encoding UTF-8 ByteOrderDemo.java
java ByteOrderDemo
~~~

Stdout:

~~~text
big=16909060
little=67305985
~~~

Giải thích:

- Big-endian diễn giải `01 02 03 04` thành `0x01020304`, tức `16909060`.
- Little-endian diễn giải cùng dãy byte thành `0x04030201`, tức `67305985`.
- Không byte nào bị thay đổi trong mảng `data`; chỉ quy tắc ghép chúng thành `int` khác nhau.

`ByteOrder.nativeOrder()` cho biết thứ tự byte gốc của nền tảng phục vụ các tình huống tương tác bộ nhớ/native. **Đừng dùng nó để quyết định cách đọc định dạng tệp hoặc giao thức**: định dạng mới là thứ quy định big- hay little-endian.

`DataInputStream` không có `order(ByteOrder.LITTLE_ENDIAN)`. Nếu giao thức dùng little-endian, có thể dùng `ByteBuffer` được cấu hình đúng, đọc từng byte rồi ghép thủ công, hoặc sử dụng một thư viện I/O có API little-endian.

## 8. Tự ghép unsigned 16-bit và unsigned 32-bit theo big-endian

Khi một định dạng có cấu trúc byte cụ thể hoặc không muốn dùng `DataInputStream`, có thể ghép bằng phép dịch và OR. Với hai byte `hi`, `lo`, giá trị unsigned 16-bit big-endian là:

\[
(\mathrm{hi}\ \&\ 255)\times 256 + (\mathrm{lo}\ \&\ 255)
\]

Với bốn byte `a, b, c, d`, giá trị unsigned 32-bit big-endian là:

\[
((a\ \&\ 255)\ll 24)\;|\;((b\ \&\ 255)\ll 16)\;|\;((c\ \&\ 255)\ll 8)\;|\;(d\ \&\ 255)
\]

Trong Java cần dùng kiểu `long` nếu muốn in ra giá trị **unsigned 32-bit không âm**. Khi đọc trực tiếp từ `InputStream.read()`, giá trị mỗi byte đã là `0..255`; nhưng vẫn phải kiểm tra EOF (`-1`) **trước khi ghép**. Nếu một byte còn thiếu, không được đưa `-1` vào phép dịch rồi coi là dữ liệu hợp lệ.

### Ví dụ Java 17+: thao tác mask và dịch với unsigned

Tạo file **`UnsignedBitwiseDemo.java`**:

~~~java
public class UnsignedBitwiseDemo {
    public static void main(String[] args) {
        byte high = (byte) 0xAB;
        byte low = (byte) 0xCD;

        int u16 = ((high & 0xFF) << 8) | (low & 0xFF);
        short signed16 = (short) u16;

        System.out.println("u16=" + u16);
        System.out.println("s16=" + signed16);

        byte[] four = {
                (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF
        };

        long u32 = ((long) (four[0] & 0xFF) << 24)
                 | ((long) (four[1] & 0xFF) << 16)
                 | ((long) (four[2] & 0xFF) << 8)
                 |  (long) (four[3] & 0xFF);

        System.out.println("u32=" + u32);
        System.out.println("asInt=" + (int) u32);
    }
}
~~~

~~~bash
javac -encoding UTF-8 UnsignedBitwiseDemo.java
java UnsignedBitwiseDemo
~~~

Stdout:

~~~text
u16=43981
s16=-21555
u32=4294967295
asInt=-1
~~~

Hai byte `AB CD` có **cùng mẫu bit 16-bit**, nhưng `int u16` lưu ý nghĩa unsigned là 43981, còn ép sang `short` signed cho -21555. Bốn byte `FF FF FF FF` đại diện số unsigned 32-bit lớn nhất `4294967295`; ép về `int` giữ 32 bit nhưng diễn giải signed thành -1.

Nếu chỉ cần chuyển đổi mẫu signed 16/32-bit đã có, tránh viết lại toàn bộ bitwise:

~~~java
// Mảnh mã minh họa với short s và int i đã được đọc.
int unsigned16 = Short.toUnsignedInt(s);
long unsigned32 = Integer.toUnsignedLong(i);
~~~

Các hàm chuẩn làm rõ ý định và tránh nhiều lỗi mask/shift.

## 9. Đọc “unsigned” nhiều hơn 32 bit trong Java

Java không có primitive `unsigned byte`, `unsigned short`, `unsigned int` hoặc `unsigned long` độc lập như trong một số ngôn ngữ. Tuy nhiên có thể **giữ cùng mẫu bit** và sử dụng các API diễn giải unsigned.

| Số bit unsigned | Phạm vi | Cách Java biểu diễn phù hợp |
|---|---|---|
| 8 | 0..255 | `int` với `Byte.toUnsignedInt(byte)` hoặc `DataInputStream.readUnsignedByte()` |
| 16 | 0..65535 | `int` với `Short.toUnsignedInt(short)` hoặc `DataInputStream.readUnsignedShort()` |
| 32 | 0..4294967295 | `long` với `Integer.toUnsignedLong(int)` |
| 64 | 0..18446744073709551615 | Mẫu bit trong `long` kèm các hàm unsigned, hoặc `BigInteger` nếu cần giá trị toán học vượt `Long.MAX_VALUE` |

Với dữ liệu unsigned 64-bit:

- `DataInputStream.readLong()` lấy đủ 8 byte big-endian, trả kiểu `long` signed mang **đúng mẫu 64 bit**.
- `Long.toUnsignedString(value)` hiển thị giá trị đó dưới dạng số thập phân unsigned.
- `Long.compareUnsigned(a, b)` và `Long.divideUnsigned(...)`/`remainderUnsigned(...)` hỗ trợ so sánh và phép chia unsigned.
- `BigInteger` có thể biểu diễn đầy đủ miền 0..2^64-1 khi ứng dụng cần tính toán ngoài giới hạn signed `long`. Việc chuyển byte sang `BigInteger` phải xác định rõ **dấu, độ dài và byte order**; constructor `new BigInteger(1, magnitudeBigEndian)` nhận mảng độ lớn dạng big-endian và ép dấu dương.

Không tự cộng `2^64` bằng một biểu thức `long`, vì số đó không biểu diễn được trong signed `long`. Tránh thao tác ngầm định overflow như thể nó là số nguyên dương không giới hạn.

## 10. Những lỗi đọc nhị phân cần tránh

### 10.1. Nhầm `0xFF` với EOF

Sai:

~~~java
// Mảnh mã minh họa: sai vì ép int trả về từ read() thành byte trước.
byte value = (byte) input.read();
if (value == -1) {
    // Không phân biệt được byte 0xFF với EOF.
}
~~~

Đúng:

~~~java
// Mảnh mã minh họa: input là InputStream đang mở.
int value = input.read();
if (value == -1) {
    // EOF.
} else {
    int unsigned8 = value; // 0..255
}
~~~

### 10.2. Dịch `byte` âm trước khi mask

Sai:

~~~java
// Mảnh mã minh họa: b0 và b1 là byte có thể âm.
int combined = (b0 << 8) | b1;
~~~

Đúng:

~~~java
int combined = ((b0 & 0xFF) << 8) | (b1 & 0xFF);
~~~

Nếu cần bốn byte thành số unsigned 32-bit không âm, dùng `long` như ví dụ phần 8.

### 10.3. Đọc unsigned bằng kiểu quá nhỏ

Sai:

~~~java
// Mảnh mã minh họa: readUnsignedShort() có thể trả 65535.
short size = (short) dataIn.readUnsignedShort();
~~~

Với giá trị vượt 32767, biến `size` sẽ âm do ép kiểu `short`. Đúng là giữ trong `int`:

~~~java
int size = dataIn.readUnsignedShort();
~~~

### 10.4. Dùng đúng kiểu nhưng sai endian

Nếu file chứa hai byte `34 12` được định nghĩa là số little-endian `0x1234`, gọi `readUnsignedShort()` của `DataInputStream` sẽ cho `0x3412`, **không** cho giá trị mong đợi 4660. Cần đọc little-endian đúng hợp đồng nguồn.

### 10.5. Cho rằng một lần `read(byte[])` luôn đủ độ dài

`InputStream.read(byte[])` có thể trả ít byte hơn kích thước mảng dù chưa tới EOF. Nếu dữ liệu cố định cần đủ N byte, dùng `DataInputStream.readFully(byte[])`, `readNBytes(...)` với kiểm tra số byte nhận được, hoặc lặp cho đến khi đủ. `readFully(...)` ném `EOFException` khi không thể đọc đủ byte yêu cầu.

`DataInputStream.readInt()`/`readUnsignedShort()` tự đọc theo số byte cần thiết, không phải một lệnh `read(byte[])` thiếu kiểm tra.

## 11. Khi nào dùng API nào?

| Nhu cầu | Lựa chọn thực tế | Lưu ý |
|---|---|---|
| Lấy từng byte từ luồng | `InputStream.read()` | Trả `0..255` hoặc `-1` khi EOF |
| Diễn giải một `byte` signed theo unsigned | `Byte.toUnsignedInt(b)` hoặc `b & 0xFF` | Không đổi bit |
| Đọc unsigned 16-bit big-endian | `DataInputStream.readUnsignedShort()` | Trả `int`, ném lỗi nếu thiếu 2 byte |
| Đọc unsigned 32-bit big-endian | `Integer.toUnsignedLong(in.readInt())` | Trả `long` 0..4294967295 |
| Đọc nhiều kiểu big-endian liên tiếp | `DataInputStream` | Cùng schema với phía ghi |
| Đọc int/short có thể chọn endian | `ByteBuffer.order(...).getInt()/getShort()` | Nhớ chuyển unsigned nếu cần |
| Xử lý một định dạng nhị phân đặc biệt | Đọc các byte và mask/shift theo tài liệu | Phải kiểm tra độ dài, EOF, thứ tự |
| Unsigned 64-bit | `Long.toUnsignedString` hoặc `BigInteger` | Không có primitive số dương đủ toàn miền |

### Checklist khi thiết kế hoặc phân tích định dạng nhị phân

1. **Độ dài** trường là bao nhiêu byte? Có tiền tố độ dài hoặc padding không?
2. **Thứ tự byte** là big-endian hay little-endian?
3. **Có dấu hay không dấu**? Miền giá trị hợp lệ là gì?
4. **Kiểu lưu kết quả** có đủ miền không? Ví dụ unsigned 16-bit cần `int`, unsigned 32-bit không âm cần `long`.
5. **Nguồn đã đọc đủ** chưa? Cần xử lý `-1`/`EOFException` thay vì đưa giá trị thiếu vào phép bit.
6. **Kích thước đầu vào có được giới hạn** không? Đừng dùng một trường length từ file không đáng tin để cấp phát mảng tùy ý.
7. **Byte thô khác văn bản**: các phương thức số của `DataInputStream` không thay thế quy tắc charset cho chuỗi ký tự.

## 12. Ghi nhớ trọng tâm

- `byte` Java là **signed -128..127**, còn mẫu 8 bit của dữ liệu vẫn có thể được hiểu **unsigned 0..255** bằng `b & 0xFF` hoặc `Byte.toUnsignedInt(b)`.
- `InputStream.read()` trả `int 0..255` cho byte hợp lệ và `-1` cho **EOF**; tuyệt đối không ép về `byte` trước khi kiểm tra EOF.
- **Big-endian** đặt byte có trọng số lớn nhất trước; **little-endian** đặt byte có trọng số nhỏ nhất trước. Endianness không liên quan đến dấu của một byte riêng lẻ.
- `DataInputStream`/`DataOutputStream` dùng **big-endian** khi đọc/ghi số nhiều byte. `ByteBuffer` mặc định big-endian và cho phép chọn `ByteOrder.LITTLE_ENDIAN`.
- `readUnsignedByte()` trả `int`; `readUnsignedShort()` trả `int`. Với unsigned 32-bit, dùng `Integer.toUnsignedLong(readInt())`.
- Khi ghép byte, **mask trước khi dịch/OR**, giữ kết quả trong kiểu đủ rộng và kiểm tra đủ số byte đầu vào.
- Bất cứ giá trị nào chỉ đúng khi **độ dài, endian, signedness và schema** khớp với tài liệu định dạng/giao thức.

### Tài liệu chính thức

- [Java SE 17 — Byte](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Byte.html)
- [Java SE 17 — DataInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataInputStream.html)
- [Java SE 17 — DataOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataOutputStream.html)
- [Java SE 17 — ByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html)
- [Java SE 17 — Integer.toUnsignedLong](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html#toUnsignedLong(int))


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="binary-data-005">readFully khác read và cách xử lý EOFException</a>

<details>
<summary>Xem chi tiết</summary>

# readFully khác read và cách xử lý EOFException trong Java I/O

Trong xử lý **dữ liệu nhị phân**, không thể mặc định một lệnh đọc sẽ nhận đủ số byte cần thiết. Một header dài 4 byte, một bản ghi cố định 16 byte hay payload có độ dài khai báo 1.024 byte đều cần được đọc **đủ chính xác số byte yêu cầu** trước khi giải mã thành cấu trúc hoàn chỉnh. Đây là khác biệt trọng tâm giữa `read(...)` và `readFully(...)`.

- **`read(...)`**: thực hiện một lượt đọc và **trả về số byte thực tế nhận được**. Con số này có thể nhỏ hơn độ dài yêu cầu; `-1` thường biểu thị EOF.
- **`DataInputStream.readFully(...)`**: đọc lặp cho tới khi **đủ số byte yêu cầu**, hoặc ném **`EOFException`** nếu luồng kết thúc trước khi đủ. Phương thức trả về `void`, không trả số byte.
- **`EOFException`**: báo “đã hết dữ liệu trước khi hoàn thành cấu trúc bắt buộc”, cần xử lý theo ý nghĩa nghiệp vụ: bản tin bị cắt, file thiếu, kết nối đóng quá sớm hoặc thiếu dữ liệu theo định dạng.

Mục tiêu của bài là biết khi nào nên chấp nhận **short read**, khi nào cần đọc **exact length**, và làm sao tránh sử dụng dữ liệu thiếu sau `EOFException`.

## 1. Phân biệt dữ liệu “đang có” và “đủ theo định dạng”

Giả sử một phần của file nhị phân khai báo rằng payload kế tiếp **phải dài 5 byte**. Tầng I/O chỉ chuyển về 2 byte ở lượt đầu:

```text
Yêu cầu:  5 byte dữ liệu

Nguồn:    A B C D E
read #1:  A B           -> trả về 2
read #2:      C D       -> trả về 2
read #3:          E     -> trả về 1
read #4: EOF

read(buffer[5]) một lần:    có thể chỉ nhận A B
readFully(buffer[5]):       tiếp tục tới khi nhận đủ A B C D E
```

Nếu dùng `read(buffer)` rồi giả định buffer đã đủ 5 byte, ba vị trí còn lại có thể vẫn chứa giá trị mặc định hoặc dữ liệu cũ. Nếu có bytes bị thiếu thực sự, `readFully` phải báo lỗi. **Một lượt đọc ít hơn kích thước buffer không có nghĩa đã EOF**: một luồng mạng có thể cung cấp dữ liệu thành nhiều mảnh.

| Tiêu chí | `read(byte[])` | `DataInputStream.readFully(byte[])` |
| --- | --- | --- |
| Mục đích | Nhận số byte hiện có của một lượt đọc | Nhận **đúng N byte** theo yêu cầu |
| Số lượt đọc tầng dưới | Không được yêu cầu đọc tới khi lấp đầy | Tự đọc tiếp nếu lượt trước ngắn |
| Giá trị trả về | `int` = số byte, hoặc `-1` khi EOF | `void` |
| Nguồn hết trước khi đủ N | Có thể trả `-1` / trả ít byte | Ném `EOFException` |
| Cần tự kiểm tra số byte? | **Có** | Thành công nghĩa là đủ N |
| Tình huống thích hợp | Sao chép luồng tới EOF, xử lý chunk | Đọc header, record, payload biết trước độ dài |

Các quy tắc trên xét buffer có độ dài **dương**. Yêu cầu đọc `0` byte có trường hợp đặc biệt: `readFully(b, off, 0)` hoàn thành ngay cả khi luồng ở EOF; còn `InputStream.read(b, off, 0)` trả `0`.

## 2. `read(byte[])` trả về điều gì?

`InputStream.read(byte[])` và `DataInputStream.read(byte[])` có chữ ký:

```java
int bytesRead = input.read(buffer);
```

Đoạn minh họa giả định `input` là `InputStream` hoặc `DataInputStream` đã có và `buffer` là `byte[]`.

Với `buffer.length > 0` và một `InputStream` tuân thủ hợp đồng thông thường, kết quả là:

- `1..buffer.length`: số byte hợp lệ **ở lần gọi vừa rồi**;
- `-1`: EOF, không còn byte để đọc;
- `IOException`: lỗi I/O.

Ví dụ `byte[] buffer = new byte[8192]` không đảm bảo `read(buffer) == 8192`. Nguồn có thể trả 1, 2 hay vài trăm byte dù còn dữ liệu trong tương lai. **Không dùng một lượt `read` để kết luận đã nhận đủ cả bản tin.**

### Khi không biết trước tổng độ dài: dùng vòng lặp theo số byte

```java
byte[] buffer = new byte[8192];
int n;

while ((n = input.read(buffer)) != -1) {
    output.write(buffer, 0, n);
}
```

Đây là đoạn minh họa với `input` và `output` đã mở. `output.write(buffer, 0, n)` ghi **đúng `n` byte vừa đọc**, tránh ghi rác từ các vị trí chưa được điền hoặc còn dữ liệu cũ. Lặp đến EOF phù hợp để **sao chép toàn bộ luồng**; nếu protocol xác định độ dài riêng cho từng message, EOF không thay thế được việc kiểm soát ranh giới message.

## 3. `DataInputStream.readFully(...)` hoạt động ra sao?

`DataInputStream` triển khai giao diện `DataInput`, cung cấp các phương thức đọc dữ liệu nhị phân có cấu trúc. Hai overload chính:

```java
dataInput.readFully(buffer);
dataInput.readFully(buffer, offset, length);
```

Các đoạn trên giả định `dataInput` là `DataInputStream` và `buffer` là `byte[]`.

- `readFully(buffer)` yêu cầu điền **toàn bộ** `buffer.length` byte.
- `readFully(buffer, offset, length)` yêu cầu điền đúng `length` byte, bắt đầu ở chỉ số `offset`. Những phần tử ngoài đoạn đích không phải vùng cần ghi.
- Nếu **đọc đủ**, phương thức hoàn thành bình thường, không trả số byte. **Không cần** gọi thêm một `read` để “xác nhận đủ”.
- Nếu **EOF trước khi đủ**, nó ném `EOFException`, một lớp con của `IOException`.
- Nếu có lỗi I/O khác, nó có thể ném `IOException` tương ứng.
- Nếu tham số `offset` hoặc `length` không hợp lệ, phương thức báo lỗi chỉ số; đừng thay thế lỗi lập trình này bằng nhánh xử lý EOF.

Ví dụ cần nhận đúng 8 byte đầu của một record:

```java
byte[] header = new byte[8];
dataInput.readFully(header);  // Chỉ tiếp tục khi đã có đủ 8 byte.
parseHeader(header);         // parseHeader là hàm nghiệp vụ minh họa.
```

**Điểm then chốt:** không gọi `parseHeader` nếu `readFully` ném `EOFException`. Khi thiếu byte, dữ liệu trong buffer có thể bị **thay đổi một phần**. Vì `readFully` trả `void`, nó không thông báo đã điền được bao nhiêu byte trước lỗi; không coi buffer thiếu là một header hợp lệ.

### Nguyên lý vòng đọc bên trong

Có thể hình dung `readFully(b, off, len)` thực hiện logic tương tự:

```java
// Pseudocode để giải thích ý tưởng, không phải mã nguồn thư viện.
int total = 0;
while (total < len) {
    int n = input.read(b, off + total, len - total);
    if (n == -1) {
        throw new EOFException();
    }
    total += n;
}
```

Đây là **pseudocode** với `input` giả định tuân thủ hợp đồng đọc buffer dương; không dùng để thay thế implementation của JDK hay bỏ qua xử lý các stream tùy chỉnh vi phạm hợp đồng trả `0` liên tục. Mục tiêu là hiểu vì sao một lần `read` ngắn **không** khiến `readFully` bỏ cuộc, nhưng EOF trước khi điền đủ **là lỗi**.

## 4. Ví dụ Java 17+ độc lập: short read, readFully và EOFException

Chương trình sau cố ý bọc `InputStream` để **mỗi lần đọc vào mảng nhận tối đa 2 byte**, dù buffer được yêu cầu lớn hơn. Nhờ vậy kiểm tra được hành vi ngay cả khi một `ByteArrayInputStream` bình thường có thể cung cấp đủ nhiều byte trong một lượt.

Lưu đúng tên tệp **`ReadFullyDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ReadFullyDemo {

    public static void main(String[] args) throws IOException {
        demonstrateShortRead();
        demonstrateTruncatedInput();
        demonstrateLengthPrefixedFrame();
    }

    private static void demonstrateShortRead() throws IOException {
        byte[] source = "ABCDE".getBytes(StandardCharsets.US_ASCII);
        byte[] result = new byte[5];

        try (DataInputStream input = new DataInputStream(
                new TwoByteInputStream(new ByteArrayInputStream(source)))) {

            int first = input.read(result);
            System.out.println("First read: " + first);

            input.readFully(result, first, result.length - first);
            System.out.println("Reconstructed: " +
                    new String(result, StandardCharsets.US_ASCII));
            System.out.println("EOF after data: " + input.read());
        }
    }

    private static void demonstrateTruncatedInput() throws IOException {
        byte[] shortSource = {10, 20, 30};
        byte[] requestedFive = new byte[5];
        Arrays.fill(requestedFive, (byte) -1);

        try (DataInputStream input = new DataInputStream(
                new TwoByteInputStream(
                        new ByteArrayInputStream(shortSource)))) {

            try {
                input.readFully(requestedFive);
                System.out.println("Unexpected success");
            } catch (EOFException e) {
                System.out.println("Truncated: " +
                        e.getClass().getSimpleName());
            }
        }

        // Chỉ minh họa buffer đã bị điền dở, tuyệt đối
        // KHÔNG xử lý buffer này như một record hợp lệ.
        System.out.println("Partial buffer (invalid): " +
                Arrays.toString(requestedFive));
    }

    private static void demonstrateLengthPrefixedFrame()
            throws IOException {
        byte[] message = "Xin chào".getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(wire)) {
            output.writeInt(message.length);
            output.write(message);
        }

        try (DataInputStream input = new DataInputStream(
                new TwoByteInputStream(
                        new ByteArrayInputStream(wire.toByteArray())))) {

            int declaredLength = input.readInt();

            if (declaredLength < 0 || declaredLength > 1024) {
                throw new IOException("Invalid frame length");
            }

            byte[] payload = new byte[declaredLength];
            input.readFully(payload);

            System.out.println("Frame bytes: " + payload.length);
            System.out.println("Frame text: " +
                    new String(payload, StandardCharsets.UTF_8));
        }
    }

    private static final class TwoByteInputStream
            extends FilterInputStream {

        private TwoByteInputStream(InputStream source) {
            super(source);
        }

        @Override
        public int read(byte[] target, int offset, int length)
                throws IOException {
            return super.read(
                    target, offset, Math.min(length, 2));
        }
    }
}
```

Biên dịch/chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 ReadFullyDemo.java
java ReadFullyDemo
```

**Stdout dự kiến:**

```text
First read: 2
Reconstructed: ABCDE
EOF after data: -1
Truncated: EOFException
Partial buffer (invalid): [10, 20, 30, -1, -1]
Frame bytes: 9
Frame text: Xin chào
```

Diễn giải ba trường hợp:

1. **Short read:** nguồn có đủ `ABCDE` nhưng giới hạn chỉ cho đọc tối đa 2 byte/lượt. `read(result)` trả `2`; gọi `readFully(result, 2, 3)` tiếp tục đọc nốt `CDE`. Lúc này có thể xử lý đủ 5 byte.
2. **Truncated:** nguồn chỉ có `10, 20, 30` nhưng đích đòi đủ 5 byte. `readFully(requestedFive)` điền được vài byte rồi chạm EOF và ném `EOFException`. Nội dung buffer sau lỗi có thể đã thay đổi; hiển thị buffer **chỉ để thấy dữ liệu điền dở**, không phải “kết quả gần đúng” được phép sử dụng.
3. **Length-prefixed frame:** `DataOutputStream.writeInt(message.length)` ghi 4 byte độ dài theo quy ước big-endian của `DataOutput`, sau đó ghi payload UTF-8. `DataInputStream.readInt()` đọc đúng số byte cần cho một `int`, rồi `readFully(payload)` nhận đủ số byte được khai báo. Chuỗi `"Xin chào"` có 9 byte UTF-8 và được giải mã đúng sau khi nhận đầy đủ.

Wrapper `TwoByteInputStream` là công cụ giả lập nguồn trả ít byte; code không nên dựa vào số byte mỗi lần đọc khi vận hành với mạng hay file thực tế.

## 5. Hiểu đúng `EOFException`

`EOFException` kế thừa `IOException`:

```text
Exception
  -> IOException
       -> EOFException
```

Sự khác biệt cốt lõi:

| Thao tác | Khi EOF | Diễn giải |
| --- | --- | --- |
| `InputStream.read()` | Trả `-1` | Hết byte theo API đọc đơn |
| `InputStream.read(byte[])` (buffer dương) | Trả `-1` nếu đã hết trước khi đọc thêm | Kết thúc một luồng thông thường |
| `DataInputStream.readFully(byte[N])` | Ném `EOFException` nếu thiếu để đủ N | Không đủ dữ liệu cho cấu trúc bắt buộc |
| `DataInputStream.readInt()` | Ném `EOFException` nếu thiếu byte của `int` | Không thể hình thành một số `int` hoàn chỉnh |
| `RandomAccessFile.readFully(byte[N])` | Ném `EOFException` nếu thiếu N byte | Không đủ dữ liệu trong tệp |

**EOF không luôn là lỗi.** Đọc luồng cho tới khi hết là nghiệp vụ hợp lệ: `read(...)` trả `-1` là tín hiệu bình thường. Nhưng khi một record **bắt buộc** phải dài N byte, gặp EOF trước N byte nghĩa là record thiếu; `readFully` chủ động ném exception thay vì trả một bộ dữ liệu ngắn.

### Catch đúng lớp và đúng phạm vi

```java
try {
    input.readFully(record);
    processCompleteRecord(record);
} catch (EOFException truncated) {
    throw new IOException(
            "Record bị cắt trước khi đủ " + record.length + " byte",
            truncated);
}
```

Đây là minh họa cần biến `input` (kiểu `DataInputStream`), `record` (mảng byte), và hàm `processCompleteRecord`. Việc bọc ngoại lệ giúp tầng gọi biết rằng một record cụ thể không hoàn chỉnh, đồng thời giữ nguyên nguyên nhân bằng `cause`.

Nếu đây là protocol có nhiều record, **không tiếp tục đọc record tiếp theo sau EOFException như thể record cũ đã thành công**. Kết nối đã kết thúc trước khi đủ frame; thường cần coi frame là lỗi và dừng đọc/đóng hoặc báo lỗi.

### EOF sạch ở ranh giới record khác với record bị cắt

Giả sử protocol có header 4 byte chứa độ dài. Khi `readInt()` gặp `EOFException`, có hai khả năng:

- **EOF sạch:** không có byte nào của header mới, nghĩa là hết luồng tại đúng ranh giới record.
- **Header bị cắt:** có 1–3 byte của header rồi mới hết, dữ liệu sai định dạng.

Chỉ `catch (EOFException)` từ `readInt()` **không đủ thông tin phân biệt** hai tình huống, vì cả hai đều ném cùng loại ngoại lệ. Để phân biệt, protocol/decoder cần quản lý ranh giới rõ ràng, hoặc đọc byte đầu header riêng trước rồi buộc đọc đủ 3 byte còn lại. Không tự coi mọi `EOFException` của `readInt` là “hết file hợp lệ”.

Ví dụ logic riêng cho một luồng các bản ghi có header dài 4 byte:

```java
int first = input.read();

if (first == -1) {
    // EOF trước byte đầu header: hết luồng sạch ở ranh giới record.
    return;
}

byte[] restOfHeader = new byte[3];
input.readFully(restOfHeader); // Thiếu 1-3 byte: lỗi truncated header.

// Ghép header theo chuẩn giao thức (big-endian) nếu cần.
int length = ((first & 0xFF) << 24)
        | ((restOfHeader[0] & 0xFF) << 16)
        | ((restOfHeader[1] & 0xFF) << 8)
        | (restOfHeader[2] & 0xFF);
```

Đây là **đoạn minh họa** đặt trong một phương thức được phép `return`, với `input` là `DataInputStream`. Định dạng header ví dụ dùng số nguyên 32-bit big-endian như `DataInputStream.readInt()`. Cần tiếp tục **kiểm tra `length`** theo giới hạn của protocol trước khi cấp phát buffer; nếu payload thiếu byte, `readFully` sẽ ném `EOFException`.

## 6. Trường hợp có offset và độ dài

`readFully(buffer, off, len)` chỉ yêu cầu điền vào vùng `[off, off + len)`. Ví dụ một mảng 12 byte, muốn đọc 4 byte dữ liệu từ chỉ số 5:

```java
byte[] frame = new byte[12];
input.readFully(frame, 5, 4);
```

Phương thức yêu cầu 4 byte mới cho các vị trí `5, 6, 7, 8`. Nếu thành công, những byte ngoài đoạn chỉ định không phải dữ liệu mới của lần gọi này.

Khi tự kiểm tra dữ liệu đã đọc, đừng nhầm tham số `len` là *vị trí kết thúc*: `off=5, len=4` là bốn byte từ chỉ số 5, không phải đoạn từ 5 đến 4.

Với `read(buffer, off, len)`, phương thức trả `n` byte vừa đọc trong đoạn đó; để lấp đầy một phần bằng vòng đọc thủ công, cần tăng offset theo `n` **chứ không theo `len`**.

## 7. Nếu không dùng `readFully`, tự đọc đủ N byte thế nào?

Khi bạn chỉ có `InputStream`, có thể tự viết hàm đọc đúng số byte yêu cầu. Ví dụ dưới đây hoạt động với `InputStream` tuân thủ hợp đồng thông thường cho buffer có độ dài dương:

```java
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

static void readExactly(InputStream input, byte[] buffer)
        throws IOException {
    int offset = 0;

    while (offset < buffer.length) {
        int n = input.read(
                buffer, offset, buffer.length - offset);

        if (n == -1) {
            throw new EOFException(
                    "Unexpected EOF after " + offset +
                    " of " + buffer.length + " bytes");
        }

        offset += n;
    }
}
```

Đây là phương thức minh họa phải đặt trong một class. `offset` chỉ tăng bằng **số byte thực đọc `n`**. Khi buffer đầy, hàm hoàn thành. Nếu EOF trước khi đầy, hàm ném `EOFException`.

Trong thực tế, `DataInputStream.readFully` đã cung cấp chức năng này cho trường hợp cần nhận đúng N byte. Không nên tự viết lại chỉ vì muốn “tối ưu” khi API chuẩn đã phù hợp.

Với các nguồn I/O tùy chỉnh có thể trả `0` liên tục dù yêu cầu đọc số byte dương (không tuân thủ hợp đồng của `InputStream`), một vòng lặp tự viết thiếu kiểm soát sẽ có nguy cơ quay vô hạn; cần xử lý theo contract cụ thể của nguồn. Mã học tập ở trên dựa vào hợp đồng chuẩn.

## 8. So với `InputStream.readNBytes` và `readAllBytes`

Từ Java 9, `InputStream` có thêm các API tiện dụng:

| API | Hành vi khi EOF trước số lượng mong muốn |
| --- | --- |
| `readNBytes(int len)` | Trả `byte[]` có thể **ngắn hơn `len`**; người gọi kiểm tra độ dài |
| `readNBytes(byte[] b, int off, int len)` | Trả số byte thực đọc, có thể **ít hơn `len`** |
| `readAllBytes()` | Đọc tất cả byte còn lại đến EOF; không thể bảo đảm cấu trúc N byte nếu không tự đối chiếu |
| `DataInputStream.readFully(byte[] b)` | **Ném `EOFException`** nếu chưa đủ đúng `b.length` byte |

Nếu muốn chính sách “bắt buộc đủ N”, vẫn có thể dùng `readNBytes` rồi kiểm tra:

```java
byte[] bytes = input.readNBytes(expectedLength);
if (bytes.length != expectedLength) {
    throw new EOFException("Expected " + expectedLength
            + " bytes, got " + bytes.length);
}
```

Đoạn minh họa giả định `input` là `InputStream`, `expectedLength` là số nguyên không âm đã **qua kiểm tra giới hạn**, và đã import `EOFException`. Điểm khác biệt là `readNBytes` **không tự báo EOF thiếu N như `readFully`**, mà để lập trình viên đối chiếu độ dài.

**Không dùng `available()` làm số byte còn lại của file/network stream**: `available()` chỉ cho biết ước lượng byte có thể đọc ngay mà không chặn trong một số tình huống, không phải kích thước bản tin hoặc điều kiện kết thúc chính xác.

## 9. Áp dụng cho file và giao thức có chiều dài khai báo

Bài toán phổ biến: phần đầu của mỗi frame chứa **độ dài payload**, sau đó là đúng số byte payload. Nếu đã đọc và xác thực header, logic điển hình:

```java
int payloadSize = input.readInt();

if (payloadSize < 0 || payloadSize > MAX_PAYLOAD_BYTES) {
    throw new IOException("Payload length out of bounds");
}

byte[] payload = new byte[payloadSize];
input.readFully(payload); // Thiếu byte => EOFException.
handlePayload(payload);
```

Đây là đoạn minh họa chưa độc lập: `input` là `DataInputStream`, `MAX_PAYLOAD_BYTES` là giới hạn tin cậy, và `handlePayload` là hàm ứng dụng.

**Phải xác thực độ dài trước khi cấp phát.** Dữ liệu bên ngoài có thể khai báo số âm, rất lớn hoặc cố ý sai. Không cấp phát `new byte[untrustedLength]` rồi mới kiểm tra, vì có thể gây lỗi bộ nhớ. Ngoài kích thước tối đa, ứng dụng mạng còn cần chính sách timeout, tổng dữ liệu được phép nhận và xử lý kết nối chết/chậm.

Các bước đọc nhị phân an toàn theo cấu trúc thường là:

1. Xác định trạng thái luồng và ranh giới record hiện tại.
2. Đọc đủ header theo đúng định dạng; phân biệt EOF sạch tại ranh giới với header thiếu.
3. Giải mã và **kiểm tra giá trị độ dài**.
4. Cấp phát trong giới hạn đã kiểm tra hoặc xử lý theo từng khối nếu payload quá lớn cho RAM.
5. `readFully` đúng số byte payload; nếu EOF sớm thì báo frame bị cắt.
6. Chỉ giải mã hoặc xử lý **payload đã hoàn chỉnh**.

Không nên coi việc `readFully` thành công là kiểm tra toàn bộ tính hợp lệ của payload. Dữ liệu có thể đủ byte nhưng sai magic number, checksum, nội dung UTF-8, cấu trúc hoặc business rule; các bước xác thực ấy vẫn phải có.

## 10. Lỗi thường gặp

| Cách làm sai | Hậu quả | Cách xử lý |
| --- | --- | --- |
| Gọi `read(buffer)` đúng một lần rồi parse N byte | Dữ liệu có thể chỉ mới nhận một phần | Lặp đọc đủ hoặc dùng `readFully` |
| Dừng khi `bytesRead < buffer.length` | Short read không đồng nghĩa EOF | Chỉ kết thúc luồng thường bằng `-1` |
| Gọi `write(buffer)` sau short read | Ghi cả byte chưa hợp lệ/byte cũ | `write(buffer, 0, bytesRead)` |
| Catch `EOFException` rồi parse buffer như record đủ | Buffer có thể bị điền dở | Bỏ record thiếu; thông báo lỗi |
| Catch `EOFException` ở `readInt` rồi xem là kết thúc sạch mọi lúc | Không phân biệt hết ở ranh giới với header bị cắt | Theo dõi số byte header hoặc thiết kế framing rõ |
| Tưởng `readFully` trả số byte | Phương thức trả `void` | Thành công nghĩa là đủ số byte |
| Dùng `available()` để quyết định đã đủ N | Không phản ánh chính xác tổng byte còn lại | Dùng contract của protocol và `readFully` |
| Cấp phát trực tiếp từ độ dài chưa kiểm tra | Rủi ro bộ nhớ, từ chối dịch vụ | Kiểm tra giới hạn trước khi cấp phát |
| Dùng `readAllBytes()` với input vô hạn/lớn | Có thể sử dụng quá nhiều RAM | Giới hạn dữ liệu, đọc có kiểm soát |
| Bỏ qua EOF của frame khi socket đóng | Đọc bản tin thiếu như thành công | Báo truncated frame và dừng xử lý |

## 11. Tự kiểm tra

1. `byte[] b = new byte[10]`, gọi `read(b)` và nhận `3`. Có được parse cả 10 byte không? **Không**; chỉ có 3 byte mới hợp lệ.
2. `readFully(b)` cần 10 byte nhưng luồng chỉ còn 7 byte. Kết quả là gì? **Ném `EOFException`**; không trả 7 và không được dùng buffer làm record hoàn chỉnh.
3. `readFully(b, 4, 3)` cần đọc vào các chỉ số nào? **4, 5, 6**.
4. `readFully(b, 0, 0)` khi đã EOF có ném `EOFException` không? **Không**, vì yêu cầu 0 byte.
5. `readNBytes(10)` gặp EOF sau 7 byte có luôn ném `EOFException` không? **Không**; thường trả mảng 7 byte, lập trình viên kiểm tra độ dài nếu bắt buộc đủ 10.
6. `readInt()` ném `EOFException`. Có kết luận chắc chắn “hết record sạch” được không? **Không**; header có thể thiếu 1–3 byte.
7. File có header 4 byte khai báo payload dài 2 GB trong khi ứng dụng chỉ cho phép 1 MB. Nên làm gì? **Từ chối độ dài trước khi tạo `byte[]`**.
8. `readFully` nhận đủ payload nghĩa là nội dung hợp lệ tuyệt đối? **Không**; vẫn cần kiểm tra định dạng, checksum và quy tắc của dữ liệu.

**Ghi nhớ:** `read` trả **bao nhiêu byte thực tế vừa nhận**; `readFully` yêu cầu **đúng N byte hoặc ném `EOFException`**. Hãy chỉ giải mã cấu trúc khi đã đọc đủ, và coi EOF giữa một record bắt buộc là **dữ liệu bị cắt**, không phải record thành công.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="binary-data-006">readUTF, writeUTF và modified UTF-8</a>

<details>
<summary>Xem chi tiết</summary>

# readUTF, writeUTF và modified UTF-8 trong Java I/O

## 1. Đọc và ghi String trong định dạng dữ liệu nhị phân

Trong Java Core, **DataOutputStream.writeUTF(String)** và **DataInputStream.readUTF()** là cặp API để ghi và đọc chuỗi theo một định dạng nhị phân có quy ước cụ thể. Nó thường đi cùng các thao tác có kiểu như **writeInt/readInt** và **writeBoolean/readBoolean** khi chương trình tự định nghĩa giao thức nhị phân.

Cần phân biệt: **writeUTF không đơn thuần ghi UTF-8 chuẩn**. Nó ghi **hai byte chiều dài** rồi tới nội dung theo **modified UTF-8** (UTF-8 biến đổi theo định dạng Java DataInput/DataOutput). **readUTF** thực hiện quy trình ngược lại.

Ví dụ:

~~~text
writeUTF("Hi") → 00 02 48 69

00 02  : số byte payload = 2 (unsigned short, big-endian)
48 69  : nội dung "Hi" theo modified UTF-8
~~~

Trong khi đó, **"Hi".getBytes(StandardCharsets.UTF_8)** chỉ sinh **48 69**; nó **không** có hai byte tiền tố chiều dài. Vì vậy không thể thay thế cặp writeUTF/readUTF bằng getBytes và new String mà bỏ qua việc thay đổi định dạng trên wire.

Mục tiêu của bài: nắm rõ cấu trúc bản ghi, phân biệt modified UTF-8 và UTF-8 chuẩn, kiểm tra giới hạn byte 65.535, phân loại lỗi đọc/ghi, và triển khai Java 17+ chính xác.

## 2. Cấu trúc nhị phân của writeUTF

Khi gọi **DataOutputStream.writeUTF(text)**, kết quả trên OutputStream có dạng:

~~~text
┌─────────────────────────┬───────────────────────────────────────┐
│ unsigned 16-bit length  │       modified UTF-8 payload         │
│        2 byte           │        chính xác length byte         │
└─────────────────────────┴───────────────────────────────────────┘
          big-endian
~~~

- **Length** là số byte **đã mã hóa** của nội dung, **không tính hai byte prefix**.
- Hai byte length ghi theo **big-endian**: byte cao trước, byte thấp sau.
- Miền length là **0 đến 65.535**, tương đương unsigned short 16-bit.
- Nếu chuỗi rỗng, **writeUTF("")** ghi đúng hai byte **00 00**.
- Nếu chuỗi là **"A"**, kết quả là **00 01 41**.
- Nếu chuỗi là **"é"** (U+00E9), kết quả là **00 02 C3 A9**.
- Nếu chuỗi chỉ gồm ký tự NUL, kết quả là **00 02 C0 80**.

**String.length() không phải độ dài byte**. Java String lưu trữ nội dung theo các đơn vị UTF-16; số byte modified UTF-8 trên wire có thể lớn hơn String.length() nhiều lần. Length prefix chính là số byte payload **sau khi mã hóa**.

**DataInputStream.readUTF()** đọc hai byte chiều dài trước, sau đó lấy đúng số byte payload để giải mã thành một **String**. Nó không đọc một dòng, không dò dấu kết thúc văn bản và không đọc tất cả những byte còn lại của stream.

## 3. Modified UTF-8 khác UTF-8 chuẩn như thế nào?

Modified UTF-8 ở đây mã hóa từng **UTF-16 code unit** tương ứng với Java **char**:

| Giá trị UTF-16 code unit | Độ dài | Dạng byte |
| --- | ---: | --- |
| U+0001 đến U+007F | 1 byte | 0xxxxxxx |
| U+0000 hoặc U+0080 đến U+07FF | 2 byte | 110xxxxx 10xxxxxx |
| U+0800 đến U+FFFF | 3 byte | 1110xxxx 10xxxxxx 10xxxxxx |

Trong đó các chữ x là các bit lấy từ code unit.

### 3.1. NUL: U+0000 thành C0 80

UTF-8 chuẩn mã hóa ký tự U+0000 thành **00**. Với Java DataOutput.writeUTF, ký tự đó được mã hóa thành **C0 80** (hai byte). Đây là chuỗi **overlong** nếu xét theo tiêu chuẩn UTF-8 hiện đại, nên **không được xem nó là UTF-8 chuẩn hợp lệ**.

~~~text
String chứa "A", NUL, "B"

UTF-8 chuẩn payload:         41 00 42
writeUTF payload biến đổi:  41 C0 80 42
Toàn bản ghi writeUTF:   00 04 41 C0 80 42
~~~

Chỉ một ký tự NUL đã khiến hai cách mã hóa khác nhau dù các byte ASCII còn lại giống nhau.

### 3.2. Ký tự Unicode ngoài BMP: UTF-8 4 byte, modified UTF-8 6 byte

Một code point bổ sung như **U+1F600** biểu diễn bằng UTF-8 chuẩn trong **4 byte: F0 9F 98 80**. Java String biểu diễn nó bằng **cặp surrogate UTF-16 D83D DE00**, tức hai Java **char**.

**writeUTF** mã hóa hai code unit ấy riêng biệt, mỗi code unit 3 byte:

~~~text
U+1F600:
UTF-16 surrogate pair:   D83D DE00
Modified UTF-8 payload: ED A0 BD ED B8 80    (6 byte)
UTF-8 chuẩn:            F0 9F 98 80          (4 byte)
writeUTF full record:   00 06 ED A0 BD ED B8 80
~~~

Sau khi **readUTF** giải mã, nó tạo lại hai code unit trong Java String. Không được nhầm **một Unicode code point** với **một Java char**.

### 3.3. Khi nào byte giống nhau?

Các ký tự ASCII khác NUL được mã hóa một byte như UTF-8 chuẩn. Nhiều ký tự BMP, chẳng hạn **é** U+00E9, được mã hóa giống UTF-8 chuẩn (**C3 A9**).

Nhưng **trùng một phần payload không làm hai định dạng tương thích**: **writeUTF** vẫn có tiền tố chiều dài và vẫn khác ở NUL/surrogates. Không được dùng **StandardCharsets.UTF_8** làm decoder tổng quát cho payload writeUTF.

## 4. Ví dụ Java 17+ độc lập: mã hex và round-trip

Lưu file **ModifiedUtfDemo.java** với mã sau:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

public class ModifiedUtfDemo {
    public static void main(String[] args) throws IOException {
        String first = "A" + '\0' + '\u00E9';
        String supplementary =
                new String(Character.toChars(0x1F600));

        ByteArrayOutputStream memory = new ByteArrayOutputStream();

        try (DataOutputStream out = new DataOutputStream(memory)) {
            out.writeUTF(first);
            out.writeUTF(supplementary);
        }

        byte[] wire = memory.toByteArray();
        System.out.println("wire=" + HexFormat.of().formatHex(wire));
        System.out.println("wireLength=" + wire.length);

        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(wire))) {
            String a = in.readUTF();
            String b = in.readUTF();
            System.out.println("firstEqual=" + first.equals(a));
            System.out.println("supplementaryEqual=" +
                    supplementary.equals(b));
        }

        byte[] standard = first.getBytes(StandardCharsets.UTF_8);
        System.out.println("standardUtf8=" +
                HexFormat.of().formatHex(standard));
        System.out.println("firstLength=" + first.length());
        System.out.println("supplementaryLength=" +
                supplementary.length());
        System.out.println("differentWire=" +
                !Arrays.equals(standard, wire));
    }
}
~~~

Biên dịch và chạy trên JDK 17+:

~~~bash
javac -encoding UTF-8 ModifiedUtfDemo.java
java ModifiedUtfDemo
~~~

Stdout:

~~~text
wire=000541c080c3a90006eda0bdedb880
wireLength=15
firstEqual=true
supplementaryEqual=true
standardUtf8=4100c3a9
firstLength=3
supplementaryLength=2
differentWire=true
~~~

Phân tích:

- **00 05**: payload chuỗi đầu có 5 byte.
- **41**: A; **C0 80**: NUL; **C3 A9**: é.
- **00 06**: payload chuỗi sau có 6 byte.
- **ED A0 BD ED B8 80**: cặp surrogate của U+1F600.
- Toàn bộ stream: **2 + 5 + 2 + 6 = 15 byte**.
- Chuỗi đầu có **3 char UTF-16**, nhưng payload modified UTF-8 là **5 byte**.
- Chuỗi thứ hai có **2 char UTF-16** nhưng biểu diễn **một code point Unicode**.
- Mã hóa UTF-8 chuẩn của chuỗi đầu là **41 00 C3 A9**, dài **4 byte**, khác rõ wire format của writeUTF.

**readUTF hai lần** giải mã chính xác hai bản ghi do writeUTF tạo. Kết quả này minh họa tính đối xứng giữa hai API, không có nghĩa payload là UTF-8 chuẩn.

## 5. Phối hợp writeUTF với writeInt/writeBoolean

DataOutputStream ghi các trường đúng thứ tự lời gọi. DataInputStream phải đọc theo **đúng thứ tự và đúng kiểu**. Không có schema tự động trong stream.

Lưu ví dụ **DataRecordDemo.java**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HexFormat;

public class DataRecordDemo {
    public static void main(String[] args) throws IOException {
        ByteArrayOutputStream memory = new ByteArrayOutputStream();

        try (DataOutputStream out = new DataOutputStream(memory)) {
            out.writeInt(7);
            out.writeUTF("OK");
            out.writeBoolean(true);
        }

        byte[] raw = memory.toByteArray();
        System.out.println("hex=" + HexFormat.of().formatHex(raw));

        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(raw))) {
            int version = in.readInt();
            String name = in.readUTF();
            boolean enabled = in.readBoolean();

            System.out.println("version=" + version);
            System.out.println("name=" + name);
            System.out.println("enabled=" + enabled);
        }
    }
}
~~~

Chạy:

~~~bash
javac DataRecordDemo.java
java DataRecordDemo
~~~

Stdout:

~~~text
hex=0000000700024f4b01
version=7
name=OK
enabled=true
~~~

Wire format theo thứ tự:

~~~text
00 00 00 07     int 7, big-endian, 4 byte
00 02           payload UTF dài 2 byte
4F 4B           hai byte của "OK"
01              boolean true
~~~

Nếu bên đọc gọi **readUTF()** trước **readInt()**, nó có thể hiểu hai byte đầu của int 7 (**00 00**) là một chuỗi rỗng và đọc sai các trường tiếp theo. Vì vậy, hai phía phải thống nhất **schema, version, thứ tự field, kiểu, độ dài và cách xử lý lỗi**.

## 6. Giới hạn độ dài: tối đa 65.535 byte payload

Phương thức **writeUTF** không ghi chuỗi có payload modified UTF-8 dài hơn **65.535 byte**, vì chiều dài được mã hóa trên 16 bit không dấu. Nếu vượt, nó ném **UTFDataFormatException**.

Bảng minh họa:

| Giá trị String | String.length() | Payload modified UTF-8 | Kết quả writeUTF |
| --- | ---: | ---: | --- |
| "A".repeat(65535) | 65.535 | 65.535 byte | Thành công |
| "A".repeat(65536) | 65.536 | 65.536 byte | UTFDataFormatException |
| Một chuỗi 32.767 ký tự NUL | 32.767 | 65.534 byte | Thành công |
| Một chuỗi 32.768 ký tự NUL | 32.768 | 65.536 byte | UTFDataFormatException |
| "é".repeat(32768) | 32.768 | 65.536 byte | UTFDataFormatException |

**Giới hạn này là byte đã mã hóa, không phải số ký tự**. Ngay cả khi String.length() thấp hơn 65.535, payload vẫn có thể vượt giới hạn khi nhiều code unit mã hóa thành 2 hoặc 3 byte.

Lưu file **UtfLengthLimitDemo.java**:

~~~java
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UTFDataFormatException;

public class UtfLengthLimitDemo {
    private static void test(String s) throws IOException {
        ByteArrayOutputStream target = new ByteArrayOutputStream();

        try (DataOutputStream out = new DataOutputStream(target)) {
            out.writeUTF(s);
        } catch (UTFDataFormatException ex) {
            System.out.println("too long");
            return;
        }

        System.out.println("written=" + target.size());
    }

    public static void main(String[] args) throws IOException {
        test("A".repeat(65535));
        test("A".repeat(65536));
        test("\0".repeat(32768));
    }
}
~~~

Lệnh chạy:

~~~bash
javac UtfLengthLimitDemo.java
java UtfLengthLimitDemo
~~~

Stdout:

~~~text
written=65537
too long
too long
~~~

Giá trị **65.537** gồm **65.535 byte payload + 2 byte prefix**. Với String vượt giới hạn, DataOutputStream.writeUTF từ chối thay vì ghi một chuỗi có tiền tố không thể biểu diễn đủ độ dài.

## 7. Lỗi đọc: UTFDataFormatException, EOFException và IOException

**DataInputStream.readUTF()** có thể thất bại do nhiều nguyên nhân khác nhau:

| Tình huống | Ngoại lệ/hiện tượng | Giải thích |
| --- | --- | --- |
| Payload không hợp lệ theo decoder modified UTF-8 | **UTFDataFormatException** | Dạng mã hóa sai, thiếu byte continuation hoặc byte mở đầu không chấp nhận |
| File/socket hết trước khi nhận đủ 2 byte prefix | **EOFException** | Không đọc đủ chiều dài bản ghi |
| Có prefix nhưng byte payload ngắn hơn length đã ghi | **EOFException** | Bản ghi bị cắt |
| Đọc dữ liệu từ luồng nền bị lỗi | **IOException** | Lỗi đọc I/O khác EOF |
| Người đọc dùng nhầm vị trí trong stream | Sai nội dung hoặc ngoại lệ | Prefix có thể bị hiểu nhầm từ byte của trường khác |

**readUTF()** không phải parser có cơ chế tự đồng bộ lại khi dữ liệu lỗi. Nếu byte trên wire bị hỏng hoặc thiếu, các lời đọc tiếp theo có thể bị lệch. Trong ứng dụng thực tế nên dừng xử lý bản ghi lỗi, ghi log có ngữ cảnh phù hợp và quyết định đóng kết nối hoặc phục hồi theo quy tắc giao thức rõ ràng.

Nếu dữ liệu đến từ nguồn không tin cậy, cần bổ sung giới hạn tổng kích thước thông điệp, kiểm tra phiên bản, thứ tự trường và timeout tại tầng mạng. Hai-byte prefix giới hạn riêng mỗi chuỗi nhưng **không tự giới hạn tổng số bản ghi hoặc tổng số byte của toàn stream**.

## 8. Vì sao getBytes(UTF_8) không dùng trực tiếp với readUTF?

Ba luồng xử lý trông giống nhau ở mức người dùng nhưng khác định dạng:

| Cách ghi | Dữ liệu sinh ra | Cách đọc tương ứng |
| --- | --- | --- |
| **DataOutputStream.writeUTF** | Prefix 2 byte + modified UTF-8 | **DataInputStream.readUTF()** |
| **text.getBytes(StandardCharsets.UTF_8)** | Byte UTF-8 chuẩn, không prefix | **new String(bytes, UTF_8)** hoặc decoder chuẩn |
| **OutputStreamWriter(..., UTF_8)** | Byte UTF-8 chuẩn, không prefix tự động | **InputStreamReader(..., UTF_8)** |

Nếu bên gửi ghi **48 69** bằng getBytes UTF-8 nhưng bên nhận gọi **readUTF()**, hai byte đó được diễn giải là **length 0x4869 = 18.537** chứ không phải "Hi"; nếu không đủ các byte payload mà length khai báo, readUTF sẽ ném EOFException.

Nếu bên gửi gọi writeUTF để tạo **00 04 41 C0 80 42** và bên nhận dùng String với UTF-8 chuẩn, đầu đọc không biết phải bỏ qua prefix; hơn nữa **C0 80** không hợp lệ trong UTF-8 chuẩn. Kết quả có thể có ký tự điều khiển hoặc ký tự thay thế thay vì chuỗi gốc.

Vì vậy:

- Dùng **writeUTF/readUTF** chỉ khi cả hai bên thống nhất cùng định dạng đặc thù này.
- Dùng **StandardCharsets.UTF_8** khi giao thức hoặc file yêu cầu UTF-8 chuẩn.
- Không chỉ trao đổi tên charset mà phải mô tả **framing**, prefix, endian, kiểu trường và giới hạn.

Lưu ý các định dạng như JSON, CSV, HTTP hoặc file văn bản UTF-8 thường **không dùng** cặp writeUTF/readUTF.

## 9. Muốn String dài hơn hoặc liên thông nhiều ngôn ngữ?

Một lựa chọn tự thiết kế là **length 4 byte kiểu int big-endian + byte UTF-8 chuẩn**. Đây không phải wire format của readUTF và không nên đọc bằng readUTF.

Ví dụ Java 17+, có giới hạn tối đa 1 MiB cho mỗi bản ghi. Lưu thành **LengthPrefixedUtf8Demo.java**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class LengthPrefixedUtf8Demo {
    private static final int MAX_BYTES = 1024 * 1024;

    static void writeText(DataOutputStream out, String text)
            throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) {
            throw new IOException("String exceeds limit");
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    static String readText(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_BYTES) {
            throw new IOException("Invalid length");
        }
        byte[] bytes = new byte[count];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws IOException {
        ByteArrayOutputStream memory = new ByteArrayOutputStream();

        try (DataOutputStream out = new DataOutputStream(memory)) {
            writeText(out, "Hello UTF-8");
        }

        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(memory.toByteArray()))) {
            System.out.println(readText(in));
        }
    }
}
~~~

Lệnh chạy:

~~~bash
javac LengthPrefixedUtf8Demo.java
java LengthPrefixedUtf8Demo
~~~

Stdout:

~~~text
Hello UTF-8
~~~

Ví dụ quy định **int chiều dài tính theo byte UTF-8**, sau đó ghi đúng nội dung UTF-8. **readFully** phải lấy đủ số byte hoặc báo lỗi. Nó dùng String UTF-8 thông thường, không dùng modified UTF-8.

**Bảo mật và tính chặt chẽ:** phải kiểm tra chiều dài trước khi cấp phát, không cho phép độ dài âm, không nhận kích thước tùy ý từ nguồn chưa tin cậy. **new String(bytes, UTF_8)** thường thay thế chuỗi byte không hợp lệ; nếu giao thức phải **từ chối** mọi UTF-8 lỗi, dùng **CharsetDecoder** với **CodingErrorAction.REPORT**.

Với văn bản thông thường không cần length prefix, **BufferedReader/BufferedWriter** và charset rõ ràng thường hợp lý hơn. Với dữ liệu nhị phân dạng record, nên ghi rõ version và định dạng framing cho mọi client.

## 10. Dùng Data Streams cùng buffered streams và quản lý tài nguyên

Với file nhị phân, có thể ghép các lớp:

~~~java
try (DataOutputStream out = new DataOutputStream(
        new BufferedOutputStream(Files.newOutputStream(path)))) {
    out.writeInt(1);
    out.writeUTF("Hello");
}
~~~

Đọc theo đúng thứ tự:

~~~java
try (DataInputStream in = new DataInputStream(
        new BufferedInputStream(Files.newInputStream(path)))) {
    int version = in.readInt();
    String message = in.readUTF();
}
~~~

Hai đoạn là **mảnh minh họa**, giả định biến **path** kiểu Path và đã import các lớp **java.io** cùng **java.nio.file** cần dùng. **DataOutputStream/DataInputStream** đảm nhận format nhị phân; **BufferedOutputStream/BufferedInputStream** chỉ thêm vùng đệm để giảm thao tác I/O nhỏ.

Lớp bọc ngoài cùng có thể đóng luồng nền khi kết thúc khối **try-with-resources**. Nếu luồng nền thuộc thành phần khác hoặc là **System.out**, không tự đóng khi phần mềm vẫn còn cần dùng nó.

**flush()** trên output giúp đẩy byte từ buffer Java xuống luồng nền; nó **không đồng nghĩa** với fsync bền vững trên đĩa hay xác nhận rằng đầu nhận đã xử lý thông điệp. Khi cần xác nhận, dùng giao thức hoặc API durability phù hợp.

Không được tự đọc 2 byte prefix rồi gọi **readUTF()** trên phần payload còn lại: **readUTF() luôn đòi thêm một prefix mới tại vị trí hiện tại**, vì vậy sẽ đọc sai.

## 11. Lỗi phổ biến và cách phòng tránh

1. **Nghĩ writeUTF tương đương UTF-8 chuẩn.** Sai: có prefix và modified UTF-8.
2. **Dùng readUTF với getBytes(UTF_8).** Reader diễn giải byte đầu làm length, có thể EOF.
3. **Dùng String.length() làm số byte payload.** Mỗi UTF-16 code unit dùng 1, 2 hoặc 3 byte.
4. **Quên tối đa 65.535 byte encoded.** Chuỗi có ít ký tự hơn vẫn có thể vượt giới hạn.
5. **Quên hai byte length không tính vào payload.** Tổng kích thước bản ghi là **2 + length**.
6. **Giả định modified UTF-8 dùng 4 byte cho code point bổ sung.** Nó mã hóa hai surrogate riêng, thường tổng 6 byte.
7. **Ghi sai NUL.** writeUTF tạo **C0 80**, khác UTF-8 chuẩn **00**.
8. **Dùng length theo little-endian khi bên đọc chờ big-endian.** Hai phía phải tuân thủ đúng định dạng.
9. **Đọc sai thứ tự field.** Khi ghi writeInt rồi writeUTF thì phải đọc readInt rồi readUTF.
10. **Bỏ qua EOFException/UTFDataFormatException.** Bản ghi lỗi không phải chuỗi hợp lệ.
11. **Không giới hạn tổng số bản ghi/tổng byte mạng.** Giới hạn writeUTF chỉ áp dụng cho một payload chuỗi.
12. **Dùng readUTF làm decoder của file UTF-8 thông thường.** Nó không phù hợp với định dạng text thuần.
13. **Hiểu flush là đã đồng bộ xuống đĩa.** Tầng đệm Java và cơ chế durability khác nhau.
14. **Dùng writeUTF cho giao thức liên thông mà không mô tả format.** Client nền tảng khác có thể chỉ hỗ trợ UTF-8 chuẩn.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** Kết quả hex của writeUTF("Hi") là gì?  
**Đáp:** **00 02 48 69**, gồm 2-byte length và 2-byte payload.

**Câu 2.** Ký tự NUL được writeUTF mã hóa thế nào?  
**Đáp:** **C0 80**, trong khi UTF-8 chuẩn dùng **00**.

**Câu 3.** Vì sao U+1F600 có thể chiếm 6 byte trong modified UTF-8?  
**Đáp:** Java String dùng hai UTF-16 surrogate, mỗi code unit mã hóa 3 byte.

**Câu 4.** writeUTF với "A".repeat(65535) có thành công không, tổng số byte ghi?  
**Đáp:** Có, tổng **65.537 byte** gồm 2-byte prefix và 65.535-byte payload.

**Câu 5.** Vì sao "é".repeat(32768) không ghi được bằng writeUTF?  
**Đáp:** Payload 65.536 byte vượt giới hạn 65.535 byte.

**Câu 6.** Khi readUTF gặp payload chưa đủ độ dài mà prefix chỉ định, thường báo gì?  
**Đáp:** **EOFException**.

**Câu 7.** Ghi writeInt(7), writeUTF("OK"), writeBoolean(true) thì đọc theo thứ tự nào?  
**Đáp:** **readInt(), readUTF(), readBoolean()**.

**Câu 8.** Một giao thức HTTP yêu cầu UTF-8 chuẩn có nên thay bằng writeUTF không?  
**Đáp:** Không, trừ khi chính giao thức quy định đặc biệt dùng 2-byte length + Java modified UTF-8.

## 13. Tổng kết

**DataOutputStream.writeUTF()** tạo bản ghi gồm **2 byte unsigned big-endian length** và **payload modified UTF-8** dài đúng số byte đó. **DataInputStream.readUTF()** là phương thức đối xứng, đọc prefix và giải mã để khôi phục Java String.

Điểm dễ nhầm nhất: modified UTF-8 **mã hóa NUL thành C0 80**, **mã hóa mỗi UTF-16 code unit** và có thể dùng **6 byte cho một code point bổ sung**, khác UTF-8 chuẩn. Payload tối đa **65.535 byte**. Hãy dùng cặp phương thức này khi cả hai phía thống nhất định dạng Java binary; với text UTF-8 thông thường hoặc String lớn hơn giới hạn, chọn codec/framing khác theo giao thức.

### Tham khảo chính thức

- [Oracle Java SE 17 — DataOutput.writeUTF](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataOutput.html#writeUTF(java.lang.String)): cấu trúc và cách mã hóa.
- [Oracle Java SE 17 — DataInput.readUTF](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataInput.html#readUTF()): quy tắc đọc và các ngoại lệ.
- [Oracle Java SE 17 — DataOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataOutputStream.html): luồng ghi dữ liệu có kiểu.
- [Oracle Java SE 17 — DataInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataInputStream.html): luồng đọc dữ liệu có kiểu.
- [Oracle Java SE 17 — UTFDataFormatException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/UTFDataFormatException.html): lỗi chuỗi modified UTF-8.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="binary-data-007">Giới hạn độ dài của writeUTF</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn độ dài của `writeUTF` trong Java

`DataOutputStream.writeUTF(String)` là cách ghi một chuỗi vào **luồng dữ liệu nhị phân** của Java sao cho `DataInputStream.readUTF()` có thể đọc lại. Mỗi bản ghi gồm **2 byte tiền tố độ dài** rồi tới nội dung được mã hóa bằng **Modified UTF-8** (UTF-8 biến thể của Java). Vì tiền tố dài 2 byte không dấu, **phần nội dung đã mã hóa chỉ được tối đa 65.535 byte**. Nếu chuỗi vượt giới hạn này, `writeUTF` ném `UTFDataFormatException`.

Điểm dễ nhầm nhất: **65.535 là số byte Modified UTF-8, không phải số ký tự, không phải `String.length()`, và không tính 2 byte tiền tố.** Tùy ký tự, một đơn vị `char` của Java chiếm 1, 2 hoặc 3 byte; một emoji ngoài BMP thường chiếm tới **6 byte** trong Modified UTF-8.

Bài này thuộc mục **Dữ liệu nhị phân** của Java Core I/O. Ta sẽ hiểu định dạng trên dây, tính đúng giới hạn, nhận biết ngoại lệ, kiểm chứng bằng Java 17+, rồi chọn cách ghi dữ liệu khác khi chuỗi dài hơn giới hạn.

## 1. `writeUTF` ghi gì vào OutputStream?

Các API thường dùng:

```java
DataOutputStream out = new DataOutputStream(outputStream);
out.writeUTF(text);

DataInputStream in = new DataInputStream(inputStream);
String restored = in.readUTF();
```

Đây là **mảnh mã minh họa**: `outputStream`/`inputStream` là hai nguồn byte phù hợp, `text` là `String`; cần import `java.io.*` và xử lý `IOException`. Đầu đọc phải đứng đúng vị trí bắt đầu bản ghi và dùng đúng định dạng của `writeUTF`.

Cấu trúc bản ghi:

```text
+------------------------+-------------------------------------+
| 2 byte length          | Payload: Modified UTF-8            |
| unsigned short,        | chính xác N byte                     |
| big-endian, giá trị N  | (N từ 0 đến 65535)                 |
+------------------------+-------------------------------------+

Tổng số byte của một lần writeUTF thành công = 2 + N
N tối đa = 65535
Tổng kích thước bản ghi tối đa = 65537 byte
```

- Hai byte đầu biểu diễn `N` theo thứ tự **byte cao trước, byte thấp sau** (*big-endian*). Đọc ý nghĩa của chúng như một số nguyên không dấu 16 bit trong khoảng `0..65535`.
- `N` là **số byte payload** sau khi mã hóa *toàn bộ chuỗi* bằng Modified UTF-8.
- `DataInputStream.readUTF()` tự đọc tiền tố rồi đúng số byte payload, giải mã về `String`. Bên đọc **không** truyền riêng chiều dài chuỗi.
- Nếu payload cần **65.536 byte trở lên**, `DataOutputStream.writeUTF` ném `UTFDataFormatException` (lớp con của `IOException`) thay vì tạo một bản ghi hợp lệ.
- Chuỗi rỗng vẫn tạo **2 byte** `00 00`, vì payload có chiều dài 0.

Ví dụ: ghi `"ABC"` thì các byte của bản ghi là:

```text
00 03 41 42 43
|---| |------|
  3   "ABC" trong Modified UTF-8
```

Vì vậy `writeUTF("ABC")` khác `out.write("ABC".getBytes(UTF_8))`: lệnh đầu **tự thêm độ dài 2 byte**, lệnh sau chỉ ghi byte do bạn cung cấp.

## 2. Tại sao giới hạn là đúng 65.535 byte?

Phần độ dài được biểu diễn bằng **2 byte**, tức 16 bit, nên có thể chứa tối đa:

\[
2^{16} - 1 = 65535
\]

Trong đặc tả `DataOutput.writeUTF`, Java phải tính **tổng số byte Modified UTF-8 của chuỗi** trước khi quyết định có thể ghi. Nếu tổng vượt 65.535 byte thì gọi `writeUTF` thất bại. Đây là giới hạn **định dạng nhị phân**, không phải hạn chế bộ nhớ của `String` hoặc khả năng của `OutputStream`.

Cần phân biệt:

| Đại lượng | Ý nghĩa |
| --- | --- |
| `s.length()` | Số đơn vị `char` UTF-16 trong chuỗi Java |
| `s.codePointCount(0, s.length())` | Số Unicode code point (không đồng nghĩa số ký tự người dùng nhìn thấy) |
| `s.getBytes(UTF_8).length` | Số byte theo **UTF-8 chuẩn** |
| `modifiedUtf8Length(s)` | Số byte **Modified UTF-8** mà `writeUTF` cần |
| `2 + modifiedUtf8Length(s)` | Kích thước của cả bản ghi `writeUTF` khi hợp lệ |

Nếu chuỗi chỉ gồm các ký tự ASCII từ `U+0001` đến `U+007F` thì mỗi `char` cần 1 byte, nên chuỗi dài 65.535 ký tự vẫn hợp lệ. Nếu toàn ký tự cần 3 byte thì chỉ 21.845 `char` là vừa giới hạn. Với dữ liệu hỗn hợp cần cộng độ dài từng đơn vị `char`.

Không nên xử lý bằng cách “trừ đi 2 byte trước” từ giới hạn 65.535: hai byte tiền tố **nằm ngoài** payload và đã được tính riêng trong kích thước bản ghi.

## 3. Modified UTF-8 không giống UTF-8 chuẩn

`DataOutputStream.writeUTF` dùng **Modified UTF-8 của giao thức `DataInput`/`DataOutput`**, chứ không dùng trực tiếp `StandardCharsets.UTF_8`.

Cách mã hóa dựa trên từng `char` Java (một đơn vị UTF-16):

| Giá trị `char` | Số byte trong Modified UTF-8 | Ghi chú |
| --- | ---: | --- |
| `U+0001` đến `U+007F` | 1 | ASCII khác NUL |
| `U+0000` (NUL) | 2 | Mã hóa thành `C0 80` |
| `U+0080` đến `U+07FF` | 2 | Chẳng hạn `é` = `C3 A9` |
| `U+0800` đến `U+FFFF` | 3 | Mỗi **đơn vị `char`**, kể cả surrogate |
| Unicode code point ngoài BMP, ví dụ `😀` | **6** | Java biểu diễn bằng **hai `char` surrogate**, mỗi `char` thành 3 byte |

Những khác biệt quan trọng:

1. **NUL:** UTF-8 chuẩn mã hóa `\u0000` thành `00` (1 byte), Modified UTF-8 mã hóa thành `C0 80` (2 byte).
2. **Emoji/code point ngoài BMP:** UTF-8 chuẩn dùng một dãy 4 byte; Modified UTF-8 xử lý cặp surrogate UTF-16 thành **hai dãy 3 byte** (tổng 6 byte).
3. **Modified UTF-8 không phải UTF-8 chuẩn để trao đổi văn bản tự do**. Không giả định `new String(payload, UTF_8)` hoặc `InputStreamReader(..., UTF_8)` sẽ đọc đúng payload `writeUTF`, nhất là khi có NUL hoặc ký tự ngoài BMP.
4. Vì cặp surrogate được mã hóa theo từng đơn vị `char`, đếm “số code point” không đủ để tính kích thước Modified UTF-8.

### Một bản ghi cụ thể

Nếu ghi chuỗi gồm **NUL**, `A` và emoji `😀`, Modified UTF-8 tạo payload:

```text
C0 80                NUL: 2 byte
41                   A:   1 byte
ED A0 BD ED B8 80    😀:  6 byte (hai surrogate UTF-16)
--------------------------------
Payload: 9 byte

Cả bản ghi:
00 09 C0 80 41 ED A0 BD ED B8 80
|---| |-------------------------|
  9      payload 9 byte
```

`readUTF()` của Java đọc bản ghi này và phục hồi đúng chuỗi ban đầu. Một bộ giải mã UTF-8 chuẩn có thể coi các byte biểu diễn NUL/surrogate đó là không hợp lệ.

## 4. Ví dụ số liệu ở các ranh giới quan trọng

Giả sử `repeat(n)` lặp chuỗi `n` lần, chỉ tính payload của một lệnh `writeUTF`:

| Nội dung | Byte Modified UTF-8 mỗi phần tử | Số lần lặp | Kích thước payload | Kết quả |
| --- | ---: | ---: | ---: | --- |
| `"A"` | 1 | 65.535 | 65.535 | Thành công |
| `"A"` | 1 | 65.536 | 65.536 | `UTFDataFormatException` |
| `"\u0000"` | 2 | 32.767 | 65.534 | Thành công |
| `"\u0000"` | 2 | 32.768 | 65.536 | `UTFDataFormatException` |
| `"中"` | 3 | 21.845 | 65.535 | Thành công |
| `"中"` | 3 | 21.846 | 65.538 | `UTFDataFormatException` |
| `"😀"` | 6 | 10.922 | 65.532 | Thành công |
| `"😀"` | 6 | 10.923 | 65.538 | `UTFDataFormatException` |

Riêng emoji: `"😀".length() == 2` vì là một cặp surrogate. Lặp 10.922 emoji tạo chuỗi `String` dài **21.844 `char`** nhưng payload Modified UTF-8 có **65.532 byte**. Lặp thêm một emoji chỉ tăng `String.length()` thêm 2, nhưng tăng payload thêm **6 byte** và vượt giới hạn.

Từ đó, **`s.length() <= 65535` là điều kiện cần nhưng chưa đủ** để gọi `writeUTF` thành công. Trong tập ký tự được biểu diễn bằng các đơn vị UTF-16, mỗi `char` cần tối đa 3 byte Modified UTF-8. Nếu `s.length() > 65535` thì chắc chắn quá giới hạn; còn dưới ngưỡng này vẫn cần tính kích thước đã mã hóa.

## 5. Java 17+: chương trình kiểm chứng thành công, thất bại và readUTF

Tạo file `WriteUtfLimitDemo.java`:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.util.HexFormat;

public class WriteUtfLimitDemo {

    private static void probe(String label, String text)
            throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        try (DataOutputStream out = new DataOutputStream(buffer)) {
            try {
                out.writeUTF(text);

                byte[] record = buffer.toByteArray();
                int payloadLength = ((record[0] & 0xff) << 8)
                        | (record[1] & 0xff);

                try (DataInputStream in = new DataInputStream(
                        new ByteArrayInputStream(record))) {
                    String restored = in.readUTF();
                    System.out.println(label + ": payload="
                            + payloadLength + " total=" + record.length
                            + " equal=" + text.equals(restored));
                }
            } catch (UTFDataFormatException e) {
                System.out.println(label
                        + ": UTFDataFormatException written="
                        + buffer.size());
            }
        }
    }

    public static void main(String[] args) throws IOException {
        String nul = String.valueOf((char) 0);
        String cjk = "\u4E2D"; // 中
        String emoji = new String(Character.toChars(0x1F600));

        probe("ASCII max", "A".repeat(65535));
        probe("ASCII over", "A".repeat(65536));

        probe("NUL max", nul.repeat(32767));
        probe("NUL over", nul.repeat(32768));

        probe("CJK max", cjk.repeat(21845));
        probe("CJK over", cjk.repeat(21846));

        probe("Emoji max", emoji.repeat(10922));
        probe("Emoji over", emoji.repeat(10923));

        ByteArrayOutputStream tinyBuffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(tinyBuffer)) {
            out.writeUTF(nul + "A" + emoji);
        }
        String hex = HexFormat.ofDelimiter(" ").withUpperCase()
                .formatHex(tinyBuffer.toByteArray());
        System.out.println("Wire example: " + hex);
    }
}
```

Biên dịch/chạy bằng JDK 17+:

```bash
javac WriteUtfLimitDemo.java
java WriteUtfLimitDemo
```

Stdout đã kiểm chứng trên JDK:

```text
ASCII max: payload=65535 total=65537 equal=true
ASCII over: UTFDataFormatException written=0
NUL max: payload=65534 total=65536 equal=true
NUL over: UTFDataFormatException written=0
CJK max: payload=65535 total=65537 equal=true
CJK over: UTFDataFormatException written=0
Emoji max: payload=65532 total=65534 equal=true
Emoji over: UTFDataFormatException written=0
Wire example: 00 09 C0 80 41 ED A0 BD ED B8 80
```

Giải thích kết quả:

- Trường `payload` là hai byte tiền tố mà chương trình ghép lại thành số không dấu. `total` là kích thước toàn bộ bản ghi trên `ByteArrayOutputStream`.
- Với chuỗi vừa ngưỡng, `readUTF()` phục hồi đúng chuỗi và `equals` là `true`.
- Với chuỗi quá ngưỡng, `UTFDataFormatException` xảy ra do payload vượt 65.535 byte; **trong ví dụ và triển khai JDK này**, `writeUTF` tính trước kích thước, nên `ByteArrayOutputStream` vẫn có 0 byte từ lần ghi thất bại. Đừng mở rộng kết luận này thành bảo đảm rằng mọi lỗi I/O trên mọi stream sẽ rollback dữ liệu đã ghi.
- `tinyBuffer` chứng minh cấu trúc dữ liệu thực tế, bao gồm tiền tố `00 09`, byte NUL dạng `C0 80` và emoji 6 byte.

Chương trình dùng `HexFormat` (Java 17), `String.repeat` (Java 11+) và `Character.toChars`. Nó không phụ thuộc khả năng hiển thị emoji của console: đầu ra là hex và giá trị boolean.

## 6. Tự tính số byte Modified UTF-8 trước khi ghi

Nếu muốn kiểm tra giới hạn trước khi tạo bản ghi, có thể tính dung lượng theo từng đơn vị `char`:

```java
static long modifiedUtf8Length(String s) {
    long length = 0;
    for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (c >= 0x0001 && c <= 0x007f) {
            length += 1;
        } else if (c <= 0x07ff) {
            length += 2;
        } else {
            length += 3;
        }
    }
    return length;
}
```

Đây là **mảnh mã**, đặt trong một lớp Java; `s` phải khác `null`. Dùng `long` để tránh tràn số nếu ứng dụng xử lý các chuỗi cực dài. Thuật toán đi qua mỗi `char` một lần: `O(s.length())` về thời gian và `O(1)` bộ nhớ phụ.

Tại sao ba nhánh này đúng?

1. `c` trong `0x0001..0x007F` cần 1 byte.
2. `c == 0` hoặc `c` trong `0x0080..0x07FF` cần 2 byte.
3. Phần còn lại `0x0800..0xFFFF` cần 3 byte. Hai đơn vị surrogate của một code point ngoài BMP được cộng **hai lần × 3**.

Kiểm tra:

```java
long encodedLength = modifiedUtf8Length(s);
if (encodedLength > 65535) {
    throw new IllegalArgumentException(
            "writeUTF payload exceeds 65535 bytes: " + encodedLength);
}
out.writeUTF(s);
```

Đây là mảnh mã: `s` và `out` được chuẩn bị trước, và cần xử lý `IOException`. Việc tính trước hữu ích để đưa ra thông báo lỗi có ý nghĩa, lựa chọn encoding khác hoặc từ chối giá trị vượt ngưỡng trước khi bắt đầu một bản ghi lớn. Tuy nhiên, **`writeUTF` tự kiểm tra kích thước**; bạn không bắt buộc phải viết bộ đếm này chỉ để tránh ngoại lệ.

**Không dùng** `s.getBytes(StandardCharsets.UTF_8).length` làm kiểm tra tương đương cho `writeUTF`. UTF-8 chuẩn và Modified UTF-8 có số byte khác nhau ở NUL, surrogate và code point ngoài BMP.

### Không nên tự cắt chuỗi khi vượt ngưỡng

`s.substring(0, 65535)` chỉ cắt theo đơn vị `char`, không theo byte Modified UTF-8. Ngay cả khi đoạn mới có chiều dài ngắn hơn 65.535 `char`, nó vẫn có thể cần nhiều hơn 65.535 byte. Nếu cắt ở giữa cặp surrogate thì dữ liệu Unicode có thể bị chia sai. Cắt chuỗi để vừa format còn có thể làm mất dữ liệu quan trọng.

Khi cần lưu toàn bộ nội dung, chọn **định dạng bản ghi khác** thay vì cắt/truncate dữ liệu. Nếu giao thức bắt buộc `writeUTF`, có thể chia thành nhiều bản ghi **chỉ khi cả bên ghi và bên đọc được thiết kế trước để hiểu nhiều phần**, có thứ tự/độ dài/tổng số phần rõ ràng. Không tự chia thành nhiều `writeUTF` rồi kỳ vọng một `readUTF` sẽ ghép lại.

## 7. Cách xử lý UTFDataFormatException

Một ví dụ bắt ngoại lệ:

```java
try {
    out.writeUTF(value);
} catch (UTFDataFormatException e) {
    // value không thể nằm trong một bản ghi writeUTF.
    // Trả thông báo hợp lệ, chọn giao thức thay thế hoặc từ chối dữ liệu.
}
```

Đây là mảnh mã: `out` là `DataOutputStream` và `value` là `String`; cần import `java.io.UTFDataFormatException`.

Điểm cần lưu ý:

- `UTFDataFormatException` không có nghĩa là `String` đã vượt giới hạn bộ nhớ Java. Nó báo rằng dữ liệu không vừa **hợp đồng Modified UTF-8 với tiền tố 2 byte**.
- `IOException` khác có thể phát sinh do nguồn/đích đã đóng, đĩa đầy, mạng ngắt, v.v. Không gom mọi `IOException` thành lỗi độ dài.
- `UTFDataFormatException` cũng có thể xuất hiện **khi đọc** `DataInputStream.readUTF()` nếu phần dữ liệu không phải một chuỗi Modified UTF-8 hợp lệ. Khi đọc mà thiếu byte giữa bản ghi, có thể xảy ra `EOFException`.
- Với luồng nhị phân gồm nhiều trường, nếu một lần ghi gây lỗi giữa quá trình ghi do I/O, trạng thái đích có thể đã bị thay đổi. Không dựa vào cơ chế rollback mặc định của stream.
- Ứng dụng không nên bắt `UTFDataFormatException` rồi gọi lại `writeUTF` trên cùng giá trị không đổi hoặc cắt đại chuỗi để “thử cho qua”.
- Khi có ràng buộc kỹ thuật của định dạng được công khai với người dùng (ví dụ trường mô tả phải nằm trong một bản ghi `writeUTF`), nên xác thực khi nhận dữ liệu để trả thông báo chính xác sớm.

## 8. Phương án cho chuỗi dài hơn: length-prefix 4 byte + UTF-8 chuẩn

Nếu bạn **thiết kế giao thức nhị phân của riêng mình**, có thể thay `writeUTF` bằng định dạng:

```text
[int32 độ dài N, big-endian] [N byte UTF-8 chuẩn]
               4 byte          N byte
```

Ưu điểm: không phụ thuộc giới hạn 65.535 byte của `writeUTF` và dùng **UTF-8 chuẩn**, dễ tương tác hơn với nhiều hệ thống. Nhưng **định dạng này không tương thích `readUTF`**. Bên đọc phải dùng đúng `readInt`, `readFully` và giải mã charset đã thỏa thuận.

Ví dụ Java 17+ độc lập, lưu `LongTextFrameDemo.java`:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class LongTextFrameDemo {
    private static final int MAX_PAYLOAD = 1_000_000;

    static void writeText(DataOutputStream out, String text)
            throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_PAYLOAD) {
            throw new IOException("Payload too large");
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    static String readText(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_PAYLOAD) {
            throw new IOException("Invalid payload length: " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);

        // Strict decoding: từ chối byte UTF-8 không hợp lệ.
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IOException("Invalid UTF-8 payload", e);
        }
    }

    public static void main(String[] args) throws IOException {
        String text = "A".repeat(70_000);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        try (DataOutputStream out = new DataOutputStream(buffer)) {
            writeText(out, text);
        }

        byte[] record = buffer.toByteArray();
        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(record))) {
            String recovered = readText(in);
            System.out.println("Payload bytes: " + (record.length - 4));
            System.out.println("Record bytes: " + record.length);
            System.out.println("Round trip: " + text.equals(recovered));
        }
    }
}
```

Biên dịch/chạy:

```bash
javac LongTextFrameDemo.java
java LongTextFrameDemo
```

Stdout dự kiến:

```text
Payload bytes: 70000
Record bytes: 70004
Round trip: true
```

Chi tiết quan trọng:

1. `getBytes(StandardCharsets.UTF_8)` mã hóa thành **UTF-8 chuẩn**. Byte NUL sẽ là `00` và emoji thường dùng 4 byte — khác Modified UTF-8.
2. `writeInt` tạo tiền tố 4 byte (big-endian) biểu diễn **số byte payload**, không phải `String.length()`.
3. `readInt` đọc kích thước, **kiểm tra không âm và giới hạn tối đa**, rồi `readFully` đọc đúng số byte; nếu thiếu byte, `readFully` ném `EOFException`.
4. Decoder với `REPORT` không chấp nhận chuỗi byte UTF-8 hỏng; ví dụ tránh âm thầm thay ký tự khi dữ liệu bắt buộc hợp lệ.
5. `MAX_PAYLOAD` là **giới hạn ứng dụng tự đặt**, giúp giảm rủi ro cấp phát bộ nhớ quá lớn từ độ dài trong bản tin không đáng tin. Không có nghĩa bộ đệm một triệu byte tự động an toàn trong mọi hệ thống.
6. Ví dụ gom dữ liệu vào byte[] vì kích thước nhỏ, có giới hạn. Nếu nội dung dài hàng trăm MB/GB, nên thiết kế streaming, chia chunk và kiểm soát kích thước thay vì tạo một mảng byte khổng lồ.

Nếu giao thức đã được xác định sẵn bởi một thư viện/protocol bên ngoài, bạn **không thể đơn phương đổi `writeUTF` thành `writeInt + write`** mà vẫn giữ tương thích. Phải cập nhật đồng thời bên đọc, bên ghi và tài liệu wire format.

## 9. `writeUTF` khác gì các phương thức ghi chuỗi khác?

| Cách ghi | Dạng dữ liệu được ghi | Tự có độ dài? | Giới hạn chính |
| --- | --- | --- | --- |
| `DataOutputStream.writeUTF(s)` | Modified UTF-8 | **Có: 2 byte** | Payload tối đa 65.535 byte |
| `DataOutputStream.writeChars(s)` | Mỗi `char` thành 2 byte, high byte trước | **Không** | Không có giới hạn 65.535 byte của `writeUTF`; bên đọc phải biết số ký tự |
| `DataOutputStream.writeBytes(s)` | Chỉ byte thấp của mỗi `char` | **Không** | Có thể mất dữ liệu Unicode |
| `out.write(s.getBytes(UTF_8))` | UTF-8 chuẩn | **Không** | Phải quản lý framing/độ dài |
| `out.writeInt(n); out.write(bytes)` | Do ứng dụng định nghĩa | **Có: 4 byte** | Theo kiểu số, bộ nhớ và giới hạn ứng dụng |
| `OutputStreamWriter(out, UTF_8)` | Ký tự → UTF-8 chuẩn | Không theo bản ghi `writeUTF` | Quản lý ranh giới văn bản/tài nguyên |

`writeBytes(String)` thường **không** phải lựa chọn đúng để ghi Unicode vì chỉ dùng byte thấp của từng `char`. `writeChars(String)` giữ mỗi đơn vị `char` ở hai byte, nhưng không cung cấp tiền tố độ dài; bên đọc cần hợp đồng riêng, ví dụ `readChar()` lặp đúng số lần. Không nên coi các phương thức này là cách “bỏ giới hạn nhưng giữ nguyên kết quả của `writeUTF`”.

Với file trao đổi text (UTF-8), có thể dùng `Files.writeString(path, text, UTF_8)` hoặc `BufferedWriter` thay vì tự thiết kế bản ghi nhị phân. Với giao thức nhị phân cần đọc bằng `DataInputStream`, chọn wire format rõ ràng và có framing.

## 10. Những câu hỏi thực tế về giới hạn

### 10.1. `"A".repeat(65535)` có hợp lệ không?

Có, vì mỗi ký tự `A` là 1 byte Modified UTF-8, payload đúng 65.535 byte, và hai byte tiền tố là `FF FF`. Tổng số byte bản ghi là 65.537.

### 10.2. `"A".repeat(65536)` có được Java tự cắt còn 65.535 byte không?

Không. `writeUTF` **ném `UTFDataFormatException`**. Nó không tự cắt, không tự tạo hai bản ghi và không ghi số dài hơn 65.535 vào trường length 2 byte.

### 10.3. `String.length() == 30000` có chắc ghi được không?

Không. Nếu toàn các `char` mã hóa 3 byte, payload = 90.000 byte, vượt giới hạn. Nếu toàn ASCII khác NUL, payload = 30.000 byte, hợp lệ.

### 10.4. Nếu chuỗi có tiếng Việt thì sao?

Một số chữ Việt trong UTF-16 nằm trong khoảng `U+0080..U+07FF` nên thường cần **2 byte**; một số dấu tổ hợp hoặc ký tự khác có thể cần **2–3 byte** tùy đơn vị `char`. Không suy ra chiều dài byte chỉ từ số chữ hiển thị. Đếm theo từng `char` hoặc để `writeUTF` kiểm tra.

### 10.5. Emoji có phải 4 byte như UTF-8 thông thường không?

Không trong `writeUTF`. Một emoji như `😀` có hai đơn vị surrogate UTF-16 và thường cần **6 byte** Modified UTF-8. Nhưng khi dùng `StandardCharsets.UTF_8` để mã hóa văn bản chuẩn, code point này cần 4 byte.

### 10.6. Tiền tố `FF FF` là một số âm nếu đọc bằng `readShort()`?

Có thể được diễn giải thành `-1` nếu dùng `readShort()`, vì `short` là kiểu có dấu. `DataInputStream.readUTF()` hiểu tiền tố đúng định dạng, tương đương việc đọc một **unsigned short**. Nếu cần kiểm tra length thủ công, dùng `readUnsignedShort()` và không lẫn `readShort()`.

### 10.7. `readUTF` có đọc được byte do `writeInt + UTF-8` tạo ra không?

Không. `readUTF` chờ đúng **2 byte length + Modified UTF-8**, còn định dạng tùy biến ở phần trên là **4 byte length + UTF-8 chuẩn**. Dù một chuỗi ASCII ngắn có thể trông giống nhau ở phần payload, khung bản ghi vẫn khác và decoder có thể đọc lệch.

### 10.8. Có thể dùng `writeUTF` để ghi JSON lớn hoặc văn bản HTML dài không?

Chỉ nếu toàn bộ nội dung mã hóa Modified UTF-8 không vượt 65.535 byte **và** đầu đọc hiểu đúng định dạng. Với JSON/HTTP/text thông thường, thường nên dùng UTF-8 chuẩn và framing theo giao thức thay vì phụ thuộc `writeUTF`.

## 11. Các lỗi thường gặp

| Sai lầm | Hậu quả | Cách tránh |
| --- | --- | --- |
| Dùng `s.length() <= 65535` làm điều kiện đủ | Chuỗi nhiều Unicode vẫn vượt ngưỡng | Tính byte Modified UTF-8 |
| Tính `s.getBytes(UTF_8).length` làm kích thước `writeUTF` | Sai với NUL/emoji | Tính theo từng `char` và Modified UTF-8 |
| Trừ 2 từ 65.535 vì tiền tố | Hiểu sai ngưỡng tối đa | 65.535 chỉ là payload; tổng tối đa 65.537 |
| Coi `writeUTF` như `write` UTF-8 thường | Bên đọc sai byte, lỗi charset/framing | Ghép với `readUTF` |
| Dùng `new String(payload, UTF_8)` đọc Modified UTF-8 | NUL và surrogate có thể bị giải mã sai | `DataInputStream.readUTF()` |
| Cho rằng quá giới hạn sẽ bị cắt | Không có bản ghi hợp lệ | Bắt `UTFDataFormatException` và xử lý rõ ràng |
| Gọi `writeUTF` nhiều lần và chỉ gọi `readUTF` một lần | Chỉ đọc được một bản ghi | Thiết kế số lượng/chuỗi các bản ghi |
| Chia `String` tùy tiện ở 65.535 ký tự | Có thể cắt đôi surrogate hoặc vẫn quá dung lượng | Tính bytes và xác định chunk hợp lệ |
| Đổi một đầu sang `writeInt + UTF_8`, đầu kia vẫn `readUTF` | Hỏng giao thức | Nâng cấp đồng bộ wire format |
| Nhận length 4 byte từ nguồn ngoài rồi cấp phát ngay | Có nguy cơ cấp phát bộ nhớ quá lớn | Kiểm tra nonnegative/max trước `new byte[length]` |
| Nhầm `EOFException` khi đọc với `UTFDataFormatException` | Chẩn đoán sai lỗi bản ghi | EOF = thiếu byte; format = Modified UTF-8 không hợp lệ |
| Dùng `writeBytes` để giữ Unicode | Mất phần byte cao của `char` | Dùng UTF-8/encoding thích hợp |
| Giả định mọi lỗi I/O rollback stream | Bản ghi có thể bị ghi dở | Thiết kế transaction/framing/khôi phục ở tầng ứng dụng |

## 12. Bài tập tự kiểm tra

1. **Giới hạn của `writeUTF` là số nào?** 65.535 **byte Modified UTF-8 của payload**, không tính 2 byte tiền tố.
2. **Một chuỗi gồm 65.535 chữ `A` có ghi được không?** Có; payload 65.535 byte, tổng bản ghi 65.537 byte.
3. **Một chuỗi gồm 32.768 ký tự NUL có ghi được không?** Không; cần 65.536 byte Modified UTF-8.
4. **Vì sao `"😀".repeat(10923)` thất bại?** Mỗi emoji dùng 6 byte Modified UTF-8, tổng 65.538 byte.
5. **Tiền tố `FF FF` nghĩa gì?** Payload có chiều dài 65.535 byte không dấu; không phải `-1`.
6. **Phương thức nào đọc tương thích bản ghi `writeUTF`?** `DataInputStream.readUTF()` (hoặc API `DataInput.readUTF` tương thích).
7. **`StandardCharsets.UTF_8` có phải charset của `writeUTF` không?** Không; `writeUTF` dùng Modified UTF-8 theo hợp đồng `DataOutput`.
8. **Nếu chuỗi quá dài nhưng phải giữ đủ dữ liệu thì sao?** Thiết kế định dạng có độ dài lớn hơn (ví dụ tiền tố `int` và UTF-8 chuẩn), hoặc chia bản ghi có framing thỏa thuận hai đầu.
9. **`UTFDataFormatException` chỉ xuất hiện lúc ghi quá dài?** Không; đọc nội dung Modified UTF-8 sai định dạng cũng có thể phát sinh ngoại lệ này.
10. **Có thể dùng `getBytes(UTF_8).length` để kiểm tra giới hạn Modified UTF-8 không?** Không, vì NUL, surrogate và emoji có kích thước khác nhau.

## Tài liệu Java 17 chính thức

- [`DataOutput.writeUTF` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataOutput.html#writeUTF(java.lang.String)).
- [`DataOutputStream.writeUTF` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataOutputStream.html#writeUTF(java.lang.String)).
- [`DataInput.readUTF` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataInput.html#readUTF()).
- [`DataInputStream.readUTF` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataInputStream.html#readUTF()).
- [`UTFDataFormatException` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/UTFDataFormatException.html).
- [`StandardCharsets` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/StandardCharsets.html).

**Kết luận thực hành:** Dùng `writeUTF` khi dữ liệu cần khớp giao thức nhị phân Java với `readUTF` và **payload Modified UTF-8 không quá 65.535 byte**. Muốn ghi văn bản dài hơn hoặc trao đổi với hệ thống dùng UTF-8 chuẩn, hãy chọn một định dạng có length-prefix/framing phù hợp và xác thực độ dài khi đọc.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="binary-data-008">Thiết kế bản ghi có magic number, version và length</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế bản ghi nhị phân có magic number, version và length trong Java

## Mục tiêu bài học

Một luồng byte có thể chứa tệp, dữ liệu từ socket hoặc nhiều bản ghi nối tiếp nhau. Bản thân `InputStream` chỉ cung cấp byte theo thứ tự; nó **không tự biết byte nào thuộc bản ghi nào, bản ghi thuộc loại gì hoặc được mã hóa theo phiên bản nào**. Nếu chỉ ghi payload rồi đọc theo cảm tính, chương trình rất dễ phân tích lệch vị trí, cấp phát sai bộ nhớ hoặc đọc nhầm dữ liệu.

Bài này hướng dẫn thiết kế một định dạng bản ghi nhị phân có ba trường đầu tiên:

1. **Magic number**: dấu nhận diện kiểu dữ liệu/định dạng.
2. **Version**: phiên bản cấu trúc để parser biết bộ quy tắc nào phải áp dụng.
3. **Length**: số byte của payload tiếp theo, giúp xác định ranh giới bản ghi.

Bạn sẽ định nghĩa chính xác byte layout, mã hóa và giải mã bằng Java 17+, xử lý bản ghi lỗi, phiên bản không hỗ trợ và payload bị cắt, đồng thời hiểu các giới hạn về tương thích và an toàn.

## 1. Vì sao phải có cấu trúc bản ghi rõ ràng?

Giả sử ứng dụng cần lưu hoặc truyền thông điệp văn bản `Hi`, sau đó là thông điệp khác. Nếu chỉ ghi hai byte UTF-8 `48 69` rồi viết tiếp payload thứ hai, phía đọc không biết khi nào thông điệp đầu kết thúc.

Một header cố định giải quyết nhiều câu hỏi ngay ở đầu:

- **Magic number** giúp kiểm tra nhanh: có vẻ đây là đúng loại file/thông điệp mà parser mong đợi?
- **Version** trả lời: các trường phía sau cần được hiểu theo phiên bản cấu trúc nào?
- **Length** trả lời: parser cần đọc chính xác bao nhiêu byte để kết thúc payload hiện tại?

Đây gọi là **framing** (đóng khung dữ liệu). Với socket TCP, framing đặc biệt quan trọng: TCP truyền **dòng byte liên tục**, không giữ ranh giới của mỗi lần gọi `write()` hoặc mỗi lần người gửi coi là một thông điệp. Một lần `read()` có thể trả một phần, một bản ghi hay dữ liệu từ nhiều bản ghi tùy thời điểm và buffering.

## 2. Định nghĩa giao thức mẫu: JREC phiên bản 1

Không nên tùy tiện ghi các con số vào stream trước khi viết ra **hợp đồng byte chính thức**. Ta định nghĩa một giao thức học tập tên `JREC` như sau:

| Offset (byte) | Số byte | Trường | Giá trị/quy tắc |
| --- | ---: | --- | --- |
| `0..3` | 4 | `magic` | Bốn byte ASCII `JREC`: hex `4A 52 45 43` |
| `4..5` | 2 | `version` | Số nguyên **unsigned 16-bit**, big-endian; hỗ trợ giá trị `1` |
| `6..9` | 4 | `length` | Số nguyên **signed 32-bit**, big-endian; phải thuộc `0..1_048_576` |
| `10..(9 + length)` | `length` | `payload` | Chính xác `length` byte UTF-8 |
| Tiếp theo | — | Bản ghi tiếp theo hoặc EOF | Không có padding/trailer trong phiên bản 1 |

**Tổng kích thước mỗi bản ghi = 10 + length byte.** Header luôn dài `10` byte; `length` đếm **payload byte**, không gồm header, cũng không phải số Java `char` hay Unicode code point.

```text
   0                 3 4     5 6                 9 10
   +-------------------+-------+-------------------+---------------------+
   |  MAGIC "JREC"     | VER   | PAYLOAD_LENGTH    | PAYLOAD bytes ...   |
   |     4 byte        | 2 byte|      4 byte       |  length byte        |
   +-------------------+-------+-------------------+---------------------+
     BE byte order cho các trường số của header       UTF-8, không BOM
```

Quy ước cụ thể:

- Các trường số trong header là **big-endian (network byte order)**. Người viết và người đọc phải thống nhất điều này.
- Version chỉ hỗ trợ `1`; parser **từ chối** version lạ. Không tự đoán cấu trúc mới từ bản cũ.
- `length` được mã hóa bằng `int` 32-bit nhưng **bị giới hạn ở 1 MiB** theo hợp đồng để ngăn payload quá lớn. Phải kiểm tra giới hạn **trước khi cấp phát bộ nhớ**.
- Payload phiên bản 1 là **UTF-8 hợp lệ**; parser nghiêm ngặt báo lỗi nếu byte không hợp lệ thay vì tự thay bằng ký tự thay thế.
- Một stream có thể gồm **nhiều bản ghi** nối tiếp nhau; mỗi parser chỉ đọc đúng header + payload của bản ghi mà nó xử lý.
- Magic chỉ là **dấu nhận diện định dạng**, không phải chữ ký số, checksum hay bằng chứng xác thực nguồn gửi.

Ví dụ văn bản `Hi` có byte UTF-8 `48 69`, nghĩa là `length = 2`:

```text
Magic         Version  Length      Payload
4A 52 45 43 | 00 01 | 00 00 00 02 | 48 69
```

Khi ghép không có dấu phân cách:

```text
4a5245430001000000024869
```

Đây là **12 byte**: 4 magic + 2 version + 4 length + 2 payload. Các ký tự `|` và dấu cách ở sơ đồ chỉ để dễ đọc, không nằm trong dữ liệu thật.

## 3. Vì sao lựa chọn magic, version, length như trên?

### Magic number: phát hiện nhanh sai loại đầu vào

Magic thường là một giá trị dễ nhận biết ở những byte đầu, ví dụ bốn byte ASCII cố định. Với `JREC`, đọc sai magic có thể nghĩa là:

- Người dùng gửi nhầm file thuộc định dạng khác.
- Parser bắt đầu ở sai offset, có thể vì đã đọc thiếu/thừa byte ở phần trước.
- Nội dung bị thay đổi hoặc hỏng.
- Luồng không tuân thủ giao thức đã cam kết.

Không nên chỉ dựa vào magic để kết luận dữ liệu **chắc chắn hợp lệ**: bất kỳ người gửi nào cũng có thể tạo đúng bốn byte magic. Sau khi kiểm tra magic, vẫn cần kiểm tra version, length, payload và các ràng buộc dữ liệu khác.

Magic có thể được ghi bằng `DataOutputStream.writeInt(0x4A524543)` vì API này ghi số nguyên theo **big-endian**. Trong giao thức khác, magic cũng có thể được ghi trực tiếp bằng bốn byte ASCII. Quan trọng là byte layout nhất quán giữa các ngôn ngữ.

### Version: không đọc dữ liệu mới như bản cũ

Version là mã định danh **cấu trúc wire format**, không nhất thiết trùng với phiên bản ứng dụng. Parser phiên bản 1 biết chính xác payload hiện tại là UTF-8; nếu ở version 2 payload thay đổi thành các trường nhị phân, parser v1 phải từ chối version 2 (trừ khi đã được thiết kế hỗ trợ).

Nên xác định rõ:

- Giá trị nào được hỗ trợ.
- Khi không hỗ trợ: báo lỗi với thông tin thích hợp.
- Đổi version khi một thay đổi không tương thích với quy tắc cũ.
- Nếu cần đọc nhiều version: tách parser theo version, tránh dùng một bố cục duy nhất để “đoán” mọi phiên bản.

### Length: xác định ranh giới và chống đọc vượt

Trường `length` phải nói rõ nó đo **byte**, không đo ký tự; tính **payload hay toàn bản ghi**; chứa giá trị âm hay không; và có **giới hạn tối đa** không.

Trong giao thức mẫu, `length` là số byte payload `0..1_048_576`. Giá trị âm hoặc lớn hơn ngưỡng đều bị từ chối. Không cần gọi `InputStream.available()` để tìm độ dài: `available()` chỉ cho biết ước lượng số byte có thể đọc ngay mà không bị chặn, không phải tổng độ dài bản ghi.

## 4. Java 17+: ghi và đọc một bản ghi đầy đủ

Java `DataOutputStream` và `DataInputStream` phù hợp khi định dạng có các trường số big-endian cố định:

- `writeInt(int)`: ghi bốn byte big-endian.
- `writeShort(int)`: ghi hai byte big-endian, lấy 16 bit thấp.
- `readInt()`: đọc đủ bốn byte big-endian thành `int`.
- `readUnsignedShort()`: đọc hai byte thành số `0..65535`.
- `readFully(byte[])`: đọc **đúng số byte** theo độ dài mảng, hoặc ném `EOFException` nếu stream kết thúc sớm.

**Lưu thành `RecordProtocolDemo.java`:**

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

public class RecordProtocolDemo {
    private static final int MAGIC = 0x4A524543; // ASCII "JREC"
    private static final int VERSION = 1;
    private static final int MAX_PAYLOAD_BYTES = 1_048_576;

    static void writeRecord(OutputStream target, String text) throws IOException {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("payload too large");
        }

        // Không đóng wrapper vì target thuộc quyền sở hữu của người gọi.
        DataOutputStream out = new DataOutputStream(target);
        out.writeInt(MAGIC);
        out.writeShort(VERSION);
        out.writeInt(payload.length);
        out.write(payload);
        out.flush();
    }

    static String readRecord(InputStream source) throws IOException {
        DataInputStream in = new DataInputStream(source);

        int magic = in.readInt();
        if (magic != MAGIC) {
            throw new IOException("invalid magic");
        }

        int version = in.readUnsignedShort();
        if (version != VERSION) {
            throw new IOException("unsupported version");
        }

        int length = in.readInt();
        if (length < 0 || length > MAX_PAYLOAD_BYTES) {
            throw new IOException("invalid length");
        }

        byte[] payload = new byte[length];
        in.readFully(payload);

        // Giải mã nghiêm ngặt: UTF-8 hỏng sẽ báo CharacterCodingException.
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(payload))
                .toString();
    }

    private static void expectFailure(String label, byte[] wire) {
        try {
            readRecord(new ByteArrayInputStream(wire));
            System.out.println(label + " = unexpectedly accepted");
        } catch (IOException e) {
            System.out.println(label + " = "
                    + e.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) throws IOException {
        ByteArrayOutputStream destination = new ByteArrayOutputStream();
        writeRecord(destination, "Hi");
        byte[] wire = destination.toByteArray();

        System.out.println("wire = " + HexFormat.of().formatHex(wire));
        System.out.println("decoded = "
                + readRecord(new ByteArrayInputStream(wire)));
        System.out.println("total bytes = " + wire.length);

        byte[] badMagic = wire.clone();
        badMagic[0] = 0;
        expectFailure("bad magic", badMagic);

        byte[] badVersion = wire.clone();
        badVersion[5] = 2;
        expectFailure("unsupported version", badVersion);

        byte[] tooLarge = wire.clone();
        tooLarge[6] = 0x7F; // Length > MAX_PAYLOAD_BYTES.
        expectFailure("oversized length", tooLarge);

        byte[] truncated = Arrays.copyOf(wire, wire.length - 1);
        expectFailure("truncated payload", truncated);
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac RecordProtocolDemo.java
java RecordProtocolDemo
```

Đầu ra chính xác:

```text
wire = 4a5245430001000000024869
decoded = Hi
total bytes = 12
bad magic = IOException
unsupported version = IOException
oversized length = IOException
truncated payload = EOFException
```

### Giải thích luồng xử lý

**Ghi (serialize):**

1. Chuyển `text` thành byte UTF-8 bằng `getBytes(StandardCharsets.UTF_8)`. Độ dài lấy từ **`payload.length`**, không phải `text.length()`.
2. Kiểm tra giới hạn kích thước trước khi xuất header.
3. Viết magic, version, length theo thứ tự và đúng kích thước byte đã công bố.
4. Viết toàn bộ payload; `flush()` đẩy dữ liệu xuống tầng thấp hơn. Nó **không chứng minh** dữ liệu đã được lưu bền vững trên đĩa hoặc đầu bên kia đã xử lý.

**Đọc (deserialize):**

1. `readInt()` lấy đủ bốn byte magic hoặc ném `EOFException` khi header không hoàn chỉnh.
2. `readUnsignedShort()` lấy version; nếu không hỗ trợ thì từ chối.
3. `readInt()` đọc length; từ chối giá trị âm hoặc vượt ngưỡng **trước khi gọi `new byte[length]`**.
4. `readFully(payload)` thu đúng `length` byte, kể cả nguồn trả ít byte trong từng lần đọc. Nếu thiếu payload thì ném `EOFException`.
5. Giải mã UTF-8 nghiêm ngặt: dữ liệu sai chuẩn ném `CharacterCodingException` (là một loại `IOException`).

Các phương thức trên không tự đóng `source` hoặc `target`. Trong ứng dụng thực tế, lớp sở hữu stream quyết định vòng đời và thường dùng `try-with-resources` tại điểm mở file/socket. Điều này cho phép nhiều bản ghi dùng chung một stream mà không bị đóng giữa chừng.

### Vì sao hai bài kiểm tra lỗi còn lại hoạt động?

- Khi sửa byte magic đầu thành `00`, parser từ chối magic không trùng `JREC`.
- Khi sửa byte cuối của trường version tại offset `5` thành `02`, version trở thành `2`, parser từ chối.
- Khi sửa byte đầu trường length tại offset `6` thành `7F`, length vượt giới hạn 1 MiB. Chương trình **không cấp phát** một mảng hàng tỉ byte.
- Khi cắt byte cuối của payload, header vẫn khai `length=2`, nhưng chỉ còn một byte. `readFully` ném `EOFException`, không giả vờ nhận bản ghi hợp lệ.

## 5. Tại sao không thể dùng một lần `read(byte[])`?

Đây là cách viết **sai** khi cần đọc chính xác `length` byte:

```java
// MINH HỌA SAI; giả định đã có InputStream in và int length.
byte[] payload = new byte[length];
int count = in.read(payload); // Có thể đọc ít hơn length byte.
```

Trên file hoặc TCP stream, một lần `read` **không được bảo đảm** điền đầy mảng. Dữ liệu có thể đến thành nhiều phần; `count` có thể nhỏ hơn `length` dù chưa EOF.

Đối với giao thức yêu cầu đủ số byte:

```java
// Đoạn minh họa: in là DataInputStream, length đã được kiểm tra an toàn.
byte[] payload = new byte[length];
in.readFully(payload); // Đọc đủ hoặc báo EOFException.
```

Chỉ sử dụng `readFully` sau khi kiểm tra length và giới hạn tài nguyên. Với payload lớn mà bạn không muốn đưa hết vào RAM, dùng vòng lặp đọc từng khối cho đến khi tổng byte đã đọc bằng đúng length. **Đọc đến EOF để xác định cuối một bản ghi không đúng** nếu một kết nối có thể mang nhiều bản ghi: EOF có thể chỉ tới khi kết nối được đóng, rất lâu sau khi thông điệp hiện tại hoàn thành.

### Không dựa vào `available()`

`available() == 0` không có nghĩa bản ghi đã hết. Nó chỉ cho biết tại thời điểm đó không có byte nào được bảo đảm có thể đọc ngay mà không chặn. Sử dụng field length đã công bố và xác nhận kết quả đọc thay vì dựa vào `available()`, kể cả khi bạn đã dùng `BufferedInputStream`.

## 6. Nhiều bản ghi nối tiếp trên cùng luồng

Nếu dữ liệu gồm ba bản ghi, trình tự byte là:

```text
[MAGIC | VERSION | LENGTH | PAYLOAD #1]
[MAGIC | VERSION | LENGTH | PAYLOAD #2]
[MAGIC | VERSION | LENGTH | PAYLOAD #3]
```

Sau khi `readRecord` đọc xong **đúng** `length` byte payload, vị trí luồng sẽ nằm ngay trước header của bản ghi tiếp theo. Không cần separator giữa hai bản ghi vì length cung cấp ranh giới.

Nếu ứng dụng đã biết số bản ghi (ví dụ do một header container bên ngoài quy định), có thể gọi `readRecord` đúng số lần, cùng một `InputStream`. Nếu không biết số lượng và muốn dừng khi stream kết thúc, cần phân biệt:

- **EOF sạch trước khi bắt đầu header mới:** không còn bản ghi nào.
- **EOF giữa chừng trong magic/version/length/payload:** dữ liệu đã bị cắt hoặc truyền chưa hoàn chỉnh, phải báo lỗi.

Ví dụ `DataInputStream.readInt()` ném `EOFException` cả khi **chưa đọc được byte đầu magic** lẫn khi **mới đọc được một vài byte của magic**. Vì vậy không nên bắt mọi `EOFException` rồi coi là kết thúc bình thường.

Một cách thiết kế parser chuỗi bản ghi là đọc **một byte đầu của magic**:

1. Nếu byte đầu trả `-1`, chỉ khi đó coi là EOF sạch trước bản ghi mới.
2. Nếu đã nhận byte đầu, phải đọc tiếp đủ ba byte còn lại của magic; thiếu byte nào là bản ghi lỗi.
3. Sau đó áp dụng toàn bộ kiểm tra version, length, payload như trên.

Không dùng hai `BufferedInputStream` hay hai parser độc lập cùng tranh một nguồn: lớp đệm có thể đã đọc trước byte của header tiếp theo.

## 7. Kiểm tra độ dài an toàn và chống cấp phát quá mức

Trường length là **dữ liệu do phía gửi hoặc file cung cấp**. Dù có phép kiểm tra magic, dữ liệu vẫn có thể độc hại hoặc hỏng. Hãy coi mọi trường trong header là không đáng tin cho đến khi kiểm tra:

| Rủi ro | Cách xử lý |
| --- | --- |
| Length âm | Từ chối trước khi tạo mảng. |
| Length cực lớn | Đặt giới hạn ứng dụng/giao thức; từ chối trước khi cấp phát. |
| Length nhỏ hơn payload thực | Parser sẽ hiểu các byte thừa là header kế tiếp; có thể sai magic hoặc lệch frame. Cần kiểm tra đúng định dạng phía gửi. |
| Length lớn hơn dữ liệu thực | `readFully` báo `EOFException`; nếu stream mạng chưa EOF có thể chờ thêm byte. |
| Header thiếu byte | Báo bản ghi không hoàn chỉnh, không đọc vượt sang dữ liệu không thuộc bản ghi. |
| Byte UTF-8 không hợp lệ | Decoder nghiêm ngặt báo lỗi. |
| Length hợp lệ nhưng tổng bản ghi quá nhiều | Thêm giới hạn số bản ghi, tổng byte, thời gian hoặc tốc độ phù hợp. |

**Không dùng `Files.size(path)` hoặc tổng dữ liệu còn lại trong file thay cho length từng bản ghi**, trừ khi định dạng thực sự khai báo “một file chỉ chứa một payload”. Với nhiều bản ghi nối tiếp, kích thước file là thông tin của toàn container, còn length là thông tin mỗi frame.

Với payload lớn, không nên cấp phát toàn bộ `byte[length]`. Có thể dùng buffer cố định và biến `remaining`:

```java
// MINH HỌA THUẬT TOÁN, chưa phải chương trình chạy độc lập.
// in: DataInputStream đã đọc header; out: OutputStream đích; remaining = length đã xác thực.
byte[] buffer = new byte[8192];
while (remaining > 0) {
    int chunk = (int) Math.min(buffer.length, remaining);
    in.readFully(buffer, 0, chunk);
    out.write(buffer, 0, chunk);
    remaining -= chunk;
}
```

Trong đoạn minh họa, `remaining` là biến kiểu `long` hoặc `int` đã được khởi tạo bằng length hợp lệ; `in` và `out` được quản lý bởi người gọi. Cách này không cần RAM tỷ lệ theo payload và vẫn đọc **đúng số byte của frame**. Nó chưa làm các bước kiểm tra magic/version, checksum hay policy riêng của ứng dụng; các bước đó thuộc lớp parser bên ngoài.

## 8. Byte order, charset và ranh giới dữ liệu

### Big-endian phải là quy tắc được viết ra

`DataOutputStream.writeInt` và `DataInputStream.readInt` ghi/đọc số nguyên big-endian, nhưng không phải mọi thư viện, ngôn ngữ, CPU hay `ByteBuffer` tùy chỉnh đều mặc định giống nhau.

Nếu phía gửi ghi length `2` bằng little-endian (`02 00 00 00`) còn phía nhận đọc big-endian, giá trị sẽ thành `33_554_432` và có thể bị từ chối hoặc gây cấp phát sai nếu không kiểm tra. Vì vậy bản đặc tả cần công bố endian **cho từng trường số**.

Đừng “thử đảo byte” không có kiểm soát khi length trông sai. Khi có nhiều biểu diễn hợp lệ, cách đoán dễ dẫn tới parser mơ hồ và lỗi bảo mật. Phải xác định byte order bằng đặc tả và version.

### Length của UTF-8 đếm byte

Payload `"é"` có:

- Một Unicode code point.
- Một Java UTF-16 code unit (nên `"é".length() == 1`).
- **Hai byte UTF-8**: `C3 A9`.

Vì vậy length cho payload này phải là `2`, không phải `1`. Với emoji 😀, UTF-8 cần bốn byte, còn `String.length()` trong Java trả `2` (surrogate pair). Khi ghi, **luôn mã hóa trước rồi lấy `payload.length`**.

### Không nhầm `writeUTF/readUTF` với UTF-8 payload của giao thức

`DataOutputStream.writeUTF(String)` sử dụng một dạng **modified UTF-8** với độ dài 2 byte ở phía trước và quy tắc mã hóa khác UTF-8 chuẩn cho một số ký tự. `DataInputStream.readUTF()` chỉ phù hợp khi cả hai phía cố ý sử dụng **đúng định dạng của `writeUTF`**.

Với JREC v1, header đã có length 4 byte và payload là UTF-8 chuẩn; **không dùng `writeUTF/readUTF`** để thay `writeInt(payload.length)` + `write(payload)`. Làm vậy sẽ thay đổi layout và khiến parser ngoài Java khó tương thích.

## 9. Thiết kế version để có thể tiến hóa

Một format tốt không chỉ đọc bản ghi hôm nay mà còn xác định hành vi khi gặp dữ liệu của ngày mai.

Ví dụ các tình huống tương lai:

- **Version 1:** payload text UTF-8 như ví dụ.
- **Version 2:** payload có trường loại bản ghi, timestamp hoặc checksum.
- **Version 3:** thêm cơ chế nén hay biến thể cấu trúc.

Nếu đổi ý nghĩa byte của header, phải xác định rõ **version được đọc ở đâu và parser phân nhánh khi nào**. Ở ví dụ, cả v1 và các version tương lai sẽ phải giữ magic và vị trí version như một phần giao kèo chung, hoặc phải công bố cơ chế phát hiện định dạng mới.

Các phương án tiến hóa:

1. **Tăng version** khi bố cục hoặc ý nghĩa trường thay đổi không tương thích.
2. **Giữ header tối thiểu ổn định** đủ để chọn parser đúng version.
3. **Đưa các trường tùy chọn vào payload có cấu trúc** (ví dụ TLV: type-length-value) khi cần thêm trường mà không phá dữ liệu cũ, nhưng phải định nghĩa rõ type, endian, duplicate fields và cách xử lý trường chưa biết.
4. **Không tự coi mọi version lớn hơn là tương thích.** Khả năng bỏ qua trường lạ chỉ có nếu giao thức được thiết kế hỗ trợ ngay từ đầu.
5. **Có dữ liệu mẫu chuẩn (golden bytes)** trong bộ kiểm thử liên ngôn ngữ: cùng một chuỗi phải tạo cùng các byte, đọc lại cùng giá trị.

Trong mọi trường hợp, việc đổi kích thước trường length hoặc đơn vị đo byte cũng là thay đổi giao thức: không được âm thầm sửa ở một phía.

## 10. Checksum, xác thực và đồng bộ lại luồng

Magic + version + length giúp **đóng khung và nhận diện** bản ghi, nhưng không bảo đảm tính toàn vẹn hay tính xác thực.

- **Checksum/CRC** (ví dụ CRC32) có thể phát hiện nhiều lỗi dữ liệu vô tình thay đổi; nếu thêm, phải quy định nó đặt trước/sau payload, tính trên byte nào, endian và cách parser xác minh. CRC không bảo vệ trước đối thủ có thể chủ động sửa dữ liệu và tính lại CRC.
- **MAC hoặc chữ ký số** là vấn đề khác: dùng khi giao thức cần chống giả mạo và xác minh bên gửi. Cần thiết kế key management và phạm vi dữ liệu được xác thực theo mô hình an ninh của ứng dụng.
- **Magic không bảo đảm có thể khôi phục đồng bộ.** Khi một length bị hỏng, parser có thể đứng sai vị trí. Việc quét tìm magic ở byte tiếp theo chỉ là heuristic; chuỗi giống magic có thể xuất hiện hợp lệ trong payload.
- **Độ bền ghi:** `flush()` không tương đương cam kết atomic record hoặc fsync. Một tiến trình có thể dừng giữa header và payload, để lại bản ghi cuối bị cắt. Parser cần báo EOF giữa frame; nếu cần commit nguyên tử, phải có thiết kế file/transaction riêng.

Đối với tệp chỉ phục vụ học tập hoặc dữ liệu tin cậy, header mẫu là đủ để hiểu framing. Khi dùng trong hệ thống thực, lựa chọn checksum/xác thực/nén phải dựa trên nhu cầu cụ thể và được mô tả thành version hoặc cờ trường có nghĩa rõ ràng.

## 11. Phân biệt lỗi và cách phản hồi

| Phát hiện | Ý nghĩa có thể xảy ra | Cách xử lý đề xuất |
| --- | --- | --- |
| Magic sai | Sai loại file, offset sai, dữ liệu hỏng | Từ chối, ghi nhận vị trí và magic thực tế nếu an toàn. |
| Version không hỗ trợ | Định dạng mới hoặc parser quá cũ | Từ chối rõ ràng; cập nhật parser nếu được phép. |
| Length âm | Header bất hợp lệ | Từ chối trước cấp phát. |
| Length vượt giới hạn | Dữ liệu vượt chính sách/tấn công tiêu tốn tài nguyên | Từ chối, giới hạn nguồn và số byte xử lý. |
| EOF giữa header hoặc payload | File bị cắt, truyền chưa hoàn chỉnh, kết nối đóng | Báo lỗi frame thiếu dữ liệu; không coi là EOF sạch. |
| UTF-8 sai | Payload không theo giao thức | Báo lỗi giải mã nghiêm ngặt. |
| Nội dung ngữ nghĩa sai dù đúng framing | Payload không hợp quy tắc nghiệp vụ | Xác thực thêm sau khi giải mã. |

Không phải mọi lỗi đều được sửa bằng cách thử đọc thêm. Với magic/version/length sai, parser có thể đã mất khả năng xác định ranh giới; tiếp tục đọc bừa dễ làm che giấu lỗi và khiến dữ liệu kế tiếp bị hiểu sai.

## 12. Những lỗi phổ biến cần tránh

1. **Chỉ định dạng header bằng chuỗi** như `out.print(length)`: nó tạo các chữ số text, không tạo bốn byte big-endian như `writeInt`.
2. **Không công bố byte order**: hai phía có thể hiểu cùng bốn byte thành hai số khác nhau.
3. **Lấy `String.length()` làm length của payload UTF-8**: sai với nhiều ký tự Unicode.
4. **Dùng `available()` để xác định đã nhận đủ payload**: giá trị này không đo tổng số byte sẽ đến.
5. **Gọi `read(payload)` đúng một lần rồi mặc định mảng đã đầy**: phải lặp cho đủ hoặc dùng `readFully`.
6. **Cấp phát `new byte[length]` trước khi giới hạn length**: dữ liệu không tin cậy có thể ép cấp phát quá lớn.
7. **Bắt mọi `EOFException` rồi xem là hết file bình thường**: header/payload có thể mới đọc một phần.
8. **Dùng `writeUTF/readUTF` cho payload UTF-8 chuẩn**: hai API đó dùng định dạng modified UTF-8 và có prefix riêng.
9. **Thấy magic đúng là cho rằng bản ghi an toàn**: cần còn kiểm tra version, length, dữ liệu và điều kiện nghiệp vụ.
10. **Thay đổi trường khi không đổi version**: khiến bộ đọc cũ hiểu sai wire format.
11. **Đọc tiếp đến EOF cho một bản ghi trên socket**: kết nối có thể giữ mở và chứa nhiều bản ghi, phải dừng sau đúng `length` byte.
12. **Không kiểm soát vòng đời stream**: đóng wrapper ở mỗi bản ghi có thể đóng luôn kết nối dùng cho các bản ghi tiếp theo.
13. **Quên rằng packet của TCP không phải record của ứng dụng**: số lần `write` không quyết định số lần `read` tương ứng.
14. **Chỉ kiểm tra checksum mà không kiểm tra giới hạn length**: checksum không chống cấp phát hoặc đọc quá giới hạn.

## 13. Bài tập tự kiểm tra

**1.** Với header `JREC` v1 và payload có byte UTF-8 `48 69`, tổng số byte của bản ghi là bao nhiêu?

**Đáp án:** `10 + 2 = 12` byte; header 10 byte và payload 2 byte.

**2.** Nếu gửi payload `é` nhưng ghi length bằng `"é".length()` thì có lỗi gì?

**Đáp án:** Length bị ghi thành `1` trong khi payload UTF-8 gồm `2` byte. Parser sẽ đọc thiếu payload, để sót byte và có thể làm lệch header bản ghi kế tiếp.

**3.** Vì sao phải kiểm tra version *trước* khi parse payload?

**Đáp án:** Cấu trúc payload phụ thuộc phiên bản. Parser v1 không được mặc nhiên áp dụng luật v1 cho byte version mới nếu chưa biết tương thích.

**4.** Khi `readFully(payload)` ném `EOFException`, có nên trả payload đã đọc dở như bản ghi hợp lệ không?

**Đáp án:** Không. Giao thức công bố cần đủ `length` byte; bản ghi thiếu byte phải báo lỗi, không thể coi là hoàn chỉnh.

**5.** Magic number có ngăn người gửi giả mạo bản ghi hay không?

**Đáp án:** Không. Magic chỉ đánh dấu kiểu dữ liệu. Muốn xác thực nguồn và chống thay đổi có chủ đích cần cơ chế xác thực mật mã được thiết kế riêng.

**6.** Vì sao parser một stream chứa nhiều bản ghi không nên đóng `InputStream` ngay sau mỗi `readRecord`?

**Đáp án:** Vì tất cả bản ghi có thể dùng chung nguồn. Đóng stream sau bản ghi đầu tiên sẽ ngăn đọc những bản ghi tiếp theo.

**7.** Nếu phía gửi ghi length little-endian nhưng phía đọc dùng `DataInputStream.readInt()`, điều gì xảy ra?

**Đáp án:** Parser có thể nhận giá trị length sai, dẫn tới đọc sai hoặc từ chối theo giới hạn. Phải thống nhất byte order trong đặc tả.

## Tóm tắt

Một bản ghi nhị phân có **magic number + version + length + payload** chỉ đáng tin khi có một **hợp đồng byte rõ ràng**: vị trí/kích thước trường, byte order, đơn vị đo length, charset, giới hạn và quy tắc xử lý lỗi.

Với Java 17+, `DataOutputStream`/`DataInputStream` giúp ghi/đọc trường số big-endian. Hãy xác nhận magic, version, length **trước cấp phát**, đọc đúng payload bằng `readFully`, phân biệt EOF sạch với bản ghi bị cắt, rồi kiểm tra nội dung và khả năng tương thích theo phiên bản. Khi nhu cầu hệ thống tăng, cân nhắc cơ chế checksum/xác thực và chiến lược chuyển phiên bản thay vì sửa âm thầm format v1.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="binary-data-009">Kiểm tra độ dài trước khi cấp phát hoặc đọc dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra độ dài trước khi cấp phát hoặc đọc dữ liệu nhị phân trong Java

## 1. Vì sao kiểm tra độ dài là bước bắt buộc?

Khi chương trình Java đọc dữ liệu nhị phân từ file, socket, gói tin hoặc luồng được xây dựng từ đầu vào bên ngoài, nó có thể gặp một **trường độ dài** (length prefix) cho biết cần đọc bao nhiêu byte. Nếu ứng dụng tin ngay giá trị này rồi gọi `new byte[length]` hoặc `readNBytes(length)`, một giá trị sai hoặc cố ý quá lớn có thể dẫn tới sử dụng quá nhiều bộ nhớ, lỗi cấp phát, chờ dữ liệu vô hạn, ghi thiếu dữ liệu hoặc làm cạn tài nguyên.

Quy tắc trung tâm là:

> **Xác định giới hạn tin cậy, kiểm tra số lượng trước khi cấp phát và đọc, sau đó xác minh số byte thực nhận.** Với nguồn không biết trước độ dài, sử dụng cách đọc theo khối và hạn mức tích lũy.

Đây là bài học về **dữ liệu nhị phân** trong Java Core I/O. Các kỹ thuật áp dụng cho `InputStream`, `DataInputStream`, `ByteBuffer`, `FileChannel` và định dạng có cấu trúc; không nên nhầm kích thước byte với số ký tự trong văn bản.

Sau bài học, bạn có thể:

- Phân biệt **độ dài công bố** và **số byte thật sự nhận được**, cùng đơn vị đo của trường độ dài.
- Kiểm tra số âm, số 0, giá trị vượt ngưỡng, sai phiên bản định dạng và phép tính độ dài bị tràn.
- Đọc đủ một frame nhị phân bằng `DataInputStream.readFully` với giới hạn cấp phát rõ ràng.
- Xử lý trường hợp EOF đến sớm, nguồn không phát EOF, lỗi I/O và dữ liệu dư.
- Giới hạn dữ liệu khi không có length-prefix bằng vòng lặp streaming với bộ đệm nhỏ.
- Nhận ra khi nào `Files.size`, `available()`, `readAllBytes()` và `readNBytes(int)` không phù hợp làm biện pháp bảo vệ.
- Áp dụng hạn mức theo **byte trên wire**, số phần tử, kích thước đã giải nén và ngân sách bộ nhớ ở những tầng tương ứng.

## 2. Mô hình một frame nhị phân có trường độ dài

Một định dạng đơn giản có thể gồm:

```text
+----------------------+----------------------+
| 4-byte payloadLength | payloadLength bytes  |
+----------------------+----------------------+
         header                 payload
```

Ví dụ giả định 4 byte đầu biểu diễn số nguyên `int` **có dấu, big-endian** theo `DataInputStream.readInt()`. Khi header là `00 00 00 03`, ứng dụng phải nhận đúng ba byte payload tiếp theo.

Nhưng một header nhận từ bên ngoài **không phải sự thật đã được xác minh**. Các tình huống có thể xảy ra:

| Header công bố | Payload thực nhận | Ý nghĩa và cách xử lý |
| --- | --- | --- |
| 3 byte | 3 byte | Hợp lệ về độ dài nếu giá trị không vượt ngưỡng và nội dung đáp ứng định dạng |
| 0 byte | 0 byte | Có thể hợp lệ nếu giao thức cho phép payload rỗng |
| -1 byte | Bất kỳ | Giá trị âm không hợp lệ với độ dài byte |
| 2.147.483.647 byte | Ít byte | Không cấp phát mảng cực lớn; từ chối nếu vượt ngân sách |
| 100 byte | 20 byte rồi EOF | **Truncated frame**: dữ liệu bị thiếu, phải báo lỗi |
| 3 byte | 5 byte còn trong luồng | Một frame tiêu thụ 3 byte; 2 byte dư có thể là frame tiếp theo hoặc dữ liệu không hợp lệ tùy giao thức |
| 3 byte | 3 byte nhưng checksum sai | Đủ độ dài vẫn chưa chứng minh nội dung đúng hoặc toàn vẹn |

**Tách biệt ba việc**: (1) header nằm trong miền giá trị hợp lệ, (2) nguồn thực sự cung cấp đủ byte, (3) dữ liệu đáp ứng các kiểm tra cú pháp/kiểu/độ toàn vẹn. Chỉ vượt qua một bước không thay thế hai bước còn lại.

### 2.1. Đơn vị đo: byte, phần tử hay ký tự?

Các định dạng có thể dùng những kiểu “độ dài” khác nhau:

- **Số byte payload**: ứng dụng có thể dùng trực tiếp làm kích thước bộ đệm byte *sau khi kiểm tra*.
- **Số phần tử**: nếu mỗi bản ghi chiếm `elementSize` byte, tổng byte là `count × elementSize`; phải kiểm tra tràn trước khi cấp phát.
- **Số ký tự**: độ dài theo Unicode code point, UTF-16 `char` hoặc số byte UTF-8 có thể khác nhau; không tự quy đổi 1 ký tự = 1 byte.
- **Tổng chiều dài frame**: có giao thức tính cả header, có giao thức chỉ tính payload. Đọc sai hợp đồng này sẽ lệch ranh giới frame.
- **Độ dài chưa nén hoặc đã nén**: hai giá trị có thể khác rất xa; hạn mức chỉ trên input nén không ngăn được dữ liệu giải nén phình lớn.

Trước khi viết code, hãy ghi rõ hợp đồng: header dài bao nhiêu byte, signed/unsigned, endian, đơn vị, có tính header không, giá trị 0 có hợp lệ không, kích thước tối đa cho phép và phản ứng khi dữ liệu thiếu/dư.

## 3. Kiểm tra trước `new byte[length]`

Mã nguy hiểm:

```java
// Minh họa kiểu code KHÔNG AN TOÀN:
// InputStream/dataInput và giao thức chưa được kiểm soát.
int length = dataInput.readInt();
byte[] payload = new byte[length];
dataInput.readFully(payload);
```

Đây là **đoạn minh họa**, không phải chương trình độc lập; biến `dataInput` giả định là `DataInputStream` hợp lệ. Lỗi nằm ở thứ tự tin tưởng vào `length`:

- `length < 0` có thể gây `NegativeArraySizeException` khi tạo mảng.
- Giá trị cực lớn có thể gây `OutOfMemoryError` hoặc làm cạn heap trước khi đọc được payload.
- Nếu ứng dụng đọc từ mạng và đối tác công bố độ dài lớn nhưng chỉ gửi vài byte, chương trình có thể giữ bộ nhớ lớn trong khi chờ.
- `readFully` báo lỗi khi dữ liệu bị cắt ngắn, nhưng kiểm tra này diễn ra **sau** cấp phát; vì vậy không đủ để bảo vệ tài nguyên.

Mẫu an toàn hơn:

```java
int length = dataInput.readInt();

if (length < 0 || length > MAX_PAYLOAD_BYTES) {
    throw new IOException("Invalid payload length: " + length);
}
byte[] payload = new byte[length];
dataInput.readFully(payload);
```

Đây cũng là **đoạn minh họa**; `MAX_PAYLOAD_BYTES` phải là hằng số/chính sách đã xác định, `dataInput` là nguồn hợp lệ. Với dữ liệu rỗng, quyết định có chấp nhận `length == 0` hay không phụ thuộc định dạng.

**Điểm cốt lõi:** Giới hạn tối đa phải đến từ yêu cầu hệ thống và ngân sách tài nguyên, chứ không dựa vào giá trị “tối đa mà Java cho phép” hoặc con số kẻ gửi tự công bố.

### 3.1. Chọn ngân sách cấp phát

Một giới hạn per-frame cần xét:

1. **Loại dữ liệu:** ảnh thumbnail, metadata 1 KiB và ảnh gốc hàng trăm MiB có nhu cầu khác nhau.
2. **Số request đồng thời:** 1.000 request đều cấp phát 8 MiB đã tương ứng khoảng 8.000 MiB payload tạm, chưa gồm object/GC/buffer khác.
3. **Số bản ghi trong một request:** mỗi frame 1 MiB có thể hợp lệ nhưng 100.000 frame liên tiếp không thể đọc toàn bộ vào RAM.
4. **Kích thước kết quả đã xử lý:** dữ liệu giải nén, chuyển mã, cấu trúc đối tượng hoặc danh sách phần tử có thể lớn hơn byte input.
5. **Độ trễ và tốc độ nguồn:** một request có thể giữ vùng nhớ lâu nếu mạng chậm; cần timeout và quota tổng thời gian.
6. **Đầu ra:** nếu ghi vào `ByteArrayOutputStream` hoặc gom payload vào `List<byte[]>`, bộ nhớ tăng theo tổng dữ liệu, dù từng frame nhỏ.

Đặt giới hạn per-frame, per-request và đồng thời theo **ba tầng khác nhau** khi hệ thống cần bảo vệ toàn cục. Giới hạn `int`/kích thước mảng Java chỉ là ranh giới kỹ thuật, không phải ngân sách vận hành hợp lý.

## 4. Ví dụ Java 17+ hoàn chỉnh: đọc frame có giới hạn và nhận biết EOF sớm

Chương trình dưới đây dùng `DataInputStream` để đọc **4 byte length prefix big-endian** rồi kiểm tra giới hạn `1.024 byte` **trước** khi tạo mảng. `readFully` bảo đảm hoặc đọc đủ payload hoặc ném `EOFException`.

Lưu thành **`SafeFrameDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class SafeFrameDemo {
    private static final int MAX_PAYLOAD_BYTES = 1024;

    static byte[] readFrame(InputStream source) throws IOException {
        // Caller owns source and closes it when finished.
        DataInputStream input = new DataInputStream(source);

        int declaredLength = input.readInt();
        if (declaredLength < 0
                || declaredLength > MAX_PAYLOAD_BYTES) {
            throw new IOException(
                    "Invalid frame length: " + declaredLength);
        }

        byte[] payload = new byte[declaredLength];
        input.readFully(payload);
        return payload;
    }

    static byte[] makeFrame(int announcedLength, byte[] actualPayload)
            throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(announcedLength);
            output.write(actualPayload);
        }
        return buffer.toByteArray();
    }

    public static void main(String[] args) throws IOException {
        byte[] valid = makeFrame(3, new byte[] {10, 20, 30});
        try (InputStream input = new ByteArrayInputStream(valid)) {
            System.out.println(
                    "Valid: " + Arrays.toString(readFrame(input)));
        }

        byte[] tooLarge = makeFrame(2048, new byte[0]);
        try (InputStream input = new ByteArrayInputStream(tooLarge)) {
            try {
                readFrame(input);
            } catch (IOException error) {
                System.out.println("Rejected: " + error.getMessage());
            }
        }

        byte[] truncated = makeFrame(3, new byte[] {10});
        try (InputStream input = new ByteArrayInputStream(truncated)) {
            try {
                readFrame(input);
            } catch (EOFException error) {
                System.out.println("Truncated: "
                        + error.getClass().getSimpleName());
            }
        }
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac -encoding UTF-8 SafeFrameDemo.java
java SafeFrameDemo
```

**Stdout chính xác:**

```text
Valid: [10, 20, 30]
Rejected: Invalid frame length: 2048
Truncated: EOFException
```

**Giải thích từng trường hợp:**

- **Valid:** header cho biết 3 byte; giới hạn cho phép 3, mảng 3 byte được tạo, `readFully` nhận đúng ba byte và in ra theo thứ tự.
- **Rejected:** header công bố `2048 > 1024`. Chương trình báo lỗi ngay khi đọc header, **chưa cấp phát mảng payload lớn và chưa cố đọc phần payload**.
- **Truncated:** header hợp lệ công bố 3 byte, nguồn chỉ có 1 byte. `readFully` ném `EOFException` thay vì trả một mảng “đủ dài” nhưng chứa phần còn lại là zero.
- **Vòng đời:** các `ByteArrayInputStream` được đóng trong `try-with-resources` của mã gọi. `readFrame` tạo wrapper `DataInputStream` nhưng không đóng nguồn vì caller sở hữu `InputStream`; với luồng dùng chung nhiều frame, wrapper có thể được tạo một lần và quản lý chung ở tầng caller.

### 4.1. Giới hạn ví dụ

Chương trình trên kiểm tra **một frame** theo định dạng đơn giản. Khi xử lý nhiều frame nối tiếp, nó chưa giới hạn tổng số frame và tổng byte trên kết nối. Header hoặc payload còn có thể chứa checksum, magic number, phiên bản, kiểu dữ liệu và ràng buộc nội dung cần được kiểm tra riêng.

`readFrame` không đọc thêm byte để “kiểm tra EOF toàn luồng” vì trong giao thức gồm nhiều frame, byte còn lại có thể chính là header của frame kế. Nếu giao thức quy định **chỉ một frame duy nhất**, tầng gọi cần kiểm tra có trailing data và quyết định xử lý. Với stream mạng mở lâu, đọc thêm để chờ EOF có thể block; không thể kiểm tra trailing data bằng cách chờ vô hạn một cách máy móc.

## 5. Khi chưa biết tổng chiều dài: đọc streaming với hạn mức

Có loại nguồn không cung cấp size đáng tin: stream từ socket, HTTP body chunked, một số định dạng nén hoặc nguồn sinh dữ liệu theo thời gian. Nếu gọi `readAllBytes()`, `ByteArrayOutputStream` không giới hạn hoặc `transferTo` sang đích không giới hạn, chương trình có thể tiêu thụ tài nguyên rất lớn.

Cách xử lý là:

1. Đặt **giới hạn tối đa tổng byte** theo nghiệp vụ, ví dụ `MAX_BYTES`.
2. Sử dụng bộ đệm nhỏ và chỉ yêu cầu đọc tối đa **số byte còn được phép**.
3. Cập nhật bộ đếm sau mỗi lần đọc thành công; không nhầm `read() == -1` với dữ liệu rỗng.
4. Khi đạt đúng hạn mức, xác định nguồn đã kết thúc hay còn byte. Nếu còn byte, báo lỗi vượt hạn mức; không báo thành công với kết quả cắt cụt nếu giao thức yêu cầu toàn bộ.
5. Nếu đang ghi file có giá trị nghiệp vụ, dùng **file tạm**, dọn khi lỗi và chỉ công bố khi toàn bộ quá trình đã thành công.

### 5.1. Ví dụ Java 17+ có giới hạn tích lũy

Lưu thành **`BoundedBinaryCopyDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class BoundedBinaryCopyDemo {
    static long copyWithLimit(
            InputStream input, OutputStream output, long maxBytes)
            throws IOException {
        if (maxBytes < 0) {
            throw new IllegalArgumentException(
                    "maxBytes must be nonnegative");
        }

        byte[] buffer = new byte[4096];
        long copied = 0;

        while (true) {
            long remaining = maxBytes - copied;

            if (remaining == 0) {
                // One extra read distinguishes exact size from overflow.
                if (input.read() != -1) {
                    throw new IOException(
                            "Limit exceeded: " + maxBytes);
                }
                return copied;
            }

            int requested = (int) Math.min(
                    (long) buffer.length, remaining);
            int count = input.read(buffer, 0, requested);

            if (count == -1) {
                return copied;
            }
            if (count == 0) {
                throw new IOException("Input made no progress");
            }

            output.write(buffer, 0, count);
            copied += count;
        }
    }

    public static void main(String[] args) throws IOException {
        byte[] source = {10, 20, 30, 40, 50};

        try (InputStream input = new ByteArrayInputStream(source);
             ByteArrayOutputStream output =
                     new ByteArrayOutputStream()) {
            System.out.println("Allowed: "
                    + copyWithLimit(input, output, 5));
        }

        try (InputStream input = new ByteArrayInputStream(source);
             ByteArrayOutputStream output =
                     new ByteArrayOutputStream()) {
            try {
                copyWithLimit(input, output, 4);
            } catch (IOException error) {
                System.out.println("Rejected: " + error.getMessage());
            }
            System.out.println("Partial bytes: " + output.size());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 BoundedBinaryCopyDemo.java
java BoundedBinaryCopyDemo
```

**Stdout chính xác:**

```text
Allowed: 5
Rejected: Limit exceeded: 4
Partial bytes: 4
```

**Phân tích:**

- Trường hợp giới hạn bằng 5: sao chép đủ 5 byte. Lần đọc thử tiếp theo trả EOF nên kết quả là 5.
- Trường hợp giới hạn bằng 4: chỉ bốn byte đầu được ghi. Lần đọc thử sau đó tìm thấy byte thứ năm và ném `IOException`, không trả kết quả thành công.
- Đích `ByteArrayOutputStream` vẫn giữ **4 byte đã ghi trước lỗi**. Không có rollback tự động. Với dữ liệu thực, không công bố output dở; dùng tệp tạm hoặc cơ chế transaction phù hợp.
- Bước đọc thêm một byte để xác định vượt hạn mức **có thể block** nếu nguồn chưa phát EOF. Với socket hoặc nguồn vô hạn, phải cấu hình deadline, timeout hay cancellation ở tầng nguồn. Một số giao thức có length-prefix đáng tin sau khi đã kiểm tra và không cần chờ EOF toàn kết nối.
- Ví dụ dùng `ByteArrayOutputStream` vì chỉ có 5 byte. Trong hệ thống thực, output phải là sink giới hạn/streaming nếu dữ liệu lớn; buffer 4 KiB **không giới hạn tổng byte** khi output vẫn tích lũy.

**Bài học quan trọng:** Giới hạn **bộ đệm** (`4096`) và giới hạn **toàn bộ input** (`maxBytes`) là hai điều khác nhau. Một vòng lặp có buffer nhỏ vẫn có thể đọc vô hạn nếu không kiểm tra tổng.

## 6. Đọc đủ byte và phát hiện payload bị cắt ngắn

Các API có hợp đồng khác nhau:

| API | Hành vi cần nhớ |
| --- | --- |
| `InputStream.read()` | Trả 0–255 cho byte đọc được; `-1` khi EOF |
| `InputStream.read(byte[])` | Trả số byte thực đọc; có thể ít hơn kích thước mảng |
| `InputStream.read(byte[], off, len)` | Chỉ đọc tối đa `len` byte; có thể trả ít hơn; kiểm tra `count` |
| `DataInputStream.readFully(byte[])` | Đọc đến khi đủ mảng hoặc ném `EOFException` nếu nguồn kết thúc sớm |
| `DataInputStream.readFully(byte[], off, len)` | Đọc đúng phân đoạn yêu cầu hoặc ném `EOFException` |
| `InputStream.readNBytes(int len)` | Trả mảng chứa tối đa `len` byte, **có thể ít hơn** nếu EOF; phải kiểm tra độ dài |
| `InputStream.readAllBytes()` | Đọc đến EOF, có thể cần bộ nhớ lớn; không áp dụng hạn mức riêng |
| `InputStream.transferTo(OutputStream)` | Sao chép đến EOF; số trả về là byte đã truyền, không tự áp quota |
| `Files.size(Path)` | Lấy kích thước file được báo tại một thời điểm; không khóa nội dung trong suốt lần đọc |
| `InputStream.available()` | Ước lượng byte có thể đọc ngay không chờ; **không** báo tổng dung lượng hoặc EOF |

Ví dụ vòng lặp nguy hiểm vì tin rằng một lần đọc trả đủ:

```java
// SAI về mặt giao thức nếu yêu cầu chính xác 100 byte:
byte[] payload = new byte[100];
int count = input.read(payload);
// count có thể là 17, 40 hoặc -1; payload chưa chắc đầy.
```

Với giao thức yêu cầu chính xác 100 byte, sau kiểm tra giới hạn có thể dùng `DataInputStream.readFully(payload)` hoặc vòng lặp cộng dồn `count` cho đến khi đủ; EOF sớm phải được coi là dữ liệu không hoàn chỉnh, không phải phần đuôi bằng 0.

### 6.1. Xử lý header bị cắt ngắn

`DataInputStream.readInt()` đọc 4 byte theo big-endian. Nếu mới nhận được 1–3 byte rồi EOF, nó ném `EOFException` trước khi có `declaredLength` hợp lệ. Đừng gán giá trị mặc định 0 và coi đó là frame rỗng. Phải phân biệt EOF ngay ranh giới giữa các frame (có thể là kết thúc hợp lệ) với EOF **giữa header** hoặc **giữa payload** (sai định dạng).

### 6.2. Còn byte phía sau frame thì sao?

Nếu protocol cho phép nhiều frame liên tiếp, đọc đúng số byte của frame hiện tại rồi giữ byte còn lại để đọc header kế tiếp. Nếu định dạng file chỉ cho một frame, byte trailing là lỗi hoặc phần metadata tùy đặc tả. Không tự gọi `available() == 0` để kết luận “hết frame”: `available` không đủ nghĩa để xác định cấu trúc.

## 7. Kiểm tra số học để ngăn tràn và lỗi đơn vị

Khi trường độ dài là **số phần tử**, bạn cần tính tổng byte. Ví dụ ảnh có `width`, `height`, `channels` và `bytesPerChannel`, mỗi giá trị có thể do input công bố. Không viết:

```java
// NGUY HIỂM: phép nhân int có thể overflow.
int bytes = width * height * channels * bytesPerChannel;
byte[] pixels = new byte[bytes];
```

Đây là đoạn minh họa với biến chưa khai báo. Phép nhân kiểu `int` có thể tràn thành số âm hoặc số nhỏ; sau đó code có thể cấp phát sai hoặc kiểm tra giới hạn sai. Đổi sang `long` từ **trước phép nhân đầu tiên**, kiểm tra miền từng chiều và tổng byte, sau đó mới ép sang `int` khi đã chắc chắn phù hợp.

Mẫu minh họa với `long` và `Math.multiplyExact`:

```java
// Minh họa: width, height, channels là các số lấy từ header.
// Không chạy độc lập nếu chưa khai báo biến và ràng buộc.
if (width <= 0 || height <= 0 || channels <= 0) {
    throw new IOException("Invalid dimensions");
}

long pixels;
long totalBytes;
try {
    pixels = Math.multiplyExact((long) width, (long) height);
    totalBytes = Math.multiplyExact(pixels, (long) channels);
} catch (ArithmeticException overflow) {
    throw new IOException("Dimensions overflow", overflow);
}

long maxImageBytes = 8L * 1024 * 1024;
if (totalBytes > maxImageBytes) {
    throw new IOException("Image too large");
}
byte[] image = new byte[(int) totalBytes];
```

Ví dụ giả định **một byte mỗi channel**; nếu có `bytesPerChannel`, phải nhân thêm bằng phép toán được kiểm soát. Đây là minh họa cho cấu trúc validation, chưa kiểm tra màu/stride/padding/dung lượng metadata hoặc toàn vẹn ảnh.

Tại sao không chỉ kiểm tra `bytes <= Integer.MAX_VALUE`? Vì ngân sách thật có thể nhỏ hơn rất nhiều và việc cấp phát gần giới hạn mảng Java hoàn toàn không phù hợp trong hệ thống nhiều request đồng thời. Tránh việc chuyển `long` hoặc unsigned length sang `int` **trước khi** kiểm tra giới hạn, vì cast có thể thay đổi giá trị.

### 7.1. Length unsigned và endian

Một header gồm 4 byte có thể được định nghĩa là `uint32` thay vì signed `int`. Java `DataInputStream.readInt()` diễn giải nó theo signed two's complement; nếu định dạng thật là unsigned, giá trị lớn hơn `Integer.MAX_VALUE` sẽ xuất hiện dưới dạng số âm.

Nếu giao thức dùng unsigned 32-bit big-endian, có thể diễn giải qua:

```java
// Minh họa sau khi đã có DataInputStream input:
long unsignedLength = Integer.toUnsignedLong(input.readInt());
if (unsignedLength > MAX_ALLOWED_BYTES) {
    throw new IOException("Length too large");
}
```

**Không** lập tức cast `unsignedLength` về `int`. `MAX_ALLOWED_BYTES` phải được định nghĩa và áp dụng trước, thường thấp hơn nhiều so với giới hạn `int`. Với little-endian, cần API/cách đọc phù hợp, chẳng hạn `ByteBuffer.order(ByteOrder.LITTLE_ENDIAN)` sau khi đã kiểm soát kích thước input. Đọc sai endian có thể khiến độ dài nhỏ biến thành con số rất lớn.

### 7.2. Phép cộng khi tính tổng các phần

Nếu nhiều chunk có độ dài `length`, đừng viết `total += length` rồi mới hỏi “có vượt max không” khi `total` có nguy cơ overflow. Với số không âm và giới hạn xác định, kiểm tra trước:

```java
// Minh họa: total >= 0, length >= 0, maxBytes >= 0.
if (length > maxBytes - total) {
    throw new IOException("Total size exceeded");
}
total += length;
```

Cách này đúng khi đã bảo đảm `0 <= total <= maxBytes` và `length >= 0`. Nếu không giữ được các bất biến đó, phải kiểm tra/áp dụng `Math.addExact` và giới hạn trước khi dùng kết quả. Đừng coi một quota per-chunk đủ để bảo vệ tổng nhiều chunk.

## 8. File metadata, TOCTOU và dữ liệu thay đổi trong lúc đọc

Có thể kiểm tra kích thước trước khi mở file:

```java
// Minh họa; path là Path hợp lệ do caller kiểm soát.
long size = java.nio.file.Files.size(path);
if (size < 0 || size > MAX_ALLOWED_BYTES) {
    throw new IOException("File too large");
}
```

Kiểm tra này hữu ích cho file thông thường có kích thước ổn định, nhưng **không phải cơ chế bảo vệ duy nhất**:

- Giữa lúc gọi `Files.size` và lúc mở/đọc, file có thể bị sửa, thay thế hoặc tăng dung lượng. Đây là vấn đề *time of check to time of use* (TOCTOU).
- Nhiều nguồn không phải file thông thường (socket, pipe, thiết bị, nguồn nén) không có tổng độ dài đáng tin.
- Một số hệ thống file đặc biệt có hành vi kích thước khác với file thường.
- `InputStream.available()` không có nghĩa “tổng số byte còn lại”; dùng nó để cấp phát hoặc xác nhận đã hết dữ liệu là sai.

Nếu đòi hỏi giới hạn chính xác, hãy **enforce trong lúc đọc**, đồng thời có thể dùng metadata để từ chối sớm những trường hợp hiển nhiên quá lớn. Khi cần chống thay đổi file/đường dẫn có chủ đích, còn phải kiểm soát đường dẫn, quyền, symlink, định danh file và vòng đời handle theo thiết kế hệ thống.

## 9. Những dạng dữ liệu cần nhiều hơn một hạn mức

### 9.1. Dữ liệu nén và nguy cơ phình to

Một file ZIP/gzip có thể nhỏ trên ổ đĩa nhưng giải nén thành lượng dữ liệu rất lớn. Kiểm tra kích thước file nén trước khi cấp phát **không đủ**. Khi dùng `GZIPInputStream`, `ZipInputStream` hoặc bộ giải nén khác, hãy đặt hạn mức trên **tổng byte đầu ra giải nén** và tổng số file/entry, xử lý đường dẫn entry nếu tạo file ra ổ đĩa, hạn chế độ sâu/lồng nhau và thời gian xử lý nếu ứng dụng yêu cầu.

### 9.2. Số bản ghi, số chiều và các cấu trúc lồng nhau

Nếu header nói có 10 triệu phần tử, một frame có thể không quá lớn riêng lẻ nhưng tổng đối tượng Java tạo ra có thể lớn hơn nhiều lần phần byte trên wire. Đặt giới hạn số phần tử, số lớp lồng, chiều mảng và tổng bộ nhớ ước tính trước khi dựng cấu trúc. Với ma trận, ảnh hay âm thanh nhiều kênh, kiểm tra phép nhân kích thước an toàn như mục 7.

### 9.3. Chuỗi ký tự được chứa trong frame nhị phân

Nếu frame chứa chuỗi UTF-8, giới hạn **số byte** trước khi cấp phát/giải mã; số ký tự Java có thể khác số byte. Sau đó validate charset, chính sách malformed input, giới hạn số ký tự/code points nếu nghiệp vụ cần. Không dùng `String.length()` như đại diện cho số byte trên mạng.

### 9.4. Dữ liệu đến từ mạng

Length-prefix có thể bị làm giả. Ngoài giới hạn byte, cần read timeout/deadline, cancellation, kiểm soát số kết nối và số request đồng thời. Nếu peer công bố 1.024 byte rồi gửi rất chậm, một `readFully` vẫn có thể chờ lâu ngay cả khi kích thước đã được kiểm tra hợp lệ. Đừng dùng `InputStream.available()` để “tránh bị block”: nó không cho biết bao giờ sẽ có đủ frame.

## 10. Cấp phát trước hay xử lý theo khối?

| Trường hợp | Cách phù hợp | Vì sao |
| --- | --- | --- |
| Frame nhỏ có size đáng tin sau validation | Kiểm tra giới hạn → `new byte[length]` → `readFully` | Dễ parse theo mảng, kiểm soát EOF rõ |
| File rất lớn, cần copy/hash/scan tuần tự | Buffer hữu hạn, vòng lặp theo khối | Không cần giữ toàn bộ byte |
| Nguồn không biết length | Streaming + quota tổng + timeout/cancellation | Tránh đọc vô hạn/tích lũy không kiểm soát |
| Metadata công bố size nhưng file có thể thay đổi | Kiểm tra metadata để từ chối sớm **và** quota trong lúc đọc | Chặn TOCTOU về kích thước |
| Dữ liệu nén | Giới hạn input lẫn output giải nén | Chặn phình to sau giải nén |
| Tập bản ghi có count/length | Validate count, length, tổng tích lũy và phép nhân | Tránh overflow và cạn heap |
| Cần giữ file kết quả cũ nếu input lỗi | Ghi file tạm → verify → công bố/replace theo chính sách | Tránh ghi đè file thật bằng bản dở |

Nếu chỉ muốn tính SHA-256 của file nhiều GB, đọc từng khối để cập nhật digest; không đọc tất cả byte vào một mảng. Nếu muốn parse một frame nhỏ làm object, kiểm tra độ dài trước rồi đọc đúng frame là hợp lý.

## 11. Lỗi phổ biến và cách xử lý

1. **Tin ngay header:** lấy `length` từ nguồn không đáng tin rồi cấp phát mà không có `MAX_LENGTH`.
2. **Chỉ kiểm tra `length > MAX`:** bỏ sót số âm; một số định dạng còn cấm length 0.
3. **Ép `long`/unsigned sang `int` quá sớm:** có thể tràn hoặc thành số âm khiến kiểm tra sai.
4. **Nhân/cộng kích thước bằng `int`:** phép toán tràn trước khi kiểm tra tổng byte.
5. **Giả định `read(byte[])` đọc đầy buffer:** có thể trả ít hơn; phải đọc đến đủ hoặc báo EOF sớm.
6. **Xem EOF sớm là dữ liệu hợp lệ:** mảng đã cấp phát chứa byte 0 ở phần chưa đọc; không được im lặng chấp nhận.
7. **Tin `available()` cho biết kích thước toàn bộ:** API này chỉ ước lượng byte sẵn có không phải chờ.
8. **Dùng `readAllBytes()` trên nguồn không có giới hạn:** có thể hết heap hoặc chờ vô hạn.
9. **Chỉ đặt quota trên từng chunk:** tổng một triệu chunk vẫn vượt ngân sách.
10. **Chỉ đo compressed bytes:** dữ liệu giải nén có thể phình rất lớn.
11. **Nhầm byte với ký tự:** UTF-8 biến một code point thành số byte khác nhau.
12. **Không xử lý số frame nhiều hơn dự kiến:** một frame nhỏ không có nghĩa toàn kết nối nhỏ.
13. **Tin `Files.size` không thay đổi:** không bảo vệ khỏi file tăng/thay đổi trong lúc đọc.
14. **Không giới hạn thời gian với nguồn mạng:** frame hợp lệ về length vẫn có thể không bao giờ nhận đủ byte.
15. **Ghi output trực tiếp rồi công bố khi lỗi:** phần dữ liệu dở có thể trông như file hoàn chỉnh.
16. **Không kiểm tra magic/version/checksum:** độ dài đúng không bảo đảm định dạng và nội dung đúng.

## 12. Checklist kiểm thử và bài tập

### 12.1. Kiểm thử một frame có length prefix

- **Length = 0**: chấp nhận hoặc từ chối theo đặc tả, không coi tự động là lỗi.
- **Length = 1**: đọc đúng một byte.
- **Length = MAX**: chấp nhận nếu có đủ payload và tài nguyên.
- **Length = MAX + 1**: từ chối **trước khi cấp phát**.
- **Length = -1**: từ chối trước khi tạo mảng.
- **Header thiếu 1–3 byte**: `readInt` báo EOF sớm.
- **Payload thiếu một byte**: `readFully` báo `EOFException`.
- **Payload chính xác**: parse/validate nội dung theo định dạng.
- **Trailing byte**: xử lý theo quy tắc một frame hay nhiều frame.
- **Nhiều frame hợp lệ liên tiếp**: kiểm soát quota tổng và số lượng.
- **Nguồn đọc chậm**: timeout/deadline phát huy tác dụng ở tầng nguồn.

### 12.2. Thực hành

1. Đổi `MAX_PAYLOAD_BYTES` trong `SafeFrameDemo` thành `3`, thử độ dài 0, 3, 4 và -1; ghi lại hành vi mong đợi.
2. Sửa đầu vào của ví dụ thành header 4 byte nhưng payload có 3 byte; chứng minh code ném `EOFException`.
3. Viết parser cho định dạng `count (int) + count × 8 byte`, dùng `Math.multiplyExact((long) count, 8L)`, kiểm tra số âm, số phần tử và tổng byte trước khi cấp phát.
4. Trong `BoundedBinaryCopyDemo`, thử max bằng 0, 4, 5, 6. Phân biệt trường hợp input rỗng, vừa đủ và vượt ngưỡng; chỉ rõ output dở sau lỗi.
5. Thiết kế giao thức nhiều frame: mỗi frame ≤ 1 MiB nhưng tổng một request ≤ 16 MiB. Làm sao kiểm tra quota tổng **trước khi cộng độ dài frame**?
6. So sánh dùng `Files.size` trước khi mở và đo bytes trong lúc đọc; mô tả một thay đổi file giữa hai bước khiến metadata không còn đúng.
7. Thiết kế xử lý file nén có output giới hạn 50 MiB; giải thích vì sao kiểm tra kích thước ZIP/gzip ban đầu chưa đủ.
8. Với một giao thức có trường `uint32` little-endian, xác định đúng API để đọc header, chuyển giá trị sang `long`, validate và chỉ sau đó cast sang `int` khi thật sự cần.
9. Giả lập nguồn chậm hoặc ném `IOException`; kiểm tra quá trình dừng an toàn, đóng stream và không công bố file đích chưa đầy đủ.

## 13. Tự đánh giá

Bạn đã nắm bài khi có thể giải thích rõ:

- Tại sao `new byte[dataInput.readInt()]` là rủi ro ngay cả khi phía gửi “thông thường” luôn gửi dữ liệu nhỏ?
- Tại sao `DataInputStream.readFully` quan trọng nhưng **không thay thế** kiểm tra size trước cấp phát?
- `readNBytes(length)` và `readAllBytes()` có thể làm tăng sử dụng bộ nhớ thế nào?
- Tại sao số byte có sẵn của `available()` khác với kích thước nguồn?
- Tại sao cần kiểm tra phép nhân/cộng **trước** khi đưa kết quả vào bộ cấp phát?
- Có gì khác giữa limit 4 KiB cho buffer và limit 4 MiB cho tổng input?
- Khi nào cần giới hạn **byte sau giải nén** và số phần tử, không chỉ byte nén?
- Nếu đọc thất bại ở giữa payload, kết quả đã ghi ra file đích cần được xử lý thế nào?

**Ghi nhớ:** Độ dài lấy từ nguồn ngoài chỉ là **khẳng định chưa được tin cậy**. Hãy xác định đơn vị và trần hợp lệ, kiểm tra phép toán kích thước và số lượng trước cấp phát, sau đó yêu cầu đủ byte hoặc báo lỗi. Với dữ liệu không có length tin cậy, đọc theo khối có **quota tích lũy**, kiểm soát timeout, và xử lý output dở. Cách này bảo vệ cả tính đúng dữ liệu lẫn ngân sách bộ nhớ và tài nguyên I/O.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="binary-data-010">Phân biệt định dạng nhị phân tự thiết kế với Java serialization</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt định dạng nhị phân tự thiết kế với Java serialization

Khi cần lưu dữ liệu thành byte hoặc truyền qua mạng, Java có ít nhất hai hướng tiếp cận rất khác nhau:

1. **Tự thiết kế định dạng nhị phân**: ứng dụng định nghĩa chính xác các byte nào được ghi, thứ tự, kích thước, kiểu dữ liệu, phiên bản và cách giải mã. Có thể dùng `DataOutputStream`/`DataInputStream`, `ByteBuffer` hoặc byte streams.
2. **Java Object Serialization**: dùng `ObjectOutputStream` và `ObjectInputStream` để mã hóa/khôi phục **đồ thị đối tượng Java** theo cơ chế serialization của nền tảng, thường với các lớp triển khai `Serializable`.

Cả hai đều tạo ra dãy byte, nhưng **mục đích và hợp đồng dữ liệu** không giống nhau. Định dạng tự thiết kế là một *giao thức/schema do bạn sở hữu*; Java serialization mang nhiều ngữ nghĩa liên quan đến lớp, đối tượng, tham chiếu và khả năng tương thích của Java.

Bài này tập trung vào lựa chọn, triển khai, tính tương thích và xử lý dữ liệu không tin cậy. Các ví dụ chạy được trên Java 17+.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Phân biệt **serialize object graph** với **encode một schema nhị phân**.
- Giải thích `Serializable`, `serialVersionUID`, `transient`, `ObjectOutputStream.writeObject` và `ObjectInputStream.readObject`.
- Thiết kế một format nhị phân gồm magic, version, số bản ghi, số nguyên và chuỗi UTF-8 có tiền tố độ dài.
- Sử dụng `DataOutputStream`/`DataInputStream` đúng thứ tự đọc–ghi, biết endian và xử lý `EOFException`.
- Phân biệt UTF-8 tiêu chuẩn với **modified UTF-8** của `writeUTF/readUTF`.
- Đặt giới hạn số bản ghi, độ dài chuỗi, kích thước payload và kiểm tra tính hợp lệ **trước khi cấp phát**.
- Lựa chọn cơ chế lưu trữ phù hợp khi cần ổn định format, trao đổi liên ngôn ngữ, quản lý phiên bản hoặc xử lý dữ liệu từ bên ngoài.
- Nhận diện rủi ro của Java deserialization với dữ liệu không tin cậy và vai trò giới hạn của `ObjectInputFilter`.

## 2. So sánh tổng quan

| Tiêu chí | Định dạng nhị phân tự thiết kế | Java serialization |
| --- | --- | --- |
| Đầu vào logic | Giá trị/trường theo schema đã định nghĩa | Đối tượng và đồ thị đối tượng Java |
| Công cụ điển hình | `DataOutputStream`, `DataInputStream`, `ByteBuffer` | `ObjectOutputStream`, `ObjectInputStream` |
| Bố cục byte | Do bạn quy định và kiểm thử | Theo giao thức Java Object Serialization |
| Tên lớp Java trong format | Không bắt buộc | Thường có descriptor/thông tin kiểu lớp trong luồng |
| Tham chiếu dùng chung / chu trình | Phải tự thiết kế ID/reference nếu cần | Cơ chế object handles hỗ trợ chia sẻ tham chiếu và đồ thị vòng |
| Thay đổi schema | Tự định nghĩa version, optional field, migration | Phụ thuộc quy tắc tương thích serialization và thiết kế lớp |
| Khả năng đọc bằng ngôn ngữ khác | Có thể, nếu đặc tả đủ rõ | Không phải format trao đổi liên ngôn ngữ thông dụng |
| Mức độ kiểm soát kích thước | Chủ động nếu format tối giản | Có metadata/overhead; không đoán trước số byte |
| Dữ liệu không tin cậy | Có thể giới hạn và xác thực từng trường; vẫn phải phòng parser bug | **Không nên deserialize nguồn không tin cậy**; nguy cơ qua lớp/callback/gadget |
| Use case gợi ý | Giao thức file/mạng, firmware, cache có schema, lưu lâu dài | Trao đổi hoặc lưu đồ thị đối tượng Java trong môi trường **tin cậy**, có kiểm soát lớp/phiên bản |

**Không đánh đồng** “Java serialization” với mọi hành động chuyển dữ liệu Java thành byte. Một format tự thiết kế, JSON, Protocol Buffers hoặc CBOR cũng đều là các dạng serialization theo nghĩa rộng; trong bài này **Java serialization** được dùng theo nghĩa hẹp của `ObjectOutputStream`/`ObjectInputStream` và cơ chế `Serializable`.

## 3. Format nhị phân tự thiết kế là gì?

Một format nhị phân là **hợp đồng byte** giữa bên ghi và bên đọc. Ví dụ, ta quy định một file chứa danh sách người:

~~~text
Header:
+------------------+------------------+------------------+
| magic: 4 byte    | version: 2 byte  | count: 4 byte    |
+------------------+------------------+------------------+

Mỗi bản ghi:
+------------------+------------------+--------------------------+
| id: int32, 4 B   | nameLength: 4 B  | name: UTF-8, N byte      |
+------------------+------------------+--------------------------+
~~~

Cần xác định **từng chi tiết**, không chỉ viết “ghi một số nguyên và một chuỗi”:

- **Magic**: bốn byte `50 45 50 4C` (ASCII “PEPL”) để nhận diện loại payload.
- **Version**: số nguyên 16-bit **unsigned**, thứ tự byte **big-endian**; phiên bản đầu là `1`.
- **Count**: số nguyên 32-bit có dấu, big-endian, chỉ chấp nhận `0..1000`.
- **ID**: số nguyên 32-bit có dấu, big-endian; ý nghĩa/miền giá trị phải do ứng dụng quy định.
- **Name length**: số nguyên 32-bit có dấu, big-endian, chỉ chấp nhận `0..4096` byte.
- **Name**: đúng `nameLength` byte theo UTF-8 tiêu chuẩn, **không có byte NUL kết thúc**.
- **Thứ tự**: ghi header trước, sau đó từng bản ghi theo thứ tự; không tự thêm separator/padding.
- **Điểm kết thúc**: với một payload đóng gói riêng, sau `count` bản ghi không được có dữ liệu thừa; với luồng chứa nhiều frame liên tiếp phải có framing riêng.

Format tự thiết kế phải định nghĩa rõ **endianness**, trường optional, nullable, giới hạn dữ liệu, cách xử lý version lạ, lỗi thiếu byte và dữ liệu dư. Một cặp encoder/decoder chạy được trên cùng máy **chưa đủ** để thành giao thức đáng tin cậy nếu đặc tả byte còn mơ hồ.

## 4. `DataOutputStream` và `DataInputStream`: công cụ cho format tự định nghĩa

`DataOutputStream` có các phương thức như `writeByte`, `writeShort`, `writeInt`, `writeLong`, `writeBoolean`, `write(byte[])`; `DataInputStream` có các phương thức đọc tương ứng.

Với các số nguyên nhiều byte:

- `writeShort` ghi hai byte; `writeInt` ghi bốn byte; `writeLong` ghi tám byte.
- Các phương thức này dùng **byte có trọng số cao trước** (*big-endian*), không tự thay đổi theo CPU.
- `readUnsignedShort` đọc 2 byte thành `int` trong khoảng `0..65535`; phù hợp version unsigned.
- `readFully(byte[])` cố đọc **đủ** số byte yêu cầu hoặc ném `EOFException` nếu hết nguồn trước khi đủ.
- Một `InputStream.read(byte[])` đơn lẻ **không bảo đảm** lấp đầy mảng, vì vậy khi cần chính xác N byte, `readFully` hoặc vòng lặp tích lũy là quan trọng.
- Các phương thức I/O có thể ném `IOException` do lỗi nguồn/đích; `EOFException` là trường hợp thiếu dữ liệu tại thời điểm cần đủ byte.

Điểm quyết định: `DataOutputStream` **không tự tạo schema**. Nếu bạn ghi `writeInt(id)` rồi `writeInt(nameLength)` và các byte tên, decoder phải đọc **đúng thứ tự**. Đảo vị trí sẽ khiến dữ liệu bị giải nghĩa sai, dù cả hai bên đều dùng lớp Java giống nhau.

### Đừng mặc định `writeUTF()` là UTF-8 tiêu chuẩn

`DataOutputStream.writeUTF(String)` ghi **hai byte độ dài** rồi ghi ký tự bằng **modified UTF-8**, một biến thể của UTF-8. Nó có giới hạn tối đa **65.535 byte trong phần mã hóa**, vượt quá có thể ném `UTFDataFormatException`. `DataInputStream.readUTF()` là cặp đọc phù hợp.

Nếu format cần trao đổi với phần mềm không phải Java hoặc cần định nghĩa chính xác UTF-8 theo chuẩn, cách dễ đặc tả hơn là:

~~~java
byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
out.writeInt(bytes.length);
out.write(bytes);
~~~

Bên đọc phải kiểm tra độ dài trước khi cấp phát, dùng `readFully`, rồi giải mã UTF-8. Đoạn trên là **mảnh mã** với `text` kiểu `String` và `out` là `DataOutputStream` đã khai báo. `String.length()` đo **UTF-16 code units**, không phải số byte UTF-8; không được ghi `text.length()` làm byte length.

## 5. Ví dụ Java 17+: format nhị phân có header, version và UTF-8

Tạo file **`CustomBinaryDemo.java`**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

public class CustomBinaryDemo {
    private static final int MAGIC = 0x5045504C; // "PEPL"
    private static final int VERSION = 1;
    private static final int MAX_RECORDS = 1000;
    private static final int MAX_NAME_BYTES = 4096;

    record Person(int id, String name) {}

    static byte[] encode(List<Person> people) throws IOException {
        if (people.size() > MAX_RECORDS) {
            throw new IOException("Too many records");
        }

        ByteArrayOutputStream sink = new ByteArrayOutputStream();

        try (DataOutputStream out = new DataOutputStream(sink)) {
            out.writeInt(MAGIC);
            out.writeShort(VERSION);
            out.writeInt(people.size());

            for (Person person : people) {
                byte[] nameBytes =
                        person.name().getBytes(StandardCharsets.UTF_8);

                if (nameBytes.length > MAX_NAME_BYTES) {
                    throw new IOException("Name is too long");
                }

                out.writeInt(person.id());
                out.writeInt(nameBytes.length);
                out.write(nameBytes);
            }
            out.flush();
        }

        return sink.toByteArray();
    }

    static List<Person> decode(byte[] payload) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(payload))) {

            if (in.readInt() != MAGIC) {
                throw new IOException("Bad magic");
            }

            int version = in.readUnsignedShort();
            if (version != VERSION) {
                throw new IOException("Unsupported version: " + version);
            }

            int count = in.readInt();
            if (count < 0 || count > MAX_RECORDS) {
                throw new IOException("Invalid record count");
            }

            List<Person> result = new ArrayList<>(count);

            for (int i = 0; i < count; i++) {
                int id = in.readInt();
                int nameLength = in.readInt();

                if (nameLength < 0 || nameLength > MAX_NAME_BYTES) {
                    throw new IOException("Invalid name length");
                }

                byte[] nameBytes = new byte[nameLength];
                in.readFully(nameBytes);

                String name = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(nameBytes))
                        .toString();

                result.add(new Person(id, name));
            }

            // Chỉ kiểm tra EOF vì payload này được truyền vào như
            // một thông điệp byte[] độc lập, không ghép nhiều frame.
            if (in.read() != -1) {
                throw new IOException("Unexpected trailing bytes");
            }

            return result;
        }
    }

    public static void main(String[] args) throws IOException {
        List<Person> people = List.of(
                new Person(7, "An"),
                new Person(9, "Bình")
        );

        byte[] encoded = encode(people);

        System.out.println("size=" + encoded.length);
        System.out.println(
                "magic=" + HexFormat.of().formatHex(encoded, 0, 4));
        System.out.println("decoded=" + decode(encoded));

        byte[] truncated = Arrays.copyOf(
                encoded, encoded.length - 1);
        try {
            decode(truncated);
            System.out.println("unexpected success");
        } catch (EOFException exception) {
            System.out.println("truncated=EOFException");
        }
    }
}
~~~

Biên dịch (chỉ định encoding của file nguồn để bảo toàn chữ “Bình” trên các môi trường Java 17) và chạy:

~~~bash
javac -encoding UTF-8 CustomBinaryDemo.java
java CustomBinaryDemo
~~~

**Stdout theo nội dung Unicode của chương trình:**

~~~text
size=33
magic=5045504c
decoded=[Person[id=7, name=An], Person[id=9, name=Bình]]
truncated=EOFException
~~~

### Vì sao đúng 33 byte?

- Header: `4 + 2 + 4 = 10` byte.
- Người thứ nhất: `4` byte ID + `4` byte độ dài + `2` byte UTF-8 của `An` = `10` byte.
- Người thứ hai: `4 + 4 + 5 = 13` byte vì `Bình` cần 5 byte UTF-8: `B` (1), `ì` (2), `n` (1), `h` (1).
- Tổng: `10 + 10 + 13 = 33` byte.

`HexFormat` có từ Java 17; nó chỉ định dạng byte để hiển thị, không tham gia vào giao thức. `ByteArrayOutputStream` giữ kết quả trong bộ nhớ nên phù hợp demo nhỏ. Với file lớn, nên ghi streaming và áp dụng giới hạn tổng kích thước/thời gian xử lý.

### Vì sao decoder an toàn hơn cách đọc tùy ý?

1. Kiểm tra magic để nhận diện format.
2. Từ chối version chưa hỗ trợ thay vì đoán cấu trúc.
3. Kiểm tra count **trước** khi cấp phát danh sách hoặc lặp.
4. Kiểm tra độ dài tên **trước** khi cấp phát byte array.
5. `readFully` buộc mỗi tên có đúng số byte; EOF sớm trở thành lỗi.
6. `CharsetDecoder` với `CodingErrorAction.REPORT` báo dữ liệu UTF-8 malformed thay vì âm thầm thay thế ký tự không hợp lệ.
7. Kiểm tra trailing bytes áp dụng cho **một payload độc lập**. Nếu giao thức gửi liên tiếp nhiều bản tin trên một socket, đọc thêm byte để “kiểm tra EOF” sẽ vô tình đụng sang bản tin kế tiếp hoặc chặn chờ; thay vào đó phải giải mã đúng chiều dài frame đã xác định.

Đây vẫn chỉ là một format dạy học: trước khi triển khai thật, cần định nghĩa miền ID, tên rỗng/`null`, giới hạn **toàn bộ** payload, checksum hoặc xác thực khi cần, và hành vi khi có dữ liệu hỏng/trường mới.

## 6. Java Object Serialization hoạt động khác ra sao?

Java Object Serialization được thiết kế để ghi/đọc **đối tượng Java** và mối quan hệ giữa các đối tượng:

~~~java
ObjectOutputStream out = new ObjectOutputStream(outputStream);
out.writeObject(object);

ObjectInputStream in = new ObjectInputStream(inputStream);
Object restored = in.readObject();
~~~

Đây là **mảnh mã** với nguồn/đích byte và biến `object` đã có; cần khai báo ngoại lệ và đóng tài nguyên đúng quyền sở hữu.

Các ý quan trọng:

- Lớp muốn được serialize bằng cơ chế mặc định thường triển khai **`java.io.Serializable`**, một *marker interface* không khai báo phương thức công khai bắt buộc.
- `ObjectOutputStream` ghi thông tin theo **Java serialization stream protocol**, bao gồm metadata lớp và dữ liệu đối tượng; nó không chỉ ghi các trường theo thứ tự do bạn hình dung.
- Một đồ thị có thể chứa nhiều đối tượng, các tham chiếu chia sẻ và chu trình. Cơ chế **handles** giúp bảo toàn quan hệ tham chiếu thích hợp khi ghi/đọc trong cùng stream.
- `ObjectInputStream.readObject()` phục hồi đối tượng và có thể yêu cầu các định nghĩa lớp Java tương ứng; có thể ném `ClassNotFoundException`, `InvalidClassException`, `StreamCorruptedException`, `IOException` và các lỗi khác.
- Không phải bất kỳ object nào cũng serialize được. Nếu một trường cần được serialize giữ giá trị thuộc lớp không serializable, ghi có thể ném **`NotSerializableException`**.
- Trường **`static`** thuộc lớp, không phải trạng thái riêng của object để serialize mặc định. Trường **`transient`** bị loại khỏi serialization mặc định; giá trị sau đọc cần được khôi phục/khởi tạo theo logic ứng dụng nếu quan trọng.
- Có các điểm mở rộng như `writeObject/readObject`, `readResolve/writeReplace` và `Externalizable`. Chúng tăng tính linh hoạt nhưng cũng làm logic serialization phức tạp và tăng rủi ro khi đọc dữ liệu ngoài kiểm soát.

Bạn **không chủ động định nghĩa byte offset cho từng trường** chỉ bằng việc khai báo `implements Serializable`. Một chương trình khác không dùng Java serialization protocol không thể đơn giản đọc các byte này như một chuỗi `int32 + UTF-8`.

## 7. Ví dụ Java 17+: serialize đối tượng và giữ tham chiếu dùng chung

Tạo file **`JavaSerializationDemo.java`**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;

public class JavaSerializationDemo {
    static final class Snapshot implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final int id;
        private final String name;

        Snapshot(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return "Snapshot[id=" + id + ", name=" + name + "]";
        }
    }

    public static void main(String[] args)
            throws IOException, ClassNotFoundException {

        Snapshot person = new Snapshot(7, "An");
        ByteArrayOutputStream sink = new ByteArrayOutputStream();

        try (ObjectOutputStream out = new ObjectOutputStream(sink)) {
            out.writeObject(person);
            out.writeObject(person); // Cùng một instance lần thứ hai.
        }

        // Chỉ đọc dữ liệu được CHÍNH chương trình này tạo ra.
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(sink.toByteArray()))) {

            Snapshot first = (Snapshot) in.readObject();
            Snapshot second = (Snapshot) in.readObject();

            System.out.println("first=" + first);
            System.out.println("sameReference=" + (first == second));
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac JavaSerializationDemo.java
java JavaSerializationDemo
~~~

Stdout:

~~~text
first=Snapshot[id=7, name=An]
sameReference=true
~~~

Vì sao `sameReference=true`?

- `ObjectOutputStream` có cơ chế nhận diện một object đã được ghi trong **cùng stream**, dùng tham chiếu tới object đó thay vì luôn tạo nội dung object độc lập.
- Khi `ObjectInputStream` đọc tham chiếu, nó phục hồi đúng việc hai vị trí cùng trỏ đến một object trong đồ thị đã deserialize.
- Điều này khác với hai bản ghi riêng biệt trong format tự thiết kế: cùng dữ liệu `id/name` **không tự động** có nghĩa cùng một object identity sau decode.

Trong format tự thiết kế, nếu cần chia sẻ reference hoặc xử lý chu trình, thường phải gán ID đối tượng, biểu diễn quan hệ và giải quyết reference qua nhiều bước. Nếu chỉ lưu record độc lập, thường **không cần** ngữ nghĩa này.

**Cảnh báo sử dụng:** demo serialize và deserialize chính payload do chương trình vừa tạo. Không thay nguồn trong ví dụ bằng byte gửi từ Internet, file upload hoặc nội dung không tin cậy rồi gọi `readObject()` trực tiếp.

## 8. `serialVersionUID` giúp gì và không giúp gì?

Với lớp serializable thông thường, `serialVersionUID` là mã phiên bản liên quan đến cơ chế tương thích của lớp:

~~~java
@java.io.Serial
private static final long serialVersionUID = 1L;
~~~

Gán tường minh có thể tránh phụ thuộc vào UID tự tính của lớp; UID tự tính có thể bị ảnh hưởng bởi những thay đổi khai báo không chủ ý. Nhưng **giữ nguyên UID không tự biến mọi thay đổi thành tương thích**.

Ví dụ:

- Thêm/bỏ một số trường trong thay đổi tương thích có thể được cơ chế serialization mặc định xử lý theo quy tắc của Java; trường mới có thể nhận giá trị mặc định.
- Thay đổi bản chất lớp, kiểu trường hoặc quan hệ kế thừa có thể phá vỡ khả năng đọc hoặc ngữ nghĩa, dù UID vẫn được giữ.
- Khi UID của stream và lớp không tương thích, có thể gặp `InvalidClassException`.
- `serialVersionUID` **không phải số version của format byte do bạn tự đặc tả**, không phải checksum, chữ ký hay biện pháp xác thực nguồn.
- Lớp ứng dụng vẫn phải xử lý invariant, dữ liệu cũ và migration khi trường thay đổi.

Với format tự thiết kế, version được ghi là **một trường trong header** và decoder của bạn quyết định chính xác cách đọc mỗi version. Với Java serialization, tương thích còn phụ thuộc vào cấu trúc lớp và cơ chế của Java; không nên dùng nó làm lời hứa định dạng bền vững chỉ nhờ một UID.

## 9. Thiết kế tương thích và nâng cấp schema

### Định dạng tự thiết kế

Bạn kiểm soát chiến lược tiến hóa. Ví dụ:

- **V1**: `id:int32`, `name:utf8`.
- **V2**: thêm `age`, hoặc chuyển sang bản ghi dạng TLV (*type–length–value*) để parser có thể bỏ qua trường không nhận diện.
- Decoder kiểm tra header version, rẽ nhánh đọc đúng schema, điền giá trị mặc định có chủ đích khi đọc dữ liệu cũ.
- Nếu muốn decoder cũ đọc được bản mới, format phải thiết kế **forward compatibility** từ trước, chẳng hạn mỗi frame/field có độ dài và quy tắc bỏ qua phần chưa biết.

Không tự tiện nối thêm trường `age` vào sau mỗi record V1 trong một stream không có kích thước bản ghi rõ ràng: decoder V1 có thể hiểu byte của `age` thành phần đầu bản ghi tiếp theo. Thay đổi số trường mà không đổi version hoặc không có cơ chế skip là cách dễ làm file hỏng.

Một schema thực tế nên quy định: version nào được hỗ trợ, default cho trường thiếu, trường bắt buộc, nullable, danh sách có/không giữ thứ tự, độ dài tối đa, encoding, unknown field policy, và quy trình migrate.

### Java serialization

Bạn giữ được một số ngữ nghĩa object graph, nhưng việc thay đổi cấu trúc lớp ảnh hưởng khả năng đọc dữ liệu cũ. Ngoài `serialVersionUID`, hãy chú ý class/package name, kiểu trường, serializable fields, kế thừa và logic hook.

Nếu cần lưu tài liệu 5–10 năm, chia sẻ giữa nhiều phiên bản dịch vụ hoặc trao đổi với Go/Python/Rust, nên ưu tiên một **giao thức/scheme được phiên bản hóa rõ ràng**, có tài liệu độc lập và bộ test tương thích. Có thể dùng định dạng tự thiết kế khi đủ đơn giản, hoặc cân nhắc một format/schema chuẩn khi yêu cầu liên ngôn ngữ và evolution phức tạp.

## 10. Bảo mật: vì sao không deserialize Java object từ dữ liệu không tin cậy?

**`ObjectInputStream.readObject()` không phải chỉ là đọc các field vô hại.** Java deserialization có thể tải/khôi phục các loại đối tượng và kích hoạt các cơ chế của lớp trong quá trình dựng lại đồ thị (ví dụ callback/hook thích hợp). Khi ứng dụng cho phép dữ liệu ngoài kiểm soát chỉ định các object/class được phục hồi, các class có sẵn trên classpath và chuỗi hành vi liên quan có thể tạo ra rủi ro nghiêm trọng.

Đây là lý do tài liệu Oracle cảnh báo **deserialization of untrusted data is inherently dangerous and should be avoided**. Nguyên tắc chính:

1. **Không dùng Java native deserialization cho payload do bên không tin cậy cung cấp**, kể cả khi đã “kiểm tra vài byte đầu” hoặc dự đoán rằng chỉ có một kiểu object.
2. Nếu hệ thống legacy **bắt buộc** phải deserialize, dùng `ObjectInputFilter`/cơ chế lọc serialization theo Java version và ngữ cảnh; giới hạn class được phép, độ sâu, số reference, độ dài array và lượng byte đọc.
3. Áp dụng giới hạn tổng payload, authentication/integrity, cách ly và kiểm toán classpath/callback; filter chỉ là một lớp phòng vệ, **không thay thế** thiết kế giao thức an toàn.
4. Không xem `serialVersionUID`, `transient` hoặc cast sang một class dự kiến sau `readObject` là biện pháp ngăn payload độc hại. Tác dụng phụ có thể xảy ra **trước** khi cast hoàn tất.
5. Với endpoint nhận dữ liệu từ ngoài, ưu tiên parser của format xác định rõ field và ràng buộc; kiểm soát tất cả input length và dữ liệu nhận được trước khi cấp phát hoặc chạy logic nghiệp vụ.

`ObjectInputFilter` có từ Java 9 và hỗ trợ lọc lớp/array/đồ thị, nhưng cấu hình filter phải phù hợp **toàn bộ** đồ thị và policy bảo mật của ứng dụng. Đừng dùng bộ lọc quá rộng và coi hệ thống đã an toàn tuyệt đối.

### Format nhị phân tự thiết kế có tự động an toàn không?

**Không.** Parser tự viết vẫn có thể sai khi nhận length âm, số đếm quá lớn, phép cộng số nguyên tràn, frame bị cắt, UTF-8 không hợp lệ hoặc payload khiến cấp phát quá nhiều bộ nhớ.

Những kiểm tra tối thiểu với dữ liệu từ bên ngoài:

- Giới hạn tổng frame/payload ngay từ tầng nhận trước khi sao chép cả dữ liệu vào RAM.
- Kiểm tra magic, version và tất cả count/length **trước khi cấp phát** hoặc lặp.
- Dùng `readFully`/vòng tích lũy cho dữ liệu có length cố định, xử lý EOF sớm.
- Kiểm tra length nằm trong số byte còn lại khi decode một payload đóng kín.
- Từ chối UTF-8 malformed nếu contract yêu cầu; xác định policy cho giá trị bất hợp lệ/unknown.
- Quy định checksum để phát hiện hỏng dữ liệu vô ý khi cần; **checksum thường không chứng minh nguồn đáng tin cậy**. Muốn xác thực/chống sửa đổi có chủ đích cần cơ chế mật mã phù hợp.
- Giới hạn CPU, nesting, chu kỳ và bộ nhớ, không chỉ mỗi một trường string.
- Xem parser như ranh giới đầu vào: lỗi phải kết thúc có kiểm soát, không trở thành thao tác nghiệp vụ hoặc ghi đè file ngoài ý muốn.

Ưu điểm của format đơn giản là bạn **có thể giới hạn rõ những kiểu dữ liệu được tạo** (ví dụ int/string/record) mà không yêu cầu JVM dựng lại class tùy ý; độ an toàn vẫn phụ thuộc vào cách triển khai parser.

## 11. Lỗi thường gặp khi đọc/ghi dữ liệu nhị phân

### Lỗi 1: ghi và đọc không cùng thứ tự

~~~java
// Bên ghi:
out.writeInt(12);
out.writeLong(999L);

// Bên đọc SAI:
long x = in.readLong();
int y = in.readInt();
~~~

Hai bên phải thống nhất thứ tự trường. Đây là **mảnh mã** với `out` và `in` phù hợp; nếu đảo như trên, dữ liệu có thể bị giải nghĩa sai mà không có compile error.

### Lỗi 2: nhầm byte length với số ký tự

~~~java
String name = "Bình";
int wrongLength = name.length(); // 4 UTF-16 char, nhưng 5 byte UTF-8
byte[] utf8 = name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
int correctLength = utf8.length;  // 5
~~~

Nếu header ghi 4 mà payload thực là 5 byte, mọi trường kế tiếp có thể lệch vị trí.

### Lỗi 3: chỉ gọi `read(byte[])` một lần

Một lần `read` có thể trả về ít byte hơn yêu cầu. Khi schema ghi name length `N`, phải lặp cho đến khi đủ `N` byte, hoặc dùng `DataInputStream.readFully`. EOF giữa chừng phải là lỗi frame bị cắt, không chuyển byte chưa đọc thành ký tự rỗng.

### Lỗi 4: cấp phát trước khi kiểm tra length

~~~java
int length = in.readInt();
// SAI nếu length đến từ dữ liệu không tin cậy:
byte[] bytes = new byte[length];
~~~

`length` có thể âm hoặc rất lớn. Kiểm tra giới hạn domain, tổng payload và lượng bộ nhớ cho phép trước khi cấp phát.

### Lỗi 5: dùng `writeUTF` như UTF-8 phổ thông

`writeUTF` dùng modified UTF-8 có length 2 byte và giới hạn 65535 byte; đừng ghi nó rồi dùng decoder UTF-8 tiêu chuẩn của giao thức ngoài Java. Nếu chọn modified UTF-8, hãy ghi rõ trong đặc tả và dùng `readUTF` tương ứng.

### Lỗi 6: nghĩ `Serializable` nghĩa là “ổn định vĩnh viễn”

Class thay đổi, cấu trúc đồ thị và quy tắc compatible serialization có thể thay đổi khả năng đọc. `serialVersionUID` không tự xử lý migration hay đảm bảo giữ ngữ nghĩa nghiệp vụ.

### Lỗi 7: tưởng Java serialization giữ nguyên byte khi serialize cùng giá trị

Một stream có thể chứa class descriptors, references và trạng thái phụ thuộc trình tự ghi. Không thiết kế test bằng cách đoán size byte hoặc so sánh thô như một định dạng thủ công đã chuẩn hóa. Để kiểm thử Java serialization, kiểm tra round-trip object và các quan hệ reference/invariant cần giữ.

### Lỗi 8: đưa byte không tin cậy vào `readObject`

Không dùng `ObjectInputStream` như decoder chung của dữ liệu bên ngoài. Dùng parser/schema có ràng buộc và chính sách bảo mật; lọc serialization chỉ dành cho trường hợp legacy bắt buộc và là **một phần** của phòng vệ.

### Lỗi 9: quên phân biệt format và transport

TCP là **dòng byte**, không có ranh giới bản tin mặc định. Một lần `write` bên gửi không nhất thiết tương ứng một lần `read` bên nhận. Cần có độ dài frame/terminator và xử lý partial read, timeout. Hai chương trình cùng dùng `DataInputStream` không tự tạo ra message framing.

### Lỗi 10: tưởng magic/version/chính sách length bảo đảm tính toàn vẹn

Magic giúp nhận diện format, version giúp giải mã tương thích, length giúp biết ranh giới; chúng **không** là bằng chứng dữ liệu không bị sửa. Định dạng cần chống lỗi vô ý có thể dùng checksum; cần xác thực đối tác và nội dung thì chọn giao thức/mã xác thực phù hợp.

## 12. Khi nào chọn cách nào?

**Chọn format nhị phân có schema tự định nghĩa** khi cần:

- Đọc được từ Java lẫn ngôn ngữ khác, hoặc dữ liệu tồn tại độc lập với một class cụ thể.
- Format nhỏ, rõ byte order, dễ kiểm thử bằng vector hex/golden files.
- Định nghĩa chính xác version, field, giới hạn, framing và hành vi backward/forward compatibility.
- Dữ liệu từ file/network bên ngoài cần parser với tập kiểu dữ liệu hữu hạn.
- Thiết bị nhúng, dữ liệu nhị phân tối ưu, hoặc giao thức đã có đặc tả tương ứng.

**Cân nhắc Java serialization** khi:

- Cần giữ đồ thị object và tham chiếu dùng chung giữa các object Java.
- Hai đầu đều là môi trường Java **tin cậy**, kiểm soát classpath, lớp serializable và version.
- Vòng đời dữ liệu ngắn hoặc có chiến lược duy trì tương thích/migration rõ ràng.
- Chấp nhận overhead và coupling với mô hình lớp Java, và đã đánh giá rủi ro deserialize.

Đối với API công khai, dữ liệu lưu dài hạn, giao tiếp liên ngôn ngữ hoặc dữ liệu nhập từ bên ngoài, lựa chọn một schema độc lập với implementation Java thường phù hợp hơn. Nếu schema trở nên phức tạp, không nhất thiết phải tự viết parser: các công cụ/định dạng có đặc tả như Protocol Buffers, Avro, CBOR... có thể giúp quản lý giao thức (mỗi loại có giới hạn và model tương thích riêng).

## 13. Quy trình thiết kế một định dạng nhị phân đáng tin cậy

1. **Định nghĩa use case**: file độc lập, stream nhiều frame, network payload hay cache? Có cần tìm kiếm ngẫu nhiên và nén không?
2. **Viết byte specification**: magic, version, endianness, kiểu số (signed/unsigned), charset, length, nullable, thứ tự trường, byte dư và ranh giới frame.
3. **Chọn version policy**: khi nào tăng version, cách đọc version cũ, cách bỏ unknown field, có cần migration batch?
4. **Đặt giới hạn**: max frame, record, field, name byte length, nesting, tổng cấp phát và thời gian xử lý.
5. **Viết encoder/decoder độc lập theo schema**: không truyền trực tiếp object graph chưa kiểm soát sang bên nhận.
6. **Tạo golden vectors**: với một bản ghi nhỏ, chốt byte hex mong đợi; decoder phải đọc đúng, encoder phải tạo đúng. Test cả tiếng Việt/Unicode, zero length, giá trị biên.
7. **Test sai/hỏng**: magic sai, version lạ, count âm, length quá lớn, thiếu một byte, dữ liệu dư, UTF-8 malformed, overflow.
8. **Kiểm tra interoperability**: nếu có client khác ngôn ngữ, chạy round-trip chéo chứ không chỉ round-trip cùng một implementation.
9. **Xác định failure semantics**: file ghi một phần, checksum, atomic replace, timeout, retry và sự cố mất kết nối.
10. **Bảo trì spec như API công khai**: thay đổi có version, tài liệu, fixture và chính sách hỗ trợ dữ liệu cũ.

## 14. Câu hỏi tự kiểm tra

**1.** Hai dịch vụ cùng dùng Java. Nếu muốn đọc byte bằng Python trong tương lai, nên chọn gì?  
**Đáp án:** Một format có schema và encoding đặc tả độc lập Java (tự thiết kế hoặc chuẩn có sẵn), không giả định native Object Serialization phù hợp với Python.

**2.** `DataOutputStream.writeInt` dùng endianness nào?  
**Đáp án:** Big-endian (byte trọng số cao trước). Nếu format cần little-endian, bạn phải thiết kế/triển khai rõ, chẳng hạn dùng `ByteBuffer.order(ByteOrder.LITTLE_ENDIAN)` thích hợp.

**3.** Vì sao `writeUTF("Bình")` không đồng nghĩa `write(getBytes(UTF_8))`?  
**Đáp án:** `writeUTF` thêm độ dài 2 byte và dùng modified UTF-8, còn `getBytes(UTF_8)` tạo byte UTF-8 tiêu chuẩn không tự thêm length.

**4.** `ObjectOutputStream` ghi cùng instance hai lần vào cùng stream; object sau khi đọc có giữ quan hệ tham chiếu không?  
**Đáp án:** Thông thường có, nhờ cơ chế handles trong Java serialization; demo `sameReference=true` minh họa trường hợp đó.

**5.** Chỉ định `serialVersionUID = 1L` có đảm bảo đọc được mọi phiên bản lớp không?  
**Đáp án:** Không. Cần tuân thủ quy tắc tương thích của Java serialization và giữ invariant/migration theo thay đổi thực tế.

**6.** Sau khi đọc `nameLength` từ file không tin cậy, bước tiếp theo là gì?  
**Đáp án:** Kiểm tra phạm vi hợp lệ, giới hạn tối đa, số byte frame còn lại và ngân sách bộ nhớ; chỉ sau đó cấp phát và `readFully`.

**7.** Có nên dùng `new ObjectInputStream(socket.getInputStream()).readObject()` trên dữ liệu client không kiểm soát?  
**Đáp án:** Không. Java deserialization từ dữ liệu không tin cậy có rủi ro cao; dùng schema/parser được kiểm soát và các biện pháp xác thực/giới hạn cần thiết.

**8.** Định dạng tự thiết kế có cần version nếu hôm nay chỉ có một loại bản ghi?  
**Đáp án:** Nếu dữ liệu có thể được lưu lâu dài hoặc trao đổi giữa các phiên bản, nên có chính sách version ngay từ đầu để có thể phát hiện và xử lý thay đổi tương lai.

**Ghi nhớ:** `DataInputStream`/`DataOutputStream` là **công cụ để hiện thực format byte do bạn kiểm soát**. `ObjectInputStream`/`ObjectOutputStream` là **cơ chế Java để ghi/đọc đồ thị đối tượng**. Chọn theo hợp đồng dữ liệu, tuổi thọ, tương thích, quyền sở hữu schema và mức độ tin cậy của nguồn; đừng chọn chỉ vì API nào có ít dòng code hơn.

## Tham khảo API Java 17

- [DataOutput — cách mã hóa kiểu nguyên thủy và modified UTF-8](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataOutput.html)
- [DataInputStream — cách đọc dữ liệu nhị phân](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataInputStream.html)
- [ObjectInputStream — Java serialization và cảnh báo dữ liệu không tin cậy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputStream.html)
- [ObjectInputFilter — lọc lớp và giới hạn đồ thị khi deserialization](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputFilter.html)

</details>

- [Quay lại đầu trang](#back-to-top)
