# Buffer và Channel

Stream truyền thống tạo cảm giác “đọc byte tiếp theo” hoặc “ghi byte tiếp theo”. **NIO (New I/O)** là nhóm API I/O của Java, chủ yếu trong `java.nio` và `java.nio.channels`, bổ sung một mô hình khác: dữ liệu được đặt trong một `Buffer` rồi được vận chuyển qua một `Channel`.

Trong mô hình này, `Buffer` là **vùng trung gian trong bộ nhớ** nơi chương trình đặt dữ liệu cần ghi hoặc nhận dữ liệu vừa đọc. `Channel` là **đường vận chuyển/kết nối I/O** đưa dữ liệu giữa buffer và nguồn hoặc đích như tệp hay socket. Việc tách “nơi dữ liệu đang nằm” khỏi “đường dữ liệu di chuyển” giúp code nhìn rõ vùng dữ liệu nào đang sẵn sàng đọc/ghi, xử lý các lần truyền chỉ tiến triển một phần, và dùng các khả năng riêng của từng loại channel.

`Buffer` của NIO cũng khác với **buffering** ở chương Buffered I/O. Buffering là kỹ thuật gom nhiều thao tác I/O nhỏ thành các lần truy cập lớn hơn để giảm chi phí; còn một NIO `Buffer` là đối tượng dữ liệu có trạng thái như `position`, `limit` và `capacity` mà chương trình thao tác trực tiếp.

~~~text
nguồn dữ liệu
    ↓
 Channel
    ↓ read(...)
 Buffer
    ↓
chương trình xử lý
~~~

Ở chiều ghi, hướng di chuyển đảo lại:

~~~text
chương trình đặt dữ liệu vào Buffer
    ↓
 Buffer
    ↓ write(...)
 Channel
    ↓
đích dữ liệu
~~~

Phần khó nhất với người mới thường không phải tên class mà là **trạng thái của Buffer**. Hiểu đúng `position`, `limit` và `capacity` sẽ làm cho `flip`, `clear` và `compact` trở nên logic thay vì phải học thuộc.

## <a id="buffer-state">Trạng thái của Buffer</a>

Với `ByteBuffer`, hãy hình dung một dãy ô byte có kích thước cố định:

~~~java
ByteBuffer buffer = ByteBuffer.allocate(8);
~~~

Ngay sau khi cấp phát:

~~~text
capacity = 8
limit    = 8
position = 0
~~~

Ba giá trị có ý nghĩa:

| Giá trị | Ý nghĩa |
| --- | --- |
| `capacity` | Tổng số phần tử buffer có thể chứa; cố định trong vòng đời buffer |
| `position` | Chỉ số của vị trí tiếp theo sẽ được đọc hoặc ghi |
| `limit` | Ranh giới một-past-the-end của vùng được phép dùng trong trạng thái hiện tại |

Quan hệ luôn phải thỏa:

~~~text
0 <= position <= limit <= capacity
~~~

Khi đang **ghi dữ liệu vào buffer bằng put**, `position` tiến lên:

~~~java
ByteBuffer buffer = ByteBuffer.allocate(8);

buffer.put((byte) 10);
buffer.put((byte) 20);
buffer.put((byte) 30);
~~~

Trạng thái lúc này:

~~~text
capacity = 8
limit    = 8
position = 3

[10][20][30][?][?][?][?][?]
             ^
             position
~~~

`position = 3` không có nghĩa byte hợp lệ nằm tại index 3. Nó có nghĩa **index 3 là nơi thao tác tương đối tiếp theo sẽ bắt đầu**.

Các lệnh `get()` tương đối cũng đọc tại `position` rồi tăng `position`. Vì vậy cùng một buffer có thể được dùng theo hai pha:

~~~text
pha ghi dữ liệu vào buffer
→ position chạy về phía trước

pha đọc dữ liệu ra khỏi buffer
→ position cũng chạy về phía trước
~~~

Muốn đổi giữa hai pha, ta phải cập nhật ranh giới đúng cách.

## <a id="byte-order-structured-binary">Structured binary và thứ tự byte</a>

