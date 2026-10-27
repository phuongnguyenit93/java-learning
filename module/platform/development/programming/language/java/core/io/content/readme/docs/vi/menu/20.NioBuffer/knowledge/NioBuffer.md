<a id="back-to-top"></a>

# NIO Buffer

## Menu
- [Buffer, ByteBuffer, CharBuffer và các kiểu buffer khác](#nio-buffer-001)
- [Capacity, position, limit và mark](#nio-buffer-002)
- [Chu trình ghi vào buffer, flip và đọc khỏi buffer](#nio-buffer-003)
- [clear không xóa dữ liệu trong vùng nhớ](#nio-buffer-004)
- [rewind, compact và reset](#nio-buffer-005)
- [get, put tương đối và tuyệt đối](#nio-buffer-006)
- [remaining, hasRemaining và lỗi tràn hoặc thiếu buffer](#nio-buffer-007)
- [allocate, wrap và vùng dữ liệu được chia sẻ](#nio-buffer-008)
- [Heap buffer, direct buffer và chi phí cấp phát](#nio-buffer-009)
- [ByteOrder và các view buffer cho kiểu nguyên thủy](#nio-buffer-010)
- [slice, duplicate, read-only buffer và trạng thái vị trí riêng](#nio-buffer-011)
- [Buffer không mặc nhiên an toàn cho nhiều luồng](#nio-buffer-012)

## <a id="nio-buffer-001">Buffer, ByteBuffer, CharBuffer và các kiểu buffer khác</a>

<details>
<summary>Xem chi tiết</summary>

# Buffer, ByteBuffer, CharBuffer và các kiểu buffer khác trong Java NIO

## 1. Buffer giải quyết vấn đề gì?

Trong Java NIO (New I/O), **buffer** là một vùng chứa có dung lượng xác định, dùng để ghi dữ liệu vào rồi đọc dữ liệu ra. Buffer đồng thời lưu trạng thái cho biết **đã sử dụng đến đâu** và **còn bao nhiêu phần tử có thể đọc hoặc ghi**. Đây là cầu nối quan trọng giữa chương trình và những API như `FileChannel`, `SocketChannel` hoặc bộ mã hóa ký tự.

Ví dụ: một channel đọc được 5 byte từ tệp và đưa vào `ByteBuffer` dung lượng 8 byte. Chương trình cần phân biệt 5 byte hợp lệ vừa nhận với 3 vị trí chưa được điền bằng lần đọc đó. Buffer làm được điều này qua `position` và `limit`; chỉ dựa vào kích thước mảng byte sẽ không đủ.

Các lớp liên quan thuộc gói `java.nio`:

- `Buffer` là lớp cơ sở trừu tượng: định nghĩa trạng thái và các thao tác điều khiển con trỏ.
- `ByteBuffer` chứa đơn vị `byte`; loại thường được sử dụng khi giao tiếp với channel và mã hóa nhị phân.
- `CharBuffer` chứa đơn vị `char` (mỗi `char` là một *UTF-16 code unit*).
- `ShortBuffer`, `IntBuffer`, `LongBuffer`, `FloatBuffer`, `DoubleBuffer` chứa từng kiểu nguyên thủy tương ứng.
- `MappedByteBuffer` là một dạng `ByteBuffer` đại diện vùng tệp được ánh xạ vào bộ nhớ.

Không có lớp `BooleanBuffer` chuẩn trong Java NIO. Các buffer này có cách quản lý `position`, `limit`, `capacity` tương tự nhưng mỗi lớp cung cấp `get`/`put` theo kiểu phần tử riêng.

## 2. Mô hình trạng thái: capacity, limit, position và mark

Bốn giá trị cần nắm vững:

| Thuộc tính | Ý nghĩa | Bất biến |
| --- | --- | --- |
| `capacity()` | Sức chứa tối đa, **đếm theo phần tử của buffer**. | Dung lượng cố định trong suốt vòng đời buffer đó. |
| `limit()` | Ranh giới *exclusive*: thao tác tương đối không đi tới hoặc vượt quá vị trí này. | `0 <= limit <= capacity`. |
| `position()` | Chỉ số phần tử kế tiếp để `get()` hoặc `put(...)` tương đối sử dụng. | `0 <= position <= limit`. |
| `mark()` / `reset()` | Ghi nhớ vị trí để quay lại sau; có thể chưa được thiết lập. | Nếu có mark, `0 <= mark <= position`. |

Hình minh họa một buffer có `capacity=8`, `limit=5`, `position=2`:

~~~text
index:     0   1   2   3   4   5   6   7
         +---+---+---+---+---+---+---+---+
buffer:  | A | B | C | D | E | ? | ? | ? |
         +---+---+---+---+---+---+---+---+
                 ^           ^           ^
             position=2    limit=5    capacity=8

remaining() = limit - position = 3
hasRemaining() = true
~~~

Các dấu hỏi chỉ phần tử nằm ngoài **vùng đang được xem là hợp lệ để đọc**, không nhất thiết là byte đã bị xóa hoặc chưa từng được ghi.

Một buffer tạo bằng `ByteBuffer.allocate(8)` bắt đầu với `position=0`, `limit=8`, `capacity=8`. Nếu gọi `put` 5 lần, `position=5` trong khi `limit` vẫn bằng 8. Sau `flip()`, `position=0` và `limit=5`: ta sẵn sàng đọc đúng 5 phần tử vừa ghi.

Công thức quan trọng:

~~~text
0 <= position <= limit <= capacity
remaining = limit - position
~~~

`remaining()` đếm **số phần tử**, không phải số byte trong mọi trường hợp. Với `IntBuffer`, `remaining() == 3` có nghĩa còn 3 giá trị `int`; thông thường tương đương 12 byte dữ liệu lưu trữ.

## 3. Quy trình ghi rồi đọc: put → flip → get → clear/compact

### 3.1. Các thao tác điều khiển trạng thái

| Lệnh | Thay đổi trạng thái | Dùng khi |
| --- | --- | --- |
| `flip()` | `limit = position`; `position = 0`; xóa mark. | Chuyển từ **ghi vào buffer** sang **đọc dữ liệu đã ghi**. |
| `clear()` | `position = 0`; `limit = capacity`; xóa mark. | Sẵn sàng ghi lượt mới, không cần giữ dữ liệu cũ. |
| `rewind()` | `position = 0`; giữ nguyên limit; xóa mark. | Muốn đọc lại vùng dữ liệu hiện tại. |
| `compact()` | Dời những phần tử từ `position` đến `limit-1` về đầu vùng chứa; `position = số phần tử giữ lại`, `limit = capacity`; xóa mark. | Đã đọc một phần và muốn **giữ dữ liệu chưa đọc** rồi tiếp tục ghi. |
| `mark()` | Lưu position hiện tại vào mark. | Muốn đánh dấu vị trí có thể quay lại. |
| `reset()` | Đặt position về mark, nếu mark hợp lệ. | Đọc/ghi lại từ điểm đã đánh dấu. |
| `position(n)` / `limit(n)` | Thiết lập ranh giới trực tiếp, có thể vô hiệu hóa mark. | Cần điều khiển vùng dữ liệu chính xác. |

**`clear()` không xóa hay đặt tất cả byte thành 0.** Nó chỉ đặt lại `position` và `limit` để vùng nhớ có thể được ghi đè. Cũng không nên đọc phần còn sót lại sau `clear` và coi đó là dữ liệu mới.

### 3.2. Ví dụ từng bước

~~~java
ByteBuffer b = ByteBuffer.allocate(6);
b.put((byte) 'A');
b.put((byte) 'B');
b.put((byte) 'C');
// position=3, limit=6, capacity=6

b.flip();
// position=0, limit=3; chỉ A, B, C là vùng đang đọc

byte first = b.get(); // A; position=1
byte next = b.get();  // B; position=2

b.compact();
// Phần chưa đọc là C -> dời C tới index 0.
// position=1, limit=6: có thể ghi tiếp sau C.

b.put((byte) 'D');
b.flip();
// position=0, limit=2: vùng đọc là C, D.
~~~

Đây là **đoạn minh họa** giả sử đã import `java.nio.ByteBuffer` và đặt trong phương thức. `compact()` đặc biệt hữu ích với luồng dữ liệu đến theo nhiều đợt, chẳng hạn thông điệp mạng chưa nhận đủ.

### 3.3. Vì sao `flip()` thường không thể bỏ qua?

Một `Channel.read(buffer)` hoặc loạt lệnh `put` thường **tăng position** khi ghi dữ liệu vào buffer. Nếu lập tức gọi `get()` mà không `flip()`, chương trình sẽ đọc ở vị trí sau cùng đã ghi, thay vì từ đầu vùng dữ liệu vừa nhận. Kết quả có thể sai hoặc gặp `BufferUnderflowException` tùy trạng thái.

Ngược lại, sau khi đọc hết mà muốn ghi đè từ đầu, cần `clear()`. Nếu còn phần tử chưa xử lý thì dùng `compact()`. Chọn sai hai lệnh này thường khiến ứng dụng làm mất một phần thông điệp.

## 4. ByteBuffer: làm việc với byte và dữ liệu nhị phân

`ByteBuffer` là buffer trung tâm trong nhiều ứng dụng NIO vì channel đọc và ghi những chuỗi byte.

### 4.1. Tạo ByteBuffer

~~~java
ByteBuffer heap = ByteBuffer.allocate(1024);
ByteBuffer direct = ByteBuffer.allocateDirect(1024);

byte[] source = {10, 20, 30};
ByteBuffer wrapped = ByteBuffer.wrap(source);
~~~

Phân biệt:

- `allocate(1024)` cấp một **heap byte buffer**; các phần tử thường nằm trong một mảng byte trên heap. `isDirect()` là `false`.
- `allocateDirect(1024)` tạo một **direct byte buffer**; JVM cố gắng dành vùng nhớ thích hợp cho các hoạt động native I/O. `isDirect()` là `true`. Chi phí cấp phát và quản lý có thể cao hơn; không mặc định luôn nhanh hơn.
- `wrap(source)` tạo buffer dựa trên **mảng hiện có** và chia sẻ nội dung với mảng đó. Sửa phần tử qua buffer có thể làm thay đổi mảng gốc và ngược lại.

`ByteBuffer.wrap(array, offset, length)` tạo buffer có `capacity=array.length`, `position=offset`, `limit=offset+length`. Nó **không** tự cắt mảng thành một buffer có position bằng 0. Muốn một vùng nhìn mới bắt đầu tại 0, dùng `slice()` sau khi thiết lập `position`/`limit` phù hợp.

### 4.2. Ghi và đọc: thao tác tương đối và tuyệt đối

~~~java
ByteBuffer b = ByteBuffer.allocate(4);

b.put((byte) 10);             // Ghi tại position rồi tăng position.
b.put((byte) 20);
byte second = b.get(1);       // Đọc tuyệt đối index 1; position KHÔNG đổi.
int p = b.position();         // p == 2

b.flip();
byte first = b.get();         // Đọc tương đối index 0, sau đó tăng position.
~~~

Các overload phổ biến:

| API | Ý nghĩa |
| --- | --- |
| `get()` / `put(byte)` | Đọc/ghi một byte tại position và dịch position. |
| `get(index)` / `put(index, value)` | Đọc/ghi tại chỉ số tuyệt đối; không đổi position. |
| `get(byte[])` / `put(byte[])` | Chuyển nhiều byte, cập nhật position theo số phần tử chuyển. |
| `getInt()` / `putInt(int)` | Đọc/ghi 4 byte thành một `int`, theo `ByteOrder` của buffer. |
| `getLong()` / `putLong(long)` | Đọc/ghi 8 byte thành một `long`, theo `ByteOrder`. |
| `getChar()` / `putChar(char)` | Đọc/ghi một `char` như hai byte theo byte order; **không phải** chuyển mã UTF-8. |

Với absolute `get(index)`/`put(index, ...)`, chỉ số được kiểm tra theo `limit`, không thể tùy ý truy cập phía sau limit dù capacity còn lớn. Khi muốn kiểm tra số phần tử sẵn có, dùng `remaining()` và `hasRemaining()`.

### 4.3. Byte order (endianness)

Dữ liệu đa byte cần quy ước byte đứng trước:

- **Big-endian**: byte có trọng số cao được ghi trước.
- **Little-endian**: byte có trọng số thấp được ghi trước.

~~~java
ByteBuffer b = ByteBuffer.allocate(4);
b.order(ByteOrder.LITTLE_ENDIAN);
b.putInt(0x01020304);
// Bốn byte trong buffer: 04 03 02 01
~~~

Mặc định `ByteBuffer` dùng `ByteOrder.BIG_ENDIAN`; khi đọc/ghi giao thức nhị phân, phải chọn đúng quy ước của giao thức. Sai byte order có thể khiến số nguyên đọc ra sai dù các byte đã nhận đủ.

`order(ByteOrder)` **không** chuyển đổi lại dữ liệu đã có; nó quyết định cách các thao tác đọc/ghi đa byte tiếp theo diễn giải byte. Những buffer view kiểu số được tạo từ `ByteBuffer` ghi nhận byte order thích hợp tại thời điểm tạo; thay đổi byte order về sau cần được thiết kế có chủ đích.

## 5. CharBuffer: vùng chứa ký tự, khác ByteBuffer

`CharBuffer` chứa các phần tử kiểu Java `char`, mỗi phần tử là một đơn vị mã UTF-16. Nó phù hợp khi đã có dữ liệu ký tự hoặc khi decoder biến byte thành ký tự.

~~~java
CharBuffer a = CharBuffer.allocate(10);
a.put('J').put('a').put('v').put('a');
a.flip();
System.out.println(a.toString()); // Java

CharBuffer b = CharBuffer.wrap("Hello");
System.out.println(b.get());      // H
~~~

Đây là các **mảnh minh họa** khi đã import `java.nio.CharBuffer`. Phân biệt hai dạng `wrap`:

- `CharBuffer.wrap(char[])` chia sẻ mảng `char[]` và thường có thể ghi qua buffer nếu mảng/buffer đó không bị biến thành read-only.
- `CharBuffer.wrap(CharSequence)`, như `wrap("Hello")`, tạo buffer **read-only** dựa trên chuỗi ký tự; `put` vào buffer này sẽ ném `ReadOnlyBufferException`.

`CharBuffer` triển khai `CharSequence` và các phương thức hữu ích như `length()`, `charAt(...)`, `subSequence(...)`, `toString()`. `toString()` phản ánh **các ký tự còn lại từ position đến limit**, không mặc nhiên là toàn bộ capacity.

Không đồng nhất một `char` với một ký tự Unicode hoàn chỉnh: ký tự nằm ngoài Basic Multilingual Plane (ví dụ một số emoji) cần **cặp surrogate**, tức hai phần tử `char`. Muốn đếm *code point*, phải xử lý theo quy tắc Unicode, ví dụ thông qua API `String.codePointCount(...)` trên chuỗi đã thu được.

### 5.1. Chuyển giữa ByteBuffer và CharBuffer bằng Charset

**Byte ≠ char.** Để chuyển văn bản sang byte, cần biết charset như UTF-8. Không dùng `getChar()`/`putChar()` của `ByteBuffer` để thay thế UTF-8 encoder/decoder.

~~~java
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;

ByteBuffer bytes = StandardCharsets.UTF_8.encode("Tiếng Việt");
CharBuffer chars = StandardCharsets.UTF_8.decode(bytes);
System.out.println(chars.toString()); // Tiếng Việt
~~~

Đây là mảnh minh họa đặt trong `main`. Các helper `Charset.encode`/`decode` trả buffer với position/limit ở trạng thái có thể đọc đầu ra. `decode(bytes)` **tiêu thụ vị trí đọc của bytes**; nếu cần đọc lại byte sau đó, hãy `rewind`/`duplicate` có chủ đích.

Trong ứng dụng xử lý nhiều đợt byte, một ký tự UTF-8 có thể bị chia giữa hai lượt đọc. Khi ấy dùng `CharsetDecoder` theo cơ chế streaming, xử lý kết quả `CoderResult` và giữ byte còn dang dở; không tùy tiện decode từng mảnh độc lập vì có thể sinh ký tự thay thế hoặc lỗi giải mã.

## 6. Các buffer kiểu nguyên thủy khác

| Buffer | Kiểu phần tử | Dung lượng `allocate(n)` | Tình huống sử dụng |
| --- | --- | --- | --- |
| `ByteBuffer` | `byte` (8 bit) | `n` byte | Tệp, socket, protocol, binary payload. |
| `CharBuffer` | `char` (16 bit) | `n` code unit UTF-16 | Ký tự sau khi decode, xử lý văn bản. |
| `ShortBuffer` | `short` (16 bit) | `n` giá trị `short` | Giá trị nhị phân 16 bit. |
| `IntBuffer` | `int` (32 bit) | `n` giá trị `int` | Dữ liệu số nguyên, binary view. |
| `LongBuffer` | `long` (64 bit) | `n` giá trị `long` | Số nguyên 64 bit. |
| `FloatBuffer` | `float` (32 bit) | `n` giá trị `float` | Dữ liệu số thực 32 bit. |
| `DoubleBuffer` | `double` (64 bit) | `n` giá trị `double` | Dữ liệu số thực 64 bit. |

Chẳng hạn:

~~~java
IntBuffer numbers = IntBuffer.allocate(3);
numbers.put(10).put(20).put(30);
numbers.flip();

while (numbers.hasRemaining()) {
    System.out.println(numbers.get());
}
~~~

Output:

~~~text
10
20
30
~~~

Đoạn mã cần `import java.nio.IntBuffer;` và đặt trong phương thức Java. Vòng lặp đọc **theo phần tử `int`**, không phải từng byte.

Nhiều typed buffer cũng hỗ trợ `wrap(array)`, `slice()`, `duplicate()`, `asReadOnlyBuffer()`, `get`/`put` và các thao tác tương ứng để xem dữ liệu; cách cấp phát vùng nhớ và khả năng hỗ trợ mảng phụ thuộc kiểu buffer và nguồn tạo ra nó.

## 7. View buffer, slice, duplicate và read-only

Đây là nhóm API dễ gây lỗi do nhiều buffer **chia sẻ cùng một vùng dữ liệu**, dù trạng thái `position` và `limit` độc lập.

| Cách tạo | Dữ liệu | Position/limit | Đặc điểm |
| --- | --- | --- | --- |
| `duplicate()` | Chia sẻ vùng nhớ với buffer gốc. | Sao chép trạng thái ban đầu, sau đó di chuyển độc lập. | Sửa byte qua một buffer nhìn thấy ở buffer kia. |
| `slice()` | Chia sẻ đoạn từ position đến limit của buffer gốc. | View mới bắt đầu position=0 và capacity=remaining tại lúc cắt. | Index 0 của slice tương ứng vị trí gốc tại thời điểm slice. |
| `asReadOnlyBuffer()` | Chia sẻ dữ liệu với gốc. | Trạng thái vị trí riêng. | Ghi qua view bị cấm, dù gốc có thể tiếp tục ghi. |
| `asIntBuffer()` và các primitive views | Chia sẻ byte làm dữ liệu kiểu nguyên thủy. | Position/limit tính theo **số phần tử của kiểu view**. | Diễn giải byte theo byte order của nguồn tại thời điểm tạo view. |

Ví dụ về `slice()`:

~~~java
ByteBuffer original = ByteBuffer.wrap(new byte[] {10, 20, 30, 40});
original.position(1);
original.limit(3);

ByteBuffer view = original.slice(); // View chứa 20, 30.
view.put(0, (byte) 99);             // Thay byte đầu của slice.

System.out.println(original.get(1)); // 99
System.out.println(original.position()); // 1
~~~

**Tại sao position của gốc vẫn là 1?** Vì `view.put(0, ...)` là truy cập tuyệt đối trong slice; hai buffer chia sẻ dữ liệu nhưng không chia sẻ con trỏ. Trường hợp `duplicate()` cũng không tự đồng bộ con trỏ.

### 7.1. Primitive view từ ByteBuffer

~~~java
ByteBuffer bytes = ByteBuffer.allocate(8);
bytes.order(ByteOrder.BIG_ENDIAN);
IntBuffer ints = bytes.asIntBuffer();

ints.put(0, 42);
ints.put(1, 7);

System.out.println(bytes.getInt(0)); // 42
System.out.println(bytes.getInt(4)); // 7
~~~

Đây là mảnh minh họa với import `java.nio.ByteBuffer`, `java.nio.ByteOrder` và `java.nio.IntBuffer`. `asIntBuffer()` tạo view trên các byte còn lại, dung lượng đo theo các nhóm 4 byte. Phép `ints.put(index, value)` làm thay đổi vùng nhớ chung; `bytes.getInt(...)` đọc lại cùng byte.

Nếu số byte còn lại không chia hết cho kích thước kiểu phần tử, view chỉ chứa số phần tử **nguyên vẹn** có thể biểu diễn. Byte order và alignment có thể ảnh hưởng đến tính đúng đắn hoặc hiệu năng trong những thao tác cấp thấp.

## 8. Heap buffer và direct buffer

`ByteBuffer.allocate(...)` và `ByteBuffer.allocateDirect(...)` đều cung cấp API đọc/ghi tương tự, nhưng khác về cách quản lý vùng nhớ.

| Tiêu chí | Heap ByteBuffer | Direct ByteBuffer |
| --- | --- | --- |
| Tạo | `ByteBuffer.allocate(n)` | `ByteBuffer.allocateDirect(n)` |
| `isDirect()` | `false` | `true` |
| Mảng backing | Thường có thể truy cập qua `array()` nếu writable và có mảng. | Không được giả định có mảng Java truy cập được. |
| Cấp phát/giải phóng | Chủ yếu theo cơ chế bộ nhớ Java heap. | Có chi phí quản lý native memory và phụ thuộc JVM/GC. |
| I/O native | Có thể cần thao tác sao chép trung gian. | Có thể giảm một số lần sao chép với channel I/O phù hợp. |
| Chọn dùng | Buffer nhỏ, sống ngắn, thao tác dữ liệu thường. | Buffer lớn/tái sử dụng, I/O có đo đạc chứng minh lợi ích. |

**Không gọi `array()` mà chưa kiểm tra khả năng.** `hasArray()` trả về `false` đối với direct buffer và read-only buffer; `array()` có thể ném `UnsupportedOperationException` hoặc `ReadOnlyBufferException` tùy loại buffer. Ngay cả khi có mảng, cần lưu ý `arrayOffset()` và vị trí của view/slice.

Khi cần đọc bất kỳ `ByteBuffer` nào thành mảng độc lập, có thể làm như sau:

~~~java
ByteBuffer copy = source.duplicate();
byte[] bytes = new byte[copy.remaining()];
copy.get(bytes);
~~~

Đây là mảnh minh họa với `source` là `ByteBuffer` có sẵn. Lệnh lấy **remaining bytes** của source tại thời điểm đó. Dùng duplicate giúp giữ nguyên position của source và làm việc với cả heap, direct lẫn read-only buffer.

Không cấp phát direct buffer cho mọi thao tác với niềm tin nó chắc chắn nhanh hơn. Độ lớn dữ liệu, số lần tái sử dụng, provider, thao tác mã hóa và đường đi thực tế của I/O mới quyết định hiệu quả; nếu quan trọng về hiệu năng, hãy benchmark đúng workload.

## 9. MappedByteBuffer: ánh xạ vùng tệp

`MappedByteBuffer` kế thừa `ByteBuffer`. Thông qua `FileChannel.map(...)`, ứng dụng có thể ánh xạ một vùng tệp vào bộ nhớ và đọc/ghi qua API buffer.

~~~java
try (FileChannel channel = FileChannel.open(
        path, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
    MappedByteBuffer mapped = channel.map(
            FileChannel.MapMode.READ_WRITE, 0, channel.size());

    if (mapped.hasRemaining()) {
        byte first = mapped.get(0);
        mapped.put(0, (byte) (first + 1));
        mapped.force();
    }
}
~~~

Đây là **mảnh minh họa**; cần các import `java.nio.MappedByteBuffer`, `java.nio.channels.FileChannel`, `java.nio.file.Path` và `java.nio.file.StandardOpenOption`, và phải có tệp `path` hợp lệ. `force()` yêu cầu chuyển thay đổi của mapping xuống thiết bị lưu trữ theo hợp đồng API; việc ánh xạ, độ bền dữ liệu và sự tương tác với sửa tệp ngoài chương trình còn tùy nền tảng và chế độ map.

Việc đóng `FileChannel` **không nhất thiết hủy ngay vùng nhớ đã ánh xạ**. Vòng đời mapping có thể kéo dài đến khi buffer được thu hồi. Không dùng mmap như phương án mặc định; nó có giới hạn về quản lý tài nguyên, khóa tệp hoặc phối hợp đọc/ghi đa tiến trình.

## 10. Buffer làm việc với Channel như thế nào?

Khi đọc từ một channel vào `ByteBuffer`:

~~~java
ByteBuffer buffer = ByteBuffer.allocate(4096);
int n = channel.read(buffer);  // Channel ghi byte vào buffer.
buffer.flip();                 // Chuyển sang đọc phần vừa nhận.

while (buffer.hasRemaining()) {
    byte value = buffer.get();
    // Xử lý value...
}

buffer.clear();                // Sẵn sàng nhận lượt tiếp theo.
~~~

Đây là **mảnh minh họa** giả định `channel` hỗ trợ `read(ByteBuffer)` và đã được khởi tạo. Lệnh `read` có thể trả:

- Một số dương: số byte thực tế nhận được, có thể nhỏ hơn dung lượng còn trống.
- `0`: không có byte được chuyển trong lần gọi đó (chẳng hạn với non-blocking channel chưa sẵn sàng).
- `-1`: đã đến cuối luồng (EOF) đối với những channel hỗ trợ hợp đồng này.

Không giả định **một** lần `read` chắc chắn điền đủ buffer hoặc một lần `write` chắc chắn xuất hết `remaining()`; với channel cho phép partial I/O, cần vòng lặp và quy tắc chờ phù hợp.

### 10.1. Khi còn dữ liệu chưa xử lý: dùng compact()

Giả sử ứng dụng nhận gói dữ liệu mà một thông điệp bị chia giữa hai lần đọc. Sau khi xử lý phần thông điệp hoàn chỉnh trong buffer, hãy giữ các byte còn dang dở:

~~~java
buffer.flip();           // Đọc dữ liệu vừa nhận.
consumeCompleteFrames(buffer); // Chỉ tăng position qua các frame đầy đủ.
buffer.compact();        // Giữ phần byte chưa thuộc frame hoàn chỉnh.
~~~

Đây là **pseudocode minh họa**; `consumeCompleteFrames` là hàm ứng dụng cần tự định nghĩa, không phải API Java. Ví dụ giúp giải thích lý do `compact()` có giá trị: `clear()` sẽ đặt lại con trỏ và cho phép dữ liệu chưa xử lý bị ghi đè.

Khi viết buffer ra channel, buffer thường đã ở trạng thái đọc sau `flip()`:

~~~java
while (buffer.hasRemaining()) {
    int written = channel.write(buffer);
    if (written == 0) {
        // Với channel non-blocking, đăng ký/chờ tín hiệu ghi sẵn sàng.
        break;
    }
}
~~~

Đây là đoạn minh họa điều khiển luồng; nếu dừng ở `written == 0`, ứng dụng phải **giữ nguyên phần còn lại** để tiếp tục khi channel sẵn sàng. Với channel blocking, giao thức chờ có thể khác. Không dùng vòng lặp busy-spin vô hạn trên channel non-blocking.

## 11. Chương trình chạy được trên Java 17+: nhiều kiểu buffer và compact()

Lưu nội dung sau vào tệp `BufferDemo.java`. Chương trình dùng các thao tác xác định, không phụ thuộc hệ điều hành hay file bên ngoài.

~~~java
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public class BufferDemo {
    public static void main(String[] args) {
        ByteBuffer bytes = ByteBuffer.allocate(8);
        bytes.order(ByteOrder.BIG_ENDIAN);
        bytes.putInt(0x01020304);
        bytes.put((byte) 'A');
        bytes.flip();

        System.out.println("capacity=" + bytes.capacity());
        System.out.println("limit=" + bytes.limit());
        System.out.println("hex=" + Integer.toHexString(bytes.getInt()));
        System.out.println("letter=" + (char) bytes.get());
        System.out.println("hasRemaining=" + bytes.hasRemaining());

        bytes.clear();
        bytes.put((byte) 'B');
        bytes.flip();
        System.out.println("reuse=" + (char) bytes.get());

        CharBuffer chars = CharBuffer.wrap("Java");
        System.out.println("charRemaining=" + chars.remaining());
        System.out.println("firstChar=" + chars.get());

        IntBuffer numbers = IntBuffer.allocate(3);
        numbers.put(10).put(20).put(30);
        numbers.flip();
        System.out.println("sum=" + (numbers.get() + numbers.get() + numbers.get()));

        ByteBuffer partial = ByteBuffer.allocate(8);
        partial.put("abcde".getBytes(StandardCharsets.US_ASCII));
        partial.flip();
        partial.get(); // Tiêu thụ a.
        partial.get(); // Tiêu thụ b.
        partial.compact(); // Giữ cde.
        partial.put("FG".getBytes(StandardCharsets.US_ASCII));
        partial.flip();

        byte[] remaining = new byte[partial.remaining()];
        partial.get(remaining);
        System.out.println("compact=" +
                new String(remaining, StandardCharsets.US_ASCII));

        ByteBuffer little = ByteBuffer.allocate(4);
        little.order(ByteOrder.LITTLE_ENDIAN);
        little.putInt(0x01020304);
        System.out.println("littleEndian=" +
                HexFormat.of().formatHex(little.array()));

        ByteBuffer encoded = StandardCharsets.UTF_8.encode("Tiếng Việt");
        CharBuffer decoded = StandardCharsets.UTF_8.decode(encoded);
        System.out.println("decoded=" + decoded);
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac BufferDemo.java
java BufferDemo
~~~

Stdout:

~~~text
capacity=8
limit=5
hex=1020304
letter=A
hasRemaining=false
reuse=B
charRemaining=4
firstChar=J
sum=60
compact=cdeFG
littleEndian=04030201
decoded=Tiếng Việt
~~~

Giải thích những dòng chính:

1. Sau `putInt` (4 byte) và `put('A')` (1 byte), `position=5`. `flip` đặt `limit=5` để đọc đúng 5 byte, trong khi `capacity` vẫn là 8.
2. `getInt()` đọc bốn byte big-endian và trả giá trị `0x01020304`; `Integer.toHexString` in `1020304` (không thêm số 0 đứng đầu). Lệnh `get()` tiếp theo đọc ký tự `A`, nên không còn phần tử nào giữa position và limit.
3. `clear` cho phép ghi lại vào bộ nhớ cũ, nhưng không phải xóa nội dung. `put('B')`/`flip` tạo lượt dữ liệu mới có đúng một byte.
4. `CharBuffer.wrap("Java")` có bốn `char` còn lại; đọc một phần tử đầu tiên cho kết quả `J`.
5. `IntBuffer` chứa ba số nguyên, nên tổng là 60.
6. Với `partial`, hai byte `a` và `b` đã đọc; `compact` giữ `cde`, sau đó thêm `FG` để vùng đọc cuối cùng là `cdeFG`.
7. Byte order little-endian biểu diễn `0x01020304` bằng chuỗi byte hex `04 03 02 01`.
8. UTF-8 encoding/decoding giữ nguyên chuỗi `Tiếng Việt`. Quá trình này chuyển **byte** thành **char**, không phụ thuộc byte order của `ByteBuffer`.

## 12. Lỗi thường gặp và cách chẩn đoán

| Vấn đề | Nguyên nhân điển hình | Cách xử lý |
| --- | --- | --- |
| `BufferUnderflowException` | Gọi `get` khi `remaining()` không đủ số phần tử cần đọc. | Kiểm tra trạng thái và số byte cần thiết trước khi đọc. |
| `BufferOverflowException` | Gọi `put` vượt `limit` hoặc không còn chỗ. | Kiểm tra `remaining()`, phân bổ đủ dung lượng, `clear`/`compact` khi đúng quy trình. |
| `InvalidMarkException` | Gọi `reset()` khi mark chưa được thiết lập hoặc đã bị xóa. | Gọi `mark()` tại vị trí phù hợp và hiểu tác động của `flip/clear/rewind`. |
| `ReadOnlyBufferException` | Ghi vào read-only buffer/view. | Dùng writable buffer khác hoặc tạo bản sao dữ liệu. |
| `IndexOutOfBoundsException` | Truy cập absolute index không hợp lệ so với limit hoặc vùng chỉ định. | Dùng position/limit đúng, kiểm tra trước khi đọc/ghi. |
| `UnsupportedOperationException` khi `array()` | Buffer không có backing array truy cập được. | Dùng `hasArray()` hoặc `duplicate().get(byte[])`. |
| Dữ liệu đọc bị thiếu | Quên `flip()`, hoặc một lần channel read trả ít byte hơn kỳ vọng. | Quản lý trạng thái chuẩn và xử lý partial reads. |
| Mất phần dữ liệu chưa xử lý | Gọi `clear()` thay cho `compact()` sau khi chỉ tiêu thụ một phần. | Giữ `remaining` bằng compact khi cần. |
| Số nhị phân sai | Sai `ByteOrder` hoặc đọc nhầm kiểu (ví dụ `getShort` thay vì `getInt`). | Đối chiếu đặc tả dữ liệu/giao thức. |
| Ký tự lỗi | Dùng `getChar()` thay cho decoder hoặc cắt một ký tự UTF-8 giữa hai chunk. | Chọn đúng `Charset` và xử lý luồng nhiều đợt bằng decoder. |

### Các nguyên tắc sử dụng an toàn

- **Buffer không mặc định thread-safe.** Nếu nhiều luồng cùng đọc/ghi một buffer, phải có cơ chế đồng bộ hoặc thiết kế ownership rõ ràng. `duplicate()` tách con trỏ nhưng vẫn chia sẻ dữ liệu, không tự giải quyết race condition.
- **Không giữ trạng thái position/limit bằng phỏng đoán.** Ghi lại quy trình vào/ra, dùng `remaining()`, kiểm tra read/write count và vị trí sau mỗi bước khi chẩn đoán.
- **Đừng dùng `array()` trên mọi buffer.** Direct, read-only và view buffer có thể không lộ mảng; nếu lộ mảng, `arrayOffset()` mới cho biết phép ánh xạ chỉ số thích hợp.
- **Không dùng `clear()` để xóa bí mật khỏi bộ nhớ.** Nó thay đổi trạng thái logic; muốn xóa dữ liệu cần quy trình ghi đè riêng phù hợp và lưu ý các bản sao dữ liệu đã tồn tại.
- **Không coi số phần tử là số byte cho mọi buffer.** `IntBuffer.remaining()` đếm `int`; khi tính kích thước dữ liệu nhị phân cần nhân với số byte/phần tử một cách rõ ràng.
- **Các phương thức so sánh có thể dựa trên phần tử còn lại.** `equals` và `compareTo` của nhiều typed buffer so sánh vùng từ position đến limit; cùng vùng lưu trữ nhưng vị trí khác nhau có thể không bằng nhau.
- **Chọn kích thước buffer theo tải thực tế.** Buffer quá nhỏ có thể làm tăng số lần I/O; buffer quá lớn tăng chi phí bộ nhớ. Direct buffer cần thêm lưu ý vòng đời/tái sử dụng.

## 13. Câu hỏi tự kiểm tra

1. **`ByteBuffer.allocate(10)` có position, limit, capacity ban đầu là bao nhiêu?** Tương ứng `0, 10, 10`.
2. **Sau khi ghi 4 byte vào buffer dung lượng 10 và gọi `flip()`?** `position=0`, `limit=4`, `capacity=10`; có đúng 4 byte trong vùng sẵn sàng đọc.
3. **Đã đọc một phần và muốn giữ byte còn lại để nhận thêm dữ liệu, nên gọi gì?** `compact()`, rồi ghi tiếp và `flip()` trước lượt đọc tiếp.
4. **`clear()` có ghi số 0 vào toàn bộ byte cũ không?** Không. Nó chỉ đặt position về 0 và limit về capacity.
5. **`ByteBuffer.wrap(array)` có copy nội dung array không?** Không. Nội dung được chia sẻ với mảng gốc.
6. **`duplicate()` và `slice()` có độc lập dữ liệu với gốc không?** Không. Chúng chia sẻ vùng lưu trữ; chỉ trạng thái position/limit độc lập.
7. **`CharBuffer` có lưu sẵn UTF-8 bytes không?** Không. Nó lưu `char`, tức các UTF-16 code unit. Muốn chuyển dữ liệu byte và ký tự, dùng `Charset`/encoder/decoder.
8. **`ByteBuffer.putInt(0x01020304)` dưới little-endian tạo những byte nào?** `04 03 02 01`.
9. **`ByteBuffer.allocateDirect(100)` có bảo đảm `array()` sử dụng được?** Không. Truy xuất byte qua `get/put` hoặc sao chép `remaining` ra mảng.
10. **Vì sao một lần `FileChannel.read` chưa đủ để kết luận đã nhận đầy đủ thông điệp?** Vì một lần đọc có thể chuyển ít byte hơn lượng cần thiết; chương trình phải quản lý buffer và tiếp tục đọc theo giao thức.

**Ghi nhớ:** `Buffer` định nghĩa **trạng thái vùng dữ liệu**; `ByteBuffer` là công cụ làm việc với **byte và channel I/O**; `CharBuffer` và các typed buffer diễn giải **phần tử theo kiểu cụ thể**. Quy tắc thực hành quan trọng nhất là **ghi → flip → đọc → clear hoặc compact**, đồng thời kiểm soát `position`, `limit`, `capacity`, byte order và việc chia sẻ vùng nhớ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-buffer-002">Capacity, position, limit và mark</a>

<details>
<summary>Xem chi tiết</summary>

# Capacity, position, limit và mark trong Java NIO Buffer

## 1. Vì sao Buffer cần bốn thông số trạng thái?

Trong Java NIO, một **buffer** là vùng lưu trữ dữ liệu có kích thước xác định, đồng thời duy trì trạng thái để đọc và ghi tuần tự. Những trạng thái cơ bản được khai báo trong `java.nio.Buffer` và xuất hiện ở các lớp cụ thể như `ByteBuffer`, `CharBuffer`, `IntBuffer`:

- **capacity**: sức chứa tối đa của buffer, cố định trong suốt vòng đời buffer đó.
- **position**: chỉ số phần tử kế tiếp sẽ được thao tác khi **đọc/ghi tương đối** (*relative*).
- **limit**: ranh giới trên, độc quyền, của các phần tử hiện được phép thao tác.
- **mark**: vị trí được đánh dấu tùy chọn để có thể quay lại bằng `reset()`.

Điểm khó của Buffer là **cùng một vùng nhớ được sử dụng trong nhiều giai đoạn**. Trong giai đoạn ghi, `position` thường biểu thị nơi ghi phần tử kế tiếp. Trong giai đoạn đọc, `position` thường biểu thị nơi đọc phần tử kế tiếp. Vì vậy, muốn đọc dữ liệu vừa ghi, chương trình phải thiết lập lại `position` và `limit` thích hợp, thường bằng `flip()`.

Ví dụ với `ByteBuffer.allocate(8)`: có chỗ chứa 8 byte, chỉ số hợp lệ của vùng lưu trữ là 0–7. Tuy nhiên, `limit` có thể nhỏ hơn 8, và lúc đó không phải toàn bộ 8 vị trí đều thuộc vùng được phép đọc/ghi theo trạng thái hiện tại.

## 2. Bất biến cốt lõi của Buffer

Tại mọi thời điểm, một Buffer hợp lệ phải thỏa mãn:

\[
0 \leq position \leq limit \leq capacity
\]

Nếu `mark` đã được đặt, nó phải thỏa mãn:

\[
0 \leq mark \leq position
\]

`mark` **có thể không tồn tại**; lúc này không được coi nó là một số mặc định như 0. Trạng thái “không có mark” khiến `reset()` ném `InvalidMarkException`.

Hãy xét một buffer có `capacity = 8`, `position = 3`, `limit = 6`:

```text
Chỉ số:       0   1   2   3   4   5   6   7
Vùng nhớ:    [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ]
                          ^
                       position = 3
                                       ^
                                    limit = 6
                                               ^
                                            capacity = 8

Vùng có thể thao tác tương đối từ position: [3, 6)
Số phần tử còn lại: limit - position = 3
```

Trong hình, `limit = 6` là **ranh giới**, không phải chỉ số phần tử được phép thao tác. Khi `position == limit`, đã hết phần tử cho thao tác tương đối.

| Trạng thái | Lấy bằng | Ý nghĩa |
| --- | --- | --- |
| Capacity | `buffer.capacity()` | Tổng số phần tử buffer chứa được |
| Position | `buffer.position()` | Chỉ số cho lần thao tác tương đối tiếp theo |
| Limit | `buffer.limit()` | Ranh giới trên, không bao gồm vị trí này |
| Mark | `buffer.mark()` / `buffer.reset()` | Lưu rồi khôi phục position; không có getter `mark()` trả về một số |
| Remaining | `buffer.remaining()` | `limit() - position()` |
| Has remaining | `buffer.hasRemaining()` | `position() < limit()` |

**Đơn vị là phần tử của loại Buffer**, không nhất thiết là byte. `ByteBuffer` có các vị trí theo byte; `CharBuffer` theo `char`; `IntBuffer` theo `int`. Khi tạo view buffer, cần hiểu đơn vị vị trí và quan hệ với vùng lưu trữ nền.

## 3. Trạng thái khởi tạo: `allocate`, `wrap`

Ví dụ:

```java
ByteBuffer buffer = ByteBuffer.allocate(8);
```

Ngay sau khi tạo:

```text
capacity = 8
position = 0
limit    = 8
mark     = undefined
remaining = 8
```

Đây là trạng thái **sẵn sàng ghi tối đa 8 byte** bằng các thao tác tương đối như `put(byte)`. Các byte mới ghi làm `position` tăng dần.

Với `ByteBuffer.wrap(byte[])`, buffer được tạo dựa trên một mảng có sẵn. Thông thường `position = 0`, `limit = capacity = array.length`. **Dữ liệu đã có trong mảng không có nghĩa phải gọi `flip()` trước khi đọc**: `wrap` đã thiết lập trạng thái cho phép bắt đầu đọc từ phần tử đầu tiên.

Ví dụ:

```java
ByteBuffer wrapped = ByteBuffer.wrap(new byte[] {10, 20, 30});
System.out.println(wrapped.get()); // 10
```

Lưu ý: phiên bản `wrap(array, offset, length)` có trạng thái khởi tạo khác: `position = offset`, `limit = offset + length`, `capacity = array.length`. `offset` không làm chỉ số buffer trở thành chỉ số bắt đầu từ 0 của một đoạn cắt mới.

## 4. `position`: vị trí kế tiếp và chuyển động khi đọc/ghi

Các thao tác **tương đối** (*relative*) dùng vị trí hiện tại:

```java
ByteBuffer b = ByteBuffer.allocate(5);
b.put((byte) 11);         // ghi tại index 0, position thành 1
b.put((byte) 22);         // ghi tại index 1, position thành 2
b.put((byte) 33);         // ghi tại index 2, position thành 3
```

Ngược lại, thao tác **tuyệt đối** (*absolute*) chỉ định index cụ thể, không di chuyển `position`:

```java
b.put(0, (byte) 99);      // thay byte index 0, position vẫn là 3
byte first = b.get(0);    // đọc byte index 0, position vẫn là 3
```

Hai đoạn mã trên minh họa chung một biến `b` và không phải chương trình đầy đủ. Khi áp dụng thao tác tuyệt đối, index phải hợp lệ theo `limit` hiện tại; không tự cho phép truy cập qua `limit` chỉ vì `capacity` lớn hơn.

**`position(int newPosition)`** có thể tự đặt lại con trỏ mà không đọc/ghi dữ liệu. `newPosition` phải từ 0 đến `limit`; nếu vượt phạm vi, ném `IllegalArgumentException`. Nếu thay position xuống dưới mark hiện tại, mark bị xóa.

Không nên dùng các lệnh thay đổi position để giả định byte trong vùng lưu trữ được thay đổi. Chúng **chỉ đổi trạng thái**.

## 5. `limit`: ranh giới thao tác

`limit()` trả về chỉ số ranh giới trên **không bao gồm**, tức các chỉ số hợp lệ để đọc/ghi tuyệt đối nằm trong `[0, limit)`. `limit(int newLimit)` có thể giảm hoặc tăng ranh giới, với điều kiện `0 <= newLimit <= capacity`.

Nếu đặt `newLimit` nhỏ hơn `position` hiện tại, position bị kéo xuống bằng limit mới để vẫn giữ bất biến. Nếu mark vượt quá limit mới, mark bị xóa.

Ví dụ:

```java
ByteBuffer b = ByteBuffer.allocate(8);
b.position(5);
b.limit(3);
System.out.println(b.position()); // 3
System.out.println(b.limit());    // 3
```

Sau hai lệnh, không còn phần tử cho thao tác tương đối: `remaining() == 0`.

**Khi `position == limit`:**

- `hasRemaining()` trả về `false`.
- `remaining()` trả về 0.
- `get()` tương đối sẽ ném `BufferUnderflowException`.
- `put(byte)` tương đối trên buffer có thể ghi sẽ ném `BufferOverflowException`.
- Một buffer chỉ đọc sẽ ném `ReadOnlyBufferException` khi gọi thao tác ghi.

Phân biệt `capacity` và `limit` giúp tránh lỗi xử lý cả vùng lưu trữ chưa chứa dữ liệu mới hoặc ghi vượt ranh giới của giai đoạn hiện tại.

## 6. `mark` và `reset`: ghi nhớ một vị trí, không sao lưu dữ liệu

`mark()` lưu **position hiện tại** làm mark. `reset()` đặt lại position bằng mark đã lưu; nó **không đổi nội dung**, không tự đặt lại limit và không xóa mark sau khi reset thành công.

Ví dụ tuần tự:

```java
ByteBuffer b = ByteBuffer.wrap(new byte[] {10, 20, 30, 40});

System.out.println(b.get());   // 10; position = 1
b.mark();                       // mark = 1
System.out.println(b.get());   // 20; position = 2
System.out.println(b.get());   // 30; position = 3
b.reset();                      // position quay về 1
System.out.println(b.get());   // 20 lần nữa; position = 2
```

Ở đây dữ liệu `20` không được sao chép; chương trình chỉ đọc lại cùng vị trí.

`mark` sẽ không còn hợp lệ khi:

- Gọi `clear()`, `flip()` hoặc `rewind()`.
- Gọi `compact()` trên các buffer có hỗ trợ phương thức này.
- Đặt `position` xuống **thấp hơn mark**.
- Đặt `limit` xuống **thấp hơn mark**.

Gọi `reset()` khi chưa có mark hoặc mark đã bị hủy sẽ ném `java.nio.InvalidMarkException`. Đây là ngoại lệ unchecked, không phải `IOException`.

**Mark không phải lịch sử nhiều cấp**: buffer giữ nhiều nhất một mark. Gọi `mark()` lần nữa sẽ thay mark trước đó bằng vị trí hiện tại.

## 7. Bốn thao tác đổi pha: `flip`, `clear`, `rewind`, `compact`

Cần hiểu mục đích từng thao tác để chuyển giữa ghi và đọc trên **cùng một buffer**.

| Thao tác | Position sau thao tác | Limit sau thao tác | Mark | Nội dung dữ liệu |
| --- | --- | --- | --- | --- |
| `flip()` | 0 | position cũ | Bỏ | Không tự sửa byte |
| `clear()` | 0 | capacity | Bỏ | **Không xóa byte** |
| `rewind()` | 0 | Giữ nguyên limit | Bỏ | Không tự sửa byte |
| `compact()` | Số phần tử còn lại trước compact | capacity | Bỏ | **Sao chép** phần chưa đọc về đầu |

### 7.1. `flip()`: từ ghi sang đọc

Trong giai đoạn ghi, position đi sau dữ liệu đã ghi. `flip()` thực hiện chính xác:

```text
limit = oldPosition
position = 0
mark = undefined
```

Ví dụ `capacity = 8`, ghi 3 byte:

```text
Sau put(10), put(20), put(30):
[10][20][30][  ][  ][  ][  ][  ]
             ^
         position = 3
                             limit = 8

Sau flip():
[10][20][30][  ][  ][  ][  ][  ]
 ^
 position = 0
             ^
         limit = 3
```

Bây giờ chỉ ba byte đã ghi thuộc phần dữ liệu đọc qua thao tác tương đối. **`flip` không đảo thứ tự byte** và không “biến” byte thành dạng khác; nó chỉ thay đổi ranh giới.

Nếu gọi `flip()` một lần nữa ngay sau đó, limit trở thành 0 và buffer trông như không có dữ liệu để đọc. Không gọi `flip` lặp lại nếu chưa có bước ghi mới cần chuyển pha.

### 7.2. `clear()`: quay về trạng thái sẵn sàng ghi từ đầu

`clear()` thực hiện:

```text
position = 0
limit = capacity
mark = undefined
```

**`clear()` không xóa vùng lưu trữ.** Byte cũ vẫn có thể nằm trong backing array/direct memory cho đến khi bị ghi đè, nhưng theo logic buffer dữ liệu đó đã được bỏ qua để bắt đầu chu kỳ ghi mới. Đừng dùng `clear()` để xóa thông tin nhạy cảm.

Khi dữ liệu cũ **đã đọc hết và không cần giữ**, dùng `clear()` để tái sử dụng vùng ghi.

### 7.3. `rewind()`: đọc lại từ đầu trong limit hiện tại

`rewind()` đặt position về 0, **giữ limit như cũ**, đồng thời bỏ mark. Nó phù hợp khi cần đọc lại cùng đoạn dữ liệu đã được giới hạn.

Ví dụ sau `flip()` có `limit = 3`, đọc hết ba byte rồi `rewind()`: `position` trở về 0 và `limit` vẫn 3; có thể đọc lại ba byte. Nó khác `clear()` vì `clear` nâng limit lên toàn bộ capacity.

### 7.4. `compact()`: giữ phần chưa đọc khi còn cần ghi thêm

Giả sử buffer đang ở **read mode**:

```text
capacity = 8, position = 2, limit = 5

[10][20][30][40][50][ ?][ ?][ ?]
         ^
      position = 2
                     ^
                  limit = 5
```

Ba byte **chưa đọc** nằm tại các offset 2, 3, 4. `compact()` sao chép chúng về đầu:

```text
Sau compact():
[30][40][50][ ?][ ?][ ?][ ?][ ?]
             ^
          position = 3
                             ^
                          limit = 8
```

Sau đó tiếp tục `put` các byte mới, rồi `flip()` để đọc cả phần còn lại lẫn dữ liệu mới.

Công thức của `compact()`:

\[
newPosition = oldLimit - oldPosition
\]

`newLimit = capacity` và mark bị xóa. **Không được dựa vào giá trị các ô khác ngoài phần được chuyển về đầu**; những vị trí ấy sẽ được xem là vùng để ghi tiếp. `compact()` không có cùng tác dụng với `clear()` vì nó **bảo toàn các phần tử chưa đọc**.

## 8. Ví dụ Java 17+ chạy độc lập: quan sát toàn bộ trạng thái

Chương trình sau minh họa cả bốn trạng thái và kiểm tra `mark`, `remaining`, `flip`, `rewind`, `clear`.

**Tệp `BufferStateDemo.java`:**

```java
import java.nio.ByteBuffer;
import java.nio.InvalidMarkException;

public class BufferStateDemo {
    private static void show(String label, ByteBuffer b) {
        System.out.printf(
            "%s: cap=%d, pos=%d, lim=%d, remaining=%d%n",
            label, b.capacity(), b.position(), b.limit(), b.remaining());
    }

    public static void main(String[] args) {
        ByteBuffer b = ByteBuffer.allocate(8);
        show("Moi tao", b);

        b.put((byte) 10);
        b.put((byte) 20);
        b.put((byte) 30);
        b.put((byte) 40);
        show("Sau 4 put", b);

        b.flip();
        show("Sau flip", b);

        System.out.println("Doc dau: " + b.get());
        b.mark();
        System.out.println("Doc tiep: " + b.get());
        System.out.println("Doc nua: " + b.get());
        show("Truoc reset", b);

        b.reset();
        show("Sau reset", b);
        System.out.println("Doc lai: " + b.get());

        b.rewind();
        show("Sau rewind", b);

        try {
            b.reset();
            System.out.println("Mark van ton tai");
        } catch (InvalidMarkException ex) {
            System.out.println("Mark da bi huy");
        }

        b.clear();
        show("Sau clear", b);
        System.out.println("Byte cu tai 0: " + b.get(0));
        show("Sau get tuyet doi", b);
    }
}
```

Biên dịch, chạy:

```bash
javac BufferStateDemo.java
java BufferStateDemo
```

**Stdout chính xác:**

```text
Moi tao: cap=8, pos=0, lim=8, remaining=8
Sau 4 put: cap=8, pos=4, lim=8, remaining=4
Sau flip: cap=8, pos=0, lim=4, remaining=4
Doc dau: 10
Doc tiep: 20
Doc nua: 30
Truoc reset: cap=8, pos=3, lim=4, remaining=1
Sau reset: cap=8, pos=1, lim=4, remaining=3
Doc lai: 20
Sau rewind: cap=8, pos=0, lim=4, remaining=4
Mark da bi huy
Sau clear: cap=8, pos=0, lim=8, remaining=8
Byte cu tai 0: 10
Sau get tuyet doi: cap=8, pos=0, lim=8, remaining=8
```

**Phân tích quan trọng:**

1. Sau bốn `put`, position là 4 dù capacity là 8.
2. `flip` biến position cũ 4 thành limit mới 4, đưa position về 0.
3. `mark()` ở position 1; sau hai lần đọc đến position 3, `reset()` quay về 1, rồi đọc lại `20`.
4. `rewind()` đưa position về 0, giữ limit 4 nhưng **xóa mark**; `reset()` sau đó ném `InvalidMarkException`.
5. `clear()` đưa limit lên 8 nhưng **không xóa byte cũ**: `get(0)` vẫn đọc được 10 trong ví dụ. `get(0)` là phép đọc tuyệt đối nên position giữ ở 0. Điều này không có nghĩa dữ liệu cũ còn hợp lệ về mặt nghiệp vụ sau khi clear.

## 9. Ví dụ Java 17+: `compact` và dữ liệu đọc dở

Một sai sót phổ biến khi xử lý dữ liệu từ kênh I/O là nhận được một phần thông điệp, chỉ đọc một số byte rồi cần giữ phần chưa đọc cho lượt nhận tiếp theo. Nếu gọi `clear` ngay, chương trình có thể làm mất phần chưa xử lý khi nó bị ghi đè. `compact` giải quyết trường hợp này.

**Tệp `BufferCompactDemo.java`:**

```java
import java.nio.ByteBuffer;

public class BufferCompactDemo {
    public static void main(String[] args) {
        ByteBuffer b = ByteBuffer.allocate(6);

        b.put(new byte[] {10, 20, 30, 40, 50});
        System.out.println("Sau ghi: pos=" + b.position()
                + ", lim=" + b.limit());

        b.flip();
        System.out.println("Da xu ly: " + b.get() + ", " + b.get());
        System.out.println("Chua xu ly: " + b.remaining());

        b.compact();
        System.out.println("Sau compact: pos=" + b.position()
                + ", lim=" + b.limit());

        b.put((byte) 60);
        b.put((byte) 70);
        b.flip();

        System.out.print("Du lieu con lai:");
        while (b.hasRemaining()) {
            System.out.print(" " + b.get());
        }
        System.out.println();
    }
}
```

Chạy:

```bash
javac BufferCompactDemo.java
java BufferCompactDemo
```

**Stdout chính xác:**

```text
Sau ghi: pos=5, lim=6
Da xu ly: 10, 20
Chua xu ly: 3
Sau compact: pos=3, lim=6
Du lieu con lai: 30 40 50 60 70
```

`compact()` giữ ba byte `30 40 50` và chuyển chúng xuống đầu buffer. Position trở thành 3, có chỗ cho ba byte mới. Ghi thêm `60`, `70` đưa position lên 5; `flip()` thiết lập phần đọc là đúng năm byte `30 40 50 60 70`.

Lưu ý `compact()` trên buffer chỉ đọc sẽ ném `ReadOnlyBufferException`. Nếu phần dữ liệu chưa đọc gần bằng capacity, lần ghi mới vẫn có thể thiếu chỗ; chương trình cần xử lý tăng kích thước, giới hạn bản ghi hoặc chính sách nhận dữ liệu phù hợp.

## 10. Mark bị hủy chính xác khi nào?

Xét `ByteBuffer.allocate(10)`, ban đầu `position=0`, `limit=10`. Lần lượt:

```java
ByteBuffer b = ByteBuffer.allocate(10);
b.position(4);
b.mark();       // mark = 4

b.position(6);  // mark 4 vẫn hợp lệ
b.reset();      // trở về position 4

b.position(3);  // position < mark 4: mark bị xóa
// b.reset();    // sẽ ném InvalidMarkException
```

Ở một chuỗi thao tác khác:

```java
ByteBuffer b = ByteBuffer.allocate(10);
b.position(6).mark(); // mark 6
b.limit(5);           // position bị kéo xuống 5; mark 6 bị xóa
// b.reset();         // sẽ ném InvalidMarkException
```

Hai ví dụ này độc lập. Không nên hiểu `mark` là “điểm khôi phục chắc chắn mãi mãi”; nó chỉ tồn tại trong phạm vi trạng thái cho phép của buffer.

Khi cần nhiều điểm phục hồi, hoặc cần khôi phục sau những thao tác làm mất mark, ứng dụng có thể **lưu các giá trị position vào biến kiểu int** rồi gọi lại `position(savedPosition)` sau khi kiểm tra `savedPosition <= limit()`. Cách này không đồng nghĩa với `mark/reset` và vẫn phải bảo đảm dữ liệu/limit còn thích hợp.

## 11. Buffer đầy, Buffer rỗng và những ngoại lệ cần phân biệt

**Buffer đầy theo giai đoạn ghi** nghĩa là `position == limit` và không còn chỗ `put` tương đối. **Buffer đã đọc hết** cũng có `position == limit`, nhưng ý nghĩa nghiệp vụ khác. Trạng thái số học giống nhau; chu kỳ xử lý quyết định bước tiếp theo.

| Tình huống | Kết quả thường gặp |
| --- | --- |
| `get()` khi `position == limit` | `BufferUnderflowException` |
| `put(byte)` khi `position == limit` trên buffer có thể ghi | `BufferOverflowException` |
| `get(-1)` hoặc `get(index >= limit)` | `IndexOutOfBoundsException` |
| `position(-1)` hoặc `position(limit + 1)` | `IllegalArgumentException` |
| `limit(capacity + 1)` | `IllegalArgumentException` |
| `reset()` khi không có mark | `InvalidMarkException` |
| Ghi vào buffer chỉ đọc | `ReadOnlyBufferException` |

`remaining()` và `hasRemaining()` giúp tránh nhiều trường hợp đọc/ghi quá limit:

```java
while (buffer.hasRemaining()) {
    byte value = buffer.get();
    // Xử lý value.
}
```

Đây là đoạn minh họa; để biên dịch độc lập phải có biến `buffer` kiểu phù hợp và định nghĩa việc xử lý. Không được suy ra rằng một vòng lặp như vậy tự hiểu ranh giới thông điệp hay giao thức. Nó chỉ đọc đến `limit` hiện tại.

Trong đọc/ghi với `FileChannel`, lượng byte xử lý có thể nhỏ hơn `remaining()` mong muốn trong một lần gọi; nên viết vòng lặp đúng theo hợp đồng API kênh, xử lý EOF và trường hợp tiến độ bằng 0 khi áp dụng. `flip`, `compact`, `clear` chỉ quản lý **trạng thái buffer**; chúng không tự đọc dữ liệu từ hệ thống tệp hoặc gửi byte ra mạng.

## 12. Ứng dụng vào chu trình NIO đọc rồi xử lý dữ liệu

Một chu trình thông dụng khi đọc từ nguồn byte vào `ByteBuffer`:

```text
Buffer ban đầu:        position=0, limit=capacity
       |
       v
Nguồn ghi vào Buffer:  position tăng theo byte nhận được
       |
       v
flip():                limit=oldPosition, position=0
       |
       v
Xử lý dữ liệu:         đọc các byte, position tăng
       |
       +-- Đã xử lý hết mọi byte:
       |        clear() --> chuẩn bị ghi lượt mới
       |
       +-- Còn byte chưa xử lý:
                compact() --> giữ phần dư, chuẩn bị ghi tiếp
```

Chọn `clear` hay `compact` phụ thuộc việc **còn cần giữ các byte từ position đến limit hay không**. Ví dụ khi nhận từng mảnh một thông điệp có độ dài biến đổi, parser có thể chưa đủ dữ liệu để giải mã một record; `compact` giữ phần chưa đủ để nhận tiếp.

Đối với text UTF-8, ký tự có thể bị chia giữa hai lần nhận. Không nên ép mỗi chunk bytes thành `String` độc lập rồi mất dữ liệu ký tự dở; phải dùng decoder xử lý trạng thái hoặc cơ chế gom byte theo giao thức. Đây là ứng dụng trực tiếp của `position`, `limit` và `compact`.

Khi **ghi dữ liệu từ ứng dụng vào kênh**, quy trình thường đi ngược lại: `put` để chuẩn bị dữ liệu → `flip` để kênh lấy byte → lặp `write` đến khi buffer không còn remaining → `clear` hoặc `compact` phù hợp với tình trạng ghi dở. Điều quan trọng là theo dõi rõ buffer đang ở pha ghi vào buffer hay pha đọc ra khỏi buffer.

## 13. `slice`, `duplicate`, `asReadOnlyBuffer` và trạng thái độc lập

Một `ByteBuffer` có thể tạo view:

- `duplicate()` tạo buffer chia sẻ cùng nội dung, nhưng giữ **position, limit và mark độc lập** với buffer gốc; trạng thái ban đầu sao chép giá trị hiện có.
- `slice()` tạo view của vùng từ position hiện tại đến limit hiện tại. View mới có `position=0`, `limit=capacity=remaining` của buffer gốc tại thời điểm tạo và chia sẻ dữ liệu vùng ấy.
- `asReadOnlyBuffer()` tạo view chỉ đọc, chia sẻ nội dung với trạng thái position/limit/mark riêng. Mọi thao tác ghi qua view này bị từ chối.

**Chia sẻ nội dung không có nghĩa chia sẻ position.** Dịch position ở view không trực tiếp dịch position ở buffer gốc; tuy nhiên ghi dữ liệu qua một view có thể thay đổi byte mà view khác nhìn thấy. Các view cũng không tự bảo đảm an toàn luồng.

Ví dụ nhỏ:

```java
ByteBuffer base = ByteBuffer.wrap(new byte[] {10, 20, 30, 40});
base.position(1);
base.limit(3);

ByteBuffer view = base.slice();
System.out.println(view.capacity()); // 2
System.out.println(view.position()); // 0
System.out.println(view.limit());    // 2
System.out.println(view.get());      // 20
System.out.println(base.position()); // 1 (không đổi)
```

View có hai byte tương ứng `20, 30`, có chỉ số riêng `0,1`. Với buffer được cấp phát direct hoặc memory-mapped, chuyện sở hữu tài nguyên và vòng đời backing storage cũng cần được thiết kế phù hợp; các trục này độc lập với các bất biến capacity/position/limit/mark.

## 14. Lỗi phổ biến và cách sửa

| Lỗi | Tại sao sai | Cách sửa |
| --- | --- | --- |
| Ghi bằng `put` rồi đọc ngay bằng `get` mà quên `flip()` | Position đang nằm sau dữ liệu vừa ghi | `flip()` trước pha đọc |
| Tưởng `flip()` đảo thứ tự phần tử | Flip chỉ thay trạng thái | Theo dõi limit/position đúng công thức |
| Dùng `clear()` để xóa bí mật | Clear không ghi đè nội dung | Có phương án xóa/ghi đè dữ liệu phù hợp và quản lý vòng đời bộ nhớ |
| Dùng `clear()` khi còn bytes chưa xử lý | Phần dư dễ bị ghi đè | `compact()` khi cần giữ remaining |
| Dùng `rewind()` để bắt đầu pha ghi đầy capacity | Rewind giữ limit cũ | Nếu bỏ dữ liệu cũ và muốn ghi lại toàn bộ, dùng `clear()` |
| Gọi `reset()` sau `flip/clear/rewind/compact` | Mark đã bị loại bỏ | Đặt `mark()` lại ở pha thích hợp |
| Nhầm capacity với số byte đã ghi | Có thể đọc cả vùng chưa hợp lệ | Dùng `flip` để đặt limit theo lượng đã ghi |
| Gọi `get()` khi `remaining() == 0` | Underflow | Kiểm tra `hasRemaining()`, hoặc xử lý ngoại lệ có chủ đích |
| Gọi `put()` khi `position == limit` | Overflow | Kiểm tra không gian, chuyển pha hoặc dùng buffer lớn hơn |
| Nghĩ `get(index)` làm position tăng | Đây là truy cập tuyệt đối | Dùng `get()` nếu muốn tăng position |
| Nhầm vị trí trong `IntBuffer` là byte offset | Vị trí của IntBuffer theo phần tử int | Nhớ mỗi loại buffer có đơn vị phần tử riêng |
| Dùng chung buffer thay đổi position giữa nhiều luồng | Trạng thái có thể bị xen kẽ | Đồng bộ hoặc phân chia quyền sở hữu buffer rõ ràng |

## 15. Bài tập kiểm tra trạng thái

Với buffer mới:

```java
ByteBuffer b = ByteBuffer.allocate(6);
b.put((byte) 1);
b.put((byte) 2);
b.put((byte) 3);
b.flip();
b.get();
b.mark();
b.get();
b.compact();
```

**Hãy dự đoán position, limit, capacity sau mỗi bước quan trọng.**

Đáp án:

1. Mới tạo: `capacity=6`, `position=0`, `limit=6`.
2. Sau ba `put`: `position=3`, `limit=6`, dữ liệu đã ghi `1,2,3`.
3. Sau `flip`: `position=0`, `limit=3`.
4. Sau `get()` thứ nhất: `position=1`, lấy byte `1`.
5. `mark()` đặt mark tại **1**; không thay đổi position.
6. Sau `get()` thứ hai: `position=2`, lấy byte `2`.
7. Trước `compact`, `remaining = 3 - 2 = 1`, byte chưa đọc là **3**.
8. Sau `compact`: byte `3` được chuyển về index 0; `position=1`, `limit=6`, `capacity=6`, **mark không còn**. Buffer sẵn sàng ghi tiếp từ offset 1.

Nếu sau bước cuối gọi `reset()`, chương trình sẽ ném `InvalidMarkException` vì `compact()` loại bỏ mark.

## 16. Ghi nhớ

**Bộ bốn trạng thái luôn tuân theo `0 <= position <= limit <= capacity`**, còn mark (nếu tồn tại) phải nằm trong `[0, position]`. `position` là nơi thao tác tương đối tiếp theo, `limit` là ranh giới trên độc quyền, `capacity` là sức chứa cố định, `mark` dùng để nhớ và phục hồi position.

Khi viết logic I/O, hãy luôn biết buffer ở giai đoạn nào:

- **Ghi xong → đọc:** `flip()`.
- **Đọc xong và bỏ dữ liệu cũ → ghi lại:** `clear()`.
- **Cần đọc lại dữ liệu trong limit:** `rewind()`.
- **Chưa đọc hết và cần nhận/ghi thêm:** `compact()`.
- **Chỉ muốn trở về một vị trí đã đánh dấu:** `mark()` rồi `reset()`, miễn mark chưa bị hủy.

Tài liệu tham khảo: Java SE 17 API — `java.nio.Buffer`, `java.nio.ByteBuffer`, `java.nio.InvalidMarkException` và `java.nio.channels.FileChannel`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-buffer-003">Chu trình ghi vào buffer, flip và đọc khỏi buffer</a>

<details>
<summary>Xem chi tiết</summary>

# Chu trình ghi vào buffer, `flip()` và đọc khỏi buffer trong Java NIO

## 1. Buffer không tự biết lúc nào bạn muốn ghi hay đọc

Trong Java NIO (`java.nio`), **buffer** là vùng lưu dữ liệu kèm các con trỏ mô tả phần nào đang được phép truy cập. Khi đọc dữ liệu từ channel, dữ liệu thường được **ghi vào buffer** trước, rồi chương trình **đọc ra khỏi buffer** để xử lý hoặc ghi sang channel khác.

Một `ByteBuffer` có ba đại lượng cần quan sát liên tục:

- **`capacity`**: tổng số phần tử mà buffer có thể chứa, cố định trong suốt vòng đời buffer.
- **`position`**: vị trí của phần tử sẽ được đọc/ghi tiếp theo khi dùng các thao tác *tương đối* như `get()`, `put(byte)`.
- **`limit`**: chỉ số đầu tiên **nằm ngoài vùng thao tác được phép** trong trạng thái hiện tại.

Bất biến:

~~~text
0 <= position <= limit <= capacity
remaining() = limit - position
hasRemaining() tương đương position < limit
~~~

**“Chế độ ghi” và “chế độ đọc” là cách chương trình sử dụng buffer**, không phải một cờ `READ/WRITE` được tự động chuyển đổi. Các lời gọi `flip()`, `clear()`, `rewind()` và `compact()` thay đổi `position`/`limit` để chuẩn bị cho bước kế tiếp.

Ví dụ buffer có `capacity = 8`, đang ghi 3 byte:

~~~text
index:      0   1   2   3   4   5   6   7
dữ liệu:    A   B   C   ?   ?   ?   ?   ?
           └─ đã ghi ─┘└──── còn chỗ ghi ────┘
position = 3
limit    = 8
capacity = 8
~~~

Ký hiệu `?` biểu diễn dữ liệu chưa có ý nghĩa cho lần ghi này; không được đọc các ô đó như nội dung hợp lệ.

## 2. Giai đoạn 1: ghi *vào* buffer

Sau khi tạo:

~~~java
ByteBuffer buffer = ByteBuffer.allocate(8);
~~~

Trạng thái ban đầu:

~~~text
position = 0
limit    = 8
capacity = 8
remaining = 8
~~~

Lệnh ghi tương đối:

~~~java
buffer.put((byte) 'A');
buffer.put((byte) 'B');
buffer.put((byte) 'C');
~~~

Mỗi `put(byte)` đặt một byte tại `position` hiện tại, sau đó tăng `position` lên 1. Sau ba lệnh:

~~~text
position = 3
limit    = 8
capacity = 8
remaining = 5
~~~

Đó là trạng thái **đang ghi/fill**. `limit` vẫn là 8 vì chưa gọi `flip()`. Nếu gọi `get()` ngay lúc này, `get()` sẽ đọc từ index 3 (nếu còn trong limit), nơi có thể chứa byte cũ hoặc byte không thuộc đợt ghi hiện tại. Không có cơ chế tự động hiểu “3 byte vừa ghi là toàn bộ dữ liệu hợp lệ”.

Lệnh `channel.read(buffer)` cũng thuộc pha **ghi vào buffer**: channel lấy dữ liệu từ nguồn rồi đưa vào các vị trí từ `position` đến trước `limit`. Nếu đọc được `n > 0` byte, `position` tăng `n`. Giá trị `0` có thể xuất hiện khi chưa có dữ liệu hoặc thao tác không tạo tiến triển; `-1` báo kết thúc đầu vào với `ReadableByteChannel`.

Lưu ý về giới hạn: nếu `position == limit` thì `remaining() == 0`, một lệnh `put(byte)` tương đối nữa sẽ ném `BufferOverflowException`. `ByteBuffer` không tự nới dung lượng.

## 3. Giai đoạn 2: `flip()` chuyển từ ghi sang đọc

Sau khi ghi xong *một đợt*, gọi:

~~~java
buffer.flip();
~~~

`flip()` thực hiện về mặt trạng thái:

~~~text
limit = position cũ
position = 0
mark bị hủy (nếu có)
capacity không đổi
~~~

Trước và sau khi ghi A, B, C vào buffer 8 byte:

| Thời điểm | position | limit | capacity | remaining |
| --- | ---: | ---: | ---: | ---: |
| Mới tạo | 0 | 8 | 8 | 8 |
| Sau 3 lần `put` | 3 | 8 | 8 | 5 |
| Sau `flip()` | 0 | 3 | 8 | 3 |

~~~text
index:      0   1   2   3   4   5   6   7
dữ liệu:    A   B   C   ?   ?   ?   ?   ?
           └─ vùng đọc ─┘└── ngoài limit ─────┘
position = 0          limit = 3          capacity = 8
~~~

Lúc này `get()` có thể đọc đúng **3 byte vừa ghi**, và không đọc vào phần trống sau index 2.

`flip()` **không** đảo byte, không di chuyển dữ liệu, không cấp phát vùng nhớ mới và không chuyển ByteBuffer thành đối tượng read-only. Tên “flip” chỉ diễn tả đổi ranh giới truy cập: phần dữ liệu vừa được tạo trở thành vùng sẽ được tiêu thụ.

**Nhầm lẫn thường gặp:** gọi `flip()` hai lần liên tiếp mà không ghi/đọc có mục đích. Sau `flip` đầu, `position = 0`; `flip` thứ hai đặt `limit = 0`, khiến buffer trông như không còn byte nào để đọc. Chỉ `flip()` sau pha ghi thực sự.

## 4. Giai đoạn 3: đọc *khỏi* buffer

Sau `flip()`:

~~~java
while (buffer.hasRemaining()) {
    byte value = buffer.get();
    // Xử lý value.
}
~~~

Mỗi `get()` tương đối lấy phần tử tại `position` hiện tại rồi tăng `position`. Khi đã đọc A, B, C:

~~~text
position = 3
limit    = 3
remaining = 0
hasRemaining() = false
~~~

Nếu gọi thêm `get()` tương đối thì phát sinh `BufferUnderflowException` vì đã vượt giới hạn dữ liệu đang được phép đọc.

Vòng `while (buffer.hasRemaining())` diễn đạt rõ “đọc cho tới hết phần đang có”. Nếu cần biết số byte chưa đọc, dùng `remaining()` thay vì `capacity - position`: sau `flip`, phần từ `limit` tới `capacity` không thuộc dữ liệu của đợt hiện tại.

### Tương đối và tuyệt đối

- `get()` và `put(value)` là thao tác **tương đối**, làm thay đổi `position`.
- `get(index)` và `put(index, value)` là thao tác **tuyệt đối**, tác động tới chỉ số được chỉ định nhưng **không tự tăng `position`**. Các lệnh này vẫn kiểm tra giới hạn index và khả năng đọc/ghi.
- Nếu dùng cả hai kiểu lẫn nhau, phải xác định rõ `position` có còn biểu diễn đúng nơi sẽ tiếp tục đọc/ghi hay không.

Đối với bài toán xử lý dữ liệu tuần tự, `put` rồi `flip` rồi `get` tương đối thường dễ theo dõi nhất.

## 5. Sơ đồ vòng đời chuẩn

~~~text
        ┌──────────────────────────────┐
        │ FILL: ghi dữ liệu vào buffer │
        │ put(...) / channel.read(...)│
        │ position tăng, limit giữ    │
        └───────────────┬──────────────┘
                        │ flip()
                        ▼
        ┌──────────────────────────────┐
        │ DRAIN: đọc dữ liệu có sẵn    │
        │ get(...) / channel.write(...)│
        │ position tăng tới limit     │
        └───────────────┬──────────────┘
                        │
               Đã đọc hết dữ liệu?
                  /            \
                Có             Còn dư
                │                │
              clear()         compact()
                │                │
                └─────→ FILL ←───┘
~~~

Khi đọc một file vào channel rồi đọc khỏi buffer, chu trình quen thuộc là:

1. Buffer còn chỗ trống: ghi từ nguồn vào buffer.
2. `flip()`: đặt ranh giới dữ liệu vừa nhận để có thể đọc.
3. Đọc/ghi ra khỏi buffer trong phạm vi `position..limit`.
4. Nếu không còn dữ liệu chưa xử lý, dùng `clear()`. Nếu còn dữ liệu chưa tiêu thụ, cân nhắc `compact()`.
5. Tiếp tục đợt ghi vào buffer mới.

**Không phải mọi lần đọc channel sẽ lấp đầy buffer**, và không phải mọi lần ghi channel sẽ tiêu thụ toàn bộ dữ liệu. Luôn dựa vào `position`, `remaining()` và kết quả I/O thực tế.

## 6. Chương trình Java 17+ chạy độc lập: ghi → flip → đọc

Lưu file `BufferCycleDemo.java`:

~~~java
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class BufferCycleDemo {

    private static void state(String name, ByteBuffer b) {
        System.out.printf(
                "%s: position=%d, limit=%d, capacity=%d, remaining=%d%n",
                name, b.position(), b.limit(), b.capacity(), b.remaining());
    }

    private static String readAscii(ByteBuffer b) {
        StringBuilder result = new StringBuilder();
        while (b.hasRemaining()) {
            result.append((char) b.get());
        }
        return result.toString();
    }

    public static void main(String[] args) {
        ByteBuffer b = ByteBuffer.allocate(8);
        state("new", b);

        b.put((byte) 'A');
        b.put((byte) 'B');
        b.put((byte) 'C');
        state("written", b);

        b.flip();
        state("flipped", b);

        System.out.println("read = " + readAscii(b));
        state("drained", b);

        b.rewind();
        System.out.println("reread = " + readAscii(b));

        b.clear();
        state("cleared", b);
        b.put((byte) 'X');
        b.flip();
        System.out.println("next = " + readAscii(b));

        ByteBuffer partial = ByteBuffer.allocate(6);
        partial.put("ABCD".getBytes(StandardCharsets.US_ASCII));
        partial.flip();

        System.out.println(
                "consumed = " + (char) partial.get() + (char) partial.get());
        state("two read", partial);

        partial.compact();
        state("compacted", partial);
        partial.put((byte) 'E');
        partial.put((byte) 'F');
        partial.flip();

        System.out.println("remaining + new = " + readAscii(partial));
        state("second drained", partial);
    }
}
~~~

Chạy từ thư mục chứa file bằng JDK 17+:

~~~bash
javac BufferCycleDemo.java
java BufferCycleDemo
~~~

Stdout chính xác:

~~~text
new: position=0, limit=8, capacity=8, remaining=8
written: position=3, limit=8, capacity=8, remaining=5
flipped: position=0, limit=3, capacity=8, remaining=3
read = ABC
drained: position=3, limit=3, capacity=8, remaining=0
reread = ABC
cleared: position=0, limit=8, capacity=8, remaining=8
next = X
consumed = AB
two read: position=2, limit=4, capacity=6, remaining=2
compacted: position=2, limit=6, capacity=6, remaining=4
remaining + new = CDEF
second drained: position=4, limit=4, capacity=6, remaining=0
~~~

Vì ví dụ chỉ ghi các ký tự ASCII đơn byte, chuyển từ byte sang `char` trong `readAscii` là đủ cho việc minh họa con trỏ. **Không** dùng cách cast đó làm bộ giải mã UTF-8 tổng quát: ký tự tiếng Việt có thể chiếm nhiều byte. Với dữ liệu văn bản thực tế, dùng `CharsetDecoder` hoặc API giải mã qua `StandardCharsets.UTF_8` và bảo toàn các chuỗi byte chưa đủ tạo thành ký tự hoàn chỉnh.

Đọc các mốc quan trọng:

- `written`: ba byte vừa được ghi nhưng `limit` vẫn là 8.
- `flipped`: `position` về 0, `limit` thành 3, đúng số byte đã ghi.
- `drained`: vòng `readAscii` lấy hết ba byte, nên `remaining` về 0.
- `reread`: `rewind()` cho đọc lại A, B, C vì `limit` vẫn là 3.
- `cleared`: `clear()` đặt lại cửa sổ ghi; tiếp theo chỉ ghi X và `flip`, vì vậy lần đọc mới chỉ thấy X.
- `two read`/`compacted`: hai byte C, D chưa đọc được giữ lại để ghép thêm E, F ở đợt kế tiếp.

## 7. `clear()`, `rewind()` và `compact()` khác nhau thế nào?

Ba thao tác này **không tương đương**, dù đều thường xuất hiện gần cuối pha đọc.

### `clear()`: chuẩn bị ghi mới, bỏ qua phần chưa đọc

~~~java
buffer.clear();
~~~

Tác dụng:

~~~text
position = 0
limit = capacity
mark bị hủy
~~~

`clear()` **không xóa dữ liệu vật lý** trong backing array hoặc bộ nhớ trực tiếp; tên phương thức có nghĩa là “đặt lại trạng thái truy cập để sử dụng lại vùng chứa”. Nếu trước `clear` vẫn còn dữ liệu chưa đọc, chương trình đang chủ động bỏ qua chúng. Do đó chỉ dùng `clear()` khi đã tiêu thụ xong nội dung cũ hoặc quyết định không cần nó nữa.

### `rewind()`: đọc lại cùng vùng dữ liệu

~~~java
buffer.rewind();
~~~

Tác dụng:

~~~text
position = 0
limit giữ nguyên
mark bị hủy
~~~

Sau khi đọc hết A, B, C và có `limit=3`, `rewind()` cho phép đọc lại cả ba byte mà không gọi `flip()` lần nữa. `rewind()` phù hợp khi cần quét lại dữ liệu trong cửa sổ hiện tại, không nhằm chuẩn bị nhận đợt dữ liệu mới từ channel.

### `compact()`: giữ phần chưa đọc, mở chỗ để ghi tiếp

Giả sử buffer 6 byte chứa A, B, C, D đã `flip` rồi chỉ đọc A và B:

~~~text
index:        0   1   2   3   4   5
data:         A   B   C   D   ?   ?
                  ↑       ↑
             position=2  limit=4
unread: C D
~~~

Gọi:

~~~java
buffer.compact();
~~~

Khi đó `compact()` chuyển **hai byte còn chưa đọc (C, D)** lên đầu buffer, rồi đặt:

~~~text
index:        0   1   2   3   4   5
data:         C   D   ?   ?   ?   ?
position = 2
limit    = 6
capacity = 6
remaining = 4
~~~

Dấu `?` không tuyên bố bộ nhớ được dọn sạch: các byte bên ngoài phần dữ liệu hợp lệ có thể vẫn chứa giá trị cũ. Sau `compact`, buffer sẵn sàng **ghi nối tiếp từ position 2**. Ghi thêm E, F rồi `flip` sẽ cho vùng đọc C, D, E, F.

`compact()` là bước đặc biệt hữu ích khi một gói tin, bản ghi hoặc ký tự nhiều byte chưa được nhận đủ để xử lý. Nó giữ lại dữ liệu chưa tiêu thụ. Nếu buffer là read-only, `compact()` sẽ ném `ReadOnlyBufferException`.

| Phương thức | position sau gọi | limit sau gọi | Phần dữ liệu còn dùng |
| --- | --- | --- | --- |
| `flip()` | 0 | position cũ | Dữ liệu vừa ghi, từ 0 tới position cũ |
| `clear()` | 0 | capacity | Không cam kết giữ dữ liệu cũ để đọc tiếp |
| `rewind()` | 0 | limit cũ | Cùng vùng dữ liệu trong limit hiện tại |
| `compact()` | Số phần tử chưa đọc | capacity | Các phần tử từ position cũ tới limit cũ, đã dồn lên đầu |

`compact()` tiêu tốn công di chuyển phần tử chưa đọc; nếu đã tiêu thụ hết thì `clear()` dễ hiểu và thường không cần sao chép gì.

## 8. Gắn chu trình với `Channel`

Có hai chiều nhìn dễ gây nhầm:

- `channel.read(buffer)`: **đọc từ channel**, nghĩa là **ghi vào buffer**. Nó làm tăng `buffer.position()` theo số byte đọc được.
- `channel.write(buffer)`: **ghi vào channel**, nghĩa là **đọc ra khỏi buffer**. Nó làm tăng `buffer.position()` theo số byte thực sự được ghi.

Dòng chảy khi sao chép tệp:

~~~text
Source FileChannel
     │ read(buffer)   — fill
     ▼
ByteBuffer: position tiến về limit/capacity
     │ flip()
     ▼
ByteBuffer: position=0, limit=số byte đã nhận
     │ write(buffer)  — drain
     ▼
Destination FileChannel
     │ clear() hoặc compact()
     └────────────────────→ đợt tiếp theo
~~~

### Ví dụ minh họa sao chép tệp theo đợt

Đoạn này dành cho trường hợp có sẵn hai đối tượng `FileChannel input`, `FileChannel output` mở ở chế độ đọc/ghi phù hợp. **Đây là mảnh code minh họa nằm trong phương thức**, không phải chương trình Java độc lập:

~~~java
ByteBuffer buf = ByteBuffer.allocate(4096);
int count;
while ((count = input.read(buf)) != -1) {
    buf.flip();
    while (buf.hasRemaining()) {
        output.write(buf);
    }
    buf.clear();
}
~~~

Ý nghĩa:

1. `input.read(buf)` điền byte vào buffer; không giả định sẽ nhận đủ 4096 byte.
2. `flip()` đặt `limit` bằng số byte đã có.
3. Vòng `while (buf.hasRemaining())` bảo đảm output channel có cơ hội nhận hết những byte chưa ghi. Một lần `write` không nhất thiết tiêu thụ hết buffer.
4. `clear()` an toàn trong ví dụ này vì vòng trước chỉ hoàn thành khi đã chuyển hết byte trong buffer.
5. `read` trả `-1` báo EOF; không còn đợt byte mới để đưa vào buffer.

Đoạn code trên thể hiện chu trình cho kênh tệp thông thường; với **non-blocking channel**, `read/write` có thể trả `0` khi chưa tiến triển. Đặc biệt, không được bận chờ vô hạn bằng một vòng `while` gọi `write` liên tục nếu đầu ra chưa sẵn sàng. Trong môi trường NIO không chặn, hãy dùng `Selector`/khả năng sẵn sàng ghi hoặc cơ chế điều phối phù hợp, đồng thời giữ lại phần dữ liệu chưa gửi bằng `compact()` hoặc không chuyển trạng thái buffer sai lúc.

Với dữ liệu phân mảnh (chẳng hạn một bản ghi chưa đủ byte để phân tích), quy tắc là **sau `flip` chỉ tiêu thụ các bản ghi hoàn chỉnh; sau đó `compact` để giữ phần đuôi chưa xử lý**, trước khi đọc thêm từ nguồn. `clear` sẽ làm mất vùng dữ liệu cần ghép giữa hai đợt.

## 9. Những lỗi hay gặp khi quản lý `position` và `limit`

### Quên `flip()` trước khi đọc

~~~java
ByteBuffer b = ByteBuffer.allocate(8);
b.put((byte) 'A');
byte x = b.get(); // Sai mục đích: đọc từ position=1, không phải A ở index=0.
~~~

`get()` ở ví dụ có thể trả giá trị cũ ở index 1, không ném lỗi, khiến bug khó nhận ra. Cách sửa là gọi `b.flip()` trước khi bắt đầu đọc byte vừa ghi.

### Dùng `flip()` thay `clear()` trước lượt ghi mới

Sau khi đọc hết buffer, `position == limit`. Gọi `flip()` sẽ đặt `limit` thành số byte đã đọc và `position` về 0, chưa chắc mang ý nghĩa “chuẩn bị ghi mới với toàn bộ capacity”. Để tái sử dụng buffer cho đợt ghi mới, dùng `clear()` nếu không còn dữ liệu cần bảo toàn, hoặc `compact()` nếu còn dữ liệu chưa xử lý.

### Dùng `clear()` khi vẫn còn byte chưa xử lý

Giả sử đã nhận một phần bản tin, chỉ xử lý được header mà body chưa đủ. `clear()` đặt lại vùng ghi nhưng loại bỏ trạng thái chỉ ra phần body đang đợi. Kết quả là bản tin bị mất/không ghép được. Với dữ liệu chưa đọc nằm ở đoạn `position..limit`, dùng `compact()`.

### Đọc quá `limit` hoặc ghi khi hết chỗ

Các thao tác tương đối kiểm tra trạng thái:

- `get()` khi `position == limit` ⇒ `BufferUnderflowException`.
- `put(byte)` khi `position == limit` ⇒ `BufferOverflowException`.

Dùng `hasRemaining()` hoặc `remaining()`; khi ghi nhiều byte trong một lần, kiểm tra còn đủ chỗ trước khi gọi.

### Tưởng `capacity` là số byte hợp lệ

`capacity` phản ánh dung lượng vùng chứa, không phải lượng dữ liệu đã nhận. Sau `flip()`, **`limit`** là ranh giới dữ liệu có thể đọc; `remaining()` là số phần tử chưa đọc tính từ `position`.

### Tưởng `flip()` biến `ByteBuffer` thành dữ liệu văn bản

Buffer là dãy byte. Với UTF-8, một ký tự có thể trải qua nhiều lần `channel.read`. Đọc từng byte và cast sang `char` chỉ đáng tin với ASCII trong ví dụ học tập. Khi xây parser text, dùng charset decoder hỗ trợ chuỗi byte chưa hoàn chỉnh và xử lý trạng thái cuối luồng đúng cách.

### Hiểu sai `mark()` và `reset()`

`mark()` lưu position hiện tại; `reset()` quay về mark nếu còn hợp lệ. `flip()`, `clear()`, `rewind()` và `compact()` đều hủy mark, nên `reset()` sau các thao tác đó sẽ ném `InvalidMarkException`. Hãy đặt mark lại sau khi chuyển trạng thái nếu cần.

## 10. Tự kiểm tra

1. Một `ByteBuffer.allocate(10)` vừa được `put` 4 byte. Hãy xác định `position=4`, `limit=10`. Sau `flip()`, kết quả là `position=0`, `limit=4`, `remaining=4`.
2. Sau `flip` của câu 1, chương trình đọc 2 byte. Còn `remaining=2`. Nếu muốn giữ hai byte đó và ghi nối đợt sau, chọn **`compact()`**, khi đó `position=2`, `limit=10`.
3. Nếu đã đọc hết tất cả byte trong buffer và cần ghi đợt mới, chọn **`clear()`**. Vì sao không cần `compact()`? Không còn dữ liệu chưa đọc để dồn về đầu.
4. Nếu muốn đọc lại cùng 4 byte mà không thay đổi `limit`, chọn **`rewind()`**.
5. Một `channel.write(buffer)` ghi được ít byte hơn `buffer.remaining()`. Vì sao không `clear()` ngay? Vì phần còn lại trong buffer chưa được gửi, phải giữ để gửi tiếp.
6. Tại sao `flip()` là bước bắt buộc trong mô hình ghi rồi đọc tương đối? Vì nó đặt `limit` đúng bằng lượng dữ liệu vừa ghi, đồng thời đưa `position` về đầu vùng dữ liệu hợp lệ.

**Ghi nhớ thực hành:** Mẫu chuẩn là **`put`/`channel.read` → `flip` → `get`/`channel.write` → `clear` hoặc `compact`**. Luôn dùng `position`, `limit` và `remaining()` làm bằng chứng cho byte nào đang có, byte nào đã được tiêu thụ và chỗ nào còn có thể ghi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-buffer-004">clear không xóa dữ liệu trong vùng nhớ</a>

<details>
<summary>Xem chi tiết</summary>

# `Buffer.clear()` không xóa dữ liệu trong vùng nhớ — Java NIO Buffer

## 1. Vấn đề cốt lõi

Tên `clear()` dễ làm người mới hiểu nhầm: gọi `ByteBuffer.clear()` **không xóa các byte đã ghi và không đặt chúng về 0**. Lời gọi này chỉ đặt lại **trạng thái điều khiển việc đọc/ghi** của buffer để chuẩn bị ghi một lượt dữ liệu mới. Đây là hành vi theo hợp đồng API của `java.nio.Buffer`, không phải mẹo phụ thuộc JVM.

Điểm cần phân biệt:

- **Vùng nhớ lưu trữ (storage):** nơi các giá trị byte, char, int... thực sự nằm.
- **Trạng thái của buffer:** các chỉ số `position`, `limit`, `capacity` và `mark` cho biết ứng dụng nên đọc/ghi trong phạm vi nào.
- **Dữ liệu hợp lệ về mặt nghiệp vụ:** thường chỉ là phần đã được ghi ở lượt hiện tại và được giới hạn bằng `flip()`, chứ không phải mọi byte cũ còn nằm trong storage.

Trong module Java Core I/O, hiểu đúng `clear()` là điều kiện để dùng `ByteBuffer` với `FileChannel`, `SocketChannel`, bộ phân tích dữ liệu nhị phân và xử lý dữ liệu nhạy cảm mà không đọc nhầm byte của lượt trước.

## 2. Bốn thuộc tính quyết định cách buffer hoạt động

Một `Buffer` có bốn khái niệm chính:

| Thuộc tính | Ý nghĩa | Bất biến |
| --- | --- | --- |
| `capacity` | Số phần tử tối đa mà buffer có thể chứa theo góc nhìn của chính buffer đó | Không đổi trong vòng đời instance |
| `limit` | Vị trí đầu tiên không được phép đọc/ghi trong lượt thao tác hiện tại | `0 <= limit <= capacity` |
| `position` | Vị trí phần tử tiếp theo của thao tác đọc/ghi tương đối | `0 <= position <= limit` |
| `mark` | Vị trí đã đánh dấu để `reset()` quay lại | Hoặc không xác định, hoặc `mark <= position` |

Giả sử một `ByteBuffer` có `capacity=8` và vừa ghi ba byte:

```text
Chỉ số:       0   1   2   3   4   5   6   7
Storage:     'A' 'B' 'C'  0   0   0   0   0
Trạng thái:  position=3, limit=8, capacity=8
                                   ^
                      Có thể ghi tiếp từ vị trí 3
```

Các byte trong storage không tự động được gắn nhãn “đã cũ” hay “đang hợp lệ”. `position` và `limit` chỉ giúp code quản lý phần đã ghi hoặc có thể đọc. Một thao tác **absolute** như `get(0)` dùng chỉ số được truyền trực tiếp; thao tác **relative** như `get()` dùng `position` rồi tăng nó.

## 3. Hợp đồng chính xác của `clear()`

Đối với `Buffer.clear()` và các lớp con NIO như `ByteBuffer`, hành vi là:

```text
position = 0
limit    = capacity
mark     = undefined (bỏ mark cũ)
storage  = KHÔNG thay đổi do clear()
```

Hình dung trước và sau `clear()` với cùng một vùng nhớ:

```text
Trước clear:
  capacity = 8       limit = 8       position = 3
  storage  = [A, B, C, 0, 0, 0, 0, 0]

Sau clear:
  capacity = 8       limit = 8       position = 0
  storage  = [A, B, C, 0, 0, 0, 0, 0]
             ^
         Lượt ghi mới bắt đầu tại đây
```

**Vì sao Java thiết kế như vậy?** Buffer thường được tái sử dụng rất nhiều lần khi làm I/O. Phần mềm chỉ cần biết “được ghi lại từ đầu”, nên việc xóa từng byte là công việc thừa, tốn chi phí `O(capacity)`. `clear()` thay đổi metadata, thường có độ phức tạp hằng số, giúp vòng lặp đọc kênh hoạt động hiệu quả.

**Không nên hiểu `clear()` là một lệnh bảo mật, giải phóng RAM, thu nhỏ dung lượng hay xóa dữ liệu của file hoặc socket nguồn.** Nó chỉ sửa trạng thái của đối tượng buffer.

## 4. Ví dụ Java 17+ có thể chạy: byte cũ vẫn còn nguyên

Tạo tệp `BufferClearDemo.java`:

```java
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class BufferClearDemo {
    public static void main(String[] args) {
        ByteBuffer buffer = ByteBuffer.allocate(6);

        buffer.put((byte) 'A');
        buffer.put((byte) 'B');
        buffer.put((byte) 'C');

        System.out.println("Before clear: position=" + buffer.position()
                + ", limit=" + buffer.limit());

        buffer.clear();

        System.out.println("After clear: position=" + buffer.position()
                + ", limit=" + buffer.limit());
        System.out.println("Old byte at index 2: " + (char) buffer.get(2));

        buffer.put((byte) 'X');
        buffer.put((byte) 'Y');
        buffer.flip();

        byte[] current = new byte[buffer.remaining()];
        buffer.get(current);

        System.out.println("Current logical data: "
                + new String(current, StandardCharsets.US_ASCII));
        System.out.println("Underlying bytes: "
                + Arrays.toString(buffer.array()));
    }
}
```

Biên dịch/chạy bằng JDK 17+:

```bash
javac BufferClearDemo.java
java BufferClearDemo
```

Kết quả chuẩn xác:

```text
Before clear: position=3, limit=6
After clear: position=0, limit=6
Old byte at index 2: C
Current logical data: XY
Underlying bytes: [88, 89, 67, 0, 0, 0]
```

Phân tích:

1. `allocate(6)` tạo một heap `ByteBuffer` có mảng byte mới, ban đầu chứa 6 byte bằng 0. Đây là hệ quả khởi tạo của `byte[]`, không phải do `clear()`.
2. Sau ba lệnh `put`, `position=3`; trong storage có các mã ASCII 65, 66, 67 tại chỉ số 0, 1, 2.
3. `clear()` đặt `position=0, limit=6`. Lệnh `get(2)` là **absolute get**, không làm thay đổi `position`, nên vẫn đọc được byte `C` từ lượt trước.
4. Hai lệnh `put` tiếp theo ghi `X` và `Y` tại chỉ số 0 và 1, **không đụng đến** byte `C` tại chỉ số 2.
5. `flip()` đặt `limit=2, position=0`; vì thế lần đọc tương đối tiếp theo chỉ lấy `XY`. Mảng backing còn `[88,89,67,0,0,0]`: byte `C` vẫn tồn tại nhưng **không nằm trong đoạn dữ liệu hợp lệ của lượt mới**.

Đây là điểm quan trọng: việc `get(2)` nhìn thấy dữ liệu cũ sau `clear()` **không cho phép** coi dữ liệu đó là một phần thông điệp mới.

## 5. Khi nào stale data có thể vô tình được đọc?

Sau `clear()`, `limit=capacity`, nên nếu gọi ngay `get()` hoặc `get(index)` mà chưa ghi dữ liệu mới, API vẫn có thể đọc các giá trị từ storage cũ. Buffer không biết dữ liệu nào được ứng dụng coi là “hợp lệ”.

Ví dụ sai:

```java
ByteBuffer buffer = ByteBuffer.allocate(4);
buffer.put(new byte[] {10, 20, 30});
buffer.clear();

// Sai về mặt nghiệp vụ: đây là dữ liệu cũ, không phải lượt đọc mới.
byte old = buffer.get();
System.out.println(old); // 10
```

Đây là đoạn mã minh họa đặt trong phương thức; có thể chạy khi thêm import `java.nio.ByteBuffer`. Để đọc đúng lượt hiện tại, hãy ghi hoặc để channel điền byte mới, sau đó `flip()` và chỉ đọc trong `remaining()` đã giới hạn.

Các trường hợp dễ lộ dữ liệu cũ:

- Quên `flip()` sau khi ghi ít hơn `capacity` nhưng vẫn đọc theo toàn bộ buffer.
- Dùng `buffer.array()` rồi truyền **cả mảng** cho hàm ghi file, gửi mạng hoặc serialize. Heap `ByteBuffer.array()` có thể trả toàn bộ backing array với byte cũ phía ngoài vùng dữ liệu hiện tại; không dùng mảng đó như số byte hợp lệ.
- Dùng `get(index)` hoặc `put(index,...)` sai phạm vi logic, vì absolute index có quy tắc kiểm tra theo `limit`, không theo số byte hợp lệ mà nghiệp vụ mong muốn.
- Dùng chung buffer giữa các tác vụ/luồng mà không đồng bộ. Buffer không an toàn cho nhiều luồng cập nhật trạng thái cùng lúc.

Các lệnh `get()`/ `put()` tương đối sử dụng `position` và `limit`, nhưng **không có cơ chế tự đánh dấu byte thuộc yêu cầu mạng nào**. Phân định ranh giới thông điệp vẫn là nhiệm vụ của chương trình.

## 6. Quan hệ giữa `clear()`, `flip()`, `rewind()`, `compact()`

| Phương thức | `position` sau gọi | `limit` sau gọi | Storage có bị xóa không? | Mục đích |
| --- | --- | --- | --- | --- |
| `clear()` | `0` | `capacity` | Không | Chuẩn bị **ghi mới từ đầu** |
| `flip()` | `0` | `position` trước gọi | Không | Chuyển từ **ghi** sang **đọc lượng vừa ghi** |
| `rewind()` | `0` | Giữ nguyên | Không | Đọc/ghi lại trong giới hạn hiện tại |
| `compact()` | Số phần tử còn lại trước gọi | `capacity` | Không; có thể **dịch chuyển** dữ liệu còn lại | Giữ phần chưa tiêu thụ, tiếp tục ghi vào phía sau |

Cả bốn thao tác đều loại bỏ mark đã đặt trước đó. `compact()` khác ba thao tác trên vì nó **có thể ghi đè storage** khi dồn các byte từ đoạn `[position, limit)` về đầu buffer, nhưng vẫn **không xóa an toàn toàn bộ dữ liệu cũ**.

### `flip()`: giới hạn dữ liệu mới

Sau khi `put` 3 byte vào buffer dung lượng 8, `position=3`. Gọi `flip()` tạo `position=0, limit=3`, nên `remaining()=3`. Nếu gọi `clear()` thay `flip()` rồi đọc, `remaining()=8`, có nguy cơ đọc thêm byte chưa thuộc lượt hiện tại.

### `rewind()`: đọc lại chứ không làm trống

Với một buffer sau `flip()`, `rewind()` cho phép đọc lại cùng giới hạn `limit`. Nội dung vẫn giữ nguyên. Chọn `rewind` nếu cần duyệt lại dữ liệu đang hợp lệ; chọn `clear` khi muốn ghi lượt mới từ đầu.

### `compact()`: giữ phần còn lại

Giả sử buffer chứa `ABCDE` và đã đọc `AB`. Sau `compact()`, phần chưa đọc `CDE` được dồn về đầu, `position=3`, `limit=capacity`; lượt ghi tiếp nối sau `CDE`. Sau đó `flip()` mới chuyển lại chế độ đọc.

Không giả định các byte nằm ngoài phần hợp lệ sau `compact()` bằng 0: API không cam kết dọn vùng nhớ dư.

## 7. Vòng đời đọc/ghi đúng với `FileChannel` hoặc `SocketChannel`

```text
Chuẩn bị ghi vào buffer    Ghi byte vào buffer      Chuẩn bị đọc byte      Tiêu thụ
       clear()         ->   channel.read(buffer) ->   flip()           -> channel.write(buffer)
          ^                                                              |
          |_____________________ xử lý phần còn lại rồi dùng lại __________|
```

Với một `FileChannel` đọc dữ liệu trong khi `WritableByteChannel` ghi ra nơi khác, mẫu cơ bản là:

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

static void copy(ReadableByteChannel source,
                 WritableByteChannel destination) throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(8192);

    while (true) {
        buffer.clear();                // Chuẩn bị nhận dữ liệu mới
        int count = source.read(buffer);

        if (count == -1) {
            break;                     // Hết nguồn
        }
        if (count == 0) {
            continue;                  // Không có byte mới; tùy channel cần chờ phù hợp
        }

        buffer.flip();                 // Chỉ đọc count byte mới nhận
        while (buffer.hasRemaining()) {
            int written = destination.write(buffer);
            if (written == 0) {
                // Với kênh non-blocking, cần chờ khả năng ghi (Selector, v.v.).
                // Không spin vô hạn ở đây trong ứng dụng thực tế.
                throw new IOException("Destination did not accept data");
            }
        }
    }
}
```

Đây là **phương thức minh họa**, đặt bên trong lớp Java với các import nêu trên. Mẫu phù hợp cho kênh ghi kiểu blocking hoặc nơi có chính sách rõ ràng khi `write` không tiến triển; đối với I/O không đồng bộ và non-blocking cần quản lý buffer/lượt đọc khác nhau.

Trong thực tế:

1. `clear()` thông báo “buffer sẵn sàng nhận tối đa `capacity` byte”, không cần xóa byte cũ.
2. `source.read(buffer)` tăng `position` theo số byte đọc được. Số trả về có thể thấp hơn dung lượng buffer, bằng 0, hoặc `-1` khi hết dữ liệu.
3. `flip()` đặt `limit` bằng số byte đã nhận trong lượt này, không cho bộ tiêu thụ đọc nhầm phần còn sót của lượt trước bằng relative `get`.
4. `write` có thể tiêu thụ ít hơn toàn bộ byte còn lại. Cần xử lý phần chưa gửi xong; nếu chỉ gửi một phần rồi `clear()`, dữ liệu **chưa gửi sẽ bị bỏ khỏi phạm vi logic** dù các byte vẫn ở RAM.

Đặc biệt ở socket non-blocking, khi vừa đọc vừa ghi, `compact()` thường được dùng để giữ phần chưa xử lý; `clear()` chỉ hợp lý khi không còn dữ liệu cần giữ lại.

## 8. `clear()` không liên quan đến việc xóa tệp hoặc giải phóng bộ nhớ

Ba thao tác hoàn toàn khác nhau:

- **`buffer.clear()`**: đặt lại cursor/trạng thái đọc ghi của buffer, không chỉnh nội dung file.
- **Ghi đè dữ liệu bên trong buffer**: dùng `put` hoặc `Arrays.fill` với vùng nhớ có thể ghi, làm byte lưu trong vùng cụ thể thay đổi.
- **Đóng hoặc giải phóng tài nguyên**: ví dụ `FileChannel.close()` đóng handle của channel; riêng `ByteBuffer.clear()` không đóng channel, không `free()` và không bắt GC thu hồi bộ nhớ.

Với **heap buffer** tạo bằng `ByteBuffer.allocate(n)`, có thể lấy mảng backing qua `array()` khi buffer hỗ trợ; với **direct buffer** tạo qua `ByteBuffer.allocateDirect(n)`, `array()` thường ném `UnsupportedOperationException` vì không có mảng Java được truy xuất theo cách đó. Cả hai loại buffer vẫn có `clear()` mang ngữ nghĩa metadata giống nhau.

Với **read-only buffer** từ `asReadOnlyBuffer()`, việc thay đổi `position`/`limit` qua `clear()` vẫn hợp lệ, nhưng `put` sẽ ném `ReadOnlyBufferException`. Điều này cho thấy `clear()` không tự thực hiện ghi đè dữ liệu.

## 9. Cần xóa byte nhạy cảm thật sự thì làm gì?

Đối với dữ liệu như mật khẩu, khóa mật mã hay nội dung riêng tư, `clear()` **không bảo đảm xóa dữ liệu cũ**. Nếu muốn giảm thời gian dữ liệu còn nằm trong vùng lưu trữ của buffer có thể ghi, phải chủ động ghi đè phần vùng nhớ liên quan.

Ví dụ phương thức ghi đè toàn bộ **vùng nhìn thấy qua một `ByteBuffer` có thể ghi**:

```java
import java.nio.ByteBuffer;

static void overwriteVisibleBytes(ByteBuffer buffer) {
    buffer.clear();                         // Mở giới hạn đến capacity
    while (buffer.hasRemaining()) {
        buffer.put((byte) 0);              // Ghi đè từng byte
    }
    buffer.clear();                         // Chuẩn bị trạng thái tái sử dụng
}
```

Đây là **phương thức minh họa** được đặt trong một lớp Java. Nó thay đổi storage thực sự; thời gian thực hiện tỷ lệ với `capacity`. Nếu buffer là read-only, `put` sẽ ném `ReadOnlyBufferException`. Nếu buffer là một `slice()`, chỉ các byte nằm trong vùng mà slice có thể nhìn thấy được ghi đè. Việc ghi đè một vùng chia sẻ cũng làm thay đổi dữ liệu nhìn thấy qua các view/duplicate liên quan.

Với mảng byte thuần:

```java
java.util.Arrays.fill(secretBytes, (byte) 0);
```

**Giới hạn bảo mật phải nói rõ:** Java/JVM, thư viện, GC, hệ điều hành, file hoán đổi, bản sao trong heap/native memory hoặc JIT có thể giữ thêm bản dữ liệu. Ghi đè một buffer/mảng không phải bằng chứng đã xóa mọi bản sao trên toàn hệ thống, và không đảm bảo “secure erase”. Hạn chế sao chép dữ liệu nhạy cảm ngay từ thiết kế, kiểm soát vòng đời và sử dụng API chuyên dụng khi có yêu cầu bảo mật cao.

## 10. `slice()`, `duplicate()` và dữ liệu còn lưu

`slice()` và `duplicate()` tạo ra các buffer với trạng thái `position`/`limit` riêng nhưng **chia sẻ nội dung storage** với buffer gốc.

```java
ByteBuffer original = ByteBuffer.allocate(4);
original.put(new byte[] {1, 2, 3, 4});
original.flip();

ByteBuffer view = original.duplicate();
view.clear();

System.out.println(view.get(2));      // 3
System.out.println(original.get(2));  // 3
```

Đây là đoạn minh họa trong phương thức (cần `import java.nio.ByteBuffer;`). `view.clear()` chỉ đặt lại state của view; không xóa phần storage chia sẻ và không đặt lại `position` hay `limit` của `original`. Tương tự, `slice()` có offset và capacity tương ứng với phần vùng nhớ được chọn; `clear()` trên slice chỉ đặt lại state của slice.

Điều này quan trọng với ứng dụng tái sử dụng buffer pool: nếu một thành phần đọc thẳng từ backing storage hoặc một view khác, dữ liệu cũ vẫn có thể thấy được cho đến khi storage được ghi đè.

## 11. Sai lầm thường gặp và cách sửa

| Quan niệm/sai lầm | Hậu quả | Cách làm đúng |
| --- | --- | --- |
| “`clear()` xóa toàn bộ byte/char.” | Byte cũ vẫn đọc được nếu phạm vi cho phép | Hiểu `clear` chỉ reset `position`, `limit`, `mark` |
| “`clear()` tương đương `Arrays.fill(...,0)`.” | Có thể rò dữ liệu hoặc bài kiểm tra bảo mật sai | Ghi đè chủ động khi cần, hiểu giới hạn bảo đảm của ghi đè |
| Ghi ít byte rồi `clear()` để bắt đầu đọc | `limit=capacity`, có thể đọc dữ liệu dư cũ | `flip()` sau ghi, đọc qua `remaining()` |
| Dùng `array().length` để xác định số byte mới | Độ dài storage thường lớn hơn lượng dữ liệu hợp lệ | Sử dụng `remaining()` sau `flip()` hoặc theo bộ đếm giao thức |
| Gọi `clear()` khi còn byte chưa gửi qua channel | Mất trạng thái theo dõi phần dữ liệu chưa gửi | Chờ ghi hết hoặc dùng `compact()` và quản lý backpressure |
| `clear()` khiến direct buffer được giải phóng | Tốn bộ nhớ native trong thời gian không mong muốn | Quản lý vòng đời đối tượng/tài nguyên và áp lực cấp phát |
| `clear()` thay đổi trạng thái mọi `duplicate()` | View còn chỉ số riêng; storage vẫn chia sẻ | Xem từng instance có state riêng, chia sẻ dữ liệu |
| Gọi `array()` với mọi `ByteBuffer` | Direct/read-only buffer có thể không hỗ trợ backing array công khai | Sử dụng `get`/`put` hoặc kiểm tra `hasArray()` trước |

## 12. Thực hành tự kiểm tra

### Câu 1: `clear()` đặt lại gì?

```java
ByteBuffer b = ByteBuffer.allocate(5);
b.put((byte) 7);
b.put((byte) 8);
b.clear();
```

**Đáp án:** `position=0`, `limit=5`, `capacity=5`; mark (nếu có) bị bỏ. Các byte `7`, `8` vẫn nằm tại vị trí 0, 1 cho đến khi bị ghi đè.

### Câu 2: Tại sao gọi `flip()` sau `clear()` ngay lập tức lại không trả dữ liệu đã viết ở lượt trước?

**Đáp án:** `clear()` đặt `position=0`; `flip()` lập tức đặt `limit=position=0`, làm `remaining()=0`. Byte cũ vẫn ở storage, nhưng vùng đọc tương đối hiện tại rỗng.

### Câu 3: Vì sao `get(2)` có thể đọc được byte cũ mà `get()` không đọc được cùng byte sau khi `flip()` với chỉ hai byte mới?

**Đáp án:** Sau `flip()`, `limit=2`. Cả absolute `get(2)` và relative `get()` khi đã đọc hết hai byte đều bị giới hạn bởi `limit`; `get(2)` sẽ ném `IndexOutOfBoundsException`, còn relative `get()` sẽ ném `BufferUnderflowException` nếu không còn phần tử. Trước `flip()`, `clear()` mở `limit=capacity` nên absolute `get(2)` mới có thể thấy byte cũ. Đây là hai **thời điểm trạng thái khác nhau**.

### Câu 4: Khi `FileChannel.read(buffer)` đọc được 3 byte vào buffer có `capacity=8192`, tại sao không được gửi cả 8192 byte đi?

**Đáp án:** Chỉ 3 byte mới thuộc lượt đọc. Sau `flip()`, `limit=3`; dùng `remaining()` hoặc `channel.write(buffer)` có kiểm soát để gửi đúng dữ liệu hợp lệ. Các byte còn lại không phải thông điệp hiện tại.

### Câu 5: Một view `duplicate()` gọi `clear()` có làm mất dữ liệu của buffer gốc không?

**Đáp án:** Không. Nó chỉ reset state của chính view; dữ liệu vẫn chia sẻ. Nếu view thực hiện `put`, lúc đó storage chia sẻ mới có thể thay đổi.

## Tóm tắt cần nhớ

```text
clear()   = sẵn sàng GHI từ đầu, không xóa storage
flip()    = sẵn sàng ĐỌC số phần tử vừa ghi
rewind()  = quay về đầu vùng đã giới hạn để đọc/ghi lại
compact() = giữ phần còn lại, dồn về đầu để ghi tiếp
overwrite = ghi đè byte nếu thực sự cần thay đổi storage
```

Quy tắc áp dụng thực tế: **dùng `clear()` để tái sử dụng buffer, `flip()` để xác định vùng dữ liệu mới có thể đọc, và ghi đè chủ động khi cần thay đổi nội dung lưu trữ.** Đừng coi bất kỳ thao tác chuyển state nào là cơ chế xóa an toàn dữ liệu.

## Tài liệu tham khảo

- Java SE 21, `java.nio.Buffer` (hợp đồng `clear`, `flip`, `rewind`, `mark`, `reset`): https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/Buffer.html
- Java SE 21, `java.nio.ByteBuffer` (relative/absolute operations, `compact`, backing array, direct buffer): https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html
- Java SE 21, `java.nio.channels.ReadableByteChannel`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/ReadableByteChannel.html
- Java SE 21, `java.nio.channels.WritableByteChannel`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/WritableByteChannel.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-buffer-005">rewind, compact và reset</a>

<details>
<summary>Xem chi tiết</summary>

# Java NIO Buffer: `rewind()`, `compact()` và `reset()`

## 1. Vì sao cần phân biệt ba thao tác này?

Khi dùng `java.nio.ByteBuffer` để đọc dữ liệu từ tệp/socket hoặc để ghép các mảnh thông điệp, ta thường luân phiên giữa **ghi dữ liệu vào buffer** và **đọc dữ liệu đã có trong buffer**. Vấn đề xuất hiện khi muốn:

- **Đọc lại từ đầu vùng đang được phép đọc:** dùng `rewind()`.
- **Giữ phần dữ liệu chưa xử lý, dồn nó về đầu để có chỗ nhận thêm dữ liệu:** dùng `compact()`.
- **Quay lại đúng vị trí đã đánh dấu trước đó:** dùng `mark()` kết hợp `reset()`.

Ba thao tác đều liên quan đến con trỏ, nhưng **không có cùng ý nghĩa**. Chọn sai có thể làm chương trình đọc lại dữ liệu cũ, bỏ sót phần dữ liệu còn dang dở hoặc cố đọc các byte chưa hợp lệ.

Bài viết dùng `ByteBuffer` trên Java 17+ để diễn giải; các nguyên tắc chung về `position`, `limit`, `mark` cũng áp dụng cho họ `java.nio.Buffer`. `compact()` là phương thức của các kiểu buffer cụ thể hỗ trợ nó (chẳng hạn `ByteBuffer`, `CharBuffer`), không phải phương thức trừu tượng của lớp nền `Buffer`.

## 2. Bốn trạng thái cần nắm: capacity, limit, position, mark

| Thuộc tính | Ý nghĩa |
|---|---|
| `capacity` | Số phần tử tối đa buffer có thể chứa; cố định trong vòng đời một buffer. Với `ByteBuffer`, đơn vị là **byte**. |
| `limit` | Ranh giới đầu tiên **không được** truy cập bằng thao tác tương đối; index hợp lệ nằm trước `limit`. |
| `position` | Vị trí của thao tác `get()` / `put()` tương đối tiếp theo; một lần `get()` hoặc `put()` thành công thường tăng `position` thêm một phần tử. |
| `mark` | Mốc tùy chọn do `mark()` ghi nhớ; `reset()` dùng để quay lại mốc này. Mark ban đầu **không được định nghĩa**. |

Với buffer có mark hợp lệ, bất biến là:

~~~text
0 <= mark <= position <= limit <= capacity
~~~

Nếu mark chưa được đặt, vẫn có `0 <= position <= limit <= capacity`.

Các API hữu ích:

- `buffer.position()`, `buffer.limit()`, `buffer.capacity()`: xem các giá trị.
- `buffer.remaining()` = `limit - position`; `buffer.hasRemaining()` cho biết còn vùng có thể truy cập tương đối hay không.
- `buffer.get()` / `buffer.put(value)`: truy cập tương đối và **dịch position**.
- `buffer.get(index)` / `buffer.put(index, value)`: truy cập theo index tuyệt đối và **không dịch position**.
- `buffer.mark()`: đặt mark = position hiện tại.
- `buffer.reset()`: đưa position trở lại mark.

**Các thao tác điều khiển vị trí không có nghĩa tự xóa dữ liệu.** Chúng thay đổi vùng đang được xem là hợp lệ cho lần đọc/ghi kế tiếp. Đối với `compact()`, còn có hành động **di chuyển dữ liệu còn lại** trong bộ nhớ.

### Ví dụ trạng thái khi nạp dữ liệu

Giả sử:

~~~java
ByteBuffer buf = ByteBuffer.allocate(8); // position=0, limit=8, capacity=8
buf.put(new byte[] {10, 20, 30, 40, 50}); // position=5, limit=8
buf.flip(); // position=0, limit=5: chuẩn bị đọc 5 byte hợp lệ
~~~

Sơ đồ sau `flip()`:

~~~text
index:      0    1    2    3    4   | 5    6    7
data:      10   20   30   40   50   | ?    ?    ?
           ^ position=0             ^ limit=5      capacity=8
usable:    [----- 5 byte có thể đọc -----)
~~~

Dấu `?` biểu thị các vị trí **ngoài vùng đọc hiện tại**. Mặc dù `ByteBuffer.allocate` cấp vùng bộ nhớ được khởi tạo ban đầu bằng 0, ta vẫn không nên coi những phần tử bên ngoài `limit` là dữ liệu của thông điệp.

## 3. `rewind()`: đọc lại từ đầu vùng hiện tại

**Quy tắc chính thức:**

- `position = 0`.
- **Giữ nguyên `limit` và `capacity`**.
- **Bỏ mark**, nếu trước đó đã đặt.
- **Không sao chép/xóa nội dung** trong buffer.

Ví dụ (đoạn minh họa, cần `import java.nio.ByteBuffer;`):

~~~java
ByteBuffer buf = ByteBuffer.allocate(6);
buf.put(new byte[] {10, 20, 30, 40});
buf.flip();  // p=0, l=4, c=6
buf.get();   // 10; p=1
buf.get();   // 20; p=2

buf.rewind(); // p=0, l=4, c=6
byte firstAgain = buf.get(); // 10; p=1
~~~

Sau `rewind()`, bốn byte `10, 20, 30, 40` vẫn có thể được đọc theo giới hạn `limit=4`. Phương thức này **không biến buffer thành vùng ghi trống**.

**Khi nên dùng:**

- Cần phân tích lại cùng payload từ đầu.
- Cần xuất/gửi lại toàn bộ vùng dữ liệu đã chuẩn bị, với điều kiện `limit` vẫn mô tả đúng độ dài payload.
- Cần so sánh hoặc duyệt lại các byte mà không phải tạo/copy một buffer khác.

**Khi không nên dùng:** khi một phần thông điệp đã xử lý xong và phần **chưa xử lý** cần giữ lại để nhận thêm dữ liệu. `rewind()` sẽ quay về index 0 và khiến các byte đã xử lý cũng được đọc lại. Lúc đó cân nhắc `compact()`.

### `rewind()` và `flip()` không giống nhau

Nếu `position=3, limit=8, capacity=8`:

- `rewind()` → `position=0, limit=8`.
- `flip()` → `position=0, limit=3`.

`flip()` chuyển từ giai đoạn ghi sang đọc bằng cách **lấy position cũ làm limit mới**. `rewind()` giữ nguyên limit đã có để **đọc lại** vùng hiện tại.

Một lỗi điển hình là gọi `rewind()` ngay sau khi `put` mà chưa `flip()`: do `limit` vẫn bằng capacity, chương trình có thể đọc vượt ra ngoài số byte payload thực tế vừa ghi.

## 4. `reset()`: quay lại mark đã đặt

`reset()` không có nghĩa là đưa buffer về trạng thái mới tạo và cũng **không** có nghĩa tự động về vị trí 0.

Quy tắc:

1. Gọi `mark()` để lưu `mark = position`.
2. Đọc hoặc dịch `position` về phía trước.
3. Gọi `reset()` để đặt **`position = mark`**.
4. `limit` và `capacity` **không đổi**.
5. **Mark vẫn còn hợp lệ sau `reset()`**, vì thế có thể `reset()` nhiều lần, miễn chưa bị thao tác khác hủy.

Ví dụ:

~~~java
ByteBuffer buf = ByteBuffer.wrap(new byte[] {10, 20, 30, 40});
buf.get();     // 10; p=1
buf.mark();    // mark=1
buf.get();     // 20; p=2
buf.get();     // 30; p=3
buf.reset();   // p=1
byte again = buf.get(); // 20; p=2
buf.reset();   // p=1, vì mark chưa bị hủy
~~~

Phân biệt rõ:

- `mark()` **chỉ ghi nhớ vị trí**, không copy dữ liệu tại vị trí ấy.
- `reset()` **chỉ đặt lại vị trí**, không phục hồi byte đã bị ghi đè.
- Nếu gọi `reset()` khi chưa có mark hợp lệ, Java ném `java.nio.InvalidMarkException` (**unchecked exception**).

### Những thao tác làm mark mất hiệu lực

`clear()`, `flip()`, `rewind()` và `compact()` **luôn hủy mark**. Ngoài ra, `position(newPosition)` hủy mark nếu `newPosition < mark`; `limit(newLimit)` hủy mark nếu `newLimit < mark`. Đặt mark mới bằng `mark()` sẽ thay mốc cũ.

Ví dụ:

~~~java
ByteBuffer buf = ByteBuffer.wrap(new byte[] {1, 2, 3, 4});
buf.position(2).mark(); // mark=2
buf.position(1);        // Dịch về trước mark -> hủy mark
buf.reset();            // Ném InvalidMarkException
~~~

Trái lại, đọc theo index tuyệt đối như `buf.get(0)` **không thay position**, nên tự nó không làm mất mark.

**Tình huống điển hình:** parser đọc thử header, đặt mark trước một trường có độ dài biến đổi; nếu chưa đủ byte hoặc nhánh xử lý không phù hợp thì `reset()` để đọc lại đoạn đó. Với parser phức tạp có nhiều điểm rollback, một mark duy nhất có thể không đủ; có thể lưu vị trí vào biến `int saved = buffer.position()` và khôi phục có kiểm soát bằng `position(saved)`, lưu ý giới hạn hiện tại và quy tắc hủy mark.

## 5. `compact()`: giữ dữ liệu chưa đọc, tạo chỗ để ghi tiếp

Khác hai phương thức trên, `compact()` **di chuyển các phần tử còn lại** từ đoạn `[position, limit)` về đầu buffer.

Gọi:

- `p = position` trước khi compact.
- `l = limit` trước khi compact.
- `n = l - p = remaining()`.

Sau `compact()` trên buffer có thể ghi:

1. Sao chép `n` phần tử của đoạn `[p, l)` vào đoạn `[0, n)` theo đúng thứ tự.
2. Đặt `position = n`, **không phải 0**.
3. Đặt `limit = capacity`.
4. Hủy mark.
5. Trả buffer về **trạng thái sẵn sàng `put` thêm** sau phần dữ liệu còn dở.

### Ví dụ nhìn thấy phần byte được giữ lại

~~~text
Trước compact:
capacity = 8, position = 2, limit = 5
index:       0    1  | 2    3    4  | 5    6    7
data:       10   20  |30   40   50  | ?    ?    ?
              đã đọc   ^----- chưa đọc -----^
remaining = 5 - 2 = 3

Sau compact:
capacity = 8, position = 3, limit = 8
index:       0    1    2  | 3    4    5    6    7
data:       30   40   50  | ... vùng dành để ghi mới ...
                            ^ position=3
~~~

Các vị trí ngoài ba phần tử vừa được giữ lại có thể vẫn chứa byte cũ; **`compact()` không hứa xóa sạch phần còn lại**.

Thực hành:

~~~java
ByteBuffer buf = ByteBuffer.allocate(8);
buf.put(new byte[] {10, 20, 30, 40, 50});
buf.flip();                  // p=0, l=5
buf.get();                   // 10; p=1
buf.get();                   // 20; p=2

buf.compact();               // còn [30,40,50]; p=3, l=8
buf.put(new byte[] {60, 70}); // p=5, l=8
buf.flip();                  // p=0, l=5

// Năm byte có thể đọc: 30, 40, 50, 60, 70.
~~~

**Chu trình chuẩn:** `flip() → xử lý được một phần → compact() → nạp tiếp → flip()`.

### Vì sao không `rewind()` sau `compact()`?

Ngay sau `compact()`, buffer đang ở **chế độ ghi**: `position = số byte chưa xử lý`, `limit = capacity`. Gọi `rewind()` lúc này sẽ đặt `position=0` nhưng **giữ `limit=capacity`**, nên có thể đọc cả byte cũ hoặc byte chưa phải một phần của payload. Khi muốn đọc phần dữ liệu đã có sau khi ghi bổ sung, dùng **`flip()`** để giới hạn đúng số byte hợp lệ.

### Nếu không còn byte chưa đọc thì sao?

Nếu trước `compact()` có `position == limit` thì `remaining=0`:

- Không còn phần tử nào được chuyển về đầu.
- Kết quả là `position=0, limit=capacity`.
- Có thể tiếp tục nạp dữ liệu mới, giống trạng thái sẵn sàng ghi của `clear()` về mặt các con trỏ.

Nhưng ý nghĩa hai thao tác vẫn khác nhau: `compact()` được dùng theo chính sách **bảo toàn phần chưa xử lý**; `clear()` tuyên bố bỏ vùng đọc hiện tại và sẵn sàng ghi mới. Cả hai đều không đảm bảo xóa byte cũ khỏi bộ nhớ.

### Buffer chỉ đọc và tính khả chuyển

`compact()` là thao tác **ghi/copy các phần tử** bên trong buffer; với `ByteBuffer` chỉ đọc (ví dụ từ `asReadOnlyBuffer()`), nó ném `ReadOnlyBufferException`. Trong khi đó `rewind()`, `mark()` và `reset()` vẫn có thể điều chỉnh vị trí của một buffer chỉ đọc.

Các buffer kiểu `ByteBuffer`, `CharBuffer` và các buffer nguyên thủy khác có thể hỗ trợ `compact()` theo phần tử của chúng. Vị trí và số lượng trong `CharBuffer` tính theo **char code unit**, không phải byte; trong `IntBuffer` tính theo số nguyên 32-bit. Đừng lấy đơn vị byte của `ByteBuffer` áp đặt cho mọi buffer.

## 6. Bảng so sánh trực tiếp

Giả sử trước thao tác: `position=p`, `limit=l`, `capacity=c` và có thể đã đặt mark.

| Phương thức | position sau gọi | limit sau gọi | Dữ liệu | Mark sau gọi | Sử dụng |
|---|---:|---:|---|---|---|
| `rewind()` | `0` | `l` (không đổi) | Không di chuyển/xóa | Bị hủy | Đọc lại từ đầu vùng hiện tại |
| `reset()` | `mark` cũ | `l` (không đổi) | Không di chuyển/xóa | **Giữ nguyên** | Quay lại mốc đã đánh dấu |
| `compact()` | `l - p` | `c` | **Dời dữ liệu chưa đọc về đầu** | Bị hủy | Giữ phần còn dở để ghi thêm |
| `flip()` *(đối chiếu)* | `0` | `p` cũ | Không di chuyển/xóa | Bị hủy | Chuyển ghi → đọc |
| `clear()` *(đối chiếu)* | `0` | `c` | Không xóa vật lý | Bị hủy | Chuẩn bị ghi mới, bỏ qua dữ liệu còn dở |

Nếu chưa từng `mark()`, `reset()` ném `InvalidMarkException`. `compact()` có thể ném `ReadOnlyBufferException` nếu buffer là read-only.

**Mẹo nhớ:**

- **re-wind** → đưa con trỏ về **đầu** để đọc lại;
- **re-set** → đưa con trỏ về **mốc mark**;
- **com-pact** → **nén phần chưa đọc về đầu**, đặt con trỏ sau phần được giữ lại để ghi tiếp.

## 7. Chương trình Java 17+ chạy được và stdout chính xác

Lưu mã sau thành **`BufferNavigationDemo.java`**. Chương trình tách hai buffer để theo dõi hai luồng: đọc lại bằng mark/rewind và xử lý phần còn dở bằng compact.

~~~java
import java.nio.ByteBuffer;
import java.nio.InvalidMarkException;
import java.util.StringJoiner;

public class BufferNavigationDemo {

    private static void show(String name, ByteBuffer b) {
        System.out.printf(
                "%s: p=%d, l=%d, c=%d, remaining=%d%n",
                name, b.position(), b.limit(),
                b.capacity(), b.remaining());
    }

    public static void main(String[] args) {
        // Phần 1: mark/reset và rewind.
        ByteBuffer a = ByteBuffer.allocate(6);
        a.put(new byte[] {10, 20, 30, 40});
        a.flip();

        show("after flip", a);
        System.out.println("first=" + Byte.toUnsignedInt(a.get()));

        a.mark(); // mark = 1
        System.out.println("next=" + Byte.toUnsignedInt(a.get()));
        System.out.println("another=" + Byte.toUnsignedInt(a.get()));

        a.reset(); // Quay về mark = 1.
        show("after reset", a);
        System.out.println("again=" + Byte.toUnsignedInt(a.get()));

        a.rewind(); // Về position = 0, hủy mark.
        show("after rewind", a);
        System.out.println("replay=" + Byte.toUnsignedInt(a.get()));

        try {
            a.reset(); // Mark đã bị rewind() hủy.
        } catch (InvalidMarkException e) {
            System.out.println("reset after rewind: InvalidMarkException");
        }

        // Phần 2: giữ byte chưa xử lý rồi ghi tiếp.
        ByteBuffer b = ByteBuffer.allocate(8);
        b.put(new byte[] {10, 20, 30, 40, 50});
        b.flip();

        System.out.println("consumed="
                + Byte.toUnsignedInt(b.get()) + ","
                + Byte.toUnsignedInt(b.get()));
        show("before compact", b);

        b.compact(); // Giữ 30,40,50 ở đầu; p=3, l=8.
        show("after compact", b);
        b.put(new byte[] {60, 70});
        b.flip();
        show("after next flip", b);

        StringJoiner values = new StringJoiner(",");
        while (b.hasRemaining()) {
            values.add(Integer.toString(Byte.toUnsignedInt(b.get())));
        }
        System.out.println("recovered=" + values);
    }
}
~~~

Chạy từ thư mục chứa file nguồn:

~~~bash
javac BufferNavigationDemo.java
java BufferNavigationDemo
~~~

**Stdout:**

~~~text
after flip: p=0, l=4, c=6, remaining=4
first=10
next=20
another=30
after reset: p=1, l=4, c=6, remaining=3
again=20
after rewind: p=0, l=4, c=6, remaining=4
replay=10
reset after rewind: InvalidMarkException
consumed=10,20
before compact: p=2, l=5, c=8, remaining=3
after compact: p=3, l=8, c=8, remaining=5
after next flip: p=0, l=5, c=8, remaining=5
recovered=30,40,50,60,70
~~~

**Giải thích kết quả:**

1. Buffer `a` có 4 byte đã nạp nên sau `flip()`: `p=0, l=4, c=6`. `get()` trả 10 rồi dịch con trỏ đến 1.
2. `mark()` đặt mốc 1. Hai lần đọc tiếp nhận 20, 30. `reset()` phục hồi `p=1`, vì thế lần đọc tiếp lại nhận 20.
3. `rewind()` đặt `p=0`, vẫn giữ `l=4`; đọc lần nữa được 10. Vì mark đã bị xóa, `reset()` sau đó ném `InvalidMarkException`.
4. Buffer `b` đã xử lý 10 và 20, còn đúng ba byte `30,40,50`. `compact()` chuyển ba byte đó đến đầu và đặt `p=3, l=8`.
5. Thêm 60 và 70 tại hai vị trí kế tiếp làm `p=5`; `flip()` đặt `p=0, l=5`. Kết quả đọc là **30,40,50,60,70**, không có hai byte đã xử lý.

## 8. Ví dụ ứng dụng: đọc bản ghi không trọn vẹn từ FileChannel

Giả sử giao thức truyền dữ liệu gồm các bản ghi **mỗi bản ghi đúng 4 byte**, được giải mã bằng `getInt()`. Một lần `FileChannel.read(buffer)` có thể điền vào buffer một số byte không đủ bội số của 4. Khi đó phần dư phải được giữ cho lượt đọc tiếp theo.

Ví dụ sau là **đoạn minh họa** nằm trong phương thức xử lý; `input` là `FileChannel` đang mở để đọc từ một nguồn tệp, và đoạn xử lý phải xử lý ngoại lệ I/O:

~~~java
ByteBuffer buffer = ByteBuffer.allocate(1024);

while (input.read(buffer) != -1) {
    buffer.flip(); // Chuyển phần vừa nạp + phần còn dư sang chế độ đọc.

    while (buffer.remaining() >= Integer.BYTES) {
        int value = buffer.getInt(); // Xử lý đúng một bản ghi 4 byte.
        consume(value);              // Hàm ứng dụng định nghĩa.
    }

    // Nếu còn 1..3 byte, giữ chúng và dành chỗ cho lần đọc tiếp.
    buffer.compact();
}

buffer.flip(); // Kiểm tra số byte còn dư sau EOF.
if (buffer.hasRemaining()) {
    throw new java.io.EOFException("Ban ghi 4 byte cuoi bi thieu");
}
~~~

Vì sao dùng `compact()` ở đây?

- Ví dụ một lượt đọc tạo ra 6 byte: parser xử lý 4 byte đầu, còn 2 byte trong vùng `[position, limit)`.
- `compact()` mang 2 byte này về index 0 và đặt `position=2`; lần đọc tiếp của channel bổ sung byte từ index 2.
- Khi có thêm ít nhất 2 byte, `flip()` cho parser đọc tiếp một bản ghi 4 byte hoàn chỉnh.
- Nếu thay bằng `clear()`, phần dư 2 byte bị coi là không còn cần thiết và dễ bị ghi đè; nếu thay bằng `rewind()`, parser có thể đọc lại 4 byte đã xử lý.
- Nếu gặp EOF mà vẫn còn 1..3 byte, dữ liệu cuối **không đủ một bản ghi**; ví dụ chủ động báo lỗi thay vì âm thầm bỏ dữ liệu.

**Lưu ý cho I/O phi chặn (non-blocking):** lượt đọc có thể trả `0` dù chưa EOF; cần tích hợp với cơ chế readiness/selector hoặc kế hoạch chờ phù hợp, tránh vòng lặp bận. Ví dụ trên minh họa cách dùng buffer với `FileChannel` đọc tuần tự; nó không phải bộ giải mã mạng đa luồng hoàn chỉnh. Nếu tệp có byte order khác big-endian mặc định của `ByteBuffer`, phải thiết lập `buffer.order(ByteOrder.LITTLE_ENDIAN)` hoặc theo giao thức tương ứng.

### Một trường hợp gần giống: ghi một phần ra channel

Khi `SocketChannel.write(buffer)` chỉ tiêu thụ **một phần byte còn lại**, buffer vẫn giữ `position` tại điểm chưa gửi. Nếu cần **nối thêm payload** vào cùng buffer trước lượt ghi tiếp, `compact()` đưa các byte chưa gửi về đầu, rồi chương trình có thể `put(...)` phần mới và `flip()` để chuẩn bị gửi lại.

Trường hợp chỉ cần tiếp tục gửi những byte hiện có mà **không thêm dữ liệu**, thường không cần compact: cứ tiếp tục gọi `write(buffer)` theo chính sách readiness đến khi `hasRemaining()==false`. Không dùng `rewind()` vì nó có thể khiến gửi lặp các byte đã được channel tiêu thụ.

## 9. Lỗi hay gặp, nguyên nhân và cách khắc phục

| Triệu chứng | Nguyên nhân thường gặp | Cách sửa |
|---|---|---|
| Đọc lại cả byte đã xử lý | Dùng `rewind()` thay vì `compact()` sau một lần parse dở | Chỉ compact phần còn lại rồi nạp tiếp, `flip()` trước khi parse lần sau |
| `InvalidMarkException` | Chưa gọi `mark()` hoặc mark bị `flip/clear/rewind/compact` hủy | Đặt mark ngay trước vùng cần thử đọc; theo dõi vòng đời mark |
| `reset()` không về đầu buffer | Mark đã được đặt ở vị trí khác 0 | Dùng `rewind()` nếu thật sự muốn về 0 |
| `rewind()` đọc ra giá trị thừa sau ghi | `limit` còn bằng capacity, chưa `flip()` | `flip()` sau giai đoạn `put` để giới hạn vùng đã ghi |
| `compact()` xong đọc ngay thấy dữ liệu thừa | Compact để buffer trong **trạng thái ghi** với `limit=capacity` | Nạp tiếp khi cần, rồi `flip()` trước khi đọc |
| `BufferUnderflowException` | Đọc nhiều hơn `remaining()` | Kiểm tra đủ số byte trước khi giải mã; compact phần thiếu để nạp tiếp |
| `BufferOverflowException` | Không còn chỗ cho các lệnh `put` | Kiểm tra `remaining()`, xử lý bớt dữ liệu hoặc cấp buffer phù hợp |
| `ReadOnlyBufferException` khi compact | Buffer không cho phép ghi | Dùng buffer writable hoặc copy dữ liệu vào buffer writable mới |
| Thay đổi nội dung sau `reset()` không khôi phục được | Mark chỉ lưu **vị trí** | Nếu cần rollback giá trị, phải quản lý bản sao dữ liệu/transaction riêng |
| Sau `compact()` còn byte cũ trong backing array | Compact không xóa vùng dư | Xem dữ liệu hợp lệ theo `position/limit`, không coi toàn bộ backing array là payload |

## 10. Cách chọn trong tình huống thực tế

- **Cần đọc lại toàn bộ vùng hiện hành:** `rewind()`. Điều kiện: `limit` đã biểu diễn đúng ranh giới đọc.
- **Cần đọc thử và quay lại một điểm cụ thể:** `mark()` rồi `reset()`. Điều kiện: mark còn hợp lệ; không mong mark tự lưu byte cũ.
- **Cần giữ phần chưa parse/chưa gửi để nạp thêm:** `compact()`, tiếp tục `put` hoặc `channel.read(buffer)`, rồi `flip()`.
- **Vừa ghi vào buffer xong và muốn đọc:** `flip()`.
- **Muốn bỏ toàn bộ vùng chưa xử lý để nạp từ đầu:** `clear()`.
- **Cần di chuyển ngẫu nhiên đến một offset bất kỳ:** kiểm tra phạm vi rồi cân nhắc `position(index)`, không dùng `reset()` nếu chưa có mark.

### Tự kiểm tra nhanh

1. `position=4, limit=7, capacity=10`. Sau `rewind()`? **`position=0, limit=7, capacity=10`**.
2. Cùng trạng thái trên, sau `compact()`? **Di chuyển 3 phần tử còn lại về đầu, `position=3, limit=10, capacity=10`**.
3. `mark()` tại 2, đọc đến 5, rồi `reset()`? **`position=2`**, limit không đổi.
4. Gọi `rewind()` rồi `reset()` mà không `mark()` lại? **`InvalidMarkException`**.
5. Sau `compact()` có `position=3, limit=8`; muốn đọc đúng ba byte còn giữ lại và chưa ghi thêm thì gọi gì? **`flip()`** để thành `position=0, limit=3`.
6. Parser đã xử lý 7 byte trong 10 byte hợp lệ, còn 3 byte chưa đủ một bản ghi. Nên dùng gì trước lần nạp tiếp? **`compact()`**, không phải `rewind()`.
7. `reset()` có xóa mark không? **Không.** Nhưng `clear()`, `flip()`, `rewind()`, `compact()` có.
8. `compact()` có đảm bảo ghi số 0 vào những ô nhớ trống không? **Không.** Nội dung ngoài vùng payload cần được coi là không có ý nghĩa.

## 11. Tài liệu chính thức

- [Java SE 17 — `Buffer`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/Buffer.html): `position`, `limit`, `capacity`, `mark`, `reset`, `rewind`, `clear`, `flip` và bất biến của buffer.
- [Java SE 17 — `ByteBuffer`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html): `compact`, thao tác `get/put`, các view và các giới hạn read-only.
- [Java SE 17 — `FileChannel`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html): đọc/ghi buffer qua channel.
- [Java SE 17 — `InvalidMarkException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/InvalidMarkException.html): lỗi khi `reset()` không có mark hợp lệ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-buffer-006">get, put tương đối và tuyệt đối</a>

<details>
<summary>Xem chi tiết</summary>

# NIO Buffer: get và put tương đối, tuyệt đối trong Java

Trong `java.nio`, **Buffer** là một vùng dữ liệu có trạng thái đọc/ghi: `capacity`, `limit` và `position`. Hai nhóm thao tác cốt lõi là **tương đối (relative)** và **tuyệt đối (absolute)**:

- `get()` và `put(value)` **tương đối** thao tác tại vị trí `position` hiện tại, sau đó **tăng `position`**.
- `get(index)` và `put(index, value)` **tuyệt đối** thao tác trực tiếp tại `index`, **không thay đổi `position`**.

Đây là sự khác nhau nền tảng để đọc lần lượt, ghi tuần tự, nhìn trước dữ liệu (peek), sửa một phần đã biết vị trí và xây parser nhị phân. “Tuyệt đối” ở đây là **chỉ số bên trong buffer**, không phải địa chỉ RAM, byte offset của file hay vị trí của `FileChannel`.

## 1. Nắm chắc trạng thái Buffer trước khi dùng get/put

Mọi `Buffer` đều có ba thuộc tính cốt lõi:

| Thuộc tính | Ý nghĩa |
| --- | --- |
| `capacity()` | Tổng số phần tử mà buffer có thể chứa; không đổi trong suốt vòng đời buffer đó. |
| `limit()` | Ranh giới **độc quyền** (exclusive) của vùng đang được phép đọc hoặc ghi. |
| `position()` | Chỉ số phần tử tiếp theo cho thao tác **tương đối**. |

Bất biến:

```text
0 <= position <= limit <= capacity
remaining = limit - position
```

Các phần tử hợp lệ cho lệnh truy cập tuyệt đối một phần tử có chỉ số **`0` đến `limit - 1`**, không phải cứ nhỏ hơn `capacity` là được. Nếu `limit = 3` và `capacity = 8` thì `get(3)` vẫn vượt biên; bản thân capacity không cho phép vượt limit.

Ví dụ buffer dung lượng 8, đã ghi ba byte theo cách tương đối:

```text
Index:       0    1    2    3    4    5    6    7
Content:    10   20   30    ?    ?    ?    ?    ?
                             ^
position = 3, limit = 8, capacity = 8
```

Khi gọi `flip()`, trạng thái chuyển thành `position = 0, limit = 3, capacity = 8`. Lúc này có ba phần tử đã được ghi và có thể đọc bằng các thao tác tương đối, trong khi `get(3)` hoặc `put(3, value)` tuyệt đối bị từ chối vì `index >= limit`.

**Vùng byte đã được cấp phát không đồng nghĩa dữ liệu hợp lệ.** `ByteBuffer.allocate` khởi tạo bộ nhớ bằng 0, nhưng chương trình vẫn phải xác định phần nào đã được ghi và quản lý `position/limit` cho đúng.

## 2. get/put tương đối: đọc ghi và dịch con trỏ

Các ví dụ sau dùng `ByteBuffer`, với đơn vị chỉ số là **byte**:

```java
ByteBuffer b = ByteBuffer.allocate(5);    // position=0, limit=5
b.put((byte) 10);                         // ghi index 0; position=1
b.put((byte) 20);                         // ghi index 1; position=2
b.put((byte) 30);                         // ghi index 2; position=3
b.flip();                                 // position=0, limit=3
byte a = b.get();                         // a=10; position=1
byte c = b.get();                         // c=20; position=2
```

Đây là snippet minh họa, cần `import java.nio.ByteBuffer;` và đặt trong một phương thức Java hợp lệ.

Với `get()`, buffer cần tối thiểu một phần tử còn lại (`position < limit`). Với `put(value)`, cũng cần còn chỗ trong vùng ghi của buffer. Nếu không:

- `get()` khi `remaining() == 0` ném **`BufferUnderflowException`**.
- `put(value)` khi `remaining() == 0` ném **`BufferOverflowException`**, trừ các ràng buộc khác như buffer chỉ đọc.
- `ByteBuffer.put` trả lại chính buffer để có thể chain `put(...).put(...)`.
- Với `byte`, kết quả `get()` là kiểu có dấu (`-128..127`). Nếu muốn số 0..255, dùng `b.get() & 0xFF`.

Không cần tự gọi `position(position() + 1)` sau một `get/put` tương đối: API đã tiến position. Tự cộng thêm sẽ bỏ sót phần tử.

## 3. get/put tuyệt đối: truy cập index mà giữ nguyên position

Với một `ByteBuffer b` đang mở và ở trạng thái hợp lệ:

```java
byte first = b.get(0);      // Đọc phần tử số 0, position không đổi.
b.put(1, (byte) 99);       // Ghi đè phần tử số 1, position không đổi.
```

Điểm cần nhớ:

1. Chỉ số là **zero-based**, chỉ số âm hoặc `index >= limit` gây **`IndexOutOfBoundsException`** đối với thao tác một phần tử.
2. `get(index)` không tiêu thụ dữ liệu ở position hiện tại. Ví dụ `position = 2`, gọi `get(0)` vẫn để position = 2.
3. `put(index, value)` không ghi thêm một phần tử vào cuối phần “đã xử lý”; nó **ghi đè** phần tử tại index cho trước. Nếu buffer có khả năng ghi và index hợp lệ, dữ liệu cũ tại đó thay đổi.
4. `get(index)` vẫn có thể đọc dù `position == limit`, miễn index nằm trong vùng `[0, limit)`. Vì thao tác tuyệt đối không cần lấy dữ liệu từ position hiện tại.
5. `put(index, value)` không di chuyển `position`, không thay `limit`, và không cập nhật “số byte đã ghi hợp lệ” để `flip()` tự biết.

Điểm 5 dễ gây lỗi nhất:

```java
ByteBuffer b = ByteBuffer.allocate(4); // position=0, limit=4
b.put(2, (byte) 7);                    // position vẫn 0
b.flip();                              // limit trở thành 0!
System.out.println(b.remaining());    // 0
```

Ví dụ cho thấy **`flip()` sử dụng position cũ làm limit**, không tự quét vùng nhớ để phát hiện mọi vị trí đã được ghi bằng `put(index,...)`. Nếu chủ đích lưu một cấu trúc cố định, hãy quản lý vùng hợp lệ rõ ràng (chẳng hạn ghi tuần tự trước, rồi dùng absolute put để vá header) và chỉ `flip` sau khi position phản ánh lượng dữ liệu đã chuẩn bị.

## 4. Ví dụ Java 17+ chạy độc lập: so sánh trực tiếp

Lưu thành **`RelativeAbsoluteBufferDemo.java`**:

```java
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

public class RelativeAbsoluteBufferDemo {
    private static String state(ByteBuffer b) {
        return "pos=" + b.position()
                + " limit=" + b.limit()
                + " capacity=" + b.capacity();
    }

    public static void main(String[] args) {
        ByteBuffer b = ByteBuffer.allocate(6);
        System.out.println("initial: " + state(b));

        // Relative put: tăng position mỗi lần ghi.
        b.put((byte) 10).put((byte) 20).put((byte) 30);
        System.out.println("relative put: " + state(b));

        // Absolute put: ghi đè index 1; position không đổi.
        b.put(1, (byte) 99);
        System.out.println("absolute put: " + state(b));

        // Chuyển từ ghi sang đọc 3 byte đã chuẩn bị.
        b.flip();
        System.out.println("flip: " + state(b));

        // Absolute get: nhìn trước byte thứ hai mà không tiêu thụ.
        System.out.println("peek get(1): " + b.get(1));
        System.out.println("after peek: " + state(b));

        // Relative get: đọc và tăng position.
        System.out.println("consume get(): " + b.get());
        System.out.println("after consume: " + state(b));
        System.out.println("remaining=" + b.remaining());

        // Quay về đọc lại toàn bộ vùng [0, limit).
        b.rewind();
        System.out.println("rewind: " + state(b));
        System.out.println(
                "values=" + b.get() + "," + b.get() + "," + b.get());

        try {
            b.get(); // position == limit, không còn byte để đọc.
        } catch (BufferUnderflowException e) {
            System.out.println("read past limit: BufferUnderflowException");
        }

        b.clear(); // Chỉ reset trạng thái, không xóa byte trong bộ nhớ.
        System.out.println("clear: " + state(b));
        try {
            b.get(6); // index bằng limit là không hợp lệ.
        } catch (IndexOutOfBoundsException e) {
            System.out.println("get(6): IndexOutOfBoundsException");
        }
    }
}
```

Biên dịch và chạy (Java 17 trở lên):

```bash
javac RelativeAbsoluteBufferDemo.java
java RelativeAbsoluteBufferDemo
```

Stdout:

```text
initial: pos=0 limit=6 capacity=6
relative put: pos=3 limit=6 capacity=6
absolute put: pos=3 limit=6 capacity=6
flip: pos=0 limit=3 capacity=6
peek get(1): 99
after peek: pos=0 limit=3 capacity=6
consume get(): 10
after consume: pos=1 limit=3 capacity=6
remaining=2
rewind: pos=0 limit=3 capacity=6
values=10,99,30
read past limit: BufferUnderflowException
clear: pos=0 limit=6 capacity=6
get(6): IndexOutOfBoundsException
```

Diễn giải:

- Ba `put` tương đối ghi lần lượt vào index 0, 1, 2; `position` tiến tới 3.
- `put(1, 99)` đổi byte 20 thành 99, nhưng `position` vẫn là 3; nhờ vậy `flip` giữ đúng `limit = 3`.
- Sau `flip`, `get(1)` trả về 99 mà `position` vẫn bằng 0; `get()` đọc 10 và tăng lên 1.
- `rewind` giữ limit 3 và đặt lại position 0, cho phép đọc lại `10,99,30`.
- Đọc tương đối qua limit gây underflow; đọc tuyệt đối tại index bằng limit gây `IndexOutOfBoundsException`. Đây là **hai lỗi khác nhau**.
- `clear` đưa trạng thái về `position=0, limit=capacity`; không xóa nội dung mảng nhớ, nên không được xem như cách làm sạch dữ liệu nhạy cảm.

## 5. Bảng so sánh chính xác các thao tác

| Nhóm | API `ByteBuffer` | Vị trí đọc/ghi | Sau khi thành công | Vượt biên điển hình |
| --- | --- | --- | --- | --- |
| Relative get | `get()` | `position` hiện tại | `position += 1` | `BufferUnderflowException` |
| Relative put | `put(byte)` | `position` hiện tại | `position += 1` | `BufferOverflowException` |
| Absolute get | `get(int index)` | `index` chỉ định | `position` không đổi | `IndexOutOfBoundsException` |
| Absolute put | `put(int index, byte)` | `index` chỉ định | `position` không đổi | `IndexOutOfBoundsException` |
| Relative typed get | `getInt()` | 4 byte từ `position` | `position += 4` | `BufferUnderflowException` |
| Absolute typed get | `getInt(index)` | 4 byte từ `index` | `position` không đổi | `IndexOutOfBoundsException` |
| Relative typed put | `putInt(value)` | 4 byte từ `position` | `position += 4` | `BufferOverflowException` |
| Absolute typed put | `putInt(index, value)` | 4 byte từ `index` | `position` không đổi | `IndexOutOfBoundsException` |

`getInt(index)` và `putInt(index, value)` cần **cả bốn byte** nằm trước limit, tức `index >= 0` và `index + 4 <= limit`. Tương tự `getLong/putLong` cần 8 byte. Đối với các lệnh ghi, buffer chỉ đọc còn có thể ném `ReadOnlyBufferException`.

Bảng dùng `ByteBuffer` để minh họa; `IntBuffer`, `LongBuffer`, `CharBuffer`, `ShortBuffer`, `FloatBuffer`, `DoubleBuffer` cũng có get/put tương đối và tuyệt đối theo **đơn vị phần tử riêng**. Ví dụ `IntBuffer.get(2)` nghĩa là phần tử `int` thứ ba, không phải byte offset 2.

## 6. Hiểu limit, flip, clear, rewind và compact

Các hàm này **quản lý trạng thái**, không tự chuyển dữ liệu sang một buffer mới:

| Phương thức | Position sau lệnh | Limit sau lệnh | Mục đích |
| --- | --- | --- | --- |
| `flip()` | 0 | Position **trước** lệnh | Chuyển dữ liệu vừa ghi tuần tự sang chế độ đọc. |
| `clear()` | 0 | Capacity | Chuẩn bị ghi từ đầu; **không** xóa các byte cũ. |
| `rewind()` | 0 | Giữ nguyên limit | Đọc lại vùng hiện tại từ đầu. |
| `compact()` | Số phần tử còn lại trước lệnh | Capacity | Dồn phần chưa đọc về đầu rồi chuẩn bị ghi tiếp. |
| `position(n)` | n | Giữ nguyên limit | Đặt con trỏ tương đối trong khoảng hợp lệ. |
| `limit(n)` | Có thể bị hạ xuống n | n | Đổi vùng hợp lệ; position không được lớn hơn limit. |

Ví dụ vòng xử lý khi dữ liệu đến từng đợt:

```text
GHI:
position = số byte đã được đặt vào buffer, limit = capacity
      |
      | flip()
      v
ĐỌC:
position = số byte đã tiêu thụ, limit = số byte có dữ liệu
      |
      | compact() nếu vẫn còn byte chưa đủ tạo một thông điệp
      v
GHI TIẾP:
các byte chưa đọc nằm đầu buffer
position = số byte giữ lại, limit = capacity
```

Một workflow thông thường: `channel.read(buffer)` ghi vào vị trí hiện tại → `buffer.flip()` → xử lý bằng `buffer.get()` → `buffer.compact()` để giữ phần còn dang dở; hoặc `clear()` khi chắc chắn đã tiêu thụ hết. Khi kết thúc luồng, `flip()` và xử lý phần còn lại theo quy tắc giao thức. Nếu data gồm số nguyên 4 byte hoặc chuỗi UTF-8 nhiều byte, không được mặc định mỗi đợt đọc cung cấp đủ một đơn vị; parser cần giữ phần chưa đủ.

**Lưu ý:** `compact()` có thể sao chép bytes giữa các vị trí; mọi absolute index đã lưu từ trước có thể không còn trỏ đúng thông điệp. Tính lại vị trí và giữ lập luận về state.

## 7. Absolute get thường dùng để peek header và sửa byte tại offset

Một parser nhị phân có thể muốn nhìn trước header mà chưa tiêu thụ con trỏ:

```java
// Snippet: giả sử b là ByteBuffer đã flip và còn >= 4 byte.
if (b.remaining() >= Integer.BYTES) {
    int magic = b.getInt(b.position()); // peek: position không đổi
    if (magic == 0x01020304) {
        int consumed = b.getInt();     // relative: tiến thêm 4 byte
        // consumed == magic
    }
}
```

`ByteBuffer` mặc định dùng `ByteOrder.BIG_ENDIAN`. Nếu protocol yêu cầu little-endian, thiết lập `b.order(ByteOrder.LITTLE_ENDIAN)` và phải thống nhất giữa bên ghi và bên đọc. Cả `getInt()` và `getInt(index)` dùng byte order của buffer đó; tính chất relative/absolute **không** quyết định byte order.

Một kỹ thuật thường gặp là để trống header độ dài, ghi payload, rồi quay lại điền header bằng absolute put. Cách này giúp giữ position tại cuối payload:

```java
import java.nio.ByteBuffer;

ByteBuffer b = ByteBuffer.allocate(64);
b.putInt(0);                 // Reserve 4 bytes for length; position=4.
int start = b.position();   // start=4
b.put(new byte[] {7, 8, 9});
int length = b.position() - start; // 3
b.putInt(0, length);        // Patch header; position vẫn = 7.
b.flip();                   // limit=7; sẵn sàng đọc.
int payloadLength = b.getInt(); // 3; position=4.
```

Đây là **snippet** dùng import và các biến trong ngữ cảnh; nó cho thấy cách dùng relative put để xác định phần dữ liệu hợp lệ, sau đó dùng absolute put để ghi đè header. Tuy nhiên, nó không tự tạo framing protocol hoàn chỉnh: bên nhận còn phải kiểm tra `length >= 0`, hạn mức, số byte thực có sẵn trước khi đọc payload.

Chỉ số tuyệt đối thuộc buffer **đang được dùng**. Nếu buffer là slice bắt đầu giữa một buffer khác, `index=0` của slice tương ứng với phần tử đầu slice, chứ không nhất thiết là phần tử số 0 của buffer gốc.

## 8. Bulk get/put: thao tác một nhóm phần tử

Với Java 17+, `ByteBuffer` hỗ trợ cả phương thức bulk tương đối và bulk tuyệt đối:

```java
ByteBuffer b = ByteBuffer.allocate(5);
b.put(new byte[] {11, 22, 33});   // relative: position=3
b.flip();                         // position=0, limit=3

byte[] firstTwo = new byte[2];
b.get(firstTwo);                  // relative: position=2

byte[] peek = new byte[2];
b.get(0, peek);                   // absolute bulk: position vẫn 2
```

`get(byte[])` đọc số phần tử tương ứng độ dài array từ position và tăng position; nếu `remaining()` không đủ sẽ ném underflow. `get(int index, byte[] dst)` đọc từ index cố định, không thay đổi position. Biến thể có offset/length như `get(index, dst, offset, length)` cho phép chọn một lát của array. Các hàm bulk absolute của `ByteBuffer` được hỗ trợ trong Java 17; khi xây thư viện cần tương thích JDK cũ hơn, kiểm tra API version.

Tương tự, `put(byte[])` là ghi bulk tương đối, còn `put(index, src)` là ghi bulk tuyệt đối. Không nhầm **offset trong array nguồn/đích** với **index trong buffer**: đó là hai hệ tọa độ khác nhau.

Nếu cần xử lý byte array rất lớn, tránh đọc tất cả vào RAM; làm việc theo khối, phối hợp `remaining()` và kiểm tra giới hạn định dạng.

## 9. Read-only, duplicate, slice và các vùng nhớ dùng chung

`duplicate()` tạo một buffer có **trạng thái position/limit/mark độc lập** nhưng cùng vùng chứa dữ liệu. `slice()` tạo view bắt đầu tại position hiện tại với capacity bằng `remaining()` tại thời điểm tạo; nó cũng chia sẻ dữ liệu nhưng các chỉ số trong slice tính từ 0.

```java
ByteBuffer original = ByteBuffer.wrap(new byte[] {10, 20, 30, 40});
original.position(1); // original: position=1, limit=4

ByteBuffer duplicate = original.duplicate();
ByteBuffer slice = original.slice(); // slice index 0 -> original index 1

slice.put(0, (byte) 99); // Ghi đè original index 1, không đổi position.
System.out.println(original.get(1));  // 99
System.out.println(duplicate.position()); // 1
System.out.println(slice.position());     // 0
```

Đây là snippet; cần `import java.nio.ByteBuffer;`. Sửa nội dung qua slice hay duplicate có thể thấy từ các view khác, nhưng cursor được quản lý độc lập. Điều này đặc biệt quan trọng khi dùng absolute index: index gắn với **view**, không phải vị trí trong file hay tọa độ toàn cục.

`asReadOnlyBuffer()` trả về view chỉ đọc có thể `get` nhưng không thể `put`, dù lệnh ghi là relative hay absolute. Nội dung của read-only view vẫn có thể đổi nếu buffer gốc có quyền ghi chia sẻ cùng vùng nhớ; đây không phải ảnh chụp bất biến.

Không dùng `duplicate` như giải pháp an toàn cho luồng đồng thời: vị trí riêng không làm cho dữ liệu chia sẻ có transaction hoặc synchronization. Đặt chính sách đồng bộ và sở hữu bộ nhớ khi có nhiều thread.

## 10. Những lỗi phổ biến phải phân biệt

| Tình huống | Hành vi / exception | Cách xử lý |
| --- | --- | --- |
| `get()` sau khi `position == limit` | `BufferUnderflowException` | Dùng `hasRemaining()`, `remaining()` hoặc kiểm tra đủ độ dài trước khi đọc. |
| `put(value)` khi hết chỗ | `BufferOverflowException` | Đảm bảo buffer ở pha ghi với dung lượng đủ, xử lý/flush phần cũ. |
| `get(index)` với `index < 0` hoặc `index >= limit` | `IndexOutOfBoundsException` | Kiểm tra theo limit, không chỉ capacity. |
| `put(index, value)` ngoài limit | `IndexOutOfBoundsException` | Không dùng index ngoài vùng hợp lệ, điều chỉnh state có chủ đích. |
| `put` trên read-only buffer | `ReadOnlyBufferException` | Dùng buffer ghi được; không sửa view chỉ đọc. |
| `getInt()` khi chỉ còn 2 byte | `BufferUnderflowException` | Cần `remaining() >= 4` trước khi đọc. |
| `getInt(index)` không đủ 4 byte tới limit | `IndexOutOfBoundsException` | Kiểm tra cả span `[index, index+4)`. |
| `put(index, ...)` rồi `flip()` thấy limit 0 | Position không đổi sau ghi tuyệt đối. | Xác định vùng đã chuẩn bị bằng ghi relative hoặc chủ động thiết lập position trước flip. |
| `clear()` rồi đọc thấy byte cũ | `clear` reset metadata, không xóa nội dung. | Không dùng `clear` như phép wipe hoặc chứng nhận dữ liệu mới. |
| `slice().get(0)` khác `original.get(0)` | Slice có tọa độ index từ đầu view. | Tính lại index theo offset slice. |
| Đọc ra số sai thứ tự byte | Byte order không thống nhất. | Dùng `order(...)` theo schema, kiểm tra dữ liệu mẫu. |

Một lỗi tư duy tinh vi: **position không phải địa chỉ lưu trữ duy nhất của dữ liệu**. Buffer có thể chứa nhiều byte phía trước hoặc sau position, nhưng `limit` mới xác định vùng thao tác hợp lệ; ứng dụng vẫn phải theo dõi byte nào thật sự đại diện cho một trường dữ liệu đã hoàn chỉnh.

## 11. Khi nào dùng tương đối và khi nào dùng tuyệt đối?

**Relative get/put** phù hợp khi:

- Đọc từng phần tử theo thứ tự một stream đã có `flip`.
- Ghi tuần tự bản tin, tệp nhị phân hoặc các khối dữ liệu.
- Tiêu thụ byte trong vòng `while (b.hasRemaining())`.
- Muốn ứng dụng dựa trên `position` để biết đã xử lý đến đâu.

**Absolute get/put** phù hợp khi:

- Peek một header, magic number, length, checksum hoặc tag trước khi quyết định tiêu thụ.
- Cập nhật một trường tại index xác định sau khi đã ghi payload.
- Đọc lại một phần tử cũ mà không làm thay đổi vòng xử lý tương đối.
- Thao tác theo bảng offset của một cấu trúc nhị phân đã được xác thực.

Không nên sử dụng absolute put để thay cho toàn bộ vòng ghi tuần tự nếu vẫn định dùng `flip()` mà không quản lý `position`. Cũng đừng cố thực hiện mọi phép đọc bằng cách tự tăng index khi vòng duyệt tương đối đã diễn đạt đúng ý định.

## 12. Bài tập tự luyện và kiểm chứng

**Bài tập 1 — Đọc trạng thái chính xác.** Khởi tạo `ByteBuffer.allocate(4)`, gọi lần lượt `put((byte)5)`, `put(0,(byte)9)`, `flip()`, `get(0)`, `get()`. Hãy ghi position/limit sau mỗi lệnh. Đáp án: `(1,4)` → `(1,4)` → `(0,1)` → `(0,1)` → `(1,1)`; hai `get` cuối đều trả 9.

**Bài tập 2 — Header length.** Mở rộng snippet vá header: ghi 4-byte length, payload 5 byte, dùng absolute `getInt(0)` để kiểm tra length **mà không đổi position**, rồi đọc bằng `getInt()`. Tại sao hai thao tác trả cùng giá trị nhưng state sau lệnh khác nhau?

**Bài tập 3 — Chẩn đoán ngoại lệ.** Với `ByteBuffer.allocate(4)` đã ghi 2 byte rồi `flip()`, thử `get(2)` và thử gọi `get()` ba lần. Dự đoán chính xác `IndexOutOfBoundsException` so với `BufferUnderflowException`.

**Bài tập 4 — Slice và con trỏ riêng.** Dùng `wrap(new byte[]{1,2,3,4})`, đặt `position(2)`, tạo slice, gọi `slice.put(0,(byte)8)`. Quan sát `original.get(2)` và position của original/slice. Giải thích vì sao hai vị trí độc lập nhưng nội dung đổi chung.

**Bài tập 5 — Byte order.** Tạo `ByteBuffer.allocate(4)`, ghi `putInt(0x01020304)`, `flip()` và đọc lại bằng `getInt()`. Thử đọc từng byte bằng absolute `get(index)`; sau đó áp dụng `order(ByteOrder.LITTLE_ENDIAN)` **trước khi ghi** một buffer mới và so sánh thứ tự byte.

### Tự đánh giá nhanh

- Relative `get` và absolute `get(index)` có cùng cập nhật position không?
- Tại sao absolute index bị ràng buộc bởi limit chứ không chỉ capacity?
- Tại sao `putInt(index, value)` không tự làm cho `flip()` biết đã có dữ liệu?
- Khi đọc một khung gồm 4-byte header, cần bao nhiêu `remaining()` trước `getInt()`?
- Sau `compact()`, các absolute index đã ghi nhớ còn áp dụng nguyên vẹn không?

**Ghi nhớ:** Relative get/put sử dụng rồi dịch `position`, absolute get/put truy cập theo `index` mà giữ nguyên cursor. Cả hai đều tuân thủ `limit` và quyền đọc/ghi của buffer; hãy tổ chức rõ pha ghi → `flip` → đọc → `compact/clear` theo nhu cầu, nhất là khi xử lý dữ liệu nhị phân nhiều đợt.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-buffer-007">remaining, hasRemaining và lỗi tràn hoặc thiếu buffer</a>

<details>
<summary>Xem chi tiết</summary>

# `remaining()`, `hasRemaining()` và lỗi tràn hoặc thiếu NIO Buffer

## 1. Mục tiêu bài học

Trong Java NIO, một `Buffer` không chỉ là vùng nhớ chứa dữ liệu. Nó còn có một **trạng thái đọc/ghi** gồm `capacity`, `limit` và `position`. Hai phương thức `remaining()` và `hasRemaining()` trả lời câu hỏi về khoảng còn lại **giữa position và limit**, chứ không tự biết khoảng ấy chứa dữ liệu có ý nghĩa hay có đủ chỗ trống trong file.

Sau bài này, bạn có thể:

- Tính `remaining()` và giải thích `hasRemaining()` từ trạng thái buffer.
- Dùng đúng `flip()`, `clear()`, `rewind()` và `compact()` để chuyển qua lại giữa ghi dữ liệu vào buffer và đọc dữ liệu từ buffer.
- Phân biệt `BufferUnderflowException` (đọc tương đối thiếu dữ liệu) với `BufferOverflowException` (ghi tương đối không đủ khoảng trống).
- Nhận biết các ngoại lệ khác: `IndexOutOfBoundsException`, `ReadOnlyBufferException`; không nhầm EOF của channel với buffer underflow.
- Viết vòng lặp đọc/ghi `ByteBuffer` qua `FileChannel`, bao gồm xử lý trường hợp một lần ghi chưa tiêu thụ hết buffer.

Các ví dụ dùng Java 17+ và các lớp `java.nio.Buffer`, `java.nio.ByteBuffer`, `java.nio.BufferUnderflowException`, `java.nio.BufferOverflowException`. Nguyên tắc cơ bản cũng áp dụng cho các buffer có kiểu phần tử khác như `CharBuffer`, `IntBuffer` hoặc `DoubleBuffer`; chú ý đơn vị đếm của chúng là **phần tử**, không nhất thiết là byte.

## 2. Bộ ba `position`, `limit`, `capacity`

Một buffer duy trì các giá trị:

| Thuộc tính | Ý nghĩa | Ràng buộc |
| --- | --- | --- |
| `capacity()` | Số phần tử tối đa mà buffer có thể chứa | `capacity >= 0`, không đổi trong vòng đời buffer |
| `limit()` | Ranh giới độc quyền của vùng có thể truy cập theo trạng thái hiện tại | `0 <= limit <= capacity` |
| `position()` | Vị trí của lần đọc/ghi **tương đối** tiếp theo | `0 <= position <= limit` |
| `mark` | Vị trí đánh dấu tùy chọn bằng `mark()` để `reset()` quay lại | Nếu đã đặt, `mark <= position` |

**Công thức cốt lõi:**

```text
remaining() = limit() - position()
hasRemaining() = (position() < limit())
               = (remaining() > 0)
```

Vì API duy trì `0 <= position <= limit`, `remaining()` không âm. Một buffer có thể còn sức chứa vật lý (`capacity > limit`) mà `remaining() == 0`, vì chỉ khoảng từ `position` tới **limit hiện tại** mới là khoảng có thể dùng cho thao tác tương đối.

Minh họa buffer byte có `capacity = 8`:

```text
index:       0 1 2 3 4 5 6 7 |8 (capacity)
             . . . . . . . .
                       ^     ^
                  position=5 limit=8
remaining=8-5=3; hasRemaining=true
```

Nếu `position = limit = 8` thì `remaining = 0` và `hasRemaining = false`. Nếu chỉnh `limit` xuống 6 và `position` là 6, kết quả cũng bằng 0 **dù capacity vẫn là 8**.

`remaining()` không đo kích thước dữ liệu đã ghi trong quá khứ; ý nghĩa khoảng `[position, limit)` do bạn xác lập theo pha vận hành:

- Trong **write mode** (ví dụ buffer mới tạo hoặc sau `clear()/compact()`), `remaining()` thường là số phần tử có thể ghi thêm.
- Trong **read mode** (sau `flip()`), `remaining()` thường là số phần tử sẵn sàng được tiêu thụ, **nếu** dữ liệu trước đó đã được chuẩn bị đúng.

Các khái niệm read/write mode ở đây là **quy ước sử dụng** do lập trình viên thiết lập bằng vị trí và giới hạn. `ByteBuffer` không có thuộc tính boolean `isReadMode()` và sẽ không tự chặn mọi thao tác sai pha.

## 3. Trạng thái điển hình của buffer

Giả sử `ByteBuffer buffer = ByteBuffer.allocate(6)`. Buffer mới có `position = 0`, `limit = capacity = 6`.

### Bước 1: ghi 4 byte vào buffer

```text
Sau put([10,20,30,40]):
index:       0   1   2   3   4   5
value:      10  20  30  40   ?   ?
                           ^
                         position=4; limit=6
remaining = 2  (chỗ có thể ghi tiếp theo write mode)
```

### Bước 2: `flip()` để chuẩn bị đọc

`flip()` đặt `limit = position` cũ rồi `position = 0`; mark bị loại bỏ.

```text
Sau flip():
index:       0   1   2   3   4   5
value:      10  20  30  40   ?   ?
             ^               ^
         position=0        limit=4
remaining = 4  (số byte đã ghi mà pha đọc chuẩn bị tiêu thụ)
```

Lúc này `get()` đọc byte tại position rồi tăng position. Sau khi đọc 3 byte, `position = 3`, `limit = 4` và `remaining = 1`.

### Bước 3: `compact()` giữ phần chưa đọc

`compact()` sao chép phần `[position, limit)` chưa đọc về đầu buffer, sau đó đặt `position` bằng số phần tử vừa giữ lại, `limit = capacity` và loại bỏ mark. Nếu còn byte 40 chưa đọc:

```text
Trước compact:  position=3, limit=4, remaining=1
Sau compact:   byte 40 được chuyển về index 0
                position=1, limit=6, remaining=5
```

Buffer trở về trạng thái phù hợp để **ghi nối tiếp** từ position 1. Nếu ghi thêm byte 50 rồi `flip()`, buffer đọc được lần lượt 40 và 50. Đừng gọi `clear()` thay cho `compact()` khi dữ liệu chưa đọc cần được giữ lại: `clear()` chỉ đặt lại chỉ số, không bảo tồn ranh giới của phần đang chờ xử lý.

## 4. Ví dụ Java 17+ hoàn chỉnh: theo dõi `remaining()` và `hasRemaining()`

Tạo tệp **`BufferLifecycleDemo.java`**:

```java
import java.nio.ByteBuffer;
import java.util.Arrays;

public class BufferLifecycleDemo {
    private static void state(String label, ByteBuffer buffer) {
        System.out.printf(
                "%s: position=%d, limit=%d, capacity=%d, remaining=%d, has=%s%n",
                label,
                buffer.position(),
                buffer.limit(),
                buffer.capacity(),
                buffer.remaining(),
                buffer.hasRemaining()
        );
    }

    public static void main(String[] args) {
        ByteBuffer buffer = ByteBuffer.allocate(6);
        state("new", buffer);

        buffer.put(new byte[] {10, 20, 30, 40});
        state("after put", buffer);

        buffer.flip();
        state("after flip", buffer);

        System.out.println("first=" + buffer.get());
        state("after get", buffer);

        byte[] pair = new byte[2];
        buffer.get(pair);
        System.out.println("pair=" + Arrays.toString(pair));
        state("after pair", buffer);

        buffer.compact();
        state("after compact", buffer);

        buffer.put((byte) 50);
        buffer.flip();

        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        System.out.println("result=" + Arrays.toString(result));
        state("at end", buffer);
    }
}
```

```bash
javac BufferLifecycleDemo.java
java BufferLifecycleDemo
```

Stdout khi chạy thành công:

```text
new: position=0, limit=6, capacity=6, remaining=6, has=true
after put: position=4, limit=6, capacity=6, remaining=2, has=true
after flip: position=0, limit=4, capacity=6, remaining=4, has=true
first=10
after get: position=1, limit=4, capacity=6, remaining=3, has=true
pair=[20, 30]
after pair: position=3, limit=4, capacity=6, remaining=1, has=true
after compact: position=1, limit=6, capacity=6, remaining=5, has=true
result=[40, 50]
at end: position=2, limit=2, capacity=6, remaining=0, has=false
```

Điểm quan trọng: ở dòng `after put`, `remaining = 2` nghĩa là **hai ô còn trống cho thao tác put trong pha ghi**. Sau `flip`, `remaining = 4` nghĩa là **bốn byte đang chờ đọc**. Hai phép đo dùng cùng công thức nhưng được diễn giải theo pha khác nhau.

`compact()` giữ byte 40 còn chưa đọc; byte 50 được nối ngay sau nó. Dòng `result=[40, 50]` xác nhận xử lý chunk tiếp theo mà không làm mất phần dữ liệu đang chờ từ chunk trước.

## 5. `BufferUnderflowException`: yêu cầu đọc vượt `limit`

`BufferUnderflowException` là ngoại lệ runtime khi một thao tác **đọc tương đối** từ buffer đòi hỏi nhiều phần tử hơn `remaining()`. Ví dụ:

```java
ByteBuffer b = ByteBuffer.allocate(2);
b.get(); // BufferUnderflowException? KHÔNG:
         // allocate(2) tạo position=0, limit=2;
         // xét trạng thái NIO, thao tác get() vẫn có thể đọc một byte.
```

Điểm tinh tế: **buffer mới cấp phát không tự ở read mode**. Nó có `remaining = 2`, nên `get()` không ném underflow chỉ vì ứng dụng chưa chủ động `put()`. Dữ liệu đọc được từ một `ByteBuffer.allocate` mới được khởi tạo thành zero theo hợp đồng allocate, nhưng bạn chưa thiết kế dữ liệu có ý nghĩa trong giao thức ứng dụng.

Muốn ví dụ **đúng** về underflow, đặt `limit` về 0 bằng `flip()` khi chưa ghi byte nào:

```java
ByteBuffer emptyForRead = ByteBuffer.allocate(2);
emptyForRead.flip();       // position=0, limit=0
emptyForRead.get();        // BufferUnderflowException
```

Hoặc sau khi tiêu thụ toàn bộ dữ liệu hợp lệ trong read mode:

```java
ByteBuffer b = ByteBuffer.allocate(2);
b.put((byte) 7);
b.flip();                  // position=0, limit=1
b.get();                   // OK: 7; position=1
b.get();                   // BufferUnderflowException
```

Tương tự, nếu buffer chỉ còn `2` byte trong read mode nhưng yêu cầu `b.get(new byte[3])`, đây là underflow. Với các bulk `get`, API kiểm tra đủ phần tử trước khi truyền; khi thiếu, buffer position không bị dịch một phần vì lệnh thất bại.

**Quan trọng:** `BufferUnderflowException` nói rằng **buffer hiện tại** không có đủ phần tử theo limit, chứ không có nghĩa file/channel đã kết thúc. Đọc dữ liệu từ `FileChannel.read(buffer)` khi đến EOF trả `-1`; đọc từ `SocketChannel` theo chế độ nonblocking có thể trả `0` khi chưa có dữ liệu. Hai trạng thái đó khác với ngoại lệ do thao tác `ByteBuffer.get()` sai số lượng.

## 6. `BufferOverflowException`: ghi vượt `limit`

`BufferOverflowException` xuất hiện khi một thao tác **ghi tương đối** vào buffer cần nhiều phần tử hơn số lượng `remaining()` hiện tại.

```java
ByteBuffer b = ByteBuffer.allocate(2);
b.put((byte) 7);
b.put((byte) 8);
b.put((byte) 9);            // BufferOverflowException
```

Khi đã ghi 2 byte, `position = limit = capacity = 2`; `remaining = 0`. Muốn tiếp tục ghi vào buffer, cần tiêu thụ/di chuyển phần dữ liệu đang có theo đúng quy trình rồi `clear()` hoặc `compact()`. Không đơn giản gọi `flip()` sau khi buffer đầy rồi `put()` tiếp: `flip()` chuẩn bị **đọc** phần dữ liệu đã ghi, nên `position=0` và `limit=2`; một `put()` lúc này có thể ghi **đè** dữ liệu đang chờ đọc.

Điều quan trọng với bulk put:

```java
ByteBuffer source = ByteBuffer.wrap(new byte[] {1, 2, 3});
ByteBuffer target = ByteBuffer.allocate(2);
target.put(source); // BufferOverflowException
```

Vì `source.remaining() = 3` nhưng `target.remaining() = 2`, thao tác `target.put(source)` ném `BufferOverflowException`; nó không được phép “chép được 2 byte rồi báo lỗi” theo hợp đồng bulk put của buffer. Hai position vẫn như trước thao tác. Cần tự chia chunk nhỏ phù hợp nếu muốn sao chép từng phần.

**Overflow của buffer không đồng nghĩa với hết dung lượng đĩa.** Nó cho biết giới hạn bộ nhớ logic trong `Buffer` hiện tại. Lỗi ghi xuống đĩa thường biểu hiện qua `IOException` hoặc ngoại lệ đặc thù hệ thống lưu trữ.

## 7. Phân biệt underflow, overflow, index và read-only

| Tình huống | Ngoại lệ hoặc kết quả điển hình | Cách xử lý |
| --- | --- | --- |
| `get()` khi `remaining() == 0` | `BufferUnderflowException` | Chỉ đọc khi còn phần tử, hoặc bổ sung dữ liệu rồi `flip()` |
| `get(byte[4])` khi remaining chỉ 3 | `BufferUnderflowException` | Giảm số byte yêu cầu hoặc thu thập thêm input |
| `put(byte)` khi remaining bằng 0 | `BufferOverflowException` | Đưa buffer qua pha tiêu thụ rồi `clear()/compact()` |
| `put(sourceBuffer)` khi source.remaining lớn hơn target.remaining | `BufferOverflowException` | Chia dữ liệu theo chunk hoặc dùng buffer đích lớn hơn |
| `get(index)` hoặc `put(index, value)` với index không hợp lệ so với limit | `IndexOutOfBoundsException` | Kiểm tra chỉ số truy cập **tuyệt đối** |
| `put(...)` vào read-only buffer | `ReadOnlyBufferException` | Dùng buffer có quyền ghi hoặc chỉ đọc từ view này |
| `FileChannel.read(buffer)` ở EOF | Trả `-1` | Hoàn tất xử lý dữ liệu cuối, không xem đây là BufferUnderflowException |
| `Channel.write(buffer)` ghi được một phần | Trả số byte thực đã ghi | Tiếp tục khi `buffer.hasRemaining()`, xử lý trường hợp không có tiến triển |

Không phải mọi truy cập sai đều ném `BufferUnderflowException` hoặc `BufferOverflowException`. Các phương thức absolute như `get(index)` thường kiểm tra index theo `limit` và ném `IndexOutOfBoundsException` khi sai; chúng **không tự thay đổi position**. Ví dụ `ByteBuffer.allocate(2).get(2)` vượt limit 2, dù không làm thay đổi trạng thái con trỏ.

`ReadOnlyBufferException` thuộc trường hợp quyền ghi của buffer, không phải số lượng còn lại. Các buffer view như `asReadOnlyBuffer()` có thể chia sẻ bộ nhớ nền nhưng ngăn ghi qua view read-only.

## 8. Ví dụ Java 17+ hoàn chỉnh: tái hiện từng lỗi

Tạo tệp **`BufferErrorsDemo.java`**:

```java
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

public class BufferErrorsDemo {
    public static void main(String[] args) {
        ByteBuffer buffer = ByteBuffer.allocate(2);

        buffer.flip(); // không có byte đã put; limit=0
        try {
            buffer.get();
        } catch (BufferUnderflowException ex) {
            System.out.println("underflow: position=" + buffer.position());
        }

        buffer.clear();
        buffer.put((byte) 7).put((byte) 8);
        try {
            buffer.put((byte) 9);
        } catch (BufferOverflowException ex) {
            System.out.println("overflow: position=" + buffer.position());
        }

        buffer.flip(); // position=0, limit=2
        try {
            buffer.get(new byte[3]);
        } catch (BufferUnderflowException ex) {
            System.out.println("bulk underflow: position=" + buffer.position());
        }

        try {
            buffer.get(2); // index=2 == limit => ngoài phạm vi
        } catch (IndexOutOfBoundsException ex) {
            System.out.println("absolute index out of bounds");
        }

        ByteBuffer readOnly = buffer.asReadOnlyBuffer();
        try {
            readOnly.put((byte) 1);
        } catch (ReadOnlyBufferException ex) {
            System.out.println("read-only write rejected");
        }

        ByteBuffer source = ByteBuffer.wrap(new byte[] {1, 2, 3});
        ByteBuffer target = ByteBuffer.allocate(2);
        try {
            target.put(source);
        } catch (BufferOverflowException ex) {
            System.out.println("bulk overflow: source="
                    + source.position() + ", target=" + target.position());
        }

        System.out.println("first valid byte=" + buffer.get());
    }
}
```

```bash
javac BufferErrorsDemo.java
java BufferErrorsDemo
```

Stdout khi chạy thành công:

```text
underflow: position=0
overflow: position=2
bulk underflow: position=0
absolute index out of bounds
read-only write rejected
bulk overflow: source=0, target=0
first valid byte=7
```

Chú ý: `buffer.flip()` đầu tiên làm `limit=0` và bảo đảm underflow ở lệnh đọc sau đó; khác với ví dụ sai thường gặp là gọi `get()` ngay sau `allocate(2)` rồi mong bị underflow. Bulk read thất bại không dịch position, nên lệnh `get()` cuối đọc được byte 7 như mong đợi.

## 9. Tránh lỗi bằng cách kiểm tra khoảng còn lại

### Đọc một phần tử

Nếu yêu cầu “có thì đọc, hết buffer thì dừng”, `hasRemaining()` thích hợp:

```java
while (buffer.hasRemaining()) {
    byte value = buffer.get();
    // Xử lý value
}
```

Nhưng nếu giao thức yêu cầu **đúng 4 byte**, chỉ kiểm tra `hasRemaining()` là chưa đủ. Với `ByteBuffer`:

```java
if (buffer.remaining() >= Integer.BYTES) {
    int value = buffer.getInt();
    // Xử lý value
} else {
    // Chưa đủ 4 byte: yêu cầu thêm input, hoặc báo lỗi định dạng.
}
```

`getInt()` cần 4 byte liên tiếp; `remaining() == 1` khiến `hasRemaining()` trả true nhưng `getInt()` vẫn có thể ném `BufferUnderflowException`. `getLong()` tương tự cần 8 byte; với `CharBuffer.get()` một lần đọc cần một phần tử char.

### Ghi phần tử hoặc nhóm phần tử

```java
if (buffer.remaining() >= Long.BYTES) {
    buffer.putLong(123L);
} else {
    // Flush/consume dữ liệu hiện có, compact nếu phải giữ phần chưa dùng,
    // hoặc cấp phát buffer đủ lớn theo yêu cầu thiết kế.
}
```

Nếu cần ghi một mảng `data` với đoạn `offset` và `length`, kiểm tra số byte dự kiến cùng tính hợp lệ của offset/length. Đừng xem `hasRemaining()` là đủ cho một bulk operation lớn hơn 1 phần tử.

### Khi bắt buộc đủ toàn bộ record

Trong giao thức mạng có header cố định hoặc payload theo chiều dài, có thể phải đợi đủ dữ liệu của record. Mẫu xử lý thường là:

1. Đọc vào buffer đang ở write mode.
2. `flip()` để đọc những byte đã nhận.
3. Chỉ parse khi `remaining()` đủ cho trường dữ liệu hiện tại.
4. Nếu chưa đủ, **không tiêu thụ record dở một cách tùy tiện**; có thể dùng `mark()/reset()` khi đã kiểm soát trạng thái hoặc chỉ đọc sau khi kiểm tra đủ.
5. `compact()` để giữ những byte chưa đọc, đọc tiếp rồi lặp lại.

`mark()` lưu position, `reset()` quay lại mark. Thao tác `flip()`, `clear()`, `rewind()` và `compact()` làm mark không còn hợp lệ; gọi `reset()` khi không có mark hợp lệ ném `InvalidMarkException`. Phương thức `position(newPosition)` hoặc `limit(newLimit)` cũng có thể loại bỏ mark khi mark không còn tương thích với trạng thái mới.

## 10. `flip()`, `clear()`, `rewind()`, `compact()`: chọn đúng

| Phương thức | Position sau gọi | Limit sau gọi | Ý nghĩa |
| --- | --- | --- | --- |
| `flip()` | 0 | Position cũ | Chuyển từ ghi dữ liệu sang tiêu thụ dữ liệu |
| `clear()` | 0 | Capacity | Chuẩn bị dùng lại toàn bộ buffer; **không xóa byte vật lý** |
| `rewind()` | 0 | Giữ nguyên | Đọc/ghi lại từ đầu khoảng hiện hành; không nới limit |
| `compact()` | Số phần tử còn lại trước compact | Capacity | Giữ phần chưa tiêu thụ, chuyển sang chuẩn bị ghi thêm |

Các lệnh này thao tác **trạng thái** của buffer; `clear()` không đổ số 0 vào bộ nhớ. Sau `clear()`, bộ nhớ có thể vẫn chứa byte cũ, nhưng chương trình phải hiểu rằng trạng thái logic mới là để chuẩn bị ghi. Nếu `clear()` rồi `flip()` ngay mà chưa ghi byte nào, `limit=0` và `hasRemaining()=false`.

`compact()` chỉ có ý nghĩa theo phần chưa tiêu thụ `[position, limit)`. Khi buffer đang ở write mode, gọi `compact()` một cách vô thức có thể sao chép cả vùng mà ứng dụng chưa xem là dữ liệu hợp lệ. Cần kiểm soát pha trước khi dùng.

## 11. Vòng lặp channel: buffer còn dữ liệu chưa chắc ghi xong trong một lần

`WritableByteChannel.write(buffer)` cố gắng ghi một số byte từ khoảng `[position, limit)` hiện tại, tăng position bằng số byte thực đã ghi và **có thể chỉ ghi một phần**. Do đó:

```java
buffer.flip();
while (buffer.hasRemaining()) {
    channel.write(buffer);
}
buffer.clear();
```

Đây là ý tưởng nền, nhưng vòng lặp như trên **có thể bận chờ vô hạn** nếu channel không ghi tiến triển (`write` liên tục trả 0, thường gặp với nonblocking channel khi chưa sẵn sàng). Phải lựa chọn cơ chế chờ đúng: với `SocketChannel` không chặn, dùng `Selector` / OP_WRITE hoặc cơ chế backpressure; với channel chặn, hiểu các hành vi I/O phù hợp.

Một điểm khác: `hasRemaining()` chỉ biết buffer còn byte đang chờ, **không** đảm bảo tài nguyên đích đang sẵn sàng, có dung lượng hoặc ghi xong sẽ bền vững. Ghi thành công vẫn phải kiểm tra tiến triển và xử lý `IOException`.

### Ví dụ đầy đủ: sao chép tệp qua `FileChannel`

Tạo tệp **`ChannelCopyDemo.java`**:

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

public class ChannelCopyDemo {
    public static void main(String[] args) throws IOException {
        Path source = Files.createTempFile("nio-source-", ".bin");
        Path destination = Files.createTempFile("nio-dest-", ".bin");

        try {
            byte[] input = "abcdefghi".getBytes(StandardCharsets.UTF_8);
            Files.write(source, input);

            try (FileChannel from = FileChannel.open(
                         source, StandardOpenOption.READ);
                 FileChannel to = FileChannel.open(
                         destination,
                         StandardOpenOption.WRITE,
                         StandardOpenOption.TRUNCATE_EXISTING)) {

                ByteBuffer buffer = ByteBuffer.allocate(4);

                while (true) {
                    int read = from.read(buffer);
                    if (read == -1) {
                        break; // EOF của file, không phải buffer underflow
                    }
                    if (read == 0) {
                        continue; // với FileChannel thường không xảy ra ở đây
                    }

                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        int written = to.write(buffer);
                        if (written == 0) {
                            throw new IOException("FileChannel write made no progress");
                        }
                    }
                    buffer.clear();
                }
            }

            System.out.println("copied bytes=" + Files.size(destination));
            System.out.println("matches=" + Arrays.equals(
                    input, Files.readAllBytes(destination)));
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(destination);
        }
    }
}
```

```bash
javac ChannelCopyDemo.java
java ChannelCopyDemo
```

Stdout khi chạy thành công:

```text
copied bytes=9
matches=true
```

Giải thích từng vòng: buffer có capacity 4; `from.read(buffer)` nạp byte và tăng position; `flip()` đặt limit đến số byte đã nạp; `to.write(buffer)` chuyển một phần hoặc toàn bộ số byte còn lại, có thể phải gọi nhiều lần; `clear()` chuẩn bị buffer cho lần đọc tiếp. File dài 9 byte được truyền theo các chunk tối đa 4 byte và không bị mất chunk cuối cùng (1 byte).

Ví dụ dùng **`FileChannel` thông thường** cho file cục bộ. Với nonblocking channel, việc xử lý `read == 0` hoặc `write == 0` cần chiến lược chờ có sự kiện; không dùng vòng `continue` liên tục để đốt CPU. Trong chương trình thực tế, tạo hai file tạm liên tiếp cũng nên có cleanup phù hợp ngay cả khi lần tạo thứ hai thất bại; ở đây mục đích chính là minh họa giao thức trạng thái buffer.

## 12. Buffer view, slice và bộ nhớ dùng chung

`duplicate()`, `slice()` và `asReadOnlyBuffer()` có thể tạo những buffer chia sẻ nội dung nền nhưng có **position/limit/mark riêng**. Điều này ảnh hưởng trực tiếp đến `remaining()` và `hasRemaining()`:

```java
ByteBuffer original = ByteBuffer.wrap(new byte[] {10, 20, 30, 40});
original.position(1);
original.limit(3);          // original remaining = 2

ByteBuffer slice = original.slice(); // capacity=2, position=0, limit=2
slice.get();                // slice position=1, remaining=1

System.out.println(original.remaining()); // 2
System.out.println(slice.remaining());    // 1
```

Mặc dù nội dung nền được chia sẻ, thao tác `get()` trên `slice` không dịch position của `original`. Những thay đổi **byte dữ liệu** thông qua một view có thể nhìn thấy qua view khác, nhưng chỉ số trạng thái của chúng vẫn độc lập.

Với code đa luồng, buffer không được bảo đảm thread-safe cho truy cập đồng thời tùy ý. Nếu các luồng dùng chung buffer hoặc vùng nhớ được chia sẻ, cần chiến lược phối hợp rõ ràng; không coi `remaining()` là kết quả bất biến trước một thao tác tiếp theo.

## 13. Một số ranh giới quan trọng của kiến thức

- `remaining()` của `ByteBuffer` là số **byte**, của `CharBuffer` là số **char**, của `IntBuffer` là số **int**. Khi thiết kế dung lượng của record, tính đúng đơn vị.
- `buffer.get(byte[])` có thể phát sinh underflow nếu yêu cầu quá nhiều byte; `buffer.get(index)` là absolute, dùng kiểm tra index và không tăng position.
- `ByteBuffer.putInt` và `getInt` tiêu thụ **bốn byte**; `hasRemaining() == true` chưa đủ. Kiểm tra `remaining() >= Integer.BYTES`.
- `BufferOverflowException` không có nghĩa file hết chỗ và `BufferUnderflowException` không đồng nghĩa network EOF.
- `clear()`, `flip()`, `rewind()`, `compact()` không tự ghi xuống đĩa hoặc đọc từ socket; chúng chỉ điều chỉnh con trỏ/giới hạn và trong trường hợp compact thì sao chép phần chưa đọc trong buffer.
- Những tên **UNDERFLOW/OVERFLOW** trong API giải mã ký tự `java.nio.charset.CoderResult` là **trạng thái kết quả của coder** (có phương thức `isUnderflow()/isOverflow()`), không phải cứ gặp tên ấy là `BufferUnderflowException` hoặc `BufferOverflowException`. Khi xử lý `CharsetDecoder` hoặc `CharsetEncoder`, phải tuân theo hợp đồng trạng thái của coder.
- `ByteBuffer.allocate(...)` tạo buffer heap được khởi tạo theo mặc định của Java, nhưng “byte có giá trị 0” không tự biến thành “frame dữ liệu hợp lệ”. Hợp đồng giao thức ứng dụng vẫn cần xác định bao nhiêu byte đã được ghi/nhận hợp lệ.
- `ByteBuffer.allocateDirect(...)` có đặc điểm cấp phát và thao tác khác buffer heap; logic position/limit/remaining vẫn dùng cùng hợp đồng `Buffer`, nên không thay đổi nguyên tắc kiểm tra giới hạn.

## 14. Lỗi phổ biến và cách phòng tránh

1. **Quên `flip()` trước khi đọc byte vừa `put`.** `position` vẫn ở cuối dữ liệu, `limit` chưa thu về số byte thực đã ghi. Hãy chuyển pha rõ ràng.
2. **Quên `clear()/compact()` trước khi nạp đợt kế tiếp.** Buffer có thể hết `remaining` ở pha đọc hoặc ghi sai vị trí.
3. **Gọi `clear()` khi còn dữ liệu chưa tiêu thụ.** Trạng thái coi buffer là trống để ghi mới; dùng `compact()` khi muốn giữ phần còn lại.
4. **Hiểu nhầm `hasRemaining()` là đủ cho mọi thao tác.** Nếu chuẩn bị `getLong()`, hãy kiểm tra ít nhất 8 byte.
5. **Bắt underflow/overflow để điều khiển từng vòng lặp thông thường.** Với dữ liệu có thể kiểm tra trước, `remaining()` giúp code rõ hơn và tránh ngoại lệ làm luồng xử lý chính.
6. **Nhầm overflow buffer với lỗi ổ đĩa.** Xem lại `position/limit` và luồng `flip/clear` trước.
7. **Sao chép buffer-to-buffer nhưng không kiểm tra kích thước đích.** `target.put(source)` yêu cầu đủ chỗ cho toàn bộ `source.remaining()`; hãy chia chunk hoặc dùng vùng đủ lớn.
8. **Gọi `get(index)` và mong position tăng.** Absolute access không dịch position; kiểm tra bằng `getFilePointer` không liên quan, phải dùng `position()` của buffer.
9. **Không xử lý partial write từ channel.** Một lần `write(buffer)` không được đảm bảo tiêu thụ hết byte; dùng vòng lặp có xử lý tiến triển.
10. **Vòng lặp nonblocking bận chờ khi kết quả I/O bằng 0.** Sử dụng selector/event và không quay vòng vô hạn.
11. **Dùng `limit=capacity` làm số byte đã nhận.** Khi channel read được ít byte, `flip()` giúp limit phản ánh position trước đó; `capacity` chỉ là kích thước tối đa.
12. **Chia sẻ view rồi tưởng position chung.** `slice` và `duplicate` có con trỏ riêng dù nội dung nền có thể chung.

## 15. Câu hỏi và bài tập tự kiểm tra

1. Buffer có `capacity=16`, `limit=11` và `position=7`. `remaining()` và `hasRemaining()` là gì? Nếu `position=11` thì sao?
2. Buffer mới `allocate(8)` có `remaining=8`. Điều này có chứng minh ứng dụng đã ghi 8 byte dữ liệu có nghĩa vào đó không?
3. Sau `put` 5 byte vào buffer capacity 8, vì sao phải `flip()` trước khi đọc 5 byte ấy? Sau flip, `position` và `limit` là bao nhiêu?
4. `remaining()=3` và `hasRemaining()=true`, gọi `getInt()` có hợp lệ không? Cần kiểm tra điều gì?
5. Buffer vừa chứa 10 byte dữ liệu, đã đọc 7 byte. Nên dùng `clear()` hay `compact()` nếu phải giữ 3 byte cuối để ghép với chunk mới?
6. Vì sao `ByteBuffer.get(10)` khi `limit=10` gây `IndexOutOfBoundsException`, trong khi `get()` lúc `position=limit` gây `BufferUnderflowException`?
7. Tại sao `FileChannel.read(buffer)==-1` có ý nghĩa khác với `BufferUnderflowException`?
8. Có được kết luận đã gửi thành công toàn bộ dữ liệu nếu một lần `SocketChannel.write(buffer)` trả về số byte nhỏ hơn `buffer.remaining()` trước lúc gọi không? Bạn sẽ thiết kế vòng lặp thế nào?
9. Hai buffer `original` và `original.slice()` chia sẻ memory; gọi `slice.get()` có tăng `original.position()` không?
10. Hãy dự đoán toàn bộ stdout của `BufferLifecycleDemo`, chạy rồi đối chiếu; sửa chương trình để `compact()` giữ 2 byte cuối thay vì 1.

## 16. Tóm lược kiến thức

Cốt lõi của NIO Buffer là **`0 <= position <= limit <= capacity`**, từ đó:

```text
remaining() = limit - position
hasRemaining() = remaining() > 0
```

Trong pha ghi, số còn lại thường là chỗ để ghi thêm; trong pha đọc, đó là số phần tử còn để tiêu thụ. `BufferUnderflowException` báo thao tác đọc tương đối vượt khoảng hiện có, `BufferOverflowException` báo thao tác ghi tương đối vượt khoảng còn trống. Dùng `flip()` để bắt đầu đọc dữ liệu vừa nạp, `clear()` để dùng lại khi đã tiêu thụ xong, `compact()` để giữ phần còn sót, và `remaining()` để kiểm tra đủ cho đúng **số phần tử** mà API sắp dùng. Khi tích hợp channel, việc buffer còn phần tử chỉ là một điều kiện trong vòng lặp; EOF, partial write và nonblocking readiness phải được xử lý riêng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-buffer-008">allocate, wrap và vùng dữ liệu được chia sẻ</a>

<details>
<summary>Xem chi tiết</summary>

# `allocate`, `wrap` và vùng dữ liệu được chia sẻ trong Java NIO Buffer

## 1. Vì sao cần phân biệt tạo buffer với chia sẻ dữ liệu?

`java.nio.Buffer` là nền tảng của các bộ đệm NIO; các kiểu cụ thể như `ByteBuffer`, `CharBuffer`, `IntBuffer` cho phép thao tác dữ liệu thông qua một **vùng lưu trữ** và một tập thông số điều khiển **position**, **limit**, **capacity**. Hai lời gọi trông gần giống nhau có thể mang ý nghĩa quản lý dữ liệu hoàn toàn khác nhau:

```java
ByteBuffer allocated = ByteBuffer.allocate(4);
byte[] bytes = {10, 20, 30, 40};
ByteBuffer wrapped = ByteBuffer.wrap(bytes);
```

Đây là đoạn minh họa, cần import `java.nio.ByteBuffer`. Cả hai buffer đều có `capacity() == 4`, nhưng:

- `allocate(4)` cấp phát **vùng lưu trữ mới**. Không có mảng nguồn do caller cung cấp được chia sẻ ngay từ đầu.
- `wrap(bytes)` tạo một **buffer nhìn lên chính mảng `bytes` hiện có**. Không sao chép dữ liệu. Thay đổi phần tử bằng một phía được nhìn thấy qua phía còn lại.
- Cùng một vùng dữ liệu có thể có **nhiều buffer view**, mỗi view lại có `position`, `limit` và `mark` riêng. Thay đổi cursor của một view không đồng nghĩa cursor view khác thay đổi.
- “Độc lập về vị trí đọc/ghi” **không** có nghĩa “độc lập về nội dung”.

Bài học tập trung vào `allocate`, `wrap`, cách xác định vùng dữ liệu dùng chung, và những sai lầm khi coi các buffer như bản sao độc lập.

## 2. Mô hình bộ đệm: vùng dữ liệu và trạng thái truy cập

Một buffer thông thường có bốn khái niệm liên quan:

| Thuộc tính | Ý nghĩa |
|---|---|
| `capacity` | Số phần tử tối đa buffer có thể chứa; cố định trong suốt vòng đời buffer |
| `limit` | Chỉ số đầu tiên không được đọc/ghi qua thao tác tương đối; giới hạn phạm vi đang dùng |
| `position` | Vị trí của thao tác `get()`/`put()` tương đối tiếp theo |
| `mark` | Vị trí đã đánh dấu để có thể `reset()`; có thể không được đặt |

Bất biến quan trọng: `0 <= position <= limit <= capacity`. Trong code NIO, “chỉ số phần tử” là **chỉ số tương đối với buffer hiện tại**, không tự động là chỉ số trong mảng Java gốc.

Sơ đồ ví dụ, buffer có `capacity=8, position=2, limit=6`:

```text
index:       0    1    2    3    4    5    6    7
           +----+----+----+----+----+----+----+----+
storage:   |    |    |    |    |    |    |    |    |
           +----+----+----+----+----+----+----+----+
                      ^                   ^
                   position              limit
           <----------- capacity = 8 ----------->
                     <remaining = 4>
```

`remaining() == limit - position`. `get()` lấy ở `position` và tăng `position`; `put(value)` ghi ở `position` và tăng nó. `get(index)`/`put(index,value)` là thao tác **absolute** trong phạm vi chỉ số hợp lệ, không tự tăng `position`. Với `get(index)`/`put(index,value)` một phần tử, chỉ số phải nhỏ hơn `limit`. Khi tạo slice mới, chỉ số 0 của slice có thể ánh xạ tới một vị trí khác trong vùng lưu trữ chung.

**Lưu ý phân biệt:** gọi `clear()`, `flip()`, `rewind()` điều chỉnh trạng thái đọc/ghi; chúng **không** xóa nội dung vật lý của mảng hay cấp phát storage mới.

## 3. `ByteBuffer.allocate(capacity)` — cấp phát vùng lưu trữ mới

```java
import java.nio.ByteBuffer;

ByteBuffer buffer = ByteBuffer.allocate(5);
System.out.println(buffer.capacity());  // 5
System.out.println(buffer.position());  // 0
System.out.println(buffer.limit());     // 5
System.out.println(buffer.isDirect());  // false
System.out.println(buffer.hasArray());  // true
```

Đây là đoạn minh họa có thể đặt trong `main`. `ByteBuffer.allocate(5)` tạo một **heap byte buffer** mới có capacity 5. Các byte ban đầu bằng 0. Trạng thái ban đầu: `position=0`, `limit=capacity`, `mark` chưa được định nghĩa.

Các điểm thiết yếu:

- `allocate` tạo backing storage mới. Nếu tạo hai buffer bằng hai lời gọi `allocate` khác nhau, chúng không chia sẻ cùng mảng backing chỉ vì có cùng capacity.
- Heap `ByteBuffer` được tạo bằng `allocate` có backing `byte[]` truy cập được thông qua `array()`, và thông thường `arrayOffset() == 0` cho buffer ban đầu.
- `buffer.array()` trả **tham chiếu đến mảng backing**, **không tạo bản sao**. Nếu bạn đưa mảng này cho code khác, chính bạn đã mở đường chia sẻ dữ liệu và thay đổi ngoài buffer.
- Capacity âm gây `IllegalArgumentException`. Dung lượng rất lớn có thể gây `OutOfMemoryError`, không nên xem cấp phát là thao tác luôn thành công.

Ví dụ hai buffer cấp phát độc lập:

```java
ByteBuffer first = ByteBuffer.allocate(2);
ByteBuffer second = ByteBuffer.allocate(2);

first.put(0, (byte) 7);

System.out.println(first.get(0));  // 7
System.out.println(second.get(0)); // 0
```

Ở đây `first` và `second` có storage khác nhau. Ngược lại, biến `byte[] alias = first.array()` sẽ truy cập chính storage của `first`; đó là chia sẻ tham chiếu, dù buffer ban đầu được tạo bằng `allocate`.

### 3.1. `allocateDirect` là trường hợp riêng, không đồng nghĩa `allocate`

`ByteBuffer.allocateDirect(n)` yêu cầu direct byte buffer. Direct buffer thường giúp thực hiện một số thao tác I/O native với ít sao chép trung gian, nhưng chi phí cấp phát/quản lý khác heap buffer và không mặc định nhanh hơn cho mọi công việc.

```java
ByteBuffer heap = ByteBuffer.allocate(8);
ByteBuffer direct = ByteBuffer.allocateDirect(8);

System.out.println(heap.isDirect());   // false
System.out.println(direct.isDirect()); // true
System.out.println(heap.hasArray());   // true
System.out.println(direct.hasArray()); // false
```

Đoạn trên có thể đặt trong `main` cùng import `ByteBuffer`. Direct buffer không cho phép truy cập một `byte[]` backing qua `array()` như heap buffer. `hasArray()==false` cũng **chưa đủ để kết luận direct**, vì read-only heap buffer có thể không cho truy cập mảng. Khái niệm direct/heap thuộc lựa chọn lưu trữ; khái niệm shared/independent thuộc quan hệ các view và nguồn dữ liệu.

## 4. `ByteBuffer.wrap(byte[])` — dùng chung đúng mảng đã có

Khi có mảng byte:

```java
byte[] source = {10, 20, 30};
ByteBuffer buffer = ByteBuffer.wrap(source);

buffer.put(1, (byte) 99);
System.out.println(source[1]); // 99

source[2] = 77;
System.out.println(buffer.get(2)); // 77
```

Đây là đoạn minh họa dùng trong `main` với import `ByteBuffer`. Không có lần copy ngầm nào trong `wrap(source)`; cả `buffer` lẫn `source` đều nhìn cùng phần tử bộ nhớ.

Trạng thái ban đầu khi gọi `wrap(source)`:

```text
position = 0
limit    = source.length
capacity = source.length
backing array = source
arrayOffset = 0
```

`wrap` đặc biệt hữu ích khi một API tạo hoặc nhận `byte[]` còn phần xử lý NIO cần dùng các thao tác `get`, `put`, `flip`, `slice`, giải mã nhị phân hay tích hợp kênh I/O. Giá trị “không sao chép” giúp giảm copy, nhưng bắt buộc người viết code hiểu **ai sở hữu mảng và ai có quyền chỉnh sửa nó**.

### 4.1. Vì sao `wrap` không phải “chuyển đổi bất biến”?

`ByteBuffer.wrap(source)` giữ tham chiếu đến `source`. Ngay cả khi caller không còn giữ biến `source`, một đoạn khác đã nhận được mảng trước đó vẫn có thể thay đổi dữ liệu. Ngược lại, khi buffer được truyền cho người khác, người ấy có thể sửa dữ liệu mảng gốc thông qua buffer nếu nó còn writable.

Muốn có **bản sao nội dung độc lập**, phải sao chép chủ động, ví dụ:

```java
byte[] original = {1, 2, 3};
ByteBuffer shared = ByteBuffer.wrap(original);
ByteBuffer detached = ByteBuffer.wrap(original.clone());

original[0] = 9;
System.out.println(shared.get(0));   // 9
System.out.println(detached.get(0)); // 1
```

`clone()` tạo mảng byte khác trước khi `wrap`. Lưu ý với cấu trúc dữ liệu phức tạp, `clone` có thể là shallow copy; nhưng riêng `byte[]` chứa giá trị nguyên thủy nên tạo bản sao byte độc lập.

## 5. Bẫy nổi bật: `wrap(array, offset, length)` không tạo buffer capacity = length

Xét mảng:

```java
byte[] bytes = {10, 20, 30, 40, 50, 60};
ByteBuffer view = ByteBuffer.wrap(bytes, 1, 3);
```

Nhiều người đoán `view` có `capacity=3`, `position=0`, `limit=3`. Đây là **phỏng đoán sai**.

Theo contract `ByteBuffer.wrap(array, offset, length)`:

```text
capacity = array.length           = 6
position = offset                 = 1
limit    = offset + length        = 4
mark     = undefined
arrayOffset = 0 (ở buffer bao toàn mảng ban đầu)
```

Đối số `offset` và `length` xác định **phần mảng sẵn sàng cho thao tác tương đối ban đầu**, không thay đổi capacity của buffer thành `length`. Vì vậy:

```java
System.out.println(view.capacity()); // 6
System.out.println(view.position()); // 1
System.out.println(view.limit());    // 4
System.out.println(view.remaining()); // 3

System.out.println(view.get());      // 20 (position tăng thành 2)
System.out.println(view.get(0));     // 10 (absolute get, không đổi position)
```

Đây là đoạn minh họa tiếp từ khai báo `view`. `get(0)` có thể truy cập phần tử nằm **trước** offset, miễn chỉ số nằm dưới `limit`; `offset` không phải hàng rào cách ly bộ nhớ. Sau `clear()`, buffer này có thể thao tác tới capacity 6; `clear()` không sao chép hoặc giới hạn lại vào đoạn 3 byte.

### 5.1. Muốn có hệ chỉ số bắt đầu ở 0 cho đoạn được chọn: `slice()`

```java
byte[] bytes = {10, 20, 30, 40, 50, 60};
ByteBuffer window = ByteBuffer.wrap(bytes, 1, 3).slice();
```

Vì buffer gốc có `position=1` và `limit=4`, `slice()` tạo buffer mới chỉ nhìn **vùng còn lại** gồm ba byte `{20,30,40}`. Nó có:

```text
position    = 0
limit       = 3
capacity    = 3
arrayOffset = 1 (với heap buffer này)
```

Hệ chỉ số của `window` là 0..2, nhưng các byte **vẫn chia sẻ** với `bytes`:

```java
window.put(0, (byte) 99);
System.out.println(bytes[1]); // 99
```

Có thể xem phép ánh xạ ở đây là:

```text
window index:       0     1     2
                    |     |     |
underlying bytes:  [10]  [99]  [30]  [40]  [50]  [60]
array index:         0     1     2     3     4     5
                          ^-----------^
                     window's backing region
```

Nếu cần xác định chỉ số phần tử đầu của một accessible array-backed buffer, dùng `buffer.arrayOffset() + bufferIndex`. **Không giả định `buffer.array()[0]` luôn là byte ở `buffer.get(0)`.** Mệnh đề đó chỉ đúng với vài buffer ban đầu có `arrayOffset()==0`.

### 5.2. `slice(index, length)` — chọn một đoạn theo chỉ số xác định

Trong các phiên bản Java hiện đại (có trong Java 17), có thể gọi:

```java
ByteBuffer original = ByteBuffer.wrap(new byte[]{10, 20, 30, 40, 50});
ByteBuffer part = original.slice(1, 3);

System.out.println(part.capacity()); // 3
System.out.println(part.position()); // 0
System.out.println(part.get(0));     // 20
```

Chỉ số `1` ở đây là chỉ số **của buffer `original`**, không phải tham số offset truyền tới constructor của một mảng tùy ý. `slice(index,length)` tạo vùng nhìn gồm các byte từ `index` tới `index+length-1`, phụ thuộc `limit` của `original` và phải hợp lệ theo contract. Bộ đệm slice cũng dùng chung storage và mang trạng thái cursor riêng.

## 6. `duplicate`, `slice`, `asReadOnlyBuffer`: khác cursor, cùng dữ liệu

Từ một `ByteBuffer base` có thể tạo các view:

| API | Vùng dữ liệu nhìn thấy | `position` / `limit` ban đầu | Có chia sẻ byte với gốc? |
|---|---|---|---|
| `base.duplicate()` | Cùng toàn bộ vùng buffer gốc | Sao lại giá trị `position`, `limit`, `mark` ban đầu | Có |
| `base.slice()` | Vùng `base.position()` đến `base.limit()` | `position=0`, `limit=capacity=base.remaining()` | Có |
| `base.slice(index,length)` | Vùng chỉ định của base | `position=0`, `limit=capacity=length` | Có |
| `base.asReadOnlyBuffer()` | Cùng vùng buffer gốc nhưng không được ghi qua view đó | Sao lại các giá trị trạng thái cursor ban đầu | Có |
| `ByteBuffer.wrap(base.array().clone())` | Mảng copy (chỉ hợp lệ khi `array()` truy cập được) | `position=0`, `limit=capacity=độ dài mảng copy` | Không |

**Nguyên tắc:** `duplicate` và `slice` không copy bytes. `asReadOnlyBuffer` cũng không copy bytes; “read-only” chỉ chặn **ghi thông qua view đó**, không làm ảnh chụp snapshot bất biến trước thay đổi của view khác.

### 6.1. Cursor độc lập

```java
ByteBuffer original = ByteBuffer.wrap(new byte[]{5, 6, 7, 8});
ByteBuffer other = original.duplicate();

original.position(2);
other.position(1);

System.out.println(original.get()); // 7, original position thành 3
System.out.println(other.get());    // 6, other position thành 2
```

Hai view cùng nhìn byte gốc nhưng không chia sẻ biến `position`. Khi tạo duplicate, trạng thái ban đầu được sao chép; **sau đó** các cursor thay đổi độc lập. Tương tự, thay đổi `limit` hay `mark` của view này không phải thao tác lên state của view kia.

### 6.2. Dữ liệu không độc lập

```java
byte[] bytes = {5, 6, 7};
ByteBuffer a = ByteBuffer.wrap(bytes);
ByteBuffer b = a.duplicate();

b.put(1, (byte) 99);
System.out.println(a.get(1)); // 99
System.out.println(bytes[1]); // 99
```

Dù `a.position()` và `b.position()` riêng biệt, một thao tác ghi qua `b` vẫn đổi byte mà `a` và `bytes` nhìn thấy.

### 6.3. Read-only view không đồng nghĩa snapshot

```java
ByteBuffer writable = ByteBuffer.wrap(new byte[]{1, 2, 3});
ByteBuffer readOnly = writable.asReadOnlyBuffer();

writable.put(0, (byte) 9);
System.out.println(readOnly.get(0)); // 9

// readOnly.put(0, (byte) 5) sẽ ném ReadOnlyBufferException.
```

Read-only view nhìn thấy thay đổi từ writable alias. `readOnly.isReadOnly()` là `true`; `readOnly.array()` không cho truy cập backing array ghi được. Một buffer được tạo trực tiếp từ read-only view không đột nhiên trở thành writable; các view mới vẫn tuân theo hạn chế read-only.

**Nếu cần dữ liệu ổn định**, bạn phải copy bytes sang vùng riêng và kiểm soát quyền ghi vào bản đó. Gán “read-only” lên một góc nhìn không loại bỏ alias ghi khác.

## 7. Ví dụ Java 17+ biên dịch và chạy được

Ví dụ sau kết hợp `allocate`, `wrap(offset,length)`, `slice`, `duplicate`, `asReadOnlyBuffer` và **copy độc lập**, kèm số liệu cụ thể cho vị trí và nội dung.

**Tệp:** `BufferSharingDemo.java`

```java
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.Arrays;

public class BufferSharingDemo {
    public static void main(String[] args) {
        ByteBuffer allocated = ByteBuffer.allocate(4);
        System.out.printf(
                "allocate: pos=%d limit=%d capacity=%d%n",
                allocated.position(), allocated.limit(), allocated.capacity());

        byte[] bytes = {10, 20, 30, 40, 50, 60};
        ByteBuffer wrapped = ByteBuffer.wrap(bytes, 1, 3);
        System.out.printf(
                "wrap(offset,length): pos=%d limit=%d capacity=%d%n",
                wrapped.position(), wrapped.limit(), wrapped.capacity());

        wrapped.put(2, (byte) 99);
        System.out.println("after wrap.put: " + Arrays.toString(bytes));

        ByteBuffer window = ByteBuffer.wrap(bytes, 1, 3).slice();
        System.out.printf(
                "slice: pos=%d limit=%d capacity=%d arrayOffset=%d%n",
                window.position(), window.limit(), window.capacity(),
                window.arrayOffset());

        window.put(0, (byte) 77);
        ByteBuffer duplicate = window.duplicate();
        duplicate.position(2);
        duplicate.put(1, (byte) 88);

        System.out.println("after views: " + Arrays.toString(bytes));
        System.out.println("positions: window=" + window.position()
                + ", duplicate=" + duplicate.position());

        ByteBuffer readOnly = window.asReadOnlyBuffer();
        System.out.println("read-only: " + readOnly.isReadOnly()
                + ", sees=" + readOnly.get(1));

        try {
            readOnly.put(0, (byte) 1);
        } catch (ReadOnlyBufferException ex) {
            System.out.println("read-only write: ReadOnlyBufferException");
        }

        ByteBuffer independent = ByteBuffer.allocate(window.remaining());
        independent.put(window.duplicate());
        independent.flip();

        window.put(0, (byte) 11);
        System.out.println("copy first=" + independent.get(0)
                + ", source first=" + window.get(0));
    }
}
```

Chạy bằng JDK 17+ tại thư mục chứa tệp nguồn:

```bash
javac BufferSharingDemo.java
java BufferSharingDemo
```

**Stdout dự kiến chính xác:**

```text
allocate: pos=0 limit=4 capacity=4
wrap(offset,length): pos=1 limit=4 capacity=6
after wrap.put: [10, 20, 99, 40, 50, 60]
slice: pos=0 limit=3 capacity=3 arrayOffset=1
after views: [10, 77, 88, 40, 50, 60]
positions: window=0, duplicate=2
read-only: true, sees=88
read-only write: ReadOnlyBufferException
copy first=77, source first=11
```

**Tại sao có các kết quả này?**

1. `allocate(4)` tạo buffer mới ở chế độ ghi với `position=0`, `limit=capacity=4`.
2. `wrap(bytes,1,3)` không thu nhỏ capacity của mảng; nó đặt `position=1` và `limit=4`. Absolute `put(2,99)` ghi vào `bytes[2]` mà không đổi position.
3. `slice()` tạo view riêng của đoạn `bytes[1..3]`; do đó capacity 3 và `arrayOffset=1`.
4. `window.put(0,77)` đổi `bytes[1]`; `duplicate.put(1,88)` đổi `bytes[2]`, mặc dù cursor của duplicate đã được đặt bằng 2. Hai phép `put(index,value)` không thay đổi position.
5. Read-only view không cho ghi qua nó, nhưng vẫn đọc được 88 từ storage chung.
6. `independent` có vùng lưu trữ mới và sao chép ba byte. Sau khi `window` đổi byte đầu từ 77 thành 11, buffer copy vẫn giữ 77.

Mã dùng các thao tác đồng bộ trong một thread để output tái lập, không dựa vào scheduler hoặc filesystem.

## 8. `get`, `put`, `flip`, `clear` khi các buffer chia sẻ mảng

Với một buffer thông thường:

```java
ByteBuffer buffer = ByteBuffer.allocate(4);
buffer.put((byte) 10);
buffer.put((byte) 20);
buffer.flip();

System.out.println(buffer.get()); // 10
System.out.println(buffer.get()); // 20
```

`flip()` đặt `limit` thành `position` cũ rồi `position=0` để chuẩn bị đọc lượng dữ liệu vừa ghi. Nếu buffer có nhiều view, `flip()` chỉ cập nhật trạng thái **view được gọi**, không tự cập nhật cursor của tất cả view kia.

### 8.1. `clear()` có thật sự xóa dữ liệu?

Không. `clear()` thường đưa `position=0`, `limit=capacity` và bỏ mark, chuẩn bị ghi đè. Dữ liệu cũ có thể vẫn nằm trong backing storage cho đến khi bị thay:

```java
byte[] bytes = {1, 2, 3};
ByteBuffer wrapped = ByteBuffer.wrap(bytes);
wrapped.clear();
System.out.println(Arrays.toString(bytes)); // [1, 2, 3]
```

Đoạn minh họa cần import `java.util.Arrays` và `java.nio.ByteBuffer`. Với dữ liệu nhạy cảm, `clear()` **không** phải biện pháp xóa an toàn hay zero memory.

### 8.2. Khi nào `BufferOverflowException` và `BufferUnderflowException` xảy ra?

Chính `position` và `limit` của view quyết định số phần tử còn lại. Một lời gọi relative `put` khi không còn chỗ trong vùng `remaining` sẽ ném `BufferOverflowException`; relative `get` khi không còn phần tử sẽ ném `BufferUnderflowException`. Đừng cho rằng còn chỗ trong **mảng gốc** thì buffer luôn ghi/đọc tiếp được: view có limit riêng.

Ví dụ, với `ByteBuffer.wrap(bytes,1,3)`, chỉ còn ba byte cho các thao tác tương đối ban đầu; sau ba lần `get()` tiếp theo, `get()` thứ tư sẽ underflow. Thay đổi limit hoặc dùng absolute access là lựa chọn khác nhưng phải phù hợp yêu cầu nghiệp vụ và kiểm tra bounds.

### 8.3. `compact()` có thể ảnh hưởng view khác vì nó di chuyển bytes

`compact()` sao chép các phần tử chưa đọc về đầu **vùng storage của buffer hiện tại**, đặt position để ghi tiếp và có thể làm thay đổi nội dung backing array được các view khác quan sát. Vì vậy, `compact()` không chỉ là cập nhật cursor đơn thuần như `clear()` hay `flip()`; với shared storage, đây là thao tác có tác dụng lên dữ liệu.

## 9. Vùng dữ liệu chia sẻ và quyền truy cập backing array

Với **heap ByteBuffer writable**, thường có thể:

```java
ByteBuffer heap = ByteBuffer.allocate(6);
byte[] backing = heap.array();
int offset = heap.arrayOffset();
```

`backing` là cùng mảng chứa bytes, không phải bản sao. `offset` cần cộng vào index khi buffer là một slice có vùng lưu trữ bắt đầu ở giữa mảng.

Có ba trường hợp cần phân biệt:

| Buffer | `hasArray()` | `array()` |
|---|---|---|
| Heap `ByteBuffer.allocate(...)` ban đầu writable | `true` | Trả cùng `byte[]` backing |
| Heap slice/duplicate writable có accessible array | `true` | Trả cùng mảng backing, `arrayOffset` có thể khác |
| Read-only view của heap buffer | `false` | Ném `ReadOnlyBufferException` |
| Direct `ByteBuffer` | `false` | Ném `UnsupportedOperationException` |

`hasArray()` chỉ cho biết có backing array **truy cập được theo contract**; không nói rằng bộ nhớ “không bị chia sẻ”. Một read-only buffer có thể share bytes qua view writable khác; một direct buffer có thể chia sẻ storage qua các direct slice dù không có accessible `byte[]`.

Khi viết thư viện tổng quát nhận `ByteBuffer`, cách vững chắc là dùng `get`/`put`/`slice`/`duplicate` và chỉ tối ưu sang `array()` khi `hasArray()` phù hợp, đồng thời xử lý đúng `position`, `remaining`, `arrayOffset` và quyền read-only.

### 9.1. Chuyển byte buffer thành array mới mà không sửa cursor caller

Ví dụ phương thức copy **chỉ vùng còn lại**:

```java
import java.nio.ByteBuffer;

public class BufferBytes {
    public static byte[] remainingBytes(ByteBuffer source) {
        ByteBuffer reader = source.duplicate();
        byte[] result = new byte[reader.remaining()];
        reader.get(result);
        return result;
    }
}
```

Đây là lớp helper có thể biên dịch độc lập (`BufferBytes.java`), không có `main` và không có stdout. `duplicate()` giữ cùng storage nhưng có cursor riêng; `get(result)` đọc dữ liệu vào mảng **mới**, nên `source.position()` không bị thay đổi. Lưu ý `source` có thể bị sửa đồng thời qua alias khác, khiến các byte copy được phản ánh một trạng thái trung gian; API này không cung cấp snapshot nguyên tử dưới ghi đồng thời.

**Tại sao không dùng `source.array()`?** Vì nó có thể ném ngoại lệ trên direct/read-only buffer, trả cả backing array lớn thay vì `remaining`, và có thể làm lộ dữ liệu ngoài vùng caller dự định cho phép đọc.

## 10. `CharBuffer.wrap` và các kiểu buffer khác

Ý tưởng “wrap dùng vùng dữ liệu có sẵn” không chỉ áp dụng cho `ByteBuffer`:

```java
char[] letters = {'J', 'a', 'v', 'a'};
CharBuffer characters = CharBuffer.wrap(letters);

characters.put(0, 'K');
System.out.println(letters[0]); // K
```

Đoạn minh họa với `import java.nio.CharBuffer;`. `CharBuffer.wrap(char[])` chia sẻ mảng `char[]`. Với các kiểu `IntBuffer`, `ShortBuffer`, `LongBuffer`, `FloatBuffer`, `DoubleBuffer`, cách wrap mảng primitive tương ứng cũng theo tư tưởng backing storage dùng chung.

**Ngoại lệ quan trọng khi gặp `CharSequence`:**

```java
CharBuffer text = CharBuffer.wrap("Java");
System.out.println(text.isReadOnly()); // true, đối với String
```

`CharBuffer.wrap(CharSequence)` tạo một char buffer đọc từ sequence; nó không giống wrap mutable `char[]`. Với `String` bất biến, view không cho sửa nội dung theo cách sửa mảng; cố `put` vào char buffer được tạo bởi overload này sẽ ném `ReadOnlyBufferException`. Với `CharSequence` có thể thay đổi, hãy xét contract của sequence và provider trước khi suy luận về tính ổn định của dữ liệu được quan sát.

Một kiểu buffer khác cũng có thể là **typed view** trên byte buffer, ví dụ `byteBuffer.asIntBuffer()`: nó cho một góc nhìn các phần tử `int` trên cùng byte storage (với quy tắc thứ tự byte và alignment tương ứng). Khi chỉnh byte bằng view này, dữ liệu cùng vùng có thể thay đổi ở các view khác. Không nên suy ra mọi buffer cùng capacity số phần tử bằng nhau: `IntBuffer` tính capacity theo số giá trị `int`, không theo số byte.

## 11. Rủi ro thiết kế và đồng thời

### 11.1. Chia sẻ backing storage là chia sẻ khả năng thay đổi dữ liệu

Ví dụ một API nhận mảng và giữ reference:

```java
static ByteBuffer parseLater(byte[] packet) {
    return ByteBuffer.wrap(packet);
}
```

Nếu caller sửa `packet` sau khi truyền, buffer mà parser dùng về sau sẽ nhìn dữ liệu mới. Trường hợp parser muốn “đóng băng đầu vào” để kiểm tra checksum, chữ ký, protocol frame hoặc cache, nó phải xác định rõ **sở hữu mảng** và cân nhắc copy chủ động. Không chỉ nhìn vào tên tham số kiểu `ByteBuffer` để đoán đối tượng được sở hữu độc quyền.

Các chiến lược phổ biến:

- **Chia sẻ có chủ đích:** nói rõ caller không được thay dữ liệu trong thời gian parser dùng buffer, giúp tránh copy nhưng cần phối hợp vòng đời.
- **Bản sao riêng:** `ByteBuffer.wrap(array.clone())` hoặc copy `remaining` vào mảng mới; tăng chi phí bộ nhớ/copy nhưng giảm alias với đầu vào.
- **Chỉ cấp read-only view:** hạn chế ghi từ người nhận, nhưng không ngăn owner hoặc alias khác thay backing storage.
- **Truyền dữ liệu theo luồng:** nếu không cần random access, cân nhắc stream/channel và quy tắc vòng đời nguồn; không phải mọi API đều cần backing array chia sẻ.

### 11.2. Buffer không mặc định thread-safe

Một `ByteBuffer` mutable có cursor và storage có thể thay đổi. Dùng cùng instance hoặc các view chung backing storage từ nhiều thread mà không đồng bộ có thể gây tranh chấp dữ liệu, trạng thái quan sát không nhất quán hoặc logic đọc sai.

Việc `duplicate()` tạo cursor riêng có thể giúp tách trạng thái vị trí đọc/ghi, nhưng **không làm bytes dùng chung trở thành thread-safe**. `asReadOnlyBuffer()` hạn chế ghi qua một alias, song alias khác vẫn có thể thay dữ liệu đồng thời. Khi có tác vụ concurrent, cần ownership/handoff, synchronization hoặc cấu trúc dữ liệu và cơ chế điều phối riêng phù hợp Java Memory Model.

### 11.3. Đọc dữ liệu ổn định không chỉ là đặt limit

`position` và `limit` kiểm soát API `get`/`put` trên view, **không phải rào chắn bảo mật đối với backing storage**. Một view khác hoặc mảng gốc vẫn có thể chạm phần tử ngoài vùng giới hạn của view này. Nếu có dữ liệu riêng tư ở trước/sau một vùng slice, đừng gửi cả mảng thông qua `array()` cho thành phần không đáng tin cậy; gửi bản sao chính xác vùng cho phép.

### 11.4. Lifetime của direct buffer và mapped buffer

Đối với direct buffer, cần lưu ý chi phí cấp phát native memory và vòng đời do JVM quản lý; không nên tích lũy vô hạn direct allocations. Với `MappedByteBuffer` ánh xạ tệp, thao tác qua các view có thể liên quan dữ liệu file được ánh xạ và việc đồng bộ/durability cần theo contract riêng. Các trường hợp này vẫn minh họa rằng “buffer” là mô hình truy cập dữ liệu, còn vị trí/vòng đời dữ liệu phụ thuộc cách tạo buffer.

## 12. Các lỗi thường gặp và cách sửa

1. **Nghĩ `wrap(array)` tạo bản sao.** Thực tế buffer chia sẻ mảng. Nếu cần độc lập, dùng `array.clone()` hoặc copy có chủ đích.
2. **Nghĩ `allocate` trả buffer không có backing array.** Heap buffer từ `ByteBuffer.allocate` có backing array; `allocateDirect` mới có direct storage.
3. **Nghĩ `wrap(array,offset,length)` đặt capacity = length.** Nó giữ capacity bằng `array.length`, đặt position/limit tương ứng. Muốn cửa sổ 0-based, sử dụng `slice()`.
4. **Quên `arrayOffset` ở slice.** `buffer.get(0)` có thể trỏ tới `backing[arrayOffset]`, không phải `backing[0]`.
5. **Nghĩ `slice` và `duplicate` sao chép bytes.** Chúng tạo views chia sẻ storage; cursor độc lập nhưng nội dung liên kết.
6. **Nghĩ `asReadOnlyBuffer` đóng băng dữ liệu.** View readonly không ngăn writer khác thay backing bytes.
7. **Cho rằng `hasArray()==false` tức là direct.** Read-only heap buffer cũng có thể không có accessible array; dùng `isDirect()` nếu câu hỏi là direct hay không.
8. **Nhầm `clear()` là zero/reset dữ liệu.** Nó thay trạng thái cursor, không ghi đè toàn bộ mảng.
9. **Truy cập `array()` mà không xét phạm vi.** Trả toàn mảng backing, có thể lớn hơn vùng `position..limit` muốn xuất.
10. **Dùng relative `put` mà không xét `remaining()`.** Chạm limit gây overflow dù array vẫn còn các index bên ngoài view.
11. **Nhầm absolute access với relative access.** `put(index,value)` không tăng position; `put(value)` có tăng.
12. **Cho rằng cùng backing là an toàn khi đa luồng.** Cần quy tắc đồng bộ và sở hữu; cursor riêng không ngăn data race.
13. **Nghĩ buffer chỉ có thể chia sẻ storage khi tạo qua `wrap`.** `duplicate`, `slice` và `array()` từ `allocate` cũng tạo đường chia sẻ.
14. **Dùng `wrap(CharSequence)` như wrap `char[]` để ghi.** Overload nhận chuỗi có ngữ nghĩa read-only tùy sequence; cần chọn overload phù hợp.
15. **Đồng nhất capacity với số byte hợp lệ để đọc.** Sau khi ghi cần `flip()` hoặc đặt limit phù hợp; dữ liệu nằm trong backing array không đồng nghĩa thuộc vùng readable dự định.

## 13. Kiểm tra nhanh khả năng dự đoán shared storage

**Câu 1.** Với:

```java
byte[] a = {1, 2, 3, 4};
ByteBuffer b = ByteBuffer.wrap(a, 1, 2);
```

`b.capacity()`, `b.position()`, `b.limit()` bằng bao nhiêu?  
**Đáp án:** `4`, `1`, `3`. `offset` và `length` chỉ thiết lập cursor/range ban đầu.

**Câu 2.** Nếu gọi `ByteBuffer c = b.slice()` ngay sau đó thì `c.capacity()` và `c.arrayOffset()` là gì?  
**Đáp án:** `2` và `1` với array-backed buffer này; `c.position()=0` và `c.limit()=2`. `c.get(0)` tương ứng `a[1]`.

**Câu 3.** `c.put(0, (byte) 8)` có thay `a` không?  
**Có.** `a[1]` thành 8. Slice vẫn chia sẻ backing.

**Câu 4.** Sau `ByteBuffer d = c.duplicate()`, gọi `d.position(1)` có thay `c.position()` không?  
**Không.** Cursor độc lập, storage chung.

**Câu 5.** `c.asReadOnlyBuffer()` có ngăn việc sửa dữ liệu qua `a[1]=9` không?  
**Không.** Nó chỉ chặn ghi qua view read-only đó; view vẫn có thể quan sát giá trị 9.

**Câu 6.** `ByteBuffer.allocate(4).array()` có tạo bản sao mới khi đọc `array()` không?  
**Không.** Đây là mảng backing của chính buffer.

**Câu 7.** Muốn tạo bản sao chính xác phần `remaining` của một buffer có thể là direct/read-only, dùng gì?  
**Gợi ý:** `source.duplicate()` để không đổi cursor caller, tạo `byte[]` kích thước `duplicate.remaining()` rồi `duplicate.get(bytes)`.

## 14. Ghi nhớ và tài liệu tham khảo

**Quy tắc học thuộc:** `allocate` tạo storage mới, `wrap` dùng storage đã có; `slice`/`duplicate` tạo view dùng chung storage nhưng quản lý position/limit riêng; `asReadOnlyBuffer` chặn ghi qua view, không tạo snapshot. Đối với `wrap(array,offset,length)`, nhớ `capacity=array.length` và muốn đoạn 0-based thực sự thì tiếp tục `slice()`. Khi cần độc lập hoàn toàn, hãy copy dữ liệu có chủ đích.

Tài liệu chính thức Java SE 21:

- `ByteBuffer` — `allocate`, `allocateDirect`, `wrap`, `slice`, `duplicate`, `asReadOnlyBuffer`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html
- `Buffer` — position, limit, capacity, mark, `flip`, `clear`, `rewind`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/Buffer.html
- `CharBuffer` — wrap mảng và CharSequence: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/CharBuffer.html
- `ReadOnlyBufferException`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ReadOnlyBufferException.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-buffer-009">Heap buffer, direct buffer và chi phí cấp phát</a>

<details>
<summary>Xem chi tiết</summary>

# Heap buffer, direct buffer và chi phí cấp phát trong Java NIO

Trong Java NIO, `ByteBuffer` là vùng nhớ có cấu trúc để đọc, ghi và chuyển byte giữa ứng dụng với file, socket hoặc những kênh dữ liệu khác. Hai cách tạo phổ biến là `ByteBuffer.allocate(capacity)` và `ByteBuffer.allocateDirect(capacity)`. Cả hai đều trả về `ByteBuffer` có các thao tác `put`, `get`, `flip`, `clear` như nhau, nhưng **vị trí vùng nhớ lưu dữ liệu, chi phí cấp phát, cách thu hồi và hiệu năng I/O có thể khác nhau**.

Lựa chọn đúng phụ thuộc vào vòng đời buffer, kích thước, tần suất cấp phát, kiểu xử lý dữ liệu và đường đi I/O cụ thể. Direct buffer không tự động nhanh hơn heap buffer, và việc cấp phát ngoài Java heap không có nghĩa bộ nhớ được giải phóng ngay khi gọi `clear()`.

## 1. Những phần của `ByteBuffer` giống nhau

Một `ByteBuffer` quản lý bốn giá trị:

| Thuộc tính | Ý nghĩa |
| --- | --- |
| `capacity()` | Sức chứa cố định theo byte, được chọn khi tạo buffer; không đổi khi `flip` hoặc `clear` |
| `limit()` | Mốc đầu tiên không được truy cập theo chế độ hiện tại |
| `position()` | Chỉ số của phần tử tiếp theo được đọc/ghi bằng phương thức tương đối |
| `mark()` / `reset()` | Ghi nhớ và quay lại một vị trí trong cùng buffer; mark có thể chưa được đặt |

Các bất biến là `0 <= position <= limit <= capacity`; mark, nếu được xác định, không vượt position. Một buffer mới từ `allocate(8)` hoặc `allocateDirect(8)` đều có `position=0`, `limit=8`, `capacity=8`. Nội dung ban đầu được khởi tạo bằng 0 theo hợp đồng API.

Chu trình ghi rồi đọc:

```java
ByteBuffer buffer = ByteBuffer.allocate(8);
buffer.put((byte) 10);       // position: 0 -> 1
buffer.put((byte) 20);       // position: 1 -> 2
buffer.flip();               // position=0, limit=2
int first = Byte.toUnsignedInt(buffer.get()); // 10, position=1
```

Đây là đoạn minh họa; `ByteBuffer` cần import từ `java.nio`. Nếu thay `allocate(8)` bằng `allocateDirect(8)`, các giá trị vị trí và kết quả đọc vẫn như vậy.

- `flip()` biến các byte vừa ghi thành vùng có thể đọc, đặt `limit` bằng position cũ và position về 0.
- `clear()` đặt position về 0, limit về capacity để chuẩn bị ghi tiếp; **không xóa byte**, không đổi loại buffer và không giải phóng bộ nhớ.
- `compact()` giữ các byte chưa đọc bằng cách chuyển chúng về đầu buffer, rồi đặt position sau phần dữ liệu còn lại; hữu ích khi xử lý một khung dữ liệu đến chưa đầy đủ.
- `get(index)` và `put(index, value)` truy cập tuyệt đối theo chỉ số của buffer, không tự di chuyển position.
- Buffer không tự thay đổi capacity: muốn có sức chứa lớn hơn phải cấp phát buffer khác và chuyển dữ liệu còn cần giữ.

**Capacity tính bằng byte**, không phải số ký tự. Một ký tự Unicode có thể được mã hóa thành nhiều byte UTF-8; chuyển đổi byte sang văn bản phải có charset và xử lý đúng phần dữ liệu hợp lệ từ position/limit.

## 2. Heap buffer được cấp phát thế nào?

`ByteBuffer.allocate(n)` tạo **non-direct byte buffer**, thường dựa trên một mảng `byte[]` thuộc Java heap. `ByteBuffer.wrap(byte[])` bọc mảng đã tồn tại thành một heap buffer mà **không chép mảng dữ liệu**. Đây là lựa chọn tự nhiên khi dữ liệu được tạo, sửa, kiểm tra hoặc thao tác bằng Java thông thường.

```java
ByteBuffer heap = ByteBuffer.allocate(1024);
ByteBuffer wrapped = ByteBuffer.wrap(new byte[1024]);

System.out.println(heap.isDirect()); // false
System.out.println(heap.hasArray());  // true
System.out.println(wrapped.isDirect()); // false
```

Đoạn này minh họa đặc tính buffer cấp phát/bọc theo cách trên. Với heap buffer thông thường có mảng truy cập được, `hasArray()` trả `true`, `array()` cung cấp mảng bên dưới và `arrayOffset()` giúp xác định vị trí tương ứng trong mảng đó.

Đừng mặc định mọi heap buffer đều cho gọi `array()`: **heap buffer chỉ đọc** (`asReadOnlyBuffer()`) có thể không cho truy cập mảng, `hasArray()` trả `false` và `array()` có thể ném `ReadOnlyBufferException`. Hãy kiểm tra `hasArray()` trước khi phụ thuộc vào API mảng.

### Chi phí chính của heap buffer

- **Cấp phát:** Mảng backing `byte[]` và đối tượng buffer được quản lý bởi JVM. Với các buffer nhỏ, cơ chế cấp phát heap tối ưu có thể làm thao tác này khá rẻ; đừng giả định luôn chỉ có một phép cấp phát hay không tốn chi phí.
- **Bộ nhớ và GC:** Dữ liệu nằm trong phần heap được quản lý, có thể làm tăng áp lực GC khi tạo nhiều mảng hoặc giữ nhiều mảng lớn.
- **Truy cập bằng Java:** Thuận tiện với `array()`, hệ sinh thái API dùng mảng, thao tác từng byte và xử lý trong JVM.
- **I/O với hệ điều hành:** Một số implementation/channel có thể cần copy qua bộ nhớ trung gian thích hợp cho native I/O. **Số bản sao và chi phí cụ thể không phải lời hứa chung của mọi API Java NIO.**

Nếu đã có dữ liệu trong `byte[]`, `ByteBuffer.wrap(existing)` có thể tránh tạo thêm một mảng dữ liệu mới. Tuy nhiên mảng và buffer chia sẻ nội dung, nên chỉnh mảng sẽ làm thay đổi byte nhìn qua buffer và ngược lại.

## 3. Direct buffer khác ở đâu?

`ByteBuffer.allocateDirect(n)` tạo **direct byte buffer**. Theo API Java, JVM cố gắng đặt nội dung của direct buffer vào bộ nhớ có thể phục vụ thao tác native I/O trực tiếp, tránh một số bước copy trung gian trên những đường I/O phù hợp. Trên các JVM phổ biến, bộ nhớ chứa byte của direct buffer thường là vùng **ngoài Java heap (off-heap/native memory)**; bản thân đối tượng `ByteBuffer` và metadata vẫn là đối tượng Java.

```java
ByteBuffer direct = ByteBuffer.allocateDirect(1024);

System.out.println(direct.isDirect()); // true
System.out.println(direct.hasArray());  // false
```

**Direct không đồng nghĩa với “không có đối tượng Java” hoặc “không chịu ảnh hưởng GC”.** Java vẫn phải theo dõi khả năng tiếp cận đối tượng buffer để có thể thu hồi tài nguyên phía native khi không còn được sử dụng. Direct buffer cũng không tự biến một thao tác `get()` lặp từng byte thành thao tác vector hóa nhanh hơn.

Các đặc điểm quan trọng:

1. **Chi phí tạo cao hơn trong nhiều tình huống:** JVM có thể phải đặt trước/commit bộ nhớ native, khởi tạo nội dung, tạo và theo dõi metadata dọn dẹp. Đặc biệt khi cấp phát rất nhiều direct buffer nhỏ trong vòng lặp, chi phí phân bổ và thu hồi có thể lấn át lợi ích I/O.
2. **Có thể giảm copy tại ranh giới native I/O:** Một số channel và provider tận dụng địa chỉ vùng nhớ direct; lợi ích rõ hơn với khối dữ liệu tương đối lớn và buffer được dùng lặp lại. Điều này **phụ thuộc implementation, nền tảng, loại channel và cách gọi**.
3. **Vẫn cần kiểm soát lifetime:** `clear()` và `flip()` không trả native memory về hệ điều hành. Buffer tồn tại lâu nếu còn được giữ qua field, queue, cache hoặc các view.
4. **Không cung cấp Java array backing:** `hasArray()` thường là `false`, nên các API yêu cầu `byte[]` có thể cần một lần copy riêng.
5. **Có thể chịu giới hạn direct memory của JVM:** Việc cấp phát thất bại có thể dẫn tới `OutOfMemoryError` liên quan direct-buffer memory **dù Java heap vẫn còn chỗ**.

Với HotSpot, tùy chọn JVM như `-XX:MaxDirectMemorySize=256m` đặt giới hạn cho một phần quản lý direct memory. Đây là tùy chọn triển khai JVM, không phải quy tắc quản lý bộ nhớ giống hệt trên mọi JVM. Dữ liệu native khác của tiến trình (stack, mã máy, thư viện, mmap, v.v.) còn nằm ngoài phạm vi riêng của tùy chọn này.

## 4. Bảng so sánh heap và direct buffer

| Khía cạnh | Heap buffer | Direct buffer |
| --- | --- | --- |
| Tạo buffer | `ByteBuffer.allocate(n)`, `wrap(array)` | `ByteBuffer.allocateDirect(n)` |
| `isDirect()` | `false` | `true` |
| Vùng chứa byte | Thường là mảng thuộc Java heap | Thường là vùng native/off-heap trên JVM phổ biến |
| `hasArray()` | Thường `true` nếu không phải read-only | `false` |
| Cấp phát thường xuyên số lượng nhỏ | Thường có lợi về chi phí tạo | Dễ tốn chi phí tạo/thu hồi hơn |
| Truy cập nội dung trong Java | Thuận tiện với mảng và API Java | Vẫn dùng `get/put`, nhưng không mở backing array |
| I/O native | Có thể phát sinh copy trung gian | Có thể giảm copy ở một số đường I/O |
| Thu hồi | Theo GC heap | Phụ thuộc lifetime/reachability và cơ chế thu hồi native memory của JVM |
| Khi giữ nhiều vùng lớn | Tăng áp lực Java heap và GC | Tăng native memory/RSS; vẫn có áp lực quản lý đối tượng và giới hạn riêng |
| Hiệu năng tổng thể | Phụ thuộc workload | Phụ thuộc workload; không có bảo đảm luôn nhanh hơn |

Hai loại đều có `position`, `limit`, `capacity`, byte order, khả năng tạo view và đọc/ghi bằng NIO channel. `ByteBuffer` mới dùng thứ tự byte mặc định `BIG_ENDIAN` khi đọc/ghi kiểu nhiều byte. Byte order chỉ ảnh hưởng diễn giải các số nhiều byte, **không quyết định buffer heap hay direct**.

## 5. Chương trình Java 17+ chạy được: so sánh tính chất và vùng nhớ chia sẻ

Chương trình sau không giả lập benchmark. Nó kiểm tra những hành vi **được hợp đồng API bảo đảm**, đồng thời minh họa mảng của heap buffer và view của direct buffer có thể cùng trỏ tới dữ liệu gốc.

Tạo tệp **`HeapAndDirectDemo.java`**:

```java
import java.nio.ByteBuffer;

public final class HeapAndDirectDemo {
    private HeapAndDirectDemo() {}

    public static void main(String[] args) {
        ByteBuffer heap = ByteBuffer.allocate(8);
        ByteBuffer direct = ByteBuffer.allocateDirect(8);
        ByteBuffer wrapped = ByteBuffer.wrap(new byte[8]);

        describe("heap", heap);
        describe("direct", direct);
        describe("wrapped", wrapped);

        heap.put(new byte[] {10, 20, 30, 40});
        direct.put(new byte[] {10, 20, 30, 40});

        System.out.println("heap after put: pos=" + heap.position()
                + ", remaining=" + heap.remaining());

        heap.flip();
        direct.flip();
        System.out.println("heap first=" + Byte.toUnsignedInt(heap.get()));
        System.out.println("heap position=" + heap.position());

        System.out.println("direct index 1=" +
                Byte.toUnsignedInt(direct.get(1)));
        System.out.println("direct position=" + direct.position());

        heap.array()[0] = 99;
        System.out.println("heap after array edit=" +
                Byte.toUnsignedInt(heap.get(0)));

        ByteBuffer directView = direct.slice();
        directView.put(0, (byte) 77);
        System.out.println("direct view isDirect=" + directView.isDirect());
        System.out.println("direct after view edit=" +
                Byte.toUnsignedInt(direct.get(0)));

        ByteBuffer readOnly = direct.asReadOnlyBuffer();
        System.out.println("readOnly direct=" + readOnly.isDirect()
                + ", hasArray=" + readOnly.hasArray());
        System.out.println("readOnly first=" +
                Byte.toUnsignedInt(readOnly.get(0)));

        direct.clear();
        System.out.println("direct after clear: pos=" + direct.position()
                + ", limit=" + direct.limit());
    }

    private static void describe(String label, ByteBuffer buffer) {
        System.out.println(label + ": isDirect=" + buffer.isDirect()
                + ", hasArray=" + buffer.hasArray()
                + ", capacity=" + buffer.capacity());
    }
}
```

Biên dịch và chạy:

```bash
javac HeapAndDirectDemo.java
java HeapAndDirectDemo
```

Kết quả:

```text
heap: isDirect=false, hasArray=true, capacity=8
direct: isDirect=true, hasArray=false, capacity=8
wrapped: isDirect=false, hasArray=true, capacity=8
heap after put: pos=4, remaining=4
heap first=10
heap position=1
direct index 1=20
direct position=0
heap after array edit=99
direct view isDirect=true
direct after view edit=77
readOnly direct=true, hasArray=false
readOnly first=77
direct after clear: pos=0, limit=8
```

**Phân tích:**

- Ba buffer có cùng capacity 8 nhưng `isDirect` và `hasArray` khác nhau. `wrap` cho heap buffer và chia sẻ mảng được bọc.
- Sau `put` 4 byte, position tăng từ 0 lên 4, remaining bằng 4. `flip()` giới hạn phần đọc ở 4 byte; `heap.get()` làm position tăng lên 1.
- `direct.get(1)` dùng index tường minh nên `direct.position()` vẫn bằng 0.
- `heap.array()[0] = 99` sửa trực tiếp mảng backing, lập tức phản ánh qua `heap.get(0)`.
- `direct.slice()` tạo một view **dùng chung nội dung** với direct buffer gốc; sửa `directView.put(0, ...)` làm giá trị nhìn qua `direct` cũng đổi. `slice` có position/limit riêng và vẫn là direct.
- `asReadOnlyBuffer()` cho một view không được ghi, vẫn chia sẻ nội dung với đối tượng gốc và vẫn là direct. Nó không tạo snapshot bất biến.
- `direct.clear()` đặt lại trạng thái để ghi nhưng **không chứng minh vùng native được giải phóng hoặc byte đã được xóa**.

Kết quả trên thể hiện thuộc tính API; **không dùng thời gian chạy của chương trình này để kết luận heap/direct bên nào nhanh hơn**.

## 6. Phân biệt chi phí cấp phát, chi phí truy cập và chi phí I/O

Nhiều so sánh heap/direct gây nhầm lẫn vì gộp mọi chi phí vào một con số.

### 6.1. Chi phí cấp phát (`allocation cost`)

Đây là thời gian và tài nguyên cần để có một buffer đủ dung lượng:

```java
ByteBuffer h = ByteBuffer.allocate(64 * 1024);
ByteBuffer d = ByteBuffer.allocateDirect(64 * 1024);
```

Đây là đoạn minh họa hai buffer 64 KiB, không phải phép đo. Cả hai phương thức đều phải cấp vùng chứa byte được khởi tạo, nhưng đường cấp phát khác nhau. Với một buffer dùng đúng một lần và chỉ xử lý dữ liệu trong Java, lợi ích giảm copy I/O của direct có thể không đủ để bù thời gian cấp phát ban đầu.

Trong trường hợp cấp phát nhiều lần (ví dụ tạo buffer mới cho từng packet 1 KiB), chi phí gồm tạo đối tượng, khởi tạo vùng nhớ, cấp phát/thu hồi native memory và áp lực garbage collection. Đừng xây đường xử lý hiệu năng cao bằng cách gọi `allocateDirect` cho mọi sự kiện mà chưa đo.

### 6.2. Chi phí đọc/ghi byte trong Java (`access cost`)

Các thao tác `buffer.get()`, `buffer.put()`, `getInt()`, `putLong()` vẫn cần kiểm tra chỉ số, truy cập vùng nhớ và có thể chịu ảnh hưởng CPU cache, JIT, kích thước khối, thứ tự byte, căn chỉnh dữ liệu và kiểu vòng lặp.

Heap buffer không luôn thắng các phép đọc/ghi trong Java; direct buffer cũng không luôn thắng. Việc lấy backing array để làm việc với thư viện chỉ hỗ trợ `byte[]` có thể làm heap buffer thuận tiện hơn, trong khi direct buffer cần bước chép dữ liệu.

### 6.3. Chi phí chuyển dữ liệu tới/từ kênh I/O (`transfer cost`)

`FileChannel.read(ByteBuffer)`, `FileChannel.write(ByteBuffer)` và các API NIO khác làm việc với cả hai loại buffer. Trên một số nền tảng và provider, JVM có thể tận dụng direct buffer để giao dữ liệu cho native I/O mà tránh một bản copy từ heap tới buffer tạm.

Tuy nhiên, **direct buffer không đồng nghĩa với zero-copy I/O**. Vẫn có thể tồn tại những lần sao chép giữa kernel và user space, trong trình điều khiển, ở lớp mạng/TLS, hoặc trong triển khai channel cụ thể. `FileChannel.transferTo/transferFrom` là họ API khác, và `MappedByteBuffer` dùng memory mapping; không nên đánh đồng các cơ chế này với `allocateDirect`.

Lợi ích tiềm năng thường tăng khi lượng byte chuyển lớn, buffer được tái sử dụng và chi phí copy nằm trên đường nóng. Một lần đọc tệp nhỏ hay đoạn xử lý nhiều thao tác tính toán trên mảng có thể không nhận được lợi ích đó.

## 7. Tái sử dụng buffer để giảm chi phí lặp lại

Thay vì tạo direct buffer cho mỗi lần đọc/ghi, một thành phần sở hữu I/O có thể tạo buffer **một lần** và dùng lặp:

```java
// Đoạn minh họa trong phương thức xử lý IOException.
// channel là ReadableByteChannel có dữ liệu, được sở hữu/đóng ở ngoài.
ByteBuffer buffer = ByteBuffer.allocateDirect(64 * 1024);
while (true) {
    buffer.clear();
    int n = channel.read(buffer);
    if (n == -1) {
        break;
    }
    if (n == 0) {
        // Tùy channel/blocking mode, xử lý trường hợp chưa có dữ liệu.
        continue;
    }
    buffer.flip();
    while (buffer.hasRemaining()) {
        processByte(buffer.get()); // Hàm nghiệp vụ minh họa.
    }
}
```

Đoạn trên chỉ diễn tả chu trình reuse; `processByte` là hàm nghiệp vụ giả định, không phải chương trình biên dịch độc lập. Trong xử lý thực tế, không nên spin vô hạn khi `read()` liên tục trả 0 trên non-blocking channel; cần dùng selector/cơ chế chờ hoặc scheduling phù hợp.

Một **buffer pool** có thể quản lý nhiều vùng direct memory tái sử dụng cho các kết nối hoặc request. Nhưng pool cũng có chi phí: lock/đồng bộ, tranh chấp, phân mảnh theo kích cỡ, lượng bộ nhớ giữ nhàn rỗi, và nguy cơ cấp nhầm một buffer đang được thread khác sử dụng.

Thiết kế pool cần làm rõ:

- Ai sở hữu buffer, khi nào được mượn và hoàn trả, và có được dùng đồng thời hay không.
- Giới hạn **số lượng × dung lượng**, tránh tăng bộ nhớ native vô hạn khi tải đột biến.
- Cách đặt `position/limit` đúng khi tái sử dụng; `clear()` không làm sạch dữ liệu cũ. Nếu dữ liệu nhạy cảm, cần chính sách xóa/ghi đè thực sự trước khi chuyển quyền sở hữu.
- Xử lý trường hợp dữ liệu chưa được tiêu thụ hết bằng `compact()` hoặc thiết kế phân đoạn phù hợp; không `clear()` làm mất phần chưa đọc.
- View từ `slice()`, `duplicate()` hoặc `asReadOnlyBuffer()` vẫn có thể tham chiếu cùng nội dung, vì vậy không hoàn trả gốc để người khác ghi đè khi view còn đang được dùng.

Đối với buffer nhỏ và ngắn hạn, cấp phát heap đơn giản có thể hợp lý hơn nhiều so với xây pool direct buffer phức tạp.

## 8. Thu hồi bộ nhớ và các loại lỗi thiếu bộ nhớ

Tài nguyên của direct buffer thường gắn với khả năng tiếp cận đối tượng Java đại diện; JVM quản lý cách dọn dẹp native memory khi buffer không còn được sử dụng. **Không có thời điểm giải phóng tức thì được đảm bảo chỉ vì một biến cục bộ ra khỏi scope**. Các view và tham chiếu giữ trong queue/cache có thể kéo dài lifetime của vùng byte.

Đừng dùng những cách sau như phương thức giải phóng:

- `buffer.clear()`: chỉ đặt lại position/limit.
- `buffer.flip()`: chỉ chuyển trạng thái ghi sang đọc.
- Gán `buffer = null` và kỳ vọng native memory được thu hồi ngay.
- Gọi thủ công các API nội bộ như `Cleaner` của JDK qua reflection để ép giải phóng: cách này phụ thuộc triển khai, có thể phá tính an toàn bộ nhớ nếu còn view hoặc công việc I/O đang dùng buffer.

Hai tình huống lỗi là khác nhau:

| Hiện tượng | Gợi ý nguyên nhân |
| --- | --- |
| `OutOfMemoryError: Java heap space` | Áp lực vùng Java heap, có thể do nhiều heap buffer/mảng và các đối tượng khác |
| `OutOfMemoryError` liên quan `direct buffer memory` | Vượt giới hạn/quỹ bộ nhớ direct do JVM quản lý hoặc không thu hồi kịp các buffer cũ |

Câu chữ chính xác của `OutOfMemoryError` phụ thuộc JVM và phiên bản. Ngoài hai lỗi này, tiến trình còn có thể bị giới hạn bởi tổng bộ nhớ của hệ điều hành/container. Cấp phát direct có thể giảm áp lực lưu byte trên heap nhưng **không xóa bỏ giới hạn tổng RAM** và không bảo đảm không xảy ra pause do GC.

### Theo dõi qua JVM

Với JVM có hỗ trợ, `BufferPoolMXBean` cho biết các bộ đệm trong pool như `direct` và `mapped`. Ví dụ sau là **đoạn minh họa trong chương trình Java có import tương ứng**:

```java
for (java.lang.management.BufferPoolMXBean pool :
        java.lang.management.ManagementFactory.getPlatformMXBeans(
                java.lang.management.BufferPoolMXBean.class)) {
    if ("direct".equals(pool.getName())) {
        System.out.println("count=" + pool.getCount());
        System.out.println("capacity=" + pool.getTotalCapacity());
        System.out.println("memoryUsed=" + pool.getMemoryUsed());
    }
}
```

Các số này hữu ích theo dõi xu hướng nhưng **không đại diện chính xác cho toàn bộ native memory/RSS của tiến trình**; một số thống kê có thể không được hỗ trợ trên nền tảng cụ thể (ví dụ báo `-1`). Khi phân tích bộ nhớ, nên xem cùng Java heap, GC, lượng request đang chạy, giới hạn container và chỉ số hệ điều hành.

## 9. Đo hiệu năng đúng câu hỏi

Ví dụ ở phần 5 là kiểm chứng tính năng, **không phải benchmark**. Một phép đo có ích cần tách ít nhất ba câu hỏi:

1. **Cấp phát:** Mất bao nhiêu thời gian và bao nhiêu bộ nhớ khi tạo/thu hồi 1.000 buffer 4 KiB, 64 KiB hoặc 1 MiB? Phải đo cùng số lượng, dung lượng và điều kiện GC.
2. **Tái sử dụng và truy cập:** Với buffer đã cấp phát sẵn, thời gian đọc/ghi theo block, theo byte hoặc theo kiểu số liệu khác nhau thế nào? Tránh để trình biên dịch JIT loại bỏ công việc không quan sát được.
3. **I/O thực tế:** Khi đọc/ghi cùng lượng dữ liệu qua `FileChannel` hoặc socket, throughput, latency, CPU usage và số lần copy thay đổi ra sao? Xét giới hạn thiết bị, cache hệ điều hành và mức đồng thời.

Đối với microbenchmark, ưu tiên **JMH** để xử lý warm-up, fork JVM, đo và tránh nhiều bẫy tối ưu hóa. Với workload I/O, cần thêm end-to-end benchmark trên môi trường phù hợp. Không nên đo một vòng `System.nanoTime()` duy nhất rồi công bố kết luận tổng quát, vì thời gian khởi động JIT, tải hệ thống, GC và trạng thái cache có thể chi phối kết quả.

Khi chạy so sánh cần cố định hoặc báo rõ: phiên bản JDK/JVM, cấu hình heap/direct memory, CPU/hệ điều hành, loại channel, kích thước buffer, việc tái sử dụng, số luồng, blocking/non-blocking, lượng dữ liệu, lần chạy lặp và sai số. Mục tiêu cuối cùng là đo **thời gian hoàn thành công việc của ứng dụng**, không chỉ đo một phép cấp phát rời rạc.

## 10. Chọn heap hay direct theo tình huống

| Tình huống | Hướng chọn ban đầu | Lý do cần kiểm chứng |
| --- | --- | --- |
| Parse một thông điệp nhỏ vừa tạo trong Java | Heap / `wrap(byte[])` | Truy cập mảng tiện, tránh cấp phát native mới cho thao tác ngắn |
| Xử lý mảng byte trong thư viện cũ | Heap | API yêu cầu `byte[]`, direct có thể phải chép dữ liệu |
| Đọc/ghi I/O khối lớn và liên tục | Xem xét direct tái sử dụng | Có khả năng giảm copy ở đường I/O thích hợp |
| Server có nhiều socket, buffer dùng nhiều lần | Xem xét pool direct có giới hạn | Cân bằng chi phí cấp phát và thời gian giữ native memory |
| Buffer tồn tại lâu với lượng dữ liệu lớn | Cần cân đối heap/direct | Heap gây áp lực GC; direct tiêu thụ native memory và khó dự đoán thời điểm thu hồi |
| Ít dữ liệu, throughput không quan trọng | Heap trước | Đơn giản, dễ quản lý hơn; tối ưu hóa chỉ sau khi đo |
| Cần chia sẻ một vùng byte qua nhiều view | Cả hai đều hỗ trợ | Phải quản lý alias, thread-safety và vòng đời buffer |

Không nên coi dung lượng **64 KiB**, **1 MiB** hoặc một ngưỡng cố định nào là ranh giới chính xác mà trên đó direct luôn nhanh hơn. Các con số trong ví dụ chỉ là kích thước minh họa, không phải khuyến nghị phổ quát.

## 11. Lỗi thường gặp và cách sửa

| Lỗi | Hệ quả | Cách xử lý |
| --- | --- | --- |
| Tạo `ByteBuffer.allocateDirect` trong mọi vòng lặp xử lý gói tin | Tăng chi phí cấp phát và áp lực native memory | Tái sử dụng buffer hoặc đo để cân nhắc heap buffer |
| Nghĩ direct buffer luôn là zero-copy | Kỳ vọng throughput sai trên những provider/I/O path khác nhau | Đo workload thật, phân biệt direct, transferTo và mapped buffer |
| Gọi `array()` trên direct buffer | `UnsupportedOperationException` | Kiểm tra `hasArray()`, dùng `get/put` hoặc copy có chủ đích |
| Nghĩ `clear()` sẽ xóa dữ liệu hoặc giải phóng vùng native | Rò rỉ dữ liệu qua buffer pool, ước lượng sai bộ nhớ | Phân biệt reset trạng thái, xóa byte thật và thu hồi bộ nhớ |
| Giữ `slice()` sau khi trả buffer gốc vào pool | View thấy byte bị chủ sở hữu tiếp theo ghi đè | Ràng buộc lifetime của các view với tài nguyên gốc |
| Benchmark chỉ một lần, không warm-up | Kết quả phản ánh JIT/GC/cache hơn là workload | Dùng JMH và benchmark tổng thể, báo môi trường |
| Nghĩ JVM heap còn trống thì direct allocation không thể lỗi | Có thể gặp `OutOfMemoryError` do direct memory hoặc giới hạn process | Đặt ngân sách native memory, theo dõi direct buffer pool và RSS |
| Coi direct buffer là thread-safe mặc định | Race khi nhiều luồng sửa cùng byte/position/limit | Dùng quyền sở hữu độc lập hoặc đồng bộ phù hợp |
| Nhầm `flip()` với `clear()` | Đọc byte chưa ghi hoặc làm mất trạng thái cần xử lý | Theo đúng chu trình write → flip → read → compact/clear |

**Ghi nhớ:** Heap buffer ưu tiên sự đơn giản, cấp phát thường rẻ và dễ phối hợp với mảng Java. Direct buffer có thể hữu ích cho I/O lớn và được tái sử dụng, nhưng phải trả chi phí quản lý/cấp phát native memory và không đảm bảo nhanh hơn trong mọi trường hợp. Hãy chọn dựa trên **đường dữ liệu thực, vòng đời buffer và số đo phù hợp**; đừng suy luận tốc độ chỉ từ `isDirect()`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-buffer-010">ByteOrder và các view buffer cho kiểu nguyên thủy</a>

<details>
<summary>Xem chi tiết</summary>

# ByteOrder và các view buffer cho kiểu nguyên thủy trong Java NIO

## 1. Vấn đề cần giải quyết

Khi trao đổi dữ liệu nhị phân, một dãy byte có thể biểu diễn các giá trị khác nhau tùy **thứ tự byte (byte order/endian)** và **kiểu dữ liệu** dùng để giải mã. Java NIO cung cấp `ByteBuffer` để chứa byte, `ByteOrder` để quy định cách kết hợp byte thành giá trị nhiều byte, và các **view buffer** như `IntBuffer`, `ShortBuffer`, `FloatBuffer` để đọc/ghi nhiều phần tử cùng kiểu trên chính vùng dữ liệu đó.

Ba câu hỏi cần phân biệt:

1. **Byte order:** một giá trị `int`, `short`, `long`, `float`, `double`, `char` được chuyển thành các byte theo thứ tự nào?
2. **Kiểu nhìn (view):** ta đang đọc vùng nhớ chứa byte như các phần tử kiểu gì và mỗi phần tử chiếm bao nhiêu byte?
3. **Trạng thái buffer:** `position`, `limit`, `capacity` và `mark` của view liên quan thế nào đến buffer nguồn?

Câu trả lời quan trọng: **view chia sẻ nội dung byte, nhưng có trạng thái vị trí độc lập; byte order của view được chốt vào lúc tạo view**.

## 2. Big-endian và little-endian

`java.nio.ByteOrder` có hai hằng:

- `ByteOrder.BIG_ENDIAN`: lưu byte có trọng số cao trước.
- `ByteOrder.LITTLE_ENDIAN`: lưu byte có trọng số thấp trước.

Xét giá trị nguyên 32-bit `0x01020304`:

| Thứ tự byte | Offset 0 | Offset 1 | Offset 2 | Offset 3 |
|---|---|---|---|---|
| Big-endian | `01` | `02` | `03` | `04` |
| Little-endian | `04` | `03` | `02` | `01` |

Đây là **thứ tự lưu trữ byte của cùng một giá trị**, không phải phép đảo các bit bên trong mỗi byte. Endianness chỉ ảnh hưởng đến việc ghép nhiều byte; một thao tác `get()`/`put(byte)` với **một byte** không có khác biệt big/little-endian.

Ví dụ minh họa:

~~~java
ByteBuffer big = ByteBuffer.allocate(4);
big.putInt(0x01020304); // 01 02 03 04

ByteBuffer little = ByteBuffer.allocate(4);
little.order(ByteOrder.LITTLE_ENDIAN);
little.putInt(0x01020304); // 04 03 02 01
~~~

Đây là **đoạn mã minh họa** khi đã import `java.nio.ByteBuffer` và `java.nio.ByteOrder`. Dòng comment nói về nội dung byte, không phải chuỗi được in tự động.

### Mặc định của ByteBuffer

Mọi `ByteBuffer` **mới tạo** có thứ tự byte ban đầu là **`BIG_ENDIAN`** theo hợp đồng Java 17, kể cả được tạo bằng `allocate(...)`, `allocateDirect(...)` hoặc `wrap(...)`.

~~~java
ByteBuffer buffer = ByteBuffer.allocate(8);
System.out.println(buffer.order()); // BIG_ENDIAN

buffer.order(ByteOrder.LITTLE_ENDIAN);
System.out.println(buffer.order()); // LITTLE_ENDIAN
~~~

`ByteBuffer.order(ByteOrder)` thay đổi cách buffer **đọc/ghi các giá trị nhiều byte từ thời điểm đó**. Lệnh không tự đảo các byte đã lưu. Với cùng bốn byte `01 02 03 04`, `getInt()` theo big-endian đọc là `0x01020304`, theo little-endian đọc là `0x04030201`.

### ByteOrder.nativeOrder() có phải lựa chọn mặc định?

Không. `ByteOrder.nativeOrder()` trả thứ tự byte của nền tảng chạy JVM. Nó hữu ích khi tối ưu thao tác với bộ nhớ hoặc thư viện native, nhưng **không thay đổi mặc định BIG_ENDIAN của ByteBuffer mới tạo** và không phải tiêu chuẩn của file/network protocol.

**Nguyên tắc:** định dạng nhị phân quy định endian nào thì chọn đúng endian đó, bất kể `nativeOrder()` trên máy phát triển là gì. Trong một số giao thức mạng, các trường nhiều byte dùng big-endian; một số định dạng file khác dùng little-endian.

## 3. ByteBuffer đọc/ghi primitive nhiều byte

`ByteBuffer` hỗ trợ thao tác tương ứng với các primitive: `char` (2 byte), `short` (2), `int` (4), `long` (8), `float` (4), `double` (8). Không có `getBoolean()` hay `asBooleanBuffer()` tương ứng.

Ví dụ:

~~~java
ByteBuffer b = ByteBuffer.allocate(12)
        .order(ByteOrder.LITTLE_ENDIAN);
b.putShort((short) 0x1234); // chiếm 2 byte
b.putInt(0x01020304);       // chiếm 4 byte
b.putFloat(1.5f);           // chiếm 4 byte
b.flip();

short s = b.getShort();
int i = b.getInt();
float f = b.getFloat();
~~~

**Lưu ý:** `ByteBuffer.getInt(int index)` dùng chỉ số **theo byte**, nên `getInt(4)` đọc 4 byte bắt đầu tại vị trí byte số 4; đối lập với `IntBuffer.get(4)` đọc **phần tử int thứ 5** của view (offset 16 byte tính từ đầu view).

Hai dạng thao tác thường gặp:

- **Relative:** `getInt()`, `putInt(value)` sử dụng và cập nhật `position` theo số byte đọc/ghi (4 đối với int).
- **Absolute:** `getInt(index)`, `putInt(index, value)` dùng offset byte chỉ định và **không thay đổi position**.

Các thao tác tương tự áp dụng cho short, long, float, double và char. Khi không đủ byte còn lại cho thao tác relative, có thể ném `BufferUnderflowException` hoặc `BufferOverflowException`; thao tác absolute có index không hợp lệ ném `IndexOutOfBoundsException`. Ghi vào read-only buffer ném `ReadOnlyBufferException`.

`flip()` chỉ đặt `limit` thành vị trí ghi hiện tại và đưa `position` về 0, để chuẩn bị đọc. Nó **không tự đổi endian**.

## 4. View buffer cho các primitive là gì?

Một view buffer là một buffer kiểu primitive được tạo **trên nội dung byte đang có** của một `ByteBuffer`, qua:

| Phương thức trên ByteBuffer | Kiểu view | Số byte/phần tử |
|---|---|---:|
| `asCharBuffer()` | `CharBuffer` | 2 |
| `asShortBuffer()` | `ShortBuffer` | 2 |
| `asIntBuffer()` | `IntBuffer` | 4 |
| `asLongBuffer()` | `LongBuffer` | 8 |
| `asFloatBuffer()` | `FloatBuffer` | 4 |
| `asDoubleBuffer()` | `DoubleBuffer` | 8 |

Không có `asByteBuffer()` vì đối tượng gốc vốn đã là `ByteBuffer`; cũng không có view cho boolean.

Một view cung cấp API tiện lợi của kiểu phần tử đó, như `IntBuffer.get()`, `put(int)`, `get(int index)` và bulk operations `get(int[])` / `put(int[])`, thay vì phải tự đọc từng cụm 4 byte. **Tạo view không tự sao chép nội dung:** cả view và buffer byte cùng truy cập một vùng dữ liệu. Thay đổi ở view được quan sát từ buffer gốc và ngược lại.

### Bảng về nội dung và trạng thái

| Thuộc tính | Quan hệ giữa view và ByteBuffer gốc |
|---|---|
| Nội dung byte | **Chia sẻ**; thao tác ghi ảnh hưởng cả hai cách nhìn |
| `position`, `limit`, `mark` | **Độc lập**; đọc/ghi tương đối trên view không đẩy position của ByteBuffer |
| Điểm bắt đầu của view | ByteBuffer.`position()` **tại lúc tạo view** |
| Dung lượng view | `floor(ByteBuffer.remaining() / bytesPerElement)` |
| Position/limit mới | `position=0`, `limit=capacity`; mark chưa được đặt |
| Byte order | Chốt theo `ByteBuffer.order()` tại thời điểm tạo view |
| Direct | View là direct khi và chỉ khi buffer byte gốc direct |
| Read-only | View là read-only khi và chỉ khi buffer byte gốc read-only |

Ví dụ `ByteBuffer` có `position=2`, `limit=13` thì `remaining=11` byte. Tạo `asIntBuffer()` cho `capacity=floor(11/4)=2` số nguyên, dùng 8 byte bắt đầu ở offset 2. **3 byte lẻ còn lại không thuộc view IntBuffer này**; chúng vẫn tồn tại trong ByteBuffer gốc.

Nếu sau khi tạo view bạn di chuyển `position` hoặc `limit` của buffer nguồn, view đã tạo **không tự đổi kích thước hay vị trí bắt đầu**. Nếu muốn view ở vùng khác, hãy chỉnh buffer byte và tạo view mới.

### Byte order của view là cố định

~~~java
ByteBuffer bytes = ByteBuffer.allocate(4);
bytes.order(ByteOrder.LITTLE_ENDIAN);
IntBuffer first = bytes.asIntBuffer();  // cố định LITTLE_ENDIAN

bytes.order(ByteOrder.BIG_ENDIAN);
IntBuffer second = bytes.asIntBuffer(); // cố định BIG_ENDIAN
~~~

Cả `first` và `second` đọc trên cùng 4 byte, nhưng diễn giải các byte theo thứ tự khác nhau. `first.order()` trả `LITTLE_ENDIAN`, `second.order()` trả `BIG_ENDIAN`. Những view primitive này có getter `order()`, **không có setter `order(ByteOrder)`** tương tự ByteBuffer. Muốn một cách đọc khác, hãy cấu hình byte buffer và tạo view mới.

## 5. Minh họa vị trí và chia sẻ bộ nhớ

Giả sử vùng byte là:

~~~text
offset:      0   1   2   3   | 4   5   6   7
byte LE:    04  03  02  01  | 0D  0C  0B  0A
IntBuffer: [      int[0]    |     int[1]     ]
giá trị LE:       01020304  |     0A0B0C0D
giá trị BE:       04030201  |     0D0C0B0A
~~~

Tạo view từ `ByteBuffer.position()==0` và `limit()==8` cho `IntBuffer` có 2 phần tử. Nếu ghi `0x11223344` qua **view LITTLE_ENDIAN** tại index 1, bốn byte thứ hai thành `44 33 22 11`; view BIG_ENDIAN đọc lại cùng vị trí sẽ nhận `0x44332211`.

Dù hai view và ByteBuffer chia sẻ vùng nhớ, việc gọi `little.get(0)` hay `little.put(1, value)` theo **absolute index** không thay đổi bất kỳ `position` nào. Nếu dùng `little.get()` hoặc `little.put(value)` tương đối, chỉ position của `little` thay đổi.

## 6. Ví dụ Java 17+ đầy đủ, chạy được

Ví dụ sau kiểm chứng byte order của hai view, sự chia sẻ dữ liệu, trạng thái position và view từ một vị trí byte không bằng 0.

**Tệp: `ByteOrderViewDemo.java`**

~~~java
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.HexFormat;

public class ByteOrderViewDemo {
    public static void main(String[] args) {
        ByteBuffer bytes = ByteBuffer.allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(0x01020304);
        bytes.putInt(0x0A0B0C0D);
        bytes.flip();

        IntBuffer little = bytes.asIntBuffer();
        System.out.println(HexFormat.of().formatHex(bytes.array()));
        System.out.printf("LE[0]=%08X%n", little.get(0));

        bytes.order(ByteOrder.BIG_ENDIAN);
        IntBuffer big = bytes.asIntBuffer();
        System.out.printf("BE[0]=%08X%n", big.get(0));
        System.out.println("orders=" +
                little.order() + "," + big.order());

        little.put(1, 0x11223344);
        System.out.println(HexFormat.of().formatHex(bytes.array()));
        System.out.printf("BE[1]=%08X%n", big.get(1));

        System.out.println("positions=" + bytes.position()
                + "," + little.position() + "," + big.position());

        bytes.position(2);
        ShortBuffer shorts = bytes.asShortBuffer();
        System.out.println("shortCapacity=" + shorts.capacity());
        System.out.printf("BE short=%04X%n", shorts.get(0) & 0xFFFF);
    }
}
~~~

Biên dịch/chạy (JDK 17+):

~~~bash
javac ByteOrderViewDemo.java
java ByteOrderViewDemo
~~~

Stdout:

~~~text
040302010d0c0b0a
LE[0]=01020304
BE[0]=04030201
orders=LITTLE_ENDIAN,BIG_ENDIAN
0403020144332211
BE[1]=44332211
positions=0,0,0
shortCapacity=3
BE short=0201
~~~

Giải thích chi tiết:

1. `putInt` trong mode little-endian ghi các byte `04 03 02 01 0D 0C 0B 0A`. `HexFormat` dùng để hiển thị byte rõ ràng, không phụ thuộc endian của phần cứng.
2. `little` đọc bốn byte đầu thành `01020304`; `big` được tạo **sau khi đổi order** nên đọc cùng byte thành `04030201`.
3. `little.put(1, 0x11223344)` ghi `44 33 22 11` vào vùng byte thứ hai. Vì nội dung chia sẻ, `big.get(1)` nhận `44332211`.
4. Các lệnh `get(index)`/`put(index, value)` là **absolute**, nên cả ba position vẫn bằng 0.
5. Đổi `bytes.position(2)` rồi gọi `asShortBuffer()` tạo view bắt đầu từ offset byte 2, còn 6 byte ⇒ 3 short. Buffer byte khi ấy đang big-endian; hai byte `02 01` thành `0x0201`.
6. `bytes.array()` hợp lệ trong ví dụ vì `ByteBuffer.allocate(...)` cấp phát heap buffer có mảng có thể truy cập. Với **direct buffer** hoặc **read-only buffer**, không được tự tin gọi `array()`; hãy kiểm tra `hasArray()` hoặc đọc qua API buffer.

Không có bản sao dữ liệu tự động nào được tạo bởi `asIntBuffer()` và `asShortBuffer()` trong ví dụ.

## 7. View khác gì so với các cách dùng buffer gần giống?

### 7.1. `getInt()` khác `asIntBuffer().get()`

Giả sử `ByteBuffer` có 12 byte, position bằng 0:

~~~java
int x = bytes.getInt();           // đọc 4 byte, bytes.position += 4
IntBuffer view = bytes.asIntBuffer(); // bắt đầu ở bytes.position hiện tại (4)
int y = view.get();               // đọc 1 int, view.position += 1
// bytes.position không tăng thêm khi view.get() chạy
~~~

Đây là đoạn minh họa trong một phạm vi đã có biến `bytes` chứa dữ liệu thích hợp. Nếu mục tiêu là view toàn bộ 12 byte, phải đưa ByteBuffer về đúng position và limit trước khi tạo view (chẳng hạn `bytes.position(0)` nếu dữ liệu hợp lệ đang từ byte 0).

Nên dùng các lệnh `getInt`/`putShort`/`getDouble` trực tiếp khi **dữ liệu pha trộn nhiều kiểu theo một schema**; dùng typed view khi **có nhiều phần tử liên tiếp cùng kiểu**, muốn indexing theo phần tử và bulk operations.

### 7.2. `slice()` và `duplicate()` không giống typed view

- `ByteBuffer.slice()` tạo một **ByteBuffer** mới chia sẻ nội dung bắt đầu từ position hiện tại của buffer nguồn, với position mới bằng 0.
- `ByteBuffer.duplicate()` tạo một **ByteBuffer** khác chia sẻ toàn vùng byte, sao chép trạng thái position/limit/mark ban đầu.
- `asIntBuffer()` tạo **IntBuffer** có đơn vị phần tử 4 byte, capacity dựa trên remaining và endian được chốt theo order tại lúc tạo.

**Bẫy dễ gặp trong Java 17:** `ByteBuffer.slice()`, `duplicate()`, `asReadOnlyBuffer()` trả về **ByteBuffer mới có order mặc định BIG_ENDIAN**, ngay cả khi nguồn đang LITTLE_ENDIAN. Vì thế phải đặt lại order nếu mục tiêu là dùng endian của nguồn:

~~~java
ByteBuffer original = ByteBuffer.allocate(8)
        .order(ByteOrder.LITTLE_ENDIAN);

ByteBuffer part = original.slice();
part.order(original.order()); // cần đặt lại theo giao thức

IntBuffer ints = part.asIntBuffer(); // lúc này LITTLE_ENDIAN
~~~

Đây là đoạn minh họa. Đừng kết luận mọi kiểu buffer hoặc mọi bản Java đều sao chép order theo cách intuitively mong đợi: hãy dựa vào hợp đồng phương thức cụ thể.

### 7.3. `asCharBuffer()` không phải bộ giải mã UTF-8

`CharBuffer` nhìn mỗi **2 byte thành một đơn vị mã UTF-16 `char`** theo endian đã chọn. Nếu dữ liệu là UTF-8 (một ký tự có thể dài 1–4 byte), không thể giải mã bằng `asCharBuffer()`.

Để giải mã dữ liệu văn bản, dùng `StandardCharsets.UTF_8.decode(byteBuffer)` hoặc `CharsetDecoder` phù hợp. Typed view giúp **reinterpret dữ liệu nhị phân kiểu cố định**, không thay thế charset decoder.

### 7.4. Native byte order, direct buffer và hiệu năng

View có cùng tính chất **direct** như `ByteBuffer` gốc. `allocateDirect` có thể phù hợp cho I/O native hoặc truy cập bulk hiệu quả nhưng tốn chi phí cấp phát/thu hồi cao hơn trong một số trường hợp. `ByteOrder.nativeOrder()` có thể có lợi cho thao tác native trên direct buffers, nhưng **phải ưu tiên endian của định dạng tệp/giao thức**. Không tự ý chọn nativeOrder chỉ vì muốn nhanh.

Typed view và byte buffer **không thread-safe**. Nếu nhiều luồng thay đổi cùng nội dung qua các view, phải có đồng bộ thích hợp; các position độc lập không bảo đảm an toàn dữ liệu chia sẻ.

## 8. Quy trình xử lý một định dạng binary thực tế

Giả sử giao thức quy định header 2 byte big-endian, sau đó 4 số nguyên little-endian. Có thể đọc rõ ràng bằng:

~~~java
ByteBuffer packet = /* buffer chứa ít nhất 18 byte hợp lệ */;
packet.order(ByteOrder.BIG_ENDIAN);
short header = packet.getShort();

packet.order(ByteOrder.LITTLE_ENDIAN);
IntBuffer values = packet.asIntBuffer(); // bắt đầu sau header
if (values.remaining() < 4) {
    throw new IllegalArgumentException("Thiếu dữ liệu");
}
for (int i = 0; i < 4; i++) {
    int value = values.get();
    // Xử lý value.
}
~~~

**Mã minh họa không độc lập:** biến `packet` cần chứa dữ liệu đã nhận và được cấu hình position/limit đúng. Sau `getShort()`, position của `packet` tăng 2; `asIntBuffer()` nhìn từ vị trí đó theo little-endian, với phần tử rộng 4 byte. Gọi `values.get()` chỉ tăng position của `values`. Nếu muốn `packet` đi tiếp sau 16 byte payload, phải tự cập nhật `packet.position(packet.position() + 16)` sau khi đã kiểm tra giới hạn.

Khi thiết kế parser, nên:

1. Xác nhận byte order và kích thước field từ specification (không dựa vào OS).
2. Chuẩn bị đúng `position`/`limit` trước khi tạo view.
3. Kiểm tra `remaining()` đủ dữ liệu; đối với view, kiểm tra theo **số phần tử**.
4. Tạo view đúng endian cho phần payload đồng nhất kiểu.
5. Quản lý cập nhật position của buffer nguồn và view tách biệt nếu parser có nhiều bước.
6. Tính tới trường hợp buffer read-only hoặc không có backing array dễ truy cập.

## 9. Những lỗi thường gặp

1. **Quên `order(LITTLE_ENDIAN)`:** dữ liệu đọc ngược giá trị vì ByteBuffer mặc định BIG_ENDIAN.
2. **Đổi `bytes.order(...)` rồi nghĩ view cũ đổi theo:** view giữ byte order lúc nó được tạo; muốn order mới, tạo view mới.
3. **Nhầm chỉ số theo byte với theo phần tử:** `bytes.getInt(2)` đọc từ byte offset 2, còn `ints.get(2)` là phần tử thứ ba (thường bắt đầu ở byte offset 8 tương đối tới view).
4. **Cho rằng tạo view làm tăng position nguồn:** `asIntBuffer()` không dịch position của ByteBuffer.
5. **Cho rằng view được tạo từ toàn bộ capacity:** view chỉ nhìn từ **position hiện tại đến limit hiện tại**, capacity bị làm tròn xuống theo kích thước phần tử.
6. **Đọc `array()` của direct hoặc read-only buffer:** có thể không có accessible backing array; dùng `hasArray()` hoặc API đọc byte.
7. **Quên đặt lại order cho `slice()`/`duplicate()`:** trong Java 17, ByteBuffer mới có order BIG_ENDIAN.
8. **Dùng `asCharBuffer()` để giải mã UTF-8:** sai vì đó là view theo đơn vị `char` 2 byte, không phải decoder văn bản.
9. **Dùng `nativeOrder()` cho file format:** dữ liệu cần phù hợp với quy định của file/network protocol.
10. **Tin rằng dùng view là an toàn khi nhiều thread cùng ghi:** chia sẻ memory cần đồng bộ, đặc biệt với thao tác cập nhật giá trị.

## 10. Tự kiểm tra

**Câu 1.** ByteBuffer chứa `01 02 03 04` và đang BIG_ENDIAN. Đọc `getInt(0)` ra gì? Nếu đổi LITTLE_ENDIAN thì sao?

**Đáp án:** `0x01020304` ở BIG_ENDIAN; `0x04030201` ở LITTLE_ENDIAN. Các byte gốc không tự thay đổi chỉ vì đổi `order`.

**Câu 2.** Buffer có 10 byte còn lại, tạo `asIntBuffer()` thì view có bao nhiêu phần tử? Có làm thay đổi ByteBuffer.position không?

**Đáp án:** `floor(10/4)=2` phần tử int; dư 2 byte không vào view; position ByteBuffer không thay đổi.

**Câu 3.** Tạo `IntBuffer` lúc byte order LITTLE_ENDIAN, sau đó đổi ByteBuffer thành BIG_ENDIAN. View cũ dùng endian nào?

**Đáp án:** LITTLE_ENDIAN cố định; muốn BIG_ENDIAN thì tạo view khác sau khi đổi order.

### Tài liệu chính thức

- [Java 17: ByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html)
- [Java 17: ByteOrder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteOrder.html)
- [Java 17: IntBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/IntBuffer.html)
- [Java 17: Buffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/Buffer.html)
- [Java 17: StandardCharsets](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/StandardCharsets.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-buffer-011">slice, duplicate, read-only buffer và trạng thái vị trí riêng</a>

<details>
<summary>Xem chi tiết</summary>

# Java NIO Buffer: slice, duplicate, read-only buffer và trạng thái vị trí riêng

## 1. Mục tiêu của bài

Khi xử lý dữ liệu nhị phân bằng Java NIO, một `ByteBuffer` không chỉ là mảng byte. Nó gồm hai phần cần phân biệt:

- **Nội dung (content):** vùng nhớ chứa các byte.
- **Trạng thái của từng đối tượng buffer:** `capacity`, `limit`, `position`, `mark` và các thuộc tính như read-only, direct/heap, byte order.

Ba phương thức quan trọng:

- `slice()` tạo **cửa sổ con** bắt đầu từ `position` hiện tại tới `limit`.
- `duplicate()` tạo **một góc nhìn khác trên cùng vùng byte**, ban đầu có cùng capacity, limit, position và mark.
- `asReadOnlyBuffer()` tạo **góc nhìn chỉ đọc** trên cùng vùng byte, ban đầu có cùng trạng thái vị trí/giới hạn.

**Điểm cốt lõi:** các view này **chia sẻ dữ liệu**, nhưng **không chia sẻ biến `position`, `limit` và `mark`**. Đổi position của view A không làm position của view B thay đổi. Ngược lại, sửa một byte trong vùng nhớ chung qua view có quyền ghi thì các view cùng nhìn vào byte ấy thấy giá trị mới.

Đây là cơ chế khác với sao chép dữ liệu: tạo view thường không copy toàn bộ nội dung. Vì thế view phù hợp để truyền một đoạn buffer cho hàm xử lý mà không phải tạo mảng byte mới, đồng thời phải quản lý quyền sửa và vòng đời vùng nhớ cẩn thận.

## 2. Ôn lại capacity, limit, position và mark

Mỗi `Buffer` có các chỉ số thỏa bất biến:

```text
0 <= position <= limit <= capacity
```

- `capacity`: sức chứa tối đa của **view đó**. Không thay đổi trong vòng đời view.
- `limit`: ranh giới trên mà thao tác đọc/ghi thông thường không được vượt qua (không bao gồm phần tử tại limit).
- `position`: chỉ số phần tử kế tiếp sẽ được đọc/ghi bởi thao tác tương đối (`get()`, `put(...)`).
- `mark`: mốc tùy chọn do `mark()` ghi tại position hiện tại để `reset()` quay về. Mark có thể chưa được đặt (undefined).
- `remaining()`: bằng `limit - position`.
- `hasRemaining()`: đúng nếu `position < limit`.

Các phép biến đổi trạng thái hay dùng:

| Phép gọi | Ảnh hưởng |
|---|---|
| `get()` / `put(byte)` | Đọc/ghi từ position hiện tại rồi tăng position 1 |
| `get(index)` / `put(index, value)` | Truy cập tuyệt đối theo chỉ số trong view; **không đổi position** |
| `position(n)` | Đặt position; mark bị bỏ nếu mark lớn hơn position mới |
| `limit(n)` | Đặt limit; nếu position lớn hơn limit thì position hạ về limit; mark lớn hơn limit bị bỏ |
| `mark()` | Lưu position hiện tại làm mark |
| `reset()` | Đưa position về mark; lỗi `InvalidMarkException` nếu mark chưa có/hết hiệu lực |
| `flip()` | `limit = position` cũ, `position = 0`, bỏ mark; thường chuẩn bị đọc dữ liệu vừa ghi |
| `clear()` | `position = 0`, `limit = capacity`, bỏ mark; **không xóa byte vật lý** |
| `rewind()` | `position = 0`, giữ limit, bỏ mark |
| `compact()` | Dồn các byte còn lại lên đầu view, đặt position sau phần đã dồn, limit = capacity; **có sửa nội dung** và không dùng được với read-only buffer |

**Lưu ý:** `clear()` là thao tác trạng thái, tên của nó không có nghĩa là đặt mọi byte bằng 0. Một view có capacity riêng, vì vậy gọi `clear()` trên `slice` không mở rộng view ra ngoài vùng slice.

Ví dụ trạng thái một buffer 8 byte:

```text
ByteBuffer capacity=8, position=2, limit=6

Index:      0   1   2   3   4   5   6   7
Nội dung: [10, 11, 12, 13, 14, 15, 16, 17]
                   ^               ^
                position          limit
Vùng remaining: các index [2, 6) = 2, 3, 4, 5.
```

Các chỉ số nói trên là **chỉ số cục bộ của từng view**, không nhất thiết là vị trí trong mảng gốc hay trong tệp.

## 3. `slice()`: view con có chỉ số bắt đầu lại từ 0

`slice()` trên `ByteBuffer` tạo một view chứa vùng `[position, limit)` tại **thời điểm gọi**.

Nếu buffer gốc capacity 8, position 2, limit 6:

```text
Original index:  0  1  2  3  4  5  6  7
Original bytes: 10 11 12 13 14 15 16 17
                      |-----------|
                      Vùng [2,6)

Slice index:           0  1  2  3
Slice bytes:          12 13 14 15

Original: capacity=8, position=2, limit=6
Slice:    capacity=4, position=0, limit=4, mark=undefined
```

Ý nghĩa chính:

1. View slice nhìn vào **cùng bốn byte** tại index gốc 2–5. `slice.get(0)` chính là byte mà `original.get(2)` nhìn thấy.
2. `slice.put(0, value)` (nếu cho phép ghi) cập nhật byte ở vị trí gốc 2; hai bên cùng thấy giá trị mới.
3. Thay `slice.position(...)`, `slice.limit(...)`, `slice.mark()` chỉ đổi trạng thái **slice**. Vị trí của original vẫn giữ nguyên.
4. Thay original.position/limit **sau khi tạo** không làm thay đổi kích thước hay vùng nhớ mà slice đã đại diện.
5. Slice có capacity giới hạn bởi đoạn đã chọn. Nó không thể truy cập các byte bên ngoài đoạn đó bằng các API chỉ số thông thường.
6. Original là read-only thì slice cũng read-only; original direct thì slice cũng direct.

Đoạn minh họa (không phải chương trình độc lập):

```java
ByteBuffer original = ByteBuffer.wrap(
        new byte[]{10, 11, 12, 13, 14, 15, 16, 17});

original.position(2);
original.limit(6);

ByteBuffer slice = original.slice();

System.out.println(slice.capacity()); // 4
System.out.println(slice.get(0));      // 12

slice.put(1, (byte) 99);
System.out.println(original.get(3));   // 99
System.out.println(original.position()); // 2
```

Chú ý: `ByteBuffer.slice(int index, int length)` còn cho phép tạo view của một đoạn cụ thể theo **chỉ số tuyệt đối trong buffer nhận lời gọi**, không cần thay position trước. Biến thể này có từ Java 13, nên dùng được trong Java 17:

```java
ByteBuffer selected = original.slice(2, 3);
// selected chứa original ở các index 2, 3, 4.
// selected.position() == 0, selected.limit() == selected.capacity() == 3.
```

Ở đây `index` là chỉ số của `original` hiện tại, **không phải offset cộng thêm vào `original.position()`**. Cần `0 <= index` và `0 <= length <= original.limit() - index`; vi phạm gây `IndexOutOfBoundsException`.

### Hai lớp slice lồng nhau

Nếu `first = root.slice()` rồi `second = first.slice()`, mọi chỉ số của `second` được tính từ vị trí của `first` tại thời điểm tạo `second`. Các lớp view cùng chia sẻ vùng byte tương ứng, nhưng có các vị trí riêng. Khi cần quy đổi index để debug, nên ghi chú offset của từng lớp:

```text
root absolute index = rootOffsetOfFirst + firstOffsetOfSecond + secondIndex
```

Đây là quan hệ để tính chỉ số trong cây slice của **một vùng nhớ đã biết**, không phải API tự động của `ByteBuffer`. Khi xây dựng parser nhiều tầng, nên truyền offset và độ dài rõ ràng để tránh nhầm.

## 4. `duplicate()`: view toàn vùng với con trỏ riêng

`duplicate()` tạo một buffer mới trỏ tới **cùng vùng dữ liệu tương ứng** của buffer nguồn. Với `ByteBuffer`, view mới có ban đầu:

- `capacity` **bằng** nguồn.
- `limit` **bằng** nguồn.
- `position` **bằng** nguồn.
- `mark` được **sao trạng thái giá trị** từ nguồn nếu đã xác định; sau đó mark mỗi view hoạt động độc lập.
- Thuộc tính direct/heap và read-only được giữ tương ứng.
- **Dữ liệu vẫn dùng chung**, không có bản copy byte riêng.

Khác biệt lớn với `slice` là duplicate vẫn có **cùng hệ chỉ số/capacity** của buffer nguồn. Nó phù hợp để hai thành phần có con trỏ duyệt riêng nhưng cùng tham chiếu một tập byte.

```java
ByteBuffer original = ByteBuffer.wrap(new byte[]{10, 20, 30, 40, 50});
original.position(1);
original.limit(4);
original.mark();

ByteBuffer duplicate = original.duplicate();
duplicate.position(3);  // original vẫn position=1

duplicate.put(2, (byte) 99);  // absolute put: không đổi vị trí duplicate
System.out.println(original.get(2));  // 99
System.out.println(original.position()); // 1

duplicate.reset(); // Mark được sao khi duplicate() gọi: về position=1.
```

Đoạn này minh họa Java với import `java.nio.ByteBuffer`. Vì duplicate và original dùng chung byte, việc thay limit trên duplicate không cấm original dùng vùng dữ liệu tương ứng nếu original vẫn có phạm vi hợp lệ.

**Cảnh báo về `clear()` trên duplicate:** duplicate có capacity bằng original. Nếu original đang limit thấp hơn capacity để biểu thị số byte dữ liệu hợp lệ, `duplicate.clear()` sẽ mở rộng limit của **duplicate** lên capacity. Điều đó không copy và không “tạo” dữ liệu hợp lệ mới; có thể làm lộ dữ liệu cũ trong buffer nếu chương trình quên giới hạn logic.

## 5. `asReadOnlyBuffer()`: chặn ghi qua view, không đóng băng dữ liệu

`asReadOnlyBuffer()` tạo **view chỉ đọc** trên cùng bộ nhớ, có ban đầu cùng capacity/position/limit/mark với nguồn (tương tự duplicate). Phương thức `isReadOnly()` của view trả `true`.

- Cho phép `get()`, `get(index)`, thay `position`, `limit`, `mark`, `reset`, `flip`, `clear`, `rewind`. Read-only **không** có nghĩa trạng thái vị trí bị khóa.
- Cấm các thao tác sửa nội dung qua view này, ví dụ `put(...)`, `compact()`: ném `ReadOnlyBufferException`.
- Nếu nguồn gốc hoặc một writable view khác cập nhật các byte chung, read-only view **vẫn thấy nội dung mới**. Read-only bảo vệ **đường truy cập qua view**, không tạo immutable snapshot.
- Tạo `duplicate()` hoặc `slice()` từ read-only view vẫn tạo read-only view; không thể dùng chúng để khôi phục quyền ghi.
- Read-only heap `ByteBuffer` không cung cấp trực tiếp mảng nền qua `array()`; `hasArray()` sẽ là `false`. Tương tự, direct buffer thường không có mảng `byte[]` có thể truy cập.

Ví dụ:

```java
ByteBuffer writable = ByteBuffer.wrap(new byte[]{1, 2, 3});
ByteBuffer readOnly = writable.asReadOnlyBuffer();

readOnly.position(1);      // Hợp lệ: chỉ thay con trỏ của view.
writable.put(1, (byte) 9); // Hợp lệ: nguồn vẫn có quyền ghi.

System.out.println(readOnly.get(1));  // 9: thấy sửa đổi chung
System.out.println(readOnly.isReadOnly()); // true

// readOnly.put(1, (byte) 8); // Ném ReadOnlyBufferException.
```

Nếu cần **dữ liệu bất biến thực sự**, phải tạo **bản sao tách rời** rồi kiểm soát toàn bộ cách truy cập tới bản sao. Ví dụ: tạo `ByteBuffer.allocate(source.remaining())`, chuyển dữ liệu từ view duplicate, `flip()`, sau đó chỉ phân phối read-only view của bản sao đó và không để lộ buffer writable của bản sao.

> **Read-only view ≠ immutable storage ≠ thread-safe storage.** Read-only chỉ ngăn ghi thông qua đối tượng view cụ thể.

## 6. Bảng so sánh trạng thái khi vừa tạo

Giả sử `src` là writable `ByteBuffer` capacity=8, position=2, limit=6, mark đã được đặt ở position=2.

| View mới | Chia sẻ byte với src? | Capacity mới | Position mới | Limit mới | Mark mới | Có được ghi qua view? |
|---|---|---:|---:|---:|---|---|
| `src.slice()` | Có, vùng [2,6) | 4 | 0 | 4 | Chưa xác định | Có, nếu src writable |
| `src.slice(1,3)` | Có, vùng [1,4) | 3 | 0 | 3 | Chưa xác định | Có, nếu src writable |
| `src.duplicate()` | Có, toàn vùng src | 8 | 2 | 6 | Sao trạng thái mark 2 | Có, nếu src writable |
| `src.asReadOnlyBuffer()` | Có, toàn vùng src | 8 | 2 | 6 | Sao trạng thái mark 2 | **Không** |
| `ByteBuffer.allocate(8)` | **Không** | 8 | 0 | 8 | Chưa xác định | Có |

Mỗi cột trạng thái là **giá trị ban đầu của đối tượng view mới**, không phải biến dùng chung; sau khi tạo, mỗi đối tượng tự duy trì position/limit/mark. Khi gọi `duplicate()` hoặc `asReadOnlyBuffer()` trên buffer đã bị read-only, view mới cũng read-only.

Một số lời gọi thay đổi metadata của original trước khi tạo view sẽ thay đổi **trạng thái ban đầu** của view được tạo sau đó. Các view đã tạo trước vẫn giữ trạng thái riêng của chúng.

## 7. Ví dụ Java 17+ đầy đủ: cùng dữ liệu, các con trỏ độc lập

Ví dụ dưới đây trình diễn bốn đối tượng `root`, `slice`, `duplicate` và `readOnly`. Mọi kết quả đều xác định, không phụ thuộc vị trí file hay thiết bị.

Lưu thành **`BufferViewsDemo.java`**:

```java
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

public class BufferViewsDemo {
    public static void main(String[] args) {
        ByteBuffer root = ByteBuffer.wrap(
                new byte[]{10, 11, 12, 13, 14, 15, 16, 17});

        root.position(2);
        root.limit(6);
        root.mark(); // mark của root đặt tại position=2

        ByteBuffer slice = root.slice();
        ByteBuffer duplicate = root.duplicate();
        ByteBuffer readOnly = root.asReadOnlyBuffer();

        System.out.printf("root: p=%d, l=%d, c=%d%n",
                root.position(), root.limit(), root.capacity());
        System.out.printf("slice: p=%d, l=%d, c=%d%n",
                slice.position(), slice.limit(), slice.capacity());
        System.out.printf("duplicate: p=%d, l=%d, c=%d%n",
                duplicate.position(), duplicate.limit(), duplicate.capacity());
        System.out.printf("readOnly: p=%d, l=%d, c=%d%n",
                readOnly.position(), readOnly.limit(), readOnly.capacity());

        slice.position(1);
        duplicate.position(4);
        readOnly.position(3);

        System.out.printf("positions: root=%d, slice=%d, duplicate=%d, readOnly=%d%n",
                root.position(), slice.position(),
                duplicate.position(), readOnly.position());

        slice.put(0, (byte) 99); // slice index 0 -> root index 2
        root.put(3, (byte) 88);  // root index 3 -> slice index 1

        System.out.println("root[2]=" + root.get(2));
        System.out.println("readOnly[2]=" + readOnly.get(2));
        System.out.println("slice[1]=" + slice.get(1));
        System.out.println("readOnly.isReadOnly=" + readOnly.isReadOnly());

        try {
            readOnly.put(2, (byte) 7);
            System.out.println("unreachable");
        } catch (ReadOnlyBufferException ex) {
            System.out.println("write through readOnly: rejected");
        }

        duplicate.reset(); // duplicate được sao mark=2 lúc tạo
        root.reset();      // mark riêng của root vẫn là 2

        System.out.printf("after reset: root=%d, duplicate=%d, slice=%d%n",
                root.position(), duplicate.position(), slice.position());
    }
}
```

Chạy:

```bash
javac BufferViewsDemo.java
java BufferViewsDemo
```

**Stdout chính xác:**

```text
root: p=2, l=6, c=8
slice: p=0, l=4, c=4
duplicate: p=2, l=6, c=8
readOnly: p=2, l=6, c=8
positions: root=2, slice=1, duplicate=4, readOnly=3
root[2]=99
readOnly[2]=99
slice[1]=88
readOnly.isReadOnly=true
write through readOnly: rejected
after reset: root=2, duplicate=2, slice=1
```

Diễn giải:

1. `root.slice()` chọn dải root index 2–5; khi nhìn từ slice, dải này có index 0–3.
2. `duplicate` và `readOnly` được tạo khi root ở position=2, limit=6; chúng bắt đầu với cùng vị trí và giới hạn đó, nhưng không trói nhau về sau.
3. `slice.position(1)`, `duplicate.position(4)` và `readOnly.position(3)` không làm `root.position()` đổi khỏi 2.
4. `slice.put(0, 99)` thay byte root index 2. `readOnly.get(2)` thấy 99 dù read-only không cho phép tự ghi.
5. `root.put(3, 88)` thay byte tương ứng với `slice.get(1)`. Các phép `get(index)/put(index,...)` tuyệt đối không đổi position.
6. `readOnly.put(...)` ném `ReadOnlyBufferException` và được bắt; không in `unreachable`.
7. Mark của duplicate đã được sao khi tạo, nên `duplicate.reset()` đưa duplicate từ position 4 về 2; root reset riêng về 2; slice vẫn ở 1.

## 8. Bẫy byte order riêng của ByteBuffer view

Đây là chi tiết dễ bị bỏ qua khi buffer chứa `short`, `int`, `long` hay `double` thay vì chỉ byte:

**Theo API Java 17, `ByteBuffer.slice()`, `slice(index,length)`, `duplicate()` và `asReadOnlyBuffer()` tạo view có byte order ban đầu là `BIG_ENDIAN`**, kể cả khi buffer nguồn đã chọn `LITTLE_ENDIAN`. Vì vậy không nên tự giả định view mới tự kế thừa cài đặt order của nguồn.

Đoạn minh họa:

```java
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

ByteBuffer base = ByteBuffer.allocate(8);
base.order(ByteOrder.LITTLE_ENDIAN);

ByteBuffer view = base.duplicate();
System.out.println(base.order()); // LITTLE_ENDIAN
System.out.println(view.order()); // BIG_ENDIAN theo đặc tả Java 17

view.order(base.order());        // Đặt rõ khi cần đọc cùng định dạng.
```

Đoạn này **không phải chương trình độc lập**; các lệnh `import` cần đặt đầu file khi đưa vào một class. Thói quen an toàn khi thao tác nhiều byte: đặt `view.order(expectedOrder)` rõ ràng ngay sau khi tạo view; đừng dùng giá trị mặc định như một giả định ngầm.

Khác với điều đó, **typed view** như `asIntBuffer()` có quy tắc byte order và đơn vị index riêng: nó xem byte dưới dạng `int` (mỗi phần tử bốn byte) và dùng byte order của nguồn **tại thời điểm view được tạo**. Khi so sánh `ByteBuffer` với `IntBuffer` phải lưu ý đơn vị index (byte so với số nguyên), tránh áp dụng tùy tiện một công thức cho cả hai.

## 9. Slice theo phần dữ liệu hợp lệ thay vì toàn bộ capacity

Mẫu xử lý NIO thường gặp là:

1. Ghi byte vào `ByteBuffer` qua `put(...)`; position tiến theo số byte đã ghi.
2. `flip()` để đặt limit bằng số byte có dữ liệu hợp lệ và position về 0.
3. Tạo view tới một phân đoạn, chẳng hạn header hoặc payload.
4. Đưa từng view tới các hàm đọc; chúng duy trì position riêng.

Ví dụ (minh họa):

```java
ByteBuffer packet = ByteBuffer.allocate(32);
packet.put(new byte[]{1, 2, 3, 4, 5, 6});
packet.flip(); // position=0, limit=6, capacity=32

ByteBuffer header = packet.slice(0, 2);
ByteBuffer payload = packet.slice(2, 4);

// header  chứa hai byte [1,2]
// payload chứa bốn byte [3,4,5,6]
// packet.position() vẫn bằng 0.
```

Những view này có capacity đúng bằng vùng logic. Nếu parser đọc quá limit sẽ nhận `BufferUnderflowException` (đọc tương đối) hoặc `IndexOutOfBoundsException` (truy cập tuyệt đối vượt limit). Không nên xử lý ngoại lệ đó thay cho việc xác minh định dạng packet; luôn kiểm tra độ dài theo giao thức trước khi tạo slice.

Nếu dùng `slice()` không tham số:

```java
packet.position(2);
ByteBuffer payload = packet.slice();
// Bắt đầu tại index 2 của packet, đến packet.limit() hiện tại.
```

Khác biệt: `slice()` dựa vào **position hiện tại**, còn `slice(2, 4)` dùng **index 2 được nêu tường minh** mà không cần thay position. Chọn biến thể phù hợp để giảm tác dụng phụ lên con trỏ đọc của caller.

## 10. Tạo view an toàn trong API

Khi thiết kế API nhận và trả `ByteBuffer`, hãy xác định hàm có quyền thay position của caller hay không.

### Mẫu A: Hàm chỉ đọc, giữ position/limit của caller

```java
static int sumRemainingUnsignedBytes(ByteBuffer source) {
    ByteBuffer cursor = source.duplicate();
    int sum = 0;
    while (cursor.hasRemaining()) {
        sum += Byte.toUnsignedInt(cursor.get());
    }
    return sum;
}
```

Hàm trên là **đoạn phương thức minh họa**, cần import `java.nio.ByteBuffer` và được đặt trong một class. Nó dùng `duplicate()` để di chuyển con trỏ **riêng** mà không thay source.position/limit. Nó không ngăn tiến trình khác sửa nội dung chung đồng thời; nếu dữ liệu cần ổn định, phải phối hợp quyền sở hữu hoặc copy.

### Mẫu B: Chỉ cấp quyền đọc dữ liệu cho bên nhận

```java
static ByteBuffer payloadView(ByteBuffer packet, int offset, int length) {
    return packet.slice(offset, length).asReadOnlyBuffer();
}
```

Hàm trả view chỉ đọc, có vùng chỉ số cục bộ `0..length-1`. Bên nhận không sửa được qua view trả về, nhưng nếu giữ reference tới buffer nguồn, bên tạo vẫn có thể sửa dữ liệu. Không quảng bá “immutable data” hay “snapshot” cho loại API này.

### Mẫu C: Cần snapshot độc lập

```java
static ByteBuffer readOnlySnapshot(ByteBuffer source) {
    ByteBuffer cursor = source.duplicate();
    ByteBuffer copy = ByteBuffer.allocate(cursor.remaining());
    copy.put(cursor);
    copy.flip();
    return copy.asReadOnlyBuffer();
}
```

Đây là bản **copy độc lập** của vùng `remaining` tại thời điểm copy. Hàm dùng thêm bộ nhớ tương ứng và giữ nguyên position của `source`. Để thực sự bảo vệ snapshot theo ranh giới API, không để lộ view writable `copy` ở nơi khác; đồng thời đảm bảo nguồn không bị sửa đồng thời trong lúc sao chép nếu cần snapshot nhất quán.

Nếu dữ liệu chứa kiểu nguyên thủy đa byte, nên đặt `order` rõ ràng ở view nhận/trả vì các view byte có thể mặc định BIG_ENDIAN.

## 11. Concurrency và vòng đời bộ nhớ

**Position riêng không đồng nghĩa thread-safe.** Java NIO `Buffer` nhìn chung không được thiết kế để nhiều luồng thao tác đồng thời trên **cùng một đối tượng** mà không phối hợp.

Tạo duplicate riêng cho mỗi tác vụ giúp các tác vụ **không tranh chấp một biến position**. Tuy nhiên chúng vẫn chia sẻ byte. Nếu một luồng ghi root trong khi luồng khác đọc read-only view, vẫn có thể xảy ra tranh chấp dữ liệu, đọc không nhất quán hoặc yêu cầu về memory visibility. Read-only view không tự cấp khóa hay đảm bảo snapshot.

Một số nguyên tắc thực tế:

- Cho nhiều luồng **chỉ đọc trên dữ liệu không thay đổi**, có thể cấp mỗi luồng một duplicate/read-only view riêng để độc lập con trỏ, sau khi bảo đảm dữ liệu đã được công bố an toàn.
- Nếu có thể sửa vùng nhớ chung, cần đồng bộ thích hợp, chia vùng không chồng lấn với hợp đồng rõ ràng, hoặc sao chép để cô lập dữ liệu.
- Direct buffer (`ByteBuffer.allocateDirect`) và heap buffer đều có thể dùng slice/duplicate/read-only view. View chỉ giữ tham chiếu tới vùng nhớ chung; không suy ra thời điểm giải phóng tài nguyên chỉ từ việc bỏ một reference.
- Với buffer đến từ memory mapping hoặc các nguồn dữ liệu được quản lý vòng đời riêng, cần xem xét khả năng file/nguồn thay đổi, đồng bộ và tính sẵn sàng của vùng nhớ. Read-only view không bảo đảm tệp nguồn luôn còn nguyên.

## 12. Những lỗi phổ biến và cách tránh

1. **Cho rằng `slice()` đã copy dữ liệu.** Sai: slice chia sẻ byte; sửa một bên thấy ở bên kia. Nếu muốn dữ liệu tách rời, hãy copy.
2. **Cho rằng `duplicate()` dùng chung position.** Sai: nó sao trạng thái ban đầu, sau đó các con trỏ độc lập.
3. **Cho rằng read-only view là snapshot.** Sai: bên có quyền ghi vào cùng storage vẫn thay nội dung mà read-only view đọc thấy.
4. **Nhầm `slice.get(0)` là root.get(0).** Sai: `slice.get(0)` tương ứng với **root.position tại lúc tạo slice** (với `slice()`) hoặc root index được truyền (với `slice(index,length)`).
5. **Tính offset hai lần.** `slice(index,length)` đã chọn vùng theo index của buffer nhận; không cộng thêm buffer.position một lần nữa.
6. **Không kiểm tra limit trước khi tạo `slice(index,length)`.** Vượt `limit` sẽ ném `IndexOutOfBoundsException`; capacity lớn hơn không cứu được.
7. **Mong `clear()` xóa byte.** Sai: `clear` chỉ đặt lại position/limit/mark. Nếu cần tẩy dữ liệu nhạy cảm, phải thực hiện chính sách xóa/ghi đè riêng phù hợp với kiểu bộ nhớ và yêu cầu bảo mật.
8. **Đọc sai byte order sau khi duplicate/slice.** Với ByteBuffer Java 17, hãy đặt `order(...)` minh bạch khi đọc/ghi các primitive đa byte.
9. **Dùng `array()` trên read-only/direct buffer.** `hasArray()` có thể `false`; `array()` có thể ném `ReadOnlyBufferException` hoặc `UnsupportedOperationException`.
10. **Thay đổi `limit` rồi giả định view khác thay theo.** Mỗi view có limit riêng. Khi muốn áp dụng giới hạn logic mới cho nhiều view, phải quản lý/thiết lập ở từng view.
11. **Giữ mark cũ sau `flip/clear/rewind`.** Những thao tác này loại bỏ mark của **đối tượng được gọi**; `reset()` tiếp theo có thể ném `InvalidMarkException`.
12. **Cho nhiều luồng vừa đọc vừa sửa bộ nhớ chung mà không phối hợp.** View độc lập về con trỏ nhưng **không tự cô lập nội dung**.

## 13. Câu hỏi tự kiểm tra

**Câu 1.** Buffer A có `position=3, limit=9, capacity=12`. Gọi `B=A.slice()`. B có trạng thái gì?  
**Đáp án:** B có `capacity=limit=6`, `position=0` và mark chưa xác định. Byte `B.get(0)` tương ứng byte `A.get(3)`.

**Câu 2.** Tạo `B=A.duplicate()`, sau đó gọi `B.position(5)`. `A.position()` có đổi không?  
**Đáp án:** Không. Mỗi buffer giữ vị trí riêng dù dữ liệu chia sẻ.

**Câu 3.** `B=A.asReadOnlyBuffer()`. Gọi `A.put(0,(byte)7)` thì `B.get(0)` có thể thấy 7 không?  
**Đáp án:** Có, vì nội dung chung; B chỉ chặn ghi **thông qua B**. Điều kiện: index 0 nằm trong limit hợp lệ của cả hai view và không có ghi khác xen vào.

**Câu 4.** Nếu A có byte order `LITTLE_ENDIAN`, view `A.slice()` mới tạo có mặc định kế thừa không?  
**Đáp án:** Theo hợp đồng `ByteBuffer` Java 17, view mới có `BIG_ENDIAN`. Khi cần dữ liệu multi-byte, gọi `view.order(A.order())` hoặc thiết lập order theo giao thức.

**Câu 5.** Muốn parser tiến position riêng nhưng không làm thay đổi caller, chọn gì?  
**Đáp án:** `duplicate()` nếu cần cùng hệ chỉ số/limit ban đầu; `slice()` nếu muốn parser chỉ nhìn đoạn remaining với index mới bắt đầu từ 0. Nếu cần ngăn ghi qua view, thêm `asReadOnlyBuffer()`.

**Câu 6.** `slice()` tạo ở root position 2, limit 6. Sau đó root chuyển position 0, limit 8. Slice có mở rộng lên 8 byte không?  
**Đáp án:** Không. Slice vẫn capacity 4 và chia sẻ đúng vùng đã chọn lúc tạo.

## 14. Ghi nhớ

- `slice`: **cùng byte, một vùng con, position mới = 0**.
- `duplicate`: **cùng byte, toàn vùng, sao trạng thái vị trí ban đầu**.
- `asReadOnlyBuffer`: **cùng byte, không cho phép sửa qua view**, nhưng dữ liệu vẫn có thể bị sửa qua view writable khác.
- **Position, limit, mark độc lập giữa các view; nội dung được chia sẻ.**
- Đối với `ByteBuffer` Java 17, **đặt byte order tường minh** ở các view mới khi cần xử lý primitive nhiều byte.
- Không dùng read-only view để thay thế immutable snapshot hoặc đồng bộ đa luồng.

### Tham khảo API

- Java SE 17, `ByteBuffer`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html
- Java SE 17, `Buffer`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/Buffer.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-buffer-012">Buffer không mặc nhiên an toàn cho nhiều luồng</a>

<details>
<summary>Xem chi tiết</summary>

# Buffer không mặc nhiên an toàn cho nhiều luồng trong Java NIO

## 1. Vấn đề cần hiểu

`java.nio.Buffer` và các lớp con như `ByteBuffer`, `CharBuffer`, `IntBuffer` được thiết kế để thao tác hiệu quả với dữ liệu và giữ trạng thái đọc/ghi. Chúng **không mặc nhiên bảo đảm an toàn khi nhiều thread đồng thời sử dụng cùng một buffer**. Tài liệu API Java mô tả rõ buffer không an toàn cho nhiều thread truy cập đồng thời nếu không có cơ chế đồng bộ phù hợp.

Đây là vấn đề ngay cả khi nhiều thread **chỉ đọc các byte**. Với phương thức đọc tương đối như `get()`, việc đọc làm thay đổi `position`, nên các thread đang sửa **trạng thái con trỏ dùng chung**. Nếu thread khác vừa `flip()`, `clear()` hoặc `compact()` trong lúc đọc, còn có thể gặp lỗi giới hạn và dữ liệu không nhất quán.

Cần tách hai câu hỏi:

- **Trạng thái buffer có được chia sẻ không?** `position`, `limit`, `mark` và một số phép biến đổi chúng quyết định byte nào được đọc/ghi.
- **Vùng nhớ chứa dữ liệu có được chia sẻ không?** Hai buffer có thể có con trỏ riêng nhưng vẫn cùng trỏ đến một mảng hoặc vùng nhớ; đọc/ghi cùng vùng có thể gây data race.

Kết luận khi thiết kế: **Một buffer chỉ an toàn khi quyền sử dụng, thao tác và quan hệ đồng bộ giữa các thread được quy định rõ.** Không nên suy luận rằng `ByteBuffer` tự cung cấp lock chỉ vì nó thuộc Java NIO.

## 2. Position, limit, capacity, mark là trạng thái mutable

Một `Buffer` có bốn khái niệm cốt lõi:

| Thuộc tính | Ý nghĩa | Lưu ý với nhiều thread |
| --- | --- | --- |
| `capacity` | Kích thước bộ nhớ biểu diễn bởi buffer, không đổi trong vòng đời object | Không bảo vệ trạng thái khác |
| `limit` | Ranh giới kết thúc vùng đọc/ghi đang được phép | Thread thay đổi limit có thể làm các thread khác đọc sai phạm vi |
| `position` | Chỉ số cho thao tác đọc/ghi tương đối kế tiếp | `get()`/`put(...)` dịch chuyển position chung |
| `mark` | Vị trí được đánh dấu để `reset()` trở về | Thay đổi position/limit có thể làm mark mất hiệu lực |

Các ràng buộc được buffer duy trì trong thao tác đơn lẻ có dạng `0 ≤ position ≤ limit ≤ capacity`; nếu có mark thì `mark ≤ position`. Việc một method đơn lẻ duy trì bất biến của buffer **không có nghĩa chuỗi thao tác từ nhiều thread được thực hiện nguyên tử như một giao dịch**.

Ví dụ vòng đời buffer thường gặp:

~~~java
ByteBuffer buffer = ByteBuffer.allocate(4);
buffer.put((byte) 10);
buffer.put((byte) 20);
buffer.flip();        // chuyển từ ghi sang đọc: limit=2, position=0
byte a = buffer.get(); // a=10; position=1
~~~

Đây là **đoạn minh họa** có thể đặt trong một method. Thao tác `put`, `flip` và `get` đều làm biến đổi trạng thái. `flip()` chuẩn bị đọc dữ liệu vừa ghi; `clear()` đặt position/limit cho một chu kỳ ghi mới nhưng **không xóa dữ liệu**, còn `compact()` có thể di chuyển các byte chưa đọc trong bộ nhớ.

Nếu một thread đọc khi thread khác gọi `flip()`/`clear()`/`compact()` mà không đồng bộ, reader có thể bị thay đổi phạm vi đọc, thấy byte cũ hoặc đọc dữ liệu ở vị trí ngoài dự kiến.

## 3. Đọc tương đối là một thao tác thay đổi trạng thái

Giả sử buffer chứa bốn byte:

~~~text
index:     0   1   2   3
data:     10  20  30  40
position:  0
limit:     4
~~~

Hai thread A và B đều gọi `get()` trên **chính object buffer đó**. Mỗi lần `get()` đọc byte ở `position` hiện tại rồi cập nhật vị trí kế tiếp. Ngay cả nếu không ai sửa nội dung mảng byte, hai thread vẫn tranh chấp **cùng một cursor**.

Một cách xen kẽ hợp lệ khi có thứ tự kiểm soát:

~~~text
A: get() -> 10, position=1
B: get() -> 20, position=2
B: get() -> 30, position=3
A: get() -> 40, position=4
~~~

Nếu A muốn nhận hai byte đầu, B muốn nhận hai byte cuối, cùng gọi `get()` trên một buffer **không thể tự đáp ứng ranh giới công việc**. Ngoài ra, khi hai thread thật sự gọi cùng lúc và không đồng bộ, các bước đọc/cập nhật position không có bảo đảm nguyên tử để phát mỗi byte chính xác một lần: có thể trùng dữ liệu, bỏ byte, hoặc ném `BufferUnderflowException`/`BufferOverflowException` tùy tình huống.

Cần phân biệt **xen kẽ thao tác có kiểm soát** với **data race**: ví dụ ở phần tiếp theo dùng latch để có kết quả ổn định, nhằm chứng minh cursor bị dùng chung. Nó không dựa vào việc kích hoạt data race ngẫu nhiên, nên không phải bài kiểm thử phát hiện mọi race.

## 4. Ví dụ Java 17+ chạy được: cùng cursor và các view riêng

Tạo tệp `BufferThreadsDemo.java`:

~~~java
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

public class BufferThreadsDemo {
    public static void main(String[] args) throws InterruptedException {
        ByteBuffer shared = ByteBuffer.wrap(new byte[] {10, 20, 30, 40});
        CountDownLatch aReadFirst = new CountDownLatch(1);
        CountDownLatch bReadBoth = new CountDownLatch(1);
        int[] a = new int[2], b = new int[2];

        Thread threadA = new Thread(() -> {
            a[0] = Byte.toUnsignedInt(shared.get());
            aReadFirst.countDown();
            await(bReadBoth);
            a[1] = Byte.toUnsignedInt(shared.get());
        });
        Thread threadB = new Thread(() -> {
            await(aReadFirst);
            b[0] = Byte.toUnsignedInt(shared.get());
            b[1] = Byte.toUnsignedInt(shared.get());
            bReadBoth.countDown();
        });

        threadA.start();
        threadB.start();
        threadA.join();
        threadB.join();
        System.out.println("Shared A: " + Arrays.toString(a));
        System.out.println("Shared B: " + Arrays.toString(b));
        System.out.println("Shared position: " + shared.position());

        ByteBuffer source = ByteBuffer.wrap(new byte[] {10, 20, 30, 40});
        ByteBuffer left = source.slice(0, 2).asReadOnlyBuffer();
        ByteBuffer right = source.slice(2, 2).asReadOnlyBuffer();
        int[][] results = new int[2][2];

        Thread leftThread = new Thread(() -> {
            results[0][0] = Byte.toUnsignedInt(left.get());
            results[0][1] = Byte.toUnsignedInt(left.get());
        });
        Thread rightThread = new Thread(() -> {
            results[1][0] = Byte.toUnsignedInt(right.get());
            results[1][1] = Byte.toUnsignedInt(right.get());
        });
        leftThread.start();
        rightThread.start();
        leftThread.join();
        rightThread.join();
        System.out.println("View A: " + Arrays.toString(results[0]));
        System.out.println("View B: " + Arrays.toString(results[1]));
        System.out.println("Source position: " + source.position());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac BufferThreadsDemo.java
java BufferThreadsDemo
~~~

Stdout **chính xác** của chương trình (đã biên dịch và chạy trên JDK):

~~~text
Shared A: [10, 40]
Shared B: [20, 30]
Shared position: 4
View A: [10, 20]
View B: [30, 40]
Source position: 0
~~~

Giải thích:

1. Hai thread đầu tiên nhận cùng một `ByteBuffer shared`. `CountDownLatch` buộc thứ tự A đọc một byte, B đọc hai byte, rồi A đọc byte cuối. Kết quả A nhận `[10, 40]`, thay vì cặp byte liên tiếp giả định `[10, 20]`. Mọi thao tác get trong phần này được sắp thứ tự qua latch, vì thế **đây là chứng minh chia sẻ position chứ không phải mô phỏng race không đồng bộ**.
2. Với phần thứ hai, `slice(0, 2)` và `slice(2, 2)` tạo hai view trên những đoạn khác nhau của cùng dữ liệu. Mỗi view có position, limit, mark **riêng**. Phương thức `asReadOnlyBuffer()` cấm ghi **qua view đó**.
3. Mỗi thread thứ hai chỉ sử dụng view của mình, và không có thread nào thay đổi các byte nền; A đọc `[10, 20]` còn B đọc `[30, 40]`.
4. `source.position()` vẫn là 0 vì đọc bằng view không dịch chuyển con trỏ của `source`. Các thread được khởi động sau khi tạo và thiết lập các view; `Thread.start()` và `Thread.join()` cũng tạo quan hệ *happens-before* thích hợp cho dữ liệu đầu vào/đầu ra của ví dụ.

**Đừng kết luận rằng `slice` và `asReadOnlyBuffer` giúp mọi tình huống dùng chung trở nên thread-safe.** Các view vẫn chia sẻ cùng bộ nhớ byte; nếu thread khác còn tham chiếu đến buffer gốc hoặc mảng gốc và ghi vào đó, dữ liệu mà các view đọc có thể bị thay đổi đồng thời.

## 5. duplicate(), slice(), asReadOnlyBuffer(): tách con trỏ, không nhất thiết tách dữ liệu

Java NIO cho phép tạo nhiều view đến cùng vùng nhớ:

| Cách tạo | Trạng thái `position/limit/mark` của view | Nội dung byte | Cẩn trọng |
| --- | --- | --- | --- |
| `original.duplicate()` | Ban đầu sao chép giá trị trạng thái; về sau thay đổi độc lập | Chia sẻ backing storage | Hai view cùng ghi một byte vẫn có thể xung đột |
| `original.slice()` | View mới position 0, limit/capacity là vùng `remaining()` của source tại thời điểm tạo | Chia sẻ vùng từ position hiện tại của source | Không bị ràng buộc bởi các thay đổi cursor sau này của source, nhưng vẫn chia sẻ dữ liệu |
| `original.slice(index, length)` | View mới position 0, limit/capacity bằng length | Chia sẻ đúng vùng từ index tới index+length của source | Chỉ có trên Java phiên bản hỗ trợ overload, dùng tốt với Java 17+ |
| `original.asReadOnlyBuffer()` | Trạng thái con trỏ độc lập, ban đầu theo source | Chia sẻ dữ liệu nhưng **không cho put qua view** | Owner hoặc alias khác vẫn có thể sửa dữ liệu |
| `ByteBuffer.allocate(n)` | Trạng thái độc lập | Cấp vùng byte mới | Hữu ích khi mỗi worker cần ghi riêng, nhưng cần cơ chế trao đổi kết quả |
| `ByteBuffer.wrap(array)` | Trạng thái buffer độc lập | **Chia sẻ mảng array bên ngoài** | Bất kỳ ai giữ mảng gốc vẫn có thể thay đổi nội dung |

Điểm quyết định:

- Tách **cursor** bằng `duplicate`/`slice` giúp các thread không giẫm lên position của nhau khi mỗi thread sở hữu view riêng.
- Tách **dữ liệu** đòi hỏi vùng nhớ khác nhau, ví dụ `allocate` mới và copy bytes, hoặc bảo đảm các vùng ghi không giao nhau cùng với quy tắc không có alias ghi chồng.
- Đọc từ các view dùng chung vùng dữ liệu có thể an toàn trong thực hành nếu dữ liệu đã được hoàn tất, công bố đúng cách (*safe publication*) và không còn writer nào sửa vùng đó.
- Read-only view chỉ bảo vệ giao diện ghi của chính view; nó **không làm backing data trở thành bất biến**.

Ví dụ về view chỉ đọc nhưng dữ liệu vẫn có thể thay đổi:

~~~java
byte[] bytes = {1, 2, 3};
ByteBuffer readOnly = ByteBuffer.wrap(bytes).asReadOnlyBuffer();
bytes[0] = 9;
System.out.println(readOnly.get(0)); // 9
~~~

Đây là đoạn code minh họa có thể đặt trong một method. Không có concurrent access trong ví dụ này; nó chứng minh sự chia sẻ backing storage. Nếu việc thay đổi `bytes[0]` diễn ra đồng thời với việc đọc mà không có đồng bộ, còn phát sinh vấn đề *visibility* và data race.

Một buffer được tạo bằng `allocateDirect` cũng không tự động thread-safe. *Direct* chỉ nói đến cách cấp phát/khả năng sử dụng vùng nhớ ngoài heap; nó không cung cấp giao thức khóa, phân quyền ghi hoặc thứ tự bộ nhớ thay cho chương trình.

## 6. Absolute get/put tránh thay đổi position nhưng chưa giải quyết mọi race

ByteBuffer cung cấp thao tác **tương đối** và **tuyệt đối**:

~~~java
byte next = buffer.get();           // lấy ở position, làm tăng position
byte atThree = buffer.get(3);       // lấy ở index 3, không đổi position
buffer.put((byte) 7);               // ghi ở position, làm tăng position
buffer.put(3, (byte) 7);            // ghi ở index 3, không đổi position
~~~

Đây là đoạn minh họa, giả sử `buffer` tồn tại và các vị trí nằm trong limit hợp lệ. Một thao tác theo index tuyệt đối không thay đổi cursor, nên tránh được **một nguồn tranh chấp**. Tuy nhiên:

- Nếu thread khác đang thay đổi `limit`, kiểm tra chỉ số hợp lệ và phạm vi truy cập có thể không ổn định.
- Hai writer truy cập cùng index, hoặc reader đọc đồng thời writer ghi cùng byte, vẫn có khả năng data race và kết quả không được điều phối.
- Không có gì bảo đảm một chuỗi thao tác tuyệt đối như “đọc 4 byte, kiểm tra rồi ghi 4 byte” là nguyên tử.
- `getInt(index)` hoặc `putLong(index, value)` không tự biến thao tác cập nhật nhiều trường của record thành transaction. Tính nguyên tử và visibility phải được thiết kế theo các bảo đảm thực tế của API/JVM.

Có thể dùng absolute access trong **schema chỉ đọc, đã đóng băng dữ liệu** với các vùng byte rõ ràng, nhưng vẫn cần công bố buffer cho các thread một cách an toàn.

## 7. Các chiến lược an toàn khi chạy đa luồng

### 7.1. Thread confinement: mỗi thread sở hữu buffer riêng

Đây thường là lựa chọn dễ hiểu nhất: mỗi worker tự `ByteBuffer.allocate(...)` hoặc nhận bản copy của dữ liệu, sau đó chỉ worker đó được `put/get/flip/clear`. Không chia sẻ mutable cursor và không có writer khác chạm vào backing storage.

Ví dụ về logic xử lý một tác vụ đơn luồng:

~~~java
static int parseTwoBytes(byte[] input) {
    ByteBuffer own = ByteBuffer.wrap(input.clone());
    int a = Byte.toUnsignedInt(own.get());
    int b = Byte.toUnsignedInt(own.get());
    return (a << 8) | b;
}
~~~

Đoạn trên là **method minh họa**; caller phải bảo đảm input có ít nhất hai byte. `clone()` tạo bản sao tách nội dung khỏi mảng do caller giữ. Nếu chỉ một thread sở hữu `input` trong toàn bộ quá trình, copy có thể không cần thiết.

### 7.2. Phân chia vùng làm việc, mỗi thread một view

Nếu dữ liệu lớn và không muốn copy, có thể tạo các view không chồng lấn sau khi xác định ranh giới:

~~~java
ByteBuffer left  = source.slice(0, half);
ByteBuffer right = source.slice(half, source.limit() - half);
~~~

Đây là đoạn minh họa, giả sử `source` đã được tạo ổn định, `0 ≤ half ≤ source.limit()`. Chỉ giao `left` cho worker A, `right` cho worker B. Nếu có ghi, mọi alias (kể cả `source` hoặc mảng backing) phải tuân thủ **vùng sở hữu không giao nhau**. Nếu dữ liệu chỉ đọc, dùng thêm `asReadOnlyBuffer()` để phát hiện ghi nhầm qua view.

Cần hiểu rằng thao tác tạo view không tự khóa vùng dữ liệu. Nếu thành phần khác vẫn có quyền ghi cả mảng gốc thì việc phân đoạn giữa hai worker chưa đủ.

### 7.3. Chuyển quyền sở hữu thay vì cùng sử dụng

Một pipeline producer/consumer thường hoạt động như sau:

1. Producer được quyền duy nhất ghi vào buffer, hoàn tất ghi và gọi `flip()`.
2. Producer **bàn giao** buffer qua cơ chế đồng bộ như `BlockingQueue.put(buffer)`.
3. Consumer gọi `take()` và là thành phần duy nhất đọc/dịch chuyển position trong giai đoạn đó.
4. Consumer báo hoàn thành hoặc đưa buffer vào pool theo giao thức rõ ràng; **chỉ lúc ấy** producer/pool mới được `clear()` và tái sử dụng nó.

`BlockingQueue` có bảo đảm memory consistency: những hành động trước khi đặt đối tượng vào queue *happen-before* các hành động sau khi thread khác lấy đối tượng ra. Điều đó hỗ trợ **safe publication**, nhưng queue **không tự thu hồi quyền sở hữu** của producer. Nếu producer tiếp tục sửa buffer sau khi `put`, vẫn có lỗi dùng chung.

Trong hệ thống async I/O, cần chờ thao tác hoàn tất trước khi tái sử dụng buffer. Đừng `clear()`/`compact()` một buffer trong khi một tác vụ đọc/ghi bất đồng bộ còn đang sử dụng vùng đó.

### 7.4. Đồng bộ mọi thao tác trên buffer được chia sẻ

Nếu thật sự cần chia sẻ **cùng một cursor**, sử dụng khóa bao phủ một đơn vị công việc đủ lớn. Ví dụ method sau lấy ra byte kế tiếp an toàn **nếu tất cả code truy cập/mutate cùng buffer đều tuân theo khóa này**:

~~~java
static byte takeNext(ByteBuffer shared) {
    synchronized (shared) {
        if (!shared.hasRemaining()) {
            throw new java.nio.BufferUnderflowException();
        }
        return shared.get();
    }
}
~~~

Đây là **method minh họa**, không phải chương trình độc lập. `hasRemaining()` và `get()` nằm chung critical section nên không có thread cùng tuân theo monitor đó chen vào làm thay đổi position giữa kiểm tra và đọc.

Tuy nhiên, khóa này chỉ có ý nghĩa khi **mọi đường truy cập liên quan** (`put`, `get`, `position`, `limit`, `flip`, `clear`, `compact` và truy cập backing array nếu có) tuân thủ cùng giao thức. Chỉ khóa hàm đọc rồi để thread ghi dùng `shared.put(...)` ngoài lock sẽ làm mất bảo đảm. Ngoài ra, khóa từng byte có thể không đủ cho thao tác nghiệp vụ gồm nhiều byte cần nhất quán như một record; lúc đó phải khóa toàn bộ record/batch hoặc dùng thiết kế khác.

`ReentrantLock` hoặc cơ chế khóa khác có thể phù hợp khi cần timeout, fairness hay điều kiện chờ. Điều quan trọng là **phạm vi khóa**, không phải tên primitive.

### 7.5. Dữ liệu immutable và đọc đồng thời

Với dữ liệu được tạo một lần rồi không đổi, nhiều worker có thể cùng đọc **qua view độc lập**, miễn là:

- Các byte đã hoàn tất trước khi công bố cho worker; không còn writer hoặc alias còn khả năng sửa dữ liệu trong lúc đọc.
- Có safe publication (ví dụ `Thread.start()`, trao qua `BlockingQueue` hoặc cấu trúc đồng bộ phù hợp).
- Mỗi thread giữ cursor riêng (`duplicate`, `slice` hoặc dùng index tuyệt đối với giới hạn bất biến).

Không nên gắn nhãn “immutable” cho `ByteBuffer.asReadOnlyBuffer()` khi vẫn còn alias ghi vào backing storage. Với yêu cầu bất biến mạnh, sao chép dữ liệu vào đối tượng được quản lý và bảo vệ toàn bộ alias, hoặc cung cấp API chỉ đọc mà phần nội dung thực tế cũng không bị sửa.

## 8. Sai lầm thường gặp trong ứng dụng NIO

| Sai lầm | Hậu quả | Cách xử lý |
| --- | --- | --- |
| Hai thread đều gọi `get()` trên cùng object | Cùng dịch chuyển position, mất phân vùng đọc | Giao view riêng, chuyển quyền sở hữu hoặc đồng bộ |
| Hai thread đều gọi `put()` trên cùng object | Tranh chấp vị trí ghi và dữ liệu | Vùng riêng, khóa hoặc kiến trúc producer/consumer |
| Một thread `flip()` khi thread kia còn `put()` | Thay đổi limit/position khi dữ liệu chưa hoàn chỉnh | Kết thúc ghi rồi bàn giao buffer bằng đồng bộ |
| Dùng `duplicate()` và cho rằng đã copy bytes | Hai view vẫn chia sẻ backing storage | Copy riêng nếu cần bộ nhớ độc lập |
| Dùng `asReadOnlyBuffer()` và cho rằng dữ liệu không thể đổi | Alias khác vẫn có thể ghi | Kiểm soát alias và quyền sửa dữ liệu |
| Đọc tuyệt đối `get(index)` rồi kết luận toàn bộ thao tác thread-safe | Vẫn có tranh chấp dữ liệu/limit và thiếu atomicity | Xác định bất biến dữ liệu hoặc dùng khóa |
| Cho nhiều thread dùng chung `ByteBuffer.wrap(array)` nhưng sửa `array` từ ngoài | Reader thấy dữ liệu thay đổi không đồng bộ | Không sửa array hoặc sao chép |
| Dùng `volatile ByteBuffer buffer` rồi nghĩ mọi `position/get/put` được đồng bộ | Volatile reference không đồng bộ hóa nội trạng thái mutable | Giao thức ownership/lock phù hợp |
| Lấy buffer từ pool rồi tái sử dụng trước khi consumer/async I/O xong | Byte cũ bị ghi đè, cursor/limit đổi giữa tác vụ | Chỉ trả pool khi có xác nhận hoàn tất |
| Dùng `clear()` để “xóa sạch dữ liệu” | Dữ liệu cũ còn trong backing storage | Nếu cần xóa nhạy cảm, ghi đè vùng cần thiết theo yêu cầu bảo mật riêng |

**Lưu ý về lỗi runtime:** một race có thể sinh `BufferUnderflowException`, `BufferOverflowException` hoặc `InvalidMarkException`, nhưng cũng có thể sinh **kết quả sai mà không ném exception**. Việc chạy thử 1.000 lần không lỗi không chứng minh chương trình an toàn đa luồng; lỗi phụ thuộc thời điểm thực thi và thứ tự truy cập bộ nhớ.

## 9. Hệ quả đối với SocketChannel, FileChannel và pipeline

Một ứng dụng NIO thường dùng `ByteBuffer` làm đầu vào/đầu ra của channel. Cần tách hai đối tượng và hợp đồng của chúng:

- Channel có thể có các bảo đảm đồng thời riêng của từng implementation/method, nhưng **bảo đảm đó không tự áp dụng lên cursor của một buffer đang được nhiều thread truyền vào**.
- Khi gọi `channel.read(buffer)`, channel **ghi bytes vào buffer và cập nhật position**; `channel.write(buffer)` **đọc bytes từ buffer và cập nhật position**. Nếu thread khác đồng thời thay đổi position/limit hoặc nội dung, dữ liệu gửi/nhận có thể sai.
- `SocketChannel` phi blocking có thể đọc/ghi được một phần: buffer phải giữ position chính xác để phần còn lại được xử lý tiếp. Không cho worker khác `rewind()`/`clear()` giữa các lần ghi tiếp phần còn lại.
- Trong pipeline xử lý packet, mỗi kết nối hoặc mỗi tác vụ có thể có buffer riêng; nếu chuyển buffer giữa event loop và worker, cần giao thức bàn giao ownership rõ ràng.
- Nếu dùng buffer pool, thời điểm trả pool phải sau thao tác channel/consumer hoàn tất; lưu một view vào nơi khác rồi trả backing storage quá sớm vẫn nguy hiểm.

Ví dụ một chu kỳ send thường gặp:

~~~java
buffer.flip();
while (buffer.hasRemaining()) {
    channel.write(buffer);
}
buffer.clear();
~~~

Đây là **mã minh họa cho channel ở chế độ blocking**, giả sử `buffer` đã chứa dữ liệu và `channel` có sẵn, mở và ghi được. Với channel non-blocking, `write` có thể trả 0; cần đăng ký trạng thái có thể ghi hoặc cơ chế retry phù hợp, không busy-spin trong vòng lặp trên. Trong cả hai trường hợp, thread hiện tại phải giữ quyền thao tác buffer trong suốt chu kỳ, hoặc đồng bộ phù hợp.

## 10. Tính visibility và happens-before

Java Memory Model xác định khi nào ghi ở thread này được thread khác quan sát đúng. Nếu một thread ghi bytes rồi thread khác đọc cùng backing storage mà không có một quan hệ *happens-before* phù hợp, mã không có cơ sở bảo đảm visibility và thứ tự của các thao tác.

Một số cơ chế tạo quan hệ này:

- `Thread.start()`: các hành động của thread khởi tạo trước `start()` happen-before hành động của thread mới.
- `Thread.join()`: khi thread A kết thúc và thread B hoàn thành join A, các hành động của A happen-before các hành động tiếp theo của B.
- Release/acquire trên cùng monitor qua `synchronized`.
- Các thao tác trao đổi phần tử phù hợp qua `BlockingQueue`.
- Hợp đồng memory consistency của các cơ chế đồng bộ khác trong `java.util.concurrent`.

**Happens-before không đồng nghĩa với quyền được cùng sửa.** Ví dụ truyền buffer qua queue đúng cách tạo visibility cho dữ liệu đã ghi trước bàn giao, nhưng nếu producer và consumer tiếp tục ghi cùng byte một lúc, vẫn có vấn đề thiết kế. Cần kết hợp memory visibility với ownership và phối hợp concurrency.

## 11. Cách quyết định thiết kế

| Nhu cầu | Lựa chọn thường hợp lý | Giới hạn cần ghi nhớ |
| --- | --- | --- |
| Mỗi thread xử lý dữ liệu riêng | Buffer cấp phát riêng hoặc copy | Chi phí copy/cấp phát |
| Nhiều thread đọc một dataset không đổi | View độc lập, backing storage không còn writer, safe publication | Read-only view không tự đóng băng backing data |
| Chia dữ liệu lớn cho các worker | `slice(index,length)` theo vùng không chồng lấn | Alias bên ngoài phải tôn trọng ranh giới vùng |
| Producer ghi, consumer đọc theo lượt | Blocking queue hoặc cơ chế bàn giao độc quyền | Không reuse buffer trước acknowledgement |
| Nhiều consumer cần lấy byte kế tiếp từ cùng cursor | Đồng bộ quanh `hasRemaining/get` hoặc thiết kế hàng đợi rõ ràng | Khóa phải bao phủ cả phép đọc/sửa liên quan |
| Nhiều writer cập nhật cùng vùng byte | Khóa hoặc chia quyền sở hữu vùng nhớ; giao thức nhất quán | Positional access không phải transaction |
| Nhiều thread truyền cùng buffer cho I/O channel | Ưu tiên mỗi tác vụ buffer riêng hoặc một owner tại mỗi thời điểm | Channel thread safety không thay thế Buffer thread safety |

Không cần dùng lock cho mọi buffer trong chương trình. Khi chỉ một thread sử dụng một buffer tại một thời điểm, **quyền sở hữu đơn giản** thường dễ kiểm chứng hơn. Chỉ cần lock hoặc điều phối khi thực sự chia sẻ trạng thái/dữ liệu mutable.

## 12. Câu hỏi tự kiểm tra

1. Hai thread chỉ gọi `ByteBuffer.get()` (không gọi `put`), có được coi là chỉ đọc không có tranh chấp? **Không**. `get()` tương đối thay đổi position dùng chung.
2. Hai thread nhận `duplicate()` của cùng một buffer, có thể sửa position độc lập không? **Có**. Nhưng các byte backing storage vẫn được chia sẻ; không được cho hai writer ghi chồng mà thiếu đồng bộ.
3. Có thể dùng `asReadOnlyBuffer()` rồi cho thread khác ghi vào buffer gốc đồng thời mà tự động an toàn không? **Không**. Chỉ view bị cấm ghi, nội dung vẫn có thể bị alias khác thay đổi.
4. Có phải `ByteBuffer.get(index)` thay đổi position không? **Không**. Nhưng thao tác này một mình không bảo đảm an toàn khi có writer hoặc limit được đổi đồng thời.
5. Khi producer đưa buffer vào `BlockingQueue`, producer có thể `clear()` ngay lập tức không? **Không**, trừ khi một giao thức khác xác nhận consumer đã dùng xong và trả quyền sở hữu.
6. Sử dụng `allocateDirect()` có tự đồng bộ truy cập đa luồng không? **Không**; direct/heap là đặc tính lưu trữ, không phải hợp đồng thread safety.
7. Vì sao nhiều thread cùng `channel.write(sharedBuffer)` dễ lỗi dù channel có đồng bộ nội bộ? Vì việc đọc từ buffer làm thay đổi position và các thread còn có thể thay đổi buffer bên ngoài phạm vi đồng bộ của channel.
8. Viết code `synchronized(shared) { shared.get(); }` ở một nơi có đủ chưa? **Chưa chắc**; mọi đường truy cập liên quan phải tuân theo cùng giao thức và phạm vi critical section phải đủ lớn cho tính nhất quán cần thiết.

## Tài liệu tham khảo

- [Java SE 17 — Buffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/Buffer.html): position, limit, mark, thao tác trạng thái và thread safety.
- [Java SE 17 — ByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html): relative/absolute access, `slice`, `duplicate`, `asReadOnlyBuffer`.
- [Java SE 17 — CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): thứ tự thực thi và memory consistency.
- [Java SE 17 — BlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html): bàn giao giữa producer/consumer và memory consistency.
- [Java SE 17 — FileChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html): tương tác giữa channel và buffer.


</details>

- [Quay lại đầu trang](#back-to-top)
