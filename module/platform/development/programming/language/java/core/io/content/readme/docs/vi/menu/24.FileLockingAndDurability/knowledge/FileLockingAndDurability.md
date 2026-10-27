<a id="back-to-top"></a>

# Khóa và dữ liệu bền

## Menu
- [FileLock, lock và tryLock](#file-locking-and-durability-001)
- [Khóa độc quyền, khóa chia sẻ và vùng byte](#file-locking-and-durability-002)
- [File lock thuộc phạm vi JVM, không thay thế khóa giữa các luồng](#file-locking-and-durability-003)
- [OverlappingFileLockException và giới hạn hỗ trợ của hệ điều hành](#file-locking-and-durability-004)
- [Giải phóng lock khi đóng tài nguyên](#file-locking-and-durability-005)
- [Phân biệt flush, FileDescriptor.sync và FileChannel.force](#file-locking-and-durability-006)
- [Ghi ra tệp tạm rồi thay thế đích](#file-locking-and-durability-007)
- [ATOMIC_MOVE và khả năng không được hỗ trợ](#file-locking-and-durability-008)
- [Tính nguyên tử của đổi tên khác độ bền sau sự cố nguồn điện](#file-locking-and-durability-009)
- [Giới hạn nhất quán trên network file system](#file-locking-and-durability-010)

## <a id="file-locking-and-durability-001">FileLock, lock và tryLock</a>

<details>
<summary>Xem chi tiết</summary>

# FileLock, lock và tryLock trong Java NIO

Khi hai tiến trình cùng đọc hoặc sửa một tệp, mỗi tiến trình có thể ghi những byte hợp lệ nhưng kết quả tổng thể vẫn sai: nội dung bị ghi đè, cập nhật dở dang hoặc một tiến trình đọc giữa lúc tiến trình khác đang thay đổi dữ liệu. `java.nio.channels.FileLock` cung cấp cơ chế **khóa vùng của tệp ở cấp hệ thống tệp** thông qua `FileChannel`. Hai thao tác quan trọng nhất là `lock()` (có thể chờ để lấy khóa) và `tryLock()` (thử lấy ngay).

Hiểu `FileLock` cần phân biệt **khóa độc quyền/chia sẻ**, **xung đột giữa các tiến trình và ngay trong cùng JVM**, **phạm vi byte bị khóa**, **vòng đời khóa**, và giới hạn thực tế của khóa trên từng hệ điều hành. Khóa tệp cũng không tự thay thế đồng bộ giữa các luồng hay bảo đảm dữ liệu đã được ghi bền xuống thiết bị lưu trữ.

## 1. FileLock là gì và dùng khi nào?

`FileLock` mô tả quyền khóa một vùng byte trong tệp mà một `FileChannel` đã mở. Mục tiêu là phối hợp truy cập vào **cùng tệp** giữa các thành phần, thường là các tiến trình khác nhau. Khóa có thể có hai loại:

- **Exclusive lock (khóa độc quyền):** Yêu cầu không có khóa khác xung đột trên cùng vùng. Phù hợp khi một tiến trình đang cập nhật tệp.
- **Shared lock (khóa chia sẻ):** Cho phép các bên cùng giữ khóa chia sẻ nếu hệ thống hỗ trợ, nhưng xung đột với khóa độc quyền trên cùng vùng. Hữu ích khi các bên phối hợp chỉ đọc.

Ví dụ: hai ứng dụng dùng chung `records.dat`. Trước khi cập nhật một bản ghi, chương trình A xin exclusive lock trên vùng byte chứa bản ghi đó. Chương trình B cũng xin exclusive lock cho cùng vùng sẽ phải chờ, thất bại khi thử không chặn, hoặc nhận ngoại lệ tùy trường hợp và API.

Một khóa không tự giải nghĩa các bản ghi, không khôi phục nội dung khi ghi lỗi và không tự kiểm tra tính hợp lệ dữ liệu. Muốn giữ tính nhất quán phải xác định **quy ước tất cả các thành phần cùng tham gia tuân thủ khóa**.

### FileLock không giống khóa luồng Java

`synchronized`, `ReentrantLock` và `FileLock` giải quyết các lớp vấn đề khác nhau:

| Cơ chế | Phạm vi phối hợp | Phù hợp |
| --- | --- | --- |
| `synchronized` hoặc `ReentrantLock` | Các luồng sử dụng cùng quy ước khóa bên trong một JVM | Bảo vệ biến, đối tượng, trình tự thao tác |
| `FileLock` | Khóa tệp qua cơ chế hệ thống, có thể phối hợp nhiều tiến trình | Tránh các tiến trình hợp tác thao tác cùng vùng tệp |
| Ghi file tạm + thay thế có kiểm soát | Quy trình cập nhật toàn bộ file, kiểm soát trạng thái hiển thị | Tránh để file đích bị ghi dở trong một số kịch bản |

Đặc biệt, **khóa tệp được giữ thay cho cả JVM**, không thuộc riêng luồng đã lấy khóa. Vì vậy không dùng `FileLock` làm khóa để điều phối các luồng trong cùng tiến trình. Nhiều luồng của một JVM xin khóa vùng chồng lấn sẽ không đơn giản chờ nhau như `ReentrantLock`.

## 2. Mở FileChannel với quyền phù hợp

Muốn xin khóa bằng `FileChannel`, trước tiên phải mở tệp bằng những `OpenOption` tương thích.

```java
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

// Minh họa trong phương thức xử lý IOException.
Path file = Path.of("records.dat");

try (FileChannel channel = FileChannel.open(
        file,
        StandardOpenOption.READ,
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE)) {
    // Có thể yêu cầu exclusive lock và thực hiện đọc/ghi.
}
```

Đoạn này chỉ minh họa cách mở: `CREATE` tạo tệp nếu chưa tồn tại; `READ + WRITE` cấp quyền đọc/ghi cho channel. `CREATE` không tạo thư mục cha; nếu đường dẫn không hợp lệ hoặc không có quyền, `open` vẫn có thể ném `IOException`.

Quy tắc quan trọng của `FileChannel.lock(position, size, shared)`:

- **Exclusive lock** (`shared=false`) yêu cầu channel được mở để ghi; nếu không, có thể nhận `NonWritableChannelException`.
- **Shared lock** (`shared=true`) yêu cầu channel được mở để đọc; nếu không, có thể nhận `NonReadableChannelException`.
- Trên một số nền tảng, yêu cầu shared lock có thể được hệ điều hành thực hiện dưới dạng exclusive lock. Vì thế sau khi lấy khóa, nên kiểm tra `fileLock.isShared()` nếu ứng dụng phụ thuộc chặt vào loại khóa được cấp.
- Không suy ra rằng mở tệp bằng `READ` sẽ bảo đảm shared lock được hỗ trợ trên tất cả hệ thống.

Trong trường hợp chỉ quản lý tiến trình ghi, mở channel có `WRITE` và xin exclusive lock thường là lựa chọn đơn giản hơn.

## 3. lock(): xin khóa có thể phải chờ

Các overload thường dùng:

```java
FileLock entire = channel.lock();
FileLock range = channel.lock(100, 50, false);
FileLock shared = channel.lock(0, 1024, true);
```

Đây là các dòng minh họa **cho những lần gọi độc lập** trên channel phù hợp; không chạy cả ba nối tiếp trên cùng tệp khi các vùng chồng lấn. Phải giải phóng khóa trước khi xin khóa xung đột.

- `lock()` tương đương yêu cầu exclusive lock trên vùng từ byte 0 với độ dài `Long.MAX_VALUE`. Vùng này bao phủ mọi byte có thể truy cập của tệp theo ý định khóa toàn bộ.
- `lock(position, size, shared)` xin khóa một vùng liên tục bắt đầu ở byte `position`, độ dài `size`; `shared` chỉ loại khóa được yêu cầu.
- Nếu một tiến trình **khác** đã giữ khóa xung đột, `lock()` thường **chặn luồng gọi** chờ cho đến khi khóa có thể được cấp, bị gián đoạn hoặc có lỗi.
- Nếu **chính JVM hiện tại** đã giữ khóa chồng lấn, hoặc có luồng trong JVM đang chờ khóa chồng lấn, yêu cầu có thể ném `OverlappingFileLockException` ngay thay vì chờ.

Không có timeout tích hợp trong chữ ký `lock()`. Nếu tác vụ có deadline hoặc giao diện cần phản hồi nhanh, cần thiết kế timeout/hủy và điều phối phù hợp; không nên gọi `lock()` trên luồng UI hay event loop không được phép chặn.

Một cách viết an toàn khi muốn giữ exclusive lock trong suốt việc cập nhật:

```java
// Minh họa, file là Path hợp lệ; phương thức cho phép ném IOException.
try (FileChannel channel = FileChannel.open(
        file, StandardOpenOption.READ, StandardOpenOption.WRITE);
     FileLock ignored = channel.lock()) {
    // Thực hiện chuỗi thao tác cập nhật đã được thiết kế.
    // Khóa được giải phóng tự động khi rời try.
}
```

Khối `try-with-resources` giúp tránh quên `release()` khi cập nhật ném ngoại lệ. Tên biến `ignored` chỉ có nghĩa không cần dùng đối tượng khóa trong thân khối; nó vẫn là tài nguyên giữ khóa.

## 4. tryLock(): thử không chờ

Đối với code cần biết ngay khóa có sẵn hay không:

```java
FileLock lock = channel.tryLock();
```

`tryLock()` mặc định xin exclusive lock toàn bộ tệp; `tryLock(position, size, shared)` cho phép xác định vùng và loại khóa. Kết quả gồm:

| Trạng thái | `tryLock()` |
| --- | --- |
| Lấy khóa thành công | Trả về `FileLock` hợp lệ; caller phải giải phóng |
| Một **tiến trình khác** đang giữ khóa xung đột | Trả về `null` thay vì chờ |
| JVM hiện tại đã giữ hoặc đang chờ khóa chồng lấn | Ném `OverlappingFileLockException` |
| Quyền kênh/option không phù hợp | Ném ngoại lệ như `NonWritableChannelException` hoặc `NonReadableChannelException` |
| Kênh đã đóng, lỗi hệ thống tệp hoặc provider không hỗ trợ | Ngoại lệ tương ứng, có thể là `IOException` |

**Đừng viết `channel.tryLock().release()`**: kết quả có thể là `null`. Cũng không nên bắt `OverlappingFileLockException` rồi coi đó là lỗi từ một tiến trình khác; nó chủ yếu báo **xung đột khóa ngay trong JVM hiện tại**.

Mẫu thường dùng:

```java
// Minh họa trong phương thức xử lý IOException.
FileLock acquired = channel.tryLock();
if (acquired == null) {
    System.out.println("File is busy; retry later");
} else {
    try (acquired) {
        // Truy cập dữ liệu cần khóa.
    }
}
```

Ở đây `acquired` là biến được gán một lần nên đủ điều kiện làm tài nguyên trong `try (acquired)` của Java 9+. Với `OverlappingFileLockException`, đoạn code sẽ để ngoại lệ truyền ra ngoài vì đó là tình huống điều phối trong cùng JVM cần xử lý riêng.

### Có nên thử lại nhiều lần?

Trong ứng dụng cần thử lại, nên đặt số lần thử tối đa, deadline, khoảng chờ hoặc exponential backoff và cơ chế hủy. Một vòng `while (channel.tryLock() == null) { }` liên tục có thể chiếm CPU, và còn không xử lý `OverlappingFileLockException`. Nếu file được cập nhật liên tục bởi nhiều tiến trình, một chiến lược retry tốt cũng cần ngăn tình trạng đói tài nguyên (starvation).

## 5. Vùng byte: vị trí và độ dài khóa

Hai đối số `position` và `size` mô tả **vùng byte của file**, không phải `channel.position()` hiện tại và cũng không phải `ByteBuffer.position()`.

Ví dụ:

```text
File:    |--------- bytes 0..99 ---------|---- 100..149 ----|---- 150..199 ----|
Lock A:                                    [100, 150)
Lock B:                                                   [150, 200)
Lock C:                                           [120, 140)
```

- `lock(100, 50, false)` xin khóa các byte từ 100 đến dưới 150 (vùng nửa mở `[100,150)`).
- `lock(150, 50, false)` xin vùng kế tiếp: **không chồng lấn theo tọa độ byte** với vùng trước.
- `lock(120, 20, false)` chồng lấn với khóa đầu tiên và sẽ bị xem là xung đột tùy loại khóa cũng như JVM/tiến trình.
- Hai vùng khóa có thể nằm **sau EOF hiện tại**; vị trí và độ dài vùng khóa không làm tệp tự tăng kích thước.
- Các giá trị âm, phạm vi không hợp lệ hoặc phép cộng `position + size` vượt khả năng biểu diễn được yêu cầu API xử lý bằng ngoại lệ như `IllegalArgumentException`.

Với tệp cố định gồm nhiều bản ghi có cùng kích thước `recordSize`, có thể chọn vùng khóa cho bản ghi `index`:

```java
// Minh họa trong phương thức xử lý IOException.
long offset = Math.multiplyExact(index, recordSize);
try (FileLock recordLock =
        channel.lock(offset, recordSize, false)) {
    // Chỉ cập nhật bản ghi tương ứng.
}
```

Đoạn này giả định `index` và `recordSize` không âm, `recordSize > 0`, channel có quyền ghi và dữ liệu được truy cập bằng quy ước offset chính xác; `Math.multiplyExact` giúp phát hiện tràn trong phép nhân. Còn phải kiểm tra `offset + recordSize` không vượt giới hạn số nguyên dài và tệp có dữ liệu hợp lệ nếu muốn đọc bản ghi đã có.

**Không nên phụ thuộc tuyệt đối vào khóa vùng có độ hạt nhỏ trên mọi nền tảng.** Hệ thống tệp, provider hoặc cách filesystem thực thi locking có thể ảnh hưởng khả năng cấp khóa vùng và cách các ứng dụng ngoài JVM tuân thủ chúng. Khi ứng dụng cần tương thích rộng, hãy kiểm thử cơ chế này trên môi trường triển khai.

## 6. Chương trình Java 17+ hoàn chỉnh: lock, tryLock và xung đột trong một JVM

Ví dụ sau tạo file tạm mới rồi mở **hai FileChannel độc lập trỏ tới cùng file trong một JVM**. Kênh thứ nhất lấy exclusive lock. Kênh thứ hai gọi `tryLock()` khi vùng đã bị khóa, giúp quan sát rằng xung đột **cùng JVM** được báo bằng `OverlappingFileLockException`, không phải `null`. Khi khóa đầu tiên được giải phóng, kênh thứ hai có thể lấy khóa thành công.

Tạo file **`FileLockDemo.java`**:

```java
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class FileLockDemo {
    private FileLockDemo() {}

    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("file-lock-demo-", ".bin");
        try {
            try (FileChannel first = FileChannel.open(file,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE);
                 FileChannel second = FileChannel.open(file,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE)) {

                FileLock holder = first.lock();
                try {
                    System.out.println("first valid=" + holder.isValid());
                    System.out.println("first shared=" + holder.isShared());

                    try (FileLock unexpected = second.tryLock()) {
                        // Xung đột trong cùng JVM phải được báo bằng exception.
                        throw new AssertionError("Unexpected overlapping lock");
                    } catch (OverlappingFileLockException expected) {
                        System.out.println("same JVM overlap=exception");
                    }
                } finally {
                    holder.release();
                }

                System.out.println("first after release="
                        + holder.isValid());

                try (FileLock acquired = second.tryLock()) {
                    if (acquired == null) {
                        throw new IOException("File locked by another process");
                    }
                    System.out.println("second acquired=" + acquired.isValid());
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
javac FileLockDemo.java
java FileLockDemo
```

Kết quả trong môi trường tệp tạm cục bộ hỗ trợ file locking và không có tác nhân bên ngoài can thiệp:

```text
first valid=true
first shared=false
same JVM overlap=exception
first after release=false
second acquired=true
```

Giải thích:

1. `Files.createTempFile` tạo tên tệp mới trong thư mục tạm. Hai kênh dùng cùng đường dẫn nên hai yêu cầu khóa xét cùng file.
2. `first.lock()` xin exclusive lock vùng toàn bộ; `holder.isValid()` là `true` và `holder.isShared()` là `false`.
3. `second.tryLock()` xin exclusive lock chồng lấn trong cùng JVM nên ném `OverlappingFileLockException`, được bắt riêng.
4. `holder.release()` giải phóng khóa; `isValid()` trở thành `false`.
5. Sau giải phóng, `second.tryLock()` lấy exclusive lock. Khối `try-with-resources` đóng đối tượng `FileLock` và các channel theo đúng thứ tự tài nguyên; cuối cùng xóa file tạm.

Ví dụ này **không chứng minh `tryLock()` trả `null` trong xung đột liên tiến trình**. Muốn kiểm thử nhánh đó, cần **hai tiến trình JVM riêng biệt**: tiến trình thứ nhất giữ khóa, tiến trình thứ hai thử khóa cùng vùng khi tiến trình đầu còn giữ. Trên hệ thống hỗ trợ locking phù hợp, lần thử trong tiến trình thứ hai trả `null`. Không giả lập việc này bằng hai `FileChannel` trong cùng JVM rồi chờ `null`.

## 7. Giải phóng khóa và quản lý vòng đời

Đối tượng `FileLock` cung cấp:

| API | Ý nghĩa |
| --- | --- |
| `position()` | Vị trí bắt đầu của vùng đã xin khóa |
| `size()` | Độ dài vùng khóa |
| `isShared()` | Cho biết khóa được cấp là shared hay exclusive |
| `isValid()` | Có còn hiệu lực theo trạng thái API hay không |
| `release()` | Yêu cầu giải phóng khóa |
| `close()` | Là phương thức `AutoCloseable` giúp giải phóng trong `try-with-resources` |
| `acquiredBy()` | Trả về channel đã cấp khóa |

Một khóa hợp lệ tồn tại đến khi được `release()`, channel chứa nó bị đóng hoặc JVM kết thúc (tùy cơ chế giải phóng hệ thống). `FileLock.isValid()` dùng kiểm tra trạng thái đã biết, nhưng không phải cam kết bất biến nếu tài nguyên bị đóng đồng thời.

Cần đặc biệt lưu ý: trên **một số hệ điều hành**, đóng *bất kỳ channel nào* tới cùng file có thể giải phóng **tất cả khóa mà JVM đang giữ trên file đó**, kể cả các khóa được lấy bằng channel khác. Do đó không mở/đóng nhiều channel vào cùng file một cách tùy tiện trong khi đang dựa vào khóa của một channel khác. Nếu nghiệp vụ nhạy cảm, quản lý quyền sở hữu file/channel và thời gian giữ khóa thật rõ ràng, đồng thời kiểm thử trên môi trường chạy.

Mẫu đóng khóa nên ưu tiên:

```java
// Minh họa: file là Path; cần IOException handling.
try (FileChannel channel = FileChannel.open(file,
        StandardOpenOption.READ, StandardOpenOption.WRITE);
     FileLock lock = channel.lock()) {
    // Truy cập tài nguyên được phối hợp theo quy ước.
}
// Lock được giải phóng, channel được đóng.
```

Tuy nhiên, nếu code trong phạm vi khóa tạo thêm view/mapping hoặc truyền tài nguyên cho luồng khác, cần bảo đảm không để luồng khác tiếp tục thao tác sau khi khóa đã được giải phóng.

## 8. Ngoại lệ và tình huống chờ khóa

| Tình huống | Phản ứng API |
| --- | --- |
| Một khóa vùng trùng trong cùng JVM | `OverlappingFileLockException` |
| `lock()` phải đợi tiến trình khác | Luồng có thể chặn đến khi khóa được cấp hoặc có lỗi |
| `tryLock()` gặp khóa xung đột ở tiến trình khác | `null` |
| Exclusive lock từ channel không cho ghi | `NonWritableChannelException` |
| Shared lock từ channel không cho đọc | `NonReadableChannelException` |
| Luồng bị interrupt trong khi `lock()` đang chờ | Có thể ném `FileLockInterruptionException` |
| Channel bị đóng trong khi luồng đang chờ lock | Có thể ném `AsynchronousCloseException` |
| File/channel gặp lỗi I/O | `IOException` hoặc lớp con phù hợp |
| Tọa độ vùng khóa không hợp lệ | `IllegalArgumentException` |
| Channel đã đóng trước thao tác | Thường `ClosedChannelException` |

Với tác vụ nền, sự gián đoạn và đóng channel cần được xử lý có chủ đích. Tránh bắt mọi exception rồi ghi log "file đang bận": `OverlappingFileLockException` là dấu hiệu quản lý khóa trong JVM; `IOException` có thể là lỗi thiết bị hay mất quyền; còn `null` từ `tryLock()` mới là trường hợp thử không chặn bị một tiến trình khác giữ khóa theo hợp đồng API.

Nếu cần chờ hữu hạn thay vì chờ vô hạn, một vòng thử theo deadline trên `tryLock()` có thể dễ điều khiển hơn `lock()`, nhưng phải giảm tần suất thử, xử lý interrupt và phân biệt rõ null/exception.

## 9. Giới hạn của FileLock trong thực tế

### 9.1. Khóa advisory và mandatory

Không phải mọi hệ thống bắt buộc các thao tác đọc/ghi thông thường phải tuân thủ `FileLock`. Có hệ thống thực thi **advisory locks**: chỉ các chương trình cùng tham gia quy ước khóa mới tránh xung đột. Có hệ thống hỗ trợ hoặc dùng **mandatory locks** trong một số chế độ. Java không hứa một hành vi cưỡng chế giống nhau trên tất cả provider/file system.

Hệ quả: nếu ứng dụng A giữ exclusive lock nhưng một công cụ khác bỏ qua quy ước khóa và ghi trực tiếp, hành vi có thể khác tùy môi trường. **Không dựa vào FileLock như hàng rào bảo mật bắt buộc** chống mọi ứng dụng khác; cần phân quyền file, giao thức sở hữu và kiểm thử thực tế.

### 9.2. Không phải transaction, không bảo đảm atomicity hay durability

Lấy khóa thành công **không tự**:

- Kết hợp nhiều lệnh `channel.write()` thành một giao dịch không thể bị chia nhỏ.
- Ngăn hệ điều hành hoặc thiết bị gặp lỗi khi ghi.
- Phục hồi dữ liệu khi tiến trình chết giữa chừng.
- Đảm bảo dữ liệu đã xuống bộ nhớ bền vững của thiết bị.

Đối với dữ liệu quan trọng, ngoài quy ước khóa có thể phải dùng `FileChannel.force(boolean)` theo yêu cầu durability, ghi file tạm rồi thay thế với chính sách riêng, hoặc cấu trúc ghi nhật ký có khả năng khôi phục. `force` cũng có phạm vi cam kết tùy thiết bị/provider; nó không thay thế việc quản lý khóa và khôi phục lỗi.

### 9.3. Khóa tệp và memory-mapped buffer

`FileChannel.map()` tạo ánh xạ tệp vào bộ nhớ; các thao tác truy cập vùng được ánh xạ có thể có hành vi tương tác với file locks phụ thuộc hệ điều hành. **Không mặc định khóa FileLock sẽ ngăn mọi thao tác đọc/ghi vào vùng mapping trên tất cả nền tảng.** Nếu ứng dụng dùng cả mapping và locking, phải thiết kế/kiểm thử giao thức đồng bộ riêng.

### 9.4. NFS, SMB, container và giới hạn hệ điều hành

File locking phụ thuộc provider, khả năng filesystem và đôi khi cả cấu hình mạng. Trên network filesystem hoặc môi trường nhiều container, cách thực thi và xử lý mất kết nối có thể khác với đĩa cục bộ. Phải kiểm tra trên đúng hạ tầng sử dụng, nhất là khi khóa dùng như cơ chế bảo vệ job chạy độc quyền hoặc dữ liệu ghi bởi nhiều máy.

Giữ khóa quá lâu cũng có thể giảm throughput và dẫn đến deadlock nghiệp vụ nếu nhiều file/lock được xin theo những thứ tự khác nhau. Khi cần khóa nhiều tài nguyên, hãy chọn thứ tự cố định, phạm vi nhỏ nhất đủ an toàn và luôn giải phóng qua `try-with-resources`.

## 10. Cách dùng FileLock để đảm bảo một tiến trình chủ sở hữu

Một trường hợp thường gặp là chỉ cho một phiên xử lý cập nhật một file dữ liệu tại một thời điểm. Ví dụ logic:

1. Mở channel với `READ/WRITE` phù hợp, bảo đảm file lock có vị trí lưu trữ dùng chung giữa các tiến trình cần phối hợp.
2. Gọi `tryLock()`. Nếu nhận `null`, ứng dụng có thể dừng hoặc lên lịch thử lại thay vì chờ.
3. Nếu nhận `FileLock`, giữ khóa trong toàn bộ khoảng đọc, xác minh, cập nhật và kiểm tra kết quả liên quan.
4. Trong `finally` hoặc `try-with-resources`, giải phóng khóa và đóng channel. Ghi nhận riêng lỗi I/O thay vì bỏ qua.

Mẫu này đòi hỏi mọi tiến trình tham gia dùng chung một quy ước locking. Nếu không bảo đảm được cùng đường dẫn vật lý/file system hoặc cùng hành vi provider, một distributed lock hoặc cơ chế điều phối ngoài file có thể cần thiết.

Một **lock file** riêng (ví dụ `worker.lock`) có thể giúp tránh đụng trực tiếp file dữ liệu, nhưng quy trình không được **xóa lock file rồi tạo lại** khi một tiến trình khác đang giữ khóa cũ: việc xóa/tạo lại có thể dẫn tới hai file object khác nhau tại cùng tên đường dẫn, phá giả định tất cả bên đang khóa cùng một đối tượng. Hãy phân biệt file tồn tại trên đường dẫn và khóa OS đang giữ trên file object.

## 11. Lỗi phổ biến và cách tránh

| Lỗi | Hệ quả | Cách sửa |
| --- | --- | --- |
| Chờ `tryLock()` như thể đó là `lock()` | Không chờ; kết quả null nếu tiến trình khác giữ khóa | Xử lý null hoặc dùng `lock()` khi thật sự muốn chờ |
| Cho rằng cùng JVM giữ khóa chồng lấn sẽ khiến `tryLock()` trả null | Nhận `OverlappingFileLockException` | Điều phối bằng khóa luồng Java, phân biệt lỗi nội JVM |
| Dùng `FileLock` thay `ReentrantLock` trong đa luồng | Race/deadlock nghiệp vụ, exception vùng chồng lấn | Dùng cơ chế đồng bộ luồng riêng |
| Xin exclusive lock trên read-only channel | `NonWritableChannelException` | Mở channel có quyền WRITE |
| Nghĩ shared lock chắc chắn được hỗ trợ | Khóa thực tế có thể là exclusive | Kiểm tra `isShared()` |
| Quên `release()` hay không đóng channel | Giữ khóa/tài nguyên quá lâu | `try-with-resources` |
| Giữ một khóa rồi tự do đóng channel khác tới cùng tệp | Một số hệ thống có thể giải phóng khóa ngoài ý muốn | Tập trung quản lý channel và khóa |
| Khóa một vùng nhưng ghi ngoài vùng đó | Dữ liệu ngoài phạm vi khóa không được bảo vệ theo quy ước | Tính offset/size rõ ràng và kiểm tra nghiệp vụ |
| Tin rằng FileLock bắt buộc mọi phần mềm khác tuân theo | Tiến trình không hợp tác vẫn có thể sửa tệp trên hệ thống advisory | Phân quyền và cùng quy ước locking |
| Tin rằng khóa = ghi bền | Crash vẫn có thể làm mất/cắt dở dữ liệu | Tách locking, force, ghi tạm và recovery |
| Spin vòng lặp gọi `tryLock()` khi bận | Tiêu tốn CPU và có thể gây starvation | Deadline, retry/backoff và cơ chế hủy |

**Ghi nhớ:** `FileChannel.lock()` yêu cầu khóa và có thể chờ tiến trình khác; `tryLock()` trả ngay khi không thể lấy khóa từ tiến trình khác, nhưng vẫn có thể ném `OverlappingFileLockException` khi chồng lấn trong cùng JVM. `FileLock` bảo vệ **vùng byte theo quy ước locking của hệ thống**, cần quản lý quyền mở channel, loại khóa, độ dài vùng khóa, lỗi, vòng đời và phối hợp giữa các tiến trình.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-locking-and-durability-002">Khóa độc quyền, khóa chia sẻ và vùng byte</a>

<details>
<summary>Xem chi tiết</summary>

# Khóa độc quyền, khóa chia sẻ và vùng byte với FileChannel (Java NIO.2)

Khi hai tiến trình cùng đọc/ghi một tệp, việc thao tác tại đúng offset **không tự bảo vệ nội dung trước cập nhật đồng thời**. Java NIO cung cấp `FileChannel.lock(...)`, `tryLock(...)` và `FileLock` để yêu cầu khóa một **vùng byte** của tệp, có thể ở chế độ **độc quyền (exclusive)** hoặc **chia sẻ (shared)**. Những khóa này chủ yếu dùng để **phối hợp giữa các tiến trình** thông qua cơ chế khóa tệp của hệ điều hành.

Điểm quan trọng nhất: **FileLock không phải khóa dùng để điều phối các thread trong cùng JVM; không phải transaction; không phải lời cam kết dữ liệu đã được ghi bền vững.** Muốn hệ thống đúng đắn, cần hiểu phạm vi vùng byte, cách hệ điều hành thực thi khóa, thời điểm giữ/thả khóa và mối quan hệ giữa khóa, nội dung file, `force` và giao thức ứng dụng.

Sau bài này, bạn có thể xác định lúc nào cần shared/exclusive lock, cách chia vùng byte, đọc hiểu các kết quả và exception của `lock/tryLock`, viết code Java 17+ có vòng đời khóa rõ ràng, và nhận diện những giới hạn của việc dùng file locking để bảo vệ dữ liệu.

## 1. Khóa tệp bảo vệ điều gì?

Giả sử hai ứng dụng cùng cập nhật file `counter.dat` có một số nguyên. Mỗi ứng dụng đều thực hiện “đọc số hiện tại → cộng một → ghi lại”. Nếu chúng đọc cùng giá trị `41`, cả hai có thể ghi `42` thay vì kết quả mong muốn `43`. Đó là **lost update**. Việc gọi `FileChannel.write` thành công không giải quyết tranh chấp nghiệp vụ này.

Một giao thức khóa phù hợp có thể yêu cầu:

1. Mỗi tiến trình xin **exclusive lock** trên vùng chứa counter.
2. Chỉ sau khi giữ được lock mới đọc giá trị hiện tại.
3. Tính toán và ghi giá trị mới trong lúc vẫn giữ lock.
4. Nếu yêu cầu độ bền, gọi `channel.force(...)` đúng điểm cam kết.
5. Giải phóng lock; tiến trình khác mới được vào vùng cập nhật theo cùng giao thức.

Với tác vụ chỉ đọc, nhiều tiến trình có thể cùng giữ **shared lock** trên cùng vùng nếu nền tảng hỗ trợ, với điều kiện writer xin exclusive lock và tất cả đều tôn trọng giao thức.

Tuy nhiên, mức độ hệ điều hành **thực sự ngăn truy cập** khác nhau: trên nền tảng có **advisory lock**, tiến trình không tham gia giao thức vẫn có thể mở hoặc thay đổi file; trên hệ thống có **mandatory lock**, một số thao tác trái khóa có thể bị ngăn. Java khuyến nghị **luôn thiết kế như khóa là advisory**, dù chạy trên OS nào.

## 2. Shared lock và exclusive lock

| Tính chất | Exclusive lock | Shared lock |
| --- | --- | --- |
| Mục đích phổ biến | Phối hợp writer hoặc thao tác đọc–sửa–ghi | Phối hợp các reader đồng thời |
| Đối tượng xung đột | Một exclusive lock ngăn các khóa shared/exclusive chồng vùng được cấp cho tiến trình khác theo cơ chế OS | Nhiều shared lock chồng vùng có thể cùng tồn tại; yêu cầu exclusive chồng vùng sẽ xung đột |
| Ký hiệu tham số | `shared=false` | `shared=true` |
| Điều kiện mở channel | Channel phải cho phép **WRITE** | Channel phải cho phép **READ** |
| Tính di động | Phụ thuộc OS/filesystem hỗ trợ khóa | Có nền tảng không hỗ trợ shared; Java có thể **chuyển yêu cầu sang exclusive** |
| Cách biết loại khóa thực nhận | `lock.isShared() == false` | Kiểm tra `lock.isShared()`, không giả định luôn `true` |

Ví dụ tưởng tượng ba tiến trình truy cập cùng vùng `[0, 100)`:

- P1 đang giữ shared. P2 xin shared trên cùng vùng: **có thể** cùng được cấp nếu OS/provider cho phép shared.
- P1 đang giữ shared. P3 xin exclusive vùng chồng lấn: phải chờ, thất bại ngay với `tryLock` hoặc gặp lỗi tùy điều kiện.
- P1 đang giữ exclusive. P2/P3 xin khóa chồng lấn, dù shared hay exclusive: không được đồng thời giữ cùng kiểu xung đột.

Đây là mô hình giao thức giữa các tiến trình. **Không** dùng bảng trên để kết luận một JVM được phép tạo hai `FileLock` chồng vùng: Javadoc cấm các lock giữ trong **cùng JVM** chồng lấn nhau, kể cả hai yêu cầu đều shared. Khi có lock chồng lấn đã giữ hoặc request chồng vùng đang đợi trong cùng JVM, API ném `OverlappingFileLockException`.

Nếu dùng shared lock cho reader, nhớ rằng **không phải mọi reader đều tự động xin khóa**: `Files.readAllBytes(path)` hoặc `FileChannel.read(...)` không “đi xin shared lock” hộ bạn. Phối hợp chỉ đáng tin khi tất cả bên liên quan dùng và tuân thủ cùng một quy tắc.

## 3. Vùng khóa là khoảng byte, không phải toàn file mặc định

Chữ ký chính:

```java
FileLock lock(long position, long size, boolean shared) throws IOException;
FileLock tryLock(long position, long size, boolean shared) throws IOException;
```

- `position` là **byte offset bắt đầu**, từ `0`; phải không âm.
- `size` là số byte trong vùng; phải không âm và `position + size` không được tràn sang giá trị âm.
- Với `size > 0`, lock bảo vệ vùng nửa mở **`[position, position + size)`**. Byte ở điểm cuối **không thuộc** vùng.
- `size == 0` có ý nghĩa đặc biệt: yêu cầu khóa từ `position` đến hết không gian file theo hợp đồng API, kể cả khi tệp mở rộng hoặc thu ngắn sau đó. Khóa được trả về có kích thước `Long.MAX_VALUE - position`.
- Một vùng khóa **không bắt buộc nằm trong kích thước hiện tại của file**. Ví dụ file có 8 byte vẫn có thể xin khóa vùng `[100, 200)` trên filesystem hỗ trợ.
- Vùng có kích thước dương là **cố định** sau khi cấp. Nếu khóa `[0, 100)`, sau đó tệp được mở rộng từ 20 byte lên 500 byte, phần `[100, 500)` nằm ngoài vùng khóa đã lấy.

Ví dụ các vùng:

| Khóa đã giữ | Vùng muốn xin tiếp | Chồng lấn? | Giải thích |
| --- | --- | --- | --- |
| `[0, 10)` | `[5, 15)` | Có | Chung các byte `5..9` |
| `[0, 10)` | `[10, 20)` | Không | Hai vùng tiếp giáp; điểm cuối `10` nằm ngoài vùng đầu |
| `[100, 200)` | `[0, 100)` | Không | Điểm kết thúc vùng thứ hai đúng điểm đầu vùng thứ nhất |
| `[100, 200)` | `[199, 201)` | Có | Chung byte `199` |
| `[50, 60)` | `[55, 56)` | Có | Vùng nhỏ nằm trong vùng lớn |

`FileLock.overlaps(position, size)` kiểm tra hình học chồng lấn giữa vùng của một **đối tượng FileLock** và vùng được truyền vào. Nó **không cấp thêm lock**, không kiểm tra các process khác và cũng không nói rằng lock vẫn hợp lệ: muốn kiểm tra trạng thái của chính lock, dùng `isValid()`.

### Phân biệt byte offset và record

Nếu mỗi record có kích thước cố định 16 byte, record thứ `i` (đếm từ `0`) nằm ở `[i * 16, (i + 1) * 16)`. Khóa một record dùng:

```java
// Đoạn minh họa: channel phải mở đúng quyền.
long offset = Math.multiplyExact(recordIndex, 16L);
try (FileLock lock = channel.lock(offset, 16, false)) {
    // Đọc/sửa/ghi record tại offset trong khi lock còn hiệu lực.
}
```

`recordIndex` phải không âm và phép nhân không được tràn số. Kích thước record theo **byte**, không tính bằng số ký tự Unicode. Với record biến độ dài, phải có bảng offset/length hoặc framing rõ ràng; khóa 16 byte khi record thực chiếm 50 byte sẽ không bảo vệ toàn bộ record.

Với file có thể mở rộng không dự đoán trước, cần chọn chiến lược khóa bao phủ vùng sắp thêm hoặc dùng một khóa điều phối metadata/offset riêng. **Không nên tin rằng khóa cũ tự mở rộng khi file lớn lên**, trừ trường hợp xin vùng tới EOF bằng `size=0` theo hợp đồng.

## 4. `FileChannel.lock` và `tryLock` khác nhau thế nào?

| API | Cách xin khóa | Khi có tiến trình khác giữ khóa chồng lấn | Khi có khóa chồng lấn trong cùng JVM |
| --- | --- | --- | --- |
| `channel.lock()` | Chờ để xin **exclusive lock** cho cả miền file (`0` đến `Long.MAX_VALUE`) | Có thể **chờ** tới khi cấp được, channel đóng hoặc thread bị interrupt | `OverlappingFileLockException` |
| `channel.lock(p, n, shared)` | Chờ xin khóa đúng vùng/loại yêu cầu | Có thể chờ | `OverlappingFileLockException` |
| `channel.tryLock()` | Thử ngay exclusive lock toàn file | Trả **`null`** khi xung đột với khóa của tiến trình khác | `OverlappingFileLockException` |
| `channel.tryLock(p, n, shared)` | Thử ngay khóa vùng/loại được yêu cầu | Trả **`null`** khi xung đột với khóa của tiến trình khác | `OverlappingFileLockException` |

`tryLock` thường hữu ích khi ứng dụng muốn báo “tệp đang được sử dụng” hoặc lên lịch thử sau, tránh đợi vô hạn. Nó **không phải** lời hứa sẽ luôn trả trong thời gian xác định nếu chính hệ thống tệp/OS gặp trục trặc, và vẫn có thể ném exception cho những lỗi không phải xung đột bình thường.

`lock` có thể chờ khi process khác giữ khóa xung đột. Nếu luồng đang chờ bị interrupt, `FileLockInterruptionException` có thể được ném; nếu channel bị đóng bởi thread khác, có thể gặp `AsynchronousCloseException`. Khi thiết kế server, đừng giữ một worker thread chờ lock vô thời hạn nếu độ trễ có SLA; cân nhắc `tryLock` với deadline/backoff do ứng dụng quản lý hoặc một cơ chế quản lý tài nguyên phù hợp.

**Điều kiện quyền:**

- Xin shared lock với channel chỉ mở `WRITE` có thể ném `NonReadableChannelException`.
- Xin exclusive lock với channel chỉ mở `READ` có thể ném `NonWritableChannelException`.
- Cần thêm quyền truy cập và policy OS phù hợp; `AccessDeniedException`/`IOException` có thể phát sinh, kể cả khi mode Java đúng.
- Một số filesystem/provider không hỗ trợ cơ chế cần thiết hoặc có giới hạn về phạm vi. Tránh khẳng định “mọi hệ điều hành chắc chắn cho phép mọi vùng”.

## 5. Ví dụ Java 17+ kiểm chứng vùng byte và khóa chồng lấn trong một JVM

Chương trình tạo file tạm 8 byte, mở hai `FileChannel` riêng đến **cùng file**, yêu cầu exclusive lock trên `[0, 4)`, kiểm tra vùng chồng lấn, và thử thêm khóa tại `[4, 8)`.

Tạo file `RegionLockDemo.java`:

```java
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public class RegionLockDemo {
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("region-lock-", ".dat");
        try {
            Files.writeString(file, "abcdefgh", StandardCharsets.US_ASCII);
            try (FileChannel first = FileChannel.open(file,
                         StandardOpenOption.READ, StandardOpenOption.WRITE);
                 FileChannel second = FileChannel.open(file,
                         StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                try (FileLock exclusive = first.lock(0, 4, false)) {
                    System.out.println("exclusive=" + !exclusive.isShared());
                    System.out.println("overlaps-3-5=" + exclusive.overlaps(3, 2));
                    System.out.println("overlaps-4-8=" + exclusive.overlaps(4, 4));
                    try {
                        second.tryLock(2, 2, false);
                        throw new AssertionError("Unexpected overlapping lock");
                    } catch (OverlappingFileLockException expected) {
                        System.out.println("same-jvm-overlap=rejected");
                    }
                    try (FileLock adjacent = second.lock(4, 4, false)) {
                        System.out.println("adjacent-lock=" + adjacent.isValid());
                    }
                }
                try (FileLock shared = first.lock(0, 4, true)) {
                    System.out.println("shared-request-acquired=" + shared.isValid());
                    // shared.isShared() có thể false nếu OS chuyển thành exclusive.
                }
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Biên dịch/chạy trên Java 17+ với local filesystem cho phép khóa:

```bash
javac --release 17 RegionLockDemo.java
java RegionLockDemo
```

Kết quả đã kiểm chứng:

```text
exclusive=true
overlaps-3-5=true
overlaps-4-8=false
same-jvm-overlap=rejected
adjacent-lock=true
shared-request-acquired=true
```

Giải thích:

1. `first.lock(0, 4, false)` giữ exclusive lock vùng `[0, 4)`; `isShared() == false`.
2. `overlaps(3, 2)` thử vùng `[3, 5)`, giao vùng đầu ở byte `3` nên `true`. `overlaps(4, 4)` thử vùng `[4, 8)`, không chung byte nên `false`.
3. `second.tryLock(2, 2, false)` là yêu cầu chồng lên khóa của cùng JVM; ném `OverlappingFileLockException`, **không trả `null`**. Điều này đúng dù dùng hai channel riêng.
4. `second.lock(4, 4, false)` không chồng vùng nên trong môi trường đã kiểm chứng có thể được cấp. Khi rời khối `try-with-resources`, cả hai khóa được thả.
5. Sau khi thả exclusive lock, chương trình yêu cầu shared lock `[0, 4)`. Dòng `shared-request-acquired=true` chỉ xác nhận lock nhận được đang hợp lệ; **không khẳng định `isShared() == true`** trên mọi OS. Một số nền tảng có thể đổi shared thành exclusive.

Kết quả ví dụ được ghi nhận trên file system hỗ trợ các thao tác tương ứng. Ở file store không hỗ trợ hoặc có chính sách riêng, thao tác có thể thất bại với lỗi I/O/quyền; phần còn lại của bài giải thích cách xử lý. Chương trình cố ý không tạo hai shared lock chồng vùng trong cùng JVM, vì như vậy sẽ **vi phạm hợp đồng Java**.

## 6. Vòng đời FileLock: lấy, kiểm tra, thả và đóng

`FileLock` là một đối tượng đại diện cho khóa đã cấp, triển khai `AutoCloseable`. Nó có các phương thức:

- `position()`: vị trí byte bắt đầu.
- `size()`: kích thước phạm vi khóa đã cấp; với tham số `size=0`, đối tượng có thể biểu diễn phạm vi tới điểm kết thúc miền file theo hợp đồng.
- `isShared()`: loại khóa **thực tế**, không nhất thiết bằng tham số yêu cầu trên môi trường không hỗ trợ shared.
- `isValid()`: khóa còn hiệu lực theo đối tượng Java không.
- `overlaps(p, n)`: kiểm tra phạm vi chồng lấn về mặt hình học.
- `release()` / `close()`: nhả khóa; rời khối `try-with-resources` tự đóng lock.

Một lock lúc mới cấp là valid; nó mất hiệu lực khi `release`, channel sở hữu được đóng, hoặc JVM kết thúc. **Giữ reference `FileLock` trong collection không làm lock sống lâu hơn channel**. Nếu chương trình thoát quá sớm khỏi block khóa, writer khác có thể bắt đầu thao tác trước khi bạn hoàn tất logic.

Mẫu vận dụng:

```java
// Đoạn minh họa: channel mở READ + WRITE, start/length đã được kiểm tra.
try (FileLock lock = channel.lock(start, length, false)) {
    // Chỉ sau khi lock thành công mới được đọc/sửa/ghi record.
    updateRecord(channel, start);
    // Có thể gọi channel.force(true) tại điểm commit nếu ứng dụng cần durability.
} // close() / release() bảo đảm nhả lock kể cả khi thao tác ném exception.
```

Đoạn trên **không phải source độc lập**: `updateRecord` là hàm nghiệp vụ do ứng dụng định nghĩa. Khi dùng `tryLock`, phải xét kết quả `null` **trước** khi truyền vào `try-with-resources` hoặc dùng lock:

```java
// Đoạn minh họa cho channel và recordRange đã có.
FileLock lock = channel.tryLock(start, length, false);
if (lock == null) {
    // Tiến trình khác đang giữ khóa xung đột: trả retry/busy hợp lý.
} else {
    try (lock) {
        // Thực hiện đơn vị công việc cần khóa.
    }
}
```

Đóng **một channel đang sở hữu FileLock** sẽ làm các lock thuộc channel đó không còn hiệu lực. Đáng chú ý, Javadoc cảnh báo trên một số nền tảng, **đóng một channel có thể làm mất các khóa khác của JVM trên cùng file**, kể cả khóa lấy từ channel khác. Vì vậy, khi dùng khóa để phối hợp các process, nên dùng **một channel được quản lý rõ vòng đời để giữ lock** cho file đó, tránh mở/đóng các channel khác không kiểm soát trong lúc khóa vẫn cần hiệu lực. Cũng không nên trông cậy vào finalizer hoặc GC để nhả khóa đúng thời điểm; nhả rõ bằng `try-with-resources`.

### Cần giữ khóa trong bao lâu?

Khóa cần bao trùm **tất cả thao tác thuộc transaction nghiệp vụ** cần được phối hợp. Với read-modify-write, chỉ giữ lock lúc `write` là chưa đủ: hai tiến trình có thể đã cùng đọc giá trị cũ trước khi đến lượt ghi. Cách đúng là lấy exclusive lock **trước lần đọc đầu**, đọc giá trị hiện tại, tính toán, ghi đủ, xử lý yêu cầu độ bền, rồi thả.

Không giữ khóa lâu trong lúc thực hiện tác vụ không liên quan như gọi API mạng bên ngoài, chờ người dùng, render UI hoặc xử lý CPU không bắt buộc. Một thời gian giữ khóa dài làm các tiến trình khác chờ lâu và có thể dẫn đến kẹt công việc.

## 7. Ví dụ Java 17+: exclusive lock cho bộ đếm lưu trong tệp

Ví dụ thứ hai cho thấy cách biến chuỗi “đọc → cộng → ghi” thành một đơn vị cần exclusive lock. Mỗi lần chạy là một process Java. Chương trình dùng cùng đường dẫn do người dùng truyền vào, nên hai lần chạy liên tiếp tăng giá trị từ `0` lên `1` rồi `2`.

Tạo file `LockedCounterDemo.java`:

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;

public class LockedCounterDemo {
    private static long readCounter(FileChannel channel) throws IOException {
        if (channel.size() == 0) return 0;
        if (channel.size() != Long.BYTES) {
            throw new IOException("Invalid counter file size");
        }
        ByteBuffer buf = ByteBuffer.allocate(Long.BYTES);
        long offset = 0;
        while (buf.hasRemaining()) {
            int n = channel.read(buf, offset);
            if (n <= 0) throw new IOException("Incomplete counter");
            offset += n;
        }
        buf.flip();
        return buf.getLong();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: java LockedCounterDemo counter.dat");
        }
        Path file = Path.of(args[0]);
        try (FileChannel channel = FileChannel.open(file,
                StandardOpenOption.CREATE, StandardOpenOption.READ,
                StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            long updated = Math.addExact(readCounter(channel), 1);
            ByteBuffer buf = ByteBuffer.allocate(Long.BYTES).putLong(updated);
            buf.flip();
            long offset = 0;
            while (buf.hasRemaining()) {
                int n = channel.write(buf, offset);
                if (n <= 0) throw new IOException("Write made no progress");
                offset += n;
            }
            channel.force(true);
            System.out.println("counter=" + updated);
        }
    }
}
```

Biên dịch và chạy tại thư mục chưa có file `counter.dat`:

```bash
javac --release 17 LockedCounterDemo.java
java LockedCounterDemo counter.dat
java LockedCounterDemo counter.dat
```

Kết quả đã kiểm chứng khi file ban đầu chưa tồn tại:

```text
counter=1
counter=2
```

Điều cần quan sát:

1. `FileChannel.open` với `CREATE`, `READ`, `WRITE` cho phép đọc/ghi cùng file; `channel.lock()` xin **exclusive lock toàn file**.
2. Chương trình **chỉ đọc counter sau khi lấy lock**, tránh hai process hợp tác cùng đọc một giá trị cũ.
3. Lần đầu file rỗng nên giá trị được hiểu là `0`; những lần sau file phải có đúng `Long.BYTES` (8) byte. File hỏng/ghi dở ở kích thước khác 0/8 khiến chương trình báo lỗi.
4. `ByteBuffer.putLong` lưu số theo byte order mặc định của buffer (big-endian); reader trong cùng chương trình dùng `getLong` tương ứng. Không được nhầm layout này với chuỗi chữ số text trong file.
5. `channel.write(buf, offset)` dùng offset tuyệt đối và lặp đến khi hết buffer. `force(true)` được gọi **trước khi giải phóng khóa và in thành công**, để yêu cầu đồng bộ nội dung/metadata của file theo hợp đồng Java.
6. `try-with-resources` nhả khóa và đóng channel nếu thành công hoặc ném exception.

**Giới hạn quan trọng:** Đây là ví dụ về **phối hợp process hợp tác**, không phải cơ chế transaction crash-safe đầy đủ. Nếu chương trình hoặc OS lỗi giữa lúc ghi 8 byte, giá trị có thể bị cập nhật một phần; `force` không biến lần ghi 8 byte thành transaction. Khi triển khai thực tế cần checksum, journal/WAL, bản ghi phiên bản, recovery hoặc database nếu mất điện không được phép làm hỏng counter. Đồng thời tất cả process phải cùng tuân thủ file lock; process bỏ qua advisory lock có thể phá dữ liệu. Nếu chạy hai bản chương trình đồng thời, thứ tự kết quả console không được bảo đảm, nhưng mỗi process dùng cùng giao thức khóa ở môi trường hỗ trợ.

## 8. Tại sao FileLock không dành cho đồng bộ nhiều thread trong cùng JVM?

Các `FileLock` được **giữ thay mặt toàn JVM**. Theo Javadoc, vùng lock đã giữ trong một JVM không được chồng lấn; điều này áp dụng cả khi chúng được lấy trên hai `FileChannel` khác nhau của cùng file. Vì vậy:

```java
// Minh họa lỗi logic, channelA/channelB cùng trỏ một file.
FileLock first = channelA.lock(0, 100, true);
FileLock second = channelB.lock(0, 100, true); // OverlappingFileLockException
```

Đoạn trên là ví dụ **không nên chạy như giải pháp đồng bộ** (cũng chưa khai báo biến và không đóng khóa); dùng để minh họa lý do hai thread Java không thể dựa vào “shared file locks” như nhiều process.

Đối với **thread trong một JVM**:

- Dùng `synchronized`, `ReentrantLock`, `ReadWriteLock` hoặc một worker owner duy nhất để bảo vệ đơn vị nghiệp vụ trên record.
- Nếu muốn nhiều reader đồng thời, có thể dùng `ReadWriteLock` ứng dụng: nhiều thread giữ read lock, writer giữ write lock; không cần lấy thêm nhiều `FileLock` chồng vùng trong JVM.
- Đối với các process ngoài JVM, giữ **một FileLock cấp OS** đại diện cho giao thức liên tiến trình; bên trong JVM vẫn phải có lock theo thread/record khi cần.
- `FileChannel` có hợp đồng đồng bộ một số lời gọi I/O liên quan position, nhưng điều đó **không** biến chuỗi `read → tính toán → write` thành transaction an toàn.

Ví dụ nếu hai thread cùng tăng counter và một JVM đã giữ exclusive FileLock, **cả hai thread vẫn có thể race với nhau bên trong JVM** nếu không có mutex hoặc quản lý owner. FileLock chỉ giải quyết phần phối hợp với **bên ngoài** JVM.

## 9. Advisory vs mandatory: khác biệt theo hệ điều hành và filesystem

File locking của Java ánh xạ tới hệ thống khóa native. Hành vi truy cập bị chặn thực sự phụ thuộc OS, filesystem, network mount và cấu hình.

| Bối cảnh | Điều cần dự kiến |
| --- | --- |
| Linux / Unix với filesystem cục bộ hỗ trợ | Khóa vùng có thể mang tính **advisory**: chương trình không hợp tác có thể vẫn mở/đọc/ghi vùng file. Không mặc định một khóa do JVM A giữ sẽ chặn mọi lời gọi `write` của JVM B nếu JVM B bỏ qua giao thức. |
| Windows với filesystem phù hợp | Một số cơ chế khóa vùng có thể cưỡng chế các thao tác đọc/ghi xung đột ở OS; chính sách mở file, sharing và quyền có thể khiến hành vi khác Unix. Không xây dựng logic dựa trên assumption “mandatory ở mọi Windows volume”. |
| NFS/SMB, ổ mạng | Semantics locking có thể liên quan server, network partition, lease/reconnect và mount options. Các tình huống mất kết nối/khóa hết hiệu lực cần kiểm thử riêng. |
| Docker/container / volume chia sẻ | Container có thể dùng host FS, overlay, mount mạng hoặc quyền user khác. Hai container chỉ phối hợp được nếu backend/file system thực sự hỗ trợ cùng cơ chế khóa. |
| Filesystem ảo/in-memory/provider tùy chỉnh | Khả năng khóa có thể thiếu hoặc khác biệt; cần thử trên đúng provider. |

Vì tính di động, **hãy coi FileLock là advisory** và yêu cầu mọi reader/writer tuân thủ hợp đồng khóa ứng dụng. Đừng khẳng định rằng khóa vùng byte tự cấp quyền đọc/ghi hoặc chống được mọi truy cập từ một chương trình không hợp tác.

Một số OS có hạn chế với file mapping: lấy lock mandatory trên vùng file được memory-map có thể thất bại, hoặc mapping sau khi có lock có thể bị từ chối. Khi phối hợp `MappedByteBuffer` và `FileLock`, cần kiểm thử trực tiếp trên filesystem mục tiêu; không coi hai API luôn tương thích.

### Không coi khóa là “phát hiện đầy đủ process đang mở file”

`tryLock()` thành công chỉ nói JVM xin được một vùng khóa theo cơ chế OS. Nó **không** chứng minh không có process nào đang mở tệp, không có reader nào đang xem nội dung hoặc không có writer ngoài giao thức. Ngược lại, việc thất bại với exception có thể do quyền, khả năng OS, trạng thái channel chứ không nhất thiết “có ai đó đang giữ khóa”.

## 10. Độ bền dữ liệu: khóa không thay thế FileChannel.force

Hai mục tiêu khác nhau:

- **Khóa:** điều phối quyền thực hiện thao tác đồng thời giữa các bên hợp tác trong khoảng thời gian giữ khóa.
- **Độ bền:** khi ứng dụng đã báo giao dịch hoàn tất, dữ liệu có còn sau sự cố theo hợp đồng lưu trữ hay không.

`channel.force(true)` yêu cầu đồng bộ nội dung và metadata file trong phạm vi hợp đồng `FileChannel`, nhưng **không khóa dữ liệu**. `FileLock` giúp giảm tranh chấp khi các process hợp tác nhưng **không buộc dữ liệu xuống đĩa**.

Quy trình thường dùng cho read-modify-write trên file local:

```text
Xin exclusive lock trên vùng record phù hợp
          |
          v
Đọc dữ liệu bên trong lock
          |
          v
Tính toán và ghi đủ bytes
          |
          v
force(true) nếu yêu cầu dữ liệu bền vững
          |
          v
Chỉ lúc hoàn tất hợp đồng commit mới báo thành công
          |
          v
Giải phóng khóa
```

Ngay cả với quy trình này, **độ bền và khả năng phục hồi không tuyệt đối**: Java không bảo đảm giống nhau với remote storage, directory entry có hợp đồng riêng, và crash ở giữa record nhiều bước có thể tạo trạng thái dở dang. Với WAL/database, cơ chế commit phức tạp hơn một khóa và một `force`.

Điều cần nhớ: việc gọi `force` trong lúc đang giữ FileLock là lựa chọn hữu ích khi muốn bảo vệ ranh giới commit giữa các **process hợp tác**; không có quy định rằng `FileLock` tự force hoặc `force` tự lock.

## 11. Các lỗi và exception cần xử lý

| Exception / kết quả | Nguyên nhân thường gặp | Hướng xử lý |
| --- | --- | --- |
| `tryLock(...) == null` | Khóa chồng lấn đang được **process khác** giữ. | Trả trạng thái busy, thử lại với backoff/deadline hoặc chuyển sang queue theo yêu cầu. |
| `OverlappingFileLockException` | JVM hiện tại đã giữ hoặc đang chờ xin khóa chồng lấn trên cùng file. | Kiểm tra ownership/khóa vùng trong JVM; không retry theo vòng vô hạn như với `null`. |
| `NonReadableChannelException` | Yêu cầu shared lock trên channel không đọc được. | Mở channel với `READ`, kiểm tra quyền file/OS. |
| `NonWritableChannelException` | Yêu cầu exclusive lock trên channel không ghi được. | Mở channel với `WRITE`, kiểm tra quyền file/OS. |
| `IllegalArgumentException` | `position` hoặc `size` âm, hoặc tổng vùng tràn số không hợp lệ. | Kiểm tra offset và length, dùng `Math.addExact` / `Math.multiplyExact` khi tính vùng. |
| `ClosedChannelException` | Channel đã đóng khi gọi lock/tryLock. | Quản lý vòng đời channel một nơi, tránh sử dụng sau close. |
| `FileLockInterruptionException` | Thread đang xin lock blocking bị interrupt. | Tuân thủ chính sách cancel/shutdown, không nuốt lỗi rồi báo đã có lock. |
| `AsynchronousCloseException` | Channel bị thread khác đóng trong lúc chờ/tác vụ I/O. | Dừng/khôi phục đúng vòng đời, không coi đã giữ được lock. |
| `IOException` / lỗi file store | Lỗi native I/O, quyền, mount, mạng, thiết bị hoặc hỗ trợ platform. | Ghi log path, vùng khóa, thao tác và chính sách retry phù hợp; không phá dữ liệu hiện có. |

Đừng bắt mọi exception rồi tiếp tục đọc/ghi như thể lock đã được cấp. Với `tryLock`, `null` có ý nghĩa cụ thể là **không lấy được khóa do xung đột bên ngoài JVM**; exception là trạng thái cần phân tích khác. Khi `lock` bị interrupt hoặc channel đóng, không được giả định đã lấy khóa.

### Vấn đề deadlock khi nhiều vùng

Giả sử một giao dịch cần khóa record A và B:

- Process 1 giữ A rồi chờ B.
- Process 2 giữ B rồi chờ A.

Nếu cả hai dùng `lock` chờ blocking, có thể bị kẹt theo hình thức deadlock giữa các process. Để tránh:

1. Chuẩn hóa thứ tự xin lock (ví dụ theo byte offset tăng dần).
2. Không giữ một khóa rồi chờ vô hạn một khóa khác không có thời hạn.
3. Nếu dùng `tryLock` và không lấy được toàn bộ khóa, hãy giải phóng những khóa đã lấy và retry theo policy phù hợp.
4. Tránh gọi dịch vụ ngoài hoặc chờ input của user trong khi đang giữ nhiều file lock.

Một số OS có cơ chế deadlock detection ở mức native nhưng **Java FileLock không hứa giải quyết deadlock nghiệp vụ cho bạn**.

## 12. Checklist thiết kế đúng

Trước khi triển khai hệ thống chia sẻ tệp:

1. **Ai tham gia?** Nhiều thread trong một JVM, nhiều JVM/process hay cả hai? Thread dùng mutex/lock ứng dụng; process dùng FileLock nếu mọi bên cùng hợp tác và OS hỗ trợ.
2. **Vùng khóa là gì?** Byte offset và length đến từ đâu? Record cố định hay biến độ dài? Có chồng lấn hoặc mở rộng file không?
3. **Cần shared hay exclusive?** Reader cần nhiều process đọc cùng lúc; writer/read-modify-write cần exclusive. Shared có thể bị hệ thống chuyển sang exclusive.
4. **Channel có đúng quyền không?** `READ` cho shared, `WRITE` cho exclusive; policy OS có cho phép tạo lock không?
5. **Có xử lý `null` và exception riêng không?** `tryLock` gặp xung đột process khác trả `null`, cùng JVM chồng lấn ném `OverlappingFileLockException`.
6. **Vòng đời khóa rõ chưa?** Lấy trước lần đọc cần bảo vệ, giữ đến khi kết thúc ghi/force theo contract, thả trong `try-with-resources`.
7. **Có channel khác bị đóng không?** Trên một số OS, đóng một channel cùng file có thể làm mất các lock của JVM; quản lý duy nhất owner file/lock.
8. **Có process không hợp tác không?** Advisory lock đòi hỏi tất cả reader/writer tuân thủ giao thức; một `tryLock` thành công không chứng minh tệp không bị ai truy cập.
9. **Có yêu cầu durability không?** Nếu có, dùng `force`/giao thức recovery đúng loại lưu trữ; lock không phải flush.
10. **Thử trên đúng môi trường:** filesystem local/network, Windows/Linux/macOS, container/VM, quyền user, trạng thái process crash, timeout và network disconnect.
11. **Đo chi phí chờ khóa:** lock granularity quá rộng làm throughput giảm; vùng quá hẹp làm mất bảo vệ. Thử với nhiều process thật thay vì chỉ thread.
12. **Có cách khôi phục khi crash giữa write?** Cần record format kiểm chứng, WAL, atomic replace hoặc storage engine khi dữ liệu không được phép hỏng.

### Câu hỏi tự kiểm tra

**1. Nếu hai process đều chỉ đọc vùng `[0, 10)`, có thể xin shared lock đồng thời không?**

Theo semantics shared lock, các request chồng vùng từ nhiều process có thể cùng tồn tại nếu platform hỗ trợ và tất cả tuân thủ giao thức. Một số OS có thể nâng shared thành exclusive nên khả năng đồng thời thực tế thay đổi.

**2. Hai channel trong cùng một JVM xin shared lock cùng vùng có luôn được không?**

**Không.** Javadoc cấm FileLock cùng JVM chồng lấn, kể cả đều xin shared; `OverlappingFileLockException` được ném. Dùng `ReadWriteLock`/mutex trong JVM.

**3. Đã khóa `[0, 100)`, xin thêm `[100, 200)` có chồng lấn không?**

**Không.** Các vùng là nửa mở: byte `100` không thuộc khóa đầu.

**4. `tryLock` trả `null` khác `OverlappingFileLockException` thế nào?**

`null` thể hiện xung đột khóa từ **process khác**; `OverlappingFileLockException` thể hiện lock chồng lấn đã giữ hoặc yêu cầu chồng vùng đang đợi trong cùng JVM.

**5. Nếu file 8 byte mà chương trình khóa `[100, 200)` thì khóa có bắt buộc thất bại không?**

**Không.** Hợp đồng cho phép xin khóa vùng ngoài phạm vi kích thước file hiện tại, miễn hệ thống hỗ trợ. Nếu sau đó file mở rộng, khóa vùng cố định vẫn chỉ bao phủ các byte đã xác định.

**6. Tại sao lấy FileLock và ghi dữ liệu chưa đủ để cam kết chống mất điện?**

Vì FileLock quản lý phối hợp truy cập, không yêu cầu dữ liệu được flush xuống thiết bị. Cần `force` và giao thức recovery phù hợp OS/filesystem, đồng thời xét partial write.

**7. Có thể coi `FileLock` như bảo mật chặn mọi tiến trình đọc file không?**

**Không.** Đây là cơ chế phối hợp file I/O, có thể advisory. Quyền truy cập thuộc filesystem ACL/mode và OS policy; process không hợp tác có thể bỏ qua khóa trên một số platform.

## Tài liệu chính thức

- [Java SE 21 — FileLock](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileLock.html): shared/exclusive lock, vòng đời, isShared/isValid/overlaps, advisory/mandatory và giới hạn JVM.
- [Java SE 21 — FileChannel](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html): lock/tryLock, vùng byte, tham số size=0, ngoại lệ và force.
- [Java SE 21 — OverlappingFileLockException](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/OverlappingFileLockException.html): khóa chồng vùng trong cùng JVM.
- [Java SE 21 — AsynchronousFileChannel](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/AsynchronousFileChannel.html): API khóa trong I/O bất đồng bộ với callback/Future và giới hạn theo platform.

Điểm mấu chốt: **exclusive/shared lock quyết định yêu cầu phối hợp đọc/ghi giữa các process; `position` và `size` quyết định chính xác vùng byte; khóa không dùng thay mutex cho thread trong JVM; và muốn dữ liệu bền sau crash thì cần thêm force cùng giao thức cập nhật/khôi phục đúng đắn.**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-locking-and-durability-003">File lock thuộc phạm vi JVM, không thay thế khóa giữa các luồng</a>

<details>
<summary>Xem chi tiết</summary>

# File lock thuộc phạm vi JVM, không thay thế khóa giữa các luồng

## 1. Hai loại cạnh tranh khi truy cập tệp

Trong Java NIO, `FileLock` được dùng để phối hợp truy cập tệp hoặc vùng byte của tệp, đặc biệt khi **nhiều tiến trình** cùng quan tâm tới một tài nguyên. Nhưng ứng dụng Java còn có thể chạy **nhiều thread trong cùng JVM**. Hai bài toán cần cơ chế khác nhau:

- **Đồng bộ trong JVM:** tổ chức các thao tác đọc–sửa–ghi giữa thread, bảo vệ các object Java và tạo quan hệ happens-before về bộ nhớ. Dùng `synchronized`, `ReentrantLock` hoặc cơ chế đồng bộ phù hợp.
- **Phối hợp giữa các tiến trình:** dùng `FileChannel.lock(...)`/`tryLock(...)` để yêu cầu khóa vùng byte, theo khả năng filesystem/hệ điều hành. Mọi chương trình tham gia cần tuân thủ cùng giao thức khóa, nhất là khi khóa là advisory.

Một file lock được giữ **thay mặt cho toàn bộ JVM**. Nó không phải một monitor hay một mutex dành cho từng Java thread. Vì thế, theo hợp đồng `java.nio.channels.FileLock`, **file lock không phù hợp để kiểm soát truy cập tệp giữa các thread trong cùng JVM**.

~~~text
                  CÙNG JVM A                             JVM B
      +--------------------------------+             +----------+
      | Thread A       Thread B        |             | Process B|
      |    \              /            |             +-----+----+
      |     \            /             |                   |
      |   synchronized / ReentrantLock |                   |
      |       bảo vệ critical section  |                   |
      |                |               |                   |
      |         FileChannel.lock() ----+-------+-----------+
      +--------------------------------+       |
                                     Hệ điều hành / filesystem
                               file lock có khả năng phối hợp tiến trình
~~~

**File lock không tự khóa các biến, collection hay buffer Java.** File lock cũng không bảo đảm ứng dụng ngoài JVM phải tuân thủ nếu hệ điều hành triển khai advisory locking.

## 2. API FileChannel và FileLock

Các phương thức chính của `FileChannel`:

~~~java
FileLock lock() throws IOException;
FileLock lock(long position, long size, boolean shared) throws IOException;

FileLock tryLock() throws IOException;
FileLock tryLock(long position, long size, boolean shared)
        throws IOException;
~~~

Đây là các **chữ ký minh họa API**, chưa phải chương trình chạy độc lập.

`lock()` yêu cầu khóa độc quyền vùng byte bắt đầu từ offset 0, có độ dài `Long.MAX_VALUE`. `lock(position, size, shared)` yêu cầu một vùng chỉ định:

- `position` là byte offset bắt đầu, không âm.
- `size` là chiều dài vùng byte, không âm; offset và chiều dài phải hợp lệ.
- `shared = false` yêu cầu exclusive lock.
- `shared = true` yêu cầu shared lock, nếu nền tảng/provider hỗ trợ. Nếu shared lock không được hỗ trợ, provider có thể cấp exclusive lock; dùng `FileLock.isShared()` để biết loại đã nhận thực tế.

`FileLock` cung cấp `position()`, `size()`, `isShared()`, `isValid()`, `release()` và `channel()`. Không coi `FileLock.position()` là `FileChannel.position()`: cái thứ nhất nói về điểm bắt đầu của **vùng khóa**, cái thứ hai nói về vị trí **đọc/ghi tương đối hiện tại**.

### lock và tryLock khác nhau

| Trạng thái | `lock(...)` | `tryLock(...)` |
| --- | --- | --- |
| Có thể cấp khóa | Trả `FileLock` | Trả `FileLock` |
| Khóa xung đột đang do tiến trình khác giữ | Có thể chờ (hoặc thất bại khi bị gián đoạn/lỗi) | Trả `null` nếu không thể cấp ngay |
| Vùng khóa chồng lấn với khóa đã giữ hoặc đang chờ **trong cùng JVM** | `OverlappingFileLockException` | `OverlappingFileLockException` |
| Channel không có quyền hoặc có lỗi I/O | Ngoại lệ tương ứng | Ngoại lệ tương ứng |

**Đừng nhầm `tryLock() == null` với mọi dạng tranh chấp.** Khi chính JVM hiện tại có một lock trùng vùng, `tryLock` sẽ ném `OverlappingFileLockException`, thay vì trả `null` để thread có thể âm thầm chờ.

## 3. Phạm vi JVM thể hiện ở đâu?

Giả sử JVM mở hai `FileChannel` riêng biệt tới cùng tệp:

~~~text
FileChannel A ── lock [0, 100) ── đã lấy
FileChannel B ── tryLock [0,100) ── trùng vùng, cùng JVM
~~~

Một lần thử khóa trùng vùng trên B sẽ bị phát hiện và ném `OverlappingFileLockException`. Điều này có thể xảy ra dù A và B thuộc hai thread khác nhau. JVM **không** xem mỗi channel hoặc mỗi thread là một chủ khóa độc lập cho mục đích chồng lấn file lock.

Phạm vi khóa byte được biểu diễn theo khoảng nửa mở:

~~~text
A: [0,100)       gồm offset 0 đến 99
B: [100,200)     không chồng lấn A
C: [50,150)      chồng lấn cả A và B
~~~

Hai vùng không chồng lấn có thể được khóa riêng theo khả năng provider. Khóa không nhất thiết phải chỉ nằm trong kích thước tệp hiện hữu. Ví dụ khóa `[0, Long.MAX_VALUE)` có thể được lấy ngay cả khi file đang rỗng.

Khả năng phát hiện lock trong cùng JVM **không** biến `FileLock` thành mutex nội bộ, vì:

1. Một thread khác trong cùng JVM **không tự bị monitor chặn** khi đọc/ghi dữ liệu hoặc sửa state của chương trình.
2. Nếu thread đó yêu cầu lock trùng vùng, nó có thể **nhận ngoại lệ ngay**, không xếp hàng chờ như khi dùng `ReentrantLock.lock()`.
3. File lock không cung cấp đầy đủ quan hệ happens-before cho mọi biến Java đang được thread thao tác.
4. Thread khác có thể sử dụng một channel khác mà không tuân thủ giao thức khóa; mức ngăn cản truy cập trực tiếp phụ thuộc hệ điều hành/provider.

## 4. Ví dụ Java 17+: hai channel trong một JVM

Chương trình dùng tệp tạm, có hai channel trong **cùng một tiến trình Java**. Nó lấy khóa bằng channel thứ nhất, thử khóa vùng trùng bằng channel thứ hai, rồi giải phóng khóa thứ nhất để chứng minh vùng đó có thể được cấp lại.

Lưu thành `JvmFileLockDemo.java`:

~~~java
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class JvmFileLockDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("lock-demo-", ".bin");

        try {
            try (FileChannel first = FileChannel.open(
                        file, StandardOpenOption.READ, StandardOpenOption.WRITE);
                 FileChannel second = FileChannel.open(
                        file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {

                try (FileLock held = first.lock()) {
                    System.out.println("first lock valid: " + held.isValid());

                    boolean rejected = false;
                    try {
                        FileLock unexpected = second.tryLock();
                        if (unexpected != null) {
                            unexpected.release();
                        }
                    } catch (OverlappingFileLockException ex) {
                        rejected = true;
                    }
                    System.out.println("same JVM overlap rejected: " + rejected);
                }

                try (FileLock after = second.tryLock()) {
                    System.out.println("lock after release: "
                            + (after != null && after.isValid()));
                }
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac JvmFileLockDemo.java
java JvmFileLockDemo
~~~

Stdout trên hệ thống tệp mặc định hỗ trợ exclusive file lock, khi không có tiến trình khác tranh chấp tệp tạm:

~~~text
first lock valid: true
same JVM overlap rejected: true
lock after release: true
~~~

Diễn giải:

1. Hai channel cùng trỏ tới một tệp tạm, cùng tồn tại trong một JVM.
2. `first.lock()` lấy exclusive lock trên toàn vùng mặc định và `held.isValid()` là `true`.
3. `second.tryLock()` yêu cầu khóa trùng vùng, JVM phát hiện khóa chồng lấn và ném `OverlappingFileLockException`.
4. `try-with-resources` đóng `held` (tức giải phóng khóa). Sau đó `second.tryLock()` lấy được khóa mới.
5. Tệp tạm được dọn trong `finally`; channel và lock được đóng đúng thứ tự.

Ví dụ chủ động **không** tạo thread thứ hai: hai channel trong cùng JVM đủ để quan sát tính chất chồng lấn. Để hiểu bài toán thực tế có nhiều thread, cần xét thêm khóa nội bộ ở các phần sau. Nếu filesystem không hỗ trợ lock hoặc quyền của tiến trình không đủ, kết quả có thể là ngoại lệ I/O thay cho stdout trên.

## 5. Vì sao FileLock không thay thế đồng bộ giữa các thread?

Ví dụ hai thread cùng ghi thêm dữ liệu:

~~~text
Tệp đang có 100 byte

Thread A: size() trả 100
Thread B: size() trả 100
Thread A: position(100), write(10 byte)
Thread B: position(100), write(20 byte)

Hai lần ghi có thể chồng lấn và làm mất bản ghi.
~~~

Với một channel dùng chung, các thao tác `position(...)` và `write(...)` còn có thể bị xen kẽ. Với hai channel riêng, việc cả hai cùng nhìn thấy size=100 cũng không tạo ra chỗ ghi riêng. Nếu cả hai cố lấy file lock trùng vùng trong cùng JVM, một thread có thể nhận `OverlappingFileLockException` thay vì được tự động xếp hàng.

Đồng bộ đúng ở đây là bảo vệ **toàn bộ thao tác logic** bằng cùng mutex mà mọi thread liên quan phải sử dụng:

~~~java
private final java.util.concurrent.locks.ReentrantLock mutex =
        new java.util.concurrent.locks.ReentrantLock();

void performUpdate() {
    mutex.lock();
    try {
        // Đọc dữ liệu đang có, tính offset, sửa và ghi.
        // Mọi thread cùng JVM cần đi qua cùng mutex này.
    } finally {
        mutex.unlock();
    }
}
~~~

Đây là mảnh code trong một class, chưa có `main`.

`synchronized` trên một object dùng chung cũng có thể bảo vệ một vùng tương tự. `ReentrantLock` phù hợp khi cần `tryLock`, timeout, cơ chế lấy lock có thể bị ngắt, hoặc chính sách chờ tùy biến. Quan trọng nhất là **các thread phải chia sẻ cùng đối tượng khóa**. Mỗi lần gọi lại tạo một `new ReentrantLock()` thì các thread sẽ không phối hợp.

Nếu state Java như `HashMap`, cache, counter, buffer hoặc danh sách record cũng được sửa, file lock không thay thế các quy tắc thread safety của những đối tượng đó.

## 6. Khi cần phối hợp cả trong JVM lẫn giữa các tiến trình

Một mẫu thường dùng là phối hợp **hai tầng**:

1. Lấy mutex chung của các thread trong JVM.
2. Mở channel theo đúng chế độ, rồi lấy file lock theo vùng/giao thức mà các tiến trình ngoài cũng chấp nhận.
3. Khi đang nắm cả hai, thực hiện trọn vẹn chuỗi đọc–sửa–ghi.
4. Giải phóng file lock, đóng channel, sau đó nhả mutex trong `finally`.

Ví dụ class sử dụng chung một instance cho mọi thread ghi cùng file:

~~~java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.locks.ReentrantLock;

final class CoordinatedAppender {
    private final Path file;
    private final ReentrantLock inProcess = new ReentrantLock();

    CoordinatedAppender(Path file) {
        this.file = file;
    }

    void append(byte[] record) throws IOException {
        inProcess.lock();
        try {
            try (FileChannel channel = FileChannel.open(
                    file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                try (FileLock ignored = channel.lock()) {
                    channel.position(channel.size());
                    ByteBuffer bytes = ByteBuffer.wrap(record);
                    while (bytes.hasRemaining()) {
                        if (channel.write(bytes) == 0) {
                            throw new IOException("No write progress");
                        }
                    }
                }
            }
        } finally {
            inProcess.unlock();
        }
    }
}
~~~

Đây là **class minh họa**, không phải chương trình có `main`. Một số giới hạn phải được hiểu rõ:

- Trong cùng JVM, **các thread phải chia sẻ cùng `CoordinatedAppender` hoặc cùng mutex cho đúng tệp**. Tạo nhiều instance với mutex khác nhau sẽ mất bảo đảm phối hợp.
- Mọi tiến trình khác muốn phối hợp phải **tự sử dụng file lock tương thích**; trên hệ thống advisory, một tiến trình không tuân thủ vẫn có thể thao tác.
- Không dùng `APPEND` trong mẫu này: vị trí ghi được chọn sau `size()` khi đang giữ khóa.
- Mẫu này không tự tạo transaction khôi phục khi ghi nửa bản ghi rồi lỗi; ứng dụng thực tế có thể cần length-prefix, checksum, commit marker và `FileChannel.force(...)` khi yêu cầu durability.
- Nếu nhiều thread đang giữ các khóa khác nhau, cần nhất quán thứ tự lấy khóa để giảm nguy cơ deadlock.

Hai tầng đáp ứng hai mục tiêu khác nhau. Mutex bảo vệ critical section và trạng thái trong một JVM; file lock giúp phối hợp với tiến trình ngoài khi provider và những chương trình liên quan hỗ trợ.

## 7. Giới hạn hệ điều hành và filesystem

### Advisory và mandatory

Trên một số hệ điều hành/filesystem, file lock là **advisory**: chỉ các tiến trình chủ động tham gia giao thức mới tôn trọng nhau. Trên nền tảng khác, một số thao tác được OS enforce theo ngữ nghĩa mandatory. **Java không hứa rằng bất kỳ tiến trình ngoài nào cũng bị chặn mọi thao tác đọc/ghi**.

Do đó, file lock không phải hệ thống phân quyền truy cập hay giải pháp bảo mật tổng quát. Nếu file chứa dữ liệu nhạy cảm, phải dùng permission và cơ chế bảo mật riêng.

### Khóa chia sẻ và độc quyền

Exclusive lock thường phục vụ cập nhật tệp. Shared lock thường phục vụ nhiều bên đọc cùng lúc, nhưng support thực tế phụ thuộc provider/hệ điều hành. Một yêu cầu shared có thể được đáp ứng bằng exclusive; `FileLock.isShared()` xác định kiểu được cấp.

### Khóa và đóng channel

`FileLock` triển khai `AutoCloseable`, có thể dùng `try (FileLock lock = channel.lock())` để tự giải phóng. Nếu channel bị đóng, lock được giải phóng và `lock.isValid()` trả `false`.

**Lưu ý quan trọng:** trên một số nền tảng, đóng bất kỳ channel nào đến cùng file trong cùng JVM có thể giải phóng mọi file lock JVM đang giữ trên file đó, kể cả lock tạo từ channel khác. Vì vậy, tránh mở rồi đóng channel bất kỳ tới cùng file trong khi còn dựa vào một lock; quản lý vòng đời của các channel tập trung.

### Filesystem mạng, provider khác nhau

NFS/SMB, filesystem ảo, các provider đặc biệt và từng OS có thể có ngữ nghĩa khóa, granularity và lỗi khác nhau. Trường hợp provider không hỗ trợ, có thể có `IOException` hoặc ngoại lệ thích hợp. Kiểm thử bằng hệ thống tệp triển khai thực tế trước khi phụ thuộc vào file lock để bảo vệ dữ liệu quan trọng.

## 8. Các lỗi và phản ứng phù hợp

| Trường hợp | Kết quả có thể gặp |
| --- | --- |
| Vùng khóa chồng lấn trong cùng JVM (đã giữ hoặc đang chờ) | `OverlappingFileLockException` |
| `tryLock` xung đột với khóa từ tiến trình khác | `null` |
| Yêu cầu exclusive lock nhưng channel không có quyền ghi | `NonWritableChannelException` |
| Yêu cầu shared lock nhưng channel không có quyền đọc | `NonReadableChannelException` |
| Channel đã đóng | `ClosedChannelException` |
| Đang chờ `lock` thì thread bị interrupt | `FileLockInterruptionException` |
| Đã release hoặc channel bị đóng | `FileLock.isValid() == false` |

Phải bắt và xử lý ngoại lệ theo mục đích. Ví dụ nếu một thread nhận `OverlappingFileLockException` do thao tác khác của chính JVM, việc lặp `tryLock` liên tục sẽ không tự giải quyết vấn đề thiết kế: cần điều phối bằng mutex nội bộ.

Một mẫu thử không chặn:

~~~java
try {
    FileLock lock = channel.tryLock();
    if (lock == null) {
        // Tiến trình khác đang nắm khóa xung đột.
    } else {
        try (lock) {
            // Đã lấy được khóa.
        }
    }
} catch (OverlappingFileLockException ex) {
    // Chính JVM này có vùng khóa chồng lấn hoặc đang chờ khóa.
}
~~~

Đoạn code minh họa trong một phương thức với `channel` đang mở và import phù hợp.

## 9. Lỗi phổ biến

**Lỗi 1:** dùng `FileLock` để thay `ReentrantLock` trong hai thread. Một yêu cầu trùng vùng có thể ném `OverlappingFileLockException` thay vì chờ.

**Lỗi 2:** cho rằng `tryLock()` luôn trả `null` khi khóa chưa sẵn sàng. Cần phân biệt xung đột từ tiến trình khác và xung đột trong JVM.

**Lỗi 3:** tạo mutex mới mỗi lần gọi; hai thread thực tế không có cùng đối tượng khóa để phối hợp.

**Lỗi 4:** cho rằng file lock tạo memory visibility cho mọi biến Java. Cần `synchronized` hoặc công cụ Java concurrency phù hợp.

**Lỗi 5:** giữ file lock mà code khác tùy tiện mở/đóng channel tới cùng file. Trên một số OS, thao tác đóng này có thể ảnh hưởng khóa đang giữ.

**Lỗi 6:** giả định file lock chặn mọi tiến trình. Advisory lock cần các bên cùng tuân thủ.

**Lỗi 7:** khóa những vùng byte không tương thích giữa các tiến trình. Nếu A khóa [0,100) còn B chỉ khóa [100,200), chưa bảo vệ chung một vùng.

**Lỗi 8:** không release trong `finally` hoặc `try-with-resources`; để ngoại lệ làm mutex không được giải phóng.

**Lỗi 9:** tin rằng file lock là giao dịch. Khóa không tự rollback ghi dở, kiểm tra checksum hay bảo đảm dữ liệu đã durable trên thiết bị.

## 10. Câu hỏi tự kiểm tra

1. Hai thread trong cùng JVM muốn khóa cùng vùng byte. Thread đến sau có luôn chờ? **Không.** Nó có thể nhận `OverlappingFileLockException`.
2. `tryLock()` trả `null` có nghĩa là gì? **Không thể lấy khóa ngay do xung đột với tiến trình khác.** Xung đột ngay trong JVM có ngoại lệ riêng.
3. File lock có tự đồng bộ một `HashMap` mà hai thread đang sửa không? **Không.** Cần quy tắc thread safety của Java.
4. Hai `FileChannel` trỏ cùng tệp trong cùng JVM có phạm vi khóa riêng không? **Không** đối với cơ chế chồng lấn JVM.
5. Một tiến trình khác không tham gia cơ chế khóa có chắc bị chặn ghi không? **Không thể bảo đảm trên mọi OS/filesystem**, đặc biệt với advisory lock.
6. Mẫu hai tầng nên lấy khóa theo thứ tự nào? **Mutex trong JVM, rồi file lock**; nhả theo thứ tự ngược và thống nhất với mọi đường code.
7. Shared lock có luôn thực sự được cấp dạng shared? **Không.** Xem `FileLock.isShared()`.
8. Vì sao nên dùng `try-with-resources` cho FileLock? **Để release khóa đúng lúc kể cả khi thao tác gây lỗi**.

**Ghi nhớ:** `FileLock` có phạm vi **JVM** đối với việc nắm giữ và phát hiện vùng khóa chồng lấn, hữu ích trong giao thức phối hợp tệp giữa các tiến trình. Nó **không** thay thế `synchronized` hay `ReentrantLock` để điều phối nhiều thread, bảo vệ trạng thái chương trình và làm cho một chuỗi đọc–sửa–ghi trở thành critical section trong Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-locking-and-durability-004">OverlappingFileLockException và giới hạn hỗ trợ của hệ điều hành</a>

<details>
<summary>Xem chi tiết</summary>

# OverlappingFileLockException và giới hạn hỗ trợ của hệ điều hành trong Java NIO

## 1. Vì sao khóa tệp không đơn giản là gọi lock()?

Khi nhiều luồng, nhiều `FileChannel` hoặc nhiều tiến trình cùng truy cập một tệp, ứng dụng đôi khi cần phối hợp để tránh ghi đè và đọc dữ liệu ở trạng thái đang thay đổi. Java NIO cung cấp `FileChannel.lock(...)` và `FileChannel.tryLock(...)` để yêu cầu **khóa một vùng byte của tệp** (*file-region lock*).

Một lỗi đặc biệt dễ gây nhầm lẫn là `java.nio.channels.OverlappingFileLockException`. Lỗi này liên quan đến **các vùng khóa chồng lấn trong cùng Java Virtual Machine (JVM)**. Nó khác với trường hợp một **tiến trình khác** đang giữ khóa khiến thao tác xin khóa không thành công hoặc phải chờ.

Ngoài ra, khóa tệp đi qua dịch vụ khóa của hệ điều hành và file system bên dưới. Tính chất *shared/exclusive*, phạm vi ảnh hưởng của khóa, cách các tiến trình khác thực sự tuân thủ khóa, và việc sử dụng trên network file system có thể khác nhau giữa các nền tảng. Một chương trình đúng phải phân biệt:

1. **Quy tắc API của Java:** kiểu khóa, phạm vi byte, vòng đời, ngoại lệ và các điều kiện trong cùng JVM.
2. **Quy tắc phối hợp của ứng dụng:** ai khóa, khóa phần nào, dùng chung channel hay không, thứ tự lấy/nhả khóa.
3. **Khả năng hệ điều hành/file system:** khóa có được hỗ trợ đầy đủ không, có mang tính bắt buộc hay chỉ khuyến nghị, có giới hạn trên network file system không.

Mục tiêu là hiểu được khi nào xảy ra `OverlappingFileLockException`, xử lý khác biệt với `tryLock() == null` ra sao, và tại sao chỉ có `FileLock` vẫn chưa đủ để khẳng định dữ liệu đã được ghi bền hoặc một chuỗi thao tác là transaction.

## 2. Mô hình FileChannel và FileLock

Một `FileChannel` có thể yêu cầu khóa một vùng tệp với ba tham số:

~~~java
FileLock lock = channel.lock(position, size, shared);
~~~

Đây là đoạn minh họa, không phải chương trình độc lập; `channel` phải được mở với quyền phù hợp.

- `position`: byte offset bắt đầu vùng khóa, là số `long` không âm.
- `size`: độ dài vùng khóa, cũng là số `long` không âm. Phạm vi được hiểu theo nửa khoảng **[position, position + size)**, nghĩa là byte đầu có trong vùng và điểm kết thúc không thuộc vùng.
- `shared`: `false` yêu cầu khóa độc quyền (*exclusive*); `true` yêu cầu khóa chia sẻ (*shared*), nếu hệ điều hành/file system hỗ trợ.

Ví dụ:

~~~text
Khóa A: [0, 100)      -> byte 0 tới 99
Khóa B: [100, 200)    -> byte 100 tới 199
Khóa C: [50, 150)     -> byte 50 tới 149

A và B không chồng lấn vì điểm 100 là cuối A, đầu B.
A và C chồng lấn ở [50, 100).
B và C chồng lấn ở [100, 150).
~~~

Mã Java có thể yêu cầu khóa bao trùm cả vùng nằm ngoài độ dài hiện tại của tệp; vùng khóa **không tự thay đổi** khi kích thước tệp sau đó thay đổi. Một vùng khóa không phải lời hứa rằng tệp sẽ giữ nguyên kích thước hoặc không thể bị thao tác ngoài giao thức khóa. Trong nhiều bài toán cần khóa “toàn tệp”, overload `channel.lock()` tương đương yêu cầu khóa độc quyền vùng bắt đầu ở 0 với kích thước `Long.MAX_VALUE`.

`FileLock` là đối tượng đại diện cho khóa đã được cấp thành công. Có thể hỏi:

~~~java
lock.position();  // vị trí vùng khóa
lock.size();      // độ dài vùng khóa
lock.isShared();  // loại khóa thực tế
lock.isValid();   // khóa còn hợp lệ hay không
lock.release();   // giải phóng khóa
~~~

Từ `FileLock` cũng có thể lấy channel gắn với khóa. Cần nhớ rằng **khóa tệp thuộc JVM đang chạy**, dù API cho phép xin khóa qua nhiều đối tượng `FileChannel` khác nhau. Vì vậy hai channel mở cùng tệp ở một JVM không tạo ra hai không gian khóa độc lập.

## 3. Khi nào ném OverlappingFileLockException?

`OverlappingFileLockException` là ngoại lệ **không kiểm tra lúc biên dịch** (subclass của `IllegalStateException`). Các phương thức `lock(...)` và `tryLock(...)` có thể ném ngoại lệ này khi vùng được xin khóa **chồng lấn với vùng đang được khóa bởi JVM hiện tại**, hoặc có một thread trong JVM đã chờ khóa một vùng chồng lấn.

Các trường hợp phổ biến:

- Thread A trong cùng JVM đã có khóa `[0, 100)` trên một tệp; thread B gọi `lock(50, 25, false)` cho cùng tệp.
- Một `FileChannel` thứ hai mở cùng tệp, dù tạo từ lệnh `FileChannel.open(...)` riêng, lại xin khóa chồng lên khóa còn hiệu lực của channel thứ nhất.
- Một thread đang chờ yêu cầu khóa vùng A; thread khác trong cùng JVM xin vùng chồng lấn trước khi yêu cầu chờ đó kết thúc.

Điều quan trọng: **quy tắc kiểm tra chồng lấn trong cùng JVM áp dụng cả khi cả hai yêu cầu đều là shared lock**. Không được suy luận rằng hai thread cùng JVM có thể mỗi thread giữ một `FileLock` chia sẻ riêng trên vùng byte trùng nhau. Khóa chia sẻ có ý nghĩa khi phối hợp với **tiến trình khác** theo khả năng của hệ điều hành; nếu JVM đã giữ lock chồng lấn, một yêu cầu `FileLock` mới từ JVM đó vẫn có thể bị ném ngoại lệ.

Bảng nhận biết:

| Tình huống | Kết quả thường gặp tại API Java | Ý nghĩa |
| --- | --- | --- |
| Trong JVM đã giữ khóa chồng lấn, gọi `tryLock(...)` | `OverlappingFileLockException` | Xung đột quản lý lock **nội bộ JVM**, không phải `null` |
| Trong JVM đã giữ hoặc đang đợi khóa chồng lấn, gọi `lock(...)` | `OverlappingFileLockException` | Không nên giả định call sẽ “xếp hàng chờ” chính JVM mình |
| Tiến trình **khác** đang giữ khóa xung đột, gọi `tryLock(...)` | Thường trả `null` | Không lấy được khóa ngay, có thể thử lại theo lịch do ứng dụng quyết định |
| Tiến trình **khác** đang giữ khóa xung đột, gọi `lock(...)` | Có thể chờ đến khi được cấp hoặc bị lỗi/gián đoạn | Lời gọi blocking không đồng nghĩa sẽ thành công vô điều kiện |
| Vùng không chồng lấn, được hệ điều hành hỗ trợ | Có thể nhận `FileLock` | Vẫn phải xử lý lỗi I/O, quyền truy cập và giới hạn hệ thống |

Đừng dùng `tryLock() == null` làm điều kiện duy nhất để nhận biết mọi kiểu xung đột. `OverlappingFileLockException` được **ném** chứ không chuyển thành `null`. Và đừng gọi mã có khả năng ném ngoại lệ nội bộ JVM là dấu hiệu chắc chắn “tiến trình khác đang chiếm tệp”.

## 4. Ví dụ Java 17+ hoàn chỉnh: khóa chồng lấn, nhả và khóa lại

Ví dụ tạo một tệp tạm riêng, mở hai channel trên cùng tệp trong cùng JVM, dùng channel thứ nhất giữ `[0, 16)` rồi yêu cầu channel thứ hai khóa `[8, 16)`. Hai vùng chồng lấn nên `tryLock` sẽ ném `OverlappingFileLockException`. Khi khóa đầu đã được giải phóng, việc xin khóa lại có thể thành công.

Tạo tệp `LockOverlapDemo.java`:

~~~java
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class LockOverlapDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("lock-overlap-", ".bin");
        try {
            try (FileChannel first = FileChannel.open(file,
                    StandardOpenOption.READ, StandardOpenOption.WRITE);
                 FileChannel second = FileChannel.open(file,
                    StandardOpenOption.READ, StandardOpenOption.WRITE)) {

                try (FileLock held = first.lock(0, 16, false)) {
                    System.out.println("First lock valid: " + held.isValid());
                    try {
                        FileLock unexpected = second.tryLock(8, 8, false);
                        if (unexpected != null) unexpected.close();
                        System.out.println("Unexpected: no overlap exception");
                    } catch (OverlappingFileLockException expected) {
                        System.out.println("Overlap detected in the same JVM");
                    }
                }

                try (FileLock afterRelease = second.tryLock(8, 8, false)) {
                    if (afterRelease == null) {
                        System.out.println("Other process holds the range");
                    } else {
                        System.out.println("Reacquired after release: " + afterRelease.isValid());
                    }
                }
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
~~~

Biên dịch và chạy với **Java 17 trở lên**:

~~~bash
javac LockOverlapDemo.java
java LockOverlapDemo
~~~

Kết quả đã chạy trên môi trường kiểm thử với hệ thống tệp cục bộ:

~~~text
First lock valid: true
Overlap detected in the same JVM
Reacquired after release: true
~~~

Kết quả dòng cuối giả định **không có tiến trình khác giữ khóa [8,16)** và nền tảng hỗ trợ các khóa cần thiết. Nếu có một tiến trình ngoài JVM lấy khóa trong khoảng giữa hai thao tác, lời gọi `tryLock` thứ hai có thể trả `null` (hoặc gặp lỗi tùy tình huống).

Các bước đáng chú ý:

1. `Files.createTempFile(...)` tạo tệp mới, tránh làm thay đổi một tệp nghiệp vụ đang được sử dụng.
2. Hai `FileChannel` đều mở cùng tệp và có quyền đọc/ghi. Khóa được yêu cầu là exclusive vì đối số `shared=false`.
3. `first.lock(0, 16, false)` lấy vùng [0,16). `second.tryLock(8, 8, false)` xin vùng [8,16), nên bị xem là chồng lấn mặc dù hai channel khác object.
4. `try-with-resources` trên `FileLock held` tự gọi `close()`/`release()` khi ra khỏi khối. Điều này giải phóng khóa.
5. Lần thứ hai `second.tryLock(8, 8, false)` có thể lấy được khóa vì không còn xung đột nội bộ JVM.
6. Hai channel được đóng trước khi xóa tệp tạm; `finally` vẫn thử xóa nếu quá trình khóa gặp exception.

Ví dụ này cố ý có thứ tự tuần tự rõ ràng nên không cần chạy hai thread mới chứng minh được quy tắc **cùng JVM**. Nếu muốn mô phỏng nhiều JVM thực sự cạnh tranh, cần chạy **hai process Java riêng**, chứ không chỉ tạo hai thread. Tránh viết bài kiểm thử phụ thuộc vào thời điểm ngẫu nhiên vì sẽ cho kết quả không ổn định.

## 5. lock() và tryLock(): blocking, null và exception

Có hai nhóm phương thức quan trọng:

~~~java
FileLock blocking = channel.lock();                   // exclusive, cả vùng lớn
FileLock region = channel.lock(0, 1024, false);       // blocking cho vùng [0,1024)
FileLock quick = channel.tryLock(0, 1024, false);      // non-blocking
~~~

Đây là **các lời gọi minh họa**, không nên chạy đồng thời cả ba trên cùng channel vì chúng đang yêu cầu các vùng chồng lấn; mã thực tế chỉ chọn cách phù hợp.

- `lock()` và `lock(position, size, shared)` có thể **chặn thread** khi một tiến trình khác giữ khóa xung đột, cho đến lúc nhận được khóa hoặc thao tác bị hủy/lỗi.
- `tryLock(...)` yêu cầu khóa **không chờ**. Khi không nhận được ngay vì một tiến trình khác, API có thể trả `null`. Khi khóa chồng lấn đang được giữ hoặc chờ trong cùng JVM, nó ném `OverlappingFileLockException`.
- `tryLock()` không có đối số yêu cầu exclusive lock trên vùng bắt đầu ở byte 0 và kéo dài tới `Long.MAX_VALUE`.
- Các lời gọi vẫn có thể ném `IOException` vì lỗi file system, không tương thích khóa, quyền truy cập, tài nguyên hoặc kết nối với network FS. Việc `tryLock` “không chặn” chỉ mô tả cách chờ cấp khóa; không nên suy ra mọi I/O liên quan đều luôn không có độ trễ.

Khi `lock(...)` đang chờ, `Thread.interrupt()` có thể khiến nó kết thúc bằng `FileLockInterruptionException`; đóng channel từ thread khác khi nó đang chờ có thể gây `AsynchronousCloseException`. Đây là các trường hợp khác với `OverlappingFileLockException` và cần xử lý theo chính sách hủy tác vụ, retry hoặc đóng tài nguyên của ứng dụng.

Một nhánh xử lý `tryLock` minh họa:

~~~java
try (FileLock acquired = channel.tryLock(0, 1024, false)) {
    if (acquired == null) {
        // Tiến trình khác có thể đang giữ vùng khóa: thử lại có giới hạn hoặc báo bận.
        return;
    }
    // Chỉ thực hiện thao tác cần khóa trong khối này.
} catch (OverlappingFileLockException e) {
    // Xung đột khóa nội bộ JVM: sửa giao thức sở hữu thay vì retry vô hạn.
}
~~~

Đây là **mã minh họa đặt trong method trả về void**, nơi `channel` đã được tạo/mở với quyền thích hợp. `try-with-resources` chấp nhận tham chiếu null, nên nếu `tryLock` trả null, nhánh kiểm tra giúp tránh dereference null; mã trên vẫn nên được tổ chức để không để nhánh return qua nhiều lớp làm khó việc giám sát. Trong ứng dụng production, bắt và xử lý riêng các lỗi I/O có ý nghĩa nghiệp vụ.

Nếu nhiều thread trong cùng JVM cùng xử lý một file, tốt hơn nên dùng một nơi quản lý lock theo file/region để điều phối nội bộ trước khi xin OS lock. Bắt ngoại lệ rồi ngủ và retry liên tục không giải quyết được vấn đề khi một component quên nhả khóa.

## 6. Vùng khóa, shared và exclusive: đừng suy diễn quá mức

**Exclusive lock** yêu cầu độc quyền vùng byte với các khóa xung đột do những tiến trình có phối hợp áp đặt. **Shared lock** dành cho các reader có thể cùng giữ khóa đọc, với điều kiện hệ điều hành/file system thực sự hỗ trợ shared file locks.

Trên một số nền tảng, một yêu cầu shared lock có thể được **nâng thành exclusive**. Khi đó hãy kiểm tra `FileLock.isShared()` trên kết quả để biết trạng thái khóa thực tế; không giả định đối số `shared=true` được thực thi nguyên trạng.

Quyền mở channel cũng quan trọng:

- Để yêu cầu **shared lock**, channel phải được mở cho đọc; nếu không có quyền tương ứng, có thể gặp `NonReadableChannelException`.
- Để yêu cầu **exclusive lock**, channel phải được mở cho ghi; nếu không, có thể gặp `NonWritableChannelException`.
- Tùy nền tảng, file system có thể yêu cầu khả năng ghi ngay cả khi xin shared lock, hoặc không hỗ trợ khóa theo vùng như mong muốn. Có thể phải mở thêm quyền đọc/ghi cho một số kiểu khóa theo hợp đồng API/nền tảng.

Khóa vùng [0,100) **không tự động mở rộng** sang vùng [100,200) nếu file lớn lên; hai vùng kề nhau không overlap. Để bảo vệ cả append mới, cần chọn vùng khóa bao trùm dữ liệu có thể được thêm hoặc xây giao thức khóa thống nhất cho append. Khóa tối thiểu một byte theo key/record chỉ an toàn khi mọi tiến trình tham gia đều biết và tuân thủ cùng quy ước byte-offset.

Tài liệu `FileLock` khuyến nghị chỉ dùng file locking khi thực sự cần phối hợp với chương trình khác vì khác biệt giữa các hệ điều hành; còn **nhiều thread trong một JVM** nên phối hợp bằng `synchronized`, `Lock` hoặc cấu trúc đồng bộ chuyên dụng, kết hợp với khóa file khi còn có nhiều process.

## 7. Giới hạn phụ thuộc hệ điều hành và file system

### 7.1. Advisory và mandatory locking

Ở một số hệ điều hành, file locks mang tính **advisory**: chỉ các tiến trình chủ động yêu cầu/kiểm tra khóa mới phối hợp; một tiến trình khác có thể bỏ qua cơ chế khóa và tiếp tục đọc/ghi. Ở nền tảng khác, một số kiểu khóa có thể được **cưỡng chế** (*mandatory*) ở tầng hệ điều hành.

Vì vậy, `lock()` trả về `FileLock` **không chứng minh** rằng mọi chương trình trên máy sẽ bị chặn khi truy cập vùng đó. Đặc biệt nếu có mã native, ứng dụng khác, thư viện bên thứ ba hoặc môi trường network file system, cần xác minh chính sách thực tế thay vì coi khóa là cơ chế phân quyền/bảo mật.

Khóa file **không phải ACL** hay cơ chế xác thực. Chương trình có quyền mở/ghi dữ liệu có thể không tôn trọng giao thức khóa ở một số nền tảng.

### 7.2. Khóa là tài nguyên của JVM nhưng có hiệu ứng liên tiến trình

Theo hợp đồng Java, khóa giữ thay mặt cho **toàn JVM**, không phải riêng thread hoặc object `FileChannel`. Nếu code tạo hai channel đến cùng file và xin vùng chồng lấn, điều kiện xung đột JVM vẫn áp dụng.

Ở một số hệ điều hành, khóa có thể gắn với *process* theo cách mà việc đóng một descriptor/channel **khác** đến cùng file cũng ảnh hưởng đến khóa. Vì thế, khi dùng file locks trong Java, tránh thiết kế dựa vào việc mở/đóng nhiều channel trên cùng file hoàn toàn độc lập, nhất là trong mã phức tạp hoặc khi ứng dụng có thư viện khác cùng mở file.

Hợp đồng `FileLock` còn lưu ý không nên dùng nhiều kênh đến cùng tệp trong JVM khi cần bảo đảm portable; dùng một channel duy nhất cho các khóa nếu có thể, và tránh đóng channel trong lúc có vùng khóa cần giữ.

### 7.3. Khóa theo vùng có thể chỉ được hỗ trợ một phần

Một số nền tảng/file system không hỗ trợ vùng khóa bất kỳ hoặc giới hạn chúng theo những ranh giới nhất định; một số trường hợp yêu cầu shared bị chuyển thành exclusive. Network file systems có thể có thêm giới hạn, độ trễ, trạng thái mất kết nối, lease hết hạn hoặc chính sách máy chủ khác với file system cục bộ.

Tài liệu API không bảo đảm mọi đặc điểm khóa đều giống nhau trên NTFS, ext4, NFS, SMB hoặc nhà cung cấp file system ảo. Nếu chương trình phải chạy đa nền tảng, hãy thử đúng tình huống có **hai JVM riêng**, các kiểu open/lock thực tế và network share thực tế của sản phẩm.

### 7.4. File mapping và thao tác trên file còn khóa

Một số hệ điều hành không cho phép ánh xạ một vùng đang khóa hoặc khóa một vùng đã được memory-map theo một số chế độ nhất định. Tương tác giữa file locking, `MappedByteBuffer`, truncate, rename hay delete không thể mặc định giống nhau trên mọi nền tảng.

Nếu nhiệm vụ sử dụng `FileChannel.map(...)`, hãy xem xét vòng đời mapping và file lock riêng. Đừng coi việc đóng channel đồng nghĩa với việc mọi mapping của file chắc chắn đã được hủy tức thời.

### 7.5. Thử nghiệm thực tế thay vì phụ thuộc kết quả một máy

Các ví dụ học thuật trên file system cục bộ chứng minh các quy tắc API có thể quan sát trong môi trường đang thử. Chúng **không xác nhận** rằng OS/network FS mục tiêu sản phẩm sẽ cung cấp mandatory locking, shared lock thật, hay bảo vệ file khi process/crash/mạng đứt. Những yêu cầu này phải được kiểm chứng trên nền tảng đích.

## 8. Vòng đời FileLock và vấn đề giải phóng

Sau khi cấp khóa thành công, vùng khóa vẫn tồn tại cho tới khi gọi `release()`, đóng channel liên quan hoặc JVM kết thúc (theo cơ chế hệ điều hành). `FileLock` triển khai `AutoCloseable`; cách tự nhiên nhất để quản lý là `try-with-resources`.

~~~java
try (FileChannel channel = FileChannel.open(path,
        StandardOpenOption.READ, StandardOpenOption.WRITE);
     FileLock lock = channel.lock(0, 4096, false)) {
    // Thao tác đọc/ghi vùng có khóa khi lock còn hiệu lực.
}
~~~

Đây là **mã minh họa**, giả sử `path` hợp lệ và file đã tồn tại; cần import `FileChannel`, `FileLock` và `StandardOpenOption`. Tài nguyên được đóng theo thứ tự ngược: nhả lock trước, rồi đóng channel.

- `lock.isValid()` trả false sau khi khóa được giải phóng hoặc không còn hiệu lực, chẳng hạn khi channel đóng.
- Đóng channel gắn với khóa làm các khóa được giữ qua channel đó không còn hiệu lực; thao tác đóng có thể có các tác động ngoài mong muốn khác tùy OS như đã nêu.
- Nếu một hàm chỉ **mượn** `FileChannel` hay `FileLock` do nơi khác quản lý, không tự đóng chúng nếu giao thức sở hữu chưa trao quyền.
- Thứ tự đóng quan trọng: hoàn tất dữ liệu cần bảo vệ trước khi nhả lock; kiểm tra và xử lý lỗi ghi/flush trước khi cho bên khác vào critical section.

Đừng giữ file lock qua lời gọi mạng dài hoặc tác vụ có thể treo nếu không cần: sẽ kéo dài thời gian tiến trình khác bị chặn. Với blocking `lock()`, cần có chiến lược hủy/giới hạn thời gian phù hợp, hoặc dùng `tryLock()` kết hợp retry có giới hạn và backoff tùy chính sách hệ thống.

## 9. File lock không đồng nghĩa dữ liệu đã bền hoặc thao tác đã nguyên tử

Một khóa file giúp phối hợp truy cập **giữa các bên tuân thủ giao thức**, nhưng không cung cấp:

- **Giao dịch nhiều bước:** viết record A, sửa index B rồi crash không được tự rollback chỉ nhờ file lock.
- **Tính nguyên tử của ghi dữ liệu:** một thao tác lớn có thể được hệ điều hành chia thành nhiều lần cập nhật, không phải transaction.
- **Durability:** nhả lock hoặc đóng đối tượng khóa không tự khẳng định dữ liệu đã được ghi bền xuống phương tiện lưu trữ theo yêu cầu nghiệp vụ.
- **Chống xóa/đổi tên bởi mọi chương trình:** hành vi phụ thuộc OS, quyền và kiểu khóa.

Khi cần yêu cầu lưu dữ liệu rõ ràng, có thể dùng `FileChannel.force(true)` để yêu cầu đồng bộ thay đổi nội dung và metadata ra thiết bị lưu trữ theo hợp đồng API và giới hạn thiết bị/file system. `force(true)` và `force(false)` khác nhau về yêu cầu đồng bộ metadata; cả hai **không biến nhiều lần ghi thành giao dịch nguyên tử**. Một cách tổ chức có thể là:

~~~java
try (FileLock lock = channel.lock(0, Long.MAX_VALUE, false)) {
    // Ghi dữ liệu/metadata theo giao thức ứng dụng đã xác định.
    channel.force(true);
} // Chỉ sau đó mới nhả khóa.
~~~

Đây là đoạn **minh họa**, giả sử `channel` là channel ghi, còn `try` này sở hữu riêng `FileLock`. Nếu cần recovery sau crash, phải bổ sung kiểm tra tính toàn vẹn, write-ahead log, copy-on-write hoặc hệ quản trị cơ sở dữ liệu có transaction phù hợp.

Trong ứng dụng nhiều process, mọi process phải nhất quán về **vùng khóa, thứ tự ghi dữ liệu, việc flush và lúc nhả khóa**; nếu một process ghi mà không xin lock, thuộc tính an toàn của giao thức tổng thể không còn được bảo đảm.

## 10. Phân biệt các ngoại lệ và hướng xử lý

| Hiện tượng/ngoại lệ | Diễn giải | Hướng xử lý |
| --- | --- | --- |
| `OverlappingFileLockException` | Khóa cùng JVM đã giữ hoặc đang chờ trên vùng chồng lấn | Sửa quản lý khóa nội bộ JVM, không retry vô hạn |
| `tryLock(...) == null` | Không cấp khóa ngay, thường do tiến trình khác có khóa xung đột | Báo bận, backoff/retry có giới hạn theo yêu cầu |
| `NonReadableChannelException` | Yêu cầu shared nhưng channel thiếu quyền đọc | Mở channel với quyền phù hợp |
| `NonWritableChannelException` | Yêu cầu exclusive nhưng channel thiếu quyền ghi | Mở channel với quyền phù hợp |
| `ClosedChannelException` | Channel đóng trước khi xin khóa | Sửa vòng đời resource |
| `FileLockInterruptionException` | Thread bị interrupt trong khi đang chờ khóa blocking | Tuân thủ chính sách hủy, không nuốt tín hiệu |
| `AsynchronousCloseException` | Channel bị đóng từ thread khác khi blocking operation diễn ra | Điều phối đóng và tác vụ |
| `IOException` | Lỗi OS/file system, unsupported operation hoặc lỗi I/O khác | Báo chi tiết, xử lý/retry có điều kiện |
| `IllegalArgumentException` | `position`/`size` âm hoặc vùng yêu cầu tràn `long` | Kiểm tra phép tính offset và size trước khi gọi |

Không bắt `Exception` rộng rồi tự cho rằng mọi lỗi đều là “file đang bị process khác lock”. Điều đó che mất lỗi cấu hình, quyền truy cập, lifecycle và những vấn đề lập trình nghiêm trọng.

Nếu hai vùng byte rời nhau nhưng vẫn liên quan cùng một invariant nghiệp vụ (ví dụ record dữ liệu và bảng chỉ mục), đặt khóa rời nhau có thể chưa đủ để bảo vệ thao tác cập nhật toàn vẹn. Hãy chọn phạm vi khóa bao trùm toàn bộ critical section cần tính nhất quán.

## 11. Mẫu thiết kế phối hợp an toàn hơn

Một thiết kế thực tế có nhiều thread và nhiều tiến trình có thể gồm hai lớp:

1. **Trong cùng JVM:** dùng `ReentrantLock`, `synchronized` hoặc lock manager theo file/region để chỉ một luồng được thực hiện thao tác xung đột tại một thời điểm. Các thread chờ ở lớp này trước khi gọi `FileChannel.lock`. Cách làm này tránh nhiều thread cùng JVM tự ném `OverlappingFileLockException` khi đồng thời xin khóa giống nhau.
2. **Giữa các JVM/process:** giữ `FileLock` trên vùng đã thống nhất, nếu môi trường hỗ trợ; ghi/đọc dữ liệu, đồng bộ khi cần, rồi nhả khóa. Có chiến lược khi không lấy được khóa trong thời gian cho phép.

Với ứng dụng hoạt động trên nhiều máy hoặc network FS mà cơ chế file lock không bảo đảm chính sách mong muốn, cân nhắc hệ quản trị dữ liệu/giao thức phối hợp chuyên dụng. Không nên tự xem file lock cục bộ là distributed lock mạnh.

### Checklist khi thiết kế

- Đã định nghĩa tệp và vùng byte khóa chính xác chưa?
- Có thread nào khác trong JVM đang giữ/đợi vùng chồng lấn không?
- Các channel và quyền mở có phù hợp shared/exclusive không?
- Ai có quyền đóng `FileLock` và `FileChannel`?
- Tất cả tiến trình liên quan có thực sự tuân thủ giao thức khóa không?
- Mục tiêu file system có hỗ trợ loại khóa/vùng khóa này không?
- Có đóng channel khác đến cùng tệp trong lúc cần giữ lock không?
- Việc ghi/đọc có giữ lock đủ lâu để bảo vệ invariant không?
- Đã kiểm soát crash, partial writes và durability ngoài lock chưa?
- Có kiểm thử riêng trên hệ điều hành và file system triển khai thật không?

## 12. Câu hỏi tự kiểm tra

1. Hai `FileChannel` mở cùng tệp trong một JVM, channel A đã khóa [0,20), channel B gọi `tryLock(10,5,false)`. Điều gì xảy ra? **`OverlappingFileLockException`**, vì [10,15) chồng lấn vùng đang được JVM giữ.
2. Nếu thay bằng `tryLock(20,5,false)` thì có chồng lấn không? **Không**, hai vùng [0,20) và [20,25) chỉ tiếp xúc ở biên, nhưng việc lấy khóa vẫn có thể gặp các giới hạn của file system/tiến trình khác.
3. Shared lock của cùng JVM có được xin thêm trên vùng đã có shared lock không? **Không thể mặc định**, vì API vẫn xét chồng lấn nội bộ JVM; có thể ném `OverlappingFileLockException`.
4. `tryLock()` trả `null` chứng minh gì? Chỉ rằng thao tác không được cấp khóa ngay; thường do xung đột với khóa từ process khác. **Không** phải dấu hiệu bắt buộc cho mọi kiểu xung đột.
5. `lock()` có luôn phải chờ khi JVM hiện tại đã có khóa vùng tương ứng? **Không**, có thể ném `OverlappingFileLockException` ngay.
6. `lock(..., true)` có chắc chắn tạo shared lock trên mọi nền tảng không? **Không**; một số hệ thống có thể nâng thành exclusive. Kiểm tra `isShared()`.
7. File lock có làm mọi chương trình khác không được đọc/ghi vùng dữ liệu không? **Không bảo đảm trên mọi hệ điều hành/file system**, nhất là môi trường advisory locking.
8. `channel.force(true)` có đồng nghĩa một chuỗi ghi dưới lock trở thành transaction nguyên tử không? **Không**; nó phục vụ yêu cầu đồng bộ dữ liệu/metadata, không tự tạo atomic transaction.
9. Khi hai luồng cùng JVM cần truy cập cùng một file, có nên xem file lock là primitive đồng bộ nội bộ chính không? **Không nên**; dùng đồng bộ trong JVM và quản lý file lock ở biên liên tiến trình.

## Tài liệu API tham khảo

- [Java SE 17 — FileLock](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileLock.html): phạm vi vùng khóa, vòng đời, shared/exclusive, giới hạn hệ điều hành.
- [Java SE 17 — FileChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html): các overload `lock`/`tryLock`, exceptions, `force`.
- [Java SE 17 — OverlappingFileLockException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/OverlappingFileLockException.html): ý nghĩa ngoại lệ cùng JVM.
- [Java SE 17 — StandardOpenOption](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardOpenOption.html): các quyền mở file ảnh hưởng đến loại khóa.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-locking-and-durability-005">Giải phóng lock khi đóng tài nguyên</a>

<details>
<summary>Xem chi tiết</summary>

# Giải phóng FileLock khi đóng tài nguyên trong Java NIO

Khi nhiều chương trình cùng thao tác trên một file, Java NIO cung cấp `FileChannel.lock()` và `tryLock()` để yêu cầu **khóa một vùng byte của file**. Đối tượng `java.nio.channels.FileLock` biểu diễn khóa đã nhận. Khóa cần có **vòng đời rõ ràng**: nhận khóa → thực hiện công việc trong phạm vi bảo vệ → giải phóng khóa → đóng channel. Nếu tài nguyên bị đóng sớm, khóa cũng mất hiệu lực; nếu không giải phóng đúng lúc, chương trình khác có thể phải chờ lâu hoặc không lấy được khóa.

Điểm cốt lõi: **một `FileLock` được giải phóng khi gọi `release()`/`close()`, hoặc khi đóng channel đã cấp khóa, hoặc khi JVM kết thúc — tùy sự kiện nào xảy ra trước.** Tuy nhiên, khi điều phối nhiều luồng/tiến trình và trên các filesystem khác nhau, phải hiểu thêm phạm vi khóa, quy tắc của hệ điều hành và cách xử lý ngoại lệ.

## 1. Vai trò của FileChannel và FileLock

Hai đối tượng có vai trò khác nhau:

- **`FileChannel`**: handle mở để đọc/ghi một file. Channel cung cấp `lock(...)` hoặc `tryLock(...)` để xin khóa và quản lý tài nguyên I/O.
- **`FileLock`**: token đại diện **một khóa đang được giữ**, có vùng byte `position()` và `size()`, chế độ `isShared()` và trạng thái `isValid()`.

Một `FileLock` hợp lệ chỉ khi khóa còn tồn tại và channel tương ứng vẫn mở; `isValid()` cho biết trạng thái khóa **ở thời điểm kiểm tra**, không phải cam kết nó sẽ vẫn hợp lệ mãi về sau.

```text
FileChannel.open(...)
        |
        v
    channel.lock() ------------> FileLock (valid)
        |                            |
        |                            | release() hoặc close()
        |                            v
        |                       FileLock (invalid)
        |
        +-- channel.close() -------> mọi lock cấp từ channel được giải phóng
                                     (invalid)

JVM kết thúc ---------------------> các khóa do JVM giữ được giải phóng
```

Khóa không làm file tự động được ghi bền vững xuống thiết bị, không sao lưu nội dung và không cung cấp transaction. Một khóa được giữ đúng vẫn có thể đi cùng dữ liệu chưa flush, chưa `force()`, hoặc mới chỉ được ghi một phần. **Quản lý khóa** và **quản lý độ bền dữ liệu** là hai trách nhiệm bổ sung cho nhau.

## 2. Ba con đường giải phóng khóa

Theo hợp đồng `FileLock`:

| Hành động | Tác động lên khóa | Tác động lên channel |
| --- | --- | --- |
| `lock.release()` | Giải phóng khóa hợp lệ; sau đó `isValid() == false`. | Channel vẫn mở nếu không bị đóng riêng. |
| `lock.close()` | Gọi `release()`; thích hợp với `try-with-resources`. | Channel vẫn mở. |
| `channel.close()` | Giải phóng các khóa nhận qua chính channel, khiến chúng không còn hợp lệ. | Channel đóng; không thể dùng để I/O tiếp. |
| JVM kết thúc | Các khóa đang giữ của JVM được hệ điều hành giải phóng. | Toàn bộ tài nguyên của tiến trình kết thúc. |

Thứ tự thực hành nên là **giải phóng `FileLock` trước, đóng `FileChannel` sau**. Dù đóng channel cũng giải phóng khóa, cách đóng tuần tự giúp giới hạn vùng được bảo vệ và làm chủ thời điểm giải phóng.

Sau khi `FileLock` mất hiệu lực, không sử dụng nó như một bằng chứng rằng tài nguyên vẫn bị khóa. Mở lại channel sau đó không làm token `FileLock` cũ hợp lệ trở lại: muốn có khóa mới phải **gọi `lock()`/`tryLock()` mới**.

**Đừng trông chờ chương trình tự giải phóng khi garbage collector chạy.** Sử dụng `try-with-resources` hoặc `finally`. JVM kết thúc là cơ chế thu hồi cuối cùng, không phải chiến lược quản lý lock cho ứng dụng chạy lâu.

## 3. Mẫu try-with-resources chuẩn: đóng lock trước channel

Dạng ngắn và an toàn về vòng đời:

```java
// Snippet: file là java.nio.file.Path đã tồn tại hoặc được tạo.
try (FileChannel channel = FileChannel.open(
        file, StandardOpenOption.READ, StandardOpenOption.WRITE);
     FileLock lock = channel.lock()) {
    // Chỉ làm việc cần được bảo vệ ở bên trong khối này.
    // Nếu cần ghi dữ liệu, channel phải có quyền WRITE.
}
```

Đoạn trên là **snippet**; cần import `FileChannel`, `FileLock` và `StandardOpenOption`. Không chạy độc lập vì biến `file` được định nghĩa ở ngữ cảnh bên ngoài.

**Tại sao thứ tự khai báo quan trọng?**

1. `try-with-resources` khởi tạo từ trái sang phải: mở `FileChannel` rồi xin `FileLock`.
2. Khi thoát khối, Java đóng **theo thứ tự ngược lại**: gọi `lock.close()` trước, rồi `channel.close()`.
3. Nếu code bên trong ném exception, Java **vẫn** thực hiện đóng cả hai tài nguyên. Ngoại lệ khi đóng được quản lý theo cơ chế suppressed exceptions; lỗi chính không bị tùy tiện thay thế bằng lỗi đóng.
4. Nếu `channel.lock()` ném exception trước khi tạo được `FileLock`, tài nguyên `channel` đã được khởi tạo thành công vẫn được đóng tự động.

Nếu chỉ mở channel rồi `channel.lock()` nhưng **quên giữ và giải phóng token**, khóa sẽ tồn tại đến khi channel đóng. Đối với một channel sống lâu (ví dụ gắn với một service), điều đó có thể làm các tiến trình hợp tác phải chờ quá thời gian mong muốn.

## 4. Ví dụ Java 17+ đầy đủ: release tường minh và giải phóng khi đóng channel

Ví dụ tạo file tạm riêng, nhận khóa trên một `FileChannel`, kiểm tra trạng thái trước/sau khi `FileLock` tự đóng; tiếp đó nhận một khóa khác và để việc đóng channel giải phóng khóa.

Lưu thành **`LockLifecycleDemo.java`**:

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class LockLifecycleDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("lock-lifecycle-", ".bin");
        try {
            FileLock releasedExplicitly;
            try (FileChannel channel = FileChannel.open(
                    file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                try (FileLock lock = channel.lock()) {
                    releasedExplicitly = lock;
                    System.out.println("during lock: " + lock.isValid());
                    channel.write(ByteBuffer.wrap(new byte[] {65, 66, 67}));
                }
                System.out.println("after FileLock.close: "
                        + releasedExplicitly.isValid());
            }

            FileLock releasedByChannel;
            try (FileChannel channel = FileChannel.open(
                    file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                releasedByChannel = channel.lock();
                System.out.println("before channel.close: "
                        + releasedByChannel.isValid());
                // Khi channel đóng, lock của channel tự được giải phóng.
            }
            System.out.println("after channel.close: "
                    + releasedByChannel.isValid());
            System.out.println("bytes: " + Files.size(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Biên dịch và chạy trên JDK 17+ có filesystem hỗ trợ `FileLock`:

```bash
javac -encoding UTF-8 LockLifecycleDemo.java
java LockLifecycleDemo
```

Stdout (đã đối chiếu bằng chương trình Java chạy thực tế):

```text
during lock: true
after FileLock.close: false
before channel.close: true
after channel.close: false
bytes: 3
```

**Diễn giải:**

- Lần đầu, `channel.lock()` xin khóa **exclusive** cho toàn bộ vùng khóa mặc định; `isValid()` trả `true` trong khối. `ByteBuffer` chứa ba byte `A B C` và `channel.write(...)` ghi chúng vào file.
- Khi rời khối `try (FileLock lock = ...)`, `lock.close()` tự gọi `release()`. Token `releasedExplicitly` đã mất hiệu lực, dù `channel` vẫn đang mở.
- Lần hai mở một channel mới trên cùng file. Khóa mới `releasedByChannel` được giữ đến khi `try (FileChannel channel = ...)` kết thúc. Không gọi `release()` riêng trong đoạn này để minh họa cơ chế **đóng channel giải phóng lock**.
- Sau khi đóng channel, `releasedByChannel.isValid()` là `false`. File có đúng ba byte vì chương trình chỉ ghi ba byte ở phiên đầu.

Ví dụ dùng một tiến trình, chạy tuần tự và không có writer cạnh tranh. Nếu OS/filesystem không hỗ trợ loại khóa được yêu cầu, việc nhận lock có thể thất bại; chương trình khi đó sẽ ném exception thay vì in đủ stdout trên.

## 5. Dùng release() trong finally khi không dùng try-with-resources

Nếu cấu trúc ứng dụng bắt buộc quản lý thủ công, giữ token và giải phóng trong `finally`:

```java
// Snippet: channel là FileChannel đang mở với WRITE.
FileLock lock = null;
try {
    lock = channel.lock();
    // Thao tác được bảo vệ ở đây.
} finally {
    if (lock != null && lock.isValid()) {
        lock.release();
    }
}
```

Đây là đoạn minh họa, yêu cầu `IOException` được xử lý hoặc truyền ra ngoài. `finally` chạy dù logic trong khối `try` phát sinh exception, nhưng **không phải** bảo đảm tuyệt đối trước việc tiến trình bị kill hoặc JVM dừng đột ngột. Nếu `release()` tự ném `IOException`, cần xử lý lỗi một cách có chủ đích; code nghiệp vụ quan trọng không nên bị che khuất trong log.

Nếu cả channel do hàm này sở hữu, cũng phải đóng channel trong `finally` hoặc `try-with-resources`. Không nên gọi `release()` sau khi channel đã đóng rồi dựa vào đó để “giải phóng lần nữa”. Theo hợp đồng `FileLock`, `release()` trên token không hợp lệ không còn thao tác khóa thực sự, và API còn quy định ngoại lệ trong một số trường hợp channel đóng; cách rõ ràng nhất là đóng lock **trước** khi đóng channel.

Dùng `try-with-resources` thường giúp code gọn hơn và xử lý cả trường hợp nhận khóa thất bại trong quá trình khởi tạo tài nguyên.

## 6. Thời điểm khóa còn hiệu lực: phạm vi critical section

Ví dụ cập nhật một số đếm trong file:

```text
Đúng về phạm vi:
xin lock ──> đọc dữ liệu cũ ──> tính toán ──> ghi dữ liệu mới
                                               |
                                               v
                                           giải phóng lock

Dễ bị race:
xin lock ──> đọc ──> giải phóng lock ──> tính toán ──> ghi
                          ^                            ^
                          |                            |
                 Writer khác có thể thay đổi file giữa hai bước
```

Khóa phải được giữ cho **toàn bộ chuỗi read-modify-write** khi các writer cần thấy thao tác như một critical section. Việc chỉ khóa riêng lần đọc rồi đóng lock trước lần ghi vẫn để lại khoảng trống cạnh tranh.

Nhưng **khóa đúng phạm vi không đồng nghĩa transaction**. Nếu tiến trình chết giữa lần ghi, có thể tồn tại dữ liệu dở dang dù tại thời điểm ghi chỉ có một writer hợp tác. Với dữ liệu cần phục hồi, phải thêm record framing/checksum, file tạm và chiến lược commit/rollback phù hợp.

Hạn chế giữ khóa trong khi làm việc tốn thời gian không liên quan như gọi HTTP, chờ người dùng nhập liệu hoặc sleep dài. Tính toán trước phần có thể làm ngoài critical section, rồi chỉ giữ khóa cho đoạn cần đọc trạng thái mới nhất và cập nhật.

## 7. lock() và tryLock(): khác nhau về cách chờ, giống về trách nhiệm giải phóng

`FileChannel.lock()` là lệnh **chờ** cho đến khi vùng được khóa, channel bị đóng hoặc luồng đang đợi bị interrupt. `tryLock()` **không chờ**: hoặc nhận `FileLock`, hoặc trả `null` nếu chương trình/tiến trình khác đã giữ khóa xung đột; trong những tình huống lỗi khác nó có thể ném exception.

Mẫu `tryLock` an toàn:

```java
// Snippet: channel mở với WRITE và chưa có khóa trùng trong JVM.
FileLock lock = channel.tryLock();
if (lock == null) {
    System.out.println("File đang được tiến trình khác sử dụng");
} else {
    try (lock) {
        // Critical section: chỉ chạy khi có khóa.
    }
}
```

Biến resource `lock` trong `try(lock)` hỗ trợ từ Java 9 nếu biến cuối cùng hoặc effectively final; ở ví dụ này không đổi giá trị sau khi gán. Không dùng token `null` để đi vào critical section.

Các trường hợp cần phân biệt:

| Tình trạng | Hành vi thường gặp |
| --- | --- |
| Một process khác giữ khóa xung đột | `lock()` chờ; `tryLock()` trả `null`. |
| Chính JVM đang giữ lock trùng vùng trên cùng file | `OverlappingFileLockException`, kể cả khi dùng channel khác trong JVM. |
| Cố xin exclusive lock bằng channel không có quyền ghi | `NonWritableChannelException`. |
| Cố xin shared lock bằng channel không có quyền đọc | `NonReadableChannelException`. |
| Channel đã đóng | `ClosedChannelException`. |
| Đóng channel khi một thread đang chờ `lock()` | Có thể ném `AsynchronousCloseException`. |
| Interrupt thread trong lúc chờ `lock()` | Có thể ném `FileLockInterruptionException`; trạng thái interrupt được thiết lập. |

Không nên “sửa” `OverlappingFileLockException` bằng vòng lặp `tryLock` bận vô hạn. Vì `FileLock` được nắm giữ **ở cấp JVM**, nhiều thread trong cùng JVM phải có cơ chế đồng bộ cấp ứng dụng khác (`synchronized`, `ReentrantLock`, một hàng đợi writer). File lock chủ yếu là cơ chế phối hợp **giữa các process** có tôn trọng giao thức khóa.

## 8. Khóa toàn file và khóa vùng byte

`channel.lock()` có ý nghĩa tương đương:

```java
FileLock lock = channel.lock(0L, Long.MAX_VALUE, false);
```

Trong đó `position=0`, `size=Long.MAX_VALUE`, `shared=false`; đây là yêu cầu **exclusive**. Đoạn chỉ minh họa, cần đóng/release `lock` sau khi xử lý.

Khi cần khóa một phân vùng dữ liệu:

```java
// Snippet: khóa vùng [100, 200) bằng channel ghi được.
try (FileLock lock = channel.lock(100L, 100L, false)) {
    // Chỉ thao tác với phần file theo quy ước khóa vùng của ứng dụng.
}
```

Phạm vi là `[position, position + size)`, tức điểm cuối **không thuộc** vùng đã khóa. Vùng khóa có thể vượt qua EOF; nó không tự mở rộng nội dung file. Một lock có `size` xác định không tự mở rộng cùng file; nếu file lớn thêm vượt phạm vi, phần mới không được che phủ.

Các hàm hỗ trợ `FileLock`:

- `position()`: vị trí bắt đầu.
- `size()`: kích thước vùng bị khóa.
- `overlaps(start, length)`: kiểm tra hai vùng có giao nhau không; nó không xin lock mới.
- `isShared()`: xác nhận lock đã nhận thuộc chế độ shared/exclusive. Một số nền tảng có thể chuyển yêu cầu shared thành exclusive.
- `acquiredBy()`: trả channel đã cấp khóa; với `FileChannel` có thể dùng `channel()`.

Không nên mặc định có thể chia file thành nhiều vùng khóa độc lập trên mọi filesystem; giới hạn native locking khác nhau theo nền tảng, đặc biệt là network filesystem.

## 9. Cạm bẫy: đóng channel khác có thể làm mất lock

Đây là khác biệt nền tảng được **JDK cảnh báo rõ**: trên một số hệ thống, **đóng một channel tới cùng file** có thể giải phóng **mọi file lock do JVM đang giữ trên file đó**, kể cả khóa được xin bằng **channel khác**.

Tình huống khái niệm:

```text
channel A ── lock vùng [0, 100) ──> lockA còn được kỳ vọng là valid
channel B ── cùng underlying file ──> đóng channel B
                                    |
                                    v
                Trên một số OS, lockA cũng có thể bị giải phóng.
```

Đây là hành vi **phụ thuộc hệ điều hành**; không đưa ra output cố định cho thí nghiệm này. Không suy rằng việc `lockA.isValid() == true` luôn tiếp tục đúng sau khi mọi channel khác đóng.

Hướng thiết kế được khuyến nghị:

1. Trong mỗi chương trình, ưu tiên **một channel do một thành phần sở hữu việc xin tất cả lock trên cùng file**.
2. Xác định rõ người sở hữu channel và người sở hữu khóa; tránh hàm phụ bất ngờ mở và đóng channel thứ hai lên cùng file đang khóa.
3. Khi cần nhiều thread truy cập, điều phối qua service quản lý channel hoặc đồng bộ trong JVM thay vì mỗi thread mở channel và tranh cùng vùng lock.
4. Không dựa vào một bài test nhỏ trên một OS để cam kết tính tương thích trên mọi platform.

Lưu ý rằng đóng channel **đã cấp lock** luôn giải phóng lock đó theo hợp đồng. Còn đóng **channel khác** có ảnh hưởng tới lock kia hay không mới là điểm không portable.

## 10. Shared lock, exclusive lock và giới hạn của OS

Hai chế độ:

- **Exclusive lock:** yêu cầu khóa độc quyền, không cho các process hợp tác lấy shared/exclusive lock trùng vùng trong thời gian nó còn hiệu lực.
- **Shared lock:** nhiều chương trình có thể cùng xin shared lock trên một vùng, nhưng xung đột với yêu cầu exclusive.

Để xin shared lock:

```java
// Snippet: channel cần được mở với READ.
try (FileLock lock = channel.lock(0L, Long.MAX_VALUE, true)) {
    System.out.println("shared actually: " + lock.isShared());
}
```

Một số platform không hỗ trợ shared lock: yêu cầu shared có thể bị chuyển thành exclusive. Vì thế kiểm tra `lock.isShared()` nếu chế độ thực tế có ý nghĩa đối với ứng dụng. Exclusive lock đòi channel có quyền ghi; shared lock đòi channel có quyền đọc.

**Không coi FileLock là hàng rào bảo mật tuyệt đối.** Trên một số OS, khóa là *advisory*: process không hợp tác vẫn có thể đọc/ghi file. Trên OS khác, khóa có thể ảnh hưởng bắt buộc tới các thao tác file; một số cấu hình/file system có thể có hạn chế mapping và locking đồng thời. Khi cần bảo vệ dữ liệu khỏi truy cập trái phép, dùng permission/ACL, kiểm soát danh tính và ranh giới hệ thống; file locking không thay thế chúng.

Đặc biệt trên NFS/SMB/network share, việc cấp/thu hồi lock có thể chịu ảnh hưởng giao thức và cấu hình máy chủ. Đưa việc kiểm thử lock timeout, reconnect, crash và quyền truy cập vào môi trường triển khai thực tế.

## 11. Đóng tài nguyên an toàn khi xảy ra exception

Một chương trình ghi file cần xử lý cả **lỗi công việc** và **lỗi dọn tài nguyên**. Nếu dùng `try-with-resources`, Java đóng tài nguyên theo thứ tự ngược với lúc khai báo. Nếu thân `try` ném exception và `close()` cũng ném exception, lỗi đóng thường được gắn vào lỗi chính dưới dạng **suppressed**.

Ví dụ mẫu:

```java
try (FileChannel channel = FileChannel.open(
        file, StandardOpenOption.WRITE);
     FileLock lock = channel.lock()) {
    writeRecord(channel);  // Có thể ném IOException.
} // FileLock.close() rồi FileChannel.close().
```

Đây là snippet; `file` và `writeRecord` phải được cung cấp bởi chương trình. Chương trình không cần viết `catch` để đảm bảo khóa được giải phóng. Nếu muốn ghi log lỗi kỹ lưỡng, có thể log `ex.getSuppressed()` bên cạnh exception chính.

**Lưu ý tránh deadlock nghiệp vụ:**

- Không giữ lock trong khi chờ một tác vụ khác cũng đang cần lock vùng trùng nhau.
- Không nhận nhiều khóa trên nhiều file theo thứ tự tùy ý giữa các process; quy định **thứ tự cố định** để giảm deadlock.
- Không chờ vô hạn khi ứng dụng có deadline: `lock()` có thể đợi. Thiết kế retry/backoff theo deadline với `tryLock()`, nhưng không polling quá nhanh.
- Nếu yêu cầu dừng tác vụ, xử lý interrupt và việc đóng channel với hiểu biết rằng thao tác `lock()` đang chờ có thể bị gián đoạn và ném exception.

Đóng lock sớm giúp giảm thời gian chiếm tài nguyên của hệ thống, nhưng phải đảm bảo tất cả công việc được bảo vệ đã hoàn thành.

## 12. Khóa và độ bền dữ liệu: release không đồng nghĩa commit

Giả sử chương trình chỉ có một writer được khóa hợp lệ. Khi thực hiện:

```java
try (FileLock lock = channel.lock()) {
    while (buffer.hasRemaining()) {
        channel.write(buffer);
    }
    channel.force(true);
}
```

đây là snippet dùng `channel` và `buffer` đã khai báo. `force(true)` yêu cầu các cập nhật nội dung và metadata phù hợp được đẩy xuống thiết bị lưu trữ theo hợp đồng `FileChannel`, với bảo đảm cụ thể phụ thuộc việc thiết bị là **cục bộ** hay **không cục bộ**.

Phân biệt rõ:

| Sự kiện | Nó làm gì | Nó không làm gì |
| --- | --- | --- |
| `lock.release()` / `lock.close()` | Giải phóng quyền giữ vùng lock. | Không bắt buộc dữ liệu đã được flush/sync xuống ổ đĩa. |
| `channel.close()` | Đóng handle, giải phóng các lock của channel. | Không tạo ra transaction có rollback. |
| `channel.force(false)` | Yêu cầu ghi các thay đổi nội dung thích hợp xuống thiết bị. | Không làm nhiều lần ghi trở thành atomic. |
| `channel.force(true)` | Yêu cầu cả content và metadata thích hợp. | Không xác nhận record có schema hợp lệ hoặc checksum đúng. |
| `SYNC` / `DSYNC` | Tùy chọn mở file yêu cầu đồng bộ dữ liệu khi cập nhật. | Không thay thế lock, record framing hay cơ chế commit. |

Nếu file chứa nhiều record, dùng thêm length-prefix, checksum, version, journal hoặc chiến lược thay file tạm tùy yêu cầu. Trong hệ thống yêu cầu crash consistency mạnh, một thao tác `write` thành công không có nghĩa toàn bộ chuỗi read-modify-write đã commit nguyên tử.

Việc sắp xếp `force` **trước khi** rời critical section có thể hợp lý khi writer khác chỉ được phép tiếp tục sau khi dữ liệu được yêu cầu đồng bộ, nhưng chi phí có thể cao. Phải chọn đúng yêu cầu nghiệp vụ; không nhất thiết force mỗi record nếu độ bền định kỳ là đủ.

## 13. Lỗi thường gặp

| Hiện tượng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| Process khác chờ khóa mãi | Quên `release` và channel vẫn sống lâu, hoặc critical section quá lớn. | Dùng `try-with-resources`, thu hẹp phạm vi, chọn deadline. |
| `isValid() == false` dù không tự gọi `release()` | Channel cấp khóa đã đóng, JVM kết thúc hoặc hệ thống thu hồi lock. | Rà soát vòng đời channel; nhận lock mới nếu cần. |
| Có lock nhưng process khác vẫn sửa file | Hệ điều hành dùng advisory locking hoặc process không hợp tác. | Yêu cầu mọi writer tuân thủ giao thức, kết hợp quyền truy cập. |
| `OverlappingFileLockException` | JVM đã có lock trùng vùng hoặc thread khác đang chờ lock trùng. | Dùng đồng bộ cấp JVM; tránh tranh cùng file trong một JVM. |
| `tryLock() == null` | Lock xung đột do process khác giữ. | Bỏ qua tác vụ, báo bận hoặc retry theo deadline/backoff. |
| `NonWritableChannelException` | Xin exclusive lock trên channel không có quyền ghi. | Mở channel với WRITE. |
| `NonReadableChannelException` | Xin shared lock trên channel không có quyền đọc. | Mở channel với READ. |
| `ClosedChannelException` | Channel đóng trong khi xin hoặc dùng lock. | Quản lý sở hữu tài nguyên, không sử dụng channel sau close. |
| Lock khác bị mất khi đóng channel phụ | Một số OS gắn lock theo file và có chính sách release rộng hơn. | Dùng channel quản lý lock duy nhất mỗi file trong chương trình. |
| Dữ liệu vẫn hỏng dù có lock | Ghi dở dang, crash, thiếu framing/transaction. | Dùng định dạng và khôi phục phù hợp, `force` theo chính sách. |

Một lỗi review phổ biến là đặt `FileChannel` trong `try-with-resources` quá **ngắn** so với khối logic cần được bảo vệ, rồi đưa việc ghi/cập nhật quan trọng ra ngoài sau khi channel đã đóng. Việc có dòng `channel.lock()` trong code chưa đủ: **thời điểm giải phóng lock quyết định chính xác đoạn mã nào có thể được bảo vệ**.

## 14. Bài tập thực hành

**Bài tập 1 — Quan sát trạng thái khóa.** Chạy ví dụ `LockLifecycleDemo` và ghi lại các điểm `isValid()` chuyển `true → false`. Thử chuyển chương trình sang một dạng `try (FileChannel channel = ...; FileLock lock = channel.lock())` và xác nhận thứ tự đóng ngược với thứ tự khai báo.

**Bài tập 2 — Chạy hai process.** Viết process A xin exclusive lock, chờ tín hiệu người dùng rồi thoát khỏi `try`. Process B dùng `tryLock()` kiểm tra trước, trong và sau lúc A giữ lock. Trên filesystem hỗ trợ, B sẽ nhận `null` khi A giữ khóa và có thể xin được sau khi A giải phóng. Không cần dùng sleep cố định để giả lập thời điểm giao nhau; dùng tín hiệu/sự kiện thử nghiệm.

**Bài tập 3 — Bảo vệ read-modify-write.** Lưu một số `long` tại offset 0 của file; cho hai process cùng đọc, cộng một, ghi lại. Yêu cầu giữ lock xuyên suốt việc đọc + ghi, sau đó so sánh với tình huống giải phóng lock trước khi ghi. Vì sao một lần đọc hoặc ghi riêng lẻ có thể vẫn thành công nhưng kết quả cuối sai?

**Bài tập 4 — Khóa vùng.** Chia file thành hai vùng byte không chồng lấn, xin lock riêng cho từng vùng và quan sát behavior theo OS. Kiểm tra `FileLock.overlaps` trên các khoảng `[0, 100)`, `[100, 200)`, `[50, 150)`. Giải thích vì sao hai vùng chạm ở biên nhưng không overlap.

**Bài tập 5 — Lỗi giữa critical section.** Sau khi nhận lock, chủ động ném một `IOException` thử nghiệm. Đảm bảo `try-with-resources` giải phóng tài nguyên và một process hợp tác có thể xin lock về sau. Bổ sung log cho suppressed exceptions khi dọn tài nguyên.

**Bài tập 6 — Durability.** Tạo một chương trình ghi đầy đủ record có checksum và gọi `channel.force(true)` trước khi `lock.close()`. Phân biệt được điều gì nếu chỉ giải phóng lock mà không force, và vì sao ngay cả force cũng không làm chuỗi ghi thành transaction.

## 15. Checklist khi đưa vào ứng dụng

- File/channel được mở với quyền phù hợp loại lock (exclusive cần WRITE, shared cần READ).
- Ai sở hữu và đóng `FileChannel`? Ai sở hữu và đóng `FileLock`? Có mở một channel thứ hai cùng file ngoài dự kiến không?
- Toàn bộ phần cần đồng bộ đã nằm **bên trong** critical section chưa? Có giữ lock qua gọi mạng hoặc chờ lâu không?
- Có dùng `try-with-resources`/`finally` và xử lý tốt các exception khi nhận/đóng lock không?
- Nhiều thread cùng JVM đã có đồng bộ riêng chưa? Các process không cùng JVM đã cùng tuân thủ protocol lock chưa?
- Nền tảng chạy thật có hỗ trợ loại lock/vùng lock theo yêu cầu không? Tác vụ có deadline hoặc kế hoạch timeout/retry không?
- Đã phân biệt **giải phóng lock**, **đóng channel**, **đồng bộ dữ liệu** và **transaction** chưa?
- Có kiểm tra quy trình recovery nếu tiến trình chết sau khi ghi một phần nhưng trước khi `release()`/`force()` không?

## 16. Tài liệu tham khảo chính thức

- [Java SE 21 — FileLock](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileLock.html): điều kiện khóa hợp lệ, `release()`, `close()`, trạng thái và các giới hạn phụ thuộc nền tảng.
- [Java SE 21 — FileChannel](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html): `lock()`, `tryLock()`, `force()`, khóa vùng, lỗi và lifecycle.
- [Java SE 21 — try-with-resources](https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html): thứ tự và cách Java tự đóng tài nguyên.

**Ghi nhớ:** `FileLock` là tài nguyên `AutoCloseable`. Dùng `try-with-resources` để giải phóng khóa **trước khi** đóng channel; đóng channel cấp khóa cũng làm khóa mất hiệu lực. Tổ chức ownership rõ ràng, không trộn nhiều channel xin khóa cùng file tùy tiện, và đừng đánh đồng lock release với durable commit.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-locking-and-durability-006">Phân biệt flush, FileDescriptor.sync và FileChannel.force</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt `flush()`, `FileDescriptor.sync()` và `FileChannel.force()` trong Java

## 1. Vấn đề: “đã ghi” có nghĩa là đã bền vững chưa?

Ứng dụng ghi một bản ghi giao dịch, một tệp cấu hình hoặc nhật ký phục hồi. Lệnh `write()` đã trả về, chương trình đọc lại được dữ liệu, nhưng máy bị mất điện ngay sau đó. Có chắc những byte này còn nguyên khi khởi động lại không?

Trong Java I/O, cần phân biệt **dữ liệu đã ra khỏi bộ đệm do ứng dụng quản lý** với **yêu cầu đồng bộ dữ liệu xuống thiết bị lưu trữ**. Ba API thường bị nhầm là:

- `flush()` của `OutputStream`/`Writer`: đẩy dữ liệu đang được các lớp stream/writer đệm đến đích phía dưới, theo hợp đồng implementation; **không tự bảo đảm lưu xuống đĩa vật lý**.
- `FileDescriptor.sync()`: yêu cầu hệ thống đồng bộ các bộ đệm liên quan đến **descriptor** xuống thiết bị; chỉ có thể tác động lên dữ liệu đã đi xuống phía dưới descriptor.
- `FileChannel.force(boolean metaData)`: yêu cầu đồng bộ các cập nhật của **tệp gắn với channel** xuống thiết bị. Tham số `metaData` quyết định có yêu cầu đồng bộ metadata cùng nội dung hay không.

Mục tiêu của bài là hiểu chính xác **đường đi của dữ liệu**, thứ tự thao tác, mức bảo đảm và giới hạn của từng API, sau đó áp dụng vào những lần ghi có yêu cầu durability.

## 2. Các tầng bộ đệm từ ứng dụng đến storage

Một đường ghi byte đơn giản có thể đi qua nhiều tầng:

```text
Ứng dụng: write("ABC")
        |
        v
BufferedWriter / OutputStreamWriter / BufferedOutputStream
(đệm ký tự, trạng thái bộ mã hóa hoặc đệm byte trong JVM)
        |                 ^
        | flush()         | dữ liệu chưa flush có thể còn ở đây
        v
FileOutputStream / file descriptor / FileChannel
        |
        v
Hệ điều hành, filesystem và page cache
        |
        | sync() hoặc force(...)
        v
Thiết bị lưu trữ / dịch vụ lưu trữ thực tế
        |
        v
Khả năng tồn tại qua lỗi hệ thống
(phụ thuộc hợp đồng Java, OS, driver, thiết bị và môi trường)
```

Đây là sơ đồ khái niệm, không có nghĩa mọi chương trình đều có tất cả các tầng. Ví dụ ghi thẳng qua `FileChannel` không cần `BufferedOutputStream`; ghi qua `BufferedWriter` có thể trải qua cả bộ đệm ký tự và bước mã hóa.

Hai câu hỏi khác nhau:

1. **Visibility (nhìn thấy):** một người đọc khác hoặc một file handle mới có thể quan sát byte đã được đưa đến filesystem chưa?
2. **Durability (bền vững):** hệ thống đã hoàn tất lời gọi đồng bộ theo hợp đồng, nhằm giảm nguy cơ mất cập nhật khi lỗi/crash chưa?

Thấy dữ liệu bằng `Files.readString(path)` ngay sau `write()` **không chứng minh** dữ liệu đã được đồng bộ xuống thiết bị. Dữ liệu có thể được phục vụ từ page cache của hệ điều hành.

## 3. Bảng so sánh nhanh

| API | Tầng tác động trực tiếp | Khi nên dùng | Điều không nên suy ra |
| --- | --- | --- | --- |
| `BufferedOutputStream.flush()` | Đệm byte của stream và gọi `flush()` tầng dưới | Muốn đẩy dữ liệu đang ở bộ đệm JVM xuống sink bên dưới | Không phải cam kết `fsync` hoặc không mất dữ liệu khi mất điện |
| `BufferedWriter.flush()` / `OutputStreamWriter.flush()` | Đệm ký tự/encoder và luồng bên dưới theo hợp đồng | Muốn hoàn tất việc chuyển text đang đệm xuống luồng byte | Không xác nhận độ bền của file trên storage |
| `OutputStream.flush()` mặc định | Implementation mặc định không làm gì | Điểm mở rộng cho subclass | Không mặc định có đệm để xả |
| `FileOutputStream.flush()` | Kế thừa hành vi từ `OutputStream` | Không phải API đồng bộ thiết bị | Gọi riêng nó không tương đương `FileDescriptor.sync()` |
| `FileDescriptor.sync()` | Các system buffers liên quan descriptor | Muốn đồng bộ ghi qua descriptor tới thiết bị | Không xả hộ `BufferedOutputStream` chưa flush |
| `FileChannel.force(false)` | Dữ liệu file do channel cập nhật theo phạm vi bảo đảm | Cần force nội dung | Không yêu cầu đầy đủ metadata theo hợp đồng |
| `FileChannel.force(true)` | Nội dung và metadata theo phạm vi bảo đảm | Cần cả metadata | Không phải commit transaction nhiều file hay đồng bộ tên trong thư mục |
| `StandardOpenOption.DSYNC` / `SYNC` | Chính sách đồng bộ khi mở file | Muốn các cập nhật nội dung / nội dung và metadata được ghi đồng bộ | Không xả đệm byte riêng ở tầng ứng dụng chưa chuyển xuống channel |

**Mấu chốt:** `flush()` xử lý “byte đã đi qua lớp bọc chưa?”, còn `sync()/force()` xử lý “cập nhật đã được yêu cầu đẩy ra storage chưa?”. Với pipeline nhiều lớp, thường phải **flush trước, sync/force sau**.

## 4. `flush()`: đẩy bộ đệm ứng dụng xuống tầng dưới

### 4.1. Hợp đồng chung của `OutputStream.flush()`

`OutputStream` triển khai `Flushable`. Theo Java SE 17, `flush()` thể hiện yêu cầu rằng những byte được stream implementation giữ lại trong bộ đệm cần được chuyển ngay đến đích mà stream đó quản lý. Nếu đích là abstraction của hệ điều hành như file, **flush chỉ bảo đảm byte đã được chuyển cho hệ điều hành theo hợp đồng, không bảo đảm đã được ghi xuống thiết bị vật lý**.

Implementation mặc định của `OutputStream.flush()` không làm gì. Vì vậy câu “mọi `flush()` đều đẩy page cache xuống đĩa” là sai.

### 4.2. `BufferedOutputStream` có bộ đệm byte thật

`BufferedOutputStream` chứa một mảng byte nội bộ. Khi gọi `write(int)`, byte có thể chỉ ở trong mảng đó. `flush()` sẽ ghi các byte đang đệm ra `OutputStream` phía dưới và tiếp tục gọi `flush()` bên dưới.

```java
FileOutputStream raw = new FileOutputStream(file);
BufferedOutputStream buffered = new BufferedOutputStream(raw);

buffered.write('A');
// 'A' có thể còn trong bộ đệm của buffered, raw chưa nhận byte.

buffered.flush();
// Buffer đã đẩy 'A' xuống raw/FileOutputStream,
// nhưng chưa có cam kết tương đương fsync.

// Quản lý close bằng try-with-resources trong chương trình thật.
```

*Đoạn mã trên chỉ minh họa*: `file` cần là `File` hợp lệ, cần import và xử lý `IOException`. `flush()` có thể ném `IOException` nếu tầng dưới không nhận được dữ liệu.

Khi ghi một mảng rất lớn, một số stream có thể chuyển byte thẳng xuống tầng dưới mà không giữ toàn bộ trong buffer; điều này **không đổi ý nghĩa** của `flush()`.

### 4.3. Flush của writer còn liên quan mã hóa ký tự

`BufferedWriter` chứa ký tự; `OutputStreamWriter` chuyển ký tự thành byte theo charset. Nếu dữ liệu được gửi qua nhiều lớp:

```text
BufferedWriter
     |
     v
OutputStreamWriter (UTF-8)
     |
     v
BufferedOutputStream
     |
     v
FileOutputStream
```

Lời gọi `writer.flush()` ở tầng ngoài có nhiệm vụ đẩy dữ liệu đang đệm theo chuỗi wrapper xuống tầng dưới. Khi cần hoàn tất một file text độc lập, `close()` sau khi hoàn tất ghi sẽ xử lý vòng đời của writer; sau đó vẫn phải tách riêng yêu cầu durability của storage nếu nghiệp vụ cần.

Đừng gọi `flush()` trên một stream khác không phải tầng ngoài đang giữ dữ liệu và tin rằng mọi byte đều đã đi xuống descriptor. Ví dụ `fileOutputStream.getFD().sync()` **không** tự đọc và làm rỗng mảng trong `BufferedOutputStream` đang bọc `fileOutputStream`.

### 4.4. `flush()` không đồng nghĩa `close()`

- `flush()` giúp đẩy dữ liệu đang đệm mà **stream vẫn mở**.
- `close()` đóng tài nguyên; nhiều lớp buffered/writer xả dữ liệu khi đóng.
- `close()` không phải API portable hứa hẹn mọi file đã được sync xuống thiết bị.
- Với stream còn được dùng tiếp, `flush()` hợp lý khi giao thức yêu cầu phía nhận sớm thấy byte, nhưng không thay thế sync.
- Với resource phải kết thúc, dùng `try-with-resources` để đóng có kỷ luật và xử lý `IOException` có thể phát sinh trong `write`, `flush` hoặc `close`.

## 5. `FileDescriptor.sync()`: đồng bộ các bộ đệm của descriptor

`java.io.FileDescriptor` đại diện cho kết nối mức thấp tới file, socket hoặc resource do hệ điều hành quản lý. `FileOutputStream.getFD()` trả descriptor của stream file đó.

Phương thức:

```java
public void sync() throws SyncFailedException
```

Theo hợp đồng Java SE 17, `sync()` yêu cầu **các system buffers liên quan FileDescriptor** được đồng bộ với thiết bị cơ sở. Khi thành công với một file lưu trên physical storage, mục đích là đưa những bản sửa đổi đang ở bộ nhớ do hệ thống quản lý tới media liên quan, theo bảo đảm mà môi trường có thể thực hiện.

**Giới hạn rất quan trọng:** `sync()` chỉ tác động đến các bộ đệm **ở phía dưới FileDescriptor**. Các byte còn ở `BufferedOutputStream`, `BufferedWriter` hay bộ đệm do ứng dụng tự quản lý phải được **flush** hoặc ghi xuống descriptor trước.

Do đó, với `BufferedOutputStream`:

```java
buffered.write(recordBytes);
buffered.flush();       // 1. JVM buffer -> FileOutputStream/OS
file.getFD().sync();    // 2. Yêu cầu OS đồng bộ descriptor
```

Đoạn này minh họa logic thứ tự, cần khai báo đối tượng, import và xử lý các checked exceptions. Nó cũng giả định `file` chính là `FileOutputStream` nằm dưới `buffered`.

### `SyncFailedException` có ý nghĩa gì?

`FileDescriptor.sync()` có thể ném `SyncFailedException` khi không thể xả buffers hoặc hệ thống không bảo đảm được các buffer đã đồng bộ với thiết bị. Không nên bắt rồi lặng lẽ coi một giao dịch quan trọng là thành công; application phải chuyển lỗi đến tầng phù hợp (retry có kiểm soát, báo lỗi, không xác nhận commit).

`FileDescriptor.valid()` chỉ kiểm tra descriptor có đại diện kết nối đang mở/hợp lệ hay không; nó **không chứng minh** yêu cầu sync đã thành công.

## 6. Ví dụ Java 17+ chạy được: flush trước, sync sau

Ví dụ thực nghiệm sau thiết kế để chứng minh **khả năng nhìn thấy byte qua một file handle khác** khi byte còn trong buffer của Java. Nó **không** kiểm thử mất điện hoặc bảo đảm durability; các kết quả đọc tệp chỉ xác nhận byte đã xuống tầng thấp hơn.

Tạo **`FlushAndSyncDemo.java`**:

```java
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FlushAndSyncDemo {
    public static void main(String[] args) throws Exception {
        Path path = Files.createTempFile("flush-sync-", ".dat");

        try {
            try (FileOutputStream raw = new FileOutputStream(path.toFile());
                 BufferedOutputStream buffered =
                         new BufferedOutputStream(raw, 8192)) {

                buffered.write('A');
                System.out.println("before flush="
                        + Files.size(path));

                buffered.flush();
                System.out.println("after flush="
                        + Files.readString(path, StandardCharsets.US_ASCII));

                // 'B' mới chỉ nằm trong BufferedOutputStream.
                buffered.write('B');

                // sync() không xả byte vẫn còn trong buffered.
                raw.getFD().sync();
                System.out.println("sync before second flush="
                        + Files.readString(path, StandardCharsets.US_ASCII));

                buffered.flush();
                raw.getFD().sync();

                System.out.println("flush then sync="
                        + Files.readString(path, StandardCharsets.US_ASCII));
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
```

Biên dịch/chạy trong thư mục chứa file:

```bash
javac FlushAndSyncDemo.java
java FlushAndSyncDemo
```

Stdout khi các thao tác I/O và `sync()` đều thành công:

```text
before flush=0
after flush=A
sync before second flush=A
flush then sync=AB
```

Giải thích thứ tự:

1. `Files.createTempFile` tạo tệp rỗng. `FileOutputStream` mở tệp để ghi; buffer 8192 byte của `BufferedOutputStream` giữ lại ký tự ASCII `A`, nên file vẫn dài 0 khi chưa flush.
2. `buffered.flush()` đẩy `A` xuống `FileOutputStream`. Một lần đọc khác có thể thấy `A`, nhưng điều đó **chưa chứng minh** byte đã bền vững khi thiết bị mất nguồn.
3. `buffered.write('B')` giữ thêm `B` trong memory của JVM. `raw.getFD().sync()` đồng bộ những gì đã đến descriptor, **không** chuyển `B` khỏi buffer ứng dụng; vì vậy lần đọc vẫn là `A`.
4. `buffered.flush()` tiếp tục gửi `B` xuống descriptor; `raw.getFD().sync()` sau đó yêu cầu đồng bộ. Lúc này file chứa `AB` theo đường đọc thông thường.
5. `try-with-resources` đóng stream và `finally` xóa tệp tạm.

Nếu `sync()` bị hệ thống từ chối, chương trình ném lỗi và stdout có thể dừng trước dòng cuối. Đừng suy luận từ output rằng bạn đã mô phỏng được power loss; Java không có API chuẩn tái tạo mất nguồn để chứng minh độ bền bằng một lần test thông thường.

## 7. `FileChannel.force(boolean metaData)`: đồng bộ theo file channel

`java.nio.channels.FileChannel` cung cấp:

```java
public abstract void force(boolean metaData) throws IOException
```

Mục đích là yêu cầu các cập nhật của **tệp gắn với FileChannel** được ghi xuống thiết bị chứa tệp. Theo Java SE 17:

- Nếu tệp trên **thiết bị lưu trữ cục bộ**, khi `force` hoàn tất thành công, Java công bố bảo đảm các thay đổi có liên quan từ lúc channel được tạo hoặc từ lần force trước đã được ghi tới thiết bị đó, trong **phạm vi các thao tác API được bảo đảm**.
- Nếu tệp không nằm trên thiết bị cục bộ (chẳng hạn một số kiểu filesystem mạng), Java **không** đưa ra cùng bảo đảm này.
- `force(false)` yêu cầu đồng bộ **nội dung** file; nó **không bắt buộc** đồng bộ đầy đủ metadata.
- `force(true)` yêu cầu đồng bộ **nội dung cùng metadata** của file. Việc chọn `true` có thể cần thêm I/O; ảnh hưởng chính xác của cờ phụ thuộc OS.
- `force` có thể thực hiện I/O cả khi channel mở chỉ để đọc do OS có thể cập nhật metadata như last-access time.
- `force` có thể ném `ClosedChannelException` nếu channel đã đóng hoặc `IOException` nếu lỗi I/O.

### 7.1. `metaData = false` và `metaData = true`

Metadata có thể bao gồm kích thước, mốc thời gian và các thuộc tính mà filesystem quản lý. Không nên dựa vào việc từng OS coi thuộc tính nào “cần thiết” hoặc “không cần thiết” khi force với `false`; hợp đồng Java chỉ chỉ định loại cập nhật cần force.

```java
try (FileChannel channel = FileChannel.open(
        path,
        StandardOpenOption.WRITE)) {

    // Giả sử write(...) đã đưa toàn bộ nội dung cần ghi vào channel.
    channel.force(false); // Yêu cầu force nội dung
    channel.force(true);  // Yêu cầu force nội dung + metadata
}
```

Đây là snippet (cần khai báo `path` và `write` tương ứng). Gọi cả hai liên tiếp **thường không cần thiết**: chọn đúng mức độ cho lần đồng bộ của nghiệp vụ. Nếu công việc đã gọi `force(true)` ở điểm cam kết, không cần gọi thêm `force(false)` chỉ để “an toàn gấp đôi”.

### 7.2. Những cập nhật được `FileChannel.force()` bao phủ

Java SE 17 bảo đảm `FileChannel.force()` đối với thay đổi tạo ra bằng **các phương thức do `FileChannel` định nghĩa**. Nếu channel lấy qua `getChannel()` của `FileOutputStream` hoặc `RandomAccessFile`, đừng tự suy rộng phạm vi bảo đảm ngoài tài liệu API; với code quan trọng, hãy dùng cùng pipeline ghi/force rõ ràng và kiểm tra hợp đồng triển khai.

Đặc biệt, việc sửa **`MappedByteBuffer`** tạo từ `FileChannel.map` có quy tắc riêng: `FileChannel.force()` **có thể hoặc không** force các cập nhật được tạo qua mapped buffer. Hãy dùng **`MappedByteBuffer.force()`** để yêu cầu đồng bộ thay đổi của vùng map theo hợp đồng API của mapped buffer.

`force()` không đọc hay làm rỗng một buffer do ứng dụng tự quản lý. Nếu ứng dụng có `BufferedOutputStream` ở phía trên, luôn chuyển byte xuống filesystem trước khi gọi force trên channel liên quan.

## 8. Ví dụ Java 17+ chạy được: force nội dung và metadata

Tạo **`FileChannelForceDemo.java`**:

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileChannelForceDemo {
    private static void writeAll(FileChannel channel, String text)
            throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(
                text.getBytes(StandardCharsets.US_ASCII));

        while (buffer.hasRemaining()) {
            int written = channel.write(buffer);
            if (written == 0) {
                throw new IOException("FileChannel made no progress");
            }
        }
    }

    public static void main(String[] args) throws IOException {
        Path path = Files.createTempFile("file-force-", ".txt");

        try {
            try (FileChannel channel = FileChannel.open(
                    path,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {

                writeAll(channel, "DATA");
                channel.force(false); // force nội dung

                writeAll(channel, "!");
                channel.force(true);  // force nội dung + metadata

                System.out.println("size=" + channel.size());
            }

            System.out.println("text="
                    + Files.readString(path, StandardCharsets.US_ASCII));
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
```

```bash
javac FileChannelForceDemo.java
java FileChannelForceDemo
```

Stdout khi cả hai lần force thành công:

```text
size=5
text=DATA!
```

Điểm chính:

1. `FileChannel.open` mở file đã tồn tại với quyền ghi, chế độ `TRUNCATE_EXISTING` đặt length về 0. Tùy môi trường, mở/ghi/force có thể ném `IOException`.
2. `writeAll` ghi đến khi hết byte trong `ByteBuffer`; nó không mặc định một `channel.write` đã ghi đủ dữ liệu.
3. `force(false)` ở lần đầu yêu cầu đồng bộ nội dung `DATA`.
4. Sau khi ghi dấu `!`, `force(true)` yêu cầu đồng bộ nội dung mới và metadata theo hợp đồng.
5. `size()` và lần đọc sau khi đóng xác nhận logical content của file là `DATA!`. **Chúng không kiểm thử khả năng sống sót qua mất điện.**

Không bao giờ coi `System.out.println("text=DATA!")` là bằng chứng thiết bị đã thực thi thành công mọi cơ chế chống mất điện. Bằng chứng ở đây là **`force` trả về mà không ném lỗi**, dưới đúng phạm vi bảo đảm của API và môi trường lưu trữ.

## 9. Ba API là ba mức khác nhau, không thay thế trực tiếp

Ví dụ cùng payload `"ORDER=42"`, có ba cách xử lý:

**Chỉ ghi rồi flush:**

```java
buffered.write(bytes);
buffered.flush();
```

Ý nghĩa: đẩy những byte đang ở bộ đệm ứng dụng xuống stream bên dưới, để phía nhận/OS sớm tiếp nhận. Thích hợp khi yêu cầu là đưa dữ liệu ra tầng kế tiếp, **không đủ** để hứa dữ liệu sống sót qua hệ thống crash.

**Ghi rồi flush và sync descriptor:**

```java
buffered.write(bytes);
buffered.flush();
rawFileOutputStream.getFD().sync();
```

Ý nghĩa: sau khi byte đã đi tới file descriptor, yêu cầu đồng bộ các system buffers liên quan descriptor.

**Ghi thẳng qua FileChannel rồi force:**

```java
ByteBuffer buffer = ByteBuffer.wrap(bytes);
while (buffer.hasRemaining()) {
    channel.write(buffer);
}
channel.force(true);
```

Ý nghĩa: với file channel, yêu cầu đồng bộ các cập nhật của file bao gồm metadata theo hợp đồng Java. Đoạn này giả định file channel mở `WRITE`, `bytes` đã có và không có đường ghi song song chưa được phối hợp.

Ba snippet này **không phải chương trình độc lập**; chúng mô tả các mô hình ứng dụng lựa chọn tùy API gốc. Không ghép ngẫu nhiên descriptor của file A với channel của file B rồi tưởng toàn bộ dữ liệu đã được đồng bộ.

## 10. Chọn `StandardOpenOption.SYNC` hoặc `DSYNC` khi nào?

Ngoài việc gọi `force()` sau một nhóm thao tác, Java NIO có option mở tệp yêu cầu ghi đồng bộ:

- `StandardOpenOption.DSYNC`: yêu cầu mỗi cập nhật **nội dung** file được ghi đồng bộ tới thiết bị.
- `StandardOpenOption.SYNC`: yêu cầu mỗi cập nhật **nội dung hoặc metadata** file được ghi đồng bộ tới thiết bị.

Ví dụ:

```java
try (FileChannel ch = FileChannel.open(
        path,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.DSYNC)) {

    // Các lần ch.write(...) thực hiện theo chế độ DSYNC.
}
```

Đây là snippet; cần `Path path`, import, và xử lý `IOException`. Không có ý nghĩa chọn `DSYNC` trên `BufferedOutputStream` nếu byte vẫn chưa được chuyển xuống `FileChannel`: option trên file channel không ép một buffer tầng ứng dụng khác phải flush.

**Khác biệt về hiệu năng:** gọi `force` theo batch thường ít lần đồng bộ hơn so với yêu cầu sync ở **mỗi lần cập nhật**. Đổi lại, nếu crash trước checkpoint force, các thay đổi từ checkpoint trước có thể chưa bền vững. Mô hình `SYNC/DSYNC` có thể tăng độ trễ cho từng lần ghi. Quyết định dựa trên yêu cầu mất dữ liệu tối đa được chấp nhận, kích thước batch, latency và đặc điểm filesystem.

Nếu chạy trên provider đặc biệt hoặc hệ thống tệp không hỗ trợ option yêu cầu, `FileChannel.open` có thể ném ngoại lệ. Không giả định độ bền và hiệu suất giống nhau trên volume mạng, bộ nhớ tạm, thiết bị ảo hoặc container.

## 11. Trường hợp `MappedByteBuffer`: phải hiểu đúng `force()` của map

Một vùng file mapping được lấy từ `FileChannel.map` có thể cho phép đọc/ghi trực tiếp qua `MappedByteBuffer`:

```java
try (FileChannel ch = FileChannel.open(
        path,
        StandardOpenOption.READ,
        StandardOpenOption.WRITE)) {

    MappedByteBuffer map = ch.map(
            FileChannel.MapMode.READ_WRITE, 0, 4096);

    map.put(0, (byte) 42);
    map.force();
}
```

**Đoạn mã minh họa, không độc lập**; nó giả định file/OS hỗ trợ vùng map tương ứng (mapping có thể làm dài file theo ngữ nghĩa API khi cần), cần import và xử lý exception. Quan trọng là:

- `FileChannel.force()` được Java 17 mô tả là **có thể hoặc không** bao phủ cập nhật qua mapped buffer.
- `MappedByteBuffer.force()` được thiết kế để yêu cầu các thay đổi của buffer mapping được ghi xuống thiết bị chứa mapped file (có giới hạn tương tự tùy loại thiết bị/OS/provider).
- Với chương trình thực tế, cần kiểm tra cách xử lý vòng đời map, giới hạn mapping, đồng bộ các luồng và lỗi khi thiết bị/region không còn truy cập được.

Không dùng một lệnh `channel.force(true)` như bảo đảm tổng quát cho mọi thay đổi từng được thực hiện qua `MappedByteBuffer`.

## 12. Durability chưa đồng nghĩa transaction hay atomicity

`sync()` hoặc `force()` giải quyết **đồng bộ dữ liệu**, không thay thế:

1. **Tính nguyên tử của nhiều lệnh ghi:** một ghi lớn hoặc nhiều record có thể chỉ ghi được một phần nếu ứng dụng/hệ thống thất bại giữa chừng. `force()` không biến chuỗi ghi thành “all-or-nothing”.
2. **Tính nhất quán của định dạng:** header và payload có thể không tương thích sau crash nếu không thiết kế thứ tự ghi, version, checksum hoặc log phục hồi.
3. **Tính nguyên tử thay thế file:** muốn publish một cấu hình mới, chiến lược thường là ghi file tạm cùng directory, force nội dung, sau đó đổi tên/move phù hợp. `Files.move` với `ATOMIC_MOVE` chỉ bảo đảm thao tác đổi tên/move mang tính atomic **nếu filesystem hỗ trợ**, không mặc nhiên đồng bộ bền vững cả directory entry.
4. **Tính bền vững của metadata thư mục:** force file không tự là một lời hứa portable rằng tên mới, thao tác tạo hoặc rename ở parent directory cũng đã được đồng bộ. Java SE không cung cấp một API cross-platform đơn giản bảo đảm `fsync(parent-directory)` trên mọi provider.
5. **Giao dịch nhiều file:** sync từng file không cung cấp commit nguyên tử của cả tập file. Cần transaction protocol hoặc hệ thống lưu trữ thiết kế cho yêu cầu ấy.

Một crash-safe protocol có thể cần record header, checksums, write-ahead log, commit marker và recovery logic. `flush/sync/force` chỉ là **một phần của thiết kế** đó.

### Ví dụ quy trình lưu cấu hình an toàn hơn

Với tệp cấu hình dùng để thay thế bản cũ:

```text
1. Tạo file tạm trong cùng thư mục với file chính.
2. Ghi toàn bộ nội dung mới và kiểm tra lỗi.
3. FileChannel.force(true) trên file tạm (sau khi dữ liệu đã tới channel).
4. Đóng channel/file tạm.
5. Files.move(temp, target, ATOMIC_MOVE, ...), nếu provider hỗ trợ.
6. Giải quyết durability của thao tác đổi tên/directory theo OS,
   filesystem và yêu cầu ứng dụng.
```

Đây là **luồng thiết kế**, không phải bảo đảm portable crash-safety chỉ bằng năm bước đầu. Nếu `ATOMIC_MOVE` không hỗ trợ, có thể nhận `AtomicMoveNotSupportedException`. Việc thay thế file đích và cách hoạt động của option bổ sung phải được xác định theo hợp đồng và filesystem; không tự giả định rollback hoặc không mất dữ liệu khi crash ở mọi thời điểm.

## 13. Đường ghi và trách nhiệm khi dùng nhiều lớp

Có ba kiểu kiến trúc thường gặp:

### A. Text log được đệm trong JVM

```text
BufferedWriter --> OutputStreamWriter --> BufferedOutputStream
                  --> FileOutputStream --> file descriptor --> filesystem
```

Nếu nghiệp vụ yêu cầu mỗi nhóm log phải sync, hãy flush **lớp writer ở ngoài cùng**, sau đó gọi `FileDescriptor.sync()` của đúng `FileOutputStream` bên dưới. Gọi `sync` trước `flush` không bao phủ byte còn trong writer hoặc buffered output stream.

### B. NIO file batch writer

```text
domain records --> encode thành ByteBuffer --> FileChannel.write(...)
                                      --> FileChannel.force(true/false)
```

Không có `BufferedOutputStream`, nên không có `flush()` ở kiểu API đó. `ByteBuffer.flip()` / `clear()` là thao tác **quản lý trạng thái bộ đệm dữ liệu**, không phải yêu cầu file sync. Đảm bảo mọi byte đã được `write` hết trước khi force.

### C. Kết hợp FileOutputStream và channel liên kết

`FileOutputStream.getChannel()` trả channel gắn với stream. Việc ghi qua stream và channel liên quan đến cùng file position/file connection nên phải được phối hợp. Nếu dùng `BufferedOutputStream` bọc stream, **flush lớp buffered** trước khi gọi bất cứ yêu cầu đồng bộ nào trên descriptor/channel.

Không nên đồng thời ghi byte qua nhiều thread ở hai kiểu API khác nhau mà thiếu kiểm soát thứ tự; flush và sync chỉ có ý nghĩa nếu ứng dụng xác định rõ tập byte nào thuộc checkpoint cần commit.

## 14. Hiệu năng, nhóm checkpoint và các giới hạn môi trường

Force có chi phí I/O; tần suất gọi có thể ảnh hưởng đáng kể độ trễ và throughput. Khi thiết kế một batch writer, có thể chọn:

- **Durability cao, mỗi record quan trọng:** ghi đủ record, sync/force trước khi báo “đã cam kết” cho bên gọi.
- **Group commit:** gom một nhóm record hợp lệ, sau đó force một lần; chỉ xác nhận những record tương ứng khi checkpoint hoàn tất.
- **Best effort output/log:** flush theo batch để dữ liệu sớm nhìn thấy; nếu chấp nhận mất đoạn log cuối khi crash, có thể không cần sync mỗi lần.

Lựa chọn không chỉ phụ thuộc mức nhanh/chậm mà phụ thuộc yêu cầu dữ liệu: bản ghi thanh toán, file checkpoint, cache có thể tái tạo hay log chẩn đoán có mức ưu tiên khác nhau.

Giới hạn cần nói rõ:

- Với filesystem **không cục bộ**, hợp đồng `FileChannel.force` không đưa cùng lời hứa như thiết bị local; storage server, cache, replication và network có chính sách riêng.
- Với thiết bị/driver có cache volatile hoặc thông báo đồng bộ không đáng tin, API Java không thay thế quy trình đánh giá durability của toàn bộ storage stack.
- Không kiểm chứng durability bằng cách chạy `Files.readAllBytes` ngay sau ghi rồi kết luận “đã an toàn khi mất điện”; OS page cache có thể trả byte mới.
- `IOException` hoặc `SyncFailedException` nghĩa là không thể xác nhận yêu cầu đồng bộ thành công; đừng trả về “committed” nếu cần bảo đảm dữ liệu.
- `force(true)` không tự sửa lỗi mất tính nguyên tử của update nhiều file hoặc nhiều bước nghiệp vụ.

## 15. Những lỗi phổ biến cần tránh

1. **“`flush` tương đương `fsync`.”** Sai. `flush` của stream thường đẩy bộ đệm ứng dụng xuống sink bên dưới; nó không hứa storage vật lý đã ghi xong.
2. **“Gọi `FileDescriptor.sync` trước flush vẫn giữ mọi record mới.”** Sai với byte còn nằm trong `BufferedOutputStream` hoặc các writer. Cần flush đúng lớp ngoài trước.
3. **“`FileOutputStream.flush` là chức năng đặc biệt để sync đĩa.”** Sai; `FileOutputStream` kế thừa `OutputStream.flush` mặc định, không phải lựa chọn thay `getFD().sync()`.
4. **“Cứ `force(false)` là metadata luôn bền vững.”** Không có bảo đảm yêu cầu force đầy đủ metadata từ cờ `false`.
5. **“`force(true)` là transaction commit toàn hệ thống.”** Không. Nó xử lý phạm vi cập nhật của một file theo hợp đồng, không commit nguyên tử nhiều file/directory.
6. **“`FileChannel.force` luôn bao phủ mapped buffer.”** Không chắc. Sửa file qua `MappedByteBuffer` nên dùng API force của vùng map.
7. **“Có thể thấy nội dung từ JVM khác nghĩa là file đã sync.”** Sai. Visibility có thể đến từ page cache.
8. **“`close` đồng nghĩa force.”** Không nên suy diễn; gọi yêu cầu đồng bộ rõ ràng nếu durability là tiêu chí.
9. **“Một lệnh `FileChannel.write(ByteBuffer)` luôn ghi hết buffer.”** Không bảo đảm. Kiểm tra `hasRemaining()` và số byte thực ghi, xử lý lỗi trước khi force.
10. **“Thêm `force(true)` khắp mọi nơi chỉ làm an toàn hơn.”** Có thể làm giảm throughput, tăng latency nhưng vẫn không giải quyết atomicity hoặc recovery protocol.
11. **“`SYNC` và `DSYNC` ép buffered writer tự flush.”** Sai. Option đặt trên open file, byte ở bộ đệm ứng dụng phải được chuyển xuống channel trước.
12. **“Nếu `sync` thành công thì không cần kiểm tra tính nhất quán dữ liệu.”** Không đủ. Trường hợp crash giữa nhiều record vẫn cần định dạng phục hồi phù hợp.

## 16. Bài tập vận dụng

### Bài tập A — dự đoán dữ liệu nhìn thấy

Một file ban đầu rỗng được bọc bởi `BufferedOutputStream` có capacity lớn hơn 100 byte. Sau:

```java
buffered.write('X');
raw.getFD().sync();
```

Tại thời điểm này, bạn **không được khẳng định** `X` đã đến descriptor. Vì sao? Hãy thêm thao tác đúng vị trí và giải thích sự khác nhau giữa visibility và durability.

### Bài tập B — ghi nhóm record

Một chương trình ghi 100 record vào `FileChannel`, sau đó gọi `force(true)` và mới xác nhận với người dùng. Nếu lỗi xảy ra sau record 90, trước khi force, các record nào có thể được xem là đã cam kết theo chính sách này? Thiết kế metadata nào giúp phân biệt record hoàn chỉnh và record dở?

### Bài tập C — cờ metadata

Nếu hệ thống chỉ quan tâm byte nội dung, `force(false)` có đủ ý nghĩa gì? Nếu cần bảo đảm cả metadata theo hợp đồng, nên chọn cờ nào? `force(true)` có bảo đảm directory entry của thao tác đổi tên file khác không?

### Bài tập D — log trong bộ nhớ map

Một file được sửa bằng `MappedByteBuffer.put`. Có thể chỉ gọi `FileChannel.force(true)` để khẳng định các thay đổi ở map đã được force không? API nào trực tiếp đảm nhận đồng bộ cập nhật từ mapped buffer?

### Bài tập E — mở file với `DSYNC`

Nếu ghi một triệu byte qua `BufferedOutputStream` trên một file open với `DSYNC` mà chưa flush buffer, bạn có thể kết luận từng byte đã xuống thiết bị chưa? Hãy phân tích ranh giới giữa buffer ứng dụng và option của file.

## 17. Tự kiểm tra kiến thức

1. `OutputStream.flush()` mặc định có làm thao tác gì không? Khi nào implementation cụ thể như `BufferedOutputStream` cần override?
2. `FileDescriptor.sync()` dùng từ `FileOutputStream` qua phương thức nào, và có ngoại lệ chuyên biệt gì?
3. Giải thích vì sao cần `buffered.flush()` **trước** `raw.getFD().sync()`.
4. `FileChannel.force(false)` và `FileChannel.force(true)` khác nhau thế nào? Tại sao chọn `false` không đồng nghĩa mọi metadata được force?
5. `FileChannel.force` có bảo đảm gì nếu file ở thiết bị local? Với remote filesystem thì sao?
6. Tại sao `MappedByteBuffer.force()` xuất hiện khi bài toán sửa vùng memory-mapped file?
7. `Files.readString(path)` đọc đúng ngay sau ghi có chứng minh dữ liệu bền vững sau mất điện không?
8. Tại sao đồng bộ file tạm rồi atomic rename vẫn cần suy nghĩ thêm về durability của thư mục cha?
9. Khi nào group commit giúp giảm I/O sync, và nó đổi thời điểm xác nhận “committed” như thế nào?
10. Phân biệt `SYNC/DSYNC` lúc mở file với `force` theo checkpoint sau nhiều lần ghi.

## 18. Tài liệu chính thống

- [Java SE 17 — `OutputStream.flush()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/OutputStream.html#flush()).
- [Java SE 17 — `BufferedOutputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedOutputStream.html).
- [Java SE 17 — `FileDescriptor.sync()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/FileDescriptor.html#sync()).
- [Java SE 17 — `FileChannel.force(boolean)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html#force(boolean)).
- [Java SE 17 — `StandardOpenOption.SYNC` và `DSYNC`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardOpenOption.html).
- [Java SE 17 — `MappedByteBuffer.force()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/MappedByteBuffer.html#force()).

## 19. Ghi nhớ

`flush()` trả lời câu hỏi **“byte đang đệm ở tầng ứng dụng đã được chuyển xuống sink bên dưới chưa?”**. `FileDescriptor.sync()` và `FileChannel.force()` là yêu cầu **đồng bộ cập nhật đã đến tầng file/descriptor với thiết bị lưu trữ** theo những hợp đồng và giới hạn cụ thể.

Khi có bộ đệm ứng dụng, thứ tự cơ bản là **ghi đủ → flush đúng lớp ngoài → sync/force đúng tài nguyên → chỉ xác nhận khi đồng bộ thành công**. `force(false)` tập trung vào nội dung, `force(true)` yêu cầu cả metadata; với memory-mapped file, dùng `MappedByteBuffer.force()` cho những cập nhật qua map. Đối với giao dịch phải sống sót qua crash, còn cần xem xét atomicity, định dạng record, recovery, directory durability và giới hạn của storage stack.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-locking-and-durability-007">Ghi ra tệp tạm rồi thay thế đích</a>

<details>
<summary>Xem chi tiết</summary>

# Ghi ra tệp tạm rồi thay thế đích trong Java NIO.2

## 1. Vì sao nên ghi ra tệp tạm trước khi thay thế?

Khi cập nhật một tệp quan trọng như `config.json`, `settings.properties`, chỉ mục nhỏ hoặc file lưu trạng thái, cách ghi trực tiếp vào tệp đích có một vấn đề: nếu chương trình bị dừng giữa chừng, tệp có thể chỉ chứa **một phần nội dung mới**, bị cắt ngắn hoặc không còn hợp lệ.

Ví dụ thao tác dễ gây rủi ro:

```java
Files.writeString(
        Path.of("config.txt"),
        "version=2\nmode=production\n",
        StandardCharsets.UTF_8
);
```

Đây chỉ là đoạn minh họa, cần import `java.nio.file.Files`, `Path` và `java.nio.charset.StandardCharsets`. Nếu tệp đích đang tồn tại, `Files.writeString` với tùy chọn mặc định có thể ghi đè và cắt nội dung cũ. Nếu lỗi I/O hoặc tiến trình chết sau khi đã thay đổi một phần dữ liệu, người đọc có thể gặp nội dung hỏng.

Mẫu **write temporary file, then replace destination** giải quyết vấn đề ở cấp cấu trúc:

1. **Tạo một tệp tạm mới** nằm cùng thư mục/cùng filesystem với đích.
2. **Viết toàn bộ nội dung mới** vào tệp tạm và kiểm tra tính hợp lệ.
3. Nếu yêu cầu độ bền, **đồng bộ dữ liệu tệp tạm** bằng `FileChannel.force(true)`.
4. **Đóng handle** tệp tạm.
5. Dùng `Files.move(temp, destination, ...)` để thay thế tên đích bằng tệp mới; ưu tiên `ATOMIC_MOVE` nếu hệ thống hỗ trợ.
6. **Dọn tệp tạm** nếu chưa được chuyển thành công; thiết kế recovery cho tệp tạm còn sót khi hệ thống sập.

Trước khi thay thế, tệp đích vẫn chứa bản cũ hoàn chỉnh. Nếu bước rename/move được hỗ trợ **nguyên tử**, bên đọc qua tên đích sẽ thấy bản cũ hoặc bản mới, không thấy một tệp đang được ghi dở do chính quy trình này.

Điều đó **không đồng nghĩa giao dịch cơ sở dữ liệu**, không đảm bảo mọi filesystem có atomic replace, và chưa đủ để kết luận “mất điện cũng không mất bản mới”. Chúng ta cần phân biệt ba khái niệm: tính nguyên vẹn nội dung trước khi công bố, tính nguyên tử khi thay thế tên, và độ bền trên lưu trữ.

## 2. Mô hình trạng thái trước và sau

Giả sử `settings.json` chứa bản V1 và muốn cập nhật V2:

```text
Giai đoạn A — trước khi ghi:
  settings.json                 [V1 hoàn chỉnh]

Giai đoạn B — ghi tệp tạm:
  settings.json                 [V1 hoàn chỉnh]
  .settings-47392.tmp           [V2 đang ghi, chưa công bố]

Giai đoạn C — đã ghi/validate/force/đóng:
  settings.json                 [V1 hoàn chỉnh]
  .settings-47392.tmp           [V2 hoàn chỉnh]

Giai đoạn D — move/replace nguyên tử:
  settings.json                 [V2 hoàn chỉnh]
  .settings-47392.tmp           [không còn ở tên tạm]
```

Nếu `move` nguyên tử được filesystem hỗ trợ, tên `settings.json` đổi đối tượng tệp được tham chiếu trong một thao tác công bố. Nếu move **không nguyên tử**, giữa chừng có thể có khoảng thời gian không nhìn thấy đích hoặc những trạng thái khác tùy cách filesystem/provider thực hiện.

Khi đang ghi V2 vào tệp tạm, không nên vô tình sử dụng tên cố định `settings.tmp` khiến hai tiến trình tranh nhau ghi đè. `Files.createTempFile(parent, prefix, suffix)` cấp một tên mới chưa tồn tại bằng thao tác tạo tệp của filesystem, thuận tiện hơn tự ghép tên dựa trên thời gian.

## 3. “Nguyên tử”, “bền” và “nhất quán” là ba yêu cầu khác nhau

| Yêu cầu | Câu hỏi | Công cụ thường dùng | Giới hạn |
|---|---|---|---|
| Nội dung mới hợp lệ trước khi công bố | Có ghi đủ và validate V2 chưa? | Ghi tệp tạm, kiểm tra size/checksum/format | Chỉ tốt nếu kiểm tra đúng định dạng |
| Atomic visibility của tên đích | Reader mở đường dẫn đích có thấy V1 hoặc V2 hoàn chỉnh? | `Files.move(..., ATOMIC_MOVE)` khi được hỗ trợ | Không phải mọi filesystem/provider hoặc cách replace đều hỗ trợ |
| Độ bền sau sự cố | Khi máy mất điện, V2 có còn sau reboot? | `FileChannel.force(true)` cho tệp tạm; cơ chế đồng bộ metadata/thư mục theo OS | API Java chuẩn không cung cấp cam kết portable đầy đủ cho directory fsync + rename |
| Tránh ghi đè bản cập nhật từ writer khác | Writer A có đè mất bản vừa công bố của B? | Điều phối writer, version/CAS, lock ở tầng phù hợp | Atomic rename chỉ nguyên tử bước công bố, không giải quyết lost update |
| Nhiều file cùng cập nhật | Hai file có cùng chuyển V1 sang V2 một lượt không? | Thiết kế manifest/version, journal hoặc transaction chuyên dụng | Một lần move chỉ liên quan một mục nhập đích |

**Atomic visibility ≠ crash durability.** Dù move thành công và reader thấy V2, ứng dụng không nên mặc định rằng mọi directory metadata đã an toàn qua mất điện. Nếu có yêu cầu nghiêm ngặt về phục hồi sau sự cố, cần hợp đồng filesystem/OS cụ thể, có thể đồng bộ thư mục theo giao diện hệ thống, hoặc dùng cơ chế transaction/journal đã được kiểm chứng.

## 4. Vì sao tệp tạm nên ở cùng thư mục với đích?

Một rename nguyên tử thông thường hoạt động **trong cùng filesystem**. Nếu tạo tệp tạm trong thư mục temp toàn hệ thống, tệp đích có thể nằm trên volume khác, khiến `ATOMIC_MOVE` không được hỗ trợ.

Ví dụ không nên mặc định an toàn:

```java
Path temporary = Files.createTempFile("update-", ".tmp");
Path destination = Path.of("/mnt/data/config.json");
```

Đoạn minh họa có thể tạo tệp tạm tại thư mục `java.io.tmpdir`, trong khi đích ở filesystem khác. Ngay cả nếu tên đích và temp trông như cùng ổ đĩa, mount/bind mount/network filesystem có thể ảnh hưởng semantics.

Thay vào đó, chọn **thư mục cha của chính đích**:

```java
Path destination = Path.of("config.json").toAbsolutePath();
Path parent = destination.getParent();
Path temporary = Files.createTempFile(parent, ".config-", ".tmp");
```

- `Files.createTempFile` tạo tệp mới và trả `Path` thực, tránh trùng tên trong điều kiện bình thường.
- Temp và đích nằm cùng thư mục, qua đó tăng khả năng hỗ trợ move nguyên tử.
- Đường dẫn temp thường **không** phải file backup lâu dài: sau move, tên tạm bị xóa vì tệp được đổi sang tên đích.
- Thư mục cha cần tồn tại và phải có quyền tạo/rename/xóa mục nhập.
- Với thông tin nhạy cảm, xem xét quyền mặc định của temp ngay từ khi tạo và điều chỉnh quyền/ACL theo policy trước khi công bố, vì temp là một **đối tượng tệp mới**.

Nằm cùng thư mục chỉ là **điều kiện thuận lợi**, không bảo đảm `ATOMIC_MOVE` hoạt động; provider/filesystem vẫn có thể không hỗ trợ hoặc có quy tắc replace riêng.

## 5. Ghi đủ dữ liệu vào tệp tạm

Khi dùng `FileChannel`, việc gọi `write(ByteBuffer)` một lần **không đảm bảo** đã ghi hết buffer. Cần kiểm tra `hasRemaining()` và số byte trả về:

```java
static void writeFully(FileChannel channel, ByteBuffer source)
        throws IOException {
    while (source.hasRemaining()) {
        int n = channel.write(source);
        if (n == 0) {
            throw new IOException("Write made no progress");
        }
    }
}
```

Đây là phương thức minh họa, cần import `FileChannel`, `ByteBuffer` và `IOException`. Nếu format có nhiều trường nhị phân, hãy thiết lập `ByteOrder` nhất quán. Nếu ghi UTF-8, mã hóa tường minh bằng `StandardCharsets.UTF_8`. Không dùng số ký tự làm số byte; ký tự UTF-8 có thể chiếm hơn một byte.

Với tệp tạm đã tồn tại do `createTempFile`, mở channel để ghi:

```java
try (FileChannel channel = FileChannel.open(
        temporary,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING)) {
    ByteBuffer bytes = StandardCharsets.UTF_8.encode("version=2\n");
    writeFully(channel, bytes);
    channel.force(true);
}
```

Đoạn này giả định `temporary` đã được tạo và quyền ghi hợp lệ; các import tương ứng phải được khai báo. `TRUNCATE_EXISTING` ở đây là **tệp tạm**, không phải file đích, nên không làm hỏng V1. Đóng channel trước khi move cũng giúp tránh một số hạn chế khi thay tên file trên các hệ điều hành có xử lý handle khác nhau.

### Xác thực tệp trước khi công bố

Việc `write` hoàn tất mới chỉ xác nhận đã chuyển các byte cho I/O. Nếu dữ liệu có định dạng JSON, CSV, bản ghi nhị phân, chứng chỉ, manifest hoặc index, nên **validate trước move**:

- Kích thước hợp lý và không bị cắt đuôi.
- Có cấu trúc/phiên bản/schema đúng yêu cầu.
- Các giá trị, liên kết tham chiếu, checksum hoặc hash hợp lệ nếu định dạng yêu cầu.
- Quyền truy cập/owner/ACL phù hợp với ứng dụng sau khi tệp tạm trở thành đích.

Không nhất thiết phải đọc toàn bộ temp lần nữa với mọi loại file; lựa chọn validation theo độ tin cậy của serializer và mức thiệt hại nếu công bố file lỗi. Với dữ liệu giao dịch quan trọng, thiết kế kiểm tra có chủ đích thay vì chỉ đợi `write` trả về.

## 6. `Files.move`, `ATOMIC_MOVE` và `REPLACE_EXISTING`

API Java NIO.2:

```java
Files.move(
        temporary,
        destination,
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING
);
```

- `ATOMIC_MOVE` **yêu cầu** hệ thống thực hiện di chuyển như thao tác filesystem nguyên tử. Nếu không thể, có thể ném `AtomicMoveNotSupportedException`.
- `REPLACE_EXISTING` dùng cho cách move thông thường để cho phép thay tên đích đã tồn tại, chịu các giới hạn khác của provider.
- **Chi tiết quan trọng:** theo tài liệu `Files.move`, khi có `ATOMIC_MOVE`, **các tùy chọn khác bị bỏ qua**; nếu mục tiêu đã tồn tại, hành vi thay thế/không thay thế là **phụ thuộc implementation**. Vì vậy chỉ đặt cả hai option không bảo đảm atomic replacement được hỗ trợ trên mọi hệ thống.
- Nếu `ATOMIC_MOVE` thất bại, không nên âm thầm bỏ nó rồi tiếp tục như thể thao tác vẫn atomic.
- Nếu lựa chọn fallback sang move không atomic, phải coi đây là **thay đổi mức bảo đảm** có ảnh hưởng đến reader và phục hồi lỗi.

Có thể thử atomic move trước:

```java
try {
    Files.move(temp, target,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
} catch (AtomicMoveNotSupportedException ex) {
    // Chính sách 1: báo lỗi và giữ nguyên đích; dọn temp khi phù hợp.
    throw new IOException("Filesystem không hỗ trợ atomic move", ex);
}
```

Đây là đoạn minh họa cần import `Path`, `Files`, `StandardCopyOption`, `AtomicMoveNotSupportedException` và `IOException`. Nó là chính sách **yêu cầu atomic**: nếu không hỗ trợ thì dừng, không tự hạ bảo đảm.

Trên một số provider, atomic move khi mục tiêu đã tồn tại có thể thất bại với `FileAlreadyExistsException` theo quy tắc implementation. Khi cần strict atomic replacement, không coi kết quả đó là tín hiệu để xóa đích trước rồi rename: **xóa đích trước sẽ tạo một khoảng trống có thể quan sát được**.

## 7. Chương trình Java 17+ hoàn chỉnh: viết tệp tạm rồi thay đích

Ví dụ sau tạo tệp đích chứa `old`, viết `new` vào temp **trong cùng thư mục**, gọi `force(true)` rồi đóng channel trước khi move. Ví dụ có **chính sách fallback được khai báo rõ**: nếu không thể atomic replace do provider không hỗ trợ atomic move hoặc không cho replace mục tiêu đang tồn tại theo chế độ atomic, chương trình cho phép dùng `REPLACE_EXISTING` không atomic. Trong sản phẩm yêu cầu atomic nghiêm ngặt, chuyển `ALLOW_NON_ATOMIC_FALLBACK` thành `false` để dừng thay vì hạ mức bảo đảm.

Lưu thành **`TempFileReplaceDemo.java`**:

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public class TempFileReplaceDemo {
    // Ví dụ cho phép fallback để chạy trên nhiều filesystem.
    // Đặt false nếu nghiệp vụ bắt buộc atomic replacement.
    private static final boolean ALLOW_NON_ATOMIC_FALLBACK = true;

    public static void main(String[] args) throws IOException {
        Path folder = Files.createTempDirectory("replace-demo-");
        Path target = folder.resolve("config.txt");

        try {
            Files.writeString(target, "old\n", StandardCharsets.UTF_8);
            replaceWithText(target, "new\n");

            System.out.println("Destination: "
                    + Files.readString(target, StandardCharsets.UTF_8).strip());
            System.out.println("Destination exists: " + Files.exists(target));
        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(folder);
        }
    }

    private static void replaceWithText(Path target, String text)
            throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        Path temporary = Files.createTempFile(
                parent, ".config-", ".tmp");

        try {
            ByteBuffer data = StandardCharsets.UTF_8.encode(text);

            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                while (data.hasRemaining()) {
                    int written = channel.write(data);
                    if (written == 0) {
                        throw new IOException("Write made no progress");
                    }
                }
                channel.force(true);
            }

            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException
                     | FileAlreadyExistsException ex) {
                if (!ALLOW_NON_ATOMIC_FALLBACK) {
                    throw ex;
                }
                // Fallback minh bạch: không còn bảo đảm atomic.
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            // Nếu move đã thành công, path tạm không còn tồn tại.
            // Nếu lỗi xảy ra trước move, dọn tệp tạm tốt nhất có thể.
            Files.deleteIfExists(temporary);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac TempFileReplaceDemo.java
java TempFileReplaceDemo
```

**Stdout khi thao tác tạo/ghi/đọc/move/xóa thành công** (dù nhánh atomic hay fallback được sử dụng):

```text
Destination: new
Destination exists: true
```

Giải thích:

1. `Files.writeString(target,"old\n")` chỉ tạo trạng thái ban đầu cho ví dụ. Trong ứng dụng thực, đây sẽ là file có sẵn và không được ghi lại trước mỗi lần cập nhật.
2. `Files.createTempFile(parent,...)` sinh một file riêng trong cùng thư mục với đích. V1 chưa bị ảnh hưởng khi code bắt đầu ghi V2.
3. `ByteBuffer` mã hóa UTF-8; vòng lặp `write` đảm bảo gửi hết dữ liệu vào channel. `force(true)` yêu cầu đồng bộ nội dung và metadata liên quan của file theo ngữ nghĩa API.
4. Thoát try-with-resources đóng channel. Sau đó mới thử `Files.move` atomic.
5. Nếu provider báo không hỗ trợ atomic hoặc không cho atomic replace đích hiện tại theo hành vi implementation, ví dụ **có chủ ý** fallback sang move thường. Kết quả vẫn có thể là `new`, nhưng fallback **không bảo đảm atomic visibility**.
6. `finally` ở helper dọn temp nếu nó còn tồn tại. Khi move thành công, temp đã được đổi tên và `deleteIfExists(temporary)` không xóa đích.
7. `main` dọn file và thư mục demo sau khi đọc/in kết quả. Trong ứng dụng thực **không được xóa đích sau khi publish**; việc dọn trong `main` chỉ dành cho môi trường ví dụ.

Trường hợp môi trường từ chối rename vì file đang mở, thiếu quyền, filesystem bị khóa, thiếu dung lượng hoặc lỗi I/O khác, chương trình có thể ném `IOException` thay vì in hai dòng trên. Ví dụ không nuốt các lỗi đó. Nhánh fallback chỉ được kích hoạt cho hai loại ngoại lệ đã nêu; không giả định mọi `FileSystemException` đều có thể phục hồi bằng move thường.

**Vì sao không tự động fallback trong hệ thống nghiêm ngặt?** Vì writer và reader cần cùng hiểu bảo đảm. Một sản phẩm yêu cầu reader chỉ thấy snapshot hoàn chỉnh phải khai báo atomic replacement là điều kiện bắt buộc; nếu filesystem không hỗ trợ thì nên báo lỗi, giữ nguyên V1 và phục hồi thích hợp thay vì giả định fallback tương đương.

## 8. `force(true)` giúp gì, và chưa bảo đảm điều gì?

`FileChannel.force(boolean metaData)` yêu cầu buộc cập nhật tệp qua channel xuống thiết bị lưu trữ theo hợp đồng API:

```java
channel.force(true);
```

- `force(false)`: yêu cầu đồng bộ **nội dung** tệp.
- `force(true)`: yêu cầu đồng bộ nội dung **và metadata của tệp**.
- Việc gọi `force` trước khi thay tên giúp giảm rủi ro công bố một đối tượng tệp mà byte mới chỉ đang ở cache chưa được đẩy xuống lưu trữ.
- `force` không giải quyết nhiều bản ghi thành transaction; không chứng minh reader không thể thấy file đích đang bị thay thế theo cách không nguyên tử.
- Mức đảm bảo thực sự còn tùy loại thiết bị và filesystem; hợp đồng Java có khác biệt với tệp không nằm trên thiết bị lưu trữ cục bộ.

**Metadata của tệp tạm không đồng nghĩa metadata của thư mục chứa tên đích.** Rename thường làm thay đổi directory entry. Trên hệ POSIX, quy trình bảo đảm crash durability mạnh hơn thường yêu cầu đồng bộ dữ liệu tệp, rename, rồi đồng bộ **thư mục chứa đích**. Java SE 17 chuẩn **không có API portable, được bảo đảm hỗ trợ đồng bộ thư mục trên mọi provider**; việc mở directory để force có thể bị từ chối. Không nên viết ví dụ Java chung rồi khẳng định `force(true)` trên temp đã hoàn tất mọi bước crash-safe rename.

Nếu yêu cầu độ bền sau mất điện rất cao, cần tài liệu và kiểm thử theo OS/filesystem đích hoặc chọn kho lưu trữ có transaction/journal phù hợp. Một lần chạy thành công không chứng minh bảo đảm khi mất điện.

## 9. Điều gì xảy ra nếu ứng dụng chết ở từng giai đoạn?

| Thời điểm xảy ra lỗi | Tình trạng thường gặp | Điều cần làm |
|---|---|---|
| Trước khi tạo temp | Đích vẫn chứa V1 | Có thể thử lại |
| Đang ghi temp, trước move | Đích vẫn V1; temp có thể dang dở | Không publish temp lỗi; dọn/kiểm tra theo policy |
| Đã ghi temp và đóng nhưng chưa move | Đích V1; temp là ứng viên V2 | Có thể validate và phục hồi nếu có manifest/version |
| Atomic move hoàn tất | Reader mở theo tên đích sau move thấy V2 | Vẫn phải xét durability directory nếu mất điện |
| Fallback move không atomic bị gián đoạn | Trạng thái đích tùy filesystem/provider; không có bảo đảm V1 hoặc V2 trọn vẹn | Recovery, kiểm tra checksum/version, giữ backup nếu cần |
| Move thành công nhưng tiến trình chết trước khi nhận phản hồi | Đích **có thể đã là V2**; writer chưa biết commit có hiệu lực hay chưa | Thiết kế retry/idempotency và kiểm tra version/hash |

`finally` trong Java **không chạy khi mất điện, kill -9 hoặc hệ thống sập đột ngột**. Vì vậy phải có chiến lược tìm và xử lý tệp temp còn sót. Không dọn theo tên prefix một cách mù quáng khi nhiều writer vẫn có thể đang hoạt động; cần ownership, tuổi tệp, lock hoặc định danh transaction phù hợp.

## 10. Đọc đồng thời trong lúc thay thế

Khi rename/replace thực sự nguyên tử:

- Reader **mở đường dẫn đích trước** thời điểm replace có thể tiếp tục đọc từ handle đang trỏ vào đối tượng tệp cũ, tùy OS và filesystem.
- Reader **mở đường dẫn đích sau** thời điểm replace sẽ tra cứu mục nhập tên mới và thường nhìn thấy V2.
- Reader không nhất thiết tự “nhảy” sang V2 chỉ vì path đã được rename khi nó giữ handle mở. Nếu cần reload cấu hình, chương trình cần chủ động mở lại/tải lại.
- Việc thay tên không đồng bộ logic nội bộ của reader. Ví dụ reader đọc hai file riêng hoặc đọc V2 rồi cache một phần V1 ở chỗ khác vẫn có thể xảy ra bất nhất nghiệp vụ.

Do đó mẫu temp + rename rất hữu ích cho **một file hoàn chỉnh**: công bố tệp bằng một điểm chuyển đổi tên, còn reader quyết định thời điểm mở lại.

Đừng nhầm `Files.move` với việc **ghi nội dung tại chỗ**. Nếu chương trình khác giữ hard link tới đối tượng file V1, hard link đó có thể tiếp tục thấy V1 sau khi tên đích được thay bằng đối tượng V2. Nếu có symlink trỏ theo tên đích, việc đọc lại symlink có thể dẫn tới tệp mới; nhưng bản thân target là symlink lại có quy tắc thay thế riêng.

## 11. Đồng thời nhiều writer và lost update

Atomic replace đảm bảo về thao tác thay tên nếu được hỗ trợ, **không bảo đảm chỉ có một writer hợp lệ**.

Ví dụ:

```text
Writer A đọc V1, tính V2-A, ghi temp-A.
Writer B đọc V1, tính V2-B, ghi temp-B.
Writer A atomic replace → đích là V2-A.
Writer B atomic replace → đích là V2-B.
Kết quả: thay đổi của A có thể bị mất (lost update).
```

Giải pháp tùy bài toán:

1. Chỉ cho một writer chịu trách nhiệm tệp, mọi request đi qua một hàng đợi.
2. Dùng khóa ở mức ứng dụng hoặc một lock file riêng, với giao thức lock/recovery được thiết kế rõ.
3. Dùng version/checksum và điều kiện cập nhật ở tầng dữ liệu; riêng `Files.move` không có CAS “chỉ replace nếu bản cũ vẫn là V1”.
4. Với cập nhật đồng thời nhiều thực thể, dùng cơ chế transaction đúng nghĩa thay vì chỉ rename một file.

**Đừng cho rằng `FileLock` trên tệp đích hiện tại luôn bảo vệ tên đích qua mọi lần replace.** Khóa file có thể gắn với đối tượng/handle cũ, trong khi rename thay object dưới tên đích; mức bắt buộc của file lock còn phụ thuộc nền tảng. Nếu chọn lock, dùng hợp đồng phù hợp và không đồng nhất “khóa tệp cũ” với “khóa quyền xuất bản tên đích”.

## 12. Quyền, metadata và symbolic link

### Metadata có thể thay đổi

Vì temp là một **tệp mới**, khi nó thay đích, các thuộc tính của tệp đích cũ không tự động trở thành thuộc tính của temp:

- Quyền truy cập/ACL, owner/group.
- Timestamps và các thuộc tính đặc thù filesystem.
- Extended attributes, nhãn bảo mật, sparse/compression flags trong một số hệ thống.
- Các liên kết cứng đang tham chiếu tệp cũ.

Nếu nghiệp vụ phải giữ quyền/owner/ACL cụ thể, cần xác định policy và áp dụng/kiểm tra thuộc tính lên temp **trước khi công bố**. Tránh sao chép quyền nguy hiểm một cách vô điều kiện; quyền nên phù hợp security model. `Files.createTempFile` và `Files.getFileAttributeView`/`setAttribute` hỗ trợ một phần tùy provider, không phải mọi thuộc tính đều có API portable.

### Symlink và thư mục không tin cậy

Nếu đường dẫn đích là symbolic link, `Files.move(temp, destination, ...)` thường thao tác tới **mục nhập tên đích**, chứ không được giả định nó sẽ mở symlink rồi ghi nội dung file được symlink trỏ tới. Kết quả thay symlink hay thất bại phụ thuộc ngữ nghĩa move và provider.

Nếu kẻ khác có thể thay tệp/link/thư mục giữa khi kiểm tra và move (TOCTOU), các kiểm tra `Files.exists`/`Files.isSymbolicLink` trước đó không tạo bảo đảm an toàn tuyệt đối. Khi xử lý đường dẫn do người dùng không tin cậy cung cấp, cần kiểm soát thư mục, quyền, ownership, symlink policy và kiến trúc xuất bản phù hợp; không dựa vào một lần kiểm tra rồi giả định path không đổi.

## 13. Những cách làm sai thường gặp

1. **Ghi trực tiếp vào file đích với `TRUNCATE_EXISTING` rồi mới ghi dữ liệu mới.** Reader có thể thấy file trống hoặc nửa bản ghi nếu lỗi ở giữa.
2. **Tạo temp ở `java.io.tmpdir` dù đích ở volume khác.** Atomic move qua filesystem thường không được hỗ trợ; hãy tạo temp cùng thư mục đích.
3. **Không xử lý short write.** `FileChannel.write(ByteBuffer)` có thể ghi ít byte hơn remaining. Cần lặp tới khi hết buffer.
4. **Không đóng temp trước rename.** Trên một số OS, handle chưa đóng có thể gây lỗi move/replace.
5. **Chỉ bắt `AtomicMoveNotSupportedException` rồi âm thầm fallback.** Nếu hệ thống yêu cầu atomic, fallback phải bị từ chối; nếu chấp nhận, phải nêu rõ mức bảo đảm giảm xuống.
6. **Tin rằng `ATOMIC_MOVE + REPLACE_EXISTING` luôn đảm bảo atomic overwrite.** Khi có ATOMIC_MOVE, option khác bị bỏ qua; target đã tồn tại là hành vi phụ thuộc implementation.
7. **Xóa đích trước rồi rename temp.** Tạo một giai đoạn tên đích không tồn tại và có thể đánh mất V1 khi lỗi.
8. **Cho rằng `force(true)` trên temp đồng nghĩa rename bền tuyệt đối.** Metadata thư mục cũng quan trọng; Java không cung cấp directory fsync portable cho mọi môi trường.
9. **Ghi file tạm xong nhưng chưa kiểm tra định dạng.** Atomic publish một file sai schema vẫn tạo ra dữ liệu nghiệp vụ lỗi hoàn chỉnh.
10. **Quên quyền/ACL của temp.** Tệp được công bố là đối tượng tệp mới, có thể khác metadata của đích cũ.
11. **Coi atomic replace là lock/CAS.** Hai writer vẫn có thể đè kết quả nhau; phải có cơ chế phối hợp.
12. **Cho rằng finally luôn dọn temp.** Sau mất điện hoặc kill đột ngột vẫn có thể tồn tại temp, cần recovery khi khởi động.
13. **Tưởng mọi reader đang mở handle tự thấy nội dung mới.** Handle cũ có thể vẫn trỏ đối tượng V1; reader phải mở lại khi cần snapshot mới.
14. **Tưởng một move giải quyết transaction nhiều file.** Một file được publish nguyên tử không đồng nghĩa nhiều file đồng bộ phiên bản.

## 14. Khi nào cần backup hoặc manifest?

Temp + replace giúp giảm rủi ro tệp bị **ghi dở**, nhưng không tự bảo vệ trước:

- Đã công bố **dữ liệu hợp lệ về cú pháp nhưng sai về nghiệp vụ**.
- Writer mới ghi đè mất cập nhật trước đó.
- Lỗi lưu trữ hoặc file bị xóa sau khi công bố.
- Cần rollback sang bản cũ khi một thành phần phụ thuộc V2 không tương thích.

Trong các trường hợp đó, cân nhắc **giữ bản sao V1 độc lập**, lịch sử phiên bản có checksum, hoặc manifest chỉ tới tệp dữ liệu được đặt tên theo version:

```text
data-v1.bin
data-v2.bin
manifest.json  → {"active":"data-v2.bin"}
```

Ý tưởng: ghi và xác nhận file V2 trước, sau đó cập nhật **một manifest nhỏ** bằng temp + atomic replacement. Reader lấy manifest, rồi mở file version tương ứng. Thiết kế này giúp định danh snapshot rõ, nhưng vẫn đòi hỏi chiến lược dọn phiên bản cũ và đảm bảo metadata/durability; một lần thay manifest không giải quyết mọi vấn đề transaction hoặc đồng thời.

Đây là hướng mở rộng phù hợp cho ứng dụng có tệp dữ liệu lớn, reader cần snapshot cố định hoặc rollback; với một file cấu hình nhỏ, giải pháp temp + replace kèm validation có thể đủ.

## 15. Checklist áp dụng trong ứng dụng

Trước khi công bố tệp mới, kiểm tra theo thứ tự:

1. **Xác định đích và thư mục tin cậy:** có quyền tạo temp và replace; thống nhất chính sách symlink.
2. **Tạo temp độc lập trong cùng thư mục:** tránh tên cố định dễ tranh chấp.
3. **Ghi đúng toàn bộ byte:** dùng encoding, định dạng và vòng lặp I/O rõ ràng.
4. **Validate và thiết lập metadata cần thiết:** schema/checksum, quyền/ACL, kích thước.
5. **Đồng bộ temp nếu nghiệp vụ yêu cầu:** `FileChannel.force(true)`, hiểu giới hạn filesystem.
6. **Đóng temp trước khi move.**
7. **Thử công bố theo bảo đảm đã chọn:** strict atomic hoặc policy fallback đã được phê duyệt về mặt thiết kế; không tự xóa đích trước.
8. **Xác nhận trạng thái cuối khi cần:** file đích tồn tại, phiên bản/hash đúng, log kết quả; nhận diện khả năng lỗi xảy ra sau khi đã commit.
9. **Dọn temp còn lại:** `finally` khi chạy bình thường và quy trình startup recovery khi crash.
10. **Điều phối writer/reader:** tránh lost update; reader mở lại khi cần dữ liệu mới.

Với yêu cầu **không bao giờ để reader thấy bản dang dở**, bắt buộc môi trường phải hỗ trợ một cơ chế công bố nguyên tử phù hợp; nếu không thể đáp ứng, từ chối cập nhật hoặc đổi thiết kế lưu trữ. Với yêu cầu **khôi phục qua mất điện**, cần thêm giải pháp bảo đảm durability của filesystem/OS và kiểm thử sự cố, không chỉ dựa vào một lệnh move.

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao không ghi trực tiếp nội dung V2 vào file đích V1?  
**Đáp án:** Ghi trực tiếp có thể cắt/ghi dở V1 trước khi V2 hoàn tất. Ghi temp giữ V1 trong suốt giai đoạn xây dựng V2, sau đó mới công bố.

**Câu 2.** Dùng `ATOMIC_MOVE` thì `REPLACE_EXISTING` có chắc chắn buộc thay mục tiêu tồn tại không?  
**Đáp án:** Không. Theo tài liệu `Files.move`, khi có `ATOMIC_MOVE` thì các tùy chọn khác bị bỏ qua; thay thế target hiện hữu phụ thuộc implementation.

**Câu 3.** Sau `channel.force(true)` trên temp và `Files.move(..., ATOMIC_MOVE)` thành công, đã bảo đảm mất điện không thể làm mất tên đích mới chưa?  
**Đáp án:** Chưa thể kết luận portable. Việc đồng bộ directory entry/rename còn phụ thuộc filesystem/OS, trong khi Java chuẩn không bảo đảm directory fsync trên mọi provider.

**Câu 4.** Writer A và B cùng build bản mới từ V1, cả hai atomic replace. Có tránh được lost update không?  
**Đáp án:** Không. Bản publish sau có thể đè bản của writer trước. Cần serialize, version checking hoặc transaction phù hợp.

**Câu 5.** Nếu atomic rename không được hỗ trợ thì luôn dùng `REPLACE_EXISTING` để chương trình tiếp tục được không?  
**Đáp án:** Chỉ khi nghiệp vụ **chấp nhận mất bảo đảm atomic** và đã có phương án đọc/khôi phục tương ứng. Khi yêu cầu nguyên tử bắt buộc, nên báo lỗi.

**Câu 6.** Nếu Java process bị kill giữa lúc ghi temp, `finally` có chắc chạy không?  
**Đáp án:** Không. Cần phương án phát hiện và dọn/khôi phục temp tồn dư sau crash, tránh đụng file đang được writer hợp lệ dùng.

**Câu 7.** Sau atomic replace, một reader đang mở handle tới V1 có nhất thiết thấy V2 ngay khi đọc tiếp không?  
**Đáp án:** Không. Handle cũ có thể tiếp tục tham chiếu V1; reader cần mở lại tên đích nếu muốn snapshot hiện hành.

## 17. Ghi nhớ

- **Viết đủ + kiểm tra tệp tạm trước, thay tên đích sau** giúp tránh công bố file đang ghi dở.
- **Tạo temp cùng thư mục** để tăng khả năng move nguyên tử; dùng `Files.createTempFile` tránh trùng tên.
- **`Files.move(..., ATOMIC_MOVE)` phải được provider hỗ trợ**, và cách xử lý đích tồn tại phụ thuộc implementation. Không âm thầm giả định fallback là nguyên tử.
- **`FileChannel.force(true)` hỗ trợ durability của tệp**, nhưng không đồng nghĩa directory rename đã được flush bền trên mọi nền tảng.
- **Atomic replace không phải transaction, CAS hay khóa nhiều writer**. Cần cơ chế riêng cho consistency, rollback và crash recovery khi bài toán đòi hỏi.

### Tài liệu API Java 17

- [Files.move](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#move(java.nio.file.Path,java.nio.file.Path,java.nio.file.CopyOption...))
- [StandardCopyOption.ATOMIC_MOVE / REPLACE_EXISTING](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardCopyOption.html)
- [FileChannel.force](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html#force(boolean))
- [Files.createTempFile](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#createTempFile(java.nio.file.Path,java.lang.String,java.lang.String,java.nio.file.attribute.FileAttribute...))


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-locking-and-durability-008">ATOMIC_MOVE và khả năng không được hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>

# `ATOMIC_MOVE` và khả năng không được hỗ trợ trong Java NIO.2

## 1. Vấn đề cần giải quyết

Khi đổi tên, xuất bản hoặc thay thế một file, ứng dụng thường muốn những người đọc khác **không nhìn thấy trạng thái di chuyển dở dang**. Java NIO.2 cung cấp tùy chọn `StandardCopyOption.ATOMIC_MOVE` cho `Files.move(source, target, options...)` để yêu cầu hệ thống tệp thực hiện thao tác move như **một thao tác nguyên tử**.

Ví dụ: một công cụ tạo file cấu hình mới trong file tạm rồi chuyển nó thành `config.json`. Mục tiêu là tránh việc người đọc mở đúng thời điểm ứng dụng đang chuyển file và nhìn thấy file đích chỉ chứa một nửa nội dung.

Tuy nhiên, `ATOMIC_MOVE` **không phải cam kết luôn thành công**:

- Hệ điều hành, `FileSystemProvider`, file store, cách mount, quyền truy cập và loại đối tượng đều có thể ảnh hưởng.
- Nếu không thể thực hiện move nguyên tử, API ném `AtomicMoveNotSupportedException`.
- Nếu đường dẫn đích đã tồn tại, Java **không bảo đảm** một `ATOMIC_MOVE` sẽ thay thế đích: provider có thể thay thế hoặc ném `IOException`, tùy triển khai.
- Một move nguyên tử về mặt tên/khả năng quan sát **không đồng nghĩa** nội dung đã được ghi bền vững xuống thiết bị khi mất điện; đây là hai yêu cầu khác nhau.

Bài học thuộc nhóm **Java Core I/O → Khóa và dữ liệu bền**. Sau bài này, bạn sẽ hiểu chính xác hợp đồng `ATOMIC_MOVE`, các trường hợp bất khả thi, ý nghĩa của ngoại lệ, chiến lược xuất bản file theo quyền hạn và cách phân biệt **atomicity**, **visibility**, **durability** và **consistency**.

## 2. `Files.move` và `ATOMIC_MOVE` hoạt động thế nào?

Chữ ký API:

```java
Path result = Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
```

Đây là đoạn minh họa đặt trong một phương thức có thể ném `IOException`; `source` và `target` là các `Path` phù hợp.

`Files.move` di chuyển hoặc đổi tên một mục tệp. Với tùy chọn `ATOMIC_MOVE`, Java yêu cầu thao tác được thực hiện dưới dạng **atomic file-system operation**: quan sát của ứng dụng khác về việc đổi tên/di chuyển không thấy một chuỗi trạng thái trung gian của chính thao tác move đó.

Mô hình điển hình khi file đích chưa tồn tại:

```text
Trước:
  work/new.tmp    -> chứa toàn bộ dữ liệu hoàn chỉnh
  work/result.dat -> chưa có

Files.move(new.tmp, result.dat, ATOMIC_MOVE)

Sau khi thành công:
  work/new.tmp    -> không còn
  work/result.dat -> xuất hiện với dữ liệu hoàn chỉnh
```

Đây là minh họa thao tác thay đổi một tên file, **không phải giao dịch nhiều file**. Nếu yêu cầu cập nhật đồng thời `data.bin` và `index.json`, một lần `ATOMIC_MOVE` không biến hai thay đổi đó thành một transaction thống nhất.

### 2.1. Hợp đồng chính xác của `ATOMIC_MOVE`

Theo tài liệu Java SE 17/21 về `Files.move`:

1. `ATOMIC_MOVE` yêu cầu move dưới dạng thao tác nguyên tử của file system.
2. **Mọi tùy chọn move khác đều bị bỏ qua** khi có `ATOMIC_MOVE`.
3. Nếu đích đang tồn tại, provider **tự quyết định** có thay thế đích hay ném `IOException`. Không có lời hứa đa nền tảng cho việc replace.
4. Nếu không thể thực hiện move nguyên tử, ném `AtomicMoveNotSupportedException`, ví dụ khi buộc phải copy giữa hai file store khác nhau hoặc khi đường dẫn thuộc provider khác nhau.
5. Ngay cả khi hai đường dẫn cùng file store, thao tác vẫn có thể thất bại vì chính sách/cơ chế của provider hoặc lỗi I/O khác.

Từ khóa cần nhớ: `ATOMIC_MOVE` là **yêu cầu phải có tính nguyên tử**, không phải “cố gắng nguyên tử, không được thì Java sẽ tự hạ xuống copy rồi delete”.

## 3. So sánh move thường, replace và atomic move

| Cách gọi | Ý định | Khi target đã tồn tại | Có bảo đảm atomic không? |
| --- | --- | --- | --- |
| `Files.move(src, dst)` | Di chuyển/đổi tên theo mặc định | Thường thất bại, trừ trường hợp cùng file hoặc quy tắc provider | Không |
| `Files.move(src, dst, REPLACE_EXISTING)` | Cho phép thay thế đích nếu được hỗ trợ | Thay thế trong giới hạn API; thư mục không rỗng không thể bị thay thế theo cách này | Không |
| `Files.move(src, dst, ATOMIC_MOVE)` | Yêu cầu move nguyên tử | Có thể thay thế hoặc thất bại tùy provider | Có nếu **hoàn tất thành công với hợp đồng của provider** |
| `Files.move(src, dst, ATOMIC_MOVE, REPLACE_EXISTING)` | Vẫn là yêu cầu atomic move | **`REPLACE_EXISTING` bị bỏ qua**; như dòng trên | Có nếu hoàn tất đúng thao tác nguyên tử |

Các giá trị trên là `StandardCopyOption.ATOMIC_MOVE` và `StandardCopyOption.REPLACE_EXISTING`. Trong các bảng/ví dụ rút gọn, hai enum được viết không kèm tên lớp khi ngữ cảnh đã rõ.

Điểm dễ sai nhất:

```java
Files.move(staging, finalPath,
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING);
```

Không được chú thích dòng này là “chắc chắn atomic overwrite”. Hợp đồng Java nói `ATOMIC_MOVE` khiến **tất cả tùy chọn khác bị bỏ qua**, còn đích tồn tại thì hành vi replace tùy implementation. Cách viết trên không thể biến một nền tảng không hỗ trợ thay thế nguyên tử thành nền tảng có hỗ trợ.

## 4. `AtomicMoveNotSupportedException` là ngoại lệ gì?

Ngoại lệ đầy đủ:

```java
java.nio.file.AtomicMoveNotSupportedException
```

Nó kế thừa `FileSystemException`, mà `FileSystemException` kế thừa `IOException`. Đây là **checked exception**. Khi gọi `Files.move`, chương trình cần xử lý/khai báo `IOException` hoặc các lớp con liên quan.

Ví dụ:

```java
try {
    Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
} catch (AtomicMoveNotSupportedException e) {
    // Tính nguyên tử không được hỗ trợ cho lần move này.
    // Quyết định theo nghiệp vụ: thất bại hoặc dùng phương án không nguyên tử.
}
```

Đoạn trên cần được đặt trong phương thức xử lý các `IOException` còn lại và có import `java.nio.file.*` thích hợp. Không nên bắt mọi `IOException` rồi kết luận “ATOMIC_MOVE không được hỗ trợ”.

| Ngoại lệ | Ý nghĩa có thể gặp | Cách phản ứng |
| --- | --- | --- |
| `AtomicMoveNotSupportedException` | Move nguyên tử không thể được thực hiện trong ngữ cảnh này | Không giả định đã thực hiện move; chọn chính sách fallback hoặc báo lỗi |
| `FileAlreadyExistsException` | Target đã tồn tại và provider không thể/không cho thay thế trong tình huống đó | Kiểm tra chính sách conflict, không tự ghi đè |
| `AccessDeniedException` | Thiếu quyền sửa đổi mục/thư mục hoặc có hạn chế truy cập | Kiểm tra quyền và tình trạng file đang sử dụng |
| `NoSuchFileException` | Source hoặc một phần đường dẫn cần thiết không có | Xem lại đường dẫn/đồng thời thay đổi |
| `DirectoryNotEmptyException` | Không thể thay thế thư mục đích không rỗng trong điều kiện API | Không biến thao tác này thành recursive delete ngoài ý muốn |
| `IOException` khác | Lỗi I/O, file system, khóa, mạng hoặc provider | Xem loại và nguyên nhân; không giả định trạng thái hậu lỗi |

Với non-atomic move, Javadoc nêu rõ nếu `IOException` xuất hiện **trạng thái source/target không được xác định hoàn toàn**: cả hai có thể tồn tại hoặc target có thể mới được copy một phần. Vì thế, một fallback không nguyên tử cần cả **chính sách khôi phục**, không chỉ một dòng gọi API.

## 5. Tại sao atomic move có thể không được hỗ trợ?

### 5.1. Chuyển giữa hai file store

Đổi tên file trong cùng một file store thường có thể được provider thực hiện bằng primitive rename của hệ điều hành. Nhưng khi source và target thuộc các file store khác nhau, di chuyển có thể cần:

```text
Source filesystem A                  Destination filesystem B
          |                                     ^
          +---- copy byte / metadata -----------+
          |
          +---- delete old file khi hoàn tất
```

Quy trình copy rồi delete gồm nhiều bước; một bên quan sát có thể thấy target đang được copy, hoặc hệ thống gặp lỗi giữa chừng. Java không thể hứa toàn bộ quy trình này là một file-system atomic operation phổ quát.

Ví dụ Windows chuyển từ một volume sang volume khác; Unix/Linux chuyển qua hai mount khác nhau; dữ liệu đang ở file-system provider ZIP rồi muốn đổi sang file system mặc định.

**Lưu ý:** “cùng ổ đĩa vật lý” không tương đương “cùng `FileStore`”. Các mount point, partition, network FS và provider có thể có quy tắc riêng. Và **cùng `FileStore` chỉ là điều kiện thuận lợi**, không phải chứng chỉ atomic move chắc chắn sẽ thành công.

### 5.2. Provider và hệ thống tệp khác nhau

`Path` trong Java thuộc một `FileSystem`; `FileSystem` được tạo bởi `FileSystemProvider`. Những file system như ZIP/JAR hoặc provider ảo có thể không hỗ trợ atomic rename giống filesystem mặc định.

`Files.move` có thể làm việc giữa các provider theo cách của API, nhưng khi được yêu cầu `ATOMIC_MOVE` thì việc chuyển dữ liệu liên-provider có thể không có primitive nguyên tử chung.

### 5.3. Giới hạn thực tế khác

Ngay trong cùng file store, thao tác có thể thất bại bởi quyền, file lock, khả năng provider, loại mục (thư mục/file/symlink), quy tắc chia sẻ trên Windows, giới hạn network FS, hoặc race với tiến trình khác. Không suy đoán tất cả lỗi đều là `AtomicMoveNotSupportedException`: nhiều lỗi xuất hiện dưới các lớp `IOException` khác.

## 6. Ví dụ Java 17+ chạy được: yêu cầu atomic, fallback có nhãn rõ

Ví dụ này tạo một file staging trong cùng thư mục với target, ghi nội dung đầy đủ, rồi thử `ATOMIC_MOVE`. Target cố tình **chưa tồn tại** để tránh phụ thuộc vào quy tắc replace-target của provider. Nếu không hỗ trợ atomic move, bản demo chọn fallback thường để minh họa nhánh lỗi; điều này được in ra rõ ràng.

Tạo file `AtomicMoveDemo.java`:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class AtomicMoveDemo {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("atomic-move-");
        Path staging = Files.createTempFile(directory, "stage-", ".tmp");
        Path target = directory.resolve("published.txt");

        try {
            Files.writeString(staging, "version-2", StandardCharsets.UTF_8);

            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
                System.out.println("mode: atomic");
            } catch (AtomicMoveNotSupportedException ex) {
                // Chỉ bản demo chấp nhận giảm bảo đảm để vẫn có output.
                // Ứng dụng thật phải chọn chính sách này một cách chủ động.
                Files.move(staging, target);
                System.out.println("mode: non-atomic fallback");
            }

            System.out.println("content: "
                    + Files.readString(target, StandardCharsets.UTF_8));
            System.out.println("staging exists: " + Files.exists(staging));
        } finally {
            Files.deleteIfExists(staging);
            Files.deleteIfExists(target);
            Files.deleteIfExists(directory);
        }
    }
}
```

Lệnh thực thi:

```bash
javac AtomicMoveDemo.java
java AtomicMoveDemo
```

Khi provider hỗ trợ `ATOMIC_MOVE`, stdout:

```text
mode: atomic
content: version-2
staging exists: false
```

Nếu provider từ chối move nguyên tử nhưng cho phép move thường, stdout thay thế:

```text
mode: non-atomic fallback
content: version-2
staging exists: false
```

Hai trường hợp đều đúng với điều kiện tương ứng. Nếu gặp lỗi khác (quyền, file đang bị sử dụng, lỗi I/O), chương trình có thể kết thúc bằng exception thay vì các dòng trên. Đây không phải demo chứng minh máy nào cũng hỗ trợ atomic move.

**Giải thích:** `Files.createTempFile(directory,...)` đặt staging trong cùng thư mục cha với target, làm tăng khả năng được phép rename nguyên tử; chương trình không hứa điều đó. Khi move thành công, tên staging biến mất và target mang nội dung `version-2`. Trong nhánh fallback, tính nguyên tử **không được bảo đảm**, dù quan sát sau khi hoàn tất vẫn có vẻ giống nhau. Kết quả một lần chạy chỉ nói về môi trường và thao tác ấy, không chứng minh một provider luôn hỗ trợ mọi tình huống atomic move.

Lưu ý đường dẫn tạm phụ thuộc OS; demo không in đường dẫn ngẫu nhiên để stdout có thể so sánh.

## 7. Thiết kế publish file: staging hoàn chỉnh rồi mới move

Một ứng dụng cập nhật cấu hình hoặc dữ liệu thường dùng quy trình:

```text
[1] Tạo staging trong thư mục đích
          |
[2] Ghi toàn bộ nội dung, kiểm tra dữ liệu
          |
[3] Nếu yêu cầu độ bền: force dữ liệu staging
          |
[4] Đóng handle ghi
          |
[5] Files.move(staging, target, ATOMIC_MOVE)
          |
[6] Nếu thành công: target được xuất bản
    Nếu unsupported/lỗi: làm theo chính sách, ghi nhận và dọn staging có kiểm soát
```

Đây là mô hình **staging + publish**:

- Người đọc không sử dụng staging làm nguồn dữ liệu chính.
- Chỉ khi nội dung đã chuẩn bị đầy đủ mới đổi tên sang đường dẫn chính thức.
- Mục tiêu của move nguyên tử là không quan sát thấy trạng thái trung gian của bản thân thao tác đổi tên.
- Cần định nghĩa rõ điều gì xảy ra nếu target đã tồn tại; Java không hứa atomic replacement trên mọi provider.
- Nếu ứng dụng lưu nhiều file liên quan nhau, cần cơ chế phiên bản/manifest hoặc giao thức công bố khác thay vì giả định từng lần move độc lập tạo ra toàn bộ transaction.

### 7.1. Mẫu nghiêm ngặt: yêu cầu atomic, thiếu là thất bại

Khi tính nguyên tử là một điều kiện **bắt buộc**, hãy giữ `ATOMIC_MOVE` là precondition:

```java
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

static void publishStrict(Path staging, Path target) throws IOException {
    try {
        Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
        throw new IOException(
                "Cannot publish atomically: " + staging + " -> " + target, e);
    }
}
```

Đây là **phương thức minh họa**, đặt trong một lớp Java với các import tương ứng. Nó chỉ xử lý nhánh ngoại lệ cụ thể; các `IOException` khác truyền lên. Caller chịu trách nhiệm quản lý staging, lỗi và mục tiêu tồn tại. Không chuyển sang `Files.move` thường sau khi yêu cầu nguyên tử thất bại, vì điều đó làm yếu hợp đồng của chức năng xuất bản.

### 7.2. Mẫu cho phép giảm bảo đảm: fallback có chủ đích

Khi nghiệp vụ chấp nhận một giai đoạn di chuyển không nguyên tử, có thể bắt đúng `AtomicMoveNotSupportedException` rồi sử dụng `Files.move(source,target)` hoặc `REPLACE_EXISTING` theo chính sách.

Nhưng cần ghi rõ:

1. Fallback làm **mất bảo đảm atomicity**.
2. Trong nhánh non-atomic, nếu có `IOException`, tình trạng source/target **có thể chưa hoàn chỉnh hoặc không xác định**.
3. Chính sách có cho phép target đang tồn tại bị thay thế không? Nếu có, có được mất bản cũ nếu chương trình bị ngắt ở giữa không?
4. Có cần khôi phục từ bản sao staging/back-up, kiểm tra hash, marker trạng thái hoặc chạy reconcile lần sau không?
5. Nếu hệ thống dùng nhiều writer song song, ai có quyền publish phiên bản cuối cùng?

Không nên bắt tất cả `IOException` rồi luôn retry non-atomic: những lỗi như permission hoặc disk full sẽ không được giải quyết bằng cách đổi option, có thể còn làm hỏng trạng thái.

## 8. `ATOMIC_MOVE` với target đã tồn tại: bẫy quan trọng

Giả sử source chứa phiên bản mới và target chứa phiên bản cũ:

```text
source: new.tmp     = "version-2"
target: config.txt  = "version-1"
```

Với code:

```java
Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
```

Kết quả có thể tùy provider:

- Provider hỗ trợ atomic replace, thao tác thành công và đường dẫn target chuyển sang file mới.
- Provider từ chối vì target đã tồn tại và ném `IOException`.
- Nếu không hỗ trợ atomic move trong ngữ cảnh này, `AtomicMoveNotSupportedException` có thể được ném.

Thêm `REPLACE_EXISTING` **không sửa hợp đồng** của `ATOMIC_MOVE` vì các option khác bị bỏ qua. Kể cả khi bạn đã kiểm thử trên hệ điều hành A, không được suy luận hệ điều hành B hoặc ZIP/SMB/NFS sẽ xử lý target đã tồn tại giống hệt.

**Một điều không nên làm để né conflict:** xóa `target` trước, sau đó atomic move source vào. Hai thao tác `delete` và `move` không được hợp nhất thành một giao dịch nguyên tử. Trong khoảng giữa, target không tồn tại, phá vỡ mục tiêu cập nhật không gián đoạn.

Nếu yêu cầu “replace existing atomically” là bắt buộc, hãy ghi rành mạch trong hợp đồng ứng dụng và kiểm thử provider/file store/OS của môi trường triển khai. Khi không đáp ứng, thất bại rõ ràng hoặc dùng kiến trúc snapshot/versioned file; không lén giảm bảo đảm.

## 9. Atomicity khác durability: move thành công chưa chắc an toàn khi mất điện

Hai khái niệm thường bị trộn lẫn:

| Khái niệm | Câu hỏi | `ATOMIC_MOVE` có giải quyết hoàn toàn không? |
| --- | --- | --- |
| **Atomicity/visibility** | Một người đọc quan sát move như một thao tác duy nhất hay trạng thái trung gian? | Hướng tới điều này nếu move nguyên tử thành công |
| **Durability** | Khi hệ thống mất điện, nội dung và tên file đã tồn tại bền vững trên lưu trữ chưa? | **Không** được bảo đảm chỉ bởi `ATOMIC_MOVE` |
| **Consistency** | File có đúng định dạng, checksum, liên kết logic với dữ liệu khác không? | Không; ứng dụng phải kiểm tra |
| **Isolation** | Nhiều writer có tranh chấp phiên bản hoặc đọc/ghi đồng thời không? | Không tạo transaction nhiều thao tác |
| **Crash recovery** | Sau crash, chương trình biết dữ liệu nào là hợp lệ và phục hồi ra sao? | Cần thiết kế riêng |

Trước khi publish dữ liệu quan trọng, thường cần buộc nội dung staging được đẩy xuống thiết bị:

```java
try (FileChannel channel = FileChannel.open(
        staging, StandardOpenOption.WRITE)) {
    channel.force(true);
}
Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
```

Đây là đoạn minh họa trong phương thức khai báo `throws IOException`, với `staging`/`target` đã tồn tại, và cần import `java.nio.channels.FileChannel`, `java.nio.file.StandardOpenOption`, `java.nio.file.Files`, `java.nio.file.StandardCopyOption`. `force(true)` trên `FileChannel` yêu cầu đẩy cập nhật nội dung **và metadata của file** xuống thiết bị trong phạm vi cam kết API, nhưng **không tự bảo đảm** metadata của **thư mục chứa tên file sau rename** đã durable.

Để hướng tới crash consistency cao trên một số OS, thường còn cần flush/fsync **thư mục** sau thao tác rename. Java NIO không cung cấp một hợp đồng di động chung đảm bảo cách mở và `force` thư mục sẽ hoạt động trên mọi hệ điều hành/provider; không viết code Java chung rồi tuyên bố “100% crash safe trên mọi filesystem”. Một số hệ thống dùng transaction/journal ở tầng khác hoặc giao thức versioned publish + checkpoint.

Cũng cần phân biệt: `Files.move` không làm cho nội dung file mới hợp lệ một cách tự động. Nếu file staging được ghi thiếu, JSON sai cấu trúc hoặc checksum lỗi, một atomic move thành công vẫn sẽ xuất bản **nội dung sai đó một cách nguyên tử**.

## 10. Tạo staging cùng thư mục đích và những giới hạn còn lại

Lựa chọn thường tốt:

```java
Path dir = target.toAbsolutePath().getParent();
Path staging = Files.createTempFile(dir, ".publish-", ".tmp");
```

Đây là đoạn minh họa trong phương thức `throws IOException`; cần xác thực `target` có thư mục cha, quyền tạo tệp và mục tiêu hợp lệ. Tạo staging trong thư mục cha của target làm tăng xác suất hai đường dẫn cùng file store và được rename nguyên tử.

Nhưng vẫn có các giới hạn:

- `Files.getFileStore(staging)` hoặc `Files.getFileStore(dir)` có thể cho biết file store tại thời điểm kiểm tra, **không chứng minh atomic move chắc chắn hỗ trợ**.
- Nếu target là một symlink, replace-target và chính sách tương tác với symlink có thể ảnh hưởng đường đi. `Files.move` di chuyển **chính symlink** nếu source là symlink, không di chuyển nội dung đích.
- Đường dẫn cha có thể được thay bằng link hoặc đổi quyền bởi tiến trình khác giữa hai thao tác; với môi trường không tin cậy phải có chính sách bảo mật đường dẫn/thư mục phù hợp.
- Nếu staging chứa quyền/ownership/attributes không đúng, atomic rename không tự biến nó thành file có thuộc tính đúng. Cần chuẩn bị thuộc tính trước publish nếu bắt buộc.
- Một thao tác atomically **đổi tên** không có nghĩa mọi process đang giữ file descriptor tới file cũ lập tức thấy nội dung mới. Reader đã mở handle có thể tiếp tục nhìn file cũ theo ngữ nghĩa OS; nên mở lại đường dẫn hoặc dùng protocol version theo ứng dụng.

## 11. Không dùng `ATOMIC_MOVE` như một phép thử "an toàn tuyệt đối"

Những ngộ nhận thường gặp:

| Ngộ nhận | Vì sao sai | Cách sửa |
| --- | --- | --- |
| `ATOMIC_MOVE` luôn được hỗ trợ trên Java 17+ | Khả năng phụ thuộc provider, file store, OS và thao tác | Bắt `AtomicMoveNotSupportedException`; kiểm tra môi trường |
| Có `ATOMIC_MOVE` thì Java tự fallback copy/delete | API yêu cầu move nguyên tử hoặc báo lỗi phù hợp | Chọn chính sách fallback rõ ràng |
| `ATOMIC_MOVE, REPLACE_EXISTING` buộc phải atomic overwrite | Các option khác bị bỏ qua; target tồn tại implementation-specific | Thiết kế và kiểm thử replacement theo provider |
| Hai đường dẫn cùng ổ đĩa thì chắc chắn atomic | Một file store/OS vẫn có thể có ràng buộc khác | Dùng thao tác thực và xử lý lỗi |
| `FileAlreadyExistsException` nghĩa là không hỗ trợ atomic move | Có thể là vấn đề conflict với target hiện tại | Phân biệt lỗi |
| Atomic rename đảm bảo dữ liệu durable sau mất điện | Visibility và durability khác nhau | Chuẩn bị flush file, có chiến lược flush metadata/recovery |
| Xóa target trước rồi atomic move staging thì vẫn atomic replace | Hai bước có khoảng target thiếu | Giữ target đến bước publish nếu cần |
| `Files.copy` rồi `Files.delete` là atomic | Copy/delete là chuỗi nhiều thao tác | Dùng atomic move trong phạm vi hỗ trợ hoặc thừa nhận giảm bảo đảm |
| `Files.exists(target)` trước là đủ để chống race | Check-then-act có TOCTOU | Dùng quyền truy cập, ownership và chiến lược đồng thời phù hợp |
| Catch mọi `IOException` rồi thử thường sẽ sửa lỗi | Lỗi quyền/thiết bị/đĩa không liên quan thiếu atomicity | Phân loại exception, giữ ngữ cảnh lỗi |
| Atomic move là transaction gồm nhiều file | API áp dụng cho một thao tác move | Dùng manifest/version/checkpoint hoặc transaction cấp khác |

## 12. Đồng thời: nếu có hai writer cùng publish thì sao?

Giả sử writer A và B cùng chuẩn bị staging riêng cho `config.json`. Kể cả khi mỗi lần rename là nguyên tử, **không có đảm bảo** writer nào sẽ chiến thắng hoặc dữ liệu mới hơn là dữ liệu cuối cùng. `ATOMIC_MOVE` không cung cấp compare-and-swap theo phiên bản, lock phân tán hay đảm bảo thứ tự giao dịch.

Biện pháp phụ thuộc nghiệp vụ:

1. Chỉ một tiến trình có quyền publish trong mỗi thời điểm.
2. Sử dụng file lock hoặc hệ thống khóa phù hợp khi môi trường hỗ trợ, nhưng hiểu phạm vi khóa và sự hợp tác giữa tiến trình.
3. Gán version tăng dần, kiểm tra điều kiện cập nhật tại tầng điều phối, hoặc xuất bản file theo tên phiên bản bất biến.
4. Khi có nhiều file cần được đọc nhất quán, chỉ đổi một con trỏ manifest/current version cuối cùng sau khi tất cả file dữ liệu của phiên bản đã chuẩn bị xong.
5. Thiết kế recovery khi tiến trình chết ở bất kỳ thời điểm nào, bao gồm dọn staging tồn đọng có kiểm tra.

Một atomic move đảm bảo thuộc tính của **bản thân move đó nếu provider thực hiện thành công**; không thay thế giao thức quản lý phiên bản hay đồng thời.

## 13. Test tính hỗ trợ đúng cách

Một test có ý nghĩa nên thực hiện trong **môi trường thật** với:

- File source nằm trên đúng file store dự kiến.
- Target nằm ở đúng đường dẫn và điều kiện thực tế.
- Nếu cần thay thế, target **đã tồn tại**, để kiểm tra nhánh khó hơn.
- Quyền và ownership tương đương process chạy thật.
- Các tình huống lỗi (disk full, permission, concurrent reader/writer, target là directory/symlink) được kiểm tra có kiểm soát khi liên quan.

Ví dụ từ demo ở mục 6 chứng minh trường hợp **đích chưa tồn tại** trên đúng file store của máy chạy demo. Nó **không chứng minh** hỗ trợ replace-existing và không đo được crash durability.

Nếu muốn kiểm tra runtime:

```java
try {
    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    // Đã thành công cho source/target tại thời điểm này.
} catch (AtomicMoveNotSupportedException ex) {
    // Atomicity thiếu trong ngữ cảnh hiện tại.
} catch (IOException ex) {
    // Lỗi I/O khác; giữ thông tin để phân tích.
}
```

Mã này là đoạn minh họa trong phương thức; chưa xử lý chính sách file đích hay dọn source. Một phương thức `Files.move` thành công không thể khái quát rằng các file store khác, thư mục khác và target-existing cũng sẽ thành công.

## 14. Kịch bản thực tế và lựa chọn phù hợp

### A. Ghi cấu hình cần luôn thấy phiên bản hoàn chỉnh

Ghi vào file staging nằm cùng thư mục, xác thực nội dung, flush dữ liệu nếu yêu cầu độ bền, và thử `ATOMIC_MOVE`. Nếu replace-target là điều kiện bắt buộc nhưng provider không hỗ trợ, **báo lỗi và giữ phiên bản cũ** theo chính sách. Không chủ động `delete(target)` trước publish.

### B. Di chuyển file sang đĩa khác

Khi source ở volume A và destination ở volume B, atomic move có thể không khả thi. Nếu nhu cầu là chuyển dữ liệu, có thể chấp nhận copy vào một **staging ở đích**, xác minh checksum/độ dài, sau đó atomic rename từ staging đến tên cuối **trong file store đích**, và chỉ sau khi publish thành công mới cân nhắc xóa source. Đây là quy trình nhiều bước, **không bảo đảm atomic cho cả việc di chuyển xuyên volume**, nhưng có thể tránh xuất bản nội dung đích đang copy dở.

### C. Triển khai tập tin dung lượng lớn

Một chương trình tạo artifact `download.part`/file staging. Tới khi download + checksum hoàn tất, nó publish qua atomic rename nếu hỗ trợ. Dù rename có thể nguyên tử, vẫn phải thiết kế cách xử lý file chưa hoàn tất, timeouts và lỗi flush.

### D. Khóa tệp và tính bền

Nếu hệ thống đang dùng `FileLock`, đừng mặc định rằng lock của inode/file cũ bảo vệ tên target sau rename hoặc mọi provider tôn trọng lock theo cùng quy tắc. `FileLock` và `ATOMIC_MOVE` giải quyết vấn đề khác nhau: khóa kiểm soát truy cập đồng thời trong phạm vi OS/provider, còn atomic move kiểm soát tính nguyên tử của một thao tác di chuyển. Khả năng bền qua crash lại cần chính sách khác.

## 15. Câu hỏi tự kiểm tra

**1.** `Files.move(a,b,ATOMIC_MOVE)` có thể trả về thành công nếu target `b` đã tồn tại không?

**Đáp án:** Có thể, nhưng phụ thuộc provider; Java không bảo đảm cho mọi file system.

**2.** Thêm `REPLACE_EXISTING` vào `ATOMIC_MOVE` có bảo đảm thay thế target đang tồn tại không?

**Đáp án:** Không. Khi có `ATOMIC_MOVE`, tất cả option move khác bị bỏ qua; việc replace target vẫn implementation-specific.

**3.** Nếu source/target nằm trên hai file store khác nhau, Java có nhất định tự copy rồi delete để đảm bảo atomic không?

**Đáp án:** Không. Move nguyên tử có thể không khả thi; Java ném `AtomicMoveNotSupportedException` cho trường hợp không thể thực hiện atomic file-system move.

**4.** Khi `AtomicMoveNotSupportedException` xảy ra, có nên luôn fallback sang `Files.move` thường không?

**Đáp án:** Chỉ khi nghiệp vụ **chấp nhận mất bảo đảm atomicity** và có phương án khôi phục khi fallback bị lỗi giữa chừng. Nếu bảo đảm atomic là bắt buộc, phải báo lỗi.

**5.** Atomic move thành công có nghĩa đã chắc chắn durable sau mất điện không?

**Đáp án:** Không. Đó là bảo đảm về tính nguyên tử của thao tác move; độ bền của nội dung và metadata thư mục cần xử lý riêng, phụ thuộc OS/provider.

**6.** Có thể dùng `Files.delete(target)` rồi `Files.move(staging,target,ATOMIC_MOVE)` để tự tạo atomic replace không?

**Đáp án:** Không. Hai thao tác có khoảng thời gian target đã bị xóa nhưng staging chưa publish.

**7.** Hai writer cùng atomic move tới `config.json` có tự được sắp theo version mới nhất không?

**Đáp án:** Không. Atomic rename không cung cấp version ordering, CAS hay isolation cho một hệ thống nhiều writer.

**8.** Tại sao staging nên tạo tại thư mục cha của target?

**Đáp án:** Tăng khả năng source và target ở cùng file store và cùng provider, giảm nhu cầu copy qua volume. Tuy vậy vẫn cần xử lý ngoại lệ khi provider không hỗ trợ hoặc khi có lỗi khác.

## 16. Ghi nhớ và checklist khi triển khai

```text
ATOMIC_MOVE:
  - Yêu cầu move nguyên tử
  - Nếu không thể -> AtomicMoveNotSupportedException
  - Các tùy chọn move khác bị bỏ qua
  - Target tồn tại -> replace hay IOException phụ thuộc provider
  - Không mặc nhiên = durable / crash-safe / multi-file transaction
```

Trước khi đưa vào sản phẩm:

1. Xác định rõ mục tiêu: rename nguyên tử, atomic replacement, hay crash durability.
2. Tạo staging ở thư mục đích, ghi dữ liệu hoàn chỉnh và xác minh payload.
3. Nếu yêu cầu độ bền, cân nhắc `FileChannel.force(true)` và cơ chế flush metadata phù hợp hệ điều hành.
4. Chỉ publish sau khi hoàn tất chuẩn bị; không xóa bản đích cũ trước khi move.
5. Dùng `Files.move(..., ATOMIC_MOVE)` và phân biệt `AtomicMoveNotSupportedException` với các lỗi I/O khác.
6. Nếu nghiệp vụ đòi atomicity, **fail rõ ràng** khi không được hỗ trợ.
7. Nếu chấp nhận fallback, ghi rõ giảm bảo đảm và có cách xử lý source/target sau lỗi.
8. Kiểm thử trên file store, provider, OS, quyền và target-existing đúng môi trường triển khai.
9. Điều phối các writer và thêm cơ chế version/manifest nếu hệ thống cập nhật nhiều file.
10. Ghi nhận metadata và tình trạng staging để hỗ trợ recovery, tránh dọn nhầm dữ liệu hợp lệ.

## Tài liệu tham khảo

- Oracle Java SE 17 — `Files.move(Path,Path,CopyOption...)`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html
- Oracle Java SE 21 — `StandardCopyOption`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/StandardCopyOption.html
- Oracle Java SE 21 — `AtomicMoveNotSupportedException`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/AtomicMoveNotSupportedException.html
- Oracle Java SE 21 — `FileChannel.force(boolean)`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-locking-and-durability-009">Tính nguyên tử của đổi tên khác độ bền sau sự cố nguồn điện</a>

<details>
<summary>Xem chi tiết</summary>

# Tính nguyên tử của đổi tên khác độ bền sau sự cố nguồn điện

## 1. Vấn đề: “đổi tên thành công” không đồng nghĩa “đã lưu bền”

Trong Java NIO.2, một ứng dụng thường cập nhật tệp cấu hình hoặc trạng thái bằng mẫu **ghi tệp tạm rồi đổi tên**:

```text
Trước:      config.json = dữ liệu cũ
Chuẩn bị:   .config-123.tmp = dữ liệu mới
Công bố:    đổi tên .config-123.tmp thành config.json
Sau:        ứng dụng khác mở config.json sẽ thấy dữ liệu mới
```

Mẫu này giảm khả năng người đọc nhìn thấy tệp đích **đang được ghi dở**. Nhưng câu hỏi “người đọc có thấy bản cập nhật nguyên vẹn khi hệ thống đang chạy?” khác căn bản với “sau khi máy mất điện và khởi động lại, cập nhật có còn tồn tại?”.

Có ít nhất ba thuộc tính cần phân biệt:

1. **Atomicity của đổi tên (rename atomicity):** đối với thao tác tệp được hỗ trợ, việc di chuyển/đổi tên là một thao tác nguyên tử trong namespace của file system. Người quan sát không thấy trạng thái thay đổi tên *nửa chừng* do chính thao tác đó.
2. **Durability (độ bền):** byte và metadata cần thiết đã được đẩy xuống lưu trữ theo hợp đồng, để có thể khôi phục sau crash hoặc mất nguồn.
3. **Consistency của ứng dụng:** dữ liệu được công bố là một phiên bản hoàn chỉnh, hợp lệ về schema/checksum; các file liên quan nhau tuân theo bất biến. Atomic rename không tự kiểm tra nội dung và không tạo transaction nhiều file.

**Kết luận trung tâm:** `Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)` có thể cung cấp **atomic rename** khi provider hỗ trợ; bản thân nó **không phải một lệnh fsync**, cũng không tự tạo lời hứa rằng phiên bản mới sống sót qua sự cố nguồn điện.

Mục tiêu bài: hiểu `ATOMIC_MOVE` và giới hạn với `REPLACE_EXISTING`, phân biệt `FileChannel.force(...)` với ghi bền thư mục, thiết kế chuỗi “write → sync file → rename → sync directory” theo nền tảng, đọc đúng kết quả demo và tránh các giả định về crash consistency.

## 2. Ba thời điểm phải mô hình hóa riêng

Giả sử `state.dat` đang có nội dung `OLD` và chương trình muốn thay bằng `NEW`.

```text
T0: target = OLD
             │
             ▼
T1: tạo tệp tạm, ghi NEW (có thể còn trong cache)
             │
             ▼
T2: đồng bộ dữ liệu tệp tạm (nếu yêu cầu độ bền)
             │
             ▼
T3: atomic rename tệp tạm → target
             │
             ▼
T4: đồng bộ thư mục/metadata namespace (nếu nền tảng hỗ trợ)
             │
             ▼
T5: hoàn thành chính sách cập nhật bền của ứng dụng
```

Câu hỏi ở **T3**: khi rename hoàn tất, thao tác quan sát đường dẫn target được công bố như thế nào?  
Câu hỏi ở **T5**: nếu xảy ra crash ngay sau khi API trả về, nền tảng có cung cấp đủ bảo đảm để khôi phục tên tệp trỏ tới dữ liệu hợp lệ không?

Chúng là hai bài toán khác nhau. Việc thay tên có thể được nhìn thấy ngay trong namespace đang chạy, trong khi thay đổi của entry thư mục hoặc byte file vẫn cần được đồng bộ để có độ bền sau crash. Ngược lại, việc flush dữ liệu file tạm **trước** rename không tự đảm bảo tên cuối đã được lưu bền.

### 2.1. Phân biệt sự cố ứng dụng và sự cố mất nguồn

- **Process crash**: tiến trình Java dừng, nhưng OS vẫn chạy. Dữ liệu đang ở page cache có thể tiếp tục được OS ghi xuống đĩa.
- **OS crash/kernel panic**: hệ điều hành dừng bất thường; page cache chưa được ghi có thể mất.
- **Power loss**: mất điện đột ngột; ngoài page cache còn cần xét bộ đệm thiết bị, firmware, controller, hệ thống tệp, journal và các cam kết thực tế của ổ lưu trữ.

Đừng dùng thử nghiệm `kill -9` một tiến trình rồi đọc lại file làm bằng chứng rằng chương trình đã an toàn trước *mất điện*. Đó là kiểu lỗi khác với shutdown đột ngột của OS hoặc nguồn điện.

## 3. `Files.move` và `StandardCopyOption.ATOMIC_MOVE`

Java NIO.2 cung cấp:

```java
Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
```

Trong đó `source` và `target` là `Path` đã xác định; ví dụ minh họa phải nằm trong method xử lý `IOException` và import `java.nio.file.Files`, `java.nio.file.Path`, `java.nio.file.StandardCopyOption`.

Theo Java SE 21:

- `ATOMIC_MOVE` yêu cầu move diễn ra như **một thao tác nguyên tử của file system**.
- Nếu không hỗ trợ thao tác nguyên tử cho cặp source/target, API ném `AtomicMoveNotSupportedException`; một nguyên nhân điển hình là phải di chuyển giữa các `FileStore` khác nhau hoặc provider khác.
- Khi có `ATOMIC_MOVE`, **mọi option khác đều bị bỏ qua**. Không được suy ra rằng `ATOMIC_MOVE` cộng `REPLACE_EXISTING` sẽ luôn thay thế tệp đích đã tồn tại.
- **Nếu target đã tồn tại**, việc atomic move có thay thế target hay ném `IOException` là **implementation-specific**. Hãy xác minh contract/provider/môi trường chạy nếu muốn dùng để cập nhật đè một tệp hiện hữu.
- Khi không chỉ định `ATOMIC_MOVE`, `Files.move` không cam kết luôn có tính nguyên tử; nếu move cần copy/delete, trạng thái khi lỗi có thể phức tạp và tệp đích có thể không hoàn chỉnh.

**Quan trọng:** `Files.move` trả về thành công không đồng nghĩa Java đã gọi/hoàn thành fsync cho file và các directory entry liên quan. `ATOMIC_MOVE` là yêu cầu *atomicity*, không phải option *durability*.

### 3.1. `REPLACE_EXISTING` không thay thế `ATOMIC_MOVE`

```java
Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
```

`REPLACE_EXISTING` chỉ yêu cầu thay thế target hiện hữu trong phạm vi contract và ngoại lệ tương ứng. Nó **không tự cam kết** atomic rename. Với hai option:

```java
Files.move(source, target,
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING);
```

`ATOMIC_MOVE` khiến các option còn lại bị bỏ qua theo API. Không mô tả câu lệnh này như “bảo đảm overwrite nguyên tử trên mọi OS”. Khi target đã tồn tại, chính sách replace và các giới hạn liên quan phải được xác nhận cho file system cụ thể.

### 3.2. Vì sao nên đặt tệp tạm cạnh đích?

`Files.createTempFile(target.getParent(), ...)` tạo tệp tạm trong **cùng thư mục** chứa đích. Điều đó giúp tránh tình huống tệp tạm mặc định nằm ở ổ đĩa khác, khiến atomic rename không thực hiện được.

Tuy nhiên, “cùng thư mục” **không phải bằng chứng tuyệt đối** provider hỗ trợ `ATOMIC_MOVE`. Quyền truy cập, loại file system, mount, trạng thái target và hành vi provider vẫn quyết định thành công. Dùng đúng API và xử lý `AtomicMoveNotSupportedException`.

## 4. Atomicity bảo đảm điều gì cho người đọc đang chạy?

Giả sử rename là nguyên tử và file system thực hiện thao tác thành công. Người đọc mở đường dẫn target **trước** rename có thể đã giữ handle tới phiên bản cũ, trong khi người đọc mở **sau** rename có thể lấy phiên bản mới, tùy semantics của OS/provider và ứng dụng.

Điểm được bảo vệ là **bước thay đổi tên** không bị triển khai thành một chuỗi thao tác có trạng thái rename một nửa. Nhưng:

- Nó không bảo đảm tất cả người đọc đang giữ file handle cũ đều lập tức nhìn nội dung mới.
- Nó không ngăn chương trình khác sửa trực tiếp file đích hoặc file tạm cùng lúc.
- Nó không chuyển những lần ghi vào **cùng một file đang mở** thành nguyên tử; hãy ghi đủ dữ liệu vào file tạm trước khi publish.
- Nó không bảo đảm tính nguyên tử nếu một bản cập nhật bao gồm nhiều file hoặc nhiều đường dẫn riêng lẻ.
- Nó không làm dữ liệu hợp lệ về JSON, checksum hoặc quy tắc nghiệp vụ.
- Nó không tự nói gì về tính bền vững sau mất điện.

**So sánh:** ghi đè trực tiếp file đích `TRUNCATE_EXISTING` rồi viết từng chunk có thể để người đọc gặp tệp rỗng/thiếu byte. Ghi đầy đủ vào file tạm, sau đó dùng atomic move được hỗ trợ, giúp tránh vấn đề *công bố nội dung dang dở* đối với các quan sát đường dẫn trong điều kiện phù hợp.

## 5. `FileChannel.force(boolean)` và độ bền nội dung tệp

Java cung cấp cách yêu cầu flush dữ liệu tệp xuống thiết bị:

```java
try (FileChannel channel = FileChannel.open(
        temp,
        StandardOpenOption.WRITE)) {

    // Sau khi ghi byte bằng channel...
    channel.force(true);
}
```

Đây là đoạn minh họa với `temp` là `Path` hợp lệ; import `FileChannel` và `StandardOpenOption`, method xử lý `IOException`. Giá trị `true` yêu cầu flush **nội dung cùng metadata** liên quan của tệp trong phạm vi contract; `false` chỉ yêu cầu nội dung, không nhất thiết metadata.

Các điều kiện và giới hạn quan trọng trong Java SE:

- Khi file nằm trên **thiết bị lưu trữ cục bộ**, `force(...)` có bảo đảm về những thay đổi liên quan đến file theo hợp đồng `FileChannel`.
- Với file trên thiết bị **không cục bộ** như một số network filesystem, Java **không đưa ra cùng bảo đảm**.
- Java `FileChannel.force` chỉ bảo đảm cho những thay đổi đã được thực hiện qua phương thức của channel và các đường dẫn liên quan mà API quy định. Không mặc định nó flush mọi thứ do code khác/provider khác vừa viết.
- Nếu tệp được sửa qua `MappedByteBuffer`, `channel.force(...)` có thể **không** flush đầy đủ thay đổi qua mapping; cần `mapped.force()` theo contract của buffer.
- `force(true)` có thể cần thao tác I/O tốn chi phí. Dùng có chủ đích ở điểm commit, không phải mặc định sau mỗi byte nếu không cần.
- Thành công của `force(true)` **đối với tệp** chưa đồng nghĩa entry tên `target` sau thao tác rename đã bền trên ổ lưu trữ.

### 5.1. Lỗi phổ biến: gọi `force` sau khi rename nhưng không phân biệt phạm vi

Giả sử ghi đầy đủ vào `temp`, gọi `channel.force(true)` rồi rename. Tại thời điểm `force`, Java yêu cầu đồng bộ **tệp temp**; **rename xảy ra sau đó** làm thay đổi directory entry. Vì thế bước `force` trước rename không thể “đồng bộ trước” một namespace change vẫn chưa xảy ra.

Ngay cả khi mở lại `target` và `force(true)`, hợp đồng của `FileChannel.force` nói về thay đổi của **file do channel quản lý**; nó **không cung cấp API đồng bộ portable cho directory entry đã đổi tên**. Không coi `FileChannel.force(true)` trên file cuối là đủ để bảo đảm parent directory đã được lưu bền.

## 6. Parent directory cũng chứa metadata cần được ghi bền

Trong hệ thống tệp, sự liên kết giữa **tên** `state.dat` và đối tượng file nằm trong metadata của thư mục. Khi rename, directory entry thay đổi.

Trên Linux/POSIX, manual `fsync(2)` nói rõ: gọi `fsync` trên **file** không nhất thiết đồng bộ entry của **directory chứa file**; để có bảo đảm ghi entry tương ứng, cần `fsync` trên file descriptor của thư mục.

Vì vậy một quy trình cập nhật mạnh trên hệ thống có hỗ trợ phù hợp thường có dạng:

```text
1. Tạo file tạm trong cùng directory với target.
2. Ghi toàn bộ dữ liệu mới vào file tạm.
3. fsync/force tệp tạm để ghi bền nội dung (+ metadata cần thiết).
4. Atomic rename file tạm thành tên target.
5. fsync thư mục cha để ghi bền thay đổi tên trong namespace.
6. Chỉ sau đó mới xác nhận cập nhật bền theo chính sách của nền tảng.
```

Đây là **quy trình mức hệ điều hành**, không phải lời hứa rằng Java `Files.move` đã tự làm cả bước 3 và bước 5. Nếu rename đi qua **hai thư mục khác nhau**, chính sách durability có thể cần sync cả hai thư mục nguồn và đích. Ví dụ tạm cùng thư mục làm quy trình dễ kiểm soát hơn.

### 6.1. Có thể mở directory bằng `FileChannel` rồi `force(true)` cho mọi OS không?

**Không có bảo đảm portable như vậy từ Java SE 17/21.** Việc mở directory như một `FileChannel` phụ thuộc OS, provider và quyền. `FileChannel.force` cũng không phải một API Java được định nghĩa chung để sync mọi thay đổi directory entry do `Files.move` thực hiện.

Ví dụ bạn có thể gặp trong mã riêng cho Linux:

```java
// Minh họa tùy nền tảng; không phải giải pháp Java portable.
try (FileChannel dir = FileChannel.open(
        directory, StandardOpenOption.READ)) {
    dir.force(true);
}
```

Đoạn trên **không** được dùng để tuyên bố “bảo đảm đồng bộ rename trên Windows/macOS/Linux với Java NIO.2”. Một hệ thống có yêu cầu mất điện nghiêm ngặt nên dùng adapter nền tảng/OS đã kiểm chứng cho thao tác fsync directory, hoặc giao thức lưu trữ/engine cung cấp hợp đồng durability thích hợp; đồng thời kiểm thử trên thiết bị, file system và cấu hình triển khai thực tế.

### 6.2. Journaled filesystem cũng không miễn trừ bài toán

Một journal có thể giúp phục hồi metadata sau crash, nhưng chính sách journaling khác nhau: thứ tự data/metadata, chế độ mount, flush cache thiết bị, barrier, snapshot, network server và crash-recovery semantics có thể khác nhau.

Không thể suy diễn “hệ thống tệp có journaling” ⇒ mọi `Files.move(ATOMIC_MOVE)` đã bền sau mất điện. Khi thiết kế dữ liệu quan trọng, cần đọc contract của platform/file system, kể cả khả năng thiết bị báo flush thành công mà không thực sự lưu bền như kỳ vọng.

## 7. Ví dụ Java 17+ chạy được: ghi tệp tạm, force, yêu cầu rename nguyên tử

Ví dụ này chứng minh các thao tác Java NIO được hỗ trợ và nêu **ranh giới bảo đảm**. Để tránh phụ thuộc vào hành vi replace target đã tồn tại (implementation-specific), chương trình tạo **đường dẫn target chưa có** trong một thư mục tạm riêng.

**Tệp:** `AtomicRenameDemo.java`

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public class AtomicRenameDemo {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("atomic-rename-");
        Path target = directory.resolve("state.txt");
        Path staging = null;

        try {
            staging = Files.createTempFile(directory, ".state-", ".tmp");

            byte[] payload = "READY".getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(
                    staging, StandardOpenOption.WRITE)) {

                ByteBuffer buffer = ByteBuffer.wrap(payload);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }

                channel.force(true); // Flush nội dung và metadata tệp staging.
            }

            System.out.println("before move exists: "
                    + Files.exists(target));

            try {
                Files.move(staging, target,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                System.out.println("atomic move: unsupported");
                return; // Không âm thầm thay bằng move thường.
            }

            System.out.println("after move: "
                    + Files.readString(target, StandardCharsets.UTF_8));
            System.out.println("staging exists: "
                    + Files.exists(staging));

            // Đây mới là demo atomic move + force(file).
            // Java SE không bảo đảm parent-directory fsync portable.
        } finally {
            // Chỉ dọn những file do demo tạo.
            if (staging != null) {
                Files.deleteIfExists(staging);
            }
            Files.deleteIfExists(target);
            Files.deleteIfExists(directory);
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp nguồn:

```bash
javac AtomicRenameDemo.java
java AtomicRenameDemo
```

**Stdout dự kiến khi filesystem hỗ trợ `ATOMIC_MOVE`:**

```text
before move exists: false
after move: READY
staging exists: false
```

**Nếu provider không hỗ trợ atomic move**, output đi theo nhánh khác:

```text
before move exists: false
atomic move: unsupported
```

Diễn giải:

1. `createTempDirectory` tạo một thư mục riêng, `createTempFile(directory, ...)` đặt staging file **cùng thư mục** với target.
2. Dữ liệu `READY` được encode UTF-8 rồi ghi với vòng lặp `while (buffer.hasRemaining())` vì một lần `channel.write` không được bảo đảm ghi hết.
3. `channel.force(true)` được gọi khi channel vẫn mở, sau khi byte đã được ghi bằng chính channel đó.
4. `Files.move(..., ATOMIC_MOVE)` yêu cầu thao tác rename nguyên tử. Nếu không thể, ví dụ báo `unsupported` và **không hạ cấp lặng lẽ**.
5. Ở nhánh thành công, target chứa toàn bộ chuỗi `READY` và staging path không tồn tại.
6. Cuối chương trình dọn file tạm. Việc demo mở/đọc file thành công chứng minh nội dung hiện nhìn thấy, **không chứng minh** directory entry đã được sync bền qua mất điện.

**Lưu ý:** ngay cả với filesystem hỗ trợ `ATOMIC_MOVE`, I/O vẫn có thể thất bại bởi lý do khác (quyền, hết dung lượng, lỗi thiết bị, lỗi xóa file tạm...). Chương trình sẽ để `IOException` được báo ra ngoài, không hứa một stdout cố định cho mọi lỗi môi trường.

### 7.1. Vì sao demo không gọi `Files.move(..., REPLACE_EXISTING)` khi `ATOMIC_MOVE` thất bại?

Vì yêu cầu của ví dụ là **không công bố bản cập nhật bằng một thao tác không đảm bảo atomicity**. Nếu fallback sang move thông thường, lời hứa “người đọc chỉ thấy một bản đầy đủ” không còn được bảo đảm bởi `ATOMIC_MOVE`.

Trong ứng dụng thật, bạn có thể cho phép một fallback kém mạnh hơn, nhưng đó phải là **quyết định nghiệp vụ rõ ràng**, có thông báo trạng thái, hành vi lỗi và quy trình recovery; không thể dùng cùng nhãn “atomic commit”.

## 8. Khi cập nhật đè target đã tồn tại: điểm phức tạp cần xử lý

Tình huống “lần ghi đầu” (target chưa có) đơn giản hơn tình huống “thay phiên bản đang dùng”. Muốn thay `settings.json` hiện hữu bằng bản mới:

```text
T0: target = phiên bản A
T1: staging = phiên bản B hợp lệ
T2: sync staging
T3: atomic rename staging → target (nếu platform hỗ trợ overwrite)
T4: sync parent directory theo platform
```

**Không nên giả định** `Files.move(staging, target, ATOMIC_MOVE, REPLACE_EXISTING)` luôn thay A bằng B. Vì khi có `ATOMIC_MOVE`, option còn lại bị bỏ qua; khi target có sẵn, **replace hay fail là implementation-specific**.

Một thiết kế production cần:

- Xác định rõ OS, filesystem, provider và khả năng atomic replace trên target hiện hữu.
- Xác nhận quyền, chính sách symlink, hard link và race với tiến trình khác.
- Viết tệp tạm hoàn chỉnh và kiểm tra nội dung trước khi công bố.
- Kiểm tra kết quả `force` và rename, không báo “committed/durable” nếu bước bắt buộc chưa hoàn thành.
- Đảm bảo recovery sau crash có thể phân biệt bản hợp lệ, bản cũ, file tạm hoặc trạng thái thiếu.
- Với yêu cầu durability mạnh, triển khai sync directory phù hợp nền tảng.

**Nguyên tắc nhất quán:** atomic rename một target **không** làm một nhóm file (ví dụ `config.json` và `index.json`) cùng đổi trong một transaction. Khi phải công bố nhiều file nhất quán, có thể cân nhắc ghi các file bất biến theo phiên bản, sau đó atomic update **một file manifest/pointer** duy nhất, kết hợp chính sách durability/recovery phù hợp.

## 9. Bảng “đã làm gì?” và “được bảo đảm gì?”

| Thao tác | Cải thiện tính chất gì? | Không tự bảo đảm điều gì? |
|---|---|---|
| `Files.write(...)` vào target | Ghi nội dung file | Không atomic publish, không chắc durable |
| Ghi file tạm hoàn chỉnh | Có phiên bản B riêng để kiểm tra | Chưa công bố target; chưa chắc durable |
| `channel.force(false)` | Flush nội dung theo `FileChannel` | Không nhất thiết flush metadata; không sync entry rename |
| `channel.force(true)` | Flush nội dung và metadata file theo contract | Không tự sync directory entry của rename sau đó |
| `Files.move(temp,target,ATOMIC_MOVE)` | Yêu cầu atomic namespace move khi hỗ trợ | Không tự fsync file/directory, không luôn replace được target có sẵn |
| `Files.move(temp,target,REPLACE_EXISTING)` | Yêu cầu thay target khi hỗ trợ | Không cam kết atomicity nếu thiếu `ATOMIC_MOVE` |
| Native `fsync(parentDir)` đúng platform | Có thể làm durable cập nhật directory entry theo contract OS | Không chữa payload chưa sync hoặc mọi lỗi phần cứng |
| `FileLock` | Điều phối một số truy cập concurrent theo semantics file lock | Không phải atomic rename, không phải cơ chế durable |
| `close()` channel | Giải phóng tài nguyên, có thể flush các layer liên quan theo contract của chúng | Không tương đương một lời gọi `force(true)` chắc chắn trong mọi trường hợp |

### 9.1. Tại sao `close()` không thể thay thế chính sách fsync?

Đóng một Writer/OutputStream có thể đẩy buffer trong process đến kênh I/O, nhưng Java không tuyên bố mọi `close` tương đương `FileChannel.force(true)` về độ bền dữ liệu và metadata. Cần gọi API sync đúng cách khi durability là yêu cầu. Ngay cả sau đó vẫn phải xử lý directory entry nếu nghiệp vụ cần bảo đảm rename bền qua mất nguồn.

### 9.2. `StandardOpenOption.SYNC` và `DSYNC`

Khi mở file để ghi, Java hỗ trợ:

- `SYNC`: yêu cầu mỗi cập nhật nội dung **hoặc metadata** được ghi đồng bộ tới thiết bị nền.
- `DSYNC`: yêu cầu cập nhật **nội dung** được ghi đồng bộ tới thiết bị nền; metadata không nhất thiết đi theo, trừ khi cần để truy xuất dữ liệu.

Đây là **option lúc mở file**, không phải option của `Files.move`:

```java
try (FileChannel ch = FileChannel.open(
        staging,
        StandardOpenOption.WRITE,
        StandardOpenOption.SYNC)) {
    // Ghi nội dung theo chính sách SYNC.
}
```

Ví dụ minh họa với `staging` đã tồn tại; các option phụ thuộc provider, chi phí đồng bộ có thể cao. `SYNC` hoặc `DSYNC` **không tự sync directory entry của rename xảy ra sau đó**. Với network filesystem, durability cuối cùng vẫn cần tuân theo contract của hệ thống bên dưới.

## 10. Kết quả sau crash: không được suy ra duy nhất từ một lời gọi API

Ta cần phân tích theo thời điểm xảy ra lỗi và giả định về OS/file system.

| Thời điểm sự cố | Trong lúc chạy đã quan sát gì? | Điều cần lưu ý khi phục hồi |
|---|---|---|
| Trước khi ghi đủ staging | Target cũ chưa công bố bản mới | Staging có thể thiếu dữ liệu; chỉ chấp nhận file có validation |
| Sau ghi staging, chưa sync file | Có thể đọc được staging B | Byte B có thể chưa durable |
| Sau sync staging, trước rename | Staging B được yêu cầu ghi bền | B chưa phải tên target; cần recovery cleanup/resume |
| Sau atomic rename, trước sync directory | Target B có thể đã nhìn thấy ngay | Không được mặc định directory entry B bền sau mất nguồn |
| Sau sync file + rename + sync directory đúng hợp đồng | Đã thực hiện các bước commit bền theo nền tảng | Vẫn phải tuân theo giới hạn thiết bị/OS và phục hồi dữ liệu ứng dụng |

Bảng này là **mô hình phân tích rủi ro**, không phải dự đoán tất định rằng “sau power loss luôn chỉ có A hoặc B” trên mọi file system. Java SE không quy định crash-recovery semantics cho từng thiết bị/provider. Không khẳng định chắc chắn target cũ hay mới sẽ tồn tại khi thiếu các bước sync cần thiết.

### 10.1. Một tên đích có đồng nghĩa một transaction không?

Nếu file mới chứa nhiều bản ghi, atomic rename chỉ đổi **con trỏ tên** từ phiên bản cũ sang phiên bản mới tại một thời điểm quan sát trong filesystem. Chính sách hợp lệ của file vẫn cần checksum, version, độ dài, schema và kiểm tra cấu trúc. Nếu ứng dụng còn cập nhật database, message queue hoặc nhiều file, rename không bảo đảm atomicity xuyên các tài nguyên đó.

Ví dụ: ghi `state.json` rồi gửi message “phiên bản 2 đã sẵn sàng” trước khi bảo đảm độ bền. Sau mất điện, message có thể đã đến consumer trong khi namespace file chưa bền, tạo sự bất nhất. Cần thiết kế đúng thứ tự commit và recovery.

## 11. Kiểm tra và xử lý lỗi trong ứng dụng thật

### 11.1. Không nuốt `AtomicMoveNotSupportedException`

```java
try {
    Files.move(staging, target,
            StandardCopyOption.ATOMIC_MOVE);
} catch (AtomicMoveNotSupportedException ex) {
    // Chính sách: trả về lỗi rõ ràng, hoặc
    // thực hiện fallback với bảo đảm yếu hơn được mô tả riêng.
    throw ex;
}
```

Đây là đoạn minh họa trong method xử lý `IOException`. Không được chuyển thành `Files.move(..., REPLACE_EXISTING)` rồi tiếp tục log “atomic update succeeded”. Nếu sản phẩm chọn fallback, nên có tín hiệu phân biệt cả trong log/metrics và tài liệu API.

### 11.2. Cẩn thận về cleanup khi có lỗi

Nếu việc ghi staging thất bại, có thể còn file chưa hoàn chỉnh. Nếu rename chưa được thực hiện, dọn staging theo chính sách và giữ target cũ. Nếu rename đã thành công, không được giả định staging vẫn ở đường dẫn cũ. Ví dụ dùng:

```java
Path staging = Files.createTempFile(parent, ".update-", ".tmp");
try {
    // Ghi đủ bytes, force, validation, atomic rename ...
} finally {
    Files.deleteIfExists(staging);
}
```

Đây là đoạn mã minh họa trong method xử lý `IOException`. `finally` chạy khi process vẫn có cơ hội thực thi Java, không chạy nếu máy mất điện. Cleanup phải an toàn đối với file do chương trình tạo và không xóa target sau khi đã publish.

### 11.3. Kiểm tra failure mode mà không phá dữ liệu thật

- Unit test với thư mục tạm có thể xác minh nhánh rename thành công, quyền, xử lý exception, nội dung cuối và cleanup.
- Integration test trên filesystem thực có thể kiểm tra khả năng atomic move và hành vi replace target hiện hữu.
- Kiểm thử concurrent reader có thể tìm lỗi giao thức publish, nhưng **không chứng minh** độ bền sau mất điện.
- Crash/power-loss test cần môi trường thử riêng, cơ chế khôi phục và phép đo thực tế. Không cắt nguồn máy production để suy diễn.
- Nếu provider không hỗ trợ atomic move hoặc directory sync đáng tin, hệ thống phải hạ mức bảo đảm đã công bố hoặc chọn thiết kế lưu trữ khác.

## 12. Lỗi phổ biến

1. **“`ATOMIC_MOVE` nghĩa là dữ liệu đã durable.”** Sai: atomicity của namespace và durability của lưu trữ là hai thuộc tính riêng.
2. **“`FileChannel.force(true)` trước rename đủ để lưu tên cuối.”** Sai: rename cập nhật entry directory **sau** force file.
3. **“Đóng file tự động tương đương fsync file và fsync directory.”** Không có bảo đảm portable từ `close`.
4. **“`ATOMIC_MOVE + REPLACE_EXISTING` luôn overwrite nguyên tử.”** Java quy định các option khác bị bỏ qua; target đã tồn tại xử lý theo implementation.
5. **“Cùng một thư mục chắc chắn có atomic rename.”** Chỉ tăng khả năng tương thích; provider vẫn có thể từ chối.
6. **“Move không có `ATOMIC_MOVE` cũng chắc chắn là atomic trên mọi OS.”** Không được contract cam kết.
7. **“Nếu `Files.move` trả thành công, chắc chắn power-loss recovery giữ bản mới.”** Chưa đủ; cần các bước sync đúng phạm vi và contract nền tảng.
8. **“fsync file = fsync parent directory.”** Trên Linux, hai việc khác nhau; entry của thư mục cần sync riêng để có bảo đảm thích hợp.
9. **“`FileChannel.open(dir).force(true)` là directory fsync portable.”** Java SE không cung cấp hợp đồng chung như vậy.
10. **“Mọi thiết bị đều tôn trọng đúng flush.”** Độ bền thực phụ thuộc OS, firmware, controller, filesystem và thiết bị.
11. **“Rename giữ transaction cho nhiều file.”** Một thao tác rename không tạo commit nguyên tử cho một tập file hoặc database.
12. **“Kill process rồi thấy file đúng là đã kiểm chứng mất điện.”** Process crash không tương đương OS crash hoặc power loss.
13. **“File tạm để ở `java.io.tmpdir` vẫn atomic move sang đích.”** Temp mặc định có thể nằm trên file store khác.
14. **“Ghi xong một lần `channel.write` là đủ.”** NIO write có thể chỉ ghi một phần; phải lặp cho hết `ByteBuffer`.
15. **“Dùng `FileLock` thay sync.”** Lock liên quan phối hợp truy cập, không tự tạo độ bền.
16. **“Staging B đọc được” nghĩa là “B đã durable”.** Byte có thể chỉ ở page cache.
17. **“Journaling bảo đảm ứng dụng không bao giờ thấy file hỏng.”** Phụ thuộc mode filesystem và chính sách ghi nội dung, chưa kể dữ liệu ứng dụng.
18. **“Không cần recovery vì đã dùng atomic rename.”** Vẫn phải kiểm tra version/checksum và xử lý temp/file cũ theo chính sách.
19. **“Dùng `force(false)` luôn đủ metadata cần thiết.”** False không bắt buộc flush mọi metadata; xem nhu cầu thực.
20. **“Không có lỗi I/O tức đã đạt mức đảm bảo cao nhất.”** API chỉ cam kết trong phạm vi cụ thể; phần không được quy định phải được đánh giá riêng.

## 13. Câu hỏi tự kiểm tra

**Câu 1.** `Files.move(temp,target,ATOMIC_MOVE)` thành công. Điều đó có bảo đảm target mới còn sau mất điện không?  
**Đáp án:** Không. Nó yêu cầu atomic namespace move, không tự bảo đảm nội dung file và directory entry đã được ghi bền.

**Câu 2.** Nếu target tồn tại, `ATOMIC_MOVE` có bắt buộc thay thế nó không?  
**Đáp án:** Không. Hợp đồng Java để implementation quyết định replace hoặc thất bại; option khác như `REPLACE_EXISTING` bị bỏ qua khi có `ATOMIC_MOVE`.

**Câu 3.** Vì sao ghi temp trong cùng directory hữu ích?  
**Đáp án:** Tránh một nguyên nhân move không atomic: source và target ở các file store khác nhau. Nhưng vẫn phải kiểm tra provider hỗ trợ.

**Câu 4.** `FileChannel.force(true)` trên temp trước rename làm được gì?  
**Đáp án:** Trong điều kiện contract về lưu trữ cục bộ và cách ghi qua channel, yêu cầu flush nội dung cùng metadata file. Nó chưa sync đổi tên diễn ra sau đó.

**Câu 5.** Trên Linux, vì sao cần xem xét `fsync` parent directory sau rename?  
**Đáp án:** Vì `fsync` trên file không mặc định lưu bền entry tên trong thư mục; rename sửa namespace metadata của thư mục.

**Câu 6.** Java SE 17/21 có API directory fsync portable bảo đảm mọi OS không?  
**Đáp án:** Không có lời hứa như vậy từ `Files.move` hay `FileChannel.force`; cần đánh giá giải pháp nền tảng riêng nếu yêu cầu mức bảo đảm đó.

**Câu 7.** Nếu `ATOMIC_MOVE` không hỗ trợ thì gọi `REPLACE_EXISTING` và báo thành công “atomic update” được không?  
**Không.** Đây là chế độ có bảo đảm thấp hơn; nếu fallback phải công bố lại contract.

**Câu 8.** Dùng `channel.force(true)` sau khi sửa qua `MappedByteBuffer` có đủ chắc chắn không?  
**Đáp án:** `FileChannel.force` có thể không flush sửa đổi qua mapped buffer; xem `MappedByteBuffer.force()`.

**Câu 9.** Làm sao phân biệt kiểm thử process crash với kiểm thử power loss?  
**Đáp án:** Process crash để OS tiếp tục chạy và ghi cache; power loss ngắt cả khả năng tiếp tục flush, nên kết luận khác nhau.

**Câu 10.** Khi cần cập nhật đồng thời nhiều file cùng một phiên bản, atomic rename từng file có tạo một transaction không?  
**Không.** Cần giao thức riêng như snapshot bất biến + manifest/pointer commit, hoặc dùng storage engine có transaction/durability đã được xác minh.

## 14. Ghi nhớ và tài liệu chính thức

**Một câu cần nhớ:** *Đổi tên nguyên tử là thuộc tính của thao tác công bố tên; ghi bền là thuộc tính của dữ liệu/metadata sau crash. Muốn cả hai, phải thiết kế cả chuỗi ghi file, đồng bộ file, atomic rename và đồng bộ directory theo contract nền tảng.*

Tài liệu tham khảo:

- Java SE 21 — `Files.move`, `ATOMIC_MOVE` và `REPLACE_EXISTING`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html
- Java SE 21 — `StandardCopyOption`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/StandardCopyOption.html
- Java SE 21 — `FileChannel.force(boolean)`, `SYNC` và `DSYNC`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html
- Java SE 21 — `StandardOpenOption`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/StandardOpenOption.html
- Linux man-pages — `fsync(2)`, đặc biệt ghi chú về đồng bộ thư mục chứa file: https://man7.org/linux/man-pages/man2/fsync.2.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-locking-and-durability-010">Giới hạn nhất quán trên network file system</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn nhất quán trên network file system trong Java NIO

## 1. Vấn đề: hai tiến trình cùng nhìn một tệp qua mạng có thấy cùng dữ liệu không?

Một **network file system** cho phép nhiều máy truy cập tệp được lưu ở máy chủ hoặc một dịch vụ lưu trữ chung. Ví dụ quen thuộc là **NFS** trên Linux/Unix, **SMB/CIFS** trong môi trường chia sẻ tệp và những file share được mount vào máy ứng dụng.

Giả sử:

~~~text
          Máy A                                  Máy B
     Java writer                            Java reader
       FileChannel                            Files.read...
           |                                       |
    OS page/attribute cache                 OS page/attribute cache
           |                                       |
           +-------------- Network ----------------+
                                |
                          File server
                                |
                         Backing storage
~~~

Máy A ghi `balance=900` và gọi `channel.force(true)`, rồi máy B đọc cùng đường dẫn được mount. Ta cần phân biệt **ba câu hỏi độc lập**:

1. **Visibility (khả năng nhìn thấy):** máy B sẽ thấy ngay phiên bản mới, hay còn thấy bản cũ trong cache?
2. **Durability (tính bền vững):** thay đổi đã được lưu đủ bền để tồn tại sau crash của client, server hoặc hệ thống lưu trữ chưa?
3. **Concurrency/atomicity (đồng thời/nguyên tử):** nếu A và B cùng ghi, có bảo đảm không bị mất cập nhật, bản ghi nửa mới nửa cũ hoặc trình tự khóa sai không?

**Không có một phương thức Java NIO nào tự giải quyết đầy đủ cả ba câu hỏi trên cho mọi network file system.** API Java phụ thuộc vào file-system provider, OS, client cache, giao thức mạng, cấu hình mount và máy chủ lưu trữ.

Bài này tập trung vào giới hạn nhất quán của các thao tác `Files.*`, `FileChannel`, `FileLock`, `MappedByteBuffer` và các mode đồng bộ khi tệp nằm trên hệ thống file qua mạng. Những nhận xét riêng cho NFS không mặc nhiên áp dụng giống hệt cho SMB, hệ thống file phân tán có coherence mạnh hoặc dịch vụ object storage.

## 2. Ba mức nhất quán dễ bị nhầm lẫn

### 2.1. Visibility: có thể đọc lại bản cũ do cache

Ứng dụng Java trên máy B có thể đọc dữ liệu từ page cache hoặc metadata cache của OS. Thời điểm làm mới cache phụ thuộc giao thức và cấu hình. Một phép `Files.readAllBytes(path)` **không mang ý nghĩa đặc biệt “bắt server trả bản mới nhất từ mọi client”**.

Ví dụ thời gian:

~~~text
t0  A và B cùng nhìn version=1
t1  B cache bản version=1
t2  A ghi version=2 và đóng file
t3  B đọc lại nhưng cache chưa được làm mới -> có thể thấy version=1
t4  B mở lại / cache được xác thực hoặc hết hạn -> có thể thấy version=2

Thời điểm, hành vi cụ thể ở t3/t4 phụ thuộc filesystem,
giao thức, mount options và cách chương trình mở/đọc tệp.
~~~

Nếu ứng dụng yêu cầu rằng mọi client đều thấy một lần ghi đã hoàn tất ngay lập tức, nó cần một **hợp đồng nhất quán cụ thể** từ hệ thống lưu trữ. Không được suy luận điều đó chỉ từ tên đường dẫn giống nhau.

### 2.2. Durability: dữ liệu nhìn thấy mới chưa chắc đã bền

Một lời gọi `write(...)` có thể đã làm dữ liệu xuất hiện trong cache của writer, trong khi chưa có bằng chứng dữ liệu đã được lưu an toàn trên thiết bị hoặc được máy chủ cam kết ở mức cần thiết. Gọi `force` có ý nghĩa đối với file cục bộ, nhưng **đặc tả Java 17 không đưa ra bảo đảm tương tự cho file trên thiết bị không cục bộ**.

Ngay cả khi server trả lời đã nhận dữ liệu, tính bền vững trước sự cố điện, thiết bị, replica hoặc nhiều failure domain còn phụ thuộc chính sách của server/storage.

### 2.3. Atomicity và ordering: nhất quán tệp không phải giao dịch

Ghi tuần tự hai trường:

~~~java
// Đoạn minh họa, channel đã mở để ghi.
channel.write(header);
channel.write(body);
~~~

không có nghĩa mọi reader ở máy khác sẽ luôn quan sát cả `header` và `body` như một thay đổi nguyên tử. Mỗi lời gọi có thể ghi một phần, hai lời gọi có thể xen với thao tác của writer khác nếu không được phối hợp.

**Một thao tác I/O thành công, cache coherence, atomic rename, mutual exclusion và durability là những thuộc tính khác nhau.** Ứng dụng cần xác định riêng từng yêu cầu.

## 3. NFS thường cung cấp close-to-open, không phải coherent cache tuyệt đối

Với nhiều cách triển khai NFS phổ biến, mô hình **close-to-open (CTO)** được sử dụng:

1. Client A mở tệp và cập nhật nội dung.
2. A đóng tệp; client NFS có cơ hội đẩy các thay đổi đang chờ lên server.
3. Client B **mở tệp sau đó**; client B có cơ hội xác thực thuộc tính/tình trạng tệp ở server và làm mới dữ liệu cache cần thiết.
4. B đọc bản mới, **nếu** hệ thống tuân theo chính sách CTO trong điều kiện và cấu hình đó.

~~~text
Client A: open -> write -> close
                                    Client B: open -> read -> close
                 ^ ranh giới close-to-open giúp tăng khả năng nhìn thấy
~~~

**CTO không có nghĩa** mọi lượt `read` trên một handle đã mở từ lâu lập tức thấy từng byte A vừa ghi. B cũng không được bảo đảm đọc một snapshot nguyên tử của toàn bộ chuỗi cập nhật A đang thực hiện.

Các điểm cần đặc biệt lưu ý:

- NFS có thể cache **data** và **metadata/attributes** riêng; hai loại cache có lịch làm mới khác nhau.
- Tùy phiên bản giao thức, client kernel và mount options, kết quả quan sát có thể khác.
- Mount option `nocto` trên một số NFS client có thể tắt một phần hành vi close-to-open.
- Mount option `noac` trên Linux NFS nhằm giảm cache thuộc tính, **không có nghĩa toàn bộ data cache đã bị tắt** và cũng không xóa hết race.
- Hệ thống file phân tán khác có thể cung cấp coherence mạnh hơn NFS thông thường; hãy đọc tài liệu của đúng sản phẩm.

**Kết luận thực hành:** Khi cần chuyển giao dữ liệu hoàn chỉnh từ A sang B, thường nên thiết kế ranh giới **“writer hoàn tất → đóng/commit → thông báo phiên bản mới → reader mở/kiểm tra lại”** theo giao thức đã kiểm nghiệm. Nhưng đây vẫn là một **quy trình ứng dụng**, không phải lời hứa tuyệt đối từ Java cho mọi NFS/SMB.

## 4. Metadata cache gây ra những nhầm lẫn gì?

Các thao tác như:

- `Files.exists(path)`
- `Files.size(path)`
- `Files.getLastModifiedTime(path)`
- `Files.readAttributes(path, BasicFileAttributes.class)`
- duyệt thư mục bằng `Files.list(...)` hoặc `DirectoryStream`

có thể phụ thuộc những thông tin file/directory do OS hoặc server cung cấp, và thông tin đó có thể đã bị cache trên client.

Ví dụ máy A đổi tên `ready.tmp` thành `ready.dat`, trong lúc máy B vẫn có cache danh sách thư mục. Máy B **có thể** chưa nhìn thấy entry mới ngay tại thời điểm ứng dụng mong đợi. Việc quan sát entry mới còn chịu ảnh hưởng của các quy tắc rename và invalidation cụ thể.

Một mẫu sai:

~~~java
// Minh họa logic chưa an toàn trước thay đổi đồng thời:
if (Files.exists(path)) {
    long size = Files.size(path);
    byte[] bytes = Files.readAllBytes(path);
    // Không có bảo đảm size và bytes cùng một thời điểm/snapshot.
}
~~~

Từ thời điểm `exists` tới `size` rồi `readAllBytes`, file có thể bị thay đổi, xóa hoặc thay thế. Đây là **TOCTOU (time-of-check to time-of-use)**; nó vẫn có thể xảy ra trên filesystem cục bộ, và cache/mạng làm mô hình quan sát phức tạp hơn.

**Không lấy timestamp hoặc size làm version tuyệt đối.** Độ phân giải thời gian, đồng hồ client/server, cache và chuỗi thay đổi có cùng kích thước làm cách kiểm tra này có thể bỏ sót cập nhật. Nếu cần phát hiện chính xác phiên bản, lưu **version/counter, checksum hoặc manifest** được quản lý rõ ràng.

## 5. `FileChannel.force(...)`: bảo đảm cục bộ không tự mở rộng sang mạng

Java 17 định nghĩa:

~~~java
// Mã minh họa; channel là FileChannel đã mở.
channel.force(false); // Yêu cầu ghi bền các thay đổi nội dung.
channel.force(true);  // Yêu cầu ghi bền nội dung và metadata.
~~~

Theo đặc tả `FileChannel.force(boolean metaData)`:

- Nếu file trên **local storage device**, khi `force` trả về, những cập nhật thuộc hợp đồng đã được ghi xuống thiết bị.
- Nếu file **không nằm trên thiết bị cục bộ**, **không có bảo đảm tương tự**.
- `metaData=false` yêu cầu đồng bộ nội dung; `metaData=true` bao gồm cả metadata cần thiết.
- Khi cập nhật được thực hiện qua **memory-mapped buffer**, `FileChannel.force(...)` **không được bảo đảm** tự force các dirty page do mapped buffer sửa. Cần dùng `MappedByteBuffer.force()` cho nội dung mapping.

Điều này **không có nghĩa** gọi `force` trên NFS/SMB chắc chắn vô ích. Nó có thể dẫn tới yêu cầu ghi/sync phù hợp trên OS và server. Điều không được phép khẳng định là: **“Java đã bảo đảm dữ liệu sống qua mọi crash của mạng/server vì `force(true)` trả về.”**

### `RandomAccessFile` và các mode đồng bộ

| API/Mode | Ý nghĩa trên Java | Giới hạn với file qua mạng |
|---|---|---|
| `RandomAccessFile(..., "rw")` | Đọc/ghi, không bắt buộc đồng bộ trên từng lần ghi | Không có checkpoint durability per-write được hứa |
| `RandomAccessFile(..., "rwd")` | Đồng bộ phần **content** khi cập nhật | Java không đưa ra bảo đảm tương tự local storage trên network device |
| `RandomAccessFile(..., "rws")` | Đồng bộ **content và metadata** khi cập nhật | Vẫn chịu giới hạn trên remote storage |
| `FileChannel.force(false/true)` | Chủ động yêu cầu content / content+metadata được ghi xuống thiết bị | Không phải chứng cứ về replica/quorum hoặc server crash recovery |
| `MappedByteBuffer.force()` | Yêu cầu đồng bộ thay đổi trong vùng đã map | Bảo đảm sau return được đặc tả cho local storage, không tự mở rộng sang remote |

**SYNC/DSYNC** trong `StandardOpenOption` cũng biểu đạt yêu cầu cập nhật đồng bộ tương ứng nhưng không nên được xem là giải pháp bỏ qua giới hạn giao thức/máy chủ mạng. Một file provider có thể không hỗ trợ một số tuỳ chọn hoặc thực hiện chúng theo cơ chế của nó.

### Vì sao đọc lại ngay sau force không chứng minh durability?

Sau khi `write` và `force`, một phép đọc trên cùng client có thể trả dữ liệu từ cache đã chứa phiên bản mới, **kể cả khi server/storage chưa đáp ứng mức bền vững mà ứng dụng cần**.

Muốn kiểm chứng cần thử nghiệm đúng hệ thống NFS/SMB, phiên bản server, mount options và failure model. Việc mô phỏng crash, mất mạng, failover và đọc từ client khác là quan trọng. Ngay cả test thành công cũng chỉ chứng minh hành vi trong cấu hình/điều kiện đã thử, không thành một bảo đảm API Java tổng quát.

## 6. `FileLock`: điều phối quyền ghi, nhưng cần kiểm tra hỗ trợ NFS

Java cung cấp `FileChannel.lock(...)`, `tryLock(...)` và đối tượng `java.nio.channels.FileLock`.

~~~java
// Đoạn minh họa: FileChannel mở với quyền ghi phù hợp.
try (FileLock lock = channel.lock()) {
    // Vùng code cần bảo vệ bởi cùng một quy ước lock.
    updateSharedFile();
    channel.force(true);
} // lock.close()/release() giải phóng khóa.
~~~

Một số điều quan trọng từ đặc tả:

1. **Lock có phạm vi theo file/byte range**, ví dụ `channel.lock(0, 128, false)` yêu cầu khóa **exclusive** trên vùng 128 byte.
2. `lock(position, size, true)` yêu cầu **shared lock**, nhưng hệ thống không hỗ trợ shared lock có thể chuyển thành exclusive. Hãy kiểm tra `FileLock.isShared()` nếu semantics này quan trọng.
3. File lock có tính chất thuộc **JVM**, không được thiết kế để điều phối các thread bên trong cùng JVM. Với các thread, dùng cơ chế đồng bộ Java thích hợp, chẳng hạn `synchronized` hay `ReentrantLock`.
4. Một số nền tảng dùng **advisory lock**: tiến trình không tuân theo giao thức lock vẫn có thể truy cập file. Nền tảng khác có cơ chế mandatory; không giả định giống nhau.
5. Trên network filesystem, việc hỗ trợ lock phụ thuộc phiên bản giao thức, server, client, mount/configuration và kiểu vùng khóa. Java cảnh báo một số NFS không hỗ trợ lock cho vùng map trừ khi canh theo page size hoặc có giới hạn offset/độ dài.
6. Việc đóng channel trên một số hệ điều hành có thể khiến các khóa liên quan tới file trong JVM được giải phóng ngoài dự kiến. Nên dùng **một channel chuyên quản lý khóa trên một file** trong luồng công việc.

Vì vậy **“đã lấy được FileLock” chưa tự chứng minh tính nhất quán toàn cluster**. Cần chứng minh các client tương tác qua cùng một hệ thống khóa, không có client bỏ qua lock, lock không bị mất khi network partition/failover, và ứng dụng xử lý trường hợp không thể gia hạn hoặc xác nhận ownership.

### Ví dụ: tránh mất cập nhật khi hai writer cùng sửa số dư

Hai writer cùng làm quy trình sai:

~~~text
A đọc balance = 1000
B đọc balance = 1000
A tính 1000 - 100 = 900, ghi 900
B tính 1000 - 200 = 800, ghi 800
Kết quả cuối có thể = 800; cập nhật của A bị mất.
~~~

Muốn serialize, cả A và B cần tuân thủ:

~~~text
acquire lock
    -> mở/đọc phiên bản đang có theo quy trình cache coherence đã kiểm nghiệm
    -> xác minh version / điều kiện cập nhật
    -> ghi phiên bản mới
    -> yêu cầu đồng bộ theo chính sách durability
release lock
~~~

Ngay cả có lock, nếu B đọc **bản cache cũ** hoặc server mất lock khi failover, kết quả vẫn có thể sai. Dùng **version number + compare-and-swap logic** ở tầng ứng dụng hoặc hệ lưu trữ phù hợp khi yêu cầu nghiêm ngặt. Một chương trình tự kiểm tra version rồi ghi trên file thường cũng có thể gặp race nếu hai thao tác không được bảo vệ bởi cơ chế coordination đáng tin cậy.

### `tryLock()` và lỗi khi lock không sẵn sàng

- `channel.tryLock()` thường trả về `null` nếu không lấy được khóa ngay vì một tiến trình khác đang giữ khóa tương ứng.
- Nếu trong cùng JVM đã có lock chồng lấn, có thể gặp `OverlappingFileLockException` thay vì `null`.
- Một lời gọi lock có thể thất bại do `IOException` hoặc do provider/OS không hỗ trợ hành vi được yêu cầu.
- Đừng spin vô hạn gọi `tryLock()` mà không có backoff/timeout theo thiết kế. Trên NFS, phản hồi chậm hoặc lỗi mạng cần được xử lý riêng.

## 7. Atomic rename không đồng nghĩa data durability hoặc snapshot xuyên client

Một cách publish file thường dùng:

1. Ghi nội dung hoàn chỉnh vào file tạm **trong cùng thư mục**.
2. Flush/sync tệp tạm theo khả năng nền tảng.
3. Đổi tên file tạm thành tên mà reader dùng.
4. Reader chỉ xử lý file được công bố.

Trong Java có thể yêu cầu:

~~~java
// Đoạn minh họa; temp và target đã được chuẩn bị hợp lệ.
Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
~~~

**Những giới hạn quan trọng:**

- `ATOMIC_MOVE` yêu cầu move là thao tác **nguyên tử theo filesystem**. Nếu không hỗ trợ, Java ném `AtomicMoveNotSupportedException`. Thường cần nguồn và đích trên cùng FileStore/provider; không chắc chắn khi đổi qua mount khác.
- Nếu đích đã tồn tại, việc thay thế hay báo lỗi khi dùng `ATOMIC_MOVE` là **implementation-specific** theo Java; không giả định kiểu replace luôn thành công.
- Một rename được thực hiện nguyên tử trên server **không tự đảm bảo** mọi client đã hủy cache ngay lập tức hoặc đọc được cùng một snapshot của nhiều file.
- `ATOMIC_MOVE` cũng **không tự bảo đảm durability** của nội dung tệp tạm, metadata hoặc directory entry sau server crash.
- Nếu không dùng `ATOMIC_MOVE`, giữa kiểm tra tồn tại và thao tác move có thể có race; việc move có thể không nguyên tử với các thao tác filesystem khác.

Đừng dùng cách sai:

~~~java
// Minh họa điều cần tránh khi dữ liệu chưa được phối hợp.
Files.writeString(target, largeDocument); // Reader có thể bắt gặp trạng thái đang ghi.
~~~

Cách publish qua file tạm và rename thường tốt hơn cho **reader chỉ cần toàn bộ bản hoàn tất**, nhưng vẫn cần xác định semantics của đúng shared filesystem và quy tắc reader re-open/verify.

### Manifest và checksum cho dữ liệu nhiều file

Nếu một “phiên bản dữ liệu” gồm `data-1.bin`, `data-2.bin` và `metadata.json`, việc đổi tên từng file không tạo transaction xuyên ba file. Một thiết kế thường dùng:

- Ghi các file phiên bản mới dưới **tên không trùng, bất biến**.
- Force/sync theo khả năng hệ lưu trữ và kiểm tra checksum.
- Khi tất cả sẵn sàng, publish **manifest** trỏ tới tên/version của các file mới.
- Reader đọc manifest, xác minh version/checksum, và chỉ dùng các file khớp phiên bản.
- Nếu checksum hoặc version không khớp, reader **retry theo chính sách có giới hạn**, không âm thầm coi nội dung là mới.

Mô hình này cải thiện khả năng **phát hiện phiên bản hỗn hợp**. Nó vẫn không xóa bỏ yêu cầu lưu bền manifest hay giả định cache consistency của hệ thống.

## 8. Chương trình Java 17+ chạy được: minh họa lock, ghi và force trên file cục bộ

Chương trình dưới đây dùng **một file tạm cục bộ**, có thể chạy ổn định không cần mount NFS. Nó minh họa API Java và trạng thái mà một reader **trên cùng môi trường kiểm thử** có thể xác nhận. Kết quả **không chứng minh** NFS có cùng visibility hoặc durability.

Lưu tệp **`NetworkFileConsistencyDemo.java`**:

~~~java
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class NetworkFileConsistencyDemo {

    private static void writeAll(FileChannel channel, ByteBuffer data)
            throws IOException {
        while (data.hasRemaining()) {
            int n = channel.write(data);
            if (n == 0) {
                throw new IOException("No write progress");
            }
        }
    }

    private static void readExactly(FileChannel channel, ByteBuffer dst)
            throws IOException {
        while (dst.hasRemaining()) {
            int n = channel.read(dst);
            if (n < 0) {
                throw new EOFException("Incomplete record");
            }
            if (n == 0) {
                throw new IOException("No read progress");
            }
        }
    }

    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("fs-consistency-");
        Path file = dir.resolve("record.bin");

        try {
            try (FileChannel writer = FileChannel.open(file,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE);
                 FileLock lock = writer.lock()) {

                ByteBuffer record = ByteBuffer.allocate(16);
                record.putLong(1L);    // version
                record.putLong(900L);  // balance
                record.flip();

                writeAll(writer, record);
                writer.force(true); // Trên local file có bảo đảm Java phù hợp.
                System.out.println("writer: version=1, balance=900");
            }

            // Mở lại sau khi writer đã hoàn tất và đóng channel.
            try (FileChannel reader = FileChannel.open(
                    file, StandardOpenOption.READ)) {
                ByteBuffer record = ByteBuffer.allocate(16);
                readExactly(reader, record);
                record.flip();

                System.out.println("reader: version="
                        + record.getLong() + ", balance="
                        + record.getLong());
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac NetworkFileConsistencyDemo.java
java NetworkFileConsistencyDemo
~~~

**Stdout mong đợi khi filesystem cục bộ cho phép thao tác:**

~~~text
writer: version=1, balance=900
reader: version=1, balance=900
~~~

**Diễn giải:**

1. `ByteBuffer.allocate(16)` có đủ chỗ cho hai số `long`, mỗi số 8 byte. `putLong` ghi dưới dạng nhị phân big-endian mặc định.
2. Writer lấy `FileLock`, ghi đến khi `ByteBuffer` hết remaining, rồi gọi `force(true)` trước khi đóng.
3. Sau khi writer và lock đã đóng, reader mở file, đọc đúng 16 byte và giải mã version/balance.
4. Nếu chạy trên NFS/SMB, stdout **có thể** giống ví dụ, nhưng điều đó không phải thử nghiệm crash/replication/coherence. Các lời gọi lock, force, close và read phải được kiểm chứng trên cấu hình storage cụ thể.

**Lưu ý:** trên một số hệ điều hành, xóa file ngay khi mapping/handle còn mở có thể thất bại; chương trình dùng `try-with-resources` để đóng lock và channel trước khi dọn file. Nếu filesystem có lỗi I/O/permission, chương trình ném ngoại lệ và không tạo đủ stdout.

### Kiểm thử visibility trên hai client thật

Để thử riêng vấn đề NFS, cần **hai máy hoặc hai mount/client thực sự** trỏ đến cùng shared filesystem. Chạy một chương trình writer trên A và reader trên B theo cùng một kịch bản đã ghi thời gian. Nên thu thập:

- NFS/SMB version, server OS và file-system type, mount options và cache policy.
- Thứ tự open → write → force → close ở A; open → read ở B.
- Checksum/version do mỗi bên nhìn thấy, thời điểm nhìn thấy mới và mọi lỗi I/O.
- Kết quả khi B giữ handle mở liên tục so với đóng rồi mở lại.
- Hành vi sau network partition, reconnect, server restart hoặc failover có kiểm soát.

Một lượt kiểm thử luôn thành công cũng **không đủ để suy ra bảo đảm tất cả client luôn thấy ngay lập tức**, nhưng nó là bằng chứng vận hành cho môi trường được thử.

## 9. Network partition, failover và lock bị mất

Khi kết nối bị gián đoạn, client có thể tiếp tục có dữ liệu cũ trong cache. File server hoặc dịch vụ lưu trữ có thể khôi phục phiên, lease hoặc lock theo cách phụ thuộc giao thức. Đặc biệt, không nên suy ra:

- Nếu JVM từng giữ `FileLock` hợp lệ thì sau mọi sự cố mạng server vẫn chắc chắn tôn trọng khóa cũ.
- Nếu server failover thành công thì tất cả dữ liệu gần nhất của mỗi client đã đạt cùng mức durability.
- Nếu thao tác ghi timeout thì “chắc chắn chưa thực hiện”: server có thể đã nhận và áp dụng thay đổi nhưng phản hồi bị mất.
- Nếu retry một thao tác ghi số dư thì sẽ không bị trừ tiền hai lần. Retry có thể tạo lỗi business logic khi thao tác không idempotent.

Nếu dữ liệu rất quan trọng, cần thiết kế **idempotency key**, version/fencing token, bước xác thực lại ownership và quy tắc phục hồi sau lỗi mạng. Các yêu cầu này đôi khi phù hợp hơn với cơ sở dữ liệu/transaction store có hợp đồng nhất quán rõ, thay vì dùng file share làm kho trạng thái đồng thời.

### “Exactly once” không tự đến từ FileChannel

Thử tưởng tượng ứng dụng ghi bản ghi giao dịch rồi mất kết nối trước khi nhận xác nhận. Nó không thể chỉ dựa vào exception của Java để biết server **đã commit hay chưa**. Quy trình đúng thường dùng ID giao dịch ổn định và kiểm tra trạng thái server/manifest trước khi retry; file I/O thô không tự cung cấp exactly-once.

## 10. Sự khác nhau giữa NFS, SMB và các hệ file phân tán khác

**Không đồng nhất các hệ thống sau:**

| Loại hệ thống | Cần xác minh điều gì? |
|---|---|
| NFS (các phiên bản khác nhau) | Close-to-open, attribute/data caching, session/lock behavior, mount options, server commit semantics |
| SMB/CIFS | Oplock/lease, cache invalidation, share modes, durability và failover semantics của phiên bản triển khai |
| Cluster/distributed file system có coherence mạnh | Hợp đồng consistency, cross-client serialization, metadata transactions, failure behavior |
| Object storage được mount như filesystem | Provider/FUSE semantics: rename, locking, append, list consistency, sync/force có thể khác POSIX |
| Filesystem cục bộ | Vẫn cần xét atomicity, lock và durability, nhưng bảo đảm Java cho local force được xác định rõ hơn |

Không nên dùng những mẹo như luôn tắt cache hoặc mở lại file liên tục như một “giải pháp tổng quát”: chúng có thể rất tốn network I/O và vẫn không giải quyết được transaction hoặc partition. Chọn theo contract đã công bố của hệ lưu trữ.

## 11. Những lỗi phổ biến

| Ngộ nhận | Thực tế |
|---|---|
| “A ghi xong thì B lập tức đọc được bản mới” | Cache/client coherence có thể gây độ trễ; cần xác minh filesystem cụ thể. |
| “`Files.size()` luôn là độ dài mới nhất từ server” | Metadata cache và race có thể khiến kết quả không còn đúng tại lúc dùng. |
| “`force(true)` là durable trên mọi network drive” | Java chỉ đưa ra bảo đảm local storage; remote storage không được hứa tương tự. |
| “`rws/rwd` sẽ thay thế distributed transaction” | Chỉ là yêu cầu đồng bộ I/O, không cung cấp transaction/isolation. |
| “`FileLock` luôn khóa bắt buộc trên mọi máy” | Có thể advisory, phụ thuộc triển khai và hỗ trợ server/client. |
| “Lấy FileLock là đủ, bất kể cache stale” | Cần phối hợp cache coherence, mở/đọc đúng phiên bản và xác minh ownership. |
| “Close-to-open có nghĩa mọi read qua handle cũ đều mới” | CTO chủ yếu liên quan ranh giới close/open và chính sách cache cụ thể. |
| “`ATOMIC_MOVE` chắc chắn hỗ trợ và bền vững trên mọi mount” | Có thể không hỗ trợ; atomic namespace không đồng nghĩa durability và cache visibility. |
| “Timestamp và size là version chắc chắn” | Có thể trùng, bị cache, khác độ phân giải hoặc thay đổi giữa kiểm tra và sử dụng. |
| “Một lần đọc đúng từ client B đã chứng minh crash safety” | Đó chỉ là bằng chứng visibility ở một thời điểm, không phải thử nghiệm crash/durability. |
| “Ghi nhiều file có thể commit đồng thời chỉ bằng rename” | Rename từng file không tạo transaction xuyên file. |
| “Network timeout chắc chắn ghi thất bại” | Có thể server đã xử lý và chỉ mất phản hồi; cần idempotency/verification. |

## 12. Checklist thiết kế ứng dụng dùng shared file

1. **Xác định consistency contract:** ứng dụng chấp nhận eventual/close-to-open hay thực sự cần linearizable read/write?
2. **Xác định durability contract:** server xác nhận đến cache, ổ lưu trữ hay replica/quorum? Nếu mất server có phục hồi không?
3. **Xác định owner/writer policy:** một writer duy nhất hay nhiều writer cạnh tranh? File locks có hoạt động xuyên toàn bộ client không?
4. **Quản lý version:** dùng version ID/checksum/manifest cho dữ liệu quan trọng; reader phát hiện stale/incomplete data.
5. **Publish an toàn:** cân nhắc file tạm + atomic move khi được hỗ trợ, cùng quy tắc force/directory durability của nền tảng.
6. **Xử lý lỗi có thể đã commit:** retry có giới hạn và idempotent; không tự suy ra trạng thái từ timeout.
7. **Thử nghiệm môi trường thật:** hai client riêng, mount options xác thực, fault injection/network partition/server restart khi có điều kiện.
8. **Cân nhắc công nghệ khác:** nếu cần transaction mạnh cho nhiều writer, một database hoặc coordination service có contract rõ thường dễ đảm bảo hơn raw shared file.

### Câu hỏi tự kiểm tra

1. A ghi file rồi gọi `FileChannel.force(true)` trên NFS. Theo Java 17, có thể khẳng định dữ liệu tồn tại sau mọi server crash không? **Không.** Bảo đảm local force không áp dụng tổng quát cho remote storage.
2. B giữ file mở lâu và đọc lại sau A cập nhật. Có chắc thấy bản mới nhờ CTO? **Không chắc.** Cần xét chính sách cache, close/open và filesystem.
3. `Files.exists()` trả `true` rồi ứng dụng mở file. File có chắc còn nguyên? **Không.** Có race giữa kiểm tra và dùng.
4. Có `FileLock` thì mọi tiến trình trên mọi nền tảng đều không thể sửa file? **Không.** Lock có thể advisory và hỗ trợ khác nhau.
5. `Files.move(temp,target,ATOMIC_MOVE)` có nghĩa nội dung bền vững sau mất điện? **Không.** Atomic move và durability là hai thuộc tính độc lập.
6. `force()` của `MappedByteBuffer` có thể được thay tổng quát bằng `FileChannel.force(true)`? **Không**, đặc tả FileChannel không bảo đảm force dirty mapped pages.
7. Nếu giao dịch gửi tới server báo timeout, có chắc chưa ghi? **Không.** Có thể chỉ mất phản hồi; cần kiểm tra version/ID trước khi retry.
8. Cần đảm bảo reader không dùng dữ liệu nhiều file bị trộn phiên bản. Một hướng thiết kế? **Versioned immutable files + manifest + checksum + publish theo protocol đã kiểm nghiệm.**
9. `noac` trên Linux NFS có đồng nghĩa tắt toàn bộ data cache và mọi race? **Không.**
10. Khi nào nên cân nhắc database/coordination service? **Khi cần transaction, ordering và isolation mạnh với nhiều writer hoặc nhiều failure domain.**

## 13. Tài liệu chính thức

- [Java SE 17 — FileChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html): `force(boolean)`, `lock(...)`, đọc/ghi, phạm vi bảo đảm local/remote.
- [Java SE 17 — FileLock](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileLock.html): tính hệ thống, advisory/mandatory, shared/exclusive, đặc thù network filesystem.
- [Java SE 17 — RandomAccessFile](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/RandomAccessFile.html): `"rw"`, `"rwd"`, `"rws"` và đồng bộ trên thiết bị cục bộ.
- [Java SE 17 — MappedByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/MappedByteBuffer.html): `force()` và giới hạn file trên non-local storage.
- [Java SE 17 — Files.move](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#move(java.nio.file.Path,java.nio.file.Path,java.nio.file.CopyOption...)): `ATOMIC_MOVE`, trường hợp không hỗ trợ và tính chất phụ thuộc provider.
- [Linux man-pages — nfs(5), Data and Metadata Coherence](https://man7.org/linux/man-pages/man5/nfs.5.html): cache, close-to-open, attribute cache và các mount options cụ thể của Linux NFS.


</details>

- [Quay lại đầu trang](#back-to-top)
