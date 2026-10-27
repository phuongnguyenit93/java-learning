<a id="back-to-top"></a>

# File truyền thống

## Menu
- [File là biểu diễn đường dẫn, không phải nội dung tệp](#legacy-file-io-001)
- [Tạo File không đồng nghĩa tạo tệp vật lý](#legacy-file-io-002)
- [Đường dẫn tuyệt đối, tương đối và thư mục làm việc](#legacy-file-io-003)
- [getPath, getAbsolutePath và getCanonicalPath](#legacy-file-io-004)
- [Kiểm tra tồn tại, loại tệp, kích thước và quyền truy cập](#legacy-file-io-005)
- [list, listFiles, FileFilter và FilenameFilter](#legacy-file-io-006)
- [mkdir, mkdirs, createNewFile, renameTo và delete](#legacy-file-io-007)
- [Giới hạn thông tin lỗi của API trả về boolean](#legacy-file-io-008)
- [Chuyển đổi File.toPath và Path.toFile](#legacy-file-io-009)
- [Khi nào nên ưu tiên Path và Files](#legacy-file-io-010)

## <a id="legacy-file-io-001">File là biểu diễn đường dẫn, không phải nội dung tệp</a>

<details>
<summary>Xem chi tiết</summary>

# File là biểu diễn đường dẫn, không phải nội dung tệp trong Java

`java.io.File` là lớp truyền thống của Java đại diện cho **một đường dẫn trừu tượng (*abstract pathname*)** tới tệp hoặc thư mục trong hệ thống tệp. Đối tượng `File` giúp chương trình **mô tả và thao tác với một vị trí**: lấy tên/đường dẫn, kiểm tra tồn tại, hỏi thông tin metadata, tạo, đổi tên hoặc xóa đối tượng hệ thống tệp khi gọi đúng phương thức.

**Điểm quan trọng nhất:** tạo `new File(...)` **không tạo tệp trên ổ đĩa, không đọc nội dung và không tải dữ liệu vào RAM**. Nó chỉ tạo một **đối tượng Java chứa thông tin đường dẫn**. Nội dung của tệp được đọc/ghi bằng các API I/O thích hợp như `Files.readString`, `Files.writeString`, `FileInputStream` hoặc `FileOutputStream`.

## 1. Hai đối tượng khác nhau: đường dẫn và tệp trên hệ thống tệp

Xét đoạn mã:

```java
File file = new File("data/report.txt");
```

Đây là một khai báo Java; để chạy cần `import java.io.File;` và đặt trong class/phương thức. Sau lệnh này:

- Java đã tạo một **đối tượng `File`** chứa đường dẫn tương đối `data/report.txt`.
- Có thể đường dẫn chưa tồn tại; `new File(...)` không kiểm tra và không tạo tệp chỉ vì gọi constructor.
- Không byte nào từ `report.txt` được tự động đọc vào đối tượng; `File` **không phải mảng byte, không phải nội dung `String` và không phải một luồng đã mở**.
- Đường dẫn có thể chỉ tới **tệp**, **thư mục** hoặc một vị trí chưa tồn tại. Chỉ nhìn tên `report.txt` hoặc phần mở rộng `.txt` không chứng minh đó là tệp văn bản.

```text
Đối tượng Java:
  File("data/report.txt")
        |
        | mô tả một vị trí
        v
Hệ thống tệp:
  data/report.txt   -> có thể đang tồn tại hoặc không
                         |
                         | khi có tệp thực, có các byte nội dung
                         v
                       [byte][byte][byte]...

new File(...)  : chỉ tạo đối tượng đường dẫn
Files.read...  : đọc nội dung từ hệ thống tệp
Files.write... : ghi nội dung và có thể tạo tệp, tùy API/tùy chọn
```

Điều này tương tự ghi một **địa chỉ nhà** lên giấy: có địa chỉ không có nghĩa tại đó đã có ngôi nhà hoặc giấy đã chứa đồ đạc trong nhà. Đối tượng `File` không giữ “ảnh chụp” bất biến của metadata; mỗi lần gọi các phương thức như `exists()` có thể tra lại trạng thái hiện tại của hệ thống tệp.

## 2. `File` biểu diễn đường dẫn như thế nào?

Lớp `File` thuộc package `java.io`; các cách tạo thông dụng:

```java
import java.io.File;

File relative = new File("docs", "note.txt");
File absolute = new File("/var/tmp/note.txt"); // Ví dụ đường dẫn Unix.
File current = new File(".");
```

Trên Windows, dạng tuyệt đối có thể là `C:\temp\note.txt` (trong Java string literal cần viết `"C:\\temp\\note.txt"`). Không giả định đường dẫn Unix là tuyệt đối trên mọi nền tảng. Có thể dùng API hiện đại `Path.of("docs", "note.txt")` để ghép các phần đường dẫn theo hệ điều hành.

Các phương thức thông dụng:

| API `File` | Ý nghĩa | Có tự đọc nội dung tệp? |
| --- | --- | --- |
| `getName()` | Lấy thành phần tên cuối đường dẫn | Không |
| `getPath()` | Trả cách biểu diễn đường dẫn được lưu trong đối tượng | Không |
| `getParent()` / `getParentFile()` | Trả phần đường dẫn cha, nếu có | Không |
| `isAbsolute()` | Đường dẫn là tuyệt đối theo quy tắc nền tảng? | Không |
| `getAbsolutePath()` | Biểu diễn đường dẫn tuyệt đối | Không |
| `getCanonicalPath()` | Tạo đường dẫn canonical theo hệ thống tệp; có thể ném `IOException` | Không đọc nội dung |
| `toPath()` | Chuyển sang đối tượng `java.nio.file.Path` | Không |
| `exists()` | Kiểm tra vị trí đang tồn tại, phụ thuộc quyền/trạng thái FS | Không đọc nội dung |
| `isFile()` / `isDirectory()` | Kiểm tra loại đối tượng FS tại thời điểm gọi | Không đọc nội dung |
| `length()` | Trả kích thước tính bằng **byte** trong trường hợp tệp thích hợp | Không trả nội dung |
| `listFiles()` | Liệt kê các entry con của thư mục dưới dạng `File[]` | Không đọc nội dung từng tệp |

`getCanonicalPath()` có thể yêu cầu truy vấn hệ thống tệp và chuẩn hóa đường dẫn theo nền tảng. `getAbsolutePath()` chỉ tạo biểu diễn tuyệt đối, **không chứng minh tệp tồn tại**. Cả hai không phải cách mở/đọc file.

### Tương đối và tuyệt đối

`new File("report.txt")` thường biểu diễn đường dẫn **tương đối** so với thư mục làm việc của tiến trình Java tại thời điểm giải quyết đường dẫn. Giá trị `System.getProperty("user.dir")` cho biết thư mục làm việc thường được JVM dùng khi xử lý đường dẫn tương đối. Khi chạy từ IDE, terminal hoặc hệ thống build, thư mục làm việc có thể khác nhau.

```java
File file = new File("report.txt");
System.out.println(file.isAbsolute());      // Thường false.
System.out.println(file.getAbsolutePath()); // Phụ thuộc thư mục làm việc.
```

Không nên ghi cố định stdout của `getAbsolutePath()` trong ví dụ học tập vì kết quả phụ thuộc máy. Một đường dẫn tuyệt đối chưa chắc trỏ tới tệp tồn tại; tên file có phần mở rộng cũng không xác định được loại hay nội dung thực tế.

## 3. Tạo `File` khác với tạo tệp thực

Các thao tác cần phân biệt:

```java
File file = new File("note.txt");     // Chỉ tạo đối tượng đường dẫn.
boolean created = file.createNewFile(); // Yêu cầu tạo tệp rỗng trên đĩa.
```

`createNewFile()` có thể ném `IOException`; nếu tệp đã tồn tại, nó trả `false` và không ghi đè nội dung có sẵn. Phép kiểm tra và tạo này là một thao tác nguyên tử về mặt API, trong phạm vi cơ chế hệ thống tệp hỗ trợ. Không nên gọi nó khi chỉ muốn **đọc** một tệp.

Các thao tác truyền thống khác:

| Phương thức | Việc có thể làm trên hệ thống tệp |
| --- | --- |
| `new File(path)` | Không tạo file hay thư mục |
| `createNewFile()` | Tạo **tệp rỗng** nếu chưa tồn tại |
| `mkdir()` | Tạo một thư mục tại đường dẫn khi điều kiện cho phép |
| `mkdirs()` | Tạo thư mục cùng các thư mục cha còn thiếu |
| `delete()` | Yêu cầu xóa entry được trỏ tới; trả `boolean` |
| `renameTo(dest)` | Yêu cầu đổi tên/di chuyển; hành vi phụ thuộc nền tảng, kết quả có thể thất bại |
| `setReadable(...)`, `setWritable(...)` | Yêu cầu thay đổi thuộc tính/quyền, có giới hạn theo hệ điều hành |

Những phương thức trên **có tác dụng lên hệ thống tệp khi được gọi**; đối tượng `File` bản thân nó vẫn chỉ là biểu diễn đường dẫn.

**Không nhầm `createNewFile()` với việc ghi nội dung.** Nếu cần tạo file kèm dữ liệu, API đọc/ghi mới như `Files.writeString(...)` hoặc stream đầu ra sẽ phù hợp hơn và phải xác định chính sách tạo mới/ghi đè rõ ràng.

## 4. Chương trình Java 17+ chạy được: trước và sau khi tệp tồn tại

Để kiểm tra mà không làm ảnh hưởng file sẵn có, chương trình tạo một **thư mục tạm mới**, rồi tạo đối tượng `File` cho một tên tệp chưa tồn tại trong đó. Chỉ sau khi gọi `Files.writeString`, tệp mới có nội dung.

Lưu mã sau vào **`FilePathDemo.java`**:

```java
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FilePathDemo {

    public static void main(String[] args) throws IOException {
        Path temporaryDirectory =
                Files.createTempDirectory("java-file-demo-");
        File file = temporaryDirectory.resolve("note.txt").toFile();

        try {
            // Tạo đối tượng File chưa làm xuất hiện file thật.
            System.out.println("Name: " + file.getName());
            System.out.println("Before exists: " + file.exists());
            System.out.println("Before isFile: " + file.isFile());

            // Đây mới là thao tác I/O thực sự tạo và ghi nội dung.
            Files.writeString(
                    file.toPath(), "Java I/O", StandardCharsets.UTF_8);

            System.out.println("After exists: " + file.exists());
            System.out.println("After isFile: " + file.isFile());
            System.out.println("Length in bytes: " + file.length());
            System.out.println("Text: " + Files.readString(
                    file.toPath(), StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(temporaryDirectory);
        }
    }
}
```

Biên dịch và chạy trên JDK 17+:

```bash
javac -encoding UTF-8 FilePathDemo.java
java FilePathDemo
```

**Stdout dự kiến khi thao tác hệ thống tệp thành công:**

```text
Name: note.txt
Before exists: false
Before isFile: false
After exists: true
After isFile: true
Length in bytes: 8
Text: Java I/O
```

Giải thích:

1. `Files.createTempDirectory(...)` tạo thư mục có tên riêng để ví dụ không đụng đến dữ liệu đang có.
2. `temporaryDirectory.resolve("note.txt").toFile()` tạo **đối tượng `File`** cho tệp chưa tồn tại; `exists()` và `isFile()` đều trả `false`.
3. `Files.writeString(..., UTF_8)` mới thực sự tạo và ghi file. Chuỗi `Java I/O` gồm 8 ký tự ASCII, do đó mã hóa thành **8 byte UTF-8**.
4. Sau khi ghi, `exists()` và `isFile()` là `true`; `file.length()` báo 8 **byte**, không trả nội dung.
5. `Files.readString(...)` đọc byte từ tệp thật và giải mã UTF-8 thành `String`.
6. `finally` xóa file và thư mục tạm; nếu môi trường cấm tạo/ghi/xóa, chương trình có thể ném `IOException`, không phải vì constructor `File` tự đọc hoặc tạo tệp.

Chương trình không in đường dẫn thư mục tạm vì đường dẫn đó khác nhau giữa các lần chạy. Nó kiểm chứng rõ rằng **`File` tồn tại trong bộ nhớ trước khi tệp vật lý được tạo**.

## 5. Metadata không phải nội dung

Ví dụ:

```java
File file = new File("data.bin");

boolean exists = file.exists();
boolean regular = file.isFile();
long bytes = file.length();
```

Đây là đoạn minh họa cần đặt bên trong một class/phương thức. Nó **không đọc từng byte** của `data.bin` vào ứng dụng. Các phép truy vấn có thể tương tác với hệ thống tệp để lấy metadata.

Các giới hạn cần hiểu:

- `exists() == false` không phải bằng chứng chắc chắn rằng không có đường dẫn trên thế giới: trạng thái có thể thay đổi, quyền truy cập hoặc lỗi hệ thống tệp có thể ảnh hưởng kết quả.
- `isFile()` có nghĩa là đường dẫn hiện liên kết tới tệp thông thường theo kiểm tra của Java; nếu trỏ tới thư mục, dùng `isDirectory()`.
- `length()` dùng để hỏi **kích thước tính bằng byte** trong bối cảnh tệp thông thường. `0` có thể là tệp rỗng, nhưng cũng có thể là đường dẫn không tồn tại hoặc loại đối tượng mà kích thước không được xác định theo `File.length()`. **Không dùng `length() == 0` để chứng minh file không tồn tại.**
- Metadata có thể đổi **ngay sau khi kiểm tra**, ví dụ luồng khác xóa hoặc sửa tệp. Không coi `exists()` rồi `open()` là một hành động nguyên tử.

Nếu thật sự cần đọc nội dung, dùng API phù hợp với định dạng: `Files.readString` với file văn bản và charset đã biết; `Files.readAllBytes` cho file nhị phân nhỏ; hoặc stream để xử lý file lớn.

## 6. `File` so với `FileInputStream` và `FileOutputStream`

Tên gần nhau nhưng nhiệm vụ khác nhau:

| API | Vai trò | Có trực tiếp đọc/ghi dữ liệu? |
| --- | --- | --- |
| `java.io.File` | Biểu diễn đường dẫn; metadata và thao tác hệ thống tệp | Không qua constructor; bản thân không cung cấp các phương thức đọc byte nội dung |
| `FileInputStream` | Mở luồng **đọc byte** từ tệp | Có, khi gọi `read(...)` |
| `FileOutputStream` | Mở luồng **ghi byte** ra tệp | Có, khi gọi `write(...)`; constructor có thể tạo/cắt ngắn file |
| `FileReader` / `FileWriter` | Luồng **ký tự** cho file; dùng charset tương ứng | Có, khi gọi `read/write`; cần xác định charset |
| `Files` (`java.nio.file.Files`) | API tĩnh cho tạo/đọc/ghi/sao chép/di chuyển/xóa và metadata | Có, tùy phương thức |
| `Path` (`java.nio.file.Path`) | Biểu diễn đường dẫn kiểu NIO.2 | Không tự đọc nội dung |

Ví dụ đọc file qua `FileInputStream`:

```java
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

static int firstByte(File file) throws IOException {
    try (FileInputStream input = new FileInputStream(file)) {
        return input.read(); // 0..255 hoặc -1 nếu EOF.
    }
}
```

Đây là **phương thức minh họa**, cần đặt trong một class. `new FileInputStream(file)` có thể ném lỗi nếu tệp không tồn tại, không đọc được hoặc là thư mục. Chỉ **khi mở stream và gọi `read()`**, nội dung mới được lấy từ tệp.

Đừng nhầm `new FileInputStream(file)` với `new File(filePath)`: constructor đầu tiên có thể mở tài nguyên thật và thất bại do I/O; constructor thứ hai chủ yếu chỉ xác lập đối tượng đường dẫn.

## 7. Lớp `Path` và `Files` trong Java hiện đại

Từ Java 7, API NIO.2 (`java.nio.file`) giới thiệu `Path` và `Files`, thường giúp xử lý đường dẫn và thao tác hệ thống tệp rõ ràng hơn:

```java
import java.nio.file.Path;
import java.nio.file.Files;

Path path = Path.of("data", "report.txt");   // Chỉ mô tả đường dẫn.
boolean found = Files.exists(path);           // Kiểm tra metadata.
```

`Path.of(...)` **không tự tạo file**, giống ý nghĩa cốt lõi của `new File(...)`. Còn `Files.*` thường là nơi gọi những thao tác thật:

```java
Files.createDirectories(path.getParent()); // Tạo thư mục cha nếu cần.
Files.writeString(path, "Xin chào");       // Tạo/ghi nội dung theo API.
String content = Files.readString(path);   // Đọc nội dung.
```

Đây là mã minh họa; việc thực thi có thể tạo/ghi đè file tùy tùy chọn. `Files.writeString(path, text)` sử dụng UTF-8 theo hợp đồng mặc định; khi cần rõ ý định, có thể truyền `StandardCharsets.UTF_8`. Trong ứng dụng thực tế nên xác định đường dẫn, chính sách ghi đè và xử lý `IOException` trước khi thao tác.

Chuyển đổi giữa hai kiểu:

```java
File legacy = new File("report.txt");
Path modern = legacy.toPath();
File back = modern.toFile();
```

`toPath()` và `toFile()` chuyển **biểu diễn đường dẫn**, không sao chép hoặc tự đọc nội dung tệp. `Path` thường được ưu tiên cho mã Java mới bởi nhiều API xử lý file, link, attributes và ngoại lệ phong phú hơn; `File` vẫn phổ biến trong thư viện/ứng dụng legacy.

## 8. Đường dẫn chưa chắc tồn tại, đúng loại hoặc được truy cập

Một đối tượng `File` hợp lệ về mặt Java vẫn có thể đại diện cho:

- Một đường dẫn đến file đang tồn tại;
- Một thư mục đang tồn tại;
- Một đường dẫn chưa tồn tại;
- Một liên kết tượng trưng (*symbolic link*) và đối tượng đích theo quy tắc API;
- Một vị trí mà tiến trình không có quyền truy cập;
- Một đường dẫn có trạng thái thay đổi giữa các lần kiểm tra.

Vì vậy, không suy luận rằng “khởi tạo thành công” thì “đọc được”.

Ví dụ kiểm tra **trước khi xử lý**:

```java
if (!file.exists()) {
    throw new IllegalArgumentException("Đường dẫn không tồn tại");
}
if (!file.isFile()) {
    throw new IllegalArgumentException("Đây không phải tệp thông thường");
}
```

Đoạn minh họa này có thể giúp báo lỗi sớm trong UI, nhưng **không thay thế xử lý `IOException` khi mở/đọc**. Một tiến trình khác có thể xóa hoặc thay file sau khi bạn kiểm tra, hoặc quyền truy cập có thể thay đổi. Đây là lỗi *time-of-check to time-of-use* (TOCTOU). Với dữ liệu quan trọng hoặc không tin cậy, hãy chọn API mở file an toàn, kiểm soát quyền và xử lý lỗi trên chính thao tác thật.

Thêm nữa, các kiểm tra path như `getCanonicalPath()` không tự động biến đường dẫn do người dùng nhập thành an toàn: khi ứng dụng ghép đường dẫn từ input, cần giới hạn thư mục được phép, xử lý `..`, symlink và quyền truy cập theo yêu cầu bảo mật.

## 9. Lỗi thường gặp khi học `java.io.File`

| Quan niệm hoặc thao tác sai | Vì sao sai? | Cách hiểu/cách xử lý |
| --- | --- | --- |
| `new File("a.txt")` là đã tạo file | Constructor chỉ tạo đối tượng đường dẫn | Dùng API tạo/ghi riêng như `createNewFile` hoặc `Files.writeString` |
| `new File(...)` đã đọc file vào RAM | `File` không chứa nội dung | Mở `InputStream` hoặc dùng `Files.read*` |
| `file.toString()` trả nội dung file | `toString()` chủ yếu trả biểu diễn đường dẫn | `Files.readString(file.toPath(), charset)` để đọc văn bản |
| `file.getName()` trả tên thật từ nội dung | Nó lấy phần cuối của đường dẫn | Phân biệt tên path với dữ liệu trong file |
| `file.exists()` là bằng chứng đọc được | File có thể không có quyền đọc hoặc thay đổi sau check | Xử lý lỗi khi mở file |
| `file.length()` cho số ký tự trong file văn bản | `length()` là byte, có thể khác số ký tự UTF-8 | Chọn cách đếm ký tự sau decode nếu cần |
| `file.length() == 0` nghĩa là không tồn tại | File rỗng cũng có thể dài 0 byte | Kiểm tra tồn tại và loại file riêng |
| Đường dẫn có đuôi `.txt` chắc chắn là văn bản | Phần mở rộng chỉ là quy ước tên | Biết rõ định dạng và charset thực tế |
| `getAbsolutePath()` là tệp đã tồn tại | Chỉ biểu diễn path tuyệt đối | `exists()`/`Files.readAttributes` khi cần metadata |
| `File` và `Path` là hai file khác nhau | Chúng có thể mô tả cùng vị trí | `toPath()`/`toFile()` không tạo bản sao dữ liệu |
| Chỉ cần `exists()` trước khi đọc là không thể lỗi | Có race và lỗi I/O | Xử lý ngoại lệ từ thao tác đọc thực |
| Mở `FileOutputStream` để “xem đường dẫn” | Nó là API ghi, có thể tạo hoặc làm mất nội dung cũ | Dùng `File`/`Path` để mô tả, không mở writer ngoài ý muốn |

## 10. Khi nào chọn `File`, khi nào chọn `Path`?

Dùng `File` khi phải làm việc với API hoặc thư viện legacy nhận `java.io.File`; lớp này vẫn hữu ích cho bài học về **đường dẫn** và các thao tác filesystem truyền thống.

Với code Java hiện đại, nên ưu tiên `Path` + `Files`, đặc biệt khi cần:

- Đọc/ghi nội dung có charset xác định;
- Xử lý nhiều loại thao tác file, thuộc tính và link;
- Thông báo lỗi I/O chi tiết thông qua ngoại lệ;
- Sao chép, di chuyển, tạo thư mục và dùng tùy chọn rõ ràng.

Điều quan trọng vẫn như nhau ở cả hai hệ API:

```text
File hoặc Path = thông tin về vị trí
Files / Stream  = hành động đọc, ghi, tạo, xóa hoặc xử lý
byte[] / String = nội dung đã đọc vào bộ nhớ của chương trình
```

Ngay cả khi đối tượng `File` đã tồn tại lâu trong chương trình, trạng thái file thực tế vẫn có thể thay đổi theo thời gian; không sử dụng `File` như một bộ nhớ đệm nội dung hoặc chứng nhận “file luôn sẵn sàng”.

## 11. Bài tập tự kiểm tra

1. **`new File("missing.txt")` có ném lỗi ngay nếu file không tồn tại?** Thông thường **không**; nó chỉ tạo đối tượng biểu diễn đường dẫn.
2. **Cần tạo tệp rỗng mới từ `File` thì dùng gì?** `createNewFile()`, đồng thời xử lý `IOException` và kết quả `boolean`.
3. **`File.length()` trả gì?** Giá trị metadata kích thước **byte** trong trường hợp phù hợp, không phải chuỗi nội dung hay số ký tự.
4. **`new File("notes.txt").getName()` cho kết quả gì?** `notes.txt`, không phải văn bản bên trong.
5. **`File.toPath()` có tạo bản sao file không?** **Không**; chỉ chuyển biểu diễn đường dẫn.
6. **Muốn đọc UTF-8 từ tệp hiện có thì dùng gì?** `Files.readString(file.toPath(), StandardCharsets.UTF_8)` hoặc `InputStreamReader` phù hợp.
7. **`exists()` trả true thì lần đọc sau chắc chắn thành công?** **Không**; quyền, trạng thái và đối tượng hệ thống tệp có thể thay đổi.
8. **Có thể có một `File` trỏ tới thư mục?** **Có**; dùng `isDirectory()` để kiểm tra trạng thái tại thời điểm gọi.
9. **Vì sao không dùng `FileOutputStream` chỉ để “xác minh file”?** Vì constructor có thể tạo hoặc cắt ngắn file, gây thay đổi dữ liệu ngoài ý muốn.
10. **Nếu code mới chỉ cần mô tả đường dẫn, nên xem API nào?** `Path.of(...)`; khi thực sự cần thao tác nội dung thì dùng `Files` hoặc stream.

**Kết luận thực hành:** `File` là **một đối tượng mang đường dẫn**, không phải đối tượng lưu nội dung. **Khởi tạo `File` không đồng nghĩa tạo file; hỏi metadata không đồng nghĩa đọc nội dung; đọc/ghi dữ liệu phải dùng đúng API I/O.**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="legacy-file-io-002">Tạo File không đồng nghĩa tạo tệp vật lý</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo `File` không đồng nghĩa tạo tệp vật lý trong Java

Khi viết `new File("report.txt")`, Java **chỉ tạo một đối tượng mô tả đường dẫn**, chưa tạo `report.txt` trên ổ đĩa. Đây là một điểm rất quan trọng trong nhóm **File truyền thống** (`java.io.File`): một đối tượng `File` có thể trỏ tới tệp đang tồn tại, thư mục, đường dẫn chưa tồn tại hoặc đường dẫn không truy cập được. Sự tồn tại của đối tượng Java và sự tồn tại của tài nguyên trên hệ thống tệp là **hai việc khác nhau**.

Ví dụ tối giản:

```java
File report = new File("report.txt"); // Chỉ tạo đối tượng biểu diễn tên/đường dẫn
boolean exists = report.exists();     // Hỏi hệ thống tệp có tài nguyên tương ứng không
```

Mảnh mã cần `import java.io.File;`. `new File(...)` không thực hiện thao tác tạo file vật lý. `exists()` mới kiểm tra trạng thái đường dẫn tại thời điểm gọi; nó có thể là `false` nếu file chưa tồn tại. Nếu file tồn tại từ trước, `exists()` có thể trả `true` ngay sau constructor, nhưng chính constructor **không phải tác nhân đã tạo file**.

Bài học này trình bày bản chất của `File`, các phương thức thực sự tạo tệp/thư mục, cách xử lý lỗi, ví dụ chạy được bằng JDK 17+ và những điểm cần biết khi chuyển sang `java.nio.file.Path` / `Files`.

## 1. Đối tượng File chỉ là biểu diễn đường dẫn

Tài liệu Java định nghĩa `File` là một **abstract representation of file and directory pathnames** — biểu diễn trừu tượng của đường dẫn tệp hoặc thư mục. Nó không phải là nội dung file, không phải một luồng byte đang mở và cũng không phải một handle bắt buộc đang liên kết tới file tồn tại.

Hãy hình dung:

```text
Trong JVM                             Trên ổ đĩa
----------------------------------   -------------------------------
File f = new File("data/a.txt");      data/a.txt có thể chưa tồn tại
        |
        +-- lưu thông tin đường dẫn
        +-- có getPath(), getName()
        +-- có thể gọi exists(), isFile()
        |
        +-- CHƯA tự mở, ghi hoặc tạo file vật lý
                                        |
f.createNewFile() ---------------------> tạo file rỗng nếu chưa có
Files.writeString(...) ---------------> có thể tạo/ghi file
new FileOutputStream(f) -------------> có thể tạo/mở file để ghi
```

Ba trường hợp đều hợp lệ:

1. `File f = new File("a.txt")` khi `a.txt` **chưa tồn tại**: vẫn tạo được đối tượng `f`.
2. `File f = new File("a.txt")` khi `a.txt` **đã tồn tại**: vẫn tạo đối tượng `f`, không tạo lại hoặc ghi đè.
3. `File f = new File("reports")` khi `reports` là **thư mục**: vẫn tạo đối tượng `f`; kiểm tra `isDirectory()` để xác định loại tài nguyên.

Constructor có thể báo các lỗi lập trình như đường dẫn `null`, nhưng **không** kiểm chứng rằng file hiện hữu hoặc tự tạo thư mục cha. Đừng coi thành công của constructor là bằng chứng đã ghi dữ liệu.

### Phân biệt tên đường dẫn và tài nguyên thực

| Khái niệm | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| Đối tượng `File` | `new File("data.txt")` | Biểu diễn đường dẫn trong bộ nhớ JVM |
| Đường dẫn tương đối | `"data.txt"` | Được phân giải theo thư mục làm việc của tiến trình |
| Đường dẫn tuyệt đối | `"C:\\data\\data.txt"`, `"/tmp/data.txt"` | Chỉ định vị trí không phụ thuộc thư mục hiện hành (theo quy tắc hệ điều hành) |
| Tài nguyên trên hệ thống tệp | File thường, thư mục, symbolic link, v.v. | Chỉ tồn tại khi hệ thống tệp chứa mục tương ứng |
| Luồng mở | `FileInputStream` / `FileOutputStream` | Tương tác thực tế với byte của nguồn/đích, có vòng đời đóng |

`File` không đảm bảo rằng đường dẫn được định danh là file thường; nó có thể chỉ tới **thư mục**. Hơn nữa, tài nguyên có thể bị tiến trình khác tạo, xóa hoặc đổi tên **sau** khi đối tượng `File` đã được tạo.

## 2. Constructor nào cũng chỉ tạo đối tượng đường dẫn

Các cách khởi tạo phổ biến:

```java
File a = new File("notes.txt");
File b = new File("data", "notes.txt");
File parent = new File("data");
File c = new File(parent, "notes.txt");
```

Mảnh mã cần `java.io.File`.

Cả bốn dòng **không tạo file/thư mục**. `b` và `c` được dựng từ phần cha/con; phần cha chưa tồn tại trên ổ đĩa vẫn không cản trở việc tạo các đối tượng này. Dựng đối tượng đường dẫn không tự tạo đường dẫn cha trung gian.

Đặc biệt, hai dòng dưới đây không tương đương:

```java
new File("logs");  // Chỉ tạo đối tượng File mô tả "logs"
new File("logs").mkdir(); // Cố gắng tạo thư mục "logs" trên hệ thống tệp
```

Dòng thứ hai mới yêu cầu thao tác I/O, có thể thành công hoặc thất bại và phải kiểm tra kết quả.

Kể cả `getAbsolutePath()` cũng **chỉ trả thông tin đường dẫn**:

```java
File f = new File("missing.txt");
System.out.println(f.getAbsolutePath());
```

Lệnh in ra một chuỗi đường dẫn tuyệt đối dựa trên thư mục làm việc. Nó **không** tạo `missing.txt`, và kết quả không phải chứng cứ file có mặt. `getCanonicalPath()`/`getCanonicalFile()` có thể chuẩn hóa đường dẫn theo quy tắc hệ thống tệp và có thể ném `IOException`, nhưng không đồng nghĩa với thao tác tạo file vật lý.

## 3. Kiểm tra sự tồn tại và loại tài nguyên

`File` cung cấp một số phương thức thăm dò trạng thái:

| Phương thức | Ý nghĩa |
| --- | --- |
| `exists()` | Kiểm tra đường dẫn tương ứng hiện có tài nguyên truy cập được theo API |
| `isFile()` | Kiểm tra có phải **file thường** hay không |
| `isDirectory()` | Kiểm tra có phải **thư mục** hay không |
| `length()` | Lấy kích thước file theo byte; nếu không phải file thường hoặc lỗi xảy ra, kết quả không đủ để kết luận nội dung |
| `lastModified()` | Thời điểm sửa đổi cuối được báo bởi hệ thống tệp, có giá trị giới hạn khi không tồn tại/lỗi |
| `canRead()` / `canWrite()` | Kiểm tra khả năng đọc/ghi được báo tại thời điểm gọi |
| `getName()` / `getParent()` / `getPath()` | Trích xuất thông tin đường dẫn; không cần tài nguyên phải tồn tại |

Thí dụ:

```java
File candidate = new File("config/app.properties");
if (candidate.isFile()) {
    System.out.println("Đang trỏ tới một file thường");
} else if (candidate.isDirectory()) {
    System.out.println("Đang trỏ tới thư mục");
} else {
    System.out.println("Không có file thường/thư mục truy cập được tại đường dẫn");
}
```

Đây là mảnh mã với `import java.io.File;`.

**Không suy ra quá nhiều từ các kết quả boolean:**

- `exists() == false` có thể là chưa có đường dẫn, nhưng cũng có thể liên quan quyền truy cập/lỗi hệ thống. Chỉ dựa vào boolean không phân tích được mọi nguyên nhân.
- `isFile() == false` **không** khẳng định đường dẫn chắc chắn trống: nó có thể trỏ tới thư mục hoặc một loại mục hệ thống tệp khác.
- `length() == 0` **không** chứng minh file rỗng: có thể là đường dẫn chưa tồn tại hoặc lỗi.
- `canWrite() == true` không đảm bảo thao tác ghi sắp tới sẽ thành công; quyền và trạng thái có thể thay đổi.
- Sau khi kiểm tra `exists()`, một tiến trình khác vẫn có thể tạo/xóa file ngay trước thao tác tiếp theo. Đây là vấn đề **TOCTOU** (*time-of-check to time-of-use*).

Khi hành động cần đảm bảo “tạo mới nếu chưa có, nếu đã có thì thất bại”, ưu tiên chính thao tác tạo mới nguyên tử thay vì kiểm tra riêng bằng `exists()` rồi mới mở ghi.

## 4. Thao tác nào thực sự tạo file vật lý?

### 4.1. `File.createNewFile()`

```java
File output = new File("result.txt");
boolean created = output.createNewFile();
```

Đây là mảnh mã cần `java.io.File`, `IOException` phải được xử lý.

Ý nghĩa:

- Tạo **file rỗng** tại đường dẫn của `output` khi **chưa có mục cùng tên**, đồng thời kiểm tra tồn tại và tạo mới như một thao tác nguyên tử theo hợp đồng API.
- Trả `true` nếu **đã tạo mới thành công**.
- Trả `false` nếu mục cùng tên **đã tồn tại**; không tự ghi đè.
- Có thể ném `IOException` nếu không tạo được file vì lỗi I/O, thư mục cha thiếu, quyền truy cập hoặc điều kiện hệ thống khác.
- Không tự tạo những thư mục cha bị thiếu.
- File vừa tạo có độ dài 0 byte; nó chưa chứa văn bản hay nội dung ứng dụng.

Vì vậy:

```java
File f = new File("result.txt");
System.out.println(f.createNewFile()); // true khi mới tạo
System.out.println(f.createNewFile()); // false nếu file vẫn còn
```

Hai dòng `println` minh họa kết quả **trong điều kiện không có tác nhân khác thay đổi đường dẫn** và việc tạo lần đầu thành công. Không thể cam kết mọi hệ thống đều cho phép ghi tại đường dẫn ví dụ.

### 4.2. `Files.createFile(path)`

API NIO hiện đại:

```java
Path path = Path.of("result.txt");
Files.createFile(path);
```

Đây là mảnh mã cần `java.nio.file.*` và xử lý `IOException`.

`Files.createFile(...)` cũng yêu cầu tạo **file rỗng mới** với kiểm tra tồn tại và tạo mới nguyên tử. Khác biệt dễ quan sát là: **nếu đường dẫn đã tồn tại, nó thường ném `FileAlreadyExistsException`**, thay vì trả `false` như `File.createNewFile()`. Các lỗi khác cũng thường được cung cấp dưới dạng những subtype `IOException` giàu thông tin hơn.

Nên dùng `Files.createFile(...)` khi thiết kế mới với `Path`; dùng `createNewFile()` khi đang làm việc với API `java.io.File` truyền thống.

### 4.3. Mở luồng ghi cũng có thể tạo file

`new FileOutputStream(file)` hoặc `new FileWriter(file)` là thao tác mở **đích ghi thực tế**, và có thể:

- Tạo file mới nếu đường dẫn chưa tồn tại và thư mục cha hợp lệ.
- Ghi đè/truncate nội dung file đã có nếu dùng constructor mặc định không append.
- Thất bại nếu không có quyền, đường dẫn sai, thư mục cha chưa tồn tại, hoặc đang trỏ vào tài nguyên không ghi được.

Ví dụ:

```java
File f = new File("message.txt"); // Chưa tạo file
try (FileOutputStream out = new FileOutputStream(f)) {
    out.write(new byte[] { 65, 66, 67 }); // Ghi "ABC" ở dạng byte
}
```

Đây là mảnh mã; cần `java.io.File`, `java.io.FileOutputStream` và xử lý `IOException`. **Thao tác mở `FileOutputStream`** có thể tạo file; không phải `new File`. Với file đã tồn tại, `FileOutputStream(f)` mặc định làm trống nội dung cũ trước khi ghi. Để ghi nối, dùng constructor có cờ append phù hợp và hiểu quy tắc ghi nối của hệ thống.

Với dữ liệu văn bản, cũng có thể dùng:

```java
Files.writeString(Path.of("message.txt"), "Xin chào",
        StandardCharsets.UTF_8);
```

Trong Java 17, khi không truyền `OpenOption`, `Files.writeString` mặc định tương đương `CREATE`, `TRUNCATE_EXISTING` và `WRITE`: có thể **tạo file nếu chưa có hoặc ghi đè nội dung nếu đã có**. Cần import `java.nio.file.*`, `java.nio.charset.StandardCharsets` và xử lý `IOException`. Đừng dùng mặc định đó cho file người dùng quan trọng nếu không muốn ghi đè.

## 5. Tạo thư mục khác tạo file thường

`File` có hai phương thức quan trọng:

```java
File single = new File("logs");
boolean one = single.mkdir();

File nested = new File("reports/2026/october");
boolean many = nested.mkdirs();
```

Mảnh mã cần `java.io.File`. Trong đó:

- `mkdir()` cố gắng tạo **một thư mục**. Nếu thư mục cha còn thiếu thì thao tác thông thường thất bại.
- `mkdirs()` cố gắng tạo thư mục đích **và các thư mục cha còn thiếu**. Khi thất bại, một số thư mục cha **có thể đã được tạo**, nên thao tác không phải transaction rollback toàn bộ.
- Cả hai trả `true` khi thư mục mục tiêu **được tạo bởi lời gọi đó**, và `false` nếu không tạo được; `false` cũng có thể xảy ra vì thư mục đã tồn tại.
- `mkdirs()` không có nghĩa tạo file văn bản `october`; nó cố gắng tạo **thư mục** có tên đó.

Mẫu cho một file nằm trong thư mục chưa tồn tại:

```java
File file = new File("reports/2026/result.txt");
File parent = file.getParentFile();

if (parent != null && !parent.isDirectory()) {
    if (!parent.mkdirs() && !parent.isDirectory()) {
        throw new IOException("Cannot create parent directory");
    }
}
boolean created = file.createNewFile();
```

Đây là mảnh mã; cần `java.io.*`. Kiểm tra lần hai bằng `isDirectory()` giúp xử lý trường hợp một tác nhân khác vừa tạo thư mục trước khi `mkdirs()` trả về. Tuy vậy, filesystem có thể tiếp tục thay đổi giữa các lệnh; đoạn mã không loại bỏ mọi race.

NIO thường rõ hơn khi tạo nhiều cấp thư mục:

```java
Path folder = Path.of("reports", "2026");
Files.createDirectories(folder);
Path file = folder.resolve("result.txt");
Files.createFile(file);
```

Mảnh mã cần `java.nio.file.*` và xử lý `IOException`. `Files.createDirectories` không phải `Files.createFile`: lệnh đầu tạo **thư mục**, lệnh sau tạo **file thường**.

## 6. Ví dụ Java 17+ chạy độc lập và kiểm tra được

Ví dụ dùng **thư mục tạm riêng** để không phụ thuộc một file có sẵn trong thư mục dự án. Nó cho thấy điều gì xảy ra tại mỗi bước, sau đó xóa những tài nguyên đã tạo.

Lưu thành `FileObjectVsDiskDemo.java`:

```java
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileObjectVsDiskDemo {
    public static void main(String[] args) throws IOException {
        // Phương thức này thực sự tạo một thư mục tạm trên ổ đĩa.
        Path root = Files.createTempDirectory("file-path-demo-");

        // resolve(...) và toFile() chỉ chuẩn bị các đường dẫn/đối tượng.
        Path parent = root.resolve("reports").resolve("2026");
        Path name = parent.resolve("lesson.txt");
        Path generated = parent.resolve("generated.txt");

        try {
            File f = name.toFile();

            System.out.println("after new File: " + f.exists());
            System.out.println("parent exists: " + Files.exists(parent));

            System.out.println("mkdirs: " + parent.toFile().mkdirs());

            System.out.println("first createNewFile: " + f.createNewFile());
            System.out.println("second createNewFile: " + f.createNewFile());

            System.out.println("isFile: " + f.isFile());
            System.out.println("isDirectory: " + f.isDirectory());

            Files.writeString(generated, "Hello", StandardCharsets.UTF_8);
            System.out.println("writeString created: "
                    + Files.isRegularFile(generated));
            System.out.println("read: "
                    + Files.readString(generated, StandardCharsets.UTF_8));
        } finally {
            // Dọn từ con tới cha; deleteIfExists không tự xóa cây thư mục.
            Files.deleteIfExists(generated);
            Files.deleteIfExists(name);
            Files.deleteIfExists(parent);
            Files.deleteIfExists(root.resolve("reports"));
            Files.deleteIfExists(root);
        }
    }
}
```

Biên dịch/chạy:

```bash
javac FileObjectVsDiskDemo.java
java FileObjectVsDiskDemo
```

Stdout trong môi trường tệp tạm ghi được và không có tác nhân thay đổi đường dẫn giữa các thao tác:

```text
after new File: false
parent exists: false
mkdirs: true
first createNewFile: true
second createNewFile: false
isFile: true
isDirectory: false
writeString created: true
read: Hello
```

Giải thích:

1. `Files.createTempDirectory(...)` **thực sự tạo một thư mục tạm** để chứa ví dụ. Việc này khác với `new File(...)`.
2. `root.resolve(...)` tạo `Path` trỏ tới tên chưa tồn tại; `name.toFile()` tạo một đối tượng `File`. `exists()` vẫn là `false` vì chưa có tệp vật lý.
3. `parent.toFile().mkdirs()` tạo `reports` và `reports/2026`, nên kết quả là `true`.
4. `f.createNewFile()` lần đầu tạo file rỗng `lesson.txt` nên trả `true`; lần sau trả `false` vì mục đã có.
5. `f.isFile()` xác nhận file thường; `f.isDirectory()` là `false`.
6. `Files.writeString(generated, ...)` tạo file `generated.txt` và ghi byte của `"Hello"` theo UTF-8; `readString` đọc lại.
7. `finally` xóa file trước, thư mục con sau. Nếu một thao tác dọn dẹp gặp lỗi I/O, Java sẽ ném ngoại lệ; ví dụ không được xem là cơ chế dọn dẹp bền vững cho mọi hệ điều hành/điều kiện race.

Ví dụ được thiết kế với đường dẫn tạm mới, nhưng đầu ra vẫn phụ thuộc quyền tạo tệp/thư mục và hệ thống tệp không bị thay đổi đồng thời. Không mã hóa cứng đường dẫn tuyệt đối vào stdout để giữ bài kiểm thử dễ tái lập trên Windows, Linux và macOS.

## 7. Các cách tạo file đều có hành vi khác nhau

| Cách gọi | Có tự tạo file/thư mục vật lý không? | Nếu đường dẫn đã có |
| --- | --- | --- |
| `new File(path)` | **Không** | Chỉ tạo đối tượng đường dẫn |
| `Path.of(path)` | **Không** | Chỉ tạo đối tượng `Path` |
| `file.getAbsolutePath()` | **Không** | Chỉ trả chuỗi đường dẫn |
| `file.exists()` | **Không** | Chỉ thăm dò trạng thái |
| `file.createNewFile()` | **Có: file rỗng** nếu chưa có | Trả `false` |
| `Files.createFile(path)` | **Có: file rỗng** nếu chưa có | Thường ném `FileAlreadyExistsException` |
| `file.mkdir()` | **Có: một thư mục** nếu tạo được | Trả `false` |
| `file.mkdirs()` | **Có: chuỗi thư mục cần thiết** nếu tạo được | Có thể trả `false` |
| `Files.createDirectories(path)` | **Có: các thư mục cần thiết** | Thành công nếu đường dẫn đã là thư mục phù hợp |
| `new FileOutputStream(file)` | Có thể tạo/mở file để ghi | Mặc định truncate file đang có |
| `new FileOutputStream(file, true)` | Có thể tạo file nếu chưa có | Mở để ghi nối |
| `Files.writeString(path, text, UTF_8)` | Có thể tạo và ghi file | Mặc định truncate nội dung cũ |
| `File.createTempFile(...)` | **Có: tạo file tạm rỗng thực** | API chọn tên phù hợp theo cơ chế tạo tạm |
| `Files.createTempFile(...)` | **Có: tạo file tạm rỗng thực** | API chọn tên phù hợp theo cơ chế tạo tạm |
| `Files.createTempDirectory(...)` | **Có: tạo thư mục tạm thực** | API chọn tên phù hợp theo cơ chế tạo tạm |

Sự tương phản `new File(...)` với `File.createTempFile(...)` rất đáng nhớ: tên hai API cùng nhắc tới `File`, nhưng **constructor chỉ tạo đối tượng**, còn **factory `createTempFile` có tác dụng phụ lên hệ thống tệp**.

## 8. File có thể tồn tại nhưng không phải file thường

Giả sử một thư mục tên `data` đã tồn tại:

```java
File candidate = new File("data");
System.out.println(candidate.exists());
System.out.println(candidate.isFile());
System.out.println(candidate.isDirectory());
```

Mảnh mã chỉ minh họa. Trong điều kiện `data` là thư mục truy cập được, kết quả lần lượt là `true`, `false`, `true`.

Điều đó có nghĩa:

- `exists()` kiểm tra có tài nguyên tại đường dẫn, **không** kiểm tra loại mong muốn.
- Nếu chương trình cần đọc một file thường, hãy kiểm tra `isFile()` hoặc sử dụng API đọc với xử lý lỗi thích hợp.
- Nếu chương trình cần tạo file `data` nhưng đã có thư mục cùng tên, `createNewFile()` không thể biến thư mục đó thành file thường.
- Nếu chương trình cần tạo thư mục `data` nhưng đã có file thường cùng tên, `mkdirs()` không biến file đó thành thư mục.

Trong nhiều trường hợp, bạn không cần gọi trước `exists()` hay `isFile()`; thực hiện thao tác đọc/ghi đúng yêu cầu và xử lý `IOException` sẽ tránh được một khoảng race giữa kiểm tra và sử dụng.

## 9. Đường dẫn tương đối, thư mục làm việc và vị trí thật

`new File("output.txt")` tạo đối tượng với **đường dẫn tương đối**. Vị trí mà chương trình nhìn tới phụ thuộc thư mục làm việc hiệu dụng của JVM. Không được đồng nhất với thư mục chứa tệp `.java`, vị trí IDE hiển thị hay thư mục chứa `.class`.

Ví dụ:

```java
File f = new File("output.txt");
System.out.println(f.getPath());
System.out.println(f.getAbsolutePath());
System.out.println(f.getCanonicalPath());
```

Mảnh mã cần `java.io.File` và xử lý `IOException` từ `getCanonicalPath()`. Ba kết quả có thể khác về việc chứa đường dẫn tương đối, tuyệt đối và canonical. **Không có phương thức nào trên đây tạo file.**

Một số lưu ý:

- Tùy hệ điều hành, dấu phân cách đường dẫn có thể là `/` hoặc `\`. Đừng tự nối đường dẫn bằng ký tự không phù hợp khi có thể dùng `new File(parent, child)` hoặc `Path.resolve(...)`.
- `getAbsolutePath()` không cam kết mọi thành phần `.`/`..` đã được chuẩn hóa như đường dẫn thực. `getCanonicalPath()` trả dạng canonical theo quy tắc hệ thống và có thể cần tương tác với filesystem.
- `Path.toAbsolutePath()` và `Path.normalize()` cũng **không tạo file**. `normalize()` chủ yếu chuẩn hóa cú pháp và không thay thế xác thực tài nguyên thực.
- `Path.toRealPath()` khác: nó truy vấn filesystem và thường yêu cầu đường dẫn thực sự tồn tại. Nó cũng **không tạo tài nguyên**.
- Nếu chuỗi đường dẫn do người dùng cung cấp, phải kiểm tra phạm vi truy cập được cho phép ở ứng dụng; việc tạo `File` không đảm bảo đường dẫn an toàn hoặc nằm trong thư mục dự kiến.

## 10. Thư mục cha chưa tồn tại: lỗi thường gặp

Đây là lỗi rất điển hình:

```java
File file = new File("reports/2026/summary.txt");
file.createNewFile();
```

Nếu `reports/2026` chưa tồn tại, `createNewFile()` không có trách nhiệm tạo những thư mục đó; thao tác có thể ném `IOException`. Khi bài toán yêu cầu tạo file nằm trong cấu trúc thư mục mới, cần:

1. Xác định đúng đường dẫn cha.
2. Tạo/công nhận các thư mục cha bằng `mkdirs()` hoặc `Files.createDirectories(...)`.
3. Tạo file thực bằng `createNewFile()`/`Files.createFile(...)` hoặc mở stream với tùy chọn mong muốn.
4. Ghi nội dung và đóng tài nguyên, rồi kiểm tra kết quả hoặc xử lý ngoại lệ.

Ví dụ NIO:

```java
Path target = Path.of("reports", "2026", "summary.txt");
Files.createDirectories(target.getParent());
Files.writeString(target, "Báo cáo",
        StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
```

Đây là **mảnh mã**; cần `java.nio.file.*`, `java.nio.charset.StandardCharsets` và xử lý `IOException`. `StandardOpenOption.CREATE_NEW` yêu cầu tạo file mới và thất bại nếu đường dẫn đã có; cách viết này giúp tránh ghi đè dữ liệu hiện có. Nó khác hành vi mặc định của `Files.writeString` khi không có tùy chọn.

Nếu bạn chỉ muốn “tạo file rỗng và chắc chắn không ghi đè”, dùng `Files.createFile(target)` sau khi đã chuẩn bị thư mục cha. Nếu muốn “mở file để ghi và có thể ghi đè”, chọn rõ `CREATE`/`TRUNCATE_EXISTING` theo hợp đồng nghiệp vụ.

## 11. Kiểm tra tồn tại trước khi tạo không phải một transaction

Mã này có vấn đề nếu hai tiến trình đồng thời chạy:

```java
if (!file.exists()) {
    // Giữa hai dòng, tiến trình khác có thể tạo file cùng tên.
    Files.writeString(file.toPath(), "content",
            StandardCharsets.UTF_8);
}
```

Bên khác có thể tạo file **sau** `exists()` nhưng **trước** `writeString(...)`. Khi đó, vì cách ghi mặc định có thể truncate file hiện hữu, chương trình có nguy cơ ghi đè nội dung không thuộc về nó.

Nếu yêu cầu chỉ tạo khi chưa tồn tại, dùng thao tác create-new:

```java
try {
    Files.writeString(file.toPath(), "content",
            StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
} catch (FileAlreadyExistsException e) {
    // Xử lý trạng thái tên file đã được sử dụng.
}
```

Đây là mảnh mã cần import `java.nio.file.*`, `java.nio.charset.StandardCharsets`; vẫn phải xử lý các `IOException` khác.

`File.createNewFile()` cũng kết hợp kiểm tra và tạo file rỗng theo hợp đồng nguyên tử. Nhưng sau khi tạo file rỗng, nếu chương trình đóng mở lại để ghi, tiến trình khác vẫn có thể thay đổi filesystem trong khoảng đó. Muốn an toàn hơn, lựa chọn mở bằng `CREATE_NEW` để giữ đúng yêu cầu tạo và ghi ngay trong cùng thao tác mở phù hợp với provider.

Không dùng file tồn tại/không tồn tại để tạo cơ chế khóa liên tiến trình tự chế. Java có `FileLock` và những cơ chế phối hợp phù hợp hơn; ngay cả thao tác tạo mới nguyên tử cũng không thay thế được mọi yêu cầu đồng bộ/quyền truy cập.

## 12. Xóa đối tượng File không xóa tệp vật lý

Một nhầm lẫn ngược lại cũng rất phổ biến:

```java
File f = new File("report.txt");
f = null;
```

Việc bỏ tham chiếu tới `File` hoặc để đối tượng được garbage collector thu gom **không xóa file trên đĩa**, không đóng một luồng mở độc lập và không tác động lên đường dẫn thực. Tài nguyên file được thay đổi bởi những thao tác I/O cụ thể như `delete()`, `Files.delete(...)` hoặc ghi/move tương ứng.

Ví dụ:

```java
File f = new File("report.txt");
boolean deleted = f.delete();
```

`delete()` mới yêu cầu xóa mục tương ứng và trả `boolean`. Nó có thể thất bại, và không xóa đệ quy thư mục không rỗng. Với NIO, `Files.delete(path)` hoặc `Files.deleteIfExists(path)` cung cấp thao tác xóa phù hợp và có thể báo lỗi chi tiết hơn.

Cũng không nên gọi `deleteOnExit()` rồi nghĩ rằng tài nguyên đã bị xóa ngay. Đây chỉ là **yêu cầu xóa lúc JVM kết thúc bình thường**, không thay cho vòng đời tài nguyên và kiểm tra lỗi của thao tác xóa thực.

## 13. Tài nguyên và quyền sở hữu khi mở stream

Đối tượng `File` thường không cần `close()`, vì nó chỉ biểu diễn đường dẫn và bản thân không có phương thức `close()`. Nhưng các stream/reader/channel dùng để thao tác dữ liệu thật thường phải đóng:

```java
File f = new File("input.txt");
try (FileInputStream in = new FileInputStream(f)) {
    int firstByte = in.read();
    // Xử lý firstByte; -1 nghĩa EOF.
}
```

Đây là mảnh mã cần `java.io.*`, xử lý `IOException`. Constructor `FileInputStream` mở tài nguyên để **đọc** và có thể ném `FileNotFoundException` khi file không tồn tại/không thể đọc; **nó không tự tạo file bị thiếu**.

Ngược lại, constructor `FileOutputStream` dùng để **ghi** có thể tạo file mới. Vì vậy hai tên lớp gần giống nhau có hành vi tạo file khác nhau. Từ `new File(...)` không suy ra rằng file được mở cho đọc hoặc ghi.

Đặt try-with-resources quanh stream/reader để giải phóng handle ngay cả khi gặp ngoại lệ. Không cần tự ý đóng tài nguyên hệ thống dùng chung do thành phần khác sở hữu.

## 14. Chọn File hay Path/Files?

`java.io.File` vẫn phổ biến khi tương tác với API cũ, thư viện yêu cầu `File` hoặc khi học nền tảng I/O. Nhưng Java NIO có `java.nio.file.Path` và lớp tiện ích `Files` cung cấp các thao tác file rõ ràng hơn:

| Nhiệm vụ | java.io.File truyền thống | Java NIO |
| --- | --- | --- |
| Biểu diễn đường dẫn, chưa tạo tài nguyên | `new File("data.txt")` | `Path.of("data.txt")` |
| Kiểm tra tồn tại | `file.exists()` | `Files.exists(path)` |
| Kiểm tra file thường | `file.isFile()` | `Files.isRegularFile(path)` |
| Tạo file rỗng mới | `file.createNewFile()` | `Files.createFile(path)` |
| Tạo một thư mục | `file.mkdir()` | `Files.createDirectory(path)` |
| Tạo cả cây thư mục cha | `file.mkdirs()` | `Files.createDirectories(path)` |
| Ghi văn bản UTF-8 | `FileWriter` với charset phù hợp | `Files.writeString(path, text, UTF_8)` |
| Xóa đường dẫn | `file.delete()` | `Files.delete(path)` |
| Chuyển giữa hai loại | `file.toPath()` | `path.toFile()` trên default filesystem |

`Files.exists(...)` và các thao tác thăm dò NIO cũng có giới hạn về race/quyền truy cập. Khi thao tác cần phân biệt nguyên nhân thất bại, các phương thức NIO ném `IOException` cụ thể thường hữu ích hơn những boolean của `File`.

Một lý do khác để chọn NIO là quyền truy cập filesystem/metadata, đường dẫn biểu diễn symbolic link và các thao tác nâng cao có API chuyên biệt. Dù dùng loại nào, **đối tượng đường dẫn luôn khác với việc tạo tài nguyên trên ổ đĩa**.

## 15. Những lỗi phổ biến và cách sửa

| Sai lầm | Hậu quả | Cách xử lý |
| --- | --- | --- |
| `new File("a.txt")` rồi cho rằng `a.txt` đã tồn tại | Đọc/ghi thất bại khi file thiếu | Thực hiện thao tác tạo hoặc mở ghi phù hợp |
| Dựa vào `getAbsolutePath()` như bằng chứng file đã có | Nhầm chuỗi tên với trạng thái filesystem | `exists()`/`isFile()` khi cần thông tin tức thời |
| Dùng `createNewFile()` trên đường dẫn thiếu thư mục cha | Có thể ném `IOException` | `mkdirs()` / `Files.createDirectories(...)` trước |
| Dùng `mkdirs()` để tạo file văn bản | Tạo thư mục thay vì file | Tạo thư mục cha rồi tạo file thường |
| Bỏ qua boolean từ `createNewFile()` | Không biết file đã có hay mới tạo | Kiểm tra `true`/`false` hoặc dùng `Files.createFile` |
| Dùng `exists() == true` thay cho `isFile()` | Có thể xử lý thư mục như file | Kiểm tra loại hoặc để API đọc/ghi xác thực |
| Dùng `exists() == false` rồi ghi mặc định | Dễ race và có thể ghi đè file của tiến trình khác | `CREATE_NEW` hoặc tạo mới nguyên tử |
| Dùng `Files.writeString` mà không biết mặc định | Có thể truncate dữ liệu cũ | Chọn `CREATE_NEW`/`APPEND`/`TRUNCATE_EXISTING` theo yêu cầu |
| Cho rằng `FileInputStream` tạo file còn thiếu | Ném lỗi khi mở file để đọc | Tạo tài nguyên trước nếu nghiệp vụ đòi hỏi |
| Cho rằng `new FileOutputStream` chỉ tạo đối tượng trong bộ nhớ | Có thể tạo/truncate file thật ngay lúc mở | Quản lý đường dẫn và tuỳ chọn ghi rõ ràng |
| Quên đóng stream sau khi mở tệp | Rò handle và tài nguyên | Dùng try-with-resources |
| Xóa tham chiếu `File` rồi cho rằng file đã bị xóa | File thực vẫn tồn tại | Gọi `delete()`/`Files.delete(...)` nếu cần xóa |
| Dùng đường dẫn tương đối nhưng tưởng nằm cạnh source code | Đọc/ghi sai vị trí | Kiểm tra thư mục làm việc, dùng `Path`/đường dẫn tuyệt đối khi cần |
| Dùng boolean tồn tại làm kiểm tra an toàn cuối cùng | Kết quả có thể lỗi thời do race | Thao tác mở/tạo trực tiếp với error handling |
| Không kiểm tra ngoại lệ/quyền filesystem | Ghi báo thành công giả | Xử lý `IOException`, xác minh thao tác đã hoàn tất |

## 16. Câu hỏi tự kiểm tra

1. **`new File("hello.txt")` có tạo file rỗng trên ổ đĩa không?** Không. Nó tạo đối tượng đường dẫn.
2. **`File` có thể trỏ tới thư mục không?** Có. Dùng `isDirectory()` để thăm dò loại tài nguyên.
3. **Nếu file chưa tồn tại, `getAbsolutePath()` có trả đường dẫn được không?** Có. Nó tạo dạng đường dẫn tuyệt đối, không tạo file.
4. **`createNewFile()` trả gì nếu file đã có?** Trả `false`, không ghi đè nội dung.
5. **`Files.createFile(path)` khác gì khi file đã có?** Thường ném `FileAlreadyExistsException`.
6. **`mkdir()` và `mkdirs()` khác nhau như thế nào?** `mkdir()` tạo một cấp thư mục, `mkdirs()` cố tạo cả các thư mục cha cần thiết.
7. **Vì sao `new File("a/b/c.txt").createNewFile()` có thể thất bại?** Vì thư mục cha `a/b` chưa có hoặc không thể truy cập.
8. **`new FileOutputStream(file)` có thể tạo file mới không?** Có, khi mở đích ghi với đường dẫn/quyền hợp lệ; nếu đã có có thể truncate.
9. **`new FileInputStream(file)` có tự tạo file thiếu không?** Không. Đây là thao tác đọc, có thể báo `FileNotFoundException`.
10. **Có nên kiểm tra `!exists()` rồi gọi `Files.writeString` để bảo đảm không ghi đè?** Không. Có race. Dùng `CREATE_NEW` khi yêu cầu chỉ tạo mới.
11. **`File` bị garbage collector thu gom có xóa file thật không?** Không.
12. **Tạo file tạm bằng `File.createTempFile(...)` có tạo tệp vật lý không?** Có. Đây là factory thực hiện thao tác tạo, khác với constructor `new File(...)`.

## Nguồn API Java 17

- [`java.io.File`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/File.html): constructor, `createNewFile`, `mkdir`, `mkdirs`, `exists`, `delete`.
- [`java.nio.file.Path`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html): biểu diễn và thao tác đường dẫn.
- [`java.nio.file.Files`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html): tạo/ghi/đọc/xóa tệp và thư mục.
- [`java.nio.file.StandardOpenOption`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardOpenOption.html): `CREATE_NEW`, `CREATE`, `APPEND` và `TRUNCATE_EXISTING`.
- [`java.io.FileOutputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/FileOutputStream.html): mở và ghi luồng byte, có thể tạo file.
- [`java.io.FileInputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/FileInputStream.html): mở và đọc luồng byte.
- [`java.io.IOException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/IOException.html): xử lý lỗi khi thao tác filesystem.

**Ghi nhớ:** `new File(path)` chỉ nói với JVM **đường dẫn nào đang được nhắc đến**. `createNewFile()`, `mkdir()/mkdirs()`, `Files.createFile(...)`, `Files.writeString(...)` và các thao tác mở ghi mới là những hành động có thể **tạo hoặc thay đổi tài nguyên thật trên hệ thống tệp**. Chọn đúng thao tác theo việc cần tạo file, tạo thư mục, chỉ đọc, ghi mới hay ghi đè và luôn xử lý lỗi I/O.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="legacy-file-io-003">Đường dẫn tuyệt đối, tương đối và thư mục làm việc</a>

<details>
<summary>Xem chi tiết</summary>

# Đường dẫn tuyệt đối, tương đối và thư mục làm việc trong Java

## Mục tiêu

Trong Java I/O, một đường dẫn như `data/report.txt` chỉ xác định được vị trí thực tế khi biết **thư mục làm việc** hoặc một thư mục gốc được chỉ định. Đó là lý do cùng một chương trình đôi khi đọc file thành công trong IDE nhưng báo không tìm thấy file khi chạy từ terminal, JAR, Gradle hay container.

Sau bài này, bạn sẽ biết:

- Phân biệt đường dẫn **tuyệt đối** và **tương đối**, và diễn giải đường dẫn tương đối dựa trên working directory.
- Hiểu `System.getProperty("user.dir")` và tại sao thư mục đặt `.java`, `.class` hay `.jar` không mặc nhiên là thư mục làm việc.
- Dùng `java.io.File`: `getPath()`, `isAbsolute()`, `getAbsolutePath()`, `getCanonicalPath()` và các phép kiểm tra tồn tại.
- Dùng `java.nio.file.Path` và `Files` để ghép đường dẫn, chuẩn hóa, mở file và xử lý lỗi.
- Biết khi nào cần `toAbsolutePath()`, `normalize()`, `toRealPath()`; tránh nhầm lẫn với symbolic link và kiểm tra bảo mật.
- Chạy ví dụ Java 17+ trên nhiều hệ điều hành mà không phụ thuộc đường dẫn tuyệt đối của một máy cụ thể.

## 1. Đường dẫn tuyệt đối và tương đối

Đường dẫn (*path*) mô tả vị trí file hoặc thư mục trong một filesystem. **Tạo đối tượng đường dẫn không có nghĩa file đã tồn tại hay đã được mở.**

- **Tuyệt đối (absolute path):** mang đủ thông tin vị trí từ gốc do hệ điều hành/filesystem quy định. Ví dụ `/opt/demo/data/report.txt` trên Linux hoặc `C:\projects\demo\data\report.txt` trên Windows.
- **Tương đối (relative path):** cần một thư mục cơ sở (base directory). Ví dụ `data/report.txt`, `reports/2026.txt`, `../config/app.properties`.
- **Thư mục làm việc (working directory):** thư mục mà tiến trình Java thường sử dụng để diễn giải đường dẫn tương đối trên filesystem mặc định.

Giả sử working directory là `/home/learner/demo`:

```text
Working directory: /home/learner/demo

Đường dẫn tương đối: data/report.txt
Đường dẫn được giải: /home/learner/demo/data/report.txt

Đường dẫn tương đối: ../config/app.properties
Sau khi giải và chuẩn hóa:
                    /home/learner/config/app.properties
```

Một file tương đối có thể trỏ đến nơi khác khi chương trình được khởi động từ thư mục khác. Ngược lại, đường dẫn tuyệt đối không cần dựa vào working directory khi xác định địa chỉ trên filesystem tương ứng.

**Tuyệt đối không đồng nghĩa tồn tại:** `Path.of("/opt/demo/missing.txt").isAbsolute()` có thể là `true` ngay cả khi file không tồn tại. Việc đọc vẫn có thể gặp `NoSuchFileException` hoặc lỗi quyền truy cập.

### Khác biệt cú pháp giữa hệ điều hành

| Nền tảng/kiểu | Ví dụ | Lưu ý |
| --- | --- | --- |
| Unix/Linux/macOS | `/var/log/app.log` | Bắt đầu từ gốc `/`. |
| Unix tương đối | `./data/input.txt` | Dấu `.` chỉ thư mục hiện tại. |
| Windows có ổ đĩa | `C:\apps\demo\input.txt` | Có drive và gốc của drive. |
| Windows UNC | `\\server\share\input.txt` | Có thể trỏ đến chia sẻ mạng. |
| Windows đặc biệt | `C:notes.txt` | Là đường dẫn tương đối theo ngữ cảnh hiện tại của drive C:, **khác** `C:\notes.txt`. |

Đừng tự kết luận tính tuyệt đối bằng `path.startsWith("/")` hoặc tìm dấu hai chấm. `C:\data` trên Linux có thể chỉ là một tên đường dẫn tương đối chứa các ký tự thông thường. Dùng `File.isAbsolute()` hoặc `Path.isAbsolute()` theo filesystem của môi trường đang chạy.

## 2. Thư mục làm việc đến từ đâu?

Working directory là ngữ cảnh được môi trường khởi chạy thiết lập cho tiến trình:

- **Terminal:** thư mục đang đứng khi gọi `java`, trừ khi cách khởi chạy thay đổi nó.
- **IDE:** cấu hình *Working directory* trong Run/Debug Configuration.
- **Gradle/Maven:** tùy tác vụ, plugin và working directory cấu hình cho tiến trình chạy.
- **Container:** giá trị `WORKDIR` hoặc working directory do runtime/container định nghĩa.
- **System service / scheduler:** cấu hình dịch vụ, không nhất thiết là thư mục chứa JAR.

Java thường cung cấp thuộc tính:

```java
String cwd = System.getProperty("user.dir");
System.out.println(cwd);
```

Đây là **snippet** trong phương thức Java. Kết quả là đường dẫn của môi trường chạy, nên không có một stdout chung cho mọi máy.

Ví dụ, khi thực hiện `java -jar /opt/tools/app.jar` từ `/home/learner`, chương trình có thể có working directory `/home/learner` mặc dù JAR nằm ở `/opt/tools`. Khi đó:

```text
new File("config/app.properties")
    -> /home/learner/config/app.properties
    (nếu working directory là /home/learner)
```

Thư mục chứa mã nguồn, thư mục chứa tệp lớp, đường dẫn classpath và vị trí JAR là **những khái niệm khác working directory**.

**Không nên đổi working directory bằng `System.setProperty("user.dir", "...")`.** Thay thuộc tính Java không phải một cách tổng quát, đáng tin cậy để đổi current directory của tiến trình OS và có thể làm các phần ứng dụng hiểu khác nhau. Khi muốn làm việc theo một thư mục nhất định, hãy lưu `Path base` và gọi `base.resolve(...)` rõ ràng.

## 3. API truyền thống: `java.io.File`

`File` biểu diễn tên đường dẫn đến file hoặc thư mục; constructor không tự tạo file, không tự mở stream. Các phương thức quan trọng:

| Phương thức | Hành vi |
| --- | --- |
| `new File("data", "report.txt")` | Tạo đối tượng đường dẫn con của thư mục `data`. |
| `getPath()` | Trả biểu diễn đường dẫn hiện tại; có thể tương đối. |
| `isAbsolute()` | Kiểm tra đường dẫn có tuyệt đối trên nền tảng hiện tại. |
| `getAbsolutePath()` | Trả dạng đường dẫn tuyệt đối; không đòi hỏi file tồn tại. |
| `getAbsoluteFile()` | Trả đối tượng `File` chứa đường dẫn tuyệt đối. |
| `getCanonicalPath()` | Trả đường dẫn canonical theo filesystem; có thể truy cập filesystem và ném `IOException`. |
| `getName()` | Tên thành phần cuối của đường dẫn. |
| `getParentFile()` | Đường dẫn thư mục cha được thể hiện trong đối tượng, có thể `null`. |
| `exists()` | Kiểm tra tồn tại tại thời điểm gọi; không đảm bảo thao tác sau sẽ thành công. |
| `isFile()` / `isDirectory()` | Kiểm tra loại đối tượng, không suy từ đuôi file. |
| `toPath()` | Chuyển sang API `Path`. |

### Ví dụ Java 17+: đường dẫn tương đối không cần file thật

Lưu thành **`FilePathDemo.java`**:

```java
import java.io.File;
import java.nio.file.Path;

public class FilePathDemo {
    public static void main(String[] args) {
        File relative = new File("data", "report.txt");
        File absolute = relative.getAbsoluteFile();

        System.out.println("relative.isAbsolute = " + relative.isAbsolute());
        System.out.println("absolute.isAbsolute = " + absolute.isAbsolute());
        System.out.println("file name = " + relative.getName());
        System.out.println("parent name = " + relative.getParentFile().getName());

        Path cleaned = Path.of("output", "..", "data", "report.txt")
                .normalize();
        System.out.println("normalized first = " + cleaned.getName(0));
        System.out.println("normalized last = " + cleaned.getFileName());
        System.out.println("normalized absolute = "
                + cleaned.toAbsolutePath().isAbsolute());
    }
}
```

Chạy:

```bash
javac FilePathDemo.java
java FilePathDemo
```

Kết quả trên hệ thống tệp mặc định thông thường:

```text
relative.isAbsolute = false
absolute.isAbsolute = true
file name = report.txt
parent name = data
normalized first = data
normalized last = report.txt
normalized absolute = true
```

Các giá trị trên không in nguyên địa chỉ gốc nên không phụ thuộc tên thư mục làm việc của từng máy. Tạo `File` và `Path` không yêu cầu `data/report.txt` hay `output` tồn tại. Dấu `..` trong `Path` được rút gọn **từ vựng** khi gọi `normalize()`.

## 4. Từ `File` sang `Path` và `Files`

Java NIO.2 cung cấp `java.nio.file.Path` để diễn tả đường dẫn và `java.nio.file.Files` để thực hiện I/O. Trong Java hiện đại nên ưu tiên cách này cho nghiệp vụ mới, đồng thời hiểu `File` khi làm việc với API cũ.

| Nhu cầu | `File` | `Path` / `Files` |
| --- | --- | --- |
| Xây path | `new File("a", "b.txt")` | `Path.of("a", "b.txt")` |
| Kiểm tra tuyệt đối | `isAbsolute()` | `isAbsolute()` |
| Lấy đường dẫn tuyệt đối | `getAbsolutePath()` | `toAbsolutePath()` |
| Chuẩn hóa `.`, `..` theo cú pháp | Dùng canonical khi thích hợp | `normalize()` không cần I/O |
| Giải đường dẫn theo base | `new File(parent, child)` | `base.resolve(child)` |
| Tìm đường dẫn tương đối từ base tới target | Không có API tương đương trực tiếp | `base.relativize(target)` nếu tương thích |
| Đường dẫn thực của file hiện tồn tại | Kiểm tra tồn tại + canonical theo nhu cầu | `toRealPath()` |
| Kiểm tra tồn tại | `exists()` | `Files.exists(path)` |
| Tạo thư mục cha | `mkdir()` / `mkdirs()` | `Files.createDirectories(path)` |
| Đọc/ghi text | Stream/Reader/Writer kết hợp | `Files.readString` / `Files.writeString` |
| Lỗi I/O | Nhiều phương thức trả boolean | Phần lớn phép I/O ném `IOException` |

### `resolve` và `relativize`

`base.resolve(child)` thông thường ghép `child` tương đối vào `base`. Nếu `child` là **absolute path**, nó không bị buộc nằm dưới `base`; đừng dùng `resolve` làm cơ chế bảo vệ quyền truy cập.

`base.relativize(target)` tìm đường dẫn tương đối từ `base` đến `target` khi hai đường dẫn có kiểu/gốc tương thích. Đừng mong phương thức này tự truy cập filesystem hoặc tự xác minh target tồn tại.

Ví dụ:

```java
// Snippet minh họa Path, không làm I/O:
Path base = Path.of("workspace");
Path child = base.resolve("notes").resolve("today.txt");
Path relative = base.relativize(child); // notes/today.txt
```

Dấu phân cách hiển thị phụ thuộc hệ điều hành; `relativize` và `resolve` làm việc ở cấp thành phần đường dẫn, tránh việc tự ghép chuỗi.

### `toAbsolutePath()`, `normalize()` và `toRealPath()` không giống nhau

- `toAbsolutePath()`: chuyển thành đường dẫn tuyệt đối dựa vào ngữ cảnh filesystem, **không yêu cầu file có thật**.
- `normalize()`: rút gọn các thành phần `.` và `..` theo cú pháp, **không đọc filesystem**.
- `toRealPath()`: mặc định cần đối tượng tồn tại, xác minh và giải quyết symbolic link theo provider, có thể ném `IOException`.
- `toRealPath(LinkOption.NOFOLLOW_LINKS)`: dùng quy tắc khác với symbolic link, không mặc định theo chúng khi xác định đường dẫn thực.
- `File.getCanonicalPath()`: chuẩn hóa tên đường dẫn theo quy tắc file system, có thể giải symlink và có thể trả canonical path khi đích chưa tồn tại; **không hoàn toàn đồng nghĩa** với `Path.toRealPath()`.

Một đường dẫn có `someLink/..` có thể mang nghĩa khác nhau sau khi filesystem giải symbolic link. `normalize()` rút gọn theo từ vựng nên **không đủ để xác minh đối tượng mà I/O sẽ thực sự tác động**.

## 5. Ví dụ Java 17+: tạo file trong thư mục tạm

Ví dụ dưới đây tạo một thư mục tạm riêng, ghi một file UTF-8, kiểm chứng `normalize` và `toRealPath` rồi xóa những đối tượng đã tạo. Nó không cần cố định đường dẫn tuyệt đối kiểu `C:\temp` hoặc `/tmp`.

Lưu thành **`TemporaryFilePathDemo.java`**:

```java
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class TemporaryFilePathDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("path-lesson-");
        Path reports = root.resolve("reports");
        Path file = reports.resolve("daily.txt");

        try {
            Files.createDirectories(reports);
            Files.writeString(file, "Xin chao", StandardCharsets.UTF_8);

            Path withDots = root.resolve("reports")
                    .resolve(".")
                    .resolve("..")
                    .resolve("reports")
                    .resolve("daily.txt");

            System.out.println("file exists = " + Files.exists(file));
            System.out.println("regular file = " + Files.isRegularFile(file));
            System.out.println("same normalized path = "
                    + withDots.normalize().equals(file.normalize()));
            System.out.println("real path matches = "
                    + file.toRealPath().equals(withDots.toRealPath()));
            System.out.println("read text = "
                    + Files.readString(file, StandardCharsets.UTF_8));

            File legacy = file.toFile();
            System.out.println("File name = " + legacy.getName());
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(reports);
            Files.deleteIfExists(root);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac TemporaryFilePathDemo.java
java TemporaryFilePathDemo
```

Stdout dự kiến trong filesystem mặc định hỗ trợ tạo/xóa file và thư mục:

```text
file exists = true
regular file = true
same normalized path = true
real path matches = true
read text = Xin chao
File name = daily.txt
```

**Giải thích:**

1. `Files.createTempDirectory` trả một thư mục tạm do Java tạo theo cấu hình runtime.
2. `root.resolve("reports").resolve("daily.txt")` xác định rõ file bên dưới base, không phụ thuộc working directory khi đã có `root`.
3. `Files.createDirectories` tạo thư mục cha trước khi ghi; `Files.writeString` không tự tạo mọi thư mục cha.
4. `withDots.normalize()` rút gọn chuỗi path, còn `toRealPath()` truy cập filesystem để xác minh đường dẫn thực của file đã tồn tại.
5. `Files.deleteIfExists` dọn dữ liệu đã tạo từ file đến thư mục cha. Nếu có dữ liệu ngoài ý muốn được tạo đồng thời trong thư mục, xóa thư mục có thể thất bại; chương trình không xóa đệ quy vô điều kiện.
6. `Path.toFile()` hữu ích để tương tác với API cũ trên filesystem mặc định; với một số filesystem provider đặc biệt nó có thể không được hỗ trợ.

Nếu chạy trong môi trường không cho tạo file tạm hoặc lỗi quyền, thao tác sẽ ném `IOException`. Đó là giới hạn môi trường I/O thực tế; không nên giả lập thành công.

## 6. Vì sao cùng code chạy từ IDE và terminal cho kết quả khác?

Giả sử ứng dụng viết:

```java
Path config = Path.of("config", "app.properties");
String content = Files.readString(config);
```

Đây là **snippet**: cần import `Path`/`Files`, xử lý `IOException` và có file thật. Với cùng đường dẫn tương đối:

| Working directory | File thực tế sẽ được tìm |
| --- | --- |
| `/home/alice/project` | `/home/alice/project/config/app.properties` |
| `/home/alice` | `/home/alice/config/app.properties` |
| `/srv/app` | `/srv/app/config/app.properties` |

Cách chẩn đoán:

1. In hoặc log `System.getProperty("user.dir")`.
2. Log `config.toAbsolutePath().normalize()` để xác định vị trí được tìm.
3. Xác minh cấu hình working directory ở IDE, build tool, container hoặc service.
4. Kiểm tra quyền truy cập và thao tác mở file thật. `Files.exists` có thể trả `false` nếu lỗi I/O/không xác định được trạng thái; nó không bảo đảm lần đọc tiếp theo sẽ thành công.
5. Nếu ứng dụng cần thư mục gốc cố định, nhận giá trị đó qua cấu hình rõ ràng (tham số CLI, environment, config file đáng tin), sau đó `base.resolve(...)`.

### Tài nguyên nằm trong JAR không phải file bên cạnh JAR

Nếu `defaults.properties` được đóng gói trong classpath/JAR, thay vì giả định nó vẫn tồn tại dưới thư mục `src/main/resources` sau khi build, hãy dùng classpath resource:

```java
// Snippet; MyApp là class thật trong ứng dụng.
try (java.io.InputStream in =
        MyApp.class.getResourceAsStream("/config/defaults.properties")) {
    if (in == null) {
        throw new java.io.FileNotFoundException("resource not found");
    }
    // Đọc nội dung từ in.
}
```

Đoạn trên chưa phải chương trình độc lập vì class `MyApp` chỉ là tên minh họa. Trong ứng dụng thực tế, thay bằng class tồn tại và xử lý/khai báo `IOException`. Resource bên trong JAR được đọc qua class loader; path của source code không tự trở thành path file trong runtime.

## 7. Đừng nối đường dẫn bằng dấu phân cách tự viết

Cách nối chuỗi kiểu Windows:

```java
// Ví dụ không nên áp dụng xuyên hệ điều hành.
String filePath = base + "\\" + folder + "\\" + filename;
```

là một snippet giả định các biến `base`, `folder`, `filename` đã tồn tại. Nó dễ sai trên hệ điều hành dùng dấu phân cách khác, dễ sinh ký tự thừa và khó xử lý các dạng path đặc biệt.

Thay bằng:

```java
// Snippet; cần import java.nio.file.Path
Path file = Path.of(base).resolve(folder).resolve(filename);
```

Hoặc dùng API cũ:

```java
// Snippet; base và filename là chuỗi do ứng dụng cung cấp.
File file = new File(new File(base), filename);
```

Phân biệt:

- `File.separator`: dấu phân cách **thành phần đường dẫn**, ví dụ forward slash trên Unix hoặc backslash trên Windows.
- `File.pathSeparator`: dấu phân cách **nhiều path trong một danh sách**, thường `:` trên Unix, `;` trên Windows (ví dụ classpath).

Đối với file trong thư mục con, `Path.of("data", "report.txt")` thường đơn giản và ít lỗi hơn thao tác chuỗi.

## 8. Đường dẫn từ người dùng và path traversal

Khi một đường dẫn đến từ tên file upload, URL, archive entry hoặc input CLI, đối tượng bên ngoài có thể cung cấp path chứa `..` hoặc path tuyệt đối. Nếu ghép mà không kiểm tra, ứng dụng có thể truy cập ngoài thư mục dự kiến:

```text
Thư mục được phép: /srv/app/uploads
Đầu vào:           ../../etc/secret
Sau giải từ vựng:  /srv/etc/secret
```

Từ thư mục `/srv/app/uploads`, hai bước đi lên đưa path về `/srv`, rồi ghép `etc/secret`. Điều nguy hiểm là path đã thoát vùng `uploads`.

Để minh họa **kiểm tra cú pháp ban đầu**, có thể dùng `Path.startsWith(Path)` thay vì so sánh chuỗi. Lưu thành **`LexicalPathGuard.java`**:

```java
import java.nio.file.Path;

public class LexicalPathGuard {
    static Path resolveUnder(Path base, String input) {
        Path supplied = Path.of(input);
        if (supplied.isAbsolute()) {
            throw new IllegalArgumentException("absolute input rejected");
        }

        Path root = base.toAbsolutePath().normalize();
        Path target = root.resolve(supplied).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("outside allowed directory");
        }
        return target;
    }

    public static void main(String[] args) {
        Path base = Path.of("uploads");
        System.out.println(resolveUnder(base, "2026/report.txt")
                .getFileName());

        try {
            resolveUnder(base, "../secret.txt");
        } catch (IllegalArgumentException e) {
            System.out.println("traversal rejected");
        }
    }
}
```

Chạy:

```bash
javac LexicalPathGuard.java
java LexicalPathGuard
```

Kết quả:

```text
report.txt
traversal rejected
```

**Cảnh báo về giới hạn của ví dụ:** hàm trên chỉ kiểm tra các **thành phần tên đường dẫn**, không đủ bảo vệ nếu trong thư mục có symbolic link, junction/reparse point, mount, hard link hoặc đối tượng thay đổi giữa lúc kiểm tra và lúc mở. Với thao tác nhạy cảm, cần thiết kế quyền truy cập phù hợp; kiểm tra đường dẫn thực của file/cha hiện hữu theo chính sách, xử lý khả năng thay đổi đồng thời, và cân nhắc các API mở file gắn với thư mục đáng tin như `SecureDirectoryStream` nơi filesystem provider hỗ trợ. Đừng quảng bá `normalize().startsWith(root)` là bảo vệ path traversal đầy đủ.

`Path.startsWith(Path)` kiểm tra **thành phần path**, không kiểm tra tiền tố chuỗi. Ví dụ `/safe/app2` không phải con của `/safe/app` dù chuỗi chữ bắt đầu giống nhau.

## 9. Những nhầm lẫn thường gặp

1. **Tưởng path tương đối tính từ thư mục chứa file Java.** Nó thường phụ thuộc working directory của tiến trình hoặc base được truyền vào API.
2. **Tưởng `getAbsolutePath()` kiểm tra file đã có.** Nó chỉ trả địa chỉ tuyệt đối, không mở file.
3. **Tưởng `normalize()` tương đương `toRealPath()`.** Một cái rút gọn cú pháp; một cái mặc định xác minh file hiện hữu và xử lý symlink.
4. **Tưởng `getCanonicalPath()` luôn cần file tồn tại.** Nó có thể đưa ra đường dẫn canonical ngay cả khi file đích không tồn tại, tùy filesystem.
5. **Tưởng `Files.exists() == false` luôn nghĩa file chắc chắn không có.** Có thể thiếu quyền hoặc xảy ra lỗi; khi cần đọc, hãy mở và xử lý ngoại lệ.
6. **Tưởng thay `user.dir` là đổi cwd của mọi API.** Hãy lưu và truyền base directory tường minh.
7. **Tưởng slash/backslash có cùng nghĩa trên mọi nền tảng.** Hãy dùng `Path.of` và `resolve`.
8. **Tưởng path của resource đóng gói trong JAR là path của một file trên ổ đĩa.** Dùng API classpath resource khi phù hợp.
9. **Tưởng kiểm tra có tồn tại trước thì thao tác sau chắc chắn thành công.** File có thể bị thay đổi hoặc quyền bị thu hồi; vẫn phải xử lý `IOException`.
10. **Tưởng `resolve(userInput)` sẽ giữ input trong base.** Absolute path, `..` và symlink có thể làm mất ranh giới mong muốn; cần validation và cơ chế filesystem an toàn.
11. **Tưởng tên có đuôi `.txt` nghĩa là file thường.** Cần kiểm tra loại đối tượng, không dựa vào phần mở rộng.
12. **Không kiểm tra quyền sở hữu file tạm và dọn dẹp.** Với test/ứng dụng tạm thời, nên tạo file trong thư mục tạm riêng, xử lý lỗi và xóa đúng các file mình tạo.

## 10. Bảng lựa chọn nhanh

| Mục đích | API/hướng xử lý |
| --- | --- |
| Kiểm tra path tuyệt đối | `File.isAbsolute()` / `Path.isAbsolute()` |
| Hiển thị working directory | `System.getProperty("user.dir")` |
| Hiển thị vị trí dự kiến đọc file | `path.toAbsolutePath().normalize()` |
| Xác định file dưới một thư mục cấu hình | `base.resolve("sub").resolve("file.txt")` |
| Rút gọn `.` và `..` mà không làm I/O | `Path.normalize()` |
| Xác minh đường dẫn thực của file hiện có | `Path.toRealPath()` và xử lý `IOException` |
| Tương thích thư viện chỉ nhận `File` | `path.toFile()` nếu provider hỗ trợ |
| Tạo thư mục cha trước khi ghi | `Files.createDirectories(path.getParent())` khi parent hợp lệ |
| Tài nguyên đi kèm JAR | Classpath stream thay vì path dựa vào thư mục source |
| File cấu hình bên ngoài | Đường dẫn/base truyền qua cấu hình chạy |
| Dữ liệu đường dẫn không tin cậy | Chính sách validate + kiểm soát symlink/race và quyền filesystem |
| Bài kiểm thử thao tác file | `Files.createTempDirectory` và dọn dữ liệu trong `finally` |

## 11. Câu hỏi tự kiểm tra

**Câu 1.** Chạy JAR đặt tại `/opt/apps/app.jar` từ thư mục `/home/alice`. `new File("data.txt")` thường trỏ đến đâu?

**Trả lời:** `/home/alice/data.txt` khi working directory của tiến trình là `/home/alice`. Vị trí JAR không mặc định quyết định đường dẫn tương đối.

**Câu 2.** Gọi `new File("missing.txt").getAbsolutePath()` có tạo file không?

**Trả lời:** Không. Nó chỉ cho biểu diễn địa chỉ tuyệt đối.

**Câu 3.** `Path.of("a", "..", "b.txt").normalize()` có cần file `a` tồn tại không?

**Trả lời:** Không. Đây là phép biến đổi tên đường dẫn theo cú pháp, không truy cập filesystem.

**Câu 4.** `Path.toRealPath()` khác `toAbsolutePath()` ở đâu?

**Trả lời:** `toAbsolutePath()` xây path tuyệt đối mà không cần file có thật; `toRealPath()` mặc định kiểm tra tồn tại và giải quyết path thực, có thể ném `IOException`.

**Câu 5.** Dùng `File.pathSeparator` để nối thư mục `data` và file `report.txt` được không?

**Trả lời:** Không. `pathSeparator` tách danh sách nhiều path; dùng `Path.of("data", "report.txt")` hoặc `File.separator` khi thực sự cần chuỗi.

**Câu 6.** Vì sao `root.resolve(input).normalize().startsWith(root)` chưa phải kiểm tra bảo mật đầy đủ?

**Trả lời:** Nó chỉ kiểm tra tên path từ vựng. Symlink và thay đổi file đồng thời vẫn có thể khiến thao tác I/O thực tế trỏ ra ngoài vùng dự kiến.

**Câu 7.** File cấu hình đã được đóng gói thành resource bên trong JAR nên được mở từ đâu?

**Trả lời:** Từ classpath resource API như `getResourceAsStream`, không mặc nhiên bằng path `src/main/resources` tương đối với working directory.

## Kết luận

**Path tương đối + base directory = vị trí chương trình sẽ tìm đến.** Với chương trình chạy bằng IDE, Gradle, Docker hay JAR, hãy xác định working directory trước khi chẩn đoán lỗi “không tìm thấy file”. `File` giúp hiểu hệ API truyền thống; `Path`/`Files` giúp ghép, chuyển đổi, kiểm tra và thao tác file minh bạch hơn.

Khi cần đường dẫn ổn định, hãy cấu hình **base path tường minh**, dùng `resolve` để tạo path con, phân biệt rõ `toAbsolutePath()`, `normalize()` với `toRealPath()`, và xử lý ngoại lệ I/O thay vì suy đoán sự tồn tại của file từ tên đường dẫn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="legacy-file-io-004">getPath, getAbsolutePath và getCanonicalPath</a>

<details>
<summary>Xem chi tiết</summary>

# `File.getPath()`, `getAbsolutePath()` và `getCanonicalPath()` trong Java

## 1. Mục tiêu học tập

`java.io.File` là API truyền thống để biểu diễn **tên đường dẫn trừu tượng** (abstract pathname) tới một tệp hoặc thư mục. Khi tạo một đối tượng `File`, Java chưa nhất thiết mở, tạo hay kiểm tra sự tồn tại của tệp trên đĩa. Một đường dẫn có thể là **tương đối**, **tuyệt đối** hoặc được chuyển sang dạng **canonical** theo quy tắc của hệ thống tệp.

Ba phương thức thường gây nhầm lẫn:

- `getPath()`: trả về **chuỗi đường dẫn mà đối tượng `File` đang lưu**.
- `getAbsolutePath()`: trả về **đường dẫn tuyệt đối**, nhưng không bắt buộc xóa `.`, `..` hoặc giải quyết symbolic link.
- `getCanonicalPath()`: chuyển sang **dạng chuẩn tắc theo hệ thống tệp**, thường loại bỏ thành phần dư thừa và giải quyết liên kết tượng trưng khi có thể; có thể ném `IOException`.

Bài học thuộc nhóm **File truyền thống** trong Java Core I/O. Sau bài này, bạn cần biết chính xác ý nghĩa từng phương thức, khả năng phụ thuộc hệ điều hành, vấn đề về file chưa tồn tại, cách so sánh hai đường dẫn và vì sao canonical path **không tự đủ** để bảo vệ ứng dụng trước path traversal hoặc race condition.

> **Ý chính:** `File` biểu diễn *đường dẫn*, không phải tay nắm (handle) gắn bất biến với một file vật lý. Việc chuẩn hóa tên đường dẫn không chứng minh tài nguyên vẫn là cùng một file ở thời điểm sử dụng.

## 2. Khái niệm nền tảng: relative, absolute và canonical

Một đường dẫn tương đối được hiểu dựa trên **thư mục làm việc hiện tại** của quá trình Java. Ví dụ:

```text
Tương đối:             reports/../notes.txt
Thư mục làm việc:      /home/learner
Tuyệt đối (minh họa): /home/learner/reports/../notes.txt
Canonical (minh họa): /home/learner/notes.txt
```

Đây chỉ là minh họa đường dẫn kiểu Unix. Trên Windows, đường dẫn thường dùng dấu `\`, ký hiệu ổ đĩa hoặc UNC; canonical hóa còn có thể thay đổi cách viết hoa/thường của một số thành phần tùy hệ thống tệp và JDK. Không nên viết bài kiểm thử đòi hỏi mọi máy in cùng một chuỗi đường dẫn.

- **Relative pathname:** thiếu thông tin gốc tuyệt đối, cần ngữ cảnh để biết điểm xuất phát.
- **Absolute pathname:** chỉ định vị trí không cần ghép thêm working directory.
- **Canonical pathname:** dạng đại diện chuẩn hóa theo quy tắc hệ thống; có thể phải hỏi hệ thống tệp về symbolic link, định dạng tên và những thành phần đang tồn tại.

Một đường dẫn **tuyệt đối** vẫn có thể chứa `.` hoặc `..`. Vì thế **absolute và canonical không phải từ đồng nghĩa**.

### 2.1. So sánh nhanh ba API

| Phương thức | Kết quả | Có dùng thư mục làm việc khi nguồn tương đối? | Loại bỏ `.` / `..`? | Giải quyết symbolic link? | Checked exception? |
| --- | --- | --- | --- | --- | --- |
| `getPath()` | Chuỗi path đang lưu trong `File` | Không cần để tạo chuỗi trả về | Không được cam kết; có thể có chuẩn hóa dấu phân cách ban đầu | Không | Không |
| `getAbsolutePath()` | Chuỗi path tuyệt đối | Có, khi cần chuyển relative thành absolute | Không được cam kết | Không | Không |
| `getCanonicalPath()` | Chuỗi canonical theo filesystem | Có, nếu nguồn relative | Thường có, theo quy tắc filesystem | Thường có với đường dẫn tồn tại, tùy nền tảng | **`IOException`** |

Điểm đáng nhớ: **không có phương thức nào tự tạo tệp**. Với `new File("missing.txt")`, bạn vẫn có thể lấy `getPath()`, `getAbsolutePath()`; canonical hóa tên chưa tồn tại có thể phụ thuộc tình trạng filesystem và có thể thất bại.

## 3. `getPath()` — tên đường dẫn gắn với đối tượng `File`

Hợp đồng:

```java
public String getPath()
```

Phương thức trả lại chuỗi biểu diễn **abstract pathname của chính đối tượng đó**. Nếu tạo từ tên tương đối, kết quả thường vẫn tương đối; nếu tạo từ tên tuyệt đối, kết quả thường là tuyệt đối.

```java
// Đoạn minh họa, không phải chương trình độc lập.
File relative = new File("reports/../notes.txt");
String raw = relative.getPath();
```

Điều cần hiểu:

1. `getPath()` **không kiểm tra** tệp có tồn tại hay không.
2. Nó không thực hiện thao tác đòi quyền đọc nội dung file.
3. Giá trị có thể giữ thành phần `.` và `..`.
4. Khi khởi tạo `File`, Java có thể chuẩn hóa cách viết dấu phân cách theo nền tảng. Vì thế `getPath()` là path Java đang lưu, không nhất thiết là bản sao từng ký tự chính xác của chuỗi constructor ban đầu.
5. Nếu tên tương đối, việc đổi ngữ cảnh làm việc tại môi trường khác sẽ khiến **cùng chuỗi** trỏ tới nơi khác.

Ví dụ `File f = new File("notes.txt");`: `f.getPath()` là `"notes.txt"`; không phải `"/..."` hay `"C:\..."`. Tên này mô tả *vị trí theo ngữ cảnh*, không nói liệu tài nguyên thật có mặt hay không.

### 3.1. `getPath()` khác `getName()` và `getParent()`

- `getName()` lấy thành phần tên cuối của abstract pathname, ví dụ `"notes.txt"`.
- `getParent()` lấy phần cha ở dạng chuỗi theo pathname đang lưu; với `new File("notes.txt")`, `getParent()` trả `null`.
- `getPath()` trả chuỗi biểu diễn toàn bộ pathname đang lưu, có thể gồm phần cha.

Đừng sử dụng `getName()` để ghép với một root rồi mặc nhiên cho rằng đã kiểm soát được mọi trường hợp đường dẫn không đáng tin. Việc kiểm soát phạm vi filesystem cần xử lý theo mô hình `Path`/quyền truy cập và quy tắc bảo mật phù hợp.

## 4. `getAbsolutePath()` — chuyển thành vị trí tuyệt đối

Hợp đồng:

```java
public String getAbsolutePath()
```

Nếu `File` đang có pathname tuyệt đối, phương thức trả dạng tuyệt đối tương ứng. Nếu pathname tương đối, nó được **giải quyết dựa trên thư mục người dùng hiện tại** của Java (thông thường gắn với thuộc tính hệ thống `user.dir`). Có khác biệt theo nền tảng, nhất là đối với đường dẫn gắn ổ đĩa trên Windows.

Ví dụ khái niệm:

```text
user.dir:         /work/course
File path:        assets/../notes.txt
getPath():        assets/../notes.txt
getAbsolutePath(): /work/course/assets/../notes.txt
```

**Quan trọng:** phần `assets/..` có thể vẫn có mặt trong kết quả tuyệt đối. `getAbsolutePath()` giải quyết **điểm gốc của relative path**; nó không đảm bảo kết quả đã canonical hóa hay kiểm tra symlink.

`File.isAbsolute()` cho biết abstract pathname đã là absolute theo hệ thống tệp hiện tại. `getAbsoluteFile()` trả về một **đối tượng `File`** ở dạng absolute, thay vì `String`. Mọi nhận định về quyền truy cập và tồn tại vẫn cần thao tác kiểm tra riêng.

### 4.1. Đường dẫn tương đối và tính di động

`new File("data/config.txt")` trong chương trình chạy từ thư mục khác có thể trỏ tới file khác. Khi thấy “file không tồn tại” dù dự án chứa `data/config.txt`, trước tiên hãy kiểm tra `System.getProperty("user.dir")` hoặc in giá trị absolute để biết chương trình đang tìm ở đâu.

Tránh giả định directory hiện tại luôn bằng thư mục mã nguồn hoặc thư mục chứa file `.class`/JAR. IDE, Maven/Gradle, container, service manager và script có thể thiết lập working directory khác nhau.

## 5. `getCanonicalPath()` — đường dẫn chuẩn tắc theo filesystem

Hợp đồng:

```java
public String getCanonicalPath() throws IOException
```

Java trước hết chuyển đường dẫn sang dạng tuyệt đối nếu cần, rồi thực hiện canonical hóa theo quy tắc filesystem. Điển hình:

- Bỏ các thành phần dư thừa như `.` và `..` khi có thể.
- Giải quyết symbolic link của các thành phần đường dẫn đang tồn tại (đặc biệt trên Unix).
- Chuẩn hóa một số cách biểu diễn khác nhau của cùng đường dẫn; tùy nền tảng có thể gồm cách viết hoa/thường của drive letter hoặc tên.
- Có thể phát sinh `IOException` vì canonical hóa cần thông tin hệ thống tệp hoặc gặp lỗi.

**Canonical path phụ thuộc trạng thái hệ thống tệp.** Với pathname chưa tồn tại, cách canonical hóa có thể khác với sau khi tệp/thư mục được tạo; không coi kết quả đã được ghi nhớ là bất biến nếu filesystem thay đổi.

### 5.1. `getCanonicalPath()` và `getCanonicalFile()`

`getCanonicalPath()` trả `String`; `getCanonicalFile()` trả một `File` đại diện cho pathname canonical. Cả hai có thể ném `IOException`.

```java
// Đoạn minh họa; file là một File hợp lệ.
String canonicalName = file.getCanonicalPath();
File canonicalFile = file.getCanonicalFile();
```

Chỉ cần chuỗi để hiển thị thì dùng phương thức String; cần tiếp tục làm việc với đối tượng File thì dùng `getCanonicalFile()`. Trong code mới với NIO.2, `Path` và `Files` thường cho phép mô hình hóa thao tác filesystem rõ hơn.

### 5.2. Canonical hóa có đồng nghĩa “file này chắc chắn tồn tại”?

**Không.** API `File.getCanonicalPath()` có thể canonical hóa một pathname chưa tồn tại; trên một số hệ thống hoặc trong một số trạng thái, thao tác có thể lỗi. Canonical pathname không phải bản xác nhận rằng file đã được mở thành công.

Để kiểm tra file có tồn tại, có thể dùng `Files.exists(path)` hoặc `File.exists()` như một phép kiểm tra tại thời điểm gọi. Kết quả vẫn có thể cũ ngay sau đó vì tài nguyên có thể bị tạo, xóa, đổi tên hoặc thay thế đồng thời. Khi thực sự đọc/ghi, hãy xử lý ngoại lệ của chính thao tác đọc/ghi.

## 6. Ví dụ Java 17+ chạy được, không phụ thuộc chuỗi đường dẫn hệ điều hành

Chương trình sau minh họa cả **relative → absolute** và **đường dẫn có `..` → canonical**. Nó tự tạo thư mục tạm, tệp thử và xóa sau khi kiểm tra. Để stdout ổn định trên Windows, macOS và Linux, chương trình in các **kết quả logic** thay vì in toàn bộ đường dẫn tuyệt đối.

Lưu mã bằng UTF-8 với tên **`FilePathFormsDemo.java`**:

```java
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FilePathFormsDemo {
    public static void main(String[] args) throws IOException {
        File relative = new File("notes.txt");

        System.out.println("getPath: " + relative.getPath());
        System.out.println("Relative: " + !relative.isAbsolute());
        System.out.println("Absolute result: "
                + new File(relative.getAbsolutePath()).isAbsolute());

        Path root = Files.createTempDirectory("file-path-demo-");
        Path nested = root.resolve("nested");
        Path direct = root.resolve("lesson.txt");

        try {
            Files.createDirectory(nested);
            Files.writeString(direct, "Java", StandardCharsets.UTF_8);

            File viaParent = nested.resolve("..")
                    .resolve("lesson.txt").toFile();
            File actual = direct.toFile();

            System.out.println("getPath keeps ..: "
                    + viaParent.getPath().contains(".."));
            System.out.println("absolute keeps ..: "
                    + viaParent.getAbsolutePath().contains(".."));
            System.out.println("canonical matches actual: "
                    + viaParent.getCanonicalPath()
                            .equals(actual.getCanonicalPath()));
        } finally {
            Files.deleteIfExists(direct);
            Files.deleteIfExists(nested);
            Files.deleteIfExists(root);
        }
    }
}
```

Biên dịch/chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 FilePathFormsDemo.java
java FilePathFormsDemo
```

**Stdout dự kiến khi thao tác filesystem thành công:**

```text
getPath: notes.txt
Relative: true
Absolute result: true
getPath keeps ..: true
absolute keeps ..: true
canonical matches actual: true
```

**Giải thích:**

1. `new File("notes.txt")` tạo một pathname tương đối, không cần file `notes.txt` tồn tại.
2. `getPath()` giữ tên đang lưu, ở đây là `notes.txt`; `isAbsolute()` của pathname này là `false`.
3. `getAbsolutePath()` tạo một pathname tuyệt đối theo môi trường đang chạy; chương trình không in chuỗi cụ thể để không phụ thuộc `user.dir`.
4. `viaParent` được tạo qua `nested/../lesson.txt`. `getPath()` và `getAbsolutePath()` vẫn chứa `..` vì không tự canonical hóa.
5. `viaParent.getCanonicalPath()` và `actual.getCanonicalPath()` cùng trỏ tới tệp thử đã tồn tại, nên bằng nhau khi filesystem hoạt động bình thường.
6. `try/finally` dọn tệp và hai thư mục. Lỗi tạo/đọc/xóa file được biểu diễn bằng `IOException`, không bị nuốt.

**Lưu ý về môi trường:** Bài tập chạy trên filesystem thông thường có quyền tạo/xóa tệp. Một filesystem đặc biệt hoặc chính sách quyền hạn có thể khiến thao tác tạo/canonical hóa thất bại; đó là lỗi I/O cần được xử lý chứ không phải bằng chứng rằng canonical hóa luôn giống nhau trên mọi hệ thống.

### 6.1. Vì sao không kiểm tra bằng chuỗi đường dẫn tuyệt đối cố định?

Một bài kiểm thử viết:

```java
// SAI nếu kỳ vọng chạy trên mọi máy:
boolean ok = file.getAbsolutePath()
        .equals("/home/alice/project/notes.txt");
```

không thể portable vì working directory, tên người dùng và ký tự phân cách phụ thuộc môi trường. Khi cần kiểm tra ý nghĩa, hãy so sánh các thuộc tính như `isAbsolute()`, phần tử `Path` và canonical/real path của các nguồn đã xác định rõ, thay vì so với tên máy người viết ví dụ.

## 7. Những điểm khác nhau trên Windows, Linux và macOS

### 7.1. Dấu phân cách

- Unix/macOS thông thường dùng `/`.
- Windows thường hiển thị `\` và có thể có drive letter hoặc đường dẫn UNC.
- `File.separator` hoặc `Path.resolve(...)` giúp ghép path mà không phụ thuộc vào việc tự chèn dấu phân cách.

Nên dùng `new File(parent, child)` hoặc `Path.resolve(...)`, thay vì ghép chuỗi thủ công `parent + "/" + child` cho mọi đường dẫn.

### 7.2. Phân biệt hoa/thường

Một số filesystem phân biệt hoa/thường, một số không, và quy tắc có thể thay đổi theo volume/cấu hình. `getCanonicalPath()` có thể chuẩn hóa một số khác biệt nhưng **đừng coi kết quả string-equality là bài kiểm tra identity đáng tin cho mọi filesystem và mọi thời điểm**. Nếu hai file đang tồn tại, `Files.isSameFile(path1, path2)` biểu đạt trực tiếp câu hỏi “hai Path này có chỉ tới cùng một file không?”, song thao tác có thể truy cập filesystem và ném `IOException`.

### 7.3. Symlink và đường dẫn chưa tồn tại

- `getAbsolutePath()` không có hợp đồng giải quyết symlink.
- `getCanonicalPath()` thường giải quyết symlink của thành phần tồn tại nhưng chi tiết phụ thuộc platform.
- Nếu muốn **đường dẫn thực của một file đang tồn tại** với NIO.2, `Path.toRealPath()` có hợp đồng rõ ràng hơn: mặc định yêu cầu đường dẫn tồn tại và xử lý symbolic link; dùng `LinkOption.NOFOLLOW_LINKS` khi bài toán yêu cầu semantics khác.
- Với file chưa tồn tại, `toRealPath()` thường ném `NoSuchFileException`, còn `getCanonicalPath()` có thể vẫn trả canonical pathname dự kiến.

Không tự cho rằng cùng pathname canonical hôm nay chắc chắn trỏ tới cùng tài nguyên ngày mai khi symlink, mount hoặc file bị thay thế.

## 8. So sánh với `java.nio.file.Path` và `Files`

NIO.2 từ Java 7 cung cấp `Path` và `Files` với API rõ ràng cho các thao tác đường dẫn và filesystem.

| Nhu cầu | `java.io.File` | NIO.2 |
| --- | --- | --- |
| Chuỗi path hiện tại | `file.getPath()` | `path.toString()` |
| Chuyển thành absolute | `file.getAbsolutePath()` | `path.toAbsolutePath()` |
| Chuẩn hóa cú pháp `.`, `..` | Không có phương thức riêng tương đương | `path.normalize()` |
| Canonical hóa có tương tác filesystem | `file.getCanonicalPath()` | `path.toRealPath()` cho path tồn tại; semantics khác với File canonical |
| Kiểm tra tồn tại | `file.exists()` | `Files.exists(path)` |
| Kiểm tra hai path có chỉ tới cùng file | Không nên chỉ so `getPath()` | `Files.isSameFile(a, b)` |
| Đọc/ghi file | Các lớp stream truyền thống hoặc method File hạn chế | `Files.newInputStream`, `Files.readString`, `Files.writeString`, ... |
| Đổi `File` ↔ `Path` | `file.toPath()` | `path.toFile()` khi provider hỗ trợ |

Cần phân biệt kỹ:

- `Path.normalize()` là **chuẩn hóa cú pháp**, loại bỏ thành phần dư thừa mà không yêu cầu file tồn tại; nó không xác nhận quyền truy cập hoặc đường dẫn thật.
- `Path.toAbsolutePath()` tạo absolute path và không mặc nhiên giải quyết symbolic link.
- `Path.toRealPath()` tương tác filesystem, thường yêu cầu file tồn tại và có thể ném `IOException`.
- `File.getCanonicalPath()` cũng có thể truy cập filesystem nhưng **không thể coi là phép thay thế chính xác cho `toRealPath()` trong mọi trường hợp**, đặc biệt khi file chưa tồn tại và khi có `NOFOLLOW_LINKS`.

### 8.1. Ví dụ ngắn với `Path.normalize()`

```java
// Đoạn minh họa không phụ thuộc filesystem tồn tại.
Path p = Path.of("reports", "..", "notes.txt");
Path cleaned = p.normalize();

System.out.println(cleaned.equals(Path.of("notes.txt"))); // true
System.out.println(p.toAbsolutePath().isAbsolute());       // true
```

Đoạn mã này cần `import java.nio.file.Path;` trong chương trình chạy đầy đủ. `normalize()` chỉ xử lý chuỗi thành phần; nó không kiểm tra `reports` có tồn tại hay là symlink. Do đó `p.normalize()` và `p.toRealPath()` có thể cho kết quả khác hoặc `toRealPath()` có thể lỗi.

### 8.2. Chọn API nào trong code mới?

- Nếu mã cũ dùng `File`, hiểu ba phương thức này giúp debug và tương tác với thư viện cũ.
- Nếu cần duyệt thư mục, đọc/ghi nội dung, thuộc tính, symlink, copy/move hoặc kiểm soát mở file, ưu tiên các API `Path`/`Files` có hợp đồng thao tác rõ ràng.
- Nếu chỉ muốn hiển thị vị trí tham chiếu, `getAbsolutePath()` hoặc `toAbsolutePath()` thường đủ.
- Nếu cần đối chiếu các đường dẫn trỏ tới cùng file đã tồn tại, xem `Files.isSameFile(...)` hoặc `toRealPath()` theo semantics phù hợp.

## 9. Canonical path và an toàn đường dẫn

Canonical hóa hữu ích để phát hiện một số cách viết khác nhau như `a/../b`, nhưng **không tự tạo một ranh giới bảo mật hoàn chỉnh** khi đường dẫn đến từ người dùng.

### 9.1. Cảnh giác path traversal

Giả sử ứng dụng chỉ cho đọc bên dưới thư mục dữ liệu, nhưng người dùng gửi `../secret.txt`. Việc nối chuỗi rồi kiểm tra bằng:

```java
// Ví dụ SAI: so sánh tiền tố chuỗi dễ gây nhầm lẫn.
if (candidatePath.startsWith(allowedRootString)) {
    // ...
}
```

có thể sai với đường dẫn như `/srv/data-backup` khi root là `/srv/data`. Cần so sánh **thành phần path**, không phải tiền tố chuỗi tùy ý, và xử lý symlink/đường dẫn đích thật theo mô hình tin cậy.

Một lớp kiểm tra cú pháp sơ bộ với `Path`:

```java
// Minh họa; root là Path đã xác định, userName do caller cung cấp.
Path normalizedRoot = root.toAbsolutePath().normalize();
Path candidate = normalizedRoot.resolve(userName).normalize();
if (!candidate.startsWith(normalizedRoot)) {
    throw new SecurityException("Path outside allowed directory");
}
```

Đoạn này **chỉ phát hiện việc thoát khỏi root theo cú pháp**; nó chưa đủ để chống symlink và các thay đổi filesystem đồng thời. Nếu một thành phần bên trong root là symbolic link trỏ ra ngoài, kiểm tra lexical `startsWith` vẫn có thể cho kết quả cho phép.

### 9.2. Race condition (TOCTOU)

Chuỗi thao tác nguy hiểm:

1. Canonical hóa candidate và kiểm tra nó thuộc root.
2. Một tiến trình khác thay đổi symlink hoặc đổi tên một thành phần đường dẫn.
3. Chương trình mở file bằng pathname cũ.
4. Tệp thực tế được mở có thể khác tệp đã kiểm tra.

Vì vậy, ứng dụng xử lý path không đáng tin không thể dựa **chỉ** vào `getCanonicalPath()` rồi dùng lại chuỗi như một quyền truy cập bất biến. Tùy môi trường, cân nhắc dùng xử lý dựa trên file handle/descriptor và các API mở file an toàn có hỗ trợ của filesystem; `SecureDirectoryStream` là một khả năng NIO.2 khi provider hỗ trợ. Luôn xác định root tin cậy, quyền đọc/ghi và chính sách symbolic link; không cố “chữa” race chỉ bằng `normalize()`.

### 9.3. So sánh bằng `String.equals` có đủ không?

Cùng một file có thể có nhiều tên do hard link, symlink, mount hoặc chính sách filesystem. `getCanonicalPath().equals(...)` có ích ở nhiều trường hợp, nhưng không phải phép thử file identity tổng quát. Với path tồn tại, `Files.isSameFile(...)` thường phù hợp hơn. Tuy vậy, bất kỳ kết quả kiểm tra nào cũng có giới hạn thời điểm nếu tài nguyên bị thay đổi đồng thời.

## 10. Lỗi phổ biến khi sử dụng ba phương thức

1. **Nghĩ `getPath()` luôn trả absolute:** nó có thể vẫn relative.
2. **Nghĩ `getAbsolutePath()` xóa `..`:** absolute không đồng nghĩa canonical.
3. **Nghĩ `getCanonicalPath()` chỉ là xử lý chuỗi:** nó có thể cần filesystem và ném `IOException`.
4. **Bỏ qua `IOException`:** dùng canonical hóa trong code đọc/ghi phải có chính sách xử lý lỗi đúng tầng.
5. **Nghĩ gọi `new File(...)` là đã tạo tệp:** đây mới là đối tượng pathname.
6. **Cho rằng canonical path chứng minh file tồn tại:** đối tượng/path có thể được canonical hóa dù tệp chưa tồn tại.
7. **Cho rằng hai chuỗi absolute bằng nhau mới cùng một file:** symlink/hard link và quy tắc filesystem có thể làm phức tạp identity.
8. **Dựa vào đường dẫn kiểu Unix cho mọi OS:** dấu phân cách, drive letter, UNC và quy tắc tên khác nhau.
9. **Dùng `System.getProperty("user.dir")` như một hằng số nội tại của project:** working directory do môi trường chạy quyết định.
10. **Ghép chuỗi `parent + "/" + name` tràn lan:** dùng `Path.resolve` hoặc constructor cha/con để tránh lỗi phân cách.
11. **Dùng `normalize()` làm kiểm tra an toàn đủ:** symlink/TOCTOU không được giải quyết bởi thao tác lexical.
12. **So `startsWith` trên chuỗi:** `"/data2"` có thể bị nhầm là con của `"/data"`; khi kiểm tra containment, dùng đối tượng `Path` và thêm kiểm soát filesystem phù hợp.
13. **Tin kết quả kiểm tra sẽ đúng mãi:** symlink, tệp và directory có thể thay đổi sau khi kiểm tra.
14. **Dùng `getCanonicalPath()` để xác định nội dung tệp:** canonical hóa giải quyết tên, không đọc hay xác minh byte nội dung.
15. **Nhầm `File` với `FileInputStream`:** `File` tự nó không phải luồng dữ liệu đang mở.

## 11. Checklist chọn phương thức

| Bạn cần làm gì? | Lựa chọn khởi đầu |
| --- | --- |
| Hiển thị tên đường dẫn mà đối tượng `File` đang lưu | `getPath()` |
| Debug chương trình đang tìm file relative tại đâu | `getAbsolutePath()` và kiểm tra working directory |
| Loại bỏ thành phần `.` / `..` trong path một cách **thuần cú pháp** | `Path.normalize()` |
| Lấy canonical pathname theo semantics `File` | `getCanonicalPath()`, xử lý `IOException` |
| Xác định real path của một file đã tồn tại | `Path.toRealPath()` |
| Kiểm tra hai path trỏ cùng file hiện hữu | `Files.isSameFile(...)` |
| Chỉ kiểm tra sự tồn tại tại thời điểm gọi | `Files.exists(...)` hoặc `File.exists()`, sau đó vẫn xử lý lỗi khi dùng |
| Xử lý đường dẫn từ input không đáng tin | Kiểm tra cấu trúc path, root cho phép, symlink, permissions và TOCTOU; không chỉ canonical hóa |

## 12. Bài tập tự đánh giá

1. **Relative → absolute:** tạo `new File("docs/notes.txt")` và in `getPath()`, `getAbsolutePath()`. Giải thích kết quả khác nhau thế nào khi chạy ở hai working directory.
2. **Canonical:** tạo file thật ở `temp/nested/../notes.txt` (thư mục `nested` tồn tại), so sánh `getAbsolutePath()` và `getCanonicalPath()`. Chỉ ra thành phần `..` trong mỗi kết quả.
3. **File chưa tồn tại:** tạo `new File("missing.txt")` (không cần tạo tệp trên đĩa), lấy `getPath()` và `getAbsolutePath()`. `getCanonicalPath()` có luôn chứng minh file tồn tại không?
4. **So sánh path:** xem `Path.of("/srv/data-backup")` có là con của `Path.of("/srv/data")` không (trên hệ Unix minh họa). Vì sao so sánh `String.startsWith` có thể gây sai?
5. **Symlink:** nếu môi trường cho phép, tạo symlink `shortcut` trỏ đến thư mục thật rồi so sánh absolute/canonical/real path. Giải thích điều gì thay đổi khi sửa đích symlink.
6. **NIO.2:** viết lại thao tác `getAbsolutePath()` bằng `Path.toAbsolutePath()` và một ví dụ chuẩn hóa cú pháp bằng `normalize()`.
7. **Kiểm thử đa nền tảng:** thay các assertion phụ thuộc `"/home/..."` bằng assertion mang ý nghĩa logic để ví dụ chạy được trên Windows/macOS/Linux.
8. **Phân tích bảo mật:** tại sao việc kiểm tra canonical path nằm trong root rồi vài giây sau mới mở file vẫn có nguy cơ với filesystem có writer khác?

**Tiêu chí hoàn thành:** Bạn có thể trả lời chính xác **“Path đang lưu?” → `getPath()`**, **“Absolute từ relative?” → `getAbsolutePath()`**, **“Canonical theo filesystem?” → `getCanonicalPath()`**; đồng thời phân biệt được canonical với thao tác thuần cú pháp `Path.normalize()` và hiểu giới hạn của một phép kiểm tra pathname đối với tài nguyên có thể thay đổi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="legacy-file-io-005">Kiểm tra tồn tại, loại tệp, kích thước và quyền truy cập</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra tồn tại, loại tệp, kích thước và quyền truy cập với `java.io.File`

`java.io.File` là API truyền thống của Java để **biểu diễn tên đường dẫn** của tệp hoặc thư mục và truy vấn một số thuộc tính liên quan đến hệ thống tệp. Nó không đồng nghĩa với một tệp đang mở, cũng không chứng minh một đối tượng thực sự tồn tại chỉ vì bạn gọi `new File(...)`.

Các câu hỏi thông dụng là:

- Đường dẫn này có tồn tại không?
- Nó là **tệp thông thường** (regular file) hay **thư mục**?
- Tệp có bao nhiêu **byte**?
- Tiến trình Java hiện tại có thể **đọc**, **ghi** hoặc **thực thi** đường dẫn không?
- Khi kiểm tra xong, điều gì đảm bảo thao tác I/O tiếp theo thành công?

Bài học tập trung vào `File.exists()`, `isFile()`, `isDirectory()`, `length()`, `canRead()`, `canWrite()`, `canExecute()` và những giới hạn quan trọng của chúng; đồng thời chỉ rõ khi nào nên chuyển sang `java.nio.file.Path`/`Files` để lấy metadata và xử lý lỗi chính xác hơn.

## 1. `File` là biểu diễn đường dẫn, không phải file handle

```java
File file = new File("data/report.txt");
```

Đây là **đoạn minh họa** (cần `import java.io.File;`). Constructor nhận chuỗi đường dẫn và tạo một đối tượng `File`. Nó **không tự tạo** tệp `data/report.txt`, không mở luồng đọc/ghi và không bảo đảm đường dẫn hợp lệ hoặc đang tồn tại.

Phân biệt:

| Đối tượng/API | Vai trò chính | Có mở tài nguyên I/O ngay khi tạo không? |
| --- | --- | --- |
| `new File("report.txt")` | Đại diện cho pathname, truy vấn thuộc tính/đường dẫn | Không |
| `Path.of("report.txt")` | Biểu diễn đường dẫn theo API NIO.2 | Không |
| `new FileInputStream(file)` | Mở tệp để đọc byte | Có, có thể ném `FileNotFoundException` |
| `new FileOutputStream(file)` | Mở tệp để ghi byte; mặc định có thể tạo/ghi đè | Có, có thể ném lỗi |
| `Files.newInputStream(path)` | Mở byte stream với các tùy chọn tương ứng | Có, có thể ném `IOException` |
| `Files.readAttributes(...)` | Truy vấn thuộc tính theo API NIO.2 | Có thao tác truy vấn hệ thống tệp, có thể ném `IOException` |

Đường dẫn tương đối như `"data/report.txt"` được giải quyết theo **working directory** của tiến trình Java, không tự động theo thư mục chứa file `.java` hoặc `.class`. Dấu `/` và `\` là những dấu phân cách có chi tiết phụ thuộc nền tảng; dùng `Path.of("data", "report.txt")` hoặc `new File(parent, child)` giúp biểu diễn đường dẫn rõ ràng hơn.

`getName()`, `getParent()`, `getPath()` chủ yếu xử lý thông tin đường dẫn; chúng **không** xác nhận file tồn tại. `getAbsolutePath()` tạo đường dẫn tuyệt đối nhưng không mặc nhiên giải quyết symbolic link hoặc kiểm tra tệp. `getCanonicalPath()` có thể cần truy cập hệ thống tệp và ném `IOException`; không nên đồng nhất nó với `getAbsolutePath()`.

## 2. Các phép kiểm tra chính của `java.io.File`

| Phương thức | Giá trị và ý nghĩa | Giới hạn quan trọng |
| --- | --- | --- |
| `exists()` | `true` nếu đối tượng mà đường dẫn trỏ tới tồn tại theo phép truy vấn | `false` không cung cấp nguyên nhân rõ ràng; có thể do thiếu file hoặc không xác định được |
| `isFile()` | `true` khi là **regular file** theo cách nhận diện của hệ thống tệp | `false` nếu thư mục, không tồn tại hoặc thuộc loại khác |
| `isDirectory()` | `true` khi là thư mục | `false` không nhất thiết nghĩa là file thường |
| `length()` | Độ dài file theo **byte**, kiểu `long` | Với đường dẫn không tồn tại có thể trả `0`; với thư mục giá trị **không xác định** |
| `canRead()` | Kiểm tra quyền đọc theo cơ chế mà Java/hệ điều hành cung cấp | Là tín hiệu tham khảo, không bảo đảm thao tác đọc tiếp theo thành công |
| `canWrite()` | Kiểm tra quyền ghi | Không thay thế thao tác ghi thật và xử lý exception |
| `canExecute()` | Kiểm tra quyền thực thi | Phụ thuộc nền tảng, chính sách/permission, không đảm bảo lệnh chạy thành công |
| `isHidden()` | Kiểm tra thuộc tính tệp ẩn theo quy ước của nền tảng | Không nên chỉ dựa vào tiền tố dấu chấm trên mọi hệ điều hành |

**Quan trọng:** `isFile()` và `isDirectory()` không phải hai phép phủ định của nhau. `!file.isFile()` **không có nghĩa** `file.isDirectory()`: file có thể vắng mặt, là symbolic link hỏng, thiết bị, socket đặc biệt hoặc không thể truy vấn. Tương tự, `exists() == true` không nhất thiết có nghĩa là tệp **thông thường**.

### Ví dụ 1 — tệp, thư mục và đường dẫn không tồn tại

Chương trình Java 17+ sau tạo thư mục tạm và một tệp có dữ liệu ASCII đã biết, không phụ thuộc vào đường dẫn hay tệp do người học chuẩn bị.

```java
// FileTypeDemo.java
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileTypeDemo {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("file-type-");
        Path text = root.resolve("notes.txt");
        Path folder = root.resolve("folder");
        Path missing = root.resolve("missing.txt");

        try {
            Files.writeString(text, "ABC", StandardCharsets.UTF_8);
            Files.createDirectory(folder);

            File file = text.toFile();
            File directory = folder.toFile();
            File absent = missing.toFile();

            System.out.println("file.exists=" + file.exists());
            System.out.println("file.isFile=" + file.isFile());
            System.out.println("file.isDirectory=" + file.isDirectory());
            System.out.println("file.length=" + file.length());

            System.out.println("dir.exists=" + directory.exists());
            System.out.println("dir.isFile=" + directory.isFile());
            System.out.println("dir.isDirectory=" + directory.isDirectory());

            System.out.println("missing.exists=" + absent.exists());
            System.out.println("missing.isFile=" + absent.isFile());
            System.out.println("missing.isDirectory=" + absent.isDirectory());
            System.out.println("missing.length=" + absent.length());
        } finally {
            Files.deleteIfExists(text);
            Files.deleteIfExists(folder);
            Files.deleteIfExists(root);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 FileTypeDemo.java
java FileTypeDemo
```

Đầu ra khi các thao tác tạo tệp/thư mục thành công:

```text
file.exists=true
file.isFile=true
file.isDirectory=false
file.length=3
dir.exists=true
dir.isFile=false
dir.isDirectory=true
missing.exists=false
missing.isFile=false
missing.isDirectory=false
missing.length=0
```

`missing.length=0` là lý do không thể dùng `length()==0` để khẳng định “đây là file rỗng”: đường dẫn không tồn tại cũng có thể cho ra `0`. File **rỗng thật sự** cũng có `length()==0`. Kiểm tra `exists()` và `isFile()` có thể giúp diễn đạt điều bạn muốn, nhưng với mã cần xử lý lỗi chính xác vẫn nên thực hiện thao tác I/O và bắt exception.

`length()` của **thư mục** không nên được dùng để hỏi “thư mục chứa bao nhiêu dữ liệu” hoặc “có bao nhiêu file con”. Giá trị trả về cho thư mục không được bảo đảm. Muốn liệt kê file con, dùng API liệt kê thư mục; muốn đo dung lượng toàn bộ cây thư mục, phải duyệt các mục bên trong theo chính sách rõ ràng.

## 3. Kích thước `length()` đo byte, không đo ký tự

Một file văn bản chứa ký tự Unicode có thể có số byte khác số `char` và Unicode code point. Ví dụ trong UTF-8:

- Chữ `A` có 1 byte.
- Emoji `😀` có 4 byte UTF-8.
- Chuỗi `"A😀"` có 5 byte, nhưng `String.length()` trả 3 đơn vị mã UTF-16 và số code point là 2.

### Ví dụ 2 — so sánh dung lượng tệp và độ dài chuỗi

```java
// FileByteLengthDemo.java
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileByteLengthDemo {
    public static void main(String[] args) throws Exception {
        Path path = Files.createTempFile("byte-size-", ".txt");
        String text = "A😀";

        try {
            Files.writeString(path, text, StandardCharsets.UTF_8);

            File file = path.toFile();
            System.out.println("fileBytes=" + file.length());
            System.out.println("stringChars=" + text.length());
            System.out.println("codePoints=" +
                    text.codePointCount(0, text.length()));
            System.out.println("nioBytes=" + Files.size(path));
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 FileByteLengthDemo.java
java FileByteLengthDemo
```

Đầu ra:

```text
fileBytes=5
stringChars=3
codePoints=2
nioBytes=5
```

Số byte thực tế phụ thuộc **charset** dùng khi ghi. Nếu file được ghi bằng encoding khác, `length()` vẫn trả số byte lưu trữ (khi thông tin có thể lấy được), không tự giải mã để đếm ký tự.

`File.length()` trả `long`; không chuyển về `int` khi file có thể lớn hơn giới hạn `int`. Một số thuộc tính file có thể thay đổi sau khi truy vấn, đặc biệt khi có tiến trình khác ghi hoặc xóa file. Đừng dùng `length()` như một cam kết dữ liệu sẽ không đổi trong lúc đọc.

### Tệp lớn và dữ liệu chưa flush

Nếu ghi qua `BufferedOutputStream` hoặc `BufferedWriter`, có thể còn dữ liệu ở bộ đệm tầng ứng dụng chưa chuyển đến file. Khi cần quan sát nội dung vừa ghi, hãy `flush()` hoặc đóng writer phù hợp trước khi kiểm tra; tuy nhiên `flush()` **không tự đảm bảo dữ liệu được ghi bền vững xuống phần cứng**. Ngay cả khi đã flush, một tiến trình khác vẫn có thể thay đổi file.

## 4. Quyền đọc, ghi, thực thi: ba câu hỏi khác nhau

```java
File file = new File("data/report.txt");
boolean readable = file.canRead();
boolean writable = file.canWrite();
boolean executable = file.canExecute();
```

Đây là **đoạn minh họa**, không phải chương trình độc lập.

Các phương thức trên kiểm tra trạng thái được nền tảng báo cáo cho tiến trình đang chạy. Ý nghĩa trong thực tế phụ thuộc hệ điều hành, hệ thống tệp, quyền người dùng, ACL, volume gắn từ xa, chính sách sandbox, quyền của thư mục cha và trạng thái đối tượng.

| Phép kiểm tra | Câu hỏi dự kiến | Điều **không được suy ra** |
| --- | --- | --- |
| `canRead()` | Đường dẫn hiện có thể đọc theo thông tin mà nền tảng cung cấp không? | Một thao tác mở/đọc sau đó chắc chắn thành công |
| `canWrite()` | Đường dẫn hiện có thể ghi theo thông tin mà nền tảng cung cấp không? | Ghi chắc chắn thành công; dung lượng đĩa đủ; file có thể bị ghi đè |
| `canExecute()` | Có quyền thực thi theo mô hình nền tảng không? | Tệp là chương trình hợp lệ hay `ProcessBuilder` sẽ chạy được |
| `exists()` | Đối tượng được truy vấn có tồn tại không? | Caller có quyền đọc nội dung hoặc mở tệp |

Một file có thể tồn tại nhưng `canRead()==false`; thư mục có thể tồn tại nhưng không thể liệt kê; file có thể ghi được ở thời điểm kiểm tra rồi đổi quyền ngay sau đó. Trên một số nền tảng hoặc khi chạy bằng tài khoản có đặc quyền, kết quả kiểm tra quyền có thể khác kỳ vọng từ bit POSIX. Không viết bài test cứng rằng một file tạm bất kỳ nhất thiết `canExecute()==false` trên mọi hệ điều hành.

Đối với thư mục, quyền “thực thi” trên POSIX thường liên quan khả năng **traverse/search** thư mục, không phải “chạy thư mục như ứng dụng”. Khả năng tạo file mới còn phụ thuộc quyền của thư mục chứa, không chỉ `canWrite()` trên file chưa tồn tại.

**Không dùng các phép kiểm tra này như cơ chế phân quyền bảo mật cho hệ thống nhiều người dùng.** Server cần cơ chế xác thực/ủy quyền độc lập và vẫn phải xử lý lỗi truy cập thật khi thao tác.

### Ví dụ 3 — quan sát quyền mà không giả định một kết quả nền tảng duy nhất

```java
// FilePermissionsDemo.java
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FilePermissionsDemo {
    public static void main(String[] args) throws Exception {
        Path path = Files.createTempFile("permissions-", ".txt");

        try {
            Files.writeString(path, "data", StandardCharsets.UTF_8);
            File file = path.toFile();

            System.out.println("exists=" + file.exists());
            System.out.println("regularFile=" + file.isFile());

            System.out.println("canRead=" + file.canRead());
            System.out.println("canWrite=" + file.canWrite());
            System.out.println("canExecute=" + file.canExecute());

            System.out.println("nioReadable=" + Files.isReadable(path));
            System.out.println("nioWritable=" + Files.isWritable(path));
            System.out.println("nioExecutable=" + Files.isExecutable(path));
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
```

Chạy:

```bash
javac FilePermissionsDemo.java
java FilePermissionsDemo
```

Hai dòng đầu thông thường là:

```text
exists=true
regularFile=true
```

Sáu dòng sau in `true` hoặc `false` **theo môi trường chạy**, nên không có một stdout chuẩn duy nhất cho toàn bộ chương trình. Bạn có thể thấy các phương thức `File` và `Files` cho kết quả tương tự trên file cục bộ, nhưng đó không phải cam kết chung cho mọi provider hoặc trạng thái chạy.

Đặt quyền bằng `setReadable()`, `setWritable()`, `setExecutable()` hoặc `setReadOnly()` có nhiều điều kiện phụ thuộc nền tảng; giá trị trả về chỉ cho biết thao tác yêu cầu thay đổi theo hợp đồng API, không làm các kiểm tra quyền trở thành cơ chế bảo mật hoàn chỉnh. Với quyền POSIX, có thể cân nhắc `Files.getPosixFilePermissions()`/`setPosixFilePermissions()` trên provider hỗ trợ; Windows và các hệ thống khác có mô hình quyền khác. Các lời gọi không được provider hỗ trợ có thể ném `UnsupportedOperationException`.

## 5. Bốn trạng thái thường bị nhầm lẫn

Đừng dùng logic `if (file.exists())` như thể nó đồng nghĩa với một trong bốn nhận định sau:

1. **Có đối tượng hệ thống tệp:** `exists()` có thể trả `true` cho file, thư mục hoặc một loại đối tượng khác.
2. **Là file thường:** `isFile()` mới kiểm tra loại này; đường dẫn trỏ tới thư mục phải trả `false`.
3. **Có dữ liệu:** file thường có thể **rỗng**, `length()==0`, và vẫn tồn tại hoàn toàn hợp lệ.
4. **Có thể thực hiện thao tác mong muốn:** đọc/ghi/xóa/đổi tên còn phụ thuộc quyền, tình trạng hiện thời, quyền của thư mục cha, file lock, provider và những ràng buộc khác.

Ví dụ các trạng thái:

| Trường hợp | `exists()` | `isFile()` | `isDirectory()` | `length()` | Ghi chú |
| --- | --- | --- | --- | --- | --- |
| File thường, có nội dung | Thường `true` | `true` | `false` | Số byte nếu xác định được | Đọc/ghi vẫn có thể lỗi |
| File thường, rỗng | `true` | `true` | `false` | `0` | File rỗng hợp lệ |
| Thư mục | `true` | `false` | `true` | **Không xác định theo hợp đồng** | Không dùng `length()` để đếm file con |
| Đường dẫn không tồn tại | `false` | `false` | `false` | `0` theo hành vi của `File.length()` khi tệp không tồn tại | Không phân biệt được với file rỗng chỉ từ `length()` |
| Đường dẫn không thể truy vấn đầy đủ | Có thể là `false` | Có thể là `false` | Có thể là `false` | Không tin cậy để suy loại | Cần thao tác thực hoặc API có exception |
| Symbolic link trỏ tới file thường | Thường xét **đích liên kết** | Có thể `true` | Thường `false` | Thường theo đích | NIO.2 cho phép lựa chọn không đi theo link |

Các trường trong bảng mô tả tình huống **thông thường**, không đảm bảo kết quả không thay đổi giữa những lời gọi, hoặc provider nào cũng hỗ trợ cùng loại đối tượng.

## 6. Symbolic link: “đường dẫn” có thể trỏ tới đối tượng khác

Một symbolic link là mục hệ thống tệp chứa tham chiếu đến một đường dẫn khác. Vì vậy cần hỏi rõ: bạn muốn kiểm tra **link** hay **đích của link**?

Ví dụ về mặt khái niệm:

```text
notes-link.txt  -->  documents/notes.txt
       ^                    ^
       |                    |
     link                target
```

- Nếu target tồn tại và là file thường, các phép truy vấn `File` thông dụng thường báo theo target.
- Nếu target bị xóa, link trở thành **broken/dangling symbolic link**; `File.exists()` có thể trả `false` mặc dù **bản thân link vẫn còn**.
- Nếu cần quản lý chính link (kiểm tra link hỏng, xóa link, xem loại mà không theo link), dùng `Path` và `Files` với `LinkOption.NOFOLLOW_LINKS` ở API hỗ trợ.

```java
Path path = Path.of("notes-link.txt");

// Mã minh họa: cần import java.nio.file.Files,
// java.nio.file.Path và java.nio.file.LinkOption.
boolean targetExists = Files.exists(path);
boolean linkItselfExists =
        Files.exists(path, LinkOption.NOFOLLOW_LINKS);
boolean isLink = Files.isSymbolicLink(path);
```

`Files.exists(path)` theo link ở mặc định theo lựa chọn của API; `Files.exists(path, NOFOLLOW_LINKS)` hỏi về **mục link** (nếu xác định được); `Files.isSymbolicLink(path)` xác định loại mục. Với broken symbolic link, điều này cho phép phân biệt target không còn với link còn tồn tại. Trên một số nền tảng tạo symbolic link yêu cầu quyền đặc biệt hoặc không được provider hỗ trợ; không ép một bài test về symbolic link phải chạy thành công trên mọi máy.

Nếu áp dụng chính sách bảo mật liên quan thư mục gốc, cần thận trọng với symbolic link, canonicalization và **đường dẫn bị đổi trong lúc kiểm tra**. Một kiểm tra `startsWith` trên đường dẫn chuỗi chưa chuẩn hóa không phải cơ chế confinement đáng tin cậy trước input độc hại. Dùng APIs thao tác qua directory handle, các tùy chọn không theo link hoặc kiểm soát truy cập của hệ điều hành khi bài toán yêu cầu.

## 7. So sánh `java.io.File` với `java.nio.file.Files`

`File` có API tiện lợi và vẫn gặp trong thư viện cũ. Tuy nhiên `java.nio.file.Path` + `Files` thường phù hợp hơn khi cần truy vấn trạng thái, xử lý lỗi, symbolic link và thuộc tính chi tiết.

| Nhu cầu | `File` truyền thống | NIO.2 (`Path`/`Files`) |
| --- | --- | --- |
| Tồn tại | `file.exists()` | `Files.exists(path)` |
| Không tồn tại | `!file.exists()` thường bị dùng sai ngữ nghĩa | `Files.notExists(path)` (có thể cùng `exists()` là `false` nếu trạng thái không xác định) |
| File thường | `file.isFile()` | `Files.isRegularFile(path)` |
| Thư mục | `file.isDirectory()` | `Files.isDirectory(path)` |
| Byte length | `file.length()` | `Files.size(path)` có thể ném `IOException` |
| Đọc/ghi/thực thi được? | `canRead/canWrite/canExecute` | `Files.isReadable/isWritable/isExecutable` |
| Xem thuộc tính cơ bản | `lastModified()`, một số tiện ích | `Files.readAttributes(path, BasicFileAttributes.class)` |
| Symbolic link | Khó kiểm soát hành vi theo link | `LinkOption.NOFOLLOW_LINKS`, `isSymbolicLink`, `readSymbolicLink` |
| Loại file đặc biệt | Thông tin hạn chế | `BasicFileAttributes.isOther()` và file attribute views |
| Khi truy vấn lỗi | Nhiều phương thức trả `false`/`0` | Nhiều thao tác đọc metadata ném `IOException` với thông tin hữu ích |

Đừng hiểu `Files.exists(path)==false` là `Files.notExists(path)==true`. Java NIO.2 có **ba trạng thái có thể xảy ra** khi hỏi hệ thống tệp: xác định tồn tại, xác định không tồn tại, hoặc **không thể xác định**. Trong trạng thái thứ ba, **cả `exists()` và `notExists()` có thể đều trả `false`**. Khi cần phân biệt rõ lỗi quyền truy cập, hệ thống tệp hay I/O, hãy thực hiện truy vấn/hoạt động có thể báo `IOException`, thay vì chỉ dựa vào boolean.

### Ví dụ 4 — lấy metadata có kiểm soát lỗi với `Files`

```java
// NioFileAttributesDemo.java
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class NioFileAttributesDemo {
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("nio-meta-", ".txt");

        try {
            Files.writeString(file, "Hello", StandardCharsets.UTF_8);

            BasicFileAttributes attributes =
                    Files.readAttributes(file, BasicFileAttributes.class);

            System.out.println("regular=" + attributes.isRegularFile());
            System.out.println("directory=" + attributes.isDirectory());
            System.out.println("size=" + attributes.size());
            System.out.println("filesSize=" + Files.size(file));
            System.out.println("exists=" + Files.exists(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Chạy:

```bash
javac NioFileAttributesDemo.java
java NioFileAttributesDemo
```

Đầu ra khi thao tác thành công:

```text
regular=true
directory=false
size=5
filesSize=5
exists=true
```

`BasicFileAttributes` còn có `creationTime()`, `lastModifiedTime()`, `lastAccessTime()`, `fileKey()`, `isSymbolicLink()`, `isOther()`; ý nghĩa và mức hỗ trợ phụ thuộc file-system provider. Không tự mặc định mọi timestamp có độ chính xác giống nhau hoặc `fileKey()` luôn khác `null`.

`Files.readAttributes(...)` cho phép yêu cầu truy vấn nhiều thuộc tính cơ bản trong cùng một lời gọi và báo lỗi qua `IOException`. Tuy nhiên không mặc định đây là **snapshot nguyên tử** của mọi thuộc tính đối với mọi provider: file vẫn có thể thay đổi giữa lúc lấy attributes và lúc ứng dụng sử dụng.

## 8. Thao tác I/O mới là phép kiểm tra thực tế: TOCTOU

Một lỗi thiết kế phổ biến là **check-then-act**:

```java
if (file.exists() && file.canRead()) {
    // Một tiến trình khác có thể xóa hoặc đổi quyền
    // ngay sau khi kiểm tra.
    readTheFile(file);
}
```

Đây là **mã minh họa**, không chạy độc lập. Giữa thời điểm **kiểm tra** (*time of check*) và thời điểm **sử dụng** (*time of use*) có một khoảng trống. Trong khoảng này, file có thể bị xóa, bị thay bằng thư mục, đổi nội dung, đổi quyền hoặc bị chuyển hướng bởi link. Vì vậy các kết quả preflight không phải sự bảo đảm cho thao tác tiếp theo.

Cách ứng dụng nên làm khi mục tiêu là **đọc nội dung**:

```java
try (InputStream input = Files.newInputStream(path)) {
    // Đọc bằng stream; vẫn xử lý IOException khi cần.
} catch (NoSuchFileException ex) {
    // File không còn tồn tại tại thời điểm mở.
} catch (AccessDeniedException ex) {
    // Provider báo lỗi truy cập.
} catch (IOException ex) {
    // Lỗi I/O khác.
}
```

Đây là **mã minh họa**, cần import `InputStream`, `Files`, `Path`, `IOException`, `NoSuchFileException`, `AccessDeniedException`; `path` phải được khai báo. Các loại exception cụ thể phụ thuộc provider và thao tác; không phải mọi lỗi thiếu quyền đều được phân loại thành `AccessDeniedException`.

### Ví dụ 5 — kiểm tra tồn tại thành công nhưng thao tác sau đó vẫn lỗi

Chương trình sau **mô phỏng một cách xác định** thay đổi trạng thái bằng cách tự xóa file giữa hai bước. Nó không tạo race thật, nhưng cho thấy về mặt logic vì sao câu lệnh `exists()` không thể bảo đảm kết quả của lời gọi tiếp theo.

```java
// CheckThenActDemo.java
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public class CheckThenActDemo {
    public static void main(String[] args) throws Exception {
        Path path = Files.createTempFile("check-act-", ".txt");

        try {
            Files.writeString(path, "data", StandardCharsets.UTF_8);

            System.out.println("before=" + Files.exists(path));

            // Một thay đổi có thể xuất hiện sau bước kiểm tra.
            Files.delete(path);

            try {
                Files.readString(path, StandardCharsets.UTF_8);
                System.out.println("read succeeded");
            } catch (NoSuchFileException ex) {
                System.out.println("read failed: file was removed");
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
```

Chạy:

```bash
javac CheckThenActDemo.java
java CheckThenActDemo
```

Stdout khi các thao tác hệ thống tệp thực hiện bình thường:

```text
before=true
read failed: file was removed
```

Kết luận **không phải** “hãy kiểm tra `exists()` lần nữa trước khi đọc”. Kiểm tra thêm vẫn có cửa sổ race. Thay vào đó:

- Với thao tác đọc, **mở** file rồi xử lý kết quả của chính lần mở/đọc; thực hiện thêm kiểm tra metadata nếu nghiệp vụ cần.
- Với ghi/tạo file mới, dùng các tùy chọn mở mang tính thao tác như `StandardOpenOption.CREATE_NEW` để yêu cầu tạo **chỉ nếu chưa tồn tại**, thay vì tự `if (!exists) createFile()` rồi hy vọng không có đối thủ.
- Với xử lý file nhiều bước hoặc yêu cầu bảo mật, sử dụng các kỹ thuật quản lý handle, atomic rename, giao dịch ứng dụng hoặc API chuyên dụng tùy hệ thống tệp và mức đảm bảo cần đạt.

## 9. Liệt kê thư mục và kiểm tra loại đối tượng

Để liệt kê thành viên thư mục theo API cũ, `File.list()` hoặc `File.listFiles()` có thể được sử dụng. Tuy nhiên `listFiles()` **có thể trả `null`** nếu đường dẫn không phải thư mục hoặc xảy ra lỗi I/O, thay vì luôn trả mảng rỗng:

```java
// Mã minh họa với directory là File đã khai báo.
File[] children = directory.listFiles();

if (children == null) {
    // Không thể coi đây là thư mục được liệt kê thành công.
} else {
    for (File child : children) {
        System.out.println(child.getName());
    }
}
```

`children.length == 0` nghĩa là việc liệt kê **thành công và không có phần tử**; `children == null` nghĩa là **không lấy được danh sách**. Không viết `for (File child : directory.listFiles())` mà không xét `null` nếu đầu vào có thể không hợp lệ hoặc không truy cập được.

Với NIO.2, `Files.newDirectoryStream(path)` hoặc `Files.list(path)` cung cấp cơ chế liệt kê và có thể báo lỗi bằng exception; phải **đóng** directory stream/stream trong `try-with-resources`. Các dòng được liệt kê có thể thay đổi khi có tiến trình khác chỉnh thư mục; không mặc định thứ tự liệt kê cố định, và không coi kết quả liệt kê là một snapshot nhất quán cho thao tác tiếp theo.

## 10. Chọn API theo câu hỏi thực sự cần trả lời

| Mục tiêu của chương trình | Cách tiếp cận |
| --- | --- |
| Hiển thị thông tin tham khảo trong giao diện | Có thể dùng `File.exists()/isFile()/length()/canRead()`, kèm khả năng trạng thái thay đổi |
| Kiểm tra file thường trước khi đề xuất chọn file | `isFile()` hoặc `Files.isRegularFile()` hữu ích, nhưng vẫn bắt lỗi khi mở |
| Cần lý do lỗi chính xác khi metadata không đọc được | `Files.readAttributes`, `Files.size` với `IOException` |
| Kiểm tra broken symbolic link | `Files.exists(path, NOFOLLOW_LINKS)` và `Files.isSymbolicLink(path)` |
| Đọc một file phục vụ nghiệp vụ | Mở stream hoặc dùng `Files.readString` phù hợp, bắt và xử lý lỗi thật |
| Tạo file mới mà không ghi đè file đã tồn tại | Dùng `CREATE_NEW` thay vì check rồi create |
| Đếm số file trong thư mục | Liệt kê bằng directory stream/listFiles và xử lý lỗi; không dùng `File.length()` |
| Xác thực quyền truy cập người dùng đến dữ liệu | Cơ chế phân quyền ứng dụng/hệ điều hành, không chỉ `canRead/canWrite` |
| Nhận thuộc tính của nhiều loại file | API NIO.2 và attribute view/provider tương ứng |

Các phép `exists`, `canRead`, `canWrite` rất có ích để hỗ trợ điều kiện UI, cảnh báo sớm và hiển thị trạng thái. Nhưng **quyết định nghiệp vụ cuối cùng** thường phụ thuộc kết quả thao tác thật và hợp đồng của hệ thống.

## 11. Lỗi phổ biến khi dùng `File`

1. **Nhầm `new File(path)` với tạo tệp:** constructor không tạo file. Dùng phương thức tạo tệp phù hợp và xử lý exception.
2. **Dùng `!isFile()` để kết luận là thư mục:** có nhiều loại/ trạng thái khác nhau; kiểm tra `isDirectory()` riêng.
3. **Dùng `length()==0` để xác định tệp rỗng:** file không tồn tại cũng cho `0`. Đừng quên kiểm tra loại file và tình trạng lỗi.
4. **Nhầm byte với ký tự:** `length()` là số byte; UTF-8 và UTF-16 có số đơn vị khác nhau.
5. **Tin tuyệt đối `canRead()`/`canWrite()`:** quyền, file và hệ thống có thể thay đổi; một check không thay cho thao tác và `IOException`.
6. **Không xét symbolic link:** link hỏng có thể làm `exists()` trả `false`; nếu muốn hỏi về link, dùng `NOFOLLOW_LINKS`.
7. **Không phân biệt không tồn tại và không thể xác định:** `Files.exists()==false` và `Files.notExists()==false` có thể cùng đúng nếu provider không thể xác định.
8. **Dùng `File.length()` cho thư mục:** độ dài thư mục không mang nghĩa số file hoặc tổng dung lượng cây.
9. **Gọi `listFiles()` rồi duyệt ngay:** mảng có thể là `null`, gây `NullPointerException`.
10. **Cho rằng có đường dẫn tuyệt đối là file tồn tại:** `getAbsolutePath()` xử lý đường dẫn, không xác thực file.
11. **Tạo trước bằng `exists()` rồi tạo file:** có thể dính race; ưu tiên `CREATE_NEW` cho yêu cầu tạo mới không ghi đè.
12. **Bỏ qua exception khi mở/đọc/ghi:** xử lý lỗi thật là yêu cầu bắt buộc, dù đã kiểm tra quyền.
13. **Không đóng stream/directory stream:** dễ rò rỉ file descriptor hoặc resource; dùng `try-with-resources`.
14. **Dùng kết quả phân quyền như kiểm soát bảo mật:** kiểm tra file không thay thế authorization theo người dùng và nghiệp vụ.

## 12. Tự kiểm tra kiến thức

**Câu 1.** Sau `File f = new File("missing.txt");`, file đã được tạo chưa?

**Đáp án:** Chưa. Đối tượng `File` mới biểu diễn pathname; không có thao tác mở hoặc tạo file.

**Câu 2.** `f.exists()` trả `true`. Có thể kết luận `f.isFile()` cũng `true` không?

**Đáp án:** Không. Nó có thể là thư mục hoặc đối tượng khác.

**Câu 3.** `f.length() == 0` có nghĩa file đang rỗng không?

**Đáp án:** Không nhất thiết. File không tồn tại cũng có thể cho `0`; thư mục có độ dài không xác định.

**Câu 4.** Ghi `"A😀"` bằng UTF-8, `File.length()` và `String.length()` bằng bao nhiêu?

**Đáp án:** Lần lượt 5 **byte** và 3 đơn vị `char` UTF-16; chuỗi chứa 2 Unicode code point.

**Câu 5.** `canRead()==true` thì lần `new FileInputStream(f)` kế tiếp chắc chắn thành công?

**Đáp án:** Không. File/quyền có thể thay đổi, hoặc thao tác còn bị giới hạn bởi các điều kiện khác.

**Câu 6.** `Files.exists(path)==false` có luôn nghĩa `Files.notExists(path)==true`?

**Đáp án:** Không. Nếu không thể xác định trạng thái, cả hai có thể cùng trả `false`.

**Câu 7.** Một symbolic link tồn tại nhưng target đã bị xóa. Muốn kiểm tra chính link thì dùng gì?

**Đáp án:** `Files.exists(path, LinkOption.NOFOLLOW_LINKS)` và, khi phù hợp, `Files.isSymbolicLink(path)`.

**Câu 8.** Vì sao `if (!Files.exists(path)) { Files.createFile(path); }` không phải cách bảo đảm tạo mới an toàn khi có đồng thời?

**Đáp án:** Hai thao tác tách biệt có khoảng TOCTOU. Với yêu cầu không ghi đè, dùng API tạo mới có kiểm tra nguyên tử tương ứng như `Files.createFile(path)` hoặc mở với `StandardOpenOption.CREATE_NEW` và xử lý `FileAlreadyExistsException`.

**Câu 9.** `listFiles()` trả `null` và mảng rỗng có ý nghĩa như nhau?

**Đáp án:** Không. `null` có thể chỉ không phải thư mục hoặc lỗi I/O; mảng rỗng nghĩa là liệt kê thành công nhưng không có mục nào.

**Câu 10.** Nếu mục tiêu là đọc nội dung để xử lý nghiệp vụ, nên dừng sau khi `exists()` và `canRead()` đều đúng?

**Đáp án:** Không. Phải mở/đọc file, kiểm tra kết quả và xử lý `IOException`; các phép kiểm tra chỉ mang tính tham khảo tại thời điểm gọi.

**Quy tắc nhớ:** `File` đại diện cho **đường dẫn**; `exists()/isFile()/isDirectory()` trả lời những câu hỏi khác nhau; `length()` đo **byte** và không phân biệt được mọi trạng thái bằng `0`; `canRead()/canWrite()/canExecute()` phản ánh quyền theo môi trường hiện tại nhưng **không bảo đảm thao tác sau thành công**. Khi cần lỗi rõ ràng, kiểm soát symbolic link hoặc thao tác an toàn hơn, ưu tiên `Path` và `Files`, đồng thời xử lý exception của thao tác thật.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="legacy-file-io-006">list, listFiles, FileFilter và FilenameFilter</a>

<details>
<summary>Xem chi tiết</summary>

# `list()`, `listFiles()`, `FileFilter` và `FilenameFilter`

## Mục tiêu bài học

Trong Java I/O truyền thống, lớp `java.io.File` biểu diễn **đường dẫn** tới file hoặc thư mục, không phải nội dung file hay một file descriptor đang mở. Để liệt kê các phần tử **trực tiếp** trong một thư mục, lớp này cung cấp hai họ phương thức: `list(...)` trả **tên** dạng `String`, còn `listFiles(...)` trả các đối tượng `File`. Hai giao diện `FilenameFilter` và `FileFilter` giúp chọn những phần tử được đưa vào kết quả.

Sau bài này, bạn có thể:

- Phân biệt các overload `list()`, `list(FilenameFilter)`, `listFiles()`, `listFiles(FilenameFilter)` và `listFiles(FileFilter)`.
- Viết lambda/implementation cho `FilenameFilter.accept(File dir, String name)` và `FileFilter.accept(File pathname)` đúng tham số.
- Biết khi nào cần **tên đơn giản**, khi nào cần **đường dẫn đầy đủ theo đối tượng `File`**, và cách kết hợp parent với child.
- Xử lý kết quả `null` của `list`/`listFiles`, sắp xếp khi cần, giới hạn ở một cấp thư mục, và các tình huống permission/race condition.
- Viết Java 17+ dùng thư mục tạm để kiểm chứng bộ lọc một cách độc lập với thư mục máy cá nhân.
- Biết lúc nào nên chuyển sang `java.nio.file.Files.list`, `DirectoryStream`, `Files.walk` hoặc `Files.walkFileTree` trong mã mới.

## 1. Nền tảng: `java.io.File` đại diện đường dẫn

```java
File directory = new File("data");
File child = new File(directory, "report.txt");
```

Đây là **đoạn minh họa**, cần `import java.io.File;`. Tạo `new File(...)` **không** tạo file/thư mục vật lý, không kiểm tra chắc chắn nó tồn tại và không tự mở luồng dữ liệu. `File` chỉ lưu cách biểu diễn đường dẫn để các phương thức I/O sử dụng.

Các phép kiểm tra thường dùng:

| Phương thức | Ý nghĩa cần nhớ |
|---|---|
| `exists()` | Kiểm tra đường dẫn hiện đang tồn tại theo khả năng quan sát của runtime |
| `isDirectory()` | Kiểm tra đường dẫn hiện tham chiếu tới thư mục |
| `isFile()` | Kiểm tra đường dẫn hiện tham chiếu tới file thông thường |
| `getName()` | Thành phần tên cuối cùng của đường dẫn, không gồm parent |
| `getPath()` | Chuỗi đường dẫn được `File` biểu diễn |
| `getAbsolutePath()` | Dạng đường dẫn tuyệt đối theo cách runtime phân giải |
| `getCanonicalPath()` | Dạng đường dẫn chuẩn hóa theo quy tắc của filesystem; có thể ném `IOException` |
| `toPath()` | Chuyển sang `java.nio.file.Path`, thuận tiện khi dùng NIO.2 |

**Lưu ý:** Kết quả `exists()`, `isFile()` hay `isDirectory()` chỉ mô tả trạng thái tại thời điểm kiểm tra. Ngay sau đó, file có thể bị đổi tên, xóa hoặc thay bởi một process khác; vì vậy không dùng chuỗi “kiểm tra rồi thao tác” làm cơ chế khóa hoặc bảo mật tuyệt đối.

Một đường dẫn tới directory là đối tượng `File`; liệt kê directory mới là hành động I/O thực sự. Các phương thức `list` và `listFiles` chỉ liệt kê **con trực tiếp**, không tự duyệt các thư mục con.

## 2. Bản đồ API `list` và `listFiles`

| Phương thức | Kiểu trả về | Bộ lọc | Nội dung kết quả |
|---|---|---|---|
| `dir.list()` | `String[]` hoặc `null` | Không | Tên các mục trực tiếp trong thư mục |
| `dir.list(FilenameFilter filter)` | `String[]` hoặc `null` | `accept(File dir, String name)` | Tên các mục được filter chấp nhận |
| `dir.listFiles()` | `File[]` hoặc `null` | Không | Các đối tượng `File` cho các mục trực tiếp |
| `dir.listFiles(FilenameFilter filter)` | `File[]` hoặc `null` | Filter theo parent và tên | Các đối tượng `File` tương ứng mục được chấp nhận |
| `dir.listFiles(FileFilter filter)` | `File[]` hoặc `null` | `accept(File pathname)` | Các đối tượng `File` được filter chấp nhận |

Ví dụ thư mục tưởng tượng:

```text
data/
├── README.md
├── notes.txt
├── photo.png
└── images/
    └── icon.png
```

Nếu `File dir = new File("data")`:

- `dir.list()` cho mảng tên trực tiếp: `README.md`, `notes.txt`, `photo.png`, `images` (thứ tự **không được bảo đảm**).
- `dir.listFiles()` cho các `File` tương ứng với `data/README.md`, `data/notes.txt`, `data/photo.png`, `data/images` (biểu diễn đường dẫn phụ thuộc hệ điều hành).
- **Không** có `icon.png` trong kết quả của hai lời gọi trên, vì nó ở cấp sâu hơn.
- `list` có thể trả tên của **cả file lẫn thư mục**; tên chứa phần mở rộng không tự chứng minh đó là file thông thường.
- Mảng kết quả có thể rỗng, nhưng mảng rỗng **khác `null`**.

Các phương thức không bảo đảm thứ tự sắp xếp. Nếu trình bày kết quả cho người dùng, so sánh trong unit test hoặc cần thứ tự xử lý xác định, hãy **sắp xếp một cách tường minh**.

## 3. `list()`: lấy tên `String[]`

```java
File dir = new File("data");
String[] names = dir.list();

if (names == null) {
    System.err.println("Cannot list directory");
} else {
    Arrays.sort(names);
    for (String name : names) {
        System.out.println(name);
    }
}
```

Đây là **đoạn minh họa**, cần `import java.io.File;`, `import java.util.Arrays;`, và một thư mục `data` tồn tại để nhận dữ liệu thực tế.

Những điểm quan trọng:

1. `list()` trả về **tên** từng entry, **không phải** đường dẫn đầy đủ.
2. Nếu cần mở một mục, phải kết hợp tên với directory: `new File(dir, name)` hoặc `dir.toPath().resolve(name)`.
3. Nếu thư mục trống, kết quả thông thường là `new String[0]`, không phải `null`.
4. Nếu đường dẫn **không trỏ tới directory** hoặc xảy ra lỗi I/O khi liệt kê, `list()` có thể trả `null`. Ngoài ra, API có thể ném `SecurityException` khi kiểm tra quyền theo môi trường/security manager có liên quan; đừng mặc định mọi lỗi chỉ thể hiện qua `null`.
5. Giá trị do `list()` trả về chỉ là một **snapshot kết quả liệt kê tại lần gọi đó**; filesystem có thể thay đổi ngay sau đó.

Khi chỉ cần hiển thị tên, lọc phần mở rộng hoặc điền danh sách tên, `String[]` rất tiện. Khi cần kiểm tra `isFile()`, `length()`, `lastModified()` hoặc thao tác file, `File[]` thường giúp code rõ hơn.

## 4. `listFiles()`: lấy các đối tượng `File[]`

```java
File[] children = dir.listFiles();

if (children == null) {
    System.err.println("Cannot list directory");
} else {
    Arrays.sort(children, Comparator.comparing(File::getName));
    for (File child : children) {
        System.out.println(
            child.getName() + " / isFile=" + child.isFile()
        );
    }
}
```

Đây là **đoạn minh họa** với `dir` kiểu `File`; cần import `java.io.File`, `java.util.Arrays`, `java.util.Comparator`.

Mỗi phần tử của `File[]` biểu diễn đường dẫn đến một mục **trong directory**. Đối tượng `File` không tự bảo đảm mục đó vẫn tồn tại khi bạn gọi `isFile()`; kết quả kiểm tra được hệ điều hành cung cấp tại lúc gọi.

**Không nhầm `File[]` với các file đã mở.** Lấy mảng `File` không có nghĩa Java đang giữ `InputStream` hay file descriptor của từng mục, và không có `close()` cho các đối tượng `File` đơn thuần.

### Những lựa chọn thường gặp

- Muốn nhận **tên** rồi tự xử lý: `list()`.
- Muốn kiểm tra loại entry hoặc metadata: `listFiles()`.
- Muốn trả cả parent/name để callback quyết định: `FilenameFilter`.
- Muốn callback nhận thẳng object pathname: `FileFilter`.

Ví dụ `listFiles()` trên một file thông thường thay vì directory cũng có thể trả `null`, không phải một mảng chứa chính file đó.

## 5. `FilenameFilter`: nhận `dir` và `name`

Giao diện `java.io.FilenameFilter` là **functional interface** với một phương thức trừu tượng:

```java
boolean accept(File dir, String name);
```

Đây là **chữ ký minh họa**, không phải một hàm top-level để biên dịch độc lập.

- `dir`: directory đang được liệt kê.
- `name`: tên **đơn giản** của entry nằm ngay trong directory đó.
- Trả `true`: chấp nhận entry vào kết quả.
- Trả `false`: loại entry khỏi kết quả.

Ví dụ chỉ lấy tên có đuôi `.txt`:

```java
FilenameFilter onlyTxt =
    (parent, name) -> name.endsWith(".txt");

String[] txtNames = dir.list(onlyTxt);
File[] txtEntries = dir.listFiles(onlyTxt);
```

Đây là **đoạn minh họa**, cần import `java.io.File` và `java.io.FilenameFilter`; `dir` là directory `File`.

Hai lời gọi cùng điều kiện chấp nhận nhưng khác kiểu trả về: `String[]` và `File[]`.

Nếu cần kiểm tra loại đối tượng dù đang dùng `FilenameFilter`:

```java
FilenameFilter onlyTxtFiles = (parent, name) -> {
    File candidate = new File(parent, name);
    return candidate.isFile() && name.endsWith(".txt");
};
```

**Đúng:** Kết hợp directory và name bằng `new File(parent, name)`. **Sai:** `new File(name)` nếu kỳ vọng kiểm tra một đường dẫn trong `parent`: đường dẫn tương đối có thể bị phân giải theo working directory của JVM, dẫn tới kiểm tra nhầm chỗ.

### Phân biệt phần mở rộng và file thực

`name.endsWith(".txt")` chỉ xét **chuỗi tên**, vì vậy **một thư mục có tên `backup.txt` vẫn có thể được chấp nhận**. Nếu nghiệp vụ yêu cầu *file thông thường* có phần mở rộng `.txt`, cần kết hợp kiểm tra `isFile()` và tên.

### Phân biệt hoa/thường

`name.endsWith(".txt")` không chọn `README.TXT`. Nếu chính sách cho phép tên không phân biệt hoa/thường:

```java
FilenameFilter caseInsensitiveTxt =
    (parent, name) ->
        name.toLowerCase(Locale.ROOT).endsWith(".txt");
```

Đây là **đoạn minh họa**, cần `import java.util.Locale;`. `Locale.ROOT` giúp tránh phụ thuộc vào locale người dùng với quy tắc so sánh tên kiểu kỹ thuật. Tuy nhiên, đó vẫn chỉ là **chính sách lọc của ứng dụng**: không đồng nghĩa filesystem trên mọi nền tảng đều không phân biệt hoa/thường, và với Unicode có những trường hợp case mapping không thể xử lý đầy đủ chỉ bằng một lần `toLowerCase`.

## 6. `FileFilter`: nhận đường dẫn `File`

Giao diện `java.io.FileFilter` cũng là **functional interface**:

```java
boolean accept(File pathname);
```

Đây là **chữ ký minh họa**, không phải chương trình độc lập.

Khác `FilenameFilter`, callback nhận luôn object `File` đại diện entry; rất tiện để kiểm tra **file thông thường**, **directory**, **kích thước**, **thời gian sửa đổi**, hoặc điều kiện liên quan đường dẫn.

```java
FileFilter onlyRegularFiles = File::isFile;
FileFilter directoriesOnly = File::isDirectory;
FileFilter nonEmptyLogs = file ->
    file.isFile()
        && file.getName().endsWith(".log")
        && file.length() > 0L;

File[] regular = dir.listFiles(onlyRegularFiles);
File[] folders = dir.listFiles(directoriesOnly);
File[] logs = dir.listFiles(nonEmptyLogs);
```

Đây là **đoạn minh họa**, cần `java.io.File` và `java.io.FileFilter`, với `dir` trỏ thư mục. Nên kiểm tra `null` sau mỗi lời gọi trước khi lặp kết quả.

**Khác biệt phương thức:** `FileFilter` được dùng với `listFiles(FileFilter)`, **không có overload `list(FileFilter)`**. Nếu chỉ muốn nhận mảng tên nhưng điều kiện cần kiểm tra loại file, dùng `list(FilenameFilter)` và dựng `File` từ `parent/name`, hoặc dùng `listFiles(FileFilter)` rồi ánh xạ `File::getName`.

### Nếu callback tốn kém?

Một `FileFilter` gọi `isFile()` hoặc `length()` có thể phát sinh thêm lượt truy vấn metadata tới filesystem cho từng entry. Trong directory rất lớn hoặc storage từ xa, điều đó có thể tốn thời gian. Nếu chỉ cần lọc theo tên, `FilenameFilter` đơn giản thường đủ và tránh truy vấn metadata không cần thiết.

Đừng dựa vào số lượng hoặc thứ tự callback `accept` như một hợp đồng ổn định để thực hiện side effect (ghi/xóa/đổi tên file). Filter nên **không có side effect**, quyết định nhanh và nhất quán từ thông tin cần thiết.

## 7. So sánh `FilenameFilter` và `FileFilter`

| Tiêu chí | `FilenameFilter` | `FileFilter` |
|---|---|---|
| Phương thức | `accept(File dir, String name)` | `accept(File pathname)` |
| Đối số trực tiếp | Parent directory + tên con | `File` đại diện đường dẫn con |
| `list(FilenameFilter)` | Có | Không có `list(FileFilter)` |
| `listFiles(...)` | Có | Có |
| Phù hợp | Lọc tên, pattern, đuôi file | Lọc theo loại file, thuộc tính và đường dẫn |
| Lọc `isFile()` | Tự dựng `new File(dir, name)` | Gọi trên `pathname` |
| Lambda hai tham số | `(dir, name) -> ...` | Không |
| Lambda một tham số | Không | `file -> ...` |

Ví dụ đối chiếu cùng một điều kiện, chọn file `.csv` thông thường:

```java
FilenameFilter a = (parent, name) ->
    name.endsWith(".csv")
        && new File(parent, name).isFile();

FileFilter b = pathname ->
    pathname.isFile()
        && pathname.getName().endsWith(".csv");

File[] usingA = dir.listFiles(a);
File[] usingB = dir.listFiles(b);
```

Đây là **đoạn minh họa**, cần import `File`, `FileFilter`, `FilenameFilter`. Cả hai diễn đạt cùng ý định trong trường hợp filesystem không thay đổi và điều kiện kiểm tra tương đương. Chúng không bảo đảm kết quả như nhau nếu directory thay đổi **giữa hai lần gọi**.

### Hai overload `listFiles` có thể gây nhầm

Có hai overload cùng tên: `listFiles(FilenameFilter)` và `listFiles(FileFilter)`. Với lambda, số tham số thường giúp compiler xác định:

```java
dir.listFiles((parent, name) -> name.endsWith(".txt"));
dir.listFiles(file -> file.isFile());
```

Đây là **đoạn minh họa**; lambda hai tham số tương ứng `FilenameFilter`, lambda một tham số tương ứng `FileFilter`.

**Đặc biệt:** Lời gọi `dir.listFiles(null)` có thể **mơ hồ khi biên dịch** vì cả hai overload đều nhận kiểu tham chiếu và không có overload nào cụ thể hơn. Nếu thật sự muốn không lọc, hãy dùng `dir.listFiles()` thay vì truyền `null`.

## 8. Ví dụ Java 17 hoàn chỉnh: năm phương thức liệt kê và sắp xếp

Chương trình tự tạo directory tạm với ba file và một thư mục con. Toàn bộ output được sắp xếp nên không phụ thuộc vào thứ tự liệt kê của filesystem.

Lưu **`FileListingDemo.java`**:

```java
import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

public class FileListingDemo {
    private static String sorted(String[] names) {
        if (names == null) {
            return "<null>";
        }
        Arrays.sort(names);
        return Arrays.toString(names);
    }

    private static String sorted(File[] files) {
        if (files == null) {
            return "<null>";
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        String[] names = Arrays.stream(files)
            .map(File::getName)
            .toArray(String[]::new);
        return Arrays.toString(names);
    }

    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("file-list-");

        try {
            Files.writeString(
                root.resolve("notes.txt"),
                "note",
                StandardCharsets.UTF_8
            );
            Files.writeString(
                root.resolve("photo.png"),
                "not a real image",
                StandardCharsets.UTF_8
            );
            Files.writeString(
                root.resolve("readme.md"),
                "readme",
                StandardCharsets.UTF_8
            );
            Files.createDirectory(root.resolve("sub"));

            File directory = root.toFile();
            FilenameFilter txt = (parent, name) ->
                name.endsWith(".txt");
            FilenameFilter markdown = (parent, name) ->
                name.endsWith(".md");
            FileFilter regular = File::isFile;
            FileFilter folders = File::isDirectory;

            System.out.println(
                "list(): " + sorted(directory.list()));
            System.out.println(
                "listFiles(): " + sorted(directory.listFiles()));
            System.out.println(
                "list(txt): " + sorted(directory.list(txt)));
            System.out.println(
                "listFiles(markdown): " +
                    sorted(directory.listFiles(markdown)));
            System.out.println(
                "listFiles(regular): " +
                    sorted(directory.listFiles(regular)));
            System.out.println(
                "listFiles(folders): " +
                    sorted(directory.listFiles(folders)));

            File missing = root.resolve("absent").toFile();
            System.out.println(
                "missing list() is null: " +
                    (missing.list() == null));
        } finally {
            Files.deleteIfExists(root.resolve("notes.txt"));
            Files.deleteIfExists(root.resolve("photo.png"));
            Files.deleteIfExists(root.resolve("readme.md"));
            Files.deleteIfExists(root.resolve("sub"));
            Files.deleteIfExists(root);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac FileListingDemo.java
java FileListingDemo
```

Stdout khi thao tác filesystem thành công và không có process khác sửa thư mục tạm:

```text
list(): [notes.txt, photo.png, readme.md, sub]
listFiles(): [notes.txt, photo.png, readme.md, sub]
list(txt): [notes.txt]
listFiles(markdown): [readme.md]
listFiles(regular): [notes.txt, photo.png, readme.md]
listFiles(folders): [sub]
missing list() is null: true
```

Giải thích:

1. `list()` và `listFiles()` nhận bốn **con trực tiếp**.
2. `list(txt)` chỉ trả `String[]` chứa `notes.txt`.
3. `listFiles(markdown)` trả `File[]` chỉ chứa `readme.md`.
4. `listFiles(regular)` loại thư mục `sub`; `listFiles(folders)` chỉ chọn `sub`.
5. Đường dẫn `absent` không tồn tại, vì vậy `missing.list() == null`.
6. Helper `sorted(...)` sắp xếp kết quả trước khi in; **không được khẳng định** bản thân API luôn trả đúng thứ tự trên.

Ví dụ dùng `Files.createTempDirectory` và `Files.writeString` để dựng fixture; phần giải thích chính vẫn là các API **truyền thống** của `java.io.File`. `Files.writeString` có trong Java 11+, phù hợp yêu cầu Java 17. File giả `photo.png` chỉ minh họa rằng **đuôi tên không chứng minh định dạng nội dung**.

Phần `finally` xóa các mục theo tên cố định. Nếu việc tạo một file bị lỗi trước khi vào phần liệt kê, `deleteIfExists` giúp dọn những mục đã tạo; lỗi cleanup có thể ném `IOException`. Trong code production, cần cơ chế dọn dẹp file tạm giữ nguyên nguyên nhân lỗi gốc khi điều đó quan trọng. Không dùng mã xóa tự động trên directory chứa dữ liệu của người dùng.

## 9. Ví dụ Java 17: cùng đuôi tên nhưng khác loại entry

Đôi khi dùng mỗi `name.endsWith(".csv")` là chưa đủ. Chương trình sau tạo hai file có đuôi `.csv` và một **thư mục** cũng có đuôi `.csv`, sau đó so sánh lọc theo tên với lọc theo metadata.

Lưu **`FileFilterVsFilenameFilterDemo.java`**:

```java
import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class FileFilterVsFilenameFilterDemo {
    private static String names(File[] files) {
        if (files == null) {
            return "<null>";
        }
        String[] values = Arrays.stream(files)
            .map(File::getName)
            .sorted()
            .toArray(String[]::new);
        return Arrays.toString(values);
    }

    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("filter-demo-");

        try {
            Files.writeString(root.resolve("empty.csv"), "");
            Files.writeString(root.resolve("filled.csv"), "id\n1\n");
            Files.createDirectory(root.resolve("archive.csv"));

            File directory = root.toFile();

            FilenameFilter bySuffix =
                (parent, name) -> name.endsWith(".csv");

            FileFilter realCsvFiles = file ->
                file.isFile()
                    && file.getName().endsWith(".csv");

            FileFilter nonEmptyCsvFiles = file ->
                file.isFile()
                    && file.getName().endsWith(".csv")
                    && file.length() > 0;

            System.out.println("suffix: " +
                names(directory.listFiles(bySuffix)));
            System.out.println("regular: " +
                names(directory.listFiles(realCsvFiles)));
            System.out.println("nonempty: " +
                names(directory.listFiles(nonEmptyCsvFiles)));
        } finally {
            Files.deleteIfExists(root.resolve("empty.csv"));
            Files.deleteIfExists(root.resolve("filled.csv"));
            Files.deleteIfExists(root.resolve("archive.csv"));
            Files.deleteIfExists(root);
        }
    }
}
```

Chạy:

```bash
javac FileFilterVsFilenameFilterDemo.java
java FileFilterVsFilenameFilterDemo
```

Stdout khi tạo/đọc directory thành công:

```text
suffix: [archive.csv, empty.csv, filled.csv]
regular: [empty.csv, filled.csv]
nonempty: [filled.csv]
```

Giải thích từng bộ lọc:

- `bySuffix` chỉ xét **tên**, nên cả thư mục `archive.csv` được nhận.
- `realCsvFiles` yêu cầu `isFile()`, nên loại directory.
- `nonEmptyCsvFiles` còn yêu cầu `length() > 0`; vì `filled.csv` chứa `id\n1\n`, chỉ nó được chọn.

`File.length()` có ý nghĩa với file thông thường nhưng không thể thay thế một quy trình xác minh nội dung file. Nếu file thay đổi giữa lúc lọc và lúc mở, kích thước hoặc loại entry có thể khác; `isFile()` và `length()` là **các truy vấn trạng thái tức thời**, không tạo giao dịch nguyên tử.

### Lọc tên không đồng nghĩa lọc nội dung

Một file tên `report.csv` vẫn có thể chứa dữ liệu nhị phân sai định dạng CSV. Nếu ứng dụng phải xử lý CSV thực sự, sau bước liệt kê cần mở và parse nội dung bằng bộ parser phù hợp, kiểm tra charset, schema, escaping và xử lý lỗi.

## 10. Trả về `null`, mảng rỗng và lỗi quyền truy cập

Điểm dễ sai nhất của họ `list` là **không phải lúc nào kết quả cũng là một mảng hợp lệ**.

| Trường hợp | Kỳ vọng hợp lý |
|---|---|
| Directory tồn tại, truy cập được, không có entry | `String[0]` hoặc `File[0]` |
| Directory tồn tại, có n entry và liệt kê thành công | Mảng n phần tử (hoặc ít hơn khi filter) |
| Đường dẫn không tồn tại | `null` |
| Đường dẫn trỏ tới file, không phải directory | `null` |
| Lỗi I/O khi liệt kê | Có thể trả `null` |
| Vi phạm kiểm tra quyền bảo mật theo runtime | Có thể ném `SecurityException` |
| Permission hoặc filesystem thay đổi bất ngờ | Không thể coi `null` hay kết quả cũ là chẩn đoán duy nhất |

**`null` không nói chính xác nguyên nhân.** Đừng biến `null` thành “directory rỗng”. Ví dụ sai:

```java
for (File f : dir.listFiles()) {
    System.out.println(f.getName());
}
```

Đây là **phản ví dụ**: nếu `listFiles()` trả `null`, vòng for-each sẽ ném `NullPointerException`.

Một cách bảo vệ tối thiểu:

```java
File[] entries = dir.listFiles();
if (entries == null) {
    throw new IllegalStateException(
        "Cannot list directory: " + dir);
}
for (File entry : entries) {
    System.out.println(entry.getName());
}
```

Đây là **đoạn minh họa**, biến `dir` là `File`. Việc ném `IllegalStateException` phù hợp cho ví dụ đơn giản nhưng không tái tạo nguyên nhân `IOException` vốn không được API `listFiles()` cung cấp trực tiếp. Nếu xử lý lỗi chi tiết là yêu cầu quan trọng, cân nhắc API NIO.2 như `Files.newDirectoryStream()` hoặc `Files.list()`, vốn có cách báo lỗi rõ hơn.

### Có nên gọi `isDirectory()` trước?

Bạn có thể làm:

```java
if (!dir.isDirectory()) {
    System.err.println("Not a readable directory: " + dir);
    return;
}
File[] entries = dir.listFiles();
if (entries == null) {
    System.err.println("Listing failed: " + dir);
    return;
}
```

Đây là **mã minh họa bên trong một phương thức trả về `void`**; nếu đặt trong mã khác cần điều chỉnh `return`. Tuy nhiên, việc `isDirectory()` trả `true` **không đảm bảo** `listFiles()` thành công; filesystem có thể thay đổi và quyền truy cập có thể khác. Vẫn bắt buộc xử lý `null` sau khi gọi.

## 11. Thứ tự phần tử và cách sắp xếp

JDK **không quy định thứ tự** trả về của `list` hoặc `listFiles`. Không xây dựng logic dựa trên giả định “theo thứ tự tạo”, “theo thứ tự tên” hay “theo độ mới”.

Sắp xếp tên:

```java
String[] names = dir.list();
if (names != null) {
    Arrays.sort(names);
}
```

Sắp xếp `File[]` theo tên:

```java
File[] files = dir.listFiles();
if (files != null) {
    Arrays.sort(files, Comparator.comparing(File::getName));
}
```

Đây là **các đoạn minh họa**, cần import `Arrays`, `Comparator`, `File`.

- `Arrays.sort(names)` so sánh chuỗi Unicode theo quy tắc `String.compareTo`; không đồng nghĩa xếp chữ cái theo văn hóa của người Việt.
- Nếu cần thứ tự phù hợp ngôn ngữ/locale, cân nhắc `Collator` với locale được khai báo rõ.
- Nếu chỉ cần thứ tự ổn định trong test, comparator theo tên là đủ khi các tên không trùng trong một directory.
- Sort mảng lớn cần thêm bộ nhớ/thời gian; với listing hàng triệu entry, nên cân nhắc pipeline streaming/giới hạn đầu vào.

Không so sánh kết quả thực tế của `listFiles()` với expected array theo **vị trí** nếu chưa sort cả hai.

## 12. Không tự đệ quy: muốn duyệt thư mục con phải chủ động

Với cấu trúc:

```text
root/
├── top.txt
└── child/
    └── nested.txt
```

`new File("root").listFiles()` chỉ cho `top.txt` và directory `child`; nó **không** cho `nested.txt`.

Một ví dụ đệ quy khái niệm:

```java
static void visit(File directory) {
    File[] children = directory.listFiles();
    if (children == null) {
        return; // Cần chính sách báo lỗi phù hợp ứng dụng.
    }
    for (File child : children) {
        if (child.isDirectory()) {
            visit(child);
        } else if (child.isFile()) {
            System.out.println(child.getPath());
        }
    }
}
```

Đây là **đoạn minh họa**, không phải chương trình độc lập. Nó cố tình đơn giản, **không thích hợp để dùng nguyên xi trên cây không tin cậy**, vì:

- Symlink tới directory có thể dẫn đến vòng lặp hoặc đi ra ngoài root nếu implementation đi theo liên kết và ứng dụng không kiểm soát.
- Cây sâu có thể gây `StackOverflowError`.
- Directory có thể thay đổi trong lúc duyệt; `listFiles()` có thể trả `null` mà ví dụ lặng lẽ bỏ qua.
- Liệt kê hàng triệu phần tử tạo mảng lớn.
- Quyền truy cập, filesystem từ xa, device node hoặc đường dẫn đặc biệt có thể yêu cầu chính sách riêng.

Với mã sản phẩm cần duyệt sâu, hãy xem `Files.walkFileTree` cùng `FileVisitor`, giới hạn độ sâu, chính sách `FOLLOW_LINKS`, xử lý lỗi và kiểm soát vòng lặp. Nếu chỉ duyệt một cấp, không cần recursion.

## 13. Vấn đề bảo mật: symlink, race condition và dữ liệu người dùng

Các phương thức `File.isFile()`, `isDirectory()` và `listFiles()` quan sát trạng thái **có thể thay đổi**. Nếu ứng dụng quét directory mà người dùng không tin cậy có thể sửa, đừng kết luận một pathname luôn chỉ đến cùng một object qua các bước:

```text
T1: listFiles() thấy "upload.txt"
T2: isFile() trả true
T3: process khác thay "upload.txt" thành liên kết/đích khác
T4: ứng dụng mở và xử lý đường dẫn đó
```

Đây là ví dụ **TOCTOU** (*time of check to time of use*). Kiểm tra tại T2 không khóa object cho thao tác T4.

Điểm phòng tránh ở mức thiết kế:

1. Không tin tên file từ nguồn không kiểm soát; kiểm tra ràng buộc đường dẫn theo hệ thống của bạn.
2. Không dựa duy nhất vào `getCanonicalPath()`, `isFile()` hoặc `startsWith` string để giải quyết mọi cuộc đua symlink. Việc check và open vẫn có thể là hai bước khác nhau.
3. Dùng API và cờ mở theo chính sách mong muốn: NIO.2 có `LinkOption.NOFOLLOW_LINKS` cho những thao tác thích hợp và `SecureDirectoryStream` trên provider có hỗ trợ; không giả định nó có sẵn ở mọi filesystem.
4. Giới hạn root được phép truy cập, quyền OS, depth, số entry, thời gian và tài nguyên xử lý.
5. Khi ứng dụng chỉ phân tích dữ liệu thông thường trong directory do chính nó tạo và kiểm soát, các filter đơn giản thường đủ; không cần phức tạp hóa nếu threat model không yêu cầu.

**FileFilter không phải cơ chế bảo mật.** Nó là callback quyết định một entry xuất hiện trong kết quả listing; nó không ngăn process khác thay directory và không bảo vệ thao tác mở file sau đó.

## 14. `java.io.File` truyền thống so với NIO.2

Các API từ Java 7 trong `java.nio.file` có thể giúp xử lý lỗi và duyệt directory linh hoạt hơn. Bài này tập trung vào `File`, nhưng cần biết hướng di chuyển khi viết mã mới.

| Nhu cầu | `java.io.File` | NIO.2 thường dùng |
|---|---|---|
| Liệt kê con trực tiếp | `File.listFiles()` | `Files.list(Path)` |
| Liệt kê con theo filter | `listFiles(FileFilter)`, `listFiles(FilenameFilter)` | `Files.newDirectoryStream(Path, filter)` |
| Chỉ khớp glob tên | `FilenameFilter` viết điều kiện | `Files.newDirectoryStream(dir, "*.txt")` |
| Duyệt cây thư mục | Tự recursion | `Files.walk` / `Files.walkFileTree` |
| Có thể nhận `IOException` rõ hơn | `listFiles()` có thể trả `null` | `Files.list`/`DirectoryStream` báo lỗi bằng exception |
| Quản lý vòng đời listing | Mảng không phải resource cần close | Stream/directory stream cần `close()` |
| Tính năng filesystem hiện đại | API hạn chế | `Path`, `Files`, attributes, link options |

**`Files.list(Path)` trả `Stream<Path>` cần đóng**; đừng bỏ quên try-with-resources. Stream liệt kê cũng không tự bảo đảm thứ tự; nếu cần output theo tên, sort tường minh. Trong lúc duyệt, lỗi I/O xảy ra muộn có thể xuất hiện qua `UncheckedIOException`.

`DirectoryStream<Path>` thường phù hợp khi muốn duyệt entry theo lô mà không tạo toàn bộ `File[]` cùng lúc. Tuy nhiên nó không tự sort, không tự recursion, cần close, và vẫn có các vấn đề trạng thái filesystem thay đổi.

### Ví dụ Java 17: so sánh `listFiles` và `Files.list`

Lưu **`FileAndNioListingDemo.java`**:

```java
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileAndNioListingDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("nio-list-");

        try {
            Files.writeString(root.resolve("b.txt"), "b");
            Files.writeString(root.resolve("a.txt"), "a");

            File[] oldApi = root.toFile().listFiles();
            if (oldApi == null) {
                throw new IOException("Legacy listing failed");
            }
            String legacy = Arrays.stream(oldApi)
                .map(File::getName)
                .sorted()
                .collect(Collectors.joining(", "));

            String modern;
            try (Stream<Path> entries = Files.list(root)) {
                modern = entries
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .collect(Collectors.joining(", "));
            }

            System.out.println("File: " + legacy);
            System.out.println("NIO: " + modern);
        } finally {
            Files.deleteIfExists(root.resolve("a.txt"));
            Files.deleteIfExists(root.resolve("b.txt"));
            Files.deleteIfExists(root);
        }
    }
}
```

Chạy:

```bash
javac FileAndNioListingDemo.java
java FileAndNioListingDemo
```

Stdout khi thao tác file thành công:

```text
File: a.txt, b.txt
NIO: a.txt, b.txt
```

Điều chứng minh: hai API có thể liệt kê cùng các **entry trực tiếp**. Khác biệt ở **kiểu trả về và cơ chế vòng đời**: `File[]` là mảng đã nhận về, còn `Files.list` tạo `Stream<Path>` cần được đóng để giải phóng resource. Không dùng ví dụ này để kết luận NIO tự sort; hai nhánh đều gọi `sorted()`.

## 15. Khi nào dùng `list(FilenameFilter)` và khi nào `listFiles(FileFilter)`?

### Trường hợp A — chọn tên cấu hình

Yêu cầu: “Liệt kê các file có tên `config-*.properties` để hiển thị”. Nếu chỉ cần tên và quy tắc tên, `list(FilenameFilter)` ngắn gọn:

```java
String[] configs = dir.list((parent, name) ->
    name.startsWith("config-") && name.endsWith(".properties")
);
```

Đây là **đoạn minh họa**. Nếu chỉ chọn file thông thường, thêm `new File(parent, name).isFile()`; nếu cần pattern phức tạp, cân nhắc glob từ NIO.2.

### Trường hợp B — chọn file log lớn hơn 1 MiB

Yêu cầu: “Chỉ các file thông thường `.log` có kích thước lớn hơn 1 MiB”.

```java
FileFilter largeLogs = file ->
    file.isFile()
        && file.getName().endsWith(".log")
        && file.length() > 1024L * 1024L;

File[] result = dir.listFiles(largeLogs);
```

Đây là **đoạn minh họa**; `dir` là `File`, cần import `File` và `FileFilter`. `FileFilter` đọc được `File.length()` trực tiếp, nhưng kết quả vẫn là trạng thái nhất thời; không thay thế kiểm tra trong quá trình mở/đọc file thật.

### Trường hợp C — chỉ liệt kê thư mục con

```java
File[] directories = dir.listFiles(File::isDirectory);
```

Đây là **đoạn minh họa**. Java suy ra `FileFilter` từ method reference một tham số. Cần kiểm tra `null` như mọi lời gọi `listFiles`.

### Trường hợp D — chỉ muốn đuôi `.txt` và kết quả là `File[]`

```java
File[] texts = dir.listFiles(
    (parent, name) -> name.endsWith(".txt")
);
```

Đây là **đoạn minh họa**; lambda hai tham số chọn `FilenameFilter`, nhưng kiểu trả về vẫn là `File[]`. Đừng lầm rằng “dùng FilenameFilter thì bắt buộc nhận String[]”: **phương thức bạn gọi** quyết định kiểu trả về.

## 16. Lỗi phổ biến và cách khắc phục

1. **Không kiểm tra `null` trước khi lặp.** `list()`/`listFiles()` có thể thất bại; phân biệt `null` với mảng rỗng.
2. **Tưởng `list()` trả đường dẫn đầy đủ.** Nó trả tên con; khi mở cần `new File(parent, name)`.
3. **Dùng `new File(name)` trong `FilenameFilter` để kiểm tra entry.** Việc này có thể kiểm tra sai working directory; dùng `new File(dir, name)`.
4. **Tưởng tên `.txt` chắc chắn là file.** Directory cũng có thể tên `archive.txt`; kiểm tra loại khi cần.
5. **Tưởng `FileFilter` và `FilenameFilter` hoán đổi được ở mọi overload.** Không có `list(FileFilter)`; hai `listFiles` overload nhận kiểu callback khác.
6. **Tưởng kết quả đã sort.** API không bảo đảm; sort kết quả với comparator rõ ràng.
7. **Tưởng `listFiles()` đệ quy.** Chỉ con trực tiếp; muốn đệ quy phải thiết kế traversal.
8. **Tin `isDirectory()` trước listing là đủ.** Directory có thể biến mất hoặc quyền truy cập thay đổi; vẫn phải xử lý kết quả.
9. **Bỏ qua trường hợp quyền truy cập hạn chế.** Legacy API đôi khi chỉ trả `null`, không cung cấp nguyên nhân chi tiết; dùng NIO.2 khi cần báo lỗi cụ thể.
10. **Lọc dựa trên tên mà tin nội dung/định dạng cũng đúng.** Đuôi file không chứng minh dữ liệu hợp lệ; kiểm tra nội dung khi xử lý.
11. **Đưa side effect vào `accept()`.** Filter nên là hàm kiểm tra, tránh xóa/di chuyển các entry trong khi đang liệt kê.
12. **Dùng recursion đơn giản trên cây có symlink không kiểm soát.** Có thể gặp vòng lặp, vượt root hoặc tràn stack; cân nhắc NIO.2 với chính sách liên kết.
13. **Bỏ qua TOCTOU.** Kết quả `listFiles()` không đảm bảo file vẫn tồn tại hoặc không bị thay ở lúc mở.
14. **Đọc toàn bộ directory cực lớn vào `File[]` mà không cân nhắc bộ nhớ.** Khi quy mô lớn, `DirectoryStream` và streaming traversal có thể tốt hơn.
15. **Quên đóng `Files.list`/directory stream.** Khác với mảng `File[]`, những API này giữ tài nguyên cần được đóng qua TWR.
16. **Tự coi so sánh tên với `equalsIgnoreCase` là cùng semantics filesystem.** Mỗi filesystem có quy tắc so sánh đường dẫn/case/Unicode riêng; cần chính sách ứng dụng rõ ràng.
17. **Hiểu sai `FileFilter` là bộ lọc bảo mật.** Nó chỉ chọn kết quả liệt kê, không khóa quyền truy cập và không chống sửa entry đồng thời.

## 17. Bài tập tự kiểm tra

**Câu 1.** Khác biệt giữa `File.list()` và `File.listFiles()`?

**Đáp án:** `list()` trả `String[]` tên entry; `listFiles()` trả `File[]` đại diện đường dẫn entry. Cả hai chỉ con trực tiếp và có thể trả `null`.

**Câu 2.** `FilenameFilter.accept(File dir, String name)` nhận `name` là đường dẫn đầy đủ hay tên con?

**Đáp án:** Tên con. Nếu cần truy vấn file, tạo `new File(dir, name)`.

**Câu 3.** Muốn lọc các **file** `.csv` nhưng loại directory cũng mang tên `.csv`, viết thế nào?

**Đáp án:** Dùng `FileFilter file -> file.isFile() && file.getName().endsWith(".csv")`, hoặc `FilenameFilter` kết hợp `new File(dir, name).isFile()`.

**Câu 4.** `listFiles((parent, name) -> ...)` trả `String[]` hay `File[]`?

**Đáp án:** `File[]`. Lambda hai tham số chọn overload `FilenameFilter`; tên phương thức `listFiles` quyết định kiểu trả về.

**Câu 5.** `listFiles()` trả `null` có nghĩa thư mục rỗng?

**Đáp án:** Không. Directory rỗng thường trả mảng độ dài 0; `null` có thể báo không phải directory hoặc lỗi I/O. Cần báo lỗi thay vì giả định rỗng.

**Câu 6.** API có bảo đảm file được trả theo tên alphabet không?

**Đáp án:** Không. Phải tự sort nếu cần thứ tự xác định.

**Câu 7.** `File.listFiles()` có tự lấy các file ở mọi directory cấp dưới không?

**Đáp án:** Không. Nó chỉ liệt kê entry trực tiếp; phải chủ động duyệt cây hoặc dùng NIO.2 traversal.

**Câu 8.** Vì sao kiểm tra `file.isFile()` rồi `Files.readString(file.toPath())` vẫn có rủi ro?

**Đáp án:** File có thể bị thay/xóa giữa lúc kiểm tra và lúc đọc (TOCTOU). Kết quả filter không khóa identity hoặc trạng thái file.

**Câu 9.** Nếu cần duyệt directory rất lớn và thông tin lỗi rõ ràng hơn, nên xem API nào?

**Đáp án:** `Files.newDirectoryStream(Path)` hoặc `Files.list(Path)`; dùng try-with-resources để đóng. Nếu duyệt nhiều cấp, `Files.walk` hoặc `Files.walkFileTree` theo yêu cầu.

**Câu 10.** Khi nào chỉ lọc theo tên là đủ, khi nào nên kiểm tra metadata?

**Đáp án:** Nếu bài toán chỉ hiển thị/chọn tên theo mẫu, `FilenameFilter` đơn giản thường đủ. Khi yêu cầu file thông thường, directory, kích thước hoặc thuộc tính, `FileFilter` phù hợp hơn; nhớ rằng metadata vẫn có thể đổi khi thao tác thật.

## Tổng kết

- `list()` và `list(FilenameFilter)` trả **`String[]` tên con**.
- `listFiles()`, `listFiles(FilenameFilter)`, `listFiles(FileFilter)` trả **`File[]` đường dẫn con**.
- `FilenameFilter` nhận **`(dir, name)`**; `FileFilter` nhận **`File pathname`**.
- Cả hai họ API chỉ liệt kê **một cấp**, **không bảo đảm thứ tự**, có thể trả **`null`**, và không ngăn filesystem thay đổi trong lúc xử lý.
- Khi cần sắp xếp, kiểm tra loại mục, duyệt sâu, chống TOCTOU hoặc xử lý lỗi tốt hơn, chọn API/phương án phù hợp, thường là `Path` và NIO.2 cho mã mới.

Nắm được sự khác biệt giữa **tên**, **đường dẫn**, **loại entry** và **kết quả liệt kê** sẽ giúp bạn sử dụng lớp `java.io.File` truyền thống đúng và tránh những lỗi I/O khó phát hiện.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="legacy-file-io-007">mkdir, mkdirs, createNewFile, renameTo và delete</a>

<details>
<summary>Xem chi tiết</summary>

# Java File truyền thống: mkdir(), mkdirs(), createNewFile(), renameTo() và delete()

## 1. Mục tiêu và bối cảnh

**java.io.File** là API Java truyền thống đại diện cho **đường dẫn trừu tượng** đến file hoặc thư mục trên hệ thống file. Một đối tượng File **không tự tạo file/thư mục trên ổ đĩa** chỉ vì bạn gọi constructor.

Ví dụ:

~~~java
File folder = new File("data/reports");
File report = new File(folder, "daily.txt");
~~~

Đây là **mảnh code minh họa**, cần import **java.io.File**. Sau hai lệnh trên, Java chỉ có hai đối tượng đại diện cho đường dẫn. Muốn thực sự tạo thư mục hoặc file, bạn phải gọi API như **mkdir()**, **mkdirs()**, **createNewFile()**.

Năm phương thức chính:

| Phương thức | Công dụng | Kiểu trả về | Điều quan trọng |
| --- | --- | --- | --- |
| **mkdir()** | Tạo **một** thư mục | boolean | Thư mục cha phải tồn tại |
| **mkdirs()** | Tạo thư mục đích **và các thư mục cha còn thiếu** | boolean | Trả false khi thư mục đích đã có; thất bại có thể để lại một số thư mục cha |
| **createNewFile()** | Tạo file rỗng **chỉ khi tên chưa tồn tại** | boolean, có thể ném IOException | Kiểm tra tồn tại và tạo mới là một thao tác nguyên tử theo hợp đồng API |
| **renameTo(File dest)** | Đổi tên hoặc di chuyển đường dẫn nguồn sang đích | boolean | Thành công phụ thuộc hệ thống file; không bảo đảm nguyên tử |
| **delete()** | Xóa một file hoặc **thư mục rỗng** | boolean | Không tự xóa đệ quy cây thư mục |

**Quy tắc lớn nhất:** Với các thao tác trả **boolean**, phải **kiểm tra kết quả**. Trả false không cung cấp chẩn đoán chi tiết “vì sao thất bại”. Khi viết ứng dụng mới cần thông báo lỗi chính xác, **java.nio.file.Files** và **Path** thường phù hợp hơn.

## 2. Mô hình đường dẫn của java.io.File

Đoạn code:

~~~java
File root = new File("workspace");
File child = new File(root, "images");
File image = new File(child, "logo.png");
~~~

biểu diễn quan hệ:

~~~text
workspace/
└── images/
    └── logo.png
~~~

**Nhưng các đường dẫn này chưa chắc tồn tại trên đĩa.** Đây chỉ là mô hình tên đường dẫn. **File** cũng không “mở file”, không giữ stream và không bảo đảm loại tài nguyên cuối cùng: một đường dẫn có thể chưa tồn tại, là thư mục, là file thường hoặc tài nguyên khác theo hệ thống file.

Các phép kiểm tra:

~~~java
boolean exists = image.exists();
boolean regularFile = image.isFile();
boolean directory = child.isDirectory();
File parent = image.getParentFile();
~~~

Đây là **mảnh code minh họa**. **exists()**, **isFile()**, **isDirectory()** quan sát trạng thái hệ thống file ở một thời điểm, có thể chịu ảnh hưởng quyền truy cập. Kết quả kiểm tra **không khóa trạng thái**: đối tượng khác có thể tạo, đổi tên hoặc xóa đường dẫn ngay sau khi chương trình kiểm tra.

Cách ghép **new File(parent, child)** giúp tránh tự nối chuỗi đường dẫn bằng dấu **/** hoặc **\\**. Đường dẫn tương đối được diễn giải theo thư mục làm việc của tiến trình; đường dẫn tuyệt đối chỉ rõ nơi đến, nhưng vẫn phụ thuộc môi trường. **File.separator** và **Path.resolve(...)** là cách biểu diễn đường dẫn tương thích đa hệ điều hành hơn việc hard-code dấu phân cách.

## 3. mkdir(): chỉ tạo đúng một cấp thư mục

Chữ ký:

~~~java
public boolean mkdir()
~~~

**mkdir()** yêu cầu hệ thống tạo thư mục đúng tại đường dẫn của File. Nó **không tự tạo các thư mục cha** còn thiếu.

Giả sử thư mục **project** đã tồn tại:

~~~text
project/
~~~

Gọi:

~~~java
File logs = new File("project/logs");
boolean created = logs.mkdir();
~~~

Nếu có quyền ghi và không có xung đột, thư mục **project/logs** được tạo và **created == true**. Ngược lại, nếu cố tạo trực tiếp **project/archive/2026** mà **project/archive** chưa có, **mkdir()** không tự tạo **archive**, nên thường trả false.

**Các lý do trả false** gồm: thư mục đã tồn tại, tên đã tồn tại nhưng là file, thư mục cha thiếu, không đủ quyền hoặc lỗi hệ thống file. Khi chỉ nhìn boolean, các nguyên nhân không được phân biệt chính xác.

**Điểm dễ nhầm:** Gọi **mkdir()** lần thứ hai trên thư mục vừa tạo thường trả **false**. False ở đây không chứng minh rằng thư mục đang bị lỗi: phương thức chỉ trả true khi **chính lần gọi đó đã tạo thành công**.

## 4. mkdirs(): tạo cả cây thư mục còn thiếu

Chữ ký:

~~~java
public boolean mkdirs()
~~~

**mkdirs()** tạo thư mục đích và các cấp thư mục cha còn thiếu nếu cần.

Ví dụ một thư mục gốc hợp lệ đã tồn tại:

~~~text
data/
~~~

Với:

~~~java
File output = new File("data/2026/october/reports");
boolean created = output.mkdirs();
~~~

nếu thành công, kết quả:

~~~text
data/
└── 2026/
    └── october/
        └── reports/
~~~

Lần gọi đầu **có thể trả true** khi thành công; lần tiếp theo trên cùng đường dẫn thường **trả false** vì đích đã tồn tại. Điều đó khác với một API kiểu “đảm bảo tồn tại”, vốn có thể được gọi nhiều lần mà không coi việc tồn tại là lỗi.

### 4.1. mkdir() so với mkdirs()

| Tình huống | mkdir() | mkdirs() |
| --- | --- | --- |
| Tạo **data/logs**, thư mục **data** đã có | Có thể thành công | Có thể thành công |
| Tạo **data/a/b**, **a** chưa có | Thường **false** | Có thể tạo cả **a** và **b** |
| Thư mục đích đã tồn tại | **false** | **false** |
| Một thư mục cha trùng tên file thường | Thất bại | Thất bại |
| Tạo một phần cây rồi có lỗi ở cấp sau | Không tự tạo các cấp cha | **Có thể đã tạo một số thư mục cha** trước khi thất bại |

Không được nghĩ rằng **mkdirs()** là giao dịch “được hết hoặc không tạo gì”. Tài liệu Java lưu ý nếu thao tác thất bại, **một số thư mục cha có thể đã được tạo**, dù thư mục đích không có.

### 4.2. Khi thư mục đã tồn tại thì nên xử lý thế nào?

Nếu mục tiêu nghiệp vụ là “thư mục phải tồn tại và đúng là một thư mục”, thay vì chỉ “lần gọi này tạo được thư mục”, có thể viết:

~~~java
File directory = new File("data/reports");
if (!directory.isDirectory()) {
    if (!directory.mkdirs() && !directory.isDirectory()) {
        throw new IllegalStateException(
                "Không thể chuẩn bị thư mục: " + directory);
    }
}
~~~

Đây là **mảnh mã minh họa**. Phép kiểm tra **isDirectory()** sau khi mkdirs thất bại giúp nhận diện trường hợp một tiến trình khác vừa tạo thư mục, nhưng vẫn **không loại bỏ hoàn toàn race condition** nếu tài nguyên có thể thay đổi đồng thời.

Với mã hiện đại, **Files.createDirectories(path)** là cách thích hợp hơn: tạo cha khi cần, có thể được gọi khi thư mục đích đã tồn tại và trả Path nếu hoàn tất, trong khi lỗi I/O thường được biểu diễn bằng **IOException** cụ thể.

## 5. createNewFile(): tạo file rỗng nếu chưa tồn tại

Chữ ký:

~~~java
public boolean createNewFile() throws IOException
~~~

**createNewFile()** tạo một **file rỗng** tại đường dẫn khi **chưa có mục file system với tên đó**.

- **true**: tạo mới thành công trong lần gọi này.
- **false**: đường dẫn đích đã tồn tại.
- **IOException**: có lỗi I/O như thư mục cha không có, không tạo được đối tượng file hoặc lỗi do hệ thống file; trường hợp cụ thể còn tùy môi trường.
- **SecurityException**: có thể xảy ra khi chính sách bảo mật của môi trường từ chối thao tác.

Ví dụ:

~~~java
File folder = new File("data");
if (!folder.isDirectory() && !folder.mkdirs()) {
    throw new IOException("Không tạo được thư mục data");
}

File report = new File(folder, "report.txt");
if (report.createNewFile()) {
    System.out.println("Đã tạo file rỗng");
} else {
    System.out.println("Tên file đã tồn tại");
}
~~~

Đây là **mảnh code minh họa**, cần import **java.io.File** và **java.io.IOException**, đồng thời nằm trong phương thức khai báo hoặc xử lý IOException. Khi file chưa có và tạo thành công, kích thước ban đầu là **0 byte**. **createNewFile() không tự ghi nội dung, không tạo thư mục cha và không mở OutputStream để người gọi ghi dữ liệu**.

### 5.1. Tính nguyên tử của tạo mới

Theo hợp đồng **createNewFile()**, việc **kiểm tra đường dẫn chưa tồn tại** và **tạo file mới** được thực hiện như **một thao tác nguyên tử** đối với các hoạt động hệ thống file có thể tác động đến đường dẫn.

Đây là lý do nên dùng:

~~~java
boolean created = file.createNewFile();
~~~

thay vì:

~~~java
if (!file.exists()) {
    file.createNewFile();
}
~~~

khi mục tiêu là **chỉ tạo mới một lần nếu chưa có**. Hai lần thao tác tách rời (exists rồi create) tạo ra khoảng thời gian để tiến trình khác can thiệp. Vẫn phải kiểm tra giá trị trả về của createNewFile. Đồng thời, **không dùng createNewFile như cơ chế file-lock đáng tin**; tài liệu Java khuyên dùng **FileLock** cho bài toán khóa file.

Nếu cần **mở ngay một luồng ghi và chỉ chấp nhận tạo file mới**, ứng dụng hiện đại có thể dùng **Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)**. Cách này đặt điều kiện tạo mới ở thao tác mở luồng thay vì tạo xong rồi mở lại, giúp tránh khoảng trống giữa hai hoạt động riêng.

## 6. renameTo(File dest): đổi tên hoặc chuyển vị trí nhưng phụ thuộc nền tảng

Chữ ký:

~~~java
public boolean renameTo(File dest)
~~~

**renameTo(dest)** yêu cầu đổi tên hoặc di chuyển đối tượng tại đường dẫn của nguồn sang đường dẫn đích.

Ví dụ:

~~~java
File source = new File("data/old.txt");
File destination = new File("data/new.txt");

boolean renamed = source.renameTo(destination);
if (!renamed) {
    System.err.println("Không đổi tên được; cần xử lý lỗi");
}
~~~

Đây là **mảnh code minh họa**, cần import **java.io.File** và dữ liệu nguồn phải tồn tại để rename có thể thành công.

### 6.1. Những điều KHÔNG được giả định

1. **Không bảo đảm luôn chuyển được giữa hai filesystem/ổ đĩa.** Chuyển trong cùng filesystem có thể thành công, trong khi chuyển qua vùng lưu trữ khác thất bại.
2. **Không bảo đảm thao tác luôn nguyên tử.** Ngữ nghĩa cụ thể phụ thuộc nền tảng và hệ thống file.
3. **Không bảo đảm ghi đè khi đường dẫn đích đã tồn tại.** Có hệ thống cho phép trong một số trường hợp, có hệ thống từ chối; không coi replace là hợp đồng chung.
4. **Không bảo đảm tự tạo thư mục cha của đích.** Phải chuẩn bị đường dẫn cha riêng.
5. **Không cập nhật chính đối tượng File nguồn.** File là đối tượng biểu diễn đường dẫn bất biến: **source** vẫn giữ tên cũ sau một lần rename thành công, còn **destination** giữ tên mới.
6. **Không tự ném IOException diễn giải mọi lỗi thất bại.** Đối với nhiều lỗi rename, phương thức chỉ trả **false**; SecurityException vẫn có thể xuất hiện do kiểm soát quyền.

Nếu renameTo trả true, có thể sử dụng đối tượng **destination** để thao tác với đường dẫn mới. Nếu trả false, **không tiếp tục như thể thao tác đã thành công**; cần kiểm tra trạng thái thực tế và xử lý theo hợp đồng của ứng dụng.

### 6.2. API thay thế: Files.move(...)

Nên cân nhắc **Files.move(sourcePath, destinationPath)** khi cần exception rõ nghĩa hơn:

~~~java
Path sourcePath = Path.of("data", "old.txt");
Path destinationPath = Path.of("data", "new.txt");

Files.move(sourcePath, destinationPath);
~~~

Đây là **mảnh minh họa**, cần import **java.nio.file.Files**, **java.nio.file.Path** và xử lý **IOException**. Mặc định, với đường dẫn đích đã tồn tại, Files.move thường báo lỗi; khi cần thay thế có thể chỉ định **StandardCopyOption.REPLACE_EXISTING** và chấp nhận chính sách thay thế rõ ràng.

Nếu nghiệp vụ yêu cầu đổi tên **nguyên tử**, có thể thử **StandardCopyOption.ATOMIC_MOVE**. Hệ thống file không hỗ trợ có thể ném **AtomicMoveNotSupportedException**; **không** được tự mặc định rằng một lần move không có ATOMIC_MOVE cũng nguyên tử. Thứ tự xử lý khi kết hợp option, tài nguyên đang mở và quy tắc ghi đè phụ thuộc hợp đồng cụ thể của Files.move và filesystem, vì vậy cần xác định rõ điều kiện trước khi triển khai.

## 7. delete(): xóa file hoặc thư mục rỗng, không xóa đệ quy

Chữ ký:

~~~java
public boolean delete()
~~~

**delete()** trả true **chỉ nếu thao tác xóa thành công**; trả false nếu không xóa được. Đường dẫn có thể trỏ đến:

- **File thường**: delete yêu cầu xóa file, tùy quyền và quy tắc của OS.
- **Thư mục rỗng**: có thể xóa.
- **Thư mục còn file/thư mục con**: **không được xóa bằng File.delete()**; trả false.
- **Đường dẫn không tồn tại**: thường trả false.

Ví dụ:

~~~java
File file = new File("data/report.txt");
if (!file.delete()) {
    System.err.println("Chưa xóa được: " + file);
}
~~~

Đây là **mảnh code minh họa**. Trả false không nói rõ do file không tồn tại, file còn mở trên một số hệ điều hành, thiếu quyền hay lý do khác. Các trường hợp làm việc với liên kết tượng trưng (**symbolic link**) cần chú ý ý nghĩa xóa đường dẫn/entry và không được suy diễn “xóa mục này” tức là xóa toàn bộ nội dung mà symlink trỏ đến.

**File.delete() không có chế độ recursive**. Muốn xóa cây thư mục, ứng dụng phải có quy trình duyệt và xóa có kiểm soát, thường theo thứ tự **con trước cha**; đây là thao tác nguy hiểm với dữ liệu người dùng nên cần giới hạn đường dẫn và tránh symlink escape.

### 7.1. So sánh với Files.delete và Files.deleteIfExists

~~~java
Files.delete(path);
~~~

- **Files.delete(path)** xóa đường dẫn, có thể ném **NoSuchFileException** nếu thiếu hoặc **DirectoryNotEmptyException** nếu thư mục không rỗng (khả năng báo lỗi cụ thể phụ thuộc hệ thống file).
- **Files.deleteIfExists(path)** trả **false** nếu không tồn tại; vẫn có thể ném **IOException** cho các lý do khác.
- Cả hai **không tự biến thành xóa đệ quy**.
- Cần xử lý đặc biệt khi dữ liệu quan trọng: không thử xóa hàng loạt chỉ vì một phương thức trả false.

### 7.2. deleteOnExit() không phải thay thế cho delete()

**File.deleteOnExit()** đăng ký yêu cầu xóa khi JVM kết thúc **bình thường**, không xóa ngay lúc gọi. Không được dựa vào nó để bảo đảm dọn sạch sau crash hay khi tiến trình bị kill; với ứng dụng chạy lâu, số lượng đăng ký cũng có thể tăng và không giải phóng như một cơ chế quản lý bộ nhớ thông thường. Với dữ liệu tạm quan trọng, nên dọn ngay trong **finally** hoặc try-with-resources thích hợp.

## 8. Ví dụ Java 17+ độc lập, tạo trong thư mục tạm an toàn

Để không xóa file thực tế của người học, chương trình dưới đây tạo **một thư mục tạm mới** rồi minh họa đủ năm phương thức. Tên thư mục tạm ngẫu nhiên nên **không in đường dẫn cụ thể**. Chương trình chỉ thao tác với tài nguyên tự tạo và có **finally** dọn dẹp nếu phát sinh lỗi.

Lưu thành **LegacyFileMethodsDemo.java**:

~~~java
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public class LegacyFileMethodsDemo {
    public static void main(String[] args) throws IOException {
        File root = Files.createTempDirectory("legacy-file-demo-").toFile();

        File single = new File(root, "single");
        File nested = new File(root, "a/b/c");
        File source = new File(single, "old.txt");
        File destination = new File(single, "new.txt");

        try {
            System.out.println("mkdir=" + single.mkdir());

            // Cha "a/b" chưa có: mkdir() chỉ tạo 1 cấp.
            System.out.println("nested.mkdir=" + nested.mkdir());
            System.out.println("nested.mkdirs=" + nested.mkdirs());
            System.out.println("nested.mkdirs again=" + nested.mkdirs());

            System.out.println("createNewFile=" + source.createNewFile());
            System.out.println("createNewFile again=" + source.createNewFile());

            boolean moved = source.renameTo(destination);
            if (!moved) {
                throw new IOException("renameTo thất bại trên filesystem này");
            }
            System.out.println("renameTo=" + moved);

            // Root còn con nên không được xóa bằng delete().
            System.out.println("root.delete nonempty=" + root.delete());

            System.out.println("destination.delete=" + destination.delete());
            System.out.println("single.delete=" + single.delete());
            System.out.println("nested.delete=" + nested.delete());
            System.out.println("b.delete=" + nested.getParentFile().delete());
            System.out.println("a.delete=" +
                    nested.getParentFile().getParentFile().delete());
            System.out.println("root.delete=" + root.delete());
        } finally {
            // Dọn tài nguyên tự tạo nếu lỗi khiến một số thư mục còn lại.
            if (root.exists()) {
                try (Stream<Path> paths = Files.walk(root.toPath())) {
                    for (Path path :
                            paths.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(path);
                    }
                }
            }
        }
    }
}
~~~

Lệnh:

~~~bash
javac LegacyFileMethodsDemo.java
java LegacyFileMethodsDemo
~~~

**Stdout trên một filesystem cục bộ bình thường**, khi quyền tạo/xóa hợp lệ và **renameTo** trong cùng thư mục thành công:

~~~text
mkdir=true
nested.mkdir=false
nested.mkdirs=true
nested.mkdirs again=false
createNewFile=true
createNewFile again=false
renameTo=true
root.delete nonempty=false
destination.delete=true
single.delete=true
nested.delete=true
b.delete=true
a.delete=true
root.delete=true
~~~

**Các giá trị trong stdout trên là kết quả dự kiến trong môi trường hợp lệ, không phải cam kết rằng mọi filesystem luôn cho cùng kết quả**: đặc biệt **renameTo** có thể thất bại; code chủ động báo IOException thay vì in “thành công” giả. Thư mục tạm được tạo độc nhất, nên không trùng với thư mục của lần chạy trước. Dọn dẹp trong **finally** dùng Files.walk với thứ tự đảo để xóa con trước cha, chỉ trong **root** của lần chạy; khi một path không thể xóa, chương trình vẫn có thể ném IOException khi cleanup. Đây là ví dụ kỹ thuật, không phải bộ xóa thư mục tùy ý do người dùng nhập.

Phân tích lần lượt:

1. **single.mkdir()** tạo thư mục một cấp dưới root → true.
2. **nested.mkdir()** thất bại vì **a/b** chưa có → false.
3. **nested.mkdirs()** tạo **a**, **b**, **c** → true. Gọi lại khi c đã tồn tại → false.
4. **source.createNewFile()** tạo file rỗng, lần thứ hai không tạo thêm → true rồi false.
5. **source.renameTo(destination)** đổi tên trong cùng thư mục; nếu true, file nằm ở đường dẫn destination.
6. **root.delete()** khi root chưa rỗng → false.
7. Xóa file và thư mục theo thứ tự từ trong ra ngoài → từng lời gọi trả true trong trường hợp thành công.

## 9. Khi trả false, nên chẩn đoán và xử lý thế nào?

Trước hết, không nên gộp tất cả false thành một loại lỗi. Dưới đây là **câu hỏi kiểm tra**, không phải phép suy luận chắc chắn vì trạng thái hệ thống file có thể thay đổi đồng thời.

| Lệnh trả false | Khả năng cần kiểm tra |
| --- | --- |
| **mkdir()** | Cha đã tồn tại? Đích đã tồn tại? Có quyền tạo thư mục? |
| **mkdirs()** | Đích là thư mục sẵn có? Một cấp cha lại là file? Có phần cây đã được tạo một phần? |
| **createNewFile()** | Theo hợp đồng: tên đích đã tồn tại; lỗi tạo file khác thường ném IOException |
| **renameTo()** | Đích đã tồn tại? Chuyển khác filesystem? Hạn chế nền tảng/quyền? |
| **delete()** | Mục tiêu còn tồn tại? Là thư mục không rỗng? Thiếu quyền? File bị khóa? |

**Không dùng exists() rồi kết luận chắc chắn về nguyên nhân thất bại**: giữa các lần gọi, một tiến trình khác có thể thay đổi trạng thái. Nếu cần thông tin chẩn đoán, chuyển sang API Files có ngoại lệ cụ thể sẽ hiệu quả hơn việc cố đoán từ boolean.

Với mỗi thao tác, ứng dụng cần xác định **hậu điều kiện mong muốn**:

- Nếu mục tiêu là **“thư mục phải tồn tại”**, xác minh **isDirectory()** (hoặc dùng Files.createDirectories).
- Nếu mục tiêu là **“phải tạo file mới, không đè cái cũ”**, dựa vào kết quả **createNewFile()** hoặc dùng chế độ **CREATE_NEW**.
- Nếu mục tiêu là **“đã di chuyển thành công”**, kiểm tra **renameTo() == true** rồi tiếp tục với **đường dẫn mới**; với yêu cầu chặt chẽ hơn, dùng Files.move.
- Nếu mục tiêu là **“đã xóa thành công”**, kiểm tra **delete() == true** hoặc dùng Files.delete và xử lý IOException.

## 10. java.io.File so với java.nio.file.Files: chọn API nào?

**java.io.File** vẫn rất thường gặp trong codebase cũ, thư viện lâu đời và API cần kiểu File. Tuy nhiên, với Java 17, **Path/Files** thường rõ ràng hơn về lỗi và tùy chọn.

| Tác vụ | File truyền thống | java.nio.file.Files |
| --- | --- | --- |
| Tạo **một** thư mục | **file.mkdir()** | **Files.createDirectory(path)** |
| Tạo thư mục với cha còn thiếu | **file.mkdirs()** | **Files.createDirectories(path)** |
| Tạo file rỗng chỉ khi chưa tồn tại | **file.createNewFile()** | **Files.createFile(path)** |
| Đổi tên/di chuyển | **source.renameTo(dest)** | **Files.move(source, dest, options...)** |
| Xóa file/thư mục rỗng | **file.delete()** | **Files.delete(path)** |
| Xóa nếu có, không báo lỗi khi thiếu | Kiểm tra giá trị boolean của delete, nhưng không phân biệt nguyên nhân | **Files.deleteIfExists(path)** |
| Chuyển biểu diễn | **file.toPath()** | **path.toFile()** (với Path thuộc filesystem hỗ trợ) |

Ví dụ minh họa cách làm hiện đại, giả định đã import **java.nio.file.*** và **java.io.IOException**:

~~~java
Path base = Path.of("data");
Files.createDirectories(base.resolve("reports"));

Path a = Files.createFile(base.resolve("reports").resolve("a.txt"));
Path b = base.resolve("reports").resolve("b.txt");

Files.move(a, b);          // Nếu không thành công sẽ báo IOException.
Files.delete(b);           // Nếu không xóa được sẽ báo IOException.
~~~

Đây là **mảnh code**, không phải chương trình tự chạy: nó giả định quyền truy cập phù hợp, thư mục làm việc chứa vị trí "data" và file đích **b.txt** chưa tồn tại. Chạy lại khi a.txt/b.txt có trạng thái khác có thể cho kết quả khác. Ví dụ nhằm minh họa API, không mô tả thao tác an toàn để áp dụng trên dữ liệu thật khi chưa xác nhận tên đích.

**Files.createDirectories(...)** phù hợp để chuẩn bị directory idempotent hơn: khi thư mục đích đã tồn tại đúng loại, lệnh thường hoàn tất mà không coi đó là thất bại. Nhưng **nếu có file thường chiếm tên thư mục**, việc tạo vẫn thất bại. Trên một số nền tảng, thao tác tạo cây có thể tạo được một phần trước khi lỗi; không tự coi đó là giao dịch.

## 11. An toàn dữ liệu và race condition

### 11.1. Tránh kiểm tra rồi thao tác như một giao dịch

Ví dụ không an toàn trước cạnh tranh:

~~~java
if (!file.exists()) {
    file.createNewFile();
}
~~~

Một tiến trình khác có thể tạo file ngay sau **exists()**. Khi cần tạo duy nhất, **createNewFile()** thực hiện kiểm tra và tạo cùng một thao tác; nếu đồng thời cần mở file để ghi, cân nhắc Files.newOutputStream với **CREATE_NEW**.

Tương tự, **isDirectory()** trước **mkdir()** hoặc **exists()** trước **delete()** chỉ là ảnh chụp trạng thái tại một thời điểm. Không thể lấy các phép kiểm tra này để tuyên bố đường dẫn “chắc chắn” không đổi trong ứng dụng nhiều tiến trình.

### 11.2. Không xóa/đổi tên dựa trên đường dẫn chưa xác thực

Với tên file lấy từ đầu vào người dùng, **đừng ghép trực tiếp chuỗi rồi xóa**, bởi các thành phần như **..**, đường dẫn tuyệt đối, symlink hoặc bất đồng giữa chuẩn hóa đường dẫn có thể làm thao tác ảnh hưởng đến nơi ngoài phạm vi dự định. Xác thực phạm vi truy cập ở tầng thích hợp, tránh xóa đệ quy tùy tiện; nếu cần thao tác an toàn trước race/symlink, cân nhắc API và thiết kế có cơ chế kiểm soát quyền thật sự.

Đối với file quan trọng, có thể ghi nội dung vào **file tạm trong cùng filesystem**, hoàn tất flush/đồng bộ khi cần rồi mới **Files.move(...)** theo hợp đồng thay thế phù hợp. **Không khẳng định mọi hệ thống đều hỗ trợ atomic move**; phải xử lý trường hợp không hỗ trợ.

### 11.3. Thư mục không rỗng và tài nguyên đang mở

Một số hệ điều hành hạn chế đổi tên hoặc xóa file đang được chương trình khác mở. Ngay cả khi API trả true, việc hiển thị trạng thái sau thao tác có thể chịu ảnh hưởng caching hoặc race từ bên ngoài. Để tránh mất dữ liệu, nên dùng **try-with-resources** cho I/O stream và kiểm tra kết quả các thao tác file.

**SecurityException** là ngoại lệ do cơ chế kiểm soát quyền trong những môi trường có SecurityManager/chính sách tương ứng, khác với **IOException**. API boolean không có nghĩa là không bao giờ phát sinh exception nào.

## 12. Lỗi thường gặp và cách tránh

1. **Tưởng new File(...) tự tạo file trên đĩa.** Constructor chỉ tạo đối tượng đường dẫn; phải gọi API tạo.
2. **Dùng mkdir() cho đường dẫn nhiều cấp có cha chưa tồn tại.** Dùng mkdirs() hoặc Files.createDirectories.
3. **Xem mkdirs() == false đồng nghĩa thư mục chắc chắn không tồn tại.** Nó thường false khi đích đã có; kiểm tra hậu điều kiện hoặc dùng Files.createDirectories.
4. **Tin mkdirs() thất bại thì không tạo ra gì.** Có thể đã tạo một phần cây, cần dọn hoặc khôi phục có kiểm soát.
5. **Gọi createNewFile() rồi cho rằng đã ghi dữ liệu.** File mới rỗng; ghi nội dung bằng OutputStream, Files.write... nếu cần.
6. **Viết exists() rồi createNewFile() như kiểm tra nguyên tử.** Kết quả exists có thể cũ; ưu tiên hợp đồng tạo mới nguyên tử.
7. **Không kiểm tra boolean từ renameTo().** Nó có thể false do filesystem hoặc đích tồn tại; không giả định rename luôn thành công.
8. **Sau renameTo thành công vẫn dùng File nguồn như thể đường dẫn đã tự đổi.** Đối tượng File nguồn vẫn giữ path cũ; dùng File dest.
9. **Dùng renameTo như cơ chế thay thế nguyên tử đa nền tảng.** Không được bảo đảm; cân nhắc Files.move với option tường minh.
10. **Thử file.delete() trên thư mục còn con.** Chỉ xóa được thư mục rỗng; delete không recursive.
11. **Tưởng File.delete() sẽ giải thích chi tiết lỗi.** Nó thường chỉ trả false; Files.delete có IOException hữu ích hơn.
12. **Xóa từ cha trước khi xóa con.** Muốn dọn cây tự tạo, phải xóa con trước cha, và phải giới hạn đúng phạm vi.
13. **Dùng deleteOnExit() thay cho dọn dẹp chắc chắn.** Nó không bảo đảm chạy khi JVM bị dừng bất thường.
14. **Hard-code dấu phân cách đường dẫn.** Dùng **new File(parent, child)** hoặc **Path.resolve**.
15. **Không cân nhắc chạy lại chương trình.** Lần thứ hai, file/thư mục có thể đã tồn tại; kết quả boolean thay đổi hợp lệ.
16. **Thử xóa hoặc đổi tên đường dẫn do người khác cung cấp mà chưa xác thực.** Có thể gây mất dữ liệu hoặc thao tác ra ngoài vùng cho phép.

## 13. Câu hỏi ôn tập

**Câu 1.** Có thư mục **data** nhưng chưa có **data/2026**. Lệnh nào phù hợp hơn để tạo **data/2026/reports** trong một lần?  
**Đáp:** **mkdirs()**; nó tạo cả cha còn thiếu. **mkdir()** không tự tạo data/2026.

**Câu 2.** Vì sao **mkdirs()** lần thứ hai thường trả false nhưng **Files.createDirectories()** có thể hoàn tất?  
**Đáp:** mkdirs trả true khi thực sự tạo thư mục đích cùng cha cần thiết trong lần gọi; Files.createDirectories cho phép đường dẫn đích đã tồn tại đúng loại mà không coi đó là lỗi.

**Câu 3.** **createNewFile()** trả false có nghĩa đã tạo được file nhưng không ghi dữ liệu?  
**Đáp:** Không. Theo hợp đồng, false nghĩa tên đích **đã tồn tại**. Khi vừa tạo mới thành công, kết quả là true và file ban đầu rỗng.

**Câu 4.** **renameTo()** có luôn đổi được tên file giữa hai ổ đĩa không?  
**Đáp:** Không. Nó phụ thuộc nền tảng và filesystem; phải kiểm tra boolean hoặc dùng Files.move để xử lý lỗi/tùy chọn rõ hơn.

**Câu 5.** Sau **source.renameTo(dest)** thành công, **source** có tự trỏ đến tên mới không?  
**Đáp:** Không. File đại diện cho đường dẫn bất biến; **source** vẫn mang đường dẫn cũ, còn **dest** là biểu diễn đích.

**Câu 6.** Có thư mục chứa file; gọi **directory.delete()** có xóa mọi thứ bên trong không?  
**Đáp:** Không. File.delete không recursive và chỉ có thể xóa thư mục rỗng.

**Câu 7.** Muốn biết lý do xóa thất bại, nên dùng API nào?  
**Đáp:** **Files.delete(path)** với IOException cụ thể, thay vì chỉ dựa trên giá trị boolean của File.delete.

**Câu 8.** Nếu cần tạo file chỉ khi chưa tồn tại **và mở ngay để ghi**, API nào đáng cân nhắc?  
**Đáp:** **Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)** để áp dụng điều kiện tạo mới ngay khi mở luồng.

## 14. Tổng kết

- **mkdir()**: tạo đúng **một** thư mục, không tự tạo cha.
- **mkdirs()**: tạo cả **các thư mục cha còn thiếu**, nhưng có thể để lại cây tạo dở khi lỗi.
- **createNewFile()**: **tạo file rỗng nếu chưa tồn tại**, trả false nếu tên đã có; việc kiểm tra và tạo là một thao tác nguyên tử.
- **renameTo(dest)**: yêu cầu đổi tên/di chuyển, **phụ thuộc nền tảng**, không bảo đảm nguyên tử hoặc ghi đè; đối tượng File nguồn không đổi đường dẫn.
- **delete()**: xóa **file hoặc thư mục rỗng**, không tự xóa đệ quy; trả false nếu không xóa được.

Trong code mới, **Path + Files** thường là lựa chọn nên ưu tiên khi cần xử lý lỗi đầy đủ, tùy chọn rõ ràng hoặc thao tác an toàn hơn. Dù dùng API nào, hãy kiểm tra hậu điều kiện, tránh xóa dữ liệu ngoài phạm vi và nhận thức rằng hệ thống file có thể thay đổi đồng thời.

### Tham khảo chính thức

- [Oracle Java SE 17 — java.io.File](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/File.html): mkdir, mkdirs, createNewFile, renameTo, delete, deleteOnExit và kết quả boolean.
- [Oracle Java SE 17 — java.nio.file.Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html): createDirectory, createDirectories, createFile, move, delete, deleteIfExists.
- [Oracle Java SE 17 — StandardOpenOption](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardOpenOption.html): CREATE_NEW và các tùy chọn mở file.
- [Oracle Java SE 17 — StandardCopyOption](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardCopyOption.html): REPLACE_EXISTING và ATOMIC_MOVE.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="legacy-file-io-008">Giới hạn thông tin lỗi của API trả về boolean</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn thông tin lỗi của API trả về boolean trong java.io.File

Trong Java I/O truyền thống, nhiều phương thức của **`java.io.File`** chỉ trả về `boolean`: `delete()`, `mkdir()`, `mkdirs()`, `renameTo()`, `setLastModified()`, `setReadable()`, `setWritable()`, `setExecutable()` và `setReadOnly()`. Một số phương thức kiểm tra trạng thái như `exists()`, `isFile()`, `isDirectory()`, `canRead()` cũng trả về boolean.

Thiết kế này giúp câu lệnh ngắn gọn, nhưng **một giá trị `false` không đủ để biết tại sao thao tác thất bại**. Chẳng hạn, `file.delete() == false` có thể có nghĩa đường dẫn không tồn tại, thư mục không rỗng, quyền truy cập không đủ hoặc hệ thống tập tin đang gặp trở ngại. `false` chỉ báo thao tác không thành công theo hợp đồng, **không chứa `IOException`, error code, nguyên nhân hoặc stack trace từ hệ điều hành**.

Khi cần xử lý lỗi rõ ràng, Java cung cấp **`java.nio.file.Files`** với các phương thức như `Files.delete`, `Files.createDirectory`, `Files.move` hoặc `Files.readAttributes`. Nhiều phương thức này ném `IOException` hoặc lớp con cụ thể, cung cấp tín hiệu chẩn đoán tốt hơn.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Phân biệt **boolean cho kết quả thao tác**, **boolean để kiểm tra trạng thái**, và **ngoại lệ I/O**.
- Chỉ ra vì sao `File.delete()`, `mkdir()`, `mkdirs()` và `renameTo()` thường không cung cấp đủ thông tin nguyên nhân khi trả `false`.
- Nhận diện ngoại lệ đặc biệt: `File.createNewFile()` cũng trả boolean nhưng **ném `IOException` khi lỗi I/O xảy ra**.
- Viết cách xử lý tương đương bằng `Files.delete`, `Files.deleteIfExists`, `Files.createDirectory`, `Files.createDirectories` và `Files.move`.
- Phân biệt tình trạng “không tồn tại” với “không xác định được” ở các API kiểm tra `Files.exists`/`Files.notExists`.
- Tránh **TOCTOU** (*time-of-check to time-of-use*): kiểm tra trước không bảo đảm thao tác sau thành công.
- Viết log thông tin lỗi vừa đủ, bảo toàn nguyên nhân, và quyết định retry hoặc báo thất bại phù hợp.
- Hiểu hạn chế về quyền truy cập, symbolic link, thư mục không rỗng và tính phụ thuộc nền tảng của file API.

## 2. `java.io.File` đại diện cho đường dẫn, không phải một file đã mở

`File` biểu diễn **abstract pathname**: đường dẫn tới một file hoặc thư mục tiềm năng. Tạo đối tượng `File` không tự tạo file, không tự mở file và không chứng minh đối tượng thực sự tồn tại:

~~~java
java.io.File file = new java.io.File("reports/output.txt");
~~~

Dòng trên chỉ tạo đối tượng mô tả đường dẫn. Khi gọi `file.exists()` hoặc `file.delete()`, phương thức mới truy vấn/yêu cầu thao tác trên hệ thống tập tin.

Các phương thức boolean legacy có **những ý nghĩa khác nhau**, không thể gom mọi kết quả `false` thành “I/O bị lỗi”:

| Nhóm | Phương thức | `true` thường có nghĩa | `false` có thể là |
| --- | --- | --- | --- |
| Thao tác xóa | `delete()` | Đã xóa file/thư mục | Không tìm thấy hoặc không xóa được vì lý do khác |
| Tạo thư mục đơn | `mkdir()` | Tạo thư mục đích thành công | Đã tồn tại, cha không tồn tại, thiếu quyền hoặc lỗi |
| Tạo nhiều cấp | `mkdirs()` | Đã tạo thư mục cần thiết theo hợp đồng trả về | Thư mục đích đã tồn tại hoặc không tạo được; có thể đã tạo một số thư mục cha |
| Đổi tên/di chuyển | `renameTo(dest)` | Đổi tên theo provider/nền tảng đã thành công | Thất bại vì nhiều nguyên nhân; không có `IOException` chi tiết |
| Thay metadata | `setLastModified(...)`, `setReadOnly()`, `setWritable(...)`... | Thao tác cập nhật thành công | Thiếu quyền, nền tảng không hỗ trợ, đường dẫn không phù hợp... |
| Kiểm tra tồn tại | `exists()` | Đường dẫn tồn tại tại thời điểm kiểm tra | Đường dẫn không tồn tại hoặc truy vấn không xác định chính xác trạng thái |
| Kiểm tra loại | `isFile()`, `isDirectory()` | Đúng loại tương ứng tại lúc kiểm tra | Không tồn tại, loại khác hoặc không xác định chắc chắn |
| Kiểm tra quyền | `canRead()`, `canWrite()`, `canExecute()` | Ứng dụng được báo là có khả năng tương ứng | Không tồn tại, không có quyền hoặc không xác định chắc chắn |

Nhóm **thao tác** thường dùng boolean để báo thành công/thất bại. Nhóm **kiểm tra** dùng boolean để mô tả một đặc tính tại thời điểm hỏi. Phải đọc riêng hợp đồng của từng API: ví dụ `isDirectory()==false` **không khẳng định** rằng file tồn tại và là file thường.

## 3. Ví dụ: `File.delete() == false` không nói lý do

Theo hợp đồng `File.delete()`, giá trị `true` nghĩa là file/thư mục được xóa thành công; `false` nghĩa là không xóa được. Một số khả năng khi false:

1. Đường dẫn không tồn tại.
2. Đường dẫn chỉ đến một thư mục còn chứa mục con.
3. Quyền xóa trên thư mục cha hoặc chính sách hệ thống tập tin không cho phép.
4. File đang được sử dụng theo cách khiến nền tảng không cho xóa.
5. File system/thiết bị không sẵn sàng, lỗi I/O hoặc các giới hạn khác.

**Chỉ từ `false` bạn không thể kết luận nguyên nhân nào đã xảy ra.** Nếu sau đó gọi `exists()` và thấy `false` cũng không chứng minh file vốn đã thiếu: tác vụ khác có thể vừa xóa nó hoặc quyền truy vấn có thể khác.

### Java 17: tái hiện trường hợp không tồn tại

Tạo file **`BooleanDeleteDemo.java`**:

~~~java
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public class BooleanDeleteDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("boolean-delete-");
        Path missing = root.resolve("never-created.txt");
        File legacy = missing.toFile();

        try {
            System.out.println("File.delete=" + legacy.delete());

            try {
                Files.delete(missing);
                System.out.println("unexpected success");
            } catch (NoSuchFileException exception) {
                System.out.println("Files.delete="
                        + exception.getClass().getSimpleName());
            }

            System.out.println("Files.deleteIfExists="
                    + Files.deleteIfExists(missing));
        } finally {
            Files.deleteIfExists(root);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 BooleanDeleteDemo.java
java BooleanDeleteDemo
~~~

Kết quả khi không có tác nhân ngoài can thiệp vào thư mục tạm:

~~~text
File.delete=false
Files.delete=NoSuchFileException
Files.deleteIfExists=false
~~~

Cùng tình huống thiếu file, ba API báo kết quả theo ba **hợp đồng khác nhau**:

- `File.delete()` → `false`, không có lý do được truyền lên.
- `Files.delete(path)` → ném `NoSuchFileException` (một dạng `FileSystemException`/`IOException`).
- `Files.deleteIfExists(path)` → `false` vì file không tồn tại; tuy vẫn trả boolean, phương thức **có thể ném `IOException` cho lỗi xóa**. Đây là lựa chọn phù hợp nếu nghiệp vụ coi “đã không còn file” là kết quả chấp nhận được.

`Files.deleteIfExists` không cam kết tính nguyên tử tuyệt đối trong mọi hệ thống tập tin; kiểm tra/xóa và các thao tác khác vẫn có thể tương tác với nhau.

## 4. Ví dụ: thư mục không rỗng cho thấy lợi ích của ngoại lệ cụ thể

Khi muốn xóa một thư mục, `File.delete()` chỉ xóa được nếu thư mục rỗng. Nếu thư mục còn file con, nó thường trả `false` mà không cho biết lý do cụ thể.

Tạo file **`NonEmptyDirectoryDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;

public class NonEmptyDirectoryDemo {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("nonempty-");
        Path child = Files.createFile(directory.resolve("item.txt"));

        try {
            System.out.println("legacyDelete="
                    + directory.toFile().delete());

            try {
                Files.delete(directory);
                System.out.println("unexpected success");
            } catch (DirectoryNotEmptyException exception) {
                System.out.println("nioDelete="
                        + exception.getClass().getSimpleName());
            }
        } finally {
            Files.deleteIfExists(child);
            Files.deleteIfExists(directory);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac NonEmptyDirectoryDemo.java
java NonEmptyDirectoryDemo
~~~

Kết quả điển hình trên hệ thống tập tin thông thường:

~~~text
legacyDelete=false
nioDelete=DirectoryNotEmptyException
~~~

`DirectoryNotEmptyException` là **ngoại lệ cụ thể được API cho phép**, nhưng không phải mọi file system provider đều buộc phải dùng đúng lớp con này: đặc tả Java đánh dấu một số ngoại lệ đặc thù của `Files.delete` là *optional specific exception*. Một provider vẫn có thể báo `IOException` tổng quát. Vì thế:

- Bắt `DirectoryNotEmptyException` để xử lý trường hợp đó khi provider cung cấp.
- Bắt hoặc cho phép lan truyền `IOException` cho các lỗi còn lại.
- Không kết luận mọi lỗi xóa thư mục không rỗng nhất định luôn có đúng kiểu exception như ví dụ.

Một ứng dụng thực tế **không tự động xóa hết thư mục con** chỉ vì thấy thư mục chưa rỗng. Việc xóa đệ quy là một quyết định có tính phá hủy khác, cần xác nhận phạm vi rõ ràng.

## 5. `mkdir()` và `mkdirs()`: false có thể là “đã tồn tại”

Xem hai lần gọi trên cùng đường dẫn:

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DirectoryBooleanDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("mkdir-demo-");
        Path child = root.resolve("cache");

        try {
            System.out.println("first=" + child.toFile().mkdir());
            System.out.println("second=" + child.toFile().mkdir());
            System.out.println("isDirectory=" + Files.isDirectory(child));
        } finally {
            Files.deleteIfExists(child);
            Files.deleteIfExists(root);
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac DirectoryBooleanDemo.java
java DirectoryBooleanDemo
~~~

Output trong môi trường bình thường, không có thay đổi đồng thời:

~~~text
first=true
second=false
isDirectory=true
~~~

Lần đầu tạo thành công, lần thứ hai trả `false` vì đích **đã là thư mục**. Đây không nhất thiết là lỗi nghiệp vụ: nếu mục tiêu “bảo đảm thư mục tồn tại”, trạng thái hiện tại là hợp lệ.

### Khác nhau giữa `mkdir` và `mkdirs`

- `mkdir()` chỉ cố tạo **thư mục cuối cùng**; các thư mục cha phải tồn tại.
- `mkdirs()` cũng cố tạo các **thư mục cha còn thiếu**.
- Cả hai có thể trả `false` nếu không tạo được thư mục hoặc thư mục đích đã tồn tại.
- `mkdirs()` có thể tạo thành công **một phần cây thư mục** trước khi thất bại; không giả định false nghĩa là không có thay đổi nào trên đĩa.

Đối chiếu NIO:

~~~java
Path dir = Path.of("data", "archive");

Files.createDirectory(dir);   // Tạo đúng một cấp; có thể ném IOException.
Files.createDirectories(dir); // Tạo các cấp còn thiếu; nếu đã là thư mục, thành công.
~~~

Đây là **mảnh mã** cần `import java.nio.file.Files;`, `Path` và xử lý `IOException`. Không nên gọi hai lệnh liên tiếp như trên trong cùng bài toán nếu không có lý do; chúng thể hiện hai chiến lược khác nhau.

`Files.createDirectory` có thể ném `FileAlreadyExistsException` khi đích đã tồn tại; `Files.createDirectories` hỗ trợ yêu cầu “thư mục cuối cùng phải tồn tại”, nhưng nếu đường dẫn tồn tại mà là một file không phải thư mục, hoặc không đủ quyền, vẫn sẽ lỗi. Việc tạo nhiều cấp không bảo đảm toàn bộ chuỗi tạo thư mục là một transaction nguyên tử.

## 6. `renameTo()`: trường hợp false khó chẩn đoán hơn

`File.renameTo(File dest)` có hợp đồng phụ thuộc nền tảng: có hệ thống không đổi tên được khi nguồn/đích nằm ở file system khác, hoặc không thay thế được đích có sẵn, hoặc có quy tắc riêng khi file đang mở. Theo Java API, nhiều khía cạnh như liệu thao tác di chuyển giữa các file system có thành công, liệu thao tác có tính nguyên tử, và xử lý đích tồn tại phụ thuộc nền tảng.

~~~java
File source = new File("old.txt");
File destination = new File("new.txt");

if (!source.renameTo(destination)) {
    // Không biết chắc: nguồn không tồn tại, đích xung đột,
    // sai quyền, khác file system hay một vấn đề khác.
}
~~~

Đây là **mảnh mã** cần import `java.io.File`. Không được tự lập trình kiểu “thử lại không giới hạn” trên `false`, vì không rõ điều kiện sẽ biến mất hay thao tác đã có tác dụng một phần.

Với NIO:

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class MoveExample {
    private MoveExample() {}

    public static Path replace(Path source, Path destination)
            throws IOException {
        return Files.move(source, destination,
                StandardCopyOption.REPLACE_EXISTING);
    }
}
~~~

Đây là lớp có thể biên dịch (không có `main`), với việc **cho phép thay thế đích** là lựa chọn nghiệp vụ chủ động. `Files.move` có thể ném `IOException`; thao tác `REPLACE_EXISTING` **không tự hứa nguyên tử** hoặc bảo toàn mọi thuộc tính trên mọi provider.

Nếu cần atomic move, có thể yêu cầu `StandardCopyOption.ATOMIC_MOVE`, nhưng phải xử lý `AtomicMoveNotSupportedException` nếu provider không hỗ trợ. Không âm thầm bỏ yêu cầu atomic mà không đánh giá tính nhất quán dữ liệu. Với move qua file system khác, triển khai có thể phải copy rồi delete nên phải cân nhắc lỗi, dữ liệu dở dang và bước rollback.

## 7. Một ngoại lệ quan trọng: `File.createNewFile()` không chỉ trả boolean

Không nên phát biểu “mọi phương thức `File` trả boolean đều nuốt lỗi I/O”. `File.createNewFile()` **vừa trả boolean vừa khai báo `throws IOException`**:

~~~java
boolean created = file.createNewFile();
~~~

Ý nghĩa:

- `true`: file mới đã được tạo thành công.
- `false`: tên đích đã có file/đường dẫn tồn tại tại thời điểm thực hiện thao tác tạo.
- `IOException`: lỗi I/O khi cố tạo file.

Tài liệu cũng quy định phép kiểm tra tên chưa tồn tại và tạo file mới là **một thao tác nguyên tử** so với các hoạt động liên quan trên file system, trong phạm vi hợp đồng của API.

### Java 17: phân biệt đã tồn tại và thất bại I/O

Tạo file **`CreateNewFileDemo.java`**:

~~~java
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CreateNewFileDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("create-new-");
        Path path = root.resolve("report.txt");
        File file = path.toFile();

        try {
            System.out.println("first=" + file.createNewFile());
            System.out.println("second=" + file.createNewFile());

            try {
                Files.createFile(path);
                System.out.println("unexpected success");
            } catch (FileAlreadyExistsException exception) {
                System.out.println("nio=" +
                        exception.getClass().getSimpleName());
            }
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(root);
        }
    }
}
~~~

Chạy:

~~~bash
javac CreateNewFileDemo.java
java CreateNewFileDemo
~~~

Kết quả trên file system thông thường:

~~~text
first=true
second=false
nio=FileAlreadyExistsException
~~~

Vì thế khi review code, phải hỏi **boolean này có ý nghĩa gì và phương thức còn khai báo exception nào?** `File.createNewFile()` vẫn cung cấp `IOException` cho lỗi I/O, khác với `File.mkdir()`/`File.delete()`.

Tương tự, `Files.deleteIfExists` cũng trả boolean nhưng **không giấu tất cả lỗi**: nếu file không tồn tại thì false, còn xóa không thành công có thể ném `IOException`.

## 8. Giá trị `false` từ phương thức *kiểm tra* cũng có giới hạn

### `exists()`, `isFile()` và `isDirectory()`

~~~java
File candidate = new File("incoming/data.bin");

if (!candidate.isFile()) {
    // Chưa đủ để nói "file không tồn tại".
}
~~~

`isFile()==false` có thể là vì đích là thư mục, đường dẫn chưa tồn tại, hoặc không thể xác định chính xác. `isDirectory()==false` cũng không chứng minh đích là file thường.

Một số API truy vấn dưới `java.nio.file.Files` **vẫn trả boolean** và cũng có thể không phân biệt lý do nếu trạng thái không xác định:

~~~java
boolean present = Files.exists(path);
boolean absent = Files.notExists(path);
~~~

**`Files.notExists(path)` không phải đơn giản là `!Files.exists(path)`.** Có ba nhóm kết quả cần phân biệt:

| `Files.exists(path)` | `Files.notExists(path)` | Diễn giải |
| --- | --- | --- |
| `true` | `false` | Xác định được file tồn tại tại thời điểm hỏi |
| `false` | `true` | Xác định được file không tồn tại tại thời điểm hỏi |
| `false` | `false` | Không xác định được trạng thái tồn tại hoặc không tồn tại |

Hai phép kiểm tra là **hai thao tác riêng theo thời gian**, nên khi file system thay đổi đồng thời, cặp giá trị đọc thực tế có thể không phản ánh một snapshot nhất quán. Bảng diễn giải điều kiện trạng thái ổn định tại lúc truy vấn và không thể thay thế bằng khóa/giao dịch file system.

Khi cần một lỗi cụ thể (ví dụ tồn tại nhưng không đọc được metadata), gọi API như `Files.readAttributes(path, BasicFileAttributes.class)`; nó có thể ném `IOException`/`NoSuchFileException` thay vì chỉ trả false.

### `canRead()`, `canWrite()`, `canExecute()`

Các phép kiểm tra quyền mô tả khả năng được ghi nhận ở một thời điểm, **không phải bảo đảm** lần mở file kế tiếp sẽ thành công:

- Quyền file/ACL có thể đổi.
- File có thể bị xóa hoặc thay thế.
- File system có thể có chính sách khác tùy loại hoạt động.
- Hệ điều hành có thể áp đặt chế độ chia sẻ/khóa hoặc các giới hạn khác.
- Symbolic link có thể trỏ sang mục khác.

Nên **thực hiện thao tác thực sự** (`Files.newInputStream`, `Files.newOutputStream`, `Files.readAttributes`...) và xử lý `IOException`, thay vì dùng `canRead() == true` như chứng nhận tương lai.

## 9. TOCTOU: “kiểm tra trước” không sửa được nhược điểm của boolean

Cách thường gặp:

~~~java
if (file.exists()) {
    if (!file.delete()) {
        // ...
    }
}
~~~

Có hai vấn đề độc lập:

1. `delete()==false` vẫn không cho biết nguyên nhân.
2. **Race condition**: file có thể bị xóa, thay thế hoặc thay đổi quyền ngay sau `exists()` và trước `delete()`.

Tương tự:

~~~java
if (!Files.exists(target)) {
    Files.createFile(target); // Vẫn có thể lỗi vì ai đó đã tạo trước.
}
~~~

Ngay cả khi `Files.exists(target)` trả false, điều đó **không chứng minh** file chưa tồn tại; trạng thái có thể không xác định. Và kể cả xác định được không tồn tại, kết quả đã có thể lỗi thời.

**Cách tốt hơn:** dùng thao tác trực tiếp có hợp đồng phù hợp:

- “Phải xóa file, nếu không có thì báo lỗi” → `Files.delete(path)`.
- “Đảm bảo sau thao tác file không còn, thiếu file được chấp nhận” → `Files.deleteIfExists(path)`, xử lý `IOException` khi không xóa được.
- “Tạo file mới và không được ghi đè” → `Files.createFile(path)` hoặc `CREATE_NEW`.
- “Đảm bảo thư mục tồn tại” → `Files.createDirectories(path)`.
- “Di chuyển file, cần xử lý xung đột/lỗi chi tiết” → `Files.move(...)` với options phù hợp.

API mạnh hơn giúp phân biệt lỗi, nhưng **không làm biến mất mọi race condition**. Đặc biệt với yêu cầu bảo mật liên quan symlink, quyền hoặc cây thư mục có người khác sửa, cần dùng API/thao tác và thiết kế có tính nguyên tử cần thiết thay vì nối nhiều bước kiểm tra mong manh.

## 10. `list()` và `listFiles()`: còn có trường hợp trả về `null`

Không phải API truyền thống nào thất bại cũng trả boolean. `File.list()` và `File.listFiles()` trả mảng, nhưng có thể trả **`null`** nếu đường dẫn không chỉ đến thư mục hoặc lỗi I/O xảy ra trong quá trình liệt kê.

~~~java
File directory = new File("reports");
File[] entries = directory.listFiles();

if (entries == null) {
    // Không được giả định "thư mục rỗng".
    // Có thể không phải thư mục hoặc đã gặp lỗi đọc.
} else {
    for (File entry : entries) {
        System.out.println(entry.getName());
    }
}
~~~

`entries.length == 0` mới là mảng rỗng được trả từ thao tác liệt kê thành công; `entries == null` là trường hợp khác.

Khi cần chẩn đoán tốt hơn, dùng:

~~~java
try (java.nio.file.DirectoryStream<java.nio.file.Path> entries =
             java.nio.file.Files.newDirectoryStream(path)) {
    for (java.nio.file.Path entry : entries) {
        System.out.println(entry.getFileName());
    }
}
~~~

Đây là **mảnh mã**, cần biến `path` kiểu `Path` và xử lý `IOException`. `DirectoryStream` là tài nguyên phải đóng; lỗi duyệt có thể xuất hiện dưới dạng `DirectoryIteratorException` (bọc nguyên nhân `IOException`), khác với lỗi lúc mở stream. Không mặc định danh sách trả về theo thứ tự cố định.

`Files.list(path)` cũng tạo `Stream<Path>` cần đóng bằng `try-with-resources`; lỗi khi duyệt lazy có thể được bọc trong `UncheckedIOException`. Đây là một sắc thái khác: dùng NIO không có nghĩa toàn bộ API đều truyền đúng một loại exception hoặc không cần quản lý tài nguyên.

## 11. Xử lý ngoại lệ chi tiết: điều gì có thể quan sát được?

Các thao tác NIO điển hình có thể báo:

- `NoSuchFileException`: đường dẫn/file cần thiết không tồn tại.
- `FileAlreadyExistsException`: đích đã tồn tại khi thao tác không cho phép.
- `DirectoryNotEmptyException`: thư mục không rỗng khi yêu cầu xóa.
- `AccessDeniedException`: không đủ quyền/truy cập bị từ chối.
- `AtomicMoveNotSupportedException`: provider không hỗ trợ di chuyển nguyên tử được yêu cầu.
- `FileSystemException`: thông tin liên quan đến file, file khác và lý do (khi provider cung cấp).
- `IOException` chung: một lỗi I/O khác hoặc provider không báo bằng lớp con cụ thể.

Các lớp con đặc thù không luôn được bảo đảm với mọi provider/OS. Hãy xử lý ngoại lệ cụ thể mà ứng dụng thực sự cần phân biệt rồi để `IOException` bao quát trường hợp còn lại.

### Mẫu log/biểu diễn lỗi có thông tin

~~~java
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public final class DeleteWithReason {
    private DeleteWithReason() {}

    public static void deleteRequired(Path path) throws IOException {
        try {
            Files.delete(path);
        } catch (NoSuchFileException exception) {
            throw new IOException("Required file is missing: " + path,
                    exception);
        } catch (AccessDeniedException exception) {
            throw new IOException("Cannot delete due to access policy: "
                    + path, exception);
        }
    }
}
~~~

Đây là lớp có thể biên dịch nhưng **không có `main`**. Nó minh họa cách giữ **cause** bằng constructor `IOException(message, cause)`, giúp log còn stack trace và thông tin nhà cung cấp gửi. Phương thức tiếp tục ném các `IOException` khác mà không đoán lý do.

Trong sản phẩm thật, tránh ghi đường dẫn nhạy cảm, tên tài khoản hoặc dữ liệu riêng vào log công khai. Chọn log level/structured logging theo mục đích; thêm path đã được xử lý phù hợp và nguyên nhân thực tế, không tạo thông điệp kiểu “permission denied” khi chỉ có `File.delete()==false`.

### Không “sửa” dữ liệu dựa trên một nguyên nhân phỏng đoán

Không nên:

~~~java
if (!file.delete()) {
    file.setWritable(true);
    file.delete();
}
~~~

Đây chỉ là **mảnh mã minh họa cách làm không an toàn**: không biết lý do thất bại, có thể thay đổi quyền file ngoài phạm vi mong muốn, vẫn không kiểm tra kết quả lần hai và có thể gây sai lệch bảo mật. Không sử dụng trong chương trình thật.

Nếu thao tác cần retry, phải xác định lỗi nào là tạm thời, giới hạn số lần và thời gian retry, kiểm soát tính idempotent, đồng thời tránh thay đổi permission hoặc xóa nhầm một file vừa bị thay thế.

## 12. Đặc điểm nền tảng: quyền, symbolic link và file system provider

### Quyền truy cập

Cùng một thao tác có thể thành công trên hệ thống này nhưng thất bại trên hệ thống khác. Đặc biệt, quyền xóa trên Unix-like thường liên quan tới **thư mục cha**, không đơn giản chỉ đọc bit writable của chính file. Trên Windows, file đang được mở với chế độ chia sẻ không phù hợp có thể bị từ chối xóa/di chuyển.

`File.setReadable/setWritable/setExecutable/setReadOnly` là API tiện lợi có mức độ biểu đạt hạn chế; khi cần quyền POSIX hoặc ACL, xem xét các API thuộc `java.nio.file.attribute` như `PosixFilePermissions`, `PosixFileAttributeView` hoặc `AclFileAttributeView` nếu file system hỗ trợ. Nên kiểm tra capability và xử lý lỗi thay vì giả định mọi OS đều có cùng mô hình permission.

### Symbolic link

Các API có những quy tắc riêng khi đụng symbolic link:

- Xóa **symbolic link** thông thường xóa chính link, không xóa target.
- Các truy vấn `Files.exists`/`isRegularFile` mặc định thường **theo link**; có thể dùng `LinkOption.NOFOLLOW_LINKS` ở nơi API hỗ trợ.
- Khi giải quyết đường dẫn trong thư mục do bên ngoài kiểm soát, symlink có thể thay đổi giữa các thao tác; nếu có rủi ro bảo mật, cần thiết kế hạn chế di chuyển qua đường dẫn và sử dụng cơ chế file-system an toàn phù hợp.

### File system provider

`Path`/`Files` hỗ trợ nhiều provider, không chỉ ổ đĩa local mặc định. Một provider có thể không hỗ trợ atomic move, view thuộc tính hoặc thao tác permission nhất định; ngoại lệ và thông điệp lỗi cũng có thể khác.

`File` truyền thống chủ yếu làm việc với hệ thống tập tin mặc định; `Path` mở rộng các khả năng NIO và thường có API thao tác chi tiết hơn. Chuyển từ `File` sang `Path` thông qua `file.toPath()` phù hợp khi dùng default provider; không giả định mọi `Path` có thể chuyển thành `File` (một số provider không hỗ trợ `toFile()`).

## 13. Kiểm thử đúng khi API báo boolean

Đừng kiểm thử bằng giả định “file bị khóa chắc chắn không xóa được” hoặc “đổi quyền sẽ gây AccessDenied trên mọi OS”. Kết quả như vậy có thể khác Windows/Linux, quyền tài khoản, mount và file system provider.

Ưu tiên các tình huống **kiểm soát được**:

1. File thiếu trong một thư mục tạm mới → `File.delete()` trả `false`; `Files.delete` có cơ hội báo `NoSuchFileException`.
2. Một thư mục con đã tồn tại → lần tạo `mkdir()` tiếp theo trả `false`, dù mục tiêu “thư mục tồn tại” đã đạt.
3. Một thư mục có file con → `File.delete()` không thể xóa trực tiếp; `Files.delete` báo lỗi, trên nhiều provider là `DirectoryNotEmptyException`.
4. File đã tồn tại → `File.createNewFile()` trả `false`; `Files.createFile` có thể báo `FileAlreadyExistsException`.
5. Với lỗi do quyền hoặc thiết bị, dùng file system giả lập/provider kiểm thử có thể chủ động phát sinh lỗi, hoặc integration test theo môi trường đã xác định; đừng ép lỗi hệ điều hành trên máy developer/CI bằng hành vi không ổn định.

Khi test logic nghiệp vụ, đừng chỉ assert boolean của API hệ thống. Hãy kiểm tra **trạng thái cuối** và rằng chương trình phân loại lỗi theo hợp đồng đã chọn; cleanup dữ liệu test bằng `try/finally` hoặc tài nguyên tạm mà test framework quản lý.

## 14. So sánh cách xử lý từng yêu cầu nghiệp vụ

| Yêu cầu | Legacy API | Cách NIO phù hợp | Tín hiệu lỗi |
| --- | --- | --- | --- |
| Xóa file bắt buộc có mặt | `file.delete()` | `Files.delete(path)` | `IOException`/lớp con |
| Xóa nếu có, thiếu file được chấp nhận | `file.delete()` + tự suy luận | `Files.deleteIfExists(path)` | `false` nếu không có; lỗi xóa có thể ném |
| Tạo một thư mục mới, đã tồn tại là lỗi | `file.mkdir()` | `Files.createDirectory(path)` | `FileAlreadyExistsException` (khi provider cung cấp) |
| Đảm bảo cây thư mục tồn tại | `file.mkdirs()` | `Files.createDirectories(path)` | Lỗi ném, phân biệt tồn tại loại khác |
| Di chuyển/đổi tên file | `file.renameTo(dest)` | `Files.move(source, target, options)` | `IOException`/options rõ |
| Xác định metadata thay vì boolean | `file.isFile()`/`isDirectory()` | `Files.readAttributes(path, ...)` | Có thể biết lỗi truy vấn |
| Liệt kê thư mục | `file.listFiles()` trả mảng hoặc `null` | `Files.newDirectoryStream`/`Files.list` | Có lỗi rõ hơn, cần đóng tài nguyên |
| Tạo mới file không ghi đè | `file.createNewFile()` | `Files.createFile(path)` | Legacy **cũng có IOException**; NIO có lớp con |

Không phải lúc nào NIO cũng là lựa chọn bắt buộc: nếu `false` đúng là thông tin duy nhất nghiệp vụ cần, một API boolean có thể đủ. Ví dụ một thao tác dọn file tạm “nếu còn thì thử xóa” có thể chấp nhận thất bại có ghi log phù hợp. Nhưng nếu xóa tài liệu quan trọng, thực hiện giao dịch, cập nhật cấu hình hoặc cần chẩn đoán lỗi cho người vận hành, **boolean không đủ làm kết quả nghiệp vụ cuối cùng**.

## 15. Những hiểu nhầm thường gặp

**“`File.delete() == false` nghĩa là file không tồn tại.”**  
Sai. Có nhiều nguyên nhân không thể xóa; dùng `Files.delete` nếu muốn biết lỗi cụ thể.

**“`mkdirs() == false` nghĩa là không có thư mục nào được tạo.”**  
Sai. Nó có thể thất bại sau khi đã tạo một phần cây thư mục; hoặc đích đã tồn tại từ trước.

**“`File.isFile() == false` nghĩa là nó chắc chắn là thư mục.”**  
Sai. Đường dẫn có thể không tồn tại, là loại khác hoặc không xác định được.

**“Mọi phương thức boolean của `File` đều không bao giờ ném `IOException`.”**  
Sai. `createNewFile()` trả boolean **và** khai báo `IOException`. Ngoài ra, các phương thức khác có thể ném ngoại lệ không phải I/O (ví dụ `SecurityException` trong môi trường có security manager phù hợp).

**“`Files.exists(path) == false` tức `Files.notExists(path) == true`.”**  
Sai. Nếu không xác định được trạng thái, cả hai đều có thể false.

**“Kiểm tra `exists()` trước sẽ bảo đảm thao tác sau không lỗi.”**  
Sai. File system có thể đổi trạng thái ngay lập tức; đây là nguy cơ TOCTOU.

**“Dùng `Files.move` sẽ luôn là atomic rename.”**  
Sai. Tính nguyên tử tùy provider/option; phải yêu cầu `ATOMIC_MOVE` và xử lý khi không được hỗ trợ nếu đó là điều kiện bắt buộc.

**“Catch IOException là luôn có thông báo đầy đủ từ OS.”**  
Sai. Provider có thể chỉ cung cấp lỗi chung; thông điệp lỗi có thể vắng hoặc khác theo môi trường. Tuy vậy exception và nguyên nhân vẫn cung cấp kênh chẩn đoán tốt hơn một boolean đơn lẻ.

**“False nghĩa là nên retry ngay.”**  
Sai. Nếu nguyên nhân là đường dẫn sai, quyền truy cập, đích xung đột hoặc thư mục không rỗng, retry có thể vô ích. Phải biết điều kiện nào thực sự tạm thời và tác dụng phụ có thể xảy ra.

## 16. Checklist khi review một đoạn code dùng `java.io.File`

- [ ] Phương thức trả boolean đang là **thao tác** hay **kiểm tra trạng thái**?
- [ ] Đã kiểm tra giá trị trả về chưa, hay bỏ qua thất bại?
- [ ] Nếu false, có đang suy đoán một nguyên nhân mà API **không bảo đảm** không?
- [ ] Nếu cần biết chính xác lý do, có phương thức `Files` tương ứng ném `IOException` không?
- [ ] Đã phân biệt `File.createNewFile` với các API chỉ trả boolean chưa?
- [ ] Đã xử lý tình huống đường dẫn đã tồn tại, đường dẫn không tồn tại và trạng thái **không xác định được**?
- [ ] Có tránh dựa vào `exists/canWrite/isFile` như bằng chứng cho thao tác tương lai?
- [ ] Với rename/move, đã làm rõ overwrite, atomicity, và khác file system?
- [ ] Có bảo toàn exception cause khi báo lỗi lên tầng nghiệp vụ?
- [ ] Log có tránh lộ thông tin đường dẫn nhạy cảm không?
- [ ] Nếu có retry/cleanup, đã giới hạn số lần và kiểm tra tác dụng phụ?
- [ ] Nếu file có thể thay đổi từ bên ngoài, đã đánh giá symlink và TOCTOU?
- [ ] Test có dùng tình huống ổn định thay vì phụ thuộc hành vi khóa/permission của từng OS?

## 17. Câu hỏi tự kiểm tra

**Câu 1.** `File.delete()` trả false. Có thể kết luận file không tồn tại không?  
**Đáp án:** Không. Nó chỉ cho biết xóa không thành công; nguyên nhân cần tín hiệu khác hoặc API chi tiết hơn.

**Câu 2.** Nếu cần xóa file và chấp nhận nó đã không tồn tại, nên chọn API nào?  
**Đáp án:** `Files.deleteIfExists(path)`; khi file thiếu trả `false`, còn lỗi xóa có thể ném `IOException`.

**Câu 3.** `mkdirs()` trả false nhưng `isDirectory()` trả true. Trường hợp thường gặp là gì?  
**Đáp án:** Đích có thể đã là thư mục từ trước; cũng cần tính đến concurrency. Không có đủ dữ liệu để quy false thành thất bại nghiệp vụ.

**Câu 4.** `Files.exists(path)` và `Files.notExists(path)` có thể cùng false không?  
**Đáp án:** Có, khi không thể xác định trạng thái.

**Câu 5.** `File.createNewFile()` trả false khi nào, và vẫn có ngoại lệ gì?  
**Đáp án:** False khi đường dẫn đích đã tồn tại tại thời điểm tạo; lỗi I/O có thể ném `IOException`.

**Câu 6.** `File.renameTo()` trả false. Có nên tự động dùng `setWritable(true)` rồi retry không?  
**Đáp án:** Không. Nguyên nhân chưa biết; thay đổi quyền là một hành động khác, có rủi ro và không bảo đảm giải quyết được lỗi.

**Câu 7.** `File.listFiles() == null` có nghĩa thư mục rỗng không?  
**Đáp án:** Không. Có thể đích không phải thư mục hoặc liệt kê gặp lỗi. Mảng rỗng và `null` có ý nghĩa khác nhau.

**Câu 8.** Vì sao `Files.move(..., ATOMIC_MOVE)` vẫn cần xử lý lỗi?  
**Đáp án:** Provider có thể không hỗ trợ atomic move hoặc thao tác vẫn gặp lỗi I/O; yêu cầu atomicity phải được kiểm tra và xử lý khi không thể thực hiện.

**Ghi nhớ:** Các API `java.io.File` trả `boolean` thường phản ánh **kết quả rất ít thông tin**. Đừng tự suy ra lý do từ `false`, và đừng coi một phép kiểm tra trạng thái là bảo đảm tương lai. Khi cần xử lý lỗi có thể kiểm toán, ưu tiên thao tác `java.nio.file.Files` với `IOException` và phân biệt rõ trường hợp “đã không tồn tại” với “không thao tác được”.

## Tài liệu API chính thức (Java 17)

- [`java.io.File`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/File.html)
- [`java.nio.file.Files`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [`java.nio.file.FileSystemException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileSystemException.html)
- [`java.nio.file.LinkOption`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/LinkOption.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="legacy-file-io-009">Chuyển đổi File.toPath và Path.toFile</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển đổi File.toPath và Path.toFile trong Java

## 1. Mục tiêu và phạm vi

Trong Java, **`java.io.File`** là biểu diễn đường dẫn tệp/thư mục theo API I/O truyền thống; **`java.nio.file.Path`** là biểu diễn đường dẫn thuộc một `FileSystem` theo API NIO.2 (có từ Java 7). Một dự án thực tế thường gặp cả hai: thư viện cũ trả `File`, còn mã mới dùng `Files`, `Path`, `DirectoryStream`, `FileChannel` hoặc `WatchService`.

Hai cầu nối trực tiếp là:

- **`File.toPath()`**: chuyển một `File` sang `Path` thuộc hệ thống tệp mặc định.
- **`Path.toFile()`**: chuyển một `Path` thuộc *default file system provider* sang `File`.

Chúng giúp tái sử dụng đường dẫn giữa API cũ và mới mà không phải tự nối chuỗi đường dẫn:

~~~text
                    File.toPath()
java.io.File ------------------------------> java.nio.file.Path
              <------------------------------
                    Path.toFile()
       Đường dẫn trên hệ thống tệp mặc định
~~~

Cần nắm năm giới hạn trước khi áp dụng:

1. **Chuyển đổi không tạo, mở, di chuyển hoặc đọc tệp.** Nó chuyển biểu diễn của đường dẫn, không thực hiện thao tác I/O trên nội dung.
2. **`Path.toFile()` không dùng được với mọi `Path`.** Đặc biệt, `Path` do provider không phải hệ thống tệp mặc định tạo ra (ví dụ ZIP filesystem) có thể ném `UnsupportedOperationException`.
3. **Đường dẫn tương đối vẫn là tương đối.** Chuyển đổi không tự gọi `toAbsolutePath()` hay `toRealPath()`.
4. **Chuyển đổi không chuẩn hóa và không xác nhận tệp tồn tại.** `..`, `.`, symbolic link, quyền truy cập và tình trạng tệp là các vấn đề khác.
5. **Khi cần thao tác thực tế, chọn API phù hợp.** Ví dụ `Files.readString(path, UTF_8)`, `Files.size(path)`, `Files.copy(...)` có hành vi/ngoại lệ của riêng chúng.

Bài học trình bày cú pháp, cơ chế, tình huống chuyển đổi, giới hạn provider, tính đúng đắn của đường dẫn và ví dụ Java 17 chạy độc lập.

## 2. File và Path đại diện cho điều gì?

### 2.1. File là một abstract pathname, không phải một tệp đã mở

`java.io.File` biểu diễn **đường dẫn trừu tượng** đến một tệp hoặc thư mục. Việc tạo đối tượng:

~~~java
File file = new File("data/report.txt");
~~~

không tạo `data/report.txt` trên ổ đĩa; cũng không kiểm tra file có tồn tại hay không. Đây chỉ là một đối tượng mô tả đường dẫn. Các phương thức truyền thống như `exists()`, `isFile()`, `length()`, `listFiles()`, `renameTo()` và `delete()` mới thực hiện các kiểm tra/thao tác tương ứng, với hợp đồng kết quả và xử lý lỗi riêng.

`File` có từ Java rất sớm và xuất hiện trong nhiều API, ví dụ:

~~~java
void uploadLegacy(File attachment);
File getLegacyConfigurationFile();
~~~

Đây là **chữ ký minh họa**, không phải chương trình chạy độc lập. Chúng vẫn có giá trị khi cần tích hợp thư viện cũ hoặc giao diện yêu cầu `File`.

### 2.2. Path nằm trong mô hình FileSystem của NIO.2

`java.nio.file.Path` là một **interface** biểu diễn đường dẫn trong một `FileSystem` nhất định. Đối với hệ thống tệp mặc định, có thể tạo bằng `Path.of(...)` (Java 11+) hoặc `Paths.get(...)`:

~~~java
Path path = Path.of("data", "report.txt");
~~~

Một `Path` cung cấp các thao tác xử lý thành phần đường dẫn như `getFileName()`, `getParent()`, `resolve()`, `relativize()`, `normalize()` và `toAbsolutePath()`. Những thao tác đó thường chỉ xử lý biểu diễn đường dẫn, không đọc nội dung tệp.

Hệ thống tệp NIO còn có thể đến từ provider khác, ví dụ một ZIP/JAR filesystem. Chính khả năng đa provider này giải thích tại sao `Path` không phải lúc nào cũng chuyển được thành `File`.

### 2.3. `Files` mới là nơi thực hiện nhiều thao tác NIO

`java.nio.file.Files` là lớp tiện ích có các phương thức tĩnh làm việc với `Path`:

~~~java
boolean regular = Files.isRegularFile(path);
long byteCount = Files.size(path);
String content = Files.readString(path, StandardCharsets.UTF_8);
~~~

Đây là đoạn minh họa với `Path path` đã có. Lời gọi `Files.size` và `Files.readString` có thể ném `IOException`; `Files.isRegularFile` có cách báo kết quả khác, và `false` không nhất thiết phân biệt “không phải tệp thường” với “không xác định được do lỗi”. Chuyển `File` thành `Path` trước khi dùng `Files` chỉ cung cấp đối tượng đường dẫn đúng kiểu; bản thân chuyển đổi không đọc dữ liệu.

## 3. File.toPath(): đưa API cũ sang Path

### 3.1. Cú pháp cơ bản

~~~java
import java.io.File;
import java.nio.file.Path;

File oldFile = new File("docs", "notes.txt");
Path path = oldFile.toPath();
~~~

`File.toPath()` trả một `Path` biểu diễn cùng đường dẫn trừu tượng trên **default file system**. Nếu `File` mô tả đường dẫn tương đối, `Path` nhận được cũng mang tính tương đối. Nếu `File` mô tả đường dẫn tuyệt đối, `Path` tương ứng cũng tuyệt đối.

Không có cuộc gọi mở hay kiểm tra quyền I/O chỉ vì sử dụng `toPath()`. Do đó mã có thể viết:

~~~java
File missing = new File("not-created.txt");
Path missingPath = missing.toPath();
~~~

ngay cả khi tệp `not-created.txt` không tồn tại. Điều đó không có nghĩa thao tác đọc tiếp theo sẽ thành công.

### 3.2. Chuyển để dùng Files API

Một lý do thực tế để gọi `File.toPath()` là tận dụng các API mới hơn:

~~~java
File oldFile = obtainLegacyFile();
Path path = oldFile.toPath();

long size = Files.size(path);
byte[] bytes = Files.readAllBytes(path);
~~~

Đây là **đoạn minh họa không độc lập**: `obtainLegacyFile()` đại diện một hàm có thật trong ứng dụng, và phương thức bao ngoài cần khai báo hoặc bắt `IOException`. `Files.readAllBytes` giữ toàn bộ dữ liệu trong RAM; với tệp lớn nên đọc theo luồng thay vì giả định có đủ bộ nhớ.

Một cách dùng thực tế khác là lấy metadata, di chuyển hoặc sao chép:

~~~java
Path source = oldFile.toPath();
Path target = Path.of("archive", oldFile.getName());
Files.copy(source, target);
~~~

Đây cũng là đoạn minh họa. `Files.copy` có thể ném `IOException` nếu tệp đích đã tồn tại, thiếu quyền, thư mục cha không có hoặc nguồn gặp lỗi. **`File.toPath()` không giải quyết các ngoại lệ này.**

### 3.3. `toPath()` không thay đổi đối tượng File

Sau lời gọi:

~~~java
Path path = oldFile.toPath();
~~~

đối tượng `oldFile` vẫn là `File`, không bị chuyển đổi “tại chỗ” và không bị vô hiệu. `path` là đối tượng khác, cùng mô tả đường dẫn theo mô hình NIO. Không cần “đổi lại” để tiếp tục dùng API cũ trên `oldFile`.

## 4. Path.toFile(): tích hợp API yêu cầu java.io.File

### 4.1. Cú pháp

~~~java
import java.io.File;
import java.nio.file.Path;

Path path = Path.of("docs", "notes.txt");
File file = path.toFile();
~~~

`Path.toFile()` chuyển một đường dẫn thuộc **hệ thống tệp mặc định** sang đối tượng `java.io.File`. Ví dụ thư viện bên thứ ba hoặc API lâu đời nhận `File`:

~~~java
Path path = Path.of("reports", "daily.txt");
legacyProcess(path.toFile());
~~~

Đoạn này là minh họa; `legacyProcess(File)` là hàm giả định do ứng dụng/thư viện cung cấp. Chọn `toFile()` khi thật sự cần một `File`; nếu mọi bước sau đều dùng NIO, không cần chuyển qua lại cho có.

### 4.2. Không gọi toFile() mù quáng trên Path của provider khác

Một `Path` có thể thuộc ZIP filesystem, filesystem ảo, hoặc provider khác. `Path.toFile()` có quyền ném `UnsupportedOperationException` nếu `Path` không liên kết với default provider. Ví dụ tư duy:

~~~text
Default FileSystem:
    Path.of("logs", "app.log")      → toFile() được hỗ trợ

ZIP/JAR FileSystem:
    zipFs.getPath("/entry.txt")     → toFile() không được hỗ trợ
                                      (UnsupportedOperationException)
~~~

Đây là mô hình, không phải lệnh Java độc lập. Nếu đang dùng ZIP filesystem, hãy dùng các API `Files` làm việc trực tiếp với `Path` đó thay vì cố ép nó sang `File`. Một đường dẫn bên trong tệp ZIP không tự nhiên là một tệp thường thuộc hệ thống tệp mặc định.

**Giới hạn provider là khác biệt quan trọng giữa hai chiều chuyển đổi:** `File.toPath()` đi vào default filesystem; `Path.toFile()` không bảo đảm đưa mọi provider về một `File` tương ứng.

### 4.3. Tại sao không dùng new File(path.toString()) thay cho toFile()?

Với `Path` thuộc hệ thống tệp mặc định, `path.toFile()` diễn đạt rõ ý định chuyển kiểu theo API. Việc tự gọi:

~~~java
File file = new File(path.toString());
~~~

có thể nhìn giống nhau trên máy đang chạy, nhưng nó **ném mất thông tin rằng Path thuộc FileSystem nào** và không tạo cầu nối chính thức cho provider khác. Với `Path` từ ZIP filesystem, chuỗi `"/entry.txt"` là đường dẫn nội bộ của ZIP; tạo `new File("/entry.txt")` lại trỏ tới đường dẫn trên filesystem mặc định, có nghĩa hoàn toàn khác.

Vì vậy, **không sử dụng việc chuyển thành chuỗi để giả lập hỗ trợ provider**. Nếu một API chỉ chấp nhận `File`, dữ liệu từ provider khác có thể cần được trích ra tệp tạm thuộc filesystem mặc định theo hợp đồng ứng dụng, hoặc cần một API nhận `InputStream`/`Path` để tránh trích xuất.

## 5. Ví dụ Java 17: chuyển hai chiều bằng đường dẫn tương đối

Lưu tệp **FilePathRoundTripDemo.java**:

~~~java
import java.io.File;
import java.nio.file.Path;

public class FilePathRoundTripDemo {
    public static void main(String[] args) {
        Path original = Path.of("data", "report.txt");
        File legacy = original.toFile();
        Path restored = legacy.toPath();

        System.out.println("Relative: " + !original.isAbsolute());
        System.out.println("Same path: " + original.equals(restored));
        System.out.println("Name: " + restored.getFileName());
        System.out.println("Absolute result: "
                + restored.toAbsolutePath().isAbsolute());
    }
}
~~~

Lệnh biên dịch và chạy:

~~~bash
javac -encoding UTF-8 FilePathRoundTripDemo.java
java FilePathRoundTripDemo
~~~

Kết quả trên default file system thông thường:

~~~text
Relative: true
Same path: true
Name: report.txt
Absolute result: true
~~~

Giải thích:

- `Path.of("data", "report.txt")` xây dựng đường dẫn tương đối theo cú pháp hệ điều hành.
- `original.toFile()` cung cấp một `File` cho API truyền thống; `legacy.toPath()` chuyển ngược lại.
- `Path.equals` ở đây trả true vì cả hai biểu diễn cùng đường dẫn tương đối trên default provider với cấu trúc thành phần tương ứng.
- `getFileName()` trả phần cuối của đường dẫn.
- `toAbsolutePath()` tạo biểu diễn tuyệt đối theo ngữ cảnh filesystem, **không xác nhận tệp tồn tại**.

Chương trình không tạo thư mục `data` hoặc tệp `report.txt`. Do đó không được suy luận từ dòng `Same path: true` rằng tệp đã tồn tại hay đã đọc được.

## 6. Chuyển đổi không phải kiểm tra tồn tại và cũng không mở tệp

Hãy phân biệt bốn thao tác:

| Lời gọi | Ý nghĩa | Truy cập nội dung tệp? |
| --- | --- | --- |
| `file.toPath()` | Biến biểu diễn File thành Path | Không |
| `path.toFile()` | Biến Path thuộc default FS thành File | Không |
| `Files.exists(path)` | Kiểm tra sự tồn tại theo hợp đồng API | Kiểm tra filesystem, không đọc nội dung |
| `Files.readString(path, UTF_8)` | Mở/đọc và giải mã văn bản | Có; có thể ném IOException |

`Files.exists(path)` trả false nếu không tồn tại **hoặc nếu không thể xác định** theo quyền, lỗi I/O hoặc các tình huống provider khác. Vì vậy không nên coi `false` là bằng chứng chắc chắn tệp không tồn tại trong mọi trường hợp. Với thao tác yêu cầu tệp thực, thực hiện thao tác và bắt ngoại lệ là cách đáng tin hơn việc tách “check then act”.

Ví dụ sai về kỳ vọng:

~~~java
File source = new File("missing.txt");
Path path = source.toPath();   // Vẫn có thể tạo Path.
String text = Files.readString(path); // Có thể ném IOException.
~~~

Đây là đoạn minh họa. `toPath()` không làm tệp “mất” hay “xuất hiện”; lỗi phát sinh từ hành động đọc.

### 6.1. TOCTOU: kiểm tra trước không bảo đảm thao tác sau

Ngay cả khi `Files.exists(path)` trả true tại thời điểm kiểm tra, một tiến trình khác có thể xóa hoặc thay tệp trước khi `Files.newInputStream(path)` thực sự mở nó. Đây là điều kiện *time-of-check to time-of-use* (TOCTOU).

Do đó, kiểm tra tồn tại có ích cho giao diện hoặc logic sơ bộ, nhưng **không thay thế xử lý IOException của thao tác thực sự**. Tương tự, chuyển đổi `File ↔ Path` không tạo bất kỳ khóa hoặc cam kết nào về trạng thái filesystem.

## 7. Đường dẫn tương đối, tuyệt đối và thư mục làm việc

### 7.1. Tương đối giữ nguyên nghĩa tương đối

~~~java
File oldFile = new File("reports", "2026.txt");
Path path = oldFile.toPath();

System.out.println(path.isAbsolute()); // false
~~~

Đây là đoạn minh họa. Chuyển đổi không tự nối đường dẫn tới thư mục làm việc. Một đường dẫn tương đối được diễn giải theo quy tắc thư mục mặc định của môi trường thực thi và filesystem khi thực hiện thao tác cần truy cập.

Để có biểu diễn tuyệt đối, gọi tường minh:

~~~java
Path absolute = path.toAbsolutePath();
~~~

`toAbsolutePath()` không buộc tệp tồn tại; nó làm việc ở mức biểu diễn đường dẫn. Đừng nhầm nó với đường dẫn thực đã giải quyết symbolic link.

### 7.2. normalize() xử lý cú pháp, không xác nhận filesystem

Ví dụ:

~~~java
Path raw = Path.of("docs", ".", "drafts", "..", "final.txt");
Path normalized = raw.normalize();
~~~

Sau normalize, đường dẫn logic là `docs/final.txt`. Tuy nhiên, `normalize()` là thao tác **từ vựng** (*lexical*); nó không cần mở tệp hoặc kiểm tra symbolic link. Khi đường dẫn đi qua symbolic link, loại bỏ `..` có thể thay đổi cách đường dẫn được giải quyết trong filesystem thực.

Do đó, chỉ normalize khi điều đó đúng với quy tắc của ứng dụng; không coi normalize là cách tự động làm đường dẫn an toàn hoặc đồng nhất mọi đường dẫn chỉ tới cùng một tệp.

### 7.3. toRealPath() mới kiểm tra thực tế

`path.toRealPath()` trả đường dẫn thực/canonical theo hợp đồng filesystem và yêu cầu đối tượng tồn tại khi áp dụng các option mặc định. Nó có thể ném `IOException` và thường giải quyết symbolic link; có thể cung cấp `LinkOption.NOFOLLOW_LINKS` để thay đổi xử lý liên kết.

Khác nhau:

~~~text
file.toPath()               Chuyển biểu diễn; không đọc filesystem
path.toAbsolutePath()       Tạo đường dẫn tuyệt đối; không kiểm tra tồn tại
path.normalize()            Rút gọn . và .. về mặt cú pháp
path.toRealPath()           Truy vấn filesystem, có thể ném IOException
~~~

Một đường dẫn tuyệt đối chưa chắc là đường dẫn thực; một đường dẫn đã normalize chưa chắc đã tồn tại.

## 8. Ví dụ Java 17: File cũ dùng API Files để đọc văn bản

Lưu tệp **LegacyFileModernFilesDemo.java**:

~~~java
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class LegacyFileModernFilesDemo {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("file-path-");
        Path path = directory.resolve("note.txt");

        try {
            Files.writeString(path, "Java 17", StandardCharsets.UTF_8);
            File legacyFile = path.toFile();

            Path converted = legacyFile.toPath();
            System.out.println("File exists: " + legacyFile.isFile());
            System.out.println("Path exists: "
                    + Files.isRegularFile(converted));
            System.out.println("Size: " + Files.size(converted));
            System.out.println("Text: "
                    + Files.readString(converted, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(directory);
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac -encoding UTF-8 LegacyFileModernFilesDemo.java
java LegacyFileModernFilesDemo
~~~

Kết quả trong môi trường tệp thông thường có quyền tạo/xóa tệp tạm:

~~~text
File exists: true
Path exists: true
Size: 7
Text: Java 17
~~~

Giải thích:

1. `Files.createTempDirectory` tạo một thư mục tạm thật, riêng cho chương trình.
2. `Files.writeString` ghi văn bản với UTF-8.
3. `path.toFile()` đưa `Path` về kiểu `File` để minh họa một API cũ giữ đối tượng này.
4. `legacyFile.toPath()` quay lại `Path` nhằm sử dụng `Files.isRegularFile`, `Files.size` và `Files.readString`.
5. `Files.size` trả **số byte**, không phải số code point hay ký tự. Văn bản ASCII `Java 17` có 7 byte.
6. `finally` xóa tệp rồi xóa thư mục tạm, kể cả khi thân `try` gặp lỗi. Trong phần mềm thực, nếu dọn dẹp cũng thất bại thì cần bảo toàn và ghi nhận cả lỗi chính và lỗi cleanup theo chiến lược ứng dụng.

Ví dụ này cho thấy ưu điểm của việc dùng cầu nối khi dữ liệu đang ở dạng `File` nhưng ta muốn thao tác bằng API hiện đại, **không cần** biến đường dẫn thành một chuỗi thủ công.

## 9. Dùng Path.toFile khi thư viện cũ bắt buộc nhận File

Giả sử một giao diện thư viện cũ là:

~~~java
void attachFile(File file);
~~~

Bạn đang quản lý đường dẫn trong một ứng dụng dùng NIO:

~~~java
Path attachment = Path.of("uploads", "image.png");
attachFile(attachment.toFile());
~~~

Hai đoạn này là **chữ ký/lời gọi minh họa**, không phải chương trình Java độc lập. Đây là kiểu chuyển đổi hợp lý nếu `attachment` thuộc default filesystem và thư viện thật sự yêu cầu `File`.

Cần hỏi thêm hai vấn đề:

- Thư viện cũ **chỉ dùng đường dẫn** hay yêu cầu tệp phải đã tồn tại và có quyền đọc? `toFile()` không thỏa mãn các điều kiện đó.
- `Path` có chắc thuộc default provider không? Nếu không, `toFile()` có thể ném `UnsupportedOperationException`.

Nếu API cũ chấp nhận `InputStream`, đôi khi mở `Files.newInputStream(path)` và truyền luồng phù hợp hơn việc phải chuyển đổi qua `File`. Cần xác định ai đóng stream; tài nguyên phải được quản lý đúng vòng đời.

## 10. Provider: giới hạn của Path.toFile và tình huống ZIP

`Path` được gắn với một `FileSystem`, còn `File` truyền thống chủ yếu làm việc với hệ thống tệp mặc định. Vì vậy:

~~~java
Path defaultPath = Path.of("data", "report.txt");
File legacy = defaultPath.toFile(); // Hợp lệ trên default filesystem.
~~~

Nhưng một `Path` tạo bởi ZIP filesystem có thể đại diện cho một entry *bên trong* tệp ZIP. Entry ấy không tự động trở thành một tệp OS riêng. Do đó gọi `toFile()` với provider không phải mặc định sẽ ném `UnsupportedOperationException`.

### 10.1. Phân biệt hai Path khi làm việc với ZIP

Giả sử tệp ZIP nằm tại `/tmp/archive.zip` và trong đó có `/docs/a.txt`:

~~~text
Path của ZIP trên hệ thống tệp mặc định:
    /tmp/archive.zip          → toFile() có thể dùng

Path của entry trong ZIP filesystem:
    /docs/a.txt               → không phải File OS trực tiếp
                                → toFile() không được hỗ trợ
~~~

Không được khắc phục bằng `new File(entry.toString())`: đường dẫn thu được khi đó có thể trỏ tới `/docs/a.txt` ngoài filesystem mặc định, chứ không phải entry trong ZIP.

### 10.2. Xử lý bằng Files API hoặc trích xuất có chủ đích

Nếu chỉ cần đọc entry, làm việc trực tiếp với `Path`:

~~~java
byte[] payload = Files.readAllBytes(zipEntryPath);
~~~

Đoạn minh họa với `Path zipEntryPath` của ZIP filesystem đã được mở. Không cần `File`.

Nếu thư viện bắt buộc phải nhận `File` thật, ứng dụng có thể **trích xuất vào một tệp tạm trên default filesystem**, truyền tệp đó, rồi quản lý việc xóa tệp tạm đúng lúc. Đây là việc sao chép dữ liệu có chủ đích, **không phải** chuyển đổi `Path.toFile`. Cần kiểm tra kích thước entry, quyền và rủi ro tệp ZIP không tin cậy trước khi trích xuất.

### 10.3. Khi nào bắt UnsupportedOperationException?

`UnsupportedOperationException` là unchecked exception. Nếu một API công khai nhận `Path` bất kỳ nhưng bên trong bắt buộc trả `File`, hãy công bố rõ giới hạn provider, hoặc chuyển đổi tại điểm tích hợp và xử lý trường hợp không hỗ trợ theo hợp đồng nghiệp vụ.

Đừng dùng `catch(Exception)` để biến mọi lỗi thành “không tìm thấy tệp”: `UnsupportedOperationException` chỉ ra **không hỗ trợ mô hình chuyển đổi**, không nhất thiết tệp bên trong provider không tồn tại.

## 11. Giữ nguyên ý nghĩa đường dẫn trên Windows, Linux và macOS

Cùng một chuỗi ký tự có thể được hiểu khác nhau giữa hệ điều hành và filesystem:

- Trên Windows có quy tắc ổ đĩa, UNC, ký tự phân cách và một số dạng đường dẫn đặc biệt.
- Trên Linux/macOS, quy tắc đường dẫn và xử lý dấu `\` khác Windows.
- Quy tắc phân biệt chữ hoa/chữ thường, Unicode và symbolic link có thể phụ thuộc filesystem, không chỉ hệ điều hành.

Thay vì tự nối:

~~~java
String name = folder + "/" + fileName;
~~~

nên dùng API đường dẫn:

~~~java
Path full = Path.of(folder).resolve(fileName);
~~~

Hai đoạn minh họa với `folder` và `fileName` là chuỗi được cung cấp. `Path.resolve` dùng quy tắc filesystem và mô tả rõ thao tác ghép thành phần. Nhưng cần thận trọng nếu `fileName` là đầu vào không tin cậy: nó có thể chứa `..` hoặc là đường dẫn tuyệt đối làm thay đổi ý định. `normalize()` một mình **không phải bảo đảm chống path traversal**, đặc biệt khi có symbolic link hoặc thay đổi filesystem đồng thời.

Để so sánh hai đường dẫn, không nên chỉ so chuỗi `file.getPath()` với `path.toString()` và kết luận chúng trỏ cùng một tệp. Hai biểu diễn có thể khác về tương đối/tuyệt đối, `.`/`..`, casing hoặc symbolic link.

Nên lựa chọn phép so sánh theo mục đích:

- `Path.equals`: so sánh các đối tượng Path theo quy tắc của provider và cấu trúc đường dẫn, không bảo đảm xác định cùng một tệp vật lý.
- `Path.normalize()`: so sánh sau chuẩn hóa cú pháp nếu hợp đồng cho phép.
- `Files.isSameFile(a, b)`: khi cần kiểm tra hai đường dẫn có cùng chỉ định tới một file theo filesystem; có thể truy cập filesystem và ném `IOException`.

## 12. File.toPath khác File.getAbsoluteFile, File.getCanonicalFile

Một ứng dụng cũ có thể dùng:

~~~java
File absolute = file.getAbsoluteFile();
File canonical = file.getCanonicalFile();
~~~

`getAbsoluteFile()` trả biểu diễn `File` tuyệt đối theo quy tắc môi trường. `getCanonicalFile()` cố tạo đường dẫn canonical theo filesystem; nó có thể ném `IOException` và bị ảnh hưởng bởi hiện trạng filesystem.

Cách diễn đạt tương đương về **mục đích** trong NIO thường là:

~~~java
Path absolutePath = file.toPath().toAbsolutePath();
Path normalizedPath = file.toPath().toAbsolutePath().normalize();
Path realPath = file.toPath().toRealPath();
~~~

Đây là các đoạn minh họa, yêu cầu `File file` đã có và xử lý `IOException` với `toRealPath()`. Không coi ba kết quả là hoàn toàn tương đương:

- `getCanonicalFile()` và `toRealPath()` có các hợp đồng, hành vi khi tệp chưa tồn tại và tùy chọn xử lý liên kết khác nhau.
- `normalize()` xử lý cú pháp; không tương đương với việc giải quyết toàn bộ đường dẫn vật lý.
- `toRealPath()` yêu cầu tệp tồn tại khi sử dụng mặc định, có thể ném lỗi hệ thống tệp.

Nếu đang chuyển đổi code cũ, hãy xác định mục đích thật của bước “canonicalize” trước khi thay bằng một lời gọi NIO cho giống cú pháp.

## 13. Chuyển đổi khi thao tác copy, move và delete

Một tình huống phổ biến khi hiện đại hóa ứng dụng là thay các phương thức `File` cũ bằng API `Files`:

| Mã File truyền thống | Hướng NIO với File.toPath() | Điểm cần nhớ |
| --- | --- | --- |
| `file.exists()` | `Files.exists(file.toPath())` | False có thể bao gồm trường hợp không thể xác định |
| `file.isDirectory()` | `Files.isDirectory(file.toPath())` | Liên quan symbolic link/option |
| `file.length()` | `Files.size(file.toPath())` | `Files.size` có thể ném IOException |
| `file.delete()` | `Files.delete(file.toPath())` | NIO ném ngoại lệ khi thất bại thay vì chỉ boolean |
| `file.renameTo(dest)` | `Files.move(file.toPath(), dest.toPath())` | Không phải hành vi tương đương tuyệt đối; các option/provider ảnh hưởng |
| `file.listFiles()` | `Files.list(file.toPath())` | Trả Stream<Path>; phải đóng luồng trả về |
| `file.mkdirs()` | `Files.createDirectories(file.toPath())` | NIO báo lỗi chi tiết hơn; vẫn có race/điều kiện filesystem |

Cẩn trọng khi chuyển đổi: API tương tự về ý định chưa chắc có cùng điều kiện ghi đè, cách xử lý symbolic link, quyền hoặc kết quả lỗi. Một số `File` method trả boolean; nhiều `Files` method ném `IOException` giúp phân loại lỗi rõ ràng hơn. Không nên nuốt ngoại lệ để cố tái tạo boolean nếu ứng dụng cần chẩn đoán.

Đặc biệt, `Files.list` trả `Stream<Path>` gắn với tài nguyên thư mục đang mở. Nếu sử dụng, đóng stream bằng `try-with-resources`; đây là ví dụ cụ thể mà `File.toPath()` tự nó **không** chịu trách nhiệm quản lý tài nguyên.

### 13.1. Ví dụ chuyển File.renameTo sang Files.move

Lưu **MoveWithPathDemo.java**:

~~~java
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class MoveWithPathDemo {
    public static void main(String[] args) throws IOException {
        Path folder = Files.createTempDirectory("move-bridge-");
        Path sourcePath = folder.resolve("old.txt");
        Path targetPath = folder.resolve("new.txt");

        try {
            Files.writeString(sourcePath, "moved", StandardCharsets.UTF_8);

            File legacySource = sourcePath.toFile();
            Files.move(legacySource.toPath(), targetPath);

            System.out.println("Old exists: "
                    + Files.exists(legacySource.toPath()));
            System.out.println("New content: "
                    + Files.readString(targetPath, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(targetPath);
            Files.deleteIfExists(sourcePath);
            Files.deleteIfExists(folder);
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 MoveWithPathDemo.java
java MoveWithPathDemo
~~~

Kết quả trong filesystem thông thường có quyền ghi:

~~~text
Old exists: false
New content: moved
~~~

Điểm cần chú ý: nguồn và đích thuộc cùng thư mục tạm, đích chưa tồn tại. Đây là ví dụ đơn giản để tránh tranh cãi về ghi đè. `Files.move` có thể ném `IOException` (ví dụ thiếu quyền, target tồn tại, lỗi thiết bị) hoặc các ngoại lệ khác theo hợp đồng. Nếu yêu cầu di chuyển nguyên tử, xem xét `StandardCopyOption.ATOMIC_MOVE` và xử lý khi không được hỗ trợ; không suy ra rằng mọi lời gọi `move` mặc định luôn nguyên tử.

## 14. File, Path và URL/URI: tránh nhầm kiểu chuyển đổi

Ba lớp dễ bị nhầm:

- `File`: đường dẫn trừu tượng truyền thống.
- `Path`: đường dẫn trong một `FileSystem`.
- `URI`: định danh tài nguyên theo cú pháp URI, có thể chứa scheme như `file:` hoặc `jar:`.

Với một `File` thuộc default filesystem:

~~~java
URI uri = file.toURI();
Path path = file.toPath();
~~~

Hai lời gọi không tương đương về **kiểu kết quả**: `toURI()` tạo URI, `toPath()` tạo Path. Khi có một URI `file:` hợp lệ, `Path.of(uri)` có thể tạo Path nhờ filesystem provider hỗ trợ URI đó. Nhưng URI `jar:` hoặc URI của dịch vụ mạng không thể được đổi thành `File` bằng cách tùy tiện cắt bỏ scheme.

Cũng không dùng `Path.toString()` như một dạng URI để truyền qua mạng, vì đó là biểu diễn đường dẫn theo provider, không phải encoding URI.

### 14.1. Ví dụ minh họa URI không thay cho File

~~~java
Path path = Path.of("data", "report.txt");
File file = path.toFile();       // File cho API cũ.
URI uri = file.toURI();          // URI file: cho việc định danh.
Path recovered = Path.of(uri);   // Path trên provider tương ứng.
~~~

Đây là đoạn minh họa với import `java.net.URI`, `java.io.File`, `java.nio.file.Path`. `URI` ở đây đến từ `File.toURI()` nên có scheme `file`. Chỉ dùng `Path.of(uri)` với URI mà một provider thích hợp có thể xử lý; nó không mặc định hoạt động với mọi URL HTTP.

## 15. Ngoại lệ và vấn đề an toàn khi chuyển đổi

Bản thân thao tác cầu nối tương đối nhẹ, nhưng vẫn có thể gặp những tình huống quan trọng:

| Trường hợp | Ngoại lệ/hệ quả | Cách xử lý |
| --- | --- | --- |
| `File.toPath()` với tên đường dẫn không biểu diễn được thành Path hợp lệ của default FS | Có thể ném `InvalidPathException` | Kiểm tra/chuẩn hóa đầu vào theo hợp đồng ứng dụng; không đoán OS |
| `Path.toFile()` trên provider không mặc định | `UnsupportedOperationException` | Dùng Files trên Path hoặc trích xuất có chủ đích nếu API cũ bắt buộc |
| `Files.readString(path)` khi tệp thiếu | Thường `NoSuchFileException` | Bắt IOException theo ngữ cảnh |
| `Files.size(path)` khi thiếu quyền | Có thể `AccessDeniedException`/IOException khác | Báo lỗi đúng nguyên nhân |
| Đường dẫn đến file có symbolic link | Thao tác có thể theo liên kết tùy API/option | Chọn `LinkOption` phù hợp, kiểm thử |
| Ghép đường dẫn từ input không tin cậy | Có nguy cơ path traversal | Xác định root hợp lệ, kiểm tra an toàn thực tế |
| Mã cũ gọi `new File(path.toString())` với Path ngoại provider | Có thể trỏ sang **file khác** trên default FS | Không dùng cách ép chuỗi |

**`InvalidPathException`** là unchecked exception; trong một số hệ thống, chuỗi đường dẫn có ký tự không hợp lệ sẽ bị từ chối từ giai đoạn tạo Path. Còn lỗi thiếu tệp/quyền thường chỉ xuất hiện khi bắt đầu truy cập filesystem, chẳng hạn khi `Files.newInputStream` hoặc `Files.readAttributes`.

**`SecurityException`** có thể phát sinh trong một số môi trường có cơ chế bảo mật tương ứng; nó không phải là `IOException`. Không nên gom mọi lỗi của việc tạo/chuyển đổi đường dẫn vào một nhóm “đọc tệp thất bại”.

## 16. Quản lý tài nguyên: File và Path không cần close

`File` và `Path` là các đối tượng mô tả đường dẫn. Không phải vì tên `File` mà đối tượng này đã mở một file descriptor, và không có nhu cầu dùng `try-with-resources` chỉ để tạo/chuyển đổi:

~~~java
File file = new File("example.txt");
Path path = file.toPath();
// Không cần close file hoặc path.
~~~

Khi **mở stream, channel hoặc directory stream**, cần đóng đúng vòng đời:

~~~java
try (InputStream input = Files.newInputStream(path)) {
    byte[] buffer = new byte[4096];
    int count;
    while ((count = input.read(buffer)) != -1) {
        // Xử lý buffer[0..count).
    }
}
~~~

Đoạn minh họa cần import `java.io.InputStream` và `java.nio.file.Files`, phương thức bao ngoài xử lý `IOException`. `try-with-resources` quản lý `InputStream`; nó không liên quan tới việc phải đóng `path`.

Tương tự, `Files.lines(path)` và `Files.list(path)` trả Stream cần được đóng khi kết thúc để giải phóng tài nguyên. Một phương thức đọc ngắn như `Files.readString(path)` tự quản lý việc mở/đóng theo hợp đồng của nó nhưng vẫn có thể báo lỗi.

## 17. Kiểm thử và tình huống thường gặp trong dự án

### 17.1. Legacy API trả File, mã mới cần metadata NIO

**Đề bài:** `legacyApi.getFile()` trả `File`; cần đọc metadata hoặc văn bản với `Files`.

~~~java
File file = legacyApi.getFile();
Path path = file.toPath();
long size = Files.size(path);
~~~

Đây là đoạn minh họa với `legacyApi` do thư viện cung cấp. Nếu tệp bị mất hoặc không truy cập được, `Files.size` sẽ báo lỗi theo hợp đồng, không phải `File.toPath()`.

### 17.2. Mã mới giữ Path, thư viện cũ chỉ nhận File

**Đề bài:** Đường dẫn NIO thuộc default filesystem, cần truyền vào thư viện cũ.

~~~java
Path report = Path.of("reports", "daily.pdf");
oldLibrary.upload(report.toFile());
~~~

Đoạn minh họa giả định `oldLibrary.upload(File)` tồn tại. Nếu đường dẫn thực sự thuộc ZIP filesystem, không dùng `toFile()` như một phép ép chung; phải thay luồng làm việc hoặc trích xuất theo quy định.

### 17.3. Muốn biết File và Path có cùng chỉ tới một tài nguyên?

Nếu cả hai trỏ tới default filesystem:

~~~java
boolean same = Files.isSameFile(file.toPath(), path);
~~~

Đây là đoạn minh họa; cần xử lý `IOException`. `isSameFile` đặt câu hỏi về thực thể trong filesystem chứ không chỉ so hai chuỗi. Tuy nhiên filesystem có thể thay đổi giữa lần kiểm tra và thao tác sau; đây không phải một cơ chế khóa.

### 17.4. Mã legacy dùng renameTo và không có thông báo lỗi chi tiết

Khi cải tiến:

~~~java
Files.move(sourceFile.toPath(), targetFile.toPath());
~~~

Đây là đoạn minh họa. `Files.move` có thể ném ngoại lệ cho lý do cụ thể thay vì trả một boolean giống `File.renameTo`. Cần xem lại chính sách về tệp đích đã tồn tại, symlink, di chuyển khác provider và atomicity, không chỉ thay tên phương thức.

### 17.5. Kiểm thử không nên phụ thuộc file có sẵn

Các chương trình ví dụ phía trên dùng `Files.createTempDirectory` để tạo thư mục riêng, thêm tệp do chính chương trình ghi và dọn cuối chương trình. Cách này hạn chế phụ thuộc máy người học, nhưng vẫn cần quyền tạo/xóa tệp tạm. Khi test lỗi, nên chủ động tạo tình huống tệp chưa tồn tại hoặc đã tồn tại thay vì giả định tên bất kỳ trong thư mục làm việc chắc chắn trống.

## 18. Các lỗi phổ biến khi học File.toPath / Path.toFile

| Hiểu sai | Hậu quả | Điều đúng |
| --- | --- | --- |
| `toPath()` “đổi file thành đối tượng đã mở” | Tưởng dữ liệu đã được đọc và quên IOException | Chỉ chuyển biểu diễn đường dẫn |
| `toFile()` tạo tệp vật lý | Tưởng file tồn tại sau khi gọi | Chỉ tạo đối tượng File |
| Mọi Path đều có thể `toFile()` | Crash trên ZIP/provider khác | Chỉ default filesystem hỗ trợ |
| `new File(path.toString())` luôn bằng `path.toFile()` | Mất ý nghĩa provider, có thể trỏ file khác | Dùng `toFile()` khi được hỗ trợ |
| Convert tự tạo đường dẫn tuyệt đối | Truy cập nhầm thư mục | Dùng `toAbsolutePath()` khi cần |
| Convert tự bỏ `.`/`..` và symlink | So sánh/bảo mật sai | Phân biệt normalize với toRealPath |
| `Files.exists` bảo đảm lần đọc sau thành công | TOCTOU, quyền thay đổi | Xử lý IOException từ thao tác thực |
| Dùng `File.exists()` để xử lý mọi trường hợp lỗi | Không biết rõ vì sao false | Chọn API Files có báo ngoại lệ khi cần |
| So sánh đường dẫn chỉ bằng chuỗi | Bỏ qua cấu trúc/provider/alias | So sánh theo mục đích, cân nhắc isSameFile |
| Tự nối `"/"` giữa các thành phần | Có thể sai quy tắc nền tảng | Dùng Path.of/resolve |
| Giả định `File.renameTo` và `Files.move` giống nhau mọi điểm | Sai chính sách thay thế, lỗi và atomicity | Đọc hợp đồng API, dùng option phù hợp |
| Đóng File hoặc Path như một stream | Nhầm mô hình tài nguyên | Đóng stream/channel chứ không đóng đối tượng path |
| Lấy Path từ ZIP rồi toFile để truyền thư viện cũ | UnsupportedOperationException | Dùng Files trực tiếp hoặc trích tệp tạm hợp lệ |

## 19. Bài tập thực hành

### Bài 1 — Đường dẫn tương đối và round-trip

Tạo `Path.of("logs", "app.log")`, chuyển sang `File` rồi về `Path`. Kiểm tra `isAbsolute()`, `getFileName()`, `equals()`. Vì sao tất cả đều có thể thực hiện dù không có tệp thật?

**Gợi ý:** Những thao tác này không cần mở nội dung. `toAbsolutePath()` cũng không bắt buộc tệp tồn tại.

### Bài 2 — Đọc metadata qua File.toPath

Tạo một tệp tạm trong thư mục tạm của chương trình, ghi `"12345"` theo UTF-8, chuyển tệp thành `File` rồi dùng `Files.size(file.toPath())`. Kết quả trong trường hợp này là bao nhiêu?

**Đáp án:** 5 byte. Đây là dữ liệu ASCII nên mỗi ký tự chiếm một byte UTF-8. Nếu đổi sang văn bản có dấu/emoji, không suy số byte từ `String.length()`.

### Bài 3 — Provider không mặc định

Giả sử một thư viện trả `Path` đến entry trong ZIP filesystem. Vì sao không thể đơn giản gọi `path.toFile()`? Nếu một thư viện cũ chỉ nhận `File` thật, cần làm gì?

**Đáp án:** `Path.toFile()` không hỗ trợ provider không mặc định. Có thể tiếp tục xử lý qua `Files` với Path gốc; nếu bắt buộc File OS, trích nội dung ra tệp tạm thuộc default filesystem và quản lý vòng đời tệp đó.

### Bài 4 — normalize không phải toRealPath

Với đường dẫn `a/./b/../c.txt`, `normalize()` làm gì? Nó có xác nhận `c.txt` tồn tại không?

**Đáp án:** Rút gọn cú pháp thành `a/c.txt`, không kiểm tra tệp tồn tại. Với symbolic link, phép biến đổi này không nhất thiết bảo toàn cách giải quyết đường dẫn vật lý.

### Bài 5 — Kiểm tra tính an toàn của chuyển mã cũ

Một đoạn cũ dùng `file.delete()` trả boolean. Nếu thay bằng `Files.delete(file.toPath())` thì phải thay đổi hợp đồng xử lý lỗi như thế nào?

**Đáp án:** `Files.delete` có thể ném `IOException`, bao gồm nguyên nhân file không tồn tại/không có quyền, và có các ngoại lệ runtime khác theo hợp đồng. Caller nên xử lý/khai báo lỗi thay vì chỉ kiểm tra true/false. Nếu muốn không coi file vắng là lỗi, xem `Files.deleteIfExists` nhưng vẫn phải xử lý khả năng I/O thất bại.

### Bài 6 — Xử lý đường dẫn từ người dùng

Đường dẫn con nhận từ bên ngoài là `"../../secrets.txt"`. Vì sao gọi `normalize()` rồi `toFile()` chưa đủ bảo đảm người dùng chỉ truy cập trong thư mục cho phép?

**Đáp án:** Chuỗi có thể đi ra ngoài root dự định; symlink và biến đổi filesystem có thể làm thay đổi đích thực tế. Phải có cơ chế kiểm soát phạm vi đường dẫn và quyền truy cập phù hợp, không dựa duy nhất vào việc chuyển kiểu hay normalize.

## 20. Ghi nhớ cốt lõi

- **`File.toPath()`** chuyển một đường dẫn theo API truyền thống thành `Path` thuộc hệ thống tệp mặc định.
- **`Path.toFile()`** chuyển ngược lại khi `Path` thuộc **default file system provider**; không phải mọi Path đều hỗ trợ.
- **Chuyển đổi chỉ đổi biểu diễn.** Nó không mở, đọc, ghi, tạo, xóa tệp; không tự bảo đảm đường dẫn tồn tại hoặc có quyền truy cập.
- **Tương đối/tuyệt đối và canonical path là vấn đề riêng.** Sử dụng `toAbsolutePath`, `normalize` và `toRealPath` đúng ý nghĩa, không hoán đổi tùy tiện.
- **Để hiện đại hóa mã cũ,** dùng `file.toPath()` tại ranh giới rồi thực hiện `Files` API có xử lý ngoại lệ rõ ràng.
- **Để tích hợp thư viện cũ,** chỉ dùng `path.toFile()` khi đã biết provider hỗ trợ; với ZIP/virtual filesystem, ưu tiên API nhận Path/stream hoặc xử lý trích xuất có chủ đích.
- **Không nhầm File/Path với một tài nguyên đã mở.** Đóng luồng/kênh/thư mục stream bằng `try-with-resources`, không phải đóng đối tượng biểu diễn đường dẫn.

### Tài liệu API chính thức (Java SE 17)

- [File.toPath() — java.io.File](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/File.html#toPath())
- [Path.toFile() — java.nio.file.Path](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html#toFile())
- [Path — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html)
- [Files — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [FileSystem — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileSystem.html)
- [StandardCopyOption — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardCopyOption.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="legacy-file-io-010">Khi nào nên ưu tiên Path và Files</a>

<details>
<summary>Xem chi tiết</summary>

# Khi nào nên ưu tiên `Path` và `Files` thay cho `java.io.File`?

Trong Java, cả `java.io.File` lẫn bộ API `java.nio.file` đều có thể đại diện cho tên đường dẫn tới file/thư mục và hỗ trợ những thao tác phổ biến trên hệ thống file. Tuy nhiên, khi viết **mã Java mới trên Java 17+**, phần lớn tình huống làm việc với file và thư mục nên **ưu tiên `Path` + `Files`** thay vì khởi tạo `File` rồi gọi các phương thức truyền thống.

Lý do không chỉ là cú pháp ngắn hơn. NIO.2 (xuất hiện từ Java 7) đem lại mô hình đường dẫn rõ ràng, thao tác file có ngoại lệ chứa nguyên nhân, lựa chọn tham số cho copy/move, API đọc/ghi hiện đại, thuộc tính file, duyệt cây thư mục, symbolic link, trình quan sát thay đổi thư mục và khả năng làm việc với các **file system provider** khác nhau.

Điều này **không có nghĩa `File` đã không còn dùng được**, hoặc tất cả dự án phải xóa ngay `java.io.File`. Mục tiêu là nhận biết khi nào `Path` + `Files` giúp giải quyết đúng bài toán, khi nào vẫn phải dùng `File` để tương thích và cách chuyển đổi an toàn giữa chúng.

## 1. `File`, `Path`, `Files` khác nhau ở vai trò nào?

### `java.io.File` — biểu diễn đường dẫn với API truyền thống

Một đối tượng `File` đại diện cho **tên đường dẫn trừu tượng** (abstract pathname). Nó có thể trỏ tới file, thư mục, đường dẫn chưa tồn tại hoặc vị trí không truy cập được. Việc tạo đối tượng `new File("report.txt")` **không tự tạo file thật** trên đĩa.

`File` kết hợp khá nhiều chức năng trong một lớp: thao tác tên đường dẫn (`getName()`, `getParentFile()`), kiểm tra (`exists()`, `isFile()`), tạo file/thư mục (`createNewFile()`, `mkdirs()`), xóa (`delete()`), đổi tên (`renameTo()`), liệt kê (`listFiles()`), v.v.

API này vẫn được sử dụng rộng rãi bởi thư viện và mã cũ, nhưng có một số nhược điểm:

- Nhiều phép toán trả về **`boolean`**: khi `delete()` hay `renameTo()` trả `false`, caller không nhận ngay ngoại lệ giải thích file không tồn tại, thiếu quyền, file đang được sử dụng hay do filesystem không hỗ trợ.
- Một số API liệt kê trả `null` nếu không phải thư mục **hoặc** khi xảy ra lỗi I/O; cần thêm logic để phân biệt.
- Cơ chế xử lý symbolic link, thuộc tính và copy/move đa dạng không đồng đều như NIO.2.
- `File` chủ yếu gắn với filesystem mặc định của môi trường, không mang mô hình provider tổng quát như `Path`.

### `java.nio.file.Path` — biểu diễn một đường dẫn

`Path` là **interface** thể hiện một vị trí trong filesystem: tên, phần tử đường dẫn, đường dẫn tương đối/tuyệt đối, thao tác `resolve`, `relativize`, `normalize`, `toAbsolutePath`, v.v.

Ví dụ:

```java
Path source = Path.of("data", "report.txt");
Path backup = source.resolveSibling("report.bak");
```

Đây là **đoạn minh họa**, cần import `java.nio.file.Path`. Tương tự `File`, việc tạo một `Path` **không đọc, ghi hoặc kiểm tra** file có thật. `Path` cũng không giữ một file descriptor đã mở.

`Path.of(...)` có từ **Java 11**, còn `Paths.get(...)` có từ **Java 7**. Trong Java 17, `Path.of` thường gọn và đủ dùng.

### `java.nio.file.Files` — các thao tác trên file, thư mục và thuộc tính

`Files` là lớp tiện ích gồm nhiều phương thức tĩnh nhận `Path`:

```java
Files.createDirectories(directory);
Files.writeString(file, "Hello", StandardCharsets.UTF_8);
String text = Files.readString(file, StandardCharsets.UTF_8);
Files.copy(source, destination);
Files.move(oldPath, newPath);
Files.delete(file);
```

Đây là **mã minh họa**, cần các biến được định nghĩa, `IOException` được xử lý và import `java.nio.file.Files`, `java.nio.charset.StandardCharsets`. Các thao tác thật trên filesystem có thể ném ngoại lệ như `NoSuchFileException`, `FileAlreadyExistsException` hay `AccessDeniedException` (tùy provider), giúp caller phản ứng đúng hơn so với một giá trị `false` không rõ nguyên nhân.

> **Quy tắc thực hành:** Dùng **`Path` để mang đường dẫn**, **`Files` để thực hiện thao tác**, và các lớp stream/channel/reader/writer phù hợp khi cần truyền dữ liệu. `Path` + `Files` không phải tên gọi thay thế cho toàn bộ Java NIO không chặn (*non-blocking I/O*); các thao tác `Files` thường vẫn là **blocking I/O**.

## 2. So sánh để chọn API

| Nhu cầu | Cách truyền thống | Cách thường ưu tiên với Java 17+ | Lợi ích chính |
|---|---|---|---|
| Tạo đối tượng đường dẫn | `new File("data/report.txt")` | `Path.of("data", "report.txt")` | API đường dẫn rõ ràng, provider-aware |
| Tạo thư mục và các thư mục cha | `file.mkdirs()` | `Files.createDirectories(path)` | Ngoại lệ khi thất bại, không chỉ `false` |
| Tạo file trống mới | `file.createNewFile()` | `Files.createFile(path)` | Báo file đã tồn tại qua `FileAlreadyExistsException` |
| Kiểm tra loại đường dẫn | `isFile()`, `isDirectory()` | `Files.isRegularFile`, `Files.isDirectory` | Có lựa chọn xử lý symbolic link |
| Đọc file text nhỏ | Mở `FileReader`/`BufferedReader` | `Files.readString(path, UTF_8)` | Gọn, charset tường minh |
| Ghi file text nhỏ | Mở `FileWriter` | `Files.writeString(path, text, UTF_8)` | Tùy chọn tạo/ghi đè/thêm |
| Đọc file lớn theo luồng | `FileInputStream` hoặc `BufferedReader` | `Files.newInputStream` / `Files.newBufferedReader` | Kết hợp đường dẫn, charset và stream |
| Sao chép file | Tự viết vòng lặp input/output | `Files.copy(source, target, options...)` | Có tùy chọn và ngoại lệ |
| Di chuyển/đổi tên | `renameTo` | `Files.move(source, target, options...)` | API rõ nghĩa, tùy chọn atomic khi được hỗ trợ |
| Xóa file | `delete()` trả boolean | `Files.delete(path)` / `deleteIfExists(path)` | Phân biệt trường hợp không tồn tại và lỗi |
| Liệt kê thư mục | `listFiles()` | `Files.list(dir)` / `newDirectoryStream(dir)` | Stream/filter linh hoạt, nhớ đóng |
| Duyệt cây thư mục | Viết recursion bằng `listFiles` | `Files.walk` / `walkFileTree` | Visitor, kiểm soát vòng đời và lỗi |
| Đọc metadata | `length()` / `lastModified()` | `Files.size` / `readAttributes` | Thuộc tính phong phú hơn |
| Symbolic link | Khả năng hạn chế | `Files.isSymbolicLink`, `readSymbolicLink`, `NOFOLLOW_LINKS` | Kiểm soát có chủ đích |
| Theo dõi thay đổi thư mục | Tự polling | `WatchService` | Nhận sự kiện, có giới hạn provider |

**Không dùng bảng như quy tắc tuyệt đối.** Nếu API thư viện bắt buộc `File`, truyền `File` là hợp lý. Với filesystem ảo hoặc provider đặc biệt, `Path` mới là mô hình đúng; `Path.toFile()` có thể không hỗ trợ.

## 3. Khi nào `Path` + `Files` là lựa chọn ưu tiên?

### 3.1 Khi bắt đầu một chức năng thao tác file mới

Nếu service cần đọc, ghi, sao chép, di chuyển hoặc kiểm tra file, API `Files` thường giảm lượng boilerplate. Ví dụ thay vì xây dựng chuỗi đường dẫn bằng dấu `"/"` hoặc `"\\"` theo nền tảng:

```java
Path directory = Path.of("data", "reports");
Path report = directory.resolve("october.txt");
```

`Path` diễn giải dấu phân cách dựa trên filesystem tương ứng. Không cần tự nối `"data/" + name` và hy vọng hoạt động ở mọi nơi.

### 3.2 Khi cần biết **vì sao** thao tác thất bại

`File.delete()` trả `false` khi không xóa được. `Files.delete(path)` khai báo `throws IOException` và có thể ném những lớp chuyên biệt như `NoSuchFileException` hoặc `DirectoryNotEmptyException`. Caller có thể phân biệt:

- File không tồn tại: bỏ qua nếu nghiệp vụ cho phép, hoặc báo lỗi cụ thể.
- Thư mục không rỗng: quyết định có cần thao tác đệ quy không; `Files.delete` **không tự xóa đệ quy**.
- Không đủ quyền hoặc filesystem từ chối: báo lỗi vận hành và không giả làm “không có file”.

**Lưu ý:** những exception cụ thể còn tùy hệ điều hành/file system provider; không có bảo đảm mọi nền tảng sẽ ném đúng một subtype giống nhau trong mọi tình huống. Vẫn phải xử lý `IOException` tổng quát.

### 3.3 Khi phải quản lý tạo thư mục, ghi file với options

`Files.createDirectories(path)` tạo các thư mục cha cần thiết; nếu đường dẫn đã là thư mục thì thao tác có thể thành công. Khác với `Files.createDirectory(path)`, nó hỗ trợ nhiều cấp chưa tồn tại.

`Files.writeString`/`write` hỗ trợ `StandardOpenOption` rõ ràng. Ví dụ:

- Mặc định của `Files.writeString(path, text, charset)` là tạo file nếu chưa có hoặc cắt nội dung file cũ rồi ghi mới.
- `CREATE_NEW`: chỉ tạo nếu chưa có, báo lỗi nếu đường dẫn đã tồn tại (dùng khi không muốn ghi đè).
- `APPEND`: thêm nội dung vào cuối; cần cân nhắc newline và tương thích option.
- `TRUNCATE_EXISTING`: rút file về độ dài 0 khi mở để ghi nếu được chọn.
- `WRITE`: mở để ghi.

**Đừng gọi `writeString` mà không xét khả năng ghi đè nội dung cũ.** Với cấu hình hay dữ liệu quan trọng, chọn `CREATE_NEW` hoặc chiến lược file tạm theo yêu cầu.

### 3.4 Khi cần copy/move với semantics có chủ đích

`Files.copy` và `Files.move` hỗ trợ các option như `StandardCopyOption.REPLACE_EXISTING` (thay thế đích trong phạm vi contract) và `StandardCopyOption.COPY_ATTRIBUTES` (khi áp dụng cho copy, với giới hạn provider).

`StandardCopyOption.ATOMIC_MOVE` yêu cầu một thao tác move nguyên tử nếu provider/filesystem hỗ trợ; có thể ném `AtomicMoveNotSupportedException` khi không hỗ trợ. **Không giả định atomic move luôn sẵn**, đặc biệt qua filesystem khác nhau. Nếu fallback sang move thường thì phải hiểu rằng nó **không còn đảm bảo atomicity**.

Phân biệt:

- **Copy:** giữ nguồn và tạo bản sao đích; `Files.copy(source, target)` không tự copy toàn bộ cây thư mục đệ quy. Copy một đường dẫn thư mục theo API thông thường không có nghĩa toàn bộ nội dung con đều được copy.
- **Move:** chuyển đường dẫn hoặc nội dung theo hành vi provider; không đồng nghĩa “copy rồi delete” luôn an toàn hay nguyên tử.
- **Replace existing:** cần cân nhắc ghi đè tài nguyên đích, không tự bật chỉ vì code ngắn hơn.

### 3.5 Khi cần thuộc tính file, symbolic link và filesystem khác nhau

`Files.readAttributes`, `Files.getLastModifiedTime`, `Files.size`, `Files.getOwner` và các API attribute view giúp lấy thông tin đa dạng hơn `File.length()` hoặc `lastModified()`. Thuộc tính khả dụng phụ thuộc filesystem và provider.

`Files.isSymbolicLink(path)` xác định đường dẫn là symbolic link; `NOFOLLOW_LINKS` là tùy chọn dùng trong các thao tác hỗ trợ để không tự theo link. Điều này quan trọng cho chức năng dọn thư mục, sao lưu, đồng bộ và thao tác trên đường dẫn đến từ bên ngoài.

`Path` gắn với một `FileSystem` và provider. NIO.2 có thể làm việc với các filesystem provider khác (ví dụ ZIP filesystem khi có provider tương ứng), trong khi `java.io.File` thường chỉ làm việc với filesystem mặc định.

## 4. `Path` biểu diễn tên, không bảo đảm file tồn tại

Các phương thức của `Path` chủ yếu biến đổi **cú pháp đường dẫn**, không đụng đĩa:

- `getFileName()`: lấy thành phần tên cuối.
- `getParent()`: lấy phần đường dẫn cha (có thể `null`).
- `getRoot()`: lấy root (có thể `null`).
- `resolve(other)`: kết hợp đường dẫn; nếu `other` là đường dẫn tuyệt đối, `resolve` thường trả đường dẫn tuyệt đối đó.
- `resolveSibling(other)`: thay tên cuối bằng đường dẫn sibling bên cùng cha.
- `normalize()`: loại bỏ các đoạn `.` và giải quyết `..` theo cú pháp, **không truy cập filesystem**.
- `toAbsolutePath()`: tạo đường dẫn tuyệt đối theo filesystem/context của provider, không chứng minh file tồn tại.
- `toRealPath(...)`: truy cập filesystem để lấy đường dẫn thực của một mục **đang tồn tại**, thường giải quyết symbolic link theo options, và có thể ném `IOException`.
- `relativize(other)`: tạo quan hệ tương đối giữa hai `Path` tương thích; có thể lỗi khi trộn root/tuyệt đối/tương đối không tương thích.

### Ví dụ 1 — `resolve`, `normalize`, `resolveSibling`, `relativize`

Tạo tệp `PathSemanticsDemo.java`:

```java
import java.nio.file.Path;

public class PathSemanticsDemo {
    public static void main(String[] args) {
        Path base = Path.of("workspace", "data").toAbsolutePath().normalize();
        Path report = base.resolve("..").resolve("report.txt").normalize();

        System.out.println("is absolute=" + report.isAbsolute());
        System.out.println("filename=" + report.getFileName());
        System.out.println("parent ends workspace="
                + report.getParent().endsWith("workspace"));
        System.out.println("resolveSibling matches="
                + report.equals(base.resolveSibling("report.txt")));
        System.out.println("relative name=" + report.getParent().relativize(report));
    }
}
```

Biên dịch/chạy với Java 17 trở lên:

```bash
javac -encoding UTF-8 --release 17 PathSemanticsDemo.java
java PathSemanticsDemo
```

Kết quả:

```text
is absolute=true
filename=report.txt
parent ends workspace=true
resolveSibling matches=true
relative name=report.txt
```

Ví dụ không cần file `report.txt` có thật. `base` là đường dẫn tuyệt đối từ thư mục làm việc hiện tại, `resolve("..")` tạo đoạn cha theo cú pháp và `normalize()` loại bỏ đoạn thừa.

`Path.equals` so sánh đường dẫn **theo quy tắc equality của provider**, không nhất thiết tương đương “hai đường dẫn trỏ cùng file thật”. Ví dụ đường dẫn qua symbolic link hoặc tên khác kiểu case vẫn có thể cần `Files.isSameFile(a, b)` nếu muốn hỏi hai đường dẫn đang tồn tại có chỉ cùng một file hay không.

**Điều kiện quan trọng:** `normalize()` **không phải kiểm tra an ninh**. Nếu tồn tại symbolic link giữa các đoạn đường dẫn, kết quả chuẩn hóa cú pháp có thể khác đường đi vật lý. Không dùng nó một mình để xác nhận dữ liệu tải lên vẫn nằm trong thư mục được phép.

## 5. Ví dụ 2 — làm việc với file thật bằng `Files`

Tạo tệp `FilesCrudDemo.java`:

```java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.stream.Stream;

public class FilesCrudDemo {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("path-files-");
        try {
            Path folder = Files.createDirectories(root.resolve("docs/notes"));
            Path source = folder.resolve("note.txt");
            String content = "Xin chào Java\n";
            Files.writeString(source, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Path copy = root.resolve("copy.txt");
            Files.copy(source, copy);
            Path moved = root.resolve("moved.txt");
            Files.move(copy, moved);

            System.out.println("folder directory=" + Files.isDirectory(folder));
            System.out.println("UTF-8 round trip="
                    + content.equals(Files.readString(source, StandardCharsets.UTF_8)));
            System.out.println("copy remains=" + Files.exists(copy));
            System.out.println("moved regular file=" + Files.isRegularFile(moved));
            System.out.println("contents equal="
                    + Files.readString(source, StandardCharsets.UTF_8)
                        .equals(Files.readString(moved, StandardCharsets.UTF_8)));
            System.out.println("file size positive=" + (Files.size(source) > 0));
            try (Stream<Path> walk = Files.walk(root)) {
                System.out.println("regular files=" + walk.filter(Files::isRegularFile).count());
            }
        } finally {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }
    }
}
```

```bash
javac -encoding UTF-8 --release 17 FilesCrudDemo.java
java FilesCrudDemo
```

```text
folder directory=true
UTF-8 round trip=true
copy remains=false
moved regular file=true
contents equal=true
file size positive=true
regular files=2
```

**Giải thích theo thứ tự:**

1. `Files.createTempDirectory` tạo thư mục tạm riêng; tên thực tế có hậu tố ngẫu nhiên, nhưng ví dụ không in đường dẫn nên stdout ổn định.
2. `Files.createDirectories` tạo cả `docs` và `notes` bên trong thư mục tạm.
3. `Files.writeString` ghi tiếng Việt với `UTF-8` và `CREATE_NEW`, để không âm thầm ghi đè file `note.txt`.
4. `Files.copy` tạo file `copy.txt`. Sau `Files.move(copy, moved)`, đường dẫn `copy.txt` không còn, còn `moved.txt` là file hợp lệ.
5. `Files.readString` lấy nội dung text từ cả hai file và xác nhận dữ liệu không đổi. `Files.size(source)` cho số **byte**, không phải số `char` của Java.
6. `Files.walk(root)` duyệt toàn cây, lọc file thường và thấy hai file: `note.txt`, `moved.txt`.
7. Khối `finally` dọn file/thư mục tạm theo thứ tự sâu lên nông bằng `Comparator.reverseOrder()`; phương thức `walk` được đóng trong `try-with-resources`.

Mặc dù ví dụ có import `StandardCopyOption` để dễ mở rộng, demo này **không sử dụng** `REPLACE_EXISTING`: nếu đích đã tồn tại, thao tác copy/move thông thường có thể thất bại thay vì ghi đè. Với một cây thư mục do người dùng chỉ định, việc xóa đệ quy là hành động nguy hiểm; ví dụ chỉ dọn một thư mục tạm **do chính nó vừa tạo**, không phải mã xóa một đường dẫn tùy ý.

**Khả năng tương thích:** file trong ví dụ được tạo trên filesystem mặc định, nên hành vi phù hợp với Java 17 ở môi trường thông thường. Vẫn có thể phát sinh `IOException` nếu môi trường từ chối ghi/xóa hoặc hết dung lượng; chương trình không cam kết thao tác filesystem luôn thành công.

## 6. `Files.readString`, `writeString` và dữ liệu lớn

### Đọc hoặc ghi file text nhỏ

Với Java 11+:

```java
String text = Files.readString(path, StandardCharsets.UTF_8);
Files.writeString(path, text, StandardCharsets.UTF_8);
```

Đây là **đoạn minh họa**: phải khai báo `Path path`, import `Files`/`StandardCharsets` và xử lý `IOException`. Nó phù hợp với cấu hình, tệp văn bản nhỏ, snapshot có kích thước vừa phải.

Nếu cần đọc từng dòng của file lớn, chọn `Files.newBufferedReader(path, UTF_8)` hoặc `Files.lines(path, UTF_8)`, chứ không nhất thiết đưa toàn bộ file vào một chuỗi. Nếu cần xử lý file nhị phân, chọn `Files.newInputStream`, `Files.newOutputStream` hoặc `Files.newByteChannel`.

### API streaming phải được đóng

Ví dụ đọc từng dòng:

```java
try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
    lines.filter(line -> !line.isBlank()).forEach(System.out::println);
}
```

Đây là **đoạn minh họa**, cần import `java.util.stream.Stream`, `Path`, `Files` và charset tương ứng. `Files.lines` giữ tài nguyên file bên dưới; **gọi terminal operation không thay thế close**. Dùng `try-with-resources` để đóng stream.

Tương tự, `Files.list(dir)`, `Files.walk(dir)` và `Files.find(...)` trả stream gắn với tài nguyên hệ thống. Việc quên đóng gây rò rỉ descriptor hoặc handle, đặc biệt khi service hoạt động liên tục.

### Byte I/O và charset là hai vấn đề khác nhau

`Path` và `Files` không tự biến dữ liệu nhị phân thành text. Khi đọc văn bản, chỉ định charset phù hợp (thường `StandardCharsets.UTF_8`); khi đọc ảnh/ZIP, xử lý byte.

Đừng dùng `String.length()` để tính số byte file UTF-8; một ký tự tiếng Việt hoặc emoji có thể cần nhiều byte. `Files.size(path)` đo **số byte** của file, còn `String.length()` đo **UTF-16 code unit** của chuỗi.

## 7. Ví dụ 3 — tương thích `java.io.File` khi thư viện cũ yêu cầu

Tạo tệp `FileInteropDemo.java`:

```java
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public class FileInteropDemo {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("interop-");
        try {
            Path child = root.resolve("report.txt");
            File legacy = child.toFile();
            System.out.println("same path=" + legacy.toPath().equals(child));
            System.out.println("before create=" + Files.exists(child));
            Files.writeString(child, "report", StandardCharsets.UTF_8);
            System.out.println("after create=" + legacy.isFile());
            System.out.println("legacy name=" + legacy.getName());
            System.out.println("read=" + Files.readString(child, StandardCharsets.UTF_8));
            try {
                Files.readString(root.resolve("missing.txt"));
            } catch (NoSuchFileException e) {
                System.out.println("missing exception=" + e.getClass().getSimpleName());
            }
        } finally {
            Files.deleteIfExists(root.resolve("report.txt"));
            Files.deleteIfExists(root);
        }
    }
}
```

```bash
javac -encoding UTF-8 --release 17 FileInteropDemo.java
java FileInteropDemo
```

```text
same path=true
before create=false
after create=true
legacy name=report.txt
read=report
missing exception=NoSuchFileException
```

Trong ví dụ, `root` thuộc filesystem mặc định nên `child.toFile()` hợp lệ. `File.toPath()` chuyển lại sang biểu diễn `Path` và kết quả so sánh cùng đường dẫn.

**`Path.toFile()` không phổ quát**: nếu `Path` thuộc file system provider khác filesystem mặc định, phương thức có thể ném `UnsupportedOperationException`. Do đó **đừng chuyển sang `File` trừ khi bạn thực sự cần API cũ**.

**`Files.exists(path)` không phải kiểm tra chắc chắn:** phương thức có thể trả `false` nếu không thể xác định trạng thái tồn tại (ví dụ thiếu quyền). Khi thực hiện thao tác có yêu cầu file tồn tại, hãy gọi trực tiếp `Files.readString`, `Files.newInputStream` hoặc API cụ thể và **xử lý IOException**, thay vì mặc định tin vào một kết quả `exists` trước đó.

## 8. Các lựa chọn thao tác đường dẫn quan trọng

### `Path.of` và đường dẫn tương đối

`Path.of("data", "output.txt")` tạo đường dẫn **tương đối** với bối cảnh làm việc của filesystem; trên filesystem mặc định thường là thư mục làm việc của tiến trình. Trên IDE, terminal, test runner và môi trường triển khai, working directory có thể khác nhau.

Khi file phải nằm trong một thư mục cấu hình, hãy xác định root và dùng `resolve` trên root đó thay vì dựa vào một đường dẫn tương đối chưa biết ý nghĩa.

`Path.of("data/output.txt").toAbsolutePath()` không tự xác minh file tồn tại, còn `toRealPath()` thường yêu cầu đối tượng file có tồn tại và truy cập được.

### `normalize`, `toAbsolutePath` và `toRealPath`

| Phương thức | Tính chất | Có truy cập filesystem? |
|---|---|---|
| `normalize()` | Tinh gọn `.`, `..` theo cú pháp | Không |
| `toAbsolutePath()` | Chuyển sang đường dẫn tuyệt đối theo provider | Thường không kiểm tra tồn tại |
| `toRealPath()` | Lấy đường dẫn thực, xử lý link theo options | Có; thường cần mục tồn tại |
| `Files.isSameFile(a, b)` | Hỏi hai đường dẫn hiện tại có trỏ cùng file không | Có thể truy cập filesystem |

**Đường dẫn chuẩn hóa không đồng nghĩa đường dẫn an toàn.** Ví dụ ứng dụng có một thư mục upload được phép mà người dùng kiểm soát một phần của đường dẫn; đường dẫn chứa `..` có thể thoát khỏi root. Ngay cả sau `normalize`, một symbolic link bên trong root có thể trỏ ra ngoài.

Không dùng kiểm tra chuỗi thuần như `candidate.toString().startsWith(root.toString())` để quyết định một đường dẫn có thuộc root hay không: chuỗi `/data/uploads2` cũng bắt đầu bằng `/data/uploads`. `Path.startsWith(other)` so sánh theo **thành phần đường dẫn**, tốt hơn kiểm tra tiền tố chuỗi, nhưng **vẫn không loại được nguy cơ symlink hoặc thay đổi đồng thời**.

## 9. Symbolic link: một lý do lớn để dùng `Path` + `Files`

**Symbolic link** (liên kết biểu tượng) là một mục filesystem tham chiếu tới đường dẫn khác. Khi đường dẫn trỏ tới symlink, thao tác theo link hay thao tác **chính symlink** có thể cho kết quả rất khác nhau.

Các API cần biết:

- `Files.isSymbolicLink(path)`: kiểm tra đối tượng đang được coi là symbolic link.
- `Files.readSymbolicLink(path)`: đọc nội dung đường dẫn mà symlink lưu.
- `Files.createSymbolicLink(link, target)`: tạo symlink, có thể cần quyền/điều kiện môi trường.
- `LinkOption.NOFOLLOW_LINKS`: truyền ở các thao tác hỗ trợ để không theo symbolic link.
- `Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW_LINKS)`: lấy attributes của link thay vì đối tượng đích khi cần.
- `Path.toRealPath(...)`: lấy đường dẫn thực dựa trên filesystem; mặc định thường resolve link nếu không dùng `NOFOLLOW_LINKS`.

**Khi nào cần lưu ý nhất?** Quét upload, xóa file, đồng bộ, sao lưu, đọc đường dẫn do user cung cấp, giới hạn thao tác trong một root được phép.

Ví dụ logic bảo vệ chỉ bằng:

```java
Path candidate = root.resolve(userInput).normalize();
if (!candidate.startsWith(root)) {
    throw new SecurityException("Outside root");
}
```

là **minh họa chưa đủ an toàn để triển khai trực tiếp**. Nó chỉ chặn một lớp đường dẫn thoát root theo cú pháp nếu `root` đã cùng loại/định dạng và chuẩn hóa thích hợp. Nó **không** chặn symlink dẫn ra ngoài root hoặc tấn công thay đổi liên kết giữa lần kiểm tra và thao tác (*TOCTOU*).

Trong quy trình nhạy cảm, phải xác định threat model, dùng các kiểm tra đường dẫn thực thích hợp khi mục tồn tại, tránh follow link không cần thiết và khi khả dụng dùng thao tác tương đối với directory handle an toàn (ví dụ `SecureDirectoryStream` nếu provider hỗ trợ). Việc bảo vệ khỏi race đòi hỏi thiết kế filesystem rõ ràng, không có một lời gọi `normalize()` nào tự giải quyết hết.

## 10. Tạo, xóa và duyệt thư mục đúng cách

### Tạo thư mục

`Files.createDirectory(path)` tạo **một** thư mục, đòi hỏi cha phù hợp phải tồn tại. `Files.createDirectories(path)` có thể tạo cả chuỗi cha còn thiếu.

Lưu ý `createDirectories` không có nghĩa thao tác nhiều cấp hoàn toàn nguyên tử: nếu lỗi giữa chừng, một số thư mục cha có thể đã được tạo.

### Xóa

`Files.delete(path)` xóa một file, symlink hoặc thư mục rỗng; với thư mục còn con, nó **không tự động xóa đệ quy**. `Files.deleteIfExists(path)` bỏ qua tình huống đường dẫn không tồn tại, nhưng vẫn có thể ném `IOException` đối với lỗi khác.

Xóa cả cây thư mục phải có logic rõ quyền sở hữu và thứ tự xóa. Khi dữ liệu thuộc người dùng hoặc chứa symlink, càng cần kiểm soát. **Không sao chép ví dụ cleanup một thư mục tạm vào một chức năng xóa đường dẫn bất kỳ lấy từ form.**

### Duyệt một cấp so với đệ quy

- `Files.list(dir)`: các mục trực tiếp trong thư mục, không tự duyệt sâu.
- `Files.newDirectoryStream(dir)`: iterator directory entry, hữu ích khi cần duyệt thư mục có kiểm soát.
- `Files.walk(dir)`: stream đi qua cây thư mục, theo depth-first trong một số chi tiết traversal, có tùy chọn độ sâu.
- `Files.walkFileTree(root, visitor)`: callback visitor, kiểm soát vào/ra thư mục và xử lý lỗi I/O theo từng mục; hợp lý cho copy/delete tree hoặc công cụ quét có nhiều nhánh xử lý.

Không tự sử dụng `Files.walk(root)` rồi ép duyệt toàn bộ cây nhiều triệu file chỉ để kiểm tra một tên cụ thể khi có thuật toán gọn hơn. Hãy đóng stream và cân nhắc độ sâu, quyền truy cập, symbolic link, chu trình và chiến lược xử lý exception.

### Khi cần quan sát thay đổi filesystem

`WatchService` có thể theo dõi sự kiện tạo, xóa, sửa trong thư mục trên provider hỗ trợ. Nó không phải giải pháp đồng bộ tuyệt đối: event có thể bị gộp, bỏ sót trong một số điều kiện hoặc nhận `OVERFLOW`; cần có cơ chế quét lại nếu ứng dụng đòi hỏi trạng thái chính xác. Đừng hiểu WatchService là “giám sát tất cả thư mục con” mặc định; thường phải đăng ký những thư mục cụ thể theo thiết kế.

## 11. Tránh lỗi TOCTOU: “kiểm tra trước rồi làm” không bảo đảm

**TOCTOU** (*time-of-check to time-of-use*) là lỗi khi trạng thái hệ thống thay đổi giữa bước kiểm tra và thao tác. Ví dụ:

```java
if (!Files.exists(target)) {
    Files.writeString(target, text);
}
```

Đây là **mã minh họa về cách làm có race**, không phải giải pháp an toàn. Một thread/process khác có thể tạo `target` ngay sau khi `exists` trả `false` và trước lúc `writeString`; khi đó phương thức ghi mặc định có thể **ghi đè** file mới tạo.

Nếu ý định là **chỉ tạo mới khi chưa tồn tại**, dùng `CREATE_NEW` để filesystem thực hiện điều kiện tạo file theo semantics của operation:

```java
Files.writeString(target, text, StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
```

Đây là **đoạn minh họa** cần có `target`, `text` và xử lý `IOException`. Với dữ liệu không tin cậy và symlink, vẫn phải xem xét quyền trên thư mục cha, provider và toàn bộ mô hình bảo vệ.

Tương tự, `Files.exists`/`isRegularFile`/`isReadable` chỉ là các **truy vấn trạng thái tại một thời điểm**; sau đó file có thể bị xóa, đổi quyền hoặc thay thế. Với tác vụ cần kết quả chính xác, thực hiện operation cần thiết và xử lý ngoại lệ.

## 12. Ghi file an toàn và tính nguyên tử

Nếu chương trình ghi trực tiếp vào file cấu hình hay tài liệu quan trọng, sự cố giữa chừng có thể để lại file dở dang. `Files.writeString` không mặc nhiên tạo một transaction ghi tất-cả-hoặc-không-gì.

Một chiến lược phổ biến:

1. Tạo một file tạm **trong cùng thư mục hoặc filesystem đích** nếu phù hợp.
2. Ghi nội dung mới vào file tạm, đóng/flush nguồn ghi.
3. Validate nội dung hoặc kiểm tra checksum theo yêu cầu.
4. Di chuyển file tạm tới đích, có thể yêu cầu `ATOMIC_MOVE` khi filesystem hỗ trợ.
5. Nếu `ATOMIC_MOVE` không được hỗ trợ, quyết định rõ **từ chối thao tác** hay **fallback không nguyên tử**, dựa trên yêu cầu.
6. Dọn file tạm nếu thất bại; cân nhắc quyền, attributes và khôi phục sau sự cố.

Mẫu minh họa, **không phải một chương trình độc lập**:

```java
Path temporary = Files.createTempFile(target.getParent(), "write-", ".tmp");
try {
    Files.writeString(temporary, text, StandardCharsets.UTF_8);
    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
} finally {
    Files.deleteIfExists(temporary);
}
```

Đoạn trên cần import `Path`, `Files`, `StandardCharsets` và `StandardCopyOption`, cần `target.getParent()` là thư mục tồn tại và các biến đã xác định. Nó **không** bao hàm chính sách thay thế file đích và không có fallback. `ATOMIC_MOVE` có thể ném `AtomicMoveNotSupportedException`. **Atomic rename không đồng nghĩa dữ liệu đã bền vững trên ổ đĩa sau mất điện**; durability thực sự có thể cần đồng bộ file/thư mục và cơ chế phù hợp với filesystem.

Đừng tự coi `REPLACE_EXISTING` + `ATOMIC_MOVE` luôn có cùng ngữ nghĩa thay thế ở mọi provider. Với mục tiêu ghi file quan trọng, phải kiểm tra hỗ trợ và contract thực tế.

## 13. `File` vẫn phù hợp ở đâu?

### 13.1 API thư viện cũ yêu cầu `File`

Nhiều thư viện, framework, công cụ test hoặc legacy SDK có constructor như `SomeLibrary(File source)`. Nếu bạn đã có `Path` trên filesystem mặc định, có thể dùng `path.toFile()` ở ranh giới tích hợp.

Nếu nhận `File` từ API cũ, gọi `file.toPath()` và tiếp tục thao tác bằng `Files`. Đây là quá trình **chuyển đổi đơn giản khi tương thích**; không cần thiết viết lại tất cả dữ liệu qua stream trung gian.

### 13.2 Đang bảo trì hệ thống dùng `File` nhất quán

Một module ổn định có API public sử dụng `File` và hàng trăm call site không bắt buộc chuyển toàn bộ cùng lúc. Khi bổ sung chức năng mới đòi hỏi copy/move, metadata, symlink hay ngoại lệ cụ thể, có thể chuyển `File` sang `Path` cục bộ rồi dùng `Files`, thay vì thay đổi public API và gây migration lớn.

### 13.3 Khi nhu cầu đơn giản và contract đã đủ

`File` không “sai” nếu logic thực sự chỉ cần kiểm tra một đường dẫn trong một test hay tích hợp legacy. Quan trọng là **hiểu semantics** của `exists`, `delete`, `renameTo`, và các trường hợp thất bại, chứ không phải áp dụng một lệnh cấm tuyệt đối.

### 13.4 Java phiên bản rất cũ

`Path`/`Files` xuất hiện từ Java 7, còn `Path.of` và `Files.readString/writeString` từ Java 11. Dự án bị ràng buộc với Java 6 hoặc cũ hơn không có NIO.2 chuẩn. Nhưng với **Java 17+**, thông thường không có lý do kỹ thuật để tiếp tục dùng toàn bộ thao tác file mới bằng `File` chỉ vì tương thích phiên bản Java.

## 14. Các lưu ý về provider và tính di động

**`Path` không phải chỉ là chuỗi.** Một `Path` thuộc một `FileSystem` có provider và quy tắc riêng. Hai đường dẫn từ provider hoặc filesystem khác nhau có thể không tương thích với một số thao tác kết hợp, so sánh hoặc di chuyển.

Một số điểm cần nhớ:

- `Path.of(...)` tạo `Path` ở **default filesystem** của runtime.
- `FileSystem.getPath(...)` có thể tạo `Path` của provider cụ thể.
- `File.toPath()` làm việc với `File` và filesystem mặc định.
- `Path.toFile()` có thể ném `UnsupportedOperationException` với `Path` từ provider không phải default.
- Một số phương thức `Files` có thể ném `UnsupportedOperationException`, `IOException` hoặc ngoại lệ tương ứng nếu feature không được hỗ trợ.
- Tính năng POSIX file permissions, owner/group, ACL hoặc extended attributes không đồng đều giữa Windows/Linux/filesystem ảo.
- Symlink creation có thể cần quyền đặc biệt. Đừng giả định test tạo symlink hoạt động trên mọi máy.
- `Files.copy` giữa provider khác nhau phụ thuộc contract, có thể thiếu metadata hoặc thất bại; kiểm tra mục tiêu của hệ thống thay vì suy luận chung.

API `Path` và `Files` **giúp làm rõ ranh giới filesystem**, nhưng không tự bảo đảm tất cả thao tác chạy đồng nhất trên mọi provider. Khi làm công cụ đa hệ điều hành, viết test với filesystem và permission thực tế quan trọng không kém việc chọn API.

## 15. Những lỗi phổ biến khi chuyển từ `File` sang `Path`/`Files`

1. **Nghĩ tạo `Path` là tạo file:** `Path.of("x.txt")` chỉ tạo một đối tượng đường dẫn; phải gọi `Files.createFile`, `writeString`, v.v.
2. **Coi `normalize` là đường dẫn thực:** nó chỉ biến đổi cú pháp, không resolve symlink hoặc bảo đảm mục tồn tại.
3. **Dựa vào working directory mà không biết:** đường dẫn tương đối có thể trỏ khác nơi giữa IDE và production.
4. **Đọc toàn bộ file lớn bằng `Files.readString`:** dễ tốn bộ nhớ; dùng buffered reader/stream khi xử lý dữ liệu lớn.
5. **Quên đóng `Files.lines`/`list`/`walk`:** stream có tài nguyên hệ thống; dùng `try-with-resources`.
6. **Tin rằng `Files.copy(dir, target)` sao chép đệ quy:** thao tác copy trên mục thư mục **không** tự copy toàn bộ con; cần chiến lược tree traversal.
7. **Tưởng `Files.delete` sẽ xóa thư mục không rỗng:** phương thức không tự dọn toàn cây.
8. **Dùng `Files.exists` rồi ghi mà không chọn option:** tạo race và có nguy cơ ghi đè dữ liệu; dùng `CREATE_NEW` khi cần điều kiện chỉ-tạo-mới.
9. **Tưởng `Files.exists(path) == false` chứng minh chắc chắn file không tồn tại:** có thể do không xác định được trạng thái; gọi thao tác và xử lý ngoại lệ.
10. **Bật `REPLACE_EXISTING` vô thức:** dễ làm mất dữ liệu đã có.
11. **Giả định `ATOMIC_MOVE` luôn được hỗ trợ:** phải xử lý `AtomicMoveNotSupportedException` và quyết định fallback hợp lý.
12. **Tin rằng atomic move bảo đảm durability:** rename atomic và flush/fsync là hai yêu cầu khác nhau.
13. **Chuyển mọi `Path` thành `File`:** provider không mặc định có thể không hỗ trợ `toFile()`.
14. **Quét/xóa thư mục theo đường dẫn do user cung cấp mà bỏ qua symlink:** có thể dẫn ra ngoài vùng được phép; cần threat model và kiểm soát race.
15. **Đọc text mà không quản lý charset:** dùng `StandardCharsets.UTF_8` hoặc charset do định dạng quy định; đừng coi filename extension quyết định encoding.
16. **Không kiểm soát lỗi filesystem:** permission, path không tồn tại, file bị thay đổi hoặc khóa có thể khiến thao tác thất bại; đừng gom mọi lỗi thành “không có file”.
17. **Quên khác biệt file metadata và file content:** `Files.size` trả byte, `String.length()` trả UTF-16 code units.
18. **Chỉ test trên một hệ điều hành:** phân cách đường dẫn, ký tự hợp lệ trong tên file, case sensitivity, symbolic link và quyền có thể khác.
19. **Kết luận `Files` là non-blocking I/O:** đây là API filesystem tiện ích; đọc/ghi đồng bộ thường vẫn có thể chặn thread.
20. **Dùng `isSameFile` thay cho so sánh cú pháp khi chỉ muốn so tên:** `isSameFile` có thể đụng filesystem và ném `IOException`; `Path.equals` có nghĩa khác.

## 16. Checklist lựa chọn nhanh

| Nếu ứng dụng cần... | Ưu tiên... | Vì sao |
|---|---|---|
| Code Java 17 mới xử lý đường dẫn/file | `Path` + `Files` | API rõ nghĩa, options, ngoại lệ |
| Chuyển đường dẫn cha/con hoặc tương đối | `resolve`, `relativize`, `normalize` | Không phải nối chuỗi thủ công |
| Đọc/ghi file text nhỏ | `Files.readString/writeString` | Ngắn gọn, charset rõ |
| Đọc file text lớn theo dòng | `Files.newBufferedReader` hoặc `Files.lines` | Tránh giữ toàn bộ file trong RAM |
| Sao chép/di chuyển/xóa | `Files.copy/move/delete` | Có lựa chọn và mô tả lỗi |
| Duyệt, lọc hoặc thao tác cây thư mục | `Files.list/walk/walkFileTree` | Phù hợp cho cấu trúc cây, xử lý lỗi |
| Xem attributes hoặc xử lý symbolic link | `Files.readAttributes`, `LinkOption` | Thông tin rõ hơn |
| Phát hiện thay đổi thư mục | `WatchService` | Hệ thống sự kiện có giới hạn |
| Tích hợp thư viện chỉ nhận `java.io.File` | `path.toFile()` nếu hỗ trợ | Giữ tương thích |
| Nhận `File` từ thư viện cũ rồi thao tác mới | `file.toPath()` + `Files` | Dễ tăng tính rõ ràng mà không migration toàn bộ |
| Tạo file chỉ khi chưa tồn tại | `Files.writeString(..., CREATE_NEW, WRITE)` | Tránh check-then-write race |
| Ghi file quan trọng không công bố bản dở dang | File tạm + validate + move có kiểm tra | Rõ chiến lược nguyên tử và lỗi |

## 17. Tự kiểm tra kiến thức

**Câu 1.** Dùng `Path.of("notes.txt")` có tạo file trên đĩa không?  
**Đáp án:** không. `Path` chỉ là đại diện đường dẫn. Phải gọi một API như `Files.createFile` hoặc `Files.writeString` để tác động lên filesystem.

**Câu 2.** Một lệnh `file.delete()` trả `false`; tại sao `Files.delete(path)` thường có ích hơn?  
**Đáp án:** `Files.delete` có thể ném `IOException`/subtype cụ thể và giúp biết lỗi do file vắng mặt, thư mục chưa rỗng hay lý do khác (tùy provider).

**Câu 3.** `Files.copy(sourceDirectory, targetDirectory)` có copy toàn bộ cây con không?  
**Đáp án:** không. Cần thiết kế sao chép đệ quy riêng khi muốn copy toàn cây.

**Câu 4.** `Files.readString(path, UTF_8)` thích hợp cho file 100 GB không?  
**Đáp án:** thông thường không, vì đưa dữ liệu vào bộ nhớ. Nên dùng buffered reader, stream hoặc xử lý theo khối.

**Câu 5.** `Files.exists(path) == false` rồi gọi `Files.writeString(path, data)` có bảo đảm không ghi đè file được tạo đồng thời không?  
**Đáp án:** không. Có TOCTOU; dùng `CREATE_NEW` nếu chỉ được tạo khi chưa tồn tại.

**Câu 6.** Vì sao `normalize()` không đủ bảo đảm một đường dẫn con nằm trong root upload?  
**Đáp án:** nó chỉ xử lý `.` và `..` theo cú pháp; symbolic link và thay đổi đồng thời của filesystem có thể đưa thao tác tới vị trí khác.

**Câu 7.** `Path.toFile()` có chắc chạy với ZIP filesystem không?  
**Đáp án:** không. `Path` từ provider không mặc định có thể không hỗ trợ chuyển sang `File`.

**Câu 8.** `Files.move(source, target, ATOMIC_MOVE)` báo `AtomicMoveNotSupportedException` thì nên làm gì?  
**Đáp án:** tùy yêu cầu: thất bại có kiểm soát nếu atomicity bắt buộc, hoặc quyết định fallback không nguyên tử và thông báo/thiết kế xử lý trạng thái trung gian.

**Câu 9.** Lúc nào vẫn hợp lý dùng `java.io.File`?  
**Đáp án:** khi API legacy yêu cầu `File`, dự án chưa cần migration lớn hoặc nhu cầu nhỏ có contract rõ; có thể chuyển sang `Path` tại ranh giới cần chức năng NIO.2.

**Câu 10.** `Files.walk(dir).count()` xong là stream tự đóng chưa?  
**Đáp án:** không nên giả định. Stream của `Files.walk` có tài nguyên, phải đóng trong `try-with-resources`.

## Kết luận

Với **Java 17+**, hãy coi **`Path` + `Files` là lựa chọn mặc định** cho mã mới xử lý đường dẫn, file và thư mục. `Path` mô tả vị trí và thao tác đường dẫn, còn `Files` xử lý filesystem với API rõ nghĩa, ngoại lệ phong phú, lựa chọn tạo/copy/move, metadata và duyệt cây. `java.io.File` vẫn hữu ích ở **ranh giới tương thích legacy**; có thể chuyển bằng `file.toPath()` hoặc `path.toFile()` khi provider hỗ trợ.

Điều quan trọng hơn tên API là hiểu hợp đồng: **tạo đường dẫn khác tạo file; truy vấn trạng thái khác thao tác thật; `normalize` khác đường dẫn thực; copy thư mục khác copy đệ quy; atomic move khác durability; stream có tài nguyên phải đóng**. Khi dùng đúng các nguyên tắc đó, `Path` và `Files` giúp mã Java dễ đọc, dễ kiểm soát lỗi và dễ mở rộng hơn.

### Tài liệu Java SE 17

- [`Path`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html) và [`Paths`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Paths.html)
- [`Files`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [`java.io.File`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/File.html)
- [`StandardOpenOption`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardOpenOption.html)
- [`StandardCopyOption`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardCopyOption.html)
- [`LinkOption`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/LinkOption.html), [`FileVisitOption`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileVisitOption.html)
- [`BasicFileAttributes`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/BasicFileAttributes.html)
- [`WatchService`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/WatchService.html)


</details>

- [Quay lại đầu trang](#back-to-top)
