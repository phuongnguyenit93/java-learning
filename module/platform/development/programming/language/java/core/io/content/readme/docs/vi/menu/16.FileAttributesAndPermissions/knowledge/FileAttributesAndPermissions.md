<a id="back-to-top"></a>

# Thuộc tính và quyền

## Menu
- [BasicFileAttributes và BasicFileAttributeView](#file-attributes-and-permissions-001)
- [Thời gian tạo, sửa, truy cập và FileTime](#file-attributes-and-permissions-002)
- [Kích thước, loại tệp và fileKey](#file-attributes-and-permissions-003)
- [Đọc thuộc tính theo kiểu hoặc theo tên](#file-attributes-and-permissions-004)
- [POSIX permission và PosixFileAttributeView](#file-attributes-and-permissions-005)
- [Thuộc tính DOS trên Windows](#file-attributes-and-permissions-006)
- [Owner, UserPrincipal và AclFileAttributeView](#file-attributes-and-permissions-007)
- [UserDefinedFileAttributeView và hỗ trợ theo file system](#file-attributes-and-permissions-008)
- [Thiết lập thuộc tính khi tạo tệp](#file-attributes-and-permissions-009)
- [FileStore, dung lượng và supportedFileAttributeViews](#file-attributes-and-permissions-010)
- [probeContentType và giới hạn suy đoán MIME type](#file-attributes-and-permissions-011)

## <a id="file-attributes-and-permissions-001">BasicFileAttributes và BasicFileAttributeView</a>

<details>
<summary>Xem chi tiết</summary>

# BasicFileAttributes và BasicFileAttributeView trong Java NIO

Trong Java NIO.2, **`java.nio.file.attribute.BasicFileAttributes`** và **`java.nio.file.attribute.BasicFileAttributeView`** đều liên quan tới metadata của một file hoặc thư mục, nhưng **không có cùng vai trò**:

- **`BasicFileAttributes`** là một **giao diện dữ liệu chỉ đọc**: chứa các giá trị đã lấy về, như loại mục, kích thước, timestamp và khóa nhận diện file nếu có.
- **`BasicFileAttributeView`** là một **giao diện để truy cập nhóm thuộc tính cơ bản của một đường dẫn**: cho phép gọi `readAttributes()` để đọc bộ thuộc tính mới và `setTimes(...)` để yêu cầu thay đổi một số timestamp.

Đọc thuộc tính không đồng nghĩa mở nội dung file. Cập nhật timestamp không phải cập nhật nội dung byte. Ngoài ra, metadata của hệ thống tập tin có thể thay đổi ngay sau khi được đọc, phụ thuộc quyền, loại file, symbolic link và khả năng của file system provider.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

1. Kể tên và sử dụng đúng các phương thức của `BasicFileAttributes`: `size`, `creationTime`, `lastModifiedTime`, `lastAccessTime`, `isRegularFile`, `isDirectory`, `isSymbolicLink`, `isOther`, `fileKey`.
2. Đọc metadata theo kiểu an toàn bằng `Files.readAttributes(path, BasicFileAttributes.class)`.
3. Lấy một `BasicFileAttributeView` từ `Files.getFileAttributeView(...)` và sử dụng `view.readAttributes()` hoặc `view.setTimes(...)`.
4. Hiểu các tham số `null` của `setTimes`, độ chính xác timestamp, lỗi I/O và những hành vi phụ thuộc provider.
5. Phân biệt snapshot metadata với file thực, không suy ra việc đọc thuộc tính là một thao tác khóa/snapshot nguyên tử của toàn hệ thống.
6. Phân biệt chế độ theo symbolic link mặc định với `LinkOption.NOFOLLOW_LINKS`.
7. Nhận biết khi cần các view chuyên biệt như `PosixFileAttributeView` hoặc `DosFileAttributeView` thay vì cố dùng basic view để đổi quyền.
8. Viết chương trình Java 17+ có thể biên dịch/chạy, kiểm tra metadata trên file tạm và xử lý trường hợp file chưa tồn tại.

## 2. Hai abstraction: bộ giá trị và cửa sổ thao tác

Hình dung quan hệ:

~~~text
Path
  |
  +-- Files.readAttributes(path, BasicFileAttributes.class)
  |          |
  |          v
  |    BasicFileAttributes
  |      size, timestamps, type, fileKey...
  |      (dữ liệu vừa đọc, không có setter)
  |
  +-- Files.getFileAttributeView(
         path, BasicFileAttributeView.class
      )
             |
             v
       BasicFileAttributeView
         + readAttributes()
         |      |
         |      v
         |  BasicFileAttributes mới
         |
         + setTimes(modified, accessed, created)
                |
                v
          Yêu cầu cập nhật file system
~~~

`BasicFileAttributes` trả về kết quả quan sát ở **một lần đọc**. Nếu file được ghi thêm một phút sau, object thuộc tính bạn đang giữ không tự làm mới `size()` hoặc `lastModifiedTime()`; cần yêu cầu đọc lại.

`BasicFileAttributeView` đại diện cho API của **một nhóm thuộc tính** gắn với `Path`. View không phải file handle khóa vĩnh viễn một mục. Bạn có thể lấy view trước, rồi khi gọi `readAttributes`, đường dẫn đã thay đổi hoặc không còn tồn tại và thao tác có thể lỗi. Cần phân biệt **lấy được view** với **đọc được thuộc tính ở thời điểm sử dụng**.

Các kiểu chính:

~~~java
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.BasicFileAttributeView;

Path path = Path.of("data.txt");

BasicFileAttributes attrs =
        Files.readAttributes(path, BasicFileAttributes.class);

BasicFileAttributeView view =
        Files.getFileAttributeView(
                path, BasicFileAttributeView.class);

BasicFileAttributes refreshed = view.readAttributes();
~~~

Đây là **mảnh mã**, cần đặt trong phương thức khai báo hoặc xử lý `IOException`, và `data.txt` phải tồn tại/truy cập được để các lệnh đọc thành công. `getFileAttributeView` có thể trả `null` nếu provider không hỗ trợ kiểu view yêu cầu; khi viết API tổng quát, nên kiểm tra trước khi sử dụng.

## 3. `BasicFileAttributes` cung cấp những gì?

Đây là interface trong gói `java.nio.file.attribute`. Nó khai báo các phương thức sau:

| Phương thức | Kiểu trả về | Ý nghĩa |
| --- | --- | --- |
| `lastModifiedTime()` | `FileTime` | Thời điểm sửa đổi cuối được file system báo cáo |
| `lastAccessTime()` | `FileTime` | Thời điểm truy cập cuối được file system báo cáo |
| `creationTime()` | `FileTime` | Thời điểm tạo được file system báo cáo |
| `size()` | `long` | Kích thước được báo cáo, thường theo **byte** đối với file thường |
| `isRegularFile()` | `boolean` | Có phải file thường hay không |
| `isDirectory()` | `boolean` | Có phải thư mục hay không |
| `isSymbolicLink()` | `boolean` | Có phải symbolic link hay không |
| `isOther()` | `boolean` | Một loại mục khác ba loại trên hay không |
| `fileKey()` | `Object` hoặc `null` | Khóa nhận diện file nếu provider cung cấp |

### FileTime và các mốc thời gian

`FileTime` biểu diễn một mốc thời gian, có thể được chuyển sang `Instant` hoặc các đơn vị thời gian phù hợp.

~~~java
FileTime modified = attrs.lastModifiedTime();
System.out.println(modified.toInstant());
~~~

Đây là mảnh mã với `attrs` đã đọc từ một file. Không in một timestamp cố định trong bài tập trừ khi bạn đã chủ động đặt giá trị và xác định provider hỗ trợ độ chính xác đó.

Ba timestamp thường được hiểu:

- **last modified**: lần sửa nội dung/metadata theo định nghĩa của hệ thống, thông thường liên quan thay đổi nội dung file.
- **last accessed**: lần truy cập cuối; nhiều file system/mount không cập nhật thời điểm truy cập theo từng lần đọc vì lý do hiệu năng.
- **creation**: thời điểm tạo; một số hệ thống không lưu mốc này như một trường độc lập.

**Hợp đồng Java quan trọng:** nếu hệ thống không hỗ trợ một timestamp, phương thức vẫn có thể trả **giá trị mặc định do triển khai quy định**, chẳng hạn thời điểm sửa đổi cuối hoặc epoch, thay vì báo “không có” bằng `null`. Không được xem mọi `creationTime()` đều chắc chắn phản ánh ngày sinh thật của file.

### `size()` đo byte, không đo ký tự Java

Với file thường, `size()` trả kích thước theo byte được file system báo cáo, **không phải**:

- `String.length()` (đơn vị UTF-16 `char`),
- số ký tự hiển thị,
- số dòng,
- số Unicode code point.

Ví dụ chuỗi `"Xin chào"` khi ghi bằng UTF-8 có thể có số byte lớn hơn số ký tự hiển thị. Với file nén/sparse file, số byte được báo cáo có thể không trùng dung lượng vật lý thực chiếm. Với thư mục hoặc mục không phải file thường, giá trị kích thước **phụ thuộc implementation** và không có ý nghĩa “tổng dung lượng mọi file con”.

### Bốn phép phân loại loại file

~~~java
if (attrs.isRegularFile()) {
    // File thường, có nội dung dạng byte.
} else if (attrs.isDirectory()) {
    // Directory.
} else if (attrs.isSymbolicLink()) {
    // Chính symbolic link nếu đọc với NOFOLLOW_LINKS.
} else if (attrs.isOther()) {
    // Loại khác, ví dụ đối tượng đặc biệt do provider xác định.
}
~~~

Mảnh mã trên dùng `attrs` đã khai báo. Thông tin phân loại được xác định **tại thời điểm đọc**, không cấp quyền truy cập hay khóa file. Khi đọc thuộc tính mặc định (theo link), `isSymbolicLink()` có thể false ngay cả khi đường dẫn đầu vào là symbolic link, vì bạn đang nhìn thuộc tính **đích của link**.

### `fileKey()` là khóa nhận diện, không phải đường dẫn

`fileKey()` có thể trả một object dùng để nhận diện file trong phạm vi provider, ví dụ tổ hợp device ID/inode trên một số hệ thống Unix. Nhưng:

- Provider được phép trả `null` khi không có file key.
- Không coi `fileKey().toString()` là format chuẩn hay ID ổn định để lưu lâu dài.
- Tính duy nhất chỉ được bảo đảm trong điều kiện file system và file giữ nguyên; sau khi xóa có thể tái sử dụng ID.
- Không so sánh tùy tiện các file key từ hai providers khác nhau.
- Nếu cần xác định **hai Path có đang chỉ tới cùng file**, dùng `Files.isSameFile(path1, path2)` theo hợp đồng riêng, và vẫn tính tới khả năng file bị thay đổi ngay sau đó.

## 4. Chương trình Java 17+ hoàn chỉnh: đọc thuộc tính file thường

Tạo file **`BasicAttributesDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.BasicFileAttributeView;

public class BasicAttributesDemo {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("basic-attrs-");
        Path file = directory.resolve("hello.txt");

        try {
            Files.writeString(file, "Hello!", StandardCharsets.UTF_8);

            BasicFileAttributes attrs =
                    Files.readAttributes(
                            file, BasicFileAttributes.class);

            BasicFileAttributeView view =
                    Files.getFileAttributeView(
                            file, BasicFileAttributeView.class);

            if (view == null) {
                throw new IOException("Basic attribute view unavailable");
            }

            BasicFileAttributes viaView = view.readAttributes();

            System.out.println("view=" + view.name());
            System.out.println("regular=" + attrs.isRegularFile());
            System.out.println("directory=" + attrs.isDirectory());
            System.out.println("symbolicLink=" + attrs.isSymbolicLink());
            System.out.println("other=" + attrs.isOther());
            System.out.println("bytes=" + attrs.size());
            System.out.println("viaViewBytes=" + viaView.size());
            System.out.println("hasModifiedTime=" +
                    (attrs.lastModifiedTime() != null));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac BasicAttributesDemo.java
java BasicAttributesDemo
~~~

**Stdout dự kiến trên file system mặc định thông thường, không có sửa file đồng thời:**

~~~text
view=basic
regular=true
directory=false
symbolicLink=false
other=false
bytes=6
viaViewBytes=6
hasModifiedTime=true
~~~

Giải thích:

1. `Files.createTempDirectory` tạo thư mục thật. Đường dẫn tới file `hello.txt` ban đầu chưa có mục đích.
2. `Files.writeString` ghi 6 byte ASCII của `Hello!` dưới UTF-8.
3. `Files.readAttributes` lấy một bộ thuộc tính theo kiểu `BasicFileAttributes`.
4. `Files.getFileAttributeView` trả view thuộc tên `"basic"`; `view.readAttributes()` đọc một bộ metadata tương ứng ở **lần gọi khác**.
5. `size() == 6` đúng vì chuỗi `Hello!` gồm sáu byte UTF-8.
6. Giá trị timestamp cụ thể không được in, vì không ổn định giữa môi trường và thời điểm chạy.
7. `finally` dọn tài nguyên tạm. Nếu cleanup thất bại thì `IOException` vẫn có thể được truyền lên.

**Không suy luận rằng hai object `attrs` và `viaView` là cùng một instance hay một snapshot nguyên tử chung.** Chúng được lấy qua hai lời gọi đọc riêng; nội dung có thể khác nhau nếu file bị tác nhân khác thay đổi ở giữa.

## 5. `BasicFileAttributeView`: cửa sổ để đọc và cập nhật timestamp

Giao diện này mở rộng `FileAttributeView` và cung cấp ba khả năng chính:

~~~java
String name();
BasicFileAttributes readAttributes() throws IOException;
void setTimes(
        FileTime lastModifiedTime,
        FileTime lastAccessTime,
        FileTime createTime
) throws IOException;
~~~

Tên của view là **`"basic"`**. `readAttributes()` lấy các thuộc tính cơ bản như một **bulk operation** (một lần gọi API). Tuy nhiên, tài liệu **không bảo đảm mọi thuộc tính được lấy nguyên tử so với các thao tác file system khác**; điều đó phụ thuộc implementation.

`setTimes(...)` yêu cầu thay đổi một hoặc nhiều timestamp. Nó **không** dùng để cập nhật size, file key, loại file, tên, ACL hay permission. Các phần đó hoặc là thông tin chỉ đọc, hoặc cần các API khác.

### Cú pháp và quy tắc tham số `null`

~~~java
view.setTimes(modifiedTime, null, null);
~~~

Nghĩa là chỉ yêu cầu cập nhật **last modified time**, còn last access và creation time được chỉ định `null` nên **không yêu cầu thay đổi**.

~~~java
view.setTimes(null, accessedTime, null);
~~~

Chỉ yêu cầu thay đổi last access time.

~~~java
view.setTimes(null, null, createdTime);
~~~

Chỉ yêu cầu thay đổi creation time, **nếu provider/file store hỗ trợ**.

~~~java
view.setTimes(null, null, null);
~~~

Không thay đổi timestamp nào theo hợp đồng.

**Cẩn trọng:** `null` ở đây có nghĩa “không thay đổi thuộc tính tương ứng”, **không phải** “đặt thời gian thành null/1970”. Java có thể phải đọc các giá trị cũ khi chỉ thay một số trường rồi ghi lại; vì vậy cập nhật không được bảo đảm atomic và đôi lúc còn mất độ chính xác của timestamp khác.

### Độ chính xác và giá trị không được hỗ trợ

`FileTime` có thể đại diện một mốc thời gian rất chính xác, nhưng file store có thể chỉ lưu timestamp ở độ phân giải giây, mili giây hoặc cấp khác. Khi cập nhật:

- Java chuyển giá trị về mốc/độ chính xác mà file store hỗ trợ.
- Chuyển từ độ chính xác cao xuống thấp có thể làm tròn hoặc mất phần lẻ.
- Với timestamp không được hỗ trợ hoặc nằm ngoài khoảng hợp lệ, hành vi **không được định nghĩa đầy đủ**: thao tác có thể báo `IOException` hoặc không.
- Vì vậy, **đọc lại để kiểm chứng** nếu timestamp là điều kiện nghiệp vụ quan trọng; không giả định `setTimes` trả về bình thường luôn tạo chính xác từng nano giây.

## 6. Chương trình Java 17+ hoàn chỉnh: cập nhật thời gian sửa đổi

Tạo file **`BasicTimesDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

public class BasicTimesDemo {
    public static void main(String[] args) throws IOException {
        Path folder = Files.createTempDirectory("basic-times-");
        Path file = folder.resolve("data.txt");

        try {
            Files.writeString(file, "data", StandardCharsets.UTF_8);

            BasicFileAttributeView view =
                    Files.getFileAttributeView(
                            file, BasicFileAttributeView.class);
            if (view == null) {
                throw new IOException("Basic view unavailable");
            }

            FileTime requested =
                    FileTime.from(Instant.parse("2020-01-02T03:04:05Z"));

            // Chỉ cập nhật last-modified-time.
            view.setTimes(requested, null, null);

            BasicFileAttributes after = view.readAttributes();

            System.out.println("view=" + view.name());
            System.out.println("regular=" + after.isRegularFile());
            System.out.println("requestedYear="
                    + requested.toInstant().atZone(
                            java.time.ZoneOffset.UTC).getYear());
            System.out.println("actualTimestamp="
                    + after.lastModifiedTime());
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(folder);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac BasicTimesDemo.java
java BasicTimesDemo
~~~

**Ví dụ stdout trên file store hỗ trợ thời gian được yêu cầu:**

~~~text
view=basic
regular=true
requestedYear=2020
actualTimestamp=2020-01-02T03:04:05Z
~~~

Dòng `actualTimestamp` **không phải kết quả bắt buộc trên mọi nền tảng**: provider có thể thay độ chính xác, từ chối cập nhật hoặc báo một timestamp implementation-specific. Đó là lý do chương trình đọc lại thuộc tính sau khi `setTimes`, thay vì tự in giá trị đã yêu cầu như bằng chứng thành công.

Nếu ứng dụng cần độ chính xác tới đơn vị giây, nên so sánh theo chính sách ứng dụng sau readback:

~~~java
boolean sameSecond =
        after.lastModifiedTime().toInstant().getEpochSecond()
        == requested.toInstant().getEpochSecond();
~~~

Đây là mảnh mã sử dụng `after` và `requested` từ ví dụ trên. Nó so sánh tới **giây**, không đòi nano giây giống nhau, nhưng vẫn có thể trả `false` nếu file store không lưu được giá trị hoặc thao tác thực sự không đạt yêu cầu. Không bỏ qua `false` khi timestamp là ràng buộc nghiệp vụ.

## 7. Đọc một lần bằng `Files.readAttributes` hay giữ `BasicFileAttributeView`?

Có hai cách phổ biến.

### Cách A — Đọc trực tiếp

~~~java
BasicFileAttributes attrs =
        Files.readAttributes(path, BasicFileAttributes.class);
~~~

Ưu điểm:

- Ngắn gọn, rõ kiểu tĩnh.
- Phù hợp khi chỉ cần **đọc một snapshot metadata**.
- Mọi trường có thể được dùng nhiều lần từ object kết quả mà không phải đọc lại file system.
- Có thể truyền `LinkOption.NOFOLLOW_LINKS` để đọc chính symbolic link.

### Cách B — Lấy view và thao tác

~~~java
BasicFileAttributeView view =
        Files.getFileAttributeView(
                path, BasicFileAttributeView.class);

BasicFileAttributes before = view.readAttributes();
view.setTimes(newTime, null, null);
BasicFileAttributes after = view.readAttributes();
~~~

Đây là mảnh mã; `path` kiểu `Path`, `newTime` kiểu `FileTime` và cần xử lý `IOException`. Nếu view có thể là `null`, phải kiểm tra trước khi gọi các phương thức.

Ưu điểm:

- Gom việc đọc và yêu cầu cập nhật timestamp qua **cùng loại attribute view**.
- Tiện khi cần đọc–đổi–đọc lại nhiều lần trong một phương thức.
- Cho phép API làm việc với các attribute view khác nhau khi thiết kế hệ thống cần mở rộng.

Cả hai đều thao tác trên file system theo thời điểm gọi; **view không đảm bảo path còn tồn tại, không giữ khóa và không tự đồng bộ hóa người sửa khác**.

| Nhu cầu | Gợi ý |
| --- | --- |
| Xem nhanh size, type, thời gian sửa | `Files.readAttributes(path, BasicFileAttributes.class)` |
| Lấy thuộc tính với kiểm soát symlink | `Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW_LINKS)` |
| Đọc và cập nhật timestamp qua cùng abstraction | `Files.getFileAttributeView(...).setTimes(...)` rồi `readAttributes()` |
| Chỉ cần last-modified-time | `Files.getLastModifiedTime` / `Files.setLastModifiedTime` |
| Đọc thuộc tính theo tên chuỗi động | `Files.getAttribute` / `Files.readAttributes(path, "basic:*")` |
| Chỉnh permission POSIX hay ACL | Dùng attribute views/chức năng chuyên biệt tương ứng, không phải basic view |

## 8. Typed attributes so với attribute-name strings

Java còn có API thuộc tính động dựa trên tên chuỗi, ví dụ:

~~~java
Map<String, Object> values =
        Files.readAttributes(path, "basic:*");
~~~

Nó trả về `Map<String, Object>` thay vì một `BasicFileAttributes` có phương thức được kiểm tra kiểu lúc biên dịch.

Các tên của nhóm `basic` bao gồm:

- `lastModifiedTime` — `FileTime`
- `lastAccessTime` — `FileTime`
- `creationTime` — `FileTime`
- `size` — `Long`
- `isRegularFile` — `Boolean`
- `isDirectory` — `Boolean`
- `isSymbolicLink` — `Boolean`
- `isOther` — `Boolean`
- `fileKey` — `Object` (có thể `null`)

Khi muốn một thuộc tính:

~~~java
FileTime modified =
        (FileTime) Files.getAttribute(
                path, "basic:lastModifiedTime");
~~~

Khi muốn yêu cầu thay đổi:

~~~java
Files.setAttribute(
        path,
        "basic:lastModifiedTime",
        requestedTime
);
~~~

Đây đều là **mảnh mã** cần import `java.nio.file.Files`, `Path`, `Map`, `FileTime`, khai báo biến `path`/`requestedTime` và xử lý `IOException`.

**Quan trọng:** không được suy ra mọi key trong `basic:*` đều có thể `setAttribute`. Basic view chỉ cung cấp hành vi setter cho **ba trường thời gian** theo `setTimes`. Các thuộc tính như `size`, `isRegularFile`, `fileKey` không có setter tương đương trong `BasicFileAttributeView`.

Với code ứng dụng biết rõ các trường cần đọc, bản typed thường dễ bảo trì hơn vì tránh cast `Object` thủ công và lỗi đánh máy tên thuộc tính.

## 9. Symbolic link và `LinkOption.NOFOLLOW_LINKS`

Đây là một trong những lý do quan trọng nhất phải hiểu **đọc thuộc tính của đối tượng nào**.

Giả sử:

~~~text
data.txt                 (file thường)
shortcut -> data.txt     (symbolic link)
~~~

Khi đọc mặc định:

~~~java
BasicFileAttributes followed =
        Files.readAttributes(
                shortcut, BasicFileAttributes.class);
~~~

`Files.readAttributes` mặc định **theo symbolic link**. Nếu target tồn tại và là file thường, `followed.isRegularFile()` thường là `true`; `followed.isSymbolicLink()` thường là `false` vì attributes mô tả **đích sau khi theo link**.

Để đọc **chính link entry**:

~~~java
BasicFileAttributes linkAttrs =
        Files.readAttributes(
                shortcut,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);

System.out.println(linkAttrs.isSymbolicLink());
~~~

Đây là mảnh mã cần `shortcut` kiểu `Path`, các import thích hợp và xử lý `IOException`. Nếu link thực sự tồn tại, thuộc tính của chính link có `isSymbolicLink() == true`.

Tương tự với attribute view:

~~~java
BasicFileAttributeView linkView =
        Files.getFileAttributeView(
                shortcut,
                BasicFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS
        );
~~~

`linkView.readAttributes()` nhắm tới chính link theo policy của view. `setTimes` khi dùng view này cũng có thể liên quan cập nhật timestamp của **link entry**, và hỗ trợ thao tác trên link phụ thuộc file system/provider.

**Không gọi code tạo symlink mặc định trong mọi test CI:** trên một số hệ điều hành, quyền người dùng và provider có thể không cho tạo symlink. Khi cần test thật, thiết lập fixture theo khả năng môi trường hoặc skip khi không hỗ trợ.

### Dangling symbolic link

Nếu `shortcut` trỏ đến mục không tồn tại:

- Đọc thuộc tính **theo link** có thể thất bại với `NoSuchFileException`.
- Đọc thuộc tính với `NOFOLLOW_LINKS` vẫn có thể mô tả **chính link** nếu entry link còn tồn tại.

Do đó `Files.exists(shortcut)==false` mặc định không tự chứng minh link entry không có ở đường dẫn. Nếu cần kiểm tra entry link, chọn `NOFOLLOW_LINKS` và xử lý điều kiện unknown/truy cập bị từ chối.

## 10. File chưa tồn tại và view có thể được tạo trước khi file xuất hiện

`Path` là biểu diễn vị trí, không phải một file đã mở; việc lấy attribute view cũng không nên bị hiểu là đã đọc được metadata thật.

Tạo file **`MissingAttributesDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;

public class MissingAttributesDemo {
    public static void main(String[] args) throws IOException {
        Path folder = Files.createTempDirectory("missing-attrs-");
        Path missing = folder.resolve("absent.txt");

        try {
            BasicFileAttributeView view =
                    Files.getFileAttributeView(
                            missing, BasicFileAttributeView.class);

            System.out.println("viewAvailable=" + (view != null));

            if (view == null) {
                return;
            }

            try {
                view.readAttributes();
                System.out.println("unexpected success");
            } catch (NoSuchFileException exception) {
                System.out.println("read=NoSuchFileException");
            }
        } finally {
            Files.deleteIfExists(folder);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac MissingAttributesDemo.java
java MissingAttributesDemo
~~~

**Stdout dự kiến trên default file system thông thường:**

~~~text
viewAvailable=true
read=NoSuchFileException
~~~

Bài học:

1. `getFileAttributeView` có thể cấp view cho **một Path chưa tồn tại**.
2. `view != null` chỉ chứng minh kiểu view được provider hỗ trợ; **không chứng minh file hiện tồn tại hoặc có quyền đọc**.
3. `readAttributes()` mới cố lấy metadata thật, nên đường dẫn bị thiếu thường dẫn tới `NoSuchFileException`.
4. Đừng dùng view như thay thế cho `Files.exists`; kể cả nếu một phép kiểm tra trước thành công, file vẫn có thể bị thay đổi ngay sau đó.

Với file system provider đặc biệt, loại ngoại lệ cụ thể có thể khác; ví dụ dùng `NoSuchFileException` để minh họa default provider, còn code sản xuất vẫn phải xử lý `IOException`.

## 11. Metadata có phải “snapshot nguyên tử” của file không?

**Không được mặc định là có.**

`BasicFileAttributeView.readAttributes()` đọc tập thuộc tính bằng bulk operation. Tuy vậy, theo tài liệu Java, **liệu tất cả các trường có được đọc nguyên tử so với thao tác file system khác hay không là implementation-specific**.

Một timeline có thể như sau:

~~~text
Thread A                         Thread B
--------                         --------
readAttributes() bắt đầu
                                 ghi thêm dữ liệu vào file
lấy attrs.size()
                                 cập nhật timestamp metadata
nhận attrs
...
sử dụng kết quả cũ
~~~

Chương trình A có thể thấy metadata của một thời điểm, và khi mở file để đọc nội dung thì kích thước/nội dung đã khác. Một `BasicFileAttributes` đã nhận vẫn giữ giá trị cũ, không theo dõi file realtime.

Ví dụ tình huống:

~~~java
BasicFileAttributes before =
        Files.readAttributes(path, BasicFileAttributes.class);

// Người dùng khác/tiến trình khác có thể sửa file ở đây.

byte[] bytes = Files.readAllBytes(path);
boolean sizeMatched = before.size() == bytes.length;
~~~

Đây là mảnh mã đặt trong phương thức xử lý `IOException` với biến `path` đã khai báo. `sizeMatched` có thể false do file thay đổi, và ngay cả nếu true thì **không bảo đảm byte là cùng một phiên bản nội dung** (file có thể được sửa mà kích thước không đổi).

Nếu cần một snapshot nhất quán thực sự, hãy xác định cơ chế của hệ thống: file bất biến/versioned, file locks trong phạm vi hỗ trợ, copy tới staging, transactional storage hoặc protocol phối hợp các bên ghi/đọc. Một object `BasicFileAttributes` không tự tạo ra điều đó.

## 12. `setTimes` không phải giao dịch nguyên tử và không thay quyền

Một câu lệnh:

~~~java
view.setTimes(newModified, null, null);
~~~

có thể cần đọc timestamp cũ rồi ghi metadata mới, tùy provider. Nó không hứa:

- Cập nhật đồng thời cả ba timestamp một cách nguyên tử.
- Giữ nguyên độ chính xác của các timestamp khác trên mọi file store.
- Chống tác nhân khác sửa file ngay sau khi set.
- Giữ một file identity cố định dưới cùng Path.
- Chỉnh `size`, `fileKey`, owner, ACL hoặc POSIX permissions.

Nếu cần thay permission, hãy dùng các API thích hợp, ví dụ:

~~~java
// Chỉ minh họa API; không chạy trên mọi file system.
Files.setPosixFilePermissions(path, permissions);
~~~

Đây là mảnh mã với `permissions` kiểu `Set<PosixFilePermission>`, phụ thuộc hỗ trợ của provider và quyền thực thi. Nó không phải phương thức của `BasicFileAttributeView`.

Với Windows/DOS-style attributes như `hidden`, `readOnly`, `system`, `archive`, cần xem `DosFileAttributeView`. Với POSIX owner/group/permissions, xem `PosixFileAttributeView`. Các view chuyên biệt có thể không được hỗ trợ ở một provider, nên `getFileAttributeView` có thể trả `null` hoặc API tương ứng có thể ném lỗi.

## 13. Phân biệt Basic, DOS và POSIX

| View | Nhóm thuộc tính chính | Hành vi bổ sung |
| --- | --- | --- |
| `BasicFileAttributeView` | Type, size, timestamps, fileKey | `readAttributes`, `setTimes` |
| `DosFileAttributeView` | Basic + hidden, read-only, archive, system | Có setter tương ứng khi hỗ trợ |
| `PosixFileAttributeView` | Basic + owner, group, permission bits POSIX | Có API đổi owner/group/permissions khi hỗ trợ |
| `AclFileAttributeView` | ACL theo mô hình hỗ trợ của provider | Đọc/ghi ACL khi hỗ trợ |
| `UserDefinedFileAttributeView` | Thuộc tính mở rộng do người dùng định nghĩa | Đọc/ghi tên/value cụ thể khi hỗ trợ |

Không phải mọi hệ thống tập tin cung cấp mọi loại view. Đừng dùng `BasicFileAttributes` để kết luận file “có quyền đọc hay không”: nó **không định nghĩa method `canRead()`**. Muốn đánh giá khả năng truy cập, dùng các API tương ứng như `Files.isReadable` (chỉ kiểm tra tại một thời điểm), hoặc thực hiện thao tác mở/đọc và xử lý lỗi thực tế.

Khi code đa nền tảng:

~~~java
if (Files.getFileAttributeView(
        path, java.nio.file.attribute.PosixFileAttributeView.class) != null) {
    // Provider cung cấp POSIX attribute view.
}
~~~

Đây là mảnh mã, không đảm bảo thao tác đổi quyền sẽ thành công — còn phụ thuộc quyền file thực tế và việc file vẫn tồn tại khi thao tác.

## 14. Những lỗi I/O và ngoại lệ cần xử lý

Các thao tác đọc thuộc tính có thể thất bại vì:

- File hoặc thư mục đã bị xóa/chưa từng tồn tại.
- Symbolic link bị hỏng hoặc không thể được giải quyết khi theo link.
- Quyền đọc metadata bị từ chối.
- File system provider/thiết bị mạng gặp lỗi.
- File bị thay thế trong lúc truy vấn.
- Provider không hỗ trợ view hoặc attribute yêu cầu.
- Timestamp mới nằm ngoài phạm vi/độ chính xác hỗ trợ.

Ví dụ code quản lý lỗi rõ ràng:

~~~java
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public final class AttributeReader {
    private AttributeReader() {}

    public static BasicFileAttributes requireMetadata(Path path)
            throws IOException {
        try {
            return Files.readAttributes(
                    path, BasicFileAttributes.class);
        } catch (NoSuchFileException exception) {
            throw new IOException("File not found: " + path,
                    exception);
        } catch (AccessDeniedException exception) {
            throw new IOException("Cannot inspect metadata: " + path,
                    exception);
        }
    }
}
~~~

Đây là **lớp tiện ích có thể biên dịch** (không có `main`). Nó giữ nguyên nguyên nhân bên trong `IOException(message, cause)` để caller/log có thêm thông tin. Provider không bắt buộc luôn dùng đúng lớp con cụ thể cho mọi tình huống, nên các `IOException` khác vẫn được lan truyền.

Với API đọc map động, tên thuộc tính không hỗ trợ có thể ném `IllegalArgumentException` hoặc `UnsupportedOperationException` theo hợp đồng từng phương thức. Không bắt mọi `RuntimeException` rồi gộp thành “file không tồn tại”.

Một số giá trị `false` từ `Files.isRegularFile(path)` / `Files.exists(path)` có thể có nghĩa không xác định được trạng thái; khi nghiệp vụ cần lý do, `Files.readAttributes` cung cấp đường báo lỗi rõ hơn.

### Bảo mật và quyền truy cập

Metadata có thể chứa đường dẫn, ID file hay timestamp nhạy cảm trong một số bối cảnh. Không ghi thông tin người dùng/path tuyệt đối vào log công khai theo thói quen. Nếu đường dẫn đến từ nguồn không tin cậy, kiểm soát path traversal/symlink và quyền thao tác thực tế. `readAttributes()` thành công không trao quyền truy cập file hay bảo vệ khỏi TOCTOU.

## 15. Khi nào dùng `Files.size` / `getLastModifiedTime` thay vì đọc nhiều trường?

Nếu chỉ cần **một trường**, API tiện ích có thể rõ nghĩa hơn:

~~~java
long bytes = Files.size(path);
FileTime modified = Files.getLastModifiedTime(path);
~~~

Nếu cần **nhiều trường** để hiển thị hoặc phân loại file:

~~~java
BasicFileAttributes attrs =
        Files.readAttributes(
                path, BasicFileAttributes.class);

long bytes = attrs.size();
boolean directory = attrs.isDirectory();
FileTime modified = attrs.lastModifiedTime();
~~~

Những lời gọi đầu là hai truy vấn riêng; lời gọi sau lấy bộ thuộc tính một lần. Không được tuyên bố rằng đọc một lần đảm bảo tất cả trường atomic, nhưng khi mục tiêu là đọc nhiều thuộc tính gần thời điểm nhau, bộ attribute typed là giải pháp tiện và có thể giảm số round trip metadata.

Nếu cần đổi timestamp, dùng `Files.setLastModifiedTime` cho trường hợp đơn giản; dùng `BasicFileAttributeView.setTimes` khi muốn đổi các mốc khác hoặc nhiều mốc trong cùng API.

**Đừng lạm dụng `Files.size` để dự đoán chắc số byte sẽ nhận trong lần đọc tiếp theo**: file có thể thay đổi. Khi đọc streaming, số byte thực đọc và EOF từ stream vẫn là sự thật cần xử lý.

## 16. Ứng dụng thực tế: thống kê file và lọc theo loại

Ví dụ ứng dụng liệt kê thư mục và chỉ xử lý file thường, với metadata lấy từ từng path:

~~~java
try (java.nio.file.DirectoryStream<Path> stream =
             Files.newDirectoryStream(directory)) {
    for (Path child : stream) {
        BasicFileAttributes attrs =
                Files.readAttributes(
                        child, BasicFileAttributes.class,
                        java.nio.file.LinkOption.NOFOLLOW_LINKS);
        if (attrs.isRegularFile()) {
            System.out.println(child.getFileName() + ": "
                    + attrs.size() + " bytes");
        }
    }
}
~~~

Đây là **mảnh mã** nằm trong phương thức có các biến/import phù hợp và xử lý `IOException` (lỗi phát sinh trong lúc duyệt `DirectoryStream` có thể được bọc trong `DirectoryIteratorException`). `DirectoryStream` là tài nguyên phải đóng. Đọc với `NOFOLLOW_LINKS` có nghĩa một symlink tới file sẽ **không** được coi là file thường trong chính lần phân loại đó.

Nếu cần quét một cây thư mục lớn, có thể sử dụng `Files.walkFileTree` và tận dụng `BasicFileAttributes` được cung cấp trong callback của `FileVisitor`. Không nhất thiết truy vấn metadata lần hai nếu callback đã có bộ thuộc tính phù hợp.

### Callback trong `walkFileTree`

~~~java
@Override
public FileVisitResult visitFile(
        Path file, BasicFileAttributes attrs) throws IOException {
    if (attrs.isRegularFile()) {
        totalBytes += attrs.size();
    }
    return FileVisitResult.CONTINUE;
}
~~~

Đây là **mảnh mã của một lớp kế thừa `SimpleFileVisitor<Path>`** với biến `totalBytes` khai báo trong lớp và các import `FileVisitResult`, `Path`, `BasicFileAttributes`, `IOException`. Callback nhận snapshot thuộc tính theo quy trình duyệt. Nếu file thay đổi giữa duyệt và xử lý, `attrs.size()` không nhất thiết trùng với số byte về sau được đọc.

## 17. Hiểu nhầm thường gặp

**1. “`BasicFileAttributes` là đối tượng live; file đổi thì `attrs.size()` đổi.”**  
Sai. `attrs` là dữ liệu đã đọc ở một thời điểm; muốn cập nhật phải đọc lại metadata.

**2. “`BasicFileAttributeView` chính là danh sách thuộc tính.”**  
Sai. View là API đọc và cập nhật; `view.readAttributes()` mới trả về bộ dữ liệu `BasicFileAttributes`.

**3. “`getFileAttributeView(...) != null` chứng minh file đang tồn tại.”**  
Sai. Nó chủ yếu chứng minh provider hỗ trợ view kiểu đó; `readAttributes()` mới thực hiện truy cập metadata và có thể lỗi.

**4. “`setTimes(null, null, null)` đặt timestamp về epoch.”**  
Sai. Cả ba `null` nghĩa là không thay đổi trường nào; `null` riêng lẻ là giữ nguyên timestamp tương ứng.

**5. “`setTimes` bảo đảm thay đổi đồng thời cả ba thời gian một cách nguyên tử.”**  
Sai. Nhà cung cấp file system có thể đọc/ghi trung gian và làm mất độ chính xác, nhất là khi chỉ thay một số trường.

**6. “`creationTime()` luôn là ngày tạo thực sự.”**  
Sai. Nếu file system không hỗ trợ creation time, kết quả có thể là giá trị implementation-specific như thời điểm sửa cuối/epoch.

**7. “`attrs.size()` cho biết dung lượng toàn bộ thư mục.”**  
Sai. Size của mục không phải file thường không được đặc tả phổ quát theo cách đó.

**8. “`fileKey()` luôn khác null và có thể lưu như ID vĩnh viễn.”**  
Sai. Có thể null; không được đảm bảo ổn định sau khi file bị xóa/thay thế hoặc chuyển sang file system khác.

**9. “Đọc attributes từ symlink luôn báo `isSymbolicLink()==true`.”**  
Sai. Mặc định `Files.readAttributes` theo symlink; muốn đọc chính link thì dùng `NOFOLLOW_LINKS`.

**10. “Basic view cho phép đổi owner, permission, ACL.”**  
Sai. Basic view chỉ định nghĩa `setTimes` cho timestamp; cần view chuyên biệt khi muốn cập nhật quyền/thuộc tính bảo mật.

**11. “Đọc metadata thành công đồng nghĩa mở file hoặc ghi file tiếp theo sẽ thành công.”**  
Sai. Quyền, file identity và trạng thái có thể thay đổi; cần xử lý lỗi I/O tại thao tác thật.

**12. “Tất cả provider đều báo đúng cùng một subclass `IOException`.”**  
Sai. Java cho phép khác biệt về các exception cụ thể và khả năng attribute view; code phải sẵn sàng xử lý `IOException`.

## 18. Checklist khi áp dụng trong dự án

- [ ] Đã phân biệt data object `BasicFileAttributes` và view `BasicFileAttributeView`?
- [ ] Có chọn `Files.readAttributes(..., BasicFileAttributes.class)` khi muốn đọc nhiều thuộc tính typed?
- [ ] Có đọc lại nếu cần thông tin cập nhật, thay vì giữ mãi một object attrs?
- [ ] Có hiểu `size` là byte count, không phải số ký tự hay dung lượng đệ quy của directory?
- [ ] Có xác định ý nghĩa `creationTime/lastAccessTime` trên file system thực tế, thay vì tin tuyệt đối?
- [ ] Nếu cần symlink metadata, đã dùng `NOFOLLOW_LINKS` phù hợp?
- [ ] Có kiểm tra khả năng hỗ trợ view và xử lý đường dẫn thiếu, lỗi I/O hoặc thiếu quyền?
- [ ] Khi `setTimes`, đã truyền `null` cho các timestamp không muốn đổi?
- [ ] Khi timestamp là ràng buộc nghiệp vụ, đã đọc lại và đánh giá độ chính xác?
- [ ] Có tránh dùng `fileKey` như ID vĩnh viễn hoặc kết luận identity khi file bị thay đổi?
- [ ] Có dùng POSIX/DOS/ACL view phù hợp nếu cần quyền/thuộc tính ngoài basic?
- [ ] Có xử lý TOCTOU nếu file được thay đổi bởi bên khác?
- [ ] Nếu duyệt nhiều mục, đã đóng `DirectoryStream`/`Stream<Path>` và xử lý lỗi phát sinh khi duyệt?
- [ ] Có kiểm tra kết quả trên những file systems/providers thực sự triển khai?

## 19. Câu hỏi tự kiểm tra

**Câu 1.** `BasicFileAttributes` và `BasicFileAttributeView` khác gì?  
**Đáp án:** `BasicFileAttributes` là bộ giá trị metadata đã đọc; `BasicFileAttributeView` là API để gọi đọc lại và yêu cầu cập nhật timestamp.

**Câu 2.** Muốn biết size, loại file và timestamp bằng một truy vấn typed, dùng gì?  
**Đáp án:** `Files.readAttributes(path, BasicFileAttributes.class)`.

**Câu 3.** `view.setTimes(null, accessTime, null)` thay đổi trường nào?  
**Đáp án:** Chỉ yêu cầu đổi last-access-time; hai trường còn lại được giữ theo hợp đồng.

**Câu 4.** Một `Path` chưa có file thì `getFileAttributeView` có thể trả view không?  
**Đáp án:** Có thể. Lấy view không chứng minh file tồn tại; `view.readAttributes()` có thể ném `NoSuchFileException`.

**Câu 5.** `attrs.fileKey() == null` có nghĩa file đã bị hỏng không?  
**Đáp án:** Không. Provider có thể không cung cấp file key.

**Câu 6.** `attrs.size()` đối với directory có luôn bằng tổng số byte file con không?  
**Đáp án:** Không. Size của mục không phải file thường phụ thuộc implementation.

**Câu 7.** Muốn xem metadata của chính symbolic link chứ không phải target, cần gì?  
**Đáp án:** Đọc với `LinkOption.NOFOLLOW_LINKS`.

**Câu 8.** `setTimes` hoàn tất mà đọc lại không trùng từng nanosecond thì có chắc Java sai không?  
**Đáp án:** Không. File store có thể có độ phân giải thấp hơn, hoặc một timestamp không được hỗ trợ. Phải đánh giá theo chính sách ứng dụng/file system.

**Câu 9.** Có thể dùng basic view để chỉnh quyền POSIX không?  
**Đáp án:** Không. Dùng API thuộc tính POSIX nếu provider hỗ trợ.

**Câu 10.** `readAttributes` có bảo đảm một snapshot nguyên tử khi file bị sửa đồng thời không?  
**Đáp án:** Không. Java nêu rõ tính nguyên tử của bulk metadata read phụ thuộc implementation.

**Ghi nhớ:** **`BasicFileAttributes` cho bạn biết những gì vừa được quan sát; `BasicFileAttributeView` cho bạn cách đọc/đề nghị cập nhật những thuộc tính đó**. Đặc biệt, `readAttributes()` không khóa file và `setTimes()` không hứa atomicity hay độ chính xác tuyệt đối. Hãy chú ý symbolic link, quyền, provider và xử lý `IOException` ở từng thao tác thật.

## Tài liệu Java SE 17

- [BasicFileAttributes — Oracle Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/BasicFileAttributes.html)
- [BasicFileAttributeView — Oracle Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/BasicFileAttributeView.html)
- [Files.readAttributes / getFileAttributeView — Oracle Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [FileTime — Oracle Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/FileTime.html)
- [LinkOption — Oracle Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/LinkOption.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-attributes-and-permissions-002">Thời gian tạo, sửa, truy cập và FileTime</a>

<details>
<summary>Xem chi tiết</summary>

# Thời gian tạo, sửa, truy cập và `FileTime` trong Java NIO.2

## Mục tiêu bài học

Một file không chỉ có tên, kích thước và nội dung. Filesystem còn có thể lưu các **mốc thời gian** (*timestamps*), thường gồm **thời gian tạo** (*creation time*), **thời gian sửa nội dung gần nhất** (*last-modified time*) và **thời gian truy cập gần nhất** (*last-access time*). Trong Java NIO.2, `BasicFileAttributes` cho phép đọc ba thuộc tính này; `FileTime` là kiểu biểu diễn thời điểm dùng trong API filesystem.

Những mốc thời gian đó **không phải hợp đồng tuyệt đối giống nhau trên mọi hệ điều hành, thiết bị lưu trữ hay filesystem**. Một số timestamp không được hỗ trợ thật sự; giá trị có thể là giá trị thay thế, làm tròn theo độ phân giải, bị trì hoãn cập nhật hoặc thay đổi bởi công cụ sao chép/đồng bộ.

Sau bài này, bạn có thể:

- Phân biệt `creationTime()`, `lastModifiedTime()`, `lastAccessTime()` và tránh nhầm **Unix ctime** với creation time.
- Đọc thuộc tính qua `Files.readAttributes(..., BasicFileAttributes.class)`, `Files.getLastModifiedTime()`, hoặc `Files.getAttribute()`.
- Sử dụng `FileTime.fromMillis(...)`, `FileTime.from(Instant)`, `toMillis()`, `toInstant()`, `to(TimeUnit)`, `compareTo()`.
- Hiểu `Instant` là thời điểm độc lập múi giờ; múi giờ chỉ xuất hiện khi **hiển thị**, ví dụ `Asia/Ho_Chi_Minh`.
- Thay đổi `lastModifiedTime` bằng `Files.setLastModifiedTime()` và tìm hiểu `BasicFileAttributeView.setTimes()` cho các timestamp khác.
- Xử lý symbolic link với `LinkOption.NOFOLLOW_LINKS`, lỗi `IOException`, vấn đề độ phân giải, copy metadata và **TOCTOU**.
- Chạy các ví dụ Java 17+ và diễn giải đúng những kết quả **ổn định** so với kết quả **phụ thuộc filesystem**.

## 1. Ba loại thời gian chính

Giả sử một file được tạo, sau đó được ghi lại và được đọc:

```text
09:00   Tạo file "report.txt"
09:15   Sửa nội dung "report.txt"
09:20   Mở và đọc nội dung
            │
            ├─ creationTime       → thời điểm tạo (nếu filesystem hỗ trợ)
            ├─ lastModifiedTime   → thời điểm sửa nội dung theo metadata
            └─ lastAccessTime     → thời điểm truy cập theo metadata
```

Đây là **minh họa khái niệm**, không phải cam kết rằng mọi hệ thống sẽ lưu đúng 09:00, 09:15, 09:20. Trên một số filesystem, `lastAccessTime` không cập nhật theo mỗi lần đọc; `creationTime` có thể thiếu hoặc được mô phỏng.

| Timestamp | Java NIO.2 | Câu hỏi mà nó *thường* trả lời | Lưu ý |
|---|---|---|---|
| **Creation time** | `BasicFileAttributes.creationTime()` | Entry/file được tạo khi nào? | Không phải mọi filesystem đều lưu “birth time”; có thể trả giá trị mặc định của provider |
| **Last modified time** | `BasicFileAttributes.lastModifiedTime()` | Nội dung file được sửa gần đây nhất khi nào? | Có thể bị đặt lại bằng API, copy hoặc restore; không phải checksum nội dung |
| **Last access time** | `BasicFileAttributes.lastAccessTime()` | File được truy cập gần đây nhất khi nào? | Có thể tắt hoặc giảm tần suất cập nhật để tối ưu I/O |

### Creation time có luôn đáng tin?

Không. `creationTime()` là thời gian tạo **theo khả năng filesystem/provider cung cấp**. Nếu không hỗ trợ mốc tạo, Java cho phép provider trả một **giá trị mặc định tùy triển khai**, ví dụ dùng last-modified time. Giá trị không `null` không chứng minh đó là timestamp tạo gốc của file.

Ngoài ra:

- **Sao chép** một file có thể tạo entry mới nhưng **giữ metadata nguồn** nếu ứng dụng dùng cơ chế bảo toàn thuộc tính.
- **Move/rename** trên cùng filesystem thường không tương đương tạo mới nội dung, nhưng ngữ nghĩa timestamp phụ thuộc filesystem.
- File được **restore** từ backup hoặc archive có thể mang timestamp được phục hồi, không phải thời điểm thực sự xuất hiện ở đích.
- Có thể tạo file rồi đặt thủ công timestamp trong phạm vi filesystem cho phép.

Đừng dùng `creationTime()` như ID, bằng chứng pháp lý duy nhất, nguồn sự thật chống gian lận hoặc khóa giao dịch.

### Last modified time có ghi mọi thay đổi metadata?

Không nên giả định. Trong mô hình POSIX, một thay đổi nội dung thường cập nhật `mtime`; thay đổi metadata như mode/owner thường liên quan `ctime`. **Unix ctime thông thường là *status-change time*, không phải creation time**. Đây là một nhầm lẫn đặc biệt dễ gặp khi thấy chữ “c”.

`BasicFileAttributes` chuẩn có `creationTime`, `lastModifiedTime`, `lastAccessTime`; nó không định nghĩa một phương thức `ctime()` chuẩn tương đương POSIX status-change time. Nếu cần metadata đặc thù, phải kiểm tra khả năng của provider, OS hoặc API native tương ứng.

### Last access time có thay đổi sau mỗi `read()`?

**Không được bảo đảm.** Nhiều hệ thống có cơ chế tối ưu như không ghi access time hoặc chỉ cập nhật trong một số điều kiện (*noatime*, *relatime* trên một số môi trường Unix). Một số filesystem, thiết bị, cơ chế cache hoặc phương thức đọc cũng có hành vi riêng.

Vì vậy, không xây dựng thuật toán “file nào có atime cũ nhất thì chắc chắn chưa từng được dùng gần đây” nếu nghiệp vụ cần độ chính xác cao. Đối với sản phẩm theo dõi lượt truy cập, nên ghi sự kiện truy cập do **ứng dụng** quản lý, thay vì coi atime là log nghiệp vụ tuyệt đối.

## 2. Các API đọc timestamp trong Java 17

Import thường gặp:

```java
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
```

### 2.1. Đọc cả ba thời gian trong một lần

```java
BasicFileAttributes attrs = Files.readAttributes(
    path,
    BasicFileAttributes.class
);

FileTime created = attrs.creationTime();
FileTime modified = attrs.lastModifiedTime();
FileTime accessed = attrs.lastAccessTime();
```

Đây là **đoạn minh họa**, `path` là một `Path` hợp lệ và caller phải xử lý `IOException`.

`BasicFileAttributes` còn có các phương thức như `size()`, `isRegularFile()`, `isDirectory()`, `isSymbolicLink()`, `fileKey()\). Nếu vừa cần loại file vừa cần nhiều timestamp, lấy chung `BasicFileAttributes` giúp biểu đạt rõ “đọc nhóm metadata” và thường tránh ba lời gọi metadata riêng lẻ.

**Không nên diễn giải “đọc cùng `BasicFileAttributes`” là một giao dịch filesystem hoàn toàn nguyên tử.** Provider có thể không thu thập mọi thuộc tính bằng một thao tác bất khả phân chia, và file vẫn có thể bị thay đổi đồng thời.

### 2.2. Đọc riêng last-modified time

```java
FileTime modified = Files.getLastModifiedTime(path);
```

Đây là **đoạn minh họa**, cần xử lý `IOException`. API này tiện nếu bài toán chỉ cần một timestamp. Mặc định nó **theo symbolic link**; có thể truyền `LinkOption.NOFOLLOW_LINKS` khi muốn đọc metadata của chính link:

```java
FileTime linkModified = Files.getLastModifiedTime(
    path, LinkOption.NOFOLLOW_LINKS
);
```

`NOFOLLOW_LINKS` có ý nghĩa khi `path` trỏ tới symbolic link. Nó không tự bảo đảm các filesystem hỗ trợ mọi thuộc tính của link hoặc loại bỏ race giữa các thao tác.

### 2.3. Đọc qua tên thuộc tính dạng chuỗi

Nếu API yêu cầu tên thuộc tính:

```java
FileTime modified = (FileTime) Files.getAttribute(
    path, "basic:lastModifiedTime"
);
```

Đây là **đoạn minh họa**, cần xử lý `IOException`. Provider có thể hỗ trợ attribute view `basic` và các view bổ sung. Khi biết trước mình cần ba mốc thời gian chuẩn, API **strongly typed** `BasicFileAttributes` thường rõ hơn và tránh cast nhầm.

Có thể đọc nhiều thuộc tính theo cú pháp:

```java
Map<String, Object> values = Files.readAttributes(
    path, "basic:creationTime,lastModifiedTime,lastAccessTime"
);
```

Đây là **đoạn minh họa**, cần import `java.util.Map` và xử lý `IOException`. Values có kiểu `Object`, nên code cần cast đúng `FileTime` khi lấy từng key. Nếu chỉ đang học ba timestamp cơ bản, ưu tiên `BasicFileAttributes` để compiler giúp kiểm tra kiểu dữ liệu.

### 2.4. Mặc định theo symbolic link

Các lời gọi đọc thuộc tính có thể được truyền `NOFOLLOW_LINKS` để đổi đối tượng đang kiểm tra. Mặc định, nhiều phép đọc NIO.2 **đi qua symbolic link** và lấy thông tin target. Điều này ảnh hưởng trực tiếp tới ý nghĩa “thời gian tạo của *cái gì*?”: **link entry** hay **file đích**.

## 3. `FileTime`: biểu diễn thời điểm cho filesystem

`java.nio.file.attribute.FileTime` là kiểu bất biến, biểu diễn một thời điểm trong API thuộc tính file. Đừng nhầm nó với `java.io.File`; cũng đừng nhầm `FileTime` với `LocalDateTime` (ngày-giờ địa phương không gắn rõ múi giờ).

Các phương thức hữu ích:

| API | Ý nghĩa |
|---|---|
| `FileTime.fromMillis(long value)` | Tạo từ milliseconds tính từ Unix epoch |
| `FileTime.from(long value, TimeUnit unit)` | Tạo từ số lượng đơn vị thời gian so với epoch |
| `FileTime.from(Instant instant)` | Tạo từ `java.time.Instant` |
| `fileTime.toMillis()` | Chuyển về milliseconds kể từ epoch, **có thể mất độ chính xác** |
| `fileTime.to(TimeUnit unit)` | Chuyển thành giá trị với đơn vị tương ứng |
| `fileTime.toInstant()` | Chuyển thành `Instant`, thuận tiện tính toán và hiển thị |
| `fileTime.compareTo(other)` | So sánh thứ tự thời điểm |
| `fileTime.equals(other)` | So sánh hai giá trị `FileTime` theo hợp đồng giá trị |
| `fileTime.toString()` | Dạng chuỗi timestamp theo hợp đồng API |

`FileTime` không phải số mili-giây thuần túy; nó có thể đại diện độ chính xác khác mili-giây. `FileTime.from(Instant)` còn giúp làm việc với nanoseconds nếu `Instant` chứa phần nano.

### Unix epoch và độ chính xác

**Unix epoch** là `1970-01-01T00:00:00Z`. Giá trị tính từ epoch có thể âm nếu thời điểm trước epoch. Ví dụ:

```java
FileTime epoch = FileTime.fromMillis(0);
FileTime plusOneSecond = FileTime.from(1, TimeUnit.SECONDS);
```

Đây là **đoạn minh họa**, cần `FileTime` và `TimeUnit` (`java.util.concurrent.TimeUnit`).

Những khác biệt:

- `fromMillis(1_000)` và `from(1, TimeUnit.SECONDS)` biểu diễn **cùng thời điểm**.
- `from(Instant.parse("2025-01-01T00:00:00.123456789Z"))` có phần nano chi tiết hơn một giá trị milliseconds.
- Khi gọi `toMillis()`, phần sub-millisecond **không còn được giữ**; không thể hoàn nguyên nanoseconds chỉ từ `long` milliseconds.
- Chuyển sang `TimeUnit.SECONDS` cũng mất chi tiết dưới giây.
- Với timestamp cực lớn/vượt vùng biểu diễn của kiểu đích, các phép chuyển có thể **saturate** ở biên giá trị thay vì cho kết quả toán học vô hạn. Không xây dựng thuật toán yêu cầu sự chính xác vô hạn của `long`.

Nói cách khác: **độ chính xác biểu diễn trong Java** khác với **độ phân giải thực tế mà filesystem lưu được**. Một `FileTime` với 9 chữ số phần thập phân không có nghĩa filesystem luôn sẽ lưu lại được đủ 9 chữ số.

### `FileTime` so với `Instant`

`Instant` thuộc `java.time` và mô tả một thời điểm trên timeline UTC, rất hợp để so sánh hoặc tính khoảng cách. `FileTime` là kiểu tương tác với thuộc tính file. Khi ứng dụng cần logic thời gian:

```java
Instant instant = fileTime.toInstant();
Duration age = Duration.between(instant, Instant.now());
```

Đây là **đoạn minh họa**, cần `Duration`, `Instant` và biến `fileTime` kiểu `FileTime`. `Instant.now()` phụ thuộc clock lúc thực thi; bài test deterministic nên cung cấp `Clock` hoặc `Instant` cố định thay vì so với thời gian hệ thống.

Không giả định một timestamp “cũ hơn 24 giờ” luôn là bằng chứng file không thay đổi hoặc chưa được ai đọc trong 24 giờ, vì metadata có thể bị thay đổi hoặc chưa cập nhật.

## 4. Ví dụ Java 17: chuyển đổi `FileTime`, `Instant` và múi giờ

Ví dụ đầu tiên **không truy cập filesystem**. Nó giúp kiểm tra API thời gian mà không bị nhiễu bởi độ phân giải, permission hay chính sách metadata của thiết bị.

Lưu **`FileTimeBasicsDemo.java`**:

```java
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

public class FileTimeBasicsDemo {
    public static void main(String[] args) {
        Instant point = Instant.parse(
            "2024-02-29T12:34:56.123456789Z"
        );
        FileTime fileTime = FileTime.from(point);

        System.out.println("instant: " + fileTime.toInstant());
        System.out.println("millis: " + fileTime.toMillis());
        System.out.println("round trip: " +
            fileTime.toInstant().equals(point));

        FileTime twoSeconds = FileTime.from(
            2, TimeUnit.SECONDS
        );
        System.out.println("two seconds: " +
            twoSeconds.toInstant());

        DateTimeFormatter local = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
        System.out.println("Vietnam time: " +
            local.format(fileTime.toInstant()));
    }
}
```

Biên dịch và chạy:

```bash
javac FileTimeBasicsDemo.java
java FileTimeBasicsDemo
```

Stdout:

```text
instant: 2024-02-29T12:34:56.123456789Z
millis: 1709210096123
round trip: true
two seconds: 1970-01-01T00:00:02Z
Vietnam time: 2024-02-29 19:34:56
```

Giải thích:

1. `Instant.parse(...Z)` nhận thời điểm UTC. `FileTime.from(point)` tạo giá trị timestamp tương ứng.
2. `toInstant()` giữ được thời điểm gốc trong trường hợp giá trị bình thường này.
3. `toMillis()` trả milliseconds từ epoch; **phần `456789` nanoseconds nằm sau 123 milliseconds không được giữ**.
4. `FileTime.from(2, TimeUnit.SECONDS)` biểu diễn hai giây sau epoch.
5. Formatter dùng vùng `Asia/Ho_Chi_Minh` để hiển thị giờ địa phương: tháng 2/2024 thời điểm này là UTC+07, nên `12:34:56Z` thành `19:34:56`.

Thời điểm **không bị “đổi thành một thời điểm khác”** khi hiển thị theo múi giờ; chỉ đổi cách trình bày cùng một điểm trên timeline. Nếu cần lưu trạng thái để truyền giữa hệ thống, `Instant` hoặc thời gian epoch theo đơn vị đã quy ước thường phù hợp hơn chuỗi ngày-giờ địa phương không có zone.

## 5. Đọc timestamp thật với `BasicFileAttributes`

Một filesystem thực tế trả thời gian do nó quản lý. Hãy phân biệt **điều được bảo đảm bởi type/API** với **giá trị cụ thể phụ thuộc môi trường**.

Lưu **`ReadFileTimesDemo.java`**:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class ReadFileTimesDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("times-read-", ".txt");

        try {
            BasicFileAttributes attrs = Files.readAttributes(
                file, BasicFileAttributes.class
            );

            System.out.println("regular: " +
                attrs.isRegularFile());
            System.out.println("created available: " +
                (attrs.creationTime() != null));
            System.out.println("modified available: " +
                (attrs.lastModifiedTime() != null));
            System.out.println("accessed available: " +
                (attrs.lastAccessTime() != null));

            // Giá trị cụ thể sẽ khác mỗi lần chạy.
            System.out.println(
                "created value: " + attrs.creationTime());
            System.out.println(
                "modified value: " + attrs.lastModifiedTime());
            System.out.println(
                "accessed value: " + attrs.lastAccessTime());
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Chạy:

```bash
javac ReadFileTimesDemo.java
java ReadFileTimesDemo
```

Phần đầu stdout **thường** là:

```text
regular: true
created available: true
modified available: true
accessed available: true
```

Sau đó có ba dòng `created value: ...`, `modified value: ...`, `accessed value: ...`; **không có kết quả timestamp cố định**, vì thời gian tạo file tạm do filesystem cung cấp. API `BasicFileAttributes` trả các đối tượng `FileTime` cho ba phương thức thời gian, nhưng một timestamp có thể là **giá trị mặc định** khi filesystem không lưu thuộc tính thực; “available” trong chương trình chỉ kiểm tra **object khác null**, không phải bằng chứng “filesystem hỗ trợ timestamp tạo chính xác”.

Đừng viết unit test yêu cầu **ba timestamp bằng nhau** ngay sau `createTempFile`: độ phân giải hoặc cập nhật metadata có thể làm chúng khác nhau. Cũng không nên giả định creation time tồn tại trên Linux giống Windows; điều đó còn tùy filesystem và provider.

## 6. Đổi `lastModifiedTime`: `Files.setLastModifiedTime`

Nếu nghiệp vụ phải **đặt thời gian sửa đổi** của file:

```java
FileTime requested = FileTime.from(
    Instant.parse("2024-01-01T00:00:00Z")
);

Files.setLastModifiedTime(path, requested);
```

Đây là **đoạn minh họa**, cần xử lý `IOException`; `path` là `Path` cần sửa thuộc tính. `setLastModifiedTime` trả lại `Path`, thuận tiện khi chain nhưng không phải cam kết filesystem đã lưu **chính xác từng nanosecond**.

Những giới hạn cần nắm:

- Operation có thể thất bại do quyền truy cập, filesystem chỉ đọc, provider không hỗ trợ hoặc lỗi I/O.
- Filesystem có thể lưu thời gian ở **độ phân giải thấp hơn** hoặc làm tròn/cắt các phần lẻ.
- Nếu timestamp nằm ngoài dải filesystem có thể lưu, kết quả có thể bị giới hạn theo cách của API/provider hoặc ném lỗi.
- Một process khác có thể ghi file ngay sau khi bạn set timestamp, làm last-modified time đổi tiếp.
- Thay đổi mtime **không** đổi nội dung file. Vì thế metadata time **không phải checksum** hoặc bằng chứng dữ liệu giống nhau.

### Ví dụ Java 17: set và đọc lại mtime có kiểm tra

Lưu **`SetModifiedTimeDemo.java`**:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

public class SetModifiedTimeDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("mtime-demo-", ".txt");
        FileTime requested = FileTime.from(
            Instant.parse("2020-01-01T00:00:00Z")
        );

        try {
            Files.setLastModifiedTime(file, requested);

            FileTime stored = Files.getLastModifiedTime(file);
            System.out.println("requested: " +
                requested.toInstant());
            System.out.println("stored: " +
                stored.toInstant());
            System.out.println("same instant: " +
                requested.toInstant().equals(stored.toInstant()));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Biên dịch/chạy:

```bash
javac SetModifiedTimeDemo.java
java SetModifiedTimeDemo
```

Trên filesystem hỗ trợ và giữ chính xác mốc 2020-01-01 00:00:00 UTC theo đơn vị giây, stdout sẽ là:

```text
requested: 2020-01-01T00:00:00Z
stored: 2020-01-01T00:00:00Z
same instant: true
```

**Đây là output có điều kiện.** Filesystem có thể lưu timestamp khác yêu cầu do quy tắc provider/độ phân giải, hoặc `setLastModifiedTime` có thể ném `IOException`. Chương trình in cả giá trị `requested` và `stored` nhằm **quan sát thực tế**, không ngầm tuyên bố luôn bằng nhau.

Đặt thời điểm đúng đầu giây giúp ví dụ ít gặp sai khác do làm tròn hơn khi dùng thời điểm có nanoseconds. Trong chương trình xử lý chuyên nghiệp, nên đọc lại nếu việc lưu đúng thời điểm là yêu cầu, nhưng ngay cả readback cũng chỉ là trạng thái ở thời điểm quan sát.

## 7. Đặt nhiều timestamp qua `BasicFileAttributeView.setTimes`

Java cung cấp `BasicFileAttributeView` để làm việc với thuộc tính cơ bản của file. Với implementation hỗ trợ, bạn có thể yêu cầu cập nhật **mtime**, **atime** và **creation time**:

```java
BasicFileAttributeView view = Files.getFileAttributeView(
    path, BasicFileAttributeView.class
);

view.setTimes(modified, accessed, created);
```

Đây là **đoạn minh họa**, cần import `java.nio.file.attribute.BasicFileAttributeView`, `Files` và các biến `FileTime`, xử lý `IOException`. Không nên giả định mọi provider đều cho phép sửa cả ba timestamp, đặc biệt creation time.

Chữ ký:

```java
void setTimes(
    FileTime lastModifiedTime,
    FileTime lastAccessTime,
    FileTime createTime
) throws IOException;
```

Đây là **chữ ký API minh họa**, không phải định nghĩa mới. Ý nghĩa tham số:

1. `lastModifiedTime`: thời điểm sửa đổi nội dung.
2. `lastAccessTime`: thời điểm truy cập gần nhất.
3. `createTime`: thời điểm tạo.

**Truyền `null` cho một tham số** nghĩa là không yêu cầu cập nhật thuộc tính tương ứng, rất hữu ích khi chỉ muốn đặt một timestamp mà không làm thay đổi hai timestamp khác.

Ví dụ chỉ yêu cầu đặt access time:

```java
view.setTimes(null, desiredAccessTime, null);
```

Đây là **đoạn minh họa**. Lời gọi có thể ném `IOException`; provider có thể không hỗ trợ set access time theo ý ứng dụng. Tùy filesystem, thuộc tính không được hỗ trợ có thể bị bỏ qua theo hợp đồng provider hoặc không thể cập nhật; phải kiểm tra tài liệu/khả năng của môi trường.

### Đổi timestamp của chính symlink

Nếu cần xử lý thuộc tính của **entry link**, không phải target, lấy view với `NOFOLLOW_LINKS`:

```java
BasicFileAttributeView linkView =
    Files.getFileAttributeView(
        link,
        BasicFileAttributeView.class,
        LinkOption.NOFOLLOW_LINKS
    );
```

Đây là **đoạn minh họa**, `link` kiểu `Path`. Một filesystem có thể không cho sửa timestamp của symbolic link qua view này, hoặc có cách xử lý khác khi thiếu quyền. Không gọi `setTimes` khi `linkView == null` (nếu provider không hỗ trợ attribute view được yêu cầu); thông thường default filesystem có basic view, nhưng code sử dụng provider tùy ý nên kiểm tra capability.

**Chú ý về metadata khác:** Một lần set mtime/atime không có nghĩa mọi metadata khác bất biến. Filesystem có thể ghi timestamp status-change hoặc thông tin quản trị riêng; chương trình không nên khẳng định quá mức về những thuộc tính ngoài hợp đồng Java NIO.2.

## 8. Ví dụ Java 17: chỉ yêu cầu thay đổi access time

Để thấy rõ cách gọi `setTimes` với `null` và xử lý khác biệt provider, dùng chương trình sau.

Lưu **`SetAccessTimeDemo.java`**:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

public class SetAccessTimeDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("atime-demo-", ".txt");

        try {
            BasicFileAttributeView view =
                Files.getFileAttributeView(
                    file, BasicFileAttributeView.class);

            if (view == null) {
                System.out.println("Basic view unavailable");
                return;
            }

            FileTime desired = FileTime.from(
                Instant.parse("2020-01-01T00:00:00Z")
            );

            try {
                view.setTimes(null, desired, null);
                BasicFileAttributes attrs = view.readAttributes();

                System.out.println("requested: " +
                    desired.toInstant());
                System.out.println("observed: " +
                    attrs.lastAccessTime().toInstant());
            } catch (IOException | UnsupportedOperationException ex) {
                System.out.println(
                    "Access-time update unavailable");
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Chạy:

```bash
javac SetAccessTimeDemo.java
java SetAccessTimeDemo
```

Với một filesystem hỗ trợ thay đổi atime như yêu cầu, hai dòng quan sát thường là:

```text
requested: 2020-01-01T00:00:00Z
observed: 2020-01-01T00:00:00Z
```

Một số môi trường có thể hiển thị timestamp `observed` khác, in `Access-time update unavailable`, hoặc không cung cấp view được yêu cầu. Mục đích của ví dụ là **thực hiện và quan sát** lời gọi, chứ không quy kết một kết quả duy nhất cho mọi filesystem.

Đặc biệt, một lần **đọc metadata** không giống một lần đọc nội dung file. Nhưng provider có thể có chính sách cập nhật access time riêng, do đó không nên dựa vào kết quả thí nghiệm đơn lẻ để suy ra mọi thao tác sau sẽ không làm thay đổi atime.

## 9. Múi giờ, UTC và cách hiển thị timestamp

Các timestamp của `FileTime` đại diện **một thời điểm**, không phải một cặp ngày/giờ gắn với múi giờ của người xem. Để hiển thị đúng theo người dùng, chuyển sang `Instant`, sau đó dùng `ZoneId` và `DateTimeFormatter`:

```java
DateTimeFormatter fmt = DateTimeFormatter
    .ofPattern("uuuu-MM-dd HH:mm:ss z")
    .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

System.out.println(
    fmt.format(fileTime.toInstant())
);
```

Đây là **đoạn minh họa**, cần import `java.time.ZoneId`, `java.time.format.DateTimeFormatter` và biến `fileTime`.

**Không** diễn giải `toMillis()` là milliseconds theo giờ Việt Nam hoặc giờ máy: epoch milliseconds là số lượng milliseconds trên timeline UTC. Chuyển sang GMT+7 chỉ ảnh hưởng biểu diễn.

Một tình huống dễ nhầm:

- Java in timestamp `2024-02-29T12:34:56Z`.
- File manager trên máy ở Việt Nam có thể hiển thị `19:34:56`.
- Hai giá trị có thể chỉ **cùng một thời điểm**, không phải file bị sửa hai lần.

Khi trao đổi timestamp giữa dịch vụ, nên quy định một định dạng thời điểm rõ ràng như ISO-8601 có offset/UTC hoặc epoch milliseconds; không gửi riêng `"29/02/2024 19:34:56"` mà không nói múi giờ nếu hệ thống có nhiều vùng địa lý.

### `LocalDateTime` không phải `Instant`

`LocalDateTime` là ngày-giờ **không mang zone/offset**; `Instant` là một điểm thời gian có nghĩa tuyệt đối. Đừng làm:

```java
LocalDateTime local = LocalDateTime.parse("2024-02-29T19:34:56");
// Không thể suy ra một Instant duy nhất nếu chưa xác định zone/offset.
```

Đây là **đoạn minh họa**, cần import `LocalDateTime`. Cùng một ngày-giờ địa phương có thể ứng với thời điểm khác nhau trên thế giới, và ở một số múi giờ có chuyển DST còn có khoảng thời gian bị lặp hoặc không tồn tại.

## 10. Độ phân giải, làm tròn và tính nhất quán

Không phải mọi filesystem lưu timestamp chính xác tới nanosecond. Một ví dụ lý thuyết:

```text
Java yêu cầu mtime:
2024-01-01T00:00:00.123456789Z

Filesystem A lưu được:
2024-01-01T00:00:00.123456789Z

Filesystem B có thể chỉ lưu được:
2024-01-01T00:00:00.123Z

Filesystem C có thể lưu theo bước thô hơn:
2024-01-01T00:00:00Z
```

Đây là **minh họa**, không khẳng định một filesystem cụ thể luôn có chính xác một trong ba mức trên.

Các hệ quả:

1. **Không so sánh `FileTime` sát từng nanosecond** trong integration test portable sau khi set/read timestamp, trừ khi môi trường đã có hợp đồng về độ phân giải.
2. Khi kiểm tra “file có thay đổi”, mtime bằng nhau **không** bảo đảm nội dung giống nhau. Hai lần ghi có thể rơi vào cùng một tick timestamp hoặc mtime bị set trở lại.
3. Khi kiểm tra “mtime khác”, bản thân việc backup/restore/copy có thể thay timestamp mà không đổi nội dung.
4. `Files.readAttributes` trên network filesystem có thể phản ánh cache metadata; đừng mặc định giá trị luôn cập nhật ngay tức thì trên tất cả máy.
5. `FileTime.toMillis()` làm mất phần thời gian nhỏ hơn 1 ms; tránh chuyển qua milliseconds nếu nghiệp vụ cần độ phân giải cao và cả nguồn/đích thực sự hỗ trợ.
6. Các phép đo tuổi file dùng `Instant.now()` còn bị ảnh hưởng bởi clock skew hoặc đồng hồ hệ thống được điều chỉnh; không coi mtime như clock đơn điệu.

Nếu muốn đo **khoảng thời gian một tác vụ chạy**, dùng `System.nanoTime()` hoặc clock đơn điệu phù hợp, **không dùng thời gian sửa file** làm đồng hồ đo hiệu năng.

### Dùng mtime cho cache hoặc incremental scan

Một bộ quét file thường làm:

```text
Lần trước: lưu (đường dẫn, mtime, kích thước)
Lần sau: lấy metadata mới, nếu khác → xử lý lại
```

Đây là **heuristic tối ưu**, không phải bằng chứng mật mã rằng file không đổi nếu metadata trùng. Muốn tính toàn vẹn mạnh, có thể cần hash nội dung hoặc hệ thống version/commit ID được quản lý nhất quán. Hash cũng phải được tính từ dữ liệu được đọc đúng cách, trong khi file có thể bị thay đổi đồng thời; hệ thống nghiêm ngặt có thể cần snapshot hoặc cơ chế transaction của storage.

## 11. Sao chép, di chuyển và timestamp

Sao chép file không nhất thiết giữ mọi timestamp của nguồn. `Files.copy` có option `StandardCopyOption.COPY_ATTRIBUTES` để **cố gắng sao chép metadata** khi provider hỗ trợ:

```java
Files.copy(
    source,
    target,
    StandardCopyOption.COPY_ATTRIBUTES
);
```

Đây là **đoạn minh họa**, với `source`, `target` kiểu `Path`, đã import `Files`/`StandardCopyOption` và xử lý `IOException`. Có những thuộc tính không thể copy chính xác hoặc bị provider giới hạn; mức hỗ trợ cụ thể tùy filesystem. Chỉ vì `COPY_ATTRIBUTES` được yêu cầu **không nên kết luận** creation/modified/access time của file đích hoàn toàn giống nguồn trên mọi môi trường.

Một số câu hỏi phải xác định theo nghiệp vụ:

- File đích là **bản sao lịch sử** và nên giữ mtime nguồn?
- File đích là **tài liệu mới sinh**, nên có timestamp hiện tại?
- Khi restore, phải phục hồi metadata nào? Có quyền đặt creation time không?
- Nếu mục tiêu dùng mtime để invalid cache, việc copy giữ mtime có thể khiến cache bỏ qua nội dung khác không?

**Move/rename** cũng có thể ảnh hưởng mốc thời gian theo quy tắc khác copy. Với `ATOMIC_MOVE`, trọng tâm của option là tính nguyên tử của thao tác move ở mức provider hỗ trợ, không phải hứa hẹn mọi timestamp bất biến và durable sau sự cố.

## 12. Symbolic link và object identity

Khi làm việc với symlink:

```text
root/
├── target.txt          (nội dung thật)
└── shortcut.txt -> target.txt
```

Có ít nhất hai nhóm metadata được quan tâm:

- Metadata của **target.txt**.
- Metadata của chính **shortcut.txt** như một symbolic link entry.

`Files.readAttributes(link, BasicFileAttributes.class)` thường theo link và đọc thuộc tính target; `Files.readAttributes(link, BasicFileAttributes.class, NOFOLLOW_LINKS)` đọc thuộc tính link entry nếu provider hỗ trợ.

```java
BasicFileAttributes targetAttributes = Files.readAttributes(
    link, BasicFileAttributes.class
);

BasicFileAttributes linkAttributes = Files.readAttributes(
    link, BasicFileAttributes.class,
    LinkOption.NOFOLLOW_LINKS
);
```

Đây là **đoạn minh họa**, `link` là `Path`, cần các import tương ứng và xử lý `IOException`.

Nếu là **dangling link** (target bị xóa), lời gọi follow-link có thể thất bại hoặc không xác minh được target, còn `NOFOLLOW_LINKS` có thể đọc được metadata của entry link còn tồn tại. Việc link có mtime khác target là điều có thể xảy ra; không thể suy ra trạng thái link từ mtime của target và ngược lại.

### Không dùng timestamp như ID bền vững

Hai file có thể có cùng mtime; một file có thể bị thay bằng file khác **cùng tên, cùng kích thước và cùng mtime**. Nếu cần nhận diện object, `BasicFileAttributes.fileKey()` có thể giúp ở một số provider, nhưng **không được bảo đảm khác null hoặc bền qua mọi thao tác/khởi động**. Tùy mục tiêu, cần database ID, content hash, version hoặc handle mở an toàn.

## 13. Xử lý lỗi khi đọc và đặt timestamp

Các API metadata có thể ném:

| Lỗi/tình huống | Diễn giải |
|---|---|
| `NoSuchFileException` | Đường dẫn không còn tồn tại tại thời điểm thao tác (nếu provider báo subtype này) |
| `AccessDeniedException` | Không có quyền cho thao tác tương ứng |
| `FileSystemException` | Lỗi filesystem cụ thể, có thể kèm lý do |
| `IOException` | Lỗi I/O chung hoặc lỗi do provider |
| `UnsupportedOperationException` | Thao tác/thuộc tính/option không được hỗ trợ theo API/provider |
| `SecurityException` | Kiểm tra bảo mật runtime từ chối theo môi trường |
| Kết quả timestamp mặc định | Không phải lúc nào cũng là lỗi; provider có thể không hỗ trợ timestamp thực |

Không bắt mọi `IOException` rồi in “file không tồn tại”. Lỗi quyền, thiết bị và path missing cần chính sách retry/báo lỗi khác nhau.

Một đoạn xử lý:

```java
try {
    BasicFileAttributes attrs = Files.readAttributes(
        path, BasicFileAttributes.class
    );
    System.out.println(attrs.lastModifiedTime());
} catch (NoSuchFileException ex) {
    System.err.println("Path missing: " + path);
} catch (AccessDeniedException ex) {
    System.err.println("Access denied: " + path);
} catch (IOException ex) {
    System.err.println("Metadata I/O error: " + ex.getMessage());
}
```

Đây là **đoạn minh họa**, cần `Files`, `Path`, `BasicFileAttributes` và các subtype exception `java.nio.file`.

Với filesystem đa provider, exception subtype không phải lúc nào giống nhau. Trong ứng dụng cần kiểm soát việc retry/backup, hãy ghi log context, phân biệt lỗi tạm thời với lỗi chắc chắn và tránh xóa/cập nhật file chỉ vì không đọc được metadata.

## 14. TOCTOU: metadata vừa đọc có thể đã cũ

Một đoạn code:

```java
if (Files.getLastModifiedTime(path).toMillis() < cutoff) {
    Files.delete(path);
}
```

Đây là **phản ví dụ nguy hiểm nếu directory bị sửa đồng thời**. Sau lúc kiểm tra, process khác có thể ghi đè hoặc thay thế entry. Nếu xóa bằng pathname sau đó, chương trình có thể xóa **file mới**, không phải file cũ mà metadata vừa mô tả.

Lược đồ:

```text
T1  Scanner đọc mtime của report.txt → "cũ"
T2  Process khác thay report.txt bằng bản mới
T3  Scanner gọi Files.delete(report.txt)
T4  Bản mới bị xóa ngoài ý muốn
```

Đây là dạng **time-of-check-to-time-of-use**. Một lệnh `readAttributes` không giữ khóa filesystem sau khi hàm kết thúc.

Cách giảm rủi ro phụ thuộc môi trường:

- Giới hạn quyền ghi vào directory được quét; tránh cho untrusted process sửa cùng lúc.
- Dùng workflow version/snapshot/lock phù hợp hoặc lưu trạng thái qua transaction của ứng dụng.
- Với thao tác theo directory cần chống race đường dẫn, cân nhắc `SecureDirectoryStream` nếu provider hỗ trợ.
- Nếu cần xóa chỉ những file chắc chắn thuộc một phiên bản, thiết kế protocol với ID/version; không dựa độc nhất vào `mtime`/filename.
- Một lần kiểm tra lại ngay trước xóa có thể giảm xác suất race, nhưng **không loại bỏ** khoảng thời gian giữa kiểm tra và thao tác.

**Timestamp không phải cơ chế bảo mật hoặc đồng bộ.**

## 15. `java.io.File.lastModified()` so với NIO.2

Java I/O truyền thống có các phương thức:

```java
File f = new File("report.txt");
long modifiedMillis = f.lastModified();
boolean changed = f.setLastModified(
    1_700_000_000_000L
);
```

Đây là **đoạn minh họa**, cần import `java.io.File`. Có hai giới hạn đáng chú ý:

- `File.lastModified()` trả `long` milliseconds; **0L cũng có thể là giá trị báo không lấy được thông tin** theo hợp đồng, gây nhập nhằng với timestamp epoch thật.
- `File.setLastModified(long)` trả `boolean` báo thành công/thất bại, ít giàu thông tin lỗi hơn cách dùng `Files.setLastModifiedTime` và checked exceptions.

NIO.2 thường diễn đạt chính xác hơn:

```java
Path path = f.toPath();
FileTime mtime = Files.getLastModifiedTime(path);
Files.setLastModifiedTime(
    path, FileTime.fromMillis(1_700_000_000_000L)
);
```

Đây là **đoạn minh họa**, cần import `Files`, `Path`, `FileTime` và xử lý `IOException`.

Nếu mục tiêu là **creation/access time**, `BasicFileAttributes` và `BasicFileAttributeView` mới là API tổng quát phù hợp. Không cố đọc “creation time” từ `File.lastModified()` vì đó là hai thuộc tính khác nhau.

## 16. Các lỗi phổ biến

1. **Nhầm Unix `ctime` với creation time.** Trên nhiều hệ Unix, `ctime` nói về thay đổi trạng thái metadata, không phải “birth time”.
2. **Tin `creationTime()` luôn là timestamp tạo thật.** Provider có thể trả giá trị thay thế nếu không hỗ trợ.
3. **Tin `lastAccessTime()` luôn đổi sau mỗi lần đọc.** Chính sách noatime/relatime, cache và provider ảnh hưởng cập nhật.
4. **So sánh timestamp theo chuỗi ngày-giờ địa phương.** Dùng `Instant`/`FileTime` để so sánh thời điểm; dùng zone khi trình bày.
5. **Coi `FileTime.toMillis()` giữ nanoseconds.** Nó chỉ biểu diễn milliseconds; phần nhỏ hơn bị mất.
6. **Đặt mtime với nano bất kỳ rồi assert đọc lại giống 100%.** Filesystem có thể làm tròn hoặc có độ phân giải thấp.
7. **Coi file có mtime giống nhau là có nội dung giống nhau.** Cần thêm cơ chế kiểm tra phiên bản/hash nếu nghiệp vụ yêu cầu.
8. **Coi mtime khác là bằng chứng nội dung khác.** Metadata có thể bị đổi qua copy/restore hoặc API set time.
9. **Nhầm timestamp của symlink và target.** Chọn follow hoặc `NOFOLLOW_LINKS` theo câu hỏi.
10. **Bỏ qua `IOException` khi set time.** Quyền truy cập, filesystem chỉ đọc và provider có thể từ chối.
11. **Quên `null` trong `BasicFileAttributeView.setTimes`.** `null` nghĩa là không yêu cầu đặt timestamp tương ứng, không phải “đặt về epoch”.
12. **Dùng `Files.exists()` trước mỗi thao tác và nghĩ tránh race.** Kết quả check có thể lỗi thời; thao tác chính vẫn cần catch error.
13. **Dùng mtime làm ID file.** Timestamp không duy nhất và có thể bị giả lập/giữ nguyên khi file bị thay.
14. **Không phân biệt `FileTime` với `LocalDateTime`.** `FileTime` là thời điểm filesystem; `LocalDateTime` thiếu zone/offset.
15. **Coi Java API luôn tự đồng bộ metadata xuống đĩa bền vững.** Set time thành công không phải cam kết crash durability trên mọi thiết bị.
16. **Hiểu `Files.copy(..., COPY_ATTRIBUTES)` là bảo toàn mọi timestamp hoàn hảo.** Khả năng copy metadata tùy provider và storage.
17. **Không kiểm thử thực tế filesystem đích.** Local development, network mount, container và production có thể có cơ chế timestamp khác nhau.

## 17. Bảng chọn API theo tình huống

| Nhu cầu | API/thiết kế đề xuất |
|---|---|
| Xem thời gian sửa một file | `Files.getLastModifiedTime(path)` |
| Xem cả tạo/sửa/truy cập | `Files.readAttributes(path, BasicFileAttributes.class)` |
| Cần metadata của chính symbolic link | Truyền `LinkOption.NOFOLLOW_LINKS` khi API hỗ trợ |
| Đặt mtime | `Files.setLastModifiedTime(path, fileTime)` |
| Yêu cầu đặt atime/creation time | `BasicFileAttributeView.setTimes(...)` và kiểm tra provider |
| Lưu/truyền một thời điểm | `Instant` hoặc epoch millis với đơn vị được quy định |
| Hiển thị theo giờ Việt Nam | `fileTime.toInstant()` + `ZoneId.of("Asia/Ho_Chi_Minh")` |
| So sánh hai timestamp | `FileTime.compareTo` hoặc `Instant.compareTo` |
| Kiểm tra thay đổi nội dung mạnh | Hash/version và cơ chế đồng bộ phù hợp, không chỉ mtime |
| Theo dõi truy cập nghiệp vụ | Log/sự kiện truy cập do ứng dụng quản lý |
| Cần xác định lỗi I/O rõ ràng | API NIO.2 với `IOException`/subtype |
| Tạo/xóa theo trạng thái để tránh race | API thao tác trực tiếp, version/transaction thay vì check-then-act |

## 18. Câu hỏi tự kiểm tra

**Câu 1.** Ba phương thức thời gian quan trọng của `BasicFileAttributes` là gì?

**Đáp án:** `creationTime()`, `lastModifiedTime()`, `lastAccessTime()`; chúng trả `FileTime`.

**Câu 2.** `creationTime()` có luôn bằng thời điểm file xuất hiện lần đầu trên ổ đĩa không?

**Đáp án:** Không. Filesystem có thể không hỗ trợ timestamp tạo thực, provider có thể trả giá trị mặc định; copy/restore/set metadata có thể thay nghĩa của timestamp.

**Câu 3.** Unix `ctime` có phải creation time không?

**Đáp án:** Thông thường không. Unix `ctime` là thời gian thay đổi trạng thái inode/metadata; khác birth/creation time.

**Câu 4.** `FileTime.from(Instant)` và `toMillis()` có luôn bảo toàn nanosecond không?

**Đáp án:** `FileTime` có thể biểu diễn độ chính xác cao, nhưng `toMillis()` làm mất phần nhỏ hơn milliseconds. Filesystem thực tế còn có độ phân giải riêng.

**Câu 5.** Tại sao `lastAccessTime()` không phù hợp làm lịch sử lượt đọc đáng tin cậy?

**Đáp án:** OS/filesystem có thể không cập nhật atime mỗi lần đọc hoặc dùng chính sách tối ưu, cache; timestamp cũng có thể bị đặt lại.

**Câu 6.** Muốn đặt chỉ mtime qua `BasicFileAttributeView.setTimes`, truyền tham số nào?

**Đáp án:** `view.setTimes(desiredModified, null, null)`. Hai `null` nghĩa là không yêu cầu cập nhật atime và creation time.

**Câu 7.** Muốn đọc metadata của symlink, không phải target, làm thế nào?

**Đáp án:** `Files.readAttributes(link, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)` khi provider hỗ trợ.

**Câu 8.** Có thể kết luận file nội dung không đổi khi mtime và size không đổi?

**Đáp án:** Không. Mtime có thể làm tròn, được đặt lại; nội dung có thể thay nhưng kích thước giữ nguyên. Đó chỉ là heuristic.

**Câu 9.** `Files.setLastModifiedTime` thành công có bảo đảm đọc lại chính xác timestamp nano không?

**Đáp án:** Không. Độ phân giải, dải thời gian, quyền và hành vi filesystem/provider có thể làm khác; nếu quan trọng phải đọc lại và kiểm chứng theo môi trường.

**Câu 10.** Đọc mtime rồi xóa file nếu mtime quá cũ có an toàn khi process khác cũng ghi directory đó?

**Đáp án:** Không chắc. Đây là TOCTOU: file có thể bị thay trước khi xóa. Cần cơ chế version, lock/snapshot hoặc API/thiết kế phù hợp.

**Câu 11.** Vì sao `File.lastModified()` cũ có thể ít rõ ràng hơn `Files.getLastModifiedTime()`?

**Đáp án:** `File.lastModified()` trả long milliseconds và giá trị 0 có thể biểu thị không lấy được thông tin; NIO.2 dùng `FileTime` và checked `IOException` giàu ngữ cảnh hơn.

**Câu 12.** Muốn hiển thị cùng thời điểm cho người dùng ở hai múi giờ khác nhau, có cần sửa timestamp file không?

**Đáp án:** Không. Dùng cùng `Instant`, format theo `ZoneId` tương ứng; timestamp gốc không thay đổi.

## Tổng kết

- **Creation**, **last modified** và **last access** là ba loại timestamp khác nhau, có thể thiếu hoặc không chính xác tuyệt đối tùy filesystem.
- `BasicFileAttributes` đọc ba timestamp dưới dạng `FileTime`; `Files.getLastModifiedTime` tiện khi chỉ cần mtime.
- `FileTime` là thời điểm, có thể chuyển qua `Instant` để so sánh/format; `toMillis()` không giữ độ chính xác dưới mili-giây.
- `Files.setLastModifiedTime` và `BasicFileAttributeView.setTimes` cho phép **yêu cầu** sửa metadata trong phạm vi provider hỗ trợ; không bảo đảm mọi timestamp đều đặt được hay được lưu nguyên vẹn.
- **Symlink**, **múi giờ**, **độ phân giải**, **copy/restore**, **quyền truy cập** và **thao tác đồng thời** đều ảnh hưởng cách đọc/diễn giải timestamp.
- Khi nghiệp vụ đòi hỏi tính toàn vẹn mạnh, đồng bộ hóa hoặc lịch sử truy cập, không xem timestamp của filesystem là nguồn sự thật duy nhất.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-attributes-and-permissions-003">Kích thước, loại tệp và fileKey</a>

<details>
<summary>Xem chi tiết</summary>

# Kích thước, loại tệp và `fileKey` với `BasicFileAttributes` trong Java NIO.2

Khi xử lý file trong Java, một đường dẫn (`Path`) không tự cho biết thứ mà nó đang trỏ đến là **tệp thông thường**, **thư mục**, **symbolic link**, đối tượng hệ thống tệp đặc biệt hay không tồn tại. Ứng dụng cũng cần biết **kích thước theo byte**, và đôi khi cần xác định liệu hai đường dẫn có đại diện cho **cùng một tệp thực** hay không. Những câu hỏi này liên quan nhưng không đồng nhất.

API trọng tâm là:

```java
BasicFileAttributes attributes = Files.readAttributes(
        path, BasicFileAttributes.class);
```

Đây là đoạn mã **minh họa**, cần các import `java.nio.file.Files`, `java.nio.file.Path`, `java.nio.file.attribute.BasicFileAttributes`, và xử lý `IOException`. Lệnh đọc thuộc tính từ hệ thống tệp thông qua provider; nó **không đọc nội dung** file vào một `String`, không mở một `InputStream` để bạn đọc các byte nội dung và không tạo file mới.

Từ `attributes`, có thể hỏi:

```java
long bytes = attributes.size();
boolean regular = attributes.isRegularFile();
boolean directory = attributes.isDirectory();
boolean symbolicLink = attributes.isSymbolicLink();
boolean other = attributes.isOther();
Object key = attributes.fileKey();
```

Trong các phương thức này, `size()` và bốn phép phân loại trả về thông tin thuộc tính; `fileKey()` cung cấp một **định danh đối tượng do filesystem/provider quyết định** nếu có hỗ trợ. `fileKey` rất hữu ích nhưng **không phải ID toàn cục, không bảo đảm tồn tại và không dùng làm chứng cứ bảo mật bất biến**.

Bài này dùng Java 17+, tập trung vào ý nghĩa hợp đồng API, khác biệt `File`/`Files`, hành vi với symbolic link, file system metadata thay đổi, kiểm tra danh tính tệp và các sai lầm thực tế.

## 1. `BasicFileAttributes` và `Files.readAttributes(...)`

`BasicFileAttributes` là interface trong package:

```java
java.nio.file.attribute.BasicFileAttributes
```

Nó cung cấp nhóm metadata cơ bản:

| Thành viên | Giá trị | Câu hỏi nó trả lời |
| --- | --- | --- |
| `size()` | `long` | Kích thước được filesystem báo cáo, đơn vị byte |
| `isRegularFile()` | `boolean` | Đây có phải regular file không? |
| `isDirectory()` | `boolean` | Đây có phải thư mục không? |
| `isSymbolicLink()` | `boolean` | Đây có phải symbolic link không? |
| `isOther()` | `boolean` | Đây có thuộc loại khác những loại nêu trên không? |
| `fileKey()` | `Object` hoặc `null` | Có khóa định danh tệp do provider cung cấp không? |
| `creationTime()` | `FileTime` | Thời điểm tạo (phụ thuộc provider) |
| `lastModifiedTime()` | `FileTime` | Thời điểm sửa gần nhất |
| `lastAccessTime()` | `FileTime` | Thời điểm truy cập gần nhất |

Ba timestamp là những phần khác của `BasicFileAttributes`, nhưng trọng tâm ở đây là **size, type, fileKey**. Không mặc định mọi provider có độ chính xác thời gian giống nhau hoặc hỗ trợ đầy đủ metadata của mọi loại filesystem.

Một lời gọi đọc thuộc tính điển hình:

```java
BasicFileAttributes attrs = Files.readAttributes(
        path, BasicFileAttributes.class);
```

Theo mặc định, phép truy vấn thường **đi theo symbolic link** để lấy thuộc tính của *đích liên kết* (target). Nếu muốn hỏi về **chính mục symbolic link**, dùng:

```java
BasicFileAttributes linkAttrs = Files.readAttributes(
        path,
        BasicFileAttributes.class,
        LinkOption.NOFOLLOW_LINKS);
```

Đây là những đoạn **minh họa**, không phải chương trình Java độc lập. `LinkOption.NOFOLLOW_LINKS` quan trọng vì cùng một `Path` có thể dẫn tới hai đối tượng metadata khác nhau: **link** hoặc **target**.

### Vì sao đọc một nhóm thuộc tính thay vì gọi nhiều phương thức?

Ta có thể gọi:

```java
long bytes = Files.size(path);
boolean regular = Files.isRegularFile(path);
```

Nếu cần cả loại tệp, kích thước và `fileKey`, `Files.readAttributes(...)` gom các thuộc tính cơ bản vào **một truy vấn ở mức API**. Nó cũng cung cấp kết quả theo cùng bộ thuộc tính được trả về.

Tuy nhiên, **không mặc định toàn bộ thuộc tính được đọc nguyên tử** trên mọi filesystem; hợp đồng của API cho phép cách đọc cụ thể tùy implementation. Hơn nữa, các thuộc tính đã lấy có thể trở nên lỗi thời ngay sau khi gọi. `BasicFileAttributes` không phải “khóa” file và không bảo đảm trạng thái không đổi cho các thao tác phía sau.

## 2. `size()` là kích thước theo byte, không phải ký tự

Đối với regular file, `attrs.size()` biểu diễn **kích thước file tính bằng byte** mà hệ thống tệp báo cáo; giá trị có kiểu `long`. `Files.size(path)` cũng trả về kích thước byte và có thể ném `IOException` nếu không lấy được.

Ba đơn vị thường bị nhầm:

- **Byte trên file**: giá trị từ `Files.size(path)` hoặc `attrs.size()` cho regular file.
- **Đơn vị `char` UTF-16**: `String.length()` trong Java, không phải số byte UTF-8.
- **Unicode code point**: số mã Unicode trong chuỗi, cũng không nhất thiết bằng byte hay `String.length()`.

Ví dụ chuỗi `"A😀"` khi được mã hóa bằng UTF-8:

| Đơn vị | Kết quả |
| --- | ---: |
| Byte UTF-8 | 5 |
| `String.length()` | 3 |
| `String.codePointCount(...)` | 2 |

Ký tự `A` chiếm 1 byte UTF-8; emoji ở ví dụ chiếm 4 byte. Emoji chiếm **hai đơn vị UTF-16** trong Java nên chuỗi có `length()==3`.

### Ví dụ 1 — quan sát size, type và fileKey

Chương trình tạo một file tạm trong máy đang chạy, ghi UTF-8 và đọc thuộc tính của file đó. Các phép so sánh không phụ thuộc vào đường dẫn tuyệt đối hay tên file tạm.

```java
// BasicAttributesDemo.java
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class BasicAttributesDemo {
    public static void main(String[] args) throws Exception {
        Path path = Files.createTempFile("attrs-", ".txt");
        String text = "A😀";

        try {
            Files.writeString(path, text, StandardCharsets.UTF_8);

            BasicFileAttributes attrs = Files.readAttributes(
                    path, BasicFileAttributes.class);

            System.out.println("regular=" + attrs.isRegularFile());
            System.out.println("directory=" + attrs.isDirectory());
            System.out.println("symbolicLink=" + attrs.isSymbolicLink());
            System.out.println("other=" + attrs.isOther());
            System.out.println("size=" + attrs.size());
            System.out.println("filesSize=" + Files.size(path));
            System.out.println("utf16Units=" + text.length());
            System.out.println("codePoints="
                    + text.codePointCount(0, text.length()));
            System.out.println("fileKeyAvailable="
                    + (attrs.fileKey() != null));
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
```

Biên dịch/chạy với JDK 17+:

```bash
javac -encoding UTF-8 BasicAttributesDemo.java
java BasicAttributesDemo
```

Trên một hệ thống tệp tạm thông thường có regular file và hỗ trợ ghi thành công, các dòng đầu là:

```text
regular=true
directory=false
symbolicLink=false
other=false
size=5
filesSize=5
utf16Units=3
codePoints=2
```

Dòng cuối **phụ thuộc filesystem/provider**:

```text
fileKeyAvailable=true
```

hoặc:

```text
fileKeyAvailable=false
```

Không in chính nội dung `fileKey` vào output mẫu vì kiểu, định dạng và tính sẵn có của nó **không cố định** giữa Windows, Linux, macOS và các filesystem khác.

Lời gọi `Files.writeString(...)` ở ví dụ sử dụng lựa chọn mặc định để ghi nội dung. Sau khi nó hoàn thành, `attrs.size()` và `Files.size(path)` có thể cùng báo 5 byte. Trong một ứng dụng có nhiều tiến trình truy cập file, hai lần truy vấn này vẫn có thể thấy kết quả khác nhau nếu dữ liệu thay đổi ở giữa.

## 3. `size()` không phải “dung lượng đang chiếm trên đĩa”

Phân biệt:

- **Logical file size**: độ dài nội dung file mà ứng dụng nhìn thấy theo byte; thường là kết quả `size()`.
- **Allocated space**: số block/extent thực sự được filesystem cấp phát trên thiết bị, có thể khác logical size.
- **Dung lượng volume**: thông tin tổng, còn trống hoặc có thể sử dụng của phân vùng, không phải kích thước một file.

Ví dụ một file nhỏ 1 byte vẫn có thể chiếm một block trên hệ thống tệp; một **sparse file** rất lớn có thể chỉ cấp phát block tại một số vùng; file được nén tại filesystem có thể chiếm vật lý ít hơn logical size.

`Files.size(path)` và `BasicFileAttributes.size()` **không phải API đo chính xác dung lượng cấp phát vật lý**. Không dùng chúng để suy số block đã chiếm, và cũng không suy lượng bộ nhớ cần thiết cho mọi biến thể giải nén/nén từ giá trị đó.

Ngoài ra:

- **Thư mục:** kích thước do `size()` báo cho directory là **implementation-specific, không được quy định nhất quán**. Nó không có nghĩa tổng kích thước tất cả file con.
- **Symbolic link:** nếu đọc thuộc tính của **chính link** (`NOFOLLOW_LINKS`), ý nghĩa `size()` tùy provider, chẳng hạn độ dài biểu diễn đường dẫn liên kết; không lấy làm dung lượng của target.
- **Socket, device, FIFO hoặc loại khác:** ý nghĩa `size()` có thể khác theo hệ thống; không áp quy tắc regular file một cách mù quáng.
- **File rỗng và file không tồn tại:** regular file rỗng có size bằng 0; một đường dẫn không tồn tại **không có bộ thuộc tính hợp lệ** để `Files.readAttributes` trả về bình thường.

Nếu cần tổng dung lượng của một **cây thư mục**, phải duyệt cây bằng API phù hợp và định nghĩa quy tắc riêng: có tính link không, có theo mount không, có đếm hard link lặp không, xử lý permission và file biến mất thế nào. Không dùng `Files.size(directory)` để thay thao tác đó.

## 4. Phân loại tệp với bốn cờ type

`BasicFileAttributes` phân loại theo những câu hỏi khác nhau:

| Cờ | Ý nghĩa |
| --- | --- |
| `isRegularFile()` | File thường, nội dung được quản lý như regular file |
| `isDirectory()` | Directory |
| `isSymbolicLink()` | Symbolic link |
| `isOther()` | Một loại file khác không nằm trong ba loại được nêu |

Đừng viết:

```java
if (!attrs.isRegularFile()) {
    // Sai nếu mặc định chắc chắn đây là thư mục!
}
```

Một đối tượng không phải regular file có thể là symbolic link, thiết bị, FIFO, socket hoặc file loại khác. Nếu bạn đọc metadata **có đi theo link**, cờ `isSymbolicLink()` có thể là `false` vì đối tượng bạn đang quan sát là **target** (ví dụ một regular file). Khi muốn phân loại mục link, phải đọc với `NOFOLLOW_LINKS`.

`isOther()` **không có nghĩa là file không tồn tại**. Nếu `Files.readAttributes` không lấy được thuộc tính của file vì đường dẫn không tồn tại, thông thường nó ném `NoSuchFileException`/`IOException`, thay vì tạo một `BasicFileAttributes` có `isOther()==true`.

### Ví dụ 2 — phân biệt file thường, thư mục và lỗi không tồn tại

```java
// FileTypeAttributesDemo.java
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class FileTypeAttributesDemo {
    private static void inspect(String name, Path path)
            throws java.io.IOException {
        try {
            BasicFileAttributes attrs =
                    Files.readAttributes(path, BasicFileAttributes.class);
            System.out.println(name + ":regular="
                    + attrs.isRegularFile() + ",directory="
                    + attrs.isDirectory());
        } catch (NoSuchFileException ex) {
            System.out.println(name + ":missing");
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("kinds-");
        Path regular = root.resolve("file.txt");
        Path folder = root.resolve("folder");
        Path missing = root.resolve("missing.txt");

        try {
            Files.writeString(regular, "OK", StandardCharsets.UTF_8);
            Files.createDirectory(folder);

            inspect("file", regular);
            inspect("folder", folder);
            inspect("absent", missing);
        } finally {
            Files.deleteIfExists(regular);
            Files.deleteIfExists(folder);
            Files.deleteIfExists(root);
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 FileTypeAttributesDemo.java
java FileTypeAttributesDemo
```

Kết quả trên hệ thống tệp tạm thông dụng, khi thao tác tạo tệp/thư mục thành công:

```text
file:regular=true,directory=false
folder:regular=false,directory=true
absent:missing
```

Khác với `java.io.File.isFile()`/`isDirectory()` vốn thường trả `false` khi không xác định được, `Files.readAttributes()` có thể cho biết **lý do thất bại qua `IOException`**. Tuy nhiên, không phải mọi provider đều phân loại lỗi thành đúng `NoSuchFileException`; ví dụ vẫn có thể ném một dạng `IOException` khác và truyền lỗi lên caller.

## 5. `Files.size()`, `File.length()` và `BasicFileAttributes.size()`

Có ba API dễ gặp khi tìm kích thước:

| Cách | Kiểu trả về | Hành vi đáng nhớ |
| --- | --- | --- |
| `new File(pathString).length()` | `long` | API cũ; file không tồn tại thường trả `0`; giá trị cho thư mục không xác định |
| `Files.size(path)` | `long` | API NIO.2, ném `IOException` khi truy vấn thất bại |
| `Files.readAttributes(path, BasicFileAttributes.class).size()` | `long` | Lấy kích thước cùng các thuộc tính type/fileKey, có thể ném `IOException` |

Vì file rỗng cũng có size `0`, không được dùng điều kiện:

```java
if (legacyFile.length() == 0) {
    // Kết luận "file rỗng" là thiếu điều kiện.
}
```

Đây là đoạn minh họa một sai lầm: file có thể không tồn tại, là thư mục, có lỗi truy vấn hoặc thật sự là regular file rỗng.

Cũng không có cách bảo đảm file không đổi chỉ bằng một chuỗi:

```java
if (Files.exists(path) && Files.isRegularFile(path)) {
    long size = Files.size(path);
    // File có thể đã thay đổi giữa các lệnh.
}
```

Kiểm tra trước rồi dùng sau có **TOCTOU** (*time of check to time of use*). Luôn xử lý lỗi của lần truy vấn hoặc mở file thật. Nếu phần xử lý phải đọc một version nhất quán, cần chính sách snapshot, lock hoặc các khả năng cụ thể của hệ thống tệp, không chỉ ba boolean ở trên.

## 6. `fileKey()` chính xác là gì?

Theo hợp đồng `BasicFileAttributes`, `fileKey()` trả một `Object` **nhận diện file** theo cơ chế do filesystem/provider cung cấp, hoặc `null` nếu không có khóa định danh.

```java
Object key = attrs.fileKey();

if (key == null) {
    // Provider không cung cấp file key trong trường hợp này.
}
```

Đây là **đoạn minh họa**; `attrs` là `BasicFileAttributes` có sẵn. Kiểu là `Object`, không phải `long`, `UUID` hoặc `String` bắt buộc. Không ép kiểu nó thành `Long` hay đoán cú pháp in ra. Trên một filesystem, key có thể được triển khai bằng số hiệu inode kết hợp device identifier; nơi khác có thể dùng file ID hoặc cấu trúc riêng; implementation quyết định hình dạng.

### Công dụng

`fileKey` hữu ích khi cần:

- Ghi nhận **danh tính được provider cung cấp** cho một file trong một đợt quét hoặc thao tác.
- Phát hiện **cùng một đối tượng tệp** xuất hiện qua nhiều đường dẫn, chẳng hạn hard link (khi provider hỗ trợ file keys).
- Hỗ trợ thuật toán duyệt cây và nhận biết đối tượng đã gặp trong tình huống có symbolic link, kết hợp cơ chế khác khi key không có.
- So sánh metadata **theo đúng phạm vi tính ổn định của provider**, ví dụ phát hiện một đường dẫn giờ đã trỏ đến object khác sau khi bị thay thế.

Nhưng phải tôn trọng các giới hạn.

### Giới hạn cần nhớ

1. **Có thể `null`.** Không được giả định luôn có key.
2. **Không phải ID toàn cục.** File key chỉ có ý nghĩa trong phạm vi filesystem/provider tương ứng; hai filesystem có thể phát ra các giá trị trông giống nhau.
3. **Không bảo đảm tồn tại vĩnh viễn.** Một file bị xóa có thể giải phóng ID để filesystem tái sử dụng; đừng dùng key như mã nhận diện hồ sơ lâu dài.
4. **Không gắn chặt với chuỗi path.** Đổi tên cùng filesystem có thể giữ file identity; thay file khác vào cùng pathname có thể thay `fileKey`.
5. **Không chứng minh tệp không thay đổi nội dung.** Một file có thể bị ghi lại mà key vẫn thuộc cùng đối tượng.
6. **Không phải token cấp quyền.** `fileKey` không thể thay thế xác thực, ủy quyền hay file handle được bảo vệ.
7. **Không dựa vào `toString()` của key làm chuẩn portable.** Format in ra là chi tiết implementation.
8. **Không đủ để ngăn race.** Sau khi lấy key, entry tại đường dẫn có thể bị thay thế bởi một thao tác đồng thời.

**Cần phân biệt `fileKey` với nội dung hash**: key nhận diện đối tượng do filesystem cung cấp; SHA-256 (ví dụ) nhận diện giá trị băm của **nội dung đã được đọc**. Hai file khác nhau có thể trùng nội dung và hash; cùng một file có thể thay nội dung theo thời gian trong khi key không đổi. Không dùng key để suy dữ liệu hai file giống nhau.

## 7. `Path.equals()`, `Files.isSameFile()` và `fileKey()` khác nhau

Ba câu hỏi hay bị trộn lẫn:

| API | Câu hỏi | Có nhất thiết truy cập filesystem? |
| --- | --- | --- |
| `pathA.equals(pathB)` | Hai đối tượng path có bằng nhau theo quy tắc provider không? | Không dùng làm phép kiểm tra file identity |
| `Files.isSameFile(pathA, pathB)` | Hai đường dẫn hiện nhận diện **cùng file** không? | Có thể truy vấn filesystem, có thể ném `IOException` |
| `attrsA.fileKey()` và `attrsB.fileKey()` | Provider có cấp khóa định danh và hai key có thể dùng để so sánh không? | Key đã được lấy từ các lần đọc metadata; không tạo bất biến |

Ví dụ:

```text
/data/report.txt
/data/aliases/current.txt  --(symbolic link)-->  /data/report.txt
/data/backup-hardlink.txt  --(hard link)------>  cùng đối tượng file
```

Hai `Path` có chuỗi khác nhau vẫn **có thể** đại diện cùng file thực. Trên filesystem hỗ trợ hard link, hai tên trỏ tới cùng một file object có thể có cùng `fileKey`. Một symbolic link **khi đi theo link** có thể dẫn đến cùng object và cùng key với target; **khi dùng NOFOLLOW_LINKS** thì bạn đang lấy thuộc tính và key của **chính link**, không phải target.

`Files.isSameFile(...)` thường là API thích hợp hơn để hỏi “hai path này có trỏ tới cùng file không?”. Nếu hai đối tượng `Path` **bằng nhau theo `equals`**, implementation được phép/ theo hợp đồng trả `true` ngay mà không kiểm tra file thực có tồn tại. Vì thế đừng dùng `Files.isSameFile(p, p)` để xác nhận chắc chắn `p` tồn tại; muốn kiểm tra file thực phải có thao tác khác phù hợp.

### Ví dụ 3 — file key, hard link và danh tính tệp

Chương trình sau thử tạo một **hard link** đến file tạm. Vì filesystem không phải nơi nào cũng cho tạo hard link, chương trình có hai nhánh được giải thích riêng; nó không giả định `fileKey()` luôn có giá trị.

```java
// FileKeyIdentityDemo.java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class FileKeyIdentityDemo {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("key-demo-");
        Path original = directory.resolve("original.txt");
        Path alias = directory.resolve("alias.txt");

        try {
            Files.writeString(original, "same object",
                    StandardCharsets.UTF_8);

            BasicFileAttributes first = Files.readAttributes(
                    original, BasicFileAttributes.class);

            System.out.println("fileKeyAvailable="
                    + (first.fileKey() != null));

            try {
                Files.createLink(alias, original);

                BasicFileAttributes second = Files.readAttributes(
                        alias, BasicFileAttributes.class);

                System.out.println("sameFile="
                        + Files.isSameFile(original, alias));

                if (first.fileKey() != null
                        && second.fileKey() != null) {
                    System.out.println("keyEqual="
                            + first.fileKey().equals(second.fileKey()));
                } else {
                    System.out.println("keyEqual=unavailable");
                }
            } catch (IOException | UnsupportedOperationException
                     | SecurityException ex) {
                System.out.println("hardLinkDemo=unavailable");
            }
        } finally {
            Files.deleteIfExists(alias);
            Files.deleteIfExists(original);
            Files.deleteIfExists(directory);
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 FileKeyIdentityDemo.java
java FileKeyIdentityDemo
```

Kết quả **phụ thuộc nền tảng**. Trên filesystem có hỗ trợ hard link, khi mọi thao tác thành công, phần stdout có dạng:

```text
fileKeyAvailable=true
sameFile=true
keyEqual=true
```

Trong một hệ thống cấp `fileKey` là `null` nhưng vẫn hỗ trợ hard link, nhánh này có thể là:

```text
fileKeyAvailable=false
sameFile=true
keyEqual=unavailable
```

Nếu môi trường không hỗ trợ hoặc từ chối tạo hard link:

```text
fileKeyAvailable=true
hardLinkDemo=unavailable
```

Giá trị của `fileKeyAvailable` trong nhánh cuối cũng có thể là `false`. Kết quả **không phải lời hứa stdout cố định trên mọi máy**. Chương trình đặc biệt không in đối tượng `fileKey` vì định dạng và ý nghĩa bên trong của nó không portable.

Trong trường hợp hai tên hard link đến cùng file, `Files.isSameFile()` thích hợp để kiểm tra danh tính hiện tại. So sánh hai key được đọc là thông tin hỗ trợ khi cả hai có giá trị và cùng filesystem, nhưng không thay thế xử lý lỗi và nguy cơ file bị thay đổi trong quá trình kiểm tra.

## 8. Symbolic link: đọc thuộc tính link hay target?

Đây là điểm then chốt đối với `isSymbolicLink()` và `fileKey()`.

Giả sử:

```text
report.txt                 (regular file)
report-link.txt ----------> report.txt   (symbolic link)
```

Hai cách đọc khác nhau:

```java
// Theo link: quan sát thuộc tính target.
BasicFileAttributes targetAttrs = Files.readAttributes(
        link, BasicFileAttributes.class);

// Không theo link: quan sát chính symbolic link.
BasicFileAttributes linkAttrs = Files.readAttributes(
        link, BasicFileAttributes.class,
        LinkOption.NOFOLLOW_LINKS);
```

Đây là đoạn minh họa với `link` là `Path` đã khai báo; import `LinkOption` cùng những lớp khác.

Nếu target là regular file:

- `targetAttrs.isRegularFile()` thường là `true`.
- `targetAttrs.isSymbolicLink()` thường là `false`, vì đang nhìn **target**.
- `linkAttrs.isSymbolicLink()` là `true` khi provider nhận diện link.
- `targetAttrs.size()` tương ứng regular target (khi có thể xác định).
- `linkAttrs.size()` là thuộc tính của **link** và không nên suy bằng kích thước target.
- `fileKey` có thể phân biệt đối tượng link với đối tượng target, nếu provider cung cấp khóa.

### Ví dụ 4 — quan sát `NOFOLLOW_LINKS`, kể cả broken link

Chương trình này **thử** tạo symlink tương đối trong thư mục tạm. Một số môi trường Windows cần quyền/tính năng nhất định, một số provider không hỗ trợ symlink; khi đó chương trình báo không khả dụng thay vì khẳng định tính năng tồn tại trên mọi máy.

```java
// SymbolicLinkAttributesDemo.java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class SymbolicLinkAttributesDemo {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("link-attrs-");
        Path target = dir.resolve("original.txt");
        Path link = dir.resolve("shortcut.txt");

        try {
            Files.writeString(target, "OK", StandardCharsets.UTF_8);

            try {
                Files.createSymbolicLink(
                        link, target.getFileName());

                BasicFileAttributes followed = Files.readAttributes(
                        link, BasicFileAttributes.class);
                BasicFileAttributes notFollowed = Files.readAttributes(
                        link, BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);

                System.out.println("followRegular="
                        + followed.isRegularFile());
                System.out.println("nofollowSymbolic="
                        + notFollowed.isSymbolicLink());

                Files.delete(target);

                System.out.println("brokenTargetExists="
                        + Files.exists(link));
                System.out.println("linkEntryExists="
                        + Files.exists(
                                link, LinkOption.NOFOLLOW_LINKS));
            } catch (IOException | UnsupportedOperationException
                     | SecurityException ex) {
                System.out.println("symlinkDemo=unavailable");
            }
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(target);
            Files.deleteIfExists(dir);
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 SymbolicLinkAttributesDemo.java
java SymbolicLinkAttributesDemo
```

Trong hệ thống hỗ trợ symbolic link và thực thi thành công mọi bước, stdout là:

```text
followRegular=true
nofollowSymbolic=true
brokenTargetExists=false
linkEntryExists=true
```

Nếu không tạo/truy vấn được link, chương trình có thể in:

```text
symlinkDemo=unavailable
```

Đây là ví dụ **có đầu ra phụ thuộc hệ thống**. Sau khi target bị xóa, `Files.exists(link)` (thường đi theo link) không thấy target, còn `Files.exists(link, NOFOLLOW_LINKS)` vẫn có thể xác nhận mục symlink còn đó. Đừng coi `Files.exists(link)==false` là đủ chứng minh bản thân link đã biến mất.

Khi một thư viện cần quy tắc “không theo symbolic link”, hãy truyền option thích hợp vào **chính thao tác có hỗ trợ** và hiểu rõ hành vi của provider. Việc kiểm tra trước với `NOFOLLOW_LINKS` không tự bảo vệ một thao tác mở sau đó khỏi thay đổi đồng thời (TOCTOU).

## 9. `fileKey` sau rename, replace, xóa và tạo lại

Một tên đường dẫn không phải danh tính bất biến của đối tượng. Có các tình huống:

| Thao tác | Pathname | File object / key có thể thế nào? |
| --- | --- | --- |
| Ghi đè nội dung trong cùng file đang mở | Path không đổi | File identity có thể giữ nguyên dù nội dung và size thay đổi |
| Rename/move trong cùng filesystem | Path đổi | Object thường được giữ, nhưng phải tôn trọng provider |
| Tạo hard link thêm tên mới | Hai path khác nhau | Có thể cùng file object và cùng key |
| Xóa file rồi tạo file mới trùng pathname | Path giống cũ | Thường là object mới; ID đã giải phóng có thể được tái sử dụng |
| Atomic replace tên đích bằng file mới | Tên đích có thể giữ nguyên | File object ở target có thể đổi; readers cũ có thể vẫn giữ handle cũ tùy OS |
| Di chuyển qua filesystem khác bằng copy/delete | Path đổi và file object có thể đổi | Key có thể đổi hoặc không so sánh trực tiếp được giữa provider |

Vì thế không nên lưu vĩnh viễn `(path, fileKey)` làm “ID duy nhất không đổi cho mọi thời đại” của dữ liệu nghiệp vụ. Nếu cần ID ổn định giữa di chuyển máy, sao lưu, phục hồi và thay thế file, ứng dụng nên tạo **định danh nghiệp vụ** riêng.

### `fileKey` có giúp phát hiện thay thế file không?

Có thể **hỗ trợ** ở một số provider. Ví dụ trước khi đọc file, lấy `fileKey` ban đầu; sau khi thực hiện một thao tác, đọc lại và so sánh. Nếu key thay đổi, đó có thể là tín hiệu file object đã bị thay thế. Nhưng:

- Nếu key là `null`, không có dữ kiện này.
- Một key có thể được tái sử dụng sau xóa/tạo mới.
- Nội dung cùng file có thể đổi mà key không đổi.
- Hai lần đọc thuộc tính không nguyên tử với thao tác của tiến trình khác.
- Chỉ so sánh `key.equals(...)` mà bỏ qua filesystem/provider là không đủ.

Do đó, phương pháp này phù hợp một số bài toán kiểm tra/giám sát có giới hạn, **không phải cơ chế khóa/authorization** cho thao tác nguy hiểm. Nếu cần chống việc thay đổi đối tượng đích giữa kiểm tra và sử dụng, hãy ưu tiên thao tác dựa trên handle/tài nguyên đã mở hoặc API filesystem bảo vệ phù hợp.

## 10. Thay đổi kích thước và metadata không phải dữ liệu bất biến

`BasicFileAttributes` là kết quả thu thập ở **một thời điểm**, không phải đối tượng tự động cập nhật khi file đổi. Nếu bạn gọi `readAttributes()` trước khi ghi, rồi giữ `attrs`, `attrs.size()` vẫn biểu diễn giá trị đã thu thập, không tự tăng theo file mới.

### Ví dụ 5 — so sánh thuộc tính cũ và mới

```java
// FileSizeSnapshotDemo.java
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class FileSizeSnapshotDemo {
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("size-snapshot-", ".txt");

        try {
            Files.writeString(file, "A", StandardCharsets.UTF_8);
            BasicFileAttributes before = Files.readAttributes(
                    file, BasicFileAttributes.class);

            Files.writeString(file, "ABCDE", StandardCharsets.UTF_8);
            BasicFileAttributes after = Files.readAttributes(
                    file, BasicFileAttributes.class);

            System.out.println("oldSize=" + before.size());
            System.out.println("newSize=" + after.size());
            System.out.println("currentSize=" + Files.size(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

```bash
javac FileSizeSnapshotDemo.java
java FileSizeSnapshotDemo
```

Stdout khi thao tác với regular file tạm thành công:

```text
oldSize=1
newSize=5
currentSize=5
```

`Files.writeString(file, text, UTF_8)` không có option bổ sung, nên với file đã có nó ghi dữ liệu theo các tùy chọn mặc định bao gồm truncate nội dung trước đó. `before` vẫn giữ kích thước cũ. Sự chênh lệch có thể xuất hiện khi một chương trình khác thay đổi file, hoặc khi một stream writer còn chứa dữ liệu trong bộ đệm.

Quan trọng: dù đọc `BasicFileAttributes` một lần có thể tiện hơn nhiều lời gọi thuộc tính riêng, nó cũng **không bảo đảm đọc một snapshot bất biến của hệ thống tệp trong toàn bộ vòng đời ứng dụng**.

## 11. Quyền truy cập và exception trong việc đọc metadata

`Files.readAttributes()` có thể thất bại vì file không còn tồn tại, thiếu quyền truy cập, mount/provider lỗi hoặc những điều kiện khác. Một mẫu xử lý phù hợp:

```java
// Mã minh họa, path là Path được cung cấp bởi caller.
try {
    BasicFileAttributes attrs = Files.readAttributes(
            path, BasicFileAttributes.class);

    if (attrs.isRegularFile()) {
        System.out.println("Size in bytes: " + attrs.size());
    } else {
        System.out.println("Not a regular file");
    }
} catch (NoSuchFileException ex) {
    System.out.println("Path missing at metadata read time");
} catch (AccessDeniedException ex) {
    System.out.println("Cannot read attributes due to access restrictions");
} catch (IOException ex) {
    System.out.println("Other I/O failure: " + ex.getMessage());
}
```

Mã trên **không độc lập**, cần import và phạm vi phương thức phù hợp. Các subtype được minh họa là tình huống thường gặp, nhưng một provider có thể ném `IOException` tổng quát cho một số loại lỗi. Nếu ứng dụng không có quyền hoặc file bị xóa sau khi đọc attributes, vẫn cần xử lý lỗi **khi mở/đọc thật**.

`Files.exists(path)` hoặc `Files.isRegularFile(path)` trả boolean hữu ích nhưng có thể không phân biệt đầy đủ “không tồn tại” với “không thể xác định”. Đặc biệt, `Files.exists(path)==false` và `Files.notExists(path)==false` **có thể cùng xảy ra** nếu filesystem không thể xác định trạng thái. Khi cần nguyên nhân lỗi để hiển thị cho người dùng hoặc retry, một thao tác `Files.readAttributes(...)` có exception thường phù hợp hơn chỉ dựa vào boolean.

### Quyền và loại file không phải một vấn đề

Một regular file có thể **không đọc được**; một symbolic link có thể dẫn đến target bị hạn chế; một thư mục có thể tồn tại nhưng không thể liệt kê. Việc nhận diện `isRegularFile()==true` không có nghĩa `Files.newInputStream(path)` chắc chắn thành công.

Với các hệ thống nhiều người dùng, metadata là dữ kiện **của hệ thống tệp**, không phải chính sách phân quyền nghiệp vụ. Không dùng `fileKey` làm bearer token hoặc chứng cứ cho phép truy cập; thao tác thật phải đi qua cơ chế authorization đúng.

## 12. Phân loại số lượng file trong thư mục và vai trò `fileKey`

Khi tính dung lượng hoặc thống kê file trong một cây thư mục, `fileKey` có thể giúp tránh **đếm trùng cùng một đối tượng** trong các hệ thống hỗ trợ hard link. Nhưng cần phân biệt hai kiểu bài toán:

- **Đếm đường dẫn/entry**: một file có hai hard link xuất hiện dưới hai tên, đếm thành hai **entry**.
- **Đếm đối tượng file khác nhau**: hai hard link có thể được coi một đối tượng và một kích thước logical, nhưng cách xử lý phải nhất quán.
- **Dung lượng cấp phát trên đĩa**: việc cộng logical `size()` không trả lời chính xác vì sparse files, nén, metadata và chính sách cấp phát.
- **Duyệt theo symlink**: nếu đi theo symlink, một đối tượng có thể được ghé nhiều lần và có thể gặp vòng liên kết.

Vì `fileKey()` có thể `null`, thuật toán đếm/khử trùng lặp cần **fallback**; không chỉ lưu `null` vào `Set` rồi kết luận mọi file thiếu key là một đối tượng. Các cách thay thế còn tùy mục tiêu: so sánh danh tính bằng `Files.isSameFile()` (có chi phí và exception), không đi theo link, hoặc đơn giản là đếm entries thay vì file objects.

Trong `Files.walkFileTree(...)`, lựa chọn **có theo symbolic link** hay không ảnh hưởng cách phân loại/duyệt và quản lý vòng lặp; không dùng `fileKey` tách rời hợp đồng của `FileVisitOption.FOLLOW_LINKS` rồi giả định an toàn với mọi provider.

## 13. Chọn cách lấy metadata phù hợp

| Bài toán | API gợi ý | Lưu ý |
| --- | --- | --- |
| Chỉ cần kích thước một regular file | `Files.size(path)` | Byte, có thể ném `IOException` |
| Cần size + type + identity provider | `Files.readAttributes(path, BasicFileAttributes.class)` | Gom thông tin, `fileKey()` có thể null |
| Cần metadata **của link** | `readAttributes(..., NOFOLLOW_LINKS)` | Khác metadata target |
| Hỏi “có phải symbolic link?” | `Files.isSymbolicLink(path)` | Không đồng nghĩa “target tồn tại” |
| Hỏi “có phải regular file?” | `Files.isRegularFile(path, options...)` | Có thể trả false khi không xác định |
| Hỏi hai path trỏ cùng file? | `Files.isSameFile(pathA, pathB)` | Có thể ném `IOException`, và có shortcut khi `Path.equals` |
| Hỏi tệp có quyền POSIX nào? | `Files.getPosixFilePermissions(...)` nếu hỗ trợ | Khác BasicFileAttributes, phụ thuộc provider |
| Hỏi owner/ACL | `FileOwnerAttributeView`, `AclFileAttributeView` khi hỗ trợ | Không mặc định có trên mọi filesystem |
| Muốn kích thước cây thư mục | Duyệt tree, thống nhất quy tắc cộng kích thước | Không dùng `size(directory)` |
| Muốn nhận biết thay đổi nội dung | Đọc dữ liệu và hash hoặc cơ chế version cụ thể | `fileKey` không phải content hash |
| Muốn bảo đảm mở file đúng quyền | Thực hiện thao tác mở theo contract và bắt exception | Không chỉ `isRegularFile()` rồi tin trạng thái không đổi |

Nếu cần thuộc tính nâng cao ngoài nhóm basic, Java có các file-attribute views như `PosixFileAttributes`, `DosFileAttributes`, `AclFileAttributeView`. Những API này **không phải phần bảo đảm của mọi provider** và có thể ném `UnsupportedOperationException` khi tính năng không có.

## 14. Các lỗi dễ mắc

| Hiểu lầm / lỗi | Vì sao sai | Hướng xử lý |
| --- | --- | --- |
| Nghĩ `size()` trả số ký tự | Kích thước regular file là byte | Phân biệt UTF-8, UTF-16 và code point |
| Dùng `size(directory)` để tính dung lượng thư mục | Giá trị của thư mục không được xác định nhất quán | Duyệt tree, quy định rõ link/hard link |
| Coi `File.length()==0` chắc là file rỗng | File không tồn tại cũng có thể cho 0 | Kiểm tra loại, thực hiện truy vấn có báo lỗi |
| Coi `isRegularFile()==false` nghĩa là directory | Còn symlink, special file, metadata không xác định | Kiểm tra từng loại và xử lý lỗi |
| Dùng `isOther()` để kiểm tra file missing | `isOther()` nói về loại đối tượng, không phải sự vắng mặt | Bắt lỗi `NoSuchFileException`/`IOException` |
| Đọc link không có `NOFOLLOW_LINKS` rồi kết luận đây là symlink | Mặc định có thể lấy thuộc tính target | Chỉ định `NOFOLLOW_LINKS` khi hỏi về link |
| Giả định `fileKey()` luôn có | Có thể trả `null` | Thiết kế fallback |
| Cast `fileKey()` sang `long`, lưu lâu dài như ID toàn cục | Kiểu là `Object`, ý nghĩa tùy provider, có thể tái sử dụng | Dùng đúng phạm vi và ID nghiệp vụ riêng |
| Dùng `key.toString()` làm định dạng trao đổi portable | Implementation tùy filesystem | Không phụ thuộc định dạng key |
| Dùng `fileKey` để phát hiện thay đổi nội dung | Một file thay dữ liệu có thể giữ nguyên identity | Hash/version dữ liệu theo yêu cầu |
| Đồng nhất `Path.equals()` với `Files.isSameFile()` | So sánh đường dẫn khác kiểm tra object | Chọn API theo câu hỏi |
| Lấy attributes một lần rồi giả định không bao giờ đổi | File có thể bị thay thế hoặc ghi sau đó | Xử lý snapshot/race và thao tác thật |
| Cộng mọi `attrs.size()` qua hard link | Có thể đếm trùng cùng file object | Quyết định thống kê entry hay object |
| Coi `Files.exists()==false` là chắc chắn file không tồn tại | Trạng thái đôi khi không xác định | Phân biệt `notExists` và exception khi truy vấn |
| Tin kiểm tra metadata có thể cấp quyền đọc | Metadata không thay thế authorization | Mở file và xử lý permission/I/O |

## 15. Bài tập thực hành

### Bài tập 1 — bảng phân loại

Viết chương trình tạo một file trống và một thư mục tạm. Đọc `BasicFileAttributes` của mỗi đường dẫn rồi hiển thị `isRegularFile`, `isDirectory`, `isSymbolicLink`, `isOther`. Giải thích tại sao **cờ `isOther` không đại diện cho đường dẫn không tồn tại**.

**Gợi ý:** Với file không tồn tại, thử `Files.readAttributes()` và bắt `NoSuchFileException`; không giả vờ nhận được một object attributes hợp lệ.

### Bài tập 2 — Unicode và số byte

Ghi `"A😀"` dưới dạng UTF-8, so `Files.size()`, `attrs.size()`, `String.length()` và `codePointCount(...)`. Sau đó ghi nội dung UTF-8 khác để thấy file size thay đổi theo số byte thực tế.

**Gợi ý:** Kích thước `"A😀"` khi ghi UTF-8 là **5 byte**, không phải 2 hoặc 3.

### Bài tập 3 — hard link

Nếu filesystem hỗ trợ, tạo hai đường dẫn hard link cùng một object. So sánh `Path.equals`, `Files.isSameFile`, `fileKey` (chỉ khi khác `null`). Vì sao chỉ một trong những phép này phản ánh chắc chắn **câu hỏi identity tại thời điểm truy vấn**?

**Gợi ý:** Hai tên khác nhau cho cùng object không làm hai `Path` có cùng cấu trúc.

### Bài tập 4 — symlink

Tạo một symlink trỏ tới regular file nếu quyền/provider cho phép. So sánh `Files.readAttributes` khi theo link và không theo link. Xóa target (giữ link), kiểm tra `Files.exists` với và không với `NOFOLLOW_LINKS`. Không lấy size link làm size file target.

### Bài tập 5 — thiết kế thống kê

Bạn được yêu cầu “tính tổng dung lượng các file trong một thư mục”. Trước khi viết vòng duyệt, giải thích cần làm rõ điều gì về: recursive hay một cấp, đi theo symbolic link, hard link được đếm mấy lần, lỗi quyền truy cập, file thay đổi đồng thời, logical bytes so với allocated bytes.

## 16. Câu hỏi tự kiểm tra

**1.** `Files.size(path)` và `BasicFileAttributes.size()` của regular file trả đơn vị gì?

**Đáp án:** **Byte**, kiểu `long`, không phải `char` UTF-16 hay Unicode code point.

**2.** `isRegularFile()==false` có chắc `isDirectory()==true` không?

**Đáp án:** Không. Có các loại khác như symbolic link và other file types; cũng phải xét việc bạn đang theo link hay không.

**3.** Dùng `size(directory)` để biết tổng dung lượng file bên trong có đúng không?

**Đáp án:** Không. Size của directory không được quy định nhất quán và không phải tổng size file con.

**4.** `fileKey()` có luôn là một số nguyên khác `null` không?

**Đáp án:** Không. Kiểu trả về là `Object` và có thể là `null`; representation do provider quyết định.

**5.** Nếu hai đường dẫn có `Path.toString()` khác nhau, chúng có thể trỏ cùng file không?

**Đáp án:** Có. Hard link, symbolic link hoặc cách đặt tên khác có thể dẫn đến cùng file thực.

**6.** Muốn hỏi về **mục symlink**, truyền tùy chọn gì khi đọc attributes?

**Đáp án:** `LinkOption.NOFOLLOW_LINKS` vào `Files.readAttributes(...)`.

**7.** Một symbolic link đã mất target thì `Files.exists(link)` và `Files.exists(link, NOFOLLOW_LINKS)` nhất thiết bằng nhau?

**Đáp án:** Không. Theo link có thể báo target không tồn tại; không theo link có thể xác nhận chính link vẫn còn.

**8.** File được sửa nội dung nhưng không bị thay thế object. `fileKey` nhất thiết đổi không?

**Đáp án:** Không. File identity và content khác nhau.

**9.** Vì sao không nên lưu `fileKey` lâu dài làm ID toàn cầu?

**Đáp án:** Nó không được bảo đảm trên mọi provider, không duy nhất giữa filesystem, có thể bị tái sử dụng và không phải ID nghiệp vụ ổn định.

**10.** `Files.isSameFile(p, p)` trả `true` có chứng minh `p` tồn tại?

**Đáp án:** Không nhất thiết. API có thể trả `true` ngay khi hai `Path` bằng nhau mà không truy vấn filesystem.

**11.** Sau khi gọi `Files.readAttributes`, một tiến trình khác có thể xóa/thay file trước khi bạn mở nó không?

**Đáp án:** Có. Metadata là thông tin tại thời điểm truy vấn, không loại bỏ race. Phải xử lý lỗi của thao tác thật.

**12.** Khi đếm file trong cây có hard link và symlink, tại sao phải thống nhất chính sách trước?

**Đáp án:** Một object có thể xuất hiện qua nhiều path. Đếm entries, logical file objects và allocated disk usage là những phép tính có ý nghĩa khác nhau.

**Tóm tắt:** Với Java NIO.2, `BasicFileAttributes` giúp lấy **kích thước byte**, **loại đối tượng tệp** và **fileKey** do provider cung cấp. Chú ý `NOFOLLOW_LINKS` khi muốn hỏi về symbolic link thay vì target; không dùng size directory để suy tổng dung lượng; không coi `fileKey` là ID toàn cầu hoặc content hash. Sử dụng `Files.isSameFile()` khi cần kiểm tra identity hiện tại và xử lý `IOException`/TOCTOU ở mọi thao tác thực tế.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-attributes-and-permissions-004">Đọc thuộc tính theo kiểu hoặc theo tên</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc thuộc tính theo kiểu hoặc theo tên trong Java NIO.2

Khi làm việc với tệp và thư mục, ngoài việc đọc nội dung, ứng dụng thường cần biết **kích thước (byte), thời điểm sửa đổi, loại tài nguyên, chủ sở hữu hoặc quyền truy cập**. Java NIO.2, thông qua `java.nio.file.Files` và `java.nio.file.attribute`, cung cấp hai cách quan trọng để đọc **nhiều thuộc tính của một `Path`**:

1. **Theo kiểu (typed):** `Files.readAttributes(path, BasicFileAttributes.class)` trả về một đối tượng có interface xác định tại thời điểm biên dịch.
2. **Theo tên (named):** `Files.readAttributes(path, "basic:size,lastModifiedTime")` trả về `Map<String,Object>` chứa những thuộc tính được yêu cầu bằng tên chuỗi.

Nếu chỉ cần **một** thuộc tính, có `Files.getAttribute(path, "basic:size")`. Mỗi cách có ưu điểm về tính rõ ràng, khả năng mở rộng và yêu cầu provider khác nhau.

Bài này nằm trong phần **Thuộc tính và quyền** của Java Core I/O, dùng Java 17+. Trọng tâm là đọc metadata **không làm thay đổi dữ liệu**, hiểu đúng view `basic`/`posix`/`dos`, tùy chọn symbolic link, khả năng hỗ trợ của filesystem và các tình huống lỗi.

## 1. File attributes là gì?

**File attributes** là thông tin mô tả tệp hoặc thư mục do hệ thống tệp/provider cung cấp, không phải nội dung byte của tệp. Ví dụ:

- `size`: kích thước được báo của tệp theo byte.
- `lastModifiedTime`: thời điểm hệ thống tệp ghi nhận lần sửa đổi cuối.
- `lastAccessTime`: thời điểm truy cập cuối, tùy khả năng hệ thống tệp.
- `creationTime`: thời điểm tạo; có thể không được hỗ trợ đầy đủ.
- `isRegularFile`, `isDirectory`, `isSymbolicLink`, `isOther`: phân loại đối tượng.
- `fileKey`: định danh do provider trả về, có thể là `null`.
- Với những filesystem hỗ trợ: `permissions`, `owner`, `group` của POSIX hoặc `hidden`/`readonly`/`archive`/`system` của DOS.

Đây là metadata, không phải dữ liệu văn bản. `Files.readAttributes` **không đọc toàn bộ nội dung file** giống `Files.readString` hoặc `Files.readAllBytes`. Với tệp lớn, một lần đọc metadata thường không yêu cầu nạp toàn bộ nội dung vào RAM; tuy nhiên nó vẫn có thể là **thao tác I/O** tới ổ đĩa, mạng hoặc filesystem provider.

### Tổng quan các API

| API | Kết quả | Khi dùng |
| --- | --- | --- |
| `Files.readAttributes(path, BasicFileAttributes.class)` | `BasicFileAttributes` | Cần metadata cơ bản và type safety |
| `Files.readAttributes(path, PosixFileAttributes.class)` | `PosixFileAttributes` | Cần POSIX permissions/owner/group và provider hỗ trợ |
| `Files.readAttributes(path, DosFileAttributes.class)` | `DosFileAttributes` | Cần DOS hidden/read-only/archive/system và provider hỗ trợ |
| `Files.readAttributes(path, "basic:size,isDirectory")` | `Map<String,Object>` | Cần chọn thuộc tính theo danh sách tên |
| `Files.readAttributes(path, "posix:*")` | `Map<String,Object>` | Cần tập thuộc tính POSIX từ provider hỗ trợ |
| `Files.getAttribute(path, "basic:size")` | `Object` (thực tế là `Long`) | Cần một thuộc tính xác định |
| `Files.size(path)` | `long` | Cách tiện lợi nếu chỉ cần kích thước |
| `Files.getLastModifiedTime(path)` | `FileTime` | Cách tiện lợi nếu chỉ cần thời gian sửa đổi |
| `Files.getOwner(path)` | `UserPrincipal` | Cách tiện lợi nếu cần owner và provider hỗ trợ |

**Điểm mấu chốt:** Từ “theo kiểu” và “theo tên” nói về **cách Java biểu diễn và yêu cầu thuộc tính**, không phải hai loại file khác nhau.

## 2. Đọc theo kiểu: Files.readAttributes(Path, Class<A>, ...)

Chữ ký quan trọng (rút gọn):

```java
<A extends BasicFileAttributes> A readAttributes(
        Path path,
        Class<A> type,
        LinkOption... options) throws IOException
```

Cách dùng cơ bản:

```java
BasicFileAttributes attrs =
        Files.readAttributes(path, BasicFileAttributes.class);

long bytes = attrs.size();
boolean regular = attrs.isRegularFile();
FileTime modified = attrs.lastModifiedTime();
```

Đây là **mảnh mã**: cần import `java.nio.file.*` và `java.nio.file.attribute.*`; `path` đã được xác định và phải xử lý `IOException`.

Đặc điểm:

- Tham số `Class<A>` chỉ rõ kiểu đối tượng metadata cần nhận.
- Trả về đối tượng thực thi interface thuộc tính tương ứng; lập trình viên gọi phương thức có kiểu như `size()` hoặc `isDirectory()` thay vì tự ép kiểu từ `Object`.
- `BasicFileAttributes.class` được mọi triển khai NIO.2 hỗ trợ ở mức giao diện cơ bản. Các kiểu mở rộng như `PosixFileAttributes` và `DosFileAttributes` **không được bảo đảm hỗ trợ trên mọi provider**.
- Cách đọc này yêu cầu **một lần đọc nhiều thuộc tính** qua provider (*bulk operation*). Điều đó **không bảo đảm tất cả trường được đọc nguyên tử** so với thay đổi đồng thời của filesystem.
- Đối tượng `attrs` mô tả kết quả tại thời điểm đọc. Không nên coi `attrs.size()` là giá trị “tự cập nhật” khi file tiếp tục bị ghi. Muốn lấy thông tin mới, đọc lại metadata.

### Các thành viên chính của BasicFileAttributes

| Phương thức | Kiểu trả về | Ý nghĩa |
| --- | --- | --- |
| `size()` | `long` | Kích thước file theo byte; ý nghĩa với loại không phải regular file phụ thuộc provider |
| `isRegularFile()` | `boolean` | Có phải file thường |
| `isDirectory()` | `boolean` | Có phải thư mục |
| `isSymbolicLink()` | `boolean` | Có phải symbolic link — phụ thuộc cách follow link |
| `isOther()` | `boolean` | Loại khác file thường/thư mục/symbolic link |
| `creationTime()` | `FileTime` | Thời điểm tạo, có thể dùng giá trị dự phòng khi filesystem không hỗ trợ |
| `lastModifiedTime()` | `FileTime` | Thời điểm sửa nội dung/metadata theo provider |
| `lastAccessTime()` | `FileTime` | Thời điểm truy cập cuối nếu có |
| `fileKey()` | `Object`, có thể `null` | Định danh đối tượng file nếu provider cung cấp |

`FileTime` không phải `long` mặc định. Bạn có thể dùng `toMillis()` hoặc `toInstant()` khi cần chuyển đổi, nhưng phải nhớ độ phân giải, chính sách cập nhật thời gian và vùng thời gian hiển thị khác nhau giữa filesystem.

**Đừng suy diễn:** `creationTime()` có giá trị không có nghĩa filesystem chắc chắn lưu được thời điểm tạo thực; Java cho phép giá trị mặc định/giá trị thay thế khi thuộc tính thời gian không được hỗ trợ. `fileKey()` có thể `null` và không phải mã định danh bền vững vĩnh viễn qua xóa/tạo lại.

### Chọn type khi metadata nâng cao

`PosixFileAttributes` mở rộng `BasicFileAttributes`, thêm:

```java
PosixFileAttributes posix =
        Files.readAttributes(path, PosixFileAttributes.class);

Set<PosixFilePermission> permissions = posix.permissions();
UserPrincipal owner = posix.owner();
GroupPrincipal group = posix.group();
```

Đây là **mảnh mã chạy trên provider có POSIX attribute view**, không mặc định portable lên mọi Windows hoặc ZIP filesystem. Cần import `java.nio.file.attribute.*`, `java.nio.file.*` và xử lý `IOException`/`UnsupportedOperationException` khi phù hợp.

Tương tự, `DosFileAttributes` mở rộng `BasicFileAttributes` và có các boolean `isHidden()`, `isReadOnly()`, `isArchive()` và `isSystem()`. Không được giả định rằng tệp trên mọi hệ điều hành đều có những cờ DOS này.

## 3. Đọc theo tên: Files.readAttributes(Path, String, ...)

Chữ ký:

```java
Map<String, Object> values =
        Files.readAttributes(path, "basic:size,lastModifiedTime");
```

Mảnh mã cần `java.nio.file.Files`, `java.nio.file.Path`, `java.util.Map` và xử lý `IOException`.

Tham số chuỗi có cú pháp:

```text
[view-name:]attribute-name[,attribute-name...]

Ví dụ:
size
basic:size
basic:size,isRegularFile,lastModifiedTime
size,lastModifiedTime
basic:*
posix:permissions,owner,group
posix:*
dos:hidden,readonly,archive,system
```

Ý nghĩa:

- `view-name` là tên **FileAttributeView** như `basic`, `posix` hoặc `dos`.
- Nếu bỏ `view-name`, Java hiểu là `basic`. Vì vậy `"size"` tương đương cách yêu cầu `"basic:size"` trong ngữ cảnh default basic view.
- Với `"basic:size,isDirectory"`, phần `basic:` áp dụng cho **toàn danh sách** ở phía sau, không phải riêng `size`.
- Nhiều thuộc tính được phân tách bằng dấu phẩy.
- Ký hiệu `*` có nghĩa chọn **tất cả thuộc tính mà view đó hỗ trợ**. `"*"` mặc định là `"basic:*"`.
- Kết quả là `Map<String,Object>`, các **key là tên thuộc tính** như `"size"` hoặc `"isDirectory"`, **không kèm `"basic:"` ở đầu key**.
- Những thuộc tính không được hỗ trợ có thể không xuất hiện trong map theo hợp đồng; tên không hợp lệ hoặc view không hỗ trợ có thể gây ngoại lệ.

Ví dụ:

```java
Map<String, Object> attrs =
        Files.readAttributes(path, "basic:size,isRegularFile");

long size = (Long) attrs.get("size");
boolean regular = (Boolean) attrs.get("isRegularFile");
```

Đây là **mảnh mã**; giá trị của `size` trong basic view có kiểu boxed `Long`, của `isRegularFile` là `Boolean`. Đừng ép sang `Integer` chỉ vì size nhỏ. `Map.get(...)` có thể trả `null` khi key không có; với những thuộc tính/provider không chắc chắn, kiểm tra `containsKey(...)` và kiểu dữ liệu trước khi ép.

### Vì sao map trả Object?

Cùng một view có thuộc tính với nhiều kiểu:

| Tên khóa basic | Java object thường nhận |
| --- | --- |
| `size` | `Long` |
| `lastModifiedTime` | `FileTime` |
| `creationTime` | `FileTime` |
| `isDirectory` | `Boolean` |
| `isRegularFile` | `Boolean` |
| `fileKey` | `Object` hoặc `null` nếu không có |

Với `posix:permissions`, giá trị là `Set<PosixFilePermission>`; `posix:owner` là `UserPrincipal`; `posix:group` là `GroupPrincipal`. Cần hiểu schema của attribute view trước khi sử dụng kết quả.

Ưu điểm của named API là có thể chọn theo cấu hình lúc chạy, đặc biệt khi chương trình cần tùy chọn các field hoặc hỗ trợ nhiều provider. Nhược điểm là lỗi chính tả tên field có thể chỉ được phát hiện khi chạy và cần ép kiểu.

### Có nên in map trực tiếp?

Bạn có thể dùng `System.out.println(attrs)` để quan sát, nhưng đừng coi thứ tự key trong `Map` là thứ tự bảo đảm. Với dữ liệu cần output kiểm thử ổn định, lấy từng key riêng, hoặc sắp xếp tên key nếu muốn in danh sách.

## 4. Chỉ cần một thuộc tính: Files.getAttribute(Path, String, ...)

`Files.getAttribute` trả về **một** giá trị dưới dạng `Object`:

```java
long bytes = (Long) Files.getAttribute(path, "basic:size");
FileTime modified =
        (FileTime) Files.getAttribute(path, "lastModifiedTime");
boolean directory = (Boolean) Files.getAttribute(path, "basic:isDirectory");
```

Mảnh mã cần import `java.nio.file.*` và `java.nio.file.attribute.FileTime`, xử lý `IOException`.

Sự khác nhau:

| Trường hợp | Chọn API hợp lý |
| --- | --- |
| Nhiều thuộc tính basic cần sử dụng trong code Java có kiểu tĩnh | `readAttributes(path, BasicFileAttributes.class)` |
| Cần chọn vài tên theo chuỗi/cấu hình | `readAttributes(path, "basic:size,isDirectory")` |
| Cần đọc đúng một thuộc tính đặc thù | `getAttribute(path, "view:name")` |
| Chỉ muốn kích thước file | `Files.size(path)` |
| Chỉ muốn thời điểm sửa đổi | `Files.getLastModifiedTime(path)` |

`Files.size(path)` trả `long` nên tránh phải cast `Object`. Nó là API tiện ích dễ đọc cho trường hợp chỉ muốn kích thước. Nhưng đừng gọi `Files.size(...)` liên tiếp với nhiều getter rồi giả định tất cả giá trị được lấy từ cùng một thời điểm hệ thống tệp: file có thể thay đổi giữa các lệnh.

## 5. Ví dụ hoàn chỉnh Java 17+: so sánh typed, named và single attribute

Tạo file `AttributeReadDemo.java`:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

public class AttributeReadDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("attrs-", ".txt");

        try {
            Files.writeString(file, "Hello", StandardCharsets.UTF_8);

            BasicFileAttributes typed =
                    Files.readAttributes(file, BasicFileAttributes.class);

            Map<String, Object> named = Files.readAttributes(
                    file, "basic:size,isRegularFile,isDirectory");

            long onlySize =
                    (Long) Files.getAttribute(file, "basic:size");

            System.out.println("typed size: " + typed.size());
            System.out.println("typed regular: " + typed.isRegularFile());
            System.out.println("named size: " + named.get("size"));
            System.out.println("named regular: "
                    + named.get("isRegularFile"));
            System.out.println("named directory: "
                    + named.get("isDirectory"));
            System.out.println("single size: " + onlySize);
            System.out.println("named keys: " + named.size());
            System.out.println("basic supported: "
                    + Files.getFileStore(file)
                            .supportsFileAttributeView("basic"));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Chạy với JDK 17+:

```bash
javac AttributeReadDemo.java
java AttributeReadDemo
```

Stdout đã đối chiếu với Java trên filesystem bình thường:

```text
typed size: 5
typed regular: true
named size: 5
named regular: true
named directory: false
single size: 5
named keys: 3
basic supported: true
```

Giải thích:

1. `Files.createTempFile(...)` thực sự tạo một file tạm có tên riêng, nên demo tránh ghi đè file của người dùng.
2. `Files.writeString(file, "Hello", UTF_8)` ghi đúng **5 byte ASCII/UTF-8**.
3. Typed overload trả `BasicFileAttributes`; `typed.size()` là kiểu `long` và `typed.isRegularFile()` là `boolean`.
4. Named overload chỉ yêu cầu ba trường, vì vậy map có đúng ba key được cung cấp trên basic view.
5. `getAttribute(file, "basic:size")` trả `Object`, được ép sang `Long` rồi auto-unbox về `long`.
6. Basic file attribute view là phần được bảo đảm trên các provider NIO.
7. `finally` xóa duy nhất file tạm do demo tạo. Nếu file bị tiến trình khác sửa/xóa giữa chừng, hoặc filesystem từ chối I/O, chương trình có thể báo lỗi; những điều đó không phải hành vi mà bài demo cố tái hiện.

**Lưu ý về “cùng kết quả”:** trong ví dụ không có tiến trình khác sửa file sau khi `writeString`, cả ba cách báo 5 byte. Trong hệ thống đang có ghi đồng thời, mỗi lần đọc có thể thấy trạng thái khác nhau. Đọc nhiều thuộc tính trong một lời gọi không được Java bảo đảm là snapshot nguyên tử so với thao tác filesystem khác.

## 6. LinkOption.NOFOLLOW_LINKS: thuộc tính của link hay của đích?

Filesystem có thể chứa **symbolic link**. Một `Path` trỏ tới symlink thực tế có hai đối tượng liên quan:

```text
Path: alias.txt  ----symbolic link---->  actual.txt
       |                                  |
       | metadata của chính link          | metadata của file đích
       |                                  |
       +-- NOFOLLOW_LINKS                 +-- theo link (mặc định)
```

Các overload đọc thuộc tính nhận `LinkOption... options`. Mặc định, khi gặp symbolic link, Java thường **đi theo link tới đích** và đọc thuộc tính đích. Để yêu cầu metadata của chính symbolic link:

```java
BasicFileAttributes linkAttrs = Files.readAttributes(
        symbolicLink, BasicFileAttributes.class,
        LinkOption.NOFOLLOW_LINKS);

Map<String, Object> linkNamed = Files.readAttributes(
        symbolicLink, "basic:isSymbolicLink,size",
        LinkOption.NOFOLLOW_LINKS);

Object value = Files.getAttribute(
        symbolicLink, "basic:isSymbolicLink",
        LinkOption.NOFOLLOW_LINKS);
```

Đây là mảnh mã; `symbolicLink` là `Path` thực sự trỏ tới một link; cần import `java.nio.file.*`, `java.nio.file.attribute.*` và `java.util.Map`. Nếu hệ thống không hỗ trợ symbolic link, quyền tạo link bị giới hạn, hoặc link bị thay đổi trong lúc chạy, phải xử lý lỗi phù hợp.

Trong điều kiện link tồn tại và trỏ tới file thường:

| Cách đọc | `isRegularFile()` | `isSymbolicLink()` |
| --- | ---: | ---: |
| `Files.readAttributes(link, BasicFileAttributes.class)` | Thường `true` | Thường `false`, vì đang xem **đích** |
| `Files.readAttributes(link, BasicFileAttributes.class, NOFOLLOW_LINKS)` | Thường `false` | `true`, vì đang xem **link** |

Không được hiểu rằng `NOFOLLOW_LINKS` tự động chặn mọi loại filesystem traversal hoặc chống tất cả TOCTOU/symlink race. Nó là tùy chọn **chỉ dẫn cách xử lý link cho lời gọi đó**, không phải sandbox bảo mật toàn diện.

### Ví dụ có thể chạy khi provider hỗ trợ symlink

```java
Path dir = Files.createTempDirectory("links-");
Path target = dir.resolve("target.txt");
Path link = dir.resolve("alias.txt");

try {
    Files.writeString(target, "AB", StandardCharsets.UTF_8);
    Files.createSymbolicLink(link, target.getFileName());

    BasicFileAttributes followed =
            Files.readAttributes(link, BasicFileAttributes.class);
    BasicFileAttributes notFollowed = Files.readAttributes(
            link, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

    System.out.println("followed regular: " + followed.isRegularFile());
    System.out.println("link itself: " + notFollowed.isSymbolicLink());
} finally {
    Files.deleteIfExists(link);
    Files.deleteIfExists(target);
    Files.deleteIfExists(dir);
}
```

Đây là **mảnh mã**, cần import `java.nio.file.*`, `java.nio.file.attribute.BasicFileAttributes`, `java.nio.charset.StandardCharsets` và xử lý `IOException`. Trên filesystem hỗ trợ và cho phép tạo symbolic link, hai dòng output là `true` và `true`. Trên một số Windows hoặc provider khác, `createSymbolicLink` có thể không được hỗ trợ hoặc cần quyền đặc biệt; vì vậy ví dụ **không** tuyên bố portable về quyền tạo link. Trong thực tế, dùng `try/finally` và dọn chính link chứ không duyệt theo link ra ngoài thư mục.

## 7. FileAttributeView: basic, posix, dos và owner

Tên `basic`, `posix`, `dos` trong cú pháp chuỗi là **tên các attribute view**, không phải file extension hay chế độ mã hóa nội dung. Chúng tương ứng những nhóm thuộc tính mà provider hỗ trợ.

### basic: phổ biến nhất

`BasicFileAttributes` gồm kích thước, loại file, các mốc thời gian và fileKey. Mọi NIO provider phải hỗ trợ basic view. Ví dụ:

```java
BasicFileAttributes a =
        Files.readAttributes(path, BasicFileAttributes.class);
Map<String, Object> b =
        Files.readAttributes(path, "basic:size,lastModifiedTime");
```

Bạn có thể dùng cả hai trên file và thư mục; tuy nhiên `size()` cho đối tượng không phải regular file có ý nghĩa do provider quy định. Ví dụ không được dựa vào `size()` của thư mục để xác định số lượng file con.

### posix: permissions, owner, group

Nếu provider hỗ trợ `posix`:

```java
PosixFileAttributes a =
        Files.readAttributes(path, PosixFileAttributes.class);

Set<PosixFilePermission> perms = a.permissions();
UserPrincipal owner = a.owner();
GroupPrincipal group = a.group();

Map<String, Object> named =
        Files.readAttributes(path, "posix:permissions,owner,group");
```

Đây là mảnh mã, cần import `java.nio.file.*`, `java.nio.file.attribute.*`, `java.util.*`, xử lý `IOException`. Một provider không hỗ trợ view này có thể ném `UnsupportedOperationException` khi bạn cố đọc.

`permissions()` trả một tập `PosixFilePermission`; có thể hiển thị bằng `PosixFilePermissions.toString(perms)` dưới dạng như `rw-r-----`. Tập quyền phản ánh metadata hệ thống tệp, không cam kết ứng dụng chắc chắn được thao tác: ACL, chính sách sandbox, owner, mount option hoặc thay đổi đồng thời vẫn có thể tác động.

### dos: hidden, read-only, archive, system

Nếu view `dos` được hỗ trợ:

```java
DosFileAttributes a =
        Files.readAttributes(path, DosFileAttributes.class);

boolean hidden = a.isHidden();
boolean readOnly = a.isReadOnly();

Map<String, Object> named =
        Files.readAttributes(path, "dos:hidden,readonly");
```

Đây là mảnh mã; cần `java.nio.file.*`, `java.nio.file.attribute.*` và `java.util.Map`, xử lý `IOException`. Trong named view, key thường là `"hidden"` và `"readonly"`, không phải `"isHidden"`/`"isReadOnly"`. Lưu ý sự khác nhau giữa **tên thuộc tính trong chuỗi** và **tên phương thức Java**!

POSIX và DOS không phải hai chế độ mà mọi OS có đầy đủ. Provider có thể hỗ trợ một, cả hai hoặc có ràng buộc riêng. Hãy kiểm tra khả năng hỗ trợ và giải quyết graceful fallback theo nhu cầu nghiệp vụ.

### owner, ACL, user-defined attributes

Các nhóm thuộc tính khác như `FileOwnerAttributeView`, `AclFileAttributeView` và `UserDefinedFileAttributeView` được truy cập qua API view phù hợp, ví dụ:

```java
FileOwnerAttributeView ownerView =
        Files.getFileAttributeView(path, FileOwnerAttributeView.class);

if (ownerView != null) {
    UserPrincipal owner = ownerView.getOwner();
}
```

Mảnh mã cần `java.nio.file.*` và `java.nio.file.attribute.*`. `getFileAttributeView(...)` có thể trả `null` khi không hỗ trợ. **Không** nghĩ rằng tất cả attribute view đều là một kiểu con của `BasicFileAttributes`: overload typed `readAttributes(Path, Class<A>)` chỉ chấp nhận `A extends BasicFileAttributes`, vì vậy `AclFileAttributeView.class` không phải đối số kiểu hợp lệ cho overload đó. Với ACL, dùng `Files.getFileAttributeView(path, AclFileAttributeView.class)` rồi gọi `getAcl()` nếu được hỗ trợ.

## 8. Xác định provider hỗ trợ view nào

Một chương trình portable cần **phát hiện khả năng hỗ trợ trước khi phụ thuộc vào POSIX/DOS**. Hai API hữu ích:

```java
Set<String> views = path.getFileSystem()
        .supportedFileAttributeViews();

boolean posixOnStore = Files.getFileStore(path)
        .supportsFileAttributeView("posix");
```

Đây là mảnh mã; cần `java.nio.file.*` và `java.util.Set`. `supportedFileAttributeViews()` mô tả các view mà filesystem hỗ trợ; `FileStore.supportsFileAttributeView("posix")` hỏi về hỗ trợ của file store chứa file đó. Hai tầng này cung cấp thông tin khả năng hỗ trợ nhưng **không** bảo đảm đọc file cụ thể chắc chắn thành công (quyền, đường dẫn bị xóa, race, lỗi I/O có thể xảy ra).

Mẫu có fallback:

```java
BasicFileAttributes basic =
        Files.readAttributes(path, BasicFileAttributes.class);

FileStore store = Files.getFileStore(path);
if (store.supportsFileAttributeView("posix")) {
    PosixFileAttributes posix =
            Files.readAttributes(path, PosixFileAttributes.class);
    // Xử lý owner/group/permissions nếu thao tác thành công.
} else {
    // Chỉ dùng basic attributes hoặc chiến lược khác.
}
```

Đây là mảnh mã, cần import `java.nio.file.*` và `java.nio.file.attribute.*`, xử lý `IOException`. Nếu một view có thể biến mất/không hoạt động giữa lúc kiểm tra và sử dụng, vẫn xử lý ngoại lệ ở lời gọi thực sự.

Không đặt một chuỗi `"posix:*"` làm mặc định cho mọi runtime rồi bắt mọi lỗi bằng `catch (Exception ignored)`. Điều đó có thể che các lỗi quyền/đường dẫn quan trọng và làm hệ thống tưởng đã kiểm tra bảo mật thành công.

## 9. Ngoại lệ khi đọc thuộc tính

Đọc metadata vẫn là I/O, do đó có thể lỗi:

| Lỗi/ngoại lệ | Nguyên nhân phổ biến |
| --- | --- |
| `NoSuchFileException` | File hoặc thành phần đường dẫn không tồn tại tại thời điểm đọc |
| `AccessDeniedException` | Provider/hệ điều hành từ chối truy cập |
| `IOException` | Lỗi I/O tổng quát, file system/network provider, v.v. |
| `UnsupportedOperationException` | Provider không hỗ trợ attribute type/view được yêu cầu |
| `IllegalArgumentException` | Chuỗi attribute name/syntax không hợp lệ |
| `SecurityException` | Môi trường áp đặt kiểm tra bảo mật và từ chối thao tác |

Không phải mọi provider sẽ ném đúng một subtype cố định trong mọi tình huống. Ví dụ unsupported attribute name có thể bị từ chối, trong khi một thuộc tính hợp lệ nhưng không được cung cấp có thể bị bỏ qua khỏi kết quả map. Khi thiếu key, đừng ép kiểu vô điều kiện.

Dạng xử lý có thể dùng:

```java
try {
    BasicFileAttributes attrs =
            Files.readAttributes(path, BasicFileAttributes.class);
    System.out.println(attrs.size());
} catch (NoSuchFileException e) {
    // File đã bị xóa, hoặc chưa tồn tại.
} catch (AccessDeniedException e) {
    // Không đủ quyền để đọc metadata.
} catch (IOException e) {
    // Một lỗi I/O khác.
}
```

Đây là mảnh mã; cần `java.nio.file.*` và `java.nio.file.attribute.*`. Hai subtype cụ thể đứng trước `IOException`. Nếu ứng dụng thực sự dùng POSIX, cũng cần xử lý trường hợp unsupported view.

`Files.exists(path)` có thể hữu ích cho UI hoặc luồng điều khiển mềm, nhưng **không** nên dùng `exists` như điều kiện bảo đảm lời gọi `readAttributes` tiếp theo thành công. Filesystem có thể thay đổi giữa hai thao tác (*TOCTOU*).

## 10. Thời gian và tính nhất quán của metadata

Các timestamp `creationTime`, `lastModifiedTime` và `lastAccessTime` là `FileTime`. Có thể chuyển:

```java
FileTime time = attrs.lastModifiedTime();
Instant instant = time.toInstant();
long millis = time.toMillis();
```

Đây là mảnh mã với biến `attrs` đã đọc; cần import `java.nio.file.attribute.FileTime` và `java.time.Instant`. Khi định dạng hiển thị theo múi giờ, sử dụng `Instant` và `ZoneId` thích hợp; không tự mặc định timestamp là chuỗi theo giờ địa phương.

Một số giới hạn:

- Độ chính xác/độ phân giải timestamp phụ thuộc filesystem (nanosecond, millisecond, second hoặc kém hơn).
- Một filesystem không hỗ trợ đầy đủ creation/access time có thể trả giá trị mặc định thay thế.
- Các attribute có thể đến từ cache của OS/network filesystem. Không được coi metadata là “real-time tuyệt đối” trên mọi provider.
- Nhiều trường đọc trong một lời gọi `readAttributes` **có thể không phải một snapshot nguyên tử** khi có hoạt động file system đồng thời; đây là chi tiết triển khai.
- Nếu file lớn đang được ghi, kích thước và lastModifiedTime có thể đổi ngay sau khi đọc.
- `fileKey()` có thể hỗ trợ nhận biết cùng file trong những điều kiện nhất định, nhưng có thể `null`, không bảo đảm duy nhất vĩnh viễn sau khi file bị xóa/tạo lại.

Khi nghiệp vụ đòi hỏi tính nhất quán cao (ví dụ trước khi xử lý file nhạy cảm hoặc kiểm tra hash), phải thiết kế cách mở file/khóa/đối chiếu nội dung phù hợp thay vì chỉ dựa vào những kiểm tra metadata trước đó.

## 11. So sánh hai overload cho các tác vụ điển hình

| Tiêu chí | Typed `readAttributes(Path, Class<A>)` | Named `readAttributes(Path, String)` |
| --- | --- | --- |
| Giá trị trả về | `BasicFileAttributes` hoặc subtype | `Map<String,Object>` |
| Truy cập field | Gọi phương thức như `size()` | Tra key như `get("size")` |
| Type safety | Cao hơn: compiler biết kiểu phương thức | Thấp hơn: cần cast/kiểm tra kiểu |
| Chọn field | Đọc tập thuộc tính mà interface định nghĩa | Chọn danh sách tên yêu cầu |
| Tên thuộc tính | Theo method API | Theo tên trong attribute view |
| Phát hiện sai tên | Lỗi method dễ phát hiện lúc biên dịch | Dễ sai chuỗi, lỗi runtime |
| POSIX/DOS | Dùng subtype nếu provider hỗ trợ | Dùng `posix:...`/`dos:...` nếu hỗ trợ |
| Kết quả khi không hỗ trợ | Unsupported type/view có thể ném exception | Unsupported view/name có thể ném exception; unsupported field có thể vắng |
| Bulk operation | Có | Có |
| Atomic metadata snapshot | Không được đảm bảo mọi provider | Không được đảm bảo mọi provider |

**Gợi ý thực hành:**

- Code Java cần dùng vài trường cơ bản và muốn compiler hỗ trợ: dùng **typed** `BasicFileAttributes`.
- Công cụ thống kê hoặc xuất metadata theo cấu hình tùy chọn: dùng **named** API, song có schema rõ ràng cho các key.
- Chỉ cần một trường: dùng API tiện ích `Files.size` hoặc `getAttribute`.
- Muốn sửa metadata thay vì đọc: dùng `Files.setAttribute`, `Files.setLastModifiedTime` hoặc attribute view tương ứng; đó là **một nhiệm vụ khác**, có thể thay đổi filesystem và cần quyền ghi.

## 12. Ví dụ tình huống: xuất báo cáo kích thước và loại tệp

Một chương trình quét thư mục có thể dùng `walkFileTree`. `FileVisitor.visitFile(Path, BasicFileAttributes)` **đã được truyền metadata theo kiểu `BasicFileAttributes`** cho entry đang thăm, nên không nhất thiết phải gọi `Files.readAttributes` một lần nữa cho mỗi file:

```java
Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
    @Override
    public FileVisitResult visitFile(Path file,
                                     BasicFileAttributes attrs)
            throws IOException {
        if (attrs.isRegularFile()) {
            System.out.println(file.getFileName() + ": " + attrs.size());
        }
        return FileVisitResult.CONTINUE;
    }
});
```

Đây là **mảnh mã** cần import `java.nio.file.*`, `java.nio.file.attribute.*`, `java.io.IOException`; `root` đã có sẵn. Những callback của visitor có thể báo lỗi I/O; ứng dụng thực tế cần xử lý `visitFileFailed` theo chính sách. Trong quét file lớn, hạn chế những lần đọc lại metadata không cần thiết thường giúp tránh I/O phụ.

Nếu ứng dụng muốn hiển thị quyền POSIX cho một vài file, có thể đọc `PosixFileAttributes` hoặc `Files.readAttributes(path, "posix:permissions")` tại nơi cần. Tránh giả định mỗi entry đều hỗ trợ POSIX, nhất là khi quét nhiều filesystem.

## 13. Lưu ý về API đặt thuộc tính theo tên

API **đọc** và API **ghi** có tên gần nhau nhưng mục đích khác:

```java
Object before = Files.getAttribute(path, "basic:lastModifiedTime");
Files.setAttribute(path, "basic:lastModifiedTime", someFileTime);
```

Đây là mảnh mã; `path` đã có, `someFileTime` là `FileTime` hợp lệ, và cần xử lý `IOException`. Lệnh đầu **không sửa file**; lệnh sau yêu cầu **thay đổi metadata**. Không ghép thao tác set vào ví dụ chỉ đọc nếu không có yêu cầu. Với view hoặc attribute không cho sửa, việc `setAttribute` có thể thất bại dù `getAttribute` đọc được.

Để đọc thời gian sửa đổi, `Files.getLastModifiedTime(path)` thường rõ hơn là gọi `getAttribute` và ép `FileTime`; để ghi, `Files.setLastModifiedTime(path, time)` có chữ ký rõ ràng hơn.

## 14. Lỗi thường gặp khi đọc thuộc tính

| Sai lầm | Hậu quả | Cách làm đúng |
| --- | --- | --- |
| Dùng `Files.readString` để hỏi kích thước | Nạp nội dung file không cần thiết | `BasicFileAttributes.size()` hoặc `Files.size` |
| Nhầm `readAttributes(path, BasicFileAttributes.class)` trả `Map` | Sai kiểu hoặc không biên dịch | Dùng phương thức typed `size()`, `isDirectory()` |
| Nghĩ `readAttributes(path, "basic:size")` trả `long` | Cast sai | Nhận `Map<String,Object>` rồi `get("size")` |
| Ép `map.get("size")` sang `Integer` | `ClassCastException` | Kiểu basic `size` là `Long` |
| Dùng `map.get("basic:size")` | Nhận `null` do key không có prefix | Dùng `map.get("size")` |
| Dùng `Files.getAttribute` để đọc cả danh sách | Không đúng API | Dùng overload `Files.readAttributes` theo tên |
| Ghi `"basic:lastModifiedTime,size"` nhưng giả định key có thứ tự | Báo cáo output không ổn định | Đọc từng key hoặc sắp xếp nếu cần |
| Dùng `PosixFileAttributes.class` ở mọi hệ điều hành | Unsupported view | Kiểm tra provider/file store; fallback basic |
| Dùng tên method `"isHidden"` cho DOS map | Sai key attribute | Dùng `"dos:hidden"`, giá trị Boolean |
| Cho rằng `fileKey()` luôn có | Null/giả định sai | Cho phép `null`, tránh phụ thuộc identity vĩnh viễn |
| So sánh time ở độ phân giải nanosecond trên mọi FS | Kết luận sai | Chấp nhận độ chính xác theo provider |
| Gọi `size()` cho directory rồi coi là tổng byte các file con | Sai ý nghĩa | Duyệt file con nếu cần tính tổng |
| Gọi typed/read named nhiều lần rồi coi là snapshot thống nhất | Metadata có thể đổi giữa các lần | Thiết kế nhất quán và retry/lock khi cần |
| Đọc link khi muốn chính metadata của symlink | Thấy metadata của đích | `LinkOption.NOFOLLOW_LINKS` |
| Coi `NOFOLLOW_LINKS` là kiểm tra bảo mật toàn diện | Vẫn còn race/traversal | Kết hợp mô hình quyền/đường dẫn an toàn |
| Không xử lý `IOException` khi đọc metadata | Ứng dụng thất bại ngoài ý muốn | Bắt và xử lý lỗi phù hợp |
| Đọc sai view-name/attribute-name | Runtime error hoặc thiếu key | Dựa trên API của view và kiểm tra schema |
| Nhầm kiểm tra `exists()` với đảm bảo `readAttributes` thành công | TOCTOU | Gọi API thật và xử lý ngoại lệ |
| Coi `readAttributes` là thao tác sửa quyền | Hiểu sai semantics | Dùng `setAttribute`/view setter khi thật sự cần sửa |

## 15. Câu hỏi tự kiểm tra

1. **Hai overload `Files.readAttributes` khác nhau thế nào?** Một nhận `Class<A extends BasicFileAttributes>` và trả đối tượng typed, một nhận danh sách tên `String` và trả `Map<String,Object>`.
2. **`"basic:size,isRegularFile"` yêu cầu mấy thuộc tính?** Hai thuộc tính thuộc basic view: `size` và `isRegularFile`.
3. **Nếu không viết `basic:`, view nào được dùng?** `basic`.
4. **Ký hiệu `"*"` nghĩa gì trong `readAttributes` theo tên?** Lấy tất cả thuộc tính mà view tương ứng hỗ trợ.
5. **Map key của `"basic:size"` là `"basic:size"` hay `"size"`?** `"size"`.
6. **Kiểu Java cho size trong map?** `Long`; `getAttribute` cũng trả một `Object` là `Long` cho basic size.
7. **`BasicFileAttributes` có sẵn ở mọi provider NIO không?** Có, basic attribute view được yêu cầu hỗ trợ.
8. **Có thể đưa `AclFileAttributeView.class` vào typed `readAttributes` không?** Không, vì overload yêu cầu subtype của `BasicFileAttributes`.
9. **Khi nào dùng `PosixFileAttributes`?** Khi cần owner/group/permissions và provider hỗ trợ POSIX view.
10. **Đọc theo kiểu có chắc snapshot nguyên tử không?** Không; tính nguyên tử so với I/O khác là phụ thuộc triển khai.
11. **`NOFOLLOW_LINKS` dùng để làm gì?** Yêu cầu lấy metadata của chính link thay vì đi theo link tới đích (nếu provider hỗ trợ).
12. **`creationTime()` có chắc thời điểm tạo thật trong mọi filesystem?** Không, provider có thể dùng giá trị mặc định nếu không hỗ trợ.
13. **`size()` của thư mục có phải tổng kích thước các file bên trong?** Không.
14. **`Files.getFileStore(path).supportsFileAttributeView("posix")` có bảo đảm lần đọc chắc thành công không?** Không; quyền và trạng thái file vẫn có thể thay đổi.
15. **Muốn đọc một thuộc tính thay vì một map thì làm gì?** `Files.getAttribute(path, "view:name")` hoặc API tiện ích như `Files.size`.
16. **Nếu filesystem không hỗ trợ view DOS/POSIX?** Không giả định có mặt; kiểm tra capability, chọn basic/fallback, xử lý `UnsupportedOperationException`.
17. **`readAttributes` có tự sửa permission hay timestamp không?** Không, đó là thao tác đọc.
18. **Làm sao giảm đọc metadata dư khi dùng `walkFileTree`?** Dùng tham số `BasicFileAttributes` mà callback `visitFile` đã cung cấp.

## Tài liệu API Java 17

- [`Files.readAttributes`, `Files.getAttribute` và `Files.getFileAttributeView`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html).
- [`BasicFileAttributes`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/BasicFileAttributes.html).
- [`PosixFileAttributes`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/PosixFileAttributes.html).
- [`DosFileAttributes`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/DosFileAttributes.html).
- [`FileAttributeView`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/FileAttributeView.html).
- [`FileStore`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileStore.html).
- [`LinkOption.NOFOLLOW_LINKS`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/LinkOption.html).
- [`FileTime`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/FileTime.html).
- [`FileSystemProvider`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/spi/FileSystemProvider.html).

**Ghi nhớ:** Với Java NIO, muốn có **thuộc tính rõ kiểu**, dùng `Files.readAttributes(path, BasicFileAttributes.class)`; muốn **chọn thuộc tính theo tên**, dùng `Files.readAttributes(path, "basic:size,isDirectory")`; muốn một thuộc tính, dùng `Files.getAttribute` hoặc getter chuyên dụng. Đừng lẫn metadata với nội dung file, đừng giả định POSIX/DOS có trên mọi provider, và luôn xử lý symbolic link, quyền truy cập, lỗi I/O cùng khả năng metadata thay đổi đồng thời.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-attributes-and-permissions-005">POSIX permission và PosixFileAttributeView</a>

<details>
<summary>Xem chi tiết</summary>

# POSIX permission và PosixFileAttributeView trong Java NIO.2

Một ứng dụng Java xử lý file có thể cần đọc quyền hiện tại, giới hạn ai được đọc/ghi một file chứa dữ liệu riêng tư, hoặc cấp quyền thực thi cho một script. Trên những filesystem hỗ trợ **POSIX file attributes**, Java NIO.2 cung cấp `PosixFilePermission`, `PosixFilePermissions`, `PosixFileAttributes` và **`PosixFileAttributeView`** để thao tác các thuộc tính này bằng API có kiểu, thay vì ghép lệnh shell `chmod`.

Tuy nhiên, không phải hệ điều hành hay filesystem nào cũng hỗ trợ giao diện POSIX. Kể cả khi có, quyền truy cập hiệu lực vẫn có thể chịu ảnh hưởng bởi chủ sở hữu, nhóm, ACL, quyền trên thư mục cha và cơ chế bảo mật của hệ thống. Bài học này chỉ ra cách **phát hiện khả năng hỗ trợ**, **đọc/ghi quyền đúng API**, **xử lý lỗi** và tránh tạo ra lỗ hổng quyền truy cập.

## 1. POSIX permission là gì?

POSIX-style permission cơ bản mô tả ba nhóm người dùng và ba loại quyền:

- **Owner (`u`):** chủ sở hữu file hoặc thư mục.
- **Group (`g`):** thành viên của nhóm sở hữu.
- **Others (`o`):** những chủ thể không thuộc hai trường hợp trên theo cơ chế phân loại thích hợp của hệ thống.
- **Read (`r`):** quyền đọc.
- **Write (`w`):** quyền ghi.
- **Execute (`x`):** quyền thực thi, hoặc quyền tìm kiếm/đi qua thư mục tùy loại đối tượng.

Một chuỗi quyền phổ biến là:

~~~text
rwxr-x---
│││││││││
││││││└┴┴─ others: --- (không có r/w/x)
│││└┴┴──── group:  r-x (đọc + thực thi)
└┴┴──────── owner:  rwx (đọc + ghi + thực thi)
~~~

Chuỗi trên có **đúng 9 ký tự**: ba ký tự đầu dành cho owner, ba ký tự tiếp theo dành cho group và ba ký tự cuối dành cho others.

Ví dụ thường gặp:

| Dạng ký tự | Dạng bát phân (octal) quen dùng | Ý nghĩa |
|---|---|---|
| `rw-------` | `0600` | Owner đọc/ghi; group và others không có quyền |
| `rw-r-----` | `0640` | Owner đọc/ghi; group chỉ đọc; others không có quyền |
| `rw-r--r--` | `0644` | Owner đọc/ghi; group và others chỉ đọc |
| `rwx------` | `0700` | Owner đọc/ghi/thực thi; hai nhóm khác không có quyền |
| `rwxr-x---` | `0750` | Owner đầy đủ; group đọc/thực thi; others không có quyền |
| `rwxr-xr-x` | `0755` | Owner đầy đủ; group và others đọc/thực thi |

Quy ước số: `r = 4`, `w = 2`, `x = 1` trong từng nhóm; ví dụ `rwx = 7`, `r-x = 5`, `--- = 0`. API `PosixFilePermissions.fromString` nhận chuỗi **9 ký tự**, không nhận trực tiếp chuỗi `"0755"`.

### Quyền trên file và thư mục có ý nghĩa khác nhau

| Quyền | File thông thường | Thư mục |
|---|---|---|
| `r` | Cho phép đọc dữ liệu file theo chính sách hệ thống | Thường cho phép liệt kê tên các mục bên trong |
| `w` | Cho phép sửa nội dung file theo chính sách hệ thống | Cùng quyền `x`, thường cho phép tạo/xóa/đổi tên mục trong thư mục |
| `x` | Cho phép thực thi file nếu định dạng và hệ thống hỗ trợ | Cho phép **search/traverse**: đi qua thư mục để truy cập mục theo tên |

Một file có quyền đọc **không bảo đảm** bạn mở được bằng đường dẫn đầy đủ nếu thiếu quyền đi qua một thư mục cha. Ngược lại, quyền xóa file thường được quyết định bởi **quyền trên thư mục chứa nó**, không đơn giản bởi bit `w` của file.

Bảng chỉ giải thích ba bộ bit quyền cơ bản. Những cơ chế khác như ACL, sticky bit, setuid/setgid, mount options và chính sách bảo mật của hệ thống có thể làm quyền hiệu lực khác với suy luận từ chín ký tự này.

## 2. Các lớp và giao diện Java liên quan

Những kiểu chính trong `java.nio.file.attribute`:

| API | Nhiệm vụ |
|---|---|
| `PosixFilePermission` | Enum gồm 9 giá trị quyền cho owner, group, others |
| `PosixFilePermissions` | Chuyển đổi giữa chuỗi như `rw-r-----` và `Set<PosixFilePermission>`; tạo `FileAttribute` để cấp quyền lúc tạo file |
| `PosixFileAttributes` | Gói thông tin file kiểu POSIX: `permissions()`, `owner()`, `group()` và thuộc tính cơ bản kế thừa |
| `PosixFileAttributeView` | View để **đọc và sửa** thuộc tính POSIX của đối tượng file cụ thể |
| `Files.getPosixFilePermissions` | Phương thức tiện ích đọc bộ quyền |
| `Files.setPosixFilePermissions` | Phương thức tiện ích ghi bộ quyền |
| `Files.getFileAttributeView` | Lấy đối tượng view nếu provider hỗ trợ loại view tương ứng |

`PosixFilePermission` bao gồm các hằng:

~~~java
OWNER_READ
OWNER_WRITE
OWNER_EXECUTE
GROUP_READ
GROUP_WRITE
GROUP_EXECUTE
OTHERS_READ
OTHERS_WRITE
OTHERS_EXECUTE
~~~

Các tên trên là **hằng enum**, không phải biến tự do. Khi dùng trong Java phải có kiểu hoặc static import, ví dụ `PosixFilePermission.OWNER_READ`.

Cơ chế qua view:

~~~text
Path --------------------------> Files.getFileAttributeView(...)
                                      |
                                      v
                              PosixFileAttributeView
                                |       |       |
                                |       |       +-- setGroup(...)
                                |       +---------- setPermissions(...)
                                +------------------ readAttributes()
                                      |
                                      v
                              PosixFileAttributes
                               owner / group / permissions
~~~

Một view là **đối tượng truy cập thuộc tính của một path**, không phải bản sao nội dung file. Khi gọi `readAttributes()`, chương trình yêu cầu filesystem cung cấp một bộ thuộc tính tại thời điểm đọc.

## 3. Chuyển đổi chuỗi quyền bằng `PosixFilePermissions`

Khai báo bộ quyền từ chuỗi:

~~~java
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

Set<PosixFilePermission> permissions =
        PosixFilePermissions.fromString("rw-r-----");
System.out.println(PosixFilePermissions.toString(permissions));
// rw-r-----
~~~

Đây là **đoạn minh họa** có thể đặt trong `main`; nó chỉ chuyển đổi dữ liệu trong bộ nhớ, **không tự đổi quyền của bất kỳ file nào**.

Bạn cũng có thể xây bộ quyền trực tiếp:

~~~java
Set<PosixFilePermission> privateReadWrite = Set.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE);
~~~

Trong `Set` trên chỉ có hai quyền của owner, tương ứng `rw-------`. Hai cách biểu diễn là tương đương về **chín permission bit cơ bản**.

Các ví dụ sai:

~~~java
// Không hợp lệ cho fromString: phải có đúng 9 ký tự rwx/-.
PosixFilePermissions.fromString("0644");
PosixFilePermissions.fromString("rwx");
~~~

Đây là các **dòng minh họa lỗi**, không nên cho vào chương trình chạy thật. `fromString` ném `IllegalArgumentException` khi chuỗi có cấu trúc/ký tự không hợp lệ.

Không được nhầm `PosixFilePermissions.toString(permissions)` với `permissions.toString()` của `Set`: một bên cho chuỗi 9 ký tự quyền Unix quen thuộc; một bên chỉ là cách biểu diễn tập enum theo Java.

## 4. Kiểm tra hệ thống có hỗ trợ POSIX không

`PosixFileAttributeView` chỉ khả dụng khi **file system provider** hỗ trợ. Một mã chạy ổn trên Linux với filesystem hỗ trợ POSIX có thể không chạy theo cùng cách trên một provider mặc định của Windows hoặc trên filesystem đặc biệt.

Cách kiểm tra trực tiếp cho một path:

~~~java
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;

PosixFileAttributeView view = Files.getFileAttributeView(
        path, PosixFileAttributeView.class);

if (view == null) {
    System.out.println("Không có POSIX attribute view.");
} else {
    System.out.println("Có thể thử đọc thuộc tính POSIX.");
}
~~~

Đây là **đoạn minh họa**, `path` phải có giá trị phù hợp. Khi provider **không hỗ trợ** view được yêu cầu, `getFileAttributeView` trả `null`. Việc lấy được view **không bảo đảm** thao tác đọc hoặc sửa quyền sau đó luôn thành công: file có thể mất, quyền bị từ chối hoặc provider gặp lỗi I/O.

Cách kiểm tra khả năng ở cấp filesystem store:

~~~java
boolean supported = Files.getFileStore(path)
        .supportsFileAttributeView("posix");
~~~

Cần xử lý `IOException` khi truy cập file store. Đối với thao tác thật trên một file, lấy view trực tiếp tại path rồi xử lý lỗi tại bước đọc/ghi thường rõ hơn.

**Lưu ý quan trọng về portability:** Không tự diễn giải tên file hoặc hệ điều hành thành bảo đảm có POSIX view. Một nhà cung cấp filesystem tùy biến, ổ mạng, file ZIP hoặc quyền filesystem khác có thể có mức hỗ trợ không giống hệ điều hành nền.

## 5. Đọc permission bằng `Files` hoặc bằng `PosixFileAttributeView`

### Cách ngắn: `Files.getPosixFilePermissions`

~~~java
Set<PosixFilePermission> current =
        Files.getPosixFilePermissions(path);

System.out.println(PosixFilePermissions.toString(current));
~~~

Đây là **đoạn minh họa**; cần các import `java.nio.file.Files`, `java.nio.file.Path`, `java.nio.file.attribute.*`, `java.util.Set` và xử lý `IOException`. `Files.getPosixFilePermissions(path)` có thể ném `UnsupportedOperationException` nếu provider không hỗ trợ thuộc tính POSIX.

### Cách qua view: `readAttributes()`

~~~java
PosixFileAttributeView view =
        Files.getFileAttributeView(path, PosixFileAttributeView.class);

if (view != null) {
    PosixFileAttributes attrs = view.readAttributes();
    Set<PosixFilePermission> permissions = attrs.permissions();
    System.out.println(PosixFilePermissions.toString(permissions));
    System.out.println("Owner: " + attrs.owner().getName());
    System.out.println("Group: " + attrs.group().getName());
}
~~~

Đây là **đoạn minh họa**. `readAttributes()` trả một đối tượng `PosixFileAttributes`, trong đó `owner()` và `group()` là các đối tượng `UserPrincipal` và `GroupPrincipal`, không phải những số UID/GID được Java bảo đảm ở mọi provider.

Ưu điểm của view là có thể đọc cùng lúc nhiều thuộc tính có liên quan thay vì gọi các phương thức riêng nhiều lần. Nhưng đừng coi snapshot thuộc tính là khóa file: các thuộc tính có thể thay đổi sau khi đã đọc.

## 6. Ghi permission: `Files.setPosixFilePermissions` và `view.setPermissions`

Khi muốn đặt quyền cơ bản cho file **do ứng dụng sở hữu/quản lý**, có thể gọi:

~~~java
Set<PosixFilePermission> restricted =
        PosixFilePermissions.fromString("rw-------");

Files.setPosixFilePermissions(path, restricted);
~~~

Hoặc qua view:

~~~java
PosixFileAttributeView view = Files.getFileAttributeView(
        path, PosixFileAttributeView.class);

if (view == null) {
    throw new UnsupportedOperationException("Không hỗ trợ POSIX");
}
view.setPermissions(restricted);
~~~

Các đoạn trên cần đặt trong phương thức có import và xử lý `IOException`. `setPermissions` cập nhật tập **chín permission bit cơ bản** của file, không phải một cơ chế cấp quyền trừu tượng hoạt động đồng nhất ở mọi hệ thống.

Các điểm cần hiểu:

1. `Set<PosixFilePermission>` là tập các quyền cần có, không phải lệnh “thêm từng quyền vào tập hiện tại” theo mặc định.
2. Nếu bạn truyền tập chỉ chứa `OWNER_READ`, mọi bit quyền khác trong mô hình POSIX cơ bản có thể bị **loại bỏ**. Hãy kiểm tra nhu cầu của ứng dụng trước khi cập nhật.
3. Muốn **thêm đúng một bit** mà giữ các bit khác, cần đọc quyền hiện tại, tạo một tập có thể sửa, thêm enum tương ứng, rồi ghi lại. Đây là thao tác đọc–sửa–ghi có thể gặp race khi nhiều tiến trình cùng sửa quyền.
4. Chỉ nên chỉnh sửa quyền đối với đối tượng bạn được phép quản lý; hệ thống có thể ném `IOException` hoặc `SecurityException` tùy provider và môi trường.
5. Đừng mặc định gọi `setPosixFilePermissions` trên Windows/native provider không hỗ trợ: `UnsupportedOperationException` là một kết quả cần xử lý.
6. Việc đặt quyền thay đổi metadata của đối tượng có thật, không phải thay đổi nội dung file; tuy nhiên nó có thể ảnh hưởng trực tiếp đến khả năng truy cập của các tiến trình khác.

### Thêm quyền mà không xóa quyền khác

~~~java
Set<PosixFilePermission> updated = new HashSet<>(
        Files.getPosixFilePermissions(path));

updated.add(PosixFilePermission.GROUP_READ);
Files.setPosixFilePermissions(path, updated);
~~~

Đây là **đoạn minh họa** với import `java.util.HashSet`, `java.nio.file.*` và `java.nio.file.attribute.*`. Nếu chương trình chỉ muốn thử sửa quyền trong bài tập, dùng **tệp tạm do chính mình tạo**, không dùng tệp cấu hình hoặc dữ liệu thật của người khác.

### Đặt owner/group không giống đặt permission

`PosixFileAttributeView` còn có phương thức như `setGroup(GroupPrincipal)` và kế thừa thao tác đổi owner qua `setOwner(UserPrincipal)`. Đổi chủ sở hữu hoặc nhóm thường có yêu cầu đặc quyền riêng; không thể suy ra rằng sửa được permission thì chắc chắn đổi được owner/group.

**Quyền owner, group và các bit permission là những thuộc tính liên quan nhưng độc lập.** Khi thay owner/group, tác động quyền hiệu lực có thể thay đổi ngay cả khi chuỗi `rwxr-x---` không đổi.

## 7. Ví dụ Java 17 hoàn chỉnh: chỉ thay quyền tệp tạm

Chương trình sau tạo **một file tạm**, kiểm tra `PosixFileAttributeView`, đặt `rw-------` nếu view có sẵn và đọc lại để xác minh. Nó không đụng vào quyền của file thực thuộc người học.

Lưu thành **`PosixPermissionsDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

public class PosixPermissionsDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("posix-demo-", ".txt");

        try {
            PosixFileAttributeView view = Files.getFileAttributeView(
                    file, PosixFileAttributeView.class,
                    LinkOption.NOFOLLOW_LINKS);

            if (view == null) {
                System.out.println("POSIX view available: false");
            } else {
                System.out.println("POSIX view available: true");

                Set<PosixFilePermission> requested =
                        PosixFilePermissions.fromString("rw-------");
                view.setPermissions(requested);

                PosixFileAttributes attrs = view.readAttributes();
                System.out.println("Requested: "
                        + PosixFilePermissions.toString(requested));
                System.out.println("Read back: "
                        + PosixFilePermissions.toString(attrs.permissions()));
                System.out.println("Exact match: "
                        + attrs.permissions().equals(requested));

                Set<PosixFilePermission> viaFiles =
                        Files.getPosixFilePermissions(file);
                System.out.println("Files getter agrees: "
                        + viaFiles.equals(attrs.permissions()));
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 PosixPermissionsDemo.java
java PosixPermissionsDemo
~~~

Trên filesystem hỗ trợ POSIX thông thường, khi người chạy có quyền sửa permission và provider chấp nhận yêu cầu, kết quả dự kiến:

~~~text
POSIX view available: true
Requested: rw-------
Read back: rw-------
Exact match: true
Files getter agrees: true
~~~

Nếu provider **không hỗ trợ** `PosixFileAttributeView`, chương trình in:

~~~text
POSIX view available: false
~~~

Các trường hợp lỗi quyền, lỗi I/O hoặc filesystem đặc biệt có thể khiến chương trình ném ngoại lệ thay vì in đầy đủ các dòng của nhánh hỗ trợ. Đó là hành vi cần xử lý trong ứng dụng thực; không nên che giấu bằng cách âm thầm bỏ qua vấn đề bảo mật.

**Giải thích:**

1. `Files.createTempFile` tạo một file mới và trả đường dẫn tới nó. Ví dụ chỉ thay quyền trên file mới này.
2. `Files.getFileAttributeView(file, PosixFileAttributeView.class, NOFOLLOW_LINKS)` yêu cầu view để làm việc với **chính path** mà không theo symbolic link ở bước tra cứu thuộc tính. Vì file do chương trình vừa tạo là file thông thường, nhánh symlink không tham gia.
3. Nếu view không có, chương trình **không thử gọi `setPermissions`**.
4. Nếu có, `fromString("rw-------")` tạo tập gồm đúng `OWNER_READ` và `OWNER_WRITE`, rồi `view.setPermissions` yêu cầu cập nhật quyền.
5. `view.readAttributes().permissions()` đọc lại tập quyền, chuyển sang chuỗi và so sánh bằng `Set.equals`.
6. `Files.getPosixFilePermissions(file)` là phương thức tiện ích đọc quyền POSIX. Trên file không thay đổi đồng thời trong ví dụ, kết quả khớp với view.
7. Khối `finally` xóa file tạm dù nhánh xử lý thành công hay ném ngoại lệ. Nếu xóa file cũng thất bại, Java có thể báo `IOException` cho bước dọn dẹp; đây là một yếu tố cần theo dõi trong mã sản xuất.

**Lưu ý về kiểm thử:** chương trình chỉ kiểm tra rằng provider **báo đã lưu các permission bit được yêu cầu**. Nó không kiểm thử hành vi thực tế của từng user/group, và không bảo đảm mọi hệ thống thực thi kiểm soát truy cập giống nhau.

## 8. `PosixFileAttributeView` và symbolic link

Symbolic link là một mục trong filesystem trỏ tới mục khác. Nếu path `link` là symlink trỏ tới `target`, cần quyết định muốn thao tác thuộc tính của **đích** hay của **chính liên kết**.

Theo mặc định, nhiều lời gọi Files đọc thuộc tính **theo symbolic link** tới đích. `LinkOption.NOFOLLOW_LINKS` cho phép yêu cầu **không theo liên kết**:

~~~java
PosixFileAttributeView noFollowView = Files.getFileAttributeView(
        link,
        PosixFileAttributeView.class,
        LinkOption.NOFOLLOW_LINKS);
~~~

Đây là **đoạn minh họa**, trong đó `link` là `Path` đã có. Một số filesystem **không cho phép đọc/đổi permission của bản thân symlink theo cùng cách với file đích**; không được suy ra rằng `NOFOLLOW_LINKS` khiến mọi thao tác `setPermissions` trên symlink đều được hỗ trợ.

Nếu không chủ đích thay quyền của **file target**, hãy xác định link policy trước khi gọi. Đây là vấn đề bảo mật: một kẻ khác có thể thay thế link trong khoảng thời gian từ khi ứng dụng kiểm tra path đến khi sửa quyền hoặc mở file (**time-of-check/time-of-use**). `NOFOLLOW_LINKS` ở một bước không tự loại bỏ mọi race ở các bước khác.

Khi xử lý đường dẫn do người dùng không tin cậy cung cấp, nên xác định thư mục gốc được phép, quyền truy cập và chiến lược mở file an toàn ở cấp nền tảng. **Không biến thao tác chỉnh permission thành cơ chế tự động “sửa mọi lỗi truy cập”**.

## 9. Quyền khởi tạo lúc tạo file và `umask`

Một vấn đề thường gặp là tạo file xong rồi mới gọi `setPosixFilePermissions`. Nếu dữ liệu nhạy cảm đã được ghi trước lúc siết quyền, có thể tồn tại một khoảng thời gian file có quyền rộng hơn mong muốn.

Khi provider hỗ trợ POSIX, Java cho phép chỉ định permission **ngay trong thao tác tạo** thông qua `FileAttribute`:

~~~java
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

FileAttribute<Set<PosixFilePermission>> privateFile =
        PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rw-------"));

Path newFile = Files.createFile(path, privateFile);
~~~

Đây là **đoạn minh họa**, `path` phải trỏ tới file chưa tồn tại và caller xử lý `IOException`. Lựa chọn này hữu ích nếu **bảo mật tại thời điểm tạo** là yêu cầu. Khi file đã tồn tại, `Files.createFile` không ghi đè file đó mà thất bại.

Tương tự, `Files.createTempFile` có overload nhận file attributes, và có thể dùng `asFileAttribute` theo chính sách của bạn. Tuy nhiên:

- Tạo file với attributes POSIX trên provider không hỗ trợ có thể thất bại.
- Quyền thực tế khi tạo có thể bị giới hạn bởi chính sách filesystem và **umask** của môi trường; cần đọc lại thuộc tính nếu ứng dụng yêu cầu xác minh.
- Việc chỉ định quyền khi tạo không thay thế kiểm tra owner/group, ACL, quyền của thư mục cha hoặc cơ chế bảo vệ dữ liệu nhạy cảm khác.
- Không viết chuỗi mật khẩu/dữ liệu riêng tư vào một file “tạm mặc định” rồi chỉ siết quyền ở bước cuối và giả định không có cửa sổ rủi ro.

**Umask** là cơ chế POSIX thường dùng để loại bớt những bit quyền được yêu cầu lúc tạo mới. Ví dụ umask `022` có thể khiến file mặc định được tạo với quyền ít hơn so với yêu cầu rộng hơn. Không coi umask là một thuộc tính mà `PosixFilePermission` tự động mô hình hóa trong tập 9 enum.

## 10. Quyền POSIX, ACL và quyền truy cập thực tế

Chín bit `rwxrwxrwx` chỉ là mô hình cơ bản. Một ứng dụng có thể gặp:

- **POSIX ACL:** điều khoản quyền bổ sung/ghi đè theo từng user hoặc group, tùy filesystem và cách diễn giải quyền hiệu lực.
- **Windows ACL:** mô hình quyền khác, không nên ép vào chuỗi 9 ký tự như thể đầy đủ.
- **Sticky bit** trên một số thư mục; **setuid/setgid** trên file hoặc directory; không được biểu diễn trong chính `Set<PosixFilePermission>` chín giá trị.
- **Quyền root/superuser**, capabilities hoặc chính sách an ninh bắt buộc có thể ảnh hưởng khả năng mở, đọc, sửa hay xóa.
- **Thuộc tính thư mục cha** và trạng thái mount (ví dụ filesystem chỉ đọc) ảnh hưởng thao tác dù file có `rw-------`.
- **Cache và thay đổi đồng thời** làm thuộc tính đọc được tại thời điểm trước không còn phản ánh trạng thái ở thời điểm dùng.

Trên provider hỗ trợ `AclFileAttributeView`, API khác có thể cho thông tin ACL. Không được kết luận “không có quyền” hoặc “chắc chắn có quyền” chỉ từ một thao tác kiểm tra đơn lẻ mà bỏ qua lỗi khi thực sự mở hoặc ghi dữ liệu.

`Files.isReadable(path)`, `Files.isWritable(path)` và `Files.isExecutable(path)` là các kiểm tra tiện ích nhưng không phải hàng rào bảo mật bất biến; file hoặc quyền có thể thay đổi ngay sau khi kiểm tra. Nên thử thao tác thật trong khối xử lý lỗi phù hợp với nghiệp vụ thay vì chỉ dựa vào kiểm tra trước.

## 11. Xử lý lỗi và portability trong ứng dụng thật

| Tình huống | Khả năng xảy ra | Phản ứng đúng |
|---|---|---|
| View POSIX không có | `getFileAttributeView` trả `null` | Tránh dereference; chọn đường xử lý tương thích hoặc thông báo không hỗ trợ |
| Truy cập POSIX attribute bằng Files API nhưng provider không hỗ trợ | `UnsupportedOperationException` | Không ép sửa quyền; dùng cơ chế hợp đồng của provider |
| File không tồn tại hoặc bị xóa đồng thời | `IOException`, ví dụ `NoSuchFileException` | Báo lỗi hoặc retry có kiểm soát |
| Không có quyền đọc/sửa thuộc tính | `IOException` hay `SecurityException` tùy môi trường | Báo rõ quyền hạn, không tự mở rộng quyền bừa bãi |
| Chuỗi permission sai định dạng | `IllegalArgumentException` từ `fromString` | Kiểm tra đúng 9 ký tự, chỉ dùng `rwx-` |
| Thao tác trên symlink | Có thể đi theo link hoặc không, tùy option | Xác định chính xác file đích hay link là đối tượng cần sửa |
| FileStore có view nhưng path không dùng được | Lỗi thuộc tính cụ thể vẫn có thể xảy ra | Thử thao tác trong `try/catch` và xử lý lỗi thực tế |
| Đổi quyền giữa hai tiến trình | Có thể mất cập nhật trong chuỗi đọc–sửa–ghi | Cần điều phối khi ứng dụng có concurrent writers |

Mẫu helper kiểm tra view trước khi đọc:

~~~java
static String permissionText(Path file) throws IOException {
    PosixFileAttributeView view = Files.getFileAttributeView(
            file, PosixFileAttributeView.class);
    if (view == null) {
        throw new UnsupportedOperationException(
                "POSIX attributes are unavailable for " + file);
    }
    return PosixFilePermissions.toString(
            view.readAttributes().permissions());
}
~~~

Đây là **phương thức minh họa** trong một lớp Java với các import `java.io.IOException`, `java.nio.file.*`, `java.nio.file.attribute.*`. Caller nhận được chuỗi 9 ký tự nếu đọc thành công; nếu không, lỗi được phân biệt thay vì trả về một giá trị giả như `rwxrwxrwx`.

Một hệ thống đa nền tảng có thể cung cấp **giao diện trừu tượng mức ứng dụng**: nếu POSIX có sẵn thì dùng permission view; nếu không, chọn API quyền thích hợp của provider hoặc dừng với thông báo rõ. Đừng âm thầm biến yêu cầu bảo vệ file thành “bỏ qua chỉnh quyền” trên nền tảng không hỗ trợ nếu mức bảo mật đó là điều kiện bắt buộc.

## 12. Lỗi phổ biến

| Nhầm lẫn | Hậu quả | Cách làm đúng |
|---|---|---|
| Cho rằng `PosixFileAttributeView` luôn có trên mọi máy chạy Java | `null`/unsupported trên provider không tương thích | Kiểm tra hỗ trợ, có chiến lược thay thế |
| Dùng `"755"` cho `fromString` | Sai định dạng API | Dùng `"rwxr-xr-x"` |
| Tưởng `getPosixFilePermissions` đã sửa quyền | Chỉ đọc metadata | Dùng `setPosixFilePermissions` hoặc `view.setPermissions` |
| Tưởng `setPermissions` là “thêm các quyền mới” | Có thể xóa các bit không nằm trong tập mới | Xây tập quyền đầy đủ trước khi set |
| Đặt `rwxrwxrwx` để “sửa lỗi không truy cập được” | Mở quyền rộng nguy hiểm, chưa chắc giải quyết được lỗi | Xác định owner/group, thư mục cha, ACL và nhu cầu thực |
| Coi `r` của thư mục như quyền đi qua thư mục | Trên POSIX, duyệt theo tên thường cần `x` | Phân biệt ý nghĩa r/w/x file và directory |
| Quên chính sách symlink | Sửa nhầm quyền file khác | Chọn follow/nofollow phù hợp, chống race |
| Tin rằng có view thì chắc chắn set thành công | Permission change có thể bị cấm | Bắt lỗi I/O và không đoán |
| Ghi bí mật trước rồi mới siết quyền | Có thể có khoảng thời gian file lộ quyền rộng | Chỉ định thuộc tính khi tạo nếu cần |
| Tin rằng 9 bit đủ mô tả quyền hiệu lực | ACL và cơ chế hệ thống còn ảnh hưởng | Kết hợp kiểm tra thực tế và policy |
| Không phân biệt quyền file và quyền thư mục cha | Có thể không truy cập dù file có r/w | Xem đường đi và directory traversal |
| Bắt mọi lỗi rồi im lặng | Ứng dụng tưởng đã bảo vệ dữ liệu | Báo lỗi và dừng nếu bảo mật là bắt buộc |

## 13. Bài tập tự kiểm tra

**Câu 1.** Chuỗi `rw-r-----` có nghĩa gì? Tương ứng dạng bát phân nào?

> Owner được đọc và ghi, group chỉ được đọc, others không có quyền. Đó là `0640` trong ký hiệu octal quen dùng.

**Câu 2.** Vì sao `Files.getFileAttributeView(file, PosixFileAttributeView.class)` có thể trả `null`?

> Vì filesystem provider của file không hỗ trợ view POSIX. Phải kiểm tra kết quả trước khi gọi `readAttributes` hay `setPermissions`.

**Câu 3.** Muốn lấy quyền hiện tại từ `PosixFileAttributes`, dùng phương thức nào? Muốn đổi quyền, dùng gì?

> Dùng `view.readAttributes().permissions()` để lấy tập quyền. Để đổi quyền, dùng `view.setPermissions(set)` hoặc `Files.setPosixFilePermissions(file, set)`.

**Câu 4.** Có một file đang có `rw-r-----`. Nếu gọi `setPermissions(fromString("rw-------"))` thì có còn `GROUP_READ` không?

> **Không** trong tập chín quyền cơ bản: tập mới yêu cầu chỉ owner đọc/ghi. `GROUP_READ` không còn trong tập được đặt.

**Câu 5.** Khi muốn tạo file bí mật, vì sao truyền `PosixFilePermissions.asFileAttribute` vào `Files.createFile` có lợi hơn so với chỉnh quyền sau khi ghi dữ liệu?

> Vì có thể yêu cầu quyền phù hợp **ngay tại thời điểm tạo**, tránh khoảng hở giữa tạo/ghi và siết quyền. Vẫn cần tính đến provider, umask, ACL và chính sách hệ thống.

**Câu 6.** Trong một thư mục, có quyền `r` nhưng thiếu `x`. Có chắc ứng dụng mở được file con theo đường dẫn tên file không?

> Không. Quyền `x` trên thư mục thường cần cho việc đi qua/tìm mục theo tên. `r` của thư mục không thay thế `x`.

**Câu 7.** `NOFOLLOW_LINKS` có khiến mọi hệ điều hành đều cho đổi permission của một symbolic link không?

> Không. Nó chỉ yêu cầu không theo symbolic link trong bước truy cập thuộc tính; khả năng đọc/sửa metadata của bản thân link còn phụ thuộc provider/filesystem.

**Câu 8.** Có quyền `rw-------` trên file, vậy ứng dụng chắc chắn có thể ghi file bằng mọi cách không?

> Không. Quyền hiệu lực còn tùy danh tính tiến trình, quyền trên thư mục cha, ACL, trạng thái filesystem, mount policy và thay đổi đồng thời. Phải xử lý lỗi khi thực hiện I/O.

## Ghi nhớ

- **`PosixFilePermission`** là enum cho chín quyền owner/group/others × read/write/execute.
- **`PosixFilePermissions`** giúp chuyển giữa `"rwxr-x---"` và `Set<PosixFilePermission>`, hoặc cấp thuộc tính lúc tạo file.
- **`PosixFileAttributeView`** cho phép đọc thuộc tính POSIX và sửa quyền, owner, group khi hệ thống hỗ trợ và caller có đủ quyền.
- **`Files.getPosixFilePermissions` / `Files.setPosixFilePermissions`** là hai phương thức tiện ích cho các thao tác thông dụng.
- **Không mặc định POSIX luôn khả dụng**; kiểm tra view, xử lý `IOException` / `UnsupportedOperationException` và tôn trọng provider.
- **Chín bit quyền không phải toàn bộ mô hình bảo mật**. Hãy hiểu permission trên file/thư mục, ACL, symbolic link, umask và quyền của môi trường trước khi thao tác.

**Tài liệu tham khảo:** [Java SE 17 — `PosixFileAttributeView`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/PosixFileAttributeView.html), [`PosixFilePermission`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/PosixFilePermission.html), [`PosixFilePermissions`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/PosixFilePermissions.html), [`Files`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-attributes-and-permissions-006">Thuộc tính DOS trên Windows</a>

<details>
<summary>Xem chi tiết</summary>

# Thuộc tính DOS trên Windows trong Java NIO.2

Trên Windows, tệp và thư mục có một nhóm cờ thuộc tính quen thuộc: **Read-only**, **Hidden**, **System** và **Archive**. Java NIO.2 cung cấp các giao diện `DosFileAttributes` và `DosFileAttributeView` để đọc, sửa những cờ này trên hệ thống tệp hỗ trợ chúng, thường gặp ở các định dạng FAT/NTFS trên Windows.

Điểm quan trọng: **thuộc tính DOS là metadata của file system, không phải nội dung tệp, không phải phần mở rộng tên và không tương đương toàn bộ quyền truy cập Windows ACL hoặc POSIX**. Một file có cờ `hidden` vẫn có thể được đọc nếu chương trình có quyền; một file có `readonly` không mặc nhiên là “không ai có thể chỉnh sửa hoặc xóa” trong mọi trường hợp.

Bài học sử dụng Java 17+, trình bày cách đọc/sửa cờ bằng API kiểu tường minh hoặc tên thuộc tính dạng chuỗi, cách nhận biết provider không hỗ trợ, lỗi thường gặp, symbolic link và ví dụ tự tạo file tạm an toàn.

## 1. Bốn thuộc tính DOS cần nhớ

| Thuộc tính | Trong Java | Ý nghĩa thường gặp | Điều **không** nên suy ra |
|---|---|---|---|
| **Read-only** | `isReadOnly()` / `setReadOnly(boolean)` | Đánh dấu tệp chỉ đọc theo kiểu DOS | Không đồng nghĩa toàn bộ chính sách quyền ACL, không phải cam kết bất khả xâm phạm |
| **Hidden** | `isHidden()` / `setHidden(boolean)` | Đánh dấu mục nên được ẩn khỏi giao diện hiển thị thông thường | Không phải mã hóa, không phải kiểm soát truy cập |
| **System** | `isSystem()` / `setSystem(boolean)` | Đánh dấu mục liên quan hệ thống | Không biến tệp thành thành phần hệ điều hành hợp lệ |
| **Archive** | `isArchive()` / `setArchive(boolean)` | Gợi ý trạng thái có thể cần lưu trữ/sao lưu | Không bảo đảm backup đã hoặc sẽ diễn ra |

Các cờ có thể được kết hợp. Một tệp có thể vừa `hidden` vừa `readonly`; cập nhật một cờ không có nghĩa phải đặt lại tất cả cờ còn lại.

Java chỉ cung cấp mô hình truy cập các cờ. **Khả năng hỗ trợ và mức thực thi** vẫn do file-system provider, hệ điều hành, loại ổ lưu trữ và quyền của tiến trình quyết định.

### Đối chiếu với File Explorer

Trên Windows, thuộc tính này thường thấy trong hộp thoại **Properties** của File Explorer hoặc qua công cụ dòng lệnh `attrib`. Tuy nhiên, một số cờ như System/Archive không nhất thiết được trình bày giống Read-only/Hidden trong giao diện đơn giản, và cách File Explorer xử lý thư mục có thể khác tệp.

Không thay thế việc đọc metadata từ Java bằng suy đoán dựa trên giao diện Explorer, tên bắt đầu bằng dấu chấm hay đuôi file.

## 2. Hai giao diện cốt lõi

Cả hai ở package:

~~~java
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
~~~

### `DosFileAttributes`: ảnh chụp thuộc tính để **đọc**

`DosFileAttributes` kế thừa `BasicFileAttributes`. Ngoài bốn phương thức riêng `isReadOnly()`, `isHidden()`, `isSystem()`, `isArchive()`, nó còn có các phương thức cơ bản:

- `isRegularFile()`, `isDirectory()`, `isSymbolicLink()`, `isOther()`.
- `size()` cho kích thước do provider báo (byte đối với tệp thường).
- `creationTime()`, `lastModifiedTime()`, `lastAccessTime()`.
- `fileKey()` nếu provider có khóa nhận dạng.

Một đối tượng `DosFileAttributes` là **kết quả đọc tại một thời điểm**; nó không tự cập nhật khi ai đó đổi cờ sau đó. Muốn nhận trạng thái mới, gọi `Files.readAttributes(...)` hoặc `view.readAttributes()` lần nữa.

### `DosFileAttributeView`: giao diện **đọc và cập nhật**

`DosFileAttributeView` kế thừa `BasicFileAttributeView` và có các phương thức:

~~~java
DosFileAttributes readAttributes() throws IOException;
void setReadOnly(boolean value) throws IOException;
void setHidden(boolean value) throws IOException;
void setSystem(boolean value) throws IOException;
void setArchive(boolean value) throws IOException;
~~~

Đây là **các chữ ký minh họa**; để dùng phải có `DosFileAttributeView` gắn với một `Path`. Tên của view này theo Javadoc là `"dos"`, dùng trong các lệnh truy cập động như `"dos:hidden"`.

Lưu ý: không phải mỗi lần sửa cờ đều là thao tác nguyên tử với các hoạt động file system khác. Tài liệu `DosFileAttributeView` cho phép triển khai phải đọc các cờ trước rồi mới cập nhật; việc nhiều tiến trình đồng thời sửa thuộc tính cần được thiết kế và kiểm thử riêng.

## 3. Cách đọc thuộc tính DOS bằng API kiểu tường minh

Có hai cách chính.

### Cách A: `Files.readAttributes`

~~~java
// Mảnh mã minh họa: path là Path của mục cần đọc.
DosFileAttributes attrs =
        Files.readAttributes(path, DosFileAttributes.class);

boolean readOnly = attrs.isReadOnly();
boolean hidden = attrs.isHidden();
boolean system = attrs.isSystem();
boolean archive = attrs.isArchive();
~~~

Nếu file system không hỗ trợ loại thuộc tính yêu cầu, `readAttributes(path, DosFileAttributes.class)` có thể ném `UnsupportedOperationException`. Các lỗi truy cập đường dẫn có thể là `IOException`; chính sách bảo mật có thể tạo `SecurityException`.

### Cách B: `Files.getFileAttributeView`

~~~java
// Mảnh mã minh họa: path là Path hợp lệ.
DosFileAttributeView view =
        Files.getFileAttributeView(path, DosFileAttributeView.class);

if (view == null) {
    // Provider không cung cấp view này.
} else {
    DosFileAttributes attrs = view.readAttributes();
    System.out.println(attrs.isHidden());
}
~~~

`Files.getFileAttributeView(...)` trả `null` khi loại view không khả dụng. Đây là đường đi thuận tiện nếu muốn **kiểm tra hỗ trợ trước khi đọc hoặc cập nhật**.

**Đừng nhầm:** có được một view không bảo đảm mọi thao tác đọc/ghi chắc chắn thành công: tệp có thể không còn tồn tại, quyền không đủ, ổ đĩa gặp lỗi hoặc thao tác bị provider từ chối. Khi dùng view vẫn cần xử lý `IOException`.

## 4. Xác định hỗ trợ thay vì kiểm tra tên hệ điều hành

Không nên viết quy tắc:

~~~java
// Mảnh mã KHÔNG nên dùng làm phép xác định khả năng DOS view.
boolean supported =
        System.getProperty("os.name").contains("Windows");
~~~

Tên OS không phản ánh đầy đủ tính năng của **file system nơi Path nằm**. Cùng một máy Windows có thể truy cập ổ mạng, ZIP file system hoặc provider khác; một nhà cung cấp `FileSystem` trên nền tảng khác cũng có thể cung cấp mô hình DOS.

Các API xác định hỗ trợ:

~~~java
// Mảnh mã minh họa.
boolean providerAdvertises =
        path.getFileSystem()
                .supportedFileAttributeViews().contains("dos");

boolean storeSupports =
        Files.getFileStore(path)
                .supportsFileAttributeView(DosFileAttributeView.class);

DosFileAttributeView view =
        Files.getFileAttributeView(path, DosFileAttributeView.class);
boolean viewAvailable = view != null;
~~~

Điểm khác nhau:

- `supportedFileAttributeViews()` là tập tên view mà **FileSystem** công bố; có thể không mô tả đầy đủ những hạn chế riêng của từng file store.
- `FileStore.supportsFileAttributeView(...)` hỏi **file store**; ngay cả khi báo hỗ trợ, vẫn có thể gặp lỗi theo môi trường hoặc tài nguyên cụ thể. Với default provider, kết quả trên thiết bị không phải lưu trữ cục bộ có thể có giới hạn độ chính xác theo Javadoc.
- `getFileAttributeView(...)` lấy một đối tượng view cho `Path`; nếu không có thì trả `null`, nhưng thao tác thực tế sau đó mới xác nhận truy cập thành công.
- `Files.getFileStore(path)` cũng có thể ném `IOException` nếu không tìm được file store.

Một cách thực hành vững là **lấy view và kiểm tra `null`, rồi xử lý lỗi khi sử dụng**. Nếu chương trình chạy đa nền tảng, hãy định nghĩa rõ nhánh khi DOS view không hỗ trợ thay vì ép kiểu hoặc cố truy cập thuộc tính bằng mọi giá.

## 5. Ví dụ Java 17+: đọc cờ trên file tạm và nhận biết nơi không hỗ trợ

Tạo file **`DosFlagsProbe.java`**:

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;

public class DosFlagsProbe {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("dos-probe-", ".txt");

        try {
            DosFileAttributeView view =
                    Files.getFileAttributeView(
                            file, DosFileAttributeView.class);

            if (view == null) {
                System.out.println("dosView=unsupported");
            } else {
                DosFileAttributes attrs = view.readAttributes();

                System.out.println("dosView=available");
                System.out.println("regularFile=" + attrs.isRegularFile());
                System.out.println("readonly=" + attrs.isReadOnly());
                System.out.println("hidden=" + attrs.isHidden());
                System.out.println("system=" + attrs.isSystem());
                System.out.println("archive=" + attrs.isArchive());
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac -encoding UTF-8 DosFlagsProbe.java
java DosFlagsProbe
~~~

Nếu provider **không hỗ trợ**:

~~~text
dosView=unsupported
~~~

Nếu provider hỗ trợ và đọc thành công, đầu ra có dạng:

~~~text
dosView=available
regularFile=true
readonly=<boolean>
hidden=<boolean>
system=<boolean>
archive=<boolean>
~~~

Trong khối trên, `<boolean>` là **ký hiệu giải thích**, không phải stdout cố định: Java thực sự in `true` hoặc `false`. Các giá trị ban đầu của cờ phụ thuộc file system và cách tạo file; vì thế **không nên bịa một bộ giá trị duy nhất cho mọi máy**. Ví dụ này được thiết kế để tự động chọn nhánh tùy môi trường.

File được tạo bằng `Files.createTempFile` và được xóa trong `finally`. Nếu có lỗi đọc quyền/thiết bị, chương trình có thể ném `IOException` — đó là hành vi bình thường của tác vụ truy cập file system.

## 6. Cập nhật bốn cờ với DosFileAttributeView

Sau khi xác nhận view có sẵn, dùng các setter:

~~~java
// Mảnh mã minh họa, tác động lên file thật: cần quyền và chính sách rõ.
view.setReadOnly(true);  // Bật cờ Read-only.
view.setHidden(true);    // Bật cờ Hidden.
view.setArchive(true);   // Bật cờ Archive.
view.setSystem(true);    // Bật cờ System.
~~~

Đặt `false` để tắt cờ tương ứng. Nhưng **không nên bật System hoặc Read-only tùy tiện trên file thật**: các ứng dụng khác có thể phụ thuộc vào những cờ này; tùy nền tảng, xóa/chỉnh file có thể bị từ chối sau khi bật read-only. Hơn nữa, “System=true” **không** đồng nghĩa file được hệ điều hành xác nhận là thành phần hệ thống.

Khi thao tác trên dữ liệu thực:

1. Đọc trạng thái trước đó để quyết định có cần thay đổi không.
2. Xác nhận chương trình có quyền và thực sự sở hữu tài nguyên.
3. Sửa **chỉ cờ mong muốn**.
4. Đọc lại thuộc tính để xác minh kết quả.
5. Xử lý `IOException`/`SecurityException` và tránh retry vô hạn.
6. Nếu chỉ thay đổi tạm thời, phục hồi trạng thái khi kết thúc.

Không dùng `Files.setAttribute(...)` hoặc setter của view để thử nghiệm trực tiếp trên tệp cấu hình hệ thống, thư mục Windows hay dữ liệu người dùng mà chưa được cho phép.

## 7. Ví dụ Java 17+: bật/tắt Hidden trên file tạm rồi phục hồi

Tạo file **`DosHiddenRoundTrip.java`**:

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;

public class DosHiddenRoundTrip {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("dos-hidden-", ".txt");

        try {
            DosFileAttributeView view =
                    Files.getFileAttributeView(
                            file, DosFileAttributeView.class);

            if (view == null) {
                System.out.println("dosView=unsupported");
            } else {
                boolean original = view.readAttributes().isHidden();

                try {
                    view.setHidden(!original);
                    boolean updated =
                            view.readAttributes().isHidden();
                    System.out.println("changed=" + (updated != original));
                } finally {
                    // Phục hồi cờ ban đầu trên chính file tạm.
                    view.setHidden(original);
                }
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 DosHiddenRoundTrip.java
java DosHiddenRoundTrip
~~~

Trên provider **không hỗ trợ**:

~~~text
dosView=unsupported
~~~

Trên provider **hỗ trợ và cho phép cập nhật thành công**:

~~~text
changed=true
~~~

Nếu file system hỗ trợ view nhưng cấm sửa cờ, lệnh setter có thể ném `IOException`; **đó không phải là một đầu ra ổn định** và cần được xử lý theo ứng dụng thực. Trong ví dụ, trạng thái Hidden ban đầu được lưu rồi phục hồi ở `finally`; sau đó chỉ xóa file tạm.

Việc thay đổi thuộc tính qua Java được thực thi trên file system thật, không phải chỉ đổi một biến Java. Nếu ứng dụng tạo nhiều tác vụ đồng thời hoặc bên ngoài cùng sửa cờ, ví dụ đơn giản này không đảm bảo bảo toàn thay đổi của tác nhân khác. Với file riêng do chương trình tạo, rủi ro này dễ kiểm soát hơn.

## 8. API động: "dos:readonly", "dos:hidden", "dos:system", "dos:archive"

Ngoài giao diện có kiểu, Java hỗ trợ đọc/sửa thuộc tính theo tên view và tên thuộc tính dạng chuỗi:

~~~java
// Mảnh mã minh họa; path là Path thuộc file system hỗ trợ DOS.
Object hidden = Files.getAttribute(path, "dos:hidden");
Files.setAttribute(path, "dos:hidden", Boolean.TRUE);
Files.setAttribute(path, "dos:readonly", Boolean.FALSE);
~~~

Bốn tên thuộc tính động được định nghĩa trong Javadoc:

| Tên đầy đủ | Kiểu khi đọc | Setter tương ứng |
|---|---|---|
| `"dos:readonly"` | `Boolean` | `setReadOnly(boolean)` |
| `"dos:hidden"` | `Boolean` | `setHidden(boolean)` |
| `"dos:system"` | `Boolean` | `setSystem(boolean)` |
| `"dos:archive"` | `Boolean` | `setArchive(boolean)` |

**Chú ý tên:** key động là `readonly` viết liền và chữ thường, **không phải** `readOnly`, `read-only` hay `ReadOnly`. Đừng nhầm tên thuộc tính với tên phương thức Java `isReadOnly()`.

Đọc nhiều thuộc tính một lượt:

~~~java
// Mảnh mã minh họa; cần import java.util.Map.
Map<String, Object> flags = Files.readAttributes(
        path, "dos:readonly,hidden,system,archive");
Boolean isHidden = (Boolean) flags.get("hidden");
~~~

Map chứa các key ngắn như `"readonly"`, `"hidden"`, `"system"`, `"archive"`, **không** bắt buộc gắn tiền tố `"dos:"` trên mỗi key. Với provider không hỗ trợ, thao tác có thể ném `UnsupportedOperationException`; lỗi truy cập file vẫn có thể ném `IOException`.

Nếu dùng API kiểu tường minh, trình biên dịch kiểm tra được tên phương thức. API động hữu ích khi phải xây dựng trình xem metadata tổng quát, nhưng dễ mắc lỗi gõ sai tên và ép kiểu. Với bốn cờ DOS cố định, dùng `DosFileAttributes`/`DosFileAttributeView` thường rõ nghĩa hơn.

## 9. Ví dụ chạy được: nhất quán giữa API kiểu và API động

Tạo file **`DosAttributeApiComparison.java`**:

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.util.Map;

public class DosAttributeApiComparison {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("dos-compare-", ".txt");

        try {
            DosFileAttributeView view =
                    Files.getFileAttributeView(
                            file, DosFileAttributeView.class);

            if (view == null) {
                System.out.println("dosView=unsupported");
                return;
            }

            DosFileAttributes typed = view.readAttributes();
            Map<String, Object> dynamic = Files.readAttributes(
                    file, "dos:readonly,hidden,system,archive");

            boolean equal =
                    typed.isReadOnly() == (Boolean) dynamic.get("readonly")
                    && typed.isHidden() == (Boolean) dynamic.get("hidden")
                    && typed.isSystem() == (Boolean) dynamic.get("system")
                    && typed.isArchive() == (Boolean) dynamic.get("archive");

            System.out.println("sameFlags=" + equal);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 DosAttributeApiComparison.java
java DosAttributeApiComparison
~~~

Nếu không hỗ trợ DOS view:

~~~text
dosView=unsupported
~~~

Nếu view được hỗ trợ và không có tác nhân nào đồng thời thay đổi thuộc tính giữa hai lần đọc:

~~~text
sameFlags=true
~~~

Hai phép đọc được thực hiện **không đồng thời**, nên kết quả trên là mô hình cho một file tạm không bị bên khác sửa; nếu có thay đổi giữa hai phép đọc, giá trị so sánh có thể khác. Mục tiêu là cho thấy **bốn tên động tương ứng** với bốn getter có kiểu.

## 10. Thuộc tính DOS khác quyền Windows ACL và POSIX

Đây là chỗ dễ hiểu sai nhất khi xây dựng phần mềm quản lý file.

### DOS Read-only không thay thế ACL

Cờ `readonly` thường dùng như một dấu đánh dấu nhằm hạn chế cập nhật, nhưng Javadoc `DosFileAttributes` nói rõ **có hay không có cơ chế cưỡng chế ngăn sửa/xóa tùy thuộc triển khai**. Trên Windows, NTFS còn có **ACL** quyết định tài khoản hoặc nhóm nào có quyền đọc, ghi, xóa, thay đổi quyền...

Vì vậy:

- `attrs.isReadOnly() == false` **không** chứng minh tiến trình có quyền ghi file.
- `attrs.isReadOnly() == true` **không** là bằng chứng tuyệt đối rằng file không thể bị thay đổi trong mọi ngữ cảnh.
- `Files.isWritable(path)` phản ánh khả năng ghi từ góc nhìn JVM hiện tại nhưng cũng có hạn chế, có thể lỗi thời ngay sau khi kiểm tra.
- Để đọc/sửa ACL, xem `AclFileAttributeView` khi provider hỗ trợ, và luôn áp chính sách phân quyền đúng hệ điều hành.

Không xây dựng kiểm soát truy cập dữ liệu nhạy cảm dựa duy nhất trên DOS Read-only hoặc Hidden.

### DOS Hidden khác tên bắt đầu bằng dấu chấm trên Unix

`Files.isHidden(path)` có quy tắc **phụ thuộc platform/provider**. Theo Javadoc:

- Trên Windows, việc một mục được coi là hidden thường dựa vào **DOS hidden flag**.
- Trên Unix, tên bắt đầu bằng dấu chấm `.` thường được coi là hidden.
- Một file tên `.secret` trên Windows **không tự động có cờ DOS Hidden chỉ vì tên bắt đầu bằng dấu chấm**.
- Một file có cờ DOS Hidden không cần có tiền tố `.` ở tên.

Bởi vậy `Files.isHidden(path)` là API tương đối portable nếu mục tiêu là **“hệ điều hành này coi tệp có ẩn không?”**. Nếu mục tiêu là **đọc/sửa đúng cờ DOS**, hãy dùng `DosFileAttributes.isHidden()` hoặc `DosFileAttributeView.setHidden(...)`.

### DOS khác POSIX permissions

Trên Unix/Linux, quyền phổ biến như `rwxr-x---` được quản lý bởi `PosixFilePermissions`/`PosixFileAttributeView`. Nó không tương đương một-một với bốn cờ DOS. Cùng một chương trình muốn chạy trên Windows và Linux phải chọn abstraction phù hợp, hoặc viết nhánh xử lý capability-based thay vì giả định cả hai mô hình giống nhau.

## 11. Symlink, NOFOLLOW_LINKS và rủi ro thay đổi đích

Các API `Files.getFileAttributeView(...)` và `Files.readAttributes(...)` nhận `LinkOption...`. Mặc định chúng **theo symbolic link** tới đích cuối nếu provider hỗ trợ link:

~~~java
// Mảnh mã minh họa; cần import LinkOption.
DosFileAttributes targetAttrs = Files.readAttributes(
        link, DosFileAttributes.class);

DosFileAttributes linkAttrs = Files.readAttributes(
        link, DosFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
~~~

`NOFOLLOW_LINKS` yêu cầu xét **đối tượng link**, không đi theo tới target cuối, trong trường hợp provider hỗ trợ. Nhưng không có bảo đảm mọi provider đều cung cấp DOS view trên bản thân symbolic link. Một số file system có thể không hỗ trợ thao tác đọc/sửa một cờ DOS trên link hoặc có quy tắc riêng.

Tương tự với view dùng để sửa:

~~~java
// Mảnh mã minh họa.
DosFileAttributeView linkView = Files.getFileAttributeView(
        link, DosFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
~~~

Đừng coi `getFileAttributeView(..., NOFOLLOW_LINKS)` là cách phòng chống mọi cuộc tấn công race: một liên kết hoặc đường dẫn có thể bị thay đổi giữa thời điểm chọn view và khi cập nhật. Trong ứng dụng thao tác trên path không tin cậy, cần kiểm soát ownership, quyền, link và khả năng thay đổi của file system.

Với path do người dùng cung cấp, kể cả thao tác tưởng nhỏ như bật Hidden cũng là **thay đổi trạng thái của tài nguyên thật**. Nên giới hạn quyền và chỉ thay đổi những file mà ứng dụng quản lý.

## 12. Thứ tự thay đổi cờ và vấn đề nhất quán

Giả sử ứng dụng muốn bật cả `hidden` và `archive`:

~~~java
// Mảnh mã minh họa trên view có quyền sửa.
view.setHidden(true);
view.setArchive(true);
~~~

Hai lệnh này **không được cam kết tạo thành một transaction**. Có thể bật Hidden thành công rồi bật Archive thất bại, khiến file ở trạng thái cập nhật một phần. Ngoài ra, giữa hai lời gọi, một thread hoặc process khác có thể đọc/sửa cờ.

Khi nghiệp vụ yêu cầu một trạng thái nhất quán:

1. Xác định thuộc tính nào thực sự cần chỉnh, không tự ý sửa System/Archive cho mọi file.
2. Tính đến khả năng một phần thao tác đã hoàn thành.
3. Đọc lại trạng thái sau khi sửa.
4. Cân nhắc cơ chế phối hợp giữa những process có quyền thay đổi cùng file.
5. Thiết kế cách xử lý thất bại nhưng không xóa các thay đổi hợp lệ của tiến trình khác.

Không giả định `Files.readAttributes(...)` luôn là một ảnh chụp nguyên tử hoàn toàn tương ứng với mọi thao tác đồng thời; Javadoc cho biết tính nguyên tử của phép đọc bulk **phụ thuộc triển khai**.

### Archive flag không phải hệ thống sao lưu

`archive` là cờ truyền thống thường được phần mềm backup sử dụng để gợi ý dữ liệu đã thay đổi hoặc cần lưu trữ. Nhưng có các lưu ý:

- Không có nghĩa Windows hay Java đã sao lưu file.
- Công cụ backup khác nhau có thể dùng hoặc bỏ qua cờ này.
- Một số hoạt động ghi/sửa có thể cập nhật cờ theo quy tắc của file system.
- Không dùng `archive=false` làm bằng chứng chắc chắn rằng dữ liệu đã được backup an toàn.

Nếu hệ thống có yêu cầu khôi phục sau mất dữ liệu, phải có chính sách backup, kiểm tra snapshot và thử khôi phục thực tế.

## 13. Lỗi thường gặp và xử lý

| Trường hợp | Hành vi/cách hiểu | Xử lý phù hợp |
|---|---|---|
| `getFileAttributeView(..., DosFileAttributeView.class)` trả `null` | DOS view không khả dụng cho path/provider | Dùng nhánh fallback hoặc báo không hỗ trợ |
| `Files.readAttributes(..., DosFileAttributes.class)` ném `UnsupportedOperationException` | View yêu cầu không được hỗ trợ | Tránh giả định mọi OS đều có |
| Setter ném `IOException` | Lỗi quyền, I/O, path biến mất hoặc provider từ chối | Giữ ngữ cảnh lỗi, không retry vô hạn |
| `Files.setAttribute(path, "dos:readOnly", ...)` | Sai tên thuộc tính động | Dùng `"dos:readonly"` |
| `Files.setAttribute(path, "dos:hidden", "true")` | Giá trị là chuỗi, không phải `Boolean` | Dùng `Boolean.TRUE` hoặc `true` |
| Đổi `readonly` rồi quên phục hồi | Có thể ảnh hưởng thao tác ghi/xóa sau này | Sửa cờ trên file sở hữu, lưu trạng thái cũ |
| Coi `hidden` là cách bảo mật nội dung | Hidden chỉ là metadata/hiển thị | Dùng quyền/ACL, mã hóa hoặc cơ chế bảo mật thực |
| Cho rằng dấu `.` ở đầu file tương đương DOS Hidden | Quy tắc khác giữa Unix/Windows | Phân biệt `Files.isHidden` và DOS view |
| Đọc một lần rồi coi attrs cập nhật trực tiếp | `DosFileAttributes` là kết quả snapshot | Gọi `readAttributes()` lại |
| Sửa nhiều cờ và mong nguyên tử | Không có transaction mặc định | Thiết kế xử lý cập nhật một phần |
| Mở một đường dẫn có link và giả định không follow | Mặc định nhiều API theo symlink | Xác định rõ `NOFOLLOW_LINKS` và khả năng hỗ trợ |
| Dùng `archive` để xác nhận backup đã hoàn tất | Cờ Archive không là chứng nhận backup | Kiểm tra hệ thống sao lưu thực tế |

### Ngoại lệ cần dự liệu

- `IOException`: lỗi truy cập file, path không còn tồn tại, thiết bị hay provider gặp trục trặc.
- `UnsupportedOperationException`: attribute view không hỗ trợ; thường gặp trên provider không có DOS attributes.
- `IllegalArgumentException`: tên thuộc tính động sai/không phù hợp, tùy API.
- `ClassCastException`: truyền giá trị sai kiểu vào API động `Files.setAttribute(...)`.
- `SecurityException`: chính sách quyền Java/triển khai từ chối thao tác.

Trước khi xử lý ngoại lệ, phân biệt đây là **lỗi kỹ thuật** hay **tính năng vốn không hỗ trợ trên nền tảng**. Với ứng dụng đa nền tảng, không nên làm cả chương trình thất bại chỉ vì phần hiển thị một cờ DOS không tồn tại nếu nghiệp vụ cho phép bỏ qua.

## 14. Khi nào nên chọn API nào?

| Nhu cầu | API khuyến nghị | Lý do |
|---|---|---|
| Kiểm tra file được OS coi là ẩn hay không | `Files.isHidden(path)` | Có abstraction theo platform |
| Đọc chính xác bốn cờ DOS | `Files.readAttributes(path, DosFileAttributes.class)` | Kiểu rõ ràng, đọc nhiều thuộc tính |
| Đọc rồi thay đổi cờ | `Files.getFileAttributeView(path, DosFileAttributeView.class)` | Kiểm tra `null` và dùng getter/setter |
| Chỉ lấy một cờ động | `Files.getAttribute(path, "dos:hidden")` | Thuận tiện khi xây dựng metadata viewer |
| Sửa một cờ động | `Files.setAttribute(path, "dos:hidden", true)` | Hợp đồng tên thuộc tính rõ ràng |
| Kiểm tra quyền ghi thực tế của JVM | `Files.isWritable(path)` kèm xử lý thao tác ghi thật | DOS readonly không đủ thay ACL |
| Quản lý Windows ACL | `AclFileAttributeView` (nếu hỗ trợ) | ACL là mô hình quyền riêng |
| Quản lý quyền Unix | `PosixFileAttributeView` (nếu hỗ trợ) | POSIX permission bits riêng |
| Ứng dụng chạy trên nhiều provider | Capability check + graceful fallback | Không hard-code `os.name` |

Lựa chọn giữa `DosFileAttributes` và `DosFileAttributeView` cũng là lựa chọn giữa **đọc một snapshot** và **có đối tượng cho phép đổi thuộc tính**. Đừng thêm quyền ghi metadata vào một module chỉ cần quan sát.

## 15. Checklist thực hành an toàn

1. Tạo `Path` đúng provider; đừng suy ra hỗ trợ DOS view chỉ vì OS là Windows.
2. Lấy `DosFileAttributeView` và kiểm tra `null`, hoặc gọi `Files.readAttributes` rồi xử lý `UnsupportedOperationException`.
3. Khi chỉ muốn đọc, dùng getter; không sửa cờ để “thử xem có hỗ trợ không” trên file thật.
4. Khi cần cập nhật, chỉ sửa cờ được yêu cầu và nhớ rằng setter có thể ném `IOException`.
5. Nếu sửa tạm thời, lưu cờ cũ và cố gắng khôi phục trong `finally`; thử nghiệm trên file tạm.
6. Với Read-only, Hidden, System và Archive, hiểu đúng vai trò của từng cờ, không biến chúng thành cơ chế bảo mật tự chế.
7. Định rõ xử lý symbolic link; sử dụng `NOFOLLOW_LINKS` nếu cần và provider hỗ trợ.
8. Đọc lại attrs nếu cần trạng thái sau sửa; không coi object `DosFileAttributes` cũ là live.
9. Khi truy cập nhiều file hoặc thay nhiều cờ, tính tới race và cập nhật một phần; không hứa tính nguyên tử ngoài hợp đồng API.
10. Với dữ liệu quan trọng, phân biệt DOS flag, file permissions, ACL, và chiến lược sao lưu thực sự.

## 16. Ghi nhớ cốt lõi

- **DOS attributes trên Windows** gồm bốn cờ: `readonly`, `hidden`, `system`, `archive`.
- **`DosFileAttributes`** để đọc (gồm các thuộc tính `BasicFileAttributes`); **`DosFileAttributeView`** để đọc và cập nhật.
- **`Files.getFileAttributeView(...)` có thể trả `null`** nếu view không khả dụng; đừng giả định mọi file system đều hỗ trợ DOS.
- **`Files.getAttribute/setAttribute`** dùng tên dạng `"dos:hidden"`, `"dos:readonly"`, v.v.
- **Hidden không phải bảo mật; Read-only không thay ACL; Archive không xác nhận đã backup.**
- Cần xử lý lỗi I/O, symbolic link, khác biệt provider và trạng thái thay đổi giữa các thao tác.

### Tham khảo chính thức

- [Java SE 17 — DosFileAttributes](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/DosFileAttributes.html)
- [Java SE 17 — DosFileAttributeView](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/DosFileAttributeView.html)
- [Java SE 17 — Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [Java SE 17 — FileStore](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileStore.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-attributes-and-permissions-007">Owner, UserPrincipal và AclFileAttributeView</a>

<details>
<summary>Xem chi tiết</summary>

# Owner, UserPrincipal và AclFileAttributeView trong Java NIO.2

## 1. Mục tiêu bài học

Khi đọc/ghi tệp, ngoài nội dung byte và đường dẫn, hệ thống tệp còn lưu **thuộc tính** như chủ sở hữu (owner) và quy tắc truy cập. Java NIO.2 cung cấp các API để tra cứu người dùng/nhóm và, trên các hệ thống hỗ trợ, đọc hoặc cập nhật danh sách kiểm soát truy cập **ACL (Access Control List)**.

Ba khái niệm trọng tâm:

- **Owner**: một danh tính được hệ thống tệp ghi nhận là chủ sở hữu của tệp/thư mục. Trong Java, owner được biểu diễn qua `UserPrincipal`.
- **`UserPrincipal`**: giao diện mô tả danh tính người dùng, có phương thức `getName()`. `GroupPrincipal` mở rộng `UserPrincipal` cho danh tính nhóm; danh tính thực được quản lý bởi filesystem/provider/hệ điều hành.
- **`AclFileAttributeView`**: giao diện NIO.2 dùng để lấy/chỉnh ACL dạng có thứ tự và làm việc với chủ sở hữu khi filesystem hỗ trợ mô hình quyền tương ứng.

Sau bài này, bạn sẽ biết:

1. Đọc owner bằng `Files.getOwner(path)`, phân biệt thao tác đọc và thay đổi owner.
2. Tra cứu danh tính bằng `UserPrincipalLookupService` và hiểu vì sao tên hiển thị không đủ để khẳng định cùng một tài khoản.
3. Kiểm tra khả năng hỗ trợ ACL trước khi gọi `getAcl()`/`setAcl()`.
4. Phân tích `AclEntry`: principal, kiểu entry, quyền, cờ kế thừa, thứ tự.
5. Phân biệt ACL với quyền POSIX (`rwx`), DOS attributes và quyền truy cập hiệu lực của một tiến trình.
6. Xử lý `IOException`, `UnsupportedOperationException`, `SecurityException`, khác biệt provider, symbolic link và race condition.
7. Viết Java 17+ đọc metadata an toàn, đồng thời tránh vô tình thay đổi quyền của tệp thật.

> **Tư duy quan trọng:** đọc được owner **không chứng minh** người đang chạy có thể đọc/ghi tệp; đọc được ACL **không có nghĩa** bạn đã tính được đầy đủ quyền hiệu lực; còn `setAcl()`/`setOwner()` là thao tác thay đổi quyền có rủi ro, cần chính sách rõ ràng.

## 2. Chủ sở hữu (owner) là gì?

Owner thường là danh tính mà filesystem gắn với một đối tượng tệp. Trên hệ Unix/POSIX, tệp có user owner và group owner; trên Windows, owner và DACL là các khái niệm trong security descriptor. Java cung cấp lớp trừu tượng chung, nhưng **quy tắc quản trị và quyền hiệu lực do hệ điều hành và filesystem quyết định**.

Lưu ý:

- Owner không đồng nghĩa tác giả ban đầu hoặc người tạo file mãi mãi; quyền sở hữu có thể được thay đổi theo chính sách của hệ thống.
- Một người là owner không có nghĩa mặc định có mọi quyền đọc/ghi/xóa.
- `UserPrincipal.getName()` là **tên danh tính theo provider**, không phải khóa toàn cục dùng được giữa Windows, Linux và mọi miền tài khoản.
- ACL có thể cấp/khước từ quyền cho các principal khác ngoài owner; nhóm và đặc quyền quản trị có thể tham gia đánh giá quyền.
- File có thể là symlink, và thuộc tính owner của **link** có thể khác owner của **đích**, tùy khả năng hỗ trợ và lựa chọn follow link.

### 2.1. Các API chính

| Mục tiêu | API | Kết quả / giới hạn |
| --- | --- | --- |
| Đọc owner | `Files.getOwner(path)` | `UserPrincipal`; mặc định theo liên kết tượng trưng |
| Đọc owner của link (khi hỗ trợ) | `Files.getOwner(path, NOFOLLOW_LINKS)` | Phụ thuộc provider/link semantics |
| Đặt owner | `Files.setOwner(path, principal)` | Thay metadata; yêu cầu quyền thích hợp |
| Lấy view owner | `Files.getFileAttributeView(path, FileOwnerAttributeView.class)` | Có thể nhận `null` nếu view không được hỗ trợ |
| Lấy ACL view | `Files.getFileAttributeView(path, AclFileAttributeView.class)` | Có thể nhận `null` |
| Đọc ACL | `view.getAcl()` | `List<AclEntry>` có thứ tự |
| Ghi ACL | `view.setAcl(entries)` | Thay thế danh sách ACL; nguy cơ mất quyền nếu làm sai |
| Đọc owner từ ACL view | `view.getOwner()` | `AclFileAttributeView` kế thừa `FileOwnerAttributeView` |
| Tra cứu tài khoản | `FileSystem.getUserPrincipalLookupService()` | `lookupPrincipalByName` hoặc `lookupPrincipalByGroupName` |
| POSIX owner/group/mode | `PosixFileAttributeView` và `Files.readAttributes(..., PosixFileAttributes.class)` | Chỉ với filesystem hỗ trợ POSIX |

`Files.getOwner` và `Files.setOwner` có thể sử dụng cơ chế `FileOwnerAttributeView` theo provider. Khi một tính năng không được hỗ trợ, không ép provider tạo thông tin giả; hãy phát hiện và xử lý đúng.

## 3. `UserPrincipal` và `GroupPrincipal`

`java.nio.file.attribute.UserPrincipal` là một giao diện mở rộng `java.security.Principal`. Phương thức quan trọng là:

```java
String displayName = principal.getName();
```

Danh tính có thể đến từ một hệ thống user/group riêng: tài khoản cục bộ, domain, một filesystem từ xa hoặc mô hình do provider triển khai. **Không tự suy luận** danh tính thật bằng cách so sánh chuỗi tên hiển thị với tên nhập tự do của người dùng.

`GroupPrincipal` là kiểu con của `UserPrincipal` dùng cho **nhóm**. Một entry ACL có thể trỏ tới principal của một user hay một group tùy filesystem.

### 3.1. Tra cứu principal bằng `UserPrincipalLookupService`

Với hệ thống có dịch vụ tra cứu thích hợp:

```java
// Minh họa API; tên tài khoản phải được lấy từ cấu hình tin cậy.
UserPrincipalLookupService lookup =
        path.getFileSystem().getUserPrincipalLookupService();

UserPrincipal user = lookup.lookupPrincipalByName("example-user");
GroupPrincipal group = lookup.lookupPrincipalByGroupName("example-group");
```

**Đoạn này không phải chương trình độc lập**: cần `import java.nio.file.Path;`, `java.nio.file.attribute.*;`, biến `path` hợp lệ và các tài khoản **thực sự tồn tại trong provider**.

Điểm phải nhớ:

- `lookupPrincipalByName` và `lookupPrincipalByGroupName` có thể ném `UserPrincipalNotFoundException` nếu không thấy danh tính.
- Có thể phát sinh `IOException` khi gặp lỗi tra cứu hoặc `SecurityException` nếu môi trường áp chính sách bảo mật tương ứng.
- Tên tài khoản được diễn giải theo hệ thống/provider; `"admin"` hay `"Everyone"` không có nghĩa phổ quát.
- **Không dùng `() -> "somebody"` làm đối tượng principal để gọi `Files.setOwner` trên filesystem thật.** Provider có thể yêu cầu principal do chính nó tạo/tra cứu, hoặc không cho gán.
- `getName()` chỉ cung cấp biểu diễn tên; tính đúng của phép gán quyền cần dựa trên principal mà provider chấp nhận.

### 3.2. Phân biệt “biết tên”, “biết danh tính” và “có quyền”

| Nhận định | Có thể kết luận chỉ từ `getName()`? |
| --- | --- |
| Provider trả một tên danh tính | Có |
| Principal này là cùng tài khoản trên hai máy khác nhau | Không |
| Principal chắc chắn có quyền đọc file | Không |
| Quy tắc ACL đã được thực thi trên filesystem hiện tại | Không |
| Chính ứng dụng có quyền thay owner/ACL | Không |
| User và group với tên tương tự là cùng một loại principal | Không |

Tránh lưu duy nhất một chuỗi tên để dùng lại trên nhiều hệ thống khi chương trình cần độ chính xác về danh tính.

## 4. Đọc owner bằng `Files.getOwner`

Ví dụ API:

```java
Path file = Path.of("data", "report.txt");
UserPrincipal owner = Files.getOwner(file);
System.out.println(owner.getName());
```

Đây là **đoạn minh họa**, không phải chương trình độc lập vì cần import và file có thể chưa tồn tại. Kết quả là tên principal theo provider, **không có stdout cố định**.

Đọc owner có thể gặp:

1. `NoSuchFileException` nếu đường dẫn không tồn tại.
2. `AccessDeniedException` hoặc `IOException` khi filesystem không cho đọc thuộc tính.
3. `UnsupportedOperationException` khi provider không hỗ trợ thuộc tính owner theo cách gọi đó.
4. `SecurityException` nếu có chính sách kiểm soát bảo mật tương ứng.
5. Thay đổi ngoài dự kiến nếu một tiến trình khác sửa/đổi tên tệp giữa lúc kiểm tra và đọc.

### 4.1. Symlink: theo đích hay đọc thuộc tính link?

Thông thường `Files.getOwner(path)` theo symbolic link. Nếu muốn làm việc với chính đối tượng link:

```java
UserPrincipal linkOwner = Files.getOwner(
        path, LinkOption.NOFOLLOW_LINKS);
```

Tuy nhiên, **không phải mọi provider đều hỗ trợ cùng semantics** cho owner của symlink. Có thể không hỗ trợ thao tác; cần thử nghiệm trên môi trường sẽ triển khai. `NOFOLLOW_LINKS` không tự khóa symlink để tránh thay đổi đồng thời (TOCTOU).

### 4.2. Đọc owner từ `FileOwnerAttributeView`

`FileOwnerAttributeView` cho phép lấy/đặt owner qua thuộc tính view:

```java
FileOwnerAttributeView view = Files.getFileAttributeView(
        path, FileOwnerAttributeView.class);

if (view == null) {
    throw new UnsupportedOperationException(
            "Owner view unavailable");
}
UserPrincipal owner = view.getOwner();
```

Đây là đoạn minh họa. Ưu điểm của `view` là bạn có thể làm việc với thuộc tính của filesystem qua một interface xác định. Nếu view là `null`, không được gọi `getOwner()` trên nó.

## 5. ACL là gì? Vì sao khác quyền `rwx`?

**ACL (Access Control List)** là danh sách các quy tắc truy cập gắn với một đối tượng. Mỗi quy tắc thường chỉ rõ:

- **Principal**: ai được nhắc đến (user hoặc group).
- **Type**: cho phép, từ chối hoặc loại thông tin kiểm toán theo mô hình ACL.
- **Permissions**: các hành động như đọc dữ liệu, ghi dữ liệu, đọc ACL, đổi owner.
- **Flags**: một số quy tắc kế thừa hoặc phạm vi áp dụng.

Ví dụ **minh họa**, không khẳng định là ACL thật trên một hệ thống cụ thể:

```text
Entry 1: ALLOW team-editors → READ_DATA, WRITE_DATA
Entry 2: DENY  guest        → WRITE_DATA
Entry 3: ALLOW owner        → READ_DATA, READ_ACL
```

Không coi ACL là một tập quyền không có thứ tự. **Danh sách có thứ tự** và thứ tự entry có thể ảnh hưởng đến cách hệ thống áp dụng chúng. Việc đánh giá quyền hiệu lực còn liên quan token nhóm, inheritance, đặc quyền, policy và filesystem.

### 5.1. POSIX mode và ACL view không đồng nhất

Trên Unix/POSIX, quyền cơ bản thường được thể hiện qua ba nhóm `owner/group/others` và các bit `rwx`. Java cung cấp:

```java
PosixFileAttributes attributes = Files.readAttributes(
        path, PosixFileAttributes.class);
Set<PosixFilePermission> mode = attributes.permissions();
```

Đây là đoạn minh họa cần filesystem hỗ trợ POSIX; không chạy trên mọi Windows filesystem.

`AclFileAttributeView` dành cho mô hình ACL phong phú hơn, thường gắn với các filesystem có ACL kiểu tương thích NFSv4/Windows. **Không giả định** mọi filesystem POSIX đều có `AclFileAttributeView`, cũng không cho rằng ACL kiểu POSIX mặc định được biểu diễn đầy đủ bằng Java `AclFileAttributeView`. Một filesystem có thể cung cấp owner/POSIX view nhưng **không** cung cấp ACL view.

### 5.2. Khác thuộc tính DOS

`DosFileAttributeView` có các cờ như read-only, hidden, system, archive, không phải danh sách user/group ACE. Cờ read-only không thay thế phân tích quyền ACL hoặc POSIX, và `Files.isReadable`/`isWritable` chỉ là kiểm tra khả năng truy cập tại một thời điểm.

## 6. `AclFileAttributeView`: khả năng hỗ trợ và API

Lấy view bằng:

```java
AclFileAttributeView view = Files.getFileAttributeView(
        path, AclFileAttributeView.class);
```

Nếu provider không hỗ trợ ACL cho đường dẫn, `view` có thể là **`null`**. Đây là kiểm tra cần thiết trước khi gọi `view.getAcl()`. Nếu view có nhưng thao tác thất bại vì quyền hoặc lỗi I/O, vẫn có thể ném ngoại lệ.

Các thành phần chính:

| API | Vai trò |
| --- | --- |
| `AclFileAttributeView` | View thuộc tính ACL và owner |
| `AclFileAttributeView.getAcl()` | Đọc danh sách `List<AclEntry>` theo thứ tự |
| `AclFileAttributeView.setAcl(List<AclEntry>)` | Đặt **toàn bộ danh sách** ACL theo yêu cầu |
| `AclFileAttributeView.getOwner()` | Đọc owner |
| `AclFileAttributeView.setOwner(UserPrincipal)` | Thay owner theo quyền/provider |
| `AclEntry` | Một quy tắc ACL, thường có principal, type, permissions, flags |
| `AclEntryType` | `ALLOW`, `DENY`, `AUDIT`, `ALARM` |
| `AclEntryPermission` | Các hành động như `READ_DATA`, `WRITE_DATA`, `READ_ACL`, `WRITE_ACL`, `WRITE_OWNER` |
| `AclEntryFlag` | Cờ về cách kế thừa ACE ở thư mục/con |
| `UserPrincipalLookupService` | Tìm danh tính do provider hiểu |

`getAcl()` trả danh sách biểu diễn ACL, có thể rỗng trên một số file/provider. **Đừng sắp xếp lại theo tên user**; entry order có thể có ý nghĩa khi đánh giá quyền.

### 6.1. Đọc danh sách ACL

Đoạn code minh họa:

```java
AclFileAttributeView view =
        Files.getFileAttributeView(path, AclFileAttributeView.class);

if (view != null) {
    List<AclEntry> entries = view.getAcl();
    for (int i = 0; i < entries.size(); i++) {
        AclEntry entry = entries.get(i);
        System.out.println(i + " " + entry.type()
                + " principal=" + entry.principal().getName()
                + " permissions=" + entry.permissions()
                + " flags=" + entry.flags());
    }
}
```

Đây **không phải class chạy độc lập** vì chưa khai báo import và `path`. Kết quả phụ thuộc ACL thực và không có stdout chung.

Không log danh sách ACL nhạy cảm ra nơi công khai. Trong chương trình thật, phân quyền cho chức năng xem ACL và xử lý lỗi riêng theo từng tệp.

### 6.2. Kiểm tra khả năng hỗ trợ qua `FileStore`

`FileStore.supportsFileAttributeView(AclFileAttributeView.class)` là tín hiệu về khả năng hỗ trợ của file store, ví dụ:

```java
boolean storeSaysAcl = Files.getFileStore(path)
        .supportsFileAttributeView(AclFileAttributeView.class);
```

Đây là đoạn minh họa (cần import và `path`). Nó không phải sự đảm bảo rằng **mọi đường dẫn/thao tác** đều cấp quyền đọc hoặc chỉnh ACL; kiểm tra view cụ thể rồi gọi thao tác thật và bắt ngoại lệ vẫn là bắt buộc. `getFileStore` bản thân cũng có thể ném `IOException`.

## 7. `AclEntry` gồm những gì?

Một entry có các thuộc tính bất biến:

- `entry.principal()` — danh tính bị ảnh hưởng.
- `entry.type()` — loại (allow/deny/audit/alarm).
- `entry.permissions()` — tập các `AclEntryPermission`.
- `entry.flags()` — các `AclEntryFlag`.

### 7.1. Các kiểu ACL entry

| `AclEntryType` | Ý nghĩa khái quát |
| --- | --- |
| `ALLOW` | Quy tắc cấp các quyền chỉ định |
| `DENY` | Quy tắc phủ nhận các quyền chỉ định |
| `AUDIT` | Quy tắc liên quan ghi nhận các truy cập theo policy |
| `ALARM` | Quy tắc liên quan báo hiệu truy cập theo policy |

Cách thực thi audit/alarm và hỗ trợ từng entry type phụ thuộc nền tảng/provider. Không coi việc tạo được một đối tượng `AclEntry` trong bộ nhớ là bằng chứng filesystem sẽ chấp nhận `setAcl` với entry đó.

### 7.2. Các quyền thường gặp

| Quyền | Đối tượng thao tác theo ngữ cảnh |
| --- | --- |
| `READ_DATA` / `LIST_DIRECTORY` | Đọc dữ liệu file hoặc liệt kê nội dung thư mục |
| `WRITE_DATA` / `ADD_FILE` | Ghi dữ liệu file hoặc thêm file trong directory |
| `APPEND_DATA` / `ADD_SUBDIRECTORY` | Nối dữ liệu file hoặc thêm directory con |
| `READ_NAMED_ATTRS` | Đọc các thuộc tính được đặt tên |
| `WRITE_NAMED_ATTRS` | Ghi các thuộc tính được đặt tên |
| `EXECUTE` | Chạy file hoặc đi qua thư mục theo semantics filesystem |
| `DELETE` | Xóa đối tượng |
| `DELETE_CHILD` | Xóa mục con trong thư mục |
| `READ_ATTRIBUTES` / `WRITE_ATTRIBUTES` | Đọc/ghi thuộc tính cơ bản |
| `READ_ACL` / `WRITE_ACL` | Đọc/ghi danh sách ACL |
| `WRITE_OWNER` | Thay đổi thông tin owner |
| `SYNCHRONIZE` | Khả năng đồng bộ truy cập theo mô hình ACL |

Một vài cặp tên quyền là **alias/cách dùng theo kiểu đối tượng** trong Java; ví dụ `READ_DATA` và `LIST_DIRECTORY` mô tả cùng bit tương ứng nhưng mang ý nghĩa file hoặc directory khác nhau. Không tự dựng mô hình “mỗi tên là một bit độc lập” nếu API khai báo tương đương.

### 7.3. Các cờ kế thừa

| Cờ | Vai trò khái quát |
| --- | --- |
| `FILE_INHERIT` | Quy tắc có thể được kế thừa bởi file con |
| `DIRECTORY_INHERIT` | Quy tắc có thể được kế thừa bởi thư mục con |
| `NO_PROPAGATE_INHERIT` | Hạn chế phạm vi lan truyền kế thừa |
| `INHERIT_ONLY` | Entry dành cho kế thừa, không áp trực tiếp ở đối tượng hiện tại theo semantics thích hợp |

Không suy từ các tên cờ rằng mọi filesystem đều triển khai kế thừa như nhau. Đặc biệt, **ACL kế thừa** từ thư mục cha có thể dẫn tới những ACE mới khi file được tạo, tùy chính sách hệ thống.

## 8. Java 17+ hoàn chỉnh: tạo một `AclEntry` minh họa trong bộ nhớ

Ví dụ này **không sửa ACL thật**. Nó sử dụng một `UserPrincipal` giả lập chỉ để học về object model. Bản thân `AclEntry` được tạo qua builder; không mở filesystem.

Lưu vào tệp **`AclEntryModelDemo.java`**:

```java
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.UserPrincipal;

public class AclEntryModelDemo {
    public static void main(String[] args) {
        // Demo principal only; never pass it to Files.setOwner.
        UserPrincipal demo = () -> "demo-user";

        AclEntry entry = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(demo)
                .setPermissions(
                        AclEntryPermission.READ_DATA,
                        AclEntryPermission.READ_ACL)
                .build();

        System.out.println("Type: " + entry.type());
        System.out.println("Principal: "
                + entry.principal().getName());
        System.out.println("Can read data: "
                + entry.permissions().contains(
                        AclEntryPermission.READ_DATA));
        System.out.println("Can write ACL: "
                + entry.permissions().contains(
                        AclEntryPermission.WRITE_ACL));
        System.out.println("Has inheritance flags: "
                + !entry.flags().isEmpty());
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 AclEntryModelDemo.java
java AclEntryModelDemo
```

**Stdout chính xác:**

```text
Type: ALLOW
Principal: demo-user
Can read data: true
Can write ACL: false
Has inheritance flags: false
```

**Giải thích:**

1. `demo` là `UserPrincipal` tự cài đặt với tên cố định; nó chỉ dùng để minh họa mô hình dữ liệu trong RAM.
2. Builder gán kiểu `ALLOW` và các quyền `READ_DATA`, `READ_ACL`.
3. Không thêm `WRITE_ACL`, nên kiểm tra membership cho quyền đó trả `false`.
4. Không gọi `setFlags`; tập flags rỗng.
5. **Không có file nào bị cấp quyền**. Entry này chỉ là object Java; để áp dụng ACL thực cần view hỗ trợ, principal hợp lệ do provider chấp nhận và quyền sửa ACL của người gọi.

Không diễn giải dòng `Can read data: true` là “tiến trình hiện tại có quyền đọc một tệp”: nó chỉ nói rằng **entry giả lập chứa bit `READ_DATA`**.

## 9. Java 17+ hoàn chỉnh: kiểm tra owner và ACL thực ở chế độ chỉ đọc

Ví dụ dưới đây **không thay owner/ACL**. Chương trình chỉ quan sát một đường dẫn do người dùng chỉ định. Nếu không cung cấp đường dẫn, nó thông báo cách dùng, tránh tự quét thư mục.

Lưu thành **`OwnerAclInspect.java`**:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;

public class OwnerAclInspect {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java OwnerAclInspect <path>");
            return;
        }

        Path path = Path.of(args[0]);
        try {
            FileOwnerAttributeView ownerView =
                    Files.getFileAttributeView(
                            path, FileOwnerAttributeView.class);

            if (ownerView == null) {
                System.out.println("Owner view: unsupported");
            } else {
                UserPrincipal owner = ownerView.getOwner();
                System.out.println("Owner: " + owner.getName());
            }

            AclFileAttributeView aclView =
                    Files.getFileAttributeView(
                            path, AclFileAttributeView.class);

            if (aclView == null) {
                System.out.println("ACL view: unsupported");
                return;
            }

            List<AclEntry> entries = aclView.getAcl();
            System.out.println("ACL entries: " + entries.size());

            for (int index = 0; index < entries.size(); index++) {
                AclEntry entry = entries.get(index);
                System.out.println(index + ": "
                        + entry.type()
                        + " principal=" + entry.principal().getName()
                        + " permissions=" + entry.permissions()
                        + " flags=" + entry.flags());
            }
        } catch (IOException failure) {
            System.err.println("I/O error: "
                    + failure.getClass().getSimpleName());
        } catch (UnsupportedOperationException failure) {
            System.err.println("Unsupported view operation");
        } catch (SecurityException failure) {
            System.err.println("Security policy denied operation");
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 OwnerAclInspect.java
java OwnerAclInspect
java OwnerAclInspect my-file.txt
```

Với lệnh **không có đối số**, stdout xác định là:

```text
Usage: java OwnerAclInspect <path>
```

Với `java OwnerAclInspect my-file.txt`, **không có một stdout cố định chung**: đường dẫn có thể không tồn tại, owner khác nhau, view ACL có thể không được hỗ trợ, số lượng/quy tắc entry khác nhau hoặc có lỗi quyền. Điều này là hành vi đúng với API phụ thuộc filesystem.

**Điểm học tập:**

- Kiểm tra `ownerView == null` và `aclView == null` trước khi gọi phương thức.
- Đọc `getAcl()` chỉ khi ACL view hiện diện.
- In `entry.type()`, principal, quyền và flags đúng theo thứ tự từ `List`; code **không sort hoặc đổi ACL**.
- Phân biệt lỗi I/O, view không hỗ trợ và chính sách bảo mật.
- Không gọi `Files.setOwner`, `view.setOwner` hay `view.setAcl`; đây là ví dụ có thể dùng để nghiên cứu metadata an toàn ở chế độ **chỉ đọc**.
- Với path là symlink, mặc định các lời gọi attribute view thường theo link. Nếu muốn xem chính link, cân nhắc overload với `LinkOption.NOFOLLOW_LINKS` và khả năng hỗ trợ thực tế.

## 10. Thay owner bằng `Files.setOwner` — khi nào nên làm?

API:

```java
Files.setOwner(path, principal);
```

Đây là thao tác **thay đổi metadata bảo mật**, không phải lệnh “đặt tên hiển thị cho file”. Khi provider cho phép, caller phải có quyền cần thiết. Người được gán owner nên là `UserPrincipal` phù hợp với filesystem/provider của đường dẫn.

Quy trình có kiểm soát:

1. Chọn một tệp thử trong vùng **do bạn sở hữu**, không dùng tệp cấu hình hệ thống hoặc dữ liệu quan trọng.
2. Xác định provider/owner view có hỗ trợ không.
3. Tra cứu danh tính đích qua `UserPrincipalLookupService` của cùng filesystem (nếu cần).
4. Xác minh tài khoản đích theo chính sách tổ chức; không chấp nhận một tên tùy ý từ request để cấp quyền.
5. Thực hiện `Files.setOwner` với handling `IOException`, `UnsupportedOperationException` và `SecurityException`.
6. Đọc lại `Files.getOwner` và đánh giá theo identity/provider, không chỉ hiển thị tên.
7. Ghi audit về việc ai yêu cầu, tệp nào và kết quả thay đổi quyền.

**Chỉ dùng đoạn sau như cấu trúc API, không thực thi trên file thật nếu không có quyền và quy trình kiểm soát:**

```java
// Minh họa: path và trustedUserName do ứng dụng xác thực cung cấp.
var lookup = path.getFileSystem().getUserPrincipalLookupService();
var newOwner = lookup.lookupPrincipalByName(trustedUserName);
Files.setOwner(path, newOwner);
```

Đây là **đoạn minh họa không độc lập**; không tự suy luận tên người dùng cần gán từ dữ liệu bên ngoài. Không tạo user mới: principal lookup là tra cứu, không phải provisioning tài khoản.

### 10.1. Thay owner không phải tự động cấp quyền đọc/ghi

Sau khi đổi owner, ACL có thể vẫn giữ các entry hiện có; POSIX modes/nhóm và chính sách hệ thống còn ảnh hưởng. Đừng cho rằng `setOwner` thay thế được `setAcl` hoặc ngược lại. Nếu cần “user X có quyền Y”, hãy xây chính sách quyền cụ thể và kiểm chứng phù hợp với provider, thay vì chỉ đổi owner.

## 11. Thay ACL bằng `setAcl`: rủi ro và quy trình an toàn

API:

```java
view.setAcl(entries);
```

Đây **không phải thao tác cộng thêm một dòng ACL đơn giản**. Nó yêu cầu provider áp dụng **danh sách entry mới** theo hợp đồng view. Nếu xây danh sách chỉ gồm một ACE rồi gọi `setAcl`, bạn có thể vô tình xóa các quyền hiện có hoặc khóa chính mình khỏi tài nguyên.

### 11.1. Quy trình có kiểm soát (không tự chạy trên tệp thật)

1. Kiểm tra `AclFileAttributeView` hỗ trợ và caller có quyền thay ACL.
2. Đọc **toàn bộ** danh sách hiện có qua `getAcl()`; ghi nhận thứ tự, principal, flags và quyền.
3. Xác định thay đổi đúng theo yêu cầu nghiệp vụ; tránh tự “sort” danh sách hoặc bỏ các ACE chưa hiểu.
4. Tra cứu đúng `UserPrincipal` hoặc `GroupPrincipal` qua provider, không gán principal giả từ `UserPrincipal` lambda của demo.
5. Xây danh sách mới với `AclEntry.newBuilder()`, giữ những entry cần bảo toàn và xác định thứ tự theo mô hình filesystem.
6. Kiểm thử trên tệp thử riêng, kiểm tra quyền trước/sau; chuẩn bị kế hoạch phục hồi nếu khóa quyền quản trị.
7. Gọi `setAcl` và đọc lại `getAcl` để kiểm tra kết quả, xử lý thay đổi ngoài dự kiến; lưu audit.
8. Nhớ rằng giữa đọc và ghi ACL, tiến trình khác có thể thay đổi ACL: đây là **time-of-check-to-time-of-use**. Nếu cần đồng bộ nghiêm ngặt, dùng cơ chế quản trị phù hợp, không giả định view có compare-and-swap.
9. Chuẩn bị cho trường hợp `setAcl` thất bại và trạng thái sau lỗi cần được xác minh lại; không hứa sẽ có rollback nguyên tử trên mọi provider.

### 11.2. Vì sao không nên chuyển ACL thành `Set`?

`AclFileAttributeView.getAcl()` trả **`List<AclEntry>`**, tức có thứ tự. Chuyển thành `HashSet` rồi ghi ngược làm mất thứ tự, có thể thay đổi cách diễn giải. Không “chuẩn hóa” thứ tự tùy ý theo tên user hoặc enum type trừ khi thực sự hiểu quy tắc của filesystem.

### 11.3. ACL không phải công cụ thay thế nguyên tắc phân quyền

Đừng cấp quyền `WRITE_ACL` hoặc `WRITE_OWNER` khi yêu cầu chỉ là đọc nội dung; đó là quyền quản trị rất mạnh. Tuân theo nguyên tắc **least privilege**, tách quyền đọc dữ liệu khỏi quyền sửa bảo mật, và kiểm tra các ACE được kế thừa có phù hợp không.

## 12. Tính quyền hiệu lực: nhiều yếu tố hơn một ACE

Khi `getAcl()` cho biết một ACE `ALLOW user → READ_DATA`, ta **chưa** biết người dùng có thể đọc dữ liệu trong mọi trường hợp:

- Có thể tồn tại ACE khác với kiểu `DENY` hoặc thứ tự/di sản ACL ảnh hưởng.
- User có thể thuộc nhiều nhóm với các entry khác nhau.
- File có thể nằm dưới các thư mục cha không cho traverse.
- Hệ thống còn áp dụng share permissions, sandbox, mount flags, lock/policy, filesystem restrictions.
- Người đang chạy có thể là một principal khác với principal được hiển thị.
- ACL có thể thay đổi ngay sau khi bạn đọc.

`Files.isReadable(path)`/`isWritable(path)` là **kiểm tra tại thời điểm gọi**, không phải quyết định bảo mật bất biến. Khi thực sự mở/đọc/ghi, phải xử lý lỗi của thao tác đó; đừng sử dụng kiểm tra metadata làm bước phê duyệt quyền duy nhất.

### 12.1. Owner, ACL và POSIX trong một hệ thống hỗn hợp

Đối với ứng dụng Java chạy trên Linux lẫn Windows:

- Kiểm tra các view có sẵn (`basic`, `posix`, `acl`, `owner` tùy provider).
- Không tự chuyển ACL Windows 1:1 thành `rwx` POSIX.
- Nếu chỉ muốn cho user hiện tại tạo/đọc file theo chính sách ứng dụng, đôi khi **đặt permission khi tạo file** thích hợp hơn là sửa ACL hậu kỳ.
- Với yêu cầu kiểm toán quyền có tính pháp lý/vận hành, dùng API/công cụ quản trị chuyên biệt của platform; Java view không trình bày toàn bộ lớp chính sách bảo mật của hệ thống.

## 13. Symbolic link, chủ sở hữu và race condition

### 13.1. Đọc attribute của link hay đích?

Các API NIO.2 thường theo symbolic link theo mặc định. Overload nhận `LinkOption.NOFOLLOW_LINKS` giúp bày tỏ ý định thao tác trên link thay vì đích, **khi provider hỗ trợ**:

```java
AclFileAttributeView aclView = Files.getFileAttributeView(
        path, AclFileAttributeView.class,
        LinkOption.NOFOLLOW_LINKS);
```

Đây là đoạn minh họa. Nó không đảm bảo mọi provider cung cấp ACL của symlink hay cho phép sửa nó.

### 13.2. Thay đổi giữa lúc kiểm tra và sử dụng

Giả sử ứng dụng:

1. Kiểm tra `Files.getOwner(path)`.
2. Đọc `view.getAcl()`.
3. Sau đó mới mở file bằng `Files.newOutputStream(path)`.

Nếu path/symlink bị tiến trình khác thay đổi giữa các bước, file được mở có thể khác file đã kiểm tra. Đây là vấn đề **TOCTOU**. Không xem `getOwner`, `getAcl` hoặc `Path.normalize()` là khóa tài nguyên.

Khi thiết kế thao tác cần an toàn cao với path không tin cậy, cân nhắc mô hình mở bằng handle/directory stream và các API an toàn hơn nếu provider hỗ trợ; luôn giới hạn quyền trên filesystem, phân quyền tiến trình và quản lý quyền ghi vào thư mục cha.

## 14. Xử lý lỗi thực tế

| Tình huống | Hành vi/ngoại lệ có thể gặp | Cách phản ứng |
| --- | --- | --- |
| File không tồn tại | `NoSuchFileException` | Kiểm tra nguồn/đường dẫn, không giả định metadata có |
| Không có quyền đọc thuộc tính | `AccessDeniedException`, `IOException` | Báo quyền; không “fallback” sang cấp quyền rộng |
| ACL view không có | `null` từ `getFileAttributeView` | Thông báo không hỗ trợ, không gọi view |
| View được tạo nhưng method không hỗ trợ | `UnsupportedOperationException` | Phân biệt với dữ liệu rỗng |
| Principal không tìm thấy | `UserPrincipalNotFoundException` | Kiểm tra tên/miền tài khoản |
| Principal không phù hợp provider | Lỗi provider / ngoại lệ từ thao tác set | Không dùng principal tự tạo để set owner/ACL |
| Đổi owner/ACL bị từ chối | `IOException` hoặc `SecurityException` | Báo lỗi; giữ audit, không retry mù |
| Symlink/target bị thay đổi | Metadata không còn đúng tại lúc dùng | Áp dụng chính sách tài nguyên/TOCTOU |
| `getAcl()` chứa principal lạ | Entry hợp lệ ở mô hình provider nhưng người đọc không hiểu | Giữ nguyên; không xóa hoặc sắp xếp lại tùy tiện |
| ACL ghi thành công nhưng truy cập vẫn bị từ chối | Có thể do các lớp quyền khác | Kiểm tra toàn bộ chính sách thay vì cấp thêm `WRITE_ACL` |

Không nuốt mọi ngoại lệ bằng `catch (Exception ignored)`. Nếu chức năng chỉ để báo cáo quyền, hãy cho thấy **view không hỗ trợ**, **không có quyền** và **không thấy file** là ba trạng thái khác nhau.

## 15. Sai lầm thường gặp

1. Nhầm **owner** với “tài khoản có toàn quyền”.
2. Nhầm `UserPrincipal.getName()` với danh tính có thể so sánh toàn cầu.
3. Nghĩ rằng mọi hệ thống tệp hỗ trợ `AclFileAttributeView`.
4. Gọi `view.getAcl()` ngay khi `view == null`.
5. Cho rằng `getAcl()` trả đủ thông tin để tính chính xác quyền hiệu lực của một user bất kỳ.
6. Nhầm `PosixFilePermissions` (`rwx`) với ACL phong phú kiểu Windows.
7. Không phân biệt `FileOwnerAttributeView` và `AclFileAttributeView`.
8. Dùng principal giả bằng lambda của ví dụ để `Files.setOwner` trên máy thật.
9. Dùng `setAcl` với danh sách chỉ chứa một ACE mà không bảo toàn ACL cũ.
10. Chuyển `List<AclEntry>` sang `Set`, làm mất thứ tự.
11. Cấp `WRITE_ACL` hoặc `WRITE_OWNER` khi chỉ muốn cho phép đọc dữ liệu.
12. Bỏ qua các flags kế thừa và đánh giá ACL của thư mục cha.
13. Cho rằng `NOFOLLOW_LINKS` có semantics giống nhau trên mọi provider.
14. Đọc ACL rồi mở file, tin rằng pathname không thể đổi.
15. Ghi cứng tên user như `"Administrator"`/`"root"` và áp dụng cho mọi nền tảng.
16. Gọi `Files.isReadable` rồi tin thao tác đọc tiếp theo chắc chắn thành công.
17. Không xử lý `IOException` khi đổi owner/ACL, không có kế hoạch khôi phục quyền.
18. Log ACL/tên tài khoản ra kênh công khai mà không kiểm soát dữ liệu nhạy cảm.

## 16. Bài tập thực hành và tự kiểm tra

1. **Mô hình:** `AclEntryModelDemo` thêm `WRITE_DATA` vào builder, dự đoán sự thay đổi của kết quả kiểm tra membership.
2. **Quan sát:** chạy `OwnerAclInspect` với file do bạn sở hữu, ghi nhận owner và ACL view có hỗ trợ không. Tại sao bạn không thể dự đoán một danh sách ACL chung cho mọi máy?
3. **View:** kiểm tra `Files.getFileAttributeView(path, FileOwnerAttributeView.class)` khác gì với `AclFileAttributeView.class`. Trên filesystem chỉ hỗ trợ owner, chương trình nên làm gì?
4. **Tra cứu:** viết mã dùng `UserPrincipalLookupService` cho một tài khoản tồn tại thật trên filesystem. Bắt riêng `UserPrincipalNotFoundException`.
5. **Thứ tự ACL:** tại sao việc sort `getAcl()` theo tên principal rồi gọi `setAcl()` có thể làm thay đổi semantics?
6. **Least privilege:** nếu yêu cầu là user chỉ được xem nội dung và thông tin ACL, vì sao không cấp luôn `WRITE_ACL`/`WRITE_OWNER`?
7. **Symlink:** nếu `link` trỏ đến `target`, so sánh ý nghĩa của `Files.getOwner(link)` và `Files.getOwner(link, NOFOLLOW_LINKS)` trên hệ thống hỗ trợ.
8. **POSIX:** thử đọc `PosixFileAttributes.permissions()` trên môi trường hỗ trợ, đối chiếu với ACL view. Vì sao không thể khẳng định hai mô hình tương đương?
9. **Lỗi:** thiết kế đầu ra rõ ràng cho file thiếu, ACL view không hỗ trợ và bị từ chối quyền đọc thuộc tính.
10. **Quy trình sửa ACL:** lập checklist sao lưu, bảo toàn entry cũ, kiểm thử ở file thử, tra cứu principal, ghi audit và xác minh readback trước khi cho phép `setAcl`.
11. **Race:** mô tả vì sao kiểm tra owner/ACL xong vẫn không đảm bảo file mở sau đó là cùng đối tượng.
12. **Đúng phạm vi:** giải thích tại sao việc đọc ACL không tự chứng minh một tiến trình có thể xóa file: quyền xóa có thể liên quan permission trên directory cha và các policy khác.

## 17. Tổng kết

- `Files.getOwner()` trả **`UserPrincipal`** mô tả owner của đường dẫn theo provider; `Files.setOwner()` thay đổi metadata bảo mật và đòi quyền phù hợp.
- `UserPrincipalLookupService` dùng để tìm danh tính hợp lệ; `GroupPrincipal` mô tả nhóm. Tên hiển thị không phải định danh phổ quát cho mọi hệ thống.
- `AclFileAttributeView` cho phép đọc danh sách **`List<AclEntry>` có thứ tự** và, khi được phép, đặt lại ACL. View có thể **không được hỗ trợ**; kiểm tra `null`, xử lý ngoại lệ.
- Mỗi `AclEntry` chứa loại, principal, tập quyền và flags; hỗ trợ/diễn giải ACE phụ thuộc provider và hệ điều hành.
- ACL, POSIX `rwx`, thuộc tính DOS và **quyền hiệu lực** là các lớp khái niệm liên quan nhưng không đồng nhất.
- Đọc owner/ACL có thể hữu ích cho báo cáo, nhưng không phải bảo đảm về file identity hoặc khả năng truy cập ở thời điểm sau đó.
- Khi cần thay ACL/owner, dùng principal được tra cứu qua provider, bảo toàn quy tắc cần thiết, kiểm thử trên tài nguyên riêng, xác minh sau khi ghi và có kế hoạch phục hồi.

**Nguyên tắc cuối cùng:** quản lý owner/ACL là quản lý **quyền truy cập thực tế** của tài nguyên. Hãy kiểm tra tính hỗ trợ và hiểu đầy đủ chính sách quyền của filesystem trước khi thay đổi, thay vì suy ra quyền từ một tên user hay một entry ACL riêng lẻ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-attributes-and-permissions-008">UserDefinedFileAttributeView và hỗ trợ theo file system</a>

<details>
<summary>Xem chi tiết</summary>

# UserDefinedFileAttributeView và hỗ trợ theo file system trong Java

`java.nio.file.attribute.UserDefinedFileAttributeView` cho phép chương trình Java thao tác với **thuộc tính mở rộng do ứng dụng tự đặt tên** (*user-defined file attributes*) của một file hoặc entry trong hệ thống tệp. Mỗi thuộc tính gồm **tên** và **giá trị byte**; dữ liệu nằm trong hệ thống metadata mà file system/provider cung cấp, chứ không được tự động chèn vào nội dung chính của file.

**Ý chính:** đây là một tính năng **không bảo đảm tồn tại trên mọi file system**. API có mặt trong JDK không có nghĩa mọi ổ đĩa, quyền truy cập, network share hoặc filesystem ảo đều lưu được user-defined attributes. Code phải hỏi khả năng hỗ trợ, kiểm tra view và **xử lý lỗi từ thao tác thật**.

Bài thuộc nhóm **Thuộc tính và quyền**: học cách truy cập metadata tùy biến trong Java 17+, hiểu các giới hạn về byte/charset/dung lượng và chọn cách lưu trữ thay thế khi cần tính di động.

## 1. Nội dung file và thuộc tính tùy biến là hai thứ khác nhau

Ví dụ một file báo cáo:

```text
data/report.txt
  |
  +-- Nội dung chính: "Báo cáo tháng 10"
  |
  +-- Metadata tiêu chuẩn:
  |     size, lastModifiedTime, owner, permissions...
  |
  +-- Metadata tùy biến (nếu được hỗ trợ):
        "app.category" -> byte UTF-8 của "finance"
        "app.status"   -> byte UTF-8 của "approved"
```

Khi đọc `Files.readString(path, StandardCharsets.UTF_8)`, kết quả là **nội dung chính**. Hai thuộc tính `app.category` và `app.status` không tự xuất hiện trong chuỗi đó. Ngược lại, khi ghi bằng `UserDefinedFileAttributeView.write`, chương trình không tự thay đổi các dòng trong file.

Một thuộc tính có hai phần:

- **Tên `String`**, ví dụ `"lesson.note"`. Các ký tự hợp lệ, namespace và độ dài tên tùy thuộc vào provider/file system.
- **Giá trị là một dãy byte**, truyền vào và nhận ra bằng `ByteBuffer`. Java không mặc định giải mã thành UTF-8, JSON hay đối tượng Java.

Tính năng này phù hợp hơn với **metadata nhỏ** (nhãn phân loại, thông tin nguồn, trạng thái phụ trợ) thay vì nội dung file lớn hoặc kho dữ liệu nghiệp vụ.

## 2. Kiểm tra có hỗ trợ hay không

Lấy view của một `Path`:

```java
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserDefinedFileAttributeView;

// Minh họa: path là một Path đã xác định.
UserDefinedFileAttributeView view =
        Files.getFileAttributeView(
                path, UserDefinedFileAttributeView.class);
```

Nếu loại view không có sẵn cho provider/path, `view` có thể là **`null`**. Không gọi `view.list()` hoặc `view.write(...)` trước khi xử lý khả năng này.

Muốn hỏi khả năng của **file store** chứa path:

```java
import java.nio.file.FileStore;

FileStore store = Files.getFileStore(path);

boolean supportedByType = store.supportsFileAttributeView(
        UserDefinedFileAttributeView.class);
boolean supportedByName =
        store.supportsFileAttributeView("user");
```

Đoạn trên minh họa khi `path` có sẵn và phương thức xử lý/khai báo `IOException`. `Files.getFileStore` có thể ném `IOException` khi không lấy được thông tin hệ thống tệp.

Tên của `UserDefinedFileAttributeView` là **`"user"`**. Không nhầm từ `user` ở đây với chủ sở hữu Unix/POSIX; view này chỉ thao tác metadata tùy biến.

**Lưu ý về hai kiểm tra:**

1. `FileStore.supportsFileAttributeView(...)` cho biết khả năng chung do file store báo cáo.
2. `Files.getFileAttributeView(...)` cho biết provider có thể cung cấp view đối với một path.
3. Kể cả hai điều kiện đều thuận lợi, `read/write/list/delete` **vẫn có thể thất bại** do quyền, chính sách mount, tên không hợp lệ, giới hạn kích thước, file bị xóa giữa lúc chạy hoặc lỗi I/O.

Vì vậy **support=true không phải cam kết ghi thành công**. Chỉ kết quả của thao tác thật mới cho biết ứng dụng đã lưu/đọc được giá trị hay chưa.

## 3. API cốt lõi của `UserDefinedFileAttributeView`

| Phương thức | Công dụng | Dữ liệu hoặc kết quả |
| --- | --- | --- |
| `name()` | Tên view | `"user"` |
| `list()` | Liệt kê những tên thuộc tính được lưu | `List<String>`; có thể ném `IOException` |
| `size(String name)` | Lấy độ dài giá trị thuộc tính | **Số byte**, kiểu `int` |
| `write(String name, ByteBuffer src)` | Ghi giá trị byte | Số byte đã ghi; có thể ném `IOException` |
| `read(String name, ByteBuffer dst)` | Đọc byte thuộc tính vào buffer | Số byte đã đọc; có thể ném `IOException` |
| `delete(String name)` | Xóa một thuộc tính được chỉ định | Không trả giá trị |

**Phân biệt:** `view.delete("lesson.note")` chỉ xóa **metadata có tên đó**; `Files.delete(path)` mới là API **xóa entry file**. Hai tác vụ không đồng nghĩa.

### Chọn tên và định dạng giá trị

Một quy ước đơn giản:

```text
Tên: "app.version"
Giá trị: UTF-8 bytes của "1"

Tên: "app.document-status"
Giá trị: UTF-8 bytes của "approved"
```

Bên ghi và bên đọc phải thống nhất **tên thuộc tính, charset, định dạng và phiên bản schema**. Giá trị metadata được truyền qua byte chứ không phải Java `String` mang charset tự động.

Với văn bản:

```java
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

byte[] bytes = "Xin chào".getBytes(StandardCharsets.UTF_8);
int written = view.write("lesson.note", ByteBuffer.wrap(bytes));
```

Đây là ví dụ minh họa, không độc lập: cần `view` khả dụng và phương thức xử lý `IOException`. `written` là **số byte**, không phải `String.length()`. Ví dụ `"Café"` dài bốn `char` UTF-16 nhưng mã hóa thành **5 byte UTF-8**.

## 4. Đọc lại thuộc tính đúng với `ByteBuffer`

Các bước thông dụng:

```java
int size = view.size("lesson.note");
ByteBuffer buffer = ByteBuffer.allocate(size);
int bytesRead = view.read("lesson.note", buffer);
buffer.flip();

String value = StandardCharsets.UTF_8.decode(buffer).toString();
```

Đoạn minh họa giả định `view`, `ByteBuffer` và `StandardCharsets` được import, đồng thời xử lý `IOException`. Diễn giải:

1. `size` cho biết kích thước **byte** của giá trị ở thời điểm hỏi.
2. `allocate(size)` chuẩn bị vùng nhớ để nhận dữ liệu.
3. `read` ghi dữ liệu vào buffer, làm thay đổi `position`.
4. `flip()` chuyển buffer từ giai đoạn ghi sang giai đoạn đọc: `limit` bằng vị trí đã ghi, `position` về đầu.
5. `UTF_8.decode(buffer)` giải mã vùng byte sẵn sàng đọc thành ký tự Java.

Khi muốn giữ nguyên byte thay vì giải mã:

```java
buffer.flip();
byte[] actual = new byte[buffer.remaining()];
buffer.get(actual);
```

**Không dùng toàn bộ capacity thay cho vùng byte hợp lệ.** Bộ đệm có các thuộc tính `capacity`, `position` và `limit`; chỉ phần được ghi vào buffer mới là dữ liệu mới đọc.

### Cạnh tranh giữa `size` và `read`

Hai lời gọi `view.size(name)` và `view.read(name, buffer)` **không phải snapshot nguyên tử**. Một tiến trình khác có thể đổi/xóa thuộc tính giữa hai thao tác. Khi đó buffer có thể không đủ, nội dung khác hoặc thao tác ném lỗi.

Cũng không cấp phát mảng khổng lồ từ metadata không tin cậy mà không có giới hạn. Thuộc tính mở rộng thường nhỏ, nhưng ứng dụng vẫn nên áp quota rõ ràng và xử lý lỗi cấp phát thích hợp.

## 5. Ví dụ hoàn chỉnh Java 17+: ghi, đọc và xóa metadata

Ví dụ tự tạo một **file tạm riêng**, lưu nội dung `"File body"`, sau đó thử tạo metadata tùy biến. Chương trình chỉ thử nếu file store báo hỗ trợ và view tồn tại. Nếu môi trường hoặc quyền không cho phép, ví dụ báo `SKIPPED`; trong ứng dụng thật, metadata bắt buộc **không được âm thầm bỏ qua**.

Lưu nguyên chương trình sau vào file **`UserDefinedAttributesDemo.java`**:

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.Arrays;

public class UserDefinedAttributesDemo {

    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("user-attr-demo-", ".txt");
        String originalBody = "File body";
        String attributeName = "lesson.note";
        String attributeText = "Xin chào";

        try {
            Files.writeString(file, originalBody, StandardCharsets.UTF_8);
            String outcome = "SKIPPED";

            FileStore store = Files.getFileStore(file);
            UserDefinedFileAttributeView view =
                    Files.getFileAttributeView(
                            file, UserDefinedFileAttributeView.class);

            if (store.supportsFileAttributeView(
                    UserDefinedFileAttributeView.class)
                    && view != null) {
                try {
                    byte[] expected =
                            attributeText.getBytes(StandardCharsets.UTF_8);

                    int written = view.write(
                            attributeName, ByteBuffer.wrap(expected));

                    ByteBuffer buffer =
                            ByteBuffer.allocate(view.size(attributeName));
                    int read = view.read(attributeName, buffer);
                    buffer.flip();

                    byte[] actual = new byte[buffer.remaining()];
                    buffer.get(actual);

                    boolean listed = view.list().contains(attributeName);
                    view.delete(attributeName);
                    boolean deleted =
                            !view.list().contains(attributeName);

                    outcome = Arrays.equals(expected, actual)
                            && read == expected.length
                            && written == expected.length
                            && listed && deleted
                            ? "PASS" : "FAIL";
                } catch (IOException | UnsupportedOperationException
                         | IllegalArgumentException | SecurityException ex) {
                    // Demo: một số môi trường có view nhưng cấm thao tác.
                    outcome = "SKIPPED";
                }
            }

            System.out.println("Attribute round-trip: " + outcome);
            System.out.println("File content unchanged: " +
                    originalBody.equals(
                            Files.readString(file, StandardCharsets.UTF_8)));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Chạy trên JDK 17+:

```bash
javac -encoding UTF-8 UserDefinedAttributesDemo.java
java UserDefinedAttributesDemo
```

**Stdout nếu thuộc tính tùy biến được hỗ trợ và thao tác thành công:**

```text
Attribute round-trip: PASS
File content unchanged: true
```

**Stdout nếu không có view hoặc thao tác không khả dụng:**

```text
Attribute round-trip: SKIPPED
File content unchanged: true
```

Nếu implementation hỗ trợ nhưng dữ liệu ghi/đọc không nhất quán, chương trình còn có thể in `Attribute round-trip: FAIL`, khi đó cần điều tra thay vì coi là thành công. Những lỗi xảy ra ngoài khối thử metadata (ví dụ không tạo được file tạm, không truy cập được file store, không xóa được file tạm) vẫn có thể ném `IOException`.

**Phân tích chương trình:**

- `Files.createTempFile` tạo file với tên riêng, không thao tác trên file dữ liệu của người dùng.
- `Files.getFileStore` và `Files.getFileAttributeView` kiểm tra tính năng ở môi trường thực.
- `String.getBytes(UTF_8)` tạo dãy byte; `view.write` lưu dữ liệu vào thuộc tính có tên `lesson.note`.
- `view.size` và `view.read` khôi phục byte; `ByteBuffer.flip()` làm đúng vùng dữ liệu cần so sánh.
- `view.list` xác nhận tên có mặt; `view.delete` xóa thuộc tính. File tạm **vẫn còn** cho tới cuối `finally`.
- `Files.readString` xác nhận nội dung chính của file không bị thay đổi.
- `Files.deleteIfExists` chỉ dùng để **dọn chính file tạm mà demo đã tạo**, không phải bước xóa attribute.

Dòng `SKIPPED` giúp bài học chạy trên nhiều nền tảng. Ở production, nếu custom metadata là điều kiện bắt buộc, nên thông báo thiếu hỗ trợ hoặc lưu vào nơi khác có hợp đồng độ bền rõ ràng.

## 6. Hỗ trợ là theo file system/provider, không chỉ tên hệ điều hành

Các trường hợp thường gặp:

| Môi trường | Lưu ý thực tế |
| --- | --- |
| Linux với filesystem có extended attributes | Có thể hỗ trợ nhóm user xattr, nhưng vẫn tùy mount options, quyền và provider |
| Windows trên filesystem/provider có metadata bổ sung | Có thể hỗ trợ bằng cơ chế riêng; không khẳng định mọi ổ đĩa đều giống nhau |
| Network filesystem/NAS | Server, giao thức, cấu hình chia sẻ và quyền có thể giới hạn |
| Filesystem ảo hoặc ZIP filesystem | Có thể không có `UserDefinedFileAttributeView` |
| File từ USB, thiết bị ngoại vi | Định dạng của ổ và công cụ copy có thể không giữ metadata |
| Container, CI, sandbox hoặc ổ chỉ đọc | Quyền/tính năng có thể bị hạn chế dù JDK cung cấp interface |

**Tránh hai kết luận sai:** “Windows luôn không hỗ trợ” và “Linux luôn hỗ trợ”. Cần kiểm tra **file store thực tế tại đường dẫn** và xử lý chính thao tác.

### Các attribute view khác với `"user"`

- `BasicFileAttributeView`: kích thước, thời gian sửa/đọc, loại entry.
- `PosixFileAttributeView`: quyền/owner/group kiểu POSIX khi hỗ trợ.
- `DosFileAttributeView`: cờ hidden/read-only kiểu DOS khi hỗ trợ.
- `AclFileAttributeView`: access control lists khi provider hỗ trợ.
- `UserDefinedFileAttributeView`: tên và dữ liệu byte do ứng dụng tự quản lý.

Tên view `"user"` **không đồng nghĩa đã có quyền đọc/ghi**. Quyền thao tác attribute có thể phụ thuộc vào người dùng hệ điều hành, ACL, mount options hoặc provider.

## 7. Symbolic link và `NOFOLLOW_LINKS`

Các hàm truy cập metadata có thể có tùy chọn `LinkOption.NOFOLLOW_LINKS`. Khi `path` là symbolic link, cần xác định muốn hỏi thuộc tính **của link** hay **của file đích**:

```java
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.UserDefinedFileAttributeView;

// Đoạn minh họa; path đã tồn tại.
UserDefinedFileAttributeView linkView =
        Files.getFileAttributeView(
                path,
                UserDefinedFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS);
```

`NOFOLLOW_LINKS` yêu cầu không theo liên kết khi lấy view, nhưng provider **có thể không hỗ trợ user-defined attributes trên chính symbolic link**. `linkView` có thể `null` hoặc thao tác với nó thất bại.

Không coi `NOFOLLOW_LINKS` hay `Path.normalize()` là biện pháp đầy đủ chống path traversal, race hoặc symlink attack. Với input không tin cậy, cần kiểm soát root, đường dẫn, quyền và cách mở file theo chính sách an toàn.

## 8. Thuộc tính có được giữ khi copy/move/backup không?

**Không có bảo đảm di động tuyệt đối.** Một file có thể được copy sang filesystem khác mà nội dung còn nguyên, nhưng các user-defined attributes bị mất. Công cụ nén, email, cloud sync, network transfer hay phần mềm backup cũng có thể không đưa metadata ngoài nội dung vào kết quả.

`Files.copy` có tùy chọn:

```java
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

// Minh họa: source và target là Path đã xác định.
Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
```

`COPY_ATTRIBUTES` yêu cầu sao chép thuộc tính theo khả năng provider, **không cam kết giữ mọi xattr ở mọi đích**. Nếu metadata quan trọng, hãy kiểm tra lại sau copy hoặc lưu nó bằng phương pháp khác: sidecar JSON, database, metadata trong định dạng file khi chuẩn cho phép.

Tương tự, `Files.move` có các ràng buộc provider khi di chuyển giữa filesystem. Không suy luận rằng move/copy file cũng duy trì quyền, owner và mọi user attribute trong tất cả trường hợp.

## 9. Giới hạn dung lượng và bảo mật

Các lý do không nên dùng xattr như một kho lưu trữ tổng quát:

1. **Hạn mức kích thước:** một giá trị hoặc tổng giá trị metadata có thể bị giới hạn khá nhỏ theo filesystem.
2. **Tên hợp lệ:** có thể có giới hạn ký tự, độ dài, namespace.
3. **Quyền truy cập:** có thể đọc được nội dung nhưng không có quyền ghi thuộc tính.
4. **Độ bền và sao lưu:** metadata có thể không theo file qua các hệ thống hoặc công cụ.
5. **Đồng thời:** `size` rồi `read` không bảo đảm snapshot nguyên tử.
6. **Bảo mật:** thuộc tính không tự mã hóa, không phải kho mật khẩu/token bí mật.
7. **Quản trị dữ liệu:** không có mô hình query, transaction, index, replication đa nền tảng như database.

Nếu lưu chuỗi Unicode, chọn charset rõ ràng như `StandardCharsets.UTF_8`; không ép các byte tùy ý thành text mà không biết định dạng. Với dữ liệu không tin cậy, không deserialize Java object trực tiếp từ một xattr mà thiếu kiểm tra.

**Thay thế phù hợp:**

| Yêu cầu | Phương án |
| --- | --- |
| Metadata phụ nhỏ, trên môi trường đã kiểm chứng | `UserDefinedFileAttributeView` |
| Metadata phải đi cùng file qua hệ thống khác | Sidecar như `report.txt.meta.json`; quản lý rename/copy nhất quán |
| Metadata bắt buộc, có query/transaction | Database và khóa tham chiếu tới file |
| Metadata được chuẩn file hỗ trợ | Thao tác qua thư viện cho đúng định dạng |
| Nội dung lớn hoặc dữ liệu chính | Lưu vào nội dung file/blob store |
| Secret cần bảo vệ | Secrets manager/keystore và chính sách truy cập phù hợp |

## 10. Xử lý lỗi: đừng nhầm "không hỗ trợ" với "ghi xong"

| Kết quả / lỗi | Cách hiểu |
| --- | --- |
| `view == null` | Provider/path không cung cấp loại view |
| `store.supportsFileAttributeView(...) == false` | File store báo thiếu hỗ trợ |
| `view.write` ném `IOException` | Có lỗi I/O/quyền/dung lượng hoặc hạn chế cụ thể |
| `view.list` không chứa tên sau khi ghi | Chưa có trạng thái mong muốn hoặc có thay đổi đồng thời |
| `view.read` ném lỗi | Tên không có, permission hoặc trạng thái file đổi |
| `view.size` trả về số byte khác kỳ vọng | Giá trị/charset hoặc dữ liệu đã thay đổi |
| `Files.copy` xong thiếu attribute | Provider/công cụ không bảo toàn metadata tùy biến |
| Byte giải mã ra ký tự lỗi | Charset đọc khác charset ghi |
| File vẫn đọc được nhưng xattr ghi thất bại | Nội dung file và quyền metadata không nhất thiết giống nhau |

**Mã production:** khi metadata là bắt buộc, phải **fail rõ ràng hoặc fallback có kiểm soát**. Chỉ demo đa nền tảng nên in `SKIPPED`. Ghi log chi tiết vừa đủ để sửa lỗi, tránh đưa metadata nhạy cảm hoặc đường dẫn bí mật vào log ngoài ý muốn.

### Ví dụ kiểm tra yêu cầu tính năng

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserDefinedFileAttributeView;

static UserDefinedFileAttributeView requireUserView(Path path)
        throws IOException {

    UserDefinedFileAttributeView view =
            Files.getFileAttributeView(
                    path, UserDefinedFileAttributeView.class);

    if (view == null) {
        throw new IOException("File system thiếu user attribute view");
    }
    return view;
}
```

Đây là một **phương thức minh họa** đặt trong class, **không** hứa rằng `view.write` thành công: ứng dụng vẫn phải bắt lỗi khi thao tác thật. Nếu khả năng tồn tại metadata là bắt buộc, có thể bổ sung kiểm tra `FileStore` và round-trip với một tên tạm do ứng dụng sở hữu, nhưng không tự xóa thuộc tính của người khác.

## 11. Câu hỏi tự kiểm tra

1. **`UserDefinedFileAttributeView` lưu gì?** Metadata tùy biến theo **tên → dãy byte**, không phải nội dung chính của file.
2. **Lấy view bằng API nào?** `Files.getFileAttributeView(path, UserDefinedFileAttributeView.class)`.
3. **Tên view là gì?** `"user"`.
4. **`FileStore.supportsFileAttributeView(...)` trả `true` thì `write` luôn thành công?** Không, quyền/hạn mức/provider vẫn có thể cản trở.
5. **`view.list()` trả gì?** Danh sách **tên** attribute, không tự giải mã giá trị.
6. **`view.size(name)` tính theo ký tự hay byte?** Byte.
7. **`ByteBuffer.flip()` giúp gì sau khi `read`?** Chuẩn bị phần đã ghi vào buffer để đọc/giải mã.
8. **`view.delete(name)` có xóa file không?** Không, chỉ xóa attribute được đặt tên.
9. **Nếu copy file qua USB hoặc gửi lên cloud, xattr có chắc giữ nguyên?** Không.
10. **Có thể lưu password an toàn trong xattr không?** Không nên coi xattr là cơ chế bảo mật/mã hóa.
11. **Muốn đọc thuộc tính của chính symbolic link thì cân nhắc gì?** `LinkOption.NOFOLLOW_LINKS` và giới hạn hỗ trợ của provider.
12. **Dữ liệu thuộc tính là UTF-8 text hay binary?** Về API là byte; ứng dụng tự quyết định charset hoặc định dạng.
13. **File system không hỗ trợ thì ứng dụng có metadata bắt buộc nên làm gì?** Báo lỗi hoặc chuyển sang phương án lưu trữ thay thế đã thiết kế.

**Ghi nhớ:** `UserDefinedFileAttributeView` là API cho **metadata tùy biến dạng byte**, không phải cơ chế lưu nội dung, không phải kho bí mật và **không được bảo đảm trên mọi file system**. Luôn kiểm tra hỗ trợ, xử lý lỗi I/O thực tế, thống nhất charset/format và có phương án di động nếu metadata quan trọng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-attributes-and-permissions-009">Thiết lập thuộc tính khi tạo tệp</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết lập thuộc tính khi tạo tệp trong Java NIO.2

## 1. Vấn đề: không chỉ tạo được tệp, mà cần tạo với thuộc tính đúng

Khi ứng dụng ghi cấu hình, khóa riêng, báo cáo, tệp tạm hoặc dữ liệu của người dùng, câu hỏi không chỉ là “tạo tệp thành công chưa?” mà còn là **tệp mới có quyền truy cập, chủ sở hữu và những thuộc tính cần thiết hay chưa?**

Java NIO.2 hỗ trợ truyền các **thuộc tính ban đầu** (*initial file attributes*) vào một số phương thức tạo tệp và thư mục. Ví dụ, trên hệ thống tệp hỗ trợ quyền POSIX, ta có thể yêu cầu tạo một tệp chỉ cho **chủ sở hữu** đọc và ghi:

~~~java
Set<PosixFilePermission> permissions =
        PosixFilePermissions.fromString("rw-------");

FileAttribute<Set<PosixFilePermission>> attrs =
        PosixFilePermissions.asFileAttribute(permissions);

Files.createFile(path, attrs);
~~~

Đây là **đoạn mã minh họa**: biến `path` là một `Path` chưa tồn tại, và cần các import từ `java.nio.file`, `java.nio.file.attribute`, `java.util.Set`. Lệnh có thể ném `IOException` hoặc `UnsupportedOperationException` tùy hệ thống tệp. Chương trình độc lập và cách kiểm tra hỗ trợ nằm ở các phần bên dưới.

**Ý tưởng cốt lõi:** yêu cầu thuộc tính **ngay trong thao tác tạo** thường tránh được “khoảng thời gian tệp tồn tại với quyền mặc định rồi mới được sửa”, nếu provider hỗ trợ thuộc tính đó tại thời điểm tạo. Đây là vấn đề đặc biệt quan trọng với thông tin nhạy cảm. Tuy nhiên, không phải mọi provider chấp nhận mọi thuộc tính, và hệ điều hành/file system vẫn có chính sách riêng về quyền hiệu lực.

Bài này tập trung vào **thiết lập thuộc tính khi tạo tệp** với `Files` của Java 17. Bạn sẽ học:

1. Phân biệt **thuộc tính ban đầu**, **tùy chọn mở tệp** và **thuộc tính thay đổi sau khi tạo**.
2. Dùng `FileAttribute<?>` với `Files.createFile`, `Files.createDirectory`, `Files.createTempFile` và `Files.newByteChannel`.
3. Tạo yêu cầu quyền **POSIX** bằng `PosixFilePermissions.fromString` và `asFileAttribute`.
4. Hiểu phạm vi hỗ trợ của provider, Windows/POSIX và giới hạn của việc tự đặt `chmod` sau khi tạo.
5. Kiểm tra kết quả, xử lý lỗi và giữ an toàn khi tạo tệp không được phép ghi đè.
6. Áp dụng Java 17 qua các ví dụ có thể biên dịch, chạy và đối chiếu đầu ra.

## 2. File attributes, permissions và open options là những khái niệm khác nhau

Hệ thống tệp có thể lưu những loại thông tin về một entry như:

- **Tên, loại và kích thước:** tệp thường, thư mục, symbolic link; số byte; thông tin cơ bản.
- **Dấu thời gian:** thời điểm tạo, sửa, truy cập (khả năng và độ chính xác tùy provider).
- **Quyền truy cập POSIX:** các bit đọc/ghi/thực thi cho chủ sở hữu, nhóm và đối tượng khác.
- **Chủ sở hữu/nhóm:** thông tin người dùng và nhóm gắn với entry, nếu hệ thống tệp hỗ trợ.
- **ACL:** danh sách kiểm soát truy cập chi tiết trên một số filesystem.
- **Thuộc tính DOS:** ví dụ hidden, read-only, system, archive khi provider hỗ trợ.
- **Thuộc tính do provider định nghĩa:** không có bảo đảm sử dụng được trên mọi hệ thống.

Ba nhóm tham số rất dễ bị nhầm:

| Loại | Ví dụ | Trả lời câu hỏi |
| --- | --- | --- |
| `FileAttribute<?>` ban đầu | `PosixFilePermissions.asFileAttribute(...)` | *Tạo mới với thuộc tính nào?* |
| `OpenOption` | `CREATE_NEW`, `WRITE`, `APPEND` | *Mở/tạo tệp bằng chế độ nào?* |
| Phương thức đặt thuộc tính sau tạo | `Files.setPosixFilePermissions`, `Files.setAttribute`, `Files.setOwner` | *Thay đổi thuộc tính entry đã tồn tại ra sao?* |

Không dùng `StandardOpenOption.CREATE_NEW` để thể hiện “chỉ cho owner đọc”. Nó quyết định điều kiện tạo/mở, **không phải một bộ quyền**. Tương tự, `PosixFilePermissions.asFileAttribute` không tự quyết định ghi đè hay append.

### 2.1. Thuộc tính ban đầu không có nghĩa mọi metadata đều đặt được

Mỗi `FileAttribute<T>` có **tên** thuộc tính và **giá trị**. Java có nhiều `FileAttributeView` để đọc/ghi metadata, nhưng **không có bảo đảm mọi thuộc tính đọc/ghi được đều có thể truyền vào lúc tạo**.

Ví dụ, một filesystem có thể cho phép sửa thời gian truy cập **sau** khi tệp đã có, nhưng không hỗ trợ thiết lập thời gian đó như initial attribute trong `Files.createFile`. Với thuộc tính không hỗ trợ khi tạo, provider có thể ném `UnsupportedOperationException`.

Hãy đọc hợp đồng phương thức tạo và thử trên provider thật thay vì phỏng đoán từ việc một attribute view tồn tại.

## 3. Những API nhận FileAttribute<?> lúc tạo

Các phương thức sau có tham số cuối `FileAttribute<?>... attrs`:

| Phương thức | Tác dụng | Điểm lưu ý về attrs |
| --- | --- | --- |
| `Files.createFile(path, attrs)` | Tạo tệp mới; không ghi đè tệp đã tồn tại | Áp dụng các thuộc tính được hỗ trợ ngay lúc tạo |
| `Files.createDirectory(path, attrs)` | Tạo một thư mục mới; thư mục cha phải tồn tại | Có thể yêu cầu quyền ban đầu của thư mục |
| `Files.createDirectories(path, attrs)` | Tạo các thư mục cha cần thiết | Thuộc tính áp dụng cho những thư mục **được tạo mới**; thư mục hiện hữu không được đặt lại |
| `Files.createTempFile(dir, prefix, suffix, attrs)` | Tạo tệp tạm mới trong thư mục chỉ định | Có thể yêu cầu thuộc tính ban đầu |
| `Files.createTempDirectory(dir, prefix, attrs)` | Tạo thư mục tạm mới | Phù hợp làm thư mục riêng cho dữ liệu nhạy cảm |
| `Files.newByteChannel(path, options, attrs)` | Mở kênh byte, có thể tạo tệp | Thuộc tính ban đầu chỉ có ý nghĩa **khi tệp được tạo mới** |
| `Files.createSymbolicLink(link, target, attrs)` | Tạo liên kết tượng trưng nếu được hỗ trợ | Nhiều provider không hỗ trợ initial attrs cho symbolic link; không suy ra quyền đích |

`Files.createFile` và `Files.createDirectory` cung cấp API trực tiếp cho mục tiêu “tạo entry mới với thuộc tính”. Nếu cần mở và ghi ngay trong một lần thao tác, `Files.newByteChannel` là lựa chọn đáng xem xét khi bạn cần cung cấp `FileAttribute<?>...` cùng các `OpenOption`.

Không phải mọi phương thức ghi của `Files` đều có tham số thuộc tính ban đầu. Đặc biệt, overload `Files.newOutputStream(path, options...)` nhận **OpenOption**, không có varargs `FileAttribute<?>`. Nếu cần áp dụng initial attributes mà chỉ dùng `newOutputStream`, không thể mặc định truyền thêm `FileAttribute` vào overload không tồn tại.

## 4. FileAttribute<?> là gì?

`java.nio.file.attribute.FileAttribute<T>` là interface mô tả một thuộc tính:

~~~java
public interface FileAttribute<T> {
    String name();
    T value();
}
~~~

Đây là **chữ ký API minh họa** (không cần tạo lại interface trong ứng dụng). Tên thường có dạng `view-name:attribute-name`, ví dụ `"posix:permissions"`. Giá trị thuộc tính tương ứng là một kiểu Java được hợp đồng đó định nghĩa.

Với quyền POSIX, Java cung cấp helper tạo `FileAttribute<Set<PosixFilePermission>>`:

~~~java
Set<PosixFilePermission> permissions =
        PosixFilePermissions.fromString("rw-------");
FileAttribute<Set<PosixFilePermission>> initial =
        PosixFilePermissions.asFileAttribute(permissions);
~~~

Đây là đoạn minh họa, với import:

~~~java
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
~~~

Phần lớn bài toán thông dụng không cần tự viết implementation của `FileAttribute`; dùng helper chính thức sẽ rõ nghĩa và giảm lỗi tên/kiểu attribute.

### 4.1. Tham số varargs có nghĩa gì?

Một chữ ký như:

~~~java
Files.createFile(Path path, FileAttribute<?>... attrs)
~~~

cho phép truyền **không có**, **một** hoặc **nhiều** thuộc tính ban đầu (nếu hợp lệ và provider hỗ trợ). Về cú pháp:

~~~java
Files.createFile(path);                 // Không yêu cầu attrs tường minh.
Files.createFile(path, permissionsAttr); // Một thuộc tính ban đầu.
~~~

Đây là đoạn minh họa với `permissionsAttr` và `path` đã có. Tên thuộc tính có thể bị trùng khi cung cấp nhiều lần; theo hợp đồng các phương thức `Files`, nếu một tên xuất hiện nhiều lần thì thường chỉ giá trị của **lần xuất hiện cuối** được sử dụng. Tuy nhiên, việc cung cấp trùng thuộc tính là lỗi thiết kế dễ nhầm; tốt hơn hãy chỉ truyền một giá trị cuối được xác định rõ ràng.

Đừng thêm thuộc tính tùy ý mà không kiểm tra sự hỗ trợ: phần còn lại có thể hoàn toàn hợp lệ nhưng **một thuộc tính không được hỗ trợ** khiến lời gọi tạo tệp thất bại.

## 5. Quyền POSIX: ba nhóm và ba hành động

Trên filesystem hỗ trợ quyền POSIX, mode thường được biểu diễn thành 9 ký tự:

~~~text
rwxrwxrwx
│││││││││
││││││└┴┴─ Other: read/write/execute
│││└┴┴──── Group: read/write/execute
└┴┴─────── Owner: read/write/execute
~~~

Các vị trí tương ứng quyền:

| Nhóm | Read | Write | Execute |
| --- | --- | --- | --- |
| Owner | `OWNER_READ` | `OWNER_WRITE` | `OWNER_EXECUTE` |
| Group | `GROUP_READ` | `GROUP_WRITE` | `GROUP_EXECUTE` |
| Others | `OTHERS_READ` | `OTHERS_WRITE` | `OTHERS_EXECUTE` |

Trong Java, từng quyền là một giá trị enum `PosixFilePermission`. `Set<PosixFilePermission>` lưu bộ quyền đã chọn, không có thứ tự quan trọng.

Ví dụ ký hiệu:

| Chuỗi mode | Ý nghĩa | Trường hợp |
| --- | --- | --- |
| `rw-------` | Owner đọc/ghi; group và others không có bit nào | File bí mật hoặc dữ liệu riêng |
| `r--------` | Chỉ owner đọc | Một file chỉ đọc sau tạo (cần xem khả năng ghi nội dung) |
| `rw-r-----` | Owner đọc/ghi, group đọc | Tệp chia sẻ trong nhóm được kiểm soát |
| `rw-r--r--` | Owner đọc/ghi, group/others đọc | Tệp công khai trên hệ thống phù hợp |
| `rwx------` | Owner đọc/ghi/traverse; nhóm khác không có | Thư mục riêng |
| `rwxr-x---` | Owner có đủ quyền; group đọc/traverse | Thư mục chia sẻ nhóm |

Quyền `x` trên **thư mục** liên quan khả năng **đi xuyên/traverse** đường dẫn; không hoàn toàn giống ý nghĩa chạy tệp thực thi. Một thư mục owner chỉ có `rw-------` mà không có `x` có thể khiến người dùng không truy cập được các entry bên trong theo mô hình POSIX. Vì vậy mode của tệp và thư mục không nên sao chép y hệt nhau chỉ vì muốn “riêng tư”.

### 5.1. fromString và toString

`PosixFilePermissions.fromString(String)` chuyển chuỗi 9 ký tự theo cú pháp POSIX thành `Set<PosixFilePermission>`. `toString(Set<PosixFilePermission>)` chuyển ngược thành chuỗi mode, hữu ích để in/kiểm tra một bộ quyền.

~~~java
Set<PosixFilePermission> p =
        PosixFilePermissions.fromString("rw-r-----");
System.out.println(PosixFilePermissions.toString(p));
~~~

Kết quả:

~~~text
rw-r-----
~~~

Đây là đoạn minh họa, cần đặt trong phương thức/lớp và import như trên. Nếu chuỗi mode không hợp lệ về độ dài hoặc ký tự, API ném `IllegalArgumentException`.

## 6. Tạo tệp mới với quyền ban đầu

### 6.1. Ví dụ Java 17 độc lập: tạo file riêng tư

Tệp **CreatePrivateFileDemo.java**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

public class CreatePrivateFileDemo {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("private-file-demo-");
        Path file = dir.resolve("private.txt");

        try {
            Set<PosixFilePermission> mode =
                    PosixFilePermissions.fromString("rw-------");
            FileAttribute<Set<PosixFilePermission>> initial =
                    PosixFilePermissions.asFileAttribute(mode);

            // Ví dụ này cần một provider hỗ trợ quyền POSIX.
            Files.createFile(file, initial);

            Files.writeString(file, "secret", StandardCharsets.UTF_8);

            Set<PosixFilePermission> actual =
                    Files.getPosixFilePermissions(file);
            System.out.println("Readable text: " +
                    Files.readString(file, StandardCharsets.UTF_8));
            System.out.println("Requested mode: " +
                    PosixFilePermissions.toString(mode));
            System.out.println("Actual mode: " +
                    PosixFilePermissions.toString(actual));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}
~~~

Lệnh chạy:

~~~bash
javac -encoding UTF-8 CreatePrivateFileDemo.java
java CreatePrivateFileDemo
~~~

Ví dụ đầu ra trên một POSIX filesystem chấp nhận đúng bộ quyền và không có chính sách làm giảm quyền:

~~~text
Readable text: secret
Requested mode: rw-------
Actual mode: rw-------
~~~

**Điều kiện thực thi:** Ví dụ này **không dành để chạy nguyên xi trên mọi Windows/provider**; nếu provider không hỗ trợ thuộc tính POSIX, thao tác có thể ném `UnsupportedOperationException`. Giá trị `Actual mode` phải được **đọc lại**, không mặc định bằng yêu cầu trên mọi filesystem. Các chính sách như umask, quyền kế thừa/ACL và provider có thể ảnh hưởng quyền hiệu lực.

Chúng ta không in tên đường dẫn tạm (được JVM chọn ngẫu nhiên), giúp phần output có thể dự đoán theo điều kiện đã nêu.

### 6.2. Tại sao tạo trước rồi set quyền không hoàn toàn tương đương?

Mẫu:

~~~java
Files.createFile(path);
Files.setPosixFilePermissions(
        path, PosixFilePermissions.fromString("rw-------"));
~~~

là đoạn minh họa có **hai thao tác filesystem riêng**. Giữa lần tạo và lần sửa quyền, tệp có thể đã tồn tại với quyền mặc định của provider, gây ra **cửa sổ thời gian** mà chính sách mong muốn chưa được áp dụng. Với dữ liệu nhạy cảm, đây có thể là vấn đề bảo mật.

Mẫu ưu tiên trên POSIX provider hỗ trợ:

~~~java
FileAttribute<Set<PosixFilePermission>> attrs =
        PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rw-------"));
Files.createFile(path, attrs);
~~~

giúp yêu cầu thuộc tính **ngay khi tạo**; theo hợp đồng của `Files.createFile`, việc kiểm tra tệp chưa tồn tại và tạo tệp là thao tác nguyên tử đối với các hoạt động filesystem ảnh hưởng thư mục. Các thuộc tính ban đầu cũng được đặt nguyên tử khi tạo nếu provider hỗ trợ. Nhưng **đây không phải giao dịch nhiều bước cho mọi hành động tiếp theo**, cũng không đảm bảo nội dung được ghi bền vững trên ổ đĩa.

## 7. Đừng nhầm “quyền yêu cầu” và “quyền thật sự”

Có ít nhất ba khái niệm:

1. **Requested permissions**: `Set<PosixFilePermission>` mà chương trình yêu cầu.
2. **Observed mode bits**: bộ quyền trả về bởi `Files.getPosixFilePermissions(file)` sau khi tạo.
3. **Effective access**: ứng dụng/người dùng có thể làm gì trên thực tế, còn phụ thuộc nhóm hiệu lực, ACL, chính sách OS, thư mục cha và các yếu tố khác.

Trên một số POSIX hệ thống, **umask** và chính sách tạo của OS/filesystem có thể làm quyền quan sát được **hẹp hơn** hoặc khác kỳ vọng. Đừng khẳng định tuyệt đối rằng `Files.createFile(..., attrs)` luôn tạo đúng mọi bit được yêu cầu trong mọi môi trường. Hãy kiểm tra quyền thật nếu điều đó là một **hậu điều kiện bắt buộc**.

Với tệp bí mật, nếu không chứng minh được quyền hiệu lực phù hợp, ứng dụng nên **từ chối xử lý nội dung nhạy cảm** hoặc chọn cơ chế bảo vệ theo hệ thống, thay vì bỏ qua kiểm tra rồi ghi dữ liệu.

### 7.1. Kiểm tra quyền sau tạo

~~~java
Set<PosixFilePermission> actual =
        Files.getPosixFilePermissions(path);

Set<PosixFilePermission> expected =
        PosixFilePermissions.fromString("rw-------");

if (!actual.equals(expected)) {
    throw new IOException("File permissions do not match policy");
}
~~~

Đây là đoạn minh họa với `Path path`, các import cần thiết và môi trường POSIX. Việc so sánh chính xác mode là **chính sách ví dụ**, có thể quá chặt hoặc không đủ với ứng dụng khác. Nếu chỉ yêu cầu “không cho group/others”, kiểm tra rằng các bit tương ứng **không xuất hiện** có thể phù hợp hơn kiểm tra equality tuyệt đối.

Quyền mode không phải tất cả bảo đảm bảo mật. Một filesystem với ACL khác hoặc cơ chế sandbox riêng có thể cần thêm bước kiểm tra.

## 8. Tạo thư mục với quyền ban đầu

Đối với thư mục cần chứa dữ liệu riêng, yêu cầu phổ biến là `rwx------`: chỉ owner được đọc danh sách, tạo/xóa (tùy quyền parent) và traverse vào thư mục.

~~~java
Set<PosixFilePermission> privateDirMode =
        PosixFilePermissions.fromString("rwx------");

FileAttribute<Set<PosixFilePermission>> dirAttrs =
        PosixFilePermissions.asFileAttribute(privateDirMode);

Files.createDirectory(path, dirAttrs);
~~~

Đây là đoạn minh họa, cần `Path path` chưa tồn tại và provider hỗ trợ POSIX. `Files.createDirectory` tạo **một** thư mục; nó không tự tạo toàn bộ cha nếu chưa có.

Với chuỗi thư mục lồng nhau:

~~~java
Files.createDirectories(path, dirAttrs);
~~~

`Files.createDirectories` tạo những thư mục còn thiếu; thuộc tính ban đầu áp dụng cho **những thư mục thực sự được tạo**. Các thư mục đã tồn tại không được thay quyền chỉ vì xuất hiện trong đường dẫn. Mặt khác, việc tạo nhiều thư mục **không phải một giao dịch nguyên tử duy nhất**: nếu bước giữa thất bại, một số thư mục cha có thể đã được tạo.

Đây là khác biệt cần nhớ khi cấu hình bảo mật cho toàn bộ cây thư mục. Nếu có thư mục cha do bên khác sở hữu hoặc có quyền mở rộng, đặt quyền cho thư mục con không tự giải quyết mọi nguy cơ đường dẫn.

### 8.1. Ví dụ Java 17: tạo thư mục riêng POSIX có kiểm tra hỗ trợ

Lưu **CreatePrivateDirectoryDemo.java**:

~~~java
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

public class CreatePrivateDirectoryDemo {
    public static void main(String[] args) throws IOException {
        Path parent = Files.createTempDirectory("private-dir-parent-");
        Path child = parent.resolve("private");

        try {
            FileStore store = Files.getFileStore(parent);
            if (!store.supportsFileAttributeView("posix")) {
                System.out.println("POSIX unsupported");
                return;
            }

            Set<PosixFilePermission> p =
                    PosixFilePermissions.fromString("rwx------");
            FileAttribute<Set<PosixFilePermission>> attr =
                    PosixFilePermissions.asFileAttribute(p);

            Files.createDirectory(child, attr);

            Set<PosixFilePermission> actual =
                    Files.getPosixFilePermissions(child);
            System.out.println("Created: " + Files.isDirectory(child));
            System.out.println("Owner access: " +
                    (actual.contains(PosixFilePermission.OWNER_READ)
                     && actual.contains(PosixFilePermission.OWNER_WRITE)
                     && actual.contains(PosixFilePermission.OWNER_EXECUTE)));
        } finally {
            Files.deleteIfExists(child);
            Files.deleteIfExists(parent);
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 CreatePrivateDirectoryDemo.java
java CreatePrivateDirectoryDemo
~~~

Có hai loại kết quả có chủ đích:

~~~text
POSIX unsupported
~~~

hoặc, trên môi trường POSIX cho phép tạo thư mục với các quyền owner yêu cầu:

~~~text
Created: true
Owner access: true
~~~

Ví dụ **không tự động hạ chuẩn bảo mật**: nếu không có POSIX view, nó thông báo không hỗ trợ thay vì tạo thư mục dùng quyền không xác định rồi coi như tương đương. Ngay cả khi `FileStore.supportsFileAttributeView("posix")` trả true, thao tác tạo có thể thất bại vì quyền, cấu hình cụ thể hoặc thuộc tính ban đầu không được hỗ trợ; chương trình để ngoại lệ đi lên thay vì giả thành công.

## 9. Thuộc tính khi tạo tệp tạm

Tệp tạm không đồng nghĩa “tệp không quan trọng”. Tên ngẫu nhiên giúp tránh trùng, nhưng **không phải thay thế** cho việc bảo vệ quyền đọc/ghi. Nếu tệp tạm chứa token truy cập, dữ liệu y tế, bản xuất chưa công bố hoặc khóa, cần đặt chính sách bảo mật phù hợp.

NIO cung cấp các overload:

~~~java
Files.createTempFile(dir, "session-", ".tmp", attrs);
Files.createTempDirectory(dir, "private-", attrs);
~~~

Đây là đoạn minh họa với `Path dir` và `FileAttribute<?> attrs` đã có. API trả lại `Path` đến entry **được tạo thật**. Dữ liệu mới tạo khác với một chuỗi tên được dự đoán sẵn.

### 9.1. Ví dụ Java 17: tệp tạm với yêu cầu POSIX

Lưu **PrivateTempFileDemo.java**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

public class PrivateTempFileDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("temp-policy-root-");
        Path temp = null;

        try {
            FileStore store = Files.getFileStore(root);
            if (!store.supportsFileAttributeView("posix")) {
                System.out.println("POSIX unsupported");
                return;
            }

            FileAttribute<Set<PosixFilePermission>> attr =
                    PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rw-------"));

            temp = Files.createTempFile(root, "private-", ".txt", attr);
            Files.writeString(temp, "one", StandardCharsets.UTF_8);

            System.out.println("Exists: " + Files.isRegularFile(temp));
            System.out.println("Contents: " +
                    Files.readString(temp, StandardCharsets.UTF_8));
        } finally {
            if (temp != null) {
                Files.deleteIfExists(temp);
            }
            Files.deleteIfExists(root);
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 PrivateTempFileDemo.java
java PrivateTempFileDemo
~~~

Các kết quả mong đợi tùy provider:

~~~text
POSIX unsupported
~~~

hoặc khi provider POSIX hỗ trợ việc tạo:

~~~text
Exists: true
Contents: one
~~~

Chương trình không in tên tệp ngẫu nhiên. `Files.createTempFile` tự cấp một tên không trùng theo hợp đồng, thay vì ứng dụng tự ghép `System.currentTimeMillis()` rồi nghĩ rằng tên đó an toàn. Nếu tệp không được phép rò rỉ, trước khi ghi nội dung nhạy cảm vẫn nên kiểm tra hậu điều kiện và quản lý cleanup chắc chắn.

### 9.2. Một số default attributes có thể đã khác Files.createFile

Các overload tạo **tệp tạm** có thể sử dụng các thuộc tính mặc định khác khi không chỉ định attrs, tùy provider, ví dụ có xu hướng hạn chế hơn ở một số hệ thống. Nhưng không nên xây cam kết bảo mật trên phỏng đoán “temp luôn là private”: nếu có yêu cầu cụ thể, hãy **truyền thuộc tính phù hợp và xác minh** bằng API/platform hỗ trợ.

## 10. Mở và tạo ngay bằng Files.newByteChannel

`Files.newByteChannel(Path, Set<? extends OpenOption>, FileAttribute<?>... attrs)` cho phép vừa khai báo cách mở, vừa cung cấp thuộc tính khi tệp cần được tạo mới.

Một mẫu tạo tệp mới tuyệt đối không ghi đè:

~~~java
Set<OpenOption> options = Set.of(
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE);

try (SeekableByteChannel channel =
        Files.newByteChannel(path, options, initialAttr)) {
    // Ghi các byte thông qua channel.
}
~~~

Đây là đoạn minh họa; cần `Path path`, `FileAttribute<?> initialAttr`, các import `java.nio.channels.SeekableByteChannel`, `java.nio.file.Files`, `java.nio.file.OpenOption`, `java.nio.file.StandardOpenOption`, `java.util.Set`. Tùy chọn `CREATE_NEW` yêu cầu tệp **chưa tồn tại**; việc kiểm tra trùng tên và tạo được gắn với cùng thao tác tạo theo hợp đồng. `WRITE` yêu cầu quyền ghi.

Nếu tệp **đã tồn tại**, `CREATE_NEW` làm thao tác thất bại; initial attrs không dùng để thay quyền của tệp cũ. Với `CREATE` và một tệp hiện hữu, `attrs` cũng không tự động cập nhật quyền hiện tại.

### 10.1. Ví dụ Java 17: tạo tệp và ghi byte với initial attributes

Lưu **CreateWithChannelDemo.java**:

~~~java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

public class CreateWithChannelDemo {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("channel-create-");
        Path file = dir.resolve("entry.txt");

        try {
            FileStore store = Files.getFileStore(dir);
            if (!store.supportsFileAttributeView("posix")) {
                System.out.println("POSIX unsupported");
                return;
            }

            FileAttribute<Set<PosixFilePermission>> attr =
                    PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rw-------"));

            Set<OpenOption> options =
                    Set.of(StandardOpenOption.CREATE_NEW,
                           StandardOpenOption.WRITE);

            try (SeekableByteChannel channel =
                         Files.newByteChannel(file, options, attr)) {
                ByteBuffer bytes = ByteBuffer.wrap(
                        "hello".getBytes(StandardCharsets.UTF_8));
                while (bytes.hasRemaining()) {
                    channel.write(bytes);
                }
            }

            System.out.println("Text: " +
                    Files.readString(file, StandardCharsets.UTF_8));
            System.out.println("Bytes: " + Files.size(file));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 CreateWithChannelDemo.java
java CreateWithChannelDemo
~~~

Kết quả khi provider POSIX đáp ứng yêu cầu:

~~~text
Text: hello
Bytes: 5
~~~

Hoặc:

~~~text
POSIX unsupported
~~~

**Tại sao có vòng `while (bytes.hasRemaining())`?** `SeekableByteChannel.write(ByteBuffer)` không nên bị giả định sẽ ghi toàn bộ lượng byte còn lại trong một lời gọi trên mọi triển khai. Lặp đến khi buffer hết giúp xử lý các lần ghi một phần, dù trong ứng dụng thực còn cần xem xét tình huống không tiến triển hay channel không chặn theo loại channel thực tế.

**Phân biệt với `Files.newOutputStream`:** overload stream nhận các open options nhưng không có đối số initial attributes. Khi cần truyền `FileAttribute<?>` cùng chế độ mở, `newByteChannel` là lựa chọn NIO trực tiếp.

## 11. CREATE, CREATE_NEW và nguy cơ ghi đè

Hai option dễ nhầm:

- **`CREATE`**: nếu tệp thiếu, cho phép tạo; nếu tệp đã có, có thể mở tệp hiện hữu theo những option còn lại.
- **`CREATE_NEW`**: bắt buộc tạo mới; khi tệp đã tồn tại, thao tác thường ném `FileAlreadyExistsException`. Yêu cầu này đặc biệt hữu ích khi không được phép ghi đè dữ liệu khác.

Mẫu nguy hiểm:

~~~java
if (!Files.exists(path)) {
    Files.createFile(path, attrs);
}
~~~

Đoạn minh họa có điều kiện đua (*TOCTOU*). Giữa bước kiểm tra và bước tạo, một tiến trình khác có thể tạo tệp. Kết quả kiểm tra trước không bảo đảm thao tác sau thành công.

Tốt hơn:

~~~java
try {
    Files.createFile(path, attrs);
} catch (FileAlreadyExistsException e) {
    // Phân xử tình huống trùng tên: không ghi đè.
}
~~~

Đây là đoạn minh họa. `Files.createFile` vốn đã kiểm tra sự không tồn tại và tạo theo hợp đồng nguyên tử đối với thao tác filesystem ảnh hưởng thư mục.

**Lưu ý:** “Tạo tệp và đặt thuộc tính ban đầu nguyên tử” không có nghĩa “ghi toàn bộ nội dung, tính checksum, đóng và chuyển tên” đều nằm trong cùng một transaction. Nếu cần cập nhật tài liệu quan trọng, còn phải thiết kế quy trình ghi tệp tạm, xác minh và thay thế đích phù hợp.

## 12. Kiểm tra provider và view trước khi dùng POSIX

Không phải môi trường nào cũng hỗ trợ quyền `PosixFilePermission`. Ví dụ nhiều hệ thống Windows thường dùng ACL theo chính sách hệ điều hành; việc cố dùng `PosixFilePermissions` trực tiếp có thể không được hỗ trợ.

Hai cách kiểm tra phổ biến:

~~~java
boolean posixSupported = Files.getFileStore(dir)
        .supportsFileAttributeView("posix");
~~~

hoặc:

~~~java
boolean posixSupported =
        Files.getFileAttributeView(dir,
                PosixFileAttributeView.class) != null;
~~~

Cả hai là đoạn minh họa; `dir` là `Path` của thư mục hiện hữu. `FileStore.supportsFileAttributeView("posix")` hỏi capability của store; `Files.getFileAttributeView` lấy một view khả dụng đối với path và provider hiện tại.

**Các phép kiểm tra capability không phải lời hứa tuyệt đối rằng thao tác tạo sắp tới sẽ luôn thành công.** Quyền, trạng thái mount, chính sách provider và thay đổi filesystem vẫn có thể làm lời gọi tạo thất bại; luôn xử lý ngoại lệ ngay tại thao tác.

### 12.1. Khi POSIX không được hỗ trợ phải làm gì?

Không nên:

~~~java
try {
    Files.createFile(path, posixAttrs);
} catch (UnsupportedOperationException ignored) {
    Files.createFile(path); // Có thể làm giảm yêu cầu bảo mật.
}
~~~

Đây là **anti-pattern** nếu mục tiêu là “file bắt buộc chỉ owner đọc được”. Tạo tệp với mặc định khi chính sách POSIX không thể áp dụng có thể làm tệp mới rộng quyền hơn dự định.

Các chiến lược phù hợp hơn:

1. **Dừng và báo không hỗ trợ** nếu hệ thống bắt buộc có quyền POSIX cụ thể.
2. **Thiết kế chính sách riêng cho Windows/ACL** bằng các API/provider hỗ trợ, và kiểm chứng hiệu lực trên OS mục tiêu.
3. **Dùng thư mục bảo vệ đã được provisioning** theo chính sách nền tảng; không suy rằng directory permissions của POSIX tương đương ACL của mọi hệ thống khác.
4. **Chọn lưu trữ phù hợp với mức nhạy cảm** (ví dụ kho bí mật, dịch vụ quản lý khóa hoặc môi trường có policy cụ thể), thay vì cố gắng mô phỏng chmod trên mọi máy.
5. **Ghi rõ khả năng tương thích của ứng dụng** trong tài liệu triển khai và kiểm thử trên filesystem đích.

Nếu dữ liệu không nhạy cảm và không có yêu cầu quyền chặt chẽ, có thể lựa chọn hành vi portable hơn. Nhưng đó là **quyết định nghiệp vụ**, không phải một fallback âm thầm sau khi bảo mật thất bại.

## 13. Một FileAttributeView có thể tồn tại nhưng không đặt được lúc tạo

Ví dụ các view:

| View | API thường gặp | Mục đích |
| --- | --- | --- |
| `BasicFileAttributeView` | `Files.readAttributes`, `Files.getLastModifiedTime` | Kích thước, loại, thời gian |
| `PosixFileAttributeView` | `Files.getPosixFilePermissions`, `Files.setPosixFilePermissions` | POSIX owner/group/mode |
| `DosFileAttributeView` | `Files.readAttributes(path, DosFileAttributes.class)`, `Files.setAttribute(path, "dos:hidden", ...)` | DOS read-only/hidden/archive/system |
| `AclFileAttributeView` | `Files.getFileAttributeView(path, AclFileAttributeView.class)` | Access control lists |
| `FileOwnerAttributeView` | `Files.getOwner`, `Files.setOwner` | Chủ sở hữu |

API đọc hoặc cập nhật một view **sau khi tạo** không tự chứng minh provider chấp nhận thuộc tính đó trong mảng `FileAttribute<?>...` truyền lúc tạo.

Ví dụ, `Files.setAttribute(path, "dos:hidden", true)` (nếu provider hỗ trợ) là **thao tác sau khi entry đã tồn tại**, không có nghĩa một `FileAttribute<Boolean>` tự tạo có tên `"dos:hidden"` chắc chắn được chấp nhận lúc `Files.createFile`.

Tương tự, việc yêu cầu đổi owner hoặc ACL ở thời điểm tạo cần xem đúng hợp đồng/khả năng provider, và không mặc định mọi ứng dụng được phép đặt chủ sở hữu thành người khác.

## 14. Những trường hợp thực tế

### 14.1. File cấu hình chứa token

Yêu cầu: file chỉ owner được đọc/ghi trên POSIX.

Thiết kế hợp lý:

1. Đảm bảo thư mục cha do thành phần tin cậy sở hữu và không cho đối tượng ngoài sửa tên entry.
2. Xác định POSIX view/initial attribute có được hỗ trợ không.
3. Tạo bằng `Files.createFile(path, posixAttr)` hoặc `Files.newByteChannel(path, CREATE_NEW+WRITE, attr)`.
4. Đọc lại quyền và kiểm tra hậu điều kiện trước khi ghi dữ liệu nhạy cảm nếu chính sách yêu cầu.
5. Quản lý writer/channel bằng `try-with-resources`, xử lý lỗi ghi/close.
6. Không ghi đè một tệp có sẵn bằng tên do người dùng không tin cậy cung cấp.

### 14.2. Thư mục làm việc riêng

Yêu cầu: chỉ owner có thể liệt kê/traverse thư mục.

Trên POSIX, yêu cầu `rwx------` khi tạo `Files.createDirectory` hoặc `Files.createTempDirectory`. Nhưng cũng cần xem quyền của **thư mục cha**, đặc biệt nếu đường dẫn nằm ở vùng chia sẻ hoặc được tạo từ input bên ngoài.

### 14.3. Tệp báo cáo nhóm

Yêu cầu: owner đọc/ghi, group đọc.

`rw-r-----` là một tập mode POSIX có thể thích hợp. Nhưng group thực tế của tệp thường phụ thuộc chính sách owner/group, setgid của thư mục và hệ thống tệp; **chỉ đặt mode chưa bảo đảm file thuộc đúng group được phép đọc**. Khi có yêu cầu chia sẻ nhóm nghiêm ngặt, cần kiểm tra cả group và quyền hiệu lực.

### 14.4. Tệp chỉ đọc ngay khi tạo

`r--------` biểu thị owner read-only. Tuy nhiên, nếu ngay sau khi tạo chương trình muốn ghi nội dung bằng một stream khác, một số cách ghi có thể không được phép; và trạng thái file descriptor đã mở trước thay đổi quyền có thể có quy tắc OS riêng.

Thông thường nên:
- Tạo tệp riêng tư cho quá trình ghi bằng quyền phù hợp (như `rw-------`).
- Hoàn tất dữ liệu và đóng đúng tài nguyên.
- Nếu nghiệp vụ đòi file cuối cùng chỉ đọc, đặt quyền cuối theo chính sách rõ ràng và chấp nhận rằng đây là một bước khác cần quản lý lỗi.
- Nếu có yêu cầu “không tồn tại bất kỳ cửa sổ quyền nào rộng hơn chính sách”, cần thiết kế provisioning/atomic publication phù hợp.

“Chỉ owner đọc” và “chương trình cần ghi nội dung ban đầu” là hai mục tiêu phải được phối hợp, không đơn thuần nhồi cùng một mode cho mọi giai đoạn.

## 15. Thay đổi thuộc tính sau khi tạo: khác biệt và rủi ro

Java có thể thay quyền sau tạo:

~~~java
Files.setPosixFilePermissions(
        path, PosixFilePermissions.fromString("rw-------"));
~~~

hoặc dùng một `FileAttributeView` tương ứng. Điều này hữu ích khi quản trị tệp hiện hữu, hoặc khi muốn thay đổi quyền **sau khi đã ghi xong nội dung**.

Nhưng hai chiến lược này khác nhau:

~~~text
A. Initial attrs:
   Files.createFile(path, attrs)
   └─ Tạo mới + yêu cầu áp dụng attrs tại thời điểm tạo

B. Post-creation update:
   Files.createFile(path)
   └─ Tệp tồn tại với thuộc tính ban đầu mặc định
       └─ Files.setPosixFilePermissions(path, ...)
           └─ Có khoảng thời gian giữa hai thao tác
~~~

Nếu ứng dụng tạo file rồi đổi quyền, ngay cả khi hai lời gọi nằm liên tiếp trong cùng thread, thread/process khác vẫn có thể quan sát file trong khoảng giữa. Với dữ liệu cần bảo mật ngay từ khi xuất hiện, ưu tiên initial attributes **khi provider hỗ trợ**.

Với tệp hiện hữu, `FileAttribute<?>` không phải công cụ “sửa mode” qua `Files.createFile`: vì `createFile` yêu cầu chưa tồn tại, nó sẽ thất bại với entry đang có. Chọn API set thuộc tính riêng và đánh giá kiểm soát truy cập thích hợp.

## 16. Lỗi thường gặp và ngoại lệ liên quan

| Lỗi/tình huống | Loại ngoại lệ/hệ quả | Cách xử lý |
| --- | --- | --- |
| Tạo file nhưng path đã tồn tại | Thường `FileAlreadyExistsException` | Không tự ghi đè; quyết định conflict |
| Thư mục cha không tồn tại | Có thể `NoSuchFileException` | Tạo cha theo chính sách hoặc báo lỗi |
| Không có quyền tạo trong thư mục cha | `AccessDeniedException` hoặc `IOException` khác | Báo lỗi/quyền, không nâng đặc quyền tùy tiện |
| Thuộc tính ban đầu không được hỗ trợ | `UnsupportedOperationException` | Dừng hoặc chiến lược provider riêng được duyệt |
| Đối số `null` không hợp lệ | `NullPointerException` | Xác thực API inputs |
| Chuỗi POSIX permissions sai cú pháp | `IllegalArgumentException` | Kiểm tra chính xác 9 ký tự |
| Nhiều attrs có cùng name | Hành vi dùng giá trị sau cùng theo hợp đồng áp dụng | Tránh truyền tên trùng |
| Tạo nhiều thư mục với `createDirectories` rồi lỗi giữa chừng | Một số thư mục cha đã được tạo | Xử lý cleanup/rollback theo nghiệp vụ |
| Đã tạo file với attrs, nhưng ghi nội dung lỗi | File trống hoặc dở dang có thể còn lại | Dọn/tạm hóa/thay thế có kiểm soát |
| Kiểm tra mode sau tạo không như yêu cầu | Chính sách OS/provider thay đổi quyền hiệu lực | Không coi tác vụ bảo mật là thành công |
| Kiểm tra `Files.exists` trước `createFile` | TOCTOU, vẫn có thể trùng tên | Dựa vào thao tác create và bắt lỗi |
| Tạo Path bên trong vùng có symbolic link ngoài ý muốn | Có thể trỏ tới vị trí khác | Kiểm soát thư mục cha, owner, symlink và race |

**Không phân loại lỗi bằng chuỗi thông báo hệ điều hành** như `"Permission denied"` vì thông điệp phụ thuộc OS/provider và có thể thay đổi. Bắt lớp con `IOException` phù hợp, giữ nguyên `cause`, và log thao tác/đường dẫn an toàn.

### 16.1. Khi nào là IOException, khi nào là UnsupportedOperationException?

`IOException` là checked exception cho lỗi I/O, ví dụ thiếu đường dẫn hoặc thiết bị. `UnsupportedOperationException` là unchecked exception thường biểu thị rằng provider không hỗ trợ lựa chọn/thuộc tính được yêu cầu.

Nếu ứng dụng có chính sách bắt buộc quyền POSIX, `UnsupportedOperationException` là **lỗi khả năng đáp ứng yêu cầu**, không phải một tình huống để tạo file rộng quyền “cho xong”.

Cũng cần lưu ý `SecurityException` trong những môi trường có cơ chế kiểm soát truy cập phù hợp; nó không phải `IOException`.

## 17. Kiểm thử đúng cách khi nhiều hệ thống tệp khác nhau

Không phải mọi môi trường Windows đều giống nhau; cũng không phải mọi thư mục trên một máy Linux đều nằm trên cùng filesystem/provider. Có thể có NTFS, FAT, tmpfs, network mount, ZIP filesystem hoặc provider đặc biệt.

Một chiến lược kiểm thử:

1. Tạo một thư mục thử nghiệm trong **đúng vùng filesystem** ứng dụng sẽ sử dụng.
2. Kiểm tra hỗ trợ của `FileStore`/`FileAttributeView` khi quan trọng.
3. Tạo file mới với `FileAttribute<?>` theo chính sách.
4. Đọc lại thuộc tính sau tạo; đừng chỉ kiểm tra `Files.exists`.
5. Kiểm tra tệp không bị ghi đè nếu path đã tồn tại.
6. Kiểm thử thiếu quyền và giới hạn khác bằng fixture kiểm soát, không làm thay đổi quyền của dữ liệu thật.
7. Kiểm thử lỗi khi ghi nội dung để xem file dở dang có bị để lại hay không.
8. Kiểm tra cleanup hoạt động trên filesystem mục tiêu.
9. Không giả định việc set mode 9 ký tự mô tả đầy đủ ACL của Windows.

### 17.1. Kiểm tra thuộc tính theo bất biến bảo mật

Với chính sách “group và others **không được có quyền mode**”, có thể kiểm tra các phần tử trong set:

~~~java
Set<PosixFilePermission> actual =
        Files.getPosixFilePermissions(path);

boolean restricted =
        !actual.contains(PosixFilePermission.GROUP_READ)
        && !actual.contains(PosixFilePermission.GROUP_WRITE)
        && !actual.contains(PosixFilePermission.GROUP_EXECUTE)
        && !actual.contains(PosixFilePermission.OTHERS_READ)
        && !actual.contains(PosixFilePermission.OTHERS_WRITE)
        && !actual.contains(PosixFilePermission.OTHERS_EXECUTE);
~~~

Đây là đoạn minh họa, không phải chương trình độc lập. **Bất biến mode-bit** này là một bước kiểm tra cụ thể, không tự chứng minh mọi quyền hiệu lực nếu có ACL/đặc quyền/quy tắc OS khác. Khi yêu cầu bảo mật cao, phải đánh giá trên đúng filesystem và mô hình quyền của ứng dụng.

### 17.2. Tránh test bằng tên cố định trong thư mục người dùng

Không nên chọn một tên như `"test.txt"` trong thư mục làm việc, xóa tệp nếu tồn tại rồi chạy test: điều đó có thể xóa dữ liệu của người dùng khác hoặc dữ liệu ứng dụng. Sử dụng `Files.createTempDirectory` để tạo vùng thử nghiệm riêng, thực hiện các thao tác bên trong rồi xóa **đúng những entry do test tạo**.

## 18. Chọn giải pháp nào cho từng nhu cầu?

| Nhu cầu | Lựa chọn có ý nghĩa |
| --- | --- |
| Tạo tệp mới, không ghi đè, kèm POSIX mode | `Files.createFile(path, PosixFilePermissions.asFileAttribute(...))` |
| Tạo thư mục riêng tư | `Files.createDirectory(path, attrs)` hoặc `createTempDirectory(..., attrs)` |
| Tạo nhiều thư mục cha còn thiếu | `Files.createDirectories(path, attrs)`; nhớ không bảo đảm atomic toàn cây |
| Tạo và mở kênh byte để ghi ngay với attrs | `Files.newByteChannel(path, Set.of(CREATE_NEW, WRITE), attrs)` |
| Tạo tệp tạm có quyền cụ thể | `Files.createTempFile(dir, prefix, suffix, attrs)` |
| Đặt quyền của tệp đã tồn tại | `Files.setPosixFilePermissions(path, permissions)` |
| Cần ACL của filesystem không POSIX | Tìm hiểu `AclFileAttributeView`/provider và chính sách triển khai |
| Chỉ cần tệp mới với quyền mặc định, không có policy riêng | `Files.createFile(path)` hoặc overload tạo tệp thích hợp |
| Cần ngăn race “đã tồn tại” | `Files.createFile` hoặc `CREATE_NEW`, không dùng `exists` rồi mới tạo |
| Cần chống mất dữ liệu khi ghi lỗi | Ghi tệp tạm/đóng/xác minh/thay thế theo policy; initial attrs chỉ giải quyết một phần |

## 19. Những hiểu sai phổ biến

**Hiểu sai 1: `FileAttribute<?>` là cách đổi quyền sau khi đã mở một tệp.**

Không. Nó mô tả **thuộc tính ban đầu** trong các overload có hỗ trợ khi tạo. Với entry tồn tại, dùng API `set...` tương ứng nếu hệ thống cho phép.

**Hiểu sai 2: Chỉ cần gọi `Files.createFile(path)` rồi `Files.setPosixFilePermissions` ngay sau đó là hoàn toàn nguyên tử.**

Không. Hai lần gọi tạo một khoảng thời gian có thể quan sát được. Nếu policy buộc có mode ngay lúc xuất hiện, hãy dùng overload nhận attrs trên provider hỗ trợ.

**Hiểu sai 3: `CREATE_NEW` chính là quyền file riêng tư.**

Không. Nó chỉ định thao tác tạo mới không ghi đè. Quyền file do initial attributes và chính sách của hệ thống tệp quyết định.

**Hiểu sai 4: POSIX mode 9 ký tự hoạt động như nhau trên mọi Windows, ZIP và network filesystem.**

Không. Các view được hỗ trợ phụ thuộc provider. Windows thường dựa trên ACL/NTFS; POSIX mode không phải mô hình quyền chung cho mọi môi trường.

**Hiểu sai 5: Nếu provider không hỗ trợ attrs, chỉ cần bỏ attrs và tạo file mặc định, vẫn bảo mật tương đương.**

Không chắc và có thể nguy hiểm. Với tệp nhạy cảm, cần từ chối hoặc dùng phương án bảo mật đã được kiểm chứng cho nền tảng.

**Hiểu sai 6: Set initial attrs thành công tức là đã ghi xong nội dung an toàn.**

Không. Tạo tệp, ghi dữ liệu, close, đồng bộ và publish kết quả là các vấn đề khác nhau. Nếu cần cập nhật quan trọng, phải thiết kế toàn bộ vòng đời.

**Hiểu sai 7: Có quyền POSIX owner-only là chắc chắn không ai khác truy cập được trong mọi hoàn cảnh.**

Không. Quyền hiệu lực còn phụ thuộc ACL, đặc quyền OS, quyền của thư mục cha và mô hình bảo mật của hệ thống. Chỉ khẳng định trong phạm vi đã kiểm tra.

## 20. Câu hỏi tự kiểm tra

**Câu 1.** Sự khác nhau giữa `Files.createFile(path, attrs)` và `Files.setPosixFilePermissions(path, perms)`?

**Đáp án:** lệnh đầu tạo tệp mới và yêu cầu thuộc tính ngay khi tạo; lệnh thứ hai sửa quyền một entry đã tồn tại. Nếu tạo rồi mới sửa, có thể có cửa sổ thời gian với quyền chưa đúng.

**Câu 2.** `FileAttribute<Set<PosixFilePermission>>` dùng để làm gì?

**Đáp án:** là đối tượng kiểu hóa tên/giá trị thuộc tính được truyền vào các phương thức tạo có tham số `FileAttribute<?>...`. `PosixFilePermissions.asFileAttribute(...)` tạo giá trị thích hợp cho POSIX permissions.

**Câu 3.** Mode `rw-------` là gì?

**Đáp án:** owner đọc và ghi; group/others không được cấp bit nào trong POSIX mode. Nó không tự nói hết về ACL hoặc đặc quyền khác.

**Câu 4.** Với một thư mục riêng tư, `rw-------` có phù hợp như một thư mục cần traverse hay không?

**Đáp án:** thường không. Thư mục cần quyền `x` để traverse trong mô hình POSIX; `rwx------` là mẫu điển hình khi chỉ owner cần truy cập.

**Câu 5.** Truyền attrs vào `Files.createDirectories` có tự đổi quyền của thư mục cha đã có không?

**Đáp án:** không. Chúng áp dụng cho thư mục được tạo mới theo hợp đồng, không tự cập nhật entry hiện hữu. Và toàn bộ việc tạo nhiều cấp không phải một transaction duy nhất.

**Câu 6.** Nếu đang dùng một filesystem không hỗ trợ POSIX, nên tự bắt `UnsupportedOperationException` rồi tạo tệp không có attrs không?

**Đáp án:** chỉ khi policy nghiệp vụ cho phép và đã hiểu quyền mặc định. Nếu cần owner-only theo tiêu chuẩn bắt buộc, không được fallback âm thầm.

**Câu 7.** Khi dùng `Files.newByteChannel(path, Set.of(CREATE, WRITE), attrs)` trên tệp đã có, attrs có tự thay mode của tệp cũ?

**Đáp án:** không. Initial attributes chỉ được dùng khi **thật sự tạo mới**. Thao tác mở tệp đã tồn tại không phải một yêu cầu sửa quyền.

**Câu 8.** Vì sao nên dùng `CREATE_NEW` khi không được phép ghi đè?

**Đáp án:** nó yêu cầu quá trình tạo mới và từ chối nếu đã tồn tại theo hợp đồng, tránh mô hình kiểm tra `Files.exists` rồi tạo gây TOCTOU.

**Câu 9.** Với tệp nhạy cảm, chỉ kiểm tra `Files.exists(file)` sau khi tạo đã đủ?

**Đáp án:** chưa. Cần xác minh quyền, quyền sở hữu/ACL theo chính sách, việc ghi/đóng thành công và các điều kiện an toàn liên quan.

### Bài tập thực hành

1. Viết chương trình tạo một tệp qua `Files.createFile` với `rw-------`, đọc lại bằng `Files.getPosixFilePermissions` và in theo `PosixFilePermissions.toString`. Nếu provider không hỗ trợ, báo rõ và không tạo dữ liệu nhạy cảm.
2. Tạo thư mục bằng `rwx------` với `Files.createDirectory`. Thử tạo một tệp bên trong rồi xóa cả hai ở cuối chương trình.
3. Dùng `Files.createTempFile` với initial attrs; xác minh kết quả không dựa vào tên tệp ngẫu nhiên.
4. Dùng `Files.newByteChannel` với `CREATE_NEW` + `WRITE` + attrs, ghi 5 byte UTF-8 và kiểm tra kích thước.
5. Thử tạo lại cùng đường dẫn bằng `Files.createFile` để thấy `FileAlreadyExistsException`; đừng xóa tệp gốc khi thử.
6. Đặt chính sách nhóm `rw-r-----` và tìm hiểu vì sao group thực tế của file cũng cần được kiểm tra.
7. Tạo một thư mục con bằng `Files.createDirectories`, so sánh quyền của thư mục đã tồn tại và thư mục mới được tạo; đừng giả định thư mục cũ được đổi quyền.
8. Thiết kế nhánh xử lý cho Windows/ACL và POSIX mà không bỏ qua ràng buộc bảo mật, ghi rõ khả năng hỗ trợ trước khi triển khai.

## 21. Kết luận

- **`FileAttribute<?>`** là cách diễn đạt thuộc tính ban đầu khi tạo entry thông qua những overload `Files` có hỗ trợ.
- **`Files.createFile(path, attrs)`** và **`Files.createDirectory(path, attrs)`** cho phép yêu cầu thuộc tính lúc tạo; `Files.createDirectories` áp dụng attrs cho entry mới chứ không tự sửa entry cũ.
- **`PosixFilePermissions.fromString`** và **`asFileAttribute`** là bộ công cụ gọn để tạo yêu cầu quyền POSIX; mode `rw-------` thường dùng cho tệp riêng, `rwx------` cho thư mục riêng.
- **Tạo với attrs khác với “tạo xong rồi chmod”.** Khi provider hỗ trợ, đặt thuộc tính ngay lúc tạo hạn chế race về quyền mặc định.
- **Provider quyết định khả năng hỗ trợ và quyền hiệu lực.** Không giả định mọi hệ thống tệp hiểu POSIX, ACL hay metadata giống nhau; kiểm tra và báo lỗi nếu chính sách không được đáp ứng.
- **`CREATE_NEW` ngăn ghi đè**, còn `CREATE` không tự thay đổi thuộc tính của tệp đã tồn tại.
- **Tạo đúng quyền chưa đủ cho độ an toàn toàn diện.** Vẫn cần quản lý luồng ghi, close, lỗi dở dang, đồng bộ dữ liệu và kiểm soát đường dẫn.

### Tài liệu tham khảo Java SE 17

- [Files.createFile / createDirectory / createDirectories / createTempFile](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [Files.newByteChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#newByteChannel(java.nio.file.Path,java.util.Set,java.nio.file.attribute.FileAttribute...))
- [FileAttribute](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/FileAttribute.html)
- [PosixFilePermissions](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/PosixFilePermissions.html)
- [PosixFilePermission](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/PosixFilePermission.html)
- [FileStore](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileStore.html)
- [StandardOpenOption](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardOpenOption.html)
- [PosixFileAttributeView](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/PosixFileAttributeView.html)
- [AclFileAttributeView](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/AclFileAttributeView.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-attributes-and-permissions-010">FileStore, dung lượng và supportedFileAttributeViews</a>

<details>
<summary>Xem chi tiết</summary>

# `FileStore`, dung lượng và `supportedFileAttributeViews` trong Java

Khi thao tác file bằng Java NIO.2, chương trình đôi khi cần biết **file đang nằm trên kho lưu trữ nào**, **dung lượng còn có thể sử dụng là bao nhiêu**, hoặc **filesystem có hỗ trợ POSIX permissions, DOS attributes, ACL và các loại metadata khác không**. Đây là những câu hỏi ở tầng **filesystem và thuộc tính**, khác với việc đọc nội dung byte của một file.

Java 17 cung cấp những điểm vào chính:

- `Files.getFileStore(Path)`: tìm `FileStore` gắn với vị trí một đường dẫn.
- `FileStore.getTotalSpace()`, `getUnallocatedSpace()`, `getUsableSpace()`: thông tin dung lượng tính theo **byte**.
- `FileStore.supportsFileAttributeView(...)`: hỏi một store có hỗ trợ loại attribute view cụ thể hay không.
- `FileSystem.supportedFileAttributeViews()`: lấy tập tên các attribute view mà **filesystem** báo hỗ trợ.
- `Files.readAttributes(...)`, `Files.getFileAttributeView(...)`: đọc hoặc truy cập view của **một đường dẫn cụ thể**.

Điểm quan trọng: **dung lượng báo “có thể sử dụng” chỉ là ảnh chụp trạng thái ở một thời điểm, không bảo đảm lệnh ghi tiếp theo thành công**; và **“supportedFileAttributeViews” của `FileSystem` không hoàn toàn đồng nghĩa kiểm tra support trên một `FileStore` riêng lẻ**. Bài này giải thích rõ ba tầng đó qua ví dụ Java 17+, bảng lựa chọn và các lưu ý về quyền, quota, portability và điều kiện cạnh tranh.

## 1. Phân biệt `Path`, `FileSystem`, `FileStore` và attribute view

### `Path` — vị trí của một mục

`Path` là đường dẫn tới file/thư mục hoặc một vị trí chưa tồn tại:

```java
Path path = Path.of("data", "report.txt");
```

Đây là **mã minh họa**, cần import `java.nio.file.Path`. Tạo `Path` không tự mở, tạo hay đọc file. Một `Path` gắn với một `FileSystem` cụ thể và được diễn giải theo quy tắc provider của filesystem đó.

### `FileSystem` — mô hình filesystem do provider cung cấp

`java.nio.file.FileSystem` đại diện cho một filesystem với root, separator, path matcher, attribute view có thể hỗ trợ và tập file store mà provider cho phép liệt kê.

Để lấy filesystem của một path:

```java
FileSystem fs = path.getFileSystem();
```

Đây là **mã minh họa**, cần biến `path` và import `FileSystem`. Để lấy filesystem mặc định:

```java
FileSystem defaultFs = FileSystems.getDefault();
```

Cần import `java.nio.file.FileSystems`. Một ứng dụng có thể còn làm việc với provider khác default, chẳng hạn ZIP filesystem khi đã mở provider tương ứng.

### `FileStore` — nơi lưu trữ, với thông tin dung lượng và metadata

`java.nio.file.FileStore` mô tả một **kho lưu trữ** được filesystem/provider phơi bày. Trên hệ thống thật, nó thường liên hệ tới volume, mount, phân vùng, network share hoặc đối tượng lưu trữ logic, nhưng **không bắt buộc trùng một ổ cứng vật lý**.

```java
FileStore store = Files.getFileStore(path);
```

Đây là **mã minh họa**, cần import `FileStore`, `Files`, biến `path` và xử lý `IOException`. `Files.getFileStore` có thể truy cập filesystem, nên khác với việc chỉ gọi `path.getFileSystem()`.

### Attribute view — nhóm thuộc tính có cấu trúc

`FileAttributeView` là giao diện dùng để truy cập một **nhóm metadata** của file. Những view thường gặp:

| View | Mục đích | API liên quan |
|---|---|---|
| `basic` | Loại file, size, thời điểm tạo/sửa/truy cập, file key khi có | `BasicFileAttributeView`, `BasicFileAttributes` |
| `posix` | Permissions kiểu POSIX, owner, group | `PosixFileAttributeView`, `PosixFileAttributes` |
| `dos` | Hidden, read-only, system, archive | `DosFileAttributeView`, `DosFileAttributes` |
| `acl` | Access-control list theo provider | `AclFileAttributeView` |
| `owner` | Chủ sở hữu file | `FileOwnerAttributeView` |
| `user` | Thuộc tính mở rộng do người dùng đặt, khi có support | `UserDefinedFileAttributeView` |

Không có bảo đảm **mọi filesystem đều hỗ trợ mọi view**. POSIX thường thấy trên các hệ thống kiểu Unix; DOS/ACL/user-defined có thể xuất hiện ở những provider khác, nhưng phải **kiểm tra tại runtime** thay vì suy ra chắc chắn chỉ từ tên hệ điều hành.

## 2. Lấy `FileStore` của đường dẫn

Dùng `Files.getFileStore(path)` khi bạn quan tâm nơi sẽ đọc/ghi dữ liệu:

```java
Path dir = Path.of("data").toAbsolutePath();
FileStore store = Files.getFileStore(dir);
```

Đây là **mã minh họa**, cần import `Path`, `Files`, `FileStore`, và `data` phải là vị trí mà provider xác định được store theo contract. Nếu đường dẫn không tồn tại hoặc không thể tra cứu trong provider, thao tác có thể ném `IOException`; không nên tự giả định mọi path tưởng tượng đều tra ra dung lượng.

Một số phương thức của `FileStore`:

- `name()`: tên được provider báo, **có thể là chuỗi rỗng**.
- `type()`: loại store/filesystem do provider báo, ví dụ một tên định dạng filesystem; không phải mã phân loại chuẩn duy nhất giữa các provider.
- `isReadOnly()`: provider xem store có read-only hay không; **không đủ chứng minh** bạn có quyền ghi vào một file cụ thể.
- `getTotalSpace()`: tổng dung lượng của store.
- `getUnallocatedSpace()`: phần không được cấp phát ở store.
- `getUsableSpace()`: phần dung lượng **có thể dùng bởi Java VM này** theo cách provider đánh giá.
- `supportsFileAttributeView(...)`: kiểm tra khả năng hỗ trợ attribute view ở store.
- `getAttribute(String)`: thuộc tính **store-specific** theo tên được provider hỗ trợ; không phải API tiêu chuẩn chung cho mọi loại thuộc tính.

### Đừng coi `name()` là ID duy nhất

Một chương trình có thể chạy trong container, môi trường ảo, network mount hoặc provider với tên store rỗng. Không nên dùng `store.name()` làm khóa duy nhất để nhận diện file hoặc quyết định hai đường dẫn có cùng thiết bị vật lý.

Trong ví dụ kiểm thử bên dưới trên một filesystem NTFS, `name()` đã trả chuỗi **rỗng**. Do đó, chương trình thực tế phải chấp nhận trường hợp tên hiển thị không có thông tin hữu ích.

## 3. Ba đại lượng dung lượng: total, unallocated, usable

| Phương thức | Đơn vị | Ý nghĩa thực hành |
|---|---|---|
| `getTotalSpace()` | Byte | Dung lượng tổng mà store báo |
| `getUnallocatedSpace()` | Byte | Dung lượng chưa được phân bổ/cấp phát trong store |
| `getUsableSpace()` | Byte | Dung lượng store dự kiến khả dụng cho **Java VM/caller hiện tại** |

Sự khác nhau quan trọng nhất là giữa **unallocated** và **usable**:

- Một filesystem có thể còn nhiều dung lượng trống vật lý nhưng một người dùng/process bị giới hạn quota hoặc phần dung lượng được dành riêng cho hệ thống.
- Khi đó dung lượng **usable** có thể thấp hơn dung lượng **unallocated**.
- Ở một số hệ thống, hai số này bằng nhau; đây chỉ là trạng thái của provider tại thời điểm gọi, không phải quy luật chung.
- Con số được đo trên **store**, không phải kích thước của một file.
- Các số liệu **có thể thay đổi ngay sau khi đọc** vì tiến trình khác ghi/xóa dữ liệu, quota thay đổi, filesystem được mount lại hoặc do đặc tính provider.

Không nên lấy `getUnallocatedSpace()` thay cho `getUsableSpace()` để kết luận rằng ứng dụng **có quyền sử dụng** toàn bộ dung lượng trống. Ngược lại, `getUsableSpace()` cũng **không đảm bảo** một file mới có thể được tạo thành công: còn có ACL, permission, inode/entry limits, path lỗi, quota không được phản ánh tức thì, giới hạn mạng và nhiều nguyên nhân khác.

### Byte, KiB, MiB, GiB khác với kB, MB, GB

Các API dung lượng trả về **byte** dưới dạng `long`. Để trình bày theo đơn vị nhị phân:

- `1 KiB = 1.024 byte`.
- `1 MiB = 1.024 KiB = 1.048.576 byte`.
- `1 GiB = 1.024 MiB = 1.073.741.824 byte`.

Đơn vị thập phân dùng cho một số nhãn của nhà sản xuất lưu trữ:

- `1 kB = 1.000 byte`.
- `1 MB = 1.000.000 byte`.
- `1 GB = 1.000.000.000 byte`.

Hai quy ước không nên bị ghi nhãn lẫn nhau. Ví dụ phép chia `bytes / (1024.0 * 1024.0 * 1024.0)` tạo **GiB**, không phải GB thập phân.

### Đừng ép dữ liệu dung lượng về `int`

`long` là lựa chọn phù hợp cho thông tin byte lớn. Một store dung lượng hàng trăm GiB dễ vượt quá giới hạn `int` 32 bit. Cũng cần chú ý tràn số khi tính `requiredBytes + overhead`: với đầu vào không đáng tin, hãy kiểm tra trước phép cộng hoặc dùng so sánh giới hạn phù hợp.

## 4. Ví dụ 1 — truy vấn dung lượng của một thư mục thật

Tạo tệp `FileStoreCapacityDemo.java` (Java 17+):

```java
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class FileStoreCapacityDemo {
    static double toGiB(long bytes) {
        return bytes / (1024.0 * 1024.0 * 1024.0);
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("store-capacity-");
        try {
            FileStore store = Files.getFileStore(dir);
            long total = store.getTotalSpace();
            long unallocated = store.getUnallocatedSpace();
            long usable = store.getUsableSpace();
            System.out.println("store name=" + store.name());
            System.out.println("store type=" + store.type());
            System.out.println("total bytes=" + total);
            System.out.println("unallocated bytes=" + unallocated);
            System.out.println("usable bytes=" + usable);
            System.out.println("nonnegative byte counts="
                    + (total >= 0 && unallocated >= 0 && usable >= 0));
            System.out.printf(Locale.ROOT, "one GiB formatted=%.2f%n",
                    toGiB(1L << 30));
        } finally {
            Files.deleteIfExists(dir);
        }
    }
}
```

Biên dịch, chạy:

```bash
javac -encoding UTF-8 --release 17 FileStoreCapacityDemo.java
java FileStoreCapacityDemo
```

Kết quả **quan sát được trong một lần chạy Java 17 trên NTFS**:

```text
store name=
store type=NTFS
total bytes=499024654336
unallocated bytes=203543601152
usable bytes=203543601152
nonnegative byte counts=true
one GiB formatted=1.00
```

**Không coi các con số trên là stdout cố định trên mọi máy.** `name()`, `type()`, `total`, `unallocated` và `usable` là dữ liệu thực của filesystem nơi ví dụ chạy; chúng có thể khác khi đổi máy, ổ đĩa, quota hoặc đơn giản chỉ một lần chạy sau. Đây là lý do code đọc giá trị thực thay vì hard-code ví dụ dung lượng.

**Cơ chế:**

1. `Files.createTempDirectory` tạo thư mục riêng trên vị trí tạm mặc định, tránh phụ thuộc file người dùng.
2. `Files.getFileStore(dir)` lấy store tương ứng với đường dẫn thư mục vừa tạo.
3. Ba phương thức dung lượng trả `long` và đều có đơn vị **byte**.
4. `toGiB` minh họa phép đổi đơn vị không dùng phép chia số nguyên, tránh mất phần lẻ do `long / long`.
5. Khối `finally` dọn thư mục tạm sau khi query. Nếu filesystem cấm thao tác, lời gọi liên quan có thể ném `IOException` và cần xử lý ở ứng dụng thật.

Không nên kiểm thử theo kiểu assert chính xác một con số dung lượng trên máy test. Những test đáng tin hơn xác nhận kiểu dữ liệu, cơ chế lỗi và phép quy đổi bằng **số giả lập cố định**, còn test tích hợp chỉ kiểm tra thuộc tính với giả định phù hợp môi trường.

## 5. Phân biệt `FileSystem.supportedFileAttributeViews()` và `FileStore.supportsFileAttributeView(...)`

Hai API có tên gần giống nhưng **khác phạm vi**.

### 5.1 Tập view ở tầng `FileSystem`

`FileSystem.supportedFileAttributeViews()` trả một `Set<String>` tên view mà filesystem/provider cho biết hỗ trợ:

```java
Set<String> views =
        FileSystems.getDefault().supportedFileAttributeViews();
```

Đây là **đoạn minh họa**, cần import `java.nio.file.FileSystems` và `java.util.Set`.

Tên có thể là `"basic"`, `"posix"`, `"dos"`, `"acl"`, `"owner"` hoặc `"user"`, tùy provider. **Không được giả định danh sách luôn giống nhau** giữa Windows, Linux, container, network filesystem và provider tùy chỉnh.

`supportedFileAttributeViews()` mang ý nghĩa **provider/filesystem-level**. Nếu provider quản lý nhiều file store, một view có trong tập có thể **không được hỗ trợ tại mọi store hoặc mọi đường dẫn**. Nó cũng không đồng nghĩa bạn đang có **quyền đọc/ghi** toàn bộ metadata ở một file cụ thể.

### 5.2 Kiểm tra view trên một `FileStore`

`FileStore` có hai overload quan trọng:

```java
boolean byName = store.supportsFileAttributeView("posix");
boolean byClass =
        store.supportsFileAttributeView(PosixFileAttributeView.class);
```

Đây là **đoạn minh họa**, cần `store` là `FileStore` và import `PosixFileAttributeView`.

- Dạng `String` nhận **tên view** được provider định nghĩa; với các view chuẩn, dùng `"basic"`, `"posix"`, `"dos"`, v.v.
- Dạng `Class<? extends FileAttributeView>` nhận lớp interface view.
- Một store hỗ trợ `basic` không kéo theo hỗ trợ `posix` hay `acl`.
- Nếu view không được hỗ trợ, kết quả thường là `false` theo contract; cũng cần xử lý các lỗi liên quan đến đối số/provider theo tài liệu cụ thể.

**Đừng nhầm `supportedFileAttributeViews()` (số nhiều, thuộc `FileSystem`) với `supportsFileAttributeView(...)` (hỏi `FileStore` riêng lẻ).** `FileStore` không có cùng phương thức trả `Set<String>` với tên như ở `FileSystem`.

### 5.3 Kiểm tra view tại một `Path` và đọc thuộc tính

Để truy cập view của file cụ thể:

```java
BasicFileAttributeView view = Files.getFileAttributeView(
        path, BasicFileAttributeView.class);
```

Đây là **đoạn minh họa**, cần import `Files` và `BasicFileAttributeView`. Giá trị có thể là `null` nếu view không được hỗ trợ. Nếu có view, lời gọi như `view.readAttributes()` còn có thể ném `IOException` do quyền truy cập, file biến mất hoặc lỗi filesystem.

Đọc một nhóm thuộc tính:

```java
BasicFileAttributes attrs = Files.readAttributes(
        path, BasicFileAttributes.class);
long fileSizeBytes = attrs.size();
boolean isDirectory = attrs.isDirectory();
```

Đây là **mã minh họa**, cần import `BasicFileAttributes`, `Files` và xử lý `IOException`. `attrs.size()` là **kích thước của file/entry**, không phải tổng dung lượng của `FileStore`. Hãy phân biệt:

```text
FileStore.getTotalSpace()      -> tổng dung lượng của store (byte)
FileStore.getUsableSpace()     -> dung lượng dự kiến khả dụng cho JVM (byte)
BasicFileAttributes.size()     -> kích thước một mục/path (byte)
```

## 6. Ví dụ 2 — ba cấp kiểm tra attribute view

Tạo tệp `FileStoreViewsDemo.java`:

```java
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Set;

public class FileStoreViewsDemo {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("store-views-");
        try {
            Set<String> providerViews =
                    FileSystems.getDefault().supportedFileAttributeViews();
            FileStore store = Files.getFileStore(dir);
            System.out.println("provider basic="
                    + providerViews.contains("basic"));
            System.out.println("store basic by name="
                    + store.supportsFileAttributeView("basic"));
            System.out.println("store basic by class="
                    + store.supportsFileAttributeView(BasicFileAttributeView.class));
            BasicFileAttributes attrs =
                    Files.readAttributes(dir, BasicFileAttributes.class);
            System.out.println("directory via basic=" + attrs.isDirectory());
            System.out.println("provider posix="
                    + providerViews.contains("posix"));
            System.out.println("store posix="
                    + store.supportsFileAttributeView(PosixFileAttributeView.class));
            System.out.println("store dos="
                    + store.supportsFileAttributeView(DosFileAttributeView.class));
        } finally {
            Files.deleteIfExists(dir);
        }
    }
}
```

```bash
javac -encoding UTF-8 --release 17 FileStoreViewsDemo.java
java FileStoreViewsDemo
```

Đầu ra **đã kiểm thử trên một môi trường Java 17 dùng NTFS**:

```text
provider basic=true
store basic by name=true
store basic by class=true
directory via basic=true
provider posix=false
store posix=false
store dos=true
```

**Các dòng `posix`/`dos` có thể khác trên Linux, macOS, network store hoặc provider khác.** Đây là kết quả môi trường, **không phải kỳ vọng phổ quát**. Ngay cả trên hai file store cùng hệ điều hành, tập view hỗ trợ có thể khác.

**Ý nghĩa của từng nhóm kiểm tra:**

1. `providerViews.contains("basic")` hỏi tầng filesystem mặc định có khai báo view `basic` không.
2. Hai cách `store.supportsFileAttributeView(...)` hỏi **store nơi thư mục tạm đang nằm** có hỗ trợ `basic` không.
3. `Files.readAttributes(dir, BasicFileAttributes.class)` thực sự truy cập metadata của **đường dẫn** và biết đó là directory.
4. `PosixFileAttributeView` và `DosFileAttributeView` được hỏi riêng vì support là đặc tính provider/store, không phải một lựa chọn mà ứng dụng tự bật.
5. Tất cả truy vấn diễn ra trên môi trường hiện tại. Để chương trình không bị gắn cứng vào Windows/Linux, hãy **nhánh hành vi theo khả năng runtime**.

**Lưu ý:** code chỉ *hỏi* có hỗ trợ, không hề thay đổi permission, hidden flag hoặc ACL. Nếu chức năng chính yêu cầu ghi POSIX permissions, kiểm tra support mới là bước đầu; vẫn cần xử lý quyền truy cập và lỗi thực tế ở lúc gọi `Files.setPosixFilePermissions` hoặc phương thức của view.

## 7. `basic`, `posix`, `dos`, `acl` và `user`: chọn view đúng bài toán

### View `basic`

`BasicFileAttributes` cho biết:

- `size()`: kích thước file/entry, theo byte.
- `lastModifiedTime()`, `lastAccessTime()`, `creationTime()`: các mốc thời gian khi provider có hỗ trợ thích hợp.
- `isRegularFile()`, `isDirectory()`, `isSymbolicLink()`, `isOther()`: loại mục.
- `fileKey()`: khóa nhận dạng do provider trả, **có thể `null`**.

Đây là nhóm metadata nền tảng, thường dễ dùng nhất khi chỉ cần xem loại file hoặc kích thước. Không cần gọi `Files.getFileStore` chỉ để đọc `BasicFileAttributes` của một đường dẫn.

### View `posix`

`PosixFileAttributeView` và `PosixFileAttributes` cung cấp mô hình permissions, owner/group kiểu POSIX. Trên provider hỗ trợ, có thể dùng:

```java
Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
```

Đây là **mã minh họa**; cần import `Set`, `PosixFilePermission` và `Files`; phương thức có thể ném `IOException` hoặc `UnsupportedOperationException` nếu filesystem không hỗ trợ.

**Không viết chương trình luôn gọi `Files.setPosixFilePermissions` mà không kiểm tra**, vì API đó có thể thất bại trên môi trường chỉ hỗ trợ DOS/ACL. Ngoài ra, permission bits POSIX không phản ánh đầy đủ mọi mô hình bảo mật phức tạp.

### View `dos`

`DosFileAttributeView` hỗ trợ các thuộc tính như **read-only**, **hidden**, **system**, **archive** theo provider. Chúng **khác** với `FileStore.isReadOnly()`:

- `FileStore.isReadOnly()` nói về khả năng ghi của **store** nói chung.
- `DosFileAttributes.isReadOnly()` nói về thuộc tính **một file/entry**.
- Một store báo có thể ghi không đồng nghĩa mọi file đều ghi được, vì còn ACL/permission/lock.

### View `acl` và `owner`

`AclFileAttributeView` dành cho access-control list và `FileOwnerAttributeView` cho owner khi provider hỗ trợ. Mô hình phân quyền ACL có thể phức tạp hơn mô hình POSIX 9 bit. Tránh tự chuyển ACL thành chuỗi `rwx` rồi tuyên bố đã giữ nguyên chính sách bảo mật.

### View `user`

`UserDefinedFileAttributeView` cho phép truy cập một số **extended attributes** do ứng dụng đặt, tùy filesystem và provider. Không nên xem nó như một kho key-value bền vững, kích thước không giới hạn hoặc có sẵn ở mọi môi trường. Nếu dữ liệu metadata là phần thiết yếu của ứng dụng, cần có lựa chọn fallback/di chuyển dữ liệu và test phù hợp.

## 8. Dùng khả năng được hỗ trợ để chọn đường xử lý

Một quy trình portable cho chức năng **yêu cầu POSIX permissions** có thể là:

```java
FileStore store = Files.getFileStore(path);
if (!store.supportsFileAttributeView(PosixFileAttributeView.class)) {
    throw new UnsupportedOperationException(
            "POSIX file permissions are not available here");
}
Set<PosixFilePermission> permissions =
        Files.getPosixFilePermissions(path);
```

Đây là **đoạn minh họa**, cần import `FileStore`, `Files`, `PosixFileAttributeView`, `PosixFilePermission`, `Set`, biến `path` và xử lý `IOException`. Nếu view không có, lỗi được trình bày rõ thay vì bí mật thử POSIX rồi bỏ qua thất bại.

Tuy nhiên, **support-check không phải quyền truy cập**: ngay sau khi trả `true`, file vẫn có thể bị xóa, đổi quyền, hoặc operation tiếp theo bị từ chối. **Đừng sử dụng kết quả support-check như cam kết thao tác chắc chắn thành công**.

Nếu POSIX chỉ là tính năng tùy chọn, ứng dụng có thể chọn policy thay thế thích hợp. Ví dụ một công cụ sao lưu có thể ghi lại metadata nào đã có/không có, thay vì âm thầm bỏ qua quyền khi mục tiêu yêu cầu bảo toàn security metadata.

## 9. Khi nào cần lấy danh sách `FileStore` của một filesystem?

Ngoài `Files.getFileStore(path)`, Java cho phép lấy **các FileStore mà FileSystem cung cấp**:

```java
for (FileStore store : FileSystems.getDefault().getFileStores()) {
    System.out.println(store.name() + " / " + store.type());
}
```

Đây là **mã minh họa**, cần import `FileStore` và `FileSystems`. Số store được liệt kê, thứ tự, tên và các store có thể truy cập đều phụ thuộc provider/môi trường.

**Chọn đúng API theo câu hỏi:**

- “File đầu ra ở thư mục `/data/export` sẽ được ghi lên store nào?” → `Files.getFileStore(exportDirectory)`.
- “Provider mặc định cho phép liệt kê những store nào?” → `FileSystems.getDefault().getFileStores()`.
- “Provider/filesystem hỗ trợ những view attribute nào?” → `fileSystem.supportedFileAttributeViews()`.
- “Store đang chứa đường dẫn này có hỗ trợ POSIX không?” → `store.supportsFileAttributeView("posix")`.
- “File cụ thể có thuộc tính hidden/permissions gì?” → `Files.readAttributes` hoặc `Files.getFileAttributeView` với view thích hợp.

Không duyệt `getFileStores()` rồi tự chọn store có tên gần giống với đường dẫn một cách suy đoán. `Files.getFileStore(path)` là câu hỏi đúng khi đã có một đường dẫn đích.

## 10. Kiểm tra dung lượng trước khi ghi: có ích nhưng không phải bảo đảm

Một ứng dụng cần tạo file dung lượng dự kiến 4 GiB có thể muốn cảnh báo nếu store chỉ còn ít byte usable:

```java
FileStore store = Files.getFileStore(destinationDirectory);
long usableBytes = store.getUsableSpace();
if (usableBytes < expectedBytes) {
    throw new IOException("Insufficient estimated usable space");
}
```

Đây là **mã minh họa**, cần biến `destinationDirectory`, `expectedBytes` và xử lý/khai báo `IOException`. `expectedBytes` phải là **số byte dự kiến của dữ liệu thực sự sẽ ghi**, không phải số ký tự `String.length()` khi mã hóa UTF-8.

Nhưng kiểm tra này chỉ là **preflight advisory**:

1. Giữa lúc đọc `usableBytes` và lúc ghi, tiến trình khác có thể dùng hết dung lượng (**TOCTOU**).
2. Dung lượng cho JVM hiện tại không phản ánh đầy đủ quota theo file/user/container hoặc mọi giới hạn hệ thống trong mọi provider.
3. Ghi file có thể cần metadata, directory entry, block allocation hoặc journal overhead.
4. File có thể nằm trên đường dẫn mount khác sau khi chuyển hoặc do symlink/mount thay đổi.
5. Quyền truy cập, permission/ACL, file lock và đường dẫn cha có thể khiến thao tác thất bại dù usable rất lớn.
6. Ghi nén/thưa/sparse/compression có thể làm dung lượng vật lý khác kích thước logic; dự đoán từ payload không luôn chính xác.

**Vì vậy:** dùng `getUsableSpace` để cảnh báo/lập kế hoạch sơ bộ, nhưng vẫn **bắt và xử lý `IOException` trong lúc ghi**, xác nhận kích thước/checksum khi cần, và có chiến lược rollback/cleanup cho file không hoàn chỉnh.

### Khi tính byte cần chú ý encoding

`"Xin chào 😊".length()` là số UTF-16 code unit của chuỗi Java, trong khi dữ liệu UTF-8 có thể dài hơn:

```java
byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
long expectedBytes = encoded.length;
```

Đây là **mã minh họa**, cần có `String text` và import `StandardCharsets`. Với stream lớn, không nên tạo `byte[]` chứa toàn bộ payload chỉ để biết độ dài; chọn cách tính/ước lượng byte của format hoặc đếm trong lúc serialize khi kiến trúc cho phép.

## 11. Ví dụ 3 — kiểm tra ước lượng bằng phép so sánh an toàn

Tạo tệp `SpacePreflightDemo.java`:

```java
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;

public class SpacePreflightDemo {
    static boolean enoughForEstimate(long estimatedBytes, long usableBytes) {
        if (estimatedBytes < 0 || usableBytes < 0) {
            throw new IllegalArgumentException("Negative size");
        }
        return usableBytes >= estimatedBytes;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("zero estimate="
                + enoughForEstimate(0, 0));
        System.out.println("estimate fits="
                + enoughForEstimate(1024, 4096));
        System.out.println("estimate exceeds="
                + enoughForEstimate(4097, 4096));

        Path temp = Files.createTempDirectory("preflight-space-");
        try {
            FileStore store = Files.getFileStore(temp);
            long usable = store.getUsableSpace();
            System.out.println("usable nonnegative=" + (usable >= 0));
            System.out.println("zero bytes within measured usable="
                    + enoughForEstimate(0, usable));
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
```

```bash
javac -encoding UTF-8 --release 17 SpacePreflightDemo.java
java SpacePreflightDemo
```

Kết quả đã kiểm thử trong môi trường có truy vấn dung lượng thành công:

```text
zero estimate=true
estimate fits=true
estimate exceeds=false
usable nonnegative=true
zero bytes within measured usable=true
```

Các phép kiểm thử bằng số cụ thể chứng minh ý nghĩa của phương thức `enoughForEstimate`:

- Nếu dự kiến 1.024 byte và usable 4.096 byte, phép so sánh cho `true`.
- Nếu dự kiến 4.097 byte và usable 4.096 byte, kết quả `false`.
- Nếu dữ liệu đầu vào âm, phương thức ném `IllegalArgumentException` để tránh âm thầm chấp nhận một kích thước vô lý.
- Ví dụ đo trực tiếp `store.getUsableSpace()` trên thư mục tạm do chương trình tạo, không dùng một con số cấu hình giả làm dung lượng thực.

**Quan trọng:** Giá trị `true` có nghĩa *ước lượng không vượt số usable đọc được tại thời điểm kiểm tra*, **không** có nghĩa “ghi chắc chắn thành công”. Trong ví dụ, việc kiểm tra **0 byte** gần như luôn khả dụng nhưng bản thân tạo file mới vẫn có thể thất bại bởi quyền, quota entry/inode, filesystem read-only hoặc lỗi I/O khác.

Nếu nghiệp vụ cần độ tin cậy cao, hãy kết hợp:
- xác định store của **thư mục đích thật**, không phải một ổ đĩa tình cờ khác;
- ước lượng byte và overhead hợp lý;
- ghi file tạm, theo dõi byte đã ghi;
- bắt lỗi ghi, dọn file tạm khi thất bại;
- publish/commit chỉ sau khi ghi và validate hoàn chỉnh.

## 12. `FileStore.isReadOnly()` khác permission của một file

`store.isReadOnly()` trả trạng thái read-only của **file store** theo provider. Đây là tín hiệu có ích nếu bạn cân nhắc thao tác ghi, nhưng không thay thế permission/ACL của đường dẫn.

Một vài tình huống:

- Store báo `isReadOnly() == true`: thao tác ghi lên đó thường không được phép; vẫn phải xem semantics và lỗi cụ thể của provider.
- Store báo `false`: bạn **vẫn có thể không có quyền ghi** vào thư mục vì ACL/POSIX permissions, owner, quota hoặc chính sách khác.
- Một file có DOS read-only flag không đồng nghĩa toàn store read-only.
- File đang được ứng dụng khác sử dụng/khóa có thể khiến ghi thất bại dù store và permissions về lý thuyết cho phép.

Một kiểm tra `Files.isWritable(path)` cũng là thông tin tại một thời điểm, **không bảo đảm lần ghi tiếp theo thành công**. Đặc biệt với một file chưa được tạo, muốn biết có thể tạo file trong directory hay không không thể chỉ dựa trên việc file chưa tồn tại.

## 13. `getAttribute(String)` của `FileStore` có phải API chuẩn cho mọi store?

`FileStore.getAttribute(String attribute)` cho phép lấy một thuộc tính do provider định nghĩa ở tầng **store**. Tên attribute và giá trị trả về **không có một bộ chung bắt buộc** cho mọi filesystem.

Đừng viết:

```java
Object x = store.getAttribute("someProviderKey");
```

rồi nghĩ mọi JVM/provider đều phải hỗ trợ key đó. Đây là **mã minh họa**, tên key chỉ mang ý nghĩa biểu trưng; ứng dụng cần đọc tài liệu provider cụ thể và xử lý `UnsupportedOperationException`/`IOException` khi không hỗ trợ.

Phân biệt:

- `FileStore.getAttribute(...)`: thuộc tính **store-specific**.
- `Files.getAttribute(path, "basic:size")`: thuộc tính **file/path-specific** theo cú pháp view:name của `Files`.
- `Files.readAttributes(path, BasicFileAttributes.class)`: đọc một nhóm metadata file kiểu mạnh.
- `FileStore.getUsableSpace()`: API chuẩn để truy vấn usable bytes; không cần đoán tên attribute provider.

Nếu mục tiêu là dung lượng, ưu tiên ba phương thức dung lượng chuẩn thay vì `getAttribute` không portable.

## 14. Đừng suy ra mọi file đều thuộc cùng một store

Trong một ứng dụng có nhiều mount, container volume, network share hoặc virtual filesystem:

- Hai đường dẫn có thể cùng `FileSystem` provider nhưng nằm trên **các `FileStore` khác nhau**.
- Một đường dẫn bên trong cây directory có thể đi qua mount point sang store khác.
- File tạm do `Files.createTempFile(...)` tạo ở system temp có thể không nằm cùng store với file output.
- Thao tác move qua store có thể không hỗ trợ atomic move; hành vi copy/move và metadata có thể bị giới hạn theo provider.

Do đó, nếu cần kiểm tra dung lượng cho một file dự kiến ghi ở `/data/export`, hãy xác định store liên quan đến **vị trí đích hợp lệ**, thường qua thư mục đích tồn tại. Không lấy dung lượng của `Path.of(".")` rồi áp dụng đại cho mọi nơi.

Để tránh race khi path chưa tồn tại, chương trình có thể xác định **thư mục cha tồn tại** làm điểm tham chiếu cho preflight; tuy nhiên, kết quả vẫn chỉ là ước lượng và cần xử lý filesystem thay đổi sau đó.

## 15. Quyền, symbolic link và trạng thái đường dẫn

`Files.getFileStore(path)` và API attribute có thể chịu tác động của:

- Directory traversal permissions.
- Symbolic link và cách giải quyết đích theo provider.
- File được xóa hoặc thay thế giữa hai phép truy vấn.
- Thiết bị bị tháo/mount lại.
- Network filesystem mất kết nối.
- Một đường dẫn tương đối được diễn giải trên working directory khác nhau.

**Không dùng kết quả `normalize()` như bằng chứng đường dẫn đã được giải quyết trên filesystem.** `Path.normalize()` chỉ xử lý dấu `.` và `..` theo cú pháp, không xác thực symlink hay quyền truy cập.

Với input đường dẫn do người dùng cung cấp, cần xác thực root, quyền truy cập, symbolic link/race theo threat model. Sau khi chọn một `FileStore`, vẫn phải thực hiện thao tác thật và **bắt exception**, không tin rằng metadata đọc trước là cam kết trạng thái tương lai.

Khi truy vấn `Files.getFileAttributeView(path, clazz, LinkOption.NOFOLLOW_LINKS)`, điều đó có thể giúp hỏi attribute của **chính symlink** thay vì target khi provider hỗ trợ. Không giả định mọi view hay thao tác metadata đều hỗ trợ `NOFOLLOW_LINKS` và quyền giống nhau.

## 16. Dùng `supportedFileAttributeViews` để xây ứng dụng portable

Một chiến lược hợp lý cho công cụ quản lý file đa hệ điều hành:

1. Xác định đường dẫn và filesystem provider mà dữ liệu thực sự sử dụng.
2. Hỏi `path.getFileSystem().supportedFileAttributeViews()` để có thông tin **mức filesystem/provider**.
3. Nếu chức năng làm việc với một store cụ thể, hỏi `Files.getFileStore(path)` và `store.supportsFileAttributeView(...)`.
4. Trước thao tác thuộc tính của file, chọn view phù hợp và truy cập bằng `Files.getFileAttributeView` / `Files.readAttributes`.
5. Nếu view không hỗ trợ, từ chối chức năng yêu cầu bắt buộc hoặc dùng fallback **có chủ đích**, không âm thầm giả lập thuộc tính.
6. Dù support-check báo `true`, vẫn xử lý `IOException`, `UnsupportedOperationException` và trường hợp quyền truy cập không đủ.
7. Viết test ít nhất với cấu hình filesystem thực tế cần hỗ trợ, nhất là POSIX/DOS/ACL/ZIP/network mount.
8. Với metadata mang ý nghĩa bảo mật (permissions, ACL), không âm thầm bỏ chúng khi backup/migrate nếu contract yêu cầu giữ nguyên.

### Ví dụ quyết định: tính năng “đặt hidden flag”

Nếu `FileStore` không hỗ trợ DOS attribute view, không thể mặc nhiên gọi `Files.setAttribute(path, "dos:hidden", true)` rồi mong chạy đa nền tảng. Có thể chọn tính năng ẩn theo UI của ứng dụng thay vì sửa attribute file, hoặc báo rõ “không hỗ trợ trên store này”.

Tương tự, đổi owner hoặc chmod theo POSIX cần xác minh view và quyền phù hợp. Không dùng `FileStore` support check thay thế kiểm soát bảo mật thực sự.

## 17. Những sai lầm thường gặp

1. **Nhầm `FileStore` với một file.** `FileStore.getTotalSpace()` là dung lượng store; `BasicFileAttributes.size()` là kích thước một file.
2. **Dùng `getTotalSpace()` để quyết định đủ chỗ ghi.** Total bao gồm cả dung lượng đã dùng; nên tham khảo `getUsableSpace()`.
3. **Coi `getUnallocatedSpace()` luôn bằng `getUsableSpace()`.** Quota/reserved space có thể khiến hai số khác nhau.
4. **Bỏ đơn vị byte.** Các API trả byte, không phải KB/MB/GiB trực tiếp.
5. **Dùng `int` lưu dung lượng.** Có nguy cơ tràn số; dùng `long`.
6. **Dùng `String.length()` để đo byte UTF-8.** Chuỗi Java có UTF-16 code unit; byte mã hóa có thể khác.
7. **Tin rằng usable >= size là ghi chắc thành công.** Dung lượng thay đổi, còn permission/ACL/inode/quota/overhead và lỗi I/O.
8. **Đo dung lượng trên temp rồi áp cho output ở store khác.** Cần tham chiếu vị trí đích thích hợp.
9. **Coi `FileStore.name()` luôn có giá trị hoặc duy nhất.** Provider có thể trả chuỗi rỗng.
10. **Dựa vào `FileStore.type()` như tiêu chuẩn cross-platform.** Đây là chuỗi theo provider, có thể không portable.
11. **Nhầm `FileSystem.supportedFileAttributeViews()` với `FileStore.supportsFileAttributeView(...)`.** Tầng provider vs store cụ thể.
12. **Suy rằng mọi view trong set đều hoạt động ở mọi path.** Store, quyền và trạng thái path vẫn phải được xem xét.
13. **Dùng POSIX API trên hệ thống chỉ có DOS view.** Có thể bị unsupported; cần kiểm tra và có policy.
14. **Tưởng DOS read-only flag giống store read-only.** Một cái là thuộc tính file, một cái là khả năng lưu trữ.
15. **Cho rằng view supported đồng nghĩa có quyền sửa metadata.** Support không phải authorization.
16. **Bỏ qua exception của thao tác đọc metadata.** Path biến mất, quyền đổi hoặc network lỗi vẫn có thể xảy ra.
17. **Lạm dụng `getAttribute("...")` với key provider-specific.** Không có danh sách key chung bắt buộc.
18. **Lấy danh sách `getFileStores()` rồi đoán theo tên.** Cần `Files.getFileStore(path)` khi quan tâm nơi chứa path.
19. **Không xét symlink/mount/quota thay đổi.** Kết quả metadata có thể lỗi thời.
20. **Tưởng `isReadOnly() == false` bảo đảm ghi được.** Vẫn cần mở/ghi và xử lý lỗi thật.

## 18. Bảng chọn API theo câu hỏi

| Câu hỏi cần trả lời | API thích hợp | Lưu ý |
|---|---|---|
| Đường dẫn hiện tại thuộc store nào? | `Files.getFileStore(path)` | Có thể ném `IOException` |
| Có bao nhiêu byte tổng của store? | `store.getTotalSpace()` | Không phải dung lượng khả dụng |
| Có bao nhiêu byte chưa được cấp phát? | `store.getUnallocatedSpace()` | Có thể khác usable |
| JVM hiện có thể sử dụng khoảng bao nhiêu byte? | `store.getUsableSpace()` | Chỉ là snapshot/ước lượng |
| Store có ở chế độ read-only? | `store.isReadOnly()` | Không thay quyền file cụ thể |
| Filesystem provider hỗ trợ view tên nào? | `path.getFileSystem().supportedFileAttributeViews()` | Set tên view ở mức filesystem |
| Store đích hỗ trợ POSIX view không? | `store.supportsFileAttributeView("posix")` | Có thể khác store khác |
| Path cụ thể có metadata basic gì? | `Files.readAttributes(path, BasicFileAttributes.class)` | Lấy thuộc tính của file/entry |
| Có truy cập được một attribute view? | `Files.getFileAttributeView(path, Class)` | Có thể `null` hoặc lỗi khi dùng |
| Filesystem cho phép liệt kê store nào? | `fileSystem.getFileStores()` | Không phải phép ánh xạ path tự động |
| Một file cụ thể dài bao nhiêu byte? | `Files.size(path)` | Không phải dung lượng store |
| Có ghi file đích thành công không? | Mở/ghi/đóng, kiểm tra kết quả | Không thay bằng preflight metadata |

## 19. Câu hỏi tự luyện

**Câu 1.** `getTotalSpace()` và `getUsableSpace()` có cùng ý nghĩa không?  
**Đáp án:** không. Total là dung lượng tổng store; usable là dung lượng mà JVM có thể sử dụng theo provider tại thời điểm đo.

**Câu 2.** Vì sao không dùng `getUnallocatedSpace()` để bảo đảm ứng dụng có thể ghi hết số byte đó?  
**Đáp án:** quyền, quota, reserved space, filesystem overhead và điều kiện cạnh tranh có thể hạn chế byte thực tế có thể ghi.

**Câu 3.** `FileSystem.supportedFileAttributeViews()` khác `FileStore.supportsFileAttributeView("posix")` ở đâu?  
**Đáp án:** lời gọi đầu trả tập tên view ở tầng filesystem/provider; lời gọi sau hỏi view `posix` có được hỗ trợ trên một store cụ thể hay không.

**Câu 4.** Một store báo hỗ trợ `posix`. Có chắc được phép đổi permission trên mọi file của store không?  
**Đáp án:** không. Support không đồng nghĩa quyền thao tác; file có thể bị khóa, xóa hoặc thay quyền.

**Câu 5.** Trên hệ thống NTFS ở ví dụ, `name()` là chuỗi rỗng. Có phải `getFileStore` lỗi không?  
**Đáp án:** không. `name()` là tên do provider cung cấp và có thể rỗng, trong khi các phương thức khác vẫn trả thông tin bình thường.

**Câu 6.** Muốn biết kích thước của `report.txt`, gọi `store.getTotalSpace()` có đúng không?  
**Đáp án:** không; dùng `Files.size(reportPath)` hoặc `BasicFileAttributes.size()`.

**Câu 7.** Preflight thấy `usableBytes >= expectedBytes`. Có thể bỏ qua `IOException` khi ghi không?  
**Đáp án:** không. Đây chỉ là kiểm tra mang tính tư vấn tại thời điểm đo; thao tác ghi vẫn có thể thất bại.

**Câu 8.** Một ứng dụng muốn hiển thị dung lượng theo GiB; nên chia cho 1.000.000.000 hay 1.073.741.824?  
**Đáp án:** GiB dùng 1.073.741.824; GB thập phân mới dùng 1.000.000.000.

**Câu 9.** Nếu muốn kiểm tra DOS hidden của file, chỉ cần xem `store.isReadOnly()` không?  
**Đáp án:** không. Đó là thuộc tính khác; dùng `DosFileAttributeView`/`DosFileAttributes` theo khả năng hỗ trợ.

**Câu 10.** Tại sao một `Path` thuộc cùng default filesystem với path khác chưa chắc nằm trên cùng `FileStore`?  
**Đáp án:** filesystem/provider có thể bao gồm nhiều volume, mount, network share hoặc store logic khác nhau.

## Kết luận

**`FileStore`** là nơi Java hỏi dung lượng và thuộc tính cấp store: `getTotalSpace`, `getUnallocatedSpace`, `getUsableSpace`, `isReadOnly` và `supportsFileAttributeView`. **`FileSystem.supportedFileAttributeViews()`** lại là thông tin hỗ trợ thuộc tính ở mức filesystem/provider. Để đọc thuộc tính một file thật, dùng **`Files.readAttributes` / `Files.getFileAttributeView`** với view thích hợp.

Khi cần quyết định có đủ chỗ ghi file không, ưu tiên **`getUsableSpace`** hơn `getTotalSpace`/`getUnallocatedSpace`, nhưng luôn hiểu đó chỉ là **ước lượng thay đổi được**, không thay thế việc xử lý ngoại lệ, quota, permission và kiểm tra tính toàn vẹn sau ghi. Chọn attribute view **theo support ở môi trường chạy**, không hard-code giả định “Windows thì luôn DOS, Linux thì luôn POSIX” cho mọi provider/store.

### Tài liệu Java SE 17

- [`FileStore`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileStore.html)
- [`Files.getFileStore`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [`FileSystem.supportedFileAttributeViews`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileSystem.html)
- [`FileAttributeView`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/FileAttributeView.html)
- [`BasicFileAttributeView` / `BasicFileAttributes`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/BasicFileAttributeView.html)
- [`PosixFileAttributeView`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/PosixFileAttributeView.html)
- [`DosFileAttributeView`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/DosFileAttributeView.html)
- [`AclFileAttributeView`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/AclFileAttributeView.html)
- [`UserDefinedFileAttributeView`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/UserDefinedFileAttributeView.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-attributes-and-permissions-011">probeContentType và giới hạn suy đoán MIME type</a>

<details>
<summary>Xem chi tiết</summary>

# `Files.probeContentType(Path)` và giới hạn suy đoán MIME type

## 1. Mục đích và ý nghĩa của MIME type

**MIME type** (*media type*) mô tả kiểu nội dung theo cấu trúc `type/subtype`. Ví dụ: `text/plain` (văn bản thuần), `text/html` (HTML), `image/png` (ảnh PNG), `image/jpeg` (ảnh JPEG), `application/pdf` (PDF), `application/zip` (ZIP). `application/octet-stream` thường được ứng dụng dùng cho dữ liệu nhị phân chưa biết loại cụ thể.

Trong Java NIO.2, `java.nio.file.Files.probeContentType(Path)` trả về **loại nội dung mà môi trường có thể suy đoán** cho một đường dẫn. Nó hữu ích để hiển thị nhãn tệp, hỗ trợ phân loại ban đầu hoặc lựa chọn `Content-Type` khi đã có chính sách xác minh độc lập.

Điểm cần ghi nhớ: **suy đoán MIME không có nghĩa là xác minh định dạng**. MIME type khác với đuôi tệp (`.png`, `.txt`), bộ mã hóa văn bản (UTF-8, UTF-16) và quyền truy cập tệp. Kết quả dò MIME cũng không tự chứng minh tệp tồn tại hay an toàn.

## 2. Hợp đồng API

```java
public static String probeContentType(Path path) throws IOException
```

- **Đối số `path`:** đường dẫn cần dò loại nội dung.
- **Giá trị trả về:** chuỗi MIME, ví dụ `"text/plain"`, hoặc **`null` khi không xác định được**.
- **`IOException`:** có thể xảy ra nếu bộ phát hiện cần truy cập tệp hoặc tài nguyên hệ thống và gặp lỗi I/O.
- **`NullPointerException`:** phát sinh khi truyền `null` làm đường dẫn.
- Phương thức **không hứa** đọc toàn bộ tệp, kiểm tra chữ ký nhị phân hay xác minh tệp hợp lệ.

Theo hợp đồng Java SE, cách dò là *implementation-specific*: bộ dò có thể dùng phần mở rộng, tên tệp, thuộc tính, dữ liệu của hệ điều hành hoặc byte nội dung. Java không quy định một cách dò duy nhất và không bảo đảm MIME cố định trên mọi máy.

**Ví dụ:** Một tệp chứa văn bản nhưng được đặt tên `photo.png` vẫn có thể được bộ dò dựa vào đuôi tên trả về `image/png`. Ngược lại, một tệp hợp lệ nhưng có đuôi lạ có thể trả về `null`.

## 3. Cơ chế phát hiện và yếu tố môi trường

Java có SPI `java.nio.file.spi.FileTypeDetector` để cung cấp bộ phát hiện loại tệp. Các triển khai được cài đặt có thể được nạp qua cơ chế provider/`ServiceLoader`; chúng được thử trước bộ dò mặc định của hệ thống. Thứ tự thử giữa nhiều bộ dò được cài đặt là chi tiết triển khai, vì thế không nên thiết kế ứng dụng phụ thuộc thứ tự ấy.

Mỗi bộ dò tự quyết định cách suy đoán. Có bộ dò dựa trên ánh xạ đuôi tệp của hệ điều hành, có bộ dò kiểm tra dữ liệu khác. Kết quả chịu ảnh hưởng của JDK, hệ điều hành, bộ dò được cài và cấu hình môi trường.

| Câu hỏi | API hoặc cách xử lý phù hợp |
| --- | --- |
| Hệ thống suy đoán MIME nào? | `Files.probeContentType(path)` |
| Đường dẫn có phải tệp thường tại thời điểm kiểm tra? | `Files.isRegularFile(path)` |
| Hiện tại có thể đọc tệp không? | `Files.isReadable(path)` hoặc thử mở tệp |
| Kích thước tệp là bao nhiêu? | `Files.size(path)` |
| Byte đầu có giống chữ ký định dạng? | Đọc các byte thực và so sánh với đặc tả |
| Tệp có được ứng dụng chấp nhận không? | Chính sách loại tệp + parser/decoder + giới hạn tài nguyên |

`Files.isReadable` và `Files.isRegularFile` cũng chỉ kiểm tra trạng thái tại một thời điểm; tệp và quyền có thể thay đổi trước khi thao tác kế tiếp diễn ra (*TOCTOU*). Với dữ liệu quan trọng, phải xử lý lỗi ngay tại thao tác mở và đọc thực tế.

## 4. Ví dụ Java 17+ chạy độc lập

Ví dụ tự tạo tệp tạm có đuôi `.txt`, ghi nội dung UTF-8, dò MIME, xử lý `null` và xóa tệp sau khi dùng.

**`ProbeContentTypeDemo.java`**

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ProbeContentTypeDemo {
    public static void main(String[] args) throws IOException {
        Path path = Files.createTempFile("mime-demo-", ".txt");
        try {
            Files.writeString(path, "Hello, Java!", StandardCharsets.UTF_8);

            String mime = Files.probeContentType(path);
            if (mime == null) {
                System.out.println("Khong xac dinh duoc MIME type.");
            } else {
                System.out.println("MIME type suy doan: " + mime);
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp mã nguồn:

```bash
javac ProbeContentTypeDemo.java
java ProbeContentTypeDemo
```

Một **kết quả có thể gặp** khi môi trường nhận diện đuôi `.txt` là:

```text
MIME type suy doan: text/plain
```

Nếu bộ dò không xác định được loại tệp, stdout là:

```text
Khong xac dinh duoc MIME type.
```

Đây là **hai nhánh có điều kiện**. Không có stdout MIME cố định được bảo đảm xuyên môi trường. `finally` giúp dọn tệp tạm ngay cả khi việc ghi hoặc dò MIME bị lỗi.

Với một `Path file` đã có trong chương trình, có thể sử dụng ngắn gọn:

```java
String mime = Files.probeContentType(file);
String displayType = (mime == null) ? "Chua xac dinh" : mime;
```

Đây là **đoạn mã minh họa**, không phải chương trình độc lập; nó vẫn cần xử lý `IOException` tại phương thức gọi. `displayType` chỉ nên là thông tin hiển thị hoặc một gợi ý, không phải điều kiện đủ để chấp nhận tệp tải lên.

## 5. Giới hạn cần nắm vững

### 5.1. Dò MIME không xác thực nội dung

Đổi tên `note.txt` thành `note.png` không biến nội dung thành ảnh. Nếu bộ dò dùng phần mở rộng, kết quả có thể là `image/png` dù toàn bộ byte là văn bản. Các tệp sai đuôi, dữ liệu giả mạo, tệp hỏng, tệp ghép nhiều định dạng (*polyglot*) hoặc phần mở rộng không quen thuộc đều gây rủi ro suy đoán sai.

Không mặc định rằng `Files.probeContentType` đã đọc *magic bytes*. Ngay cả khi bộ dò có đọc vài byte, kiểm tra đó chưa bảo đảm toàn bộ tệp có cấu trúc hợp lệ.

### 5.2. `null` không khẳng định tệp bị hỏng hay không tồn tại

`null` chỉ có nghĩa bộ dò hiện tại **không xác định được MIME type**. Tệp có thể tồn tại, đọc được và hoàn toàn hợp lệ. Nếu nghiệp vụ bắt buộc phải biết định dạng, hãy chuyển sang quy trình xác minh phù hợp hoặc từ chối theo chính sách, thay vì tự gán một MIME nằm trong danh sách được phép.

`application/octet-stream` là fallback hợp lý trong một số ngữ cảnh truyền tải nhị phân, **nhưng không phải kết quả fallback do API này tự động bảo đảm**. Gán MIME fallback là quyết định của ứng dụng.

### 5.3. MIME khác charset, định dạng hợp lệ và quyền truy cập

`text/plain` không xác định nội dung được mã hóa UTF-8 hay UTF-16. Khi đọc văn bản, ứng dụng cần một hợp đồng charset rõ ràng; ví dụ `Files.readString(path, StandardCharsets.UTF_8)` chỉ phù hợp nếu nội dung thực sự sử dụng UTF-8.

MIME không cho biết tệp có quyền đọc/ghi/thực thi hay không. Trạng thái quyền được quản lý bằng cơ chế hệ thống tệp và chính sách ứng dụng. Dựa vào MIME để quyết định quyền thực thi hay truy cập là sai mục đích API.

### 5.4. Môi trường có thể trả kết quả khác nhau

Máy A có thể nhận dạng `.svg` trong khi máy B trả về `null` hoặc một chuỗi khác. Đặc biệt không nên viết kiểm thử cứng rằng `Files.probeContentType` phải trả về cùng MIME trong mọi CI, JDK và hệ điều hành.

Khi cần hành vi bất biến, ứng dụng nên định nghĩa danh sách định dạng hỗ trợ và bộ xác minh riêng. Kiểm thử nên bảo đảm mã xử lý đúng khi nhận MIME hợp lệ, MIME không mong đợi, `null` và `IOException`; kiểm thử cấu trúc tệp nên kiểm thử parser/decoder thay vì hành vi bộ dò của máy.

### 5.5. Không dùng MIME suy đoán làm hàng rào bảo mật

Không chỉ dùng `probeContentType` để chấp nhận upload, cho phép thực thi, mở tệp tự động, giải nén, hay hiển thị HTML/SVG inline trên trình duyệt. HTTP header `Content-Type` do client cung cấp cũng có thể bị giả mạo. Cần đánh giá loại tệp theo nội dung thực tế, ứng dụng xử lý và chính sách an toàn cụ thể.

## 6. Đối chiếu với kiểm tra chữ ký PNG bằng byte thực

PNG có chữ ký cố định ở đầu tệp: `89 50 4E 47 0D 0A 1A 0A` (hex). Chương trình sau đọc đúng tối đa tám byte đầu và đối chiếu chữ ký, **không dựa vào tên hay đuôi tệp**.

**`PngSignatureDemo.java`**

```java
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class PngSignatureDemo {
    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Cach dung: java PngSignatureDemo <duong-dan>");
            return;
        }

        Path path = Path.of(args[0]);
        try (InputStream input = Files.newInputStream(path)) {
            byte[] prefix = input.readNBytes(PNG_SIGNATURE.length);
            boolean matches = Arrays.equals(prefix, PNG_SIGNATURE);
            System.out.println("Co chu ky PNG: " + matches);
        }
    }
}
```

Lệnh biên dịch và chạy:

```bash
javac PngSignatureDemo.java
java PngSignatureDemo example.png
```

Nếu tệp tồn tại, đọc được và tám byte đầu khớp chữ ký PNG, stdout chính xác là:

```text
Co chu ky PNG: true
```

Nếu tệp đọc được nhưng không khớp chữ ký, stdout là:

```text
Co chu ky PNG: false
```

Nếu tệp quá ngắn, `readNBytes(8)` trả về mảng ít byte hơn và `Arrays.equals` trả về `false`. Nếu không mở hoặc đọc được tệp, chương trình có thể kết thúc bằng `IOException`. Một chữ ký đúng **chưa đủ chứng minh ảnh PNG hợp lệ hoặc an toàn**: cần kiểm tra cấu trúc/giải mã bằng thư viện thích hợp, cùng giới hạn kích thước, bộ nhớ và thời gian xử lý.

## 7. Quy trình phù hợp khi nhận tệp từ nguồn không tin cậy

Giả sử backend cho phép người dùng tải lên ảnh đại diện PNG/JPEG. Có thể triển khai theo thứ tự:

1. **Xác định danh sách cho phép**: chỉ PNG và JPEG; loại khác bị từ chối theo chính sách. Không tin tên tệp do client cung cấp để xây đường dẫn lưu trữ.
2. **Giới hạn dữ liệu trước khi xử lý tốn tài nguyên**: kích thước file upload, số lượng tệp, vị trí lưu, quyền truy cập và ngân sách giải mã.
3. **Dò MIME để tham khảo**: gọi `Files.probeContentType(path)` và xử lý cả `null` lẫn `IOException`; gợi ý MIME không cấp quyền chấp nhận.
4. **Kiểm tra byte và định dạng**: kiểm tra chữ ký, sau đó dùng decoder/parser tin cậy để xác minh tệp đọc được theo định dạng đã cho. Không chỉ kiểm tra phần mở rộng hoặc tám byte đầu.
5. **Áp dụng chính sách xử lý và xuất tệp**: chỉ chấp nhận tệp đã qua các kiểm tra cần thiết; chọn MIME cho phản hồi dựa trên định dạng đã được xác minh. Với nội dung không tin cậy, cân nhắc `Content-Disposition: attachment` và `X-Content-Type-Options: nosniff` khi phục vụ qua HTTP.
6. **Xử lý lỗi và thay đổi trạng thái**: kiểm tra `IOException` ở thao tác I/O thật; tránh dựa vào một lần kiểm tra thuộc tính trước đó nếu tệp có thể bị thay thế.

Kiểm tra định dạng không thay thế quét nội dung độc hại, cô lập môi trường xử lý hoặc cập nhật thư viện giải mã khi tình huống yêu cầu những biện pháp ấy.

## 8. Lỗi thường gặp

| Cách viết hoặc giả định sai | Hệ quả | Cách xử lý |
| --- | --- | --- |
| `Files.probeContentType(path).equals("image/png")` | `null` gây `NullPointerException` | Viết `"image/png".equals(Files.probeContentType(path))` và xử lý I/O |
| `null` nghĩa là không tồn tại | Chẩn đoán sai | Dùng API truy cập/kiểm tra tồn tại phù hợp |
| `image/png` nghĩa là ảnh PNG hợp lệ | Có thể bị giả mạo bằng phần mở rộng | Kiểm tra byte và giải mã |
| MIME chứa charset | Có thể đọc văn bản sai mã hóa | Đặt quy tắc charset rõ ràng |
| MIME quyết định quyền đọc/ghi | Sai khái niệm | Áp dụng quyền hệ thống tệp/chính sách |
| `isReadable` bảo đảm lần đọc sau thành công | Tệp/quyền có thể đổi giữa hai thao tác | Bắt lỗi tại thao tác I/O thực tế |
| Test bắt buộc `.txt` phải luôn thành `text/plain` | Thất bại trên máy khác | Test nhánh `null`/giá trị không mong đợi; kiểm định riêng |

## 9. Tổng kết

**`Files.probeContentType(Path)` là API suy đoán MIME type, có thể trả về `null` hoặc ném `IOException`.** Cách dò phụ thuộc JDK, hệ điều hành và các bộ phát hiện được cài đặt. Không được suy ra rằng Java luôn mở tệp, đọc magic bytes hay xác minh toàn bộ định dạng. Trong hệ thống I/O, cần phân biệt tên và thuộc tính, khả năng truy cập, nội dung thực tế, tính hợp lệ của định dạng và chính sách nghiệp vụ.

**Tham khảo API:** Java SE 17, `java.nio.file.Files#probeContentType(Path)` và `java.nio.file.spi.FileTypeDetector`.


</details>

- [Quay lại đầu trang](#back-to-top)