`ByteBuffer` không chỉ đọc/ghi từng `byte`. Nó còn có các operation cho primitive nhiều byte như `putShort`, `putInt`, `putLong`, `putFloat`, `putDouble` và các `get...` tương ứng. Đây là cách tiện để xử lý **structured binary**: định dạng binary trong đó từng vùng byte có ý nghĩa đã được quy định, ví dụ 4 byte đầu là version, 8 byte tiếp theo là timestamp.

~~~java
ByteBuffer buffer = ByteBuffer.allocate(12);

buffer.putInt(3);
buffer.putLong(1_700_000_000L);

buffer.flip();

int version = buffer.getInt();
long timestamp = buffer.getLong();
~~~

Một `int` cần 4 byte, một `long` cần 8 byte. Khi nhiều byte cùng tạo thành một giá trị, code phải biết **byte order (endianness)**: byte quan trọng nhất hay ít quan trọng nhất nằm trước trong dữ liệu.

`ByteBuffer` mới tạo có byte order mặc định là `ByteOrder.BIG_ENDIAN`:

~~~text
giá trị 0x01020304
BIG_ENDIAN    → 01 02 03 04
LITTLE_ENDIAN → 04 03 02 01
~~~

Nếu định dạng file hoặc protocol quy định little-endian, phải đặt order trước khi đọc/ghi các primitive nhiều byte:

~~~java
ByteBuffer little = ByteBuffer
        .allocate(8)
        .order(ByteOrder.LITTLE_ENDIAN);

little.putInt(0x01020304);
~~~

Byte order là **một phần của data contract**, không phải tùy chọn tối ưu hiệu năng. Đọc little-endian bằng big-endian vẫn có thể trả về một số hợp lệ về kiểu dữ liệu nhưng giá trị sẽ sai. Thứ tự byte không ảnh hưởng khi chỉ đọc/ghi từng byte riêng lẻ; nó quan trọng khi nhiều byte được ghép thành primitive.

`ByteBuffer` cũng có thể tạo typed view như `asIntBuffer()` hoặc `asLongBuffer()`. View chia sẻ cùng vùng byte nhưng cho phép thao tác theo đơn vị `int`/`long`; byte order tại lúc tạo view quyết định cách các byte được diễn giải. Đây là công cụ hữu ích cho layout binary dày đặc, nhưng mental model gốc vẫn là: **byte storage + byte order + kiểu primitive mà format quy định**.

## <a id="flip-clear-compact">flip, clear và compact</a>

Giả sử ta vừa ghi 3 byte vào buffer:

~~~text
position = 3
limit    = 8
capacity = 8
~~~

Nếu gọi `get()` ngay, buffer sẽ bắt đầu đọc từ index 3 chứ không quay lại ba byte vừa ghi. Trước khi tiêu thụ dữ liệu, gọi:

~~~java
buffer.flip();
~~~

`flip()` biến trạng thái theo ý tưởng:

~~~text
limit = position cũ
position = 0
~~~

Kết quả:

~~~text
position = 0
limit    = 3
capacity = 8

[10][20][30][?][?][?][?][?]
 ^           ^
 position    limit
~~~

Bây giờ vùng có dữ liệu cần đọc là `[position, limit)`:

~~~java
while (buffer.hasRemaining()) {
    System.out.println(buffer.get());
}
~~~

Sau khi đọc hết:

~~~text
position = 3
limit    = 3
~~~

Nếu toàn bộ dữ liệu cũ đã được tiêu thụ và muốn dùng lại toàn bộ buffer cho vòng ghi mới:

~~~java
buffer.clear();
~~~

`clear()` đặt:

~~~text
position = 0
limit    = capacity
~~~

Tên `clear` dễ gây hiểu nhầm: nó **không xóa hoặc điền số 0 vào byte cũ**. Nó chỉ thay đổi metadata trạng thái để toàn bộ vùng buffer có thể bị ghi đè trong vòng tiếp theo.

