<a id="back-to-top"></a>

# Theo dõi thay đổi

## Menu
- [WatchService, Watchable và đăng ký thư mục](#watch-service-001)
- [ENTRY_CREATE, ENTRY_DELETE và ENTRY_MODIFY](#watch-service-002)
- [WatchKey, pollEvents và đường dẫn tương đối của sự kiện](#watch-service-003)
- [take, poll và vòng xử lý sự kiện](#watch-service-004)
- [reset key và trạng thái đăng ký không còn hợp lệ](#watch-service-005)
- [OVERFLOW và nhu cầu quét lại trạng thái](#watch-service-006)
- [Đăng ký đệ quy và thư mục được tạo mới](#watch-service-007)
- [Sự kiện lặp, gộp và thời điểm nội dung tệp chưa hoàn tất](#watch-service-008)
- [Giới hạn phụ thuộc nền tảng và network file system](#watch-service-009)
- [Đóng WatchService và dừng luồng chờ](#watch-service-010)

## <a id="watch-service-001">WatchService, Watchable và đăng ký thư mục</a>

<details>
<summary>Xem chi tiết</summary>

# WatchService, Watchable và đăng ký thư mục trong Java NIO.2

## 1. Bài toán: biết khi nào một thư mục thay đổi

Một ứng dụng có thể cần cập nhật danh sách file sau khi người dùng tải ảnh lên, nhận biết file cấu hình mới được đặt vào thư mục, hoặc phản ứng khi file bị xóa. Việc liên tục gọi `Files.list()` hoặc `Files.exists()` để quét toàn bộ thư mục có thể gây tốn I/O và khó chọn chu kỳ kiểm tra hợp lý.

Từ Java 7, **Java NIO.2** cung cấp cơ chế theo dõi thay đổi thông qua `java.nio.file.WatchService`. Ứng dụng **đăng ký một thư mục** vào service và yêu cầu nhận các loại sự kiện; khi hệ thống tệp phát hiện thay đổi, service đưa một **`WatchKey`** vào hàng đợi để ứng dụng lấy ra và xử lý.

Một sơ đồ đơn giản:

```text
Path đại diện thư mục
       |
       | register(watchService, ENTRY_CREATE, ...)
       v
     WatchKey --------------------------+
       |                                |
       | tệp được thêm/xóa/sửa          | theo dõi vòng đời đăng ký
       v                                |
   WatchService                        |
       |                                |
       | take() / poll()                 |
       v                                |
  WatchKey được signal ----------------+
       |
       | pollEvents()
       v
 WatchEvent<?> gồm kind/count/context
       |
       | xử lý xong -> key.reset()
       v
 Tiếp tục chờ sự kiện mới
```

Ba đối tượng giữ ba trách nhiệm:

- **`WatchService`**: nguồn thông báo và hàng đợi các watch key đã có sự kiện.
- **`Watchable`**: đối tượng *có thể đăng ký* với watch service; trong học phần theo dõi thư mục, `Path` là kiểu được dùng.
- **`WatchKey`**: token đại diện cho **một lần đăng ký** và lưu những sự kiện đang chờ xử lý cho đăng ký đó.

**Lưu ý phạm vi:** đây là cơ chế *thông báo thay đổi trên đối tượng hệ thống tệp*, không phải cơ chế truyền nội dung file và không bảo đảm ghi nhận một bản ghi sự kiện cho mỗi thao tác vật lý trên mọi hệ điều hành.

## 2. Khái niệm và API nền tảng

Các lớp/interface liên quan ở package `java.nio.file`:

| Thành phần | Vai trò | Ví dụ API |
| --- | --- | --- |
| `WatchService` | Nhận key đang có tín hiệu; quản lý vòng đời watcher | `poll()`, `poll(timeout, unit)`, `take()`, `close()` |
| `Watchable` | Cho phép đăng ký theo dõi thay đổi | `register(watcher, events...)` |
| `Path` | Đại diện đường dẫn; triển khai `Watchable` | `dir.register(...)` |
| `WatchKey` | Quản lý một đăng ký, lấy event và tái kích hoạt | `pollEvents()`, `reset()`, `cancel()`, `isValid()`, `watchable()` |
| `WatchEvent<T>` | Một sự kiện được phát hiện | `kind()`, `count()`, `context()` |
| `StandardWatchEventKinds` | Các loại sự kiện chuẩn | `ENTRY_CREATE`, `ENTRY_DELETE`, `ENTRY_MODIFY`, `OVERFLOW` |
| `FileSystem` | Tạo watch service ứng với file system | `newWatchService()` |

Quy trình khởi tạo thông dụng:

```java
try (WatchService watcher =
         FileSystems.getDefault().newWatchService()) {
    Path dir = Path.of("watched-directory");
    WatchKey key = dir.register(
        watcher,
        StandardWatchEventKinds.ENTRY_CREATE,
        StandardWatchEventKinds.ENTRY_DELETE,
        StandardWatchEventKinds.ENTRY_MODIFY
    );

    // Lấy key từ watcher và xử lý WatchEvent<?>.
}
```

Đây là **đoạn minh họa**, chưa phải chương trình hoàn chỉnh: thư mục phải tồn tại, chương trình cần import, xử lý ngoại lệ và vòng lặp theo dõi. Nên dùng `try-with-resources` vì `WatchService` có thể đóng và nắm tài nguyên hệ điều hành.

### `Watchable` khác `WatchService`

`Watchable` chỉ định khả năng **đăng ký**. `Path` triển khai interface này, nên ta gọi `dir.register(watcher, ...)` trực tiếp. Ngược lại, `WatchService` **không chứa một phương thức `register(Path)` thông thường**; đối tượng được theo dõi tự đăng ký với watcher của nó.

Khai báo chuẩn của `Watchable` gồm hai dạng:

```java
WatchKey register(
        WatchService watcher,
        WatchEvent.Kind<?>... events) throws IOException;

WatchKey register(
        WatchService watcher,
        WatchEvent.Kind<?>[] events,
        WatchEvent.Modifier... modifiers) throws IOException;
```

Đoạn trên là các chữ ký API rút gọn. Với bài học căn bản, dùng phiên bản varargs các `StandardWatchEventKinds`, không cần modifier. Java SE 17 không định nghĩa **modifier chuẩn**; nếu có modifier của hệ thống tệp/JDK cụ thể, phải xét khả năng tương thích riêng.

## 3. Phải đăng ký **thư mục đã tồn tại**, không phải từng file

Trong Java SE 17 với cách dùng chuẩn của `Path.register`, đường dẫn đăng ký là **một thư mục hiện tồn tại**; các sự kiện đại diện cho thay đổi đối với **các entry của thư mục đó**.

Ví dụ cây thư mục:

```text
project/
├── config.json
├── a.txt
└── nested/
    └── x.txt
```

Nếu đăng ký `project/`:

- Tạo `project/new.txt` có thể sinh `ENTRY_CREATE`.
- Xóa `project/a.txt` có thể sinh `ENTRY_DELETE`.
- Sửa nội dung `project/config.json` có thể sinh `ENTRY_MODIFY`.
- Tạo `project/nested/new.txt` **không mặc nhiên được quan sát** vì `WatchService` không tự theo dõi toàn bộ cây thư mục.

**Đăng ký không đệ quy theo mặc định.** Muốn giám sát các thư mục con, cần đăng ký riêng từng thư mục hiện tại, đồng thời bổ sung đăng ký khi thư mục con mới xuất hiện. Những thay đổi xảy ra **giữa lúc phát hiện thư mục mới và lúc đăng ký nó** có thể bị bỏ lỡ; vì thế giải pháp mạnh phải có bước rà soát/bù trạng thái nếu không được mất thay đổi.

Nếu truyền đường dẫn là file thay vì thư mục, thao tác đăng ký không phù hợp với hợp đồng chuẩn và có thể gặp `NotDirectoryException` hoặc `IOException` tùy hệ thống. Thư mục không tồn tại cũng không thể được giám sát bằng cách “đăng ký trước rồi đợi nó được tạo” trên chính đường dẫn ấy. Hãy đăng ký **thư mục cha đang tồn tại** để theo dõi việc tạo entry con.

Ví dụ:

```java
Path parent = Path.of("/data").toAbsolutePath();
Path watchedChild = parent.resolve("incoming");

// Đăng ký parent trước, rồi nhận ENTRY_CREATE cho "incoming"
// nếu nó được tạo trực tiếp bên trong parent.
```

Đoạn này chỉ minh họa đường dẫn; không giả định thư mục `/data` tồn tại ở mọi môi trường và không phải chương trình chạy độc lập.

## 4. Ba loại sự kiện chuẩn cho directory entries

Các constant được định nghĩa trong `StandardWatchEventKinds`:

| Event kind | Khi nào thường được báo? | `context()` |
| --- | --- | --- |
| `ENTRY_CREATE` | Entry được tạo hoặc chuyển **vào** thư mục đang xem | `Path` tương đối chỉ entry vừa xuất hiện |
| `ENTRY_DELETE` | Entry bị xóa hoặc chuyển **ra khỏi** thư mục đang xem | `Path` tương đối chỉ entry đã biến mất |
| `ENTRY_MODIFY` | Entry trong thư mục được sửa | `Path` tương đối chỉ entry được sửa |
| `OVERFLOW` | Sự kiện có thể bị mất hoặc bị loại bỏ do vượt giới hạn | Thường `null`; không được ép kiểu như event của entry |

**Sự kiện create/modify/delete là tín hiệu, không phải nhật ký tuyệt đối chính xác.** Hệ điều hành có thể gộp nhiều lần modify thành một event, phát sinh nhiều event khi ứng dụng ghi một file, hoặc cung cấp hành vi khác nhau về thứ tự. Ví dụ editor thường ghi vào file tạm rồi rename để thay file gốc; bạn có thể thấy create/delete thay vì một modify đơn nhất.

`OVERFLOW` **được đăng ký tự động**, không cần và không nên dựa vào việc liệt kê nó trong mảng `events` để nhận thông báo.

Với `ENTRY_CREATE`, `ENTRY_DELETE` và `ENTRY_MODIFY` khi theo dõi thư mục, `event.context()` là **đường dẫn tương đối so với thư mục đã đăng ký**. Nó thường chỉ là tên entry, ví dụ `notes.txt`; không mặc định là đường dẫn tuyệt đối.

Cách xác định đường dẫn mục tiêu:

```java
Path watchedDir = (Path) key.watchable();

for (WatchEvent<?> event : key.pollEvents()) {
    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
        // Không có context là tên file đáng tin trong event này.
        continue;
    }

    Path relative = (Path) event.context();
    Path affected = watchedDir.resolve(relative);

    System.out.println("Event: " + event.kind()
            + ", path: " + affected);
}
```

Đây là đoạn minh họa cần import và biến `key` hợp lệ. Dạng ép kiểu `(Path) event.context()` là phù hợp với **các entry events trên Path đã đăng ký theo dõi thư mục**; không nên áp dụng tùy tiện cho mọi implementation-specific kind hoặc `OVERFLOW`.

## 5. Đăng ký thư mục qua `Path.register`

Mẫu thường gặp:

```java
Path directory = Path.of("watch-me").toAbsolutePath();
try (WatchService service =
         directory.getFileSystem().newWatchService()) {

    WatchKey key = directory.register(
        service,
        StandardWatchEventKinds.ENTRY_CREATE,
        StandardWatchEventKinds.ENTRY_DELETE,
        StandardWatchEventKinds.ENTRY_MODIFY);

    // Chờ thông báo và gọi key.reset().
}
```

Ở đây `getFileSystem().newWatchService()` đảm bảo watcher được tạo từ **cùng FileSystem** với path. Với đường dẫn trên default filesystem, `FileSystems.getDefault().newWatchService()` thường tương đương. Không trộn `Path` của provider này với `WatchService` của provider khác rồi giả định chúng tương thích.

**Cần kiểm tra sớm:**

- `Files.isDirectory(directory)` trả `true` tại thời điểm kiểm tra, nhưng thư mục vẫn có thể bị xóa/chuyển trong lúc đăng ký.
- Quyền truy cập của tiến trình phải cho phép theo dõi thư mục.
- FileSystem/provider có thể **không hỗ trợ watch service** hoặc một loại sự kiện/modifier nhất định.
- `register` có thể ném `IOException`, `UnsupportedOperationException`, `IllegalArgumentException` hoặc `ClosedWatchServiceException` tùy tình huống.

### Đăng ký lại đối tượng với cùng WatchService

Theo `Watchable.register`, nếu một đối tượng đã được đăng ký với **cùng WatchService**, gọi `register` lần nữa sẽ lấy lại key cho đăng ký đó đồng thời **cập nhật tập event kinds/modifiers** được theo dõi. Những sự kiện đang chờ không tự bị bỏ chỉ vì thay danh sách quan tâm.

Ví dụ ý tưởng:

```java
WatchKey first = dir.register(service,
        StandardWatchEventKinds.ENTRY_CREATE);

WatchKey second = dir.register(service,
        StandardWatchEventKinds.ENTRY_CREATE,
        StandardWatchEventKinds.ENTRY_DELETE);

// Với cùng Path và service đang có đăng ký hợp lệ,
// đây là key đại diện cùng đăng ký được cập nhật.
```

Không sử dụng cách gọi lại `register` như một cách bảo đảm “xóa sạch queue cũ”, và không giả định `cancel()` một key vẫn được dùng để tiếp tục theo dõi; key đã hủy sẽ trở thành invalid.

## 6. `WatchKey`: vòng đời ready, signalled và reset

`WatchKey` là chìa khóa hiểu cơ chế:

```text
          đăng ký thành công
                 |
                 v
              READY
                 |
          event được phát hiện
                 |
                 v
             SIGNALLED ------> được đưa vào queue của WatchService
                 |                        |
                 |                        v
                 |                    take()/poll()
                 |                        |
                 |                        v
                 |                 key.pollEvents()
                 |                        |
                 |                        v
                 +<------ key.reset() sau khi xử lý
                             |
                      +------+-------+
                      |              |
               key còn valid     key invalid
                      |              |
              trở lại READY       hủy/cleanup
            hoặc requeue ngay
            nếu còn event chờ
```

**Khi một key đã signalled**, các event mới có thể tiếp tục được tích lũy cho key, nhưng key không được đưa lặp vào hàng đợi của service chỉ vì mỗi event mới xuất hiện. Sau khi xử lý xong event, ứng dụng phải gọi `key.reset()` để cho phép phát tín hiệu/đưa vào hàng đợi ở lần sau.

Các phương thức quan trọng:

| API | Ý nghĩa |
| --- | --- |
| `key.pollEvents()` | Lấy danh sách event đã tích lũy và loại chúng khỏi danh sách pending |
| `key.reset()` | Hoàn tất lượt xử lý, đưa key về trạng thái có thể được signal tiếp; trả boolean |
| `key.isValid()` | Kiểm tra key còn đại diện cho đăng ký hợp lệ không |
| `key.cancel()` | Hủy đăng ký; key trở thành invalid |
| `key.watchable()` | Trả lại đối tượng đã đăng ký, ví dụ `Path` của thư mục |

`key.reset()` trả **`true`** nếu key còn hợp lệ và đã được reset; **`false`** nếu key không thể reset do không còn hợp lệ. Nếu sau khi reset còn event pending, key có thể **được đưa lại vào queue ngay**, chứ không nhất thiết phải chờ một thay đổi vật lý khác.

Một key có thể tự bị hủy nếu thư mục đang theo dõi bị xóa hoặc file system không còn truy cập được. Vì vậy, nếu `reset()` trả `false`, ứng dụng cần xóa đăng ký khỏi cấu trúc quản lý, xử lý cleanup hoặc khôi phục theo chính sách. Không được lặp vô hạn chỉ vì đã gọi `reset()` một lần.

### Vì sao reset quá sớm cũng nguy hiểm?

Nếu nhiều thread cùng lấy key từ một watch service, cần bảo đảm chỉ một consumer đang xử lý event cho cùng watchable ở một thời điểm. Gọi `reset()` **trước khi** xử lý xong có thể khiến key được signal và đưa vào queue tiếp, dẫn đến hai consumer xử lý đồng thời cùng thư mục và gây race condition. Dù WatchService cho phép nhiều consumer dùng chung, **thứ tự xử lý cùng một key** vẫn cần được điều phối rõ ràng.

## 7. `take()` và hai dạng `poll()`

`WatchService` cung cấp ba cách lấy key:

| Phương thức | Có chờ? | Kết quả khi chưa có key |
| --- | --- | --- |
| `watcher.poll()` | Không | Trả `null` ngay |
| `watcher.poll(timeout, TimeUnit.SECONDS)` | Có, tối đa timeout | Trả `null` nếu hết thời gian |
| `watcher.take()` | Có thể chờ đến khi có key | Không dùng `null` để báo timeout, vì không có timeout |

Mẫu xử lý chính:

```java
while (running) {
    WatchKey key = watcher.take();

    for (WatchEvent<?> event : key.pollEvents()) {
        // Xử lý event.
    }

    boolean valid = key.reset();
    if (!valid) {
        // Xóa đăng ký không còn hợp lệ khỏi ứng dụng.
    }
}
```

Đây là **pseudocode gần Java**; `running`, `watcher` và handler cần được định nghĩa. `take()` có thể ném `InterruptedException` nếu luồng bị ngắt trong lúc chờ, và `ClosedWatchServiceException` nếu service đã đóng.

Nếu chương trình cần kiểm tra điều kiện dừng định kỳ, `poll(timeout, unit)` thường dễ tổ chức hơn so với `take()` chờ vô hạn. Nếu muốn yêu cầu dừng từ thread khác, đóng WatchService sẽ đánh thức thread đang chờ và khiến lời gọi ném `ClosedWatchServiceException`; cần xử lý ngoại lệ đó như một phần của vòng đời shutdown, không tự mặc định đó là lỗi không thể phục hồi.

**Đừng busy-poll:**

```java
while (watcher.poll() == null) {
    // Vòng lặp rỗng có thể đốt CPU!
}
```

Dùng `take()` hoặc `poll(timeout, unit)` khi phù hợp.

## 8. Ví dụ Java 17+ chạy độc lập: đăng ký, kiểm tra và hủy WatchKey

Để có stdout **ổn định**, ví dụ đầu tập trung vào vòng đời đăng ký, không dựa vào thời điểm hệ điều hành phát sự kiện. Chương trình tạo một thư mục tạm, tạo watch service từ cùng file system, đăng ký ba event kind, kiểm tra key hợp lệ và hủy key có chủ đích.

**Tệp `WatchRegistrationDemo.java`:**

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

public class WatchRegistrationDemo {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("watch-demo-");

        try {
            try (WatchService service =
                     directory.getFileSystem().newWatchService()) {

                WatchKey key = directory.register(
                        service,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_MODIFY);

                System.out.println(
                        "Thu muc ton tai: " + Files.isDirectory(directory));
                System.out.println("Key hop le: " + key.isValid());
                System.out.println(
                        "Watchable trung Path: "
                        + key.watchable().equals(directory));

                key.cancel();
                System.out.println(
                        "Key sau cancel: " + key.isValid());
            }
        } finally {
            Files.deleteIfExists(directory);
        }
    }
}
```

Biên dịch/chạy với **JDK 17+** trên filesystem hỗ trợ watch service:

```bash
javac WatchRegistrationDemo.java
java WatchRegistrationDemo
```

**Stdout nếu tạo service, đăng ký và dọn dẹp thành công:**

```text
Thu muc ton tai: true
Key hop le: true
Watchable trung Path: true
Key sau cancel: false
```

Giải thích:

1. `Files.createTempDirectory` tạo thư mục thật, tránh lỗi đăng ký đường dẫn không tồn tại.
2. `directory.getFileSystem().newWatchService()` tạo watcher tương ứng với FileSystem của thư mục.
3. `directory.register(...)` trả về `WatchKey`; đây là đăng ký cho **thư mục**, không phải cho một file con.
4. `key.watchable().equals(directory)` kiểm chứng key tham chiếu tới đối tượng đã đăng ký.
5. `key.cancel()` làm key trở thành invalid, không cần có một event nào trước đó.
6. `try-with-resources` đóng watch service, và `finally` xóa thư mục tạm. Nếu nền tảng không hỗ trợ watcher hoặc có lỗi I/O, chương trình có thể ném ngoại lệ thay vì xuất stdout như trên.

**`cancel()` không phải `reset()`.** `cancel` kết thúc đăng ký; `reset` dùng sau mỗi lượt xử lý khi vẫn muốn giám sát thư mục.

## 9. Ví dụ Java 17+: theo dõi sự kiện của thư mục

Ví dụ sau tự tạo một file bên trong thư mục đã đăng ký. Nó dùng `poll(timeout)` để tránh chờ vô hạn, in event kind và entry name thực tế, xử lý `OVERFLOW`, gọi `reset()` sau khi đọc event rồi đóng mọi tài nguyên.

**Tệp `WatchEventsDemo.java`:**

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class WatchEventsDemo {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("watched-");
        Path file = directory.resolve("sample.txt");

        try {
            try (WatchService service =
                     directory.getFileSystem().newWatchService()) {

                directory.register(
                        service,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);

                Files.writeString(file, "Hello WatchService");
                WatchKey key = service.poll(3, TimeUnit.SECONDS);

                if (key == null) {
                    System.out.println("Khong co key trong 3 giay");
                } else {
                    List<WatchEvent<?>> events = key.pollEvents();

                    for (WatchEvent<?> event : events) {
                        if (event.kind() ==
                                StandardWatchEventKinds.OVERFLOW) {
                            System.out.println("OVERFLOW: can quet lai");
                            continue;
                        }

                        Path entry = (Path) event.context();
                        Path absolute = directory.resolve(entry);
                        System.out.println(event.kind().name()
                                + ": " + absolute.getFileName()
                                + " (count=" + event.count() + ")");
                    }

                    boolean valid = key.reset();
                    System.out.println("Key con hop le: " + valid);
                }
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }
}
```

Biên dịch/chạy:

```bash
javac WatchEventsDemo.java
java WatchEventsDemo
```

**Một kết quả có thể quan sát, không phải stdout được bảo đảm:**

```text
ENTRY_CREATE: sample.txt (count=1)
ENTRY_MODIFY: sample.txt (count=1)
Key con hop le: true
```

**Vì sao không được tuyên bố stdout chính xác?** WatchService nhận tín hiệu từ cơ chế filesystem có tính phụ thuộc nền tảng. Một hệ thống có thể phát thêm `ENTRY_MODIFY`, gộp sự kiện, hoặc chưa đưa key vào queue trong thời gian 3 giây. Thứ tự/số lượng/count của event có thể khác nhau. Do đó chương trình **in sự kiện thực tế**, không giả tạo thứ tự bắt buộc.

Một giới hạn khác: ví dụ chỉ gọi `poll(...)` một lần, đủ để minh họa cách lấy một key. Nếu sau `reset()` còn sự kiện chờ hoặc có event mới, một ứng dụng monitoring thực phải **tiếp tục vòng lặp** thay vì kết luận rằng một lần poll đã thu hết mọi thay đổi. Việc ghi bằng `Files.writeString` cũng có thể kích hoạt event modify ngoài create; đây không phải lỗi.

## 10. `WatchEvent.context()`, `count()` và cách xác định đường dẫn

Mỗi `WatchEvent<?>` có:

- `kind()`: loại sự kiện.
- `count()`: số lần sự kiện được tích lũy/gộp (không phải số byte hoặc kích thước file).
- `context()`: dữ liệu phụ thuộc kind, với entry event chuẩn thường là `Path` tương đối của entry.

Ví dụ:

```text
Thư mục đăng ký: /srv/inbox
event.kind(): ENTRY_CREATE
event.context(): daily.csv
event.count(): 1

Đường dẫn cần xử lý:
    /srv/inbox/daily.csv
```

Vì context là đường dẫn tương đối, dùng:

```java
Path directory = (Path) key.watchable();
Path relative = (Path) event.context();
Path fullPath = directory.resolve(relative);
```

Không dùng `Path.of(event.context().toString())` như thể nó luôn là absolute path. Nếu thư mục đăng ký được đại diện bằng đường dẫn tương đối, `resolve` cũng có thể cho kết quả tương đối; gọi `toAbsolutePath()` khi thực sự cần đường dẫn tuyệt đối, và cân nhắc symlink/canonicalization trong ứng dụng bảo mật.

Nếu một ứng dụng xử lý event bằng cách mở hoặc xóa file, hãy kiểm tra trạng thái **tại thời điểm sử dụng**: entry có thể đã bị xóa, di chuyển hoặc thay thế trong khoảng thời gian giữa lúc event được phát hiện và lúc handler thao tác (*time-of-check to time-of-use*). Sự kiện không cấp quyền truy cập, không giữ lock và không giữ một snapshot dữ liệu của file.

## 11. `OVERFLOW`: không thể coi thông báo là danh sách thay đổi hoàn chỉnh

WatchService có thể có giới hạn số event được tích lũy. Khi triển khai nhận biết rằng event đã bị loại bỏ, nó cung cấp `OVERFLOW`. Bạn có thể gặp `OVERFLOW` dù chỉ đăng ký create/delete/modify.

Cách xử lý đúng:

```java
if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
    // Đánh dấu trạng thái quan sát có thể thiếu.
    // Rà soát lại các entry của thư mục hoặc tái đồng bộ chỉ mục.
    continue;
}
```

Ý nghĩa không phải “thử đọc lại chính event đó” vì nội dung chi tiết đã mất. Ứng dụng nên **quét trạng thái hiện tại** của những thư mục cần theo dõi và đối chiếu với trạng thái/metadata đã ghi nhận để khôi phục tính nhất quán.

Thí dụ service dùng để duy trì cache file:

1. Khi bắt đầu, lấy danh sách file đang có.
2. Trong lúc chạy, áp dụng create/delete/modify để cập nhật cache.
3. Khi có `OVERFLOW`, đánh dấu cache có thể sai.
4. Quét lại thư mục và xây lại trạng thái cache.
5. Tiếp tục nhận event, có cơ chế phối hợp với các thay đổi phát sinh trong lúc quét.

**Không thể biến WatchService thành audit log không mất dữ liệu** chỉ bằng việc tăng tốc vòng đọc event. Nếu cần biết chắc *mọi* thay đổi (không bỏ sót) cho nghiệp vụ quan trọng, cần giao thức phát sự kiện/transaction log riêng, đối soát, hoặc nguồn dữ liệu có tính bền vững phù hợp.

## 12. Giới hạn về thời gian và nội dung file được sửa

`ENTRY_MODIFY` **không có nghĩa file đã ghi xong**. Ứng dụng khác có thể còn mở và tiếp tục ghi file trong khi bạn nhận được event. Vì thế một file watcher dùng để nhập dữ liệu cần thiết kế dấu hiệu “file đã sẵn sàng”, ví dụ:

- Bên sản xuất ghi vào một tên file tạm, đóng file, rồi đổi tên vào thư mục nhận nếu filesystem và chiến lược rename phù hợp.
- Bên nhận chờ một marker/manifest “complete” do bên sản xuất phát sau cùng.
- Ứng dụng xác minh tính toàn vẹn hoặc độ dài/metadata ổn định trong một khoảng phù hợp, đồng thời chấp nhận đây chỉ là **heuristic**, không phải bảo đảm tuyệt đối.

Nếu file vừa có `ENTRY_CREATE` rồi `ENTRY_DELETE` trước lúc handler xử lý, việc `Files.readAllBytes(fullPath)` có thể ném `NoSuchFileException`. Handler cần xem event như “gợi ý kiểm tra lại trạng thái” thay vì mệnh lệnh bắt buộc mở file luôn thành công.

Trong hệ thống nhiều writer, event của cùng một entry có thể xuất hiện liên tiếp. Cần **debounce/coalesce ở tầng ứng dụng** nếu xử lý quá nặng, nhưng không được đơn giản bỏ sự kiện theo thời gian rồi khẳng định không mất thay đổi nghiệp vụ.

## 13. Vòng lặp giám sát dài hạn đúng cách

Một khung event loop dùng `WatchService`:

```java
try (WatchService watcher =
         directory.getFileSystem().newWatchService()) {

    directory.register(
        watcher,
        StandardWatchEventKinds.ENTRY_CREATE,
        StandardWatchEventKinds.ENTRY_MODIFY,
        StandardWatchEventKinds.ENTRY_DELETE);

    while (!Thread.currentThread().isInterrupted()) {
        WatchKey key = watcher.take();

        try {
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() ==
                        StandardWatchEventKinds.OVERFLOW) {
                    reconcileDirectory((Path) key.watchable());
                    continue;
                }

                Path root = (Path) key.watchable();
                Path entry = (Path) event.context();
                processEvent(event.kind(), root.resolve(entry));
            }
        } finally {
            boolean valid = key.reset();
            if (!valid) {
                break;
            }
        }
    }
}
```

**Đây là khung minh họa, không phải chương trình Java độc lập:** cần khai báo `directory`, `reconcileDirectory` và `processEvent`, import các API và xử lý `IOException`/`InterruptedException`/runtime exceptions. Dùng `finally` cho `reset()` bảo đảm vòng đời key được xử lý ngay cả khi handler ném lỗi, **nhưng không tự làm cho lỗi nghiệp vụ an toàn**; tùy ứng dụng, có thể cần đánh dấu cần đồng bộ lại nếu handler thất bại trước khi xử lý đủ event.

Nếu giám sát **nhiều thư mục**, không thể `break` toàn bộ event loop chỉ vì một key invalid nếu còn các key khác hợp lệ. Thay vào đó quản lý registry và chỉ kết thúc khi không còn mục tiêu cần theo dõi hoặc có tín hiệu shutdown.

### Registry của nhiều thư mục

Một cách tổ chức:

```java
Map<WatchKey, Path> roots = new HashMap<>();

WatchKey keyA = dirA.register(watcher, ENTRY_CREATE, ENTRY_DELETE);
roots.put(keyA, dirA);

WatchKey keyB = dirB.register(watcher, ENTRY_CREATE, ENTRY_DELETE);
roots.put(keyB, dirB);
```

Đây là đoạn minh họa với biến `dirA`, `dirB` và imports thích hợp; không phải chương trình chạy được độc lập. Lần `take()` kế tiếp có thể trả **key của bất kỳ thư mục nào có event**. Dùng `roots.get(key)` hoặc `(Path) key.watchable()` để xác định nguồn. Nếu `reset()` trả false, xóa key khỏi map. Không đánh đồng `watcher` với một thư mục duy nhất khi đã đăng ký nhiều nơi.

## 14. Theo dõi thư mục con: làm rõ tính **không đệ quy**

Giả sử cần theo dõi `root/sub1/sub2`. `root.register(watcher, ...)` chỉ cho bạn những thay đổi của entry **trực tiếp** dưới `root`. Để quan sát trong `sub1` và `sub2`, phải đăng ký chính chúng.

Quy trình cơ bản:

1. Duyệt cây thư mục hiện tại bằng `Files.walkFileTree` hoặc API phù hợp.
2. Đăng ký từng thư mục đang tồn tại, lưu `WatchKey → Path`.
3. Khi nhận `ENTRY_CREATE` của một thư mục con mới, kiểm tra `Files.isDirectory(path)` tại thời điểm xử lý và đăng ký thư mục ấy.
4. Có thể rà soát thêm nội dung bên dưới thư mục vừa xuất hiện để bù khoảng thời gian xảy ra trước khi đăng ký.
5. Khi key không còn hợp lệ, xóa registry và quyết định có cần thử đăng ký lại khi thư mục xuất hiện ở vị trí mới.

Cân nhắc thêm:

- Quy tắc theo symlink: có thể tạo vòng lặp hoặc đi ra ngoài thư mục gốc. Dùng chính sách follow/no-follow rõ ràng.
- Giới hạn số lượng watcher và tài nguyên OS khi cây có hàng trăm nghìn thư mục.
- Race condition khi thư mục bị xóa giữa `isDirectory` và `register`.
- Chuyển thư mục sang tên khác: không có bảo đảm mọi event tương ứng rename được biểu diễn theo một chuẩn duy nhất trên mọi platform.
- File system ảo, mạng hoặc container volume có thể có hành vi phát event khác local disk.

Không gọi một chương trình giám sát mỗi thư mục cha là “recursive watcher” nếu thực tế chưa đăng ký các cấp con.

## 15. Khác biệt với `Selector` và I/O mạng

Tuy `WatchService` và `Selector` đều có tác vụ “chờ có việc”:

| Khía cạnh | WatchService | Selector |
| --- | --- | --- |
| Đối tượng theo dõi | `Watchable`, thường là `Path` thư mục | `SelectableChannel`, thường là socket/pipe |
| Token | `WatchKey` | `SelectionKey` |
| Nguồn dữ liệu | Sự kiện thay đổi entry hệ thống tệp | Readiness của channel cho I/O |
| Cách đợi | `take()`, `poll()` | `select()`, `selectNow()` |
| Sau xử lý | **`WatchKey.reset()`** | Lấy/xóa key khỏi `selectedKeys()` theo event loop |
| Các kind/ops | `ENTRY_CREATE`/`DELETE`/`MODIFY`/`OVERFLOW` | `OP_ACCEPT`/`CONNECT`/`READ`/`WRITE` |

Một lỗi dễ mắc là nghĩ `WatchKey.reset()` tương đương `iterator.remove()` trên `Selector.selectedKeys()`. Chúng thuộc **hai API khác nhau**, vòng đời và nội dung sự kiện khác nhau. `WatchService` trả về key có danh sách `WatchEvent` đang chờ; `Selector` trả về key cho biết **loại I/O sẵn sàng**, không phải danh sách thay đổi file.

Cũng không dùng `FileChannel.configureBlocking(false)` để tạo watcher cho thay đổi thư mục. `FileChannel` không phải selectable socket channel; `WatchService` mới là API dành cho sự kiện directory entries.

## 16. Giới hạn phụ thuộc hệ điều hành và filesystem

Đặc tả Java cho phép triển khai WatchService dùng cơ chế thông báo native của hệ điều hành hoặc phương án khác như polling khi không có cơ chế native phù hợp. Vì vậy:

- **Thời điểm thông báo** có thể khác thời điểm thay đổi xảy ra.
- **Số lượng và thứ tự** event có thể khác nhau giữa Linux, Windows, macOS, file system provider hoặc JDK vendor.
- **Coalescing** có thể gộp các event cùng loại; `event.count()` phản ánh số lần tích lũy theo hợp đồng, không là bằng chứng của đầy đủ mọi thao tác.
- **OVERFLOW** báo khả năng sự kiện đã mất; ngay cả khi không nhận overflow, vẫn nên thiết kế có đối soát nếu tính đúng nghiệp vụ là yêu cầu nghiêm ngặt.
- **Quyền, symlink, rename và truncate** có thể gây hành vi phức tạp hoặc khác nhau.
- **File system không hỗ trợ watcher** có thể gây `UnsupportedOperationException` khi tạo dịch vụ; đừng giả định mọi `FileSystem` đều có khả năng theo dõi.

Đặc biệt, **không có bảo đảm một event modify tương ứng “bên ghi đã đóng file”**. Sự kiện là thông tin phát hiện thay đổi, không phải lời hứa dữ liệu đã ổn định và sẵn sàng nhập.

Ứng dụng cần độ tin cậy cao thường kết hợp watcher để phát hiện sớm với **cơ chế quét lại trạng thái**, nhật ký riêng hoặc giao thức đồng bộ hóa giữa các thành phần.

## 17. Những ngoại lệ và lỗi phổ biến

| Vấn đề | Kết quả / rủi ro | Cách xử lý |
| --- | --- | --- |
| Đăng ký `Path` là file thay vì thư mục | Không đúng mục tiêu; có thể `NotDirectoryException` | Đăng ký thư mục cha đã tồn tại |
| Đăng ký đường dẫn không tồn tại | Không thể đăng ký đúng đối tượng | Tạo/kiểm tra thư mục rồi đăng ký |
| Đăng ký event/modifier không được hỗ trợ | `UnsupportedOperationException` hoặc lỗi tham số | Chỉ dùng kinds chuẩn và kiểm tra provider |
| Quên gọi `key.reset()` | Key có thể không được tái xếp hàng cho lần xử lý sau | Reset sau khi xử lý pending events |
| Gọi `reset()` trước khi xử lý xong cùng một key | Có thể có consumer khác xử lý trùng | Quản lý một owner per key, reset sau |
| Nghĩ `pollEvents()` lấy event mãi mà không tiêu thụ | Bỏ qua đặc tính lấy và xóa pending events | Xử lý danh sách lấy ra một lần |
| Nghĩ `take()` luôn trả ngay | Luồng có thể chờ vô hạn nếu không có event | Có shutdown/interrupt/close hoặc poll timeout |
| Coi `OVERFLOW` là tên file | `context()` không phải Path của entry | Quét và đối soát lại thư mục |
| Ép `event.context()` thành Path với mọi kind | Có thể sai kiểu/null | Kiểm tra kind trước |
| Đọc `ENTRY_MODIFY` như file đã hoàn tất | Dữ liệu chưa ổn định | Giao thức hoàn tất, rename/marker/đối soát |
| Giám sát thư mục cha và tin theo dõi cả cây | Bỏ lỡ thay đổi trong thư mục con | Đăng ký từng thư mục |
| Đóng WatchService mà vẫn `take()` | `ClosedWatchServiceException` | Xử lý shutdown đúng cách |
| Bỏ qua `InterruptedException` | Worker khó dừng hợp lệ | Khôi phục cờ interrupt hoặc thoát theo chính sách |
| Không xử lý `reset()==false` | Vòng lặp giữ key invalid | Xóa registry, quyết định re-register |
| Dùng busy-poll liên tục | CPU tăng mạnh | `take()` hoặc `poll(timeout)` |
| Không đóng WatchService | Rò rỉ tài nguyên native | `try-with-resources` |
| Tin event cung cấp snapshot file | Race khi entry đã bị xóa/thay thế | Kiểm tra file tại thời điểm sử dụng |

## 18. Tự kiểm tra kiến thức

**Câu 1.** `WatchService` có phải đối tượng được gọi `register(ENTRY_CREATE)` trực tiếp hay không?

**Đáp án:** Thông thường **không**. Một `Watchable` (như `Path` đại diện thư mục) gọi `register(watcher, kinds...)` và trả `WatchKey`.

**Câu 2.** Khi `watcher.take()` trả một `WatchKey`, có thể quên `reset()` nếu muốn tiếp tục theo dõi không?

**Đáp án:** Không. Sau khi xử lý `pollEvents()`, phải gọi `reset()` và kiểm tra boolean hợp lệ. Nếu key vẫn ở trạng thái signalled, những event tiếp theo có thể được tích lũy nhưng key không được xếp lại vào queue theo cách bạn kỳ vọng.

**Câu 3.** Đăng ký `root` có nhận được `ENTRY_CREATE` của `root/a/b.txt` không?

**Đáp án:** Không mặc định. Watcher theo dõi các entry trực tiếp của `root`; phải đăng ký `root/a` nếu muốn theo dõi thay đổi của entry `b.txt`.

**Câu 4.** Khi `event.kind()==ENTRY_DELETE`, có thể mở file qua `dir.resolve(event.context())` và mong nó vẫn tồn tại không?

**Đáp án:** Không. Event báo entry đã bị xóa hoặc chuyển ra; đường dẫn có thể không tồn tại. Handler cần xử lý `NoSuchFileException` và kiểm tra trạng thái tại thời điểm dùng.

**Câu 5.** Khi có `OVERFLOW`, một sự kiện cụ thể có thể đã bị bỏ. Giải pháp nào đúng?

**Đáp án:** Đánh dấu trạng thái theo dõi không còn đầy đủ và **rà soát lại trạng thái** thư mục, khôi phục cache/chỉ mục theo thiết kế. Không thể khôi phục chính xác event đã mất chỉ bằng `pollEvents()` lần nữa.

**Câu 6.** Sau khi `WatchKey.cancel()`, `key.watchable()` có trả lại `Path` đã đăng ký không?

**Đáp án:** Có, theo hợp đồng `WatchKey.watchable()`, phương thức này vẫn trả đối tượng watchable ngay cả khi key đã bị hủy. Tuy nhiên điều đó **không** làm key valid lại hoặc chứng minh thư mục hiện còn tồn tại.

## 19. Tổng kết và tài liệu

Để dùng `WatchService` đúng cách, hãy ghi nhớ chuỗi thao tác:

1. **Tạo** `WatchService` từ `FileSystem` phù hợp.
2. **Đăng ký** một `Path` trỏ tới thư mục đang tồn tại bằng `Path.register(watcher, ENTRY_...)`.
3. **Đợi** `WatchKey` qua `take()` hoặc `poll(...)`.
4. **Duyệt** `key.pollEvents()`; xử lý kind, context tương đối, count và `OVERFLOW`.
5. **Reset** bằng `key.reset()`; nếu false, dọn đăng ký không còn hợp lệ.
6. **Đóng** watcher đúng vòng đời; xử lý interrupt, race, đồng thời và tình huống provider không hỗ trợ.

**Điểm cốt lõi:** `WatchService` là cơ chế **phát hiện thay đổi**, không phải log có bảo đảm ghi nhận mọi sự kiện. `Watchable` định nghĩa cách đăng ký, `WatchKey` đại diện đăng ký và cần reset sau mỗi lượt, còn `WatchEvent` chứa những thông tin được phát hiện. Khi độ chính xác quan trọng, kết hợp sự kiện với việc kiểm tra lại **trạng thái hệ thống tệp thực tế**.

**Tài liệu tham khảo chính thức cho Java SE 17:**

- [WatchService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/WatchService.html)
- [Watchable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Watchable.html)
- [WatchKey](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/WatchKey.html)
- [Path.register](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html)
- [StandardWatchEventKinds](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardWatchEventKinds.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="watch-service-002">ENTRY_CREATE, ENTRY_DELETE và ENTRY_MODIFY</a>

<details>
<summary>Xem chi tiết</summary>

# ENTRY_CREATE, ENTRY_DELETE và ENTRY_MODIFY: Theo dõi thay đổi tệp bằng Java NIO.2

## 1. Mục tiêu của bài

Khi cần phát hiện một tệp mới được tạo, tệp cũ bị xóa hoặc nội dung của một tệp được chỉnh sửa, Java NIO.2 cung cấp **`WatchService`**. Cơ chế này theo dõi thay đổi của các **entry trong thư mục đã đăng ký**, rồi cung cấp các thông báo thông qua `WatchKey` và `WatchEvent`.

Ba sự kiện thường gặp nhất được định nghĩa trong `java.nio.file.StandardWatchEventKinds`:

- **`ENTRY_CREATE`**: một entry mới xuất hiện trong thư mục được theo dõi.
- **`ENTRY_DELETE`**: một entry biến mất khỏi thư mục được theo dõi.
- **`ENTRY_MODIFY`**: một entry trong thư mục được quan sát là đã thay đổi.

Bài này giải thích ý nghĩa và giới hạn của từng loại, cách đăng ký và xử lý sự kiện đúng vòng đời, ví dụ Java 17+ có thể chạy được, các lỗi thường gặp như mất sự kiện, đăng ký thiếu thư mục con và đọc tệp khi writer còn đang ghi.

**Tư tưởng quan trọng:** `WatchService` là một *cơ chế thông báo thay đổi*, **không phải nhật ký giao dịch có khả năng ghi nhận đầy đủ mọi thay đổi**. Khi ứng dụng cần trạng thái chính xác của dữ liệu, hãy dùng sự kiện làm tín hiệu để **kiểm tra trạng thái tệp thật** và đối chiếu với snapshot/chỉ mục.

## 2. Các thành phần trong một luồng xử lý

| Thành phần | Vai trò |
| --- | --- |
| `FileSystems.getDefault().newWatchService()` | Tạo dịch vụ tiếp nhận sự kiện file system |
| `Path.register(watchService, kinds...)` | Đăng ký thư mục với những loại sự kiện cần theo dõi |
| `WatchKey` | Đại diện một registration; được đánh dấu khi có sự kiện |
| `WatchService.take()` | Chờ một key có tín hiệu, có thể chặn thread |
| `WatchService.poll()` / `poll(timeout, unit)` | Lấy key ngay hoặc đợi có giới hạn thời gian |
| `WatchKey.pollEvents()` | Lấy danh sách sự kiện đã tích lũy của registration |
| `WatchEvent.kind()` | Loại sự kiện: CREATE / DELETE / MODIFY / OVERFLOW |
| `WatchEvent.context()` | Ngữ cảnh entry; với ba sự kiện chuẩn là `Path` tương đối |
| `WatchEvent.count()` | Số lần một sự kiện đã được cộng dồn theo cơ chế của provider |
| `WatchKey.reset()` | Sau khi xử lý xong, cho phép key được kích hoạt lại; trả `false` nếu không còn hợp lệ |
| `WatchKey.cancel()` | Hủy đăng ký |
| `WatchService.close()` | Giải phóng dịch vụ, đánh thức các thread đang chờ với lỗi đóng dịch vụ |

Một ví dụ khai báo cơ bản:

~~~java
Path directory = Path.of("data");
try (WatchService watcher =
             directory.getFileSystem().newWatchService()) {
    WatchKey key = directory.register(
            watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
    // Vòng lặp nhận và xử lý sự kiện nằm tại đây.
}
~~~

Đây là **mảnh mã minh họa**, chưa phải chương trình hoàn chỉnh; cần import các lớp NIO và static import ba hằng trong `StandardWatchEventKinds`. Thư mục phải tồn tại, provider phải hỗ trợ cơ chế theo dõi.

Tại sao ví dụ dùng `directory.getFileSystem().newWatchService()` thay vì luôn `FileSystems.getDefault()`? Vì `Path` có thể thuộc một `FileSystem`/provider khác. Việc tạo dịch vụ từ cùng file system giúp tránh ghép một `Path` với watcher của provider không tương thích. Trên một số provider ảo, watch service có thể không được hỗ trợ; cần xử lý `UnsupportedOperationException` hoặc lỗi tương ứng.

## 3. ENTRY_CREATE: entry mới xuất hiện

`ENTRY_CREATE` được báo khi provider quan sát rằng một entry mới xuất hiện **trực tiếp** trong thư mục được đăng ký. Entry có thể là tệp, thư mục con hoặc dạng entry khác mà provider hỗ trợ.

Các thao tác có thể gây `ENTRY_CREATE`:

- `Files.createFile(folder.resolve("a.txt"))` tạo tệp mới.
- `Files.createDirectory(folder.resolve("child"))` tạo thư mục con.
- Chương trình khác tạo tệp hoặc di chuyển/đổi tên một entry **vào trong** thư mục được theo dõi.

Ví dụ một entry mới:

~~~text
Trước:  watched/
              notes.txt
Sau:    watched/
              notes.txt
              report.csv   <-- ENTRY_CREATE cho report.csv
~~~

Với `WatchEvent<Path>` của sự kiện chuẩn, `event.context()` thường là đường dẫn **tương đối với thư mục đã đăng ký**, ví dụ `Path.of("report.csv")`, không phải đường dẫn đầy đủ. Muốn tạo đường dẫn để kiểm tra:

~~~java
Path changed = directory.resolve(relativeName).normalize();
~~~

Đây là **mã minh họa**, giả sử `directory` là thư mục đã đăng ký và `relativeName` là `Path` context hợp lệ. Nếu cần chống đường dẫn đáng ngờ trong các hệ thống nhạy cảm, phải kiểm tra phạm vi trước khi đọc và cân nhắc symbolic link/race khi truy cập.

**Một CREATE không chứng minh tệp đã được ghi xong.** Writer có thể tạo tên tệp trước, rồi truyền nhiều MB nội dung vào đó. Watcher thấy entry mới trong lúc tệp còn rỗng hoặc chưa đủ dữ liệu. Nếu cần chỉ nhận file hoàn chỉnh, bên tạo có thể ghi vào tệp tạm, đóng/đồng bộ theo yêu cầu, rồi đổi tên sang tên chính thức; bên nhận chờ tên chính thức xuất hiện và xác thực kích thước, checksum hoặc marker hoàn tất.

Đổi tên một entry **vào** thư mục có thể được báo dưới dạng CREATE. Đây là thông báo về thay đổi entry, không phải khẳng định nội dung của file được tạo từ đầu.

Theo hợp đồng `StandardWatchEventKinds` của Java SE 17, `count()` đối với một sự kiện `ENTRY_CREATE` là 1; nhiều thao tác tạo khác nhau có thể sinh các event khác nhau, nhưng vẫn không được coi danh sách sự kiện là lịch sử bất biến trong mọi tình huống (vì provider có thể mất sự kiện và phát `OVERFLOW`).

## 4. ENTRY_DELETE: entry không còn trong thư mục

`ENTRY_DELETE` được báo khi một entry bị loại khỏi thư mục đang theo dõi. Nguyên nhân có thể là:

- `Files.delete(file)` hoặc ứng dụng khác xóa entry.
- Entry được **di chuyển ra ngoài** thư mục theo dõi.
- Tên entry thay đổi theo cách provider báo là xóa tên cũ và tạo tên mới.

Ví dụ:

~~~text
Trước:  watched/
              old.log
              config.yml
Sau:    watched/
              config.yml
          <-- ENTRY_DELETE cho old.log
~~~

Một DELETE **không nhất thiết chứng minh dữ liệu vật lý đã bị xóa vĩnh viễn**. Có thể file được di chuyển nơi khác, hoặc có process còn giữ handle mở tùy hệ điều hành. Sự kiện chỉ nói rằng tên/entry trong thư mục được theo dõi đã biến mất theo quan sát của watcher.

Ở thời điểm nhận event DELETE, gọi `Files.readString(changed)` thường không hợp lý: đường dẫn có thể không tồn tại. Trong khoảng thời gian giữa lúc nhận event và lúc kiểm tra, entry cùng tên cũng có thể **đã được tạo lại**; phải xem đây là trạng thái hiện tại, không phải bằng chứng về một lịch sử tuần tự của cùng đối tượng file.

Ứng dụng quản lý chỉ mục cần xử lý trường hợp file bị xóa rồi tạo lại nhanh, hoặc sự kiện đến muộn: nên đối chiếu lại danh sách thư mục và danh tính/metadata cần thiết thay vì xóa dữ liệu nghiệp vụ dựa hoàn toàn vào một thông báo.

Theo Java SE 17, `count()` của một sự kiện `ENTRY_DELETE` là 1. Nếu thay đổi xảy ra quá nhanh hoặc provider quá tải, sự kiện có thể không được quan sát đầy đủ; xem phần `OVERFLOW` và reconciliation.

## 5. ENTRY_MODIFY: entry đã thay đổi, không đồng nghĩa “ghi xong”

`ENTRY_MODIFY` được báo khi provider quan sát một entry đã thay đổi. Thay đổi có thể xuất phát từ cập nhật nội dung, metadata, thao tác lưu tệp từ editor hoặc những hành vi khác theo provider/file system.

Ví dụ:

~~~text
report.csv ban đầu:
id,total
1,100

Sau một lần ghi:
id,total
1,200

Watcher có thể báo ENTRY_MODIFY cho report.csv.
~~~

Những giới hạn phải nhớ:

1. **Một lần lưu có thể sinh nhiều MODIFY.** Editor có thể ghi từng phần, truncate, fsync, đổi metadata hoặc ghi file tạm/rename; watcher có thể phát nhiều event cho một thao tác “Save” ở tầng người dùng.
2. **Nhiều thay đổi có thể gộp thành một event**, với `event.count() > 1`. Tài liệu Java SE 17 cho phép `count()` của `ENTRY_MODIFY` bằng 1 hoặc lớn hơn.
3. **Nhận MODIFY không bảo đảm writer đã kết thúc**. Không đọc ngay rồi mặc định dữ liệu là hoàn chỉnh. Khi đọc được snapshot giữa lúc ghi, file có thể trống hoặc chứa một phần dữ liệu.
4. **Không có before/after payload.** `WatchEvent` không mang nội dung cũ, nội dung mới hoặc bản diff. Muốn biết thay đổi, ứng dụng phải tự đọc/so sánh trạng thái hiện tại.
5. **Không nên coi mọi MODIFY đều có ý nghĩa nghiệp vụ.** Một số ứng dụng thay đổi metadata hoặc ghi lại nội dung giống hệt; cần so sánh kích thước, timestamp, hash hay dữ liệu thực nếu muốn lọc.
6. **Không dùng `count()` làm số lần ghi business chính xác.** Count chỉ phản ánh cách provider gộp các thông báo, không tương đương số transaction hoặc lần gọi `Files.write`.

Một chiến lược khi nhận MODIFY:

~~~text
ENTRY_MODIFY
     ↓
Đưa đường dẫn vào hàng đợi "dirty paths" (set chống trùng)
     ↓
Chờ một khoảng debounce / xác nhận producer đã hoàn tất
     ↓
Đọc dữ liệu mới có kiểm tra lỗi và kích thước
     ↓
So sánh phiên bản/checksum và cập nhật chỉ mục
~~~

Debounce chỉ làm giảm xử lý trùng và tăng cơ hội dữ liệu ổn định, **không phải chứng minh tệp đã được ghi xong**. Khi yêu cầu mạnh hơn, cần phối hợp với producer bằng rename nguyên tử trong phạm vi được hỗ trợ, marker hoàn tất hoặc một giao thức khóa/phiên bản.

## 6. Ví dụ Java 17+ hoàn chỉnh: tạo, sửa, xóa một tệp

Ví dụ sau tự tạo thư mục tạm mới, đăng ký watcher với cả ba loại sự kiện, rồi thực hiện lần lượt:

1. Tạo `sample.txt` và chờ `ENTRY_CREATE`.
2. Ghi nội dung mới vào chính tệp này và chờ `ENTRY_MODIFY`.
3. Xóa file và chờ `ENTRY_DELETE`.

Ví dụ dùng `poll(timeout, unit)` với hạn 5 giây cho mỗi loại sự kiện để tránh treo vô hạn nếu provider không báo event theo kỳ vọng. Nó **không** giả định một operation chỉ sinh đúng một event; hàm `await` lọc theo loại và tên, bỏ qua event phụ không phải mục tiêu ở mỗi bước. Cách bỏ qua này chỉ phù hợp demo; chương trình production phải xử lý mọi sự kiện liên quan.

Lưu file **`WatchKindsDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.*;

public class WatchKindsDemo {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("watch-kinds-");
        Path file = directory.resolve("sample.txt");
        try (WatchService watcher = directory.getFileSystem().newWatchService()) {
            directory.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            System.out.println("Watching sample.txt");

            Files.writeString(file, "first", StandardOpenOption.CREATE_NEW);
            await(watcher, ENTRY_CREATE, file.getFileName());

            Files.writeString(file, "second",
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            await(watcher, ENTRY_MODIFY, file.getFileName());

            Files.delete(file);
            await(watcher, ENTRY_DELETE, file.getFileName());
        } finally {
            if (Files.exists(file)) Files.delete(file);
            Files.delete(directory);
        }
    }

    private static void await(WatchService watcher, WatchEvent.Kind<Path> wanted,
                              Path filename) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new IOException("Timed out waiting for " + wanted.name());
            }
            WatchKey key = watcher.poll(remaining, TimeUnit.NANOSECONDS);
            if (key == null) throw new IOException("Timed out waiting for " + wanted.name());
            boolean matched = false;
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == OVERFLOW) {
                    throw new IOException("WatchService overflow; rescan needed");
                }
                if (event.kind() == wanted && filename.equals(event.context())) {
                    matched = true;
                }
            }
            if (!key.reset()) throw new IOException("Watched directory unavailable");
            if (matched) {
                System.out.println(wanted.name() + ": " + filename);
                return;
            }
        }
    }
}
~~~

Chạy với JDK 17 trở lên:

~~~bash
javac WatchKindsDemo.java
java WatchKindsDemo
~~~

**Đầu ra quan sát được khi biên dịch và chạy trên môi trường kiểm thử hỗ trợ WatchService:**

~~~text
Watching sample.txt
ENTRY_CREATE: sample.txt
ENTRY_MODIFY: sample.txt
ENTRY_DELETE: sample.txt
~~~

Các dòng trên là **kết quả đã kiểm tra trên hệ thống thử nghiệm**, không phải lời bảo đảm tất cả hệ điều hành/file system đều phát event cùng cách và trong 5 giây. Nếu provider không hỗ trợ một sự kiện, hoặc báo chậm, chương trình có thể kết thúc bằng `IOException` timeout thay vì in đủ ba dòng; ứng dụng thực tế phải định nghĩa chính sách retry/reconciliation.

**Điểm quan trọng trong mã:**

- `Path directory` là thư mục được đăng ký; `file.getFileName()` chính là tên tương đối dùng để đối chiếu `event.context()`.
- `Files.writeString(..., CREATE_NEW)` tạo entry mới; lệnh ghi tiếp theo trên cùng file yêu cầu `TRUNCATE_EXISTING, WRITE`; thao tác thứ ba xóa entry.
- `watcher.poll(timeout, NANOSECONDS)` chờ sự kiện có giới hạn; `null` nghĩa là đã hết thời gian mà không có key sẵn sàng.
- `WatchKey.pollEvents()` cung cấp các event trong lần xử lý. `OVERFLOW` có thể xuất hiện ngay cả khi không đăng ký nó; khi gặp, demo dừng để tránh coi chuỗi sự kiện là đầy đủ.
- `key.reset()` **bắt buộc** sau khi xử lý batch để key có thể báo lại. Trả `false` nghĩa là key không còn hợp lệ; ví dụ báo lỗi thay vì chờ mãi.
- `try-with-resources` đóng WatchService; khối `finally` dọn tệp và thư mục tạm. Mã chạy trên dữ liệu riêng nên không động đến thư mục người dùng.
- Việc theo dõi ba thao tác **tuần tự** giảm nguy cơ sự kiện bị gộp làm demo khó quan sát. Tuy nhiên event phụ, thứ tự quan sát và thời điểm nhận vẫn phụ thuộc provider.
- Hàm `await` chủ ý tìm một sự kiện phù hợp; trong hệ thống thực tế không thể bỏ qua những event khác như vậy vì có thể bỏ sót thay đổi quan trọng.

## 7. Vòng lặp watcher có thể dùng lâu dài

Trong hệ thống chạy liên tục, thường dùng một thread tiêu thụ event và chuyển công việc sang queue. Trình tự đúng:

1. Tạo `WatchService` với file system thích hợp.
2. Đăng ký **thư mục** hiện có với những event kinds cần theo dõi.
3. Gọi `take()` (chặn tới khi có key) hoặc `poll(timeout, ...)`.
4. Lặp qua **toàn bộ** `key.pollEvents()`.
5. Phân nhánh `event.kind()`; nếu `OVERFLOW`, đánh dấu cần quét đối chiếu.
6. Với `ENTRY_CREATE`/`ENTRY_DELETE`/`ENTRY_MODIFY`, lấy context và ghép với thư mục tương ứng với key.
7. Đưa thay đổi vào hàng đợi xử lý phù hợp, tránh làm việc nặng trên watcher thread.
8. Gọi `key.reset()` **sau khi đã duyệt batch**. Nếu `false`, xóa registration khỏi registry và xử lý việc thư mục biến mất/không truy cập được.
9. Khi shutdown, đóng dịch vụ và dừng thread theo giao thức hủy đã định nghĩa.

Một đoạn minh họa về vòng lặp, **không phải chương trình độc lập**:

~~~java
while (running) {
    WatchKey key = watcher.take();
    Path watched = (Path) key.watchable();

    for (WatchEvent<?> event : key.pollEvents()) {
        WatchEvent.Kind<?> kind = event.kind();
        if (kind == OVERFLOW) {
            scheduleFullRescan(watched);
            continue;
        }
        if (kind == ENTRY_CREATE || kind == ENTRY_DELETE || kind == ENTRY_MODIFY) {
            Path relative = (Path) event.context();
            Path absolute = watched.resolve(relative);
            enqueueChange(kind, absolute, event.count());
        }
    }
    if (!key.reset()) {
        onRegistrationInvalid(watched);
    }
}
~~~

Giả định các biến `running`, `watcher` và các hàm `scheduleFullRescan`, `enqueueChange`, `onRegistrationInvalid` do chương trình sở hữu; imports cần thiết đến từ `java.nio.file`. Đoạn này trình bày flow, **không phải chương trình để copy và biên dịch độc lập**.

**Tại sao kiểm tra `OVERFLOW` trước khi ép kiểu context?** Vì `OVERFLOW` có kiểu `Kind<Object>` và context mang tính implementation-specific, có thể `null`. Không ép thẳng tất cả `WatchEvent<?>.context()` thành `Path` trước khi phân loại.

**Tại sao phải reset sau khi xử lý?** Theo hợp đồng API, WatchKey đã báo tín hiệu cần được reset để có thể báo lại. Nếu không reset, thread có thể xử lý batch đầu rồi không nhận các thay đổi tiếp theo theo kỳ vọng.

Nếu `key.reset()` trả `false`, thư mục được theo dõi có thể đã bị xóa, không còn truy cập được hoặc đăng ký đã hủy. Không reset liên tục để hy vọng key tự sống lại; cần xử lý state, bỏ key hoặc đăng ký lại thư mục khi điều kiện cho phép.

## 8. Cơ chế WatchKey, reset và coalescing

WatchService không đặt “một key trong queue cho mỗi event”. Mỗi registration có một `WatchKey`; nó được *signal* khi có event và được đưa vào queue nếu chưa ở trạng thái đã báo. Nhiều event có thể tích lũy trên cùng key trước khi consumer lấy ra.

Sơ đồ:

~~~text
Watchable directory
        │ create/modify/delete
        ▼
WatchService ghi nhận events cho WatchKey
        │ signal / queue key nếu chưa signalled
        ▼
watcher.take() hoặc watcher.poll()
        │
        ▼
key.pollEvents() → [event1, event2, ...]
        │
        └── xử lý xong → key.reset()
                             │
                             ├── true: sẵn sàng nhận các lượt event tiếp
                             └── false: registration không còn hợp lệ
~~~

Một số điểm cần nhớ:

- `pollEvents()` có thể cung cấp nhiều event cùng tên/loại hoặc event đã được **gộp**. Với `ENTRY_MODIFY`, `event.count()` có thể lớn hơn 1.
- `WatchService` có thể tích lũy event nhanh hơn consumer kịp xử lý; implementation có quyền giới hạn hàng đợi và báo `OVERFLOW` khi biết đã bỏ sự kiện.
- `WatchEvent.count()` không phải timestamp, version, byte count hay thứ tự transaction.
- Một event đã được báo không khóa file. Trong lúc consumer xử lý, file có thể lại bị sửa hoặc xóa. Bất biến “sự kiện nói rằng file hiện vẫn đang tồn tại” **không được bảo đảm**.
- Kể cả khi không thấy `OVERFLOW`, do sự khác biệt nền tảng và điều kiện I/O, không nên thiết kế nghiệp vụ yêu cầu event log hoàn chỉnh mà chỉ dựa vào WatchService.

## 9. Phân biệt ba event khi rename và move

WatchService chuẩn chỉ định `ENTRY_CREATE`, `ENTRY_DELETE`, `ENTRY_MODIFY`, `OVERFLOW`; **không có sự kiện rename riêng trong StandardWatchEventKinds**.

Một thao tác rename/move có thể được phản ánh bằng những thay đổi entry:

| Thao tác | Những sự kiện có thể quan sát |
| --- | --- |
| Tạo `new.txt` trong thư mục được theo dõi | `ENTRY_CREATE new.txt`; có thể có thêm MODIFY khi ghi |
| Xóa `old.txt` | `ENTRY_DELETE old.txt` |
| Đổi tên `a.txt` thành `b.txt` trong cùng thư mục | Thường thấy DELETE tên cũ và CREATE tên mới, nhưng chi tiết phụ thuộc provider |
| Di chuyển file từ ngoài vào thư mục watched | Thường thấy CREATE cho tên được đưa vào |
| Di chuyển file từ watched ra ngoài | Thường thấy DELETE cho tên bị đưa ra |
| Ghi lại nội dung file bằng cách replace/rename temp file | Có thể xuất hiện CREATE/DELETE/MODIFY cho nhiều tên |

Không tự suy luận từ cặp `DELETE(old)` + `CREATE(new)` rằng đó chắc chắn là **cùng một file được rename**. Hai file không liên quan có thể bị xóa/tạo gần nhau. Nếu ứng dụng cần phát hiện move/rename chính xác, phải dùng thêm file identity/metadata phù hợp hoặc API native/giao thức của ứng dụng; `WatchEvent` tiêu chuẩn không cung cấp cặp source/destination như một transaction.

## 10. Theo dõi thư mục con: không tự động recursive

Một điểm dễ sai: đăng ký `root` **không mặc nhiên** theo dõi các thay đổi nằm sâu trong cây thư mục.

~~~text
root/                  <-- đã đăng ký watcher cho root
  a.txt                <-- thay đổi có thể báo qua root
  sub/                 <-- bản thân sub tạo/xóa trong root có thể báo qua root
    nested.txt         <-- thay đổi bên trong sub không mặc nhiên báo qua root
~~~

Nếu muốn theo dõi đệ quy:

1. Duyệt các thư mục con hiện có và đăng ký từng thư mục.
2. Khi quan sát `ENTRY_CREATE` cho một thư mục con mới, kiểm tra nó có thực sự là thư mục và đăng ký watcher tương ứng.
3. Cân nhắc các tệp/thư mục đã xuất hiện **trước khi** đăng ký thư mục con mới; cần quét lại thư mục đó để bắt các thay đổi đã xảy ra trong khoảng trống.
4. Khi thư mục bị xóa/move hoặc `reset()` báo invalid, cập nhật registry và các đăng ký liên quan.
5. Có chính sách với symlink, mount point, quyền truy cập và giới hạn số lượng registration của hệ điều hành.

Việc kiểm tra thư mục con bằng `Files.isDirectory(path)` sau event có **race**: entry có thể bị xóa hoặc thay thế trước khi kiểm tra. Trên hệ thống lớn, nên kết hợp watcher với snapshot/reconciliation định kỳ. Không theo dõi bừa mọi symbolic link vì có thể gây loop hoặc vượt phạm vi dữ liệu.

Không dùng một thread blocking riêng cho **mỗi tệp** chỉ để chờ sự kiện nếu thật ra muốn theo dõi các entry của cùng một thư mục. WatchService cho phép một watcher theo dõi nhiều directory registrations, nhưng chi phí và giới hạn thực tế phụ thuộc nền tảng.

## 11. OVERFLOW: trường hợp bắt buộc quét đối chiếu

`StandardWatchEventKinds.OVERFLOW` báo rằng **có thể đã có sự kiện bị mất hoặc bị bỏ**. Bạn **không cần đăng ký `OVERFLOW`** để nhận được nó; đó là tín hiệu đặc biệt của WatchService.

Nếu chỉ xử lý ba sự kiện chính mà bỏ qua `OVERFLOW`, index nội bộ có thể vĩnh viễn lệch so với thư mục thực tế. Cách tiếp cận an toàn hơn:

1. Đánh dấu directory hoặc subtree là **dirty / needs-reconcile**.
2. Đọc lại danh sách entry và metadata cần thiết.
3. So sánh với snapshot/index đang quản lý: phát hiện entry mới, entry mất và entry cần kiểm tra nội dung.
4. Cập nhật trạng thái nghiệp vụ theo giao thức idempotent; không giả định mỗi thay đổi chỉ được phát một lần.
5. Tiếp tục nghe sự kiện và lên lịch quét định kỳ để giảm tác động của mất/sai thứ tự event.

Ví dụ snapshot mức đơn giản có thể gồm `Map<Path, Metadata>`, trong đó Metadata lưu kích thước và `lastModifiedTime`. Tuy nhiên hai thuộc tính này cũng không phát hiện **mọi** thay đổi (độ chính xác timestamp, write same-size, metadata thay đổi, thao tác nhanh). Nếu cần xác thực nội dung, tính hash hoặc dùng version/commit marker do producer cung cấp.

### “Lấy snapshot trước hay đăng ký trước?”

Một vấn đề khởi tạo: nếu quét thư mục **trước** khi register, có thể có tệp được tạo trong khoảng trống giữa hai bước nên bị bỏ sót. Đăng ký **trước**, rồi quét snapshot và tiêu thụ events cũng phải xử lý những trùng lặp/race giữa snapshot và event.

Mẫu tốt: register → snapshot → drain events → reconcile (hoặc thiết kế tương đương), mọi thao tác update idempotent. Khi độ chính xác rất cao, cần hợp đồng nguồn dữ liệu chuyên dụng chứ không chỉ WatchService.

## 12. Cẩn thận khi xử lý tệp đang được ghi

Một chuỗi thường gặp:

~~~text
Writer:
  create("dataset.csv")
  write chunk 1
  write chunk 2
  write chunk 3
  close()

Watcher:
  ENTRY_CREATE
  ENTRY_MODIFY
  ENTRY_MODIFY ...
~~~

Watcher có thể chạy ngay giữa các chunk; nếu consumer parse khi chỉ có chunk đầu, sẽ thấy CSV/JSON/ZIP chưa hoàn chỉnh. Đây không phải lỗi riêng của Java mà là khác biệt giữa **entry đã thay đổi** và **đơn vị dữ liệu nghiệp vụ đã commit**.

Các giải pháp tùy mức độ yêu cầu:

- **Tệp tạm + đổi tên publish:** producer ghi vào tên tạm, xác thực/flush và đóng tệp, rồi chuyển sang tên publish. Nếu cùng file system hỗ trợ `StandardCopyOption.ATOMIC_MOVE`, có thể yêu cầu move nguyên tử; nếu không được hỗ trợ, phải quyết định fallback hoặc báo lỗi. Không tự khẳng định mọi rename trên network FS đều atomic/durable.
- **Marker hoàn tất:** producer ghi dữ liệu, sau đó tạo tệp sentinel như `dataset.csv.done`; watcher chỉ đưa dữ liệu vào xử lý sau marker và kiểm tra lại dữ liệu.
- **Lock hợp tác:** producer và consumer cùng thỏa thuận dùng khóa file hoặc lock khác. File locks phụ thuộc hệ điều hành và các bên phải thực sự tuân thủ; chúng không tự biến nhiều thao tác thành transaction.
- **Debounce:** gộp nhiều MODIFY trong khoảng lặng, sau đó kiểm tra size/mtime lần nữa. Cách này tiện cho file watcher UI nhưng **không đảm bảo hoàn tất** nếu writer tạm nghỉ lâu rồi ghi tiếp.
- **Checksum/version:** dữ liệu chỉ được nhận khi đáp ứng định dạng và phiên bản mong đợi; giúp phát hiện partial write hoặc trạng thái cũ.

Khi có DELETE rồi CREATE lại cùng tên, phải coi đó có thể là object mới. Cache nội dung theo tên file mà không xác thực version/identity dễ trả nhầm dữ liệu.

## 13. Tránh blocking event loop và xử lý trùng lặp

Nếu dùng `watcher.take()` trong một thread, đừng để event handler thực hiện đọc file cực lớn, upload mạng hay truy vấn database blocking ngay trên thread đó. Nếu handler chậm, event queue tích lũy, tăng khả năng `OVERFLOW`.

Kiến trúc gợi ý:

~~~text
WatchService thread
   │ take/poll → pollEvents → classify
   │
   ├── OVERFLOW ────────────> schedule full rescan
   │
   └── CREATE/DELETE/MODIFY -> dirtyPaths set / bounded task queue
                                        │
                                        ▼
                               Worker kiểm tra trạng thái thật
                                        │
                                        ▼
                               Update chỉ mục idempotent
~~~

Các quy tắc:

- Worker pool và queue cần **giới hạn** để tránh bộ nhớ tăng vô hạn.
- Có thể **deduplicate theo Path**: nhiều MODIFY cho cùng path chỉ cần một lần kiểm tra trạng thái sau cùng.
- Worker phải chịu được `NoSuchFileException`: file có thể biến mất trước khi đọc. Điều đó bình thường trong thiết kế theo dõi bất đồng bộ.
- Một file có thể được sửa lần nữa trong lúc worker đang đọc; có thể cần recheck version/hash sau khi xử lý.
- Không tự chia sẻ mutable `Map`/`Set` giữa watcher và worker nếu thiếu synchronized/concurrent collection.
- Nếu registration key đã invalid hoặc WatchService đóng, worker không được tùy tiện cho rằng sẽ có event tiếp theo để bù trạng thái.
- Cần xử lý shutdown/interrupt: `take()` có thể ném `InterruptedException`, đóng watcher khiến thread đang chờ ném `ClosedWatchServiceException`; dừng đúng vòng đời.

Tuy `WatchService` được thiết kế có thể sử dụng với nhiều consumer, ứng dụng đơn giản thường để **một thread sở hữu việc lấy key và reset sau khi xử lý batch**, tránh hai thread xử lý cùng một registration theo cách khó kiểm soát. Nếu nhiều consumer, cần đảm bảo `reset()` chỉ gọi sau khi sự kiện của key đã được xử lý xong theo giao thức của ứng dụng.

## 14. Hạn chế theo nền tảng và provider

WatchService dùng cơ chế thông báo native của hệ điều hành khi có, hoặc cơ chế khác như polling nếu không có native support. Điều này làm hành vi phụ thuộc:

| Môi trường/đặc tính | Cần lưu ý |
| --- | --- |
| Linux, Windows, macOS | Thời điểm giao sự kiện, cách gộp event, metadata được báo có thể khác nhau |
| Network FS / ổ đĩa đồng bộ cloud | Có thể chậm, thiếu, khác hành vi hoặc không đáp ứng kỳ vọng realtime |
| ZIP FS, file system ảo/provider riêng | Không mặc nhiên hỗ trợ watch; tạo/đăng ký có thể không được provider hỗ trợ |
| Thư mục bị xóa, rename, mất quyền | Registration có thể invalid; `reset() == false` |
| Rất nhiều thay đổi liên tục | Queue có thể quá tải dẫn tới `OVERFLOW` |
| Symlink, mounted folders | Cần chính sách phạm vi theo dõi và kiểm soát permission |
| Thay đổi metadata | Không có hợp đồng portable rằng mọi metadata change đều được báo giống nhau qua MODIFY |

Không sử dụng WatchService làm lớp **kiểm toán bảo mật duy nhất** hoặc cơ sở tính toán tài chính đòi hỏi mọi thao tác được ghi nhận chính xác. Trong các bài toán đó, cần hệ thống event log/transaction đáng tin, hoặc trạng thái chuẩn từ database, kết hợp watcher như cơ chế tối ưu để phát hiện cần đồng bộ.

## 15. Exceptions và lỗi thường gặp

| Hiện tượng | Nguyên nhân hoặc ý nghĩa | Hướng xử lý |
| --- | --- | --- |
| `UnsupportedOperationException` khi tạo/register | Provider không hỗ trợ watcher hoặc event kind | Xem capabilities, fallback polling/reconciliation hoặc chọn provider khác |
| `IOException` | Không thể mở, đăng ký hoặc truy cập tài nguyên theo yêu cầu | Log đủ bối cảnh, kiểm tra path/quyền/file system |
| `ClosedWatchServiceException` | Thao tác trên watcher đã đóng hoặc thread đang chờ bị đánh thức do đóng | Kết thúc worker/watcher loop đúng lifecycle |
| `InterruptedException` khi `take()/poll(timeout)` | Thread được interrupt khi chờ | Thực thi chính sách hủy và bảo toàn trạng thái interrupt khi thích hợp |
| `WatchKey.reset() == false` | Registration không còn hợp lệ | Gỡ khỏi registry, xem xét đăng ký lại nếu directory phục hồi |
| `OVERFLOW` | Có thể đã bỏ event | Quét lại thư mục và đối chiếu snapshot |
| `ENTRY_MODIFY` phát nhiều lần | Provider có thể báo/gộp nhiều thay đổi | Debounce, idempotent updates, dựa trạng thái thật |
| Không thấy file trong `Files.exists()` sau CREATE | File bị xóa/rename nhanh hoặc thay đổi race | Đối chiếu trạng thái cuối, không tin sự kiện là snapshot |
| Không thấy event trong thư mục con | Đăng ký không tự recursive | Đăng ký các subdirectories phù hợp |
| Chỉ nhận batch đầu rồi ngừng | Quên `key.reset()` hoặc key invalid | Reset đúng vị trí, kiểm tra boolean |
| Chờ mãi ở `take()` | Không có event hoặc chưa shutdown, lỗi đăng ký | Dùng timeout/close/interrupt phù hợp với yêu cầu |
| Sai đường dẫn được xử lý | Hiểu `event.context()` thành đường dẫn tuyệt đối | Dùng `((Path) key.watchable()).resolve((Path) context)` |

**Trường hợp tệp đang được rename:** `Files.exists(path)` có thể trả false vì lỗi I/O, thiếu quyền hoặc symlink không đúng chứ không chỉ vì file thực sự bị xóa. Nếu cần quyết định chính xác, dùng thao tác đọc thông tin có xử lý ngoại lệ và kiểm tra chính sách symbolic link.

## 16. Checklist khi triển khai watcher thực tế

1. **Đã chọn đúng file system/provider chưa?** `Path` và `WatchService` có tương thích không?
2. **Đã register đúng thư mục chưa?** Đừng đăng ký “file mục tiêu” khi mục đích là theo dõi entry ở thư mục cha.
3. **Đã chọn ba `ENTRY_*` phù hợp chưa?** Nếu không muốn báo sửa, có thể chỉ đăng ký CREATE/DELETE, nhưng vẫn phải xử lý OVERFLOW.
4. **Đã duyệt mọi event trong key?** Không dừng sau event đầu rồi bỏ các entry còn lại.
5. **Đã xử lý OVERFLOW trước khi cast context?** `OVERFLOW` có thể có context null/không phải Path.
6. **Đã gọi reset sau batch và kiểm tra false?** Registration có thể bị hủy tự động.
7. **Có biết watcher không tự recursive?** Nếu cần theo dõi subtree, đăng ký từng thư mục và quét bổ sung khi thư mục mới xuất hiện.
8. **Có kiểm soát file readiness?** MODIFY không chứng minh writer hoàn tất; CREATE cũng không chứng minh file sẵn sàng.
9. **Có cơ chế reconcile?** Một event không phải event-log hoàn chỉnh; cần snapshot/dirty paths/periodic rescan.
10. **Có xử lý race?** File có thể biến mất, đổi tên, bị viết tiếp trước khi đọc.
11. **Có phân tách watcher thread khỏi xử lý nặng?** Tránh backlog/OVERFLOW.
12. **Có dừng sạch?** Đóng WatchService, ngừng worker, cancel key và giải phóng resource phù hợp.

## 17. Câu hỏi tự kiểm tra

1. Sự khác biệt cốt lõi giữa ba loại sự kiện là gì? **CREATE**: entry xuất hiện; **DELETE**: entry biến mất; **MODIFY**: entry được quan sát thay đổi.
2. File `a.txt` được tạo rồi liên tục ghi 100 khối dữ liệu. Có bắt buộc nhận đúng 1 CREATE và 100 MODIFY không? **Không**. Số MODIFY phụ thuộc provider và cơ chế gộp; có thể bị bỏ khi quá tải.
3. CREATE có chứng minh file đã ghi xong không? **Không**; có thể thấy entry ngay trước khi writer hoàn tất.
4. MODIFY có cung cấp nội dung mới/cũ không? **Không**; phải đọc trạng thái hiện tại và so sánh nếu cần.
5. DELETE có đồng nghĩa file bị xóa vĩnh viễn không? **Không**; có thể là move/rename khỏi thư mục hoặc chỉ mất tên trong thư mục đó.
6. Đăng ký `root` có tự bắt MODIFY của `root/sub/x.txt` không? **Không**; phải register `sub` riêng hoặc có cơ chế recursive do hệ thống triển khai.
7. Nhận event có `kind() == OVERFLOW` thì làm gì? **Quét đối chiếu (rescan/reconciliation)** vì có thể đã mất sự kiện.
8. `event.count() > 1` cho MODIFY có chứng minh đúng số lần người dùng nhấn Save không? **Không**; nó chỉ nói về sự kiện được gộp theo provider.
9. Nếu quên `key.reset()` sau `pollEvents()` thì sao? Key có thể không được báo lại theo kỳ vọng; phải reset sau khi xử lý.
10. Tại sao `key.watchable()` và `event.context()` thường cần ghép? Vì context của entry chuẩn là **đường dẫn tương đối** so với thư mục đã đăng ký.
11. Vì sao rename không có sự kiện riêng? `StandardWatchEventKinds` chỉ có CREATE, DELETE, MODIFY và OVERFLOW; move có thể được phản ánh qua đổi entry tên cũ/tên mới, tùy nền tảng.
12. Nếu watcher cho biết MODIFY nhưng `Files.readString(path)` ném `NoSuchFileException`, đó có phải lỗi API không? **Không nhất thiết**; entry có thể đã bị xóa/rename sau khi event được tạo.
13. Có nên dùng WatchService làm nguồn kiểm toán duy nhất nếu mọi thay đổi đều phải được ghi nhận không? **Không**; cần event log/transaction có bảo đảm riêng.
14. Có thể nhận OVERFLOW dù không đăng ký nó không? **Có**; đó là tín hiệu đặc biệt.
15. Vì sao ví dụ dùng poll với timeout mà không dùng take vô hạn? Để bài demo **không treo vô hạn** khi sự kiện không đến trên một môi trường cụ thể.

## Tài liệu API tham khảo

- [Java SE 17 — StandardWatchEventKinds](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardWatchEventKinds.html): định nghĩa chính thức của `ENTRY_CREATE`, `ENTRY_DELETE`, `ENTRY_MODIFY` và `OVERFLOW`.
- [Java SE 17 — WatchService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/WatchService.html): cơ chế watcher, queue, take/poll, đóng và giới hạn nền tảng.
- [Java SE 17 — WatchKey](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/WatchKey.html): `pollEvents`, `reset`, `cancel`, `watchable`.
- [Java SE 17 — WatchEvent](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/WatchEvent.html): `kind`, `context`, `count`.
- [Java SE 17 — Path](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html): `register` và xử lý đường dẫn.
- [Java — Watching a Directory for Changes](https://dev.java/learn/api/io/java-io/file-system/watching-dir-changes/): hướng dẫn WatchService và xử lý event.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="watch-service-003">WatchKey, pollEvents và đường dẫn tương đối của sự kiện</a>

<details>
<summary>Xem chi tiết</summary>

# `WatchKey`, `pollEvents()` và đường dẫn tương đối của sự kiện trong Java NIO.2

## 1. Mục tiêu bài học

`java.nio.file.WatchService` (Java NIO.2) giúp chương trình nhận thông báo về những thay đổi **trong thư mục đã đăng ký**: một mục mới được tạo, một mục bị xóa, hoặc mục bị sửa đổi. Nhưng kết quả từ watcher không phải một chuỗi đường dẫn tuyệt đối sẵn dùng; mỗi lần thông báo, bạn nhận một **`WatchKey`**, lấy danh sách sự kiện bằng `pollEvents()`, rồi kết hợp **`WatchEvent.context()`** với thư mục gắn với key.

Đây là ba mắt xích phải dùng đúng:

- **`WatchKey`**: đại diện cho lần đăng ký của một đối tượng `Watchable` (thường là một thư mục `Path`) với một `WatchService`. Key quản lý hàng đợi sự kiện của registration và trạng thái cần `reset()`.
- **`key.pollEvents()`**: trả danh sách những sự kiện đang chờ của **key đó** và loại những sự kiện đã lấy khỏi hàng đợi của key.
- **`event.context()`**: với các event của thư mục qua provider mặc định, thường là một `Path` **tương đối với thư mục đã đăng ký**, ví dụ `report.txt`, không phải đường dẫn tuyệt đối của tệp.

Sau bài này, bạn có thể tạo watcher, hiểu vòng đời key, ghép `watchable.resolve(context)` đúng cách, tránh nhầm `WatchService.poll()` và `WatchKey.pollEvents()`, xử lý `ENTRY_CREATE`/`ENTRY_DELETE`/`ENTRY_MODIFY`/`OVERFLOW`, quản lý nhiều thư mục và biết giới hạn độ tin cậy của thông báo filesystem.

Ví dụ ưu tiên Java 17+, không yêu cầu thư viện ngoài. Đầu ra có điều kiện về môi trường sẽ được ghi rõ: WatchService phụ thuộc provider, filesystem, OS và thời điểm phát hiện sự kiện.

## 2. Các đối tượng và phương thức quan trọng

| Đối tượng / API | Vai trò |
| --- | --- |
| `WatchService` | Dịch vụ tập trung nhận các **watch key đã được signal**, do `FileSystems.getDefault().newWatchService()` tạo |
| `Path.register(service, kinds...)` | Đăng ký một `Path` (thường là thư mục) và các loại thay đổi với service, trả `WatchKey` |
| `WatchKey` | Registration cụ thể: biết `watchable()`, `pollEvents()`, `reset()`, `isValid()`, `cancel()` |
| `WatchService.take()` | **Chặn** tới khi có key được báo hoặc dịch vụ đóng |
| `WatchService.poll()` | Thử lấy key đang chờ, trả `null` nếu chưa có |
| `WatchService.poll(timeout, unit)` | Đợi tối đa thời hạn để lấy key, có thể trả `null` |
| `WatchKey.pollEvents()` | Rút danh sách sự kiện của **một key** hiện có |
| `WatchEvent.kind()` | Loại sự kiện, chẳng hạn `ENTRY_CREATE` hoặc `OVERFLOW` |
| `WatchEvent.context()` | Context gắn với sự kiện: đối với entry event, thường là `Path` tương đối |
| `WatchEvent.count()` | Số lần sự kiện cùng loại/context đã được gộp theo hợp đồng |
| `WatchKey.reset()` | Xác nhận đã xử lý và đưa key về trạng thái có thể tiếp tục nhận signal; trả `false` nếu key không còn valid |
| `WatchKey.cancel()` | Hủy registration, không xóa file và không tự đóng service |

Một `WatchService` có thể phục vụ nhiều thư mục. Mỗi thư mục đăng ký có `WatchKey` riêng; khi nhận một key, trước tiên phải biết **key thuộc thư mục nào** rồi mới giải quyết event context.

### Hai chữ “poll” nhưng làm hai việc khác nhau

```text
watcher.poll()  hoặc watcher.take()
   |
   v
WatchKey key                 <-- lấy key đã được signal
   |
   +--> key.watchable()      <-- thư mục đã đăng ký
   |
   +--> key.pollEvents()     <-- lấy List<WatchEvent<?>>
          |                       của key này
          v
      kind / context / count
   |
   +--> key.reset()          <-- cho phép theo dõi tiếp
```

`watcher.poll()` **không** trả danh sách event; nó trả `WatchKey` hoặc `null`. Ngược lại, `key.pollEvents()` không dùng để chờ service có sự kiện mới; nó chỉ lấy các event đã được tích lũy trên key đó.

## 3. Đăng ký một thư mục

Đoạn mẫu cơ bản:

```java
Path watchedDir = Path.of("inbox").toAbsolutePath().normalize();

try (WatchService watcher =
        FileSystems.getDefault().newWatchService()) {

    WatchKey registration = watchedDir.register(
            watcher,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY);

    // Sau đó gọi watcher.take() hoặc watcher.poll(timeout, unit).
}
```

Đây là **snippet**, chưa phải chương trình độc lập. Nó giả định `inbox` đã tồn tại, là thư mục mà provider hỗ trợ theo dõi; cần import `java.nio.file.*` và xử lý `IOException`.

Ý nghĩa của từng kind:

- `ENTRY_CREATE`: phát hiện một entry được tạo trong thư mục.
- `ENTRY_DELETE`: phát hiện một entry bị xóa khỏi thư mục.
- `ENTRY_MODIFY`: phát hiện một entry được sửa đổi, theo khả năng và chính sách provider.
- `OVERFLOW`: một hoặc nhiều sự kiện có thể bị bỏ hoặc mất; **không đăng ký `OVERFLOW` như một loại watch event thông thường**; watcher có thể báo nó bất kể các kind cụ thể đã đăng ký.

Đối tượng được đăng ký **là thư mục** (theo cách dùng chuẩn của default filesystem provider) chứ không phải tên của một file riêng lẻ. Đăng ký `inbox` tức là theo dõi các entry **trực tiếp** bên dưới `inbox`, chứ không tự động theo dõi toàn bộ cây thư mục con.

Nếu ứng dụng cần giám sát `inbox/a/b/file.txt` mà chỉ đăng ký `inbox`, bạn không nên tin rằng sẽ có `ENTRY_MODIFY` cho một tệp sâu nhiều cấp. Hãy đăng ký thư mục con cần thiết và xử lý trường hợp thư mục mới được tạo trong lúc chạy.

## 4. `WatchKey` đại diện cho cái gì?

Một `WatchKey` là một registration với watcher. Sau khi đăng ký, nó cung cấp:

```java
Watchable observed = key.watchable();
boolean valid = key.isValid();
List<WatchEvent<?>> events = key.pollEvents();
boolean stillValid = key.reset();
```

**Snippet minh họa**: biến `key` được giả định tồn tại, cần import `Watchable`, `WatchKey`, `WatchEvent` và `List`. Với watcher thư mục đăng ký bằng `Path.register`, `key.watchable()` là `Path` của thư mục đã đăng ký. Không nên tự động hiểu mọi implementation `Watchable` đều là `Path`; ở đây phạm vi là watcher filesystem thư mục.

Một số điểm quan trọng:

1. `key.watchable()` trả **đối tượng gốc được đăng ký**, không phải file vừa thay đổi.
2. `key.isValid()` có thể trở thành false khi registration bị cancel, watcher bị đóng hoặc đối tượng không còn theo dõi được (ví dụ thư mục bị xóa).
3. `key.cancel()` dừng theo dõi; nó không thực hiện xóa hay chuyển file.
4. `key.reset()` phải được gọi sau khi xử lý event; kết quả `false` là tín hiệu không nên chờ registration ấy nữa.
5. Khi nhiều thay đổi xảy ra nhanh trước khi thread lấy key, một key có thể chứa **nhiều event**, và cùng loại event có thể được gộp với `count()`.
6. Khi `WatchService` bị đóng, các lời gọi chờ và thao tác liên quan có thể báo `ClosedWatchServiceException`; quản lý vòng đời service đúng cách.

### Trạng thái ready và signalled

Khái niệm trạng thái của một key:

```text
Đã register
    |
    v
 READY -- có sự kiện --> SIGNALLED và được đưa vào hàng đợi của WatchService
    |                       |
    |                       v
    |             watcher.take() / poll()
    |                       |
    |                       v
    |               key.pollEvents()
    |                       |
    |                       v
    +<--------- key.reset() nếu valid
                            |
                            +-- nếu có thêm event đang chờ,
                                key có thể tiếp tục được đưa vào
                                hàng đợi của WatchService
```

`pollEvents()` chỉ lấy event; **nó không tự đưa key về READY**. Vì vậy nếu quên `reset()`, chương trình có thể xử lý một đợt rồi không được báo đợt mới như mong đợi.

Không được thay `reset()` bằng `cancel()` sau mỗi lần xử lý: `cancel()` hủy registration, trong khi `reset()` xác nhận key và tiếp tục giám sát.

## 5. `pollEvents()`: lấy danh sách sự kiện đúng cách

`WatchKey.pollEvents()` trả `List<WatchEvent<?>>`:

```java
WatchKey key = watcher.take();
for (WatchEvent<?> event : key.pollEvents()) {
    WatchEvent.Kind<?> kind = event.kind();
    Object context = event.context();
    int count = event.count();
    // Xử lý loại, context và số lần sự kiện đã gộp.
}
boolean valid = key.reset();
```

Đây là đoạn minh họa cần `watcher` đã tạo/đăng ký và các import. Điểm cần nhớ:

- `pollEvents()` chỉ lấy các event **đang chờ của một key**; danh sách có thể rỗng trong một số tình huống, không có nghĩa watcher hỏng.
- Nó **loại những event vừa lấy khỏi hàng đợi của key**. Gọi lại `pollEvents()` ngay thường sẽ không trả lại cùng các object event cũ.
- `pollEvents()` có thể chứa nhiều kind khác nhau; không suy ra “một `take` ứng với đúng một file”.
- `event.count()` có thể lớn hơn 1 do nhiều sự kiện giống nhau được gộp. Không thiết kế logic “mỗi event object == đúng một lần sửa file”.
- Có thể có `OVERFLOW`, kể cả khi bạn đăng ký chỉ `ENTRY_CREATE`.
- Sau khi xử lý cần gọi `reset()`, kể cả khi bạn bỏ qua một số kind; nếu false, loại registration khỏi state nội bộ.

Đừng giữ kết quả `pollEvents()` làm danh sách tuyệt đối phản ánh mọi thay đổi trên filesystem. Nó là thông báo để ứng dụng **kiểm tra lại trạng thái** khi cần.

### `WatchService.take()` / `poll()` / `poll(timeout)`

| Cách lấy key | Khi nào hợp lý |
| --- | --- |
| `take()` | Vòng watch chuyên dụng, chấp nhận chặn đến khi có key |
| `poll()` | Event loop đã có công việc khác, chỉ kiểm tra không chờ |
| `poll(5, TimeUnit.SECONDS)` | Muốn chờ có giới hạn, xử lý timeout/hủy |
| `close()` | Đóng WatchService, giải phóng resource, đánh thức thread đang chờ qua ngoại lệ tương ứng |

`take()` có thể ném `InterruptedException` khi thread bị interrupt. `poll(timeout, unit)` cũng có thể bị interrupt và trả `null` khi hết thời hạn không có key. Đừng biến timeout thành “file không còn tồn tại”: chỉ kết luận **không có key được lấy trong khoảng chờ ấy**.

## 6. `WatchEvent.context()` là đường dẫn tương đối

Đây là trọng tâm của bài.

Giả sử đăng ký thư mục:

```text
/opt/app/inbox
```

Một file được tạo:

```text
/opt/app/inbox/report.csv
```

Với event thuộc `ENTRY_CREATE`, context thường là:

```text
report.csv
```

Chứ không phải `/opt/app/inbox/report.csv`. Cách tạo đường dẫn đến entry bị tác động:

```java
Path registeredDirectory = (Path) key.watchable();
Path relative = (Path) event.context();
Path changed = registeredDirectory.resolve(relative);
```

Ba dòng là **snippet**, chỉ dùng khi `event.kind()` là một entry event có `Path` context và key thực sự đến từ `Path.register()`. Có thể thêm `toAbsolutePath().normalize()` khi cần một dạng địa chỉ thống nhất cho log hoặc so sánh.

Quan trọng:

- **Relative to watched directory**, không phải relative to current working directory của Java process.
- Nếu watcher đăng ký nhiều thư mục, phải lấy thư mục từ **đúng key** đang xử lý; nếu lấy nhầm thư mục, đường dẫn ghép sẽ sai.
- `WatchEvent.context()` được khai báo kiểu generic `T`, nhưng danh sách `pollEvents()` có kiểu `WatchEvent<?>`. Không nên cast `Path` với `OVERFLOW`: context của `OVERFLOW` có thể là `null`.
- `ENTRY_DELETE` thông báo một entry từng tồn tại bị xóa; sau đó `Files.exists(changed)` có thể false, không thể mặc định mở nội dung.
- `ENTRY_CREATE`/`ENTRY_MODIFY` không bảo đảm file còn tồn tại hoặc vẫn ở trạng thái lúc sự kiện xảy ra khi code xử lý.
- `normalize()` chỉ chuẩn hóa cú pháp `.`/`..`; không chứng minh một đường dẫn có được phép truy cập hay tránh escape qua symbolic links. Với đường dẫn không đáng tin, bổ sung kiểm tra an toàn phù hợp.

### Nên giữ `Path` tuyệt đối ngay khi đăng ký

Ví dụ:

```java
Path watchedDir = Path.of("inbox").toAbsolutePath().normalize();
WatchKey key = watchedDir.register(watcher,
        StandardWatchEventKinds.ENTRY_CREATE);
```

Nếu dùng thư mục relative `Path.of("inbox")` để đăng ký, `key.watchable()` có thể là `Path` relative giống giá trị đã đăng ký. Khi dùng tiếp trong một hệ thống phức tạp hoặc lưu log, dạng absolute normalized thường giúp tránh nhầm với đường dẫn ở subsystem khác. Nó vẫn không loại bỏ mọi race condition/symlink.

### Mô hình kết hợp nhiều thư mục

```text
WatchService
   |
   +--> WatchKey A --> watchable() = /data/a
   |                          context() = first.txt
   |                          resolved = /data/a/first.txt
   |
   +--> WatchKey B --> watchable() = /data/b
                              context() = second.txt
                              resolved = /data/b/second.txt
```

Cả hai event đều có thể dùng cùng một tên `output.log`. Chỉ dùng `context().toString()` làm khóa định danh duy nhất trên toàn bộ watcher là sai nếu đang theo dõi nhiều thư mục. Khóa có thể cần bao gồm thư mục/watch registration cộng relative context.

## 7. Ví dụ Java 17+ chạy được: quan sát event và ghép `Path`

Ví dụ đầu tiên tạo thư mục tạm, đăng ký watch, tạo file `note.txt`, chờ `ENTRY_CREATE` rồi in loại sự kiện, tên tương đối và kết quả giải quyết vào thư mục gốc. Lệnh chỉ in event **đúng tên** nên không phụ thuộc vào số `ENTRY_MODIFY` bổ sung được OS phát ra.

Lưu thành **`WatchKeyRelativePathDemo.java`**:

```java
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;

public class WatchKeyRelativePathDemo {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("watch-relative-")
                        .toAbsolutePath().normalize();
        Path file = dir.resolve("note.txt");

        try {
            try (WatchService watcher =
                         FileSystems.getDefault().newWatchService()) {

                dir.register(watcher,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY);

                Files.writeString(file, "hello");
                boolean found = false;
                long deadline = System.nanoTime()
                        + TimeUnit.SECONDS.toNanos(5);

                while (!found) {
                    long left = deadline - System.nanoTime();
                    if (left <= 0) {
                        throw new IOException("ENTRY_CREATE not reported");
                    }

                    WatchKey key = watcher.poll(
                            left, TimeUnit.NANOSECONDS);
                    if (key == null) {
                        throw new IOException("Timed out waiting for event");
                    }

                    Path watchedDir = (Path) key.watchable();

                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind()
                                == StandardWatchEventKinds.OVERFLOW) {
                            // Ứng dụng thật cần rescan thư mục.
                            continue;
                        }

                        if (event.kind()
                                != StandardWatchEventKinds.ENTRY_CREATE) {
                            continue;
                        }

                        // Với entry events của Path watcher, context là Path.
                        Path relative = (Path) event.context();
                        if (relative.equals(Path.of("note.txt"))) {
                            Path changed = watchedDir.resolve(relative)
                                    .toAbsolutePath().normalize();
                            System.out.println("kind="
                                    + event.kind().name());
                            System.out.println("relative=" + relative);
                            System.out.println("resolvedMatches="
                                    + changed.equals(file));
                            found = true;
                        }
                    }

                    boolean valid = key.reset();
                    if (!valid) {
                        throw new IOException("Directory registration invalid");
                    }
                }
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}
```

Chạy:

```bash
javac WatchKeyRelativePathDemo.java
java WatchKeyRelativePathDemo
```

**Stdout điển hình khi filesystem/provider báo `ENTRY_CREATE` trong thời hạn:**

```text
kind=ENTRY_CREATE
relative=note.txt
resolvedMatches=true
```

Đây là output **có điều kiện**, không phải lời hứa mọi platform đều phát event giống nhau. Nếu provider không gửi `ENTRY_CREATE` trong năm giây, chương trình ném `IOException` nêu rõ lý do. Các event `ENTRY_MODIFY` bổ sung không được in nên không gây sai dữ liệu mẫu.

Vì sao `resolvedMatches=true`? Khi đăng ký, `dir` đã là absolute normalized. `event.context()` cho file mới là `Path.of("note.txt")` tương đối với `dir`. `watchedDir.resolve(relative)` ghép lại đúng đường dẫn `file` đã dùng để tạo entry.

Ví dụ còn thể hiện `key.reset()` **sau khi lấy và xử lý danh sách sự kiện**. Không gọi `reset()` sớm khi chương trình vẫn chưa quyết định xử lý payload vì có thể phức tạp hóa việc điều phối event ở ứng dụng thực tế.

## 8. Ví dụ Java 17+ chạy được: một service, nhiều thư mục

Khi theo dõi nhiều folder, `key.watchable()` hoặc một `Map<WatchKey, Path>` là cách lấy root đúng. Nếu mỗi thư mục dùng một watcher riêng sẽ tốn thêm tài nguyên và gây khó quản lý; một service có thể đủ cho một nhóm thư mục cùng filesystem/provider.

Ví dụ có hai thư mục `a` và `b`. Chương trình cố ý sắp xếp kết quả trước khi in để output không phụ thuộc vào thứ tự watcher giao key.

Lưu file **`MultiDirectoryWatchDemo.java`**:

```java
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

public class MultiDirectoryWatchDemo {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("multi-watch-");
        Path dirA = Files.createDirectory(root.resolve("a"));
        Path dirB = Files.createDirectory(root.resolve("b"));
        Path fileA = dirA.resolve("first.txt");
        Path fileB = dirB.resolve("second.txt");

        try {
            try (WatchService watcher =
                         FileSystems.getDefault().newWatchService()) {

                Map<WatchKey, Path> roots = new HashMap<>();
                roots.put(dirA.register(watcher,
                        StandardWatchEventKinds.ENTRY_CREATE), dirA);
                roots.put(dirB.register(watcher,
                        StandardWatchEventKinds.ENTRY_CREATE), dirB);

                Files.createFile(fileA);
                Files.createFile(fileB);

                Set<String> found = new TreeSet<>();
                long deadline = System.nanoTime()
                        + TimeUnit.SECONDS.toNanos(5);

                while (found.size() < 2) {
                    long left = deadline - System.nanoTime();
                    if (left <= 0) {
                        throw new IOException("Missing create notifications");
                    }

                    WatchKey key = watcher.poll(
                            left, TimeUnit.NANOSECONDS);
                    if (key == null) {
                        throw new IOException("Watcher timeout");
                    }

                    Path watched = roots.get(key);
                    if (watched == null) {
                        throw new IOException("Unknown watch key");
                    }

                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind()
                                == StandardWatchEventKinds.OVERFLOW) {
                            throw new IOException(
                                    "Overflow; directory rescan required");
                        }

                        if (event.kind()
                                != StandardWatchEventKinds.ENTRY_CREATE) {
                            continue;
                        }

                        Path relative = (Path) event.context();
                        Path changed = watched.resolve(relative);

                        if (changed.equals(fileA)) {
                            found.add("a/" + relative);
                        } else if (changed.equals(fileB)) {
                            found.add("b/" + relative);
                        }
                    }

                    if (!key.reset()) {
                        roots.remove(key);
                        throw new IOException("Watched directory invalid");
                    }
                }

                for (String item : found) {
                    System.out.println(item);
                }
                System.out.println("createdEntries=" + found.size());
            }
        } finally {
            Files.deleteIfExists(fileA);
            Files.deleteIfExists(fileB);
            Files.deleteIfExists(dirA);
            Files.deleteIfExists(dirB);
            Files.deleteIfExists(root);
        }
    }
}
```

```bash
javac MultiDirectoryWatchDemo.java
java MultiDirectoryWatchDemo
```

**Stdout khi cả hai thông báo được nhận thành công:**

```text
a/first.txt
b/second.txt
createdEntries=2
```

Điểm học được:

- `Map<WatchKey, Path>` gắn registration với thư mục của chính nó. `key.watchable()` cũng có thể được cast thành `Path` vì các key trong ví dụ được tạo từ `Path.register()`.
- Hai key có thể được watcher trả về theo bất kỳ thứ tự nào. `TreeSet` sắp xếp để minh họa output ổn định khi cùng hai entry đã được phát hiện.
- `Files.createFile` tạo file trống; event `ENTRY_CREATE` ở đây chỉ cần xác định việc tạo entry, không cần đọc nội dung.
- Khi một key invalid, code loại nó khỏi `roots` và báo lỗi. Ứng dụng vận hành lâu dài phải quyết định đăng ký lại hoặc ngừng theo dõi thư mục tương ứng.
- Đây không phải hệ thống theo dõi sản xuất có thể tự hồi phục sau `OVERFLOW`. Trong trường hợp đó, **phải quét lại trạng thái** và đối chiếu với snapshot của ứng dụng.

**Chú ý:** thứ tự thông báo OS/provider, thời điểm sẵn sàng và độ hỗ trợ `WatchService` phụ thuộc môi trường. Nếu không nhận đủ event, chương trình ném lỗi thay vì in kết quả thành công giả. Việc in sắp xếp không làm một provider thiếu event trở nên đáng tin hơn.

## 9. `OVERFLOW` và `count()`: tại sao không coi event là nhật ký đầy đủ?

Các thay đổi filesystem có thể xảy ra dồn dập, ví dụ một tiến trình chép hàng nghìn file vào thư mục hoặc chương trình khác sửa cùng file rất nhiều lần. Watch service có thể:

1. Gộp nhiều sự kiện có cùng kind và context thành một `WatchEvent` với `count()` lớn hơn 1.
2. Không phân biệt hết mọi thay đổi nếu hệ thống/provider không có chi tiết ấy.
3. Thông báo `OVERFLOW` khi sự kiện đã bị mất hoặc bỏ qua vì quá nhiều sự kiện.

Ví dụ kiểm tra event:

```java
for (WatchEvent<?> event : key.pollEvents()) {
    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
        System.err.println("Changes were lost; rescan required");
        continue;
    }

    System.out.println(event.kind().name()
            + " " + event.context()
            + " count=" + event.count());
}
```

Đây là snippet chỉ dành cho minh họa. `count()` là số lần sự kiện cụ thể được tích lũy/gộp, **không** phải số file khác nhau bị tác động. Nếu nhận `ENTRY_MODIFY` với `count=5`, không kết luận chính xác có năm lần người dùng lưu file theo ý nghĩa nghiệp vụ.

### Chiến lược đúng sau `OVERFLOW`

- Quét lại thư mục/thư mục con cần theo dõi.
- Đối chiếu snapshot hiện tại (tên, kích thước, timestamp, checksum nếu thật sự cần) với trạng thái đã biết.
- Đồng bộ lại cache/chỉ mục công việc hoặc đánh dấu cần kiểm tra lại.
- Tiếp tục xử lý các event mới và `reset()` theo trạng thái key.
- Nếu cần audit tuyệt đối mọi lần đổi tên/nội dung theo thứ tự, `WatchService` **không đủ**; cần giao thức sự kiện hay nhật ký chuyên dụng do nguồn phát thay đổi quản lý.

`OVERFLOW` không nên được cast `event.context()` sang `Path`. Với event loại này context có thể `null`, và không có “file cụ thể” nào bảo đảm khôi phục được chỉ từ bản thân event.

## 10. `reset()`, hủy key và đóng service

Ba hành động bị nhầm:

| Thao tác | Ý nghĩa |
| --- | --- |
| `key.pollEvents()` | Lấy danh sách event đang đợi của key, không tự re-arm key |
| `key.reset()` | Xác nhận đã xử lý và cho key cơ hội được signal ở lượt tiếp; trả false nếu invalid |
| `key.cancel()` | Hủy đăng ký; không xóa directory hoặc đóng watcher |
| `watcher.close()` | Đóng toàn bộ WatchService, không còn dùng để nhận key |
| `key.isValid()` | Kiểm tra key hiện còn hợp lệ; không xác nhận dữ liệu event đầy đủ |

Mẫu xử lý chuẩn:

```java
while (running) {
    WatchKey key = watcher.take();
    try {
        for (WatchEvent<?> event : key.pollEvents()) {
            // Xử lý sự kiện, kể cả OVERFLOW.
        }
    } finally {
        boolean valid = key.reset();
        if (!valid) {
            // Loại key khỏi map registration, xử lý directory đã mất...
        }
    }
}
```

**Snippet không độc lập**; `running`, `watcher` và xử lý ngoại lệ do ứng dụng cung cấp. Mẫu này nhấn mạnh reset sau khi tiêu thụ event, ngay cả khi xử lý gặp vấn đề có thể khôi phục. Với chương trình thật, ngoại lệ nặng có thể dẫn đến đóng watcher thay vì tiếp tục.

Khi `reset()` trả `false`:

- Key không còn được dùng để chờ thay đổi mới.
- Có thể directory đã bị xóa, resource không còn truy cập được hoặc registration bị cancel.
- Loại nó khỏi `Map<WatchKey, Path>`, đóng các resource liên quan nếu không còn cần.
- Nếu directory xuất hiện trở lại, phải có cơ chế thích hợp để **đăng ký lại**; key cũ không tự sống lại.

Đừng coi `reset() == false` là một tín hiệu đủ để suy ra **lý do duy nhất** là directory bị xóa. Lý do cụ thể có thể phụ thuộc provider/lifecycle.

## 11. Path tương đối, thư mục công tác và tránh ghép sai

### 11.1. Không phải relative với current working directory

Ví dụ:

```text
Current working directory:  /home/app
Watched directory:          /home/data/input
WatchEvent.context():       report.csv
```

Sai về mặt ý nghĩa:

```java
Path wrong = Path.of(event.context().toString());
// Tại đây wrong được diễn giải theo working directory,
// không phải thư mục watcher đã đăng ký.
```

Đúng:

```java
Path watchedDir = (Path) key.watchable();
Path changed = watchedDir.resolve((Path) event.context());
```

Hai đoạn snippet trên chỉ minh họa, cần event entry có Path context. Nếu chương trình chạy trong thư mục khác, cách đúng vẫn lấy root từ key chứ không dựa vào `user.dir`.

### 11.2. Có thể chọn lưu mapping riêng

Nếu ứng dụng cần thêm metadata cho mỗi đăng ký (mã dự án, loại xử lý, snapshot), dùng cấu trúc:

```java
Map<WatchKey, Path> dirByKey = new HashMap<>();
WatchKey key = watchedDir.register(watcher,
        StandardWatchEventKinds.ENTRY_CREATE);
dirByKey.put(key, watchedDir);
```

Trong các hàm xử lý khác, lấy `watchedDir = dirByKey.get(key)`. Cần loại mapping khi key invalid hoặc service đóng. Không suy ra key cũ hợp lệ sau khi directory bị xóa và tạo lại.

### 11.3. Đường dẫn đã ghép không đảm bảo file còn tồn tại

Một watcher báo `ENTRY_CREATE`, nhưng khi handler chạy, file có thể đã bị xóa hoặc rename. Với `ENTRY_DELETE`, file hầu như thường không mở được nữa. Vì vậy quy trình tốt:

1. Tạo đường dẫn bằng đúng root và context.
2. Nếu định đọc, kiểm tra loại sự kiện và truy cập file với exception handling thực tế.
3. Chấp nhận rằng `Files.exists` rồi `Files.readAllBytes` vẫn có race giữa hai lời gọi.
4. Nếu nghiệp vụ cần bảo đảm file “đã hoàn tất ghi”, hãy có protocol publish rõ ràng (ví dụ tạo tệp tạm, ghi xong, đổi tên) thay vì dựa vào một `ENTRY_MODIFY`.
5. Với đầu vào không tin cậy, kiểm tra đường dẫn, symlink và quyền truy cập; không lấy `normalize()` làm biện pháp bảo mật duy nhất.

## 12. Không theo dõi đệ quy mặc định

Watch service thường phát event cho **entry trực tiếp** của directory đăng ký, không phải mọi file dưới các directory con.

```text
root/                      <- đăng ký WatchKey R
  a.txt                    <- event ở R
  nested/                  <- tạo nested -> event ở R
    b.txt                  <- sửa b.txt KHÔNG được R tự động
                               hứa phát event
```

Để theo dõi cả cây thư mục:

- Đầu tiên duyệt những directory hiện có và đăng ký từng directory.
- Khi nhận `ENTRY_CREATE` cho một directory mới, đăng ký directory đó và duyệt những directory con đã có.
- Khi nhận key invalid, loại mapping tương ứng.
- Xử lý race trong lúc quét/đăng ký: file có thể xuất hiện, biến mất hoặc thay đổi giữa các bước.
- Đối phó `OVERFLOW` bằng rescan; không giả định mọi thư mục mới đều đã được đăng ký đầy đủ nếu bị mất sự kiện.

Chức năng watch thư mục đệ quy cần thêm logic đáng kể; không có cờ `recursive=true` tiêu chuẩn của `Path.register(...)` cho mọi provider Java 17. Một số provider có modifier riêng, nhưng không lấy đặc tính riêng của platform làm cam kết portable.

## 13. Event name và hành vi nhà cung cấp

### `ENTRY_CREATE`

Một entry xuất hiện trong thư mục. Đây không nhất thiết là “file cuối cùng đã ghi đủ và sẵn sàng parse”. Một công cụ có thể tạo file rồi ghi tiếp trong nhiều lần. Để xử lý nội dung hoàn chỉnh, cần giao thức với nhà sản xuất file, ví dụ ghi file tạm và rename khi hoàn tất.

### `ENTRY_MODIFY`

Một entry bị sửa. Số lượng event có thể khác nhau theo OS/provider, kể cả nhiều event trong một thao tác nghiệp vụ hoặc event được gộp. Đừng coi `count()` là số lần lưu văn bản.

### `ENTRY_DELETE`

Entry bị xóa. Path context mô tả **tên entry đã biến mất**; thường không còn đối tượng để mở. Việc rename/move có thể được báo như delete ở một thư mục và create ở thư mục khác, nhưng không có yêu cầu portable rằng các sự kiện được liên kết thành một giao dịch rename duy nhất.

### `OVERFLOW`

Thay đổi có thể đã bị mất. Rescan; không có entry Path bắt buộc trong context.

### Phụ thuộc filesystem/provider

`WatchService` có thể được triển khai bằng notification native của OS hoặc bằng polling. Độ trễ, khả năng hỗ trợ, tính liên tục khi mất kết nối mạng, cách theo dõi mount từ xa, và việc xử lý symlink khác nhau theo provider.

Không thiết kế kiểm thử portable yêu cầu:

- Mọi lần ghi phát đúng một `ENTRY_MODIFY`.
- Mọi thao tác rename tạo event theo thứ tự tuyệt đối được bảo đảm.
- Có một thời gian cố định tính bằng mili-giây cho việc phát event.
- Mọi filesystem mạng hoạt động như ổ local.
- `pollEvents()` là audit trail đầy đủ của tất cả thay đổi.

Bản thân một thông báo chỉ nói “hãy quan sát lại state liên quan”, không thay thế API đọc/kiểm tra dữ liệu.

## 14. Quản lý các event trong hệ thống thật

Trong ứng dụng import file, thường phải xử lý một lượng lớn thay đổi nhanh. Cách tổ chức hữu ích:

1. Một thread watcher dùng `take()` hoặc `poll(timeout)` để lấy key.
2. Duyệt `pollEvents()`, xác định root qua key, lấy relative context và tạo Path hoàn chỉnh.
3. Chuyển việc đọc file/giải mã/parse tốn thời gian sang executor có giới hạn, tránh khiến watcher loop bỏ lỡ quá nhiều event.
4. `reset()` và kiểm tra kết quả; loại registration không hợp lệ.
5. Khi `OVERFLOW`, xếp lịch rescan có giới hạn và đồng bộ lại trạng thái.
6. Thiết kế tính idempotent (xử lý trùng event vẫn cho kết quả hợp lệ), snapshot/checksum nếu cần và backpressure cho hàng đợi.
7. Bảo đảm đóng `WatchService` và worker pool trong quy trình shutdown.

Nếu callback xử lý `ENTRY_MODIFY` quá chậm trước khi reset key, các thay đổi tiếp theo có thể bị dồn lại/gộp; queue của watcher có thể tràn. Điều phối watcher loop nhẹ nhàng và tách phần việc tốn thời gian thường là quyết định tốt.

**Lưu ý đặc biệt về race:** đưa một Path vào queue để worker mở sau đó **không** bảo đảm worker sẽ thấy chính nội dung file tại thời điểm event. Nếu cần version nhất quán, producer và consumer phải có protocol phối hợp (rename file hoàn thiện, manifest, khóa phù hợp hoặc content-addressed artifact).

## 15. Các lỗi thường gặp và cách sửa

1. **Nhầm `watcher.poll()` với `key.pollEvents()`.** Một cái lấy key; cái kia lấy sự kiện của key.
2. **Quên `key.reset()`.** Sau một đợt signal, key có thể không được nhận lại như mong muốn.
3. **Gọi `key.cancel()` thay `reset()`.** Cancel hủy theo dõi, không phải xác nhận đã xử lý.
4. **Giả định `event.context()` luôn là absolute Path.** Với entry event của directory, nó tương đối với watched directory; phải resolve đúng root.
5. **Resolve với working directory thay vì `key.watchable()`.** Có thể mở nhầm file ở location khác.
6. **Cast context sang Path trước khi kiểm tra `OVERFLOW`.** Context overflow có thể null; phân nhánh theo kind trước.
7. **Bỏ qua `WatchEvent.count()`.** Không cần xử lý lại nhiều lần bằng mọi giá, nhưng cần hiểu event có thể bị gộp; count không phải số file.
8. **Không xử lý `OVERFLOW`.** Có nguy cơ bỏ lỡ file; phải rescan khi yêu cầu tính đúng đắn.
9. **Nhìn thấy `ENTRY_MODIFY` rồi coi file đã ghi xong.** Có thể là file đang được ghi; cần protocol hoàn tất.
10. **Giả định watcher đệ quy.** Phải đăng ký directory con nếu cần theo dõi sâu.
11. **Dùng một watched root cho mọi key.** Với nhiều thư mục, cùng relative filename có thể dẫn đến chọn nhầm tệp.
12. **Cố mở file sau `ENTRY_DELETE`.** Entry có thể không còn; cập nhật chỉ mục bằng path/context đã biết.
13. **Không kiểm tra `reset() == false`.** Key không còn valid thì phải loại khỏi map và quyết định re-register.
14. **Chặn watcher loop bằng xử lý dài.** Event bị dồn/gộp/tràn; tách sang worker executor có giới hạn.
15. **Dùng watcher như hệ thống audit tuyệt đối.** OS/provider có thể gộp/mất event; muốn audit cần nguồn thay đổi có log đáng tin riêng.
16. **Không đóng `WatchService`.** Resource và thread đang chờ có thể tồn tại lâu hơn dự kiến; cần chiến lược shutdown.
17. **Suy luận thời gian sự kiện bằng thứ tự tuyệt đối.** Thông báo có thể đến sau thay đổi, giữa lúc state đã biến động tiếp.
18. **Dùng `normalize()` để thay việc bảo vệ path.** Chuẩn hóa cú pháp không xử lý hết symlink/race và quyền truy cập.

## 16. Bài tập thực hành

### Bài A — xem sự khác nhau giữa hai loại poll

Tạo watcher cho một thư mục tạm. Gọi `watcher.poll()` khi chưa tạo file và quan sát rằng có thể trả `null`. Sau khi tạo file và nhận `WatchKey`, gọi `key.pollEvents()` rồi thử gọi lần nữa ngay. Tại sao danh sách lần hai thường rỗng? Sau xử lý, hàm nào cần gọi để tiếp tục theo dõi?

### Bài B — kiểm tra context tương đối

Giả sử `key.watchable()` trả `/tmp/receive` và event context là `data.csv`. Đường dẫn nào đúng để đọc file? Vì sao `Path.of("data.csv")` không tương đương nếu chương trình chạy tại `/home/app`?

### Bài C — nhiều registration

Đăng ký `dirA` và `dirB` trên cùng một `WatchService`. Tạo file cùng tên `data.json` trong cả hai thư mục. Hãy xây `Map<WatchKey, Path>` và in các path sau khi resolve sao cho không nhầm lẫn hai file.

### Bài D — tình huống `OVERFLOW`

Giả sử watcher bỏ lỡ một số `ENTRY_CREATE` vì queue quá tải. Hãy giải thích tại sao `event.count()` không đủ để khôi phục danh sách file đã tạo, và viết các bước quét lại/đối chiếu snapshot.

### Bài E — thiếu tính recursive

Bạn đăng ký `inbox`, sau đó chương trình khác tạo `inbox/newFolder/item.bin`. Vì sao watcher của `inbox` chưa đủ để đảm bảo nhận event tạo `item.bin`? Bạn sẽ đăng ký thêm root nào và cần xử lý race ra sao?

### Bài F — lifecycle và shutdown

Một watcher thread đang chặn trong `take()`. Thread điều phối muốn tắt ứng dụng. Nên quản lý `WatchService.close()`, xử lý `ClosedWatchServiceException` và đóng executor ra sao để không bỏ quên resource?

## 17. Câu hỏi tự kiểm tra

1. `Path.register(watcher, kinds...)` trả kiểu gì, và `WatchKey.watchable()` thường đại diện cho gì?
2. `WatchService.poll()` và `WatchKey.pollEvents()` khác nhau về object trả về và nhiệm vụ như thế nào?
3. `WatchEvent.context()` với `ENTRY_CREATE` trên directory thường là Path absolute hay relative? Relative với đâu?
4. Nếu chương trình giám sát ba thư mục, làm sao xác định Path đầy đủ cho cùng một tên `log.txt` từ ba key khác nhau?
5. Vì sao phải kiểm tra `event.kind() == OVERFLOW` trước khi cast context sang `Path`?
6. `pollEvents()` có xóa key khỏi watch service hay tự `reset` không?
7. `key.reset()` trả `false` mang ý nghĩa gì? Có bắt buộc chứng minh thư mục bị xóa không?
8. `WatchEvent.count()` có đồng nghĩa số file thay đổi không?
9. Nếu file bị tạo rồi xóa trước khi watcher xử lý `ENTRY_CREATE`, có thể mặc định `Files.readAllBytes` thành công không?
10. `WatchService` có bảo đảm quan sát toàn bộ cây directory một cách đệ quy trên mọi provider không?
11. Sự khác nhau giữa `key.cancel()` và `watcher.close()` là gì?
12. `ENTRY_MODIFY` có bảo đảm producer đã đóng file, dữ liệu không còn thay đổi nữa không?
13. Vì sao một watcher nhập dữ liệu nên có chiến lược rescan/snapshot khi `OVERFLOW`?
14. `watcher.take()` có thể chặn; làm sao kết thúc thread đang chờ khi ứng dụng shutdown?
15. Tại sao không dùng event watcher làm cơ chế audit thay thế log giao dịch có thứ tự?

## 18. Tài liệu Java SE 17

- [`WatchService`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/WatchService.html): `take()`, `poll()`, `poll(timeout, unit)`, vòng đời dịch vụ.
- [`WatchKey`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/WatchKey.html): `pollEvents()`, `reset()`, `cancel()`, `watchable()`, `isValid()`.
- [`WatchEvent`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/WatchEvent.html): `kind()`, `context()`, `count()`.
- [`StandardWatchEventKinds`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardWatchEventKinds.html): `ENTRY_CREATE`, `ENTRY_MODIFY`, `ENTRY_DELETE`, `OVERFLOW`.
- [`Path.register`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html): đăng ký Path với WatchService.

## 19. Ghi nhớ

**`WatchService` trả `WatchKey`; `WatchKey.pollEvents()` trả danh sách `WatchEvent`.** Với sự kiện thay đổi entry trong thư mục, `event.context()` là tên/đường dẫn **tương đối so với thư mục đã đăng ký**, vì vậy phải ghép với `(Path) key.watchable()` hoặc mapping root tương ứng.

Sau khi tiêu thụ event, gọi **`key.reset()`** và kiểm tra kết quả để tiếp tục theo dõi. Phân biệt `reset()` với `cancel()`, xử lý `OVERFLOW` bằng quét lại trạng thái, không giả định watch là recursive hay ghi nhận đầy đủ mọi thay đổi. Watcher là **cơ chế nhận tín hiệu để kiểm tra filesystem**, không phải bản sao filesystem hay nhật ký kiểm toán tuyệt đối.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="watch-service-004">take, poll và vòng xử lý sự kiện</a>

<details>
<summary>Xem chi tiết</summary>

# Java WatchService: take, poll và vòng xử lý sự kiện

## 1. Bài toán và phạm vi

Khi ứng dụng cần biết một thư mục có tệp mới, tệp bị sửa hoặc một mục bị xóa, Java NIO.2 cung cấp **`WatchService`** trong package `java.nio.file`. Thay vì liên tục quét lại cả thư mục bằng `Files.list(...)`, ứng dụng có thể đăng ký theo dõi và tiêu thụ các tín hiệu thay đổi thông qua **`WatchKey`**.

Ba cách lấy key phổ biến là:

- `watchService.take()`: **chờ** đến khi có key được báo hiệu.
- `watchService.poll()`: **kiểm tra ngay**; nếu chưa có key thì trả `null`.
- `watchService.poll(timeout, unit)`: **chờ có giới hạn thời gian**; hết hạn mà chưa có key thì trả `null`.

Đề bài “take, poll và vòng xử lý sự kiện” không chỉ là học ba cú pháp. Một vòng theo dõi đúng còn phải lấy danh sách `pollEvents()`, xử lý đường dẫn tương đối, luôn đưa key trở lại trạng thái sẵn sàng qua `reset()`, chú ý `OVERFLOW`, và đóng dịch vụ hợp lý khi dừng.

**Mục tiêu học tập:** hiểu chu trình đăng ký → chờ key → xử lý events → reset → lặp; chọn `take` hay `poll` theo yêu cầu latency và shutdown; tránh mất sự kiện do sử dụng sai `WatchKey`; xây một chương trình Java 17+ có thể chạy thử.

## 2. Các thành phần cốt lõi

| Thành phần | Ý nghĩa |
|---|---|
| `FileSystems.getDefault().newWatchService()` | Tạo dịch vụ theo dõi filesystem mặc định |
| `Path.register(watcher, kinds...)` | Đăng ký thư mục và các loại sự kiện quan tâm |
| `WatchKey` | Phiếu đăng ký nhận tín hiệu; liên kết với một đối tượng được theo dõi |
| `watchService.take()` | Chờ một key đã được báo hiệu |
| `watchService.poll()` | Lấy key báo hiệu ngay nếu có |
| `watchService.poll(timeout, unit)` | Lấy key nếu có trong thời hạn |
| `key.pollEvents()` | Lấy và xóa danh sách sự kiện đang chờ trong key |
| `key.reset()` | Đặt lại key để tiếp tục nhận báo hiệu, trả `false` nếu không còn hợp lệ |
| `key.watchable()` | Trả về đối tượng đã đăng ký (thường là `Path` thư mục) |
| `key.cancel()` | Hủy đăng ký của key |
| `watchService.close()` | Đóng dịch vụ và giải phóng tài nguyên |

Các loại sự kiện chuẩn thường dùng:

- `StandardWatchEventKinds.ENTRY_CREATE`: một mục xuất hiện trực tiếp trong thư mục đăng ký.
- `StandardWatchEventKinds.ENTRY_MODIFY`: một mục trực tiếp có thay đổi được provider nhận biết.
- `StandardWatchEventKinds.ENTRY_DELETE`: một mục trực tiếp bị xóa/biến mất.
- `StandardWatchEventKinds.OVERFLOW`: dấu hiệu sự kiện có thể đã bị mất/bị loại bỏ, cần đồng bộ lại trạng thái bằng cách quét thực tế.

Các loại `ENTRY_*` thường mang `WatchEvent<Path>`; `context()` là **tên/đường dẫn tương đối từ thư mục đã đăng ký**, không phải đường dẫn tuyệt đối. `OVERFLOW` có ngữ nghĩa khác và không được giả định cung cấp một Path hợp lệ.

## 3. Đăng ký trước khi đợi sự kiện

Ví dụ đăng ký một thư mục đã tồn tại:

```java
import java.nio.file.*;
import static java.nio.file.StandardWatchEventKinds.*;

Path directory = Path.of("inbox");
try (WatchService watcher =
         FileSystems.getDefault().newWatchService()) {

    WatchKey key = directory.register(
            watcher, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
    // Tiêu thụ key bằng take/poll, đọc events và reset ở đây.
}
```

Đây là đoạn minh họa; mã cần đặt trong một method/class, thư mục `inbox` phải tồn tại và provider phải hỗ trợ đăng ký. Thường tạo thư mục trước bằng `Files.createDirectories(directory)` nếu nghiệp vụ cho phép.

- `WatchService` **không tự động theo dõi mọi thư mục trong hệ thống**. Đăng ký rõ từng thư mục.
- `Path.register` trả `WatchKey` cho lần đăng ký; bản thân nó **không phải** một lần gọi chờ.
- Loại sự kiện phụ thuộc provider. Một số filesystem hoặc môi trường chạy không hỗ trợ đầy đủ; khi đó có thể gặp `UnsupportedOperationException` hoặc ngoại lệ I/O.
- Đừng đăng ký path là tệp thông thường rồi giả định hành vi như directory watch trên mọi provider: ví dụ cơ bản nên theo dõi **thư mục chứa tệp**.
- Quyền truy cập và nền tảng (filesystem cục bộ, remote mount, container...) có thể ảnh hưởng sự kiện thực tế.

## 4. Chu trình sống của WatchKey

`WatchKey` có trạng thái **ready** và **signalled**. Khi có thay đổi, provider ghi nhận sự kiện vào key tương ứng rồi key có thể được đưa vào hàng đợi của `WatchService` để ứng dụng lấy ra.

```text
Đăng ký thư mục
      |
      v
Key READY  <------------------------+
      |                              |
Có event được ghi nhận              |
      v                              |
Key SIGNALLED, được enqueue         |
      |                              |
      v                              |
take() hoặc poll() lấy key           |
      |                              |
      v                              |
pollEvents(): lấy events đang chờ     |
      |                              |
      v                              |
Xử lý từng event                     |
      |                              |
      v                              |
reset() ------------------------------+
      |
      +-- false: key không hợp lệ -> bỏ đăng ký / dừng / phục hồi
```

Một `WatchKey` có thể tích lũy **nhiều events** trước khi ứng dụng gọi `pollEvents()`. Nếu một key vẫn đang signalled, các tín hiệu bổ sung được tích lũy theo cơ chế provider thay vì bảo đảm mỗi event lại tạo một key riêng trong hàng đợi.

**Bước `reset()` là bắt buộc về mặt chu trình xử lý**: sau khi lấy key, ứng dụng nên đọc events rồi reset để key có thể tiếp tục được thông báo đúng cách. Nếu không reset, vòng xử lý có thể không nhận được các tín hiệu tiếp theo như dự tính.

`reset()` trả `boolean`:

- `true`: key vẫn hợp lệ để tiếp tục theo dõi.
- `false`: key không còn hợp lệ, ví dụ đăng ký đã bị hủy, đối tượng không còn đủ điều kiện theo dõi, hoặc service đã đóng.

Khi `reset() == false`, cần bỏ key khỏi cấu trúc quản lý, xem xét đăng ký lại thư mục nếu nó được tạo lại và policy cho phép; không tiếp tục giả định key vẫn hoạt động.

## 5. `take()`: chờ cho đến khi có key

Chữ ký:

```java
WatchKey key = watcher.take();
```

`take()` là thao tác **blocking**: nếu chưa có key signalled, thread gọi sẽ chờ. Khi có key, nó trả key để ứng dụng lấy danh sách events.

Phù hợp khi:

- Có một worker chuyên theo dõi filesystem và thường không cần làm việc khác trong lúc chờ.
- Muốn tránh busy loop liên tục gọi `poll()`.
- Shutdown đã được thiết kế rõ bằng interrupt/đóng WatchService và xử lý ngoại lệ.

`take()` có thể ném `InterruptedException` nếu thread đang chờ bị interrupt. Sau khi service bị đóng, thao tác có thể ném `ClosedWatchServiceException`. Trong thiết kế event loop, hãy xử lý shutdown chủ động, không coi mọi ngoại lệ này là lỗi dữ liệu.

Ví dụ minh họa **không độc lập**:

```java
try {
    while (!Thread.currentThread().isInterrupted()) {
        WatchKey key = watcher.take();
        try {
            processEvents(key);
        } finally {
            if (!key.reset()) {
                break;
            }
        }
    }
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
} catch (ClosedWatchServiceException ex) {
    // Service đã đóng, thường là một nhánh shutdown bình thường.
}
```

Biến `watcher` và hàm `processEvents` phải được khai báo ở chỗ khác. Trong chương trình thật, dùng map `WatchKey -> Path` và quyết định xử lý trường hợp một trong nhiều key không còn hợp lệ; không phải lúc nào một key invalid cũng buộc dừng toàn bộ service.

## 6. `poll()`: kiểm tra không chờ

```java
WatchKey key = watcher.poll();
if (key == null) {
    // Không có key signalled tại thời điểm kiểm tra.
}
```

`poll()` **trả ngay**:

- Có key → trả key cho vòng xử lý.
- Không có key → trả `null`.
- Service đã đóng → có thể ném `ClosedWatchServiceException`.

Phù hợp khi ứng dụng đã có một main loop làm việc khác và chỉ muốn kiểm tra sự kiện định kỳ mà không đứng chờ.

**Bẫy:** viết một vòng vô hạn chỉ gọi `poll()` rồi kiểm tra `null` liên tục có thể tiêu thụ CPU lớn:

```java
// KHÔNG khuyến nghị: vòng quay nóng khi không có sự kiện.
while (running) {
    WatchKey key = watcher.poll();
    if (key == null) {
        continue;
    }
    // ...
}
```

Nếu dùng `poll()`, nên lồng vào một chu kỳ có công việc thật, giới hạn tần suất hoặc dùng bản `poll(timeout, unit)`. Không nên tự thêm `Thread.sleep(...)` dài và bỏ qua nhu cầu shutdown/độ trễ; thường timed poll diễn đạt ý định rõ hơn.

## 7. `poll(timeout, unit)`: chờ có thời hạn

```java
WatchKey key = watcher.poll(500, TimeUnit.MILLISECONDS);
```

Khác với `poll()`, overload này có thể chờ tối đa khoảng thời gian yêu cầu khi hàng đợi chưa có key. Nếu chưa có tín hiệu trước khi hết hạn, trả `null`. Nó có thể ném `InterruptedException` và `ClosedWatchServiceException` tương ứng.

Phù hợp với vòng xử lý cần:

1. Thường xuyên xử lý sự kiện thay đổi tệp.
2. Sau mỗi khoảng thời gian thực hiện công việc nền, ví dụ kiểm tra cờ dừng, flush metrics hoặc quét phòng khi missed events.
3. Có cơ chế dừng có giới hạn độ trễ mà không phải đóng service ngay.

Ví dụ:

```java
while (running) {
    WatchKey key = watcher.poll(1, TimeUnit.SECONDS);

    if (key == null) {
        // Sau tối đa 1 giây không có key: làm việc định kỳ nếu cần.
        continue;
    }

    processEvents(key);
    if (!key.reset()) {
        // Loại bỏ đăng ký này / quyết định retry.
    }
}
```

Đây là đoạn minh họa có `running` và `processEvents` cần được xác định bên ngoài. Không coi timeout bằng chứng rằng filesystem không thay đổi — một sự kiện có thể bị mất, gộp, chậm hoặc thư mục chưa được đăng ký.

## 8. So sánh nhanh ba cách lấy key

| Tiêu chí | `take()` | `poll()` | `poll(timeout, unit)` |
|---|---|---|---|
| Có block khi chưa có key? | Có, có thể chờ vô hạn | Không | Có, tối đa thời hạn |
| Khi không có key | Tiếp tục chờ | Trả `null` ngay | Trả `null` sau timeout |
| Thường phù hợp | Worker chuyên xử lý event | Tích hợp main loop đang có | Watch loop + tác vụ định kỳ |
| Chú ý CPU | Không busy spin khi chờ | Dễ busy spin nếu lặp liên tục | Thường giảm busy spin |
| Interrupt | Có thể ném `InterruptedException` | Không chờ nên không có checked interrupt | Có thể ném `InterruptedException` |
| Khi WatchService đóng | `ClosedWatchServiceException` | `ClosedWatchServiceException` | `ClosedWatchServiceException` |
| Cần `pollEvents()/reset()`? | Có | Có, nếu trả key | Có, nếu trả key |

Không có lựa chọn nào “luôn tốt nhất”. Chọn theo nhu cầu chờ, công việc đồng thời trong thread, yêu cầu độ trễ shutdown, và hệ thống thực tế. Phần **xử lý WatchKey** về cơ bản giống nhau cho cả ba cách.

## 9. Xử lý events trong WatchKey

Sau khi lấy key:

```java
for (WatchEvent<?> event : key.pollEvents()) {
    WatchEvent.Kind<?> kind = event.kind();

    if (kind == StandardWatchEventKinds.OVERFLOW) {
        // Cần rescan / đồng bộ trạng thái.
        continue;
    }

    Path relativeName = (Path) event.context();
    Path directory = (Path) key.watchable();
    Path changed = directory.resolve(relativeName);

    System.out.println(kind.name() + ": " + changed);
}
boolean valid = key.reset();
```

Đây là ví dụ minh họa cần import `Path`, `WatchEvent`, `StandardWatchEventKinds` và khai báo `key`. Điểm cần hiểu:

- `key.pollEvents()` trả danh sách sự kiện đang chờ của **key đó**, đồng thời bỏ các sự kiện được lấy khỏi danh sách chờ tương ứng.
- `event.kind()` giúp phân biệt create/modify/delete/overflow.
- Với sự kiện `ENTRY_*` đăng ký trên `Path`, `context()` thường là `Path` **tương đối**. `directory.resolve(relativeName)` mới tạo đường dẫn dưới thư mục đang theo dõi.
- Không dùng `event.context().toString()` để tạo đường dẫn tuyệt đối rồi đoán thư mục.
- `OVERFLOW` cần được xử lý **trước** việc ép `context()` sang `Path`, vì context của overflow không phải một tên tệp hợp lệ để resolve.
- `event.count()` cho biết số lần sự kiện tương tự được tích lũy/gộp theo semantics watch service; **không** nên suy ra đó là số lần ghi chính xác hoặc số phiên bản file.
- Sau khi xử lý, `reset()` để tiếp tục hoạt động. Dù handler gặp lỗi nghiệp vụ, vòng điều phối phải có chính sách không bỏ quên reset hoặc đóng đăng ký.

### Một tên có thể phát nhiều sự kiện

Việc editor lưu file có thể tạo file tạm, ghi file, rename rồi xóa file cũ. Một hành động “Save” của người dùng có thể tạo nhiều sự kiện, và trình tự trên Windows/Linux/macOS khác nhau.

Ngay cả `ENTRY_MODIFY` cũng có thể phát nhiều lần cho một chuỗi ghi. Vì vậy:

- Thiết kế handler **idempotent**: xử lý cùng file nhiều lần không làm trạng thái nghiệp vụ sai.
- Có thể **debounce**: đợi một khoảng ngắn trước khi xử lý nội dung cuối, nhưng vẫn kiểm tra file có thật và đủ ổn định.
- Đừng cho rằng `ENTRY_CREATE` xảy ra tức file đã được ghi xong.
- Nếu muốn xử lý dữ liệu đầy đủ, ứng dụng thường kiểm tra nội dung/metadata sau sự kiện, có retry có giới hạn nếu file còn bị khóa hoặc chưa sẵn sàng.

## 10. Chương trình Java 17+ hoàn chỉnh với timed poll

Ví dụ tự tạo một thư mục tạm, đăng ký sự kiện, tạo rồi sửa một file bên trong, sau đó lấy các `WatchKey` trong khoảng thời gian hữu hạn. Chương trình không in số lượng/tên/thứ tự event như một đáp án cố định, vì các tín hiệu phụ thuộc filesystem.

Tạo file **`WatchServiceLoopDemo.java`**:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;

public class WatchServiceLoopDemo {

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("watch-demo-");
        Path file = directory.resolve("sample.txt");

        try {
            try (WatchService watcher =
                         FileSystems.getDefault().newWatchService()) {

                directory.register(watcher,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);

                Files.writeString(file, "first\n", StandardCharsets.UTF_8);
                Files.writeString(file, "second\n", StandardCharsets.UTF_8);

                int receivedKeys = 0;
                long deadline = System.nanoTime()
                        + TimeUnit.SECONDS.toNanos(2);

                while (System.nanoTime() < deadline) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        break;
                    }

                    WatchKey key = watcher.poll(
                            remaining, TimeUnit.NANOSECONDS);

                    if (key == null) {
                        break;
                    }

                    receivedKeys++;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind()
                                == StandardWatchEventKinds.OVERFLOW) {
                            System.out.println("Event: OVERFLOW");
                            continue;
                        }

                        Path name = (Path) event.context();
                        System.out.println("Event: "
                                + event.kind().name()
                                + ", name=" + name);
                    }

                    if (!key.reset()) {
                        System.out.println("WatchKey invalid");
                        break;
                    }
                }

                System.out.println("At least one key: "
                        + (receivedKeys > 0));
            } catch (ClosedWatchServiceException ex) {
                System.out.println("WatchService closed");
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac WatchServiceLoopDemo.java
java WatchServiceLoopDemo
```

**Ví dụ stdout thường gặp** trên hệ thống phát đủ create/modify cho tệp:

```text
Event: ENTRY_CREATE, name=sample.txt
Event: ENTRY_MODIFY, name=sample.txt
At least one key: true
```

**Đây là output minh họa, không phải cam kết stdout chính xác trên mọi hệ thống.** Số lượng `ENTRY_MODIFY`, việc coalescing, thứ tự và thời điểm quan sát có thể khác nhau. Trên môi trường không phát event trong thời hạn 2 giây, chương trình có thể chỉ in:

```text
At least one key: false
```

Nếu provider không hỗ trợ watch service, thiếu quyền thư mục hoặc lỗi I/O, chương trình có thể ném ngoại lệ thay vì in kết quả trên. Trình biên dịch và runtime Java 17+ có thể chạy chương trình độc lập; khả năng quan sát sự kiện phụ thuộc filesystem thực tế.

Phân tích từng bước:

1. `Files.createTempDirectory` tạo thư mục riêng; `Files.writeString` tạo rồi ghi file để tạo các thay đổi có thể quan sát được.
2. Đăng ký `ENTRY_CREATE`, `ENTRY_MODIFY`, `ENTRY_DELETE` **trước** khi ghi file, tránh bỏ lỡ ngay các thao tác do ví dụ thực hiện.
3. Vòng timed poll sử dụng `System.nanoTime()` để tính deadline tương đối, thay vì tiếp tục chờ 2 giây cho **mỗi** key. Nó dừng sau khoảng thời gian tối đa dành cho giai đoạn chờ.
4. `pollEvents()` lấy danh sách events; `OVERFLOW` được kiểm tra trước khi ép context sang Path.
5. `key.reset()` giúp key tiếp tục theo dõi nếu còn hợp lệ.
6. `WatchService` được đóng bằng try-with-resources. File và thư mục demo được xóa trong `finally`. Việc xóa cuối cùng xảy ra sau khi đóng watcher, vì vậy không phải sự kiện đáng mong chờ trong output.

Để test kỹ hơn, có thể đưa các thay đổi vào một thread writer riêng cách nhau vài trăm mili-giây. Vẫn không nên assert rằng từng lần ghi sẽ tương ứng chính xác một `ENTRY_MODIFY` trên mọi provider.

## 11. Khi có nhiều thư mục: map WatchKey -> Path

Một `WatchService` có thể nhận keys từ nhiều thư mục đã đăng ký. Mẫu phổ biến:

```java
Map<WatchKey, Path> directories = new HashMap<>();

WatchKey keyA = pathA.register(watcher, ENTRY_CREATE, ENTRY_DELETE);
WatchKey keyB = pathB.register(watcher, ENTRY_MODIFY);

directories.put(keyA, pathA);
directories.put(keyB, pathB);
```

Đây là đoạn minh họa, cần import `Map`, `HashMap`, các `Path` và static event kinds; `pathA`, `pathB` phải hợp lệ.

Khi lấy một key:

```java
Path directory = directories.get(key);
if (directory == null) {
    // Key không thuộc tập đăng ký do ứng dụng quản lý.
} else {
    for (WatchEvent<?> event : key.pollEvents()) {
        if (event.kind() == OVERFLOW) {
            // Rescan directory.
            continue;
        }

        Path relative = (Path) event.context();
        Path changed = directory.resolve(relative);
        // Xử lý changed phù hợp loại event.
    }
}

if (!key.reset()) {
    directories.remove(key);
}
```

Đây cũng là đoạn minh họa, giả định các biến và constant đã được khai báo. Có thể dùng `(Path) key.watchable()` thay map cho trường hợp chỉ đăng ký trực tiếp Path. Map giúp ứng dụng gắn thêm metadata như tenant, project, cấu hình filter, policy retry hay trạng thái thư mục.

**Không reset key không thuộc về mình mà bỏ qua mãi** nếu đang dùng một WatchService dùng chung: cần chính sách quản lý ownership, cleanup và bảo vệ luồng xử lý rõ ràng.

## 12. Một thư mục không có nghĩa theo dõi đệ quy

Trong Java NIO.2 chuẩn, đăng ký một thư mục thường chỉ nhận sự kiện đối với **các mục trực tiếp** bên dưới:

```text
root/              <- đăng ký root
  a.txt            <- có thể nhận ENTRY_MODIFY cho a.txt
  nested/
    b.txt          <- không tự nhiên nhận event b.txt qua root watch
```

Để theo dõi cây thư mục:

1. Duyệt các thư mục con đang tồn tại và đăng ký **từng thư mục**.
2. Khi thấy `ENTRY_CREATE` đối với thư mục mới, kiểm tra đó là directory và đăng ký nó.
3. Với thư mục bị xóa/di chuyển, loại bỏ hoặc xử lý key invalid.
4. Có chiến lược chống race giữa lúc duyệt cây và lúc đăng ký. Một phần thay đổi có thể đã xảy ra trước khi thư mục con được đăng ký.
5. Cân nhắc symbolic link, vòng lặp đường dẫn, quyền truy cập, và giới hạn tài nguyên nếu cây rất lớn.

Không nên viết `root.register(watcher, ENTRY_CREATE)` rồi khẳng định có thể nhận mọi thay đổi sâu nhiều cấp. Nếu bài toán đòi hỏi phát hiện **chắc chắn mọi thay đổi**, WatchService thường cần bổ sung snapshot/rescan hoặc cơ chế theo dõi filesystem chuyên dụng với hợp đồng phù hợp.

## 13. OVERFLOW: sự kiện có thể bị mất

`StandardWatchEventKinds.OVERFLOW` báo rằng sự kiện có thể đã bị bỏ vì hàng đợi/quản lý tài nguyên không theo kịp số lượng thay đổi hoặc lý do provider khác.

Sai lầm nguy hiểm:

```java
for (WatchEvent<?> event : key.pollEvents()) {
    Path p = (Path) event.context(); // Sai với OVERFLOW.
    process(p);
}
```

Đây chỉ là đoạn **phản ví dụ**, `process` chưa được định nghĩa. Với `OVERFLOW`, hãy coi trạng thái cache theo dõi có nguy cơ **không đồng bộ với filesystem**:

- Đánh dấu thư mục/cây cần **rescan** để lấy trạng thái thực tế.
- So sánh snapshot mới với trạng thái ứng dụng lưu trước đó nếu nghiệp vụ cần.
- Thực hiện xử lý idempotent khi phát hiện thay đổi.
- Ghi log/metrics overflow, theo dõi khả năng event loop xử lý quá chậm.
- Tránh coi event stream là hệ thống event sourcing chuẩn xác: sau overflow bạn **không thể tái tạo mọi thao tác trung gian** chỉ từ các event còn lại.

Ngay cả khi không thấy `OVERFLOW`, filesystem notifications vẫn có thể gộp sự kiện, phụ thuộc provider, và không nên thay thế mọi kiểm tra trạng thái thực tế nếu bài toán cần độ chính xác cao.

## 14. Đọc file khi có ENTRY_CREATE/ENTRY_MODIFY

`WatchService` báo **thay đổi được quan sát**, không bảo đảm “file đã được ghi xong” hay “người ghi đã đóng handle”.

Ví dụ producer có thể:

1. Tạo `report.csv`.
2. Ghi header.
3. Ghi từng batch rows.
4. Flush và đóng file.

Consumer có thể nhận `ENTRY_CREATE` ngay sau bước 1 và đọc thấy file chưa hoàn chỉnh.

Chiến lược thiết kế tốt hơn:

- Producer ghi vào file tạm, đóng, rồi **rename/move** tới tên chính thức khi sẵn sàng, với hợp đồng filesystem phù hợp.
- Consumer xem `ENTRY_CREATE` là dấu hiệu cần kiểm tra trạng thái, không đồng nhất với transaction completed.
- Nếu chỉ có `ENTRY_MODIFY`, debounce và đọc lại sau khi file ổn định theo policy; cần xử lý file vẫn đang bị ghi/khóa.
- Thiết kế parser chịu được EOF và định dạng tạm thời không hợp lệ; retry có giới hạn thay vì loop vô hạn.
- Khi chỉ cần biết trạng thái hiện thời, đọc lại snapshot/file state thay vì cố phát lại mọi event riêng lẻ.

Đừng mở file rồi mặc định nó tồn tại: mục có thể đã bị xóa/rename **giữa event và lúc mở** (TOCTOU). Kiểm tra/đọc với xử lý `NoSuchFileException` và các lỗi quyền truy cập.

## 15. Dừng vòng lặp đúng cách

### Với vòng `take()`

`take()` có thể chờ vô hạn nếu không còn thay đổi nào. Những cách dừng thường dùng:

- **Interrupt thread** đang `take()`; bắt `InterruptedException`, đặt lại interrupt flag nếu phù hợp và kết thúc loop.
- **Đóng WatchService** từ thread điều khiển; thread đang chờ có thể nhận `ClosedWatchServiceException`.
- Dùng protocol shutdown riêng, đảm bảo không giữ lock gây deadlock khi watcher dừng.

Ví dụ minh họa:

```java
try {
    while (running) {
        WatchKey key = watcher.take();
        try {
            handle(key);
        } finally {
            key.reset();
        }
    }
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
} catch (ClosedWatchServiceException ex) {
    // Phù hợp khi người điều khiển đã chủ động đóng service.
}
```

Đoạn này **không độc lập**; `running`, `watcher` và `handle` do ứng dụng định nghĩa. Khi có nhiều keys, vẫn phải kiểm tra giá trị trả về của `reset()` và hủy/loại đăng ký invalid; snippet tối giản chỉ minh họa lối thoát.

### Với timed poll

Khi dùng `poll(500, MILLISECONDS)`, thread có cơ hội kiểm tra cờ dừng đều đặn kể cả khi không có event. Có thể dùng `AtomicBoolean` hoặc cơ chế đồng bộ thích hợp cho `running` khi flag được thread khác cập nhật.

```java
while (running.get()) {
    WatchKey key = watcher.poll(500, TimeUnit.MILLISECONDS);
    if (key == null) {
        continue;
    }

    try {
        handle(key);
    } finally {
        if (!key.reset()) {
            // Hủy/loại khỏi cấu trúc đăng ký.
        }
    }
}
```

Mẫu này không có busy spin liên tục, thời gian nhận ra cờ dừng có thể bị giới hạn bởi timeout và thời gian xử lý callback. Nó không thay thế việc đóng service khi ứng dụng giải phóng tài nguyên.

### Đóng service

`WatchService` triển khai `Closeable`, nên dùng try-with-resources. Sau `close()`, `take/poll` không còn là các thao tác tiêu thụ key hợp lệ. `WatchKey.cancel()` hủy một đăng ký, không đồng nghĩa đóng toàn bộ service.

## 16. Độ tin cậy và các giới hạn thực tế

Không nên coi `WatchService` là một hàng đợi giao dịch/bản ghi thay đổi đầy đủ như message broker:

- **Số lượng và thứ tự sự kiện phụ thuộc provider.** Có thể gộp modify, bỏ event khi overflow, hoặc có tín hiệu muộn.
- **Rename/move** có thể thể hiện bằng create/delete, modify hoặc tổ hợp khác nhau tùy filesystem.
- **Remote mounts/network drives** có thể có semantics khác filesystem cục bộ hoặc không hỗ trợ đầy đủ.
- **Crash/restart**: WatchService không cung cấp lịch sử sự kiện bền để phát lại trong thời gian ứng dụng tắt. Cần rescan lúc khởi động.
- **Nội dung file** không có sẵn trong event; handler phải tự đọc, xử lý file đổi/xóa giữa chừng.
- **Nhiều thay đổi cùng lúc** có thể gây `OVERFLOW`. Ưu tiên xử lý event ngắn, đẩy tác vụ CPU/I/O nặng sang worker pool có giới hạn.
- **Bảo mật:** không coi path từ event là input đã được xác thực; kiểm soát thư mục gốc, symbolic links, quyền và dữ liệu không tin cậy.
- **Tài nguyên:** đăng ký hàng nghìn thư mục có chi phí OS/provider. Khi key invalid phải dọn map, tránh rò rỉ.
- **Nhiều consumer trên cùng service:** nếu các luồng cùng gọi take/poll, một key có thể được tiêu thụ bởi một consumer; cần điều phối ownership, không giả định mọi consumer đều nhận bản sao sự kiện.

Đối với tác vụ yêu cầu không bỏ lỡ thay đổi nào (ví dụ audit pháp lý), chỉ dựa vào WatchService thường không đủ. Cân nhắc nhật ký thay đổi ở nguồn dữ liệu hoặc transaction log được thiết kế cho mục tiêu đó.

## 17. Lỗi phổ biến

1. **Quên `reset()`**: key có thể ngừng phát tín hiệu tiếp theo như mong muốn.
2. **Gọi `reset()` trước khi xử lý events rồi quên trạng thái sự kiện**: làm flow khó hiểu; ưu tiên drain `pollEvents()` rồi reset có kiểm soát.
3. **Không xét `reset()==false`**: dùng một registration đã invalid, dẫn tới im lặng không thấy thay đổi.
4. **Nhầm `poll()==null` thành thư mục chắc chắn không thay đổi**: đó chỉ là không có key signalled trong hàng đợi ngay lúc kiểm tra.
5. **Dùng `poll()` trong vòng quay nóng**: CPU tăng cao khi không có event.
6. **Dùng `take()` nhưng không có cơ chế shutdown**: thread có thể chờ vô hạn.
7. **Không bắt `InterruptedException` ở take/timed poll**: mất cơ hội dừng lịch sự.
8. **Quên kiểm tra `OVERFLOW` trước khi cast `event.context()`**: context có thể không phải Path hợp lệ.
9. **Hiểu `event.context()` là đường dẫn tuyệt đối**: cần resolve với thư mục gắn key.
10. **Tưởng `ENTRY_CREATE` nghĩa file đã viết xong**: có thể đọc file còn đang ghi.
11. **Dựa vào đúng một `ENTRY_MODIFY` cho mỗi lần save**: provider có thể gộp/phát nhiều thông báo.
12. **Giả định theo dõi một root là theo dõi recursive**: phải đăng ký từng thư mục con.
13. **Xử lý file bằng một thao tác nặng ngay trong watch loop**: có thể làm backlog tăng và overflow.
14. **Không xử lý file bị xóa/rename trước khi handler đọc**: TOCTOU dễ xảy ra.
15. **Quên đăng ký thư mục mới khi theo dõi cây**: không nhận thay đổi sâu bên trong.
16. **Không rescan sau OVERFLOW hoặc restart**: trạng thái ứng dụng có thể không đồng bộ.
17. **Không đóng WatchService**: giữ tài nguyên OS hoặc thread đang chờ.
18. **Coi events như lịch sử chính xác**: WatchService là hệ thống thông báo thay đổi, không phải event journal.
19. **Chỉ có một map path nhưng không quản lý các WatchKey invalid**: rò rỉ reference và mất khả năng đăng ký lại.
20. **Giả định code sẽ phát stdout giống hệt trên mọi OS**: thứ tự và count không ổn định; test nên dựa vào eventual state và timeout phù hợp.

## 18. Lựa chọn take hay poll trong ứng dụng

### Trường hợp A — Dịch vụ import file chuyên dụng

Một thread theo dõi thư mục `incoming`, gần như chỉ làm việc khi có file. Dùng `take()` giúp ngủ chờ thay đổi, không cần đánh thức định kỳ. Khi shutdown, interrupt thread hoặc đóng service, bắt ngoại lệ thích hợp. Handler nên chuyển công việc import nặng sang queue/worker pool và xử lý file chưa ghi xong.

### Trường hợp B — Ứng dụng GUI hoặc chương trình có main loop

Main loop đã xử lý input và UI/tác vụ khác. `poll()` có thể phù hợp nếu được gọi với tần suất hợp lý, không busy spin. Tuy nhiên không nên chặn UI thread bằng `take()`; có thể dùng watcher thread riêng và gửi thông báo vào UI dispatcher.

### Trường hợp C — Watcher kiêm tác vụ định kỳ

Dùng `poll(1, TimeUnit.SECONDS)` để chờ event có giới hạn rồi kiểm tra health, cờ dừng và thực hiện rescan định kỳ nếu cần. Mỗi lần có key vẫn phải drain events + reset.

### Trường hợp D — Theo dõi cây thư mục lớn

Cần map keys, đăng ký từng directory, đăng ký directory vừa xuất hiện, xử lý invalid keys và overflow. Có thể dùng một `WatchService` và một consumer loop, phân phối công việc nặng sang executor. Trong bài toán rất lớn, cân nhắc giới hạn tài nguyên và đặc tính OS.

### Trường hợp E — Đồng bộ trạng thái quan trọng

WatchService là **trigger** để đọc trạng thái mới, không phải nguồn dữ liệu duy nhất. Đăng ký watch, quét snapshot ban đầu, xử lý thay đổi rồi rescan khi overflow hoặc khởi động lại. Với yêu cầu exactly-once, cần cơ chế transaction/idempotency và nhật ký khác.

## 19. Câu hỏi tự kiểm tra

**Câu 1.** `take()` trả `null` khi không có event không?  
**Không.** Bình thường nó chờ đến khi có key; khi bị interrupt/đóng service có thể ném ngoại lệ.

**Câu 2.** `poll()` trả `null` có nghĩa là filesystem không có thay đổi nào từ trước đến giờ?  
**Không.** Chỉ có nghĩa chưa có key signalled sẵn trong service tại thời điểm gọi.

**Câu 3.** `poll(2, TimeUnit.SECONDS)` khác `take()` thế nào?  
**Nó chờ tối đa thời hạn**, rồi có thể trả `null`; `take` có thể chờ vô hạn.

**Câu 4.** Vì sao cần `key.reset()` sau khi xử lý `pollEvents()`?  
**Để đưa key về trạng thái sẵn sàng** cho lần báo hiệu tiếp theo nếu đăng ký còn hợp lệ.

**Câu 5.** `reset()` trả `false` thì phải làm gì?  
**Không tiếp tục tin vào registration đó**; bỏ key/hủy theo policy, nếu cần thì chờ thư mục tồn tại rồi đăng ký lại.

**Câu 6.** Một `WatchService` đăng ký `root` có tự động nhận `ENTRY_MODIFY` cho `root/nested/a.txt` không?  
**Không nên giả định.** Java NIO.2 thường chỉ watch direct entries, cần đăng ký `nested` riêng.

**Câu 7.** Có thể gọi `(Path) event.context()` cho `OVERFLOW` không?  
**Không.** Phải nhận diện OVERFLOW trước; dùng rescan để đồng bộ trạng thái.

**Câu 8.** Vừa thấy `ENTRY_CREATE` có được hiểu rằng producer đã đóng file và file hoàn chỉnh không?  
**Không.** Có thể producer vẫn đang ghi; cần giao thức publish/rename hoặc kiểm tra ổn định.

**Câu 9.** Vì sao `poll()` chạy trong vòng `while(true)` không có chờ có thể gây hại?  
**Vì busy spin**, tiêu tốn CPU liên tục khi không có event.

**Câu 10.** Có một tiến trình cần audit chính xác mọi lần file được ghi, WatchService có đủ không?  
**Thông thường không.** Events có thể gộp/mất và không bền qua restart; cần cơ chế audit/journal ở tầng thích hợp.

## 20. Tóm tắt

- **`WatchService.take()`**: đợi key được báo hiệu; hợp với dedicated watcher thread.
- **`WatchService.poll()`**: kiểm tra ngay, trả `null` nếu không có key; tránh busy loop.
- **`WatchService.poll(timeout, unit)`**: đợi có hạn, phù hợp vòng xử lý có nhiệm vụ định kỳ và shutdown.
- **Chu trình đúng**: đăng ký → lấy key → `pollEvents()` → xử lý `ENTRY_*`/`OVERFLOW` → `reset()` → tiếp tục.
- **`WatchEvent.context()` là đường dẫn tương đối** của mục trực tiếp trong thư mục đã đăng ký; resolve với directory của key.
- **Key invalid, OVERFLOW, restart, file đang ghi và theo dõi đệ quy** cần được xử lý rõ để watcher đáng tin cậy hơn.
- **WatchService là tín hiệu thay đổi, không phải log chính xác từng thao tác filesystem.** Khi yêu cầu consistency cao, kết hợp kiểm tra trạng thái thực tế, idempotency và recovery.

### Tài liệu Java 17

- [WatchService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/WatchService.html)
- [WatchKey](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/WatchKey.html)
- [WatchEvent](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/WatchEvent.html)
- [StandardWatchEventKinds](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardWatchEventKinds.html)
- [Path.register](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html#register(java.nio.file.WatchService,java.nio.file.WatchEvent.Kind...))


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="watch-service-005">reset key và trạng thái đăng ký không còn hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

# WatchKey.reset() và trạng thái đăng ký không còn hợp lệ trong Java NIO.2

Khi theo dõi thay đổi trong thư mục bằng `WatchService`, chương trình đăng ký một `Path` để nhận các sự kiện `ENTRY_CREATE`, `ENTRY_MODIFY`, `ENTRY_DELETE`. Lời gọi đăng ký trả về một `WatchKey` đại diện cho **đăng ký của thư mục với WatchService**. Sau khi lấy key ra khỏi hàng đợi và xử lý sự kiện, ứng dụng cần gọi **`key.reset()`** để đăng ký có thể tiếp tục được báo hiệu.

Điểm cốt lõi: `reset()` trả `true` nếu key **vẫn hợp lệ** và có thể được đặt lại trạng thái; trả `false` nếu đăng ký không còn hợp lệ. `false` **không có nghĩa là tạm thời chưa có sự kiện**, cũng không có nghĩa là cần gọi `reset()` lặp liên tục cho đến khi thành công. Khi key đã bị hủy hoặc tài nguyên đang theo dõi không còn truy cập được, cần quyết định **dừng theo dõi, loại bỏ đăng ký cũ hoặc đăng ký mới** theo chính sách của ứng dụng.

Bài viết giải thích vòng đời `WatchKey`, trạng thái `READY/SIGNALLED`, cách dùng `pollEvents()` và `reset()` đúng thứ tự, ý nghĩa của `isValid()`, `cancel()`, xử lý mất sự kiện và lỗi phổ biến khi xây dựng một watcher chạy lâu dài.

## 1. WatchService và WatchKey có vai trò khác nhau

Các thành phần thường gặp:

| Thành phần | Vai trò |
| --- | --- |
| `WatchService` | Dịch vụ nhận thông báo và giữ hàng đợi các key đã được báo hiệu |
| `Path` / `Watchable` | Đối tượng được đăng ký để theo dõi; trong tình huống thông thường là thư mục |
| `WatchKey` | Đăng ký cụ thể giữa thư mục với một `WatchService`, có trạng thái hợp lệ/không hợp lệ và khả năng được báo hiệu |
| `WatchEvent<?>` | Một sự kiện đã nhận, gồm loại `kind()`, số lần `count()` và ngữ cảnh `context()` |
| `StandardWatchEventKinds` | Các loại sự kiện tiêu chuẩn như `ENTRY_CREATE`, `ENTRY_DELETE`, `ENTRY_MODIFY`, `OVERFLOW` |

Đăng ký cơ bản với Java 17:

```java
import java.nio.file.*;
import static java.nio.file.StandardWatchEventKinds.*;

// Đoạn minh họa trong phương thức đã xử lý IOException.
Path directory = Path.of("watched-folder");
try (WatchService watcher =
        FileSystems.getDefault().newWatchService()) {
    WatchKey key = directory.register(
            watcher, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
    System.out.println(key.isValid());
}
```

Đây là một **đoạn minh họa**: thư mục `watched-folder` phải tồn tại và filesystem phải hỗ trợ đăng ký loại sự kiện yêu cầu. Việc đăng ký thất bại có thể ném `IOException`, `UnsupportedOperationException` hoặc ngoại lệ phù hợp khác. Khi thành công, `key` đại diện cho **thư mục được theo dõi**, không đại diện cho một sự kiện cụ thể hoặc một file con.

`WatchService.take()` chờ đến khi có key báo hiệu; `poll()` không chờ và có thể trả `null`; `poll(timeout, unit)` chờ tối đa thời gian chỉ định. **`null` từ `watcher.poll()` và `false` từ `key.reset()` là hai trạng thái hoàn toàn khác nhau.**

## 2. Trạng thái READY và SIGNALLED của WatchKey

Theo mô hình của `WatchKey`, một đăng ký hợp lệ có trạng thái nội bộ **READY** hoặc **SIGNALLED**. Những trạng thái này khác với thuộc tính **valid/invalid**.

- **READY:** Key đang sẵn sàng nhận và báo hiệu sự kiện tiếp theo. Key không cần hiện diện trong hàng đợi lúc này.
- **SIGNALLED:** Một hoặc nhiều sự kiện đã khiến key được báo hiệu; key được đưa vào hàng đợi thông báo của `WatchService` theo quy tắc của provider.
- **INVALID:** Đăng ký không còn sử dụng được, chẳng hạn đã `cancel()`, thư mục không còn truy cập được hoặc `WatchService` đã đóng. `reset()` không thể làm cho key invalid hợp lệ trở lại.

Sơ đồ tư duy:

```text
register(directory) --> WatchKey hợp lệ, READY
                               |
                               | Sự kiện được phát hiện
                               v
                      SIGNALLED / queued
                               |
                               | take() hoặc poll() lấy key
                               | pollEvents() đọc danh sách sự kiện
                               v
                        gọi key.reset()
                         /           \
                 true                false
                  |                    |
             hợp lệ; READY         đăng ký không hợp lệ
             có thể được           dừng dùng key đó;
             báo hiệu nữa          cleanup hoặc re-register
```

**Lấy key ra khỏi hàng đợi không tự động reset key.** Nếu chỉ gọi `pollEvents()` rồi bỏ qua `reset()`, đăng ký vẫn có thể ở trạng thái SIGNALLED và không tiếp tục cung cấp chu kỳ thông báo như mong muốn. Đây là lý do vòng lặp xử lý key luôn có bước reset sau khi hoàn tất phần sự kiện hiện có.

Nếu các sự kiện mới đến trong khi key còn SIGNALLED, provider có thể tích lũy thêm event trong danh sách chờ; việc gọi `reset()` cho phép key lại được đưa vào hàng đợi theo quy tắc báo hiệu. Không diễn giải một lần `take()` là một sự kiện duy nhất, vì một key có thể chứa **nhiều `WatchEvent`**.

### Mệnh đề dễ nhớ

**`isValid()` nói về đăng ký**, còn **`reset()` điều khiển vòng báo hiệu tiếp theo và báo về tính hợp lệ**. Một key hợp lệ có thể đang READY hoặc SIGNALLED; ngược lại key invalid không trở lại valid chỉ nhờ reset.

## 3. Cách xử lý đúng: take → pollEvents → reset

Một vòng lặp cơ bản:

```java
// Phác thảo, watcher đã được mở và các thư mục đã đăng ký.
while (true) {
    WatchKey key = watcher.take(); // Có thể chờ; ném InterruptedException.
    Path directory = (Path) key.watchable();

    for (WatchEvent<?> event : key.pollEvents()) {
        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
            System.out.println("Event loss; rescan needed");
            continue;
        }

        Path relative = (Path) event.context();
        Path changed = directory.resolve(relative);
        System.out.println(event.kind().name() + ": " + changed);
    }

    boolean stillValid = key.reset();
    if (!stillValid) {
        System.out.println("Registration is no longer valid: " + directory);
        // Cập nhật registry; đăng ký lại nếu directory có thể theo dõi.
    }
}
```

Đây là **phác thảo**, không phải chương trình độc lập vì chưa cung cấp quản lý ngoại lệ, chính sách dừng vòng lặp và cleanup. Hành vi cần nắm:

1. `take()` lấy **một key đã được signal**, không phải một `WatchEvent` lẻ.
2. `key.pollEvents()` trả danh sách event đang tích lũy và lấy chúng ra khỏi danh sách của key. Một lần gọi có thể chứa nhiều event.
3. Với sự kiện thư mục tiêu chuẩn không phải `OVERFLOW`, `event.context()` thường là `Path` tương đối với thư mục mà key theo dõi; dùng `((Path) key.watchable()).resolve(relative)` để có đường dẫn file bị ảnh hưởng.
4. Sau khi xử lý danh sách, gọi `key.reset()` **một lần** rồi kiểm tra boolean trả về.
5. Nếu trả `false`, bỏ giả định đăng ký còn hoạt động: không tiếp tục chờ event từ key cũ như bình thường.

Trong code thực tế, nên ghi trạng thái `WatchKey → Path` bằng `Map` khi theo dõi nhiều thư mục; `key.watchable()` cũng giúp truy ngược đối tượng được đăng ký. Số lượng key còn hợp lệ quyết định có cần tiếp tục vòng lặp hay có thể dừng.

## 4. Ý nghĩa của reset(): true không phải “có event”, false không phải “hết event”

API:

```java
boolean valid = key.reset();
```

Một số tình huống:

| Tình huống | Kết quả mong đợi | Giải thích |
| --- | --- | --- |
| Key được đăng ký, vẫn hợp lệ, chưa có event | `true` | Đang hợp lệ; reset không yêu cầu phải có event trước đó |
| Key được `take()`, xử lý event, thư mục vẫn theo dõi được | `true` | Key tiếp tục chu kỳ báo hiệu |
| Đã gọi `key.cancel()` | `false` | Key đã invalid; reset không đăng ký lại |
| Dịch vụ watcher đã đóng | `false` đối với key không còn hợp lệ | Các đăng ký bị vô hiệu hóa; không thể mở lại bằng reset |
| Thư mục bị xóa / không còn truy cập được | Có thể `false` khi provider đã phát hiện mất hiệu lực | Thời điểm phát hiện phụ thuộc provider và hệ điều hành |
| `watcher.poll()` trả `null` | Không suy ra kết quả reset | Chưa có key nào sẵn sàng trong queue; không nói key nào invalid |

**Không viết logic `if (!key.reset()) { key.reset(); }` để “khôi phục” key.** Reset lần tiếp theo không tự tạo lại registration. Nếu thư mục đã được xóa và tạo lại cùng tên, cần nhận thức đường dẫn mới không nhất thiết còn liên kết với đăng ký theo dõi cũ.

### isValid() và reset() có phải thay thế nhau?

Không hoàn toàn:

```java
boolean registered = key.isValid();
boolean canContinue = key.reset();
```

`isValid()` giúp quan sát key còn hiệu lực **tại thời điểm kiểm tra**. `reset()` là thao tác cần thiết trong chu trình tiêu thụ sự kiện để key trở lại READY, đồng thời trả về trạng thái hợp lệ. Kiểm tra `isValid()` rồi **bỏ `reset()`** là lỗi phổ biến. Cũng không dùng một lần `isValid()` làm cam kết rằng registration không thể bị vô hiệu hóa ở bước tiếp theo; có thể có luồng khác `cancel()` hoặc filesystem thay đổi.

## 5. Tại sao WatchKey trở nên không hợp lệ?

Có nhiều nguyên nhân:

### 5.1. Hủy tường minh bằng cancel()

`key.cancel()` vô hiệu hóa đăng ký của key. Sau đó `key.isValid()` trả `false`, và `key.reset()` cũng trả `false`. `cancel()` không có nghĩa xóa thư mục trên đĩa, cũng không đóng toàn bộ `WatchService` nếu watcher còn được dùng cho đăng ký khác.

```java
// Đoạn minh họa: key được register thành công trước đó.
System.out.println(key.isValid()); // true
key.cancel();
System.out.println(key.isValid()); // false
System.out.println(key.reset());   // false
```

Nếu cùng một thư mục cần theo dõi lại sau hủy, gọi `directory.register(watcher, kinds...)` để nhận một đăng ký hợp lệ. Đừng tiếp tục sử dụng `key` đã hủy.

### 5.2. Đóng WatchService

`watcher.close()` đóng dịch vụ và vô hiệu hóa các key của nó. Luồng đang chờ `watcher.take()` có thể nhận `ClosedWatchServiceException`. Đây thường là **tín hiệu dừng có chủ đích** khi ứng dụng shutdown, không phải cơ hội để reset từng key rồi tiếp tục dùng watcher đã đóng.

### 5.3. Thư mục không còn khả dụng

Nếu thư mục bị xóa, unmount, mất quyền hoặc filesystem không thể tiếp tục giám sát, provider có thể vô hiệu hóa đăng ký. Cách thức và thời điểm nhận biết **không đồng nhất giữa hệ điều hành và provider**.

Một trường hợp rất dễ gây lỗi:

```text
Đăng ký theo dõi: /data/incoming   --> key A hợp lệ
Một tác nhân xóa /data/incoming    --> key A có thể trở nên invalid
Một tác nhân tạo lại /data/incoming --> không có bảo đảm key A tự theo dõi
                                      đối tượng thư mục mới
```

Ngay cả khi đường dẫn có cùng chuỗi ký tự, đối tượng thư mục đã được tạo lại có thể khác. Nếu muốn tiếp tục giám sát, ứng dụng phải có chiến lược **phát hiện + đăng ký lại**, không chỉ tin rằng đường dẫn vừa xuất hiện lại sẽ kích hoạt key cũ.

### 5.4. Đăng ký lại và key hợp lệ

Theo hợp đồng `Watchable.register`, khi đối tượng đã được đăng ký với cùng `WatchService` và key tương ứng còn hợp lệ, đăng ký lại có thể trả về **key hiện có** và cập nhật tập sự kiện theo yêu cầu. Ngược lại, sau khi đã `cancel()` hoặc key invalid, việc đăng ký mới có thể tạo key hợp lệ mới nếu thư mục và dịch vụ vẫn sử dụng được.

Điều cần kiểm tra là **`newKey.isValid()`** và cập nhật bảng ánh xạ key của ứng dụng, không khẳng định key cũ sẽ hồi sinh.

## 6. Java 17 chạy độc lập: quan sát reset, cancel và đăng ký lại

Chương trình sau tạo thư mục tạm, đăng ký các event chuẩn, tạo một file nhỏ để kích hoạt thông báo, đọc event nếu provider chuyển tới trước timeout, thử `reset()`, rồi **chủ động hủy key để chứng minh chắc chắn trạng thái invalid**. Sau đó nó đăng ký lại cùng thư mục và cuối cùng dọn tài nguyên.

Tạo file **`WatchKeyResetDemo.java`**:

```java
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.*;

public final class WatchKeyResetDemo {
    private WatchKeyResetDemo() {}

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("watch-key-demo-");
        Path sample = directory.resolve("sample.txt");

        try {
            try (WatchService watcher =
                    FileSystems.getDefault().newWatchService()) {

                WatchKey key = directory.register(
                        watcher, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);

                System.out.println("registered valid=" + key.isValid());
                System.out.println("reset while ready=" + key.reset());

                Files.writeString(sample, "hello");

                WatchKey signalled = watcher.poll(5, TimeUnit.SECONDS);
                if (signalled == null) {
                    System.out.println("no event within timeout");
                } else {
                    for (WatchEvent<?> event : signalled.pollEvents()) {
                        if (event.kind() ==
                                StandardWatchEventKinds.OVERFLOW) {
                            System.out.println("OVERFLOW: rescan needed");
                            continue;
                        }
                        System.out.println("event=" + event.kind().name()
                                + " relative=" + event.context());
                    }
                    System.out.println("reset after events="
                            + signalled.reset());
                }

                key.cancel();
                System.out.println("after cancel valid=" + key.isValid());
                System.out.println("after cancel reset=" + key.reset());

                WatchKey newKey =
                        directory.register(watcher, ENTRY_CREATE);
                System.out.println(
                        "new registration valid=" + newKey.isValid());
                newKey.cancel();
            }
        } finally {
            Files.deleteIfExists(sample);
            Files.deleteIfExists(directory);
        }
    }
}
```

Biên dịch và chạy với Java 17+:

```bash
javac WatchKeyResetDemo.java
java WatchKeyResetDemo
```

Đầu ra *thường gặp* trên filesystem cục bộ hỗ trợ watcher:

```text
registered valid=true
reset while ready=true
event=ENTRY_CREATE relative=sample.txt
event=ENTRY_MODIFY relative=sample.txt
reset after events=true
after cancel valid=false
after cancel reset=false
new registration valid=true
```

**Không có bảo đảm hai dòng `event=...` luôn xuất hiện đúng số lần hoặc đúng thứ tự như trên.** Provider có thể gộp `ENTRY_MODIFY`, trả nhiều event, chỉ trả một số event trong lần `pollEvents()`, hoặc chưa giao thông báo trong 5 giây. Khi không nhận được key trước timeout, chương trình in `no event within timeout` thay cho các dòng event và `reset after events`. Các dòng kiểm tra trước và sau `cancel()` là điểm chính: **key không hợp lệ không thể được khôi phục bằng `reset()`**.

### Phân tích từng bước

1. `directory.register(watcher, ...)` nhận `WatchKey` hợp lệ. `isValid()` là `true`.
2. `key.reset()` khi chưa có sự kiện vẫn có thể trả `true` vì key hợp lệ; điều này không chứng minh đã nhận event.
3. `Files.writeString(sample, "hello")` tạo tệp nên là một hành động mà provider có thể phát `ENTRY_CREATE` và `ENTRY_MODIFY` theo quy tắc của nó.
4. `watcher.poll(5, TimeUnit.SECONDS)` chờ một key đã signal tối đa 5 giây. Nếu nhận được, `pollEvents()` lấy event để xử lý, sau đó `reset()` cho phép tiếp tục nhận thông báo.
5. `key.cancel()` vô hiệu hóa đăng ký; `key.isValid()` và `key.reset()` đều trả `false`.
6. `directory.register(watcher, ENTRY_CREATE)` cấp một key hợp lệ khi đăng ký lại thành công; cần thay key cũ trong state của chương trình.
7. `try-with-resources` đóng watcher, khối `finally` xóa file/thư mục tạm do chương trình sở hữu.

Ví dụ ưu tiên tính dễ quan sát hơn tính ổn định thời gian sự kiện. Một ứng dụng thật không nên dựa vào timeout 5 giây như bảo đảm sự kiện đã xảy ra, và cũng không nên lấy một sự kiện duy nhất làm bằng chứng không có thay đổi nào khác trong thư mục.

## 7. Xử lý key.reset() == false trong ứng dụng chạy lâu dài

Khi key không thể reset, logic cần phụ thuộc **ý định theo dõi** của ứng dụng. Các khả năng:

- **Directory bị hủy có chủ đích:** Xóa key ra khỏi registry, không đăng ký lại.
- **Ứng dụng đang shutdown:** Đóng watcher, dừng các luồng nhận sự kiện và dọn bộ nhớ.
- **Directory bị xóa hoặc tạm thời không truy cập được:** Đánh dấu thư mục đang offline, quyết định retry/backoff và kiểm tra khả năng đăng ký lại ở một thời điểm sau.
- **Directory được tạo lại:** Đăng ký mới và thực hiện đối chiếu trạng thái thư mục nếu yêu cầu không bỏ sót thay đổi.
- **Tất cả key của watcher đều invalid:** Nếu watcher không còn mục tiêu theo dõi và không có chính sách tự phục hồi, có thể dừng vòng lặp thay vì chờ vô hạn.

Phác thảo xử lý nhiều thư mục:

```java
// Phác thảo trong lớp điều phối watcher, chưa phải chương trình hoàn chỉnh.
Map<WatchKey, Path> registrations = new HashMap<>();

while (running) {
    WatchKey key = watcher.take();
    Path dir = registrations.get(key);

    for (WatchEvent<?> event : key.pollEvents()) {
        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
            scheduleRescan(dir);
        } else if (dir != null && event.context() instanceof Path path) {
            processChange(dir.resolve(path), event.kind());
        }
    }

    if (!key.reset()) {
        Path lost = registrations.remove(key);
        if (lost != null && shouldRetry(lost)) {
            scheduleReRegistration(lost);
        }
    }
}
```

Đây là **pseudocode Java mang tính phác thảo**: `watcher`, `running` và các hàm `scheduleRescan`, `processChange`, `shouldRetry`, `scheduleReRegistration` chưa định nghĩa. Chương trình thật cần quản lý `InterruptedException`, `ClosedWatchServiceException`, IOException và shutdown.

**Không đăng ký lại một key invalid chỉ bằng cách bỏ `reset()` rồi tiếp tục gọi `take()`**. Một đăng ký đã mất hiệu lực có thể không bao giờ báo sự kiện trở lại; phải có một đường phục hồi riêng nếu ứng dụng vẫn cần theo dõi thư mục.

### Quy trình phục hồi nên có

1. **Loại key cũ:** Bỏ entry tương ứng trong `Map<WatchKey, Path>` và các cấu trúc quản lý liên quan; không giữ key invalid như một đăng ký đang hoạt động.
2. **Kiểm tra mục tiêu:** Nếu thư mục đã không tồn tại, chờ sự kiện từ **thư mục cha còn được theo dõi** hoặc một lịch kiểm tra/backoff khác; không kỳ vọng key invalid sẽ tự báo thư mục được tạo lại.
3. **Đăng ký lại:** Khi thư mục hợp lệ và provider hỗ trợ, gọi `directory.register(watcher, kinds...)` để nhận key mới.
4. **Đồng bộ hiện trạng:** Quét trạng thái thư mục nếu ứng dụng cần biết chính xác danh sách file, vì giữa lúc đăng ký cũ mất hiệu lực và đăng ký mới có thể đã xảy ra thay đổi không có event.
5. **Giới hạn retry:** Tránh vòng lặp retry không nghỉ khi thư mục không có quyền truy cập, provider lỗi liên tục hoặc filesystem bị tháo gỡ.

Nếu ứng dụng không kiểm soát được tác nhân sửa thư mục, việc đăng ký lại vẫn có khoảng hở thời gian; **WatchService không tự cung cấp nhật ký thay đổi chính xác, bền vững như cơ sở dữ liệu**.

## 8. OVERFLOW và sự kiện bị bỏ lỡ không đồng nghĩa key invalid

`StandardWatchEventKinds.OVERFLOW` biểu thị các sự kiện có thể đã bị mất hoặc loại bỏ. Điều này có thể xảy ra khi ứng dụng tiêu thụ sự kiện không đủ nhanh hay provider có giới hạn hàng đợi. Key **vẫn có thể hợp lệ**, và `reset()` vẫn có thể trả `true`.

| Dấu hiệu | Ý nghĩa | Hành động |
| --- | --- | --- |
| `key.pollEvents()` chứa `OVERFLOW` | Độ đầy đủ của sự kiện không còn được đảm bảo | Quét lại trạng thái thư mục, đồng bộ với dữ liệu thực |
| `key.reset()==false` | Đăng ký không còn hợp lệ | Xóa key khỏi registry, dừng hoặc re-register |
| `watcher.poll()==null` | Chưa có key đang đợi trong queue lúc kiểm tra | Tiếp tục chờ theo chính sách, không tự hủy đăng ký |
| `key.pollEvents().isEmpty()` | Không có event tích lũy tại thời điểm đọc danh sách | Vẫn gọi `reset()` nếu key cần tiếp tục theo dõi |
| `key.isValid()==true` | Đăng ký đang được provider xem là hợp lệ | Tiếp tục vòng xử lý đúng quy trình; không bỏ qua reset |

Khi gặp `OVERFLOW`, `event.context()` không phải một đường dẫn file con sử dụng được. Không cast context của `OVERFLOW` sang `Path`. Các loại sự kiện chuẩn khác thường cung cấp `Path` tương đối; dù vậy ứng dụng nên kiểm tra kiểu dữ liệu của event nếu có provider mở rộng.

### Event không phải nhật ký thay đổi đầy đủ

`ENTRY_MODIFY` có thể được gộp, `count()` có thể lớn hơn 1, thứ tự và thời điểm thông báo phụ thuộc provider. Tạo file rồi đổi tên nhanh có thể sinh chuỗi event khác nhau tùy nền tảng. Một event create không bảo đảm file đã ghi hoàn tất; một event delete không bảo đảm ứng dụng có thể suy ra nội dung file bị xóa. Nếu yêu cầu tính đúng đắn cao, dùng WatchService để **kích hoạt bước kiểm tra trạng thái thực tế** thay vì xem event là nguồn dữ liệu duy nhất.

## 9. WatchService khác với FileChannel và Selector

Nhìn qua các API Java NIO, chữ “key” dễ gây nhầm:

| API | Key / thao tác | Ý nghĩa |
| --- | --- | --- |
| `WatchService` | `WatchKey`, `reset()`, `isValid()` | Theo dõi thay đổi của đối tượng được đăng ký với filesystem |
| `Selector` | `SelectionKey`, `isValid()`, `interestOps()` | Theo dõi readiness của `SelectableChannel`, thường dùng cho socket |
| `FileChannel` | `position()`, `size()`, `force()` | Đọc/ghi tệp; không phải bộ máy phát sự kiện WatchKey |

**`WatchKey.reset()` không có cùng ý nghĩa với `ByteBuffer.reset()`**. `ByteBuffer.reset()` đưa position về mark (nếu mark tồn tại), còn `WatchKey.reset()` điều khiển chu kỳ báo hiệu của đăng ký filesystem và trả boolean về hiệu lực.

Một `WatchKey` có thể còn hợp lệ dù tệp con cụ thể vừa bị xóa; key gắn với **thư mục được đăng ký**, không gắn với từng file con. Xóa tệp con thường tạo `ENTRY_DELETE` và không tự vô hiệu hóa key của thư mục cha. Ngược lại, xóa chính thư mục đang theo dõi có thể làm đăng ký mất hiệu lực.

## 10. Thread interruption, close và vòng lặp shutdown

`WatchService.take()` và `WatchService.poll(timeout, unit)` có thể chờ. Nếu luồng đang đợi bị interrupt, chúng có thể ném `InterruptedException`; nếu watcher đã đóng, chúng có thể ném `ClosedWatchServiceException`.

Một khung điều phối phù hợp:

```java
// Phác thảo trong phương thức watcherLoop(WatchService watcher).
try {
    while (!Thread.currentThread().isInterrupted()) {
        WatchKey key = watcher.take();
        try {
            for (WatchEvent<?> event : key.pollEvents()) {
                process(event, key); // Hàm nghiệp vụ, chưa định nghĩa.
            }
        } finally {
            if (!key.reset()) {
                unregisterLostKey(key); // Hàm nghiệp vụ.
            }
        }
    }
} catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt();
} catch (ClosedWatchServiceException shutdown) {
    // Shutdown có chủ đích hoặc watcher bị đóng từ luồng khác.
}
```

Đoạn này **không độc lập** vì `process` và `unregisterLostKey` chưa định nghĩa. Nó nhấn mạnh: khi xử lý event thất bại nhưng key vẫn cần tiếp tục, việc reset phải nằm trong luồng cleanup có chủ đích. Tuy vậy nếu lỗi xử lý có nghĩa ứng dụng phải dừng hoàn toàn, có thể chọn đóng watcher thay vì đặt lại key một cách máy móc; thiết kế error policy rõ ràng.

Đừng cố gọi `take()` vô hạn sau khi `ClosedWatchServiceException` xuất hiện; phải thoát vòng lặp và giải phóng state. Đừng nuốt `InterruptedException` rồi tiếp tục nếu đó là tín hiệu shutdown.

## 11. Giới hạn thực tế khi theo dõi hệ thống tệp

- **Theo dõi thư mục, không tự động đệ quy:** Đăng ký một thư mục không bảo đảm nhận event trong mọi thư mục con sâu bên dưới. Muốn giám sát cả cây, ứng dụng thường phải đăng ký riêng từng thư mục con và xử lý khi thư mục mới xuất hiện.
- **Provider phụ thuộc hệ điều hành:** Khả năng và độ trễ event khác nhau giữa filesystem cục bộ, network filesystem, môi trường container và JDK/provider.
- **Không bảo đảm mọi thay đổi đều sinh một event duy nhất:** Có thể có sự kiện gộp, OVERFLOW, sự kiện đến trễ hoặc các thay đổi diễn ra giữa lúc app xử lý.
- **Xử lý quyền truy cập:** Thư mục có thể bị di chuyển/xóa/mất quyền; khi không truy cập được, vòng phục hồi phải tránh retry quá nhanh.
- **Vùng nhớ và registry:** Nếu theo dõi nhiều thư mục, cần quản lý map từ key tới path, đếm số key valid và dọn key invalid để không giữ bộ nhớ/tài nguyên lâu dài.
- **Không thực hiện I/O nặng trong vòng nhận event nếu không kiểm soát tốc độ:** Những thao tác nặng có thể khiến watcher bị nghẽn và tăng nguy cơ OVERFLOW.
- **Đóng tài nguyên đúng lúc:** `WatchService` là `AutoCloseable`, thích hợp với `try-with-resources`; khi watcher đóng, mọi key của nó không còn phục vụ theo dõi.

Nếu cần đồng bộ chính xác trạng thái tệp giữa các tiến trình hoặc máy, `WatchService` chỉ nên là **tín hiệu phát hiện thay đổi**, không phải nguồn chân lý duy nhất. Có thể cần quét định kỳ, checksum/version, metadata hoặc cơ chế đồng bộ ở tầng ứng dụng.

## 12. Lỗi thường gặp và cách sửa

| Lỗi | Hệ quả | Cách sửa |
| --- | --- | --- |
| Quên `key.reset()` sau `pollEvents()` | Key có thể không tiếp tục chu kỳ báo hiệu | Gọi reset sau xử lý, kiểm tra boolean trả về |
| Coi `reset()==false` là “hôm nay không có event” | Giữ đăng ký đã invalid, có thể chờ vô hạn | Cleanup key và chọn chính sách re-register |
| Gọi `reset()` nhiều lần nhằm hồi sinh key đã cancel | Không tạo được đăng ký hợp lệ mới | `directory.register(watcher, kinds...)` khi còn hợp lệ |
| Coi `watcher.poll()==null` là key invalid | Dừng watcher đúng lúc chưa có event | Phân biệt queue trống và key invalid |
| Dùng `reset()` mà không đọc `pollEvents()` | Không xử lý các event đã tích lũy, khó kiểm soát mất event | Lấy/xử lý event rồi mới reset |
| Chỉ xử lý event đầu tiên trong danh sách | Bỏ thay đổi khác trong cùng lần báo hiệu | Duyệt toàn bộ `pollEvents()` |
| Không xử lý `OVERFLOW` | Bỏ lỡ thay đổi mà ứng dụng không biết | Quét lại thư mục và đồng bộ với thực tế |
| Cast `OVERFLOW.context()` thành `Path` | Sai dữ liệu, có thể `ClassCastException` | Kiểm tra kind trước khi đọc context |
| Xóa/tạo lại thư mục rồi tin key cũ tự hoạt động | Bỏ sót sự kiện sau khi thư mục thay thế | Phát hiện invalid, đăng ký mới và rescan |
| Nghĩ đăng ký thư mục cha bao phủ tự động toàn cây | Không nhận thay đổi sâu ở thư mục con | Đăng ký từng thư mục khi cần recursive watch |
| Không gỡ `WatchKey` đã invalid khỏi map | State sai, tăng bộ nhớ và báo cáo sai số watcher | Xóa registry entry, cập nhật mục tiêu còn active |
| Lờ `InterruptedException` hoặc `ClosedWatchServiceException` | Vòng lặp không shutdown đúng cách | Phân biệt interrupt/close, dừng có chủ đích |

**Ghi nhớ:** Đối với `WatchService`, mỗi lần nhận một key đã được signal, hãy **đọc các `WatchEvent`, xử lý hoặc ghi nhận khả năng mất sự kiện, rồi gọi `WatchKey.reset()` và kiểm tra kết quả**. `true` nghĩa đăng ký còn hợp lệ để tiếp tục theo dõi; `false` nghĩa đăng ký đã invalid và cần cleanup hoặc re-register theo nhu cầu. `reset()` không thể khôi phục một key đã hủy, không thay thế việc đăng ký lại và không bảo đảm toàn bộ lịch sử thay đổi của filesystem.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="watch-service-006">OVERFLOW và nhu cầu quét lại trạng thái</a>

<details>
<summary>Xem chi tiết</summary>

# OVERFLOW và nhu cầu quét lại trạng thái trong Java NIO WatchService

## 1. Tại sao WatchService có thể bỏ lỡ thay đổi?

`java.nio.file.WatchService` là cơ chế Java NIO.2 dùng để nhận thông báo khi nội dung một thư mục thay đổi. Ứng dụng thường đăng ký `ENTRY_CREATE`, `ENTRY_MODIFY` và `ENTRY_DELETE` để tự động xử lý file mới, làm mới chỉ mục, đọc lại cấu hình hoặc đồng bộ dữ liệu.

Tuy nhiên, WatchService **không phải nhật ký giao dịch bền vững**. Hệ điều hành, filesystem provider và hàng đợi thông báo có thể gộp hoặc loại bỏ sự kiện. Khi sự kiện bị mất, WatchService có thể báo loại sự kiện đặc biệt **`StandardWatchEventKinds.OVERFLOW`**.

~~~text
Thư mục: create A, modify B, delete C, create D, ...
                         |
                         v
             WatchService / provider
                         |
                   queue quá tải
                         |
             một số sự kiện bị mất
                         |
                         v
                WatchEvent OVERFLOW
                         |
                         v
                 ĐÁNH DẤU DIRTY
                         |
                         v
              QUÉT LẠI THƯ MỤC
                         |
                         v
             ĐỐI CHIẾU VỚI SNAPSHOT
~~~

**Ý nghĩa:** sau `OVERFLOW`, không còn bảo đảm ứng dụng đã biết tất cả thay đổi của thư mục chỉ nhờ những sự kiện đã nhận. Cách đúng là **quét lại trạng thái hiện có và đối chiếu với trạng thái đang lưu**, không đoán danh sách file từ những sự kiện bị mất.

## 2. Các loại sự kiện WatchEvent

| Loại sự kiện | Ý nghĩa | Thông tin thường dùng |
| --- | --- | --- |
| `ENTRY_CREATE` | Một entry xuất hiện | `event.context()` là tên `Path` tương đối của entry |
| `ENTRY_MODIFY` | Một entry có thay đổi | `event.context()` là tên `Path` tương đối |
| `ENTRY_DELETE` | Một entry biến mất | `event.context()` là tên `Path` tương đối |
| `OVERFLOW` | Sự kiện có thể đã mất hoặc bị loại bỏ | Không dùng context để suy ra file nào bị bỏ sót |

Đăng ký thư mục bằng:

~~~java
dir.register(watcher,
        StandardWatchEventKinds.ENTRY_CREATE,
        StandardWatchEventKinds.ENTRY_MODIFY,
        StandardWatchEventKinds.ENTRY_DELETE);
~~~

Đây chỉ là **mảnh Java minh họa**, với `Path dir` và `WatchService watcher` đã được tạo.

Không cần đăng ký `OVERFLOW` như một sự kiện file thông thường để xử lý tình huống event loss. Khi lấy các sự kiện, phải kiểm tra `event.kind() == StandardWatchEventKinds.OVERFLOW` **trước** khi ép kiểu context thành `Path`.

### OVERFLOW không có nghĩa là một file cụ thể đã thay đổi

Giả sử snapshot của ứng dụng là:

~~~text
a.txt = phiên bản cũ
b.txt = có tồn tại
~~~

Trong thời gian ứng dụng chưa lấy hết event:

~~~text
a.txt được sửa
b.txt bị xóa
c.txt được tạo
d.txt được tạo rồi bị xóa ngay
~~~

Hàng đợi có thể chỉ giữ được một phần các sự kiện đó và báo `OVERFLOW`. Khi quét lại, ứng dụng có thể nhận ra `a.txt` khác, `b.txt` mất, `c.txt` mới. Nhưng `d.txt` đã được tạo và xóa giữa hai lần quan sát có thể **không thể phát hiện**.

Do vậy:

- `OVERFLOW` cho biết **mất độ tin cậy về sự kiện đã xảy ra**, chứ không liệt kê sự kiện đã mất.
- Quét lại giúp tái lập **trạng thái quan sát được hiện tại**.
- Quét lại **không khôi phục đầy đủ lịch sử**, thứ tự và số lần sửa đã xảy ra.
- Một `ENTRY_MODIFY` cũng không bảo đảm tương ứng chính xác một lần ghi file; nhiều thay đổi có thể bị gộp và `WatchEvent.count()` có thể lớn hơn 1.

Nếu hệ thống phải xử lý từng giao dịch một cách chính xác dù file tồn tại rất ngắn, cần một giao thức hoặc hàng đợi bền vững khác, không thể chỉ dựa vào WatchService.

## 3. WatchService, WatchKey và reset

Chu trình WatchService cơ bản:

1. Tạo `WatchService` cho `FileSystem` phù hợp.
2. Đăng ký một thư mục với `Path.register(watcher, ...)`, nhận `WatchKey`.
3. Đợi `watcher.take()` hoặc gọi `poll()`.
4. Lấy sự kiện của key bằng `key.pollEvents()`.
5. Xử lý bình thường hoặc đánh dấu rescan nếu gặp `OVERFLOW`.
6. **Gọi `key.reset()`** để key có thể tiếp tục báo những sự kiện mới.
7. Nếu `reset()` trả `false`, key không còn hợp lệ; cần xử lý thay đổi vòng đời của directory hoặc đăng ký lại theo chính sách ứng dụng.

Một `WatchService` có thể quản lý nhiều thư mục. Mỗi `WatchKey` tương ứng một registration; dùng `key.watchable()` hoặc bản đồ `Map<WatchKey,Path>` để biết sự kiện thuộc thư mục nào.

Đặc biệt, **đăng ký thư mục cha không tự động theo dõi đệ quy** mọi thư mục con. Nếu muốn theo dõi cả cây, ứng dụng phải đăng ký từng thư mục con liên quan và duy trì các registration khi cây thay đổi.

### Mẫu phát hiện OVERFLOW

~~~java
WatchKey key = watcher.take();
boolean dirty = false;

for (WatchEvent<?> event : key.pollEvents()) {
    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
        dirty = true;
        continue;
    }

    @SuppressWarnings("unchecked")
    WatchEvent<Path> typed = (WatchEvent<Path>) event;
    Path changed = watchedDir.resolve(typed.context());

    // Có thể cập nhật gia tăng theo ENTRY_CREATE / MODIFY / DELETE.
    // Không giả định file còn tồn tại khi thực sự truy cập.
}

if (dirty) {
    // Quét lại watchedDir; đối chiếu với trạng thái đã lưu.
}

if (!key.reset()) {
    // Key invalid: kiểm tra lại watcher và thư mục đã đăng ký.
}
~~~

Đây là **mảnh Java trong vòng xử lý sự kiện**, không phải chương trình độc lập. `watcher` và `watchedDir` cần được khai báo trong ứng dụng thực tế.

Với `OVERFLOW`, không ép `event.context()` thành `Path`; loại sự kiện này không cung cấp một danh sách tên file đã mất.

`key.reset()` cần được thực hiện trên đường xử lý bình thường, kể cả sau OVERFLOW. Nếu code có nhiều nhánh hoặc có thể ném ngoại lệ, nên tổ chức `finally` để không bỏ quên reset. `reset() == false` không thể được sửa chỉ bằng cách gọi `reset()` lặp vô hạn: cần kiểm tra thư mục có bị xóa, đổi tên, thay quyền hoặc filesystem ngừng theo dõi.

## 4. Tại sao một snapshot phải được quét lại?

Ứng dụng thường có **trạng thái nội bộ** khác với filesystem:

~~~text
Trạng thái RAM trước khi mất sự kiện:
  config.json → version 2
  report.csv  → tồn tại
  inbox/a.txt → tồn tại

Trạng thái thực tế sau khi mất sự kiện:
  config.json → version 3
  report.csv  → đã bị xóa
  inbox/a.txt → vẫn tồn tại
  inbox/b.txt → mới được tạo
~~~

Nếu không rescan, cache hoặc index vẫn nghĩ `config.json` ở phiên bản 2 và `report.csv` còn tồn tại. Sau OVERFLOW, ứng dụng cần:

- **Chụp lại trạng thái hiện tại** của những thư mục chịu ảnh hưởng.
- Phát hiện tên mới (added), tên không còn (removed), tên còn nhưng nội dung/thuộc tính đã thay đổi (changed).
- Cập nhật các cấu trúc dữ liệu, công việc chờ, index hoặc cache theo kết quả.
- Tiếp tục tiêu thụ sự kiện mới; nếu có thay đổi trong lúc quét, có thể cần quét thêm lượt nữa.

Mục tiêu là **reconciliation**: đưa biểu diễn của ứng dụng hội tụ về filesystem hiện tại, thay vì dựng lại một chuỗi sự kiện vốn đã thất lạc.

### Nên lưu gì trong snapshot?

| Dữ liệu snapshot | Ưu điểm | Hạn chế |
| --- | --- | --- |
| Tên/đường dẫn | Đối chiếu tạo/xóa đơn giản | Không biết nội dung có thay đổi |
| `Files.size` | Rẻ, phát hiện thay đổi độ dài | Nội dung có thể thay đổi nhưng giữ nguyên size |
| `Files.getLastModifiedTime` | Tương đối nhanh | Độ phân giải thời gian khác nhau; metadata có thể bị giữ nguyên/đặt lại |
| `BasicFileAttributes.fileKey()` | Có thể giúp nhận diện một object filesystem | Có thể null hoặc không ổn định giữa provider |
| Hash nội dung | Nhận ra nhiều thay đổi mà metadata không thể hiện | Tốn đọc đĩa/CPU, khó dùng với file đang thay đổi |
| Version/manifest do ứng dụng quản lý | Có thể có nghĩa chính xác cho domain | Cần thiết kế giao thức từ đầu |

Không có một signature metadata rẻ tiền nào bảo đảm phát hiện mọi thay đổi nội dung trên mọi filesystem. Với yêu cầu độ chính xác cao, xây một protocol chứa version/checksum và quy tắc công bố file hoàn chỉnh.

## 5. Thuật toán rescan và đối chiếu

Một thuật toán đơn giản:

~~~text
known = snapshot đã lưu trước đó

khi nhận ENTRY_CREATE/MODIFY/DELETE:
    cập nhật từng mục hoặc đánh dấu mục cần kiểm tra

khi nhận OVERFLOW:
    dirty = true

nếu dirty:
    current = scanDirectory()
    added   = tên trong current nhưng không có trong known
    removed = tên trong known nhưng không có trong current
    changed = tên có trong cả hai nhưng signature khác
    cập nhật cache/index/job theo các khác biệt
    known = current
    chỉ xóa dirty nếu không có thay đổi mới trong lúc scan
~~~

Đây là pseudocode. Ứng dụng thật cần quyết định rõ xử lý thay đổi file trong lúc scan:

- **Register trước rồi mới lấy snapshot ban đầu.** Nếu scan trước và register sau, có một khoảng hở mà thay đổi không được watcher báo.
- **Scan không phải transaction nguyên tử.** File có thể xuất hiện/biến mất ngay sau khi liệt kê; `NoSuchFileException` khi đọc metadata là một tình huống cần xử lý.
- **Không mất cờ dirty mới.** Nếu OVERFLOW xảy ra trong lúc worker đang scan, không được đánh dấu sạch theo snapshot cũ. Dùng generation counter hoặc trạng thái đồng bộ rõ ràng.
- **Gộp nhiều yêu cầu quét lại.** Một directory thường chỉ nên có một lượt scan đang chạy; nếu dirty tiếp thì lên lịch thêm lượt sau.
- **Bảo vệ thread nhận sự kiện.** Nếu scan rất lâu trên watcher thread, hàng đợi có thể tiếp tục quá tải và gây thêm OVERFLOW.

Với thư mục nhỏ, chiến lược quét lại mọi khi có sự kiện có thể đơn giản và đáng tin hơn logic incremental phức tạp. Với cây hàng triệu file, thường cần cập nhật gia tăng và full rescan khi OVERFLOW hoặc theo lịch định kỳ.

## 6. Ví dụ Java 17+ chạy độc lập: mô phỏng mất sự kiện rồi rescan

**Không thể ép OVERFLOW thật xảy ra một cách di động và xác định**, vì nó phụ thuộc hệ điều hành và provider. Ví dụ sau tạo một `WatchService` thật, rồi **cố ý không dựa vào những event phát sinh** để mô phỏng tình huống đã bỏ lỡ thông báo. Nó gọi `reconcile` và đối chiếu trạng thái file bằng nội dung text để stdout xác định.

Bản demo chỉ dùng vài file UTF-8 nhỏ. Trong production, không nên đọc toàn bộ các file lớn vào RAM mỗi lần quét lại.

Lưu thành `OverflowRescanDemo.java`:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

public class OverflowRescanDemo {
    private static Map<String, String> snapshot(Path dir)
            throws IOException {
        Map<String, String> result = new TreeMap<>();

        try (Stream<Path> paths = Files.list(dir)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                result.put(file.getFileName().toString(),
                        Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return result;
    }

    private static void reconcile(Path dir, Map<String, String> known)
            throws IOException {
        Map<String, String> current = snapshot(dir);
        Set<String> allNames = new TreeSet<>();
        allNames.addAll(known.keySet());
        allNames.addAll(current.keySet());

        for (String name : allNames) {
            if (!known.containsKey(name)) {
                System.out.println("ADDED " + name);
            } else if (!current.containsKey(name)) {
                System.out.println("REMOVED " + name);
            } else if (!known.get(name).equals(current.get(name))) {
                System.out.println("CHANGED " + name);
            }
        }

        known.clear();
        known.putAll(current);
    }

    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("watch-rescan-");

        try {
            Path alpha = dir.resolve("alpha.txt");
            Path old = dir.resolve("obsolete.txt");
            Path newest = dir.resolve("new.txt");

            Files.writeString(alpha, "A", StandardCharsets.UTF_8);
            Files.writeString(old, "old", StandardCharsets.UTF_8);

            try (WatchService watcher =
                         dir.getFileSystem().newWatchService()) {
                // Đăng ký trước khi chụp baseline.
                dir.register(watcher,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);

                Map<String, String> known = snapshot(dir);
                System.out.println("BASELINE " + known.keySet());

                // Giả lập ứng dụng mất thông báo về ba thao tác.
                Files.writeString(alpha, "A2", StandardCharsets.UTF_8);
                Files.delete(old);
                Files.writeString(newest, "new", StandardCharsets.UTF_8);

                System.out.println("RESCAN after missed events");
                reconcile(dir, known);
                System.out.println("CURRENT " + known.keySet());

                // Nếu có OVERFLOW thực trong các event đã được
                // chuyển tới queue, reconcile cũng là lối phục hồi.
                // Những ENTRY_* còn lại ở đây cố ý không dùng làm
                // nguồn sự thật, vì snapshot đã được làm mới.
                WatchKey key;
                while ((key = watcher.poll()) != null) {
                    boolean overflow = false;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind()
                                == StandardWatchEventKinds.OVERFLOW) {
                            overflow = true;
                        }
                    }
                    if (overflow) {
                        reconcile(dir, known);
                    }
                    if (!key.reset()) {
                        break;
                    }
                }
            }
        } finally {
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.toList()) {
                    Files.deleteIfExists(file);
                }
            }
            Files.deleteIfExists(dir);
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac OverflowRescanDemo.java
java OverflowRescanDemo
~~~

Stdout khi chạy trong thư mục tạm không có thay đổi từ bên ngoài:

~~~text
BASELINE [alpha.txt, obsolete.txt]
RESCAN after missed events
CHANGED alpha.txt
ADDED new.txt
REMOVED obsolete.txt
CURRENT [alpha.txt, new.txt]
~~~

Thứ tự ổn định nhờ `TreeMap` và `TreeSet`:

1. Snapshot đầu có `alpha.txt = "A"` và `obsolete.txt = "old"`.
2. Sau đó `alpha.txt` đổi thành `"A2"`, `obsolete.txt` bị xóa và `new.txt` xuất hiện.
3. `reconcile` so sánh tên và nội dung, báo chính xác ba khác biệt còn quan sát được.
4. `known` được cập nhật, nên một lượt reconcile tiếp theo không in lại các khác biệt nếu directory không đổi.
5. Demo tạo watcher thực để minh họa registration và xử lý `OVERFLOW`, nhưng **không khẳng định OVERFLOW đã xảy ra thật**. Lời gọi rescan được thực hiện chủ động để mô phỏng hậu quả bỏ sót thông báo.
6. `key.reset()` được gọi với các key đã được lấy, các tài nguyên và file tạm được dọn sau khi dùng.

Phần demo chỉ quét một directory nông, nội dung text nhỏ; nó chưa có event loop production chạy liên tục, chính sách retry khi scan gặp race, hoặc registration đệ quy.

## 7. Mẫu vòng event loop cần có ở ứng dụng thực tế

Mảnh code xử lý một `WatchKey` với cờ dirty:

~~~java
WatchKey key = watcher.take();
boolean needsRescan = false;
Path dir = registrations.get(key);

try {
    for (WatchEvent<?> event : key.pollEvents()) {
        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
            needsRescan = true;
            continue;
        }

        @SuppressWarnings("unchecked")
        WatchEvent<Path> typed = (WatchEvent<Path>) event;
        Path child = dir.resolve(typed.context());

        // Với ENTRY_* có thể cập nhật gia tăng hoặc
        // đánh dấu child cần được kiểm tra lại.
    }

    if (needsRescan) {
        // Đưa yêu cầu rescan(dir) vào hàng đợi công việc.
    }
} finally {
    if (!key.reset()) {
        // Registration invalid: điều tra và đăng ký lại nếu cần.
    }
}
~~~

Đây là **mảnh code minh họa**, cần `registrations`, `watcher` và chính sách ứng dụng đã được định nghĩa. Nếu nhiều thư mục được đăng ký, cần map key tới đúng directory. Nếu `key.reset()` thất bại, chỉ rescan thôi chưa đủ để tiếp tục nhận sự kiện mới: có thể cần đăng ký lại thư mục hoặc theo dõi parent.

Với số lượng file lớn, đừng đọc hàng trăm nghìn file ngay trong thread gọi `watcher.take()`. Tách một worker quét, tiếp tục lấy event và duy trì dirty flag/generation là cách giảm nguy cơ quá tải lặp lại.

### Đừng mất OVERFLOW xảy ra trong lúc đang quét

Một lỗi thiết kế thường gặp:

~~~text
Worker thấy dirty=true
Worker bắt đầu scan
Watcher thấy OVERFLOW mới → đặt dirty=true
Worker scan xong → gán dirty=false
Lần OVERFLOW mới bị xóa mất, không còn rescan!
~~~

Có thể quản lý số thế hệ:

~~~text
onOverflow: changeGeneration += 1

trước scan: capturedGeneration = changeGeneration
scan và áp dụng snapshot
nếu changeGeneration != capturedGeneration:
    phải quét tiếp
ngược lại:
    có thể đánh dấu đã bắt kịp thời điểm quan sát
~~~

Đây là pseudocode về **logic quản lý sự kiện**; trong nhiều thread cần synchronization hoặc kiểu atomic phù hợp. Không chỉ thay bool mà không có quy tắc ownership hoặc memory visibility.

## 8. Theo dõi cây thư mục và các cuộc đua khi quét lại

WatchService chuẩn đăng ký theo **từng directory**, không tự quét sâu toàn bộ cây. Nếu ứng dụng theo dõi một root có các thư mục con:

- Duyệt và đăng ký từng subdirectory cần theo dõi, chẳng hạn bằng `Files.walkFileTree`.
- Khi phát hiện directory mới, **đăng ký ngay** và quét nội dung directory đó vì file bên trong có thể xuất hiện trước khi registration hoàn tất.
- Khi key invalid hoặc một subdirectory bị xóa/đổi tên, cập nhật mapping registration.
- Khi có OVERFLOW ở một directory, quét lại phạm vi liên quan. Nếu cần bảo đảm trạng thái của cả cây, phải đối chiếu cả tập directory registration và những thư mục mới.
- Cần chính sách symlink rõ ràng, tránh vô tình duyệt ngoài root hoặc lặp qua đường dẫn liên kết.

### File đang được ghi dở

`ENTRY_CREATE` có thể đến khi producer mới mở file và chưa ghi hết. Trong lần rescan sau OVERFLOW cũng có thể thấy file chưa hoàn chỉnh. Nếu ứng dụng phải xử lý file nguyên vẹn, nên dùng giao thức như:

~~~text
Producer: ghi report.tmp → flush/close → rename thành report.ready
Consumer: chỉ xử lý file *.ready
~~~

Đây là mô hình thiết kế minh họa; tính nguyên tử của rename còn phụ thuộc filesystem và lựa chọn API, không nên giả định luôn đúng xuyên filesystem.

### File biến mất giữa list và read

Sau `Files.list(dir)`, file có thể bị xóa trước khi `Files.readAttributes(...)` hoặc `Files.readString(...)`. `NoSuchFileException` là một race bình thường trong môi trường có thay đổi liên tục. Một scanner production cần xử lý lỗi từng item, đánh dấu retry và giới hạn vòng scan khi thư mục không ổn định.

### Mức chính xác của metadata

Nếu file bị sửa nhưng vẫn giữ kích thước và timestamp, signature dựa trên `size + mtime` có thể không đổi. Hash nội dung phát hiện nhiều trường hợp hơn, nhưng đắt và cũng gặp race khi file đang ghi. Chọn cơ chế theo yêu cầu nghiệp vụ, thay vì mặc định metadata nhanh là bằng chứng file bất biến.

## 9. OVERFLOW không giải quyết bằng tăng tốc vô hạn

Nếu OVERFLOW thường xuyên, đó là tín hiệu hệ thống không theo kịp lưu lượng thay đổi hoặc provider có giới hạn riêng. Có thể cải thiện:

- Giữ thread lấy WatchKey nhẹ: nhanh chóng nhận, phân loại và reset key.
- Gộp nhiều sự kiện liên tiếp liên quan tới cùng file/directory.
- Hạn chế rescan trùng lặp và chia giai đoạn scan thư mục lớn.
- Ghi nhận số lần OVERFLOW, độ trễ từ event tới xử lý, độ dài backlog và thời gian quét để phát hiện quá tải.
- Nếu hệ thống phải xử lý từng tác vụ, chuyển nguồn sự thật sang journal/manifest/queue bền vững và dùng WatchService làm tín hiệu đánh thức.
- Định kỳ đối chiếu trạng thái ngay cả khi không có OVERFLOW nếu tính đúng đắn quan trọng hơn hiệu năng.

Không nên chỉ giả định cấp phát buffer ở tầng ứng dụng lớn hơn sẽ loại trừ mọi OVERFLOW: khả năng xếp hàng và thông báo nằm ở provider và OS, không hoàn toàn do kích thước ByteBuffer hay queue tự tạo trong Java quyết định.

## 10. Các lỗi phổ biến

**Lỗi 1 — Bỏ qua OVERFLOW.** Ứng dụng tiếp tục tin tưởng snapshot cũ và bỏ sót file. Cần đánh dấu dirty rồi reconcile.

**Lỗi 2 — Ép context của OVERFLOW thành Path.** OVERFLOW không mang danh sách file bị mất event. Không dựa vào context để đoán một đường dẫn cụ thể.

**Lỗi 3 — Nghĩ rescan phục hồi mọi sự kiện quá khứ.** Nó chủ yếu phục hồi trạng thái hiện tại; tạo-rồi-xóa rất nhanh có thể không còn dấu vết.

**Lỗi 4 — Quét baseline trước rồi mới register watcher.** Sự kiện ở khoảng hở có thể mất. Register trước, scan sau và xử lý event đến trong lúc scan.

**Lỗi 5 — Không reset key.** Sau `pollEvents()` phải `key.reset()`; nếu trả false thì xử lý registration không còn hiệu lực.

**Lỗi 6 — Cho rằng đăng ký root tự theo dõi đệ quy.** Cần đăng ký các subdirectory và tái đồng bộ chúng khi có thay đổi cây.

**Lỗi 7 — Quét rất lâu trong watcher thread.** Việc chậm lấy event có thể tạo OVERFLOW mới. Chuyển scan nặng sang worker có quản lý dirty.

**Lỗi 8 — Xóa dirty quá sớm.** Một OVERFLOW mới trong khi scan đang chạy có thể bị ghi đè trạng thái. Dùng generation hoặc chính sách đồng bộ.

**Lỗi 9 — Tin rằng mtime/size phát hiện mọi thay đổi.** Metadata có thể không đổi dù payload đã đổi; ứng dụng có yêu cầu chặt cần checksum/version.

**Lỗi 10 — Dùng WatchService như queue exactly-once.** API không lưu bền hoặc replay đầy đủ các sự kiện; cần giao thức xử lý công việc riêng.

## 11. Tự kiểm tra

1. `OVERFLOW` nói gì? **Có thể đã mất hoặc loại bỏ sự kiện**; danh sách sự kiện đã đọc không còn đầy đủ.
2. Vì sao phải quét lại? **So sánh trạng thái filesystem hiện tại với snapshot cũ** để tìm thêm/xóa/đổi vẫn quan sát được.
3. Có tìm được một file đã tạo rồi xóa giữa hai lần scan không? **Không bảo đảm**.
4. Có thể dùng `event.context()` từ OVERFLOW để tìm tên file bị mất event không? **Không**.
5. Sau `key.pollEvents()` cần gọi gì? **`key.reset()`**; xử lý khi kết quả false.
6. Theo dõi directory cha có tự theo dõi mọi cấp con không? **Không**.
7. Vì sao register trước baseline? **Giảm khoảng hở không có khả năng nhận event**.
8. Nếu filesystem thay đổi trong lúc scan, snapshot có atomic không? **Không**; cần retry/reconcile theo chính sách.
9. Nếu trùng thời điểm rescan xong lại có OVERFLOW, làm sao tránh mất tín hiệu? **Dùng dirty-generation và không xóa cờ mới một cách vô điều kiện**.
10. Nếu hệ thống cần exactly-once, có nên chỉ dùng WatchService không? **Không**; cần nguồn sự thật bền vững, idempotency và xác nhận xử lý.

**Ghi nhớ:** `WatchService` là cơ chế **thông báo**, không phải nhật ký thay đổi bảo đảm đầy đủ. `StandardWatchEventKinds.OVERFLOW` là tín hiệu phải **ngừng tin rằng mọi event đã được quan sát** và **quét lại trạng thái thực tế**. Hãy so sánh snapshot, cập nhật cache/index, tiếp tục xử lý event mới, quản lý `WatchKey.reset()`, registration đệ quy nếu cần và race khi quét. Mục tiêu đạt được là **hội tụ với trạng thái filesystem**, không phục hồi lịch sử đã mất.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="watch-service-007">Đăng ký đệ quy và thư mục được tạo mới</a>

<details>
<summary>Xem chi tiết</summary>

# WatchService: đăng ký đệ quy và theo dõi thư mục được tạo mới trong Java NIO

Trong Java NIO, `java.nio.file.WatchService` theo dõi **các sự kiện thay đổi của thư mục**: tạo, xóa, sửa entry và mất sự kiện do tràn hàng đợi. Khi xây trình theo dõi toàn bộ cây thư mục, khó khăn lớn nhất là: **WatchService không tự đăng ký đệ quy**. Đăng ký thư mục cha **không** mặc nhiên đăng ký các thư mục con đã tồn tại; khi một thư mục mới xuất hiện, ứng dụng cũng phải đăng ký nó và các thư mục con của nó.

Vì vậy một watcher đệ quy đúng cần hai phần phối hợp: **quét cây hiện có để đăng ký ban đầu** và **xử lý sự kiện `ENTRY_CREATE` để mở rộng phạm vi giám sát**. Ngoài ra cần kiểm soát race condition, mất sự kiện (`OVERFLOW`), vòng đời `WatchKey`, symbolic link và chính sách đối chiếu lại filesystem khi tính chính xác quan trọng.

## 1. WatchService quan sát cái gì?

Ba thành phần chính:

| Thành phần | Vai trò |
| --- | --- |
| `WatchService` | Dịch vụ thu nhận thay đổi, được tạo từ `FileSystem.newWatchService()`. |
| `Path.register(watcher, kinds...)` | Đăng ký **một thư mục** với dịch vụ để quan sát các loại sự kiện. |
| `WatchKey` | Đại diện một đăng ký, được trả khi đăng ký và được đưa vào hàng đợi khi có sự kiện. |

Những loại sự kiện thường gặp:

- **`ENTRY_CREATE`**: một entry mới xuất hiện trong thư mục được đăng ký, có thể là file hoặc thư mục.
- **`ENTRY_DELETE`**: một entry biến mất khỏi thư mục đã đăng ký.
- **`ENTRY_MODIFY`**: một entry có thay đổi thuộc loại provider báo theo cơ chế giám sát.
- **`OVERFLOW`**: đã có sự kiện có thể bị mất hoặc bị loại bỏ; đây là tín hiệu cần đối chiếu lại filesystem.

`WatchEvent.context()` đối với các event entry chuẩn thường là **tên entry tương đối với thư mục được đăng ký**, không phải đường dẫn tuyệt đối. Vì vậy ứng dụng cần lưu quan hệ `Map<WatchKey, Path>` để ghép:

```java
Path directory = registeredDirectories.get(key);
Path affected = directory.resolve((Path) event.context());
```

Đây là snippet; `event.context()` phải được cast phù hợp cho entry events; **không** xử lý `OVERFLOW` như một `Path` bình thường vì context của nó có thể là `null`.

### Vì sao chỉ đăng ký cha là chưa đủ?

```text
root/                 <- Đã register(root)
  report.txt          <- CREATE/DELETE/MODIFY có thể xuất hiện ở root
  docs/               <- Không tự được giám sát!
    guide.md          <- Sự kiện trực tiếp trong docs có thể không được thấy
    images/           <- Cũng không được giám sát
      logo.png
```

Đăng ký `root` chỉ theo dõi **entry nằm ngay trong root**. Muốn nhận sự kiện cho `docs/guide.md`, cần đăng ký `docs`; muốn theo dõi `docs/images/logo.png`, cần đăng ký `docs/images`.

**Không có một lời gọi chuẩn kiểu `watchRecursively(root)` trong WatchService API của Java SE 17.** Đăng ký đệ quy là thuật toán do ứng dụng viết bằng các API như `Files.walkFileTree`.

## 2. Hai pha của đăng ký đệ quy

**Pha A — Đăng ký các thư mục đã tồn tại:**

1. Kiểm tra đường dẫn gốc là thư mục và có quyền truy cập phù hợp.
2. Tạo một `WatchService`.
3. Duyệt cây thư mục với `Files.walkFileTree(root, visitor)`.
4. Trong `preVisitDirectory`, gọi `dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)`.
5. Lưu `WatchKey → Path` để khi nhận sự kiện biết thư mục chứa entry.

**Pha B — Đăng ký thư mục mới khi đang chạy:**

1. Lấy `WatchKey` từ `watcher.take()` hoặc `watcher.poll(...)`.
2. Tra thư mục tương ứng trong map, duyệt `key.pollEvents()`.
3. Nếu nhận `ENTRY_CREATE` và entry vừa tạo hiện là thư mục, gọi **đăng ký đệ quy trên chính thư mục mới**, không chỉ đăng ký một cấp.
4. Xử lý business event theo yêu cầu, sau đó gọi `key.reset()`.
5. Nếu `reset() == false`, khóa không còn hợp lệ; xóa khỏi map và quyết định có cần khôi phục/đăng ký lại hay không.

Sơ đồ:

```text
root/ ── walkFileTree ──> watch root, old/, old/sub/
  |
  +── ENTRY_CREATE new/ ──> isDirectory(new/, NOFOLLOW_LINKS)?
                                  |
                                 Có
                                  |
                                  v
                         walkFileTree(new/)
                           ├── register(new/)
                           └── register(new/sub/) nếu đã tồn tại

Mọi entry nằm dưới new/sub/ xuất hiện SAU khi đăng ký thành công
có thể được đưa vào hàng đợi WatchService của new/sub/.
```

**Lý do cần `walkFileTree(newDir)` thay vì chỉ `newDir.register(...)`:** bên khác có thể tạo cả `new/sub/deep` rất nhanh trước khi event của `new` được xử lý. Khi ấy việc duyệt cây mới có thể tìm được các thư mục con đã xuất hiện, dù ứng dụng chưa kịp theo dõi chúng lúc được tạo.

## 3. Hàm đăng ký đệ quy dùng Files.walkFileTree

API `Files.walkFileTree` duyệt từng directory và cho phép register đúng ở thời điểm vào thư mục:

```java
// Snippet: watcher là WatchService đang mở,
// directories là Map<WatchKey, Path>.
Files.walkFileTree(root, new SimpleFileVisitor<>() {
    @Override
    public FileVisitResult preVisitDirectory(
            Path dir, BasicFileAttributes attrs) throws IOException {
        WatchKey key = dir.register(watcher,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);
        directories.put(key, dir);
        return FileVisitResult.CONTINUE;
    }
});
```

Đoạn này **không độc lập**: cần các imports từ `java.nio.file`, `java.nio.file.attribute.BasicFileAttributes`, `java.io.IOException`, và các biến `root`, `watcher`, `directories`.

Mặc định overload trên không đi theo symbolic link. Khi phải giới hạn cây trong một root đáng tin cậy, **không tùy tiện bật `FOLLOW_LINKS`**, vì symlink có thể tạo vòng hoặc dẫn ra ngoài vùng cho phép. Nếu ứng dụng chủ động hỗ trợ symlink, cần giải bài toán canonical path, quyền truy cập, vòng lặp và chính sách giới hạn phạm vi.

`preVisitDirectory` có thể ném `IOException`/`AccessDeniedException` hoặc provider không hỗ trợ đăng ký. Tùy yêu cầu, ứng dụng nên ghi nhận lỗi và tiếp tục với nhánh khác, hoặc coi đây là lỗi khiến watcher chưa giám sát đầy đủ. Đừng thông báo “theo dõi toàn bộ” nếu có nhánh không đăng ký thành công.

## 4. Chương trình Java 17+ hoàn chỉnh: theo dõi thư mục mới và con cháu

Chương trình tự tạo một cây thư mục tạm, đăng ký ban đầu, tạo một subtree mới rồi đợi `ENTRY_CREATE` của thư mục cha mới. Khi xử lý sự kiện đó, nó dùng `walkFileTree` để đăng ký cả subtree (bao gồm thư mục con đã có), sau đó tạo file nằm sâu bên trong để chứng minh watcher đã giám sát được thư mục mới.

Ví dụ sử dụng **một thread**, không cần chương trình bên ngoài tạo file. Kết quả được in ở dạng đường dẫn tương đối để không phụ thuộc vị trí thư mục tạm trên Windows/Linux/macOS.

Lưu thành **`RecursiveWatchDemo.java`**:

```java
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.nio.file.StandardWatchEventKinds.*;

public class RecursiveWatchDemo {
    private static void registerTree(Path start, WatchService watcher,
                                     Map<WatchKey, Path> directories)
            throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(
                    Path dir, BasicFileAttributes attrs) throws IOException {
                WatchKey key = dir.register(watcher,
                        ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                directories.put(key, dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void awaitCreated(Path expected, WatchService watcher,
                                     Map<WatchKey, Path> directories)
            throws IOException, InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            WatchKey key = watcher.poll(1, TimeUnit.SECONDS);
            if (key == null) {
                continue;
            }

            Path dir = directories.get(key);
            boolean seen = false;
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == OVERFLOW) {
                    throw new IOException("WatchService overflow: need rescan");
                }
                if (dir == null) {
                    continue;
                }
                Path child = dir.resolve((Path) event.context());
                if (event.kind() == ENTRY_CREATE) {
                    if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                        registerTree(child, watcher, directories);
                    }
                    if (child.equals(expected)) {
                        seen = true;
                    }
                }
            }

            if (!key.reset()) {
                directories.remove(key);
            }
            if (seen) {
                return;
            }
        }
        throw new IOException("Timed out waiting for " + expected.getFileName());
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("recursive-watch-");
        try {
            Files.createDirectories(root.resolve("existing/deep"));
            try (WatchService watcher = FileSystems.getDefault()
                    .newWatchService()) {
                Map<WatchKey, Path> directories = new HashMap<>();
                registerTree(root, watcher, directories);
                System.out.println("initial directories=" + directories.size());

                Path fresh = root.resolve("new");
                Files.createDirectories(fresh.resolve("sub"));
                awaitCreated(fresh, watcher, directories);
                System.out.println("after new subtree=" + directories.size());

                Path file = fresh.resolve("sub/hello.txt");
                Files.writeString(file, "hello");
                awaitCreated(file, watcher, directories);
                System.out.println("created=" +
                        root.relativize(file).toString().replace('\\', '/'));
            }
        } finally {
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder())
                        .toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
```

Biên dịch/chạy với JDK 17+:

```bash
javac -encoding UTF-8 RecursiveWatchDemo.java
java RecursiveWatchDemo
```

Stdout đã được xác nhận bằng biên dịch và chạy thực tế trên một filesystem hỗ trợ WatchService:

```text
initial directories=3
after new subtree=5
created=new/sub/hello.txt
```

**Phân tích từng bước:**

1. Trước khi đăng ký, ví dụ tạo `root/`, `root/existing/` và `root/existing/deep/`. Duyệt cây đã có sẽ đăng ký đúng **3 thư mục**.
2. `Files.createDirectories(root.resolve("new/sub"))` tạo `new/` và `new/sub/`. Khi observer xử lý `ENTRY_CREATE` của `new`, cả hai thư mục mới đã tồn tại.
3. `registerTree(new, ...)` đi xuống `new/sub/` và tạo thêm hai đăng ký, nên tổng là **5**.
4. Chương trình **đợi đăng ký hoàn tất rồi mới tạo file** `new/sub/hello.txt`. Đây là điểm rất quan trọng để tránh race trong demo: nếu tạo file trước khi `new/sub` được đăng ký, sự kiện của file có thể không được ghi nhận.
5. Khi source thư mục `new/sub/` phát `ENTRY_CREATE`, `context()` là tên `hello.txt`, ghép với `Path` của khóa thành đường dẫn chính xác.
6. Hàm dùng `watcher.poll(1, TimeUnit.SECONDS)` để không chờ vĩnh viễn trong demo; deadline tổng thể giới hạn 5 giây. Nếu provider/file system không hỗ trợ sự kiện kịp thời, nó báo lỗi thay vì khẳng định mọi môi trường đều có output giống nhau.
7. Mỗi key đã nhận được gọi `reset()`. Nếu key invalid, map được cập nhật. Cuối cùng `try-with-resources` đóng WatchService và `finally` dọn cây thư mục tạm sau khi đã dừng giám sát.

**Phạm vi ví dụ:** cố ý chỉ chờ hai event cụ thể, ném lỗi khi nhận `OVERFLOW` và không triển khai recovery đầy đủ. Để xây watcher production, xem các chiến lược rescan, giám sát quyền truy cập và dừng dịch vụ ở các phần tiếp theo.

## 5. Tại sao đăng ký thư mục mới vẫn có khoảng trống sự kiện?

Đây là điểm cần hiểu sâu nhất. Xét trật tự sau:

```text
T0: root đã được đăng ký.
T1: Tiến trình khác tạo root/new/.
T2: Tiến trình khác tạo root/new/a.txt.
T3: Thread watcher lấy ENTRY_CREATE của root/new/.
T4: Watcher mới gọi register(root/new/).
```

Sự kiện `a.txt` ở **T2** xảy ra trước khi `root/new/` được register tại **T4**. `WatchService` không có lời hứa cung cấp lịch sử thay đổi trước khi đăng ký, nên ứng dụng **có thể bỏ lỡ sự kiện tạo `a.txt`**, dù hiện tại file đó tồn tại.

Đăng ký `new/` và các thư mục con bằng `walkFileTree` chỉ giải quyết **phạm vi các directory hiện còn tồn tại**, không tái tạo đầy đủ những sự kiện file đã bị bỏ lỡ.

### Chiến lược: đăng ký rồi đối chiếu trạng thái

Khi cần đồng bộ file đáng tin, không chỉ phụ thuộc `ENTRY_CREATE`:

1. Đăng ký các thư mục trong subtree mới nhanh nhất có thể.
2. Sau khi đăng ký, quét danh sách file/thư mục hiện tại của subtree.
3. So sánh với **snapshot/index trạng thái đã biết**, phát hiện file mới, file đã xóa, file đã sửa, có thể dựa trên metadata và khi cần là hash nội dung.
4. Xử lý event trong lúc quét cùng quá trình đối chiếu; thiết kế **idempotency/deduplication** vì một file có thể vừa xuất hiện trong scan, vừa xuất hiện trong event queue.
5. Nếu có `OVERFLOW` hoặc nghi ngờ thiếu dữ liệu, quét lại subtree/root theo chính sách.

Không có phép quét + register nào mặc nhiên tạo ra một **snapshot atomic của toàn bộ cây filesystem đang bị sửa đồng thời**. Nếu thực sự cần bảo đảm không mất thay đổi giữa nhiều tiến trình, cân nhắc giao thức phối hợp writer, snapshot filesystem, change journal native hoặc cơ chế lưu lịch sử giao dịch; `WatchService` chỉ là nguồn thông báo, không phải log sự kiện bền vững.

## 6. Cách đối chiếu (reconciliation) thay vì tin mọi event là đầy đủ

Ứng dụng đồng bộ thư mục thường giữ index:

```text
Map<Path, FileInfo> knownFiles
Map<WatchKey, Path> registeredDirectories

FileInfo có thể chứa:
  isDirectory
  size
  lastModifiedTime
  optional fileKey / contentHash
```

Một vòng reconciliation:

```text
Scan filesystem root
     |
     v
Lập snapshot quan sát hiện tại
     |
     +-- Path mới: bổ sung/index, xử lý ingest
     +-- Path mất: đánh dấu/xử lý delete
     +-- Metadata/hash đổi: cập nhật/xử lý modify
     |
     v
Đăng ký lại các directory còn thiếu
     |
     v
Áp dụng tiếp các event đã đến (dedup theo trạng thái/id)
```

Điều kiện vận hành quan trọng:

- `Files.exists(path)` và `Files.isDirectory(path)` chỉ là **trạng thái ở thời điểm gọi**. Entry có thể biến mất hoặc đổi loại ngay sau đó.
- Khi một file vừa được tạo, writer có thể chưa ghi xong. Nếu yêu cầu xử lý một file hoàn chỉnh, dùng quy ước publish qua rename, marker/manifest, kiểm tra kích thước ổn định hoặc phối hợp bên tạo file.
- Timestamp/kích thước có giới hạn độ phân giải và không chứng minh nội dung không đổi. Nếu correctness cao, cân nhắc content hash hoặc protocol phù hợp.
- Rename/move có thể được báo bằng chuỗi delete/create/modify khác nhau giữa provider; WatchService chuẩn không cung cấp transaction rename với cả source và destination một cách portable.
- Sau quét, ứng dụng có thể tiếp tục nhận event trùng cho cùng trạng thái; handler nên **idempotent** để tránh nhập lại cùng file nhiều lần.

## 7. OVERFLOW: không thể lờ đi như một event bình thường

`OVERFLOW` biểu thị rằng một phần sự kiện có thể đã bị bỏ sót. Không được làm như sau:

```java
// SAI: nếu OVERFLOW là event duy nhất, vẫn coi như đã đồng bộ.
if (event.kind() == OVERFLOW) {
    continue;
}
```

Khi xử lý filesystem cho mục đích đồng bộ, hãy **đánh dấu trạng thái không còn đáng tin** rồi thực hiện reconciliation trên root hoặc subtree tương ứng, tùy mức độ provider biết được. Có thể giảm áp lực bằng cách xử lý event nhanh, batch công việc sang worker queue và tránh chạy tác vụ tốn thời gian trong thread lấy events.

Ví dụ ý tưởng:

```java
// Snippet: scanAndReconcile là hàm do ứng dụng triển khai.
if (event.kind() == OVERFLOW) {
    needsFullRescan = true;
} else {
    // Xử lý entry event, tránh blocking lâu.
}
// Ở điểm thích hợp:
if (needsFullRescan) {
    scanAndReconcile(root);
    needsFullRescan = false;
}
```

Đây là **snippet**, không phải API có sẵn. Việc gọi quét toàn bộ có thể tốn chi phí lớn nên cần quota, coalescing và chính sách tần suất; không nên mặc định hàng đợi sự kiện sẽ giữ mọi thay đổi vô hạn.

## 8. WatchKey: nhận, pollEvents, reset và trường hợp vô hiệu hóa

Mẫu event loop căn bản:

```java
// Snippet: watcher và directories đã khai báo.
// Map chỉ được cập nhật trong thread event loop này.
while (running) {
    WatchKey key = watcher.take();  // Chờ key có tín hiệu.
    Path dir = directories.get(key);

    for (WatchEvent<?> event : key.pollEvents()) {
        if (event.kind() == OVERFLOW) {
            scheduleRescan();
            continue;
        }

        if (dir == null) {
            continue;
        }
        Path affected = dir.resolve((Path) event.context());

        if (event.kind() == ENTRY_CREATE &&
                Files.isDirectory(affected, LinkOption.NOFOLLOW_LINKS)) {
            registerTree(affected, watcher, directories);
        }

        handleChange(affected, event.kind());
    }

    if (!key.reset()) {
        directories.remove(key);
        handleInvalidRegistration(dir);
    }
}
```

Đoạn mã trên là **pseudocode kiểu Java**; `running`, `scheduleRescan`, `registerTree`, `handleChange`, `handleInvalidRegistration` do ứng dụng xây dựng.

**Các nguyên tắc không được quên:**

- Một `WatchKey` có thể chứa **nhiều `WatchEvent`**; phải duyệt tất cả hoặc có chiến lược bỏ/scan lại rõ ràng.
- `WatchEvent.count()` cho biết số lần event cùng loại/context có thể đã bị gộp, không nhất thiết mỗi thay đổi cho một object event riêng.
- Sau xử lý, **`key.reset()`** cho phép tiếp tục nhận tín hiệu. Nếu quên reset, có thể khiến key không được đưa vào hàng đợi đúng như mong đợi.
- `reset() == false` nghĩa là key không còn hợp lệ; không xem thư mục đó vẫn được bảo vệ. Một thư mục bị xóa, filesystem mất kết nối, đăng ký bị hủy hoặc watcher đóng đều có thể liên quan.
- Không `cancel()` một key còn cần theo dõi; `key.cancel()` hủy đăng ký.
- Khi `watcher.close()`, thread đang chờ `take()`/`poll()` có thể bị đánh thức bằng `ClosedWatchServiceException`; quản lý shutdown một cách có chủ đích.

Sự kiện `ENTRY_DELETE` có thể báo một thư mục bị xóa khỏi cha. Không có ý nghĩa đi đăng ký vào path đó; hãy cập nhật index, xử lý các key con có thể bị vô hiệu hóa và chuẩn bị đăng ký lại nếu path được tạo lại.

## 9. Thư mục mới đã bị xóa hoặc thay thế trước khi register

Một tình huống race khác:

```text
root đã đăng ký
  |
  +-- CREATE root/tmp
  +-- DELETE root/tmp
  +-- CREATE root/tmp   (có thể là đối tượng khác)
  |
Watcher giờ mới xử lý ENTRY_CREATE đầu tiên.
```

Khi watcher thực hiện `Files.isDirectory(root/tmp)`, nó chỉ thấy **đối tượng đang tồn tại lúc đó** (nếu có), không bảo đảm đó là thư mục xuất hiện ở event đầu tiên. Sau khi kiểm tra, nó vẫn có thể bị xóa trước khi `register`.

Chiến lược đúng không phải “bắt buộc register thành công” bằng vòng retry vô hạn. Hãy:

- Chấp nhận `NoSuchFileException`/`NotDirectoryException` hoặc IOException phù hợp như trạng thái cây đã thay đổi, lên lịch rescan.
- Giới hạn số lần thử và giới hạn công việc mỗi chu kỳ.
- Không dùng đường dẫn ngoài phạm vi root; kiểm soát symbolic link/path traversal khi path chứa phần người dùng có thể tác động.
- Nếu thư mục gốc biến mất, quyết định watcher sẽ dừng, thử khôi phục theo deadline, hay theo dõi thư mục cha để biết khi root được tạo lại. Không thể mặc định watcher của root đã xóa sẽ tự sống lại.
- Dùng khóa/coordination với producer hoặc snapshot filesystem nếu nghiệp vụ đòi đảm bảo mạnh hơn mức “eventual reconciliation”.

## 10. Không đi theo symlink một cách vô tình

Với một cây chứa symbolic link:

```text
root/
  docs/
  external -> /outside-root/private
  loop -> root/
```

Nếu `Files.walkFileTree` được gọi với `FOLLOW_LINKS` không kiểm soát, crawler có thể đi ra ngoài khu vực được phép hoặc gặp vòng lặp đường dẫn. Do đó:

- Mặc định **không bật `FOLLOW_LINKS`** khi giám sát một cây root.
- Khi quyết định một `ENTRY_CREATE` có là directory để đăng ký hay không, dùng `Files.isDirectory(affected, LinkOption.NOFOLLOW_LINKS)`.
- Nếu cần hỗ trợ symlink theo sản phẩm, xác định chính sách: theo dõi đối tượng link, theo dõi target, hay bỏ qua. Kiểm tra quyền, giới hạn root thực, tránh vòng và suy luận thay đổi đường dẫn.
- Với cây do actor không đáng tin sửa đồng thời, `NOFOLLOW_LINKS` ở một điểm kiểm tra vẫn không loại bỏ mọi race TOCTOU khi path bị thay thế. Dùng APIs/giao thức quản lý quyền truy cập phù hợp rủi ro.

WatchService cũng không cam kết mọi thay đổi về metadata/symlink được báo giống nhau trên tất cả hệ điều hành.

## 11. Mở nhiều thư mục có thể tốn tài nguyên hệ thống

Mô hình “register mỗi directory” thường cần tài nguyên kernel/provider cho **mỗi đăng ký**, đặc biệt trên hệ điều hành có giới hạn số watcher/inotify watch. Một cây rất sâu hoặc nhiều thư mục có thể gặp hạn mức, thiếu file descriptor, hoặc lỗi khi đăng ký thêm.

Các biện pháp vận hành:

1. Đếm số thư mục đã đăng ký thực tế và số đăng ký lỗi.
2. Đặt giới hạn hợp lý về chiều sâu, số thư mục và thời gian scan nếu cây không tin cậy.
3. Có chiến lược degrade: thông báo một phần cây chưa được giám sát và thực hiện periodic scan hoặc từ chối khởi động tính năng.
4. Hạn chế event storm; xử lý nhanh và chuyển các tác vụ nặng sang queue có giới hạn.
5. Dọn key không còn hợp lệ để map không tăng vô hạn; đóng WatchService khi shutdown.
6. Khi hệ thống triển khai có network filesystem, container hoặc thiết bị gắn ngoài, kiểm thử đúng provider/mount, không suy ra hành vi native chỉ từ ví dụ trên filesystem cục bộ.

Việc đăng ký quá nhiều không được giải bằng cách “đăng ký root một lần là đủ”: theo API chuẩn, root register **không cung cấp recursive watch**.

## 12. Thời gian, thứ tự và nội dung sự kiện không phải nhật ký đáng tin

Các giới hạn cần nhớ:

| Vấn đề | Hậu quả | Phương án |
| --- | --- | --- |
| Sự kiện có thể trễ | File hiện đã đổi nhưng watcher xử lý sau. | Chấp nhận eventual consistency hoặc phối hợp writer. |
| Sự kiện có thể được gộp | Nhiều lần sửa không thành từng event riêng lẻ. | Đọc lại trạng thái thực, dùng `event.count()` để ghi nhận. |
| `OVERFLOW` | Một số sự kiện không còn trong queue. | Rescan và đồng bộ theo snapshot/index. |
| Tạo thư mục con quá nhanh | Thay đổi xảy ra trước khi register con. | Register cả subtree, sau đó reconcile. |
| Rename hoặc move | Có thể xuất hiện delete/create theo provider. | Phân tích trạng thái và protocol file thay vì dựa một event. |
| Symlink/permission thay đổi | Mất quyền, ra ngoài root, lỗi đăng ký. | Không follow mặc định; kiểm soát quyền và xử lý IOException. |
| Đường dẫn bị xóa rồi tạo lại | WatchKey cũ không tự ràng buộc tới đối tượng mới. | Đăng ký lại và reconcile khi cần. |
| Shutdown/close | Key invalid; thread chờ có thể nhận exception. | Đóng service có điều phối và giải phóng tài nguyên. |

**Không suy “đã thấy `ENTRY_MODIFY` là file đã ghi xong”.** Tác giả file có thể vẫn đang ghi, hoặc file được thay đổi thêm. Với pipeline ingest nghiêm ngặt, đặt hợp đồng publish cụ thể (ghi file tạm rồi rename khi sẵn sàng, sidecar marker, manifest, checksum...) và xử lý duplicate.

## 13. Xử lý ENTRY_CREATE của cả file và thư mục

Một handler thường có hai nhánh:

```java
// Snippet: affected đã được resolve từ key directory + context.
if (event.kind() == ENTRY_CREATE) {
    if (Files.isDirectory(affected, LinkOption.NOFOLLOW_LINKS)) {
        registerTree(affected, watcher, directories);
        // Quét và reconcile subtree để tìm các file đã có.
    } else if (Files.isRegularFile(
            affected, LinkOption.NOFOLLOW_LINKS)) {
        // Đưa file vào queue ingest có giới hạn, nếu đủ điều kiện.
    } else {
        // Entry không còn tồn tại/không phải regular file
        // hoặc trạng thái không kiểm tra được: hẹn rescan.
    }
}
```

Hãy **đăng ký subtree** trước khi dành nhiều thời gian xử lý nghiệp vụ để giảm cửa sổ bỏ lỡ events tương lai. Nếu nghiệp vụ xử lý file nặng, nên đưa nó vào worker queue có giới hạn thay vì xử lý trực tiếp trong thread lấy `WatchKey`, nhưng đồng thời phải có cơ chế backpressure và phối hợp snapshot/event để tránh mất consistency.

Đừng tin chỉ mỗi kiểm tra `Files.isDirectory` rồi khẳng định đó chính là directory được tạo bởi event: path có thể bị đổi giữa các bước. Chỉ có chính sách kết hợp reconciliation và giới hạn race mới mang lại độ tin cậy phù hợp.

## 14. Vòng đời và dừng watcher đúng cách

Một watcher có thể chạy trong thread riêng:

```java
// Snippet: thread phụ đang gọi watcher.take() trong vòng xử lý.
try {
    while (!Thread.currentThread().isInterrupted()) {
        WatchKey key = watcher.take();
        processKey(key);
    }
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
} catch (ClosedWatchServiceException e) {
    // Dịch vụ đã đóng: thoát vòng giám sát.
} finally {
    // Dọn state của thread này; chủ sở hữu sẽ đóng watcher
    // nếu chưa đóng từ đường điều khiển khác.
}
```

`processKey` là hàm ứng dụng được triển khai để pollEvents/reset và xử lý overflow. Với service chạy lâu, một luồng điều phối có thể gọi `watcher.close()` để giải phóng tài nguyên và làm `take()` đang chờ kết thúc theo cơ chế của API.

Trình tự shutdown nên xác định: ngừng chấp nhận công việc mới → báo thread watcher dừng/đóng service → giải phóng đăng ký → hoàn tất hoặc hủy các nhiệm vụ đã queue theo deadline → báo trạng thái cuối. **Không xóa toàn bộ map trong khi worker còn đang truy cập** nếu không có chính sách đồng bộ thread-safe.

Dùng `try-with-resources` với `WatchService` nếu chủ sở hữu của dịch vụ có vòng đời rõ ràng. `WatchService.close()` giải phóng tài nguyên, khác việc gọi `key.cancel()` cho từng thư mục.

## 15. Phân biệt nhu cầu “thông báo” và “phát hiện đầy đủ”

**Chỉ cần gợi ý làm mới giao diện:** WatchService có thể phù hợp. Nếu bỏ lỡ một vài event, giao diện có thể quét lại khi người dùng mở hoặc khi timer kích hoạt.

**Đồng bộ thư mục:** cần WatchService kết hợp snapshot/index và xử lý idempotent. Đăng ký lại khi có directory mới, rescan khi overflow và có periodic reconciliation để chống sai lệch kéo dài.

**Ingest file quan trọng:** cần hợp đồng file hoàn tất, queue bounded, xử lý retry/dedup, logging và recovery khi crash. WatchService không thể tự chứng minh publisher đã ghi xong.

**Audit đầy đủ mọi thay đổi trong cây:** WatchService chuẩn **không đủ làm log audit không mất sự kiện**. Cần change journal native, audit subsystem phù hợp hoặc producer viết nhật ký giao dịch bền vững, tùy hệ điều hành và yêu cầu pháp lý/kỹ thuật.

Khi muốn “theo dõi đệ quy”, hãy hỏi thêm câu kỹ thuật quan trọng: **Mục tiêu là thông báo cập nhật nhanh hay chứng minh toàn bộ trạng thái luôn nhất quán?** Chiến lược xử lý lỗi/scan khác nhau rõ rệt.

## 16. Lỗi thường gặp

| Triệu chứng | Nguyên nhân | Cách khắc phục |
| --- | --- | --- |
| Không thấy file tạo trong `root/a/b` | Chỉ `register(root)`. | Duyệt `walkFileTree` và register mỗi thư mục. |
| Thấy thư mục mới nhưng không thấy file con | Chưa register directory mới, hoặc file đã tạo trước khi register. | Register toàn subtree, reconcile sau đăng ký. |
| Cast context gây lỗi/null | Coi `OVERFLOW` như entry event. | Kiểm tra kind trước rồi mới cast `(Path) event.context()`. |
| Lặp vài event rồi watcher dừng nhận | Quên `key.reset()`. | Reset sau khi poll/process; kiểm tra boolean trả về. |
| Map giữ key của thư mục đã xóa | Không xử lý `reset() == false`. | Xóa key, rescan/re-register khi thích hợp. |
| Bỏ sót sự kiện khi thay đổi nhanh | Event gộp hoặc `OVERFLOW`, registration race. | Dùng index/snapshot + reconciliation. |
| Đường dẫn affected sai | Dùng context là path tuyệt đối hoặc ghép với root thay vì thư mục key. | Dùng `directories.get(key).resolve(context)`. |
| Watcher nhìn ra ngoài vùng root | Tự ý đi theo symlink. | `NOFOLLOW_LINKS`, policy cho symlink, kiểm tra quyền. |
| File được xử lý khi còn dang dở | Xem `ENTRY_CREATE` hoặc `ENTRY_MODIFY` là tín hiệu “ghi xong”. | Giao thức publish/marker, xác minh file hoàn chỉnh. |
| CPU cao, queue tràn | Handler làm việc nặng hoặc producer tạo quá nhiều event. | Thread xem event ngắn, bounded queue, rescan có ngân sách. |
| Đóng watcher làm thread ném exception | `watcher.close()` làm `take()` ngừng chờ. | Bắt `ClosedWatchServiceException` trong shutdown. |
| Đăng ký lỗi khi cây lớn | Hạn mức kernel/provider/permission. | Giới hạn số watch, cảnh báo, fallback rescan. |

## 17. Bài tập thực hành

**Bài 1 — Bổ sung OVERFLOW recovery.** Thay `throw new IOException` khi gặp `OVERFLOW` trong demo thành đánh dấu `needsRescan`. Viết một hàm scan root để tìm file mới và register lại thư mục chưa được theo dõi. Giải thích vì sao cần dedup giữa event và scan.

**Bài 2 — Race trước đăng ký.** Sửa demo: tạo `root/new/sub/hello.txt` **trước** khi hàm `awaitCreated(root/new)` xử lý event. Theo dõi khả năng không thấy `ENTRY_CREATE` của file. Dùng scan ngay sau register để vẫn phát hiện file đã tồn tại.

**Bài 3 — Thư mục lồng sâu.** Tạo `root/a/b/c/d` trong một lệnh `Files.createDirectories` rồi quan sát số directory được register khi nhận `ENTRY_CREATE` của `a`. Sau đó tạo `d/file.txt` và xác minh được phát hiện.

**Bài 4 — Xóa rồi tạo lại.** Tạo một thư mục có watcher, xóa nó rồi tạo lại cùng tên. Quan sát `WatchKey.reset()` và các sự kiện ở thư mục cha. Giải thích vì sao phải register đối tượng mới và đối chiếu snapshot.

**Bài 5 — Symlink và bảo mật.** Trên hệ thống hỗ trợ symbolic links và có quyền tạo link, tạo một symlink trong root trỏ ra ngoài. Kiểm chứng watcher mẫu không đi theo link. Viết chính sách rõ ràng nếu sản phẩm yêu cầu hỗ trợ link mà không mở rộng phạm vi dữ liệu nhạy cảm.

**Bài 6 — File được ghi dần.** Một producer tạo file rồi ghi năm chunk cách nhau. Quan sát `ENTRY_CREATE/ENTRY_MODIFY` có thể khác nhau theo OS. Đưa ra quy tắc “file đã sẵn sàng” bằng rename từ file tạm sang tên chính thức hoặc marker riêng, thay vì suy luận từ event count.

**Bài 7 — Nhiều thay đổi/OVERFLOW.** Tạo hàng nghìn file ở thư mục thử nghiệm, đo event queue và thời gian xử lý. Không cố ý gây áp lực lên hệ thống sản xuất. Khi có `OVERFLOW` hoặc event không khớp snapshot, thử rescan và thống kê độ trễ.

**Bài 8 — Shutdown.** Chạy watcher trong thread phụ, cho thread chính đóng `WatchService` rồi `join()` có deadline. Xác nhận vòng lấy event thoát không kẹt và không để thread sống rò rỉ.

### Câu hỏi tự kiểm tra

1. Đăng ký `root` có tự theo dõi `root/sub/file.txt` không?
2. Vì sao xử lý `ENTRY_CREATE` của directory cần đăng ký **cả subtree mới**?
3. Tại sao một file tạo ngay sau directory có thể không có event dù `registerTree(newDir)` chạy thành công?
4. `WatchEvent.context()` có phải lúc nào cũng là đường dẫn tuyệt đối không?
5. Vì sao phải xử lý `OVERFLOW` bằng rescan?
6. Quên `key.reset()` gây vấn đề gì và `reset() == false` có ý nghĩa gì?
7. Vì sao nên dùng `NOFOLLOW_LINKS` khi giám sát cây có phạm vi tin cậy?
8. File đã có `ENTRY_CREATE` có chắc là đã ghi đủ nội dung để ingest chưa?
9. Khi cần audit không mất sự kiện, WatchService có phải nguồn lịch sử bền vững không?

## 18. Tài liệu chính thức

- [Java SE 21 — WatchService](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/WatchService.html): vòng đời dịch vụ, key, queue, close và hành vi provider.
- [Java SE 21 — WatchKey](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/WatchKey.html): `pollEvents()`, `reset()`, `cancel()` và hiệu lực đăng ký.
- [Java SE 21 — WatchEvent](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/WatchEvent.html): `kind()`, `context()` và `count()`.
- [Java SE 21 — StandardWatchEventKinds](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/StandardWatchEventKinds.html): các kind `ENTRY_CREATE`, `ENTRY_DELETE`, `ENTRY_MODIFY`, `OVERFLOW`.
- [Java SE 21 — Files.walkFileTree](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html#walkFileTree(java.nio.file.Path,java.nio.file.FileVisitor)): duyệt và xử lý cây directory.
- [Java SE 21 — Path.register](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Path.html#register(java.nio.file.WatchService,java.nio.file.WatchEvent.Kind...)): đăng ký watch cho từng directory.

**Ghi nhớ:** WatchService không theo dõi đệ quy một cách tự động. Dùng `Files.walkFileTree` đăng ký mọi thư mục hiện có; khi có `ENTRY_CREATE` cho thư mục mới, đăng ký **toàn bộ subtree**, xử lý `WatchKey.reset()` và `OVERFLOW`, đồng thời **reconcile filesystem state** để khắc phục race và event bị mất. Khi cần dữ liệu đúng tuyệt đối, phải bổ sung giao thức và nguồn sự thật mạnh hơn một luồng thông báo filesystem.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="watch-service-008">Sự kiện lặp, gộp và thời điểm nội dung tệp chưa hoàn tất</a>

<details>
<summary>Xem chi tiết</summary>

# Sự kiện lặp, gộp và thời điểm nội dung tệp chưa hoàn tất trong Java WatchService

## 1. Vấn đề cần hiểu

`WatchService` của Java NIO.2 báo cho ứng dụng biết **trạng thái của thư mục có thể đã thay đổi**. API này hữu ích cho công cụ tự tải lại cấu hình, đồng bộ tệp, phát hiện tài liệu đầu vào, tự động xử lý file hoặc giám sát thư mục. Tuy nhiên, các sự kiện **không phải nhật ký giao dịch chính xác**, không phải một hàng đợi bền vững và **không đảm bảo file đã ghi hoàn tất** khi consumer nhận được thông báo.

Ba hiện tượng phải thiết kế trước:

1. **Sự kiện lặp:** một thao tác lưu file có thể sinh nhiều `ENTRY_MODIFY`; cùng một path có thể xuất hiện trong nhiều đợt.
2. **Sự kiện gộp và mất sự kiện:** provider có thể gộp các thay đổi cùng loại/ngữ cảnh trong một `WatchEvent` có `count()` lớn hơn một; khi tích lũy không kịp, có thể phát `OVERFLOW` vì một số event đã bị loại.
3. **File chưa sẵn sàng:** `ENTRY_CREATE` hoặc `ENTRY_MODIFY` có thể tới trước khi writer đóng file hoặc hoàn tất record cuối cùng. Đọc ngay có thể lấy được chỉ một phần JSON, CSV, ZIP hoặc nội dung nhị phân.

Nguyên tắc thiết kế: **coi event là tín hiệu cần đối chiếu lại trạng thái file, không coi event là lệnh nhập dữ liệu đúng một lần hoặc dấu hiệu producer đã kết thúc.**

## 2. Phạm vi bảo đảm của WatchService

Ứng dụng tạo một `WatchService` qua `FileSystems.getDefault().newWatchService()`, rồi đăng ký **thư mục** bằng `Path.register(...)`. Các event chuẩn:

| Kind | Điều thể hiện | Điều không được bảo đảm |
| --- | --- | --- |
| `ENTRY_CREATE` | Một entry xuất hiện trong thư mục đã đăng ký. | File chứa đầy đủ bytes, hoặc producer không còn ghi. |
| `ENTRY_MODIFY` | Provider quan sát thấy thay đổi của một entry. | Chỉ có một lần sửa, thay đổi đã kết thúc hoặc dữ liệu hợp lệ. |
| `ENTRY_DELETE` | Entry không còn hiện diện ở vị trí được quan sát. | Đây chắc chắn là xóa nghiệp vụ thay vì move/rename. |
| `OVERFLOW` | Một số thông báo có thể đã không được giữ trong queue. | Các event còn lại đủ để phục dựng mọi thay đổi. |

Với các event entry tiêu chuẩn, `event.context()` là đường dẫn tương đối tới mục bên trong thư mục đã đăng ký. Đường dẫn đầy đủ thường lấy bằng:

```java
// Snippet minh họa, key là WatchKey hợp lệ.
Path watched = (Path) key.watchable();
Path relative = (Path) event.context();
Path full = watched.resolve(relative);
```

Không được cast `event.context()` sang `Path` trước khi kiểm tra `OVERFLOW`, vì overflow không đại diện một file riêng. `Path.register` trong Java SE 17/21 chủ yếu theo dõi các entry của **thư mục**, không tự động theo dõi cây thư mục con. Muốn giám sát cây phải đăng ký từng thư mục, kể cả các thư mục con mới tạo.

Với filesystem mạng, provider tùy chỉnh hoặc cơ chế theo dõi bằng polling, độ trễ và khả năng nhận sự kiện có thể khác. Javadoc Java nói rõ thời điểm phát hiện, thứ tự event và khả năng phát hiện thay đổi trên remote filesystem **phụ thuộc implementation**; file tồn tại rất ngắn đôi khi không được phát hiện. Không đặt giả định “sửa một lần chắc chắn nhận một event ngay lập tức”.

## 3. Vì sao cùng một file có nhiều ENTRY_MODIFY?

Một writer có thể mở file rồi ghi lần lượt:

```text
open("data.csv")
write("id,amount\n")
flush()
write("1,100\n")
write("2,200\n")
close()
```

Từ phía watcher, có thể thấy một chuỗi `ENTRY_CREATE` và nhiều `ENTRY_MODIFY`, hoặc chỉ một số sự kiện, tùy OS và cách JDK nhận native notifications.

Nguyên nhân bao gồm:

- Producer ghi theo từng khối nhỏ hoặc nhiều lần flush.
- File được truncate rồi viết lại; metadata và nội dung thay đổi ở các thời điểm khác nhau.
- Một editor lưu nội dung qua tên tạm rồi rename; watcher có thể quan sát tạo/xóa/modify ở đường dẫn khác nhau.
- Provider tổng hợp event theo đợt; cùng path xuất hiện lại sau khi WatchKey được reset.
- Hoạt động đồng bộ, backup, antivirus hoặc process khác tạo thêm metadata/content changes.

**Sai lầm:** mỗi lần nhận `ENTRY_MODIFY` lại gọi `importFile(path)` và chèn toàn bộ record vào database. Cùng file có thể bị import nhiều lần, sinh duplicated invoices hoặc gửi notification lặp.

**Thiết kế đúng:** event chỉ đánh dấu path là “dirty”; worker xử lý trạng thái hiện tại theo một **idempotency key** (artifact ID + phiên bản/checksum/transaction ID), ghi nhận kết quả chỉ khi commit thành công. Nếu cùng một phiên bản xuất hiện qua nhiều event, xử lý lại phải không gây side effect lặp.

## 4. Ý nghĩa của WatchEvent.count và coalescing

`WatchEvent<T>` có ba thành phần chính:

- `kind()`: loại sự kiện.
- `context()`: thông tin gắn với sự kiện, thường là tên path tương đối.
- `count()`: số lần event được quan sát và biểu diễn chung trong đối tượng event.

Ví dụ:

```text
kind=ENTRY_MODIFY
context=report.csv
count=4
```

Dòng ví dụ không nói rằng producer đã gọi `write` đúng 4 lần, cũng không có nghĩa file có đúng 4 phiên bản hợp lệ. Đây là **count của notification mà provider ghi nhận/gộp**, không phải đếm giao dịch kinh doanh.

Cùng một `kind` và `context` có thể được gộp để giảm số đối tượng event. Khi hai thao tác không giống nhau hoặc đến ở thời điểm khác, không có quy tắc portable buộc mọi hệ thống phải gộp thành cùng một cách.

Không dùng vòng xử lý file `count()` lần:

```java
// Anti-pattern: số notification không phải số phiên bản dữ liệu.
for (int i = 0; i < event.count(); i++) {
    processFile(path);
}
```

Đoạn trên chỉ là **minh họa lỗi**, không phải source độc lập. `event.count()` hữu ích cho metrics hoặc cảnh báo burst changes, nhưng **nội dung file lúc đọc mới là trạng thái cần kiểm chứng**.

### Coalescing khác OVERFLOW

| Hiện tượng | Ý nghĩa | Hướng ứng xử |
| --- | --- | --- |
| Duplicate events | Cùng mục được thông báo lặp qua một hay nhiều đợt. | Dedup/debounce và idempotent processing. |
| Coalesced event | Một WatchEvent biểu diễn nhiều lần quan sát cùng kind/context. | Không import theo count; xác định file version cần xử lý. |
| `OVERFLOW` | Hệ thống có thể đã bỏ thông tin sự kiện do giới hạn queue. | **Rescan/reconcile thư mục**, không dựa vào danh sách event hiện có. |
| Không nhận event sau restart | WatchService không phải durable message log. | Startup scan; processed-version state bền vững. |

Javadoc `WatchService` quy định khi implementation biết đã bỏ event, nó đưa `OVERFLOW`. Nhưng việc **không thấy** overflow không phải lời chứng minh mọi thay đổi ở mọi OS/remote filesystem đã được phát hiện.

## 5. Vòng đời WatchKey và xử lý batch đúng cách

`WatchService` đưa **WatchKey** đại diện cho một đăng ký đã được signal vào queue. Consumer dùng:

- `watcher.take()`: đợi tới khi có key.
- `watcher.poll()`: lấy ngay hoặc trả `null`.
- `watcher.poll(timeout, unit)`: đợi có hạn.
- `key.pollEvents()`: lấy và loại các event đang tích lũy của key.
- `key.reset()`: đưa key trở lại trạng thái ready sau khi xử lý batch; trả `false` nếu key không còn hợp lệ.

Luồng xử lý:

```text
key READY
  |
FileSystem quan sát thay đổi
  v
key SIGNALED -> được đưa vào queue
  |
watcher.take / poll
  |
key.pollEvents -> duyệt event
  |                ENTRY_*  -> đánh dấu path dirty
  |                OVERFLOW -> cần full rescan
  v
key.reset()
  +-> true : tiếp tục giám sát
  +-> false: mất đăng ký, cần phục hồi
```

Nếu quên `reset()`, watcher có thể không tiếp tục nhận những event mới như mong đợi. Nếu trì hoãn `reset()` hàng chục giây để xử lý file lớn trong cùng thread, backlog có thể tăng và làm mất sự kiện. Vì thế trong production, watcher nên **lấy sự kiện nhanh, cập nhật trạng thái nhẹ, reset đúng lúc**, còn parse/import được giao cho worker có giới hạn queue.

Một snippet hướng dẫn xử lý:

```java
// Bên trong vòng lặp watcher, rescanNeeded đã được khai báo.
WatchKey key = watcher.take();
Path dir = (Path) key.watchable();

for (WatchEvent<?> ev : key.pollEvents()) {
    if (ev.kind() == StandardWatchEventKinds.OVERFLOW) {
        rescanNeeded = true;
        continue;
    }
    Path child = dir.resolve((Path) ev.context());
    markDirty(child, ev.kind(), ev.count());
}
if (!key.reset()) {
    handleInvalidRegistration(dir);
}
```

Đây là **snippet kiến trúc**, không phải chương trình độc lập: `markDirty`, `handleInvalidRegistration` và `rescanNeeded` do ứng dụng định nghĩa. `OVERFLOW` không mang một path file hợp lệ để đưa thẳng vào `markDirty`. Các lỗi như `ClosedWatchServiceException`, `InterruptedException` cần được xử lý theo vòng đời ứng dụng.

## 6. Một event MODIFY không nói writer đã đóng tệp

Javadoc của `WatchService` nói rõ: khi có event báo file trong thư mục được sửa, **không bảo đảm chương trình thực hiện sửa đổi đã hoàn tất**.

Minh họa diễn biến:

```text
Producer                     Watcher / consumer
--------                     ------------------
CREATE input.json
write("{ \"items\": [")
                             ENTRY_CREATE tới
                             đọc file, JSON chưa đóng
write("{ \"id\": 1 }")
                             ENTRY_MODIFY tới
                             retry parse, vẫn có thể sai
write("] }")
close writer
                             không có event chuẩn "WRITE_COMPLETED"
```

Đây chỉ là **một lịch xen kẽ có thể xảy ra**, không phải số lượng hoặc thứ tự event portable. Ngay cả khi `Files.exists(path)` là `true` và `Files.size(path) > 0`, producer vẫn có thể tiếp tục ghi.

Các dạng file dễ bị đọc dở:

- JSON/XML chưa có dấu đóng, parser ném lỗi.
- CSV chưa có dòng cuối hoặc ký tự newline cuối.
- ZIP/JAR có central directory ở cuối, có thể chưa đọc được.
- Record nhị phân chứa length/header đã ghi nhưng payload/checksum chưa hoàn tất.
- Ảnh/video được tạo header trước khi ghi hết frame.

Đừng coi mọi parse exception đều do writer chưa xong: file cũng có thể hỏng thật. Ứng dụng phải có **giới hạn retry, timeout, kiểm tra cấu trúc và quarantine**.

## 7. Chiến lược mạnh nhất: file tạm rồi công bố atomically

Khi có thể chỉnh producer, hãy dùng một **giao thức công bố** thay vì đoán từ `ENTRY_MODIFY`:

1. Producer tạo tệp dưới tên tạm như `report.csv.tmp`; consumer bỏ qua `.tmp`.
2. Producer ghi đủ nội dung, kiểm tra nội bộ và **đóng file**.
3. Producer gọi `Files.move(temp, ready, StandardCopyOption.ATOMIC_MOVE)` trong cùng filesystem, nếu được hỗ trợ.
4. Consumer **chỉ xử lý** `report.csv.ready` sau khi nó xuất hiện; theo dõi event là trigger để scan candidate.
5. Consumer xác nhận version, độ dài/checksum/schema, xử lý idempotent và lưu kết quả.

`ATOMIC_MOVE` làm thao tác đổi tên/move ở cấp filesystem có tính nguyên tử khi provider hỗ trợ; nó không bảo đảm mọi filesystem hỗ trợ hay thay thế target đã tồn tại giống nhau. Nếu không hỗ trợ, Java ném `AtomicMoveNotSupportedException`. Khi atomic publication là yêu cầu bắt buộc, **không lặng lẽ fallback sang copy/move không nguyên tử**.

**Atomic rename không đồng nghĩa durability khi mất điện.** Nếu yêu cầu dữ liệu và directory entry sống sót sau crash, cần chính sách `FileChannel.force`/synchronization phù hợp filesystem/OS. Và `WatchService` vẫn có duplicate/overflow: consumer phải reconcile chứ không dựa hoàn toàn event.

### Ví dụ Java 17+: tạo bản tạm và công bố

Tạo file `AtomicPublishDemo.java`:

```java
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class AtomicPublishDemo {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("publish-demo-");
        Path temp = dir.resolve("message.tmp");
        Path ready = dir.resolve("message.ready");
        try {
            Files.writeString(temp, "PART", StandardCharsets.US_ASCII,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            System.out.println("partial-temp=" + Files.readString(temp));
            Files.writeString(temp, "DONE", StandardCharsets.US_ASCII,
                    StandardOpenOption.APPEND);
            Files.move(temp, ready, StandardCopyOption.ATOMIC_MOVE);
            System.out.println("published=" + Files.readString(ready));
            System.out.println("temp-exists=" + Files.exists(temp));
        } finally {
            Files.deleteIfExists(temp);
            Files.deleteIfExists(ready);
            Files.deleteIfExists(dir);
        }
    }
}
```

```bash
javac --release 17 AtomicPublishDemo.java
java AtomicPublishDemo
```

Kết quả đã kiểm chứng trên filesystem hỗ trợ atomic move:

```text
partial-temp=PART
published=PARTDONE
temp-exists=false
```

Đầu tiên producer mới có `PART`. Consumer không được xem nội dung tạm là một artifact đã sẵn sàng. Sau khi app ghi thêm `DONE` và đóng stream, tên `message.ready` mới được công bố. **Chính quy tắc công bố tên** mang ý nghĩa hoàn tất, không phải số notification nhận được. Khi dùng mô hình này với watcher, chỉ enqueue những file `.ready`/manifest hợp lệ, không xử lý mọi `ENTRY_CREATE`.

## 8. Marker file hoặc manifest có checksum

Một biến thể là producer xuất hiện tệp dữ liệu trước, nhưng chỉ tạo file marker `.done` hoặc `.ready` **sau khi hoàn tất**:

```text
batch-0007.data
batch-0007.ready
```

Manifest có thể chứa tên, version/sequence, kích thước, checksum, tổng số record và schema version. Consumer theo dõi marker, sau đó xác minh tệp dữ liệu.

**Lưu ý:** nếu producer tạo marker trước khi hoàn tất dữ liệu hoặc ghi marker không nguyên tử, chính marker cũng có thể bị đọc dở. Vì vậy marker cần một contract rõ: viết tạm rồi công bố, metadata/manifest có định dạng kiểm chứng được; consumer cần xử lý file bị xóa, thiếu, thay thế hoặc có checksum sai.

Dùng producer sequence hoặc content hash để idempotency sẽ mạnh hơn chỉ dùng path. Nếu file mới thay thế cùng tên `batch.data`, consumer không thể coi đó là cùng version chỉ vì path giống nhau.

## 9. Không kiểm soát producer: debounce và kiểm tra ổn định chỉ là heuristic

Khi không thể yêu cầu file tạm + atomic move hoặc marker, consumer có thể dùng thời gian yên lặng và so sánh metadata:

1. Khi nhận create/modify, đặt path vào danh sách dirty và ghi lại thời điểm sự kiện mới nhất.
2. Sau khoảng quiet period được cấu hình theo nguồn (ví dụ một vài trăm mili-giây hoặc vài giây), kiểm tra `size` và `lastModifiedTime`.
3. Chờ thêm rồi kiểm tra lại; nếu không đổi, thử mở và parse file theo định dạng.
4. Đọc/parse có giới hạn, xác minh file chưa đổi sau khi xử lý; nếu thay đổi, đánh dấu dirty để thử lại.
5. Sau deadline hoặc số lần retry tối đa, báo lỗi/quarantine thay vì lặp vô hạn.

**Không được tuyên bố file hoàn tất chỉ nhờ size/mtime ổn định.** Writer có thể tạm dừng lâu hơn quiet period, ghi đè cùng size, OS có timestamp độ phân giải thấp hoặc file bị thay thế sau khi đọc metadata. Race giữa `Files.exists` và `Files.readAllBytes` cũng tồn tại. Nếu phải đảm bảo mạnh, cần giao thức hợp tác từ producer hoặc định dạng có commit marker/transaction log.

Đừng dùng `Thread.sleep(...)` ngay trong watch loop: nó chặn xử lý event của thư mục khác, làm backlog tăng và tăng nguy cơ overflow. Dùng `ScheduledExecutorService` hoặc một worker scheduler riêng, giới hạn queue và backpressure.

### Mô hình debounce per-path bằng generation

```java
// Snippet minh họa, không độc lập:
// versions: ConcurrentHashMap<Path, Long>
// scheduler: ScheduledExecutorService
long generation = versions.merge(path, 1L, Long::sum);
scheduler.schedule(() -> {
    Long current = versions.get(path);
    if (current == null || current.longValue() != generation) return;
    enqueueForReadinessCheck(path);
}, 700, TimeUnit.MILLISECONDS);
```

Nếu event mới tới cùng path, generation tăng; callback lịch cũ không enqueue. Tuy vậy, snippet này không xử lý toàn bộ tình huống: task cũ vẫn có thể tồn tại trong scheduler, file tiếp tục đổi mà event không tới, và logic worker phải có trạng thái “dirty during processing”. Production cần giới hạn số lịch hẹn, đồng bộ state và xử lý xóa/tạo lại path. Chỉ dùng ví dụ để hiểu **debounce không đồng nghĩa readiness guarantee**.

## 10. Ví dụ Java 17+: WatchService và vòng đời WatchKey

Demo tạo thư mục tạm, đăng ký watcher **trước khi tạo file**, sau đó lấy `ENTRY_CREATE` trong vòng tối đa 5 giây. Vì số modify events phụ thuộc OS, demo chỉ kiểm tra create, không in số lượng modify.

Tạo file `WatchEventDemo.java`:

```java
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public class WatchEventDemo {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("watch-demo-");
        Path file = dir.resolve("report.txt");
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            Files.writeString(file, "READY", StandardCharsets.US_ASCII,
                    StandardOpenOption.CREATE_NEW);
            boolean created = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!created && System.nanoTime() < deadline) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) break;
                WatchKey key = watcher.poll(remaining, TimeUnit.NANOSECONDS);
                if (key == null) break;
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        throw new IllegalStateException("Rescan needed after OVERFLOW");
                    }
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE
                            && Path.of("report.txt").equals(event.context())) {
                        created = true;
                    }
                }
                if (!key.reset()) throw new IllegalStateException("Invalid watch key");
            }
            if (!created) throw new IllegalStateException("Create event not observed");
            System.out.println("created-observed=" + created);
            System.out.println("file-content=" + Files.readString(file));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}
```

```bash
javac --release 17 WatchEventDemo.java
java WatchEventDemo
```

Kết quả đã kiểm chứng trên local filesystem có native notification:

```text
created-observed=true
file-content=READY
```

**Giải thích:** watcher được đăng ký trước thời điểm file xuất hiện, `poll(timeout)` có giới hạn chờ, `pollEvents()` lấy các event trong batch, `event.context()` được so theo relative path và `reset()` tái kích hoạt key. Demo báo lỗi khi `OVERFLOW` vì đây không phải consumer production; consumer thật phải rescan.

Điều khiến file có nội dung `READY` lúc đọc ở chương trình này là **`Files.writeString` đã trả về và đóng stream trước khi chúng ta lấy event**, không phải vì `ENTRY_CREATE` tự bảo đảm producer đã đóng file. Trên polling filesystem hoặc remote mount, event có thể không tới trước deadline. Không coi stdout trên là cam kết universal.

## 11. Thiết kế xử lý lặp, overflow và tệp thay đổi trong lúc xử lý

Một consumer thực tế nên tách watcher và processing workers:

```text
WatchService / WatchKey
   |
   +-- ENTRY_CREATE / ENTRY_MODIFY ---> markDirty(path)
   |                                      |
   |                                      v
   |                               debounce / readiness
   |                                      |
   |                                      v
   |                              validate + parse
   |                                      |
   |                                      v
   |                              idempotent commit
   |
   +-- ENTRY_DELETE -------------------> reconcile missing entry
   |
   +-- OVERFLOW -----------------------> full directory rescan

Startup scan / periodic reconciliation -> same processing pipeline
```

### Không đánh mất event mới khi worker đang xử lý

Một Set chứa các path pending tránh queue trùng, nhưng có race:

1. Worker đang đọc file A.
2. Producer sửa file A lần nữa; watcher thấy event nhưng Set đã có A nên không enqueue.
3. Worker xóa A khỏi Set khi xử lý xong.
4. Không còn task nào xử lý phiên bản mới.

Hãy dùng state rõ:

```text
IDLE -> DIRTY -> PROCESSING
                   |
                   +-- có event trong lúc xử lý -> DIRTY_AGAIN
                   |
                   +-- xong và dirty_again -> PROCESSING lần nữa
                   |
                   +-- xong và không dirty -> IDLE
```

State phải được cập nhật thread-safe (`ConcurrentHashMap.compute`, mutex per-key hoặc event loop owner duy nhất). Worker có thể so sánh producer version/hash trước và sau import; nếu nội dung đổi, chạy lại. **Không có state machine nào tự biến WatchService thành reliable event log**, nên khi restart/overflow vẫn phải reconcile.

### Idempotency với cơ sở dữ liệu

Nếu input artifact có `artifactId` và `version`, hãy dùng chúng làm khóa duy nhất ở nơi lưu trạng thái xử lý. Ví dụ bảng `processed_files` có unique key trên cặp identity/version; transaction vừa ghi dữ liệu nghiệp vụ vừa ghi processed marker nếu được phép trong cùng storage. Nhờ đó, xử lý lại duplicate notification không phát sinh duplicate invoice hoặc gửi cùng job hai lần. Nếu việc thực hiện side effect bên ngoài database (gửi email, thanh toán), cần thêm outbox/idempotency protocol phù hợp, không dựa riêng vào Set trong RAM.

## 12. Rescan và xử lý tình huống mất event

Khi `OVERFLOW`, có thể đã bỏ event cho những path chưa từng được consumer thấy. Không thể lấy `event.count()` hoặc event cuối để khôi phục danh sách path đã mất. Phải **scan trạng thái thư mục** rồi đối chiếu với durable processed state.

Các thời điểm nên rescan:

- Sau khi đăng ký watcher lúc startup, để phát hiện files có sẵn và hạn chế khe race giữa scan/registration.
- Khi có `OVERFLOW`.
- Khi `WatchKey.reset() == false`, thư mục được tạo lại hoặc mount bị thay đổi.
- Sau restart/redeploy của ứng dụng.
- Định kỳ nếu độ tin cậy quan trọng hoặc filesystem có giới hạn event.

Một lộ trình hợp lý: **đăng ký watcher → scan snapshot hiện tại → bắt đầu xử lý queue event → rescan khi cần**. Dù vậy không thể có snapshot transaction tuyệt đối với directory đang đổi chỉ nhờ `Files.list` và WatchService; cần idempotency, versioning và kiểm tra lại. Nếu phải lưu mọi thay đổi trung gian, dùng producer log, message queue hoặc versioned manifest làm nguồn sự thật.

Với thư mục rất lớn, rescan có thể tốn I/O: chia batch, dùng bounded worker queues, ghi trạng thái processing, tránh đọc toàn bộ tệp vào heap và dùng streaming parse.

## 13. Hệ điều hành và loại filesystem ảnh hưởng thế nào?

| Môi trường | Khác biệt cần kiểm tra |
| --- | --- |
| Linux / Windows / macOS local | Provider có thể dùng native notifications nhưng số modify events và thời điểm nhận thay đổi theo OS, filesystem, editor và cách ghi file. |
| Remote filesystem NFS/SMB | Thay đổi từ host khác **không được Java bảo đảm luôn phát hiện**; cache, polling và network latency có thể làm event trễ/mất. |
| Container / bind mount / overlay | Native event propagation và permission phụ thuộc cách mount/host/provider; kiểm thử đúng cấu hình triển khai. |
| Polling-based provider | File vừa tạo vừa xóa rất nhanh có thể không được phát hiện; các đợt thay đổi có thể gộp. |
| Filesystem ảo/custom provider | Có thể không hỗ trợ WatchService hoặc có semantics khác; xử lý `UnsupportedOperationException`/`IOException` và fallback phù hợp. |

Đừng cố suy ra “file đã sẵn sàng” từ việc nhận event nhanh hay chậm. Phải xác định **producer protocol**, file type, yêu cầu consistency và recovery.

## 14. Lỗi phổ biến và hướng xử lý

| Sai lầm | Hậu quả | Giải pháp |
| --- | --- | --- |
| Mỗi `ENTRY_MODIFY` đều import dữ liệu. | Duplicate records, nhiều side effects. | Dedup và idempotency theo phiên bản. |
| Vòng `for` chạy `event.count()` lần. | Count không phải số transaction nghiệp vụ. | Xử lý state/manifest hiện tại. |
| Đọc ngay sau `ENTRY_CREATE`. | File có thể đang ghi dở. | Temp + atomic publish hoặc ready marker. |
| Coi file size không đổi 1 giây là “đã hoàn tất”. | Writer có thể tạm dừng hoặc overwrite cùng size. | Chỉ coi heuristic; validate/retry có deadline. |
| Bỏ qua `OVERFLOW`. | Mất sự kiện mà không tự chữa. | Full rescan/reconciliation. |
| Quên `WatchKey.reset()`. | Key không được kích hoạt đúng cho event tiếp theo. | Reset mỗi batch và kiểm tra boolean. |
| Dùng watcher thread để parse file hàng GB. | Backlog, tăng nguy cơ overflow. | Offload bounded worker, stream file. |
| Cast `OVERFLOW.context()` thành Path. | Sai ngữ cảnh, có thể lỗi. | Phân nhánh kind trước khi đọc context. |
| Giám sát thư mục cha và tưởng là recursive. | Bỏ qua nhiều file trong subdirectory. | Đăng ký từng thư mục. |
| Chỉ dựa vào queue event để phục hồi sau restart. | WatchService không replay durable. | Startup scan + durable processed state. |
| Có atomic rename rồi khẳng định dữ liệu bền sau mất điện. | Atomic rename khác durability. | File sync, directory sync/recovery theo OS/FS. |
| Retry mọi parse error mãi mãi. | Quá tải, stuck pipeline. | Deadline, backoff, quarantine và metrics. |
| File mới dùng lại path cũ, dedup theo tên. | Mất phiên bản dữ liệu. | Version/sequence/hash và idempotency. |

## 15. Checklist ứng dụng thật

1. Đăng ký đúng thư mục, đúng event kinds; chuyển `context()` từ relative sang full path phù hợp.
2. Mỗi WatchKey được `pollEvents` và `reset`; xử lý invalid key.
3. Phân nhánh `OVERFLOW` trước các event khác, chạy full rescan.
4. Watch thread chỉ cập nhật dirty state nhanh; không parse file dài ngay trong vòng lấy key.
5. Debounce và worker queue có giới hạn, có backpressure.
6. Ưu tiên producer temp file + atomic move hoặc marker/manifest kiểm chứng được.
7. Nếu chỉ dùng size/mtime quiet period, luôn ghi nhận đây là **heuristic** và có timeout, validation.
8. Dữ liệu có format version/checksum/length; parse dữ liệu dở đúng cách.
9. Import idempotent dựa trên artifact/version, không dựa vào event count.
10. Có startup/periodic reconciliation và xử lý restart.
11. Xử lý `NoSuchFileException`/`IOException`, file bị thay thế, mất quyền, writer lỗi.
12. Kiểm thử burst events, partial writes, overwrite, rename, network mounts và file cực ngắn trên đúng OS.

### Tự kiểm tra

**1. `ENTRY_MODIFY` có `count() == 3` đồng nghĩa producer viết ba record hoàn chỉnh?**  
Không. Count là số event tương ứng được WatchService biểu diễn gộp, không phải record count.

**2. Vì sao nhận `ENTRY_CREATE` chưa đủ để parse JSON?**  
Producer có thể mới tạo file rồi ghi tiếp, nên JSON đang thiếu dữ liệu.

**3. Gặp `OVERFLOW` có thể bỏ qua nếu event cuối cùng đã được xử lý?**  
Không. Các file khác có thể đã thay đổi mà không còn event; phải rescan.

**4. Debounce cố định 500 ms có chắc loại bỏ file chưa hoàn tất?**  
Không. Producer có thể dừng lâu hơn hoặc ghi lại cùng kích thước; đây chỉ là heuristic.

**5. Cách hợp tác tốt khi kiểm soát producer?**  
Ghi file tạm, đóng/validate, công bố bằng atomic move cùng filesystem hoặc manifest/ready marker có contract rõ.

**6. Vì sao vẫn cần idempotency sau atomic publication?**  
Watcher còn có duplicate/coalescing/overflow/restart; consumer có thể thử lại cùng artifact/version.

**7. Một WatchService có cung cấp exactly-once delivery và durable replay không?**  
Không. Muốn bảo đảm nghiệp vụ phải xây dựng transaction/state bền vững hoặc dùng event log/message broker.

## Nguồn chính thức

- [Java SE 21 — WatchService](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/WatchService.html): tính phụ thuộc platform, `OVERFLOW` và cảnh báo file chưa được writer hoàn tất.
- [Java SE 21 — WatchEvent](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/WatchEvent.html): `kind()`, `count()`, `context()` và coalescing.
- [Java SE 21 — WatchKey](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/WatchKey.html): queue, `pollEvents()`, `reset()`.
- [Java SE 21 — Path](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Path.html): `register` và đường dẫn tương đối trong event context.
- [Java SE 21 — Files](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html): `move` với `ATOMIC_MOVE` và giới hạn tính nguyên tử.
- [Oracle Java Tutorials — Watching a Directory for Changes](https://docs.oracle.com/javase/tutorial/essential/io/notification.html): mô hình theo dõi thư mục và vòng xử lý WatchKey (hướng dẫn gốc dùng JDK 8).

**Kết luận:** `WatchService` là **tín hiệu quan sát**, không phải tín hiệu transaction commit. Hãy xử lý lặp/gộp bằng dedup + idempotency, khôi phục `OVERFLOW` bằng rescan, và xác nhận file hoàn tất bằng giao thức công bố phù hợp. Khi không có giao thức từ producer, debounce/metadata chỉ là phương án tốt nhất có giới hạn chứ không phải bảo đảm tuyệt đối.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="watch-service-009">Giới hạn phụ thuộc nền tảng và network file system</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn phụ thuộc nền tảng và network file system khi theo dõi thay đổi bằng WatchService

## 1. Bối cảnh và mục tiêu

Java NIO.2 cung cấp `WatchService` (`java.nio.file.WatchService`) để ứng dụng theo dõi thay đổi ở những thư mục đã đăng ký. Các loại sự kiện tiêu chuẩn thường dùng là:

- `StandardWatchEventKinds.ENTRY_CREATE`: có mục mới xuất hiện trong thư mục.
- `StandardWatchEventKinds.ENTRY_DELETE`: một mục bị xóa hoặc không còn trong thư mục.
- `StandardWatchEventKinds.ENTRY_MODIFY`: một mục được ghi nhận đã thay đổi.
- `StandardWatchEventKinds.OVERFLOW`: bộ phát hiện đã bỏ lỡ một hoặc nhiều sự kiện do vượt khả năng ghi nhận/tích lũy.

Điểm quan trọng: **`WatchService` không phải một nhật ký thay đổi đầy đủ, bảo đảm chính xác và độc lập với hệ điều hành.** Khả năng phát hiện, độ trễ, thứ tự và mức độ gộp sự kiện phụ thuộc vào triển khai `FileSystemProvider`, cơ chế của hệ điều hành, file system và cả vị trí lưu dữ liệu. Với NFS, SMB hoặc các thư mục mạng được mount, ứng dụng đặc biệt cần kiểm tra khả năng phát hiện các sửa đổi thực hiện từ **máy khác**.

Bài này giúp bạn:

1. Hiểu hợp đồng API nào có thể tin cậy và phần nào phụ thuộc nền tảng.
2. Giải thích vì sao mạng chia sẻ tệp có thể không phát sự kiện đầy đủ hoặc kịp thời.
3. Xử lý `OVERFLOW`, sự kiện lặp, thay đổi tệp khi đang ghi và mất registration.
4. Kết hợp watch với **đối chiếu trạng thái (reconciliation)**, polling/snapshot và xử lý idempotent.
5. Thiết kế một ứng dụng Java 17+ chạy được và chọn giải pháp phù hợp yêu cầu nghiệp vụ.

## 2. Cơ chế cơ bản của WatchService

Một ứng dụng thường tạo service, đăng ký **thư mục**, lấy `WatchKey` và xử lý sự kiện:

~~~java
try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
    WatchKey registration = directory.register(
            watcher,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE);

    while (registration.isValid()) {
        WatchKey key = watcher.take(); // Chờ một key được báo có thay đổi.
        for (WatchEvent<?> event : key.pollEvents()) {
            System.out.println(event.kind() + ": " + event.context());
        }
        if (!key.reset()) {
            break; // Registration không còn hợp lệ.
        }
    }
}
~~~

Đây là **đoạn minh họa API**, không phải chương trình độc lập: cần các import trong `java.nio.file` và `directory` là một `Path` trỏ tới thư mục hiện có. Trong ứng dụng thật, thường quản lý nhiều key và không giả định key nhận về luôn là key của một thư mục duy nhất.

Các bước quan trọng:

- `directory.register(watcher, ...)` đăng ký thư mục; `WatchKey` đại diện cho registration.
- `watcher.take()` chặn đến khi có key sẵn sàng; `poll()` trả ngay hoặc `poll(timeout, unit)` chờ tối đa thời gian xác định.
- `key.pollEvents()` cung cấp các sự kiện đã ghi nhận của key. Với các sự kiện `ENTRY_*` tiêu chuẩn trên thư mục, `event.context()` thường là `Path` **tương đối với thư mục được theo dõi**.
- Phải gọi `key.reset()` sau khi xử lý; nếu bỏ qua, key có thể tiếp tục ở trạng thái signalled và không được đưa trở lại hàng đợi đúng cách.
- `reset() == false` báo key không còn hợp lệ: chẳng hạn registration đã bị hủy hoặc đối tượng không còn truy cập được. Cần xử lý tiếp theo chính sách ứng dụng.
- Khi service đóng, `take()`/`poll()` không thể tiếp tục sử dụng; chờ có thể kết thúc bằng `ClosedWatchServiceException`.

**Sự kiện mô tả điều được hệ thống ghi nhận tại một thời điểm; chúng không phải bảo đảm rằng tình trạng mục tệp còn y nguyên lúc handler xử lý.**

## 3. Phần nào phụ thuộc nền tảng?

Java định nghĩa các kiểu sự kiện và API để nhận sự kiện, nhưng cho phép provider sử dụng cơ chế giám sát native hoặc polling. Do đó, hai môi trường cùng chạy mã Java 17 có thể cho hành vi khác nhau:

| Khía cạnh | Điều cần hiểu |
| --- | --- |
| Thời gian nhận sự kiện | Có thể nhanh hoặc chậm tùy provider, tải hệ thống và cơ chế theo dõi |
| Số lần `ENTRY_MODIFY` | Một thay đổi logic có thể sinh một hoặc nhiều sự kiện; không giả định đúng một |
| Thứ tự sự kiện quan sát | Không xây quy trình nghiệp vụ dựa vào việc mọi môi trường giữ đúng một thứ tự tổng quát |
| Phát hiện tệp tồn tại rất ngắn | Hệ thống polling có thể không kịp thấy tệp vừa tạo rồi xóa |
| Giới hạn hàng đợi | Triển khai có thể giới hạn số sự kiện chờ; dùng `OVERFLOW` để báo mất sự kiện |
| Xử lý rename/move | Không có sự kiện rename tiêu chuẩn riêng cho mọi provider; thường phải xem như thay đổi trạng thái mục |
| Theo dõi cây thư mục | Đăng ký một thư mục không có nghĩa tự động theo dõi đệ quy mọi thư mục con |
| Quyền và tình trạng mount | Mất quyền, đường dẫn không truy cập được hoặc thay đổi mount có thể ảnh hưởng việc giám sát |
| Thư mục chia sẻ qua mạng | Thay đổi thực hiện ở máy khác **không được Java bảo đảm** sẽ phát hiện |

Trên một số nền tảng, provider tận dụng tính năng hệ điều hành; ở nơi không có hỗ trợ thích hợp, nó có thể dùng cách quét định kỳ. Không nên viết mã sản xuất phụ thuộc cứng vào tên một cơ chế cụ thể của Linux, Windows hay macOS trừ khi đã kiểm tra môi trường triển khai.

Ví dụ, việc tạo rồi sửa `config.yml` có thể phát `ENTRY_CREATE` tiếp theo là nhiều `ENTRY_MODIFY`; ở môi trường khác có thể quan sát ít sự kiện hơn. Tên file, thời điểm phát và số event **không phải hợp đồng chứng minh file đã được ghi xong**.

## 4. Network file system: điểm đặc biệt cần cảnh giác

### 4.1. NFS/SMB là gì trong bối cảnh này?

Một ứng dụng Java có thể truy cập thư mục `/mnt/share/data` hoặc `Z:\shared\data` bằng `Path` giống như đường dẫn cục bộ. Tuy nhiên, dữ liệu thực tế có thể nằm trên máy chủ khác, được truy cập qua giao thức chia sẻ tệp như NFS hoặc SMB.

**Việc `Files.exists(path)` và `Files.readString(path)` hoạt động không chứng minh `WatchService` sẽ nhìn thấy mọi thay đổi ở share.** API chỉ xác nhận hệ thống tệp cho phép các thao tác tệp tương ứng, không đưa ra cam kết riêng về khả năng giám sát sự kiện giữa các máy.

### 4.2. Thay đổi từ chính client và từ máy khác

Xét ba thành phần:

~~~text
Máy A: Java WatchService đăng ký /mnt/share/inbox
              |
              | mount NFS/SMB
              v
         Máy chủ lưu trữ
              ^
              | thay đổi tập tin
              |
Máy B: ứng dụng tạo report.csv
~~~

Có ít nhất hai tình huống phải thử nghiệm riêng:

1. **Máy A tự tạo/ghi tệp qua mount:** hệ điều hành hoặc provider của A có thể nhận biết một số sự kiện do chính client A gây ra. Mức độ hỗ trợ và cách báo vẫn phụ thuộc nền tảng.
2. **Máy B tạo/ghi/xóa tệp trên cùng share:** thông tin thay đổi có thể không được chuyển tới cơ chế watch của A. **Java không yêu cầu provider phải phát hiện thay đổi do máy khác thực hiện.**

Nếu A chỉ gọi `watcher.take()` và không có sự kiện, chương trình có thể ngủ mãi dù `report.csv` đã xuất hiện trên server. Nếu business flow cần xử lý file đó, chỉ dựa vào event là không đủ.

Không mặc định rằng một phiên bản Linux, Windows hay một bản NFS/SMB cụ thể luôn có cùng hành vi ở mọi cấu hình. Server, client cache, hệ thống mount, quyền và provider đều có thể làm kết quả khác nhau.

### 4.3. Cache và dữ liệu quan sát trễ

Network file system thường có cơ chế cache metadata hoặc nội dung để giảm round-trip. Vì thế, khi một client nhìn thấy sự kiện hoặc thực hiện `Files.size`, `Files.getLastModifiedTime` hay `Files.readAllBytes`, thời điểm dữ liệu/metadata trở nên quan sát được có thể khác với thời điểm máy khác vừa ghi.

Hệ quả:

- Một lần đọc trạng thái không phải ảnh chụp giao dịch của toàn bộ share.
- Một sự kiện `ENTRY_MODIFY` không chứng minh tác giả đã đóng file hoặc dữ liệu đã ổn định.
- Chỉ so sánh `lastModifiedTime` có thể bỏ sót thay đổi nếu độ phân giải timestamp thô, thời gian bị làm tròn hoặc hai lần ghi cho cùng metadata quan sát được.
- Tệp có thể bị thay thế giữa lúc kiểm tra và lúc mở; phải xử lý lỗi I/O và kiểm tra tính nhất quán theo yêu cầu nghiệp vụ.

Cách khắc phục hoàn toàn phụ thuộc nhu cầu: checksum, size/mtime, phiên bản hoặc manifest, giao thức “ghi tệp tạm rồi công bố” và cơ chế xác nhận của phía producer.

## 5. WatchService không phải cơ chế phát hiện chắc chắn mọi thay đổi

### 5.1. Sự kiện có thể bị gộp hoặc lặp

Nếu một ứng dụng ghi tệp theo nhiều lệnh, không được tính “số lần `ENTRY_MODIFY`” thành số lần hoàn chỉnh của file. Sự kiện có thể bị gộp hoặc xuất hiện nhiều lần cho một quá trình ghi.

**Mẫu tốt:** dùng sự kiện như lời nhắc “hãy kiểm tra mục này”, sau đó đọc trạng thái hiện tại. Handler cần idempotent: xử lý lại cùng một file không gây tác dụng phụ lặp ngoài ý muốn.

### 5.2. `OVERFLOW` là tín hiệu mất dữ liệu sự kiện

Hệ điều hành/provider có thể phát sự kiện nhanh hơn ứng dụng lấy ra. Nếu triển khai biết đã loại bỏ một số sự kiện, `WatchService` có thể đưa `OVERFLOW` vào danh sách sự kiện của key.

~~~java
for (WatchEvent<?> event : key.pollEvents()) {
    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
        // Không thể biết đầy đủ file nào đã thay đổi.
        // Đánh dấu thư mục để quét lại trạng thái hiện tại.
        needsReconcile = true;
        continue;
    }

    Path changedName = (Path) event.context();
    Path changedPath = watchedDir.resolve(changedName);
    // Kiểm tra tình trạng thật của changedPath; tránh xử lý mù.
}
~~~

Đoạn này minh họa logic, cần `key`, `watchedDir`, `needsReconcile` đã được khai báo. Không giả định `OVERFLOW` có context là một tên file cụ thể. Khi có overflow, **không thể phục dựng đầy đủ lịch sử chỉ từ các event còn lại**; cần quét đối chiếu thư mục hoặc nguồn dữ liệu có lịch sử đáng tin.

### 5.3. Registration có thể không còn hợp lệ

Nếu thư mục bị xóa, quyền thay đổi hoặc file system mất khả năng truy cập, `WatchKey` có thể bị hủy. Hãy kiểm tra kết quả `reset()`:

~~~java
boolean valid = key.reset();
if (!valid) {
    // Ngừng tin tưởng registration cũ.
    // Ghi nhận lỗi và cân nhắc đăng ký lại khi thư mục sẵn sàng.
}
~~~

Cũng không nên giả định việc mount bị thay thế chắc chắn luôn làm key invalid ngay. Nếu một đường dẫn quan trọng trỏ tới mount mới nhưng watch cũ còn hiệu lực theo góc nhìn provider, ứng dụng vẫn cần cơ chế phát hiện/đối chiếu trạng thái ngoài event.

### 5.4. Đăng ký không hồi tố, không tự nhận snapshot

`WatchService` không tự phát toàn bộ các tệp đã có trong thư mục khi đăng ký. Nếu `inbox` đã có 1.000 file trước lúc ứng dụng chạy, không nên chờ `ENTRY_CREATE` để xử lý chúng.

Ngoài ra, trong khoảng thời gian thực hiện snapshot và đăng ký, một thay đổi có thể lọt qua nếu thứ tự không đúng. Đối với hệ thống cần phát hiện tối đa:

1. Đăng ký watch nếu provider hỗ trợ.
2. Quét trạng thái thực để lập baseline và nhận diện các file đã tồn tại.
3. Tiếp tục xử lý event và quét lại theo chu kỳ.
4. Chấp nhận event trùng rồi **lọc bằng idempotency/state**.

Cách này giảm khoảng trống phát hiện nhưng **không biến WatchService thành nhật ký sự kiện không mất dữ liệu**.

## 6. Ví dụ Java 17+ chạy độc lập: thấy sự khác nhau giữa event và trạng thái thực

Chương trình sau tạo một thư mục tạm, đăng ký theo dõi, tự tạo tệp `report.txt`, chờ tối đa hai giây để đọc event rồi kiểm tra trực tiếp sự tồn tại và nội dung file. Nó cố ý **không khẳng định luôn nhận được `ENTRY_CREATE`**: điều đó phụ thuộc provider và môi trường.

Lưu dưới tên **`WatchServicePlatformDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;

public class WatchServicePlatformDemo {

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("watch-platform-");
        Path file = directory.resolve("report.txt");

        try {
            try (WatchService watcher =
                         FileSystems.getDefault().newWatchService()) {

                directory.register(
                        watcher,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);

                // Tạo file sau khi đăng ký. Vẫn không giả định
                // provider phải phát một tập event cố định.
                Files.writeString(
                        file, "ready", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW);

                WatchKey key = watcher.poll(2, TimeUnit.SECONDS);
                boolean sawCreate = false;
                boolean sawOverflow = false;

                if (key != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind()
                                == StandardWatchEventKinds.OVERFLOW) {
                            sawOverflow = true;
                            continue;
                        }

                        Object context = event.context();
                        if (event.kind()
                                == StandardWatchEventKinds.ENTRY_CREATE
                                && file.getFileName().equals(context)) {
                            sawCreate = true;
                        }
                    }
                    if (!key.reset()) {
                        System.out.println("Watch key invalid");
                    }
                }

                boolean present = Files.isRegularFile(file);
                boolean correctContent = present
                        && "ready".equals(Files.readString(
                                file, StandardCharsets.UTF_8));

                System.out.println("Saw CREATE event: " + sawCreate);
                System.out.println("Saw OVERFLOW event: " + sawOverflow);
                System.out.println("Snapshot confirms file: "
                        + correctContent);
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }
}
~~~

Biên dịch và chạy trên JDK 17+:

~~~bash
javac -encoding UTF-8 WatchServicePlatformDemo.java
java WatchServicePlatformDemo
~~~

**Một kết quả có thể gặp** trên file system cục bộ hỗ trợ việc giám sát:

~~~text
Saw CREATE event: true
Saw OVERFLOW event: false
Snapshot confirms file: true
~~~

**Nếu provider chưa phát event trong hai giây**, phần đầu có thể thay bằng:

~~~text
Saw CREATE event: false
Saw OVERFLOW event: false
Snapshot confirms file: true
~~~

Đây là hai **ví dụ kết quả minh họa**, không phải bảo đảm stdout giống nhau trên mọi hệ thống. Nếu key bị hủy, chương trình có thể in thêm `Watch key invalid`. Nếu `newWatchService` hoặc `register` không được hỗ trợ hay có lỗi I/O thì chương trình có thể ném exception; ứng dụng production phải bắt và chuyển sang chiến lược dự phòng phù hợp.

### Vì sao ví dụ được viết như vậy?

1. `directory.register(...)` diễn ra **trước** khi tạo `report.txt`; như vậy demo không phụ thuộc một event hồi tố cho file đã tồn tại.
2. `watcher.poll(2, SECONDS)` chờ có giới hạn; trả `null` nghĩa là **không có key sẵn sàng trong thời gian chờ**, không chứng minh thư mục không thay đổi.
3. `event.kind()` được so với `ENTRY_CREATE`, không suy ra event nào xảy ra chỉ từ một lần `Files.writeString`.
4. `event.context()` được so với `file.getFileName()`, là tên tương đối của mục trong thư mục watch. Không cần ghép đường dẫn tuyệt đối khi chỉ so sánh tên.
5. `OVERFLOW` được kiểm tra riêng vì không thể xử lý như một sự kiện của một file bình thường.
6. `key.reset()` được gọi sau khi lấy event; nếu invalid thì ứng dụng cần xem xét tình trạng đăng ký.
7. `Files.isRegularFile` và `Files.readString` **kiểm tra trạng thái thực** thay vì phỏng đoán từ việc event có/không đến. Dù không có `ENTRY_CREATE`, snapshot vẫn nhìn thấy file do chính chương trình vừa tạo.
8. `try-with-resources` đóng watcher trước khi xóa thư mục tạm; cleanup không phải minh họa cho tính năng đồng bộ dữ liệu từ máy từ xa.

**Hạn chế của demo:** chương trình chỉ đọc **một** key và một lô event trong thời gian ngắn; đây không phải vòng watch dùng liên tục hay test bao phủ NFS/SMB. Để đánh giá network file system, cần chạy watcher trên máy A và tạo/sửa/xóa file từ máy B theo kịch bản riêng.

## 7. Thiết kế vững hơn: Event là tín hiệu, snapshot là trạng thái

Một hệ thống xử lý file trong `inbox` không nên phụ thuộc việc nhận được đúng một event cho mỗi tệp. Có thể dùng kiến trúc:

~~~text
                +----------------------+
WatchService -->| Đánh dấu path "dirty"|----+
                +----------------------+    |
                                             v
Scheduled scan ------------------------> Reconciler
                                             |
                                             v
                                   Snapshot trạng thái file
                                             |
                                             v
                                   So sánh state đã xử lý
                                             |
                                  +----------+-----------+
                                  |                      |
                                 Mới/đổi               Không đổi
                                  |                      |
                             Xử lý idempotent          Bỏ qua
                                  |
                             Lưu checkpoint
~~~

### 7.1. Reconciliation là gì?

**Reconciliation** là thao tác đọc trạng thái tệp đang tồn tại, so với trạng thái đã biết, rồi xử lý những khác biệt cần thiết. Nó không cần biết chắc toàn bộ chuỗi event đã xảy ra; nó xác định **điều gì đang hiện diện hoặc khác với lần quan sát trước**.

Ví dụ snapshot đơn giản có thể bao gồm:

| Thuộc tính | Công dụng | Giới hạn |
| --- | --- | --- |
| Tên/path tương đối | Định danh đường dẫn trong thư mục đã giám sát | File bị rename/copy có thể đổi danh tính |
| Kích thước `Files.size` | Lọc tệp có lượng byte đổi | Hai nội dung khác nhau có thể cùng size |
| `lastModifiedTime` | Gợi ý có cập nhật | Độ phân giải timestamp/cache có thể bỏ sót |
| Hash nội dung (khi cần) | Phân biệt nội dung cùng size/mtime | Tốn đọc toàn bộ byte và CPU, có thể gặp file đang thay đổi |
| Version ID/manifest bên tạo | Nhận diện phiên bản đã công bố | Cần thiết kế phối hợp với producer |
| Checkpoint xử lý | Tránh xử lý lại tác dụng phụ đã hoàn tất | Phải lưu bền và có cơ chế cập nhật an toàn |

**Lưu ý về ảnh chụp:** quét thư mục bằng `DirectoryStream` hay `Files.list` không tự cung cấp snapshot nguyên tử; file có thể biến mất/đổi giữa lúc duyệt và lúc mở. Một reconciler phải chịu được `NoSuchFileException`, `AccessDeniedException` và lỗi mạng tạm thời.

### 7.2. Kịch bản quét đối chiếu đơn giản

~~~java
try (var entries = Files.list(inboxDirectory)) {
    for (Path candidate : entries.toList()) {
        if (!Files.isRegularFile(candidate)) {
            continue;
        }

        long size;
        try {
            size = Files.size(candidate);
        } catch (IOException transientFailure) {
            // File có thể vừa bị xóa hoặc share tạm không truy cập được.
            continue;
        }

        // So sánh metadata/hash/manifest với checkpoint.
        // Nếu có phiên bản mới và đã sẵn sàng, xử lý idempotent.
        // Chỉ checkpoint sau khi nghiệp vụ hoàn tất thành công.
    }
}
~~~

Đây là **đoạn minh họa**, sử dụng `inboxDirectory` được khai báo và import `java.nio.file.Files`, `Path`, `IOException`; không tự chạy độc lập. Với thư mục cực lớn, tránh `toList()` nếu cần hạn chế RAM; có thể duyệt stream khi còn mở hoặc dùng `DirectoryStream`. Với network share, từng thao tác lấy metadata cũng có chi phí mạng; cần giới hạn tần suất và mức đồng thời.

### 7.3. Kết hợp watchdog event với polling dự phòng

Một chu kỳ hoạt động thực tế:

1. Khi khởi động, cố đăng ký `WatchService` và thực hiện **full scan** ban đầu.
2. Khi nhận `ENTRY_CREATE`/`ENTRY_MODIFY`/`ENTRY_DELETE`, đánh dấu path/thư mục cần kiểm tra; không mặc định event chứa trạng thái cuối.
3. Nếu nhận `OVERFLOW`, đánh dấu **full rescan** vì danh sách event đã mất độ đầy đủ.
4. Dù không có event, sau một khoảng thời gian phù hợp vẫn chạy **scheduled reconciliation**, đặc biệt quan trọng với network mount.
5. Nếu watch không hỗ trợ, bị hủy hoặc lỗi, tiếp tục hoạt động theo **polling** với giám sát/cảnh báo; thử đăng ký lại khi thích hợp.
6. Sau khi xử lý xong file hoặc phiên bản, ghi checkpoint bền vững; chống trùng khi nhận thêm event hoặc ứng dụng khởi động lại.

**Tần suất polling** là lựa chọn dựa trên SLA và chi phí: quét mỗi giây có thể quá tải share nhiều triệu file; quét mỗi vài phút có thể không đủ nếu cần phản ứng rất nhanh. Dùng batch, phân vùng thư mục, chỉ mục/manifest hay thông báo từ producer khi hệ thống lớn.

## 8. “File đã thay đổi” không có nghĩa “File đã hoàn thành ghi”

Đây là rủi ro lớn của cả watch cục bộ lẫn remote. Khi observer nhận `ENTRY_CREATE` hoặc `ENTRY_MODIFY`, producer có thể còn đang ghi một file 5 GiB.

Nếu consumer xử lý ngay có thể đọc file thiếu, parse hỏng hoặc tính hash trên dữ liệu đang thay đổi. Các cách phối hợp tốt hơn:

### 8.1. Ghi tệp tạm rồi công bố

Producer ghi vào `report.csv.part`, đồng bộ/đóng theo nhu cầu, sau đó công bố bằng cách đổi tên sang `report.csv` **nếu phép move/rename thích hợp được hỗ trợ trong cùng file system**. Consumer chỉ xử lý tên hoàn chỉnh. Trên network file system, tính nguyên tử và khả năng nhìn thấy của rename phụ thuộc provider và hệ thống; không giả định mọi kiểu move đều atomic.

Nếu yêu cầu atomic move, Java hỗ trợ `StandardCopyOption.ATOMIC_MOVE` nhưng có thể ném `AtomicMoveNotSupportedException`; ứng dụng cần chiến lược dự phòng rõ ràng. Hệ thống phải kiểm tra khả năng triển khai thực tế trên share.

### 8.2. Manifest hoặc marker hoàn tất

Producer ghi dữ liệu, sau đó tạo file marker `report.csv.done` hoặc bản ghi manifest chứa tên, size, hash và version. Consumer chỉ xử lý khi marker và dữ liệu khớp.

Cách này vẫn cần quy định **thứ tự công bố**, kiểm tra đồng bộ và xử lý file marker tồn tại trước khi mọi client nhìn thấy dữ liệu do cache mạng. Khi yêu cầu độ tin cậy cao, có thể dùng một dịch vụ phân phối message hoặc cơ sở dữ liệu lưu transaction thay vì suy ra trạng thái hoàn tất chỉ từ sự kiện file.

### 8.3. “Đợi file ổn định” là heuristic

Một số ứng dụng chờ `size`/`mtime` không đổi qua vài lần quét rồi xử lý. Đây chỉ là **phỏng đoán**: producer có thể tạm nghỉ rồi ghi tiếp. Không sử dụng làm bằng chứng hoàn tất nếu nghiệp vụ cần chắc chắn.

## 9. Watch thư mục và giới hạn đệ quy

`Path.register(watcher, ...)` cho một thư mục không bảo đảm tự động đăng ký **toàn bộ cây con**. Nếu muốn giám sát `root/a/` và `root/b/`, thường phải đăng ký các thư mục con cần quan tâm.

Vấn đề khi có thư mục mới:

1. Nhận `ENTRY_CREATE` của một mục trong parent.
2. Kiểm tra mục đó có thực sự là thư mục hay không; nó có thể biến mất trước khi kiểm tra.
3. Nếu muốn theo dõi tiếp, đăng ký thư mục mới.
4. **Quét ngay nội dung thư mục vừa đăng ký**, vì bên trong có thể đã có file được tạo trước khi đăng ký xong.

Đối với cây nhiều thư mục hoặc share mạng, số registration, mức sâu, thay đổi quyền, symlink và lỗi khi duyệt đều cần giới hạn. **Không tự động đi theo symbolic link** đến thư mục ngoài phạm vi được phép nếu chưa có chính sách rõ ràng; có nguy cơ vòng lặp hoặc quét ra ngoài vùng tin cậy.

Một số file system provider có thể hỗ trợ modifier riêng, nhưng Java SE 17 **không định nghĩa một modifier đệ quy chuẩn áp dụng cho mọi nền tảng**. Tránh phụ thuộc API nội bộ chỉ vì môi trường phát triển đang hỗ trợ.

## 10. Các kiểu lỗi thường xuất hiện trên network share

| Tình huống | Triệu chứng | Hướng xử lý |
| --- | --- | --- |
| Máy B tạo file, máy A không nhận `ENTRY_CREATE` | Consumer không xử lý nếu chỉ dùng watcher | Full scan định kỳ, thông báo từ producer hoặc event bus |
| Sự kiện đến chậm | Xử lý trễ ngoài kỳ vọng | Đo latency thực, áp dụng polling/hệ thống thông báo theo SLA |
| Client cache metadata | Snapshot từ A chưa giống trạng thái vừa công bố ở B | Retry có giới hạn, manifest/version và quan sát tính nhất quán |
| Share tạm ngắt | `IOException`, tệp không truy cập được, key có thể invalid | Health check, backoff, reconnect, đăng ký/quét lại |
| Mount bị thay thế | Watch có thể gắn với đối tượng cũ hoặc trạng thái không đúng kỳ vọng | Theo dõi mount/file store và reconciliation; tái khởi tạo watcher nếu cần |
| File được tạo rồi xóa rất nhanh | Polling không nhìn thấy file từng xuất hiện | Nếu cần lịch sử mọi thao tác, dùng log/event có đảm bảo từ nguồn |
| Quá nhiều file đổi | `OVERFLOW`, tràn hàng đợi hoặc chậm xử lý | Full rescan, giới hạn tải, tối ưu xử lý |
| Producer ghi từng phần | Consumer đọc file chưa hoàn tất | Publish protocol bằng tệp tạm/marker/version |
| Rename/move | Quan sát delete/create hoặc sự kiện khác nhau | Dựa trên trạng thái thực và định danh nghiệp vụ |
| Mtime cùng giá trị qua hai phiên bản | Bỏ sót nếu chỉ so timestamp | Kết hợp size/hash/version/manifest |
| Một event bị xử lý nhiều lần | Tạo trùng record, gửi trùng thông báo | Idempotency và checkpoint bền vững |

**Không có cách polling snapshot đơn thuần nào phục dựng được toàn bộ lịch sử các file đã xuất hiện rồi biến mất giữa hai lần quét.** Nếu yêu cầu “nhìn thấy mọi thao tác tạo/xóa trong quá khứ”, phải dùng một cơ chế được thiết kế như event log/audit từ chính nguồn sinh ra thay đổi.

## 11. Kiểm thử khả năng hỗ trợ trước khi đưa vào production

Một smoke test chỉ tạo file ngay trong JVM trên máy chạy watcher là **chưa đủ** để kết luận NFS/SMB hoạt động đúng. Nên kiểm tra theo ma trận:

| Nguồn thao tác | Các thao tác cần thử | Những gì cần đo |
| --- | --- | --- |
| Cùng tiến trình/host với watcher | Create, modify, delete | Event có đến không, bao nhiêu lần, độ trễ |
| Máy khác cùng mount/share | Create, modify, delete, rename | Có phát hiện hay chỉ nhìn thấy qua snapshot |
| Dữ liệu lớn/ghi lâu | Ghi nhiều chunk, close/rename | Event xảy ra khi nào; consumer có đọc sớm không |
| Burst hàng nghìn file | Create/modify liên tiếp | Có `OVERFLOW`, mất event, thời gian full scan |
| File tuổi thọ ngắn | Create rồi delete rất nhanh | Có bị bỏ sót không |
| Gián đoạn kết nối | Ngắt/reconnect mạng, remount | `WatchKey` còn valid hay không, khi nào cần đăng ký lại |
| Khởi động/restart | Có file trước khi đăng ký | Initial scan có phát hiện và tránh trùng không |
| Thay quyền/di chuyển thư mục | Permission denied, thư mục mất | Lỗi và khả năng recovery |

**Đo các chỉ số có ý nghĩa:** tỉ lệ file đã công bố được phát hiện, độ trễ từ lúc “sẵn sàng” đến lúc xử lý, số `OVERFLOW`, số lần rescan, thời gian hoàn thành rescan, số lỗi I/O, độ sâu backlog, CPU và I/O trên server mạng.

Một triển khai có thể cho kết quả tốt khi thử nghiệm với 10 file nhưng thất bại khi production có 100.000 file hoặc mạng mất kết nối. Test cần dùng đúng loại mount, option, quyền, bản Java và tải gần với thực tế.

## 12. Quản lý concurrency và vòng đời an toàn

### 12.1. Không chặn thread nhận event bằng xử lý nặng

Nếu watcher xử lý trực tiếp việc parse/đọc hàng GiB trước khi `reset()`, hàng đợi sự kiện có thể tích tụ. Thiết kế hữu ích hơn:

- Thread watch chỉ nhận key, chuyển path/thư mục vào hàng đợi **có giới hạn** hoặc đánh dấu dirty.
- Worker/reconciler thực hiện I/O, validate, parse và nghiệp vụ.
- Các path trùng được gộp để giảm tải; khi queue quá lớn, ưu tiên rescan có kiểm soát.
- Theo dõi số item pending và thời gian chờ, không để backpressure biến thành tiêu thụ bộ nhớ vô hạn.

### 12.2. Kết thúc đúng cách

Dùng `try-with-resources` để đóng `WatchService`. Nếu thread khác gọi `close()` trong lúc `take()` đang chờ, thread chờ có thể nhận `ClosedWatchServiceException`; đó có thể là tín hiệu dừng có chủ đích.

Với `watcher.take()`, thread cũng có thể nhận `InterruptedException`; hãy phục hồi interrupt nếu không ném tiếp:

~~~java
try {
    WatchKey key = watcher.take();
    // Xử lý key.
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    // Dừng vòng theo dõi theo chính sách ứng dụng.
}
~~~

Đây là đoạn minh họa, trong đó `watcher` đã được tạo và import `WatchKey`. Không gọi `reset()` một cách tùy tiện khi chưa xử lý xong lô sự kiện, và không để exception trong handler khiến toàn bộ watch loop dừng vĩnh viễn mà không có thông báo.

### 12.3. Path và an toàn thư mục

`WatchEvent.context()` chứa đường dẫn tương đối của mục trong thư mục đăng ký, nhưng trạng thái hệ thống tệp có thể thay đổi kể từ khi event xuất hiện. Trước khi mở file, kiểm tra phạm vi, quyền và loại đối tượng. Nếu vùng watch do tác nhân không tin cậy kiểm soát, phải xét symbolic link, thay thế file và rủi ro TOCTOU (time-of-check to time-of-use).

`watchedDir.resolve((Path) event.context())` chỉ **tạo một đường dẫn**, không bảo đảm mục còn tồn tại hay vẫn trỏ đến đối tượng ban đầu. Đặc biệt với share mạng, xử lý các lỗi truy cập là đường chạy bình thường.

## 13. Khi nào nên dùng WatchService thuần, khi nào cần phương án khác?

### Chỉ WatchService (có xử lý `OVERFLOW`) có thể đủ nếu

- Ứng dụng chủ yếu cần **làm mới giao diện** hoặc cache khi thư mục cục bộ thay đổi.
- Có thể chấp nhận mất một vài thông báo và tự kiểm tra lại trạng thái lúc cần.
- Không cần lưu lịch sử mọi thao tác tạo/xóa trong quá khứ.
- File system và hành vi provider đã được thử nghiệm ở quy mô phù hợp.

Ngay cả ở đây, nên có cơ chế refresh khi ứng dụng khởi động và khi key invalid.

### WatchService kết hợp polling/reconciliation nên dùng nếu

- Theo dõi **network share** hoặc bất kỳ storage nào mà event remote chưa được chứng minh đáng tin.
- Ứng dụng cần phát hiện tệp mới sau khi restart hoặc khi tạm mất kết nối.
- Một file có thể phát nhiều event hay có burst lớn.
- Cần tự phục hồi sau `OVERFLOW`, remount và lỗi I/O.
- Tính đúng đắn phụ thuộc **trạng thái hiện có**, không phải mọi bước lịch sử.

### Dùng notification/event log từ producer nếu

- Phải phát hiện **mọi** lần công bố kể cả file tồn tại cực ngắn.
- Cần thứ tự/định danh/ack/retry có hợp đồng rõ ràng.
- Xử lý nghiệp vụ yêu cầu kiểm soát đúng một lần theo ngữ nghĩa (thường qua idempotency + checkpoint).
- Nhiều máy xử lý cùng một thư mục và phải tránh nhận trùng hoặc xử lý đua.

Trong trường hợp đó, message queue, database outbox, manifest versioned hoặc một cơ chế phát sự kiện đáng tin từ chính hệ thống tạo file thường là nguồn sự thật rõ ràng hơn một watcher.

## 14. Lỗi phổ biến và cách khắc phục

**Lỗi 1 — “Java dùng WatchService nên sẽ nhận sự kiện từ mọi máy ghi vào SMB/NFS.”** Không có bảo đảm như vậy. Phải thử nghiệm thao tác từ client khác và có phương án quét lại.

**Lỗi 2 — “Không nhận event trong 5 giây chứng tỏ chưa có file.”** Sai. `poll(timeout) == null` chỉ nói chưa có key được phát hiện trong thời gian chờ. Kiểm tra snapshot và SLA.

**Lỗi 3 — “Một lần ghi file sẽ phát đúng một `ENTRY_MODIFY`.”** Sai. Sự kiện có thể lặp, gộp hoặc bị bỏ lỡ.

**Lỗi 4 — “Đã nhận modify thì file chắc chắn hoàn tất.”** Sai. Producer có thể vẫn đang ghi. Dùng giao thức công bố/marker hoặc xác thực readiness.

**Lỗi 5 — “Bỏ qua `OVERFLOW` vì không có context file.”** Sai. Đây là dấu hiệu đã mất tính đầy đủ của danh sách sự kiện; cần reconciliation/rescan.

**Lỗi 6 — “Gọi `pollEvents()` là đủ, không cần `reset()`.”** Sai. Sau khi xử lý key, phải reset để key trở lại trạng thái sẵn sàng nhận tín hiệu mới; kiểm tra kết quả boolean.

**Lỗi 7 — “Đăng ký root là tự động theo dõi đệ quy.”** Sai với hợp đồng chung của API. Muốn theo dõi thư mục con thì cần registration và xử lý thư mục mới.

**Lỗi 8 — “Chỉ cần so mtime là không thể mất cập nhật.”** Sai. Mtime bị ảnh hưởng bởi độ phân giải timestamp, cache, thao tác preserve metadata; bổ sung size/hash/version khi cần.

**Lỗi 9 — “Snapshot đầy đủ sẽ tái hiện mọi lịch sử file.”** Sai. File tạo rồi xóa giữa hai lần scan có thể không để lại trạng thái nào để phát hiện.

**Lỗi 10 — “Thay mount xong thì watch cũ tự phục hồi chắc chắn.”** Không được bảo đảm. Dùng health check, kiểm tra registration, đăng ký lại và rescan khi phát hiện thay đổi môi trường.

## 15. Câu hỏi tự kiểm tra

**Câu 1.** Máy A đăng ký watcher trên NFS; máy B tạo file. Máy A không nhận `ENTRY_CREATE`. Có thể coi JVM bị lỗi?

**Đáp án:** Không. Java không bảo đảm phát hiện thay đổi remote. Kiểm tra provider và thiết lập cơ chế polling/reconciliation hoặc notification từ nguồn.

**Câu 2.** Watcher nhận ba `ENTRY_MODIFY` cho một file. Có phải producer đã lưu file ba lần thành công?

**Đáp án:** Không. Số event không phải số lần commit nghiệp vụ. Chỉ xử lý theo trạng thái tệp và cơ chế nhận diện phiên bản.

**Câu 3.** Khi gặp `OVERFLOW`, có nên chỉ bỏ qua event đó và tiếp tục các event còn lại?

**Đáp án:** Không nếu cần đầy đủ trạng thái. Phải quét đối chiếu phạm vi liên quan; không thể suy ra chắc chắn file nào đã bị bỏ qua.

**Câu 4.** Đăng ký watch sau khi file đã tồn tại sẽ tự sinh `ENTRY_CREATE` cho file đó?

**Đáp án:** Không. Cần initial scan để nhận các mục có trước thời điểm đăng ký.

**Câu 5.** Với consumer xử lý báo cáo tài chính từ shared folder, một sự kiện `ENTRY_CREATE` có đủ để xác nhận file sẵn sàng?

**Đáp án:** Không. Cần giao thức công bố (file tạm rồi rename thích hợp, marker/manifest hoặc tín hiệu từ producer), kiểm tra nội dung và tính toàn vẹn.

**Câu 6.** Nếu cần ghi lại mọi lần tạo/xóa file rất ngắn trên nhiều máy, WatchService + polling có bảo đảm không bỏ sót lịch sử?

**Đáp án:** Không. Cần nguồn event log/audit đáng tin cậy tại nơi sinh sự kiện.

**Câu 7.** Tại sao một hệ thống vẫn nên quét định kỳ khi watcher có vẻ hoạt động tốt?

**Đáp án:** Để nhận các thay đổi bị bỏ sót, phục hồi sau outage/overflow và kiểm tra tính nhất quán giữa trạng thái đã lưu với trạng thái thực.

## 16. Ghi nhớ

- **WatchService là cơ chế thông báo thay đổi với hành vi phụ thuộc nền tảng**, không phải event log đầy đủ hay cơ chế giao dịch.
- **Network file system đặc biệt quan trọng:** thay đổi từ máy khác có thể không phát hiện; kiểm tra thực tế bằng NFS/SMB và đúng cấu hình triển khai.
- **`OVERFLOW`, event lặp, event đến trễ và key invalid** đều phải có nhánh xử lý rõ ràng.
- **Nhận event không đồng nghĩa file đã ghi xong**; dùng protocol công bố và xác thực trạng thái.
- **Kết hợp event + reconciliation định kỳ + checkpoint/idempotency** là cách thực tế cho các hệ thống quan trọng; nếu phải bảo toàn toàn bộ lịch sử, cần nguồn sự kiện đáng tin từ producer.

### Tài liệu tham khảo chính thức

- [Java SE 17 — WatchService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/WatchService.html): hợp đồng API, `OVERFLOW` và giới hạn phụ thuộc nền tảng/remote file systems.
- [Java SE 17 — WatchKey](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/WatchKey.html): `pollEvents()`, `reset()`, trạng thái valid/cancel.
- [Dev.java — Watching a Directory for Changes](https://dev.java/learn/api/io/java-io/file-system/watching-dir-changes/): quy trình đăng ký và xử lý directory watch.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="watch-service-010">Đóng WatchService và dừng luồng chờ</a>

<details>
<summary>Xem chi tiết</summary>

# Đóng WatchService và dừng luồng chờ trong Java NIO.2

## 1. Vấn đề: luồng theo dõi thư mục có thể chờ vô hạn

`WatchService` thuộc `java.nio.file` cho phép ứng dụng nhận thông báo khi các mục trong một thư mục thay đổi, ví dụ tạo, xóa hoặc sửa tệp. Một chương trình theo dõi thường chạy vòng lặp ở một thread:

```java
while (running) {
    WatchKey key = watcher.take();
    // Xử lý event...
    key.reset();
}
```

Đoạn trên là **mã minh họa chưa độc lập**, giả định `watcher` là `WatchService`, `running` do ứng dụng quản lý. `take()` **chặn** khi chưa có key nào được báo sự kiện. Nếu ứng dụng chỉ đặt `running = false` lúc cần tắt, luồng đang đứng ở `take()` có thể không bao giờ quay lại để kiểm tra biến này.

Bài học tập trung vào hai hành động phải phối hợp:

1. **Dừng chờ và dừng vòng lặp xử lý sự kiện** theo một cơ chế được API hỗ trợ.
2. **Đóng `WatchService` để giải phóng tài nguyên** và vô hiệu hóa các đăng ký (`WatchKey`) còn liên quan.

Cách shutdown thông thường, khi đã quyết định không dùng lại dịch vụ theo dõi, là **gọi `watcher.close()` từ luồng điều khiển**, bắt `ClosedWatchServiceException` ở luồng chờ để kết thúc vòng lặp, rồi `join()` để xác nhận luồng đã thoát. Khi cần giữ dịch vụ mở, một phương án khác là `Thread.interrupt()` hoặc `poll(timeout)`, nhưng mỗi cách có ý nghĩa và hệ quả riêng.

### 1.1. Bốn đối tượng cần phân biệt

| Thành phần | Vai trò |
|---|---|
| `WatchService` | Dịch vụ nhận và xếp hàng các key có sự kiện, quản lý đăng ký |
| `WatchKey` | Token của đăng ký một đối tượng theo dõi, thông thường là thư mục |
| `WatchEvent<?>` | Một sự kiện cụ thể lấy qua `key.pollEvents()` |
| Thread theo dõi | Luồng do ứng dụng tạo, gọi `take()`/`poll(...)` và xử lý các key |

**Đóng WatchService không phải API kill thread.** Nó khiến thao tác chờ trên dịch vụ bị đánh thức bằng ngoại lệ và làm các key không hợp lệ; thread vẫn phải có logic thoát. Nếu thread đang xử lý nghiệp vụ lâu hoặc đang chặn ở API khác, `close()` của WatchService không đảm bảo dừng tức thì các công việc đó.

## 2. Luồng hoạt động bình thường trước khi shutdown

Để theo dõi một thư mục:

```java
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

Path directory = Path.of("incoming");

try (WatchService watcher =
        FileSystems.getDefault().newWatchService()) {

    WatchKey registration = directory.register(
            watcher,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY);

    // Chương trình xử lý sự kiện ở đây.
}
```

Đây là **đoạn minh họa trong phương thức có `throws IOException`**; cần thư mục `incoming` tồn tại và provider hỗ trợ các event này. `try-with-resources` bảo đảm gọi `watcher.close()` khi rời scope. Nếu đã tạo một thread riêng đang gọi `watcher.take()`, việc đóng service cần kết hợp với chờ thread thoát, không chỉ đóng ở cuối scope mà bỏ mặc thread.

Thông thường:

```text
directory.register(...) ──► WatchKey (trạng thái ready)
                                 │
                        phát hiện thay đổi
                                 ▼
                           key signalled
                                 │
                watcher.take() lấy key được báo
                                 │
                        key.pollEvents()
                                 │
                         xử lý các event
                                 │
                          key.reset()
                                 │
                    chờ sự kiện tiếp theo
```

Theo contract, sau khi một key được báo (`signalled`), nó cần `reset()` sau khi xử lý để trở lại trạng thái chờ tín hiệu mới. Nếu bỏ `reset()`, key có thể không được xếp hàng lại như mong đợi dù sự kiện vẫn tích lũy. `key.pollEvents()` rút các event hiện có ra khỏi key; không có nghĩa hủy đăng ký.

### 2.1. API lấy key và trạng thái khi chưa có event

| Phương thức | Khi chưa có key | Có thể bị ngắt? | Khi WatchService đã đóng |
|---|---|---|---|
| `watcher.take()` | Chờ cho đến khi có key hoặc bị đánh thức theo quy tắc API | Có, ném `InterruptedException` | Ném `ClosedWatchServiceException` |
| `watcher.poll()` | Trả `null` ngay | Không chờ theo thời gian | Ném `ClosedWatchServiceException` |
| `watcher.poll(timeout, unit)` | Chờ tối đa thời gian yêu cầu; nếu hết hạn trả `null` | Có, ném `InterruptedException` | Ném `ClosedWatchServiceException` |

Đây là điểm mấu chốt khi shutdown: `take()` không bị ảnh hưởng bởi một biến boolean mà ứng dụng vừa đổi; muốn thread rời trạng thái chặn, bạn phải tác động đến **thao tác chờ** (`close` hoặc `interrupt`), hoặc không dùng chờ vô hạn ngay từ đầu (`poll(timeout)`).

## 3. `WatchService.close()`: cách kết thúc dịch vụ theo dõi

Contract của `WatchService.close()` (Java 17/21):

- Đóng dịch vụ theo dõi và giải phóng tài nguyên liên quan.
- Nếu có thread đang chặn trong `take()` hoặc `poll(timeout)` để chờ key, thread đó được **đánh thức** bằng `ClosedWatchServiceException`.
- Các `WatchKey` còn hợp lệ gắn với dịch vụ bị **vô hiệu hóa**.
- Sau khi service đã đóng, những thao tác khác trên service như `take()`, `poll()` hoặc đăng ký mới đều không còn hợp lệ và có thể ném `ClosedWatchServiceException`.
- Gọi `close()` trên service đã đóng **không có tác dụng thêm** (idempotent).
- `close()` có chữ ký `throws IOException`; caller vẫn phải xử lý lỗi đóng trong những tình huống bất thường.

Cách dùng tối thiểu:

```java
WatchService watcher = FileSystems.getDefault().newWatchService();

Thread worker = new Thread(() -> {
    try {
        for (;;) {
            WatchKey key = watcher.take();
            // Xử lý key khi còn hoạt động.
            if (!key.reset()) {
                break;
            }
        }
    } catch (ClosedWatchServiceException ex) {
        // Kết thúc bình thường khi controller đóng WatchService.
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        // Kết thúc theo yêu cầu ngắt.
    }
});

worker.start();

// Ở một luồng khác khi cần shutdown:
watcher.close();
worker.join();
```

Đây là **đoạn minh họa** cần các import và phạm vi `throws IOException, InterruptedException`. Nó tập trung vào tín hiệu shutdown, chưa phải implementation xử lý event đầy đủ. Khi service đóng, `take()` bị đánh thức hoặc, nếu nó chưa kịp gọi `take()`, lần gọi tiếp theo phát hiện service đã đóng. Trong cả hai trường hợp, vòng lặp phải bắt `ClosedWatchServiceException` để thoát mà không log shutdown bình thường thành lỗi bất ngờ.

### 3.1. `ClosedWatchServiceException` là unchecked

`ClosedWatchServiceException` kế thừa `IllegalStateException`, không phải `IOException` hay `InterruptedException`. Vì vậy, một khối:

```java
try {
    watcher.take();
} catch (IOException ex) {
    // KHÔNG bắt được ClosedWatchServiceException.
}
```

Đoạn minh họa trên còn có vấn đề biên dịch ở vài ngữ cảnh vì `WatchService.take()` không khai báo `IOException`; nó chỉ nhằm chỉ ra **đừng bắt nhầm loại ngoại lệ**, không được chép như một ví dụ độc lập. Cách đúng:

```java
try {
    WatchKey key = watcher.take();
    // Xử lý key.
} catch (ClosedWatchServiceException ex) {
    // Dịch vụ đã đóng, thoát vòng theo dõi.
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    // Thread bị yêu cầu ngắt; thoát hoặc xử lý theo policy.
}
```

Đây là snippet có thể đặt trong method đã khai báo `watcher` và import `java.nio.file.ClosedWatchServiceException` cùng `WatchKey`. Nếu bất ngờ log toàn bộ `ClosedWatchServiceException` như failure cấp cao, ứng dụng có thể ghi báo động sai mỗi lần được shutdown hợp lệ.

### 3.2. `close()` không phải “gửi một WatchEvent shutdown”

`close()` không tạo một `ENTRY_DELETE` hoặc `OVERFLOW` giả để đưa vào `key.pollEvents()`. Tín hiệu shutdown đi qua **trạng thái dịch vụ đóng / ngoại lệ trên phương thức chờ**, không phải một event nghiệp vụ.

Hệ quả: code xử lý event không nên cố suy luận shutdown bằng cách chờ một tệp “stop.signal” xuất hiện. Đó là một giao thức khác, có thể không nhận event hoặc bị trễ; `close()` là tín hiệu trực tiếp của API phù hợp khi sẽ không tái sử dụng WatchService.

## 4. Ví dụ Java 17+ độc lập: đóng dịch vụ đánh thức luồng `take()`

Ví dụ tạo thư mục tạm, đăng ký nhận event tạo tệp, khởi chạy thread gọi `take()`, sau đó đóng service từ thread main. Kết quả được lưu trong `AtomicReference` và **chỉ in sau khi `join()` thành công**, nhờ đó thứ tự stdout không phụ thuộc scheduler. Không cần tạo file để “đánh thức” watcher.

**Tệp:** `WatchCloseDemo.java`

```java
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class WatchCloseDemo {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("watch-close-");

        try {
            try (WatchService watcher =
                    FileSystems.getDefault().newWatchService()) {

                directory.register(
                        watcher, StandardWatchEventKinds.ENTRY_CREATE);

                CountDownLatch aboutToWait = new CountDownLatch(1);
                AtomicReference<String> result =
                        new AtomicReference<>("not finished");

                Thread worker = new Thread(() -> {
                    aboutToWait.countDown();

                    try {
                        watcher.take();
                        result.set("key received");
                    } catch (ClosedWatchServiceException ex) {
                        result.set("service closed");
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        result.set("interrupted");
                    }
                }, "watch-worker");

                // Chỉ là hàng rào an toàn của demo khi có lỗi môi trường.
                // Daemon KHÔNG thay thế close/join trong ứng dụng thật.
                worker.setDaemon(true);
                worker.start();

                // Biết worker sắp gọi take, nhưng chưa chắc đã chặn.
                aboutToWait.await();

                // Đây là tín hiệu shutdown thật sự.
                watcher.close();

                worker.join(5000);
                if (worker.isAlive()) {
                    throw new IllegalStateException(
                            "watcher did not stop");
                }

                System.out.println("watcher stopped: "
                        + !worker.isAlive());
                System.out.println("result: " + result.get());

                try {
                    watcher.take();
                } catch (ClosedWatchServiceException ex) {
                    System.out.println(
                            "take after close: ClosedWatchServiceException");
                }
            }
        } finally {
            Files.deleteIfExists(directory);
        }
    }
}
```

Biên dịch/chạy:

```bash
javac WatchCloseDemo.java
java WatchCloseDemo
```

**Stdout dự kiến trong môi trường filesystem bình thường:**

```text
watcher stopped: true
result: service closed
take after close: ClosedWatchServiceException
```

**Giải thích kỹ từng bước:**

1. `Files.createTempDirectory` tạo nơi đăng ký sự kiện thật, không trùng với thư mục dự án.
2. `directory.register(watcher, ENTRY_CREATE)` tạo `WatchKey`. Chương trình **không tạo tệp trong thư mục**, vì mục tiêu là minh họa `take()` có thể đứng chờ khi không có event.
3. `CountDownLatch` thông báo worker đã tới gần lệnh `take()`; nó **không chứng minh** worker đang chặn. Ví dụ vẫn đúng nếu main đóng service trước khi worker thực sự gọi `take()`.
4. Main gọi `watcher.close()`. Nếu worker đang chặn, nó được đánh thức bằng `ClosedWatchServiceException`. Nếu chưa chặn, `take()` sau đó cũng gặp service đã đóng.
5. Worker bắt ngoại lệ và ghi kết quả `service closed`, không giữ vòng lặp vô hạn.
6. `join(5000)` đợi worker kết thúc tối đa khoảng 5 giây; nếu không kết thúc, ví dụ báo lỗi thay vì khẳng định thành công. Trong điều kiện bình thường với demo đơn giản này, worker dừng ngay.
7. In kết quả **sau join** nên không có race về thứ tự giữa stdout worker/main. Lần gọi `take()` thêm sau khi đóng chứng minh service không còn sử dụng được.
8. `try-with-resources` đóng lại service ở cuối (đóng lặp là không có tác dụng). `finally` xóa thư mục tạm.

Ví dụ đã được kiểm tra với JDK trên môi trường hỗ trợ WatchService. Nếu môi trường đặc biệt không hỗ trợ đăng ký sự kiện hoặc I/O thất bại, ngoại lệ sẽ được báo ra thay vì bịa stdout “thành công”.

### 4.1. Vì sao không cần tạo tệp để đánh thức `take()`?

Tạo một tệp chỉ tạo **event nghiệp vụ** có thể làm `take()` trả về `WatchKey` bình thường. Đó không phải tín hiệu kết thúc toàn bộ dịch vụ; sau khi xử lý và `reset()`, watcher có thể chờ tiếp vô hạn.

`close()` khác ở chỗ nó kết thúc dịch vụ, làm key bị vô hiệu và ngăn thao tác tiếp. Đây là lựa chọn hợp lý khi ứng dụng đang tắt hoặc watcher không còn được sử dụng.

## 5. So sánh `close()`, `interrupt()`, `cancel()` và timeout

Các kỹ thuật dừng phổ biến không tương đương:

| Kỹ thuật | Có đánh thức `take()` đang chặn? | WatchService còn mở? | WatchKey còn hợp lệ? |
|---|---|---|---|
| `watcher.close()` | Có; nhận `ClosedWatchServiceException` | Không | Các key liên quan bị vô hiệu |
| `worker.interrupt()` | Có; nhận `InterruptedException` nếu đang chờ theo kiểu interruptible | Có, trừ khi code tự đóng | Thông thường vẫn hợp lệ |
| `key.cancel()` | **Không bảo đảm** đánh thức một luồng đang `take()` khi không có key được xếp hàng | Có | Key bị hủy, trở nên invalid |
| `watcher.poll(timeout, unit)` với cờ dừng | Không cần đánh thức nếu đợi tới hết timeout | Có cho tới khi tự đóng | Tùy việc ứng dụng cancel/close |
| Chỉ gán `running=false` | Không | Có | Không đổi |

### 5.1. `Thread.interrupt()`: yêu cầu ngắt luồng, không đóng WatchService

`WatchService.take()` và `poll(timeout, unit)` là các điểm chờ có thể bị ngắt, khai báo `throws InterruptedException`. Nếu interrupt luồng đang đợi:

```java
worker.interrupt();
```

Luồng chờ thường nhận `InterruptedException`. WatchService **không tự đóng** vì một thread bị interrupt. Ứng dụng có thể tiếp tục dùng service trên một thread khác hoặc trong một giai đoạn sau, nếu đó là chính sách cần thiết.

Cách xử lý được khuyến nghị khi quyết định dừng:

```java
try {
    WatchKey key = watcher.take();
    // Xử lý key.
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    // Thoát; caller quyết định có đóng WatchService hay không.
}
```

Khôi phục interrupt status qua `Thread.currentThread().interrupt()` là thực hành phổ biến để không nuốt tín hiệu điều khiển. Tuy nhiên **không khôi phục interrupt rồi tiếp tục gọi `take()` vô điều kiện** trong một vòng lặp vô hạn: việc đó có thể gây vòng lặp lỗi hoặc bỏ qua ý định shutdown, tùy cách bạn thiết kế xử lý interrupt.

Nếu một watcher có tài nguyên thuộc sở hữu riêng, việc nhận interrupt thường nên đưa nó vào nhánh dọn dẹp (`finally`/`try-with-resources`), đóng WatchService do chính nó sở hữu, rồi thoát. Nếu WatchService thuộc một controller bên ngoài, hai bên cần thống nhất ai được quyền đóng.

### 5.2. `WatchKey.cancel()`: hủy **một đăng ký**, không dừng cả service

`WatchKey.cancel()` vô hiệu hóa key của đăng ký tương ứng:

```java
WatchKey key = directory.register(
        watcher, StandardWatchEventKinds.ENTRY_CREATE);

key.cancel();
System.out.println(key.isValid()); // false
```

Đoạn minh họa ở trong method xử lý `IOException`, với `directory` tồn tại và WatchService đã mở. Theo API, cancel không có tác dụng thêm nếu key đã bị cancel; key đã cancel **không thể trở lại valid** chỉ bằng `reset()`.

**Đặc biệt quan trọng:** cancel key không được tài liệu mô tả là tín hiệu bắt buộc phải giải phóng ngay thread đang `take()` không có event. Một key đã ở hàng đợi có thể vẫn ở đó cho đến khi được lấy; events pending có thể vẫn lấy qua `pollEvents()` sau cancel. Bởi vậy, **không** dùng `key.cancel()` làm cơ chế shutdown chắc chắn cho một loop đang chờ vô hạn.

Nếu toàn bộ dịch vụ không còn cần thiết, đóng service là cơ chế trực tiếp. Nếu chỉ không cần theo dõi một thư mục trong nhiều thư mục, cancel key đó và tiếp tục vận hành WatchService cho các đăng ký còn lại.

### 5.3. `poll(timeout)`: kiểm tra cờ dừng định kỳ

Một lựa chọn khác:

```java
while (running) {
    WatchKey key = watcher.poll(500, TimeUnit.MILLISECONDS);

    if (key == null) {
        continue; // Hết thời gian chờ, quay lại xem running.
    }

    for (WatchEvent<?> event : key.pollEvents()) {
        // Xử lý event.
    }

    if (!key.reset()) {
        break;
    }
}
```

Đây là **snippet minh họa**, cần `watcher`, cờ `running` có cơ chế memory visibility (`volatile` hoặc atomic), import `TimeUnit`, `WatchKey`, `WatchEvent` và xử lý `InterruptedException` / `ClosedWatchServiceException`. `poll(500, MILLISECONDS)` đợi tối đa 500 ms, trả `null` nếu chưa có key; không đợi vô hạn như `take()`.

Đánh đổi: độ trễ phản hồi cờ stop có thể gần timeout trong trường hợp không có event. Timeout ngắn tăng tần suất wake/check không cần thiết; timeout quá dài làm shutdown chậm. Nếu yêu cầu dừng nhanh và dứt khoát, `close()` thường rõ ràng hơn, nhất là khi WatchService không còn dùng lại.

### 5.4. `poll()` không tham số

`watcher.poll()` trả `null` ngay khi không có key và ném `ClosedWatchServiceException` khi service đã đóng. Một vòng:

```java
while (running) {
    WatchKey key = watcher.poll();
    // Nếu không có key mà cứ lặp không nghỉ, CPU có thể tăng cao.
}
```

là ví dụ **busy polling cần tránh**. Nếu không cần polling thực sự, dùng `take()` hoặc `poll(timeout)`. Đừng dùng `Thread.sleep(...)` để che mọi vấn đề mà không có cơ chế đóng và dọn dẹp rõ ràng.

## 6. Mẫu vòng lặp watcher đúng với việc đóng dịch vụ

Vòng lặp hoạt động thực tế phải xử lý ba điều: các key được báo, tình huống key invalid, và tín hiệu shutdown.

```java
static void runLoop(WatchService watcher, Path directory) {
    try {
        for (;;) {
            WatchKey key = watcher.take();

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();

                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    System.out.println("Rescan required");
                    continue;
                }

                Path relative = (Path) event.context();
                Path changed = directory.resolve(relative);

                // Gọi handler ứng dụng, tránh chặn quá lâu.
                System.out.println(kind.name() + ": "
                        + changed.getFileName());
            }

            if (!key.reset()) {
                // Directory không còn được theo dõi qua key này.
                break;
            }
        }
    } catch (ClosedWatchServiceException ex) {
        // Normal shutdown do WatchService.close().
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        // Dừng theo chính sách interrupt.
    }
}
```

Đây là **method helper dùng trong một class**, với các import `WatchService`, `WatchKey`, `WatchEvent`, `StandardWatchEventKinds`, `ClosedWatchServiceException`, `Path`; nó không phải một tệp có `main`. Bản này giả định **chỉ theo dõi đúng một thư mục**, và event kinds là những loại `ENTRY_*` có context `Path`. Khi watch nhiều thư mục, phải ánh xạ từng `WatchKey` về đúng đường dẫn, không dùng một `directory` duy nhất.

Những điểm cần hiểu:

1. `watcher.take()` có thể chặn. Khi một thread khác đóng service, luồng bắt `ClosedWatchServiceException` và thoát.
2. `key.pollEvents()` có thể trả nhiều event; không chỉ xử lý phần tử đầu.
3. `OVERFLOW` là thông báo có thể mất event. Context của nó có thể là `null` hoặc phụ thuộc implementation; **không cast sang Path** khi gặp `OVERFLOW`. Ứng dụng cần quét lại trạng thái thư mục khi tính chính xác là quan trọng.
4. Với event bình thường `ENTRY_CREATE`/`ENTRY_MODIFY`/`ENTRY_DELETE`, `context()` là tên tương đối với thư mục đăng ký. Ghép `directory.resolve(relative)` để xác định đường dẫn tương ứng.
5. Sau khi xử lý batch, `reset()` làm key sẵn sàng để được báo lại. Nếu trả `false`, key không còn valid; chương trình một-thư-mục thường kết thúc loop.
6. `runLoop` **không tự đóng watcher** ở đây vì ownership được để cho controller. Nếu worker là owner, cần bao `watcher` trong `try-with-resources` hoặc `finally` phù hợp.

### 6.1. `reset()` không phải cách đóng service

`key.reset()` làm key đã signalled quay lại trạng thái chờ báo mới, nếu key còn hợp lệ. Nó **không hủy** key và **không đóng** service. `reset()` có thể trả `false` khi đăng ký đã invalid (chẳng hạn directory không còn truy cập được, key đã cancel, hoặc service bị đóng).

Nếu bạn chỉ có một registration, `reset()==false` là tín hiệu hợp lý để thoát và dọn dẹp. Nếu có nhiều directory, nên xóa key invalid khỏi bảng ánh xạ và tiếp tục nếu còn registration hợp lệ. Không ép key invalid “chạy lại” bằng cách gọi `reset()` liên tục.

## 7. Shutdown có trình tự: signal → close → join → cleanup

Một shutdown đáng tin cậy thường gồm:

```text
Thread điều khiển:
  1. Đặt trạng thái stopping (ngăn đăng ký/thêm handler mới).
  2. Gọi watcher.close() để đánh thức take/poll và vô hiệu key.
  3. Chờ worker thoát bằng join/termination handle.
  4. Giải phóng tài nguyên ứng dụng còn liên quan.
  5. Chỉ sau bước 3 mới công bố "watcher stopped".
```

Ở thread worker:

```text
RUNNING:
  lấy WatchKey
  xử lý event (có thể kiểm tra stopping giữa các event)
  reset key

KHI CLOSE:
  take/poll -> ClosedWatchServiceException
  thoát loop
  finally: cleanup của worker
  kết thúc thread
```

Đừng thay bước 3 bằng `Thread.sleep(100)` rồi giả định thread đã chết. Scheduler, tải hệ thống và thời lượng callback khác nhau; `join()` hoặc một completion signal có tính xác nhận rõ hơn.

### 7.1. `close()` giải phóng thao tác chờ, không hủy handler tùy ý

Một lỗi thiết kế phổ biến:

```java
for (WatchEvent<?> event : key.pollEvents()) {
    doSlowWork(event); // Có thể chặn ở network/database/hàng đợi.
}
```

Đây là pseudocode theo cú pháp Java, `doSlowWork` do ứng dụng định nghĩa. Nếu main gọi `watcher.close()` trong khi worker đang đứng ở `doSlowWork`, việc đóng service **không** tự ngắt hàm nghiệp vụ đó. Worker chỉ gặp `ClosedWatchServiceException` khi thao tác lên WatchService sau đó.

Giải pháp:

- Giữ callback xử lý WatchEvent ngắn; nếu công việc dài, chuyển sang executor/task queue riêng.
- Sử dụng timeout/cancellation policy cho I/O của handler, không trông đợi `close(WatchService)` hủy một network call khác.
- Có cờ `stopping` an toàn đa luồng để worker tránh bắt đầu batch công việc mới.
- Khi chờ thread thoát, có thể dùng `join(timeout)` và báo rõ nếu thread vẫn sống; đừng khẳng định hoàn tất khi chưa dừng.
- Nếu sử dụng `interrupt()` như tín hiệu bổ sung, handler cần xử lý interrupt đúng; việc ngắt không phải cơ chế kill mọi code Java.

### 7.2. Ví dụ class quản lý lifecycle (không có stdout cố định)

Một wrapper có thể tạo, chạy, đóng và đợi thread theo dõi:

```java
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public final class DirectoryWatcher implements AutoCloseable {
    private final WatchService service;
    private final Thread thread;
    private volatile boolean stopping;

    public DirectoryWatcher(Path directory) throws IOException {
        service = directory.getFileSystem().newWatchService();

        try {
            directory.register(service,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
        } catch (IOException | RuntimeException ex) {
            service.close();
            throw ex;
        }

        thread = new Thread(this::loop, "directory-watcher");
        thread.start();
    }

    private void loop() {
        try {
            while (!stopping) {
                WatchKey key = service.take();

                if (stopping) {
                    break;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (stopping) {
                        break;
                    }

                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        // Lên lịch quét lại directory theo chính sách app.
                        continue;
                    }

                    // Chỉ xử lý ngắn; phần việc dài nên chuyển queue.
                    System.out.println(event.kind().name()
                            + ": " + event.context());
                }

                if (!key.reset()) {
                    break;
                }
            }
        } catch (ClosedWatchServiceException ex) {
            // Normal shutdown khi controller đóng service.
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            stopping = true;
            try {
                service.close();
            } catch (IOException ex) {
                // Trong app thật: báo lỗi cleanup qua logging/monitoring.
            }
        }
    }

    @Override
    public void close() throws IOException {
        stopping = true;

        // Close là bước đánh thức take(), không chỉ đổi biến.
        service.close();

        try {
            thread.join(TimeUnit.SECONDS.toMillis(5));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for watcher", ex);
        }

        if (thread.isAlive()) {
            throw new IOException("Watcher thread did not stop");
        }
    }
}
```

Đây là class **có thể biên dịch** trong `DirectoryWatcher.java`, không có `main` và **không có stdout ổn định** vì sự kiện thực tế phụ thuộc thư mục, OS và lịch thực thi. Nó nhấn mạnh lifecycle hơn là tối ưu hiệu năng.

Những điểm production cần cải thiện tùy yêu cầu:

- Constructor khởi chạy thread ngay; nếu muốn tránh `this` escape khi khởi tạo object, tách constructor và `start()`, quản lý trạng thái STARTING/RUNNING/STOPPING/CLOSED.
- Xử lý lỗi `service.close()` trong worker `finally` không nên im lặng ở ứng dụng thật; log hoặc đưa lỗi về bộ điều phối.
- Nếu `close()` bị gọi **ngay từ thread watcher**, `thread.join(...)` tự đợi chính mình sẽ không có ý nghĩa; thiết kế nên cấm hoặc tách phương thức `requestStop()` khỏi `awaitStopped()`.
- Nếu `service.close()` ném `IOException`, wrapper đang chọn chuyển lỗi ra ngoài; caller nên có cleanup/fallback phù hợp và không khẳng định thread đã dừng khi chưa join.
- Event loop ở đây chỉ xử lý một key và không hỗ trợ tự động theo dõi thư mục con. Nếu cần cây thư mục, phải đăng ký thêm thư mục và quản lý map key–directory.
- `volatile stopping` bảo đảm visibility của cờ, nhưng cờ riêng **không** đánh thức `take()`; `service.close()` đảm nhiệm việc đó.
- Có thể thay `thread.join(...)` bằng một `CountDownLatch terminated` và đợi completion, đặc biệt khi cần báo lỗi worker sang controller.
- Không dùng `daemon thread` hoặc JVM shutdown hook làm cơ chế dọn dẹp duy nhất cho ứng dụng chạy lâu.

Ví dụ sử dụng trong một method (đoạn minh họa):

```java
try (DirectoryWatcher watcher =
        new DirectoryWatcher(Path.of("incoming"))) {
    // Ứng dụng thực hiện công việc khác.
    // Khi thoát scope, close() dừng watcher và đợi tối đa 5 giây.
}
```

`incoming` phải là directory tồn tại; caller cần xử lý `IOException`. Những lỗi khi `close()` báo timeout nghĩa là không nên log “shutdown complete” nếu worker vẫn hoạt động.

## 8. Interrupt status và các race khi shutdown

### 8.1. Thread đang chờ `take()`, controller gọi đồng thời `close()` và `interrupt()`

Nếu hai tín hiệu đến gần nhau, thứ tự chính xác ngoại lệ được quan sát có thể khác nhau. Không viết logic chỉ coi **một** trong hai ngoại lệ là shutdown hợp lệ rồi gán ngoại lệ còn lại thành lỗi không thể phục hồi.

Một watcher có yêu cầu đóng rõ ràng thường xử lý cả:

```java
try {
    watcher.take();
} catch (ClosedWatchServiceException ex) {
    // WatchService đã đóng.
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    // Thread được yêu cầu ngắt.
}
```

Nếu `watcher.close()` là bước shutdown chủ đạo, tránh interrupt “dự phòng” quá sớm khi chưa xác minh tình trạng thread; việc interrupt là một hành động mạnh hơn ở mức thread và có thể ảnh hưởng những đoạn code khác đang sử dụng interrupt.

### 8.2. Chỉ `running=false` chưa đủ

Ngay cả khi biến được khai báo:

```java
private volatile boolean running = true;
```

và thread khác gán `running=false`, `volatile` chỉ giải quyết **visibility** khi thread đọc biến. Nếu thread đang blocked ở `take()`, nó chưa đọc lại `running`. Không có cơ chế tự đánh thức. Cần `close()` hoặc `interrupt()`, hoặc dùng `poll(timeout)` để kiểm tra cờ định kỳ.

### 8.3. Không nuốt `InterruptedException` rồi lặp ngay

Một pattern sai:

```java
for (;;) {
    try {
        watcher.take();
    } catch (InterruptedException ignored) {
        // SAI nếu đây là yêu cầu shutdown.
    }
}
```

Nếu controller mong interrupt dừng luồng, code trên làm mất ý nghĩa tín hiệu và có thể chờ tiếp. Hãy có chính sách rõ: khôi phục interrupt và thoát, hoặc nếu chủ đích tiếp tục, phải có lý do, trạng thái và quy tắc xử lý interruption phù hợp.

### 8.4. Đừng giả định có đúng một luồng chờ

Có thể nhiều worker đang gọi `take()` trên cùng WatchService. `close()` có contract đánh thức **các** thread đang chờ, và key liên quan đều invalid. Tuy nhiên nếu có nhiều worker cùng xử lý các key, chương trình phải điều phối `pollEvents()` và `reset()` tránh hai worker thao tác cùng một key không đúng thứ tự.

Tài liệu `WatchKey` khuyến cáo reset sau khi xử lý hết các event, nhất là trong thiết kế nhiều thread. Tạo nhiều thread watcher hiếm khi cần thiết cho ứng dụng đơn giản; một consumer đọc key và giao công việc qua executor thường dễ đảm bảo thứ tự và shutdown hơn.

## 9. Sự kiện có thể bị mất và lúc nào cần quét lại thư mục

`WatchService` là cơ chế **notification**, không phải một log giao dịch đầy đủ của filesystem. Sự kiện có thể bị gộp, trễ hoặc mất tùy provider và tải. `StandardWatchEventKinds.OVERFLOW` báo rằng một số event có thể đã bị mất hoặc bỏ qua.

```java
for (WatchEvent<?> event : key.pollEvents()) {
    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
        // Đọc lại trạng thái directory bằng Files.list/walk theo chính sách.
        continue;
    }

    Path name = (Path) event.context();
    Path changed = directory.resolve(name);
    // Xử lý đúng nghĩa của ENTRY_CREATE/DELETE/MODIFY.
}
```

Đoạn minh họa yêu cầu `key` hợp lệ, directory đã đăng ký và imports tương ứng. Khi shutdown, ưu tiên **ngăn lịch công việc mới**; không nên bắt đầu quét lại thư mục lớn sau khi cờ stopping đã bật, trừ khi yêu cầu nghiệp vụ bắt buộc hoàn thành lượt đang xử lý.

Các giới hạn:

- Theo dõi một directory thường thông báo thay đổi **entry trực tiếp**, không tự động theo dõi sâu toàn bộ cây.
- `ENTRY_MODIFY` có thể được gom và có `event.count()` lớn hơn 1.
- File có thể đã bị xóa hoặc đổi tên trước khi code xử lý event. Đọc đường dẫn cần chuẩn bị xử lý `NoSuchFileException` hoặc sai khác trạng thái.
- Trên một số provider, sự kiện có thể đến muộn hoặc có ngữ nghĩa khác nhau; không dùng một event `ENTRY_CREATE` làm bằng chứng nội dung tệp đã ghi xong hoàn toàn.
- Khi đóng WatchService, những event đang chờ có thể không được ứng dụng xử lý; lựa chọn **drain trước shutdown** hay **ngừng ngay** cần thuộc policy ứng dụng, không thể tự mặc định mọi event đã được tiêu thụ.

### 9.1. Shutdown “graceful” và “immediate” khác nhau

Một số hệ thống cần xử lý xong batch đã nhận rồi mới tắt, một số khác cần dừng ngay. Hai chiến lược hợp lý:

**Ngừng ngay:** đánh dấu stopping, đóng WatchService để wake `take()`, handler kiểm tra stopping và thoát, rồi controller đợi worker. Có thể bỏ qua event chưa xử lý; cần cơ chế quét/reconcile lại khi khởi động nếu tính đầy đủ quan trọng.

**Hoàn thành batch:** đánh dấu stopping để không bắt đầu lấy thêm key mới, cho batch đang xử lý kết thúc có giới hạn thời gian, rồi đóng service để bảo đảm không còn chờ ở `take()`. Nếu handler có thể chặn, cần timeout/cancellation riêng. Không sử dụng `close()` như một lời hứa “mọi event đã được xử lý”.

Định nghĩa rõ **khi nào ứng dụng báo STOPPED**: ví dụ service đã đóng, thread đã kết thúc và mọi tác vụ đã nhận được bàn giao hoặc dọn dẹp. Không chỉ dựa vào `watcher.close()` trả về mà không biết trạng thái các executor/handler khác.

## 10. Ownership: ai là người được phép đóng WatchService?

Một WatchService thường là tài nguyên dùng chung giữa controller và thread consumer. Có hai mô hình:

1. **Controller sở hữu:** Controller tạo service, đăng ký các directory, khởi worker và gọi `close()` khi cần shutdown. Worker bắt `ClosedWatchServiceException` và kết thúc. Đây là mô hình rõ ràng cho server/service.
2. **Worker sở hữu:** Worker tự tạo service trong `try-with-resources` và cung cấp API `requestStop()`. Khi nhận tín hiệu, worker đóng service trong `finally`, nhưng phải có cách đánh thức `take()` (ví dụ controller có quyền gọi `worker.interrupt()` hoặc đóng service qua một thread-safe handle do worker cung cấp).

Tránh “double ownership” mơ hồ, nơi handler sự kiện gọi `close()` bất ngờ trong khi module khác vẫn đang đăng ký directory mới. Cần quy định service lifecycle là một phần contract của thư viện.

### 10.1. `try-with-resources` đủ trong ứng dụng một luồng nhưng chưa đủ cho worker

Trong một method chạy watcher trên chính thread caller:

```java
try (WatchService watcher =
        FileSystems.getDefault().newWatchService()) {
    // Chạy loop và thoát theo chính sách của cùng thread.
}
```

`try-with-resources` sẽ đóng service khi rời khối. Nhưng nếu một thread **khác** đang gọi `take()`, chủ sở hữu còn cần `join` hoặc termination mechanism để xác nhận thread đó đã kết thúc. Tránh scope kết thúc “mất dấu” worker vì giữ một WatchService đã đóng chỉ là một phần shutdown.

### 10.2. Đóng service có làm `WatchKey` sống lại sau khi tạo mới service không?

Không. Khi service đã đóng, các key của nó invalid. Bạn không thể `reset` một key cũ để biến nó thành key của WatchService mới.

Nếu muốn bắt đầu theo dõi lại:

```java
try (WatchService replacement =
        directory.getFileSystem().newWatchService()) {
    WatchKey newKey = directory.register(
            replacement,
            StandardWatchEventKinds.ENTRY_CREATE);
    // Khởi động consumer trên replacement.
}
```

Đoạn này nằm trong method xử lý `IOException`, directory tồn tại. Tạo **dịch vụ mới**, đăng ký **key mới**, tạo/điều phối **consumer mới** theo lifecycle ứng dụng.

## 11. Kiểm thử shutdown một cách đáng tin cậy

Một test ngẫu nhiên tạo tệp rồi chờ event thường dễ bị ảnh hưởng bởi OS và thời điểm lập lịch. Để kiểm thử `close()` đánh thức thread `take()`, nên tạo một tình huống **không có event**, đồng bộ thời điểm thread sắp chờ và xác nhận nó kết thúc sau khi close.

Các điểm nên kiểm tra:

- Thread worker thực sự khởi động trước khi controller đóng (dùng `CountDownLatch`; latch chỉ xác nhận sắp tới đoạn chờ).
- `close()` từ thread khác không cần một file-event giả.
- Worker bắt được `ClosedWatchServiceException` và không tạo vòng lặp không kết thúc.
- `join(timeout)` hoặc `terminated.await(timeout)` trả đúng kết quả; nếu timeout, test thất bại thay vì ngủ thêm không có giới hạn.
- Service đã đóng thì `take()`/`poll()` sau đó ném `ClosedWatchServiceException`.
- Key đã đăng ký không còn valid theo `key.isValid()` sau đóng.
- `close()` lặp lại không gây lỗi vì service đã đóng.
- Test cleanup thư mục tạm trong `finally`.
- Không đòi hỏi thứ tự giữa các lần báo file-event thực tế nếu contract không bảo đảm.

### 11.1. Ví dụ kiểm tra key invalid sau khi đóng

```java
Path dir = Files.createTempDirectory("watch-key-");
try {
    WatchKey key;
    try (WatchService watch =
            dir.getFileSystem().newWatchService()) {
        key = dir.register(
                watch, StandardWatchEventKinds.ENTRY_CREATE);
        System.out.println("before close: " + key.isValid());
    }
    System.out.println("after close: " + key.isValid());
} finally {
    Files.deleteIfExists(dir);
}
```

Đây là snippet trong một phương thức xử lý `IOException`; khi provider hỗ trợ đăng ký, output dự kiến:

```text
before close: true
after close: false
```

Nó chứng minh khóa đăng ký bị vô hiệu theo contract của close, **không** chứng minh thread đã dừng vì không khởi tạo worker trong snippet này.

### 11.2. Test `interrupt()` không phải test `close()`

Nếu chỉ interrupt worker và thấy `InterruptedException`, bạn mới kiểm tra được **tính interruptible của thao tác chờ** và chính sách interrupt; bạn chưa kiểm tra tác dụng của việc đóng service hoặc vô hiệu key.

Các bài test nên có những trường hợp riêng: close khi đang chờ, interrupt khi đang chờ, timeout polling, cancel một key trong khi vẫn còn key khác, và đóng service khi worker đang xử lý event dài. Mỗi case có điều kiện “STOPPED” và yêu cầu cleanup khác nhau.

## 12. Những lỗi phổ biến cần tránh

1. **Chỉ gán `running=false` mà không wake thread.** `take()` có thể chờ vô hạn nên không đọc lại cờ.
2. **Chỉ gọi `WatchKey.cancel()` để dừng watcher.** Cancel hủy đăng ký, không bảo đảm giải phóng luồng `take()` đang chờ.
3. **Nghĩ `close()` trả về là mọi worker đã kết thúc.** Close làm wake thao tác chờ, nhưng thread vẫn phải xử lý ngoại lệ và thoát; dùng `join`/completion.
4. **Không bắt `ClosedWatchServiceException`.** Normal shutdown thành stack trace không cần thiết hoặc task kết thúc bằng exception không được ghi nhận đúng.
5. **Bắt `IOException` thay cho `ClosedWatchServiceException`.** Exception của dịch vụ đóng là unchecked `IllegalStateException` subclass, không phải IOException.
6. **Nhầm `ClosedWatchServiceException` với `InterruptedException`.** Close dịch vụ khác interrupt thread; service sau interrupt có thể vẫn mở.
7. **Nuốt `InterruptedException` rồi gọi `take()` tiếp.** Có thể bỏ qua yêu cầu shutdown.
8. **Gọi `watcher.take()` sau `close()` và mong được `null`.** Nó ném `ClosedWatchServiceException`.
9. **Nghĩ `poll()` sau đóng trả `null`.** Khi service đóng, nó ném exception, không phải “chưa có event”.
10. **Cho rằng `close()` tự phát sinh một `WatchEvent` shutdown.** Không có event giả như vậy; luồng được wake bởi closed state.
11. **Dựa vào `Thread.sleep(...)` thay `join()`.** Sleep không xác nhận worker đã kết thúc.
12. **Gọi `join()` từ chính worker lên nó.** Tự chờ chính mình có thể gây treo; thiết kế phân vai requestStop/awaitStop.
13. **Dùng daemon thread để né cleanup.** Daemon chỉ không giữ JVM sống, không bảo đảm dọn tài nguyên hoặc hoàn thành event.
14. **Dùng `poll()` trong tight loop.** Gây busy spin; dùng `take()` hoặc `poll(timeout)`.
15. **Nhầm `poll(timeout)` = `take()`.** Poll timeout trả `null` khi hết thời gian; take chờ tới khi key/event hoặc tín hiệu đặc biệt.
16. **Không gọi `key.reset()` sau khi xử lý.** Key đã signalled có thể không được xếp hàng lại để nhận notification mới.
17. **Tin `key.reset()` sẽ làm key invalid hợp lệ trở lại.** Reset trả false nếu key bị cancel/invalid; cần đăng ký lại với service phù hợp.
18. **Dùng context `OVERFLOW` như `Path`.** Context có thể null/implementation-specific; hãy quét lại trạng thái.
19. **Chỉ đọc một event trong `key.pollEvents()`.** Có thể có nhiều event và `count` lớn hơn 1.
20. **Giả định close bảo đảm xử lý hết event đang chờ.** Shutdown và xử lý queue là hai chính sách độc lập.
21. **Để handler lâu hoặc chặn I/O khác rồi mong `close()` dừng ngay.** Close chỉ wake thao tác trên WatchService.
22. **Quên close WatchService khi key cuối không còn valid.** Không còn key không tự động giải phóng service.
23. **Cố tái sử dụng service đã close.** Cần tạo WatchService mới và đăng ký key mới.
24. **Không có contract ownership.** Nhiều module có thể close service bất ngờ khi module khác còn cần.
25. **Tin filesystem events luôn đầy đủ và đúng thứ tự.** Có coalescing, overflow, khác biệt provider; cần reconcile khi cần sự thật đầy đủ.
26. **Xóa thư mục tạm trước khi watcher dừng.** Có thể tạo event bất ngờ hoặc lỗi cleanup; dừng/đóng rồi mới dọn.
27. **Báo “stopped” trước khi kiểm tra thread còn sống.** Đóng service và hoàn tất worker là hai mốc riêng.
28. **Quên xử lý IOException từ `close()`.** API có checked exception; log hoặc chuyển lỗi theo hợp đồng.
29. **Dùng interrupt để kill mọi callback.** Interrupt là cooperative mechanism, không ngăn được mọi công việc blocking tùy loại API.
30. **Giả định một WatchService theo dõi sâu mọi cây thư mục.** Thường cần đăng ký các directory con riêng nếu muốn recursive monitoring.

## 13. Tự kiểm tra

**1.** Tại sao `volatile boolean running=false` vẫn chưa đủ để dừng một thread đang `watcher.take()`?  
**Trả lời:** `volatile` cho phép nhìn thấy giá trị mới khi đọc biến, nhưng `take()` đang chặn và chưa quay lại đọc. Cần close, interrupt hoặc polling có timeout.

**2.** Khi thread A chặn ở `watcher.take()`, thread B gọi `watcher.close()`, thread A nhận gì?  
**Trả lời:** `ClosedWatchServiceException`, loại unchecked `IllegalStateException`. Thread A phải bắt và thoát loop.

**3.** Đóng WatchService có làm các `WatchKey` liên quan còn valid không?  
**Không.** Những key còn valid bị vô hiệu hóa.

**4.** `WatchKey.cancel()` có đóng toàn bộ WatchService không?  
**Không.** Nó hủy một đăng ký, không đóng service và không là tín hiệu đánh thức `take()` được bảo đảm.

**5.** `Thread.interrupt()` có đóng WatchService không?  
**Không.** Nó có thể khiến `take()` ném `InterruptedException`, trong khi service vẫn mở trừ khi code đóng riêng.

**6.** `watcher.poll(200, MILLISECONDS)` trả `null` nghĩa là service đã đóng?  
**Không.** Trong trường hợp đang mở, đó là timeout chưa có key. Service đã đóng gây `ClosedWatchServiceException`.

**7.** Tại sao sau `close()` vẫn cần `thread.join()`?  
**Vì** close không phải lệnh chờ thread kết thúc. Join xác nhận thread đã xử lý tín hiệu, chạy cleanup và thực sự rời trạng thái running.

**8.** Sau `key.pollEvents()` nên làm gì nếu tiếp tục theo dõi?  
**Trả lời:** `key.reset()` và kiểm tra giá trị trả về; nếu false, key không còn hợp lệ.

**9.** Trong event loop, `OVERFLOW` có an toàn để ép `(Path)event.context()` không?  
**Không.** Context có thể null hoặc do provider quyết định. Cần nhánh xử lý riêng, thường quét lại.

**10.** Có thể đăng ký một `WatchKey` invalid vào service mới bằng `reset()` không?  
**Không.** Tạo WatchService mới nếu cần và gọi `directory.register(...)` để nhận key mới.

**11.** Nếu `close()` được gọi khi worker đang thực hiện `doSlowWork(event)`, worker có dừng ngay không?  
**Không bảo đảm.** Close wake các phương thức chờ của WatchService; handler ứng dụng cần cancellation, timeout và điểm kiểm tra stopping riêng.

**12.** “Shutdown graceful” có nghĩa WatchService phải xử lý mọi event queued không?  
**Tùy chính sách ứng dụng.** Java close không bảo đảm drain mọi event. Ứng dụng phải thiết kế xử lý batch, bàn giao task hoặc reconcile khi restart.

## 14. Ghi nhớ và tài liệu tham khảo

**Nguyên tắc thực hành:** Khi dịch vụ theo dõi sẽ kết thúc, **đóng WatchService để giải phóng `take()` đang chờ**, bắt `ClosedWatchServiceException` để thoát bình thường, **đợi thread kết thúc** bằng `join`/completion, rồi dọn tài nguyên liên quan. `interrupt` là tín hiệu cho **thread**, `cancel` là tín hiệu hủy **một key**, còn `poll(timeout)` là cơ chế **kiểm tra định kỳ**. Đừng xem bốn việc này là tương đương.

Tài liệu Java SE 21 chính thức:

- `WatchService` — `close`, `take`, `poll` và cơ chế đánh thức thread: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/WatchService.html
- `WatchKey` — trạng thái signalled/ready, `pollEvents`, `reset`, `cancel`, `isValid`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/WatchKey.html
- `ClosedWatchServiceException` — unchecked exception khi service đã đóng: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/ClosedWatchServiceException.html
- `StandardWatchEventKinds` — ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY, OVERFLOW: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/StandardWatchEventKinds.html
- `Path.register` — đăng ký directory với WatchService: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Path.html
- Oracle Java Tutorials — Watching a Directory for Changes: https://docs.oracle.com/javase/tutorial/essential/io/notification.html


</details>

- [Quay lại đầu trang](#back-to-top)
