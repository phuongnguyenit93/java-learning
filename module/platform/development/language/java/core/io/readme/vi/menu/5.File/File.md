# File API truyền thống

Stream trả lời câu hỏi “dữ liệu đi vào/ra như thế nào”. Trước khi mở stream cho một file, chương trình còn cần nói tới vị trí của file, tên file, **directory (thư mục)** và **metadata** — thông tin mô tả như file có tồn tại không, là file hay directory, kích thước bao nhiêu, sửa lần cuối khi nào. `java.io.File` là API cũ (legacy) được Java dùng từ sớm cho phần này.

## <a id="legacy-file-model">Mô hình java.io.File</a>

Tên `File` dễ làm người mới hiểu rằng object này “chính là file đang mở”. Thực tế, một `java.io.File` chủ yếu biểu diễn một **pathname** — giá trị mô tả tên/vị trí của một file hoặc directory trong filesystem. **Absolute pathname** chứa vị trí tính từ root/điểm gốc của filesystem, còn **relative pathname** phải được diễn giải dựa trên working directory hiện tại của process. Object vẫn tạo được ngay cả khi mục tiêu chưa tồn tại. Cụm “abstract pathname” trong JDK nhấn mạnh rằng đây là mô hình đường dẫn, không phải file handle đã được mở để đọc/ghi.

```text
java.io.File object
    ↓ chứa pathname
"data/report.txt"
    ↓ khi gọi API cần filesystem
exists? isFile? isDirectory? length? mkdir? delete? ...
```

Tạo object `File` không mở file và cũng không đảm bảo đường dẫn tồn tại:

```java
java.io.File file = new java.io.File("data/report.txt");

System.out.println(file.getPath());
System.out.println(file.exists());
```

Dòng đầu luôn có thể in pathname object đang giữ. Dòng `exists()` mới hỏi filesystem về trạng thái hiện tại.

`File` cũng có thể biểu diễn directory:

```java
java.io.File tempDir =
    new java.io.File(System.getProperty("java.io.tmpdir"));

System.out.println(tempDir.isDirectory());
```

Vì vậy mental model đúng là **pathname + legacy filesystem operations**, không phải “handle đang đọc/ghi dữ liệu”. Muốn đọc/ghi nội dung theo stream, ta vẫn mở `FileInputStream`, `FileOutputStream`, reader/writer hoặc dùng API NIO hiện đại.

## <a id="file-path-limitations">Giới hạn của File/path model cũ</a>

`File` giải quyết được nhiều nhu cầu cơ bản, nhưng API cũ có một số giới hạn đáng chú ý.

Thứ nhất, nhiều thao tác báo thất bại bằng `boolean` thay vì cho biết nguyên nhân chi tiết:

```java
java.io.File file = new java.io.File("data/report.txt");

if (!file.delete()) {
    System.out.println("Xóa thất bại, nhưng boolean không nói rõ nguyên nhân");
}
```

Thất bại có thể do file không tồn tại, thiếu quyền, file đang được sử dụng, filesystem từ chối thao tác hoặc lý do khác. Chỉ từ `false`, code không có đủ ngữ cảnh để phân biệt.

Thứ hai, pathname dạng chuỗi dễ trộn nhiều khái niệm:

- đường dẫn tương đối và tuyệt đối;
- **separator** là ký tự phân cách các thành phần đường dẫn và có thể khác theo nền tảng, ví dụ `/` hoặc `\`;
- `.` biểu diễn “directory hiện tại”, còn `..` biểu diễn “directory cha” trong cú pháp đường dẫn;
- **symbolic link (symlink)** — một entry trong filesystem trỏ tới path/target khác — và **canonical path** — dạng đường dẫn đã được filesystem giải quyết/chuẩn hóa theo quy tắc của nó;
- thao tác trên file so với thao tác trên chính đường dẫn.

`getAbsoluteFile()` chỉ tạo biểu diễn tuyệt đối theo môi trường hiện tại. `getCanonicalFile()` còn chuẩn hóa thêm theo filesystem và có thể cần I/O, vì vậy nó có thể ném `IOException`. Hai khái niệm này không nên coi là giống nhau.

Thứ ba, API `File` không cung cấp mô hình phong phú bằng **NIO.2**, tên thường dùng cho nhóm API filesystem hiện đại trong `java.nio.file` như `Path` và `Files`, cho attributes, symbolic links, walking directory tree, copy/move options và thông tin lỗi.

## <a id="file-api-boundary">Ranh giới File với Path/Files</a>

Code mới thường ưu tiên `java.nio.file.Path` để biểu diễn đường dẫn và `java.nio.file.Files` để thực hiện filesystem operations.

```text
legacy
File
  └─ pathname + operations cùng một type

modern NIO.2
Path
  └─ biểu diễn path

Files
  └─ operations trên filesystem
```

`File` vẫn xuất hiện nhiều trong API cũ và thư viện hiện hữu, nên cần biết cách làm việc với nó. Java cho phép chuyển qua lại:

```java
java.io.File legacy = new java.io.File("data/report.txt");
java.nio.file.Path path = legacy.toPath();
java.io.File back = path.toFile();
```

`Path`/`Files` thường cho API rõ hơn và exception giàu thông tin hơn:

```java
java.nio.file.Path path = java.nio.file.Path.of("data", "report.txt");

try {
    java.nio.file.Files.delete(path);
} catch (java.nio.file.NoSuchFileException e) {
    System.out.println("File không tồn tại: " + e.getFile());
}
```

Điều này tạo transition tự nhiên sang chapter kế tiếp: sau khi hiểu `File` là mô hình pathname legacy và thấy giới hạn của nó, ta sẽ học `Path`/`Files` như boundary hiện đại để biểu diễn đường dẫn và thao tác filesystem.