Ngoài `position/limit/capacity`, `Buffer` còn có một **mark** tùy chọn. `mark()` ghi nhớ `position` hiện tại; `reset()` đưa `position` quay lại mark đó. Mark bị discard bởi các thao tác như `clear()`, `flip()` và `rewind()`, nên không nên coi nó là bookmark tồn tại qua mọi lần chuyển trạng thái.

`rewind()` đặt `position = 0` nhưng **giữ nguyên `limit`**, nên phù hợp khi muốn đọc lại cùng vùng dữ liệu từ đầu. Nó khác `clear()`, vì `clear()` mở lại toàn bộ `[0, capacity)` cho một pha ghi mới.

`compact()` dành cho tình huống vẫn còn dữ liệu chưa tiêu thụ. Giả sử sau `flip()` buffer có 6 byte hợp lệ, nhưng chương trình mới đọc 2 byte:

Giống `clear()`, `flip()` và `rewind()`, `compact()` cũng **discard mark** nếu buffer đang có mark.

~~~text
position = 2
limit    = 6
capacity = 8

[A][B][C][D][E][F][?][?]
       ^           ^
       position    limit
~~~

Gọi:

~~~java
buffer.compact();
~~~

Các byte chưa đọc `[C][D][E][F]` được copy về đầu buffer. Sau đó:

~~~text
[C][D][E][F][...free space...]
             ^
             position = 4

limit = capacity = 8
~~~

Buffer đã sẵn sàng nhận thêm dữ liệu mà không làm mất phần còn lại. Sau khi ghi thêm, gọi `flip()` trước khi chuyển lại sang đọc.

Chu trình dễ nhớ:

~~~text
ghi vào buffer
→ flip()
→ đọc khỏi buffer
→ clear() nếu đã đọc hết
  hoặc compact() nếu còn dữ liệu chưa đọc
→ ghi tiếp
~~~

Nếu `put` cố ghi vượt `limit`, `BufferOverflowException` có thể xảy ra. Nếu `get` cố đọc vượt `limit`, `BufferUnderflowException` có thể xảy ra. Hai lỗi này thường là tín hiệu rằng code đã hiểu sai trạng thái buffer.

## <a id="channel-model">Mô hình Channel</a>

`Channel` là đầu nối tới nguồn hoặc đích I/O. Với `ReadableByteChannel`, đọc nghĩa là đưa byte **từ channel vào buffer**:

~~~java
int count = channel.read(buffer);
~~~

Nếu đọc được `count > 0` byte, `position` của buffer tăng đúng số byte đó. Tùy loại channel và mode, một lần đọc có thể trả số byte nhỏ hơn vùng trống còn lại; với channel có khái niệm EOF, `-1` biểu thị đã tới cuối nguồn.

`WritableByteChannel` làm chiều ngược lại:

~~~java
int count = channel.write(buffer);
~~~

Channel lấy byte trong vùng:

~~~text
[position, limit)
~~~

và `position` tăng theo số byte thực tế đã ghi.

Một vòng copy bằng **blocking channel** (ví dụ `FileChannel`) cho thấy cả hai vai trò:

~~~java
ByteBuffer buffer = ByteBuffer.allocate(8 * 1024);

while (source.read(buffer) != -1) {
    buffer.flip();

    while (buffer.hasRemaining()) {
        target.write(buffer);
    }

    buffer.clear();
}
~~~

Vòng `while (buffer.hasRemaining())` bên trong rất quan trọng. Một lần `write` không nên được hiểu là luôn ghi hết phần còn lại; giá trị trả về mới cho biết số byte thực tế đã chuyển.

Vòng lặp trên cố ý dùng mental model blocking. Với non-blocking channel, `read()` hoặc `write()` có thể trả `0` khi hiện tại chưa tiến triển được, nên thuật toán còn phải tích hợp readiness/event-loop thay vì quay vòng liên tục theo mẫu này.

So với stream:

~~~text
Stream
→ abstraction tập trung vào dòng byte/char tuần tự

Channel + Buffer
→ tách đường vận chuyển khỏi vùng dữ liệu
→ trạng thái buffer trở thành phần rõ ràng của thuật toán
~~~

