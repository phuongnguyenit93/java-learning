<a id="back-to-top"></a>

# Path

## Menu
- [Tạo Path bằng Path.of, Paths.get và FileSystem](#path-001)
- [Root, parent, file name và các thành phần đường dẫn](#path-002)
- [resolve, resolveSibling và ghép đường dẫn](#path-003)
- [relativize và điều kiện tương thích](#path-004)
- [normalize chỉ xử lý cú pháp đường dẫn](#path-005)
- [toAbsolutePath khác toRealPath](#path-006)
- [startsWith, endsWith, equals và so sánh theo thành phần](#path-007)
- [URI, toUri và giới hạn chuyển đổi giữa các provider](#path-008)
- [Đường dẫn Windows, Unix, dấu phân cách và tính phân biệt hoa thường](#path-009)
- [Path không bảo đảm đích đang tồn tại](#path-010)
- [PathMatcher với glob và regex](#path-011)

## <a id="path-001">Tạo Path bằng Path.of, Paths.get và FileSystem</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo Path bằng Path.of, Paths.get và FileSystem trong Java Core I/O

> **Ngữ cảnh:** Java Core → I/O → **Path**. Bài học phân biệt ba cách tạo đối tượng `java.nio.file.Path`: `Path.of(...)`, `Paths.get(...)` và `FileSystem.getPath(...)`; giải thích default file system, file system provider, xử lý URI, đường dẫn trong ZIP, thao tác path an toàn và lựa chọn API theo phiên bản JDK. Các ví dụ hoàn chỉnh biên dịch/chạy bằng Java 17, trừ khi ghi rõ.

## 1. Path là gì? Tạo Path không đồng nghĩa tạo tệp

`Path` trong package `java.nio.file` đại diện **vị trí định danh của một đường dẫn trong một file system cụ thể**. Nó mô tả cấu trúc tên file/thư mục mà **file system provider** hiểu: tên thành phần, gốc, đường dẫn tương đối hay tuyệt đối, cách nối/chuẩn hóa và cách chuyển đổi.

Ví dụ:

~~~java
import java.nio.file.Path;

Path report = Path.of("reports", "2026", "summary.csv");
~~~

Đây là **fragment** đặt trong một method. Nó tạo một đối tượng `Path` tương đối trỏ tới vị trí logic `reports/2026/summary.csv` trên hệ thống dùng dấu phân tách `/`; trên Windows cách thể hiện khi gọi `toString()` có thể khác. **Lệnh trên không tạo directory hay file, không kiểm tra tệp tồn tại và không mở handle I/O**.

Để tương tác filesystem thực tế, mới dùng `java.nio.file.Files` như:

~~~java
// Fragment: path là Path hợp lệ; method xử lý IOException.
boolean exists = Files.exists(path);
Files.createDirectories(path.getParent());
Files.writeString(path, "Hello", StandardCharsets.UTF_8);
~~~

Fragment cần import `java.nio.file.Files` và `java.nio.charset.StandardCharsets`. **Không đánh đồng** việc tạo `Path` (thuần biểu diễn) với tạo file `Files.createFile(...)`, tạo thư mục `Files.createDirectories(...)` hoặc mở stream.

### FileSystem có vai trò gì?

Một `FileSystem` là môi trường diễn giải các `Path`. File system **mặc định** thường phản ánh hệ thống file mà JVM được chạy trên đó; một provider khác có thể đại diện cho file ZIP/JAR, filesystem ảo hoặc lưu trữ ngoài (nếu có provider thích hợp).

~~~text
String / URI
    |
    +--> Path.of("...", "...") -----------+
    |                                     |
    +--> Paths.get("...", "...") ---------+--> default FileSystem --> Path
    |                                     |
    +--> FileSystems.getDefault()
              .getPath("...", "...") -----+

    +--> FileSystem khác (ví dụ ZIP)
              .getPath("/docs/file.txt") ----> Path thuộc ZIP FileSystem

Path + Files API --> thao tác thật lên filesystem tương ứng
~~~

`Path` không tự chứa toàn bộ dữ liệu tệp; `Files` chuyển các thao tác file cho provider của path. Vì thế cùng cú pháp `getPath` nhưng đường dẫn trên disk và đường dẫn **bên trong ZIP** có thể đại diện cho hai không gian khác nhau.

## 2. Ba API tạo Path và mối quan hệ giữa chúng

| API | Từ JDK | Môi trường file system | Khi nào chọn? |
| --- | --- | --- | --- |
| `Path.of(String first, String... more)` | **Java 11** | Default FileSystem | Lựa chọn ngắn gọn cho ứng dụng Java 11+ sử dụng filesystem mặc định. |
| `Paths.get(String first, String... more)` | **Java 7** | Default FileSystem | Tương thích source với Java 7/8 và codebase cũ; vẫn hợp lệ trên JDK mới. |
| `FileSystems.getDefault().getPath(String first, String... more)` | **Java 7** | Default FileSystem được chỉ định rõ | Khi muốn thể hiện rõ provider mặc định hoặc đã có biến FileSystem. |
| `someFileSystem.getPath(String first, String... more)` | **Java 7** | FileSystem chính là `someFileSystem` | Khi xây dựng path **trong ZIP** hoặc provider tùy biến, không giả định filesystem mặc định. |
| `Path.of(URI uri)` | **Java 11** | Provider được tìm qua **scheme** của URI | Chuyển URI phù hợp (ví dụ `file:`) thành Path khi provider tồn tại. |
| `Paths.get(URI uri)` | **Java 7** | Provider theo URI | API lâu đời tương đương mục đích trên, hữu ích khi yêu cầu Java 8. |

Điểm quan trọng: `Path.of(String, String...)` và `Paths.get(String, String...)` trên Java 11+ tạo `Path` bằng **default FileSystem**; triển khai hiện đại của `Paths.get` ủy quyền cho `Path.of`. Không có cơ chế “nhanh hơn” đáng để chọn chỉ vì tên. Chọn theo **minimum JDK**, tính rõ ràng và khả năng tái sử dụng với filesystem khác.

### 2.1. `Path.of("...")`

~~~java
Path file = Path.of("data.txt");
Path nested = Path.of("input", "archive", "2026.json");
Path absolute = Path.of(System.getProperty("user.home"), "notes.txt");
~~~

Đây là fragment. `Path.of(first, more...)` yêu cầu `first` và tùy ý những chuỗi `more`; các thành phần không rỗng được ghép theo **quy tắc của default provider**. Trên Unix thường dùng dấu `/`; Windows có cú pháp drive, UNC và dấu phân tách riêng. API không yêu cầu bạn nối chuỗi thủ công bằng `"/"` hay `"\\"`.

**Một đường dẫn tuyệt đối chỉ vì có chuỗi dài chưa chắc hợp lệ trên mọi OS.** Ví dụ `Path.of("/tmp", "file.txt")` mang ngữ nghĩa gốc Unix nhưng trên Windows có thể diễn giải khác. Code cross-platform nên nhận gốc từ cấu hình, `Path` do caller truyền, hoặc API môi trường, sau đó dùng `resolve`.

### 2.2. `Paths.get("...")`

~~~java
import java.nio.file.Path;
import java.nio.file.Paths;

Path a = Paths.get("docs", "readme.md");
Path b = Path.of("docs", "readme.md"); // Chỉ có từ Java 11.
System.out.println(a.equals(b));     // true với default provider.
~~~

Nếu project phải chạy/biên dịch với Java 8, chỉ dùng `Paths.get` và không viết `Path.of`. Ngược lại, ứng dụng Java 17 có thể dùng cả hai; `Paths.get` **không bị loại bỏ** khỏi Java SE.

### 2.3. `FileSystem.getPath("...")`

~~~java
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

FileSystem defaultFs = FileSystems.getDefault();
Path document = defaultFs.getPath("docs", "readme.md");
~~~

Cũng là fragment. Tác dụng quan trọng là **nguồn FileSystem có thể khác default**:

~~~java
// Fragment: zipFs là FileSystem đã mở từ ZIP provider.
Path member = zipFs.getPath("/docs", "readme.md");
~~~

Path `member` đại diện thành phần bên trong ZIP, **không phải** file `/docs/readme.md` trên ổ đĩa máy chủ. Những API như `Files.exists(member)`, `Files.readString(member)` sẽ dùng provider của ZIP filesystem đó.

**Tránh sai lầm phổ biến:** khi nhận một `Path root` do caller cung cấp, nhưng method lại gọi `Path.of("child.txt")` rồi giả định nó nằm trên cùng filesystem với `root`. `Path.of` dùng default, trong khi `root` có thể thuộc ZIP hoặc một provider khác. Nên dùng `root.resolve("child.txt")` hoặc `root.getFileSystem().getPath(...)` tùy ý nghĩa mong muốn.

## 3. Ví dụ độc lập: đối chiếu ba factory trên default file system

Tạo file **`PathFactoriesDemo.java`**:

~~~java
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PathFactoriesDemo {
    public static void main(String[] args) {
        Path modern = Path.of("docs", "guide.md");
        Path legacy = Paths.get("docs", "guide.md");
        Path fromFs = FileSystems.getDefault()
                .getPath("docs", "guide.md");

        Path messy = Path.of("docs", ".", "draft", "..", "guide.md");

        System.out.println("equivalent="
                + (modern.equals(legacy) && modern.equals(fromFs)));
        System.out.println("relative=" + !modern.isAbsolute());
        System.out.println("names=" + modern.getNameCount());
        System.out.println("filename=" + modern.getFileName());
        System.out.println("parent=" + modern.getParent().getFileName());
        System.out.println("normalized=" + messy.normalize().equals(modern));
        System.out.println("uriRoundTrip="
                + Path.of(modern.toAbsolutePath().toUri())
                    .equals(modern.toAbsolutePath()));
    }
}
~~~

Chạy:

~~~bash
javac --release 17 PathFactoriesDemo.java
java PathFactoriesDemo
~~~

**Stdout đã kiểm tra:**

~~~text
equivalent=true
relative=true
names=2
filename=guide.md
parent=docs
normalized=true
uriRoundTrip=true
~~~

Vì ví dụ chỉ in dữ kiện cấu trúc và so sánh boolean, không in `Path.toString()` chứa separator nền tảng, stdout được thiết kế ổn định giữa các hệ thống tệp mặc định thông thường. Đây là minh họa với đường dẫn đơn giản hợp lệ; kết quả có thể khác trong provider mặc định bất thường có quy tắc tên khác.

Giải thích:

1. Ba lời gọi cùng nhận tên `docs` và `guide.md` trên default FileSystem nên Path tương ứng bằng nhau.
2. `isAbsolute()` trả `false` với đường dẫn không có root.
3. `getNameCount()` đếm số thành phần, **không** đếm số byte, thư mục tồn tại hoặc slash. `getFileName()` lấy thành phần cuối.
4. `normalize()` loại `.` và xử lý cặp tên + `..` **về mặt từ vựng**. Nó không xác minh đường dẫn có thật.
5. `toAbsolutePath().toUri()` chuyển sang file URI; `Path.of(uri)` dựng lại Path theo provider phù hợp. Round-trip với default provider có bảo đảm theo Javadoc của `Path` trong cùng JVM theo các điều kiện nêu ở đó.

## 4. Đường dẫn tương đối và tuyệt đối

Một `Path` có thể **relative** hoặc **absolute**:

- `Path.of("data", "users.csv")`: relative; được diễn giải trong ngữ cảnh default directory khi dùng cho I/O.
- `Path.of("data", "users.csv").toAbsolutePath()`: tạo một đường dẫn tuyệt đối tương ứng theo provider, thường dựa vào thư mục làm việc của tiến trình.
- `Path.of("data", "users.csv").toRealPath()`: cố truy vấn filesystem thực để xác định real path; cần tệp tồn tại và có quyền truy cập. Mặc định nó có thể giải quyết symbolic links; tùy chọn `LinkOption.NOFOLLOW_LINKS` thay đổi cách xử lý liên kết.
- `Path.of(".").toAbsolutePath().normalize()`: thường dùng để xem thư mục làm việc dưới dạng absolute path đã đơn giản hóa về mặt từ vựng.

Không hard-code một đường dẫn tuyệt đối như `C:\data\...` hay `/home/worker/...` cho mọi OS. Giá trị `user.dir` là thuộc tính JVM thường phản ánh working directory lúc launch; đừng viết chương trình dựa vào khả năng đổi `System.setProperty("user.dir", "...")` để điều khiển mọi thao tác relative path tại runtime.

### 4.1. `toAbsolutePath()` không phải `toRealPath()`

| API | Có truy vấn tệp tồn tại? | Có loại `.` / `..`? | Giải quyết symbolic links? |
| --- | --- | --- | --- |
| `toAbsolutePath()` | Không phải kiểm tra tồn tại | Không nhất thiết loại mọi thành phần thừa | Không thực hiện symlink resolution |
| `normalize()` | Không | Có, **lexical** theo quy tắc Path | Không |
| `toAbsolutePath().normalize()` | Không | Có | Không |
| `toRealPath(...)` | Có thể thực hiện filesystem I/O và đòi đường dẫn tồn tại | Trả real/absolute path theo hợp đồng provider | Theo mặc định có thể resolve symlink; `NOFOLLOW_LINKS` đổi hành vi |

**Cảnh báo:** `normalize()` đôi khi **thay đổi đối tượng mà đường dẫn thực chỉ tới** nếu đường dẫn có symbolic links. Ví dụ một tên nằm trước `..` lại là symlink tới nơi khác; bỏ cặp từ vựng chưa chắc tương đương đi thực tế qua symlink. Không sử dụng `normalize()` như bằng chứng hoàn chỉnh rằng dữ liệu từ người dùng chỉ nằm trong thư mục được cho phép.

### 4.2. `resolve`, `resolveSibling`, `relativize`

~~~java
// Fragment: base thuộc một FileSystem cụ thể.
Path base = Path.of("app", "data");
Path child = base.resolve("users.csv");
Path sibling = child.resolveSibling("roles.csv");
Path relative = base.relativize(child);
~~~

Khi xuất text trên Unix, chúng thường được thể hiện như `app/data/users.csv`, `app/data/roles.csv` và `users.csv`. Để portable, nên so sánh thành phần hoặc Path trực tiếp, không so sánh string có dấu slash cố định.

- `base.resolve(other)` tạo path bằng cách nối `other` dưới `base` nếu `other` là đường dẫn tương đối; nếu `other` là đường dẫn tuyệt đối, kết quả phụ thuộc quy tắc `resolve` của provider (default provider thường trả lại `other`).
- `resolveSibling(name)` thay thành phần cuối bằng tên mới trong cùng thư mục cha.
- `relativize(other)` tạo đường đi tương đối từ path này đến path khác; thường cần cùng provider/FileSystem và cùng tính absolute/relative. Đừng trộn `absolute` và `relative` tùy tiện.
- Đường dẫn có dấu `..` cần tính lại và kiểm tra ngữ nghĩa, đặc biệt với input không tin cậy.

## 5. FileSystem mặc định và FileSystem bất kỳ

Lấy filesystem mặc định:

~~~java
FileSystem fs = FileSystems.getDefault();
Path data = fs.getPath("data", "input.txt");

System.out.println(data.getFileSystem() == fs); // true
~~~

Đây là fragment. `getDefault()` có thể được cấu hình thông qua default provider đặc biệt khi JVM khởi động, nhưng với phần lớn ứng dụng desktop/server, nó là hệ thống file của OS. Cách nó diễn giải absolute path, separators, case sensitivity và tên không hợp lệ là thuộc filesystem/provider cụ thể.

Một `FileSystem` còn cung cấp:

| API | Công dụng |
| --- | --- |
| `getPath(first,more)` | Tạo path thuộc chính filesystem đó. |
| `getRootDirectories()` | Liệt kê các root mà provider biết, không nhất thiết giống ổ `C:` trên mọi OS. |
| `getSeparator()` | Dấu phân tách tên theo provider; không nên hard-code slash để giả lập file system. |
| `getFileStores()` | Các file store đang được provider trình bày, thông tin có thể thay đổi theo thời gian. |
| `getPathMatcher("glob:...")` | Tạo bộ so mẫu tên path theo glob/regex hỗ trợ bởi provider. |
| `isOpen()` | Kiểm tra trạng thái filesystem có thể đóng; mặc định thường được quản lý bởi JVM. |
| `close()` | Giải phóng tài nguyên filesystem **mở riêng**; không tự ý đóng filesystem mặc định. |

**Vòng đời:** `FileSystems.getDefault()` do JVM quản lý; JavaDocs nêu default filesystem không thể đóng, gọi `close()` với nó gây `UnsupportedOperationException`. Ngược lại, `FileSystems.newFileSystem(...)` tạo filesystem riêng như ZIPFS và thường phải được đóng bằng try-with-resources để flush/giải phóng handle. Sau khi close ZIPFS, những `Path` thuộc filesystem đó không còn có thể dùng cho Files I/O như lúc còn mở.

## 6. Đường dẫn từ URI: phân biệt Path với URL

Có hai họ factory nhận `URI`:

~~~java
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

Path fromUri = Path.of(URI.create("file:///tmp/demo.txt")); // Java 11+.
Path oldStyle = Paths.get(URI.create("file:///tmp/demo.txt")); // Java 7+.
~~~

Ví dụ là fragment minh họa URI có scheme `file` và đường dẫn theo cú pháp thường thấy trên hệ điều hành Unix; **không phải snippet portable cho file `C:` trên Windows**. Với dữ liệu thực, ưu tiên tạo URI từ Path của chính filesystem đó bằng `path.toUri()` rồi chuyển ngược nếu cần.

Đừng viết `Path.of("https://example.com/file.txt")` để kỳ vọng tải file qua HTTP. Overload nhận **String** tạo path theo **default filesystem**, trong khi overload nhận **URI** tìm provider theo **scheme** của URI. Một scheme `https` không mặc định có FileSystemProvider cài sẵn; `Path.of(URI.create("https://..."))` thường gây `FileSystemNotFoundException` hoặc lỗi provider tương ứng, **không tải dữ liệu qua mạng**. Dùng API HTTP/URL client riêng cho truy cập mạng.

### 6.1. `Path.of(URI)` có tự tạo FileSystem mới không?

Không được giả định như vậy. Hợp đồng `Path.of(URI)` là tra cứu FileSystemProvider phù hợp với scheme và chuyển đổi URI thành Path; với scheme ngoài `file`, một provider có thể yêu cầu **FileSystem đã được mount/mở** trước đó.

Ví dụ với URI trỏ vào ZIP/JAR (dạng `jar:file:...!/entry`), thường phải mở file system tương ứng bằng `FileSystems.newFileSystem(...)`, sau đó dùng chính `zipFs.getPath(...)`. Nếu gọi `Path.of(jarUri)` khi chưa có filesystem tương ứng, có thể gặp `FileSystemNotFoundException`. Không dùng `Paths.get(URI)` như một cách “tự động mount mọi loại tệp” chung.

Các exception có thể gặp tùy factory/provider: `InvalidPathException` với string path không hợp lệ; `IllegalArgumentException` khi URI vi phạm định dạng yêu cầu; `FileSystemNotFoundException` khi filesystem chưa có; `ProviderNotFoundException` khi không có provider hỗ trợ; `SecurityException` hoặc `IOException` ở API tạo/mở filesystem. Xem tài liệu overload cụ thể vì không phải exception nào cũng được khai báo ở mọi method.

## 7. Ví dụ đầy đủ: tạo Path trong ZIP FileSystem

Một file ZIP có thể được mở như một filesystem riêng nếu runtime có ZIP provider (thường là module `jdk.zipfs` trên JDK tiêu chuẩn). `FileSystem.getPath("/docs", "readme.txt")` định danh một phần tử **bên trong archive**, không phải file cùng tên trên host.

Tạo file **`ZipPathDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class ZipPathDemo {
    public static void main(String[] args) throws IOException {
        Path tempDir = Files.createTempDirectory("zip-path-demo-");
        Path zip = tempDir.resolve("sample.zip");

        try {
            // 'zip' chưa tồn tại; provider nhận env create=true.
            try (FileSystem zipFs =
                     FileSystems.newFileSystem(zip, Map.of("create", "true"))) {

                Path member = zipFs.getPath("/docs", "readme.txt");
                Files.createDirectories(member.getParent());
                Files.writeString(member, "Hello ZIP",
                                  StandardCharsets.UTF_8);

                System.out.println("exists=" + Files.exists(member));
                System.out.println("content="
                        + Files.readString(member, StandardCharsets.UTF_8));
                System.out.println("defaultFs="
                        + (member.getFileSystem() == FileSystems.getDefault()));
            } // close zipFs: hoàn tất và giải phóng tài nguyên ZIP.

        } finally {
            Files.deleteIfExists(zip);
            Files.deleteIfExists(tempDir);
        }
    }
}
~~~

Lệnh chạy:

~~~bash
javac --release 17 ZipPathDemo.java
java ZipPathDemo
~~~

**Stdout đã kiểm tra trên JDK có ZIP provider:**

~~~text
exists=true
content=Hello ZIP
defaultFs=false
~~~

**Điểm cốt lõi:**

- `Files.createTempDirectory` tạo **thư mục thật** trên default filesystem; đây là I/O. File `sample.zip` **chưa có**, cho phép env `create=true` để provider tạo archive. Không tạo sẵn một **file ZIP rỗng chỉ bằng bytes rỗng** rồi mặc định nó là archive ZIP hợp lệ.
- `FileSystems.newFileSystem(zip, Map.of("create", "true"))` từ Java 13 mở provider thích hợp cho path của archive, với cấu hình provider-specific `create`. Tham số `Map` và tên key này thuộc ZIPFS, không phải tùy chọn chung bắt buộc của mọi provider.
- `zipFs.getPath("/docs", "readme.txt")` tạo path **trong ZIPFS**; `Files.createDirectories(member.getParent())` và `Files.writeString(member,...)` thao tác thông qua provider tương ứng.
- Khi thoát try-with-resources, `zipFs.close()` đóng filesystem; sau đó `finally` xóa archive và thư mục tạm trên default filesystem.
- Nếu JDK runtime đã bị tối giản và không chứa ZIP provider, `newFileSystem` có thể gặp `ProviderNotFoundException`. Mục tiêu của ví dụ là file system semantics; không coi ZIPFS có sẵn ở mọi runtime cắt giảm.

### 7.1. Dùng cách nào nếu cần hỗ trợ Java 8?

Overload `FileSystems.newFileSystem(Path, Map)` như trên chỉ có từ Java 13. Với Java 8/11, có thể dùng overload URI đã có từ Java 7:

~~~java
// Fragment Java 8: zipFile là Path tới vị trí ZIP archive cần mở.
// URI kiểu jar:<file-uri> được zipfs provider sử dụng.
URI uri = URI.create("jar:" + zipFile.toUri());
Map<String, String> env = new HashMap<>();
env.put("create", "true");

try (FileSystem zipFs = FileSystems.newFileSystem(uri, env)) {
    Path member = zipFs.getPath("/docs", "readme.txt");
    // Thao tác trong ZIPFS ...
}
~~~

Snippet cần import `java.net.URI`, `java.nio.file.Files`, `java.nio.file.FileSystem`, `java.nio.file.FileSystems`, `java.nio.file.Path`, `java.util.Map`, `java.util.HashMap` và nằm trong method xử lý `IOException`. Trong chương trình thật, phải quản lý archive và filesystem sau khi hoàn tất. Điều kiện provider ZIPFS vẫn áp dụng; API này không tạo provider nếu runtime không có.

## 8. Tính portable: đừng tự nối chuỗi file path

Thay vì:

~~~java
// Phản ví dụ: tự ráp dấu phân tách sẽ khóa giả định về OS/provider.
String path = base + "/" + fileName;
~~~

nên dùng:

~~~java
// base đã là Path do caller cung cấp.
Path file = base.resolve(fileName);
~~~

**Ưu điểm:** `resolve` giữ path cùng provider với `base`, diễn giải separator theo provider và không cần tự xử lý trường hợp có/không slash cuối. Nhưng `resolve` **không tự bảo đảm** `fileName` an toàn, không nhất thiết ngăn đường dẫn tuyệt đối ghi đè base và không tự tạo file.

Ví dụ riêng cho Windows: chuỗi `"C:\\data\\report.txt"` có cú pháp hợp lệ trên Windows, nhưng trên Unix dấu backslash thường được coi là ký tự tên bình thường. Tương tự, `Path.of("/var/log/app.log")` là absolute Unix, không phải thiết kế cross-platform mặc định cho Windows.

### 8.1. Không dùng `Path.toString()` như định dạng trao đổi độc lập hệ điều hành

`path.toString()` được định nghĩa theo provider và thường dùng cho **hiển thị/debug/ghi log**, không nên tự coi nó là URL chuẩn hoặc format trao đổi portable giữa Windows/Linux. Khi cần nhận dạng tài nguyên bằng URI, dùng `toUri()` trong trường hợp provider hỗ trợ và lưu ý vòng đời FileSystem liên quan. Với JSON/config chuẩn quốc tế, cần quy ước riêng separator, gốc đường dẫn và cách escape chuỗi.

Trong HTML/HTTP, `/` là delimiter của URL path, không đồng nghĩa separator của filesystem. `URLDecoder` giải mã form encoding theo ngữ nghĩa khác; không tự đưa URL path đã decode vào `Path.of(...)` rồi xem như đã sandbox an toàn.

## 9. Path được tạo không chứng minh tồn tại hoặc là tệp thường

~~~java
Path p = Path.of("notes", "todo.txt");
System.out.println(p.isAbsolute()); // false trên default FS thông thường.
// Files.exists(p) có thể false nếu chưa tạo tệp.
~~~

Chỉ tạo Path không ném `NoSuchFileException` vì tệp không tồn tại. Khi muốn thao tác thực tế, dùng `Files`:

| Mục tiêu | API | Lưu ý |
| --- | --- | --- |
| Kiểm tra tồn tại | `Files.exists(path)` | `false` cũng có thể do không xác định được trạng thái trong một số điều kiện, đừng dùng riêng nó làm khóa chống race. |
| Đọc thuộc tính | `Files.readAttributes(...)` | Có thể ném IOException, phụ thuộc provider và quyền truy cập. |
| Tạo thư mục cha | `Files.createDirectories(path.getParent())` | Kiểm tra `getParent() != null` khi path chỉ có tên file. |
| Tạo file mới, không ghi đè | `Files.createFile(path)` | Thực hiện create-if-absent theo contract; có thể `FileAlreadyExistsException`. |
| Đọc text | `Files.readString(path, UTF_8)` | Java 11+, có I/O và charset. |
| Ghi text | `Files.writeString(path, content, UTF_8)` | Java 11+, tạo/ghi file tùy options; không tự tạo cha. |
| Mở byte stream | `Files.newInputStream(path)` | Caller chịu trách nhiệm close kết quả. |
| Duyệt cây | `Files.walk(path)` | Trả lazy `Stream<Path>` cần đóng, có thể giữ resource. |
| Canonical/real | `path.toRealPath()` | Có I/O, đòi đường dẫn tồn tại, xử lý symlink theo options. |

`Files.exists` rồi `Files.createFile` vẫn có **time-of-check to time-of-use (TOCTOU)**: bên khác có thể tạo/sửa file trong khoảng giữa hai thao tác. Nếu cần thao tác atomic hoặc bảo vệ quyền, sử dụng API có contract phù hợp như `createFile` hay tùy chọn `CREATE_NEW`, không dựa vào `if (!exists)` làm điều kiện an toàn duy nhất.

## 10. Kiểm soát đường dẫn từ input người dùng

Một ví dụ phổ biến là upload file vào một thư mục cố định. Nếu tên file đến từ HTTP request, không được nối vào root rồi cho phép `..` thoát vùng lưu trữ.

Ví dụ thực hành **thuần thao tác Path, không ghi file**:

Tạo file **`PathGuardDemo.java`**:

~~~java
import java.nio.file.Path;

public class PathGuardDemo {
    public static void main(String[] args) {
        Path root = Path.of("store").toAbsolutePath().normalize();

        Path good = root.resolve("docs")
                .resolve("note.txt").normalize();

        Path bad = root.resolve("..")
                .resolve("outside.txt").normalize();

        System.out.println("inside=" + good.startsWith(root));
        System.out.println("outside=" + bad.startsWith(root));
    }
}
~~~

~~~bash
javac --release 17 PathGuardDemo.java
java PathGuardDemo
~~~

**Stdout đã kiểm tra:**

~~~text
inside=true
outside=false
~~~

Ý nghĩa: sau khi đưa về absolute và chuẩn hóa **lexical**, `startsWith(root)` so sánh thành phần Path, đáng tin cậy hơn `someString.startsWith("/root")` vốn có thể nhầm `/rootA` với `/root`. Tuy vậy, **đây không phải giải pháp sandbox hoàn chỉnh**, đặc biệt khi filesystem có symlink hoặc các tiến trình khác có thể sửa cây thư mục.

### 10.1. Giới hạn của kiểm tra lexical

Để kiểm soát security boundary thực sự, cần xem cả:

1. **Input là path tuyệt đối hay tương đối?** Nếu yêu cầu tên file đơn, từ chối separator, `.` và `..` theo chính sách nghiệp vụ; `Path.getFileName` có thể hữu ích nhưng không tự xác thực mọi input.
2. **Có symlink trong cây không?** Đường dẫn lexical nằm trong root có thể thực tế dẫn ra ngoài nếu một thành phần là symbolic link. `toRealPath()` có thể giúp biết đích hiện thời nhưng còn race.
3. **Có race với actor khác không?** Sau khi kiểm tra nhưng trước khi open, symlink hoặc directory entry có thể thay đổi. Cần thiết kế quyền hệ điều hành, thư mục không bị sửa bởi bên không tin cậy, hoặc API như `SecureDirectoryStream` khi provider hỗ trợ.
4. **Có path thuộc filesystem/provider khác không?** `root.resolve(other)` và so sánh cross provider có thể không hoạt động như mong muốn. Giữ đường dẫn con tương ứng với đúng filesystem của root.
5. **Có quy tắc riêng trên Windows không?** Drive-relative, UNC, ký tự bị cấm, case sensitivity và normalisation behavior có thể khác Unix. Không hard-code một check string rồi cho rằng an toàn trên mọi nền tảng.

**Ghi nhớ:** `normalize()` và `startsWith()` hữu ích cho kiểm tra cấu trúc, nhưng không ngăn được symlink traversal hoặc TOCTOU nếu không có các ràng buộc thực thi bổ sung.

## 11. Kiểm tra mối quan hệ giữa các FileSystem và Path

Một `Path` không phải chỉ là một string. Nó gắn với filesystem của nó:

~~~java
Path path = Path.of("a.txt");
FileSystem owner = path.getFileSystem();
boolean defaultOwner = owner == FileSystems.getDefault();
~~~

Mã fragment trên default filesystem thường có `defaultOwner == true`. Với path trong `zipFs`, `getFileSystem() == zipFs` mới đúng.

Một số thao tác có thể báo `ProviderMismatchException` hoặc `IllegalArgumentException` khi kết hợp path khác provider, tùy API/provider (ví dụ `relativize` giữa path không tương thích). `Files.copy(sourcePath, targetPath)` hỗ trợ một số thao tác cross-provider theo hợp đồng Files, nhưng không được đánh đồng nó với phương thức nối path `resolve/relativize`.

Trong **library reusable**, Oracle Javadoc của `Path.of(String,...)` lưu ý việc giả định default FileSystem hạn chế khả năng tái sử dụng. Nếu hàm được giao một `Path base`, ưu tiên xây thêm tên tương đối qua `base.resolve(...)` thay vì gọi `Path.of(...)` cho từng bước, đặc biệt khi caller có thể truyền ZIPFS hoặc virtual FS. Với API chỉ biết nội dung tên file và cần tạo path mới, có thể nhận thêm một `FileSystem` từ caller.

## 12. Xử lý ngoại lệ khi chuyển String/URI thành Path

Tạo Path từ chuỗi thường là thao tác cấu trúc và có thể fail **ngay lập tức** nếu tên không hợp lệ theo provider:

~~~java
// Fragment: raw là String lấy từ cấu hình/user input.
try {
    Path path = Path.of(raw);
    // Chỉ đến đây mới biết chuỗi chuyển thành Path được.
    // Chưa chắc file tồn tại hoặc có quyền truy cập.
} catch (java.nio.file.InvalidPathException ex) {
    // Trả lỗi validation phù hợp, không tiết lộ path nhạy cảm vào log.
}
~~~

Các tình huống tách biệt:

- **`InvalidPathException`:** chuỗi không phải path hợp lệ theo quy tắc provider, ví dụ ký tự bị cấm hoặc cấu trúc không hợp lệ.
- **`NoSuchFileException`:** thường xuất hiện lúc thực hiện Files I/O trên tài nguyên không tồn tại, **không phải** cứ `Path.of` cho tên chưa tồn tại là ném.
- **`AccessDeniedException`/`IOException`:** quyền hoặc I/O lúc thao tác tệp/dir.
- **`FileSystemNotFoundException`/`ProviderNotFoundException`:** có thể xảy ra khi xây Path từ URI hoặc mở provider không tìm thấy file system phù hợp.
- **`ClosedFileSystemException`:** đường dẫn thuộc file system đã đóng được đem đi thực hiện những thao tác phụ thuộc provider.
- **`ProviderMismatchException`:** API/provider phát hiện path thuộc provider không tương thích.
- **`SecurityException`:** một số thao tác có thể liên quan kiểm soát quyền ở môi trường tương ứng.

**Không thay mọi trường hợp bằng `catch (Exception)` rồi cho rằng chỉ cần tạo lại Path là đủ.** Hợp đồng tạo path, đường dẫn không tồn tại và lỗi đọc/ghi là những tầng khác nhau.

## 13. Các mốc phiên bản JDK cần nhớ

| Mốc | Ý nghĩa đối với bài này |
| --- | --- |
| **Java 7** | NIO.2 ra mắt với `Path`, `Paths`, `FileSystems`, `Files`; dùng `Paths.get(...)` hoặc `fs.getPath(...)`. |
| **Java 8** | Vẫn chưa có `Path.of`. Có thể dùng `Paths.get`, `Files.lines`, `Stream` và `FileSystems.newFileSystem(URI,Map)`. |
| **Java 9** | Không phải mốc giới thiệu `Path.of`; đã có các cải tiến khác trong NIO/stream, đừng nhầm. |
| **Java 11** | Thêm `Path.of(String...)` và `Path.of(URI)`; thêm các convenience text I/O như `Files.readString`/`Files.writeString`. |
| **Java 13** | Có overload `FileSystems.newFileSystem(Path,Map)` dùng trong ví dụ ZIPFS. |
| **Java 17+** | Cả ba cách tạo Path và overload ZIPFS trong bài đều khả dụng trên JDK đầy đủ; code có thể bị hạn chế bởi môi trường/provider riêng. |

**Nếu build hỗ trợ Java 8:** thay `Path.of(...)` bằng `Paths.get(...)` cho default provider hoặc dùng `FileSystem.getPath(...)`; không dùng `Files.readString/writeString` hay `FileSystems.newFileSystem(Path,Map)`; thay các API đó bằng stream/reader và overload URI tương ứng. Dùng `javac --release 8` để kiểm tra source + API khả dụng, không chỉ đổi `-source`/`-target` rồi giả định không gọi API mới.

### API selection cheat sheet

~~~text
Java 11+ + default filesystem   → Path.of("dir", "file.txt")
Java 7/8 + default filesystem   → Paths.get("dir", "file.txt")
Đã có Path base              → base.resolve("file.txt")
Đã có FileSystem fs          → fs.getPath("dir", "file.txt")
Có URI hợp lệ + provider      → Path.of(uri) (Java 11+) / Paths.get(uri)
Cần ZIP archive filesystem  → FileSystems.newFileSystem(...), rồi zipFs.getPath(...)
~~~

Không chọn `FileSystems.getDefault().getPath(...)` chỉ vì cú pháp dài hơn trông “an toàn” hơn. Nó có ý nghĩa khi muốn truyền/chọn file system tường minh; còn Path ở default provider tạo theo ba factory chính thường tương đương.

## 14. Lỗi phổ biến và cách tránh

1. **“Tạo Path tức tạo file.”** Sai: Path mô tả vị trí; `Files.createFile`/`Files.writeString` mới thực hiện I/O.
2. **“Path.of dùng được từ Java 8.”** Sai: `Path.of` được thêm từ Java 11; Java 8 dùng `Paths.get`/`FileSystem.getPath`.
3. **“Paths.get đã bị loại bỏ.”** Sai: vẫn hợp lệ, rất hữu ích cho source cũ và compatibility.
4. **“Path.of(String) và Path.of(URI) giống nhau.”** Sai: String → default FS, URI → tìm provider theo scheme.
5. **“FileSystem.getPath luôn là default FS.”** Sai: nó thuộc chính FileSystem được gọi, chẳng hạn ZIPFS.
6. **“Path trong ZIP là đường dẫn disk thông thường.”** Sai: `Path.getFileSystem()` gắn với ZIP provider.
7. **“FileSystems.newFileSystem là resource chẳng cần close.”** Sai với file system có handle như ZIPFS; đóng đúng vòng đời.
8. **“Có thể close default FileSystem để giải phóng bộ nhớ.”** Không: default FS được quản lý bởi JVM và không thể đóng theo API.
9. **“Tôi dùng dấu / nên mọi Windows path đều tương đương Unix.”** Sai: drive, UNC, separator, chữ hoa/thường và các quy tắc provider khác nhau.
10. **“Path.toString là URL.”** Sai: nó là representation theo provider, dùng `toUri` khi cần URI phù hợp.
11. **“normalize chứng minh file tồn tại.”** Sai: lexical, không truy vấn filesystem.
12. **“normalize xong startsWith(root) là sandbox tuyệt đối.”** Sai: symlink/race vẫn có thể vượt phạm vi.
13. **“toAbsolutePath kiểm tra tệp thật.”** Sai: muốn canonical real path cần `toRealPath` với ngữ nghĩa I/O.
14. **“Files.exists rồi Files.createFile là atomic.”** Sai: có TOCTOU; ưu tiên operation có contract atomic phù hợp.
15. **“resolve với tên do user gửi luôn nằm dưới base.”** Sai: input tuyệt đối/`..` có thể làm thoát root.
16. **“Mỗi FileSystem chỉ khác dấu slash.”** Sai: provider quyết định URI, roots, thuộc tính, khả năng I/O và lifecycle.
17. **“JAR URI bất kỳ đưa vào Path.of sẽ tự mount được.”** Không đảm bảo: cần provider và có thể phải tạo/mở FileSystem trước.
18. **“Path equality tức hai path trỏ cùng file vật lý.”** Không luôn đúng: so sánh path object khác với `Files.isSameFile` có thể truy vấn filesystem, symlinks/hardlinks.
19. **“Chỉ lưu string path rồi dùng trên mọi máy.”** Có thể không portable: root, name separator, encoding, provider, quyền đều phụ thuộc môi trường.
20. **“Thư viện nhận base Path nên tạo child bằng Path.of.”** Có thể sai provider; ưu tiên `base.resolve(...)`.

## 15. Câu hỏi tự kiểm tra

1. `Path.of("hello.txt")` có tạo tệp trên disk không?
2. Điểm chung và khác nhau giữa `Paths.get("a","b")` và `Path.of("a","b")` là gì? Java 8 chọn API nào?
3. Nếu caller truyền `Path base` thuộc ZIPFS, vì sao `Path.of("child.txt")` có thể là bug?
4. `FileSystems.getDefault()` có nên được đóng bằng try-with-resources không?
5. `Path.of("logs","app.log").toAbsolutePath()` có đòi file tồn tại không? Còn `toRealPath()`?
6. `Path.of(URI.create("https://example.com/x"))` có tải dữ liệu từ mạng không? API nào khác cần dùng?
7. `Files.newFileSystem(zip, Map.of("create","true"))` trong demo tạo kiểu filesystem nào và cần dọn thế nào?
8. Tại sao `normalize()` có thể thay đổi ý nghĩa đường dẫn khi symlink tham gia?
9. Nếu `base.resolve(untrustedName)` được dùng cho upload, còn cần các kiểm tra nào ngoài `startsWith`?
10. Vì sao `Files.exists` ngay trước `Files.createFile` không loại bỏ hoàn toàn tình trạng race?
11. Nếu một path thuộc file system đã đóng, phép xử lý `Files.readString(path)` có thể ra sao?
12. Khi cần code reusable cho provider khác default, nên nhận tham số `String`, `Path` hay `FileSystem`?

**Gợi ý đáp án:**

1. Không; phải gọi Files API để tạo/ghi.
2. Đều dùng default FS cho string overload trên JDK 11+; `Path.of` xuất hiện Java 11, `Paths.get` có từ Java 7 và dùng được Java 8.
3. `Path.of` lấy default FS, còn `base.resolve` giữ provider của base.
4. Không, default FS do JVM quản lý; `close()` không được hỗ trợ.
5. `toAbsolutePath` không kiểm tra tồn tại; `toRealPath` dựa vào filesystem thật và thường cần tồn tại.
6. Không; `Path.of(URI)` cần provider cho scheme, dùng HTTP client/API mạng thích hợp.
7. ZIP filesystem, đóng `FileSystem` để commit/giải phóng handle, dọn file archive nếu chỉ là dữ liệu tạm.
8. Xóa lexical `name/..` có thể khác hành trình thực qua symlink.
9. Kiểm soát tuyệt đối/tương đối, thành phần `..`, symlink, TOCTOU, quyền, provider và atomic file creation theo threat model.
10. Hai lần gọi là hai thời điểm riêng, actor khác có thể thay đổi filesystem giữa chừng.
11. Có thể ném `ClosedFileSystemException` khi provider cần filesystem đang mở.
12. Nhận `Path base` nếu cần resolve child, hoặc `FileSystem` nếu muốn chính caller quyết định provider.

## 16. Tài liệu Java SE chính thức

- [Oracle Java SE 21 — Path](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Path.html): `of(String...)`, `of(URI)`, `resolve`, `normalize`, `toRealPath`, thao tác name components và ghi chú provider.
- [Oracle Java SE 21 — Paths](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Paths.html): `get(String...)` và `get(URI)`, tương thích default provider.
- [Oracle Java SE 21 — FileSystem](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/FileSystem.html): `getPath`, separators, roots, lifecycle và provider.
- [Oracle Java SE 21 — FileSystems](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/FileSystems.html): `getDefault`, `newFileSystem(URI,Map)`, `newFileSystem(Path,Map)`.
- [Oracle Java SE 21 — Files](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html): tạo/đọc/ghi tệp, operation thực tế, `exists`, `isSameFile`, directory streams.
- [Oracle Java SE 21 — InvalidPathException](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/InvalidPathException.html): lỗi chuyển path string không hợp lệ.
- [OpenJDK — ZIP File System Provider](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jar.html): bối cảnh ZIP/JAR; khi triển khai ZIPFS cần kiểm tra module/provider trên runtime.

**Ghi nhớ:** `Path.of` (Java 11+) và `Paths.get` (Java 7+) là lựa chọn tiện lợi cho **default FileSystem**; `fs.getPath` tạo đường dẫn trong **đúng FileSystem cụ thể**. Tạo `Path` không tạo tệp. Khi nhận một base Path từ caller, `base.resolve(child)` thường giúp giữ đúng provider; còn khi đường dẫn đến từ người dùng, cần kiểm soát symlink, quyền và race trước thao tác Files I/O.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="path-002">Root, parent, file name và các thành phần đường dẫn</a>

<details>
<summary>Xem chi tiết</summary>

# Root, parent, file name và các thành phần đường dẫn trong Java Path

## 1. Path mô tả vị trí bằng các thành phần, không chỉ là một chuỗi

Trong Java NIO.2, **java.nio.file.Path** biểu diễn một đường dẫn trong một file system. Đường dẫn có thể trỏ tới file, thư mục, symbolic link, vị trí chưa tồn tại hoặc một root. Đối tượng Path gồm các **name element** (thành phần tên), có thể có **root component** và được giải thích theo quy tắc của file-system provider.

Ví dụ minh họa theo kiểu đường dẫn Unix:

~~~text
/home/learner/docs/lesson.md
|---|---------------------|
root        name elements

root       = /
name[0]    = home
name[1]    = learner
name[2]    = docs
name[3]    = lesson.md
parent     = /home/learner/docs
fileName   = lesson.md
nameCount  = 4
~~~

Ba khái niệm dễ nhầm nhất:

- **Root:** thành phần gốc của hệ thống đường dẫn, ví dụ dấu / trên Unix, ổ đĩa hoặc share root trên Windows. Lấy bằng **getRoot()**.
- **Parent:** đường dẫn cha về mặt **cú pháp**, bỏ thành phần tên cuối cùng. Lấy bằng **getParent()**.
- **File name:** thành phần tên cuối, không nhất thiết là một file thường hay đã tồn tại. Lấy bằng **getFileName()**.

Những phương thức này chủ yếu phân tích **cấu trúc đường dẫn**, không mở file, không kiểm tra quyền truy cập và không xác minh vị trí có tồn tại.

**Điểm quan trọng:** Path là đối tượng bất biến (immutable). Các phép như normalize(), resolve() hoặc subpath() trả về Path kết quả; chúng không sửa đối tượng gốc.

## 2. Các API cần nhớ

| API | Kết quả | Lưu ý |
| --- | --- | --- |
| **getRoot()** | Root dưới dạng Path; có thể null | Root không được tính là name element |
| **getParent()** | Path cha; có thể null | Chỉ tính theo cấu trúc đường dẫn; không tự loại . hoặc .. |
| **getFileName()** | Thành phần tên cuối dưới dạng Path; có thể null | Không xác minh đó là file hay thư mục |
| **getNameCount()** | Số name element | Đường dẫn chỉ gồm root có count = 0 |
| **getName(index)** | Thành phần tên tại vị trí index | Bắt đầu từ 0; root không có chỉ số |
| **subpath(begin, end)** | Dãy name element con dưới dạng Path tương đối | begin bao gồm, end không bao gồm |
| **iterator()** | Duyệt lần lượt các name element | Không trả root |
| **isAbsolute()** | Đường dẫn có phải absolute theo provider? | Không phải phép kiểm tra file có thật |
| **normalize()** | Bỏ những name element dư thừa về mặt cú pháp | Không truy vấn file system |
| **resolve(other)** | Ghép đường dẫn bên dưới path hiện tại | Nếu other là absolute thì thường trả other |
| **resolveSibling(other)** | Thay phần tử cuối bằng một path cùng cấp | Dựa trên parent của path hiện tại |
| **relativize(other)** | Đường dẫn tương đối từ path hiện tại đến other | Phải dùng các path tương thích |
| **toAbsolutePath()** | Biểu diễn absolute của đường dẫn | Không tương đương xác minh file thực |
| **toRealPath()** | Đường dẫn thực của file đã tồn tại | Truy cập file system; có thể ném IOException |

Hầu hết phép phân tách Path không cần đến Files.exists(): một Path chỉ là mô tả địa chỉ và vẫn có thể được phân tích dù đường dẫn chưa tồn tại.

## 3. Đường dẫn relative và absolute ảnh hưởng root thế nào?

**Relative path (đường dẫn tương đối)** cần ngữ cảnh thư mục làm việc hoặc base path để định vị file. Ví dụ:

~~~text
courses/io/README.md
~~~

Với default file system thông thường, đường dẫn đó có:

~~~text
root     = null
parent   = courses/io
fileName = README.md
names    = [courses, io, README.md]
~~~

**Absolute path (đường dẫn tuyệt đối)** có đủ thông tin theo quy tắc file system để xác định vị trí mà không cần ghép thêm thư mục cơ sở. Ví dụ:

~~~text
Unix:      /var/log/app.log
Windows:   C:\work\logs\app.log
Windows:   \\server\share\reports\2026.txt
~~~

Các ví dụ trên minh họa **cú pháp của từng hệ điều hành**, không có nghĩa Java trên Linux sẽ hiểu mọi đường dẫn Windows đúng như Java trên Windows. Trong một môi trường Linux, chuỗi bắt đầu bằng C: có thể chỉ là tên tương đối; trong Windows, root/drive/share có cách phân tích riêng.

Đừng tự xây đường dẫn dùng chung bằng cách nối chuỗi với / hoặc \. Ưu tiên:

~~~java
Path file = Path.of("courses", "io", "README.md");
~~~

Khi dùng default provider, Path.of(...) ghép các phần theo quy tắc platform. **toString()** có thể hiển thị dấu phân cách khác nhau giữa Windows và Unix. Khi viết ví dụ và test chạy đa hệ điều hành, nên kiểm tra từng **name element** hoặc tính chất đường dẫn, thay vì cố định toàn bộ chuỗi kết quả với một dấu phân cách duy nhất.

**Root không đồng nghĩa thư mục home hoặc thư mục làm việc**. Chẳng hạn trên Unix root có thể là /, còn thư mục làm việc là /home/learner/project. Trên Windows đường dẫn tuyệt đối có thể thuộc ổ đĩa hoặc share riêng.

## 4. Chương trình Java 17+ đọc các thành phần đường dẫn

Tạo file **PathComponentsDemo.java**, lưu mã nguồn UTF-8:

~~~java
import java.nio.file.Path;

public class PathComponentsDemo {
    public static void main(String[] args) {
        Path path = Path.of("courses", "io", "README.md");

        System.out.println("absolute=" + path.isAbsolute());
        System.out.println("root=" + path.getRoot());
        System.out.println("parentLast="
                + path.getParent().getFileName());
        System.out.println("fileName=" + path.getFileName());
        System.out.println("count=" + path.getNameCount());

        for (int i = 0; i < path.getNameCount(); i++) {
            System.out.println("name[" + i + "]=" + path.getName(i));
        }

        Path single = Path.of("README.md");
        System.out.println("singleParent=" + single.getParent());

        Path systemRoot = Path.of("").toAbsolutePath().getRoot();
        System.out.println("rootParent=" + systemRoot.getParent());
        System.out.println("rootFileName=" + systemRoot.getFileName());
        System.out.println("rootNameCount=" + systemRoot.getNameCount());
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 PathComponentsDemo.java
java PathComponentsDemo
~~~

Stdout trên các default file system Windows/Unix thông dụng:

~~~text
absolute=false
root=null
parentLast=io
fileName=README.md
count=3
name[0]=courses
name[1]=io
name[2]=README.md
singleParent=null
rootParent=null
rootFileName=null
rootNameCount=0
~~~

**Vì sao kết quả đúng?**

1. Path tương đối courses/io/README.md có ba thành phần tên, không có root.
2. getParent() là courses/io; lấy fileName() của parent được io.
3. getFileName() của path ban đầu là README.md; fileName là thành phần cuối, kể cả nếu đây là một **thư mục** chứ không phải file thường.
4. Một Path tương đối chỉ có một thành phần README.md **không có parent**, nên getParent() là null. Thư mục làm việc hiện tại không tự được gắn vào path.
5. Khi lấy root của một absolute path thuộc default file system, bản thân root không có parent, không có fileName và có **0 name element**.

**Không nhầm Path.of("") với đường dẫn chỉ gồm root:** Path rỗng theo hợp đồng Path có một name element rỗng, trong khi Path biểu diễn root đơn độc có nameCount() = 0. Tạo Path rỗng không phải cách trực tiếp lấy root; đoạn code lấy root từ absolute path của thư mục làm việc.

## 5. getRoot(): thành phần gốc, không phải element đầu tiên

Đối với /home/learner/docs theo cú pháp Unix:

~~~text
getRoot()     -> /
getName(0)    -> home
getName(1)    -> learner
getName(2)    -> docs
getNameCount()-> 3
~~~

Root đứng **ngoài** danh sách name element. Vì vậy gọi getName(0) trên Path chỉ chứa / không hợp lệ; path đó có getNameCount() bằng 0.

Với Path tương đối trong môi trường thông thường, getRoot() trả null. Tuy nhiên **cách định nghĩa root và absolute là phụ thuộc provider**. Ví dụ Windows có thể phân biệt ổ đĩa và đường dẫn liên quan đến ổ đĩa hoặc root tương đối; không dùng quy tắc văn bản đơn giản kiểu “có dấu / đầu chuỗi là absolute” hoặc “có dấu hai chấm là root” cho mọi hệ điều hành.

Khi cần biết tính absolute, gọi **isAbsolute()** thay vì so sánh chuỗi. Khi cần root, gọi **getRoot()** và xử lý khả năng null:

~~~java
Path root = path.getRoot();
if (root == null) {
    System.out.println("No root component");
} else {
    System.out.println("Has a root component");
}
~~~

Đây là mảnh code dùng trong phương thức đã có biến path kiểu Path.

## 6. getParent(): đường dẫn cha theo cú pháp

getParent() trả một Path mới gồm root (nếu có) và toàn bộ name element **trừ tên cuối**:

~~~text
courses/io/README.md  -> courses/io
courses/io            -> courses
courses               -> null

Trên Unix:
/home/learner/file    -> /home/learner
/home                 -> /
/                     -> null
~~~

Một path một tên **không có root** như report.txt không có parent được biểu diễn bên trong path, nên:

~~~java
Path name = Path.of("report.txt");
System.out.println(name.getParent() == null); // true
~~~

Nếu cần xác định thư mục chứa file tương đối dựa trên thư mục làm việc, có thể chuyển sang absolute trước:

~~~java
Path absolute = name.toAbsolutePath();
Path parent = absolute.getParent();
~~~

Nhưng không được coi parent đã tồn tại hay file đã được tạo. **getParent() không gọi hệ thống file**, không xử lý symbolic link và không kiểm tra quyền truy cập.

### Dấu . và .. không tự được getParent loại bỏ

~~~text
Đường dẫn tương đối:  a/../b
getParent():          a/..
normalize():          b
normalize().getParent(): null
~~~

Vì vậy, nếu muốn parent của **đường dẫn đã giản lược về mặt cú pháp**, hãy chủ động normalize trước:

~~~java
Path parent = path.normalize().getParent();
~~~

Ngay cả vậy, normalize cũng **không chứng minh nơi lưu trữ thực sự**: một đoạn đường dẫn có symbolic link có thể thay đổi ý nghĩa khi xử lý .. ở tầng file system. Nếu cần vị trí thực của một đường dẫn đã tồn tại, xem toRealPath() và các quy tắc về symlink.

**Tránh NullPointerException:** getParent() có thể null khi path không có parent; không gọi getParent().getFileName() khi chưa chắc parent tồn tại.

## 7. getFileName(): tên cuối có thể là file hoặc thư mục

getFileName() chỉ phân tích thành phần tên cuối:

~~~text
courses/io/README.md -> README.md
courses/io           -> io
README.md            -> README.md

Trên Unix:
/home/learner         -> learner
/                     -> null
~~~

Tên phương thức **không đảm bảo** phần tử cuối là regular file. Một đường dẫn tên photos có thể trỏ tới thư mục, symlink hoặc chưa tồn tại. Nếu cần loại thực tế, dùng API Files như **Files.isRegularFile**, **Files.isDirectory**, **Files.isSymbolicLink**, tùy ngữ nghĩa và chính sách theo symbolic link.

Ví dụ:

~~~java
Path filename = Path.of("data", "reports");
System.out.println(filename.getFileName()); // reports
~~~

Đây vẫn là kết quả hợp lệ nếu data/reports là thư mục hoặc chưa có trên đĩa.

**Extension không phải thành phần riêng của Path.** getFileName() cho README.md, không tự trả md. Nếu ứng dụng cần phân tích extension, phải quy định rõ cách xử lý tên bắt đầu bằng dấu chấm, nhiều dấu chấm, tên không có extension và quy ước hệ điều hành. Path không có getExtension() chuẩn.

## 8. getNameCount(), getName(index), subpath() và Iterator

Các phần tử tên được đánh chỉ số từ **0**, không gồm root:

~~~text
Path:  courses/java/io/lesson.md

index     0        1    2       3
name      courses  java io      lesson.md
count     4
~~~

- **getName(0)** trả courses.
- **getName(3)** trả lesson.md.
- **subpath(1, 3)** trả phần gồm name[1] và name[2], tức java/io.
- **subpath(0, getNameCount())** chứa tất cả name element nhưng là **relative Path**, không mang root gốc.
- **iterator()** duyệt lần lượt các phần tử tên; không bao gồm root.

Khi lấy chỉ số ngoài phạm vi 0 đến nameCount-1, getName(index) ném **IllegalArgumentException** theo đặc tả Path Java SE 17. Với subpath(begin,end), begin phải hợp lệ, end lớn hơn begin và không vượt nameCount. Không được gọi subpath(0,0) để kỳ vọng một dãy tên rỗng bình thường.

Ví dụ duyệt không quan tâm dấu phân cách:

~~~java
Path path = Path.of("courses", "java", "io", "lesson.md");
for (Path element : path) {
    System.out.println(element);
}
~~~

Kết quả:

~~~text
courses
java
io
lesson.md
~~~

Điều này phù hợp khi viết chương trình đa nền tảng, vì việc iterate từng name element không phụ thuộc toàn bộ path in ra dùng / hay dấu gạch chéo ngược.

### Trường hợp đặc biệt: path chỉ gồm root và path rỗng

- Path chỉ gồm root, ví dụ / trên Unix, có **0 name element**, getFileName() trả null.
- Path rỗng tạo từ Path.of("") là một **empty Path** có một name element rỗng theo đặc tả Java Path.
- Không đồng nhất **empty Path**, **root Path** và **relative Path một tên**; chúng có cấu trúc và ngữ nghĩa khác nhau.

## 9. Ghép đường dẫn: resolve và resolveSibling

Khi đã có base directory, dùng **resolve()** thay vì nối chuỗi:

~~~java
Path base = Path.of("courses", "java");
Path file = base.resolve("io").resolve("lesson.md");
~~~

Ở đây file tương ứng các name element [courses, java, io, lesson.md]. **resolve(relative)** thường ghép các tên bên phải vào base; nếu đối số **absolute**, kết quả là chính absolute path đó theo hợp đồng thông thường.

Nếu muốn đổi tên cùng cấp, dùng **resolveSibling()**:

~~~java
Path oldFile = Path.of("courses", "io", "lesson.md");
Path renamed = oldFile.resolveSibling("summary.md");
~~~

renamed có parent courses/io và fileName summary.md. Khi path gốc không có parent, resolveSibling() có quy tắc riêng: thường trả other; không tự lấy thư mục làm việc làm parent.

**relativize()** tìm đường dẫn tương đối từ một Path tới Path khác:

~~~text
base:   courses/java
target: courses/java/io/lesson.md
relative = base.relativize(target)
         = io/lesson.md
~~~

Không tự áp dụng relativize giữa một absolute và một relative path. Các path cần cùng ngữ cảnh/provider và root phù hợp, nếu không có thể ném IllegalArgumentException. Nếu hai path bằng nhau, kết quả là empty Path.

## 10. Chương trình Java 17+: normalize, resolve, relativize

Tạo file **PathCompositionDemo.java**:

~~~java
import java.nio.file.Path;

public class PathCompositionDemo {
    public static void main(String[] args) {
        Path base = Path.of("workspace", "guides");
        Path target = Path.of(
                "workspace", "guides", "io", "lesson.md");

        Path relative = base.relativize(target);
        Path rebuilt = base.resolve(relative);

        System.out.println("relativeCount=" + relative.getNameCount());
        System.out.println("relativeFirst=" + relative.getName(0));
        System.out.println("relativeFileName="
                + relative.getFileName());
        System.out.println("roundTrip=" + rebuilt.equals(target));

        Path raw = Path.of(
                "workspace", "guides", "..", "assets", "icon.png");
        Path normalized = raw.normalize();

        System.out.println("beforeCount=" + raw.getNameCount());
        System.out.println("afterCount=" + normalized.getNameCount());
        System.out.println("afterParentLast="
                + normalized.getParent().getFileName());

        Path odd = Path.of("a", "..", "b");
        System.out.println("parentBefore="
                + odd.getParent().getFileName());
        System.out.println("parentAfter=" + odd.normalize().getParent());

        System.out.println("siblingName="
                + target.resolveSibling("index.md").getFileName());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 PathCompositionDemo.java
java PathCompositionDemo
~~~

Stdout mong đợi:

~~~text
relativeCount=2
relativeFirst=io
relativeFileName=lesson.md
roundTrip=true
beforeCount=5
afterCount=3
afterParentLast=assets
parentBefore=..
parentAfter=null
siblingName=index.md
~~~

Lý do:

1. base và target đều relative, có cùng prefix, nên relativize trả hai thành phần io và lesson.md.
2. resolve các thành phần tương đối trở lại base tái tạo đúng target.
3. raw gồm năm thành phần workspace, guides, .., assets, icon.png; normalize loại cặp guides/.. để còn workspace, assets, icon.png.
4. getParent() của a/../b trả a/.., tên cuối của parent là ..; còn normalize(a/../b) tạo path b chỉ một thành phần nên không có parent.
5. resolveSibling("index.md") thay tên cuối lesson.md bằng index.md.

Các thao tác trong chương trình đều **có thể thực hiện dù không có file hoặc thư mục tương ứng**; đây là phân tích/ghép đường dẫn thuần túy.

## 11. Cú pháp đường dẫn so với trạng thái thật trên đĩa

Dễ nhầm ba phép sau:

| Phép | Mục đích | Có kiểm tra file thực tế? |
| --- | --- | --- |
| **getParent/getFileName/getRoot** | Tách các thành phần của đường dẫn | Không |
| **normalize()** | Rút gọn dấu . và cặp tên/.. theo cú pháp | Không |
| **toAbsolutePath()** | Tạo biểu diễn absolute, thường dựa vào thư mục làm việc | Không bảo đảm đối tượng tồn tại |
| **toRealPath()** | Phân giải đường dẫn thực tới đối tượng đã tồn tại | Có; có thể ném IOException |

**toAbsolutePath() không tương đương toRealPath().** Ví dụ một path tương đối không tồn tại vẫn có thể được chuyển thành absolute path. Muốn tìm path thực sự của một file đã có, toRealPath() có thể cần truy cập filesystem, kiểm tra quyền và xử lý symbolic link theo tùy chọn.

Ngay cả khi dùng Files.exists(path) hay Files.isDirectory(path), trạng thái file có thể thay đổi giữa thời điểm kiểm tra và thời điểm mở file. Với I/O thực tế, cần xử lý IOException ở thao tác đọc/ghi thay vì xem kiểm tra trước đó là bảo đảm tuyệt đối.

### Symbolic link và ..

Giả sử thư mục link là symbolic link trỏ đến vị trí ở nơi khác. Biến đổi lexical như:

~~~text
link/../file.txt
        |
   normalize()
        v
file.txt
~~~

có thể **khác đường đi thực** nếu hệ thống giải quyết link trước thành phần .., tùy provider và cách truy cập. Vì vậy normalize() chỉ đơn giản hóa cú pháp, không thay thế toRealPath() hay kiểm tra ranh giới an toàn với symbolic link.

## 12. Không so sánh bằng phép nối chuỗi

Ví dụ có hai đường dẫn:

~~~text
data
database
~~~

Dùng **String.startsWith("data")** sẽ cho true ở cả hai chuỗi, dù database **không nằm trong** thư mục data. Dùng **Path.startsWith(Path)** kiểm tra theo thành phần đường dẫn theo ngữ nghĩa của provider:

~~~java
Path a = Path.of("data", "file.txt");
Path b = Path.of("database", "file.txt");

System.out.println(a.startsWith(Path.of("data"))); // true
System.out.println(b.startsWith(Path.of("data"))); // false
~~~

Path.equals và Path.startsWith đều xét các đường dẫn theo quy tắc file-system provider; không tự khẳng định hai tên khác nhau trỏ đến cùng một inode/file thật. Nếu cần kiểm tra hai đường dẫn hiện có trỏ đến cùng một file, có thể dùng **Files.isSameFile(...)**, vốn có thể cần I/O và ném IOException.

Một điểm an toàn: khi ghép tên file từ input người dùng, kiểm tra một đường dẫn sau resolve().normalize().startsWith(base) chỉ phát hiện một số trường hợp **thoát khỏi base theo cú pháp**. Nó **không đủ làm hàng rào bảo mật** khi có symbolic link, race condition hoặc file system không tin cậy. Với yêu cầu bảo mật thực sự, cần kiểm tra/kiểm soát việc mở file và xử lý link ở tầng filesystem thích hợp.

## 13. Tình huống sử dụng thực tế

### 13.1. Hiển thị tên file cho người dùng

~~~java
Path selected = Path.of("reports", "summary.csv");
Path fileName = selected.getFileName();
System.out.println(fileName); // summary.csv
~~~

Nên kiểm tra fileName != null nếu path có thể là root. Cũng đừng dùng tên này làm định danh duy nhất: hai file ở thư mục khác nhau có thể cùng tên summary.csv.

### 13.2. Tạo đường dẫn đến một file cùng thư mục

~~~java
Path source = Path.of("reports", "summary.csv");
Path backup = source.resolveSibling("summary.bak");
~~~

backup sẽ có parent giống source. Nếu thực sự muốn sao chép file, cần gọi Files.copy(...) hoặc API khác; resolveSibling() tự nó **không tạo file**.

### 13.3. Duyệt từng thư mục để hiển thị breadcrumb

~~~java
Path path = Path.of("school", "courses", "java", "notes.txt");
for (Path element : path) {
    System.out.println(element);
}
~~~

Iterator chỉ trả các name element. Nếu cần hiển thị root trong breadcrumb absolute, phải lấy riêng getRoot() và xử lý null.

### 13.4. Kiểm tra phần cuối đường dẫn

~~~java
Path path = Path.of("backup", "archive.zip");
System.out.println(path.endsWith(Path.of("archive.zip"))); // true
~~~

endsWith là so sánh theo thành phần; **không phải phép khẳng định file có tồn tại**. Nếu cần mở ZIP, dùng thư viện thích hợp sau khi xác minh đường dẫn.

## 14. Những lỗi phổ biến

| Lỗi | Vì sao sai | Cách đúng |
| --- | --- | --- |
| Coi getFileName() luôn là file vật lý | Nó chỉ trả tên cuối của Path | Dùng Files API để kiểm tra thực tế khi cần |
| Coi getParent() luôn có giá trị | Path relative một tên hoặc root có thể không có parent | Kiểm tra null |
| Coi getRoot() là getName(0) | Root không tính trong name element | Gọi getRoot() riêng |
| Gọi getName(getNameCount()) | Index cuối hợp lệ là count-1 | Kiểm tra index 0..count-1 |
| Gọi subpath(0,0) | Khoảng tên phải không rỗng | end > begin |
| In Path thành chuỗi rồi tự cắt tại / | Separator phụ thuộc OS/provider | Dùng Path.getName/getParent/getFileName |
| Coi relative path có parent là current directory | Path relative một tên có parent null | Dùng toAbsolutePath() nếu thật sự cần ngữ cảnh cwd |
| Coi normalize() là xác thực đường dẫn vật lý | Chỉ xử lý . và .. về mặt cú pháp | Dùng Files/toRealPath khi cần quan sát thực |
| Dùng String.startsWith để kiểm tra thư mục cha | Dễ nhầm data và database | Dùng Path.startsWith, kèm kiểm soát symbolic link khi cần bảo mật |
| Gọi relativize với hai path không tương thích | Có thể ném IllegalArgumentException | Đưa về cùng provider và kiểu root phù hợp |
| Nghĩ resolveSibling tạo file | Nó chỉ tạo Path mới | Gọi Files API mới tạo file |
| Dựa vào đuôi .txt để xác nhận regular file | Đuôi tên không phải loại file thực tế | Files.isRegularFile và xử lý lỗi mở file |

## 15. Tự kiểm tra kiến thức

**Câu 1.** Với Path.of("a","b","c.txt"), getRoot(), getParent(), getFileName() trả gì?  
**Đáp án:** getRoot() = null; getParent() là a/b (dấu phân cách theo hệ điều hành); getFileName() = c.txt.

**Câu 2.** Với Path biểu diễn / trên Unix, getFileName() và getNameCount() bằng bao nhiêu?  
**Đáp án:** getFileName() = null; getNameCount() = 0. Root không phải một name element.

**Câu 3.** Vì sao Path.of("report.txt").getParent() là null dù chương trình đang chạy trong một thư mục?  
**Đáp án:** Vì bản thân Path tương đối chỉ chứa một tên; nó chưa được chuyển sang ngữ cảnh absolute của thư mục làm việc.

**Câu 4.** Với path a/../b, getParent() và normalize().getParent() có giống nhau không?  
**Đáp án:** Không. getParent() của path gốc là a/..; normalize() thành b, một Path tương đối một tên nên parent null.

**Câu 5.** Trên Unix, /a/b/c.txt có getNameCount() bằng 3 hay 4?  
**Đáp án:** 3: a, b, c.txt. Dấu / là root và không được tính là một name element.

**Câu 6.** Vì sao không dùng split("/") để lấy fileName?  
**Đáp án:** Cú pháp dấu phân cách phụ thuộc file-system provider, và Path API đã có getFileName() xử lý đúng cấu trúc.

**Câu 7.** Tạo Path bằng resolve() có tự tạo file không?  
**Đáp án:** Không. resolve chỉ ghép các thành phần; muốn tạo file phải gọi Files API.

**Câu 8.** Nếu muốn duyệt mọi name element của một Path absolute, iterator có trả root không?  
**Đáp án:** Không. Lấy root riêng qua getRoot() nếu cần hiển thị nó.

**Câu 9.** normalize() đã bảo đảm đường dẫn cuối không đi ra ngoài thư mục cho phép khi có symbolic link chưa?  
**Đáp án:** Chưa. Normalize là lexical; phải có cơ chế kiểm soát file system và symbolic link phù hợp nếu đây là ranh giới bảo mật.

**Câu 10.** getFileName() trả về Path hay String?  
**Đáp án:** Path. Có thể gọi toString() nếu muốn hiển thị tên như văn bản, nhưng không được thay thế mọi thao tác đường dẫn bằng xử lý String.

## 16. Tài liệu Java SE 17

- [Path](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html): getRoot, getParent, getFileName, getNameCount, getName, subpath, iterator, resolve, relativize, normalize.
- [Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html): thao tác thật với file và thư mục, kiểm tra loại và quyền truy cập.
- [FileSystem](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileSystem.html): provider, root directories và dấu phân cách tên.

**Ghi nhớ:** **Root** xác định gốc của cấu trúc đường dẫn, **parent** là path cha về mặt cú pháp, **file name** là thành phần cuối, còn **name element** là những tên có thể truy cập bằng getName(index) hoặc iterator. Java Path giúp phân tích và ghép đường dẫn mà không phụ thuộc cách viết dấu phân cách, nhưng **không tự xác nhận file tồn tại hay đường dẫn an toàn với symbolic link**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="path-003">resolve, resolveSibling và ghép đường dẫn</a>

<details>
<summary>Xem chi tiết</summary>

# resolve, resolveSibling và ghép đường dẫn trong Java Path

`java.nio.file.Path` biểu diễn **đường dẫn** tới một vị trí trong hệ thống tệp. Khi làm việc với nhiều thư mục, tệp và tên do chương trình tạo, nên dùng `Path.resolve(...)`, `Path.resolveSibling(...)` và `Path.of(...)` để ghép các thành phần thay vì tự nối chuỗi dấu `/` hoặc `\`.

Cần ghi nhớ ngay: **tạo hoặc biến đổi `Path` không tạo thư mục, không tạo tệp và không đọc nội dung**. Chỉ khi gọi các thao tác I/O như `Files.createDirectories`, `Files.writeString` hoặc `Files.newInputStream`, chương trình mới thật sự tương tác với dữ liệu tại đường dẫn đó.

## 1. Phân biệt ba thao tác chính

| API | Ý tưởng | Ví dụ đường dẫn dạng tương đối |
| --- | --- | --- |
| `Path.of(first, more...)` | Tạo đường dẫn từ những thành phần được cung cấp | `Path.of("data", "2026", "report.txt")` |
| `base.resolve(child)` | Giải quyết đường dẫn `child` **dựa trên `base`**, xem `base` như một vị trí thư mục trong trường hợp thông thường | `Path.of("data").resolve("report.txt")` |
| `path.resolveSibling(name)` | Thay tên cuối bằng tên anh em, dựa trên **thư mục cha** của `path` | `Path.of("data", "old.txt").resolveSibling("new.txt")` |

Để dễ hình dung:

```text
Path.of("reports", "2026")          -> reports/2026
  .resolve("summary.txt")           -> reports/2026/summary.txt

Path.of("reports", "2026", "old.txt")
  .resolveSibling("new.txt")        -> reports/2026/new.txt
                                      ^ giữ thư mục cha; thay tên cuối
```

Dấu `/` trong sơ đồ chỉ giúp người đọc dễ quan sát. Khi hiển thị đường dẫn trên Windows, dấu phân tách thường là `\`. Không nên viết bài kiểm thử phụ thuộc vào chuỗi dấu phân tách nếu muốn mã chạy trên nhiều hệ điều hành.

## 2. `Path.of(...)` tạo một đối tượng đường dẫn

`Path.of` là factory method có từ Java 11 để tạo `Path`:

```java
import java.nio.file.Path;

Path file = Path.of("workspace", "docs", "guide.txt");
Path directory = Path.of("workspace", "docs");
Path current = Path.of(".");
```

Các đoạn trên là khai báo minh họa; cần đặt trong một class/phương thức. Chúng chỉ tạo đối tượng đường dẫn: **không cần `workspace` hoặc `docs` đang tồn tại**.

`Path.of("workspace", "docs", "guide.txt")` thường dễ đọc và an toàn về dấu phân tách hơn việc tự ghép `"workspace/" + "docs/" + "guide.txt"`. Một số lưu ý:

- Thành phần đường dẫn phải tuân theo cú pháp của **filesystem provider** hiện tại. Tên không hợp lệ có thể khiến `InvalidPathException` được ném ra khi tạo `Path`.
- `Path.of` không kiểm tra quyền đọc, ghi hoặc tính tồn tại của file.
- `Path` là một đối tượng biểu diễn tên/vị trí; đối tượng nhận từ `resolve` hoặc `normalize` là kết quả đường dẫn mới, không tự sửa `Path` ban đầu.
- `Path.of("report.txt")` là một đường dẫn tương đối một thành phần; nó **không tự có thư mục cha** trong đối tượng đó: `getParent() == null`.

Trong Java 7–10, cách phổ biến là `Paths.get(...)`; với Java 17+, `Path.of(...)` thường rõ ràng hơn.

## 3. `resolve(...)` — đi từ thư mục gốc logic xuống vị trí con

Ví dụ:

```java
Path base = Path.of("storage", "documents");
Path report = base.resolve("2026").resolve("report.txt");
```

Kết quả tương đương đường dẫn được cấu thành bằng `Path.of("storage", "documents", "2026", "report.txt")` trên cùng provider.

`resolve` có hai overload thường gặp:

```java
Path child1 = base.resolve("image.png");             // String
Path child2 = base.resolve(Path.of("2026", "data"));  // Path
```

Khi đối số `other` là đường dẫn **tương đối** thông thường, phương thức nối các thành phần vào `base`. `resolve` không đọc nội dung và không kiểm tra file con có tồn tại hay không.

### `base` được hiểu như thư mục

Hãy xét:

```java
Path path = Path.of("images", "photo.jpg");
Path unexpected = path.resolve("thumbnail.jpg");
```

Về mặt ghép tên, kết quả sẽ có các thành phần `images/photo.jpg/thumbnail.jpg`. `resolve` **không biết** `photo.jpg` là file thông thường; nó chỉ coi đường dẫn bên trái là nơi đặt đường dẫn con. Muốn có `images/thumbnail.jpg`, nên dùng `path.resolveSibling("thumbnail.jpg")` hoặc `path.getParent().resolve("thumbnail.jpg")` sau khi kiểm tra có parent.

### Đối số tuyệt đối và đường dẫn có root

Với các đường dẫn tương đối, `resolve` tạo kết quả ghép có thể dự đoán. Với đối số là **đường dẫn tuyệt đối**, quy tắc thường là kết quả chính là đường dẫn tuyệt đối được truyền vào; các trường hợp đường dẫn có root/drive mang tính đặc thù hệ điều hành hoặc provider nên được xử lý cẩn thận.

Đối với ứng dụng **chỉ cho phép tên file tương đối**, hãy xác thực bằng `child.isAbsolute()` trước khi gọi `base.resolve(child)`, đồng thời kiểm soát `..`. Không coi phép `resolve` tự nó là biện pháp chống path traversal.

### Tên rỗng và đường dẫn đặc biệt

`Path` có thể biểu diễn đường dẫn rỗng và các thành phần `.`, `..`. `resolve` không nhất thiết tự bỏ tất cả các thành phần đặc biệt; nếu muốn rút gọn *theo cú pháp*, gọi `normalize()`. Việc chuẩn hóa cú pháp không xác nhận đường dẫn có tồn tại hoặc an toàn trước symbolic links.

## 4. `resolveSibling(...)` — đường dẫn ngang cấp

`resolveSibling` dùng **parent của đường dẫn hiện tại** làm cơ sở để giải quyết đường dẫn được đưa vào:

```java
Path oldFile = Path.of("reports", "2026", "old.csv");
Path newFile = oldFile.resolveSibling("new.csv");
```

Kết quả là đường dẫn có các thành phần `reports/2026/new.csv`, tức **hai file ngang cấp trong thư mục `2026`**. Đây là thao tác ghép tên; nó **không đổi tên file thật**.

Hãy so sánh:

```java
Path original = Path.of("reports", "old.csv");

Path child = original.resolve("new.csv");
// reports/old.csv/new.csv : coi original là vị trí thư mục.

Path sibling = original.resolveSibling("new.csv");
// reports/new.csv : dùng parent của original.
```

`resolveSibling` hữu ích khi cần tạo một đường dẫn “cùng thư mục, tên khác”, ví dụ:

- Tệp gốc `orders.csv` và tệp kết quả `orders-clean.csv`.
- File cấu hình `service.yml` và file sao lưu `service.yml.bak`.
- Tệp tạo tạm trong cùng thư mục để chuẩn bị thay thế.
- Ảnh gốc `photo.jpg` và bản thu nhỏ `photo-thumb.jpg`.

Tuy vậy, thao tác ghi/đổi tên/move thật phải gọi API `Files`; chỉ `resolveSibling` **không tạo hay sửa** file nào.

### Trường hợp không có parent

Với `Path.of("readme.md")`, `getParent()` trả `null`; theo hợp đồng, `resolveSibling("other.md")` sẽ trả đường dẫn `other.md` được cung cấp (thay vì ném `NullPointerException` vì không có parent).

```java
Path file = Path.of("readme.md");
Path renamed = file.resolveSibling("other.md");

System.out.println(renamed.equals(Path.of("other.md"))); // true
```

Do đó, khi muốn ghép với `getParent()` bằng tay, **phải tự xử lý `null`**, còn `resolveSibling` cung cấp hành vi đã định nghĩa cho tình huống này.

## 5. Bảng minh họa đường dẫn con và đường dẫn ngang cấp

Giả sử:

```java
Path base = Path.of("projects", "demo");
Path file = base.resolve("README.md");
```

| Biểu thức | Các thành phần của kết quả | Ý nghĩa |
| --- | --- | --- |
| `base.resolve("src")` | `projects / demo / src` | Thư mục con logic |
| `base.resolve(Path.of("src", "Main.java"))` | `projects / demo / src / Main.java` | File dưới `src` |
| `file.resolveSibling("LICENSE")` | `projects / demo / LICENSE` | Tên khác cùng cấp `README.md` |
| `base.resolveSibling("demo-backup")` | `projects / demo-backup` | Đường dẫn ngang cấp với **thư mục** `demo` |
| `file.resolve("LICENSE")` | `projects / demo / README.md / LICENSE` | Không phải sibling; dễ sai nếu `README.md` là file |

Điểm cần nhấn mạnh: `resolveSibling` không chỉ dùng cho file. Khi gọi trên một **đường dẫn thư mục**, nó tạo đường dẫn ngang cấp với thư mục đó trong cùng parent (theo logic đường dẫn).

## 6. Chương trình Java 17+ đầy đủ: ghép đường dẫn, không tạo file

Ví dụ sau kiểm tra các quan hệ bằng `Path.equals`, `getFileName` và `getParent` để kết quả **không phụ thuộc ký tự phân tách** giữa Windows/Linux. Không có lệnh đọc, ghi hoặc tạo file.

Lưu thành **`PathComposeDemo.java`**:

```java
import java.nio.file.Path;

public class PathComposeDemo {

    public static void main(String[] args) {
        Path base = Path.of("workspace", "reports");
        Path report = base.resolve("2026").resolve("summary.txt");
        Path joined = Path.of(
                "workspace", "reports", "2026", "summary.txt");

        Path sibling = report.resolveSibling("metrics.csv");
        Path directorySibling = base.resolveSibling("archive");
        Path normalized = base.resolve("drafts")
                .resolve("..")
                .resolve("final.txt")
                .normalize();

        System.out.println("Resolve equals Path.of: "
                + report.equals(joined));
        System.out.println("Report name: " + report.getFileName());
        System.out.println("Sibling name: " + sibling.getFileName());
        System.out.println("Sibling keeps parent: "
                + sibling.getParent().equals(report.getParent()));
        System.out.println("Directory sibling name: "
                + directorySibling.getFileName());
        System.out.println("Normalize result matches: "
                + normalized.equals(base.resolve("final.txt")));
        System.out.println("No-parent sibling: "
                + Path.of("note.txt")
                    .resolveSibling("done.txt")
                    .equals(Path.of("done.txt")));
        System.out.println("Original base unchanged: "
                + base.equals(Path.of("workspace", "reports")));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 PathComposeDemo.java
java PathComposeDemo
```

**Stdout chính xác:**

```text
Resolve equals Path.of: true
Report name: summary.txt
Sibling name: metrics.csv
Sibling keeps parent: true
Directory sibling name: archive
Normalize result matches: true
No-parent sibling: true
Original base unchanged: true
```

Giải thích:

1. `Path.of("workspace", "reports", "2026", "summary.txt")` và chuỗi `resolve("2026").resolve("summary.txt")` tạo các thành phần giống nhau.
2. `resolveSibling("metrics.csv")` chỉ đổi thành phần tên cuối, nên parent của sibling vẫn bằng parent của report.
3. `base.resolveSibling("archive")` tạo đường dẫn tới `archive` ngang cấp với `reports`, chứ không đặt `archive` bên trong `reports`.
4. `normalize()` rút gọn đoạn `drafts/..` theo cú pháp; nó không cần file/directory thật.
5. `Path.of("note.txt")` không có parent được ghi trong đường dẫn, nhưng `resolveSibling` vẫn trả đúng tên mới.
6. `Path` ban đầu vẫn giữ nguyên; các phương thức dựng ra đối tượng kết quả.

Ví dụ **không kiểm tra `Files.exists`** vì bài đang minh họa biến đổi đường dẫn, độc lập với file có thực tế tồn tại hay không.

## 7. `normalize()` và `toAbsolutePath()` không mở file

`resolve` thường được dùng cùng `normalize`:

```java
Path input = Path.of("data", "staging", "..", "reports", ".", "a.txt");
Path cleaned = input.normalize();
```

Các thành phần `staging/..` và `.` được xử lý theo quy tắc lexical; kết quả tương ứng có các thành phần `data/reports/a.txt` trong tình huống thông thường. `normalize()` **không thực hiện I/O**. Đặc biệt, với symbolic link, một đường dẫn đã normalize không nhất thiết dẫn tới cùng vị trí thực như đường dẫn trước đó, bởi `..` có thể có ý nghĩa khác sau khi đi qua liên kết.

`toAbsolutePath()` tạo biểu diễn tuyệt đối dựa trên quy tắc provider/thư mục làm việc:

```java
Path relative = Path.of("data", "report.txt");
Path absolute = relative.toAbsolutePath();
```

Nó **không chứng minh file tồn tại**. Cũng không nên dùng kết quả `toAbsolutePath().toString()` làm stdout cố định trong bài học vì máy và thư mục làm việc khác nhau.

`toRealPath(...)` khác ở chỗ thường cần truy cập hệ thống tệp để tìm đường dẫn tồn tại và có thể giải quyết symbolic links theo tùy chọn; phương thức này có thể ném `IOException`. Đừng nhầm ba khái niệm:

- **`normalize()`:** rút gọn đường dẫn theo cú pháp.
- **`toAbsolutePath()`:** tạo đường dẫn tuyệt đối, không yêu cầu file tồn tại.
- **`toRealPath()`:** lấy đường dẫn thực theo khả năng của provider/hệ thống tệp, cần xử lý lỗi I/O.

## 8. Khi nào bắt đầu thao tác hệ thống tệp thật?

Ghép đường dẫn chưa làm xuất hiện file hoặc thư mục mới:

```java
Path root = Path.of("out");
Path output = root.resolve("daily").resolve("report.txt");
```

Muốn **tạo và ghi** thì phải gọi các phương thức `Files`:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

static void writeReport(Path root) throws IOException {
    Path output = root.resolve("daily").resolve("report.txt");

    Files.createDirectories(output.getParent());
    Files.writeString(output, "Kết quả", StandardCharsets.UTF_8);
}
```

Đây là **phương thức minh họa** cần đặt trong class, không phải chương trình độc lập. Trong ví dụ:

1. `resolve` tạo đường dẫn trong bộ nhớ.
2. `Files.createDirectories(...)` mới yêu cầu tạo các thư mục cha cần thiết.
3. `Files.writeString(...)` mới thực sự ghi nội dung; với tùy chọn mặc định, phương thức có thể tạo file mới hoặc **cắt ngắn file đã tồn tại**.

Nếu không được phép ghi đè, có thể truyền tùy chọn phù hợp như `StandardOpenOption.CREATE_NEW` và xử lý `FileAlreadyExistsException`. Không gọi thao tác ghi chỉ để “kiểm tra path”.

Để **đổi tên/move file thực**, cần `Files.move(source, target, ...)`:

```java
Path source = Path.of("reports", "old.csv");
Path target = source.resolveSibling("new.csv");
// Files.move(source, target);  // Chỉ thực thi khi chủ đích di chuyển thật.
```

`resolveSibling` đơn thuần tính toán đích; **`Files.move` mới thực hiện di chuyển**. `Files.move` có thể thất bại do quyền, đích đã tồn tại, khác filesystem hoặc ràng buộc provider; phải xử lý `IOException`.

## 9. Ghép đường dẫn từ đầu vào người dùng: `..` và path traversal

Nếu tên file do người dùng cung cấp, việc gọi `base.resolve(userInput)` **chưa đủ để giới hạn truy cập bên trong `base`**. Ví dụ:

```java
Path base = Path.of("uploads");
Path unsafe = base.resolve("../secrets.txt");
Path normalized = unsafe.normalize();
```

Sau normalize, kết quả có thể **thoát ra ngoài thư mục uploads**. Chỉ vì thao tác đầu tiên là `resolve` không có nghĩa file nằm trong vùng cho phép.

Để kiểm tra đường dẫn về mặt **cú pháp**, một bước thường gặp là:

```java
Path root = Path.of("uploads").toAbsolutePath().normalize();
Path relative = Path.of(userInput); // Chuỗi do người dùng cung cấp.

if (relative.isAbsolute()) {
    throw new IllegalArgumentException("Không chấp nhận đường dẫn tuyệt đối");
}

Path candidate = root.resolve(relative).normalize();
if (!candidate.startsWith(root)) {
    throw new IllegalArgumentException("Đường dẫn vượt khỏi thư mục cho phép");
}
```

Đây là **đoạn minh họa**, yêu cầu biến `userInput` có sẵn và chạy trong class/phương thức. Kiểm tra `startsWith(Path)` so sánh các **thành phần đường dẫn**, khác với `String.startsWith`: đường dẫn `/data-old` không được xem như nằm dưới `/data` chỉ vì trùng tiền tố ký tự.

**Cảnh báo kỹ thuật:** normalize và startsWith mới chỉ kiểm tra lexical path; symbolic link có thể trỏ ra ngoài, và trạng thái thư mục có thể thay đổi giữa lúc kiểm tra và lúc mở (TOCTOU). Nếu đang triển khai hệ thống xử lý đường dẫn không tin cậy, phải tính đến real path, symlink, quyền truy cập, thao tác mở an toàn theo provider và race. Không coi đoạn trên là bộ kiểm tra bảo mật hoàn chỉnh.

Nếu ứng dụng chỉ cần tên file đơn lẻ, có thể giới hạn rõ thành phần tên (không cho dấu phân tách, `.`, `..` hoặc tên ngoài tập hợp cho phép) trước khi ghép. Chính sách phụ thuộc định dạng và nghiệp vụ.

## 10. Những lỗi phổ biến cần tránh

| Sai lầm | Vì sao sai? | Cách đúng |
| --- | --- | --- |
| Tự nối đường dẫn bằng `base + "/" + name` | Dễ sai dấu phân tách/kiểu Path và quy tắc provider | `base.resolve(name)` hoặc `Path.of(parts...)` |
| Dùng `file.resolve("new.txt")` để đổi tên cùng cấp | Sẽ coi `file` là vị trí thư mục | `file.resolveSibling("new.txt")` |
| Gọi `file.getParent().resolve("new.txt")` mà không kiểm tra | `getParent()` có thể `null` với path một thành phần | Dùng `resolveSibling` hoặc kiểm tra parent |
| Nghĩ `resolve` tạo file/thư mục | `Path` chỉ là biểu diễn đường dẫn | `Files.createDirectories`/`Files.write*` mới có tác dụng I/O |
| Nghĩ `resolveSibling` đổi tên file thật | Chỉ tính toán đích mới | `Files.move` để thao tác file thực |
| Nghĩ `normalize` kiểm tra tồn tại | Chỉ rút gọn theo cú pháp | `Files.exists` hoặc I/O thật và xử lý ngoại lệ |
| Nghĩ `toAbsolutePath` là canonical/real path | Chỉ tạo biểu diễn tuyệt đối | `toRealPath` khi cần truy vấn đường dẫn thực |
| Xử lý đầu vào tuyệt đối mà không kiểm tra | Có thể bỏ qua/đổi nghĩa gốc dự tính | Kiểm tra `isAbsolute()` và chính sách tên |
| Dùng `String.startsWith` để kiểm tra thư mục gốc | Tiền tố ký tự không phải ranh giới thành phần | Dùng `Path.startsWith` và biện pháp an toàn bổ sung |
| Chỉ normalize để ngăn truy cập ngoài thư mục | Không xử lý đầy đủ symbolic links và race | Xác thực và mở theo chính sách an toàn của hệ thống |
| So sánh string kết quả với dấu `/` cố định | Kém di động giữa hệ điều hành | So sánh `Path` hoặc các thành phần đường dẫn |
| Giả định đuôi `.txt` chứng minh tồn tại/nội dung | Tên file không xác định trạng thái hay format thật | Dùng đúng API metadata và nội dung khi cần |

## 11. Câu hỏi tự kiểm tra

1. **`Path.of("data").resolve("a.txt")` tạo file `a.txt` chưa?** Chưa; chỉ dựng đường dẫn.
2. **Đang có `Path.of("data", "old.txt")`, muốn đường dẫn `data/new.txt` nên dùng gì?** `resolveSibling("new.txt")`.
3. **`Path.of("data", "old.txt").resolve("new.txt")` có cho `data/new.txt` không?** Không; nó ghép thêm thành phần con vào `old.txt`.
4. **Tại sao `resolveSibling` hữu ích với `Path.of("readme.md")`?** Vì trường hợp parent `null` đã có hành vi: trả đường dẫn được truyền vào.
5. **`normalize()` có tạo file hay kiểm tra symlink thật không?** Không; chỉ biến đổi theo cú pháp.
6. **`toAbsolutePath()` có đảm bảo file tồn tại không?** Không.
7. **Muốn tạo file sau khi resolve phải gọi gì?** Một API có tác dụng I/O như `Files.writeString` hoặc `Files.createFile`; nhớ kiểm tra chính sách ghi đè.
8. **`resolveSibling` có thay `Files.move` được không?** Không; nó chỉ tạo đường dẫn đích.
9. **Có thể an toàn tuyệt đối bằng `root.resolve(input).normalize().startsWith(root)` không?** Không; cần xử lý symbolic links, race, quyền và chính sách truy cập.
10. **Muốn ghép các thành phần đường dẫn không phụ thuộc ký tự `/` hay `\` nên chọn cách nào?** `Path.of("a", "b")` hoặc `base.resolve("b")`.

**Quy tắc thực hành:** dùng `Path.of(...)` để khởi tạo đường dẫn từ các thành phần; dùng `resolve(...)` để ghép **đường dẫn con**; dùng `resolveSibling(...)` để ghép **đường dẫn ngang cấp**. Đó đều là thao tác trên biểu diễn đường dẫn; tác dụng tạo/đọc/ghi/di chuyển thật thuộc về các API I/O như `Files`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="path-004">relativize và điều kiện tương thích</a>

<details>
<summary>Xem chi tiết</summary>

# `Path.relativize()` và điều kiện tương thích trong Java NIO.2

## 1. Mục tiêu và khái niệm cốt lõi

Trong Java NIO.2, `java.nio.file.Path` biểu diễn một đường dẫn trong hệ thống tệp. `Path.relativize(Path other)` tạo một **đường dẫn tương đối từ vị trí mà Path hiện tại biểu diễn tới Path đích**. Nó trả về một đối tượng `Path` mới; không mở, tạo, di chuyển, kiểm tra nội dung hay thay đổi tệp trên đĩa.

Ví dụ, giả sử trong dự án có hai vị trí:

```text
workspace/
├── module/                 ← đường dẫn gốc (base)
└── assets/
    └── logo.png            ← đường dẫn đích (target)
```

Từ `workspace/module` tới `workspace/assets/logo.png`, đường đi tương đối là:

```text
../assets/logo.png
```

Ở đây `..` có nghĩa là đi lên một thành phần của đường dẫn; sau đó đi xuống `assets/logo.png`. `Path.relativize` dùng các thành phần `Path` và quy tắc của filesystem/provider, **không phải phép cắt chuỗi theo dấu `/`**.

Bài học giúp bạn:

- Hiểu kết quả của `base.relativize(target)` và chiều tính đường dẫn (đổi vị trí hai đối số sẽ đổi kết quả).
- Nắm các **điều kiện tương thích**: đường dẫn cùng loại tương đối/tuyệt đối, root phù hợp, provider/filesystem tương thích.
- Dùng `normalize()`, `toAbsolutePath()` và `resolve()` đúng vai trò để tính và đối chiếu đường dẫn.
- Phân biệt thao tác **thuần cú pháp** với đường dẫn thật trên filesystem, symbolic link và `toRealPath()`.
- Xử lý `IllegalArgumentException`/`ProviderMismatchException` thay vì tự sửa chuỗi hoặc giả định thao tác luôn thành công.
- Áp dụng vào xây dựng đường dẫn tương đối cho cấu hình, liên kết giữa thư mục, cấu trúc dự án và đường dẫn file nhưng không dùng nó thay cơ chế bảo mật.

**Ghi nhớ ngay từ đầu:** Trong `base.relativize(target)`, đối tượng gọi là **base**, đối số là **target**. Đây không phải lệnh biến `target` thành absolute, cũng không phải lệnh tạo file trên đĩa.

## 2. Cú pháp và cơ chế tính đường dẫn

Khai báo:

```java
Path relative = base.relativize(target);
```

Kết quả thường là đường dẫn **không có root**, gồm các thành phần `..` để đi lên từ base và các tên còn lại để đi xuống tới target.

Hình dung các bước cho đường dẫn tương thích:

1. Xét các thành phần chung ở đầu của base và target.
2. Với mỗi thành phần còn lại ở **base**, thêm một `..` vào kết quả.
3. Sau đó thêm các thành phần còn lại ở **target**.
4. Trả về một `Path` tương đối dùng dấu phân cách phù hợp với provider.

Ví dụ về thành phần (dùng dấu `/` để minh họa):

| Base | Target | Kết quả tương đối minh họa |
| --- | --- | --- |
| `project` | `project/src/Main.java` | `src/Main.java` |
| `project/src` | `project/test/Test.java` | `../test/Test.java` |
| `a/b/c` | `a/b/c` | Đường dẫn rỗng (empty path) |
| `a/b/c` | `a/b` | `..` |
| `a/b/c` | `a/x.txt` | `../../x.txt` |
| `docs` | `images/logo.png` | `../images/logo.png` |

Bảng giả định cả hai đối số **cùng provider, cùng loại relative** và các thành phần đã ở dạng thích hợp. Java thực hiện theo quy tắc của `Path` provider nên khi xuất chuỗi trên Windows có thể thấy `\` thay cho `/`.

### 2.1. Cùng đường dẫn thì sao?

Nếu `base` và `target` biểu diễn cùng một đường dẫn theo phép so sánh thích hợp, `base.relativize(target)` tạo một **empty path**, thường có chuỗi `""`. Không phải `null`, và không nên giả định `Path.getNameCount() == 0` ở tất cả provider cho empty path. Cần phân biệt:

- `Path.of("")`: một đối tượng Path hợp lệ biểu diễn chuỗi đường dẫn rỗng.
- `null`: không phải Path; truyền vào `relativize` sẽ không phải thao tác hợp lệ.
- `"."`: thành phần thư mục hiện tại trong cú pháp đường dẫn; `Path.of(".")` không nhất thiết bằng `Path.of("")` khi chưa normalize.

### 2.2. `relativize` là phép toán một chiều

Không được nhầm:

```java
Path a = Path.of("project", "src");
Path b = Path.of("project", "test", "Test.java");

Path fromSrcToTest = a.relativize(b); // ../test/Test.java
Path fromTestToSrc = b.relativize(a); // ../..
```

Hai kết quả khác nhau vì base đã đổi. Trong ví dụ thứ hai, base là **đường dẫn biểu diễn chính file `Test.java`**, không phải thư mục cha `test`. Nếu muốn đi **từ thư mục chứa file** thay vì từ đường dẫn file, hãy dùng `b.getParent().relativize(a)`; lúc đó kết quả chỉ cần đi lên một cấp (`..`).

Đây là điểm quan trọng: `relativize` chỉ tính toán từ **Path thành phần**, không tự biết đường dẫn trỏ tới tệp hay thư mục và không tự “bỏ tên file cuối”.

## 3. Điều kiện tương thích quan trọng nhất

### 3.1. Cả hai cùng relative hoặc cả hai cùng absolute

Một cặp tương thích thông thường:

```java
Path base = Path.of("project", "src");
Path target = Path.of("project", "docs", "guide.md");
Path result = base.relativize(target); // ../docs/guide.md
```

Cả hai đều **relative** theo cùng filesystem. Ngược lại:

```java
Path base = Path.of("project", "src");   // relative
Path target = base.toAbsolutePath();      // absolute

// Không hợp lệ: base.relativize(target) ném IllegalArgumentException
```

Theo hợp đồng `Path.relativize`, khi một đường dẫn absolute còn đường kia relative, phương thức ném **`IllegalArgumentException`**. Java không tự đoán root cho một phía trong phép tính này.

Cách chuẩn bị hai đối số khi ngữ cảnh thực sự cho phép:

```java
Path relativeBase = Path.of("project", "src");
Path relativeTarget = Path.of("project", "docs", "guide.md");

Path absoluteBase = relativeBase.toAbsolutePath().normalize();
Path absoluteTarget = relativeTarget.toAbsolutePath().normalize();

Path route = absoluteBase.relativize(absoluteTarget);
```

**Chú ý:** hai `toAbsolutePath()` đều lấy ngữ cảnh thư mục làm việc của tiến trình; việc chuyển đổi phải phản ánh cùng một hệ quy chiếu thực tế. Chuyển mọi thứ thành absolute **không tự giải quyết** vấn đề root khác nhau hoặc provider khác nhau.

### 3.2. Root phải tương thích

Với đường dẫn absolute, cả hai thường cần có **root có thể relativize cùng nhau**. Trên Linux/Unix, ví dụ `/home/a` và `/var/data` đều có root `/` nên provider mặc định thường tính đường dẫn từ nơi này tới nơi kia.

Trên Windows, một đường dẫn absolute ở ổ `C:\` và một đường dẫn ở `D:\` là hai root khác nhau; thường không thể biểu diễn đích chỉ bằng các bước `..` tương đối từ base. Kết quả có thể là `IllegalArgumentException`. Đường dẫn UNC (network shares) cũng có đặc điểm root riêng.

Ví dụ mô tả, **không phải mã chạy portable**:

```text
Base:    C:\projects\app
Target:  D:\archive\image.png
Result:  không có đường dẫn tương đối phù hợp theo cơ chế relativize thông thường
```

Không nên xây logic bằng cách bắt exception rồi xóa ký tự ổ đĩa bằng `substring`; như vậy sẽ đánh mất ý nghĩa root và tạo đường dẫn sai. Nếu không thể relativize, hãy giữ absolute path hoặc từ chối tác vụ theo hợp đồng ứng dụng.

### 3.3. Provider và filesystem phải tương thích

`Path` không nhất thiết đại diện cho filesystem mặc định trên ổ đĩa. Java có thể dùng những **FileSystemProvider** khác nhau, ví dụ default filesystem, ZIP filesystem hay một provider của thư viện bên ngoài.

Một `Path` đến từ default filesystem và một `Path` đến từ ZIP filesystem **không thể được coi là hai nhánh trong cùng một cây path** chỉ vì `toString()` của chúng giống nhau.

Trong trường hợp provider không tương thích, `relativize` có thể ném **`ProviderMismatchException`** (một lớp con của `IllegalArgumentException`), hoặc có điều kiện tương thích khác theo provider. Không được trộn đối tượng `Path` khác provider rồi hy vọng `normalize()` hoặc `toAbsolutePath()` biến chúng thành cùng filesystem.

### 3.4. Bảng quyết định nhanh

| Base | Target | Khả năng |
| --- | --- | --- |
| Relative A | Relative B trên cùng provider | Thường relativize được |
| Absolute A | Absolute B cùng root/provider phù hợp | Thường relativize được |
| Relative A | Absolute B | **Không tương thích**, ném `IllegalArgumentException` |
| Absolute A | Relative B | **Không tương thích**, ném `IllegalArgumentException` |
| Absolute C: | Absolute D: trên Windows | Root không tương thích; thường không relativize được |
| Default filesystem | ZIP filesystem | Provider/filesystem khác; không giả định có thể relativize |
| Cùng pathname | Cùng pathname | Empty path |
| Hai Path có `.`, `..` chưa normalize | Cùng loại/root | Có thể tính nhưng kết quả có thể khó hiểu; nên xác định chiến lược normalize |

Không có một phép “ép sang relative” áp dụng cho mọi cặp `Path`. Cần xác định **hệ quy chiếu**: cùng filesystem, cùng root, cùng semantics về đường dẫn.

## 4. Ví dụ Java 17+ hoàn chỉnh, chạy đa nền tảng

Ví dụ dưới đây tránh in chuỗi có dấu phân cách phụ thuộc hệ điều hành. Thay vào đó, in số thành phần, kiểm tra ký tự `..` và chứng minh quan hệ `resolve`/`normalize`. Chương trình **không cần tạo file thật**.

Lưu thành **`PathRelativizeDemo.java`**:

```java
import java.nio.file.Path;

public class PathRelativizeDemo {
    public static void main(String[] args) {
        Path base = Path.of("workspace", "module");
        Path target = Path.of("workspace", "assets", "logo.png");

        Path route = base.relativize(target);

        System.out.println("Base relative: " + !base.isAbsolute());
        System.out.println("Target relative: " + !target.isAbsolute());
        System.out.println("Route relative: " + !route.isAbsolute());
        System.out.println("Route parts: " + route.getNameCount());
        System.out.println("Starts with ..: "
                + route.getName(0).toString().equals(".."));
        System.out.println("Reconstructed: "
                + base.resolve(route).normalize()
                          .equals(target.normalize()));

        Path same = base.relativize(base);
        System.out.println("Same gives empty: "
                + same.toString().isEmpty());

        try {
            base.relativize(target.toAbsolutePath());
            System.out.println("Mixed types accepted");
        } catch (IllegalArgumentException error) {
            System.out.println("Mixed types rejected");
        }
    }
}
```

Biên dịch/chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 PathRelativizeDemo.java
java PathRelativizeDemo
```

**Stdout trong môi trường filesystem mặc định thông thường:**

```text
Base relative: true
Target relative: true
Route relative: true
Route parts: 3
Starts with ..: true
Reconstructed: true
Same gives empty: true
Mixed types rejected
```

**Giải thích theo thứ tự:**

1. `Path.of("workspace", "module")` và `Path.of("workspace", "assets", "logo.png")` đều relative.
2. Hai đường dẫn có thành phần chung `workspace`. Từ `module` đi lên bằng `..` rồi đi xuống `assets/logo.png`, vì vậy route có ba thành phần.
3. `route.getName(0)` là `".."`. Kiểm thử dựa trên **thành phần Path**, không dựa trên dấu phân cách `/` hay `\`.
4. `base.resolve(route).normalize()` tạo lại đường dẫn lexical tương đương `target.normalize()` trong ví dụ.
5. Khi gọi `base.relativize(base)`, kết quả biểu diễn đường dẫn rỗng.
6. Khi gọi `base.relativize(target.toAbsolutePath())`, một bên relative còn bên kia absolute; nhánh bắt `IllegalArgumentException` được thực thi.

Ví dụ không chứng minh file `logo.png` tồn tại. `Path.relativize` và `normalize()` trong tình huống này chỉ thao tác trên thành phần tên. Nếu không có `workspace` trên ổ đĩa, mọi dòng stdout về cú pháp vẫn có ý nghĩa như trên.

### 4.1. Tránh dùng chuỗi separator cố định trong kiểm thử

Trên Unix, `route.toString()` thường trông như `../assets/logo.png`, còn trên Windows là `..\assets\logo.png`. Nếu muốn bài kiểm thử chạy cả hai nền tảng, có thể so sánh với `Path.of("..", "assets", "logo.png")`:

```java
// Minh họa bổ sung (đặt sau khi route được tạo):
boolean equivalent =
        route.equals(Path.of("..", "assets", "logo.png"));
System.out.println(equivalent);
```

Đây là **đoạn minh họa**; cần biến `route` thuộc ví dụ chạy đầy đủ. Kết quả là `true` theo provider mặc định khi đầu vào đúng như trên.

## 5. `normalize()` và thời điểm nên áp dụng

### 5.1. Chuẩn hóa cú pháp trước khi tính

`Path.normalize()` loại bỏ các thành phần dư thừa như `.` và các cặp tên/`..` khi có thể. Phương thức này **không truy cập filesystem** và không yêu cầu file tồn tại.

Ví dụ:

```java
Path base = Path.of("root", "docs", "..", "src");
Path target = Path.of("root", "assets", ".", "icon.svg");

Path normalizedBase = base.normalize();     // root/src
Path normalizedTarget = target.normalize(); // root/assets/icon.svg

Path route = normalizedBase.relativize(normalizedTarget);
// ../assets/icon.svg (hiển thị theo separator của nền tảng)
```

Những dòng trên là minh họa cho cú pháp API, không phải class hoàn chỉnh. Trong tình huống cấu trúc dự án và đường dẫn thuần lexical, chiến lược **normalize hai phía rồi relativize** giúp kết quả dễ hiểu và dự đoán hơn.

Cần áp dụng **cùng chiến lược** cho cả base và target. Nếu một bên được normalize còn bên kia chứa thành phần `..` thừa, kết quả có thể khiến người đọc hiểu sai.

### 5.2. Không được nhầm `normalize` với canonical/real path

`normalize()` chỉ sửa đường dẫn về mặt cú pháp, không hỏi filesystem xem thành phần `link` có phải symbolic link không.

Ví dụ khái niệm (giả sử `link` là symbolic link trỏ tới một vị trí khác):

```text
/data/link/../report.txt
```

`normalize()` có thể rút gọn thành `/data/report.txt`, nhưng khi OS thực sự đi qua `link` là symlink, semantics của `..` có thể khác. Vì thế quy tắc “bỏ một tên rồi một `..`” **không phải chứng minh** đường dẫn cũ và mới trỏ tới cùng file vật lý.

- `Path.normalize()`: xử lý cú pháp; không yêu cầu tồn tại.
- `Path.toAbsolutePath()`: tạo absolute path; không phải bước giải quyết symlink.
- `Path.toRealPath()`: thực hiện thao tác filesystem để lấy đường dẫn thực, mặc định yêu cầu tồn tại và giải quyết symbolic link, có thể ném `IOException`.
- `File.getCanonicalPath()`: API truyền thống với semantics canonical pathname; không đồng nhất hoàn toàn với `toRealPath()`, nhất là với path chưa tồn tại.

**Lựa chọn:** Nếu mục tiêu là tạo tên đường dẫn relative cho tệp nguồn/dự án, hãy xem xét normalize. Nếu phải xác định đường dẫn thực của **file đang tồn tại**, cân nhắc `toRealPath()` và xử lý lỗi/tính thay đổi đồng thời.

## 6. `resolve()` và `relativize()`: quan hệ hai chiều

### 6.1. `resolve` làm gì?

`base.resolve(other)` ghép `other` vào `base` khi `other` là relative. Nếu `other` là absolute, theo hợp đồng `Path.resolve` của provider mặc định, kết quả thường là chính `other`; với provider khác, chi tiết có thể phụ thuộc triển khai. Không được dựa vào `resolve` để âm thầm xác nhận rằng `other` an toàn hoặc nằm trong base.

Ví dụ thuần lexical:

```java
Path base = Path.of("app", "src");
Path other = Path.of("..", "assets", "logo.png");
Path combined = base.resolve(other).normalize();
// app/assets/logo.png
```

`resolve` **không đọc/ghi file**, và việc ghép đường dẫn không tự tạo thư mục.

### 6.2. Kiểm tra quan hệ reconstruction

Với hai đường dẫn tương thích và đã normalize, có thể kiểm tra:

```java
Path relative = base.relativize(target);
Path reconstructed = base.resolve(relative).normalize();
boolean matches = reconstructed.equals(target.normalize());
```

Trong tình huống chuẩn của ví dụ, `matches` là `true`. Đây là công cụ hữu ích để phát hiện bug **đổi chiều** hoặc xử lý `..` không đúng.

Nhưng đây là phép đồng nhất **đường dẫn lexical**. Nó không chứng minh file đã tồn tại, kiểm tra quyền, đối chiếu inode/file identity hoặc ngăn symlink thay đổi. Với ngữ cảnh có root phức tạp, path chưa normalize và provider khác nhau, cần kiểm tra hợp đồng provider thay vì suy diễn từ một công thức cho mọi trường hợp.

### 6.3. `relativize` không phải `resolve` với dấu âm

Không nên coi `relativize` là “phép trừ đường dẫn” dựa trên `String.replace`. Đường dẫn có hệ thống root, separator, filesystem provider và có thể có thành phần đặc biệt. `Path.relativize` tính theo cấu trúc Path, còn phép cắt chuỗi bỏ mất các thông tin đó.

## 7. Relative và absolute: quy trình chọn dùng an toàn, dễ bảo trì

Trong một ứng dụng, trước khi gọi `relativize` hãy xác định **ngữ cảnh gốc của cả hai đường dẫn**:

1. Nếu cả hai là relative và được hiểu từ **cùng working directory/anchor**, có thể normalize rồi relativize.
2. Nếu cả hai là absolute, kiểm tra chúng thuộc root/provider tương thích và relativize theo hệ tọa độ đó.
3. Nếu một absolute, một relative, **đừng tự đoán**. Xác định xem relative thật sự được hiểu từ working directory, từ một root dự án hay từ thư mục cha cụ thể, rồi convert *có chủ đích*.
4. Nếu không thể xác định cùng hệ quy chiếu, xử lý bằng thông báo lỗi, giữ absolute path hoặc thay đổi dữ liệu đầu vào.
5. Nếu Path thuộc provider đặc biệt (ZIP, filesystem ảo), bảo đảm cả hai cùng provider/filesystem thích hợp thay vì đổi qua `toString()` để ghép thủ công.

### 7.1. Trường hợp cần cố định root ứng dụng

Giả sử cấu hình của dự án dùng các đường dẫn tương đối dưới root xác định, hãy resolve cả hai **từ cùng root**:

```java
// Minh họa; projectRoot là Path absolute do ứng dụng cung cấp.
Path base = projectRoot.resolve("src").normalize();
Path target = projectRoot.resolve("assets/logo.png").normalize();
Path relative = base.relativize(target);
```

Ví dụ này **không phải chương trình độc lập** vì chưa khai báo `projectRoot`. Trong ứng dụng thật, `projectRoot` nên được xác định rõ từ cấu hình/đường dẫn tin cậy. Nếu `projectRoot` chỉ là relative, cần thống nhất việc chuyển thành absolute trước khi ghép.

### 7.2. Không suy luận working directory từ vị trí file nguồn

Một lỗi thường gặp: chương trình dùng `Path.of("src")` rồi giả định đó là thư mục `src` trong repository. Khi chạy bằng IDE, service, script hoặc container, working directory có thể khác. `toAbsolutePath()` chỉ giải quyết theo ngữ cảnh tiến trình, không biết module nào được lập trình viên “muốn dùng”.

Nếu ứng dụng cần đường dẫn độc lập với working directory, hãy dùng cấu hình absolute hoặc xác định root rõ ràng tại thời điểm khởi tạo ứng dụng.

## 8. Root và filesystem provider trong hệ thống thực tế

### 8.1. FileSystem mặc định

`Path.of(...)` thường tạo đường dẫn thuộc default provider của hệ điều hành hiện tại. Trên hệ Unix, absolute paths hay bắt đầu từ `/`. Trên Windows, có thể có root của ổ đĩa hoặc UNC.

Với hai đường dẫn absolute cùng root, phép tính tương đối có ý nghĩa. Với hai ổ đĩa không cùng root, có thể không tồn tại một relative path thuần `..` nối giữa chúng.

Khi kiểm tra khả năng tương thích, có thể xem:

```java
// Minh họa với các Path đã tồn tại trong mã.
boolean sameFileSystem = base.getFileSystem()
        .equals(target.getFileSystem());
boolean sameKind = base.isAbsolute() == target.isAbsolute();
```

Hai boolean này là **chỉ báo ban đầu**, không thay thế hợp đồng `relativize`. Ví dụ, trên Windows cùng FileSystem mặc định vẫn có thể có hai drive root khác nhau. Khi thực sự tính, gọi `relativize` và xử lý `IllegalArgumentException` nếu đầu vào không tương thích.

### 8.2. Provider riêng như ZIP filesystem

Một Path bên trong ZIP filesystem do provider quản lý không được xem là con của một directory Path mặc định chỉ vì tên hai path cùng chứa `/docs`. Provider có thể xác định cú pháp root, cách so sánh và cách relativize khác nhau.

Không nên dùng `Path.of(zipPath.toString())` để “ép” path ZIP sang default filesystem rồi coi nó vẫn tham chiếu tới cùng file. Cách đó chỉ tạo **một Path mới có chuỗi giống hoặc tương tự**, không tự giữ ngữ nghĩa filesystem ban đầu.

Nếu ứng dụng cần sao chép dữ liệu giữa hai provider, hãy dùng API mở/đọc/ghi trên từng provider và xác định đích riêng, không yêu cầu `relativize` nối hai cây filesystem khác nhau.

### 8.3. Xử lý ngoại lệ chính xác

Hợp đồng `relativize` có thể ném:

- `IllegalArgumentException` khi hai path không thể relativize, ví dụ absolute/relative trộn hoặc root không tương thích.
- `ProviderMismatchException` khi các Path do provider không phù hợp quản lý; đây là kiểu con của `IllegalArgumentException` trong Java.
- `NullPointerException` nếu truyền `null` vào phương thức.

Lưu ý `Path.relativize` không khai báo `throws IOException` vì nó chủ yếu thao tác cấu trúc Path, khác với `toRealPath()` có thể truy cập filesystem.

Nếu cần thông báo riêng cho provider mismatch, bắt `ProviderMismatchException` **trước** `IllegalArgumentException` trong nhiều nhánh catch; không bắt ngược thứ tự khi lớp con đã bị catch bởi lớp cha.

Không nên viết `catch (Exception ignored)` rồi trả một chuỗi “relative” giả để che việc hai hệ quy chiếu không tương thích.

## 9. Sai lầm thường gặp và cách sửa

### 9.1. Đổi chiều giữa base và target

Sai mục tiêu: muốn đường từ `src` đến `tests` nhưng lại gọi `tests.relativize(src)`. Hãy đọc câu “**từ object gọi phương thức đến đối số**”. Trong code, đặt tên rõ `from`/`to`:

```java
Path from = Path.of("project", "src");
Path to = Path.of("project", "tests");
Path navigation = from.relativize(to); // ../tests
```

### 9.2. Dùng một absolute và một relative

Không thể gọi thành công theo hợp đồng mặc định. Trước khi convert, phải biết relative được neo ở đâu. `toAbsolutePath()` chỉ là một cách dựa trên working directory; không phải lựa chọn đúng cho mọi dữ liệu.

### 9.3. Suy luận kết quả không phụ thuộc nền tảng

Chuỗi `relative.toString()` có thể dùng `/` hoặc `\`. Hãy so sánh `Path`/tên thành phần, không hard-code separator trong test đa hệ điều hành.

### 9.4. Quên xử lý `..` trong chính input

Với đường dẫn có `. / ..`, kết quả có thể khó đọc hoặc không phù hợp với dự kiến. Khi mục tiêu là phép tính lexical, cân nhắc `normalize()` trước phép tính. Không dùng normalize làm bằng chứng path trỏ cùng file nếu có symlink.

### 9.5. Coi relative path là bảo đảm file tồn tại

`relativize` không gọi `Files.exists()`, không tạo file và không mở handle. Đường dẫn đích có thể không có thật. Khi dùng kết quả để đọc file, vẫn phải xử lý `IOException`.

### 9.6. So sánh path bằng chuỗi tiền tố

Nếu ứng dụng muốn kiểm tra xem một file nằm dưới một thư mục, gọi `string.startsWith("/data")` không tương đương với kiểm tra thành phần path vì `/database` cũng có tiền tố `/data`. Sau khi chọn cùng filesystem và normalize phù hợp, `candidate.startsWith(root)` trên **Path** là kiểm tra lexical về thành phần, nhưng chưa xử lý symlink hoặc các thay đổi trên đĩa.

### 9.7. Nhầm `relativize` là công cụ bảo mật

Một chương trình có thể tạo một relative path trỏ **ra ngoài** root thông qua `..`. Kết quả tương đối không đồng nghĩa “được phép truy cập”. Với đường dẫn người dùng nhập, cần giới hạn các thành phần cho phép, root tin cậy, quyền truy cập, symlink policy và biện pháp chống thay đổi filesystem đồng thời.

### 9.8. Xử lý tất cả đường dẫn như cùng provider

Hai `Path` trông giống nhau khi in ra có thể thuộc hai filesystem khác nhau. So sánh `getFileSystem()`/provider khi cần và thiết kế rõ nơi dữ liệu được lưu trữ.

## 10. Symlink, `toRealPath()` và vấn đề bảo mật

### 10.1. Lexical route không đồng nghĩa route vật lý

Giả sử `/project/assets` là symlink đến `/mnt/shared-assets`. `Path.of("/project/src").relativize(Path.of("/project/assets/logo.png"))` tính toán bằng các thành phần `Path`, không mở symlink để hỏi file đích thực ở đâu.

Nếu cần tương quan giữa **hai file thực sự đang tồn tại**, có thể lấy real paths:

```java
// Minh họa: base và target là Path tới các tài nguyên đang tồn tại.
Path realBase = base.toRealPath();
Path realTarget = target.toRealPath();
Path navigation = realBase.relativize(realTarget);
```

Cách này yêu cầu file/dir tồn tại và cùng provider/root có thể relativize, có thể ném `IOException`. Đường dẫn sau khi giải quyết symlink có thể nằm ngoài cây thư mục ban đầu. `toRealPath()` còn có tùy chọn `LinkOption.NOFOLLOW_LINKS` nếu yêu cầu là *không theo* liên kết; hãy lựa chọn theo threat model, không dùng như nghi thức mặc định.

### 10.2. Kiểm tra rồi mới dùng có thể gặp TOCTOU

Nếu tiến trình khác có quyền thay đổi symlink/directory sau khi bạn kiểm tra, đường dẫn thực có thể đổi trước lúc đọc file (time-of-check-to-time-of-use). Vì vậy, kể cả `toRealPath()` và `relativize` sau đó **không tự khóa** đối tượng file.

Với yêu cầu bảo mật cao, hãy dùng API mở tài nguyên bằng cơ chế quản lý directory/file handle phù hợp với provider, phân quyền filesystem, và cân nhắc `SecureDirectoryStream` khi được hỗ trợ. Đừng cố dùng một phép tính chuỗi/Path thuần lexical để chứng minh không thể có race.

## 11. Checklist chọn và sử dụng đúng

| Câu hỏi | Cách quyết định |
| --- | --- |
| Muốn đường đi từ A đến B? | `A.relativize(B)` |
| Hai Path khác loại absolute/relative? | Xác định anchor rồi chuyển cả hai về hệ tọa độ chung; nếu không, báo lỗi |
| Hai absolute path khác root? | Đừng giả định có relative route; xử lý `IllegalArgumentException` |
| Hai Path khác provider? | Không ép chuỗi để trộn; xử lý theo từng filesystem |
| Đường dẫn có `.`/`..`? | Xem xét normalize **cả hai** nếu mục tiêu là cú pháp |
| Cần ghép base và relative route? | `base.resolve(route)`; normalize nếu cần |
| Cần xác minh kết quả trong test? | So sánh `Path` thành phần hoặc `resolve(...).normalize()` với target normalized |
| Cần kiểm tra sự tồn tại/tài nguyên thật? | Dùng `Files`/`toRealPath()`; xử lý `IOException` |
| Cần ngăn truy cập ra ngoài root? | Thiết kế kiểm soát containment + symlink + race, không dựa đơn lẻ vào `relativize` |
| Chạy đa nền tảng? | Tránh kỳ vọng chuỗi `/` hay `\` cố định |

## 12. Bài tập tự đánh giá

1. **Cơ bản:** tính `Path.of("a", "b").relativize(Path.of("a", "c", "d.txt"))` và trình bày các thành phần kết quả.
2. **Đổi chiều:** tính đường ngược lại. Vì sao số bước `..` khác khi base là path tới một file?
3. **Tương thích:** giải thích vì sao `Path.of("docs").relativize(Path.of("docs").toAbsolutePath())` ném `IllegalArgumentException`.
4. **Đồng nhất:** gọi `p.relativize(p)`, in `toString()`, nhận xét về empty path.
5. **Normalize:** với `base = "a/b/../src"`, `target = "a/docs/./intro.md"`, normalize cả hai và relativize; kiểm tra `base.normalize().resolve(route).normalize()`.
6. **Windows roots:** vì sao `C:\work` và `D:\backup` thường không thể relativize? Vì sao xử lý bằng substring ổ đĩa là sai?
7. **Provider:** nêu vấn đề khi trộn Path từ ZIP filesystem với Path từ default filesystem.
8. **Symlink:** so sánh ý nghĩa của `normalize()` và `toRealPath()` khi một thư mục trung gian là symbolic link.
9. **Thực hành:** viết hàm trả về `Optional<Path>` cho một cặp Path tương thích, còn cặp không tương thích trả `Optional.empty()` hoặc báo lỗi có ngữ cảnh. Chỉ bắt lỗi liên quan; không âm thầm đổi root.
10. **Thiết kế an toàn:** người dùng đưa tên file cần tải xuống; giải thích tại sao `relativize` không phải bước xác thực được phép truy cập vào root thư mục.

## 13. Tóm tắt kiến thức

- **`base.relativize(target)`** tạo một đường dẫn tương đối **từ base đến target**.
- **Tính tương thích là bắt buộc:** cả hai relative hoặc cả hai absolute; absolute paths cần root thích hợp và cả hai Path phải thuộc môi trường filesystem/provider tương thích.
- **`normalize()`** giúp đơn giản hóa cú pháp, **`toAbsolutePath()`** định vị từ ngữ cảnh làm việc, **`resolve()`** ghép relative path và **`toRealPath()`** yêu cầu tra cứu filesystem khi cần đường dẫn thực.
- `relativize` **không mở tệp, không bảo đảm file tồn tại, không truy cập symlink để xác minh**, không tự làm công cụ path security.
- Để tránh lỗi, hãy xác định **chiều from → to**, thống nhất hệ quy chiếu, viết test đối chiếu bằng `Path` và xử lý rõ ràng các trường hợp không tương thích.

**Quy tắc thực hành:** Chọn hai `Path` cùng hệ tọa độ, xác định cần tính toán lexical hay cần tương quan với tài nguyên thật, rồi mới dùng `relativize`. Không thay thế việc kiểm tra và bảo vệ filesystem bằng một phép biến đổi đường dẫn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="path-005">normalize chỉ xử lý cú pháp đường dẫn</a>

<details>
<summary>Xem chi tiết</summary>

# Path.normalize() chỉ xử lý cú pháp đường dẫn

## 1. Vì sao cần hiểu đúng normalize()?

Trong Java NIO.2, `java.nio.file.Path` biểu diễn **đường dẫn** đến một vị trí trong hệ thống tệp. Một đường dẫn có thể chứa các phần tên bình thường cùng những thành phần đặc biệt như `.` (thư mục hiện tại) và `..` (thư mục cha). Phương thức `Path.normalize()` giúp rút gọn các thành phần dư thừa theo **quy tắc cú pháp đường dẫn**.

Điểm quan trọng nhất:

> **`Path.normalize()` là phép biến đổi từ một đối tượng Path thành một Path khác theo cú pháp; nó không mở file, không tra cứu sự tồn tại của file, không kiểm tra quyền và không xác minh symbolic link trỏ đến đâu.**

Ví dụ đường dẫn tương đối:

```text
Đầu vào:    data/reports/../images/./logo.png
            └───────┬──────┘       │
                  reports/..        .
                    loại bỏ     loại bỏ
Kết quả:    data/images/logo.png
```

Nếu ta chỉ cần làm sạch biểu diễn đường dẫn cho mục đích thao tác cú pháp, `normalize()` rất hữu ích. Nhưng nếu muốn biết hai đường dẫn thật sự trỏ tới **cùng một file**, thư mục đích thật là đâu hoặc một đường dẫn có nằm an toàn trong thư mục được phép không, phải dùng các API và biện pháp khác.

Trong cùng module Java I/O, hãy phân biệt ba việc:

1. **Lexical normalization:** chuẩn hóa phần tên trong `Path` bằng `normalize()`.
2. **Absolute path conversion:** tạo biểu diễn tuyệt đối bằng `toAbsolutePath()`.
3. **Filesystem resolution:** truy cập hệ thống tệp để xác định đường dẫn thực, tồn tại và liên kết, ví dụ `toRealPath()` hoặc `Files.isSameFile()`.

Đây là ba ý nghĩa khác nhau dù chúng thường xuất hiện gần nhau trong code.

## 2. Path là một biểu diễn đường dẫn

`Path` là interface thuộc `java.nio.file`, được tạo thông dụng bằng `Path.of(...)`. Một `Path` có thể:

- Là **tương đối**: `docs/manual.txt`.
- Là **tuyệt đối**: ví dụ `/var/data/manual.txt` trên hệ thống Unix hoặc đường dẫn có ký hiệu ổ đĩa thích hợp trên Windows.
- Có dấu phân cách và đặc điểm được quyết định bởi **filesystem provider**.
- Chứa các name element như `docs`, `.`, `..`, `manual.txt`.

Các thao tác thuần về `Path` như `resolve()`, `getFileName()`, `getParent()`, `startsWith()`, `endsWith()`, `relativize()`, và `normalize()` có thể thực hiện trên biểu diễn đường dẫn. Không cần có tệp tương ứng đang tồn tại để gọi `normalize()`.

Ví dụ:

```java
Path path = Path.of("data", "reports", "..", "images", ".", "logo.png");
Path normalized = path.normalize();
```

Đoạn minh họa cần `import java.nio.file.Path;`. `normalized` có những name element `data`, `images`, `logo.png`. Hình thức chuỗi khi in có thể dùng `/` hoặc `\\` tùy provider và hệ điều hành, vì vậy ví dụ kiểm thử nên so sánh **`Path` với `Path`** thay vì kỳ vọng literal đường dẫn Unix trên Windows.

## 3. normalize() xử lý chính xác điều gì?

Theo hợp đồng `Path.normalize()` trong Java, kết quả loại bỏ các name element dư thừa như `.` và những cặp `name/..` có thể triệt tiêu theo cú pháp. Một số nguyên tắc:

- Một thành phần `.` thường bị loại bỏ.
- Một thành phần tên bình thường ngay trước `..` có thể bị triệt tiêu cùng `..`.
- Một `..` ở đầu một đường dẫn tương đối mà không có tên trước nó để triệt tiêu thường **được giữ lại**.
- Phần root của đường dẫn tuyệt đối được giữ theo quy tắc provider; việc gặp `..` ở sát root không thể suy ra rằng có thể đi lên vô hạn.
- Kết quả vẫn là một `Path` theo cùng filesystem provider; phương thức không bắt buộc trả đúng cùng object instance.
- Gọi `normalize()` trên kết quả một lần nữa không cần làm thay đổi biểu diễn đã được chuẩn hóa (tính idempotent về giá trị).

Một số ví dụ **cú pháp** với dấu `/` để dễ nhìn, không phải khẳng định mọi OS sẽ in cùng dấu phân cách:

| Đường dẫn đầu vào | Sau normalize | Ý nghĩa |
| --- | --- | --- |
| `a/./b` | `a/b` | Bỏ `.` |
| `a/x/../b` | `a/b` | Triệt tiêu cặp `x/..` |
| `a/b/../../c` | `c` | Triệt tiêu hai cấp tên |
| `../a/./b` | `../a/b` | Giữ `..` đầu đường dẫn tương đối |
| `a/../../b` | `../b` | Chỉ triệt tiêu được một cặp `a/..` |
| `a/b` | `a/b` | Đã gọn, không cần biến đổi |

**Không đồng nhất việc rút gọn cú pháp với đường đi thực tế trên đĩa.** Khi gặp symbolic link hoặc các đặc tính filesystem đặc biệt, loại bỏ một cặp `name/..` có thể thay đổi **tệp vật lý** mà đường dẫn sẽ phân giải đến.

### Điều normalize() không làm

`normalize()` **không**:

- Kiểm tra `Files.exists(path)`.
- Mở file hoặc đọc nội dung file.
- Kiểm tra quyền đọc/ghi.
- Theo symbolic link để biết mục tiêu thật.
- Tự biến đường dẫn tương đối thành tuyệt đối.
- Chứng minh hai `Path` khác nhau trỏ tới cùng inode/file.
- Phát hiện chắc chắn traversal attack hoặc xác nhận input người dùng an toàn.
- Đảm bảo dữ liệu đã được “canonical hóa” theo trạng thái hiện tại của filesystem.
- Tạo thư mục, xóa tệp hoặc thay đổi working directory.

Một số phương thức `Path` như `toRealPath()` mới cần truy cập hệ thống tệp và có thể ném `IOException`.

## 4. Ví dụ Java 17 chạy được: normalize thuần cú pháp

Chương trình này không tạo hoặc đọc bất kỳ file nào. Kết quả cố ý dựa vào `Path.equals` và số name element để tránh phụ thuộc dấu phân cách trên Windows/Unix.

**Tệp `NormalizeBasics.java`:**

```java
import java.nio.file.Path;

public class NormalizeBasics {
    public static void main(String[] args) {
        Path original = Path.of(
                "data", "reports", "..", "images", ".", "logo.png");
        Path expected = Path.of("data", "images", "logo.png");
        Path normalized = original.normalize();

        System.out.println("Original names: " + original.getNameCount());
        System.out.println("Normalized names: " + normalized.getNameCount());
        System.out.println("Expected path: " + normalized.equals(expected));
        System.out.println("Still relative: " + !normalized.isAbsolute());
        System.out.println("Idempotent: "
                + normalized.normalize().equals(normalized));

        Path leadingParent = Path.of("a", "..", "..", "b").normalize();
        System.out.println("Leading .. retained: "
                + leadingParent.equals(Path.of("..", "b")));
    }
}
```

Biên dịch/chạy với JDK 17+:

```bash
javac NormalizeBasics.java
java NormalizeBasics
```

**Stdout chính xác:**

```text
Original names: 6
Normalized names: 3
Expected path: true
Still relative: true
Idempotent: true
Leading .. retained: true
```

Đoạn code không gọi `Files.exists`, `Files.readString` hay `Files.newInputStream`. Dù đường dẫn `data/images/logo.png` không hề tồn tại, việc `normalize()` vẫn hợp lệ. Điều này chứng minh rằng **phép biến đổi chỉ dựa trên các thành phần đường dẫn**, không cần kiểm tra đích trên đĩa.

### Hai điều cần nhớ trong ví dụ

1. `original.getNameCount()` đếm các thành phần hiện hữu trong Path, gồm cả `..` và `.`, nên khác số thành phần của `normalized`.
2. `Path.equals` so sánh các biểu diễn Path theo filesystem provider và các quy tắc của nó; không đảm bảo kết luận về **hai file vật lý** khi có liên kết hoặc nhiều cách đặt tên.

## 5. Path.normalize(), toAbsolutePath() và toRealPath() khác nhau

Ba phương thức này rất dễ bị đánh đồng:

| Phương thức | Đọc thông tin filesystem? | Kết quả/ý nghĩa |
| --- | --- | --- |
| `path.normalize()` | Không cần | Rút gọn thành phần cú pháp như `.`, `name/..` |
| `path.toAbsolutePath()` | Chuyển thành biểu diễn tuyệt đối theo provider; không có nghĩa là kiểm tra file tồn tại | Một `Path` tuyệt đối, có thể vẫn có thành phần cần normalize |
| `path.toRealPath()` | Có, tra cứu theo filesystem | Đường dẫn tuyệt đối, thực tế, được xử lý theo trạng thái filesystem (mặc định theo liên kết) và có thể ném `IOException` |

Cần đọc hợp đồng của provider; trên filesystem mặc định, `toAbsolutePath()` thường dùng working directory để đổi đường dẫn tương đối. Nhưng phương thức này **không đồng nghĩa** với `toRealPath()`, và không bảo đảm file tồn tại.

Hai biểu thức thường gặp:

```java
Path absoluteAndLexical = path.toAbsolutePath().normalize();
Path real = path.toRealPath();
```

Đây là **đoạn minh họa** với `Path path` và xử lý `IOException`. Biểu thức đầu cho một đường dẫn tuyệt đối đã loại bỏ thành phần dư thừa theo cú pháp; biểu thức sau truy cập filesystem để tìm đường dẫn thực.

**Không thay `toRealPath()` bằng `toAbsolutePath().normalize()`** nếu thuật toán phụ thuộc vào symbolic link, nhận dạng file hoặc vị trí đích đã được phân giải. Hai biểu thức có thể trả Path khác nhau dù xuất phát từ cùng input.

### toRealPath và LinkOption.NOFOLLOW_LINKS

Thông thường `toRealPath()` phân giải symbolic link và yêu cầu đường dẫn thực sự tồn tại. Một biến thể:

```java
Path result = path.toRealPath(LinkOption.NOFOLLOW_LINKS);
```

Đây là đoạn minh họa cần `java.nio.file.LinkOption`, `Path` và xử lý `IOException`. Khi đưa `NOFOLLOW_LINKS`, cách xử lý symbolic link thay đổi theo hợp đồng của API, vì vậy `result` không nên được hiểu là cùng ý nghĩa với `path.toRealPath()` mặc định. Dù có tùy chọn không theo liên kết, lời gọi vẫn là **hoạt động filesystem**, khác `normalize()`.

## 6. Ví dụ thực tế: tồn tại file mới cần toRealPath

Một đoạn chương trình có thể tạo một file tạm, rồi so sánh biểu diễn cú pháp với kết quả thực tế:

**Tệp `RealPathDemo.java`:**

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class RealPathDemo {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("normalize-demo-");
        Path child = directory.resolve("child");
        Path file = directory.resolve("sample.txt");

        try {
            Files.createDirectory(child);
            Files.writeString(file, "ok", StandardCharsets.UTF_8);

            Path withDots = child.resolve("..").resolve("sample.txt");
            Path lexical = withDots.normalize();

            System.out.println("Lexical equals file path: "
                    + lexical.equals(file));
            System.out.println("Real paths equal: "
                    + withDots.toRealPath().equals(file.toRealPath()));
            System.out.println("Same file: "
                    + Files.isSameFile(withDots, file));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(child);
            Files.deleteIfExists(directory);
        }
    }
}
```

Biên dịch/chạy:

```bash
javac RealPathDemo.java
java RealPathDemo
```

**Stdout trong điều kiện file tạm được tạo/xóa thành công, không có thay đổi đồng thời:**

```text
Lexical equals file path: true
Real paths equal: true
Same file: true
```

Trong ví dụ, `child` là thư mục thật, `..` thường trỏ trở về `directory`, nên cả ba phép so sánh đều đúng. Nhưng đây là điều kiện **mà chương trình đã tạo và kiểm soát**. Với một component là symbolic link, kết quả có thể khác, và bước `normalize()` có thể không bảo toàn ý nghĩa đường dẫn đối với việc truy cập thật.

Không nên dùng `Files.exists()` rồi xem đó là bằng chứng file sẽ tiếp tục tồn tại đến khi đọc: filesystem có thể bị thay đổi giữa lúc kiểm tra và lúc sử dụng (**TOCTOU** — time of check to time of use).

## 7. Rủi ro lớn nhất: symbolic link làm lexical normalize khác thực tế

Giả sử cấu trúc file:

```text
/work/
├── data/
│   ├── notes.txt
│   └── alias  -> /shared/reports   (symbolic link)
└── ...
/shared/
├── notes.txt
└── reports/
```

Xét đường dẫn:

```text
/work/data/alias/../notes.txt
```

**Rút gọn thuần cú pháp** bằng `normalize()` loại cặp `alias/..`, tạo:

```text
/work/data/notes.txt
```

Nhưng **phân giải vật lý** khi hệ điều hành đi qua `alias` (trỏ đến `/shared/reports`) có thể khiến phần `..` đi lên thư mục cha của đích symlink và trỏ đến:

```text
/shared/notes.txt
```

Vì vậy, theo trạng thái filesystem và cách thực hiện path resolution, đường dẫn trước/sau normalize có thể **trỏ đến hai file khác nhau**.

Đây là lý do tài liệu `Path.normalize()` cảnh báo: loại bỏ `name/..` có thể đổi kết quả tìm file nếu `name` là symbolic link. **Không dùng `normalize()` như bằng chứng rằng đã truy cập đúng file mong muốn.**

### Một vấn đề nữa: symbolic link không phải dữ liệu tĩnh

Ngay cả khi vừa gọi `toRealPath()` và kiểm tra kết quả, một tác nhân khác có quyền thay đổi cây thư mục có thể **thay symbolic link** trước khi lời gọi mở file tiếp theo. Đó là tình huống race condition. Với hệ thống có yêu cầu an ninh cao, thiết kế cần ràng buộc quyền truy cập ở cấp filesystem, dùng thao tác gắn với directory handle hoặc cơ chế tương đương thay vì chỉ kiểm tra chuỗi Path rồi mở lại tên.

## 8. normalize không có nghĩa đường dẫn an toàn

Một sai lầm thường gặp trong hệ thống phục vụ file:

```java
Path candidate = base.resolve(userInput).normalize();
return Files.readAllBytes(candidate);
```

Đây là đoạn minh họa **chưa có kiểm tra an toàn**, cần `Path base`, `Path userInput`, `Files` và xử lý `IOException`. Nếu userInput chứa `..` hoặc là đường dẫn tuyệt đối, `candidate` có thể trỏ **ra ngoài thư mục được phép**. `normalize()` chỉ làm rõ biểu diễn đó chứ không chặn truy cập.

Ví dụ:

```text
base:        /srv/app/public
userInput:   ../private/secret.txt
resolve:     /srv/app/public/../private/secret.txt
normalize:   /srv/app/private/secret.txt
```

Kết quả đã loại `..`, nhưng vẫn ở **ngoài** `/srv/app/public`.

**Quan trọng:** với `Path.resolve(other)`, nếu `other` là đường dẫn tuyệt đối theo provider, kết quả có thể bỏ qua `base` và dùng `other` trực tiếp. Không thể giả định `base.resolve(userInput)` luôn nằm dưới base.

### Một kiểm tra cú pháp (chỉ dùng trong tình huống phù hợp)

```java
Path normalizedBase = base.toAbsolutePath().normalize();
Path candidate = normalizedBase.resolve(userInput).normalize();

if (!candidate.startsWith(normalizedBase)) {
    throw new SecurityException("Path escapes permitted directory");
}
```

Đây là đoạn **minh họa lexical containment**. Nó kiểm tra candidate có nằm dưới base ở cấp các thành phần Path hay không, sau khi rút gọn cú pháp. Tuy nhiên **nó chưa ngăn symbolic link ở bên trong base trỏ ra ngoài** và không chống được race khi thư mục/file bị thay đổi giữa bước kiểm tra và bước mở.

Tùy ứng dụng, cần thêm:

- Kiểm tra loại/đặc tính input hợp lệ từ người dùng.
- Xác nhận real root và real candidate qua filesystem khi target đã tồn tại.
- Cẩn trọng nếu target mới **chưa tồn tại** vì `toRealPath()` của target chưa dùng được; có thể cần kiểm tra parent thật và áp dụng cơ chế tạo file an toàn.
- Sử dụng quyền filesystem chặt chẽ, sandbox/container và directory-handle-based operations khi cần bảo vệ chống race.
- Thiết kế chính sách symbolic link rõ ràng (cho phép, cấm hay chỉ cho phép trong một cây thư mục nhất định).

**`normalize()` là bước hữu ích để xử lý cú pháp, không phải hệ thống kiểm soát truy cập.**

## 9. startsWith(Path) khác String.startsWith

Một lỗi an toàn phổ biến là dùng:

```java
candidate.toString().startsWith(base.toString())
```

Đây là phép so sánh **tiền tố ký tự**, nên `/data/public2` cũng bắt đầu bằng `/data/public` dù là thư mục khác.

`Path.startsWith(Path)` so sánh **các name element của đường dẫn** theo provider; nó tránh lỗi đồng nhất tiền tố văn bản với tổ tiên thực của Path. Tuy nhiên, như đã nói, nó vẫn chỉ xét biểu diễn Path và **không tự phân giải symbolic link**.

### Ví dụ Java 17 chạy được: tiền tố chuỗi đánh lừa

**Tệp `PathPrefixDemo.java`:**

```java
import java.nio.file.Path;

public class PathPrefixDemo {
    public static void main(String[] args) {
        Path base = Path.of("public").toAbsolutePath().normalize();
        Path sibling = base.resolveSibling("public-backup")
                .resolve("config.txt").normalize();

        System.out.println("String prefix: "
                + sibling.toString().startsWith(base.toString()));
        System.out.println("Path prefix: " + sibling.startsWith(base));

        Path escape = base.resolve("..").resolve("outside.txt").normalize();
        System.out.println("Escaping .. rejected lexically: "
                + !escape.startsWith(base));
    }
}
```

Chạy:

```bash
javac PathPrefixDemo.java
java PathPrefixDemo
```

**Stdout chính xác trên filesystem mặc định thông thường:**

```text
String prefix: true
Path prefix: false
Escaping .. rejected lexically: true
```

Mục tiêu là minh họa hai tầng so sánh: string prefix nhìn thấy cùng tiền tố `public`, còn Path prefix nhận ra `public-backup` là tên khác. Dù `Path.startsWith` đúng hơn khi nói về đường dẫn, nó không thay thế kiểm tra **real path và chính sách filesystem** trong tình huống an ninh.

## 10. So sánh đường dẫn: equals, normalize, isSameFile, toRealPath

Các API khác nhau trả lời các câu hỏi khác nhau:

| API | Trả lời câu hỏi |
| --- | --- |
| `a.equals(b)` | Hai đối tượng Path được coi là bằng nhau về biểu diễn theo provider hay không? |
| `a.normalize().equals(b.normalize())` | Hai biểu diễn sau rút gọn cú pháp có giống nhau không? |
| `a.toAbsolutePath().normalize()` | Đường dẫn tuyệt đối rút gọn cú pháp nhìn như thế nào? |
| `a.toRealPath()` | Đường dẫn thực tới entry hiện tồn tại theo filesystem là gì? |
| `Files.isSameFile(a, b)` | Hai Path có định danh cùng file ở thời điểm kiểm tra theo filesystem hay không? |

`Files.isSameFile` có thể ném `IOException`; tùy filesystem, nó có thể xử lý symbolic link, hard link và nhận dạng file theo provider. `Path.equals` đơn thuần không được dùng để khẳng định hai đường dẫn khác chữ là hai file khác nhau.

Ví dụ:

```text
/var/data/current.txt -> /var/data/releases/v2.txt  (symlink)
```

`Path.of("/var/data/current.txt")` và `Path.of("/var/data/releases/v2.txt")` rõ ràng có name element khác nhau, nên lexical equality có thể là `false`. Nhưng nếu symlink đang trỏ tới tệp đó, `Files.isSameFile` có thể là `true`. Hãy chọn API theo câu hỏi thật sự cần trả lời.

Một `Path` có thể bị thay đổi ý nghĩa vật lý theo thời gian dù chính đối tượng Java không đổi, vì filesystem là trạng thái bên ngoài. Kiểm tra hôm nay không phải một xác nhận mãi mãi.

## 11. resolve, relativize và normalize: thứ tự có ý nghĩa

Các thao tác Path có thể được kết hợp, nhưng thứ tự biến đổi cần theo mục tiêu.

### resolve: ghép đường dẫn

```java
Path base = Path.of("docs");
Path child = base.resolve("chapters").resolve("..").resolve("intro.md");
Path result = child.normalize(); // docs/intro.md theo cú pháp.
```

`resolve()` ghép đường dẫn ở cấp provider. Nó **không đọc filesystem**. Một thành phần `..` sau khi resolve vẫn cần normalize nếu muốn biểu diễn gọn.

### relativize: tìm đường đi tương đối giữa hai Path

```java
Path a = Path.of("docs", "java");
Path b = Path.of("docs", "images", "logo.png");
Path relative = a.relativize(b);
```

Đoạn này tạo một relative Path theo các phần tên. Đối với các provider phổ biến, có thể có dạng `../images/logo.png`. `relativize` thường yêu cầu các Path có hình thức tương thích (ví dụ cùng tuyệt đối hoặc cùng tương đối và cùng provider); nếu không, có thể ném `IllegalArgumentException`.

**`relativize` cũng mang ý nghĩa cú pháp**; nó không khám phá symlink hay đường đi vật lý “ngắn nhất” giữa hai vị trí thật.

### Thứ tự với toAbsolutePath

Trong các ứng dụng thông thường, `base.toAbsolutePath().normalize().resolve(relative).normalize()` giúp có một biểu diễn tuyệt đối có thể dự đoán. Nhưng nếu mục tiêu là định danh file thật, bước `toRealPath()` hoặc một chiến lược filesystem-aware mới giải quyết phần đó.

Không suy luận kết quả `resolve`, `relativize`, `normalize` từ chuỗi thô theo hệ điều hành khác mà không tính tới root, ổ đĩa, UNC paths hoặc provider.

## 12. normalize và đường dẫn tương đối có ..

Nếu một đường dẫn tương đối bắt đầu bằng `..`, `normalize()` không thể biết thư mục cha nào là “đúng” nếu không có ngữ cảnh:

```java
Path p = Path.of("..", "data", ".", "info.txt");
Path n = p.normalize();
System.out.println(n.equals(Path.of("..", "data", "info.txt")));
```

Đoạn trên minh họa giá trị `true`. `..` đầu được giữ vì không có một thành phần tên bình thường nào đứng trước nó để rút gọn. Việc cố tự xóa mọi `..` khỏi path có thể tạo ra một đường dẫn mang **ý nghĩa khác**, không phải normalize.

Đừng giải quyết traversal bằng cách xóa các đoạn `..` dưới dạng chuỗi. Cách đó dễ có lỗi khi xử lý các separator, encoding, provider và symbolic link. Cần quy ước rõ về đầu vào hợp lệ và ranh giới truy cập.

## 13. normalize không liên quan đến Unicode normalization

Tên `normalize` trong `Path.normalize()` dễ gây liên tưởng nhầm tới chuẩn hóa Unicode như NFC, NFD. Đây là hai khái niệm **hoàn toàn khác nhau**:

- `Path.normalize()`: rút gọn **cấu trúc path**, ví dụ `a/./b` thành `a/b`.
- `java.text.Normalizer.normalize(text, form)`: chuẩn hóa **biểu diễn Unicode**, ví dụ các chuỗi code point có ý nghĩa kết hợp tương đương.

`Path.normalize()` không biến tên `café.txt` ở một dạng Unicode sang dạng khác chỉ vì tên chứa dấu. Hành vi so sánh tên file với Unicode, case sensitivity và normalization vật lý còn phụ thuộc filesystem/provider.

Nếu cần quản lý tên file đa ngôn ngữ, phải hiểu đặc tính filesystem mục tiêu và quy ước ứng dụng; `Path.normalize()` không phải công cụ giải quyết mọi vấn đề Unicode của tên file.

## 14. normalize không đảm bảo an toàn khi thao tác tệp

Các tình huống thực tế cần chú ý:

### 14.1 File không tồn tại

`Path.of("missing/../a.txt").normalize()` vẫn tạo một Path rút gọn mà không ném `NoSuchFileException` chỉ vì các thư mục chưa tồn tại. Nếu sau đó `Files.newInputStream` được gọi, nó mới có thể phát sinh lỗi filesystem.

### 14.2 Quyền truy cập

`normalize()` không kiểm tra quyền. Một path trông hợp lệ có thể không đọc được vì quyền trên thư mục cha, ACL, sandbox hoặc khóa tệp.

### 14.3 Symbolic link và hard link

`normalize()` không phân giải liên kết. Hai đường dẫn có biểu diễn khác nhau có thể chỉ về cùng file; hoặc một đường dẫn thay đổi ý nghĩa sau khi symlink được cập nhật.

### 14.4 Race conditions

Ngay cả `toRealPath()`, `Files.exists()` hay `Files.isSameFile()` chỉ phản ánh trạng thái tại lúc truy vấn; filesystem có thể thay đổi sau đó. Với đầu vào không tin cậy, không nên dựa duy nhất vào mẫu “check rồi mở” để bảo vệ khỏi kẻ thay đổi cây thư mục đồng thời.

### 14.5 Khác biệt provider và nền tảng

`Path` phụ thuộc filesystem provider. Root, separator, drive letter, đường dẫn UNC, định dạng case và khả năng symbolic link khác nhau giữa các hệ. Code cần chạy trên nhiều nền tảng nên dùng API Path thay vì ghép dấu `/` hoặc `\\` thủ công. Khi kiểm thử, so sánh Path hoặc các thuộc tính thay vì mặc định mọi máy đều in đúng một đường dẫn string.

### 14.6 Di chuyển và tạo tệp mới

Nếu target chưa tồn tại, `toRealPath()` có thể thất bại. Trong quy trình upload hoặc tạo file, cần phân biệt phần thư mục đã tồn tại, quy tắc được phép ghi, và khả năng kẻ khác thay parent/symlink. Chỉ `normalize()` và `startsWith()` không đủ bảo đảm chống traversal/race trong môi trường có đối thủ.

## 15. Một cách kiểm tra vị trí file hiện hữu (và giới hạn)

Với file **đã tồn tại**, một hướng kiểm tra filesystem-aware ở mức cơ bản:

```java
static Path validateExistingFile(Path permittedRoot, Path request)
        throws IOException {
    Path realRoot = permittedRoot.toRealPath();
    Path realTarget = realRoot.resolve(request).toRealPath();

    if (!realTarget.startsWith(realRoot)) {
        throw new SecurityException("Outside permitted root");
    }
    return realTarget;
}
```

Đây là **phương thức minh họa**, cần `Path`, `IOException`; không phải giao thức an ninh hoàn chỉnh. Lời gọi `toRealPath()` phân giải vị trí thực và cần mục tiêu tồn tại. `realTarget.startsWith(realRoot)` sau đó so sánh các name element **trên đường dẫn thực đã được xác định tại thời điểm kiểm tra**.

Tuy nhiên:

- Nếu resource nằm ngoài root, phương thức từ chối; nếu không tồn tại hoặc không được phép truy cập, `toRealPath()` ném lỗi.
- Nếu một attacker có thể thay symlink giữa validate và thao tác open tiếp theo, có thể xảy ra **TOCTOU**.
- Nếu phải tạo file mới, target chưa tồn tại không thể được xử lý nguyên xi bằng cách này.
- Nếu có nhiều filesystem provider, các `Path` không tương thích có thể gây lỗi khi `resolve` hoặc so sánh.
- Chính sách có cho phép symlink, mount point, hard link hay không cần được quy định riêng theo yêu cầu ứng dụng.

Với tác vụ an ninh cao, xem xét phương pháp thao tác tương đối tới directory handle, chẳng hạn `SecureDirectoryStream` khi provider hỗ trợ, và hạn chế quyền của tiến trình. **Không một lời gọi `normalize()` đơn lẻ tạo ra biên an ninh.**

## 16. Câu hỏi tự kiểm tra

1. Vì sao `Path.of("a","..","b").normalize()` trả một Path hợp lệ ngay cả khi thư mục `a` không tồn tại?
2. Nêu khác biệt giữa `normalize()`, `toAbsolutePath()`, `toRealPath()` và `Files.isSameFile()`.
3. Chạy `NormalizeBasics` trên Java 17. Thay `"reports", ".."` bằng `"reports", "2026", "..", ".."` rồi dự đoán số name element trước/sau.
4. Viết test chứng minh `Path.of("..","a").normalize()` vẫn có `..` ở đầu.
5. Vẽ cấu trúc filesystem có symlink `alias` rồi giải thích tại sao đường dẫn `alias/../x` có thể không tương đương với việc xóa hai thành phần ấy.
6. Chạy `PathPrefixDemo`, thay `public-backup` bằng `public2`; giải thích tại sao kiểm tra chuỗi vẫn gây nhầm lẫn.
7. Với input `../private/secret.txt` và root `/srv/app/public`, hãy tính kết quả của `resolve` và `normalize`. Vì sao không thể gọi kết quả ấy là “safe path”?
8. Tạo một tệp tạm, gọi `toRealPath()`, rồi thử `toRealPath()` trên một tệp không tồn tại. Phân biệt kết quả với `normalize()`.
9. Thiết kế một hàm nhận đường dẫn do người dùng cung cấp và chỉ cho phép đọc file hiện hữu trong root. Nêu các bước kiểm tra cùng giới hạn TOCTOU.
10. Hãy giải thích vì sao `Path.normalize()` không đồng nghĩa với Unicode NFC/NFD và không sửa mọi vấn đề về tên file đa ngôn ngữ.
11. Với một Path tương đối và một Path tuyệt đối thuộc cùng filesystem, điều gì có thể xảy ra khi gọi `relativize` trực tiếp?
12. Trình bày trường hợp `Files.isSameFile(a,b)` trả true nhưng `a.normalize().equals(b.normalize())` là false.

## 17. Những lỗi phổ biến cần tránh

| Sai lầm | Hậu quả | Cách đúng |
| --- | --- | --- |
| Gọi `normalize()` rồi nghĩ file chắc chắn tồn tại | Lỗi khi mở file | Dùng Files API phù hợp và xử lý `IOException` |
| Coi `normalize()` là kiểm tra quyền | Quyền truy cập vẫn có thể bị từ chối | Kiểm tra ở lúc thao tác thật; quản lý exception |
| Coi `normalize()` là canonical path vật lý | Sai khi có symlink | Dùng `toRealPath` khi cần đường dẫn thực |
| So `Path.equals` để khẳng định là cùng file | Có thể bỏ qua symlink/hard link | Dùng `Files.isSameFile` khi cần |
| So `candidate.toString().startsWith(base.toString())` | `public2` trùng tiền tố `public` | Dùng `Path.startsWith` cho so sánh thành phần |
| Dùng `normalize().startsWith(root)` như kiểm soát truy cập tuyệt đối | Symlink/race có thể thoát root | Chính sách filesystem-aware và cơ chế chống race |
| Coi `toAbsolutePath()` là `toRealPath()` | Chưa phân giải symlink và chưa chứng minh tồn tại | Chọn đúng API theo mục tiêu |
| Xóa mọi đoạn `..` bằng xử lý string | Có thể đổi ý nghĩa path và tạo lỗi bảo mật | Làm việc với Path và chính sách input rõ ràng |
| Dùng `File.separator` ghép string khắp nơi | Dễ sai provider/platform | `Path.of`, `resolve`, `getParent` |
| Cho rằng normalize giống Unicode normalization | Không xử lý vấn đề Unicode trong tên file | Dùng API Unicode riêng khi thực sự cần |
| Kiểm tra file rồi mặc định trạng thái không thể đổi | TOCTOU | Thiết kế thao tác an toàn ở cấp filesystem |
| Normalize rồi ghi đè file được user cung cấp mà không xác minh root | Có nguy cơ truy cập ngoài phạm vi | Validation thực tế và giới hạn quyền truy cập |

## 18. Ghi nhớ

**`Path.normalize()` chỉ xử lý cấu trúc cú pháp của đường dẫn.** Nó hữu ích để loại `.`, triệt tiêu những cặp `name/..` có thể triệt tiêu và tạo Path gọn hơn cho các phép toán khác.

Nhưng normalize **không** đảm bảo tệp tồn tại, quyền truy cập hợp lệ, đường dẫn vật lý đúng theo ý muốn hoặc input người dùng không thể vượt khỏi thư mục cho phép. Khi cần nhận dạng file, dùng `Files.isSameFile` hoặc `toRealPath` theo hợp đồng; khi cần bảo vệ vùng lưu trữ, xem xét symbolic link và rủi ro thay đổi đồng thời.

**Mental model:** `normalize` trả lời “đường dẫn này rút gọn theo ký hiệu như thế nào?”, còn việc truy cập filesystem mới có thể trả lời “tệp thực tế ở đâu và có đọc/ghi được không?”.

### Tài liệu tham khảo

- [Java SE 17: `Path.normalize()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html#normalize()) và [`Path.toRealPath()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html#toRealPath(java.nio.file.LinkOption...)).
- [Java SE 17: `Files.isSameFile()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#isSameFile(java.nio.file.Path,java.nio.file.Path)).
- [Java SE 17: `SecureDirectoryStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/SecureDirectoryStream.html) — cơ chế phù hợp cho một số thao tác directory-handle-based khi provider hỗ trợ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="path-006">toAbsolutePath khác toRealPath</a>

<details>
<summary>Xem chi tiết</summary>

# `Path.toAbsolutePath()` khác `Path.toRealPath()` trong Java

Trong Java NIO.2, hai câu lệnh dưới đây đều có thể cho ra một đường dẫn **tuyệt đối**, nhưng chúng **không làm cùng một việc**:

~~~java
Path absolute = path.toAbsolutePath();
Path real = path.toRealPath();
~~~

`toAbsolutePath()` chủ yếu chuyển cách biểu diễn đường dẫn thành **dạng tuyệt đối**; đường dẫn được tạo **không cần trỏ tới tệp đang tồn tại**. `toRealPath()` đi xa hơn: nó xác định **đường dẫn thực của một đối tượng đã tồn tại trong hệ thống tệp**, thường loại bỏ các thành phần dư thừa và **giải quyết symbolic link theo mặc định**. Vì cần tra cứu hệ thống tệp, nó có thể ném `IOException`.

Nhầm hai phương thức này dễ dẫn đến các lỗi như hiển thị đường dẫn rồi tưởng tệp đã tồn tại, kiểm tra quyền truy cập sai chỗ, hoặc so sánh hai đường dẫn mà không nhận ra một bên đi qua symbolic link.

## 1. Nền tảng: `Path` mô tả đường dẫn, không phải tệp đã mở

`java.nio.file.Path` là đối tượng biểu diễn vị trí trong một hệ thống tệp. Bạn có thể tạo một `Path` bất kể đối tượng tương ứng có tồn tại không:

~~~java
import java.nio.file.Path;

Path relative = Path.of("data", "report.txt");
Path missing = Path.of("not-created-yet.txt");
~~~

Hai câu lệnh chỉ tạo **đối tượng đường dẫn**; không tự tạo file hay kiểm chứng file có tồn tại. Các thao tác trên `Path` như `resolve()`, `getParent()`, `getFileName()`, `startsWith()` và `normalize()` thường làm việc trên cấu trúc đường dẫn; chỉ những API thực sự yêu cầu tương tác với hệ thống tệp mới có ý nghĩa như mở, đọc metadata hoặc xác định đường dẫn thực.

Phân biệt:

- **Relative path (đường dẫn tương đối):** không có root tuyệt đối; ví dụ `docs/guide.txt` trên hệ Unix.
- **Absolute path (đường dẫn tuyệt đối):** chứa thông tin để diễn giải từ một root của file system; ví dụ `/home/app/docs/guide.txt` trên Unix hoặc đường dẫn có ổ đĩa/root thích hợp trên Windows.
- **Real path (đường dẫn thực):** đường dẫn tuyệt đối được suy ra bằng cách tham chiếu đối tượng thực trong hệ thống tệp, với quy tắc giải quyết liên kết và thành phần dư thừa.

Một đường dẫn tuyệt đối vẫn có thể **sai, chưa tồn tại, bị chặn truy cập hoặc chứa `..`**. Từ “absolute” mô tả **hình thức đường dẫn**, không phải một chứng nhận về trạng thái tệp.

## 2. `toAbsolutePath()` thực sự làm gì?

Cú pháp:

~~~java
Path absolute = path.toAbsolutePath();
~~~

Theo hợp đồng `Path` trong Java 17:

- Trả về `Path` biểu diễn đường dẫn **tuyệt đối**.
- Nếu `path` đã là đường dẫn tuyệt đối, phương thức trả về chính đối tượng đường dẫn đó theo hợp đồng API.
- Với đường dẫn tương đối trên provider mặc định, đường dẫn thường được **giải quyết dựa trên thư mục mặc định của hệ thống tệp / thư mục làm việc**.
- **Không tự kiểm tra tệp có tồn tại.**
- **Không tự chuẩn hóa** để xóa mọi thành phần `.` hoặc `..`.
- **Không tự giải quyết symbolic link** thành đường dẫn của đích.
- Việc chuyển đổi là phụ thuộc `FileSystemProvider` đối với một số chi tiết, nên không tự ghép chuỗi bằng dấu `/` hoặc `\\` thay cho API.

Ví dụ giả định thư mục làm việc là `/app` trên hệ Unix:

~~~text
Path.of("docs", "..", "report.txt")
    .toAbsolutePath()

Kết quả điển hình:
    /app/docs/../report.txt

Không nhất thiết là:
    /app/report.txt
~~~

Ở đây `/app/docs/../report.txt` đã là **tuyệt đối**, nhưng vẫn có `docs/..`. Và việc `/app/report.txt` có tồn tại hay không **không ảnh hưởng tới khả năng tạo đối tượng `Path` tuyệt đối**.

`toAbsolutePath()` có thể gặp lỗi theo hợp đồng provider (ví dụ `IOError` hoặc `SecurityException` ở một số cấu hình), nên không nên diễn đạt tuyệt đối rằng nó “không bao giờ có lỗi”. Khác biệt chính là **không bắt buộc xác thực tệp tồn tại để tạo đường dẫn tuyệt đối**.

### Khi nào cần `toAbsolutePath()`?

Nó phù hợp khi muốn:

1. Ghi log hoặc hiển thị đường dẫn đầy đủ để người dùng biết ứng dụng đang diễn giải đường dẫn tương đối từ đâu.
2. Tạo đường dẫn tuyệt đối cho **một tệp đầu ra chưa tồn tại**, ví dụ đường dẫn một báo cáo sẽ được tạo sau.
3. Chuẩn bị đường dẫn cho thao tác ở tầng tiếp theo, khi bạn **chưa cần** truy vấn hệ thống tệp.
4. So sánh cấu trúc đường dẫn theo quy ước riêng, nếu chấp nhận rằng việc so sánh chỉ mang ý nghĩa cú pháp.

Nếu muốn bỏ phần `.`/`..` về mặt cú pháp, bạn có thể gọi riêng `normalize()`:

~~~java
Path lexical = path.toAbsolutePath().normalize();
~~~

Nhưng đừng hiểu nhầm: **`normalize()` không truy cập filesystem để kiểm tra cấu trúc thật**. Trong môi trường có symbolic link, rút gọn `link/../file` bằng phép biến đổi thuần đường dẫn có thể **không giữ nguyên đối tượng được tham chiếu**. Đây là lý do phân biệt xử lý lexical với tra cứu real path.

## 3. `toRealPath()` thực sự làm gì?

Cú pháp:

~~~java
import java.io.IOException;
import java.nio.file.Path;

Path real = path.toRealPath(); // Có thể ném IOException.
~~~

`toRealPath(LinkOption... options)` trả đường dẫn thực của **một file/directory đang tồn tại** theo định nghĩa của provider. Trong trường hợp mặc định thông thường:

1. Nếu đầu vào tương đối, trước hết xác định đường dẫn tuyệt đối.
2. Kiểm tra và xử lý đường dẫn thông qua hệ thống tệp.
3. Loại bỏ các thành phần dư thừa trong kết quả; quy tắc chính xác có thể phụ thuộc provider.
4. **Theo mặc định, đi theo symbolic link** để xác định đích cuối.
5. Có thể phản ánh cách viết thực của tên file/thư mục trên hệ thống tệp, ví dụ cách viết hoa/thường thực tế khi provider hỗ trợ.
6. Trả về `Path` **tuyệt đối** định vị đối tượng thực, hoặc ném `IOException` nếu đối tượng không tồn tại/không truy cập được hoặc có lỗi I/O.

Trong trường hợp một đường dẫn trỏ tới tệp có thật:

~~~text
input:     /app/docs/../report.txt
              |
              | toRealPath()
              v
result:    /app/report.txt   (nếu docs tồn tại và hệ thống tệp có cấu trúc phù hợp)
~~~

Ngược lại, khi `docs` không tồn tại, không được giả định `toRealPath()` sẽ coi mọi `docs/..` là có thể lược bỏ vô điều kiện; hoạt động tra cứu thực có thể gặp lỗi tùy provider và cách diễn giải thành phần đường dẫn.

**Quan trọng:** `toRealPath()` không mở một file handle cho bạn sử dụng về sau và không “khóa” đối tượng vào vị trí đó. Đây là kết quả tra cứu **tại thời điểm gọi**. Nếu file hoặc symbolic link bị đổi ngay sau đó, thao tác mở file tiếp theo có thể thấy trạng thái khác.

## 4. Bảng so sánh trực tiếp

| Tiêu chí | `toAbsolutePath()` | `toRealPath()` |
|---|---|---|
| Mục tiêu | Có đường dẫn ở **dạng tuyệt đối** | Xác định **đường dẫn thực** của đối tượng hệ thống tệp |
| Có cần tệp/thư mục tồn tại? | **Không** | **Có**, theo hợp đồng xác định real path |
| Kết quả có tuyệt đối? | Có | Có |
| Có tự bỏ `.`, `..`? | Không phải mục tiêu; không tự `normalize` | Thường loại bỏ thành phần dư thừa khi xác định real path, theo provider |
| Có tự giải quyết symbolic link? | Không | **Có theo mặc định** |
| `NOFOLLOW_LINKS` | Không có tham số option này | Có, để thay đổi cách xử lý symbolic link |
| Có thể ném `IOException`? | Không khai báo `throws IOException`; có khả năng lỗi runtime theo provider | **Có**, khai báo `throws IOException` |
| Phải truy cập trạng thái tệp? | Không cần xác nhận tồn tại | Có, cần xác định đối tượng tệp tồn tại |
| Hợp cho tệp đầu ra chưa tạo? | Có | Không, cho đến khi đối tượng tồn tại |
| Hợp cho đường dẫn đã tồn tại đi qua symlink? | Cho ra absolute nhưng không biết đích symlink | Dùng để lấy real path, có thể giải quyết symlink |

Một biểu thức thường gây nhầm lẫn:

~~~java
path.toAbsolutePath().normalize()
~~~

**không tương đương một cách tổng quát với**:

~~~java
path.toRealPath()
~~~

Dù nhiều ví dụ đơn giản không có symlink cho kết quả giống nhau, ý nghĩa và các điều kiện bảo đảm của hai cách là khác nhau.

## 5. Ví dụ Java 17 chạy độc lập, không phụ thuộc đường dẫn máy

Để không phải dự đoán thư mục làm việc trên máy người học, ví dụ tạo một thư mục tạm có thật, một thư mục con `nested` và một tệp `info.txt`. Nó tạo một `Path` chứa `nested/../info.txt`, rồi so sánh đường dẫn tuyệt đối, đường dẫn chuẩn hóa thuần cú pháp và đường dẫn thực.

Lưu thành **`AbsoluteVsRealPathDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class AbsoluteVsRealPathDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("real-path-demo-").toRealPath();

        try {
            Path nested = Files.createDirectory(root.resolve("nested"));
            Path file = Files.writeString(
                    root.resolve("info.txt"), "OK", StandardCharsets.UTF_8);

            Path candidate = root.resolve(Path.of(
                    "nested", "..", "info.txt"));

            Path absolute = candidate.toAbsolutePath();
            Path normalized = absolute.normalize();
            Path real = candidate.toRealPath();

            System.out.println("Absolute? " + absolute.isAbsolute());
            System.out.println("Absolute keeps ..? "
                    + !absolute.equals(normalized));
            System.out.println("Normalized equals file path? "
                    + normalized.equals(file.toAbsolutePath().normalize()));
            System.out.println("Real equals file real path? "
                    + real.equals(file.toRealPath()));

            Path absent = root.resolve("missing.txt");
            System.out.println("Missing can be absolute? "
                    + absent.toAbsolutePath().isAbsolute());

            try {
                absent.toRealPath();
                System.out.println("Missing real lookup succeeded");
            } catch (IOException ex) {
                System.out.println("Missing real lookup failed");
            }
        } finally {
            Files.deleteIfExists(root.resolve("info.txt"));
            Files.deleteIfExists(root.resolve("nested"));
            Files.deleteIfExists(root);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 AbsoluteVsRealPathDemo.java
java AbsoluteVsRealPathDemo
~~~

Đầu ra kỳ vọng trên hệ thống tệp mặc định bình thường, nơi có quyền tạo/xóa thư mục tạm:

~~~text
Absolute? true
Absolute keeps ..? true
Normalized equals file path? true
Real equals file real path? true
Missing can be absolute? true
Missing real lookup failed
~~~

### Phân tích đầu ra

- `Files.createTempDirectory(...)` tạo một thư mục thực. Ta lấy `toRealPath()` của thư mục đó để xác định root có thật và dùng nó làm cơ sở.
- `Files.createDirectory(root.resolve("nested"))` tạo thư mục con thật. Đặt `nested/..` trong candidate giúp minh họa điểm khác biệt mà không phụ thuộc vào tên thư mục làm việc.
- `toAbsolutePath()` cho đường dẫn tuyệt đối nhưng **giữ thành phần `..`**, nên `absolute.equals(absolute.normalize())` là `false`.
- `normalize()` loại bỏ `nested/..` về mặt cú pháp. Vì trong ví dụ `nested` tồn tại và không có symlink, đường dẫn chuẩn hóa trùng vị trí `info.txt`.
- `candidate.toRealPath()` kiểm chứng file có thật, trả đường dẫn thực và bằng `file.toRealPath()`.
- `missing.txt` chưa được tạo vẫn có thể có `Path` tuyệt đối, nhưng gọi `toRealPath()` sẽ không xác định được real path; ví dụ bắt `IOException`.
- Khối `finally` cố gắng xóa các mục do chương trình tạo. Nếu người khác thao tác vào thư mục tạm cùng lúc hoặc môi trường hạn chế I/O, code có thể gặp `IOException`; ví dụ này minh họa hành vi trong điều kiện bình thường, không cố che giấu lỗi hệ thống tệp.

**Lưu ý:** `Path.equals()` so sánh hai đối tượng `Path` theo hợp đồng của provider; việc hai `Path` bằng nhau về cách biểu diễn không phải một cơ chế chứng minh chắc chắn hai thao tác file trong tương lai sẽ truy cập cùng một thực thể khi filesystem có thể thay đổi.

## 6. Symbolic link (liên kết mềm) là điểm khác biệt quan trọng

Một **symbolic link** là một mục trong filesystem tham chiếu tới mục tiêu khác. Hình dung trên Unix:

~~~text
/work/assets/logo.png       (tệp thật)
/work/public/logo.png  ----> /work/assets/logo.png
        ^ đường dẫn symlink
~~~

Nếu `Path link = Path.of("/work/public/logo.png")` đại diện cho symlink hợp lệ:

~~~java
Path a = link.toAbsolutePath();
Path b = link.toRealPath();
~~~

- `a` biểu diễn một **đường dẫn tuyệt đối tới chính vị trí symlink**; nó không tự xác định tệp mà symlink trỏ tới.
- `b` theo mặc định **giải quyết symlink**, nên có thể trở thành `/work/assets/logo.png`.
- Một cách viết đường dẫn đi qua nhiều symlink có thể trỏ tới cùng tệp thật với các đường dẫn có biểu diễn rất khác nhau.
- Trong trường hợp có symlink, `toAbsolutePath().normalize()` vẫn chỉ chuẩn hóa **cú pháp**, nên không thay thế việc giải quyết liên kết thật.

### `toRealPath(LinkOption.NOFOLLOW_LINKS)`

Khi muốn xác định đường dẫn thực **mà không theo symbolic link**, API hỗ trợ `LinkOption.NOFOLLOW_LINKS`:

~~~java
import java.nio.file.LinkOption;

Path withoutFollowing = link.toRealPath(LinkOption.NOFOLLOW_LINKS);
~~~

Đây là **đoạn minh họa**, `link` phải là `Path` đã được khai báo và đối tượng filesystem tương ứng phải thỏa hợp đồng của provider.

Khi có `NOFOLLOW_LINKS`, API không giải quyết symbolic link tới đích như chế độ mặc định. Nhưng **không nên đơn giản hóa thành “luôn lấy toAbsolutePath().normalize()”**: hợp đồng `toRealPath` vẫn phụ thuộc filesystem/provider, và cách xử lý `..` xung quanh symlink có thể khác để tránh thay đổi đối tượng được tham chiếu.

Trên hệ Windows, việc tạo symbolic link có thể phụ thuộc quyền hoặc chính sách hệ thống; do đó chương trình ở mục 5 cố ý không tạo symlink để không yêu cầu quyền đặc biệt. Ví dụ symlink ở trên là **mô hình tư duy**, không phải đường dẫn được bảo đảm tồn tại trên máy người học.

## 7. `normalize()`, `toAbsolutePath()` và `toRealPath()` không thể hoán đổi

Ba thao tác dễ lẫn:

### `normalize()`: chuẩn hóa về mặt lexical

~~~java
Path p = Path.of("a", "b", "..", "c");
Path n = p.normalize(); // Biểu diễn thường rút gọn thành a/c.
~~~

`normalize()` loại bỏ các thành phần `.`, hoặc cặp thành phần `name/..` theo quy tắc lexical. Nó **không cần kiểm tra `a` hoặc `b` có tồn tại**. Do đó không phải xác minh filesystem. Trên một số đường dẫn có symbolic link, bỏ `link/..` có thể khiến đường dẫn kết quả trỏ tới **vị trí khác** nếu truy cập thật.

### `toAbsolutePath()`: chuyển thành dạng tuyệt đối

~~~java
Path absolute = Path.of("a", "b", "..", "c")
        .toAbsolutePath();
~~~

`absolute` là đường dẫn tuyệt đối, nhưng `..` không bắt buộc được loại bỏ, và tệp có thể chưa tồn tại.

### `toRealPath()`: tìm đường dẫn thực

~~~java
Path real = Path.of("a", "b", "..", "c")
        .toRealPath(); // throws IOException
~~~

`real` chỉ được nhận thành công khi hệ thống tệp có thể xác định đối tượng theo hợp đồng. Nó mang thông tin về filesystem, không đơn thuần là thao tác chuỗi.

**Mẹo học:** normalize = *rút gọn biểu thức*, absolute = *đặt vào gốc*, real = *đối chiếu với filesystem*. Mẹo này giúp nhớ, nhưng khi xử lý symbolic link và provider khác nhau phải ưu tiên hợp đồng API.

## 8. Tình huống nên chọn phương thức nào?

| Yêu cầu | Cách tiếp cận | Lý do |
|---|---|---|
| Log vị trí dự kiến của file output chưa tạo | `path.toAbsolutePath()` | Không đòi file tồn tại |
| Hiển thị đường dẫn đã bỏ dấu `.`/`..` để người dùng dễ đọc | `path.toAbsolutePath().normalize()` | Chuẩn hóa thuần cú pháp, không xác thực |
| Lấy vị trí thực của tệp đang tồn tại | `path.toRealPath()` | Kiểm chứng và giải quyết liên kết mặc định |
| Giữ symlink mà không theo tới đích khi lấy real path | `path.toRealPath(NOFOLLOW_LINKS)` | Điều chỉnh hợp đồng xử lý liên kết |
| Muốn mở file ngay sau đó | `Files.newInputStream(path)` / `Files.newByteChannel(...)` | Phương thức mở mới thực sự cung cấp handle; `toRealPath` không mở sẵn |
| Muốn biết hai đường dẫn cùng trỏ tới một tệp hiện tại? | `Files.isSameFile(a, b)` nếu phù hợp | API dành cho kiểm tra danh tính file; không chỉ so sánh chuỗi |
| Kiểm tra đối tượng tệp có tồn tại không | `Files.exists(path)` trong ngữ cảnh đơn giản | Nhưng kết quả `false` có thể do không thể xác định trạng thái; không thay thế thao tác I/O chính |
| Xử lý input từ người dùng trong thư mục cho phép | Thiết kế chính sách đường dẫn và mở file an toàn | Chỉ gọi normalize/real path **chưa đủ** ngăn race hoặc traversal |

Một số đoạn code vô tình làm hai việc cùng lúc:

~~~java
Path target = userInput.toAbsolutePath().normalize();
if (Files.exists(target)) {
    // ...
}
~~~

Đoạn này **không tương đương** với việc đã có real path đáng tin và cũng không giữ file cố định giữa kiểm tra và sử dụng. Nếu cần chính sách an ninh, đừng áp dụng phép so sánh chuỗi đơn giản như một hàng rào an toàn duy nhất.

## 9. Xử lý lỗi của `toRealPath()` đúng cách

Vì có tra cứu filesystem, `toRealPath()` khai báo `throws IOException`. Các tình huống thất bại thường gặp:

- **Không tìm thấy đối tượng:** `NoSuchFileException` (một loại `IOException`) có thể được ném.
- **Không đủ quyền:** có thể gặp `AccessDeniedException` hoặc một ngoại lệ I/O khác, phụ thuộc hệ điều hành/provider.
- **Liên kết hỏng hoặc vòng liên kết:** quá trình giải quyết symlink có thể gặp lỗi.
- **Filesystem không sẵn sàng:** đường dẫn trên network filesystem hoặc thiết bị gắn ngoài có thể thất bại.
- **Thay đổi đồng thời:** file/directory có thể đã bị đổi hoặc xóa trong lúc truy vấn.

Ví dụ xử lý trong một hàm:

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public class RealPathLookup {
    static Path resolveExisting(Path input) throws IOException {
        try {
            return input.toRealPath();
        } catch (NoSuchFileException ex) {
            throw new IOException("Đường dẫn không tồn tại: " + input, ex);
        }
    }
}
~~~

Đây là **lớp Java có thể biên dịch** nhưng không có `main()`; import `Files` trong ví dụ chỉ minh họa ngữ cảnh `java.nio.file` và không cần cho thân hàm.

**Đừng bắt mọi `IOException` rồi tùy tiện dùng `toAbsolutePath()` làm phương án thay thế** nếu ứng dụng cần xác nhận file tồn tại: cách fallback đó thay đổi hợp đồng từ “đối tượng có thật” thành “đường dẫn có dạng tuyệt đối”, có thể che giấu lỗi quyền truy cập hoặc liên kết hỏng. Nếu ứng dụng chủ đích cho phép đường dẫn chưa tồn tại, hãy thể hiện như một nhánh nghiệp vụ rõ ràng.

Không được suy ra `Files.exists(path) == false` chắc chắn nghĩa là file vắng mặt: Java có thể không xác định được do lỗi hoặc quyền truy cập. Với tác vụ thực, xử lý lỗi khi **mở/đọc/ghi** mới là bước quan trọng.

## 10. Đường dẫn thực và an toàn ứng dụng

Một tình huống dễ gặp là ứng dụng chỉ được truy cập file trong thư mục `base`, nhưng nhận đường dẫn tương đối từ người dùng. Một phép kiểm tra cú pháp:

~~~java
Path candidate = base.resolve(userSupplied).normalize();
if (!candidate.startsWith(base.normalize())) {
    throw new IllegalArgumentException("Outside allowed directory");
}
~~~

Đây là **đoạn minh họa kiểm tra lexical** (cần có `base` và `userSupplied` kiểu `Path`), **không phải cơ chế bảo vệ filesystem đầy đủ**. Nếu `base` hoặc `candidate` có symbolic link, một đường dẫn nhìn như nằm dưới `base` có thể dẫn ra ngoài.

Nếu đối tượng **đã tồn tại**, việc so sánh các real path có thể phát hiện nhiều tình huống symlink làm thay đổi vị trí:

~~~java
Path realBase = base.toRealPath();
Path realTarget = candidate.toRealPath();
if (!realTarget.startsWith(realBase)) {
    throw new SecurityException("File outside permitted root");
}
~~~

Đoạn code trên là **minh họa**, chỉ áp dụng khi các đường dẫn hiện tồn tại và có xử lý `IOException`. `Path.startsWith(Path)` kiểm tra theo các thành phần đường dẫn với quy tắc provider, **không phải phép kiểm tra tiền tố chuỗi ký tự**.

Nhưng cần hiểu giới hạn: giữa bước `toRealPath` và bước mở tệp, một bên khác có thể thay symlink hoặc thay file, tạo tình huống **time-of-check to time-of-use (TOCTOU)**. Vì vậy, **không được tuyên bố `toRealPath` tự nó bảo vệ tuyệt đối trước path traversal hoặc symlink attack**. Tác vụ bảo mật nhạy cảm cần các API/thao tác mở theo thư mục và kiểm soát liên kết phù hợp nền tảng (chẳng hạn `SecureDirectoryStream` khi provider hỗ trợ), cùng chính sách quyền tệp thích hợp.

Đối với một file output **chưa tồn tại**, `toRealPath()` trên chính file đó thường thất bại. Nếu cần định tuyến output an toàn, hãy thiết kế dựa trên thư mục cha/handle được kiểm soát và thao tác tạo file thích hợp; đừng tạo một file “giả” chỉ để qua kiểm tra real path.

## 11. Những lỗi phổ biến cần tránh

| Nhầm lẫn | Vì sao sai? | Cách sửa |
|---|---|---|
| “`toAbsolutePath()` cho biết tệp tồn tại.” | Tạo `Path` tuyệt đối không cần file có thật. | Dùng API filesystem khi cần xác định file. |
| “`toAbsolutePath()` tự xóa `..`.” | Không thực hiện lexical normalization mặc định. | Dùng `normalize()` nếu thực sự muốn rút gọn cú pháp. |
| “`normalize()` cũng giống `toRealPath()`.” | Normalize không tra cứu filesystem, không giải quyết symlink. | Chọn đúng thao tác theo nhu cầu và hợp đồng. |
| “`toRealPath()` dùng cho file output chưa tạo.” | Yêu cầu đối tượng tồn tại theo hợp đồng. | Dùng absolute path để chuẩn bị nơi tạo, rồi tạo bằng Files API. |
| “Hai `toAbsolutePath()` trùng nhau chứng minh chúng là cùng file.” | Đường dẫn có thể thay đổi, hard link/symlink và provider có ngữ nghĩa riêng. | Cân nhắc `Files.isSameFile` khi cần kiểm tra file hiện tại. |
| “`toRealPath()` không theo symlink.” | Mặc định giải quyết symlink tới đích. | Dùng `NOFOLLOW_LINKS` khi yêu cầu không theo liên kết. |
| “`NOFOLLOW_LINKS` giống hệt `normalize()`.” | Quy tắc giữ nguyên đối tượng khi có `..` và symlink tinh vi hơn. | Đọc hợp đồng `toRealPath` và provider. |
| “Bắt `IOException` rồi dùng absolute path, coi như đã kiểm tra.” | Vô tình bỏ qua sai quyền truy cập hoặc file vắng mặt. | Tách rõ nghiệp vụ file tồn tại/chưa tồn tại. |
| “`toRealPath()` trả handle đã được khóa.” | Nó chỉ trả `Path`, file có thể thay đổi sau đó. | Dùng Files/Channel API để mở và kiểm soát vòng đời. |
| “Kiểm tra real path một lần đủ tránh race.” | Filesystem có thể thay đổi sau khi kiểm tra. | Dùng chiến lược mở/kiểm soát phù hợp yêu cầu bảo mật. |
| “Cùng một chuỗi path sẽ có cùng nghĩa trên mọi OS.” | Root, separator, case sensitivity và provider khác nhau. | Sử dụng `Path.of`, `resolve`, không tự ghép cú pháp hệ điều hành. |

## 12. Câu hỏi tự kiểm tra

**Câu 1.** Tệp `missing.txt` chưa tồn tại. Điều gì xảy ra khi gọi hai phương thức?

> `Path.of("missing.txt").toAbsolutePath()` vẫn có thể trả một đường dẫn tuyệt đối. `toRealPath()` cần xác định đối tượng tồn tại, nên sẽ thất bại với `IOException` trên trường hợp file không tồn tại.

**Câu 2.** Đường dẫn `/work/a/../b.txt` có gì khác khi xử lý bằng `toAbsolutePath()` và `toAbsolutePath().normalize()`?

> `toAbsolutePath()` giữ đường dẫn tuyệt đối như đầu vào; `normalize()` có thể rút gọn lexical thành `/work/b.txt`. Không phương thức nào trong hai phương thức này tự chứng thực rằng tệp tồn tại hoặc kiểm tra symlink thật.

**Câu 3.** Có một symlink `alias.txt` tới `/data/original.txt`. Khi nào `toRealPath()` và `toRealPath(NOFOLLOW_LINKS)` khác nhau?

> Chế độ mặc định giải quyết symlink để tìm đường dẫn thực của đích. Với `NOFOLLOW_LINKS`, API không theo symlink tới đích; đường dẫn kết quả tương ứng với việc giữ liên kết theo hợp đồng/provider.

**Câu 4.** Nếu tệp đã tồn tại nhưng ứng dụng không đủ quyền để xác định đường dẫn thực, liệu `toAbsolutePath()` trả thành công có chứng minh đủ quyền truy cập không?

> Không. Đường dẫn tuyệt đối chỉ nói về dạng biểu diễn. `toRealPath` có thể ném `IOException` do quyền hoặc lỗi filesystem; thao tác đọc/ghi vẫn cần kiểm tra và xử lý lỗi riêng.

**Câu 5.** Có nên thay mọi chỗ dùng `toRealPath()` bằng `normalize()` để tránh phải bắt `IOException`?

> Không. Đây là hai yêu cầu khác nhau: `normalize()` chỉ xử lý cú pháp; `toRealPath()` cần đối chiếu filesystem và có thể giải quyết symlink. Việc bỏ exception đồng nghĩa bỏ mất phần xác minh cần thiết của nghiệp vụ.

**Câu 6.** Tại sao kiểm tra `target.toRealPath().startsWith(base.toRealPath())` có ích nhưng vẫn chưa phải hàng rào bảo mật hoàn chỉnh?

> Nó có thể phát hiện target hiện trỏ ra ngoài thư mục qua symlink, nhưng filesystem có thể bị thay đổi **sau khi kiểm tra**. Cần chiến lược mở file và quyền hệ thống phù hợp nếu cần bảo đảm chống tấn công.

## Ghi nhớ

- **`toAbsolutePath()`:** có **đường dẫn tuyệt đối**, không yêu cầu file tồn tại, không tự normalize hay giải quyết symlink.
- **`toAbsolutePath().normalize()`:** có **đường dẫn tuyệt đối rút gọn cú pháp**, vẫn không xác minh file thực.
- **`toRealPath()`:** lấy **đường dẫn thực của file/directory đang tồn tại**, thường bỏ phần dư thừa và **giải quyết symbolic link mặc định**; có thể ném `IOException`.
- **`toRealPath(LinkOption.NOFOLLOW_LINKS)`:** lấy real path **mà không theo symbolic link**, với quy tắc provider phù hợp.
- **`toRealPath` không khóa file và không loại bỏ mọi nguy cơ thay đổi đồng thời**.

Câu hỏi quyết định trước khi chọn API là: **“Tôi chỉ cần một cách biểu diễn đường dẫn đầy đủ, hay cần xác định đối tượng thật hiện tồn tại trong filesystem?”** Trả lời được câu đó giúp sử dụng `Path` đúng ở cả I/O thông thường lẫn xử lý đường dẫn có liên kết.

**Tài liệu tham khảo:** [Java SE 17 — `java.nio.file.Path`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html), [`java.nio.file.LinkOption`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/LinkOption.html), [`java.nio.file.Files`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="path-007">startsWith, endsWith, equals và so sánh theo thành phần</a>

<details>
<summary>Xem chi tiết</summary>

# Path.startsWith, endsWith, equals và so sánh theo thành phần trong Java

`java.nio.file.Path` biểu diễn một **đường dẫn** dưới dạng các thành phần tên (name elements) và, nếu có, một thành phần gốc (root). Khi kiểm tra một đường dẫn thuộc thư mục nào, có đuôi tên nào hoặc có bằng đường dẫn khác không, ba phương thức quan trọng là `startsWith(...)`, `endsWith(...)` và `equals(...)`.

Điểm cốt lõi: **đây là các phép so sánh trên cấu trúc `Path`, không phải phép kiểm tra tiền tố/hậu tố của chuỗi tùy ý**. Chẳng hạn, đường dẫn `docs/api/readme.md` bắt đầu bằng `docs/api`, nhưng không bắt đầu bằng `docs/ap`; tên `readme.md` không kết thúc bằng `me.md` theo nghĩa `Path.endsWith(...)`, dù `String.endsWith("me.md")` là `true`.

Bài học dùng Java 17+ và ưu tiên ví dụ đường dẫn tương đối, ghép bằng `Path.of("a", "b")` để tránh phụ thuộc dấu `/` hay `\` của hệ điều hành. Những khác biệt về root, phân biệt chữ hoa/chữ thường và provider sẽ được trình bày riêng.

## 1. Path là chuỗi có cấu trúc, không chỉ là String

Một đường dẫn như:

~~~text
docs / api / guide.md
  0     1      2
~~~

gồm ba thành phần tên: `docs`, `api`, `guide.md`. Với `Path path = Path.of("docs", "api", "guide.md")`:

| API | Ý nghĩa | Kết quả trong ví dụ |
|---|---|---|
| `path.getNameCount()` | Số phần tử tên | `3` |
| `path.getName(0)` | Phần tử tên đầu tiên | `docs` |
| `path.getName(1)` | Phần tử thứ hai | `api` |
| `path.getFileName()` | Phần tử tên cuối | `guide.md` |
| `path.getParent()` | Đường dẫn cha | `docs/api` trên Unix; dấu phân cách có thể khác trên Windows |
| `path.subpath(0, 2)` | Hai phần tử ở chỉ số 0 và 1 | `docs/api` theo cú pháp file system |
| `path.getRoot()` | Phần gốc nếu có | `null` đối với đường dẫn tương đối này |
| `path.isAbsolute()` | Có phải đường dẫn tuyệt đối theo file system hay không | `false` |

`Path` còn triển khai `Iterable<Path>`; vòng lặp `for (Path part : path)` duyệt **từng name element**, không bao gồm root như một phần tử tên. Chỉ số của `getName(...)` và `subpath(...)` bắt đầu từ **0**.

Cách biểu diễn chuỗi bằng `path.toString()` phụ thuộc hệ thống tệp: cùng cấu trúc có thể hiển thị `docs/api/guide.md` trên Unix và `docs\api\guide.md` trên Windows. Không nên viết kiểm thử Java portable bằng cách so sánh với một dấu phân cách cố định khi ý định thật là so sánh `Path`.

## 2. startsWith: so sánh các phần tử đầu đường dẫn

`boolean startsWith(Path other)` kiểm tra đường dẫn hiện tại có bắt đầu bằng những **phần tử tên** của `other` hay không, đồng thời áp dụng quy tắc tương thích root của hệ thống tệp.

~~~java
// Mảnh mã minh họa; cần import java.nio.file.Path.
Path path = Path.of("docs", "api", "guide.md");

boolean a = path.startsWith(Path.of("docs"));          // true
boolean b = path.startsWith(Path.of("docs", "api"));   // true
boolean c = path.startsWith(Path.of("docs", "ap"));    // false
boolean d = path.startsWith(Path.of("doc"));          // false
~~~

Mặc dù chuỗi `"docs/api/guide.md"` bắt đầu với các ký tự `"doc"` và `"docs/ap"`, `Path.startsWith` yêu cầu **mỗi name element tương ứng phải bằng nhau theo quy tắc của file system**. Nó không kiểm tra tiền tố ký tự bên trong tên thư mục.

Các điểm cần nhớ:

1. Nếu `other` có nhiều phần tử tên hơn đường dẫn hiện tại, kết quả là `false`.
2. `path.startsWith(path)` là `true`.
3. Root phải tương thích. Đường dẫn tương đối không thể bắt đầu bằng một `other` tuyệt đối có root; giữa các đường dẫn có root, quy tắc so khớp root do file system quy định.
4. Nếu hai `Path` thuộc hai `FileSystem` khác nhau, `startsWith(Path)` trả `false`.
5. `startsWith(String other)` là overload tiện lợi: chuỗi được chuyển thành `Path` **bằng file system của đường dẫn hiện tại** trước khi so sánh. Vì vậy nó vẫn là so sánh theo thành phần, không phải `toString().startsWith(...)`.

Ví dụ trên Unix, `/data/reports/a.txt` bắt đầu bằng `/data`, nhưng `/database/a.txt` không bắt đầu bằng `/data`. Để tránh phụ thuộc root Unix trong mã chạy trên Windows, hãy tạo đường dẫn bằng API `Path` rồi kiểm tra bằng cùng provider.

## 3. endsWith: so sánh các phần tử cuối đường dẫn

`boolean endsWith(Path other)` so sánh **phần đuôi theo name element**, không phải các ký tự cuối của `toString()`.

~~~java
// Mảnh mã minh họa; cần import java.nio.file.Path.
Path path = Path.of("docs", "api", "guide.md");

boolean a = path.endsWith(Path.of("guide.md"));         // true
boolean b = path.endsWith(Path.of("api", "guide.md"));  // true
boolean c = path.endsWith(Path.of("ide.md"));           // false
boolean d = path.endsWith(Path.of("docs", "api"));      // false
~~~

Điều này dẫn tới khác biệt quan trọng:

~~~java
Path path = Path.of("notes.txt");

// So sánh hậu tố thành phần đường dẫn:
boolean pathSuffix = path.endsWith(Path.of(".txt")); // false

// So sánh hậu tố ký tự trong tên file:
boolean extension = path.getFileName().toString()
        .endsWith(".txt");                           // true
~~~

Đây là **mảnh mã minh họa**. `Path.endsWith(".txt")` không phải hàm kiểm tra phần mở rộng file. Nó hỏi liệu thành phần tên cuối có đúng là `.txt` hay không. Muốn kiểm tra phần mở rộng, cần lấy tên file rồi áp dụng quy tắc chuỗi phù hợp nghiệp vụ.

Đối với `endsWith(Path other)`, nếu `other` không có root, nó có thể khớp với các phần tử tên cuối của một `Path` dài hơn. Nhưng nếu `other` **có root**, nó chỉ có thể khớp khi phần root và phần tên tương ứng phù hợp; trong trường hợp điển hình, không thể dùng một đường dẫn tuyệt đối để hỏi hậu tố một đường dẫn khác bằng cách bỏ qua root.

`endsWith(String other)` cũng chuyển chuỗi thành `Path` trong cùng `FileSystem` rồi so sánh cấu trúc. Đường dẫn ở những provider khác nhau không được coi là có hậu tố bằng nhau chỉ vì cách in chuỗi trùng nhau.

## 4. equals: bằng nhau theo Path, không phải “cùng một file”

`path.equals(other)` kiểm tra **sự bằng nhau của hai đối tượng đường dẫn theo quy tắc của file system**. Đây là so sánh từ góc nhìn `Path`, không tự tra cứu trên đĩa để hỏi hai đường dẫn có trỏ tới cùng một đối tượng file hay không.

~~~java
// Mảnh mã minh họa.
Path a = Path.of("docs", "api", "guide.md");
Path b = Path.of("docs", "api", "guide.md");
Path c = Path.of("docs", "api", ".", "guide.md");

boolean sameLexicalPath = a.equals(b); // true
boolean containsDot = a.equals(c);    // false trên default providers thông thường
~~~

`a` và `b` gồm các phần tử tương ứng; `c` có thêm phần tử `"."` trong biểu diễn lexical. `equals` không tự gọi `normalize()` để loại bỏ phần tử `"."`. Đối với các provider lạ, chi tiết so sánh có thể phụ thuộc triển khai; đừng dựa vào giả định mọi `FileSystem` xử lý dấu chấm và chữ hoa/chữ thường hệt nhau.

Một số trường hợp **không được suy ra từ `equals`**:

- `docs/../docs/a.txt` có thể trỏ cùng nơi với `docs/a.txt` trong hoàn cảnh thích hợp, nhưng không nên coi hai đối tượng `Path` bằng nhau trước khi chuẩn hóa.
- Một đường dẫn tuyệt đối và một đường dẫn tương đối có thể chỉ tới cùng tệp sau khi được diễn giải theo thư mục làm việc; `equals` không chuyển tương đối thành tuyệt đối.
- Hai symbolic link hoặc hard link khác tên có thể định vị cùng file vật lý, nhưng `equals` vẫn có thể trả `false`.
- Hai `Path` có cách hiển thị giống nhau nhưng thuộc hai provider/`FileSystem` khác nhau không mặc nhiên bằng nhau.
- Trên file system phân biệt chữ hoa/chữ thường, tên `Report.txt` và `report.txt` có thể khác nhau; trên một số file system khác chúng có thể được coi tương đương. **Không hard-code** chính sách phân biệt hoa/thường cho mọi hệ thống.

Để kiểm tra **hai đường dẫn có xác định cùng tệp thật không**, dùng `Files.isSameFile(path1, path2)` khi thích hợp; phương thức này có thể truy cập hệ thống tệp và ném `IOException`. Chi tiết được trình bày ở phần sau.

## 5. Chương trình Java 17+ kiểm tra ba phép so sánh

Tạo tệp **`PathComparisonDemo.java`**:

~~~java
import java.nio.file.Path;

public class PathComparisonDemo {
    public static void main(String[] args) {
        Path path = Path.of("docs", "api", "guide.md");
        Path prefix = Path.of("docs", "api");
        Path lookAlike = Path.of("docs", "apix");

        System.out.println("startsWith(prefix)="
                + path.startsWith(prefix));
        System.out.println("startsWith(lookAlike)="
                + path.startsWith(lookAlike));
        System.out.println("endsWith(suffix)="
                + path.endsWith(Path.of("api", "guide.md")));
        System.out.println("endsWith(partialName)="
                + path.endsWith(Path.of("ide.md")));
        System.out.println("equals(same)="
                + path.equals(Path.of("docs", "api", "guide.md")));
        System.out.println("equals(extraDot)="
                + path.equals(Path.of("docs", "api", ".", "guide.md")));
        System.out.println("nameCount=" + path.getNameCount());
        System.out.println("subpathEqualsPrefix="
                + path.subpath(0, 2).equals(prefix));

        for (Path name : path) {
            System.out.println("part=" + name);
        }
    }
}
~~~

Biên dịch/chạy JDK 17+:

~~~bash
javac -encoding UTF-8 PathComparisonDemo.java
java PathComparisonDemo
~~~

Stdout trên default file system Java thường gặp:

~~~text
startsWith(prefix)=true
startsWith(lookAlike)=false
endsWith(suffix)=true
endsWith(partialName)=false
equals(same)=true
equals(extraDot)=false
nameCount=3
subpathEqualsPrefix=true
part=docs
part=api
part=guide.md
~~~

Vì các thành phần tên trong chương trình đều đơn giản, các kết quả không cần in dấu phân cách thư mục của hệ điều hành. Đối với Unix/Windows default provider, phép so sánh giữ phần tử `"."` riêng cho đến khi gọi `normalize()`, nên `equals(extraDot)` là `false`.

Lưu ý vòng lặp duyệt từng thành phần tên đúng thứ tự. `subpath(0, 2)` lấy phần tử tên 0 và 1, không chứa phần tử tên 2; chỉ số cuối là **exclusive**.

## 6. Root, đường dẫn tuyệt đối và tương đối

Các đường dẫn không chỉ khác ở tên thư mục mà còn có thể khác **root**:

- **Tương đối:** `Path.of("work", "notes.txt")` không có root và được diễn giải dựa vào ngữ cảnh của file system.
- **Tuyệt đối:** `Path.of("work").toAbsolutePath()` gắn với đường dẫn tuyệt đối theo file system mặc định.
- **Root:** `getRoot()` trả thành phần gốc nếu có; `null` ở đường dẫn tương đối không có root.

Trên Unix, root thường là `/`. Trên Windows, root và cú pháp đường dẫn có thể liên quan ổ đĩa hoặc UNC. Vì vậy không nên dùng `Path.of("/data")` như ví dụ portable tuyệt đối trên mọi nền tảng; hãy dùng `toAbsolutePath()` hoặc nguồn đường dẫn đã được xác định đúng cho hệ thống chạy.

Những cặp sau **không được đồng nhất**:

~~~java
// Mảnh mã minh họa: không thực hiện I/O.
Path relative = Path.of("docs", "api");
Path absolute = relative.toAbsolutePath();

boolean same = relative.equals(absolute); // false trên default FS thông thường
~~~

Tại sao có thể cùng chỉ tới một thư mục nhưng `equals` khác nhau? Vì một bên có root và phần tên của đường dẫn tuyệt đối, một bên chỉ có phần tên tương đối; `equals` so sánh cách biểu diễn `Path` theo file system, không phải phép xác nhận file vật lý.

Nếu ứng dụng muốn so sánh các đường dẫn cùng ngữ cảnh, phải định nghĩa rõ chính sách quy đổi **cả hai**: chẳng hạn `toAbsolutePath().normalize()` cho so sánh lexical, hoặc `toRealPath()` khi cần xác minh đối tượng trên hệ thống tệp thực và có quyền truy cập phù hợp.

## 7. normalize(): bỏ các phần tử “.” và xử lý “..” ở mức lexical

`Path.normalize()` tạo ra một `Path` mới đã loại các phần tử dư thừa như `"."` và cặp tên thư mục/`".."` có thể khử về mặt cú pháp. Nó **không phải lệnh truy cập đĩa** và không tự xác minh tệp có tồn tại.

Ví dụ:

~~~text
docs / api / . / guide.md
             |
             +---- normalize() ----> docs / api / guide.md

docs / api / .. / notes.txt
                   |
                   +---- normalize() ----> docs / notes.txt
~~~

Điều quan trọng là `normalize()` không luôn giữ nguyên đường dẫn **vật lý** nếu đường đi chứa symbolic link. Ví dụ `link/../target` có thể không tương đương vật lý với `target` khi `link` là symbolic link trỏ tới vị trí khác; vì thế chỉ dùng normalize cho công việc lexical đã hiểu ngữ cảnh.

Để kiểm tra cấu trúc đường dẫn, kết hợp hợp lý với `resolve(...)`, `toAbsolutePath()` và `startsWith(...)` khi cả hai đối tượng đã được đưa về cùng hệ quy chiếu:

~~~java
// Mảnh mã minh họa: xác định base ở một vị trí tin cậy.
Path base = Path.of("workspace").toAbsolutePath().normalize();

Path candidate = base.resolve(Path.of("reports", "..", "notes.txt"))
        .normalize();

boolean lexicalInside = candidate.startsWith(base);
~~~

`resolve` ghép các thành phần đường dẫn; nếu đối số đã **tuyệt đối**, kết quả thường chính là đường dẫn tuyệt đối đó theo hợp đồng provider. Vì vậy phải chuẩn hóa và kiểm tra sau khi resolve, đồng thời quyết định có chấp nhận đường dẫn tuyệt đối từ nguồn không tin cậy hay không.

## 8. Ví dụ Java 17+: so sánh trước và sau normalize

Tạo tệp **`PathNormalizationDemo.java`**:

~~~java
import java.nio.file.Path;

public class PathNormalizationDemo {
    public static void main(String[] args) {
        Path clean = Path.of("root", "docs", "note.txt");
        Path raw = Path.of("root", "docs", ".", "note.txt");

        System.out.println("rawEqualsClean=" + raw.equals(clean));
        System.out.println("normalizedEqualsClean="
                + raw.normalize().equals(clean));

        Path base = Path.of("sandbox", "home")
                .toAbsolutePath().normalize();
        Path good = base.resolve(
                Path.of("reports", "..", "summary.txt")).normalize();
        Path escape = base.resolve(
                Path.of("..", "outside.txt")).normalize();

        System.out.println("goodUnderBase=" + good.startsWith(base));
        System.out.println("escapeUnderBase=" + escape.startsWith(base));
    }
}
~~~

~~~bash
javac -encoding UTF-8 PathNormalizationDemo.java
java PathNormalizationDemo
~~~

Stdout trên default file system Unix/Windows:

~~~text
rawEqualsClean=false
normalizedEqualsClean=true
goodUnderBase=true
escapeUnderBase=false
~~~

Giải thích:

1. `raw` có phần tử `"."` nên chưa bằng `clean` theo phép so sánh lexical của default provider; `normalize()` loại phần tử đó.
2. `base` được biến thành đường dẫn tuyệt đối, chuẩn hóa, rồi các candidate đều được xử lý theo cùng cách.
3. `good` sau khi rút gọn `reports/..` trở thành `base/summary.txt`, vì vậy bắt đầu bằng `base`.
4. `escape` chứa `..` làm đường dẫn trượt lên thư mục cha của `base`; sau normalize, `startsWith(base)` là `false`.

Ví dụ kiểm tra **cấu trúc đường dẫn**. Nó **không tạo thư mục** và không đảm bảo rằng một ứng dụng đọc file qua symbolic link sẽ không thoát ra ngoài thư mục tin cậy.

## 9. equals() khác Files.isSameFile() và toRealPath()

Ba thao tác này trả lời ba câu hỏi khác nhau:

| Thao tác | Câu hỏi | Có truy cập file system không? |
|---|---|---|
| `a.equals(b)` | Hai đối tượng `Path` có được xem là bằng nhau theo provider hay không? | Thông thường không cần |
| `a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize())` | Hai đường dẫn có cùng biểu diễn tuyệt đối đã chuẩn hóa về mặt cú pháp không? | Không xác minh đối tượng trên đĩa |
| `Files.isSameFile(a, b)` | Hai đường dẫn có định vị **cùng đối tượng tệp** hay không? | Có thể cần truy cập đĩa và có thể ném `IOException` |
| `a.toRealPath()` | Đường dẫn thực của một đối tượng có tồn tại theo quy tắc của file system là gì? | Có; mặc định giải quyết symbolic link và kiểm tra tồn tại |

`Files.isSameFile(a, b)` có một ngoại lệ hợp đồng đáng chú ý: **nếu hai đối tượng `Path` đã `equals`, phương thức có thể trả `true` ngay mà không kiểm tra file thực sự có tồn tại**. Vì vậy không dùng `Files.isSameFile(path, path)` làm phép kiểm tra tồn tại.

### Ví dụ Java 17+: hai đường dẫn lexical khác nhau nhưng cùng một file

Tạo tệp **`PathSameFileDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PathSameFileDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("path-demo-", ".txt");

        try {
            Path alias = file.getParent()
                    .resolve(".")
                    .resolve(file.getFileName());

            System.out.println("equals=" + file.equals(alias));
            System.out.println("normalizedEquals="
                    + file.normalize().equals(alias.normalize()));
            System.out.println("isSameFile="
                    + Files.isSameFile(file, alias));
            System.out.println("realPathEquals="
                    + file.toRealPath().equals(alias.toRealPath()));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 PathSameFileDemo.java
java PathSameFileDemo
~~~

Stdout trên default file system Unix/Windows:

~~~text
equals=false
normalizedEquals=true
isSameFile=true
realPathEquals=true
~~~

Chương trình tạo tệp tạm duy nhất, rồi tạo đường dẫn `alias` bằng cách chèn phần tử `"."` vào giữa thư mục cha và tên tệp. Hai biểu diễn lexical chưa bằng nhau, nhưng sau `normalize()` chúng bằng nhau; hệ thống tệp xác nhận cả hai định vị cùng tệp. Khối `finally` xóa tệp tạm, không sửa file có sẵn do người dùng cung cấp.

Trong chương trình thực tế, `toRealPath()` hoặc `isSameFile(...)` có thể thất bại khi thiếu quyền, tệp bị xóa trong lúc kiểm tra, symbolic link lỗi hoặc provider không hỗ trợ đặc điểm nhất định. Không lấy output của ví dụ trên làm cam kết cho mọi virtual file system.

## 10. startsWith/endsWith và String.startsWith/endsWith: dùng đúng cấp độ

Cùng tên phương thức nhưng hai bộ API có mục tiêu khác hẳn nhau.

| Nhu cầu | API thích hợp |
|---|---|
| Đường dẫn nằm trong cây thư mục `docs` theo phần tử tên | `path.startsWith(Path.of("docs"))` |
| Đường dẫn có hai phần tử cuối `images/icon.png` | `path.endsWith(Path.of("images", "icon.png"))` |
| Tên tệp có đuôi chuỗi `.png` | `path.getFileName().toString().endsWith(".png")` |
| Tên file bắt đầu bằng chuỗi `report-` | `path.getFileName().toString().startsWith("report-")` |
| Hai đường dẫn có cùng biểu diễn lexical không? | `path1.equals(path2)`, sau bước chuẩn hóa nếu yêu cầu |
| Hai đường dẫn có trỏ đến cùng file thật không? | `Files.isSameFile(path1, path2)` |
| Một đường dẫn có phần tên ở chỉ số cụ thể không? | `getName(i)`, `subpath(from, to)` |

**Lưu ý với tên tệp**: `getFileName()` có thể trả `null` nếu đường dẫn chỉ chứa root. Khi muốn kiểm tra extension an toàn, cần xử lý trường hợp này:

~~~java
// Mảnh mã minh họa: path là một Path bất kỳ.
Path fileName = path.getFileName();
boolean isJavaFile = fileName != null
        && fileName.toString().endsWith(".java");
~~~

Đây là kiểm tra hậu tố ký tự, không phải xác thực loại nội dung. Một tệp `malware.java` không chắc là mã nguồn Java hợp lệ chỉ vì tên có đuôi như vậy, và chính sách so sánh phân biệt hoa/thường phải do ứng dụng quyết định.

## 11. Vì sao startsWith(String) vẫn không phải kiểm tra chuỗi?

`Path.startsWith(String other)` về cơ bản chuyển `other` thành `Path` bằng `getFileSystem().getPath(other)` rồi gọi `startsWith(Path)`. Ví dụ:

~~~java
Path p = Path.of("alpha", "beta");

boolean a = p.startsWith("alpha");          // true
boolean b = p.startsWith("alp");            // false
boolean c = p.toString().startsWith("alp"); // true
~~~

Đây là **mảnh mã minh họa**. Bên trái vẫn là `Path`, nên overload `startsWith(String)` phân tích `"alpha"` thành phần tử tên. Lời gọi `toString().startsWith("alp")` thì chỉ kiểm tra ba ký tự đầu, không biết `alpha` là một tên thư mục hoàn chỉnh.

Tương tự, `Path.endsWith("txt")` chỉ kiểm tra thành phần tên cuối là `txt`, còn `path.toString().endsWith("txt")` có thể trả `true` cho `notes.txt`. Với code theo cấu trúc thư mục, dùng `Path`; với quy tắc tên file/đuôi extension, hãy **lấy file name rõ ràng** rồi dùng thao tác chuỗi.

Cũng cần lưu ý: chuỗi chuyển thành `Path` phải hợp lệ với provider đó; overload nhận `String` có thể ném `InvalidPathException` nếu chuỗi không tạo được đường dẫn hợp lệ. Không truyền chuỗi đường dẫn của một hệ thống khác rồi tự giả định rằng Java sẽ phân tích giống nhau.

## 12. So sánh phân biệt hoa/thường và provider

`Path` có thể được cung cấp bởi default file system, ZIP file system hoặc các provider khác. Hợp đồng về **cách so sánh tên**, phân biệt hoa/thường và root là đặc thù của hệ thống tệp. Ví dụ:

- Một số hệ thống phân biệt `data` và `Data`.
- Một số hệ thống dùng cách so sánh không phân biệt hoa/thường.
- Cách diễn giải root và dấu phân cách cũng khác nhau giữa Unix, Windows và các provider đặc biệt.
- `Path.equals`, `startsWith` và `endsWith` không tự tạo cơ chế so sánh Unicode/chuỗi **độc lập môi trường**.

Đừng sửa bằng cách gọi `path.toString().toLowerCase()` rồi so sánh một cách tùy tiện: thao tác hạ chữ không tái tạo quy tắc tên của file system, có thể phá ý nghĩa của các ký tự đặc biệt và dễ gây lỗi bảo mật ở biên đường dẫn.

Nếu ứng dụng đòi một quy tắc không phụ thuộc hệ thống, hãy định nghĩa cấu trúc đường dẫn logic riêng, chuẩn hóa dữ liệu đầu vào theo giao thức rồi đối chiếu với file system thật tại ranh giới truy cập. Đừng dùng so sánh tên file như phép xác minh tuyệt đối danh tính tài nguyên.

### compareTo và thứ tự sắp xếp

`Path` triển khai `Comparable<Path>`, và `compareTo(...)` phục vụ việc **sắp thứ tự đường dẫn theo provider**, không phải phép so sánh trùng file. Một kết quả âm/dương không cho biết hai file có liên quan cha–con hay không. Nếu cần kiểm tra tiền tố theo thành phần, `startsWith(...)` diễn đạt đúng ý định hơn.

Phép so sánh giữa `Path` thuộc các provider khác nhau có thể không được hỗ trợ hoặc ném ngoại lệ với `compareTo`; không sử dụng nó làm bộ so sánh universal cho mọi nguồn file system.

## 13. Kiểm tra đường dẫn con: cần hiểu giới hạn bảo mật

Một bài toán thường gặp: cho phép người dùng truy cập một file bên trong thư mục gốc `base`.

**Sai:** dùng `inputString.startsWith(baseString)`. Chuỗi `/srv/app-data-secret` cũng bắt đầu bằng ký tự `/srv/app-data` nhưng không nằm bên trong thư mục đó.

Một phép kiểm tra **lexical** tốt hơn:

~~~java
// Mảnh mã minh họa, không đủ làm biện pháp chống symlink/TOCTOU.
Path base = Path.of("safe-root").toAbsolutePath().normalize();
Path userPath = Path.of(userInput);
Path candidate = base.resolve(userPath).normalize();

if (!candidate.startsWith(base)) {
    throw new SecurityException("Path escapes allowed directory");
}
~~~

Cơ chế trên giải quyết lỗi **tiền tố chuỗi** và nhiều trường hợp `..` về mặt cú pháp. Nhưng nó **chưa đủ để bảo vệ trước symbolic link, junction, mount, thay đổi file system đồng thời hoặc thao tác follow link**. Ví dụ, nếu bên trong `safe-root` có một symbolic link dẫn ra ngoài, candidate có thể `startsWith(base)` theo cú pháp nhưng truy cập file thực lại đi ra ngoài cây mong muốn.

Với đầu vào không tin cậy, còn phải xác định rõ chính sách **đường dẫn tuyệt đối, link, sự tồn tại của path, quyền truy cập và thao tác mở**; trong những trường hợp cần chống race kiểu time-of-check/time-of-use, xem `SecureDirectoryStream` hoặc cơ chế mở tương đương của hệ thống tệp khi khả dụng. Không nên làm bảo mật filesystem chỉ bằng một lệnh `normalize().startsWith(...)` rồi kết luận an toàn hoàn toàn.

Nếu yêu cầu đơn thuần là tổ chức đường dẫn logic, `toAbsolutePath().normalize()` và `startsWith` là công cụ đúng; nếu yêu cầu là **phân quyền file thực**, cần thêm chiến lược mạnh hơn.

## 14. Các lỗi thường gặp

| Sai lầm | Vì sao sai | Cách đúng |
|---|---|---|
| `Path.of("docs2", "a").startsWith(Path.of("docs"))` được kỳ vọng `true` | `docs2` không phải thành phần `docs` | So sánh theo name element |
| `path.endsWith(".java")` để kiểm tra extension | `endsWith` so phần tử tên, không so ký tự cuối | `getFileName().toString().endsWith(".java")` |
| `path.equals(other)` để kết luận hai file chắc chắn khác nhau | Hai alias/link có thể trỏ cùng file | Dùng `Files.isSameFile` nếu cần đối chiếu file thực |
| `path.normalize()` được kỳ vọng xác minh symlink | Normalize là lexical, không truy cập file system | Dùng cơ chế real-path/file access phù hợp |
| So `relative.equals(absolute)` rồi cho rằng hai đích vật lý khác nhau | Hai biểu diễn khác hệ quy chiếu | Đưa về một chuẩn đường dẫn phù hợp hoặc `isSameFile` |
| Dùng `toString().startsWith(...)` để chặn traversal | Tiền tố ký tự không biết ranh giới thành phần | So sánh `Path` đã xử lý phù hợp, cộng bảo vệ link/race |
| Cho rằng `equals` luôn case-sensitive | Provider quy định so sánh | Kiểm tra hành vi mục tiêu, tránh phụ thuộc platform |
| Cho rằng `startsWith` tự chuẩn hóa `.` hoặc `..` | Không có bảo đảm tự normalize | Chủ động `normalize()` theo mục đích |
| Dùng `compareTo` thay `startsWith` | So thứ tự không phải quan hệ cha–con | Dùng API thể hiện đúng điều cần hỏi |
| Tự nối đường dẫn bằng dấu `"/"` hoặc `"\\"` | Có thể không portable | Dùng `Path.of(...)`, `resolve(...)` |
| Giả định `getFileName()` không bao giờ null | Đường dẫn root có thể không có phần tử tên cuối | Kiểm tra `null` trước khi dùng |
| Coi lexical containment là bảo đảm chống link độc hại | Link có thể dẫn ra ngoài; file có thể đổi giữa check và open | Thiết kế bảo vệ ở thao tác file thực |

## 15. Checklist áp dụng nhanh

1. Xác định yêu cầu là **tiền tố/hậu tố thành phần**, **hậu tố chuỗi tên file**, **bằng nhau của Path** hay **cùng file thực**.
2. Dùng `startsWith(Path)` cho tiền tố thành phần và `endsWith(Path)` cho hậu tố thành phần.
3. Dùng `getFileName()` + thao tác `String` nếu muốn kiểm tra đuôi `.txt` hoặc tiền tố `report-` của **tên file**.
4. Dùng `equals` để so sánh lexical theo provider; không dùng nó để kết luận hai đường dẫn khác nhau không thể trỏ cùng file.
5. Cân nhắc `toAbsolutePath()`, `normalize()` và `toRealPath()` theo từng mục tiêu; mỗi API giải quyết một vấn đề khác nhau.
6. Đối với dữ liệu đầu vào không tin cậy, coi phép kiểm tra `startsWith(base)` chỉ là một lớp trong bảo vệ path traversal, không phải giải pháp chống symlink/race hoàn chỉnh.
7. Viết test có cả đường dẫn giống **tiền tố ký tự nhưng khác thành phần**, đường dẫn có `. / ..`, đường dẫn tương đối/tuyệt đối và khác biệt file system khi triển khai trên nhiều môi trường.

## 16. Tóm tắt

**`Path.startsWith` và `Path.endsWith` làm việc với các name element của đường dẫn, không cắt chuỗi ký tự tùy ý. `Path.equals` so sánh đường dẫn theo quy tắc provider, không tự xác minh đó là cùng file vật lý.** Để xử lý `. / ..`, sử dụng `normalize()` khi có ý nghĩa; để xác định cùng tài nguyên thật, xem `Files.isSameFile(...)` hoặc `toRealPath()`, đồng thời xử lý lỗi I/O. Với các bài toán kiểm soát truy cập, đừng coi so sánh lexical là kiểm tra bảo mật hoàn chỉnh.

### Tham khảo chính thức

- [Java SE 17 — Path](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html)
- [Java SE 17 — Files.isSameFile](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#isSameFile(java.nio.file.Path,java.nio.file.Path))
- [Oracle Java Tutorials — Path Operations](https://docs.oracle.com/javase/tutorial/essential/io/pathOps.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="path-008">URI, toUri và giới hạn chuyển đổi giữa các provider</a>

<details>
<summary>Xem chi tiết</summary>

# URI, Path.toUri() và giới hạn chuyển đổi giữa các FileSystemProvider trong Java

`java.nio.file.Path` biểu diễn một đường dẫn theo **quy tắc của một hệ thống tệp**. `java.net.URI` biểu diễn một **định danh tài nguyên có scheme** như `file:`, `jar:` hoặc `https:`. Trong Java NIO.2, `Path.toUri()` có thể biến một đường dẫn thành URI do provider của đường dẫn đó định nghĩa; `Path.of(URI)` hoặc `Paths.get(URI)` đi chiều ngược lại **chỉ khi có `FileSystemProvider` phù hợp và các điều kiện của provider được đáp ứng**.

Ba điều phải nhớ:

1. `Path.toUri()` **không tạo file**, không mở tài nguyên, cũng không hứa mọi URI đều là `file:`.
2. `Path.of(uri)` **không phải bộ tải URL hoặc phép chuyển đổi URI phổ quát**. Nó cần provider có thể diễn giải scheme và có thể cần một `FileSystem` đang mở.
3. Kết quả và khả năng chuyển đổi hai chiều **phụ thuộc provider**. URI `file:` của filesystem mặc định khác URI `jar:` của ZIP filesystem, và `https:` thông thường không thể dùng trực tiếp như một `Path` Java NIO.

Bài học dùng Java 17+, tập trung vào `Path`, `URI`, `toUri()`, `Path.of(URI)`, `FileSystems`, `FileSystemProvider`, ranh giới giữa provider và các trường hợp lỗi dễ gây nhầm lẫn.

## 1. Path và URI giải quyết hai bài toán khác nhau

| API | Mô hình | Ví dụ | Điều quan trọng |
| --- | --- | --- | --- |
| `Path` | Đường dẫn **trong một FileSystem** | `Path.of("docs", "guide.txt")` | Có provider, filesystem, root và quy tắc path riêng |
| `URI` | Định danh tài nguyên theo cú pháp chung | `URI.create("https://example.com/a")` | Có scheme và các thành phần URI; không mặc định là file cục bộ |
| `Path.toUri()` | Chuyển `Path` sang URI do provider biểu diễn | `path.toUri()` | Dạng URI và bảo đảm round-trip tùy provider |
| `Path.of(URI)` | Yêu cầu provider diễn giải URI thành `Path` | `Path.of(fileUri)` | Có thể thành công hoặc ném ngoại lệ |
| `FileSystemProvider` | Nhà cung cấp cách xử lý đường dẫn/file | Provider mặc định, ZIP/JAR provider | Quy định scheme, kiểu Path và cách truy cập |

Sơ đồ:

```text
                     +----------------------+
                     | Path                 |
                     | thuộc FileSystem A   |
                     +----------------------+
                             |
                             | path.toUri()
                             v
                    URI (scheme tùy A)
                             |
                             | Path.of(uri)
                             | chọn provider theo scheme
                             v
                FileSystemProvider tương ứng
                             |
                +------------+-------------+
                |                          |
         provider & filesystem       thiếu provider / chưa mở
         có thể giải quyết URI       filesystem / URI không hợp lệ
                |                          |
                v                          v
           Path mới              Exception / không chuyển được
```

Đối tượng `Path` có thể tồn tại ngay cả khi file chưa tồn tại, tương tự `java.io.File`. `URI` thuần túy cũng không xác nhận tài nguyên đang tồn tại. Tạo `Path`/`URI` và ghi dữ liệu xuống filesystem là những hành động khác nhau.

### Hai chiều chuyển đổi quen thuộc

```java
Path path = Path.of("docs", "hello.txt");
URI uri = path.toUri();
Path restored = Path.of(uri);
```

Đây là mảnh mã; cần import `java.nio.file.Path` và `java.net.URI`. Trong filesystem mặc định, URI tạo ra thường mang scheme `file`, ở dạng tuyệt đối. Đường dẫn nhận lại tương ứng đường dẫn **tuyệt đối**, không nhất thiết bằng chuỗi đường dẫn tương đối ban đầu.

Không viết `Path.of(uri.toString())` để thay cho `Path.of(uri)`: overload nhận `String` hiểu chuỗi đó theo **cú pháp đường dẫn của hệ điều hành**, không phân tích scheme URI; trên Windows, `file:...` có thể là đường dẫn bất hợp lệ, còn trên Unix có thể bị hiểu thành tên khác với mong muốn.

## 2. Path.toUri(): kết quả là URI tuyệt đối, không nhất thiết là file đang có

Chữ ký:

```java
URI uri = path.toUri();
```

Với provider mặc định, `toUri()` tạo một URI **tuyệt đối** có scheme `file`, biểu diễn đường dẫn tuyệt đối. Ví dụ (chỉ minh họa, phụ thuộc OS và thư mục làm việc):

```text
Path tương đối: docs/hello world.txt
Trên Unix có thể thành: file:///home/user/project/docs/hello%20world.txt
Trên Windows có thể thành: file:///C:/work/project/docs/hello%20world.txt
```

Đặc điểm:

- `path.toUri().isAbsolute()` là `true` vì kết quả có scheme.
- `path.toUri()` **không yêu cầu file vật lý đã tồn tại**. Có thể chuyển đường dẫn tới tên dự định tạo trong tương lai.
- Với default provider, đường dẫn tương đối được chuyển thành dạng tương ứng **đường dẫn tuyệt đối** khi tạo URI.
- Dấu cách, ký tự `#` và một số ký tự đặc biệt được **percent-encode** khi cần; ví dụ `hello #.txt` có phần tương ứng `hello%20%23.txt` trong dạng ASCII URI.
- Khi có thể xác định đích là thư mục, URI của đường dẫn thư mục theo provider mặc định có thể kết thúc bằng `/`. Không dựa vào dấu `/` cuối như phép xác thực duy nhất rằng tài nguyên là thư mục.
- URI này không chứng minh file có tồn tại, có quyền đọc, hay thực sự là một file thường.

`Path.toUri()` là phép tạo biểu diễn URI, không tương đương `toRealPath()`. `toRealPath()` truy vấn đường dẫn thật trên filesystem và thường yêu cầu tài nguyên tồn tại; `toUri()` không cung cấp cùng bảo đảm.

### Ví dụ chứng minh không có I/O tạo file

```java
Path missing = Path.of("never-created.txt");
URI uri = missing.toUri();
System.out.println(Files.exists(missing)); // Thường false nếu chưa được tạo
```

Đây là mảnh mã với import `java.nio.file.*` và `java.net.URI`. Kết quả `exists` phụ thuộc thư mục làm việc, vì file tên đó có thể đã tồn tại trước khi chạy. Ý chính là: bản thân `toUri()` **không gọi API tạo file**.

## 3. Quy tắc round-trip của filesystem mặc định

Trong Java SE 17, provider mặc định có bảo đảm quan trọng: khi tạo và dùng các đối tượng **trong cùng JVM**, phép chuyển `Path` → `URI` → `Path` bằng provider mặc định thỏa mãn ý tưởng:

```java
Path p = Path.of("docs", "hello.txt");
boolean same = Path.of(p.toUri()).equals(p.toAbsolutePath());
```

Đây là mảnh mã, cần import `java.nio.file.Path`. Với provider mặc định theo hợp đồng Java, kết quả là `true` trong bối cảnh áp dụng bảo đảm đó. Đối với `p` ban đầu là **tương đối**, không nên so sánh `Path.of(p.toUri())` trực tiếp với `p` rồi ngạc nhiên khi `equals` trả `false`: bên trái đã mang đường dẫn tuyệt đối.

Nhưng **bảo đảm này không được tự động áp dụng cho mọi provider khác**. Một provider có thể dùng URI riêng, yêu cầu filesystem đã mở, hoặc không cho phép tái tạo đường dẫn chỉ dựa vào một URI được lưu bên ngoài. Ngoài ra:

- `Path.equals` thường là **so sánh theo cấu trúc Path/provider**, không phải bằng chứng hai đường dẫn trỏ tới cùng một thực thể vật lý.
- `Files.isSameFile(p1, p2)` là API khác để kiểm tra hai đường dẫn có cùng file thực (có thể đụng filesystem và ném `IOException`); nó không thay thế cú pháp round-trip.
- `toAbsolutePath()` không phải `toRealPath()`. Đường dẫn tuyệt đối có thể vẫn chứa các thành phần biểu diễn chưa được giải quyết theo trạng thái thực của filesystem.
- Nếu truyền URI qua máy khác/OS khác, chuỗi URI mặc định được tạo trên Windows không được bảo đảm sẽ biến thành cùng một `Path` trên Linux, và ngược lại.

Tính round-trip nên được hiểu trong **hợp đồng của provider và môi trường cụ thể**, không phải cam kết URI có thể gắn vào bất kỳ runtime nào.

## 4. URI là đối tượng có scheme, không phải tất cả URI là file

`URI` có các thành phần cú pháp khác nhau, chẳng hạn:

```text
https://example.com/docs/guide.html?lang=vi#intro
\___/  \_________/ \______________/ \_____/ \___/
scheme  authority         path         query fragment

file:///tmp/hello%20world.txt
\__/   \_____________________/
scheme          raw path

jar:file:///tmp/sample.zip!/docs/guide.txt
\_/  \_________________________________/
scheme       scheme-specific part
```

Các phương thức hữu ích của `URI`:

| Phương thức | Ý nghĩa |
| --- | --- |
| `getScheme()` | Scheme như `file`, `jar`, `https` |
| `isAbsolute()` | URI có scheme hay không; khác `Path.isAbsolute()` |
| `isOpaque()` | URI thuộc loại opaque hay hierarchical |
| `getPath()` | Path **đã percent-decode** cho URI hierarchical, hoặc `null` nếu không có thành phần đó |
| `getRawPath()` | Path còn escape phần ký tự theo URI, hoặc `null` |
| `getSchemeSpecificPart()` | Thành phần phụ thuộc scheme (dạng đã decode) |
| `toASCIIString()` | Biểu diễn ASCII với escaping thích hợp |
| `resolve(...)` | Giải quyết URI tham chiếu theo **quy tắc URI**, không phải `Path.resolve` |

Một URI opaque như `jar:file:/...!/entry` thường có `getPath() == null`. Vì thế đừng lấy `uri.getPath()` rồi áp dụng `Path.of(...)` với mọi URI. Đừng tự cắt `jar:`/`file:` từ chuỗi theo `substring(...)` hay giải mã phần `%20` thủ công; URI có thể bao gồm các delimiter mang ý nghĩa riêng của provider.

Ví dụ đường dẫn có dấu `#`:

```java
Path p = Path.of("hello #.txt");
URI uri = p.toUri();
System.out.println(uri.getFragment()); // null với URI được tạo cho tên file đó
```

Dấu `#` **nằm trong tên file**, nên `toUri` phải escape nó thay vì diễn giải như fragment. Ngược lại, nếu người lập trình tự ghép chuỗi `"file:///.../hello #.txt"` bằng `URI.create(...)`, dấu khoảng trắng khiến URI không hợp lệ và có thể gây `IllegalArgumentException`. Hãy dùng `Path.toUri()` hoặc constructor `URI` phù hợp thay vì tự ghép URI từ path thô.

## 5. Path.of(URI) và Paths.get(URI): yêu cầu provider xử lý URI

Hai API tương ứng:

```java
Path path1 = Path.of(uri);  // Java 11+
Path path2 = Paths.get(uri); // API có từ Java 7
```

Đây là mảnh mã cần import `java.net.URI` và `java.nio.file.*`. Cả hai dựa trên hệ thống **`FileSystemProvider` được cài đặt/khám phá** để xử lý scheme. Không nên tự diễn giải rằng chúng chỉ dành cho `file:`, nhưng cũng không nghĩ chúng tự hỗ trợ mọi URI.

Quy trình logic:

1. Nhận `URI`.
2. Xem scheme, chọn provider phù hợp.
3. Yêu cầu provider tạo `Path` trỏ vào filesystem tương ứng.
4. Provider có thể trả Path hoặc báo rằng URI sai định dạng, filesystem chưa tồn tại, chưa được mở, hay provider không được cài.

Những ngoại lệ có thể gặp (tùy tình huống/provider):

- `IllegalArgumentException`: URI không đáp ứng yêu cầu cú pháp của provider hoặc đối số không hợp lệ.
- `FileSystemNotFoundException`: không tìm được filesystem phù hợp, chẳng hạn một ZIP filesystem đã đóng hoặc chưa được khởi tạo theo cách mà provider tra cứu được.
- `ProviderNotFoundException`: thao tác tạo filesystem không tìm thấy provider cho scheme theo hợp đồng của API có liên quan. Không suy diễn mọi lời gọi `Path.of(URI)` khi thiếu scheme đều nhất thiết ném đúng lớp này.
- `SecurityException` hoặc ngoại lệ truy cập có thể xuất hiện khi hệ thống bảo mật/chính sách truy cập từ chối một số thao tác.

Đừng viết:

```java
Path remote = Path.of(URI.create("https://example.com/file.txt"));
```

rồi cho rằng Java tự tải dữ liệu HTTP. Một URI `https:` là locator web; Java NIO mặc định không có filesystem provider HTTP có thể diễn giải trực tiếp như `Path`. Nếu muốn tải nội dung web, dùng `HttpClient` hoặc API mạng phù hợp, kiểm soát timeout, lỗi HTTP, kích thước, URL redirect và bảo mật. Sau khi tải, bạn có thể lưu bằng `Files.write(...)` vào một Path cục bộ **riêng biệt**.

**Một URI hợp lệ không đảm bảo chuyển được thành Path.** Đây là ranh giới thiết kế giữa định danh tài nguyên và namespace filesystem.

## 6. Ví dụ Java 17+ chạy được: file URI, ký tự escape và path chưa tồn tại

Tạo file `PathUriDefaultDemo.java`:

```java
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

public class PathUriDefaultDemo {
    public static void main(String[] args) throws Exception {
        Path relative = Path.of("folder", "hello #.txt");
        URI uri = relative.toUri();

        System.out.println("scheme: " + uri.getScheme());
        System.out.println("URI absolute: " + uri.isAbsolute());
        System.out.println("path relative: " + relative.isAbsolute());
        System.out.println("escaped name: "
                + (uri.toASCIIString().contains("%20")
                && uri.toASCIIString().contains("%23")));
        System.out.println("fragment: " + uri.getFragment());
        System.out.println("round trip: "
                + Path.of(uri).equals(relative.toAbsolutePath()));

        Path root = Files.createTempDirectory("path-uri-");
        try {
            Path missing = root.resolve("not-created.txt");
            URI missingUri = missing.toUri();
            System.out.println("exists before: " + Files.exists(missing));
            System.out.println("same path: " + Path.of(missingUri).equals(missing));
            System.out.println("exists after: " + Files.exists(missing));
        } finally {
            Files.deleteIfExists(root);
        }
    }
}
```

Chạy:

```bash
javac PathUriDefaultDemo.java
java PathUriDefaultDemo
```

Stdout trên một filesystem mặc định có quyền tạo thư mục tạm, không bị tác nhân khác thay đổi dữ liệu giữa các lệnh:

```text
scheme: file
URI absolute: true
path relative: false
escaped name: true
fragment: null
round trip: true
exists before: false
same path: true
exists after: false
```

Tại sao đầu ra ổn định dù đường dẫn tuyệt đối phụ thuộc máy?

- Không in nguyên URI thực tế; chỉ kiểm tra scheme, trạng thái absolute, encoding ký tự cần escape và round-trip.
- `relative` vẫn là Path tương đối, trong khi `relative.toUri()` cho URI tuyệt đối.
- Chuỗi `hello #.txt` có dấu cách và `#`. URI có các escape `%20` và `%23`; không xuất hiện fragment.
- `Files.createTempDirectory` **thực sự tạo một thư mục tạm**; ngược lại `missing.toUri()` và `Path.of(missingUri)` không tạo `not-created.txt`.
- `Path.of(missingUri).equals(missing)` so sánh hai Path tuyệt đối cùng provider, không yêu cầu file tồn tại.
- `finally` xóa thư mục tạm đã tạo; chương trình không phụ thuộc giá trị động của tên thư mục để tạo output.

Chỉ lệnh `Files.createTempDirectory` và `Files.deleteIfExists` có tác dụng thay đổi filesystem trong ví dụ. Các phép chuyển `Path`/`URI` không tạo tài nguyên.

## 7. FileSystemProvider là gì?

`FileSystemProvider` trong `java.nio.file.spi` là điểm mở rộng cho các kiểu hệ thống tệp. Provider quyết định cách tạo filesystem, tạo Path từ URI, đọc thuộc tính, mở kênh và thực hiện thao tác file. Ví dụ:

- **Filesystem mặc định** của hệ điều hành: đường dẫn cục bộ, scheme thường là `file`.
- **JDK ZIP/JAR filesystem provider** (khi có module `jdk.zipfs`): cho phép làm việc với các mục nằm trong một ZIP/JAR như Path, scheme `jar`.
- Các provider bên thứ ba: có thể truy cập filesystem trong bộ nhớ, kho dữ liệu đặc thù, hệ thống tệp ảo hoặc đích khác tùy triển khai.

Thao tác `path.getFileSystem().provider().getScheme()` cho biết scheme của provider sở hữu `path`. Tuy nhiên, chỉ biết **tên scheme** chưa đủ để đảm bảo URI sẽ tái tạo một Path từ bất cứ tiến trình nào: có thể còn yêu cầu cài provider, cấu hình thông số, quyền truy cập, đăng ký filesystem hoặc mở kết nối.

Ví dụ:

```java
Path local = Path.of("hello.txt");
String scheme = local.getFileSystem().provider().getScheme();
// Ở filesystem mặc định phổ biến: scheme là "file".
```

Đây là mảnh mã cần `java.nio.file.Path`. Tên scheme khác nhau giữa các provider, và không thể bắt mọi provider phải dùng cú pháp `file:/...` hoặc cung cấp URL truy cập được bên ngoài JVM.

## 8. ZIP filesystem: một file vật lý có nhiều Path bên trong

Một archive `sample.zip` có thể chứa nhiều entry như `/notes/readme.txt`. Với ZIP filesystem provider, hai loại Path sau rất khác nhau:

```text
Filesystem mặc định                       ZIP filesystem
----------------------------------        --------------------------------
Path.of("/tmp/sample.zip")                 zip.getPath("/notes/readme.txt")
  -> trỏ tới archive vật lý                -> trỏ tới entry *bên trong* archive

file:///tmp/sample.zip                     jar:file:///tmp/sample.zip!/notes/readme.txt
scheme: file                               scheme: jar
```

URI `jar:` (với provider ZIP/JAR phù hợp) chứa cả **URI của archive** và **đường dẫn entry**. Ký hiệu `!` phân tách theo cú pháp ZIP/JAR provider, không phải quy tắc chung của mọi URI.

Vì vậy không thể lấy `URI.getPath()` của `jar:...` rồi giả định đó là đường dẫn filesystem mặc định. Một URI `jar:` thường opaque, nên `getPath()` là `null`. Bạn phải dùng provider tương ứng.

Quan trọng: `Path.of(jarUri)` có thể **không hoạt động ngay cả khi URI đúng định dạng**, nếu ZIP FileSystem chưa được mở/đăng ký theo cách provider có thể tra cứu. Một ZIP archive tồn tại trên đĩa không tự làm cho FileSystem của các entry bên trong tồn tại trong JVM.

### Ví dụ Java 17+ chạy được với ZIP filesystem

Tạo `ZipUriProviderDemo.java`:

```java
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class ZipUriProviderDemo {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("zip-uri-");
        Path archive = root.resolve("sample.zip");

        // URI của filesystem ZIP: jar:<URI của archive>
        URI zipFileSystemUri = URI.create("jar:" + archive.toUri());
        URI entryUri;

        try {
            // Mở ZIP FS bằng URI để provider đăng ký nó cho việc lookup.
            try (FileSystem zip = FileSystems.newFileSystem(
                    zipFileSystemUri, Map.of("create", "true"))) {

                Path entry = zip.getPath("/lesson.txt");
                Files.writeString(entry, "hello", StandardCharsets.UTF_8);

                entryUri = entry.toUri();

                System.out.println("entry scheme: " + entryUri.getScheme());
                System.out.println("entry opaque: " + entryUri.isOpaque());
                System.out.println("provider scheme: "
                        + entry.getFileSystem().provider().getScheme());
                System.out.println("round trip while open: "
                        + Path.of(entryUri).equals(entry));
            }

            // FileSystem đã đóng; lookup URI entry có thể thất bại.
            try {
                Path.of(entryUri);
                System.out.println("round trip after close: available");
            } catch (FileSystemNotFoundException e) {
                System.out.println("round trip after close: unavailable");
            }
        } finally {
            Files.deleteIfExists(archive);
            Files.deleteIfExists(root);
        }
    }
}
```

Chạy với JDK 17+ có ZIP filesystem provider:

```bash
javac ZipUriProviderDemo.java
java ZipUriProviderDemo
```

Stdout đã kiểm tra trên JDK có `jdk.zipfs`:

```text
entry scheme: jar
entry opaque: true
provider scheme: jar
round trip while open: true
round trip after close: unavailable
```

Giải thích:

1. `archive.toUri()` là URI của file ZIP trong filesystem **mặc định** (`file:`).
2. `URI.create("jar:" + archive.toUri())` biểu diễn một ZIP filesystem để provider `jar` có thể mở.
3. `FileSystems.newFileSystem(zipFileSystemUri, Map.of("create", "true"))` tạo/mở ZIP filesystem; khi archive chưa tồn tại, tùy chọn `create` cho phép tạo archive theo cơ chế ZIP provider.
4. `zip.getPath("/lesson.txt")` là Path **thuộc ZIP filesystem**, không phải file `lesson.txt` cạnh archive ngoài đĩa.
5. `entry.toUri()` có scheme `jar`; trong trường hợp này URI là opaque.
6. Trong khi ZIP FileSystem được mở/đăng ký theo URI, `Path.of(entryUri)` có thể tìm lại entry Path.
7. Sau `close()`, không còn filesystem mở tương ứng; lookup có thể ném `FileSystemNotFoundException`. Việc file ZIP vật lý vẫn tồn tại **không có nghĩa** ZIP FileSystem vẫn được mở.

Đây là ví dụ có chủ ý phụ thuộc **ZIP FileSystemProvider có sẵn trong JDK**. Các runtime Java cắt giảm module có thể thiếu `jdk.zipfs` và không chạy được phần ZIP; điều đó chính là một minh họa cho giới hạn provider.

**Chi tiết nâng cao:** Mở ZIP FileSystem bằng overload `FileSystems.newFileSystem(Path, Map)` có thể không đăng ký thể hiện đó để `Path.of(jarUri)` tìm kiếm giống với overload nhận **URI**, tùy triển khai provider. Vì vậy không khẳng định “filesystem đang mở” là điều kiện duy nhất; cách nó được tạo và cơ chế lookup cũng quan trọng.

## 9. Các lỗi khi chuyển URI thành Path và cách xử lý

### 9.1. Không hỗ trợ scheme

`URI.create("https://example.com/a")` là URI hợp lệ, nhưng không phải URI của provider `file` hoặc `jar` mặc định. `Path.of(...)` không biến HTTP thành file cục bộ. Nếu ứng dụng có provider bên thứ ba hỗ trợ một scheme nào đó, khả năng xử lý sẽ phụ thuộc chính provider đó; đừng bắt cứng những giả định về mọi runtime.

### 9.2. Provider có nhưng FileSystem chưa mở

URI một entry ZIP như `jar:file:///tmp/a.zip!/x.txt` cần `jar` provider và filesystem thích hợp. Khi chưa mở, đã đóng, hoặc được đăng ký khác cách, `Path.of(uri)` có thể ném `FileSystemNotFoundException`. Hướng xử lý là dùng `FileSystems.newFileSystem(...)`/`getFileSystem(...)` theo hợp đồng ZIP provider, **không** đổi bừa scheme sang `file`.

### 9.3. URI sai yêu cầu của provider

Đối với default `file` provider, URI thường phải là file URI **hierarchical, tuyệt đối**, có path phù hợp và không chứa các thành phần tùy tiện như query/fragment; authority cũng phải được xử lý theo quy tắc provider/nền tảng. Ví dụ các URI dựng thủ công không đúng yêu cầu có thể làm `Path.of(uri)` ném `IllegalArgumentException`.

Đừng tự ghép:

```java
URI bad = URI.create("file:///tmp/hello world.txt");
```

Vì dấu cách không được escape trong chuỗi URI thô; `URI.create` ném `IllegalArgumentException` trước cả bước tạo Path. Một đường dẫn thật nên được tạo bằng `Path.of(...)` rồi gọi `toUri()` để Java xử lý escaping.

### 9.4. Khác máy và khác provider

Một URI `file:///C:/...` có thể là file path hợp lý trên Windows, nhưng không thể giả định nó sẽ có cùng ý nghĩa trên hệ điều hành khác. Với filesystem bên thứ ba, khả năng round-trip ở JVM khác còn phụ thuộc cài đặt provider, trạng thái filesystem và cấu hình truy cập.

Trường hợp xử lý URI từ bên ngoài hệ thống, nên kiểm tra:

- Scheme nằm trong **allowlist** mà ứng dụng thực sự hỗ trợ.
- Provider có sẵn, và filesystem/credential cần thiết đã được khởi tạo.
- Cấu trúc URI hợp lệ với provider; không tự parse cú pháp của provider khi chưa cần.
- Đường dẫn sau khi chuyển không vi phạm phạm vi cho phép, quyền truy cập hoặc chính sách bảo mật của ứng dụng.
- Nếu muốn đọc file thật, gọi `Files.newInputStream(...)`, `Files.readString(...)` hoặc API tương ứng và xử lý `IOException`; chỉ có `Path` không đồng nghĩa truy cập thành công.

### Một ví dụ bắt lỗi theo trường hợp

```java
try {
    Path result = Path.of(uri);
    // result chỉ là Path; thao tác I/O thực sự vẫn cần xử lý riêng.
} catch (FileSystemNotFoundException e) {
    // Provider có thể không tìm thấy một filesystem đã mở phù hợp.
} catch (IllegalArgumentException e) {
    // URI không đáp ứng yêu cầu của provider / đầu vào không hợp lệ.
}
```

Đây là **mảnh mã**: cần `java.net.URI`, `java.nio.file.*`; biến `uri` đã tồn tại. Nếu nghiệp vụ dùng provider tùy biến, còn phải hiểu các loại exception khác theo API/triển khai. Không nên nuốt lỗi mà giả vờ URI là một đường dẫn cục bộ.

## 10. File.toURI(), Path.toUri() và URI.toURL()

Các API thường dễ bị trộn lẫn:

| API | Input → output | Dùng khi nào |
| --- | --- | --- |
| `new File(name)` | Tên đường dẫn → `File` | Làm việc với API `java.io.File` |
| `file.toURI()` | `File` → `URI` | Cần URI cho đường dẫn thuộc mô hình File truyền thống |
| `file.toPath()` | `File` → `Path` | Chuyển sang NIO |
| `path.toUri()` | `Path` → `URI` | Provider của Path quyết định dạng URI |
| `Path.of(uri)` | `URI` → `Path` | Chỉ khi provider cho scheme và filesystem phù hợp |
| `uri.toURL()` | `URI` → `URL` | Chuyển thành đối tượng URL nếu scheme/handler hỗ trợ; không tự động đọc |
| `Path.of(uri.toString())` | Chuỗi URI → tên path thô | **Không phải** chuyển URI đúng chuẩn; tránh dùng |

Ví dụ với default filesystem:

```java
File legacy = new File("notes.txt");
URI legacyUri = legacy.toURI();

Path modern = legacy.toPath();
URI modernUri = modern.toUri();
```

Mảnh mã cần `java.io.File`, `java.net.URI`, `java.nio.file.Path`. Hai URI có thể biểu diễn cùng địa chỉ tài nguyên, nhưng không nên xem chuỗi của mọi overload/API luôn đồng nhất trong mọi hoàn cảnh. Khi bạn đang giữ `Path` của một provider phi mặc định (chẳng hạn ZIP), **`Path.toUri()`** phản ánh provider sở hữu Path; việc chuyển ép mọi thứ qua `java.io.File` không phải lúc nào cũng được hỗ trợ (ví dụ `Path.toFile()` có thể ném `UnsupportedOperationException` nếu Path không thuộc filesystem mặc định).

Nếu cần đọc nội dung từ HTTP, `URI.toURL()`/`URL.openStream()` hoặc `HttpClient` là một tầng mạng khác, không tương đương tạo `Path`. Với mã hiện đại, `HttpClient` thường cho phép quản lý timeout, phản hồi HTTP và lỗi rõ ràng hơn.

## 11. Vì sao không nên tự parse hoặc tự ghép URI file?

Giả sử tên tệp chứa khoảng trắng, `#`, `%` hay ký tự Unicode. Việc tự ghép:

```java
String text = "file://" + path.toString();
```

có thể tạo URI sai vì:

- `Path.toString()` dùng separator/syntax của provider; trên Windows có thể chứa `\`, ổ đĩa hoặc UNC, không phù hợp ghép trực tiếp như URL.
- `#` có thể bị hiểu là fragment thay vì phần tên tệp.
- `%` có thể bị hiểu là khởi đầu một escape sequence; tự encode hai lần cũng gây lỗi.
- URI opaque của provider như `jar` có cấu trúc đặc biệt; không thể áp đặt cú pháp `file:`.
- Đường dẫn tương đối không tự trở thành một URI file tuyệt đối đúng quy tắc.

Thay vào đó:

```java
Path path = Path.of("folder", "hello #.txt");
URI uri = path.toUri();
```

Đó là mảnh mã cần import `java.nio.file.Path`, `java.net.URI`. Nếu phải làm việc với thành phần URI HTTP, xây dựng URI theo cú pháp URI/URL, không dùng `Path` để tạo URL web.

### getPath() và getRawPath() không giống nhau

`uri.getPath()` trả phần đường dẫn đã decode các escape URI, còn `uri.getRawPath()` giữ các escape như `%20`. Nếu bạn gọi `URLDecoder.decode(...)` thêm một lần hoặc lấy chuỗi đã decode rồi ghi lại vào URI không đúng cách, có thể gây **double decoding** hoặc thay đổi nghĩa của ký tự đặc biệt như `+`, `%` và `#`.

Khi cần chuyển **file URI thực** sang `Path`, dùng `Path.of(uri)`. Khi chỉ hiển thị thành phần URI, hãy phân biệt raw/decoded theo nhu cầu. Với provider `jar`, `getPath()` có thể không có giá trị vì URI là opaque.

## 12. Bảo mật, dữ liệu không đáng tin và filesystem scope

Một URI hoặc đường dẫn được nhận từ API/người dùng không tự động an toàn. Một số lưu ý:

1. **Không cho phép mọi scheme** nếu ứng dụng chỉ hỗ trợ `file`. Scheme tùy ý có thể kích hoạt provider hoặc cách truy cập không mong muốn.
2. **Không coi `normalize()` là kiểm tra an toàn hoàn chỉnh**: nó chỉ xử lý thành phần cú pháp `.`/`..` và không tự xác nhận đường dẫn thực. Symbolic link có thể thay đổi đích.
3. Nếu bắt buộc giới hạn trong một thư mục gốc, kiểm tra và thao tác theo chính sách phù hợp, xét `toRealPath()`/`Files.isSameFile` khi cần và xử lý ngoại lệ. Cẩn thận race giữa thời điểm kiểm tra và mở.
4. Không dùng `Files.exists(path)` làm bảo đảm file luôn còn đó khi đọc. Filesystem có thể thay đổi tức thời.
5. Không mặc định hệ thống ngoài JVM hiểu cùng provider hoặc thông tin credential. Một Path của provider ảo không nhất thiết có biểu diễn file cục bộ.
6. Một URI có thể là **định danh**, không phải quyền được phép truy cập. Việc chuyển thành Path thành công chưa chứng minh có quyền đọc/ghi.
7. Khi tạo tài nguyên thật, chọn thao tác NIO phù hợp như `Files.createFile`, `Files.newInputStream`, `Files.writeString` với option và ngoại lệ được kiểm soát.

Ví dụ ứng dụng nhận URI bên ngoài chỉ cho phép `file`:

```java
if (!"file".equalsIgnoreCase(uri.getScheme())) {
    throw new IllegalArgumentException("Only file: URIs are supported");
}
Path path = Path.of(uri);
// Tiếp tục kiểm tra phạm vi ứng dụng/đường dẫn thật trước khi mở I/O.
```

Đây chỉ là **kiểm tra scheme**, chưa đủ là rào bảo mật đầy đủ cho mọi đường dẫn file. Muốn hạn chế truy cập trong một thư mục an toàn phải thiết kế logic cấp quyền và chống traversal/symlink/race phù hợp.

## 13. Các sai lầm thường gặp

| Sai lầm | Hậu quả | Cách đúng |
| --- | --- | --- |
| Nghĩ `toUri()` tạo file | Kết luận tài nguyên đã tồn tại | `toUri()` chỉ tạo URI; dùng `Files` khi cần I/O |
| Dùng `Path.of(uri.toString())` | Chuỗi URI bị hiểu như path thô | `Path.of(uri)` |
| Giả định mọi `Path.toUri()` là `file:` | Sai với ZIP/JAR/custom provider | Hỏi scheme/provider thực |
| Giả định mọi URI hợp lệ đều chuyển thành Path | Thiếu provider/filesystem | Chỉ chuyển URI mà NIO provider hỗ trợ |
| Dùng `Path.of(httpsUri)` để tải file web | Không phải thao tác HTTP | Dùng API mạng rồi lưu xuống Path khác |
| Bỏ qua `FileSystemNotFoundException` với URI JAR | Không tìm thấy ZIP filesystem cần thiết | Mở/đăng ký provider filesystem phù hợp |
| Cho rằng ZIP archive tồn tại là ZIP FS đã mở | URI entry vẫn không round-trip được | Quản lý vòng đời `FileSystem` |
| Gọi `uri.getPath()` với `jar:` | Có thể nhận `null` | Dùng provider, xem opaque URI |
| Tự ghép `"file://" + path` | Sai escaping, slash, Windows path | `path.toUri()` |
| Coi `getPath()` và `getRawPath()` giống nhau | Sai phần percent-encoding | Phân biệt decoded/raw |
| So sánh Path sau round-trip trực tiếp với Path tương đối | Kết luận sai do absolute/relative | So sánh theo hợp đồng provider, dùng `toAbsolutePath()` |
| Nghĩ round-trip cục bộ được đảm bảo trên máy khác | Sai khác hệ điều hành/provider | Chỉ dựa vào bảo đảm trong phạm vi tài liệu xác nhận |
| Đổi Path ZIP sang `toFile()` bất cứ lúc nào | Có thể ném `UnsupportedOperationException` | Dùng Files APIs trực tiếp trên Path |
| Đã có Path là đọc được file | Có thể không tồn tại/không có quyền | Thực hiện I/O, xử lý `IOException` |
| Dùng `normalize()` làm rào chống traversal duy nhất | Bỏ qua symlink/race | Thiết kế kiểm tra bảo mật riêng |

## 14. Tự kiểm tra kiến thức

1. **`Path.toUri()` có tạo file vật lý không?** Không; nó chỉ tạo URI biểu diễn Path theo provider.
2. **URI của Path tương đối trên default provider thường là tương đối hay tuyệt đối?** URI tuyệt đối với scheme `file`, tương ứng đường dẫn tuyệt đối.
3. **`Path.of(uri)` có hỗ trợ URI `https:` mặc định như một file cục bộ?** Không. Đó là thao tác provider của NIO, không phải HTTP download.
4. **Vì sao `Path.of(path.toUri())` không nên so sánh trực tiếp với path tương đối?** Kết quả round-trip của default provider tương ứng `path.toAbsolutePath()`.
5. **Tại sao tên file `hello #.txt` chứa `%23` trong URI?** Vì `#` phải escape để không bị xem là fragment.
6. **Scheme của Path bên trong ZIP FS thường là gì?** `jar` khi dùng JDK ZIP filesystem provider.
7. **Vì sao `Path.of(jarUri)` có thể ném `FileSystemNotFoundException`?** Không tìm thấy ZIP filesystem đang mở/đăng ký tương ứng với URI.
8. **URI `jar:...` có luôn có `getPath()` không?** Không; thường opaque nên `getPath()` trả `null`.
9. **Nếu đã đóng ZIP FileSystem, file archive vật lý có còn không?** Có thể vẫn còn, nhưng Path/URI lookup cho entry cần filesystem được mở lại.
10. **`Path.toFile()` có hoạt động cho ZIP Path không?** Không được bảo đảm; Path ngoài filesystem mặc định có thể ném `UnsupportedOperationException`.
11. **Có thể dùng `Path.of(uri.toString())` thay `Path.of(uri)` không?** Không; hai overload diễn giải đầu vào theo hai mô hình khác nhau.
12. **Làm sao biết URI có thể thành Path?** Kiểm tra scheme/provider, điều kiện filesystem và xử lý lỗi từ `Path.of(URI)` theo hợp đồng provider.
13. **Round-trip có khẳng định file đang có hoặc có quyền truy cập không?** Không.
14. **Dùng API nào để đọc file thực sau khi có Path?** `Files.newInputStream`/`Files.readString` hoặc API phù hợp, với xử lý I/O.
15. **URI/Path ngoại lai có an toàn chỉ vì đã normalize?** Không; còn traversal, symlink, quyền và race.

## Tham khảo API Java 17

- [`Path` — `toUri()`, `toFile()`, `toAbsolutePath()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html).
- [`Path.of(URI)` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html#of(java.net.URI)).
- [`Paths.get(URI)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Paths.html).
- [`URI` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URI.html).
- [`FileSystemProvider` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/spi/FileSystemProvider.html).
- [`FileSystems` — `newFileSystem(...)` / `getFileSystem(...)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileSystems.html).
- [`FileSystemNotFoundException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileSystemNotFoundException.html).
- [`Files` — thao tác I/O thực sự](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html).
- [JDK ZIP File System Provider](https://docs.oracle.com/en/java/javase/17/docs/specs/zipfs.html).

**Kết luận:** `Path.toUri()` là chiếc cầu từ một đường dẫn thuộc **một FileSystem cụ thể** sang URI do provider định nghĩa. `Path.of(URI)` là yêu cầu **provider và FileSystem tương ứng** chuyển URI trở lại thành Path, không phải công cụ chuyển mọi URI thành file. Với default `file` provider có bảo đảm round-trip trong cùng JVM; với `jar`, ZIP hoặc provider bên thứ ba, phải xét scheme, cách mở filesystem, vòng đời và hợp đồng riêng của provider.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="path-009">Đường dẫn Windows, Unix, dấu phân cách và tính phân biệt hoa thường</a>

<details>
<summary>Xem chi tiết</summary>

# Đường dẫn Windows, Unix, dấu phân cách và tính phân biệt hoa thường trong Java `Path`

`java.nio.file.Path` biểu diễn **đường dẫn theo một hệ thống tệp (`FileSystem`) cụ thể**. Một chuỗi nhìn giống đường dẫn hợp lệ trên Windows có thể mang ý nghĩa hoàn toàn khác trên Unix/Linux, và ngược lại. Để viết Java I/O chạy được trên nhiều nền tảng, cần phân biệt:

1. **Dấu phân cách thư mục** (`/` hoặc `\`) với **dấu phân cách các phần tử trong danh sách đường dẫn** (`:` hoặc `;`).
2. **Quy tắc gốc và đường dẫn tuyệt đối**: `/home/...` trên Unix, `C:\...` và `\\server\share\...` trên Windows, cùng các dạng Windows *drive-relative*.
3. **Tính phân biệt hoa–thường**: phụ thuộc filesystem, volume, thư mục và đôi khi cấu hình provider; không thể quyết định chỉ từ `System.getProperty("os.name")`.
4. **Chuỗi đường dẫn so với đối tượng tệp thực tế**: `Path.equals()` khác về mục đích với `Files.isSameFile()`, còn `normalize()` không truy cập hệ thống tệp.
5. **Tính an toàn khi ghép đường dẫn**: xử lý `..`, symbolic link, tên file từ đầu vào bên ngoài và điều kiện thay đổi giữa lúc kiểm tra/sử dụng.

Quy tắc nền tảng: **hãy ghép các thành phần bằng `Path.of(...)`/`resolve(...)` thay vì tự nối chuỗi dấu `/` hoặc `\`**. Khi cần làm việc với một đường dẫn của hệ điều hành *khác* máy đang chạy Java, đừng giao nguyên chuỗi ấy cho default filesystem rồi giả định nó sẽ được phân tích theo cú pháp của hệ điều hành nguồn.

## 1. `Path` thuộc về `FileSystem`, không chỉ là `String`

Một đường dẫn có thể được biểu diễn bằng:

```java
Path path = Path.of("data", "reports", "summary.txt");
```

Đây là đoạn minh họa, cần `import java.nio.file.Path;`. `Path.of(...)` tạo đường dẫn trên **default filesystem** của JVM. Nó không tự tạo file, không cần file tồn tại, và không mở bất kỳ luồng dữ liệu nào. Dạng chuỗi trả về khi gọi `path.toString()` do filesystem/provider xác định.

Các phần của một `Path` thường gồm:

| Thành phần | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| Root | `/` trên Unix; `C:\` với một ổ đĩa Windows; `\\server\share\` của UNC | Điểm gốc theo quy tắc filesystem |
| Name elements | `data`, `reports`, `summary.txt` | Các thành phần đường dẫn |
| Parent | `data/reports` (viết theo kiểu Unix) | Phần đường dẫn trước tên cuối |
| File name | `summary.txt` | Thành phần cuối cùng, không phải tên file đã được kiểm tra tồn tại |
| Absolute path | `/home/ana/data/...` hoặc `C:\Users\Ana\data\...` | Đã có root theo filesystem tương ứng |
| Relative path | `data/reports/summary.txt` | Được diễn giải tương đối với ngữ cảnh làm việc |

`getRoot()`, `getParent()`, `getFileName()`, `getNameCount()`, `isAbsolute()` là các phép phân tích đường dẫn, **không phải phép truy vấn xem file tồn tại**. Ví dụ `getFileName()` có thể trả `null` với một số đường dẫn chỉ chứa root. `Path` còn có thể được tạo bởi filesystem khác default, ví dụ hệ thống tệp ZIP do provider hỗ trợ; vì vậy không nên mặc định rằng mọi `Path` đều mang quy tắc tên của đĩa cục bộ.

### Ví dụ 1 — ghép đường dẫn theo từng thành phần, không phụ thuộc dấu phân cách

```java
// PathComponentsDemo.java
import java.io.File;
import java.nio.file.Path;

public class PathComponentsDemo {
    public static void main(String[] args) {
        Path path = Path.of("data", "logs", "app.log");
        Path messy = Path.of("data", ".", "cache", "..", "report.txt");
        Path cleaned = messy.normalize();

        System.out.println("relative=" + !path.isAbsolute());
        System.out.println("nameCount=" + path.getNameCount());
        System.out.println("fileName=" + path.getFileName());
        System.out.println("parentName=" + path.getParent().getFileName());
        System.out.println("joinedCorrectly=" + path.toString().equals(
                "data" + File.separator + "logs"
                        + File.separator + "app.log"));
        System.out.println("cleanedNames=" + cleaned.getNameCount());
        System.out.println("cleanedFileName=" + cleaned.getFileName());
    }
}
```

Biên dịch/chạy bằng Java 17+:

```bash
javac PathComponentsDemo.java
java PathComponentsDemo
```

Đầu ra trên default filesystem Windows hoặc Unix thông dụng:

```text
relative=true
nameCount=3
fileName=app.log
parentName=logs
joinedCorrectly=true
cleanedNames=2
cleanedFileName=report.txt
```

Trên Windows, `path.toString()` thường dùng `data\logs\app.log`; trên Unix, thường dùng `data/logs/app.log`. Bài kiểm tra `joinedCorrectly` dựa trên `File.separator` của chính nền tảng hiện tại nên vẫn cho cùng kết quả. `normalize()` rút gọn `.` và cặp thành phần `cache/..` theo **ngữ nghĩa đường dẫn thuần túy**, không kiểm tra `data` hay `cache` có tồn tại.

### Relative path không tự dựa vào thư mục chứa mã nguồn

`Path.of("data", "file.txt")` là tương đối. Khi một API cần giải quyết nó thành đường dẫn tuyệt đối, ngữ cảnh thường là **working directory của tiến trình Java**. Không giả định Java tự dùng thư mục đặt file `.java`, thư mục IDE project, hay vị trí `.class`. Các môi trường IDE, CI, container và dịch vụ hệ thống có thể khởi chạy ứng dụng với working directory khác nhau.

Dùng `toAbsolutePath()` nếu cần xem đường dẫn tuyệt đối theo default filesystem; nếu muốn xác định rõ gốc dữ liệu của ứng dụng, hãy truyền một `Path base` đã chọn, rồi gọi `base.resolve(...)`.

## 2. Dấu phân cách thư mục của Windows và Unix

### Unix/Linux và nhiều hệ thống POSIX

Đường dẫn Unix thông dụng dùng dấu **`/`**:

```text
/etc/hosts
/home/ana/projects/report.txt
var/log/app.log
```

- `/` ở đầu thường chỉ **root**.
- `var/log/app.log` không có `/` ở đầu là đường dẫn **tương đối**.
- Dấu gạch chéo ngược `\` **không phải** dấu phân cách thư mục của filesystem Unix thông thường; nó có thể là ký tự thuộc tên file (dù shell thường dùng backslash làm ký tự escape khi nhập lệnh).
- Tên file trên Unix thường không chứa `/` và ký tự NUL. Những quy tắc khác (Unicode, độ dài tên, normalization) còn tùy hệ thống tệp/provider.

### Windows

Windows thường hiển thị dấu **`\`**:

```text
C:\Users\Ana\report.txt
D:\data\log.txt
\\server\share\reports\summary.txt
```

- `C:\Users\Ana\report.txt` có drive `C:` cùng root `\`, là dạng **absolute** theo cú pháp Windows.
- `\\server\share\reports\summary.txt` là **UNC path** (đường dẫn mạng) gồm server, share và những thành phần phía sau.
- Trên default Windows filesystem của Java, dấu `/` thường cũng được chấp nhận như dấu phân cách đầu vào: `C:/Users/Ana/report.txt`. Tuy nhiên không suy rộng khả năng này cho mọi API Windows, tên dạng mở rộng hoặc provider khác.
- Windows có các dạng đường dẫn không có nghĩa tương đương nhau: `C:notes.txt` và `C:\notes.txt` **không giống nhau**.

### Ba dạng đường dẫn Windows dễ bị lẫn

| Dạng | Cách hiểu trên Windows |
| --- | --- |
| `C:\data\file.txt` | Absolute với root trên ổ `C:` |
| `C:data\file.txt` | **Drive-relative**: tương đối với thư mục hiện tại gắn với ổ `C:`, không phải mặc nhiên `C:\data\file.txt` |
| `\data\file.txt` | Root-relative trên **ổ hiện hành** theo ngữ cảnh Windows; không chỉ định ổ độc lập |
| `data\file.txt` | Relative thuần túy |
| `\\server\share\file.txt` | UNC, gốc được xác định bởi server/share |

Đừng xử lý đường dẫn `C:folder` bằng cách kiểm tra nó “bắt đầu bằng `C:`” rồi kết luận đã tuyệt đối. Muốn biết absolute theo provider hiện tại, dùng `path.isAbsolute()`. Lưu ý: nếu Java chạy trên Linux thì default provider **không phân tích `C:` thành drive Windows**, nên `Path.of("C:\\data\\file.txt").isAbsolute()` không thể thay cho phép kiểm tra cú pháp Windows ở hệ thống khác.

## 3. Hai loại dấu phân cách rất khác nhau

`java.io.File` cung cấp hai nhóm hằng số:

| Hằng số | Mục đích | Giá trị phổ biến trên Unix | Giá trị phổ biến trên Windows |
| --- | --- | --- | --- |
| `File.separator` | Phân cách **thư mục** trong một đường dẫn | `/` | `\` |
| `File.separatorChar` | Phiên bản `char` của `separator` | `/` | `\` |
| `File.pathSeparator` | Phân cách **nhiều đường dẫn** trong danh sách, như classpath | `:` | `;` |
| `File.pathSeparatorChar` | Phiên bản `char` của `pathSeparator` | `:` | `;` |

Ví dụ:

```text
Unix PATH:    /usr/local/bin:/usr/bin:/bin
Windows PATH: C:\Windows\System32;C:\Tools
```

Không được dùng `File.pathSeparator` để ghép hai thư mục. Nó dùng cho **danh sách nhiều mục đường dẫn**. Ngược lại, không được dùng `File.separator` để nối các phần tử classpath. Các giá trị trên là **phổ biến trên các nền tảng**, không phải một lời hứa rằng mọi provider ảo dùng đúng quy tắc của default filesystem.

API đáng ưu tiên khi xây dựng một path:

```java
Path file = Path.of("documents", "2026", "report.txt");
// Thay cho "documents/" + "2026/" + "report.txt".
```

Với classpath do launcher Java xử lý, cấu trúc phân cách danh sách do nền tảng quy định. Hạn chế tự thao tác classpath như một chuỗi nếu đã có API cho mục đích đó.

### Ví dụ 2 — quan sát dấu phân cách và chuỗi đường dẫn

```java
// PathSeparatorsDemo.java
import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;

public class PathSeparatorsDemo {
    public static void main(String[] args) {
        Path path = Path.of("src", "main", "App.java");

        System.out.println("directorySeparator=" + File.separator);
        System.out.println("pathListSeparator=" + File.pathSeparator);
        System.out.println("defaultFsSeparator="
                + FileSystems.getDefault().getSeparator());
        System.out.println("constructed=" + path);
        System.out.println("nameCount=" + path.getNameCount());
    }
}
```

```bash
javac PathSeparatorsDemo.java
java PathSeparatorsDemo
```

Đầu ra ví dụ **trên Unix/Linux**:

```text
directorySeparator=/
pathListSeparator=:
defaultFsSeparator=/
constructed=src/main/App.java
nameCount=3
```

Đầu ra ví dụ **trên Windows**:

```text
directorySeparator=\
pathListSeparator=;
defaultFsSeparator=\
constructed=src\main\App.java
nameCount=3
```

Đây là ví dụ minh họa hai môi trường điển hình. Cần đọc giá trị từ máy thật khi chạy; không nên tự suy từ `os.name` rồi hard-code logic. `FileSystems.getDefault()` liên quan default filesystem; một `Path` của provider khác có thể có quy tắc riêng, kiểm tra bằng `path.getFileSystem().getSeparator()`.

## 4. Dấu `\` trong Java source code cũng là ký tự escape

Một bẫy thường gặp: **cú pháp Java string literal** khác cú pháp filesystem.

Ví dụ Java đúng khi muốn tạo các chuỗi chứa dấu `\` thực:

```java
String windowsAbsolute = "C:\\Users\\Ana\\report.txt";
String windowsUnc = "\\\\server\\share\\report.txt";
String unixAbsolute = "/home/ana/report.txt";
```

Đây là các **đoạn minh họa**, cần đặt trong một lớp/phương thức Java nếu muốn biên dịch. Trong một Java string literal:

- `\\` ở **source code** biểu diễn **một** dấu backslash trong chuỗi runtime.
- `\n` là newline, `\t` là tab, `\r` là carriage return; nếu viết `"C:\new\test.txt"` có thể vô tình chèn ký tự điều khiển thật vào chuỗi thay vì các đoạn thư mục `new` và `test`.
- Chuỗi `"C:\Users\Ana\report.txt"` không escape đúng `\U`, `\A` và có thể **không biên dịch**.

**Phân biệt ba lớp khác nhau:** Chuỗi mà Java biên dịch tạo ra, cách filesystem provider diễn giải các ký tự ấy, và cách một shell (PowerShell/bash) diễn giải tham số dòng lệnh. Một backslash có thể là escape ở lớp này, nhưng lại chỉ là ký tự thông thường ở lớp khác.

### Trên Unix, backslash không tự biến thành directory separator

Nếu code Java chạy trên Linux/Unix:

```java
Path foreign = Path.of("C:\\Users\\Ana\\report.txt");
```

Đây là **đoạn minh họa** có import `Path`. Provider Unix mặc định không hiểu chuỗi trên thành đường dẫn `C:` và các thư mục `Users`, `Ana`. Nó có thể coi phần lớn chuỗi thành **một tên tương đối chứa dấu backslash**.

Nếu ứng dụng phải xử lý *văn bản đường dẫn Windows* khi chạy trên Unix (ví dụ đọc một file manifest từ máy Windows), bạn cần mô hình/logic phân tích cú pháp nguồn được xác định rõ. Không thể giả sử `Path.of(rawWindowsText)` trên Unix làm nhiệm vụ đó. Nếu muốn thao tác file thực trên **máy hiện tại**, cách bền vững hơn là nhận các **thành phần đường dẫn logic** và ghép bằng `Path.of(base, segments...)` theo provider hiện tại.

Ngược lại, một đường dẫn Unix `/var/log/...` có ý nghĩa với filesystem Unix; chạy nó qua Windows default provider không tự tạo ra Linux root `/var`.

## 5. Có nên tự gọi `replace("\\", "/")` không?

Không dùng phép thay thế này như một “công cụ chuyển đổi đường dẫn đa nền tảng” tổng quát.

- Trên Unix, `\` có thể là **ký tự tên file hợp lệ**, nên thay bằng `/` có thể đổi tên file thành nhiều thư mục.
- Trên Windows, một dấu backslash có thể thuộc phần UNC/root hoặc cú pháp mở rộng; thay đổi tùy tiện có thể làm sai ngữ nghĩa trong một API cụ thể.
- Một file path có thể xuất hiện bên trong **URI**, **URL**, **JSON**, **shell command** hoặc regex. Mỗi tầng có bộ quy tắc escape riêng; không thể dùng `replace()` ở mọi tầng.
- Các filesystem ảo như ZIP và provider tùy biến có thể có cấu trúc khác default filesystem.

Nếu nhận một *tên đường dẫn logic do ứng dụng quy định*, hãy định nghĩa input gồm các phần độc lập, ví dụ `["users", "ana", "report.txt"]`, rồi ghép. Nếu nhận một chuỗi đường dẫn **từ hệ thống khác**, cần parser/metadata nói rõ nó đến từ hệ thống nào, chứ không thể đoán từ một dấu.

### Tránh `String.split(File.separator)`

`String.split()` nhận **regex**, không nhận dấu phân cách literal. Trên Windows, `File.separator` thường là `\`, vốn là escape character trong regex; dùng thẳng `text.split(File.separator)` có thể gây `PatternSyntaxException`.

Với văn bản có delimiter được kiểm soát, `Pattern.quote(delimiter)` có thể giải quyết phần **escape regex**. Nhưng nó không giải quyết root của đường dẫn, UNC, chuỗi có nhiều dấu phân cách, hoặc sự khác nhau của filesystem:

```java
// Đoạn minh họa cho một chuỗi dữ liệu được biết trước, không
// thay cho parser đường dẫn thật.
String[] tokens = text.split(
        java.util.regex.Pattern.quote(File.separator), -1);
```

Khi mục tiêu là thao tác đường dẫn thật của provider hiện tại, dùng `Path`:

```java
Path parent = path.getParent();
Path leaf = path.getFileName();
```

`Path` có khả năng phân tích các thành phần theo quy tắc filesystem tốt hơn việc tự tách chuỗi theo delimiter.

## 6. Phân biệt hoa–thường thuộc về filesystem, không chỉ hệ điều hành

Dễ gặp hai kiểu:

- **Case-sensitive:** `README.md` và `readme.md` có thể là **hai file khác nhau**.
- **Case-insensitive, case-preserving:** khi người dùng tạo `README.md`, filesystem có thể lưu cách viết ấy nhưng tra cứu bằng `readme.md` vẫn trỏ tới cùng tệp.

Ví dụ theo thực tế **thường gặp**, không phải định luật:

| Hệ thống | Hành vi phổ biến | Điều cần thận trọng |
| --- | --- | --- |
| Linux + ext4 cấu hình thông thường | Case-sensitive | Có filesystem/ thư mục được cấu hình khác, ví dụ cơ chế casefold |
| Windows + NTFS cấu hình thông thường | Thường case-insensitive, giữ nguyên hoa–thường | Windows/NTFS có trường hợp điều chỉnh hành vi theo thư mục hoặc cấu hình; không suy mọi volume giống nhau |
| macOS + APFS | Có thể case-insensitive hoặc case-sensitive tùy volume | Không suy từ `os.name` rằng luôn là một kiểu |
| Network filesystem, archive, ZIP, virtual providers | Do server, provider và cấu hình quyết định | Không mặc định cùng quy tắc với máy chạy chương trình |

Điều này ảnh hưởng tới tên resource, cấu hình build, đường dẫn trong Git repository, bộ nhớ cache theo pathname, so khớp tên file và logic bảo mật. Một dự án tạo cả `config.json` lẫn `Config.json` có thể hoạt động trên filesystem phân biệt hoa–thường nhưng gây xung đột khi triển khai sang filesystem không phân biệt.

### Đừng tự dùng `equalsIgnoreCase()` để nhận diện cùng một file

So sánh hai chuỗi bằng `equalsIgnoreCase()` không mô phỏng chính xác mọi quy tắc ánh xạ tên file của provider. Các filesystem còn có thể áp dụng những quy tắc Unicode normalization/case folding hoặc namespace đặc biệt. Mặt khác, hai đường dẫn khác chuỗi vẫn có thể trỏ tới một đối tượng thật do symbolic link, hard link hoặc cùng một tài nguyên có nhiều tên.

Có ba câu hỏi khác nhau:

1. **Hai chuỗi có giống nhau?** `text1.equals(text2)` trả lời theo ký tự Java.
2. **Hai đối tượng `Path` có bằng nhau theo logic provider?** `path1.equals(path2)` trả lời theo hợp đồng `Path`/provider; thường không truy cập filesystem và không phải phép thử file identity.
3. **Hai đường dẫn hiện trỏ đến cùng file?** `Files.isSameFile(path1, path2)` kiểm tra thực thể file qua provider và có thể ném `IOException`.

Ngay cả khi `Path.equals(...)` trả `true`, đừng coi đó là biện pháp ủy quyền truy cập hoặc bảo đảm file đã tồn tại. Hai `Path` bằng nhau không giải quyết mọi vấn đề symbolic link, thay đổi filesystem hoặc truy cập.

### Ví dụ 3 — quan sát môi trường chạy thay vì giả định case sensitivity

```java
// PathCaseDemo.java
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class PathCaseDemo {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("path-case-");
        Path original = directory.resolve("Report.txt");
        Path alternate = directory.resolve("report.txt");
        Path dotted = directory.resolve(".").resolve("Report.txt");

        try {
            Files.writeString(original, "data", StandardCharsets.UTF_8);

            System.out.println("originalExists=" + Files.exists(original));
            System.out.println("sameThroughDot="
                    + Files.isSameFile(original, dotted));

            boolean alternateExists = Files.exists(alternate);
            System.out.println("alternateExists=" + alternateExists);

            if (alternateExists) {
                System.out.println("alternateSameFile="
                        + Files.isSameFile(original, alternate));
            } else {
                System.out.println("alternateSameFile=not-checked");
            }
        } finally {
            Files.deleteIfExists(original);
            Files.deleteIfExists(directory);
        }
    }
}
```

Chạy Java 17+:

```bash
javac PathCaseDemo.java
java PathCaseDemo
```

Hai dòng đầu khi các thao tác tạo tệp thành công:

```text
originalExists=true
sameThroughDot=true
```

Các dòng `alternateExists` và `alternateSameFile` **phụ thuộc filesystem**. Trên filesystem phân biệt hoa–thường thông thường, có thể thấy:

```text
alternateExists=false
alternateSameFile=not-checked
```

Trên filesystem không phân biệt hoa–thường thông thường, có thể thấy:

```text
alternateExists=true
alternateSameFile=true
```

Những kết quả trên là **trường hợp minh họa**, không phải stdout cố định cho mọi Windows/macOS/Linux. Bài test tạo **một file duy nhất** rồi hỏi về hai cách viết tên; không cố tạo đồng thời hai tên vốn có thể trùng nhau trên hệ thống không phân biệt hoa–thường.

`Files.isSameFile()` làm việc với các đối tượng đang tồn tại và có thể báo `IOException` nếu file biến mất hoặc không truy vấn được. Nó cung cấp thông tin về identity tại thời điểm kiểm tra, chứ không khóa file để bảo đảm identity không thay đổi về sau.

## 7. Các thao tác `Path`: `resolve`, `normalize`, `toAbsolutePath`, `toRealPath`

### `resolve(...)`

```java
Path base = Path.of("data");
Path output = base.resolve("reports").resolve("summary.txt");
```

Đây là đoạn minh họa: `output` là đường dẫn ghép theo provider hiện tại. **Nếu đối số `resolve` là absolute**, nhiều trường hợp kết quả sẽ trở thành chính đường dẫn absolute ấy thay vì tiếp tục nằm trong `base`; hợp đồng cụ thể với rooted/drive-relative path phải xem theo provider. Không bao giờ dùng `base.resolve(userProvidedPath)` rồi tự khẳng định kết quả luôn ở dưới base.

### `normalize()`

`normalize()` xóa các thành phần `.` và cặp `name/..` có thể giản lược **theo cú pháp**, không truy cập filesystem. Nó không mở/kiểm tra symbolic link, không xác định file có tồn tại.

```text
data/logs/../report.txt
  -> sau normalize(): data/report.txt
```

Trong một đường dẫn có symbolic link, bỏ `name/..` theo cú pháp có thể **khác** với việc đi qua filesystem thật. Vì vậy `normalize()` không phải phép kiểm tra bảo mật hoàn chỉnh hay phép tìm “đích thực” của link.

### `toAbsolutePath()`

Lấy đường dẫn tuyệt đối theo ngữ cảnh filesystem hiện tại. Nó không mặc nhiên xác định target, không mở file và không đảm bảo tồn tại.

### `toRealPath(...)`

Thường yêu cầu đường dẫn thực sự tồn tại và có thể truy cập; phương thức có thể ném `IOException`, có xử lý thành phần `.`/`..` và symbolic link theo tùy chọn. Nếu dùng `LinkOption.NOFOLLOW_LINKS`, ngữ nghĩa với link thay đổi theo hợp đồng. `toRealPath()` có ích khi cần biết vị trí thực tế được provider cung cấp, **nhưng không khóa trạng thái đó trong suốt các thao tác về sau**.

| Phương thức | Có truy cập filesystem? | Có thể xác nhận target tồn tại? | Ghi nhớ |
| --- | --- | --- | --- |
| `normalize()` | Không | Không | Biến đổi cú pháp, không giải symbolic link |
| `toAbsolutePath()` | Không dùng như phép kiểm tra tồn tại | Không | Bổ sung ngữ cảnh root/working directory |
| `toRealPath(...)` | Có | Thông thường cần target tồn tại để thành công | Có thể ném IOException; tùy chọn link quan trọng |
| `Files.exists(path)` | Có kiểm tra trạng thái | Trả boolean khi provider xác định được | `false` không luôn chứng minh chắc chắn không tồn tại |
| `Files.isSameFile(a, b)` | Có thể phải truy vấn danh tính file | Kiểm tra identity, có thể ném IOException | Không thay cho chính sách quyền và chống race |

## 8. `startsWith()` và dấu phân cách: kiểm tra ranh giới thành phần

Một lỗi thường thấy khi xét đường dẫn nằm dưới root:

```java
String candidate = "/srv/app-data/file.txt";
boolean inside = candidate.startsWith("/srv/app");
```

Đây là **đoạn minh họa logic sai**: so sánh chuỗi có thể cho `true` dù `/srv/app-data` không phải con của `/srv/app`.

`Path.startsWith(Path)` so theo **thành phần đường dẫn của provider**, thay vì tiền tố ký tự mù quáng. Với các đường dẫn cùng loại, cùng filesystem, nó tránh lỗi ranh giới tên kiểu `app`/`app-data`. Tuy nhiên bước này **chỉ kiểm tra quan hệ đường dẫn theo ngữ nghĩa `Path`**, không bảo đảm an ninh trước symbolic link, junction và race.

### Ví dụ 4 — kiểm tra quan hệ *lexical* một cách tường minh

```java
// PathLexicalBoundaryDemo.java
import java.nio.file.Path;

public class PathLexicalBoundaryDemo {
    public static void main(String[] args) {
        Path base = Path.of("workspace", "allowed")
                .toAbsolutePath().normalize();

        Path inside = base.resolve("docs")
                .resolve("..").resolve("readme.txt").normalize();

        Path outside = base.resolve("..")
                .resolve("allowed-backup").resolve("readme.txt").normalize();

        System.out.println("insideLexically=" + inside.startsWith(base));
        System.out.println("outsideLexically=" + outside.startsWith(base));
    }
}
```

Chạy:

```bash
javac PathLexicalBoundaryDemo.java
java PathLexicalBoundaryDemo
```

Đầu ra với default Windows/Unix filesystem thông dụng:

```text
insideLexically=true
outsideLexically=false
```

Đây **không phải** một kiểm tra an toàn cho đường dẫn không tin cậy trong môi trường có symbolic link hoặc đối thủ thay đổi filesystem. Nó chỉ chứng minh `Path.startsWith` quan sát **ranh giới thành phần** đúng hơn `String.startsWith`.

Nếu ứng dụng phải giới hạn truy cập trong một thư mục:

- Quyết định chính sách có cho phép symbolic link/junction hay không.
- Xác định root đáng tin cậy và tránh dùng tên file từ người dùng để tạo absolute path ngoài phạm vi.
- Không xem `normalize().startsWith(root)` là phép xác thực đủ trong môi trường có cạnh tranh.
- Xem xét API thao tác tương đối với directory handle, `SecureDirectoryStream` khi provider hỗ trợ, các tùy chọn không theo symbolic link và chính sách quyền hệ điều hành.
- Xử lý lỗi khi mở/đọc/ghi thật, không chỉ kiểm tra rồi tin trạng thái sẽ không đổi (**TOCTOU: time-of-check to time-of-use**).

## 9. Quy tắc file name cũng có thể khác nhau

Không chỉ dấu phân cách và chữ hoa–thường, filesystem còn có khác biệt về tên:

- Windows thông thường hạn chế một số ký tự trong thành phần tên file (chẳng hạn `<>:"/\|?*`), đồng thời có các tên thiết bị hoặc quy tắc đặc biệt; cấu hình/API có thể thay đổi một số giới hạn.
- Unix/Linux thông thường không cho `/` và NUL trong tên phần tử; những ký tự như `:` hoặc `\` có thể là ký tự tên hợp lệ.
- Độ dài, khoảng trắng cuối tên, dấu chấm cuối tên, Unicode normalization và tên tương đương có thể được xử lý khác theo provider và nền tảng.
- Một tên được chấp nhận khi tạo `Path` chưa chắc file **tạo được**: thao tác `Files.createFile(...)` còn có thể lỗi do quyền, policy hoặc giới hạn filesystem.

Nếu tên file đến từ người dùng hoặc hệ thống ngoài, không tự viết “bảng thay ký tự” chung mà giả định sẽ hợp lệ trên mọi filesystem. Hãy xác định phạm vi ứng dụng, chuẩn hóa **tên logic** ở tầng ứng dụng, giới hạn thành phần rỗng/`..` khi cần, và để thao tác thực tế báo lỗi qua exception. `InvalidPathException` có thể xuất hiện ngay khi phân tích chuỗi thành `Path`; `IOException` xuất hiện khi thao tác I/O thất bại.

Một quy ước ứng dụng tương đối dễ di chuyển là đặt tên đơn giản bằng chữ/số ASCII, dấu gạch ngang và gạch dưới, nhưng đó chỉ là **quy ước do ứng dụng chọn**, không phải tập ký tự duy nhất hợp lệ của mọi filesystem. Đồng thời phải xử lý xung đột tên nếu triển khai sang hệ thống không phân biệt hoa–thường.

## 10. Các trường hợp trong phát triển và triển khai

### 10.1. Dự án chạy trên máy cá nhân nhưng lỗi trong CI

Máy phát triển có thể có filesystem case-insensitive, còn runner Linux thường dùng filesystem case-sensitive. Dòng code:

```java
Path config = Path.of("config", "Settings.json");
```

Có thể hoạt động cục bộ khi file thật tên `settings.json`, nhưng lỗi khi triển khai vào môi trường phân biệt chữ hoa–thường. Cách sửa là **thống nhất chính xác cách viết tên file trong code/config và dữ liệu đóng gói**, không phải tự dùng `equalsIgnoreCase()` để tìm mọi file gần giống.

### 10.2. Build hoặc script tự nối dấu phân cách

```java
// Cách không nên dùng:
String location = root + "\\" + "data" + "\\" + "result.txt";
```

Đoạn minh họa trên giả định biến `root` tồn tại. Nó mang giả định Windows và có thể tạo tên file chứa `\` trên Unix. Dùng `Path base = Path.of(root); Path location = base.resolve("data").resolve("result.txt");`, rồi truyền `Path` vào `Files`/API tương ứng.

### 10.3. File path trong URL khác file path của hệ điều hành

URL và URI dùng cú pháp riêng. Đừng lấy đường dẫn Windows `C:\data\a.txt` rồi tự nối `"file://" + path`: dấu backslash, space và các ký tự đặc biệt cần tuân cú pháp URI. Khi đã có `Path`, dùng `path.toUri()` nếu cần `file:` URI; ở chiều ngược lại, dùng API chuyển URI sang `Path` phù hợp provider. **Không** dùng URI path string như một đường dẫn local mà chưa chuyển qua API.

### 10.4. Symbolic link và case sensitivity trong cache

Một cache dựa trên `Path.toString()` có thể tạo nhiều khóa cho cùng một file hoặc gộp sai các file khác nhau khi so sánh không đúng. Nếu cache phụ thuộc danh tính đối tượng thực, cân nhắc thuộc tính file, `Files.isSameFile`, `fileKey()` khi provider hỗ trợ, và ranh giới vòng đời của cache. Không khẳng định một cách chuẩn hóa chuỗi duy nhất luôn tương ứng 1:1 với file vật lý ở mọi hệ thống.

### 10.5. So sánh root trên các filesystem khác nhau

Hai `Path` đến từ những provider khác nhau không nhất thiết so sánh/resolve như các path cùng provider. Một số thao tác có thể ném `ProviderMismatchException`. Dùng provider của chính `Path` (`path.getFileSystem()`) khi cần thao tác tương thích; không cố trộn `Path` ZIP với `Path` default rồi giả định có một dấu phân cách toàn cục.

## 11. Lỗi thường gặp

| Lỗi | Hậu quả | Cách sửa |
| --- | --- | --- |
| Luôn nối `"/"` hoặc `"\\"` thủ công | Đường dẫn không tương thích provider/OS | `Path.of(segments...)`, `resolve(...)` |
| Nhầm `File.pathSeparator` với `File.separator` | Dùng `;`/`:` giữa thư mục hoặc `\`/`/` để ghép classpath | Hiểu rõ directory separator khác path-list separator |
| Truyền `"C:\\foo\\bar"` vào `Path.of` trên Unix và nghĩ đó là Windows path | Sai root, sai thành phần | Dùng provider/cú pháp đúng với nguồn dữ liệu |
| Coi `C:foo` là absolute Windows path | Thư mục hiện hành của drive có thể ảnh hưởng kết quả | Phân biệt drive-relative và drive-rooted |
| Viết `String.split(File.separator)` | Trên Windows có thể lỗi regex | Ưu tiên `Path` hoặc `Pattern.quote` cho văn bản đơn giản |
| Dựa vào `os.name` để suy case-sensitivity | Sai với volume/thư mục cấu hình khác | Kiểm tra theo filesystem và xử lý tồn tại/thao tác thật |
| Dùng `equalsIgnoreCase()` để nhận biết cùng file | Gộp sai hoặc vẫn bỏ sót alias/symlink | Hiểu `Path.equals`, dùng `Files.isSameFile` nếu cần identity |
| Nghĩ `normalize()` giải symbolic link | Có thể sai thực thể file | Phân biệt lexical normalization với `toRealPath()` |
| Dùng `String.startsWith(base)` để kiểm tra child path | Tên `app-data` có thể trông như con của `app` | `Path.startsWith` cho thành phần, và biện pháp bảo mật bổ sung |
| Quên kiểm tra absolute RHS khi gọi `resolve` | Input có thể thay root dự kiến | Ràng buộc input và kiểm tra ngữ nghĩa đường dẫn |
| Chỉ gọi `Files.exists()` rồi mở | File/quyền có thể thay đổi trong lúc chờ | Bắt `IOException` tại thao tác thật, tránh TOCTOU |
| Giả định path thực luôn giữ nguyên | Symbolic link, mount, rename có thể thay đổi | Xem xét handle và cơ chế bảo vệ phù hợp |

## 12. Câu hỏi tự kiểm tra

**Câu 1.** `File.separator` và `File.pathSeparator` khác gì?

**Đáp án:** `separator` phân cách **các thư mục trong một path**; `pathSeparator` phân cách **nhiều path trong một danh sách** như PATH/classpath. Thường là `/` và `:` trên Unix; `\` và `;` trên Windows.

**Câu 2.** `Path.of("data", "logs", "app.log")` có cần tự chọn dấu `/` hay `\` không?

**Đáp án:** Không. Default filesystem/provider xử lý các thành phần và định dạng đường dẫn.

**Câu 3.** Trên Windows, `C:notes.txt` có giống `C:\notes.txt` không?

**Đáp án:** Không. Dạng đầu là **drive-relative**; dạng sau có root trên drive `C:`.

**Câu 4.** Chạy trên Unix, `Path.of("C:\\work\\note.txt")` có tự phân tích thành root Windows không?

**Đáp án:** Không. Default provider Unix không hiểu cú pháp ổ đĩa Windows theo nghĩa native Windows filesystem.

**Câu 5.** File `Readme.txt` và `README.txt` có luôn là hai file khác nhau không?

**Đáp án:** Không. Tùy filesystem, volume/thư mục và cấu hình phân biệt hoa–thường. Cũng không được đoán chắc chỉ theo `os.name`.

**Câu 6.** Vì sao `Path.equals()` không phải phép kiểm tra hai path trỏ cùng file?

**Đáp án:** Nó so sánh các đối tượng path theo quy tắc provider, không bắt buộc truy cập filesystem. `Files.isSameFile()` được thiết kế cho câu hỏi về identity file hiện tại và có thể ném `IOException`.

**Câu 7.** `normalize()` và `toRealPath()` khác nhau cơ bản ở đâu?

**Đáp án:** `normalize()` chỉ biến đổi cú pháp; `toRealPath()` truy vấn filesystem, xử lý đường dẫn thực theo các tùy chọn và có thể ném `IOException`.

**Câu 8.** `normalizedCandidate.startsWith(normalizedRoot)` đã đủ đảm bảo người dùng không truy cập ra ngoài thư mục gốc?

**Đáp án:** Chưa. Nó hữu ích cho kiểm tra **lexical** nhưng không xử lý hết symbolic link, junction, thay đổi đồng thời và chính sách truy cập. Cần kiểm soát nguồn và sử dụng các cơ chế I/O/bảo mật phù hợp.

**Câu 9.** Vì sao `String.split(File.separator)` có thể lỗi trên Windows?

**Đáp án:** `split` dùng **regex**, trong đó `\` là ký tự escape; đường phân cách `\` không thể dùng như regex literal mà không quote đúng. Với Path thật, hãy dùng API `Path` thay vì tách string.

**Câu 10.** Khi cần tạo `file:` URI từ một `Path`, nên tự ghép `"file://"` với `Path.toString()` không?

**Đáp án:** Không. Dùng `path.toUri()` để chuyển theo API, tránh lỗi escape và cú pháp URI.

**Ghi nhớ:** `Path` là **đối tượng thuộc một filesystem/provider**, không phải chuỗi phân cách tùy tiện. Ghép tên bằng `Path.of(...)`/`resolve(...)`, phân biệt `/`, `\`, `:` và `;`, không nhầm Windows drive-relative với absolute, không mặc định filesystem nào cũng phân biệt hoa–thường giống nhau. Phân biệt **so sánh chuỗi**, **so sánh Path**, **danh tính file thực**, và nhớ rằng phép chuẩn hóa đường dẫn không thay thế bảo vệ truy cập vào filesystem.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="path-010">Path không bảo đảm đích đang tồn tại</a>

<details>
<summary>Xem chi tiết</summary>

# Path không bảo đảm đích đang tồn tại trong Java NIO

`java.nio.file.Path` mô tả **vị trí/đường dẫn của một mục trên hệ thống tập tin**, chứ không phải một file hoặc thư mục đã được mở và xác nhận tồn tại. Chương trình có thể tạo một `Path` tới đường dẫn chưa có file, đã bị xóa, không được phép truy cập, hoặc tới một file sẽ được tạo trong tương lai:

~~~java
Path file = Path.of("data", "today.txt");
~~~

Câu lệnh này chỉ tạo **đối tượng đường dẫn**. Nó không tạo `today.txt`, không kiểm tra quyền truy cập và không đảm bảo mục ở đích thực sự tồn tại. Đây là nguyên tắc cốt lõi cần nắm trước khi dùng `Files.exists`, `Files.readString`, `Files.createFile`, `Path.toRealPath` hoặc các phương thức tương tự.

## 1. Mục tiêu bài học

Sau bài này, bạn có thể:

- Phân biệt **Path là tên/vị trí** với **file-system entry** là đối tượng thực tế được truy cập.
- Giải thích các phương thức `Path.of`, `resolve`, `getFileName`, `getParent`, `normalize`, `toAbsolutePath` hoạt động thế nào mà không bảo đảm đích tồn tại.
- Sử dụng `Files.exists` và `Files.notExists` mà không nhầm `false` với kết luận chắc chắn.
- Biết lúc nào cần `Path.toRealPath` hoặc `Files.readAttributes`, và giới hạn của một lần kiểm tra thành công.
- Thực hiện trực tiếp các thao tác `Files.readString`, `Files.newInputStream`, `Files.createFile`, `Files.createDirectories`... rồi xử lý `IOException` phù hợp.
- Nhận diện vấn đề **TOCTOU** (*time of check to time of use*), symbolic link, thay đổi đồng thời, đường dẫn không hợp lệ và khác biệt giữa file system providers.
- Viết bài kiểm thử không phụ thuộc đường dẫn có sẵn trên máy người học.

## 2. `Path` là abstraction cho đường dẫn, không phải file handle

`Path` là một **interface** thuộc `java.nio.file`, biểu diễn đường dẫn do một `FileSystem` cụ thể quản lý. Đường dẫn có thể tương đối hoặc tuyệt đối, có nhiều name component và có thể được kết hợp, chuẩn hóa hoặc so sánh.

Ví dụ:

~~~java
import java.nio.file.Path;

Path a = Path.of("reports", "2026.txt");
Path b = a.getParent();           // reports
Path c = b.resolve("draft.txt");  // reports/draft.txt
Path d = a.toAbsolutePath();
Path e = a.normalize();
~~~

Đây là **mảnh mã** (đặt trong một phương thức hoặc khối Java). Việc chạy thành công những lời gọi này **không hề chứng minh** file `reports/2026.txt`, thư mục `reports` hoặc `draft.txt` tồn tại.

Hãy xem hai tầng khác nhau:

~~~text
Tầng biểu diễn đường dẫn:
Path.of / resolve / getParent / normalize / toAbsolutePath
              |
              | tạo và biến đổi đường dẫn
              v
       Path object trong JVM
              |
              | truyền cho thao tác thực tế
              v
Tầng truy cập file system:
Files.readString / Files.newInputStream / Files.createFile /
Files.readAttributes / Path.toRealPath / Files.delete / ...
              |
              v
  Thao tác với mục tồn tại hoặc thất bại có nguyên nhân
~~~

Điểm tách này giúp chương trình có thể lập kế hoạch đường dẫn trước khi tạo file, ví dụ tạo `Path` cho file báo cáo tháng tới, hoặc tính tên đích trước khi thực hiện `Files.move`.

Một **`Path` hợp lệ về mặt cú pháp** và một **mục file system đang tồn tại** là hai khái niệm độc lập. Ngược lại, ngay cả khi file thực tế tồn tại thì đối tượng `Path` cũng không tự giữ cho file ấy tồn tại: tiến trình khác có thể xóa hoặc thay thế mục đó sau thời điểm bạn tạo `Path`.

## 3. Ví dụ Java 17+: tạo Path tới file chưa có hoàn toàn hợp lệ

Tạo file **`PathExistenceDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class PathExistenceDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("path-existence-");
        Path target = root.resolve("notes.txt");

        try {
            System.out.println("pathCreated=" + (target != null));
            System.out.println("fileName=" + target.getFileName());
            System.out.println("existsBefore=" + Files.exists(target));
            System.out.println("notExistsBefore=" + Files.notExists(target));

            try {
                Files.readString(target, StandardCharsets.UTF_8);
                System.out.println("unexpected read success");
            } catch (NoSuchFileException exception) {
                System.out.println("readBefore=NoSuchFileException");
            }

            Files.writeString(
                    target,
                    "Java NIO",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );

            System.out.println("existsAfter=" + Files.exists(target));
            System.out.println(
                    "content=" + Files.readString(target, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(root);
        }
    }
}
~~~

Biên dịch và chạy Java 17+:

~~~bash
javac -encoding UTF-8 PathExistenceDemo.java
java PathExistenceDemo
~~~

**Stdout trong môi trường file system thông thường, khi không có tiến trình ngoài can thiệp vào thư mục tạm:**

~~~text
pathCreated=true
fileName=notes.txt
existsBefore=false
notExistsBefore=true
readBefore=NoSuchFileException
existsAfter=true
content=Java NIO
~~~

### Diễn giải từng bước

1. `Files.createTempDirectory` thực sự tạo **thư mục tạm**; tên riêng của nó do Java chọn nên không đưa vào stdout.
2. `root.resolve("notes.txt")` chỉ tạo `Path` cho file con. File đó **chưa được tạo**.
3. `target != null` chỉ xác nhận biến Java chứa một đối tượng tham chiếu; nó không nói bất cứ điều gì về file system.
4. `Files.exists(target)` lúc đầu là `false`, `Files.notExists(target)` là `true` vì đường dẫn được tạo trong thư mục tạm mới và chưa có file.
5. `Files.readString` cần mở và đọc file thực tế. Vì file chưa có nên default file system thường báo `NoSuchFileException`.
6. `Files.writeString` với `CREATE_NEW` tạo file mới; lựa chọn này tránh âm thầm ghi đè file đã tồn tại. Nếu có xung đột, thao tác sẽ báo lỗi thay vì ghi lên file cũ.
7. Lúc này `Files.exists` trả `true` và `Files.readString` đọc được văn bản vừa ghi.
8. `finally` xóa tài nguyên tạm sau ví dụ. Nếu cleanup gặp lỗi, `IOException` vẫn có thể phát sinh — đây là hành vi cần thiết khi không thể dọn tài nguyên.

**Bài học:** `target` là cùng một biến `Path` ở cả thời điểm **file chưa có** lẫn thời điểm **file đã được tạo**. Không có sự kiện “Path trở nên hợp lệ hơn” khi file xuất hiện; chỉ trạng thái file system đã thay đổi.

## 4. Các phép biến đổi `Path` không kiểm tra đích

| API | Nó chủ yếu làm gì | Có bảo đảm đích tồn tại? |
| --- | --- | --- |
| `Path.of("a", "b")` | Phân tích các phần tên đường dẫn theo provider | Không |
| `path.resolve("child")` | Kết hợp đường dẫn con hoặc xử lý path cho trước | Không |
| `path.resolveSibling("other")` | Tạo path thay thế cùng cấp với path gốc | Không |
| `path.getFileName()` | Lấy thành phần tên cuối (có thể `null` với root) | Không |
| `path.getParent()` | Lấy parent theo cấu trúc đường dẫn (có thể `null`) | Không |
| `path.normalize()` | Loại bỏ thành phần tên dư thừa theo quy tắc từ vựng | Không |
| `path.toAbsolutePath()` | Chuyển sang dạng đường dẫn tuyệt đối theo provider | Không |
| `path.toUri()` | Biểu diễn URI cho đường dẫn theo provider | Không nên coi là phép kiểm tra tồn tại |
| `path.toFile()` | Chuyển sang `java.io.File` khi provider hỗ trợ | Không |
| `Files.exists(path)` | Thử kiểm tra tồn tại tại thời điểm gọi | Không bảo đảm **tương lai** |
| `path.toRealPath()` | Truy cập file system để lấy đường dẫn thực, xử lý link theo options | Thường yêu cầu mục tồn tại **lúc gọi**, không bảo đảm sau đó |

### Ví dụ chạy được: `resolve` và `normalize` vẫn không “chạm” file

Tạo **`PathLexicalDemo.java`**:

~~~java
import java.nio.file.Path;

public class PathLexicalDemo {
    public static void main(String[] args) {
        Path relative = Path.of("ghost")
                .resolve("draft")
                .resolve("..")
                .resolve("report.txt");

        Path normalized = relative.normalize();

        System.out.println("relative=" + !relative.isAbsolute());
        System.out.println("lastName=" + normalized.getFileName());
        System.out.println("changedByNormalize="
                + !relative.equals(normalized));
        System.out.println("absolute=" + normalized.toAbsolutePath().isAbsolute());
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac PathLexicalDemo.java
java PathLexicalDemo
~~~

Stdout:

~~~text
relative=true
lastName=report.txt
changedByNormalize=true
absolute=true
~~~

`ghost/draft/../report.txt` được rút gọn về `ghost/report.txt` theo quy tắc từ vựng. Thư mục `ghost` và file `report.txt` không cần tồn tại để chương trình chạy.

**Cảnh báo:** `normalize()` không phải là `toRealPath()`. Nó không giải quyết symbolic link, không xác minh file và không nhất thiết bảo toàn ý nghĩa đường dẫn khi một thành phần được loại bỏ là symbolic link. Ví dụ `link/../file` có thể có ý nghĩa khác sau khi bỏ `link/..` nếu `link` trỏ tới vị trí khác. Không dùng `normalize()` như cách xác nhận một file thực sự nằm trong một thư mục tin cậy.

## 5. `Files.exists()` và `Files.notExists()`: khác nhau với phủ định logic

Một nhầm lẫn nguy hiểm là:

~~~java
if (!Files.exists(path)) {
    // Tưởng chắc chắn file KHÔNG tồn tại.
}
~~~

Theo Java NIO, `Files.exists(path)` có thể trả `false` cả khi file thực sự không tồn tại **và** khi không thể xác định trạng thái. `Files.notExists(path)` cũng có thể trả `false` nếu không thể xác định.

| `Files.exists(path)` | `Files.notExists(path)` | Có thể hiểu trong một trạng thái ổn định |
| --- | --- | --- |
| `true` | `false` | Đã xác nhận mục tồn tại tại thời điểm kiểm tra |
| `false` | `true` | Đã xác nhận mục không tồn tại tại thời điểm kiểm tra |
| `false` | `false` | Không xác định được liệu mục có tồn tại hay không |

Không viết `Files.notExists(path) == !Files.exists(path)` như một bất biến. Thêm nữa, hai lời gọi ở hai thời điểm khác nhau có thể nhìn thấy hai trạng thái khác nhau nếu có thay đổi đồng thời. Các API kiểm tra tồn tại **không giữ khóa hay bảo lưu quyền** để bảo đảm thao tác tiếp theo.

### `isRegularFile` và `isDirectory` cũng không bảo đảm kết quả âm là nguyên nhân cụ thể

~~~java
boolean regular = Files.isRegularFile(path);
boolean directory = Files.isDirectory(path);
~~~

Giá trị `false` của `isRegularFile` có thể chỉ vì đích là thư mục, vì không tồn tại hoặc vì không xác định được thuộc tính. `isDirectory == false` cũng không chứng minh đích là file thường.

Nếu nghiệp vụ phải biết thông tin chi tiết hoặc cần nguyên nhân truy cập thất bại, có thể dùng:

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public final class FileTypeProbe {
    private FileTypeProbe() {}

    public static BasicFileAttributes inspect(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class);
    }
}
~~~

Đây là lớp tiện ích có thể biên dịch nhưng không có `main`. Phương thức `readAttributes` có thể ném `NoSuchFileException`, `AccessDeniedException` hoặc `IOException` khác, tùy file system provider. Tuy nhiên, kết quả thuộc tính vẫn chỉ là **ảnh chụp tại một thời điểm**, không bảo đảm file không bị thay thế sau đó.

## 6. `toRealPath()` khác `toAbsolutePath()` như thế nào?

Hai phương thức trông giống nhau nhưng có ngữ nghĩa khác:

**`path.toAbsolutePath()`**

- Chuyển `Path` tương đối thành dạng tuyệt đối theo file system/provider.
- **Không dùng kết quả như chứng nhận file hiện tồn tại**.
- Đường dẫn có thể chứa thành phần `.`, `..`; `toAbsolutePath()` tự thân không đồng nghĩa với canonicalization đầy đủ.
- Tùy môi trường, cách lấy đường dẫn tuyệt đối có thể dựa vào thư mục làm việc/đặc tính provider; đừng suy ra nó đã đọc mọi metadata của đích.

**`path.toRealPath()`**

- Trả đường dẫn thực (*real path*) theo file system.
- Thông thường nó chuẩn hóa đường dẫn tuyệt đối, giải quyết symbolic link và **kiểm tra sự tồn tại/khả năng truy cập cần thiết** tại thời điểm gọi.
- Có thể ném `IOException`/`NoSuchFileException` khi mục không tồn tại hoặc gặp trở ngại.
- Có thể truyền `LinkOption.NOFOLLOW_LINKS` để ảnh hưởng cách xử lý symbolic link. Tùy chọn này không biến một path không tồn tại thành tồn tại.

### Demo chạy được: `toRealPath()` yêu cầu mục thực tế

Tạo **`RealPathDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public class RealPathDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("real-path-");
        Path missing = root.resolve("not-yet.txt");

        try {
            System.out.println("absoluteCanBeBuilt="
                    + missing.toAbsolutePath().isAbsolute());

            try {
                missing.toRealPath();
                System.out.println("unexpected success");
            } catch (NoSuchFileException exception) {
                System.out.println("realBefore=NoSuchFileException");
            }

            Files.createFile(missing);
            Path real = missing.toRealPath();

            System.out.println("realAfterExists=" + Files.exists(real));
        } finally {
            Files.deleteIfExists(missing);
            Files.deleteIfExists(root);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac RealPathDemo.java
java RealPathDemo
~~~

Stdout trong môi trường file system mặc định thông thường, không có thay đổi đồng thời:

~~~text
absoluteCanBeBuilt=true
realBefore=NoSuchFileException
realAfterExists=true
~~~

`toAbsolutePath()` tạo được đường dẫn tuyệt đối trước khi file xuất hiện. `toRealPath()` không tìm thấy mục ở lần gọi đầu; sau `Files.createFile`, lời gọi thành công.

**Không lạm dụng `toRealPath()`:** Nếu bạn đang tính đường dẫn cho file *sắp được tạo*, gọi `toRealPath()` trên **file đích** thường là sai bước vì đích chưa tồn tại. Có thể cần canonicalize **thư mục cha đã tồn tại** và có chính sách tạo file riêng, nhưng vẫn phải xử lý race/symlink. Hơn nữa, thành công của `toRealPath()` hôm nay không cung cấp một file handle giữ cố định mục đó cho các thao tác sau.

## 7. Kiểm tra rồi thao tác: vấn đề TOCTOU

Xem cách viết:

~~~java
if (Files.exists(target)) {
    String text = Files.readString(target);
}
~~~

Giữa `exists` và `readString`, một tiến trình/thread khác có thể xóa file, đổi quyền, chuyển đường dẫn hoặc thay thế symbolic link. Khi đó `readString` vẫn có thể ném `IOException`.

Tương tự, đoạn sau **không an toàn để bảo đảm tạo file mới**:

~~~java
if (!Files.exists(target)) {
    Files.writeString(target, "data");
}
~~~

`Files.writeString` mặc định có thể tạo file hoặc truncate file đã có. Trong thời gian giữa kiểm tra và ghi, một bên khác có thể tạo ra file tại đích; kết quả là file của họ bị ghi đè. Ngoài ra, `Files.exists()==false` không luôn khẳng định “không tồn tại”.

### Chọn thao tác có điều kiện ngay tại điểm I/O

Nếu mục tiêu là tạo **file mới và không ghi đè**, viết:

~~~java
Files.writeString(
        target,
        "data",
        java.nio.charset.StandardCharsets.UTF_8,
        java.nio.file.StandardOpenOption.CREATE_NEW,
        java.nio.file.StandardOpenOption.WRITE
);
~~~

`CREATE_NEW` yêu cầu kiểm tra “chưa có file” và tạo file **theo hợp đồng nguyên tử đối với các thao tác file system khác**. Nếu file tồn tại ở thời điểm thực thi, thao tác báo lỗi (thường là `FileAlreadyExistsException`). Đây là sự khác biệt then chốt so với chuỗi `exists → write`.

Nếu mục tiêu là đọc file bắt buộc tồn tại, **cứ mở và đọc**:

~~~java
String text = Files.readString(
        target, java.nio.charset.StandardCharsets.UTF_8);
~~~

Bắt/lan truyền `NoSuchFileException`, `AccessDeniedException`, `IOException` khác từ chính thao tác, thay vì dùng `Files.exists` làm “giấy phép bảo đảm”.

Nếu mục tiêu là thư mục **phải tồn tại sau thao tác**, dùng `Files.createDirectories(dir)` và xử lý `IOException` nếu không thể tạo hoặc đích không phải thư mục. Nếu mục tiêu là xóa file **nếu có**, `Files.deleteIfExists(path)` có thể trả `false` cho trường hợp thiếu nhưng vẫn có thể ném `IOException` cho các lỗi xóa khác.

**Quy tắc:** Việc kiểm tra trạng thái có ích cho giao diện người dùng hoặc chiến lược chẩn đoán, nhưng thao tác I/O thực sự luôn phải có đường xử lý lỗi.

## 8. `Path` không đồng nghĩa có quyền truy cập

Một file có thể tồn tại nhưng không đọc được vì:

- File-system permission, ACL, sandbox hoặc policy hệ điều hành.
- Mục đang được sử dụng/khóa tùy hệ thống và chế độ chia sẻ.
- Mục đã bị đổi/xóa/chuyển khi thao tác bắt đầu.
- File system không sẵn sàng, ổ mạng mất kết nối hoặc provider gặp lỗi.
- Đích là thư mục khi API yêu cầu file, hoặc ngược lại.
- Symbolic link là dangling hoặc dẫn đến mục không truy cập được.

`Files.isReadable(path)` / `Files.isWritable(path)` trả boolean mang ý nghĩa kiểm tra khả năng tại thời điểm đó; **không bảo đảm** lời gọi mở/ghi kế tiếp thành công. Một `Path` cũng có thể trỏ tới file hiện không tồn tại nhưng sẽ được tạo bởi một tác vụ khác trong tương lai.

Khi cần phân biệt lý do, ưu tiên **ngoại lệ từ thao tác**:

| API | Khi thành công | Một số trường hợp lỗi |
| --- | --- | --- |
| `Files.readString(path, UTF_8)` | Đọc văn bản từ file | `NoSuchFileException`, `AccessDeniedException`, `IOException` hoặc lỗi giải mã theo contract |
| `Files.newInputStream(path)` | Mở luồng đọc byte | `IOException` khi không mở được/đọc lỗi |
| `Files.createFile(path)` | Tạo file mới | `FileAlreadyExistsException`, `IOException` |
| `Files.createDirectories(path)` | Đảm bảo chuỗi thư mục được tạo/tồn tại | `IOException` nếu không thể tạo hoặc có mục không phải thư mục |
| `Files.delete(path)` | Xóa file/mục | `NoSuchFileException`, `DirectoryNotEmptyException` (tùy provider), `IOException` |
| `Files.readAttributes(path, BasicFileAttributes.class)` | Đọc thuộc tính tại thời điểm truy vấn | `IOException` khi không truy vấn được |
| `path.toRealPath()` | Lấy real path hiện tại | `IOException` khi không tìm/giải quyết được |

Các ngoại lệ cụ thể của một số API được ghi là *optional specific exceptions* trong Java API; provider có thể dùng một `IOException` chung. Không dựa vào thông báo lỗi tiếng Anh hoặc giả định mọi provider/OS đều báo đúng lớp con cụ thể.

## 9. Symbolic link: tồn tại của đường dẫn và tồn tại của đích liên kết là hai việc

Giả sử có một symbolic link `shortcut` trỏ tới `archive/missing.txt`, nhưng file đích đã mất. Khi đó:

- Bản thân mục **symbolic link** có thể vẫn tồn tại trên file system.
- `Files.exists(shortcut)` mặc định **theo symbolic link** (khi API/provider hỗ trợ), nên có thể trả `false` vì target không tồn tại.
- `Files.exists(shortcut, LinkOption.NOFOLLOW_LINKS)` kiểm tra chính link thay vì đi tới đích, nên có thể trả `true` khi link còn tồn tại.
- `Path.toRealPath()` mặc định giải quyết link và sẽ thất bại nếu không giải quyết được target; `NOFOLLOW_LINKS` thay đổi cách xử lý link theo hợp đồng.
- `Path.normalize()` hoàn toàn **không xác định link đó đang trỏ đi đâu**.

Không nên dạy `Files.exists()==false` như “không có bất cứ file-system entry nào ở vị trí này”. Nó có thể chỉ cho thấy đích *sau khi theo link* không tồn tại hoặc không được xác định.

Đừng phụ thuộc vào việc tạo symbolic link trong unit test ở mọi OS: Windows hoặc môi trường CI có thể yêu cầu quyền/chế độ riêng. Với logic quan trọng, nên có integration test theo từng provider.

## 10. Path traversal, thư mục gốc tin cậy và giới hạn của `normalize()`

Một API nhận tên file từ bên ngoài thường xây dựng:

~~~java
Path base = Path.of("uploads").toAbsolutePath().normalize();
Path requested = base.resolve(userName).normalize();
~~~

Đây là **mảnh mã** với biến `userName` kiểu `String` đã có. Nó tính đường dẫn để làm việc, không mở file. Ngay cả nếu thêm:

~~~java
if (!requested.startsWith(base)) {
    throw new IllegalArgumentException("Path escapes base");
}
~~~

thì kiểm tra này mới bảo vệ ở **mức từ vựng** đối với các thành phần như `..` (và cần quan tâm đường dẫn tuyệt đối của đầu vào). Nó không bảo đảm file thực nằm dưới `base` vì symlink, mount point, hard link và race condition có thể làm khác vị trí thực.

Đối với dữ liệu không tin cậy:

1. Xác định rõ nhóm tên file được phép (ví dụ chỉ một file name, không cho dấu phân cách nếu nghiệp vụ không cần đường dẫn con).
2. Không cho người dùng tự chỉ định đường dẫn tuyệt đối/`..` ngoài phạm vi hợp đồng.
3. Hiểu rằng `normalize` không truy vấn hoặc khóa file system; nó không xác thực symbolic links.
4. Áp dụng ranh giới quyền truy cập phù hợp, xử lý việc thư mục/entry bị đổi trong quá trình mở.
5. Với yêu cầu chống symlink traversal cao, tìm hiểu `SecureDirectoryStream` khi provider hỗ trợ và các chiến lược thao tác tương đối với một directory handle, thay vì chỉ so sánh chuỗi `Path`.

**Không dùng một lần `toRealPath()` rồi xem đó là giải pháp hoàn toàn loại bỏ TOCTOU**. Giữa việc kiểm tra real path và mở mục, tác nhân khác vẫn có thể thay đổi cây thư mục.

## 11. Tương đối, tuyệt đối và làm việc với nhiều file systems

### `Path` tương đối

~~~java
Path path = Path.of("notes.txt");
~~~

Nó có thể được hiểu dựa trên thư mục làm việc (hoặc quy tắc provider). Việc ứng dụng được chạy từ IDE, terminal hay scheduler có thể làm **cùng một chuỗi `notes.txt`** hướng tới các vị trí tuyệt đối khác nhau.

Nếu đường dẫn thuộc cấu hình quan trọng, hãy quyết định base directory rõ ràng:

~~~java
Path base = Path.of("/app/data"); // Minh họa path riêng môi trường Unix-like.
Path target = base.resolve("notes.txt");
~~~

Ở Windows hoặc một provider khác, cú pháp `/app/data` có ý nghĩa riêng. Đừng hard-code đường dẫn hệ điều hành vào thư viện đa nền tảng; tốt hơn truyền `Path base` từ cấu hình/constructor.

### `Path` từ provider khác

`Path` là interface do file system provider triển khai. Ngoài đĩa local có thể có ZIP file system hoặc những provider được cài thêm. Những đặc điểm cần nhớ:

- Không mọi provider đều hỗ trợ cùng loại thuộc tính, symlink, atomic move, permission hoặc chuyển sang `File`.
- `Files.exists`/`Files.readAttributes`/`toRealPath` có ngữ nghĩa chung nhưng cách báo một số lỗi/khả năng phụ thuộc provider.
- `Path.toFile()` có thể ném `UnsupportedOperationException` nếu provider không liên kết với default file system.
- So sánh `Path.equals` chủ yếu là so sánh representation/provider theo hợp đồng, **không phải kiểm tra hai tên có trỏ cùng inode/file thực không**.
- Muốn hỏi hai đường dẫn có chỉ đến cùng một file thực tại một thời điểm, dùng `Files.isSameFile(pathA, pathB)` khi phù hợp; lưu ý API này cũng có các quy tắc tối ưu và không được dùng thay thế kiểm tra sự tồn tại trong mọi tình huống.

## 12. Khác gì so với `java.io.File`?

Hai kiểu có điểm tương đồng quan trọng:

~~~java
File f = new File("missing.txt");
Path p = Path.of("missing.txt");
~~~

Cả `new File(...)` và `Path.of(...)` đều có thể tạo object trỏ đến đích **chưa tồn tại**. Chuyển đổi:

~~~java
Path fromFile = f.toPath();
File fromPath = p.toFile(); // Nếu provider hỗ trợ.
~~~

cũng **không tạo mục trên ổ đĩa** hoặc kiểm tra nó đã tồn tại.

Điểm khác biệt là NIO cho phép thao tác qua `Files` với các khả năng và lỗi chi tiết hơn:

| Nhu cầu | Phương pháp truyền thống | NIO thường phù hợp hơn |
| --- | --- | --- |
| Biểu diễn đường dẫn | `File` | `Path` |
| Kiểm tra tồn tại | `File.exists()` | `Files.exists(path)` (vẫn có giới hạn boolean) |
| Tạo file mới | `File.createNewFile()` | `Files.createFile(path)` |
| Tạo thư mục nhiều cấp | `File.mkdirs()` | `Files.createDirectories(path)` |
| Xóa file | `File.delete()` trả boolean | `Files.delete(path)` ném lỗi |
| Di chuyển | `File.renameTo(...)` trả boolean | `Files.move(...)` với options |
| Đọc thuộc tính | `File` có các thuộc tính đơn giản | `Files.readAttributes`/attribute views |

`Path` tự nó không mở/tạo/xóa file. **`Files` là tập tiện ích thao tác với Path**. Tuy vậy, ngay cả khi `Files.exists` trả true, ứng dụng vẫn phải xử lý lỗi ở thao tác tiếp theo.

## 13. Thiết kế API nhận `Path`: đừng ngầm đòi mục phải tồn tại

Hàm nhận `Path` có thể có các hợp đồng rất khác nhau:

~~~java
// Đọc nội dung: mục phải đọc được tại thời điểm thao tác.
static String load(Path path) throws IOException {
    return Files.readString(path, StandardCharsets.UTF_8);
}

// Tạo mới: mục KHÔNG được tồn tại tại thời điểm tạo.
static Path create(Path path) throws IOException {
    return Files.createFile(path);
}

// Chỉ tính tên đích dự kiến, chưa cần tồn tại.
static Path plannedOutput(Path folder, String fileName) {
    return folder.resolve(fileName);
}
~~~

Các đoạn trên là **mảnh mã** cần đặt trong lớp Java, import `Path`, `Files`, `IOException` và `StandardCharsets`. Đừng áp một tiền điều kiện “`Path` luôn phải tồn tại” cho cả ba: `create` **cần** đích chưa có, còn `plannedOutput` chỉ tính tên.

Nên mô tả rõ trong JavaDoc/API:

- Path được phép trỏ đến đích chưa tồn tại hay không?
- Có cho phép đường dẫn tương đối không, và base directory lấy từ đâu?
- Có theo symbolic link không?
- Nếu file đã tồn tại, ghi đè, thêm vào cuối, hay báo lỗi?
- Caller hay callee sở hữu các stream/channel sau khi mở?
- Trường hợp nào được báo `IOException` và có cần phân biệt `NoSuchFileException` hay không?

### Ví dụ tạo tệp từ Path được lập kế hoạch trước

Tạo file **`PlannedFileDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class PlannedFileDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("planned-file-");
        Path folder = root.resolve("reports");
        Path output = folder.resolve("result.txt");

        try {
            System.out.println("folderExistsBefore=" + Files.exists(folder));
            System.out.println("outputExistsBefore=" + Files.exists(output));

            Files.createDirectories(folder);
            Files.writeString(
                    output,
                    "done",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );

            System.out.println("folderExistsAfter=" + Files.isDirectory(folder));
            System.out.println("outputExistsAfter=" + Files.isRegularFile(output));
            System.out.println("text=" + Files.readString(
                    output, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(output);
            Files.deleteIfExists(folder);
            Files.deleteIfExists(root);
        }
    }
}
~~~

Chạy:

~~~bash
javac PlannedFileDemo.java
java PlannedFileDemo
~~~

Stdout trong môi trường thông thường không có thao tác cạnh tranh trên thư mục tạm:

~~~text
folderExistsBefore=false
outputExistsBefore=false
folderExistsAfter=true
outputExistsAfter=true
text=done
~~~

Bài này minh họa tình huống thực tế: code biết đường dẫn file và thư mục cần tạo **trước khi chúng tồn tại**; sau đó dùng hai thao tác cụ thể để tạo. Đừng thêm `toRealPath()` trên `output` trước khi tạo chỉ để “xác nhận path hợp lệ”.

## 14. Những hiểu nhầm phổ biến

**Hiểu nhầm 1 — Tạo `Path` cũng là tạo file.**  
Sai. `Path.of` là phép tạo đối tượng biểu diễn đường dẫn; muốn tạo file/thư mục hãy dùng `Files.createFile`, `createDirectories` hoặc mở stream với open options phù hợp.

**Hiểu nhầm 2 — `toAbsolutePath` chứng minh file có thật.**  
Sai. Một đường dẫn tuyệt đối vẫn có thể trỏ tới mục chưa tồn tại.

**Hiểu nhầm 3 — `normalize` giải quyết symlink và xác nhận vị trí thực.**  
Sai. `normalize` xử lý thành phần tên theo từ vựng; `toRealPath` mới có truy cập file system, và kết quả chỉ phản ánh một thời điểm.

**Hiểu nhầm 4 — `Files.exists(path)==false` luôn chắc chắn chưa có mục.**  
Sai. Có trường hợp không thể xác định. `Files.notExists` không đơn giản là phủ định của `Files.exists`.

**Hiểu nhầm 5 — `Files.isReadable(path)==true` đảm bảo `readString` thành công.**  
Sai. Trạng thái, quyền hoặc đối tượng ở đích có thể thay đổi; thao tác còn có thể lỗi vì lý do khác.

**Hiểu nhầm 6 — Kiểm tra `exists` rồi gọi `writeString` là tạo file an toàn.**  
Sai. Có race condition, và `writeString` mặc định có thể ghi đè đích; dùng `CREATE_NEW` nếu nghiệp vụ yêu cầu tạo mới không ghi đè.

**Hiểu nhầm 7 — Lỗi `NoSuchFileException` nghĩa là đối tượng `Path` không hợp lệ.**  
Sai. `Path` có thể hợp lệ về cú pháp nhưng file chưa tồn tại tại thời điểm thao tác. `InvalidPathException` mới liên quan tới một chuỗi đường dẫn không thể được phân tích bởi provider; đó là vấn đề khác.

**Hiểu nhầm 8 — Đường dẫn tuyệt đối đồng nghĩa đường dẫn cố định, không bao giờ đổi mục.**  
Sai. Mục tại cùng một tên file có thể bị xóa và thay thế. Một `Path` không giữ handle/lock tới một object bất biến.

**Hiểu nhầm 9 — Nếu kiểm tra `path.normalize().startsWith(base)` thì không thể có path traversal.**  
Sai. Nó có ích để kiểm tra từ vựng nhưng không đủ chống symbolic link và TOCTOU trong cây thư mục do người khác kiểm soát.

**Hiểu nhầm 10 — `Path.toFile()` luôn hoạt động trên mọi provider.**  
Sai. Provider không thuộc default file system có thể không hỗ trợ chuyển sang `java.io.File`.

## 15. Checklist khi review chương trình dùng Path

- [ ] Đang **tạo/mô tả đường dẫn** hay thực sự **truy cập hệ thống tập tin**?
- [ ] Có giả định `Path.of`/`resolve`/`toAbsolutePath` bảo đảm đích tồn tại không?
- [ ] Nếu cần đọc file, đã thực hiện thao tác đọc và xử lý `IOException` tại chính lời gọi đó?
- [ ] Nếu cần tạo file mới, đã dùng `CREATE_NEW` hoặc `Files.createFile` tránh ghi đè?
- [ ] Nếu cần thư mục đích, đã tạo/thẩm định đúng chính sách bằng `createDirectories` hoặc API tương ứng?
- [ ] Có phân biệt `Files.exists` false với `Files.notExists` true, và trường hợp unknown?
- [ ] Có tránh kiểm tra `exists` rồi coi thao tác sau chắc chắn không lỗi?
- [ ] Nếu dùng `toRealPath()`, đích có cần phải tồn tại và symbolic link được xử lý ra sao?
- [ ] Có kiểm tra input không tin cậy: đường dẫn tuyệt đối, `..`, symlink, thư mục được phép?
- [ ] Có nêu rõ default/other file-system provider, đường dẫn tương đối và base directory?
- [ ] Unit test có dùng thư mục tạm để không phụ thuộc file có sẵn ở máy người học?
- [ ] Có cleanup tài nguyên đúng cách và không xóa đường dẫn ngoài vùng test?

## 16. Tự kiểm tra kiến thức

**Câu 1.** `Path p = Path.of("missing.txt");` chạy không có exception. Có chắc file tồn tại không?  
**Đáp án:** Không. Nó chỉ tạo một `Path` biểu diễn tên `missing.txt`.

**Câu 2.** `p.toAbsolutePath()` trả một đường dẫn tuyệt đối. Điều này chứng minh được gì về sự tồn tại?  
**Đáp án:** Không chứng minh gì; nó chỉ chuyển biểu diễn sang đường dẫn tuyệt đối.

**Câu 3.** Khi nào `Files.exists` và `Files.notExists` đều có thể trả `false`?  
**Đáp án:** Khi không thể xác định trạng thái tồn tại theo quy tắc truy vấn file system.

**Câu 4.** Phương thức nào thường kiểm tra sự tồn tại và trả real path tại thời điểm gọi?  
**Đáp án:** `toRealPath()`, có thể ném `IOException` và xử lý symbolic link theo options.

**Câu 5.** Tại sao không nên viết `if (!Files.exists(p)) Files.writeString(p, text);` nếu không muốn ghi đè?  
**Đáp án:** Vì race condition và mặc định `writeString` có thể tạo/truncate file đã tồn tại; dùng `CREATE_NEW`.

**Câu 6.** Khi hàm được truyền `Path` cho output file chưa có, nên bắt nó gọi `toRealPath()` trước không?  
**Đáp án:** Không bắt buộc và thường không phù hợp. Cần xác định/cấp quyền với parent/đích theo policy, sau đó tạo file bằng thao tác cụ thể.

**Câu 7.** `p.normalize()` đã đủ ngăn truy cập file bên ngoài thư mục cho phép chưa?  
**Đáp án:** Chưa. Symbolic link, race condition và quyền truy cập vẫn cần được xử lý.

**Câu 8.** Khi `Files.readString(path)` ném `NoSuchFileException`, có phải `Path` đã “hỏng” không?  
**Đáp án:** Không. File được yêu cầu không tồn tại tại thời điểm thao tác (hoặc provider báo điều kiện thiếu mục tương ứng), còn biểu diễn `Path` có thể vẫn hoàn toàn hợp lệ.

**Ghi nhớ:** **`Path` là đường dẫn, không phải file đang tồn tại.** Việc tạo hay biến đổi đối tượng `Path` không tạo file và không giữ cho mục bên dưới tồn tại. Muốn biết trạng thái, phải truy vấn file system, nhưng ngay cả kiểm tra thành công cũng chỉ đúng tại một thời điểm. Khi cần kết quả nghiệp vụ chắc chắn, hãy dùng thao tác `Files` trực tiếp với options phù hợp và xử lý `IOException`.

## Tham khảo Java SE 17

- [Path — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html)
- [Files — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [StandardOpenOption — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardOpenOption.html)
- [LinkOption — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/LinkOption.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="path-011">PathMatcher với glob và regex</a>

<details>
<summary>Xem chi tiết</summary>

# `PathMatcher` với glob và regex

## Mục tiêu bài học

Trong Java NIO.2, `java.nio.file.Path` mô tả một đường dẫn, còn `java.nio.file.PathMatcher` trả lời câu hỏi **“đường dẫn này có khớp mẫu đã định nghĩa hay không?”**. `PathMatcher` hỗ trợ hai họ cú pháp quan trọng qua `FileSystem.getPathMatcher(...)`: **`glob:`** (dễ đọc khi chọn tên file) và **`regex:`** (mạnh hơn khi cần biểu thức chính quy).

Sau bài này, bạn có thể:

- Tạo `PathMatcher` bằng `FileSystems.getDefault().getPathMatcher("glob:...")` và `"regex:..."`.
- Hiểu `matches(Path)` so khớp **đối tượng `Path` được đưa vào**, không tự tìm kiếm file trên đĩa.
- Phân biệt `path`, `path.getFileName()`, `root.relativize(path)` và `path.toAbsolutePath()` khi lọc tên hay cả đường dẫn.
- Sử dụng các ký hiệu glob `*`, `**`, `?`, `[]`, `{}` và cách escape phù hợp; phân biệt glob với Java regular expression.
- Biết lý do một mẫu có thể cho kết quả khác trên các filesystem/OS do dấu phân cách, phân biệt hoa thường và quy tắc provider.
- Áp dụng matcher để lọc `Files.list` hoặc `Files.walk`, quản lý stream bằng try-with-resources, xử lý lỗi và bảo mật.
- Viết và chạy các ví dụ Java 17+ có đầu ra xác định.

## 1. `PathMatcher` là gì, và nó không làm gì?

`PathMatcher` là một **functional interface** trong `java.nio.file`:

```java
public interface PathMatcher {
    boolean matches(Path path);
}
```

Đây là **chữ ký minh họa**, không phải file Java độc lập để bạn khai báo lại trong dự án.

`matches(path)` quyết định mẫu có khớp `Path` được truyền hay không. Nó **không**:

- Mở hoặc tạo file, directory.
- Tự gọi `Files.list()` hoặc duyệt cây thư mục.
- Xác minh file thực sự tồn tại hoặc có quyền đọc.
- Kiểm tra MIME type hay định dạng nội dung.
- Bảo đảm tên/đường dẫn đã an toàn để mở, xóa hoặc thực thi.
- Tự chuyển sang khớp `getFileName()` nếu bạn truyền cả đường dẫn.

Sơ đồ logic:

```text
Chuỗi quy tắc                     Đường dẫn ứng viên
"glob:*.java"                     Path.of("src", "App.java")
       │                                   │
       ▼                                   ▼
FileSystem.getPathMatcher(...)     Path được caller chọn
       │                                   │
       └───────── matcher.matches(path) ───┘
                          │
                     true / false
```

Điều có tính quyết định là **`Path` nào được đưa vào matcher**. Với ví dụ trên, mẫu `glob:*.java` được thiết kế để khớp một thành phần tên, còn `Path.of("src", "App.java")` có nhiều thành phần; kết quả thông thường là `false`. Nếu muốn lọc theo tên cuối cùng, đưa `path.getFileName()` vào thay vì toàn bộ `path`.

## 2. Tạo matcher qua `FileSystem`

API cốt lõi:

```java
FileSystem fs = FileSystems.getDefault();

PathMatcher javaNames = fs.getPathMatcher("glob:*.java");
PathMatcher numberedReports =
    fs.getPathMatcher("regex:report-\\d{4}\\.csv");
```

Đây là **đoạn minh họa**, cần import `java.nio.file.FileSystem`, `FileSystems`, `PathMatcher`. Lưu ý: trong **Java source**, `\\d` và `\\.` dùng hai dấu backslash trong literal để tạo ra regex thực tế `\d` và `\.`. `PathMatcher` không trực tiếp là `java.util.regex.Pattern`; matcher được filesystem provider tạo ra theo cú pháp đã chỉ định.

Cú pháp chuỗi:

```text
syntax:pattern
```

- `syntax`: với default provider, có **`glob`** hoặc **`regex`**.
- `pattern`: quy tắc khớp được giải thích theo syntax đã chọn.
- `getPathMatcher(...)` thuộc **một `FileSystem`**, vì đường dẫn và ngữ nghĩa có thể phụ thuộc provider.
- Mẫu không hợp lệ có thể dẫn đến `PatternSyntaxException` (unchecked); syntax không được provider hỗ trợ cũng là lỗi cần xử lý theo hợp đồng.

`FileSystems.getDefault()` là filesystem mặc định của JVM. Với filesystem đặc biệt như ZipFS hoặc provider bên thứ ba, nên tạo matcher từ chính `path.getFileSystem()` hoặc filesystem đang xử lý thay vì mặc nhiên dùng default filesystem.

### Một matcher có thể tái sử dụng

```java
PathMatcher matcher =
    FileSystems.getDefault().getPathMatcher("glob:*.txt");

boolean ok1 = matcher.matches(Path.of("note.txt"));
boolean ok2 = matcher.matches(Path.of("photo.png"));
```

Đây là **đoạn minh họa** với import `java.nio.file.*`. `ok1` là `true`; `ok2` là `false` với tên ASCII trên default filesystem thông thường. Nên tạo matcher **một lần** cho một quy tắc và dùng lại trong vòng lặp thay vì biên dịch mẫu cho từng entry.

## 3. `glob:`: cú pháp thân thiện với tên file

Glob khác regex. Những quy tắc phổ biến của glob trong Java default filesystem:

| Glob | Ý nghĩa | Ví dụ |
|---|---|---|
| `*.java` | Phần tên bất kỳ, kết thúc bằng `.java`; `*` không vượt dấu phân cách thư mục | `Main.java` |
| `file?.txt` | Một ký tự không phải separator tại vị trí `?` | `file1.txt`, không phải `file12.txt` |
| `img[0-9].png` | Một ký tự trong dải ký tự nêu trong `[]` | `img7.png` |
| `report[abc].csv` | Một trong `a`, `b`, `c` | `reporta.csv` |
| `report[!0-9].csv` | Một ký tự không nằm trong dải `0..9` | `reportx.csv` |
| `*.{java,kt}` | Chọn một trong hai nhánh tên phần mở rộng | `Main.java`, `Main.kt` |
| `**` | Khớp zero hoặc nhiều ký tự, **có thể** đi qua separator | Có thể dùng trong mẫu nhiều cấp |
| `\\*` | Escape dấu `*` để coi nó là ký tự tên literal (quy tắc glob) | Tên chứa dấu `*` trên filesystem cho phép |

Cú pháp trên là mô tả glob; khi viết **Java string literal** cần escape thêm dấu `\\`, và khi dùng filesystem khác nhau thì ký tự phân cách, tên hợp lệ và case sensitivity có thể thay đổi. Không tự suy rằng tên có dấu `*` hợp lệ trên mọi hệ điều hành.

### `*` khác `**` như thế nào?

- `*` khớp **không hoặc nhiều** ký tự **trong một thành phần** đường dẫn, không nuốt directory separator.
- `**` khớp **không hoặc nhiều** ký tự **bao gồm cả dấu phân cách**, nên thích hợp để diễn đạt mẫu đường dẫn nhiều cấp.
- `?` khớp **đúng một** ký tự, không phải separator; nó không tương đương với `*`.
- `[]` là **character class** và `{}` là **nhóm lựa chọn** theo glob. Đây không phải `[]`/`{}` trong mọi trường hợp của regex.

Ví dụ ý tưởng:

```text
glob:*.java
  ├─ Main.java        ✓
  ├─ Test.java        ✓
  └─ src/Main.java    ✗  (vì có separator)

glob:**/*.java
  ├─ src/Main.java         ✓
  └─ src/test/Test.java    ✓
```

Lưu ý **rất dễ nhầm**: `glob:**/*.java` không nên được mặc định là khớp cả `Main.java` nằm ngay tại root trên mọi provider, vì mẫu vẫn có đoạn separator trước phần tên cuối. Nếu muốn thống nhất cả file ở root và ở dưới thư mục, một cách rõ ràng là duyệt cây bằng `Files.walk` rồi match `path.getFileName()` với `glob:*.java`. Như thế quy tắc phần mở rộng không phụ thuộc độ sâu của file.

### `{}` trong glob không phải regex quantifier

`glob:*.{java,kt}` chọn `.java` hoặc `.kt`.

Trong regex Java, `{4}` có thể nghĩa là **lặp đúng bốn lần**, nhưng ở glob, dấu ngoặc nhọn là nhóm lựa chọn như `{java,kt}`. Muốn đúng bốn ký tự ở glob, dùng `????` hoặc mẫu cụ thể hơn; muốn bốn chữ số, regex `\\d{4}` sẽ rõ ràng.

Glob có thể dùng `\\` để escape những ký tự có ý nghĩa đặc biệt như `*`, `?`, `[`, `{`. Trong Java source, bạn lại phải escape dấu `\\` của Java string. Khi logic xử lý dấu `\\` trên Windows, cần đặc biệt cẩn thận vì đây còn là đường phân cách hệ thống trong nhiều dạng path; hãy kiểm thử trực tiếp trên hệ điều hành được hỗ trợ.

## 4. `regex:`: sức mạnh của biểu thức chính quy

Với `regex:`, phần mẫu dùng **cú pháp của Java `Pattern`**. Ví dụ:

```java
PathMatcher report =
    FileSystems.getDefault().getPathMatcher(
        "regex:report-\\d{4}\\.csv"
    );
```

Đây là **đoạn minh họa**, với import `java.nio.file.*`. Mẫu chấp nhận `report-2026.csv`, không chấp nhận `report-ABCD.csv`.

Một số thành phần regex thường dùng:

| Regex | Nghĩa |
|---|---|
| `.` | Một ký tự bất kỳ, ngoại trừ một số line terminator theo chế độ mặc định |
| `.*` | Không hoặc nhiều ký tự bất kỳ theo quy tắc dấu `.` |
| `\\.` | Dấu chấm literal trong **biểu thức regex** |
| `\\d` | Một chữ số theo quy tắc Java regex |
| `[0-9]` | Một ký tự ASCII trong dải 0–9 |
| `[A-Za-z]` | Một ký tự thuộc dải chữ cái ASCII nêu ra |
| `(?:csv|tsv)` | Chọn một trong `csv`, `tsv` mà không tạo capturing group |
| `{4}` | Lặp đúng bốn lần phần tử trước |
| `+` | Một hoặc nhiều lần |
| `?` | Không hoặc một lần |
| `^`, `$` | Neo đầu/cuối chuỗi theo semantics Java regex |

**Khác biệt quan trọng:** `PathMatcher.matches(Path)` là **kiểm tra khớp toàn bộ biểu diễn Path**, không phải API tìm substring bất kỳ. Vì vậy không cần tự thêm `.*` vào đầu/cuối chỉ để biến matcher thành một phép tìm tên file bên trong path. Nếu muốn so khớp một phần cụ thể, đưa `path.getFileName()` hay một `Path` tương đối thích hợp vào matcher.

Khi viết biểu thức regex dưới dạng Java `String`, có **hai lớp cú pháp**:

1. Java string literal giải mã các escape của Java: `"\\d"` mới tạo chuỗi hai ký tự `` và `d`.
2. Regex engine nhận chuỗi đó và hiểu `\d` là nhóm chữ số.

Do đó:

| Ý định | Java source literal |
|---|---|
| Regex cho 4 chữ số | `"regex:\\d{4}"` |
| Regex dấu chấm thật | `"regex:\\."` |
| Regex đuôi `.java` | `"regex:.*\\.java"` |
| Regex file `report-2026.csv` | `"regex:report-\\d{4}\\.csv"` |

Đừng nhầm `glob:*.java` với `regex:*.java`: trong regex, dấu `*` không thể đứng đầu một biểu thức như glob wildcard, vì nó là quantifier của phần tử **đứng trước**. Muốn “bất kỳ ký tự rồi `.java`” theo regex, dùng `regex:.*\\.java` trong Java source.

### Glob hay regex?

- Dùng **glob** khi mô tả phần mở rộng, tiền tố, tên đơn giản hoặc một số pattern file dễ đọc.
- Dùng **regex** khi cần nhóm, số lượng lặp, vị trí, quy tắc tên phức tạp, ký tự hợp lệ hoặc định dạng token.
- Đừng chọn regex chỉ vì nó có vẻ mạnh hơn: khi lọc `*.txt` hoặc `*.java`, glob thường dễ đọc, dễ bảo trì.

## 5. Ví dụ 1: phân biệt khớp tên và khớp cả đường dẫn

Lưu **`PathMatcherBasicsDemo.java`**:

```java
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;

public class PathMatcherBasicsDemo {
    public static void main(String[] args) {
        PathMatcher glob = FileSystems.getDefault()
            .getPathMatcher("glob:*.java");
        PathMatcher regex = FileSystems.getDefault()
            .getPathMatcher("regex:.*\\.java");

        Path plain = Path.of("Demo.java");
        Path nested = Path.of("src", "Demo.java");
        Path text = Path.of("Demo.txt");

        System.out.println("glob plain: " + glob.matches(plain));
        System.out.println("glob nested full: " +
            glob.matches(nested));
        System.out.println("glob nested filename: " +
            glob.matches(nested.getFileName()));
        System.out.println("regex filename: " +
            regex.matches(plain.getFileName()));
        System.out.println("glob text: " + glob.matches(text));
    }
}
```

Biên dịch/chạy với Java 17+:

```bash
javac PathMatcherBasicsDemo.java
java PathMatcherBasicsDemo
```

Stdout với default filesystem thông thường:

```text
glob plain: true
glob nested full: false
glob nested filename: true
regex filename: true
glob text: false
```

Giải thích:

1. `glob:*.java` phù hợp với `Path.of("Demo.java")`.
2. `nested` gồm hai thành phần `src` và `Demo.java`, có separator. `*` không bao gồm separator, nên `glob.matches(nested)` là `false`.
3. `nested.getFileName()` chỉ gồm `Demo.java`; matcher lúc này nhận đúng phần tên cần lọc.
4. `regex:.*\\.java` tương đương `.*\.java` ở tầng regex, khớp tên `Demo.java`.
5. Đuôi `.txt` không được chấp nhận.

Ví dụ dùng tên ASCII đơn giản và không phụ thuộc file vật lý tồn tại. Nó thể hiện rằng **PathMatcher không phải filesystem scanner**: cả ba `Path` chỉ là object chứa đường dẫn.

## 6. Ví dụ 2: so sánh glob `?` với regex `\\d{4}`

Dù cả hai có thể kiểm tra “bốn ký tự”, chúng không hoàn toàn giống nhau.

Lưu **`GlobRegexComparisonDemo.java`**:

```java
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;

public class GlobRegexComparisonDemo {
    public static void main(String[] args) {
        PathMatcher glob = FileSystems.getDefault()
            .getPathMatcher("glob:report-????.csv");
        PathMatcher regex = FileSystems.getDefault()
            .getPathMatcher("regex:report-\\d{4}\\.csv");

        String[] names = {
            "report-2026.csv",
            "report-ABCD.csv",
            "report-26.csv",
            "other-2026.csv"
        };

        for (String name : names) {
            Path path = Path.of(name);
            System.out.println(name
                + " | glob=" + glob.matches(path)
                + " | regex=" + regex.matches(path));
        }
    }
}
```

Chạy:

```bash
javac GlobRegexComparisonDemo.java
java GlobRegexComparisonDemo
```

Stdout:

```text
report-2026.csv | glob=true | regex=true
report-ABCD.csv | glob=true | regex=false
report-26.csv | glob=false | regex=false
other-2026.csv | glob=false | regex=false
```

Tại sao `report-ABCD.csv` được glob chấp nhận? Vì `????` chỉ yêu cầu **đúng bốn ký tự không phải separator**, không nhất thiết là số. Regex `\\d{4}` (chuỗi regex sau khi Java decode là `\d{4}`) yêu cầu **bốn chữ số**, nên từ chối bốn chữ cái.

Khi yêu cầu khớp chính xác một grammar đặt tên, nên chọn regex hoặc bổ sung kiểm tra logic thay vì giả định glob wildcard đã kiểm tra định dạng nội dung.

## 7. Khớp phần nào của `Path`?

Cần quyết định rõ ràng **phạm vi pattern** trước khi tạo matcher. Có bốn trường hợp thường gặp:

| Giá trị đưa vào `matches` | Diễn giải | Ví dụ sử dụng |
|---|---|---|
| `path` | Toàn bộ `Path` hiện có, có thể tương đối hoặc tuyệt đối | Khi pattern mô tả cấu trúc đầy đủ của `path` |
| `path.getFileName()` | Thành phần cuối (tên file/dir) | Lọc tất cả `*.java` bất kể độ sâu |
| `root.relativize(path)` | Đường dẫn tương đối **so với root xác định** | Lọc đường dẫn theo vị trí trong cây |
| `path.toAbsolutePath()` | Đường dẫn tuyệt đối theo working directory hiện tại | Match rule thật sự được định nghĩa ở mức đường dẫn tuyệt đối |

Ví dụ:

```java
Path root = Path.of("project");
Path file = root.resolve(Path.of("src", "App.java"));

Path name = file.getFileName();   // App.java
Path relative = root.relativize(file); // src/App.java theo hệ
```

Đây là **đoạn minh họa**; chuỗi hiển thị của `relative` có thể là `src\\App.java` trên Windows. `Path.getFileName()` có thể trả `null` cho một số loại path như root-only; kiểm tra null trước khi gọi matcher trong mã xử lý đường dẫn tùy ý.

**Quan trọng với `relativize`:** Hai `Path` nên cùng filesystem/provider và phải tương thích về tính tương đối/tuyệt đối theo hợp đồng provider; nếu không, có thể ném `IllegalArgumentException`. Hãy chuẩn hóa cách biểu diễn `root` và `path` trước khi `relativize` nếu nguồn đến từ nhiều chỗ.

Không dùng `root.relativize(path)` như cơ chế chống path traversal: tạo đường dẫn tương đối chỉ biến đổi cách biểu diễn; nó không chứng minh `path` an toàn hay vẫn nằm trong root sau khi resolve symbolic link.

### “Khớp tên” và “khớp cấu trúc thư mục” là hai nhiệm vụ khác nhau

- Yêu cầu: “Mọi file `.java` trong cây” → `matcher.matches(path.getFileName())` trong quá trình duyệt cây.
- Yêu cầu: “Chỉ `.java` nằm trong thư mục `src`” → quy tắc trên **relative path** và cấu trúc thư mục; hoặc kết hợp kiểm tra cha với matcher tên.
- Yêu cầu: “Lọc đường dẫn tuyệt đối dưới một root” → xử lý theo `Path`, kiểm tra phạm vi và symlink an toàn riêng; match pattern chỉ là một bước chọn tên.

Khi thiết kế filter, ghi rõ **độ sâu**, **tập root**, **có theo symlink không** và **có phân biệt hoa thường không** để tránh hành vi bất ngờ khi chuyển từ local development sang CI hoặc production.

## 8. Ví dụ 3: `Files.list` và `Files.walk` + `PathMatcher`

`PathMatcher` không tự duyệt thư mục. Muốn lọc file thực tế, cần API liệt kê như:

- `Files.list(dir)`: stream các entry **trực tiếp** trong directory.
- `Files.walk(root)`: stream các đường dẫn theo quá trình duyệt cây **đệ quy**, có thể giới hạn độ sâu.
- `Files.walkFileTree(root, ...)`: visitor để quản lý từng trạng thái và lỗi traversal chi tiết hơn.

Cả `Files.list` và `Files.walk` trả về `Stream<Path>` **cần đóng** bằng try-with-resources để giải phóng tài nguyên hệ thống. Trong quá trình tiêu thụ stream, lỗi I/O có thể phát sinh dưới dạng `UncheckedIOException`. Bộ lọc `matcher.matches(path)` không biến stream thành snapshot bất biến.

Lưu **`PathMatcherDirectoryDemo.java`**:

```java
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.stream.Stream;

public class PathMatcherDirectoryDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("pathmatch-");
        Path sub = root.resolve("sub");

        try {
            Files.createDirectory(sub);
            Files.createFile(root.resolve("a.java"));
            Files.createFile(root.resolve("b.java"));
            Files.createFile(root.resolve("readme.md"));
            Files.createFile(sub.resolve("nested.java"));

            PathMatcher matcher = FileSystems.getDefault()
                .getPathMatcher("glob:*.java");

            List<String> direct;
            try (Stream<Path> entries = Files.list(root)) {
                direct = entries
                    .filter(Files::isRegularFile)
                    .filter(p -> matcher.matches(p.getFileName()))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
            }

            List<String> recursive;
            try (Stream<Path> entries = Files.walk(root)) {
                recursive = entries
                    .filter(Files::isRegularFile)
                    .filter(p -> matcher.matches(p.getFileName()))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
            }

            System.out.println("direct: " + direct);
            System.out.println("recursive: " + recursive);
        } finally {
            Files.deleteIfExists(sub.resolve("nested.java"));
            Files.deleteIfExists(root.resolve("a.java"));
            Files.deleteIfExists(root.resolve("b.java"));
            Files.deleteIfExists(root.resolve("readme.md"));
            Files.deleteIfExists(sub);
            Files.deleteIfExists(root);
        }
    }
}
```

Chạy Java 17+:

```bash
javac PathMatcherDirectoryDemo.java
java PathMatcherDirectoryDemo
```

Stdout khi fixture được tạo và liệt kê thành công:

```text
direct: [a.java, b.java]
recursive: [a.java, b.java, nested.java]
```

Phân tích:

1. Chương trình tự tạo một thư mục tạm có hai file `.java` trực tiếp và một file `.java` trong `sub`.
2. `Files.list(root)` chỉ thấy các entry cấp đầu; sau khi lọc theo file thông thường và tên `.java`, nhận `a.java` và `b.java`.
3. `Files.walk(root)` duyệt sâu nên còn có `nested.java`. Việc match **`getFileName()`** giúp cùng một glob `*.java` chấp nhận tên ở mọi độ sâu.
4. `sorted()` làm output có thứ tự xác định; bản thân các API traversal **không cam kết** thứ tự ổn định trên mọi hệ thống.
5. Cả hai Stream đều đóng trong TWR; `finally` dọn fixture.
6. `Files.isRegularFile` dùng chính sách theo symlink của API; nếu yêu cầu không theo symlink, cần lựa chọn `LinkOption.NOFOLLOW_LINKS` và traversal options rõ ràng, không mặc định mọi link được phép.

**Giới hạn:** `Files.walk` duyệt filesystem thật; trong ví dụ chỉ có bốn entry do chương trình tạo. Với cây rất lớn, hạn chế depth, chi phí I/O và chiến lược xử lý lỗi. Mã dọn file tạm trong `finally` cũng có thể ném lỗi; trong sản phẩm nên dùng cơ chế cleanup có chính sách tránh che khuất lỗi chính.

## 9. Phân biệt `PathMatcher` và `java.util.regex.Pattern`

Một regex hoàn toàn có thể được dùng theo hai cách:

**Cách A — `PathMatcher` từ filesystem:**

```java
PathMatcher matcher = FileSystems.getDefault()
    .getPathMatcher("regex:report-\\d{4}\\.csv");

boolean ok = matcher.matches(Path.of("report-2026.csv"));
```

**Cách B — `Pattern` trên chuỗi:**

```java
Pattern pattern = Pattern.compile("report-\\d{4}\\.csv");

boolean ok = pattern.matcher("report-2026.csv").matches();
```

Hai đoạn đều **minh họa**, cần import `java.nio.file.*` hoặc `java.util.regex.Pattern` tương ứng. Trong hai ví dụ tên ASCII đơn giản, `ok == true`.

Khác biệt:

| Vấn đề | `PathMatcher` | `Pattern` |
|---|---|---|
| Đầu vào | `Path` | `CharSequence` (thường là `String`) |
| Lấy qua | `FileSystem.getPathMatcher` | `Pattern.compile` |
| Cú pháp | `glob:`, `regex:` | Java regex |
| Quan hệ filesystem | Matcher gắn với provider | Không gắn với provider |
| Match toàn bộ | `matcher.matches(path)` | `pattern.matcher(s).matches()` |
| Tìm một đoạn nhỏ | Chọn phần Path phù hợp | `Matcher.find()` |
| Ứng dụng | Chính sách khớp path/tên file | Kiểm tra/khai thác pattern text nói chung |

Đừng nhầm `PathMatcher.matches` với `Matcher.find()`. `Matcher.find()` có thể tìm một chuỗi con; `matches()` đòi khớp toàn bộ đầu vào. Trong nhiều chương trình, người dùng nhập một “keyword tìm kiếm” nhưng developer gọi `PathMatcher.matches` với mẫu không có wildcard; kết quả sẽ false dù tên có chứa keyword.

Ví dụ nếu muốn khớp tên bất kỳ chứa `invoice`, mẫu glob là `glob:*invoice*` (trên một tên), không phải `glob:invoice`.

## 10. Hệ điều hành, dấu phân cách và case sensitivity

Một `PathMatcher` do filesystem provider tạo ra có thể **không mang cùng kết quả** trên mọi hệ điều hành nếu pattern phụ thuộc vào:

- **Dấu phân cách thư mục:** hệ Unix thường dùng `/`; Windows thường biểu diễn path bằng `\\`. Java `Path` trừu tượng hóa nhiều thao tác, nhưng viết cứng separator vào glob/regex có thể gây lỗi porting.
- **Phân biệt hoa/thường:** `App.JAVA` có thể được filesystem/provider đối xử khác với `App.java`. Không mặc định cùng policy trên Linux, Windows, macOS và ZipFS.
- **Quy tắc tên hợp lệ:** tên chứa `:`, `*`, `?` hoặc ký tự điều khiển có thể bị cấm hoặc có ý nghĩa khác ở filesystem này so với filesystem khác.
- **Unicode/canonical equivalence:** hai chuỗi nhìn giống nhau có thể gồm các code point khác nhau; không mặc định filesystem/provider chuẩn hóa chuỗi tên theo cùng một cách.
- **Absolute vs relative:** đường dẫn tuyệt đối chứa root, ổ đĩa hoặc các thành phần khác; glob thiết kế cho tên đơn giản không tự khớp toàn bộ dạng đó.

### Một cách làm portable cho việc lọc đuôi

Nếu yêu cầu chỉ là **tên file**, hãy lấy `path.getFileName()` và dùng pattern không chứa separator:

```java
PathMatcher javaName = path.getFileSystem()
    .getPathMatcher("glob:*.java");

Path filename = path.getFileName();
boolean accepted =
    filename != null && javaName.matches(filename);
```

Đây là **đoạn minh họa**, cần `path` khác `null` và import `Path`/`PathMatcher`. Nó tránh hardcode separator trong mẫu, nhưng **không tự tạo cùng chính sách phân biệt hoa thường** trên mọi hệ thống; nếu ứng dụng yêu cầu case-insensitive thực sự giống nhau, phải thiết kế/test policy riêng.

### Regex với đường dẫn nhiều cấp

Nếu muốn regex kiểm tra tên trong directory tree:

1. Quy định root và biến ứng viên thành `root.relativize(path)`.
2. Quy định dạng separator dùng trong regex và cách nó được nhận diện bởi provider.
3. Tránh giả định `regex:src/.+\\.java` luôn portable đối với mọi `Path` trên mọi OS.
4. Nếu chỉ cần kiểm tra một thành phần, tách `relative.getName(index)` hoặc dùng `getFileName()`.
5. Viết integration test trên các nền tảng mục tiêu nếu pattern có cấu trúc nhiều cấp.

Đừng tự động đổi `path.toString().replace('\\', '/')` rồi truyền lại cho `PathMatcher` của một provider mà không xác định kiểu `Path` mới và ngữ nghĩa mong muốn. Nếu yêu cầu một **cú pháp đường dẫn độc lập hệ điều hành** (ví dụ URL/ZIP entry key dùng `/`), cân nhắc tạo một mô hình biểu diễn chuẩn của ứng dụng và dùng `Pattern` trên chuỗi đó, hoặc dùng provider phù hợp.

## 11. Validation, lỗi pattern và dữ liệu không tin cậy

### Mẫu sai có thể làm chương trình lỗi ngay lúc tạo matcher

Ví dụ một regex sai:

```java
String syntaxAndPattern = "regex:[";
PathMatcher bad = FileSystems.getDefault()
    .getPathMatcher(syntaxAndPattern);
```

Đây là **phản ví dụ**, trên provider regex chuẩn, pattern có character class chưa đóng và việc tạo matcher sẽ ném `PatternSyntaxException`. Trong sản phẩm, nếu pattern đến từ cấu hình/người dùng, hãy catch `PatternSyntaxException`, báo lỗi rõ ràng và không lưu pattern lỗi vào bộ lọc đang hoạt động.

Một số syntax không được hỗ trợ hoặc provider không hiểu là lỗi cấu hình. Không tự chuyển bất kỳ string thiếu prefix nào sang regex; luôn xác định rõ `glob:` hay `regex:`.

### Matcher không phải công cụ kiểm tra bảo mật đường dẫn

`matcher.matches(path)` chỉ trả true/false theo mẫu. Nếu đường dẫn được nhập từ người dùng:

- **Path traversal:** đường dẫn `../secret.txt` có thể lọt vào một pipeline nếu chỉ xét `getFileName()` (`secret.txt`) và bỏ qua nơi nó trỏ tới.
- **Symlink:** một đường dẫn nhìn như nằm trong root có thể liên kết sang filesystem khác. `normalize()` chỉ xử lý thành phần cú pháp như `.`/`..`, không tự giải quyết symlink theo tất cả trường hợp.
- **TOCTOU:** đường dẫn/metadata có thể thay đổi giữa lúc match, `Files.isRegularFile` và lúc mở thật.
- **Regex quá phức tạp do người dùng cung cấp:** có thể tiêu tốn CPU bất thường trên đường dẫn dài (regex backtracking); giới hạn độ dài và độ phức tạp pattern.
- **Resource exhaustion:** `Files.walk` trên cây rất lớn, symlink/permission và số entry không giới hạn có thể tiêu tốn tài nguyên.

Phòng tránh phải dựa trên **quy tắc root, quyền truy cập, phương thức mở, file descriptor, validation và chính sách symlink** phù hợp, không chỉ pattern. Nếu xử lý thư mục không tin cậy, có thể cần `SecureDirectoryStream` (nếu provider hỗ trợ) hoặc cơ chế giới hạn truy cập của OS.

### `matches` không cần file tồn tại

```java
PathMatcher p = FileSystems.getDefault()
    .getPathMatcher("glob:*.txt");
boolean matched = p.matches(Path.of("not-created.txt"));
```

Đây là **đoạn minh họa**: `matched` có thể là `true` dù file `not-created.txt` chưa tồn tại. Nếu yêu cầu “file phải tồn tại và là file thông thường”, bạn phải kiểm tra bằng `Files.isRegularFile` hoặc thao tác mở phù hợp trong workflow, đồng thời cân nhắc rủi ro race condition.

## 12. Hiệu năng và cách thiết kế bộ lọc

Matcher thường chỉ xử lý **cách biểu diễn đường dẫn**, không tự truy cập filesystem để đọc nội dung. Tuy nhiên việc liệt kê tree, kiểm tra metadata và đọc file thật có thể tốn nhiều I/O.

Thực hành nên làm:

1. **Tạo matcher một lần** bên ngoài vòng lặp, dùng lại cho nhiều `Path`.
2. **Chọn phạm vi trước**: chỉ tên, đường dẫn tương đối hay toàn bộ path.
3. **Giảm số lần truy vấn metadata**: nếu chỉ lọc theo tên thì không cần gọi `Files.isRegularFile` cho mọi entry trừ khi yêu cầu thực sự là file thông thường.
4. **Giới hạn traversal**: chọn `Files.list` cho một cấp hoặc `Files.walk(root, maxDepth)` khi có độ sâu giới hạn.
5. **Đóng stream**: `Files.list`/`Files.walk` là tài nguyên I/O; dùng try-with-resources.
6. **Xác định thứ tự**: `sorted()` nếu cần output deterministic, nhưng nhớ sort nhiều path cũng tốn bộ nhớ.
7. **Không đưa thao tác ghi/xóa vào predicate match**: filter nên là thao tác kiểm tra; nếu muốn hành động trên file, tách giai đoạn xử lý và bảo đảm quyền/khả năng khôi phục.
8. **Log lỗi có ngữ cảnh**: thiếu permission, directory bị xóa, broken symbolic link và pattern sai có cách xử lý khác nhau.
9. **Kiểm thử trên OS/provider đích**: đặc biệt khi pattern chứa separator hoặc chữ hoa/thường.
10. **Ưu tiên đường dẫn tương đối đã định nghĩa rõ root** khi rule gắn với cấu trúc dự án; tránh viết rule ngầm phụ thuộc working directory của tiến trình.

Nếu chỉ cần pattern đơn giản cố định như `*.java`, một `glob` trên `getFileName()` thường là điểm bắt đầu tốt. Nếu cần kiểm tra “tên dạng `report-YYYY.csv`”, regex rõ ràng hơn. Nếu cần liệt kê và xử lý hàng chục nghìn file, chọn đúng API traversal mới là yếu tố hiệu năng lớn hơn việc chọn glob hay regex.

## 13. Câu hỏi tự kiểm tra

**Câu 1.** `PathMatcher` được lấy như thế nào?

**Đáp án:** Từ `FileSystem.getPathMatcher("glob:...")` hoặc `getPathMatcher("regex:...")`, thường bắt đầu bằng `FileSystems.getDefault()`.

**Câu 2.** `glob:*.java` khớp `Path.of("src", "Main.java")` không? Nếu không, làm sao lọc tên?

**Đáp án:** Trên default provider thông thường là không, vì `*` không nuốt separator. Dùng `matcher.matches(path.getFileName())` để khớp `Main.java`.

**Câu 3.** `glob:report-????.csv` có bắt buộc bốn ký tự là số không?

**Đáp án:** Không, `?` là một ký tự không phải separator. Dùng regex `report-\\d{4}\\.csv` nếu muốn bốn chữ số.

**Câu 4.** Trong Java string literal, vì sao `"regex:.*\\.java"` có hai dấu backslash?

**Đáp án:** Một dấu `\\` là escape ở tầng Java string; regex thực tế cần `\.` để khớp dấu chấm literal thay vì wildcard `.`.

**Câu 5.** Gọi `matcher.matches(path)` có tự quét thư mục hoặc kiểm tra `Files.exists(path)` không?

**Đáp án:** Không. Nó chỉ so khớp `Path` được đưa vào. Muốn lọc file đang tồn tại phải dùng API duyệt/lấy thông tin filesystem riêng.

**Câu 6.** Khi dùng `Files.walk(root)` để tìm mọi file `.java` ở mọi độ sâu, có cách nào tránh glob nhiều cấp phụ thuộc separator?

**Đáp án:** Lọc `p -> matcher.matches(p.getFileName())` với `glob:*.java`, sau đó áp dụng yêu cầu về loại file, quyền truy cập và symlink riêng.

**Câu 7.** `Files.list(root)` và `Files.walk(root)` khác nhau thế nào?

**Đáp án:** `Files.list` liệt kê một cấp; `Files.walk` duyệt cây theo cấu hình độ sâu. Cả hai trả `Stream<Path>` phải được đóng.

**Câu 8.** `PathMatcher` có bảo đảm trên Windows và Linux giống nhau hoàn toàn với mẫu chứa dấu separator và chữ hoa không?

**Đáp án:** Không. Matcher gắn với filesystem/provider; cần xét separator, case-sensitivity và cách biểu diễn Path.

**Câu 9.** `glob:*.txt` khớp một `Path` thì có chứng minh file đó an toàn để đọc hoặc thuộc root được phép không?

**Đáp án:** Không. Đó là kiểm tra mẫu tên/đường dẫn, không phải validation chống traversal/symlink, kiểm tra quyền hoặc mở file an toàn.

**Câu 10.** Muốn tìm một substring trong chuỗi chứ không phải match toàn bộ path, nên dùng gì?

**Đáp án:** Có thể dùng `java.util.regex.Pattern` và `Matcher.find()` trên một chuỗi đã xác định, hoặc thiết kế glob có wildcard bao quanh tên theo đúng yêu cầu.

## Tổng kết

- `PathMatcher` được tạo từ `FileSystem.getPathMatcher("glob:...")` hoặc `"regex:..."`.
- `glob` ngắn gọn cho tên file (`*`, `?`, `[]`, `{}`, `**`), còn `regex` phù hợp các quy tắc có cấu trúc/định lượng.
- `matches(Path)` so khớp **đúng đối tượng Path caller truyền**; khi chỉ lọc tên, dùng `getFileName()`.
- Cả glob và regex có thể nhạy với filesystem/provider, separator và case-sensitivity; không giả định hành vi giống nhau trên mọi nền tảng.
- `PathMatcher` không tự duyệt directory, xác minh tồn tại hay giải quyết bảo mật. Kết hợp nó với `Files.list`/`Files.walk`, try-with-resources và các bước validation thích hợp.
- Với Java 17, giữ rõ **phạm vi khớp**, **thứ tự filter**, **phạm vi root** và **hợp đồng filesystem** để tránh các lỗi khó phát hiện.


</details>

- [Quay lại đầu trang](#back-to-top)
