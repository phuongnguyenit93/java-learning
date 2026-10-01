# File và mô hình đường dẫn cũ

Luồng I/O trả lời câu hỏi “dữ liệu đi vào/ra như thế nào”. Trước khi mở luồng cho một tệp, chương trình còn cần nói tới vị trí, tên tệp, **thư mục (directory)** và **siêu dữ liệu (metadata)** — thông tin mô tả như tệp có tồn tại không, là tệp hay thư mục, kích thước bao nhiêu hoặc sửa lần cuối khi nào. `java.io.File` là API cũ của Java cho phần này.

## <a id="legacy-file-model">Mô hình java.io.File</a>

Tên `File` dễ làm người mới hiểu rằng đối tượng này “chính là tệp đang mở”. Thực tế, một `java.io.File` chủ yếu biểu diễn một **đường dẫn (pathname)** — giá trị mô tả tên/vị trí của một tệp hoặc thư mục trong hệ thống tệp. **Đường dẫn tuyệt đối (absolute pathname)** xác định vị trí từ điểm gốc của hệ thống tệp, còn với `java.io`, **đường dẫn tương đối (relative pathname)** được giải quyết dựa trên thư mục người dùng hiện tại do **thuộc tính hệ thống (system property)** `user.dir` chỉ ra, thường là thư mục nơi JVM được khởi chạy. Đối tượng vẫn có thể được tạo ngay cả khi mục tiêu chưa tồn tại. Cụm “abstract pathname” trong JDK nhấn mạnh rằng đây là mô hình đường dẫn, không phải **tài nguyên tệp đang mở (file handle)** để đọc/ghi.

```text
đối tượng java.io.File
    ↓ chứa đường dẫn
"data/report.txt"
    ↓ khi thao tác cần hỏi hệ thống tệp
exists? isFile? isDirectory? length? mkdir? delete? ...
```

Tạo đối tượng `File` không mở tệp và cũng không đảm bảo đường dẫn tồn tại:

```java
java.io.File file = new java.io.File("data/report.txt");

System.out.println(file.getPath());
System.out.println(file.exists());
```

Dòng đầu luôn có thể in đường dẫn mà đối tượng đang giữ. Dòng `exists()` mới hỏi hệ thống tệp về trạng thái hiện tại.

`File` cũng có thể biểu diễn thư mục:

```java
java.io.File tempDir =
    new java.io.File(System.getProperty("java.io.tmpdir"));

System.out.println(tempDir.isDirectory());
```

Vì vậy mô hình cần nhớ là **đường dẫn + các thao tác hệ thống tệp kiểu cũ**, không phải “tài nguyên tệp đang mở để đọc/ghi”. Muốn đọc/ghi nội dung theo luồng, ta vẫn mở `FileInputStream`, `FileOutputStream`, reader/writer hoặc dùng API NIO hiện đại.

## <a id="file-path-limitations">Giới hạn của mô hình File/đường dẫn cũ</a>

`File` giải quyết được nhiều nhu cầu cơ bản, nhưng API cũ có một số giới hạn đáng chú ý.

Thứ nhất, nhiều thao tác báo thất bại bằng `boolean` thay vì cho biết nguyên nhân chi tiết:

```java
java.io.File file = new java.io.File("data/report.txt");

if (!file.delete()) {
    System.out.println("Xóa thất bại, nhưng boolean không nói rõ nguyên nhân");
}
```

Thất bại có thể do tệp không tồn tại, thiếu quyền, tệp đang được sử dụng, hệ thống tệp từ chối thao tác hoặc lý do khác. Chỉ từ `false`, mã không có đủ ngữ cảnh để phân biệt.

Thứ hai, cách xử lý đường dẫn dưới dạng chuỗi dễ trộn nhiều khái niệm:

- đường dẫn tương đối và tuyệt đối;
- **ký tự phân cách (separator)** giữa các thành phần đường dẫn có thể khác theo nền tảng, ví dụ `/` hoặc `\`;
- `.` biểu diễn “thư mục hiện tại”, còn `..` biểu diễn “thư mục cha” trong cú pháp đường dẫn;
- **liên kết tượng trưng (symbolic link, symlink)** — một mục trong hệ thống tệp trỏ tới đường dẫn/mục tiêu khác — và **đường dẫn chuẩn tắc (canonical path)** — dạng đường dẫn đã được hệ thống tệp giải quyết/chuẩn hóa theo quy tắc của nó;
- thao tác trên đường dẫn so với thao tác trên nội dung tệp.

`getAbsoluteFile()` chỉ tạo biểu diễn tuyệt đối theo môi trường hiện tại. `getCanonicalFile()` còn giải quyết thêm các chi tiết phụ thuộc hệ thống tệp và có thể cần I/O, vì vậy nó có thể ném `IOException`. Hai khái niệm này không nên coi là giống nhau.

Thứ ba, API `File` không cung cấp mô hình phong phú bằng **NIO.2**, tên thường dùng cho nhóm API hệ thống tệp hiện đại trong `java.nio.file` như `Path` và `Files`, với thuộc tính, liên kết tượng trưng, duyệt cây thư mục, tùy chọn sao chép/di chuyển và thông tin lỗi chi tiết.

## <a id="file-api-boundary">Ranh giới File với Path/Files</a>

Mã Java hiện đại thường ưu tiên `java.nio.file.Path` để biểu diễn đường dẫn và `java.nio.file.Files` để thực hiện các thao tác trên hệ thống tệp.

```text
kiểu cũ
File
  └─ đường dẫn + thao tác trong cùng một kiểu

NIO.2 hiện đại
Path
  └─ biểu diễn đường dẫn

Files
  └─ thao tác trên hệ thống tệp
```

`File` vẫn xuất hiện nhiều trong API cũ và thư viện hiện hữu, nên cần biết cách làm việc với nó. Java cho phép chuyển đổi trực tiếp qua lại:

```java
java.io.File legacy = new java.io.File("data/report.txt");
java.nio.file.Path path = legacy.toPath();
java.io.File back = path.toFile();
```

`Path`/`Files` thường cung cấp thao tác rõ hơn và ngoại lệ giàu thông tin hơn:

```java
java.nio.file.Path path = java.nio.file.Path.of("data", "report.txt");

try {
    java.nio.file.Files.delete(path);
} catch (java.nio.file.NoSuchFileException e) {
    System.out.println("File không tồn tại: " + e.getFile());
}
```

Ranh giới này dẫn tự nhiên sang chương kế tiếp: sau khi hiểu `File` là mô hình đường dẫn kiểu cũ và thấy giới hạn của nó, ta sẽ học `Path`/`Files` như mô hình hiện đại để biểu diễn đường dẫn và thao tác với hệ thống tệp.
