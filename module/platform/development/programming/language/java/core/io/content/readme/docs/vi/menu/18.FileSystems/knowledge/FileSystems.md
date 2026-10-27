<a id="back-to-top"></a>

# Hệ thống tệp

## Menu
- [FileSystem, FileSystems và FileSystemProvider](#file-systems-001)
- [Default file system và các provider được cài đặt](#file-systems-002)
- [Quan hệ giữa Path với FileSystem tạo ra nó](#file-systems-003)
- [newFileSystem, getFileSystem và quyền đóng tài nguyên](#file-systems-004)
- [ZIP file system: thao tác archive bằng Path và Files](#file-systems-005)
- [Root directories, FileStore và các đặc điểm provider](#file-systems-006)
- [ProviderMismatchException, FileSystemNotFoundException và ClosedFileSystemException](#file-systems-007)
- [Giới hạn chuyển sang File và giả định về đường dẫn vật lý](#file-systems-008)
- [Khả năng mở rộng provider và ranh giới của Java API](#file-systems-009)

## <a id="file-systems-001">FileSystem, FileSystems và FileSystemProvider</a>

<details>
<summary>Xem chi tiết</summary>

# FileSystem, FileSystems và FileSystemProvider trong Java NIO.2

## 1. Vì sao Java có ba thành phần này?

Khi viết chương trình I/O, ta thường thao tác với `Path` và `Files`: mở tệp, tạo thư mục, sao chép hoặc đọc thuộc tính. Tuy nhiên, **không phải mọi đường dẫn đều thuộc hệ thống tệp mặc định của hệ điều hành**. Java NIO.2 (gói `java.nio.file` và `java.nio.file.spi`) cho phép truy cập nhiều hệ thống tệp khác nhau thông qua kiến trúc *provider*.

Ví dụ, cùng một ứng dụng Java có thể sử dụng:

- Hệ thống tệp mặc định của máy: ổ đĩa Windows, phân vùng Linux, macOS…
- Một hệ thống tệp biểu diễn nội dung tệp ZIP/JAR khi có ZIP filesystem provider.
- Một filesystem chỉ đọc cung cấp hình ảnh các module của Java Runtime (JRT).
- Một hệ thống tệp ảo do thư viện hoặc ứng dụng cài đặt thêm, nếu có provider tương ứng.

Ba tên rất dễ nhầm có vai trò khác nhau:

| Thành phần | Kiểu và gói | Trách nhiệm chính |
| --- | --- | --- |
| `FileSystems` | Lớp tiện ích `java.nio.file.FileSystems` | Cung cấp filesystem mặc định; tìm filesystem đã mở; khởi tạo/mount một filesystem qua provider thích hợp. |
| `FileSystem` | Lớp trừu tượng `java.nio.file.FileSystem` | Đại diện **một instance hệ thống tệp**; tạo `Path` thuộc nó, cung cấp root, separator, file store, metadata hỗ trợ và quản lý vòng đời. |
| `FileSystemProvider` | Lớp trừu tượng SPI `java.nio.file.spi.FileSystemProvider` | Bộ triển khai thao tác I/O và quản lý filesystem theo một URI scheme, như `file` hoặc `jar`. |

Mối quan hệ tổng quát:

~~~text
FileSystems (điểm truy cập / factory)
       |
       +-- getDefault(), getFileSystem(), newFileSystem()
                           |
                           v
                  FileSystem (instance)
                    |        |
                    |        +--> provider(): FileSystemProvider
                    |
                    +--> getPath(...) --> Path --> Files.readString/copy/delete/...
                                            |
                                            +--> provider thực hiện I/O tương ứng
~~~

`Files` là lớp API thao tác trên `Path`. Khi cần truy cập dữ liệu, Java sẽ làm việc với provider của hệ thống tệp sở hữu đường dẫn đó. Nhờ vậy, phần lớn mã dùng `Files` không cần biết đường dẫn nằm trên ổ đĩa hay trong một ZIP filesystem.

## 2. `FileSystems`: lấy, tìm và mở một filesystem

### 2.1. Hệ thống tệp mặc định: `getDefault()`

~~~java
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

FileSystem fs = FileSystems.getDefault();
Path path = fs.getPath("data", "report.txt");
System.out.println(path.getFileSystem() == fs);
~~~

Đây là **đoạn mã minh họa** có các import cần thiết; để chạy độc lập hãy đặt vào phương thức `main` của một lớp Java.

- `FileSystems.getDefault()` trả về đối tượng filesystem mặc định mà ứng dụng dùng để làm việc với hệ thống tệp của máy. Các lệnh thông thường như `Path.of("data.txt")` tạo đường dẫn thuộc filesystem mặc định.
- `fs.getPath("data", "report.txt")` tạo `Path` dựa trên cú pháp của `fs`; cách biểu diễn dấu phân cách phụ thuộc provider/nền tảng.
- `path.getFileSystem()` trả về filesystem sở hữu `Path` đó. Kết quả so sánh trong ví dụ là `true`.

Không chủ động đóng `FileSystems.getDefault()`; filesystem mặc định do Java runtime quản lý và không thể bị đóng như một filesystem tự tạo.

### 2.2. Mở một filesystem qua provider: `newFileSystem(...)`

~~~java
FileSystem fs = FileSystems.newFileSystem(uri, options);
~~~

Đối với overload nhận `URI` và `Map<String, ?>`, Java tìm provider phù hợp với **scheme** trong `uri`, rồi yêu cầu provider tạo filesystem. `options` là các tùy chọn phụ thuộc từng provider: Java không bảo đảm mọi provider hiểu chung một tập khóa.

Một số overload thường gặp:

| API | Ý nghĩa |
| --- | --- |
| `FileSystems.newFileSystem(URI, Map<String, ?>)` | Tạo filesystem với URI và các tùy chọn môi trường. |
| `FileSystems.newFileSystem(Path)` | Tìm provider hỗ trợ dùng đường dẫn tới một tệp làm nền cho filesystem; ví dụ ZIP/JAR trên JDK có ZIP provider, tùy runtime. |
| `FileSystems.newFileSystem(Path, Map<String, ?>)` | Tương tự overload `Path` nhưng có thêm tùy chọn môi trường. |
| `FileSystems.newFileSystem(URI, Map<String, ?>, ClassLoader)` | Cho phép sử dụng class loader để tìm provider tùy chọn, ngoài các provider cài sẵn; chi tiết phân giải phụ thuộc overload và cấu hình runtime. |

Cần phân biệt **khởi tạo instance mới** và **lấy instance đã tồn tại**. Gọi `newFileSystem` cho một filesystem/URI đang được provider quản lý có thể gây `FileSystemAlreadyExistsException`. Không mặc định rằng gọi lại sẽ trả về đối tượng cũ.

### 2.3. Lấy một filesystem đang mở: `getFileSystem(URI)`

~~~java
FileSystem mounted = FileSystems.getFileSystem(uri);
~~~

Phương thức này yêu cầu provider nhận diện một filesystem **đã tồn tại** theo URI. Nó không tự tạo filesystem mới. Nếu provider không tìm thấy instance tương ứng, có thể ném `FileSystemNotFoundException`.

Tùy provider, URI xác định filesystem có thể khác URI xác định **một tệp bên trong** nó. Với ZIP filesystem, URI kiểu `jar:file:/.../archive.zip` mô tả archive dùng để mở filesystem; để tới một entry cụ thể, cách trực tiếp và rõ ràng là lấy `Path` từ đối tượng `FileSystem` đã mở.

## 3. `FileSystem`: một không gian đường dẫn cụ thể

Sau khi có `FileSystem`, ta có thể hỏi filesystem về đặc tính và tạo đường dẫn bên trong nó.

| Phương thức | Công dụng | Lưu ý |
| --- | --- | --- |
| `getPath(first, more...)` | Tạo `Path` theo cú pháp filesystem hiện tại. | Không tự đọc nội dung hay xác thực tệp có tồn tại. |
| `provider()` | Truy cập provider triển khai filesystem. | Có thể xem scheme bằng `provider().getScheme()`. |
| `getSeparator()` | Ký tự phân cách tên trong đường dẫn theo provider. | Không giả định mọi hệ thống dùng cùng `/` hoặc `\`. |
| `getRootDirectories()` | Liệt kê các root được filesystem cung cấp. | Có thể là nhiều root hoặc một root; không ép theo dạng ổ đĩa Windows. |
| `getFileStores()` | Các kho lưu trữ mà provider cho phép liệt kê. | Có thể thất bại/không đầy đủ tùy môi trường và quyền. |
| `supportedFileAttributeViews()` | Các nhóm thuộc tính có thể được hỗ trợ (ví dụ `basic`). | Không mặc định có `posix`, `dos` hay `acl`. |
| `getPathMatcher("glob:...")` | Tạo bộ so khớp đường dẫn theo cú pháp provider hỗ trợ. | Phụ thuộc provider/cú pháp `glob` hoặc `regex`. |
| `isOpen()`, `isReadOnly()` | Kiểm tra trạng thái đóng/mở và khả năng ghi. | Các hệ thống tệp ảo có thể chỉ đọc. |
| `close()` | Giải phóng tài nguyên filesystem có thể đóng. | Không đóng filesystem mặc định; đóng filesystem riêng khi dùng xong. |

Một `Path` luôn **gắn với một `FileSystem` cụ thể**:

~~~java
Path local = FileSystems.getDefault().getPath("notes.txt");
FileSystem owner = local.getFileSystem();
~~~

Hai `Path` nhìn giống tên nhau vẫn có thể thuộc hai filesystem khác nhau. Một số phép toán đường dẫn như `relativize` hoặc `resolve` với `Path` từ provider khác có thể ném `ProviderMismatchException`. Tạo đường dẫn bằng **`fs.getPath(...)` của filesystem đích** là cách đáng tin cậy để tránh nhầm không gian tên.

Ví dụ, `zipFs.getPath("/notes.txt")` là entry bên trong ZIP; `Path.of("notes.txt")` là đường dẫn trên filesystem mặc định. Chúng không trỏ vào cùng một nơi dù phần tên đều là `notes.txt`.

## 4. `FileSystemProvider`: lớp triển khai thao tác và cơ chế mở rộng

`FileSystemProvider` nằm trong `java.nio.file.spi`. Một provider công bố một URI scheme thông qua `getScheme()` và triển khai các phương thức để làm việc với tài nguyên của scheme đó.

Provider chịu trách nhiệm cho các nghiệp vụ nền tảng như:

- Tạo, tìm, trả về các filesystem: `newFileSystem(...)`, `getFileSystem(URI)` và `getPath(URI)`.
- Mở I/O hoặc channel: `newByteChannel(...)`, `newInputStream(...)`, `newOutputStream(...)` (một số phương thức có triển khai mặc định).
- Thao tác mục: `createDirectory(...)`, `delete(...)`, `copy(...)`, `move(...)` và `checkAccess(...)`.
- Đọc/ghi thuộc tính và hỗ trợ các thao tác liên quan đến link, directory stream, permissions tùy khả năng của provider.

Ta có thể liệt kê các provider được cài đặt và phát hiện trong runtime:

~~~java
import java.nio.file.spi.FileSystemProvider;

for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
    System.out.println(provider.getScheme());
}
~~~

Đây là **mảnh mã dùng trong `main`**. Trên một JDK thông thường có thể thấy `file` và `jar`, có thể có `jrt` hoặc provider bổ sung; **không dựa vào thứ tự hoặc số lượng provider** trong chương trình. Với runtime được tối giản bằng `jlink`, module ZIP provider (`jdk.zipfs`) có thể không hiện diện.

Kiến trúc SPI cho phép thư viện cung cấp một provider mới. Khi tự xây dựng provider, ngoài việc kế thừa `FileSystemProvider` còn phải triển khai các đối tượng `FileSystem`, `Path` và các thao tác I/O phù hợp, xử lý vòng đời, lỗi và quyền truy cập. Provider trên classpath thường được khai báo trong tệp dịch vụ:

~~~text
META-INF/services/java.nio.file.spi.FileSystemProvider
~~~

Nội dung là tên đầy đủ của lớp provider có thể khởi tạo theo quy tắc service provider. Trong một **named module**, có thể dùng khai báo `provides java.nio.file.spi.FileSystemProvider with ...;` trong `module-info.java` thay vì cơ chế khai báo classpath. Đây là công việc phát triển một filesystem provider hoàn chỉnh, không phải yêu cầu khi chỉ muốn đọc/ghi tệp bằng NIO.2.

## 5. Ví dụ Java 17+: tạo, ghi, đóng và mở lại ZIP filesystem

**Mục tiêu:** dùng một `FileSystemProvider` có scheme `jar` để coi tệp ZIP như một filesystem. Chương trình sẽ tạo một ZIP ở thư mục tạm, tạo tệp bên trong ZIP, đọc nội dung, đóng filesystem rồi mở lại để chứng minh dữ liệu đã được lưu.

Lưu toàn bộ mã sau vào `FileSystemDemo.java`:

~~~java
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class FileSystemDemo {
    public static void main(String[] args) throws IOException {
        FileSystem defaultFs = FileSystems.getDefault();
        System.out.println("defaultScheme=" + defaultFs.provider().getScheme());

        Path local = defaultFs.getPath("example.txt");
        System.out.println("localUsesDefault=" + (local.getFileSystem() == defaultFs));

        Path tempDir = Files.createTempDirectory("nio-fs-demo-");
        Path archive = tempDir.resolve("example.zip");
        URI zipUri = URI.create("jar:" + archive.toUri());

        try {
            // "create" là tùy chọn của ZIP filesystem provider,
            // không phải tùy chọn dùng chung cho mọi provider.
            try (FileSystem zipFs = FileSystems.newFileSystem(
                    zipUri, Map.of("create", "true"))) {

                Path entry = zipFs.getPath("/message.txt");
                Files.writeString(entry, "Hello, ZipFS", StandardCharsets.UTF_8);

                System.out.println("zipScheme=" + zipFs.provider().getScheme());
                System.out.println("zipText=" +
                        Files.readString(entry, StandardCharsets.UTF_8));
            }

            // Mở lại cùng archive sau khi instance ZIP trước đã đóng.
            try (FileSystem reopened = FileSystems.newFileSystem(
                    zipUri, Map.of())) {

                Path entry = reopened.getPath("/message.txt");
                System.out.println("reopenedText=" +
                        Files.readString(entry, StandardCharsets.UTF_8));
            }
        } finally {
            // Chỉ xóa archive sau khi ZIP filesystem đã được đóng.
            Files.deleteIfExists(archive);
            Files.deleteIfExists(tempDir);
        }
    }
}
~~~

Lệnh biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac FileSystemDemo.java
java FileSystemDemo
~~~

Kết quả điển hình trên JDK đầy đủ với default provider `file` và `jdk.zipfs`:

~~~text
defaultScheme=file
localUsesDefault=true
zipScheme=jar
zipText=Hello, ZipFS
reopenedText=Hello, ZipFS
~~~

Giải thích theo trình tự:

1. `defaultFs` là filesystem của máy; `local` là một `Path` thuộc filesystem đó. Chương trình không tạo `example.txt` ở ổ đĩa, chỉ tạo đối tượng đường dẫn.
2. `tempDir` là một thư mục tạm được tạo thật; `archive` xác định tệp ZIP sẽ được tạo. `URI.create("jar:" + archive.toUri())` tạo URI để ZIP provider nhận diện archive.
3. `FileSystems.newFileSystem(zipUri, Map.of("create", "true"))` yêu cầu ZIP provider tạo/mở filesystem trên archive; provider ghi nhận dữ liệu trong ZIP. Khóa `create` chỉ có ý nghĩa với ZIP provider.
4. `zipFs.getPath("/message.txt")` là **đường dẫn entry trong ZIP**, không phải `Path` trực tiếp tới tệp trên đĩa. `Files.writeString` và `Files.readString` thao tác qua provider của `zipFs`.
5. Khối `try-with-resources` gọi `close()` khi rời khối. Đóng ZIP filesystem rất quan trọng để ghi hoàn tất cấu trúc archive và giải phóng tài nguyên.
6. Khối thứ hai tạo một instance ZIP filesystem mới trên archive đã lưu, đọc lại `message.txt` và in `reopenedText`. Sau khi đóng, mã `finally` mới dọn archive và thư mục tạm.

Giá trị `defaultScheme=file` và `zipScheme=jar` là đặc điểm của provider trong môi trường chạy ví dụ. Nếu thiếu `jdk.zipfs`, provider bị vô hiệu hoặc thiếu quyền truy cập hệ thống tệp, ví dụ có thể ném ngoại lệ. Đây là điều kiện môi trường cần kiểm tra, không phải stdout thay thế.

Một cách khác để truy cập một ZIP **đã tồn tại** là `FileSystems.newFileSystem(Path.of("archive.zip"))` (overload hỗ trợ trên Java 17), khi runtime có provider chấp nhận loại tệp đó. Cách này khác với việc tạo archive mới bằng URI và tùy chọn `create`.

## 6. URI, filesystem instance và đường dẫn: chọn đúng thao tác

Ba loại truy cập thường được đặt cạnh nhau:

~~~java
FileSystem defaultFs = FileSystems.getDefault();      // Lấy mặc định
FileSystem mounted = FileSystems.getFileSystem(uri);   // Lấy instance đã mở
FileSystem created = FileSystems.newFileSystem(uri, Map.of()); // Tạo instance mới
~~~

Đây là **mảnh minh họa**, giả sử biến `uri` đã được định nghĩa và một provider tương ứng đã có. Không chạy liên tiếp ba dòng này trên một URI bất kỳ và mong luôn thành công: `getFileSystem` cần instance đã tồn tại, còn `newFileSystem` cần các điều kiện tạo/mở hợp lệ.

Phân biệt `Path.of(URI)` với `FileSystem.getPath(...)`:

- `Path.of(URI)` yêu cầu Java tìm provider của URI và lấy `Path` tương ứng. Không phải URI nào cũng đủ thông tin để tạo `Path`, và provider có thể yêu cầu filesystem đã được khởi tạo.
- `fileSystem.getPath("/inside.txt")` chỉ tạo một đường dẫn **trong filesystem đó**; không cần dịch lại scheme từ URI mỗi lần.
- `Path.of("relative.txt")` dùng filesystem mặc định, kể cả khi trong chương trình đang có ZIP filesystem mở.

Với ZIP/JAR, tránh ghép URI hoặc đường dẫn thủ công dựa trên giả định về hệ điều hành. Mở filesystem bằng API thích hợp và tạo entry bằng chính `zipFs.getPath(...)`. Không dùng `Path.of("/message.txt")` để chỉ entry ZIP.

Một đường dẫn giữa hai provider có thể được chuyển thành dữ liệu để sao chép bằng `Files.copy(source, target)` khi thao tác/provider hỗ trợ, nhưng không có nghĩa hai `Path` nằm trong cùng không gian tên; các lựa chọn thuộc tính, symbolic link và atomicity có thể khác nhau giữa provider. Không giả định một phép `move` xuyên filesystem có tính nguyên tử.

## 7. Vòng đời và giới hạn hỗ trợ

**Filesystem mặc định:** được Java runtime sở hữu, tồn tại trong suốt vòng đời ứng dụng. Đừng gọi `close()` lên filesystem mặc định.

**Filesystem tự mở:** nếu nó có thể đóng (ví dụ ZIP filesystem), dùng `try-with-resources`. Sau khi đóng:

- `isOpen()` thường trả `false` đối với instance đó.
- Những thao tác I/O dựa trên `Path` thuộc instance đã đóng có thể ném `ClosedFileSystemException`.
- `Path` không tự chuyển sang filesystem vừa mở lại; sau khi mở lại hãy lấy `Path` mới từ instance mới.
- Việc đọc metadata hoặc thao tác trên filesystem đóng phụ thuộc hợp đồng phương thức, nên không tận dụng một đối tượng đã đóng để làm việc tiếp.

`isReadOnly()` giúp nhận biết filesystem chỉ đọc. Không phải provider nào cũng hỗ trợ đồng đều symbolic link, watch service, file attribute views, permissions hoặc thao tác đồng bộ. Khi chỉ đọc dữ liệu tĩnh, đừng giả định có khả năng tạo file; với các thao tác có ràng buộc, hãy kiểm tra các API hỗ trợ rồi xử lý ngoại lệ thực tế.

Với provider của JAR/JRT/ảo, `FileStore` và root có thể khác xa cấu trúc ổ đĩa truyền thống. `getRootDirectories()` là nguồn dữ liệu phù hợp hơn việc tự đoán root `C:\` hoặc `/`.

## 8. Những ngoại lệ đáng phân biệt

| Ngoại lệ | Tình huống thường gặp | Hướng xử lý |
| --- | --- | --- |
| `ProviderNotFoundException` | Không tìm thấy provider phù hợp khi cần tạo filesystem. | Xác minh scheme và module/thư viện provider, chẳng hạn `jdk.zipfs`. |
| `FileSystemAlreadyExistsException` | Cố tạo filesystem mà provider đã quản lý instance cho URI tương ứng. | Dùng instance đang mở khi phù hợp, hoặc đóng instance trước khi mở lại. |
| `FileSystemNotFoundException` | `getFileSystem(uri)` không tìm thấy filesystem đã được mở. | Xác minh vòng đời và thời điểm gọi `newFileSystem`. |
| `ClosedFileSystemException` | Dùng filesystem đã đóng. | Mở lại filesystem rồi lấy `Path` mới. |
| `ReadOnlyFileSystemException` | Thử ghi lên filesystem chỉ đọc. | Chọn đích ghi khác hoặc chỉ thực hiện thao tác đọc. |
| `ProviderMismatchException` | Kết hợp các đối tượng đường dẫn không tương thích provider ở phép toán yêu cầu cùng provider. | Tạo/diễn giải đường dẫn bằng đúng filesystem sở hữu. |
| `UnsupportedOperationException` | Provider không hỗ trợ phương thức hoặc tính năng được yêu cầu. | Kiểm tra khả năng của provider, chọn thao tác tương thích. |
| `IOException` và các lớp con | Lỗi tệp, quyền truy cập, định dạng archive hoặc vấn đề I/O khi tạo/đọc/ghi/đóng. | Xử lý theo nghiệp vụ; đừng coi lỗi I/O là filesystem không tồn tại. |

Một số exception ở bảng là unchecked; `IOException` là checked exception. Khi sử dụng `try-with-resources`, lỗi phát sinh từ `close()` cũng có thể là lỗi I/O cần xử lý. Không nuốt lỗi nếu kết quả ghi chưa chắc chắn đã được lưu.

## 9. Sai lầm phổ biến khi thiết kế mã NIO.2

**Nhầm `FileSystems` với `FileSystem`.** `FileSystems` chỉ là điểm truy cập với các phương thức tĩnh. Chính **instance** `FileSystem` mới quyết định `getPath`, root, provider và trạng thái đóng/mở.

**Tự tạo `Path.of("...")` rồi mong truy cập ZIP.** `Path.of(String...)` luôn dùng default filesystem. Muốn làm việc bên trong ZIP, dùng `zipFs.getPath(...)`.

**Gọi `newFileSystem` liên tục mà không đóng.** Nếu URI đó đã có instance, provider có thể từ chối; hơn nữa rò rỉ tài nguyên ZIP khiến thao tác quản lý archive bị ảnh hưởng. Dùng `try-with-resources` và xác định rõ đối tượng sở hữu vòng đời.

**Đoán scheme, provider hoặc khả năng hỗ trợ.** Đừng giả định mọi runtime đều có `jar` hoặc mọi provider đều có `posix`. Dùng `FileSystemProvider.installedProviders()` khi cần chẩn đoán; kiểm tra theo mục tiêu thực tế.

**Nhầm filesystem với thư mục.** Một `FileSystem` là toàn bộ không gian đường dẫn/đối tượng I/O do provider cung cấp; nó không đồng nghĩa một thư mục. Một ZIP archive có thể được trình bày thành filesystem riêng, còn một thư mục thường chỉ là một `Path` trong filesystem đã có.

**Cho rằng path bất kỳ tương thích với mọi provider.** `Path` mang thông tin hệ thống tệp sở hữu. Khi xây dựng API nhận `Path` từ bên ngoài, bảo toàn filesystem của nó thay vì âm thầm chuyển sang `Path.of(path.toString())` vì thao tác này có thể đổi ý nghĩa đường dẫn.

**Tin rằng API đồng nghĩa quyền truy cập.** Provider chịu trách nhiệm kiểm tra truy cập, định dạng và quyền hệ điều hành. Lỗi giữa kiểm tra và thao tác thực tế vẫn có thể xảy ra nếu hệ thống tệp thay đổi đồng thời. Khi xử lý đường dẫn do người dùng nhập, cần áp dụng quy tắc an toàn và phân quyền phù hợp.

## 10. Khi nào cần mỗi API?

| Nhu cầu | Chọn API |
| --- | --- |
| Đọc/ghi tệp thông thường trên máy | `Path.of(...)` và `Files.*`; ít khi cần tự gọi `FileSystems.getDefault()`. |
| Cần biết đường dẫn thuộc filesystem nào | `path.getFileSystem()`. |
| Cần tạo `Path` bên trong một ZIP filesystem đã mở | `zipFs.getPath(...)`. |
| Cần tạo/mở filesystem đặc biệt | `FileSystems.newFileSystem(...)` với URI/Path và tùy chọn provider thích hợp. |
| Cần dùng lại filesystem đã mở được định danh theo URI | `FileSystems.getFileSystem(uri)` nếu provider hỗ trợ và instance đang tồn tại. |
| Cần biết provider nào đang thao tác | `path.getFileSystem().provider()`. |
| Cần liệt kê provider được phát hiện | `FileSystemProvider.installedProviders()`. |
| Cần hỗ trợ giao thức/hệ thống tệp mới trong thư viện | Triển khai `FileSystemProvider` cùng `FileSystem`/`Path` phù hợp và đăng ký qua SPI. |

## 11. Tự kiểm tra

1. **`FileSystems.getDefault()` trả `FileSystem` hay `FileSystemProvider`?** Trả về một `FileSystem`; có thể lấy provider bằng `provider()`.
2. **Hai `Path` có cùng chuỗi tên thì chắc chắn chỉ cùng tệp?** Không. Chúng có thể thuộc các filesystem khác nhau hoặc được phân giải trong các ngữ cảnh khác nhau.
3. **Sau khi mở ZIP, `Path.of("/message.txt")` có chỉ entry bên trong ZIP?** Không. Nó thuộc default filesystem; phải dùng `zipFs.getPath("/message.txt")`.
4. **Vì sao cần đóng ZIP filesystem?** Để hoàn tất ghi/đồng bộ dữ liệu archive theo provider và giải phóng tài nguyên; nên dùng `try-with-resources`.
5. **`getFileSystem(uri)` và `newFileSystem(uri, env)` khác nhau ở đâu?** Phương thức đầu lấy filesystem đã có; phương thức sau yêu cầu tạo instance filesystem qua provider.
6. **Tại sao không hard-code danh sách provider?** Thành phần của runtime, module và thư viện có thể khác nhau giữa môi trường chạy.

**Ghi nhớ:** `FileSystems` là nơi **lấy hoặc khởi tạo** filesystem; `FileSystem` đại diện **một không gian đường dẫn cụ thể**; `FileSystemProvider` là **bộ triển khai các thao tác I/O** cho không gian đó. Khi đường dẫn không nằm trên filesystem mặc định, luôn lấy `Path` từ đúng `FileSystem` và quản lý vòng đời của filesystem được mở.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-systems-002">Default file system và các provider được cài đặt</a>

<details>
<summary>Xem chi tiết</summary>

# Default file system và các provider được cài đặt trong Java NIO.2

## 1. Vì sao Java có `FileSystem` và `FileSystemProvider`?

Khi làm việc với tệp bằng `java.nio.file`, ứng dụng thường bắt đầu từ hệ thống tệp sẵn có của máy: ổ đĩa Windows hoặc các thư mục trong hệ thống Unix/Linux/macOS. Java gọi đó là **default file system** (hệ thống tệp mặc định). Tuy nhiên, NIO.2 không giới hạn đường dẫn vào hệ thống tệp của hệ điều hành: một tệp ZIP có thể được mở thành hệ thống tệp riêng, hoặc một thư viện có thể cung cấp hệ thống tệp ảo.

Ba khái niệm liên kết với nhau:

- **`FileSystemProvider`** (`java.nio.file.spi.FileSystemProvider`): nhà cung cấp triển khai các thao tác với hệ thống tệp, đường dẫn, kênh tệp, thuộc tính, v.v. Mỗi provider có một scheme URI, ví dụ `file` hoặc `jar`.
- **`FileSystem`** (`java.nio.file.FileSystem`): một thể hiện hệ thống tệp được provider cung cấp. Một provider có thể quản lý nhiều thể hiện `FileSystem`.
- **`Path`** (`java.nio.file.Path`): một đường dẫn gắn với chính `FileSystem` đã tạo ra nó. Gọi `path.getFileSystem()` để kiểm tra nơi đường dẫn thuộc về.

~~~text
FileSystemProvider có scheme "file" (default provider)
          └── default FileSystem
                  ├── Path "data/input.txt"
                  └── Path "logs/app.log"

FileSystemProvider có scheme "jar" (ZIP FS provider, khi có)
          ├── FileSystem cho archive-a.zip
          │       └── Path "/docs/a.txt"
          └── FileSystem cho archive-b.zip
                  └── Path "/docs/b.txt"
~~~

Sơ đồ là minh họa quan hệ, không cam kết máy nào cũng có ZIP provider. **Provider được cài đặt không đồng nghĩa với một `FileSystem` cụ thể đã được mở**. Ví dụ provider `jar` có mặt nhưng ứng dụng chưa mở ZIP nào.

## 2. Default file system: cách lấy và các bảo đảm cơ bản

Các API trung tâm:

~~~java
FileSystem defaultFs = FileSystems.getDefault();
FileSystemProvider provider = defaultFs.provider();

Path file = Path.of("data", "input.txt");
Path another = Paths.get("data", "input.txt");
~~~

Đây là **các đoạn lệnh minh họa**, cần đặt trong lớp/phương thức Java và import `java.nio.file.*`, `java.nio.file.spi.FileSystemProvider` khi sử dụng.

`FileSystems.getDefault()` trả về hệ thống tệp mặc định của JVM. Trên JDK chạy thông thường, nó đại diện cho môi trường hệ thống tệp máy đang chạy. `Path.of(String, String...)` và `Paths.get(String, String...)` tạo đường dẫn trên hệ thống tệp mặc định; `Paths.get` là API cũ hơn và thường có thể thay bằng `Path.of` từ Java 11.

Các đặc điểm cần nhớ:

1. `FileSystems.getDefault()` trả về cùng hệ thống tệp mặc định cho tiến trình; dùng phép đồng nhất `==` khi kiểm tra hai biến có trỏ tới cùng đối tượng.
2. Provider của default filesystem sử dụng scheme **`file`**. Vì vậy `defaultFs.provider().getScheme()` trả `"file"`.
3. `Path.of("relative.txt")` chỉ tạo đối tượng đường dẫn tương đối. Nó **không** kiểm tra tệp tồn tại và **không** ghi tệp.
4. Hệ thống tệp mặc định không được quản lý theo vòng đời giống hệ thống tệp mở thêm. Không tự đóng `FileSystems.getDefault()`. Trong triển khai mặc định, `close()` không được hỗ trợ.
5. `defaultFs.getSeparator()`, `defaultFs.getRootDirectories()` và quy tắc tên có thể khác giữa các hệ điều hành hoặc provider. Đừng giả định `/`, ký tự ổ đĩa hay tính phân biệt chữ hoa/chữ thường giống nhau ở mọi máy.

**Lưu ý cấu hình nâng cao:** JDK hỗ trợ property khởi động `java.nio.file.spi.DefaultFileSystemProvider` để yêu cầu provider tùy chỉnh bọc (wrap) default provider. Đây là lựa chọn cấu hình trước khi khởi tạo default filesystem, không phải thao tác thay đổi tùy ý trong lúc ứng dụng đang dùng các `Path`. Vì vậy, đừng giả định class thực tế của default provider luôn cố định trên mọi runtime; hãy truy vấn qua API.

## 3. Các provider được cài đặt: `installedProviders()`

Danh sách provider nhìn thấy trong môi trường chạy hiện tại được lấy qua:

~~~java
List<FileSystemProvider> providers =
        FileSystemProvider.installedProviders();
~~~

`installedProviders()` trả một danh sách provider đã được cài đặt/khám phá trong runtime. **Phần tử đầu tiên là default provider**; các provider khác phụ thuộc JDK, module runtime và thư viện bổ sung. Danh sách này được nạp và lưu để sử dụng lại, nên không nên hiểu lời gọi như thao tác quét lại mọi JAR tùy ý trong mỗi lần chạy.

Một vài scheme thường gặp:

| Scheme | Ví dụ ý nghĩa | Điều cần lưu ý |
| --- | --- | --- |
| `file` | Hệ thống tệp mặc định của hệ điều hành | Có trong danh sách, đứng đầu |
| `jar` | ZIP/JAR filesystem (thường do module `jdk.zipfs` cung cấp) | Có thể thiếu trong runtime được cắt gọn |
| `jrt` | Hệ thống tệp ảnh module runtime của Java | Tùy khả năng của runtime/JDK, không phải ổ đĩa thông thường |
| Scheme tùy chỉnh | Provider do thư viện/ứng dụng triển khai | Chỉ dùng được khi provider hiện diện và được cơ chế nạp phù hợp tìm thấy |

Tên scheme có thể được in bằng `provider.getScheme()`. **Scheme là loại địa chỉ tài nguyên, không phải đường dẫn hay instance filesystem**. Ví dụ hai ZIP có thể dùng chung provider có scheme `jar` nhưng vẫn là hai `FileSystem` khác nhau.

Không giả định rằng mọi máy đều có đúng ba scheme `file`, `jar`, `jrt`; chương trình chỉ nên dựa trên những provider đã tìm thấy trong runtime đang chạy.

### Ví dụ Java 17+ chạy độc lập: khám phá provider

Lưu vào tệp `InstalledProvidersDemo.java`:

~~~java
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.List;

public class InstalledProvidersDemo {
    public static void main(String[] args) {
        FileSystem defaultFs = FileSystems.getDefault();
        List<FileSystemProvider> providers =
                FileSystemProvider.installedProviders();

        System.out.println("Default scheme: "
                + defaultFs.provider().getScheme());
        System.out.println("Path.of uses default: "
                + (Path.of("sample.txt").getFileSystem() == defaultFs));
        System.out.println("Default provider is first: "
                + (providers.get(0) == defaultFs.provider()));
        System.out.println("Provider count >= 1: "
                + (providers.size() >= 1));

        System.out.println("Installed schemes:");
        for (FileSystemProvider provider : providers) {
            System.out.println("  " + provider.getScheme());
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac InstalledProvidersDemo.java
java InstalledProvidersDemo
~~~

Phần đầu của stdout ổn định trên JDK tuân theo hợp đồng API:

~~~text
Default scheme: file
Path.of uses default: true
Default provider is first: true
Provider count >= 1: true
Installed schemes:
~~~

**Sau dòng `Installed schemes:`, số dòng và tên scheme phụ thuộc runtime.** Scheme `file` đứng đầu danh sách; một JDK đầy đủ thường có thêm `jar` hoặc `jrt`. Vì danh sách biến thiên, không có một stdout đầy đủ cố định đúng cho mọi môi trường.

Phân tích kết quả:

- `Path.of("sample.txt")` lấy provider từ default filesystem, nên phép so sánh đối tượng `FileSystem` in `true`; tệp `sample.txt` không cần tồn tại.
- `installedProviders().get(0)` là default provider theo hợp đồng API; điều này **không** chứng minh chỉ có một provider.
- Vòng lặp in các scheme đang có thật; không nên viết logic kiểm tra bằng cách so với một danh sách cố định.

## 4. Provider được nạp như thế nào?

NIO.2 dùng cơ chế **service provider** để cho phép triển khai bổ sung. Một provider tuân theo hợp đồng `java.nio.file.spi.FileSystemProvider`; môi trường Java có thể khám phá những triển khai đã được đóng gói/khai báo hợp lệ khi provider được cài đặt.

Ở mức ứng dụng, luồng ý tưởng là:

1. Provider mặc định phục vụ hệ thống tệp của tiến trình.
2. Runtime khám phá các provider được cài đặt bổ sung bằng cơ chế nạp dịch vụ của Java và các provider có sẵn trong JDK.
3. `FileSystemProvider.installedProviders()` cho phép liệt kê các provider đó.
4. Khi yêu cầu tạo/mở `FileSystem` theo URI hay theo tài nguyên, API chọn provider có khả năng xử lý.
5. Provider tạo/trả về `FileSystem`; từ hệ thống tệp này mới tạo ra các `Path`.

Điều kiện **có class provider trong một JAR** chưa đủ để khẳng định `installedProviders()` nhìn thấy nó: còn phụ thuộc khai báo dịch vụ, module, class loader và cách khởi động runtime. Với thư viện dùng Java Platform Module System, provider có thể được khai báo bằng `provides ... with ...`; với JAR theo cơ chế dịch vụ truyền thống, thông tin provider nằm trong `META-INF/services/java.nio.file.spi.FileSystemProvider`. Nếu module runtime được dựng bằng `jlink`, danh sách provider có thể khác JDK phát triển.

Ngoài các provider được cài đặt mà danh sách chung nhìn thấy, một số overload của `FileSystems.newFileSystem` hỗ trợ lựa chọn provider thông qua class loader truyền vào. Vì vậy, danh sách cài đặt chung không phải bằng chứng rằng **mọi** triển khai provider có thể nạp theo mọi class loader đều đã được liệt kê.

### Có thể gọi thẳng `newFileSystem` của provider không?

Có, đối tượng `FileSystemProvider` có các phương thức SPI như `newFileSystem(URI, Map<String, ?>)`, `getFileSystem(URI)` và `getPath(URI)`. Tuy nhiên, code ứng dụng thông thường nên ưu tiên lớp facade `FileSystems`/`Path`/`Files` vì chúng tập trung việc lựa chọn provider và thao tác với đường dẫn.

Không nên tự khởi tạo `FileSystemProvider` bằng reflection chỉ vì thấy class trên classpath: hành vi và cấu hình phụ thuộc triển khai, không phải mọi provider được thiết kế để tạo trực tiếp như object ứng dụng.

## 5. Khi nào Java chọn default provider, khi nào chọn provider khác?

| Biểu thức | Cách chọn hệ thống tệp/provider | Mục đích |
| --- | --- | --- |
| `FileSystems.getDefault()` | Default filesystem của JVM | Lấy hệ thống tệp mặc định |
| `Path.of("folder/file.txt")` | Default filesystem | Tạo đường dẫn từ chuỗi trong hệ thống tệp mặc định |
| `FileSystems.getDefault().getPath("x.txt")` | Chính default filesystem | Tạo đường dẫn thuộc `FileSystem` đã biết |
| `Path.of(uri)` | Provider hỗ trợ scheme của URI | Lấy đường dẫn qua URI; provider có thể yêu cầu `FileSystem` đã được mở |
| `FileSystems.getFileSystem(uri)` | Provider theo scheme URI | Truy xuất một filesystem đã tồn tại/được đăng ký; **không** dùng để tạo mới |
| `FileSystems.newFileSystem(uri, env)` | Provider thích hợp theo scheme URI | Tạo/mở filesystem mới theo hợp đồng provider |
| `existingFs.getPath("entry")` | Chính `existingFs` | Tạo đường dẫn trong hệ thống tệp đã có |

Một scheme được cài đặt **không bảo đảm** gọi `getFileSystem(uri)` thành công. Ví dụ với filesystem ZIP, trước tiên bạn có thể phải tạo/mở instance bằng `newFileSystem`. `getFileSystem` có thể ném `FileSystemNotFoundException` nếu chưa tồn tại filesystem tương ứng; `newFileSystem` có thể ném `ProviderNotFoundException` khi không có provider xử lý loại URI được yêu cầu.

`Path.of(uri)` cũng có thể thất bại nếu provider thích hợp chưa có filesystem sẵn sàng; đó là lý do khi đã có `FileSystem`, dùng `fs.getPath(...)` là cách trực tiếp và rõ ràng nhất.

**Không đổi `Path` sang chuỗi rồi gọi `Path.of(String)` nếu muốn giữ provider gốc.** Cách này luôn định tuyến tên mới vào default filesystem và có thể làm mất ý nghĩa khi nguồn thuộc ZIP/JAR hoặc filesystem ảo.

## 6. Thực hành với `jar` provider: cài đặt và mở một filesystem là hai bước khác nhau

Trên JDK đầy đủ có `jdk.zipfs`, provider có scheme `jar` giúp mở tệp ZIP/JAR như một filesystem. Đoạn chương trình dưới đây tự kiểm tra provider `jar` trước khi tạo ZIP, vì runtime Java thu gọn có thể không cung cấp provider này.

Lưu thành `ZipProviderDemo.java`:

~~~java
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;

public class ZipProviderDemo {
    public static void main(String[] args) throws Exception {
        boolean jarInstalled = FileSystemProvider.installedProviders()
                .stream()
                .anyMatch(provider ->
                        provider.getScheme().equalsIgnoreCase("jar"));

        if (!jarInstalled) {
            System.out.println("ZIP provider unavailable");
            return;
        }

        Path zipFile = Files.createTempFile("nio2-demo-", ".zip");
        Files.delete(zipFile); // Để provider tạo ZIP hợp lệ từ đầu.

        try {
            URI zipUri = URI.create("jar:" + zipFile.toUri());

            try (FileSystem zipFs = FileSystems.newFileSystem(
                    zipUri, Map.of("create", "true"))) {

                Path entry = zipFs.getPath("/hello.txt");
                Files.writeString(entry, "Hello ZIP");

                System.out.println("ZIP scheme: "
                        + zipFs.provider().getScheme());
                System.out.println("Same as default: "
                        + (zipFs == FileSystems.getDefault()));
                System.out.println("Content: " + Files.readString(entry));
            }
        } finally {
            Files.deleteIfExists(zipFile);
        }
    }
}
~~~

Chạy:

~~~bash
javac ZipProviderDemo.java
java ZipProviderDemo
~~~

Nếu `jar` provider phù hợp đã được cài đặt và môi trường cho phép tạo/xóa tệp tạm, stdout là:

~~~text
ZIP scheme: jar
Same as default: false
Content: Hello ZIP
~~~

Nếu không có `jar` provider, stdout là:

~~~text
ZIP provider unavailable
~~~

Đọc kỹ cơ chế:

1. `installedProviders()` kiểm tra provider *đã cài đặt*. Kiểm tra này không mở ZIP.
2. `Files.createTempFile` rồi `Files.delete` bảo đảm ta có một vị trí tạm chưa chứa ZIP, trong khi `"create" = "true"` bảo ZIP provider tạo lưu trữ mới.
3. `FileSystems.newFileSystem(zipUri, Map.of("create", "true"))` giao URI scheme `jar` cho ZIP filesystem provider; đây là lúc instance `FileSystem` được mở.
4. `zipFs.getPath("/hello.txt")` tạo `Path` bên trong ZIP, không phải tệp `/hello.txt` trên filesystem mặc định.
5. `Files.writeString` và `Files.readString` định tuyến I/O qua provider của `Path`.
6. `try-with-resources` đóng ZIP filesystem và ghi hoàn tất nội dung lưu trữ; `finally` xóa ZIP tạm kể cả khi có lỗi.

Tệp ZIP bên ngoài (`zipFile`) là `Path` thuộc default filesystem, trong khi entry (`entry`) là `Path` thuộc ZIP filesystem. Cả hai cùng tham gia một ví dụ nhưng **không** thuộc cùng filesystem.

Nếu trình chạy bị hạn chế quyền ghi tệp tạm, thiếu ZIP module, hoặc provider hoạt động không như JDK ZIP thông thường, chương trình có thể ném ngoại lệ. Xử lý ngoại lệ thực tế cần tùy môi trường.

## 7. Quan hệ giữa URI scheme, provider và filesystem

URI có thể mang dấu hiệu chọn provider:

~~~text
file:///home/user/data.txt
jar:file:///home/user/archive.zip!/docs/readme.txt
jrt:/java.base/java/lang/String.class
~~~

Đây là các **ví dụ hình dạng URI**, không phải những đường dẫn chắc chắn tồn tại trên máy đang chạy.

- `file:` thường dùng default provider, nhưng URI phải hợp lệ theo quy tắc đường dẫn hệ điều hành.
- `jar:` mô tả tài nguyên liên quan đến archive ZIP/JAR, thường dùng ZIP provider. Phần trước `!` xác định archive; phần sau xác định entry.
- `jrt:` biểu diễn tài nguyên của Java runtime image qua JRT filesystem khi provider được cung cấp.

Scheme trả lời **provider nào có khả năng xử lý**, còn `FileSystem` trả lời **instance nào đang chứa đường dẫn**. Cần phân biệt:

~~~text
Scheme "jar"
    │
    ▼
ZIP FileSystemProvider
    ├── opened archive A → FileSystem A → Path "/docs/readme.txt"
    └── opened archive B → FileSystem B → Path "/docs/readme.txt"
~~~

Hai `Path` có cùng cách in chuỗi nhưng khác đối tượng `FileSystem` và có thể chứa dữ liệu hoàn toàn khác nhau. Vì vậy, kiểm tra `path.getFileSystem()` và `path.getFileSystem().provider()` để hiểu nguồn gốc.

**Không nối chuỗi `"jar:"` hay đường dẫn filesystem theo quy tắc chung một cách tùy tiện.** Việc xây URI cần chú ý encode ký tự đặc biệt, separator và cú pháp provider; `zipFile.toUri()` trong ví dụ cung cấp URI đã escape hợp lệ của archive trên hệ thống tệp mặc định.

## 8. Những lỗi phổ biến và cách xử lý

### Giả định provider có mặt trên mọi JDK

**Sai:** mặc định cho rằng `jar`, `jrt` hoặc một provider bên thứ ba luôn được cài đặt.

**Đúng:** kiểm tra `FileSystemProvider.installedProviders()` hoặc xử lý ngoại lệ `ProviderNotFoundException` khi tạo filesystem, đồng thời làm rõ yêu cầu runtime/module của ứng dụng.

### Nhầm cài đặt provider với mở filesystem

**Sai:** thấy `jar` trong danh sách và lập tức gọi `FileSystems.getFileSystem(uri)` để lấy archive chưa mở.

**Đúng:** dùng `FileSystems.newFileSystem(...)` theo hợp đồng provider khi cần mở mới. `getFileSystem(uri)` chỉ thích hợp khi filesystem tương ứng đã có; trường hợp đã được tạo có thể cần đóng đúng lúc và tránh mở trùng.

### Dùng `Path.of(String)` cho đường dẫn trong ZIP

**Sai:**

~~~java
Path entry = Path.of("/docs/hello.txt");
~~~

Đây là đường dẫn trên default filesystem, bất kể ứng dụng đang có một `zipFs`.

**Đúng** (khi `zipFs` đã được mở):

~~~java
Path entry = zipFs.getPath("/docs/hello.txt");
~~~

Hai đoạn chỉ minh họa dòng lệnh, không phải chương trình độc lập.

### Quên vòng đời hệ thống tệp mở thêm

Đường dẫn `Path` vẫn có thể tồn tại như object sau khi `FileSystem` bị đóng, nhưng thao tác I/O trên nó có thể ném `ClosedFileSystemException`. Với filesystem mở mới, dùng `try-with-resources` và bảo đảm sử dụng `Path` khi filesystem còn mở. Ngược lại, không đóng default filesystem như tài nguyên tạm.

### Trộn `Path` thuộc provider khác trong phép xử lý tên

`resolve(Path)` hoặc `relativize(Path)` không phải API chuyển file giữa filesystem. Một số triển khai ném `ProviderMismatchException` nếu kết hợp đối số từ provider không tương thích. Khi cần truyền dữ liệu, cân nhắc `Files.copy(source, target)` với hai đường dẫn đã tạo đúng filesystem đích/nguồn, và xử lý `IOException`, khả năng/provider hỗ trợ.

### Tưởng `installedProviders()` là bản đồ tất cả ổ đĩa và archive

API trả **các loại provider**, không liệt kê ổ đĩa, mount point, ZIP đang mở, file tồn tại hay tài nguyên trong mỗi hệ thống tệp. Để khảo sát thư mục root của filesystem cụ thể, dùng `fileSystem.getRootDirectories()`; để kiểm tra file, dùng các API `Files.*` trên `Path`.

## 9. Bài tập áp dụng

1. Chạy `InstalledProvidersDemo` trong hai môi trường Java khác nhau (ví dụ JDK đầy đủ và runtime được đóng gói). So sánh các scheme ở cuối stdout; giải thích tại sao số lượng provider có thể thay đổi.
2. Thay `Path.of("sample.txt")` bằng `FileSystems.getDefault().getPath("sample.txt")` và kiểm tra quan hệ `getFileSystem() == FileSystems.getDefault()`.
3. Trong `ZipProviderDemo`, in `zipFile.getFileSystem().provider().getScheme()` và `entry.getFileSystem().provider().getScheme()` trước khi đóng ZIP. Dự đoán kết quả (`file` và `jar` nếu provider có mặt).
4. Thử dùng `zipFs.getPath("another.txt")` để tạo entry thứ hai, rồi đọc lại bằng `Files.readString`. Kiểm tra khi nào cần tạo thư mục cha nếu entry nằm trong thư mục con.
5. Giải thích vì sao `installedProviders()` trả về `jar` vẫn không đủ để kết luận `FileSystems.getFileSystem(uri)` sẽ trả filesystem của ZIP chưa từng được mở.

## 10. Ghi nhớ

- `FileSystems.getDefault()` cung cấp **một hệ thống tệp mặc định**; các `Path` tạo từ chuỗi bằng `Path.of` thuộc hệ thống đó.
- `FileSystemProvider.installedProviders()` liệt kê **các provider được cài đặt** trong runtime; default provider xuất hiện đầu danh sách, các provider khác có thể khác nhau giữa môi trường.
- **Provider → FileSystem → Path** là quan hệ gốc. Scheme nhận diện loại provider, `FileSystem` là instance, còn `Path` thuộc một instance cụ thể.
- `FileSystems.newFileSystem(...)` dùng để mở/tạo filesystem khi được provider hỗ trợ; `getFileSystem(...)` dùng để tìm filesystem đã có.
- Đừng mặc định mọi đường dẫn là đường dẫn ổ đĩa; giữ nguyên `Path` cùng `FileSystem` của nó, kiểm tra provider khi cần và đóng filesystem mở thêm đúng vòng đời.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-systems-003">Quan hệ giữa Path với FileSystem tạo ra nó</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ giữa `Path` và `FileSystem` tạo ra nó

## 1. Ý tưởng cốt lõi

Trong Java NIO.2 (`java.nio.file`), một `Path` không chỉ là chuỗi mô tả vị trí. Nó là **đối tượng đường dẫn thuộc về một `FileSystem` cụ thể**. `FileSystem` quyết định cách phân tích tên, dấu phân cách, đường dẫn gốc và một số quy tắc thao tác với đường dẫn đó.

- `FileSystem`: mô hình của một hệ thống tệp, chẳng hạn hệ thống tệp mặc định của máy hoặc một tệp ZIP được mở như hệ thống tệp.
- `Path`: vị trí của tệp/thư mục *bên trong* hệ thống tệp đó; bản thân `Path` không đảm bảo tệp thực tế tồn tại.
- `FileSystemProvider`: thành phần cung cấp triển khai cho loại hệ thống tệp tương ứng, ví dụ provider mặc định với scheme `file` hoặc ZIP filesystem provider với scheme `jar`.

Quan hệ cốt lõi:

~~~text
FileSystemProvider (triển khai cách truy cập)
          |
          +--> FileSystem A --> Path A1, Path A2, ...
          |
          +--> FileSystem B --> Path B1, Path B2, ...
~~~

**Một `Path` luôn gắn với một `FileSystem` thông qua `path.getFileSystem()`.** Một provider có thể quản lý nhiều `FileSystem`; nhiều `Path` có thể cùng thuộc một `FileSystem`. Ngay cả khi hai đường dẫn có `toString()` giống nhau, chúng chưa chắc nói đến cùng đối tượng tệp nếu thuộc các hệ thống tệp khác nhau.

Ví dụ, `/docs/readme.md` trong ZIP không phải `/docs/readme.md` trên ổ đĩa máy, dù dạng chữ trông tương tự.

## 2. Tạo `Path` từ đâu thì thuộc `FileSystem` đó

### Đường dẫn hệ thống tệp mặc định

~~~java
Path p = Path.of("documents", "report.txt");
FileSystem owner = p.getFileSystem();
FileSystem defaultFs = FileSystems.getDefault();

System.out.println(owner == defaultFs); // true trên JDK thông thường
~~~

`Path.of(String, String...)` (tương tự `Paths.get(String, String...)`) sử dụng **hệ thống tệp mặc định**. `FileSystems.getDefault()` trả về hệ thống tệp mặc định của tiến trình. Đường dẫn có thể tương đối hay tuyệt đối; hai tính chất này không thay đổi hệ thống tệp sở hữu nó.

### Đường dẫn được tạo rõ ràng từ một `FileSystem`

~~~java
Path p = someFileSystem.getPath("/docs/report.txt");
FileSystem owner = p.getFileSystem();
~~~

Ở đây `p` thuộc `someFileSystem`. Việc gọi `getPath` chưa tạo ra tệp. Cú pháp `"/docs/report.txt"` được hệ thống tệp cụ thể diễn giải; không nên coi nó là đường dẫn hệ điều hành trong mọi trường hợp.

### Đường dẫn từ URI

`Path.of(URI)` lựa chọn provider qua scheme của URI. Với scheme `file`, thao tác này thường tạo đường dẫn trên hệ thống tệp mặc định. Với một URI kiểu `jar:file:...!/entry`, hệ thống tệp ZIP tương ứng thường phải được mở/đăng ký trước thì việc lấy `Path` mới thực hiện được; nếu không có provider hay hệ thống tệp thích hợp có thể phát sinh `ProviderNotFoundException` hoặc `FileSystemNotFoundException`. Cách dễ kiểm soát nhất khi đã cầm đối tượng `FileSystem` là dùng chính `fs.getPath(...)`.

**Không tự suy luận hệ thống tệp từ chuỗi `path.toString()`.** Hãy hỏi trực tiếp `path.getFileSystem()` và khi cần hỏi thêm `path.getFileSystem().provider()`.

## 3. Hai lớp quan hệ cần phân biệt

| Kiểm tra | Điều xác nhận | Điều không tự suy ra |
| --- | --- | --- |
| `a.getFileSystem() == b.getFileSystem()` | Cùng một đối tượng `FileSystem` trong tiến trình | Cùng tệp hoặc cùng vị trí |
| `a.getFileSystem().provider() == b.getFileSystem().provider()` | Cùng một đối tượng provider | Cùng hệ thống tệp |
| `a.equals(b)` | Hai đối tượng đường dẫn bằng nhau theo quy tắc của triển khai `Path` | Chắc chắn cùng tệp vật lý trong mọi tình huống |
| `Files.isSameFile(a, b)` | API thử xác định hai đường dẫn chỉ cùng một tệp đang tồn tại | Không cần truy cập I/O hay luôn có kết quả với mọi provider |

Ví dụ: hai ZIP khác nhau có thể được mở bởi **cùng ZIP provider**, nhưng mỗi ZIP tạo thành **một `FileSystem` riêng**. Mỗi hệ thống tệp đều có `/docs/guide.txt` của nó; hai đường dẫn đó độc lập. Ngược lại, hai tên khác nhau trong cùng hệ thống tệp vẫn có thể cùng chỉ vào một tệp vì symbolic link/hard link; `Path.equals` không thay thế kiểm tra danh tính tệp.

`==` ở hai hàng đầu là phép so sánh **danh tính đối tượng**. Khi cần đánh giá tính bằng nhau của đường dẫn, dùng `equals` theo ngữ nghĩa của provider; khi cần kiểm tra hai đường dẫn trỏ cùng tệp hiện hữu, cân nhắc `Files.isSameFile` và xử lý `IOException`.

## 4. Các phép biến đổi `Path` giữ nguyên hệ thống tệp

Những phép xử lý tên trên một `Path` như `getParent()`, `getFileName()`, `subpath(...)`, `normalize()`, `toAbsolutePath()` và `resolve(String)` tạo đường dẫn kết quả trong **cùng `FileSystem`** với đường dẫn ban đầu (trừ trường hợp API trả `null`, ví dụ `getParent()` của một đường dẫn không có cha).

~~~java
Path base = Path.of("data", "reports");
Path child = base.resolve("daily.txt");
Path absolute = child.toAbsolutePath().normalize();

System.out.println(base.getFileSystem() == child.getFileSystem());    // true
System.out.println(base.getFileSystem() == absolute.getFileSystem()); // true
~~~

`normalize()` chỉ đơn giản hóa các thành phần tên như `.` và `..` theo cú pháp; nó **không** xác minh tồn tại, theo dõi symbolic link hay bảo đảm hai đường dẫn truy cập cùng tệp. `toAbsolutePath()` chuyển cách biểu diễn từ tương đối sang tuyệt đối theo ngữ cảnh của hệ thống tệp đang dùng; nó không chuyển đường dẫn sang hệ thống tệp khác.

Đặc biệt, `Path.resolve(Path other)` và `Path.relativize(Path other)` cần các đường dẫn tương thích theo quy tắc triển khai. Trộn `Path` của provider khác thường dẫn đến `ProviderMismatchException`. `relativize` còn có thể ném `IllegalArgumentException` khi không thể tạo quan hệ tương đối phù hợp (chẳng hạn khác loại tuyệt đối/tương đối trên provider thông thường).

## 5. Ví dụ chạy được: hệ thống tệp mặc định và ZIP filesystem

Chương trình Java 17+ dưới đây tạo một tệp ZIP tạm thời, mở nó bằng ZIP provider có sẵn trong bản JDK thông dụng (`jdk.zipfs`), tạo tệp *bên trong ZIP* rồi so sánh nguồn gốc của các `Path`. Tên tệp ZIP ngẫu nhiên không được in ra nên stdout ổn định.

Lưu thành `PathFileSystemDemo.java`:

~~~java
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.util.Map;

public class PathFileSystemDemo {
    public static void main(String[] args) throws IOException {
        Path hostPath = Path.of("notes.txt");
        FileSystem defaultFs = FileSystems.getDefault();
        System.out.println("host belongs to default: "
                + (hostPath.getFileSystem() == defaultFs));

        Path archive = Files.createTempFile("path-fs-demo-", ".zip");
        // ZIP provider cần tệp ZIP mới khi dùng create=true.
        Files.delete(archive);

        try {
            URI jarUri = URI.create("jar:" + archive.toUri());
            FileSystem zipFs = FileSystems.newFileSystem(
                    jarUri, Map.of("create", "true"));

            try (zipFs) {
                Path insideZip = zipFs.getPath("/docs/hello.txt");
                Files.createDirectories(insideZip.getParent());
                Files.writeString(insideZip, "Xin chao NIO.2");

                System.out.println("zip path belongs to zipFs: "
                        + (insideZip.getFileSystem() == zipFs));
                System.out.println("same filesystem: "
                        + (hostPath.getFileSystem() == insideZip.getFileSystem()));
                System.out.println("zip content: " + Files.readString(insideZip));

                try {
                    insideZip.resolve(hostPath);
                    System.out.println("unexpected resolve success");
                } catch (ProviderMismatchException expected) {
                    System.out.println("cross-provider resolve rejected: true");
                }
            }

            System.out.println("zip filesystem open: " + zipFs.isOpen());
        } finally {
            Files.deleteIfExists(archive);
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên có module `jdk.zipfs`:

~~~bash
javac PathFileSystemDemo.java
java PathFileSystemDemo
~~~

Stdout:

~~~text
host belongs to default: true
zip path belongs to zipFs: true
same filesystem: false
zip content: Xin chao NIO.2
cross-provider resolve rejected: true
zip filesystem open: false
~~~

Đọc ví dụ theo thứ tự:

1. `hostPath` sinh từ `Path.of(String)` nên thuộc hệ thống tệp mặc định, dù tệp `notes.txt` **không cần tồn tại**.
2. `jarUri` trỏ đến **chính tệp ZIP trên ổ đĩa**, còn `zipFs.getPath("/docs/hello.txt")` biểu diễn **một entry bên trong ZIP**. Đây là hai tầng địa chỉ khác nhau.
3. `Files.createDirectories` và `Files.writeString` hoạt động theo provider của `insideZip`; dữ liệu được ghi thành entry ZIP, không thành thư mục `/docs` trên ổ đĩa mặc định.
4. `insideZip.resolve(hostPath)` bị từ chối vì đối số `hostPath` thuộc default provider, khác ZIP provider. Nếu chỉ cần nối *tên tương đối* trong ZIP, dùng `insideZip.getParent().resolve("another.txt")` hoặc `zipFs.getPath(...)`.
5. `try (zipFs)` đóng ZIP filesystem và hoàn tất cập nhật tệp ZIP. `zipFs.isOpen()` lúc đó là `false`; các thao tác I/O mới trên `Path` thuộc hệ thống tệp đã đóng có thể ném `ClosedFileSystemException`.
6. `finally` dọn tệp ZIP tạm. Đóng và dọn tài nguyên cần có ngay cả khi xuất hiện ngoại lệ ở giữa.

**Giới hạn của ví dụ:** bản JRE tùy biến có thể thiếu `jdk.zipfs`, khi đó không thể chạy phần ZIP provider nếu không bổ sung module. Trong JDK tiêu chuẩn, ví dụ chạy độc lập, không cần thư viện ngoài.

## 6. Khi phối hợp hai hệ thống tệp

Có hai thao tác khác nhau rất dễ bị nhầm:

**Ghép hoặc tính đường dẫn tương đối**: xử lý *tên* trong một hệ thống tệp. Hãy tạo cả hai toán hạng bằng cùng `FileSystem` nếu dùng `resolve(Path)` hoặc `relativize(Path)`; hoặc dùng chuỗi tương đối khi có ý định tạo tên con trong hệ thống tệp đích.

**Sao chép dữ liệu giữa hai hệ thống tệp**: `Files.copy(source, target, options...)` nhận một đường dẫn nguồn và một đường dẫn đích có thể thuộc các provider khác nhau. API thực hiện thao tác sao chép theo khả năng của các provider, có thể phát sinh `IOException` hoặc lỗi tùy chọn/thuộc tính không được hỗ trợ. Trường hợp từ ZIP sang ổ đĩa:

~~~java
// insideZip thuộc zipFs, destination thuộc hệ thống tệp mặc định.
// Phải chạy đoạn này khi zipFs vẫn đang mở.
Path destination = Path.of("exported-hello.txt");
Files.copy(insideZip, destination);
~~~

Đây là **đoạn mã minh họa đặt bên trong khối `try (zipFs)`** của ví dụ ở trên, không phải một chương trình độc lập. Với một chương trình chạy lại nhiều lần, nhớ quản lý việc tệp đích đã tồn tại (ví dụ dùng `StandardCopyOption.REPLACE_EXISTING` nếu thực sự muốn thay thế).

`Files.move` giữa các provider có giới hạn khác với đổi tên nội bộ: việc di chuyển có thể được thực hiện bằng sao chép rồi xóa, không mang tính nguyên tử; yêu cầu `ATOMIC_MOVE` có thể gây `AtomicMoveNotSupportedException`. Không giả định rằng mọi thao tác trên `Path` đều an toàn hoặc có cùng hiệu năng khi đi qua hệ thống tệp khác nhau.

## 7. Quy tắc phụ thuộc `FileSystem`

Ngoài ownership, `FileSystem` ảnh hưởng đến các quy tắc và khả năng sau:

- **Phân tách tên**: `fs.getSeparator()` có thể là `/` hoặc `\` tùy provider/hệ điều hành; không hardcode đường dẫn hệ điều hành cho mọi provider.
- **Root và đường dẫn tuyệt đối**: `fs.getRootDirectories()` và `path.getRoot()` được diễn giải riêng theo hệ thống tệp; một đường dẫn ZIP có gốc `/` dù ZIP chỉ là một file trên máy.
- **Path matcher**: `fs.getPathMatcher("glob:*.txt")` tạo matcher theo cú pháp và đặc tính provider; kết quả phụ thuộc quy tắc của hệ thống tệp.
- **Thuộc tính file**: `fs.supportedFileAttributeViews()` cho biết các view được hỗ trợ; các provider không nhất thiết hỗ trợ giống nhau các thuộc tính POSIX, DOS, owner hay ACL.
- **Trạng thái/truy cập**: `fs.isOpen()`, `fs.isReadOnly()` và các lời gọi `Files.*` có thể dẫn đến `ClosedFileSystemException`, `ReadOnlyFileSystemException` hoặc `IOException` trong hoàn cảnh tương ứng.

Tất cả đều là lý do để truyền `Path` hoặc `FileSystem` cho hàm thư viện thay vì chỉ truyền một chuỗi rồi tái tạo đường dẫn bằng hệ thống tệp mặc định ở lớp bên trong.

Ví dụ, hàm nhận `Path root` nên viết:

~~~java
Path output = root.resolve("output.txt");
~~~

Cách này giữ `output` trong hệ thống tệp sở hữu `root`. Nếu thay bằng `Path.of(root.toString(), "output.txt")`, đoạn code vô tình **gắn đường dẫn mới vào hệ thống tệp mặc định** và có thể gây lỗi khi `root` nằm trong ZIP hoặc provider khác.

## 8. Vòng đời `FileSystem` và xử lý lỗi

- `FileSystems.getDefault()` quản lý hệ thống tệp mặc định cho ứng dụng. **Không tự đóng** default filesystem; lời gọi `close()` trên triển khai mặc định thông dụng có thể ném `UnsupportedOperationException`.
- Hệ thống tệp mở thêm bằng `FileSystems.newFileSystem(...)` thường do code của bạn quản lý vòng đời và nên đóng bằng `try-with-resources` khi hoàn tất. Đừng trả `Path` từ ZIP filesystem ra khỏi scope rồi đóng filesystem trước khi caller truy cập dữ liệu.
- Nếu mở hai lần cùng URI của một hệ thống tệp đã đăng ký, tùy provider có thể nhận `FileSystemAlreadyExistsException`. `FileSystems.getFileSystem(uri)` tìm một hệ thống tệp đã đăng ký, nhưng sẽ có `FileSystemNotFoundException` khi chưa có.
- Khi nhận `Path` từ bên ngoài, kiểm tra bằng `path.getFileSystem()` nếu API yêu cầu một filesystem nhất định; nếu API có thể xử lý provider bất kỳ, giữ nguyên `Path` của caller, không chuyển qua `toString()` rồi `Path.of(...)`.

**Cần phân biệt ba loại lỗi:** `ProviderMismatchException` (kết hợp hai `Path` không tương thích), `FileSystemNotFoundException` (không tìm được filesystem đã được đăng ký cho URI), và `ClosedFileSystemException` (sử dụng tài nguyên của filesystem đã đóng). Chúng chỉ ra những vấn đề khác nhau nên cách khắc phục cũng khác nhau.

## 9. Câu hỏi tự kiểm tra

1. Nếu hai đường dẫn đều in ra `/config/app.yml`, có thể kết luận chúng chỉ cùng tệp không? **Không**: cần biết `FileSystem` và có thể cần kiểm tra file thực tế.
2. Vì sao `zipFs.getPath("/x.txt")` không thể thay tùy tiện bằng `Path.of("/x.txt")`? Vì cách thứ hai tạo `Path` thuộc filesystem mặc định, không phải ZIP.
3. `normalize()`, `resolve(String)` có chuyển một đường dẫn ZIP thành đường dẫn host không? **Không**: chúng giữ filesystem sở hữu.
4. Muốn đưa nội dung từ `Path` trong ZIP ra ổ đĩa, nên ghép path hay sao chép? Dùng `Files.copy` với `target` được tạo trong filesystem đích và xử lý lỗi, vòng đời tài nguyên.
5. Nên nhận `String path` hay `Path path` trong API thao tác tệp đa provider? Thường là **`Path`**, vì nó giữ cả cấu trúc tên và thông tin hệ thống tệp sở hữu.

**Ghi nhớ:** `Path` mang theo danh tính `FileSystem` đã tạo ra nó. Việc xử lý *tên đường dẫn* phải tôn trọng danh tính này; việc *chuyển dữ liệu* giữa các hệ thống tệp cần sử dụng API I/O tương ứng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-systems-004">newFileSystem, getFileSystem và quyền đóng tài nguyên</a>

<details>
<summary>Xem chi tiết</summary>

# newFileSystem, getFileSystem và quyền đóng tài nguyên trong Java NIO.2

## 1. Bài toán: FileSystem là gì và ai quản lý vòng đời?

Trong Java NIO.2 (`java.nio.file`), một `FileSystem` là môi trường quản lý các `Path`, thư mục, tệp và thao tác I/O theo quy tắc của một *file system provider*. Hệ thống tệp có thể là hệ thống tệp mặc định của hệ điều hành, nội dung một tệp ZIP/JAR, hoặc một hệ thống tệp ảo do thư viện cung cấp.

Cần phân biệt ba đối tượng:

- `FileSystemProvider`: triển khai cơ chế truy cập một loại hệ thống tệp và xác định cách tạo/tra cứu nó.
- `FileSystem`: đối tượng đại diện cho một hệ thống tệp đang được truy cập; có thể có vòng đời `open → close`.
- `Path`: đường dẫn thuộc **một** `FileSystem` cụ thể. `Path` không sở hữu hay tự đóng `FileSystem`.

Ví dụ, đường dẫn `/data.txt` trong ZIP và đường dẫn `/data.txt` trên ổ đĩa không chỉ khác vị trí: chúng thuộc hai provider/file system khác nhau. Hãy lấy đường dẫn đến tệp trong ZIP bằng `zipFs.getPath("/data.txt")`, không dùng `Path.of("/data.txt")` vốn thuộc hệ thống tệp mặc định.

Câu hỏi quyết định khi làm việc với API này là: **Đoạn mã hiện tại đã tạo/mở `FileSystem` mới, hay chỉ nhận tham chiếu đến `FileSystem` đang được quản lý ở nơi khác?** Câu trả lời quyết định ai được gọi `close()`.

## 2. Ba cách lấy FileSystem và ý nghĩa của chúng

| API | Vai trò | Kết quả điển hình | Quyền đóng |
| --- | --- | --- | --- |
| `FileSystems.getDefault()` | Lấy hệ thống tệp mặc định của JVM | Cùng `FileSystem` mặc định qua các lần gọi | **Không đóng**; `close()` của default FS ném `UnsupportedOperationException` |
| `FileSystems.newFileSystem(uri, env)` | Yêu cầu provider tạo/mở một `FileSystem` mới theo URI và cấu hình | Ví dụ mở một ZIP qua URI `jar:file:...` | Mã tạo thành công thường **sở hữu** `FileSystem` và phải đóng |
| `FileSystems.getFileSystem(uri)` | Lấy tham chiếu đến `FileSystem` **đang tồn tại** được provider tìm theo URI | Với ZipFS, lấy instance ZIP đã được mở trước đó | Mã tra cứu chỉ **mượn** tham chiếu; không tự đóng |

`FileSystems.getFileSystem(uri)` **không tạo mới**, không đảm bảo tệp ZIP trên đĩa đã được mount, và không có cơ chế tự tăng bộ đếm tham chiếu để giữ hệ thống tệp sống. Nếu chưa có instance phù hợp, thao tác thường ném `FileSystemNotFoundException`. Chi tiết tra cứu và nhận diện URI do provider quyết định.

Đối với `FileSystems.newFileSystem(URI, Map<String, ?>)`, Java chọn provider theo *scheme* của URI; một provider có thể từ chối URI không hỗ trợ, cấu hình sai hoặc trường hợp file system cùng định danh đã tồn tại. URI của ZipFS dùng scheme `jar`; URI `file` thông thường dùng cho đường dẫn hệ thống tệp mặc định. Không được coi hai loại URI này tương đương.

Các overload cần biết trong Java 17+:

~~~java
FileSystem a = FileSystems.newFileSystem(uri, env);
FileSystem b = FileSystems.newFileSystem(uri, env, classLoader);
FileSystem c = FileSystems.newFileSystem(path, env);
FileSystem d = FileSystems.newFileSystem(path);
~~~

Đây là **các lời gọi minh họa**, không phải chương trình độc lập; `uri`, `env`, `classLoader` và `path` cần được định nghĩa trước. Với overload nhận `Path`, Java có thể tìm provider hiểu định dạng tệp (ví dụ ZIP/JAR); cơ chế tìm kiếm, lựa chọn provider và khả năng mở tệp phụ thuộc overload/provider. Không giả định mọi overload đều tra cứu cùng một registry hay có chính sách trùng lặp giống hệt nhau.

## 3. Ví dụ chạy được: tạo ZIP FS, mượn, đóng và mở lại

JDK thông dụng có module `jdk.zipfs` hỗ trợ xem ZIP/JAR như một hệ thống tệp. Ví dụ sau dùng thư mục tạm mới, nên không ghi đè tệp hiện có. Chương trình tạo ZIP, ghi nội dung, lấy tham chiếu *mượn* đến cùng ZIP FS, đóng qua chủ sở hữu, rồi mở lại ZIP để chứng minh dữ liệu đã được lưu.

Tạo tệp `FileSystemLifecycleDemo.java`:

~~~java
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class FileSystemLifecycleDemo {
    public static void main(String[] args) throws Exception {
        Path tempDir = Files.createTempDirectory("nio-fs-demo-");
        Path zipFile = tempDir.resolve("example.zip");
        URI zipUri = URI.create("jar:" + zipFile.toUri());

        FileSystem owner;
        try (FileSystem zipFs =
                FileSystems.newFileSystem(zipUri, Map.of("create", "true"))) {
            owner = zipFs;

            Path entry = zipFs.getPath("/hello.txt");
            Files.writeString(entry, "Xin chào từ ZIP\n");

            // getFileSystem chỉ mượn instance đang mở.
            FileSystem borrowed = FileSystems.getFileSystem(zipUri);
            System.out.println("Cùng đối tượng: " + (borrowed == zipFs));
            System.out.println(Files.readString(borrowed.getPath("/hello.txt")).strip());

            // Không gọi borrowed.close(): owner vẫn đang sử dụng nó.
        } // Chủ sở hữu đóng ZipFS; dữ liệu ZIP được hoàn tất.

        System.out.println("Đã đóng: " + !owner.isOpen());

        try {
            FileSystems.getFileSystem(zipUri);
        } catch (FileSystemNotFoundException e) {
            System.out.println("Không tìm thấy ZIP FS đang mở");
        }

        try (FileSystem reopened = FileSystems.newFileSystem(zipUri, Map.of())) {
            System.out.println(
                    "Đọc lại: " + Files.readString(reopened.getPath("/hello.txt")).strip());
        }

        Files.delete(zipFile);
        Files.delete(tempDir);
    }
}
~~~

Biên dịch và chạy bằng **JDK 17+ có `jdk.zipfs`**:

~~~bash
javac FileSystemLifecycleDemo.java
java FileSystemLifecycleDemo
~~~

Đầu ra dự kiến trên JDK thông dụng:

~~~text
Cùng đối tượng: true
Xin chào từ ZIP
Đã đóng: true
Không tìm thấy ZIP FS đang mở
Đọc lại: Xin chào từ ZIP
~~~

Giải thích theo trình tự:

1. `Files.createTempDirectory` sinh thư mục riêng; `zipFile.toUri()` tạo URI `file:` đúng cho nền tảng đang chạy. Ghép tiền tố `jar:` để đưa URI cho ZipFS.
2. `Map.of("create", "true")` là cấu hình **riêng của ZipFS**: tạo tệp lưu trữ nếu chưa tồn tại. Với một ZIP có sẵn, có thể dùng `Map.of()` để mở.
3. `newFileSystem` trả về instance do đoạn `try` sở hữu. `getPath("/hello.txt")` trả `Path` **bên trong ZIP**.
4. `getFileSystem(zipUri)` trả về instance đang mở; phép so sánh `==` là `true` trong ví dụ này. Tham chiếu `borrowed` không được đóng.
5. Kết thúc `try-with-resources`, Java gọi `zipFs.close()` kể cả khi thân khối ném exception. Với `jdk.zipfs`, thao tác này hoàn tất việc ghi vào archive.
6. Sau khi đóng, ZipFS không còn đăng ký instance đang mở cho URI đó; `getFileSystem(zipUri)` ném `FileSystemNotFoundException`. Đây là **hành vi của ZipFS trong ví dụ**; API cho phép provider khác xử lý lookup sau `close()` khác nhau.
7. `newFileSystem(zipUri, Map.of())` mở lại archive đã có; lúc này `create` không cần thiết. Lần mở lại cũng được đóng qua `try-with-resources` trước khi xóa tệp tạm.

Nếu dùng runtime image đã lược bỏ `jdk.zipfs`, ví dụ có thể không tìm được provider `jar`. Hãy chạy bằng JDK đầy đủ có module đó; không suy diễn rằng API `FileSystems` tự cung cấp mọi provider.

## 4. Quy tắc sở hữu: người tạo đóng, người mượn không đóng

**Ownership (quyền sở hữu vòng đời)** là thỏa thuận giữa các lớp/method, không phải thuộc tính mà `FileSystem` tự kiểm tra. API không phân biệt được biến `owner` với biến `borrowed`: cả hai có thể chỉ đến **cùng một object**, và gọi `close()` qua biến nào cũng có hiệu lực trên object đó.

Quy tắc sử dụng thực tế:

- **Tạo riêng cho một công việc** bằng `newFileSystem`: đặt trong `try-with-resources`, đóng sau khi mọi `Path`, stream, channel và tác vụ sử dụng file system hoàn tất.
- **Lấy qua `getFileSystem` hoặc `path.getFileSystem()`**: xem là tham chiếu mượn trừ khi có hợp đồng chuyển quyền sở hữu rõ ràng. Không tự đóng trong hàm tiện ích.
- **Được truyền từ tham số hoặc dependency injection**: mặc định là tài nguyên do caller/application quản lý; hàm nhận không đóng nếu hợp đồng không yêu cầu.
- **Hệ thống tệp mặc định** lấy bằng `getDefault()`: không đưa vào `try-with-resources`; `close()` ném `UnsupportedOperationException`.
- **Quản lý dùng chung giữa nhiều thành phần/luồng**: đặt nơi tạo/đóng tại tầng sở hữu rõ ràng (ví dụ service manager), bảo đảm không còn tác vụ đang dùng trước khi đóng.

Cách viết hàm *mượn* an toàn:

~~~java
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;

static String readEntry(FileSystem borrowedFs, String entryName) throws IOException {
    // Hàm mượn FileSystem và chỉ đọc dữ liệu.
    return Files.readString(borrowedFs.getPath(entryName));
    // Không close borrowedFs ở đây.
}
~~~

Đoạn trên là **method minh họa**, phải đặt trong một class và có quyền đọc entry mong muốn. Caller giữ trách nhiệm đóng `FileSystem` nếu caller đã tạo ra nó. Hàm đọc không giữ tham chiếu sống vượt thời gian gọi.

Một anti-pattern nguy hiểm:

~~~java
static String badRead(FileSystem sharedFs) throws IOException {
    try (sharedFs) {
        return Files.readString(sharedFs.getPath("/hello.txt"));
    }
}
~~~

`try (sharedFs)` hợp lệ trong Java 9+ nếu biến *effectively final*, nhưng vẫn **sai về ownership** khi `sharedFs` là tham chiếu mượn. Kết thúc hàm sẽ đóng hệ thống tệp mà caller hoặc thread khác còn cần dùng. Tính hợp lệ của cú pháp không đồng nghĩa với việc có quyền đóng tài nguyên.

## 5. FileSystem, Path và các resource có vòng đời khác nhau

Một `Path` có thể được lưu vào biến, đưa vào collection hoặc trả khỏi phương thức, nhưng không giúp hệ thống tệp tiếp tục mở. Khi parent `FileSystem` đã đóng, các thao tác đọc/ghi trên `Path` liên quan có thể ném `ClosedFileSystemException`. Ví dụ minh họa lỗi:

~~~java
Path entry;
try (FileSystem zipFs = FileSystems.newFileSystem(zipUri, Map.of())) {
    entry = zipFs.getPath("/hello.txt");
}
Files.readString(entry); // ClosedFileSystemException: ZipFS đã đóng
~~~

Đây chỉ là **đoạn minh họa**, giả sử `zipUri` trỏ đến archive hợp lệ. Cách sửa là xử lý nội dung khi `FileSystem` còn mở, hoặc trả dữ liệu đã đọc (`String`/`byte[]`) thay vì trả `Path` có vòng đời dài hơn tài nguyên nền.

Đóng `FileSystem` sẽ đóng các channel, directory stream, watch service và các closeable object liên kết theo hợp đồng API; tuy vậy vẫn nên đóng những resource cục bộ ngay khi dùng xong để kiểm soát lỗi và thời gian giữ tài nguyên. Với stream từ `Files.lines(...)` hoặc `Files.walk(...)`, hãy dùng `try-with-resources` riêng để đóng `Stream` đúng lúc. Tránh đóng parent file system trong khi các luồng còn chạy thao tác I/O.

Ví dụ thứ tự đóng đúng (resource con đóng trước parent):

~~~java
try (FileSystem zipFs = FileSystems.newFileSystem(zipUri, Map.of())) {
    try (var lines = Files.lines(zipFs.getPath("/log.txt"))) {
        lines.forEach(System.out::println);
    }
} // Đóng ZipFS sau khi Stream đã đóng.
~~~

Đoạn minh họa giả định ZIP tồn tại và chứa `/log.txt`. Khi có nhiều lỗi trong `try-with-resources`, lỗi trong `close()` có thể xuất hiện dưới dạng *suppressed exception* của lỗi chính; không nuốt lỗi đóng vì ZipFS có thể cần hoàn tất dữ liệu.

## 6. Các tình huống thường gặp và cách xử lý

| Tình huống | Hành vi cần hiểu | Cách làm |
| --- | --- | --- |
| Muốn tạo ZIP mới | `newFileSystem(jarUri, Map.of("create", "true"))` | Chủ sở hữu đóng sau khi ghi xong |
| Muốn mở ZIP đã có | `newFileSystem(jarUri, Map.of())` | Dùng `try-with-resources` trong phạm vi công việc |
| ZIP FS đã được một component khác mở | `getFileSystem(jarUri)` lấy instance đang mở | Mượn và tuân theo vòng đời của component sở hữu |
| Chưa có instance nhưng gọi `getFileSystem` | `FileSystemNotFoundException` | Mở bằng `newFileSystem` ở nơi được giao quyền sở hữu |
| Cùng URI đang mở mà tiếp tục `newFileSystem(URI, ...)` | Với ZipFS thường ném `FileSystemAlreadyExistsException` | Tổ chức một chủ sở hữu rõ ràng; client lấy tham chiếu mượn |
| Đóng FS trong khi nơi khác sử dụng | Đường dẫn/I/O có thể gặp `ClosedFileSystemException` | Chỉ chủ sở hữu đóng khi các tác vụ đã kết thúc |
| Cố `close()` FS mặc định | `UnsupportedOperationException` | Không đóng `FileSystems.getDefault()` |
| Ghi vào FS chỉ đọc | `ReadOnlyFileSystemException` | Kiểm tra `isReadOnly()` và chế độ/provider |
| Không tìm được provider `jar` | Provider/module chưa có hoặc không được tải | Kiểm tra JDK/runtime có `jdk.zipfs` |
| Tạo/mở ZIP với URI hoặc cấu hình sai | Có thể gặp `IllegalArgumentException`, `ProviderNotFoundException` hoặc `IOException` tùy API/provider | Dùng đúng URI, config, provider và kiểm tra nguyên nhân gốc |

Không viết kiểu “nếu `getFileSystem` thất bại thì `newFileSystem`” tại nhiều nơi dùng chung mà không đồng bộ. Hai thread đều có thể thấy chưa tồn tại rồi cùng cố tạo; một thread có thể nhận `FileSystemAlreadyExistsException`. Hơn nữa, vừa lấy được tham chiếu không bảo đảm chủ sở hữu khác sẽ không đóng nó ngay sau đó. Thiết kế quản lý tài nguyên phải giải quyết cả **quyền sở hữu** lẫn **thời gian sử dụng**.

### Phân biệt các exception theo mục đích

- `FileSystemNotFoundException`: lookup không tìm được file system/provider phù hợp; hay gặp ở `getFileSystem(uri)` khi ZIP chưa mở.
- `FileSystemAlreadyExistsException`: yêu cầu tạo mới trùng file system đã tồn tại theo quy tắc của provider; hay gặp ở `newFileSystem(URI, ...)` cho cùng ZIP đang mở.
- `ClosedFileSystemException`: file system đã đóng nhưng có thao tác cần dùng nó.
- `ProviderNotFoundException`: không có provider phù hợp trong một số đường tạo/mở; khác với việc có provider nhưng chưa có instance.
- `IOException`: lỗi thực sự khi truy cập, mở, ghi, flush hoặc đóng dữ liệu; phải xử lý theo logic I/O và giữ thông tin nguyên nhân.

Đừng bắt rộng `RuntimeException` để che ba nhóm vấn đề khác nhau: **chưa mở**, **đã mở**, **đã đóng**.

## 7. Chọn API theo câu hỏi thực tế

Giả sử chức năng cần đọc một tệp `config.json` nằm trong `settings.zip`:

1. Nếu chức năng tự chịu trách nhiệm mở archive cho một lượt đọc, tạo `FileSystem` bằng `newFileSystem(...)`, đọc dữ liệu bên trong `try` rồi đóng. Hợp đồng method nên trả nội dung hoặc đối tượng dữ liệu, không trả `Path` thuộc ZIP đã đóng.
2. Nếu ứng dụng đã có dịch vụ quản lý ZIP FS lâu dài, chức năng đọc nhận `FileSystem` hoặc dùng `getFileSystem(uri)` theo hợp đồng của dịch vụ. Không tự đóng sau một yêu cầu nhỏ.
3. Nếu cần đường dẫn bình thường trên máy, sử dụng `Path.of(...)`/`Files...` với default FS; không tạo hoặc đóng `FileSystem` mới.
4. Nếu client phải dùng tệp ZIP từ nhiều luồng, chủ sở hữu chỉ đóng sau khi tất cả công việc kết thúc; tham chiếu mượn không phải một “lease” tự bảo vệ trước `close()`.

Hãy xác định ngay tại API boundary: **ai tạo, ai mượn, tài nguyên sống tới khi nào, và ai được đóng**. Tài liệu hợp đồng của một helper nên chỉ rõ điều đó. Nếu method *trả về một `FileSystem` mới cho caller*, hãy nói rõ caller chịu trách nhiệm đóng; nếu method *trả một instance dùng chung*, hãy nói rõ caller không được đóng.

## 8. Những lỗi phổ biến cần tránh

- **Hiểu `getFileSystem` là thao tác mở ZIP**: nó chỉ tra cứu instance đang tồn tại; archive tồn tại trên đĩa chưa đủ.
- **Quên `close()` sau `newFileSystem`**: ZIP có thể còn giữ resource và các thay đổi chưa được hoàn tất đúng vòng đời.
- **Đóng đối tượng mượn**: vô tình đóng cùng instance mà nơi sở hữu và các thread khác đang dùng.
- **Đóng default FS**: `FileSystems.getDefault().close()` không được hỗ trợ.
- **Giữ `Path` sau khi ZIP FS đã đóng**: path không thể dùng cho I/O khi file system nền không còn mở.
- **Nhầm `jar:` với `file:` hoặc path bên trong ZIP**: `jar:file:/.../archive.zip` định danh ZIP FS cho ZipFS; `zipFs.getPath("/entry.txt")` định danh entry trong FS ấy.
- **Tự ghép đường dẫn máy bằng chuỗi cứng**: ưu tiên `Path.toUri()` để giữ đúng escape và dấu phân cách trên hệ điều hành.
- **Dựa vào registry provider như cơ chế quản lý concurrency**: lấy được reference không trao ownership và không ngăn chủ sở hữu khác đóng.
- **Dùng try-with-resources bừa bãi**: chỉ dùng trên `FileSystem` do khối mã sở hữu hoặc được hợp đồng yêu cầu đóng.

## 9. Câu hỏi tự kiểm tra

1. Một method gọi `FileSystems.getFileSystem(uri)` và thấy trả về `FileSystem`. Method đó có được đóng nó không? **Thông thường không**: nó chỉ mượn tham chiếu, quyền đóng phải được hợp đồng xác nhận.
2. ZIP tồn tại trên đĩa nhưng chưa hề được mở bằng ZipFS; `getFileSystem(jarUri)` có đọc được ngay không? **Không**: với ZipFS, chưa có instance nên thường là `FileSystemNotFoundException`.
3. Vì sao trả `Path` trong ZIP ra khỏi khối `try (FileSystem fs = ...)` là nguy hiểm? Vì `Path` tham chiếu đến FS đã đóng; thao tác I/O về sau có thể ném `ClosedFileSystemException`.
4. Lúc nào dùng `try-with-resources` với `FileSystem`? Khi phạm vi mã **sở hữu** file system mới tạo/mở và cần bảo đảm đóng sau khi hoàn thành công việc.
5. Có thể xem `getDefault()` như `newFileSystem(...)` để đóng cuối tác vụ không? **Không**: default FS là tài nguyên nền JVM, thao tác đóng không được hỗ trợ.

## Tài liệu chuẩn để tra cứu

- [Java SE 17 — FileSystems](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileSystems.html): chữ ký overload, provider lookup và các ngoại lệ.
- [Java SE 17 — FileSystem](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileSystem.html): vòng đời, `close()`, `isOpen()`, file system mặc định.
- [Java SE 17 — jdk.zipfs](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.zipfs/module-summary.html): scheme `jar`, cấu hình `create` và ZIP/JAR provider.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-systems-005">ZIP file system: thao tác archive bằng Path và Files</a>

<details>
<summary>Xem chi tiết</summary>

# ZIP file system trong Java: thao tác archive bằng Path và Files

Một tệp `.zip` thường được xem như một archive cần giải nén rồi mới xử lý. Java NIO.2 còn cung cấp **ZIP file system (ZipFS)**: gắn nội dung ZIP vào một `FileSystem`, từ đó đọc, tạo, sao chép, đổi tên và xóa entry bằng chính `Path` và `Files`. Cách này phù hợp khi cần thao tác nhiều tệp bên trong một ZIP mà không muốn tự quản lý từng `ZipEntry`.

## 1. Mục tiêu và mô hình tư duy

Sau bài này, bạn có thể:

- Tạo archive ZIP mới, mở ZIP đã có và đóng tài nguyên đúng cách.
- Phân biệt đường dẫn của hệ điều hành với đường dẫn **bên trong ZIP**.
- Sử dụng `Files.createDirectories`, `writeString`, `readString`, `copy`, `move`, `delete`, `walk` trên ZIP.
- Hiểu ranh giới provider, giới hạn, lỗi thường gặp và rủi ro khi nhận ZIP không tin cậy.
- Chọn ZipFS hoặc API dạng stream/entry tùy loại công việc.

Sơ đồ:

```text
Hệ điều hành                    ZIP file system
/tmp/work/lesson.zip  ─mở─>   /
                               └── docs/
                                   ├── readme.md
                                   └── source.txt
Path của ZIP trên máy           Path của entry trong ZIP
```

`Files` là lớp thao tác theo `Path`; nó không mặc định chỉ làm việc với đĩa. Mỗi `Path` thuộc về một `FileSystem` và `FileSystemProvider`. Khi `Path` trỏ tới entry của ZipFS, thao tác `Files` được chuyển tới ZIP provider của JDK. Nội dung trong archive chưa trở thành các tệp thông thường ở thư mục bên ngoài.

Các kiểu chính:

| Thành phần | Vai trò |
| --- | --- |
| `java.nio.file.Path` | Định danh ZIP trên đĩa, hoặc một entry **bên trong** ZipFS; hai loại Path có provider khác nhau. |
| `java.nio.file.FileSystem` | Ngữ cảnh truy cập các entry của ZIP; cần đóng bằng `close()`. |
| `java.nio.file.FileSystems` | Mở/tạo ZipFS qua `newFileSystem(...)`. |
| `java.nio.file.Files` | Đọc/ghi/liệt kê/thao tác entry như với một filesystem. |
| `jdk.zipfs` | ZIP filesystem provider đi kèm các bản JDK phổ biến; ứng dụng runtime tối giản phải có module này. |

## 2. Chương trình hoàn chỉnh: tạo, duyệt, đọc và chỉnh sửa ZIP

Ví dụ sau độc lập, chạy trên Java 17+. Nó tạo thư mục tạm riêng nên không xóa hoặc ghi đè ZIP của người học. Sau lượt đầu, `try-with-resources` đóng ZipFS để ghi các thay đổi vào archive; lượt sau mở lại file ZIP đó và sửa các entry.

Lưu đúng tên **`ZipFsDemo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.stream.Stream;

public class ZipFsDemo {
    public static void main(String[] args) throws Exception {
        Path workDir = Files.createTempDirectory("zipfs-demo-");
        Path source = workDir.resolve("source.txt");
        Path archive = workDir.resolve("lesson.zip");
        Files.writeString(source, "Tệp nguồn\n", StandardCharsets.UTF_8);

        // Tạo ZIP chưa tồn tại. Bên trong ZIP dùng Path của zipFs.
        try (FileSystem zipFs = FileSystems.newFileSystem(
                archive, Map.of("create", "true"))) {
            Path docs = zipFs.getPath("/docs");
            Files.createDirectories(docs);
            Files.copy(source, docs.resolve("source.txt"));
            Files.writeString(docs.resolve("readme.md"), "# ZIP FS\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);

            try (Stream<Path> entries = Files.walk(zipFs.getPath("/"))) {
                entries.filter(path -> Files.isRegularFile(path))
                        .map(Path::toString)
                        .sorted()
                        .forEach(System.out::println);
            }
        }

        // ZIP đã tồn tại: mở không có create, đọc, đổi tên, xóa entry.
        try (FileSystem zipFs = FileSystems.newFileSystem(archive, Map.of())) {
            Path docs = zipFs.getPath("/docs");
            Path readme = docs.resolve("readme.md");
            System.out.print(Files.readString(readme, StandardCharsets.UTF_8));
            Path guide = docs.resolve("guide.md");
            Files.move(readme, guide);
            Files.delete(docs.resolve("source.txt"));
            System.out.println("guide exists: " + Files.exists(guide));
        }
    }
}
```

Biên dịch và chạy tại thư mục chứa file:

```bash
javac ZipFsDemo.java
java ZipFsDemo
```

Kết quả stdout:

```text
/docs/readme.md
/docs/source.txt
# ZIP FS
guide exists: true
```

Đường dẫn thật của `lesson.zip` thay đổi theo máy, nhưng không được in nên stdout ở trên ổn định. Khi chương trình kết thúc, archive còn `/docs/guide.md`; `/docs/source.txt` đã bị xóa.

**Đọc từng bước:**

1. `Files.createTempDirectory` tạo thư mục vật lý; `archive` là `Path` thuộc filesystem mặc định. `Files.writeString(source, ...)` ghi tệp nguồn *ngoài ZIP*.
2. `FileSystems.newFileSystem(archive, Map.of("create", "true"))` mở một filesystem cho ZIP; tùy chọn `create` cho phép tạo archive nếu chưa có. Nếu file không phải ZIP hợp lệ, mở có thể ném `ZipException`/`IOException`.
3. `zipFs.getPath("/docs")` tạo đường dẫn *bên trong archive*. `Files.createDirectories` tạo thư mục entry; `Files.copy` chép từ file thật sang ZIP; `Files.writeString` ghi thêm một entry.
4. `Files.walk` trả về `Stream<Path>`, phải đóng bằng `try-with-resources`. Lọc file thường để không in `/` và `/docs`; sắp xếp để output ổn định.
5. Đóng `zipFs` là bước cần thiết để giải phóng tài nguyên và hoàn tất ghi; sau đó mở lại ZIP ở chế độ hiện có. `Files.move` đổi tên entry, `Files.delete` xóa entry. Các thay đổi được ghi lại khi đóng.

## 3. Hai không gian Path: điểm dễ nhầm nhất

```java
Path zipOnDisk = Path.of("backup.zip");       // Path trên OS
// Trong một khối đã mở: FileSystem zipFs = ...
Path fileInZip = zipFs.getPath("/docs/a.txt"); // Path thuộc ZipFS
```

Hai dòng chỉ minh họa sự khác biệt; `zipFs` phải được khởi tạo trong mã thực tế. `zipOnDisk.resolve("docs/a.txt")` **không** trỏ vào archive: nó biểu diễn một đường dẫn filesystem ngoài ZIP. Ngược lại, `zipFs.getPath("/docs/a.txt")` trỏ đúng entry khi ZipFS đang mở.

Có thể dùng `zipFs.getPath("/docs").resolve("a.txt")` để tạo đường dẫn con. Dấu `/` trong các ví dụ là cú pháp đường dẫn ZIP; không nên xây đường dẫn entry bằng cách nối chuỗi separator của hệ điều hành. Chỉ dùng `Files` cho những `Path` còn hiệu lực và thuộc đúng provider.

Các thao tác sau đều có ý nghĩa rõ ràng khi cả hai `Path` đã được khai báo:

| Nhu cầu | Gọi API | Ý nghĩa |
| --- | --- | --- |
| Ngoài ZIP → trong ZIP | `Files.copy(hostFile, zipEntry)` | Đưa file vào archive. |
| Trong ZIP → ngoài ZIP | `Files.copy(zipEntry, hostFile)` | Trích **một** entry ra thư mục ngoài. |
| Trong ZIP → trong ZIP | `Files.copy(entryA, entryB)` | Nhân bản một entry trong filesystem phù hợp. |
| Đổi tên trong ZIP | `Files.move(oldEntry, newEntry)` | Di chuyển/đổi tên entry. |
| Xóa entry | `Files.delete(entry)` | Xóa entry; thư mục phải trống nếu xóa thư mục. |

Nếu đích đã tồn tại, `Files.copy` thường ném `FileAlreadyExistsException`, trừ khi cung cấp `StandardCopyOption.REPLACE_EXISTING` và provider chấp nhận. Tránh dùng `Files.move` xuyên hai provider như một giả định về thao tác nguyên tử; chuyển file giữa các filesystem có thể phải sao chép rồi xóa, có thể thất bại giữa chừng.

## 4. Mở ZIP đúng cách và quản lý vòng đời

**Tạo archive mới:**

```java
try (FileSystem fs = FileSystems.newFileSystem(
        archivePath, Map.of("create", "true"))) {
    Files.createDirectories(fs.getPath("/logs"));
    Files.writeString(fs.getPath("/logs/result.txt"), "OK\n");
}
```

Đây là đoạn minh họa dùng biến `archivePath` đã được khai báo là `Path` trên đĩa. Trong API ZipFS của JDK, tùy chọn `create` nhận chuỗi `"true"`; nếu ZIP đã có, lời gọi cũng mở ZIP đó. Nếu yêu cầu **chỉ tạo file mới**, kiểm tra/điều phối việc đặt tên bên ngoài trước và xử lý tranh chấp đồng thời; một phép kiểm tra `Files.exists` đơn lẻ không phải khóa.

**Mở archive đang có:**

```java
try (FileSystem fs = FileSystems.newFileSystem(archivePath, Map.of())) {
    Path entry = fs.getPath("/logs/result.txt");
    if (Files.isRegularFile(entry)) {
        String content = Files.readString(entry, StandardCharsets.UTF_8);
        System.out.println(content);
    }
}
```

Hai đoạn này là snippet có biến `archivePath` từ ngữ cảnh, không phải chương trình độc lập. Một ZIP chỉ đọc có thể mở với `Map.of("readonly", "true")` nếu provider và phiên bản JDK hỗ trợ tùy chọn đó; đừng coi quyền filesystem ngoài ZIP hay mọi environment key là khả chuyển giữa các provider. Nếu cần chỉ đọc một cách đơn giản, tránh gọi bất kỳ API ghi nào và đảm bảo file ZIP ở ngoài được bảo vệ theo quyền OS.

Một số API thay thế:

- `FileSystems.newFileSystem(Path, Map<String, ?>)`: dùng `Path` của file archive, tiện cho công việc thông thường.
- `FileSystems.newFileSystem(URI, Map<String, ?>)`: thường dùng URI có scheme `jar:`, ví dụ `URI.create("jar:" + archivePath.toUri())`. Cần phân biệt URI của archive với `Path` entry và với URI của filesystem khác.
- `FileSystems.getFileSystem(URI)`: tra filesystem đã được đăng ký theo URI, không tạo mới. Với biến thể `newFileSystem(URI, ...)`, mở trùng một filesystem còn tồn tại có thể ném `FileSystemAlreadyExistsException`.

**Quy tắc tài nguyên:** giữ mọi thao tác với entry bên trong `try (FileSystem fs = ...)`. Sau khi `fs.close()`, sử dụng `Path` gắn với fs đó có thể ném `ClosedFileSystemException`. Không mở/đóng nhiều lần cho từng entry; xử lý một nhóm tác vụ trong một phiên ZipFS, đóng sau khi hoàn tất.

## 5. Liệt kê, tìm, đọc và ghi các entry

Khi đã có `FileSystem fs` đang mở:

```java
Path root = fs.getPath("/");
try (Stream<Path> stream = Files.walk(root)) {
    stream.filter(Files::isRegularFile)
          .map(root::relativize)
          .forEach(System.out::println);
}
```

`Files.walk` duyệt đệ quy và có thể tốn tài nguyên trên ZIP lớn; dùng `Files.list(fs.getPath("/docs"))` khi chỉ cần một cấp. Cả hai trả về stream cần đóng. Thứ tự duyệt không được đảm bảo, hãy `sorted()` nếu kiểm thử đòi output cố định. `root.relativize(path)` đổi `/docs/a.txt` thành `docs/a.txt` trong cùng filesystem; không trộn `Path` từ hai provider ở các phép `relativize`/`resolve` cần cùng ngữ cảnh.

Với file văn bản nhỏ, `Files.readString(entry, StandardCharsets.UTF_8)` và `Files.writeString(...)` thuận tiện. Dùng `Files.newBufferedReader`, `newBufferedWriter` hoặc stream cho dữ liệu lớn để không giữ toàn bộ entry trong RAM. Cần có parent directory trước khi ghi: `Files.createDirectories(entry.getParent())`. Các option ghi phổ biến:

- `CREATE_NEW`: chỉ tạo nếu entry chưa tồn tại; hữu ích để tránh vô tình thay file.
- `CREATE`: tạo nếu chưa có, nhưng có thể mở file hiện có.
- `TRUNCATE_EXISTING`: xóa nội dung cũ khi mở ghi nếu entry tồn tại.
- `APPEND`: chỉ dùng khi provider và ngữ nghĩa file cho phép; ZIP không tối ưu cho log append thường xuyên.

Khi xử lý byte không phải text, ưu tiên `Files.newInputStream`/`newOutputStream`, `Files.copy` hoặc `Files.readAllBytes` cho payload nhỏ; tránh `readString` đối với ảnh, class, PDF hay dữ liệu nhị phân.

## 6. Hành vi ghi và giới hạn cần hiểu

Một archive là một tệp container. Chỉnh sửa một entry **không tương đương** ghi từng byte trực tiếp vào một thư mục OS: provider có thể tổ chức lại và viết lại dữ liệu archive khi đóng, nên thao tác ghi hàng loạt, kích thước lớn hoặc mở/đóng liên tục có thể đắt. Đừng mặc định đổi tên, sao chép hay ghi entry có cùng chi phí và tính nguyên tử như filesystem vật lý.

Với dự án thực tế:

1. Mở ZipFS một lần cho một batch, thao tác trên `Path` của chính filesystem đó, đóng một lần; tránh xử lý từng file bằng cách mở/đóng ZIP riêng.
2. Luôn quyết định rõ xử lý trùng tên (giữ nguyên, từ chối hay `REPLACE_EXISTING`) và bọc các lỗi `IOException`.
3. Không giả định `StandardCopyOption.ATOMIC_MOVE` hoạt động với ZIP. Không coi cập nhật archive là transaction: nếu tiến trình bị dừng trong lúc sửa, file có thể không đạt trạng thái kỳ vọng.
4. Với dữ liệu quan trọng, làm việc trên bản ZIP tạm trong **cùng filesystem đích trên OS**, kiểm tra kết quả rồi thay archive đích bằng chiến lược phù hợp; lưu bản sao lưu và chuẩn bị xử lý lỗi. Việc thay file trên OS có thể được yêu cầu nguyên tử nhưng còn tùy provider/file store.
5. Tránh nhiều writer cùng sửa một ZIP. Đồng bộ hóa ở mức ứng dụng hoặc dùng quy trình single-writer; khóa filesystem và cache ZipFS không thay thế chiến lược phối hợp.

ZipFS không phải hệ thống file POSIX hoàn chỉnh. Quyền truy cập, symbolic link, file attributes, rename và các option nâng cao có thể khác, thiếu hoặc không được hỗ trợ. `Files.setPosixFilePermissions` và `WatchService` chẳng hạn không nên được giả định hoạt động. ZipFS cũng không phải API để tự động mã hóa/mở ZIP được bảo vệ bằng mật khẩu.

**Hiệu năng:** phù hợp với số entry vừa phải và nhu cầu truy cập ngẫu nhiên nhiều entry; với file lớn cần stream để giới hạn RAM. Đánh giá kích thước tổng, số entry, tần suất ghi và thời gian đóng filesystem. Đừng đọc toàn bộ archive thành mảng byte nếu chưa chắc giới hạn dung lượng.

## 7. Đọc ZIP không tin cậy và giải nén an toàn

Bài này chủ yếu thao tác *trong* ZipFS, nhưng khi copy entry ra một thư mục OS phải đặc biệt chú ý:

- **ZIP Slip/path traversal:** một tên entry do bên ngoài cung cấp có thể chứa `../` hoặc đường dẫn tuyệt đối. Chuẩn hóa đường dẫn đích và bảo đảm đích vẫn nằm trong thư mục giải nén cho phép. Không nối nguyên tên entry vào đường dẫn host rồi ghi ngay.
- **ZIP bomb:** archive nhỏ có thể giải nén thành lượng dữ liệu rất lớn, chứa nhiều entry hoặc đường dẫn lồng sâu. Giới hạn số entry, kích thước sau giải nén, thời gian và dung lượng; giới hạn tổng thể phải được kiểm tra trong lúc streaming, không chỉ tin metadata.
- **Entry bất thường:** chuẩn hóa tên, xử lý separator, tên trùng nhau, tên được mã hóa, thư mục rỗng, loại file/attribute không được hỗ trợ, và mọi `IOException`.
- **Nguồn không tin cậy:** không chạy chương trình, script hay nạp class vừa lấy từ ZIP; chỉ xử lý như dữ liệu.

Phác thảo kiểm tra đường dẫn đích khi trích entry (đoạn này sử dụng `entry` là Path của ZipFS, `destinationRoot` là thư mục OS đã chuẩn hóa):

```java
Path root = destinationRoot.toAbsolutePath().normalize();
Path relative = entry.isAbsolute()
        ? entry.getRoot().relativize(entry)
        : entry;
Path dest = root.resolve(relative.toString()).normalize();
if (!dest.startsWith(root) || dest.equals(root)) {
    throw new SecurityException("Entry path ngoài thư mục đích");
}
```

Đây chỉ là **một lớp kiểm tra cú pháp đường dẫn**, chưa phải trình giải nén an toàn hoàn chỉnh. Với dữ liệu độc hại, cần chính sách rõ về symbolic link đã tồn tại ở thư mục đích, race giữa kiểm tra/ghi, quota, và file đặc biệt; cân nhắc `SecureDirectoryStream` ở nơi hỗ trợ và môi trường giải nén cô lập. Đừng dựa duy nhất vào `normalize()` để bảo vệ trước link hay thay đổi filesystem đồng thời.

## 8. Lỗi thường gặp và cách xử lý

| Hiện tượng | Nguyên nhân thường gặp | Hướng xử lý |
| --- | --- | --- |
| `NoSuchFileException` | ZIP chưa tồn tại hoặc entry/parent thiếu. | Xác minh archive; tạo mới bằng `create=true` khi đúng mục tiêu; tạo parent trong ZIP trước. |
| `FileAlreadyExistsException` | Tạo/copy vào entry đã có. | Chọn `CREATE_NEW` hay `REPLACE_EXISTING` theo chính sách. |
| `ProviderNotFoundException` | Runtime thiếu ZIP provider, ví dụ custom `jlink` image thiếu `jdk.zipfs`. | Kiểm tra image/module runtime và thêm `jdk.zipfs` khi đóng gói. |
| `FileSystemAlreadyExistsException` | Mở lại cùng URI `jar:` khi FileSystem đó còn được đăng ký. | Dùng lại filesystem hiện có hoặc quản lý vòng đời; không mở trùng URI tùy tiện. |
| `ClosedFileSystemException` | Đọc/ghi entry sau khi đóng `FileSystem`. | Giữ toàn bộ xử lý entry trong khối `try-with-resources`. |
| `ProviderMismatchException` | Trộn Path khác provider trong thao tác yêu cầu cùng hệ. | Xác định rõ Path OS và Path ZipFS; dùng thao tác copy xuyên provider khi hỗ trợ. |
| `ZipException`/`IOException` | Archive hỏng, dữ liệu không phải ZIP, quyền hạn hoặc lỗi I/O. | Xác thực đầu vào, báo lỗi có ngữ cảnh, đóng resources, không tiếp tục với archive hỏng. |
| Không thấy thay đổi ngay trong file ngoài | FileSystem chưa được đóng hoặc ứng dụng còn giữ luồng ghi. | Đóng stream và `FileSystem` trước khi dùng archive ở quy trình khác. |

Lỗi và thời điểm ném phụ thuộc thao tác, provider và hệ điều hành; bảng trên là các tình huống điển hình, không phải ánh xạ 1–1 bắt buộc.

## 9. Khi nào dùng ZipFS, ZipFile hay ZipInputStream?

| Công cụ | Ưu điểm chính | Nên chọn khi |
| --- | --- | --- |
| **ZipFS + `Path`/`Files`** | API thống nhất để đọc/ghi/copy/đổi tên/xóa nhiều entry. | Cần xử lý ZIP như cây thư mục và cập nhật archive. |
| `java.util.zip.ZipFile` | Tra cứu và đọc từng entry trong ZIP có sẵn. | Chủ yếu đọc, muốn duyệt entry và lấy `InputStream`. |
| `ZipInputStream` | Duyệt entry nối tiếp từ một luồng dữ liệu. | Đọc stream ZIP mà không cần filesystem hoặc truy cập ngẫu nhiên. |
| `ZipOutputStream` | Ghi ZIP tuần tự với `ZipEntry`. | Tự tạo archive theo luồng, cần kiểm soát thứ tự/metadata entry. |

Không có một API tốt nhất cho mọi trường hợp. ZipFS hữu ích nhất khi thao tác đa dạng thông qua cùng giao diện NIO. Nếu chỉ trích lần lượt từng entry từ network stream, các lớp `java.util.zip` thường đơn giản hơn. ZIP chuẩn sử dụng nén theo entry; ZIP không cung cấp đầy đủ semantics filesystem như cơ sở dữ liệu hoặc giao dịch.

## 10. Checklist sử dụng và bài tập tự luyện

Trước khi triển khai: xác nhận archive nằm ở đâu, entry thuộc provider nào, ZIP tạo mới hay đang tồn tại, chính sách ghi đè, encoding văn bản, giới hạn kích thước, bảo mật đường dẫn, quản lý `close()` và chiến lược xử lý lỗi.

**Bài tập 1 — Thêm file vào ZIP.** Dựa trên chương trình mẫu, tạo `/images/info.txt` bằng `Files.createDirectories` và `Files.writeString` rồi dùng `Files.walk` chứng minh entry đã xuất hiện. Hãy thử cố tình ghi `CREATE_NEW` lần thứ hai để quan sát lỗi trùng tên.

**Bài tập 2 — Sao chép ra ngoài.** Mở ZIP đã có, chọn `/docs/guide.md` và dùng `Files.copy` ra file mới nằm dưới thư mục `workDir`. Kiểm tra nội dung ngoài ZIP bằng `Files.readString`. Đảm bảo đường dẫn ngoài ZIP được tạo từ `workDir` chứ không phải `zipFs.getPath`.

**Bài tập 3 — Kiểm tra vòng đời và tính toàn vẹn.** Mở ZIP, cập nhật hai entry, đóng ZipFS, mở lại để so sánh nội dung. Sau đó thử truy cập một `Path` cũ sau `close()` và giải thích vì sao phải tạo lại `Path` từ filesystem mới.

**Ghi nhớ:** `archivePath` trỏ tới *container ZIP trên OS*; `zipFs.getPath("/...")` trỏ tới *entry bên trong container*. Dùng `Files` trên đúng `Path`, gom thao tác vào một vòng đời `FileSystem`, đóng tài nguyên và coi dữ liệu ZIP từ bên ngoài là đầu vào không tin cậy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-systems-006">Root directories, FileStore và các đặc điểm provider</a>

<details>
<summary>Xem chi tiết</summary>

# Root directories, `FileStore` và đặc điểm của `FileSystemProvider` trong Java NIO.2

## 1. Mục tiêu và bức tranh tổng thể

Khi làm việc với hệ thống tệp, chương trình không chỉ cần biết một `Path` trỏ tới tệp nào. Nó còn có thể cần biết những vùng gốc (root) mà một `FileSystem` cung cấp, vùng lưu trữ chứa một đường dẫn, không gian đĩa có thể sử dụng, khả năng hỗ trợ thuộc tính POSIX/DOS, và nhà cung cấp (provider) quyết định cách các thao tác NIO.2 hoạt động.

Sau bài này, bạn có thể:

- Phân biệt **root directory**, **`FileStore`** và **`FileSystemProvider`**; tránh giả định mỗi root tương ứng chính xác một ổ đĩa vật lý.
- Duyệt các root và kho lưu trữ, chọn đúng `FileStore` cho một `Path` đang tồn tại.
- Đọc dung lượng, trạng thái chỉ đọc và khả năng hỗ trợ các file attribute view một cách có kiểm soát lỗi.
- Nhận ra giới hạn phụ thuộc hệ điều hành/provider và làm việc với một hệ thống tệp ZIP bằng provider khác.

Các kiểu liên quan thuộc `java.nio.file` và `java.nio.file.spi` trong Java NIO.2:

```text
FileSystemProvider (cách triển khai thao tác theo scheme)
        │ tạo/cung cấp
        ▼
FileSystem (không gian tên, separator, các root, các FileStore)
        │ tạo/cung cấp
        ├── Path (đường dẫn bên trong FileSystem)
        └── FileStore (thông tin kho lưu trữ mà provider nhìn thấy)
```

Đây là sơ đồ quan hệ API, **không** phải sơ đồ phân vùng vật lý: một `FileSystem` có thể có nhiều root hoặc nhiều `FileStore`; hai khái niệm này không có quan hệ một-một bắt buộc.

## 2. Root directory: gốc của không gian đường dẫn

**Root directory** là thành phần gốc của đường dẫn tuyệt đối trong một `FileSystem`. Dùng `FileSystem.getRootDirectories()` để lấy `Iterable<Path>` biểu diễn các root do provider liệt kê.

```java
FileSystem fs = FileSystems.getDefault();
for (Path root : fs.getRootDirectories()) {
    System.out.println(root);
}
```

Trên môi trường Unix thường có root `/`; trên Windows có thể thấy `C:\`, `D:\` hoặc các root khác. Đây chỉ là ví dụ phụ thuộc máy đang chạy. Thứ tự, số lượng, cách biểu diễn và quyền truy cập không cố định; không hard-code danh sách root.

Phân biệt:

| Khái niệm | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| `Path.getRoot()` | Phần gốc của **một đường dẫn**; có thể `null` với đường dẫn tương đối | `/` trong `/var/log/app.log` |
| `FileSystem.getRootDirectories()` | Các root mà **provider liệt kê** cho hệ thống tệp | `/` hoặc các ký tự ổ đĩa |
| Mount point | Vị trí một filesystem/volume được gắn vào cây thư mục của OS | `/mnt/data` có thể là volume riêng |
| `FileStore` | Đại diện cho kho lưu trữ và metadata của nó theo provider | volume, partition, mount hoặc đơn vị lưu trữ logic |

Điểm dễ nhầm: trên Linux, một volume riêng gắn tại `/mnt/data` **không nhất thiết** xuất hiện như root thứ hai trong `getRootDirectories()`; ứng dụng vẫn truy cập volume qua `/mnt/data`. Ngược lại, một root không bảo đảm toàn bộ nhánh bên dưới nằm trên cùng `FileStore` do mount point có thể xuất hiện sâu trong cây.

`Path.of(".")` là đường dẫn tương đối; `Path.of(".").toAbsolutePath().normalize()` cho đường dẫn tuyệt đối chuẩn hóa về mặt cú pháp. `normalize()` không tự kiểm tra sự tồn tại của tệp, không phân giải symbolic link và không chứng minh đường dẫn có thể truy cập. Khi cần đường dẫn thực trên đĩa và xác minh tồn tại, xem `toRealPath()` và xử lý `IOException`.

## 3. `FileStore`: kho lưu trữ và metadata

`FileStore` cung cấp thông tin về nơi provider đặt tệp và khả năng của đơn vị lưu trữ đó. Hai cách truy cập hay dùng:

- `Files.getFileStore(existingPath)`: tìm kho lưu trữ cho **đường dẫn cụ thể**. Thích hợp nhất khi cần xác định vị trí ghi của tệp/danh mục làm việc. Đường dẫn nên tồn tại và quyền truy cập phải phù hợp; lời gọi có thể ném `IOException`.
- `FileSystem.getFileStores()`: duyệt các `FileStore` mà hệ thống tệp/provider có thể liệt kê. Tập kết quả và mức độ hiển thị phụ thuộc môi trường, quyền và provider; danh sách không phải bản đồ đầy đủ, bất biến của phần cứng.

Các phương thức chính:

| API | Điều trả về | Diễn giải và giới hạn |
| --- | --- | --- |
| `name()`, `type()` | Tên/loại do provider báo | Có thể là chuỗi rỗng hoặc khác nhau giữa OS; không dùng làm định danh vĩnh viễn |
| `isReadOnly()` | Trạng thái chỉ đọc của store | Không thay thế kiểm tra quyền truy cập đối với một đường dẫn |
| `getTotalSpace()` | Tổng dung lượng (byte) | Dung lượng logic provider báo, không nhất thiết tương ứng dung lượng thiết bị vật lý |
| `getUnallocatedSpace()` | Số byte chưa cấp phát | Có thể bao gồm phần mà tài khoản hiện tại không được phép sử dụng |
| `getUsableSpace()` | Số byte ước tính có thể dùng bởi JVM hiện tại | Có thể bị hạn chế bởi quota, chính sách hoặc filesystem |
| `supportsFileAttributeView(String/Class)` | Store tuyên bố hỗ trợ một view thuộc tính | Cần kiểm tra thêm theo path/thao tác cụ thể nếu ứng dụng phụ thuộc khả năng đó |
| `getAttribute(name)` | Metadata mở rộng theo tên | Không bảo đảm có mọi thuộc tính; có thể không được hỗ trợ |

Mọi đại lượng không gian có đơn vị **byte**, kiểu `long`, và là một phép quan sát tại thời điểm gọi. Giá trị có thể thay đổi ngay sau đó; quota, filesystem ảo, storage mạng, container và đặc quyền của process có thể ảnh hưởng kết quả. Đặc biệt, `usableSpace` khác với `unallocatedSpace`: một phần dung lượng chưa cấp phát có thể dành riêng cho hệ thống hoặc người dùng đặc quyền.

**Không dùng `getUsableSpace()` làm cam kết rằng một thao tác ghi chắc chắn thành công.** Khoảng trống có thể thay đổi; còn có quota, inode, quyền, network failure, trạng thái mount và các hạn chế provider. Thao tác ghi thực tế phải bắt và xử lý lỗi.

## 4. Ví dụ chạy được: root, provider và `FileStore` của thư mục hiện hành

Lưu mã dưới đây thành **`StorageOverview.java`**. Chương trình dùng Java 17+, không tạo, sửa hoặc xóa tệp; nó chỉ quan sát metadata. Lệnh có thể thất bại nếu hệ thống từ chối truy cập metadata, nên ví dụ cho phép ném `IOException` để hiển thị nguyên nhân cho người chạy.

```java
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

public class StorageOverview {
    public static void main(String[] args) throws IOException {
        FileSystem fs = FileSystems.getDefault();

        System.out.println("Provider scheme: " + fs.provider().getScheme());
        System.out.println("Separator: " + fs.getSeparator());
        System.out.println("Supported views: " + fs.supportedFileAttributeViews());

        for (Path root : fs.getRootDirectories()) {
            System.out.println("Root: " + root);
        }

        Path current = Path.of(".").toAbsolutePath().normalize();
        FileStore store = Files.getFileStore(current);

        System.out.println("Current: " + current);
        System.out.println("Store: " + store.name() + " / " + store.type());
        System.out.println("Read only: " + store.isReadOnly());
        System.out.println("Basic view: "
                + store.supportsFileAttributeView("basic"));
        System.out.println("Total bytes: " + store.getTotalSpace());
        System.out.println("Unallocated bytes: " + store.getUnallocatedSpace());
        System.out.println("Usable bytes: " + store.getUsableSpace());
    }
}
```

Biên dịch/chạy tại thư mục chứa tệp:

```bash
javac StorageOverview.java
java StorageOverview
```

Đầu ra có cấu trúc `Provider scheme: ...`, `Root: ...`, `Store: ...` và các dung lượng. **Không có stdout cố định** cho ví dụ này: đường dẫn, loại filesystem, provider, số lượng root và dung lượng thực tế đều do máy đang chạy quyết định. Một JVM thông thường dùng provider mặc định với scheme `file`, nhưng mã không cần giả định tên volume, có hay không view `posix` hoặc dung lượng cụ thể.

Vì sao gọi `Files.getFileStore(current)` sau khi chọn `current`? Bởi root `/` có thể chứa nhiều mount point con; lấy store của đúng thư mục đích đáng tin cậy hơn là đoán từ root đầu tiên.

Khi vận hành, nên giữ `IOException` trong ngữ cảnh (đường dẫn nào, phép đọc metadata nào), và không để một volume tạm thời mất kết nối làm hỏng toàn bộ màn hình thống kê nếu còn những volume khác đọc được.

## 5. Liệt kê các `FileStore` một cách an toàn

Để tạo trang chẩn đoán, bạn có thể duyệt `fs.getFileStores()`. Mỗi store có thể độc lập trả lỗi khi lấy dung lượng, vì vậy nên giới hạn vùng xử lý lỗi cho từng store:

```java
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;

public class ListStores {
    public static void main(String[] args) {
        for (FileStore store : FileSystems.getDefault().getFileStores()) {
            try {
                System.out.printf(
                        "name=%s, type=%s, readOnly=%s, usable=%d bytes%n",
                        store.name(),
                        store.type(),
                        store.isReadOnly(),
                        store.getUsableSpace()
                );
            } catch (IOException | SecurityException ex) {
                System.err.println("Cannot inspect store " + store.name()
                        + ": " + ex.getMessage());
            }
        }
    }
}
```

Lưu là **`ListStores.java`**, rồi:

```bash
javac ListStores.java
java ListStores
```

Các dòng thống kê và thứ tự hoàn toàn phụ thuộc môi trường; với một số provider, danh sách có thể trống hoặc một số store không truy cập được. Đừng suy luận rằng số dòng là số ổ đĩa vật lý. Cũng không nên lưu lâu một `FileStore` rồi cho rằng metadata vẫn ổn định khi filesystem có thể bị tháo/gắn hoặc thay đổi quota.

## 6. Provider: ai thực sự triển khai thao tác tệp?

`FileSystemProvider` là SPI của NIO.2, định nghĩa cách truy cập một kiểu filesystem. Mỗi provider gắn với một **scheme** URI, ví dụ:

- `file`: provider mặc định của hệ điều hành, thường phục vụ đường dẫn trên máy.
- `jar`: ZIP filesystem provider (`jdk.zipfs` trong JDK chuẩn), cho phép xem nội dung một tệp ZIP/JAR như cây `Path`.
- Provider của bên thứ ba có thể đại diện filesystem mạng, tệp trong bộ nhớ hoặc storage tùy biến; cần kiểm tra tài liệu provider vì tính năng không đồng nhất.

Quan hệ thực tế:

1. `FileSystems.getDefault()` trả filesystem mặc định; `fs.provider()` trả provider của nó.
2. `Path` luôn gắn với một `FileSystem`: `path.getFileSystem()`. Hai `Path` từ hai provider có thể mang cú pháp khác nhau và không phải lúc nào dùng chung được.
3. `Files.readString`, `Files.copy`, `Files.getFileStore`... làm việc thông qua provider tương ứng với đường dẫn. Khi kết hợp đường dẫn từ các provider khác nhau, API có thể thực hiện truyền byte liên provider ở một số thao tác, nhưng những thao tác khác sẽ từ chối hoặc có ngữ nghĩa riêng. Không giả định việc di chuyển, quyền hoặc atomicity tương đương trên mọi provider.
4. `FileSystem` quyết định `getSeparator()`, `getRootDirectories()`, `getFileStores()`, `supportedFileAttributeViews()`. Cùng một ứng dụng có thể sử dụng nhiều filesystem/provider.

**Scheme URI không phải tên hệ thống tệp vật lý.** `file` hay `jar` nói đến cơ chế provider xử lý đường dẫn; `FileStore.type()` có thể trả dạng filesystem như NTFS, ext4 hoặc tên khác, và không có định dạng portable cố định.

Có thể liệt kê provider có sẵn bằng `FileSystemProvider.installedProviders()`; kết quả phụ thuộc JDK và classpath/module-path. Không giả định rằng mọi runtime Java đều bao gồm provider ZIP, nhất là runtime image tùy biến đã loại `jdk.zipfs`.

## 7. Ví dụ chạy được: provider `jar` với filesystem ZIP

Ví dụ sau tạo một ZIP tạm trong thư mục tạm của OS, ghi một tệp văn bản vào ZIP qua `Path` của ZIP filesystem, đọc lại, đóng filesystem rồi xóa ZIP. Chạy trên **JDK 17+ có module `jdk.zipfs`**.

Lưu thành **`ZipProviderDemo.java`**:

```java
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class ZipProviderDemo {
    public static void main(String[] args) throws IOException {
        Path archive = Files.createTempFile("nio-provider-", ".zip");
        try {
            // File ZIP mới do createTempFile tạo đang rỗng:
            // xóa placeholder để ZipFileSystem tạo archive hợp lệ.
            Files.delete(archive);
            URI zipUri = URI.create("jar:" + archive.toUri());

            try (FileSystem zipFs = FileSystems.newFileSystem(
                    zipUri, Map.of("create", "true"))) {
                Path entry = zipFs.getPath("/docs/message.txt");
                Files.createDirectories(entry.getParent());
                Files.writeString(entry, "Xin chào ZIP",
                        StandardCharsets.UTF_8);

                System.out.println(Files.readString(
                        entry, StandardCharsets.UTF_8));
                System.out.println(zipFs.provider().getScheme());
            }
        } finally {
            Files.deleteIfExists(archive);
        }
    }
}
```

```bash
javac ZipProviderDemo.java
java ZipProviderDemo
```

Stdout khi chạy thành công:

```text
Xin chào ZIP
jar
```

Giải thích:

- `archive` là `Path` của filesystem mặc định, còn `entry` là `Path` nằm **bên trong ZIP filesystem**. Cả hai là `Path` nhưng có `getFileSystem()` khác nhau.
- URI dạng `jar:file:...` chọn provider `jar`. Môi trường `Map.of("create", "true")` yêu cầu tạo ZIP nếu chưa tồn tại.
- `zipFs.getPath("/docs/message.txt")` mang ý nghĩa đường dẫn bên trong archive; ký tự `/` ở đây là separator của ZIP filesystem, kể cả khi máy chủ chạy Windows.
- `try-with-resources` đóng ZIP filesystem, hoàn tất việc ghi và giải phóng tài nguyên. Đường dẫn `entry` không còn dùng được bình thường sau khi filesystem của nó đóng (`ClosedFileSystemException` có thể phát sinh).
- `finally` xóa tệp ZIP tạm; nên tránh phụ thuộc vào ZIP file còn tồn tại sau ví dụ.

Trong runtime thiếu `jdk.zipfs`, mở scheme `jar` có thể ném `ProviderNotFoundException`. Trên máy cấm tạo/xóa tệp tạm, chương trình có thể ném `IOException`. Ví dụ không tuyên bố ZIP provider có cùng dung lượng, file attribute view hoặc khả năng ghi nguyên tử với filesystem mặc định.

## 8. Khả năng hỗ trợ thuộc tính: kiểm tra ở đúng tầng

NIO.2 cung cấp những **file attribute view** như `basic`, `dos`, `posix`, `acl`, `owner` và `user`. Hỗ trợ phụ thuộc provider, filesystem, `FileStore`, hệ điều hành và có thể phụ thuộc đường dẫn.

Ba câu hỏi khác nhau:

| Câu hỏi | API kiểm tra | Lưu ý |
| --- | --- | --- |
| Filesystem này thông báo hỗ trợ những tên view nào? | `fs.supportedFileAttributeViews()` | Danh sách ở tầng `FileSystem`; không khẳng định mọi đường dẫn dùng được |
| Store cụ thể thông báo hỗ trợ view này không? | `store.supportsFileAttributeView("posix")` | Nên dùng cùng `FileStore` của path đích |
| Path cụ thể có thể truy xuất/sửa thuộc tính không? | `Files.getFileAttributeView(path, PosixFileAttributeView.class)` và thao tác đọc/ghi tương ứng | Có thể trả `null` hoặc ném lỗi; quyền còn phụ thuộc người chạy |

Ví dụ ngắn, giả sử `path` là `Path` hợp lệ đang tồn tại:

```java
FileSystem fs = path.getFileSystem();
FileStore store = Files.getFileStore(path);

if (fs.supportedFileAttributeViews().contains("posix")
        && store.supportsFileAttributeView("posix")) {
    // Đây mới là kiểm tra khả năng, chưa phải bảo đảm có quyền đọc/ghi.
    var view = Files.getFileAttributeView(
            path, java.nio.file.attribute.PosixFileAttributeView.class);
    if (view != null) {
        var attrs = view.readAttributes(); // vẫn có thể ném IOException
        System.out.println(attrs.permissions());
    }
}
```

Đoạn này **minh họa**, không phải chương trình độc lập: cần khai báo `path`, import các API dùng tới, và phương thức bao quanh xử lý `IOException`. Không nên ép tất cả `Path` phải có POSIX permissions trên Windows hoặc trên ZIP provider. Với yêu cầu portable, nên dùng `BasicFileAttributes` / `Files.readAttributes(path, BasicFileAttributes.class)` cho thông tin cơ bản, và chỉ dùng các view nâng cao sau khi xác nhận hỗ trợ.

`FileStore.getAttribute(String)` cũng mang tính provider-specific. Nếu hỏi thuộc tính không được nhận diện, có thể nhận `UnsupportedOperationException`; truy xuất còn có thể thất bại bằng `IOException`. Đọc tài liệu provider trước khi phụ thuộc vào tên thuộc tính ngoài API chung.

## 9. Chọn API theo tình huống thực tế

**Tình huống A — hiển thị các vùng gốc để người dùng chọn thư mục.** Dùng `FileSystems.getDefault().getRootDirectories()` để gợi ý các điểm bắt đầu. Đừng giả định danh sách đầy đủ mọi mount point; hãy cho phép người dùng chọn hoặc nhập một `Path` hợp lệ ở nơi khác.

**Tình huống B — dự tính dung lượng để xuất báo cáo.** Chọn **thư mục thực sự sẽ chứa output**, tạo thư mục đích nếu nghiệp vụ cho phép, rồi gọi `Files.getFileStore(destinationDirectory)` và `getUsableSpace()`. Không kiểm tra ổ theo ký tự đầu của đường dẫn hoặc lấy đại root `/`. Kết quả chỉ để cảnh báo/sắp xếp lựa chọn; khi ghi vẫn cần xử lý lỗi và dọn dẹp dữ liệu ghi dở.

**Tình huống C — chạy trên Linux, Windows và ZIP filesystem.** Lấy `Path`, `FileSystem` và provider ngay từ đối tượng đang dùng; kiểm tra `supportedFileAttributeViews()` / `supportsFileAttributeView()` trước khi dùng POSIX/DOS. Đừng xây path con bằng cách tự nối separator của host nếu path thuộc ZIP/provider khác: dùng `path.resolve("child")`.

**Tình huống D — xử lý thư mục trên volume mạng hoặc removable media.** Một store có thể biến mất hoặc ngừng phản hồi giữa hai lời gọi. Bọc `Files.getFileStore`, truy xuất dung lượng, liệt kê thư mục và thao tác file trong xử lý `IOException` phù hợp; không coi kết quả chẩn đoán trước đó là snapshot bất biến.

## 10. Lỗi phổ biến và giới hạn cần nhớ

1. **Đếm root như đếm partition/volume.** Root mô tả namespace; volume có thể mount sâu trong cây.
2. **Dùng store của root để đo đường dẫn con.** Một thư mục con có thể nằm trên mount point khác. Hãy lấy store từ đúng path đích.
3. **Coi `usableSpace` là hạn mức chắc chắn có thể ghi.** Đây là ước tính tức thời, không thay thế xử lý lỗi khi ghi.
4. **Coi `isReadOnly() == false` là quyền ghi tệp.** Quyền truy cập còn phụ thuộc ACL, permissions, quota, path và trạng thái vận hành. `Files.isWritable(path)` cũng không thay thế việc xử lý lỗi của thao tác ghi thật.
5. **Hard-code `/`, `C:\` hoặc tên/loại store.** Hãy dùng `Path`/`resolve`; chỉ hiển thị `name()/type()` như dữ liệu chẩn đoán.
6. **Cho rằng provider nào cũng có POSIX, DOS, ACL hoặc file space đáng tin cậy.** Kiểm tra khả năng hỗ trợ và chấp nhận `UnsupportedOperationException` / `IOException` theo hợp đồng API.
7. **Tiếp tục sử dụng `Path` của ZIP đã đóng.** Vòng đời của `Path` gắn với `FileSystem`; đóng tài nguyên bằng `try-with-resources`, rồi chỉ sử dụng chúng trong phạm vi còn mở.
8. **Đồng nhất provider với thiết bị lưu trữ.** Scheme `jar` hay `file` xác định cách triển khai truy cập; filesystem type và store là thông tin ở tầng khác.

## 11. Câu hỏi tự kiểm tra

- Vì sao `getRootDirectories()` trên Linux có thể chỉ cho `/`, trong khi `Files.getFileStore(Path.of("/mnt/data"))` trả metadata của volume riêng?
- Nếu cần kiểm tra dung lượng trước khi ghi vào `/mnt/data/reports`, bạn nên lấy `FileStore` của đường dẫn nào? Điều kiện gì phải đúng với đường dẫn ấy?
- Phân biệt `getUnallocatedSpace()` và `getUsableSpace()`. Vì sao kết quả kiểm tra không bảo đảm một thao tác `Files.writeString()` sau đó sẽ thành công?
- Tại sao `Path` do `zipFs.getPath(...)` tạo ra không nên được xử lý như `Path.of(...)` của hệ điều hành, dù cả hai thực hiện API `Path`?
- Việc `fs.supportedFileAttributeViews().contains("posix")` đủ để khẳng định bạn có thể đổi quyền mọi tệp trên hệ thống không? Vì sao?

## 12. Tổng kết

Hãy xem **root directory** là gốc của không gian đường dẫn, **`FileStore`** là nguồn metadata cho nơi dữ liệu được lưu, còn **`FileSystemProvider`** là thành phần triển khai các thao tác tệp. Khi cần dung lượng hoặc view thuộc tính, hãy bắt đầu từ **`Path` đích**, xác định `FileSystem` và `FileStore` tương ứng, đọc khả năng được quảng bá, rồi xử lý ngoại lệ của thao tác thực tế. Cách làm này bền vững hơn trước khác biệt Unix/Windows, mount point, network storage và các provider như ZIP.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-systems-007">ProviderMismatchException, FileSystemNotFoundException và ClosedFileSystemException</a>

<details>
<summary>Xem chi tiết</summary>

# ProviderMismatchException, FileSystemNotFoundException và ClosedFileSystemException trong Java NIO.2

## 1. Bối cảnh: một `Path` luôn thuộc về một `FileSystem`

Java NIO.2 (`java.nio.file`) không giới hạn ở đĩa máy tính: một `FileSystem` có thể biểu diễn hệ thống tệp mặc định, nội dung ZIP/JAR (Zip File System), hoặc hệ thống tệp ảo do thư viện cung cấp. `FileSystemProvider` là thành phần hiện thực các thao tác truy cập; `Path` được tạo bởi một `FileSystem` cụ thể và mang theo quan hệ này.

```text
FileSystemProvider (cách hiện thực I/O)
        |
        +-- FileSystem (một thể hiện đang mở / vòng đời truy cập)
                |
                +-- Path (đường dẫn thuộc đúng FileSystem đó)
```

Ví dụ:

- `Path.of("report.txt")` thường thuộc file system mặc định, có provider scheme `file`.
- `zipFs.getPath("/report.txt")` thuộc một file system ZIP/JAR đã mở bằng `FileSystems.newFileSystem(...)`, có provider scheme `jar`.
- Hai `Path` nhìn giống chuỗi ký tự không có nghĩa chúng cùng provider hoặc cùng file system.
- `FileSystems.getDefault()` lấy hệ thống tệp mặc định, do JVM quản lý. File system mặc định không đóng được; một file system ZIP hoặc provider tùy chỉnh thường phải đóng khi dùng xong.

Ba ngoại lệ trong bài có nguyên nhân khác nhau: **trộn đối tượng từ provider không tương thích**; **tra cứu file system không tồn tại trong ngữ cảnh truy cập**; **dùng file system đã đóng**. Nắm được nguồn gốc `Path` và vòng đời `FileSystem` giúp chẩn đoán chính xác.

## 2. Bảng phân biệt nhanh

| Ngoại lệ | Nhóm ngoại lệ | Nguyên nhân điển hình | Hành động đúng |
| --- | --- | --- | --- |
| `ProviderMismatchException` | `IllegalArgumentException` → `RuntimeException` | API yêu cầu `Path` cùng provider nhưng nhận `Path` do provider khác tạo | Kiểm tra nguồn `Path`; tạo `Path` phụ trợ bằng cùng `FileSystem`, hoặc dùng API hỗ trợ truyền dữ liệu qua providers |
| `FileSystemNotFoundException` | `RuntimeException` | Cần một `FileSystem` theo URI nhưng chưa có thể hiện tương ứng; hoặc phép chuyển URI không tìm/tạo tự động được file system | Kiểm tra URI, provider, và bước mở/mount file system trước khi truy xuất |
| `ClosedFileSystemException` | `IllegalStateException` → `RuntimeException` | Gọi thao tác truy cập qua `Path` thuộc `FileSystem` đã đóng | Đảm bảo truy cập nằm trong thời gian file system còn mở; không giữ `Path` để dùng sau khi chủ sở hữu đóng |

Cả ba là **unchecked exceptions**: không bắt buộc khai báo `throws` trong chữ ký. Chúng khác `IOException`, `NoSuchFileException`, và `AccessDeniedException` vốn phản ánh các lỗi I/O hoặc truy cập cụ thể. Không nên gom mọi lỗi liên quan đến đường dẫn thành “file không tồn tại”.

## 3. `ProviderMismatchException`: trộn đường dẫn của các provider

### 3.1. Điều kiện xảy ra

`ProviderMismatchException` được ném khi một phương thức hoạt động trên đối tượng do provider A tạo nhưng nhận đối số do provider B tạo mà phương thức ấy yêu cầu cùng provider. Một tình huống quen thuộc là kết hợp `Path` của file system mặc định với `Path` của Zip File System trong phép `resolve(Path)` hoặc `relativize(Path)`.

Ví dụ ý tưởng:

```java
Path hostPath = Path.of("logs");       // Provider file mặc định
Path zipPath = zipFs.getPath("a.txt"); // Provider jar của ZipFS

hostPath.resolve(zipPath);             // Không tương thích provider
```

Đây là đoạn minh họa trong một phương thức; `zipFs` phải là `FileSystem` ZIP đã mở. Trên provider file/ZipFS chuẩn trong JDK, lời gọi `resolve(Path)` này gây `ProviderMismatchException`.

**Không kết luận rằng mọi API có hai `Path` đều từ chối khác provider.** Ví dụ, `Files.copy(source, target)` hỗ trợ sao chép giữa các provider, mặc dù thuộc tính, tính nguyên tử và tùy chọn hỗ trợ phụ thuộc provider. Phải đọc hợp đồng API cụ thể.

### 3.2. Cách nhận diện

```java
System.out.println(hostPath.getFileSystem().provider().getScheme());
System.out.println(zipPath.getFileSystem().provider().getScheme());
System.out.println(hostPath.getFileSystem() == zipPath.getFileSystem());
```

Thông thường hai scheme là `file` và `jar`; phép so sánh identity giúp nhận biết hai đối tượng `FileSystem` có cùng thể hiện hay không. **Scheme giống nhau chưa chứng minh hai `FileSystem` là cùng một instance**: một provider có thể quản lý nhiều file system, chẳng hạn các kho ZIP khác nhau.

### 3.3. Cách sửa mà vẫn giữ đúng ngữ nghĩa

Nếu muốn tính đường dẫn con **nằm trong ZIP**, dùng một `Path` tạo bởi ZIP:

```java
Path zipBase = zipFs.getPath("/docs");
Path zipChild = zipBase.resolve("lesson.txt");  // resolve(String) theo provider của zipBase
```

Nếu cần `Path` ở filesystem mặc định, tạo bằng `Path.of(...)`, `FileSystems.getDefault().getPath(...)`, hoặc `hostBase.resolve("name")`. Không dùng `Path.toString()` rồi `Path.of(...)` để “chuyển” file nằm trong ZIP sang file thật: chuỗi `/docs/lesson.txt` chỉ là tên đường dẫn, không chứa nội dung hay ngữ cảnh ZIP.

Nếu muốn **di chuyển dữ liệu** giữa file system, cân nhắc `Files.copy(zipEntry, hostFile)` hoặc `Files.newInputStream(zipEntry)` kết hợp với output stream ở đích, theo nhu cầu dữ liệu và ngoại lệ I/O. Nếu muốn **tương đối hóa** hai đường dẫn, bảo đảm chúng thuộc cùng hệ thống tệp và có ràng buộc root phù hợp trước khi `relativize`.

## 4. `FileSystemNotFoundException`: không tìm được file system cần truy xuất

### 4.1. Khác biệt giữa “file thiếu” và “file system chưa được mở”

`FileSystemNotFoundException` không phải thông báo rằng `docs/report.txt` bị xóa. Nó liên quan đến **việc tìm một file system**, chẳng hạn gọi:

```java
FileSystems.getFileSystem(uri);
```

`getFileSystem(URI)` chỉ **tra cứu một file system đang tồn tại** do provider nhận diện qua scheme trong URI. Nó không có nhiệm vụ tạo file system mới. Đối với ZipFS, nếu file system cho URI đó chưa được mở, provider ZIP chuẩn ném `FileSystemNotFoundException`.

```java
URI zipFsUri = URI.create("jar:file:///path/to/archive.zip");

// Chưa gọi FileSystems.newFileSystem(zipFsUri, ...):
FileSystems.getFileSystem(zipFsUri); // Có thể ném FileSystemNotFoundException
```

Đường dẫn `jar:file:///path/to/archive.zip` là minh họa định dạng URI, không phải đường dẫn di động trên mọi OS. Trong code thực tế, tạo URI từ `archive.toUri()` để tránh sai ký tự, dấu cách hoặc phân cách Windows.

`Path.of(URI)` có quy tắc riêng: nó nhờ provider chuyển URI thành `Path`, và có thể ném `FileSystemNotFoundException` khi không tìm/tự tạo được file system, **hoặc khi không có provider cho scheme**. Trong khi đó, `FileSystems.getFileSystem(URI)` có thể ném `ProviderNotFoundException` nếu provider tương ứng chưa được cài. Những API và điều kiện khác nhau không nên bị gộp thành một.

### 4.2. `getFileSystem`, `newFileSystem`, `getDefault`

| API | Ý nghĩa | Lưu ý |
| --- | --- | --- |
| `FileSystems.getDefault()` | Lấy file system mặc định của JVM | Không phải API mount ZIP/JAR |
| `FileSystems.getFileSystem(uri)` | Tìm file system đã tồn tại theo URI | Không tự tạo; khi chưa tồn tại có thể `FileSystemNotFoundException` |
| `FileSystems.newFileSystem(uri, env)` | Yêu cầu provider tạo/mở file system theo URI | Có thể `IOException`, `ProviderNotFoundException`, `FileSystemAlreadyExistsException` hoặc lỗi cấu hình |
| `Path.of(uri)` | Chuyển URI thành `Path` qua provider | Một số provider cho phép tự tạo file system; một số cần mount trước |
| `Files.exists(path)` | Kiểm tra một đường dẫn cụ thể | `false` không có nghĩa file system provider không tồn tại |

Muốn dùng URI đến mục bên trong ZIP sau khi file system được mở, `Path.of(jarEntryUri)` đôi khi phù hợp, nhưng cú pháp URI là đặc thù provider. Cách rõ ràng hơn trong code đang quản lý ZipFS là giữ đối tượng `FileSystem` và dùng `zipFs.getPath("/inside.txt")`.

### 4.3. Không xử lý bằng cách mở lặp vô điều kiện

`newFileSystem` cũng không phải hàm idempotent “đảm bảo có file system”: nếu file system theo URI đã có và provider quy định chỉ được một instance, có thể ném `FileSystemAlreadyExistsException`. Khi một ứng dụng có nhiều thành phần sử dụng chung ZIP FS, cần chỉ định rõ **ai tạo, ai giữ và ai đóng**. Tránh cách chữa lỗi bằng việc bắt mọi exception rồi gọi `newFileSystem` hoặc đóng đối tượng do thành phần khác sở hữu.

## 5. `ClosedFileSystemException`: sử dụng file system sau khi đóng

`FileSystem` mở khi được tạo. Với các file system có thể đóng như ZIP FS, gọi `close()` giải phóng tài nguyên của instance đó. Sau khi đóng, những thao tác truy cập qua các `Path` liên quan có thể ném `ClosedFileSystemException`.

Mẫu lỗi phổ biến:

```java
Path saved;
try (FileSystem zipFs = FileSystems.newFileSystem(zipUri, Map.of())) {
    saved = zipFs.getPath("/report.txt");
}
// zipFs đã close: saved vẫn là một đối tượng Path
Files.readString(saved); // ClosedFileSystemException với ZipFS chuẩn
```

Đoạn này minh họa phạm vi tài nguyên; giả định `zipUri` trỏ tới một ZIP đã tồn tại. Việc `Path` còn tham chiếu được **không chứng minh file system của nó vẫn mở**. Chưa chắc `toString()` hoặc truy cập tên cú pháp trên `Path` luôn ném ngoại lệ, nhưng các thao tác I/O đòi file system đang hoạt động.

**Không thể “mở lại” một instance đã đóng bằng `isOpen()`.** Có thể phải tạo một `FileSystem` instance mới qua provider (nếu được hỗ trợ) và dựng lại `Path` từ instance mới. Không tiếp tục dùng `Path` cũ đã lấy từ instance cũ.

### 5.1. Bẫy try-with-resources

Sai ngữ cảnh:

```java
static Path locate(FileSystem zipFs) {
    return zipFs.getPath("/data.txt");
}
```

Hàm trên chỉ trả `Path`; caller cần bảo đảm `zipFs` còn sống đến khi hoàn tất I/O. Nếu có code đóng zipFs trước khi caller dùng kết quả, lỗi nằm ở **quản lý vòng đời**, không nằm ở `locate` hay cú pháp đường dẫn.

Một thiết kế dễ kiểm soát hơn là mở và xử lý nội dung trong cùng phạm vi:

```java
static String readEntry(FileSystem zipFs) throws IOException {
    return Files.readString(zipFs.getPath("/data.txt"));
}
```

`readEntry` vẫn yêu cầu caller giữ `zipFs` mở trong lúc gọi. Một phương án khác là thành phần sở hữu ZipFS thực hiện toàn bộ I/O, sau đó trả nội dung hoặc dữ liệu đã tách khỏi vòng đời ZipFS. Không nên trả stream, iterator hoặc `Path` dài hạn mà không tài liệu hóa lifecycle.

### 5.2. Mặc định và custom provider

`FileSystems.getDefault().close()` không dùng để “refresh”: file system mặc định không thể đóng. Với các provider tùy chỉnh, quy tắc chia sẻ, cache, mở lại và đóng có thể khác, nhưng hợp đồng file system đóng vẫn quan trọng. Đừng nhầm `ClosedFileSystemException` với `ClosedWatchServiceException`, vì watch service có vòng đời riêng.

## 6. Ví dụ Java 17+ chạy được: cả ba ngoại lệ trong cùng chương trình

Chương trình này dùng **ZIP File System** có sẵn trong bản phân phối JDK chứa module `jdk.zipfs`. Nó tạo một ZIP mới trong thư mục tạm; chưa mount thì thử tra cứu; mount rồi trộn `Path` của hai provider; đóng ZipFS rồi thử đọc mục cũ. Ví dụ tự xóa tài nguyên do nó tạo.

Tạo file `FileSystemExceptionsDemo.java`:

```java
import java.io.IOException;
import java.net.URI;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.util.Map;

public class FileSystemExceptionsDemo {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("nio-provider-");
        Path archive = dir.resolve("sample.zip");

        // Tạo một URI jar: từ URI chuẩn hóa của file ZIP thật.
        URI zipUri = URI.create("jar:" + archive.toUri());
        Path zipEntry = null;

        try {
            // Case 1: file system ZIP mới này chưa được mở.
            try {
                FileSystems.getFileSystem(zipUri);
                System.out.println("already mounted");
            } catch (FileSystemNotFoundException ex) {
                System.out.println("before mount: FileSystemNotFoundException");
            }

            // Mở ZIP; try-with-resources sẽ đóng ZipFS ở cuối block.
            try (FileSystem zipFs = FileSystems.newFileSystem(
                    zipUri, Map.of("create", "true"))) {

                zipEntry = zipFs.getPath("/lesson.txt");
                Files.writeString(zipEntry, "Hello ZIP");
                System.out.println("zip content: " + Files.readString(zipEntry));

                // Case 2: Path mặc định + Path ZipFS ở phép resolve(Path).
                Path hostDir = Path.of("local");
                Path zipRelative = zipFs.getPath("relative.txt");
                try {
                    hostDir.resolve(zipRelative);
                    System.out.println("resolved unexpectedly");
                } catch (ProviderMismatchException ex) {
                    System.out.println("mixed resolve: ProviderMismatchException");
                }

                System.out.println("zip open: " + zipFs.isOpen());
            }

            // Case 3: Path còn tồn tại trong bộ nhớ nhưng ZipFS đã đóng.
            try {
                Files.readString(zipEntry);
                System.out.println("read unexpectedly");
            } catch (ClosedFileSystemException ex) {
                System.out.println("after close: ClosedFileSystemException");
            }
        } finally {
            Files.deleteIfExists(archive);
            Files.deleteIfExists(dir);
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa file nguồn:

```bash
javac FileSystemExceptionsDemo.java
java FileSystemExceptionsDemo
```

Kết quả dự kiến với JDK 17+ chuẩn có `jdk.zipfs`, quyền ghi thư mục tạm và không có can thiệp đồng thời:

```text
before mount: FileSystemNotFoundException
zip content: Hello ZIP
mixed resolve: ProviderMismatchException
zip open: true
after close: ClosedFileSystemException
```

Diễn giải theo thời điểm:

1. URI `jar:` xác định file system cho file ZIP mới; `getFileSystem` chỉ tìm instance đã mở nên báo `FileSystemNotFoundException`.
2. `newFileSystem(..., Map.of("create", "true"))` mở ZipFS và tạo archive; `Files.writeString` và `Files.readString` thực hiện I/O qua `zipEntry` hợp lệ.
3. `hostDir.resolve(zipRelative)` nhận `Path` thuộc provider `jar` trong khi `hostDir` thuộc provider `file`, nên có `ProviderMismatchException` trong triển khai JDK file + ZipFS.
4. Thoát `try` đóng ZIP. `zipEntry` vẫn tồn tại dưới dạng đối tượng Java, nhưng `Files.readString(zipEntry)` truy cập vào ZipFS đã đóng và báo `ClosedFileSystemException`.
5. Khối `finally` dọn ZIP và thư mục tạm sau khi giải phóng ZipFS. Lỗi môi trường như không đủ quyền, thiếu module `jdk.zipfs`, hoặc I/O hỏng có thể khiến chương trình ném ngoại lệ khác và không in đủ các dòng trên.

**Chú ý:** chương trình minh họa sử dụng cùng ZIP FS ở một luồng. Nếu nhiều luồng hoặc thư viện cùng thao tác trên ZIP, cần quy định ownership để không đóng một file system trong lúc thành phần khác còn đang đọc.

## 7. Cùng một triệu chứng nhưng ba hướng sửa khác nhau

Giả sử code xử lý ZIP qua nhiều tầng service:

```text
Request -> URI hoặc Path -> chọn FileSystem -> đọc entry
                           |                   |
                           |                   +-- đã close -> ClosedFileSystemException
                           +-- chưa mount / tra cứu sai -> FileSystemNotFoundException
         +-- trộn host Path với zip Path -> ProviderMismatchException
```

Với `ProviderMismatchException`, bước đầu là kiểm tra `path.getFileSystem()` và `path.getFileSystem().provider()` trên từng đối số. Nếu ý đồ là thao tác cú pháp trong ZIP, tạo đối số qua ZIP FS. Nếu ý đồ là sao chép nội dung, dùng API truyền dữ liệu được đặc tả hỗ trợ khác provider.

Với `FileSystemNotFoundException`, kiểm tra URI `scheme`, thời điểm mount, đường dẫn kho chứa và `FileSystems.getFileSystem` có đang dùng đúng URI mà `newFileSystem` từng nhận không. Không lập tức sửa bằng cách `Files.createFile`: file entry và file system là hai tầng khác nhau.

Với `ClosedFileSystemException`, xác định lời gọi `close()`, `try-with-resources`, hoặc component dọn cache xảy ra trước I/O. Đừng chỉ bắt ngoại lệ rồi retry cùng `Path`: nó vẫn trỏ tới instance đã đóng.

## 8. Phân biệt với các ngoại lệ dễ nhầm

| Ngoại lệ khác | Khi nào đáng nghi | Phân biệt |
| --- | --- | --- |
| `NoSuchFileException` | Đường dẫn đích không tồn tại khi thao tác I/O | File system có thể đang mở và hoạt động; chỉ thiếu mục tệp |
| `ProviderNotFoundException` | Chưa có provider cho URI scheme trong API tìm/mở provider | Khác với đã có provider nhưng chưa tạo file system instance |
| `FileSystemAlreadyExistsException` | Mở mới một file system theo URI đã được mount, khi provider không cho instance trùng | Ngược chiều với lookup file system chưa có; không có nghĩa entry đã tồn tại |
| `FileSystemException` | Lỗi hệ thống tệp trong thao tác cụ thể | Là `IOException` có thể mang file, otherFile và reason; không phải đồng nghĩa với closed/mismatch |
| `IllegalArgumentException` / `InvalidPathException` | URI hoặc cú pháp đường dẫn không hợp lệ | Lỗi định dạng/tiền điều kiện, cần kiểm tra dữ liệu đầu vào |
| `ClosedWatchServiceException` | Dùng `WatchService` đã đóng | Vòng đời watch service khác vòng đời `FileSystem` |

Đặc biệt, **`ProviderMismatchException` là một dạng `IllegalArgumentException`**. Nếu muốn xử lý riêng trường hợp mismatch, đặt `catch (ProviderMismatchException)` trước `catch (IllegalArgumentException)` (nếu cần cả hai); ngược lại Java báo catch con không thể tới được.

## 9. Thiết kế dịch vụ truy cập nhiều file system

### 9.1. Quy định ownership

Đối với một chức năng đọc tài liệu từ ZIP, có thể chọn:

- Mỗi thao tác tự mở ZIP, đọc toàn bộ dữ liệu cần dùng, đóng trước khi trả kết quả.
- Một component chuyên trách quản lý một ZIP FS dùng chung; mọi người sử dụng thông qua component và chỉ component này được đóng khi không còn tác vụ đang dùng.
- API trả dữ liệu độc lập như `byte[]`, `String` hoặc DTO sau khi đã đọc xong; nếu trả stream hoặc `Path`, phải xác định ai giữ tài nguyên mở và đóng lúc nào.

Dùng `try-with-resources` cho instance do chính phạm vi đó tạo, nhưng **không tự ý đóng** một file system mà caller đưa vào. Hạn chế lưu `Path` ZipFS trong singleton lâu hơn vòng đời ZipFS.

### 9.2. Giữ nhất quán nguồn của `Path`

Một method tiếp nhận đường dẫn trong ZIP nên nhận `FileSystem` hoặc một `Path` ZIP làm gốc; tạo con bằng `root.resolve("name")` để theo cùng provider. Nếu cần đích ngoài ZIP, truyền vào một `Path` host riêng và gọi thao tác có hỗ trợ cross-provider, thay vì ghép cơ học `host.resolve(zipPath)`.

Tránh dùng `toString()` để suy ra provider hoặc dùng `Path.of(...) ` để chuyển đổi tùy tiện. `Path.toUri()` mang nhiều ngữ cảnh hơn chuỗi tên, nhưng URI vẫn phải được xử lý qua provider/file system phù hợp; không phải phép “đổi provider” phổ quát.

### 9.3. Phân biệt các tầng khi ghi log

Khi có lỗi, thông tin hữu ích gồm: tên thao tác, scheme của provider, URI định danh file system (nếu có và phù hợp), tình trạng `isOpen()`, loại đối số `Path`, và stack trace. Tránh log thông tin nhạy cảm hoặc nguyên văn đường dẫn bí mật ở mức log công khai. Chỉ dùng `Files.exists()` để chẩn đoán entry nếu thực sự cần; nó không thay thế đánh giá trạng thái file system.

## 10. Checklist xử lý sự cố

1. **Đang gọi API nào?** `resolve`/`relativize`, `getFileSystem`/`Path.of(URI)`, hay thao tác đọc/ghi như `Files.readString`?
2. **`Path` được sinh ra ở đâu?** `Path.of` của host, `zipFs.getPath`, hay thư viện provider khác?
3. **Hai đối số có thuộc cùng provider không?** Nếu không, xác minh API có cho phép cross-provider.
4. **Provider có được cài không?** Nếu thiếu scheme, xem `ProviderNotFoundException` hoặc hợp đồng phương thức gọi.
5. **File system đã được tạo/mount chưa?** `getFileSystem` không tạo mới; cần bước `newFileSystem` phù hợp.
6. **File system đã bị đóng chưa?** Kiểm tra `isOpen()` khi còn giữ reference và rà soát ownership/try-with-resources.
7. **Có race với luồng khác?** Trạng thái có thể đổi sau khi kiểm tra; bảo đảm thời gian sử dụng không chồng lấn với lúc close.
8. **Kết quả sửa có đúng nghiệp vụ?** Nếu phải copy dữ liệu từ ZIP ra host, dùng copy; nếu xây đường dẫn trong ZIP, tạo con qua chính ZIP FS; nếu cần lookup, đảm bảo mount tồn tại.

## 11. Bài tập kiểm tra hiểu biết

**Bài 1.** Hai `Path` in cùng `"/a.txt"`. Có thể đưa chúng vào bất kỳ `resolve(Path)` nào không?

**Đáp án:** Không. Chuỗi biểu diễn giống nhau không chứng minh chúng cùng provider; kiểm tra `getFileSystem()`/provider và hợp đồng API.

**Bài 2.** Một file `archive.zip` thật tồn tại trên đĩa, nhưng `FileSystems.getFileSystem(zipUri)` ném `FileSystemNotFoundException`. Vì sao?

**Đáp án:** File ZIP tồn tại không có nghĩa một instance ZipFS ứng với URI đó đang được mount. `getFileSystem` tra cứu instance; `newFileSystem` mới là API tạo/mở.

**Bài 3.** Sau `try (FileSystem fs = ...)`, biến `Path p = fs.getPath("/x")` còn khác `null`. Có thể gọi `Files.readString(p)` không?

**Đáp án:** Không thể trông đợi thành công vì `p` gắn với file system đã đóng; ZipFS chuẩn ném `ClosedFileSystemException`.

**Bài 4.** Có thể `Files.copy(zipEntry, hostTarget)` khi hai `Path` thuộc providers khác nhau không?

**Đáp án:** Có; `Files.copy` có hợp đồng hỗ trợ khác provider. Vẫn cần xử lý `IOException`, tùy chọn có thể không hỗ trợ hoặc có ngữ nghĩa khác, và bảo đảm ZipFS mở trong toàn bộ thao tác.

**Bài 5.** Nếu `newFileSystem(uri, env)` gặp `FileSystemAlreadyExistsException`, có nên ngay lập tức đóng instance đã tồn tại không?

**Đáp án:** Không. Phải xác định chủ sở hữu và chính sách sử dụng chung; đóng có thể phá các thao tác đang chạy ở thành phần khác.

## Tài liệu chuẩn

- Java SE 21, `ProviderMismatchException`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/ProviderMismatchException.html
- Java SE 21, `FileSystemNotFoundException`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/FileSystemNotFoundException.html
- Java SE 21, `ClosedFileSystemException`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/ClosedFileSystemException.html
- Java SE 21, `FileSystems`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/FileSystems.html
- Java SE 21, `FileSystem`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/FileSystem.html
- Java SE 21, `Path`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Path.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-systems-008">Giới hạn chuyển sang File và giả định về đường dẫn vật lý</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn chuyển sang `File` và giả định về đường dẫn vật lý trong Java NIO.2

## 1. Bài toán: một `Path` không nhất thiết là đường dẫn tệp vật lý

Trong Java, hai kiểu được dùng phổ biến để biểu diễn tên đường dẫn:

- `java.io.File`: API cũ, biểu diễn **tên đường dẫn trừu tượng** của hệ thống tệp mặc định. Đối tượng `File` có thể đại diện cho một mục chưa tồn tại; tạo `new File("abc.txt")` không tạo tệp.
- `java.nio.file.Path`: API NIO.2, biểu diễn một đường dẫn thuộc **một `FileSystem` cụ thể**, do `FileSystemProvider` quản lý. `Path` có thể thuộc hệ thống tệp mặc định hoặc provider khác, như hệ thống tệp bên trong tệp ZIP/JAR.

Một số mã cũ nhận `File` và khiến người học nghĩ rằng mọi `Path` đều có thể chuyển thành `File`. Giả định đó sai:

```java
Path p = /* một Path được nhận từ bên ngoài */;
File file = p.toFile(); // Có thể ném UnsupportedOperationException!
```

Đoạn trên chỉ minh họa chữ ký; `p` phải được cung cấp bởi caller. `Path.toFile()` chỉ hỗ trợ `Path` gắn với **default file-system provider**. Một `Path` trỏ tới entry bên trong ZIP vẫn đọc được bằng `Files.readString(path)` nhưng không thể chuyển trực tiếp thành `File`.

Mục tiêu của bài: nhận biết ranh giới tương thích giữa `File` và `Path`, phân biệt đường dẫn logic với tài nguyên trên đĩa, và thiết kế mã xử lý file không vô tình loại bỏ mọi file-system provider ngoài mặc định.

## 2. Mô hình `Path → FileSystem → FileSystemProvider`

`Path` không tự chứa nội dung tệp. Nó mang tên đường dẫn và tham chiếu đến một hệ thống tệp, còn provider thực hiện thao tác I/O tương ứng.

```text
Path (đường dẫn có ngữ nghĩa của FileSystem)
  ├── FileSystem mặc định (file:)
  │     └── đường dẫn OS, thư mục, ổ đĩa, mount ...
  │            ↳ thường chuyển được Path.toFile()
  │
  ├── ZIP/JAR FileSystem (jar:)
  │     └── mục /docs/readme.txt trong archive.zip
  │            ↳ Files.readString() được; Path.toFile() không được
  │
  └── FileSystem provider tùy biến
        └── hệ thống tệp trong bộ nhớ / mạng / mô hình riêng
               ↳ chỉ dùng tính năng provider hỗ trợ
```

Có thể quan sát ranh giới này:

```java
Path path = Path.of("notes.txt");
FileSystem fileSystem = path.getFileSystem();
System.out.println(fileSystem.provider().getScheme()); // "file" ở provider mặc định thông thường
```

*Đoạn minh họa độc lập trong một phương thức/chương trình có các import phù hợp.* Kết quả scheme của default provider thông thường là `file`. Tuy nhiên, phương thức có thể phụ thuộc vào cấu hình default provider và môi trường; **không dùng riêng chuỗi scheme hoặc tên lớp provider làm một bằng chứng chắc chắn rằng dữ liệu nằm trên đĩa cục bộ**.

Một phân biệt quan trọng:

- **Default provider**: provider tích hợp với hệ thống tệp mặc định của JVM; có thể nhìn thấy ổ đĩa vật lý, ổ mạng, network mount, thư mục được mount hoặc môi trường container.
- **Physical/local file**: kết luận về vị trí và bản chất lưu trữ. `File`, `Path` và tên scheme `file` không bảo đảm dữ liệu thực sự ở ổ đĩa cục bộ, luôn tồn tại, có thể ghi hoặc là tệp thường.

Do đó, tính tương thích với `File` và tính “vật lý” là **hai câu hỏi khác nhau**.

## 3. Quy tắc của `Path.toFile()`

Theo API Java SE, `Path.toFile()` trả về `java.io.File` đại diện cho đường dẫn này **nếu `Path` gắn với default provider**. Trường hợp khác, phương thức ném:

```text
java.lang.UnsupportedOperationException
```

Đối với default provider, thao tác tương đương tạo một `File` từ biểu diễn chuỗi của `Path`. Không có yêu cầu đối tượng được tạo phải trỏ đến tệp đã tồn tại.

### 3.1. Trên hệ thống tệp mặc định

```java
import java.io.File;
import java.nio.file.Path;

Path path = Path.of("docs", "guide.txt");
File legacy = path.toFile();

System.out.println(legacy.getPath().equals(path.toString()));
```

Đây là đoạn minh họa; trên default provider thông thường, kết quả là `true`. Nó cho thấy **biểu diễn tên đường dẫn**, không chứng minh `guide.txt` tồn tại.

Các nhận định đúng:

- `Path.of("docs", "guide.txt")` dùng **default FileSystem**.
- `path.toFile()` thực hiện chuyển đối tượng đối với provider mặc định.
- `path` có thể là tương đối hoặc tuyệt đối, và `File` mới không tự động làm nó tuyệt đối.
- Không có bước đọc tệp hoặc kiểm tra sự tồn tại chỉ vì gọi `toFile()`.

### 3.2. Với provider khác

```java
// zipEntry lấy từ zipFs.getPath("/docs/guide.txt").
Files.readString(zipEntry); // Có thể đọc được nếu entry tồn tại.
zipEntry.toFile();           // UnsupportedOperationException.
```

Đây là mã minh họa dùng `zipEntry` và `zipFs` từ ZIP file system đã mở. Lỗi là do **không có phép biểu diễn tương đương bằng `java.io.File`**, không phải bằng chứng entry không tồn tại. Ngay cả khi `zipEntry.isAbsolute() == true` hoặc `Files.exists(zipEntry) == true`, `toFile()` vẫn không được hỗ trợ trên ZIP provider.

### 3.3. `File.toPath()` và chiều chuyển ngược

`File.toPath()` cung cấp `Path` của tên đường dẫn thuộc hệ thống tệp mặc định:

```java
File oldApiFile = new File("logs/app.log");
Path nioPath = oldApiFile.toPath();
```

Cách chuyển từ `File` sang `Path` phù hợp khi di chuyển dần từ API cũ sang `Files`. Nhưng đừng kết luận tính đối xứng cho mọi `Path`: một ZIP `Path` không có `File` tương ứng, nên không có phép chuyển hai chiều tổng quát. Ngay cả `file.toPath().toFile()` cũng không được API bảo đảm bằng `equals` với `file` ban đầu trong mọi trường hợp; hãy kiểm tra điều kiện nghiệp vụ thay vì dùng đẳng thức đối tượng làm bằng chứng.

## 4. Ví dụ Java 17+ chạy được: cùng thao tác `Files`, hai provider

Chương trình tạo một tệp tạm trên hệ thống tệp mặc định và một kho ZIP tạm. Nó ghi một entry vào ZIP, đọc entry bằng `Files` rồi thử `toFile()`.

**Tệp nguồn:** `PathToFileLimitDemo.java`

```java
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class PathToFileLimitDemo {
    public static void main(String[] args) throws IOException {
        Path regularFile = Files.createTempFile("default-fs-", ".txt");
        Path zipArchive = Files.createTempFile("virtual-fs-", ".zip");

        // ZIP provider với create=true cần một tên archive chưa tồn tại.
        Files.delete(zipArchive);

        try {
            Files.writeString(regularFile, "outside ZIP");

            System.out.println("default conversion: "
                    + regularFile.toFile().isFile());

            URI zipUri = URI.create("jar:" + zipArchive.toUri());

            try (FileSystem zipFs = FileSystems.newFileSystem(
                    zipUri, Map.of("create", "true"))) {

                Path zipEntry = zipFs.getPath("/docs/readme.txt");
                Files.createDirectories(zipEntry.getParent());
                Files.writeString(zipEntry, "inside ZIP");

                System.out.println("ZIP content: "
                        + Files.readString(zipEntry));

                System.out.println("same provider: "
                        + (zipEntry.getFileSystem().provider()
                        == FileSystems.getDefault().provider()));

                try {
                    zipEntry.toFile();
                    System.out.println("unexpected conversion");
                } catch (UnsupportedOperationException ex) {
                    System.out.println(
                            "ZIP conversion: UnsupportedOperationException");
                }
            }
        } finally {
            Files.deleteIfExists(regularFile);
            Files.deleteIfExists(zipArchive);
        }
    }
}
```

Biên dịch và chạy với **JDK 17+ chuẩn có module `jdk.zipfs`**:

```bash
javac PathToFileLimitDemo.java
java PathToFileLimitDemo
```

Kết quả dự kiến:

```text
default conversion: true
ZIP content: inside ZIP
same provider: false
ZIP conversion: UnsupportedOperationException
```

**Giải thích từng bước:**

1. `Files.createTempFile` tạo tệp thật trong file system mặc định; sau khi tạo và ghi thành công, `regularFile.toFile().isFile()` trả `true`.
2. `zipArchive` là tên tệp archive thuộc default provider. Mã xóa vỏ tệp ZIP rỗng vì `jdk.zipfs` cần tạo archive hợp lệ.
3. `FileSystems.newFileSystem(zipUri, Map.of("create", "true"))` mở ZIP dưới dạng một `FileSystem` riêng. Định dạng URI `jar:...` và tùy chọn `create` là quy ước của ZIP provider.
4. `zipFs.getPath("/docs/readme.txt")` biểu diễn **entry bên trong archive**, không phải `/docs/readme.txt` ở root hệ điều hành.
5. `Files.createDirectories`, `Files.writeString`, `Files.readString` làm việc trực tiếp qua ZIP provider: in `inside ZIP`.
6. ZIP provider không phải default provider nên hai biểu thức sau đúng theo thứ tự: so sánh provider là `false`, gọi `zipEntry.toFile()` ném `UnsupportedOperationException`.
7. `try-with-resources` đóng ZIP file system để hoàn tất thay đổi lên archive; `finally` xóa các tệp tạm. Không dùng `zipEntry` để truy cập archive sau khi đóng `zipFs`.

Nếu runtime được đóng gói tối giản và không chứa `jdk.zipfs`, thao tác mở ZIP sẽ thất bại do thiếu provider; đó là điều kiện môi trường, không phải thay đổi quy tắc `Path.toFile()`. Chương trình cố ý không giấu lỗi khởi tạo ZIP dưới một output mặc định “thành công”.

### 4.1. Vì sao `new File(zipEntry.toString())` là cách sửa sai?

Ví dụ `zipEntry.toString()` có thể chỉ là `/docs/readme.txt`. Nếu gọi:

```java
File misleading = new File(zipEntry.toString());
```

`misleading` bây giờ chỉ một tên đường dẫn trong **default filesystem**, có thể là một mục hoàn toàn khác, thậm chí có thể trùng với tệp thật ngoài ZIP. Không có Java API nào tự động “giải nén và đổi sang physical path” khi nhận chuỗi trên. Sai lầm nguy hiểm nhất ở đây là thao tác **không ném lỗi** nhưng chuyển sang **khác tài nguyên**.

## 5. Cẩn thận với “đường dẫn tuyệt đối”, “chuẩn hóa” và “đường dẫn thực”

Ba thao tác này thường bị hiểu nhầm là cách “ép” `Path` thành đường dẫn vật lý:

| Phương thức | Điều nó làm | Điều nó không bảo đảm |
|---|---|---|
| `path.toAbsolutePath()` | Tạo đường dẫn tuyệt đối theo ngữ nghĩa của **file system hiện tại** | Không chứng minh tồn tại, không chuyển provider |
| `path.normalize()` | Loại bỏ phần tử tên dư như `.` / `..` theo quy tắc provider, chủ yếu xử lý cú pháp | Không yêu cầu I/O, không đảm bảo trỏ đến cùng mục nếu có symlink |
| `path.toRealPath()` | Yêu cầu đường dẫn hiện hữu và xác định đường dẫn thực theo provider, mặc định theo symlink | Không chuyển ZIP `Path` thành `File`, không có “physical path” chung mọi provider |
| `path.toUri()` | Tạo URI đại diện cho `Path` theo provider | Không chứng minh URI là `file:`, cũng không tự đổi thành `File` |
| `path.toString()` | Biểu diễn văn bản tên đường dẫn | Không mang đủ ngữ cảnh provider để tái dựng đường dẫn tổng quát |

Ví dụ:

```java
Path virtual = zipFs.getPath("/docs/../docs/readme.txt");
Path normalized = virtual.normalize();
Path absolute = normalized.toAbsolutePath();
Path real = normalized.toRealPath();
```

Đoạn minh họa cần `zipFs` còn mở và entry tồn tại. `normalized`, `absolute` và `real` vẫn là đường dẫn do ZIP provider quản lý. Một `Path` *absolute* ở ZIP root không biến thành đường dẫn tuyệt đối ở ổ C: hoặc Linux root. `toRealPath()` có thể ném `IOException` khi mục không tồn tại hoặc không thể truy cập; không dùng nó như phép cast.

**Đặc biệt với symlink:** `normalize()` thuần cú pháp có thể thay đổi đối tượng được tham chiếu. Chẳng hạn `dirLink/../target.txt` không nhất thiết đồng nghĩa với `target.txt` nếu `dirLink` là symbolic link trỏ sang thư mục khác. `toRealPath()` tra cứu hệ thống tệp và có tùy chọn `LinkOption.NOFOLLOW_LINKS`; tuy vậy nó vẫn trả `Path` thuộc provider gốc. Một lần `toRealPath()` cũng không đảm bảo đường dẫn không đổi trước thao tác I/O sau đó.

### 5.1. “Thực” không đồng nghĩa “cục bộ”

Ngay cả với `Path.of("/mnt/share/report.csv")` trên Linux, thư mục `/mnt/share` có thể được mount từ ổ đĩa mạng; trên Windows, ổ đĩa hoặc UNC path cũng có thể trỏ đến tài nguyên mạng. Trong container, tên đường dẫn có thể được map từ máy host. Do đó:

- `path.toFile()` thành công: **có phép chuyển tương thích với API cũ**.
- `Files.exists(path)` hoặc `Files.isRegularFile(path)` thành công: **có thể truy vấn trạng thái đối tượng theo provider tại thời điểm đó**.
- Không phương thức nào trong hai nhóm chứng minh vị trí lưu trữ là ổ đĩa vật lý cục bộ, được sở hữu độc quyền, hay không thể thay đổi bởi tác nhân khác.

## 6. `Path.of(...)`, `FileSystem.getPath(...)` và `Path.resolve(...)`

Khi viết thư viện nhận một `Path` từ caller, bạn cần bảo toàn ngữ cảnh provider thay vì vô tình ép lại vào default provider.

**Cách dễ gây lỗi:**

```java
static Path childBad(Path base, String filename) {
    return Path.of(base.toString(), filename);
}
```

`Path.of(String, String...)` luôn dùng default file system. Nếu `base` nằm trong ZIP hoặc provider khác, phương thức trên trả đường dẫn trong default provider và làm mất ý nghĩa ban đầu.

**Cách phù hợp:**

```java
static Path childGood(Path base, String filename) {
    return base.resolve(filename);
}
```

`base.resolve(filename)` dùng ngữ nghĩa của file system tạo ra `base`. Điều này thường là cách viết tốt nhất cho thư viện xử lý một cây thư mục có root do người gọi cung cấp.

Có thể chọn đường dẫn từ file system cụ thể:

```java
Path defaultPath = Path.of("docs", "guide.txt");
Path zipPath = zipFs.getPath("/docs", "guide.txt");
```

`defaultPath` luôn đến từ default FileSystem; `zipPath` đến từ `zipFs`. Đoạn minh họa yêu cầu `zipFs` đã mở. Nếu muốn thao tác giữa hai provider, dùng API được thiết kế cho việc đó (chẳng hạn `Files.copy` hỗ trợ sao chép giữa provider theo contract của nó), không hoán đổi các `Path` vào phép toán yêu cầu cùng một FileSystem, và không giả định mọi thuộc tính metadata có thể sao chép đầy đủ.

### 6.1. So sánh đường dẫn phải giữ ngữ cảnh

Hai `Path` đều in ra `/docs/readme.txt` không có nghĩa chúng biểu diễn cùng tệp. Một cái có thể là root Linux, một cái là entry trong ZIP. `Path.equals` tuân theo cách định nghĩa của provider; `Files.isSameFile` cần truy cập đối tượng và có thể không hỗ trợ mọi tổ hợp hoặc thất bại I/O. Không dùng duy nhất `toString()` như khóa định danh đa-provider.

## 7. URI cũng là tên tài nguyên, không phải vé thông hành sang `File`

`Path.toUri()` thuận tiện để biểu diễn đường dẫn với scheme:

```java
URI uri = somePath.toUri();
System.out.println(uri.getScheme());
```

Đoạn minh họa với `somePath` đã được khai báo. URI của `Path` mặc định thường có scheme `file`; ZIP file system thường dùng scheme `jar` với cú pháp ghép URI dành riêng provider. Vì vậy:

- `new File(URI)` chỉ nhận dạng URI phù hợp với contract của `java.io.File` (đặc biệt scheme `file` và những ràng buộc URI cụ thể). `new File(zipEntry.toUri())` **không** chuyển nội dung ZIP thành tệp hệ điều hành; có thể ném `IllegalArgumentException` vì scheme không hợp lệ.
- `Path.of(URI)` chọn provider theo scheme URI nếu được cài đặt và có thể mở/lấy file system phù hợp. Một URI thuộc provider khác có thể cần file system đã được khởi tạo. Thiếu provider/file system có thể phát sinh `FileSystemNotFoundException` hoặc lỗi khác của contract.
- `Path.of(somePath.toString())` không tương đương `Path.of(somePath.toUri())`: phiên bản chuỗi dùng default provider, còn phiên bản URI tra cứu provider theo scheme.
- Mỗi provider quyết định phần lớn ngữ nghĩa URI của mình. Đừng tự ghép scheme `file:` trước một đường dẫn tùy ý với hy vọng chuyển provider.

Một thiết kế API tốt là truyền **`Path` hoặc `URI` có ngữ cảnh** khi cần đa-provider, thay vì mã hóa mọi thứ thành chuỗi path mặc định.

## 8. Giải pháp khi thư viện cũ chỉ nhận `java.io.File`

Trong thực tế, có thư viện legacy đưa ra API kiểu:

```java
static void legacyImport(File input) {
    // Ví dụ chữ ký API cũ: chỉ biết java.io.File.
}
```

Giả sử caller cung cấp `Path input` bất kỳ. Có ba hướng xử lý tùy nhu cầu:

### 8.1. Ưu tiên API `Path`, `InputStream` hoặc `ReadableByteChannel`

Nếu thư viện có overload dùng `Path` hoặc `InputStream`, hãy dùng dạng thích hợp:

```java
static String loadText(Path source) throws IOException {
    return Files.readString(source);
}
```

Phương thức này làm việc với mọi provider triển khai thao tác đọc tương ứng; nó không cần `toFile()`. Nếu bên nhận chỉ cần bytes, có thể truyền `Files.newInputStream(source)` và quản lý vòng đời stream rõ ràng. Điều này bảo toàn nơi lưu trữ gốc và tránh file trung gian.

### 8.2. Chỉ chuyển trực tiếp khi có hỗ trợ

Nếu đã biết contract nghiệp vụ là đường dẫn từ default provider, có thể gọi:

```java
File legacyFile = input.toFile();
legacyImport(legacyFile);
```

Đây là minh họa với `input` là một `Path` hợp lệ; caller phải quyết định cách xử lý `UnsupportedOperationException` nếu có thể nhận path ngoài default provider. `toFile()` **không** tự xác nhận file có thể đọc/ghi; thư viện legacy cần xử lý lỗi I/O thực tế.

### 8.3. Nếu buộc phải có file vật lý: staging copy tạm

Khi thư viện thực sự chỉ hỗ trợ `File`, có thể chủ động **sao chép nội dung** sang tệp tạm thuộc default provider. Ví dụ đầy đủ cho chiều đọc:

```java
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LegacyBridge {
    @FunctionalInterface
    public interface FileConsumer {
        void accept(File file) throws IOException;
    }

    public static void withStagedFile(
            Path source, FileConsumer consumer) throws IOException {
        Path staging = Files.createTempFile("legacy-", ".bin");

        try {
            Files.copy(source, staging,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            consumer.accept(staging.toFile());
        } finally {
            Files.deleteIfExists(staging);
        }
    }
}
```

Đây là **thư viện hỗ trợ có thể biên dịch** (`LegacyBridge.java`), không có `main` và không in stdout; ví dụ caller phải cung cấp `FileConsumer`. Mã trên minh họa trường hợp nguồn có thể đọc được, không nhất thiết là tệp thường, và consumer chỉ đọc staging file.

Những điều phải thiết kế rõ trước khi áp dụng trong production:

- `staging` là **bản sao**, không còn là cùng đối tượng với source. Đường dẫn, thuộc tính, owner, file key và cập nhật sau đó có thể khác.
- Consumer sửa staging file **không tự cập nhật** entry gốc. Nếu nghiệp vụ cần ghi ngược, phải có luồng đồng bộ riêng, điều kiện lỗi và khả năng xung đột rõ ràng.
- Việc sao chép có chi phí bộ nhớ đệm, I/O, dung lượng đĩa, và có thể thất bại với tài nguyên lớn hoặc quyền hạn chế.
- `createTempFile` chỉ chọn nơi lưu tạm mặc định; chính sách quyền, thư mục staging, dọn dẹp khi process chết, xử lý thông tin nhạy cảm và tránh race cần được thiết kế theo ứng dụng.
- Consumer không được giữ `File` để dùng **sau khi callback kết thúc** vì đoạn `finally` sẽ xóa staging file. Không trả `File` ra ngoài rồi xóa ngay lập tức.
- Nếu code thực sự cần tương tác trực tiếp với hệ thống tệp trong archive hoặc provider đặc biệt, hãy thay thư viện/adapter bằng API `Path` thay vì ngụy tạo một tên file mặc định.

## 9. Trường hợp thường gặp và nhận định đúng

| Tình huống | Có thể `toFile()`? | Điểm cần hiểu |
|---|---|---|
| `Path.of("hello.txt")` trên default provider | Có | Tên có thể tương đối và file có thể chưa tồn tại |
| `Paths.get("/tmp/hello.txt")` trên default provider Unix | Có | “Tuyệt đối” không xác nhận file tồn tại |
| `File("hello.txt").toPath()` | Có theo default provider | Thuận lợi khi hiện đại hóa API cũ |
| ZIP/JAR entry lấy từ `zipFs.getPath(...)` | Không, ném `UnsupportedOperationException` | Dữ liệu nằm trong một file system riêng |
| Path từ provider bên thứ ba | Không theo contract nếu không phải default provider | Có thể hỗ trợ `Files` nhưng không chuyển thành `File` |
| `zipEntry.toRealPath()` | Không | `toRealPath()` vẫn giữ provider |
| `Path` mặc định tới ổ đĩa mạng hoặc mount | Có | Không kết luận file nằm trên ổ cứng cục bộ |
| `Path.of(zipEntry.toString())` | Có vì tạo Path mới trên default provider | **Nguy cơ đổi sang một đối tượng khác**, không phải chuyển ZIP entry |

**Lưu ý thiết kế:** Nếu chương trình thực sự yêu cầu tệp thường tồn tại để đọc, biểu diễn đúng nhất là *thử đọc rồi xử lý kết quả/lỗi* (hoặc đọc metadata theo yêu cầu); không lấy thành công của `toFile()` làm điều kiện tồn tại.

## 10. Ngoại lệ và những nhầm lẫn phổ biến

### 10.1. Các loại lỗi khác nhau

- `UnsupportedOperationException` từ `Path.toFile()`: provider không tương thích với `File`. Không đồng nghĩa “file không tồn tại”.
- `NoSuchFileException`, `AccessDeniedException` hoặc `IOException` từ `Files`: lỗi khi truy cập tài nguyên theo contract I/O của provider.
- `FileSystemNotFoundException` hoặc `ProviderNotFoundException` từ API tạo/lấy file system theo URI: provider hoặc phiên file system cần thiết không có.
- `ClosedFileSystemException`: thao tác dùng file system đã đóng (đặc biệt với ZIP system được quản lý qua `try-with-resources`).
- `InvalidPathException` từ `Path.of(String)`: chuỗi không phù hợp quy tắc parser của default provider.
- `ProviderMismatchException`: đưa `Path` của provider không tương thích vào thao tác yêu cầu cùng provider (tùy API).

Đừng gom tất cả thành “sai đường dẫn” vì cách khắc phục khác nhau. Khi bắt ngoại lệ, phải biết API nào phát sinh và có nên thử nguồn khác, báo lỗi người dùng hay dừng nghiệp vụ.

### 10.2. Danh sách lỗi tư duy cần tránh

1. **“Có `Path` tức có file thật.”** `Path` có thể trỏ tới tên không tồn tại, ZIP entry hoặc nguồn đặc biệt.
2. **“`toFile()` là một phép cast luôn thành công.”** Nó chỉ hỗ trợ default provider.
3. **“`toAbsolutePath()` biến đường dẫn thành physical path.”** Nó chỉ chuyển thành đường dẫn tuyệt đối *trong cùng FileSystem*.
4. **“`normalize()` hoặc `toRealPath()` đổi provider.”** Hai phương thức giữ provider gốc; ngữ nghĩa và I/O khác nhau.
5. **“`new File(path.toString())` tương đương `path.toFile()`.”** Với provider khác, bản `File` mới âm thầm trỏ vào default filesystem.
6. **“URI có chữ `file` ở đâu đó là đủ.”** File URI, jar URI, URI ghép và cú pháp provider riêng là những cơ chế khác nhau.
7. **“Chuỗi slash giống nhau tức cùng file.”** Cần xét provider và trạng thái thật; tên có thể giống nhưng tài nguyên khác.
8. **“`File` là file vật lý và luôn tồn tại.”** `File` cũng chỉ là tên trừu tượng; phương thức kiểm tra không loại trừ thay đổi đồng thời.
9. **“Bản staging copy giữ nguyên định danh/metadata.”** Copy được dữ liệu theo policy, nhưng không còn cùng đối tượng và cần bảo đảm dọn dẹp.
10. **“Mở ZIP xong có thể dùng mãi `Path` trong ZIP.”** Đóng `FileSystem` làm nhiều thao tác truy cập tiếp theo không còn hợp lệ.

### 10.3. Thiết kế giao diện thư viện

Đối với một hàm chỉ cần đọc dữ liệu, ưu tiên:

```java
String read(Path source) throws IOException;
```

Với một hàm chỉ xử lý luồng byte và không cần tên tệp, đôi khi API:

```java
void parse(java.io.InputStream input) throws IOException;
```

là đủ; caller sẽ tự mở/đóng stream theo contract. Với hệ thống đa-provider, **không** gắn giả định default provider vào logic sâu bằng cách gọi `Path.of(input.toString())` hoặc `new File(input.toString())`. Giữ nguyên `Path` từ caller và thao tác `resolve`/`Files` theo provider của nó.

## 11. Tự kiểm tra

1. **Đúng hay sai?** `Path.of("foo.txt").toFile()` chỉ thành công nếu `foo.txt` tồn tại.  
   **Sai.** `toFile()` là chuyển biểu diễn trên default provider, không kiểm tra tồn tại.
2. **Điều gì xảy ra?** `zipFs.getPath("/images/a.png").toFile()`?  
   **`UnsupportedOperationException`**, vì ZIP `Path` không thuộc default provider, cho dù entry tồn tại.
3. **Có thể sửa bằng cách nào?** `new File(zipEntry.toString())`?  
   **Không.** Điều đó diễn giải chuỗi trên default filesystem và có thể trỏ sang đối tượng khác.
4. **Một `Path` trả từ `toRealPath()` có chắc chuyển được thành `File` không?**  
   **Không.** `toRealPath()` vẫn trả đường dẫn trong provider gốc.
5. **Nếu một thư viện chỉ nhận `File` nhưng đầu vào là ZIP entry?**  
   Ưu tiên overload nhận `Path` hoặc `InputStream`; nếu bắt buộc, chép dữ liệu sang staging file ở default provider với vòng đời và đồng bộ riêng.
6. **Một `File` từ đường dẫn mạng có phải file cục bộ?**  
   Không thể kết luận chỉ từ loại `File`/`Path`; default provider có thể truy cập các mount và chia sẻ mạng.
7. **Cần ghép tên tệp con vào `Path base` chưa rõ provider?**  
   Dùng `base.resolve(child)` để giữ ngữ nghĩa file system của `base`.

## 12. Ghi nhớ và tài liệu chính thức

Nguyên tắc trọng tâm: **`Path` thuộc một file system; `java.io.File` thuộc mô hình đường dẫn truyền thống của default provider. `Path.toFile()` chỉ là cầu nối cho default provider, không phải phép biến mọi nguồn dữ liệu thành tệp vật lý.** Nếu nhu cầu là xử lý nội dung, hãy ưu tiên `Files`/`Path`/stream. Nếu bắt buộc tương tác với API legacy, xác định có chuyển trực tiếp được không hoặc dùng staging copy có kiểm soát.

Tài liệu tham khảo chính thức (Java SE 21):

- `Path` — đặc biệt `toFile`, `toUri`, `toRealPath` và `Path.of`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Path.html
- `FileSystems` — default provider và mở file system mới: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/FileSystems.html
- `Files` — I/O qua `Path`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html
- `File` — tên đường dẫn trừu tượng và `toPath`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/File.html
- Module `jdk.zipfs` — ZIP/JAR FileSystem Provider: https://docs.oracle.com/en/java/javase/21/docs/api/jdk.zipfs/module-summary.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-systems-009">Khả năng mở rộng provider và ranh giới của Java API</a>

<details>
<summary>Xem chi tiết</summary>

# Khả năng mở rộng FileSystemProvider và ranh giới của Java API

## 1. Vấn đề: chung API không có nghĩa chung mọi khả năng

Java NIO.2 (`java.nio.file`) cho phép viết mã dựa trên `Path` và `Files` để truy cập các hệ thống tệp khác nhau: hệ thống tệp mặc định của OS, nội dung ZIP/JAR và những backend do thư viện bên thứ ba triển khai. Cơ chế đó dựa trên **FileSystemProvider**, một Service Provider Interface (SPI) mở rộng được.

Ví dụ, cùng một lời gọi `Files.readString(path)` có thể đọc một file trên ổ đĩa hay một entry trong ZIP, **miễn là provider của `path` hỗ trợ thao tác cần thiết**.

Phải phân biệt ba lớp trách nhiệm:

1. **Java API** định nghĩa giao diện, kiểu dữ liệu, các quy tắc và ngoại lệ được tài liệu hóa.
2. **File-system provider** biến yêu cầu từ API thành thao tác của backend cụ thể và công bố tính năng mà nó hỗ trợ.
3. **File system/file store thực tế** quyết định hiệu năng, quyền truy cập, loại thuộc tính, tính nguyên tử và những giới hạn nền tảng.

Do đó, một phương thức xuất hiện trong `Files` không chứng minh mọi hệ thống tệp đều có cùng chức năng hay bảo đảm hành vi.

## 2. Những thành phần tạo nên kiến trúc

| Thành phần | Vai trò | Ví dụ |
|---|---|---|
| `Path` | Biểu diễn đường dẫn gắn với một `FileSystem` cụ thể | `Path.of("data.txt")` hoặc `zipFs.getPath("/data.txt")` |
| `FileSystem` | Quản lý root, separator, provider, attribute views và vòng đời của một hệ thống tệp | `FileSystems.getDefault()`, file system ZIP |
| `FileSystemProvider` | SPI tạo/tìm file system, chuyển URI thành Path, thực thi các thao tác tệp cấp thấp | Provider scheme `file` và `jar` |
| `Files` | Các thao tác tiện lợi ở cấp API, thường dùng provider của các `Path` tương ứng | `readString`, `copy`, `walk`, `readAttributes` |
| `FileStore` | Thông tin kho lưu trữ do provider đại diện | `Files.getFileStore(path)` |

Luồng hoạt động ở mức khái niệm:

~~~text
Ứng dụng
   └── Path / Files / FileSystems (API Java NIO.2)
          └── FileSystemProvider (SPI)
                 └── Hệ thống tệp mặc định, ZIP hoặc backend cụ thể
~~~

Đây là sơ đồ kiến trúc; từng phương thức `Files` có thể phối hợp nhiều thao tác provider, không nhất thiết ủy quyền đúng một phương thức SPI.

Một `Path` luôn gắn với `FileSystem` của nó. Để biết nó thuộc provider nào:

~~~java
Path path = Path.of("data.txt");
FileSystem fs = path.getFileSystem();
System.out.println(fs.provider().getScheme()); // file với default provider
~~~

Đây là đoạn minh họa, không phải một chương trình độc lập. Tệp `data.txt` không cần tồn tại để tạo `Path`.

## 3. Mở rộng provider có nghĩa gì?

### 3.1. Provider mặc định

`FileSystems.getDefault()` trả về file system mặc định của JVM. Provider mặc định dùng URI scheme `file` và tạo các `Path` từ lời gọi thông thường như `Path.of("notes.txt")`.

Không đồng nhất scheme `file` với NTFS, ext4 hay APFS. Cùng provider mặc định có thể làm việc với các file store khác nhau, với quy tắc case sensitivity, quyền, khóa và độ chính xác timestamp phụ thuộc nền tảng.

### 3.2. Provider bổ sung

Một provider là lớp con cụ thể của `java.nio.file.spi.FileSystemProvider`, được nhận diện bằng URI scheme, đóng vai trò factory cho một hay nhiều `FileSystem`.

Ví dụ thực tế trong JDK 17: **module `jdk.zipfs` cung cấp ZIP file-system provider** với scheme `jar`, để đọc/ghi các entry trong ZIP/JAR như những `Path`.

Một thư viện khác *có thể* cung cấp backend in-memory, mạng hoặc object storage với scheme riêng (chẳng hạn `memory`). Các tên scheme giả định không phải khả năng có sẵn của Java SE; chúng chỉ có ý nghĩa nếu provider tương ứng đã được cài đặt và hỗ trợ.

Provider định nghĩa cách phân tích URI, đường dẫn, nội dung, thuộc tính tệp và các thao tác I/O. Nhờ đó mã ứng dụng có thể tái sử dụng nhiều thao tác `Files`, trong phạm vi hợp đồng mà provider hiện thực hóa.

### 3.3. ZIP là ví dụ dễ hiểu

ZIP provider cho phép sử dụng `Path` bên trong một tệp ZIP:

~~~java
URI uri = URI.create("jar:" + archive.toUri());
try (FileSystem zipFs =
         FileSystems.newFileSystem(uri, Map.of("create", "true"))) {
    Path entry = zipFs.getPath("/notes.txt");
    Files.writeString(entry, "Xin chao");
}
~~~

Đây là đoạn minh họa trong một phương thức đã có biến `Path archive` và xử lý `IOException`. `create=true` là **cấu hình của ZIP provider**, không phải tham số chung cho tất cả provider. Ví dụ đầy đủ, chạy được, có ở mục 6.

`FileSystem` mở riêng như ZIP nên được đóng với `try-with-resources`. Các `Path` trỏ đến file system đã đóng không thể tiếp tục I/O bình thường; đừng giữ `Path` ZIP sau khi đóng rồi giả định nó vẫn đọc được.

Một runtime image tùy biến (ví dụ tạo bằng `jlink`) có thể thiếu module `jdk.zipfs`. Khi đó, ứng dụng phải xử lý lỗi provider không khả dụng, thay vì giả định mọi bản phân phối Java đều có ZIP provider.

## 4. Tìm kiếm, nạp và khởi tạo provider

Những API liên quan:

- `FileSystems.getDefault()`: lấy file system mặc định.
- `FileSystemProvider.installedProviders()`: lấy danh sách provider đã được nạp theo SPI, gồm default provider.
- `FileSystems.newFileSystem(URI, Map)`: tìm provider theo URI scheme và yêu cầu tạo một file system theo tham số provider quy định.
- `FileSystems.getFileSystem(URI)`: lấy file system đã tồn tại; không phải lệnh tạo mới.
- `FileSystems.newFileSystem(Path)`: thử mở một tệp dưới dạng file system theo định dạng/provider khả dụng, ví dụ ZIP.
- `Path.of(URI)` hay `Paths.get(URI)`: đổi URI thành `Path` bằng provider. Tùy provider, file system tương ứng có thể phải được khởi tạo trước.

Ví dụ phân biệt:

~~~java
URI uri = URI.create("jar:file:/tmp/sample.zip");

// newFileSystem: tạo một phiên file system khi provider cho phép
FileSystem created = FileSystems.newFileSystem(uri, Map.of());

// getFileSystem: yêu cầu lấy file system đã tồn tại theo URI
FileSystem existing = FileSystems.getFileSystem(uri);
~~~

Đây là mã minh họa, **không phải đoạn chạy độc lập**. Nó giả định tệp ZIP có thật, provider `jar` khả dụng và `created` vẫn đang mở. Cú pháp đường dẫn URI cụ thể phụ thuộc OS/provider.

Những ngoại lệ thường gặp:

| Trường hợp | Ngoại lệ |
|---|---|
| Không có provider cho URI scheme | `ProviderNotFoundException` |
| Chưa có file system tương ứng khi gọi `getFileSystem` | `FileSystemNotFoundException` |
| Tạo trùng file system mà provider không cho phép | `FileSystemAlreadyExistsException` |
| File system đã đóng | `ClosedFileSystemException` |
| Dữ liệu/archive lỗi hoặc sự cố backend | `IOException` hoặc ngoại lệ cụ thể |

**Nạp plugin qua ServiceLoader**

JAR trên classpath thường có một lớp con concrete của `FileSystemProvider` với constructor không tham số, cùng tệp service descriptor:

~~~text
META-INF/services/java.nio.file.spi.FileSystemProvider
~~~

Nội dung tệp descriptor có một hay nhiều dòng là tên lớp provider đầy đủ, chẳng hạn `com.example.CustomProvider`. Tên này **chỉ để minh họa**, đòi hỏi lớp thật sự tồn tại và được triển khai đúng SPI.

Với named module, có thể khai báo trong `module-info.java`:

~~~java
provides java.nio.file.spi.FileSystemProvider
        with com.example.CustomProvider;
~~~

Đây chỉ là một khai báo JPMS minh họa, **không phải module hoàn chỉnh**. Module và lớp provider phải được build, cung cấp trên module path/runtime và triển khai hợp đồng SPI.

Provider bổ sung được khám phá qua `ServiceLoader`; thứ tự tải các provider không được ứng dụng tùy tiện phụ thuộc. Nếu trùng URI scheme với provider đã được chọn trước, provider trùng sau có thể bị bỏ qua; không thiết kế logic chọn provider theo vị trí danh sách.

**Mở rộng provider mặc định là trường hợp khác:** system property `java.nio.file.spi.DefaultFileSystemProvider` có thể chỉ định chuỗi lớp provider bọc provider mặc định. Những lớp này nhận một `FileSystemProvider` qua constructor một tham số và cùng scheme `file`. Đây không phải cơ chế cần dùng khi đơn giản muốn thêm scheme mới như `jar`.

## 5. Ranh giới giữa API chung và khả năng backend

### 5.1. Không phải mọi thao tác đều được hỗ trợ

| Nhu cầu | Vì sao cần kiểm tra provider/file store? |
|---|---|
| Symbolic link, hard link | Backend có thể không hỗ trợ hoặc không cấp quyền tạo link |
| POSIX owner/permissions, ACL | Chỉ có trên các attribute view được hỗ trợ |
| WatchService | Có thể không hỗ trợ, hoặc có cách thông báo khác nhau |
| File locking, memory mapping, async channel | Không phải provider nào cũng có đầy đủ tính năng kênh |
| `ATOMIC_MOVE` | Chỉ thực hiện được nếu backend hỗ trợ move nguyên tử trong hoàn cảnh đó |
| Thời gian sửa tệp và attribute copying | Độ chính xác, thuộc tính được copy phụ thuộc file store/provider |
| Case sensitivity, root, separator và path syntax | Là quy tắc của `FileSystem` |
| Latency, consistency và xử lý lỗi | Khác nhau giữa local FS, ZIP và backend từ xa |

Hỏi provider về capability thay vì suy luận theo tên OS:

~~~java
FileSystem fs = path.getFileSystem();
boolean supportsPosix =
        fs.supportedFileAttributeViews().contains("posix");
~~~

Đây là đoạn mã minh họa với `path` có sẵn. Muốn kiểm tra attribute view cụ thể cho một `Path`, có thể gọi `Files.getFileAttributeView(path, PosixFileAttributeView.class)`; có thể trả `null` nếu view không có. Việc hỗ trợ view không bảo đảm mọi thao tác quyền trên mọi tệp đều thành công.

### 5.2. Không được đánh mất hệ thống tệp của Path

Hai `Path` có chuỗi in ra giống nhau nhưng có thể thuộc hai provider khác nhau. Vì vậy:

- `Path.equals` không coi hai `Path` từ file system khác nhau là bằng nhau.
- `Files.isSameFile(a, b)` trả `false` nếu hai đường dẫn thuộc các provider khác nhau.
- Các phép như `resolve(Path)` với các đối số không tương thích có thể phát sinh `ProviderMismatchException`.
- `Path.of(zipEntry.toString())` tạo `Path` **mới trên default file system**, không còn là `Path` của ZIP.
- Dùng `zipFs.getPath("/entry.txt")` hoặc `directoryInZip.resolve("entry.txt")` để tiếp tục với ZIP provider.

Sử dụng API cùng tên không làm danh tính backend của hai `Path` trở thành một.

### 5.3. Copy giữa provider không đồng nghĩa thao tác nguyên tử

`Files.copy(source, target)` hỗ trợ trường hợp source và target thuộc những provider khác nhau. Java có thể phối hợp đọc từ provider nguồn và ghi tới provider đích.

Tuy nhiên, các bảo đảm cần hiểu rõ:

1. Copy tạo một tệp đích độc lập; nội dung giống không làm hai tệp thành cùng một tệp.
2. Copy không nguyên tử; nếu lỗi giữa chừng, đích có thể chưa hoàn chỉnh.
3. Copy attributes tùy nền tảng/provider/option, có thể mất độ chính xác timestamp hoặc không copy được một số thuộc tính.
4. `Files.copy` một thư mục **không tự copy đệ quy toàn bộ cây**; cần duyệt cây nếu muốn chuyển cả cấu trúc.
5. Move giữa các provider/file store có thể không thực hiện được hoặc cần cơ chế khác; `ATOMIC_MOVE` không được bảo đảm và có thể ném `AtomicMoveNotSupportedException`.

Một lời gọi API chung không tự biến ZIP/local/network thành cùng một transaction.

### 5.4. Từ chối đúng cách khi feature không tồn tại

Tùy phương thức và provider, Java có thể phát sinh `UnsupportedOperationException` cho option/feature/attribute view không được hỗ trợ, `IOException` cho lỗi I/O, `ProviderMismatchException` cho Path không tương thích, hoặc ngoại lệ cụ thể khác theo tài liệu API.

Không chuyển tất cả thành kết luận “file không tồn tại”. Lỗi thiếu provider, lỗi đóng file system, lỗi permission và lỗi I/O cần chiến lược xử lý khác nhau.

## 6. Ví dụ Java 17+ chạy được: local và ZIP provider

Chương trình sau tạo file local, tạo ZIP file system, copy tệp sang ZIP, rồi đóng và mở lại ZIP để chứng minh dữ liệu được lưu. Nó sử dụng JDK có module `jdk.zipfs`, không cần dependency ngoài.

**Tệp: `ProviderBoundaryDemo.java`**

~~~java
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class ProviderBoundaryDemo {
    public static void main(String[] args) throws IOException {
        Path work = Files.createTempDirectory("provider-demo-");
        Path source = work.resolve("source.txt");
        Path archive = work.resolve("bundle.zip");

        try {
            Files.writeString(source, "Xin chao", StandardCharsets.UTF_8);
            URI zipUri = URI.create("jar:" + archive.toUri());

            try (FileSystem zipFs =
                    FileSystems.newFileSystem(zipUri, Map.of("create", "true"))) {
                Path entry = zipFs.getPath("/message.txt");
                Files.copy(source, entry);

                System.out.println(
                        source.getFileSystem().provider().getScheme());
                System.out.println(
                        entry.getFileSystem().provider().getScheme());
                System.out.println(Files.readString(entry));
                System.out.println(Files.isSameFile(source, entry));
            }

            try (FileSystem reopened = FileSystems.newFileSystem(archive)) {
                System.out.println(
                        Files.readString(reopened.getPath("/message.txt")));
            }
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(archive);
            Files.deleteIfExists(work);
        }
    }
}
~~~

Biên dịch và chạy trong thư mục chứa file mã nguồn:

~~~bash
javac ProviderBoundaryDemo.java
java ProviderBoundaryDemo
~~~

**Stdout khi ZIP provider có sẵn:**

~~~text
file
jar
Xin chao
false
Xin chao
~~~

Giải thích kết quả:

1. `source` ở default provider (`file`).
2. `entry` nằm trong ZIP (`jar`).
3. Cùng API `Files.copy` và `Files.readString` thao tác qua hai provider khác nhau.
4. `Files.isSameFile` trả `false` do hai provider khác nhau.
5. Mở lại ZIP từ file sau khi đóng chứng minh `message.txt` vẫn được lưu trong archive.

Chương trình dùng tên thư mục tạm ngẫu nhiên nhưng không in ra, nên kết quả hiển thị ổn định. `finally` thực hiện dọn tệp; việc xóa vẫn có thể thất bại với `IOException` nếu backend/OS gặp lỗi. Khi runtime không có `jdk.zipfs`, việc mở `jar` có thể báo không tìm thấy provider.

## 7. Nếu tự viết FileSystemProvider thì cần làm những gì?

Một provider không chỉ là “adapter đọc file”. Nhà phát triển phải triển khai concrete `FileSystemProvider` và các kiểu `FileSystem`/`Path` có semantics nhất quán.

Những nhóm trách nhiệm chính:

- **Định danh/khởi tạo:** `getScheme()`, `newFileSystem(URI, env)`, `getFileSystem(URI)`, `getPath(URI)`.
- **Đường dẫn:** root, relative/absolute, tên và separator, so sánh/chuẩn hóa, vòng đời file system.
- **I/O:** `newByteChannel`, đọc/ghi dữ liệu, `newDirectoryStream`, tạo/xóa/copy/move file và directory.
- **Metadata:** `checkAccess`, `isSameFile`, `getFileStore`, `readAttributes`, `getFileAttributeView`, `setAttribute`, cùng khả năng hỗ trợ view.
- **Lỗi và concurrency:** ném ngoại lệ tương thích hợp đồng, xử lý đóng tài nguyên, I/O đồng thời, trạng thái tệp thay đổi.

Có phương thức SPI bắt buộc triển khai (`abstract`) và phương thức có mặc định có thể báo không hỗ trợ. Trước khi triển khai phải xem Javadoc từng phương thức, thay vì sao chép logic từ một loại backend rồi giả định áp dụng cho mọi hệ thống.

Với object storage, ví dụ “thư mục” có thể là cách biểu diễn tên key, rename có thể không nguyên tử, và danh sách file có tính chất khác POSIX. Nếu viết provider, cần mô tả rõ ranh giới về semantics, consistency và hiệu năng. Nếu chỉ cần truy cập ZIP, dùng `jdk.zipfs` sẵn có thường đơn giản hơn tự viết SPI.

## 8. Nguyên tắc viết code có thể làm việc với nhiều provider

- **Giữ nguyên `Path` và file system của nó:** truyền `Path` giữa các lớp, tránh đổi qua `toString()` rồi dựng lại bằng `Path.of(...)`.
- **Giải quyết đường dẫn con trong file system hiện tại:** `directory.resolve("child.txt")` hoặc `directory.getFileSystem().getPath(...)`. Tránh lắp ghép chuỗi separator theo OS.
- **Khảo sát capability trước tính năng đặc thù:** attribute views, file store, hỗ trợ link/watch/channel và atomic move.
- **Dùng `Files` khi hợp đồng đủ cho nhu cầu:** các thao tác đọc/ghi/copy có thể dùng chung, nhưng không giả định các tùy chọn bổ sung đều khả dụng.
- **Xử lý lỗi theo loại:** phân biệt không có provider, file system đã đóng, feature thiếu, permission và lỗi I/O.
- **Quản lý vòng đời:** đóng `FileSystem` được mở riêng như ZIP, channel, stream và `Stream<Path>` từ `Files.walk`. Không chủ động đóng file system mặc định của JVM.
- **Kiểm tra thực tế trên provider đích:** test filesystem behavior, atomicity, thuộc tính, hiệu năng. Unit test trên thư mục tạm local không thay thế được integration test của backend đặc thù.
- **Không bỏ qua chi phí:** provider từ xa có thể phát sinh nhiều request/network I/O từ các thao tác tưởng nhỏ như `exists`, `list` hoặc `readAttributes`.

## 9. Những hiểu lầm phổ biến

1. **“Có `Files.readString` thì mọi backend đọc được.”** Sai: provider phải hỗ trợ thao tác và dữ liệu phải truy cập được.
2. **“File bên trong ZIP và file local đều là `Path` nên có thể so/ghép tùy ý.”** Sai: hai file system/provider khác nhau.
3. **“`getFileSystem(uri)` tự tạo file system khi chưa có.”** Sai: nó lấy một file system đang tồn tại, khác `newFileSystem`.
4. **“`Files.copy` sẽ copy cả cây con và bảo toàn mọi thuộc tính.”** Sai: phải tự xử lý đệ quy; thuộc tính phụ thuộc provider.
5. **“`ATOMIC_MOVE` sẽ thành công giữa hai provider.”** Không được bảo đảm.
6. **“Chỉ cần service descriptor là có custom provider hoạt động.”** Sai: phải có lớp thực thi hợp đồng, đăng ký và đóng gói đúng.
7. **“Mọi runtime Java đều có `jdk.zipfs`.”** Runtime tối giản có thể loại module này.

## 10. Ghi nhớ

~~~text
Java API: mô tả cách ứng dụng yêu cầu thao tác.
FileSystemProvider: thực thi yêu cầu trên backend cụ thể.
Backend/file store: quyết định khả năng, giới hạn và chi phí thực tế.
~~~

Trước khi phụ thuộc vào một khả năng, hãy hỏi: **`Path` này thuộc provider nào? Hợp đồng Java cam kết gì? Provider/file store có thực sự hỗ trợ tính năng và mức bảo đảm mà ứng dụng cần không?**

### Tài liệu chính thức

- [Java 17: FileSystemProvider](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/spi/FileSystemProvider.html)
- [Java 17: FileSystems](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileSystems.html)
- [Java 17: Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [Java 17: Path](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html)
- [JDK 17: jdk.zipfs](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.zipfs/module-summary.html)


</details>

- [Quay lại đầu trang](#back-to-top)
