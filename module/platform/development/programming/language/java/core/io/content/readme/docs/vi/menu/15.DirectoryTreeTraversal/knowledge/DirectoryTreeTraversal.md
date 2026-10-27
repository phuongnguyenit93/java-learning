<a id="back-to-top"></a>

# Duyệt cây thư mục

## Menu
- [DirectoryStream và đóng handle thư mục](#directory-tree-traversal-001)
- [Files.list chỉ liệt kê con trực tiếp](#directory-tree-traversal-002)
- [Files.walk và độ sâu tối đa](#directory-tree-traversal-003)
- [Files.find kết hợp Path với BasicFileAttributes](#directory-tree-traversal-004)
- [walkFileTree, FileVisitor và SimpleFileVisitor](#directory-tree-traversal-005)
- [preVisitDirectory, visitFile, visitFileFailed và postVisitDirectory](#directory-tree-traversal-006)
- [FileVisitResult: tiếp tục, bỏ nhánh hoặc kết thúc](#directory-tree-traversal-007)
- [FOLLOW_LINKS, vòng liên kết và FileSystemLoopException](#directory-tree-traversal-008)
- [Thứ tự duyệt không mặc nhiên là thứ tự mong muốn](#directory-tree-traversal-009)
- [Sao chép hoặc xóa cây theo đúng thứ tự xử lý](#directory-tree-traversal-010)
- [Đóng Stream trả về từ list, walk và find](#directory-tree-traversal-011)

## <a id="directory-tree-traversal-001">DirectoryStream và đóng handle thư mục</a>

<details>
<summary>Xem chi tiết</summary>

# DirectoryStream và đóng handle thư mục trong Java NIO.2

## 1. Vì sao cần DirectoryStream?

Một thư mục trên hệ thống tệp có thể chứa vài tệp, hàng nghìn phần tử, hoặc một tập hợp thay đổi liên tục khi chương trình đang chạy. Khi cần **duyệt các phần tử trực tiếp bên trong một thư mục**, Java NIO.2 cung cấp `java.nio.file.DirectoryStream<Path>` và phương thức tạo phổ biến nhất:

~~~java
DirectoryStream<Path> stream = Files.newDirectoryStream(directory);
~~~

`DirectoryStream` là một **`Iterable` các đường dẫn `Path`** đến các phần tử được phát hiện trong thư mục. Khác với việc chỉ giữ một danh sách tên trong RAM, nó có thể được triển khai dựa trên tài nguyên duyệt thư mục của hệ điều hành và vì thế **phải được đóng**.

Điều quan trọng nhất của bài này: **`DirectoryStream` là tài nguyên có vòng đời, không chỉ là một tập hợp có thể bỏ mặc cho GC**. Hãy mở nó trong `try-with-resources`, duyệt khi nó còn mở, rồi đóng ngay sau khi hoàn tất hoặc khi có ngoại lệ.

~~~text
           Path thư mục
                │
                ▼
    Files.newDirectoryStream(dir)
                │
                ▼
    DirectoryStream<Path> (OPEN)
       │         │        │
       ▼         ▼        ▼
   file.txt    logs/    image.png
       │         │        │
       └────── duyệt ─────┘
                │
                ▼
       close() qua try-with-resources
                │
                ▼
       Handle thư mục được giải phóng
~~~

Bài học giúp bạn:

- Mở `DirectoryStream` để liệt kê các phần tử **cấp trực tiếp**.
- Hiểu khác biệt giữa `Path` đại diện thư mục, `DirectoryStream` có handle thật và `Stream<Path>` của Java Streams.
- Duyệt bằng vòng `for-each` đúng cách, tránh dùng iterator nhiều lần hoặc tự giả định thứ tự.
- Lọc theo **glob** và `DirectoryStream.Filter<Path>`.
- Xử lý lỗi khi **mở**, **duyệt**, **lọc** và **đóng** thư mục.
- Duyệt thư mục lồng nhau mà không rò rỉ handle.
- Chọn `DirectoryStream`, `Files.list`, `Files.walk` hay `Files.walkFileTree` tùy tình huống.
- Nhận biết rủi ro symbolic link, thay đổi đồng thời, đường dẫn không tin cậy và vòng đời tài nguyên.

## 2. Các API và kiểu dữ liệu cốt lõi

| Thành phần | Vai trò | Điểm cần nhớ |
| --- | --- | --- |
| `Path` | Mô tả đường dẫn | Tạo `Path` chưa mở thư mục |
| `Files.newDirectoryStream(dir)` | Mở nguồn duyệt các entry trực tiếp của thư mục | Có thể ném `IOException` ngay khi mở |
| `DirectoryStream<Path>` | `Iterable<Path>` đồng thời là `Closeable` | Phải đóng khi xong |
| `DirectoryStream.Filter<Path>` | Điều kiện nhận/bỏ một entry | Có thể ném `IOException` |
| `Files.newDirectoryStream(dir, "*.txt")` | Chỉ lấy entry có tên khớp glob | Glob khác cú pháp regex |
| `DirectoryIteratorException` | Báo lỗi I/O phát sinh **trong lúc duyệt** | Unchecked; truy xuất nguyên nhân bằng `getCause()` |
| `Files.isDirectory(path)` | Kiểm tra đường dẫn đại diện cho thư mục | Hành vi với symbolic link phụ thuộc `LinkOption` |
| `Files.list(dir)` | Trả `Stream<Path>` các entry trực tiếp | `Stream` này cũng cần đóng |
| `Files.walk(root)` | Tạo `Stream<Path>` duyệt sâu hơn | Cần đóng, tránh dùng vô giới hạn |
| `Files.walkFileTree(...)` | Duyệt cây với các callback có kiểm soát | Hữu ích cho đệ quy, lỗi và quyền truy cập |

**`DirectoryStream` không phải `java.util.stream.Stream`.** Nó triển khai `Iterable` và `Closeable`, nên dùng thuận tiện qua vòng `for (Path entry : stream)` nhưng không trực tiếp có các thao tác `map/filter/collect` của Java Streams. Nếu muốn API Stream, `Files.list` là lựa chọn riêng và cũng mang trách nhiệm đóng tài nguyên.

## 3. Mở, duyệt, đóng: mô hình chuẩn

Một hàm đúng cấu trúc:

~~~java
try (DirectoryStream<Path> entries =
        Files.newDirectoryStream(directory)) {
    for (Path entry : entries) {
        System.out.println(entry.getFileName());
    }
}
~~~

Đây là đoạn mã minh họa, chưa phải chương trình độc lập; `directory` là `Path` hợp lệ, phương thức bao ngoài khai báo hoặc bắt `IOException`. Những import cần thiết:

~~~java
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
~~~

Diễn biến của câu lệnh:

1. `Files.newDirectoryStream(directory)` yêu cầu provider mở việc duyệt thư mục.
2. Nếu mở thành công, `entries` đại diện cho một stream có thể sử dụng.
3. `for-each` lấy lần lượt các `Path` con và xử lý chúng.
4. Khi rời khối `try`, Java tự gọi `entries.close()`, kể cả khi trong thân khối có ngoại lệ.
5. Khi tài nguyên đóng, không tiếp tục dùng `DirectoryStream` cũ để duyệt thêm.

Việc tạo `Path.of("folder")` chỉ tạo một đường dẫn trừu tượng. **Không có handle thư mục cần đóng ở bước tạo Path**. Handle có thể xuất hiện khi gọi `Files.newDirectoryStream`, `Files.list`, mở `DirectoryStream` khác hoặc các API I/O thực sự truy cập hệ thống tệp.

### 3.1. Lỗi không đóng stream là gì?

Nếu mở nhiều `DirectoryStream` nhưng quên `close()`, tiến trình có thể giữ các directory handle/file descriptor lâu hơn cần thiết. Hệ quả tùy OS và provider:

- Tăng số handle đang mở, đến ngưỡng không mở thêm được.
- Gây khó khăn cho việc xóa, đổi tên hoặc thay thế một số thư mục trên hệ điều hành có cơ chế khóa.
- Tăng mức sử dụng tài nguyên không cần thiết, đặc biệt khi server xử lý nhiều request.
- Khi có ngoại lệ giữa lúc duyệt, tài nguyên bị bỏ lại nếu chỉ đóng trên nhánh thành công.

Garbage collector **không phải chiến lược vòng đời đáng tin** cho loại tài nguyên này. `try-with-resources` diễn đạt thời điểm giải phóng rõ ràng và an toàn hơn.

## 4. Ví dụ Java 17 đầy đủ: duyệt một thư mục tạm

Để ví dụ chạy được trên nhiều máy mà không phụ thuộc tệp người dùng, chương trình tự tạo thư mục tạm, ba entry và xóa chúng sau khi thử nghiệm. Vì thứ tự liệt kê **không được bảo đảm**, chương trình gom **tên** rồi sắp xếp trước khi in.

Lưu thành **DirectoryStreamBasicsDemo.java**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DirectoryStreamBasicsDemo {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("dir-stream-basics-");
        Path alpha = dir.resolve("alpha.txt");
        Path beta = dir.resolve("beta.log");
        Path subdir = dir.resolve("subdir");

        try {
            Files.writeString(alpha, "A", StandardCharsets.UTF_8);
            Files.writeString(beta, "B", StandardCharsets.UTF_8);
            Files.createDirectory(subdir);

            List<String> names = new ArrayList<>();

            try (DirectoryStream<Path> entries =
                         Files.newDirectoryStream(dir)) {
                for (Path entry : entries) {
                    names.add(entry.getFileName().toString());
                }
            }

            Collections.sort(names);
            System.out.println(names);
        } finally {
            Files.deleteIfExists(alpha);
            Files.deleteIfExists(beta);
            Files.deleteIfExists(subdir);
            Files.deleteIfExists(dir);
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac -encoding UTF-8 DirectoryStreamBasicsDemo.java
java DirectoryStreamBasicsDemo
~~~

Kết quả khi filesystem cho phép tạo/duyệt/xóa thư mục tạm:

~~~text
[alpha.txt, beta.log, subdir]
~~~

**Giải thích:**

- `Files.createTempDirectory` tạo thư mục thật với tên ngẫu nhiên; chương trình không in tên đó để stdout ổn định.
- `Files.writeString` và `Files.createDirectory` tạo entry có tên xác định.
- `Files.newDirectoryStream(dir)` duyệt **ba phần tử trực tiếp** của thư mục; nó không đi vào `subdir`.
- `entry.getFileName()` lấy thành phần cuối của đường dẫn để in tên.
- `Collections.sort(names)` làm thứ tự in có thể dự đoán. Bản thân `DirectoryStream` **không hứa thứ tự alphabet**, theo thời gian tạo hay theo thứ tự lưu vật lý.
- `try-with-resources` đóng `DirectoryStream` trước khi `finally` xóa thư mục và tệp. Đây là chi tiết quan trọng trên các nền tảng có kiểm soát handle nghiêm ngặt.

Trong ứng dụng thực, lỗi cleanup cũng có thể ném `IOException` và cần được báo đúng; đây là demo ngắn phục vụ học tập.

## 5. DirectoryStream chỉ duyệt con trực tiếp, không tự đệ quy

Ví dụ cấu trúc:

~~~text
project/
├─ pom.xml
├─ README.md
└─ src/
   ├─ Main.java
   └─ test/
      └─ ExampleTest.java
~~~

`Files.newDirectoryStream(project)` thông thường chỉ đưa về các entry `pom.xml`, `README.md` và `src`. Nó **không tự đi xuống `src/Main.java`** hay `src/test/ExampleTest.java`.

Nếu cần duyệt cấp sâu hơn, bạn phải mở thêm stream cho thư mục con, hoặc dùng API được thiết kế duyệt cây như `Files.walkFileTree`. Việc dùng `DirectoryStream` lồng nhau cần chú ý **mỗi stream có handle riêng**.

### 5.1. Kiểm tra file/thư mục khi duyệt

Bạn có thể xem loại entry:

~~~java
try (DirectoryStream<Path> entries =
        Files.newDirectoryStream(dir)) {
    for (Path entry : entries) {
        if (Files.isDirectory(entry)) {
            // Thư mục theo quy tắc kiểm tra hiện tại.
        } else if (Files.isRegularFile(entry)) {
            // Tệp thường.
        } else {
            // Có thể là symbolic link, entry đặc biệt hoặc không xác định.
        }
    }
}
~~~

Đây là đoạn minh họa với `Path dir` có sẵn. Mặc định `Files.isDirectory` và `Files.isRegularFile` có thể **theo symbolic link**. Nếu cần phân loại bản thân symbolic link hoặc tránh đi theo nó khi đệ quy, cân nhắc `LinkOption.NOFOLLOW_LINKS`. Ngoài ra, `false` không luôn có nghĩa “chắc chắn không thuộc kiểu này”: có thể không truy vấn được do điều kiện hệ thống tệp.

`DirectoryStream` trả các `Path`; không bảo đảm entry vẫn tồn tại hoặc cùng loại khi bạn thực sự mở nó sau đó. Đây là tính chất của filesystem có thể thay đổi đồng thời.

## 6. DirectoryStream là một tài nguyên: thời điểm close() quan trọng

`DirectoryStream<Path>` kế thừa `Closeable`, vì vậy có `close()`. Sau khi đóng, không nên tiếp tục gọi iterator hoặc lặp qua nó. `close()` giải phóng tài nguyên duyệt thư mục của provider; bản thân các `Path` đã lấy ra vẫn là **đối tượng biểu diễn đường dẫn**, không có nghĩa chúng trở nên vô hiệu về mặt kiểu.

Một cấu trúc đúng:

~~~java
List<Path> paths = new ArrayList<>();

try (DirectoryStream<Path> entries =
        Files.newDirectoryStream(dir)) {
    for (Path entry : entries) {
        paths.add(entry);
    }
}

// Tới đây DirectoryStream đã đóng.
// Đường dẫn vẫn có thể được dùng, nhưng tệp có thể đã thay đổi.
for (Path path : paths) {
    System.out.println(path.getFileName());
}
~~~

Đoạn mã minh họa, cần import `List`/`ArrayList` và API NIO; không phải chương trình độc lập. Nó **tích lũy mọi `Path` vào RAM**, phù hợp cho thư mục nhỏ khi phải dùng kết quả sau khi đóng. Nếu thư mục rất lớn, nên xử lý từng entry ngay trong khối `try` thay vì gom tất cả.

### 6.1. Không trả về iterator phụ thuộc stream đã đóng

Anti-pattern:

~~~java
static Iterator<Path> badIterator(Path dir) throws IOException {
    try (DirectoryStream<Path> entries =
            Files.newDirectoryStream(dir)) {
        return entries.iterator(); // Sai: stream đóng khi return.
    }
}
~~~

Đây là đoạn phương thức minh họa (cần import `java.util.Iterator`). Iterator còn tham chiếu tới nguồn mà phương thức đã đóng; người gọi không thể dựa vào nó để duyệt tiếp.

Thiết kế tốt hơn thường là:

- **Xử lý hoàn tất trong phạm vi `try`** và trả về kết quả nghiệp vụ nhỏ (count, summary...).
- **Trả một danh sách bản sao các Path** nếu số entry nhỏ và đã hoàn tất việc duyệt.
- **Để caller sở hữu DirectoryStream** nếu hợp đồng API công khai yêu cầu caller đóng và quản lý vòng đời.
- Nếu muốn lazy Stream API, cân nhắc `Files.list` nhưng vẫn quy định ai đóng Stream.

Không nên trả một `Stream<Path>` từ `Files.list` mà quên nói rõ trách nhiệm đóng ở caller.

### 6.2. Giao quyền sở hữu một cách tường minh

Nếu hàm tự mở stream, nó thường phải đóng:

~~~java
static long countEntries(Path dir) throws IOException {
    long count = 0;
    try (DirectoryStream<Path> entries =
            Files.newDirectoryStream(dir)) {
        for (Path ignored : entries) {
            count++;
        }
    }
    return count;
}
~~~

Đây là đoạn phương thức minh họa, cần import `IOException`, `DirectoryStream`, `Files`, `Path`. Nó trả số lượng entry đã duyệt thành công; nếu duyệt lỗi, nó không trả một số đếm nửa chừng như kết quả hoàn chỉnh.

Nếu một hàm **nhận** `DirectoryStream` do caller mở, đừng tự đóng nó trừ khi hợp đồng chuyển quyền sở hữu rõ ràng. Tài nguyên có thể vẫn cần được dùng tại nơi gọi.

## 7. Iterator của DirectoryStream: chỉ một iterator và không có thứ tự bảo đảm

`DirectoryStream` là `Iterable<Path>`, nhưng khác nhiều collection thông thường: hợp đồng của nó **chỉ hỗ trợ tạo tối đa một `Iterator`** cho một stream. Nếu yêu cầu iterator lần thứ hai, nó có thể ném `IllegalStateException`.

Vì vậy, không dùng các mẫu dựa trên khả năng lặp lại nhiều lần:

~~~java
try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
    for (Path entry : entries) {
        // Lượt duyệt thứ nhất.
    }
    for (Path entry : entries) {
        // Sai: yêu cầu iterator thứ hai trên cùng DirectoryStream.
    }
}
~~~

Đây là ví dụ về **cách làm sai**, không phải chương trình nên chạy như một demo thành công.

Nếu phải duyệt lại:

- Mở **một DirectoryStream mới** bằng `Files.newDirectoryStream(dir)`.
- Hoặc tích lũy các entry cần dùng vào danh sách trong lượt đầu, chấp nhận dùng RAM.
- Nếu thư mục có thể thay đổi, hiểu rằng lần mở mới có thể cho tập phần tử khác.

Thứ tự nhận các phần tử không được bảo đảm. Không giả định tên tệp sẽ được trả theo alphabet, thứ tự tạo, thứ tự chỉnh sửa hay thứ tự duyệt của command-line. Nếu cần thứ tự để hiển thị hoặc tính toán, hãy sắp xếp có chủ đích và chú ý chi phí bộ nhớ.

### 7.1. Duyệt có thể lazy và yếu nhất quán

Hệ thống tệp có thể bị tiến trình khác thay đổi **trong khi** bạn đang duyệt: thêm tệp, xóa tệp, đổi tên thư mục con. Một iterator dạng này không cung cấp snapshot giao dịch. Nó có thể phản ánh một số thay đổi hoặc không phản ánh, tùy provider và thời điểm.

Không suy ra từ việc đã thấy `entry` rằng:

- Entry chắc chắn còn tồn tại khi gọi `Files.readString(entry)`.
- Entry vẫn là tệp thường sau `Files.isRegularFile(entry)`.
- Tập entry nhận được là ảnh chụp nhất quán toàn bộ thư mục.
- Vòng duyệt đã tự khóa thư mục khỏi các thay đổi khác.

Trong các hệ thống cần bảo toàn tính nhất quán, cần thiết kế cơ chế snapshot/giao dịch/khóa/kiểm tra khác phù hợp môi trường; `DirectoryStream` một mình không cung cấp các bảo đảm đó.

## 8. Lọc theo glob với Files.newDirectoryStream(Path, String)

Để chỉ duyệt những entry có **tên phù hợp một glob**, dùng overload:

~~~java
try (DirectoryStream<Path> entries =
        Files.newDirectoryStream(dir, "*.txt")) {
    for (Path entry : entries) {
        System.out.println(entry.getFileName());
    }
}
~~~

Đây là đoạn minh họa. Mẫu `"*.txt"` được áp dụng theo quy tắc **glob filename** của `Files.newDirectoryStream(dir, glob)`, không phải mẫu regex Java tùy ý.

Điểm cần biết:

- Glob `*` thường khớp một chuỗi ký tự trong tên.
- Glob `?` khớp một ký tự trong tên theo quy tắc glob.
- `"*.txt"` nhắm tới các entry có tên kết thúc bằng `.txt` trong thư mục đang duyệt.
- Việc khớp chữ hoa/chữ thường có thể phụ thuộc provider/filesystem; đừng dựa vào hành vi giống nhau trên mọi nền tảng.
- API này chỉ xét **entry trực tiếp**, không tự tìm mọi tệp `.txt` trong cây con.
- Nó lọc **tên entry**, không mặc nhiên đảm bảo đó là tệp thường: một **thư mục** tên `notes.txt` cũng có thể khớp tên.

### 8.1. Glob không phải regular expression

Một ví dụ khác:

~~~java
Files.newDirectoryStream(dir, "*.{txt,md}");
~~~

Đây là đoạn minh họa; glob chọn tên kết thúc bằng `.txt` **hoặc** `.md` theo cú pháp lựa chọn trong glob của Java. Không diễn giải nó thành Java regex `".*\\.(txt|md)"`: hai ngôn ngữ mẫu có quy tắc khác nhau.

Nếu chỉ cần một tệp mở rộng cụ thể, `"*.txt"` dễ đọc hơn regex phức tạp. Nếu cần kiểm tra kích thước, loại entry hoặc metadata, dùng overload `Filter<Path>` ở phần sau.

### 8.2. Ví dụ Java 17: lọc .txt và xuất kết quả ổn định

Lưu **DirectoryStreamGlobDemo.java**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DirectoryStreamGlobDemo {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("glob-demo-");
        Path a = dir.resolve("a.txt");
        Path b = dir.resolve("b.txt");
        Path log = dir.resolve("notes.log");
        Path child = dir.resolve("child");

        try {
            Files.writeString(a, "A", StandardCharsets.UTF_8);
            Files.writeString(b, "B", StandardCharsets.UTF_8);
            Files.writeString(log, "log", StandardCharsets.UTF_8);
            Files.createDirectory(child);
            Files.writeString(child.resolve("nested.txt"),
                    "nested", StandardCharsets.UTF_8);

            List<String> names = new ArrayList<>();
            try (DirectoryStream<Path> entries =
                         Files.newDirectoryStream(dir, "*.txt")) {
                for (Path entry : entries) {
                    names.add(entry.getFileName().toString());
                }
            }

            Collections.sort(names);
            System.out.println(names);
        } finally {
            Files.deleteIfExists(child.resolve("nested.txt"));
            Files.deleteIfExists(child);
            Files.deleteIfExists(a);
            Files.deleteIfExists(b);
            Files.deleteIfExists(log);
            Files.deleteIfExists(dir);
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 DirectoryStreamGlobDemo.java
java DirectoryStreamGlobDemo
~~~

Kết quả:

~~~text
[a.txt, b.txt]
~~~

`nested.txt` không xuất hiện dù tên khớp mẫu: đó là entry **bên trong `child`**, không phải entry trực tiếp của `dir`. Đây là khác biệt cốt lõi giữa liệt kê một thư mục và duyệt cả cây.

## 9. Lọc bằng DirectoryStream.Filter<Path>

Khi cần điều kiện ngoài một glob tên tệp, sử dụng overload:

~~~java
DirectoryStream<Path> entries =
        Files.newDirectoryStream(dir, entry -> condition(entry));
~~~

Đây là đoạn **minh họa cú pháp**, với `condition(Path)` là hàm giả định trong ứng dụng. `DirectoryStream.Filter<Path>` là một functional interface có phương thức `accept(Path entry)` trả `boolean` và có thể khai báo `throws IOException`.

Ví dụ chọn **tệp thường** có tên bắt đầu bằng `report-` và kết thúc `.txt`:

~~~java
try (DirectoryStream<Path> entries = Files.newDirectoryStream(
        dir,
        entry -> Files.isRegularFile(entry)
                && entry.getFileName().toString().startsWith("report-")
                && entry.getFileName().toString().endsWith(".txt"))) {

    for (Path entry : entries) {
        // Xử lý các tệp được chọn.
    }
}
~~~

Đoạn này minh họa với `Path dir` có sẵn. `Files.isRegularFile` có thể truy cập metadata filesystem và có quy tắc đi theo symbolic link khi không truyền `LinkOption.NOFOLLOW_LINKS`; nếu không truy vấn được, nó có thể trả false. Nếu logic cần phân biệt lỗi metadata với “không phải tệp thường”, hãy dùng API như `Files.readAttributes` và xử lý `IOException` thay vì coi mọi false là một trường hợp bình thường.

### 9.1. Bộ lọc được gọi trong quá trình duyệt

Một điều kiện lọc không nên có side effect nguy hiểm (xóa/đổi tên entry, ghi nhận giao dịch không thể lặp, thay đổi toàn bộ thư mục), bởi thời điểm và cách thực hiện lọc liên quan đến việc lấy phần tử từ nguồn. Thiết kế filter ưu tiên kiểm tra ngắn gọn, thuần về điều kiện nhận entry.

Nếu filter phải truy cập thuộc tính tệp, nó có thể chậm và lỗi; việc duyệt thư mục lớn cần tính đến chi phí của **mỗi lần kiểm tra**.

### 9.2. IOException trong Filter

`Filter.accept(Path)` có thể ném `IOException`. Theo hợp đồng của `DirectoryStream`, lỗi I/O khi lấy entry, bao gồm lỗi từ filter, có thể xuất hiện dưới dạng **`DirectoryIteratorException`** khi duyệt. Loại này là unchecked, nhưng `getCause()` giữ `IOException` gốc.

Không nên viết `catch(IOException)` quanh toàn bộ vòng lặp rồi giả định sẽ bắt được mọi lỗi phát sinh từ iterator. Nên hiểu hai đường lỗi:

- Lỗi **mở/đóng stream** thường đi qua `IOException`.
- Lỗi **khi iterator tìm entry kế tiếp** thường được bọc trong `DirectoryIteratorException`.

Đoạn xử lý minh họa:

~~~java
try (DirectoryStream<Path> entries =
        Files.newDirectoryStream(dir, filter)) {
    for (Path entry : entries) {
        process(entry);
    }
} catch (DirectoryIteratorException e) {
    IOException cause = e.getCause();
    // Báo lỗi duyệt hoặc lọc, giữ cause.
} catch (IOException e) {
    // Báo lỗi mở hoặc đóng stream.
}
~~~

Đây là đoạn minh họa, **không phải chương trình độc lập**; `dir`, `filter` và `process` do ứng dụng cung cấp. `DirectoryIteratorException.getCause()` theo API trả `IOException`. Tránh chỉ lưu mỗi `getMessage()` mà bỏ stack trace và cause.

## 10. Ví dụ Java 17: lọc bằng lambda và tên entry

Ví dụ dưới kiểm tra tệp thường bắt đầu bằng `report-` và kết thúc `.txt`; thứ tự liệt kê được sắp xếp trước khi in.

Lưu **DirectoryStreamFilterDemo.java**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DirectoryStreamFilterDemo {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("filter-demo-");
        Path first = dir.resolve("report-1.txt");
        Path second = dir.resolve("report-2.log");
        Path other = dir.resolve("other.txt");
        Path namedDir = dir.resolve("report-dir.txt");

        try {
            Files.writeString(first, "ok", StandardCharsets.UTF_8);
            Files.writeString(second, "log", StandardCharsets.UTF_8);
            Files.writeString(other, "other", StandardCharsets.UTF_8);
            Files.createDirectory(namedDir);

            List<String> names = new ArrayList<>();

            try (DirectoryStream<Path> stream =
                         Files.newDirectoryStream(dir, entry ->
                                 Files.isRegularFile(entry)
                                 && entry.getFileName().toString()
                                         .startsWith("report-")
                                 && entry.getFileName().toString()
                                         .endsWith(".txt"))) {
                for (Path entry : stream) {
                    names.add(entry.getFileName().toString());
                }
            }

            Collections.sort(names);
            System.out.println(names);
        } finally {
            Files.deleteIfExists(first);
            Files.deleteIfExists(second);
            Files.deleteIfExists(other);
            Files.deleteIfExists(namedDir);
            Files.deleteIfExists(dir);
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 DirectoryStreamFilterDemo.java
java DirectoryStreamFilterDemo
~~~

Kết quả:

~~~text
[report-1.txt]
~~~

Hai entry khác đều bị loại vì phần tên không khớp đủ điều kiện. Thư mục `report-dir.txt` tuy có tên phù hợp nhưng không qua `Files.isRegularFile`. Lưu ý: nếu symbolic link trỏ đến tệp thường, `Files.isRegularFile` với tùy chọn mặc định có thể coi nó như tệp thường; cần xác định chính sách liên kết nếu không muốn điều đó.

## 11. Lỗi ở ba giai đoạn: mở, duyệt và đóng

Không nên coi thao tác “liệt kê thư mục” là một bước duy nhất. Nó có ít nhất ba giai đoạn có thể thất bại:

| Giai đoạn | API điển hình | Loại lỗi cần nghĩ tới |
| --- | --- | --- |
| Mở | `Files.newDirectoryStream(dir)` | `NotDirectoryException`, `NoSuchFileException`, `AccessDeniedException` hoặc `IOException` khác |
| Duyệt / áp dụng Filter | `iterator().hasNext()` / `next()` qua for-each | `DirectoryIteratorException` bọc nguyên nhân I/O |
| Đóng | `DirectoryStream.close()` | `IOException` từ việc giải phóng tài nguyên |

Ví dụ, một đường dẫn có thể tồn tại nhưng là **tệp thường**, không phải thư mục. Lời gọi `Files.newDirectoryStream` có thể ném `NotDirectoryException` hoặc `IOException` theo provider. Một thư mục có thể bị xóa hoặc bị mất quyền khi đang duyệt, khiến iterator thất bại sau khi đã trả vài entry.

**Đừng coi lỗi giữa vòng lặp là “đã duyệt xong”.** Kết quả lúc đó có thể chỉ là tập con, không có nghĩa danh sách đầy đủ.

### 11.1. EOF của iterator khác lỗi

Một vòng `for (Path entry : stream)` kết thúc bình thường khi không còn phần tử; không cần ném exception. `DirectoryIteratorException` nghĩa là có lỗi trong quá trình phát hiện phần tử, chứ không phải báo hết thư mục thông thường.

Với dữ liệu nghiệp vụ yêu cầu đếm đủ mọi entry, khi iterator ném exception cần đánh dấu **thất bại**, không trả số đếm đã có như kết quả hoàn chỉnh.

### 11.2. IOException lúc close vẫn quan trọng

Ngay cả khi đã duyệt hết entry, `close()` vẫn có thể báo `IOException`. `try-with-resources` để ngoại lệ này truyền ra đúng chỗ. Nếu thân `try` đã có ngoại lệ và close cũng thất bại, lỗi đóng được giữ dưới dạng **suppressed exception** gắn vào lỗi chính, giúp chẩn đoán đầy đủ.

Nếu có logic bắt `DirectoryIteratorException`, hãy giữ cả `getCause()` và những suppressed exceptions liên quan thay vì chỉ in một chuỗi thông điệp đơn giản.

## 12. Ví dụ kiểm thử lỗi iterator bằng một Filter chủ động ném IOException

Để kiểm chứng đường lỗi mà không phải dựa vào quyền hệ điều hành ngẫu nhiên, ta cố tình tạo filter ném `IOException` cho entry của thư mục thử nghiệm. Chương trình không phụ thuộc thứ tự liệt kê vì thư mục chỉ có một entry.

Lưu **DirectoryStreamErrorDemo.java**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class DirectoryStreamErrorDemo {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("iterator-error-");
        Path file = dir.resolve("one.txt");

        try {
            Files.writeString(file, "x", StandardCharsets.UTF_8);

            try (DirectoryStream<Path> entries =
                    Files.newDirectoryStream(dir, entry -> {
                        throw new IOException("Filter failed");
                    })) {
                for (Path ignored : entries) {
                    System.out.println("Unexpected entry");
                }
            } catch (DirectoryIteratorException e) {
                System.out.println("Cause: " + e.getCause().getMessage());
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 DirectoryStreamErrorDemo.java
java DirectoryStreamErrorDemo
~~~

Kết quả với provider hệ thống tệp mặc định theo hợp đồng bọc lỗi filter:

~~~text
Cause: Filter failed
~~~

Mục đích của ví dụ không phải “làm lỗi thật” trên ổ đĩa mà là thực hành **sự khác biệt giữa IOException và DirectoryIteratorException**. `Filter.accept` phát sinh IOException, iterator bọc lỗi để caller thấy qua `DirectoryIteratorException.getCause()`.

`Files.newDirectoryStream` vẫn cần được đóng, dù vòng lặp chưa xử lý được entry nào. `try-with-resources` giúp bảo đảm điều đó.

## 13. Đệ quy an toàn về handle: không mở vô hạn stream lồng nhau

Để duyệt nhiều tầng bằng `DirectoryStream`, một cách trực quan là đệ quy:

~~~java
static void visit(Path dir) throws IOException {
    try (DirectoryStream<Path> entries =
            Files.newDirectoryStream(dir)) {
        for (Path entry : entries) {
            if (Files.isDirectory(entry)) {
                visit(entry);
            } else {
                // Xử lý tệp.
            }
        }
    }
}
~~~

Đây là **đoạn phương thức minh họa**, không phải chương trình độc lập. Nó có những giới hạn đáng lưu ý:

- Mỗi lời gọi `visit` giữ stream của thư mục cha **đang mở** khi mở stream của thư mục con. Với cây thư mục rất sâu, nhiều handle đồng thời có thể tồn tại.
- Đệ quy sâu cũng có nguy cơ dùng nhiều stack và thậm chí `StackOverflowError`.
- `Files.isDirectory(entry)` mặc định có thể theo symbolic link, dẫn tới vòng lặp nếu liên kết tạo chu trình.
- Mỗi stream cần được đóng, kể cả khi đệ quy hoặc xử lý entry thất bại.
- Phân loại entry theo `Files.isDirectory` vẫn có thể gặp TOCTOU: trạng thái có thể đổi ngay sau khi kiểm tra.

Với nghiệp vụ cần duyệt cây sâu và kiểm soát symlink, lỗi, quyền truy cập, nên cân nhắc `Files.walkFileTree` với `FileVisitor` thay vì tự đệ quy đơn giản.

### 13.1. Duyệt hai tầng bằng hai phạm vi try-with-resources

Nếu chỉ cần đi vào vài thư mục con, có thể viết nested try:

~~~java
try (DirectoryStream<Path> top =
        Files.newDirectoryStream(root)) {
    for (Path entry : top) {
        if (Files.isDirectory(entry)) {
            try (DirectoryStream<Path> children =
                    Files.newDirectoryStream(entry)) {
                for (Path child : children) {
                    // Xử lý child.
                }
            }
        }
    }
}
~~~

Đoạn minh họa với `Path root`. Khi thoát khỏi vòng trong, stream con được đóng, còn stream cha vẫn mở để duyệt phần còn lại. Khi thoát khỏi khối ngoài, stream cha được đóng. Điều này kiểm soát tài nguyên tốt hơn việc gom các `DirectoryStream` vào một danh sách và chỉ đóng toàn bộ sau nhiều bước.

Nếu muốn tránh mở stream con khi liên kết trỏ tới thư mục, sử dụng `Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)`. Đây chỉ là bước nhận diện; cần tiếp tục xử lý lỗi truy cập/entry biến mất tại điểm mở thực sự.

## 14. Symbolic link, thay đổi đồng thời và SecureDirectoryStream

### 14.1. Symbolic link có thể khiến traversal đi xa hơn dự định

Giả sử:

~~~text
root/
  docs/
  jump -> /outside/
~~~

Khi dùng `Files.isDirectory(jump)` với tùy chọn mặc định, một symbolic link trỏ tới thư mục khác có thể được nhận là thư mục. Nếu đệ quy vào nó, chương trình có thể rời ngoài cây `root` hoặc đi vào chu trình.

Để tránh vô tình đi theo liên kết:

~~~java
boolean physicalDirectory =
        Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS);
~~~

Đây là đoạn minh họa; cần import `java.nio.file.LinkOption`. Nó phân biệt việc kiểm tra bản thân entry với việc theo đích liên kết. Nhưng việc kiểm tra rồi mở vẫn tồn tại **race** nếu entry bị thay thế giữa hai thao tác.

Không dùng `normalize()` đơn thuần như biện pháp bảo mật chống path traversal. `normalize()` chỉ rút gọn cú pháp; nó không tự giải quyết symbolic link hay khóa đường dẫn.

### 14.2. SecureDirectoryStream là gì?

Một số provider có thể cung cấp `SecureDirectoryStream<Path>`, một subtype cho phép thực hiện các thao tác tương đối với thư mục đang mở theo cách có khả năng giảm rủi ro race về đường dẫn. Không phải mọi provider đều hỗ trợ.

Mô hình kiểm tra:

~~~java
try (DirectoryStream<Path> entries =
        Files.newDirectoryStream(dir)) {
    if (entries instanceof SecureDirectoryStream<Path> secure) {
        // Có thể sử dụng API tương đối với thư mục đang mở.
    } else {
        // Provider không cung cấp tính năng này.
    }
}
~~~

Đây là đoạn minh họa; cần import `java.nio.file.SecureDirectoryStream`. Không được giả định phương thức này luôn có trên mọi filesystem. Nếu phần mềm xử lý đường dẫn do người dùng không tin cậy cung cấp, cần đánh giá quyền truy cập và rủi ro race ở cấp kiến trúc, không chỉ dựa vào một lệnh `isDirectory`.

### 14.3. Thư mục thay đổi trong lúc duyệt

`DirectoryStream` thường duyệt theo trạng thái provider và không đóng băng một snapshot giao dịch. Nếu tiến trình khác thêm/xóa/đổi tên entry:

- Một số entry mới có thể không xuất hiện, dù tồn tại khi vòng lặp kết thúc.
- Một entry đã trả có thể biến mất trước khi bạn truy cập nội dung của nó.
- Việc kiểm tra metadata và mở tệp ngay sau đó có thể trả kết quả khác.

Nếu yêu cầu danh sách mang tính kiểm toán, cần quy trình kiểm soát dữ liệu đầu vào, thời điểm chụp, tính bất biến hoặc lưu snapshot theo nghiệp vụ. `DirectoryStream` đơn thuần chỉ là cơ chế liệt kê entry, không phải transaction.

## 15. So sánh DirectoryStream, Files.list, Files.walk và walkFileTree

| Mục đích | API phù hợp | Quản lý tài nguyên |
| --- | --- | --- |
| Duyệt trực tiếp các entry bằng vòng `for-each` | `Files.newDirectoryStream(dir)` | Đóng `DirectoryStream` |
| Duyệt trực tiếp, lọc theo tên glob | `Files.newDirectoryStream(dir, "*.txt")` | Đóng `DirectoryStream` |
| Duyệt trực tiếp, filter theo metadata/custom logic | `Files.newDirectoryStream(dir, filter)` | Đóng `DirectoryStream` |
| Muốn thao tác `filter/map/collect` của Java Stream với con trực tiếp | `Files.list(dir)` | Đóng `Stream<Path>` |
| Duyệt nhiều mức sâu theo `Stream<Path>` | `Files.walk(root, maxDepth)` | Đóng `Stream<Path>` |
| Duyệt cây, cần callback `visitFileFailed` và điều khiển tiếp tục/bỏ qua | `Files.walkFileTree` | API quản lý việc duyệt nội bộ |

### 15.1. Files.list cũng mở tài nguyên cần đóng

~~~java
try (java.util.stream.Stream<Path> paths =
        Files.list(dir)) {
    paths.filter(Files::isRegularFile)
         .forEach(System.out::println);
}
~~~

Đây là đoạn minh họa với `Path dir` có sẵn. `Files.list` trả `Stream<Path>` lazy, gắn với tài nguyên duyệt thư mục; **không** nên tạo một stream, chỉ lấy iterator rồi bỏ quên việc đóng.

Một `java.util.stream.Stream` nói chung không phải lúc nào cũng gắn với tài nguyên hệ điều hành, nhưng `Files.list` và `Files.walk` là các ví dụ **cần close** theo hợp đồng.

### 15.2. Files.walk có đi sâu hơn nhưng phải giới hạn

~~~java
try (java.util.stream.Stream<Path> paths =
        Files.walk(root, 2)) {
    paths.forEach(System.out::println);
}
~~~

Đoạn minh họa với `Path root`. `maxDepth=2` giới hạn số mức đi xuống theo định nghĩa của API; phần tử gốc nằm ở độ sâu 0. Nếu muốn xử lý lỗi từng file và có chính sách bỏ qua/tiếp tục chi tiết, `walkFileTree` có thể rõ ràng hơn.

### 15.3. Files.walkFileTree cho phép xử lý lỗi theo callback

`Files.walkFileTree` gọi các phương thức của `FileVisitor` để duyệt và báo lỗi. Nó hữu ích khi cần duyệt toàn bộ cây, đếm hoặc xử lý tệp, cho phép kiểm soát việc bỏ qua subtree và xử lý `visitFileFailed`. Không nên dùng nó chỉ để liệt kê 2–3 entry trực tiếp nếu `DirectoryStream` đã đủ.

Ví dụ tư duy:

~~~text
DirectoryStream:   mở 1 thư mục → duyệt con trực tiếp → đóng
Files.list:        mở 1 thư mục → Stream operations → đóng
Files.walk:        duyệt sâu với Stream<Path> → đóng
walkFileTree:      duyệt sâu bằng FileVisitor callback
~~~

## 16. Ví dụ Java 17: đếm entry trực tiếp và so sánh với Files.list

Lưu **DirectoryStreamCountDemo.java**:

~~~java
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class DirectoryStreamCountDemo {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("count-demo-");
        Path a = dir.resolve("a.txt");
        Path b = dir.resolve("b.txt");
        Path child = dir.resolve("child");

        try {
            Files.createFile(a);
            Files.createFile(b);
            Files.createDirectory(child);
            Files.createFile(child.resolve("nested.txt"));

            long viaDirectoryStream = 0;
            try (DirectoryStream<Path> entries =
                         Files.newDirectoryStream(dir)) {
                for (Path ignored : entries) {
                    viaDirectoryStream++;
                }
            }

            long viaFilesList;
            try (Stream<Path> entries = Files.list(dir)) {
                viaFilesList = entries.count();
            }

            System.out.println("DirectoryStream: "
                    + viaDirectoryStream);
            System.out.println("Files.list: " + viaFilesList);
        } finally {
            Files.deleteIfExists(child.resolve("nested.txt"));
            Files.deleteIfExists(child);
            Files.deleteIfExists(a);
            Files.deleteIfExists(b);
            Files.deleteIfExists(dir);
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 DirectoryStreamCountDemo.java
java DirectoryStreamCountDemo
~~~

Kết quả:

~~~text
DirectoryStream: 3
Files.list: 3
~~~

Cả hai API chỉ liệt kê **con trực tiếp** của `dir`. Entry `child` được đếm một lần, còn `child/nested.txt` không nằm trong tập con trực tiếp. Ví dụ cố tình tạo tệp con để làm rõ điều này. Hai stream đều được đóng trước khi chương trình xóa thư mục.

Không suy rằng luôn có cùng số lượng nếu thư mục đang thay đổi đồng thời: đây là ví dụ với bộ dữ liệu do chương trình kiểm soát.

## 17. Xử lý exception: không biến lỗi duyệt thành danh sách hợp lệ

Một lỗi thiết kế phổ biến:

~~~java
List<Path> result = new ArrayList<>();
try (DirectoryStream<Path> entries =
        Files.newDirectoryStream(dir)) {
    for (Path entry : entries) {
        result.add(entry);
    }
} catch (Exception e) {
    // Bỏ qua mọi lỗi.
}
return result;
~~~

Đây là **anti-pattern** minh họa dưới dạng phần thân phương thức, không phải chương trình độc lập. Nếu lỗi xảy ra giữa vòng lặp, caller nhận một danh sách **chỉ chứa một phần entry** nhưng không biết nó chưa hoàn chỉnh.

Cách phù hợp hơn:

1. Để `IOException` và `DirectoryIteratorException` truyền lên nơi có thể quyết định nghiệp vụ.
2. Nếu cần thu thập nhiều thư mục độc lập, ghi trạng thái thành công/thất bại **riêng cho từng thư mục**.
3. Chỉ trả kết quả “đầy đủ” khi vòng duyệt và close kết thúc thành công.
4. Khi cần cung cấp kết quả một phần, dùng kiểu kết quả có trạng thái minh bạch (`partial`, số phần tử xử lý, lỗi gốc...).
5. Giữ `cause`, `suppressed exceptions` và ngữ cảnh đường dẫn đã xử lý an toàn khi log.

### 17.1. Bắt IOException và DirectoryIteratorException ở đúng vị trí

Một phương thức có thể viết:

~~~java
static void printDirectory(Path dir) throws IOException {
    try (DirectoryStream<Path> entries =
            Files.newDirectoryStream(dir)) {
        for (Path entry : entries) {
            System.out.println(entry.getFileName());
        }
    } catch (DirectoryIteratorException e) {
        throw new IOException("Failed while iterating: " + dir,
                e.getCause());
    }
}
~~~

Đây là đoạn phương thức minh họa; cần import `DirectoryIteratorException`, `DirectoryStream`, `Files`, `Path` và `IOException`. Lỗi mở/close vốn là `IOException` nên được truyền lên tự nhiên; lỗi iterator được bọc thành IOException với thông điệp ngữ cảnh và `cause` gốc.

Việc bọc này thích hợp nếu API tầng ngoài chỉ muốn phơi bày checked `IOException`. Nếu tầng ngoài cần phân biệt loại lỗi cụ thể, có thể giữ nguyên `DirectoryIteratorException` hoặc dùng kiểu lỗi nghiệp vụ có mã trạng thái. Tránh bọc mọi lỗi thành cùng một thông điệp vô nghĩa.

### 17.2. try-with-resources khi lỗi xuất hiện đồng thời với close

Nếu thân `try` ném exception và `DirectoryStream.close()` cũng ném exception, Java gắn lỗi close dưới dạng **suppressed** của lỗi chính. Khi cần chẩn đoán, kiểm tra `Throwable.getSuppressed()`. Việc tự đóng trong `finally` thiếu cẩn thận có thể che mất lỗi gốc.

Chỉ gọi `close()` khi thật sự sở hữu stream; không đóng một stream do caller cung cấp nếu hợp đồng không trao quyền sở hữu.

## 18. Quyền truy cập, đường dẫn và tính đúng đắn dữ liệu

### 18.1. DirectoryStream không bảo đảm quyền đọc nội dung từng entry

Có quyền liệt kê thư mục không đồng nghĩa có quyền đọc từng tệp bên trong, mở thư mục con, sửa/xóa entry hoặc đọc metadata của mọi entry. Quyền thực tế tùy filesystem và cơ chế kiểm soát truy cập.

Sau khi duyệt nhận được `Path entry`, thao tác:

~~~java
String text = Files.readString(entry);
~~~

vẫn có thể thất bại vì `entry` đã biến mất, là thư mục, không phải văn bản hoặc không được phép đọc. Đây là đoạn minh họa, không phải vòng lặp hoàn chỉnh.

### 18.2. Không tự tin vào tên tệp từ nguồn không đáng tin

Khi xử lý tên entry thành URL, lệnh shell, truy vấn hay tên tệp đích, cần áp dụng chính sách riêng về escaping, đường dẫn con, symbolic link và quyền. `DirectoryStream` chỉ cung cấp một `Path`; nó không thay thế kiểm tra dữ liệu đầu vào.

Ví dụ nếu duyệt thư mục upload của người dùng, không nên lấy `entry.getFileName().toString()` rồi nối trực tiếp vào chuỗi lệnh shell. Hãy dùng API phù hợp để tránh chèn lệnh và xử lý ký tự đặc biệt.

### 18.3. Kiểm tra rồi thao tác có thể có race

Đoạn:

~~~java
if (Files.isRegularFile(entry)) {
    byte[] bytes = Files.readAllBytes(entry);
}
~~~

là minh họa kiểm tra trước khi đọc. Giữa `isRegularFile` và `readAllBytes`, entry có thể bị thay đổi. Vì vậy ứng dụng vẫn phải xử lý `IOException` của thao tác đọc và không coi kiểm tra trước là bằng chứng tuyệt đối.

## 19. Hiệu năng và bộ nhớ

Một số lưu ý khi duyệt thư mục lớn:

- `DirectoryStream` cho phép lặp dần các `Path`; nó không buộc ứng dụng tạo ngay một `List<Path>` chứa mọi entry.
- Nếu chỉ cần đếm hay xử lý mỗi entry độc lập, dùng một vòng `for-each` trong `try-with-resources` và không tích lũy vô hạn.
- Nếu cần sắp xếp tên để hiển thị, bạn **phải trả chi phí bộ nhớ/CPU** cho việc gom rồi sort (hoặc áp dụng cơ chế sort ngoài bộ nhớ).
- Filter kiểm tra metadata nhiều lần có thể gây nhiều thao tác I/O; tối ưu theo nhu cầu và đo đạc.
- Nếu một thư mục chứa hàng triệu entry, đừng giữ DirectoryStream mở trong thời gian dài chỉ để chờ tương tác người dùng. Duyệt/đóng theo phạm vi công việc, hoặc chuyển sang kiến trúc truy vấn/index phù hợp.
- Không dùng `DirectoryStream` như một cơ chế “watch” báo thay đổi: nếu muốn theo dõi sự kiện tạo/xóa/sửa, xem `WatchService` với quy tắc riêng. Duyệt một lần không thiết lập đăng ký nhận sự kiện.
- Một `DirectoryStream` không có bảo đảm snapshot nhất quán; đừng xây kết luận giao dịch chỉ từ việc vòng lặp đã chạy hết.

### 19.1. Khi nào một collection có vẻ dễ hơn?

Nếu thư mục rất nhỏ và chỉ cần tên để hiển thị, việc tạo `List<String>` đã sort có thể đơn giản hơn thao tác trực tiếp với iterator. Tuy nhiên **việc lấy dữ liệu từ filesystem vẫn phải đóng** `DirectoryStream` hoặc `Stream<Path>`; danh sách kết quả không phải lý do bỏ qua close.

Ví dụ:

~~~java
List<String> names = new ArrayList<>();
try (DirectoryStream<Path> entries =
        Files.newDirectoryStream(dir)) {
    for (Path entry : entries) {
        names.add(entry.getFileName().toString());
    }
}
Collections.sort(names);
~~~

Đây là đoạn minh họa, cần các import Java Collections và NIO. Sau close, `names` là dữ liệu trong RAM, có thể dùng an toàn về mặt cấu trúc danh sách; nhưng filesystem có thể tiếp tục thay đổi, nên danh sách không còn phản ánh thời gian thực.

## 20. Các lỗi thường gặp và cách sửa

| Lỗi | Vì sao sai | Cách sửa |
| --- | --- | --- |
| Mở `DirectoryStream` rồi quên đóng | Rò rỉ directory handle/file descriptor | `try-with-resources` |
| Tạo `Path` rồi tưởng đã mở thư mục | Lẫn đường dẫn với tài nguyên | Chỉ `newDirectoryStream` mới mở việc duyệt |
| Duyệt sau khi đã rời khối `try` | Stream đã đóng | Xử lý trong khối hoặc trả bản sao dữ liệu |
| Gọi `iterator()` hai lần | `DirectoryStream` chỉ hỗ trợ tối đa một iterator | Mở stream mới hoặc lưu kết quả |
| Giả định kết quả đã sắp xếp | Provider không hứa thứ tự | Thu thập rồi sort nếu cần |
| Giả định sẽ duyệt đệ quy | Chỉ có entry trực tiếp | Tự quản lý tầng con hoặc dùng `walkFileTree` |
| Duyệt cây sâu với stream cha/con không đóng | Có thể mở quá nhiều handle | Nested try hoặc dùng API traversal thích hợp |
| Dùng `isDirectory` mặc định để đệ quy mọi entry | Có thể đi theo symbolic link và gặp chu trình | `NOFOLLOW_LINKS` cùng thiết kế traversal an toàn |
| Dùng glob `*.txt` rồi nghĩ chỉ nhận file thường | Thư mục tên `a.txt` cũng có thể khớp | Thêm `Files.isRegularFile` |
| Nghĩ glob là Java regex | Mẫu khớp sai | Phân biệt cú pháp glob và regex |
| Bắt mỗi `IOException` quanh vòng lặp | Bỏ sót lỗi iterator unchecked | Xử lý `DirectoryIteratorException` |
| Bắt `Exception` rồi trả list đã gom | Che thất bại, tạo kết quả chưa đầy đủ | Báo lỗi hoặc trả trạng thái partial rõ |
| Dùng `Files.list` mà không close | `Stream<Path>` giữ tài nguyên duyệt | `try-with-resources` |
| Tin rằng entry thấy được vẫn tồn tại sau đó | TOCTOU và filesystem đổi đồng thời | Bắt lỗi của thao tác thực |
| Gọi `normalize` và coi đã an toàn với symlink | Normalize không giải quyết filesystem thật | Kiểm soát quyền, liên kết, root và race |
| Dùng DirectoryStream để theo dõi mọi thay đổi | Đây là liệt kê, không phải event watcher | Xem `WatchService` |

## 21. Bài tập thực hành

**Bài 1 — Đếm con trực tiếp.** Tạo thư mục tạm chứa hai tệp và một thư mục con, trong thư mục con có một tệp khác. Nếu duyệt root bằng `DirectoryStream`, bao nhiêu entry được tính?

**Đáp án:** ba entry trực tiếp (hai tệp và thư mục con); tệp sâu hơn không được tính.

**Bài 2 — Glob và loại entry.** Một thư mục chứa `notes.txt` là file và `backup.txt` là **thư mục**. `newDirectoryStream(dir, "*.txt")` có thể trả gì?

**Đáp án:** có thể trả cả hai, vì glob kiểm tra **tên entry**. Muốn chỉ tệp thường, bổ sung `Files.isRegularFile` theo chính sách symbolic link mong muốn.

**Bài 3 — Tại sao phải dùng try-with-resources?** Nếu vòng lặp duyệt thất bại ở entry thứ 10, điều gì xảy ra với handle nếu đoạn code chỉ tự gọi `close()` ở cuối nhánh thành công?

**Đáp án:** có nguy cơ stream không được đóng. `try-with-resources` gọi close khi rời khối kể cả có ngoại lệ.

**Bài 4 — Lỗi mở hay lỗi duyệt?** Khi `Files.newDirectoryStream(dir)` không mở được vì thiếu quyền, có thể gặp ngoại lệ nào? Khi filter ném `IOException` trong lúc lấy phần tử thì sao?

**Đáp án:** lỗi mở thường là `IOException` hoặc lớp con thích hợp như `AccessDeniedException`. Lỗi lọc/duyệt có thể được bọc trong `DirectoryIteratorException` và `getCause()` cho biết IOException gốc.

**Bài 5 — Thứ tự.** Vì sao kết quả `for (Path entry : entries)` không nên dùng trực tiếp để tạo bảng báo cáo luôn phải sắp xếp tên?

**Đáp án:** `DirectoryStream` không bảo đảm thứ tự. Nếu cần thứ tự ổn định, gom dữ liệu và sort theo comparator được định nghĩa rõ.

**Bài 6 — Trách nhiệm đóng.** Một hàm nhận `DirectoryStream<Path>` do caller mở và chỉ đếm vài entry. Ai nên đóng?

**Đáp án:** phần sở hữu theo hợp đồng. Theo mặc định, caller mở và giữ quyền sở hữu thì caller đóng; hàm mượn stream không nên tự đóng mà không có thỏa thuận.

**Bài 7 — Đệ quy.** Vì sao gọi `visit(entry)` khi `Files.isDirectory(entry)` trả true có thể nguy hiểm?

**Đáp án:** có thể theo symbolic link tới bên ngoài hoặc tạo chu trình; đệ quy sâu làm tăng stack/handle. Cần chính sách link, giới hạn độ sâu và xử lý lỗi phù hợp, hoặc `walkFileTree`.

### Thực hành mở rộng

1. Thêm `"report-3.txt"` vào `DirectoryStreamFilterDemo` và bảo đảm kết quả được sắp xếp bất kể thứ tự provider trả về.
2. Thử thay glob `"*.txt"` bằng `"*.{txt,md}"` và tạo một tệp `README.md` để quan sát.
3. Viết một hàm `listNames(Path dir)` dùng `try-with-resources`, trả một `List<String>` đã sort và để lỗi I/O truyền lên, không trả danh sách nửa chừng.
4. Viết hàm duyệt hai tầng bằng nested `try-with-resources`, xác nhận stream con đóng trước khi xóa thư mục.
5. Tạo một `Filter<Path>` có thể ném `IOException`, bắt `DirectoryIteratorException` và in thông điệp cause. Không dựa vào lỗi hệ điều hành ngẫu nhiên.
6. So sánh `Files.list(dir).count()` với việc đếm qua `DirectoryStream` trên một thư mục không đổi, nhớ đóng cả hai nguồn.
7. Xây dựng chính sách đối với symbolic link: phân biệt khi nào muốn đi theo liên kết, khi nào phải bỏ qua nó, và kiểm thử trên filesystem có hỗ trợ liên kết.

## 22. Tổng kết

- **`Files.newDirectoryStream(dir)`** mở một cơ chế duyệt **các phần tử trực tiếp** của thư mục, trả về `DirectoryStream<Path>`.
- **`DirectoryStream` là tài nguyên `Closeable`**, có thể giữ handle thư mục. **Luôn đóng bằng `try-with-resources`** trong mã sở hữu stream; đừng để GC quyết định thời điểm đóng.
- **Một stream chỉ hỗ trợ tối đa một iterator**, không có thứ tự được bảo đảm, và không bảo đảm snapshot nhất quán khi thư mục thay đổi.
- **Glob** như `"*.txt"` lọc tên entry; **`Filter<Path>`** xử lý điều kiện tự định nghĩa, bao gồm metadata khi cần.
- **Lỗi mở/đóng** thường là `IOException`; **lỗi khi lặp** có thể là `DirectoryIteratorException` chứa cause `IOException`.
- **Đệ quy cần quản lý nhiều handle** và symbolic link cẩn thận; với cây sâu/hệ thống phức tạp, `Files.walkFileTree` thường phù hợp hơn.
- **`Files.list` và `Files.walk` cũng phải được đóng** vì chúng trả Stream<Path> gắn với tài nguyên filesystem.
- **Đường dẫn được liệt kê không phải khóa hay xác nhận tồn tại vĩnh viễn**; truy cập nội dung về sau vẫn cần xử lý ngoại lệ và điều kiện thay đổi đồng thời.

### Tài liệu API chính thức (Java SE 17)

- [DirectoryStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/DirectoryStream.html)
- [DirectoryStream.Filter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/DirectoryStream.Filter.html)
- [DirectoryIteratorException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/DirectoryIteratorException.html)
- [Files.newDirectoryStream và Files.list](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [Files.walkFileTree](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#walkFileTree(java.nio.file.Path,java.nio.file.FileVisitor))
- [SecureDirectoryStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/SecureDirectoryStream.html)
- [LinkOption](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/LinkOption.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="directory-tree-traversal-002">Files.list chỉ liệt kê con trực tiếp</a>

<details>
<summary>Xem chi tiết</summary>

# `Files.list` chỉ liệt kê con trực tiếp — duyệt thư mục một cấp trong Java

Trong Java NIO.2, `Files.list(Path)` là cách ngắn gọn để nhận một `Stream<Path>` của **các mục con trực tiếp trong một thư mục**. Nó không tự duyệt những mục nằm sâu trong thư mục con. Sự khác biệt này rất quan trọng: chương trình tìm một file bằng `Files.list(root)` có thể hoàn toàn không thấy file nằm ở `root/subdir/file.txt` dù file đó đang tồn tại.

Bài học thuộc phần **Duyệt cây thư mục** của Java Core I/O, tập trung chính xác vào **mức duyệt một cấp**: `Files.list` trả gì, không trả gì, làm thế nào lọc/sắp xếp/đếm, vì sao phải đóng stream, và khi nào chuyển sang `Files.walk`, `Files.find` hoặc `Files.walkFileTree`. Các ví dụ sử dụng Java 17+, tự tạo thư mục tạm, không yêu cầu dữ liệu có sẵn và đã được kiểm chứng bằng chương trình chạy thực tế.

## 1. Hiểu đúng “con trực tiếp” (direct children)

Xét cây thư mục:

```text
root/
├── alpha.txt
├── zeta.bin
└── docs/
    └── nested/
        └── deep.txt
```

Gọi:

```java
try (Stream<Path> entries = Files.list(root)) {
    entries.forEach(System.out::println);
}
```

Đây là **đoạn minh họa**: cần biến `Path root`, import `java.nio.file.Files` và `java.util.stream.Stream`, cùng xử lý `IOException`.

`Files.list(root)` trả các `Path` cho **ba mục con trực tiếp**:

- `root/alpha.txt` — file nằm ngay trong `root`.
- `root/zeta.bin` — file nằm ngay trong `root`.
- `root/docs` — **thư mục con** nằm ngay trong `root`.

Nó **không trả** `root/docs/nested`, cũng **không trả** `root/docs/nested/deep.txt`, vì hai mục này nằm ở độ sâu lớn hơn một cấp. `root` cũng **không nằm trong kết quả**, bởi `list` liệt kê **các mục trong thư mục**, không phải chính thư mục được truyền vào.

Một thư mục trống hợp lệ cho một stream không có phần tử. Điều đó khác với đường dẫn không tồn tại hoặc không thể truy cập, vốn có thể dẫn đến `IOException`.

**Quy tắc nhớ:** `Files.list(dir)` gần với thao tác “liệt kê nội dung của thư mục này”, không phải “tìm tất cả file phía dưới thư mục này”.

## 2. Cú pháp và kiểu trả về

API Java SE 17:

```java
public static Stream<Path> list(Path dir) throws IOException
```

Các điểm then chốt:

1. `dir` là `Path` tới **thư mục**. Tạo `Path.of("root")` không tự xác minh hay tạo thư mục.
2. Kết quả là **`Stream<Path>`** — mỗi phần tử là đường dẫn tới một mục con, **không** phải tên file dạng `String` hay nội dung file.
3. Các phần tử có thể đại diện cho **file thường, thư mục, symbolic link và loại entry khác**. `list` **không tự lọc** chỉ file thường.
4. `Files.list` không tự đảm bảo **thứ tự sắp xếp** của các entry. Kết quả phụ thuộc filesystem/provider; khi cần đầu ra ổn định, phải dùng `sorted(...)`.
5. Stream được tạo từ việc liệt kê thư mục có **tài nguyên hệ thống** phía dưới; **phải đóng** bằng `try-with-resources` hoặc một cách quản lý tương đương.
6. Việc lấy entry thường được tiến hành **lazily** khi tiêu thụ stream; khác với một snapshot bất biến toàn bộ cây tại thời điểm gọi.
7. Stream chỉ dùng **một lần** theo hợp đồng `Stream`. Muốn duyệt lại thư mục, tạo stream mới hoặc chủ động lưu kết quả vào collection nếu kích thước phù hợp.

`Files.list` có từ **Java 8**. NIO.2 `Path` và `Files` có từ Java 7, nhưng phương thức `list` trả stream là API Java 8+. Các ví dụ ở đây yêu cầu **Java 17+** do dùng `Path.of`, `Files.writeString` và `Stream.toList` ở những nơi phù hợp.

### `Path` không phải dữ liệu file

`Path path = Path.of("docs", "notes.txt");` chỉ mô tả tên/vị trí đường dẫn. Các thao tác như `path.getFileName()` hay `path.resolve("child")` phần lớn là thao tác **cú pháp**. `Files.isDirectory(path)` hoặc `Files.readString(path)` mới truy vấn/tác động đến filesystem và có thể thất bại do trạng thái hoặc quyền truy cập.

`Files.list` cho bạn **đường dẫn** để tiếp tục thao tác với `Files`; nó không tự đọc nội dung tất cả file, không giải mã charset và không mở từng file như `InputStream`.

## 3. Ví dụ 1 — chứng minh `Files.list` không đi sâu

Tạo file `ListOneLevelDemo.java`:

```java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public class ListOneLevelDemo {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("list-one-");
        try {
            Files.writeString(root.resolve("alpha.txt"), "A", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("zeta.bin"), "Z", StandardCharsets.UTF_8);
            Path nested = Files.createDirectories(root.resolve("docs").resolve("nested"));
            Files.writeString(nested.resolve("deep.txt"), "D", StandardCharsets.UTF_8);

            try (Stream<Path> entries = Files.list(root)) {
                entries.sorted(Comparator.comparing(
                        p -> p.getFileName().toString()))
                       .forEach(p -> System.out.println(
                               p.getFileName() + ":"
                                       + (Files.isDirectory(p) ? "directory" : "file")));
            }
            try (Stream<Path> entries = Files.list(root)) {
                System.out.println("direct children=" + entries.count());
            }
            try (Stream<Path> entries = Files.list(root)) {
                System.out.println("includes deep.txt=" + entries.anyMatch(
                        p -> p.getFileName().toString().equals("deep.txt")));
            }
        } finally {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); }
                    catch (IOException e) { throw new UncheckedIOException(e); }
                });
            }
        }
    }
}
```

Lệnh biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 ListOneLevelDemo.java
java ListOneLevelDemo
```

Đầu ra đã kiểm thử:

```text
alpha.txt:file
docs:directory
zeta.bin:file
direct children=3
includes deep.txt=false
```

**Phân tích:**

- Thư mục tạm được tạo tự động. `Files.writeString` tạo hai file ở root, `Files.createDirectories` tạo thư mục `docs/nested` và một file `deep.txt` nằm sâu bên dưới.
- `Files.list(root)` chỉ thấy `alpha.txt`, `docs` và `zeta.bin`. Không thấy `nested` hay `deep.txt`.
- `sorted(Comparator.comparing(...))` sắp theo tên entry trước khi in; nếu bỏ `sorted`, **không có cam kết** rằng các file được in theo thứ tự chữ cái.
- `Files.isDirectory(p)` dùng để phân loại mỗi entry **tại thời điểm kiểm tra**; nó có thể theo symbolic link theo mặc định, nên ví dụ chỉ dùng thư mục/file thường do chương trình vừa tạo.
- Lời gọi `entries.count()` đếm **các entry trực tiếp**, gồm cả thư mục. Kết quả là 3, **không phải tổng số file toàn cây**.
- `anyMatch` hỏi có entry **trực tiếp** tên `deep.txt` không. Giá trị `false` không chứng minh file đó không có trong các thư mục sâu hơn.
- Khối `finally` dùng `Files.walk` để dọn **cây thư mục tạm do chương trình sở hữu**. Đây chỉ là bước dọn dữ liệu ví dụ, **không có nghĩa `Files.list` tự thực hiện duyệt đệ quy**.

Chương trình có thể ném `IOException` nếu filesystem tạm không cho phép thao tác. Các dòng stdout trên được ghi nhận khi ví dụ chạy thành công trên Java 17 với filesystem bình thường.

## 4. Lấy tên hay lấy đường dẫn đầy đủ?

`Files.list(dir)` trả `Path` đến mỗi mục con, thường bao gồm tiền tố đường dẫn của `dir`. Nếu bạn chỉ cần **tên cuối**, dùng `getFileName()`:

```java
try (Stream<Path> entries = Files.list(directory)) {
    entries.map(p -> p.getFileName().toString())
           .sorted()
           .forEach(System.out::println);
}
```

Đây là **đoạn minh họa**, cần `directory` hợp lệ, import `Files`, `Path`, `Stream` và xử lý `IOException`. `getFileName()` trả phần cuối; file `root/docs` cho tên `docs`.

Nếu cần thao tác file sau đó, **giữ `Path`** thay vì chỉ giữ tên: tên `"report.txt"` không đủ thông tin nếu có nhiều thư mục. Dùng `Path.resolve` khi cần xây một đường dẫn khác, tránh nối chuỗi `"dir/" + fileName` theo hệ điều hành.

Một số thông tin metadata có thể truy xuất qua `Files.size(path)`, `Files.isRegularFile(path)`, `Files.getLastModifiedTime(path)`, v.v.; các thao tác này **không được `Files.list` thực hiện tự động** và có thể phát sinh I/O hoặc race.

## 5. Lọc entry trực tiếp bằng Stream API

Vì kết quả là `Stream<Path>`, bạn có thể dùng `filter`, `map`, `sorted`, `limit`, `count` hay `collect` như các stream Java khác.

Ví dụ lấy **file thường ở ngay root** có đuôi `.txt`:

```java
try (Stream<Path> entries = Files.list(root)) {
    List<Path> files = entries.filter(Files::isRegularFile)
            .filter(p -> p.getFileName().toString().endsWith(".txt"))
            .sorted(Comparator.comparing(p -> p.getFileName().toString()))
            .toList();
}
```

Đây là **mã minh họa**, cần import `Files`, `Path`, `List`, `Comparator`, `Stream`, biến `root` và xử lý `IOException`. `Files.isRegularFile` kiểm tra loại mục; `endsWith(".txt")` chỉ kiểm tra tên **theo chuỗi**, không đọc nội dung hay xác định MIME type thật.

### Phân biệt lọc và duyệt sâu

`filter(Files::isRegularFile)` **không làm stream đệ quy**. Nếu `deep.txt` nằm bên dưới `root/docs/nested`, bộ lọc chỉ xét ba entry trực tiếp mà `Files.list(root)` đã cung cấp; nó không tự vào `docs` để tìm file sâu.

Nếu ý định là tìm “tất cả file `.txt` ở mọi cấp”, bạn cần chuyển sang một API duyệt sâu hoặc tự triển khai thuật toán đi qua thư mục con. `Files.walk(root)` hay `Files.find(root, maxDepth, ...)` thích hợp hơn.

### `limit` không bảo đảm lấy “N file đầu tiên theo bảng chữ cái”

Nếu không có `sorted` và thứ tự list là không được xác định, `limit(10)` chỉ lấy tối đa 10 entry theo thứ tự mà traversal nhìn thấy, **không nhất thiết là 10 tên nhỏ nhất**. Nếu cần thứ tự chính xác, phải sắp xếp trước; lưu ý `sorted` cần xử lý các entry để quyết định thứ tự, có thể tốn bộ nhớ cho thư mục rất lớn.

Nếu muốn phân trang ổn định giữa các lần truy vấn trong thư mục bị thay đổi đồng thời, `Files.list` một mình không đủ để cung cấp snapshot hay thứ tự toàn cục cố định.

## 6. Ví dụ 2 — lọc file trực tiếp, so với `Files.walk`

Tạo file `ListVsWalkDemo.java`:

```java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class ListVsWalkDemo {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("list-walk-");
        try {
            Files.writeString(root.resolve("b.txt"), "B", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("a.txt"), "A", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("data.csv"), "1", StandardCharsets.UTF_8);
            Path folder = Files.createDirectories(root.resolve("sub"));
            Files.writeString(folder.resolve("inside.txt"), "I", StandardCharsets.UTF_8);

            try (Stream<Path> direct = Files.list(root)) {
                List<String> txtNames = direct.filter(Files::isRegularFile)
                        .map(p -> p.getFileName().toString())
                        .filter(name -> name.endsWith(".txt"))
                        .sorted().toList();
                System.out.println("direct .txt=" + txtNames);
            }
            try (Stream<Path> direct = Files.list(root)) {
                System.out.println("direct entries=" + direct.count());
            }
            try (Stream<Path> all = Files.walk(root)) {
                System.out.println("recursive regular files="
                        + all.filter(Files::isRegularFile).count());
            }
            try (Stream<Path> depthOne = Files.walk(root, 1)) {
                System.out.println("walk maxDepth1 entries=" + depthOne.count());
            }
        } finally {
            try (Stream<Path> all = Files.walk(root)) {
                all.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); }
                    catch (IOException e) { throw new UncheckedIOException(e); }
                });
            }
        }
    }
}
```

```bash
javac -encoding UTF-8 --release 17 ListVsWalkDemo.java
java ListVsWalkDemo
```

```text
direct .txt=[a.txt, b.txt]
direct entries=4
recursive regular files=4
walk maxDepth1 entries=5
```

**Tại sao có các con số này?**

- `Files.list(root)` cho bốn mục **trực tiếp**: `a.txt`, `b.txt`, `data.csv` và thư mục `sub`. Do vậy `direct entries=4`.
- Lọc `isRegularFile` và phần mở rộng `.txt` chỉ cho `a.txt`/`b.txt` ở root. `inside.txt` không có trong nguồn `Files.list(root)` nên không xuất hiện.
- `Files.walk(root)` theo mặc định duyệt xuống cây (không theo symbolic link nếu không yêu cầu), gồm **cả root** và các entry con. Khi lọc file thường, cả `inside.txt` nằm sâu cũng được đếm; tổng số file thường là 4.
- `Files.walk(root, 1)` có phạm vi **root + các mục sâu một cấp**. `root` được tính thêm nên kết quả là 5, khác `Files.list(root)` vốn không gồm `root`.

Đây là một khác biệt dễ quên khi so sánh số lượng:

```text
Files.list(root)            -> mục con trực tiếp, KHÔNG gồm root
Files.walk(root, 1)         -> root VÀ mục con trực tiếp
Files.walk(root)            -> root và mục nằm ở nhiều độ sâu
Files.walk(root, 0)         -> chỉ có root
```

Số phần tử của `Files.walk` có thể bao gồm cả thư mục; muốn đếm số **file thường** phải lọc với `Files.isRegularFile` hoặc dựa vào `BasicFileAttributes` thích hợp.

## 7. Mỗi `Files.list` stream chỉ dùng một lần

Java Stream không phải `Collection` có thể tự động lặp lại tùy thích. Sau một terminal operation như `forEach`, `count` hoặc `toList`, stream được coi là đã tiêu thụ; gọi terminal operation thứ hai trên cùng stream thường ném `IllegalStateException`.

Ví dụ **sai**:

```java
// Minh họa lỗi: không dùng lại Stream sau terminal operation.
try (Stream<Path> entries = Files.list(dir)) {
    long count = entries.count();
    entries.forEach(System.out::println); // Sai: stream đã tiêu thụ.
}
```

Cách đúng khi cần duyệt nhiều lần:

- **Mở stream mới** với `Files.list(dir)` mỗi lượt và đóng mỗi stream.
- Hoặc nếu thư mục đủ nhỏ, **collect** vào `List<Path>` một lần trong `try-with-resources`, rồi làm việc với danh sách. Danh sách chỉ chứa các `Path` được liệt kê, không bảo đảm mỗi file còn tồn tại ở những thời điểm sau.

Ví dụ thu thập:

```java
List<Path> snapshotOfNames;
try (Stream<Path> entries = Files.list(dir)) {
    snapshotOfNames = entries.sorted().toList();
}
System.out.println("Entries recorded=" + snapshotOfNames.size());
```

Đây là **mã minh họa**. `toList()` là phương thức Java 16+; kết quả không phải một snapshot bất biến của **nội dung file**, chỉ là danh sách các đường dẫn nhìn thấy trong lúc traversal. File có thể thay đổi hoặc biến mất ngay sau đó.

Lưu ý `sorted()` ở đây sử dụng thứ tự so sánh của `Path`, chịu quy tắc provider. Nếu muốn sắp **theo tên file** thay vì theo toàn bộ đường dẫn, chọn comparator trên `getFileName()`.

## 8. Vì sao bắt buộc đóng stream?

`Files.list` mở một nguồn duyệt entry gắn với tài nguyên filesystem, ví dụ directory stream/handle. Tài liệu JDK quy định stream được trả về **chứa tham chiếu tới một directory mở**. Nếu không đóng, ứng dụng có thể làm rò rỉ file descriptor/handle hoặc giữ tài nguyên lâu hơn cần thiết.

Mẫu đúng:

```java
try (Stream<Path> entries = Files.list(dir)) {
    entries.forEach(this::processEntry);
}
```

Đây chỉ là **minh họa bên trong một phương thức instance** có `processEntry(Path)` và biến `dir`; không phải chương trình đầy đủ. Cần import các kiểu và xử lý `IOException`.

**Các hiểu nhầm:**

- `entries.count()` chạy xong **không tự đóng** stream theo contract.
- `entries.forEach(...)` kết thúc **không tự đóng** stream.
- `entries.toList()` hoàn tất **không tự đóng** stream.
- Gán kết quả `Files.list(dir)` cho một biến static rồi dùng dần trong suốt tuổi thọ service thường khiến khó quản lý vòng đời và dễ rò rỉ.
- `try-with-resources` đóng stream cả khi thao tác terminal throw exception.

Nếu thư viện/API nhận stream và xử lý bên ngoài phạm vi method, hãy xác định rõ **ai sở hữu trách nhiệm đóng**. Một lựa chọn gọn hơn là xử lý dữ liệu **ngay trong** khối `try`, hoặc collect những giá trị nhỏ không cần giữ file descriptor sau khi khối kết thúc.

### Phạm vi lazy traversal và closure

Các thao tác trung gian `filter`, `map`, `sorted` thường chỉ tạo pipeline; thao tác terminal như `count`, `forEach`, `toList` mới yêu cầu traversal. Directory có thể thay đổi ngay cả trong lúc traversal; không có nghĩa `Files.list` tạo ảnh chụp trạng thái cố định.

Hãy đặt việc tạo stream và terminal operation trong cùng `try-with-resources`, thay vì tạo stream trong một method rồi trả về caller không biết nghĩa vụ đóng.

## 9. Lỗi khi mở hoặc duyệt thư mục

`Files.list(dir)` khai báo `throws IOException`. Các tình huống có thể xảy ra:

- `dir` không tồn tại: với provider mặc định thường là `NoSuchFileException`.
- `dir` là một file thường thay vì thư mục: có thể là `NotDirectoryException` hoặc một `IOException` phù hợp với hệ thống.
- Không được phép mở/đọc thư mục: có thể là `AccessDeniedException` hoặc `IOException` khác.
- Directory thay đổi hoặc thiết bị gặp lỗi khi **đang duyệt**: có thể xuất hiện ngoại lệ I/O sau lúc tạo stream.

Các subtype không được bảo đảm giống hệt trên mọi hệ điều hành/provider. Nên xử lý `IOException` có ngữ cảnh, không chuyển mọi tình huống thành “thư mục trống”.

### Phân biệt exception ở bước mở và bước duyệt

Khi thất bại ngay ở `Files.list(dir)`, bạn có thể nhận `IOException`. Nếu lỗi I/O xảy ra **sau khi stream đã được tạo** trong lúc duyệt entry, nó có thể được bọc trong **`UncheckedIOException`**, vì cơ chế `Stream` không khai báo checked exception ở mọi bước traversal.

Mẫu xử lý minh họa:

```java
try (Stream<Path> entries = Files.list(dir)) {
    entries.forEach(System.out::println);
} catch (UncheckedIOException e) {
    // Lỗi I/O trong lúc duyệt stream; xem e.getCause().
} catch (IOException e) {
    // Lỗi khi mở directory hoặc đóng tài nguyên.
}
```

Đây là **mã minh họa**, cần import `Files`, `Path`, `Stream`, `IOException`, `UncheckedIOException` và biến `dir`. Các exception phát sinh trong callback `forEach` cũng cần được nhận diện theo ý nghĩa của chúng.

Cần tránh code:

```java
try (Stream<Path> entries = Files.list(dir)) {
    // ...
} catch (IOException e) {
    return List.of(); // Không phân biệt directory trống và lỗi I/O!
}
```

Đoạn trên chỉ minh họa anti-pattern, giả định một phương thức trả `List<Path>`. Nếu trả danh sách trống khi lỗi, hệ thống kiểm toán/tìm file có thể báo “không có dữ liệu” thay vì báo “đã không thể kiểm tra dữ liệu”.

## 10. Ví dụ 3 — directory không tồn tại, lỗi mở và vòng đời stream

Tạo file `ListErrorsDemo.java`:

```java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public class ListErrorsDemo {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("list-error-");
        try {
            Path file = root.resolve("single.txt");
            Files.writeString(file, "x", StandardCharsets.UTF_8);
            Path missing = root.resolve("missing");
            try (Stream<Path> ignored = Files.list(missing)) {
                System.out.println("unexpected");
            } catch (NoSuchFileException e) {
                System.out.println("missing directory=" + e.getClass().getSimpleName());
            }
            try (Stream<Path> one = Files.list(root)) {
                System.out.println("listed file=" + one.findFirst()
                        .orElseThrow().getFileName());
            }
            System.out.println("still open for new traversal="
                    + Files.isDirectory(root));
        } finally {
            try (Stream<Path> all = Files.walk(root)) {
                all.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); }
                    catch (IOException e) { throw new UncheckedIOException(e); }
                });
            }
        }
    }
}
```

```bash
javac -encoding UTF-8 --release 17 ListErrorsDemo.java
java ListErrorsDemo
```

```text
missing directory=NoSuchFileException
listed file=single.txt
still open for new traversal=true
```

Diễn giải:

- `missing` chưa được tạo nên `Files.list(missing)` trên filesystem thông thường báo `NoSuchFileException`.
- `root` vẫn tồn tại với một file con. `Files.list(root)` cung cấp entry `single.txt`.
- `findFirst()` yêu cầu một phần tử đầu tiên từ stream **chưa xác định thứ tự**. Trong ví dụ chỉ có **một mục**, nên đầu ra ổn định. Nếu directory có nhiều mục, không được suy ra `findFirst` là tên nhỏ nhất theo alphabet.
- Stream đầu được tạo trong một `try-with-resources`. Nếu `Files.list(missing)` ném lỗi trước khi tạo stream, không có stream nào được gán cho tài nguyên đó; control chuyển sang `catch`. Với stream thành công, Java đóng khi rời khối.
- Sau đó ví dụ tiếp tục đọc directory bình thường. File ví dụ được dọn trong `finally` vì chương trình sở hữu duy nhất thư mục tạm này.

**Không kết luận** `Files.list` “đã đóng mới khiến directory vẫn tồn tại”. Đóng stream giải phóng handle, **không xóa** thư mục. Việc `Files.isDirectory(root)` trả `true` chỉ kiểm tra directory hiện còn tồn tại tại thời điểm gọi.

## 11. So sánh `Files.list`, `Files.walk`, `Files.find` và `walkFileTree`

| API | Phạm vi | Kết quả/hành vi | Lựa chọn phù hợp |
|---|---|---|---|
| `Files.list(dir)` | **Một cấp**, con trực tiếp | `Stream<Path>`, không gồm `dir` | Liệt kê file/thư mục ngay dưới một folder |
| `Files.walk(start)` | Đệ quy từ `start` (theo điều kiện depth/options) | `Stream<Path>` **gồm `start`** | Duyệt các mức, lọc đường dẫn |
| `Files.walk(start, maxDepth)` | Đệ quy giới hạn độ sâu | `Stream<Path>` gồm `start` | Giới hạn chi phí/quy mô duyệt |
| `Files.find(start, maxDepth, matcher, options...)` | Đệ quy giới hạn độ sâu | `Stream<Path>`, matcher nhận `Path` và `BasicFileAttributes` | Tìm theo đường dẫn cùng metadata |
| `Files.walkFileTree(start, visitor)` | Duyệt cây với callback | Gọi visitor vào/trước/sau directory, thông báo lỗi | Công cụ copy/delete/scan tree có kiểm soát |
| `Files.newDirectoryStream(dir)` | Một cấp, con trực tiếp | `DirectoryStream<Path>`, có thể lọc entry | Duyệt one-level với iterator và xử lý provider |

### Khi chỉ cần depth 1

`Files.list(root)` gần với `Files.walk(root, 1)` về phạm vi các **con trực tiếp**, nhưng hai API **không cùng tập kết quả**:

- `Files.list(root)` trả chỉ các entry con.
- `Files.walk(root, 1)` trả **root trước và các entry sâu tối đa một cấp**, theo quy tắc traversal của `walk`.

Nếu đang so sánh hoặc chuyển từ `list` sang `walk`, nhớ loại root ra nếu business logic chỉ muốn con trực tiếp.

### Khi muốn đệ quy

Đừng viết:

```java
Files.list(root).filter(p -> p.getFileName().toString().equals("deep.txt"))
```

rồi kết luận `deep.txt` không có ở đâu dưới `root`. File có thể nằm trong `root/docs/nested`.

Nếu yêu cầu thực sự là tìm mọi file ở các mức, ví dụ đơn giản:

```java
try (Stream<Path> entries = Files.walk(root)) {
    entries.filter(Files::isRegularFile)
           .filter(p -> p.getFileName().toString().endsWith(".txt"))
           .forEach(System.out::println);
}
```

Đây là **đoạn minh họa**, không phải chương trình độc lập; cần `root`, import và xử lý `IOException`. `Files.walk` đệ quy mặc định theo độ sâu hợp lệ của thư mục, **không tự theo symbolic link** nếu không truyền `FOLLOW_LINKS`. Cần tránh duyệt vô hạn hoặc tốn tài nguyên trên cây rất lớn; khi chỉ cần con trực tiếp, `Files.list` đơn giản hơn.

### Khi cần xử lý trạng thái của từng directory

`walkFileTree` phù hợp hơn nếu ứng dụng muốn biết lúc nào **vào** một thư mục, **rời** một thư mục, xử lý lỗi truy cập theo từng nhánh, hoặc thực hiện thao tác như xóa cây mà không chỉ dựa vào các đường dẫn. `Files.list` không phải một visitor cho cả cây.

## 12. `newDirectoryStream`: lựa chọn duyệt một cấp khác

`Files.newDirectoryStream(dir)` trả `DirectoryStream<Path>` với các entry trực tiếp. Nó cũng **phải được đóng**:

```java
try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
    for (Path entry : ds) {
        System.out.println(entry.getFileName());
    }
}
```

Đây là **mã minh họa**, cần import `java.nio.file.DirectoryStream`, `Files`, `Path`, biến `dir` và xử lý `IOException`. API này hữu ích khi muốn kiểu iterator hoặc bộ lọc ở lúc lấy directory entry:

```java
try (DirectoryStream<Path> ds =
             Files.newDirectoryStream(dir, "*.txt")) {
    for (Path entry : ds) {
        System.out.println(entry.getFileName());
    }
}
```

Đây là **đoạn minh họa**; mẫu glob `"*.txt"` áp dụng **tên entry trực tiếp**, không làm đệ quy. Trong một số tình huống, `DirectoryStream.Filter<? super Path>` cho phép lọc theo điều kiện tùy chỉnh. Dù dùng `newDirectoryStream` hay `Files.list`, cả hai đều không tự đi qua mọi cây.

Khi đã xử lý data theo phong cách `Stream`, `Files.list` là lựa chọn gọn. Khi muốn vòng lặp directory entry trực tiếp, `newDirectoryStream` cũng phù hợp. Không có lý do phải buộc mọi đoạn code chuyển qua Stream khi API iterator giải quyết tốt hơn.

## 13. Filesystem, symbolic link và loại entry

### `Files.list` không có nghĩa chỉ “regular files”

`Files.list(dir)` trả directory entries. Trong thư mục có thể tồn tại:

- File thường.
- Thư mục con.
- Symbolic link.
- Các loại entry đặc biệt khác tùy filesystem.

`Files.isRegularFile(p)` dùng để kiểm tra một `Path` có đang trỏ tới file thường hay không. Mặc định nhiều phép kiểm tra theo đường dẫn **theo symbolic link** đến đích nếu có; nếu muốn kiểm tra chính link, truyền `LinkOption.NOFOLLOW_LINKS` tới những API hỗ trợ.

Ví dụ các kiểm tra (mã minh họa):

```java
boolean isRegular = Files.isRegularFile(entry);
boolean isDirectory = Files.isDirectory(entry);
boolean isLink = Files.isSymbolicLink(entry);
```

Cần có `Path entry`, import `Files`; mỗi kết quả là thông tin tại thời điểm kiểm tra và có thể thay đổi ngay sau đó.

### `Files.list` có tự theo symlink để đệ quy không?

**Không có bước đệ quy ngay từ đầu.** Nếu một entry là symlink tới một thư mục khác, `Files.list(root)` chỉ cung cấp **entry symlink trực tiếp** đó như một `Path`, không tự mở cây thư mục đích để thêm các entry cháu vào cùng stream.

Nếu bạn **truyền một `Path` trỏ qua symlink tới directory** làm chính tham số `dir`, cách provider resolve đường dẫn có thể cho phép liệt kê directory đích. Đó là câu chuyện khác với việc tự đi sâu từ một entry của root.

Với `Files.walk`, theo link hay không là quyết định option như `FOLLOW_LINKS`; khi theo link trên cây có vòng lặp, traversal có thể báo `FileSystemLoopException`. Vì vậy code thao tác sâu cần hiểu symlink trước khi bật follow links.

### “Hidden file” vẫn là một entry

`Files.list` không tự bỏ file ẩn, file có tên bắt đầu bằng dấu chấm hay entry khác chỉ vì giao diện file manager thường giấu chúng. Nếu muốn bỏ các entry được coi là hidden, có thể cân nhắc `Files.isHidden(path)` nhưng phương thức này có semantics tùy filesystem và có thể ném `IOException`. Không giả định “file bắt đầu bằng dấu `.`” là định nghĩa file ẩn chung trên mọi OS.

## 14. Những giới hạn của `Files.list` cần biết

### 14.1 Không có thứ tự mặc định

Nếu ứng dụng xây bảng UI hoặc xuất báo cáo đòi hỏi ổn định, phải quyết định sort theo **tên**, thời gian sửa, kích thước hoặc tiêu chí khác. Đừng dùng thứ tự ngẫu nhiên của hệ thống file để làm thứ tự dữ liệu nghiệp vụ.

### 14.2 Không tạo snapshot bất biến

Thư mục có thể bị thay đổi bởi thread/process khác khi stream còn mở. Các thao tác sửa đổi có thể có hoặc không được phản ánh trong các entry đã liệt kê; `Files.list` là phép duyệt **weakly consistent** theo tài liệu, không cung cấp snapshot nguyên tử.

Nếu cần bảo đảm “danh sách tại cùng một thời điểm”, phải dùng cơ chế snapshot, phối hợp với producer hoặc chiến lược phiên bản hóa/đồng bộ phù hợp. `toList()` chỉ lưu lại các `Path` đã thấy; không khóa file hoặc giữ metadata bất biến.

### 14.3 Không tự truy cập metadata để lọc

`Files.list` trả `Path`; một thao tác `Files.isRegularFile`, `Files.size` hoặc `Files.getLastModifiedTime` có thể thực hiện thêm truy vấn filesystem, và trạng thái có thể đổi giữa hai lần gọi. Nếu cần duyệt sâu và dùng metadata của từng mục, `Files.find` có predicate nhận `BasicFileAttributes` giúp diễn đạt đúng công việc.

### 14.4 Không tự khắc phục lỗi quyền truy cập

Có quyền thấy tên file chưa chắc có quyền mở/đọc nội dung file đó. Ngược lại, `Files.list` có thể thất bại khi không thể đọc directory dù một `Path` cụ thể ở nơi khác vẫn sử dụng được theo cách khác. Thiết kế phải phân biệt **liệt kê**, **đọc file**, **ghi file** và **duyệt thư mục sâu hơn**.

### 14.5 Duyệt directory rất lớn có chi phí

Tạo stream không có nghĩa giữ sẵn toàn bộ kết quả trong RAM. Tuy nhiên, `sorted()`, `toList()` hoặc `collect` có thể giữ nhiều entry trong bộ nhớ. Với thư mục có hàng triệu mục, cân nhắc giới hạn xử lý, batch, streaming, độ sâu, timeout và thiết kế storage/index thay vì vô thức collect toàn bộ.

### 14.6 Duyệt song song không phải mặc định tốt hơn

`entries.parallel()` không tự biến filesystem thành nguồn dữ liệu hỗ trợ parallel traversal hiệu quả. Các phép metadata I/O và ordering có thể làm kết quả không nhanh hơn hoặc khó kiểm soát tài nguyên. Ưu tiên cách tuần tự dễ quan sát cho bài toán liệt kê thông thường; chỉ tăng song song khi có yêu cầu và đo lường thực tế.

## 15. Khi nào dùng `Files.list` trong ứng dụng?

### Tình huống A — hiển thị nội dung ngay trong một folder

Ứng dụng quản lý file cần liệt kê các item dưới đường dẫn hiện tại: `Files.list(currentDirectory)` là mô hình đúng. Mỗi entry có thể được hiển thị thành tên, loại, kích thước và thao tác mở thư mục con khi người dùng bấm. **Chỉ khi người dùng chuyển vào thư mục con**, ứng dụng mới cần list thư mục con đó.

### Tình huống B — xử lý tất cả file import nằm ngay trong inbox

Một dịch vụ nhập file từ thư mục `inbox` có quy tắc: chỉ xử lý file nằm **ngay ở inbox**, không đi vào `inbox/archived` hoặc `inbox/failed`. `Files.list(inbox).filter(Files::isRegularFile)` phù hợp vì nó không vô tình quét thư mục con.

Dù vậy, nếu file đang được copy vào inbox, có thể thấy file còn dở hoặc entry thay đổi giữa khi list và khi đọc. Cần quy tắc publish file hoàn chỉnh (ví dụ tạo temp rồi rename/move theo contract) thay vì xem list là xác nhận file đã sẵn sàng.

### Tình huống C — tìm một file đúng tên ở cấp hiện tại

Nếu biết tên file, thường **không cần list**:

```java
Path candidate = dir.resolve("config.json");
if (Files.isRegularFile(candidate)) {
    // Làm việc với candidate và xử lý lỗi khi thực sự mở nó.
}
```

Đây là **mã minh họa**, cần biến `dir`, import các lớp và xử lý I/O. Việc dựng đúng đường dẫn rồi mở có thể đơn giản, hiệu quả hơn quét toàn bộ directory để tìm tên cố định.

Nếu phải tìm “file `config.json` ở bất kỳ thư mục con nào”, mới cân nhắc `Files.walk`/`Files.find` hoặc một chỉ mục file phù hợp.

### Tình huống D — báo cáo một cấp

Với yêu cầu thống kê **bao nhiêu entry con** hoặc **bao nhiêu file thường ở cấp hiện tại**, `Files.list` vừa khớp yêu cầu. Nếu chỉ cần đếm file thường:

```java
try (Stream<Path> entries = Files.list(dir)) {
    long directFiles = entries.filter(Files::isRegularFile).count();
}
```

Đây là **mã minh họa**, không phải chương trình độc lập. Kết quả không bao gồm file nằm ở sâu hơn. Cũng không có bảo đảm rằng filesystem không đổi trong suốt quá trình đếm.

## 16. Không dùng kết quả liệt kê như kiểm tra bảo mật đủ mạnh

`Path` từ `Files.list` có thể được sử dụng trong ứng dụng xóa, di chuyển, upload hoặc phân quyền. Nhưng **chỉ thấy một entry dưới root** không đảm bảo mọi thao tác tiếp theo đều an toàn:

- Symbolic link có thể dẫn tới nơi khác.
- File có thể bị thay đổi hoặc thay thế sau khi liệt kê (**TOCTOU**, time-of-check-to-time-of-use).
- Một entry được liệt kê có thể biến mất trước khi mở.
- Quyền đọc directory khác với quyền đọc nội dung file hoặc quyền ghi.
- Nếu ứng dụng tự thêm một tên do người dùng cung cấp vào đường dẫn, cần xử lý `..`, absolute path, root boundary và symlink theo threat model.

`normalize()` chỉ xử lý đường dẫn theo cú pháp, **không** khóa filesystem và không tự resolve mọi symlink. `Path.startsWith(root)` tốt hơn kiểm tra tiền tố chuỗi, nhưng vẫn chưa đủ chống symlink/race khi dữ liệu đầu vào không tin cậy.

Với chương trình quản lý file nhạy cảm, hãy thực hiện operation cần thiết và xử lý lỗi; cân nhắc directory-handle-based API như `SecureDirectoryStream` khi provider hỗ trợ, cùng quyền filesystem và chiến lược phòng TOCTOU phù hợp.

## 17. Tự kiểm tra và lỗi thường gặp

**Lỗi 1 — Tưởng `Files.list` đệ quy.** Chỉ có con trực tiếp; file sâu hơn không thuộc stream.

**Lỗi 2 — Quên rằng kết quả còn chứa thư mục.** Nếu đếm file thường, cần `filter(Files::isRegularFile)`.

**Lỗi 3 — Quên đóng stream.** Terminal operation không tự đảm bảo đóng directory handle; dùng `try-with-resources`.

**Lỗi 4 — Nghĩ danh sách đã sắp xếp.** Thứ tự không được cam kết; dùng comparator phù hợp.

**Lỗi 5 — Dùng lại stream sau `count()` hoặc `forEach()`.** Stream không thể tiêu thụ lại; mở stream mới hoặc collect khi quy mô cho phép.

**Lỗi 6 — Nhầm `Files.walk(dir, 1)` với `Files.list(dir)` ở số lượng.** `walk` còn bao gồm chính `dir`.

**Lỗi 7 — Dùng `.getFileName()` nhưng mong có đường dẫn file đầy đủ.** Chỉ giữ tên cuối, có thể mất ngữ cảnh directory.

**Lỗi 8 — Trả danh sách rỗng khi không có quyền duyệt.** “Thư mục trống” và “đọc thất bại” cần tách thành hai trạng thái.

**Lỗi 9 — Không xử lý `UncheckedIOException`.** Có thể phát sinh trong lúc stream traversal sau khi đã mở thành công.

**Lỗi 10 — Dùng `Files.isRegularFile` như bảo đảm file không thay đổi.** File có thể bị xóa/chuyển/đổi quyền sau kiểm tra.

**Lỗi 11 — Tự động theo link để duyệt sâu mà không tính vòng lặp.** `Files.list` không đệ quy; khi dùng `walk` với `FOLLOW_LINKS` phải có chiến lược xử lý link/loop.

**Lỗi 12 — Thu thập mọi entry của directory cực lớn rồi sort/collect.** Có thể tốn RAM và thời gian; chọn xử lý streaming hay indexing tùy mục tiêu.

**Lỗi 13 — Tưởng dấu `.txt` chứng minh nội dung text.** Phần mở rộng chỉ là tên, không bảo đảm charset/MIME/content hợp lệ.

**Lỗi 14 — Tưởng gọi `Files.list` đã đọc nội dung file.** Nó chỉ cấp đường dẫn; muốn đọc file cần API khác.

**Lỗi 15 — Dùng `limit(N)` trước sort rồi muốn “N file đầu alphabet”.** Phải xét thứ tự thao tác và yêu cầu đầu ra; list không có ordering mặc định.

**Lỗi 16 — Phụ thuộc vào filesystem không đổi.** `Files.list` không cung cấp snapshot nguyên tử và có thể phản ánh thay đổi trong khi duyệt.

## 18. Bảng quyết định nhanh

| Mục tiêu | API nên chọn | Lưu ý |
|---|---|---|
| Liệt kê các entry ở ngay một thư mục | `Files.list(dir)` | Một cấp, không gồm `dir`; nhớ đóng |
| Lấy tên file ở cấp hiện tại | `list(dir).map(p -> p.getFileName())` | Không tự sắp xếp |
| Đếm file thường trực tiếp | `list(dir).filter(Files::isRegularFile).count()` | Không tính file trong thư mục con |
| Lọc tên file bằng mẫu glob ở cấp hiện tại | `Files.newDirectoryStream(dir, "*.txt")` | Không đệ quy; phải đóng |
| Tìm file ở mọi cấp | `Files.walk(dir)` | Gồm root, có thể tốn tài nguyên |
| Chỉ duyệt đến độ sâu 1 tính từ root | `Files.walk(dir, 1)` | Gồm root; khác `list` |
| Tìm theo tên + metadata ở cây | `Files.find(dir, depth, predicate)` | Có `BasicFileAttributes` |
| Xử lý copy/delete tree có điều kiện | `Files.walkFileTree` | Visitor/error handling |
| Tìm file **đã biết đường dẫn chính xác** | `dir.resolve(name)` rồi open/check | Không cần list toàn bộ |
| Tạo giao diện browser file một cấp | `Files.list(currentDir)` | Hiển thị folder như entry, vào sâu khi yêu cầu |

## 19. Câu hỏi tự luyện

**Câu 1.** Thư mục `root` chứa `a.txt`, `docs`; `docs` chứa `b.txt`. `Files.list(root)` có trả `b.txt` không?  
**Đáp án:** Không. Chỉ `a.txt` và `docs` là con trực tiếp.

**Câu 2.** Kết quả `Files.list(root).count()` có phải tổng file trong cả cây không?  
**Đáp án:** Không. Nó đếm các entry ở một cấp; bao gồm thư mục, symlink hay entry khác nếu có.

**Câu 3.** Có thể dùng `Files.list(root).filter(Files::isRegularFile)` để tìm file thường trong `root/docs` không?  
**Đáp án:** Không; filter không làm đệ quy. Cần một API duyệt sâu hoặc mở riêng `docs`.

**Câu 4.** Vì sao `Files.walk(root, 1).count()` có thể lớn hơn `Files.list(root).count()` đúng 1?  
**Đáp án:** `walk(root, 1)` còn cung cấp chính `root`, trong khi `list` chỉ cung cấp các con trực tiếp.

**Câu 5.** Một stream `Files.list` đã gọi `count()`, có gọi tiếp `forEach()` được không?  
**Đáp án:** Không. Cần stream mới hoặc thu thập dữ liệu khi dùng lần đầu.

**Câu 6.** Sau `Files.list(dir).toList()`, directory handle có tự đóng không?  
**Đáp án:** Không có bảo đảm tự đóng chỉ nhờ terminal operation; hãy tạo stream trong `try-with-resources`.

**Câu 7.** `Files.list` luôn trả tên theo alphabet đúng không?  
**Đáp án:** Không; sắp xếp khi có yêu cầu.

**Câu 8.** `Files.list` thất bại vì không có quyền đọc directory, có nên báo “directory trống” không?  
**Đáp án:** Không; phải báo/ghi nhận lỗi truy cập riêng.

**Câu 9.** Một entry là symbolic link đến thư mục có nhiều file. `Files.list(root)` có tự thêm toàn bộ file của đích link vào kết quả không?  
**Đáp án:** Không; nó chỉ liệt kê entry link ở mức trực tiếp. Duyệt tiếp là quyết định riêng.

**Câu 10.** Vì sao gọi `Files.isRegularFile(p)` ngay sau khi list không đảm bảo `Files.readString(p)` sẽ thành công?  
**Đáp án:** Quyền và trạng thái filesystem có thể thay đổi giữa kiểm tra và mở; file còn có thể bị thay thế hoặc bị xóa.

## Kết luận

**`Files.list(dir)` chỉ liệt kê các mục con trực tiếp của `dir` — không tự đi vào thư mục con và không bao gồm chính `dir`.** Nó trả `Stream<Path>` để bạn lọc, ánh xạ, đếm hoặc sắp xếp, nhưng **không bảo đảm thứ tự**, **không tự lọc file thường** và **phải được đóng** vì giữ tài nguyên directory.

Khi nhiệm vụ chỉ yêu cầu một cấp, `Files.list` là công cụ đúng và đơn giản. Khi nhiệm vụ yêu cầu nhiều cấp, dùng `Files.walk`, `Files.find` hoặc `Files.walkFileTree` theo mức độ kiểm soát cần thiết. Trong mọi trường hợp, cần xử lý ngoại lệ I/O, symbolic link, vòng đời stream và trạng thái filesystem có thể thay đổi.

### Tài liệu Java SE 17

- [`Files.list(Path)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#list(java.nio.file.Path))
- [`Files.walk`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [`Files.find` và `walkFileTree`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [`DirectoryStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/DirectoryStream.html)
- [`Path`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html)
- [`Stream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html)
- [`UncheckedIOException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/UncheckedIOException.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="directory-tree-traversal-003">Files.walk và độ sâu tối đa</a>

<details>
<summary>Xem chi tiết</summary>

# `Files.walk` và độ sâu tối đa khi duyệt cây thư mục trong Java

Trong ứng dụng thực tế, đôi khi bạn cần tìm các tệp trong một thư mục, nhưng **chỉ đi sâu đến một số cấp nhất định**: duyệt thư mục hiện tại, thư mục con trực tiếp, hoặc thêm một vài tầng nữa. Java NIO.2 cung cấp `Files.walk(...)` để duyệt cây và tham số `maxDepth` để **giới hạn việc đi xuống các thư mục con**.

Ví dụ, nếu một thư mục chứa nhiều nhánh sâu hàng trăm cấp, `Files.walk(root, 2)` chỉ duyệt đến các đối tượng có khoảng cách tối đa **hai cạnh** so với `root`. Điều này khác với duyệt toàn bộ cây rồi mới lọc kết quả theo độ sâu: tham số `maxDepth` cho phép **không cần đi xuống** các nhánh vượt mức cho phép.

Bài này tập trung vào cách hiểu độ sâu, các overload của `Files.walk`, vòng đời `Stream<Path>`, cách lọc tệp/thư mục, symbolic link, lỗi I/O và một ví dụ Java 17 có kết quả kiểm chứng được.

## 1. `Files.walk` trả về gì?

Hai chữ ký quan trọng của `java.nio.file.Files`:

~~~java
Stream<Path> paths = Files.walk(start);
Stream<Path> limited = Files.walk(start, maxDepth);
~~~

Có overload tổng quát thêm tùy chọn duyệt:

~~~java
Stream<Path> paths = Files.walk(
        start, maxDepth, FileVisitOption.FOLLOW_LINKS);
~~~

Trong đó:

- `start`: đối tượng `Path` chỉ điểm bắt đầu; thông thường là một thư mục.
- `maxDepth`: số nguyên `int` không âm, giới hạn mức sâu lớn nhất được đưa vào kết quả.
- `FileVisitOption...`: tùy chọn như `FOLLOW_LINKS` để thay đổi cách xử lý symbolic link.
- **Giá trị trả về:** `Stream<Path>` duyệt các đường dẫn phát hiện được, trong đó **`start` cũng là một phần tử**.
- `Files.walk(start)` tương đương đi sâu tối đa theo giá trị không giới hạn thực tế của `Integer.MAX_VALUE`, không phải chỉ một cấp.

**Điều kiện đặc biệt:** `maxDepth < 0` là tham số không hợp lệ và có thể gây `IllegalArgumentException`; đừng dùng số âm để biểu diễn “không giới hạn”. Muốn không đặt giới hạn thực tế, gọi overload `Files.walk(start)`.

`Files.walk` sử dụng cách đi qua cây **depth-first**: bắt đầu từ `start` rồi duyệt các mục bên dưới trước khi chuyển sang nhánh khác. **Thứ tự các mục cùng thư mục không được bảo đảm** bởi API, nên đừng mong tệp được trả về tự động theo tên tăng dần.

Kết quả là **luồng xử lý lười (lazy stream)**: không nên hiểu lời gọi `Files.walk` đã đọc toàn bộ cây vào bộ nhớ. Các thao tác terminal như `forEach`, `count` và `toList` mới kích hoạt việc tiêu thụ stream.

## 2. Hiểu chính xác `maxDepth`: cấp 0 là điểm bắt đầu

Xét cây ví dụ:

~~~text
root/                       (depth 0)
├── root.txt                (depth 1)
├── docs/                   (depth 1)
│   ├── readme.txt          (depth 2)
│   └── archive/            (depth 2)
│       └── old.txt         (depth 3)
└── images/                 (depth 1)
    └── logo.bin            (depth 2)
~~~

`maxDepth` đếm **số cạnh đường đi từ `start` tới mỗi mục**, không phải số dấu `/` trong đường dẫn tuyệt đối.

| Lệnh | Những mục nằm trong phạm vi | Số đường dẫn của cây ví dụ |
|---|---|---:|
| `Files.walk(root, 0)` | Chỉ `root` | 1 |
| `Files.walk(root, 1)` | `root` và các mục con trực tiếp | 4 |
| `Files.walk(root, 2)` | Thêm `docs/readme.txt`, `docs/archive`, `images/logo.bin` | 7 |
| `Files.walk(root, 3)` | Thêm `docs/archive/old.txt` | 8 |
| `Files.walk(root)` | Tất cả các cấp, nếu không bị lỗi hoặc hạn chế khác | 8 |

Từ đó rút ra:

1. **`maxDepth = 0`:** vẫn nhận **một mục `start`**, nếu điểm bắt đầu hợp lệ; không phải luồng rỗng.
2. **`maxDepth = 1`:** gồm `start` **và** các tệp, thư mục con trực tiếp. Đây không giống hoàn toàn `Files.list(root)`, vì `Files.list` không tự đưa `root` vào kết quả.
3. **`maxDepth = 2`:** gồm các mục cách `root` nhiều nhất hai cấp. Thư mục `docs/archive` có trong kết quả ở cấp 2 nhưng **không đi xuống** để nhận `old.txt` ở cấp 3.
4. Nếu `start` là một tệp thường, việc duyệt không có cây con để đi xuống; khi `maxDepth` hợp lệ và tệp truy cập được, kết quả thường chỉ gồm đường dẫn tệp đó.
5. Mức sâu luôn tính **tương đối với đối số `start`**. Nếu gọi `Files.walk(root.resolve("docs"), 1)` thì `docs` trở thành cấp 0, không phải cấp 1.

Mẹo dễ nhớ: **`maxDepth` là độ sâu tối đa được *xuất hiện* trong stream**, và một thư mục ở đúng mức đó **không được duyệt tiếp vào bên trong**.

## 3. Ví dụ Java 17 chạy độc lập: duyệt có giới hạn độ sâu

Chương trình tạo cây ở mục trên trong một thư mục tạm, đếm số `Path` tại từng độ sâu và hiển thị danh sách **được sắp xếp** để kết quả không phụ thuộc thứ tự duyệt của filesystem.

Lưu nguyên mã thành **`FilesWalkDepthDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class FilesWalkDepthDemo {
    static long countAtDepth(Path root, int maxDepth)
            throws IOException {
        try (Stream<Path> paths = Files.walk(root, maxDepth)) {
            return paths.count();
        }
    }

    static List<String> listAtDepth(Path root, int maxDepth)
            throws IOException {
        try (Stream<Path> paths = Files.walk(root, maxDepth)) {
            return paths
                    .map(root::relativize)
                    .map(path -> path.toString().isEmpty()
                            ? "."
                            : path.toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }

    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("files-walk-demo-");

        try {
            Files.createDirectories(root.resolve("docs/archive"));
            Files.createDirectory(root.resolve("images"));
            Files.writeString(root.resolve("root.txt"), "ROOT",
                    StandardCharsets.UTF_8);
            Files.writeString(root.resolve("docs/readme.txt"), "README",
                    StandardCharsets.UTF_8);
            Files.writeString(root.resolve("docs/archive/old.txt"), "OLD",
                    StandardCharsets.UTF_8);
            Files.write(root.resolve("images/logo.bin"),
                    new byte[] {1, 2, 3});

            for (int depth = 0; depth <= 3; depth++) {
                System.out.println("Depth " + depth + ": "
                        + countAtDepth(root, depth));
            }

            System.out.println("Depth 2 paths: "
                    + listAtDepth(root, 2));
            System.out.println("All paths: "
                    + countAtDepth(root, Integer.MAX_VALUE));
        } finally {
            try (Stream<Path> paths = Files.walk(root)) {
                List<Path> reverse = paths
                        .sorted(Comparator.reverseOrder())
                        .toList();
                for (Path path : reverse) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 FilesWalkDepthDemo.java
java FilesWalkDepthDemo
~~~

Trong filesystem thông thường, khi chương trình có quyền tạo/xóa dữ liệu tạm, output là:

~~~text
Depth 0: 1
Depth 1: 4
Depth 2: 7
Depth 3: 8
Depth 2 paths: [., docs, docs/archive, docs/readme.txt, images, images/logo.bin, root.txt]
All paths: 8
~~~

**Vì sao output đáng tin:**

- Tổng đối tượng trong cây là **8**: một root, ba đường dẫn cấp 1, ba đường dẫn cấp 2 và một đường dẫn cấp 3.
- `Files.walk(root, 2)` trả root và mọi mục tới cấp 2; nó không cần trả `old.txt`.
- `countAtDepth` đóng stream trong `try-with-resources` ngay khi đếm xong.
- `listAtDepth` gọi `root.relativize(path)` để tạo đường dẫn tương đối, chuyển dấu phân cách của kết quả hiển thị về `/` rồi `sorted()`. Vì vậy **không phụ thuộc thứ tự liệt kê của hệ điều hành**.
- `root.relativize(root)` tạo path rỗng theo quy tắc của provider mặc định; ví dụ cố ý hiển thị nó thành ký hiệu `.`. Đây chỉ là **nhãn trình bày**.
- Khối `finally` duyệt cây một lần nữa, sắp theo thứ tự ngược và xóa các mục con trước thư mục cha. Ở đây cây nhỏ do chính chương trình tạo, nên dùng `toList()` để đơn giản hóa dọn dẹp; với cây lớn, nên dùng `Files.walkFileTree` hoặc chiến lược dọn riêng thay vì gom toàn bộ đường dẫn vào RAM.
- Các lời gọi `Files.writeString` có charset rõ ràng đối với tệp văn bản; `Files.write` dành cho ba byte nhị phân. Charset **không quyết định cách đếm độ sâu**.

Lưu ý: chương trình sử dụng đường dẫn tương đối với root do ứng dụng tạo. Nếu một tác nhân ngoài thay đổi cây tạm đồng thời hoặc provider có hành vi đặc biệt, thao tác có thể ném lỗi. Ví dụ chứng minh quy tắc API trong điều kiện bình thường, không phải một công cụ xóa thư mục bất kỳ.

## 4. Cần `try-with-resources` vì stream có tài nguyên hệ thống

Đây là lỗi hay gặp:

~~~java
// Không nên để Stream<Path> mở không có giới hạn vòng đời.
Stream<Path> paths = Files.walk(root);
paths.forEach(System.out::println);
// Quên paths.close().
~~~

Khác một số stream tính toán thuần bộ nhớ, `Files.walk` có thể giữ **directory stream / handle** khi đang duyệt. Tài liệu yêu cầu đóng stream trả về để giải phóng tài nguyên kịp thời. Mẫu dùng chuẩn là:

~~~java
try (Stream<Path> paths = Files.walk(root, 2)) {
    paths.forEach(System.out::println);
}
~~~

Đây là hai đoạn **minh họa**, `root` là `Path` đã khai báo, cần import `java.nio.file.*` và `java.util.stream.Stream`, đồng thời xử lý `IOException`.

Đừng hiểu rằng gọi `count()`, `forEach()` hoặc `toList()` sẽ tự động gọi `close()`. Các thao tác terminal **không thay thế việc đóng stream**. `try-with-resources` đảm bảo đóng cả khi thân khối xử lý gặp lỗi.

### Vì sao không giữ stream cho nhiều truy vấn?

Một `Stream` trong Java thường chỉ được tiêu thụ **một lần**:

~~~java
try (Stream<Path> paths = Files.walk(root, 2)) {
    long count = paths.count();
    // Không dùng lại chính paths để gọi paths.forEach(...).
}
~~~

Nếu cần đếm và liệt kê riêng, hãy mở **hai lượt duyệt** (như ví dụ Java ở trên), hoặc thu kết quả một lần vào `List` khi số lượng mục đủ nhỏ và chấp nhận dùng bộ nhớ để lưu. Việc duyệt hai lượt có thể quan sát **hai trạng thái filesystem khác nhau** nếu file thay đổi giữa hai lần.

## 5. `maxDepth` giới hạn thăm dò; `filter` chỉ lọc phần đã duyệt

So sánh hai cách:

~~~java
// Giới hạn phần cây mà traversal đi xuống.
try (Stream<Path> paths = Files.walk(root, 2)) {
    long count = paths.count();
}
~~~

và:

~~~java
// Vẫn duyệt toàn bộ cây; filter chỉ chọn phần tử ở mức <= 2.
try (Stream<Path> paths = Files.walk(root)) {
    long count = paths
            .filter(p -> root.relativize(p).getNameCount() <= 2)
            .count();
}
~~~

Đây là **đoạn minh họa**. Biểu thức lọc theo `getNameCount()` còn cần cân nhắc trường hợp `root` tương đối với chính nó và quy tắc provider; không dùng nó như bản sao tổng quát của `maxDepth`.

**Điểm quan trọng:** cách thứ hai vẫn có thể **đi vào các thư mục sâu hơn** trước khi filter loại kết quả, do đó có thể đọc rất nhiều metadata, gặp lỗi ở nhánh mà bạn thực sự không muốn duyệt, hoặc tốn thời gian đáng kể. Nếu nghiệp vụ chỉ cần hai cấp, hãy dùng **`Files.walk(root, 2)` ngay tại nguồn**.

Dùng `filter` để chọn **loại mục** sau khi giới hạn độ sâu, ví dụ chỉ lấy tệp thường:

~~~java
try (Stream<Path> paths = Files.walk(root, 2)) {
    paths.filter(Files::isRegularFile)
         .forEach(System.out::println);
}
~~~

`Files.isRegularFile(path)` kiểm tra metadata filesystem và theo symbolic link theo hợp đồng mặc định của phương thức đó. Nếu bạn cần không theo link trong bước kiểm tra loại tệp, hãy dùng:

~~~java
paths.filter(p -> Files.isRegularFile(
        p, LinkOption.NOFOLLOW_LINKS))
~~~

Dòng sau là **biểu thức minh họa bên trong pipeline**, cần `LinkOption` tương ứng. Không áp dụng `filter` thứ hai trên chính stream đã tiêu thụ bởi `forEach` ở ví dụ trước; phải tạo stream mới.

**Chọn đúng nhiệm vụ:** `maxDepth` giới hạn **đi sâu**, còn `filter` giới hạn **các kết quả được giữ lại**.

## 6. Độ sâu tối đa **không** đồng nghĩa số lượng tệp tối đa

Một thư mục gốc có thể có hàng triệu file con trực tiếp. `Files.walk(root, 1)` vẫn có thể phải liệt kê tất cả chúng: giới hạn độ sâu **không tự giới hạn số mục** hoặc số byte đọc từ metadata.

Do đó, khi tối ưu hóa:

- Xác định `maxDepth` phù hợp để tránh những nhánh không cần duyệt.
- Nếu chỉ cần một số kết quả, có thể dùng thao tác ngắn mạch như `findFirst()` hoặc `limit(n)`, nhưng không nên mặc định mọi filesystem ngừng mọi I/O đúng tại cùng một thời điểm.
- Tránh `toList()` hoặc `collect(toList())` trên cây có số mục quá lớn nếu không cần toàn bộ kết quả trong RAM.
- Chọn `forEach` hoặc xử lý tuần tự từng mục nếu đầu ra có thể xử lý theo dòng.
- Với yêu cầu kiểm soát nhánh, hủy duyệt hoặc quyết định `SKIP_SUBTREE`, cân nhắc `Files.walkFileTree` và `FileVisitor` thay vì ép mọi trường hợp vào một stream filter.

`Files.walk` phù hợp cho nhu cầu duyệt và pipeline tương đối đơn giản; đó không phải sự bảo đảm rằng mọi thao tác trên một cây lớn sẽ nhanh hoặc nhẹ chỉ nhờ đặt `maxDepth`.

## 7. Symbolic link và tùy chọn `FOLLOW_LINKS`

Mặc định, `Files.walk` **không theo symbolic link**. Giả sử có một symlink `root/linked-docs` trỏ tới một thư mục khác:

~~~text
root/
├── linked-docs -> /elsewhere/docs
└── local.txt
~~~

Với lời gọi:

~~~java
Files.walk(root, 2);
~~~

symlink có thể xuất hiện như một `Path` được liệt kê, nhưng traversal **không tự đi theo nó để duyệt toàn bộ nội dung thư mục đích**.

Để yêu cầu theo symbolic link, truyền `FileVisitOption.FOLLOW_LINKS`:

~~~java
try (Stream<Path> paths = Files.walk(
        root, 2, FileVisitOption.FOLLOW_LINKS)) {
    paths.forEach(System.out::println);
}
~~~

**Độ sâu vẫn tính theo tuyến duyệt bắt đầu tại `root`**, bao gồm đường đi qua liên kết nếu được theo. Không được giả định độ sâu bằng số thành phần trong real path của tệp đích symlink; đường đi logic qua liên kết và đường dẫn thực có thể khác nhau.

Khi theo symlink, một cây có thể chứa **vòng liên kết** như A → B → A. `Files.walk` phát hiện vòng trong quá trình duyệt theo hợp đồng và có thể báo `FileSystemLoopException` (thuộc `IOException`; trong giai đoạn tiêu thụ stream, có thể xuất hiện dưới dạng `UncheckedIOException`). Một hệ thống từ xa hoặc cây thay đổi liên tục cũng có thể sinh lỗi I/O.

**Điểm phân biệt:** `Files.walk(root, 2)` giới hạn tối đa số cấp được duyệt, nhưng không phải cơ chế bảo mật để ngăn symlink trỏ ra ngoài thư mục gốc. Nếu phải xử lý nội dung không tin cậy, xác định chính sách liên kết và quyền truy cập riêng.

### Ví dụ khi maxDepth = 0 hoặc 1 có FOLLOW_LINKS

`FOLLOW_LINKS` không có nghĩa API luôn duyệt thư mục mà symlink trỏ đến bất chấp giới hạn. Với `maxDepth = 0`, chỉ `start` thuộc phạm vi kết quả. Với `maxDepth = 1`, chỉ các mục con trực tiếp xuất hiện; API không đi sâu hơn vào bên trong một mục liên kết ở cấp 1.

Tóm lại, **`FOLLOW_LINKS` thay quy tắc có được đi theo liên kết hay không**, còn **`maxDepth` vẫn quyết định sâu nhất tới đâu**.

## 8. IOException khi mở stream và UncheckedIOException khi duyệt

Đây là chi tiết quan trọng trong API stream:

~~~java
try (Stream<Path> paths = Files.walk(root, 3)) {
    paths.forEach(path -> System.out.println(path));
}
~~~

`Files.walk(...)` khai báo `throws IOException`. Lỗi có thể xảy ra khi gọi phương thức, ví dụ không đọc được thuộc tính của điểm bắt đầu hoặc đối tượng đầu vào không tồn tại theo provider.

Nhưng việc duyệt là **lazy**. Sau khi đã nhận `Stream<Path>`, lỗi xuất hiện trong lúc xử lý directory tiếp theo có thể được đưa ra dưới dạng **`UncheckedIOException`** vì pipeline `Stream` không khai báo checked exception trên `forEach`, `count`, v.v.

Mẫu phân biệt lỗi:

~~~java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class WalkErrors {
    static void printTree(Path root, int maxDepth)
            throws IOException {
        try (Stream<Path> paths = Files.walk(root, maxDepth)) {
            paths.forEach(System.out::println);
        } catch (UncheckedIOException ex) {
            throw ex.getCause(); // Đưa lại IOException gốc cho caller.
        }
    }
}
~~~

Đây là **lớp tiện ích Java 17 biên dịch được**, nhưng không có `main`. Nó minh họa cách chuyển lỗi unchecked trong lúc duyệt về checked `IOException` để caller dùng cùng một chính sách xử lý. Trong hệ thống thực, bạn có thể chọn báo lỗi, retry có giới hạn hoặc ghi lại nhánh lỗi theo chính sách; đừng âm thầm nuốt ngoại lệ rồi tuyên bố đã duyệt đủ cây.

Không nên mặc định mọi lỗi trong một pipeline đều do filesystem: code bên trong `map`, `filter` hoặc `forEach` cũng có thể ném runtime exception riêng. Việc xử lý lỗi cần giữ lại nguyên nhân để chẩn đoán đúng.

## 9. Kết hợp maxDepth với một vài tác vụ thường gặp

### Liệt kê tệp thường đến hai cấp

~~~java
try (Stream<Path> paths = Files.walk(root, 2)) {
    paths.filter(Files::isRegularFile)
         .forEach(System.out::println);
}
~~~

Phương thức `Files::isRegularFile` có thể truy cập metadata và mặc định theo link khi kiểm tra loại tệp; nếu cần cùng một chính sách no-follow xuyên suốt, truyền `NOFOLLOW_LINKS` cho phép kiểm tra tương ứng.

### Liệt kê thư mục (gồm cả gốc) đến ba cấp

~~~java
try (Stream<Path> paths = Files.walk(root, 3)) {
    paths.filter(Files::isDirectory)
         .forEach(System.out::println);
}
~~~

Nếu muốn **loại root** khỏi output:

~~~java
try (Stream<Path> paths = Files.walk(root, 3)) {
    paths.filter(Files::isDirectory)
         .filter(path -> !path.equals(root))
         .forEach(System.out::println);
}
~~~

Dù loại root khỏi kết quả sau `filter`, nó vẫn là **điểm bắt đầu** và được traversal xét đến ở cấp 0.

### Đếm tệp đến một cấp giới hạn

~~~java
long files;
try (Stream<Path> paths = Files.walk(root, 2)) {
    files = paths.filter(Files::isRegularFile).count();
}
~~~

Lưu ý `count()` trên stream đường dẫn không tương đương số byte tệp, số dòng, hoặc kích thước lưu trữ; đó chỉ là **số phần tử `Path` thỏa điều kiện**.

### Lấy đường dẫn tương đối để in báo cáo

~~~java
try (Stream<Path> paths = Files.walk(root, 2)) {
    paths.map(root::relativize)
         .map(Path::toString)
         .sorted()
         .forEach(System.out::println);
}
~~~

Khi root và các phần tử thuộc cùng filesystem/provider, `relativize` phù hợp để tạo nhãn tương đối. Nếu mục đích là xuất ra file báo cáo có thứ tự ổn định, `sorted()` hữu ích nhưng có thể cần **giữ nhiều phần tử trong bộ nhớ** để sắp xếp.

## 10. `Files.walk`, `Files.list`, `Files.find` và `Files.walkFileTree`

| API | Phạm vi | Điểm cần nhớ |
|---|---|---|
| `Files.list(dir)` | Liệt kê **một cấp con trực tiếp**; không bao gồm chính `dir` | Trả `Stream<Path>` cũng phải được đóng |
| `Files.walk(start)` | Duyệt từ `start` xuống các cấp bên dưới | Bao gồm `start`; không đặt giới hạn sâu hữu hạn |
| `Files.walk(start, maxDepth)` | Duyệt và dừng ở mức sâu cho phép | Bao gồm `start`, cấp 0 |
| `Files.find(start, maxDepth, matcher, options...)` | Tìm các mục phù hợp predicate dựa trên `Path` và `BasicFileAttributes` | Có thể tránh đọc attributes hai lần so với một số cách `walk().filter(...)`; cũng trả stream cần đóng |
| `Files.walkFileTree(start, visitor)` | Duyệt với `FileVisitor`, điều khiển tại từng thư mục/mục | Hợp khi cần `SKIP_SUBTREE`, xử lý lỗi theo từng node, cleanup/copy cây |

`Files.walkFileTree` còn có overload nhận `maxDepth` và `FileVisitOption`. Khi cần chính sách dừng nhánh tùy biến, nó thường rõ hơn một pipeline `Files.walk`. Nhưng nếu chỉ cần “liệt kê các tệp đến hai cấp rồi lọc”, `Files.walk(root, 2)` là một cách ngắn gọn, có sẵn trong Java NIO.2.

## 11. Những lỗi thường gặp

| Hiểu sai | Hậu quả | Cách làm đúng |
|---|---|---|
| Nghĩ `maxDepth = 0` là không có kết quả | Bỏ sót chính `start` | Nhớ root ở **depth 0** |
| Nghĩ `maxDepth = 1` chỉ trả các con của root | Đếm thừa/thiếu vì có root | Muốn loại root thì filter riêng |
| Gọi `Files.walk(root)` rồi mới filter theo depth | Vẫn đi vào nhánh sâu không cần thiết | Dùng overload `Files.walk(root, maxDepth)` |
| Cho rằng `maxDepth` giới hạn số mục được trả | Thư mục một cấp vẫn có thể chứa rất nhiều file | Giới hạn số kết quả/thiết kế xử lý riêng |
| Quên đóng `Stream<Path>` | Có thể giữ handle thư mục hoặc tài nguyên I/O | Dùng `try-with-resources` |
| Dùng cùng một stream cho hai terminal operation | `IllegalStateException` hoặc hành vi stream đã tiêu thụ | Mở lượt duyệt mới hoặc lưu danh sách có kiểm soát |
| Giả định thứ tự tệp trong mỗi thư mục ổn định | Kết quả khác nhau theo filesystem | `sorted()` nếu thứ tự là yêu cầu |
| Cho rằng luôn theo symbolic link | Mặc định không theo liên kết | Truyền `FOLLOW_LINKS` khi chủ đích |
| Theo symlink mà không kiểm soát vòng lặp | Có thể gặp `FileSystemLoopException` | Xử lý lỗi và đặt giới hạn phù hợp |
| Chỉ bắt `IOException` ngoài `Files.walk` mà quên lỗi lazy | Bỏ sót `UncheckedIOException` khi tiêu thụ stream | Phân biệt giai đoạn mở và duyệt |
| Gọi `toList()` trên toàn bộ cây khổng lồ | Dùng nhiều RAM | Xử lý dạng stream hoặc walkFileTree |
| Nhầm đường dẫn con ở maxDepth với tên file có số dấu `/` nhất định | Sai phạm vi khi root có nhiều thành phần | Đếm độ sâu tương đối với `start` |

## 12. Tự kiểm tra

**Câu 1.** Với cây có `root/a/file.txt`, gọi `Files.walk(root, 1)` có thấy `file.txt` không?

> **Không.** `root` là cấp 0, `a` cấp 1, `file.txt` cấp 2. `maxDepth = 1` không đi tới cấp 2.

**Câu 2.** Muốn đếm tệp con trực tiếp mà không đếm root, dùng `Files.list(root)` hay `Files.walk(root, 1)`?

> Cả hai có thể hỗ trợ nếu lọc đúng, nhưng `Files.list(root)` phù hợp trực tiếp hơn vì nó chỉ liệt kê con của thư mục, **không gồm root**. `Files.walk(root, 1)` gồm cả root và các con; có thể filter loại root.

**Câu 3.** Vì sao `Files.walk(root).filter(p -> depth(p) <= 2)` có thể kém hơn `Files.walk(root, 2)`?

> Cách đầu vẫn có thể duyệt các nhánh rất sâu rồi mới bỏ phần tử bằng filter. `maxDepth` giới hạn **việc đi xuống cây** ngay từ API, không chỉ loại kết quả.

**Câu 4.** Thư mục cấp 2 có rất nhiều file bên trong. `Files.walk(root, 2)` có đi vào thư mục ấy để đọc các file cấp 3 không?

> **Không.** Thư mục ở độ sâu tối đa được trả về như một mục, nhưng traversal không đi sâu hơn mức cho phép.

**Câu 5.** Có cần đóng stream của `Files.walk` sau khi `count()` đã chạy xong không?

> **Có.** Terminal operation không tự thay thế `close()`; dùng `try-with-resources` để giải phóng tài nguyên thư mục.

**Câu 6.** Nếu dùng `FOLLOW_LINKS` mà gặp vòng symlink, có thể gặp ngoại lệ gì?

> **`FileSystemLoopException`**, có thể được bọc trong `UncheckedIOException` khi lỗi phát sinh lúc tiêu thụ stream. Caller cần xử lý theo chính sách lỗi I/O.

**Câu 7.** `Files.walk(root, 2)` có đảm bảo chỉ sinh tối đa hai kết quả không?

> **Không.** Số 2 là **độ sâu**, không phải số tệp/thư mục. Số phần tử phụ thuộc độ rộng của cây ở các cấp được phép.

## Ghi nhớ

- `Files.walk(start, maxDepth)` duyệt từ **`start` là cấp 0** đến mức sâu tối đa.
- `0` lấy root, `1` lấy root + con trực tiếp, `2` thêm cháu; mọi đường dẫn đều được tính tương đối với `start`.
- Khác với `filter` sau khi duyệt, `maxDepth` giúp **không đi sâu hơn** ngưỡng.
- `Files.walk` trả **`Stream<Path>` có tài nguyên I/O**, phải đóng bằng `try-with-resources`.
- Mặc định **không theo symbolic link**; `FOLLOW_LINKS` thay đổi điều đó nhưng có nguy cơ vòng liên kết và lỗi I/O.
- Kết quả có thể phụ thuộc filesystem về thứ tự và trạng thái; **sort khi cần**, xử lý `IOException` / `UncheckedIOException` và đừng nhầm giới hạn độ sâu với giới hạn số lượng.

**Tài liệu tham khảo:** [Java SE 17 — `Files.walk`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html), [`FileVisitOption`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileVisitOption.html), [`Stream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="directory-tree-traversal-004">Files.find kết hợp Path với BasicFileAttributes</a>

<details>
<summary>Xem chi tiết</summary>

# Files.find kết hợp Path với BasicFileAttributes trong Java

Khi cần **tìm những mục trong cây thư mục theo điều kiện**, Java NIO.2 cung cấp phương thức `Files.find(...)`. Điểm mạnh của API này là hàm điều kiện nhận **đồng thời `Path` và `BasicFileAttributes`**: bạn vừa kiểm tra được **tên/vị trí** tệp, vừa kiểm tra **loại, kích thước và thời gian** mà không phải tự đọc lại thuộc tính ở từng bộ lọc.

Một ví dụ thực tế: tìm tất cả tệp có đuôi `.txt`, kích thước từ 4 byte trở lên, nằm trong tối đa 3 cấp từ thư mục gốc. `Files.find` trả về `Stream<Path>` để tiếp tục `map`, `filter`, `sorted` hoặc `forEach`.

Tuy nhiên, stream này gắn với tài nguyên duyệt hệ thống tệp và có thể ném lỗi khi đang tiêu thụ; **phải đóng bằng `try-with-resources`**. Cũng cần hiểu chính xác `maxDepth`, quy tắc symbolic link, thứ tự duyệt và giới hạn của thông tin thuộc tính.

## 1. Chữ ký và ý nghĩa từng tham số

Trong `java.nio.file.Files` (Java 17+), overload cơ bản có dạng:

~~~java
public static Stream<Path> find(
        Path start,
        int maxDepth,
        BiPredicate<Path, BasicFileAttributes> matcher,
        FileVisitOption... options) throws IOException
~~~

Các loại cần import:

~~~java
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileVisitOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
~~~

Đây là hai **mảnh mã minh họa chữ ký API và import**, không phải một file Java tự chạy.

| Thành phần | Ý nghĩa |
|---|---|
| `start` | Đường dẫn bắt đầu duyệt; có thể là thư mục hoặc một tệp đơn |
| `maxDepth` | Độ sâu tối đa: `0` là chỉ đối tượng bắt đầu, `1` thêm các con trực tiếp |
| `matcher` | Hàm kiểm tra hai đối số `(Path path, BasicFileAttributes attrs)`, trả `true` khi cần đưa đường dẫn vào kết quả |
| `options` | Các lựa chọn duyệt, thường là `FileVisitOption.FOLLOW_LINKS` nếu muốn theo symbolic link |
| `Stream<Path>` | Stream các đường dẫn **khớp điều kiện**, được tạo trong quá trình duyệt |

Ví dụ ngắn:

~~~java
// Mảnh mã minh họa: root là Path đã được xác định.
try (Stream<Path> matches = Files.find(
        root,
        2,
        (path, attrs) -> attrs.isRegularFile())) {
    matches.forEach(System.out::println);
}
~~~

Phương thức `find` chỉ đưa các `Path` có `matcher.test(path, attrs) == true` vào stream kết quả. Nếu predicate là `(path, attrs) -> true`, các mục đã duyệt đều có thể xuất hiện trong kết quả, bao gồm cả đường dẫn `start`.

**Không nhầm** `Files.find` với tìm kiếm tên theo chuỗi thuần túy, chẳng hạn `String.contains(...)`. `Files.find` **thực sự duyệt hệ thống tệp**; nó cần quyền truy cập và có thể gặp lỗi I/O.

## 2. Path và BasicFileAttributes đảm nhiệm hai vai trò khác nhau

### Path: địa chỉ và các thành phần tên

`Path` trả lời câu hỏi “mục nằm ở đâu, được đặt tên như thế nào?”:

- `path.getFileName()`: tên cuối của đường dẫn (có thể `null` đối với đường dẫn chỉ gồm root).
- `path.getParent()`: thư mục cha nếu có.
- `path.startsWith(base)`: kiểm tra tiền tố theo **thành phần đường dẫn**, không phải chuỗi ký tự.
- `path.endsWith(otherPath)`: kiểm tra hậu tố theo thành phần đường dẫn; **không** phải kiểm tra phần mở rộng `.txt`.
- `root.relativize(path)`: lấy đường dẫn tương đối từ `root` tới `path`, nếu hai `Path` tương thích theo provider.

### BasicFileAttributes: thông tin cơ bản về mục hệ thống tệp

`BasicFileAttributes` là giao diện chứa thông tin lấy từ file system:

| API của `BasicFileAttributes` | Ý nghĩa |
|---|---|
| `isRegularFile()` | Mục được xác định là tệp thường theo quy tắc truy cập thuộc tính |
| `isDirectory()` | Mục là thư mục |
| `isSymbolicLink()` | Mục được nhận dạng là symbolic link nếu việc đọc thuộc tính không đi theo nó |
| `isOther()` | Loại mục không thuộc ba nhóm trên |
| `size()` | Kích thước được báo cáo, đơn vị byte; có quy tắc riêng với mục không phải tệp thường |
| `creationTime()` | Thời gian tạo do provider báo |
| `lastModifiedTime()` | Thời gian sửa đổi cuối |
| `lastAccessTime()` | Thời gian truy cập cuối |
| `fileKey()` | Khóa nhận dạng tệp nếu provider hỗ trợ, có thể `null` |

Không nên suy diễn rằng `size()` của **thư mục** có ý nghĩa “tổng số byte của các tệp con”; đối với mục không phải tệp thường, kích thước có thể mang ý nghĩa khác hoặc không được xác định một cách hữu ích. Nếu cần tính tổng dung lượng cây thư mục, phải duyệt và cộng **kích thước các tệp phù hợp**, đồng thời xử lý hard link/symbolic link nếu muốn tránh đếm trùng.

Đối với thời gian tạo, một số file system không có thông tin ngày tạo thực sự; provider có thể cung cấp giá trị thay thế. Thời gian được trả dưới dạng `FileTime`, không nên tự giả định timezone hoặc độ chính xác cố định.

## 3. Cách viết matcher đúng và rõ nghĩa

Một `matcher` thường kết hợp điều kiện trên `Path` và `BasicFileAttributes`:

~~~java
// Mảnh mã minh họa: thuộc phần tham số của Files.find.
(path, attrs) ->
        attrs.isRegularFile()
        && path.getFileName() != null
        && path.getFileName().toString().endsWith(".txt")
        && attrs.size() >= 4
~~~

Ý nghĩa của từng điều kiện:

1. Chỉ nhận **tệp thường**, loại trừ thư mục.
2. Bảo vệ trường hợp `getFileName() == null` trước khi gọi `toString()`.
3. Kiểm tra phần mở rộng bằng **chuỗi tên tệp**, không dùng `Path.endsWith(".txt")`.
4. Kiểm tra kích thước được cung cấp trong thuộc tính, tránh gọi thêm `Files.size(path)` bên trong predicate chỉ để có cùng thông tin.

Có thể tách predicate để tái sử dụng:

~~~java
BiPredicate<Path, BasicFileAttributes> isNonEmptyText =
        (path, attrs) ->
                attrs.isRegularFile()
                && path.getFileName() != null
                && path.getFileName().toString().endsWith(".txt")
                && attrs.size() > 0;
~~~

Đây là **mảnh mã minh họa**: cần import `BiPredicate`, `Path`, `BasicFileAttributes`. Tên `isNonEmptyText` chỉ khẳng định tệp **có tên kết thúc `.txt` và kích thước khác 0**, chứ **không** xác minh nội dung là văn bản hợp lệ. Phần mở rộng tệp không phải chứng nhận về định dạng hay độ an toàn của nội dung.

Lưu ý predicate có thể được gọi với **thư mục gốc** hoặc **thư mục trung gian**, không chỉ với tệp. Không được giả định mọi `path.getFileName()` luôn khác `null` hoặc mọi `attrs` đều biểu diễn một tệp thường.

## 4. maxDepth: cấp 0, cấp 1, cấp 2...

Giả sử cây sau đây:

~~~text
root/                       ← depth 0
├── note.txt                ← depth 1
├── app.log                 ← depth 1
└── docs/                   ← depth 1
    ├── guide.txt           ← depth 2
    ├── empty.txt           ← depth 2
    └── nested/             ← depth 2
        └── deep.txt        ← depth 3
~~~

Nếu matcher nhận mọi mục:

| `maxDepth` | Những mục có thể được xét |
|---:|---|
| `0` | Chỉ `root` |
| `1` | `root` và các con trực tiếp `note.txt`, `app.log`, `docs` |
| `2` | Thêm `guide.txt`, `empty.txt`, `nested` |
| `3` | Thêm `deep.txt` |
| `Integer.MAX_VALUE` | Duyệt không có giới hạn độ sâu hữu ích ở mức tham số (vẫn chịu giới hạn tài nguyên/khả năng truy cập) |

Nếu matcher chỉ nhận `attrs.isRegularFile()`, đường dẫn thư mục vẫn có thể được **duyệt** để tìm các tệp con, nhưng sẽ **không xuất hiện trong stream kết quả**.

`maxDepth` âm không hợp lệ và gây `IllegalArgumentException`. Đối với cây lớn, hãy giới hạn độ sâu phù hợp nghiệp vụ thay vì mặc định `Integer.MAX_VALUE` một cách vô điều kiện.

Quan trọng: **trả `false` từ matcher cho thư mục không có nghĩa là không đi xuống các con của thư mục đó**. `Files.find` là cơ chế chọn các mục phù hợp; muốn chủ động **cắt bỏ cả một nhánh** (skip subtree) khi duyệt, dùng `Files.walkFileTree` với `FileVisitor`/`SimpleFileVisitor` và trả `FileVisitResult.SKIP_SUBTREE` ở `preVisitDirectory`.

## 5. Ví dụ hoàn chỉnh: tìm file theo tên, kích thước và độ sâu

Tệp **`FilesFindDemo.java`**, tương thích Java 17+:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class FilesFindDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("find-demo-");
        Path docs = root.resolve("docs");
        Path nested = docs.resolve("nested");

        try {
            Files.createDirectories(nested);
            Files.writeString(root.resolve("note.txt"), "abc",
                    StandardCharsets.UTF_8);
            Files.writeString(root.resolve("app.log"), "1234567",
                    StandardCharsets.UTF_8);
            Files.writeString(docs.resolve("guide.txt"), "hello",
                    StandardCharsets.UTF_8);
            Files.writeString(docs.resolve("empty.txt"), "",
                    StandardCharsets.UTF_8);
            Files.writeString(nested.resolve("deep.txt"), "four",
                    StandardCharsets.UTF_8);

            System.out.println("maxDepth=2:");
            printMatches(root, 2);

            System.out.println("maxDepth=3:");
            printMatches(root, 3);
        } finally {
            Files.deleteIfExists(nested.resolve("deep.txt"));
            Files.deleteIfExists(docs.resolve("empty.txt"));
            Files.deleteIfExists(docs.resolve("guide.txt"));
            Files.deleteIfExists(root.resolve("app.log"));
            Files.deleteIfExists(root.resolve("note.txt"));
            Files.deleteIfExists(nested);
            Files.deleteIfExists(docs);
            Files.deleteIfExists(root);
        }
    }

    private static void printMatches(Path root, int maxDepth)
            throws IOException {
        try (Stream<Path> matches = Files.find(
                root,
                maxDepth,
                (path, attrs) ->
                        attrs.isRegularFile()
                        && path.getFileName() != null
                        && path.getFileName().toString().endsWith(".txt")
                        && attrs.size() >= 3)) {

            matches.map(root::relativize)
                    .map(Path::toString)
                    .map(name -> name.replace('\\', '/'))
                    .sorted()
                    .forEach(System.out::println);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 FilesFindDemo.java
java FilesFindDemo
~~~

Stdout trên file system mặc định phổ biến, khi các thao tác tạo tệp thành công:

~~~text
maxDepth=2:
docs/guide.txt
note.txt
maxDepth=3:
docs/guide.txt
docs/nested/deep.txt
note.txt
~~~

Giải thích:

- `note.txt` có 3 byte, `guide.txt` có 5 byte, `deep.txt` có 4 byte; đều thỏa `attrs.size() >= 3` và tên kết thúc `.txt`.
- `empty.txt` có kích thước 0 nên không khớp. `app.log` không có đuôi `.txt`.
- `deep.txt` nằm sâu 3 cấp, nên bị loại khi `maxDepth == 2` và xuất hiện khi `maxDepth == 3`.
- `map(root::relativize)` đổi đường dẫn tuyệt đối ngẫu nhiên của tệp tạm thành dạng tương đối dễ đọc.
- `replace('\\', '/')` chỉ chuẩn hóa **chuỗi được in** giữa Windows/Unix; nó không đổi cách đối tượng `Path` được duyệt.
- `sorted()` tạo thứ tự xuất hiện có thể dự đoán. Nếu bỏ `sorted()`, không có bảo đảm chung rằng thứ tự duyệt từng mục sẽ giống nhau giữa các file system.

Chương trình chỉ thao tác trong thư mục tạm mà nó tự tạo. `finally` xóa các tệp rồi xóa thư mục từ cấp sâu lên, vì `Files.deleteIfExists(dir)` không tự xóa đệ quy một thư mục còn chứa nội dung.

## 6. Tìm bằng thời gian, kích thước và loại file

`BasicFileAttributes` cho phép xây dựng nhiều dạng bộ lọc.

### Tìm file sửa đổi sau một thời điểm

~~~java
// Mảnh mã minh họa: root và cutoff đã được xác định,
// cutoff có kiểu java.nio.file.attribute.FileTime.
try (Stream<Path> found = Files.find(
        root,
        4,
        (path, attrs) ->
                attrs.isRegularFile()
                && attrs.lastModifiedTime().compareTo(cutoff) >= 0)) {
    found.forEach(System.out::println);
}
~~~

`lastModifiedTime()` trả `FileTime`, có thể so sánh qua `compareTo(...)` hoặc đổi sang `Instant` khi cần, tùy mục tiêu xử lý. Nếu hệ thống cần lọc theo “ngày” ở múi giờ địa phương, phải xác định timezone và thời điểm cắt chính xác; file timestamp không tự mang khái niệm “hôm nay” theo timezone của người dùng.

### Tìm các thư mục

~~~java
try (Stream<Path> directories =
             Files.find(root, 2, (p, attrs) -> attrs.isDirectory())) {
    directories.forEach(System.out::println);
}
~~~

Đây là **mảnh mã minh họa**. Lưu ý `root` cũng có thể được trả về nếu `root` là thư mục; muốn chỉ nhận thư mục con, thêm `!p.equals(root)` hoặc kiểm tra độ sâu qua `root.relativize(p).getNameCount()` với những điều kiện bảo vệ thích hợp.

### Kết hợp điều kiện và tránh đọc lại thuộc tính

~~~java
try (Stream<Path> matches = Files.find(
        root,
        3,
        (p, a) -> a.isRegularFile()
                && a.size() > 1_024
                && p.getFileName() != null
                && p.getFileName().toString().endsWith(".log"))) {
    matches.forEach(System.out::println);
}
~~~

Các đoạn trên minh họa **cách đặt điều kiện vào matcher**. Khi `Files.find` duyệt mục, nó đọc `BasicFileAttributes` và đưa dữ liệu đó vào predicate. Dùng ngay `a.size()`, `a.isRegularFile()` thường tránh một lần truy vấn thuộc tính riêng mà bạn có thể phải tự viết khi dùng `Files.walk(...).filter(p -> Files.size(p) > ...)`.

Tuy nhiên, hiệu quả thực tế còn tùy provider, độ sâu, số file, thiết bị lưu trữ và chi phí xét predicate. Đừng khẳng định `find` luôn nhanh hơn bất kỳ cách duyệt nào trong mọi môi trường.

## 7. Stream<Path> được tiêu thụ như thế nào?

`Files.find(...)` trả về **stream duyệt lười (lazy)**: việc tạo stream không đồng nghĩa mọi mục đã được duyệt/đọc ngay. Phần lớn công việc xảy ra khi gọi terminal operation như `forEach`, `count`, `collect` hoặc `toList`.

~~~java
// Mảnh mã minh họa: root là Path hợp lệ.
try (Stream<Path> found = Files.find(
        root, 2, (p, attrs) -> attrs.isRegularFile())) {
    long total = found.count();
    System.out.println(total);
}
~~~

**Luôn đóng stream**: cơ chế duyệt có thể giữ tài nguyên mở, ví dụ directory handle. `Stream<Path>` từ `Files.find` phải được đóng bằng `try-with-resources` khi không còn dùng, kể cả khi terminal operation ném ngoại lệ.

Các điểm cần nhớ:

1. Một stream thông thường chỉ được tiêu thụ **một lần**. Sau `count()`, không gọi tiếp `forEach()` trên cùng stream với hy vọng duyệt lại.
2. Các intermediate operation như `filter`/`map` tự chúng thường không chạy thao tác I/O ngay; terminal operation kích hoạt pipeline.
3. Dùng `sorted()` hoặc `toList()` sẽ cần giữ các mục tương ứng trong bộ nhớ; một kết quả khổng lồ có thể gây tốn RAM. Nếu chỉ cần xử lý tuần tự và không cần thứ tự ổn định, `forEach` có thể phù hợp hơn.
4. Một số thao tác như `findFirst()`/`limit(...)` có thể ngừng tiêu thụ sớm. Nhưng vẫn phải đóng stream, và không nên coi `limit` là giới hạn chắc chắn cho mọi chi phí truy cập tài nguyên phía dưới.
5. `Stream` đi kèm tài nguyên file system không nên được trả ra khỏi phương thức rồi đóng mất ở trong `try`. Nếu muốn trả danh sách, hãy thu thập và đóng stream bên trong; nếu muốn trả stream, cần định rõ người gọi chịu trách nhiệm đóng.

Ví dụ **sai về vòng đời**:

~~~java
// Mảnh mã minh họa: KHÔNG làm thế nếu trả stream cho caller.
Stream<Path> leaked() throws IOException {
    return Files.find(root, 10, (p, a) -> a.isRegularFile());
}
// Caller có thể quên đóng stream.
~~~

Một giải pháp là xử lý trong scope:

~~~java
// Mảnh mã minh họa: kết quả là List<Path> trong RAM.
try (Stream<Path> stream = Files.find(
        root, 10, (p, a) -> a.isRegularFile())) {
    return stream.toList(); // Java 16+, đóng stream sau khi thu thập.
}
~~~

Nhưng nếu kết quả có thể rất lớn, giải pháp trên lại chuyển rủi ro từ open stream sang **tích lũy danh sách trong RAM**. Hãy cân nhắc xử lý streaming, giới hạn kết quả hoặc API xử lý theo lô tùy yêu cầu.

## 8. Theo symbolic link: mặc định và FOLLOW_LINKS

Mặc định, `Files.find` **không đi theo symbolic link** khi duyệt. Nếu muốn xử lý các thư mục được trỏ tới bởi symbolic link, có thể truyền `FileVisitOption.FOLLOW_LINKS`:

~~~java
// Mảnh mã minh họa, cần import FileVisitOption.
try (Stream<Path> found = Files.find(
        root,
        5,
        (p, attrs) -> attrs.isRegularFile(),
        FileVisitOption.FOLLOW_LINKS)) {
    found.forEach(System.out::println);
}
~~~

Cần phân biệt hai điều:

- **Quy tắc duyệt:** có đi sâu vào thư mục được trỏ đến bằng symbolic link hay không.
- **Thuộc tính mà matcher nhìn thấy:** theo cách đọc thuộc tính trong quá trình duyệt, khi đi theo link có thể thấy thuộc tính của **đích**; không theo link thì có thể thấy thuộc tính của chính **link**. Vì vậy `attrs.isRegularFile()`, `attrs.isDirectory()` và `attrs.isSymbolicLink()` cần được hiểu trong chế độ duyệt đã chọn.

Các rủi ro với `FOLLOW_LINKS`:

1. Cây duyệt có thể sang vị trí khác dự kiến, kể cả bên ngoài `root` về mặt vật lý.
2. Một đường dẫn symbolic link có thể dẫn ngược về một tổ tiên, tạo **vòng lặp**. Khi phát hiện loop, thao tác duyệt có thể phát sinh `FileSystemLoopException` được bọc trong `UncheckedIOException` khi tiêu thụ stream.
3. Cùng một đối tượng file có thể được gặp qua nhiều alias khác nhau; kết quả tìm kiếm không mặc nhiên “deduplicate” theo file identity.
4. Symbolic link và nội dung có thể đổi trong khi duyệt, gây race hoặc lỗi I/O.

Với công cụ rà soát hoặc xóa file, **không nên bật FOLLOW_LINKS chỉ vì muốn tìm nhiều kết quả hơn**. Cần chính sách rõ về quyền truy cập, phạm vi dữ liệu, giới hạn độ sâu và hành vi khi gặp link.

## 9. Files.find khác gì Files.walk và walkFileTree?

| API | Đầu ra/cơ chế | Khi nào nên dùng |
|---|---|---|
| `Files.find(start, depth, matcher, options...)` | `Stream<Path>` các mục khớp; matcher nhận `Path` **và** `BasicFileAttributes` | Cần lọc trực tiếp theo loại/kích thước/thời gian cùng tên đường dẫn |
| `Files.walk(start, depth, options...)` | `Stream<Path>` của các mục được duyệt | Muốn duyệt rồi dùng các thao tác Stream khác; tự lấy thuộc tính nếu cần |
| `Files.walkFileTree(start, visitor)` | Callback `preVisitDirectory`, `visitFile`, `visitFileFailed`, `postVisitDirectory` | Muốn kiểm soát nhánh duyệt, bỏ qua subtree, xử lý lỗi từng mục và luồng công việc tùy chỉnh |

Một lợi thế của `Files.find` so với viết `Files.walk(...).filter(path -> Files.readAttributes(...))` là thuộc tính đã có sẵn trong predicate. Ngược lại, khi cần **chỉ duyệt một số nhánh** hoặc **tiếp tục/loại bỏ dựa trên lỗi của từng mục** theo cách tùy biến, `walkFileTree` với `FileVisitor` thể hiện ý định tốt hơn.

Quan trọng: `find` không hỗ trợ `SKIP_SUBTREE` từ matcher. Ngay cả khi predicate trả `false` cho một thư mục, việc duyệt con của thư mục đó vẫn có thể diễn ra. Đừng dùng một matcher trả `false` để kỳ vọng nó tự tiết kiệm công duyệt cả nhánh.

## 10. Ngoại lệ I/O khi tạo và khi tiêu thụ stream

`Files.find(...)` có thể ném `IOException` **ngay lúc tạo stream** nếu không mở được nguồn ban đầu. Khi đang duyệt lười, một lỗi I/O xuất hiện sau đó có thể được biểu diễn dưới dạng `UncheckedIOException`, với `getCause()` là một `IOException` cụ thể.

~~~java
// Mảnh mã minh họa, cần import IOException và UncheckedIOException.
try (Stream<Path> found = Files.find(
        root, 5, (p, a) -> a.isRegularFile())) {
    found.forEach(this::processFile);
} catch (java.io.UncheckedIOException ex) {
    // Lỗi phát sinh khi stream đang được tiêu thụ.
    java.io.IOException cause = ex.getCause();
    // Ghi nhận và xử lý nguyên nhân I/O.
} catch (java.io.IOException ex) {
    // Lỗi mở nguồn/tạo stream.
}
~~~

Đây là **mảnh mã minh họa bên trong phương thức của một lớp có `processFile`**, không phải chương trình độc lập. Nếu dùng trong method `static`, cần thay `this::processFile` bằng method reference thích hợp.

Không phải mọi lỗi bên trong `matcher` tự động biến thành `UncheckedIOException`: nếu predicate của bạn tự ném một `RuntimeException` khác, ngoại lệ đó có thể lan truyền theo hợp đồng Stream thông thường. Tránh bọc mọi lỗi vào một catch chung rồi lờ đi, vì kết quả tìm kiếm có thể chỉ là **một phần**.

Ngoài ra, thư mục và các tệp có thể thay đổi giữa lần đọc thuộc tính và lúc bạn mở đường dẫn để xử lý. `BasicFileAttributes` là ảnh chụp thuộc tính tại thời điểm được đọc; nó **không khóa file**, không cho một giao dịch duy nhất và không bảo đảm nội dung không thay đổi sau đó. Nếu nghiệp vụ đòi mức nhất quán mạnh hơn, cần chiến lược đồng bộ hoặc thao tác file phù hợp.

## 11. Ví dụ Java 17+: dùng thuộc tính mà không in timestamp phụ thuộc máy

Ví dụ sau tạo một cấu trúc có nội dung xác định và minh họa phép tìm dựa trên nhiều thuộc tính mà vẫn có stdout ổn định.

Tạo file **`FilesFindAttributesDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class FilesFindAttributesDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("find-attrs-");
        Path sub = root.resolve("sub");

        try {
            Files.createDirectory(sub);
            Files.writeString(root.resolve("small.txt"), "a",
                    StandardCharsets.UTF_8);
            Files.writeString(root.resolve("large.txt"), "12345",
                    StandardCharsets.UTF_8);
            Files.writeString(sub.resolve("deep.txt"), "1234",
                    StandardCharsets.UTF_8);

            try (Stream<Path> found = Files.find(
                    root, 1,
                    (p, a) -> a.isRegularFile() && a.size() >= 4)) {
                System.out.println("level1Count=" + found.count());
            }

            try (Stream<Path> found = Files.find(
                    root, 2,
                    (p, a) -> a.isRegularFile()
                            && a.size() >= 4
                            && a.lastModifiedTime() != null)) {
                System.out.println("level2Count=" + found.count());
            }

            try (Stream<Path> found = Files.find(
                    root, 0, (p, a) -> a.isDirectory())) {
                System.out.println("rootOnly=" + found.count());
            }
        } finally {
            Files.deleteIfExists(sub.resolve("deep.txt"));
            Files.deleteIfExists(root.resolve("large.txt"));
            Files.deleteIfExists(root.resolve("small.txt"));
            Files.deleteIfExists(sub);
            Files.deleteIfExists(root);
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 FilesFindAttributesDemo.java
java FilesFindAttributesDemo
~~~

Stdout:

~~~text
level1Count=1
level2Count=2
rootOnly=1
~~~

Giải thích:

- `level1Count=1` vì chỉ `large.txt` nằm ngay dưới root với 5 byte và khớp điều kiện; `small.txt` có 1 byte, `deep.txt` ở cấp 2.
- `level2Count=2` vì khi mở rộng đến cấp 2, `deep.txt` có 4 byte cũng được nhận. `lastModifiedTime()` là thuộc tính `FileTime` do provider cung cấp; ví dụ kiểm tra sự hiện diện của đối tượng thay vì in một ngày giờ thay đổi theo máy.
- `rootOnly=1` minh họa `maxDepth=0` chỉ xét chính thư mục bắt đầu và matcher nhận nó vì `isDirectory()`.

Dù ví dụ cho stdout cố định trong môi trường bình thường, quyền tệp, thời điểm bị xóa bởi tiến trình khác hoặc file system đặc biệt vẫn có thể tạo lỗi I/O mà chương trình phải xử lý.

## 12. Đường dẫn tương đối, tuyệt đối và so sánh với root

Khi xử lý kết quả của `Files.find`, `Path` trả về có ngữ cảnh tương ứng với `start`; code hiển thị thường dùng:

~~~java
// Mảnh mã minh họa: path được tạo từ kết quả tìm kiếm dưới root.
Path relative = root.relativize(path);
System.out.println(relative);
~~~

`Path.relativize(...)` yêu cầu các đường dẫn có thể tương đối hóa theo quy tắc của provider (trên default file system, không trộn bừa absolute và relative). Để logic dễ kiểm chứng, nên chuẩn hóa cách xác định `root` trước khi gọi `find` và không ghép string từ nhiều file system không tương thích.

Nếu muốn đối chiếu path theo **thành phần**, dùng `startsWith(Path)`/`endsWith(Path)` chứ không dùng tiền tố ký tự trên `toString()`. Nhưng phép kiểm tra lexical `candidate.normalize().startsWith(root.normalize())` **không phải hàng rào bảo mật đầy đủ** khi có symbolic link và thay đổi đồng thời của filesystem.

### Một số matcher hữu ích

~~~java
// Mảnh mã minh họa (cần đặt trong một phương thức).
(path, attrs) ->
        attrs.isRegularFile()
        && path.getFileName() != null
        && path.getFileName().toString().startsWith("report-")
~~~

~~~java
// Mảnh mã minh họa.
(path, attrs) -> attrs.isDirectory() && !path.equals(root)
~~~

~~~java
// Mảnh mã minh họa: chỉ lấy tên file, không dựa vào tên thư mục cha.
(path, attrs) -> attrs.isRegularFile()
        && path.getFileName() != null
        && path.getFileName().toString().endsWith(".json")
~~~

Các predicate trên kiểm tra điều kiện **tên hoặc loại mục**, không tự đọc hay xác thực nội dung JSON/tệp báo cáo. Nếu cần phân tích nội dung, thực hiện sau khi tìm được đường dẫn, với cơ chế đọc và kiểm tra bảo mật phù hợp.

## 13. Hiệu năng và tài nguyên khi tìm trong cây lớn

`Files.find` là lựa chọn gọn, nhưng cần cân nhắc chi phí:

- **Tổng số thư mục/mục được duyệt:** matcher đơn giản không có nghĩa chỉ truy cập những mục đã khớp; cây lớn vẫn có thể cần đi qua rất nhiều thư mục.
- **Giới hạn độ sâu:** `maxDepth` giúp ngăn duyệt vào vùng quá sâu, nhưng không giới hạn số mục trong các cấp đó.
- **Chi phí thuộc tính:** `Files.find` lấy `BasicFileAttributes` cho từng mục được xét. Trên ổ mạng hoặc provider từ xa, thao tác truy vấn thuộc tính có thể đáng kể.
- **Ngưỡng bộ nhớ:** `Stream` lazy giảm nhu cầu tích lũy khi tiêu thụ tuần tự, nhưng `sorted()`, `toList()`, `collect(...)` có thể giữ hàng nghìn/hàng triệu kết quả trong RAM.
- **Không có thứ tự duyệt ổn định theo hợp đồng:** nếu yêu cầu thứ tự chính xác, cần sắp xếp có chủ đích, đồng thời chấp nhận chi phí bộ nhớ.
- **Không tự xử lý lỗi từng nhánh theo policy tùy chỉnh:** nếu cần bỏ qua một thư mục lỗi mà vẫn tiếp tục các nhánh khác, `walkFileTree` với `visitFileFailed` có thể phù hợp hơn.
- **Không suy ra nội dung file từ thuộc tính:** `attrs.size()` giúp lọc kích thước, không đảm bảo file có nội dung đúng định dạng, cũng không chống nội dung thay đổi sau khi kiểm tra.
- **Không tự giới hạn thời gian duyệt:** khi cây hoặc file system bị chậm, cần cân nhắc chạy trong thành phần có quản lý tài nguyên, giới hạn tác vụ và chính sách hủy phù hợp với API/hệ điều hành.

Nếu chỉ cần kiểm tra một file đã biết, gọi trực tiếp `Files.readAttributes(path, BasicFileAttributes.class)` hoặc phương thức chuyên dụng có thể đơn giản hơn việc duyệt cả cây.

## 14. Các lỗi thường gặp

| Sai sót | Hệ quả | Cách đúng |
|---|---|---|
| Gọi `Files.find` rồi không đóng stream | Rò rỉ handle duyệt thư mục, đặc biệt trong chương trình chạy lâu | Dùng `try-with-resources` |
| Nghĩ `maxDepth=1` chỉ duyệt root | Root là cấp 0; cấp 1 còn có các con trực tiếp | Vẽ rõ cây và cấp |
| Nghĩ matcher `false` cho thư mục sẽ bỏ qua toàn bộ cây con | `Files.find` chọn kết quả, không hỗ trợ `SKIP_SUBTREE` từ matcher | Dùng `walkFileTree` khi cần prune |
| Dùng `Path.endsWith(".txt")` để kiểm tra extension | `Path.endsWith` so thành phần tên | `getFileName().toString().endsWith(".txt")` |
| Không kiểm tra `getFileName() == null` | Đường dẫn root có thể không có phần tử tên cuối | Kiểm tra null |
| Dùng `Files.size(path)` cho mọi kết quả | Truy vấn I/O thừa và có thể gặp race | Tận dụng `attrs.size()` khi phù hợp |
| Mặc định bật `FOLLOW_LINKS` mà không xét vòng lặp | Có thể duyệt ngoài phạm vi dự tính hoặc gặp loop | Chọn chính sách link tường minh |
| Cho rằng `Files.find` trả danh sách đã duyệt hoàn tất | Stream lazy, lỗi có thể xảy ra lúc terminal operation | Xử lý `IOException` và `UncheckedIOException` |
| Cho rằng kết quả có thứ tự alphabet | Không được bảo đảm | `sorted()` nếu cần và bộ nhớ cho phép |
| Dùng `count()` xong tiếp tục `forEach` cùng stream | Stream chỉ tiêu thụ một lần | Tạo stream mới hoặc thiết kế pipeline một lượt |
| Cho rằng timestamp/size không bao giờ đổi | File system có thể thay đổi trong khi duyệt | Xử lý trạng thái thực tế khi mở/sử dụng |
| Dùng đường dẫn tìm thấy như chứng nhận truy cập an toàn | Symlink/TOCTOU có thể làm thay đổi đích vật lý | Kết hợp chính sách filesystem và thao tác mở an toàn |

## 15. Checklist trước khi dùng trong ứng dụng thật

1. Xác định `start` và `maxDepth`, tránh duyệt cả ổ đĩa nếu chỉ cần một cây con.
2. Viết predicate kết hợp `Path` và `BasicFileAttributes`; kiểm tra `attrs.isRegularFile()` trước khi dùng size theo nghĩa file thường.
3. Kiểm tra `path.getFileName() != null` trước khi xử lý tên.
4. Quyết định chính sách **có theo symbolic link không**, hiểu các rủi ro về loop và vượt phạm vi.
5. Đặt `Files.find(...)` trong `try-with-resources`, kể cả khi chỉ dùng `count()` hoặc `findFirst()`.
6. Xử lý cả ngoại lệ lúc tạo stream và lỗi được bọc `UncheckedIOException` khi duyệt.
7. Nếu kết quả cần ổn định thứ tự, dùng `sorted()`; nếu dữ liệu rất lớn, cân nhắc chi phí bộ nhớ.
8. Nếu cần bỏ qua nhánh hoặc điều khiển lỗi từng file, chuyển sang `walkFileTree`.
9. Không coi `BasicFileAttributes` là trạng thái bất biến; có thể phải kiểm tra lại khi truy cập file thật.
10. Kiểm thử với cây có thư mục rỗng, file có size 0, độ sâu biên, symlink và file biến mất trong khi tìm.

## 16. Tóm tắt

`Files.find(start, maxDepth, matcher, options...)` duyệt một cây đường dẫn và trả `Stream<Path>` gồm các mục khớp **`BiPredicate<Path, BasicFileAttributes>`**. `Path` mô tả tên/vị trí, còn `BasicFileAttributes` cung cấp loại mục, kích thước và thời gian. Dùng `maxDepth` để giới hạn độ sâu, chọn `FOLLOW_LINKS` có chủ đích và **luôn đóng stream**.

So với `Files.walk(...)`, `Files.find(...)` đặc biệt hữu ích khi matcher cần thuộc tính file sẵn có. Nhưng nếu phải cắt bỏ một cây con hoặc điều khiển lỗi theo từng nhánh, `Files.walkFileTree(...)` phù hợp hơn. Không nhầm việc lọc thuộc tính với xác thực nội dung hay kiểm soát quyền truy cập filesystem.

### Tham khảo chính thức

- [Java SE 17 — Files.find](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#find(java.nio.file.Path,int,java.util.function.BiPredicate,java.nio.file.FileVisitOption...))
- [Java SE 17 — BasicFileAttributes](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/BasicFileAttributes.html)
- [Java SE 17 — FileVisitOption](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileVisitOption.html)
- [Java SE 17 — Path](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="directory-tree-traversal-005">walkFileTree, FileVisitor và SimpleFileVisitor</a>

<details>
<summary>Xem chi tiết</summary>

# walkFileTree, FileVisitor và SimpleFileVisitor trong Java

> **Chủ đề:** Java Core → I/O → **Duyệt cây thư mục**. Bài này giải thích cách đi qua toàn bộ cây thư mục theo chiều sâu bằng `Files.walkFileTree`, cách bốn callback của `FileVisitor` phối hợp, hành vi mặc định của `SimpleFileVisitor`, các giá trị `FileVisitResult`, giới hạn độ sâu, symbolic links, xử lý lỗi, và những mẫu ứng dụng như tìm file, loại nhánh, thống kê và xóa cây. Các ví dụ độc lập chạy bằng Java 17; các API NIO.2 cốt lõi xuất hiện từ Java 7.

## 1. Khi nào cần đi qua cả cây thư mục?

Giả sử ứng dụng nhận thư mục `project`:

~~~text
project/
├── README.md
└── docs/
    ├── a.txt
    └── b.txt
~~~

Bạn muốn duyệt từng mục trong `project`, phân biệt file và thư mục, có thể bỏ qua `build` hay `target`, tính tổng byte, hoặc làm cleanup sau khi xử lý các tệp con. Việc chỉ gọi `Files.list(project)` **không đủ**: nó liệt kê các mục con trực tiếp, không tự duyệt sâu.

`Files.walkFileTree(...)` là API dành cho trường hợp cần **điều khiển quy trình duyệt**: thực hiện hành động trước/sau khi đi vào directory, xử lý lỗi từng entry, yêu cầu dừng sớm hoặc bỏ một nhánh. Thuật toán đi theo chiều sâu (**depth-first**), chứ không đảm bảo các directory ngang cấp được thăm theo thứ tự tên.

**Phân biệt tạo đường dẫn và đọc filesystem:** `Path` chỉ biểu diễn một vị trí; `walkFileTree` thực sự truy cập provider, đọc `BasicFileAttributes` và mở/đóng các thư mục đang duyệt. Nó có thể ném `IOException` hoặc các lỗi runtime khác. Không được coi cây file là snapshot bất biến: file có thể bị đổi/xóa trong khi đang duyệt.

## 2. Hai overload chính của Files.walkFileTree

~~~java
// Duyệt hết cây, không theo symbolic link mặc định:
Files.walkFileTree(start, visitor);

// Chọn option, maxDepth và visitor:
Files.walkFileTree(start, options, maxDepth, visitor);
~~~

Đây là **fragment**. `start` có kiểu `Path`, `visitor` là `FileVisitor<? super Path>`, `options` là `Set<FileVisitOption>` và method khai báo `throws IOException`.

| Tham số / kết quả | Ý nghĩa |
| --- | --- |
| `start` | Path gốc bắt đầu duyệt; **chính start cũng được thăm** theo quy tắc độ sâu. Có thể là file thường, directory hoặc symbolic link. |
| `visitor` | Object có callback xử lý directory/file/lỗi. Method gọi callback khi duyệt, không trả `Stream<Path>`. |
| `options` | Tập option, thông dụng là `FileVisitOption.FOLLOW_LINKS` để yêu cầu theo symbolic links. Tập rỗng = không follow links. |
| `maxDepth` | Số cấp directory tối đa tính từ start. `0` nghĩa là chỉ xử lý start; `Integer.MAX_VALUE` thường dùng cho toàn bộ độ sâu. Giá trị âm không hợp lệ. |
| Kiểu trả về | `Path` **start** khi duyệt hoàn thành hoặc callback trả `TERMINATE`; không phải số file đã duyệt. |
| Ngoại lệ | `IOException` từ callback có thể truyền về caller; callback trả `null` sẽ gây `NullPointerException`. |

Overload hai tham số tương đương về hành vi với:

~~~java
Files.walkFileTree(
    start,
    java.util.EnumSet.noneOf(FileVisitOption.class),
    Integer.MAX_VALUE,
    visitor);
~~~

Vì vậy **mặc định không theo symlink**, không hạn chế độ sâu theo giới hạn hợp lý do provider áp dụng, và thực hiện các callback tuần tự trong cùng lời gọi. `FileVisitOption.FOLLOW_LINKS` cần được chọn **chủ động**, không tự suy ra chỉ vì tên file trông giống directory.

### 2.1. Vòng đời tài nguyên

`walkFileTree` tự quản lý các directory stream nội bộ trong quá trình gọi. Caller **không nhận một Stream mở** để phải `close()` sau khi hàm trả về. Tuy nhiên bất kỳ resource **bạn tự mở trong callback** (file channel, input stream, output stream, network connection) vẫn cần owner riêng đóng.

Đây là khác biệt với `Files.walk(...)` hoặc `Files.find(...)`: chúng trả về `Stream<Path>` được thực hiện lazily, có thể giữ directory handles nên **phải** được đóng bằng try-with-resources.

## 3. FileVisitor: bốn callback bắt buộc

`FileVisitor<T>` là interface generic. Với file system Java NIO thường dùng `FileVisitor<Path>`. Nó yêu cầu bốn phương thức:

| Callback | Khi được gọi | Ý nghĩa tham số |
| --- | --- | --- |
| `preVisitDirectory(Path dir, BasicFileAttributes attrs)` | Trước khi đi qua các entry của directory đã mở thành công | `dir` là directory; `attrs` chứa thuộc tính cơ bản của directory. |
| `visitFile(Path file, BasicFileAttributes attrs)` | Khi gặp file được duyệt (bao gồm mục không phải directory sau khi xử lý quy tắc symlink); ở giới hạn độ sâu có thể nhận **directory như file** | `attrs` là thuộc tính provider đã lấy cho entry, không cần bắt buộc gọi lại `Files.readAttributes`. |
| `visitFileFailed(Path file, IOException exc)` | Không thể đọc thuộc tính hoặc không mở được directory/file theo quy tắc của walker; cũng dùng để báo cycle khi theo symlink | `exc` là nguyên nhân I/O gắn với entry đó. |
| `postVisitDirectory(Path dir, IOException exc)` | Sau khi thăm entries/descendants của directory đã được mở và kết thúc việc liệt kê | `exc == null` nếu vòng lặp liệt kê directory không bị lỗi I/O; khác null nếu có lỗi lúc iteration. |

Mỗi callback trả `FileVisitResult` và có thể khai báo `throws IOException`.

**Thứ tự chiều sâu điển hình** nếu không gặp lỗi hoặc skip:

~~~text
preVisitDirectory(root)
  visitFile(root/README.md)         [vị trí thực giữa các sibling không cố định]
  preVisitDirectory(root/docs)
    visitFile(root/docs/a.txt)
    visitFile(root/docs/b.txt)
  postVisitDirectory(root/docs)
postVisitDirectory(root)
~~~

Đây là **một thứ tự khả dĩ**; không được viết test bắt buộc `README.md` đứng trước `docs` hay `a.txt` đứng trước `b.txt` trên mọi filesystem. Nếu cần output xác định, tích lũy kết quả rồi sắp xếp theo quy tắc do ứng dụng định nghĩa.

### 3.1. Chi tiết về pre và post

`preVisitDirectory` chỉ được gọi cho directory walker **mở thành công** để đi sâu trong giới hạn độ sâu. Nếu nó trả `SKIP_SUBTREE` hoặc `SKIP_SIBLINGS`, walker không thăm nội dung của directory đó và **không gọi `postVisitDirectory` cho directory bị bỏ qua**.

`postVisitDirectory` phù hợp cho thao tác **sau khi đã xử lý con** như xóa directory rỗng, tổng hợp thống kê từ các con hoặc áp dụng thuộc tính directory sau khi copy. Nếu liệt kê directory gặp I/O error, tham số `exc` báo lỗi; callback phải quyết định ném ra hay tiếp tục. Đừng nhầm `exc` ở `postVisitDirectory` với mọi lỗi con: lỗi thăm một entry có thể đã được chuyển riêng vào `visitFileFailed`.

### 3.2. Bản thực thi interface trực tiếp

Nếu triển khai `FileVisitor<Path>` trực tiếp, bạn phải cung cấp **cả bốn phương thức**, kể cả khi chỉ muốn làm việc với file:

~~~java
// Fragment lớp; cần import Path, FileVisitor, FileVisitResult,
// BasicFileAttributes, IOException.
final class CountingVisitor implements FileVisitor<Path> {
    long files;

    @Override
    public FileVisitResult preVisitDirectory(
            Path dir, BasicFileAttributes attrs) {
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(
            Path file, BasicFileAttributes attrs) {
        if (attrs.isRegularFile()) {
            files++;
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(
            Path file, IOException exc) throws IOException {
        throw exc;
    }

    @Override
    public FileVisitResult postVisitDirectory(
            Path dir, IOException exc) throws IOException {
        if (exc != null) {
            throw exc;
        }
        return FileVisitResult.CONTINUE;
    }
}
~~~

Đây là **fragment có đủ method interface** nhưng chưa có `main`. Điểm quan trọng là method nào cũng trả một kết quả hợp lệ; `IOException` từ callback làm quá trình duyệt dừng và được truyền lên caller nếu không được xử lý.

## 4. SimpleFileVisitor: cách viết ngắn gọn hơn

`SimpleFileVisitor<T>` **triển khai sẵn** `FileVisitor<T>`, cho phép override chỉ callback bạn cần:

- `preVisitDirectory` mặc định trả `CONTINUE`.
- `visitFile` mặc định trả `CONTINUE`.
- `visitFileFailed` mặc định **ném lại `IOException`** đã nhận.
- `postVisitDirectory` mặc định trả `CONTINUE` nếu `exc == null`; nếu có lỗi liệt kê directory, **ném lại `exc`**.

Do đó, “Simple” **không có nghĩa nuốt mọi lỗi**. Nếu muốn xử lý một file không đọc được rồi bỏ qua, phải chủ động override `visitFileFailed` và nêu cách log/đo số lỗi. Nếu muốn theo dõi `postVisitDirectory` mà vẫn xử lý mặc định các lỗi, có thể gọi `super.postVisitDirectory(dir, exc)`.

Ví dụ fragment đếm số regular files:

~~~java
final java.util.concurrent.atomic.AtomicLong count =
        new java.util.concurrent.atomic.AtomicLong();

Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
    @Override
    public FileVisitResult visitFile(
            Path file, BasicFileAttributes attrs) {
        if (attrs.isRegularFile()) {
            count.incrementAndGet();
        }
        return FileVisitResult.CONTINUE;
    }
});
~~~

Mã fragment nằm trong method xử lý `IOException`, cần import các lớp liên quan. `AtomicLong` **không bắt buộc** với walker thông thường gọi callback tuần tự; nó được dùng ở đây để cho thấy biến mutable từ anonymous class có thể được cập nhật. Trong code đơn giản, có thể dùng field trong lớp visitor để lưu count.

## 5. FileVisitResult điều khiển chính xác hướng duyệt

`FileVisitResult` có bốn hằng:

| Giá trị | Hành vi |
| --- | --- |
| `CONTINUE` | Tiếp tục duyệt các entry còn lại theo depth-first. |
| `TERMINATE` | Dừng **toàn bộ** hành trình ngay; không còn callback file-walk nào khác sau kết quả này. |
| `SKIP_SUBTREE` | Khi **preVisitDirectory** trả về: không duyệt các con của directory hiện tại; các sibling khác vẫn có thể duyệt. |
| `SKIP_SIBLINGS` | Bỏ qua các sibling **chưa thăm** trong directory cha; nếu trả từ preVisitDirectory thì cả subtree của directory hiện tại cũng bị bỏ qua. |

Hãy hiểu “sibling” theo **cùng thư mục cha** của entry ở thời điểm callback, không phải “mọi mục khác trên toàn filesystem”.

### 5.1. Những ngữ cảnh dễ nhầm

- `preVisitDirectory(dir) -> SKIP_SUBTREE`: **không** thăm entry con, không có `postVisitDirectory(dir)`. Sau đó vẫn có thể tiếp tục các directory ngang hàng.
- `preVisitDirectory(dir) -> SKIP_SIBLINGS`: bỏ directory này cùng các sibling còn lại, không có post của dir vừa bị skip.
- `visitFile(file) -> SKIP_SIBLINGS`: không thăm thêm các sibling của file trong directory cha; iterator hiện tại kết thúc sớm.
- `postVisitDirectory(dir) -> SKIP_SIBLINGS`: sau khi xong directory hiện tại, bỏ các sibling cùng cấp chưa duyệt.
- `visitFile(file) -> SKIP_SUBTREE`: vì file không có subtree, kết quả được xử lý tương tự `CONTINUE` theo hợp đồng.
- `TERMINATE` từ một callback: thoát traversal toàn cây; đừng kỳ vọng `postVisitDirectory` của mọi ancestor được gọi như `finally`. Nếu cần giải phóng resource riêng, dùng try/finally hoặc try-with-resources bên ngoài callback.
- Callback **không được trả `null`**: walker ném `NullPointerException`.

Ví dụ tìm file đầu tiên đáp ứng điều kiện:

~~~java
// Fragment: biến target do caller xác định, callback gọi trong method
// xử lý IOException.
Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
    @Override
    public FileVisitResult visitFile(
            Path file, BasicFileAttributes attrs) {
        if (attrs.isRegularFile()
                && file.getFileName().toString().equals(target)) {
            System.out.println("Found: " + file);
            return FileVisitResult.TERMINATE;
        }
        return FileVisitResult.CONTINUE;
    }
});
~~~

Do thứ tự duyệt sibling **không xác định**, nếu có nhiều file trùng tên tại các nhánh khác nhau, kết quả “đầu tiên” **không có nghĩa** đường dẫn ngắn nhất, gần nhất hoặc nhỏ nhất theo bảng chữ cái. Nếu cần lựa chọn deterministic, phải xác định quy tắc trước và cân nhắc thu thập/sắp xếp.

## 6. Ví dụ chạy được 1: duyệt và liệt kê cây thư mục

Tạo file **`TreeWalkDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TreeWalkDemo {
    // Chỉ dọn cây tạm do chính demo tạo.
    static void deleteTree(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(
                    Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(
                    Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("tree-demo-");
        try {
            Files.createDirectory(root.resolve("docs"));
            Files.writeString(root.resolve("README.md"),
                    "root", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("docs/a.txt"),
                    "a", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("docs/b.txt"),
                    "b", StandardCharsets.UTF_8);

            List<String> found = new ArrayList<>();

            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(
                        Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(root)) {
                        found.add("D:" + dir.getFileName());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(
                        Path file, BasicFileAttributes attrs) {
                    found.add("F:" + file.getFileName());
                    return FileVisitResult.CONTINUE;
                }
            });

            // Hệ thống tệp không bắt buộc trả sibling theo tên:
            // sort sau khi thu thập để stdout ổn định.
            Collections.sort(found);
            found.forEach(System.out::println);
        } finally {
            deleteTree(root);
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 TreeWalkDemo.java
java TreeWalkDemo
~~~

**Stdout đã kiểm tra trên JDK:**

~~~text
D:docs
F:README.md
F:a.txt
F:b.txt
~~~

**Giải thích:** `root` là thư mục tạm do chương trình mới tạo, bên trong có `docs` và ba file. `preVisitDirectory` thêm directory trừ root; `visitFile` thêm file; `postVisitDirectory` không cần override trong vòng duyệt liệt kê vì mặc định của `SimpleFileVisitor` đã tiếp tục và báo lỗi khi cần. Sau khi xử lý, khối `finally` gọi một visitor **khác** để xóa toàn bộ cây tạm theo thứ tự an toàn: file trước, directory sau.

Ví dụ ghi `dir.getFileName()` và `file.getFileName()` với các tên độc lập, sau đó sort để không phụ thuộc ký tự separator Windows/Unix hoặc thứ tự duyệt directory. Trong bài toán thật, nếu có hai file cùng basename trong các nhánh khác nhau, chỉ ghi filename sẽ gây trùng tên; nên dùng `root.relativize(file)` hoặc một cấu trúc lưu đầy đủ Path.

**Cảnh báo an toàn:** phương thức `deleteTree` trong demo thật sự xóa dữ liệu. Chỉ gọi nó với cây **tạm do demo vừa tạo**. Không chuyển phương thức này sang một path do người dùng cung cấp mà chưa bổ sung xác nhận phạm vi, quyền, symlink và chính sách xóa dữ liệu.

## 7. Ví dụ chạy được 2: bỏ qua một nhánh thư mục

Một crawler không nên mở thư mục không cần thiết, ví dụ `node_modules`, `build` hoặc vùng dữ liệu nhạy cảm. Để cắt cả subtree, dùng `SKIP_SUBTREE` trong `preVisitDirectory`.

Tạo file **`SkipTreeDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicInteger;

public class SkipTreeDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("skip-demo-");
        try {
            Files.createDirectory(root.resolve("public"));
            Files.createDirectory(root.resolve("private"));
            Files.writeString(root.resolve("public/hello.txt"), "ok");
            Files.writeString(root.resolve("private/secret.txt"), "skip");
            Files.writeString(root.resolve("root.txt"), "ok");

            AtomicInteger files = new AtomicInteger();
            AtomicInteger skipped = new AtomicInteger();

            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(
                        Path dir, BasicFileAttributes attrs) {
                    if (dir.getFileName().toString().equals("private")) {
                        skipped.incrementAndGet();
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(
                        Path file, BasicFileAttributes attrs) {
                    files.incrementAndGet();
                    return FileVisitResult.CONTINUE;
                }
            });

            System.out.println("files=" + files.get());
            System.out.println("skipped=" + skipped.get());
        } finally {
            TreeWalkDemo.deleteTree(root);
        }
    }
}
~~~

Để chạy **ví dụ này**, đặt cả `TreeWalkDemo.java` của mục trước và `SkipTreeDemo.java` trong **cùng thư mục** để tái sử dụng helper xóa cây:

~~~bash
javac --release 17 TreeWalkDemo.java SkipTreeDemo.java
java SkipTreeDemo
~~~

Stdout:

~~~text
files=2
skipped=1
~~~

`private/secret.txt` **không được thăm** do callback của chính thư mục `private` trả `SKIP_SUBTREE`. `public/hello.txt` và `root.txt` được tính: hai file. `skipped=1` vì có đúng một directory tên `private` trong cây giả lập.

**Lưu ý biên dịch:** `SkipTreeDemo.java` chủ động dùng helper package-private static `TreeWalkDemo.deleteTree` từ ví dụ trước. Nếu muốn file đứng độc lập hoàn toàn, copy helper đó vào cùng class hoặc thay bằng phương pháp cleanup phù hợp. Hai ví dụ cùng được cung cấp đầy đủ trong bài và lệnh biên dịch trên bao gồm cả hai nguồn.

Một mẫu thực tế thường so sánh **đường dẫn relative** hoặc cây được phép thay vì chỉ so tên: hai thư mục trùng tên `private` ở vị trí khác nhau có thể cần policy khác. Nếu bỏ qua directory, walker không đọc các file dưới đó; đừng gọi `Files.walkFileTree` trên directory đó lần thứ hai rồi vô hiệu hóa mục đích skip.

## 8. maxDepth: cấp 0, cấp 1 và directory ở giới hạn

Với overload:

~~~java
Files.walkFileTree(
    root,
    EnumSet.noneOf(FileVisitOption.class),
    1,
    visitor);
~~~

`maxDepth=0` chỉ thăm `root`; `maxDepth=1` xử lý `root` và các entry trực tiếp bên dưới, **không duyệt sâu hơn**. Một chi tiết bất ngờ từ Javadoc: entry là **directory ở chính `maxDepth` được chuyển tới `visitFile`**, không mở sâu và không gọi cặp `preVisitDirectory/postVisitDirectory` cho directory đó.

Ví dụ cây:

~~~text
root/                  độ sâu 0
└── child/             độ sâu 1
    └── note.txt       độ sâu 2
~~~

Khi duyệt `maxDepth=1`, callback `preVisitDirectory(root)` có thể được gọi, nhưng `child` được báo qua `visitFile(child, attrs)`; `note.txt` không được thăm. Điều này giải thích vì sao mã `visitFile` nên kiểm tra `attrs.isRegularFile()` thay vì mặc định mọi `file` nhận được ở đó đều là regular file.

### 8.1. Ví dụ chạy được 3: quan sát directory tại maxDepth

Tạo file **`DepthDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class DepthDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("depth-");
        try {
            Files.createDirectory(root.resolve("child"));
            Files.writeString(root.resolve("child/note.txt"), "x");

            List<String> found = new ArrayList<>();
            Files.walkFileTree(
                    root, EnumSet.noneOf(FileVisitOption.class), 1,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult preVisitDirectory(
                                Path dir, BasicFileAttributes attrs) {
                            found.add("D:" + dir.getFileName());
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(
                                Path file, BasicFileAttributes attrs) {
                            found.add("F:" + file.getFileName());
                            return FileVisitResult.CONTINUE;
                        }
                    });

            Collections.sort(found);
            System.out.println("depthOneEntries=" + found.size());
            System.out.println("childAsFile="
                    + found.stream().anyMatch(s -> s.equals("F:child")));
        } finally {
            TreeWalkDemo.deleteTree(root);
        }
    }
}
~~~

Biên dịch cùng helper:

~~~bash
javac --release 17 TreeWalkDemo.java DepthDemo.java
java DepthDemo
~~~

Stdout:

~~~text
depthOneEntries=2
childAsFile=true
~~~

Tại `maxDepth=1`, callback có một entry cho root (`preVisitDirectory`) và một entry cho child (`visitFile`), tổng cộng 2; file `child/note.txt` sâu 2 nên không được duyệt. `Collections.sort` không cần thiết cho phép đếm, nhưng giúp minh họa cách chuẩn hóa danh sách khi muốn kiểm thử event names.

Nếu `maxDepth` âm, `Files.walkFileTree` ném `IllegalArgumentException`. Trong ứng dụng cho người dùng nhập độ sâu, hãy validate trước và đặt giới hạn hợp lý để tránh traversal khổng lồ vô ý.

## 9. Symbolic links: mặc định không follow

Mặc định `Files.walkFileTree(start, visitor)` **không tự đi theo symbolic links**. Nếu một entry bên trong cây là symbolic link trỏ tới thư mục khác, entry đó thường được đưa tới `visitFile` với thuộc tính của chính link. Điều này hữu ích khi duyệt file để không vượt khỏi root một cách vô ý.

Muốn follow links:

~~~java
// Fragment: root đã xác định, method throws IOException.
Files.walkFileTree(
        root,
        java.util.EnumSet.of(FileVisitOption.FOLLOW_LINKS),
        Integer.MAX_VALUE,
        visitor);
~~~

Nhưng **FOLLOW_LINKS cần chính sách an toàn**:

- Một symbolic link có thể dẫn **ra ngoài** cây path ban đầu; `root.relativize(path)` đơn thuần không chứng minh dữ liệu thật nằm dưới root.
- Nhiều link có thể dẫn tới cùng file/directory, dẫn đến xử lý trùng lặp. Nếu đếm kích thước, thống kê có thể bị tăng gấp.
- Khi follow links, walker phát hiện cycle đường dẫn tới ancestor bằng file key hoặc `Files.isSameFile` theo provider. Nếu phát hiện vòng, `visitFileFailed` nhận `FileSystemLoopException`.
- Không phải mọi symbolic link trỏ tới mục tiêu hợp lệ. Nếu không đọc được attrs của target, walker có thể thử attrs của chính link rồi chuyển link qua `visitFile`; do đó `attrs` không nhất thiết mô tả target trong mọi trường hợp.
- Đặt `maxDepth` vẫn giới hạn số mức, nhưng **không** giới hạn số lượng entry tại mỗi mức. Cần thêm giới hạn thời gian/số file/bytes nếu input không đáng tin.

### 9.1. Xử lý cycle có chủ đích

~~~java
// Fragment: visitor dùng trong walkFileTree(... FOLLOW_LINKS ...).
@Override
public FileVisitResult visitFileFailed(
        Path file, IOException exc) throws IOException {
    if (exc instanceof java.nio.file.FileSystemLoopException) {
        // Báo hoặc đếm vòng symlink mà không nuốt các lỗi khác.
        System.err.println("Cycle at: " + file);
        return FileVisitResult.CONTINUE;
    }
    throw exc;
}
~~~

Đây là **method fragment** bên trong subclass `SimpleFileVisitor<Path>`, không phải chương trình độc lập. Trả `CONTINUE` ở đây có nghĩa chính sách cho phép bỏ qua entry bị cycle, không đảm bảo thông tin traversal hoàn chỉnh. Nếu bài toán yêu cầu snapshot/traversal đầy đủ, nên ném lỗi để caller biết kết quả thiếu.

## 10. Xử lý lỗi trong visitor một cách có kiểm soát

### 10.1. visitFileFailed không phải postVisitDirectory

Nếu walker **không thể đọc thuộc tính** một path, hoặc không thể **mở directory** đó, nó thường gọi `visitFileFailed(path, exc)`. Nếu đã **mở directory** nhưng gặp lỗi khi **liệt kê các entry**, walker đóng directory và gọi `postVisitDirectory(dir, exc)`.

Đừng gom hai trường hợp thành một callback: chúng xảy ra ở giai đoạn khác nhau và có thể dẫn tới khả năng phục hồi khác nhau.

Hành vi mặc định của `SimpleFileVisitor` là **ném lại** I/O error, nên traversal có thể dừng ngay khi gặp thư mục không thể đọc. Nếu nghiệp vụ cho phép “best effort”, bạn có thể ghi nhận lỗi và tiếp tục:

~~~java
// Method fragment bên trong SimpleFileVisitor<Path>.
@Override
public FileVisitResult visitFileFailed(
        Path path, IOException exc) {
    errors.add(path); // errors là collection được caller quản lý.
    return FileVisitResult.CONTINUE;
}
~~~

Nếu không muốn mất nguyên nhân, lưu cả `Path` và `IOException` hoặc log lỗi theo chính sách. Ví dụ `errors.add(path)` là **fragment**, chưa định nghĩa `errors`; trong production cần collection phù hợp với yêu cầu bảo mật và bộ nhớ.

### 10.2. Lỗi liệt kê trong postVisitDirectory

~~~java
@Override
public FileVisitResult postVisitDirectory(
        Path dir, IOException exc) throws IOException {
    if (exc != null) {
        throw exc;   // Fail fast, không báo thành công khi dữ liệu thiếu.
    }
    return FileVisitResult.CONTINUE;
}
~~~

Đây là method fragment. Nếu muốn tiếp tục trong một số lỗi directory, có thể log vào báo cáo của ứng dụng rồi `return CONTINUE`. Tuy nhiên khi đang **xóa/copy** cây, nuốt lỗi rồi giả định directory đã xử lý hết là nguy hiểm: có thể còn file con chưa thấy, thao tác xóa directory sau đó sẽ thất bại.

### 10.3. Ngoại lệ ném trong callback

Nếu `visitFile` ném `IOException` trực tiếp, `walkFileTree` kết thúc và truyền exception ra caller; nó **không mặc nhiên** bắt exception đó rồi gọi `visitFileFailed` cho chính callback đã lỗi. `visitFileFailed` chủ yếu cho lỗi do walker gặp khi truy cập entry theo hợp đồng. Nếu code tự mở/đọc nội dung file trong `visitFile`, bạn cần xác định **có catch/continue cục bộ hay propagate**.

Lỗi runtime hoặc `Error` phát sinh trong callback cũng không được tự động chuyển thành `FileVisitResult.CONTINUE`. Và nếu một callback trả `TERMINATE`, code bên ngoài không nên coi điều đó giống một exception có rollback: các side effect đã thực hiện vẫn còn.

## 11. Xóa cây bằng walkFileTree: vì sao cần postVisitDirectory?

Một directory chỉ xóa được bình thường sau khi các entry bên trong đã biến mất. Vì walker đi theo depth-first, cặp callback phù hợp là:

- `visitFile`: xóa file (kể cả symbolic link mặc định không follow).
- `postVisitDirectory`: sau khi các con được thăm/xóa, xóa directory đó.
- `visitFileFailed`: báo lỗi thay vì im lặng bỏ qua file không kiểm tra được.
- `preVisitDirectory`: thường giữ mặc định `CONTINUE`, trừ khi chính sách muốn từ chối nhánh.

Đây là chính thứ tự của helper `deleteTree` trong `TreeWalkDemo`. Nếu đảo thành xóa directory ở `preVisitDirectory`, `Files.delete(dir)` sẽ có nguy cơ `DirectoryNotEmptyException`.

**Mẫu dùng trong sản phẩm cần cẩn trọng hơn demo**, vì xóa là thao tác phá hủy:

1. Chỉ nhận một root rõ ràng do caller **được ủy quyền** chọn; chuẩn hóa và xác nhận phạm vi trước thao tác.
2. Không follow links khi xóa thông thường, để xóa **link itself** thay vì đi sâu vào target bên ngoài root.
3. Ghi nhận lỗi từng entry; không coi kết quả traversal lỗi là “đã xóa sạch”.
4. Nếu cây thư mục có thể bị actor khác thay đổi đồng thời, `Files.delete` qua Path có thể có **race**; một số provider hỗ trợ `SecureDirectoryStream` để thao tác tương đối với handle và giảm một số rủi ro, nhưng không phải provider nào cũng có.
5. Nếu cần thùng rác, backup hoặc khả năng phục hồi, **đừng** dùng đoạn demo gọi `Files.delete` trực tiếp làm API nghiệp vụ.
6. Đừng gọi traversal xóa trên filesystem root, home directory, thư mục dự án thật hoặc path lấy thẳng từ input không tin cậy.

### 11.1. Vì sao callback sau directory đặc biệt có giá trị?

Không chỉ xóa: khi **copy directory tree**, bạn thường tạo directory đích ở `preVisitDirectory`, copy file trong `visitFile`, rồi áp dụng một số metadata/permission cuối cùng của directory ở `postVisitDirectory` để tránh việc copy nội dung làm thời gian sửa đổi directory thay đổi. Đây là mẫu thao tác theo **thứ tự phụ thuộc cha/con** mà `Files.walk(...)` kèm `forEach` đơn giản không biểu diễn tự nhiên bằng callback có cấu trúc.

## 12. Đếm kích thước, tìm file và độ tin cậy của attrs

`BasicFileAttributes` cung cấp một số phương thức hữu ích:

| Thuộc tính | Cách dùng |
| --- | --- |
| `isRegularFile()` | Đếm/xử lý file thường; tránh cộng size của directory hoặc link theo nhầm nghĩa. |
| `isDirectory()` | Kiểm tra entry có thuộc tính directory; hữu ích tại `maxDepth` khi directory đi qua `visitFile`. |
| `isSymbolicLink()` | Phân biệt symlink khi không follow hoặc đọc attrs của chính link. |
| `size()` | Kích thước entry theo **byte** mà provider báo; không phải số ký tự text hoặc dung lượng disk được cấp. |
| `lastModifiedTime()` | Lọc theo timestamp nếu ứng dụng cần, có thể thay đổi đồng thời. |
| `fileKey()` | Id do provider cấp để có thể nhận dạng file, không đảm bảo khác null hoặc ổn định trên mọi provider. |

Trong callback, dùng `attrs` đã được walker cung cấp thay vì gọi `Files.readAttributes` lần nữa cho cùng entry nếu không có lý do xác thực mới. Việc gọi lại tạo thêm I/O và có thể trả trạng thái khác do concurrent modifications.

Ví dụ fragment đếm byte regular files:

~~~java
// Fragment: code nằm trong class extends SimpleFileVisitor<Path>.
private long totalBytes;

@Override
public FileVisitResult visitFile(
        Path file, BasicFileAttributes attrs) {
    if (attrs.isRegularFile()) {
        totalBytes += attrs.size();
    }
    return FileVisitResult.CONTINUE;
}
~~~

`totalBytes` là tổng **logic file sizes** theo những entry được thăm, **không phải** lượng disk block đã cấp hay tổng byte không trùng lặp trên storage. Hard link, symlink-following, sparse files và dữ liệu đang bị sửa đồng thời làm diễn giải “dung lượng thư mục” phức tạp. Nếu số lượng file cực lớn, `long` về lý thuyết có thể overflow; nếu yêu cầu kiểm toán chính xác, cần chính sách xử lý cộng tràn và snapshot/lock ở cấp filesystem phù hợp.

## 13. So sánh với Files.walk, Files.find và Files.list

| API | Độ sâu / hướng | Phong cách | Cách dọn |
| --- | --- | --- | --- |
| `Files.list(dir)` | Một cấp trực tiếp | `Stream<Path>` từ một directory | **Đóng Stream** bằng try-with-resources |
| `Files.walk(start)` | Đệ quy theo chiều sâu | `Stream<Path>` lazy | **Đóng Stream**; I/O trong quá trình tiêu thụ có thể thành `UncheckedIOException` |
| `Files.walk(start, maxDepth)` | Đệ quy giới hạn độ sâu | `Stream<Path>` lazy | **Đóng Stream** |
| `Files.find(start, maxDepth, matcher, options...)` | Đệ quy, có predicate nhận path + attrs | `Stream<Path>` lazy | **Đóng Stream** |
| `Files.walkFileTree(start, visitor)` | Đệ quy depth-first không follow link mặc định | **Callback** pre/visit/fail/post; điều khiển skip/terminate | Walker tự đóng directory handles nội bộ |
| `Files.walkFileTree(start, options, depth, visitor)` | Có FOLLOW_LINKS và độ sâu tùy chọn | Callback có thể báo lỗi từng entry | Không có Stream trả về để đóng |

Nếu chỉ cần danh sách file đã lọc, `Files.walk(...)` + `filter` có thể gọn. Nếu cần **skip toàn bộ subtree theo nội dung directory**, cần callback `preVisitDirectory`. Nếu cần **xóa directory sau khi xóa hết con**, `walkFileTree` thường trực tiếp và rõ ràng hơn. Nếu dùng `Files.walk(...)`, cần ghi nhớ `Stream` lazy và giải phóng resource bằng:

~~~java
// Fragment: caller xử lý IOException từ Files.walk.
try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
    paths.filter(Files::isRegularFile).forEach(System.out::println);
}
~~~

Đây là fragment: trong ngữ cảnh tương ứng và với filesystem bị thay đổi đồng thời, vẫn có thể gặp lỗi khi stream đang được tiêu thụ. `Files.walk` stream chỉ đóng khi `close()` hoặc try-with-resources kết thúc, **không** tự đóng khi terminal operation như `count()`/`forEach()` hoàn tất.

## 14. Bài toán thứ tự và tính nhất quán

`walkFileTree` có thứ tự **depth-first**, nhưng **không đảm bảo thứ tự các sibling**. Hệ thống file khác nhau, trạng thái thư mục khác nhau, hoặc việc tạo/xóa file đồng thời có thể làm thứ tự khác nhau.

**Nếu cần output deterministic**:

- Thu thập `Path` hoặc dữ liệu thống kê, rồi sắp xếp sau cùng.
- Dùng `root.relativize(path)` để có vị trí relative; lưu ý `toString()` thể hiện separator theo provider, nên khi serialize cho hệ thống khác cần format được định nghĩa rõ.
- Không dựa vào lần duyệt đầu tiên để chọn file “nhỏ nhất”, “mới nhất” hoặc “đầu tiên theo tên” trừ khi duyệt đủ và so sánh có chủ đích.
- Nếu quá nhiều entry không thể gom RAM, cần một chiến lược directory enumeration/sort riêng hoặc external sort; `walkFileTree` không cung cấp tùy chọn sắp xếp sibling sẵn có.
- `Path`, `BasicFileAttributes` và nội dung tệp không là snapshot đồng nhất; kết quả đếm/chọn có thể bị ảnh hưởng bởi file đang sửa.

## 15. Hiệu năng và giới hạn khi duyệt cây lớn

Một cây hàng triệu file có thể tiêu tốn thời gian đáng kể do truy cập metadata và mở thư mục. Hãy tránh các sai lầm thường gặp:

1. **Không giới hạn độ sâu khi chỉ cần một vài cấp**: dùng overload `maxDepth` phù hợp để tiết kiệm I/O.
2. **Vào directory không liên quan rồi mới bỏ file bên trong**: dùng `SKIP_SUBTREE` sớm trong `preVisitDirectory`.
3. **Mở file để kiểm tra nội dung khi tên/metadata đủ lọc**: tránh I/O thừa.
4. **Gọi `Files.size(path)` cho mọi file dù đã có `attrs.size()`**: có thể tăng số lần truy cập metadata.
5. **Thu tất cả Path vào List vô hạn**: xử lý streaming theo callback, chỉ giữ trạng thái tổng hợp nếu có thể.
6. **Bỏ qua lỗi để nhận báo cáo “đã duyệt hết”**: phân biệt đếm thành công, lỗi và nhánh bị bỏ.
7. **Chạy đồng thời nhiều walker trên cùng directory**: có thể tạo áp lực I/O và làm thứ tự/biến động phức tạp; chỉ làm nếu workload cần.
8. **FOLLOW_LINKS không kiểm soát**: dễ vượt boundary hoặc đếm trùng, tăng độ sâu và số file.
9. **Giữ resource riêng mở qua nhiều callback**: có thể cạn file descriptors; mở khi cần và đóng trong callback.
10. **Trông chờ callback được gọi trên thread khác**: `walkFileTree` là API traversal đồng bộ; parallelism phải do ứng dụng tổ chức riêng và tự bảo vệ state.

Nếu cần giới hạn **số lượng entry**, callback có thể tăng count và `return TERMINATE` khi đạt ngưỡng; tuy nhiên dừng sớm có nghĩa dữ liệu chưa hoàn chỉnh. Ghi rõ báo cáo bị cắt theo policy thay vì trả kết quả “đã duyệt xong”.

## 16. An toàn khi duyệt path do người dùng cung cấp

Ứng dụng sao lưu hoặc tìm kiếm có thể nhận root từ CLI/API. Duyệt một cây không tự là lỗ hổng, nhưng nếu callback **đọc nội dung bí mật, copy ra ngoài, xóa tệp hoặc thay quyền**, giới hạn phạm vi rất quan trọng.

- Chỉ chọn root được phép theo quyền truy cập và chính sách.
- Không dùng `normalize()` hay kiểm tra `startsWith(root)` như hàng rào duy nhất khi symlink và concurrent changes có thể tham gia.
- Với `FOLLOW_LINKS`, cần quyết định xử lý symlink trỏ ra ngoài root; symlink cycles được phát hiện nhưng không thay thế policy.
- Không ghi log đường dẫn/tên người dùng nhạy cảm không cần thiết.
- Dù không follow link, directory tree vẫn có thể bị đổi **giữa** lúc attrs được đọc và thao tác `Files.read`/`delete` xảy ra. Cần xét TOCTOU và quyền filesystem.
- Với xóa/copy, nên có dry-run, backup, xác thực root, xác nhận người dùng khi có thể; tránh biến ví dụ traversal thành xóa dữ liệu thật.
- Nếu caller cần biết kết quả hoàn chỉnh, không “thành công” khi `visitFileFailed` ghi lỗi rồi `CONTINUE`; trả thống kê gồm count lỗi và chính sách rõ ràng.

## 17. Các lỗi điển hình

1. **Dùng `Files.list` nhưng mong duyệt đệ quy.** `list` chỉ trả con trực tiếp.
2. **Cho rằng sibling được duyệt theo alphabet.** Không có cam kết như vậy.
3. **Dùng `SimpleFileVisitor` nhưng nghĩ lỗi I/O tự bị bỏ qua.** `visitFileFailed` mặc định ném lại exception.
4. **Override `visitFileFailed` rồi luôn `CONTINUE` mà không báo lỗi.** Kết quả im lặng thiếu dữ liệu.
5. **Cho rằng mọi directory đều đi qua `preVisitDirectory`.** Ở maxDepth, directory có thể đi qua `visitFile`.
6. **Đếm mọi callback `visitFile` là regular file.** Cần `attrs.isRegularFile()`.
7. **Dùng `SKIP_SUBTREE` trong `visitFile` để bỏ directory cha.** Nó chỉ hữu hiệu để cắt nhánh khi trả từ `preVisitDirectory`.
8. **Dùng `TERMINATE` rồi mong postVisitDirectory vẫn được gọi cho mọi ancestor.** Không có hợp đồng cleanup callback kiểu finally.
9. **Quên xử lý `postVisitDirectory(exc)`.** Lỗi liệt kê thư mục có thể bị bỏ qua hoặc báo cáo sai.
10. **Dùng `postVisitDirectory` để giả định mọi file con được xử lý thành công.** Có thể đã có callback lỗi được cho phép bỏ qua.
11. **Dùng `FOLLOW_LINKS` cho cây không tin cậy mà không giới hạn/kiểm tra.** Có thể đi ra ngoài root, gặp cycle hoặc trùng file.
12. **Xóa directory trong `preVisitDirectory`.** Chưa xóa con nên directory thường không rỗng.
13. **Gom tất cả file vào RAM dù chỉ cần đếm.** Với cây lớn, giữ state tổng hợp sẽ hiệu quả hơn.
14. **Giữ input/output streams mở trong nhiều callback.** Không quản lý resource tốt gây leak.
15. **Cho rằng walker khóa filesystem, không ai sửa được cây.** Filesystem biến động đồng thời là tình huống phải tính tới.
16. **Đóng resource mà walker tự mở bằng cách đóng file Path.** `Path` không có `close()`; walker quản lý directory handles nội bộ.
17. **Dùng `Files.walk` rồi quên đóng Stream.** Đây là API khác với `walkFileTree`.
18. **Gọi `Files.walkFileTree` với `maxDepth=-1`.** Bị `IllegalArgumentException`.
19. **Callback trả `null`.** Bị `NullPointerException` thay vì tiếp tục.
20. **Test với file thật và xóa nhầm dữ liệu.** Ví dụ xóa phải chỉ thao tác trên cây temp tạo trong test.

## 18. Câu hỏi tự kiểm tra

1. `Files.walkFileTree(root, visitor)` có tự follow symbolic links không?
2. Bốn method của `FileVisitor<Path>` là gì? Method nào xảy ra sau khi các con của directory đã xử lý?
3. `SimpleFileVisitor` làm gì mặc định khi không mở được file?
4. `SKIP_SUBTREE` và `SKIP_SIBLINGS` khác nhau thế nào khi trả từ `preVisitDirectory`?
5. `TERMINATE` có đảm bảo gọi `postVisitDirectory` cho mọi directory ancestor để cleanup không?
6. Với `maxDepth=1`, một directory con trực tiếp được báo qua callback nào?
7. Khi nào `visitFileFailed` nhận `FileSystemLoopException`?
8. Nếu chỉ muốn đếm regular files, cần kiểm tra `attrs` bằng method nào?
9. Vì sao xóa directory nên đặt trong `postVisitDirectory`?
10. `Files.walk(root)` trả về gì và có cần đóng không?
11. Có thể dựa vào thứ tự `visitFile` để lấy file nhỏ nhất theo tên không?
12. Khi `postVisitDirectory(dir, exc)` có `exc != null`, điều đó nói lên điều gì?
13. Trong walker, callback tự mở `Files.newInputStream(file)` thì ai đóng input stream?
14. Nếu callback trả `CONTINUE` sau lỗi đọc file, báo cáo thống kê có nên ghi nhận lỗi không?
15. Khi dùng `FOLLOW_LINKS` và root do user cung cấp, chỉ `normalize()` đã đủ an toàn chưa?

**Đáp án gợi ý:**

1. Không, mặc định không follow.
2. `preVisitDirectory`, `visitFile`, `visitFileFailed`, `postVisitDirectory`; method sau khi đi con là `postVisitDirectory` khi directory được mở/duyệt.
3. Ném lại `IOException`.
4. `SKIP_SUBTREE` bỏ subtree hiện tại nhưng có thể đi sibling; `SKIP_SIBLINGS` còn bỏ các sibling chưa thăm.
5. Không; dùng finally/try-with-resources cho resource tự quản lý.
6. Thường là `visitFile` ở giới hạn maxDepth.
7. Khi FOLLOW_LINKS làm walker phát hiện vòng link về ancestor.
8. `attrs.isRegularFile()`.
9. Phải xử lý/xóa file và thư mục con trước khi directory cha có thể rỗng để xóa.
10. Trả `Stream<Path>` lazy cần đóng.
11. Không; thứ tự sibling không được đảm bảo.
12. Lỗi I/O xảy ra khi liệt kê directory (không đồng nghĩa mọi lỗi file con đều nằm trong đó).
13. Chính callback hoặc owner ứng dụng, bằng try-with-resources.
14. Có, để caller biết traversal chỉ best effort và kết quả có thể thiếu.
15. Chưa, còn symlink, quyền, traversal ngoài root và TOCTOU.

## 19. Tài liệu chính thức

- [Oracle Java SE 21 — Files.walkFileTree](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html): overload, độ sâu, FOLLOW_LINKS, lifecycle, xử lý lỗi và quy tắc callback.
- [Oracle Java SE 21 — FileVisitor](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/FileVisitor.html): bốn callback và hợp đồng `FileVisitResult`.
- [Oracle Java SE 21 — SimpleFileVisitor](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/SimpleFileVisitor.html): hành vi mặc định và exception propagation.
- [Oracle Java SE 21 — FileVisitResult](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/FileVisitResult.html): `CONTINUE`, `TERMINATE`, `SKIP_SUBTREE`, `SKIP_SIBLINGS`.
- [Oracle Java SE 21 — FileVisitOption](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/FileVisitOption.html): tùy chọn `FOLLOW_LINKS`.
- [Oracle Java SE 21 — BasicFileAttributes](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/attribute/BasicFileAttributes.html): size và loại entry.
- [Oracle Java SE 21 — FileSystemLoopException](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/FileSystemLoopException.html): vòng symbolic links trong traversal.
- [Oracle Java Tutorial — Walking the File Tree](https://docs.oracle.com/javase/tutorial/essential/io/walk.html): ví dụ thực hành duyệt và kiểm soát flow.

**Ghi nhớ:** `Files.walkFileTree` đi sâu trong cây và giao quyền quyết định cho `FileVisitor`. Dùng `SimpleFileVisitor` khi chỉ cần override vài callback; `preVisitDirectory` giúp cắt nhánh, `visitFile` xử lý entry, `visitFileFailed` báo lỗi thăm entry, `postVisitDirectory` phù hợp cleanup/tổng hợp sau các con. Cần chú ý `maxDepth`, symbolic links, thứ tự không xác định và vòng đời tài nguyên trước khi áp dụng vào file thật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="directory-tree-traversal-006">preVisitDirectory, visitFile, visitFileFailed và postVisitDirectory</a>

<details>
<summary>Xem chi tiết</summary>

# `preVisitDirectory`, `visitFile`, `visitFileFailed` và `postVisitDirectory` trong Java NIO

## 1. Vì sao cần các callback khi duyệt cây thư mục?

Một thư mục có thể chứa tệp, thư mục con và nhiều mức lồng nhau. Khi muốn kiểm kê tệp, tính dung lượng, lọc phần mở rộng, sao chép hoặc xóa cây thư mục, ứng dụng thường cần **xử lý khác nhau trước khi đi vào thư mục, khi gặp tệp, khi truy cập thất bại và sau khi đã duyệt xong thư mục**.

Java NIO.2 cung cấp `Files.walkFileTree(...)` kết hợp giao diện `FileVisitor<Path>`. Bốn callback cốt lõi là:

1. `preVisitDirectory` — được gọi **trước khi duyệt các mục bên trong thư mục**.
2. `visitFile` — được gọi khi gặp một mục được xem là **file** ở mức duyệt hiện tại.
3. `visitFileFailed` — được gọi khi hệ thống **không thể truy cập một mục** theo quy tắc của quá trình duyệt.
4. `postVisitDirectory` — được gọi **sau khi đã duyệt xong thư mục**, hoặc khi gặp lỗi trong quá trình lặp các mục của thư mục đó.

Nhờ tách thành các callback, một thuật toán có thể làm việc tại cả **thời điểm đi xuống** và **thời điểm đi lên** trong cây, thay vì phải tự viết logic đệ quy, mở từng directory stream và quản lý tất cả trường hợp lỗi.

**Điểm cốt lõi:** Đây là các phương thức của **visitor dùng khi duyệt cây**, không phải sự kiện tự động chạy nền mỗi khi filesystem có tệp mới. Chúng được gọi khi ứng dụng thực sự thực hiện `Files.walkFileTree`.

## 2. API và cấu trúc lớp

Các thành phần chính nằm trong `java.nio.file`:

```java
Files.walkFileTree(start, visitor);
Files.walkFileTree(start, options, maxDepth, visitor);
```

Trong đó:

- `start` là một `Path` chỉ nơi bắt đầu duyệt.
- `options` là tập `FileVisitOption`, ví dụ `FOLLOW_LINKS`.
- `maxDepth` giới hạn độ sâu được thăm, tính thư mục bắt đầu ở độ sâu 0.
- `visitor` là đối tượng triển khai `FileVisitor<? super Path>`.
- Các thao tác có thể ném `IOException`.

Để tránh phải triển khai cả bốn phương thức từ đầu, thông thường kế thừa **`SimpleFileVisitor<Path>`** và override các callback cần thiết.

Dạng khai báo của `FileVisitor<T>`:

```java
FileVisitResult preVisitDirectory(
        T dir, BasicFileAttributes attrs) throws IOException;

FileVisitResult visitFile(
        T file, BasicFileAttributes attrs) throws IOException;

FileVisitResult visitFileFailed(
        T file, IOException exc) throws IOException;

FileVisitResult postVisitDirectory(
        T dir, IOException exc) throws IOException;
```

Đây là **các chữ ký API để tham khảo**, không phải tệp Java độc lập. `BasicFileAttributes` đến từ `java.nio.file.attribute`. `FileVisitResult` dùng để cho bộ duyệt biết nên tiếp tục, bỏ nhánh, bỏ các mục ngang hàng hay chấm dứt.

Lớp `SimpleFileVisitor` có hành vi mặc định quan trọng:

- `preVisitDirectory(...)` và `visitFile(...)` mặc định trả `CONTINUE`.
- `visitFileFailed(..., exc)` mặc định **ném lại** `exc`.
- `postVisitDirectory(..., exc)` mặc định tiếp tục nếu `exc == null`, nhưng **ném lại** `exc` nếu có lỗi lặp thư mục.

Vì vậy, “không override callback báo lỗi” thường đồng nghĩa với **dừng và để IOException lan ra ngoài**, không phải âm thầm bỏ qua tệp lỗi.

## 3. Thứ tự callback khi duyệt thành công

Xét một cây đơn giản:

```text
root/
└── docs/
    └── note.txt
```

Khi không bỏ qua nhánh, thứ tự callback thường được hình dung:

```text
preVisitDirectory(root)
  preVisitDirectory(root/docs)
    visitFile(root/docs/note.txt)
  postVisitDirectory(root/docs, null)
postVisitDirectory(root, null)
```

Đây là **duyệt theo chiều sâu (depth-first)**: vào thư mục cha, vào thư mục con, xử lý tệp bên trong, đi ra thư mục con, rồi đi ra thư mục cha.

Nếu trong một thư mục có nhiều mục con, **thứ tự giữa các mục ngang hàng không được bảo đảm theo tên, ngày hay kích thước**. Chương trình không nên viết test giả định luôn gặp `a.txt` trước `b.txt`. Muốn xử lý theo thứ tự xác định, phải có chiến lược liệt kê/sắp xếp riêng thay vì mặc định dựa vào `walkFileTree`.

Không phải mọi phần tử trong cây đều đi qua `visitFile`: thư mục được đi xuống thông thường nhận **`preVisitDirectory` và `postVisitDirectory`**, còn tệp thường đi qua **`visitFile`**. Những ngoại lệ như mục ở giới hạn `maxDepth` hoặc symbolic link không được theo sẽ được giải thích ở phần sau.

## 4. `preVisitDirectory` — trước khi đi vào thư mục

Chữ ký:

```java
@Override
public FileVisitResult preVisitDirectory(
        Path dir, BasicFileAttributes attrs) throws IOException
```

Đây là **chữ ký minh họa**, phải đặt trong một class kế thừa `SimpleFileVisitor<Path>`, có đủ import.

Callback được gọi khi bộ duyệt đã nhận diện một thư mục và **trước khi duyệt những mục chứa bên trong**. Hai đối số:

- `dir`: `Path` của thư mục đang chuẩn bị duyệt.
- `attrs`: thuộc tính cơ bản được đọc cho mục đó, như `isDirectory()`, `size()`, `creationTime()` và `lastModifiedTime()`.

Các công việc phù hợp:

- Ghi nhận “đã vào thư mục” và tăng độ sâu.
- Tạo thư mục đích trước khi sao chép nội dung con.
- Kiểm tra có nên đi vào một nhánh hay không.
- Loại trừ thư mục như `build`, `node_modules` hoặc thư mục cache theo chính sách của ứng dụng.

Ví dụ **đoạn mã đặt trong visitor**:

```java
@Override
public FileVisitResult preVisitDirectory(
        Path dir, BasicFileAttributes attrs) {
    if (dir.getFileName() != null
            && dir.getFileName().toString().equals("cache")) {
        return FileVisitResult.SKIP_SUBTREE;
    }
    return FileVisitResult.CONTINUE;
}
```

`SKIP_SUBTREE` ở đây khiến bộ duyệt **không đi vào thư mục `cache`**. Các tệp và thư mục bên trong nó không nhận callback tương ứng; `postVisitDirectory` cho **chính thư mục bị bỏ qua** cũng không được gọi. Những nhánh khác vẫn có thể được duyệt.

Lưu ý: `preVisitDirectory` không đảm bảo “mở thành công mọi mục con” — khi tiếp tục duyệt, lỗi đọc thư mục hoặc đọc thuộc tính vẫn có thể phát sinh.

## 5. `visitFile` — xử lý mục được thăm như file

Chữ ký:

```java
@Override
public FileVisitResult visitFile(
        Path file, BasicFileAttributes attrs) throws IOException
```

Đây là **chữ ký minh họa**, không phải chương trình độc lập.

Thông thường `visitFile` được gọi cho các **tệp không phải thư mục**. Nó nhận `Path` và `BasicFileAttributes` để ứng dụng quyết định xử lý:

- Đếm số tệp và cộng kích thước (theo ý nghĩa `attrs.size()`).
- Kiểm tra phần mở rộng hoặc tên.
- Sao chép tệp sang nơi khác.
- Đọc nội dung, tính hash hoặc kiểm tra định dạng.
- Xóa tệp khi đang thực hiện một tác vụ được cho phép.

Ví dụ đếm tệp:

```java
@Override
public FileVisitResult visitFile(
        Path file, BasicFileAttributes attrs) {
    if (attrs.isRegularFile()) {
        count++;
    }
    return FileVisitResult.CONTINUE;
}
```

Đoạn này **minh họa** trong một visitor có trường `long count`. Dùng `attrs.isRegularFile()` nếu chỉ muốn đếm file thường; không mặc định mọi mục vào `visitFile` đều là file thường trong mọi lựa chọn duyệt.

Đặc biệt:

- Khi **không theo symbolic link**, liên kết thường được thăm dưới dạng một mục riêng, không tự duyệt cây ở đích của link.
- Khi đã tới **`maxDepth`**, một mục là thư mục cũng có thể được chuyển tới `visitFile` thay vì đi sâu và gọi `preVisitDirectory` cho nó.
- Nếu việc lấy thuộc tính cơ bản của một mục thất bại, bộ duyệt có thể gọi **`visitFileFailed`** thay vì `visitFile`.

`visitFile` không có nghĩa nội dung file đã được mở hoặc đọc toàn bộ. Nó chỉ báo rằng quá trình duyệt đã tới mục đó theo quy tắc của visitor.

## 6. `postVisitDirectory` — sau khi thăm các mục con

Chữ ký:

```java
@Override
public FileVisitResult postVisitDirectory(
        Path dir, IOException exc) throws IOException
```

Đây là **chữ ký minh họa**, đặt trong visitor.

Callback được gọi sau khi đã đi qua các mục trong thư mục, hoặc khi việc lặp các mục bị lỗi. Khác `preVisitDirectory`, đối số thứ hai **không phải thuộc tính**, mà là:

- `null` khi việc lặp các mục trong thư mục kết thúc bình thường.
- `IOException exc` khi có lỗi trong quá trình lặp thư mục, khiến quá trình lặp kết thúc bất thường.

Cách xử lý thường gặp:

```java
@Override
public FileVisitResult postVisitDirectory(
        Path dir, IOException exc) throws IOException {
    if (exc != null) {
        throw exc;
    }
    System.out.println("Xong thư mục: " + dir);
    return FileVisitResult.CONTINUE;
}
```

Vị trí này lý tưởng cho công việc **hậu thứ tự (post-order)**:

- Kết thúc tổng hợp thông tin từ các thư mục con.
- Hoàn tất hoặc đóng một cấu trúc ứng dụng tương ứng với thư mục.
- Xóa thư mục **sau khi** đã xóa các file và thư mục con trong một quy trình xóa cây có kiểm soát.
- Ghi nhận “rời thư mục” để cân bằng trạng thái trong thuật toán.

**Không giả định `postVisitDirectory` luôn được gọi cho mọi thư mục.** Nếu `preVisitDirectory` trả `SKIP_SUBTREE`/`SKIP_SIBLINGS` đối với một thư mục, bộ duyệt không đi vào nó và không gọi callback hậu kỳ cho thư mục bị bỏ qua. Nếu visitor chủ động ném ngoại lệ hoặc trả `TERMINATE` giữa chừng, các callback hậu kỳ cho tổ tiên chưa hoàn tất **không được bảo đảm**. Khi thật sự cần giải phóng tài nguyên, sử dụng `try/finally` hoặc `try-with-resources` tại phạm vi sở hữu thay vì chỉ dựa vào callback hậu kỳ.

Ngoài ra, lỗi xảy ra **bên trong `visitFile`** do code nghiệp vụ tự ném không nhất thiết được chuyển thành `exc` của `postVisitDirectory`; nhiều trường hợp ngoại lệ sẽ truyền thẳng ra khỏi `walkFileTree`.

## 7. `visitFileFailed` — khi không thể truy cập mục

Chữ ký:

```java
@Override
public FileVisitResult visitFileFailed(
        Path file, IOException exc) throws IOException
```

Đây là **chữ ký minh họa**, không phải chương trình độc lập.

Callback này có thể được gọi khi:

- Không đọc được thuộc tính cơ bản của mục.
- Không mở được một thư mục để duyệt.
- Gặp lỗi I/O khi đi theo symbolic link, ví dụ liên kết vòng với `FOLLOW_LINKS`.
- Đường dẫn đã bị xóa hoặc thay đổi trước lúc hệ thống truy cập nó.

`exc` chứa nguyên nhân I/O. Ví dụ:

```java
@Override
public FileVisitResult visitFileFailed(
        Path file, IOException exc) throws IOException {
    System.err.println("Không thể thăm " + file
            + ": " + exc.getClass().getSimpleName());
    return FileVisitResult.CONTINUE;
}
```

Nếu trả `CONTINUE`, bộ duyệt **bỏ qua mục thất bại và thử tiếp các mục còn lại** khi có thể. Điều này hữu ích cho tác vụ kiểm kê “best effort” nhưng **không tương đương duyệt thành công toàn bộ cây**. Chương trình nên ghi nhận các mục thất bại và không trả báo cáo là “đủ 100%” nếu đã bỏ qua dữ liệu.

Để **fail fast**, chỉ cần:

```java
@Override
public FileVisitResult visitFileFailed(
        Path file, IOException exc) throws IOException {
    throw exc;
}
```

`SimpleFileVisitor` mặc định cũng có hành vi ném lại ngoại lệ tại đây.

**Phân biệt với `postVisitDirectory(dir, exc)`:**

| Tình huống | Callback thích hợp |
| --- | --- |
| Không đọc được thuộc tính một mục | `visitFileFailed` |
| Không mở được thư mục để liệt kê | `visitFileFailed` |
| Đã mở thư mục, nhưng lỗi khi lặp các mục bên trong | `postVisitDirectory` với `exc != null` |
| Ứng dụng tự ném `IOException` trong `visitFile` | Thường truyền ra khỏi quá trình duyệt, **không** được tự động đổi thành `visitFileFailed` |

Điều này giúp phân biệt **lỗi của hệ thống duyệt** với **lỗi trong xử lý nghiệp vụ của chính visitor**.

## 8. `FileVisitResult` — bốn quyết định điều khiển luồng

Bốn callback trả một giá trị của enum `FileVisitResult`:

| Giá trị | Tác dụng |
| --- | --- |
| `CONTINUE` | Tiếp tục quá trình duyệt bình thường |
| `TERMINATE` | Dừng toàn bộ quá trình duyệt; không thăm tiếp |
| `SKIP_SUBTREE` | Khi trả từ `preVisitDirectory`, không duyệt cây con của thư mục đó |
| `SKIP_SIBLINGS` | Bỏ qua những mục ngang hàng chưa được thăm ở cùng cấp và các hậu duệ của chúng |

Chi tiết theo ngữ cảnh rất quan trọng:

- `SKIP_SUBTREE` chỉ có tác dụng thực chất ở **`preVisitDirectory`**; nếu trả từ `visitFile`, nơi không có cây con để duyệt, nó tương đương `CONTINUE`.
- `SKIP_SIBLINGS` có thể ngăn duyệt các mục còn lại dưới cùng một thư mục cha, nên không dùng chỉ để “bỏ qua tệp hiện tại”.
- Khi muốn bỏ qua **một thư mục và toàn bộ nội dung bên trong nhưng vẫn duyệt thư mục khác cùng cấp**, chọn `SKIP_SUBTREE` từ `preVisitDirectory`.
- `TERMINATE` kết thúc sớm; không phải tín hiệu lỗi tự động. Caller phải tự quyết định một cuộc duyệt dừng sớm là kết quả hợp lệ hay chưa hoàn chỉnh.
- Không trả `null`: kết quả callback phải là một giá trị `FileVisitResult` hợp lệ.

Ví dụ chính sách:

```text
Phát hiện thư mục cache            → SKIP_SUBTREE trong preVisitDirectory
Xử lý file thông thường thành công → CONTINUE trong visitFile
Không thể đọc mục nhưng cho phép bỏ qua → CONTINUE trong visitFileFailed
Gặp kết quả cần tìm và muốn ngừng  → TERMINATE
Kết thúc thư mục không có lỗi      → CONTINUE trong postVisitDirectory
```

**Thận trọng khi dùng `SKIP_SIBLINGS`:** “sibling nào chưa được duyệt” phụ thuộc thứ tự liệt kê của filesystem; không mặc định tên file có thứ tự chữ cái.

## 9. Chương trình Java 17+ chạy được: quan sát đúng thứ tự callback

Tạo tệp **`TreeCallbacksDemo.java`**:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class TreeCallbacksDemo {
    private static String label(Path root, Path path) {
        String relative = root.relativize(path).toString()
                .replace('\\', '/');
        return relative.isEmpty() ? "." : relative;
    }

    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("callback-demo-");
        Path sub = root.resolve("docs");
        Path note = sub.resolve("note.txt");

        try {
            Files.createDirectory(sub);
            Files.writeString(note, "Hello", StandardCharsets.UTF_8);

            FileVisitor<Path> visitor = new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(
                        Path dir, BasicFileAttributes attrs) {
                    System.out.println("PRE " + label(root, dir));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(
                        Path file, BasicFileAttributes attrs) {
                    System.out.println("FILE " + label(root, file));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(
                        Path file, IOException exc) throws IOException {
                    System.out.println("FAIL " + label(root, file));
                    throw exc;
                }

                @Override
                public FileVisitResult postVisitDirectory(
                        Path dir, IOException exc) throws IOException {
                    if (exc != null) {
                        throw exc;
                    }
                    System.out.println("POST " + label(root, dir));
                    return FileVisitResult.CONTINUE;
                }
            };

            Files.walkFileTree(root, visitor);
        } finally {
            Files.deleteIfExists(note);
            Files.deleteIfExists(sub);
            Files.deleteIfExists(root);
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 TreeCallbacksDemo.java
java TreeCallbacksDemo
```

Đầu ra khi thao tác tạo/duyệt tệp tạm thành công:

```text
PRE .
PRE docs
FILE docs/note.txt
POST docs
POST .
```

Ví dụ cố ý dùng **một** thư mục con và **một** tệp để thứ tự không phụ thuộc việc sắp xếp các sibling. Đường dẫn thư mục tạm có tên ngẫu nhiên, nhưng hàm `label` chuyển về đường dẫn tương đối để stdout ổn định trên Windows và hệ điều hành dùng dấu phân cách `/`.

Ở chương trình này:

- `PRE .` là callback trước khi liệt kê thư mục gốc.
- `PRE docs` là callback trước khi đi vào `docs`.
- `FILE docs/note.txt` là lần thăm tệp.
- `POST docs` rồi `POST .` phản ánh việc trở ngược từ lá lên gốc.
- Không có `FAIL` vì mọi mục đều truy cập được.
- Khối `finally` chỉ xóa các đường dẫn **do chính chương trình tạo**. Nếu cleanup gặp lỗi, `IOException` vẫn có thể được ném; không áp dụng nguyên xi logic xóa này cho dữ liệu người dùng.

## 10. Chương trình chạy được: bỏ qua nguyên nhánh nhưng vẫn duyệt nhánh khác

`SKIP_SUBTREE` có hành vi dễ nhận thấy nhất khi thư mục bị bỏ qua có các tệp bên trong. Để tránh phụ thuộc thứ tự sibling, ví dụ này ghi lại các mục được thăm rồi in kết luận **sau khi duyệt**.

Tạo tệp **`SkipSubtreeDemo.java`**:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;

public class SkipSubtreeDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("skip-demo-");
        Path keep = root.resolve("keep");
        Path skip = root.resolve("skip");
        Path visible = keep.resolve("visible.txt");
        Path hidden = skip.resolve("hidden.txt");

        try {
            Files.createDirectory(keep);
            Files.createDirectory(skip);
            Files.writeString(visible, "ok", StandardCharsets.UTF_8);
            Files.writeString(hidden, "ignored", StandardCharsets.UTF_8);

            Set<Path> entered = new HashSet<>();
            Set<Path> files = new HashSet<>();
            Set<Path> exited = new HashSet<>();

            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(
                        Path dir, BasicFileAttributes attrs) {
                    entered.add(dir);
                    return dir.equals(skip)
                            ? FileVisitResult.SKIP_SUBTREE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(
                        Path file, BasicFileAttributes attrs) {
                    files.add(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(
                        Path dir, IOException exc) throws IOException {
                    if (exc != null) {
                        throw exc;
                    }
                    exited.add(dir);
                    return FileVisitResult.CONTINUE;
                }
            });

            System.out.println("entered skip=" + entered.contains(skip));
            System.out.println("visited hidden=" + files.contains(hidden));
            System.out.println("post skip=" + exited.contains(skip));
            System.out.println("visited visible=" + files.contains(visible));
            System.out.println("post root=" + exited.contains(root));
        } finally {
            Files.deleteIfExists(visible);
            Files.deleteIfExists(hidden);
            Files.deleteIfExists(keep);
            Files.deleteIfExists(skip);
            Files.deleteIfExists(root);
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 SkipSubtreeDemo.java
java SkipSubtreeDemo
```

Đầu ra:

```text
entered skip=true
visited hidden=false
post skip=false
visited visible=true
post root=true
```

Các mục trong `skip` không được duyệt, còn `keep/visible.txt` vẫn được thăm. Vì `skip` đã bị loại trừ từ callback `preVisitDirectory`, chương trình không nhận `postVisitDirectory(skip, ...)`.

Không viết kiểm thử dựa trên việc `keep` hay `skip` xuất hiện trước: thứ tự liệt kê có thể khác giữa các hệ điều hành và filesystem.

## 11. Chương trình chạy được: `visitFileFailed` và chiến lược bỏ qua lỗi

Ví dụ dùng một đường dẫn **không tồn tại trong thư mục tạm vừa tạo** để mô phỏng việc không lấy được thuộc tính. Cách này không phải thay đổi phân quyền và không cần giả lập một filesystem hỏng.

Tạo tệp **`FileVisitFailureDemo.java`**:

```java
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class FileVisitFailureDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("failure-demo-");
        Path missing = root.resolve("missing.txt");

        try {
            Files.walkFileTree(missing, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFileFailed(
                        Path file, IOException exc) {
                    System.out.println("Failed: "
                            + file.getFileName());
                    return FileVisitResult.CONTINUE;
                }
            });
            System.out.println("Traversal returned");
        } finally {
            Files.deleteIfExists(root);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 FileVisitFailureDemo.java
java FileVisitFailureDemo
```

Đầu ra với filesystem mặc định hỗ trợ thao tác thư mục tạm bình thường:

```text
Failed: missing.txt
Traversal returned
```

Vì `missing.txt` không tồn tại, bộ duyệt không thể đọc thuộc tính và gọi `visitFileFailed`. Callback trả `CONTINUE`, nên `walkFileTree` kết thúc mà **không ném lại lỗi**. Điều này không có nghĩa tệp đã được thăm thành công; ứng dụng có trách nhiệm báo cáo chính xác những mục đã bỏ qua.

Nếu callback dùng `throw exc;`, chương trình sẽ báo ngoại lệ cho caller. Trong hệ thống sản xuất, kiểu lỗi cụ thể có thể là `NoSuchFileException`, `AccessDeniedException`, `FileSystemLoopException` hoặc loại `IOException` khác tùy tình huống/provider.

## 12. Lỗi khi lặp thư mục và khác biệt với lỗi của code visitor

Một trường hợp khó hơn: thư mục **mở thành công** nhưng đọc danh sách các mục trong thư mục gặp lỗi giữa chừng. Khi đó `postVisitDirectory(dir, exc)` có thể nhận `exc != null`.

Ví dụ đoạn chính sách fail-fast:

```java
@Override
public FileVisitResult postVisitDirectory(
        Path dir, IOException exc) throws IOException {
    if (exc != null) {
        throw new IOException(
                "Lỗi khi duyệt các mục trong " + dir, exc);
    }
    return FileVisitResult.CONTINUE;
}
```

Đây là **đoạn minh họa** để đặt trong visitor. Vì rất khó mô phỏng một lỗi directory iterator đáng tin trên mọi filesystem, không đưa stdout cố định.

Ngược lại, nếu bên trong `visitFile` có code:

```java
@Override
public FileVisitResult visitFile(
        Path file, BasicFileAttributes attrs) throws IOException {
    processFile(file);  // có thể ném IOException
    return FileVisitResult.CONTINUE;
}
```

Đây là **đoạn minh họa**: `processFile` là hàm ứng dụng, không phải API chuẩn của Java. Một `IOException` do `processFile` ném thường **thoát khỏi `walkFileTree`** thay vì tự động được chuyển sang `visitFileFailed`. Không nên dùng hai callback báo lỗi như một cơ chế “bắt mọi lỗi” thay cho quản lý ngoại lệ của ứng dụng.

Nếu ứng dụng muốn tiếp tục khi không đọc được nội dung **một file riêng**, hãy chủ động `try/catch` ngay trong `visitFile`, ghi nhận trạng thái lỗi và trả `CONTINUE` theo một chính sách đã xác định.

## 13. `maxDepth`, symbolic link và vòng lặp

### Giới hạn độ sâu

Overload `Files.walkFileTree(start, options, maxDepth, visitor)` cho phép đặt độ sâu tối đa. `start` có độ sâu 0. Ví dụ dùng `maxDepth = 1` sẽ không đi sâu vào nội dung của thư mục ở độ sâu 1.

Điểm tinh tế: những **thư mục gặp tại độ sâu tối đa có thể được chuyển tới `visitFile`**, vì quá trình duyệt không mở chúng như các thư mục sẽ tiếp tục đi xuống. Do đó nếu chỉ muốn đếm tệp thường, hãy kiểm tra `attrs.isRegularFile()` trong `visitFile`.

`maxDepth < 0` không hợp lệ. Khi cần duyệt không giới hạn thực tế, overload đơn giản dùng cách duyệt mặc định tương ứng.

### Symbolic link

Mặc định `walkFileTree` **không đi theo symbolic link**. Vì vậy symbolic link tới thư mục không mặc nhiên trở thành một nhánh con được duyệt. Có thể cấu hình:

```java
Files.walkFileTree(
        root,
        java.util.EnumSet.of(FileVisitOption.FOLLOW_LINKS),
        10,
        visitor);
```

Đây là **đoạn minh họa**, yêu cầu `Path root`, `FileVisitor<Path> visitor`, import `Files` và `FileVisitOption`, và xử lý `IOException`.

Đi theo liên kết có thể dẫn tới **chu trình**, ví dụ A trỏ B rồi B trỏ A hoặc một thư mục con trỏ lại tổ tiên. Java có cơ chế phát hiện vòng lặp trong chế độ follow links; lỗi như `FileSystemLoopException` có thể được chuyển đến `visitFileFailed`. Không dùng `FOLLOW_LINKS` thiếu suy nghĩ cho thao tác xóa/sao chép dữ liệu quan trọng; cần quy định có xử lý bản thân link hay đích của nó.

### Cây có thể thay đổi giữa lúc duyệt

Filesystem không phải ảnh chụp bất biến: tệp có thể bị đổi tên, xóa, cấp quyền khác hoặc nội dung đổi trong khi đang duyệt. Thuộc tính `attrs` được lấy trong quá trình thăm và **có thể đã cũ** khi bạn xử lý nội dung tệp. Nếu phải xử lý dữ liệu có yêu cầu nhất quán nghiêm ngặt, cần snapshot, phối hợp quyền ghi hoặc quy trình kiểm chứng ở tầng ứng dụng.

## 14. Chọn đúng callback cho từng tác vụ

| Tác vụ | Callback thường phù hợp | Lý do |
| --- | --- | --- |
| Ghi log “vào thư mục” | `preVisitDirectory` | Trước khi gặp con |
| Loại trừ cả nhánh cache | `preVisitDirectory` với `SKIP_SUBTREE` | Không cần đi vào nhánh |
| Tạo cấu trúc thư mục đích khi copy | `preVisitDirectory` | Thư mục cha cần tồn tại trước con |
| Đếm file thường, tính tổng dung lượng | `visitFile` | Có `BasicFileAttributes` của mục |
| Đọc hoặc sao chép nội dung một tệp | `visitFile` | Có đường dẫn tệp cần xử lý |
| Ghi nhận file không truy cập được | `visitFileFailed` | Nhận nguyên nhân thất bại |
| Xử lý lỗi lặp danh sách thư mục | `postVisitDirectory` với `exc != null` | Lỗi xảy ra khi duyệt các mục con |
| Tổng hợp kết quả từ nhánh con | `postVisitDirectory` | Tất cả con đã được thăm theo nhánh |
| Xóa thư mục sau khi xóa hết con | `postVisitDirectory` | Hậu thứ tự |
| Dừng tìm kiếm khi đã thấy mục mong muốn | `visitFile` trả `TERMINATE` | Không cần tiếp tục toàn cây |

### Ví dụ xóa theo hậu thứ tự — chỉ là đoạn minh họa

```java
Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
    @Override
    public FileVisitResult visitFile(
            Path file, BasicFileAttributes attrs)
            throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(
            Path dir, IOException exc)
            throws IOException {
        if (exc != null) {
            throw exc;
        }
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
    }
});
```

**Đây không phải chương trình độc lập**: cần `Path root`, các import và xử lý `IOException`. Đoạn code **xóa thật** mọi tệp/thư mục bên dưới `root` mà ứng dụng có quyền thao tác; chỉ dùng với cây dữ liệu do chính bạn tạo để kiểm thử hoặc có xác nhận rõ. Khác với xóa từ cha xuống (dễ gặp `DirectoryNotEmptyException`), hậu thứ tự cho phép xóa file và thư mục con trước khi xóa cha.

Với thư mục chứa dữ liệu quan trọng, phải xem xét symbolic link, phân quyền, thay đổi đồng thời, xử lý lỗi và khả năng khôi phục. `walkFileTree` không biến toàn bộ thao tác xóa thành transaction.

## 15. So sánh `Files.walkFileTree` với `Files.walk`

Cả hai đều giúp đi qua cây thư mục nhưng khác giao diện và cách xử lý lỗi:

| Đặc điểm | `Files.walkFileTree` | `Files.walk` |
| --- | --- | --- |
| API xử lý | Visitor với các callback rõ ràng | `Stream<Path>`, phối hợp `filter`/`map`/`forEach` |
| Trước/sau thư mục | Có `preVisitDirectory` và `postVisitDirectory` | Không có các callback tương đương trực tiếp |
| Lỗi khi duyệt | `visitFileFailed`, `postVisitDirectory(exc)`, `IOException` | Một số lỗi trong quá trình duyệt có thể xuất hiện dưới `UncheckedIOException` |
| Bỏ cả nhánh theo logic visitor | `SKIP_SUBTREE` | Không có một callback `preVisitDirectory` tương ứng để pruning |
| Đóng tài nguyên | API quản lý quá trình duyệt nội bộ | `Stream<Path>` cần `try-with-resources` để đóng |
| Khi phù hợp | Sao chép/xóa/tính kích thước cây, log vào–ra thư mục, xử lý lỗi có ngữ cảnh | Pipeline lọc/liệt kê đường dẫn |

Không dùng `Files.walk(root).filter(...)` với kỳ vọng filter một thư mục tự động ngăn hệ thống đi vào các mục con của nó; filter của Stream quyết định **các phần tử sau khi được phát ra**, không hoạt động như `SKIP_SUBTREE` của visitor.

Nếu tác vụ cần biết chính xác thời điểm trước/sau một thư mục, hoặc cần kiểm soát khi truy cập thất bại, `walkFileTree` thường dễ biểu đạt hơn.

## 16. Lỗi thường gặp và cách tránh

| Sai lầm | Hậu quả | Cách đúng |
| --- | --- | --- |
| Nghĩ `preVisitDirectory` chạy sau khi thăm con | Tạo đích, tính trạng thái sai thời điểm | Hiểu đây là callback **trước** |
| Đếm mọi mục qua `visitFile` như file thường | Đếm cả symbolic link hoặc thư mục ở `maxDepth` | Kiểm tra `attrs.isRegularFile()` |
| Nghĩ `postVisitDirectory` luôn chạy | Không cleanup đủ khi bị skip/terminate/throw | Dùng `try/finally` cho cleanup bắt buộc |
| Bỏ qua `exc` trong `postVisitDirectory` | Báo duyệt đầy đủ dù thư mục có lỗi | Kiểm tra, ghi nhận hoặc ném lại lỗi |
| `visitFileFailed` trả `CONTINUE` nhưng báo thành công tuyệt đối | Che mất file/thư mục không đọc được | Thu thập danh sách lỗi và đánh dấu “chưa đầy đủ” |
| Cho rằng lỗi của `processFile` tự chuyển thành `visitFileFailed` | Không bắt được ngoại lệ ở đúng nơi | Xử lý checked exception trong visitor hoặc ngoài `walkFileTree` |
| Dùng `SKIP_SIBLINGS` để bỏ một thư mục nhưng vẫn muốn duyệt anh em của nó | Bỏ luôn các mục ngang hàng chưa được thăm | Dùng `SKIP_SUBTREE` từ `preVisitDirectory` |
| Giả định sibling luôn theo thứ tự tên | Kết quả khác giữa máy hoặc filesystem | Không dựa vào thứ tự, hoặc tự sắp xếp |
| Theo symbolic link mà không xét vòng lặp | Có thể gặp `FileSystemLoopException`, xử lý nhầm đích | Định nghĩa chính sách link và lỗi |
| Dùng `Files.walk` mà quên đóng Stream | Rò tài nguyên hệ thống | `try-with-resources` |
| Dùng visitor xóa dữ liệu nhưng bỏ qua root thật | Có thể xóa nhầm cả cây thư mục | Chỉ thao tác trên root đã xác minh và được phép |
| Coi mọi `IOException` là file không tồn tại | Bỏ sót lỗi quyền, thiết bị, link | Xem loại và nguyên nhân lỗi cụ thể |

## 17. Checklist thiết kế visitor và bài tự luyện

**Trước khi duyệt cây:**

- [ ] Chọn đúng `start`; tránh thao tác nhầm cây dữ liệu.
- [ ] Xác định mục tiêu: liệt kê, lọc, sao chép, tổng hợp hay xóa.
- [ ] Chọn quy tắc theo symbolic link và `maxDepth`.
- [ ] Quyết định callback nào cần override, callback nào dùng mặc định.
- [ ] Chọn `CONTINUE`, `SKIP_SUBTREE`, `SKIP_SIBLINGS` hoặc `TERMINATE` theo ngữ nghĩa yêu cầu.
- [ ] Xác định xử lý khi truy cập một mục thất bại và khi lặp thư mục gặp lỗi.
- [ ] Tránh phụ thuộc thứ tự các sibling.
- [ ] Đảm bảo cleanup tài nguyên ứng dụng không chỉ dựa vào `postVisitDirectory`.

**Bài tự luyện 1 — thứ tự callback:** Tạo cây `root/a/x.txt` và `root/a/b/y.txt`, mỗi thư mục chỉ có một mục con, rồi in `PRE`, `FILE`, `POST`. Vẽ chính xác chuỗi callback theo chiều sâu và giải thích vì sao `POST a/b` xuất hiện trước `POST a`.

**Bài tự luyện 2 — loại trừ:** Thêm nhánh `root/cache/temp.txt` và cho `preVisitDirectory` trả `SKIP_SUBTREE` khi gặp `cache`. Kiểm tra rằng `temp.txt` không đi qua `visitFile` và `postVisitDirectory(cache, ...)` không được gọi, nhưng những nhánh khác vẫn được duyệt.

**Bài tự luyện 3 — lỗi:** Trong một thư mục tạm, gọi `Files.walkFileTree` cho đường dẫn không tồn tại. Thử hai phiên bản `visitFileFailed`: trả `CONTINUE` và `throw exc`. So sánh việc phương thức kết thúc bình thường với việc ném `IOException`.

**Bài tự luyện 4 — hậu thứ tự:** Với cây tạm có hai mức thư mục, hãy dùng `visitFile` để xóa file và `postVisitDirectory` để xóa thư mục. Vì sao xóa thư mục cha trước khi xóa nội dung có thể gây `DirectoryNotEmptyException`?

**Ghi nhớ:** `preVisitDirectory` chạy **trước** nội dung thư mục; `visitFile` xử lý mục được thăm như file; `visitFileFailed` báo **mục không thể truy cập**; `postVisitDirectory` chạy **sau** nội dung thư mục hoặc nhận lỗi khi lặp thư mục. `FileVisitResult` quyết định tiếp tục, bỏ nhánh, bỏ sibling hoặc dừng toàn bộ. Hiểu rõ thứ tự và ngoại lệ giúp duyệt cây an toàn, đúng và có thể kiểm thử trên Java 17+.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="directory-tree-traversal-007">FileVisitResult: tiếp tục, bỏ nhánh hoặc kết thúc</a>

<details>
<summary>Xem chi tiết</summary>

# FileVisitResult: tiếp tục, bỏ nhánh hoặc kết thúc khi duyệt cây thư mục

## 1. Bài toán: kiểm soát hành trình duyệt cây

Khi Java duyệt một thư mục có nhiều tầng, ứng dụng thường không muốn xử lý mọi tệp như nhau. Ta có thể cần:

- Duyệt hết cây để thống kê tệp.
- **Bỏ qua cả một nhánh** như `.git`, `node_modules` hoặc một thư mục không nằm trong phạm vi xử lý.
- **Bỏ qua những phần tử còn lại cùng cấp** sau khi đã tìm được một mục phù hợp.
- **Dừng toàn bộ cuộc duyệt** ngay khi có kết quả hoặc gặp điều kiện nghiêm trọng.
- Tiếp tục sau một lỗi đọc riêng lẻ, hoặc ngừng khi tính toàn vẹn không còn được bảo đảm.

Java NIO.2 cung cấp `Files.walkFileTree(...)` để duyệt cây và `FileVisitor`/`SimpleFileVisitor` để nhận các sự kiện. Sau mỗi callback, ta trả về một **`FileVisitResult`**, là chỉ dẫn cho cơ chế duyệt: tiếp tục, bỏ nhánh, bỏ anh em cùng cấp, hoặc kết thúc.

```text
root/
├── src/
│   ├── Main.java
│   └── App.java
├── build/
│   └── generated.bin
└── README.md

preVisitDirectory(root) → CONTINUE
  preVisitDirectory(src) → CONTINUE
    visitFile(Main.java) → CONTINUE
    visitFile(App.java) → CONTINUE
  postVisitDirectory(src) → CONTINUE
  preVisitDirectory(build) → SKIP_SUBTREE
    generated.bin KHÔNG được thăm
  visitFile(README.md) → CONTINUE
postVisitDirectory(root) → CONTINUE
```

Đây là **sơ đồ minh họa**, không phải thứ tự duyệt được bảo đảm. Thứ tự các mục trong một thư mục phụ thuộc filesystem/provider; nếu cần thứ tự sắp xếp, phải thiết kế thêm một bước có kiểm soát thay vì giả định walker sẽ duyệt theo bảng chữ cái.

Điểm trọng tâm của bài: **giá trị `FileVisitResult` có hiệu lực tùy callback trả nó về**, và “bỏ nhánh” khác căn bản với “bỏ các phần tử cùng cấp” hay “dừng toàn bộ”.

## 2. Khởi động một cuộc duyệt bằng Files.walkFileTree

API cơ bản:

```java
Files.walkFileTree(start, visitor);
```

Biến `start` là `Path`; `visitor` triển khai `FileVisitor<? super Path>`. `Files.walkFileTree` duyệt **depth-first**: với một thư mục, gọi callback trước khi đi vào các mục bên trong; sau khi các mục đã xử lý, gọi callback sau khi thăm thư mục (trừ trường hợp bị bỏ nhánh, dừng sớm và các ngoại lệ theo hợp đồng).

Một overload cho phép chọn cách theo liên kết và giới hạn độ sâu:

```java
Files.walkFileTree(
        start,
        EnumSet.noneOf(FileVisitOption.class),
        4,
        visitor);
```

Đây là **đoạn minh họa**, yêu cầu các import từ `java.nio.file`, `java.util.EnumSet` và biến `start`, `visitor` đã khai báo. `maxDepth = 4` đặt mức sâu tối đa. Lựa chọn `EnumSet.noneOf(FileVisitOption.class)` nghĩa là **không bật `FOLLOW_LINKS`**; mặc định symbolic link không được tự động đi theo như một thư mục bình thường.

`Files.walkFileTree` thường trả lại chính `start` khi cuộc duyệt kết thúc theo hợp đồng, còn chi tiết điều khiển được thực hiện bởi các callback. Nó có thể ném `IOException` (ví dụ một callback ném lỗi), không phải chỉ thông qua các giá trị enum.

### Khi nào dùng FileVisitor thay vì Files.walk?

`Files.walk(start)` trả một `Stream<Path>` lazy, thích hợp cho một số phép lọc/lấy danh sách đơn giản. Nhưng `FileVisitor` cho phép **quyết định tại từng thư mục có đi vào nhánh đó hay không**, cũng như nhận callback riêng trước/sau thư mục và khi thăm thất bại.

Nếu cần **prune** một nhánh trước khi walker đi sâu vào nó, `preVisitDirectory` kết hợp `SKIP_SUBTREE` là công cụ trực tiếp. `Stream.filter(path -> ...)` trên kết quả `Files.walk` chỉ lọc phần tử được **trả ra cho pipeline**, không phải một cam kết rằng walker sẽ tránh mở/đi vào các nhánh đã lọc.

## 3. FileVisitor có bốn callback

`FileVisitor<Path>` định nghĩa bốn phương thức:

| Callback | Được gọi khi nào? | Tham số quan trọng |
| --- | --- | --- |
| `preVisitDirectory(Path dir, BasicFileAttributes attrs)` | Trước khi đi vào các mục của một thư mục | `dir`, thuộc tính thư mục |
| `visitFile(Path file, BasicFileAttributes attrs)` | Khi thăm một tệp/entry không đi vào như thư mục; tùy giới hạn độ sâu, một directory cũng có thể đi qua đây | `file`, thuộc tính |
| `visitFileFailed(Path file, IOException exc)` | Khi không thể thăm entry, ví dụ lỗi đọc thuộc tính/mở thư mục | `file`, nguyên nhân thất bại |
| `postVisitDirectory(Path dir, IOException exc)` | Sau khi duyệt xong các entry/descendant của thư mục, hoặc khi duyệt bị kết thúc sớm theo trường hợp cho phép | `dir`, lỗi khi duyệt nếu có |

`preVisitDirectory` và `postVisitDirectory` không chỉ mang tính thông báo. Chúng cũng trả về `FileVisitResult` để điều khiển bước tiếp theo. Ví dụ cần tạo danh sách trước/sau duyệt thư mục cho một thuật toán xóa cây: phần xử lý thư mục sau khi duyệt con là cực kỳ quan trọng.

`BasicFileAttributes` thuộc `java.nio.file.attribute`, có các phương thức như `isDirectory()`, `isRegularFile()`, `isSymbolicLink()`, `size()` và các mốc thời gian, hữu ích để hạn chế thêm thao tác filesystem.

### Tại sao nên kế thừa SimpleFileVisitor?

Triển khai trực tiếp `FileVisitor<Path>` yêu cầu viết cả bốn callback. Trong nhiều bài, ta chỉ cần ghi đè một hoặc hai phương thức và để `SimpleFileVisitor<Path>` cung cấp phần còn lại.

Mặc định của `SimpleFileVisitor` về cơ bản:

- `preVisitDirectory`: `CONTINUE`.
- `visitFile`: `CONTINUE`.
- `visitFileFailed`: ném lại `IOException` được truyền vào.
- `postVisitDirectory`: `CONTINUE` nếu `exc == null`, ngược lại ném lại lỗi.

Vì vậy, nếu muốn **bỏ qua lỗi không thể truy cập tệp** thay vì để exception làm dừng cuộc duyệt, phải chủ động ghi đè `visitFileFailed`. Còn khi muốn không đi vào một thư mục, ghi đè `preVisitDirectory` là đủ.

## 4. Bốn giá trị của FileVisitResult

Enum `java.nio.file.FileVisitResult` có đúng bốn hằng số:

| Giá trị | Tác dụng chính | Phạm vi |
| --- | --- | --- |
| `CONTINUE` | Tiếp tục cuộc duyệt | Cơ chế đi tiếp theo lịch duyệt |
| `SKIP_SUBTREE` | Không duyệt các entry bên trong **thư mục hiện tại** | Chỉ có ý nghĩa đặc biệt trong `preVisitDirectory` |
| `SKIP_SIBLINGS` | Không duyệt các phần tử **còn lại cùng cấp** với entry hiện tại | Không có nghĩa dừng toàn bộ cây |
| `TERMINATE` | Dừng toàn bộ cuộc duyệt | Toàn bộ walker |

Điều cần nhớ: `FileVisitResult` là **giá trị điều khiển**, không phải exception, và không mang nghĩa “đọc file thành công”. Bất kỳ callback nào cũng có thể ném `IOException` theo chữ ký của nó; exception đó làm kết thúc walker trừ khi đã được xử lý theo cách thích hợp trong visitor.

### 4.1 CONTINUE: đi tiếp

`CONTINUE` là trường hợp mặc định phổ biến. Khi được trả từ `preVisitDirectory(dir)`, walker sẽ cố duyệt các entry bên trong `dir`. Khi được trả từ `visitFile(file)`, walker tiếp tục tới phần tử phù hợp tiếp theo.

```java
@Override
public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
    System.out.println(file.getFileName());
    return FileVisitResult.CONTINUE;
}
```

Đây là **phương thức trong một visitor**, chưa phải chương trình độc lập. Việc trả `CONTINUE` không đảm bảo mọi file đều được thăm thành công; vẫn có thể có lỗi I/O, thư mục không đọc được, giới hạn độ sâu, hoặc nhánh khác được bỏ qua.

### 4.2 SKIP_SUBTREE: bỏ nhánh

`SKIP_SUBTREE` có ý nghĩa đặc biệt khi trả từ **`preVisitDirectory(dir)`**. Walker **không đi vào các entry của thư mục đó**. Với thư mục bị bỏ qua theo cách này, `postVisitDirectory(dir)` **không được gọi**.

```java
@Override
public FileVisitResult preVisitDirectory(
        Path dir, BasicFileAttributes attrs) {
    if (dir.getFileName() != null
            && dir.getFileName().toString().equals("build")) {
        return FileVisitResult.SKIP_SUBTREE;
    }
    return FileVisitResult.CONTINUE;
}
```

Đây là **đoạn minh họa**, cần các import của `Path`, `BasicFileAttributes`, `FileVisitResult`. Nếu gặp `build` thì không mở rộng nhánh `build`, còn các mục ngang cấp với nó vẫn có thể được duyệt.

**Điểm tinh tế:** nếu trả `SKIP_SUBTREE` từ `visitFile`, `visitFileFailed` hoặc `postVisitDirectory`, kết quả này có nghĩa **tương đương `CONTINUE`** theo Javadoc enum. Không dùng `SKIP_SUBTREE` ở `visitFile` với hy vọng bỏ qua các file còn lại trong cùng thư mục.

### 4.3 SKIP_SIBLINGS: bỏ anh em cùng cấp

`SKIP_SIBLINGS` thông báo rằng walker không nên thăm **các entry chưa thăm là anh em cùng cấp** của file hoặc directory hiện tại.

Có hai trường hợp nổi bật:

1. Trả từ **`visitFile(file)`**: không thăm các entry còn lại trong cùng thư mục cha; sau đó walker có thể tiếp tục xử lý ở cấp cha cao hơn.
2. Trả từ **`preVisitDirectory(dir)`**: **không đi vào chính `dir`**, không gọi `postVisitDirectory(dir)`, đồng thời bỏ qua các sibling chưa thăm của `dir`.

Nếu trả từ `postVisitDirectory(dir)`, ý nghĩa là không duyệt tiếp các sibling còn lại của `dir` trong thư mục cha. Nếu trả từ `visitFileFailed`, nó cũng có thể được dùng để bỏ các sibling chưa thăm của entry lỗi.

```java
@Override
public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
    if (file.getFileName().toString().equals("stop-here.flag")) {
        return FileVisitResult.SKIP_SIBLINGS;
    }
    return FileVisitResult.CONTINUE;
}
```

**Đừng nhầm `SKIP_SIBLINGS` với `TERMINATE`.** Lệnh đầu chỉ loại bỏ **những phần tử cùng cấp chưa duyệt**, nên walker vẫn có thể trở lên cây và duyệt các nhánh khác. Lệnh sau dừng toàn bộ hoạt động duyệt.

**Thứ tự không bảo đảm:** nếu một file được tìm thấy giữa các sibling, `SKIP_SIBLINGS` không quay ngược để “bỏ những entry đã duyệt” và cũng không đảm bảo file nào trong thư mục sẽ được thăm đầu tiên. Không nên dùng nó làm cách chọn “file nhỏ nhất theo tên” khi không có sắp xếp.

### 4.4 TERMINATE: kết thúc ngay

`TERMINATE` là cách chủ động dừng **toàn bộ cuộc duyệt**. Ví dụ:

```java
@Override
public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
    if (file.getFileName().toString().equals("found.txt")) {
        found = file;
        return FileVisitResult.TERMINATE;
    }
    return FileVisitResult.CONTINUE;
}
```

Đây là **đoạn minh họa** trong một visitor có trường `Path found`. Sau khi callback trả `TERMINATE`, walker ngừng gọi các phương thức thăm tiếp theo. Không mong đợi `postVisitDirectory` chạy cho các thư mục còn nằm trên stack. Nếu có tài nguyên nghiệp vụ mở bên trong visitor, phải tự quản lý việc đóng chúng chứ không giả định post-visit đóng vai trò `finally`.

**Định nghĩa “kết thúc thành công”:** `TERMINATE` bản thân không phải lỗi. `Files.walkFileTree` có thể trả về bình thường sau khi visitor yêu cầu dừng; nếu caller cần biết đã tìm thấy mục nào, phải tự lưu trạng thái/cung cấp kết quả (ví dụ biến `found`).

## 5. Ma trận: trả giá trị nào từ callback nào?

| Callback | `CONTINUE` | `SKIP_SUBTREE` | `SKIP_SIBLINGS` | `TERMINATE` |
| --- | --- | --- | --- | --- |
| `preVisitDirectory` | Đi vào thư mục | Không đi vào subtree; không gọi post cho dir này | Không đi vào dir, không gọi post; bỏ sibling của dir | Dừng tất cả |
| `visitFile` | Xét mục kế tiếp | Tương đương CONTINUE | Bỏ sibling chưa xét của file | Dừng tất cả |
| `visitFileFailed` | Bỏ qua lỗi entry và đi tiếp | Tương đương CONTINUE | Bỏ sibling chưa xét của entry lỗi | Dừng tất cả |
| `postVisitDirectory` | Đi tiếp sau thư mục | Tương đương CONTINUE | Bỏ sibling chưa xét của thư mục | Dừng tất cả |

Có hai nhận xét cần thuộc:

- **Chỉ `preVisitDirectory` mới dùng `SKIP_SUBTREE` để chặn việc đi xuống một thư mục**. Đừng trả nó từ `postVisitDirectory` rồi nghĩ sẽ thay đổi những entry đã thăm.
- **`SKIP_SIBLINGS` từ `preVisitDirectory` vừa bỏ subtree của chính directory ấy vừa bỏ các sibling chưa thăm**. Đây là hành vi mạnh hơn `SKIP_SUBTREE`.

Khi callback trả `SKIP_SIBLINGS` vì file đã thăm trong một directory, iteration của directory có thể hoàn tất sớm; tài liệu FileVisitor nêu rằng `postVisitDirectory` của directory đó vẫn có thể được gọi sau việc kết thúc iteration sớm ấy.

## 6. Ví dụ Java 17 chạy được: dùng SKIP_SUBTREE mà vẫn duyệt nhánh khác

Ví dụ tự tạo một cây nhỏ trong thư mục tạm, chỉ có hai thư mục con `keep` và `ignored`. Visitor sẽ bỏ nhánh `ignored` bất kể thứ tự duyệt do filesystem cung cấp. Đầu ra là số lượng, không dựa vào thứ tự sự kiện.

**Tệp `SkipSubtreeDemo.java`:**

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class SkipSubtreeDemo {
    static final class Visitor extends SimpleFileVisitor<Path> {
        int seenFiles;
        int enteredDirectories;
        boolean ignoredPostCalled;

        @Override
        public FileVisitResult preVisitDirectory(
                Path dir, BasicFileAttributes attrs) {
            if (dir.getFileName() != null
                    && dir.getFileName().toString().equals("ignored")) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            enteredDirectories++;
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(
                Path file, BasicFileAttributes attrs) {
            seenFiles++;
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(
                Path dir, IOException exc) throws IOException {
            if (dir.getFileName() != null
                    && dir.getFileName().toString().equals("ignored")) {
                ignoredPostCalled = true;
            }
            if (exc != null) {
                throw exc;
            }
            return FileVisitResult.CONTINUE;
        }
    }

    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("walk-result-");
        Path keep = root.resolve("keep");
        Path ignored = root.resolve("ignored");

        try {
            Files.createDirectory(keep);
            Files.createDirectory(ignored);
            Files.writeString(keep.resolve("a.txt"), "ok",
                    StandardCharsets.UTF_8);
            Files.writeString(ignored.resolve("hidden.txt"), "secret",
                    StandardCharsets.UTF_8);

            Visitor visitor = new Visitor();
            Files.walkFileTree(root, visitor);

            System.out.println("Entered dirs: "
                    + visitor.enteredDirectories);
            System.out.println("Visited files: " + visitor.seenFiles);
            System.out.println("Ignored post called: "
                    + visitor.ignoredPostCalled);
        } finally {
            Files.deleteIfExists(keep.resolve("a.txt"));
            Files.deleteIfExists(ignored.resolve("hidden.txt"));
            Files.deleteIfExists(keep);
            Files.deleteIfExists(ignored);
            Files.deleteIfExists(root);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac SkipSubtreeDemo.java
java SkipSubtreeDemo
```

**Stdout chính xác khi các thao tác I/O thành công:**

```text
Entered dirs: 2
Visited files: 1
Ignored post called: false
```

Giải thích: `preVisitDirectory` của `root` và `keep` trả CONTINUE nên `enteredDirectories = 2`. Nhánh `ignored` bị nhận diện ở pre-visit rồi trả SKIP_SUBTREE, nên `hidden.txt` không được thăm và `postVisitDirectory(ignored)` không xảy ra. File `a.txt` trong `keep` vẫn được thăm.

Đây là **ví dụ tự kiểm soát cấu trúc cây**. Ở môi trường thực tế, filesystem có thể bị thay đổi đồng thời hoặc quyền truy cập khác dự kiến, nên khi error/cleanup xảy ra chương trình có thể ném `IOException` thay vì in các dòng trên.

## 7. Ví dụ Java 17: SKIP_SIBLINGS không phải TERMINATE

Tạo một cây mà thư mục `group` chứa hai file; sau khi thăm file đầu tiên (file nào trước là không xác định), visitor trả `SKIP_SIBLINGS`. Hai file không được thăm hết, nhưng thư mục cha có thể hoàn tất callback post. Ví dụ dùng số lượng để không phụ thuộc thứ tự:

**Tệp `SkipSiblingsDemo.java`:**

```java
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class SkipSiblingsDemo {
    static class Visitor extends SimpleFileVisitor<Path> {
        int filesVisited;
        boolean groupPost;

        @Override
        public FileVisitResult visitFile(
                Path file, BasicFileAttributes attrs) {
            filesVisited++;
            return FileVisitResult.SKIP_SIBLINGS;
        }

        @Override
        public FileVisitResult postVisitDirectory(
                Path dir, IOException exc) throws IOException {
            if (dir.getFileName() != null
                    && dir.getFileName().toString().equals("group")) {
                groupPost = true;
            }
            if (exc != null) {
                throw exc;
            }
            return FileVisitResult.CONTINUE;
        }
    }

    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("siblings-");
        Path group = root.resolve("group");

        try {
            Files.createDirectory(group);
            Files.writeString(group.resolve("first.txt"), "1");
            Files.writeString(group.resolve("second.txt"), "2");

            Visitor visitor = new Visitor();
            Files.walkFileTree(root, visitor);

            System.out.println("Files visited: " + visitor.filesVisited);
            System.out.println("Group post called: " + visitor.groupPost);
        } finally {
            Files.deleteIfExists(group.resolve("first.txt"));
            Files.deleteIfExists(group.resolve("second.txt"));
            Files.deleteIfExists(group);
            Files.deleteIfExists(root);
        }
    }
}
```

Chạy:

```bash
javac SkipSiblingsDemo.java
java SkipSiblingsDemo
```

**Stdout chính xác khi I/O thành công:**

```text
Files visited: 1
Group post called: true
```

Lưu ý: `SKIP_SIBLINGS` từ **`visitFile`** làm kết thúc sớm việc duyệt các mục còn lại trong thư mục `group`, nhưng `postVisitDirectory(group)` vẫn được gọi. Điều này **khác** việc trả `SKIP_SIBLINGS` từ **`preVisitDirectory(group)`**, khi đó `group` chưa được đi vào, các entry bị bỏ qua và `postVisitDirectory(group)` không được gọi.

Trên một cây thật với nhiều directory ở các nhánh, lệnh `SKIP_SIBLINGS` ở `group` không tự động dừng các nhánh ở cấp cao khác.

## 8. Ví dụ Java 17: TERMINATE dừng cả cây

Chương trình có đúng một file trong thư mục tạm để tạo kết quả độc lập với thứ tự filesystem. Callback `visitFile` trả `TERMINATE`, ngăn `postVisitDirectory(root)` được gọi.

**Tệp `TerminateWalkDemo.java`:**

```java
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class TerminateWalkDemo {
    static class Visitor extends SimpleFileVisitor<Path> {
        int seen;
        boolean rootPost;

        @Override
        public FileVisitResult visitFile(
                Path file, BasicFileAttributes attrs) {
            seen++;
            return FileVisitResult.TERMINATE;
        }

        @Override
        public FileVisitResult postVisitDirectory(
                Path dir, IOException exc) throws IOException {
            rootPost = true;
            if (exc != null) {
                throw exc;
            }
            return FileVisitResult.CONTINUE;
        }
    }

    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("terminate-");

        try {
            Files.writeString(root.resolve("item.txt"), "x");
            Visitor visitor = new Visitor();

            Path returned = Files.walkFileTree(root, visitor);

            System.out.println("Visited files: " + visitor.seen);
            System.out.println("Root post called: " + visitor.rootPost);
            System.out.println("Returned start: " + returned.equals(root));
        } finally {
            Files.deleteIfExists(root.resolve("item.txt"));
            Files.deleteIfExists(root);
        }
    }
}
```

Biên dịch/chạy:

```bash
javac TerminateWalkDemo.java
java TerminateWalkDemo
```

**Stdout chính xác khi file tạm được tạo/xóa thành công:**

```text
Visited files: 1
Root post called: false
Returned start: true
```

Nếu callback `visitFile` ném một `IOException` thay vì trả `TERMINATE`, `Files.walkFileTree` có thể ném lỗi cho caller. Hai hướng điều khiển này mang ngữ nghĩa khác nhau: `TERMINATE` là **quyết định ngừng có chủ đích**, còn ném exception thường biểu thị **thất bại phải truyền ra ngoài**.

## 9. Trường hợp dễ nhầm: SKIP_SUBTREE trả từ visitFile

Vì `SKIP_SUBTREE` chỉ có ý nghĩa đặc biệt trong `preVisitDirectory`, việc trả nó từ `visitFile` không làm bỏ qua sibling, cũng không có subtree nào để cắt tại thời điểm callback file.

**Tệp `SkipSubtreeFromFileDemo.java`:**

```java
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class SkipSubtreeFromFileDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("skip-file-");
        try {
            Files.writeString(root.resolve("one.txt"), "1");
            Files.writeString(root.resolve("two.txt"), "2");

            final int[] seen = {0};
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(
                        Path file, BasicFileAttributes attrs) {
                    seen[0]++;
                    return FileVisitResult.SKIP_SUBTREE;
                }
            });

            System.out.println("Files visited: " + seen[0]);
        } finally {
            Files.deleteIfExists(root.resolve("one.txt"));
            Files.deleteIfExists(root.resolve("two.txt"));
            Files.deleteIfExists(root);
        }
    }
}
```

Biên dịch/chạy:

```bash
javac SkipSubtreeFromFileDemo.java
java SkipSubtreeFromFileDemo
```

**Stdout chính xác khi thao tác thành công:**

```text
Files visited: 2
```

Điều này minh họa quy tắc của enum: từ `visitFile`, `SKIP_SUBTREE` có tác dụng như `CONTINUE`. Muốn bỏ phần còn lại **cùng cấp**, dùng `SKIP_SIBLINGS`; muốn bỏ **nhánh directory** trước khi đi vào, dùng `SKIP_SUBTREE` ở `preVisitDirectory`.

## 10. Xử lý visitFileFailed: tiếp tục hay dừng?

Một file hoặc directory có thể không được thăm vì quyền, đường dẫn biến mất, lỗi đọc thuộc tính, vòng liên kết (khi có `FOLLOW_LINKS`) hoặc nguyên nhân I/O khác. `visitFileFailed(path, exception)` cho phép visitor quyết định phản ứng.

Ví dụ **bỏ qua lỗi riêng lẻ và tiếp tục**:

```java
@Override
public FileVisitResult visitFileFailed(
        Path file, IOException exc) {
    System.err.println("Cannot visit " + file + ": " + exc);
    return FileVisitResult.CONTINUE;
}
```

Đây là phương thức minh họa trong một `SimpleFileVisitor<Path>`; không phải class độc lập. Chỉ dùng chính sách này nếu nghiệp vụ chấp nhận **kết quả không đầy đủ**. Ví dụ quét file để hiển thị những phần truy cập được có thể hợp lý, còn kiểm tra toàn vẹn dữ liệu backup phải ghi nhận rõ tất cả lỗi hoặc từ chối tuyên bố “đã quét đủ”.

Nếu gặp lỗi **không thể bỏ qua**, thường tốt hơn là ném lại `exc`:

```java
@Override
public FileVisitResult visitFileFailed(
        Path file, IOException exc) throws IOException {
    throw exc;
}
```

Điều đó cho caller biết cuộc duyệt thất bại bằng `IOException`. Trả `TERMINATE` cũng ngừng duyệt nhưng **không tự giữ exception trong kết quả**; nếu cần truyền lý do, phải lưu lại trạng thái hoặc ném lỗi có chủ đích.

### Có dùng SKIP_SIBLINGS khi file bị lỗi không?

Có thể: nếu hệ thống muốn bỏ những mục cùng cấp với entry không truy cập được, visitor có thể trả `SKIP_SIBLINGS` từ `visitFileFailed`. Tuy nhiên đây là một chính sách khá mạnh, dễ bỏ lỡ dữ liệu lành ở cùng thư mục. Chỉ dùng khi giao thức duyệt định nghĩa rõ lý do.

### File không đọc được có luôn gọi visitFileFailed không?

Không nên áp đặt một nguyên nhân duy nhất. `visitFileFailed` có thể nhận lỗi đọc metadata, lỗi mở directory và các nguyên nhân làm không thể thăm entry. Trong khi đó, **lỗi khi đang lặp entry của một directory đã mở** có thể được báo qua tham số `exc` của `postVisitDirectory`, không phải lúc nào cũng là một `visitFileFailed` trực tiếp.

## 11. postVisitDirectory: exc khác null nghĩa là gì?

`postVisitDirectory(dir, exc)` được gọi sau khi walker đã duyệt các entry của directory hoặc khi việc lặp directory kết thúc sớm theo trường hợp hợp lệ.

- `exc == null`: không có lỗi I/O khi thực hiện iteration directory được báo qua tham số này; tuy nhiên các lỗi entry trước đó có thể đã được xử lý riêng bằng `visitFileFailed`.
- `exc != null`: có lỗi I/O khiến iteration directory kết thúc bất thường, cần quyết định truyền lỗi hay tiếp tục.
- `postVisitDirectory` vẫn có thể được gọi khi một `visitFile` trả `SKIP_SIBLINGS` và iteration của directory kết thúc sớm.
- `postVisitDirectory(dir)` **không được gọi** đối với dir bị bỏ qua trực tiếp do `preVisitDirectory(dir)` trả `SKIP_SUBTREE` hoặc `SKIP_SIBLINGS`.
- Nếu walker đã bị `TERMINATE`, không chờ các post-callback như một cơ chế dọn dẹp chắc chắn.

Mẫu bảo toàn lỗi thường dùng:

```java
@Override
public FileVisitResult postVisitDirectory(
        Path dir, IOException exc) throws IOException {
    if (exc != null) {
        throw exc;
    }
    return FileVisitResult.CONTINUE;
}
```

Đây là đoạn minh họa. Nếu cần ghi report cho một directory sau khi hoàn tất con, thực hiện ở đây, nhưng đừng dựa vào callback này để **luôn luôn** chạy khi đã quyết định bỏ nhánh hoặc terminate.

## 12. Giá trị trả về và exception: hai cơ chế riêng

| Tình huống | Cơ chế phù hợp |
| --- | --- |
| Đủ dữ liệu, chủ động dừng tìm kiếm | `TERMINATE` |
| Thư mục không nằm trong phạm vi xử lý | `SKIP_SUBTREE` từ pre-visit |
| Không cần các entry cùng cấp còn lại | `SKIP_SIBLINGS` |
| Đọc lỗi và nghiệp vụ không thể tiếp tục đúng | Ném `IOException` |
| Đọc lỗi nhưng chấp nhận danh sách thiếu | Ghi nhận lỗi, trả `CONTINUE` |
| Cần thoát kèm một kết quả | Ghi kết quả trong visitor, sau walker đọc lại trạng thái |

**Đừng dùng `TERMINATE` làm cách “nuốt lỗi”** trong tác vụ mà caller cần biết rằng kết quả không hoàn chỉnh. Ngược lại, đừng dùng exception như một lệnh điều khiển thông thường khi đã tìm thấy file và không có lỗi: `TERMINATE` diễn đạt ý định rõ hơn.

Trong các framework phức tạp, visitor có thể lưu `List<IOException>` để thống kê lỗi rồi tiếp tục; nhưng cần giới hạn bộ nhớ và tránh in đường dẫn/tên file nhạy cảm vào log không phù hợp.

## 13. maxDepth có ảnh hưởng callback không?

Có. Overload:

```java
Files.walkFileTree(start, options, maxDepth, visitor);
```

cho phép giới hạn độ sâu tối đa. Root bắt đầu ở depth 0. Nếu `maxDepth = 0`, walker chỉ thăm chính start entry (dưới dạng `visitFile` ở giới hạn độ sâu, kể cả khi là directory). Ở độ sâu tối đa, **directory không còn được mở rộng thành các entry con**.

Khi directory ở `maxDepth`, nó có thể được thăm thông qua **`visitFile`** thay vì `preVisitDirectory`, vì walker không tiếp tục vào nhánh đó. Do vậy:

- Không nên mặc định mọi `Path` biểu thị directory luôn đi qua `preVisitDirectory`.
- Nếu cần đếm tất cả directory, phải xác định rõ quy tắc khi gặp maxDepth.
- `SKIP_SUBTREE` từ `visitFile` vẫn tương đương `CONTINUE`.

Chọn `maxDepth` bằng một số có chủ đích để tránh duyệt quá sâu khi nghiệp vụ không cần; điều này bổ sung cho chính sách `SKIP_SUBTREE`, không thay thế nó.

## 14. Symbolic links và FOLLOW_LINKS

Mặc định, `Files.walkFileTree` **không theo symbolic link**. Một symbolic link thường được xem như entry không đi vào, được chuyển qua `visitFile`.

Khi bật `FileVisitOption.FOLLOW_LINKS`, walker có thể đi theo link đến thư mục và làm cây có đường quay ngược hoặc vòng lặp. API có cơ chế phát hiện vòng lặp; lỗi `FileSystemLoopException` có thể được đưa vào `visitFileFailed`.

```java
Files.walkFileTree(
        start,
        EnumSet.of(FileVisitOption.FOLLOW_LINKS),
        Integer.MAX_VALUE,
        visitor);
```

Đây là đoạn minh họa. Cần hiểu hệ quả an ninh: một symlink trong cây được duyệt có thể trỏ tới dữ liệu **ngoài root ban đầu**. Nếu chỉ muốn quét dưới một ranh giới filesystem thực, không thể xem `SKIP_SUBTREE` hoặc kiểm tra chuỗi `Path.normalize()` là cơ chế bảo mật hoàn chỉnh.

Có thể không theo link, dùng `maxDepth`, kiểm tra real paths và kiểm soát quyền filesystem tùy yêu cầu; vẫn phải xem xét race condition khi thư mục bị đổi trong lúc duyệt.

### Nếu một thư mục xuất hiện dưới dạng symbolic link thì tên dir có giống nhau không?

Khi walker theo link, callback có thể nhận một `Path` mang vị trí link được duyệt, không tự động biến tất cả Path thành canonical real path. Không dùng `dir.getFileName().toString()` làm ranh giới bảo mật nếu một directory có thể được đặt lại tên hoặc symlink trỏ sang nơi khác. Các ví dụ bỏ `ignored` theo tên chỉ dành cho chính sách lọc đơn giản trong một cây được kiểm soát.

## 15. Có chắc thứ tự siblings là ổn định không?

**Không.** `Files.walkFileTree` không hứa sẽ duyệt các entry trong mỗi thư mục theo thứ tự tên. Các filesystem khác nhau, trạng thái directory và các thay đổi đồng thời có thể tạo thứ tự khác nhau.

Do đó, với mã:

```java
@Override
public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
    return FileVisitResult.SKIP_SIBLINGS;
}
```

“Bỏ sibling” nghĩa là **sibling chưa được duyệt**, không phải “tất cả file có tên đứng sau trong bảng chữ cái”. Nếu nghiệp vụ cần tìm **file có tên từ điển nhỏ nhất**, cần liệt kê và sắp xếp có chủ đích trước khi chọn; không dựa vào một traversal order ngầm.

Một chiến lược kiểm thử ổn định là:
- Tạo cây test có đúng một mục khi kiểm tra `TERMINATE`.
- Khi kiểm tra `SKIP_SIBLINGS`, chỉ assert **số mục được thăm** thay vì tên file đầu tiên.
- Khi kiểm tra `SKIP_SUBTREE`, assert một nhánh không được đi vào, trong khi các nhánh khác vẫn được xử lý.
- Không viết unit test yêu cầu chuỗi callback được phát theo thứ tự alphabetical trừ khi code tự đảm bảo thứ tự đó.

## 16. Bẫy khi dùng FileVisitResult để xóa cây thư mục

Một ứng dụng xóa đệ quy thường:

1. Xóa file trong `visitFile`.
2. Xóa directory trong `postVisitDirectory` (sau khi xóa các entry).

Nhưng nếu `preVisitDirectory` trả `SKIP_SUBTREE` cho một directory, **`postVisitDirectory` của directory đó không chạy**. Điều này có thể khiến folder vẫn còn nguyên, rồi nỗ lực xóa parent sau đó thất bại vì chưa rỗng.

Tương tự, khi `TERMINATE` xảy ra, nhiều post-callback chưa được thực thi. Không đặt công việc dọn dẹp bắt buộc (đóng resource hoặc xóa file tạm riêng) **chỉ trong `postVisitDirectory`** rồi giả định nó giống `finally`.

Với xóa đệ quy, phải xác định chính sách xử lý nhánh không xóa được và lỗi quyền truy cập. Khi phần còn lại của subtree bị bỏ qua, có thể cần giữ directory cha hoặc báo lỗi, không tự coi công việc hoàn thành.

**An toàn:** ví dụ bài học không tự động xóa cây tùy ý của người dùng. Các demo chỉ tạo và xóa chính những tệp/thư mục tạm mà chương trình đã tạo trong một phạm vi kiểm soát.

## 17. Duyệt cây để tìm file: dùng TERMINATE hay SKIP_SIBLINGS?

Giả sử tìm file tên `config.json` ở bất cứ đâu:

```java
@Override
public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
    if ("config.json".equals(file.getFileName().toString())) {
        found = file;
        return FileVisitResult.TERMINATE;
    }
    return FileVisitResult.CONTINUE;
}
```

Nếu đã có kết quả đầu tiên và chỉ cần một file, `TERMINATE` đúng nghĩa hơn: **dừng toàn bộ cây**. Nếu trả `SKIP_SIBLINGS`, walker có thể quay lên duyệt các nhánh khác và tìm thêm file cùng tên, không phải hành vi mong muốn.

Ngược lại, nếu nghiệp vụ là “với mỗi directory, chỉ cần một file thỏa điều kiện, sau đó tiếp tục các directory khác”, `SKIP_SIBLINGS` tại một file có thể hợp lý, nhưng phải biết cấu trúc cây và nơi callback đang đứng. Nó loại các sibling còn lại tại cấp đó, không tự tạo quy tắc “mỗi thư mục đúng một file” trong mọi cây đệ quy phức tạp.

## 18. Một visitor cấu hình quy tắc duyệt

Trong ứng dụng thật, có thể xây một class visitor chịu trách nhiệm:

- Danh sách tên directory cần bỏ qua.
- Bộ đếm file và thống kê kích thước.
- Điều kiện tìm file để dừng.
- Chính sách lỗi (fail-fast hay best-effort).
- Giới hạn độ sâu, liên kết tượng trưng.

**Khung minh họa**:

```java
static class SelectiveVisitor extends SimpleFileVisitor<Path> {
    private final Set<String> omittedNames;
    private final String targetName;
    private Path found;

    SelectiveVisitor(Set<String> omittedNames, String targetName) {
        this.omittedNames = omittedNames;
        this.targetName = targetName;
    }

    @Override
    public FileVisitResult preVisitDirectory(
            Path dir, BasicFileAttributes attrs) {
        Path name = dir.getFileName();
        if (name != null
                && omittedNames.contains(name.toString())) {
            return FileVisitResult.SKIP_SUBTREE;
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(
            Path file, BasicFileAttributes attrs) {
        if (file.getFileName().toString().equals(targetName)) {
            found = file;
            return FileVisitResult.TERMINATE;
        }
        return FileVisitResult.CONTINUE;
    }

    Path found() {
        return found;
    }
}
```

Đây là **class minh họa**, yêu cầu import `Set`, `Path`, `SimpleFileVisitor`, `BasicFileAttributes`, `FileVisitResult`; không phải chương trình độc lập. Có thể dùng nó để tìm file đầu tiên dưới các nhánh được phép mà không mở rộng directory nằm trong `omittedNames`.

Lưu ý **tên trùng nhau ở nhiều vị trí**: áp dụng quy tắc theo `dir.getFileName()` sẽ bỏ qua mọi directory có tên ấy, không chỉ một path cụ thể. Nếu cần bỏ một subtree chính xác, so sánh Path đã xác định hoặc sử dụng một predicate có hợp đồng rõ ràng.

## 19. Các lỗi phổ biến

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Trả `SKIP_SUBTREE` từ `visitFile` để bỏ sibling | Không bỏ sibling; hành vi như `CONTINUE` | Dùng `SKIP_SIBLINGS` nếu đúng nhu cầu |
| Trả `SKIP_SIBLINGS` nhưng mong dừng cả cây | Các nhánh cấp trên khác còn có thể được thăm | Dùng `TERMINATE` |
| Trả `SKIP_SIBLINGS` trong `preVisitDirectory` nhưng mong vào dir hiện tại | Chính dir cũng không được đi vào | Dùng `CONTINUE` hoặc `SKIP_SUBTREE` tùy ý định |
| Dựa vào `postVisitDirectory` luôn chạy | Không chạy khi pre-visit skip hay terminate | Quản lý resource bằng `try-with-resources`/`finally` |
| Giả định duyệt theo tên alphabetical | Chọn “file đầu tiên” không ổn định | Liệt kê/sắp xếp có chủ đích |
| Coi `TERMINATE` là lỗi Java | Caller không biết lý do nếu không có state | Dùng result state hoặc exception phù hợp |
| Bỏ qua mọi `visitFileFailed` mà không báo | Kết quả thiếu nhưng trông như đầy đủ | Ghi nhận lỗi hoặc fail-fast |
| Bỏ qua `exc` ở `postVisitDirectory` | Lỗi iteration directory có thể bị mất | Xem/throw `exc` theo policy |
| Cho rằng maxDepth chỉ ảnh hưởng số file, không ảnh hưởng callback | Directory ở maxDepth có thể được thăm như file | Thiết kế đếm và logic đúng |
| Bật FOLLOW_LINKS mà không kiểm soát | Có thể đi ra ngoài root hoặc gặp vòng | Xác định policy link, lỗi và giới hạn |
| Lọc `Files.walk(...).filter(...)` rồi nghĩ đã prune subtree | Walker vẫn có thể đi vào nhánh đã lọc | Dùng `walkFileTree` và pre-visit |
| Đọc số liệu attr từ các thời điểm khác nhau rồi coi nhất quán tuyệt đối | File có thể đổi trong lúc quét | Xác định tolerance/snapshot strategy |
| Duyệt/xóa trên path không tin cậy chỉ với normalize | Symlink/traversal/race không được ngăn | Áp dụng kiểm soát quyền và cơ chế filesystem-aware |

## 20. Checklist trước khi triển khai

Khi viết visitor, trả lời rõ:

1. **Điểm bắt đầu** là file hay directory? Có khả năng symlink không?
2. Có cần theo symbolic link không? `FOLLOW_LINKS` ảnh hưởng biên truy cập ra sao?
3. `maxDepth` bao nhiêu, và directory ở giới hạn được tính thế nào?
4. Muốn bỏ đúng một directory hay mọi directory có cùng tên?
5. Có cần duyệt đầy đủ hay được phép thiếu nhánh?
6. Đang định trả `SKIP_SUBTREE` từ **pre-visit** hay nhầm callback?
7. `SKIP_SIBLINGS` có phù hợp với mục tiêu từng directory không?
8. Khi tìm được file, có cần trả `TERMINATE` và lưu `found`?
9. Lỗi đọc metadata/directory được xử lý tại `visitFileFailed`/`postVisitDirectory` thế nào?
10. Có giả định thứ tự sibling nào không được API bảo đảm?
11. Có tác vụ hậu xử lý nào đặt trong post-visit nhưng sẽ bị bỏ qua khi skip/terminate không?
12. Nếu filesystem thay đổi đồng thời, kết quả quét được mô tả là snapshot hay best-effort?

## 21. Bài tập thực hành

1. Chạy `SkipSubtreeDemo`; đổi tên `ignored` thành `cache` cả ở cây và visitor, kiểm tra số file vẫn bằng 1.
2. Thêm một file thứ hai vào `keep`; giải thích vì sao `SKIP_SUBTREE` chỉ bỏ `ignored`, không ảnh hưởng `keep`.
3. Trong `SkipSiblingsDemo`, đổi kết quả `visitFile` từ `SKIP_SIBLINGS` thành `CONTINUE`, dự đoán số file sẽ thành 2 và `groupPost` vẫn true.
4. Đổi `SKIP_SIBLINGS` thành `SKIP_SUBTREE` trong `visitFile`, giải thích vì sao vẫn duyệt 2 file.
5. Trong `TerminateWalkDemo`, chuyển `TERMINATE` thành `CONTINUE`, kiểm tra `rootPost` trở thành true.
6. Thử `preVisitDirectory(root)` trả `SKIP_SUBTREE` và ghi lại việc `visitFile`/`postVisitDirectory(root)` có được gọi không.
7. Viết visitor dùng `visitFileFailed` để ghi log và `CONTINUE`, sau đó so sánh với phiên bản ném lại `exc`. Thảo luận tình huống quyền truy cập thay đổi đồng thời.
8. Tạo visitor đếm số directory và file với `maxDepth=0`, `1`, `2`. Ghi rõ callback nào nhận directory khi đến mức giới hạn.
9. Dùng `FileVisitOption.FOLLOW_LINKS` với cây kiểm thử có symbolic link vòng (trên filesystem hỗ trợ); kiểm tra `visitFileFailed` nhận loại exception gì.
10. Viết visitor tìm tên file cụ thể, lưu vào `found` và trả `TERMINATE`. Đảm bảo caller phân biệt “không tìm thấy” với “lỗi trong khi duyệt”.
11. Dùng `SKIP_SIBLINGS` tại `preVisitDirectory` trong một cây có hai thư mục cùng cấp; giải thích vì sao chỉ có một số nhánh được duyệt và thứ tự không được bảo đảm.
12. Mô phỏng logic xóa cây có post-visit rồi chủ động skip một directory; giải thích vì sao parent có thể chưa rỗng khi đến lượt xử lý.
13. So sánh `Files.walkFileTree` với `Files.walk`: khi nào filter đủ và khi nào nhất thiết phải prune subtree?
14. Viết một test không phụ thuộc thứ tự filesystem để xác minh đủ cả bốn giá trị `FileVisitResult`.

## 22. Tổng kết

`FileVisitResult` là ngôn ngữ điều khiển hành trình duyệt cây thư mục của `Files.walkFileTree`:

- **`CONTINUE`:** tiếp tục duyệt theo cơ chế bình thường.
- **`SKIP_SUBTREE`:** bỏ entry bên trong directory **khi được trả từ `preVisitDirectory`**. Từ callback khác, nó tương đương `CONTINUE`.
- **`SKIP_SIBLINGS`:** bỏ những entry **chưa duyệt cùng cấp**. Nếu trả từ pre-visit, chính directory đó cũng không được đi vào và không có post-visit cho nó.
- **`TERMINATE`:** dừng toàn bộ hành trình, không chờ phần post-visit còn lại.

Đừng nhầm các giá trị điều khiển với ngoại lệ: khi lỗi I/O cần được truyền cho caller, ném `IOException` hoặc lưu trạng thái lỗi một cách rõ ràng. Đừng giả định thứ tự các sibling hay cho rằng `postVisitDirectory` luôn được gọi. Với yêu cầu duyệt hoặc xóa có liên quan đến bảo mật, đặc biệt khi dùng symbolic link, phải xem xét provider, quyền truy cập, thay đổi đồng thời và giới hạn độ sâu.

### Tài liệu tham khảo

- [Java SE 17: `FileVisitResult`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileVisitResult.html) — bốn giá trị và phạm vi ngữ nghĩa.
- [Java SE 17: `FileVisitor`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileVisitor.html) — bốn callback và khi nào được gọi.
- [Java SE 17: `SimpleFileVisitor`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/SimpleFileVisitor.html) — hành vi mặc định và xử lý lỗi.
- [Java SE 17: `Files.walkFileTree`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#walkFileTree(java.nio.file.Path,java.nio.file.FileVisitor)) — duyệt cây với visitor.
- [Dev.java: Walking the File Tree](https://dev.java/learn/api/io/java-io/file-system/walking-tree/) — các ví dụ duyệt/bỏ nhánh và cách điều khiển.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="directory-tree-traversal-008">FOLLOW_LINKS, vòng liên kết và FileSystemLoopException</a>

<details>
<summary>Xem chi tiết</summary>

# FOLLOW_LINKS, vòng liên kết và FileSystemLoopException trong Java

Khi duyệt cây thư mục với Java NIO.2, **symbolic link (liên kết tượng trưng)** có thể trỏ tới file, thư mục khác, thư mục nằm ngoài gốc duyệt, hoặc trỏ ngược về chính một tổ tiên. Nếu đi theo liên kết mà không hiểu cơ chế này, chương trình có thể đọc trùng dữ liệu, đi ra ngoài phạm vi dự kiến, gặp chu trình (*cycle*) hoặc nhận lỗi `FileSystemLoopException`.

Các API chủ yếu liên quan:

- `Files.walkFileTree(...)`: duyệt cây và gọi các callback của `FileVisitor`; hỗ trợ tùy chọn `FileVisitOption.FOLLOW_LINKS`.
- `Files.walk(...)` và `Files.find(...)`: tạo `Stream<Path>` và cũng có thể đi theo liên kết nếu truyền `FileVisitOption.FOLLOW_LINKS`.
- `FileSystemLoopException`: ngoại lệ khi quá trình duyệt phát hiện một vòng liên kết trong lúc cố đi theo symbolic link; cách nhận/lan truyền lỗi khác nhau giữa visitor và stream.
- `Files.isSymbolicLink(...)` và `LinkOption.NOFOLLOW_LINKS`: giúp kiểm tra **bản thân liên kết** thay vì mặc định xem thuộc tính của mục tiêu.

**Mặc định, Java không đi theo symbolic link khi duyệt cây với những API trên.** Chỉ bật `FOLLOW_LINKS` khi nghiệp vụ thực sự yêu cầu khám phá cả những thư mục được liên kết tới, và cần có chiến lược về quyền truy cập, giới hạn độ sâu, vòng liên kết và đường dẫn ngoài phạm vi.

## 1. Symbolic link là gì? Vì sao có vòng lặp?

Symbolic link là một entry của hệ thống tệp trỏ đến một đường dẫn khác. Nó không giống “sao chép thư mục”: đi theo symlink đưa chương trình tới mục tiêu hiện tại của liên kết. Symlink có thể trỏ tới file, thư mục hoặc đường dẫn đã mất (*dangling link*).

Ví dụ:

```text
workspace/
└── project/
    ├── README.md
    └── back -> workspace/      (symbolic link trỏ về tổ tiên)

Duyệt workspace/ có FOLLOW_LINKS:
workspace/
  -> project/
      -> back (trỏ lại workspace/)
          -> project/
              -> back
                  -> ...
```

Trên một cây thực không có vòng liên kết, duyệt sâu theo `project` rồi hết các file con là hữu hạn. Nhưng khi `back` đưa ta quay lại `workspace`, đường đi có thể lặp **vô hạn về mặt cấu trúc** nếu không có kiểm tra vòng.

Một ví dụ khác không phải vòng trực tiếp:

```text
safe-root/
└── shortcut -> /other/location/
```

Nếu bật `FOLLOW_LINKS`, chương trình có thể duyệt nội dung `/other/location`, dù vị trí đó **nằm ngoài thư mục gốc ban đầu**. Không có `FileSystemLoopException` không có nghĩa là đã giữ đúng ranh giới dữ liệu.

## 2. Mặc định không theo link khác gì khi bật FOLLOW_LINKS?

Với `Files.walkFileTree`:

```java
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;

// Minh họa: root là Path đã xác định; xử lý IOException.
Files.walkFileTree(root, new SimpleFileVisitor<Path>() {});
```

Overload này **không theo symbolic link**. Nếu gặp một symlink, visitor thông thường nhận nó ở `visitFile(...)` như một entry dạng file/leaf, kể cả khi nó trỏ tới thư mục. Không có việc tự động mở và duyệt thư mục đích qua liên kết.

Để yêu cầu đi theo symbolic link, dùng overload có tập tùy chọn:

```java
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.util.EnumSet;

// Minh họa: root là Path đã xác định; xử lý IOException.
Files.walkFileTree(
        root,
        EnumSet.of(FileVisitOption.FOLLOW_LINKS),
        Integer.MAX_VALUE,
        new SimpleFileVisitor<Path>() {}
);
```

Hai điểm quan trọng:

1. `FileVisitOption.FOLLOW_LINKS` có nghĩa là **khi gặp liên kết tới thư mục, cố đi vào thư mục đích** thay vì chỉ xử lý bản thân entry link. Nếu đích không thể truy cập, chương trình phải xử lý lỗi thích hợp.
2. `Integer.MAX_VALUE` ở đây là `maxDepth`: số cấp tối đa tính từ root, **không phải** giới hạn số lượng file hay giới hạn bộ nhớ. Chỉ dùng giới hạn rất sâu khi đã hiểu quy mô dữ liệu. Nếu không cần theo link, nên để mặc định.

### Bảng so sánh

| Nội dung | Mặc định (không `FOLLOW_LINKS`) | Có `FOLLOW_LINKS` |
| --- | --- | --- |
| Symlink trỏ tới file | Xử lý bản thân liên kết như một entry | Cố truy cập file đích, nếu có thể |
| Symlink trỏ tới thư mục | Xem như entry lá, **không** duyệt thư mục đích | Cố duyệt thư mục đích |
| Symlink trỏ về tổ tiên | Không đi theo nên không tạo vòng duyệt từ link | Có thể phát hiện vòng và báo `FileSystemLoopException` |
| Symlink trỏ ra ngoài root | Không truy cập cây ngoài qua link | **Có thể** đi ra ngoài root |
| Dangling symlink | Có thể vẫn được xử lý như entry link | Cố theo đích; nếu không theo được, hành vi callback/phần tử có thể phản ánh link hoặc lỗi theo API |
| Khả năng truy cập lặp các entry | Hạn chế trùng từ các symlink | Có thể duyệt cùng dữ liệu qua nhiều đường liên kết |
| Rủi ro khi sử dụng để xóa cây | Thường dễ giới hạn hơn, vẫn cần quyền và kiểm tra | **Không nên bật tùy tiện**, có thể chạm đối tượng ngoài phạm vi |

**Không nhầm `FileVisitOption.FOLLOW_LINKS` với `LinkOption.NOFOLLOW_LINKS`.** Một bên là tùy chọn **duyệt** cây; một bên là tùy chọn được dùng trong **một số phương thức kiểm tra thuộc tính và đường dẫn** để không theo liên kết khi lấy thông tin.

## 3. `FileSystemLoopException` biểu thị điều gì?

`java.nio.file.FileSystemLoopException` là một subclass của `FileSystemException` và `IOException`, báo rằng hệ thống đang thực hiện thao tác có liên quan tới đường dẫn đã gặp **vòng trong cấu trúc hệ thống tệp**.

Khi `walkFileTree` có `FOLLOW_LINKS`, Java cần kiểm tra thư mục mới chuẩn bị đi vào có trùng một thư mục **tổ tiên của nhánh hiện tại** không. Việc nhận diện vòng thường dựa trên file key từ thuộc tính filesystem, và có thể dùng kiểm tra tương đương `Files.isSameFile(...)` khi cần.

Đừng suy luận rằng “hai đường dẫn có chuỗi khác nhau” thì chắc chắn là hai thư mục khác nhau. Symlink, hard link (đối với đối tượng được phép hard-link), mount hoặc cách provider biểu diễn tên có thể khiến nhiều tên tham chiếu tới cùng tài nguyên.

Trong `walkFileTree`, khi phát hiện chu trình do đi theo symlink, lỗi được cung cấp tới callback:

```java
@Override
public FileVisitResult visitFileFailed(
        Path file, IOException exc) throws IOException {
    if (exc instanceof FileSystemLoopException) {
        System.err.println("Đã phát hiện vòng tại: " + file);
        return FileVisitResult.CONTINUE;
    }
    throw exc;
}
```

Đây là **phương thức override minh họa** cần nằm bên trong `FileVisitor` hoặc `SimpleFileVisitor`, với import `Path`, `IOException`, `FileVisitResult` và `FileSystemLoopException`. Việc trả `CONTINUE` có nghĩa **không tiếp tục đi vào nhánh gây vòng**, nhưng tiếp tục duyệt những nhánh còn lại.

Nếu dùng `new SimpleFileVisitor<Path>() {}` và không override `visitFileFailed`, implementation mặc định ném lại `IOException` nhận được. Khi đó toàn bộ `walkFileTree` thường kết thúc bằng ngoại lệ ngay khi gặp vòng.

### `FileSystemLoopException` không bao quát mọi lỗi

Một lỗi ở entry khác có thể là:

- `AccessDeniedException` nếu bị từ chối quyền;
- `NoSuchFileException` nếu đường dẫn biến mất hoặc không thể truy cập theo trạng thái filesystem;
- `FileSystemException` khác nếu provider báo lỗi;
- `IOException` do I/O chung.

**Không phải mọi `IOException` đều là vòng liên kết.** Nếu chỉ muốn bỏ qua một vòng đã nhận diện, hãy xử lý riêng `FileSystemLoopException` và cho những lỗi khác lan ra ngoài (hoặc xử lý theo chính sách đã xác định).

## 4. Chương trình Java 17+ chạy độc lập

Ví dụ dưới đây tạo một thư mục tạm với một file và cố tạo một symlink từ thư mục con trỏ về thư mục gốc. Nếu môi trường cho phép tạo symbolic link, đây là một **vòng liên kết** thật. Ví dụ duyệt lần đầu **không** theo link, lần hai bật `FOLLOW_LINKS` và đếm callback `FileSystemLoopException`.

Một số hệ điều hành hoặc tài khoản (đặc biệt Windows không có quyền tạo symbolic link) có thể **không cho phép** tạo link. Chương trình vẫn chạy và in kết quả không có link, vì hành vi này phụ thuộc môi trường. Chỉ trong bài demo chúng ta mới bỏ qua lỗi tạo symlink để xem luồng duyệt không có link; ứng dụng thật phải phân biệt và xử lý lỗi theo nghiệp vụ.

Lưu thành file **`FollowLinksLoopDemo.java`**:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemLoopException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

public class FollowLinksLoopDemo {

    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("java-follow-links-");
        Path child = Files.createDirectory(root.resolve("child"));
        Path file = child.resolve("note.txt");
        Path link = child.resolve("back-to-root");

        boolean linkCreated = false;

        try {
            Files.writeString(file, "Java", StandardCharsets.UTF_8);

            try {
                // Tạo vòng: child/back-to-root -> root.
                Files.createSymbolicLink(link, root);
                linkCreated = true;
            } catch (UnsupportedOperationException
                     | SecurityException | IOException unsupported) {
                // Riêng demo này vẫn chạy nếu không tạo được link.
            }

            final int[] linkLeaves = {0};
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(
                        Path path, BasicFileAttributes attrs) {
                    if (Files.isSymbolicLink(path)) {
                        linkLeaves[0]++;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            final int[] loopErrors = {0};
            Files.walkFileTree(
                    root,
                    EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                    16,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFileFailed(
                                Path path, IOException exc)
                                throws IOException {
                            if (exc instanceof FileSystemLoopException) {
                                loopErrors[0]++;
                                return FileVisitResult.CONTINUE;
                            }
                            throw exc;
                        }
                    });

            System.out.println("Symlink created: " + linkCreated);
            System.out.println("No-follow link leaves: "
                    + linkLeaves[0]);
            System.out.println("Detected link loops: "
                    + loopErrors[0]);
            System.out.println("Traversal completed: true");
        } finally {
            // Chỉ xóa những entry do chính ví dụ tạo.
            Files.deleteIfExists(link);
            Files.deleteIfExists(file);
            Files.deleteIfExists(child);
            Files.deleteIfExists(root);
        }
    }
}
```

Chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 FollowLinksLoopDemo.java
java FollowLinksLoopDemo
```

**Stdout nếu môi trường cho phép tạo symlink và callback vòng được phát hiện:**

```text
Symlink created: true
No-follow link leaves: 1
Detected link loops: 1
Traversal completed: true
```

**Stdout nếu không được phép tạo symlink:**

```text
Symlink created: false
No-follow link leaves: 0
Detected link loops: 0
Traversal completed: true
```

Giải thích:

1. `Files.createTempDirectory` tạo vùng thử riêng, tránh đụng dữ liệu người dùng. `root.resolve(...)` chỉ tính đường dẫn; các lệnh `Files.createDirectory`, `Files.writeString` và `Files.createSymbolicLink` mới có tác động I/O.
2. Nếu link tạo thành công, `child/back-to-root` trỏ ngược về `root` — đúng là một vòng theo nhánh `root → child → back-to-root → root`.
3. Lần `walkFileTree(root, visitor)` mặc định **không theo link**; symlink được gọi qua `visitFile`, nên `linkLeaves=1`.
4. Lần `walkFileTree` thứ hai có `FOLLOW_LINKS`. Khi cố đi vào link trỏ về tổ tiên, `visitFileFailed` nhận `FileSystemLoopException` và tăng `loopErrors`.
5. Trả `FileVisitResult.CONTINUE` để duyệt xong thay vì dừng với ngoại lệ. Không có vòng thì callback đó không tăng bộ đếm.
6. `finally` dọn **entry link** trước, sau đó file, thư mục con và gốc. Xóa symlink **không xóa thư mục đích** `root`. Nếu trong thực tế có tiến trình ngoài can thiệp, dọn dẹp vẫn có thể phát sinh `IOException`; ví dụ dùng thư mục tạm riêng để giảm nguy cơ.

**Không dùng chương trình này để thử nghiệm trên thư mục sản xuất.** Nó chỉ tạo thư mục riêng và không xóa đệ quy thư mục bất kỳ mà người dùng truyền vào.

## 5. Phân biệt `Files.walkFileTree`, `Files.walk` và `Files.find` khi có vòng

### `walkFileTree`: lỗi đi qua `FileVisitor`

Với `Files.walkFileTree`, callback quan trọng là:

```java
Files.walkFileTree(
        root,
        EnumSet.of(FileVisitOption.FOLLOW_LINKS),
        20,
        new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFileFailed(
                    Path file, IOException exc)
                    throws IOException {
                if (exc instanceof FileSystemLoopException) {
                    return FileVisitResult.CONTINUE;
                }
                throw exc;
            }
        });
```

Đây là mã minh họa trong class/phương thức với import các kiểu tương ứng và biến `root` đã có. Nếu muốn ghi nhận lỗi chứ không bỏ qua, có thể lưu path vào danh sách chẩn đoán, nhưng việc lưu quá nhiều entry có thể tăng dùng RAM.

Ưu điểm của `walkFileTree` là bạn có callback riêng:

- `preVisitDirectory(dir, attrs)`: được gọi trước khi xử lý nội dung thư mục;
- `visitFile(file, attrs)`: xử lý một entry file/leaf;
- `visitFileFailed(file, exc)`: xử lý việc duyệt/thăm một entry không thành công, gồm lỗi vòng nếu đi theo link;
- `postVisitDirectory(dir, exc)`: được gọi sau khi xử lý thư mục; `exc` có thể phản ánh lỗi khi duyệt thư mục.

Các callback trả `FileVisitResult` để quyết định tiếp tục hay bỏ qua nhánh/anh em, hoặc kết thúc. Khi muốn **theo link nhưng vẫn kiểm soát vòng và lỗi cụ thể**, `walkFileTree` thường là lựa chọn dễ diễn đạt chính sách hơn.

### `Files.walk`: trả về `Stream<Path>`

`Files.walk(root)` theo mặc định không đi theo symbolic link. Để theo link:

```java
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

try (Stream<Path> paths =
        Files.walk(root, 20, FileVisitOption.FOLLOW_LINKS)) {
    paths.forEach(System.out::println);
}
```

Đây là đoạn minh họa cần `root` đã xác định và xử lý ngoại lệ phù hợp. `Files.walk` tạo stream **lazy**: lỗi đọc directory có thể xuất hiện trong quá trình tiêu thụ stream, không chỉ lúc gọi `walk(...)`. Khi có lỗi I/O trong quá trình duyệt, API có thể bọc `IOException` thành **`UncheckedIOException`**; với vòng liên kết, `FileSystemLoopException` có thể nằm ở **`getCause()`**.

```java
try (Stream<Path> paths =
        Files.walk(root, 20, FileVisitOption.FOLLOW_LINKS)) {
    paths.forEach(System.out::println);
} catch (java.io.UncheckedIOException e) {
    if (e.getCause() instanceof FileSystemLoopException) {
        System.err.println("Đã gặp vòng liên kết");
    } else {
        throw e;
    }
}
```

Đây là đoạn minh họa cần import `Path`, `Files`, `FileVisitOption`, `Stream` và xử lý lỗi `IOException` có thể xảy ra ngay khi mở stream. Với stream, không có `visitFileFailed` để chọn tiếp tục qua đúng một vòng; khi iterator/stream báo lỗi, pipeline đang tiêu thụ thường không có cơ chế tương đương visitor để bỏ qua riêng nhánh đó rồi đi tiếp.

**Luôn đóng `Stream<Path>`** từ `Files.walk` hoặc `Files.find` bằng `try-with-resources`; stream có thể giữ tài nguyên thư mục đang mở.

### `Files.find`: tìm theo thuộc tính nhưng vẫn có rủi ro tương tự

`Files.find(root, maxDepth, matcher, options...)` có thể dùng `FOLLOW_LINKS`. Nó cung cấp `BasicFileAttributes` cho predicate, thích hợp khi cần lọc theo metadata, nhưng **không tự loại bỏ vòng liên kết hoặc bảo vệ khỏi traversal ra ngoài root** khi theo link. Trên stream lazy, ngoại lệ I/O có thể xuất hiện khi duyệt và cần đóng stream.

Không nên viết `Files.find(... FOLLOW_LINKS)` rồi cho rằng đã miễn nhiễm với chu trình chỉ vì predicate chỉ chọn file `.txt`: chương trình vẫn cần **duyệt** thư mục để tìm các file phù hợp.

## 6. Độ sâu tối đa và chính sách bỏ qua nhánh

`maxDepth` là công cụ giới hạn phạm vi, nhưng **không thay cho nhận diện vòng hoặc kiểm tra bảo mật**.

```java
Files.walkFileTree(
        root,
        EnumSet.of(FileVisitOption.FOLLOW_LINKS),
        3,
        visitor);
```

Đây là đoạn minh họa giả định có `root`, `visitor` và import phù hợp. `maxDepth=0` chỉ duyệt entry root; khi độ sâu chạm giới hạn, API không đi xuống sâu hơn (entry ở độ sâu cuối được xử lý như leaf theo hợp đồng của visitor). `maxDepth=3` cho phép xem tới ba cấp tính từ root.

Tuy nhiên:

- Một directory ở độ sâu 1 có thể chứa hàng triệu entry; **giới hạn độ sâu không giới hạn tổng số entry**.
- Một symlink ở độ sâu nhỏ có thể dẫn ra ngoài cây gốc dù chưa tạo vòng.
- Nếu chỉ duyệt sâu tới một mức chưa chạm mắt xích tạo vòng, bạn có thể **không quan sát được `FileSystemLoopException`**, nhưng bản thân liên kết vẫn tồn tại.
- Với tree lớn, vẫn cần kiểm soát thời gian, số entry, quyền, và cách xử lý I/O.

Trong `FileVisitor`, có thể trả:

| Giá trị | Ý nghĩa khi callback hỗ trợ |
| --- | --- |
| `CONTINUE` | Tiếp tục duyệt bình thường |
| `SKIP_SUBTREE` | Bỏ qua thư mục con của entry hiện tại, phù hợp ở `preVisitDirectory` |
| `SKIP_SIBLINGS` | Bỏ qua những entry anh em còn lại trong ngữ cảnh callback |
| `TERMINATE` | Kết thúc toàn bộ cuộc duyệt |

Ví dụ nếu chỉ cần biết thư mục nào có tài liệu, bạn có thể bỏ qua những nhánh lớn mà nghiệp vụ không dùng ngay tại `preVisitDirectory`. Không bật `FOLLOW_LINKS` nếu việc đi vào các symlink không phải yêu cầu.

## 7. `NOFOLLOW_LINKS` dùng ở đâu?

`LinkOption.NOFOLLOW_LINKS` **khác** `FileVisitOption.FOLLOW_LINKS`. Nó được truyền vào một số API hỏi metadata:

```java
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

boolean entryExists = Files.exists(
        path, LinkOption.NOFOLLOW_LINKS);

BasicFileAttributes attrs = Files.readAttributes(
        path, BasicFileAttributes.class,
        LinkOption.NOFOLLOW_LINKS);

System.out.println("Is symbolic link: " + attrs.isSymbolicLink());
```

Đây là đoạn minh họa trong phương thức xử lý `IOException`, với `path` đã khai báo. Khi truyền `NOFOLLOW_LINKS`, metadata có thể phản ánh **chính entry liên kết**, thay vì thuộc tính của đích.

Để kiểm tra nhanh:

```java
boolean link = Files.isSymbolicLink(path);
```

`Files.isSymbolicLink(path)` kiểm tra entry có là symbolic link hay không và không buộc bạn phải mở/duyệt thư mục đích.

**Dangling link:** `Files.exists(link)` mặc định thường theo link nên có thể trả `false` khi mục tiêu biến mất, trong khi `Files.exists(link, NOFOLLOW_LINKS)` vẫn có thể `true` vì entry liên kết còn ở đó. Đừng dùng `exists` mặc định để xác nhận chính symlink đã bị xóa.

## 8. Vòng liên kết so với truy cập lặp không tạo vòng

Hai trường hợp cần phân biệt:

**Vòng (cycle):**

```text
A/child/back -> A
Đi theo link từ A/child/back sẽ quay lại tổ tiên A.
```

**Truy cập trùng nhưng không là vòng trên nhánh hiện tại:**

```text
root/
├── original/
│   └── x.txt
└── alias -> original/
```

Khi đi theo `alias`, cùng file `x.txt` có thể được truy cập lần nữa qua một đường dẫn khác. Vì `original` chưa chắc là **tổ tiên của alias trong nhánh hiện tại**, việc này **không nhất thiết** tạo `FileSystemLoopException`.

Nếu nghiệp vụ cần xử lý **mỗi đối tượng vật lý chỉ một lần**, phát hiện vòng không đủ: phải có chiến lược deduplicate theo định danh của filesystem hoặc `Files.isSameFile` khi thích hợp. So sánh `Path.toString()` không chứng minh hai file khác nhau; ngược lại, các chỉ báo như `BasicFileAttributes.fileKey()` có thể `null` hoặc có đặc tính phụ thuộc filesystem/provider.

Một tập hợp `Set<Path>` chứa chuỗi path khác nhau **không đủ** để khẳng định không trùng nội dung khi có symlink hoặc hard link. Cần xác định rõ bạn muốn “duyệt mỗi **đường dẫn**” hay “xử lý mỗi **đối tượng tệp thực**”.

## 9. Bảo mật: không tùy tiện theo link khi xử lý dữ liệu không tin cậy

Nguy cơ lớn nhất không chỉ là vòng liên kết làm chậm chương trình. Với `FOLLOW_LINKS`, một link có thể trỏ tới cây thư mục mà ứng dụng **không dự định** truy cập.

Ví dụ cấu trúc:

```text
uploads/
└── external -> /private-data/
```

Nếu code scan toàn bộ `uploads` và bật `FOLLOW_LINKS`, tiến trình có thể đọc metadata hoặc nội dung của `/private-data` nếu quyền cho phép. Với chương trình **xóa thư mục**, bật `FOLLOW_LINKS` có thể dẫn đến thao tác sai phạm vi gây mất dữ liệu nghiêm trọng.

Khuyến nghị:

1. **Mặc định không theo symlink** nếu không có yêu cầu nghiệp vụ rõ ràng.
2. Xác định phạm vi root được phép, quyền sở hữu và chính sách xử lý symbolic link.
3. Với path do người dùng đưa vào, kiểm tra thành phần đường dẫn `..`, đường dẫn tuyệt đối, và ảnh hưởng của symlink; `normalize()` chỉ là xử lý cú pháp và **không tạo ra ranh giới bảo mật hoàn chỉnh**.
4. Áp giới hạn độ sâu và số entry/thời gian/bộ nhớ; riêng `maxDepth` chưa đủ.
5. Nếu phải theo link, cân nhắc canonical/real path, `Files.isSameFile`, đánh dấu đối tượng đã thăm và chiến lược xử lý lỗi, đồng thời lưu ý thay đổi filesystem giữa lúc kiểm tra và sử dụng (TOCTOU).
6. Không dùng một bộ quét có `FOLLOW_LINKS` như một công cụ xóa file đệ quy trong thư mục người dùng chỉ định mà không có thiết kế an toàn.

Ngay cả khi không bật `FOLLOW_LINKS`, tên đường dẫn và nội dung hệ thống tệp vẫn có thể thay đổi đồng thời. Không coi kết quả `Files.exists`, `normalize` hoặc một lần duyệt là bảo đảm file không bị thay đổi sau đó.

## 10. Xử lý lỗi khi duyệt thật

Một visitor có chiến lược minh bạch thường xử lý **vòng** khác với các lỗi không mong đợi:

```java
import java.io.IOException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;

class LoopAwareVisitor extends SimpleFileVisitor<Path> {
    @Override
    public FileVisitResult visitFileFailed(
            Path file, IOException exc) throws IOException {

        if (exc instanceof FileSystemLoopException) {
            System.err.println("Bỏ qua vòng tại: " + file);
            return FileVisitResult.CONTINUE;
        }

        // Không che giấu lỗi quyền truy cập hoặc I/O khác.
        throw exc;
    }
}
```

Đây là một **class minh họa có thể đặt trong file riêng hoặc class bao ngoài phù hợp**. Để dùng, truyền instance cho `Files.walkFileTree(root, EnumSet.of(FOLLOW_LINKS), maxDepth, new LoopAwareVisitor())`. Trong ứng dụng thật, có thể thay in console bằng ghi log có cấu trúc, đếm số vòng hoặc kết thúc traversal theo yêu cầu.

### Với `postVisitDirectory`

`postVisitDirectory(Path dir, IOException exc)` nhận một `exc` khác `null` nếu gặp lỗi khi duyệt nội dung của thư mục đó. Một visitor tổng quát cần quyết định ném lại, ghi nhận, hay bỏ qua lỗi tùy yêu cầu; không giả định mọi lỗi đều phải đi qua `visitFileFailed`.

### Với `Files.walk`

Vì đây là `Stream<Path>`, bạn cần đóng stream và chuẩn bị xử lý `UncheckedIOException` trong giai đoạn tiêu thụ. Nếu mục tiêu là **tiếp tục sau một FileSystemLoopException cụ thể**, `walkFileTree` với visitor được thiết kế đúng thường rõ ràng hơn một pipeline stream cần thoát ra khi iterator ném lỗi.

## 11. Lỗi phổ biến

| Sai lầm | Hệ quả | Cách xử lý |
| --- | --- | --- |
| “Java luôn theo symlink khi `Files.walkFileTree`” | Hiểu sai cây đang được duyệt | Mặc định **không theo**, chỉ thêm `FOLLOW_LINKS` khi muốn |
| “`FOLLOW_LINKS` chỉ để tìm file nhanh hơn” | Có thể đọc trùng, ra ngoài root và gặp vòng | Xác định mục tiêu nghiệp vụ trước khi bật |
| “Vòng link làm visitor treo vô hạn chắc chắn” | Java có cơ chế phát hiện loop, thường báo `FileSystemLoopException` | Xử lý `visitFileFailed` hoặc ngoại lệ thích hợp |
| “`FileSystemLoopException` xuất hiện ở `visitFile` bình thường” | Bỏ qua callback báo lỗi | Kiểm tra `visitFileFailed(file, exc)` khi `walkFileTree` |
| “`Files.walk` ném trực tiếp cùng lỗi checked ngay khi tạo stream” | Lỗi lazy có thể xuất hiện lúc duyệt | Xử lý `UncheckedIOException` và `getCause()`, đóng stream |
| “`CONTINUE` sau loop sẽ vào lại link” | Nó bỏ qua nhánh lỗi và đi tiếp | Không tự gọi lại traversal trên cùng link |
| “Giới hạn maxDepth ngăn mọi rủi ro” | Có thể nhiều entry, dữ liệu ngoài root | Thêm giới hạn entry/thời gian/ownership |
| “Không có FileSystemLoopException nghĩa là không duyệt trùng” | Alias cùng target nhưng không là tổ tiên vẫn gây trùng | Deduplicate nếu nghiệp vụ cần |
| “`Files.exists(link)` chắc chắn kiểm tra entry link” | Có thể theo symlink tới đích | Dùng `NOFOLLOW_LINKS` khi muốn hỏi về entry link |
| “`Path.normalize()` ngăn mọi truy cập ngoài root” | Symlink và race thay đổi nghĩa đường dẫn | Kiểm tra real path và thực hiện chính sách an toàn |
| “Duyệt với FOLLOW_LINKS rồi xóa toàn bộ thì an toàn” | Có thể xóa ngoài root | Tránh theo link trong chương trình xóa nhạy cảm |
| “Trả `CONTINUE` cho mọi `IOException`” | Che giấu quyền và lỗi I/O nghiêm trọng | Phân loại loop riêng, báo các lỗi khác |
| “Bỏ quên đóng `Files.walk` stream không sao” | Có thể giữ resource mở | `try-with-resources` |
| “Windows nào cũng tạo symlink được” | Phụ thuộc quyền/chế độ và filesystem | Thử trên thư mục tạm, có nhánh không hỗ trợ |

## 12. Bài tập tự kiểm tra

1. **Nếu gọi `Files.walkFileTree(root, visitor)` mà không có `FOLLOW_LINKS`, symlink tới thư mục có được duyệt sâu không?** Không; liên kết được xem như leaf entry.
2. **`FileVisitOption.FOLLOW_LINKS` được truyền ở đâu?** Vào overload của `walkFileTree` hoặc varargs của `Files.walk` / `Files.find`.
3. **Link `A/child/back -> A` có thể gây lỗi gì khi đi theo?** `FileSystemLoopException` vì link đưa về tổ tiên.
4. **Trong `walkFileTree`, nên bắt vòng ở đâu?** `visitFileFailed(Path, IOException)`, rồi kiểm tra `exc instanceof FileSystemLoopException`.
5. **Trả `FileVisitResult.CONTINUE` khi bắt một vòng có đi vào lại nhánh vòng không?** Không; bỏ qua entry lỗi và duyệt phần còn lại.
6. **Không có vòng có chắc không đi ra ngoài root?** Không; link có thể trỏ sang thư mục bên ngoài.
7. **`maxDepth=3` có bảo đảm chỉ duyệt 100 file trở xuống không?** Không; giới hạn độ sâu, không giới hạn số entry.
8. **`Files.walk` có thể báo lỗi vòng như thế nào trong lúc tiêu thụ?** Dưới dạng `UncheckedIOException` với nguyên nhân `FileSystemLoopException`.
9. **Symlink bị mất target còn có thể hiện diện như một entry không?** Có; dùng `Files.exists(link, LinkOption.NOFOLLOW_LINKS)` hoặc `Files.isSymbolicLink` để kiểm tra entry.
10. **`FileSystemLoopException` có thay cho việc chống xử lý trùng không?** Không; cùng target được tham chiếu từ nhánh khác có thể không là vòng theo tổ tiên.
11. **Có nên bật `FOLLOW_LINKS` khi viết chương trình xóa toàn bộ thư mục upload của người dùng?** Không nếu chưa có thiết kế bảo vệ chặt chẽ; thường nên không theo link.
12. **Khi symlink không tạo được trong chương trình demo, đó có chứng minh FOLLOW_LINKS không hoạt động không?** Không; việc tạo link phụ thuộc quyền và provider, còn `FOLLOW_LINKS` là hành vi duyệt khi liên kết tồn tại.

**Ghi nhớ:** mặc định, Java duyệt cây **không theo symbolic link**. `FOLLOW_LINKS` cho phép đi vào thư mục được link trỏ tới, nhưng cũng mở ra rủi ro **vòng, duyệt trùng và vượt root**. Với `Files.walkFileTree`, dùng `visitFileFailed` để nhận `FileSystemLoopException` và quyết định `CONTINUE` hay báo lỗi; với `Files.walk`/`find`, lưu ý I/O exception có thể xuất hiện khi tiêu thụ stream.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="directory-tree-traversal-009">Thứ tự duyệt không mặc nhiên là thứ tự mong muốn</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự duyệt cây thư mục không mặc nhiên là thứ tự mong muốn

## 1. Vấn đề: “duyệt được” không có nghĩa “duyệt đúng thứ tự”

Trong Java I/O, ứng dụng có thể liệt kê file bằng **File.list()/listFiles()**, **Files.list(Path)**, **DirectoryStream**, hoặc duyệt toàn bộ cây thư mục bằng **Files.walk(Path)** và **Files.walkFileTree(...)**. Những API này giúp **tìm và duyệt các mục**, nhưng **không mặc định bảo đảm thứ tự theo tên, ngày sửa, dung lượng, loại file hay bất kỳ thứ tự nghiệp vụ nào**.

Ví dụ có cây:

~~~text
project/
├── zeta.txt
├── docs/
│   ├── chapter2.md
│   └── chapter1.md
└── alpha.txt
~~~

Một ứng dụng muốn hiện menu cho người dùng theo tên có thể mong đợi:

~~~text
alpha.txt
docs/
zeta.txt
~~~

Nhưng lệnh **Files.list(project)** chỉ lấy các mục trực tiếp dưới **project**, **không cam kết thứ tự alphabet**. Trên một máy, đầu ra có thể tình cờ trùng thứ tự mong muốn; trên máy khác, sau khi tạo/xóa file hoặc đổi filesystem, thứ tự có thể khác.

Một ứng dụng khác muốn **duyệt cây đầy đủ** theo từng nhánh, hoặc **xóa file trước khi xóa thư mục cha**. Đó lại là **bài toán thứ tự khác** so với alphabet toàn cục. Cần phân biệt **phạm vi liệt kê**, **chiến lược duyệt cây** và **thứ tự xử lý**.

> Nguyên tắc: **khi thứ tự là yêu cầu đúng-sai của chương trình, phải xác định quy tắc và áp đặt nó trong code**. Đừng phụ thuộc vào thứ tự mà filesystem trả về tình cờ.

## 2. Ba khái niệm rất dễ nhầm

| Khái niệm | Câu hỏi | Ví dụ |
| --- | --- | --- |
| **Phạm vi** | Chỉ con trực tiếp hay cả cây? | **Files.list(dir)** lấy con trực tiếp; **Files.walk(root)** đi vào các cấp con |
| **Chiến lược duyệt** | Vào nhánh nào trước, cha hay con trước? | **Files.walk** duyệt **depth-first**, mỗi thư mục trước các mục dưới nó |
| **Sắp xếp** | Giữa những mục cùng cấp, mục nào đứng trước? | Tên A–Z, thư mục trước file, thời gian tăng dần |

Dù một API có xác định **depth-first** (chiều sâu), điều đó **không đồng nghĩa** mỗi thư mục con được duyệt theo alphabet. “Depth-first” trả lời **cách đi qua cấu trúc cây**; “alphabet” trả lời **so sánh giữa các mục**.

Cũng không được suy luận theo tên file có số: **"file10.txt"** đứng **trước "file2.txt"** theo so sánh từ điển ký tự thông thường vì ký tự **'1'** đứng trước **'2'**. Nếu cần thứ tự số tự nhiên, phải có bộ so sánh chuyên biệt.

## 3. Thứ tự trả về của File.list() và File.listFiles()

API truyền thống **java.io.File**:

~~~java
File dir = new File("project");
String[] names = dir.list();
File[] entries = dir.listFiles();
~~~

Đây là **mảnh code minh họa**, cần import **java.io.File**. Các API trên chỉ liệt kê **các mục con trực tiếp**; chúng **không tự đi vào mọi cấp con**.

Hai phương thức **không bảo đảm thứ tự** của các tên hoặc đối tượng trả về. Vì vậy, không nên viết:

~~~java
File[] entries = dir.listFiles();
File first = entries[0]; // Sai nếu hiểu đây là file đầu tiên theo tên.
~~~

Ngoài chuyện chưa sắp xếp, **listFiles() có thể trả null** nếu đường dẫn không phải thư mục hoặc có lỗi I/O; đoạn code trên còn có thể lỗi null hay mảng rỗng. Cần kiểm tra:

~~~java
File[] entries = dir.listFiles();
if (entries == null) {
    throw new IOException("Không thể liệt kê thư mục: " + dir);
}
Arrays.sort(entries,
        Comparator.comparing(File::getName));
for (File entry : entries) {
    System.out.println(entry.getName());
}
~~~

Đây là **mảnh code minh họa**, cần import **java.io.File**, **java.io.IOException**, **java.util.Arrays**, **java.util.Comparator**. **Arrays.sort** áp đặt thứ tự theo **getName()** trên **mảng hiện có**, không làm thay đổi thứ tự lưu trên filesystem.

**Lưu ý:** hàm so sánh trong ví dụ phân biệt hoa/thường và theo thứ tự ký tự của Java. Nó không phải tự động là thứ tự phù hợp với ngôn ngữ tự nhiên hoặc quy tắc “folder first” của file manager.

## 4. Files.list(Path): một cấp, thứ tự không được bảo đảm

API NIO:

~~~java
try (Stream<Path> stream = Files.list(directory)) {
    stream.forEach(System.out::println);
}
~~~

Đây là **mảnh code minh họa**, giả định **directory** là Path và đã import **java.nio.file.Files**, **java.nio.file.Path**, **java.util.stream.Stream**, đồng thời xử lý IOException.

**Files.list(directory)** tạo stream lấy **các mục trực tiếp** trong directory. Javadoc nói **thứ tự phần tử không được bảo đảm**. Stream có thể được cấp dữ liệu lười (*lazy*); việc mở stream có tài nguyên gắn với thư mục, nên cần **try-with-resources**.

### 4.1. Muốn xếp tên A–Z: sorted() tường minh

~~~java
try (Stream<Path> stream = Files.list(directory)) {
    stream.sorted(
            Comparator.comparing(p -> p.getFileName().toString()))
          .forEach(System.out::println);
}
~~~

Đoạn trên cần thêm import **java.util.Comparator** và giả định thư mục hiện hữu. Kết quả sắp xếp theo **tên mục con**, không theo toàn bộ đường dẫn. Do các mục là con trực tiếp của cùng một directory, đây thường là quy tắc cần dùng cho danh sách tên đơn cấp.

Nếu chỉ cần tên:

~~~java
try (Stream<Path> stream = Files.list(directory)) {
    List<String> names = stream
            .map(p -> p.getFileName().toString())
            .sorted()
            .toList();
    System.out.println(names);
}
~~~

Đây là **mảnh code**, cần import **java.util.List**. Java 16+ có **Stream.toList()**, vì vậy Java 17 sử dụng được. Lấy danh sách rồi sort giúp kết quả không phụ thuộc vào thứ tự bất kỳ của filesystem, **với điều kiện tập mục không bị thay đổi trong lúc thu thập**.

**sorted() là thao tác có trạng thái**: để sắp xếp toàn bộ danh sách, nó phải lưu các phần tử cần so sánh. Với thư mục chứa hàng triệu file, bộ nhớ và chi phí sort có thể đáng kể.

## 5. Files.walk(Path): duyệt chiều sâu, không mặc định duyệt con theo tên

**Files.walk(root)** trả stream duyệt cây file từ root. Hợp đồng quan trọng:

- Bắt đầu ở **root**.
- Duyệt **depth-first** (theo chiều sâu) và **thư mục được phát trước các mục con** của nó (*pre-order*).
- **Không bảo đảm thứ tự các mục con/sibling** trong cùng thư mục.
- Theo mặc định **không theo symbolic link** trỏ đến thư mục khi duyệt sâu; có tùy chọn **FileVisitOption.FOLLOW_LINKS** nếu chủ động cần, kèm rủi ro vòng lặp và phạm vi truy cập.
- Stream giữ tài nguyên của các thư mục đang mở; cần **đóng stream**. Trong quá trình duyệt, lỗi I/O có thể xuất hiện dưới dạng **UncheckedIOException**.

Ví dụ:

~~~java
try (Stream<Path> paths = Files.walk(root)) {
    paths.forEach(System.out::println);
}
~~~

Đây là mảnh code minh họa, giả định **root** đã khai báo là Path, có import cần thiết và xử lý IOException. Không được suy ra rằng thư mục con tên “a” sẽ luôn được thăm trước thư mục “z”.

### 5.1. Pre-order không đồng nghĩa alphabet toàn cục

Giả sử:

~~~text
root/
├── b.txt
└── docs/
    └── a.txt
~~~

Trong **pre-order depth-first**, khi chọn đi vào **docs**, luồng sẽ phát **docs** trước **docs/a.txt**; nhưng **b.txt** có thể xuất hiện trước hay sau nhánh **docs** do thứ tự sibling không được bảo đảm.

Nếu áp dụng:

~~~java
try (Stream<Path> paths = Files.walk(root)) {
    paths.sorted().forEach(System.out::println);
}
~~~

thì đây là **sắp xếp toàn cục các Path sau khi duyệt**, không phải yêu cầu filesystem “đi qua các nhánh theo alphabet”. Hai cách có thể cho **thứ tự đầu ra cuối giống nhau** theo một comparator, nhưng khác **thời điểm khám phá** và không biến traversal gốc thành một hành trình DFS với lựa chọn sibling tường minh.

**Duyệt và xử lý ngay** (ví dụ in, copy hoặc xóa trong hành trình) khác với **thu thập tất cả rồi sắp xếp mới xử lý**. Khi hành động phụ thuộc thứ tự cha/con, không thể dùng một sort tùy ý thay cho thuật toán traversal phù hợp.

## 6. Files.walkFileTree và thứ tự trước/sau khi thăm thư mục

**Files.walkFileTree** gọi các callback của **FileVisitor**. Các callback có ý nghĩa cấu trúc:

- **preVisitDirectory(dir, attrs)**: xử lý thư mục **trước** khi duyệt các mục bên trong nó.
- **visitFile(file, attrs)**: xử lý file (và các mục không phải thư mục được duyệt sâu).
- **visitFileFailed(file, exc)**: xử lý trường hợp không truy cập được mục.
- **postVisitDirectory(dir, exc)**: xử lý thư mục **sau khi** đã duyệt xong các mục bên trong (hoặc sau khi duyệt bị gián đoạn theo hợp đồng).

Mặc dù callback xác định ý nghĩa **trước/sau đối với từng thư mục**, **thứ tự giữa các mục con cùng cấp vẫn không được bảo đảm theo alphabet**.

Một số bài toán yêu cầu thứ tự phụ thuộc cấu trúc:

| Tác vụ | Thứ tự cần thiết |
| --- | --- |
| Tạo cây thư mục ở nơi khác | Thường **tạo thư mục cha trước con**, tức là pre-order khi xử lý thư mục |
| Dọn một cây thư mục | **Xóa file/con trước khi xóa thư mục cha**, dùng visitFile + postVisitDirectory |
| Xuất danh sách theo tên | Có thể cần **sort sibling** hoặc sort toàn bộ tên đầu ra |
| Xuất cây đẹp, thụt lề theo cấp | **Cấu trúc DFS + thứ tự sibling tường minh**, không phải chỉ global sort |
| Chọn file “mới nhất” | Sort theo **thời gian thực** đã đọc từ attribute, không dựa vào vị trí trả về |

Nếu chỉ cần thứ tự **phục vụ xóa con trước cha**, **walkFileTree** với **postVisitDirectory** phù hợp hơn việc mong Files.walk trả danh sách mà đảo lại tùy tiện.

## 7. Ví dụ Java 17+ hoàn chỉnh: cùng cây, nhưng tự sắp xếp để ổn định

Chương trình sau tự tạo thư mục tạm để mọi lần chạy có cấu trúc dữ liệu thống nhất. Nó:

1. Tạo **a.txt**, **b.txt**, thư mục **docs** và **docs/intro.txt**, theo thứ tự tạo có chủ đích nhưng **không dựa vào thứ tự tạo khi liệt kê**.
2. Liệt kê **một cấp** bằng Files.list và sort theo tên.
3. Duyệt **cả cây** bằng Files.walk, biến các đường dẫn thành đường dẫn tương đối để output không chứa tên thư mục tạm ngẫu nhiên, rồi sort để đầu ra ổn định.
4. Dọn cây **con trước cha** bằng FileVisitor.

Lưu thành **TraversalOrderDemo.java**:

~~~java
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.stream.Stream;

public class TraversalOrderDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("traversal-order-");

        try {
            Files.writeString(root.resolve("b.txt"), "B");
            Path docs = Files.createDirectory(root.resolve("docs"));
            Files.writeString(docs.resolve("intro.txt"), "Intro");
            Files.writeString(root.resolve("a.txt"), "A");

            try (Stream<Path> stream = Files.list(root)) {
                List<String> children = stream
                        .map(path -> path.getFileName().toString())
                        .sorted()
                        .toList();
                System.out.println("children=" + children);
            }

            try (Stream<Path> stream = Files.walk(root)) {
                List<String> tree = stream
                        .map(path -> {
                            String relative = root.relativize(path)
                                    .toString().replace('\\', '/');
                            return relative.isEmpty() ? "." : relative;
                        })
                        .sorted()
                        .toList();
                System.out.println("tree=" + tree);
            }
        } finally {
            // Chỉ xóa cây tạm do chương trình tạo; không follow links.
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(
                        Path file, BasicFileAttributes attrs)
                        throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(
                        Path dir, IOException exc) throws IOException {
                    if (exc != null) {
                        throw exc;
                    }
                    Files.delete(dir); // Con đã được duyệt/xóa trước.
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
~~~

Chạy với Java 17 trở lên:

~~~bash
javac TraversalOrderDemo.java
java TraversalOrderDemo
~~~

Kết quả trong trường hợp không có tiến trình ngoài can thiệp vào thư mục tạm:

~~~text
children=[a.txt, b.txt, docs]
tree=[., a.txt, b.txt, docs, docs/intro.txt]
~~~

**Vì sao kết quả ổn định?**

- **children** lấy con trực tiếp rồi sort theo String từ tên file, nên **a.txt** trước **b.txt**, rồi **docs**.
- **tree** chuyển đường dẫn tuyệt đối sang tên tương đối và thống nhất dấu phân cách thành **/** trước khi sort: đường dẫn gốc được biểu diễn bằng **"."**; thứ tự từ điển là **.**, **a.txt**, **b.txt**, **docs**, **docs/intro.txt**.
- Việc **tạo b.txt trước a.txt** không ảnh hưởng kết quả đã sort.
- Đoạn **finally** không dựa vào alphabet để xóa: FileVisitor xóa mỗi file tại **visitFile** và xóa directory trong **postVisitDirectory**, bảo đảm con được xử lý trước cha trong quá trình duyệt thành công.

**Giới hạn của ví dụ:** nếu có lỗi I/O, xung đột quyền truy cập hoặc một tiến trình khác thay đổi cây trong lúc duyệt, chương trình có thể ném IOException thay vì in đủ hai dòng; bản thân filesystem không cung cấp một snapshot bất biến tự động chỉ vì gọi Files.list/Files.walk.

## 8. Muốn duyệt từng nhánh theo tên: sắp xếp sibling trước khi đệ quy

Sort toàn bộ kết quả **Files.walk(root).sorted(...)** hữu ích khi chỉ cần **danh sách cuối cùng**. Nhưng nếu bạn phải **vừa duyệt vừa thực hiện thao tác theo đúng thứ tự từng thư mục**, hãy sắp xếp **tập con trực tiếp** trước khi gọi đệ quy vào từng nhánh.

Mẫu chức năng Java:

~~~java
static void visitByName(Path root, Path current) throws IOException {
    System.out.println(root.relativize(current));

    try (Stream<Path> entries = Files.list(current)) {
        List<Path> children = entries
                .sorted((left, right) -> left.getFileName().toString()
                        .compareTo(right.getFileName().toString()))
                .toList();

        for (Path child : children) {
            if (Files.isDirectory(child,
                    java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                visitByName(root, child);
            } else {
                System.out.println(root.relativize(child));
            }
        }
    }
}
~~~

Đây là **một phương thức minh họa**, không chạy độc lập: cần khai báo trong class, import **java.nio.file.Files**, **java.nio.file.Path**, **java.util.List**, **java.util.stream.Stream**, **java.io.IOException**. Khi gọi với root là một directory, nó duyệt depth-first với các **mục con được sắp xếp theo tên**.

Nếu muốn hiện **thư mục trước file**, hãy dùng comparator so loại trước rồi so tên:

~~~java
Comparator<Path> foldersFirst = Comparator
        .comparing((Path p) -> !Files.isDirectory(
                p, LinkOption.NOFOLLOW_LINKS))
        .thenComparing(p -> p.getFileName().toString());
~~~

Đây là **mảnh code minh họa** cần import **java.util.Comparator**, **java.nio.file.Files**, **java.nio.file.LinkOption**, **java.nio.file.Path**. Boolean **false** (thư mục) đứng trước **true** (không phải thư mục). Nếu yêu cầu group theo loại, không nên kỳ vọng **String sort** tự động làm được.

**Cảnh báo:** kiểm tra **Files.isDirectory** trong comparator có thể quan sát trạng thái khác nhau khi directory bị thay đổi đồng thời; đối với dữ liệu cần nhất quán, nên thu thập metadata thành đối tượng dữ liệu rồi sort snapshot, đồng thời xử lý lỗi truy cập rõ ràng. Trên cây có symlink, chương trình phải quy định **theo symlink** hay chỉ coi symlink là một entry. Mẫu trên không tự theo thư mục symlink vì có NOFOLLOW_LINKS.

## 9. Thứ tự theo tên, số, thời gian, kích thước: chọn comparator theo yêu cầu

| Yêu cầu | Cách làm phù hợp | Lưu ý |
| --- | --- | --- |
| Tên A–Z theo ký tự Java | **Comparator.comparing(p -> p.getFileName().toString())** | Phân biệt chữ hoa/thường, không có “natural number order” |
| Tên không phân biệt hoa/thường | **String.CASE_INSENSITIVE_ORDER**, thêm tie-breaker theo tên nguyên bản | Tránh kết quả phụ thuộc đầu vào khi hai tên chỉ khác hoa/thường |
| Giao diện theo ngôn ngữ người dùng | **Collator** với Locale xác định | Quy tắc ngôn ngữ, dấu và tương đương không giống compareTo; cần tie-breaker nếu phải tất định |
| File mới sửa gần nhất trước | Lấy **Files.getLastModifiedTime**, sắp xếp giảm dần | Có thể trùng timestamp, cần tie-breaker; metadata có thể đổi khi đọc |
| Dung lượng lớn trước | Lấy **Files.size**, sort giảm dần | Files.size có thể lỗi hoặc chưa phản ánh một snapshot đồng thời |
| Thư mục trước, file sau | Comparator theo **loại entry**, rồi tên | Dùng NOFOLLOW_LINKS nếu không muốn symlink bị coi như directory |
| Tên có số theo thứ tự “file2” trước “file10” | **Natural numeric comparator** do bạn kiểm soát | Không phải thứ tự mặc định của String.compareTo |
| Trình tự nghiệp vụ riêng | Map tên thành **ưu tiên** rồi sort, thêm thứ tự phụ | Viết rõ quy tắc cho tên chưa được liệt kê |

### 9.1. Comparator cần một quy tắc phụ để kết quả tất định

Nếu có hai mục được comparator coi là **bằng nhau** (ví dụ sort không phân biệt chữ hoa cho **a.txt** và **A.txt**), phương thức sort ổn định vẫn **giữ thứ tự tương đối ban đầu** của các phần tử bằng nhau. Nhưng thứ tự đầu vào của Files.list là không bảo đảm, nên **kết quả cuối vẫn có thể khác**. Thêm tie-breaker:

~~~java
Comparator<String> byName = String.CASE_INSENSITIVE_ORDER
        .thenComparing(Comparator.naturalOrder());
~~~

Mảnh code này cần import **java.util.Comparator**. So sánh bỏ phân biệt hoa trước, nếu bằng nhau thì so String nguyên bản. Điều đó định nghĩa thứ tự rõ ràng ngay cả khi filesystem trả các tên theo thứ tự khác nhau.

Tương tự, nếu sort theo thời gian sửa mà hai file trùng timestamp, có thể xếp tiếp theo tên để có quy tắc ổn định.

### 9.2. Sort trên metadata: nên tách pha đọc và pha so sánh

Với thời gian sửa/dung lượng, phép đọc thuộc tính file có thể ném IOException; không nên giấu IOException bằng cách trả về giá trị mặc định trong comparator. Cách dễ bảo trì hơn:

1. Liệt kê các đường dẫn cần xét.
2. Đọc thuộc tính quan tâm cho từng đường dẫn, xử lý lỗi rõ ràng.
3. Tạo các bản ghi chứa Path + thuộc tính đã đọc.
4. Sort danh sách bản ghi bằng comparator có tiêu chí chính/phụ.
5. Sử dụng thứ tự đã được quyết định.

Một metadata snapshot trong bộ nhớ vẫn không **khóa filesystem**: nếu file thay đổi sau khi đọc metadata, dữ liệu thực tế có thể khác. Khi một hành động phụ thuộc metadata hiện tại, cần xác nhận lại hoặc thiết kế xử lý cạnh tranh ở tầng phù hợp.

## 10. Liệt kê nhiều lần không phải một snapshot ổn định

Ngay cả khi **cùng máy, cùng thư mục**, bạn không được giả định hai lần **Files.list** sẽ luôn có cùng thứ tự hoặc cùng tập kết quả:

- Một chương trình khác có thể tạo/xóa/đổi tên file giữa hai lần.
- Filesystem có thể thay đổi cấu trúc lưu thư mục sau thao tác.
- Provider hệ thống file (đĩa cục bộ, mạng, archive, cloud mount) có quy tắc riêng.
- Quy tắc so sánh đường dẫn phụ thuộc hệ thống (case-sensitive/case-insensitive) trong khi sort String thường dùng thứ tự Java.
- Một số API liệt kê là **weakly consistent** và không khóa một ảnh chụp nguyên tử trước các thay đổi đồng thời.

**Sắp xếp giải quyết thứ tự**, không tự giải quyết **tính nhất quán của tập file**. Nếu cần xử lý chính xác một tập bất biến, phải xác định quy trình nghiệp vụ (ví dụ đóng băng nguồn, chụp danh sách cùng metadata rồi kiểm tra lại, khóa/cơ chế snapshot của filesystem nếu có).

Đặc biệt không dùng:

~~~java
Path first = Files.list(directory).findFirst().orElseThrow();
~~~

để chọn “file đầu tiên theo tên”. Ngoài việc **không đóng stream** trong mảnh này, không có tiêu chí tên nào được áp dụng nên file chọn được không có tính tất định nghiệp vụ. Thay vào đó, phải đặt comparator rõ ràng và dùng try-with-resources.

## 11. Hiệu năng, giới hạn bộ nhớ và độ sâu cây

**Files.list** có thể liệt kê lười một thư mục lớn; **Files.walk** có thể đi qua cả cây mà không cần xây toàn bộ danh sách trong RAM. Tuy nhiên, khi thêm **sorted()**, việc sắp xếp thường cần **gom toàn bộ phần tử** trước khi phát ra kết quả cuối. Với hàng triệu mục, số đối tượng Path, chuỗi, metadata và bộ nhớ sort có thể rất lớn.

Nếu cần **DFS theo alphabet**, có thể sort **mỗi tập sibling** của từng thư mục trước khi đệ quy, chỉ giữ danh sách một thư mục trên mỗi mức recursion. Điều đó giảm nhu cầu sort toàn bộ cây, nhưng trên một thư mục có cực nhiều con vẫn phải giữ nhiều phần tử; recursion sâu có thể gây **StackOverflowError** nếu dùng đệ quy Java không giới hạn. Với cây sâu, có thể chọn **stack tường minh** hoặc một cơ chế traversal khác.

**Files.walk(root, maxDepth)** giới hạn độ sâu cần đọc khi ứng dụng không muốn đi hết cây. **maxDepth = 0** chỉ phát root; cấp 1 bao gồm root và các mục con trực tiếp (nếu đọc được). Với **Files.walkFileTree**, có overload truyền các option và maxDepth. Giới hạn độ sâu khác với sắp xếp; cần chọn cả hai theo nghiệp vụ.

Nếu muốn vừa lọc vừa sort: áp dụng lọc đúng logic rồi sort dữ liệu cần thiết. Nhưng khi muốn “mục đầu tiên theo sort”, thao tác **sorted().findFirst()** vẫn có thể phải xem mọi phần tử đầu vào, không nhanh như “lấy phần tử đầu tiên filesystem trả về”.

## 12. Duyệt để thao tác: cần thứ tự theo phụ thuộc, không chỉ theo tên

### 12.1. Tạo hoặc copy cây: cha trước con

Khi sao chép một cây thư mục sang vị trí khác, phải đảm bảo thư mục đích cha tồn tại trước khi ghi file con. Có thể dùng **walkFileTree** với **preVisitDirectory** để tạo thư mục đích trước khi xử lý file.

**Không lấy Files.walk đã sort và giả định tự tạo mọi thư mục cha**: thứ tự Path sau sort tùy comparator; chính code cần rõ quy tắc tạo cha, hoặc chủ động dùng **Files.createDirectories(parent)** khi phù hợp.

### 12.2. Xóa cây: con trước cha

Khi muốn xóa cây **được sở hữu và đã được phép xóa**, thư mục cha không thể xóa khi còn file hoặc thư mục con. Sử dụng **visitFile** để xóa file và **postVisitDirectory** để xóa thư mục đã duyệt xong.

**Không dùng tên A–Z để suy ra thứ tự xóa an toàn**. Một danh sách file đã sắp xếp không đảm bảo ràng buộc “xóa hết con trước cha” nếu comparator được chọn sai. **Files.walkFileTree** thể hiện phụ thuộc cấu trúc rõ hơn; kể cả vậy, xóa có thể thất bại do file mới xuất hiện, thiếu quyền hoặc trạng thái đồng thời.

### 12.3. Thứ tự không thay thế xử lý lỗi

**walkFileTree** cho phép triển khai **visitFileFailed** và xử lý lỗi truyền vào **postVisitDirectory**. Với pipeline nghiêm ngặt, không được nuốt lỗi để báo "đã duyệt hết" hoặc "đã xóa sạch" khi thực tế chỉ xử lý một phần. Cần xác định chính sách dừng hay tiếp tục và ghi nhận các phần tử thất bại.

## 13. Lưu ý về symbolic link, quyền và sự thay đổi đồng thời

Duyệt cây trên filesystem không tin cậy có nhiều rủi ro hơn thứ tự:

1. **Symbolic link** có thể dẫn ra ngoài root nếu ứng dụng chọn FOLLOW_LINKS; mặc định Files.walk/walkFileTree không theo symbolic link nhưng cần hiểu provider.
2. Có thể có **vòng lặp liên kết** khi FOLLOW_LINKS. Java có cơ chế phát hiện vòng và báo lỗi như **FileSystemLoopException** trong tình huống tương ứng.
3. Người dùng khác có thể **đổi symlink hoặc đường dẫn sau khi bạn kiểm tra**. Mẫu kiểm tra Path rồi mới mở/xóa là race TOCTOU.
4. **Files.isDirectory** và **Files.exists** không phải khóa. Kết quả vừa đọc không bảo đảm giữ nguyên đến bước tiếp theo.
5. Quyền truy cập có thể khác ở từng nhánh; công cụ duyệt phải quản lý IOException và UncheckedIOException.
6. Khi duyệt **để xóa**, không thay tên root bằng input tùy ý của người dùng trong một ví dụ cleanup. Trong hệ thống đối kháng, cân nhắc cơ chế xử lý thư mục dựa trên handle hoặc **SecureDirectoryStream** nếu có hỗ trợ.

Ví dụ chỉ xóa **thư mục tạm do chính chương trình tạo**, giúp phân biệt hướng dẫn học thuật với công cụ xóa toàn bộ đường dẫn bất kỳ.

## 14. Lỗi thường gặp

1. **Cho rằng File.listFiles trả tên alphabet.** Hợp đồng không bảo đảm; sort tường minh.
2. **Dựa vào thứ tự tạo file trên đĩa.** Thứ tự tạo và thứ tự được liệt kê không phải một hợp đồng chung.
3. **Nhầm Files.list với Files.walk.** list lấy một cấp, walk có thể đi toàn bộ cây.
4. **Nghĩ Files.walk là alphabet vì dùng depth-first.** DFS xác định cách đi qua nhánh, không xác định thứ tự sibling.
5. **In Files.walk rồi mới .sorted() trong cùng stream.** Stream đã tiêu thụ không thể tái sử dụng; tạo pipeline đúng thứ tự trước terminal operation.
6. **Quên close Stream từ Files.list / Files.walk.** Tài nguyên thư mục có thể bị giữ cho đến khi đóng.
7. **Dùng sorted() mà không hiểu comparator.** String sort phân biệt hoa/thường, không tự sort số “tự nhiên” hoặc theo ngôn ngữ.
8. **Không có tie-breaker khi comparator coi hai tên bằng nhau.** Kết quả có thể kế thừa thứ tự không xác định ban đầu.
9. **Dùng Files.size hoặc getLastModifiedTime trong comparator nhưng nuốt IOException.** Có thể dẫn đến thứ tự sai và che giấu lỗi; nên lấy metadata có xử lý lỗi trước.
10. **Giả định sorted() miễn phí.** Nó cần lưu dữ liệu, tốn CPU/RAM trên thư mục cực lớn.
11. **Dùng thứ tự tên để xóa cây.** Xóa phải dựa trên quan hệ con trước cha, không chỉ alphabet.
12. **Theo symbolic links mà không giới hạn phạm vi.** Có thể đi ngoài root hoặc gặp vòng lặp.
13. **Bỏ qua việc filesystem thay đổi khi duyệt.** Sort không cung cấp snapshot nhất quán.
14. **Không giới hạn độ sâu hay dung lượng trên cây không tin cậy.** Rủi ro thời gian, RAM, số file và độ sâu recursion.
15. **Coi null từ File.listFiles như mảng rỗng.** Null có thể là đường dẫn không phải directory hoặc lỗi I/O; cần phân biệt.
16. **Không phân biệt sort để hiển thị với sort để thao tác.** Có thể cần những thuật toán khác nhau.

## 15. Câu hỏi tự kiểm tra

**Câu 1:** Files.list(dir) có bảo đảm file đầu là file tên nhỏ nhất không?  
**Đáp:** Không. Muốn sắp xếp theo tên, áp dụng **sorted(Comparator...)** hoặc thu thập và sort.

**Câu 2:** Files.walk(root) duyệt kiểu gì và có đảm bảo alphabet giữa sibling không?  
**Đáp:** Nó duyệt **depth-first**, directory trước entries bên dưới, nhưng **không bảo đảm thứ tự sibling alphabet**.

**Câu 3:** Vì sao sorted() trên Stream kết quả của Files.walk khác với “duyệt từng nhánh đã sort”?  
**Đáp:** sorted() sắp xếp **danh sách đầu ra toàn cục** sau quá trình khám phá; duyệt sibling theo tên đặt thứ tự **trước khi xuống từng nhánh**. Chúng khác về thời điểm, memory và cách thực hiện side effects.

**Câu 4:** File.listFiles() có thể trả null không?  
**Đáp:** Có, nếu File không phải directory hoặc xảy ra lỗi I/O. Không được luôn gọi Arrays.sort trực tiếp mà chưa kiểm tra.

**Câu 5:** Muốn giao diện sắp xếp tên không phân biệt hoa, làm sao giữ kết quả tất định khi "A.txt" và "a.txt" bị coi là bằng nhau?  
**Đáp:** Thêm **thenComparing(Comparator.naturalOrder())** cho tên nguyên bản làm tiêu chí phụ.

**Câu 6:** Khi xóa cả cây, nên xóa directory cha ở preVisitDirectory hay postVisitDirectory?  
**Đáp:** **postVisitDirectory**, sau khi đã xử lý hết con, kết hợp visitFile.

**Câu 7:** Sort bằng Files.getLastModifiedTime có phải tự tạo snapshot nguyên tử của toàn filesystem không?  
**Đáp:** Không. Thuộc tính có thể bị thay đổi khi đọc/duyệt; cần thiết kế theo yêu cầu nhất quán thực tế.

**Câu 8:** Files.walk(root, 0) duyệt bao nhiêu mức?  
**Đáp:** Chỉ **root**. Với maxDepth 1, gồm root và các mục con trực tiếp khi có thể đọc.

## 16. Kết luận

Thứ tự mà filesystem trả về khi liệt kê không phải thứ tự nghiệp vụ của bạn. **File.listFiles**, **Files.list**, **DirectoryStream** không hứa alphabet; **Files.walk** và **walkFileTree** cung cấp cách đi qua cây, nhưng **không quy định sibling theo tên**.

Nếu mục tiêu là **danh sách A–Z**, hãy sort với comparator rõ ràng. Nếu cần **DFS theo sibling A–Z**, hãy thu thập và sắp xếp từng cấp **trước khi đệ quy**. Nếu công việc là **tạo/xóa cây**, hãy ưu tiên **thứ tự phụ thuộc cha/con**, ví dụ preVisitDirectory hoặc postVisitDirectory. Khi kết quả phải tái lập, còn cần quy tắc phụ (tie-breaker), charset/cách so tên thống nhất và kiểm soát biến động của dữ liệu nguồn.

### Tài liệu tham khảo

- [Oracle Java SE 17 — File](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/File.html): list và listFiles không bảo đảm thứ tự, giá trị null và danh sách con trực tiếp.
- [Oracle Java SE 17 — Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html): list, walk, walkFileTree, xử lý symlink và tài nguyên.
- [Oracle Java SE 17 — FileVisitor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileVisitor.html): các callback preVisitDirectory/visitFile/postVisitDirectory.
- [Oracle Java SE 17 — Stream.sorted](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html): sắp xếp, thứ tự, stateful operation và chi phí.
- [Oracle Java SE 17 — Comparator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html): tiêu chí chính/phụ để sắp xếp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="directory-tree-traversal-010">Sao chép hoặc xóa cây theo đúng thứ tự xử lý</a>

<details>
<summary>Xem chi tiết</summary>

# Sao chép hoặc xóa cây thư mục theo đúng thứ tự xử lý trong Java

## 1. Mục tiêu bài học

Khi thao tác với **cây thư mục**, thứ tự xử lý các phần tử là một phần quan trọng của tính đúng đắn. Khác với một file riêng lẻ, cây gồm thư mục cha, thư mục con và file ở nhiều cấp:

- **Sao chép cây:** phải chuẩn bị thư mục **cha trước khi** tạo file hoặc thư mục con trong đích.
- **Xóa cây:** phải xóa **file và thư mục con trước khi** xóa thư mục cha.

Java NIO.2 cung cấp `Files.walkFileTree()` kết hợp `FileVisitor` / `SimpleFileVisitor` để giải quyết bài toán này theo kiểu duyệt chiều sâu (*depth-first traversal*). Bài học tập trung vào hai thao tác ấy, không đánh đồng với `Files.copy(sourceDirectory, targetDirectory)` hay `Files.delete(root)` chỉ áp dụng cho một đối tượng đường dẫn.

Bạn sẽ học được:

1. Ý nghĩa của **pre-order** và **post-order** khi duyệt cây.
2. Cách dùng `preVisitDirectory`, `visitFile`, `postVisitDirectory` và `visitFileFailed`.
3. Ví dụ Java 17+ sao chép toàn cây, sau đó xóa cây đúng thứ tự.
4. Cách xử lý nguồn/đích, symlink, thuộc tính, các lỗi I/O, giới hạn độ sâu và an toàn khi xóa.
5. Khi nào nên dùng `Files.walk()`, vì sao duyệt xong không có nghĩa thứ tự xóa an toàn.

## 2. Cây thư mục và ràng buộc thứ tự

Giả sử có dữ liệu:

```text
source/
├── config.txt
└── reports/
    ├── january.csv
    └── history/
        └── old.csv
```

Để **sao chép** sang `backup/`, ứng dụng phải có một cấu trúc tương đương:

```text
backup/
├── config.txt
└── reports/
    ├── january.csv
    └── history/
        └── old.csv
```

Nếu thử tạo `backup/reports/history/old.csv` khi `backup/reports/history` chưa tồn tại, thao tác copy thông thường sẽ thất bại do chưa có thư mục cha. Vì thế thứ tự chuẩn bị đích thường là:

```text
Tạo backup/
Tạo backup/reports/
Tạo backup/reports/history/
Sao chép các file vào những thư mục đã được tạo
```

Thứ tự tương đối **giữa các file cùng cấp** không quan trọng với việc copy độc lập: file `config.txt` có thể được sao chép trước hoặc sau các file trong `reports/`, miễn cha của từng file đã có.

Khi **xóa** `backup/` thì ngược lại:

```text
Xóa file backup/reports/history/old.csv
Xóa thư mục backup/reports/history/
Xóa file backup/reports/january.csv
Xóa thư mục backup/reports/
Xóa file backup/config.txt
Xóa thư mục backup/
```

Các ví dụ trên chỉ minh họa một thứ tự hợp lệ; **thứ tự duyệt giữa các thư mục con cùng cấp không được bảo đảm** bởi `walkFileTree`. Điều luôn cần giữ là quan hệ cha–con, không phải thứ tự theo tên file.

### Vì sao `Files.delete(root)` chưa đủ?

`Files.delete(path)` xóa **một đối tượng**. Nếu `path` là thư mục còn phần tử con, nó thường ném `DirectoryNotEmptyException`. `Files.deleteIfExists(path)` cũng không tự xóa đệ quy.

Tương tự, `Files.copy(sourceDirectory, targetDirectory)` chỉ tạo một directory đích tương ứng mà **không tự sao chép các phần tử nằm trong directory nguồn**. Muốn sao chép đầy đủ phải duyệt từng phần tử và xử lý đúng thứ tự.

## 3. `walkFileTree()` và bốn callback quan trọng

`Files.walkFileTree(start, visitor)` duyệt cây theo chiều sâu (*depth-first*). Mặc định nó **không đi theo symbolic link**. Các callback của `SimpleFileVisitor<Path>` được gọi theo giai đoạn:

| Callback | Khi nào được gọi | Áp dụng trong bài toán |
| --- | --- | --- |
| `preVisitDirectory(dir, attrs)` | **Trước** khi duyệt các phần tử của thư mục | Tạo thư mục đích khi **copy** cây |
| `visitFile(file, attrs)` | Khi gặp một file, hoặc một entry không được coi là thư mục để đi sâu | Copy file hoặc xóa file/symlink |
| `postVisitDirectory(dir, exc)` | **Sau** khi duyệt xong thư mục hoặc sau lỗi duyệt bên trong | Xóa thư mục khi **delete**; có thể áp dụng metadata sau khi copy |
| `visitFileFailed(file, exc)` | Không thể lấy thuộc tính hoặc truy cập entry/thư mục để duyệt | Báo lỗi hoặc xử lý theo chính sách minh bạch |

Sơ đồ callback cho một cây chỉ có một nhánh:

```text
enter root                  -> preVisitDirectory(root)
  enter docs                -> preVisitDirectory(docs)
    visit hello.txt         -> visitFile(hello.txt)
  leave docs                -> postVisitDirectory(docs)
leave root                  -> postVisitDirectory(root)
```

**Pre-order**: thao tác trên thư mục **trước** nội dung của nó.

**Post-order**: thao tác trên thư mục **sau** khi các phần tử con đã xử lý.

Với `SimpleFileVisitor`, hành vi mặc định của `visitFileFailed` là **ném lại `IOException`**. Trong `postVisitDirectory`, đối số `exc` có thể khác `null` nếu việc lặp phần tử thư mục gặp lỗi; không được mặc nhiên bỏ qua lỗi này rồi xóa cha.

Các giá trị `FileVisitResult` phổ biến:

- `CONTINUE`: tiếp tục duyệt.
- `SKIP_SUBTREE`: tại `preVisitDirectory`, bỏ qua các con của thư mục hiện tại.
- `SKIP_SIBLINGS`: bỏ qua các entry cùng cấp còn lại theo hợp đồng visitor.
- `TERMINATE`: dừng chuyến duyệt.

**Cảnh báo:** Trong chương trình xóa toàn bộ cây, nếu đã `SKIP_SUBTREE` nhưng vẫn tìm cách xóa thư mục cha, thư mục có thể chưa rỗng. Phải xác định rõ chính sách bỏ qua và cách xử lý thất bại, không lờ đi ngoại lệ.

## 4. Java 17+: quan sát đúng trình tự callback

Chương trình bên dưới tạo một thư mục tạm `root`, bên trong có một thư mục `docs` và một file `note.txt`. Để stdout ổn định, ta chỉ có **một nhánh thư mục** và in tên phần tử, không in đường dẫn tuyệt đối.

Lưu thành **`TreeOrderDemo.java`**:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class TreeOrderDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("tree-order-");

        try {
            Path docs = Files.createDirectory(root.resolve("docs"));
            Files.writeString(docs.resolve("note.txt"),
                    "content", StandardCharsets.UTF_8);

            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                private String name(Path path) {
                    return path.equals(root)
                            ? "root" : path.getFileName().toString();
                }

                @Override
                public FileVisitResult preVisitDirectory(
                        Path dir, BasicFileAttributes attrs) {
                    System.out.println("enter " + name(dir));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(
                        Path file, BasicFileAttributes attrs) {
                    System.out.println("file " + name(file));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(
                        Path dir, IOException exc) throws IOException {
                    if (exc != null) throw exc;
                    System.out.println("leave " + name(dir));
                    return FileVisitResult.CONTINUE;
                }
            });
        } finally {
            // Dọn dữ liệu do chương trình tạo: file trước, thư mục sau.
            Files.deleteIfExists(root.resolve("docs").resolve("note.txt"));
            Files.deleteIfExists(root.resolve("docs"));
            Files.deleteIfExists(root);
        }
    }
}
```

Biên dịch/chạy:

```bash
javac TreeOrderDemo.java
java TreeOrderDemo
```

Stdout trên filesystem tạm hoạt động bình thường:

```text
enter root
enter docs
file note.txt
leave docs
leave root
```

Thứ tự này cho thấy:

1. `preVisitDirectory(root)` xuất hiện trước `preVisitDirectory(docs)`.
2. `visitFile(note.txt)` xảy ra khi đang ở trong `docs`.
3. `postVisitDirectory(docs)` chạy trước `postVisitDirectory(root)`.
4. `postVisitDirectory` là điểm thích hợp để xóa một thư mục **sau khi** các con đã được xử lý.

Đây là minh họa vòng đời callback; phần dọn trong `finally` cố ý gọi `deleteIfExists` theo thứ tự từ sâu ra nông vì ta đã biết cấu trúc. Với cây bất kỳ không biết trước, phải duyệt để xác định tất cả các entry.

## 5. Công thức sao chép cây thư mục

Ta có:

- `source`: thư mục gốc nguồn, ví dụ `/data/project`.
- `target`: thư mục đích mới, ví dụ `/backup/project`.
- Với mỗi `dir` thuộc cây nguồn, đường dẫn tương ứng tại đích là `target.resolve(source.relativize(dir))`.
- Với mỗi `file`, cũng dùng phép ánh xạ tương tự.

Ví dụ:

```text
source = /data/project
target = /backup/project

file:       /data/project/docs/help.txt
relative:   docs/help.txt
destination: /backup/project/docs/help.txt
```

`source.relativize(path)` đưa ra **đường dẫn tương đối trong cây nguồn**, còn `target.resolve(...)` đặt nó vào cây đích. Nếu `source` và `path` cùng filesystem, cách ánh xạ này dễ hiểu và tránh thao tác thay chuỗi kiểu `replace("project", "backup")` có thể sửa nhầm cả tên tệp.

### Hai callback quan trọng khi copy

```java
// Đoạn minh họa; source và target là Path đã được xác định.
// Đây là callback bên trong một SimpleFileVisitor<Path>.
@Override
public FileVisitResult preVisitDirectory(
        Path dir, BasicFileAttributes attrs) throws IOException {
    Path destination = target.resolve(source.relativize(dir));
    Files.createDirectory(destination);
    return FileVisitResult.CONTINUE;
}

@Override
public FileVisitResult visitFile(
        Path file, BasicFileAttributes attrs) throws IOException {
    Path destination = target.resolve(source.relativize(file));
    Files.copy(file, destination, LinkOption.NOFOLLOW_LINKS);
    return FileVisitResult.CONTINUE;
}
```

Đây **không phải chương trình độc lập**, vì `source`, `target` và các import được tạo bên ngoài. Callback đầu tiên tạo thư mục cha; callback thứ hai copy mỗi entry khi cha của nó đã có. Chương trình đầy đủ ở mục sau triển khai cùng nguyên tắc.

`Files.copy(file, destination, LinkOption.NOFOLLOW_LINKS)` nhằm **không theo symbolic link ở file nguồn**. Trên provider hỗ trợ link, thao tác này cố copy bản thân link; nếu provider đích không hỗ trợ, có thể xảy ra `IOException` hoặc ngoại lệ hỗ trợ tính năng thích hợp. Đừng mặc định bản sao của symbolic link luôn hoạt động xuyên hệ thống.

### Copy có sao chép tất cả metadata không?

Không. `Files.copy` không mặc nhiên giữ mọi quyền, owner, ACL, timestamp hoặc attributes. `StandardCopyOption.COPY_ATTRIBUTES` yêu cầu cố gắng sao chép metadata nhưng các thuộc tính được bảo toàn phụ thuộc provider và filesystem. Nếu bạn cần giữ thuộc tính thư mục, việc áp dụng **sau khi copy xong các con** có thể thích hợp hơn, vì tạo file trong thư mục có thể làm thay đổi timestamp của thư mục.

Bài này ưu tiên **cấu trúc và nội dung**; không hứa giữ mọi metadata.

## 6. Công thức xóa cây thư mục

Ngược lại với copy:

```java
// Đoạn minh họa callback của SimpleFileVisitor<Path>.
@Override
public FileVisitResult visitFile(
        Path file, BasicFileAttributes attrs) throws IOException {
    Files.delete(file);
    return FileVisitResult.CONTINUE;
}

@Override
public FileVisitResult postVisitDirectory(
        Path dir, IOException exc) throws IOException {
    if (exc != null) throw exc;
    Files.delete(dir);
    return FileVisitResult.CONTINUE;
}
```

Đây **chưa phải chương trình độc lập**. Mấu chốt:

- `visitFile`: xóa từng file và entry không được đi sâu như directory.
- `postVisitDirectory`: xóa thư mục **sau khi** các phần tử đã được xử lý.
- `if (exc != null) throw exc;`: nếu không thể duyệt hoàn chỉnh, **không vờ rằng thư mục đã được xử lý đầy đủ**.
- `visitFileFailed`: để hành vi mặc định của `SimpleFileVisitor` ném lỗi, hoặc override để báo thất bại theo chính sách riêng. Không chỉ `return CONTINUE` và đánh dấu thao tác thành công.

Cây bị thay đổi đồng thời, entry bị khóa hoặc thiếu quyền có thể khiến `Files.delete` thất bại. Thao tác xóa cây **không nguyên tử**: nếu lỗi giữa chừng, một phần cây có thể đã bị xóa và phần còn lại tồn tại.

## 7. Ví dụ Java 17+ hoàn chỉnh: sao chép rồi xóa cây

Chương trình **`TreeCopyDeleteDemo.java`** dưới đây:

1. Tạo sandbox trong một thư mục tạm riêng.
2. Tạo cây nguồn gồm thư mục con và hai file.
3. Copy cây sang một thư mục đích **chưa tồn tại** bằng `preVisitDirectory` và `visitFile`.
4. Kiểm tra nội dung và số lượng file bằng cách truy cập đích.
5. Xóa cây đích theo thứ tự `visitFile → postVisitDirectory`.
6. Dọn cây nguồn và sandbox tạm trong `finally`.

Chương trình dùng dữ liệu do chính nó tạo, **không xóa đường dẫn người dùng nhập từ bên ngoài**.

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class TreeCopyDeleteDemo {
    static void copyTree(Path source, Path target) throws IOException {
        final Path src = source.toAbsolutePath().normalize();
        final Path dst = target.toAbsolutePath().normalize();

        // Chỉ là guard từ vựng; với input không tin cậy cần thêm biện pháp.
        if (src.equals(dst) || dst.startsWith(src)) {
            throw new IllegalArgumentException("target is inside source");
        }
        if (!Files.isDirectory(src, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("source must be a directory");
        }
        if (Files.exists(dst, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(dst.toString());
        }

        Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(
                    Path dir, BasicFileAttributes attrs) throws IOException {
                Path destination = dst.resolve(src.relativize(dir));
                Files.createDirectory(destination);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(
                    Path file, BasicFileAttributes attrs) throws IOException {
                Path destination = dst.resolve(src.relativize(file));
                Files.copy(file, destination, LinkOption.NOFOLLOW_LINKS);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(
                    Path file, IOException exc) throws IOException {
                throw exc;
            }

            @Override
            public FileVisitResult postVisitDirectory(
                    Path dir, IOException exc) throws IOException {
                if (exc != null) throw exc;
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static void deleteTree(Path root) throws IOException {
        // Mặc định walkFileTree KHÔNG theo symbolic links.
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(
                    Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(
                    Path file, IOException exc) throws IOException {
                throw exc;
            }

            @Override
            public FileVisitResult postVisitDirectory(
                    Path dir, IOException exc) throws IOException {
                if (exc != null) throw exc;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void main(String[] args) throws IOException {
        Path sandbox = Files.createTempDirectory("tree-demo-");
        Path source = sandbox.resolve("source");
        Path target = sandbox.resolve("copy");

        try {
            Files.createDirectory(source);
            Files.createDirectory(source.resolve("docs"));
            Files.writeString(source.resolve("note.txt"),
                    "hello", StandardCharsets.UTF_8);
            Files.writeString(source.resolve("docs").resolve("guide.txt"),
                    "guide", StandardCharsets.UTF_8);

            copyTree(source, target);

            System.out.println("root copied = "
                    + Files.isDirectory(target));
            System.out.println("nested file copied = "
                    + Files.isRegularFile(target.resolve("docs")
                            .resolve("guide.txt")));
            System.out.println("copied content = "
                    + Files.readString(target.resolve("note.txt"),
                            StandardCharsets.UTF_8));
            System.out.println("source remains = "
                    + Files.exists(source));

            deleteTree(target);
            System.out.println("copy deleted = "
                    + Files.notExists(target));
        } finally {
            // Dọn những dữ liệu do chính ví dụ tạo ra, kể cả sau lỗi copy.
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                deleteTree(target);
            }
            if (Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
                deleteTree(source);
            }
            Files.deleteIfExists(sandbox);
        }
    }
}
```

Chạy:

```bash
javac TreeCopyDeleteDemo.java
java TreeCopyDeleteDemo
```

Stdout khi các thao tác file tạm thành công:

```text
root copied = true
nested file copied = true
copied content = hello
source remains = true
copy deleted = true
```

### Vì sao code này đúng thứ tự?

**Trong `copyTree`:**

- Lần đầu `preVisitDirectory(src)` tạo `dst`, tức thư mục gốc của cây đích.
- Với mỗi thư mục con, callback tạo thư mục tương ứng trước khi visitor đi sâu xuống các phần tử bên trong.
- `visitFile` thực hiện copy khi thư mục cha đã được tạo.
- `src.relativize(...)` và `dst.resolve(...)` bảo toàn cấu trúc tương đối của mỗi entry.
- `Files.copy` chỉ cần thao tác trên từng file/symlink riêng lẻ; thuật toán không giả định một lời gọi copy thư mục sẽ copy luôn nội dung đệ quy.

**Trong `deleteTree`:**

- `visitFile` xóa từng file/symlink khi nó được gặp.
- Chỉ sau khi các con của một directory đã được xử lý, `postVisitDirectory` mới xóa directory ấy.
- Thư mục gốc `target` cũng được xóa ở callback cuối, sau mọi nội dung bên trong.

### Hạn chế có chủ đích của ví dụ

Để giữ ví dụ dễ chạy, nó giả định **file và thư mục thường trên filesystem tạm có quyền ghi**. Đây không phải thư viện copy/delete production tổng quát:

- Chỉ từ chối đích nằm trong nguồn bằng **kiểm tra đường dẫn từ vựng**; chưa chống symlink/junction, mount, thay đổi đồng thời hoặc đối tượng được đổi tên khi đang thao tác.
- Không ghi đè đích đã tồn tại và không xử lý hợp nhất nội dung của các thư mục.
- Không copy đầy đủ ACL, owner, mọi timestamp, hard links hoặc các khả năng phụ thuộc provider.
- Không bảo đảm tính nguyên tử; nếu sao chép lỗi giữa chừng thì đích có thể chỉ được tạo một phần.
- Không giới hạn tổng số file, byte, độ sâu hay thời gian khi gặp dữ liệu ngoài hệ thống.
- `finally` minh họa cleanup trong sandbox riêng. Với dữ liệu quan trọng, xóa nguồn/đích sau một lỗi phải theo **chính sách phục hồi**, không được áp dụng vô điều kiện như với dữ liệu test.

## 8. Vì sao không nên sao chép cây vào bên trong chính nguồn?

Giả sử sao chép `source/` sang `source/backup/`. Khi visitor đi qua `source`, nó có thể nhìn thấy thư mục `backup` vừa được tạo trong lúc duyệt. Tùy thời điểm và thứ tự liệt kê, chương trình có thể vô tình copy thư mục đích vào chính nó, lặp không kiểm soát hoặc tạo cây phình to.

Vì vậy, đối với thuật toán copy cây cần:

1. Từ chối `source == target`.
2. Từ chối target là đường dẫn con của source theo chính sách đã định.
3. Giải quyết rõ trường hợp symbolic link hoặc mount khiến đường dẫn thật khác tên đường dẫn.
4. Tránh copy cây đang bị thay đổi đồng thời nếu định dạng kết quả yêu cầu snapshot nhất quán.

`dst.startsWith(src)` sau khi chuẩn hóa chỉ là một kiểm tra **cú pháp**, không phải đảm bảo bảo mật. Với đường dẫn do người dùng cung cấp, ứng dụng còn cần quản lý quyền truy cập, symlink, kiểm tra thư mục thực và biện pháp chống race nếu có thể.

Cũng không nên để target **bao trùm** source rồi vô tình ghi đè file nguồn qua một phép ánh xạ không được thiết kế cẩn thận. Khi quan hệ nguồn/đích phức tạp, hãy tách vùng lưu trữ/staging rõ ràng.

## 9. Symbolic link, symlink loop và file đặc biệt

Theo mặc định:

```java
Files.walkFileTree(start, visitor);
```

tương đương với duyệt không có `FileVisitOption.FOLLOW_LINKS`, theo mọi cấp. Vì vậy:

- Một symlink trỏ đến thư mục khác **không bị đi sâu theo thư mục đích** trong lần duyệt mặc định.
- `visitFile` có thể nhận entry symlink, dù nó không phải regular file.
- Khi xóa, `Files.delete(linkPath)` xóa **entry symlink**, không xóa cả cây mà link trỏ tới (theo ngữ nghĩa các provider thông thường có hỗ trợ symbolic link).
- Khi copy với `LinkOption.NOFOLLOW_LINKS`, ta yêu cầu copy **bản thân symbolic link**, không lấy dữ liệu ở đích mà link trỏ tới. Phải xác minh đích hỗ trợ kiểu entry ấy.
- Nếu dùng `FOLLOW_LINKS`, traversal có thể đi ra ngoài phạm vi ban đầu qua link. Java có cơ chế phát hiện chu trình và báo `FileSystemLoopException` qua `visitFileFailed`, nhưng điều đó **không thay thế chính sách bảo vệ đường dẫn**.

**Tại sao xóa cây thường không nên theo symbolic link?** Vì một link bên trong thư mục bạn định xóa có thể trỏ đến cây khác nằm ngoài phạm vi được phép. Việc vô tình đệ quy theo link có thể xóa dữ liệu **không thuộc** cây đích theo ý định ban đầu.

Còn có:

- File đặc biệt, FIFO/socket node hoặc filesystem ảo có ngữ nghĩa riêng.
- Permission và file đang được mở/khóa.
- Các giới hạn file name, path length, quota, max depth.
- Provider không hỗ trợ symlink, owner metadata hoặc thao tác đặc biệt.

Đối với input không tin cậy hoặc cần bảo đảm bảo mật cao, một phép duyệt `walkFileTree` đơn giản không tự khóa cây trước thay đổi đồng thời. Cần thiết kế việc cấp quyền, giới hạn phạm vi, chính sách symlink, và khi provider hỗ trợ có thể xem xét API tương đối với directory handle, chẳng hạn `SecureDirectoryStream`.

## 10. Xử lý lỗi trong callback như thế nào?

### Không thể đọc file hoặc mở directory

`visitFileFailed(file, exc)` nhận lỗi đã ngăn việc truy cập entry. Nếu mục tiêu là **copy đầy đủ** hoặc **xóa đầy đủ**, cách an toàn mặc định là **ném ngoại lệ**, không âm thầm bỏ qua.

```java
// Snippet callback, không phải chương trình độc lập.
@Override
public FileVisitResult visitFileFailed(
        Path file, IOException exc) throws IOException {
    throw exc;
}
```

Nếu nghiệp vụ cho phép **best-effort** (xử lý được bao nhiêu hay bấy nhiêu), có thể thu thập các lỗi theo từng đường dẫn. Nhưng phải báo kết quả là **hoàn tất một phần**, không được mô tả “đã sao chép/xóa toàn bộ cây” khi còn lỗi.

### Không thể liệt kê hết thư mục

`postVisitDirectory(dir, exc)` nhận `exc != null` khi traversal của directory bị lỗi; ví dụ không thể hoàn tất việc liệt kê entry. Nếu đang xóa, không thể mặc nhiên cho rằng thư mục đã trống và hợp lệ để xóa.

```java
// Snippet callback.
@Override
public FileVisitResult postVisitDirectory(
        Path dir, IOException exc) throws IOException {
    if (exc != null) throw exc;
    Files.delete(dir);
    return FileVisitResult.CONTINUE;
}
```

### `DirectoryNotEmptyException` và race

Nếu một tiến trình khác tạo file mới trong directory sau khi visitor đã liệt kê xong nhưng trước `Files.delete(dir)`, việc xóa thư mục có thể thất bại. `DirectoryNotEmptyException` không có nghĩa Java duyệt sai thứ tự: cây có thể đã thay đổi.

Tương tự, file có thể bị xóa bởi tiến trình khác ngay trước lúc bạn gọi `Files.delete(file)` và phát sinh `NoSuchFileException`. Có thể chọn `deleteIfExists` nếu nghiệp vụ coi file đã biến mất là chấp nhận được; đừng tự động thay hết `delete` bằng `deleteIfExists` nếu việc tồn tại là ràng buộc quan trọng.

### Vì sao `try/catch` không làm thao tác nguyên tử?

Bọc toàn bộ traversal trong `try/catch(IOException)` giúp thông báo lỗi nhưng **không hoàn tác** những file đã copy hoặc xóa. Một số entry đã được tạo/xóa trước khi callback gặp lỗi vẫn có thể giữ nguyên trạng thái mới. Cần có cơ chế staging, rollback hoặc reconciliation nếu nghiệp vụ đòi hỏi trạng thái nhất quán.

## 11. `Files.walk()` so với `Files.walkFileTree()`

`Files.walk(root)` trả về `Stream<Path>` để xử lý các path trong cây. Nó có thể hữu ích khi bạn chỉ muốn lọc/tổng hợp danh sách file. Nhưng khi cần copy/delete có ràng buộc cha–con, `walkFileTree` thể hiện ý nghĩa callback rõ ràng hơn.

| Tiêu chí | `Files.walk(root)` | `Files.walkFileTree(root, visitor)` |
| --- | --- | --- |
| Kiểu trả về | `Stream<Path>`, cần đóng | Trả lại `Path` bắt đầu sau khi duyệt |
| Điều khiển pre/post | Không có callback tách rõ | Có `preVisitDirectory` và `postVisitDirectory` |
| Phân biệt lỗi | Lỗi khi duyệt stream có thể thành `UncheckedIOException` | Có `visitFileFailed` và đối số `exc` |
| Lọc/đếm path | Tiện với Stream API | Làm qua visitor/state |
| Copy cây theo điều kiện cha trước | Cần tự tổ chức ràng buộc | `preVisitDirectory` rất phù hợp |
| Xóa cây theo điều kiện con trước | Cần sắp thứ tự rõ ràng | `postVisitDirectory` rất phù hợp |
| Symlink | Mặc định không follow; có tùy chọn | Mặc định không follow; có tùy chọn |

Một số ví dụ trên mạng dùng:

```java
// Snippet, không phải chương trình độc lập:
try (Stream<Path> stream = Files.walk(root)) {
    stream.sorted(Comparator.reverseOrder())
          .forEach(path -> {
              // Thực hiện delete từng path...
          });
}
```

Cách này **không tự giải quyết** ngoại lệ I/O, phạm vi được xóa và vấn đề đường dẫn đang thay đổi. Nếu dùng Stream phải đóng stream, đồng thời xử lý `IOException` ở bước xóa (lambda có thể cần bọc thành unchecked). Thứ tự so sánh đường dẫn còn gắn với semantics của provider: đừng dùng chỉ `Path.toString()` theo thứ tự chữ như bằng chứng tổng quát về thứ tự cha–con. Đối với copy/delete, visitor có callback cha/trước và cha/sau thể hiện mục đích rõ hơn.

### `FileVisitor` có bảo đảm thứ tự tên file không?

Không. Thứ tự entry của một thư mục thường phụ thuộc filesystem/provider và trạng thái dữ liệu. Nếu cần xử lý theo tên tăng dần vì mục đích nghiệp vụ, phải thiết kế cơ chế liệt kê/sắp thứ tự riêng và kiểm soát tài nguyên. **Đừng làm bài copy/delete phụ thuộc vào thứ tự chữ cái** của sibling.

## 12. Metadata và thời điểm sao chép attributes

Bài toán “copy tree” đôi khi phải giữ permission, thời điểm sửa, owner hoặc ACL. Tuy nhiên `COPY_ATTRIBUTES` không bảo đảm mọi metadata được giữ và có thể hành xử khác giữa POSIX/Windows/ZIP/cloud provider.

Đặc biệt với **thư mục**, nếu copy timestamp quá sớm ở `preVisitDirectory`, các thao tác tạo file và subdirectory bên trong có thể làm thay đổi `lastModifiedTime` của thư mục. Một cách thiết kế là:

1. `preVisitDirectory`: tạo directory đích và chuẩn bị quyền ghi cần thiết.
2. `visitFile`: sao chép file và attributes nào thật sự được hỗ trợ.
3. `postVisitDirectory`: sau khi các con hoàn tất, đặt lại metadata thư mục theo hợp đồng (nếu có yêu cầu và provider hỗ trợ).
4. Nếu metadata bắt buộc mà không copy được, báo thất bại thay vì coi “copy thành công” không cần kiểm tra.

Ví dụ minh họa thuật toán, **không phải chương trình độc lập**:

```java
// Giả định source/destinationDirectory là Path hợp lệ;
// policies và handling lỗi được quyết định ở nơi gọi.
FileTime stamp = Files.getLastModifiedTime(sourceDirectory);
Files.setLastModifiedTime(destinationDirectory, stamp);
```

Đoạn này cần import `java.nio.file.attribute.FileTime` và các biến đã được khai báo. Không đảm bảo mọi provider cho phép đặt chính xác timestamp hoặc giữ owner/ACL.

## 13. Copy/xóa trên dữ liệu thực: an toàn và phục hồi

Sao chép hoặc xóa một cây lớn có nhiều điểm có thể thất bại: dung lượng đĩa, lỗi mạng, quyền truy cập, symlink, quá nhiều entry, giới hạn độ sâu, file bị thay đổi đồng thời hoặc ứng dụng bị dừng.

Một quy trình thực tế nên xác định:

1. **Nguồn và đích:** canonical/real location khi phù hợp, từ chối copy target nằm trong source và không trộn đường dẫn của provider khác trong phép `relativize` không tương thích.
2. **Loại đối tượng:** regular files, directories, symlinks, special files và chiến lược cho từng loại.
3. **Quota:** giới hạn số file, tổng byte, chiều sâu, thời gian thực thi; không cứ thấy file là copy vô hạn.
4. **Quy tắc ghi đè:** không ghi đè, ghi đè có xác minh hay tạo phiên bản mới; cần giải quyết xung đột tên/permission.
5. **Staging:** có thể copy vào một thư mục tạm của đích, xác minh xong mới công bố. Staging không mặc nhiên biến cả cây thành một giao dịch nguyên tử xuyên filesystem.
6. **Verify:** kiểm tra đủ file, kích thước, hash khi cần, attributes bắt buộc; không coi `Files.copy` trả về là đủ cho mọi yêu cầu nghiệp vụ.
7. **Lỗi giữa chừng:** báo các path đã làm, path thất bại, quyết định cleanup theo quyền sở hữu dữ liệu; không tự xóa bản duy nhất của dữ liệu nguồn.
8. **Xóa:** kiểm tra thư mục đích thật chính xác, có backup/confirmation khi nghiệp vụ đòi hỏi, không duyệt theo symlink ngoài phạm vi.
9. **Concurrency:** cân nhắc khóa, snapshot hoặc ngừng writer nếu nhất quán là yêu cầu bắt buộc; `walkFileTree` không chụp ảnh snapshot toàn bộ cây.
10. **Provider:** ZIP/network/cloud filesystem có giới hạn và ngoại lệ khác nhau; phải kiểm thử trên provider thật.

Đối với thao tác **xóa**, tuyệt đối không gọi `deleteTree` trên đường dẫn rỗng, đường dẫn gốc filesystem hoặc path từ request chưa được xác minh trong ứng dụng production. Một demo xóa thư mục tạm do nó tự tạo là an toàn trong phạm vi bài học; điều đó không cấp phép xóa dữ liệu người dùng tùy tiện.

## 14. Bảng tóm tắt thứ tự

| Bài toán | Thời điểm đúng | Callback |
| --- | --- | --- |
| Copy thư mục cha | Trước khi vào các phần tử con | `preVisitDirectory` |
| Copy file | Sau khi thư mục cha đích đã có | `visitFile` |
| Thiết lập metadata thư mục sau copy | Sau khi copy xong các con (khi cần) | `postVisitDirectory` |
| Xóa file/symlink | Khi gặp entry | `visitFile` |
| Xóa thư mục | Sau khi xóa hết các con | `postVisitDirectory` |
| Xử lý file không truy cập được | Tại lỗi truy cập | `visitFileFailed` |
| Phát hiện directory iteration lỗi | Trong callback hậu duyệt | Kiểm tra `exc` của `postVisitDirectory` |

## 15. Những lỗi phổ biến

1. **Gọi `Files.copy(sourceDir, targetDir)` và nghĩ toàn bộ con được copy.** API này không tự copy đệ quy.
2. **Gọi `Files.delete(root)` khi root còn file con.** Cần xóa các entry con trước khi xóa root.
3. **Tạo file đích trước khi có directory cha.** Dùng `preVisitDirectory` để tạo thư mục đích theo cấp.
4. **Xóa directory trong `preVisitDirectory`.** Các con chưa bị xóa, nên thường gặp `DirectoryNotEmptyException`.
5. **Copy target nằm bên trong source.** Có thể làm cây copy tự đi vào chính nó; phải chặn theo chính sách tin cậy.
6. **Dùng `Files.walk` mà quên đóng Stream.** Có thể giữ descriptor/directory handle; dùng `try-with-resources`.
7. **Mặc định theo symbolic link khi xóa.** Có thể đi ra ngoài phạm vi và gây mất dữ liệu khác; mặc định `walkFileTree` không follow.
8. **Nuốt `IOException` trong visitor rồi báo hoàn tất.** Dẫn tới bản copy thiếu file hoặc cây xóa dở.
9. **Bỏ qua `exc` của `postVisitDirectory`.** Không thể kết luận thư mục đã được duyệt đầy đủ.
10. **Cho rằng copy/delete đệ quy là một transaction.** Có thể thành công một phần; cần policy về retry/cleanup.
11. **Cho rằng mọi provider hỗ trợ symlink, ACL, owner/timestamp giống nhau.** Khả năng khác nhau, phải kiểm tra thực tế.
12. **Thử “xóa cho xong” cây khi có writer đang tạo file mới.** Có thể liên tục thất bại hoặc xóa ngoài dự tính.
13. **Chỉ so sánh tên đường dẫn để bảo vệ dữ liệu quan trọng.** Symlink và race có thể làm vị trí thực khác ý định.
14. **Dùng `FileVisitResult.SKIP_SUBTREE` nhưng vẫn tính là đã xử lý hết.** Những entry bỏ qua vẫn còn nguyên.
15. **Dựa vào thứ tự chữ cái của file siblings để bảo đảm đúng thuật toán.** `walkFileTree` không hứa thứ tự đó; phải dựa vào quan hệ cha–con.

## 16. Tự kiểm tra

**Câu 1.** Tại sao phải tạo thư mục đích ở `preVisitDirectory`, không phải `postVisitDirectory`?

**Trả lời:** Vì file và thư mục con cần cha tồn tại trước khi copy. `postVisitDirectory` chạy sau khi các con đã xử lý nên quá muộn để tạo cha.

**Câu 2.** Tại sao phải xóa thư mục ở `postVisitDirectory`, không phải `preVisitDirectory`?

**Trả lời:** Vì thư mục đang còn phần tử con thì chưa thể xóa như thư mục rỗng. Callback hậu duyệt chạy sau khi các con được xử lý.

**Câu 3.** Một file bị lỗi quyền đọc trong quá trình copy cây. Có nên `return CONTINUE` rồi báo “copy đầy đủ” không?

**Trả lời:** Không. Phải ném/báo lỗi, hoặc đánh dấu kết quả hoàn tất một phần nếu nghiệp vụ cho phép bỏ qua.

**Câu 4.** Duyệt để xóa cây có symbolic link trỏ sang thư mục khác; có nên tự bật `FOLLOW_LINKS` không?

**Trả lời:** Không nên mặc định. Cần bảo vệ phạm vi xóa; mặc định không theo link sẽ xử lý entry liên kết thay vì duyệt vào đích.

**Câu 5.** Tại sao `target.resolve(source.relativize(file))` hữu ích cho copy cây?

**Trả lời:** Nó giữ đường dẫn tương đối của file bên trong source và ghép vào target, bảo toàn cấu trúc thư mục.

**Câu 6.** Nếu copy lỗi sau khi tạo một phần file tại target, có bảo đảm rollback tự động không?

**Trả lời:** Không. Phải xây dựng chiến lược cleanup, staging và xác minh kết quả nếu nghiệp vụ yêu cầu.

**Câu 7.** Có thể dựa vào `Files.walkFileTree` để duyệt `a.txt` trước `b.txt` không?

**Trả lời:** Không. API không bảo đảm thứ tự tên của các entry cùng cấp; chỉ có quy tắc callback trước/sau các phần tử con.

**Câu 8.** `DirectoryNotEmptyException` khi xóa một directory đã duyệt xong luôn là lỗi sắp xếp thứ tự không?

**Trả lời:** Không. Một tiến trình khác có thể vừa tạo file mới; hoặc visitor đã bỏ qua/lỗi khi xử lý entry. Cần kiểm tra nguyên nhân.

## 17. Tài liệu Java 17

- [`Files.walkFileTree`, `Files.copy`, `Files.delete`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [`FileVisitor` — callbacks của quá trình duyệt](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileVisitor.html)
- [`SimpleFileVisitor` — hành vi mặc định và cách override](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/SimpleFileVisitor.html)
- [`FileVisitResult` — CONTINUE, SKIP_SUBTREE, SKIP_SIBLINGS, TERMINATE](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileVisitResult.html)

**Ghi nhớ:** Để **copy cây**, tạo thư mục đích khi bắt đầu duyệt thư mục (`preVisitDirectory`), sau đó copy từng file (`visitFile`). Để **xóa cây**, xóa file ở `visitFile` và xóa thư mục chỉ khi các con đã xử lý xong (`postVisitDirectory`). `Files.walkFileTree` giúp biểu diễn đúng quy tắc cha–con; kết quả cuối vẫn phụ thuộc xử lý lỗi, symlink, quyền truy cập và trạng thái filesystem.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="directory-tree-traversal-011">Đóng Stream trả về từ list, walk và find</a>

<details>
<summary>Xem chi tiết</summary>

# Đóng Stream trả về từ Files.list, Files.walk và Files.find trong Java

## 1. Tại sao Stream duyệt thư mục phải được đóng?

Trong Java NIO.2, các phương thức **`Files.list(Path)`**, **`Files.walk(Path, ...)`** và **`Files.find(Path, ...)`** tạo **`Stream<Path>`** để duyệt nội dung thư mục hoặc cây thư mục. Chúng tận dụng cơ chế *lazy traversal*: chương trình chưa cần lấy toàn bộ danh sách đường dẫn vào bộ nhớ trước khi lọc, đếm hoặc tìm phần tử đầu tiên.

Nhưng Stream ở đây **không chỉ là một pipeline tính toán trên dữ liệu trong RAM**. Nó có thể giữ tham chiếu đến **thư mục đang mở**, bộ duyệt directory và tài nguyên hệ điều hành. Đặc biệt, `walk` và `find` có thể giữ **một hoặc nhiều thư mục đang mở** trong quá trình đi sâu vào cây.

**Quy tắc quan trọng nhất:**

> **Stream<Path> nhận từ `Files.list`, `Files.walk` hoặc `Files.find` phải được đóng bằng `close()` khi sử dụng xong; ưu tiên try-with-resources.** Việc gọi terminal operation như `forEach()`, `count()`, `findFirst()` hay `toList()` **không tự thay thế việc đóng Stream**.

~~~text
Files.list(dir) / Files.walk(root) / Files.find(root, ...)
             |
             v
Stream<Path> gắn với tài nguyên duyệt thư mục
             |
             | filter / map / limit / sorted ...
             v
Terminal operation: forEach / count / findFirst / toList
             |
             | Đã tính xong kết quả, nhưng không tự close
             v
Thoát try-with-resources
             |
             v
Stream.close() -> giải phóng directory resources liên quan
~~~

Nếu quên đóng, đặc biệt ở dịch vụ chạy lâu hoặc xử lý hàng nghìn thư mục, ứng dụng có thể giữ nhiều directory handle/file descriptor hơn cần thiết. Hệ quả tùy hệ điều hành và provider: cạn tài nguyên, lỗi mở file/thư mục, hoặc trở ngại khi sửa/xóa thư mục.

Lưu ý: **không phải mọi Stream trong Java đều buộc phải đóng**. Ví dụ `List.of(1, 2, 3).stream()` chỉ xử lý dữ liệu trong bộ nhớ. Với ba API Files nói trên, **hợp đồng API quy định tài nguyên mở cần giải phóng**, nên close không phải thao tác tùy chọn.

## 2. Phân biệt list, walk và find

| API | Phạm vi duyệt | Giá trị trả về | Điểm khác biệt | Vòng đời |
| --- | --- | --- | --- | --- |
| `Files.list(dir)` | Các mục trực tiếp trong `dir`, **không đệ quy** | `Stream<Path>` | Không tự bao gồm `dir`; không tự đi vào thư mục con | **Phải đóng** |
| `Files.walk(start)` | Duyệt đệ quy từ `start`; mặc định sâu tối đa theo giới hạn int | `Stream<Path>` | Bao gồm chính `start`; duyệt depth-first | **Phải đóng** |
| `Files.walk(start, maxDepth)` | Duyệt cây nhưng giới hạn độ sâu | `Stream<Path>` | `maxDepth=0` chỉ bao gồm `start`; độ sâu 1 có thể gồm các con trực tiếp | **Phải đóng** |
| `Files.find(start, maxDepth, matcher)` | Duyệt cây và chỉ trả các path khớp bộ lọc | `Stream<Path>` | `matcher` nhận cả `Path` và `BasicFileAttributes` | **Phải đóng** |

Các tên phương thức phản ánh **phạm vi và mục đích tìm kiếm**, không phản ánh một cơ chế cleanup khác nhau. Cả ba đều yêu cầu đóng tài nguyên sau khi xong.

**`Files.list` không phải `java.io.File.list()`**. Hai API thuộc lớp khác nhau:

- `Files.list(Path)`: trả `Stream<Path>` **gắn với thư mục mở**, nên phải đóng.
- `File.list()`: trả mảng `String[]` hoặc null theo hợp đồng riêng; không phải Stream và không có cùng vòng đời Stream trả về.

Cũng không nhầm `Files.find` với phép `stream.filter` thông thường. `Files.find` **tự duyệt cây thư mục** và cung cấp metadata cho matcher; `filter` chỉ là thao tác trung gian trên một Stream đã tồn tại.

## 3. Mẫu mặc định: try-with-resources

Để liệt kê một thư mục:

~~~java
// Mảnh code trong phương thức có Path directory và xử lý IOException.
try (Stream<Path> paths = Files.list(directory)) {
    paths.forEach(System.out::println);
}
~~~

Để đi qua cây thư mục:

~~~java
try (Stream<Path> paths = Files.walk(root)) {
    paths.filter(Files::isRegularFile)
         .forEach(System.out::println);
}
~~~

Để tìm file theo thuộc tính:

~~~java
try (Stream<Path> paths = Files.find(
        root,
        4,
        (path, attrs) -> attrs.isRegularFile()
                && path.getFileName().toString().endsWith(".txt"))) {
    paths.forEach(System.out::println);
}
~~~

Đây là **các mảnh code minh họa**, giả định `Path`, `Files`, `Stream` được import và IOException được xử lý.

Cơ chế:

1. Java gọi `Files.list/walk/find` và nhận về Stream gắn với nguồn.
2. Các intermediate operation `filter`, `map`... cấu hình pipeline.
3. Terminal operation duyệt phần tử theo nhu cầu.
4. Dù body kết thúc bình thường, `return` hay ném exception, **try-with-resources cố đóng Stream**.

Cú pháp `try (Stream<Path> paths = ...)` có thể dùng vì `Stream` kế thừa `BaseStream`, mà `BaseStream` hỗ trợ `AutoCloseable` và phương thức `close()`.

**Điều phải nhớ:** nếu gọi API Files tạo Stream **ngoài** phạm vi `try` rồi gặp lỗi trước khi vào một finally/try quản lý nó, tài nguyên có nguy cơ không được đóng. Nên đặt phép mở Stream trực tiếp trong khai báo resource của try-with-resources.

## 4. Lazy traversal không có nghĩa không mở tài nguyên

Một nhầm lẫn phổ biến là “Stream lười nên chưa cần đóng cho đến khi thực sự đọc được phần tử đầu tiên”. Thực tế, việc trả Stream có thể đã thiết lập cơ chế duyệt thư mục và giữ tham chiếu tài nguyên; **API bắt buộc đóng cả khi không tiêu thụ phần tử nào**.

~~~java
// SAI: tạo Stream rồi bỏ đi mà không đóng.
Files.list(directory);
~~~

Dù không hề có terminal operation, Stream trả về không được đóng đúng thời điểm. Tương tự, cấu hình `filter` hoặc `limit` rồi bỏ pipeline không tự đóng nguồn.

Một chương trình có thể làm cả ba việc:

~~~java
Stream<Path> stream = Files.walk(root);
Stream<Path> filtered = stream.filter(Files::isRegularFile);
// Vẫn chưa chạy terminal operation.
~~~

Đây chưa phải ví dụ cleanup hoàn chỉnh: mã phải đảm bảo **đóng pipeline cuối cùng hoặc nguồn cùng vòng đời**. Dùng TWR trên pipeline được tạo trực tiếp thường rõ ràng hơn:

~~~java
try (Stream<Path> paths = Files.walk(root)
        .filter(Files::isRegularFile)) {
    paths.forEach(System.out::println);
}
~~~

Đóng pipeline có nguồn `Files.walk` làm giải phóng tài nguyên do pipeline mang theo, thay vì tiếp tục để directory handles chờ GC. **Không dùng Stream lần thứ hai** sau khi đã gọi một terminal operation trên nó.

## 5. Chương trình Java 17+ so sánh cả ba API

Tạo file **DirectoryStreamsDemo.java**, lưu mã nguồn UTF-8:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class DirectoryStreamsDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("directory-streams-");
        Path nested = root.resolve("nested");
        Path alpha = root.resolve("alpha.txt");
        Path beta = nested.resolve("beta.txt");

        try {
            Files.createDirectory(nested);
            Files.writeString(alpha, "alpha", StandardCharsets.UTF_8);
            Files.writeString(beta, "beta", StandardCharsets.UTF_8);

            try (Stream<Path> entries = Files.list(root)) {
                List<String> names = entries
                        .map(p -> p.getFileName().toString())
                        .sorted()
                        .toList();
                System.out.println("list=" + names);
            }

            try (Stream<Path> tree = Files.walk(root, 2)) {
                List<String> names = tree
                        .map(p -> p.equals(root)
                                ? "."
                                : root.relativize(p).toString()
                                      .replace('\\', '/'))
                        .sorted()
                        .toList();
                System.out.println("walk=" + names);
            }

            try (Stream<Path> matches = Files.find(
                    root, 2,
                    (p, attrs) -> attrs.isRegularFile()
                            && p.getFileName().toString()
                                    .endsWith(".txt"))) {
                List<String> names = matches
                        .map(p -> p.getFileName().toString())
                        .sorted()
                        .toList();
                System.out.println("find=" + names);
            }
        } finally {
            Files.deleteIfExists(beta);
            Files.deleteIfExists(alpha);
            Files.deleteIfExists(nested);
            Files.deleteIfExists(root);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 DirectoryStreamsDemo.java
java DirectoryStreamsDemo
~~~

Stdout mong đợi:

~~~text
list=[alpha.txt, nested]
walk=[., alpha.txt, nested, nested/beta.txt]
find=[alpha.txt, beta.txt]
~~~

**Giải thích chi tiết:**

- Thư mục demo có `alpha.txt` tại root và `nested/beta.txt` bên dưới một thư mục con.
- `Files.list(root)` chỉ trả **hai mục ngay bên trong root**: `alpha.txt` và `nested`. Nó **không** đi tiếp đến `beta.txt` và cũng không tự trả chính `root`.
- `Files.walk(root, 2)` trả cả `root` (depth 0), hai mục trực tiếp (depth 1), và `nested/beta.txt` (depth 2). Dòng `"."` là biểu diễn do ví dụ chủ động đặt cho chính root.
- `Files.find(root, 2, matcher)` duyệt với cùng giới hạn depth 2 nhưng chỉ **đưa vào Stream** những mục có `attrs.isRegularFile()` và tên kết thúc `.txt`.
- Dùng `sorted()` để ổn định stdout giữa các hệ điều hành, vì **thứ tự liệt kê directory không được bảo đảm**. `replace('\\', '/')` chỉ chuẩn hóa dấu phân cách khi **hiển thị tương đối trong ví dụ**, không sửa Path thật.
- Cả ba Stream đều được đặt riêng trong **try-with-resources**. Mỗi nguồn được đóng trước khi chuyển sang thao tác tiếp theo.
- Khối `finally` xóa các file/thư mục tạm do chương trình sở hữu; đây là **cleanup phụ** của demo, không thay thế việc đóng Stream.

Chương trình giả định môi trường được phép tạo file và thư mục tạm; nếu quyền, bộ nhớ đĩa hoặc hệ thống I/O gặp lỗi, phương thức sẽ ném IOException thay vì in đủ stdout dự kiến.

## 6. Độ sâu của Files.walk và Files.find

Cả `walk` và `find` có tham số **`maxDepth`**:

~~~text
root/                   depth 0
root/a.txt              depth 1
root/sub/               depth 1
root/sub/b.txt          depth 2
root/sub/inner/         depth 2
root/sub/inner/c.txt    depth 3
~~~

- `maxDepth=0`: chỉ xét chính `root`.
- `maxDepth=1`: xét root và các mục trực tiếp.
- `maxDepth=2`: xét thêm các mục ở độ sâu 2.
- `maxDepth` âm: ném `IllegalArgumentException`.
- `Files.walk(root)` không truyền giới hạn được quy định tương đương việc dùng độ sâu `Integer.MAX_VALUE`.

**Giới hạn độ sâu không thay thế close()**. Chỉ đi một cấp hoặc chỉ thăm vài phần tử vẫn dùng Stream có thể giữ tài nguyên thư mục mở.

`Files.walk` duyệt **depth-first** và trả mục bắt đầu trước các mục con. Đối với mỗi directory, các mục bên trong xuất hiện khi được gặp trong quá trình duyệt. Nhưng **thứ tự các sibling không được bảo đảm theo alphabet**. Nếu cần thứ tự ổn định, áp dụng `sorted()` hoặc một comparator phù hợp, và lưu ý việc sắp xếp có thể phải giữ nhiều phần tử trong bộ nhớ.

**`Files.walk` bao gồm chính `start` ngay cả khi start là file thường**. Khi start là file thường hợp lệ, Stream có thể chỉ gồm chính đường dẫn đó; đừng hiểu rằng walk chỉ trả các file *bên trong* một directory.

## 7. Files.find: matcher nhận Path và BasicFileAttributes

Cú pháp tiêu biểu:

~~~java
try (Stream<Path> results = Files.find(
        root,
        5,
        (path, attrs) -> attrs.isRegularFile()
                && attrs.size() > 0
                && path.getFileName().toString().endsWith(".json"))) {
    results.forEach(System.out::println);
}
~~~

`BiPredicate<Path, BasicFileAttributes>` nhận:

- **`path`**: đường dẫn của mục đang được xét.
- **`attrs`**: nhóm metadata cơ bản đã lấy cho mục đó, có các thông tin như `isRegularFile()`, `isDirectory()`, `size()`, thời điểm sửa đổi...

So với `Files.walk(root).filter(p -> ...)` mà **mỗi lần lọc lại gọi `Files.readAttributes(p, ...)`**, `Files.find` có thể tránh một số lần lấy metadata trùng lặp vì API vốn truy cập thuộc tính của các mục trong lúc duyệt.

Cần phân biệt:

- **`Files.find` chỉ đưa vào kết quả những path mà matcher chấp nhận**. Nó vẫn phải duyệt các phần của cây để tìm mục phù hợp; matcher trả false cho một directory **không có nghĩa tự động cắt bỏ toàn bộ nhánh**.
- Nếu muốn **chủ động bỏ qua một subtree** khi gặp directory nhất định, `Files.walkFileTree` với `FileVisitor` và kết quả `SKIP_SUBTREE` thường phù hợp hơn.
- Vì matcher thường gọi nhiều lần, tránh side effect không cần thiết. Nếu matcher có logic I/O riêng và ném unchecked exception, exception vẫn cần được ứng dụng xử lý.

**Không coi metadata là snapshot bất biến:** file có thể thay đổi sau khi attributes được đọc. Với ứng dụng cần tính nhất quán mạnh, vẫn phải xác định chính sách khóa/snapshot hoặc cách mở file theo danh tính phù hợp.

## 8. Short-circuit không tự giải phóng directory handles

Một trong các sai lầm phổ biến nhất:

~~~java
// SAI: findFirst trả kết quả nhưng không tự close nguồn.
Path first = Files.list(directory)
        .findFirst()
        .orElse(null);
~~~

Tương tự:

~~~java
// SAI: limit(3) giới hạn phần tử, không đóng Stream.
Files.walk(root)
        .limit(3)
        .forEach(System.out::println);
~~~

Hoặc:

~~~java
// SAI: count duyệt kết quả, không tự close Stream.
long amount = Files.find(root, 3, matcher).count();
~~~

Ở mảnh cuối, `matcher` là BiPredicate phù hợp được chương trình định nghĩa.

Sửa thành:

~~~java
try (Stream<Path> entries = Files.list(directory)) {
    Path first = entries.findFirst().orElse(null);
    // Xử lý first trong thời gian tài nguyên đang mở.
}
~~~

~~~java
try (Stream<Path> tree = Files.walk(root)) {
    tree.limit(3).forEach(System.out::println);
}
~~~

~~~java
try (Stream<Path> matches = Files.find(
        root, 3, (p, a) -> a.isRegularFile())) {
    long amount = matches.count();
    System.out.println(amount);
}
~~~

`findFirst()`, `anyMatch()`, `limit()` có thể ngừng lấy phần tử sớm. `count()` thường cần duyệt dữ liệu để đếm, còn `toList()` giữ các phần tử đã thu thập. Nhưng **dù đọc không phần tử, đọc vài phần tử hay duyệt đến hết, trách nhiệm đóng Stream vẫn y nguyên**.

**TWR với stream nguồn đóng tài nguyên ở cuối block**, kể cả khi một terminal operation đã kết thúc sớm.

## 9. Ví dụ runnable: terminal operation xong nhưng chưa close

Tạo file **ShortCircuitCloseDemo.java**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class ShortCircuitCloseDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("stream-close-");
        Path file = root.resolve("one.txt");

        try {
            Files.writeString(file, "data", StandardCharsets.UTF_8);

            AtomicBoolean closeHandlerCalled = new AtomicBoolean();

            try (Stream<Path> entries = Files.list(root)
                    .onClose(() -> closeHandlerCalled.set(true))) {

                boolean found = entries.anyMatch(path ->
                        path.getFileName().toString().equals("one.txt"));

                System.out.println("found=" + found);
                System.out.println("closedInsideTry="
                        + closeHandlerCalled.get());
            }

            System.out.println("closedAfterTry="
                    + closeHandlerCalled.get());
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(root);
        }
    }
}
~~~

Chạy với Java 17+:

~~~bash
javac -encoding UTF-8 ShortCircuitCloseDemo.java
java ShortCircuitCloseDemo
~~~

Stdout:

~~~text
found=true
closedInsideTry=false
closedAfterTry=true
~~~

Giải thích:

1. `Files.list(root)` tạo Stream từ một thư mục có `one.txt`.
2. `onClose(...)` gắn **handler sẽ chạy khi close Stream**, trong demo chỉ đổi một cờ.
3. `anyMatch(...)` tìm thấy file, có thể dừng sớm. Sau khi terminal operation kết thúc, cờ vẫn **false**: terminal operation chưa gọi close.
4. Rời try-with-resources thì `close()` được gọi và handler chạy, cờ trở thành **true**.
5. Demo cho thấy thời điểm **handler close** chạy, không phải phép đo trực tiếp file descriptor của hệ điều hành. Việc Stream từ Files.list đóng tài nguyên directory là hợp đồng của Files.

Từ đây có thể suy ra: **không chỉ Stream chưa đọc hết mới cần close; Stream đã xử lý xong một terminal operation cũng vẫn phải close**.

## 10. Lỗi I/O khi mở khác lỗi khi đang duyệt

Những phương thức này công bố `throws IOException` khi **khởi tạo nguồn** không thành công, chẳng hạn:

~~~java
Stream<Path> entries = Files.list(directory);
~~~

Nếu directory không tồn tại, không phải directory, hoặc không thể mở, lệnh trên có thể ném `IOException` hoặc subclass cụ thể như `NoSuchFileException` hay `NotDirectoryException` khi provider hỗ trợ.

Nhưng vì Stream được duyệt **lười**, I/O có thể xảy ra **sau khi Files.list/walk/find đã trả về**. Khi lỗi I/O phát sinh lúc tiếp tục đọc thư mục, tài liệu Java SE 17 nêu rằng nó được **bọc vào `UncheckedIOException`** và phát sinh từ terminal/intermediate traversal đã kích hoạt truy cập đó.

~~~text
Gọi Files.list/walk/find:
    thất bại khi mở       -> IOException (checked)

Tiêu thụ Stream<Path>:
    thất bại khi duyệt     -> UncheckedIOException
                              cause là IOException gốc
~~~

Mẫu chẩn đoán:

~~~java
// Mảnh code: import java.io.IOException, java.io.UncheckedIOException
// và các lớp Path, Files, Stream.
try (Stream<Path> paths = Files.walk(root)) {
    paths.filter(Files::isRegularFile)
         .forEach(System.out::println);
} catch (java.io.UncheckedIOException traversalFailure) {
    System.err.println("Traverse failed: "
            + traversalFailure.getCause());
} catch (IOException openingFailure) {
    System.err.println("Open failed: " + openingFailure);
}
~~~

Một số chú ý:

- `UncheckedIOException.getCause()` truy cập IOException gốc để ghi log/chẩn đoán.
- Nếu chương trình **không có khả năng phục hồi**, thường nên propagate lỗi sau khi bổ sung ngữ cảnh; đừng tự coi kết quả duyệt bị cắt ngắn là thành công.
- Exception từ **lambda người dùng** (ví dụ parse, network call, hoặc runtime exception trong matcher) là một nhóm lỗi riêng; không phải mọi lỗi pipeline đều do Files API.
- Khi body ném exception và cleanup cũng phát sinh lỗi, cơ chế **primary/suppressed exceptions** của TWR cần được xét; không nuốt mất lỗi ban đầu.
- `Files.list` và `Files.walk` có tính **weakly consistent**: không đóng băng thư mục/cây trong khi duyệt. Nếu đối tượng bị thêm/xóa/đổi tên trong lúc quét, kết quả có thể phản ánh hoặc không phản ánh thay đổi. Điều này cũng có thể gây lỗi I/O.

Đừng kết hợp `Files.exists(root)` trước rồi mặc định Files.walk luôn thành công. Kiểm tra và dùng là hai thời điểm khác nhau; tài nguyên có thể biến mất hoặc quyền truy cập thay đổi. Xử lý ngay tại thao tác thực tế.

## 11. Trả Stream ra khỏi phương thức: ai chịu trách nhiệm đóng?

Với một phương thức dịch vụ, có hai cách thiết kế rõ ràng.

### Cách A — xử lý trong phương thức và trả kết quả đã hoàn thành

~~~java
// Mảnh code, có imports thích hợp.
static long countDirectEntries(Path dir) throws IOException {
    try (Stream<Path> paths = Files.list(dir)) {
        return paths.count();
    }
}
~~~

Ưu điểm: phương thức **tự sở hữu và tự đóng** Stream. Caller nhận `long`, không phải quản lý directory handle. Điều này thường dễ dùng và khó bị leak hơn.

### Cách B — trả Stream sống và chuyển trách nhiệm close cho caller

~~~java
static Stream<Path> openTextFiles(Path root)
        throws IOException {
    return Files.walk(root)
            .filter(p -> Files.isRegularFile(p)
                    && p.getFileName().toString().endsWith(".txt"));
}
~~~

Caller phải đóng:

~~~java
try (Stream<Path> textFiles = openTextFiles(root)) {
    textFiles.forEach(System.out::println);
}
~~~

Cách B phù hợp nếu API thật sự muốn trả kết quả lazy. Nhưng **ownership đã chuyển cho caller**; tên/hợp đồng API nên nói rõ kết quả là Stream giữ directory resources cần close.

**Mẫu sai:**

~~~java
// SAI: đóng nguồn trong phương thức trước khi caller tiêu thụ.
static Stream<Path> broken(Path root) throws IOException {
    try (Stream<Path> paths = Files.walk(root)) {
        return paths.filter(Files::isRegularFile);
    }
}
~~~

TWR chạy close **trước khi trả kết quả cho caller**. Stream trả về đã gắn với pipeline bị đóng, nên không thể dùng nó như một nguồn duyệt hợp lệ.

Có thể dùng `Stream.onClose(...)` cho cleanup tùy chỉnh khi xây API, nhưng cần hiểu *tài nguyên nào được sở hữu*. Với Files.list/walk/find, đóng đúng Stream được trả về là đủ; không tự tạo thêm close handler giả lập nếu chỉ để thay thế cơ chế vốn đã có.

## 12. Stream dùng một lần và xử lý trung gian

Một Stream chỉ dùng cho **một pipeline tiêu thụ**. Sau:

~~~java
try (Stream<Path> paths = Files.list(directory)) {
    long n = paths.count();
    // paths.findFirst(); // Không hợp lệ: Stream đã được tiêu thụ.
}
~~~

Không gọi tiếp `findFirst()`, `forEach()` hoặc terminal operation khác trên cùng Stream. Một số thao tác có thể ném `IllegalStateException` vì stream đã vận hành hoặc đóng.

Nếu cần hai thống kê khác nhau, có thể thiết kế **một collector** tổng hợp trong một lượt duyệt, hoặc **mở Stream mới** cho lượt thứ hai; mỗi Stream phải có TWR của riêng nó.

Khi dùng derived pipeline:

~~~java
try (Stream<Path> result = Files.walk(root)
        .filter(Files::isRegularFile)
        .limit(10)) {
    result.forEach(System.out::println);
}
~~~

Đóng derived pipeline cũng đóng tài nguyên liên quan đến nguồn duyệt của pipeline. Không cần giữ ba biến Stream rồi cố đóng mỗi biến nhiều lần.

**Không đồng nhất lazy với tiết kiệm bộ nhớ ở mọi trường hợp.** `sorted()` và `toList()` có thể cần giữ rất nhiều `Path` trong bộ nhớ; các phép toán như `count()` hoặc `anyMatch()` thường chỉ giữ ít trạng thái. Nếu thư mục rất lớn, cân nhắc xử lý từng Path thay vì collect toàn bộ rồi mới thao tác.

## 13. Symbolic link, vòng lặp và tùy chọn FOLLOW_LINKS

Mặc định, `Files.walk` và `Files.find` **không tự theo các symbolic link tới directory con** theo cách duyệt đệ quy như một thư mục thực. Nếu muốn theo symlink, có thể chọn `FileVisitOption.FOLLOW_LINKS`:

~~~java
try (Stream<Path> paths = Files.walk(
        root, 10, java.nio.file.FileVisitOption.FOLLOW_LINKS)) {
    paths.forEach(System.out::println);
}
~~~

Tuy nhiên:

- Theo link có thể dẫn cây duyệt ra ngoài vị trí mà ứng dụng ban đầu tưởng là root.
- Một symlink có thể quay lại thư mục tổ tiên, tạo **chu trình**. API duyệt file tree có quy tắc phát hiện loop và có thể phát sinh lỗi như `FileSystemLoopException` dưới dạng lỗi I/O trong quá trình duyệt.
- File system có thể thay đổi trong quá trình duyệt, nên các kết quả/thuộc tính không phải snapshot bất biến.
- Chọn có theo link hay không phải dựa trên nghiệp vụ và yêu cầu an toàn, không bật FOLLOW_LINKS chỉ để “tìm được nhiều file hơn”.

**Không dùng `normalize()` hoặc một lần kiểm tra `isDirectory()` để giả định traversal không chịu tác động từ symbolic link hay race.** Với đường dẫn không đáng tin, cần áp dụng chính sách quản lý file system phù hợp.

## 14. Vì sao filter không tự cắt nhánh duyệt cây?

Đoạn:

~~~java
try (Stream<Path> paths = Files.walk(root)) {
    paths.filter(p -> !p.getFileName().toString()
                    .equals("node_modules"))
         .forEach(System.out::println);
}
~~~

Chỉ có nghĩa: **không đưa một số Path mang tên node_modules vào đầu ra**. Nó **không đảm bảo bỏ qua việc đi sâu vào bên trong thư mục node_modules**. `Files.walk` vẫn có thể tiếp tục thăm các mục con để tạo phần tử đầu vào cho `filter`.

Nếu cần **prune subtree** thật sự, dùng visitor:

~~~java
// Mảnh code: import Files, Path, FileVisitResult,
// SimpleFileVisitor, BasicFileAttributes và IOException.
Files.walkFileTree(root,
        new java.nio.file.SimpleFileVisitor<Path>() {
    @Override
    public java.nio.file.FileVisitResult preVisitDirectory(
            Path dir,
            java.nio.file.attribute.BasicFileAttributes attrs) {
        if (dir.getFileName() != null
                && dir.getFileName().toString()
                        .equals("node_modules")) {
            return java.nio.file.FileVisitResult.SKIP_SUBTREE;
        }
        return java.nio.file.FileVisitResult.CONTINUE;
    }
});
~~~

Đây là **mảnh code**, không phải class chạy độc lập. `Files.walkFileTree` dùng visitor/callback để kiểm soát traversal (bao gồm SKIP_SUBTREE) thay vì trả Stream<Path> cần đóng; visitor vẫn phải xử lý IOException và các callback lỗi theo hợp đồng.

Với tác vụ duyệt cây yêu cầu **xóa đệ quy, bỏ qua nhánh, phân loại lỗi từng nhánh hoặc xử lý trạng thái có cấu trúc**, `walkFileTree` thường phù hợp hơn `walk(...).filter(...)`.

## 15. Files.newDirectoryStream và DirectoryStream cũng phải đóng

Bên cạnh Stream API, Java có:

~~~java
try (java.nio.file.DirectoryStream<Path> entries =
        Files.newDirectoryStream(directory)) {
    for (Path p : entries) {
        System.out.println(p);
    }
}
~~~

`DirectoryStream<Path>` là **một API khác** với `Stream<Path>`, nhưng cũng mở directory và cần đóng sau khi duyệt. Nó hỗ trợ iterator và có thể dùng glob filter khi mở:

~~~java
try (java.nio.file.DirectoryStream<Path> textEntries =
        Files.newDirectoryStream(directory, "*.txt")) {
    for (Path p : textEntries) {
        System.out.println(p.getFileName());
    }
}
~~~

DirectoryStream có quy tắc lỗi và vòng đời riêng; đừng nhầm thao tác `iterator.hasNext()` với terminal operation của Java Stream. Việc dùng for-each **không tự đóng** DirectoryStream; TWR ở ví dụ là nơi đóng nó.

Nếu rất cần API **`Stream<Path>`**, dùng list/walk/find là lựa chọn tự nhiên. Nếu cần kiểm soát iterator và filter theo tên cho một directory, newDirectoryStream cũng đáng cân nhắc.

## 16. Chọn công cụ theo mục tiêu

| Nhu cầu | API thường phù hợp | Cách quản lý tài nguyên |
| --- | --- | --- |
| Liệt kê ngay các mục của một thư mục | `Files.list(dir)` | TWR trên Stream |
| Đếm mục trực tiếp | `Files.list(dir).count()` trong TWR | Đóng dù count xong |
| Duyệt cả cây | `Files.walk(root)` | TWR trên Stream |
| Duyệt cây đến độ sâu cố định | `Files.walk(root, maxDepth)` | TWR trên Stream |
| Tìm các mục theo metadata | `Files.find(root, depth, predicate)` | TWR trên Stream |
| Dừng sớm sau vài Path | `limit(n)`, `findFirst()` trong pipeline | **Vẫn đóng** dù short-circuit |
| Bỏ qua nguyên subtree | `Files.walkFileTree` + `SKIP_SUBTREE` | Kiểm soát callback và lỗi duyệt |
| Duyệt một thư mục bằng for-each | `Files.newDirectoryStream(dir)` | TWR trên DirectoryStream |
| Cần kết quả dùng sau khi đóng nguồn | Tính toán/collect trong TWR | Trả List/Map/giá trị, không trả Stream đã đóng |
| Cần trả kết quả lazy ra ngoài | Trả Stream còn mở có contract ownership | **Caller phải đóng** |

Nếu thư mục liên tục thay đổi, việc xử lý kết quả cũng phải chấp nhận trạng thái **weakly consistent** hoặc bổ sung chính sách snapshot/đồng bộ. **Close giải quyết vòng đời tài nguyên, không làm phép duyệt trở thành snapshot hay transaction.**

## 17. Các lỗi thường gặp

| Sai lầm | Vì sao nguy hiểm | Cách sửa |
| --- | --- | --- |
| `Files.list(dir).forEach(...)` rồi bỏ Stream | Không đóng directory resource đúng hạn | TWR quanh Stream |
| `Files.walk(root).count()` không close | Terminal operation không tự close | TWR độc lập với count |
| `Files.find(...).findFirst()` không close | Short-circuit vẫn giữ nguồn | Close Stream sau khi lấy kết quả |
| Tưởng Files.list duyệt đệ quy | Bỏ sót file bên dưới directory con | Dùng walk hoặc find |
| Tưởng Files.walk không trả chính root | Đếm hoặc xử lý sai một phần tử | Biết root là phần tử đầu của traversal |
| Dùng filter bỏ directory rồi tưởng bỏ cả subtree | Vẫn đi sâu vào nhánh | walkFileTree + SKIP_SUBTREE |
| Chỉ bắt IOException lúc gọi Files.walk | Bỏ lọt lỗi khi tiêu thụ dưới dạng UncheckedIOException | Xử lý cả hai giai đoạn |
| Trả Stream từ try-with-resources đã kết thúc | Caller nhận Stream đóng | Trả kết quả hoàn chỉnh hoặc chuyển ownership |
| Gọi count rồi findFirst trên cùng Stream | Stream đã tiêu thụ | Tạo Stream mới hoặc gom trong một lượt |
| Giả định thứ tự list ổn định | Kết quả phụ thuộc provider và directory | Sort khi cần; cân nhắc RAM |
| Bật FOLLOW_LINKS tùy tiện | Có thể đi ra ngoài cây hoặc gặp loop | Xác định chính sách link, giới hạn depth |
| Dùng sorted().toList() trên cây cực lớn | Giữ nhiều Path trong bộ nhớ | Xử lý streaming hoặc duyệt có kiểm soát |
| Không đóng DirectoryStream khi for-each | Duyệt xong chưa phải close | TWR trên DirectoryStream |
| Sửa/xóa trong khi vẫn dựa vào Stream traversal mở | Có thể không nhất quán hoặc gặp I/O lỗi | Thiết kế thao tác riêng, xử lý lỗi |
| Không cleanup khi terminal operation ném | Stream còn tài nguyên mở | TWR bao cả pipeline |

## 18. Câu hỏi tự kiểm tra

**Câu 1.** Ba API `Files.list`, `Files.walk` và `Files.find` có phải trả dữ liệu hoàn toàn trong bộ nhớ nên không cần đóng?  
**Đáp án:** Không. Stream của chúng có thể giữ directory resources đang mở. Cần close sau khi dùng, tốt nhất bằng TWR.

**Câu 2.** `Files.list(dir)` có trả các file trong thư mục con của dir không?  
**Đáp án:** Không. Nó chỉ liệt kê mục trực tiếp; dùng `walk` hoặc `find` nếu cần đệ quy.

**Câu 3.** `Files.walk(root, 0)` có trả Path nào không?  
**Đáp án:** Có. Nó bao gồm chính root nếu mở/truy cập start thành công; depth 0 chỉ xét start.

**Câu 4.** Vì sao `Files.find(root, 3, matcher)` có thể thích hợp hơn `Files.walk(root, 3).filter(...)` khi cần nhiều metadata?  
**Đáp án:** Matcher của find nhận sẵn BasicFileAttributes; có thể tránh lấy thuộc tính lặp lại. Nó vẫn cần duyệt cây để tìm phần tử.

**Câu 5.** Nếu `findFirst()` hoặc `limit(1)` chỉ dùng một phần tử, có cần close Stream không?  
**Đáp án:** Có. Việc dừng sớm của terminal/pipeline không đóng tài nguyên.

**Câu 6.** Lỗi khi mở thư mục và lỗi khi đang duyệt có thể khác kiểu exception như thế nào?  
**Đáp án:** Lỗi mở có thể là checked IOException; lỗi I/O xảy ra trong traversal sau khi API đã trả Stream được bọc thành UncheckedIOException.

**Câu 7.** Tại sao không nên return Stream từ khối try-with-resources trong factory?  
**Đáp án:** TWR đóng Stream trước khi caller nhận được, khiến pipeline trả về không thể sử dụng như một nguồn còn sống.

**Câu 8.** `Files.walk(root).filter(p -> !name.equals("cache"))` có tự bỏ qua toàn bộ nội dung thư mục cache?  
**Đáp án:** Không. Filter chỉ lọc phần tử ra khỏi đầu ra; dùng walkFileTree và SKIP_SUBTREE nếu cần cắt nhánh.

**Câu 9.** Nếu cần kết quả ổn định theo thứ tự alphabet trong một thư mục, có thể giả định Files.list sắp xếp sẵn không?  
**Đáp án:** Không. Dùng sorted() hoặc comparator phù hợp và hiểu chi phí giữ phần tử.

**Câu 10.** Nếu Stream đã được tiêu thụ hết bởi `forEach`, gọi `close()` còn cần thiết không?  
**Đáp án:** Có. Terminal operation hoàn tất khác với đóng nguồn I/O; close giải phóng resources theo hợp đồng.

## 19. Tài liệu Java SE 17

- [Files.list(Path)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#list(java.nio.file.Path)): duyệt trực tiếp directory và quy định đóng Stream.
- [Files.walk(Path, int, FileVisitOption...)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#walk(java.nio.file.Path,int,java.nio.file.FileVisitOption...)): duyệt depth-first, maxDepth, tài nguyên thư mục mở.
- [Files.find(Path, int, BiPredicate, FileVisitOption...)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#find(java.nio.file.Path,int,java.util.function.BiPredicate,java.nio.file.FileVisitOption...)): matcher với BasicFileAttributes và vòng đời Stream.
- [BaseStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/BaseStream.html): close(), onClose() và tính chất một lần dùng.
- [Files.walkFileTree](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#walkFileTree(java.nio.file.Path,java.nio.file.FileVisitor)): visitor để điều khiển duyệt/cắt nhánh.
- [DirectoryStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/DirectoryStream.html): iterator duyệt directory và quản lý close.

**Tổng kết:** **`list`** duyệt một cấp, **`walk`** duyệt cây, còn **`find`** duyệt cây và chọn path theo predicate có metadata. Cả ba đều trả Stream<Path> gắn với thư mục mở, nên **bắt buộc quản lý vòng đời bằng try-with-resources hoặc cơ chế tương đương**. Lazy traversal, terminal operation hoàn tất, short-circuit, `count`, `toList` hay kết quả đã tìm thấy **đều không tự làm tài nguyên được đóng**.


</details>

- [Quay lại đầu trang](#back-to-top)
