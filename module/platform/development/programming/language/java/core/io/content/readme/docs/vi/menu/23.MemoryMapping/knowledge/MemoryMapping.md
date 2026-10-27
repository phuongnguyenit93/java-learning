<a id="back-to-top"></a>

# Ánh xạ bộ nhớ

## Menu
- [Memory-mapped file và MappedByteBuffer](#memory-mapping-001)
- [MapMode READ_ONLY, READ_WRITE và PRIVATE](#memory-mapping-002)
- [Ánh xạ từng vùng và giới hạn kích thước buffer](#memory-mapping-003)
- [Đọc ghi dữ liệu qua vùng nhớ đã ánh xạ](#memory-mapping-004)
- [load, isLoaded và tác động của page fault](#memory-mapping-005)
- [force cho thay đổi trên vùng ánh xạ](#memory-mapping-006)
- [Đóng channel không tự kết thúc vòng đời vùng ánh xạ](#memory-mapping-007)
- [Giới hạn giải phóng mapping theo phiên bản API và JVM](#memory-mapping-008)
- [Rủi ro khi tệp bị thay đổi kích thước trong lúc đang ánh xạ](#memory-mapping-009)
- [Khi nào memory mapping có ích và khi nào gây bất lợi](#memory-mapping-010)

## <a id="memory-mapping-001">Memory-mapped file và MappedByteBuffer</a>

<details>
<summary>Xem chi tiết</summary>

# Memory-mapped file và MappedByteBuffer trong Java NIO

## 1. Tổng quan: ánh xạ tệp vào bộ nhớ

**Memory-mapped file** (tệp được ánh xạ bộ nhớ) là kỹ thuật yêu cầu hệ điều hành ánh xạ một vùng byte của tệp vào không gian địa chỉ bộ nhớ của tiến trình. Ứng dụng có thể đọc hoặc sửa dữ liệu trong vùng đó qua các thao tác truy cập bộ nhớ, thay vì liên tục gọi `read`/`write` để chuyển từng khối byte giữa tệp và buffer.

Trong Java NIO, API cơ bản là:

- `FileChannel.map(FileChannel.MapMode mode, long position, long size)`: ánh xạ một **vùng** của tệp.
- `MappedByteBuffer`: một loại `ByteBuffer` trực tiếp (direct buffer) đại diện cho vùng tệp đã ánh xạ; dùng `get`, `put`, `getInt`, `slice`, `duplicate`... như `ByteBuffer` bình thường.
- `MappedByteBuffer.force()`: yêu cầu ghi các thay đổi của mapping đọc/ghi xuống thiết bị lưu trữ.
- `load()` và `isLoaded()`: API gợi ý/kiểm tra gần đúng về việc dữ liệu mapping đã ở bộ nhớ vật lý hay chưa.

Memory mapping thường hữu ích khi cần truy cập lặp lại những đoạn của tệp nhị phân tương đối lớn hoặc cần random access thuận tiện bằng chỉ số. **Không có quy tắc rằng memory mapping luôn nhanh hơn `FileChannel.read/write`**: việc tạo mapping, page fault, áp lực bộ nhớ và cơ chế ghi dữ liệu có chi phí riêng.

### Hình dung sự khác biệt

```text
I/O thông thường:
Tệp trên đĩa -- FileChannel.read(buffer) --> ByteBuffer --> chương trình
Chương trình -- FileChannel.write(buffer) --> tệp trên đĩa

Memory mapping:
Tệp trên đĩa <-- quản lý trang/cache bởi hệ điều hành -->
Vùng địa chỉ bộ nhớ được ánh xạ <-- MappedByteBuffer.get/put --> chương trình
```

Sơ đồ chỉ diễn tả **mô hình lập trình**. Dữ liệu không mặc nhiên được nạp toàn bộ vào RAM khi gọi `map`; hệ điều hành thường thực hiện tải trang theo nhu cầu, và cách truyền dữ liệu thực tế phụ thuộc hệ thống.

## 2. Tạo mapping bằng FileChannel.map

Cú pháp:

```java
MappedByteBuffer mapped = channel.map(
        FileChannel.MapMode.READ_ONLY,
        startOffset,
        regionLength);
```

Đoạn trên là minh họa, giả định `channel` đã mở với quyền phù hợp. Tham số:

| Tham số | Ý nghĩa |
|---|---|
| `mode` | Quy định quyền và cách phản ánh thay đổi: `READ_ONLY`, `READ_WRITE` hoặc `PRIVATE` |
| `position` / `startOffset` | Byte offset **trong tệp** nơi vùng ánh xạ bắt đầu; kiểu `long`, không âm |
| `size` / `regionLength` | Số byte ánh xạ; kiểu `long` nhưng không lớn hơn `Integer.MAX_VALUE` |
| Giá trị trả về | `MappedByteBuffer` với `position() == 0`, `limit() == capacity() == size` và mark chưa được đặt |

Ví dụ tệp có mười byte `ABCDEFGHIJ`, ánh xạ từ offset 3 với chiều dài 4:

```text
Tệp:            A B C D E F G H I J
Offset tệp:     0 1 2 3 4 5 6 7 8 9
                      |-------|
Ánh xạ:               D E F G
Index của mapping:    0 1 2 3

mapped.get(0) tương ứng byte tệp ở offset 3.
mapped.get(3) tương ứng byte tệp ở offset 6.
```

Công thức tổng quát cho một ánh xạ không bị thay đổi cách nhìn:

```text
fileOffset = mappingStartOffset + mappedBufferIndex
```

Với `map(mode, 3L, 4L)`, capacity của mapped buffer là **4**, không phải kích thước toàn tệp. Chỉ số hợp lệ để đọc bằng `get(index)` là từ 0 đến 3, miễn `limit` vẫn là 4.

Điểm dễ nhầm: `FileChannel.map` **không thay đổi current position của FileChannel**. Đây là cơ chế khác với `channel.read(buffer)` hay `channel.write(buffer)` theo vị trí hiện tại.

## 3. Ba chế độ ánh xạ: READ_ONLY, READ_WRITE, PRIVATE

| Chế độ | Được đọc? | Được `put` qua mapping? | Thay đổi mapping có ghi về tệp? | Quyền của FileChannel |
|---|---|---|---|---|
| `FileChannel.MapMode.READ_ONLY` | Có | Không; ném `ReadOnlyBufferException` | Không có thay đổi qua mapping để ghi | Mở cho `READ` |
| `FileChannel.MapMode.READ_WRITE` | Có | Có | Thay đổi được phản ánh tới tệp; thời điểm không xác định nếu chưa đồng bộ | Mở cho **cả `READ` và `WRITE`** |
| `FileChannel.MapMode.PRIVATE` | Có | Có | **Không** ghi thay đổi riêng về tệp; copy-on-write | Mở cho **cả `READ` và `WRITE`** |

### READ_ONLY

`READ_ONLY` là lựa chọn rõ ràng khi chương trình chỉ tra cứu hoặc parse nội dung tệp. Nếu gọi phương thức sửa nội dung như `put` hay `compact` trên mapping chỉ đọc, sẽ gặp `ReadOnlyBufferException`. Vẫn có thể thay `position`, `limit`, `mark` để duyệt dữ liệu.

Không nên diễn giải “read-only” thành snapshot bất biến: nếu một chương trình khác thay đổi tệp đã ánh xạ, nội dung đọc qua view có thể thay đổi, còn cách thức và thời điểm thay đổi được nhìn thấy phụ thuộc hệ điều hành.

### READ_WRITE

`READ_WRITE` cho phép sửa byte qua `put` và các thao tác tương ứng. Hệ thống sẽ đồng bộ thay đổi giữa mapping và tệp theo semantics của OS; thay đổi có thể tồn tại ở bộ nhớ/cache trước khi được ghi xuống thiết bị lưu trữ. Gọi `mapped.force()` nếu yêu cầu phải **đẩy dữ liệu mapping xuống storage** theo hợp đồng API, nhất là trước checkpoint hoặc bàn giao dữ liệu.

Không được đánh đồng “một byte đọc lại thấy giá trị mới” với “đã an toàn trước mất điện”. Độ bền dữ liệu là câu hỏi về `force`, filesystem và hệ lưu trữ.

### PRIVATE (copy-on-write)

`PRIVATE` cho phép sửa nội dung **trong view riêng**, nhưng các thay đổi đó không truyền về tệp gốc; hệ thống có thể tạo bản sao các trang bị sửa (copy-on-write). Thích hợp khi cần biến đổi nội dung được ánh xạ để tính toán thử mà không chủ ý cập nhật tệp.

Không nên dùng `PRIVATE` để thay thế mọi bản snapshot nhất quán: khả năng thấy các sửa đổi từ bên ngoài, nhất là khi có đồng thời, là vấn đề phụ thuộc OS/provider và không nên dựa vào hành vi ngầm. `force()` trên mapping `PRIVATE` không phải thao tác “commit” nội dung private về tệp.

## 4. Cơ chế dưới hệ điều hành: page fault và page cache

Bộ nhớ ảo thường được quản lý theo **trang (page)**. Khi một byte của mapping lần đầu được truy cập, dữ liệu tương ứng có thể chưa ở RAM; hệ điều hành giải quyết page fault để đưa trang tệp vào bộ nhớ. Trang có thể bị đẩy ra hoặc tải lại theo chính sách quản lý bộ nhớ.

Một số hệ quả:

1. **`map` không đồng nghĩa nạp toàn bộ vùng tệp vào RAM.** Nó thiết lập ánh xạ; dữ liệu có thể đến lúc sử dụng mới được tải.
2. Đọc lặp lại vùng nóng có thể hưởng lợi từ page cache của OS.
3. Truy cập ngẫu nhiên dày đặc tới file lớn hơn bộ nhớ vật lý có thể sinh nhiều page fault và giảm hiệu suất.
4. Ghi bằng `READ_WRITE` làm thay đổi trang memory-mapped; thời điểm flush thông thường do OS quyết định nếu không gọi API đồng bộ.
5. Java không cung cấp bảo đảm rằng truy cập vào một mapped byte luôn không phải I/O vật lý. Truy cập như bộ nhớ vẫn có thể gây page fault.
6. Memory mapping có chi phí hệ điều hành và quản lý vùng địa chỉ, nên không mặc định có lợi với thao tác rất nhỏ hoặc tệp ngắn.

Memory mapping **không loại bỏ disk I/O**: nó chuyển phần lớn quyết định nạp/ghi trang cho OS. Phương pháp phù hợp phụ thuộc kích thước file, pattern đọc/ghi, số lần truy cập và hệ điều hành.

## 5. Ví dụ Java 17+ hoàn chỉnh và kết quả chính xác

Chương trình dưới đây:

- Tạo tệp `mapped-demo.bin` chứa sáu byte ASCII `ABCDEF`.
- Mở `FileChannel` với quyền `READ` và `WRITE`.
- Ánh xạ vùng từ offset 1, dài bốn byte, tức `BCDE`.
- Truy cập byte theo **chỉ số tương đối của mapping** và sửa hai vị trí.
- Kiểm chứng rằng thay `MappedByteBuffer.position()` không làm thay đổi `FileChannel.position()`.
- Gọi `force()` để đồng bộ thay đổi mapping rồi đóng channel.
- Đọc lại tệp từ filesystem để kiểm chứng nội dung.

Lưu thành **`MemoryMappedDemo.java`**:

```java
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class MemoryMappedDemo {
    public static void main(String[] args) throws IOException {
        Path file = Path.of("mapped-demo.bin");
        Files.writeString(file, "ABCDEF", StandardCharsets.US_ASCII);

        try (FileChannel channel = FileChannel.open(
                file,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE)) {

            MappedByteBuffer mapped = channel.map(
                    FileChannel.MapMode.READ_WRITE, 1L, 4L);

            System.out.println("capacity=" + mapped.capacity());
            System.out.println("first=" + (char) mapped.get(0));
            System.out.println("channelPosition=" + channel.position());

            // mapped index 0 tương ứng file offset 1: B -> x
            mapped.put(0, (byte) 'x');

            // mapped index 2 tương ứng file offset 3: D -> y
            mapped.position(2);
            mapped.put((byte) 'y');

            System.out.println("mappedPosition=" + mapped.position());
            System.out.println("channelPosition=" + channel.position());

            mapped.force();
        }

        System.out.println("result=" +
                Files.readString(file, StandardCharsets.US_ASCII));
    }
}
```

Biên dịch/chạy tại thư mục chứa `MemoryMappedDemo.java`:

```bash
javac MemoryMappedDemo.java
java MemoryMappedDemo
```

**Stdout khi các thao tác I/O được hệ điều hành cho phép:**

```text
capacity=4
first=B
channelPosition=0
mappedPosition=3
channelPosition=0
result=AxCyEF
```

Giải thích từng kết quả:

- Mapping `(offset=1, size=4)` đại diện `BCDE` và có capacity=4, vị trí riêng ban đầu là 0.
- `mapped.get(0)` đọc chữ `B` tại file offset 1. Đây là **absolute get**, không tăng `mapped.position()`.
- `channel.position()` vẫn là 0 dù đã gọi `map` và `mapped.get/put`.
- `mapped.put(0,'x')` sửa byte `B` thành `x`. Sau đó `mapped.position(2)` và `mapped.put('y')` ghi ở mapping index 2, tương ứng file offset 3, sửa `D` thành `y`. Relative put tăng position thành 3.
- Đọc lại toàn bộ tệp có kết quả `AxCyEF`, vì chỉ byte ở file offset 1 và 3 bị thay.
- `force()` yêu cầu đồng bộ thay đổi của mapping `READ_WRITE` xuống thiết bị lưu trữ theo quy tắc API.

Chương trình cố ý để lại tệp `mapped-demo.bin` để bạn có thể mở bằng hex editor hoặc đọc lại sau khi JVM kết thúc. Có thể xóa tệp đó sau khi chương trình thoát. Cách này tránh phụ thuộc vào thời điểm hệ điều hành giải phóng memory mapping, nhất là khi xóa tệp còn đang được ánh xạ trên Windows.

## 6. Đọc/ghi primitive, byte order và chỉ số

`MappedByteBuffer` kế thừa phần lớn API của `ByteBuffer`. Các thao tác cơ bản:

| Phép gọi | Hành vi |
|---|---|
| `get()` | Đọc 1 byte từ mapped position, tăng position 1 |
| `get(int index)` | Đọc 1 byte theo chỉ số mapping, không đổi position |
| `put(byte value)` | Ghi 1 byte tại mapped position, tăng position 1 |
| `put(int index, byte value)` | Ghi tại chỉ số mapping, không đổi position |
| `getInt()` / `putInt(int)` | Đọc/ghi 4 byte, dùng byte order hiện tại |
| `getLong()` / `putLong(long)` | Đọc/ghi 8 byte, dùng byte order hiện tại |
| `position()`, `limit()`, `capacity()` | Quản lý trạng thái của chính mapped buffer |
| `slice()` / `duplicate()` | View dùng chung byte trong vùng mapping, có position/limit/mark riêng |

Các offset trong `MappedByteBuffer` là **offset tính từ đầu vùng mapping**, không phải tự động là offset toàn tệp.

```java
MappedByteBuffer mapped = channel.map(
        FileChannel.MapMode.READ_WRITE, 1024L, 16L);

mapped.putInt(4, 2026); // Ghi ở file offset 1024 + 4 = 1028.
```

Đây là đoạn **minh họa**, giả định file đủ dài và channel có quyền phù hợp.

`ByteBuffer` mặc định dùng **BIG_ENDIAN**. Nếu định dạng tệp của bạn là little-endian:

```java
mapped.order(java.nio.ByteOrder.LITTLE_ENDIAN);
mapped.putInt(4, 2026);
int same = mapped.getInt(4);
```

Cần thống nhất byte order ở mọi code đọc/ghi cùng định dạng. Khi tạo `slice/duplicate` mới từ `ByteBuffer/MappedByteBuffer`, Java 17 khởi tạo byte order của view mới là `BIG_ENDIAN`; nếu dùng multi-byte primitive, nên đặt `order(...)` tường minh ở view.

**Không nhầm file offset với byte của ký tự UTF-8.** Một ký tự UTF-8 có thể dài nhiều byte, nên `mapped.get(10)` là byte thứ 11 của mapping, không phải ký tự thứ 11 của văn bản.

## 7. force(), load() và isLoaded(): ba mục đích khác nhau

### `force()`: đẩy thay đổi tới thiết bị lưu trữ

```java
mapped.force();
mapped.force(100, 200); // Yêu cầu đồng bộ vùng [100, 300) của mapping.
```

Đây là đoạn minh họa; mapping cần đủ capacity cho vùng chỉ định.

- `force()` áp dụng cho toàn vùng mapping; overload `force(int index, int length)` nhắm vào một vùng byte.
- Đối với mapping `READ_WRITE` trên thiết bị lưu trữ cục bộ, tài liệu API Java quy định khi `force` trả về thành công, các thay đổi thuộc phạm vi được yêu cầu đã được ghi xuống thiết bị theo hợp đồng API.
- Với filesystem/thiết bị không phải lưu trữ cục bộ, không có cùng bảo đảm.
- OS có thể ghi thêm dữ liệu ngoài đúng vùng yêu cầu theo đơn vị trang/khối.
- Với `READ_ONLY` hoặc `PRIVATE`, `force` không có tác dụng ghi những sửa đổi riêng về tệp.
- `force` có thể ném `UncheckedIOException` khi gặp lỗi đồng bộ.

`force` **không biến nhiều thay đổi thành giao dịch nguyên tử**. Nếu yêu cầu dữ liệu chống hỏng giữa chừng, cần một định dạng và giao thức update phù hợp (ví dụ journal, checksum, double-write hoặc cơ chế giao dịch), chứ không chỉ thêm một lần `force`.

### `load()`: chủ động đề nghị nạp trang

```java
mapped.load();
```

`load()` cố gắng đưa các trang của mapping vào bộ nhớ vật lý. Đây là best effort, có thể phát sinh page fault/I/O và tiêu tốn tài nguyên. Không mặc định gọi `load()` cho mọi tệp lớn vì có thể làm tăng áp lực bộ nhớ và không tạo cam kết lưu trú vô hạn.

### `isLoaded()`: tín hiệu gần đúng

```java
boolean likelyResident = mapped.isLoaded();
```

`true` chỉ cho biết **rất có khả năng** toàn vùng đang nằm trong bộ nhớ vật lý tại thời điểm kiểm tra. `false` không chứng minh rằng dữ liệu chắc chắn chưa được nạp. Trạng thái có thể thay đổi ngay sau phép kiểm tra. Vì thế `isLoaded()` là hint quan sát, không phải điều kiện bắt buộc để `get()` được dùng an toàn.

| Phương thức | Trọng tâm | Không nên hiểu là |
|---|---|---|
| `load()` | Thử nạp dữ liệu vào bộ nhớ vật lý | Bảo đảm mọi trang luôn nằm trong RAM |
| `isLoaded()` | Gợi ý tình trạng resident | Bằng chứng chắc chắn không có page fault |
| `force()` | Đồng bộ các thay đổi read/write mapping ra storage | Tạo snapshot, khóa hay giao dịch nguyên tử |

## 8. Vòng đời mapping và đóng FileChannel

Một sự khác biệt quan trọng với I/O dùng buffer thông thường:

**Mapping không mất hiệu lực chỉ vì `FileChannel` tạo ra nó đã đóng.** Theo API, mapping và `MappedByteBuffer` vẫn hợp lệ cho tới khi bản thân buffer được garbage collected, với điều kiện vùng file backing vẫn có thể truy cập.

Ví dụ mô tả ý tưởng:

```java
MappedByteBuffer mapped;
try (FileChannel channel = FileChannel.open(
        Path.of("data.bin"), StandardOpenOption.READ)) {
    mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
}
// Channel đã đóng, mapped có thể vẫn đọc được nếu vùng file còn hợp lệ.
```

Đây chỉ là đoạn minh họa, vì `data.bin` cần tồn tại và phải có kích thước thích hợp. Việc JVM giữ mapping **không** ngăn được mọi rủi ro nếu file bị xóa/truncate/thay đổi từ bên ngoài.

Các hệ quả:

1. **Đóng channel không đồng nghĩa unmap ngay**; vùng mapping có thể còn giữ tài nguyên OS.
2. Không nên dựa vào `System.gc()` như cơ chế unmap deterministic.
3. Trên một số hệ điều hành, đặc biệt Windows, file đang được ánh xạ có thể khó xóa/đổi tên ngay.
4. Java 17 `MappedByteBuffer` không cung cấp phương thức public `close()` để chủ động giải phóng mapping như đóng stream/channel.
5. Nếu chương trình tạo nhiều mapping liên tiếp, phải cân nhắc vòng đời reference, mức sử dụng virtual address space và giới hạn tài nguyên.
6. Nếu phía khác truncate file sau khi map, việc truy cập vùng không còn backing có thể phát sinh lỗi không xác định cụ thể theo API; không nên để tình huống ấy xảy ra.

Với yêu cầu quản lý vòng đời cực chặt, cần cân nhắc giải pháp khác hoặc API mới phù hợp với JDK mục tiêu; không dựa vào các thủ thuật gọi API nội bộ/JDK không công khai để ép unmap trong mã portable Java 17.

## 9. Giới hạn mỗi mapping và cách xử lý tệp rất lớn

Tuy `FileChannel.map` nhận `position` và `size` kiểu `long`, kết quả là một `ByteBuffer` dùng chỉ số/capacity kiểu `int`. Vì vậy mỗi lời gọi `map` hỗ trợ `size` tối đa **`Integer.MAX_VALUE` byte** (xấp xỉ 2 GiB trừ 1 byte), và không có nghĩa là không thể xử lý **toàn bộ tệp lớn hơn 2 GiB**.

Chiến lược cho tệp lớn là **ánh xạ theo cửa sổ (window/chunk)**:

```java
long fileSize = channel.size();
long offset = 0L;
int windowSize = 64 * 1024 * 1024; // 64 MiB, ví dụ.

while (offset < fileSize) {
    long size = Math.min((long) windowSize, fileSize - offset);
    MappedByteBuffer window = channel.map(
            FileChannel.MapMode.READ_ONLY, offset, size);

    // Phân tích window trong phạm vi [0, window.limit()).
    // Ví dụ đọc: while (window.hasRemaining()) { byte b = window.get(); }

    offset += size;
}
```

Đoạn trên là minh họa, cần channel đã mở READ và import `MappedByteBuffer`, `FileChannel`. Trong hệ thống thực:

- Không giữ tất cả windows vô thời hạn nếu không cần; việc giải phóng mapping phụ thuộc vòng đời của buffer/GC.
- Cẩn thận với **bản ghi trải qua ranh giới cửa sổ**; phải giữ phần dư hoặc chọn ranh giới chunk trùng ranh giới bản ghi.
- Không giả định fileSize bất biến nếu tệp bị bên khác thay đổi; chọn cơ chế phối hợp/snapshot phù hợp.
- `offset` dùng `long`, còn index trong window là `int`. Không ép một offset file rất lớn xuống `int`.
- Mapping có giới hạn tài nguyên hệ điều hành, không chỉ giới hạn Java heap.
- Chọn window size bằng đo đạc thực tế; kích thước 64 MiB chỉ là ví dụ thiết kế.

Ví dụ map liên tiếp nhiều chunk cho phép xử lý file hàng chục GiB, nhưng không bảo đảm một chiến lược cụ thể phù hợp trên mọi hệ điều hành.

## 10. Vùng ánh xạ, kích thước file và an toàn truy cập

`map(mode, offset, length)` đòi hỏi `offset >= 0`, `length >= 0` và `length <= Integer.MAX_VALUE`. Java có thể ném `IllegalArgumentException` nếu vi phạm các điều kiện. Việc một vùng map không nằm trọn trong kích thước tệp tại thời điểm gọi có hành vi phụ thuộc hệ thống/đặc tả; **nên chuẩn bị file và bảo đảm dải mapping hợp lệ trước khi map**.

Ví dụ kiểm tra phạm vi đối với tệp có kích thước ổn định:

```java
long fileSize = channel.size();
long offset = 1024L;
long length = 256L;

if (offset < 0 || length < 0 ||
        length > Integer.MAX_VALUE ||
        offset > fileSize ||
        length > fileSize - offset) {
    throw new IllegalArgumentException("Mapping range outside the file");
}

MappedByteBuffer mapped = channel.map(
        FileChannel.MapMode.READ_ONLY, offset, length);
```

Đoạn này chưa phải chương trình độc lập; nó dùng cách kiểm tra `length > fileSize - offset` để tránh lỗi tràn số khi tính `offset + length`. Với tệp được tiến trình khác sửa đồng thời, kiểm tra trước không bảo đảm file không đổi ngay trước/sau lệnh map: cần quản lý đồng thời bằng cơ chế khác.

**Không truncate file trong lúc còn có mapping đang được truy cập.** Theo hợp đồng Java, mapped buffer có thể trở nên không truy cập được nếu backing file bị thu nhỏ; việc truy cập có thể ném ngoại lệ không được chỉ rõ, tại thời điểm truy cập hoặc sau đó. Không dựa vào một ngoại lệ cụ thể để phục hồi an toàn.

## 11. MappedByteBuffer, các view và trạng thái riêng

`MappedByteBuffer` kế thừa semantics `ByteBuffer`: `position`, `limit` và `mark` là trạng thái của đối tượng; chúng độc lập với `FileChannel.position()`.

- `mapped.get()`: đọc byte ở mapped position rồi tăng mapped position.
- `mapped.get(index)`: đọc tuyệt đối, không đổi mapped position.
- `mapped.put(index, value)`: sửa byte tại index trong mapping, không thay mapped position.
- `mapped.slice()`: view con trên vùng remaining; index của slice bắt đầu ở 0.
- `mapped.duplicate()`: view cùng backing storage, sao position/limit/mark ban đầu, các con trỏ độc lập về sau.
- `mapped.asReadOnlyBuffer()`: view chỉ đọc của cùng dữ liệu; không phải snapshot bất biến.

Ở Java 17, các override `MappedByteBuffer.slice()` và `duplicate()` trả về `MappedByteBuffer` nên một số tính năng mapping như `force`/`load` cũng khả dụng trên các view tương ứng. Mỗi view giới hạn vùng byte và có con trỏ riêng, nhưng vẫn cùng ánh xạ/backing dữ liệu.

Ví dụ:

```java
MappedByteBuffer mapped = channel.map(
        FileChannel.MapMode.READ_WRITE, 0L, 16L);
mapped.position(4);
mapped.limit(8);

MappedByteBuffer part = mapped.slice();
// part.capacity() == 4, part.position() == 0.
// part.get(0) nhìn thấy file offset 4.
```

Đoạn là minh họa, cần file đủ 16 byte. `part` không tạo bản sao độc lập. Khi phân phối các view cho nhiều luồng, cần chính sách chia vùng và đồng bộ dữ liệu; **position riêng không đảm bảo thread-safe khi các view ghi/đọc chung vùng**.

## 12. Khi nào nên dùng memory mapping?

### Nên cân nhắc

- Đọc nhiều lần các vùng khác nhau của tệp nhị phân lớn (ví dụ chỉ mục nhị phân, dữ liệu cố định có offset).
- Truy cập theo offset rải rác trong một vùng tệp và muốn xử lý qua `get(index)`.
- Cấu trúc dữ liệu trên đĩa gần với cách biểu diễn nhị phân trong bộ nhớ; cần thiết kế byte order/offset tường minh.
- Khối dữ liệu đủ lớn hoặc số lượt truy cập đủ cao để chi phí tạo mapping đáng được bù bằng lợi ích.
- Ứng dụng chấp nhận quản lý vòng đời mapping và tính phụ thuộc hệ điều hành.

### Thường nên dùng FileChannel.read/write hoặc Files

- Tệp nhỏ, chỉ đọc một lần hoặc thao tác ngắn; `Files.readAllBytes`, `Files.newBufferedReader` hay `FileChannel.read` có thể đơn giản hơn.
- Cần stream/parse bản ghi tuần tự mà không phải giữ mapping lâu dài.
- Cần kiểm soát chặt thời điểm đóng/xóa/đổi tên file trên nhiều nền tảng.
- File có thể bị sửa kích thước thường xuyên bởi tiến trình khác; mapping dễ gặp rủi ro tính hợp lệ khi truncate.
- Cần semantics rõ ràng của vòng lặp I/O, giới hạn bộ nhớ/tài nguyên và thứ tự ghi.

**Chọn theo benchmark và mô hình dữ liệu thực tế**, không mặc định “memory mapping là zero-copy nên luôn nhanh”. Mapping vẫn phát sinh page fault và disk I/O, cũng như overhead nếu liên tục tạo/huỷ mapping cho tác vụ nhỏ.

## 13. Đồng bộ, tính nhất quán và giới hạn an toàn

1. **Thay đổi đồng thời:** Nếu tác vụ khác ghi/truncate cùng tệp, Java không đảm bảo chính xác khi nào mapped view nhìn thấy thay đổi. Không dựa vào mapping như cơ chế đồng bộ giữa các tiến trình.
2. **Đọc/ghi nhiều byte không tự động nguyên tử:** Các phương thức `getInt/putInt` trên mapping không thay thế atomics/locks hay một giao thức đồng bộ an toàn.
3. **force không phải transaction:** Đồng bộ các trang không bảo đảm nhóm nhiều bản ghi cùng thành công hoặc tự rollback.
4. **Khoá file:** `FileChannel.lock/tryLock` có thể phối hợp giữa các bên tôn trọng khóa, nhưng cách thực thi/khả năng bắt buộc phụ thuộc OS/filesystem. Kiểm soát các writer theo thiết kế ứng dụng.
5. **PRIVATE không phải immutable snapshot toàn cục:** copy-on-write bảo vệ sửa đổi qua mapping khỏi tác động về file, nhưng không thiết lập snapshot isolation cho các thay đổi đồng thời từ bên ngoài.
6. **Xử lý file từ input không tin cậy:** Xác minh kích thước, offset, length, cấu trúc bản ghi, byte order và không cho số học offset bị overflow. Có thể dùng `Math.addExact` hoặc dạng kiểm tra hiệu an toàn.
7. **Tài nguyên không nằm toàn bộ trong heap:** Mapping tiêu thụ vùng địa chỉ và tài nguyên OS; heap memory thấp không đồng nghĩa mapping là miễn phí.
8. **Độ bền dữ liệu:** Khi cần durability, quyết định thời điểm `force`, xử lý ngoại lệ và kế hoạch khôi phục thay vì giả định mọi `put` lập tức an toàn.
9. **Khả năng xóa tệp:** Đừng yêu cầu xóa/đổi tên tức thời trong khi mapping vẫn còn được giữ tham chiếu, đặc biệt khi ứng dụng phải chạy trên Windows.

## 14. Lỗi thường gặp

| Sai lầm | Hậu quả | Cách tránh |
|---|---|---|
| Hiểu `map` là đọc toàn bộ file vào heap | Ước lượng tài nguyên và performance sai | Hiểu cơ chế mapping/page fault của OS |
| Dùng chỉ số toàn tệp trong `mapped.get(index)` | Đọc sai dữ liệu hoặc `IndexOutOfBoundsException` | Quy đổi `fileOffset - mapStartOffset` |
| Tưởng `channel.position()` thay theo `mapped.get/put` | Luồng xử lý dựa trên sai con trỏ | Quản lý riêng file position và mapped position |
| Bỏ `force` khi cần dữ liệu bền vững | Có nguy cơ thay đổi chưa xuống thiết bị | Gọi `force` ở checkpoint phù hợp, xử lý lỗi |
| Dùng `PRIVATE` rồi chờ ghi về file | Thay đổi không được commit vào file | Dùng `READ_WRITE` nếu muốn ghi về file |
| Cố `put` trên `READ_ONLY` | `ReadOnlyBufferException` | Chọn mode đúng theo quyền |
| Ánh xạ > `Integer.MAX_VALUE` trong một buffer | Tham số không hợp lệ | Chia thành mapping windows |
| Giả định `close(channel)` unmap tức thời | File vẫn bị giữ tài nguyên/khó xóa | Thiết kế lifecycle mapping rõ ràng |
| Truncate file khi có mapping còn được dùng | Lỗi truy cập không xác định cụ thể | Phối hợp bảo vệ kích thước/backing file |
| Cho rằng `isLoaded() == true` bảo đảm không page fault | Logic runtime không chính xác | Xem `isLoaded` là hint |
| Ánh xạ từng thao tác nhỏ trong vòng lặp | Overhead và nhiều mapping tồn tại | Tái sử dụng mapping hợp lý, hoặc I/O thường |
| Nhầm Java byte order của view | Đọc/ghi số nhiều byte sai | Đặt `order(...)` rõ ràng |
| Xem read-only mapping là snapshot | Có thể thấy nội dung thay đổi từ ngoài | Muốn snapshot cần thiết kế lưu trữ phù hợp |

## 15. Câu hỏi tự kiểm tra

**Câu 1.** File có mười byte `ABCDEFGHIJ`. Gọi `channel.map(READ_ONLY, 3, 4)`. `mapped.get(0)` trả byte nào và capacity là bao nhiêu?  
**Đáp án:** Byte `D`, capacity=4. Index 0 trong mapping ứng với offset 3 trong file.

**Câu 2.** Sau khi tạo mapping rồi `mapped.position(2); mapped.get();`, `channel.position()` có tự đổi không?  
**Đáp án:** Không. Mapped buffer và FileChannel duy trì vị trí riêng; mapping access không phải relative `FileChannel.read/write`.

**Câu 3.** Mapped view `READ_ONLY` có thể gọi `position(10)` trong giới hạn hợp lệ không? Và `put(...)` thì sao?  
**Đáp án:** Có thể thay position/limit hợp lệ; các thao tác sửa byte như `put` ném `ReadOnlyBufferException`.

**Câu 4.** Sửa một byte qua mapping `READ_WRITE` rồi đọc lại thấy byte mới. Điều đó có tự chứng minh đã ghi xuống ổ đĩa an toàn trước mất điện không?  
**Đáp án:** Không. Cần xét `force()` và độ bền được cung cấp bởi OS/filesystem/thiết bị.

**Câu 5.** Mapping `PRIVATE` bị thay byte qua `put`. Thay đổi đó có được ghi vào tệp gốc khi gọi `force()` không?  
**Đáp án:** Không. Mapping private dùng copy-on-write; `force` không phải lệnh commit nội dung private về file.

**Câu 6.** Tệp 5 GiB có thể xử lý bằng Java 17 `MappedByteBuffer` không?  
**Đáp án:** Có thể ánh xạ từng phần bằng nhiều windows, mỗi phần có size không vượt `Integer.MAX_VALUE` và dùng offset file kiểu `long`. Không thể map nguyên 5 GiB vào **một** `MappedByteBuffer`.

**Câu 7.** Đóng `FileChannel` có giải phóng mapping ngay không?  
**Đáp án:** Không. Mapping độc lập với vòng đời channel sau khi đã tạo; theo API Java 17 nó tồn tại tới khi buffer được GC, với điều kiện backing region vẫn hợp lệ.

**Câu 8.** Vì sao việc truncate file trong lúc một luồng đang truy cập mapping nguy hiểm?  
**Đáp án:** Vùng đã ánh xạ có thể mất backing hợp lệ; Java không bảo đảm loại ngoại lệ hay thời điểm lỗi cụ thể. Phải phối hợp việc thay kích thước và truy cập.

## 16. Ghi nhớ

- **`FileChannel.map` biến một vùng file thành `MappedByteBuffer`**, truy cập byte như direct `ByteBuffer` với index tương đối tính từ điểm bắt đầu mapping.
- Ba mode: **`READ_ONLY` để đọc**, **`READ_WRITE` để cập nhật file**, **`PRIVATE` để thay đổi riêng bằng copy-on-write**.
- `MappedByteBuffer.position/limit` độc lập với `FileChannel.position`; map/get/put không làm FileChannel.position tự tiến.
- **`force()` xử lý yêu cầu đồng bộ sửa đổi**, còn `load/isLoaded` liên quan residency bộ nhớ và chỉ cung cấp best effort/hint.
- Mỗi mapping trong Java 17 có size tối đa **`Integer.MAX_VALUE`**, muốn xử lý file lớn hơn thì map theo cửa sổ.
- **Đóng channel không giải phóng mapping ngay**; chú ý GC, file truncation, đồng thời, tính portable và tài nguyên hệ điều hành.
- Chọn memory mapping khi pattern truy cập và benchmark chứng minh hữu ích; I/O thông thường vẫn là lựa chọn tốt cho nhiều tác vụ đơn giản.

### Tài liệu tham khảo

- Oracle Java SE 17 — `FileChannel`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html
- Oracle Java SE 17 — `MappedByteBuffer`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/MappedByteBuffer.html
- Oracle Java SE 17 — `ByteBuffer`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="memory-mapping-002">MapMode READ_ONLY, READ_WRITE và PRIVATE</a>

<details>
<summary>Xem chi tiết</summary>

# MapMode READ_ONLY, READ_WRITE và PRIVATE trong Java NIO.2

## 1. Memory-mapped file là gì?

Với Java NIO.2, phương thức `FileChannel.map(...)` ánh xạ một **vùng byte của tệp** vào không gian bộ nhớ của tiến trình và trả về `MappedByteBuffer`. Thay vì liên tục gọi `read()` để sao chép byte từ channel vào buffer riêng, chương trình có thể truy cập vùng được ánh xạ bằng các phương thức `get(...)` và `put(...)` của `ByteBuffer`.

Ví dụ một tệp chứa 8 byte:

~~~text
Tệp example.bin (offset theo byte)
  0   1   2   3   4   5   6   7
+---+---+---+---+---+---+---+---+
| A | B | C | D | E | F | G | H |
+---+---+---+---+---+---+---+---+
        |<----- map(2, 4) ---->|
               ánh xạ C D E F

MappedByteBuffer mới:
  index  0   1   2   3
       +---+---+---+---+
       | C | D | E | F |
       +---+---+---+---+
~~~

Trong `channel.map(mode, position, size)`:

- `mode` chọn **quyền đọc/ghi và cách các thay đổi từ buffer ảnh hưởng tới tệp**.
- `position` là **vị trí bắt đầu trong tệp**, tính theo byte, kiểu `long`, không âm.
- `size` là **số byte** của vùng ánh xạ, kiểu `long` khi truyền vào API, nhưng đối tượng `MappedByteBuffer` trên Java 17 có dung lượng biểu diễn bởi `int`: vùng của một lần map không vượt `Integer.MAX_VALUE` byte.
- Buffer trả về có `position() == 0`, `limit() == capacity() == size`; chỉ số 0 trong buffer ứng với offset `position` của tệp.
- `FileChannel.map(...)` không làm thay đổi con trỏ vị trí I/O hiện tại của channel. Nó thiết lập một mapping riêng.

`MappedByteBuffer` là một `ByteBuffer` dạng direct. Vì vậy, nó hỗ trợ `get`/`put` tương đối hoặc tuyệt đối, byte order, view/slice và các thao tác điều khiển position/limit như buffer thông thường. Điểm khác biệt nằm ở nguồn dữ liệu và cách hệ điều hành quản lý trang bộ nhớ ánh xạ.

**Phạm vi bài này:** ba hằng `FileChannel.MapMode.READ_ONLY`, `READ_WRITE` và `PRIVATE`. Chúng có thể trông tương tự nhau khi chỉ đọc dữ liệu, nhưng có hành vi **rất khác nhau khi ứng dụng sửa buffer**.

## 2. So sánh ba MapMode

| Chế độ | Đọc dữ liệu? | Ghi `put` vào mapping? | Thay đổi từ mapping có được ghi trở lại tệp? | Cần mở FileChannel như thế nào? |
| --- | --- | --- | --- | --- |
| `READ_ONLY` | Có | Không; ném `ReadOnlyBufferException` | Không có thay đổi do buffer này thực hiện | Có quyền `READ` |
| `READ_WRITE` | Có | Có | Có; thay đổi được truyền về tệp theo cơ chế mapping, có thể gọi `force()` để yêu cầu ghi xuống lưu trữ | Có cả `READ` và `WRITE` |
| `PRIVATE` | Có | Có, trong vùng riêng của mapping | **Không**; các thay đổi của mapping không được truyền trở lại tệp | Có cả `READ` và `WRITE` |

Sự phân biệt quan trọng:

- `READ_ONLY` bảo vệ **view này khỏi việc ghi qua API buffer**. Nó không làm cho toàn bộ tệp bất biến: một tiến trình khác hoặc luồng khác có thể thay đổi tệp ở bên ngoài mapping.
- `READ_WRITE` dùng khi muốn cập nhật nội dung tệp thông qua bộ nhớ ánh xạ. Nội dung sửa có thể được hệ điều hành ghi về tệp ở thời điểm khác, nên khi cần kiểm soát việc đồng bộ cần gọi `MappedByteBuffer.force()` theo hợp đồng.
- `PRIVATE` là **copy-on-write**: ban đầu đọc từ vùng tệp được ánh xạ; khi ghi, hệ điều hành có thể tạo bản sao riêng của phần trang bị sửa. Chỉ chương trình nhìn thấy những thay đổi riêng của mapping đó; chúng không được propagate về tệp và không hiện ra ở tiến trình khác thông qua chính mapping riêng ấy.

Không coi `PRIVATE` là bản sao snapshot bất biến của toàn bộ tệp. Việc tệp nền thay đổi từ bên ngoài, và thời điểm thay đổi đó được phản ánh vào mapping, **phụ thuộc hệ điều hành và không được API bảo đảm**. Thứ được cam kết rõ ràng là **những thay đổi thực hiện qua private mapping không ghi ngược lại tệp**.

## 3. Mở FileChannel với quyền phù hợp

Cú pháp cơ bản:

~~~java
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

Path path = Path.of("example.bin");

try (FileChannel channel =
        FileChannel.open(path, StandardOpenOption.READ)) {

    long length = channel.size();
    MappedByteBuffer mapped =
            channel.map(FileChannel.MapMode.READ_ONLY, 0, length);
    // Đọc mapped tại đây.
}
~~~

Đây là **mảnh minh họa**, giả định `example.bin` đã tồn tại và không rỗng. `FileChannel.open(path, READ)` cho phép map `READ_ONLY`. Nếu muốn map `READ_WRITE` hoặc `PRIVATE`, mở channel có cả hai quyền:

~~~java
try (FileChannel channel = FileChannel.open(
        path, StandardOpenOption.READ, StandardOpenOption.WRITE)) {

    MappedByteBuffer writable =
            channel.map(FileChannel.MapMode.READ_WRITE, 0, channel.size());

    MappedByteBuffer privateView =
            channel.map(FileChannel.MapMode.PRIVATE, 0, channel.size());
}
~~~

**Chú ý về quyền:** dù `PRIVATE` không ghi các thay đổi trở lại tệp, overload `FileChannel.map` trên Java 17 vẫn yêu cầu channel mở cả `READ` lẫn `WRITE` cho chế độ này. Mở channel chỉ đọc rồi yêu cầu `READ_WRITE` hoặc `PRIVATE` có thể ném `NonWritableChannelException`; nếu thiếu quyền đọc cần thiết, có thể gặp `NonReadableChannelException`. Quyền mở tệp ở hệ điều hành cũng phải cho phép hoạt động tương ứng.

Không nên mở channel với `TRUNCATE_EXISTING` khi mục tiêu là ánh xạ dữ liệu hiện có để đọc hoặc sửa: tùy chọn này có thể xóa hết nội dung ngay khi mở. Nếu muốn tạo tệp mới và map, phải chuẩn bị kích thước/vùng nội dung một cách có chủ đích và xử lý trường hợp tệp rỗng.

## 4. READ_ONLY: đọc trực tiếp từ vùng tệp, không ghi được

~~~java
try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
    MappedByteBuffer readOnly = channel.map(
            FileChannel.MapMode.READ_ONLY, 0, channel.size());

    byte first = readOnly.get(0);
    System.out.println((char) first);

    // Ném ReadOnlyBufferException:
    // readOnly.put(0, (byte) 'X');
}
~~~

Đây là **mảnh minh họa** với biến `file` là `Path` trỏ đến tệp không rỗng. Gọi `isReadOnly()` trên buffer này trả `true`. Các thao tác thay đổi dữ liệu qua buffer như `put(...)` bị từ chối.

READ_ONLY thích hợp khi:

- Quét một tệp lớn để tìm một mẫu byte, chỉ số hay bảng tra cứu mà không có nhu cầu sửa.
- Đọc cấu trúc nhị phân có vị trí cố định trong tệp.
- Muốn API của một mapping từ chối thao tác ghi do lỗi lập trình.

READ_ONLY **không đồng nghĩa** an toàn trước thay đổi ngoài chương trình. Nếu một tiến trình khác sửa hoặc thu nhỏ tệp trong khi mapping đang tồn tại, nội dung quan sát được có thể thay đổi hoặc vùng mapping có thể trở nên không truy cập được. Java không bảo đảm snapshot của tệp tại thời điểm map.

Nếu ứng dụng cần dữ liệu không đổi để xử lý, hãy kiểm soát vòng đời tệp/đồng bộ, đọc bản sao độc lập hoặc dùng cơ chế nhất quán phù hợp hệ thống.

## 5. READ_WRITE: sửa buffer có tác động đến tệp

~~~java
try (FileChannel channel = FileChannel.open(
        file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {

    MappedByteBuffer rw = channel.map(
            FileChannel.MapMode.READ_WRITE, 0, channel.size());

    rw.put(0, (byte) 'J'); // Sửa byte đầu của vùng ánh xạ.
    rw.force();            // Yêu cầu đồng bộ các thay đổi qua mapping.
}
~~~

Điều cần hiểu:

1. `put(0, ...)` ghi vào vùng mapping tại **buffer index 0**, tương ứng vị trí bắt đầu truyền cho `map`. Với ví dụ trên, đó là byte đầu tiên của tệp.
2. Ở `READ_WRITE`, thay đổi sẽ được truyền về tệp theo cơ chế của hệ điều hành. **Thời điểm** thay đổi trở nên quan sát được từ các tiến trình khác có thể khác nhau theo nền tảng.
3. `rw.force()` dùng để yêu cầu đẩy những thay đổi của mapping xuống thiết bị lưu trữ. Theo API Java 17, với tệp trên thiết bị lưu trữ **cục bộ**, lời gọi này có bảo đảm ghi các thay đổi từ buffer vào thiết bị khi trả về; với thiết bị không cục bộ thì không có bảo đảm tương đương.
4. `rw.force(int index, int length)` yêu cầu đồng bộ một phần vùng ánh xạ khi muốn quản lý phạm vi; đây là API hiện diện trên Java 17. Nó có thể thực hiện thêm I/O ngoài phạm vi yêu cầu vì đơn vị lưu trữ vật lý.
5. Cần xử lý lỗi I/O của `force()`. Với `MappedByteBuffer.force`, lỗi ghi có thể phát sinh dưới dạng `UncheckedIOException`.

`MappedByteBuffer.force()` và `FileChannel.force(boolean metaData)` không hoàn toàn thay thế nhau. Theo Java 17, `FileChannel.force` **không được bảo đảm** sẽ đồng bộ những thay đổi thực hiện thông qua `MappedByteBuffer`. Với byte sửa qua memory mapping, hãy gọi `mapped.force()` khi cần đồng bộ thay đổi đó.

Một điểm dễ nhầm: `force()` **không phải** lệnh “lưu file” bắt buộc sau mọi `put`. Chế độ `READ_WRITE` đã có ngữ nghĩa propagate; `force` là công cụ yêu cầu thời điểm đồng bộ theo hợp đồng, đặc biệt khi ứng dụng có yêu cầu độ bền dữ liệu.

## 6. PRIVATE: copy-on-write và sự tách biệt với tệp gốc

~~~java
try (FileChannel channel = FileChannel.open(
        file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {

    MappedByteBuffer privateView = channel.map(
            FileChannel.MapMode.PRIVATE, 0, channel.size());

    privateView.put(0, (byte) 'P');

    // privateView.get(0) nhìn thấy 'P'.
    // Byte tại vị trí 0 trong tệp KHÔNG bị thay đổi bởi lệnh put trên.
}
~~~

Chế độ PRIVATE rất khác với một buffer read-only: chương trình **được phép ghi** và đọc lại những byte vừa sửa. Tuy nhiên, tác động đó bị giữ trong không gian riêng của mapping.

~~~text
Tệp ban đầu:
      H E L L O
          |
          +--------> PRIVATE mapping
                        |
                    put(0, 'P')
                        |
Mapping riêng nhìn thấy:
      P E L L O

Tệp gốc vẫn là:
      H E L L O
~~~

Private mapping không tự động tạo một “file phiên bản mới”. Những byte sửa riêng chỉ tồn tại trong mapping và có thể mất đi khi vùng mapping bị giải phóng. Nếu muốn lưu bản sửa vào tệp mới, chương trình phải chủ động sao chép dữ liệu kết quả sang tệp đích (ví dụ bằng `Files.newOutputStream(...)` hoặc channel ghi riêng) và có quy tắc đảm bảo nhất quán.

`force()` trên `PRIVATE` **không ghi các byte sửa riêng về tệp**; theo Java 17, `MappedByteBuffer.force()` không có tác dụng cho mapping `READ_ONLY` hoặc `PRIVATE`. Không dùng `force` như phương án “commit” thay đổi private mapping.

### 6.1. PRIVATE không có nghĩa “đóng vai trò transaction”

Một transaction thông thường có commit/rollback, cô lập theo cấp độ xác định và quy tắc nhất quán. `MapMode.PRIVATE` chỉ cung cấp semantics **copy-on-write cho những thay đổi từ mapping đó**; nó không tự cung cấp:

- Giao diện commit thay đổi về tệp.
- Bảo đảm snapshot độc lập hoàn toàn trước các sửa đổi bên ngoài.
- Khả năng hoàn tác thay đổi của một `READ_WRITE` mapping khác.
- Cơ chế khóa/đồng bộ giữa các tiến trình, hay khả năng giải quyết xung đột.

Nếu muốn chỉnh sửa thử một bản dữ liệu nhị phân rồi quyết định có giữ hay không, PRIVATE có thể hữu ích cho xử lý cục bộ; nếu cần transaction bền vững thì phải thiết kế cơ chế riêng ở cấp ứng dụng.

## 7. Ví dụ hoàn chỉnh Java 17+: so sánh ba chế độ trên cùng tệp

Chương trình sau dùng tệp tạm chứa chuỗi ASCII `HELLO`:

1. Mở READ_ONLY, đọc nội dung và chứng minh lệnh `put` bị chặn.
2. Mở PRIVATE, sửa `H` thành `P`. Buffer riêng thấy `PELLO` nhưng đọc lại tệp vẫn thấy `HELLO`.
3. Mở READ_WRITE, sửa `E` thành `A` và gọi `force()`. Đọc lại tệp thấy `HALLO`.

Lưu thành `MapModeDemo.java`:

~~~java
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class MapModeDemo {

    static String readAscii(MappedByteBuffer mapped) {
        // Duplicate giữ nguyên position/limit của buffer gốc.
        MappedByteBuffer copy = mapped.duplicate();
        copy.position(0);
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("map-modes-", ".bin");

        // Đăng ký xóa lúc JVM kết thúc. Việc dọn mmap phụ thuộc HĐH;
        // đừng phụ thuộc xóa tức thì lúc buffer vẫn còn mapping.
        file.toFile().deleteOnExit();
        Files.writeString(file, "HELLO", StandardCharsets.US_ASCII);

        try (FileChannel channel = FileChannel.open(
                file, StandardOpenOption.READ)) {

            MappedByteBuffer ro = channel.map(
                    FileChannel.MapMode.READ_ONLY, 0, 5);

            System.out.println("readOnly=" + ro.isReadOnly());
            System.out.println("readOnlyText=" + readAscii(ro));

            boolean blocked = false;
            try {
                ro.put(0, (byte) 'X');
            } catch (ReadOnlyBufferException ex) {
                blocked = true;
            }
            System.out.println("readOnlyBlocked=" + blocked);
        }

        try (FileChannel channel = FileChannel.open(
                file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {

            MappedByteBuffer privateView = channel.map(
                    FileChannel.MapMode.PRIVATE, 0, 5);
            privateView.put(0, (byte) 'P');

            System.out.println("privateView=" + readAscii(privateView));
            System.out.println("fileAfterPrivate=" +
                    Files.readString(file, StandardCharsets.US_ASCII));

            MappedByteBuffer rw = channel.map(
                    FileChannel.MapMode.READ_WRITE, 0, 5);
            rw.put(1, (byte) 'A');
            rw.force();

            System.out.println("readWriteView=" + readAscii(rw));
            System.out.println("fileAfterReadWrite=" +
                    Files.readString(file, StandardCharsets.US_ASCII));
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac MapModeDemo.java
java MapModeDemo
~~~

**Stdout dự kiến trong môi trường thông thường hỗ trợ memory-mapped files:**

~~~text
readOnly=true
readOnlyText=HELLO
readOnlyBlocked=true
privateView=PELLO
fileAfterPrivate=HELLO
readWriteView=HALLO
fileAfterReadWrite=HALLO
~~~

Giải thích:

- `readOnlyText=HELLO`: mapping READ_ONLY cho phép truy cập byte gốc.
- `readOnlyBlocked=true`: `put` ném `ReadOnlyBufferException` và không làm thay đổi tệp.
- `privateView=PELLO` cùng `fileAfterPrivate=HELLO`: lệnh sửa trên PRIVATE chỉ tác động lên mapping riêng.
- `readWriteView=HALLO` và `fileAfterReadWrite=HALLO`: mapping READ_WRITE sửa byte thứ hai trong tệp.
- `force()` được gọi cho READ_WRITE trước khi kiểm tra lại tệp. Ví dụ dùng dữ liệu ASCII một byte/ký tự để offset 0, 1 và kích thước 5 dễ kiểm chứng.

**Lưu ý môi trường:** memory mapping phụ thuộc hệ điều hành, filesystem và quyền truy cập. Tệp tạm được đăng ký xóa khi JVM thoát vì trên một số hệ điều hành (đặc biệt Windows), việc xóa tệp đang có mapping chưa được giải phóng có thể bị từ chối. Không có lệnh chuẩn Java 17 bảo đảm “unmap ngay lập tức” cho mọi `MappedByteBuffer`.

## 8. Những giới hạn và trạng thái của MappedByteBuffer

### 8.1. Chỉ số buffer không phải offset tuyệt đối của tệp

~~~java
MappedByteBuffer mapped = channel.map(
        FileChannel.MapMode.READ_ONLY, 1_024, 128);

byte first = mapped.get(0); // Byte ở offset 1024 của tệp.
byte last = mapped.get(127); // Byte ở offset 1151 của tệp.
~~~

Đây là mảnh mã minh họa với `channel` đã mở quyền đọc và tệp có ít nhất 1152 byte. Với offset bất kỳ `start`, `mapped.get(i)` tương ứng byte ở offset `start + i`. Vì vậy `mapped.get(1_024)` trong đoạn trên là ngoài phạm vi, dù offset 1024 tồn tại trong tệp gốc.

### 8.2. Trạng thái ByteBuffer vẫn áp dụng

Mapping mới có `position=0`, `limit=capacity=size`. `get()` tương đối tăng position; `get(index)` tuyệt đối không làm thay đổi position. `flip()`, `rewind()`, `slice()` và `duplicate()` điều khiển view/position tương tự các ByteBuffer khác.

Đặc biệt, **`clear()` không xóa byte trong tệp hoặc vùng mapping**. Nó chỉ đặt lại position và limit; với READ_WRITE mapping, thao tác `put` mới mới là thay đổi nội dung.

### 8.3. load(), isLoaded() và force() không cùng vai trò

| API | Mục tiêu | Giới hạn cần nhớ |
| --- | --- | --- |
| `mapped.load()` | Cố gắng nạp các trang tương ứng vùng mapping vào bộ nhớ vật lý. | Best-effort; có thể phát sinh page fault và I/O; không bảo đảm trang luôn resident về sau. |
| `mapped.isLoaded()` | Hỏi xem vùng mapping có vẻ đang resident hay không. | Chỉ là gợi ý; `false` chưa chắc dữ liệu không có trong RAM. |
| `mapped.force()` | Đẩy thay đổi từ READ_WRITE mapping xuống lưu trữ theo hợp đồng. | Không có tác dụng với READ_ONLY/PRIVATE; bảo đảm phụ thuộc tệp trên thiết bị cục bộ. |

Đừng gọi `load()` và cho rằng dữ liệu đã được “đồng bộ xuống đĩa”: `load` giúp nạp dữ liệu **từ tệp lên RAM**; `force` xử lý hướng ghi thay đổi **từ mapping về lưu trữ**.

### 8.4. Đóng FileChannel không lập tức hủy mapping

Sau khi `FileChannel` đóng, vùng mapping vẫn có thể tồn tại và sử dụng được đến khi `MappedByteBuffer` được thu gom rác. Đây là điểm khác biệt với những đối tượng I/O mà việc đóng đồng nghĩa giải phóng toàn bộ tài nguyên bên dưới ngay lập tức.

Hệ quả thực tế:

- Tệp có thể khó xóa, đổi tên hoặc truncate trong khi mapping vẫn còn, tùy hệ điều hành.
- Không giữ số lượng lớn mapping không cần thiết trong thời gian dài.
- Đừng xây dựng logic dựa trên dự đoán thời điểm GC; `MappedByteBuffer` không triển khai `AutoCloseable`.
- Khi cần kiểm soát vòng đời vùng nhớ chính xác, đánh giá giải pháp khác phù hợp runtime, nhưng không giả định có API unmap công khai tương đương `close()` trên Java 17.

## 9. Nguy cơ khi tệp thay đổi đồng thời hoặc bị truncate

Một tệp đang được map có thể bị tiến trình khác sửa, thay kích thước hoặc cắt ngắn. Theo Javadoc Java 17, nhiều chi tiết ánh xạ phụ thuộc hệ điều hành:

- Khi tệp nền thay đổi, **nội dung** buffer có phản ánh thay đổi đó hay không, và phản ánh khi nào, không được bảo đảm thống nhất.
- Nếu tệp bị truncate khiến một phần mapping không còn được hỗ trợ bởi dữ liệu tệp, việc truy cập vùng đó có thể trở nên không khả dụng.
- `MappedByteBuffer` có thể ném ngoại lệ khi đụng vùng mất khả năng truy cập; không suy ra tất cả hệ điều hành sẽ có cách báo lỗi giống nhau.
- `READ_ONLY` không đồng nghĩa dữ liệu bất biến, và `PRIVATE` cũng không mặc nhiên là snapshot bền vững/nhất quán trước sửa đổi bên ngoài.

Nếu ứng dụng cần giao thức đọc/ghi nhất quán, hãy kiểm soát chủ sở hữu file, khóa/phiên bản dữ liệu, hoặc chọn cơ chế lưu trữ có transaction theo yêu cầu. `FileLock` có thể hỗ trợ phối hợp giữa các tiến trình khi filesystem và tiến trình liên quan tuân thủ khóa, nhưng không phải cam kết tuyệt đối trên mọi nền tảng và không loại bỏ mọi vấn đề đồng bộ.

**Không nên map một vùng vượt EOF rồi giả định luôn đọc được byte 0 hoặc file được mở rộng đúng cách.** Hành vi khi vùng map không nằm trọn trong file không được Javadoc Java 17 xác định nhất quán. Đối với READ_ONLY, hãy kiểm tra kích thước hiện có; với READ_WRITE, nếu muốn mở rộng file, hãy xác lập dung lượng tệp có chủ đích trước khi map.

## 10. Chọn chế độ nào cho từng nhu cầu?

| Nhu cầu | Chế độ phù hợp | Lý do |
| --- | --- | --- |
| Đọc tệp nhị phân lớn, tra cứu chỉ số hoặc quét dữ liệu mà không sửa | `READ_ONLY` | View không cho phép ghi, giảm rủi ro sửa nhầm. |
| Sửa bảng chỉ số hoặc vùng cố định và muốn lưu kết quả vào tệp | `READ_WRITE` | Các thay đổi từ mapping được propagate về tệp; có thể gọi `force()` khi yêu cầu độ bền. |
| Tạo biến thể tạm để chạy thử thuật toán trên dữ liệu hiện có | `PRIVATE` | Cho phép sửa view riêng mà không ghi những sửa đổi đó về tệp nền. |
| Muốn snapshot bất biến, chống mọi thay đổi bên ngoài | Không chế độ nào tự bảo đảm | Phải bổ sung bản sao và/hoặc cơ chế nhất quán riêng. |
| Muốn transaction commit/rollback thật sự | Không chế độ nào tự cung cấp | Cần tầng giao dịch hoặc giao thức lưu dữ liệu phù hợp. |
| Tệp nhỏ, chỉ đọc/ghi một lần | Thường dùng `Files.readAllBytes`/`write` hoặc `FileChannel.read/write` | Chi phí thiết lập mapping có thể không đem lại lợi ích. |

Không phải tệp lớn nào cũng nhất thiết nên memory-map. Truy cập tuần tự toàn bộ một tệp một lần có thể dùng stream/channel với buffer đủ lớn và dễ kiểm soát tài nguyên hơn. Memory mapping hữu ích với truy cập ngẫu nhiên hoặc thao tác lặp nhiều lần trên vùng tệp, nhưng cần đo đạc phù hợp workload.

## 11. Các lỗi thường gặp

### Lỗi 1: tưởng PRIVATE giống READ_ONLY

**Sai:** “PRIVATE không sửa file nên chắc chắn buffer này không ghi được.”

**Đúng:** PRIVATE buffer **ghi được**, nhưng các sửa đổi riêng không propagate về file. READ_ONLY mới ném `ReadOnlyBufferException` khi `put`.

### Lỗi 2: tưởng PRIVATE giống READ_WRITE với force bị tắt

**Sai:** “Nếu gọi `privateView.force()` hoặc đóng channel sẽ commit dữ liệu.”

**Đúng:** PRIVATE có semantics copy-on-write; `force()` không ghi các thay đổi của PRIVATE về tệp. Muốn lưu phiên bản riêng phải copy/serialize dữ liệu sang đích khác.

### Lỗi 3: mở FileChannel chỉ đọc rồi map PRIVATE

**Sai:** “PRIVATE không viết file thì mở READ là đủ.”

**Đúng:** Java 17 yêu cầu cả `READ` và `WRITE` để map `PRIVATE`, giống `READ_WRITE`. `READ_ONLY` chỉ yêu cầu `READ`.

### Lỗi 4: gọi FileChannel.force() để flush các byte vừa sửa qua mapping

**Rủi ro:** `FileChannel.force` không được bảo đảm ghi những thay đổi do `MappedByteBuffer.put`. Khi cần đồng bộ byte sửa qua READ_WRITE mapping, gọi `mapped.force()` và xử lý lỗi theo hợp đồng.

### Lỗi 5: giả định dữ liệu đọc từ mapping là snapshot

**Rủi ro:** Tiến trình khác có thể sửa hoặc truncate tệp. Dữ liệu được quan sát và vùng truy cập hợp lệ phụ thuộc nền tảng. Sử dụng ownership/versioning/locking phù hợp hoặc sao chép dữ liệu khi cần ảnh chụp nhất quán.

### Lỗi 6: xem `clear()` là xóa dữ liệu

**Sai:** `mapped.clear()` chỉ reset trạng thái `position/limit`. Nó không đổi tệp và không xóa byte đang ánh xạ. Ghi zero cần `put` rõ ràng và phải có quyền ghi.

### Lỗi 7: bỏ qua byte order và charset

Memory mapping cung cấp **byte**, không tự biết file lưu số theo big-endian/little-endian hay văn bản UTF-8/UTF-16. Khi đọc `getInt()` hoặc `getLong()`, đặt đúng `ByteOrder`. Khi đọc chữ, dùng `CharsetDecoder` hoặc chuyển đoạn byte với charset xác định; không coi một byte bất kỳ luôn là một ký tự.

### Lỗi 8: map cả tệp rất lớn trong một lần

Mỗi `MappedByteBuffer` Java 17 có capacity đo theo `int`. Nếu tệp lớn hơn `Integer.MAX_VALUE` byte, hãy map theo **các cửa sổ vùng byte** (window) có kích thước phù hợp. Mỗi cửa sổ có offset/length riêng, xử lý ranh giới bản ghi nếu dữ liệu trải qua hai cửa sổ. Cũng phải tính chi phí mapping và thời gian vùng map còn sống.

### Lỗi 9: xóa/đổi tên file ngay khi close channel

`close()` trên channel **không** bảo đảm vùng mmap mất hiệu lực ngay lập tức. Trên một số hệ điều hành thao tác xóa/đổi tên có thể gặp lỗi khi mapping còn tồn tại. Thiết kế vòng đời, dọn tệp và tái sử dụng mapping theo các ràng buộc thực tế.

## 12. Bài tập thực hành

1. Tạo file 8 byte chứa `ABCDEFGH`. Map `READ_ONLY` với offset 2, size 4. `get(0)` trả byte nào? **`C`**, vì buffer index 0 ứng với offset 2 trong file.
2. Gọi `put(0, 'X')` trên READ_ONLY. Điều gì xảy ra? **`ReadOnlyBufferException`**; không có thay đổi do lệnh ghi này thực hiện.
3. Trên file `HELLO`, map PRIVATE và đổi byte đầu thành `P`. Buffer thấy gì và file thấy gì? **Buffer thấy `PELLO`; file vẫn `HELLO`**, giả sử không có tác nhân khác sửa file.
4. Map READ_WRITE và sửa byte thứ hai `E` thành `A`. Kết quả file là gì? **`HALLO`** khi thay đổi đã được truyền tới tệp; gọi `force()` khi cần đồng bộ theo hợp đồng.
5. PRIVATE map có thể mở trên channel chỉ có `READ` không? **Không theo API Java 17**; cần cả `READ` lẫn `WRITE`.
6. `MappedByteBuffer.force()` trên PRIVATE có ghi file không? **Không**; các thay đổi private không được propagate.
7. `MappedByteBuffer.load()` có thay `force()` không? **Không**; load cố gắng nạp trang vào RAM, force đồng bộ thay đổi của READ_WRITE mapping về lưu trữ.
8. Tệp bị một tiến trình khác truncate khi ứng dụng đang dùng mapping. Có thể chắc chắn đọc mapping an toàn không? **Không**; vùng có thể trở nên không truy cập được, hành vi phụ thuộc hệ điều hành.
9. Đóng channel xong có bảo đảm xóa được file đã map ngay không? **Không**; mapping có vòng đời riêng tới khi buffer được GC thu hồi.
10. Cần chỉnh một bản dữ liệu có khả năng “commit” và “rollback” giữa nhiều tiến trình. MapMode nào đáp ứng trọn vẹn? **Không chế độ nào tự cung cấp transaction**; phải thiết kế tầng đồng bộ/lưu trữ riêng.

## 13. Tài liệu API Java 17 để tra cứu

- [FileChannel.map và MapMode — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html): hợp đồng mapping, quyền mở channel, vùng ánh xạ và những hành vi phụ thuộc hệ điều hành.
- [FileChannel.MapMode — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.MapMode.html): ba chế độ chuẩn READ_ONLY, READ_WRITE, PRIVATE.
- [MappedByteBuffer — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/MappedByteBuffer.html): `force()`, `load()`, `isLoaded()` và vòng đời mapping.

**Ghi nhớ:** `READ_ONLY` = đọc, cấm ghi qua buffer; `READ_WRITE` = sửa buffer và propagate về tệp; `PRIVATE` = đọc/ghi trên **bản thay đổi riêng kiểu copy-on-write**, không propagate về tệp. Khi sử dụng mmap, cần hiểu thêm `force()`, quyền của `FileChannel`, giới hạn vùng map và vòng đời mapping vốn không kết thúc ngay khi đóng channel.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="memory-mapping-003">Ánh xạ từng vùng và giới hạn kích thước buffer</a>

<details>
<summary>Xem chi tiết</summary>

# Ánh xạ từng vùng và giới hạn kích thước buffer trong Java NIO.2

## 1. Vấn đề: file rất lớn nhưng `MappedByteBuffer` có kích thước `int`

Trong Java NIO.2, `FileChannel.map(MapMode mode, long position, long size)` ánh xạ **một vùng liên tiếp của file** vào không gian địa chỉ của tiến trình và trả về `MappedByteBuffer`. Ứng dụng đọc/ghi byte qua API `ByteBuffer` mà không cần gọi `channel.read`/`write` cho từng đoạn dữ liệu.

Tuy nhiên, có hai kích thước rất khác nhau:

- **Vị trí trên file** dùng `long`, cho phép xác định một offset lớn hơn 2 GiB nếu file và hệ thống tệp hỗ trợ.
- **Kích thước của một vùng ánh xạ** truyền vào `map(..., size)` không âm và **không lớn hơn `Integer.MAX_VALUE`** (2.147.483.647 byte), bởi `MappedByteBuffer.capacity()`, `position()`, `limit()`, `get(int)` đều dùng chỉ số `int`.

Vì vậy, **không thể dùng một `MappedByteBuffer` duy nhất để ánh xạ toàn bộ file lớn hơn giới hạn này**. Giải pháp thông dụng là **ánh xạ lần lượt từng vùng nhỏ (window/chunk)**, mỗi lần có một offset `long` và độ dài phù hợp với `int`.

Mục tiêu sau bài học:

1. Hiểu giới hạn của `MappedByteBuffer` và không nhầm với giới hạn kích thước file.
2. Thiết kế vòng lặp `FileChannel.map` theo từng cửa sổ, không bỏ sót hoặc đọc trùng byte.
3. Chuyển đổi đúng giữa **offset tuyệt đối của file (`long`)** và **index cục bộ của vùng ánh xạ (`int`)**.
4. Xử lý phần cuối file nhỏ hơn cửa sổ, bản ghi nằm vắt qua ranh giới, giới hạn tài nguyên và vòng đời mapping.
5. Biết lúc nào nên dùng mapping và lúc nào chọn `FileChannel.read`/`transferTo`.

## 2. Cơ chế ánh xạ file vào bộ nhớ

Hệ điều hành quản lý một ánh xạ từ vùng địa chỉ ảo của tiến trình tới nội dung file. Tùy hệ điều hành và thời điểm truy cập, trang nhớ có thể được nạp từ file qua page fault, được giữ trong cache hoặc được đẩy ra bộ nhớ phụ.

```text
File lớn trên ổ đĩa (offset kiểu long):
0                                                                fileSize
|--------------------|--------------------|--------------------|--------|
     cửa sổ 0             cửa sổ 1             cửa sổ 2          cuối

FileChannel.map(READ_ONLY, offset, length)
                       |
                       v
MappedByteBuffer (index kiểu int):
0                                     length
|------------------------------------------|
      get(0), get(1), ... get(length - 1)
```

Một `MappedByteBuffer` mới từ `FileChannel.map` có `position=0`, `limit=capacity=size`, mark chưa xác định. Từ góc nhìn của buffer, chỉ số `0` ứng với **byte đầu tiên của vùng được ánh xạ**, tức file offset bằng `position` truyền cho `map`.

**Ánh xạ 256 MiB không đồng nghĩa JVM phải ngay lập tức tạo một `byte[]` 256 MiB trên heap.** `MappedByteBuffer` là direct byte buffer, sử dụng cơ chế bộ nhớ ánh xạ của hệ điều hành; tài nguyên địa chỉ ảo và trang vật lý vẫn có giới hạn, và truy cập có thể phát sinh I/O hoặc lỗi. Không suy luận rằng mapping “miễn phí RAM” hoặc luôn nhanh hơn đọc truyền thống.

## 3. Giới hạn `Integer.MAX_VALUE` có nghĩa gì?

```java
FileChannel channel = /* một FileChannel đã mở */;
long fileSize = channel.size();    // long, có thể > 2 GiB
long offset = 3_000_000_000L;     // Có thể là vị trí hợp lệ trong file lớn
long length = 64L * 1024 * 1024; // Một cửa sổ 64 MiB

MappedByteBuffer mapped =
        channel.map(FileChannel.MapMode.READ_ONLY, offset, length);
```

Đây là **đoạn minh họa trong phương thức**, chỉ thành công nếu channel đọc được, file chứa toàn bộ vùng cần ánh xạ và hệ điều hành còn đủ tài nguyên. Không dùng nguyên văn `/* ... */` như một chương trình độc lập.

`FileChannel.map` nhận `position`/`size` kiểu `long`, nhưng **hợp đồng vẫn yêu cầu `size <= Integer.MAX_VALUE`**. Số `offset` lớn không phải vấn đề tự thân; vấn đề là kích thước **một lần ánh xạ**.

| Đại lượng | Kiểu số | Phạm vi/ý nghĩa |
| --- | --- | --- |
| `channel.size()` | `long` | Kích thước file hiện tại; file có thể lớn hơn 2 GiB |
| `map(..., position, size)` | `long` cho cả hai đối số | `position >= 0`, `0 <= size <= Integer.MAX_VALUE` |
| `mapped.capacity()` | `int` | Số byte thuộc **một** mapping |
| `mapped.position()`, `limit()`, `remaining()` | `int` | Trạng thái/giới hạn cục bộ của mapping |
| `mapped.get(index)` | `int` | Index tương đối với đầu mapping, không phải vị trí toàn file |
| `offset + mappedIndex` | `long` sau khi mở rộng kiểu | Vị trí logic trên file |

**Không ép `fileSize` từ `long` sang `int`** để gọi `map`. Với file lớn hơn 2 GiB, ép kiểu có thể làm số bị tràn hoặc âm. Cũng không cấp một mảng Java dài bằng toàn bộ file: mảng Java dùng chỉ số `int`, và cả bộ nhớ heap lẫn GC đều có giới hạn riêng.

## 4. Thuật toán ánh xạ từng cửa sổ

Giả sử `fileSize=10` byte, chọn `windowSize=4`:

```text
File offsets:  0 1 2 3 | 4 5 6 7 | 8 9
              A B C D | E F G H | I J
Cửa sổ 1:     [0, 4)  length 4
Cửa sổ 2:     [4, 8)  length 4
Cửa sổ 3:     [8,10)  length 2
```

Công thức mỗi vòng:

```text
offset = 0
while offset < fileSize:
    remainingInFile = fileSize - offset
    length = min(windowSize, remainingInFile)
    map(file, offset, length)
    xử lý chính xác length byte
    offset = offset + length
```

Ba yêu cầu quan trọng:

- **Không mất byte:** bắt đầu cửa sổ sau đúng tại `offset + length`, không cộng một số cố định khi cửa sổ cuối ngắn hơn.
- **Không ánh xạ vượt EOF:** chỉ chọn `length <= fileSize - offset`. Đọc vị trí ngoài file không có ngữ nghĩa giống tự động đọc byte bằng 0; Java không bảo đảm hành vi khi mapping vượt phạm vi file.
- **Không gây tràn số:** nếu bảo đảm `0 <= offset <= fileSize` và `length <= fileSize-offset`, thì cập nhật `offset += length` không vượt `fileSize`. Tránh viết `offset + windowSize` để kiểm tra khi hai số có thể lớn gần `Long.MAX_VALUE`.

Với file rỗng (`fileSize==0`), vòng lặp không thực hiện mapping nào. Nếu cửa sổ được cấu hình `0` hoặc âm, phải từ chối trước khi lặp để tránh vòng lặp không tiến triển.

## 5. Ví dụ Java 17+ chạy được: ba cửa sổ trên file 10 byte

Ví dụ sau tạo file tạm chứa `ABCDEFGHIJ`, đọc bằng các ánh xạ dài tối đa 4 byte, sau đó chỉnh byte `F` thành `f` qua ánh xạ ghi và gọi `force()`.

Tạo file `MappedWindowsDemo.java`:

```java
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class MappedWindowsDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("mapped-windows-", ".dat");

        // Bản demo dùng temp file. Yêu cầu xóa khi JVM kết thúc;
        // việc xóa có thể còn phụ thuộc OS nếu vùng map đang được giữ.
        file.toFile().deleteOnExit();
        Files.write(file, "ABCDEFGHIJ".getBytes(StandardCharsets.US_ASCII));

        try (FileChannel channel = FileChannel.open(
                file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {

            long fileSize = channel.size();
            long windowSize = 4L;

            for (long offset = 0; offset < fileSize;) {
                long length = Math.min(windowSize, fileSize - offset);
                MappedByteBuffer window = channel.map(
                        FileChannel.MapMode.READ_ONLY, offset, length);

                byte[] bytes = new byte[window.remaining()];
                window.get(bytes);

                System.out.printf("range=[%d,%d), data=%s%n",
                        offset, offset + length,
                        new String(bytes, StandardCharsets.US_ASCII));

                offset += length;
            }

            // File offset 4..7 gồm E F G H.
            MappedByteBuffer writable = channel.map(
                    FileChannel.MapMode.READ_WRITE, 4L, 4L);
            writable.put(1, (byte) 'f'); // index 1 trong window = file offset 5
            writable.force();

            System.out.println("after edit: "
                    + Files.readString(file, StandardCharsets.US_ASCII));
        }
    }
}
```

Biên dịch và chạy trong JDK 17+:

```bash
javac MappedWindowsDemo.java
java MappedWindowsDemo
```

Stdout dự kiến trên hệ thống tệp bình thường, không có tác nhân ngoài thay đổi file:

```text
range=[0,4), data=ABCD
range=[4,8), data=EFGH
range=[8,10), data=IJ
after edit: ABCDEfGHIJ
```

**Giải thích kỹ:**

1. Cửa sổ đầu có `offset=0,length=4`, vì vậy `window.get(0)` (trước khi `get(bytes)`) tương ứng byte `A` ở file offset 0.
2. Cửa sổ hai có `offset=4,length=4`: index cục bộ 0..3 tương ứng vị trí file 4..7, tức `EFGH`.
3. Cửa sổ cuối còn hai byte: `Math.min(4,10-8)=2`. Không cố map 4 byte từ offset 8, vì như vậy vượt cuối file.
4. Mapping ghi tại offset 4, độ dài 4: `put(1, 'f')` chỉnh **byte tại file offset `4+1=5`**, không phải byte có file offset 1.
5. `writable.force()` yêu cầu đẩy thay đổi mapping ghi xuống thiết bị lưu trữ theo hợp đồng API (đảm bảo phụ thuộc loại thiết bị). File được đọc lại thành `ABCDEfGHIJ`.

Ví dụ tạo file tạm và đăng ký `deleteOnExit()`; không in đường dẫn ngẫu nhiên để giữ stdout ổn định. Cần biết rằng trên một số hệ điều hành, nhất là Windows, mapping có thể còn giữ tài nguyên/file lock cho tới khi bộ đệm được thu gom, khiến thao tác xóa file gặp giới hạn; `deleteOnExit` cũng không phải cơ chế bảo đảm xóa an toàn hay xóa ngay tức thì.

## 6. Mẫu xử lý file lớn hơn 2 GiB, không cần cấp phát mảng lớn

Một phương thức xử lý file từng byte bằng cửa sổ cố định:

```java
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class WindowedScan {
    // Chọn cửa sổ nhỏ hơn giới hạn Integer.MAX_VALUE.
    private static final long WINDOW_BYTES = 256L * 1024 * 1024;

    public static long checksum(Path path) throws IOException {
        long sum = 0;

        try (FileChannel channel = FileChannel.open(
                path, StandardOpenOption.READ)) {

            long fileSize = channel.size();
            for (long offset = 0; offset < fileSize;) {
                long mappedSize = Math.min(
                        WINDOW_BYTES, fileSize - offset);

                MappedByteBuffer chunk = channel.map(
                        FileChannel.MapMode.READ_ONLY, offset, mappedSize);

                while (chunk.hasRemaining()) {
                    sum += Byte.toUnsignedInt(chunk.get());
                }
                offset += mappedSize;
            }
        }

        return sum;
    }
}
```

Đây là **lớp Java hợp lệ và có thể biên dịch** (không có `main`; gọi `WindowedScan.checksum(path)` từ ứng dụng hay test). Tổng `sum` là tổng giá trị byte 0..255 theo modulo tràn `long` nếu dữ liệu cực lớn; nó chỉ là phép thống kê minh họa, **không phải checksum mật mã** hay công cụ xác minh tính toàn vẹn file an toàn. Cửa sổ 256 MiB chỉ là lựa chọn mẫu; dung lượng phù hợp phụ thuộc hệ điều hành, 32/64-bit, số mapping còn tồn tại, đặc tính file và phép tính.

Điểm quan trọng là chỉ **độ dài của từng `chunk`** phải vừa `int`; biến `offset` và `fileSize` vẫn là `long`. Không chuyển toàn bộ dữ liệu vào `byte[]`.

`FileChannel.map` không bắt buộc mọi cửa sổ đều phải cùng kích thước. Phần cuối file có thể ngắn hơn nhiều. Nếu file tiếp tục được ghi hoặc bị truncate bởi tiến trình khác trong lúc quét, kích thước chụp ban đầu có thể không còn đúng; mẫu này giả định file không thay đổi trong quá trình đọc.

## 7. Cách tính offset tuyệt đối và index trong từng cửa sổ

Giả sử muốn truy cập byte ở vị trí tuyệt đối `absoluteOffset` của một file:

```text
windowStart = floor(absoluteOffset / windowSize) * windowSize
indexInWindow = absoluteOffset - windowStart
byte = mappedWindow.get((int) indexInWindow)
```

Nếu `windowSize=4` và cần đọc file offset 9:

```text
windowStart = (9 / 4) * 4 = 8
indexInWindow = 9 - 8 = 1
mapping cho [8,10) -> get(1) lấy 'J'
```

Hãy dùng các phép tính `long` trước khi ép index về `int`. Chỉ ép sau khi đã xác minh `0 <= indexInWindow < mappedWindow.limit()`. Không truyền `absoluteOffset` trực tiếp cho `mapped.get(int)`: nó là chỉ số của file chứ không phải của mapping.

Khi một cửa sổ được tạo theo yêu cầu thực tế (không cố định kích thước), tính đơn giản hơn:

```java
long localOffset = absoluteOffset - windowStart;

if (localOffset < 0 || localOffset >= window.limit()) {
    throw new IndexOutOfBoundsException("Outside current window");
}
byte value = window.get(Math.toIntExact(localOffset));
```

Đoạn trên minh họa, giả định `window` và `windowStart` là mapping/offset hiện tại. `Math.toIntExact` giúp phát hiện chuyển đổi ngoài giới hạn thay vì âm thầm tràn số. Nên lưu `windowStart` cùng `MappedByteBuffer` trong logic ứng dụng nếu cần truy cập ngẫu nhiên.

## 8. Ranh giới cửa sổ: bản ghi có thể vắt qua hai mapping

Một lỗi tinh vi xảy ra khi đọc dữ liệu có cấu trúc: **biên giới mapping không nhất thiết trùng biên giới bản ghi**.

Ví dụ file gồm các bản ghi 6 byte, nhưng cửa sổ chỉ 4 byte:

```text
File offsets:     0 1 2 3 | 4 5 6 7 | 8 9 10 11
Bản ghi 1:        [----------- 6 byte --------]
Bản ghi 2:                    [----------- 6 byte --------]
Cửa sổ:          [4 byte]   [4 byte]    [4 byte]
```

Nếu mỗi mapping chỉ gọi `getInt()` hoặc giải mã một cấu trúc mà không kiểm tra `remaining()`, phần dữ liệu ở cuối cửa sổ có thể gây `BufferUnderflowException` dù file **vẫn còn byte ở cửa sổ kế tiếp**.

Có ba chiến lược thông dụng:

1. **Cửa sổ căn theo kích thước bản ghi cố định.** Nếu mỗi record đúng 16 byte, chọn `windowSize` chia hết cho 16 và chỉ ánh xạ các phần có đủ record; xử lý phần cuối riêng. Cần lưu ý offset bắt đầu của cấu trúc cũng phải căn phù hợp.
2. **Giữ đoạn chưa đủ (carry-over).** Sao chép một vài byte cuối cửa sổ sang buffer phụ nhỏ, rồi ghép với những byte đầu cửa sổ kế tiếp trước khi parse. Cách này phù hợp format có header hoặc record biến độ dài.
3. **Ánh xạ vùng chồng lấn (overlapping windows).** Mỗi cửa sổ có thể chồng thêm vài byte từ vùng trước/sau để bảo đảm đọc trọn record, nhưng phải xác định chính xác vùng nào thuộc trách nhiệm xử lý của mỗi lượt để **không đếm hoặc ghi trùng byte**.

Ví dụ nếu một số nguyên 4 byte bắt đầu ở file offset 3, trong cửa sổ `[0,4)` chỉ còn một byte; `getInt(3)` không hợp lệ. Cần lấy ba byte còn lại từ cửa sổ `[4,8)` hoặc chọn chiến lược thiết kế khác. Đừng tăng `limit` vượt `capacity` để “mở rộng” một mapping; không thể như vậy.

### 8.1. UTF-8, ký tự và ranh giới window

Nếu file là văn bản UTF-8, một ký tự Unicode có thể mã hóa thành 2–4 byte, nằm vắt qua hai cửa sổ. Chuyển trực tiếp `new String(bytesOfOneWindow, UTF_8)` cho mỗi cửa sổ **có thể làm hỏng ký tự ở ranh giới**. Hãy giữ các byte UTF-8 chưa đủ sang cửa sổ sau, hoặc dùng `CharsetDecoder` ở chế độ streaming với trạng thái giải mã và xử lý `CoderResult.UNDERFLOW` đúng quy tắc.

Điều tương tự xảy ra với delimiter nhiều byte, header độ dài, checksum và cấu trúc nhị phân: **mapping chia theo kích thước vật lý; parser chia theo ranh giới ngữ nghĩa**. Hai việc phải được phối hợp.

## 9. Chọn kích thước cửa sổ bao nhiêu?

Không có một kích thước áp dụng tốt cho mọi hệ thống. Chọn `64 MiB`, `128 MiB` hoặc `256 MiB` có thể hợp lý để thử nghiệm, nhưng phải đo với dữ liệu và môi trường thực tế.

Yếu tố ảnh hưởng:

| Yếu tố | Ảnh hưởng |
| --- | --- |
| Giới hạn API | Mỗi mapping có kích thước tối đa `Integer.MAX_VALUE`; chọn nhỏ hơn để có dư địa |
| Không gian địa chỉ | Tiến trình 32-bit dễ cạn địa chỉ ảo hơn 64-bit |
| Số mapping còn tồn tại | Nhiều cửa sổ cùng sống có thể tăng áp lực địa chỉ và tài nguyên OS |
| Pattern truy cập | Đọc tuần tự, random access, cập nhật theo record có đặc tính khác nhau |
| Kích thước bản ghi | Nếu có thể căn biên, giảm thao tác carry-over và số lần mapping |
| Đặc tính thiết bị | SSD/HDD, network file system, page cache, latency, throughput |
| Khả năng thay đổi file | Mapping file bị truncate bên ngoài có rủi ro hơn đọc file tĩnh |
| Độ bền dữ liệu | Map `READ_WRITE` cần chiến lược force/persist phù hợp |

**Quá nhỏ:** tạo nhiều mapping, tăng overhead và số lần quản lý cửa sổ. **Quá lớn:** tăng áp lực không gian địa chỉ, chi phí map, page fault và tồn tại của mapping. Memory-mapped I/O không luôn nhanh hơn `FileChannel.read`, đặc biệt với file nhỏ hoặc đường đi đọc tuyến tính đơn giản.

Với `WINDOW_BYTES`, thực thi nên xác minh `1 <= WINDOW_BYTES <= Integer.MAX_VALUE` trước khi bắt đầu. Tránh dùng đúng mức tối đa như một mặc định bất biến, nhất là khi triển khai trên đa nền tảng.

## 10. Ba chế độ ánh xạ và khả năng sửa dữ liệu

`FileChannel.MapMode` cung cấp các lựa chọn chính:

| Chế độ | Điều kiện mở channel | Có được gọi `put`? | Có ghi thay đổi vào file gốc? |
| --- | --- | --- | --- |
| `READ_ONLY` | Mở để đọc | Không; sẽ ném `ReadOnlyBufferException` | Không |
| `READ_WRITE` | Mở để đọc **và** ghi | Có | Thay đổi được truyền tới file, thời điểm cụ thể phụ thuộc hệ điều hành; có thể dùng `force()` |
| `PRIVATE` | Mở để đọc **và** ghi | Có, qua copy-on-write | Thay đổi trong vùng riêng **không** lan về file gốc |

`PRIVATE` chỉ tạo bản sao khi cần (copy-on-write theo trang) và **không thay thế** chế độ `READ_WRITE` nếu mục tiêu là chỉnh sửa file. Gọi `force()` trên mapping không phải `READ_WRITE` có thể không có tác dụng.

Để thao tác nhiều cửa sổ ghi, phải chú ý các cửa sổ trùng nhau cùng ánh xạ một file và cách cập nhật đồng thời. Không tự suy luận rằng mọi thread/process thấy thay đổi theo một thứ tự nhất định; phải thiết kế đồng bộ hóa và nhất quán file riêng.

## 11. `force()`, `load()` và `isLoaded()` không phải cùng một chức năng

Ba phương thức thường gặp của `MappedByteBuffer`:

- **`force()`**: với mapping `READ_WRITE`, yêu cầu đẩy các thay đổi đã thực hiện tới thiết bị lưu trữ. Với file trên thiết bị local, API có cam kết về việc ghi thay đổi khi hoàn thành; với thiết bị không local, cam kết này yếu hơn. Phương thức có thể ném `UncheckedIOException` khi đẩy dữ liệu gặp I/O lỗi.
- **`force(index, length)`**: chỉ định một phần vùng của buffer cần đẩy; `index` và `length` là **chỉ số cục bộ**, không phải file offset tuyệt đối. Hệ thống có thể ghi thêm dữ liệu ngoài phạm vi chỉ định do kích thước khối thiết bị.
- **`load()`**: nỗ lực nạp nội dung mapping vào bộ nhớ vật lý; đây là **best effort**, có thể gây page fault/I/O, không có nghĩa dữ liệu sẽ luôn nằm trong RAM.
- **`isLoaded()`**: trả một gợi ý liệu các trang có nhiều khả năng đã ở RAM hay không; kết quả **không phải bảo đảm** cho lần đọc tiếp theo.

Với các cửa sổ ghi, có thể `force()` mỗi cửa sổ sau khi hoàn tất chỉnh sửa, hoặc áp dụng một chính sách đồng bộ theo batch tùy mức độ bền yêu cầu. `channel.force(true)` là thao tác riêng của `FileChannel`; không được đồng nhất vô điều kiện với `mapped.force()` về phạm vi/thời điểm.

## 12. Vòng đời của mapping khác vòng đời của `FileChannel`

Một điểm rất quan trọng: **đóng `FileChannel` không tự động hủy mọi `MappedByteBuffer` đã lấy từ channel đó**. Theo hợp đồng API, mapping vẫn hợp lệ cho đến khi chính buffer của nó được garbage-collected. Java 17 không cung cấp public `MappedByteBuffer.unmap()` tiêu chuẩn.

```java
MappedByteBuffer mapped;
try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
    mapped = ch.map(FileChannel.MapMode.READ_ONLY, 0L, 1024L);
}
// Channel đã đóng; mapping vẫn có thể còn hiệu lực.
byte first = mapped.get(0);
```

Đây là đoạn minh họa, giả sử `file` đã khai báo và file chứa tối thiểu 1024 byte; đặt code trong phương thức xử lý `IOException`. Không nên cố điều khiển việc unmap bằng `System.gc()`: JVM không bảo đảm GC chạy đúng lúc.

**Hệ quả đối với ánh xạ từng vùng:**

- Đừng giữ tất cả cửa sổ trong một `List<MappedByteBuffer>` khi không thực sự cần. Với file lớn, cách đó có thể giữ hàng trăm mapping và nhiều tài nguyên OS.
- Sau khi xử lý xong một vùng, bỏ reference khi không cần dùng lại. Việc giải phóng mapping vẫn do JVM/GC quản lý; không thể khẳng định nó được unmap ngay khi biến ra khỏi scope.
- Khi thao tác xóa, thay thế hoặc rename file đã map, Windows và một số provider có thể báo lỗi do mapping còn giữ tài nguyên. Hãy thiết kế lifecycle, kiểm tra hành vi trên hệ điều hành mục tiêu và xử lý lỗi.
- Khi cần vòng đời ánh xạ được quản lý tường minh, có thể nghiên cứu các API mới hơn ở JDK tương ứng; ví dụ Java 22+ có Foreign Function & Memory API với `MemorySegment`, nhưng chữ ký và khả năng hỗ trợ phụ thuộc JDK. Bài này dùng API Java 17+ ổn định là `MappedByteBuffer`.

## 13. File bị thay đổi hoặc truncate trong lúc đang được map

Memory mapping chỉ ổn định khi những điều kiện của file cũng ổn định. Java API nêu rằng hành vi khi một mapping yêu cầu vùng vượt ngoài file, hoặc khi kích thước/nội dung file bị thay đổi sau khi map, **có phần phụ thuộc hệ điều hành và không được đặc tả đầy đủ**.

Cần chú ý:

1. Nếu một tiến trình khác **truncate file** xuống ngắn hơn vùng đã ánh xạ, một phần mapping có thể trở nên không truy cập được. Hậu quả có thể gồm exception hoặc lỗi hệ điều hành; không giả định sẽ tự trả các byte 0.
2. Nếu file **tăng kích thước**, `MappedByteBuffer` cũ không tự tăng `capacity`. Phải tạo mapping mới cho vùng bổ sung nếu logic ứng dụng hỗ trợ.
3. `channel.size()` tại đầu vòng quét chỉ là ảnh chụp của thời điểm đó. Nếu file thay đổi đồng thời, dữ liệu quan sát có thể không nhất quán.
4. Dùng file lock hay phối hợp cấp ứng dụng khi quy trình yêu cầu snapshot ổn định; không giả định file lock sẽ bảo vệ dữ liệu trong mọi provider hoặc mọi hệ điều hành theo cùng cách.
5. Đừng map một vùng rất lớn ngoài EOF rồi mong `READ_WRITE` sẽ luôn cấp phát nội dung có thể truy cập. Có trường hợp API cho phép mapping vùng vượt file nhưng **nội dung/phạm vi bên ngoài hiện có có hành vi không được bảo đảm**; với nhu cầu tăng file, hãy thiết kế chiến lược resize và xác nhận sau đó.

Ở hệ thống sản xuất, đặc biệt với dữ liệu nhạy cảm hoặc tính nhất quán cao, file immutable hoặc phiên bản hóa giúp giảm rất nhiều rủi ro trong quá trình quét từng vùng.

## 14. Các lỗi API và lỗi thiết kế dễ gặp

| Sai lầm | Vì sao sai | Cách khắc phục |
| --- | --- | --- |
| `channel.map(READ_ONLY, 0, channel.size())` với file >2 GiB | Một mapping vượt `Integer.MAX_VALUE` | Chia thành windows nhỏ hơn giới hạn |
| Ép `long fileSize` xuống `int` | Dễ overflow/truncate | Giữ `long` cho offset, fileSize, tính window |
| Cửa sổ cuối vẫn map bằng `windowSize` | Vượt EOF | `min(windowSize, fileSize-offset)` |
| `offset += windowSize` bất kể vùng cuối | Dễ tính sai offset hoặc bỏ sót vùng khi logic thay đổi | Tăng đúng `mappedSize` thực tế |
| Gọi `mapped.get((int) absoluteOffset)` | Buffer index cục bộ, không phải file offset | `(int)(absoluteOffset-windowStart)` sau kiểm tra biên |
| Chọn `windowSize=0` | Vòng lặp không tiến triển | Validate giá trị cấu hình từ đầu |
| Đọc `getInt()` khi còn ít hơn 4 byte trong mapping | `BufferUnderflowException` dù file còn byte | Căn window theo record hoặc giữ carry-over |
| Giải mã từng vùng UTF-8 bằng `new String` | Có thể cắt ngang ký tự nhiều byte | Streaming decoder/carry-over |
| Map hàng nghìn cửa sổ và giữ mọi reference | Tài nguyên mapping/địa chỉ ảo tăng | Xử lý tuần tự, quản lý lifetime, đo tải |
| Gọi `force()` rồi coi mọi thay đổi đã bền trên mọi network FS | Cam kết khác nhau theo thiết bị/provider | Thiết kế durability theo môi trường thực |
| `channel.close()` rồi tin mapping đã unmap | Mapping có vòng đời riêng | Không giả định unmap tức thời |
| Truncate file khi còn mapping hoạt động | Mapping có thể mất tính hợp lệ | Điều phối resize với readers/writers |
| Nghĩ mapping luôn nhanh hơn `read()` | Page fault/chi phí ánh xạ có thể cao | Benchmark theo kích thước và kiểu truy cập |

### Ngoại lệ thường gặp

- `IllegalArgumentException`: offset âm, size âm hoặc size vượt giới hạn, hoặc tiền điều kiện khác của `map` bị vi phạm.
- `NonReadableChannelException`: muốn map `READ_ONLY` nhưng channel không được mở để đọc.
- `NonWritableChannelException`: muốn map `READ_WRITE`/`PRIVATE` nhưng thiếu quyền ghi (và đọc).
- `ReadOnlyBufferException`: cố `put` trên mapped buffer `READ_ONLY`.
- `IOException`: lỗi I/O trong khi tạo mapping hay thao tác channel.
- `IndexOutOfBoundsException`/`BufferUnderflowException`: truy cập index hoặc đọc nhiều hơn `limit` của một cửa sổ.

Không gộp tất cả thành “file quá lớn”. Cần log loại thao tác, file size, offset, mapped size, mode và trạng thái channel để phân tích nguyên nhân thực.

## 15. Mapping theo vùng so với `FileChannel.read()`

Memory mapping hữu ích khi thường xuyên đọc ngẫu nhiên trên file lớn hoặc cần thao tác nhiều byte mà không muốn thực hiện nhiều system call riêng lẻ. Nhưng dùng `FileChannel.read(buffer, offset)` vẫn là lựa chọn rất tốt cho tuần tự hoặc khi muốn quản lý trực tiếp kích thước buffer và vòng đời tài nguyên:

| Tiêu chí | `MappedByteBuffer` từng vùng | `FileChannel.read(ByteBuffer, long)` |
| --- | --- | --- |
| Truy cập nội dung | `get/put` trên vùng nhớ ánh xạ | Đọc dữ liệu vào buffer |
| Giới hạn thao tác | Một mapping tối đa `Integer.MAX_VALUE` byte | Một `ByteBuffer` cũng dùng capacity `int`; lặp read nhiều lần |
| Vị trí trên file | `long` offset khi `map` | `long` offset khi `read` |
| Chi phí | Tạo mapping, page fault, địa chỉ ảo | Chi phí read/syscall, copy dữ liệu vào buffer |
| Vòng đời | Mapping có thể tồn tại sau `channel.close()` | Buffer đọc có thể tái sử dụng, không tạo file mapping |
| File bị thay đổi/truncate | Có rủi ro truy cập mapped region phụ thuộc OS | Read tiếp có thể trả số byte mới, EOF hoặc IOException |
| Tính phù hợp | Random access/scan khối lớn tùy workload | Stream/scan tuần tự, parser theo block, lifetime dễ kiểm soát |

Không có quy tắc “file trên 2 GiB thì bắt buộc memory map”. File lớn vẫn có thể xử lý bằng `FileChannel.read` qua nhiều vòng buffer nhỏ. Nếu chỉ sao chép file sang channel khác, xem thêm `transferTo`/`transferFrom` và xử lý số byte thực chuyển, thay vì map rồi chép thủ công.

## 16. Bài tập và lời giải

**Bài 1.** File dài 10 byte, window size 4. Các khoảng cần map là gì?

**Đáp án:** `[0,4)`, `[4,8)`, `[8,10)`; lần lượt 4, 4 và 2 byte. Không map vùng `[8,12)`.

**Bài 2.** File 5 GiB có thể được xử lý bằng `MappedByteBuffer` Java 17 không?

**Đáp án:** Có. Không tạo một mapping 5 GiB; chia thành nhiều mapping, ví dụ 256 MiB/cửa sổ. File offset và fileSize dùng `long`.

**Bài 3.** Mapping `map(READ_ONLY, 1_000L, 100L)`. Byte ở file offset 1.037 ứng với index bao nhiêu?

**Đáp án:** `1037 - 1000 = 37`. Gọi `mapped.get(37)`.

**Bài 4.** Vì sao `mapped.getInt(98)` không được dùng trên vùng 100 byte để đọc 4 byte?

**Đáp án:** Chỉ còn hai byte có chỉ số 98 và 99. Phải đọc thêm ở mapping sau hoặc chọn vùng có đủ 4 byte; absolute `getInt(98)` không thể vượt `limit=100`.

**Bài 5.** Có thể viết `mapped.put(0, (byte)42)` vào mapping `READ_ONLY` không?

**Đáp án:** Không; ném `ReadOnlyBufferException`. Muốn sửa dữ liệu file thì phải mở channel có quyền đọc/ghi và map `READ_WRITE`.

**Bài 6.** Đóng `FileChannel` có giải phóng ngay mapped memory không?

**Đáp án:** Không bảo đảm. `MappedByteBuffer` và mapping của nó có thể vẫn tồn tại tới khi buffer được garbage-collected.

**Bài 7.** Chia UTF-8 theo từng mapping 64 MiB rồi `new String` độc lập trên từng vùng có luôn đúng không?

**Đáp án:** Không. Các byte của cùng một ký tự có thể nằm ở hai vùng; dùng streaming `CharsetDecoder` hoặc cơ chế giữ byte dư.

**Bài 8.** Khi kích thước file lớn hơn `Integer.MAX_VALUE`, có nên viết `int length = (int) channel.size()`?

**Đáp án:** Không. Phép ép có thể tràn và sinh giá trị sai; `channel.size()` phải giữ `long`, chỉ độ dài của cửa sổ đã giới hạn mới được chuyển an toàn thành `int`.

## 17. Checklist triển khai an toàn

1. Mở `FileChannel` với quyền phù hợp `MapMode`.
2. Đọc `fileSize` vào biến `long`; xác định liệu file có thể thay đổi đồng thời không.
3. Chọn `windowSize` dương, không vượt `Integer.MAX_VALUE`, kiểm tra cấu hình.
4. Duyệt `offset` từ 0 khi `offset < fileSize`.
5. Tính `length=Math.min(windowSize, fileSize-offset)` với số `long`.
6. `map(mode, offset, length)`, xử lý đúng `limit/capacity` của mapping và chuyển `absoluteOffset - offset` sang index cục bộ.
7. Nếu parser có record dài hoặc UTF-8, xử lý phần vắt ngang boundary.
8. Với ghi dữ liệu, xác định chế độ `READ_WRITE`, chính sách `force()`, lock và crash consistency.
9. Không giữ quá nhiều mapping, không giả định `close()` unmap ngay; kiểm tra Windows/Linux ở môi trường chạy thực.
10. Benchmark với `FileChannel.read`/I/O khác trước khi khẳng định mapping tốt hơn.

## Tài liệu API

- Java SE 21 — `FileChannel.map(MapMode,long,long)`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html
- Java SE 21 — `MappedByteBuffer`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/MappedByteBuffer.html
- Java SE 21 — `ByteBuffer`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html
- Java SE 21 — `FileChannel.MapMode`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.MapMode.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="memory-mapping-004">Đọc ghi dữ liệu qua vùng nhớ đã ánh xạ</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc ghi dữ liệu qua vùng nhớ đã ánh xạ (Java NIO.2)

## 1. Vấn đề và mục tiêu

Khi xử lý tệp nhị phân lớn, Java cung cấp hai cách tiếp cận phổ biến:

1. Đọc/ghi byte qua `FileChannel.read(ByteBuffer)` và `FileChannel.write(ByteBuffer)`. Chương trình tự chuẩn bị bộ đệm, gọi I/O rồi xử lý số byte thực tế đã truyền.
2. **Memory-mapped file**: dùng `FileChannel.map(...)` để ánh xạ một **vùng byte trong tệp** thành `MappedByteBuffer`. Chương trình truy cập vùng này bằng `get`, `put` và các phương thức của `ByteBuffer`, còn hệ điều hành quản lý cơ chế ánh xạ giữa bộ nhớ ảo và các trang dữ liệu tệp.

Vùng nhớ đã ánh xạ vẫn liên kết với một tệp thực tế, nhưng ứng dụng thao tác qua chỉ số của buffer. Việc ánh xạ **không có nghĩa toàn bộ tệp được nạp ngay lên heap Java**, cũng không đồng nghĩa mọi thay đổi byte được ghi bền vững xuống thiết bị ngay sau mỗi `put`.

```text
FileChannel ── map(mode, fileOffset, length) ──▶ MappedByteBuffer
    │                                             │
    │                                       get / put / getInt / putLong ...
    │                                             │
    └── tệp trên hệ thống tệp ◀── page cache / ánh xạ bộ nhớ của OS
```

**Mục tiêu:** tạo mapping đúng cách; đọc/ghi dữ liệu thông qua `MappedByteBuffer`; phân biệt `READ_ONLY`, `READ_WRITE`, `PRIVATE`; nắm `position`, `limit`, chỉ số byte so với offset trong tệp; hiểu `force()`, hạn chế về kích thước, hiệu năng, đồng bộ và vòng đời tài nguyên.

## 2. Từ `FileChannel` tới `MappedByteBuffer`

API cốt lõi:

```java
MappedByteBuffer mapped = channel.map(
        FileChannel.MapMode.READ_WRITE,
        fileOffset,
        length
);
```

Đây là đoạn minh họa trong method có `channel` là `FileChannel` đang mở thích hợp và có xử lý `IOException`.

Ba tham số có ý nghĩa:

- `mode`: quyền và cơ chế ghi của vùng ánh xạ.
- `fileOffset`: vị trí bắt đầu **tính bằng byte trong tệp**; có kiểu `long`, phải không âm.
- `length`: độ dài **vùng ánh xạ tính bằng byte**; có kiểu `long` ở lời gọi, nhưng với overload trả `MappedByteBuffer`, kích thước map không được vượt `Integer.MAX_VALUE`. Nó phải không âm.

Nếu ánh xạ từ offset 100 với length 50:

```text
Tệp:            byte 0 ... byte 99 | byte 100 ........ byte 149 | byte 150 ...
                                            ↑                   ↑
Mapped buffer:                            index 0 ...       index 49
buffer.position() ban đầu: 0
buffer.limit() ban đầu:    50
buffer.capacity():         50
```

**Công thức:** `fileOffset + bufferIndex` là vị trí byte tương ứng trong tệp, trong điều kiện vùng tệp không bị thay đổi bởi tác nhân khác. `buffer.get(0)` đọc byte tại `fileOffset`, không phải byte số 0 của cả tệp nếu `fileOffset != 0`.

Khi trả về thành công, buffer có `position=0`, `limit=capacity=length` và `mark` chưa được đặt. `MappedByteBuffer` là một **direct byte buffer**; dữ liệu gắn với mapping, không có một `byte[]` Java truy cập được theo kiểu heap `ByteBuffer.allocate(...)`.

Lưu ý rằng lời gọi `map` tạo một vùng địa chỉ ánh xạ. Nó không tương đương `Files.readAllBytes`, vốn cấp phát một mảng Java chứa toàn bộ byte đọc được.

## 3. Ba chế độ ánh xạ: quyền ghi và quan hệ với tệp

`FileChannel.MapMode` định nghĩa ba mode chuẩn:

| Chế độ | Mở channel | Có thể sửa qua buffer? | Thay đổi được ghi về tệp? |
|---|---|---|---|
| `READ_ONLY` | Cần quyền `READ` | **Không**; `put` ném `ReadOnlyBufferException` | Không có sửa đổi qua view này |
| `READ_WRITE` | Cần đồng thời `READ` và `WRITE` | **Có** | Thay đổi sẽ được truyền về tệp theo cơ chế OS; thời điểm không mặc định xác định |
| `PRIVATE` | Cần đồng thời `READ` và `WRITE` | **Có** | **Không**; copy-on-write tạo bản sao riêng cho phần sửa, không ghi thay đổi đó về tệp |

Ví dụ mở channel rõ quyền:

```java
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

Path file = Path.of("records.bin");

try (FileChannel channel = FileChannel.open(
        file,
        StandardOpenOption.READ,
        StandardOpenOption.WRITE)) {

    // Chọn mode phù hợp rồi map một đoạn nằm trong file.
    // File chưa tồn tại sẽ gây lỗi mở; ví dụ này giả định nó đã có.
}
```

`READ_ONLY` có thể map bằng channel chỉ mở `READ`. `READ_WRITE` và `PRIVATE` cần channel mở cho **cả đọc lẫn ghi** theo hợp đồng `FileChannel.map`, dù `PRIVATE` không ghi byte đã chỉnh về tệp. Nếu quyền bị thiếu, có thể gặp `NonReadableChannelException` hoặc `NonWritableChannelException`.

### 3.1. `READ_ONLY`: đọc dữ liệu và chặn ghi qua mapping

```java
try (FileChannel channel = FileChannel.open(file,
        StandardOpenOption.READ)) {
    MappedByteBuffer mapped = channel.map(
            FileChannel.MapMode.READ_ONLY, 0, channel.size());

    byte first = mapped.get(0); // Nếu vùng có ít nhất một byte.
    // mapped.put(0, (byte) 7): ReadOnlyBufferException.
}
```

Đây là đoạn mã minh họa với các import đầy đủ và `file` trỏ tới tệp đọc được, không rỗng. Chỉ riêng `mapped.isReadOnly()` biểu thị quyền trên **view**, không biến dữ liệu tệp thành bất biến: tiến trình khác vẫn có thể thay tệp. Sự thay đổi bên ngoài có hiện ngay lên vùng đang map hay không phụ thuộc hệ điều hành và được API để ngỏ.

### 3.2. `READ_WRITE`: sửa byte ngay trên vùng map

```java
MappedByteBuffer mapped = channel.map(
        FileChannel.MapMode.READ_WRITE, 0, 4);

mapped.put(0, (byte) 'J');  // Ghi vị trí đầu của vùng.
mapped.put(1, (byte) 'a');
mapped.put(2, (byte) 'v');
mapped.put(3, (byte) 'a');
mapped.force();              // Yêu cầu ghi các trang thay đổi về thiết bị.
```

Đoạn minh họa giả định `channel` có cả quyền đọc/ghi và tệp đã chứa ít nhất 4 byte. `put(index,value)` là truy cập **absolute**, không tăng `position`. `MappedByteBuffer.force()` được thiết kế để yêu cầu đẩy thay đổi của mapping `READ_WRITE` về thiết bị lưu trữ; chi tiết về độ bền dữ liệu được giải thích ở phần sau.

### 3.3. `PRIVATE`: copy-on-write chứ không phải `READ_ONLY`

```java
MappedByteBuffer privateView = channel.map(
        FileChannel.MapMode.PRIVATE, 0, 4);

privateView.put(0, (byte) 'X'); // Hợp lệ với view writable.
```

Phần dữ liệu bị sửa qua `privateView` được lưu trong bản riêng theo cơ chế copy-on-write, **không truyền ngược về tệp** và không hiển thị cho những chương trình khác đã map tệp đó qua mapping của chúng. Đây không phải cách tạo một **bản sao độc lập toàn bộ tệp ngay khi gọi map**; OS có thể chỉ tạo bản riêng cho những trang được sửa. Không dùng mode này nếu mục tiêu là lưu kết quả chỉnh sửa xuống tệp.

Với các mode khác nhau hoặc các ánh xạ do chương trình khác tạo, đừng tự đoán chính xác thời điểm byte thay đổi trở nên nhìn thấy: hợp đồng Java dành nhiều chi tiết cho OS.

## 4. Chuẩn bị kích thước tệp trước khi map

Khi map để đọc, phải biết vùng nào đang tồn tại. `channel.size()` trả kích thước hiện tại, nhưng một tiến trình khác có thể thay file giữa lúc đo và lúc truy cập.

**Quan trọng:** hợp đồng `FileChannel.map` không xác định rõ hành vi nếu vùng được yêu cầu **không nằm hoàn toàn trong kích thước tệp hiện có**; đừng dựa vào việc map tự mở rộng file mà không có bước chuẩn bị.

Để demo có hành vi rõ, hãy tạo nội dung trước:

```java
byte[] bytes = "HELLO".getBytes(StandardCharsets.US_ASCII);

try (FileChannel channel = FileChannel.open(
        file,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.READ,
        StandardOpenOption.WRITE)) {

    ByteBuffer source = ByteBuffer.wrap(bytes);
    while (source.hasRemaining()) {
        channel.write(source);
    }

    MappedByteBuffer mapped = channel.map(
            FileChannel.MapMode.READ_WRITE, 0, bytes.length);
}
```

Đoạn minh họa cần import `java.nio.ByteBuffer`, `java.nio.MappedByteBuffer`, `java.nio.charset.StandardCharsets` cùng lớp/channel ở trên, và method xử lý `IOException`. Dùng vòng lặp `while (source.hasRemaining())` bởi `FileChannel.write` **không đảm bảo** một lời gọi ghi hết byte còn lại trong buffer.

Nếu nghiệp vụ cần tệp nhị phân có kích thước cố định trước khi map, có thể dùng `FileChannel.position(...)` và ghi một byte ở cuối, hoặc phương pháp tạo tệp có kích thước mong muốn bằng các API file-system phù hợp. `FileChannel.truncate(newSize)` **chỉ cắt ngắn khi kích thước hiện tại lớn hơn**; nếu `newSize` lớn hơn kích thước tệp, `truncate` **không mở rộng tệp**.

### 4.1. Một mapping có thể dài bao nhiêu?

Với overload `FileChannel.map(MapMode,long,long)` trả `MappedByteBuffer` trong Java 17/21, `length <= Integer.MAX_VALUE`. Tệp có thể lớn hơn giới hạn này, nhưng **một buffer mapping đơn** vẫn dùng chỉ số `int`.

Để xử lý tệp rất lớn, thiết kế **mapping theo từng cửa sổ**:

```java
long fileSize = channel.size();
long windowSize = 64L * 1024 * 1024; // 64 MiB, minh họa.

for (long offset = 0; offset < fileSize; offset += windowSize) {
    long length = Math.min(windowSize, fileSize - offset);
    MappedByteBuffer window = channel.map(
            FileChannel.MapMode.READ_ONLY, offset, length);

    while (window.hasRemaining()) {
        byte value = window.get();
        // Xử lý từng byte theo yêu cầu cụ thể.
    }
}
```

Đây là đoạn **minh họa thuật toán**, giả định `channel` được mở đọc, file không bị thay đồng thời, và thay `// Xử lý` bằng xử lý nghiệp vụ. Không giữ vô hạn số mapping đã tạo: view có thể còn được giữ sống qua tham chiếu trong hệ thống. Tham số windowSize không phải kích thước trang OS, chỉ là một ví dụ chọn cửa sổ nhỏ hơn giới hạn API. Nếu dữ liệu gồm các bản ghi có thể nằm qua ranh giới cửa sổ, phải giữ lại phần byte dở dang và xử lý ở cửa sổ tiếp theo; không mặc định mỗi cửa sổ chứa một số bản ghi nguyên vẹn.

## 5. Đọc/ghi như một `ByteBuffer`

`MappedByteBuffer` kế thừa `ByteBuffer`, nên nhiều quy tắc quen thuộc áp dụng:

- `capacity`: tổng số byte của mapping, cố định với buffer đó.
- `limit`: giới hạn truy cập hiện tại, ban đầu bằng capacity.
- `position`: nơi `get()` hoặc `put(byte)` kế tiếp đọc/ghi; ban đầu 0.
- `remaining() = limit - position`.
- `get(index)` và `put(index,value)`: **absolute**, không thay position.
- `get()` và `put(value)`: **relative**, tăng position.
- `duplicate()` và `slice()`: tạo view dùng chung dữ liệu nhưng có cursor riêng.
- `clear()`, `flip()`, `rewind()`: chủ yếu quản lý cursor/limit, **không xóa byte khỏi mapping**.

Ví dụ vùng map 6 byte:

```java
MappedByteBuffer mapped = channel.map(
        FileChannel.MapMode.READ_WRITE, 0, 6);

mapped.put(0, (byte) 'A'); // position vẫn 0.
mapped.position(1);
mapped.put((byte) 'B');    // Ghi index 1; position thành 2.
byte first = mapped.get(0); // Đọc index 0; position vẫn 2.
```

Các dòng là minh họa trong method đã mở channel có quyền đọc/ghi, và tệp đủ 6 byte. Nếu bạn gọi `mapped.flip()` sau một đợt sửa byte qua absolute `put(index, value)` mà position vẫn bằng 0, `flip()` sẽ đặt limit bằng 0 và khiến các thao tác đọc tương đối không còn phần tử. Với thao tác truy cập vị trí cố định, không bắt buộc dùng `flip()` như khi ghi nội dung vào `ByteBuffer` để truyền cho channel.

### 5.1. `slice()` giữ dữ liệu chung

```java
MappedByteBuffer base = channel.map(
        FileChannel.MapMode.READ_WRITE, 0, 8);
base.position(2);
base.limit(6);
ByteBuffer slice = base.slice();

slice.put(0, (byte) 99); // Sửa byte index 2 trong base.
```

Đoạn minh họa cần import `ByteBuffer` và `MappedByteBuffer`; base hợp lệ khi tệp ít nhất 8 byte. `slice` có position=0, limit=capacity=4; ánh xạ index 0 của slice sang index 2 của base. Từ Java 17, các thao tác `slice(index,length)` có thể chọn một vùng trực tiếp theo chỉ số.

**Không coi `slice` hoặc `duplicate` là bản sao nội dung.** Việc chúng có cursor riêng không có nghĩa chúng giữ byte riêng. Khi cần snapshot độc lập, copy bytes ra array hoặc `ByteBuffer` mới và cân nhắc tính nhất quán nếu file bị sửa đồng thời.

### 5.2. Đọc số nguyên phụ thuộc `ByteOrder`

Các method `getInt`, `putInt`, `getLong`, `putLong` thao tác trên nhiều byte và yêu cầu thống nhất thứ tự byte trong định dạng file.

```java
mapped.order(ByteOrder.LITTLE_ENDIAN);
mapped.putInt(0, 0x01020304);
System.out.printf("%02X %02X %02X %02X%n",
        mapped.get(0) & 0xFF,
        mapped.get(1) & 0xFF,
        mapped.get(2) & 0xFF,
        mapped.get(3) & 0xFF);
```

Đoạn minh họa giả định mapping READ_WRITE dài ít nhất 4 byte và import `java.nio.ByteOrder`. Output byte trong LITTLE_ENDIAN:

```text
04 03 02 01
```

Nếu đọc một file lưu little-endian mà không đặt thứ tự byte, giá trị `getInt()` sẽ có thể sai. `ByteBuffer` mới dùng thứ tự mặc định **BIG_ENDIAN**. Không đồng nhất việc ánh xạ tệp với việc tự nhận diện endian từ định dạng dữ liệu.

## 6. Ví dụ hoàn chỉnh Java 17+: ghi và đọc lại một file mapped

Ví dụ sau dùng nội dung ASCII cố định để phân biệt rõ **vị trí byte** với ký tự. Nó tạo file tạm, ghi `HELLO`, map `READ_WRITE`, sửa bằng truy cập absolute lẫn relative, kiểm tra `READ_ONLY` và `PRIVATE`, rồi đọc lại byte lưu trong file.

**Tệp:** `MappedIoDemo.java`

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class MappedIoDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("mapped-io-", ".bin");

        // Mapping có thể giữ handle tài nguyên sau khi FileChannel đóng.
        // Dọn file tạm khi JVM kết thúc ở mức best effort.
        file.toFile().deleteOnExit();

        byte[] initial = "HELLO".getBytes(StandardCharsets.US_ASCII);

        try (FileChannel channel = FileChannel.open(
                file,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE)) {

            ByteBuffer source = ByteBuffer.wrap(initial);
            while (source.hasRemaining()) {
                channel.write(source);
            }

            MappedByteBuffer mapped = channel.map(
                    FileChannel.MapMode.READ_WRITE, 0, initial.length);

            System.out.println("map size: " + mapped.capacity());
            System.out.println("first byte: " + (char) mapped.get(0));

            mapped.put(0, (byte) 'h'); // H -> h, position không đổi.

            ByteBuffer alias = mapped.duplicate();
            alias.put(1, (byte) 'I');  // E -> I trong storage chung.

            mapped.position(2);
            mapped.put((byte) 'y');   // L -> y, position tăng thành 3.

            System.out.println("position: " + mapped.position());
            System.out.println("alias change: "
                    + (char) mapped.get(1));

            // READ_WRITE: yêu cầu đẩy các byte sửa sang thiết bị.
            mapped.force();

            MappedByteBuffer readOnly = channel.map(
                    FileChannel.MapMode.READ_ONLY, 0, initial.length);
            System.out.println("read only: " + readOnly.isReadOnly());

            try {
                readOnly.put(0, (byte) 'X');
            } catch (ReadOnlyBufferException ex) {
                System.out.println("read-only write blocked");
            }

            MappedByteBuffer privateMap = channel.map(
                    FileChannel.MapMode.PRIVATE, 0, initial.length);
            privateMap.put(0, (byte) 'P');
            System.out.println("private first: "
                    + (char) privateMap.get(0));
        }

        // Đọc qua API file thông thường sau khi đã force mapping.
        // Việc PRIVATE ghi 'P' không thay byte tương ứng của file.
        System.out.println("file content: "
                + Files.readString(file, StandardCharsets.US_ASCII));
    }
}
```

Biên dịch/chạy bằng JDK 17+:

```bash
javac MappedIoDemo.java
java MappedIoDemo
```

**Stdout mong đợi trên hệ thống tệp thông thường hỗ trợ memory mapping:**

```text
map size: 5
first byte: H
position: 3
alias change: I
read only: true
read-only write blocked
private first: P
file content: hIyLO
```

Giải thích:

1. File được ghi đủ 5 byte trước khi map nên vùng `0..4` hoàn toàn nằm trong file hiện có.
2. `MappedByteBuffer` mới có `capacity=5`; `get(0)` thấy chữ `H`.
3. `put(0,'h')` là absolute; `alias.put(1,'I')` thay byte index 1 trong **cùng vùng nhớ**, cho dù alias có position riêng.
4. `mapped.position(2); mapped.put('y')` sửa byte index 2 rồi tăng position lên 3. Ba thay đổi tạo byte `hIyLO`.
5. `mapped.force()` yêu cầu ghi các thay đổi của mapping `READ_WRITE`.
6. Mapping `READ_ONLY` cho phép đọc nhưng từ chối `put`.
7. Mapping `PRIVATE` cho phép sửa view thành `P...`, nhưng **không ghi** chữ `P` vào file. Vì vậy dòng đọc file cuối giữ `hIyLO`.

Ví dụ cố ý dùng byte ASCII một byte mỗi ký tự. Trong thực tế, khi file dùng UTF-8, một code point Unicode có thể chiếm nhiều byte; không đặt `mapped.put(index, (byte) someChar)` rồi kỳ vọng luôn ghi đúng tiếng Việt hoặc emoji. Hãy encode cả chuỗi bằng `CharsetEncoder` hoặc `getBytes(StandardCharsets.UTF_8)` rồi ghi đúng số byte và kích thước vùng.

Lệnh `deleteOnExit()` chỉ là dọn dẹp tạm **best effort**: một số hệ điều hành giữ file/mapping trong lúc JVM còn sống hoặc có hạn chế xóa file; không biến nó thành lời hứa đã giải phóng OS mapping ngay sau `channel.close()`.

## 7. Đọc văn bản UTF-8 qua mapping

Mapped buffer mang byte; muốn đọc văn bản phải **giải mã theo charset**, không ép từng byte thành `char`.

Ví dụ file UTF-8 đã tồn tại, đọc toàn bộ trong trường hợp kích thước **không vượt giới hạn một mapping**:

```java
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class MappedUtf8Reader {
    public static String read(Path file) throws IOException {
        try (FileChannel ch = FileChannel.open(
                file, StandardOpenOption.READ)) {

            long size = ch.size();
            if (size > Integer.MAX_VALUE) {
                throw new IOException("File too large for one mapping");
            }

            MappedByteBuffer data = ch.map(
                    FileChannel.MapMode.READ_ONLY, 0, size);

            return StandardCharsets.UTF_8.decode(data).toString();
        }
    }
}
```

Đây là helper có thể biên dịch (`MappedUtf8Reader.java`), không có `main` và không in stdout. `StandardCharsets.UTF_8.decode(data)` ở dạng convenience giải mã thành `CharBuffer`; với đầu vào không hợp lệ, cách xử lý lỗi của convenience method có thể dùng thay thế ký tự theo contract của `Charset`. Nếu nghiệp vụ yêu cầu **từ chối UTF-8 không hợp lệ**, dùng `CharsetDecoder` và `onMalformedInput(CodingErrorAction.REPORT)` cùng thao tác decode có kiểm soát. Vì output trả một `String`, helper này vẫn cần đủ RAM cho kết quả, dù quá trình đọc byte được map và không sao chép toàn file thành `byte[]` trung gian.

**Không** chia UTF-8 thành các window tùy tiện rồi decode từng window độc lập: một chuỗi byte nhiều phần có thể bị tách giữa hai cửa sổ. Nếu cần xử lý văn bản lớn theo đoạn, giữ state của decoder giữa những window, truyền phần byte chưa đủ ở ranh giới sang window kế tiếp, và xử lý end-of-input/flush đúng quy tắc `CharsetDecoder`. Nếu chỉ cần xử lý văn bản tuần tự, `BufferedReader` hoặc `Channels.newReader` thường đơn giản hơn.

## 8. `force()`, `load()` và `isLoaded()` làm việc gì?

### 8.1. `MappedByteBuffer.force()`: yêu cầu ghi vùng thay đổi

`mapped.force()` tác động lên vùng byte của mapping `READ_WRITE`. Với Java 17+, có overload:

```java
mapped.force();           // Toàn bộ vùng ánh xạ.
mapped.force(0, 1024);    // Vùng bắt đầu ở index 0, dài 1024 byte.
```

Dòng thứ hai chỉ hợp lệ khi `mapped.capacity() >= 1024`; cả hai là mã minh họa. Chỉ số của `force(index,length)` nằm trong **buffer mapped**, không phải offset tuyệt đối toàn tệp. Implementation có thể ghi cả các byte bên ngoài đoạn yêu cầu do đơn vị block/trang của OS.

Theo contract: nếu file ở **thiết bị lưu trữ cục bộ**, sau khi `force()` trả thành công, các thay đổi của vùng ánh xạ READ_WRITE đã được ghi tới thiết bị; với thiết bị không cục bộ, không có bảo đảm tương tự. `force()` trên mapping `READ_ONLY` hoặc `PRIVATE` **không** giúp lưu thay đổi về file. `force()` có thể ném `UncheckedIOException` nếu có lỗi ghi.

Không nên nhầm `mapped.force()` với `channel.force(metaData)`:

| API | Áp dụng cho | Ý nghĩa chính |
|---|---|---|
| `MappedByteBuffer.force()` | Những byte trong mapping | Yêu cầu đẩy thay đổi mapping về thiết bị theo điều kiện API |
| `MappedByteBuffer.force(index,length)` | Một đoạn của mapping | Force đoạn byte tương đối trong mapping |
| `FileChannel.force(boolean metaData)` | Nội dung cập nhật qua channel; tùy `metaData` có thể gồm metadata | Bảo đảm theo contract của FileChannel về các thay đổi file được thực hiện qua channel |

Nếu có cả thao tác ghi qua `FileChannel.write` và qua mapping, hãy đọc hợp đồng của cả hai, rồi xác định điều kiện độ bền đúng cho hệ thống tệp và môi trường. Không đánh đồng flush một buffer với transaction nhiều file hoặc bảo đảm crash consistency cho cấu trúc dữ liệu nhiều bước.

### 8.2. `load()` là nỗ lực nạp trang dữ liệu, không phải `force()`

`mapped.load()` yêu cầu JVM/OS cố gắng đưa các trang dữ liệu đã ánh xạ vào bộ nhớ vật lý trước để giảm page faults khi truy cập sau đó.

`mapped.isLoaded()` trả **gợi ý** liệu các trang đang cư trú hay không:

- `true` có nghĩa có khả năng cao dữ liệu đang resident, **không bảo đảm mãi mãi**.
- `false` **không khẳng định** mọi trang đều bị đẩy ra khỏi RAM.
- OS có thể thay đổi tình trạng resident ngay sau lời gọi.

Ví dụ:

```java
mapped.load();
boolean probablyResident = mapped.isLoaded();
```

Đây là đoạn minh họa với mapped đã hợp lệ. Không dùng `isLoaded()` làm điều kiện correctness hay khẳng định chắc chắn “đã có đủ byte trong RAM”. `load()` có thể phát sinh I/O và page faults; chỉ gọi trước khi truy cập nếu đo đạc cho thấy có lợi.

## 9. Map một vùng của file và xử lý bản ghi nhị phân

Giả sử định dạng một bản ghi cố định 12 byte gồm:

```text
Offset tương đối   Kích thước   Trường
0                  4 byte       id (int, LITTLE_ENDIAN)
4                  8 byte       timestamp (long, LITTLE_ENDIAN)
```

Đọc một record bằng mapped buffer:

```java
static long readTimestamp(MappedByteBuffer mapped, int recordIndex) {
    final int recordSize = Integer.BYTES + Long.BYTES;
    int base = Math.multiplyExact(recordIndex, recordSize);
    return mapped.getLong(base + Integer.BYTES);
}
```

Đây là đoạn minh họa trong class đã import `MappedByteBuffer`; caller phải đặt `mapped.order(ByteOrder.LITTLE_ENDIAN)` và kiểm tra recordIndex cũng như phạm vi vùng trước khi gọi. `Math.multiplyExact` giúp phát hiện tràn số nguyên khi tính offset, nhưng nếu giá trị vượt `limit`, thao tác vẫn có thể ném `IndexOutOfBoundsException`. Việc đọc một `long` có thể không đủ tính nguyên tử nếu file được tác nhân khác sửa đồng thời.

Cần phân biệt:

- **Offset trong tệp**: kiểu `long`, áp dụng khi gọi `map`.
- **Index trong buffer**: kiểu `int`, bắt đầu từ 0 của mapping.
- **Record offset**: tính theo số byte, không phải số trường.
- **Thứ tự byte**: dữ liệu trên đĩa phải có định dạng xác định và trùng với cấu hình `ByteOrder` lúc đọc/ghi.

Nếu file là log có cấu trúc, ngoài id và timestamp còn có thể cần header magic, version, length, checksum, trạng thái commit và xử lý bản ghi dở dang. Memory mapping chỉ cung cấp cách truy cập byte; nó **không tự cung cấp** định dạng bản ghi hoặc cơ chế giao dịch.

## 10. Khác nhau giữa map và read/write thông thường

| Tiêu chí | `FileChannel.read/write` | `FileChannel.map` |
|---|---|---|
| Mô hình truy cập | Truyền byte qua `ByteBuffer` khi gọi I/O | Ánh xạ file rồi truy cập byte qua `MappedByteBuffer` |
| Chi phí khởi tạo | Thường nhỏ cho I/O tuần tự ngắn | Thiết lập mapping có chi phí; không nhất thiết tốt cho file nhỏ |
| Truy cập ngẫu nhiên | Có thể dùng positional `read/write` | Thuận tiện dùng `get(index)` và `put(index,value)` |
| Bộ nhớ | Chủ động quản lý buffer | Dùng vùng địa chỉ ảo và trang OS; không phải tự động đưa toàn file lên Java heap |
| File rất lớn | Dễ xử lý từng lần đọc/ghi | Mapping đơn giới hạn `Integer.MAX_VALUE` byte; thường cần nhiều window |
| Đồng bộ và lưu bền | Quản lý write và `FileChannel.force` | Quản lý mapping và `MappedByteBuffer.force` |
| Vòng đời | Đóng channel kết thúc I/O của channel | Đóng channel **không** tự hủy mapping ngay |
| Thay đổi đồng thời | Vẫn phải thiết kế tính nhất quán | Mapping dễ chịu ảnh hưởng của thay đổi ngoài ý muốn; nhiều hành vi OS-dependent |

Chọn mmap thường có ích khi dữ liệu lớn, truy cập ngẫu nhiên nhiều lần hoặc muốn xem file như cấu trúc byte có index. Với tệp văn bản nhỏ, thao tác một lần hoặc I/O tuần tự, `Files.readString`, `BufferedInputStream`, `FileChannel.read` có thể đơn giản hơn và hiệu quả không kém. Hãy đo hiệu năng trong workload thật thay vì coi mmap luôn “zero-copy” theo nghĩa không có bất kỳ sao chép hay page fault nào.

## 11. Đồng thời, thay đổi file và những rủi ro khó thấy

### 11.1. Một vùng mapped không phải ảnh chụp bất biến

Nếu tiến trình khác sửa nội dung file, ánh xạ có thể quan sát thay đổi vào thời điểm phụ thuộc hệ điều hành. Không có bảo đảm chung rằng mỗi lần đọc nhiều trường tạo nên một snapshot logic nhất quán. Một thread khác sửa cùng mapped buffer cũng có thể tạo data race.

Muốn chia sẻ dữ liệu với nhiều thread hoặc nhiều process:

- Thiết kế giao thức cập nhật: ví dụ version, trạng thái commit, lock hoặc cơ chế memory visibility tương ứng.
- Dùng đồng bộ phù hợp; chỉ dùng `getInt`/`putInt` trên buffer không tự biến cấu trúc nhiều trường thành giao dịch atomic.
- Nếu cần phối hợp giữa tiến trình, tìm hiểu `FileLock` và bảo đảm của OS/file system; lock cũng có giới hạn và không thay thế kiểm tra consistency của định dạng.
- Đừng trộn `READ_WRITE` và `PRIVATE` rồi kỳ vọng những view này có cùng cách nhìn mọi thay đổi.

### 11.2. Không thay đổi kích thước file bên dưới một mapping đang dùng

Tài liệu Java cảnh báo rằng nội dung mapped buffer có thể trở nên **không truy cập được** nếu file bị cắt ngắn (truncate). Truy cập vùng không còn hợp lệ có thể gây ngoại lệ không được chỉ rõ. Hành vi khi thay đổi độ dài hoặc nội dung file sau khi đã map cũng phụ thuộc OS.

Nguyên tắc: khi dùng mmap cho file đang thay đổi, bảo vệ vòng đời và độ dài file bằng chính sách ownership/locking hoặc thiết kế format xử lý phiên bản; tránh `truncate` file đang còn vùng mapped truy cập. Không sử dụng `catch (RuntimeException)` rồi âm thầm coi byte lỗi như giá trị hợp lệ.

### 11.3. Mapping không tự quản lý transaction

Giả sử một bản ghi gồm `balance`, `version` và `checksum`. Việc ghi tuần tự từng trường vào mapping có thể bị người đọc khác quan sát **giữa chừng**. `force()` giúp đẩy byte thay đổi về thiết bị trong điều kiện hợp đồng, nhưng **không** biến chuỗi thay đổi thành một transaction nguyên tử và không cung cấp rollback.

Nếu cần crash consistency, phải thiết kế thứ tự cập nhật, journal/WAL, checksum, marker commit, double buffering hoặc các cơ chế bền vững phù hợp. Mmap là **cách truy cập** byte trong file, không phải hệ quản trị giao dịch.

### 11.4. Read-only buffer có còn thấy file thay đổi không?

`READ_ONLY` chỉ cấm ghi **qua buffer ấy**. Nếu tác nhân khác thay file trên đĩa, một số OS có thể phản ánh thay đổi vào mapping; Java không bảo đảm thời điểm hoặc sự hiện diện của việc phản ánh. Do đó, `READ_ONLY` không phải snapshot về mặt dữ liệu và không thay thế copy riêng khi cần tính bất biến.

## 12. Vòng đời và giải phóng ánh xạ

Một điểm khác `FileChannel` thông thường: vùng mapping do `MappedByteBuffer` đại diện vẫn có thể hợp lệ **sau khi channel dùng để tạo nó đã đóng**. Theo Java API, mapping và buffer tồn tại cho đến khi buffer được garbage collector thu hồi.

```java
MappedByteBuffer mapped;
try (FileChannel ch = FileChannel.open(
        file, StandardOpenOption.READ)) {
    mapped = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size());
}
// FileChannel đã đóng, mapped vẫn có thể dùng theo contract mapping.
// Nhưng map phụ thuộc tính hợp lệ của vùng tệp bên dưới.
```

Đây là đoạn minh họa giả định `file` hợp lệ, method xử lý `IOException` và file không bị tác nhân khác thay đổi bất lợi. **Không** suy ra rằng đóng channel sẽ unmap, giải phóng địa chỉ ảo, hoặc lập tức cho phép xóa file trên mọi OS.

Đặc biệt trên Windows, file đã map có thể khó bị xóa/đổi tên khi OS còn giữ mapping. `MappedByteBuffer` trong Java 17/21 **không có một phương thức `close()` công khai** để chủ động unmap tương tự `FileChannel.close()`. Việc cố gọi API nội bộ JDK để ép unmap rất phụ thuộc phiên bản/module access, không nên làm mặc định.

Đối với tệp tạm, cân nhắc thiết kế không giữ tham chiếu mapping quá lâu, sử dụng dọn dẹp best-effort và chấp nhận có thể cần chờ tài nguyên mapping được giải phóng. Với ứng dụng cần kiểm soát vòng đời unmap chính xác, hãy đánh giá các API memory-segment chính thức phù hợp phiên bản Java đang dùng thay vì dựa vào hàm dọn dẹp nội bộ; riêng bài này dùng API `MappedByteBuffer` phổ biến của Java 17+.

### 12.1. Vì sao không cần hoặc không thể kiểm soát page cache theo ý muốn?

Hệ điều hành quyết định lúc nào dữ liệu mapped được đưa vào trang RAM và lúc nào được thay thế. `load()` chỉ là nỗ lực, `isLoaded()` là hint. Java `System.gc()` không phải giải pháp đúng để điều khiển cache hoặc bảo đảm hủy mapping ngay; chương trình không được đòi hỏi GC thực thi theo thời điểm xác định.

## 13. Lỗi và ngoại lệ cần biết

| Tình huống | Biểu hiện thường gặp | Hướng xử lý |
|---|---|---|
| `map` với offset/size âm hoặc size quá lớn | `IllegalArgumentException` | Kiểm tra chỉ số/kích thước, dùng mapping theo cửa sổ |
| Chọn `READ_ONLY` nhưng channel không mở đọc | `NonReadableChannelException` | Mở channel đúng quyền |
| Chọn `READ_WRITE` hoặc `PRIVATE` mà thiếu quyền đọc/ghi | `NonReadableChannelException` / `NonWritableChannelException` | Mở channel với cả `READ` và `WRITE` |
| Ghi vào view `READ_ONLY` | `ReadOnlyBufferException` | Dùng `READ_WRITE` nếu thực sự cần lưu; kiểm tra chính sách |
| `get`/`put` vượt giới hạn buffer | `IndexOutOfBoundsException`, `BufferUnderflowException` hoặc `BufferOverflowException` tùy thao tác | Kiểm tra `position`/`limit`/`remaining` và dữ liệu đầu vào |
| I/O khi mở hoặc tạo mapping lỗi | `IOException` | Xem quyền, trạng thái file, dung lượng và provider |
| `force()` gặp lỗi ghi | `UncheckedIOException` | Xử lý lỗi persistence, không giả định byte đã lưu |
| Tệp bị truncate trong lúc mapping còn sử dụng | Hành vi truy cập không được chỉ rõ, có thể ném ngoại lệ | Thiết kế ownership và không đổi kích thước file đang dùng |
| Xóa/đổi tên file đang mapped không thành công | Phụ thuộc OS và handle mapping | Kiểm soát vòng đời, tránh giả định close(channel) = unmap |
| Kiểm tra `isLoaded() == false` | Không đủ bằng chứng byte không ở RAM | Đừng dùng hint này làm điều kiện correctness |

Các exception như `NonReadableChannelException` / `NonWritableChannelException` là unchecked; `IOException` là checked, còn lỗi truy cập mapped memory khi file bị thay đổi ngoài ý muốn có thể không khớp một subtype cố định. Đừng biến mọi failure thành “file không tồn tại”.

## 14. Lỗi phổ biến khi đọc/ghi qua mapped buffer

1. **Cho rằng `map` đọc ngay toàn bộ file vào heap.** Nó thiết lập ánh xạ; OS quyết định nạp trang.
2. **Tưởng `READ_WRITE` đồng nghĩa ghi bền vững ngay sau `put`.** Thời điểm truyền về file có thể không xác định; khi cần persistence, dùng `force` với điều kiện hợp đồng.
3. **Dùng `PRIVATE` để sửa file.** Mode này giữ copy-on-write, không phản ánh chỉnh sửa về file.
4. **Gọi `put` lên `READ_ONLY`.** Kết quả `ReadOnlyBufferException`.
5. **Map vượt kích thước file hiện tại** rồi dựa vào việc OS sẽ tự mở rộng. Hành vi ngoài vùng file không được contract bảo đảm.
6. **Dùng `truncate(larger)` để mở rộng file.** `truncate` không tăng size khi kích thước mới lớn hơn kích thước hiện tại.
7. **Map toàn bộ file lớn hơn 2 GiB bằng một `MappedByteBuffer`.** Overload này giới hạn size ≤ `Integer.MAX_VALUE`; cần chia cửa sổ.
8. **Nhầm offset file với index mapped.** Index 0 của mapping ứng với offset được truyền khi `map`, không nhất thiết là byte 0 của file.
9. **Nhầm `get(index)` với `get()`.** Absolute access không tăng position, relative access có tăng.
10. **Gọi `flip` sau toàn bộ absolute `put`** làm limit thành 0 nếu position chưa di chuyển; gây lỗi đọc tiếp.
11. **Bỏ qua `ByteOrder` khi ghi struct nhị phân.** `getInt`/`putLong` theo byte order của buffer; phải khớp format file.
12. **Ép từng UTF-8 byte thành `char`.** Mã Unicode có thể chiếm nhiều byte; dùng charset decoder.
13. **Cho rằng `READ_ONLY` là snapshot bất biến.** Nội dung file có thể bị thay đồng thời.
14. **Cho rằng `force()` tạo transaction** hoặc bảo đảm nhiều cập nhật atomic. Không có cơ chế transaction tự động.
15. **Dựa vào `isLoaded()` để quyết định tính đúng sai.** Nó chỉ là hint, không phải phép chứng minh.
16. **Nghĩ đóng `FileChannel` sẽ giải phóng mapping ngay.** Mapping có vòng đời độc lập và gắn với GC.
17. **Giữ nhiều mapping nhỏ mà không quản lý tham chiếu.** Tốn vùng địa chỉ, tài nguyên OS, có thể cản xóa file.
18. **Đổi kích thước file đang mapped** mà không điều phối các tiến trình/luồng. Có thể làm vùng truy cập không còn hợp lệ.
19. **Gắn memory mapping với “luôn nhanh hơn”.** Mapping có chi phí thiết lập và page faults; hiệu năng phụ thuộc kích thước và kiểu truy cập.
20. **Trộn nhiều writer trên cùng file mà không có giao thức.** Có thể làm mất nhất quán dữ liệu dù từng `put` hợp lệ.

## 15. Câu hỏi tự kiểm tra

**1.** `FileChannel.map(READ_ONLY, 100, 20)` trả buffer có `capacity` và `position` ban đầu là bao nhiêu?  
**Đáp án:** capacity = 20, position = 0; `mapped.get(0)` tương ứng byte file offset 100.

**2.** Vì sao chọn `PRIVATE` vẫn cần quyền ghi channel?  
**Đáp án:** Contract của `FileChannel.map` yêu cầu channel đọc và ghi cho cả `READ_WRITE` lẫn `PRIVATE`, dù byte sửa trong PRIVATE không được đưa về file.

**3.** Map 3 GiB qua overload trả `MappedByteBuffer` của Java 17 được không?  
**Đáp án:** Không; `length` bị giới hạn ≤ `Integer.MAX_VALUE`. Cần map từng cửa sổ và xử lý biên.

**4.** `mapped.put(0, (byte) 7)` có đưa `position` từ 0 lên 1?  
**Không.** Đây là absolute put. `mapped.put((byte)7)` mới tăng position tương đối.

**5.** Khi dùng `READ_WRITE`, gọi `mapped.force()` nhằm mục đích gì?  
**Đáp án:** Yêu cầu ghi thay đổi của mapping về thiết bị; bảo đảm cụ thể áp dụng khi file nằm trên thiết bị cục bộ, còn hệ thống khác có thể không có cam kết tương đương.

**6.** Nếu `mapped.isLoaded()` trả `false`, có chắc dữ liệu không ở RAM?  
**Không.** API chỉ trả hint; false không chứng minh điều ngược lại.

**7.** Đóng `FileChannel` có làm mọi `MappedByteBuffer` tạo từ nó mất hiệu lực ngay không?  
**Không.** Mapping có vòng đời riêng; ngược lại, việc file bị truncate có thể gây hỏng khả năng truy cập mapping.

**8.** Tại sao không thể dùng `mapped.force()` thay cho cơ chế transaction nhiều trường?  
**Vì** `force` liên quan ghi bytes về thiết bị, không bảo đảm nhóm cập nhật thành một hành động atomic hoặc tự rollback.

**9.** Nếu ghi `putInt(0, 0x01020304)` theo little-endian, byte tại các chỉ số 0..3 là gì?  
**Đáp án:** `04 03 02 01`.

**10.** Một file chứa tiếng Việt UTF-8, có thể in `(char)mapped.get(i)` cho mỗi byte để nhận văn bản đúng không?  
**Không.** Cần decoder UTF-8 và giữ trạng thái chuyển đổi tại ranh giới byte, đặc biệt với ký tự nhiều byte.

## 16. Ghi nhớ và tài liệu tham khảo

**Nguyên tắc:** `FileChannel.map` cho phép xem một vùng byte của tệp như một `MappedByteBuffer` có chỉ số. `READ_ONLY` để đọc; `READ_WRITE` để chỉnh dữ liệu sẽ truyền về file; `PRIVATE` để chỉnh copy-on-write không truyền về file. Kiểm soát offset, kích thước map, byte order, quyền truy cập, `force()` và vòng đời mapping. Memory mapping cải thiện sự thuận tiện cho một số kiểu truy cập ngẫu nhiên nhưng không thay thế cơ chế đồng bộ, giao dịch hoặc kiểm tra dữ liệu.

Nguồn tài liệu Java SE 21 chính thức:

- `FileChannel.map` và các chế độ `FileChannel.MapMode`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html
- `MappedByteBuffer` (`force`, `load`, `isLoaded`, vòng đời): https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/MappedByteBuffer.html
- `ByteBuffer` (đọc/ghi số, view, `ByteOrder`): https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html
- `StandardCharsets` và `CharsetDecoder`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/StandardCharsets.html và https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/CharsetDecoder.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="memory-mapping-005">load, isLoaded và tác động của page fault</a>

<details>
<summary>Xem chi tiết</summary>

# MappedByteBuffer.load(), isLoaded() và tác động của page fault

## 1. Hiểu đúng về memory-mapped file

Trong Java NIO, `FileChannel.map(...)` tạo một `MappedByteBuffer` để truy cập một vùng của tệp như bộ nhớ. Vùng mapping nằm trong **không gian địa chỉ ảo** của tiến trình. Điều này không đồng nghĩa tất cả byte đã được sao chép ngay vào RAM.

Khi đọc `mapped.get(index)`, hệ điều hành xác định trang chứa dữ liệu có ở bộ nhớ vật lý và đã được ánh xạ phù hợp chưa. Nếu chưa, có thể xảy ra **page fault**; kernel xử lý sự kiện, có thể lấy dữ liệu từ bộ nhớ cache hoặc thực hiện I/O tới thiết bị trước khi CPU tiếp tục truy cập.

Các khái niệm quan trọng:

- **Mapping:** liên kết địa chỉ ảo với nội dung một vùng tệp.
- **Resident page:** trang dữ liệu hiện đang có trong bộ nhớ vật lý.
- **Page fault:** sự kiện do cơ chế bộ nhớ ảo yêu cầu kernel xử lý trang/ánh xạ.
- **`load()`:** cố gắng nạp nội dung mapped buffer vào bộ nhớ vật lý (best effort).
- **`isLoaded()`:** trả lời bằng một gợi ý về trạng thái resident, không phải bảo đảm.

Hai phương thức cuối là chủ đề chính. **`load()` không cam kết loại bỏ page fault tương lai; `isLoaded()` không cam kết một kết luận residency chính xác tuyệt đối.**

## 2. Vì sao có page fault khi đã gọi map?

Ví dụ minh họa:

~~~java
try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
    MappedByteBuffer mapped =
            channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
    byte first = mapped.get(0);
}
~~~

Đoạn trên **không phải chương trình độc lập**: cần có `Path path` hợp lệ, các import cần thiết, và kích thước tệp phù hợp giới hạn mapping. Sau `map`, OS mới có thể thiết lập metadata ánh xạ mà chưa phải đưa đủ nội dung vào RAM.

Luồng truy cập phổ biến:

~~~text
FileChannel.map(...)
     |
     v
Địa chỉ ảo ↔ vùng dữ liệu tệp
     |
     v
mapped.get(offset)
     |
     +-- Trang resident và đã được map --> CPU đọc nhanh
     |
     +-- Trang cần page fault -----------+
                                         |
                         Kernel xử lý ánh xạ/trang
                              |          |
                      Dữ liệu ở RAM    Cần I/O từ storage
                              |          |
                              +----+-----+
                                   |
                           Lệnh đọc tiếp tục
~~~

Đây là sơ đồ khái niệm. Một page fault hợp lệ thường được kernel giải quyết rồi chương trình tiếp tục; không phải cứ có page fault là Java ném `Exception`.

### 2.1. Minor và major page fault

- **Minor/soft page fault:** cần cập nhật trạng thái ánh xạ nhưng dữ liệu đã có sẵn trong bộ nhớ, ví dụ trong **OS page cache**; không nhất thiết phải đọc đĩa.
- **Major/hard page fault:** cần chờ I/O để trang dữ liệu được cung cấp; có khả năng gây độ trễ lớn hơn.
- **Trang resident sẵn:** CPU có thể truy cập mà không cần fault mới tại thời điểm đó.

Phân loại và bộ đếm cụ thể mang tính hệ điều hành. **Không phải mỗi fault là một lần đọc đĩa**; page cache, filesystem readahead và những ứng dụng khác có thể khiến dữ liệu sẵn trong RAM.

### 2.2. Mapping, heap và page cache

`MappedByteBuffer` là một **direct byte buffer** đại diện cho vùng ánh xạ tệp, khác với việc tạo một `byte[]` nằm trên Java heap bằng `Files.readAllBytes`. Khi map một file, kernel có thể trì hoãn đưa dữ liệu vào bộ nhớ vật lý cho đến lần truy cập đầu. OS cũng có thể tự readahead các trang tiếp theo khi phát hiện truy cập tuần tự.

Việc dùng `MappedByteBuffer` không bỏ qua bộ nhớ ảo, không tự khóa RAM, và không làm dữ liệu trở nên miễn nhiễm với eviction.

## 3. Hợp đồng chính xác của MappedByteBuffer.load()

Java 17 định nghĩa:

~~~java
public final MappedByteBuffer load()
~~~

Theo Javadoc, phương thức **best effort** để nội dung buffer resident trong bộ nhớ vật lý lúc nó trả về. Nó có thể tự phát sinh **page faults và I/O** trong quá trình gọi.

Điều được phép kỳ vọng:

1. Hệ thống sẽ cố gắng chuẩn bị các trang của vùng mapped buffer.
2. Lời gọi có thể tiêu tốn thời gian do nạp dữ liệu; đây là bước prefetch/warm-up chủ động.
3. Phương thức trả lại **chính buffer nhận lời gọi** để có thể tiếp tục sử dụng.

Điều **không** được đảm bảo:

1. Mọi trang thật sự resident sau lời gọi.
2. Các trang sẽ tiếp tục ở trong RAM khi ứng dụng truy cập sau đó.
3. Mọi thao tác `get` tiếp theo không có page fault.
4. Lời gọi sẽ nhanh hoặc tăng hiệu năng end-to-end trong mọi workload.
5. Trang dữ liệu được khóa vĩnh viễn trong RAM.
6. Nội dung sửa đổi đã được lưu bền vững xuống thiết bị.

Ví dụ:

~~~java
MappedByteBuffer mapped =
        channel.map(FileChannel.MapMode.READ_ONLY, offset, length);

// Chuẩn bị trước: best effort page-in.
mapped.load();

// Không có bảo đảm thao tác dưới đây sẽ không fault.
byte value = mapped.get(0);
~~~

Đây là mã minh họa cần biến `FileChannel channel`, `long offset` và `long length` thích hợp.

`load()` không tạo `byte[]` và không thay đổi nội dung file. Nó cũng **không phải** `MappedByteBuffer.force()`; `force()` liên quan đến flush thay đổi ra storage.

## 4. Hợp đồng chính xác của MappedByteBuffer.isLoaded()

Java 17 định nghĩa:

~~~java
public final boolean isLoaded()
~~~

`isLoaded()` trả về **hint** về việc nội dung mapped buffer có ở bộ nhớ vật lý hay không:

- Nếu **`true`**: có khả năng cao toàn bộ nội dung buffer đang resident và nhiều truy cập có thể không cần fault/I/O.
- Nếu **`false`**: **không chứng minh** dữ liệu chưa có trong RAM. OS/provider có thể dùng heuristic hoặc không khẳng định residency.
- Với **cả hai kết quả**: ngay sau khi phương thức trả về, OS có thể đã page-out/evict một số dữ liệu. Không có bảo đảm cho lần truy cập tiếp theo.

Ví dụ sai cần tránh:

~~~java
mapped.load();
if (!mapped.isLoaded()) {
    throw new IllegalStateException("load thất bại");
}
~~~

Đây là **mã phản ví dụ**: `false` sau `load()` là hoàn toàn hợp lệ theo hợp đồng Java. Không nên coi hai phương thức này là giao thức xác nhận thành công/thất bại.

### 4.1. Những cặp giá trị đều hợp lệ

| isLoaded trước load | isLoaded sau load | Hợp lệ? | Vì sao? |
|---|---|---|---|
| `false` | `true` | Có | Trang có thể đã được chuẩn bị tốt hơn |
| `true` | `true` | Có | Dữ liệu có thể đã resident trước đó |
| `false` | `false` | Có | Best effort không bắt buộc hint chuyển thành true |
| `true` | `false` | Có | Trạng thái thay đổi hoặc hint không ổn định |

**Không dùng `isLoaded()` để suy ra số lượng page fault, lỗi đọc file, quyền truy cập hay việc nội dung file còn nguyên vẹn.** Nó chỉ là gợi ý về trạng thái cư trú trong bộ nhớ tại một thời điểm.

### 4.2. Nếu không kiểm chứng được thì vì sao có API này?

`isLoaded()` có thể giúp theo dõi trạng thái **mang tính tham khảo** của một mapping, ví dụ phục vụ thống kê/warm-up diagnostics. Nó không thay thế việc đo độ trễ, I/O, RSS hoặc bộ đếm fault từ hệ điều hành. Một chương trình có thể đọc dữ liệu thành công dù `isLoaded()` trả `false`.

## 5. Page fault ảnh hưởng độ trễ như thế nào?

### 5.1. Demand paging: trì hoãn chi phí tới lúc dùng

~~~text
map ----------------------> get(page A)
                               |
                               +-- Resident: thường nhanh
                               |
                               +-- Fault: OS phải xử lý,
                                          có thể chờ I/O
~~~

Nếu ứng dụng nhạy độ trễ, chi phí page fault có thể rơi vào đường xử lý một yêu cầu quan trọng. Tuy nhiên, đọc lần đầu không luôn chậm: file có thể đã nằm trong page cache.

### 5.2. Preload: chuyển một phần chi phí về warm-up

~~~text
map --> load() --> page fault/I/O có thể xảy ra --> xử lý chính --> get(page A)
             (warm-up)                                       (có thể nhanh hơn)
~~~

Nếu trang vẫn resident, truy cập sau có thể nhanh hơn. Nhưng chi phí I/O có thể chỉ được **dời sang trước**, không biến mất. OS có thể thu hồi trang giữa hai thời điểm, khiến `get` vẫn fault.

### 5.3. Preload sai workload có thể tệ hơn

Nếu một file rất lớn nhưng query chỉ truy cập một số ít trang, load cả vùng làm tăng I/O, page cache pressure và thời gian khởi đầu vô ích. Với truy cập ngẫu nhiên thưa, demand paging có thể hiệu quả hơn.

Do vậy, chọn `load()` dựa vào **tần suất/trật tự truy cập, kích thước tập dữ liệu, bộ nhớ khả dụng và độ nhạy độ trễ** thay vì chỉ dựa vào trực giác “prefetch luôn nhanh hơn”.

## 6. Chương trình Java 17+ hoàn chỉnh

Chương trình dưới đây tạo file tạm với dữ liệu có quy luật, ánh xạ 4.096 byte ở offset 512, kiểm tra `isLoaded()` hai lần và đọc byte theo chỉ số tuyệt đối.

**Tệp: `MappedLoadDemo.java`**

~~~java
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class MappedLoadDemo {
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("mapped-load-", ".bin");
        try {
            byte[] data = new byte[8192];
            for (int i = 0; i < data.length; i++) {
                data[i] = (byte) (i % 251);
            }
            Files.write(file, data);

            try (FileChannel channel =
                         FileChannel.open(file, StandardOpenOption.READ)) {
                MappedByteBuffer mapped =
                        channel.map(FileChannel.MapMode.READ_ONLY, 512, 4096);
                mapped.position(10);

                boolean before = mapped.isLoaded();
                boolean same = mapped.load() == mapped;
                boolean after = mapped.isLoaded();

                System.out.println("capacity=" + mapped.capacity());
                System.out.println("position=" + mapped.position());
                System.out.println("load returns same=" + same);
                System.out.println("first=" + (mapped.get(0) & 0xFF));
                System.out.println("last=" + (mapped.get(4095) & 0xFF));
                System.out.println(
                        "hint before=" + before + ", after=" + after);
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac MappedLoadDemo.java
java MappedLoadDemo
~~~

Một lần chạy thực tế trên JDK cho kết quả:

~~~text
capacity=4096
position=10
load returns same=true
first=10
last=89
hint before=false, after=false
~~~

**Dòng cuối chỉ là kết quả một lần chạy.** Nó có thể là `false,false`, `false,true`, `true,true` hoặc `true,false` tùy môi trường và thời điểm. Không viết unit test cố định kỳ vọng cho giá trị `isLoaded()`.

Giải thích từng phần:

1. Mapping bắt đầu từ byte số 512 trong file và có `capacity=4096`.
2. `position(10)` thiết lập vị trí đọc tương đối. `load()` không thay đổi position; hai lệnh `get(index)` absolute cũng không đổi position, nên chương trình in `position=10`.
3. `load()` trả về cùng đối tượng, vì vậy `same` là `true`.
4. `data[i] = (byte) (i % 251)`. Tại offset 512, giá trị là 10; tại offset 512 + 4095 = 4607, giá trị là 89. Phép `& 0xFF` giúp in byte như số không dấu 0–255.
5. `isLoaded()` trước và sau `load()` chỉ tạo các quan sát; chúng không cho phép kết luận chắc chắn trang còn resident ở lần `get` kế tiếp.

**Lưu ý về dọn tài nguyên:** `MappedByteBuffer` có vòng đời mapping tách biệt với `FileChannel`; đóng channel không tự unmap. Trên một số hệ điều hành, việc xóa file khi mapping vẫn còn có thể gặp hạn chế; lệnh `Files.deleteIfExists(file)` trong `finally` có thể ném `IOException` tùy môi trường. Đó là vấn đề vòng đời mapping, không phải bằng chứng `load()` sai.

## 7. load toàn buffer hay chỉ phần còn lại?

`MappedByteBuffer.load()` áp dụng cho **nội dung vùng mapped buffer** mà đối tượng đó đại diện, không nhận offset/length. Thay đổi `position` và `limit` của mapped buffer gốc không tự tạo một yêu cầu preload dành riêng cho khoảng `[position, limit)`.

~~~java
mapped.position(100);
mapped.limit(200);
mapped.load(); // không phải lệnh nạp duy nhất 100 byte trong [100, 200)
~~~

Đây là mã minh họa. Nếu muốn giới hạn vùng, Java 17 có `MappedByteBuffer.slice()` và `slice(index, length)` trả về mapped view của một **subrange**; `load()` trên view tác động tới vùng con ấy theo Javadoc.

~~~java
// mapped có capacity tối thiểu 8192 byte:
MappedByteBuffer part = mapped.slice(4096, 4096);
part.load(); // best effort đối với nội dung của part
~~~

Các vùng chia sẻ nội dung, không copy dữ liệu vào heap. Chỉ số và độ dài slice phải nằm trong giới hạn buffer. Hoặc trực tiếp map một cửa sổ bằng `FileChannel.map(mode, offset, length)` khi phù hợp.

`MappedByteBuffer` có capacity là `int`, vì vậy dạng `FileChannel.map(MapMode, long, long)` không dùng một buffer để biểu diễn một vùng vượt `Integer.MAX_VALUE` byte; tệp rất lớn thường cần chia thành nhiều cửa sổ.

## 8. Phân biệt load, force và thao tác đọc

| API/cơ chế | Vai trò | Có bảo đảm tránh fault về sau? |
|---|---|---|
| `FileChannel.map` | Tạo ánh xạ file vào địa chỉ ảo | Không |
| `MappedByteBuffer.load` | Best effort page-in | Không |
| `MappedByteBuffer.isLoaded` | Hint về residency | Không |
| `MappedByteBuffer.get` | Đọc byte thực sự | Không |
| `MappedByteBuffer.force` | Yêu cầu ghi thay đổi mapping về lưu trữ | Không |
| `FileChannel.force` | Yêu cầu đẩy cập nhật qua channel về thiết bị | Không |
| OS page cache | Cache nội dung file trong RAM | Không |

### 8.1. load khác force

`load()` phục vụ chiều **chuẩn bị dữ liệu trong RAM** cho truy cập. `MappedByteBuffer.force()` phục vụ chiều **ghi các thay đổi ra thiết bị** với những cam kết phụ thuộc chế độ mapping và loại storage.

~~~java
// Với mapped được tạo bằng FileChannel.MapMode.READ_WRITE:
mapped.put(0, (byte) 42);
mapped.force();
~~~

Đoạn minh họa trên không độc lập. `force()` có ý nghĩa cho `READ_WRITE`; với `READ_ONLY` hoặc `PRIVATE`, nó không có cùng tác dụng ghi dữ liệu chung ra file. `force()` cũng không phải cơ chế transaction nguyên tử của ứng dụng.

### 8.2. load khác Files.readAllBytes

`Files.readAllBytes` tạo mảng dữ liệu trong Java heap và đọc byte vào đó. `load()` không đưa file vào một mảng `byte[]`; nó yêu cầu OS cố gắng đưa các trang của mapping vào bộ nhớ vật lý. Vì thế không đánh giá hai phương thức chỉ bằng số lần cấp phát đối tượng Java.

## 9. Benchmark: đo cái gì?

Cùng một mã có thể cho kết quả khác biệt do:

- File cold/warm trong page cache.
- Readahead của filesystem, kích thước trang và mô hình truy cập tuần tự/ngẫu nhiên.
- Trạng thái JVM/JIT, áp lực RAM, số tiến trình cùng dùng dữ liệu.
- SSD/HDD, network file system, container hoặc VM.
- Sự khác nhau giữa minor fault và major fault, và chi phí I/O thực tế.

Quy trình đo nên:

1. Chuẩn bị tập dữ liệu và ít nhất hai chính sách: demand paging (không load) và gọi load trước xử lý.
2. Ghi nhận **tổng thời gian map + load + read + xử lý**, bên cạnh latency riêng của phần đọc.
3. Thử workload sequential, random, cold và warm với nhiều lần lặp; báo cáo p50/p95/p99.
4. Quan sát page fault counters và disk I/O từ OS nếu công cụ hỗ trợ; đừng dùng riêng `isLoaded()` làm thước đo.
5. Kiểm tra memory pressure và overhead của việc preload dữ liệu ít dùng.

Trên Linux có `perf`, ví dụ minh họa:

~~~bash
perf stat -e minor-faults,major-faults java MappedLoadDemo
~~~

Lệnh này tùy quyền và việc cài công cụ; bộ đếm cấp tiến trình **không bằng** số fault của một mapped buffer cụ thể. Windows/macOS cần công cụ tương ứng.

### Có nên dùng load?

| Mô hình | Hướng thử nghiệm |
|---|---|
| Quét toàn bộ vùng lớn trước tác vụ nhạy độ trễ | Thử load một lần trong warm-up |
| Truy cập vài vị trí rải rác trong file lớn | Thử demand paging/cửa sổ map nhỏ |
| Đọc lặp lại nhiều lần | Đo lợi ích load khi page cache đã warm |
| Máy chịu áp lực RAM | Đo chi phí eviction, thrashing và I/O |
| File có thể bị sửa/truncate đồng thời | Giải quyết an toàn vòng đời trước khi tối ưu |

## 10. Vòng đời và các rủi ro khi file thay đổi

Mapping sau khi tạo thành công **không phụ thuộc trực tiếp vòng đời FileChannel đã map nó**. Đóng channel không có nghĩa là unmap ngay; Java 17 không cung cấp `MappedByteBuffer.close()` hoặc `unload()` công khai để ép giải phóng. Mapping tiếp tục tồn tại đến khi buffer được thu hồi theo quản lý bộ nhớ.

Java 17 cảnh báo vùng mapped file **có thể không còn truy cập được** nếu tệp bị truncate hoặc sửa không phù hợp. Khi truy cập vùng không hợp lệ, ngoại lệ xảy ra có thể không được chỉ định trước và không nhất thiết xuất hiện ngay tại lời gọi truy cập. `load()` và `isLoaded()` không bảo vệ ứng dụng khỏi thay đổi kích thước tệp, race condition hoặc lỗi file system.

Đừng dựa vào `System.gc()` để unmap đúng một thời điểm; với ứng dụng production, phải quản lý vòng đời file/mapping, người đọc/người ghi và yêu cầu truy cập an toàn.

## 11. Các hiểu lầm thường gặp

1. **“map() đọc hết file vào RAM.”** Sai: OS có thể dùng demand paging.
2. **“load() đảm bảo không fault nữa.”** Sai: chỉ best effort, trang có thể bị evict.
3. **“isLoaded() == false nghĩa là file chưa ở RAM.”** Sai: đó chỉ là hint.
4. **“isLoaded() == true luôn chứng minh lần get tiếp theo nhanh.”** Sai: trạng thái có thể thay đổi.
5. **“Mỗi page fault đều tốn physical disk read.”** Sai: minor fault có thể được xử lý trong RAM.
6. **“load() thay cho force().”** Sai: chúng phục vụ page-in và flush, hai hướng khác nhau.
7. **“Gọi load() càng nhiều thì càng nhanh.”** Sai: prefetch dư có thể gây I/O và áp lực RAM.
8. **“position/limit tự giới hạn vùng load().”** Sai: dùng mapped slice/cửa sổ map khi cần.
9. **“Đóng FileChannel làm mapping biến mất.”** Sai: vòng đời mapping riêng.
10. **“isLoaded() có thể kiểm chứng file không bị truncate.”** Sai: residency không phải an toàn truy cập.

## 12. Tự kiểm tra

**Câu 1:** Gọi `load()` rồi `isLoaded()` vẫn ra `false` có phải lỗi?

**Đáp án:** Không. `load()` chỉ best effort và `isLoaded()` là hint.

**Câu 2:** Page fault có nhất thiết làm phát sinh I/O từ ổ đĩa?

**Đáp án:** Không. Minor fault có thể được giải quyết với dữ liệu đã có trong RAM/page cache. Major fault có thể phải đợi I/O.

**Câu 3:** Cần yêu cầu ghi thay đổi của READ_WRITE mapping về storage, dùng `load()` hay `force()`?

**Đáp án:** `force()` theo hợp đồng ghi, không phải `load()`.

**Câu 4:** Vì sao load toàn bộ file nhưng chỉ truy cập vài trang có thể chậm hơn không load?

**Đáp án:** Preload có thể đọc dư, tốn I/O và đẩy các trang hữu ích khỏi RAM/page cache.

### Tài liệu chính thức

- [Java 17: MappedByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/MappedByteBuffer.html)
- [Java 17: FileChannel.map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html#map(java.nio.channels.FileChannel.MapMode,long,long))
- [Java 17: ByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="memory-mapping-006">force cho thay đổi trên vùng ánh xạ</a>

<details>
<summary>Xem chi tiết</summary>

# `force()` cho thay đổi trên vùng ánh xạ bộ nhớ (Java NIO)

## 1. Bài toán: thay đổi trong memory-mapped file đã được lưu bền vững chưa?

Với Java NIO, `FileChannel.map(...)` ánh xạ một phần tệp vào bộ nhớ và trả về `MappedByteBuffer`. Ta có thể thay đổi nội dung thông qua `mapped.put(...)`, `mapped.putInt(...)` hoặc các phương thức ghi của `ByteBuffer`. Điều quan trọng là **byte mới đã nhìn thấy trong bộ nhớ không đồng nghĩa hệ thống lưu trữ đã nhận yêu cầu ghi bền vững các byte đó**.

Ví dụ, ứng dụng ghi một bản ghi nhị phân trực tiếp trên vùng ánh xạ:

~~~java
// Minh họa: channel mở để đọc và ghi, vùng map đủ dài.
MappedByteBuffer mapped = channel.map(
        FileChannel.MapMode.READ_WRITE, 0, 4096);
mapped.putInt(100, 42);
mapped.force();
~~~

Phương thức `force()` yêu cầu ghi những thay đổi đối với nội dung mapped buffer xuống thiết bị lưu trữ. Đây là điểm mấu chốt khi cần xác định **checkpoint lưu dữ liệu**, thay vì chờ hệ điều hành tự đẩy các trang bẩn (dirty pages) theo lịch của nó.

Cần phân biệt:

- **Thay đổi trong mapping:** ứng dụng đã sửa nội dung nhìn qua buffer; các trang nhớ có thể còn chưa được ghi bền vững.
- **`force()` trả về thành công:** đối với tệp trên **thiết bị lưu trữ cục bộ**, đặc tả Java đưa ra bảo đảm rằng những thay đổi thuộc phạm vi force đã được ghi tới thiết bị.
- **Tính nhất quán giao dịch khi crash:** là bài toán rộng hơn. `force()` không tự cung cấp atomic transaction, isolation, rollback, checksum, journal hay cơ chế phục hồi nhiều bước.

Bài này tập trung vào `MappedByteBuffer.force()` và `force(int index, int length)`, so sánh với `FileChannel.force(boolean)`, phân tích những giới hạn và đưa ra ví dụ Java 17+ chạy độc lập.

## 2. Ánh xạ file tạo ra vùng byte nào?

Để tạo mapping, dùng:

~~~java
MappedByteBuffer mapped =
        channel.map(FileChannel.MapMode.READ_WRITE, 4L, 8L);
~~~

`4L` là **offset tính theo byte trong file**, `8L` là số byte được ánh xạ. Buffer trả về có `position=0`, `limit=8`, `capacity=8` (mark ban đầu không được đặt).

~~~text
File offset:      0  1  2  3 |4  5  6  7  8  9 10 11|12 13
                              vùng ánh xạ 8 byte
Buffer index:                 0  1  2  3  4  5  6  7
                              ^                 ^
                          index đầu          index cuối
~~~

Bảng quy đổi:

| Buffer index | File offset khi map từ offset 4 |
|---:|---:|
| 0 | 4 |
| 1 | 5 |
| 4 | 8 |
| 7 | 11 |

Như vậy `mapped.putInt(0, value)` ghi vào **file byte 4–7**; `mapped.putInt(4, value)` ghi vào **file byte 8–11**. Index truyền vào `putInt(index, ...)` hay `force(index, length)` là **index của mapped buffer**, không phải offset file tuyệt đối.

`MappedByteBuffer` kế thừa khả năng truy cập tương đối và tuyệt đối từ `ByteBuffer`. Lệnh `putInt(index, value)` **không thay đổi position**, còn `putInt(value)` tương đối sử dụng position hiện tại và dịch position đi bốn byte.

### Ba chế độ `FileChannel.MapMode`

| Mode | Có ghi qua mapped buffer không? | Ảnh hưởng tới file thật | Tác dụng của `force()` |
|---|---|---|---|
| `READ_ONLY` | Không; `put(...)` ném `ReadOnlyBufferException` | Không có thay đổi qua buffer | Không có tác dụng |
| `READ_WRITE` | Có | Những thay đổi được truyền về file, thời điểm do hệ thống quyết định | **Yêu cầu ghi bền vững thay đổi của mapping** |
| `PRIVATE` | Có, theo copy-on-write | Thay đổi nằm trong bản sao private, **không truyền về file gốc** | Không có tác dụng với việc lưu thay đổi private vào file |

`READ_WRITE` và `PRIVATE` cần `FileChannel` mở cho **đọc và ghi**; `READ_ONLY` yêu cầu quyền đọc. Gọi `force()` trên mapping `PRIVATE` không biến thay đổi riêng thành dữ liệu ghi vào tệp gốc.

## 3. Hợp đồng của `MappedByteBuffer.force()`

Chữ ký trong Java 17:

~~~java
public final MappedByteBuffer force()
~~~

Đặc điểm quan trọng:

1. Yêu cầu ép những thay đổi của **nội dung mapped buffer** ra thiết bị chứa file.
2. Phạm vi là **toàn bộ `[0, capacity())`**, tương đương `force(0, capacity())`. Nó **không chỉ xử lý đoạn `[position, limit)`**.
3. Nếu file nằm trên **local storage**, Java bảo đảm khi phương thức trả về thành công, thay đổi thuộc phạm vi được yêu cầu từ khi buffer được tạo hoặc từ lần force tương ứng trước đó đã được ghi tới thiết bị.
4. Nếu file ở **non-local storage** (ví dụ một số hệ thống file mạng), đặc tả không đưa ra cùng bảo đảm.
5. Với `READ_ONLY` và `PRIVATE`, phương thức không có tác dụng; các mode triển khai đặc thù có thể có hành vi khác.
6. Nó trả về **chính `MappedByteBuffer`**, không trả về số byte flush hay boolean.
7. Lỗi I/O trong khi ghi nội dung có thể dẫn tới **`UncheckedIOException`**.

### Force không đổi position, limit hoặc capacity

~~~java
// Ví dụ minh họa: mapped đã ánh xạ READ_WRITE 128 byte.
mapped.put(10, (byte) 7);
mapped.position(100);
mapped.limit(101);
mapped.force();
~~~

Vùng đồng bộ được yêu cầu là **toàn mapping [0,128)**, không phải chỉ byte 100. Sau force, `position()` vẫn là 100 và `limit()` vẫn là 101. `force()` không thay mark hay di chuyển vị trí đọc/ghi để thực hiện việc đồng bộ.

**Lưu ý:** một chương trình đọc lại byte đã sửa ngay sau `put` hoặc `force` chỉ chứng minh dữ liệu nhìn thấy có giá trị đúng trong phiên đó. Đọc lại thành công **không phải bài kiểm tra mô phỏng mất điện**.

## 4. `force(index, length)`: ép ghi thay đổi của một vùng con

Java 17 hỗ trợ overload:

~~~java
public final MappedByteBuffer force(int index, int length)
~~~

- `index` là **index bắt đầu trong mapped buffer**.
- `length` là **số byte** của vùng yêu cầu.
- Vùng nửa mở: **`[index, index + length)`**.
- Phải có `index >= 0`, `length >= 0` và `index <= capacity() - length`. Tham số không hợp lệ gây **`IndexOutOfBoundsException`**.
- Phương thức không thay `position`/`limit`. Nó không bắt buộc hệ thống chỉ ghi đúng những byte được chọn: có thể ghi thêm byte lân cận do kích thước trang nhớ hoặc block thiết bị.

Ví dụ mapping dài 16 byte:

~~~java
// Ví dụ minh họa: mapped là READ_WRITE, capacity >= 16.
mapped.put(2, (byte) 0x11);
mapped.put(3, (byte) 0x22);
mapped.put(10, (byte) 0x33);
mapped.put(11, (byte) 0x44);

mapped.force(2, 2);   // Yêu cầu đồng bộ vùng [2,4).
mapped.force(10, 2);  // Yêu cầu đồng bộ vùng [10,12).
~~~

Hai lần force có các **vùng bắt buộc** khác nhau; không suy ra byte ngoài vùng được chọn chắc chắn chưa xuống thiết bị. OS có thể ghi nhiều hơn.

### Quy đổi phạm vi khi mapping bắt đầu từ giữa file

Giả sử `FileChannel.map(READ_WRITE, 4096, 2048)`.

| Lệnh | Vùng buffer | File byte tương ứng |
|---|---|---|
| `force(0, 128)` | [0,128) | [4096,4224) |
| `force(512, 256)` | [512,768) | [4608,4864) |
| `force()` | [0,2048) | [4096,6144) |

Các chỉ số đều tính theo byte. Java xử lý chi tiết page/block của hệ thống; không tự cộng offset file vào tham số `force(index,length)`.

### Chọn force toàn vùng hay từng vùng?

`force()` đơn giản và không cần theo dõi danh sách dirty ranges. `force(index,length)` diễn đạt chính xác phạm vi muốn đồng bộ, có thể phù hợp khi chỉ sửa vài bản ghi trong mapping lớn. **Đừng tự kết luận** overload vùng con luôn nhanh hơn trên mọi OS và mọi thiết bị; kernel có thể làm việc theo page lớn hơn vùng chỉ định. Hiệu năng cần đo bằng workload thực tế.

## 5. Chương trình Java 17+ chạy được

Chương trình tạo tệp tạm 16 byte, ánh xạ 8 byte từ file offset 4, ghi hai số nguyên, yêu cầu force một phần rồi toàn bộ, kiểm tra trạng thái buffer và đọc lại nội dung file dưới dạng hex.

Lưu file **`MappedForceDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HexFormat;

public class MappedForceDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("mapped-force-", ".bin");
        Files.write(file, new byte[16]);

        try (FileChannel channel = FileChannel.open(
                file,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE)) {

            // Buffer index 0..7 tương ứng file offset 4..11.
            MappedByteBuffer mapped = channel.map(
                    FileChannel.MapMode.READ_WRITE, 4, 8);

            mapped.putInt(0, 0x01020304);
            mapped.putInt(4, 0x0A0B0C0D);

            // Cố tình giới hạn vùng dùng cho get/put tương đối.
            mapped.position(6);
            mapped.limit(7);

            System.out.println("state=" + mapped.position()
                    + "/" + mapped.limit()
                    + "/" + mapped.capacity());

            // Yêu cầu ghi index 0..3, tương ứng file offset 4..7.
            mapped.force(0, 4);
            System.out.println("partial-force-done");

            // Yêu cầu ghi mọi thay đổi của mapping.
            mapped.force();
            System.out.println("full-force-done");

            System.out.println("state-after=" + mapped.position()
                    + "/" + mapped.limit()
                    + "/" + mapped.capacity());
        }

        byte[] actual = Files.readAllBytes(file);
        System.out.println("file-hex=" + HexFormat.of().formatHex(actual));

        // Thử dọn khi kết thúc JVM: mapping có thể còn hiệu lực
        // sau khi FileChannel đã đóng (tùy GC/OS).
        file.toFile().deleteOnExit();
    }
}
~~~

Biên dịch và chạy trong thư mục chứa source:

~~~bash
javac MappedForceDemo.java
java MappedForceDemo
~~~

**Stdout khi I/O thành công:**

~~~text
state=6/7/8
partial-force-done
full-force-done
state-after=6/7/8
file-hex=00000000010203040a0b0c0d00000000
~~~

Giải thích từng phần:

1. File ban đầu 16 byte bằng 0; mapping từ offset 4 với size 8.
2. `putInt(0, 0x01020304)` thay byte file 4–7 bằng `01 02 03 04`, còn `putInt(4, 0x0A0B0C0D)` thay byte file 8–11 bằng `0A 0B 0C 0D`. `ByteBuffer` dùng **big-endian** mặc định.
3. Hai lệnh `putInt(index, value)` là truy cập tuyệt đối và không dịch `position`. Chương trình đặt `position=6, limit=7, capacity=8`.
4. `force(0,4)` áp dụng vùng đầu; `force()` áp dụng toàn bộ 8 byte bất kể limit đang là 7.
5. Sau force, trạng thái `6/7/8` được giữ nguyên.
6. Kết quả hex thể hiện bốn byte 0 đầu tiên, tám byte đã cập nhật và bốn byte 0 cuối.

**Điều chương trình kiểm tra:** nội dung và trạng thái buffer. **Điều chương trình không kiểm tra:** khả năng phục hồi sau crash, tính nguyên tử của nhiều lần put và mức bảo đảm của remote storage.

### Vòng đời mapping và tệp tạm

`try-with-resources` đóng `FileChannel`. Nhưng `MappedByteBuffer` và mapping có thể tồn tại **sau khi channel đóng**, tới khi buffer được thu gom rác. Không có phương thức `unmap()` công khai chuẩn trên `MappedByteBuffer` trong Java 17. Trên một số OS, thao tác xóa/truncate tệp đang còn mapping có thể gặp hạn chế. `deleteOnExit()` là nỗ lực dọn khi JVM kết thúc, không phải cam kết xóa file ngay khi đóng channel.

## 6. Khác biệt quan trọng với `FileChannel.force(boolean)`

`MappedByteBuffer.force()` và `FileChannel.force(boolean)` phục vụ **hai đường thay đổi dữ liệu**:

| API | Phạm vi yêu cầu | Metadata | Có đảm bảo force các byte sửa bằng `mapped.put(...)`? |
|---|---|---|---|
| `mapped.force()` | Toàn bộ nội dung mapped region | Không có tham số metadata | **Có**, trong phạm vi và điều kiện của API |
| `mapped.force(index,length)` | Một phần mapped region | Không có tham số metadata | **Có** với phần vùng chỉ định |
| `channel.force(false)` | Các cập nhật nội dung file qua API channel theo hợp đồng | Không yêu cầu metadata | **Không được bảo đảm** |
| `channel.force(true)` | Các cập nhật nội dung **và metadata** qua API channel theo hợp đồng | Có yêu cầu metadata | **Vẫn không được bảo đảm** |

Theo tài liệu `FileChannel.force(boolean)`, phương thức **chỉ được bảo đảm** ép ghi những thay đổi file được thực hiện bằng các phương thức thuộc hợp đồng của `FileChannel` (và các trường hợp liên quan được đặc tả). Nó **có thể** hoặc **không** ép ghi thay đổi thực hiện thông qua một mapped byte buffer tạo bởi `map(...)`.

~~~java
// Ví dụ minh họa: mapped và channel đều hợp lệ.
mapped.put(10, (byte) 99);

channel.force(true); // Không thay thế bảo đảm force mapped buffer.
mapped.force();       // Phương thức phù hợp cho thay đổi bằng mapped.put.
~~~

**Đừng suy ra `channel.force(true)` là bản nâng cấp bao quát mọi dạng ghi.** Tham số `true` yêu cầu đồng bộ metadata theo phạm vi của `FileChannel`, nhưng không biến nội dung mapped buffer thành các thay đổi được bảo đảm flush qua channel.

Nếu ứng dụng vừa sửa nội dung mmap vừa cập nhật metadata hoặc ghi trực tiếp bằng channel, cần thiết kế chính sách đồng bộ cho **từng đường cập nhật**. Ngoài ra, việc tạo file, đổi tên, sửa directory entry hay commit nhiều tệp liên quan có thể cần quy tắc bổ sung tùy OS và filesystem.

## 7. `load()` và `isLoaded()` không phải cách lưu bền vững

Ba API có mục tiêu khác nhau:

- `mapped.load()`: cố gắng (best-effort) đưa các trang của mapping vào **physical memory** để giảm page fault khi truy cập, **không phải flush ra thiết bị**.
- `mapped.isLoaded()`: trả về một gợi ý trang mapped có khả năng đang ở bộ nhớ vật lý; thông tin này có thể thay đổi ngay sau khi kiểm tra.
- `mapped.force()`: yêu cầu đưa **các thay đổi mapped** xuống thiết bị.

~~~java
// Mã minh họa.
mapped.load();                   // Hỗ trợ đưa dữ liệu từ file vào memory.
boolean likelyLoaded = mapped.isLoaded();

mapped.put(0, (byte) 7);
mapped.force();                  // Yêu cầu ghi thay đổi vào storage.
~~~

`isLoaded()==true` không chứng minh dirty data đã bền vững. `load()` gọi sau khi put cũng không thay thế `force()`.

## 8. Giới hạn của độ bền sau khi force

### 8.1. Thiết bị local và remote khác nhau

Java 17 quy định bảo đảm khi `MappedByteBuffer.force()` thành công **nếu file ở thiết bị lưu trữ cục bộ**. Đối với storage không phải local (ví dụ một số network filesystem), Java **không bảo đảm tương tự**. Nếu dữ liệu đi qua network cache, replication hoặc cloud volume, mức bền vững phải dựa vào hợp đồng và triển khai của hệ lưu trữ đó.

### 8.2. Không có tính nguyên tử cho nhiều lần cập nhật

~~~java
mapped.putInt(0, newValue);
mapped.putInt(4, newChecksum);
mapped.force();
~~~

*Đây là đoạn minh họa* có hai giá trị đã định nghĩa. Nếu tiến trình crash sau lệnh `putInt` đầu nhưng trước lệnh tiếp theo, định dạng có thể không nhất quán. `force()` cũng không tự làm hai trường được cập nhật **đồng thời** với các reader.

Với dữ liệu quan trọng, cân nhắc version, checksum, write-ahead log, double buffering, commit marker và cơ chế phục hồi. Chỉ force **không có rollback**.

### 8.3. Nội dung khác với metadata

`MappedByteBuffer.force()` bảo đảm về **nội dung vùng ánh xạ** theo điều kiện đã nêu. Nó không có tùy chọn `metaData=true`. Nếu chương trình mở rộng tệp, tạo file mới, sửa thuộc tính hay đổi tên, không được kết luận một lời gọi `mapped.force()` đã khiến mọi metadata và directory entry liên quan bền vững trong mọi trường hợp.

### 8.4. Concurrent writer và truncate

Một mapped buffer có thể bị tác động bởi chương trình hoặc thread khác. Nếu file nền bị truncate trong khi mapping đang sống, một phần vùng mapped có thể trở nên **không truy cập được**; đặc tả cho phép xảy ra ngoại lệ không được chỉ định cụ thể tại thời điểm truy cập hoặc sau đó. Vì vậy, cần:

- Phối hợp quyền sửa/truncate file trong suốt thời gian còn mapping.
- Kiểm soát nhiều writer đụng cùng byte/record, bằng lock hoặc giao thức đồng bộ phù hợp.
- Không xem `force()` là file lock hay một transaction đảm bảo isolation.
- Không dùng việc có thể đọc lại giá trị mới như chứng cứ rằng đồng thời và crash consistency đã được giải quyết.

## 9. Mốc force: mỗi bản ghi hay theo batch?

| Chiến lược | Ưu điểm | Hạn chế |
|---|---|---|
| `force()` sau từng cập nhật quan trọng | Có checkpoint bền vững sớm sau mỗi cập nhật thành công | Tăng số lượt yêu cầu I/O |
| `force()` sau nhóm cập nhật | Ít lời gọi, có thể cải thiện throughput | Thay đổi sau checkpoint cuối có thể chưa bền vững khi crash |
| `force(index,length)` trên vùng dirty | Diễn đạt chính xác vùng cần đồng bộ | Phải tính chính xác index; OS có thể ghi theo page rộng hơn |
| Không chủ động force | Ít chi phí đồng bộ tức thời | Không có mốc bảo đảm từ `MappedByteBuffer.force` |

**Không tồn tại quy tắc “force càng nhiều càng đúng trong mọi trường hợp”.** Chọn checkpoint theo yêu cầu mất dữ liệu tối đa có thể chấp nhận, trạng thái transaction của ứng dụng và hiệu năng trên thiết bị thực tế. Đặc biệt không giả định `force(index,length)` luôn nhanh hơn `force()`: dirty pages, page size, hệ thống tệp và bộ điều khiển I/O có thể làm khác nhau.

### Khi có nhiều vùng mapped hoặc view

Một file có thể được map thành nhiều `MappedByteBuffer` với các phạm vi khác nhau. Khi cần force phần đã sửa, xác định **mapping sở hữu vùng** và index **tương đối với mapping ấy**. Tránh gọi force trên một mapping không liên quan rồi cho rằng tất cả thay đổi của mapping khác đều đã đạt cùng bảo đảm.

Với `slice()` và `duplicate()`, các view có thể chia sẻ dữ liệu nền nhưng giữ position/limit riêng. Phạm vi index của một view phải được tính theo chính view đó; nếu ứng dụng quản lý việc đồng bộ qua mapping gốc, cần chuyển đổi chỉ số về đúng vùng file.

## 10. Xử lý lỗi: đừng báo thành công nếu force thất bại

Các tình huống cần lưu ý:

| Tình huống | Hậu quả/ngoại lệ |
|---|---|
| `force(index,length)` vượt capacity hoặc chỉ số âm | `IndexOutOfBoundsException` |
| Lỗi I/O khi flush mapped region | `UncheckedIOException` |
| `put(...)` lên mapping `READ_ONLY` | `ReadOnlyBufferException` |
| File bị truncate, vùng mapped không còn truy cập được | Có thể xuất hiện ngoại lệ do nền tảng, không được API xác định cụ thể |
| `force()` trên mapping `PRIVATE` | Không ghi thay đổi private về file thật |
| `FileChannel.map(...)` với quyền/điều kiện file không hợp lệ | Có thể ném lỗi quyền, `IOException` hoặc lỗi tham số tùy trường hợp |

Đoạn minh họa xử lý force:

~~~java
try {
    mapped.putInt(recordIndex, value);
    mapped.force(recordIndex, Integer.BYTES);
} catch (java.io.UncheckedIOException e) {
    // Không xác nhận độ bền của bản ghi này.
    // Báo lỗi để tầng ứng dụng quyết định recovery/retry.
    throw e;
}
~~~

Ngoại lệ trên chỉ xử lý **lỗi đồng bộ I/O**; thao tác ghi hoặc index có thể thất bại theo cách khác. Nếu ứng dụng retry, cần thiết kế idempotency và xử lý cạnh tranh trước.

### Kiểm chứng nội dung và độ bền là hai loại test

- **Test nội dung:** tạo file tạm, map, put, force, đóng channel, đọc lại bytes và so sánh với kỳ vọng. Đây là test hợp lệ cho hành vi đọc/ghi, như chương trình ở mục 5.
- **Test lỗi:** giả lập lỗi đường lưu trữ ở abstraction phù hợp; chắc chắn ứng dụng không gắn trạng thái commit thành công khi force thất bại.
- **Test khôi phục crash:** cần môi trường kiểm soát crash/reboot, sau đó kiểm tra dữ liệu/metadata và phục hồi. Đây là lớp test mạnh hơn; đọc lại file ngay trong JVM hiện tại **không chứng minh** điều này.
- **Test đồng thời:** nhiều writer truy cập chung mapping, thử những thứ tự thực thi khó để xác nhận giao thức lock/record không hỏng.

## 11. Lỗi thường gặp

| Sai lầm | Cách sửa |
|---|---|
| “`put(...)` nghĩa là lưu bền” | `put` sửa vùng nhớ mapped; khi cần checkpoint, gọi `MappedByteBuffer.force()`. |
| “`FileChannel.force(true)` luôn flush mọi mmap” | Không được đặc tả bảo đảm; dùng `mapped.force()` cho thay đổi mapped. |
| “`force()` chỉ xử lý từ position đến limit” | Nó xử lý toàn vùng [0,capacity); dùng overload nếu muốn chọn đoạn con. |
| “Tham số index trong `force(index,length)` là offset file” | Index tương đối với buffer; cộng offset mapping khi diễn giải file. |
| “Force vùng con chỉ ghi chính xác vùng con đó” | OS có thể ghi nhiều byte hơn do page/block. |
| “`load()` đồng nghĩa flush” | `load()` phục vụ prefetch vào memory; `force()` phục vụ ghi từ mapping xuống storage. |
| “`MapMode.PRIVATE` có thể force để cập nhật file gốc” | Thay đổi copy-on-write không truyền về file thật. |
| “Force tạo transaction hai bước” | Cần protocol/lock/version/log riêng. |
| “Đóng FileChannel làm mapping bị hủy ngay” | Mapping có vòng đời độc lập với channel. |
| “Force trả về trên network filesystem có cùng bảo đảm như local” | Java không bảo đảm như vậy. |

## 12. Tình huống tự kiểm tra

1. `map(READ_WRITE, 1000, 100)` rồi `force(10,5)`: file byte nào thuộc vùng yêu cầu? **[1010,1015)**.
2. `position=70, limit=80, capacity=100` rồi `force()`: phạm vi nào? **[0,100)**. Position/limit vẫn 70/80.
3. Capacity 100, gọi `force(98,4)`: kết quả? **`IndexOutOfBoundsException`**.
4. Mapping `PRIVATE` được sửa rồi force: file gốc có được lưu thay đổi private không? **Không**.
5. Gọi `mapped.load()` sau khi put có thể thay force không? **Không**.
6. `channel.force(true)` sau `mapped.put(...)` đã đủ bảo đảm chưa? **Không được bảo đảm bởi API**, nên force trên mapped buffer.
7. Nếu force thành công với local file, chương trình có thêm tính nguyên tử cho hai lệnh `putInt` không? **Không**.
8. File mạng có bảo đảm force tương tự local không? **Không theo đặc tả Java 17**.
9. Buffer đã map nhưng channel đóng, mapping có chắc biến mất ngay không? **Không**.
10. Đọc lại đúng byte sau force có chứng minh khả năng phục hồi sau mất điện không? **Không**.

## 13. Tham khảo chính thức

- [Java SE 17 — MappedByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/MappedByteBuffer.html): `force()`, `force(index,length)`, ngoại lệ, các điều kiện bảo đảm local/remote, `load()` và `isLoaded()`.
- [Java SE 17 — FileChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html): `map(...)`, các `MapMode` và sự khác biệt giữa `force(boolean)` với force của mapped buffer.
- [Java SE 17 — ByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html): vị trí/giới hạn, truy cập index tuyệt đối, `putInt` và thứ tự byte.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="memory-mapping-007">Đóng channel không tự kết thúc vòng đời vùng ánh xạ</a>

<details>
<summary>Xem chi tiết</summary>

# Đóng FileChannel không tự kết thúc vòng đời vùng ánh xạ

Khi dùng Java NIO để ánh xạ nội dung một tệp vào bộ nhớ, lập trình viên thường viết `FileChannel.map(...)` để lấy `MappedByteBuffer`, sau đó đóng `FileChannel` bằng `try-with-resources`. **Vùng ánh xạ vẫn có thể tiếp tục tồn tại và được truy cập thông qua buffer sau khi channel đã đóng.** Điều này được quy định trong hợp đồng của `FileChannel.map`: vùng ánh xạ đã thiết lập không phụ thuộc vào channel dùng để tạo nó; đóng channel không làm ánh xạ mất hiệu lực.

Điểm này rất quan trọng với vòng đời tài nguyên, khả năng xóa/đổi tên tệp, mức sử dụng bộ nhớ native và cách bảo đảm dữ liệu sửa qua vùng ánh xạ được ghi ra thiết bị lưu trữ. Đóng channel là đúng, nhưng **đóng channel và kết thúc memory mapping là hai việc khác nhau**.

## 1. Phân biệt channel, vùng ánh xạ và mapped buffer

Cần nhận diện ba lớp tài nguyên:

1. **`FileChannel`:** Kênh I/O gắn với một lần mở tệp. Nó có `read`, `write`, `position`, `size`, `map`, `force` và `close`. Khi đóng channel, các thao tác trên chính channel không còn hợp lệ.
2. **Vùng ánh xạ bộ nhớ (memory mapping):** Mối liên hệ do hệ điều hành/provider thiết lập giữa một vùng địa chỉ ảo và một đoạn của tệp. Ánh xạ có vòng đời tài nguyên riêng. Những trang dữ liệu có thể được đưa vào RAM theo yêu cầu; không nên đồng nhất toàn bộ kích thước ánh xạ với RAM vật lý đã được cấp.
3. **`MappedByteBuffer`:** Đối tượng Java biểu diễn vùng ánh xạ và cung cấp `get`, `put`, `position`, `limit`, `force` và các thao tác buffer. Đây là phương tiện Java truy cập vùng ánh xạ sau khi `map` trả về.

Có thể hình dung:

```text
FileChannel (open)
       |
       | map(MapMode.READ_WRITE, offset, size)
       v
MappedByteBuffer -----> vùng địa chỉ ánh xạ -----> dữ liệu tệp
       ^
       | tiếp tục dùng get/put/force
FileChannel (closed)
```

Mũi tên mô tả quan hệ tài nguyên, không phải sơ đồ sở hữu đối tượng chính xác của mọi JVM.

**Đóng channel giải phóng tài nguyên của channel, không tự thu hồi vùng ánh xạ.** Trong khi còn hợp lệ, `MappedByteBuffer` vẫn có thể đọc và, nếu map cho phép, ghi dữ liệu mà không cần giữ `FileChannel` ở trạng thái mở. Ngược lại, không thể gọi `channel.force(...)` trên channel đã đóng chỉ vì một buffer từ channel đó còn tồn tại.

## 2. Tạo vùng ánh xạ bằng `FileChannel.map`

Chữ ký phổ biến:

```java
MappedByteBuffer map(FileChannel.MapMode mode,
                     long position,
                     long size) throws IOException
```

Đây là chữ ký minh họa, không phải một chương trình độc lập.

- `mode` chỉ định chế độ ánh xạ: `READ_ONLY`, `READ_WRITE` hoặc `PRIVATE`.
- `position` là offset **byte trong tệp** nơi ánh xạ bắt đầu; không phải `ByteBuffer.position()`.
- `size` là số byte được ánh xạ. Với overload trả `MappedByteBuffer`, capacity của buffer là kiểu `int` nên vùng ánh xạ cho một buffer phải nằm trong giới hạn capacity; cần chia thành các vùng nếu xử lý tệp rất lớn.
- Trên Java 17, ánh xạ cần được thiết lập qua channel còn mở với quyền phù hợp. Sau khi map thành công, channel có thể được đóng độc lập.

Các chế độ:

| `MapMode` | Cách truy cập | Tác động khi ghi qua buffer |
| --- | --- | --- |
| `READ_ONLY` | Đọc dữ liệu đã ánh xạ | `put` bị từ chối bằng `ReadOnlyBufferException` |
| `READ_WRITE` | Đọc và thay đổi dữ liệu tệp qua mapping | Thay đổi có thể được phản ánh vào tệp; dùng `MappedByteBuffer.force()` khi cần yêu cầu đẩy cập nhật theo hợp đồng API |
| `PRIVATE` | Ánh xạ copy-on-write | Các thay đổi riêng không được ghi trở lại tệp theo ngữ nghĩa private mapping |

`READ_WRITE` cần channel mở với quyền đọc và ghi. Với `READ_ONLY`, cần quyền đọc. Không nên suy ra chế độ ánh xạ từ việc channel có thể `write()` hay không; luôn chọn mode và `StandardOpenOption` phù hợp trước khi gọi `map`.

Việc `map` thành công tạo ánh xạ; thao tác này **không** có nghĩa toàn bộ byte đã được chép vào Java heap hoặc đều đã ở RAM. Hệ điều hành có thể nạp các trang theo nhu cầu truy cập, và JVM có thể sử dụng tài nguyên native ngoài Java heap.

## 3. Chương trình Java 17+ chạy được: channel đã đóng, buffer vẫn dùng được

Chương trình tạo một tệp tạm có năm byte ASCII, ánh xạ với `READ_WRITE`, đóng `FileChannel` khi thoát khối `try`, rồi **mới đọc, sửa và force qua `MappedByteBuffer`**. Nó không dựa vào tên tệp ngẫu nhiên để tạo stdout nên có kết quả rõ ràng.

Tạo tệp **`MappedLifetimeDemo.java`**:

```java
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class MappedLifetimeDemo {
    private MappedLifetimeDemo() {}

    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("mapped-lifetime-", ".txt");
        // Yêu cầu dọn tệp tạm khi JVM kết thúc; đây là best effort.
        file.toFile().deleteOnExit();

        Files.writeString(file, "ABCDE", StandardCharsets.US_ASCII);
        MappedByteBuffer mapped;

        try (FileChannel channel = FileChannel.open(
                file,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE)) {
            mapped = channel.map(
                    FileChannel.MapMode.READ_WRITE, 0, 5);
            System.out.println("inside: channelOpen=" + channel.isOpen());
        } // Chỉ channel được đóng tại đây.

        // Vùng ánh xạ vẫn có thể đọc, dù channel đã đóng.
        System.out.println("after close: first=" + (char) mapped.get(0));

        // Ghi thông qua mapping, không mở lại FileChannel để ghi.
        mapped.put(1, (byte) 'Z');
        System.out.println("mapped second=" + (char) mapped.get(1));

        // Yêu cầu đồng bộ thay đổi của mapping với thiết bị lưu trữ.
        mapped.force();
        System.out.println("file after force="
                + Files.readString(file, StandardCharsets.US_ASCII));
    }
}
```

Biên dịch và chạy:

```bash
javac MappedLifetimeDemo.java
java MappedLifetimeDemo
```

Kết quả khi tệp tạm được tạo và truy cập thành công:

```text
inside: channelOpen=true
after close: first=A
mapped second=Z
file after force=AZCDE
```

Tại sao kết quả hợp lý?

1. File `ABCDE` có độ dài 5 byte, nên `map(READ_WRITE, 0, 5)` ánh xạ toàn bộ tệp.
2. `MappedByteBuffer mapped` được khai báo bên ngoài `try`; sau dấu `}`, channel đóng nhưng đối tượng buffer vẫn còn được tham chiếu và vùng ánh xạ còn hợp lệ.
3. `mapped.get(0)` đọc byte `A` từ mapping đã tạo. Nếu thử `channel.read(...)` bên ngoài khối `try` (giả sử còn tham chiếu), thao tác trên channel đã đóng sẽ thất bại.
4. `mapped.put(1, 'Z')` thay byte tại offset 1. Vì đây là `READ_WRITE`, thay đổi có thể được đồng bộ về tệp. `mapped.force()` yêu cầu đẩy thay đổi của mapping, kể cả khi channel gốc đã đóng.
5. `Files.readString(...)` mở lại tệp để đọc và thấy `AZCDE`. Câu lệnh `deleteOnExit()` yêu cầu dọn tệp khi JVM kết thúc, nhưng không phải biện pháp unmap hoặc bảo đảm dọn ngay trên mọi hệ điều hành.

**Lưu ý thực hành:** Không đưa `Files.delete(file)` vào trước khi vùng ánh xạ không còn được sử dụng rồi giả định chắc chắn xóa được trên Windows. Một số hệ thống có thể giữ tài nguyên tệp vì vùng ánh xạ còn sống. Ví dụ dùng `deleteOnExit()` để tránh làm thất bại luồng minh họa bởi thời điểm unmap; lệnh dọn khi thoát cũng chỉ là nỗ lực tốt nhất.

## 4. `close()` khác với unmap như thế nào?

`FileChannel.close()` đóng kênh và giải phóng tài nguyên do lần mở channel sở hữu. Tùy nền tảng, các yêu cầu I/O còn thực hiện có thể bị ảnh hưởng. Sau khi đóng, `channel.isOpen()` trả `false` và những lời gọi yêu cầu channel mở có thể ném `ClosedChannelException`.

**Unmap** là việc chấm dứt ánh xạ, trả lại các tài nguyên liên quan như vùng địa chỉ ảo và tham chiếu tới đối tượng tệp mà hệ điều hành đang quản lý. `MappedByteBuffer` trong Java 17 **không có phương thức public `close()` hoặc `unmap()` tiêu chuẩn** để người sử dụng gọi như đóng channel.

Tài liệu `FileChannel.map` mô tả vùng ánh xạ còn hợp lệ cho đến khi chính buffer trở thành đối tượng có thể bị garbage collection. Đừng suy luận rằng chỉ một biến cục bộ ra khỏi scope, `mapped = null` hoặc một lần GC cụ thể sẽ buộc ánh xạ được tháo *ngay lập tức*. Điều kiện không còn khả năng truy cập và thời điểm thực tế thu hồi là hai chuyện khác nhau.

Các view như `slice()`, `duplicate()` hoặc `asReadOnlyBuffer()` có thể chia sẻ dữ liệu với mapping gốc. Việc giữ một view cho code khác sử dụng có thể giữ liên quan tài nguyên ánh xạ tồn tại; không nên coi bỏ tham chiếu buffer gốc là bằng chứng mọi đối tượng tham chiếu tới dữ liệu đã hết.

So sánh:

| Hành động | Channel | Mapping / buffer |
| --- | --- | --- |
| Thoát `try (FileChannel ch = ...)` | Channel tự đóng | Mapping có thể tiếp tục hoạt động |
| `ch.close()` | Không thể tiếp tục I/O qua ch | Không tự unmap vùng đã ánh xạ |
| `mapped.clear()` | Không ảnh hưởng channel | Chỉ reset position/limit, **không xóa byte và không unmap** |
| `mapped.flip()` | Không ảnh hưởng channel | Chỉ thay đổi trạng thái buffer |
| `mapped.force()` | Không cần channel gốc còn mở | Yêu cầu đồng bộ nội dung vùng ánh xạ có sửa, **không giải phóng mapping** |
| Không còn tham chiếu sử dụng mapping | Channel có thể đã đóng từ trước | Vùng ánh xạ có thể được thu hồi theo cơ chế JVM/GC về sau, thời điểm không tất định |

Trong code quản lý tài nguyên, nên coi **vòng đời FileChannel** và **vòng đời tất cả đối tượng đang dùng mapping** là hai ranh giới rõ ràng.

## 5. Đóng channel có làm dữ liệu tự ghi ra đĩa không?

Không nên đánh đồng **khả năng đọc thấy dữ liệu vừa sửa**, **thời điểm hệ điều hành chuyển dữ liệu từ trang nhớ bẩn xuống thiết bị** và **độ bền dữ liệu sau sự cố mất điện**.

Khi sửa `READ_WRITE` mapping, thay đổi được phản ánh tới nội dung tệp theo cơ chế memory mapping, nhưng JVM không hứa mọi byte đã được lưu bền vững ngay lập tức chỉ vì `put()` trả về. Đối với tệp nằm trên thiết bị lưu trữ cục bộ, `MappedByteBuffer.force()` là API để yêu cầu ghi các thay đổi của vùng mapping về thiết bị theo hợp đồng Java. Với ánh xạ trên một số loại tệp/thiết bị khác, bảo đảm có thể khác hoặc không hoàn toàn được định nghĩa.

**Đóng FileChannel không thay cho `MappedByteBuffer.force()`.** Sau khi đóng channel, buffer còn sống vẫn có thể gọi `force()`. Ngược lại, `channel.force(true)` phải được gọi khi channel còn mở và có hợp đồng liên quan các cập nhật tệp qua channel; không nhầm nó là phương thức giải phóng vùng ánh xạ.

Các mode cũng khác nhau:

- `READ_WRITE`: sửa vùng ánh xạ để tác động nội dung tệp; có thể cần `mapped.force()` theo yêu cầu durability.
- `READ_ONLY`: không sửa dữ liệu qua buffer, `put` bị cấm. Việc gọi `force` không biến nó thành writable.
- `PRIVATE`: ghi theo cơ chế copy-on-write riêng, không ghi các thay đổi của private mapping trở lại tệp. Không dùng `force()` để mong dữ liệu private mapping được persist vào file gốc.

Khi thiết kế cập nhật quan trọng, cần cả chính sách **ngăn ghi chồng**, **xử lý lỗi I/O**, **xác minh nội dung** và **durability**. `force()` không biến một loạt thay đổi byte phức tạp thành một transaction nguyên tử.

## 6. Tại sao file có thể chưa xóa/đổi tên được?

Memory mapping được hệ điều hành quản lý. Tệp có thể còn bị tham chiếu bởi một vùng ánh xạ dù ứng dụng đã đóng descriptor/channel ban đầu.

Hành vi khi `Files.delete(path)`, `Files.move(path, ...)` hoặc truncate tệp được ánh xạ **không đồng nhất trên mọi hệ thống**:

- Trên một số hệ thống kiểu Unix, tên đường dẫn có thể được unlink dù mapping vẫn còn, còn các trang dữ liệu đã ánh xạ vẫn có thể tiếp tục truy cập theo ngữ nghĩa hệ điều hành. Điều này không phải hợp đồng bảo đảm cho mọi provider Java hoặc mọi tình huống.
- Trên Windows, ánh xạ đang hoạt động có thể khiến xóa, di chuyển hoặc thay thế tệp thất bại do tài nguyên hệ điều hành vẫn tham chiếu tới tệp. Đóng channel riêng lẻ chưa bảo đảm giải phóng ràng buộc đó.
- Hệ thống tệp mạng, container, trình diệt virus, file lock và chính sách chia sẻ tệp có thể tạo ra hành vi khác nữa.

Do đó **“channel đã đóng” không đủ làm điều kiện chứng minh tệp có thể xóa ngay**. Nếu quy trình phải thay thế một file theo chu kỳ, cần thiết kế vòng đời sao cho các mapping cũ không còn được sử dụng trước bước xóa/đổi tên, đồng thời xử lý ngoại lệ thực tế từ `Files.delete/move`.

Không nên dùng cơ chế `System.gc()` và vòng đợi để “ép” unmap: `System.gc()` chỉ là yêu cầu/hint đến JVM, không phải cam kết rằng mapping được thu hồi vào thời điểm xác định. Việc gọi các API nội bộ không chuẩn như `Unsafe.invokeCleaner` để cưỡng ép giải phóng có thể phụ thuộc triển khai, phá vỡ an toàn khi mapping còn đang được dùng và không thích hợp làm giải pháp mặc định.

### Không cắt ngắn tệp khi mapping đang được sử dụng

Nếu một vùng đã ánh xạ mà tệp bên dưới bị truncate hoặc nội dung/vùng liên quan không còn truy cập được, việc `get/put` sau đó có thể thất bại với một ngoại lệ **không được Java chỉ định cụ thể**. Đây không phải một kết quả có thể dựa vào một loại exception duy nhất để xử lý mọi nền tảng.

Ứng dụng cần phối hợp lifecycle với code ghi/truncate/xóa tệp. Nếu nhiều tiến trình cùng thao tác, khóa trong Java đơn thuần không bảo vệ được trước một tiến trình khác bỏ qua khóa.

## 7. Mapping còn sống ảnh hưởng tài nguyên nào?

Một memory mapping có thể tiêu thụ **không gian địa chỉ ảo**, metadata native và các trang RAM khi được chạm tới. Không được suy ra rằng vùng ánh xạ dài 1 GiB luôn chiếm 1 GiB RAM vật lý ngay khi `map` trả về. Trên nhiều hệ thống các trang được nạp theo nhu cầu và có thể chịu cơ chế cache/thay trang của OS.

Một file lớn được chia thành hàng trăm mapping và tất cả buffer/view bị lưu trong cache có thể gây áp lực tài nguyên ngay cả khi các `FileChannel` tạo ra chúng đều đã đóng:

- Số lượng mapping/virtual memory ranges và dung lượng không gian địa chỉ có giới hạn.
- Những trang đang hoạt động có thể ảnh hưởng page faults, page cache, bộ nhớ vật lý và hành vi swap.
- Mapped buffers vẫn có các đối tượng/metadata Java; giữ tham chiếu lâu khiến việc thu hồi tài nguyên mapping khó diễn ra.
- Theo dõi riêng Java heap không cho thấy toàn bộ tài nguyên native và memory mapping. Trên JVM có hỗ trợ, `BufferPoolMXBean` với pool `mapped` cung cấp một phần chỉ số; kết hợp với bộ nhớ tiến trình/hệ điều hành mới có bức tranh đầy đủ.

Ví dụ đo chỉ số theo `BufferPoolMXBean` (đoạn minh họa, đặt trong lớp/phương thức Java):

```java
for (java.lang.management.BufferPoolMXBean pool :
        java.lang.management.ManagementFactory.getPlatformMXBeans(
                java.lang.management.BufferPoolMXBean.class)) {
    if ("mapped".equals(pool.getName())) {
        System.out.println("mapped count=" + pool.getCount());
        System.out.println("mapped capacity=" + pool.getTotalCapacity());
        System.out.println("mapped memoryUsed=" + pool.getMemoryUsed());
    }
}
```

Các chỉ số này **không đồng nghĩa** với RSS, page cache, số descriptor mở hoặc toàn bộ native memory; giá trị có thể phụ thuộc JVM. Nếu mapping tích lũy không mong muốn, hãy tìm đối tượng/view vẫn được giữ và kiểm tra chiến lược cache/pool thay vì chỉ nhìn số `FileChannel` đang mở.

## 8. Quản lý vòng đời đúng theo nghiệp vụ

Một số cách thiết kế có thể tránh lệ thuộc vào thời điểm thu hồi mapping:

### 8.1. Giữ phạm vi sử dụng rõ ràng

Tạo mapping, xử lý hoàn tất và ngừng chia sẻ buffer khi kết thúc một tác vụ. Nếu gửi `MappedByteBuffer` sang thread khác hoặc lưu trong cache, phải xem đây là phần kéo dài vòng đời vùng ánh xạ, ngay cả khi channel tạo mapping đã đóng từ lâu.

```java
// Đoạn minh họa: Path file đã có, đọc xong trong một phạm vi rõ ràng.
try (FileChannel channel = FileChannel.open(
        file, java.nio.file.StandardOpenOption.READ)) {
    MappedByteBuffer mapped =
            channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
    while (mapped.hasRemaining()) {
        process(mapped.get()); // Hàm xử lý nghiệp vụ minh họa.
    }
}
// Channel đã đóng; không còn chủ động dùng mapped.
// Không có lời hứa rằng GC/unmap sẽ xảy ra đúng tại dấu }.
```

Đoạn trên **không độc lập**: cần import `MappedByteBuffer`, `FileChannel`, khai báo `file`, hàm `process` và xử lý `IOException`. Với file quá lớn cho một `MappedByteBuffer`, phải chia thành các vùng phù hợp. Với file có thể rỗng, logic xử lý cần cân nhắc cách ứng dụng muốn xử lý vùng kích thước 0.

### 8.2. Dùng `FileChannel.read/write` nếu cần đóng tài nguyên có thể dự đoán

Nếu yêu cầu là “xử lý xong thì có thể nhanh chóng đóng và thay thế/xóa file”, nhưng ứng dụng không có nhu cầu memory mapping rõ ràng, dùng đọc/ghi từng khối qua `FileChannel` với `ByteBuffer` thông thường là lựa chọn đáng cân nhắc. Một channel đóng được theo phạm vi `try-with-resources` sẽ đơn giản hơn về ownership của tài nguyên mapping.

Tuy nhiên `FileChannel` và bộ đệm thường cũng cần quản lý concurrency, ngoại lệ và trạng thái đọc/ghi. Không nên cho rằng đổi sang `read/write` sẽ tự giải quyết toàn bộ lỗi chia sẻ dữ liệu.

### 8.3. Chia vùng lớn và tránh giữ mapping thừa

Khi bắt buộc memory-map file lớn, ánh xạ theo các vùng vừa đủ để xử lý; chỉ giữ các vùng mà tác vụ thực sự đang dùng. Tránh lưu mọi mapping trong danh sách có vòng đời dài vô hạn. Nếu tái sử dụng mapping giữa các request, cần giới hạn số lượng, chủ sở hữu và cách hủy cache khi dữ liệu nền thay đổi.

### 8.4. Không áp dụng mẹo “explicit unmap” không chuẩn

Các phiên bản Java mới có những cơ chế quản lý vùng bộ nhớ khác nhau, nhưng trong phạm vi API `MappedByteBuffer` của **Java 17**, không có thao tác public `unmap()` để bảo đảm kết thúc ánh xạ đồng bộ. Không viết thư viện phụ thuộc reflection vào nội bộ JDK chỉ để giải phóng mapping đúng một thời điểm; cần lựa chọn API và kiến trúc tài nguyên phù hợp thay thế nếu vòng đời xác định là yêu cầu cứng.

## 9. Một số ngộ nhận quan trọng

| Ngộ nhận | Thực tế và cách xử lý |
| --- | --- |
| “Ra khỏi `try(FileChannel ...) `là mapping hết hiệu lực.” | Channel đã đóng, nhưng `MappedByteBuffer` vẫn có thể truy cập vùng ánh xạ; ví dụ trên minh họa trực tiếp. |
| “`MappedByteBuffer.clear()` giải phóng file mapping.” | `clear()` chỉ reset position và limit, không unmap. |
| “Đóng channel luôn làm byte sửa qua mapping bền vững trên đĩa.” | Độ bền dữ liệu là vấn đề riêng; dùng `MappedByteBuffer.force()` khi cần theo hợp đồng áp dụng. |
| “Đóng channel xong luôn xóa/đổi tên file được.” | Mapping còn sống có thể cản thao tác trên một số OS; phải kiểm tra và xử lý lỗi. |
| “Gọi `System.gc()` sẽ unmap ngay.” | Không có bảo đảm thu hồi ngay; không dựa vào GC làm giao thức đóng tài nguyên xác định. |
| “Một mapping 2 GiB đồng nghĩa đã dùng 2 GiB RAM vật lý.” | Dung lượng vùng địa chỉ ánh xạ không đồng nghĩa với RSS/page cache thực đang cư trú. |
| “`MappedByteBuffer.force()` làm mapping kết thúc.” | `force()` yêu cầu ghi dữ liệu đã sửa, không thu hồi vùng ánh xạ. |
| “Có thể truncate file dưới một mapping mà mọi byte buffer vẫn đọc được như cũ.” | Truy cập vùng không còn hợp lệ có thể thất bại bằng ngoại lệ không được chỉ định cụ thể. |
| “Private mapping lưu thay đổi vào file khi force.” | `MapMode.PRIVATE` dùng copy-on-write riêng, không có ngữ nghĩa persist thay đổi vào file gốc. |

## 10. Cách tự kiểm tra vòng đời

Có thể mở rộng chương trình `MappedLifetimeDemo` để kiểm chứng các giả định theo cách có kiểm soát:

1. In `channel.isOpen()` trong khối `try`, rồi **sau khi ra khỏi khối** đọc buffer. Quan sát channel kết thúc vòng đời trước nhưng buffer vẫn truy cập được.
2. Đổi `READ_WRITE` thành `READ_ONLY` và chỉ thực hiện `get()`; thử `put()` riêng để quan sát `ReadOnlyBufferException`.
3. Tạo một view `slice()` rồi sửa dữ liệu qua view trong phạm vi hợp lệ; chú ý view chia sẻ nội dung và có thể kéo dài vòng đời vùng ánh xạ.
4. Chạy thử thao tác xóa/đổi tên **trên tệp tạm riêng** khi mapping còn sống để quan sát hành vi hệ điều hành; xử lý `IOException` thay vì giả định kết quả giống nhau mọi nơi. Không thử trên dữ liệu đang dùng thật.
5. Theo dõi chỉ số memory pool `mapped` và OS process memory khi tạo/giải phóng tham chiếu nhiều mapping; không dùng một lần gọi GC để suy ra thời điểm unmap được bảo đảm.

**Ghi nhớ:** `FileChannel.map()` tạo một vùng ánh xạ có vòng đời độc lập với `FileChannel` dùng để tạo ra nó. `try-with-resources` đóng channel đúng cách nhưng **không tự đóng `MappedByteBuffer`**, không buộc unmap tức thì và không tự bảo đảm có thể xóa/di chuyển file hoặc ghi bền vững mọi sửa đổi. Thiết kế cần quản lý riêng thời gian sử dụng mapping, chính sách đồng bộ dữ liệu và hoạt động bảo trì tệp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="memory-mapping-008">Giới hạn giải phóng mapping theo phiên bản API và JVM</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn giải phóng memory mapping theo phiên bản API và JVM

## 1. Vấn đề thực tế: đóng tệp rồi, mapping đã được giải phóng chưa?

Khi dùng **memory-mapped file**, Java ánh xạ một vùng nội dung của tệp vào vùng địa chỉ bộ nhớ ảo của tiến trình. Chương trình có thể truy cập vùng đó thông qua `MappedByteBuffer` hoặc, trên JDK mới, `MemorySegment`. Cơ chế này thường tránh phải tự sao chép dữ liệu bằng vòng lặp `FileChannel.read/write` ở tầng ứng dụng, nhưng kéo theo câu hỏi về **vòng đời của tài nguyên native**: khi nào hệ điều hành thôi giữ vùng ánh xạ?

Một nhầm lẫn phổ biến:

```java
MappedByteBuffer mapped = channel.map(
    FileChannel.MapMode.READ_WRITE, 0, length);
channel.close();
// Không thể suy ra vùng mapping đã được unmap.
```

Đây là đoạn minh họa, giả sử `channel` và `length` đã được khai báo. `close()` giải phóng tài nguyên của **channel**, còn mapping đã tạo có vòng đời riêng. Với API truyền thống trả về `MappedByteBuffer`, **đóng channel không hủy mapping**. Mapping có thể tiếp tục hợp lệ khi buffer vẫn còn được dùng.

Hợp đồng API Java SE 17 nói mapping của một `MappedByteBuffer` còn hợp lệ cho đến khi buffer được garbage-collected. Nhưng **GC xảy ra lúc nào không được bảo đảm**; Java SE 17 cũng không công bố phương thức `unmap()`/`close()` chuẩn trên `MappedByteBuffer`. Đây là giới hạn trọng tâm cần hiểu khi thiết kế chương trình cần giải phóng hoặc thay thế tệp ngay lập tức.

## 2. Phân biệt bốn thao tác thường bị nhầm

| Thao tác | Điều nó thực sự làm | Có bảo đảm unmap không? |
| --- | --- | --- |
| `FileChannel.close()` | Đóng kênh và tài nguyên gắn với channel | **Không** đối với mapping `MappedByteBuffer` đã tạo |
| `mapped.force()` | Yêu cầu đồng bộ thay đổi ở mapping đọc/ghi tới thiết bị lưu trữ theo hợp đồng API | **Không** |
| `mapped.clear()` | Đặt lại `position=0`, `limit=capacity`, xóa mark | **Không**; không xóa byte |
| Đối tượng mapping không còn reachable và GC thu gom | Có thể dẫn đến giải phóng tài nguyên mapping của buffer cũ | **Không có thời điểm xác định** |

Cần tách các khái niệm:

- **Flush/persistence**: dữ liệu thay đổi được đẩy xuống thiết bị lưu trữ hay chưa.
- **Unmap/release**: vùng ánh xạ bộ nhớ không còn được tiến trình sử dụng.
- **Close channel**: tài nguyên kênh được đóng.
- **Delete/rename/truncate**: thao tác hệ thống tệp; hành vi với vùng mapping đang sống phụ thuộc nền tảng.

`force()` phục vụ độ bền dữ liệu, **không phải giải phóng vùng ánh xạ**. `load()` chỉ cố gắng nạp trang vào RAM; `isLoaded()` chỉ gợi ý mức resident; chúng cũng không phải API unmap. `System.gc()` chỉ yêu cầu JVM cân nhắc chạy GC, không tạo cam kết giải phóng ngay.

## 3. API truyền thống: `FileChannel.map(mode, position, size)`

Từ những phiên bản Java cũ và vẫn tồn tại trong Java 17/21/22+, `FileChannel` có phương thức:

```java
MappedByteBuffer map(
        FileChannel.MapMode mode,
        long position,
        long size) throws IOException;
```

Kết quả là `java.nio.MappedByteBuffer`, một loại `ByteBuffer` trực tiếp đại diện cho vùng ánh xạ tệp.

**Các điểm thuộc hợp đồng quan trọng:**

1. Mapping có thể vẫn truy cập được **sau khi `FileChannel` đã đóng**.
2. Thời gian mapping được giải phóng gắn với việc buffer trở thành đối tượng có thể được GC thu gom; ứng dụng **không điều khiển chính xác thời điểm GC**.
3. `MappedByteBuffer` không triển khai `AutoCloseable` và không có phương thức `close()` hay `unmap()` chuẩn công khai.
4. Các view như `slice()` hoặc `duplicate()` có thể cùng chia sẻ vùng nội dung. Không giả định gán `mapped = null` là đủ nếu còn view hoặc tham chiếu liên quan đang sống.
5. Tài nguyên native và địa chỉ ảo có thể tiếp tục được giữ dù lượng bộ nhớ heap nhìn thấy không lớn. Dựa riêng vào `Runtime.freeMemory()` để suy đoán mapping đã được tháo là sai.

Theo API, kích thước của mapping trả về `MappedByteBuffer` bị giới hạn bởi hệ số chỉ số `int` của buffer (vùng map không được lớn hơn `Integer.MAX_VALUE` byte). Tệp lớn hơn có thể được chia thành nhiều mapping nhỏ theo thiết kế; mỗi mapping lại cần quản lý vòng đời. **Giảm số lượng map hoặc chuyển sang I/O đọc/ghi thông thường** có thể hữu ích khi khối lượng tạo/hủy mapping liên tục gây khó quản lý.

## 4. Ví dụ Java 17+: mapping vẫn dùng được sau khi channel đóng

Chương trình này tạo một tệp tạm 4 byte, ánh xạ với chế độ `READ_WRITE`, đóng channel rồi tiếp tục đọc và sửa qua `MappedByteBuffer`. Tệp được đăng ký xóa khi JVM kết thúc; đây là **dọn dẹp tệp ở lúc thoát chương trình**, không phải thao tác unmap.

**Tệp `LegacyMappingDemo.java`:**

```java
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class LegacyMappingDemo {
    public static void main(String[] args) throws IOException {
        Path path = Files.createTempFile("legacy-map-", ".bin");
        path.toFile().deleteOnExit();
        Files.write(path, new byte[] {10, 20, 30, 40});

        MappedByteBuffer mapped;
        try (FileChannel channel = FileChannel.open(
                path,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE)) {
            mapped = channel.map(
                    FileChannel.MapMode.READ_WRITE, 0, 4);
            System.out.println("Trong khoi mo: " + mapped.get(0));
        }

        // FileChannel đã đóng, mapping vẫn có thể được truy cập.
        System.out.println("Sau close channel: " + mapped.get(1));
        mapped.put(2, (byte) 99);
        mapped.force();

        System.out.println("Byte vua sua: " + mapped.get(2));
        System.out.println("Size: " + Files.size(path));
    }
}
```

Biên dịch và chạy bằng **JDK 17+**:

```bash
javac LegacyMappingDemo.java
java LegacyMappingDemo
```

**Stdout trong điều kiện I/O thành công:**

```text
Trong khoi mo: 10
Sau close channel: 20
Byte vua sua: 99
Size: 4
```

Ví dụ chứng minh **việc đóng channel không làm `mapped` mất hiệu lực**. Nó **không đo thời điểm unmap** và không dùng `System.gc()` để khẳng định tài nguyên được thu hồi tức thì. `deleteOnExit()` chỉ đăng ký một lần xóa khi kết thúc JVM, nên thành công thực tế còn phụ thuộc hệ thống tệp và trạng thái tài nguyên; không sử dụng mẹo này làm chính sách dọn dẹp dài hạn cho dịch vụ chạy liên tục.

Với Windows, một mapping đang tồn tại có thể cản trở xóa/đổi tên tệp trong những tình huống nhất định; một số hệ thống Unix có thể cho phép unlink tên tệp trong khi mapping còn sống. Không viết code phụ thuộc giả định “xóa được trên máy phát triển thì luôn xóa được trên mọi OS”.

## 5. Theo phiên bản Java: khi nào có quyền kiểm soát vòng đời rõ ràng?

Bảng dưới đây phân biệt **API chuẩn hiện có**, không đồng nhất mọi JDK vendor và mọi provider:

| Phiên bản | Cách tạo mapping chuẩn chính | Kiểm soát thời điểm giải phóng |
| --- | --- | --- |
| Java 8, 11, 17, 21 | `FileChannel.map(MapMode, long, long)` → `MappedByteBuffer` | Theo khả năng tiếp cận/GC; không có `MappedByteBuffer.close()` chuẩn |
| Java 22 trở đi | Vẫn có overload trả `MappedByteBuffer` | Mapping kiểu cũ **vẫn** có giới hạn vòng đời GC |
| Java 22 trở đi | Thêm `FileChannel.map(MapMode, long, long, Arena)` → `MemorySegment` | Có thể gắn vào **arena đóng được**; đóng arena giải phóng mapping của segment theo hợp đồng |

**Mốc Java 22** quan trọng vì Foreign Function & Memory API (FFM API, `java.lang.foreign`) được chuẩn hóa cùng overload `FileChannel.map(..., Arena)`. Điều này **không biến mọi `MappedByteBuffer` cũ thành đối tượng có `close()`**. Muốn dùng vòng đời arena, chương trình phải chủ động chọn API trả về `MemorySegment` và triển khai trên runtime/provider hỗ trợ nó.

Đặc tả của overload mới ghi rằng **triển khai mặc định có thể ném `UnsupportedOperationException`**. Trên JDK thông dụng cung cấp triển khai thực tế, tính năng mapping qua arena được hỗ trợ, nhưng nếu ứng dụng dùng một nhà cung cấp `FileChannel` tùy chỉnh, vẫn cần xét khả năng không hỗ trợ.

Không nên khẳng định rằng “mọi Java 22+ đều hỗ trợ cách gọi đó trên mọi file provider” hoặc “nâng version tự động sửa việc unmap của code cũ”.

## 6. API từ Java 22: `MemorySegment` và `Arena`

Overload mới:

```java
MemorySegment map(
        FileChannel.MapMode mode,
        long offset,
        long size,
        Arena arena) throws IOException;
```

- `MemorySegment` đại diện một vùng bộ nhớ có ranh giới rõ ràng.
- `Arena` xác định vòng đời và quy tắc truy cập của các segment gắn với nó.
- Với một **arena đóng được**, mapping được unmap khi arena đó được **đóng thành công** theo hợp đồng `FileChannel.map`.
- Các thao tác truy cập segment sau khi arena đóng bị từ chối, thường bằng `IllegalStateException`; đây là một cơ chế kiểm soát *temporal safety*.
- Với `Arena.ofConfined()`, chỉ luồng sở hữu được sử dụng/đóng vùng đó; truy cập sai luồng có thể gây `WrongThreadException`.
- `Arena.ofShared()` phù hợp khi nhiều luồng cần truy cập nhưng vẫn có vòng đời đóng tường minh.

Các arena có khác biệt quan trọng:

| Arena | Đóng tường minh? | Có thể sử dụng từ nhiều luồng? | Vòng đời |
| --- | --- | --- | --- |
| `Arena.ofConfined()` | Có | Không | Kết thúc khi đóng bởi luồng sở hữu |
| `Arena.ofShared()` | Có | Có | Kết thúc khi đóng, cần phối hợp tác vụ |
| `Arena.ofAuto()` | Không | Có | Tự động thu hồi khi JVM xác định arena/segment không còn sống; không có thời điểm chắc chắn |
| `Arena.global()` | Không | Có | Vòng đời toàn cục; không dùng để lập lịch unmap tường minh |

**Muốn có thời điểm giải phóng do mã ứng dụng quản lý, hãy chọn arena có thể đóng.** Dùng `Arena.ofAuto()` vẫn chịu tính không xác định của quản lý bộ nhớ tự động; dùng `Arena.global()` lại càng không phù hợp nếu cần giải phóng mapping theo từng request.

`Arena.close()` không idempotent: đóng hai lần có thể gây `IllegalStateException`. Cơ chế `try-with-resources` giúp tránh việc gọi đóng lặp và bảo đảm đóng khi rời scope, kể cả lúc có ngoại lệ, miễn hoạt động đóng diễn ra thành công.

## 7. Ví dụ Java 22+: giải phóng mapping bằng cách đóng arena

Chương trình sau sử dụng API chính thức của Java 22 trở lên. Tệp được ánh xạ vào một `MemorySegment` thuộc `Arena.ofConfined()`. Sau khi kết thúc `try-with-resources`, arena đóng, mapping được giải phóng theo hợp đồng API và truy cập lại segment bị từ chối.

**Tệp `ArenaMappingDemo.java`:**

```java
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ArenaMappingDemo {
    public static void main(String[] args) throws IOException {
        Path path = Files.createTempFile("arena-map-", ".bin");
        Files.write(path, new byte[] {1, 2, 3, 4});

        MemorySegment segment;
        try (Arena arena = Arena.ofConfined();
             FileChannel channel = FileChannel.open(
                     path,
                     StandardOpenOption.READ,
                     StandardOpenOption.WRITE)) {

            segment = channel.map(
                    FileChannel.MapMode.READ_WRITE, 0, 4, arena);

            System.out.println(
                    "Truoc close: " + segment.get(ValueLayout.JAVA_BYTE, 0));
            segment.set(ValueLayout.JAVA_BYTE, 1, (byte) 42);
            segment.force();
        } // Đóng channel, sau đó đóng arena => mapping được unmap.

        try {
            segment.get(ValueLayout.JAVA_BYTE, 0);
            System.out.println("Truy cap van thanh cong");
        } catch (IllegalStateException ex) {
            System.out.println("Segment khong con hieu luc");
        }

        byte[] contents = Files.readAllBytes(path);
        System.out.println("Byte tai 1: " + contents[1]);
        Files.delete(path);
    }
}
```

Dùng **JDK 22 trở lên có hỗ trợ overload mapping trên file provider đang dùng**:

```bash
javac ArenaMappingDemo.java
java ArenaMappingDemo
```

**Stdout khi I/O và giải phóng mapping thành công:**

```text
Truoc close: 1
Segment khong con hieu luc
Byte tai 1: 42
```

**Vì sao kết quả này khác ví dụ Java 17?**

1. `segment` được tạo bằng overload `map(..., arena)`, không phải overload trả `MappedByteBuffer`.
2. Trong scope của `arena`, segment hợp lệ và cho phép đọc/ghi byte.
3. `segment.force()` được dùng để đồng bộ thay đổi trên mapping `READ_WRITE`; nó **không** thay `arena.close()`.
4. Khi thoát `try`, `channel.close()` diễn ra trước `arena.close()` do thứ tự khai báo tài nguyên đảo ngược khi đóng.
5. Đóng arena thành công kết thúc vòng đời segment; `segment.get` sau đó phát sinh `IllegalStateException`, được bắt để in thông báo.
6. Tệp còn tồn tại và chứa byte 42 đã ghi; việc giải phóng mapping không đồng nghĩa tự xóa tệp. Chương trình tự xóa tệp ở cuối.

**Giới hạn:** nếu file provider không hỗ trợ overload, có thể gặp `UnsupportedOperationException`. Nếu có lỗi quyền, không gian đĩa, I/O hoặc đóng arena thất bại, chương trình có thể không in toàn bộ stdout ví dụ. Đây là ví dụ dùng một mapping trong một luồng; với code nhiều luồng hãy lựa chọn arena và thiết kế bàn giao/truy cập đúng quy tắc.

## 8. MappedByteBuffer và MemorySegment: hai mô hình quản lý tài nguyên

| Khía cạnh | Legacy `MappedByteBuffer` | `MemorySegment` với arena đóng được |
| --- | --- | --- |
| API tạo | `map(mode, offset, size)` | `map(mode, offset, size, arena)` |
| Phiên bản chính thức | Có từ lâu, dùng được Java 17 | Java 22+ |
| Điều khiển vòng đời | GC của buffer | `arena.close()` |
| Có `close()` chuẩn trực tiếp trên kết quả map? | Không | Không đóng trực tiếp segment; đóng **arena** |
| Khi đóng `FileChannel` | Mapping vẫn hợp lệ | Không thay thế thao tác đóng arena |
| Khi muốn giải phóng đúng lúc | Không có API public chuẩn trực tiếp | Gói mapping trong `try (Arena arena = ...)` |
| Truy cập sau khi tài nguyên đã giải phóng | Phụ thuộc trạng thái mapping/GC, không thiết kế để tự unmap | Segment bị từ chối sau khi arena đóng |
| Hỗ trợ nhiều mapping lớn | Bị giới hạn bởi `ByteBuffer` có chỉ số `int` | `MemorySegment` dùng kích thước `long`, tùy giới hạn thực tế |

Lưu ý `MemorySegment.asByteBuffer()` chỉ tạo view ByteBuffer khi độ dài nằm trong giới hạn `int`. View này **không thoát khỏi vòng đời của arena**: truy cập buffer-view sau khi arena đóng cũng không được phép. Điều đó khác với kết luận sai rằng “bọc segment thành ByteBuffer sẽ tự kéo dài tuổi thọ mapping”.

Mô hình mới cung cấp cách quản lý tài nguyên dễ dự đoán hơn, nhưng vẫn phải tuân theo quyền truy cập, tính nhất quán dữ liệu và giới hạn hệ điều hành. Không áp dụng mặc định rằng một mapped segment có thể dùng tự do xuyên thread nếu đang ở confined arena.

## 9. Những giới hạn vẫn tồn tại trên mọi phiên bản

### 9.1. File có thể bị sửa, rút ngắn hoặc thay thế từ nơi khác

Java không bảo đảm hành vi nhất quán nếu file bị **truncate** hoặc thay đổi kích thước trong khi mapping đang được dùng. Theo tài liệu `MappedByteBuffer` và `MemorySegment`, toàn bộ hoặc một phần vùng ánh xạ có thể trở nên không truy cập được; lỗi được ném khi truy cập có thể **không được chỉ rõ chính xác** theo hệ điều hành.

Vì vậy, đừng sử dụng việc đóng/mở arena để suy ra ứng dụng có thể tùy ý truncate một file đang được người khác map. Cần phối hợp các writer/reader hoặc thiết kế snapshot/versioning, tránh thao tác rút ngắn file khi đang còn các mapping sống.

### 9.2. Không đồng nhất giải phóng mapping với bảo đảm ghi bền vững

Khi mapping `READ_WRITE`, thay đổi có thể được đồng bộ ra file theo cơ chế hệ điều hành. Nếu nghiệp vụ cần đẩy thay đổi tới thiết bị lưu trữ trước khi xác nhận thành công, dùng `MappedByteBuffer.force()` hoặc `MemorySegment.force()` khi phù hợp.

- `force()` là thao tác liên quan **persistence**.
- `close()` trên arena là thao tác liên quan **vòng đời mapping**.
- `FileChannel.force(boolean)` liên quan dữ liệu/metadata của **channel**, không có vai trò là nút unmap.

Độ bền dữ liệu trên thiết bị từ xa hoặc file system đặc biệt còn có giới hạn theo đặc tả. Không mặc định việc unmap tương đương với giao dịch ghi dữ liệu nguyên tử.

### 9.3. Xóa/đổi tên file khi đang map khác nhau giữa OS

Một số OS cho phép đổi tên hoặc unlink file khi tiến trình còn giữ mapping; nơi khác có thể trả về lỗi “file in use” hoặc chặn xóa trong suốt thời gian mapping tồn tại. Java NIO **không cung cấp bảo đảm đồng nhất** cho mọi nền tảng trong tình huống này.

Nếu ứng dụng xử lý file tạm, file segment hoặc log xoay vòng và cần xóa ngay, hãy lựa chọn API với vòng đời rõ ràng (JDK 22+), hoặc nếu phải dùng Java 17, cân nhắc cách tổ chức **không phụ thuộc unmap ngay lập tức**, như đọc/ghi qua `FileChannel` thông thường, chia vòng đời tác vụ, hoặc trì hoãn cleanup dựa trên chính sách phù hợp.

### 9.4. Off-heap, địa chỉ ảo và giới hạn ánh xạ

Mapping không giống như cấp phát một `byte[]` lớn trên Java heap. Một lượng dữ liệu map lớn có thể tiêu thụ **không gian địa chỉ ảo** và các tài nguyên quản lý trang mà metric heap không phản ánh đầy đủ. Không kết luận mapping đã được giải phóng chỉ vì heap sử dụng ít.

Giới hạn còn phụ thuộc kiến trúc 32/64 bit, file descriptor, số vùng mmap, giới hạn địa chỉ, tình trạng tệp và khả năng của filesystem. Khi ứng dụng tạo mapping liên tục, theo dõi số mapping/vòng đời, quan sát tài nguyên tiến trình bằng công cụ phù hợp và đo đạc trên hệ điều hành triển khai thực tế.

### 9.5. Đóng arena cũng cần xử lý tác vụ đang chạy

Với `Arena.ofShared()`, nhiều thread có thể cùng truy cập vùng nhớ được map. Việc một thread cố đóng arena trong khi thread khác đang sử dụng có thể dẫn tới ngoại lệ khi đóng hoặc khi truy cập theo hợp đồng FFM. Phải hoàn tất/hủy tác vụ sử dụng và điều phối vòng đời trước khi đóng.

Với `Arena.ofConfined()`, chỉ thread sở hữu được phép truy cập/đóng. Đưa segment sang thread khác rồi gọi trực tiếp `get` hoặc `set` vi phạm giới hạn thread confinement. Nếu API yêu cầu giao dữ liệu giữa các thread, dùng shared arena hoặc sao chép dữ liệu sang cấu trúc riêng theo thiết kế.

## 10. Những “mẹo unmap” cần xem xét cẩn thận

### 10.1. Dùng `System.gc()` để buộc unmap

```java
mapped = null;
System.gc();
```

Đây **không phải giải pháp xác định**. `mapped = null` chỉ bỏ một tham chiếu cụ thể; còn các view hoặc tham chiếu khác có thể giữ tài nguyên sống. `System.gc()` chỉ là yêu cầu, có thể bị JVM bỏ qua hoặc thực thi vào lúc khác. Không thể chứng minh mapping đã unmap chỉ từ việc lời gọi `System.gc()` kết thúc.

### 10.2. Dùng `sun.misc.Unsafe.invokeCleaner` hoặc reflection nội bộ

Một số mã cũ sử dụng API nội bộ để kích hoạt cleaner trên direct buffer. Cách này **không phải giải pháp portable theo Java SE**: phụ thuộc implementation, module encapsulation, quyền truy cập, công cụ build và trạng thái buffer/view; có thể hỏng khi nâng JDK. Việc cố tình bỏ qua kiểm soát của JVM còn có nguy cơ use-after-unmap nếu tài nguyên vẫn đang được dùng.

Không nên đưa thủ thuật này thành hướng dẫn mặc định cho thư viện dùng trên nhiều vendor/JVM. Khi buộc phải duy trì hệ thống cũ, cần thẩm định riêng cho runtime cụ thể, quản lý quyền sở hữu và kiểm thử đầy đủ; ưu tiên API chuẩn có vòng đời rõ ràng khi có thể nâng cấp.

### 10.3. Nghĩ đóng channel hay gọi `clear` đủ để xóa file ngay

`close()` của channel không giải phóng mapping legacy; `clear()` của buffer chỉ chỉnh `position/limit`; `force()` đồng bộ dữ liệu. Ba thao tác phục vụ ba mục đích khác nhau. Lỗi thiết kế thường xuất hiện khi ứng dụng map file, đóng channel rồi lập tức gọi `Files.delete(path)` và cho rằng thao tác phải thành công trên mọi OS.

## 11. Chọn giải pháp cho ứng dụng thực tế

| Nhu cầu | Hướng triển khai |
| --- | --- |
| Dùng Java 17 và map file lớn, đọc nhiều lần | Giới hạn số mapping sống, kiểm soát scope tham chiếu; chấp nhận việc giải phóng theo GC hoặc chọn I/O không map |
| Java 17 cần xóa/đổi tên file temp ngay sau request | Tránh phụ thuộc vào thời điểm GC; cân nhắc `FileChannel.read/write` với `ByteBuffer` nếu phù hợp |
| Java 22+ cần unmap theo cuối phạm vi xử lý | `FileChannel.map(..., Arena.ofConfined())` với `try-with-resources`, nếu chỉ một thread sử dụng |
| Java 22+ chia mapping giữa nhiều thread | `Arena.ofShared()` và phối hợp đóng sau khi mọi tác vụ hoàn tất |
| Không cần giải phóng tường minh, chấp nhận vòng đời tự động | `Arena.ofAuto()` có thể phù hợp; không có thời điểm unmap được bảo đảm |
| Cần bảo đảm thay đổi xuống thiết bị lưu trữ | Dùng `force()` phù hợp trước khi tuyên bố hoàn tất |
| Cần tương thích nhiều phiên bản JDK | Chọn baseline build/runtime rõ ràng, kiểm tra overload JDK 22 và provider, thử nghiệm trên OS thật |

Nếu mục tiêu chỉ đọc tuần tự một file không quá lớn, mapping không nhất thiết tối ưu hơn mọi giải pháp còn lại. Thiết kế cần cân nhắc độ lớn tệp, tần suất tái sử dụng, yêu cầu thay thế/xóa file, giới hạn native resource và vòng đời dịch vụ.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** Java 17: đã gọi `FileChannel.close()` sau khi map. Có thể lập tức `Files.delete(path)` và tin chắc thành công trên mọi OS không?

**Không.** Mapping `MappedByteBuffer` vẫn có thể sống sau khi channel đóng, và hành vi delete/rename với file đang được map phụ thuộc OS.

**Câu 2.** Gọi `mapped.force()` có làm memory mapping bị tháo không?

**Không.** `force()` là yêu cầu đồng bộ dữ liệu xuống thiết bị lưu trữ, không phải unmap.

**Câu 3.** Java 22+: có `FileChannel.map(..., Arena)`, vậy mọi chương trình `MappedByteBuffer` cũ đều được unmap khi đóng channel?

**Không.** API cũ vẫn tạo mapping có vòng đời gắn với GC. Chỉ phương thức map trả `MemorySegment` gắn vào **arena đóng được** mới có hợp đồng đóng arena để unmap theo scope.

**Câu 4.** Dùng `Arena.ofAuto()` có thể gọi `close()` để buộc unmap ngay khi request hoàn tất không?

**Không.** Arena automatic không đóng tường minh được theo mô hình API. Chọn confined/shared arena nếu cần chính sách đóng đúng thời điểm.

**Câu 5.** Sau khi đóng `Arena.ofConfined()`, có nên giữ `MemorySegment` rồi tiếp tục `get` nó không?

**Không.** Scope không còn sống; thao tác truy cập bị từ chối bằng ngoại lệ, thường `IllegalStateException`. Đóng arena thể hiện **kết thúc quyền truy cập** vùng mapping.

## 13. Ghi nhớ và tài liệu chuẩn

- `MappedByteBuffer` từ `FileChannel.map(mode, position, size)` **không có unmap/close chuẩn công khai**; vòng đời mapping gắn với GC và không có thời điểm giải phóng chắc chắn.
- `FileChannel.close()`, `MappedByteBuffer.clear()` và `force()` **không phải thao tác unmap**.
- **Java 22+** có overload `FileChannel.map(mode, offset, size, arena)` trả `MemorySegment`. Với arena đóng được, **đóng arena thành công sẽ unmap** vùng gắn với nó.
- Chọn `Arena.ofConfined()` cho một thread, `Arena.ofShared()` khi cần nhiều thread; `ofAuto()`/`global()` không cung cấp lịch unmap tường minh.
- Tính khả dụng sau truncate, thời điểm ghi xuống thiết bị, hành vi xóa/đổi tên và khả năng đồng thời đều có yếu tố phụ thuộc hệ điều hành/JVM/provider.

**Tài liệu chính thức để đối chiếu phiên bản:**

1. [MappedByteBuffer — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/MappedByteBuffer.html).
2. [FileChannel — Java SE 22](https://docs.oracle.com/en/java/javase/22/docs/api/java.base/java/nio/channels/FileChannel.html), đặc biệt overload `map(..., Arena)` và phần `Since: 22`.
3. [Arena — Java SE 22](https://docs.oracle.com/en/java/javase/22/docs/api/java.base/java/lang/foreign/Arena.html), các loại arena và quy tắc đóng scope.
4. [MemorySegment — Java SE 22](https://docs.oracle.com/en/java/javase/22/docs/api/java.base/java/lang/foreign/MemorySegment.html), truy cập mapping, `force()` và vòng đời segment.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="memory-mapping-009">Rủi ro khi tệp bị thay đổi kích thước trong lúc đang ánh xạ</a>

<details>
<summary>Xem chi tiết</summary>

# Rủi ro khi tệp bị thay đổi kích thước trong lúc đang ánh xạ bằng Java NIO

## 1. Bối cảnh: ánh xạ tệp và kích thước tệp là hai trạng thái khác nhau

Trong Java NIO, `FileChannel.map(...)` cho phép ánh xạ một vùng của tệp vào không gian địa chỉ bộ nhớ và trả về `MappedByteBuffer`. Sau đó chương trình đọc hoặc ghi bằng các phương thức của `ByteBuffer` thay vì liên tục chuyển dữ liệu qua `FileChannel.read(...)` hoặc `write(...)`.

Ví dụ:

~~~java
try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
    MappedByteBuffer mapped = channel.map(
            FileChannel.MapMode.READ_ONLY, 0, 8);
    byte firstByte = mapped.get(0);
}
~~~

Đây là **mảnh mã minh họa**: biến `path` và các import cần được định nghĩa, tệp phải có ít nhất 8 byte khi lập mapping và môi trường phải hỗ trợ thao tác này.

Có ít nhất ba đại lượng cần phân biệt:

- **Kích thước tệp hiện tại** (`channel.size()` hoặc `Files.size(path)`): có thể thay đổi do ghi nối, truncate hoặc tác nhân khác.
- **Phạm vi mapping**: được xác định tại lúc gọi `map(mode, position, size)`, ví dụ offset tệp từ 0 đến trước byte số 8. Nó không tự tăng/giảm đồng bộ với kích thước tệp.
- **Trạng thái của `MappedByteBuffer`**: `position`, `limit` và `capacity` là trạng thái của buffer; `capacity` cố định theo kích thước mapping, không phải một biến luôn phản ánh kích thước tệp.

Sơ đồ khi ban đầu tệp có 8 byte:

~~~text
Tệp ban đầu, size = 8:
offset:     0   1   2   3   4   5   6   7
         +---+---+---+---+---+---+---+---+
         | A | B | C | D | E | F | G | H |
         +---+---+---+---+---+---+---+---+
                    |  map(0, 8)  |
                    v             v
Mapping: +---+---+---+---+---+---+---+---+
         | A | B | C | D | E | F | G | H |
         +---+---+---+---+---+---+---+---+
         capacity = 8

Một tác nhân khác truncate tệp xuống size = 3:
         +---+---+---+
Tệp:     | A | B | C |
         +---+---+---+

Mapping cũ: vẫn biểu diễn vùng có capacity = 8,
             nhưng phần tương ứng byte 3..7 có thể
             không còn truy cập được. KHÔNG tự kiểm chứng
             điều này bằng cách đọc những byte đó.
~~~

**Điểm mấu chốt:** một mapping có thể còn tồn tại trong khi vùng tệp làm cơ sở cho nó đã biến đổi. Việc một thao tác Java vẫn cho phép chỉ số `get(7)` theo `limit` không chứng minh vị trí đó còn được hệ điều hành hỗ trợ bởi tệp hiện tại.

## 2. Hợp đồng Java 17: hành vi nào được bảo đảm?

Theo Javadoc của `FileChannel.map` và `MappedByteBuffer` trên Java 17:

1. Một vùng ánh xạ có thể trở nên **không truy cập được toàn bộ hoặc một phần vào bất kỳ thời điểm nào**, ví dụ khi tệp nền bị truncate.
2. Khi truy cập vùng không còn truy cập được, API **không bảo đảm một loại ngoại lệ xác định**; lỗi có thể được phát hiện ngay khi truy cập hoặc về sau. Vì vậy không thể viết một đoạn `catch` cụ thể rồi cho rằng đã an toàn trên mọi hệ điều hành.
3. Khi nội dung **hoặc kích thước** tệp thay đổi, việc những thay đổi đó có được phản ánh trong buffer đã map hay không và phản ánh vào lúc nào là **không được xác định thống nhất**.
4. Hành vi của `map` nếu vùng yêu cầu **không nằm trọn trong tệp** cũng không được bảo đảm đồng nhất theo Java 17.
5. Mapped buffer và channel có **vòng đời khác nhau**: đóng channel không nhất thiết hủy ngay mapping; mapping có thể tiếp tục tồn tại đến khi buffer được garbage collector thu hồi.

Do vậy, không được xem việc đọc thành công trên một hệ điều hành hoặc một JDK cụ thể là bằng chứng một thao tác cũng an toàn trên môi trường khác.

Tham khảo hợp đồng API:

- [FileChannel.map — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html#map(java.nio.channels.FileChannel.MapMode,long,long))
- [MappedByteBuffer — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/MappedByteBuffer.html)

## 3. Trường hợp nguy hiểm nhất: truncate nhỏ hơn vùng đã ánh xạ

Giả sử ứng dụng đã map vùng `[0, 4096)` của một tệp. Một luồng hoặc tiến trình khác gọi `FileChannel.truncate(1024)` làm tệp chỉ còn 1024 byte.

Về phía buffer, `capacity()` có thể vẫn là 4096 vì capacity được xác định tại thời điểm tạo. Nhưng khi ứng dụng tiếp tục đọc các index 1024 đến 4095, một phần của mapping có thể đã mất sự hỗ trợ từ tệp.

~~~text
Thời điểm T0:       file = 4096 byte; map(0, 4096)
Thời điểm T1:       ứng dụng đang xử lý index 0..1023
Thời điểm T2:       tiến trình khác truncate file còn 1024
Thời điểm T3:       ứng dụng cố đọc index 3000 -> nguy cơ
                    truy cập vùng mapping không khả dụng
~~~

Các hậu quả thực tế có thể là:

- Truy cập buffer gặp lỗi mà chương trình không thể dự đoán theo một exception type cố định.
- Tiến trình nhận lỗi từ cơ chế bảo vệ bộ nhớ/mapping của hệ điều hành; chi tiết phụ thuộc runtime và OS.
- Một quy trình đọc tuần tự đang chạy dở không thể chứng minh tính đúng đắn hoặc tính nhất quán của dữ liệu nữa.
- Tệp có thể bị thay thế hoặc các metadata đã dùng để xác nhận nội dung không còn tương ứng với đối tượng tệp đang được ứng dụng sử dụng.

**Không thử truy cập “byte đã mất” để chẩn đoán bằng mã production.** Mục đích của thiết kế an toàn là tránh tạo điều kiện truy cập vùng không hợp lệ, không phải phụ thuộc vào một loại lỗi sẽ được ném ra.

### 3.1. Ví dụ mã có rủi ro — không chạy như bài thực hành

~~~java
// MẢNH MINH HỌA RỦI RO: KHÔNG CHẠY để kiểm nghiệm lỗi.
MappedByteBuffer mapped = channel.map(
        FileChannel.MapMode.READ_ONLY, 0, 4096);

// Có thể xảy ra từ luồng hoặc tiến trình KHÁC:
otherChannel.truncate(1024);

// Không được suy ra get(3000) là an toàn từ capacity() == 4096.
byte value = mapped.get(3000);
~~~

Biến `channel`, `otherChannel` và đối tượng tệp không được khởi tạo trong đoạn này: đây là **mã minh họa tình huống nguy hiểm**, không phải ví dụ độc lập. Nó cho thấy lỗi thiết kế cần tránh, tuyệt đối không bảo đảm bất kỳ stdout/exception cụ thể nào.

## 4. Khi tệp tăng kích thước: mapping cũ không tự mở rộng

Trường hợp ngược lại: tệp ban đầu có 8 byte và chương trình map 8 byte. Sau đó tác nhân khác nối thêm 4 byte, tệp dài 12 byte.

~~~text
T0: file.size() = 8
    mapped.capacity() = 8

T1: append 4 byte -> file.size() = 12

T2: mapped.capacity() vẫn = 8
    index 8..11 không nằm trong không gian chỉ số của buffer cũ.
~~~

`MappedByteBuffer` không biến thành buffer có capacity 12 chỉ vì file đã dài hơn. Nếu ứng dụng muốn xử lý byte mới nằm ngoài khoảng đã map, cần **tạo một mapping phù hợp** (ví dụ mapping vùng mới hoặc ánh xạ lại trên instance/vùng khác) sau khi đã xác định bản dữ liệu hợp lệ.

Điều này khác với trường hợp truncate:

- **Tăng tệp**: phần byte thêm vào nằm ngoài ranh giới cũ; cố `mapped.get(8)` khi `limit=8` sẽ vi phạm chỉ số buffer thông thường.
- **Giảm tệp**: một chỉ số **vẫn có thể hợp lệ theo buffer** nhưng vùng tệp phía dưới không còn tồn tại, khiến rủi ro phức tạp hơn.

Không được giả định phép append sẽ an toàn khi đồng thời còn một writer có thể ghi đè/truncate phần cũ. Cần chính sách ownership và đồng bộ khi nhiều tác nhân cùng thay đổi một tệp.

## 5. Thay đổi nội dung và thay đổi kích thước đều tác động tới tính nhất quán

Một file giữ nguyên kích thước nhưng bị ghi đè nội dung vẫn có thể làm kết quả đọc bị trộn giữa nhiều phiên bản.

Giả sử bộ phân tích đọc header và payload:

~~~text
Phiên bản A:
[ header length=8 ][ payload của A ]

Writer cập nhật thành phiên bản B cùng kích thước:
[ header length=8 ][ payload của B ]

Reader đọc header từ A, rồi payload từ B
=> Kết quả có thể không tương ứng phiên bản hoàn chỉnh nào.
~~~

`READ_ONLY` chỉ từ chối ghi **qua mapped buffer đó**, không khóa tệp đối với các writer khác. `PRIVATE` cũng không phải snapshot cố định tuyệt đối trước các thay đổi từ bên ngoài. Trong cả ba mode, tính nhất quán của dữ liệu khi file bị sửa từ nguồn khác cần được giải quyết bằng thiết kế bổ sung.

Các thuộc tính như `FileChannel.size()`, `Files.getLastModifiedTime(...)` hay `BasicFileAttributes.fileKey()` có thể giúp phát hiện những thay đổi **quan sát được**, nhưng không chứng minh rằng dữ liệu chưa bị sửa giữa hai phép kiểm tra. Timestamp có độ phân giải khác nhau; fileKey có thể không được provider cung cấp; kích thước giữ nguyên không loại trừ ghi đè nội dung.

## 6. Ba MapMode có giúp bảo vệ khi tệp đổi kích thước không?

| MapMode | Hành vi ghi qua mapping | Có tự bảo vệ khỏi tệp bị truncate/resize bên ngoài không? |
| --- | --- | --- |
| `READ_ONLY` | `put` bị cấm, ném `ReadOnlyBufferException`. | **Không.** Bản thân vùng đã map vẫn có thể bị ảnh hưởng khi tệp nền thay đổi. |
| `READ_WRITE` | `put` có thể cập nhật tệp, `force()` hỗ trợ yêu cầu đồng bộ. | **Không.** Writer bên ngoài vẫn có thể truncate hoặc thay đổi phạm vi ánh xạ. |
| `PRIVATE` | Ghi bằng copy-on-write, không propagate thay đổi riêng về tệp. | **Không.** Nó không bảo đảm tạo snapshot bất biến trước mọi thay đổi kích thước ở tệp nền. |

Không cố “khắc phục truncate” bằng `mapped.force()`, `mapped.load()` hoặc `mapped.isLoaded()`:

- `force()` làm việc với yêu cầu đồng bộ thay đổi của READ_WRITE mapping; không sửa lại phạm vi tệp bị mất. Nó không có tác dụng commit với PRIVATE.
- `load()` cố nạp trang của mapping vào RAM. Nó không làm mapping độc lập khỏi tệp nền và không cấp quyền đọc một vùng đã trở nên không khả dụng.
- `isLoaded()` chỉ là một gợi ý về resident pages, không bảo đảm truy cập an toàn khi file bị resize.

Nguyên tắc: **MapMode quy định kiểu truy cập và truyền thay đổi**, chứ không phải hợp đồng khóa kích thước tệp.

## 7. Vì sao kiểm tra size trước mỗi get() vẫn chưa đủ?

Một cách sửa dễ nghĩ tới:

~~~java
long currentSize = channel.size();
if (targetOffset < currentSize) {
    return mapped.get(index);
}
~~~

Đây là **mảnh minh họa thiếu an toàn**, với `targetOffset`, `index` và `mapped` được khai báo ở nơi khác. Kể cả khi phép kiểm tra đúng tại thời điểm đầu, một writer khác vẫn có thể rút ngắn tệp **sau khi kiểm tra nhưng trước khi `get()`**.

Đó là lỗi **TOCTOU** (*time-of-check to time-of-use*):

~~~text
Reader                         Writer
  |                              |
  +-- size() => 4096             |
  |                              +-- truncate(1024)
  +-- mapped.get(3000)           |
         có thể không an toàn    |
~~~

Vì thế:

- Kiểm tra `size` và ranh giới `offset + length` là **cần thiết** khi lập mapping, nhưng không đủ trong môi trường nhiều writer không có phối hợp.
- Kiểm tra metadata lặp lại sau từng record có thể giúp phát hiện sự thay đổi, nhưng không loại bỏ khoảng chạy đua ở giữa các thao tác.
- Một `synchronized` hoặc `ReentrantReadWriteLock` trong **một JVM** chỉ bảo vệ các luồng thực sự tham gia cùng cơ chế khóa; tiến trình khác vẫn có thể sửa tệp.
- `FileLock` có thể hữu ích khi mọi tác nhân tuân thủ chính sách khóa, nhưng tính hiệu lực trên các hệ điều hành và filesystem khác nhau; không coi nó là lời giải phổ quát chống resize khi mmap.

## 8. Giải pháp 1: sở hữu độc quyền vòng đời và kích thước tệp

Khi chương trình tự tạo và kiểm soát file, cách đơn giản nhất là **không cho thay đổi kích thước trong suốt thời gian còn mapping đang được sử dụng**.

Một quy trình có trách nhiệm:

1. Ghi xong dữ liệu và xác lập kích thước file.
2. Công bố file cho reader khi dữ liệu đã hoàn chỉnh.
3. Mỗi reader mở mapping trên file ổn định, xử lý và ngừng tham chiếu đến mapping sau khi hoàn tất.
4. Nếu cần phát hành phiên bản mới, ghi ra file **mới** với tên/version riêng; không truncate bản đang được reader sử dụng.
5. Thu hồi file cũ khi không còn reader và khi OS cho phép giải phóng tài nguyên liên quan.

Nếu có thể kiểm soát mọi writer, đây là nền tảng giảm rủi ro tốt hơn việc cố bắt ngoại lệ phát sinh từ truy cập vùng đã mất.

### 8.1. Không thay thế trực tiếp file reader đang map

Một lựa chọn triển khai là file phiên bản hóa:

~~~text
data-v001.bin     <- reader A đã map, giữ nguyên
data-v002.bin     <- writer tạo bản mới hoàn chỉnh
current.version   <- metadata chỉ tới phiên bản mới

Reader B mở data-v002.bin.
Reader A hoàn tất trên data-v001.bin.
Sau khi tất cả reader cũ ngừng dùng, mới dọn data-v001.bin.
~~~

Cần bảo đảm cập nhật metadata/version theo phương thức atomic phù hợp để reader chỉ thấy file đã hoàn chỉnh, và có giải pháp quản lý tham chiếu (reference count, lease, epoch, hoặc giới hạn vòng đời reader).

`Files.move(..., ATOMIC_MOVE)` có thể hỗ trợ thao tác publish **khi filesystem hỗ trợ**, nhưng có thể ném `AtomicMoveNotSupportedException`; nó không tự bảo đảm một chiến lược consistent snapshot hoặc quyền xóa/đổi tên của file đang map trên mọi nền tảng.

## 9. Giải pháp 2: ánh xạ một bản sao ổn định

Nếu nguồn có thể bị writer khác thay đổi và ứng dụng cần bản dữ liệu độc lập, có thể **tạo bản sao rồi ánh xạ bản sao đó**. Khi bản sao thuộc sở hữu riêng và không bị tiến trình khác sửa, việc resize tệp nguồn không ảnh hưởng tới kích thước bản sao.

~~~text
Tệp nguồn (có thể đổi):
source.bin (8 byte) ----- COPY ----- snapshot.bin (8 byte)
       |                                   |
       |                                   +-- READ_ONLY mapping
       |
       +-- writer truncate về 2 byte

snapshot.bin vẫn có 8 byte và mapping của nó
không bị việc truncate SOURCE tác động.
~~~

**Điều kiện quan trọng:** `Files.copy` không tự bảo đảm một snapshot nhất quán nếu writer đang sửa nguồn **trong lúc copy**. Cần phối hợp ownership/locking, đọc bản nguồn đã publish bất biến, hoặc dùng cơ chế snapshot thực sự của storage/filesystem nếu yêu cầu consistency mạnh. Ngoài ra, không được để một actor khác truncate chính **tệp bản sao** khi reader đang map nó.

### 9.1. Chương trình Java 17+ chạy được, tránh truncate vùng đang map

Ví dụ này chỉ thay đổi kích thước **tệp nguồn**. Mapping được tạo trên **một tệp snapshot khác**, nên có kết quả xác định trong chương trình đơn luồng. Không có bước thử truy cập mapping bất hợp lệ.

Lưu vào `SafeMappedSnapshotDemo.java`:

~~~java
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class SafeMappedSnapshotDemo {

    private static String ascii(MappedByteBuffer mapped) {
        MappedByteBuffer view = mapped.duplicate();
        view.position(0);
        byte[] bytes = new byte[view.remaining()];
        view.get(bytes);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("mmap-resize-demo-");
        Path source = dir.resolve("source.bin");
        Path snapshot = dir.resolve("snapshot.bin");

        // Đăng ký dọn lúc JVM kết thúc, thay vì cố xóa file đang map.
        // Các entry được đăng ký theo thứ tự ngược với thứ tự dọn.
        dir.toFile().deleteOnExit();
        source.toFile().deleteOnExit();
        snapshot.toFile().deleteOnExit();

        Files.writeString(source, "ABCDEFGH", StandardCharsets.US_ASCII);
        Files.copy(source, snapshot);

        try (FileChannel channel = FileChannel.open(
                snapshot, StandardOpenOption.READ)) {

            long size = channel.size();
            long offset = 2;
            int length = 4;

            // Kiểm tra phép trừ để tránh overflow khi kiểm tra offset+length.
            if (offset < 0 || length <= 0 ||
                    offset > size || length > size - offset) {
                throw new IOException("Mapping vượt phạm vi snapshot");
            }

            MappedByteBuffer mapped = channel.map(
                    FileChannel.MapMode.READ_ONLY, offset, length);

            System.out.println("snapshotSize=" + size);
            System.out.println("mappedCapacity=" + mapped.capacity());
            System.out.println("mappedBefore=" + ascii(mapped));

            // Chỉ rút ngắn SOURCE, không động đến SNAPSHOT đang được map.
            Files.writeString(
                    source,
                    "XY",
                    StandardCharsets.US_ASCII,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            System.out.println("sourceAfterResize=" +
                    Files.readString(source, StandardCharsets.US_ASCII));
            System.out.println("mappedAfter=" + ascii(mapped));
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac SafeMappedSnapshotDemo.java
java SafeMappedSnapshotDemo
~~~

Stdout xác định trong môi trường hỗ trợ đọc và ánh xạ tệp cục bộ:

~~~text
snapshotSize=8
mappedCapacity=4
mappedBefore=CDEF
sourceAfterResize=XY
mappedAfter=CDEF
~~~

Giải thích:

- Tệp `source.bin` có 8 byte `ABCDEFGH`; `Files.copy` tạo `snapshot.bin` độc lập cũng có 8 byte.
- Mapping dùng `offset=2`, `length=4` nên vùng đọc ban đầu là `CDEF`; `capacity=4`, không phải toàn bộ 8 byte của snapshot.
- Ghi `"XY"` với `TRUNCATE_EXISTING` thay thế nội dung nguồn, đưa kích thước **source** về 2 byte.
- Snapshot không bị ghi đè/truncate, nên mapping vẫn xem được `CDEF`.
- Ví dụ dọn file bằng `deleteOnExit()` vì thời điểm mapping thực sự được giải phóng sau khi đóng channel là không xác định. Với ứng dụng thực tế, cần chiến lược vòng đời riêng; không coi `deleteOnExit` là hệ thống quản lý file lâu dài.

Ví dụ chỉ minh họa **cách cô lập một nguồn có thể thay đổi**. Nếu muốn snapshot nhất quán khi nguồn có writer song song, cần phối hợp xuất bản bản hoàn chỉnh hoặc tính năng snapshot đáng tin cậy trước khi `Files.copy`.

## 10. Giải pháp 3: cửa sổ mapping và tái lập vùng truy cập theo version

Với tệp lớn hoặc luồng dữ liệu append, có thể chia xử lý thành các **cửa sổ ánh xạ** có kích thước giới hạn:

~~~text
Tệp 20 MiB:
  [0..4 MiB) [4..8 MiB) [8..12 MiB) [12..16 MiB) [16..20 MiB)
      window 1    window 2    window 3    window 4     window 5

Mỗi lần:
  - chỉ map vùng hợp lệ đã được công bố;
  - đọc hết vùng và hoàn tất công việc;
  - chuyển sang vùng/phiên bản mới theo quy tắc ownership.
~~~

Lợi ích của mapping theo cửa sổ:

- Mỗi `MappedByteBuffer` có `capacity` đo bằng `int`, nên tránh yêu cầu map một vùng lớn hơn `Integer.MAX_VALUE` byte trên Java 17.
- Có thể giới hạn lượng dữ liệu mỗi lần xử lý.
- Nếu tệp **chỉ được append** và các byte đã công bố không bị sửa, reader có thể map thêm vùng mới theo mốc chiều dài đã commit.

Nhưng **chia cửa sổ không tự loại bỏ rủi ro truncate**. Nếu writer được phép thu nhỏ hoặc ghi đè vùng đang dùng, bất kỳ cửa sổ nào cũng có thể mất hiệu lực. Tạo cửa sổ mới sau khi đọc `channel.size()` cũng có thể gặp TOCTOU nếu có writer không phối hợp.

Một phiên bản hệ thống tốt hơn phải có metadata cho **committed length** và **file version**, cùng nguyên tắc writer không được truncate dữ liệu đã công bố khi reader còn sử dụng.

## 11. Đồng bộ giữa các luồng và các tiến trình

### 11.1. Cùng một JVM

Nếu ứng dụng có thread đọc và thread ghi:

- Một `ReadWriteLock` có thể cho phép nhiều reader cùng đọc trong khi writer phải chờ để resize.
- Writer giữ write lock từ lúc bắt đầu thao tác thay kích thước tới khi hoàn tất việc publish phiên bản mới.
- Reader giữ read lock trong thời gian mapping và đọc dữ liệu cần bảo vệ.

Tuy nhiên, một lock Java chỉ có tác dụng với những luồng **thực sự dùng cùng lock**. Nếu một tiến trình khác có quyền truncate file thì cơ chế khóa trong JVM không giải quyết được rủi ro bên ngoài.

### 11.2. Khác tiến trình

Có thể xem xét `FileChannel.lock(...)` hoặc `tryLock(...)` như cơ chế phối hợp khi các actor cùng tuân theo quy tắc. Nhưng cần hiểu:

- Khả năng shared/exclusive lock phụ thuộc OS và filesystem.
- Một số file lock có tính **advisory**: các tiến trình không tuân thủ vẫn có thể thay đổi file.
- Mapping và locking có thể tương tác khác nhau trên từng nền tảng, kể cả trường hợp không tạo được lock hoặc việc map gặp lỗi.

Vì vậy, với dữ liệu quan trọng, **độc quyền sở hữu phiên bản tệp, snapshot bất biến hoặc giao thức publish mới** thường dễ kiểm chứng hơn kỳ vọng một lệnh lock có thể ngăn mọi thay đổi bên ngoài.

## 12. Những API cần dùng đúng mục đích

| API | Hỗ trợ điều gì? | Không giải quyết được điều gì? |
| --- | --- | --- |
| `FileChannel.map(mode, offset, length)` | Tạo mapping cố định trên vùng được yêu cầu. | Không tự theo dõi tăng/giảm size để điều chỉnh capacity. |
| `channel.size()` / `Files.size(path)` | Đọc kích thước tệp tại thời điểm gọi. | Không bảo vệ khỏi resize ngay sau khi đọc. |
| `channel.truncate(newSize)` | Nếu nhỏ hơn kích thước hiện có thì cắt ngắn file (khi channel được phép ghi). | Không tự sửa mọi mapping còn đang tồn tại ở reader. |
| `mapped.force()` | Đồng bộ sửa đổi của READ_WRITE mapping theo hợp đồng. | Không khôi phục phần tệp bị truncate, không bảo đảm snapshot. |
| `mapped.load()` | Thử nạp các trang mapping vào bộ nhớ. | Không giữ file ở kích thước cũ hoặc giữ trang truy cập được khi nền thay đổi. |
| `mapped.isLoaded()` | Đưa ra gợi ý trạng thái resident. | Không phải phép xác nhận an toàn để tránh truncate. |
| `FileLock` | Phối hợp truy cập giữa actor tuân thủ cơ chế khóa trên nền tảng hỗ trợ. | Không tự chống mọi writer không hợp tác trên mọi OS/filesystem. |
| `Files.copy(...)` | Tạo file đích độc lập khi copy thành công. | Không bảo đảm bản sao nhất quán nếu nguồn bị sửa cùng lúc. |
| `Files.move(..., ATOMIC_MOVE)` | Yêu cầu move nguyên tử nếu provider hỗ trợ. | Không bảo đảm reader dùng path sẽ tự đổi mapping; không giải phóng mapping cũ. |

Với `FileChannel.truncate(n)`, nếu `n` lớn hơn hoặc bằng kích thước tệp đang có, API **không nhất thiết mở rộng tệp**. Để tạo tệp có kích thước mới lớn hơn, cần một thao tác ghi hoặc cơ chế được provider hỗ trợ; không gọi `truncate(largerSize)` và giả định đã cấp vùng byte mới.

## 13. Quy tắc thiết kế một mmap reader chịu được việc thay đổi phiên bản tệp

Một reader production an toàn nên xác định rõ hợp đồng với writer trước khi tối ưu bằng mmap:

1. **Tệp nào được phép thay đổi?** Nếu tệp đang có reader, tối thiểu phải cấm truncate/ghi đè vùng đã công bố hoặc chuyển sang mô hình immutable versions.
2. **Version nhận diện thế nào?** Có thể dùng ID phiên bản, manifest, nội dung checksum hoặc file generation; chỉ size và mtime không đủ cho yêu cầu consistency mạnh.
3. **Reader mở đúng phiên bản nào?** Reader lấy đường dẫn phiên bản đã hoàn chỉnh, giữ ổn định trong toàn bộ lượt xử lý.
4. **Khi nào file cũ được dọn?** Cần xác nhận không còn reader hoặc lease sử dụng, ngoài việc cân nhắc OS giữ mapping sau khi đóng channel.
5. **Có bao giờ map vượt EOF không?** Ranh giới phải dựa trên kích thước đã được công bố và được giữ ổn định qua cơ chế sở hữu.
6. **Điều kiện thất bại nào có thể làm dừng xử lý?** Phân biệt file không tồn tại, lỗi quyền, dữ liệu sai định dạng, phiên bản bị thu hồi và IOException.
7. **Mục tiêu chỉ đọc hay sửa?** Nếu chỉ đọc, chọn `READ_ONLY` trên immutable file. Nếu cần sửa, xác định giao thức commit và độ bền dữ liệu; `READ_WRITE.force()` không giải quyết việc thay kích thước đồng thời.
8. **Có thực sự cần mmap?** Nếu dữ liệu được writer khác sửa kích thước liên tục và không thể phối hợp, đọc thành các buffer độc lập qua `FileChannel.read(...)` có thể dễ kiểm soát lỗi và vòng đời hơn, dù vẫn cần thiết kế cách bảo đảm tính nhất quán nội dung.

Không thể “catch mọi lỗi mmap” rồi tiếp tục như chưa có vấn đề. Một reader gặp dấu hiệu dữ liệu không còn tương ứng với phiên bản đã được xác nhận cần dừng và tải lại theo quy trình được thiết kế.

## 14. Lỗi phổ biến cần tránh

### Sai lầm 1: thấy capacity chưa đổi nên tin nội dung còn tồn tại

Sau khi file bị truncate, `mapped.capacity()` vẫn mô tả kích thước mapping ban đầu. Nó **không** chứng minh mọi byte tương ứng còn khả dụng.

### Sai lầm 2: gọi force() để phục hồi dữ liệu đã mất

`force()` không mở rộng file hoặc tái cấp lại vùng mapping bị truncate. Với `READ_WRITE` nó chỉ liên quan đến đồng bộ các sửa đổi từ mapping về thiết bị lưu trữ.

### Sai lầm 3: nghĩ PRIVATE là snapshot bất biến của file

PRIVATE có semantics copy-on-write đối với sửa đổi thông qua mapping đó. Nó không bảo đảm cô lập khỏi mọi thay đổi nội dung hoặc kích thước từ bên ngoài.

### Sai lầm 4: tin check size trước get loại bỏ rủi ro

Kiểm tra kích thước không khóa file. Writer có thể truncate ngay sau đó. Đây là TOCTOU; cần kiểm soát sự thay đổi bằng giao thức hay ownership.

### Sai lầm 5: bỏ qua lifetime của mapping

Đóng `FileChannel` không bảo đảm vùng mapping bị thu hồi ngay. Trên một số hệ điều hành, delete/rename/truncate có thể bị hạn chế do file vẫn đang được map.

### Sai lầm 6: map tệp có thể bị thay bằng phiên bản khác mà không lưu version

Path là một tên có thể được gắn với đối tượng tệp khác sau thao tác replace. Một mapping đã tồn tại không được bảo đảm sẽ tự thay đổi sang bản mới chỉ vì cùng path đang trỏ tới bản khác. Reader phải quản lý object/version tương ứng.

### Sai lầm 7: cho rằng file mới được append tự xuất hiện trong buffer cũ

`MappedByteBuffer` có capacity cố định. Byte mới nằm ngoài vùng ánh xạ cũ cần được đọc qua mapping hoặc API mới có phạm vi phù hợp.

### Sai lầm 8: kiểm thử bằng cố tình gây lỗi OS trên file đang map

Truncate một file đang map rồi cố đọc phần bị cắt là một thử nghiệm có thể gây hậu quả ngoài khả năng xử lý theo một exception chuẩn trên các nền tảng. Hãy kiểm tra chính sách ownership, vùng map hợp lệ và đường chuyển phiên bản bằng các bài test không truy cập mapping đã mất hiệu lực.

## 15. Câu hỏi tự kiểm tra

1. **Tệp dài 4 KiB đã được map toàn bộ, sau đó truncate còn 1 KiB. `mapped.capacity()` có tự giảm thành 1 KiB?** Không. Capacity của buffer cố định; phần mapping ngoài độ dài mới có nguy cơ không khả dụng.
2. **Có thể dựa vào `catch (IndexOutOfBoundsException)` để xử lý mọi lần truy cập phần bị truncate?** Không. Index có thể vẫn hợp lệ trong buffer trong khi mapping nền không còn hợp lệ; API không quy định một loại exception cố định cho trường hợp này.
3. **Nếu file được append từ 8 lên 12 byte, buffer đã map 8 byte có tự nhìn thấy index 8..11 không?** Không; capacity vẫn 8.
4. **`READ_ONLY` có ngăn tiến trình khác truncate file không?** Không. Nó chỉ cấm ghi qua buffer này.
5. **`PRIVATE` có bảo đảm snapshot nhất quán khi file nền bị thay đổi kích thước không?** Không.
6. **Vì sao kiểm tra `channel.size()` trước `mapped.get(i)` chưa đủ?** Có thể xảy ra resize giữa check và get.
7. **`FileChannel.force()` hoặc `MappedByteBuffer.force()` có “fix” được mapping sau truncate không?** Không. Đây là các API đồng bộ dữ liệu theo hợp đồng, không phải công cụ tái lập kích thước.
8. **Cách tránh rủi ro phổ biến nhất khi có nhiều reader?** Công bố các bản file bất biến/versioned và không thay đổi kích thước bản reader đang dùng; dọn bản cũ theo vòng đời reader.
9. **`Files.copy(source, snapshot)` đã đủ là snapshot nhất quán dưới concurrent writer chưa?** Chưa. Cần phối hợp để không copy giữa lúc dữ liệu đang thay đổi, hoặc dùng cơ chế snapshot của storage.
10. **Vì sao cần quản lý file cũ ngay cả sau khi đóng channel?** Mapping có vòng đời riêng và không được bảo đảm giải phóng ngay khi channel đóng.
11. **Có thể dùng một `MappedByteBuffer` Java 17 để map tệp lớn hơn 2 GiB vào một buffer duy nhất không?** Không theo giới hạn kích thước mapping của API này; cần chia vùng, hoặc chọn API phù hợp.
12. **Nếu yêu cầu dữ liệu nhất quán, có nên dựa riêng vào mtime và file size?** Không. Chúng chỉ là các dấu hiệu, không bảo đảm file không bị sửa giữa hai lần kiểm tra.

**Ghi nhớ:** memory mapping gắn buffer với một **vùng tệp ở thời điểm tạo mapping**, nhưng không đóng băng tệp nền. Nếu tệp bị thu nhỏ, vùng mapping có thể mất khả năng truy cập; nếu tệp được mở rộng, buffer cũ không tự tăng capacity. Hãy quản lý **ownership, version và vòng đời tệp**, giữ dữ liệu đang map ổn định, hoặc ánh xạ một bản sao bất biến khi cần cô lập reader khỏi resize.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="memory-mapping-010">Khi nào memory mapping có ích và khi nào gây bất lợi</a>

<details>
<summary>Xem chi tiết</summary>

# Khi nào memory mapping có ích và khi nào gây bất lợi trong Java NIO.2

## 1. Memory mapping là gì?

**Memory mapping (ánh xạ tệp vào bộ nhớ)** cho phép chương trình làm việc với một vùng nội dung tệp thông qua các thao tác đọc/ghi trên bộ đệm được ánh xạ vào **không gian địa chỉ ảo** của tiến trình. Hệ điều hành liên kết các trang bộ nhớ ảo với dữ liệu tệp và đưa những trang cần thiết vào RAM theo cơ chế quản lý bộ nhớ của nó.

Trong Java NIO.2, cách sử dụng phổ biến là gọi `FileChannel.map(...)` để nhận `MappedByteBuffer`:

~~~java
try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
    MappedByteBuffer mapped =
            channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
    byte firstByte = mapped.get(0);
}
~~~

Đây là **đoạn minh họa**, cần các import `java.nio.MappedByteBuffer`, `java.nio.channels.FileChannel`, `java.nio.file.Path` và `java.nio.file.StandardOpenOption`, với `path` là một tệp có ít nhất một byte.

`MappedByteBuffer` kế thừa `ByteBuffer`, nên có thể dùng các phép đọc/ghi theo vị trí tương đối hoặc tuyệt đối, chẳng hạn `get()`, `get(int)` và `put(int, byte)` (nếu mapping cho phép ghi).

**Memory mapping không có nghĩa là tải toàn bộ tệp vào Java heap**. Vùng mapping thuộc địa chỉ ảo và do hệ điều hành quản lý; những trang được truy cập có thể được nạp vào bộ nhớ vật lý khi cần. Java heap vẫn chứa đối tượng quản lý bộ đệm và các đối tượng khác của chương trình. Vì thế, một ứng dụng có thể dùng ít heap nhưng vẫn gây áp lực RAM, page cache và không gian địa chỉ.

Mục tiêu của bài: biết **trường hợp nào mapping có lợi**, **chi phí và rủi ro nào khiến mapping bất lợi**, và **cách chọn đúng công cụ I/O** dựa trên kiểu truy cập.

## 2. Cơ chế hoạt động ở mức cần biết

Khi gọi:

~~~java
MappedByteBuffer buffer = channel.map(
        FileChannel.MapMode.READ_ONLY, startOffset, length);
~~~

ứng dụng yêu cầu một vùng ánh xạ tệp bắt đầu tại offset `startOffset`, dài `length` byte. Việc tạo mapping không đồng nghĩa mọi byte trong vùng đã nằm sẵn trong RAM.

Một chuỗi sự kiện thường gặp:

1. Chương trình tạo mapping giữa vùng địa chỉ ảo và vùng tệp.
2. Khi chạm tới trang chưa có sẵn trong bộ nhớ, CPU/hệ điều hành có thể phát sinh **page fault** để thiết lập ánh xạ trang hoặc đưa dữ liệu từ thiết bị lưu trữ vào bộ nhớ.
3. Khi trang đã sẵn sàng, chương trình đọc/ghi qua buffer mà không cần gọi `FileChannel.read(...)`/`write(...)` ở từng thao tác.
4. Với mapping có quyền ghi, các trang bị sửa có thể trở thành **dirty pages** và được hệ điều hành ghi trở lại thiết bị theo chính sách của nó; ứng dụng có thể yêu cầu đồng bộ bằng `MappedByteBuffer.force()`.

Page fault **không phải lúc nào cũng là đọc từ đĩa**: có thể chỉ cần ánh xạ tới một trang đã nằm trong page cache (minor fault). Nếu phải chờ I/O lưu trữ, độ trễ có thể lớn hơn đáng kể (major fault). Mapping chỉ thay đổi con đường truy cập dữ liệu; nó không loại bỏ chi phí I/O hoặc bảo đảm độ trễ thấp.

### Ba chế độ `FileChannel.MapMode`

| Chế độ | Đọc/ghi trong mapping | Ý nghĩa |
| --- | --- | --- |
| `READ_ONLY` | Đọc; cố ghi sẽ gặp `ReadOnlyBufferException` | Phù hợp khi chỉ tra cứu/duyệt dữ liệu không cần cập nhật |
| `READ_WRITE` | Đọc và ghi | Thay đổi buffer được ánh xạ vào vùng tệp; cân nhắc `force()` khi cần yêu cầu ghi xuống thiết bị |
| `PRIVATE` | Có thể đọc và sửa trong vùng nhìn thấy của mapping | Cơ chế copy-on-write: thay đổi không được ghi vào tệp nguồn qua mapping này; không dùng để lưu cập nhật bền vững |

`MapMode.PRIVATE` **không đồng nghĩa đã sao chép toàn bộ tệp ngay khi map**. Các trang cần thay đổi được xử lý theo cơ chế copy-on-write của nền tảng; hành vi nhìn thấy các sửa đổi khác của tệp sau thời điểm mapping không nên được giả định là nhất quán.

## 3. Khi nào memory mapping thường có ích?

### 3.1. Truy cập ngẫu nhiên nhiều lần vào những vùng dữ liệu lớn

Ví dụ: tệp chỉ mục nhị phân có bản ghi cố định, mỗi bản ghi dài 32 byte, và chương trình cần đọc hàng nghìn bản ghi ở các offset khác nhau.

Với `FileChannel.read(dst, position)`, ứng dụng thường phải chuẩn bị `ByteBuffer`, gọi đọc và kiểm tra số byte nhận được. Với mapping, sau khi tạo vùng ánh xạ, có thể truy cập byte ở offset cần thiết trực tiếp qua `get(int)` (trong giới hạn của buffer).

~~~java
// Minh họa với mapping đang mở, recordSize = 32.
int recordSize = 32;
int recordIndex = 7;
int offset = Math.multiplyExact(recordIndex, recordSize);
byte type = mapped.get(offset);
~~~

Đoạn này minh họa truy cập có vị trí; cần chắc chắn `offset` còn nằm trong vùng mapping và `mapped` có ít nhất `offset + 1` byte. Với tệp lớn phải tính offset toàn cục bằng `long` rồi xác định cửa sổ mapping phù hợp trước khi đổi thành chỉ số `int`.

**Lợi ích tiềm năng:** giảm công việc sao chép sang bộ đệm trung gian và số lời gọi đọc có chủ đích ở cấp ứng dụng; thuận tiện với các cấu trúc được thiết kế để đọc trực tiếp từ một vùng byte.

**Điều kiện:** mô hình truy cập, kích thước tập dữ liệu, page cache, CPU và hệ thống tệp thực sự phù hợp. Truy cập ngẫu nhiên trên thiết bị chậm có thể vẫn chịu nhiều page fault và nhanh chóng trở thành nút thắt.

### 3.2. Tệp chỉ đọc được truy cập lặp đi lặp lại

Một bộ tra cứu từ điển, bảng mã, bảng chỉ mục hoặc dữ liệu nhị phân lớn nhưng tương đối ổn định có thể được ánh xạ một lần rồi truy vấn nhiều lần. Khi những trang được dùng thường xuyên đã có sẵn, các lần đọc tiếp theo có khả năng tận dụng cache của hệ điều hành.

Cách dùng phổ biến:

- Tạo mapping `READ_ONLY` trong vòng đời thành phần truy vấn.
- Xác định định dạng tệp, byte order, offset, kích thước bản ghi và xử lý dữ liệu lỗi.
- Không tạo/hủy mapping ở từng yêu cầu nhỏ; cân nhắc tái sử dụng mapping khi vòng đời dữ liệu cho phép.

Đây là **mẫu ứng dụng có thể có lợi**, không phải cam kết tốc độ vượt mọi phương án khác: `FileChannel` và buffered I/O cũng tận dụng page cache.

### 3.3. Chia sẻ dữ liệu đọc với các tiến trình trên cùng máy

Nếu nhiều tiến trình ánh xạ cùng một vùng tệp, hệ điều hành có thể chia sẻ các trang dữ liệu vật lý/page cache tương ứng. Với tập dữ liệu chỉ đọc hoặc mô hình chia sẻ được thiết kế cẩn thận, cách này có thể giảm nhu cầu duy trì các bản sao dữ liệu độc lập trong heap của từng tiến trình.

Tuy nhiên, **memory mapping không thay thế giao thức đồng bộ giữa các tiến trình**. Đối với cập nhật đồng thời, phải xác định rõ tính nhất quán, thứ tự công bố dữ liệu, khóa và giới hạn của nền tảng. `MappedByteBuffer` không tự cung cấp transaction, snapshot nhất quán hoặc cơ chế thông báo sự kiện.

### 3.4. Xử lý cấu trúc nhị phân theo vùng

Các tệp chứa header, record cố định hoặc các bảng offset thường phù hợp hơn với mapping so với dữ liệu văn bản phải phân tích tuần tự phức tạp. Chẳng hạn, một file chứa 1 triệu record có header định nghĩa chiều dài từng record: ứng dụng ánh xạ một cửa sổ cần thiết rồi dùng `getInt(...)`, `getLong(...)`, `slice()` và `order(ByteOrder...)` để giải mã các trường.

Trong ứng dụng thực tế phải kiểm tra:

- Endianness (thứ tự byte) được định nghĩa rõ: mặc định `ByteBuffer` dùng big-endian nếu chưa đặt khác.
- Mọi offset và kích thước đều nằm trong phạm vi hợp lệ trước khi đọc.
- Bộ đệm không được dùng ngoài vòng đời dữ liệu mà hệ thống tệp có thể thay đổi nguy hiểm.
- Việc ánh xạ theo cửa sổ để xử lý tệp vượt giới hạn kích thước một `ByteBuffer`.

## 4. Những trường hợp mapping có thể gây bất lợi

### 4.1. Tệp nhỏ, chỉ đọc/ghi một lần

Nếu một chương trình chỉ đọc tệp cấu hình vài kilobyte, `Files.readString(path)` hoặc `Files.readAllBytes(path)` thường đơn giản và ít ràng buộc hơn. Memory mapping có chi phí tạo/quản lý vùng ánh xạ, xử lý page fault và vòng đời tài nguyên; chi phí này có thể lớn hơn lợi ích với dữ liệu nhỏ hoặc sử dụng một lần.

Tương tự, khi cần **sao chép tuần tự** từ một tệp sang tệp khác, `Files.copy`, `InputStream`/`OutputStream`, `FileChannel.transferTo` hoặc bộ đệm tuần tự thường dễ hiểu hơn. Việc ánh xạ không tự động là đường sao chép tệp nhanh nhất.

### 4.2. Truy cập ngẫu nhiên vào tập dữ liệu lớn hơn RAM, thiếu tính cục bộ

Memory mapping có thể tiện khi mô hình truy cập ngẫu nhiên, nhưng nếu mỗi lần đọc chạm tới một trang mới và tập dữ liệu lớn vượt xa RAM, số lần thiếu trang và I/O tăng. Kết quả có thể là độ trễ tăng mạnh, tranh chấp page cache và làm chậm các ứng dụng khác trên máy.

Một buffer được ánh xạ không có nghĩa dữ liệu đã được “cache toàn bộ”. Nếu ứng dụng thường xuyên đọc vùng mới trên ổ chậm, hãy cân nhắc index nhỏ hơn, caching theo tầng, batch đọc hoặc bố trí dữ liệu theo thứ tự truy cập.

### 4.3. Hệ thống yêu cầu độ trễ ổn định, dự đoán được

Truy cập mapped memory có thể kích hoạt page fault **ngay ở biểu thức `get(...)` hoặc `put(...)`**, khiến độ trễ xảy ra ở vị trí ít hiển nhiên trong mã. Điều này khó kiểm soát khi ứng dụng có yêu cầu tail latency nghiêm ngặt.

`MappedByteBuffer.load()` có thể yêu cầu hệ thống cố gắng nạp các trang, nhưng **không phải cam kết mọi trang sẽ luôn cư trú trong RAM**, cũng không loại bỏ khả năng các trang bị thu hồi sau đó. `isLoaded()` chỉ cung cấp thông tin gần đúng/tạm thời; không dùng làm chứng nhận thời gian đọc sẽ ổn định.

Nếu cần kiểm soát lịch đọc, batching, backpressure và timeout rõ ràng, đọc tường minh qua `FileChannel`/buffer có thể dễ quản trị hơn.

### 4.4. Tệp thường xuyên đổi kích thước, bị xóa/đổi tên hoặc ghi lại

Mapping có vòng đời độc lập phần nào với `FileChannel`. Đóng channel **không lập tức hủy vùng ánh xạ**; buffer có thể còn tham chiếu tới vùng mapped memory.

Nếu tệp bị truncate khi buffer vẫn có thể truy cập vùng cũ, hành vi truy cập phần không còn hợp lệ có thể dẫn tới lỗi không dự đoán được theo triển khai. API không bảo đảm một cơ chế tự động làm mới mapping an toàn sau mọi thay đổi tệp.

Một số hệ điều hành, đặc biệt Windows, có thể hạn chế đổi tên/xóa/resize tệp khi mapping còn tồn tại. Do đó, các quy trình thường xuyên thay file bằng cách rename hoặc truncate có thể gặp vấn đề vòng đời và cleanup.

**Quy tắc thực hành:** Khi dữ liệu cần thay thế thường xuyên, hãy cân nhắc mô hình tạo tệp phiên bản mới, chuyển tham chiếu theo giao thức ứng dụng, chờ người dùng của mapping cũ kết thúc rồi mới dọn tệp. Tránh vừa sử dụng mapping vừa sửa/truncate file một cách không điều phối.

### 4.5. Tạo rất nhiều mapping ngắn hạn

Mỗi mapping sử dụng tài nguyên bộ nhớ ảo và tài nguyên quản lý của hệ điều hành/JVM. Tạo hàng nghìn mapping cho các tệp nhỏ hoặc tạo mapping mới cho từng request có thể tăng áp lực quản lý trang và tài nguyên native.

`MappedByteBuffer` ở Java 17 **không cung cấp phương thức `close()` hoặc `unmap()` công khai chuẩn**. Vùng ánh xạ tồn tại cho đến khi bộ đệm đủ điều kiện được thu gom và triển khai giải phóng tài nguyên. Dựa vào GC để giải phóng ngay lập tức là không an toàn; ép `System.gc()` cũng không phải hợp đồng thu hồi mapping đáng tin.

Nếu cần vòng đời ánh xạ chặt chẽ, hãy xem xét lựa chọn API phù hợp phiên bản JDK hoặc chuyển sang FileChannel I/O thông thường. Đừng dựa vào API nội bộ của JDK để “unmap ngay” trong chương trình portable.

### 4.6. Dữ liệu cần giải mã, giải nén hoặc biến đổi lớn

Nếu dữ liệu nằm trong tệp nén/mã hóa và cần giải nén/giải mã trước khi sử dụng, byte thô được map vào không tự biến thành dữ liệu đã xử lý. Ứng dụng vẫn cần chuỗi xử lý riêng, buffer phù hợp và kiểm soát bảo mật.

Mapping có thể giúp truy cập tệp chứa dữ liệu mã hóa theo block, nhưng lợi ích phụ thuộc định dạng, tính cục bộ và thiết kế truy cập. Với xử lý theo luồng (streaming transform), `InputStream`, `ReadableByteChannel` và buffer tường minh thường hợp lý hơn.

## 5. Ví dụ chạy độc lập với Java 17+: đọc, cập nhật, đồng bộ

Ví dụ sau tạo tệp ASCII dài 10 byte, ánh xạ bằng `READ_WRITE`, đọc/sửa byte đầu ở vị trí tuyệt đối, gọi `force()` rồi đọc lại qua API tệp để kiểm tra.

Lưu thành **`MemoryMappingDecisionDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class MemoryMappingDecisionDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("memory-mapping-", ".dat");

        try {
            Files.write(file,
                    "ABCDE12345".getBytes(StandardCharsets.US_ASCII));

            try (FileChannel channel = FileChannel.open(
                    file,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE)) {

                MappedByteBuffer mapped = channel.map(
                        FileChannel.MapMode.READ_WRITE, 0, channel.size());

                System.out.println("File size: " + channel.size());
                System.out.println("First before: " + (char) mapped.get(0));

                mapped.put(0, (byte) 'Z');
                mapped.force();

                System.out.println("First after: " + (char) mapped.get(0));
                System.out.println("Channel position: " + channel.position());
            }

            System.out.println("File content: "
                    + Files.readString(file, StandardCharsets.US_ASCII));
        } finally {
            // Trên một số nền tảng, file còn có thể bị giữ bởi mapping.
            // Dọn tạm theo best effort để demo vẫn kết thúc rõ ràng.
            try {
                Files.deleteIfExists(file);
            } catch (IOException cleanupFailure) {
                file.toFile().deleteOnExit();
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 MemoryMappingDecisionDemo.java
java MemoryMappingDecisionDemo
~~~

Trong trường hợp I/O bình thường, stdout:

~~~text
File size: 10
First before: A
First after: Z
Channel position: 0
File content: ZBCDE12345
~~~

**Giải thích:**

1. Tệp được ghi sẵn 10 byte trước khi map; vùng mapping hợp lệ và không phụ thuộc hành vi nền tảng khi ánh xạ qua cuối tệp.
2. `channel.map(READ_WRITE, 0, channel.size())` ánh xạ toàn bộ vùng dữ liệu đã tồn tại. Việc map không tự đổi `channel.position()`.
3. `mapped.get(0)` và `mapped.put(0, ...)` là thao tác **absolute access** trên `ByteBuffer`, không tăng vị trí nội bộ của buffer; nội dung vị trí 0 đổi từ `A` thành `Z`.
4. `mapped.force()` yêu cầu ghi các thay đổi trong mapping xuống thiết bị lưu trữ theo hợp đồng API cho mapping ghi. Đây không phải transaction, không chứng minh mọi metadata đã được đồng bộ hoặc bảo đảm khả năng khôi phục bất chấp sự cố mọi loại thiết bị/hệ thống tệp.
5. `Files.readString(...)` kiểm tra nội dung sau khi cập nhật, còn `channel.position() == 0` minh họa rằng thao tác mapping không tác động vị trí I/O tường minh của channel.
6. `try-with-resources` đóng `FileChannel`; tuy nhiên mapping **không có cùng vòng đời với channel**. Cleanup có thể bị trì hoãn trên một số hệ điều hành, nên ví dụ sử dụng dọn tạm theo best effort. `deleteOnExit()` chỉ là giải pháp phụ cho tệp tạm trong demo, không phải cơ chế quản lý mapping đáng tin trong dịch vụ chạy lâu dài.

### Vì sao ví dụ dùng ASCII?

Vị trí `get(0)` và `put(0)` xử lý **byte**, không phải ký tự Unicode. Với UTF-8, một ký tự có thể cần nhiều byte và việc sửa một byte ngẫu nhiên có thể phá hỏng chuỗi. Memory mapping phù hợp với cấu trúc nhị phân đã có schema/encoding rõ ràng hơn là chỉnh văn bản Unicode tại offset ngẫu nhiên.

## 6. Các giới hạn kỹ thuật quan trọng

### 6.1. Mỗi `MappedByteBuffer` dùng chỉ số `int`

Dù `FileChannel.map(...)` nhận `long position` và `long size`, đối tượng trả về là một `MappedByteBuffer` với `capacity()` kiểu `int`. Trong Java 17, độ dài của **một mapping** không vượt `Integer.MAX_VALUE` byte.

Vì vậy, không map một lần toàn bộ tệp 10 GiB. Hãy chia thành các **cửa sổ (windows)**:

~~~java
long fileSize = channel.size();
long offset = 0;
final long windowBytes = 64L * 1024 * 1024;

while (offset < fileSize) {
    int size = (int) Math.min(windowBytes, fileSize - offset);

    MappedByteBuffer window = channel.map(
            FileChannel.MapMode.READ_ONLY, offset, size);

    // Đọc/giải mã dữ liệu trong window (size byte).
    // Dữ liệu record giao giữa hai cửa sổ phải được xử lý riêng.

    offset += size;
}
~~~

Đây là **đoạn minh họa**, cần `channel` mở ở chế độ đọc, import `FileChannel` và `MappedByteBuffer`. Để tránh giữ nhiều mapping cũ cùng lúc, hãy thiết kế bộ quản lý cửa sổ và mức đồng thời phù hợp; việc thay biến `window` không bảo đảm mapping cũ được giải phóng ngay.

Nếu một bản ghi nằm vắt qua hai cửa sổ, xử lý phần dư giữa các cửa sổ hoặc thiết kế cửa sổ chồng lấn có kiểm soát. Không âm thầm làm rơi phần cuối của bản ghi.

### 6.2. Bộ nhớ ảo khác heap và RAM vật lý

Khi đánh giá memory mapping, theo dõi **nhiều chỉ số cùng lúc**:

- **Java heap:** nơi đặt các đối tượng Java, không phản ánh trực tiếp toàn bộ mapped pages.
- **Virtual address space:** vùng địa chỉ được giữ để ánh xạ, có thể lớn hơn nhiều so với lượng RAM đang sử dụng.
- **Resident memory / page cache:** trang thực sự hiện diện trong RAM; tăng khi truy cập mapping và có thể bị hệ điều hành thu hồi.
- **Page faults, disk I/O, CPU và latency:** cho thấy đường đi của bộ nhớ có thực sự giúp hiệu năng hay không.

Một heap nhỏ **không chứng minh ứng dụng dùng ít tài nguyên hệ thống**. Giới hạn container, bộ nhớ native, địa chỉ ảo và các tiến trình khác đều có thể ảnh hưởng.

### 6.3. Mapping không đồng nghĩa dữ liệu luôn sẵn trong RAM

~~~java
mapped.load();           // Gợi ý cố gắng nạp trang.
boolean loaded = mapped.isLoaded();
~~~

Đây là API minh họa sử dụng trên một `MappedByteBuffer` đã tạo. `load()` có thể tốn thời gian đáng kể và không bảo đảm trang không bị thu hồi sau đó. `isLoaded()` không thay thế phép đo page faults hoặc kiểm soát cache của hệ điều hành.

### 6.4. Tính nhất quán khi tệp thay đổi

Mapping đang hoạt động trong lúc bên khác sửa, truncate hoặc thay file là trường hợp cần **giao thức ứng dụng rõ ràng**. Không suy ra rằng mọi thay đổi ngoài mapping sẽ xuất hiện tức thì và nhất quán trong từng thao tác đọc. Không dùng mapping làm snapshot giao dịch khi không có cơ chế snapshot thật.

Với dữ liệu cấu hình/chỉ mục thay phiên bản, có thể dùng các tệp chỉ đọc bất biến cho từng phiên bản và chuyển sang phiên bản mới ở mức đối tượng quản lý; chỉ giải phóng mapping cũ khi không còn độc giả. Đây là **hướng thiết kế**, không phải tính năng được `MappedByteBuffer` tự cung cấp.

## 7. So sánh với các cách I/O khác

| Nhu cầu | Memory mapping | Lựa chọn khác thường phù hợp |
| --- | --- | --- |
| Truy vấn vị trí ngẫu nhiên nhiều lần trong file nhị phân ổn định | **Đáng thử**: truy cập theo offset trực tiếp, tận dụng cache | `FileChannel.read(buffer, position)` nếu muốn kiểm soát I/O tường minh |
| Cùng tập dữ liệu chỉ đọc được dùng lâu dài | **Đáng thử**, nếu quản lý vòng đời mapping tốt | FileChannel với cache ứng dụng |
| Sao chép file nguyên vẹn một lần | Không phải lựa chọn mặc định | `Files.copy` hoặc `FileChannel.transferTo/transferFrom` |
| Đọc văn bản nhỏ một lần | Thường thêm độ phức tạp | `Files.readString`, `BufferedReader` |
| Truyền dữ liệu mạng trực tiếp | Mapping không tự giải quyết backpressure và network | `SocketChannel`, luồng/buffer; `transferTo` khi thích hợp |
| Tệp thay thế/truncate liên tục | Rủi ro mapping cũ và tài nguyên bị giữ | I/O tường minh, versioned immutable files |
| Tệp lớn hơn 2 GiB | Phải tổ chức mapping theo cửa sổ | `FileChannel` với buffer có kích thước cố định |
| Phải kiểm soát latency, cancellation, timeout | Page fault có thể khó dự đoán | Đọc/ghi theo lô tường minh, kiến trúc async phù hợp |
| Cần giải mã/giải nén theo luồng | Chỉ có ích nếu định dạng hỗ trợ random access rõ ràng | Stream hoặc pipeline ByteBuffer |
| Ghi cập nhật đòi hỏi giao dịch/durability | Cần thêm giao thức đồng bộ, xác nhận tính toàn vẹn | Cơ sở dữ liệu hoặc log giao dịch phù hợp |

**Không có một phương pháp nhanh nhất cho mọi máy và mọi workload.** Ưu điểm của mapping phụ thuộc phân bố offset đọc, tỷ lệ đọc/ghi, kích thước tệp, cache, thiết bị lưu trữ và vòng đời dữ liệu.

## 8. Cách quyết định trong dự án thực tế

Thay vì chọn công nghệ theo tên gọi “zero-copy”, hãy kiểm tra lần lượt các câu hỏi sau:

1. **Truy cập kiểu gì?** Nếu chỉ đọc/ghi tuần tự một lần, dùng buffered/FileChannel thông thường làm baseline. Nếu nhiều random access lặp lại trong một file ổn định, mapping là ứng viên tốt.
2. **Tệp thay đổi thế nào?** Tệp bất biến/read-only dễ quản lý mapping. File thường bị truncate, resize hoặc thay tên cần phương án vòng đời cẩn thận.
3. **Kích thước và phạm vi đọc?** Nếu file vượt 2 GiB, lập kế hoạch window. Nếu phần dùng thường xuyên rất nhỏ, có thể chỉ map/đọc phần nóng.
4. **Độ trễ quan trọng tới đâu?** Nếu phục vụ request nhạy với page fault, cần đo P95/P99 chứ không chỉ throughput trung bình.
5. **Giới hạn tài nguyên là gì?** Kiểm tra heap, RSS, page cache, địa chỉ ảo, số mapping, giới hạn container và quyền truy cập file.
6. **Có cần cập nhật bền vững không?** Với `READ_WRITE`, xác định điểm gọi `force()`, giao thức khôi phục và kiểm tra tính toàn vẹn. Không xem `force()` là giao dịch đa bước.
7. **Đo đạc ra sao?** So sánh ít nhất memory mapping với `FileChannel.read/write` hoặc streaming I/O trên cùng workload, cùng kích thước file và điều kiện máy; đo cold cache/warm cache riêng, page faults, throughput, CPU, RSS và tail latency.

### Ví dụ tình huống

**Tình huống A — bộ từ điển nhị phân chỉ đọc 3 GiB, truy vấn ngẫu nhiên liên tục:** Mapping theo cửa sổ hoặc kiến trúc hỗ trợ mapping phân đoạn là phương án có cơ sở để benchmark. Thời gian truy vấn có thể giảm khi trang nóng được cache. Cần giải quyết kích thước window, cấu trúc bản ghi và vòng đời mapping.

**Tình huống B — dịch vụ nhận file upload rồi sao chép sang thư mục khác:** Không cần map chỉ để sao chép tuần tự. `Files.copy` hoặc các API channel/stream có cơ chế kiểm soát lỗi và backpressure rõ ràng thường đơn giản hơn.

**Tình huống C — file cấu hình vài KiB thay mới liên tục bằng rename:** `Files.readString` và giữ cấu hình đã parse trong bộ nhớ có thể phù hợp hơn. Mapping tạo thêm rủi ro giữ file cũ và sự phức tạp khi thay phiên bản.

**Tình huống D — hệ thống ghi record trực tiếp trong file với yêu cầu crash recovery:** Mapping `READ_WRITE` có thể hữu ích nhưng không giải quyết atomicity, journal, thứ tự flush, kiểm tra dữ liệu hay phục hồi. Cần thiết kế giao thức lưu trữ hoặc dùng hệ quản trị dữ liệu thích hợp.

## 9. Sai lầm thường gặp

**Sai lầm 1 — “Map xong là toàn bộ file đã vào RAM.”** Không. Mapping là địa chỉ ảo và thường đưa trang vào bộ nhớ theo nhu cầu. `load()` không phải cơ chế pin RAM vĩnh viễn.

**Sai lầm 2 — “Không dùng heap nên không tốn bộ nhớ.”** Không. Mapping dùng địa chỉ ảo, RAM/page cache và tài nguyên native; chỉ riêng biểu đồ heap không phản ánh đủ.

**Sai lầm 3 — “MappedByteBuffer cho phép map một file lớn tùy ý thành một buffer.”** Không trong API `MappedByteBuffer` Java 17: giới hạn `int` của capacity khiến cần nhiều cửa sổ nếu file đủ lớn.

**Sai lầm 4 — “Đóng FileChannel sẽ unmap ngay.”** Không. Mapping có vòng đời riêng và không có `close()` công khai chuẩn trên `MappedByteBuffer`.

**Sai lầm 5 — “Map luôn nhanh hơn read/write.”** Không. Page fault, layout dữ liệu, CPU/cache, workload, và overhead khởi tạo có thể làm mapping chậm hơn.

**Sai lầm 6 — “MappedByteBuffer.force() bảo đảm toàn bộ transaction thành công.”** Không. API không thực hiện transaction, không tự phối hợp các file và không tự làm cho bản ghi cập nhật nguyên tử.

**Sai lầm 7 — “Có thể truncate file trong khi các thread vẫn đọc mapping.”** Nguy hiểm. Truy cập vùng không còn hợp lệ có thể dẫn tới lỗi phụ thuộc triển khai; cần điều phối vòng đời dữ liệu.

**Sai lầm 8 — “Byte offset cũng là ký tự offset.”** Không, đặc biệt với UTF-8/UTF-16; việc giải mã phải theo encoding và ranh giới ký tự hoặc schema nhị phân.

**Sai lầm 9 — “PRIVATE mapping sẽ ghi thay đổi trở lại file khi force.”** Không. `PRIVATE` dùng semantics copy-on-write, không phù hợp để lưu thay đổi vào tệp nguồn.

**Sai lầm 10 — “Dùng mmap là tự động zero-copy cho cả truyền mạng.”** Không. Tối ưu truyền dữ liệu tới socket là vấn đề khác, với API và phụ thuộc nền tảng riêng như `FileChannel.transferTo`; mapping không thay thế lựa chọn đó.

## 10. Câu hỏi tự kiểm tra

1. **Bạn cần tìm ngẫu nhiên hàng triệu bản ghi nhị phân trong file chỉ đọc, truy vấn liên tục. Có nên thử mapping không?** Có. Đây là ứng viên hợp lý, nhưng vẫn cần benchmark với `FileChannel.read(position)` và đánh giá page faults, cache và latency.
2. **Một file 6 GiB có thể được map thành một `MappedByteBuffer` duy nhất trong Java 17 không?** Không. `MappedByteBuffer` có capacity kiểu `int`; cần chia thành những cửa sổ hợp lệ.
3. **Đã gọi `FileChannel.close()` thì có thể xóa mapped file ngay trên mọi hệ điều hành?** Không bảo đảm. Mapping có thể tồn tại sau khi channel đóng, và một số hệ điều hành hạn chế xóa/đổi tên tệp đang được map.
4. **`mapped.load()` bảo đảm sẽ không có page fault trong các lần truy vấn tiếp theo?** Không. Trạng thái trang có thể thay đổi; `load()` chỉ cố gắng nạp các trang.
5. **Bạn cần cập nhật file theo nhiều bước với rollback khi mất điện. Chỉ sử dụng `READ_WRITE` và `force()` đã đủ chưa?** Chưa. Cần thiết kế atomicity, thứ tự ghi, khôi phục và kiểm tra tính toàn vẹn riêng.
6. **Khi nào I/O tuần tự có thể hợp lý hơn?** Khi đọc một lần, xử lý streaming, dữ liệu nhỏ, cần quản lý timeout/cancellation rõ ràng hoặc tệp bị thay đổi thường xuyên.

## 11. Ghi nhớ

**Memory mapping đáng cân nhắc** khi làm việc với dữ liệu nhị phân ổn định, truy cập theo offset nhiều lần hoặc đọc lặp lại tập dữ liệu lớn mà hệ điều hành có thể cache hiệu quả. Nó giúp lập trình truy cập dữ liệu theo bộ nhớ và có thể giảm overhead ở đường đọc phù hợp.

**Memory mapping bất lợi** khi công việc quá nhỏ/đơn giản, chỉ streaming một lần, tập dữ liệu truy cập thiếu tính cục bộ, rất nhạy với page fault hoặc tệp thường xuyên bị thay/resize. Cần tính cả chi phí địa chỉ ảo, RAM/page cache, vòng đời mapping và tính nhất quán.

**Tiêu chí cuối cùng là đo trên workload thật**: so sánh throughput, độ trễ (đặc biệt P95/P99), page faults, CPU, sử dụng bộ nhớ và độ phức tạp vận hành. Chọn mapping khi những số liệu và yêu cầu ứng dụng ủng hộ nó.


</details>

- [Quay lại đầu trang](#back-to-top)