NIO không tự động nhanh hơn chỉ vì dùng `Channel`. Giá trị của mô hình này nằm ở khả năng kiểm soát buffer, random access ở một số channel, non-blocking ở nhóm channel phù hợp, và các thao tác đặc thù như transfer.

## <a id="scatter-gather-boundary">Scatter/Gather với nhiều Buffer</a>

Một số byte channel, trong đó có `FileChannel`, có thể đọc hoặc ghi **một mảng `ByteBuffer` trong một operation**.

**Scattering read** phân tán byte đọc được vào nhiều buffer theo thứ tự:

~~~java
ByteBuffer header = ByteBuffer.allocate(16);
ByteBuffer body = ByteBuffer.allocate(1024);

long read = channel.read(new ByteBuffer[]{header, body});
~~~

Channel đi qua các buffer theo thứ tự, điền buffer trước rồi tiếp tục sang buffer sau khi còn dữ liệu và còn chỗ. `position` của từng buffer tăng theo số byte thực tế mà buffer đó nhận.

**Gathering write** làm chiều ngược lại: lấy dữ liệu còn lại từ nhiều buffer theo thứ tự và ghi chúng thành một luồng byte trên channel:

~~~java
header.flip();
body.flip();

long written = channel.write(new ByteBuffer[]{header, body});
~~~

Mẫu này hữu ích khi dữ liệu tự nhiên có nhiều phần, ví dụ `header + payload`, vì code không nhất thiết phải copy tất cả vào một buffer lớn chỉ để thực hiện I/O.

Các quy tắc partial operation vẫn giữ nguyên. Giá trị trả về là **tổng số byte thực tế** đã chuyển, và một lần gọi có thể chưa tiêu thụ hết tất cả buffer. Code cần kiểm tra `position`/`hasRemaining()` của các buffer và tiếp tục theo contract của channel khi cần.

Scatter/gather cũng **không có nghĩa các buffer được đọc/ghi song song**. Đây vẫn là một operation trên một channel với nhiều vùng dữ liệu có thứ tự. Parallelism, scheduling và phối hợp giữa nhiều task/thread là bài toán khác.

## <a id="bytebuffer-types">Heap và Direct ByteBuffer</a>

Hai cách tạo `ByteBuffer` thường gặp:

~~~java
ByteBuffer heap = ByteBuffer.allocate(8192);
ByteBuffer direct = ByteBuffer.allocateDirect(8192);
~~~

`allocate` tạo **heap buffer**. Dữ liệu nằm trong vùng bộ nhớ do Java heap quản lý và, với buffer phù hợp, có thể truy cập backing array qua `array()`.

`allocateDirect` tạo **direct buffer**. JVM cố gắng bố trí vùng nhớ để native I/O có thể làm việc với nó hiệu quả hơn trong một số đường truyền dữ liệu, giảm một số bước copy trung gian.

Đánh đổi chính:

| Heap buffer | Direct buffer |
| --- | --- |
| Cấp phát thường rẻ hơn | Cấp phát/thu hồi thường đắt hơn |
| Thuận tiện cho logic Java thông thường | Có thể hữu ích khi buffer sống lâu và tham gia I/O lặp lại |
| Thường có backing array nếu không phải view đặc biệt | Không nên giả định có backing array |
| Dữ liệu nằm trên Java heap | Dữ liệu nằm ngoài Java heap |

Direct buffer vẫn là đối tượng Java và vòng đời vùng nhớ của nó gắn với khả năng JVM thu hồi buffer; đây không phải tài nguyên có `close()` để người dùng giải phóng tùy ý.

Không nên đổi mọi buffer sang direct chỉ vì tên nghe “nhanh hơn”. Với thao tác nhỏ hoặc buffer ngắn hạn, chi phí cấp phát có thể lấn át lợi ích. Chọn loại buffer dựa trên workload và phép đo thực tế.

Buffer còn có các kiểu như `CharBuffer`, `IntBuffer`, nhưng với file/network byte I/O, `ByteBuffer` là kiểu trung tâm. Chương tiếp theo dùng chính mô hình này với `FileChannel` để thấy random access, transfer, lock và memory mapping.
