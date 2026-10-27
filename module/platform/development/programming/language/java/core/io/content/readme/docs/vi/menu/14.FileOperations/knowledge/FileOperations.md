<a id="back-to-top"></a>

# Thao tác tệp

## Menu
- [createFile, createDirectory và createDirectories](#file-operations-001)
- [createTempFile, createTempDirectory và phạm vi dọn dẹp](#file-operations-002)
- [copy tệp với REPLACE_EXISTING và COPY_ATTRIBUTES](#file-operations-003)
- [move, đổi tên và lựa chọn ATOMIC_MOVE](#file-operations-004)
- [delete khác deleteIfExists](#file-operations-005)
- [Xử lý tệp đích đã tồn tại và thư mục không rỗng](#file-operations-006)
- [Thao tác xuyên file system và giới hạn provider](#file-operations-007)
- [exists, notExists và trường hợp không xác định được trạng thái](#file-operations-008)
- [Không kiểm tra trước rồi mặc định thao tác sau sẽ thành công](#file-operations-009)
- [Xử lý lỗi từng bước trong các thao tác nhiều tệp](#file-operations-010)

## <a id="file-operations-001">createFile, createDirectory và createDirectories</a>

<details>
<summary>Xem chi tiết</summary>

# Files.createFile, createDirectory và createDirectories trong Java

Trong Java NIO.2, lớp `java.nio.file.Files` cung cấp ba thao tác thường gặp để **tạo tài nguyên mới** trên hệ thống tệp:

- `Files.createFile(path)`: tạo **một tệp trống**.
- `Files.createDirectory(path)`: tạo **một thư mục**; các thư mục cha phải tồn tại.
- `Files.createDirectories(path)`: tạo **thư mục được chỉ định và những thư mục cha còn thiếu**; không coi việc đích đã là thư mục hợp lệ là lỗi.

Tên ba phương thức rất giống nhau nhưng khác đáng kể ở loại tài nguyên, yêu cầu về thư mục cha, việc cho phép đích đã tồn tại và mức độ nguyên tử (atomicity). Chọn sai dễ tạo ra `FileAlreadyExistsException`, `NoSuchFileException` hoặc một cây thư mục chỉ được tạo một phần.

Bài học sử dụng `Path`/`Files` trên Java 17+ và các ví dụ tạo tài nguyên trong thư mục tạm để không đụng tới dữ liệu của người dùng.

## 1. Bảng phân biệt nhanh

| API | Tài nguyên được tạo | Có tạo thư mục cha còn thiếu? | Nếu đích đã tồn tại? | Dữ liệu ban đầu |
|---|---|---|---|---|
| `Files.createFile(path)` | Tệp thông thường | **Không** | Thất bại; thường là `FileAlreadyExistsException` | Tệp rỗng, kích thước 0 byte |
| `Files.createDirectory(path)` | Một thư mục | **Không** | Thất bại; thường là `FileAlreadyExistsException` | Thư mục mới rỗng |
| `Files.createDirectories(path)` | Một hoặc nhiều thư mục còn thiếu | **Có** | Thành công nếu đích đã là một thư mục phù hợp; thất bại nếu đích là tệp không phải thư mục | Các thư mục mới rỗng |

Cả ba phương thức đều trả về đối tượng `Path` tương ứng với đường dẫn đã truyền, giúp dùng tiếp trong chương trình. Các phương thức đều có thể ném `IOException` hoặc những ngoại lệ con tùy điều kiện thực tế.

**Quan trọng:** `createFile` không tạo nội dung văn bản, không ghi header, không mở sẵn một `OutputStream` cho người gọi. Nếu muốn ghi dữ liệu ngay khi tạo, có thể dùng `Files.writeString`/`Files.newOutputStream` với các `StandardOpenOption` phù hợp.

## 2. Path chỉ mô tả vị trí; Files mới tương tác với đĩa

`Path` là đối tượng biểu diễn đường dẫn. Chỉ tạo `Path` không tạo tệp/thư mục thật:

~~~java
// Mảnh mã minh họa; cần import java.nio.file.Path.
Path file = Path.of("data", "report.txt");
// Chưa có thao tác tạo file nào xảy ra ở đây.
~~~

Khi gọi `Files.createFile(file)`, Java mới yêu cầu file system tạo tài nguyên tương ứng. Đường dẫn có thể là:

- **Tương đối:** `Path.of("data", "report.txt")`, được diễn giải theo thư mục làm việc hoặc ngữ cảnh provider.
- **Tuyệt đối:** ví dụ `Path.of("data").toAbsolutePath().resolve("report.txt")`.
- **Đường dẫn từ provider khác:** ZIP file system, virtual file system hoặc file system tùy biến. Chúng có thể áp quy tắc khác về quyền, root, tên, khả năng hỗ trợ thuộc tính và thông báo lỗi.

Không tự nối `"/"` hoặc `"\\"` để tạo đường dẫn đa nền tảng. `Path.of("a", "b", "c")` và `resolve(...)` biểu diễn các thành phần theo provider tương ứng.

## 3. Files.createFile: chỉ tạo đúng một tệp trống

Chữ ký thông dụng:

~~~java
public static Path createFile(
        Path path, FileAttribute<?>... attrs) throws IOException
~~~

`attrs` là tham số tùy chọn để gán thuộc tính khi tạo, ví dụ quyền truy cập trên hệ thống hỗ trợ POSIX. Nếu chưa cần cấu hình thuộc tính, chỉ gọi `Files.createFile(path)`.

Ví dụ:

~~~java
// Mảnh mã minh họa; thư mục logs phải tồn tại trước.
Path file = Path.of("logs", "app.log");
Files.createFile(file);
~~~

### Hành vi cần ghi nhớ

1. Tạo **một tệp mới, trống**, tại đúng `path`; không tạo thư mục cha `logs` nếu nó chưa có.
2. Thao tác kiểm tra tên chưa tồn tại và tạo tệp được thực hiện **nguyên tử đối với các thao tác file system khác có tác động lên cùng thư mục** theo hợp đồng `Files.createFile`. Điều này giúp tránh kiểu race “kiểm tra `exists` trước, rồi mới create”.
3. Nếu tên đích đã được dùng bởi tệp hoặc thư mục, thao tác thất bại. `FileAlreadyExistsException` là loại lỗi thường gặp và là ngoại lệ cụ thể có thể được provider sử dụng.
4. Nếu thư mục cha không có hoặc không phải thư mục hợp lệ, thao tác thất bại; thường gặp `NoSuchFileException` hoặc ngoại lệ I/O khác tùy file system.
5. Không tự tạo thư mục cha, không ghi byte vào file, không mở luồng ghi trả về cho người gọi.
6. Tệp tồn tại trên đĩa sau khi phương thức trả về thành công; muốn xóa cần thao tác riêng (`Files.delete` hoặc `Files.deleteIfExists`).

Hãy phân biệt **tạo tệp** với **mở tệp để ghi**:

~~~java
// Mảnh mã minh họa: file là Path đã xác định.
Files.createFile(file);        // Tạo tệp rỗng; lỗi nếu đã tồn tại.
Files.writeString(file, "Hi"); // Ghi dữ liệu với các open option mặc định.
~~~

Hai thao tác riêng có thể bị tác nhân khác can thiệp giữa chúng. Nếu nghiệp vụ là **tạo mới và ghi ngay, không cho ghi đè**, cân nhắc mở tệp trực tiếp với `StandardOpenOption.CREATE_NEW` thay vì tạo trước rồi mới mở.

## 4. Files.createDirectory: tạo một cấp, cha bắt buộc đã tồn tại

Chữ ký tương tự:

~~~java
public static Path createDirectory(
        Path dir, FileAttribute<?>... attrs) throws IOException
~~~

Ví dụ:

~~~java
// Mảnh mã minh họa; thư mục work phải tồn tại.
Files.createDirectory(Path.of("work", "reports"));
~~~

Nếu `work` tồn tại và cho phép tạo con, phương thức tạo đúng thư mục `reports`. Nếu `work` chưa tồn tại, `createDirectory` không âm thầm sinh ra `work`: thao tác sẽ thất bại.

Ví dụ với nhiều cấp:

~~~text
workspace/
    (chưa có thư mục 'images')
~~~

Gọi:

~~~java
Files.createDirectory(Path.of("workspace", "images", "icons"));
~~~

sẽ thất bại khi `workspace/images` chưa tồn tại. `createDirectory` chỉ tạo cấp cuối cùng; nó không có cơ chế sinh toàn bộ chuỗi thư mục cha.

Nếu đích đã tồn tại, kể cả khi chính nó là một thư mục hợp lệ, `createDirectory` vẫn không coi đây là yêu cầu “đảm bảo thư mục có mặt”. Nó cần tạo thư mục **mới** và thường ném `FileAlreadyExistsException` khi không tạo được vì trùng tên.

## 5. Files.createDirectories: tạo cả các thư mục cha còn thiếu

`Files.createDirectories(dir)` là lựa chọn khi cần bảo đảm tồn tại một cây thư mục theo đường dẫn, chẳng hạn `data/2026/reports`:

~~~java
// Mảnh mã minh họa:
Path reports = Path.of("data", "2026", "reports");
Files.createDirectories(reports);
~~~

Nếu `data` và `data/2026` còn thiếu, phương thức lần lượt tạo những thư mục cần thiết; nếu một hoặc nhiều cấp đã tồn tại dưới dạng thư mục, chúng có thể được tái sử dụng.

### Điểm khác quan trọng với createDirectory

- Nếu thư mục đích **đã tồn tại và là thư mục hợp lệ**, `createDirectories` thường hoàn thành bình thường, không buộc báo lỗi.
- Nếu một thành phần cần dùng làm thư mục **thực chất là một tệp thường**, không thể tạo thư mục con xuyên qua nó; phương thức thất bại.
- Nếu không đủ quyền, đường dẫn không hợp lệ, thiết bị lưu trữ lỗi hoặc gặp provider không hỗ trợ thao tác, vẫn có thể ném ngoại lệ.
- **Không nguyên tử đối với toàn bộ cây**: nếu đang tạo nhiều cấp và thất bại ở một cấp sau, **một số thư mục cha có thể đã được tạo**. Không có rollback tự động xóa những cấp đã thành công.

Vì vậy `createDirectories` phù hợp để chuẩn bị cấu trúc thư mục thông thường, nhưng không phải “transaction” bảo đảm tất cả-or-không-gì-cả. Nếu cần dọn dẹp sau lỗi, phải xác định rõ thư mục nào do chính lần chạy tạo và liệu còn tác vụ khác đang dùng chúng.

## 6. Ví dụ chạy được: cả ba phương thức

Tạo file **`CreatePathsDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CreatePathsDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("create-demo-");
        Path reports = root.resolve("reports");
        Path nested = root.resolve("data").resolve("2026").resolve("exports");
        Path report = reports.resolve("summary.txt");

        try {
            Files.createDirectory(reports);
            System.out.println("reportsDir=" + Files.isDirectory(reports));

            Files.createDirectories(nested);
            System.out.println("nestedDir=" + Files.isDirectory(nested));

            Files.createFile(report);
            System.out.println("fileExists=" + Files.isRegularFile(report));
            System.out.println("fileSize=" + Files.size(report));

            // Đích đã là thư mục: không phải lỗi với createDirectories.
            Files.createDirectories(nested);
            System.out.println("nestedAgain=" + Files.isDirectory(nested));

            try {
                Files.createFile(report);
            } catch (FileAlreadyExistsException ex) {
                System.out.println("duplicateFile=FileAlreadyExistsException");
            }
        } finally {
            Files.deleteIfExists(report);
            Files.deleteIfExists(nested);
            Files.deleteIfExists(nested.getParent());
            Files.deleteIfExists(nested.getParent().getParent());
            Files.deleteIfExists(reports);
            Files.deleteIfExists(root);
        }
    }
}
~~~

Biên dịch và chạy với JDK 17+:

~~~bash
javac -encoding UTF-8 CreatePathsDemo.java
java CreatePathsDemo
~~~

Stdout trên hệ thống tệp mặc định thông dụng:

~~~text
reportsDir=true
nestedDir=true
fileExists=true
fileSize=0
nestedAgain=true
duplicateFile=FileAlreadyExistsException
~~~

Giải thích:

1. `Files.createTempDirectory(...)` tạo thư mục tạm có tên duy nhất; chương trình không đoán một đường dẫn cố định có thể đã tồn tại.
2. `createDirectory(reports)` tạo một cấp `reports` vì cha `root` đã có.
3. `createDirectories(nested)` tạo ba cấp `data/2026/exports` nếu cần.
4. `createFile(report)` tạo tệp `summary.txt` với kích thước ban đầu 0 byte.
5. Gọi `createDirectories(nested)` lần thứ hai vẫn được vì `nested` đang là thư mục.
6. Gọi `createFile(report)` lần thứ hai không được vì tên tệp đã tồn tại; trên default file system lỗi cụ thể là `FileAlreadyExistsException`.
7. `finally` xóa tệp trước, rồi lần lượt xóa các thư mục từ sâu lên nông. `Files.deleteIfExists` **không xóa đệ quy** một thư mục không rỗng; các bước chủ ý được viết theo thứ tự thích hợp.

Nếu chương trình có tác nhân khác đồng thời sửa vùng tạm hoặc thiếu quyền hệ thống, mọi ví dụ thao tác tệp đều có thể thất bại vì lý do I/O; stdout ở đây dành cho môi trường chạy được với file system bình thường.

## 7. Ví dụ đối chiếu: thư mục cha thiếu và tên đích đã tồn tại

Giả sử `root` là một thư mục hiện có. Phương thức `createFile(root/missing/a.txt)` không thể tự tạo `missing`, và `createDirectory(root/missing/leaf)` cũng không thể tự tạo `missing`.

Tạo file **`CreateFailuresDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CreateFailuresDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("create-errors-");
        Path missing = root.resolve("missing");
        Path file = root.resolve("marker.txt");

        try {
            try {
                Files.createFile(missing.resolve("a.txt"));
            } catch (NoSuchFileException ex) {
                System.out.println("createFile: missing parent");
            }

            try {
                Files.createDirectory(missing.resolve("leaf"));
            } catch (NoSuchFileException ex) {
                System.out.println("createDirectory: missing parent");
            }

            Files.createDirectories(missing.resolve("leaf"));
            System.out.println("createDirectories: nested exists="
                    + Files.isDirectory(missing.resolve("leaf")));

            Files.createFile(file);
            try {
                Files.createDirectory(file);
            } catch (FileAlreadyExistsException ex) {
                System.out.println("createDirectory: target exists");
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(missing.resolve("leaf"));
            Files.deleteIfExists(missing);
            Files.deleteIfExists(root);
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 CreateFailuresDemo.java
java CreateFailuresDemo
~~~

Stdout với default file system Unix/Windows:

~~~text
createFile: missing parent
createDirectory: missing parent
createDirectories: nested exists=true
createDirectory: target exists
~~~

Đây là minh họa các ngoại lệ chuyên biệt phổ biến; **hợp đồng của một số ngoại lệ cụ thể được Javadoc ghi là tùy chọn theo triển khai**. Trong ứng dụng hỗ trợ nhiều provider, vẫn cần xử lý `IOException` khi không nhận đúng subclass mong đợi.

Không viết logic dựa duy nhất vào thông báo ngoại lệ (message) vì nội dung có thể tùy OS/ngôn ngữ/triển khai; ưu tiên loại ngoại lệ, trạng thái ứng dụng và thao tác đã yêu cầu.

## 8. Thao tác nguyên tử và lỗi race: “check rồi create” không an toàn

Mẫu code có vấn đề:

~~~java
// Mảnh mã minh họa; không an toàn trước cạnh tranh đồng thời.
if (Files.notExists(file)) {
    Files.createFile(file);
}
~~~

Giữa `Files.notExists(file)` và `Files.createFile(file)`, một thread/process khác có thể tạo chính tệp đó. `Files.notExists` chỉ phản ánh quan sát tại thời điểm kiểm tra; hơn nữa, `exists/notExists` không phải hai phép phủ định hoàn hảo trong trường hợp không thể xác minh sự tồn tại.

Nếu mục tiêu là “tạo tệp mới, không bao giờ ghi đè dữ liệu hiện có”, gọi trực tiếp `Files.createFile(file)` rồi xử lý ngoại lệ thích hợp. Theo hợp đồng, hành động kiểm tra tên và tạo tệp của `createFile` là nguyên tử đối với thao tác file system khác ảnh hưởng đến thư mục.

Tương tự, `Files.createDirectory(dir)` yêu cầu tạo **một thư mục mới**, không dùng mẫu “nếu không tồn tại thì tạo” để suy ra chắc chắn sẽ thành công. Nếu chấp nhận “thư mục đã tồn tại” là trạng thái hợp lệ, `Files.createDirectories(dir)` thường đúng ý định hơn.

Tuy nhiên, nguyên tử của **tạo một đối tượng** không phải nguyên tử của **một chuỗi nhiều đối tượng**. `createDirectories(a/b/c)` có thể để lại `a` hoặc `a/b` sau khi lỗi tại cấp sâu hơn. Khi cleanup, không được xóa bừa thư mục mà thread/process khác đã tạo hoặc đang dùng.

## 9. Muốn vừa tạo file vừa ghi nội dung: dùng CREATE_NEW

Nếu cần ghi byte/text vào một file **chỉ khi file chưa tồn tại**, có thể yêu cầu API mở/ghi dùng `StandardOpenOption.CREATE_NEW`. Nó gộp chính sách tạo mới với thao tác mở trong một quy trình, tránh hai lời gọi `createFile` rồi `writeString` có khoảng trống race.

Tạo file **`CreateAndWriteDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class CreateAndWriteDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("fresh-write-");
        Path file = root.resolve("result.txt");

        try {
            Files.writeString(
                    file,
                    "Hello\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);

            System.out.println("content="
                    + Files.readString(file, StandardCharsets.UTF_8).strip());

            try {
                Files.writeString(
                        file,
                        "Overwrite?",
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException ex) {
                System.out.println("secondWrite=refused");
            }
            System.out.println("unchanged="
                    + Files.readString(file, StandardCharsets.UTF_8)
                            .equals("Hello\n"));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(root);
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 CreateAndWriteDemo.java
java CreateAndWriteDemo
~~~

Stdout:

~~~text
content=Hello
secondWrite=refused
unchanged=true
~~~

Kết quả minh họa:

- Lần ghi đầu tạo tệp với nội dung UTF-8. `CREATE_NEW` yêu cầu tạo mới và không cho dùng một file đã tồn tại làm đích.
- Lần ghi thứ hai bị từ chối, nên dữ liệu `Hello\n` vẫn nguyên vẹn.
- `WRITE` diễn đạt mục đích ghi. Khi ghi text bằng `Files.writeString`, phải chọn charset tường minh theo định dạng cần lưu (ở đây là UTF-8).
- Nếu chỉ dùng `Files.writeString(file, text)` mà không chọn options, API mặc định có thể tạo tệp chưa tồn tại nhưng **truncate** tệp đã có. Nó **không** mang ý nghĩa “chỉ tạo mới”.
- Cần thư mục cha có sẵn. Nếu cha chưa có, hãy tạo cấu trúc thư mục bằng `createDirectories(parent)` với đúng chính sách trước khi ghi.

Một lựa chọn khác là `Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)` rồi ghi byte theo khối khi dữ liệu lớn, nhưng phải đóng stream bằng `try-with-resources`.

## 10. Thuộc tính ban đầu và quyền truy cập

Cả ba phương thức tạo tệp/thư mục có tham số biến đổi `FileAttribute<?>... attrs`. Mục tiêu là đặt những thuộc tính hỗ trợ **ngay khi tạo**, tránh có một khoảng thời gian tài nguyên đã tồn tại với quyền mặc định không mong muốn.

Ví dụ trên file system hỗ trợ POSIX:

~~~java
// Mảnh mã minh họa; chỉ dùng khi file system hỗ trợ POSIX.
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.FileAttribute;

FileAttribute<?> privateDir =
        PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rwx------"));
Files.createDirectory(path, privateDir);
~~~

Đoạn trên **không phải một chương trình Java hoàn chỉnh**: `import` phải được đặt đầu file, và biến `path` phải được khai báo. Mục đích là cho thấy tạo một thư mục với bộ quyền POSIX `rwx------` (chỉ chủ sở hữu được đọc/ghi/truy cập theo ý nghĩa POSIX). Trên Windows/default provider không hỗ trợ view POSIX, cách này có thể không phù hợp hoặc ném `UnsupportedOperationException`; không suy diễn rằng mọi provider đều dùng quyền POSIX.

Với `createDirectories(path, attrs)`, thuộc tính được đưa vào các lần tạo thư mục theo hợp đồng API, nhưng tập thuộc tính được hỗ trợ và quyền hiệu lực còn chịu tác động của provider, ACL, umask và quy tắc hệ điều hành. **Hãy xác minh quyền thực tế nếu ứng dụng xử lý dữ liệu nhạy cảm.**

Mặt khác, nếu tài nguyên **đã tồn tại**, `createDirectories` không phải API cập nhật quyền của thư mục cũ. Muốn đổi quyền phải dùng API thuộc tính khác với kiểm soát quyền phù hợp.

## 11. Đường dẫn đã tồn tại và symbolic link

Khi kiểm tra trạng thái, phân biệt:

- **Tệp thường** tại đích: `createFile`/`createDirectory` không thể tạo thêm đối tượng mới cùng tên; `createDirectories` không thể coi tệp là thư mục.
- **Thư mục đã tồn tại**: `createDirectories` có thể thành công mà không tạo lại; `createDirectory` yêu cầu mới nên thất bại.
- **Symbolic link**: đường dẫn có thể là link trỏ đến tệp hoặc thư mục khác. Các phép kiểm tra như `Files.isDirectory(path)` mặc định có thể **đi theo link**; chính sách follow link cần được xác định theo nghiệp vụ và API.
- **Broken symbolic link**: đường dẫn link tồn tại ở mức tên, nhưng đích của link có thể không có. Không đánh đồng với một chỗ trống an toàn để tạo file mới.

Nếu chương trình xử lý path do người dùng không đáng tin cung cấp, đừng dùng một chuỗi `exists`/`normalize`/`createDirectories` rồi mặc nhiên kết luận an toàn. Có rủi ro **TOCTOU** (kiểm tra rồi sử dụng sau), symbolic link thay đổi giữa các thao tác, hoặc thư mục cha trỏ ra ngoài vùng cho phép.

Ví dụ một ứng dụng chỉ được phép ghi trong `workspace` cần có chính sách rõ ràng về đường dẫn tuyệt đối, `..`, symlink, quyền, đối tượng đã tồn tại và thời điểm mở tệp. Phép kiểm tra lexical `candidate.normalize().startsWith(base)` hữu ích nhưng **không đủ một mình** để bảo vệ hệ thống tệp chống đối tượng cạnh tranh thay đổi symlink.

## 12. Xử lý ngoại lệ và chính sách retry

Các ngoại lệ thường gặp:

| Ngoại lệ/tình huống | Nguồn nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| `FileAlreadyExistsException` | `createFile`/`createDirectory` bị trùng tên | Từ chối hoặc chọn tên khác; đừng tự ghi đè |
| `NoSuchFileException` | Thiếu thư mục cha hoặc thành phần đường dẫn | Xác minh cha; dùng `createDirectories` nếu nghiệp vụ cho phép |
| `AccessDeniedException` | Thiếu quyền đọc/ghi/tạo tại vị trí | Điều chỉnh quyền hoặc báo lỗi; không retry vô hạn |
| `SecurityException` | SecurityManager hoặc chính sách bảo mật triển khai có thể từ chối | Xử lý theo chính sách quyền của ứng dụng |
| `UnsupportedOperationException` | Provider không hỗ trợ thuộc tính yêu cầu khi tạo | Chọn tập thuộc tính tương thích hoặc xử lý riêng theo platform |
| `IOException` khác | Không đủ dung lượng, đích không phải thư mục, I/O failure, race, tài nguyên bị thay đổi | Ghi log đúng ngữ cảnh, không giả định chỉ có một nguyên nhân |

Các subclass cụ thể đôi khi là **optional specific exception** trong Javadoc, vì thế khi cần hỗ trợ provider bất kỳ, phải có nhánh xử lý `IOException` chung. Không xử lý lỗi bằng cách bắt hết rồi bỏ qua hoặc tạo một cây thư mục bất kể mục đích ban đầu.

Đặc biệt với `createDirectories`: khi có lỗi ở giữa, một số cấp có thể tồn tại. Khi retry, phương thức thường tiếp tục được nếu các cấp đó là thư mục phù hợp. Nhưng nếu đã có tệp không phải thư mục ở một cấp hoặc trạng thái file system biến đổi, retry có thể vẫn thất bại. Vì thế **retry phải có lý do, giới hạn và chiến lược cleanup/rollback phù hợp**.

## 13. Những nhầm lẫn phổ biến

1. **Nhầm `Path.of(...)` với thao tác tạo tệp:** `Path.of` chỉ biểu diễn đường dẫn, không đụng tới file system.
2. **Dùng `createFile` rồi mong nó tự sinh thư mục cha:** không có; tạo cha riêng nếu được phép.
3. **Dùng `createDirectory` để tạo `a/b/c` khi `a/b` chưa tồn tại:** phải dùng `createDirectories` hoặc tạo từng cấp.
4. **Cho rằng `createDirectories` chỉ tạo khi đích không có và luôn báo lỗi nếu gọi lần hai:** nếu đích vẫn là thư mục, lời gọi lặp có thể thành công.
5. **Cho rằng `createDirectories` là transaction:** không có rollback toàn bộ khi một cấp tạo thất bại.
6. **Kiểm tra `Files.notExists` trước rồi tin rằng không còn race:** trạng thái có thể đổi giữa hai lời gọi.
7. **Dùng `createFile` để mở ghi vào tệp đã tồn tại:** thao tác tạo mới không dùng để overwrite hoặc append. Phải chọn API ghi cùng `OpenOption` đúng chính sách.
8. **Dùng `Files.writeString(path, text)` nhưng tưởng nó từ chối overwrite:** mặc định có thể truncate; dùng `CREATE_NEW` khi cần không ghi đè.
9. **Bắt `FileAlreadyExistsException` rồi giả định đích chắc chắn là thư mục:** có thể đích là tệp, symlink hoặc loại đối tượng khác; cần xác minh đúng chính sách.
10. **Chọn quyền POSIX cho mọi hệ điều hành:** provider có thể không hỗ trợ; cần kiểm tra khả năng và cơ chế permission/ACL.
11. **Dùng `deleteIfExists(dir)` với thư mục còn chứa con:** đây không phải xóa cây đệ quy.
12. **Cho rằng mã kiểm tra lexical của path đã chống mọi symbolic link:** bảo vệ file system thực tế cần thiết kế kiểm tra và mở an toàn theo hoàn cảnh.

## 14. Cách lựa chọn nhanh theo yêu cầu

**Tạo tệp mới nhưng không ghi nội dung:** chọn `Files.createFile(path)`. Nếu có file từ trước, trả lỗi rõ ràng; không âm thầm xóa.

**Tạo đúng một thư mục mới bên trong cha đã có:** chọn `Files.createDirectory(dir)`. Nếu đích đã có và nghiệp vụ coi đó là lỗi, giữ nguyên ngoại lệ.

**Chuẩn bị đường dẫn thư mục nhiều cấp hoặc bảo đảm thư mục hiện có:** chọn `Files.createDirectories(dir)`. Nhưng nhớ trường hợp file nằm ở một cấp là lỗi, và tạo nhiều cấp có thể thành công một phần.

**Tạo tệp mới rồi ghi luôn với chính sách chống ghi đè:** chọn `Files.writeString(..., CREATE_NEW, WRITE)` hoặc `newOutputStream(..., CREATE_NEW, WRITE)`. Đây thường rõ nghĩa và giảm khoảng trống race hơn hai thao tác tạo + mở ghi riêng.

**Tạo dữ liệu tạm:** chọn `Files.createTempFile(...)` cho file hoặc `Files.createTempDirectory(...)` cho thư mục. Các API tạm tự chọn tên để giảm nguy cơ đụng tên, nhưng ứng dụng vẫn phải đóng tài nguyên và dọn khi phù hợp.

**Ứng dụng có nhiều tiến trình hoặc dữ liệu nhạy cảm:** xác định permission, owner, symlink, lifecycle và xử lý TOCTOU; ba phương thức tạo này không thay thế một cơ chế giao dịch hay phân quyền tệp đầy đủ.

## 15. Ghi nhớ cốt lõi

- `createFile` **tạo đúng một tệp trống** và **không tạo cha**.
- `createDirectory` **tạo đúng một thư mục mới**, đòi hỏi cha đã tồn tại.
- `createDirectories` **tạo các thư mục còn thiếu theo chuỗi**, chấp nhận thư mục đích đã tồn tại nhưng **không nguyên tử cho toàn bộ chuỗi**.
- Khi tên file đã có, đừng tự kiểm tra rồi ghi đè; sử dụng cơ chế tạo mới/ngăn ghi đè phù hợp như `CREATE_NEW`.
- File system có thể từ chối vì thiếu quyền, xung đột loại tài nguyên, symlink và I/O; xử lý lỗi cụ thể khi có thể nhưng luôn hiểu nguồn ngoại lệ.
- Với ví dụ hoặc test, tạo thư mục tạm, thao tác trong đó và cleanup từ dưới lên; với dữ liệu thật, không xóa ngoài phạm vi sở hữu.

### Tài liệu tham khảo

- [Java 17 — Files.createFile/createDirectory/createDirectories](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [Java 17 — StandardOpenOption](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardOpenOption.html)
- [Java 17 — Path](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html)
- [Java 17 — FileAttribute](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/FileAttribute.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-operations-002">createTempFile, createTempDirectory và phạm vi dọn dẹp</a>

<details>
<summary>Xem chi tiết</summary>

# createTempFile, createTempDirectory và phạm vi dọn dẹp trong Java

Ứng dụng Java thường phải tạo dữ liệu trung gian: file tải về đang xử lý, dữ liệu export, file đầu vào cho thư viện khác, vùng giải nén, ảnh chuyển đổi, hoặc nơi kiểm thử có thể ghi dữ liệu mà không chạm vào file thật của người dùng. Java NIO.2 cung cấp **`Files.createTempFile(...)`** và **`Files.createTempDirectory(...)`** để tạo file/thư mục tạm với tên riêng trong một filesystem.

Điều quan trọng không chỉ là tạo được file tạm. Ứng dụng còn phải xác định rõ **nơi tạo**, **ai sở hữu**, **khi nào dọn**, **chỉ được xóa những gì**, **thứ tự đóng tài nguyên và xóa**, và **cách xử lý lỗi dọn dẹp**. Từ “temporary” không có nghĩa “JVM tự động xóa an toàn mọi thứ sau khi dùng”.

Bài này thuộc nhóm **Thao tác tệp** trong Java Core I/O; ví dụ dùng Java 17+.

## 1. Temporary file và temporary directory là gì?

- **Temporary file (tệp tạm):** tệp được tạo cho một mục đích có vòng đời giới hạn, ví dụ ghi nội dung trước khi upload hoặc lưu kết quả trung gian.
- **Temporary directory (thư mục tạm):** thư mục được tạo để gom một nhóm file/thư mục tạm phục vụ cùng một tác vụ, ví dụ một lượt xử lý ảnh.
- **Phạm vi dọn dẹp (cleanup scope):** tập hợp tài nguyên mà thành phần hiện tại có quyền và trách nhiệm xóa sau khi công việc kết thúc.

Sơ đồ:

```text
Ứng dụng
   |
   | Files.createTempDirectory("my-job-")
   v
[temp root do ứng dụng tạo]
   |
   +-- Files.createTempFile(root, "input-", ".bin")
   |       -> file trung gian
   |
   +-- Files.createTempDirectory(root, "stage-")
           |
           +-- file đầu ra
           +-- nhiều file nhỏ

Công việc kết thúc (thành công hoặc lỗi)
   |
   +-- đóng stream/channel/writer đang mở
   +-- xóa file và mục con thuộc root
   +-- xóa root sau cùng
   |
   v
KHÔNG xóa cha chung của root hoặc file của người khác
```

Nếu ứng dụng tạo thư mục gốc riêng bằng `createTempDirectory`, nơi đó thường là **ranh giới cleanup thích hợp** cho một công việc. Nhưng nếu ứng dụng chỉ tạo một file tạm trong thư mục chung, quyền dọn dẹp thông thường chỉ áp dụng cho **file đó**, không phải toàn bộ thư mục chứa nó.

## 2. API tạo file/thư mục tạm: các overload chính

Các phương thức thuộc `java.nio.file.Files`:

```java
// Không truyền thư mục: tạo ở vị trí tạm mặc định của provider.
Path file1 = Files.createTempFile("demo-", ".txt");
Path dir1  = Files.createTempDirectory("demo-");

// Chọn thư mục cha đã tồn tại:
Path base  = Path.of("work");
Path file2 = Files.createTempFile(base, "part-", ".bin");
Path dir2  = Files.createTempDirectory(base, "job-");
```

Đây là **mảnh mã minh họa**: cần import `java.nio.file.*`; `base` phải tồn tại, là thư mục và có quyền tạo mục. Các lời gọi có thể ném `IOException` và những ngoại lệ khác khi đối số/thuộc tính không hợp lệ.

Chữ ký tổng quát có tham số `FileAttribute<?>...` tùy chọn:

```java
Files.createTempFile(String prefix, String suffix,
                     FileAttribute<?>... attrs)

Files.createTempFile(Path dir, String prefix, String suffix,
                     FileAttribute<?>... attrs)

Files.createTempDirectory(String prefix,
                          FileAttribute<?>... attrs)

Files.createTempDirectory(Path dir, String prefix,
                          FileAttribute<?>... attrs)
```

**Kết quả là một `Path` trỏ tới tài nguyên đã thực sự được tạo** — khác `Path.of(...)` hay `new File(...)` chỉ dựng đối tượng biểu diễn đường dẫn. Nếu tạo thành công, file tạm là file mới (ban đầu rỗng), còn thư mục tạm là thư mục mới.

| Lệnh | Tạo gì? | Kết quả | Yêu cầu |
| --- | --- | --- | --- |
| `Path.of("x.tmp")` | Không tạo gì trên đĩa | `Path` | Chỉ cần đường dẫn hợp lệ |
| `Files.createTempFile("x-", ".tmp")` | File tạm thật | `Path` | Có vị trí tạm mặc định khả dụng |
| `Files.createTempDirectory("x-")` | Thư mục tạm thật | `Path` | Có vị trí tạm mặc định khả dụng |
| `Files.createTempFile(base, "x-", ".tmp")` | File mới bên trong `base` | `Path` | `base` là thư mục hiện có |
| `Files.createTempDirectory(base, "x-")` | Thư mục mới bên trong `base` | `Path` | `base` là thư mục hiện có |
| `Files.createFile(path)` | File mới tại tên `path` do ứng dụng chọn | `Path` | Path chưa tồn tại |
| `Files.createDirectory(path)` | Thư mục mới tại tên `path` do ứng dụng chọn | `Path` | Path cha tồn tại |

### Prefix, suffix và tên tạo ra

- `prefix` là phần đầu của tên do hệ thống tạo, ví dụ `report-`.
- `suffix` là phần cuối **chỉ của file tạm**, ví dụ `.csv` hoặc `.tmp`.
- Phần tên ở giữa do provider tạo để chọn một tên chưa được sử dụng.
- Java không bảo đảm tên cụ thể hoặc một độ dài cố định cho đoạn được sinh tự động. **Đừng kiểm thử bằng cách đoán tên file đầy đủ**.
- Nếu `prefix` hoặc `suffix` chứa ký tự/chuỗi path không được filesystem chấp nhận, thao tác có thể thất bại.
- Với overload file, `suffix == null` thường được xử lý như hậu tố mặc định `.tmp` theo hợp đồng API; hãy truyền rõ hậu tố khi ứng dụng cần một đuôi cụ thể.
- Đừng đưa dữ liệu bí mật như token, mã xác thực hoặc thông tin cá nhân vào prefix/suffix: tên mục có thể lộ trong log, công cụ quản lý file, trace hoặc danh sách thư mục.

Tên tạm là một **danh tính đường dẫn**, không phải mật khẩu hoặc cơ chế bảo mật. Độ khó đoán tên không thay cho quyền truy cập filesystem.

## 3. Tạo ở đâu: thư mục tạm mặc định và thư mục cụ thể

Khi dùng overload không nhận `Path dir`:

```java
Path temp = Files.createTempFile("app-", ".tmp");
Path folder = Files.createTempDirectory("app-");
```

Java dùng **vị trí tạm mặc định của provider**. Trong môi trường JDK thông thường, cấu hình liên quan đến thư mục tạm có thể được quan sát qua system property `java.io.tmpdir`:

```java
String configured = System.getProperty("java.io.tmpdir");
System.out.println(configured);
```

Mảnh mã trên cho biết cấu hình tại thời điểm đọc, **không phải bảo đảm** tất cả FileSystemProvider sẽ dùng chính đường dẫn đó. Không mã hóa cứng `/tmp` hay `C:\Temp` trong chương trình chạy đa nền tảng. Cũng đừng kết luận file tạm nằm trong thư mục dự án hoặc thư mục chứa class.

Nếu muốn kiểm soát rõ nơi tạo, truyền `Path dir` đã được tạo:

```java
Path workspace = Files.createTempDirectory("my-app-");
Path tempFile = Files.createTempFile(workspace, "data-", ".txt");
```

`tempFile` thuộc thư mục `workspace` do lượt chạy của ứng dụng tạo ra. Dùng đường dẫn rõ ràng này thường giúp **theo dõi ownership và cleanup** tốt hơn so với tạo rải rác nhiều file trong thư mục tạm chung.

### Những điều không nên giả định

- Thư mục tạm mặc định luôn có dung lượng đủ hoặc còn hoạt động.
- Mọi máy đều dùng cùng hệ thống tệp, separator và quy tắc quyền truy cập.
- File tạm chắc chắn bị xóa khi JVM kết thúc.
- `createTempFile` tự tạo mọi thư mục cha được đưa vào.
- Hai chương trình có cùng prefix sẽ tạo đúng cùng tên.
- Một đường dẫn đã tạo sẽ không bao giờ bị tiến trình khác xóa hoặc đổi tên.

Nếu file tạm tham gia xử lý dữ liệu quan trọng, phải có kiểm soát lỗi I/O, giới hạn kích thước và chính sách lưu trữ/khôi phục phù hợp.

## 4. Tạo là thật và thường có kiểm tra tên mới nguyên tử

Khi `Files.createTempFile(...)` thành công, provider tạo một file **mới**; phương thức không chỉ trả một cái tên để “có thể tạo sau”. Tương tự, `Files.createTempDirectory(...)` tạo thư mục mới.

Điểm thuận lợi về an toàn:

```java
Path output = Files.createTempFile("download-", ".part");
```

`output` đã là file được tạo, không phải tên dự kiến lấy từ bộ tạo số ngẫu nhiên của bạn. Provider chọn tên mới và thực hiện thao tác tạo theo hợp đồng API, tránh kiểu mã dễ bị race:

```java
// Không nên tự thiết kế:
Path candidate = Path.of("work", "temp-" + System.currentTimeMillis());
if (!Files.exists(candidate)) {
    Files.createFile(candidate); // Một tiến trình khác có thể chen vào.
}
```

Mảnh mã thứ hai chỉ minh họa lỗi thiết kế (*time-of-check to time-of-use*). Khi cần file tạm mới, gọi trực tiếp `Files.createTempFile(...)`. Khi cần tạo file tại một **tên cố định** và thất bại nếu đã có, dùng `Files.createFile(...)` hoặc `StandardOpenOption.CREATE_NEW`.

Tuy nhiên, sau khi tài nguyên được tạo, ứng dụng vẫn có thể gặp race nếu đóng rồi mở lại tên file trong thư mục có thể bị người khác sửa. Không cho rằng việc tạo mới ở ban đầu làm mọi thao tác sau đó trở thành an toàn tuyệt đối. Với dữ liệu nhạy cảm, quan tâm quyền truy cập thư mục cha, owner, symlink/hard link, và giữ handle khi cần.

## 5. Một ví dụ Java 17+ hoàn chỉnh: tạo rồi dọn một workspace tạm

Ví dụ sử dụng **root tạm riêng** cho một lượt chạy. Bên trong root, ta tạo một thư mục con, một file text và một file nhị phân; ghi, đọc, kiểm tra rồi dọn tất cả bằng `Files.walkFileTree(...)`.

Lưu vào file `TempWorkspaceDemo.java`:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class TempWorkspaceDemo {

    private static void deleteOwnedTree(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file,
                                             BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir,
                                                      IOException error)
                    throws IOException {
                if (error != null) {
                    throw error;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void main(String[] args) throws IOException {
        // Root được tạo mới riêng cho lần chạy này.
        Path root = Files.createTempDirectory("lesson-temp-");

        try {
            Path nested = Files.createTempDirectory(root, "batch-");
            Path text = Files.createTempFile(root, "note-", ".txt");
            Path binary = Files.createTempFile(nested, "data-", ".bin");

            Files.writeString(text, "Hello", StandardCharsets.UTF_8);
            Files.write(binary, new byte[] { 1, 2, 3 });

            System.out.println("root directory: "
                    + Files.isDirectory(root));
            System.out.println("nested directory: "
                    + Files.isDirectory(nested));
            System.out.println("text file: "
                    + Files.isRegularFile(text));
            System.out.println("text: "
                    + Files.readString(text, StandardCharsets.UTF_8));
            System.out.println("binary length: " + Files.size(binary));
        } finally {
            // Chỉ xóa root mà lượt chạy vừa tạo và các mục bên trong.
            deleteOwnedTree(root);
        }

        System.out.println("root removed: " + Files.notExists(root));
    }
}
```

Biên dịch/chạy trên JDK 17+:

```bash
javac TempWorkspaceDemo.java
java TempWorkspaceDemo
```

Stdout trong điều kiện thư mục tạm tạo/ghi/xóa được và không có tiến trình khác can thiệp:

```text
root directory: true
nested directory: true
text file: true
text: Hello
binary length: 3
root removed: true
```

Đã kiểm tra hành vi tương ứng với JDK. Tên root, folder và file được tạo là **khác nhau ở mỗi lần chạy**; ví dụ không in tên động nên đầu ra dễ đối chiếu.

Giải thích chi tiết:

1. `Files.createTempDirectory("lesson-temp-")` **tạo thư mục thật**, được xem là phạm vi mà ví dụ sở hữu.
2. `Files.createTempDirectory(root, "batch-")` tạo thư mục con bên trong root; `Files.createTempFile(root, "note-", ".txt")` và `Files.createTempFile(nested, "data-", ".bin")` tạo **hai file rỗng**.
3. `Files.writeString(...)` và `Files.write(...)` đưa dữ liệu vào file đã tồn tại. Trong ví dụ, ta dùng API ghi tiện ích với các tùy chọn mặc định để minh họa, nên có thể truncate nội dung có sẵn tại đường dẫn. Với pipeline cần bảo đảm danh tính file trước nguy cơ thay thế đường dẫn, nên kiểm soát cách mở stream và quyền truy cập thư mục.
4. `Files.readString(...)` và `Files.size(...)` kiểm tra nội dung, kích thước file.
5. `finally` được chạy khi khối `try` hoàn tất bình thường hoặc phát sinh ngoại lệ Java. Nó gọi `deleteOwnedTree(root)` để dọn cây thư mục.
6. `walkFileTree` mặc định **không theo symbolic link** vì ví dụ không truyền `FileVisitOption.FOLLOW_LINKS`. `visitFile` xóa file/link, còn `postVisitDirectory` chỉ xóa thư mục khi đã xử lý con của nó.
7. `Files.notExists(root)` sau đó cho biết root đã biến mất trong trường hợp thành công. Nó không phải bằng chứng vĩnh viễn rằng đường dẫn sẽ không bị tạo lại.

Với ứng dụng thật, cả giai đoạn xử lý và cleanup có thể ném `IOException`; phải thiết kế cách ưu tiên và ghi nhận lỗi thay vì luôn coi cleanup không bao giờ thất bại.

## 6. Vì sao dọn thư mục phải theo thứ tự con trước cha?

`Files.delete(path)` xóa file hoặc thư mục **rỗng** tương ứng; Java không coi lời gọi này là lệnh xóa đệ quy tùy tiện. Nếu thư mục còn phần tử:

```java
Files.delete(root);
```

có thể ném `DirectoryNotEmptyException`. `Files.deleteIfExists(root)` chỉ khác ở việc không ném lỗi riêng khi **root không tồn tại**; nó **không** tự động xóa các phần tử con.

Vì vậy:

```text
my-job-123/
  note-12.txt
  batch-34/
    data-56.bin
```

Thứ tự hợp lý là xóa `data-56.bin`, xóa `batch-34`, xóa `note-12.txt`, rồi xóa `my-job-123`. Khi có nhiều cấp và số mục lớn, `Files.walkFileTree` phù hợp hơn nhiều lệnh delete thủ công.

Các hook của `SimpleFileVisitor`:

| Phương thức | Vai trò trong cleanup |
| --- | --- |
| `visitFile(file, attrs)` | Xóa file thường hoặc entry được thăm như file; theo mặc định symbolic link không bị truy theo |
| `postVisitDirectory(dir, error)` | Sau khi duyệt xong con, xóa chính thư mục |
| `visitFileFailed(path, error)` | Quyết định làm gì nếu một mục không thể được truy cập |
| `preVisitDirectory(dir, attrs)` | Trước khi duyệt phần con của thư mục |

Nếu một thao tác duyệt/xóa thất bại, xử lý đúng `IOException`; không nên bỏ qua mọi lỗi một cách im lặng. Trong code ví dụ, `postVisitDirectory` kiểm tra `error != null` trước khi xóa. Nếu muốn dọn “best effort”, có thể tập hợp nhiều lỗi để báo cáo sau, nhưng phải có chủ đích và tránh tuyên bố đã dọn sạch khi vẫn còn dữ liệu.

### Một cách khác: Files.walk với thứ tự ngược

Có thể thu các đường dẫn bằng `Files.walk(root)`, sắp xếp sao cho các con đứng trước cha và gọi `Files.delete(...)`. Tuy nhiên, `Files.walk(...)` trả về **Stream có tài nguyên phải đóng**, logic sắp xếp/duyệt phải xử lý ngoại lệ và symbolic link rõ ràng. `walkFileTree` thường dễ kiểm soát lỗi I/O, thứ tự và callback hơn cho cleanup đệ quy.

Dù chọn cách nào, **chỉ bắt đầu từ root do ứng dụng thực sự sở hữu**.

## 7. Phạm vi dọn dẹp: xóa đúng phần mình tạo

Ba kịch bản phổ biến:

| Mô hình tạo tạm | Phạm vi được quản lý | Khi kết thúc |
| --- | --- | --- |
| Một `createTempFile(...)` trong thư mục tạm dùng chung | **Chính file đó** | `Files.deleteIfExists(tempFile)` sau khi đóng nguồn |
| Một `createTempDirectory(...)` riêng cho job | **Thư mục đó và con thuộc job** | Xóa con trước, xóa root sau |
| Tạo file tạm trong thư mục người dùng chỉ định | **File do ứng dụng tạo**, không phải toàn bộ thư mục người dùng | Xóa file do mình sở hữu; không xóa directory cha |
| Job tạo output cuối cùng cần giữ | Chỉ các file trung gian; **output hoàn chỉnh không phải rác** | Giữ/publish output, dọn intermediate |
| Temp resource được bàn giao cho component khác | Theo hợp đồng quyền sở hữu | Không xóa trước khi bên nhận hoàn tất |

**Một quy tắc tuyệt đối:** Đừng lấy `System.getProperty("java.io.tmpdir")` rồi xóa toàn bộ nội dung của thư mục đó. Nhiều ứng dụng và tài khoản có thể dùng chung nơi này. Root tạm do một job tạo khác với **thư mục tạm dùng chung của hệ điều hành**.

Trước khi gọi hàm xóa cây thư mục:

1. Bảo đảm root được trả về bởi thao tác tạo **dành riêng cho job này**.
2. Không chấp nhận root tùy ý từ input chưa được kiểm tra.
3. Không dùng đường dẫn rỗng, thư mục home hoặc thư mục tạm hệ thống làm root cleanup.
4. Không chủ động theo symbolic link trỏ ra ngoài root.
5. Tính đến việc dữ liệu có thể đổi tên, chuyển vị trí hoặc bị tác nhân khác thay thế giữa lúc tạo và dọn.
6. Không xóa output cuối đã được bàn giao hoặc dữ liệu được yêu cầu lưu lại.

Trong hệ thống xử lý nhiều job, một workspace tạm riêng cho mỗi job giúp theo dõi và dọn dẹp đúng phần thuộc job đó, thay vì quét một thư mục chung rồi đoán file nào là “của mình”.

## 8. try-with-resources cho stream khác với cleanup file/thư mục

`try-with-resources` tự gọi `close()` với những đối tượng `AutoCloseable` như `InputStream`, `OutputStream`, `Reader`, `Writer` và `FileChannel`. Tuy nhiên, **`Path` và `File` không phải tài nguyên `AutoCloseable`**. Đóng một stream không mặc định xóa file vật lý nằm bên dưới.

Ví dụ:

```java
Path temp = Files.createTempFile("download-", ".bin");

try {
    try (OutputStream out = Files.newOutputStream(temp)) {
        out.write(new byte[] { 1, 2, 3 });
    } // Stream đã đóng trước khi file bị xóa.

    // Dùng dữ liệu tại temp khi stream không còn mở.
} finally {
    Files.deleteIfExists(temp);
}
```

Đây là mảnh mã cần import `java.nio.file.Files`, `Path` và `java.io.OutputStream`, đồng thời xử lý `IOException`. Với ví dụ này, resource lifecycle có hai tầng:

- **Tầng 1:** Đóng stream bằng `try-with-resources`.
- **Tầng 2:** Xóa file tạm bằng `finally`.

Trên một số hệ điều hành (đặc biệt Windows), file đang mở có thể bị từ chối xóa do chia sẻ handle. Trên hệ khác, unlink file đang mở có thể thành công nhưng handle vẫn tồn tại cho tới khi đóng. Không xây dựng code dựa trên một hành vi duy nhất của mọi OS. **Đóng handle trước rồi dọn file** là nguyên tắc portable.

### Không quên resource khi cleanup lỗi

`try-with-resources` còn xử lý ngoại lệ *suppressed* khi cả phần xử lý chính và thao tác `close()` báo lỗi. Nhưng đoạn `try { work(); } finally { cleanup(); }` thông thường có thể để exception từ `cleanup()` che khuất exception gốc của `work()` nếu cả hai cùng thất bại.

Với logic phức tạp, có thể bọc workspace tạm vào lớp `AutoCloseable` để dùng `try-with-resources`:

```java
final class TempWorkspace implements AutoCloseable {
    private final Path root;

    TempWorkspace() throws IOException {
        this.root = Files.createTempDirectory("job-");
    }

    Path root() {
        return root;
    }

    @Override
    public void close() throws IOException {
        // deleteOwnedTree(root) là helper duyệt/xóa riêng
        // được triển khai theo mô hình walkFileTree ở phần trên.
        deleteOwnedTree(root);
    }
}
```

Đây là **mảnh mã thiết kế**, không độc lập: cần helper `deleteOwnedTree` và import `java.nio.file.*`/`java.io.IOException`. Khi có lớp hoàn chỉnh, sử dụng:

```java
try (TempWorkspace workspace = new TempWorkspace()) {
    process(workspace.root());
}
```

Đây cũng là mảnh mã có `process` do ứng dụng định nghĩa. Nếu `process` ném exception và `close` cũng thất bại, try-with-resources sẽ đặt lỗi đóng ở danh sách **suppressed** thay vì thay thế lỗi chính, giúp chẩn đoán. Phải đảm bảo `close()` chỉ dọn root do đối tượng tạo, tránh vô ý nhận một thư mục người dùng làm root.

## 9. deleteOnExit không phải giải pháp cleanup tổng quát

`java.io.File.deleteOnExit()` đăng ký xóa một **đường dẫn** khi JVM kết thúc **bình thường**:

```java
File temp = File.createTempFile("demo-", ".tmp");
temp.deleteOnExit();
```

Mảnh mã cần import `java.io.File` và xử lý `IOException`.

Giới hạn rất quan trọng:

- Không xóa ngay sau khi công việc hoàn tất.
- Không đảm bảo chạy khi tiến trình bị kill, mất điện hoặc JVM dừng bất thường.
- Không tự dọn toàn bộ cây thư mục đệ quy. Nếu đăng ký directory còn chứa file, việc xóa directory có thể không thành công.
- Danh sách đường dẫn đã đăng ký được JVM giữ tới lúc kết thúc; đăng ký rất nhiều file trong chương trình server chạy lâu có thể gây tích lũy thông tin không cần thiết.
- Không cho biết ngay tại thời điểm xử lý rằng xóa đã thành công.
- Không thay cho đóng stream/handle.

Do đó `deleteOnExit()` có thể phù hợp làm phương án phụ cho ví dụ CLI nhỏ, nhưng **cleanup tường minh** trong `finally` hoặc một workspace có `AutoCloseable` là cách tốt hơn để quản lý vòng đời một lượt xử lý.

Nếu ứng dụng cần khôi phục rác tạm sau sự cố, một tác vụ dọn rác theo tuổi file (*janitor*) có thể là bổ trợ, nhưng phải sử dụng **namespace/metadata sở hữu rõ ràng**, giới hạn tuổi, kiểm soát quyền và tránh xóa tác vụ còn chạy. Không chỉ dựa vào file có hậu tố `.tmp`.

## 10. Quyền truy cập khi tạo dữ liệu tạm nhạy cảm

File tạm có thể chứa thông tin nội bộ hoặc cá nhân. Đừng mặc định rằng “ở thư mục tạm” nghĩa là “chỉ tiến trình hiện tại đọc được”.

Theo tài liệu Java, `Files.createTempFile`/`createTempDirectory` có thể tạo tài nguyên với thuộc tính/quyền được quy định bằng `FileAttribute<?>... attrs`, nếu provider hỗ trợ. Ở filesystem POSIX, ví dụ file chỉ owner đọc/ghi:

```java
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

Path secret = Files.createTempFile(
        "secret-", ".bin",
        PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rw-------")));
```

Đây là **mảnh mã trên filesystem hỗ trợ POSIX attributes**. Không phải mọi Windows hoặc provider NIO đều hỗ trợ attribute `posix:permissions`; có thể ném `UnsupportedOperationException` hoặc lỗi khác. Đừng giả vờ ví dụ portable nguyên xi.

Nếu cần workspace riêng với quyền owner:

```java
Path privateRoot = Files.createTempDirectory(
        "private-job-",
        PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rwx------")));
```

Mảnh mã cần import `java.nio.file.*` và `java.nio.file.attribute.PosixFilePermissions`, xử lý `IOException`, chỉ dùng khi provider hỗ trợ POSIX. Trên các nền tảng khác, thiết kế ACL/permissions phù hợp với hệ điều hành và tổ chức.

**Các nguyên tắc chung:**

1. Đặt file nhạy cảm trong thư mục riêng có quyền hạn chế.
2. Không dựa chỉ vào tên ngẫu nhiên như một biện pháp bảo vệ.
3. Không lưu mật khẩu/token trong tên file hoặc ghi nguyên nội dung bí mật ra log.
4. Hạn chế file tạm tồn tại quá lâu và tránh chia sẻ không cần thiết với ứng dụng khác.
5. Không giả định `Files.delete` tương đương xóa an toàn không thể khôi phục ở cấp thiết bị lưu trữ; filesystem, snapshot hoặc backup có thể vẫn lưu dấu dữ liệu.
6. Sử dụng đúng chiến lược cho dữ liệu rất nhạy cảm, bao gồm mã hóa và chính sách lưu trữ khi cần.

## 11. Lỗi thường gặp trong cleanup thực tế

### 11.1. Chỉ gọi deleteIfExists trên thư mục cha

`Files.deleteIfExists(workspace)` không xóa mọi file con. Nếu còn entry bên dưới, nó có thể ném `DirectoryNotEmptyException`. Cần xóa file và thư mục con trước (hoặc dùng visitor).

### 11.2. Xóa root chưa chắc thuộc về mình

Một biến `Path root` lấy từ config chưa đảm bảo nó là workspace do ứng dụng tạo. Nếu truyền nhầm `Path.of(System.getProperty("java.io.tmpdir"))` vào helper đệ quy, hậu quả có thể phá hủy dữ liệu của chương trình khác. Chỉ dọn cây root **mới được tạo riêng và đã ghi nhận ownership**.

### 11.3. Không đóng file stream trước khi xóa

Tệp có thể còn handle đang mở; Windows thường hạn chế xóa tùy kiểu chia sẻ. Dùng try-with-resources bao bọc stream trước, rồi xóa file khi handle đã đóng.

### 11.4. Coi cleanup trong finally là luôn thành công

`finally` bảo đảm **thử chạy** cleanup khi luồng điều khiển Java đi qua, không đảm bảo thao tác xóa thực sự thành công. Disk lỗi, permission đổi, file bị khóa, race hoặc process chết đều có thể để lại file tạm. Ghi nhận lỗi dọn dẹp và thiết kế xử lý rác còn sót.

### 11.5. Dựa vào tên để quyết định quyền xóa

Tên bắt đầu `temp-` không chứng minh file do ứng dụng tạo. Một phần mềm khác có thể dùng prefix tương tự. Cần danh sách tài nguyên đã tạo hoặc root riêng và xác thực phạm vi.

### 11.6. Theo symlink ra ngoài root

`Files.walkFileTree(root)` **mặc định không bật `FOLLOW_LINKS`**. Đừng bật option này khi dọn cây tạm nếu không có nhu cầu và mô hình bảo mật rõ, vì một link bên trong root có thể trỏ tới vị trí khác ngoài phạm vi. Ngay cả khi không theo links, vẫn có rủi ro race nếu người khác được phép sửa cây trong lúc duyệt; thư mục root chỉ owner ghi giúp giảm rủi ro.

### 11.7. Không kiểm soát dung lượng

File tạm có thể làm đầy ổ đĩa. Một ứng dụng upload/giải nén có thể bị “zip bomb” hoặc input quá lớn làm tăng số file và byte. Đặt giới hạn tổng bytes, số file, độ sâu thư mục, thời gian xử lý; không xem thư mục tạm là bộ nhớ không giới hạn.

### 11.8. Tạo quá nhiều file chỉ để xử lý dữ liệu rất nhỏ

Một vài byte cần xử lý nhanh có thể giữ trong `byte[]`/`ByteArrayOutputStream` thay vì tạo file tạm thật. File tạm có chi phí I/O và đồng bộ metadata, nên dùng khi thư viện yêu cầu `Path`, cần xử lý dữ liệu lớn hoặc muốn tránh giữ toàn bộ dữ liệu trong RAM.

## 12. Cách tổ chức scope trong ứng dụng nhiều bước

Một pipeline xử lý file thường có ba dạng tài nguyên:

| Loại | Ví dụ | Quy tắc vòng đời |
| --- | --- | --- |
| **Intermediate** | `raw-input.tmp`, file giải nén, preview | Dọn sau khi job xong |
| **Output đã publish** | `reports/final.csv` | Giữ lại theo chính sách dữ liệu |
| **Shared external input** | File upload đã chuyển quyền cho storage khác, file người dùng cung cấp | Không xóa trừ khi hợp đồng cho phép |

Ví dụ quy trình export an toàn hơn việc ghi trực tiếp vào file cuối:

```text
(1) Tạo temp file cùng filesystem/thư mục đích khi phù hợp
(2) Ghi kết quả vào temp, flush và đóng stream
(3) Xác minh output
(4) Thử chuyển file tạm sang file đích bằng Files.move(...)
(5) Dọn file tạm nếu chưa được chuyển giao thành công
```

Một ví dụ *mảnh mã* minh họa:

```java
Path finalPath = Path.of("reports", "final.txt");
Path parent = finalPath.toAbsolutePath().getParent();
Files.createDirectories(parent);

Path temp = Files.createTempFile(parent, "final-", ".tmp");
try {
    Files.writeString(temp, "Kết quả", StandardCharsets.UTF_8);
    Files.move(temp, finalPath, StandardCopyOption.REPLACE_EXISTING);
} finally {
    Files.deleteIfExists(temp);
}
```

Cần import `java.nio.file.*`, `java.nio.charset.StandardCharsets` và xử lý `IOException`. File tạm được tạo **cùng thư mục cha** với đích để tránh một số vấn đề di chuyển qua filesystem khác, nhưng **`Files.move` không mặc nhiên bảo đảm atomic**. Nếu định dạng và provider cần tính nguyên tử, có thể xem xét `StandardCopyOption.ATOMIC_MOVE`, đồng thời phải xử lý trường hợp provider không hỗ trợ bằng `AtomicMoveNotSupportedException`. Không giả định một lần `Files.move(..., REPLACE_EXISTING)` đảm bảo độ bền dữ liệu sau sự cố mất điện.

Đặc biệt, khi `move` thành công, `temp` không còn là file ở path cũ, vì thế `deleteIfExists(temp)` trong `finally` sẽ không xóa output cuối. Khi `move` thất bại, `finally` thử dọn temp. Nếu file kết quả đã được publish và phải giữ lại, **không đưa `finalPath` vào scope dọn rác**.

## 13. Xử lý lỗi khi đang tạo tài nguyên tạm

Các phương thức tạo temp có thể thất bại:

- `IOException` do quyền, đĩa đầy, hết inode, thư mục cha biến mất, provider không đáp ứng hoặc lỗi I/O khác.
- `SecurityException` khi môi trường/chính sách bảo mật không cho phép thao tác tương ứng.
- `IllegalArgumentException` hoặc ngoại lệ tương tự khi prefix/suffix hoặc thuộc tính không hợp lệ với filesystem.
- `UnsupportedOperationException` khi dùng file attribute mà provider không hỗ trợ.

Không nên bắt `IOException` rồi chỉ trả `null` và làm chương trình tiếp tục như thể đã có file; trạng thái đó thường dẫn tới `NullPointerException` ở nơi khác và che giấu lỗi thật. Báo lỗi với ngữ cảnh đủ rõ (job ID, loại thao tác và vị trí được phép ghi log) mà không vô tình ghi dữ liệu nhạy cảm.

**Một bẫy về vị trí `try/finally`:**

```java
Path temp = Files.createTempFile("job-", ".tmp");
try {
    use(temp);
} finally {
    Files.deleteIfExists(temp);
}
```

Đây là mảnh mã; `use` là hàm của ứng dụng. Nếu `createTempFile` thất bại, chưa có file mới để dọn. Nếu thành công, `finally` sẽ chạy cho đoạn `use` theo luồng Java thông thường. Nhưng nếu `use` tạo thêm file con ở vị trí khác ngoài `temp`, việc chỉ xóa `temp` không dọn được các tài nguyên đó. Do vậy mô hình root workspace riêng là giải pháp dễ mở rộng khi một job tạo nhiều file.

## 14. Khả năng di động giữa filesystem providers

`Files.createTempFile(Path dir, ...)` tạo trong **filesystem provider của `dir`**. Không phải mọi provider đều hỗ trợ đầy đủ attribute, quyền, cách dọn hay khả năng move giống local disk.

Một số điểm cần cân nhắc:

- Filesystem mặc định trên Windows và Unix có quy tắc tên/permission khác nhau.
- ZIP filesystem provider có thể có hành vi write/delete khác local disk, và đóng `FileSystem` là bước riêng.
- Hệ thống tệp network có thể phát sinh độ trễ, mất kết nối hoặc lỗi quyền không giống thư mục tạm cục bộ.
- Symlink/junction và case sensitivity có thể khác theo provider.
- `Path.toFile()` không bắt buộc hoạt động cho Path thuộc provider phi mặc định.
- `File.createTempFile(...)` thuộc API legacy, thường phục vụ filesystem mặc định; với Path/provider tùy biến, ưu tiên API `Files` và truyền `Path dir`.

Đừng dùng các giả định kiểu “có file tạm là chắc chắn nó nằm trong ổ C:” hoặc “xóa file khi stream mở giống nhau trên mọi hệ điều hành”. Nếu ứng dụng phụ thuộc một đặc tính provider, kiểm thử chính xác trên runtime/filesystem mục tiêu.

## 15. So sánh với java.io.File.createTempFile

API truyền thống:

```java
File legacy = File.createTempFile("demo-", ".tmp");
```

Mảnh mã cần `java.io.File` và xử lý `IOException`. Khác với `new File("demo.tmp")`, **`File.createTempFile` thực sự tạo file tạm rỗng** rồi trả đối tượng `File` trỏ tới nó.

Bảng tóm tắt:

| API | Tạo tài nguyên thật? | Kiểu trả về | Điểm nổi bật |
| --- | --- | --- | --- |
| `new File("x.tmp")` | Không | `File` | Chỉ biểu diễn pathname |
| `File.createTempFile(prefix, suffix)` | Có: file | `File` | Legacy; dùng default filesystem |
| `File.createTempFile(prefix, suffix, directory)` | Có: file | `File` | Legacy; đặt file trong directory |
| `Files.createTempFile(prefix, suffix)` | Có: file | `Path` | NIO; hỗ trợ file attributes |
| `Files.createTempFile(dir, prefix, suffix)` | Có: file | `Path` | NIO; tạo trong thư mục/provider được chọn |
| `Files.createTempDirectory(prefix)` | Có: thư mục | `Path` | NIO; tạo namespace riêng |
| `Files.createTempDirectory(dir, prefix)` | Có: thư mục | `Path` | NIO; tạo directory con trong `dir` |

Một khác biệt đáng lưu ý từ tài liệu JDK: khi **không truyền thuộc tính** và trên một số hệ thống, `Files.createTempFile` có thể tạo file với quyền hạn chế hơn so với `File.createTempFile`. Dù vậy, chính sách bảo mật vẫn phải dựa vào provider/OS thực, không lấy câu này làm cam kết cứng cho mọi môi trường.

## 16. Câu hỏi thường gặp

### 16.1. `createTempFile` có tạo tên duy nhất không?

Nó tạo **mục mới** với tên do provider sinh ra, tránh đụng tên đã được dùng trong quá trình tạo. Không nên hiểu là cam kết một tên chưa từng được dùng ở mọi thời điểm hoặc là mã định danh không thể đoán. Khi file bị xóa, một tên có thể được dùng lại theo cơ chế provider.

### 16.2. Tôi chỉ muốn một đường dẫn tạm, chưa tạo file, dùng `createTempFile` được không?

Không đúng mục đích: `createTempFile` **đã tạo file**. Nếu thư viện cần Path tồn tại thì đó là ưu điểm. Nếu bạn cần file có tên cố định hoặc tạo lúc khác, hãy thiết kế cách đặt tên và thao tác `CREATE_NEW` phù hợp; đừng tạo file rồi xóa ngay chỉ để lấy tên và hy vọng tên đó an toàn về sau.

### 16.3. File tạm có tự biến mất khi close stream không?

Không. `close()` giải phóng handle và xả dữ liệu của stream theo hợp đồng; xóa file trên filesystem là thao tác riêng. Một số API đặc biệt như mở bằng `StandardOpenOption.DELETE_ON_CLOSE` có thể yêu cầu xóa khi đóng trên provider hỗ trợ, nhưng có giới hạn triển khai và không phải lựa chọn mặc định.

### 16.4. Nếu ứng dụng bị kill thì `finally` có chạy không?

Không thể bảo đảm. `finally` hoạt động trong cơ chế kiểm soát luồng Java bình thường; dừng tiến trình đột ngột, mất điện hoặc sự cố môi trường có thể không cho cleanup chạy. Nếu hậu quả file rác là quan trọng, cần có chiến lược xử lý sau sự cố phù hợp.

### 16.5. Có nên dọn toàn bộ `java.io.tmpdir` lúc startup?

Không. Đó có thể là thư mục chung của nhiều chương trình. Chỉ xét những workspace mang danh tính và ownership do ứng dụng của bạn quản lý, có chính sách tuổi/thời gian hoạt động rõ ràng.

### 16.6. Dọn bằng cách đi theo symbolic link có an toàn không?

Mặc định `walkFileTree` không theo symlink, hợp lý cho cleanup. Đừng bật `FOLLOW_LINKS` trừ khi hiểu chính xác đích và bảo đảm không lan ra ngoài phạm vi sở hữu. Chống race và giới hạn quyền sửa root vẫn cần thiết.

### 16.7. Dọn trong `finally` có thể che lỗi chính không?

Có. Nếu cả xử lý và cleanup ném exception, lỗi cleanup có thể khiến lỗi gốc bị thay thế. Dùng mô hình `AutoCloseable` + try-with-resources hoặc thu gom suppressed exceptions có kiểm soát để bảo toàn thông tin chẩn đoán.

### 16.8. Nên dùng file tạm trong thư mục hệ thống hay trong thư mục đích?

Tùy tác vụ. Với xử lý trung gian thông thường, dùng default temp location riêng cho job. Với chiến lược ghi file rồi move tới đích và muốn có khả năng move cùng filesystem, tạo temp trong **thư mục đích hoặc filesystem đích** có thể phù hợp. Trong cả hai trường hợp, phải kiểm soát quyền, không dùng directory của người khác làm phạm vi xóa đệ quy.

## 17. Lỗi thường gặp và cách tránh

| Sai lầm | Hậu quả | Cách tránh |
| --- | --- | --- |
| Cho rằng “temp” sẽ tự xóa lúc kết thúc chương trình | File rác tồn tại lâu, tốn dung lượng | Cleanup tường minh; có chính sách xử lý rác sau sự cố |
| Dùng `Path.of(...)` rồi nghĩ đã tạo file | Tệp chưa có khi thư viện mở | Dùng `Files.createTempFile` nếu cần file mới có thật |
| Dùng `Files.createTempDirectory` rồi gọi `Files.delete` ngay khi còn con | `DirectoryNotEmptyException` | Dọn con trước, cha sau |
| Xóa cả thư mục tạm hệ thống | Phá hủy dữ liệu của tác vụ khác | Chỉ dọn root riêng do job sở hữu |
| Quên đóng stream trước khi xóa | Khóa file/handle tồn tại | try-with-resources rồi cleanup |
| Dùng `deleteOnExit` cho mọi file ở server sống lâu | Tích lũy đăng ký, không dọn kịp | Cleanup theo scope của từng request/job |
| Tin rằng `finally` bảo đảm xóa thành công | Ẩn lỗi I/O hoặc để lại rác | Kiểm tra/log lỗi dọn, khôi phục theo chính sách |
| Quét theo prefix `temp-` để xóa cả thư mục chung | Xóa nhầm file không thuộc ứng dụng | Workspace riêng và quyền sở hữu rõ |
| Theo symlink khi xóa đệ quy | Có thể vượt ra ngoài scope | Không bật `FOLLOW_LINKS`, hạn chế quyền thay đổi root |
| Dùng tên tạm làm biện pháp bảo mật | Nguy cơ bị đọc/ghi trái phép | Dùng quyền/ACL/owner và thư mục riêng |
| Không kiểm soát kích thước/số lượng file | Đầy disk/inode | Giới hạn byte, mục và thời gian |
| Tin `File.delete()` trả `false` nghĩa chắc chắn file chưa có | Mất thông tin nguyên nhân lỗi | Dùng `Files.delete(...)` khi cần ngoại lệ chi tiết |
| Ghi output cuối vào temp rồi dọn cùng output | Mất dữ liệu đã publish | Tách “intermediate” và “output cần giữ” |
| Dùng temp mặc định để suy ra mọi file đều cùng filesystem với đích | Move có thể không atomic/không hỗ trợ | Chọn dir trên filesystem đích khi cần |
| Tạo temp tên đẹp và mở lại không bảo vệ race | File có thể bị thay bởi tác nhân khác | Quyền thư mục, chính sách handle, thao tác mở an toàn |
| Giả định POSIX permission có trên Windows | `UnsupportedOperationException` | Kiểm tra provider/OS và dùng ACL phù hợp |

## 18. Câu hỏi tự kiểm tra

1. **`Files.createTempFile("a-", ".tmp")` có thực sự tạo file không?** Có; trả về `Path` của file mới đã tạo.
2. **`Files.createTempDirectory("job-")` khác gì `Path.of("job")`?** Lệnh đầu tạo directory vật lý; lệnh sau chỉ tạo đối tượng Path.
3. **Có thể đoán chính xác tên file tạm không?** Không; phần tên được sinh ra tùy provider.
4. **File tạm được tạo ở đâu nếu không truyền `dir`?** Ở vị trí tạm mặc định của provider; không mặc nhiên là thư mục dự án.
5. **`createTempFile(dir, ...)` có tự tạo `dir` nếu thiếu không?** Không; phải chuẩn bị thư mục cha.
6. **Đóng `OutputStream` có tự xóa file tạm không?** Không theo hành vi mặc định.
7. **`Files.deleteIfExists(directory)` có xóa đệ quy không?** Không; directory còn mục con có thể ném `DirectoryNotEmptyException`.
8. **Vì sao dùng `walkFileTree` để dọn root?** Có thể xử lý file trước thư mục, kiểm soát lỗi và mặc định không theo symlink.
9. **Root cleanup nên là `java.io.tmpdir` hay folder riêng do job tạo?** Folder riêng do job tạo.
10. **`deleteOnExit()` có bảo đảm xóa trong trường hợp JVM bị kill?** Không.
11. **Nếu cleanup cũng lỗi khi xử lý chính lỗi, điều gì cần lưu ý?** Giữ thông tin exception gốc, có thể dùng suppression/try-with-resources.
12. **Khi file có dữ liệu nhạy cảm cần quan tâm gì?** Quyền/ACL, vị trí root, người sở hữu, thời gian lưu, nhật ký và dọn dẹp.
13. **Có nên tạo file tạm trong thư mục đích trước khi publish?** Có thể, nếu cần cùng filesystem; nhưng phải chọn move semantics đúng, không giả định atomic mặc định.
14. **Vì sao không bật `FOLLOW_LINKS` khi cleanup?** Có thể khiến traversal chạm tài nguyên bên ngoài root.
15. **Vì sao hệ thống server có thể cần janitor bổ sung?** Vì crash/kill/dọn lỗi có thể để lại workspace cũ dù có `finally`.
16. **File tạm chứa dữ liệu binary lớn cần giới hạn gì?** Tổng dung lượng, số file, thời gian, quyền truy cập và tác vụ có thể đang sử dụng.

## Tham khảo API Java 17

- [`Files.createTempFile` / `Files.createTempDirectory`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html).
- [`Files.walkFileTree`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#walkFileTree(java.nio.file.Path,java.nio.file.FileVisitor)).
- [`FileVisitor` / `SimpleFileVisitor`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/SimpleFileVisitor.html).
- [`Files.delete` / `Files.deleteIfExists`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html).
- [`java.io.File.createTempFile` / `deleteOnExit`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/File.html).
- [`PosixFilePermissions`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/PosixFilePermissions.html).
- [`StandardOpenOption.DELETE_ON_CLOSE`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardOpenOption.html).
- [`StandardCopyOption.ATOMIC_MOVE`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardCopyOption.html).

**Ghi nhớ:** `createTempFile` và `createTempDirectory` tạo tài nguyên **thật** với tên mới, nhưng không tự xác lập chính sách xóa. Cách sử dụng an toàn là cấp một **workspace tạm có ownership rõ**, mở/đóng tài nguyên bằng try-with-resources, dọn file/thư mục con trước root bằng cơ chế phù hợp, giữ output chính thức ngoài phạm vi xóa và chấp nhận rằng cleanup có thể thất bại hoặc không chạy khi tiến trình dừng bất thường.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-operations-003">copy tệp với REPLACE_EXISTING và COPY_ATTRIBUTES</a>

<details>
<summary>Xem chi tiết</summary>

# Sao chép tệp với REPLACE_EXISTING và COPY_ATTRIBUTES trong Java NIO

Khi cần sao chép tệp trong Java 17+, API thông dụng là `java.nio.file.Files.copy(Path source, Path target, CopyOption... options)`. Hai tùy chọn thường đi cùng nhau là:

- **`StandardCopyOption.REPLACE_EXISTING`**: nếu tệp đích đã tồn tại, cho phép thay thế tệp đích trong những điều kiện API hỗ trợ.
- **`StandardCopyOption.COPY_ATTRIBUTES`**: yêu cầu thử sao chép các thuộc tính của tệp nguồn sang tệp đích, tối thiểu gồm thời điểm sửa đổi cuối nếu cả hai file store hỗ trợ.

Ví dụ điển hình:

~~~java
Files.copy(
        source,
        target,
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.COPY_ATTRIBUTES
);
~~~

Tuy nhiên, **cho phép thay thế không có nghĩa là sao chép nguyên tử**, và **yêu cầu sao chép thuộc tính không có nghĩa mọi metadata được bảo toàn tuyệt đối**. Cần hiểu riêng từng option, cách chúng kết hợp, các trường hợp ngoại lệ và rủi ro tệp đích bị ghi chưa hoàn chỉnh.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

1. Giải thích hợp đồng của `Files.copy(Path, Path, CopyOption...)`, kiểu trả về và các ngoại lệ quan trọng.
2. Phân biệt hành vi mặc định khi tệp đích đã có với `REPLACE_EXISTING`.
3. Phân biệt sao chép **nội dung byte** với sao chép **thuộc tính tệp**, và các giới hạn của `COPY_ATTRIBUTES`.
4. Biết điều gì xảy ra khi nguồn là symbolic link hoặc thư mục, khi đích là symbolic link, hoặc khi nguồn và đích trỏ đến cùng một tệp.
5. Hiểu lý do `Files.copy` không bảo đảm atomicity, rollback hoặc bảo vệ khỏi thay đổi đồng thời.
6. Xây dựng ví dụ Java 17+ chạy được, có thư mục tạm, cleanup, kiểm tra nội dung bằng `Files.mismatch` và quan sát metadata.
7. Chọn chiến lược tệp tạm và `Files.move` khi cần một quy trình công bố kết quả có kiểm soát.
8. Tránh các lỗi thường gặp về đường dẫn, quyền, file store, thuộc tính và độ an toàn dữ liệu.

## 2. Tổng quan cú pháp và ý nghĩa

Chữ ký của overload được học:

~~~java
public static Path copy(
        Path source, Path target, CopyOption... options
) throws IOException;
~~~

Trong đó:

- `source`: `Path` của nguồn cần sao chép.
- `target`: `Path` đích cần được tạo hoặc thay thế.
- `options`: các tùy chọn điều khiển hành vi, chẳng hạn `StandardCopyOption.REPLACE_EXISTING`, `StandardCopyOption.COPY_ATTRIBUTES` hoặc `LinkOption.NOFOLLOW_LINKS`.
- Giá trị trả về: chính `Path` đích.
- `IOException`: báo các lỗi trong quá trình sao chép; có thể nhận một lớp con cụ thể như `FileAlreadyExistsException`.

Phải phân biệt rõ overload này với:

~~~java
long bytes = Files.copy(inputStream, target, copyOptions);
long bytes = Files.copy(source, outputStream);
~~~

Hai overload còn lại có kiểu trả về `long` và bộ option/hợp đồng riêng. **Không thể mặc định `COPY_ATTRIBUTES` áp dụng cho việc copy từ một `InputStream` tùy ý** vì nguồn kiểu stream không mang một bộ file attributes theo `Path` để sao chép.

### Khi không truyền option

~~~java
Path copied = Files.copy(source, target);
~~~

Mặc định, khi `target` đã tồn tại (kể cả symbolic link) thì thao tác sẽ thất bại, **trừ trường hợp nguồn và đích được xác định là cùng một tệp**: khi đó phương thức có thể hoàn tất mà không thực hiện bản sao.

Nếu là hai tệp khác nhau và đích đã tồn tại, provider thường ném `FileAlreadyExistsException`; theo tài liệu Java, đây là *optional specific exception*: code sản xuất vẫn phải xử lý `IOException` tổng quát vì không phải mọi provider bắt buộc dùng cùng loại ngoại lệ chuyên biệt.

Mặc định cũng **không yêu cầu sao chép các thuộc tính file** ngoài nội dung và thông tin mà provider cần để tạo file đích. Muốn yêu cầu giữ thuộc tính, khai báo rõ `COPY_ATTRIBUTES`.

## 3. `REPLACE_EXISTING`: thay thế tệp đích, không phải nối thêm

~~~java
Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
~~~

Option này yêu cầu cho phép thay thế một entry tại đường dẫn đích nếu nó đã tồn tại. Nó **không** có ý nghĩa:

- Nối dữ liệu vào cuối file cũ (*append*).
- Chỉ ghi những byte khác nhau hoặc đồng bộ tăng dần.
- Tạo mọi thư mục cha chưa tồn tại.
- Tự sao lưu nội dung cũ hoặc tự khôi phục khi lỗi.
- Thực hiện một thao tác thay thế nguyên tử.
- Cho phép xóa một **thư mục đích không rỗng** để thay thế.

Theo hợp đồng `Files.copy`, `REPLACE_EXISTING` không thể thay thế một target là **thư mục không rỗng**. Hãy xử lý `DirectoryNotEmptyException` nếu provider dùng lỗi cụ thể này; nếu không vẫn phải xử lý `IOException`.

### So sánh

| Tình huống | `Files.copy(source, target)` | Có `REPLACE_EXISTING` |
| --- | --- | --- |
| Đích chưa tồn tại | Tạo bản sao mới nếu đủ điều kiện | Cũng tạo bản sao mới |
| Đích đã là file thường khác nguồn | Thường lỗi `FileAlreadyExistsException` | Cho phép thay thế file đích |
| Đích là symbolic link | Mặc định coi là đích đã tồn tại nên thường lỗi | Thay thế **chính link**, không phải file mà link trỏ đến |
| Đích là thư mục không rỗng | Không ghi đè | Không thể thay thế thư mục không rỗng |
| Nguồn và đích là cùng một tệp | Hoàn tất không copy thực tế theo hợp đồng | Cũng không cần copy khi được nhận diện là cùng tệp |

**Rủi ro thực tế:** nếu người gọi chỉ định nhầm `target`, option này có thể khiến dữ liệu quan trọng cũ bị mất. Dùng option khi yêu cầu nghiệp vụ thật sự là thay thế. Với yêu cầu “đừng ghi đè”, hãy **bỏ `REPLACE_EXISTING`** và xử lý xung đột.

`REPLACE_EXISTING` cũng **không phải** giải pháp chống TOCTOU (*time-of-check/time-of-use*): trạng thái đích có thể thay đổi giữa kiểm tra và copy, và tài liệu nêu việc kiểm tra tồn tại/cấp tạo file mới không nhất thiết là nguyên tử đối với hoạt động file system khác.

## 4. `COPY_ATTRIBUTES`: cố gắng sao chép metadata, không phải mọi thuộc tính

~~~java
Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
~~~

`COPY_ATTRIBUTES` yêu cầu hệ thống cố sao chép thuộc tính đi kèm tệp nguồn sang tệp đích. Theo tài liệu Java 17:

- **Những thuộc tính được sao chép chính xác phụ thuộc nền tảng và file system**.
- Tối thiểu, `last-modified-time` được sao chép **nếu cả file store nguồn và đích cùng hỗ trợ**.
- Việc chép timestamp có thể **mất độ chính xác** do độ phân giải thời gian của file store.
- Quyền truy cập, ACL, owner, nhóm, extended attributes hoặc các metadata khác **không được bảo đảm giống nhau** trên mọi hệ thống.
- Nếu đang sao chép **symbolic link** bằng `NOFOLLOW_LINKS`, việc sao chép thuộc tính cho chính link phụ thuộc triển khai; `COPY_ATTRIBUTES` có thể bị bỏ qua cho link.

Như vậy, không nên khẳng định:

> “`COPY_ATTRIBUTES` bảo toàn 100% permission, owner, creation time và mọi ACL.”

Điều đó vượt quá hợp đồng Java. Nếu bài toán thực sự phải giữ một thuộc tính riêng, hãy dùng API thuộc tính tương ứng để **đọc, so sánh và xử lý sai khác** trong phạm vi provider hỗ trợ, ví dụ:

~~~java
FileTime sourceTime = Files.getLastModifiedTime(source);
FileTime targetTime = Files.getLastModifiedTime(target);

System.out.println("sameTimestamp=" + sourceTime.equals(targetTime));
~~~

Đây là mảnh mã cần import `java.nio.file.Files`, `java.nio.file.Path`, `java.nio.file.attribute.FileTime`. Giá trị so sánh có thể khác khi đích có độ phân giải timestamp thấp hơn. `false` không tự khẳng định dữ liệu byte bị copy sai; nó cho thấy timestamp chưa khớp chính xác theo phép so sánh này.

### REPLACE_EXISTING kết hợp COPY_ATTRIBUTES

~~~java
Files.copy(
        source,
        target,
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.COPY_ATTRIBUTES
);
~~~

Nghĩa là:

1. Cho phép copy vào đường dẫn đích ngay cả khi đích là file cũ cần thay thế.
2. Đồng thời yêu cầu cố sao chép thuộc tính nguồn sang kết quả mới.

**Không có thứ tự “thay thế xong chắc chắn mọi thuộc tính đã chép” nếu phát sinh lỗi.** Tài liệu nêu nếu gặp `IOException`, tệp đích có thể chỉ được copy một phần hoặc một số thuộc tính chưa được sao chép.

## 5. Ví dụ Java 17+ chạy được: mặc định thất bại, sau đó thay thế thành công

Tạo file **`CopyOptionsDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class CopyOptionsDemo {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("copy-options-");
        Path source = directory.resolve("source.txt");
        Path target = directory.resolve("target.txt");

        try {
            Files.writeString(source, "NEW", StandardCharsets.UTF_8);
            Files.writeString(target, "OLD", StandardCharsets.UTF_8);

            try {
                Files.copy(source, target);
                System.out.println("unexpected success");
            } catch (FileAlreadyExistsException exception) {
                System.out.println("withoutReplace=FileAlreadyExistsException");
            }

            Path returned = Files.copy(
                    source,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES
            );

            System.out.println("returnedTarget=" + returned.equals(target));
            System.out.println("targetText=" +
                    Files.readString(target, StandardCharsets.UTF_8));
            System.out.println("sourceText=" +
                    Files.readString(source, StandardCharsets.UTF_8));
            System.out.println("sameBytes=" +
                    (Files.mismatch(source, target) == -1L));
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(target);
            Files.deleteIfExists(directory);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 CopyOptionsDemo.java
java CopyOptionsDemo
~~~

**Stdout trên default file system thông thường, khi không có tiến trình khác tác động lên thư mục tạm:**

~~~text
withoutReplace=FileAlreadyExistsException
returnedTarget=true
targetText=NEW
sourceText=NEW
sameBytes=true
~~~

Giải thích:

- Cả `source.txt` và `target.txt` đều tồn tại trước khi copy.
- Lần gọi **không option** bị từ chối vì target đã tồn tại.
- Lần gọi có `REPLACE_EXISTING` sao chép nội dung của nguồn sang target.
- Thêm `COPY_ATTRIBUTES` yêu cầu sao chép metadata trong giới hạn được hỗ trợ. Ví dụ không in timestamp vì kết quả phụ thuộc file system, không nên khẳng định một chuỗi thời gian cố định.
- `Files.copy` trả `Path` đích, nên `returned.equals(target)` là `true`.
- `Files.mismatch` (Java 12+) trả `-1L` khi hai file có nội dung byte giống nhau. Đó là một phép so sánh nội dung, **không** xác nhận mọi thuộc tính đều giống.
- Việc copy không xóa nguồn; `sourceText=NEW` xác minh file nguồn còn nguyên trong ca demo.
- `finally` dọn các file và thư mục tạm để không phụ thuộc vào đường dẫn cố định của máy.

### Vì sao không cần `BufferedInputStream`?

`Files.copy(Path, Path, CopyOption...)` đã giao công việc sao chép cho file system provider/JDK. Không cần tự viết vòng `read/write` chỉ để có thể chỉ định `REPLACE_EXISTING` và `COPY_ATTRIBUTES`. Khi muốn thay đổi nội dung lúc copy, tính checksum theo luồng hoặc áp một định dạng đặc biệt, khi đó mới cân nhắc streams/channels với mô hình metadata và xử lý lỗi riêng.

## 6. Quan sát thuộc tính lastModifiedTime

Để kiểm chứng ý nghĩa `COPY_ATTRIBUTES`, có thể gán thời điểm sửa cuối cho nguồn trước khi sao chép:

~~~java
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

// Mảnh mã: source và target đã được khai báo và có cha thích hợp.
FileTime expectedTime = FileTime.from(
        Instant.parse("2020-02-03T04:05:06Z"));

Files.setLastModifiedTime(source, expectedTime);

Files.copy(
        source,
        target,
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.COPY_ATTRIBUTES
);

FileTime sourceTime = Files.getLastModifiedTime(source);
FileTime targetTime = Files.getLastModifiedTime(target);

System.out.println("sourceTime=" + sourceTime);
System.out.println("targetTime=" + targetTime);
~~~

Đây là **mảnh mã minh họa**, cần đặt trong phương thức có xử lý `IOException`. Nó không có stdout bất biến theo mọi file system: provider có thể lưu thời gian theo độ phân giải khác, nên giá trị được đọc lại có thể bị làm tròn.

Phép kiểm tra đúng là đánh giá **theo yêu cầu ứng dụng**: ví dụ file store hỗ trợ độ chính xác giây thì so sánh đến giây, hoặc yêu cầu exact match trên một môi trường đã xác định. Nếu timestamp quan trọng nhưng phía đích không hỗ trợ cùng độ chính xác, đó là một giới hạn nền tảng cần được ghi nhận thay vì im lặng tuyên bố “đã copy mọi thuộc tính”.

Cũng cần phân biệt:

- `size`: kích thước nội dung byte; nếu nội dung copy thành công thì dung lượng file thường phản ánh nội dung mới, **không phải** thứ cần bật `COPY_ATTRIBUTES` mới có.
- `lastModifiedTime`: thuộc tính thời gian được yêu cầu sao chép tối thiểu khi cả hai file store hỗ trợ.
- `lastAccessTime`, `creationTime`, `owner`, `permissions`, `ACL` và extended metadata: không được suy ra chắc chắn đã giống chỉ từ một lần gọi `COPY_ATTRIBUTES`.

## 7. Nếu không thêm `COPY_ATTRIBUTES` thì sao?

~~~java
Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
~~~

Dữ liệu được copy theo hợp đồng của `Files.copy`, nhưng không có yêu cầu **bảo toàn metadata nguồn**. Target có thể nhận các timestamp hoặc thuộc tính mới theo quy tắc tạo tệp của hệ thống.

Ví dụ:

- Một hệ thống nhận file export cần nội dung mới, không cần giữ thời gian sửa đổi của file nguồn: có thể chỉ dùng `REPLACE_EXISTING`.
- Một quy trình sao lưu/copy dữ liệu muốn giữ `lastModifiedTime` để người dùng đối chiếu: có thể thêm `COPY_ATTRIBUTES`, sau đó xác minh các thuộc tính thực sự quan trọng.

**Không nhầm** yêu cầu “sao chép file” với yêu cầu “sao chép file kèm mọi security attributes”. Đây là hai mức hợp đồng khác nhau; mức sau phải viết rõ những thuộc tính nào phải giữ và hành vi khi hệ thống không hỗ trợ.

## 8. Tình huống nguồn hoặc đích là symbolic link

Symbolic link (*liên kết tượng trưng*) có hai khía cạnh: chính **link entry** và **đích mà nó chỉ tới**.

### Khi nguồn là symbolic link

Theo mặc định, `Files.copy(sourceLink, target)` **theo symbolic link nguồn** và sao chép **file mà link trỏ đến**, nếu có thể giải quyết link.

Nếu muốn sao chép **chính symbolic link**, dùng `LinkOption.NOFOLLOW_LINKS`:

~~~java
Files.copy(
        sourceLink,
        targetLink,
        LinkOption.NOFOLLOW_LINKS
);
~~~

Đây là mảnh mã, cần import `Files`, `LinkOption` và dùng `Path` phù hợp. Nó yêu cầu provider hỗ trợ thao tác symbolic link; có thể không chạy trên mọi hệ điều hành, file system hoặc cấu hình quyền.

| Nguồn | Option | Đối tượng được yêu cầu copy |
| --- | --- | --- |
| Symlink trỏ tới file | Mặc định | File mà link trỏ đến |
| Symlink | `NOFOLLOW_LINKS` | Chính symbolic link |
| Symlink + `COPY_ATTRIBUTES` + `NOFOLLOW_LINKS` | Hai option | Chính link; việc chép **thuộc tính của link** có thể bị bỏ qua tùy triển khai |

### Khi đích là symbolic link

Với `REPLACE_EXISTING`, nếu `target` đang là symbolic link thì **chính symbolic link đó được thay thế**, chứ không phải file mà link trỏ tới. Đây là điểm thường gây nhầm:

~~~text
targetLink --> important.txt

Files.copy(source, targetLink, REPLACE_EXISTING)

Kết quả được yêu cầu:
- entry targetLink bị thay thế bằng bản sao
- không phải ghi trực tiếp nội dung vào important.txt qua link
~~~

Tuy vậy, đừng xem điều này là giải pháp tổng quát chống mọi symlink race: cây thư mục và các thành phần cha vẫn có thể thay đổi do tác nhân khác kiểm soát. Với đường dẫn lấy từ dữ liệu không tin cậy, phải đánh giá quyền truy cập, đường dẫn cha, policy và tính nguyên tử của thao tác thực.

## 9. Copy thư mục là sao chép nông (*shallow*), không tự đệ quy

Một hiểu nhầm phổ biến là `Files.copy(sourceDirectory, targetDirectory)` sẽ tự copy toàn bộ cây file. Theo Java 17:

> Khi nguồn là thư mục, API này tạo một **thư mục rỗng** ở đích; không tự copy các mục bên trong.

Ví dụ chạy được: tạo một thư mục có file con rồi copy thư mục đó.

Tạo **`DirectoryCopyDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class DirectoryCopyDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("directory-copy-");
        Path source = Files.createDirectory(root.resolve("source"));
        Path child = Files.writeString(
                source.resolve("inside.txt"), "hello",
                StandardCharsets.UTF_8
        );
        Path target = root.resolve("target");

        try {
            Files.copy(source, target);

            System.out.println("sourceChildExists=" +
                    Files.exists(child));
            System.out.println("targetIsDirectory=" +
                    Files.isDirectory(target));
            System.out.println("targetChildExists=" +
                    Files.exists(target.resolve("inside.txt")));
        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(child);
            Files.deleteIfExists(source);
            Files.deleteIfExists(root);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac DirectoryCopyDemo.java
java DirectoryCopyDemo
~~~

Stdout trong môi trường thông thường:

~~~text
sourceChildExists=true
targetIsDirectory=true
targetChildExists=false
~~~

Muốn copy **toàn bộ cây**, cần duyệt đệ quy, chẳng hạn `Files.walkFileTree` với `FileVisitor` và thao tác riêng cho mỗi thư mục/file. Khi làm như vậy, phải xác định rõ có theo symbolic links hay không, thứ tự tạo thư mục, giới hạn độ sâu, cách xử lý lỗi và metadata nào cần khôi phục sau khi copy con. Thêm `COPY_ATTRIBUTES` vào một lần copy thư mục **không biến nó thành copy đệ quy**.

## 10. `Files.copy` không phải thao tác nguyên tử

Tài liệu Java nêu rõ việc sao chép một file **không phải atomic operation**. Nếu `IOException` xảy ra giữa chừng:

- Đích có thể đã bị thay thế và chỉ chứa **một phần dữ liệu**.
- Một số attributes có thể đã copy, những attributes khác chưa.
- Nội dung cũ của đích có thể không còn; không có rollback tự động.
- Người đọc khác có thể quan sát trạng thái đích trong một số giai đoạn, tùy provider và cách triển khai.
- Lỗi khi copy metadata vẫn có thể làm hoạt động báo thất bại, dù nội dung đã được chuyển một phần hoặc toàn bộ.

Vì vậy đoạn sau **không nên** hiểu là một giao dịch an toàn tuyệt đối:

~~~java
Files.copy(source, target,
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.COPY_ATTRIBUTES);
System.out.println("published");
~~~

Dòng `published` chỉ chứng tỏ lời gọi `copy` đã hoàn thành mà không ném exception. Nó không khẳng định checksum/metadata theo chính sách nghiệp vụ đã được xác minh, file đã được đồng bộ bền vững xuống đĩa hoặc phía đọc từ xa đã nhìn thấy nội dung.

Nếu lỗi xảy ra, code phải đánh giá trạng thái đích, chính sách cleanup và khả năng thử lại. **Không xóa target một cách mù quáng** nếu target có thể là dữ liệu dùng chung hoặc đã được một tác vụ khác thay thế trong khi đang xử lý.

## 11. Mô hình an toàn hơn khi cần công bố file

Một cách thường dùng là:

1. Tạo một **file tạm trong thư mục đích** (thường để thuận lợi cho việc move trong cùng file store).
2. Copy nguồn sang file tạm, với `COPY_ATTRIBUTES` nếu cần.
3. Nếu yêu cầu, kiểm tra byte bằng `Files.mismatch`, kích thước, checksum hay metadata.
4. Di chuyển file tạm sang tên chính thức; có thể **yêu cầu** `ATOMIC_MOVE` nếu provider hỗ trợ.
5. Xóa file tạm trong `finally` khi chưa được chuyển đi.

Ví dụ lớp tiện ích Java 17, **`StagedCopy.java`**:

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class StagedCopy {
    private StagedCopy() {}

    public static void publish(Path source, Path target)
            throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Target needs a parent");
        }

        // parent phải tồn tại và caller đã xác định được phép ghi.
        Path staged = Files.createTempFile(
                parent, ".staged-copy-", ".part");

        try {
            // createTempFile đã tạo staged; cần REPLACE_EXISTING
            // để copy ghi vào tên tạm đó.
            Files.copy(
                    source,
                    staged,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES
            );

            if (Files.mismatch(source, staged) != -1L) {
                throw new IOException("Content mismatch after copy");
            }

            Files.move(
                    staged,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } finally {
            Files.deleteIfExists(staged);
        }
    }
}
~~~

Đây là **lớp có thể biên dịch nhưng không có `main`**. Caller truyền nguồn và đích phù hợp; phương thức có thể ném `IOException` nếu không thể hoàn tất. Đoạn mã cho thấy một **chiến lược**, không phải lời hứa hỗ trợ đồng nhất trên mọi hệ thống.

Các giới hạn cần hiểu kỹ:

- `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`: khi đã chọn `ATOMIC_MOVE`, **các options còn lại bị bỏ qua theo hợp đồng** của `Files.move`; nếu target đã tồn tại, việc **thay thế hay báo lỗi** phụ thuộc implementation. Không giả định luôn là “atomic replace existing” trên mọi provider.
- Nếu provider không hỗ trợ atomic move, `AtomicMoveNotSupportedException` có thể xảy ra. Trong ví dụ, phương thức **không âm thầm hạ cấp** sang move không nguyên tử.
- Bản thân quá trình **copy vào staged** vẫn không nguyên tử, nhưng chưa công bố bằng tên target cuối cho tới bước move nếu mọi thứ thành công.
- `Files.mismatch` xác nhận nội dung byte khi đọc ở hai thời điểm; nguồn vẫn có thể bị sửa **đồng thời** trong/giữa copy và verify. Nếu yêu cầu snapshot nhất quán, cần khóa/cơ chế version/nguồn bất biến hoặc biện pháp khác của hệ thống.
- `ATOMIC_MOVE` không đồng nghĩa mọi byte đã được `fsync`/ghi bền vững vào thiết bị khi mất điện. Nếu yêu cầu durability, phải thiết kế cơ chế đồng bộ thích hợp, trong phạm vi nền tảng hỗ trợ.
- Nếu move thành công thì `staged` không còn ở tên tạm, nên `deleteIfExists(staged)` không xóa target. Nếu move thất bại, cleanup sẽ cố xóa tệp tạm. Trạng thái ngoài ý muốn vẫn có thể cần giám sát thủ công.
- Với thư mục/đích không tin cậy, đường dẫn cha và symlink cần cơ chế bảo vệ bổ sung.

Nếu ứng dụng thực sự đòi **thay thế đích hiện hữu một cách nguyên tử**, phải xác nhận file system/provider của môi trường triển khai hỗ trợ đúng hành vi mong muốn. Không nên tự tin chỉ nhờ thấy cả hai option `ATOMIC_MOVE` và `REPLACE_EXISTING` trong lời gọi.

## 12. Exceptions: biết loại lỗi nào có thể xảy ra

`Files.copy(Path, Path, CopyOption...)` khai báo `throws IOException` và có thể phát sinh những lỗi như:

| Trường hợp | Ngoại lệ/tín hiệu điển hình |
| --- | --- |
| Đích đã tồn tại, không có `REPLACE_EXISTING` | `FileAlreadyExistsException` hoặc `IOException` |
| Đích là thư mục không rỗng không thể thay thế | `DirectoryNotEmptyException` hoặc `IOException` |
| Nguồn hoặc một thành phần thiết yếu không tồn tại | `NoSuchFileException` hoặc `IOException` |
| Không đủ quyền truy cập | `AccessDeniedException` hoặc `IOException` |
| Lỗi đọc/ghi thiết bị, file store, network filesystem | `IOException` |
| Option không được provider hỗ trợ | `UnsupportedOperationException` |
| Một số tham số bị `null` | `NullPointerException` theo hợp đồng API |

Những lớp con I/O chuyên biệt có thể là **optional specific exceptions**: không nên bỏ xử lý `IOException` tổng quát.

Mẫu xử lý tối thiểu:

~~~java
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class CopyWithErrors {
    private CopyWithErrors() {}

    public static Path copy(Path source, Path target)
            throws IOException {
        try {
            return Files.copy(
                    source, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES
            );
        } catch (AccessDeniedException exception) {
            throw new IOException(
                    "Access denied while copying to " + target,
                    exception
            );
        } catch (FileAlreadyExistsException exception) {
            // Có thể gặp ở môi trường/provider đặc thù hoặc
            // khi thao tác cạnh tranh làm trạng thái thay đổi.
            throw new IOException(
                    "Target collision while copying to " + target,
                    exception
            );
        }
    }
}
~~~

Lớp ví dụ không có `main`. Nó giữ `cause` để bên gọi có thông tin chẩn đoán. Nếu không có yêu cầu phân loại nghiệp vụ riêng, thường **lan truyền `IOException` gốc** đơn giản hơn so với bọc lại cùng một thông điệp.

Tránh log đường dẫn nhạy cảm một cách tùy tiện. Không có nghĩa mọi thất bại đều nên retry: `AccessDeniedException`, directory-not-empty hoặc đường dẫn bị cấu hình sai thường không giải quyết bằng retry liên tục.

## 13. Các yếu tố làm kết quả phụ thuộc môi trường

### File store, timestamp và permissions

Sao chép trong cùng filesystem thường có điều kiện thuận lợi hơn việc copy giữa hai provider/file store khác nhau, nhưng không thể suy ra điều này bảo đảm mọi thuộc tính giống nhau. Độ chính xác timestamp, quyền chủ sở hữu, ACL, extended attributes và implementation copy đều có thể khác giữa Windows, Linux, macOS và file system mạng.

Nếu chương trình yêu cầu một thuộc tính thiết yếu như permission POSIX hoặc ACL, hãy kiểm tra capability và dùng API `Files.readAttributes`/attribute views tương ứng để xác minh rõ, không dựa vào `COPY_ATTRIBUTES` như giấy cam kết tất cả metadata.

### Thư mục cha của target

`Files.copy` không có hợp đồng tự tạo toàn bộ directory tree. Nếu `target` là `output/reports/data.txt` mà `output/reports` chưa tồn tại, copy có thể thất bại.

Khi ứng dụng được phép tạo parent directories:

~~~java
Path parent = target.toAbsolutePath().getParent();
if (parent != null) {
    Files.createDirectories(parent);
}
Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
~~~

Đây là **mảnh mã** với `source/target` kiểu `Path` và xử lý `IOException`. Tạo các thư mục cha là **thao tác riêng**, có thể thất bại và có thể tạo một phần cây trước khi lỗi.

### Nguồn và đích cùng một tệp

Theo `Files.copy`, khi nguồn và đích được xem là **cùng một file**, phương thức hoàn tất mà không tạo bản sao. Điều này không chỉ là so sánh chuỗi path: hard link hoặc hai đường dẫn khác biểu diễn cùng một file có thể cần file system để nhận diện. Khi thật sự muốn biết hai path có trỏ tới cùng file ở thời điểm hiện tại, xem `Files.isSameFile`, nhưng đây cũng không phải khóa chặn thay đổi đồng thời.

### Copy giữa nhiều providers

`Files.copy` có thể làm việc với hai `Path` thuộc providers khác nhau trong phạm vi hợp đồng Java. Khi đó khả năng hỗ trợ metadata, symlink, quyền và hiệu năng khác nhau. Không giả định mọi bản sao giữa các provider đều có thể thay thế, giữ attributes và chạy đúng tốc độ như copy nội bộ trên ổ local.

## 14. Copy file có giữ nguyên nội dung văn bản không?

`Files.copy(Path, Path, ...)` sao chép file theo cơ chế file, không gọi decoder UTF-8 rồi encoder lại theo kiểu bạn tự làm với `BufferedReader`/`BufferedWriter`. Do đó nó thích hợp cho:

- Ảnh, PDF, ZIP và các file nhị phân.
- File text mà không cần thay đổi encoding hoặc line endings.
- Dữ liệu bất kỳ cần copy nguyên nội dung byte khi thao tác thành công.

Nếu bạn cần chuyển UTF-8 sang UTF-16, thay đổi newline hoặc chỉnh nội dung, phải có bước xử lý văn bản khác; `COPY_ATTRIBUTES` không thực hiện chuyển đổi charset và `REPLACE_EXISTING` cũng không làm việc đó.

Sau copy, có thể so sánh byte bằng:

~~~java
long difference = Files.mismatch(source, target);
boolean identical = difference == -1L;
~~~

`Files.mismatch` không phải checksum mật mã; nó so sánh nội dung mà API đọc được và có thể ném `IOException`. Nó cũng không so sánh permissions, ACL hoặc timestamp.

## 15. Các lỗi phổ biến

**Lỗi 1 — Quên `REPLACE_EXISTING` rồi nghĩ copy sẽ ghi đè mặc định.**  
Mặc định target tồn tại khiến copy bị từ chối (ngoại trừ trường hợp source/target là cùng file). Hãy chọn rõ chính sách overwrite.

**Lỗi 2 — Dùng `REPLACE_EXISTING` khi nghiệp vụ thực ra cấm ghi đè.**  
Điều này có thể làm mất file đích cũ. Nếu cần “create only”, đừng truyền option và xử lý xung đột theo hợp đồng.

**Lỗi 3 — Tưởng `COPY_ATTRIBUTES` bảo toàn tất cả metadata giống hệt.**  
Không đúng. Danh sách attributes và độ chính xác phụ thuộc hệ thống; tối thiểu last-modified-time nếu hai store hỗ trợ. Kiểm tra thêm những thuộc tính thật sự cần thiết.

**Lỗi 4 — Nghĩ `Files.copy` có rollback và atomicity.**  
Không. Khi lỗi I/O, target có thể chưa hoàn chỉnh hoặc bị thay thế một phần. Với dữ liệu quan trọng, cân nhắc staging file và publish phù hợp.

**Lỗi 5 — Copy thư mục rồi mong có cả cây con.**  
`Files.copy` trên directory tạo directory đích rỗng; muốn đệ quy phải duyệt cây file riêng.

**Lỗi 6 — Nhầm hành vi symbolic link.**  
Nguồn symlink mặc định theo link; `NOFOLLOW_LINKS` copy chính link. Target symlink với `REPLACE_EXISTING` bị thay thế **link entry**, không ghi qua tới file mà link trỏ đến.

**Lỗi 7 — Dùng `COPY_ATTRIBUTES` cho overload copy từ `InputStream`.**  
Đó là overload khác; nguồn stream không có attribute set kiểu `Path` tương ứng. Hãy đọc hợp đồng từng overload.

**Lỗi 8 — Cho rằng REPLACE_EXISTING xóa được mọi loại đích.**  
Không thể thay thế một thư mục đích không rỗng bằng thao tác copy như mong muốn. Ngoài ra, quyền, file store và concurrency có thể gây lỗi.

**Lỗi 9 — Chỉ kiểm tra `Files.exists(target)` trước copy.**  
Trạng thái có thể thay đổi, và `exists==false` không luôn chứng minh file vắng mặt. Hãy để chính lệnh `copy` thực hiện rồi xử lý lỗi.

**Lỗi 10 — Đọc kết quả từ một lần test local rồi suy rộng mọi OS/provider.**  
Timestamp precision, quyền, xử lý symlink, ngoại lệ chi tiết và khả năng atomic move khác nhau. Test trên môi trường triển khai quan trọng, đặc biệt nếu nghiệp vụ yêu cầu metadata.

## 16. Quy trình chọn tùy chọn cho tình huống thực tế

| Nhu cầu | Cách dùng | Điều cần kiểm tra |
| --- | --- | --- |
| Chỉ copy nếu đích chưa có | `Files.copy(source, target)` | Xử lý đích đã tồn tại và I/O lỗi |
| Cho phép thay thế đích | `Files.copy(source, target, REPLACE_EXISTING)` | Có thực sự cho phép mất nội dung đích cũ? |
| Thay thế và giữ một số metadata nguồn | `Files.copy(source, target, REPLACE_EXISTING, COPY_ATTRIBUTES)` | Kiểm tra những metadata bắt buộc và giới hạn provider |
| Sao chép chính symbolic link | Thêm `LinkOption.NOFOLLOW_LINKS` | Provider hỗ trợ symlink và attribute của link không được bảo đảm |
| Copy cả cây thư mục | `Files.walkFileTree` + copy từng mục | Thứ tự, error recovery, symlink, độ sâu, attributes |
| Công bố file với khả năng thay thế nguyên tử | Copy sang staging rồi yêu cầu `ATOMIC_MOVE` | Không mọi provider hỗ trợ và hành vi khi đích tồn tại phụ thuộc implementation |
| Chỉ cần copy byte ra một stream | `Files.copy(source, outputStream)` | Đây là overload khác, không có `COPY_ATTRIBUTES` kiểu Path→Path |
| Sao chép từ một stream vào file | `Files.copy(inputStream, target, options)` | Option hỗ trợ hạn chế, không có metadata nguồn để copy |
| Kiểm tra nội dung giống nhau | `Files.mismatch(source, target) == -1L` | Không kiểm tra metadata, race hoặc durable persistence |

## 17. Checklist khi review code sản xuất

- [ ] Nguồn là **file thường**, thư mục hay symbolic link; chọn hành vi theo link phù hợp?
- [ ] Nếu target tồn tại, chính sách có cho phép **thay thế** không?
- [ ] Nếu cần metadata, đã thêm `COPY_ATTRIBUTES` và xác định **thuộc tính nào** bắt buộc?
- [ ] Có hiểu timestamp có thể mất độ chính xác và owner/ACL không tự đảm bảo giống?
- [ ] Đã xác định thư mục cha target tồn tại, có quyền và không bị can thiệp ngoài ý muốn?
- [ ] Có xử lý `IOException` và các trường hợp provider-specific mà không giả định một subclass luôn xuất hiện?
- [ ] Có chính sách khi bản sao thất bại để tránh công bố file chưa hoàn chỉnh?
- [ ] Có kiểm tra nguồn/đích cùng file, symlink và race nếu đường dẫn đến từ bên ngoài?
- [ ] Có tránh copy nhầm một directory và tưởng đã copy toàn bộ nội dung bên trong?
- [ ] Nếu cần atomic publish, đã xem xét `ATOMIC_MOVE`, giới hạn replace existing và không hạ cấp im lặng?
- [ ] Nếu cần bảo đảm content bytes, có xác minh bằng `Files.mismatch`/hash theo yêu cầu?
- [ ] Nếu yêu cầu file thực sự durable hoặc remote ACK, có cơ chế chuyên biệt bên ngoài `Files.copy`?
- [ ] Unit test có dùng thư mục tạm, cleanup và không phụ thuộc đường dẫn cố định?
- [ ] Không log dữ liệu/đường dẫn riêng tư vào kênh công khai khi xảy ra lỗi?

## 18. Câu hỏi tự kiểm tra

**1.** `Files.copy(source, target)` khi target đã tồn tại sẽ làm gì?  
**Đáp án:** Mặc định lỗi (trừ khi source và target là cùng file); thường có thể thấy `FileAlreadyExistsException`.

**2.** `REPLACE_EXISTING` có tự làm thao tác copy nguyên tử không?  
**Đáp án:** Không. `Files.copy` không phải atomic operation theo hợp đồng.

**3.** `COPY_ATTRIBUTES` có bảo đảm quyền POSIX/ACL/owner đều y hệt không?  
**Đáp án:** Không. Thuộc tính thực sự được copy phụ thuộc provider và file store; chỉ bảo đảm tối thiểu việc copy last-modified-time khi cả hai hỗ trợ, với khả năng mất độ chính xác.

**4.** Target là symbolic link, thêm `REPLACE_EXISTING` có nghĩa ghi đè file mà link trỏ tới không?  
**Đáp án:** Không. Nó thay thế **chính symbolic link tại đường dẫn đích**.

**5.** Source là symbolic link, không có `NOFOLLOW_LINKS` thì copy cái gì?  
**Đáp án:** Mặc định copy mục mà symlink trỏ đến; thêm `NOFOLLOW_LINKS` để copy chính link nếu provider hỗ trợ.

**6.** `Files.copy(sourceDir, targetDir, COPY_ATTRIBUTES)` có copy hết các file con không?  
**Đáp án:** Không. Một lần copy directory chỉ tạo thư mục đích rỗng; cần thuật toán duyệt cây riêng.

**7.** Nếu copy thất bại giữa chừng, có chắc target vẫn giữ nguyên byte cũ không?  
**Đáp án:** Không. Target có thể bị thay thế hoặc chưa hoàn chỉnh; phải thiết kế xử lý lỗi và công bố dữ liệu phù hợp.

**8.** `Files.mismatch(source, target) == -1L` chứng minh điều gì?  
**Đáp án:** Nội dung byte được so sánh là giống nhau theo thao tác đó; không khẳng định mọi metadata, file identity hay khả năng lưu bền vững.

**9.** Muốn buộc publish nguyên tử và provider không hỗ trợ `ATOMIC_MOVE` thì sao?  
**Đáp án:** Xử lý `AtomicMoveNotSupportedException`, báo không đáp ứng yêu cầu hoặc chọn một giải pháp đã được đánh giá riêng; không tự coi move thường là tương đương.

**Ghi nhớ:** `REPLACE_EXISTING` quyết định **có cho phép thay thế entry đích đã tồn tại hay không**. `COPY_ATTRIBUTES` yêu cầu **cố sao chép metadata trong khả năng file system hỗ trợ**. Kết hợp cả hai vẫn **không** tạo ra giao dịch nguyên tử, sao lưu tự động hay cam kết metadata giống tuyệt đối. Hãy chọn option theo hợp đồng nghiệp vụ, kiểm tra lỗi và dùng staging/verification khi dữ liệu quan trọng.

## Tài liệu API chính thức (Java SE 17)

- [`Files.copy(Path, Path, CopyOption...)` và ghi chú về atomicity, thuộc tính, symlink](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#copy(java.nio.file.Path,java.nio.file.Path,java.nio.file.CopyOption...))
- [`StandardCopyOption`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardCopyOption.html)
- [`LinkOption`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/LinkOption.html)
- [`FileTime`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/FileTime.html)
- [`Files.mismatch`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#mismatch(java.nio.file.Path,java.nio.file.Path))

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-operations-004">move, đổi tên và lựa chọn ATOMIC_MOVE</a>

<details>
<summary>Xem chi tiết</summary>

# `Files.move()`, đổi tên tệp và lựa chọn `ATOMIC_MOVE` trong Java

Đổi tên (`rename`) và di chuyển (`move`) là hai thao tác thường gặp trong Java I/O: đổi `draft.txt` thành `report.txt`, chuyển file từ thư mục tạm sang thư mục công bố, hoặc thay tên của một thư mục. API hiện đại là **`java.nio.file.Files.move(Path source, Path target, CopyOption... options)`**. Trong Java NIO.2, đổi tên thường chính là một trường hợp của `move`: đường dẫn nguồn và đường dẫn đích khác tên nhưng có thể cùng thư mục.

Điểm dễ nhầm nhất là **`Files.move()` không mặc định bảo đảm tính nguyên tử**. Khi ứng dụng cần thao tác đổi tên/di chuyển diễn ra như một bước thống nhất mà các tiến trình khác không quan sát trạng thái trung gian, ta có thể yêu cầu **`StandardCopyOption.ATOMIC_MOVE`**. Tuy nhiên, khả năng đáp ứng tùy hệ thống tệp/provider, và lựa chọn này có những quy tắc đặc biệt khi đích đã tồn tại.

Bài học giải thích cú pháp, cách đổi tên và di chuyển, các lựa chọn `REPLACE_EXISTING` và `ATOMIC_MOVE`, phạm vi bảo đảm, lỗi thường gặp, giới hạn khi đi qua nhiều filesystem và cách xây dựng quy trình công bố tệp an toàn.

## 1. Đổi tên là trường hợp của `move`

Một file có thể được đổi tên trong cùng thư mục bằng cách tạo `Path target` mới:

```java
// Đoạn minh họa: source đã được khai báo là java.nio.file.Path.
Path target = source.resolveSibling("renamed.txt");
Files.move(source, target);
```

`resolveSibling("renamed.txt")` đặt tên mới **cạnh** nguồn, tức dưới cùng đường dẫn cha (nếu có). `Files.move(source, target)` yêu cầu chuyển mục nguồn sang đường dẫn đích. Nếu thành công, tên cũ thường không còn dùng để tìm mục đó, còn tên mới dùng để truy cập.

Di chuyển sang một thư mục khác:

```java
// Đoạn minh họa: source và targetDirectory là Path có sẵn.
Path target = targetDirectory.resolve(source.getFileName());
Files.move(source, target);
```

Với cùng một file, API không hề có hai cơ chế độc lập “rename” và “move”: khác nhau chủ yếu là cách xác định **đường dẫn đích**.

**Lưu ý:** `Path` chỉ biểu diễn đường dẫn. `Files.move` mới là thao tác I/O thật; nó có thể ném `IOException` vì thiếu file, đích đã tồn tại, quyền truy cập, filesystem, file đang được dùng hoặc các điều kiện khác. `move()` không tự tạo toàn bộ các thư mục cha của đích.

### Ví dụ 1 — đổi tên một file thật, độc lập với thư mục dự án

```java
// MoveRenameDemo.java
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class MoveRenameDemo {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("move-rename-");
        Path source = directory.resolve("draft.txt");
        Path target = source.resolveSibling("report.txt");

        try {
            Files.writeString(source, "Java I/O", StandardCharsets.UTF_8);

            Path moved = Files.move(source, target);

            System.out.println("resultName=" + moved.getFileName());
            System.out.println("oldExists=" + Files.exists(source));
            System.out.println("newExists=" + Files.exists(target));
            System.out.println("content="
                    + Files.readString(target, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(target);
            Files.deleteIfExists(directory);
        }
    }
}
```

Chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 MoveRenameDemo.java
java MoveRenameDemo
```

Kết quả khi hệ thống tệp thực hiện các thao tác thành công:

```text
resultName=report.txt
oldExists=false
newExists=true
content=Java I/O
```

`Files.move()` trả về `Path` đại diện đường dẫn đích; ví dụ không dùng tên thư mục tạm để tạo stdout nên kết quả dễ kiểm tra. Khối `finally` dọn các mục ví dụ tạo ra; nếu có lỗi I/O ngay trong phần dọn, lỗi ấy cũng cần được xử lý theo chính sách ứng dụng thực tế.

## 2. `Files.move(...)` nhận các đối số nào?

Chữ ký:

```java
public static Path move(
        Path source,
        Path target,
        CopyOption... options) throws IOException;
```

Đây là **chữ ký API rút gọn để giải thích**, không phải phương thức cần tự cài đặt lại.

- `source`: đường dẫn mục muốn di chuyển.
- `target`: đường dẫn đích, gồm cả tên file/thư mục mới.
- `options`: các tùy chọn của thao tác, trong đó hay dùng `StandardCopyOption.REPLACE_EXISTING` và `StandardCopyOption.ATOMIC_MOVE`.
- Kết quả: `Path` đại diện đích nếu thành công.
- Có thể phát sinh `IOException` và các subtype tùy nguyên nhân; một số điều kiện như quyền Java security hoặc provider không tương thích có lỗi khác theo API.

Ba kiểu gọi thường gặp:

```java
Files.move(source, target);

Files.move(source, target,
        StandardCopyOption.REPLACE_EXISTING);

Files.move(source, target,
        StandardCopyOption.ATOMIC_MOVE);
```

**Điểm cần nhớ:** `REPLACE_EXISTING` và `ATOMIC_MOVE` không phải hai mức “an toàn tăng dần” của cùng một tính chất. Một lựa chọn nói về **cách xử lý mục đích đã tồn tại** trong thao tác thông thường; lựa chọn còn lại yêu cầu **tính nguyên tử của bước move**. Và khi có `ATOMIC_MOVE`, những option khác bị bỏ qua theo hợp đồng `Files.move()`.

## 3. Không truyền option: điều gì xảy ra nếu đích đã tồn tại?

Mặc định, `Files.move(source, target)` **không yêu cầu thay thế đích đã tồn tại**. Nếu target đã có một mục khác, thao tác thường thất bại với `FileAlreadyExistsException`. Có những ngoại lệ/chi tiết implementation, nhưng trong mã ứng dụng hãy coi xung đột đích là một lỗi phải được xử lý, không dựa vào việc ghi đè ngầm.

Ví dụ: `draft.txt` và `final.txt` đều tồn tại. Lệnh:

```java
Files.move(Path.of("draft.txt"), Path.of("final.txt"));
```

không nên được dùng với kỳ vọng chắc chắn ghi đè `final.txt`. Nếu yêu cầu nghiệp vụ là **không thay thế** tệp đích, hãy giữ thao tác không có `REPLACE_EXISTING`, bắt lỗi xung đột và xem thêm các lưu ý về tính nguyên tử nếu cần đảm bảo mạnh trước cạnh tranh đồng thời.

Khi source và target trỏ tới **cùng một file**, `Files.move` có thể trả về mà không làm gì theo hợp đồng. Đừng coi mọi lần gọi `move` đều đồng nghĩa với thay đổi tên thực tế.

## 4. `REPLACE_EXISTING`: cho phép thay thế đích trong move thông thường

`StandardCopyOption.REPLACE_EXISTING` yêu cầu **thay thế mục ở đường dẫn đích nếu nó đã tồn tại**, theo các giới hạn của provider và loại mục:

```java
Files.move(source, target,
        StandardCopyOption.REPLACE_EXISTING);
```

Giả sử `source` và `target` đều là regular file. Sau khi thành công, target thường chứa mục/file được chuyển từ source, chứ không còn nội dung cũ.

Nhưng **không được** hiểu đây là cam kết “mọi loại đích đều thay được”: ví dụ đích là thư mục không rỗng có thể không thay thế được. Các hệ thống tệp có quy tắc khác nhau cho file đang mở, ACL, symbolic link, mục đặc biệt và đường dẫn ở nhiều provider.

### Ví dụ 2 — xung đột và thay thế có chủ đích

```java
// MoveReplaceDemo.java
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class MoveReplaceDemo {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("move-replace-");
        Path source = directory.resolve("source.txt");
        Path target = directory.resolve("target.txt");

        try {
            Files.writeString(source, "NEW", StandardCharsets.UTF_8);
            Files.writeString(target, "OLD", StandardCharsets.UTF_8);

            try {
                Files.move(source, target);
                System.out.println("unexpected: move without replace");
            } catch (FileAlreadyExistsException expected) {
                System.out.println("collision=true");
            }

            Files.move(source, target,
                    StandardCopyOption.REPLACE_EXISTING);

            System.out.println("targetText="
                    + Files.readString(target, StandardCharsets.UTF_8));
            System.out.println("sourceExists=" + Files.exists(source));
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(target);
            Files.deleteIfExists(directory);
        }
    }
}
```

```bash
javac MoveReplaceDemo.java
java MoveReplaceDemo
```

Kết quả điển hình trên filesystem cục bộ thông dụng, khi file tồn tại và quyền cho phép:

```text
collision=true
targetText=NEW
sourceExists=false
```

Ở đây việc thay thế là **chủ đích**; không có ý nghĩa nếu yêu cầu nghiệp vụ là “không bao giờ làm mất nội dung đích cũ”. Cũng không được kết luận rằng chỉ cần thêm `REPLACE_EXISTING` là thao tác trở nên nguyên tử.

### Tệp đích là symbolic link

Khi target là symbolic link, quy tắc thay thế của `Files.move(..., REPLACE_EXISTING)` liên quan đến **mục liên kết** chứ không phải tự động xóa target mà nó trỏ tới. Với source là symbolic link, `Files.move()` di chuyển chính **link** thay vì chuyển nội dung đích của link theo kiểu copy dereference.

Điều này quan trọng khi xử lý link hỏng hoặc link trỏ ra ngoài thư mục mong muốn. Nếu source/target liên quan symbolic link hoặc junction, hãy xác định hợp đồng thao tác dựa trên provider và bài toán; không giả định logic đối với file thông thường áp dụng y hệt cho mọi loại mục.

## 5. `ATOMIC_MOVE`: yêu cầu một bước di chuyển nguyên tử

Một thao tác di chuyển được thực hiện **nguyên tử** khi filesystem/provider có thể làm nó thành một thao tác đối với namespace của hệ thống tệp, thay vì ứng dụng/tiến trình khác quan sát trạng thái trung gian của một chuỗi thao tác như copy từng khối dữ liệu rồi xóa nguồn.

Trong Java:

```java
Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
```

`ATOMIC_MOVE` là **yêu cầu** đối với provider, không phải bảo đảm rằng bất kỳ hệ thống tệp nào cũng làm được. Khi không hỗ trợ atomic move giữa hai đường dẫn đã cho, Java có thể ném `AtomicMoveNotSupportedException`. Đây là tín hiệu để ứng dụng đưa ra quyết định nghiệp vụ: **dừng** nếu atomicity bắt buộc, hoặc dùng phương án không nguyên tử nếu được cho phép.

### Những gì atomic move thường hướng tới

- Thay đổi tên/đường dẫn của mục như **một bước** ở mức namespace của filesystem.
- Tránh một trạng thái nửa vời mà chương trình khác thấy một target mới đang được **copy dở** trong chính thao tác di chuyển nguyên tử.
- Làm nền tảng cho mô hình “chuẩn bị file riêng, rồi công bố tên cuối cùng” trên provider hỗ trợ.

### Những gì atomic move **không** tự bảo đảm

- **Không phải giao dịch nhiều file**: hai lệnh move riêng không được ghép thành một transaction.
- **Không chứng minh byte đã bền vững sau mất điện**: tính nguyên tử khi đổi tên không đồng nghĩa với `fsync`/độ bền lưu trữ.
- **Không bảo đảm không ai sửa nội dung file** trước, trong hoặc sau thao tác. Nó không tự đặt lock cho toàn bộ vòng đời đọc/ghi.
- **Không đảm bảo thành công khi source/target ở khác filesystem**, volume, mount hoặc provider.
- **Không bảo đảm fallback nguyên tử** nếu `ATOMIC_MOVE` bị từ chối.
- **Không bảo đảm xử lý target đã tồn tại giống mọi nền tảng**.

Nếu cần cả atomic visibility, quyền truy cập, tính bền vững khi crash và cập nhật nhiều file, phải thiết kế thêm cơ chế khác ngoài một `Files.move` duy nhất.

### Ví dụ 3 — yêu cầu `ATOMIC_MOVE` và xử lý trường hợp không hỗ trợ

```java
// AtomicMoveDemo.java
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class AtomicMoveDemo {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("atomic-demo-");
        Path source = directory.resolve("temporary.txt");
        Path target = directory.resolve("published.txt");

        try {
            Files.writeString(source, "COMPLETE", StandardCharsets.UTF_8);

            try {
                Files.move(source, target,
                        StandardCopyOption.ATOMIC_MOVE);
                System.out.println("atomicMove=performed");
                System.out.println("published="
                        + Files.readString(target, StandardCharsets.UTF_8));
            } catch (AtomicMoveNotSupportedException ex) {
                System.out.println("atomicMove=unsupported");
            }
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(target);
            Files.deleteIfExists(directory);
        }
    }
}
```

```bash
javac AtomicMoveDemo.java
java AtomicMoveDemo
```

Nếu provider hỗ trợ move nguyên tử trong thư mục tạm:

```text
atomicMove=performed
published=COMPLETE
```

Nếu provider báo đúng loại lỗi vì không hỗ trợ:

```text
atomicMove=unsupported
```

**Không có một stdout cố định trên mọi filesystem.** Những lỗi `IOException` khác (chẳng hạn không đủ quyền hoặc lỗi ổ đĩa) vẫn được truyền ra theo `throws Exception` và không bị nhận nhầm là “không hỗ trợ atomic”. Đây là ứng xử tốt hơn việc bắt mọi `IOException` rồi âm thầm thực hiện fallback.

Dù hai đường dẫn nằm trong **cùng thư mục**, atomicity vẫn cần được provider hỗ trợ; cùng thư mục làm khả năng hỗ trợ tốt hơn trong thực tế nhưng không thay thế kiểm tra lỗi.

## 6. Bẫy quan trọng: `ATOMIC_MOVE` khiến các option khác bị bỏ qua

Ví dụ dễ gây hiểu nhầm:

```java
Files.move(source, target,
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING);
```

Bạn có thể tưởng rằng lệnh trên cam kết đồng thời hai điều:

1. Thay tên nguyên tử.
2. Luôn thay thế target hiện có.

**Không đúng để suy ra như vậy.** Theo hợp đồng của `Files.move()`, **khi có `ATOMIC_MOVE`, các option khác bị bỏ qua**. Việc một atomic move có thay thế target hiện có hay không là **implementation-specific**, và không thể bảo đảm bằng cách chỉ kèm `REPLACE_EXISTING`.

Điều này cũng có nghĩa không thể viết một tiện ích “ghi đè atomically trên mọi hệ thống” chỉ bằng cách truyền cả hai hằng số mà không kiểm tra provider, loại mục và yêu cầu của ứng dụng.

| Lời gọi | Yêu cầu chính | Target đã tồn tại |
| --- | --- | --- |
| `Files.move(src, dst)` | Move thông thường, không yêu cầu atomicity | Thường thất bại, không tự cho phép overwrite |
| `Files.move(src, dst, REPLACE_EXISTING)` | Cho phép thay target theo quy tắc move thông thường | Có thể thay thế mục đích hợp lệ, nhưng không cam kết atomic |
| `Files.move(src, dst, ATOMIC_MOVE)` | Yêu cầu move nguyên tử | Hành vi nếu target tồn tại phụ thuộc implementation |
| `Files.move(src, dst, ATOMIC_MOVE, REPLACE_EXISTING)` | Vẫn là yêu cầu atomic move | `REPLACE_EXISTING` bị bỏ qua; hành vi target tồn tại vẫn phụ thuộc implementation |

Bảng phân biệt **quy tắc API** với **hành vi thường gặp**. Muốn biết mức đảm bảo về thay thế target trên một nền tảng cụ thể, cần xét hợp đồng/documentation của filesystem/provider và các trường hợp thực tế. Việc kiểm tra `Files.exists(target)` trước move không đủ loại bỏ race: file có thể xuất hiện hoặc biến mất ngay sau kiểm tra.

## 7. Khi nào nên bắt buộc nguyên tử? Khi nào cho phép fallback?

Ví dụ nghiệp vụ:

- Công bố một file cấu hình hoàn chỉnh bằng tên được consumer theo dõi: atomic rename thường quan trọng để consumer không thấy file đang ghi dở.
- Đổi tên một file cá nhân trong thư mục: có thể chấp nhận move không nguyên tử nếu thao tác và hệ thống tệp cho phép.
- Chuyển file từ ổ đĩa nội bộ sang ổ mạng: có thể phải copy rồi xóa; nếu ứng dụng cần transactional semantics thì phải có thiết kế riêng.
- Thay thế một file quan trọng đang được ứng dụng khác đọc: phải xác định đồng thời chính sách tồn tại đích, phiên bản nội dung, atomicity và cách consumer mở file.

### Chính sách A: không có atomicity thì thất bại

```java
// Minh họa; source và target đã là Path hợp lệ.
try {
    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
} catch (AtomicMoveNotSupportedException ex) {
    // Không tự copy/delete khi atomicity là yêu cầu bắt buộc.
    throw ex;
}
```

Đây là **đoạn mã minh họa**, cần import tương ứng và nằm trong phương thức cho phép `IOException`. Trong thiết kế này, lỗi “atomic không được hỗ trợ” là lỗi điều kiện tiên quyết, không phải lý do để âm thầm dùng cách yếu hơn.

### Chính sách B: chỉ fallback nếu nghiệp vụ cho phép

```java
// Minh họa; các Path đã có sẵn, không phải chương trình độc lập.
try {
    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
} catch (AtomicMoveNotSupportedException ex) {
    // Chỉ dùng khi đã chấp nhận non-atomic và chính sách đích.
    Files.move(source, target);
}
```

**Giới hạn của fallback:** lệnh thứ hai **không bảo đảm nguyên tử**. Nếu nó phải copy rồi delete và phát sinh lỗi ở giữa, trạng thái nguồn/đích có thể không như dự kiến. Cũng phải xử lý trường hợp target bị tiến trình khác tạo ra, thư mục cha thay đổi hoặc hệ thống tệp gặp lỗi. Nếu fallback muốn **thay thế** đích, phải quyết định riêng việc dùng `REPLACE_EXISTING`; nó cũng không tạo ra atomicity.

Chỉ bắt `AtomicMoveNotSupportedException` cho quyết định không hỗ trợ atomicity. Không nên catch mọi `IOException` rồi fallback, vì lỗi quyền truy cập, thiếu dung lượng hoặc hỏng filesystem không được sửa bằng cách thực hiện lại dưới ngữ nghĩa yếu hơn.

## 8. Move giữa các ổ đĩa/filesystem

Một rename cấp filesystem thường dễ thực hiện nguyên tử **trong cùng filesystem** hơn là qua ranh giới volume/mount. Ví dụ:

```text
Ổ/volume A                  Ổ/volume B
/data/draft.bin    ------>  /archive/draft.bin
```

Nếu filesystem không hỗ trợ move nguyên tử giữa hai bên, yêu cầu `ATOMIC_MOVE` có thể dẫn đến `AtomicMoveNotSupportedException`. Một move thông thường được provider cho phép có thể được thực hiện bằng copy dữ liệu sang đích rồi xóa nguồn, hoặc thất bại tùy loại mục và provider. Không được giả định mọi thư mục nhiều cấp đều được copy đệ quy chỉ vì gọi `Files.move` một lần.

Một khác biệt quan trọng: **khi move không nguyên tử ném `IOException`, trạng thái file có thể không xác định**. Source và target có thể đều còn, target có thể không hoàn chỉnh, hoặc một phần metadata chưa được sao chép. Ứng dụng phải kiểm tra/phục hồi theo chính sách thay vì mặc định “move lỗi thì chắc chắn chưa thay đổi gì”.

| Kịch bản | Rủi ro hoặc kết quả |
| --- | --- |
| Rename trong cùng thư mục của filesystem hỗ trợ | Thường là thao tác đơn giản, atomicity chỉ được yêu cầu/kiểm tra khi dùng `ATOMIC_MOVE` |
| Move giữa hai thư mục cùng filesystem | Có thể được hỗ trợ nguyên tử; vẫn phụ thuộc provider |
| Move khác volume, mount, provider | Yêu cầu atomic có thể bị từ chối; move thông thường có thể thực hiện nhiều bước hoặc thất bại |
| Move mục là thư mục | Hợp đồng phụ thuộc provider, trạng thái target, ranh giới filesystem, cấu trúc thư mục |
| Move một symbolic link | Move bản thân link, không mặc định copy nội dung target |
| Move khi file đang được mở | Hành vi OS/provider quyết định; có thể thành công hoặc gặp lỗi chia sẻ/quyền |

Không lấy việc chương trình chạy thành công trên ổ đĩa nội bộ làm bằng chứng rằng nó chắc chắn hoạt động trên network share, container volume, mounted drive hoặc provider lưu trữ ảo.

## 9. Mẫu công bố file qua tên tạm, rồi chuyển sang tên đích

Một ứng dụng thường muốn producer tạo nội dung **đầy đủ** trước khi người khác đọc tên đích:

```text
(1) Tạo file tạm tại thư mục đích
          |
(2) Ghi đủ nội dung và đóng writer
          |
(3) Files.move(temp, finalName, ATOMIC_MOVE)
          |
(4) Người đọc truy cập tên finalName
```

Lý do đặt file tạm **trong cùng thư mục với đích**: tăng khả năng các mục ở cùng filesystem, giảm khả năng move trở thành chuyển volume. Nhưng điều này không tự chứng minh provider hỗ trợ `ATOMIC_MOVE`.

### Ví dụ 4 — công bố một file mới chỉ khi có atomic move

Ví dụ chọn **tên đích mới, chưa tồn tại** khi bắt đầu, tránh cố mô phỏng cập nhật/ghi đè một target hiện có. Nó yêu cầu atomic move; nếu không được hỗ trợ thì báo lỗi và **không có fallback ngầm**.

```java
// AtomicPublishDemo.java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class AtomicPublishDemo {
    static void publishNew(Path target, String text) throws IOException {
        Path absoluteTarget = target.toAbsolutePath();
        Path parent = absoluteTarget.getParent();
        Path temporary = Files.createTempFile(parent, ".draft-", ".tmp");

        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);

            // Yêu cầu atomic move; không âm thầm dùng copy/delete.
            // Chính sách target trùng tên trong cạnh tranh phải được
            // xử lý riêng: ATOMIC_MOVE không bảo đảm no-overwrite.
            Files.move(temporary, absoluteTarget,
                    StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("publish-demo-");
        Path published = directory.resolve("result.txt");

        try {
            publishNew(published, "complete");
            System.out.println("published="
                    + Files.readString(published, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(published);
            Files.deleteIfExists(directory);
        }
    }
}
```

```bash
javac AtomicPublishDemo.java
java AtomicPublishDemo
```

Khi provider hỗ trợ atomic move, không có xung đột đích và các thao tác thành công:

```text
published=complete
```

Nếu không hỗ trợ atomic move, chương trình **ném `AtomicMoveNotSupportedException`**, không in dòng `published=...`. Đây là kết quả được thiết kế để **không hạ cấp ngữ nghĩa một cách âm thầm**. `finally` cố dọn file tạm trong cả hai trường hợp.

**Cảnh báo về cạnh tranh:** đây không phải API “create only if absent” mang bảo đảm hoàn chỉnh cho các hệ thống khác nhau. Một tác vụ khác có thể tạo target sau khi mã chọn tên. Ngữ nghĩa của `ATOMIC_MOVE` khi target tồn tại là implementation-specific, nên nếu yêu cầu nghiêm ngặt **không bao giờ ghi đè file của người khác**, phải thiết kế chính sách phối hợp/đặt tên hoặc dùng API tạo mới phù hợp, thay vì tin vào `ATOMIC_MOVE` cộng một bước `exists()`.

### Nếu cần ghi đè bản cấu hình cũ?

Một mô hình thực tế là ghi toàn bộ phiên bản mới vào file tạm cùng filesystem, hoàn tất ghi, rồi công bố dưới tên ổn định. Nhưng cần thêm những quyết định:

- Có cho phép **replace** file đích không? Nếu cho phép, provider có atomic replace với mục đang tồn tại theo hợp đồng được kiểm chứng không?
- File đang được tiến trình khác mở thì hành vi ra sao? Đặc biệt Windows và Unix có sự khác biệt về rename/open handle.
- Có yêu cầu dữ liệu sống sót sau mất điện không? Việc đóng writer và đổi tên nguyên tử **không tự đảm bảo durability**; cần xem `FileChannel.force(...)`, đồng bộ metadata/thư mục và những bảo đảm của OS/filesystem.
- Có nhiều writer cạnh tranh không? Cần cơ chế version/lock/compare-and-swap phù hợp thay vì giả định atomic rename tự ngăn lost update.
- Consumer đang đọc một handle đã mở từ trước có cần tự chuyển sang phiên bản mới không? Không thể mặc định một rename sẽ làm nội dung của handle đang mở thay đổi theo cách ứng dụng mong muốn.

Đối với một ứng dụng cần tính nhất quán mạnh, phải mô tả **cấp độ bảo đảm** chứ không dùng từ “atomic” cho mọi tính chất.

## 10. Thứ tự ghi dữ liệu, đóng luồng và move

Không chuyển file sang tên công bố khi writer vẫn giữ dữ liệu trong bộ đệm:

```java
// Minh họa: temp và finalPath là Path đã có,
// charset UTF_8; code nằm trong phương thức throws IOException.
try (BufferedWriter writer =
        Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
    writer.write("new configuration");
}

// Writer đã đóng trước khi công bố tên mới.
Files.move(temp, finalPath, StandardCopyOption.ATOMIC_MOVE);
```

Khi đóng `BufferedWriter`, dữ liệu chưa đẩy trong bộ đệm được xử lý và luồng bên dưới được đóng. Tuy nhiên, **close thành công không tự đồng nghĩa dữ liệu được đồng bộ bền vững xuống thiết bị**. Với bài toán chống mất điện, cần xác định riêng cơ chế durability.

Nếu move không có `ATOMIC_MOVE`, kết quả có thể vẫn thành công và trong cùng filesystem thường được thực hiện như rename, nhưng **API không bắt buộc** atomicity trong mọi hoàn cảnh. Chỉ khi **yêu cầu** atomicity và thao tác thành công theo hợp đồng provider bạn mới có thể dựa vào thuộc tính này.

## 11. Các ngoại lệ thường gặp khi move

| Exception | Ý nghĩa thường gặp | Cách xử lý |
| --- | --- | --- |
| `NoSuchFileException` | Nguồn hoặc một thành phần cần thiết không tồn tại | Xem lại source/parent; xử lý thay đổi đồng thời |
| `FileAlreadyExistsException` | Target xung đột trong thao tác không thay thế | Báo xung đột hoặc chuyển sang nhánh có chính sách replace rõ ràng |
| `AtomicMoveNotSupportedException` | Provider không hỗ trợ move nguyên tử cho cặp đường dẫn/tình huống | Nếu atomic bắt buộc: dừng; nếu không: fallback được chấp thuận |
| `DirectoryNotEmptyException` | Cố thay một thư mục đích không rỗng trong tình huống không được hỗ trợ | Không tự xóa nội dung của thư mục mà không có yêu cầu |
| `AccessDeniedException` | Provider báo thiếu quyền truy cập | Báo lỗi hoặc yêu cầu quyền đúng; không tự đổi quyền tùy tiện |
| `FileSystemException` | Lỗi filesystem có thông tin bổ sung | Log thông tin source/target và nguyên nhân; xử lý theo nghiệp vụ |
| `IOException` | Những lỗi I/O còn lại | Không giả định source/target bất biến sau một non-atomic failure |

Không phải mọi provider đều ném đúng subtype như bảng cho mọi lỗi. Đôi khi lỗi chung `IOException` được dùng thay subtype cụ thể. Khi ghi log, nên lưu path nguồn/đích theo cách không lộ dữ liệu nhạy cảm và giữ stack trace/exception liên quan.

### Không bắt mọi lỗi rồi thử ghi đè

Đây là một anti-pattern:

```java
// Đoạn minh họa cách làm KHÔNG AN TOÀN:
try {
    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
} catch (IOException ex) {
    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
}
```

Lý do: `IOException` có thể là lỗi quyền truy cập hoặc vấn đề hỏng filesystem, không nhất thiết do thiếu hỗ trợ atomic. Cách fallback trên cũng có thể **ghi đè một target khác** vốn được tạo trong lúc chạy. Ngoài ra sau một số lỗi non-atomic, trạng thái thực tế có thể phải được kiểm tra trước khi quyết định lại.

### Kiểm tra `exists()` không loại bỏ race condition

```java
if (!Files.exists(target)) {
    Files.move(source, target);
}
```

Đây là **đoạn mã minh họa có race**, không phải cách bảo đảm target vẫn vắng mặt tại thời điểm move. Đây là vấn đề **TOCTOU** (time of check to time of use). Một thread/process khác có thể tạo target ngay sau bước kiểm tra.

Tùy yêu cầu, hãy dựa vào ngữ nghĩa operation do provider bảo đảm, xử lý exception chính thức, hoặc dùng cơ chế phối hợp giữa những writer. Không ghép một `exists()` rồi suy ra “chắc chắn sẽ move thành công”.

## 12. So sánh với API truyền thống `File.renameTo()`

Java trước NIO.2 thường dùng:

```java
// Đoạn minh họa với oldFile và newFile thuộc java.io.File.
boolean renamed = oldFile.renameTo(newFile);
```

`File.renameTo(File)` vẫn tồn tại nhưng thường kém thích hợp cho logic cần kiểm soát lỗi:

- Trả về `boolean`, nên khi thất bại thường **không cung cấp chi tiết bằng exception I/O chuyên biệt**.
- Hành vi phụ thuộc nền tảng; có thể không làm được qua filesystem hoặc khi file đang mở.
- Không có đối số `ATOMIC_MOVE` tương đương để **yêu cầu** atomicity theo API này.
- Hành vi đối với target hiện hữu và nhiều loại mục khác nhau có thể khó dự đoán giữa các hệ thống.

Đối với mã Java 17+ cần xử lý rõ ràng, ưu tiên:

```java
Files.move(oldFile.toPath(), newFile.toPath());
```

Đây là đoạn minh họa. Chuyển `File` sang `Path` bằng `toPath()` không tự move hay tạo file; hành động thật diễn ra ở `Files.move(...)`.

| Tiêu chí | `File.renameTo()` | `Files.move()` |
| --- | --- | --- |
| Đổi tên trong cùng thư mục | Có thể làm được | Có |
| Chuyển sang thư mục khác | Phụ thuộc nền tảng | Có API rõ ràng, vẫn phụ thuộc filesystem/provider |
| Báo lỗi I/O | Thường là `false` | `IOException` và subtype hữu ích |
| Cho phép replace target có chủ đích | Không có option NIO.2 tương đương | `REPLACE_EXISTING` cho move thường |
| Yêu cầu atomic move | Không có tùy chọn chuẩn tương tự | `ATOMIC_MOVE` |
| Khả năng xử lý Path/provider | `File` gắn với đường dẫn truyền thống | `Path` là abstraction của filesystem provider |

## 13. Di chuyển thư mục, file mở và symbolic link

### Thư mục

`Files.move()` có thể áp dụng với thư mục, nhưng không có nghĩa nó sẽ thực hiện **recursive copy toàn bộ cây dữ liệu sang ổ đĩa khác** trong mọi tình huống. Trong cùng filesystem, đổi tên thư mục có thể là thao tác metadata; qua filesystem khác, yêu cầu có thể không được hỗ trợ. Đích là thư mục không rỗng cũng là trường hợp đặc biệt.

Không nhầm:

```text
đổi tên thư mục (move directory entry)
```

với:

```text
duyệt từng file con -> copy -> verify -> delete source tree
```

Hai quy trình có đặc tính lỗi, thời gian, quyền và atomicity rất khác nhau.

### File đang được mở

Một số hệ thống cho phép rename/move một file khi tiến trình vẫn giữ handle đọc; một số hệ thống có thể từ chối do quy tắc chia sẻ/quyền. Java không cam kết cùng một kết quả trên Windows, Unix, network share và mọi provider. Đừng dùng kết quả một test trên máy cá nhân làm hợp đồng đa nền tảng.

### Symbolic link

Với source là symbolic link, `Files.move` di chuyển **chính đối tượng link**. Link có thể tiếp tục trỏ tới đường dẫn cũ và trở thành broken link sau move nếu giá trị target của link là relative path và ngữ cảnh thư mục thay đổi. Nếu ứng dụng muốn chuyển nội dung đích của link, cần xử lý theo chính sách riêng.

### Thuộc tính và quyền

Việc di chuyển mục có thể tác động metadata theo cách phụ thuộc provider/volume. Đừng mặc định mọi timestamp, ACL, owner, file key hoặc thuộc tính đặc thù vẫn được giữ nguyên, đặc biệt ở move qua filesystem khác. Nếu nghiệp vụ cần các thuộc tính nhất định, hãy đọc/kiểm tra chúng theo API thích hợp sau khi thực hiện.

## 14. Checklist chọn cách move phù hợp

1. **Mục tiêu là gì?** Đổi tên trong cùng thư mục, di chuyển sang nơi khác hay công bố phiên bản mới?
2. **Nguồn có tồn tại?** Không chỉ kiểm tra trước; vẫn cần xử lý kết quả I/O thật và lỗi do thay đổi đồng thời.
3. **Target có thể tồn tại không?** Xác định rõ “từ chối”, “cho phép thay thế”, hoặc “đổi sang tên khác”.
4. **Có cần atomicity không?** Nếu cần, yêu cầu `ATOMIC_MOVE` và thất bại có kiểm soát khi không hỗ trợ.
5. **Có cần replace atomic không?** Không được mặc định `ATOMIC_MOVE + REPLACE_EXISTING` là bảo đảm di động; behavior của target tồn tại là implementation-specific.
6. **Cùng filesystem hay không?** Đặt temp cùng thư mục đích để tăng khả năng atomic move, nhưng không suy ra chắc chắn.
7. **Đã hoàn tất ghi và đóng writer chưa?** Move không tự đảm bảo dữ liệu còn trong buffer của writer đã được đẩy xuống.
8. **Có yêu cầu chống mất điện/durability không?** Tách khỏi vấn đề atomic rename; có thể cần `FileChannel.force()` và các bước đồng bộ metadata.
9. **Có nguồn/đích là thư mục, symbolic link hoặc file đang mở?** Xét hợp đồng từng provider và loại mục.
10. **Nếu move lỗi thì phục hồi thế nào?** Đặc biệt với non-atomic move, không mặc định source/target giữ nguyên trạng thái.
11. **Có nhiều writer cạnh tranh không?** Cần một giao thức đồng bộ/phiên bản thay vì chỉ dựa vào tên file.
12. **Làm sao kiểm thử?** Thử trường hợp file đích có/không có, provider hỗ trợ/không hỗ trợ atomic move, quyền truy cập và crash/failure phù hợp.

## 15. Lỗi phổ biến và cách khắc phục

| Lỗi hiểu hoặc thực hành | Vì sao sai | Cách khắc phục |
| --- | --- | --- |
| Gọi `Files.move()` mặc định rồi nói chắc chắn atomic | API không yêu cầu atomic nếu không có option | Sử dụng `ATOMIC_MOVE` khi cần và xử lý exception |
| Nghĩ `REPLACE_EXISTING` cũng đồng nghĩa atomic | Replace và atomic là các thuộc tính khác nhau | Phân biệt rõ điều kiện replace và atomicity |
| Nghĩ `ATOMIC_MOVE, REPLACE_EXISTING` chắc chắn ghi đè atomically | Khi có atomic option, các option còn lại bị bỏ qua | Xác minh ngữ nghĩa provider và chính sách target |
| Bắt `IOException` rồi fallback non-atomic | Che lỗi thực sự, có thể làm mất tính nhất quán | Chỉ fallback khi gặp `AtomicMoveNotSupportedException` và được chấp thuận |
| Tạo temp ở volume khác đích | Atomic move thường không hỗ trợ qua filesystem | Tạo temp cạnh target khi phù hợp |
| Move file trước khi đóng `BufferedWriter` | Nội dung có thể còn trong buffer | Hoàn tất ghi/đóng writer trước khi move |
| Coi atomic rename là bảo đảm chống mất điện | Namespace atomicity khác độ bền dữ liệu | Thiết kế đồng bộ dữ liệu và metadata riêng |
| Sau lỗi non-atomic move, giả sử source còn nguyên | Có thể cả hai file còn hoặc target dang dở | Kiểm tra trạng thái và phục hồi cẩn trọng |
| Tự dùng `File.renameTo()` trong code cần nguyên nhân lỗi rõ | Kết quả boolean thiếu nhiều thông tin | Dùng `Files.move()` và bắt lỗi cụ thể |
| Coi move thư mục khác ổ là recursive copy | Không phải hợp đồng move chung | Thiết kế copy/verify/delete riêng nếu thật sự cần |
| Kiểm tra `!exists(target)` rồi tin không ai tạo target | Có TOCTOU | Dựa vào hợp đồng operation và đồng bộ giữa writer |
| Coi mọi symlink là file đích của nó | Move link không mặc định move nội dung target | Phân biệt link và target |

## 16. Tự kiểm tra

**Câu 1.** Đổi `a.txt` thành `b.txt` cùng thư mục có phải một dạng move không?

**Đáp án:** Có. Với NIO.2, dùng `Files.move(source, source.resolveSibling("b.txt"))`.

**Câu 2.** `Files.move(source, target)` mặc định có cam kết atomicity không?

**Đáp án:** Không. Nếu cần move nguyên tử, phải yêu cầu `StandardCopyOption.ATOMIC_MOVE` và xử lý trường hợp không hỗ trợ.

**Câu 3.** Hai file `source.txt` và `target.txt` cùng tồn tại. Cần thay target theo quy tắc move thông thường thì chọn option nào?

**Đáp án:** `StandardCopyOption.REPLACE_EXISTING`, lưu ý quy tắc thay thế phụ thuộc loại mục/provider và không tự cam kết atomicity.

**Câu 4.** Vì sao `Files.move(src, dst, ATOMIC_MOVE, REPLACE_EXISTING)` không có nghĩa chắc chắn “atomic replace”?

**Đáp án:** Khi có `ATOMIC_MOVE`, các option khác bị bỏ qua; hành vi khi target đã tồn tại là implementation-specific.

**Câu 5.** Một file ở ổ A cần move sang ổ B; có luôn thực hiện `ATOMIC_MOVE` được không?

**Đáp án:** Không. Qua filesystem/volume có thể không hỗ trợ nguyên tử và ném `AtomicMoveNotSupportedException`.

**Câu 6.** Có nên bắt mọi `IOException` của atomic move để tự gọi non-atomic move không?

**Đáp án:** Không. Chỉ fallback khi nguyên nhân phù hợp (thường là `AtomicMoveNotSupportedException`) và nghiệp vụ cho phép từ bỏ atomicity.

**Câu 7.** `Files.move` trả về `Path` nào khi thành công?

**Đáp án:** Đường dẫn đại diện **target**.

**Câu 8.** Atomic move có đủ để bảo đảm dữ liệu vẫn còn sau mất điện không?

**Đáp án:** Không. Atomicity của thao tác đổi tên không đồng nghĩa durability của nội dung hoặc metadata.

**Câu 9.** Khi source là symbolic link, `Files.move` chuyển nội dung file ở đầu kia của link hay chuyển chính link?

**Đáp án:** Chuyển **chính symbolic link**, theo hợp đồng move.

**Câu 10.** Vì sao không thể mặc định non-atomic move lỗi thì “chưa có gì thay đổi”?

**Đáp án:** Có thể thao tác đã copy được một phần hoặc thay đổi metadata trước khi thất bại; API không bảo đảm trạng thái source/target sau lỗi non-atomic.

**Câu 11.** Tạo file tạm ở cùng thư mục đích có đảm bảo mọi move nguyên tử không?

**Đáp án:** Không. Nó làm giảm rủi ro khác filesystem, nhưng provider vẫn có thể không hỗ trợ `ATOMIC_MOVE`.

**Câu 12.** Khi ứng dụng cần publish file mới mà không cho phép bất kỳ ghi đè nào, có nên chỉ dùng `ATOMIC_MOVE` và `Files.exists(target)==false` không?

**Đáp án:** Không đủ. `exists()` dễ bị race và khi atomic move gặp target đã tồn tại, ngữ nghĩa xử lý phụ thuộc implementation. Cần chính sách tên, khóa/điều phối hoặc cơ chế tạo mới và kiểm tra tương ứng với yêu cầu.

**Ghi nhớ:** `Files.move()` là API ưu tiên để **đổi tên hoặc di chuyển** tệp. `REPLACE_EXISTING` giải quyết chính sách **đích đã tồn tại** trong move thông thường; `ATOMIC_MOVE` yêu cầu **di chuyển nguyên tử** nhưng có thể không được hỗ trợ, và khi dùng nó, **các option còn lại bị bỏ qua**. Atomic rename không tương đương giao dịch nhiều file hoặc bảo đảm độ bền sau mất điện. Chỉ cho phép fallback non-atomic nếu ứng dụng chủ động chấp nhận rủi ro, đồng thời luôn xử lý trạng thái và exception của thao tác thật.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-operations-005">delete khác deleteIfExists</a>

<details>
<summary>Xem chi tiết</summary>

# Files.delete khác Files.deleteIfExists trong Java

`Files.delete(Path)` và `Files.deleteIfExists(Path)` đều dùng để **xóa một entry của hệ thống tệp**: tệp thông thường, symbolic link hoặc thư mục rỗng. Khác biệt quan trọng nhất không phải ở cách xóa, mà ở **cách báo cáo trường hợp đường dẫn không tồn tại**:

- **`Files.delete(path)`:** không trả giá trị; xóa thành công thì đi tiếp. Nếu không có entry cần xóa, thông thường ném **`NoSuchFileException`**.
- **`Files.deleteIfExists(path)`:** trả **`true`** nếu xóa được một entry, **`false`** nếu không có entry để xóa. Những lỗi xóa thật sự khác **vẫn có thể ném `IOException`**.

Khi chọn API, câu hỏi nên là: **"Việc không có tệp là lỗi nghiệp vụ, hay là trạng thái đã đạt mục tiêu?"** Không có phương thức nào tự động xóa cả cây thư mục hoặc bảo đảm việc xóa sẽ luôn thành công.

## 1. Đặt hai phương thức cạnh nhau

Cả hai thuộc `java.nio.file.Files`, dùng `Path` thuộc `java.nio.file.Path` và có thể ném `IOException`:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// Đoạn minh họa: đặt trong phương thức khai báo throws IOException.
Path file = Path.of("data", "report.txt");

Files.delete(file);                   // void, thiếu file -> ngoại lệ
boolean removed = Files.deleteIfExists(file); // true hoặc false
```

Cú pháp chính:

```java
public static void delete(Path path) throws IOException

public static boolean deleteIfExists(Path path) throws IOException
```

Đây là **mô tả chữ ký API**, không phải hai lệnh khai báo có thể dán tự do vào phương thức. `delete` có kiểu trả về `void`; `deleteIfExists` trả về `boolean`.

| Tình huống | `Files.delete(path)` | `Files.deleteIfExists(path)` |
| --- | --- | --- |
| Entry tồn tại và xóa thành công | Hoàn tất, không có giá trị trả về | Trả `true` |
| Entry không tồn tại | Ném `NoSuchFileException` trong trường hợp thông thường | Trả `false` |
| Không có quyền xóa, lỗi hệ thống tệp | Ném `IOException` hoặc ngoại lệ phù hợp | **Vẫn có thể ném ngoại lệ** |
| Thư mục còn chứa entry | Thường ném `DirectoryNotEmptyException` | Tương tự; **không** trả `false` để thay lỗi |
| Symbolic link tồn tại | Xóa **liên kết**, không xóa đích | Xóa **liên kết**, trả `true` nếu thành công |
| Có cần kiểm tra `Files.exists` trước không? | Không; gọi trực tiếp và xử lý kết quả/lỗi | Không; gọi trực tiếp và xử lý `boolean`/lỗi |
| Có xóa đệ quy không? | Không | Không |

**Cảnh báo:** `false` của `deleteIfExists` **không phải** cách báo “không có quyền”, “thư mục không rỗng” hay “I/O hỏng”. Những tình huống đó không được đánh đồng với “đường dẫn không tồn tại”.

## 2. `Files.delete(path)`: vắng mặt là lỗi

Nếu chương trình cần xóa **một đối tượng mà nghiệp vụ yêu cầu phải tồn tại**, `delete` giúp phát hiện trường hợp bất thường:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

static void removeExpectedFile(Path path) throws IOException {
    Files.delete(path);
}
```

Đoạn mã là **phương thức minh họa** cần đặt trong class. Nếu `path` không có entry tương ứng, `Files.delete(path)` sẽ báo lỗi khi file được coi là không tồn tại, thay vì giả vờ “xóa thành công”.

Có thể xử lý riêng trường hợp vắng mặt:

```java
import java.nio.file.NoSuchFileException;

try {
    Files.delete(path);
} catch (NoSuchFileException e) {
    System.err.println("Không có đường dẫn cần xóa: " + path);
}
```

Đoạn mã minh họa giả định đã có `Path path`, các import `Files` và được đặt trong phương thức xử lý/khai báo các `IOException` khác. Không nên bắt `IOException` rộng rồi coi tất cả là “không tìm thấy file”, vì việc xóa có thể thất bại do nguyên nhân nghiêm trọng hơn.

**Ví dụ phù hợp:** quá trình di chuyển file tạm sang nơi lưu chính đã ghi nhận rằng một file nhất định phải tồn tại, và bước dọn dẹp yêu cầu xác minh giả định này. Nếu file đã mất ngoài ý muốn, việc ném `NoSuchFileException` có thể giúp phát hiện lỗi quy trình.

## 3. `Files.deleteIfExists(path)`: vắng mặt là một kết quả hợp lệ

Nếu mục tiêu chỉ là **“sau bước dọn dẹp không còn entry ở đường dẫn này”**, thì không có entry ngay từ đầu thường không phải lỗi:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

static boolean removeOptionalFile(Path path) throws IOException {
    return Files.deleteIfExists(path);
}
```

Phương thức minh họa trả:

- `true` nếu lời gọi **thực sự xóa được entry**;
- `false` nếu entry **không tồn tại**, nên không có gì để xóa;
- Ngoại lệ nếu xảy ra lỗi truy cập hoặc lỗi I/O phù hợp, chẳng hạn thư mục không rỗng.

Điều quan trọng là **“xóa nếu tồn tại” không đồng nghĩa “bỏ qua mọi lỗi”**. Chẳng hạn:

```java
boolean deleted = Files.deleteIfExists(path);

if (deleted) {
    System.out.println("Đã xóa một entry");
} else {
    System.out.println("Entry đã không tồn tại");
}
```

Đây là mã minh họa với `path` đã khai báo và `IOException` đã được xử lý/khai báo ở phương thức bao quanh. Nếu lời gọi ném lỗi thì chương trình **không** đi tiếp vào nhánh `if` như thể `deleted=false`.

### Có thể gọi nhiều lần không?

Nếu không có tác nhân khác thay đổi đường dẫn, cùng một file:

```text
Lần 1: deleteIfExists(path) -> true  (file đã bị xóa)
Lần 2: deleteIfExists(path) -> false (không có entry để xóa)
```

Đây là hành vi thuận tiện cho các thao tác dọn file tạm, nhưng chỉ là tính **idempotent đối với trạng thái vắng mặt trong điều kiện phù hợp**. Nếu một tiến trình khác tạo lại file giữa các lần gọi, lần sau có thể xóa một file **mới** tại cùng tên; vì vậy không nên xem lời gọi lặp là an toàn tuyệt đối trong môi trường cạnh tranh.

## 4. Chương trình Java 17+ độc lập, kiểm thử cả hai phương thức

Ví dụ tạo một thư mục tạm riêng, tạo hai file nhỏ, rồi kiểm chứng hành vi xóa khi entry có mặt và khi vắng mặt. Ví dụ **không đụng vào file do người dùng chỉ định** và dọn thư mục đã tạo trong khối `finally`.

Lưu nguyên mã vào file **`DeleteVsDeleteIfExistsDemo.java`**:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public class DeleteVsDeleteIfExistsDemo {

    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("java-delete-demo-");
        Path optional = directory.resolve("optional.txt");
        Path required = directory.resolve("required.txt");

        try {
            Files.writeString(optional, "one", StandardCharsets.UTF_8);

            System.out.println("Optional first removal: " +
                    Files.deleteIfExists(optional));
            System.out.println("Optional second removal: " +
                    Files.deleteIfExists(optional));

            Files.writeString(required, "two", StandardCharsets.UTF_8);
            Files.delete(required);
            System.out.println("Required removal: done");

            try {
                Files.delete(required);
            } catch (NoSuchFileException expected) {
                System.out.println(
                        "Required missing: NoSuchFileException");
            }

            System.out.println("Directory still exists: " +
                    Files.isDirectory(directory));
        } finally {
            // Dọn duy nhất những entry do ví dụ vừa tạo.
            Files.deleteIfExists(optional);
            Files.deleteIfExists(required);
            Files.deleteIfExists(directory);
        }
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac -encoding UTF-8 DeleteVsDeleteIfExistsDemo.java
java DeleteVsDeleteIfExistsDemo
```

**Stdout khi hệ thống tệp tạm hoạt động bình thường:**

```text
Optional first removal: true
Optional second removal: false
Required removal: done
Required missing: NoSuchFileException
Directory still exists: true
```

Giải thích theo thứ tự:

1. `Files.createTempDirectory` tạo thư mục riêng để bài thử không xóa file có sẵn. Hai lời gọi `resolve` tạo **đường dẫn**, chưa tạo nội dung file.
2. `Files.writeString(optional, ...)` tạo file tạm thứ nhất. Lần gọi `deleteIfExists` đầu trả `true`.
3. Vì file đã không còn, lần gọi `deleteIfExists` tiếp theo trả `false`, không ném `NoSuchFileException`.
4. `Files.writeString(required, ...)` tạo file thứ hai. `Files.delete(required)` xóa thành công nên không có giá trị trả về.
5. `Files.delete(required)` lần hai ném `NoSuchFileException` và được `catch` xử lý riêng để in dòng dự kiến.
6. Thư mục tạm vẫn tồn tại: **xóa hai file bên trong không đồng nghĩa xóa thư mục cha**. Khối `finally` sau đó thử dọn file và thư mục do chính ví dụ tạo.

Các điều kiện quyền hạn, lỗi filesystem hoặc tiến trình ngoài can thiệp có thể làm bài thử ném `IOException`, vì vậy stdout được mô tả cho **điều kiện I/O bình thường**. Ví dụ không giả định đường dẫn tạm có tên cố định.

## 5. Xóa thư mục: chỉ xóa khi phù hợp, không đệ quy

Cả hai API đều có thể xóa **thư mục rỗng**. Nếu thư mục còn chứa các entry, việc xóa thường thất bại với `DirectoryNotEmptyException` (ngoại lệ cụ thể này không được mọi provider/hệ điều hành bảo đảm trong mọi trường hợp).

Ví dụ minh họa:

```java
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;

static void deleteEmptyDirectory(Path directory)
        throws IOException {
    try {
        Files.delete(directory);
    } catch (DirectoryNotEmptyException e) {
        System.err.println("Thư mục còn chứa entry: " + directory);
        throw e;
    }
}
```

Đây là **phương thức minh họa** cần đặt trong class. Nếu dùng `Files.deleteIfExists(directory)`, phương thức **không** tự dọn tất cả file con để sau đó trả `true`; khi thư mục còn nội dung, nó vẫn có thể ném ngoại lệ I/O.

### Có thể xóa cả cây thư mục không?

Không có tham số `recursive=true` cho hai phương thức này. Để dọn cây thư mục do ứng dụng tạo, có thể dùng `Files.walkFileTree` với `FileVisitor`, xóa file trước, rồi xóa thư mục trong `postVisitDirectory`. Với dữ liệu không tin cậy hoặc folder người dùng, đây là thao tác **nguy hiểm nếu thiếu giới hạn đường dẫn, quyền và chính sách symlink**; không nên chạy mã xóa đệ quy chỉ để xử lý một `DirectoryNotEmptyException`.

Nếu mục tiêu chỉ là xóa một file, **đừng** triển khai xóa đệ quy. Việc khác biệt giữa file và thư mục nằm ở hệ thống tệp thực tế, không nằm ở phần mở rộng như `.txt`.

## 6. Symbolic link: xóa liên kết, không xóa file đích

Nếu `path` là **symbolic link**, cả `Files.delete(path)` và `Files.deleteIfExists(path)` đều xóa **bản thân entry liên kết**, không tự đi theo link để xóa file mục tiêu.

```text
link.txt  --symbolic link--> target.txt

Files.deleteIfExists(link.txt)
    -> xóa link.txt
    -> target.txt vẫn còn (nếu không bị tác động theo cách khác)
```

Điều này vẫn quan trọng với **dangling symbolic link** — liên kết còn tồn tại nhưng mục tiêu đã mất. Kiểm tra `Files.exists(link)` theo mặc định **đi theo symbolic link**, nên có thể trả `false` dù entry link vẫn hiện hữu.

Trong trường hợp muốn kiểm tra **chính entry liên kết**:

```java
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

boolean entryPresent = Files.exists(
        path, LinkOption.NOFOLLOW_LINKS);
```

Đoạn này cần `path` là `Path` có sẵn. Tuy vậy, **không bắt buộc** kiểm tra trước khi xóa: `deleteIfExists` đã diễn đạt rõ ý định “xóa entry nếu có” và tránh một bước kiểm tra riêng dễ sinh race.

Không dùng `Files.isRegularFile(path)` như điều kiện duy nhất để quyết định xóa symbolic link, vì tùy tùy chọn, phép kiểm tra có thể theo link tới đích hoặc trả kết quả khác với việc tồn tại entry link.

## 7. Vì sao không dùng `Files.exists` rồi mới `delete`?

Cách thường thấy:

```java
if (Files.exists(path)) {
    Files.delete(path);
}
```

Nhìn hợp lý nhưng có **time-of-check to time-of-use (TOCTOU)**:

```text
Luồng A: Files.exists(path) -> true
Luồng B: xóa path
Luồng A: Files.delete(path) -> NoSuchFileException
```

Giữa lúc kiểm tra và lúc dùng, trạng thái hệ thống tệp đã thay đổi. Hai lời gọi không ghép thành một giao dịch nguyên tử.

Nếu “không tồn tại” **là trạng thái chấp nhận được**, sử dụng trực tiếp:

```java
Files.deleteIfExists(path);
```

Nếu “không tồn tại” **là lỗi**, sử dụng trực tiếp:

```java
Files.delete(path);
```

**Đừng đánh đồng điều này với bảo đảm atomicity.** Tài liệu Java cũng cảnh báo `deleteIfExists` có thể **không nguyên tử** đối với các hoạt động file system khác. Một tiến trình khác có thể tạo, xóa hoặc thay entry đồng thời. Với dữ liệu quan trọng, phải thiết kế đồng bộ/ownership và phạm vi xóa phù hợp, không chỉ dựa vào một tên API.

## 8. Lỗi truy cập và ngoại lệ: `false` không có nghĩa “mọi lỗi đều bỏ qua”

Những ngoại lệ hữu ích khi xử lý API NIO:

| Ngoại lệ | Ý nghĩa điển hình | Hướng xử lý |
| --- | --- | --- |
| `NoSuchFileException` | Entry không tồn tại khi thao tác cần xóa | Nếu vắng mặt hợp lệ, dùng `deleteIfExists`; nếu không, báo lỗi |
| `DirectoryNotEmptyException` | Thư mục còn entry, không xóa được bằng thao tác đơn lẻ | Xác định đúng mục tiêu; không tự động dọn đệ quy |
| `AccessDeniedException` | Quyền truy cập/chính sách hệ thống từ chối | Kiểm tra quyền và ownership |
| `FileSystemException` | Lỗi file system chi tiết, có thể kèm lý do | Ghi thông tin phù hợp và xử lý theo nghiệp vụ |
| `IOException` | Lỗi I/O tổng quát hoặc được provider báo | Không gộp thành “entry không tồn tại” |
| `SecurityException` | Môi trường có cơ chế kiểm tra quyền tương ứng có thể từ chối | Xử lý theo chính sách bảo mật |

Các subclass cụ thể như `AccessDeniedException` và `DirectoryNotEmptyException` phụ thuộc provider/OS; không phải mọi hệ thống báo đúng cùng một loại lỗi cho cùng hiện tượng.

**Phân biệt trả giá trị và ném lỗi:**

```java
try {
    boolean removed = Files.deleteIfExists(path);
    System.out.println(removed ? "Đã xóa" : "Đã không tồn tại");
} catch (IOException e) {
    System.err.println("Không thể hoàn thành thao tác xóa: "
            + e.getMessage());
    throw e;
}
```

Đoạn minh họa cần `path` có sẵn và đặt trong phương thức `throws IOException`. Nếu lỗi thực sự, không trả về `false`: ngoại lệ đi ra ngoài để tầng gọi xử lý.

### Xóa file đang mở

Trên một số hệ điều hành, ứng dụng có thể xóa entry của file đang mở; trên môi trường khác, file đang được tiến trình khác giữ có thể không xóa được. Đừng viết logic giả định hành vi giống nhau trên Windows, Linux và mọi filesystem. Với file do mình mở, hãy **đóng stream/reader/writer** đúng lúc bằng `try-with-resources` trước khi thực hiện dọn dẹp.

## 9. So với `java.io.File.delete()` truyền thống

Tên gần giống nhưng API khác:

```java
import java.io.File;

File legacy = new File("old.txt");
boolean removed = legacy.delete();
```

`File.delete()` trả về `boolean` và có cơ chế báo lỗi kém chi tiết hơn: `false` có thể do đường dẫn không tồn tại **hoặc** xóa thất bại. Trong khi đó:

- `Files.delete(path)`: báo tình huống không tồn tại và nhiều lỗi I/O bằng ngoại lệ.
- `Files.deleteIfExists(path)`: `false` dành cho tình huống vắng mặt; các lỗi I/O **khác vẫn được báo bằng ngoại lệ**.

Với code Java hiện đại, `Path` + `Files` thường cung cấp thông tin thất bại rõ hơn. Nếu đã có `File`, có thể chuyển đổi mà không tác động tới nội dung tệp:

```java
File file = new File("report.txt");
Path path = file.toPath();
Files.deleteIfExists(path);
```

Đây là đoạn minh họa cần import `Path`, `Files` và xử lý `IOException`. `file.toPath()` chỉ chuyển biểu diễn đường dẫn; `Files.deleteIfExists` mới là lời gọi có tác dụng xóa.

Không nên nhầm `Files.deleteIfExists` với `File.delete()`: tuy cùng trả về `boolean`, cách chúng biểu đạt **lỗi thật** không giống nhau.

## 10. Tình huống nên chọn API nào?

| Nhu cầu | Gợi ý | Vì sao |
| --- | --- | --- |
| Xóa file tạm nếu nó còn sau xử lý | `Files.deleteIfExists` | File đã biến mất không phải lỗi |
| Dọn kết quả trung gian trong `finally` | `Files.deleteIfExists`, vẫn xử lý `IOException` | Dọn được cả khi chưa kịp tạo file |
| Một workflow yêu cầu đối tượng phải tồn tại | `Files.delete` | Phát hiện trạng thái bất thường |
| Gỡ bỏ một file đã được xác định chắc chắn theo nghiệp vụ | `Files.delete` nếu thiếu file cần báo lỗi | Đưa ra tín hiệu bằng exception |
| Xóa thư mục rỗng | Cả hai, tùy ngữ nghĩa vắng mặt | Cả hai đều không đệ quy |
| Xóa cả cây thư mục | Cơ chế duyệt file tree phù hợp, kiểm soát phạm vi | Không API nào trong hai API xóa đệ quy |
| Xóa symbolic link | Cả hai xóa entry link | Không xóa mục tiêu của link |
| Xóa file do người dùng chỉ định | Kiểm soát đường dẫn/quyền/symlink rồi chọn API | Tên phương thức không bảo vệ khỏi xóa nhầm |
| Xóa nhiều entry có trạng thái cạnh tranh | Thiết kế ownership/đồng bộ và xử lý lỗi | Không thể loại bỏ race chỉ bằng `deleteIfExists` |

### Ví dụ `finally` khi file tạm có thể chưa được tạo

Nếu xử lý lỗi xảy ra trước khi tạo xong file, việc dọn có thể diễn ra khi file chưa tồn tại:

```java
Path temp = Path.of("work", "temporary-result.bin");

try {
    // Tác vụ có thể thành công hoặc thất bại trước khi tạo temp.
} finally {
    Files.deleteIfExists(temp);
}
```

Đây là đoạn minh họa về **ý tưởng dọn dẹp**; cần đặt trong phương thức khai báo/xử lý `IOException` và chỉ xóa file **do tác vụ sở hữu**. Không được tự xóa tên file dùng chung của hệ thống nếu không có bảo đảm ownership; đoạn mã trên không tự tạo file.

Nếu ngoại lệ từ `deleteIfExists` xảy ra trong `finally`, nó có thể ảnh hưởng tới ngoại lệ đang được truyền ra từ thân `try`. Với ứng dụng cần bảo toàn **nguyên nhân thất bại chính**, hãy thiết kế chiến lược xử lý riêng cho lỗi dọn dẹp (ví dụ ghi log, suppressed exception hoặc `try-with-resources` với đối tượng tài nguyên phù hợp).

## 11. Xóa đường dẫn trong ứng dụng có bảo mật và đồng thời

Xóa file là thao tác **không thể thu hồi đơn giản** chỉ bằng gọi API. Trước khi xóa, cần chắc chắn đường dẫn thuộc phạm vi mà chương trình có quyền quản lý.

- **Không ghép tên đường dẫn tùy ý do người dùng cung cấp** rồi xóa ngay, ví dụ `base.resolve(userInput)`, vì `..`, đường dẫn tuyệt đối, symlink hoặc race có thể làm mục tiêu khác dự tính.
- `Path.normalize()` và `startsWith(root)` giúp kiểm tra phạm vi **theo cú pháp**, nhưng chưa đủ để chống symbolic link và thay đổi hệ thống tệp đồng thời.
- Giữ quyền truy cập tối thiểu và cơ chế ownership của thư mục riêng, nhất là với file tạm.
- Đối với thư mục nhiều người/tiến trình cùng ghi, cần cơ chế an toàn hơn như thao tác dựa trên directory handle/provider hỗ trợ, chứ không dựa duy nhất vào `Files.exists` rồi `Files.delete`.
- Đối với dữ liệu giá trị cao, xác định chính sách lưu trữ, backup, recycle bin hoặc xóa mềm (*soft delete*) theo nghiệp vụ thay vì để một lệnh xóa vĩnh viễn không thể đảo ngược.

### Có nên gọi `Files.exists` để "kiểm tra trước" không?

Có thể kiểm tra để **hiển thị thông tin cho người dùng**. Nhưng khi thực hiện hành động xóa thật, **phải xử lý kết quả/ngoại lệ của chính thao tác xóa**. `exists` không thể bảo đảm file vẫn còn ở thời điểm `delete` hoặc `deleteIfExists` chạy.

Một lưu ý với symbolic link: `Files.exists` mặc định đi theo link, vì vậy file đích không còn có thể dẫn tới kết luận sai rằng **entry link** không tồn tại. Lời gọi `deleteIfExists` trực tiếp thường đơn giản và đúng hơn khi ý định là xóa entry đang được trỏ tới.

## 12. Các lỗi học viên thường mắc

| Sai lầm | Vì sao sai | Cách sửa |
| --- | --- | --- |
| “`deleteIfExists` không bao giờ ném lỗi” | Chỉ bỏ qua sự vắng mặt, vẫn có lỗi xóa thật | Khai báo/xử lý `IOException` |
| “`deleteIfExists=false` nghĩa là không có quyền” | `false` báo không có entry để xóa | Phân biệt kết quả và ngoại lệ |
| “`delete` trả `boolean` như `File.delete`” | `Files.delete` trả `void` | Chọn API theo hợp đồng |
| “`Files.deleteIfExists` xóa cả cây” | Chỉ nhắm một entry, thư mục thường phải rỗng | Không dùng như lệnh recursive |
| “Trước khi xóa phải gọi `Files.exists`” | Có race giữa check/use | Gọi thao tác xóa trực tiếp và xử lý kết quả |
| “Nếu symbolic link hỏng, không thể xóa nó” | Link là entry riêng, có thể xóa link | Xóa `Path` tới link; không suy ra từ `Files.exists` mặc định |
| “Thất bại xóa đều là `NoSuchFileException`” | Có thể là quyền, file bị giữ, thư mục không rỗng | Xử lý ngoại lệ tương ứng và lỗi chung |
| “`Files.delete(path)` đổi tên file” | Nó xóa entry, không rename | `Files.move` nếu muốn di chuyển/đổi tên |
| “`Files.deleteIfExists` bảo đảm không có cạnh tranh” | Không phải giao dịch nguyên tử chống mọi thay đổi | Thiết kế ownership và đồng bộ |
| “Xóa tệp cha cũng xóa nội dung thư mục” | Thư mục có entry thường không xóa được | Xác định rõ nhu cầu, tránh đệ quy ngoài ý muốn |
| “File đang mở xóa được giống nhau mọi OS” | Provider/OS có quy tắc khác nhau | Đóng tài nguyên và xử lý lỗi thực |
| “Có thể bỏ qua lỗi cleanup” | Lỗi dọn có thể lấp hoặc che nguyên nhân chính | Quản lý ngoại lệ cleanup cẩn thận |

## 13. Câu hỏi tự kiểm tra

1. **`Files.delete(path)` trả về `true` hay `false`?** Không; kiểu trả về là **`void`**.
2. **`Files.delete(path)` với file không tồn tại thường gây gì?** **`NoSuchFileException`**.
3. **`Files.deleteIfExists(path)` với file không tồn tại trả gì?** **`false`**.
4. **`Files.deleteIfExists(path)` có trả `false` khi thư mục không rỗng không?** Không nên kỳ vọng; nó **có thể ném ngoại lệ I/O**, thường là `DirectoryNotEmptyException`.
5. **Dùng `deleteIfExists` trong `finally` có xóa được lỗi quyền không?** Không; lỗi quyền vẫn có thể ném ngoại lệ.
6. **Gọi `deleteIfExists` hai lần liên tiếp luôn trả `true` rồi `false`?** Chỉ trong điều kiện file có ở lần đầu và không có tác nhân khác tạo lại hoặc thay đường dẫn.
7. **Xóa symbolic link có xóa file đích không?** **Không**, nó xóa entry liên kết.
8. **Vì sao `if (Files.exists(p)) Files.delete(p);` không tránh được `NoSuchFileException`?** Vì file có thể biến mất giữa kiểm tra và xóa.
9. **`File.delete()` và `Files.deleteIfExists()` đều trả boolean thì có giống nhau?** Không; `File.delete` thường không phân biệt chi tiết các lỗi, còn `Files.deleteIfExists` báo các lỗi I/O khác bằng ngoại lệ.
10. **Muốn xóa toàn bộ folder chứa file có thể chỉ gọi `deleteIfExists(folder)` không?** **Không**, thư mục không rỗng thường khiến thao tác thất bại; phải dùng một quy trình xóa cây được kiểm soát khi thật sự cần.
11. **Khi thiếu file là trạng thái bình thường, API nào thể hiện rõ ý định hơn?** **`Files.deleteIfExists`**.
12. **Khi thiếu file cần được phát hiện là lỗi, API nào phù hợp hơn?** **`Files.delete`**.

**Ghi nhớ:** `Files.delete` coi **“không tồn tại” là lỗi** và trả `void`; `Files.deleteIfExists` coi **“không tồn tại” là trường hợp bình thường** và trả `false`. Cả hai đều có thể thất bại bởi các lỗi thật, không xóa đệ quy và cần dùng trên những đường dẫn mà ứng dụng thực sự được phép quản lý.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-operations-006">Xử lý tệp đích đã tồn tại và thư mục không rỗng</a>

<details>
<summary>Xem chi tiết</summary>

# Xử lý tệp đích đã tồn tại và thư mục không rỗng trong Java I/O

## 1. Bài toán thực tế

Khi tạo, sao chép, di chuyển hoặc xóa file bằng Java, hai tình huống rất thường gặp là:

1. **Đường dẫn đích đã tồn tại**: bạn muốn tạo **report.txt**, nhưng ở đó đã có file hay thư mục. Có được ghi đè không? Nên từ chối, đổi tên mới, ghi nối hay thay thế?
2. **Thư mục không rỗng**: bạn muốn xóa **reports/**, nhưng trong đó vẫn có file hoặc thư mục con. Một lời gọi xóa trực tiếp có xóa toàn bộ cây không? Làm sao tránh xóa nhầm?

Câu trả lời phải xuất phát từ **hợp đồng nghiệp vụ**. Java có các API **java.io.File** truyền thống và **java.nio.file.Files** hiện đại, nhưng không API nào tự biết bạn có quyền phá hủy dữ liệu cũ hay không.

> Quy tắc an toàn: **mặc định từ chối ghi đè hoặc xóa đệ quy khi chưa có yêu cầu rõ ràng**. Chỉ dùng tùy chọn thay thế hoặc dọn cây khi đã xác định phạm vi, quyền, phương án xử lý lỗi và rủi ro thay đổi đồng thời.

Bài này tập trung vào **tệp đích đã có** khi tạo/copy/move/ghi nội dung và **thư mục không rỗng** khi xóa. Mã ví dụ dùng Java 17+.

## 2. Phân biệt “đường dẫn tồn tại” với “đúng loại tài nguyên”

Một đường dẫn **Path** có thể đại diện cho:

- File thông thường.
- Thư mục, có thể rỗng hoặc không rỗng.
- Symbolic link, nếu filesystem hỗ trợ.
- Tài nguyên đặc biệt hoặc một đường dẫn chưa tồn tại.

Ví dụ:

~~~java
Path destination = Path.of("data", "report.txt");

boolean exists = Files.exists(destination);
boolean regular = Files.isRegularFile(destination);
boolean directory = Files.isDirectory(destination);
~~~

Đây là **mảnh code minh họa**; cần import **java.nio.file.Files** và **java.nio.file.Path**.

**Files.exists(path)** không đủ để quyết định “an toàn ghi đè”: một đường dẫn có thể là thư mục chứ không phải file thường; symlink có thể trỏ đến vị trí khác; quyền truy cập có thể khiến phép kiểm tra không kết luận chắc chắn. Với **Files.exists** hoặc **Files.notExists**, hai kết quả đều có thể false khi trạng thái không xác định do quyền hoặc lỗi I/O.

Quan trọng hơn, **kiểm tra rồi thao tác** không phải một giao dịch nguyên tử:

~~~text
Tiến trình A: Files.exists(target) → false
Tiến trình B: tạo target
Tiến trình A: bắt đầu ghi target → có nguy cơ đụng dữ liệu vừa tạo
~~~

Đây là race condition kiểu **TOCTOU** (*time of check to time of use*). Nếu yêu cầu chỉ tạo file mới khi tên chưa tồn tại, dùng tùy chọn **CREATE_NEW** của thao tác ghi hoặc **Files.createFile**, thay vì tin vào một kết quả exists đã kiểm tra trước.

## 3. Các chính sách xử lý file đích đã tồn tại

| Chính sách | Khi nào chọn | API phù hợp |
| --- | --- | --- |
| **Từ chối** nếu đích đã tồn tại | Tránh làm mất file người dùng; tạo báo cáo duy nhất | **Files.createFile**, **CREATE_NEW**, hoặc **Files.copy** mặc định |
| **Thay thế** file đích cũ | Đã có quyền cập nhật và chính sách backup/khôi phục | **Files.copy(..., REPLACE_EXISTING)**, **Files.move(..., REPLACE_EXISTING)**, hoặc ghi truncate |
| **Ghi tiếp (append)** | Nhật ký/những bản ghi có thể nối, không thay dữ liệu cũ | **Files.writeString(..., CREATE, APPEND)** hay **Files.newOutputStream(..., APPEND)** |
| **Đổi sang tên khác** | Lưu cả phiên bản cũ và mới | Sinh tên đích khác; **CREATE_NEW** để chốt quyền tạo |
| **Thay thế qua file tạm** | Giảm nguy cơ bản cũ bị hỏng do thao tác ghi dở | Ghi tạm + **Files.move** theo option và filesystem phù hợp |

Đừng đánh đồng **ghi đè** với **append**. Ghi đè thường loại bỏ nội dung cũ hoặc thay file; append đặt dữ liệu mới phía sau dữ liệu hiện có. Với file có định dạng hoàn chỉnh (PDF, ZIP, ảnh), append tùy tiện thường tạo file sai định dạng.

## 4. Tạo file mới: Files.createFile và CREATE_NEW

### 4.1. Files.createFile không âm thầm ghi đè

~~~java
Path path = Path.of("output.txt");
Files.createFile(path);
~~~

Đây là **mảnh code** cần import Files/Path và xử lý **IOException**.

**Files.createFile(path)** tạo file mới rỗng nếu đường dẫn chưa tồn tại. Nếu một mục filesystem đã chiếm tên đó, thông thường API ném **FileAlreadyExistsException** (một lớp con của **IOException**) thay vì ghi đè dữ liệu cũ. Hoạt động kiểm tra-chưa-tồn-tại và tạo file được yêu cầu thực hiện nguyên tử trong phạm vi hợp đồng API/provider.

**Không nên dùng** mẫu kiểm tra rồi tạo:

~~~java
if (!Files.exists(path)) {
    Files.createFile(path);
}
~~~

Kết quả **exists()** có thể cũ khi lời gọi tạo file thực sự chạy. Gọi trực tiếp **createFile** và xử lý **FileAlreadyExistsException** rõ ràng hơn.

### 4.2. Muốn mở file để ghi, dùng CREATE_NEW

Nếu mục tiêu là **tạo file và ghi nội dung ngay**, có thể dùng:

~~~java
Files.writeString(
        path,
        "Nội dung mới",
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE);
~~~

Đây là **mảnh code**, cần import **StandardCharsets** và **StandardOpenOption**. Nếu tên đã tồn tại, thao tác sẽ không mở theo chính sách ghi đè. Cách này tránh khoảng trống giữa “tạo file rỗng” và “mở file để ghi”.

Lưu ý: **CREATE_NEW không biến việc ghi hàng nghìn byte thành giao dịch nguyên tử**. Nếu hết dung lượng đĩa hoặc I/O lỗi giữa chừng, file mới có thể đã được tạo nhưng nội dung chưa đủ. Cần xử lý/đánh dấu file dở dang theo quy tắc ứng dụng.

### 4.3. So sánh với java.io.File.createNewFile

~~~java
File legacy = new File("output.txt");
boolean created = legacy.createNewFile();
~~~

Đây là **mảnh code** cần import **java.io.File** và xử lý IOException. **created == true** nghĩa là lần gọi tạo được file rỗng; **false** nghĩa là tên đó đã tồn tại. Khác với Files.createFile, kết quả false không có exception mô tả riêng trường hợp đã tồn tại.

**File.createNewFile()** cũng có hợp đồng thao tác kiểm tra-tạo mới nguyên tử, nhưng nếu cần ghi ngay với chính sách “không thay thế”, **CREATE_NEW** tích hợp trực tiếp vào thao tác mở/ghi thường diễn đạt ý định tốt hơn.

## 5. Files.copy: mặc định từ chối, REPLACE_EXISTING khi được phép

Chữ ký sử dụng thường gặp:

~~~java
Files.copy(source, target);
Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
~~~

Các mảnh code giả định **source**, **target** là Path và đã import **Files**, **StandardCopyOption**.

### 5.1. Không có REPLACE_EXISTING

Với file nguồn và file đích **khác nhau**, nếu **target đã tồn tại**, **Files.copy(source, target)** thông thường ném **FileAlreadyExistsException**. Đây là mặc định tốt khi không muốn phá hủy dữ liệu đích.

~~~text
source.txt = "NEW"
target.txt = "OLD"

Files.copy(source, target)
    → FileAlreadyExistsException
target.txt vẫn chứa "OLD" (trong trường hợp lỗi vì đã tồn tại)
~~~

### 5.2. Có REPLACE_EXISTING

Khi đã xác nhận muốn thay thế:

~~~java
Files.copy(
        source,
        target,
        StandardCopyOption.REPLACE_EXISTING);
~~~

API cố thay mục đích để bản sao dữ liệu nguồn xuất hiện ở **target**. Tuy nhiên:

- **Không bảo đảm thao tác copy là nguyên tử.** Nếu lỗi giữa chừng, trạng thái target có thể không còn nguyên vẹn.
- **Không mặc định giữ tất cả metadata** của nguồn. Muốn sao chép các thuộc tính được hỗ trợ, có thể xét **COPY_ATTRIBUTES**, nhưng không phải mọi attribute đều sao chép được.
- **Không sử dụng REPLACE_EXISTING như lệnh xóa mọi cây thư mục.** Nếu target là **thư mục không rỗng**, provider có thể không thể thay thế; **DirectoryNotEmptyException** hoặc IOException liên quan có thể xảy ra.
- Nếu **source là thư mục**, thao tác Files.copy trên directory **không có nghĩa là sao chép đệ quy toàn bộ nội dung con**. Cần thuật toán riêng nếu thực sự muốn copy cả cây.
- Trạng thái khác nhau của symlink, filesystem hoặc file đang mở có thể ảnh hưởng kết quả; không đoán dựa trên phần mở rộng tên file.

**Hậu điều kiện cần kiểm tra**: nếu việc copy hoàn thành, target phải đáp ứng yêu cầu nội dung và thuộc tính mà ứng dụng đã định. Với dữ liệu quan trọng, đối chiếu kích thước/hash hoặc dùng **Files.mismatch(source, target)** sau copy khi hai file còn ổn định.

## 6. Files.move: đích tồn tại và tính nguyên tử

Lệnh chuyển tên/vị trí:

~~~java
Files.move(source, target);
~~~

Theo quy tắc thông thường, **nếu target đã tồn tại**, move không có REPLACE_EXISTING sẽ thất bại (thường **FileAlreadyExistsException**); có ngoại lệ phụ thuộc provider và tình huống đường dẫn nguồn/đích trỏ cùng một file. Vì vậy đừng dựa vào hành vi ngoài hợp đồng đối với tài nguyên đặc biệt.

Nếu chính sách cho phép thay:

~~~java
Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
~~~

**Không nên coi REPLACE_EXISTING là bảo đảm ghi đè mọi dạng đích**. Ví dụ target là **thư mục không rỗng** có thể không thể được thay; file đang được sử dụng, filesystem khác nhau, quyền truy cập, hoặc hệ điều hành cũng có thể làm move thất bại.

### 6.1. REPLACE_EXISTING và ATOMIC_MOVE giải quyết hai mục tiêu khác nhau

- **REPLACE_EXISTING**: diễn đạt **mong muốn thay đối tượng đích hiện có** nếu provider và tình huống hỗ trợ.
- **ATOMIC_MOVE**: yêu cầu **di chuyển nguyên tử như một thao tác filesystem**, nếu provider hỗ trợ.

Ví dụ:

~~~java
Files.move(
        temporary,
        target,
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING);
~~~

Đây là **mảnh minh họa** (temporary và target là Path). Với **ATOMIC_MOVE**, hợp đồng API quy định các option khác có thể bị bỏ qua và **hành vi khi target đã tồn tại phụ thuộc triển khai**. Do đó **không suy ra** rằng ghép hai option là phép “thay thế nguyên tử” portable ở mọi provider.

Nếu không hỗ trợ atomic move, có thể nhận **AtomicMoveNotSupportedException**. Ứng dụng phải có **chính sách fallback**: từ chối cập nhật, chấp nhận move không atomic theo yêu cầu, hoặc chọn cơ chế lưu trữ khác. Không lặng lẽ bỏ option ATOMIC_MOVE trong quy trình bắt buộc nguyên tử.

### 6.2. Ghi ra file tạm rồi di chuyển

Để cập nhật file cấu hình quan trọng, chiến lược phổ biến:

1. Tạo **file tạm mới** trong thư mục chứa đích hoặc ít nhất cùng filesystem, bằng cách tạo duy nhất để không đè file khác.
2. Ghi đầy đủ nội dung và đóng tài nguyên; đồng bộ dữ liệu/metadata đến mức độ bền yêu cầu nếu ứng dụng cần.
3. Nếu có kiểm tra định dạng hoặc checksum, làm trước khi thay thế đích.
4. Thử move sang đích với chính sách atomic/replacement rõ ràng và xử lý trường hợp filesystem không hỗ trợ.
5. Nếu có lỗi, dọn file tạm do mình tạo nhưng **không xóa nhầm bản đích cũ**.

**Ghi file tạm không tự biến mọi quy trình thành giao dịch bền vững sau mất điện**. Cần xét FileChannel.force, đồng bộ thư mục và hợp đồng hệ thống file nếu ứng dụng có yêu cầu durability rất cao.

## 7. Files.writeString và Files.newOutputStream: vì sao file bị mất nội dung cũ?

Ví dụ:

~~~java
Files.writeString(path, "NEW");
~~~

**Files.writeString** mặc định dùng UTF-8 và, khi không truyền option, mở với chính sách tương đương **CREATE + TRUNCATE_EXISTING + WRITE**. Với file đã có, nội dung cũ bị cắt trước khi ghi nội dung mới. Đây là **ghi đè** có chủ đích của API, khác với Files.copy mặc định từ chối đích đã tồn tại.

Tương tự:

~~~java
try (OutputStream out = Files.newOutputStream(path)) {
    out.write(new byte[] {1, 2, 3});
}
~~~

Đây là **mảnh code**, cần import **java.io.OutputStream** và **java.nio.file.Files**; phải xử lý IOException. Với options mặc định của Files.newOutputStream, một file đã tồn tại thường bị truncate để ghi mới.

Muốn **ghi thêm** thay vì cắt:

~~~java
Files.writeString(
        path,
        "\nNEW",
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND);
~~~

Ví dụ cần import **StandardOpenOption**. Dấu xuống dòng **\n** do code chủ động chèn, không phải do APPEND tự thêm. Trên nhiều tiến trình cùng ghi, APPEND **không thay cho thiết kế đồng bộ/locking ở cấp bản ghi**.

**Tương phản cần nhớ:**

| Thao tác | Khi file đích đã tồn tại |
| --- | --- |
| **Files.createFile(path)** | Thường **ném FileAlreadyExistsException** |
| **Files.copy(source, target)** | Thường **ném FileAlreadyExistsException** |
| **Files.move(source, target)** | Thường báo lỗi, không mặc định thay thế |
| **Files.writeString(path, text)** | Mặc định **truncate rồi ghi lại** |
| **Files.newOutputStream(path)** | Mặc định **truncate để ghi mới** |
| **Files.writeString(..., CREATE_NEW, WRITE)** | Từ chối nếu tên đích đã có |
| **Files.writeString(..., CREATE, APPEND)** | Ghi thêm ở cuối |

Đừng kết luận “mọi phương thức Files đều mặc định không ghi đè” hoặc “mọi phương thức Files đều ghi đè”: **hợp đồng của từng phương thức khác nhau**.

## 8. Thư mục không rỗng: tại sao delete() thất bại?

Giả sử:

~~~text
reports/
├── 2026/
│   └── october.csv
└── notes.txt
~~~

**reports/** là thư mục không rỗng. Gọi:

~~~java
Files.delete(Path.of("reports"));
~~~

sẽ không tự xóa toàn bộ các file con. Trên filesystem thông thường, thao tác ném **DirectoryNotEmptyException** (một IOException); nhiều hệ thống khác có thể dùng IOException khác.

Tương tự, dùng API cũ:

~~~java
File directory = new File("reports");
boolean deleted = directory.delete();
~~~

**File.delete()** chỉ xóa được thư mục **rỗng** theo quy tắc thông thường và trả **false** nếu không thể xóa, không cho chẩn đoán chi tiết như Files.delete.

### 8.1. Files.delete và Files.deleteIfExists

| API | Nếu mục tiêu chưa tồn tại | Nếu mục tiêu là thư mục không rỗng |
| --- | --- | --- |
| **Files.delete(path)** | Thường ném **NoSuchFileException** | Ném **DirectoryNotEmptyException** hoặc IOException liên quan |
| **Files.deleteIfExists(path)** | Trả **false** | **Vẫn ném IOException**; không tự dọn cây |
| **new File(...).delete()** | Thường trả **false** | Thường trả **false**, ít thông tin hơn |

**deleteIfExists** chỉ bỏ qua **lỗi thiếu tên đích**, **không phải** “xóa nếu có bất kể trong đó có gì”. Với thư mục không rỗng, phương thức vẫn có thể ném lỗi.

### 8.2. Xóa thư mục rỗng

Nếu thực sự muốn xóa một thư mục đã biết phải rỗng:

~~~java
Files.delete(directoryPath);
~~~

Nếu thư mục có con, hãy **báo lỗi và yêu cầu quyết định rõ** trước khi chọn xóa nội dung. Không nên bí mật đổi từ xóa thư mục thành xóa đệ quy vì điều này có thể phá hủy dữ liệu người dùng ngoài dự kiến.

## 9. Nếu nghiệp vụ thật sự cần xóa toàn bộ cây thư mục

Xóa đệ quy là tác vụ **nguy hiểm**: người dùng có thể đưa sai đường dẫn; một symbolic link có thể dẫn tới tài nguyên ngoài phạm vi; tiến trình khác có thể tạo thêm file trong khi đang duyệt. **java.nio.file.Files** không có một hàm **deleteTree(path)** chung bảo đảm an toàn cho mọi trường hợp.

Nguyên tắc khi thực sự được phép:

1. Xác nhận đường dẫn gốc thuộc **phạm vi mà chương trình sở hữu**, không phải thư mục hệ thống hoặc dữ liệu người dùng ngoài chính sách.
2. Xác định rõ cách xử lý symbolic link; không tự động **follow** links đến cây khác.
3. Duyệt cây và xóa **file/con trước, thư mục cha sau**.
4. Xử lý **IOException**, phân quyền, file đang mở và trường hợp một mục xuất hiện/biến mất trong lúc duyệt.
5. Ghi nhận kết quả đã xóa đến đâu. Không coi xóa đệ quy là một transaction có thể rollback.
6. Với rủi ro bảo mật hoặc filesystem thay đổi đồng thời, cần thiết kế dựa trên directory handle/cơ chế thích hợp (ví dụ **SecureDirectoryStream** khi provider hỗ trợ), không chỉ tin vào phép nối đường dẫn.

Ví dụ chỉ cho **cây tạm do chính chương trình tạo**, không thay tên bằng đường dẫn do người dùng nhập:

~~~java
Path ownedTemp = Files.createTempDirectory("owned-tree-");
Path child = Files.createDirectories(ownedTemp.resolve("nested"));
Files.writeString(child.resolve("example.txt"), "example");

try (var paths = Files.walk(ownedTemp)) {
    for (Path entry :
            paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
        Files.delete(entry);
    }
}
~~~

Đây là **mảnh code minh họa**, cần import **Files**, **Path** và xử lý IOException. **Files.walk** mặc định **không đi theo symbolic links** khi không truyền FOLLOW_LINKS. Sắp xếp đường dẫn đảo thứ tự thông thường giúp trong cây minh họa có đường dẫn con trước đường dẫn cha. Với chương trình xóa cây nói chung, **Files.walkFileTree** dùng FileVisitor, **visitFile** và **postVisitDirectory** sẽ biểu đạt rõ hơn việc duyệt theo thứ tự, hỗ trợ xử lý lỗi theo từng mục; không nên dùng một mẫu ngắn như trên làm công cụ xóa thư mục tùy ý.

Trong sản phẩm thực tế, nếu bất kỳ file nào không thể xóa, có thể cần dừng và báo trạng thái đã xóa một phần, thay vì bỏ lỗi rồi tiếp tục như thể không có rủi ro.

## 10. Ví dụ Java 17+ chạy độc lập: xử lý đích tồn tại và thư mục không rỗng

Chương trình sau tạo một **thư mục tạm riêng** cho mỗi lần chạy, không động đến file người dùng. Nó minh họa chính sách không ghi đè, ghi đè có chủ đích, di chuyển, CREATE_NEW, lỗi thư mục không rỗng và dọn theo thứ tự.

Lưu thành **ExistingTargetDemo.java**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public class ExistingTargetDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("target-demo-");
        Path source = root.resolve("source.txt");
        Path target = root.resolve("target.txt");
        Path exclusive = root.resolve("exclusive.txt");
        Path folder = root.resolve("folder");
        Path child = folder.resolve("keep.txt");

        try {
            Files.writeString(source, "NEW", StandardCharsets.UTF_8);
            Files.writeString(target, "OLD", StandardCharsets.UTF_8);

            // Copy mặc định từ chối file đích đã có.
            try {
                Files.copy(source, target);
            } catch (FileAlreadyExistsException ex) {
                System.out.println("copy-existing=blocked");
            }

            // REPLACE_EXISTING: được phép thay nội dung đích.
            Files.copy(source, target,
                    StandardCopyOption.REPLACE_EXISTING);
            System.out.println("copy-replaced=" +
                    Files.readString(target));

            // Move mặc định từ chối đích đã tồn tại.
            Files.writeString(source, "MOVE", StandardCharsets.UTF_8);
            try {
                Files.move(source, target);
            } catch (FileAlreadyExistsException ex) {
                System.out.println("move-existing=blocked");
            }

            // Chỉ thay khi hợp đồng cho phép.
            Files.move(source, target,
                    StandardCopyOption.REPLACE_EXISTING);
            System.out.println("move-replaced=" +
                    Files.readString(target));
            System.out.println("source-gone=" +
                    Files.notExists(source));

            // CREATE_NEW: cố tạo lại một tên đã có.
            Files.writeString(exclusive, "FIRST",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            try {
                Files.writeString(exclusive, "SECOND",
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException ex) {
                System.out.println("create-new-existing=blocked");
            }
            System.out.println("exclusive=" +
                    Files.readString(exclusive));

            // Thư mục không rỗng: xóa trực tiếp bị từ chối.
            Files.createDirectory(folder);
            Files.writeString(child, "KEEP", StandardCharsets.UTF_8);
            try {
                Files.delete(folder);
            } catch (DirectoryNotEmptyException ex) {
                System.out.println("nonempty-directory=blocked");
            }

            // Xóa con trước, xóa thư mục rỗng sau.
            Files.delete(child);
            Files.delete(folder);
            System.out.println("folder-gone=" +
                    Files.notExists(folder));
        } finally {
            // Chỉ dọn các đường dẫn do chính demo tạo.
            Files.deleteIfExists(child);
            Files.deleteIfExists(folder);
            Files.deleteIfExists(source);
            Files.deleteIfExists(target);
            Files.deleteIfExists(exclusive);
            Files.deleteIfExists(root);
        }
    }
}
~~~

Chạy:

~~~bash
javac ExistingTargetDemo.java
java ExistingTargetDemo
~~~

Trên một filesystem cục bộ thông thường cho phép các thao tác thử nghiệm, stdout dự kiến:

~~~text
copy-existing=blocked
copy-replaced=NEW
move-existing=blocked
move-replaced=MOVE
source-gone=true
create-new-existing=blocked
exclusive=FIRST
nonempty-directory=blocked
folder-gone=true
~~~

**Lưu ý:** Một số provider có thể có quy tắc khác với hoạt động move và thông báo ngoại lệ; mô tả trên áp dụng cho filesystem cục bộ điển hình. Nếu provider không ném đúng **FileAlreadyExistsException** hoặc **DirectoryNotEmptyException** mà ném **IOException** khác, chương trình có thể dừng và báo lỗi thay vì in đủ các dòng. Demo không ép mọi filesystem phải cho cùng kết quả.

Giải thích:

- Lần **Files.copy(source,target)** không có REPLACE_EXISTING nên từ chối target đang chứa "OLD". Tên đích không bị ghi đè bởi bước copy thất bại kiểu đã tồn tại.
- **Files.copy(..., REPLACE_EXISTING)** thay target bằng dữ liệu "NEW".
- Sau đó source được ghi "MOVE"; move không có option bị từ chối vì target đang có. Move có REPLACE_EXISTING cập nhật target và làm source không còn ở đường dẫn cũ.
- **CREATE_NEW** không ghi đè "FIRST" bằng "SECOND" khi tên đích đã tồn tại.
- **Files.delete(folder)** với child bên trong bị từ chối; xóa child rồi folder thành công.

Cách viết **finally** cố dọn mọi đường dẫn tạm do demo sở hữu. Nếu hệ thống file lỗi trong quá trình cleanup, IOException có thể phát sinh; chương trình không nên khẳng định đã dọn sạch khi thực tế thất bại.

## 11. Cách xử lý lỗi: đừng chỉ dựa vào tên exception

Các ngoại lệ phổ biến của **java.nio.file**:

| Ngoại lệ | Tình huống tiêu biểu |
| --- | --- |
| **FileAlreadyExistsException** | Tạo mới/copy/move mà tên đích đã có |
| **DirectoryNotEmptyException** | Xóa thư mục đang chứa mục con |
| **NoSuchFileException** | Thiếu đường dẫn nguồn/đích cần thiết |
| **AccessDeniedException** | Hệ thống từ chối quyền truy cập |
| **AtomicMoveNotSupportedException** | Yêu cầu ATOMIC_MOVE nhưng provider không hỗ trợ |
| **IOException** | Các lỗi I/O khác, bao gồm trường hợp provider báo lỗi bằng loại tổng quát |

Các loại exception cụ thể **không phải luôn được mọi hệ thống file ném giống nhau**; Javadoc dùng các loại exception tùy chọn trong nhiều hợp đồng. Phải luôn có xử lý cho **IOException** tổng quát, hoặc để nó lan ra cho tầng trên xử lý.

Một policy rõ ràng:

~~~java
try {
    Files.copy(source, target); // Chính sách từ chối ghi đè.
} catch (FileAlreadyExistsException ex) {
    // Báo người dùng: đổi tên đích hoặc xác nhận thay thế.
} catch (IOException ex) {
    // Ghi log/chẩn đoán lỗi I/O khác; không coi là "file đã tồn tại".
    throw ex;
}
~~~

Đây là **mảnh code minh họa**, không phải chương trình chạy độc lập. Khi chọn REPLACE_EXISTING, vẫn bắt IOException; không ngầm hiểu mọi việc ghi đè đều thành công.

## 12. Quy tắc kiểm tra hậu điều kiện và an toàn thao tác

### 12.1. So sánh nội dung thay vì chỉ so đường dẫn

Sau copy cần bảo đảm byte giống nhau, có thể sử dụng:

~~~java
long mismatch = Files.mismatch(source, target);
if (mismatch != -1L) {
    throw new IOException(
            "File đích khác nguồn tại byte: " + mismatch);
}
~~~

Đây là **mảnh code**, dùng khi cả source và target là file phù hợp có thể đọc và không bị sửa đồng thời. **Files.mismatch** (Java 12+) trả -1 nếu không phát hiện byte khác nhau. Không đủ nếu chỉ so kích thước hoặc chỉ dựa vào tên file.

### 12.2. Đừng xóa trước rồi mới copy nếu cần giữ bản cũ

Một mẫu rủi ro:

~~~java
Files.delete(target);
Files.copy(source, target);
~~~

Nếu bước copy thất bại, bản cũ đã mất. Cũng không nên dùng Files.deleteIfExists(target) rồi copy như cách “tạo điều kiện” mặc định. Khi có quyền thay thế, cân nhắc **copy vào file tạm, kiểm tra rồi move**, và xác định rõ khả năng atomic move/hỗ trợ provider.

### 12.3. Đừng giả định exists() xác nhận quyền hoặc tính an toàn

Một quy trình kiểu:

~~~java
if (Files.exists(target)) {
    Files.delete(target);
}
Files.move(source, target);
~~~

vẫn có race condition giữa check/delete/move. Đặc biệt nguy hiểm nếu target do người khác có thể thay đổi hoặc trỏ tới symlink. Dùng **option** diễn đạt trực tiếp điều kiện và chỉ thao tác trong đường dẫn được phép.

### 12.4. Bảo vệ dữ liệu người dùng

Không chấp nhận trực tiếp đường dẫn tùy ý rồi gọi xóa đệ quy hoặc replace. Khi nhận tên file từ người dùng, cần chuẩn hóa và kiểm tra phạm vi trong thư mục cho phép, xử lý đường dẫn tuyệt đối, **..**, symbolic links và quyền truy cập. Với môi trường đối kháng, phép kiểm tra path thông thường vẫn có thể bị race; dùng cơ chế file handle/quyền hoặc dịch vụ lưu trữ an toàn phù hợp.

Trong các thao tác hủy dữ liệu, hãy phân biệt “**tên đích thuộc ứng dụng**” và “**nội dung đích có thể phá hủy**”. Thư mục thuộc ứng dụng vẫn có thể chứa dữ liệu người dùng không được phép xóa.

## 13. Câu hỏi tự kiểm tra

**Câu 1.** File target đã có; gọi **Files.copy(source,target)** không option thì sao?  
**Đáp:** Thông thường ném **FileAlreadyExistsException**; không mặc định thay thế dữ liệu cũ.

**Câu 2.** **REPLACE_EXISTING** có cho phép Files.copy thay một thư mục không rỗng bằng file không?  
**Đáp:** Không được mặc định như vậy. Target là thư mục không rỗng có thể không thể thay; DirectoryNotEmptyException/IOException có thể phát sinh.

**Câu 3.** **Files.writeString(path, "X")** có mặc định từ chối file đang tồn tại như Files.copy không?  
**Đáp:** Không. Với các option mặc định, nó tạo file khi thiếu hoặc **truncate** file hiện có rồi ghi nội dung.

**Câu 4.** **Files.deleteIfExists(directory)** có tự xóa mọi file con không?  
**Đáp:** Không. Nó chỉ cho phép bỏ qua trường hợp đường dẫn thiếu; thư mục không rỗng vẫn không xóa trực tiếp được.

**Câu 5.** Vì sao dùng **Files.exists(target)** rồi **Files.createFile(target)** không thay CREATE_NEW?  
**Đáp:** Giữa kiểm tra và tạo có cửa sổ race; CREATE_NEW/CreateFile ràng buộc điều kiện không tồn tại ngay tại thao tác tạo.

**Câu 6.** **REPLACE_EXISTING** và **ATOMIC_MOVE** giống nhau không?  
**Đáp:** Không. REPLACE_EXISTING quy định mong muốn thay đích; ATOMIC_MOVE yêu cầu di chuyển nguyên tử nếu provider hỗ trợ, và khi đi cùng ATOMIC_MOVE các option khác có thể bị bỏ qua.

**Câu 7.** Muốn xóa thư mục không rỗng mà vẫn an toàn, trước hết cần làm gì?  
**Đáp:** Xác định **phạm vi được phép xóa**, hiểu cây dữ liệu và symlink, chọn giải pháp duyệt con trước cha có xử lý lỗi; không tự động gọi xóa đệ quy với đường dẫn không tin cậy.

**Câu 8.** Để không mất bản đích cũ khi ghi cập nhật lỗi giữa chừng, hướng tiếp cận nào tốt hơn?  
**Đáp:** Tạo file tạm riêng, ghi/kiểm tra đầy đủ rồi move theo chính sách atomic/replacement được hỗ trợ. Vẫn cần xử lý failure và không tự suy diễn hỗ trợ atomic trên mọi filesystem.

## 14. Tổng kết

**Tệp đích đã tồn tại** không phải một lỗi có cách chữa duy nhất: cần chọn **từ chối**, **thay thế**, **ghi tiếp**, **đổi tên mới** hoặc **cập nhật thông qua file tạm**. API mang những mặc định khác nhau: **Files.copy/move** thường từ chối target đã tồn tại, nhưng **Files.writeString/newOutputStream** mặc định có thể truncate file đang có. **CREATE_NEW** giúp tạo mới không ghi đè; **REPLACE_EXISTING** biểu diễn chính sách thay thế nhưng không bảo đảm copy/move nguyên tử hay khả năng thay thư mục không rỗng.

**Thư mục không rỗng** không được xóa bởi **Files.delete**, **Files.deleteIfExists** hay **File.delete** như một phép xóa đệ quy. Muốn dọn cây phải có phạm vi sở hữu và chiến lược duyệt/xóa rõ ràng, xử lý symlink, quyền, race và thất bại một phần.

Khi thao tác trên dữ liệu quan trọng, **đừng coi boolean, option hay lời gọi Files thành cam kết giao dịch**. Kiểm tra hậu điều kiện và thiết kế để lỗi không biến thành mất dữ liệu.

### Tài liệu tham khảo chính thức

- [Oracle Java SE 17 — Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html): Files.copy, Files.move, Files.createFile, Files.delete, Files.deleteIfExists, Files.writeString và Files.walk.
- [Oracle Java SE 17 — StandardCopyOption](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardCopyOption.html): REPLACE_EXISTING, COPY_ATTRIBUTES, ATOMIC_MOVE.
- [Oracle Java SE 17 — StandardOpenOption](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardOpenOption.html): CREATE_NEW, CREATE, TRUNCATE_EXISTING, APPEND.
- [Oracle Java SE 17 — DirectoryNotEmptyException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/DirectoryNotEmptyException.html): thư mục có mục con.
- [Oracle Java SE 17 — FileAlreadyExistsException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileAlreadyExistsException.html): đường dẫn đích đã chiếm tên.
- [Oracle Java SE 17 — java.io.File](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/File.html): createNewFile, delete và renameTo trong API truyền thống.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-operations-007">Thao tác xuyên file system và giới hạn provider</a>

<details>
<summary>Xem chi tiết</summary>

# Thao tác xuyên file system và giới hạn provider trong Java NIO.2

## Mục tiêu

Java NIO.2 cho phép thao tác tệp thông qua `Path`, `FileSystem`, `FileSystemProvider` và lớp tiện ích `Files`. Một `Path` không nhất thiết trỏ đến tệp trên ổ đĩa của máy đang chạy: nó có thể đại diện một entry trong ZIP/JAR, một file system trong bộ nhớ hoặc một hệ thống lưu trữ được thư viện cung cấp. Vì vậy, **thao tác trên các đường dẫn thuộc những file system/provider khác nhau** có quy tắc và giới hạn riêng, không thể suy diễn từ việc copy/rename trên cùng một ổ đĩa.

Trong bài học, bạn sẽ:

- Phân biệt `Path`, `FileSystem`, `FileSystemProvider` và `FileStore`.
- Biết cách xác định provider của một `Path` và nhận diện trường hợp hai đường dẫn thuộc hai provider khác nhau.
- Hiểu rõ khi nào `Files.copy(source, target)` / `Files.move(source, target)` có thể thao tác xuyên provider; vì sao `ATOMIC_MOVE` có thể không được hỗ trợ.
- Biết các giới hạn của `Path.resolve`, `relativize`, `toFile`, file attributes, symbolic links, permissions và việc đóng file system.
- Thực hành ví dụ Java 17+ copy file từ filesystem mặc định vào một ZIP filesystem.
- Thiết kế thao tác file có kiểm tra lỗi, giới hạn tài nguyên và chiến lược rollback thích hợp.

## 1. File system và provider là gì?

`java.nio.file.Path` biểu diễn một tên đường dẫn **gắn với một `FileSystem`**. `FileSystem` mô tả cách tạo và phân tích đường dẫn, thư mục gốc, dấu phân cách, thuộc tính được hỗ trợ và vòng đời của hệ thống tệp. Một `FileSystemProvider` là triển khai cung cấp các thao tác I/O cho những file system thuộc nó.

```text
                    java.nio.file.Files
                     (API tiện ích)
                           |
                    ủy quyền thao tác
                           v
                FileSystemProvider
                /                \
      provider "file"        provider "jar"
           |                      |
   FileSystem mặc định       ZIP/JAR FileSystem
           |                      |
       Path trên OS          Path trong archive
    /tmp/report.txt         /docs/report.txt
```

Ví dụ:

- Provider **`file`** mặc định giúp truy cập hệ thống tệp do hệ điều hành cung cấp. Một `FileSystem` mặc định có thể nhìn thấy nhiều ổ đĩa/partition/mount.
- Provider **`jar`** (module `jdk.zipfs` của JDK) cho phép đọc và ghi các entry bên trong tệp ZIP/JAR qua API `Path`/`Files`.
- Một thư viện có thể thêm provider cho hệ thống tệp chuyên biệt. Khả năng đọc, ghi, metadata, file locking hoặc symbolic links phụ thuộc vào triển khai thực tế.

**Đừng đồng nhất ba khái niệm:**

| Khái niệm | Ý nghĩa |
| --- | --- |
| `FileSystemProvider` | Triển khai giao diện truy cập filesystem; thường được nhận diện qua URI scheme (`file`, `jar`, …). |
| `FileSystem` | Một thể hiện hệ thống tệp do provider cung cấp; một provider có thể quản lý nhiều thể hiện. |
| `FileStore` | Vùng lưu trữ/đơn vị lưu trữ được filesystem báo cáo (ví dụ phân vùng, volume, mount); không đồng nghĩa với provider. |

**Hai đường dẫn ở hai ổ đĩa không nhất thiết là hai provider.** Trên máy dùng provider `file` mặc định, nhiều drive hoặc mount thường vẫn thuộc cùng provider, dù thao tác rename nguyên tử giữa chúng không được bảo đảm.

## 2. Xác định provider của một `Path`

Đường dẫn cung cấp cách lấy filesystem và provider:

```java
// Snippet minh họa: path là Path đã được tạo.
FileSystem fs = path.getFileSystem();
FileSystemProvider provider = fs.provider();
String scheme = provider.getScheme();
```

Cần các import `java.nio.file.FileSystem` và `java.nio.file.spi.FileSystemProvider` nếu dùng đoạn mã trên như một phần chương trình.

Đối với một path trên filesystem mặc định, scheme thường là `file`. Với một path entry ZIP thuộc provider chuẩn, scheme là `jar`.

Có thể so sánh hai provider bằng:

```java
boolean sameProvider =
        pathA.getFileSystem().provider()
        == pathB.getFileSystem().provider();
```

Đây chỉ là phép so sánh **đối tượng provider**. Nó không chứng minh hai đường dẫn thuộc **cùng `FileSystem`**, cùng `FileStore`, cùng thiết bị vật lý hay có khả năng di chuyển nguyên tử.

Khi muốn so sánh **đúng cùng thể hiện FileSystem**, bạn có thể so sánh `pathA.getFileSystem() == pathB.getFileSystem()` trong ngữ cảnh thích hợp. Khi muốn hỏi filesystem nằm trên vùng lưu trữ nào, `Files.getFileStore(path)` có thể cung cấp thông tin, nhưng provider có thể không hỗ trợ hay giới hạn chi tiết này; hãy xử lý `IOException`.

## 3. Những thao tác hoạt động xuyên provider và những thao tác không

Không có quy tắc “khác provider thì mọi API NIO.2 đều thất bại”. Phải đọc hợp đồng **từng phương thức**.

| Thao tác | Khi source và target khác provider |
| --- | --- |
| `Files.copy(Path, Path, ...)` | API cấp cao **cho phép** source và target thuộc provider khác nhau; vẫn có thể lỗi do quyền, loại file, giới hạn provider, thuộc tính hoặc tài nguyên. |
| `Files.move(Path, Path, ...)` | API cấp cao cũng cho phép target khác provider; khi không thể rename trực tiếp, thao tác không nguyên tử có thể phải copy rồi xóa và có nhiều tình huống thất bại. |
| `Files.move(..., ATOMIC_MOVE)` | **Không được bảo đảm** xuyên filesystem/`FileStore`/provider; nếu không thực hiện nguyên tử được, ném `AtomicMoveNotSupportedException`. |
| `source.resolve(other)` | Là phép ghép đường dẫn của một filesystem; `other` từ provider khác có thể gây `ProviderMismatchException`. |
| `source.relativize(other)` | Yêu cầu đường dẫn tương thích; khác provider có thể gây `ProviderMismatchException` hoặc lỗi liên quan kiểu đường dẫn. |
| `Files.isSameFile(source, target)` | Nếu hai đường dẫn gắn với provider khác nhau thì kết quả là `false`, không phải phép suy luận nội dung hai file giống nhau. |
| `path.toFile()` | Chỉ được hỗ trợ với filesystem mặc định; ZIP/non-default `Path` thường ném `UnsupportedOperationException`. |
| `Files.readAttributes` / `getAttribute` | Tên view và thuộc tính phải được provider hỗ trợ; không có bảo đảm mọi provider đều hỗ trợ POSIX, DOS, owner, ACL, symlink hoặc timestamp với độ chính xác như nhau. |

Phân biệt **`Files.copy`** (phương thức facade cấp cao có thể phối hợp hai provider) với **`FileSystemProvider.copy`** (SPI của một provider, theo hợp đồng yêu cầu hai `Path` thuộc chính provider đó). Khi viết ứng dụng bình thường, hãy dùng `Files.copy` thay vì trực tiếp gọi `provider.copy` với hai kiểu path khác nhau.

### “Có thể thực hiện” không có nghĩa “luôn thành công”

Ngay cả `Files.copy` được thiết kế cho hai provider khác nhau cũng có thể từ chối vì:

- Đích là filesystem chỉ đọc hoặc đã đóng.
- Không hỗ trợ tạo file loại đó, symbolic link, attributes hoặc tùy chọn sao chép.
- File/thư mục đích đã tồn tại, đường dẫn cha chưa có hoặc không đủ quyền.
- Tệp nguồn thay đổi trong quá trình đọc.
- Hết dung lượng, giới hạn quota, lỗi I/O mạng, hoặc dữ liệu lớn hơn giới hạn của provider.

Vì vậy nên xem cross-filesystem operation là **một giao dịch I/O có thể thất bại giữa chừng**, không phải câu lệnh đổi tên tức thì.

## 4. Ví dụ Java 17+: copy file từ filesystem mặc định vào ZIP

Ví dụ sau sử dụng ZIP filesystem của JDK để thể hiện **hai provider thật sự khác nhau** mà không yêu cầu hai ổ đĩa vật lý. Chương trình tạo thư mục tạm, tạo tệp nguồn, mở một archive ZIP làm filesystem, copy dữ liệu vào ZIP, kiểm tra provider và đọc lại nội dung. Cuối chương trình nó đóng ZIP rồi dọn các tệp tạm.

Lưu tệp **`CrossProviderCopyDemo.java`**:

```java
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.util.Map;

public class CrossProviderCopyDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("provider-demo-");
        Path source = root.resolve("hello.txt");
        Path archive = root.resolve("sample.zip");

        try {
            Files.writeString(source, "Hello ZIP", StandardCharsets.UTF_8);

            URI zipUri = URI.create("jar:" + archive.toUri());
            try (FileSystem zip = FileSystems.newFileSystem(
                    zipUri, Map.of("create", "true"))) {
                Path entry = zip.getPath("/hello.txt");
                Files.copy(source, entry);

                System.out.println("source scheme = "
                        + source.getFileSystem().provider().getScheme());
                System.out.println("target scheme = "
                        + entry.getFileSystem().provider().getScheme());
                System.out.println("same provider = "
                        + (source.getFileSystem().provider()
                           == entry.getFileSystem().provider()));
                System.out.println("copied content = "
                        + Files.readString(entry, StandardCharsets.UTF_8));

                try {
                    source.resolve(zip.getPath("other.txt"));
                } catch (ProviderMismatchException e) {
                    System.out.println("resolve = ProviderMismatchException");
                }

                try {
                    entry.toFile();
                } catch (UnsupportedOperationException e) {
                    System.out.println("ZIP toFile = UnsupportedOperationException");
                }
            }
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(archive);
            Files.deleteIfExists(root);
        }
    }
}
```

Chạy trên **JDK 17+ có module `jdk.zipfs`** (JDK đầy đủ thông thường cung cấp module này):

```bash
javac CrossProviderCopyDemo.java
java CrossProviderCopyDemo
```

Stdout trong môi trường JDK chuẩn hỗ trợ ZIP filesystem:

```text
source scheme = file
target scheme = jar
same provider = false
copied content = Hello ZIP
resolve = ProviderMismatchException
ZIP toFile = UnsupportedOperationException
```

**Giải thích:**

1. `source` thuộc filesystem mặc định (`file`). `entry` thuộc ZIP filesystem (`jar`).
2. `Files.copy(source, entry)` chuyển nội dung vào entry trong ZIP dù hai provider khác nhau. Đây là API cấp cao của `Files`, không phải kết quả của việc ép hai đường dẫn thành cùng kiểu.
3. `source.resolve(zip.getPath("other.txt"))` thử trộn một `Path` ZIP vào phép resolve của path filesystem mặc định nên báo `ProviderMismatchException`. Với loại đường dẫn này, `Path` không phải chuỗi ký tự có thể trộn tùy tiện.
4. `entry.toFile()` không dùng được vì `java.io.File` đại diện đường dẫn của filesystem mặc định, không phải đường dẫn entry ZIP.
5. `try-with-resources` **đóng `FileSystem` ZIP** trước khi xóa file archive. Các path trong ZIP không còn dùng để I/O sau khi filesystem đó đã đóng.
6. Ví dụ tạo tệp ZIP thật tại đường dẫn tạm `sample.zip`. Môi trường chỉ chứa JRE tùy biến không có `jdk.zipfs` có thể ném `ProviderNotFoundException`; quyền tạo file/quota cũng có thể gây lỗi I/O.

**Bài học quan trọng:** `Files.copy` làm việc được với nhiều provider, nhưng **không biến `Path` của ZIP thành `Path` của OS**. Hãy giữ và dùng đúng đường dẫn do filesystem tương ứng tạo.

## 5. `Files.move` và giới hạn của `ATOMIC_MOVE`

Khi rename file trong cùng một directory trên cùng vùng lưu trữ, hệ điều hành thường có thể hoàn thành nhanh mà không sao chép nội dung. Khi chuyển dữ liệu sang filesystem hoặc provider khác, không thể giả định thao tác đổi tên ấy còn khả thi.

```java
// Snippet: source và target là Path đã xác định.
Files.move(source, target);
```

`Files.move` cho phép đường dẫn đích khác provider, nhưng thực tế có thể tương đương quá trình **tạo bản sao rồi xóa nguồn**. Vì thế:

- Có thể tốn thời gian tỉ lệ với kích thước file.
- Metadata được bảo toàn không hoàn toàn đồng đều.
- Nếu lỗi giữa chừng, nguồn và đích có thể **cùng tồn tại**, đích có thể thiếu dữ liệu, hoặc trạng thái khác tùy triển khai.
- Không có bảo đảm một cây thư mục phức tạp được tự động di chuyển đệ quy.
- Không có bảo đảm chạy nguyên tử chỉ vì phương thức tên là `move`.

### Yêu cầu atomic move

```java
// Snippet, cần import java.nio.file.Files;
// java.nio.file.StandardCopyOption.
Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
```

`ATOMIC_MOVE` yêu cầu thao tác di chuyển nguyên tử ở mức filesystem. Nếu không hỗ trợ, phương thức ném `AtomicMoveNotSupportedException`. Trường hợp nguồn và đích khác `FileStore` hoặc khác provider là ví dụ điển hình không thực hiện được atomic rename.

**Không được âm thầm bỏ `ATOMIC_MOVE`** nếu nghiệp vụ dựa trên nguyên tắc “người đọc chỉ nhìn thấy bản cũ hoặc bản mới, không bao giờ thấy file đang sao chép dở”. Một fallback không nguyên tử có thể phá hợp đồng an toàn của ứng dụng.

Một cách xử lý ngoại lệ đúng ngữ nghĩa:

```java
// Snippet: source/target đã xác định, phương thức khai báo IOException.
try {
    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
} catch (java.nio.file.AtomicMoveNotSupportedException e) {
    // Yêu cầu nghiệp vụ phải nguyên tử: báo không hỗ trợ,
    // không tự đổi thành move thường.
    throw new java.io.IOException(
            "The destination cannot provide an atomic move", e);
}
```

Nếu nghiệp vụ **chấp nhận move không nguyên tử**, hãy quyết định riêng một quy trình có staging, xác minh nội dung, cleanup và phục hồi khi lỗi. Việc đổi từ atomic sang non-atomic không đơn thuần là sửa một tham số cho chương trình hết exception.

### `REPLACE_EXISTING` không giải quyết mọi vấn đề

`StandardCopyOption.REPLACE_EXISTING` giúp thay thế một số loại tệp đích hiện có. Nó không bảo đảm:

- Thay thế một thư mục đích không rỗng.
- Ghi nguyên tử hoặc rollback khi copy thất bại.
- Bảo toàn mọi thuộc tính của tệp nguồn.
- Không có race giữa lúc kiểm tra tồn tại và lúc thay thế.

Khi `ATOMIC_MOVE` được chỉ định, các tùy chọn move khác theo đặc tả sẽ bị bỏ qua; cách xử lý tệp đích đã tồn tại có thể phụ thuộc triển khai. Đừng coi `REPLACE_EXISTING` cộng `ATOMIC_MOVE` là phép thay thế nguyên tử được bảo đảm trên mọi provider.

## 6. `Files.copy`: nội dung, thuộc tính và symbolic link

Sao chép một file thường bằng:

```java
// Snippet: source/target là Path, xử lý IOException ở nơi gọi.
Files.copy(source, target);
```

Để thay thế file đích nếu chính sách cho phép:

```java
Files.copy(source, target,
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
```

Một số tùy chọn:

- **`REPLACE_EXISTING`:** thử thay thế đích có sẵn theo các giới hạn của API; không tự xóa cây thư mục không rỗng.
- **`COPY_ATTRIBUTES`:** yêu cầu cố gắng sao chép thuộc tính. Những thuộc tính **thực sự** được sao chép phụ thuộc provider và file store; `lastModifiedTime` là mức tối thiểu được nêu khi cả hai phía hỗ trợ, và độ chính xác timestamp có thể thay đổi.
- **`NOFOLLOW_LINKS`:** không đi theo symbolic link nguồn trong quá trình copy. Việc tạo symbolic link tương ứng ở đích có thể thất bại nếu provider đích không hỗ trợ symbolic link.

Copy không phải thao tác nguyên tử. Nếu `IOException` phát sinh, file đích có thể đã xuất hiện và **chưa hoàn chỉnh**. Với nội dung quan trọng, thường cần:

1. Chọn đích tạm trên filesystem **của đích cuối cùng**.
2. Copy vào đích tạm và xác minh nội dung theo yêu cầu (kích thước, checksum hoặc format).
3. Chỉ sau đó mới áp dụng phương thức công bố đích cuối; nếu yêu cầu nguyên tử, thử atomic rename **trong cùng vùng lưu trữ phù hợp**.
4. Nếu atomic rename không hỗ trợ mà nghiệp vụ bắt buộc nguyên tử, báo lỗi thay vì giả định fallback tương đương.
5. Dọn tệp tạm thuộc quyền sở hữu của mình khi lỗi; lưu ý crash có thể khiến file tạm còn lại để quá trình phục hồi xử lý.

Staging trên filesystem đích giúp **giảm** rủi ro xuất hiện file cuối đang được copy dở; nó không tự bảo đảm mọi vấn đề về durability, fsync, security hoặc recovery.

## 7. Directory không được copy/move đệ quy chỉ bằng một lời gọi

`Files.copy(sourceDir, targetDir)` **không tự copy mọi file con**. Theo hợp đồng chung, khi source là directory, thao tác copy tạo một directory đích tương ứng nhưng không sao chép các entry bên trong. Muốn copy tree, cần triển khai logic duyệt riêng, ví dụ `Files.walkFileTree` và xử lý:

- Tạo từng thư mục đích đúng thứ tự.
- Copy file con.
- Giải quyết vấn đề symbolic link và tránh vòng lặp.
- Giới hạn số lượng entry, chiều sâu, tổng byte và lỗi truy cập.
- Ánh xạ metadata dựa trên khả năng của provider đích.
- Hành vi khi đang copy mà file nguồn thay đổi hoặc đích bị trùng.

### Ví dụ Java 17+: copy directory không tự copy file con

Lưu thành **`DirectoryCopyDemo.java`**:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class DirectoryCopyDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("directory-copy-");
        Path source = root.resolve("source");
        Path target = root.resolve("target");

        try {
            Files.createDirectory(source);
            Files.writeString(source.resolve("child.txt"),
                    "data", StandardCharsets.UTF_8);

            Files.copy(source, target);
            System.out.println("target directory exists = "
                    + Files.isDirectory(target));
            System.out.println("child was copied = "
                    + Files.exists(target.resolve("child.txt")));
        } finally {
            Files.deleteIfExists(source.resolve("child.txt"));
            Files.deleteIfExists(source);
            Files.deleteIfExists(target);
            Files.deleteIfExists(root);
        }
    }
}
```

Chạy:

```bash
javac DirectoryCopyDemo.java
java DirectoryCopyDemo
```

Stdout:

```text
target directory exists = true
child was copied = false
```

Ví dụ không phải copy xuyên provider; nó cô lập một giới hạn quan trọng **áp dụng cả với cùng filesystem**. Khi copy cây xuyên provider, giới hạn càng đáng chú ý vì hai bên có thể khác nhau về symlink, attributes và quyền.

## 8. `ProviderMismatchException`: lỗi do trộn đường dẫn không tương thích

Lớp `Path` là **đối tượng thuộc một filesystem cụ thể**, không chỉ là chuỗi path tùy ý. Ví dụ, một path `/docs/report.txt` trong ZIP không chỉ định tệp `/docs/report.txt` của hệ điều hành.

Các thao tác như `resolve` và `relativize` thuộc về **hệ thống quy tắc tên đường dẫn của chính filesystem**. Chuyển path của provider khác vào đó có thể gây `ProviderMismatchException`.

**Tránh cách làm sai:**

```java
// Snippet minh họa: nativePath thuộc provider "file",
// zipPath thuộc provider "jar".
Path wrong = nativePath.resolve(zipPath);
```

Đừng chữa bằng cách ép `zipPath.toString()` thành native `Path` rồi xem nó là cùng file. Chuỗi `/docs/report.txt` không chứa đủ thông tin về provider, archive hoặc mount. Làm vậy thường **đổi ý nghĩa đường dẫn**, không biến path ZIP thành path native.

**Cách đúng:** Khi copy, đưa **hai `Path` gốc** trực tiếp cho `Files.copy(nativePath, zipPath)`. Nếu tạo đường dẫn con trong ZIP, dùng chính `zipPath.getFileSystem().getPath(...)` hoặc `zipDirectory.resolve("child.txt")` với phần tử tương đối thuộc filesystem đó.

### `Path.toFile()` không phải cầu nối giữa mọi provider

`java.io.File` được thiết kế cho filesystem mặc định. Vì thế:

```java
// Snippet: zipEntry là Path của ZIP FileSystem.
File legacy = zipEntry.toFile(); // UnsupportedOperationException.
```

Khi API cũ chỉ nhận `File` nhưng dữ liệu nằm trong ZIP, phải đổi **chiến lược xử lý**, ví dụ dùng `Files.newInputStream(zipEntry)` và một API nhận `InputStream`, hoặc giải nén sang một file tạm trên filesystem mặc định nếu yêu cầu thật sự cần file vật lý. Không nên “chữa lỗi” bằng cách đổi dấu phân cách của `zipEntry.toString()`.

## 9. Thuộc tính file và capability khác nhau theo provider

Ngay cả khi `Files.copy` sao chép thành công **nội dung**, không nên mặc định metadata cũng được giữ y nguyên.

| Khả năng | Vì sao có thể khác |
| --- | --- |
| `basic` attributes | Thông tin size, timestamp hoặc loại file có thể có mức hỗ trợ và độ chính xác khác nhau. |
| `posix` permissions | Không phải provider nào cũng mô hình hóa bit `rwx` kiểu Unix. |
| `dos` attributes | Không phải hệ thống nào cũng hỗ trợ hidden/read-only/archive/system như DOS/Windows. |
| ACL/owner/group | Principal và mô hình quyền có thể không tương thích giữa nguồn và đích. |
| Symbolic links | Có provider không hỗ trợ, hoặc có cách biểu diễn link khác. |
| Hard links | `Files.createLink` có thể không được hỗ trợ hoặc chỉ hợp lệ trong filesystem thích hợp. |
| File locking/memory mapping | Khả năng và ngoại lệ phụ thuộc provider/channel. |
| Timestamp granularity | Sao chép mốc thời gian có thể mất độ chính xác. |
| FileStore / quota | Không phải provider nào cũng báo quota/dung lượng theo cách có ích. |

Kiểm tra danh sách attribute views của filesystem:

```java
// Snippet: path là Path hợp lệ.
System.out.println(path.getFileSystem()
        .supportedFileAttributeViews());
```

Cần nhớ kết quả phản ánh capability của **provider/filesystem** ở mức được công bố; thao tác trên một file cụ thể vẫn có thể thất bại do quyền hoặc loại đối tượng.

Ví dụ nếu provider đích không hỗ trợ `posix`, lời gọi `Files.getPosixFilePermissions(target)` có thể ném `UnsupportedOperationException`. Không cố copy thủ công một tập quyền POSIX vào ZIP filesystem mà chưa kiểm tra mức hỗ trợ và đặc tả của provider.

**`COPY_ATTRIBUTES` không có nghĩa sao chép tất cả metadata.** Thuộc tính nào được copy còn phụ thuộc cả nguồn, đích và loại attribute. Khi metadata là yêu cầu nghiệp vụ quan trọng, hãy liệt kê rõ những thuộc tính bắt buộc, kiểm tra khả năng hỗ trợ và **xác minh lại trên đích**.

## 10. Vòng đời ZIP filesystem và provider tùy biến

Một ZIP filesystem được tạo bằng `FileSystems.newFileSystem(...)` phải được **đóng** để giải phóng tài nguyên và hoàn tất các thao tác nội bộ cần thiết. Khi filesystem đã đóng:

- Các path của nó có thể vẫn tồn tại dưới dạng object Java nhưng **không còn dùng được cho I/O**; thao tác thích hợp có thể ném `ClosedFileSystemException`.
- Không giả định `Files.readString(entry)` vẫn hoạt động sau khi khối `try (FileSystem zip = ...)` kết thúc.
- Không xóa archive ZIP khi vẫn còn một filesystem đang thao tác trên nó nếu cần độ nhất quán dữ liệu.
- Không dùng một filesystem dùng chung trong nhiều thành phần mà không quy định rõ **ai được phép gọi `close()`**.

Đối với provider bên thứ ba, tài liệu của provider là nơi xác định thêm quy tắc mở, đóng, URI scheme, thuộc tính, hỗ trợ song song và chính sách lỗi. Tên `Path` hoặc tên file extension không đủ để kết luận provider nào đang được sử dụng.

## 11. Thiết kế thao tác cross-filesystem an toàn hơn

Một quy trình thực tế thường cần nhiều bước hơn một lời gọi `Files.move`:

1. **Xác định loại nguồn/đích**: regular file, directory, symlink hay special file; kiểm tra khả năng của filesystem đích.
2. **Kiểm soát đường dẫn**: với input bên ngoài, kiểm tra absolute path, `..`, symlink và quyền hạn theo mô hình bảo mật; không dùng riêng `normalize` để coi là an toàn.
3. **Đặt giới hạn tài nguyên**: kích thước byte, số entry, chiều sâu thư mục, tổng dung lượng, thời gian và quota.
4. **Chọn chiến lược ghi**: copy trực tiếp khi có thể chấp nhận file đích chưa hoàn chỉnh lúc lỗi; nếu không, copy vào staging area của đích.
5. **Xác minh**: kiểm tra kích thước hoặc checksum nội dung (khi định dạng và nghiệp vụ yêu cầu), các thuộc tính thật sự phải bảo toàn.
6. **Công bố kết quả**: nếu cần bảo đảm thay thế nguyên tử, chỉ dùng `ATOMIC_MOVE` khi filesystem thực sự hỗ trợ; nếu không, báo không thể đáp ứng yêu cầu.
7. **Xóa nguồn khi đã an toàn**: nếu nghiệp vụ là di chuyển bằng copy+delete, chỉ xóa sau khi xác nhận dữ liệu đích đúng tiêu chí. Thao tác này vẫn không trở thành một giao dịch nguyên tử giữa hai filesystem.
8. **Phục hồi sau lỗi**: log trạng thái, biết file tạm nào do ứng dụng tạo, tránh xóa mất dữ liệu nguồn/đích hợp lệ; thiết kế retry/idempotency nếu cần.

Khi dùng mạng, cloud mount hoặc file system ảo, còn phải xét kết nối bị cắt, tính nhất quán metadata và cơ chế xác thực quyền truy cập riêng. Không có cách “tự động dùng `Files.move`” nào đảm bảo cùng chất lượng giao dịch trên mọi provider.

## 12. Những lỗi phổ biến

1. **Tưởng mọi `Path` đều thuộc `FileSystems.getDefault()`.** ZIP, memory filesystem và provider khác có namespace riêng.
2. **Tưởng hai ổ đĩa là hai provider.** Chúng có thể chung provider `file` nhưng khác `FileStore`.
3. **Tưởng khác provider thì `Files.copy` luôn ném `ProviderMismatchException`.** Facade `Files.copy` cho phép target khác provider; `Path.resolve`/`relativize` lại là chuyện khác.
4. **Tưởng `Files.move` xuyên provider tự động nguyên tử.** Không có bảo đảm; `ATOMIC_MOVE` có thể thất bại.
5. **Bắt `AtomicMoveNotSupportedException` rồi chuyển sang move thường mà không kiểm tra yêu cầu nghiệp vụ.** Fallback có thể phá tính nhất quán mà người đọc kỳ vọng.
6. **Tưởng `COPY_ATTRIBUTES` đồng nghĩa giữ mọi quyền sở hữu/timestamp/ACL.** Tính năng và độ chính xác khác nhau theo provider.
7. **Tưởng `Files.copy` copy đệ quy cả thư mục.** Nó không tự copy các entry con; cần thuật toán duyệt cây riêng.
8. **Tưởng `REPLACE_EXISTING` là cơ chế rollback.** Có thể còn file đích không hoàn chỉnh nếu I/O lỗi giữa chừng.
9. **Gọi `zipPath.toFile()`.** API `File` không thể biểu diễn mọi đường dẫn không thuộc filesystem mặc định.
10. **Lấy `zipPath.toString()` rồi tạo `Path.of(...)`.** Thao tác đó làm mất ý nghĩa ZIP provider và có thể trỏ tới file khác hoàn toàn.
11. **Tiếp tục dùng path sau khi đóng ZIP filesystem.** Có thể nhận `ClosedFileSystemException`.
12. **Không kiểm tra symbolic link và security boundary.** Copy link khác copy dữ liệu đích link; traversal/race cần chính sách rõ.
13. **Chỉ kiểm tra `Files.exists` trước khi thao tác rồi nghĩ rằng không thể xảy ra race.** File có thể bị thay đổi giữa hai lần gọi.
14. **Dùng metadata size làm toàn bộ cơ chế giới hạn dữ liệu.** Nguồn có thể thay đổi, dữ liệu có thể quá lớn, cần đếm/giới hạn trong quá trình xử lý.

## 13. Câu hỏi tự kiểm tra

**1.** Một path ở ổ C: và path ở ổ D: có nhất thiết khác provider không?

**Đáp án:** Không. Chúng có thể cùng dùng provider `file` mặc định, nhưng khác `FileStore`, và atomic move giữa chúng vẫn có thể không được hỗ trợ.

**2.** Có thể gọi `Files.copy(pathOnDisk, pathInsideZip)` không?

**Đáp án:** Có thể, nếu ZIP filesystem được mở để ghi, đích hợp lệ và provider hỗ trợ thao tác cần thiết. `Files.copy` cấp cao cho phép đường dẫn ở hai provider khác nhau.

**3.** Vì sao `pathOnDisk.resolve(pathInsideZip)` không phải cách tạo địa chỉ entry ZIP?

**Đáp án:** Hai path thuộc namespace khác nhau. Phép resolve của filesystem mặc định không hiểu path ZIP và có thể ném `ProviderMismatchException`. Hãy dùng ZIP filesystem để tạo path đích.

**4.** `Files.move(source, target, ATOMIC_MOVE)` thất bại khi khác filesystem. Có thể tự động bỏ `ATOMIC_MOVE` không?

**Đáp án:** Chỉ khi nghiệp vụ **chấp nhận không nguyên tử** và đã thiết kế logic xác minh/phục hồi thích hợp. Nếu yêu cầu nguyên tử là bắt buộc, phải báo không hỗ trợ.

**5.** `Files.copy(sourceDirectory, targetDirectory)` có sao chép cả cây con không?

**Đáp án:** Không. Muốn sao chép cây thư mục phải tự duyệt và copy từng entry theo chính sách symbolic link và lỗi.

**6.** Khi dùng `COPY_ATTRIBUTES`, có chắc chắn giữ nguyên toàn bộ POSIX permissions và ACL trong ZIP không?

**Đáp án:** Không. Thuộc tính được hỗ trợ và chuyển giao phụ thuộc provider/`FileStore`; cần kiểm tra, xác minh những thuộc tính thật sự quan trọng.

**7.** Tại sao phải đóng `FileSystem` ZIP?

**Đáp án:** Để giải phóng tài nguyên và hoàn tất các thao tác nội bộ cần thiết. Khi ZIP filesystem đã đóng, các entry `Path` của nó không còn sử dụng được cho I/O bình thường.

**8.** Magic scheme `jar` và `file` có đồng nghĩa hai vùng lưu trên hai thiết bị vật lý khác nhau không?

**Đáp án:** Không. ZIP archive bản thân có thể nằm trên cùng thiết bị lưu trữ với file nguồn; provider khác nói về **cách truy cập và namespace**, không nhất thiết nói về phần cứng.

## 14. Tài liệu Java 17

- [`Files.copy`, `Files.move` và các tùy chọn](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [`FileSystemProvider` và khác biệt SPI/API cấp cao](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/spi/FileSystemProvider.html)
- [`Path` — `resolve`, `relativize`, `toFile`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html)
- [`jdk.zipfs` — thuộc tính và tùy chọn của ZIP File System](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.zipfs/module-summary.html)

**Ghi nhớ:** `Path` luôn gắn với một filesystem/provider. `Files.copy` và `Files.move` có thể hoạt động **xuyên provider** theo hợp đồng API, nhưng không có bảo đảm mọi thuộc tính được giữ hoặc thao tác di chuyển là nguyên tử. Đừng trộn `Path` từ hai namespace bằng `resolve`, đừng mặc định `toFile()` hoạt động với ZIP, và luôn xử lý lỗi, giới hạn provider, metadata và vòng đời tài nguyên một cách tường minh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-operations-008">exists, notExists và trường hợp không xác định được trạng thái</a>

<details>
<summary>Xem chi tiết</summary>

# `Files.exists()`, `Files.notExists()` và trạng thái không xác định

## Mục tiêu bài học

Trong Java NIO.2, hai phương thức `Files.exists(Path, LinkOption...)` và `Files.notExists(Path, LinkOption...)` giúp kiểm tra **khả năng xác minh trạng thái tồn tại của một đường dẫn**. Chúng có vẻ đối xứng, nhưng **không phải hai hàm phủ định logic của nhau trong mọi hoàn cảnh**. Nếu không thể xác định trạng thái — chẳng hạn do lỗi truy cập metadata hoặc filesystem không phản hồi theo cách có thể kết luận — **cả hai có thể cùng trả về `false`**.

Sau bài này, bạn có thể:

- Phân biệt `Files.exists(path)`, `Files.notExists(path)` và toán tử `!Files.exists(path)`.
- Diễn giải đúng **ba tình trạng**: *xác nhận tồn tại*, *xác nhận không tồn tại*, và *không xác định được*.
- Hiểu `LinkOption.NOFOLLOW_LINKS`, symbolic link hợp lệ hoặc bị hỏng, và sự khác nhau giữa **link** và **đích của link**.
- Không nhầm “có đường dẫn” với “là file thông thường”, “có quyền đọc”, “nội dung ổn định” hay “mở thành công”.
- Tránh lỗi **TOCTOU (time-of-check-to-time-of-use)** khi kiểm tra trạng thái rồi mới mở, tạo, xóa hoặc sửa file.
- Biết khi nào dùng phép thao tác trực tiếp và ngoại lệ (`Files.readAttributes`, `Files.newInputStream`, `Files.createFile`…) thay cho việc chỉ tin kết quả boolean.
- Viết và chạy mã Java 17+, xử lý trạng thái không xác định mà không tự suy diễn thành file không tồn tại.

## 1. Nền tảng: `Path` không đồng nghĩa một file đã tồn tại

```java
Path path = Path.of("data", "report.txt");
```

Đây là **đoạn minh họa** với `import java.nio.file.Path;`. Việc tạo `Path` chỉ tạo **một đối tượng biểu diễn đường dẫn**, không tự tạo file, không kiểm tra quyền và không đảm bảo file có thật. Hệ điều hành và filesystem quyết định đường dẫn đó có thể được phân giải tới một entry tại thời điểm thực hiện thao tác I/O hay không.

Các yếu tố cần phân biệt:

- **Path**: cách biểu diễn một vị trí/đường dẫn trong một filesystem.
- **Entry**: đối tượng tại tên đó (file thông thường, thư mục, symbolic link, hoặc loại khác tùy filesystem).
- **Metadata**: thuộc tính file/entry như loại, kích thước, timestamps, quyền.
- **Filesystem provider**: lớp hiện thực đọc/ghi theo filesystem (default provider, ZipFS, provider bên thứ ba…).
- **Thao tác I/O**: mở file, tạo file, đọc thuộc tính, xóa, đổi tên — có thể phát sinh ngoại lệ và có thể bị ảnh hưởng bởi thay đổi đồng thời.

`Files.exists` và `Files.notExists` kiểm tra thông tin tồn tại **ở thời điểm gần lời gọi**, không đặt khóa lên entry và không biến đường dẫn thành reference bất biến.

## 2. API của `exists` và `notExists`

Hai chữ ký trong `java.nio.file.Files`:

```java
public static boolean exists(
    Path path, LinkOption... options);

public static boolean notExists(
    Path path, LinkOption... options);
```

Đây là **chữ ký API minh họa**, không phải mã cần định nghĩa lại trong ứng dụng. `LinkOption` nằm trong `java.nio.file`. Nếu bỏ các option, **mặc định các phương thức theo symbolic link** khi kiểm tra.

Ví dụ sử dụng:

```java
Path p = Path.of("data.txt");

boolean confirmedPresent = Files.exists(p);
boolean confirmedAbsent = Files.notExists(p);

boolean linkEntryPresent =
    Files.exists(p, LinkOption.NOFOLLOW_LINKS);
```

Đây là **đoạn minh họa**; cần `Files`, `Path`, `LinkOption`, và `p` phải có ý nghĩa với filesystem hiện hành.

Các diễn giải chính:

| Biểu thức | Chỉ nên kết luận |
|---|---|
| `Files.exists(path) == true` | Đã xác minh đối tượng theo chính sách link được chọn **có tồn tại** tại lần kiểm tra |
| `Files.notExists(path) == true` | Đã xác minh đối tượng theo chính sách link được chọn **không tồn tại** tại lần kiểm tra |
| `Files.exists(path) == false` | **Chưa xác minh được rằng nó tồn tại**; có thể không tồn tại **hoặc** chưa biết |
| `Files.notExists(path) == false` | **Chưa xác minh được rằng nó không tồn tại**; có thể tồn tại **hoặc** chưa biết |
| Cả hai cùng `false` | **Trạng thái không xác định** từ hai phép kiểm tra |
| `!Files.exists(path)` | Phủ định kết quả boolean của một phép kiểm tra; **không tương đương** `Files.notExists(path)` trong trường hợp chưa biết |

Từ đây rút ra một điều: **một kết quả boolean `false` không luôn mang thông tin âm tính chắc chắn về filesystem**.

## 3. Bảng chân trị ba trạng thái: điểm cốt lõi

Nếu hai kiểm tra quan sát **cùng một trạng thái không đổi** và cùng chính sách theo link, ta có:

| Trạng thái có thể xác minh | `Files.exists(p)` | `Files.notExists(p)` | Phải hiểu là |
|---|:---:|:---:|---|
| Đã xác minh tồn tại | `true` | `false` | **PRESENT** |
| Đã xác minh không tồn tại | `false` | `true` | **ABSENT** |
| Không thể xác minh trạng thái | `false` | `false` | **UNKNOWN** |
| `true` và `true` | Không phải kết quả nhất quán của một trạng thái bất biến | | Nếu quan sát ở hai thời điểm khác nhau, có thể do thay đổi đồng thời hoặc yếu tố provider |

**Không nên viết:**

```java
if (!Files.exists(path)) {
    System.out.println("File chắc chắn không tồn tại");
}
```

Đây là **phản ví dụ**: nhánh này cũng chạy khi `exists` không xác minh được trạng thái. Nếu cần thông báo chắc chắn không tồn tại, kiểm tra `Files.notExists(path)`.

Một cách làm rõ cả ba trạng thái:

```java
if (Files.exists(path)) {
    System.out.println("Đã xác minh tồn tại");
} else if (Files.notExists(path)) {
    System.out.println("Đã xác minh không tồn tại");
} else {
    System.out.println("Không xác minh được trạng thái");
}
```

Đây là **đoạn minh họa**, `path` là `Path` và đã import `Files`. **Lưu ý:** Hai lời gọi xảy ra ở **hai thời điểm**; file có thể được tạo/xóa/đổi tên ở giữa. Vì vậy, đây là cách mô tả hợp lý để **phân loại quan sát gần đúng**, không phải giao dịch nguyên tử về trạng thái file.

### Vì sao có UNKNOWN?

Một số tình huống khiến provider không thể xác minh chắc chắn:

1. Quyền truy cập directory hoặc metadata không đủ để truy vấn đường dẫn.
2. Storage từ xa hoặc filesystem provider không phản hồi, không thể xác minh trạng thái.
3. Lỗi hệ thống tệp, môi trường container, mount, hoặc tầng I/O khiến tra cứu metadata không có kết quả kết luận được.
4. Một symbolic link hoặc đường dẫn có quá trình phân giải lỗi.
5. Một số tình huống đặc thù của filesystem/provider làm thông tin tồn tại không sẵn có.

**Không khẳng định tuyệt đối “permission denied luôn làm cả hai trả false”**: trên một filesystem cụ thể, metadata có thể vẫn đủ để kết luận tồn tại; trên provider khác có thể trả giá trị khác hoặc ném `SecurityException` theo chính sách bảo mật. Điều quan trọng là không biến trường hợp không chắc thành `ABSENT`.

Theo hợp đồng Java, khi không thể xác định kết quả vì lỗi truy cập file, `exists` và `notExists` có thể trả `false`; chúng không cung cấp mọi chi tiết lỗi I/O qua kiểu trả về boolean. Một số kiểm tra bảo mật có thể dẫn đến `SecurityException` không kiểm tra (*unchecked*).

## 4. Tại sao `notExists()` không phải `!exists()`?

Trong logic Boolean hai giá trị thông thường:

```text
exists = true  → not exists = false
exists = false → not exists = true
```

Nhưng các API này không chỉ biểu diễn trạng thái vật lý. Chúng biểu diễn **khả năng xác minh** một mệnh đề từ filesystem:

```text
                        Phép tra cứu trạng thái
                                 │
               ┌─────────────────┼──────────────────┐
               ▼                 ▼                  ▼
        Xác nhận có         Xác nhận không        Không xác minh
               │                 │                  │
        exists = true      exists = false     exists = false
        notExists = false  notExists = true   notExists = false
```

Do đó:

```text
!Files.exists(p)        = "không nhận được kết quả xác nhận có"
Files.notExists(p)      = "nhận được kết quả xác nhận không"
```

Hai câu khác nhau khi kết quả bị **unknown**.

Trong code nghiệp vụ, hãy đặt tên biến theo thông tin thật:

```java
boolean isConfirmedMissing = Files.notExists(path);
boolean isConfirmedPresent = Files.exists(path);
```

Tránh đặt tên `missing` cho `!Files.exists(path)` nếu người đọc có thể hiểu nó là sự vắng mặt chắc chắn.

Nếu hệ thống cần **một trạng thái duy nhất**, một kiểu enum ba giá trị sẽ diễn đạt hợp đồng tốt hơn hai cờ boolean, dù cần nhớ đây vẫn chỉ là snapshot không nguyên tử từ nhiều phép kiểm tra.

## 5. Ví dụ Java 17: trạng thái tệp tồn tại và tệp đã xóa

Lưu **`ExistenceBasicsDemo.java`**:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ExistenceBasicsDemo {
    private static void show(String label, Path path) {
        System.out.println(label
            + " exists=" + Files.exists(path)
            + " notExists=" + Files.notExists(path));
    }

    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("exists-demo-", ".txt");

        try {
            Files.writeString(file, "hello", StandardCharsets.UTF_8);
            show("created", file);
        } finally {
            Files.deleteIfExists(file);
        }

        show("removed", file);
    }
}
```

Chạy:

```bash
javac ExistenceBasicsDemo.java
java ExistenceBasicsDemo
```

Kết quả trong filesystem bình thường, nếu tạo/xóa và tra cứu metadata đều thành công:

```text
created exists=true notExists=false
removed exists=false notExists=true
```

Giải thích:

1. `Files.createTempFile` tạo một file mới trên filesystem và trả `Path`.
2. `Files.writeString` ghi nội dung vào file; việc ghi nội dung không ảnh hưởng đến bản chất kiểm tra tồn tại.
3. Trong vùng `try`, file đang tồn tại và có thể xác minh nên `exists == true`.
4. `Files.deleteIfExists` xóa file trong khối `finally`; nếu việc xóa thành công, lần kiểm tra tiếp theo xác nhận vắng mặt.
5. Chương trình **không chứng minh** filesystem luôn trả được kết quả chắc chắn khi mất quyền, I/O lỗi hoặc trạng thái thay đổi đồng thời.

Ví dụ không in tên file tạm vì phần ngẫu nhiên của tên khác nhau ở mỗi lần chạy. Không cần thay tên thật của người dùng bằng dữ liệu cố định để có output deterministic; chỉ in trạng thái đã kiểm tra.

## 6. `LinkOption.NOFOLLOW_LINKS`: kiểm tra link hay đích?

Symbolic link (liên kết tượng trưng) là một entry đại diện đường dẫn tham chiếu đến entry khác. Mặc định, `Files.exists(link)` và `Files.notExists(link)` **theo symbolic link**. Khi truyền `LinkOption.NOFOLLOW_LINKS`, Java kiểm tra **bản thân link** thay vì đi đến target.

Ví dụ ý tưởng:

```text
directory/
├── target.txt                 (file thật)
└── shortcut.txt -> target.txt (symbolic link)
```

Nếu `target.txt` còn tồn tại, cả kiểm tra theo link lẫn không theo link có thể xác nhận có entry:

```java
Files.exists(link);                        // thường true
Files.exists(link, LinkOption.NOFOLLOW_LINKS); // true khi link hiện hữu
```

Đây là **đoạn minh họa**, `link` là `Path` đã trỏ tới symbolic link. Nhưng khi target bị xóa:

```text
directory/
├── target.txt                  (đã xóa)
└── shortcut.txt -> target.txt  (link vẫn còn)
```

Hai câu hỏi khác nhau:

- **Theo link (mặc định):** Target có tồn tại không? Nếu đã xác minh target vắng mặt, `Files.exists(link)` là `false` và `Files.notExists(link)` là `true`.
- **Không theo link:** Entry symbolic link có tồn tại không? Nếu có và xác minh được, `Files.exists(link, NOFOLLOW_LINKS)` là `true` và `Files.notExists(link, NOFOLLOW_LINKS)` là `false`.

### Ma trận tượng trưng

| Trường hợp | Mặc định follow links | `NOFOLLOW_LINKS` |
|---|---|---|
| File thông thường tồn tại | Xác nhận tồn tại | Xác nhận tồn tại |
| Symlink tồn tại, target tồn tại | Thường xác nhận tồn tại | Xác nhận **link** tồn tại |
| Symlink tồn tại, target đã mất (*dangling link*) | Thường xác nhận target không tồn tại nếu có thể xác minh | Xác nhận **link** tồn tại |
| Đường dẫn thật sự không có entry | Xác nhận không tồn tại nếu có thể xác minh | Xác nhận không tồn tại nếu có thể xác minh |
| Không đủ thông tin truy cập metadata | Có thể UNKNOWN | Có thể UNKNOWN |

**Điểm mấu chốt:** `NOFOLLOW_LINKS` thay đổi **đối tượng đang được hỏi**, chứ không bảo đảm truy vấn chắc chắn thành công hoặc khắc phục mọi lỗi quyền.

### Khi nào dùng `NOFOLLOW_LINKS`?

- Công cụ quản trị muốn biết chính **symbolic link entry** có tồn tại hay không.
- Bộ dọn dẹp cần phân biệt “link hỏng nhưng vẫn có entry” với “tên hoàn toàn trống”.
- Logic không muốn metadata lookup tự động đi qua symlink.
- Một số workflow cần kiểm tra link rồi quyết định có xóa/đổi tên entry đó hay không.

Tuy nhiên, **kiểm tra `NOFOLLOW_LINKS` rồi mở file bình thường không tự ngăn rủi ro symlink**. Một process khác có thể thay entry sau kiểm tra. Với yêu cầu bảo mật, cần API mở/đọc phù hợp, quyền OS và xử lý TOCTOU.

## 7. Ví dụ Java 17: kiểm tra symbolic link bị hỏng (môi trường hỗ trợ)

Tạo symbolic link là thao tác có thể bị giới hạn trên Windows, trong container hoặc với filesystem provider nhất định. Vì vậy, ví dụ dưới đây **chỉ in kết quả xác định khi tạo link thành công và filesystem có ngữ nghĩa thông thường**; nếu thiếu quyền hỗ trợ, chương trình không giả định mọi môi trường đều giống nhau.

Lưu **`SymlinkExistenceDemo.java`**:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public class SymlinkExistenceDemo {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("link-demo-");
        Path link = dir.resolve("shortcut.txt");

        try {
            try {
                Files.createSymbolicLink(
                    link, Path.of("missing-target.txt"));
            } catch (UnsupportedOperationException
                     | SecurityException
                     | IOException ex) {
                System.out.println("Symlink unavailable");
                return;
            }

            System.out.println(
                "follow exists=" + Files.exists(link));
            System.out.println(
                "follow notExists=" + Files.notExists(link));
            System.out.println(
                "nofollow exists=" +
                    Files.exists(link, LinkOption.NOFOLLOW_LINKS));
            System.out.println(
                "nofollow notExists=" +
                    Files.notExists(link, LinkOption.NOFOLLOW_LINKS));
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(dir);
        }
    }
}
```

Chạy:

```bash
javac SymlinkExistenceDemo.java
java SymlinkExistenceDemo
```

Khi tạo symbolic link được hỗ trợ, link tồn tại và target không có, kết quả thông thường là:

```text
follow exists=false
follow notExists=true
nofollow exists=true
nofollow notExists=false
```

Nếu môi trường không cho phép tạo symlink, chương trình in `Symlink unavailable` rồi thoát sau khi chạy `finally`. Khi filesystem không xác định được target, cặp kiểm tra follow có thể đều `false`; do đó **output trên là kỳ vọng có điều kiện**, không phải cam kết xuyên mọi provider.

Mã dùng target tương đối `missing-target.txt` để tạo dangling symlink; nó không cần tạo file đích thật. Khi xóa `link`, `Files.deleteIfExists(link)` xóa **entry link** chứ không yêu cầu target phải tồn tại. Nếu quyền xóa bị hạn chế hoặc directory bị can thiệp đồng thời, cleanup có thể ném `IOException` — ví dụ không cố che giấu lỗi này.

## 8. Không xác định trạng thái: cách biểu diễn rõ trong chương trình

Do hai phương thức trả boolean, một ứng dụng có thể muốn gói trạng thái thành enum. Ví dụ:

```java
enum Existence {
    PRESENT, ABSENT, UNKNOWN
}
```

Mã enum trên là **đoạn định nghĩa minh họa** cho một lớp hoặc file Java; enum chỉ mô tả trạng thái quan sát, không làm filesystem trở nên nguyên tử.

### Ví dụ Java 17: phân loại ba trạng thái

Lưu **`ExistenceStateDemo.java`**:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public class ExistenceStateDemo {
    enum State {
        PRESENT, ABSENT, UNKNOWN
    }

    static State probe(Path path, LinkOption... options) {
        if (Files.exists(path, options)) {
            return State.PRESENT;
        }
        if (Files.notExists(path, options)) {
            return State.ABSENT;
        }
        return State.UNKNOWN;
    }

    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("state-demo-", ".dat");
        try {
            System.out.println("present: " + probe(file));
        } finally {
            Files.deleteIfExists(file);
        }
        System.out.println("absent: " + probe(file));
    }
}
```

Chạy:

```bash
javac ExistenceStateDemo.java
java ExistenceStateDemo
```

Stdout trong điều kiện filesystem tạo/xóa và truy vấn bình thường:

```text
present: PRESENT
absent: ABSENT
```

**Không cố mô phỏng UNKNOWN bằng cách thay đổi quyền với một thư mục tạm** rồi khẳng định kết quả cụ thể. Trên Windows, Unix, container hoặc khi chương trình chạy với quyền đặc biệt, thao tác permission có thể tạo trạng thái khác nhau; thậm chí vẫn xác minh được sự tồn tại dù không được phép đọc nội dung. Để kiểm thử nhánh UNKNOWN một cách deterministic, nên **tách logic phân loại ra khỏi API filesystem** và cung cấp kết quả probe qua một abstraction có thể giả lập.

Ví dụ hướng kiểm thử:

```text
probe.exists = true,  probe.notExists = false  → PRESENT
probe.exists = false, probe.notExists = true   → ABSENT
probe.exists = false, probe.notExists = false  → UNKNOWN
```

Đó là fixture trạng thái logic, **không phải** kết luận hệ điều hành đã tạo ra ba trạng thái đó trong một lần chạy.

### Hạn chế: hai lần tra cứu không phải snapshot atomic

Trong `probe`, `exists` và `notExists` là hai lượt truy vấn. Nếu entry bị tạo/xóa giữa các lượt, kết quả enum chỉ phản ánh logic trên hai lần quan sát, **không phải trạng thái chắc chắn tại một thời điểm duy nhất**. Hàm hữu ích cho báo cáo UI/diagnostics, nhưng không phải giao thức đồng bộ an toàn để quyết định có tạo/xóa file trong điều kiện tranh chấp.

## 9. `Files.exists` không đồng nghĩa file có thể đọc hoặc ghi

Đường dẫn có thể tồn tại nhưng:

- Đó là **directory**, symbolic link, FIFO, device node hoặc loại entry khác.
- File bị từ chối quyền đọc/ghi.
- File hiện có, nhưng ngay sau đó bị process khác xóa, thay nội dung hoặc đổi tên.
- Filesystem/provider gặp lỗi I/O khi mở.
- File là symlink trỏ tới đường dẫn bị thay đổi hoặc nằm ngoài root an toàn.
- Một kho lưu trữ từ xa có thể mất kết nối sau khi trả metadata.

Vì vậy:

```java
if (Files.exists(path)) {
    try (var input = Files.newInputStream(path)) {
        // ...
    }
}
```

Đây là **đoạn minh họa**, với `path` là `Path` và xử lý `IOException`. Kiểm tra tồn tại ban đầu không bảo đảm `newInputStream` thành công. Trong mã thực tế, nếu mục tiêu là đọc file, thường **thử mở file trực tiếp** và xử lý ngoại lệ sẽ đáng tin hơn so với kiểm tra trước bằng `exists`.

| Câu hỏi | API/chiến lược tương ứng |
|---|---|
| Có xác nhận entry đang tồn tại không? | `Files.exists(path, options)` |
| Có xác nhận entry không tồn tại không? | `Files.notExists(path, options)` |
| Có thể đọc từ đường dẫn này ngay bây giờ không? | Thử `Files.newInputStream`, đọc và xử lý lỗi |
| Có thể ghi file mới mà không ghi đè file cũ? | `Files.createFile` hoặc mở với `CREATE_NEW` |
| Entry có phải file thông thường? | `Files.isRegularFile` hoặc `BasicFileAttributes.isRegularFile()` |
| Có thể đọc metadata hoặc biết lý do thất bại? | `Files.readAttributes` với `try/catch` |
| File có dữ liệu chính xác/có đúng định dạng không? | Mở, đọc, parse và validate nội dung |
| File có bền vững sau mất điện không? | Đòi hỏi thiết kế sync/commit riêng, không suy từ `exists` |

Các hàm như `Files.isRegularFile`, `Files.isDirectory`, `Files.isReadable`, `Files.isWritable` cũng có thể trả `false` khi không xác minh được điều kiện. Không tự động diễn giải `false` của mọi predicate thành bằng chứng chắc chắn về nguyên nhân.

## 10. Khi cần chẩn đoán lý do: `Files.readAttributes` và ngoại lệ

`exists`/`notExists` giúp biểu đạt câu hỏi đơn giản bằng boolean nhưng **cố ý không truyền mọi lỗi I/O theo kiểu checked exception**. Nếu cần phân biệt đường dẫn không tồn tại với lỗi truy cập, nên cân nhắc API thực hiện thao tác cần thiết và cho phép bắt ngoại lệ.

Ví dụ:

```java
BasicFileAttributes attributes = Files.readAttributes(
    path,
    BasicFileAttributes.class,
    LinkOption.NOFOLLOW_LINKS
);
```

Đây là **đoạn minh họa**, cần import `java.nio.file.Files`, `java.nio.file.LinkOption`, `java.nio.file.attribute.BasicFileAttributes`, và xử lý `IOException`. Khi thành công, bạn nhận được thông tin `isRegularFile()`, `isDirectory()`, `isSymbolicLink()`, `size()`, v.v. Khi thất bại, API có thể ném `NoSuchFileException`, `AccessDeniedException` hoặc các kiểu `IOException` khác tùy provider.

### Ví dụ Java 17: đọc metadata với cách báo lỗi có ngữ cảnh

Lưu **`ReadAttributesStatusDemo.java`**:

```java
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class ReadAttributesStatusDemo {
    static String describe(Path path) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(
                path,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
            );
            if (attrs.isRegularFile()) {
                return "regular file";
            }
            if (attrs.isDirectory()) {
                return "directory";
            }
            if (attrs.isSymbolicLink()) {
                return "symbolic link";
            }
            return "other entry";
        } catch (NoSuchFileException ex) {
            return "missing";
        } catch (AccessDeniedException ex) {
            return "access denied";
        } catch (IOException ex) {
            return "I/O unknown";
        } catch (SecurityException ex) {
            return "security denied";
        }
    }

    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("attr-demo-", ".txt");
        try {
            System.out.println("created: " + describe(file));
        } finally {
            Files.deleteIfExists(file);
        }
        System.out.println("removed: " + describe(file));
    }
}
```

Biên dịch/chạy:

```bash
javac ReadAttributesStatusDemo.java
java ReadAttributesStatusDemo
```

Stdout trên filesystem hỗ trợ basic attributes và hoạt động bình thường:

```text
created: regular file
removed: missing
```

Điểm cần chú ý:

- `NoSuchFileException` cung cấp ngữ cảnh khác `AccessDeniedException`; `Files.exists` không tự trả cho caller một object exception tương ứng.
- `NOFOLLOW_LINKS` khiến `readAttributes` mô tả **entry link** nếu target là symbolic link.
- Một lỗi provider có thể là `FileSystemException` hoặc subtype `IOException` khác, không phải lúc nào cũng thuộc hai nhánh đầu.
- Với trường hợp sử dụng thật, đừng “thử kiểm tra tồn tại rồi đọc attributes” nếu một lần `readAttributes` đã đáp ứng mục tiêu; thêm lần truy vấn có thể tạo race không cần thiết.
- Ngay cả metadata vừa đọc vẫn có thể bị lỗi thời trước thao tác tiếp theo.

## 11. TOCTOU: kiểm tra rồi thao tác không phải nguyên tử

Một lỗi thiết kế phổ biến:

```java
if (Files.notExists(path)) {
    Files.writeString(path, "new content");
}
```

Đây là **phản ví dụ**. Kể cả khi `notExists` trả `true`, một process khác có thể tạo file tại `path` **sau lần kiểm tra và trước khi ghi**. `Files.writeString` mặc định có thể tạo hoặc truncate file theo cách mở tương ứng, gây ghi đè ngoài ý muốn. **Không** dùng kiểm tra tồn tại trước làm thay thế cho thao tác tạo file nguyên tử.

Lược đồ:

```text
Tiến trình A                        Tiến trình B
------------                       -------------
Files.notExists(p) → true
                                   Tạo p
Files.writeString(p, ...)
→ có thể tác động vào p             (race!)
```

Kỹ thuật đúng khi yêu cầu “tạo file nếu chưa có và không ghi đè”:

```java
try {
    Files.createFile(path);
    // Tạo thành công.
} catch (FileAlreadyExistsException ex) {
    // Tên đã có, xử lý chính sách không ghi đè.
}
```

Đây là **đoạn minh họa**, với `path` là `Path`; cần import `Files`, `FileAlreadyExistsException`, và xử lý các `IOException` khác. Theo hợp đồng `Files.createFile`, **kiểm tra sự tồn tại và tạo file là một thao tác nguyên tử** đối với các hoạt động filesystem khác ảnh hưởng tới directory, tùy khả năng hỗ trợ của provider. Nếu không tạo được vì quyền/trục trặc khác, API ném ngoại lệ khác — không được chuyển mọi lỗi thành “file đã tồn tại”.

Một lựa chọn khác khi muốn **tạo rồi ghi**, đồng thời từ chối ghi đè file có sẵn:

```java
try (var out = Files.newOutputStream(
        path,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE)) {
    out.write(payload);
}
```

Đây là **đoạn minh họa**, `payload` là `byte[]`, cần import `Files`, `StandardOpenOption` và xử lý `IOException`. `CREATE_NEW` dành cho mục tiêu “file chưa tồn tại mới tạo”; nếu đã có entry, thao tác mở/tạo thất bại thay vì âm thầm truncate file cũ. Quy tắc riêng của provider và xử lý symlink vẫn phải tuân theo hợp đồng của phương thức thực tế.

### Xóa file có cần `exists` trước không?

Không cần:

```java
Files.deleteIfExists(path);
```

Đây là **đoạn minh họa**, `path` là `Path` và xử lý `IOException`. `deleteIfExists` thực hiện mục tiêu “xóa nếu có” trong **một API**, thay vì tự làm:

```java
if (Files.exists(path)) {
    Files.delete(path);
}
```

Đây là **phản ví dụ không cần thiết** vì có race và thừa lần kiểm tra. Lưu ý `deleteIfExists` có thể vẫn ném `IOException` nếu việc xóa gặp lỗi; `false` thường biểu thị không có gì được xóa, không phải chứng nhận vĩnh viễn rằng tên luôn vắng mặt. API cũng không tự giải quyết mọi rủi ro symlink/permission trong môi trường không tin cậy.

## 12. `exists`, `notExists` và `Files.isRegularFile`

Một đường dẫn có thể tồn tại nhưng không phải **file thông thường**:

```text
project/
├── config.json   (file)
├── cache/        (directory)
└── latest -> config.json (symlink)
```

Khi cần phân biệt loại:

```java
if (Files.isRegularFile(path)) {
    System.out.println("regular file");
} else if (Files.isDirectory(path)) {
    System.out.println("directory");
} else if (Files.isSymbolicLink(path)) {
    System.out.println("symbolic link");
} else {
    System.out.println("other or cannot determine");
}
```

Đây là **đoạn minh họa**, `path` là `Path`, đã import `Files`.

Lưu ý:

- `isRegularFile` và `isDirectory` mặc định **theo symbolic link**, nên nếu link trỏ đến file thường thì `isRegularFile(link)` có thể là `true`.
- `isSymbolicLink` kiểm tra entry link theo nghĩa của API, không phải “nó trỏ đến file gì”.
- Nếu các predicate đều `false`, không được mặc định path chắc chắn là loại entry “other”; đó cũng có thể là trường hợp **không xác minh được**.
- Muốn một bộ thuộc tính liên quan được đọc một lần, dùng `Files.readAttributes(..., BasicFileAttributes.class, options)` khi phù hợp.
- Không giả định `exists == true` kéo theo `isReadable == true`, hoặc `isRegularFile == true` là cam kết file vẫn tồn tại đến lần mở sau.

## 13. Trường hợp đường dẫn tương đối và quyền truy cập

`Path.of("report.txt")` là một đường dẫn tương đối; nó được diễn giải theo filesystem và **working directory** của tiến trình khi giải quyết một thao tác. Nếu chạy trong IDE, terminal, service hoặc container với working directory khác nhau, cùng chuỗi đường dẫn có thể trỏ đến mục khác.

```java
Path relative = Path.of("report.txt");
System.out.println(relative.toAbsolutePath());
```

Đây là **đoạn minh họa**, kết quả tuyệt đối phụ thuộc môi trường.

Một kết quả `Files.notExists(relative)` chỉ có ý nghĩa với **vị trí mà runtime hiện diễn giải**, không phải bằng chứng mọi file tên `report.txt` đều không tồn tại trên máy.

Đối với ứng dụng nghiêm túc:

1. Xác định root working directory hoặc cấu hình path rõ ràng.
2. Dùng `Path.resolve` để ghép đúng parent/child.
3. Kiểm soát `normalize()`/canonicalization và symlink theo threat model.
4. Không dùng `Files.exists` để quyết định quyền truy cập thực tế mà không xử lý lỗi khi mở.
5. Nếu filename đến từ người dùng, chống path traversal bằng cơ chế truy cập filesystem phù hợp, không chỉ kiểm tra có tồn tại.

### `SecurityException` và lỗi I/O khác nhau

Theo môi trường và provider, lời gọi `Files.exists`/`notExists` có thể chịu kiểm tra quyền bảo mật và **ném `SecurityException`** thay vì chỉ trả `false`. Không nên xem việc không xuất hiện checked `IOException` trên chữ ký là bảo đảm “không có lỗi”.

Nếu nghiệp vụ yêu cầu log nguyên nhân chính xác, gọi một API có exception thích hợp (ví dụ `readAttributes` hoặc thao tác mở) và phân loại lỗi theo semantics của filesystem thay vì lặp lại `Files.exists` cho đến khi có true.

## 14. Lỗi phổ biến và cách sửa

| Cách làm sai | Vấn đề | Cách làm đúng hơn |
|---|---|---|
| `!Files.exists(p)` nghĩa là file không tồn tại chắc chắn | UNKNOWN cũng tạo `false` từ `exists` | `Files.notExists(p)` khi cần chứng nhận vắng mặt |
| `Files.exists(p) || Files.notExists(p)` luôn true | Cả hai có thể false | Xử lý nhánh UNKNOWN |
| Gặp UNKNOWN thì tạo file như chắc chắn trống | Có thể đã tồn tại nhưng thiếu quyền xác minh | Dùng `CREATE_NEW` hoặc thao tác nguyên tử đúng yêu cầu |
| Kiểm tra `notExists` rồi ghi bằng open mặc định | Có race và nguy cơ truncate file vừa được tạo | Tạo bằng `Files.createFile` hoặc `CREATE_NEW` |
| Kiểm tra `exists` rồi mới xóa | Thêm TOCTOU và thao tác thừa | Dùng `deleteIfExists` theo chính sách |
| Chỉ kiểm tra tồn tại rồi đọc mà không catch lỗi | File có thể thay đổi/không có quyền đọc | Thử mở/đọc trực tiếp và xử lý `IOException` |
| `exists == true` ⇒ file thông thường | Directory/link cũng tồn tại | Dùng `isRegularFile` hoặc attributes |
| `exists == false` ⇒ `notExists == true` | Không đúng khi chưa xác minh được | Giữ ba trạng thái riêng |
| Dùng `NOFOLLOW_LINKS` nhưng nghĩ đang kiểm tra target | Nó kiểm tra chính entry link | Chọn link policy rõ ràng |
| Chỉ dùng follow links cho dangling symlink và kết luận entry mất | Target mất nhưng link vẫn tồn tại | Dùng `NOFOLLOW_LINKS` khi hỏi về entry |
| Nhầm mọi lỗi truy vấn với `NoSuchFileException` | Lỗi quyền/I/O và provider khác nhau | Bắt subtype `IOException` phù hợp |
| Test UNKNOWN bằng chmod rồi yêu cầu output cố định | Hành vi quyền khác nhau theo OS/user | Fake abstraction hoặc integration test có điều kiện |
| Quên trạng thái thay đổi giữa hai lời gọi | Snapshot không nguyên tử | Tránh kiểm tra trước khi thao tác khi có thể |
| Đưa đường dẫn tương đối vào service mà không kiểm tra root | Working directory thay đổi | Quy định root/path được cấu hình |
| Dùng kết quả exists như kiểm tra bảo mật | Không giải quyết traversal/symlink/TOCTOU | Dùng cơ chế OS và API mở phù hợp |
| Tin `Files.exists` kiểm tra checksum/nội dung | Chỉ liên quan xác minh sự tồn tại | Mở, đọc và validate nội dung thật |

## 15. Quy trình quyết định theo bài toán

### A. Chỉ cần thông báo trạng thái trong UI

Có thể dùng:

```java
if (Files.exists(p)) {
    status = "Present";
} else if (Files.notExists(p)) {
    status = "Absent";
} else {
    status = "Unknown";
}
```

Đây là **đoạn minh họa**, `p` là `Path`, `status` là biến kiểu `String`. UI cần giữ khả năng hiển thị **Unknown**. Không gắn nhãn đỏ “missing” cho mọi `false`.

### B. Cần tạo file mới nếu chưa có, tuyệt đối không ghi đè

Ưu tiên thao tác `Files.createFile(p)` hoặc mở với `StandardOpenOption.CREATE_NEW`. Bắt `FileAlreadyExistsException` tách biệt các lỗi khác. **Không** đặt `notExists` làm điều kiện duy nhất.

### C. Cần đọc file nếu có, còn thiếu thì bỏ qua

Thử mở và đọc trực tiếp:

```java
try (var in = Files.newInputStream(p)) {
    // Đọc dữ liệu.
} catch (NoSuchFileException ex) {
    // Theo chính sách ứng dụng, bỏ qua nếu file không tồn tại.
} catch (IOException ex) {
    // Lỗi quyền, filesystem, I/O khác: báo hoặc xử lý phù hợp.
}
```

Đây là **đoạn minh họa**, cần import `Files`, `NoSuchFileException`, `IOException`. Tùy provider, không phải mọi trường hợp mất file đều dùng chính xác subtype này; hãy tham chiếu hợp đồng I/O đang dùng. Không nuốt toàn bộ `IOException` để biến thành “file missing”.

### D. Cần phân tích symlink hoặc kiểm tra entry để dọn

Dùng `Files.exists(path, LinkOption.NOFOLLOW_LINKS)` và/hoặc `Files.readAttributes(..., NOFOLLOW_LINKS)` nếu cần metadata. Nhưng khi thao tác entry thật, vẫn xử lý race/permission và lỗi của `delete`/`move`.

### E. Cần kiểm kê nhiều file trên storage từ xa

Một lần `exists` cho mỗi file có thể tốn round-trip và gây inconsistent snapshot. Cân nhắc API liệt kê/batch chuyên dụng của provider, thiết kế retry/backoff và biểu diễn trạng thái **unknown** rõ ràng khi không truy cập được. Không xem “không phản hồi” là “không tồn tại”.

## 16. Bài tập tự kiểm tra

**Câu 1.** Nếu `Files.exists(p) == false`, file có chắc chắn không tồn tại không?

**Đáp án:** Không. Kết quả có thể là ABSENT hoặc UNKNOWN. Phải hiểu `Files.notExists(p)` là kiểm tra xác nhận không tồn tại.

**Câu 2.** Khi nào cả `Files.exists(p)` và `Files.notExists(p)` cùng trả `false`?

**Đáp án:** Khi provider không xác minh được trạng thái; ví dụ một số lỗi quyền, metadata hoặc filesystem. Đây là UNKNOWN, không phải mâu thuẫn logic của API.

**Câu 3.** `Files.notExists(p)` có luôn bằng `!Files.exists(p)` không?

**Đáp án:** Không. Trong trạng thái UNKNOWN, `notExists` là `false` còn `!exists` là `true`.

**Câu 4.** Vì sao không nên viết `if (Files.notExists(p)) Files.writeString(p, data)` để bảo đảm không ghi đè?

**Đáp án:** Có TOCTOU: file có thể được tạo giữa hai lệnh. Dùng `CREATE_NEW` hoặc `Files.createFile` để yêu cầu tạo mới không ghi đè.

**Câu 5.** Có symbolic link vẫn còn trên đĩa nhưng target bị xóa. Mặc định `exists(link)` hỏi điều gì?

**Đáp án:** Mặc định theo link, nên hỏi target được phân giải; có thể là false khi target mất. Nếu muốn hỏi chính entry symbolic link, dùng `NOFOLLOW_LINKS`.

**Câu 6.** `Files.exists(path) == true` có bảo đảm mở được file để đọc?

**Đáp án:** Không. Path có thể là directory, thiếu quyền đọc, hoặc bị thay đổi sau kiểm tra. Mở/đọc và xử lý lỗi trực tiếp.

**Câu 7.** Nếu ứng dụng cần phân biệt không tồn tại với access denied, nên chọn gì?

**Đáp án:** API truy vấn metadata hoặc thao tác thực với `IOException`/subtype (ví dụ `Files.readAttributes`), thay vì chỉ dựa trên boolean `exists`.

**Câu 8.** `Files.deleteIfExists(p)` giải quyết điều gì?

**Đáp án:** Gộp mục tiêu xóa nếu có vào một thao tác, tránh vòng check-then-delete không cần thiết. Nó vẫn có thể ném lỗi; không tạo bảo đảm vĩnh viễn về trạng thái.

**Câu 9.** Tại sao các kết quả `exists` và `notExists` đọc liên tiếp không phải ảnh chụp nguyên tử?

**Đáp án:** Filesystem có thể thay đổi giữa hai lời gọi; các kết quả được quan sát tại những thời điểm khác nhau.

**Câu 10.** `NOFOLLOW_LINKS` có tự bảo đảm chống path traversal và symlink race không?

**Đáp án:** Không. Nó chỉ thay cách kiểm tra đối tượng; an toàn cần thiết kế root, quyền và API thao tác trực tiếp phù hợp.

## Tổng kết

- `Files.exists(p)` **xác nhận sự tồn tại** nếu có thể.
- `Files.notExists(p)` **xác nhận sự không tồn tại** nếu có thể.
- Nếu **cả hai false**, trạng thái là **UNKNOWN**, không được ép thành ABSENT.
- Hai phép kiểm tra **không nguyên tử**, không thay thế thao tác mở/tạo/xóa thực tế và không tự bảo đảm quyền, nội dung hoặc độ bền dữ liệu.
- Với symlink, phân biệt **theo link** và **`NOFOLLOW_LINKS`**; một dangling symlink có thể tồn tại dù target không tồn tại.
- Nếu cần lỗi có ngữ cảnh, chọn API trả `IOException` và xử lý đúng subtype; nếu cần tạo file không ghi đè, dùng `CREATE_NEW` hoặc `Files.createFile`.
- Khi viết ứng dụng Java 17, hãy biểu diễn **PRESENT / ABSENT / UNKNOWN** minh bạch và luôn kiểm soát TOCTOU, ownership, filesystem provider cùng chính sách bảo mật.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-operations-009">Không kiểm tra trước rồi mặc định thao tác sau sẽ thành công</a>

<details>
<summary>Xem chi tiết</summary>

# Không kiểm tra trước rồi mặc định thao tác sau sẽ thành công trong Java I/O

## 1. Vấn đề: kiểm tra đúng ở thời điểm này không bảo đảm đúng ở thời điểm sau

Khi thao tác với file, một cách viết dễ gặp là kiểm tra điều kiện trước rồi **mặc định rằng điều kiện đó vẫn còn đúng** khi thực hiện hành động:

~~~java
// KHÔNG AN TOÀN nếu coi kết quả exists() là sự bảo đảm.
if (Files.exists(path)) {
    String content = Files.readString(path);
    process(content);
}
~~~

Tại thời điểm `Files.exists(path)` chạy, file có thể đang tồn tại. Nhưng giữa lúc kiểm tra và `Files.readString(path)`, một tiến trình khác, thread khác, dịch vụ đồng bộ, người dùng hoặc chính mã của chương trình có thể **xóa, đổi tên, thay thế hoặc thay đổi quyền truy cập**. Kết quả là thao tác đọc vẫn có thể thất bại.

~~~text
Thời điểm       Ứng dụng A                     Tác nhân B
----------      ---------------------------    -----------------------
T1              Files.exists(path) == true
T2                                             Files.delete(path)
T3              Files.readString(path)
                -> IOException
~~~

Đây là dạng lỗi **TOCTOU — Time Of Check To Time Of Use** (chênh lệch giữa thời điểm kiểm tra và thời điểm sử dụng). TOCTOU không chỉ xuất hiện ở file: socket, database record, lock, giao dịch và các tài nguyên dùng chung cũng có tình huống tương tự.

**Nguyên tắc học tập:** Thao tác I/O **phải được kiểm tra ngay tại lúc thực hiện** thông qua kết quả trả về và ngoại lệ; một phép kiểm tra trước chỉ cung cấp thông tin tức thời, **không phải lời bảo đảm về tương lai**.

Không phải cứ gọi `Files.exists()` là sai. Sai là **dựa hoàn toàn vào kết quả đó để bỏ qua xử lý lỗi của thao tác tiếp theo**, hoặc dùng check-then-act để giả lập một thao tác vốn cần tính nguyên tử.

## 2. Những phép kiểm tra thường bị hiểu nhầm

| Phép kiểm tra | Thông tin nó cung cấp | Điều nó KHÔNG bảo đảm |
| --- | --- | --- |
| `Files.exists(path)` | Path có vẻ tồn tại tại thời điểm kiểm tra | Lần open/read/delete/rename sau sẽ thành công |
| `Files.notExists(path)` | Có cơ sở xác định path không tồn tại | Sau đó không ai tạo path |
| `Files.isReadable(path)` | Đánh giá khả năng đọc tại thời điểm kiểm tra | Lần đọc sau không gặp lỗi quyền, khóa, race hoặc I/O |
| `Files.isWritable(path)` | Đánh giá khả năng ghi tại thời điểm kiểm tra | Lần ghi sau sẽ thành công và đủ dung lượng |
| `Files.isRegularFile(path)` | Nhận biết kiểu mục tiêu tại thời điểm kiểm tra | Mục tiêu không bị thay bằng directory hoặc symlink |
| `Files.isDirectory(path)` | Nhận biết directory ở thời điểm kiểm tra | Lần mở thư mục/duyệt con sau sẽ thành công |
| `Files.size(path)` | Kích thước file quan sát tại thời điểm gọi | Kích thước không đổi trong khi đọc |
| `Files.getLastModifiedTime(path)` | Metadata thời gian sửa đổi hiện tại | Nội dung không thay đổi; metadata không phải khóa |

**Trường hợp đặc biệt của `exists()` và `notExists()`:** Java cho phép **cả hai cùng trả false** nếu không thể xác định trạng thái do quyền truy cập hoặc lỗi I/O. Không suy ra `!Files.exists(path)` tương đương `Files.notExists(path)` trong mọi tình huống.

Các phép kiểm tra trên có thể hữu ích để **hiển thị giao diện** hoặc **cung cấp chẩn đoán sơ bộ**, nhưng không thể thay thế `try/catch` quanh thao tác thực sự.

## 3. Ví dụ Java 17+: file biến mất sau phép kiểm tra

Tạo file **CheckThenActDemo.java** và lưu mã nguồn bằng UTF-8:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public class CheckThenActDemo {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("toctou-demo-");
        Path target = directory.resolve("notes.txt");

        try {
            Files.writeString(
                    target, "hello", StandardCharsets.UTF_8);

            boolean wasThere = Files.exists(target);
            System.out.println("checkedExists=" + wasThere);

            // Mô phỏng một tác nhân khác thay đổi file
            // giữa bước kiểm tra và bước sử dụng.
            Files.delete(target);

            try {
                String text = Files.readString(
                        target, StandardCharsets.UTF_8);
                System.out.println("text=" + text);
            } catch (NoSuchFileException expected) {
                System.out.println("readAfterChange=missing");
            }
        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(directory);
        }
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 CheckThenActDemo.java
java CheckThenActDemo
~~~

Stdout:

~~~text
checkedExists=true
readAfterChange=missing
~~~

Ở đây việc xóa file **được thực hiện có chủ ý** giữa hai lệnh để tái hiện race theo cách dễ quan sát; ví dụ **không cần tạo thread**. Trong ứng dụng thực tế, hành động chen ngang có thể đến từ thread hoặc process khác và xảy ra bất kỳ lúc nào.

Chương trình chứng minh một điều: **`exists() == true` và `readString()` thất bại có thể cùng đúng**, vì chúng đánh giá hai thời điểm khác nhau. Việc xử lý `NoSuchFileException` nằm ở thao tác đọc mới là xử lý lỗi đúng.

Đoạn `finally` dùng `deleteIfExists()` để dọn file do chương trình tạo; nếu file đã bị xóa thì hàm có thể trả false mà không coi đó là lỗi. Việc dọn dẹp directory được thực hiện sau khi dọn file.

## 4. Cách đọc đúng: thực hiện thao tác rồi xử lý kết quả

Khi mục tiêu là **đọc file nếu có**, nhiều trường hợp không cần hỏi file có tồn tại trước hay không. Hãy trực tiếp gọi hàm đọc:

~~~java
// Mảnh code: path là Path, các import tương ứng đã có.
try {
    String text = Files.readString(path, StandardCharsets.UTF_8);
    use(text);
} catch (NoSuchFileException missing) {
    // Đối tượng không còn tồn tại lúc mở/đọc.
    reportMissing(path);
} catch (java.nio.file.AccessDeniedException denied) {
    // Không được phép truy cập.
    reportDenied(path);
} catch (IOException failure) {
    // Các lỗi I/O còn lại: filesystem, thiết bị, đọc lỗi...
    reportIoFailure(failure);
}
~~~

`use`, `reportMissing`, `reportDenied` và `reportIoFailure` ở trên là các hàm **do ứng dụng cung cấp**; đây là **mảnh code minh họa**, không phải chương trình chạy độc lập.

Ưu điểm:

1. Không tạo một giả định sai rằng kiểm tra ban đầu đảm bảo thao tác.
2. Bắt được các lỗi phát sinh **ngay tại bước làm việc**.
3. Phân biệt file không còn, quyền bị từ chối và các lỗi I/O khác khi provider cung cấp exception cụ thể.
4. Giảm một lần truy vấn filesystem có thể không cần thiết.

**Không đảm bảo mọi hệ điều hành và provider sẽ luôn ném cùng một lớp ngoại lệ cụ thể.** `NoSuchFileException` và `AccessDeniedException` là các trường hợp phổ biến, nhưng ứng dụng vẫn nên có nhánh `IOException` tổng quát.

**Đối với file tùy chọn**, “file không tồn tại” có thể là tình huống nghiệp vụ bình thường (ví dụ chưa có cấu hình riêng). Đối với file bắt buộc, cùng tình huống ấy là lỗi cần báo cho người dùng. Cách xử lý tùy hợp đồng ứng dụng, không tùy việc có hay không có phép `exists()`.

## 5. Check-then-create: nguy cơ khi tạo file mới

Một mẫu dễ gây race:

~~~java
// SAI nếu cần bảo đảm không ghi đè file đã có.
if (Files.notExists(target)) {
    Files.writeString(target, "new content");
}
~~~

Sau khi `notExists()` trả true, tác nhân khác có thể tạo target ngay lập tức. Nếu `Files.writeString` dùng các tùy chọn mặc định, thao tác đó có thể **tạo file nếu thiếu hoặc truncate file đã tồn tại**. Như vậy chương trình có thể ghi đè dữ liệu của người khác trái với mục tiêu “chỉ tạo khi chưa có”.

Mục tiêu đúng ở đây là một **thao tác tạo có điều kiện** do filesystem thực hiện như một đơn vị, không phải hai thao tác kiểm tra + ghi tách rời.

Dùng **`StandardOpenOption.CREATE_NEW`** khi tạo file và ghi nội dung:

~~~java
Files.writeString(
        target,
        "new content",
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE);
~~~

Theo hợp đồng Files, với `CREATE_NEW`, **kiểm tra tồn tại và tạo file mới được thực hiện nguyên tử đối với các thao tác filesystem khác**. Nếu đã có file (kể cả symbolic link tại vị trí đích), thao tác thất bại; trên default provider thông thường đó là `FileAlreadyExistsException`.

Lưu ý: **nguyên tử ở bước tạo file** không có nghĩa việc ghi toàn bộ nội dung ngay sau đó cũng là một transaction nguyên tử. Nếu ghi lỗi giữa chừng, file mới tạo có thể chỉ chứa nội dung một phần. Đừng nhầm “không tạo trùng” với “nội dung được commit trọn vẹn”.

### Ví dụ Java 17+: CREATE_NEW ngăn lần tạo thứ hai

Tạo file **CreateNewDemo.java**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class CreateNewDemo {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("create-new-demo-");
        Path target = directory.resolve("result.txt");

        try {
            Files.writeString(
                    target,
                    "first",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            System.out.println("firstCreate=ok");

            try {
                Files.writeString(
                        target,
                        "second",
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
                System.out.println("secondCreate=ok");
            } catch (FileAlreadyExistsException expected) {
                System.out.println("secondCreate=already-exists");
            }

            System.out.println("content=" + Files.readString(
                    target, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(directory);
        }
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 CreateNewDemo.java
java CreateNewDemo
~~~

Stdout:

~~~text
firstCreate=ok
secondCreate=already-exists
content=first
~~~

Ở lần đầu, file chưa tồn tại nên tạo thành công. Ở lần hai, `CREATE_NEW` phát hiện đích đã có và từ chối thao tác. Nội dung **vẫn là first**, không bị lần gọi thứ hai ghi đè. Thay vì hỏi trước `notExists`, mã chọn hành vi “chỉ được tạo mới” ở **chính lệnh ghi**, nơi nó có hiệu lực.

Cũng có thể dùng `Files.createFile(target)` để tạo file rỗng mới. Theo hợp đồng, thao tác kiểm tra-và-tạo của `createFile` là nguyên tử đối với các thao tác filesystem khác. Tuy vậy, sau `createFile` rồi mở luồng khác để ghi vẫn là **hai bước riêng**: nếu quan tâm cả độ an toàn của nội dung, hãy cân nhắc mở ngay bằng `CREATE_NEW` và giữ handle thích hợp.

## 6. Check-then-delete và deleteIfExists

Mẫu sau cũng không có bảo đảm:

~~~java
// Không cần thiết và vẫn có race.
if (Files.exists(path)) {
    Files.delete(path);
}
~~~

Tác nhân khác có thể xóa path giữa `exists` và `delete`. Lúc đó `Files.delete(path)` có thể ném `NoSuchFileException`.

Nếu yêu cầu nghiệp vụ là “xóa nếu có; nếu không có thì bỏ qua”, dùng trực tiếp:

~~~java
boolean deleted = Files.deleteIfExists(path);
System.out.println("deleted=" + deleted);
~~~

- `true`: thao tác đã xóa một đối tượng.
- `false`: thao tác không xóa vì đối tượng không tồn tại.
- Ngoài ra, thao tác vẫn có thể ném `IOException` do quyền truy cập, thư mục không rỗng, lỗi I/O hoặc những điều kiện khác.

**`deleteIfExists` không bảo đảm xóa thành công**, và cũng không làm các bước nghiệp vụ trước/sau thành một transaction. Nó chỉ gộp logic “nếu tồn tại thì xóa” vào một API thuận tiện và hạn chế một khoảng check-then-act không cần thiết.

Nếu ứng dụng **bắt buộc phải xóa một file cụ thể đã kiểm tra trước đó**, việc gọi `deleteIfExists(path)` chưa giải quyết yêu cầu định danh đó. Đường dẫn có thể đã bị thay bằng đối tượng khác. Bài toán chống race với đối tượng cụ thể cần cơ chế handle/file identity và quyền kiểm soát filesystem phù hợp.

## 7. Check-then-write, copy và move

Không nên viết:

~~~java
// SAI nếu mục đích là chắc chắn không ghi đè file khác.
if (!Files.exists(destination)) {
    Files.copy(source, destination);
}
~~~

Giữa lúc kiểm tra và copy, đích có thể xuất hiện. Hãy quyết định **chính sách tại thao tác**:

- **Không ghi đè đích có sẵn:** gọi `Files.copy(source, destination)` không kèm `REPLACE_EXISTING` rồi xử lý trường hợp file đích đã tồn tại.
- **Chấp nhận thay thế đích:** cân nhắc `StandardCopyOption.REPLACE_EXISTING`, hiểu rằng kết quả còn phụ thuộc filesystem, quyền và các lỗi có thể xảy ra.
- **Di chuyển file:** dùng `Files.move` với tùy chọn phù hợp; nếu cần đổi tên theo cơ chế nguyên tử, cân nhắc `StandardCopyOption.ATOMIC_MOVE` nhưng phải chuẩn bị xử lý `AtomicMoveNotSupportedException`.

Các ví dụ:

~~~java
// Không cho phép copy đè: ứng dụng xử lý IOException nếu đích có.
Files.copy(source, destination);
~~~

~~~java
// Chấp nhận thay thế theo hợp đồng của Files.copy.
Files.copy(
        source, destination,
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
~~~

~~~java
// Yêu cầu move nguyên tử; có provider/filesystem không hỗ trợ.
Files.move(
        source, destination,
        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
~~~

Những đoạn trên là **mảnh code** trong phương thức đã có Path và xử lý IOException. Lưu ý quan trọng:

1. Các thao tác `copy` và `move` có thể thất bại bởi quyền truy cập, file nguồn mất, đích xuất hiện, cross-filesystem hoặc các lỗi khác.
2. **`Files.copy` không bảo đảm nguyên tử**; nếu xảy ra lỗi I/O, một phần file đích có thể đã được tạo hoặc ghi.
3. `ATOMIC_MOVE` là yêu cầu hỗ trợ từ filesystem/provider; nó có thể bị từ chối bằng `AtomicMoveNotSupportedException`. Không tự coi “move” hay “replace existing” là giao dịch nguyên tử.
4. Với `ATOMIC_MOVE`, ngữ nghĩa khi **target đã tồn tại** có những chi tiết phụ thuộc implementation theo đặc tả; phải xác định hợp đồng ứng dụng và hành vi provider. Không kết hợp tùy chọn rồi suy rằng mọi platform đều thay thế giống nhau.

**Quy tắc tổng quát:** tránh kiểm tra rồi làm như thể hai bước là một giao dịch; thay vào đó chọn một thao tác có điều kiện/tùy chọn thích hợp và **luôn xử lý kết quả của chính thao tác đó**.

## 8. Check-then-mkdir và createDirectories

Một ví dụ phổ biến:

~~~java
if (!Files.exists(directory)) {
    Files.createDirectory(directory);
}
~~~

Tác nhân khác có thể tạo directory ngay sau check, gây `FileAlreadyExistsException`. Nếu yêu cầu là “bảo đảm toàn bộ chuỗi thư mục cha tồn tại khi thao tác có thể”, thường dùng:

~~~java
Files.createDirectories(directory);
~~~

`createDirectories` có thể tạo các thành phần thư mục chưa có và **không thất bại chỉ vì thư mục mục tiêu đã tồn tại dưới dạng directory**. Nhưng nó vẫn có thể ném IOException nếu một thành phần là file thường, không có quyền, có lỗi hệ thống, hoặc nếu môi trường thay đổi trong khi tạo.

**Không suy ra createDirectories là một transaction nguyên tử.** Nếu việc tạo một loạt thành phần thất bại, một số thư mục cha trước đó có thể đã được tạo thành công. Ứng dụng cần xem xét cleanup của những tài nguyên/side effect do chính mình sở hữu nếu cần rollback.

## 9. Quyền truy cập và kiểm tra metadata không thay thế thao tác mở

Mẫu:

~~~java
if (Files.isReadable(path)) {
    return Files.readString(path);
}
return "";
~~~

có hai vấn đề:

1. `isReadable` chỉ mô tả trạng thái **tại thời điểm kiểm tra**; quyền có thể bị thay đổi hoặc đối tượng bị đổi sau đó.
2. Nếu `isReadable` trả false, có thể do không kiểm tra được hoặc điều kiện đặc thù provider; trả chuỗi rỗng sẽ **che mất lý do thất bại** và khiến ứng dụng tưởng file có nội dung rỗng.

Tốt hơn:

~~~java
try {
    return Files.readString(path, StandardCharsets.UTF_8);
} catch (IOException failure) {
    // Quyết định nghiệp vụ: báo lỗi, fallback hoặc thử lại có giới hạn.
    throw failure;
}
~~~

Tương tự, `Files.isWritable` không chứng minh còn đủ dung lượng lưu trữ, không bị quota, file không bị khóa và ghi thành công. `Files.size` không bảo đảm file vẫn giữ kích thước đó trong lúc đọc. Để đọc một snapshot nhất quán, cần thiết kế giao dịch/snapshot/locking thích hợp với filesystem và tác nhân ghi.

**File locking cũng không phải giải pháp phổ quát:** mức độ bắt buộc, phạm vi, tương tác với phần mềm khác và tính portable phụ thuộc filesystem cùng hệ điều hành. Không thay thế logic xử lý exception bằng niềm tin rằng kiểm tra quyền hoặc lock một lần sẽ giải quyết mọi race.

## 10. Symbolic link làm check-then-use nguy hiểm hơn

Giả sử ứng dụng muốn đọc file dưới một thư mục cho phép. Nó kiểm tra:

~~~text
/base/uploads/report.txt
~~~

Nếu attacker hoặc tiến trình khác có quyền sửa cây thư mục, chúng có thể thay file hay một thành phần cha bằng **symbolic link** trỏ sang nơi khác giữa bước xác minh và mở. Một phép kiểm tra bằng đường dẫn **không cố định danh tính của đối tượng sẽ được mở sau đó**.

Các khái niệm liên quan:

- **`Path.normalize()`** chỉ rút gọn các thành phần `.` và `..` theo cú pháp, không truy vấn filesystem.
- **`Path.toRealPath()`** có thể phân giải một đường dẫn tồn tại tại thời điểm gọi, nhưng vẫn không “khóa” kết quả khỏi thay đổi về sau.
- **`LinkOption.NOFOLLOW_LINKS`** ảnh hưởng một số thao tác theo cách đặc tả, nhưng một lần kiểm tra không tự bảo đảm tất cả thành phần của đường dẫn không bị thay giữa check/use.
- **`SecureDirectoryStream`** cung cấp một số thao tác tương đối so với một directory stream mở, có thể giảm rủi ro race trong những trường hợp provider hỗ trợ; không phải mọi filesystem đều cung cấp API này.

Với đường dẫn không đáng tin cậy, chỉ kiểm tra `resolved.normalize().startsWith(base)` là **chưa đủ** nếu có symbolic link và race. Thiết kế an toàn hơn cần quản lý danh tính thư mục, khả năng theo symlink, quyền sửa thư mục và cách mở file, chứ không chỉ so sánh String hay Path trước đó.

Nếu ứng dụng không hoạt động trong mô hình có đối tượng đối kháng, việc phân biệt vẫn hữu ích: kể cả không ai cố tình tấn công, các tiến trình đồng bộ file hoặc người dùng thao tác đồng thời cũng có thể gây race.

## 11. Quy tắc mở rồi dùng: handle vẫn có thể lỗi

Có một khác biệt quan trọng:

- Khi **mở** một file qua `Files.newInputStream` hoặc `Files.newByteChannel`, ta có một đối tượng stream/channel gắn với việc mở được chấp nhận tại thời điểm đó.
- Nhưng **không có gì bảo đảm mọi lần read/write/flush/close về sau đều thành công**. Thiết bị có thể gặp lỗi, mạng hoặc bộ nhớ ngoài có thể biến mất, filesystem hết dung lượng, stream bị đóng hoặc tài nguyên khác thay đổi.
- Bởi vậy cần **try-with-resources** để đóng và xử lý exception ở cả lúc mở, đọc/ghi, và đóng.

Ví dụ đọc theo luồng:

~~~java
// Mảnh code: input là Path, output là OutputStream hợp lệ.
try (var stream = Files.newInputStream(input)) {
    stream.transferTo(output);
}
~~~

`transferTo` có thể ném IOException trong khi đang copy. TWR vẫn cố đóng stream. Không cho rằng “mở stream thành công” đồng nghĩa đọc trọn vẹn và đóng sẽ luôn thành công.

**Không dùng `Files.exists` như cách đảm bảo tính nguyên tử của toàn bộ quy trình.** Một tiến trình muốn ghi output hoàn chỉnh có thể dùng mô hình tạo file tạm, ghi/flush/đóng, rồi publish bằng move theo semantics hỗ trợ; nhưng phải xử lý lỗi và dọn file tạm. Giữa các bước vẫn cần thiết kế rõ ràng, không thể giả định mọi phần được rollback tự động.

## 12. Không kiểm tra trước nghĩa là không được xác minh dữ liệu?

Không. Bài học này **không khuyên bỏ kiểm tra dữ liệu đầu vào**. Hãy phân biệt:

**Kiểm tra hợp đồng đầu vào** (nên làm):

- Người dùng đã cung cấp tên file hay chưa?
- Extension/định dạng có được cho phép theo nghiệp vụ không?
- Dung lượng tối đa của payload sẽ chấp nhận là bao nhiêu?
- Đường dẫn có vượt chính sách bảo mật không?
- Dữ liệu đã đọc có đúng schema, checksum, magic bytes hoặc version không?

**Kiểm tra trạng thái động rồi giả định không thay đổi** (không nên):

- `exists` hôm nay, đọc chắc chắn được ở dòng kế tiếp.
- `notExists` trước khi ghi, nên tuyệt đối không đè file khác.
- `isWritable` trước khi export, nên chắc chắn file sẽ được ghi đủ.
- `size` trước khi cấp phát, nên file không bao giờ thay đổi khi đọc.
- `isRegularFile` trước khi open, nên không thể bị thay bằng đối tượng khác.

Ứng dụng có thể **vừa xác minh đầu vào vừa bắt lỗi thao tác thực tế**. Với security boundary, nên dùng thao tác dựa trên handle hoặc cơ chế được thiết kế tránh race, thay vì dựa duy nhất vào một phép kiểm tra trạng thái filesystem.

## 13. Cần retry khi file biến mất hoặc xuất hiện không?

Không phải mọi IOException đều nên thử lại:

| Tình huống | Hướng xử lý |
| --- | --- |
| File tùy chọn chưa tồn tại | Có thể fallback sang cấu hình mặc định, nếu nghiệp vụ cho phép |
| File bắt buộc không tồn tại | Báo rõ lỗi hoặc yêu cầu cung cấp đường dẫn đúng |
| Tạo file với CREATE_NEW gặp FileAlreadyExistsException | Chọn tên khác hoặc báo xung đột, không tự ghi đè |
| Lỗi quyền AccessDeniedException | Thường báo lỗi quyền/cấu hình, không retry vô hạn |
| Lỗi I/O tạm thời theo giao thức có quy định | Có thể retry có giới hạn, delay/backoff và kiểm tra trạng thái |
| Việc ghi đã có thể hoàn thành một phần | Phải biết thao tác có idempotent hay không trước khi retry |
| Move/copy thất bại giữa chừng | Kiểm tra trạng thái output thực tế, xử lý bản dở dang theo contract |

Một retry không kiểm soát có thể ghi đè dữ liệu, tạo nhiều file, lặp vô tận hoặc tạo thêm race. Nếu cần retry, chọn **thao tác idempotent** hoặc dùng tên/ID riêng và protocol an toàn. Đặc biệt với ghi dữ liệu, hãy xem xét khả năng **đã ghi một phần** trước khi thực hiện lại.

## 14. Các mẫu đúng và sai đặt cạnh nhau

| Mục tiêu | Mẫu dễ sai | Cách diễn đạt ý định tốt hơn |
| --- | --- | --- |
| Đọc file có thể vắng mặt | `if (exists) read` rồi bỏ xử lý lỗi | `readString` trực tiếp, bắt NoSuchFileException/IOException |
| Chỉ tạo nếu chưa có | `if (notExists) write` | `CREATE_NEW` hoặc `createFile` |
| Xóa nếu có | `if (exists) delete` | `deleteIfExists` và xử lý IOException |
| Tạo cây thư mục | `if (!exists) createDirectory` | `createDirectories` và xử lý IOException |
| Copy không ghi đè | `if (!exists(dest)) copy` | `Files.copy` không REPLACE_EXISTING, xử lý conflict |
| Move yêu cầu tính nguyên tử | Kiểm tra đích trước rồi move thường | Cân nhắc `ATOMIC_MOVE` và xử lý unsupported/race |
| Kiểm tra đọc được | `isReadable` rồi tin lần read chắc thành công | Mở/đọc trực tiếp, xử lý lỗi thực |
| Đọc file lớn | `size` rồi tin kích thước cố định | Đọc theo luồng, giới hạn dữ liệu, xử lý EOF/lỗi |
| Cho phép file chỉ nằm dưới base | Chỉ `normalize().startsWith(base)` | Kiểm soát symlink, quyền và thao tác mở thực tế |

**Các hàm Files API giải quyết những yêu cầu khác nhau.** Hãy chọn API theo **bất biến nghiệp vụ**: không ghi đè, thay thế có kiểm soát, chỉ đọc, xóa nếu có, tạo thư mục... thay vì kiểm tra lẻ rồi hy vọng.

## 15. Quy trình thực hành khi viết thao tác file

1. **Xác định hành vi mong muốn:** file bắt buộc hay tùy chọn? Có được tạo? Có được ghi đè? Nếu xung đột thì báo lỗi hay đổi tên?
2. **Chọn phép toán phản ánh hành vi tại bước thực hiện:** ví dụ `CREATE_NEW` cho tạo độc quyền, `deleteIfExists` cho xóa tùy chọn, `createDirectories` cho tạo cây.
3. **Đừng dùng kết quả của một phép kiểm tra trạng thái làm cam kết tương lai.** Nếu cần hiển thị “file tồn tại”, vẫn có thể gọi `exists`, nhưng thao tác dùng file phải tự xử lý lỗi.
4. **Quản lý vòng đời tài nguyên** bằng try-with-resources và xử lý lỗi trong đọc/ghi/flush/close.
5. **Định nghĩa tính nhất quán:** chấp nhận file đang thay đổi, cần snapshot, cần atomic publish hay cần lock/handle an toàn?
6. **Xử lý cả output dở dang** sau một thao tác thất bại; phân biệt cleanup resource với rollback dữ liệu.
7. **Kiểm tra thông báo lỗi và phép thử race:** file mất sau check; file xuất hiện trước create; permission thay đổi; file bị thay bằng symlink; I/O lỗi khi đang copy.

Đây là tư duy **“try the operation, handle its actual result”**. Nó không loại bỏ nhu cầu validate đầu vào mà loại bỏ giả định rằng một trạng thái vừa quan sát sẽ luôn đúng cho hành động kế tiếp.

## 16. Câu hỏi tự kiểm tra

**Câu 1.** `Files.exists(path)` trả true. Lần đọc tiếp theo có chắc chắn thành công không?  
**Đáp án:** Không. File có thể bị xóa, đổi tên, thay thế, đổi quyền hoặc gặp lỗi I/O trước/trong lúc đọc.

**Câu 2.** Vì sao `if (Files.notExists(path)) Files.writeString(path, data)` có thể ghi đè file vừa được tác nhân khác tạo?  
**Đáp án:** Check và write là hai bước riêng. Tác nhân khác có thể tạo file sau check; `writeString` mặc định có thể truncate file đã tồn tại.

**Câu 3.** Khi chỉ được tạo file mới nếu chưa tồn tại, nên chọn gì?  
**Đáp án:** `StandardOpenOption.CREATE_NEW` hoặc `Files.createFile`, rồi xử lý trường hợp đích đã tồn tại. Không thay bằng check-then-create.

**Câu 4.** Nếu `Files.exists` và `Files.notExists` cùng false thì kết luận file tồn tại hay không?  
**Đáp án:** Không thể khẳng định từ hai giá trị đó; có thể hệ thống không xác định được trạng thái file.

**Câu 5.** `Files.deleteIfExists` bảo đảm xóa mọi file thành công không?  
**Đáp án:** Không. Nó bỏ qua trường hợp đối tượng vắng mặt, nhưng vẫn có thể thất bại bởi quyền, thư mục không rỗng và lỗi I/O.

**Câu 6.** `Files.isReadable(path)` true có đủ để bỏ catch IOException quanh `Files.readString` không?  
**Đáp án:** Không. Trạng thái có thể thay đổi và lỗi vẫn xảy ra khi đọc.

**Câu 7.** `Files.copy` có mặc định là một transaction nguyên tử không?  
**Đáp án:** Không. Lỗi trong quá trình copy có thể để lại đích dở dang; cần thiết kế cleanup hoặc publish đúng yêu cầu.

**Câu 8.** `normalize().startsWith(base)` có đủ để ngăn truy cập ngoài base khi có symbolic link không?  
**Đáp án:** Chưa. Normalize là lexical, còn symlink và race cần kiểm soát theo filesystem/handle.

**Câu 9.** Nếu `CREATE_NEW` tạo file thành công rồi thao tác ghi thất bại nửa chừng, file có tự biến mất không?  
**Đáp án:** Không có bảo đảm đó. CREATE_NEW đảm bảo điều kiện tạo, không tạo transaction cho toàn bộ nội dung ghi; ứng dụng cần xử lý file dở dang.

**Câu 10.** Nếu có phép kiểm tra UI “tệp đang tồn tại” thì có nên bỏ không?  
**Đáp án:** Không nhất thiết. Thông tin tức thời hữu ích cho giao diện; chỉ không được coi nó thay thế kiểm tra kết quả và ngoại lệ tại thao tác I/O.

## 17. Tài liệu Java SE 17

- [Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html): `exists`, `notExists`, `isReadable`, `createFile`, `createDirectories`, `deleteIfExists`, `copy`, `move`.
- [StandardOpenOption](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardOpenOption.html): `CREATE_NEW`, `CREATE`, `TRUNCATE_EXISTING`, `WRITE`.
- [StandardCopyOption](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardCopyOption.html): `REPLACE_EXISTING` và `ATOMIC_MOVE`.
- [FileAlreadyExistsException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileAlreadyExistsException.html) và [NoSuchFileException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/NoSuchFileException.html): lỗi tồn tại/không tồn tại tại thời điểm thao tác.
- [SecureDirectoryStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/SecureDirectoryStream.html): thao tác thư mục có cơ chế hỗ trợ chống một số race dựa trên đường dẫn, khi provider hỗ trợ.

**Ghi nhớ:** Đối với file và tài nguyên dùng chung, **trạng thái quan sát ở thời điểm kiểm tra không bảo đảm thao tác sử dụng sau đó thành công**. Thay vì check-then-act và giả định mọi thứ giữ nguyên, hãy dùng **API thể hiện điều kiện tại chính thao tác** (chẳng hạn `CREATE_NEW`), xử lý đầy đủ ngoại lệ, đóng tài nguyên đúng lúc và chỉ dùng kiểm tra trước như thông tin bổ sung.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-operations-010">Xử lý lỗi từng bước trong các thao tác nhiều tệp</a>

<details>
<summary>Xem chi tiết</summary>

# Xử lý lỗi từng bước trong các thao tác nhiều tệp với Java NIO.2

## 1. Vấn đề: một thao tác nhiều tệp không phải một giao dịch nguyên tử

Một chức năng Java có thể cần **sao chép 50 tệp**, **đổi tên hàng loạt**, **xuất nhiều tài liệu** hoặc **di chuyển nhiều ảnh** sang thư mục đích. Nếu tệp thứ 37 gặp lỗi, điều gì xảy ra với 36 tệp đã xử lý trước đó? Nếu một thao tác chỉ hoàn tất một phần, chương trình biết trạng thái nào để khôi phục?

Các lời gọi như `Files.copy`, `Files.move`, `Files.delete` và `Files.write` vận hành với **một đối tượng đường dẫn ở mỗi lần gọi**. Lặp nhiều lời gọi **không tự biến thành một transaction**: lỗi ở bước sau không tự đảo ngược các bước trước. Một lệnh copy/move cá biệt cũng có những ràng buộc về trạng thái sau lỗi, nhất là qua filesystem khác nhau.

Ví dụ:

```text
Nguồn:     a.txt    b.txt    c.txt
             |        |        |
Bước:       COPY     COPY     COPY
             OK       OK       LỖI
Đích:      a.txt    b.txt     (thiếu)
```

Chương trình cần biết **đích đang ở trạng thái nào** và ứng dụng yêu cầu:

- **Fail-fast:** dừng ngay ở lỗi đầu tiên, thường dùng khi cần một tập đầu ra đầy đủ và nhất quán.
- **Best-effort:** tiếp tục với các tệp độc lập, ghi nhận thành công/thất bại riêng cho từng tệp.
- **Staging + publish:** chuẩn bị các tệp tạm, xác minh trước, rồi công bố kết quả; nếu lỗi thì dọn phần đã tạo khi an toàn.
- **Khôi phục sau crash:** dùng manifest/journal và quy trình phục hồi nếu cần xử lý cả trường hợp tiến trình bị tắt đột ngột.

Bài học thuộc nhóm **Thao tác tệp** của Java Core I/O. Mục tiêu là thiết kế **các bước rõ ràng, phạm vi quyền sở hữu, khả năng phục hồi, quan sát lỗi và trách nhiệm dọn dẹp** thay vì chỉ đặt một khối `try/catch` quanh toàn bộ vòng lặp.

## 2. Xác định yêu cầu trước khi viết code

Trước tiên trả lời các câu hỏi:

1. **Đầu ra phải đủ bộ hay chấp nhận thiếu tệp?** Ví dụ bản phát hành cần 100% thành phần; tác vụ copy ảnh cá nhân có thể cho phép 97/100 thành công.
2. **Đã có tệp đích chưa?** Có được ghi đè không, hay tuyệt đối phải giữ bản cũ?
3. **Có tiến trình khác truy cập/cập nhật thư mục không?** Nếu có, việc kiểm tra tồn tại trước khi ghi không chặn được race.
4. **Các tệp có liên hệ với nhau không?** Một bộ metadata + payload cần đồng nhất; nhiều bản ghi log độc lập có thể xử lý riêng.
5. **Độ lớn, số lượng và vị trí tệp?** Hàng chục tệp nhỏ trên cùng filesystem khác hàng triệu tệp lớn trải nhiều volume hoặc cloud storage.
6. **Lỗi nào có thể thử lại?** Hết quyền truy cập khác mạng gián đoạn; retry phải tính tới việc tệp đích có thể đã tồn tại một phần.
7. **Khi lỗi xảy ra, điều gì được phép xóa?** Chỉ được dọn tài nguyên do chính lần chạy **sở hữu**, không xóa dữ liệu có sẵn của người dùng.
8. **Đầu ra xuất hiện với người đọc ở thời điểm nào?** Ghi trực tiếp ra tên cuối khiến người khác có thể đọc bản chưa đầy đủ.

Đối với tác vụ nhiều tệp, cần chọn một **chính sách thành công** có thể kiểm chứng, không chỉ “vòng lặp chạy xong không ném ngoại lệ”.

### 2.1. Bảng chọn chiến lược

| Yêu cầu | Chiến lược bắt đầu | Giới hạn |
| --- | --- | --- |
| Mọi tệp phải được chuẩn bị thành công | Fail-fast khi chuẩn bị, sau đó publish | Công bố nhiều tệp vẫn không tự atomic |
| Các tệp độc lập, cho phép thất bại riêng | Best-effort và kết quả từng tệp | Người gọi phải biết bản nào thất bại |
| Không được đụng tới bản đích đang có | `CREATE_NEW` hoặc copy/move không `REPLACE_EXISTING`; kiểm tra phù hợp | Vẫn cần xử lý race, ngoại lệ |
| Giữ bản cũ nếu tạo bản mới thất bại | Staging trong thư mục riêng, xác minh rồi chuyển | Đổi nhiều tệp không có transaction chung |
| Dừng/crash giữa quy trình vẫn phải phục hồi | Manifest/journal, idempotency, recovery | Tăng độ phức tạp và cần thiết kế dữ liệu |
| File đơn lẻ cần đổi tên nguyên tử nếu có hỗ trợ | `Files.move` với `ATOMIC_MOVE` | Chỉ phạm vi từng move, provider có thể không hỗ trợ |
| Sao chép nhiều tệp lớn | Streaming, quota, giới hạn đồng thời | Không đọc hết file vào heap |
| Nhiều writer cùng cập nhật cùng thư mục | Đồng bộ giao thức hoặc khóa thích hợp | Kiểm tra `exists` trước thao tác không đủ |

## 3. Phân tách quy trình thành các giai đoạn

Một thiết kế thực dụng gồm bốn giai đoạn:

```text
PLAN / VALIDATE
  │ Kiểm tra nguồn, đích, trùng tên, quyền, quota dự kiến
  ▼
STAGE
  │ Tạo tệp tạm trong vùng mình quản lý; copy/ghi; checksum nếu cần
  ▼
VERIFY
  │ Xác minh số file, kích thước, checksum/định dạng theo yêu cầu
  ▼
PUBLISH
  │ Chuyển sang tên chính thức, cập nhật manifest/điểm công bố
  ▼
CLEANUP / RECOVERY
    Dọn tạm, tổng hợp lỗi, khôi phục khi khả thi
```

Các bước chuẩn bị giúp giảm rủi ro nhưng **không loại bỏ mọi race**. Ví dụ `Files.exists(source)` trả `true` hôm nay không đảm bảo `Files.copy(source, target)` thành công ngay sau đó; nguồn có thể bị xóa/đổi tên hoặc bị đổi quyền giữa hai lời gọi. Vì vậy **thao tác thực tế vẫn phải bắt và phân loại lỗi**.

### 3.1. Lập kế hoạch trước khi tác động filesystem

Một bản kế hoạch (plan) cho mỗi tệp có thể gồm:

| Trường | Ý nghĩa |
| --- | --- |
| Source | `Path` của nguồn |
| Destination | `Path` đích cuối, đã quyết định tên |
| Stage | `Path` tạm do tác vụ này tạo |
| Expected length/hash | Kích thước hoặc checksum nếu có hợp đồng kiểm tra |
| State | `PLANNED`, `STAGED`, `VERIFIED`, `PUBLISHED`, `FAILED`, `CLEANED` |
| Error | Loại ngoại lệ, bước xảy ra, thông điệp và nguyên nhân |
| Owned by task? | Có được phép xóa trong rollback không |

Nên **phát hiện đích trùng nhau** trong danh sách trước khi chạy; ví dụ hai source khác nhau cùng được ánh xạ thành `output/report.pdf`. Nếu không kiểm tra, nguồn đến sau có thể ghi đè kết quả của nguồn đến trước.

### 3.2. Phân biệt kiểm tra trước và bảo đảm thực sự

- `Files.exists`, `Files.isReadable`, `Files.isWritable` chỉ là **snapshot**, có thể lỗi thời ngay sau khi trả kết quả.
- `Files.createDirectories(parent)` có thể tạo thư mục cha thiếu, nhưng không đảm bảo quyền, quota hoặc tình trạng filesystem sẽ giữ nguyên.
- Với trường hợp **không được ghi đè**, hạn chế kiểm tra `if (!Files.exists(target))` rồi mở với chế độ ghi đè; có cửa sổ race. Dùng thao tác/file option thể hiện ý định không ghi đè (ví dụ `CREATE_NEW` khi tạo file mới), và xử lý `FileAlreadyExistsException`.
- Khi di chuyển, chọn các options theo hợp đồng `Files.move`; `REPLACE_EXISTING` chủ động cho phép thay thế, còn `ATOMIC_MOVE` có semantics riêng tùy provider.

## 4. Phân loại lỗi đúng với từng bước

`java.nio.file.Files` thường ném `IOException` hoặc các lớp con để mô tả lỗi tác vụ I/O. Một số tình huống:

| Loại lỗi | Tình huống thường gặp | Cách xử lý |
| --- | --- | --- |
| `NoSuchFileException` | Nguồn biến mất, đường dẫn không tồn tại | Ghi nhận tệp bị thiếu; có thể báo lỗi dữ liệu đầu vào |
| `FileAlreadyExistsException` | Đích đã tồn tại nhưng tác vụ không cho phép thay thế | Bảo toàn đích; quyết định đổi tên/từ chối |
| `AccessDeniedException` | Thiếu quyền file hoặc thư mục | Báo quyền truy cập, tránh retry mù |
| `DirectoryNotEmptyException` | Xóa thư mục vẫn có nội dung | Không xóa đệ quy ngoài phạm vi sở hữu |
| `AtomicMoveNotSupportedException` | Không hỗ trợ `ATOMIC_MOVE` ở filesystem/provider này | Theo chính sách: fail hoặc fallback có thông báo rõ |
| `FileSystemException` | Lỗi filesystem có thông tin nguồn/đích/lý do | Giữ ngữ cảnh, phân tích nguyên nhân |
| `EOFException` | Có thể gặp ở các thao tác đọc nhị phân có hợp đồng đủ byte | Không chấp nhận tệp/bản ghi cắt ngắn |
| `IOException` chung | Đầy đĩa, mất kết nối, lỗi ghi, lỗi filesystem | Xác định bước đã hoàn tất và đầu ra có thể dở |

**Không phải mọi lỗi đều là `IOException`.** `IllegalArgumentException` có thể xuất hiện khi arguments/options không hợp lệ; `SecurityException` có thể xuất hiện theo môi trường/quyền; lỗi logic cũng cần được xử lý ở tầng phù hợp. Không nên bắt `Exception` rồi in “copy failed” và bỏ qua trạng thái không rõ.

### 4.1. Ghi thông tin đủ để xử lý

Một bản ghi lỗi có giá trị gồm: **tệp nguồn, tệp đích, tên bước, loại ngoại lệ, thông báo, timestamp/request ID, số phần tử đã thành công và trạng thái cleanup**. Không log đường dẫn hoặc nội dung nhạy cảm nếu môi trường không cho phép.

Ví dụ minh họa (không phải chương trình độc lập):

```java
try {
    Files.copy(source, target);
} catch (NoSuchFileException e) {
    System.err.println("Missing input: " + source);
    throw e;
} catch (FileAlreadyExistsException e) {
    System.err.println("Destination conflict: " + target);
    throw e;
} catch (IOException e) {
    System.err.println("Copy failed: " + source + " -> " + target);
    throw e;
}
```

Ở đây `source` và `target` phải là các `Path` hợp lệ. Trật tự `catch` đặt **lớp con trước lớp cha `IOException`**; nếu bắt `IOException` đầu tiên, hai nhánh chuyên biệt phía sau sẽ không thể biên dịch vì đã bị bắt bởi kiểu cha.

## 5. Fail-fast, best-effort và kết quả trả về

### 5.1. Fail-fast

Trong mô hình fail-fast, khi một tệp thất bại, dừng các bước tiếp theo và trả thông tin lỗi:

```java
// Minh họa: sources là List<Path>, destinationDir là Path,
// và các tên nguồn phải được kiểm tra để tránh trùng đích.
for (Path source : sources) {
    Path target = destinationDir.resolve(source.getFileName());
    Files.copy(source, target);
}
```

Mã này **không rollback tự động**: nếu 3 tệp đã copy thành công rồi tệp thứ tư lỗi, 3 tệp đầu vẫn tồn tại. Fail-fast là chính sách **dừng**, không phải chính sách **hoàn tác**.

### 5.2. Best-effort

Nếu mỗi tệp độc lập, có thể bắt `IOException` **ở bên trong vòng lặp**, rồi tiếp tục. Kết quả phải ghi nhận cả thành công và thất bại thay vì chỉ in ra console; ví dụ trả danh sách `FileResult(source, destination, status, error)` cho tầng gọi.

Điều tối thiểu là không gộp một batch 95% thành công thành “thành công toàn bộ”. Hãy báo `total`, `succeeded`, `failed`, và danh sách tệp lỗi. Retry có thể thực hiện với các phần thất bại, nhưng phải bảo đảm tính **idempotency** của từng bước.

### 5.3. Hai trường hợp phù hợp với từng chiến lược

- **Xuất bộ cấu hình gồm metadata + dữ liệu:** chỉ công bố khi toàn bộ thành phần hợp lệ; staging rồi publish thường hợp lý.
- **Chuyển ảnh cá nhân độc lập:** có thể cho phép vài ảnh bị lỗi, tiếp tục các ảnh khác và trả báo cáo chi tiết.

Cả hai vẫn cần xác định khả năng làm dở **một tệp riêng lẻ** và chính sách tệp đích có sẵn.

## 6. Ví dụ Java 17+: thử từng tệp, tiếp tục khi lỗi

Chương trình sau tạo nguồn thử trong thư mục tạm. Nó copy một tệp có thật, thử copy tệp thiếu, rồi copy tệp còn lại. Mỗi lần thử có kết quả riêng; cuối cùng in tổng số thành công/thất bại. Không có file người dùng nào bị thay đổi.

Lưu thành **`BestEffortFilesDemo.java`**:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class BestEffortFilesDemo {
    static boolean copyOne(Path source, Path target) {
        try {
            Files.copy(source, target);
            System.out.println("OK: " + source.getFileName());
            return true;
        } catch (IOException failure) {
            System.out.println("FAIL: " + source.getFileName()
                    + " (" + failure.getClass().getSimpleName() + ")");
            return false;
        }
    }

    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("best-effort-");
        Path inputs = Files.createDirectory(root.resolve("inputs"));
        Path outputs = Files.createDirectory(root.resolve("outputs"));
        Path first = inputs.resolve("first.txt");
        Path missing = inputs.resolve("missing.txt");
        Path second = inputs.resolve("second.txt");

        try {
            Files.writeString(first, "one", StandardCharsets.UTF_8);
            Files.writeString(second, "two", StandardCharsets.UTF_8);

            int succeeded = 0;
            for (Path source : List.of(first, missing, second)) {
                Path target = outputs.resolve(source.getFileName());
                if (copyOne(source, target)) {
                    succeeded++;
                }
            }

            System.out.println("Succeeded: " + succeeded);
            System.out.println("Failed: " + (3 - succeeded));
        } finally {
            Files.deleteIfExists(outputs.resolve("first.txt"));
            Files.deleteIfExists(outputs.resolve("second.txt"));
            Files.deleteIfExists(first);
            Files.deleteIfExists(second);
            Files.deleteIfExists(outputs);
            Files.deleteIfExists(inputs);
            Files.deleteIfExists(root);
        }
    }
}
```

Lệnh chạy với JDK 17+:

```bash
javac -encoding UTF-8 BestEffortFilesDemo.java
java BestEffortFilesDemo
```

**Stdout trên filesystem mặc định thông thường khi các thao tác thử thành công:**

```text
OK: first.txt
FAIL: missing.txt (NoSuchFileException)
OK: second.txt
Succeeded: 2
Failed: 1
```

**Giải thích:**

1. `first.txt` tồn tại và được copy sang thư mục `outputs`.
2. `missing.txt` **cố ý không được tạo**, vì vậy `Files.copy` báo lỗi. Lớp `NoSuchFileException` là kết quả thông thường khi nguồn không có; các provider khác có thể biểu diễn lỗi khác, do đó code in **tên lớp ngoại lệ thực tế**.
3. Vòng lặp vẫn tiếp tục và copy `second.txt`.
4. Tổng kết phân biệt tệp thành công và tệp thất bại. `copyOne` không ném lại lỗi vì đây là ví dụ chính sách best-effort; trong ứng dụng thực, nên **trả/ghi nhận nguyên nhân chi tiết**, không chỉ boolean.
5. `finally` chỉ cố xóa các file và thư mục nằm trong vùng tạm do ví dụ tạo. Nếu cleanup ném lỗi, `IOException` đó có thể che ngoại lệ trước đó; ứng dụng sản xuất cần cơ chế lưu cả lỗi chính lẫn lỗi dọn dẹp.

**Giới hạn quan trọng:** `Files.copy` thông thường **không thay thế file đích đang tồn tại** nếu không có `REPLACE_EXISTING`. Với tải đồng thời, phải xử lý cả xung đột đích và kết quả copy dở. Bản demo thực hiện trên thư mục đích mới tạo, nên không có dữ liệu cũ.

## 7. Ví dụ Java 17+: staging và hoàn tác best-effort khi publish thất bại

Khi cả nhóm tệp phải cùng thuộc một lần phát hành, phương pháp hữu ích là:

1. Copy từng nguồn sang tên **tạm** trong thư mục đích riêng.
2. Chỉ khi toàn bộ bước chuẩn bị thành công mới đổi tên các file tạm thành tên cuối.
3. Nếu publish có lỗi, cố xóa những file thuộc sở hữu lần phát hành này.
4. Không xóa file có sẵn từ trước của người dùng.

Ví dụ dưới **cố tình gây lỗi sau khi publish tệp đầu tiên** để kiểm tra rollback. Nó hoạt động trong **thư mục đích mới, riêng tư và ban đầu rỗng**; nhờ vậy chương trình sở hữu mọi tên kết quả trong thư mục này.

Lưu thành **`StagedBatchFilesDemo.java`**:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class StagedBatchFilesDemo {
    static void publish(List<Path> sources, Path outputDir,
            boolean injectFailure) throws IOException {

        List<Path> staged = new ArrayList<>();
        List<Path> published = new ArrayList<>();

        try {
            // Phase 1: prepare every file in the private output directory.
            for (Path source : sources) {
                Path temp = Files.createTempFile(
                        outputDir, ".stage-", ".tmp");
                staged.add(temp);
                Files.copy(source, temp,
                        StandardCopyOption.REPLACE_EXISTING);
            }

            // Phase 2: publish under the known final filenames.
            for (int i = 0; i < staged.size(); i++) {
                Path target = outputDir.resolve(
                        "part-" + (i + 1) + ".txt");
                Files.move(staged.get(i), target);
                published.add(target);

                if (injectFailure && i == 0) {
                    throw new IOException(
                            "Simulated failure after first publish");
                }
            }
        } catch (IOException failure) {
            // Only remove files owned by this run.
            for (int i = published.size() - 1; i >= 0; i--) {
                try {
                    Files.deleteIfExists(published.get(i));
                } catch (IOException cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            for (int i = staged.size() - 1; i >= 0; i--) {
                try {
                    Files.deleteIfExists(staged.get(i));
                } catch (IOException cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            throw failure;
        }
    }

    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("batch-files-");
        Path inputs = Files.createDirectory(root.resolve("inputs"));
        Path outputs = Files.createDirectory(root.resolve("outputs"));
        Path one = inputs.resolve("one.txt");
        Path two = inputs.resolve("two.txt");

        try {
            Files.writeString(one, "alpha", StandardCharsets.UTF_8);
            Files.writeString(two, "beta", StandardCharsets.UTF_8);
            List<Path> sources = List.of(one, two);

            try {
                publish(sources, outputs, true);
            } catch (IOException failure) {
                System.out.println("First attempt: "
                        + failure.getMessage());
            }

            try (Stream<Path> files = Files.list(outputs)) {
                System.out.println("After rollback: " + files.count());
            }

            publish(sources, outputs, false);
            System.out.println("Published: "
                    + Files.readString(outputs.resolve("part-1.txt"))
                    + ","
                    + Files.readString(outputs.resolve("part-2.txt")));
        } finally {
            Files.deleteIfExists(outputs.resolve("part-2.txt"));
            Files.deleteIfExists(outputs.resolve("part-1.txt"));
            Files.deleteIfExists(one);
            Files.deleteIfExists(two);
            Files.deleteIfExists(outputs);
            Files.deleteIfExists(inputs);
            Files.deleteIfExists(root);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 StagedBatchFilesDemo.java
java StagedBatchFilesDemo
```

**Stdout dự kiến trong môi trường thử nghiệm bình thường:**

```text
First attempt: Simulated failure after first publish
After rollback: 0
Published: alpha,beta
```

**Tại sao kết quả như vậy?**

- Hai tệp gốc được copy vào hai file tạm cùng thư mục `outputs`. Tệp nguồn luôn được giữ nguyên.
- Trong lần đầu, sau khi di chuyển file tạm thứ nhất thành `part-1.txt`, chương trình ném lỗi giả lập. Khối `catch` xóa file đã publish và các file tạm chưa chuyển, nên thư mục `outputs` không còn file.
- Lần thứ hai không tạo lỗi giả lập và công bố cả hai file. `Files.readString` dùng để quan sát **các tệp thử rất nhỏ**; trong batch file lớn nên tránh đọc toàn bộ tệp chỉ để xác minh.
- Các lệnh dọn dẹp trong `catch` dùng `addSuppressed`: nếu xóa một file cũng thất bại, **lỗi gốc được giữ lại** và thông tin cleanup được đính kèm qua `Throwable.getSuppressed()`. Chương trình thực tế nên log cả những lỗi bị suppress.

**Giới hạn của mẫu này không được bỏ qua:**

1. Đây chỉ là **rollback bù trừ best-effort**, không phải một transaction nguyên tử của filesystem. Trong thời gian giữa các lần `Files.move`, người đọc khác có thể nhìn thấy một phần kết quả.
2. Khi `Files.move` thất bại theo cách không nguyên tử, trạng thái file nguồn/đích có thể không xác định hoàn toàn. Danh sách `published` chỉ ghi lại các bước **đã trả về thành công**; nếu move đang thất bại để lại một phần kết quả, chỉ dọn danh sách này có thể chưa đủ. Hệ thống sản xuất cần quét/xác minh trạng thái theo **manifest** trong vùng mà tác vụ sở hữu.
3. Mẫu **chỉ an toàn khi output directory là vùng riêng, không có file đích trước đó và không có writer ngoài thay đổi cùng các tên tệp**. Với file có sẵn, không được ghi đè rồi xóa khi rollback; cần giữ bản trước đó bằng versioning/backup và quy trình restore được thiết kế riêng.
4. `Files.createTempFile` tạo file tạm trước, nên `Files.copy(..., REPLACE_EXISTING)` chỉ thay nội dung của **file tạm do tác vụ này vừa tạo**. Không áp dụng `REPLACE_EXISTING` bừa bãi lên tên đích của người dùng.
5. `Files.move` ở đây **không được yêu cầu `ATOMIC_MOVE`**. Dù file tạm và đích cùng thư mục, không được khẳng định mọi provider làm move nguyên tử. Nếu muốn một lần rename nguyên tử, yêu cầu `ATOMIC_MOVE` với xử lý `AtomicMoveNotSupportedException` phù hợp.
6. Nếu JVM bị kill hoặc máy mất điện giữa chừng, khối `catch`/`finally` **không được bảo đảm chạy**. Cần cơ chế recovery chạy lại dựa trên manifest, trạng thái thực tế và ownership.
7. Không có checksum hay validation nội dung thực; nếu nghiệp vụ đòi tính toàn vẹn, phải kiểm tra sau khi ghi stage và trước publish.
8. Khi cleanup thất bại, tài nguyên còn lại cần được ghi nhận để có thể xử lý sau. Không được báo “rollback thành công tuyệt đối” chỉ vì đã chạy vòng xóa.

**Ý nghĩa thực hành:** staging giúp tránh công bố dữ liệu chưa chuẩn bị xong, còn danh sách trạng thái giúp biết đã làm gì và cố hoàn tác những thao tác thuộc sở hữu mình. Nhưng **việc công bố nhiều tên file vẫn có cửa sổ quan sát trạng thái không đầy đủ**.

## 8. `Files.copy`, `Files.move` và `Files.delete`: khác nhau ở trạng thái sau lỗi

### 8.1. Copy

`Files.copy(source, target, options...)` sao chép nội dung và, tùy options, một số thuộc tính. Thao tác có thể thất bại sau khi tệp đích đã được tạo hoặc đang chứa một phần dữ liệu. Với `REPLACE_EXISTING`, tệp cũ có thể đã bị thay thế; lựa chọn này cần sự cho phép rõ.

Nếu đầu ra phải nguyên vẹn, tránh copy thẳng vào tên kết quả cuối khi tác vụ chưa được kiểm tra. Dùng tên tạm trong vùng sở hữu và công bố khi đủ điều kiện.

### 8.2. Move

`Files.move` có thể dùng:

```java
// Đoạn minh họa; staged và finalPath là các Path phù hợp.
Files.move(staged, finalPath);
```

Khi muốn yêu cầu một lần move nguyên tử:

```java
Files.move(staged, finalPath, StandardCopyOption.ATOMIC_MOVE);
```

**Cần biết:**

- `ATOMIC_MOVE` có thể không được filesystem/provider hỗ trợ; khi đó có thể ném `AtomicMoveNotSupportedException`.
- Khi `ATOMIC_MOVE` được dùng, semantics về việc đích đã tồn tại phụ thuộc hợp đồng provider; không dùng nó như một cách phổ quát để bảo đảm “không ghi đè” trong mọi môi trường.
- **Nguyên tử của một move không phải nguyên tử của một batch gồm 20 move.** Những tệp đã chuyển vẫn có thể lộ ra trước khi toàn bộ batch hoàn thành.
- Khi move không nguyên tử thất bại, nguồn và đích có thể ở trạng thái chưa rõ; phải kiểm tra trước khi retry hoặc cleanup.

Một mô hình publish mạnh hơn có thể là đưa **toàn bộ batch vào một thư mục phiên bản mới**, xác minh rồi chuyển một tên directory/pointer manifest như một thao tác công bố cuối. Ngay cả thiết kế đó vẫn cần xét giới hạn của filesystem, việc có thể thay thế tên, và khôi phục sau crash.

### 8.3. Delete

`Files.delete(path)` yêu cầu xóa và báo lỗi nếu không thể xóa; `Files.deleteIfExists(path)` không xem file thiếu là lỗi. Cả hai có thể thất bại vì quyền, lock, thư mục không rỗng hoặc lỗi filesystem.

**Chỉ dùng `deleteIfExists` khi đúng chính sách:** với rollback, file không còn tồn tại có thể là bình thường; với yêu cầu “phải xóa chính xác file X”, đôi khi cần ghi nhận file thiếu như một trạng thái khác. Tuyệt đối không xóa đệ quy một thư mục do người dùng cung cấp nếu chưa chứng minh phạm vi được phép.

## 9. Cách giữ ngoại lệ chính và lỗi cleanup

### 9.1. `try-with-resources`

Đối với các tài nguyên mở bằng `Files.newInputStream`, `Files.newOutputStream`, `Files.newBufferedReader` hoặc `Files.newBufferedWriter`, ưu tiên `try-with-resources`:

```java
// Đoạn minh họa: inputPath và outputPath phải là Path hợp lệ.
try (InputStream in = Files.newInputStream(inputPath);
     OutputStream out = Files.newOutputStream(outputPath)) {
    in.transferTo(out);
}
```

Nếu thân `try` ném ngoại lệ và quá trình đóng stream cũng lỗi, Java giữ ngoại lệ chính và có thể gắn ngoại lệ đóng tài nguyên vào danh sách **suppressed exceptions**. Đây là lợi ích quan trọng khi chẩn đoán tác vụ nhiều bước.

Khai báo stream đầu ra phải rất cẩn thận: `Files.newOutputStream(outputPath)` với options mặc định sẽ tạo file nếu thiếu và có thể **truncate** file đã có. Khi cần tránh hủy nội dung cũ, chọn chính sách mở tương ứng (ví dụ tạo tên tạm hoặc `CREATE_NEW`).

### 9.2. `finally` không tự bảo đảm rollback

`finally` chạy khi thực thi rời khối theo cơ chế thông thường, nhưng không được bảo đảm khi tiến trình bị kill, mất điện hoặc hệ thống sập. Ngoài ra, nếu `finally` ném ngoại lệ mới, nó có thể **che lỗi gốc**.

Nếu buộc cleanup thủ công, hãy giữ ngoại lệ gốc và gắn lỗi cleanup bằng `addSuppressed`, đồng thời ghi log/manifest cho công việc dọn còn tồn đọng. Không chọn việc im lặng bỏ qua ngoại lệ cleanup trong các hệ thống cần biết trạng thái file.

### 9.3. Không bắt lỗi quá rộng

Một `catch (Exception ignored)` quanh toàn batch khiến không biết bước nào lỗi. Ngược lại, catch quá hẹp ở mỗi byte thao tác cũng khó bảo trì. Nên phân tầng:

- Mỗi bước I/O trả lỗi có ngữ cảnh `source → target` và tên bước.
- Bộ điều phối batch chọn dừng hay tiếp tục.
- Tầng ngoài cùng tổng hợp/report trạng thái và cleanup.
- Các lỗi lập trình và vi phạm bất biến không bị che dưới nhãn “file missing”.

## 10. Retry và tính idempotent

Thử lại thao tác là cần thiết với lỗi tạm thời, nhưng **retry mù quáng** có thể gây tác hại:

- Copy lại với `REPLACE_EXISTING` có thể ghi đè file đã được tiến trình khác cập nhật.
- Append log lại có thể tạo bản ghi trùng.
- Di chuyển nguồn đã được move thành công nhưng client tưởng thất bại có thể gặp `NoSuchFileException` ở lần retry.
- Xóa một tên tệp bị tái sử dụng bởi writer khác có thể xóa nhầm dữ liệu mới.

**Idempotent** nghĩa là lặp cùng yêu cầu không làm kết quả nghiệp vụ thay đổi sau lần thành công đầu tiên, dưới những giả định rõ. Một thiết kế có thể dùng mã batch duy nhất, target versioned, manifest và checksum để phát hiện thao tác đã hoàn tất.

Quy trình retry cần:

1. Phân loại nguyên nhân (thiếu quyền, đầy đĩa, nguồn tạm thời không khả dụng, xung đột đích).
2. Kiểm tra trạng thái thực tế của source, stage và destination.
3. Chỉ thử lại khi đủ điều kiện tránh đè dữ liệu không thuộc sở hữu.
4. Giới hạn số lượt/thời gian retry, ghi lại lần thử và nguyên nhân thất bại.
5. Không retry vô hạn trên lỗi xác định như sai quyền hoặc input không hợp lệ.

Với batch lớn, manifest nên ghi **ID, source, target, checksum nếu cần, trạng thái từng bước**. Đây là dữ liệu hỗ trợ recovery; không nhất thiết phải có database cho mọi bài toán nhỏ.

## 11. Sao lưu, ghi đè và khôi phục bản cũ

Nếu target đã tồn tại và phải giữ nó khi lỗi, mô hình “xóa mọi output mới” **không đủ**, vì một bước ghi đè có thể đã làm mất bản cũ.

Các hướng thiết kế:

- **Không ghi đè:** tạo tên độc nhất bằng `CREATE_NEW` hoặc thư mục versioned; công bố phiên bản khi hoàn chỉnh.
- **Staging riêng + backup:** tạo bản sao/version của đích trước khi thay thế và có quy trình rollback từ backup, phải kiểm tra backup thật sự khả dụng.
- **Thay đổi pointer/manifest:** file nội dung được ghi vào phiên bản mới, sau đó cập nhật một tên/file chỉ dẫn nhỏ để người đọc chọn phiên bản đã hoàn tất.
- **Transaction ở tầng ứng dụng:** nếu cần đồng bộ nhiều đối tượng, cân nhắc kho dữ liệu/metadata giao dịch và quy trình recovery được thiết kế riêng.

**Không hứa hẹn rằng nhiều file có thể được thay thế nguyên tử chỉ bằng `Files.move` nhiều lần.** Atomicity của từng file khác với atomicity của cả tập. Đồng thời, khôi phục “bản cũ” cần kiểm soát tệp nguồn, file backup và quyền sở hữu; lệnh copy ngược không tự khắc phục mọi tình huống mất điện hoặc thay đổi đồng thời.

### 11.1. Tạo file tạm đúng chỗ

`Files.createTempFile(targetDir, prefix, suffix)` giúp đặt file stage cùng vùng đích, có thể thuận lợi cho move trong cùng filesystem. Tuy nhiên:

- Tên tạm phải duy nhất; lưu danh sách để cleanup.
- Có thể tồn tại quota/permission, và tạo tệp tạm cũng có thể thất bại.
- Nếu cần secure permissions, xem cách đặt thuộc tính file khi tạo.
- Không dựng tệp tạm bằng một đường dẫn cố định dễ bị người khác chiếm dụng, đặc biệt ở thư mục công cộng.

## 12. An toàn đường dẫn và giới hạn tài nguyên

Batch thao tác hàng loạt thường nhận danh sách đường dẫn từ file cấu hình hoặc API. Cần bảo vệ cả **tệp đích** và **tài nguyên hệ thống**:

- Kiểm tra đường dẫn nguồn/đích, xử lý `..`, tên tuyệt đối, symlink và các quy tắc giới hạn thư mục. `Path.normalize()` chỉ xử lý cú pháp; không ngăn tự động TOCTOU.
- Đừng dùng `source.getFileName()` rồi cho rằng mọi trùng tên đã biến mất: hai tệp ở thư mục khác nhau có thể cùng tên.
- Giới hạn số tệp, tổng byte, số stream mở đồng thời, thời gian và kích thước tệp staging.
- Với file nhiều GB, dùng API copy/streaming; không dùng `Files.readAllBytes`/`readString` cho toàn bộ batch.
- Dùng một tập tên đầu ra xác định; không tự xóa bất kỳ mục nào nằm ngoài phạm vi batch.
- Thao tác nhiều tệp trên filesystem mạng/khác volume có semantics khác với một ổ đĩa cục bộ; kiểm thử ở môi trường triển khai thực.

## 13. Những lỗi thiết kế phổ biến

1. **Cho rằng vòng lặp `Files.copy` là transaction:** thực tế một lỗi có thể để lại tệp đã copy từ trước.
2. **Bắt lỗi ở ngoài cùng nhưng không biết file nào thất bại:** mất khả năng chẩn đoán và retry chọn lọc.
3. **Bắt lỗi từng tệp rồi báo cả batch thành công:** che số lượng và danh sách thất bại.
4. **Dọn mọi file trong thư mục đích khi rollback:** có thể xóa tài nguyên cũ của người khác.
5. **Không theo dõi các file stage đã tạo:** có thể để lại file tạm lâu dài.
6. **Dùng `REPLACE_EXISTING` tùy tiện:** có thể mất dữ liệu cũ không khôi phục được.
7. **Dùng `Files.exists` rồi tự tin rằng đích không thể đổi:** time-of-check-to-time-of-use vẫn tồn tại.
8. **Không xử lý cleanup thất bại:** thông tin file còn sót bị thất lạc.
9. **Tin mọi `move` đều nguyên tử:** `ATOMIC_MOVE` cần hỗ trợ; move từng tệp không làm cả batch nguyên tử.
10. **Tin catch/finally luôn chạy khi crash:** tiến trình có thể chết bất ngờ; cần recovery nếu yêu cầu.
11. **Giữ hàng nghìn stream mở cùng lúc:** dễ cạn file descriptor; mở/đóng theo phạm vi mỗi tệp.
12. **Chỉ đo số file, không đo byte/checksum:** nội dung có thể bị sai hoặc dở mà vẫn có tên file.
13. **Retry ngay lập tức không kiểm tra trạng thái:** có thể làm trùng bản ghi hoặc ghi đè bản mới.
14. **Bỏ qua symlink:** path “nằm trong thư mục” theo chuỗi có thể dẫn tới file khác qua link.
15. **Log tên file mà quên bước bị lỗi:** không phân biệt lỗi stage, verify, publish hay cleanup.
16. **Không có manifest khi cần hồi phục sau crash:** không biết tệp nào đã publish và tệp nào cần xử lý tiếp.

## 14. Bài tập thực hành

1. Với ba tệp `a.txt`, `b.txt`, `c.txt`, giả lập lỗi sao chép `b.txt`. Phân biệt trạng thái còn lại khi dùng **fail-fast** và **best-effort**.
2. Sửa `BestEffortFilesDemo` để trả danh sách kết quả gồm source, target, thành công/thất bại và nguyên nhân, thay vì boolean.
3. Thêm kiểm tra tên đích trùng nhau trước khi chạy batch. Hai nguồn `x/a.txt` và `y/a.txt` cần xử lý thế nào?
4. Thử tạo sẵn `outputs/first.txt` trước khi copy. `Files.copy` không có `REPLACE_EXISTING` báo ngoại lệ gì?
5. Thay giả lập lỗi trong `StagedBatchFilesDemo` bằng thất bại khi tạo stage. Danh sách cleanup có đủ để dọn các file do tác vụ sở hữu không?
6. Làm cho `Files.deleteIfExists` của rollback thất bại trong một môi trường thử. Kiểm tra `getSuppressed()` của ngoại lệ ban đầu.
7. Mô tả trường hợp `Files.move` thất bại sau khi tạo một phần đích. Vì sao chỉ dựa vào danh sách các bước “đã return thành công” có thể chưa đủ?
8. Thiết kế một manifest cho batch 10 tệp với các trạng thái `STAGED`, `VERIFIED`, `PUBLISHED`, `FAILED`; xử lý resume sau khi tiến trình bị kill.
9. Mô tả vì sao backup trước ghi đè chưa đủ nếu backup cũng có thể thất bại hoặc bị ghi đè.
10. So sánh “10 lần move có `ATOMIC_MOVE`” và “một thao tác công bố cả thư mục/version đã xây xong”. Cả hai có những điều kiện gì trên filesystem?
11. Với 1.000 tệp nhiều GB, hãy xây chính sách giới hạn số stream đang mở, tổng byte, dung lượng tạm và thời gian.
12. Giải thích tại sao `Path.normalize()` không thay thế kiểm tra symbolic link và permission khi cho người dùng tải lên file vào thư mục được kiểm soát.

## 15. Checklist trước khi triển khai

- [ ] Đã xác định fail-fast, best-effort hay staging/publish?
- [ ] Đã kiểm tra mapping source → target, trùng tên và chính sách ghi đè?
- [ ] Đã xác định rõ tài nguyên nào thuộc sở hữu batch, tài nguyên nào tuyệt đối không được xóa?
- [ ] Đã giới hạn tổng số file, tổng byte, mức đồng thời và thời gian?
- [ ] Đã tách lỗi chuẩn bị, kiểm tra, publish và cleanup?
- [ ] Đã giữ nguyên ngoại lệ chính cùng suppressed exceptions từ cleanup?
- [ ] Đã định nghĩa trạng thái sau lỗi, sau retry và sau crash?
- [ ] Đã kiểm tra cách công bố kết quả khi chỉ một phần batch thành công?
- [ ] Đã xác định semantics của `Files.copy`/`move`/`delete` trên filesystem thật?
- [ ] Đã xử lý quyền, symlink, thay đổi đồng thời và trường hợp hết đĩa?
- [ ] Đã xác định cần checksum/version/manifest hay không?
- [ ] Đã kiểm thử fail injection ở từng bước quan trọng?

## 16. Tổng kết

Xử lý nhiều tệp là **bài toán về trạng thái và quyền sở hữu**, không chỉ vòng lặp `Files.copy`. Một thiết kế đúng cần quyết định rõ **dừng hay tiếp tục**, tách **plan → stage → verify → publish → cleanup**, ghi nhận thành công/thất bại theo từng bước, và tuyệt đối tránh dọn những tài nguyên không thuộc tác vụ.

`try-with-resources` và `IOException` giúp quản lý lỗi I/O; `addSuppressed` giúp giữ thông tin cleanup; `ATOMIC_MOVE` (khi hỗ trợ) có thể giúp cho **một bước chuyển**, nhưng không tự tạo transaction cho toàn bộ batch. Với tính nhất quán cao, cần staged output, manifest/versioning, kiểm tra toàn vẹn, chính sách retry và recovery phù hợp.

**Nguyên tắc thực hành:** Sau mỗi bước, phải trả lời được **“đã thay đổi những file nào, file nào vẫn thuộc quyền của lần chạy này, và nếu bước tiếp theo thất bại thì sẽ làm gì?”**. Khi trả lời được câu hỏi đó bằng dữ liệu cụ thể, hệ thống mới có thể xử lý lỗi nhiều tệp một cách kiểm chứng và an toàn.

</details>

- [Quay lại đầu trang](#back-to-top)
