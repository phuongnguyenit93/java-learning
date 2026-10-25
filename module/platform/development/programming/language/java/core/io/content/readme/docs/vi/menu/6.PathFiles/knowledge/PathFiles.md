# Path, Files và mô hình hệ thống tệp hiện đại

Sau `java.io.File`, Java hiện đại thường làm việc với hệ thống tệp qua hai vai trò tách biệt: `Path` biểu diễn **đường dẫn**, còn `Files` thực hiện **thao tác I/O và truy vấn siêu dữ liệu (metadata)** trên đường dẫn đó. Cách tách này giúp ta trả lời hai câu hỏi độc lập: tài nguyên nằm ở đâu, và ta muốn làm gì với tài nguyên ấy?

Trong các ví dụ của chương này, ta dùng một thư mục tạm và một tệp UTF-8:

~~~java
Path tempDir = Files.createTempDirectory("io-demo-");
Path note = tempDir.resolve("note.txt");
String text = "Xin chào Java I/O";
~~~

## <a id="path-model">Mô hình Path</a>

`Path` mô hình hóa một đường dẫn trong hệ thống tệp. **Đường dẫn tuyệt đối (absolute path)** bắt đầu từ gốc đủ để xác định vị trí độc lập với thư mục làm việc hiện tại, ví dụ `C:\work\note.txt` hoặc `/tmp/note.txt`. **Đường dẫn tương đối (relative path)** như `data/note.txt` phải được hiểu dựa trên một đường dẫn cơ sở, thường là thư mục làm việc hiện tại hoặc một `Path` khác mà mã chủ động ghép bằng `resolve(...)`.

Điểm quan trọng với người mới học là: **tạo một Path không đồng nghĩa với truy cập hệ thống tệp**. Đoạn mã sau chỉ tạo một đối tượng mô tả đường dẫn:

~~~java
Path path = Path.of("data", "note.txt");
~~~

Tệp `data/note.txt` có thể tồn tại hoặc chưa tồn tại. Chỉ khi gọi các API như `Files.readString(path)`, `Files.exists(path)` hay `Files.createFile(path)` thì chương trình mới yêu cầu hệ thống tệp thực hiện công việc tương ứng.

`Path` cũng không chỉ là một `String`. Nó biết các thành phần đường dẫn:

~~~java
Path path = Path.of("data", "reports", "2026", "summary.txt");

System.out.println(path.getFileName()); // summary.txt
System.out.println(path.getParent());   // data/reports/2026 theo cách hiển thị của hệ thống tệp
System.out.println(path.isAbsolute());  // false
~~~

Dấu phân cách và cách hiển thị cụ thể phụ thuộc hệ thống tệp. Vì vậy nên xây dựng đường dẫn bằng `Path.of(...)`, `resolve(...)` và các API của `Path` thay vì tự nối chuỗi bằng ký tự phân cách.

Mối quan hệ cần nhớ:

~~~text
Path
→ mô tả vị trí / tên đường dẫn

Files
→ thực hiện thao tác hệ thống tệp bằng Path
~~~

Chương trước dùng `File` để thấy mô hình API cũ. `Path`/`Files` là hướng nên ưu tiên cho mã Java hiện đại vì API rõ vai trò hơn, hỗ trợ nhiều thao tác hơn và biểu diễn lỗi bằng ngoại lệ cụ thể hơn.

## <a id="resolve-normalize">resolve, normalize và relativize</a>

Ứng dụng thường có một thư mục gốc rồi cần tạo đường dẫn con. `resolve` diễn đạt đúng ý này:

~~~java
Path root = Path.of("workspace");
Path file = root.resolve("notes").resolve("today.txt");
~~~

Nếu đối số của `resolve` là một đường dẫn tuyệt đối, kết quả là chính đường dẫn tuyệt đối đó. Đây là hành vi cần nhớ khi một phần đường dẫn đến từ cấu hình hoặc dữ liệu đầu vào.

`normalize()` xử lý cấu trúc đường dẫn theo cú pháp, ví dụ loại `.` và các cặp `name/..` có thể rút gọn:

~~~java
Path raw = Path.of("data", ".", "reports", "..", "note.txt");
Path normalized = raw.normalize();

System.out.println(normalized); // data/note.txt
~~~

`normalize()` **không truy cập hệ thống tệp** và không giải quyết liên kết tượng trưng (symbolic link, thường gọi là symlink). Symbolic link là một mục trong hệ thống tệp dùng để trỏ tới một đường dẫn/đích khác thay vì tự chứa nội dung của đích. Đây là lý do `normalize()` khác `toRealPath()`: `toRealPath()` truy cập hệ thống tệp, yêu cầu đường dẫn tồn tại và trả về đường dẫn thực sau khi xử lý link theo tùy chọn.

Với symbolic link, một đường dẫn có thành phần `..` có thể mang ý nghĩa khi chạy khác với đường dẫn đã rút gọn thuần cú pháp. Vì vậy không nên xem `normalize()` là bằng chứng rằng hai đường dẫn chắc chắn trỏ cùng một tệp.

`relativize()` trả lời câu hỏi ngược lại: “từ A cần đi tương đối thế nào để tới B?”:

~~~java
Path base = Path.of("workspace", "docs");
Path target = Path.of("workspace", "images", "logo.png");

Path relative = base.relativize(target);
System.out.println(relative);
~~~

Hai đường dẫn phải tương thích, chẳng hạn cùng là đường dẫn tuyệt đối hoặc cùng là đường dẫn tương đối và thuộc hệ thống tệp phù hợp. `relativize()` hữu ích khi lưu liên kết tương đối hoặc hiển thị đường dẫn ngắn, nhưng nó vẫn là phép toán trên đường dẫn, chưa đọc hay ghi dữ liệu.

## <a id="files-operations">Các thao tác với Files</a>

Khi đã có `Path`, lớp tiện ích `Files` cung cấp các thao tác hệ thống tệp phổ biến. Với tệp nhỏ và có giới hạn kích thước rõ ràng, Java hiện đại có các phương thức tiện lợi để đọc hoặc ghi toàn bộ nội dung:

~~~java
Path tempDir = Files.createTempDirectory("io-demo-");
Path note = tempDir.resolve("note.txt");

Files.writeString(
        note,
        "Xin chào Java I/O",
        StandardCharsets.UTF_8
);

String loaded = Files.readString(note, StandardCharsets.UTF_8);
System.out.println(loaded);
~~~

Ta cố ý truyền `StandardCharsets.UTF_8` để quy tắc chuyển đổi giữa byte trên đĩa và ký tự trong `String` được thể hiện rõ.

Các nhóm thao tác thường gặp:

| Nhu cầu | API thường dùng |
| --- | --- |
| Tạo thư mục | `Files.createDirectory`, `Files.createDirectories` |
| Tạo tệp | `Files.createFile` |
| Đọc/ghi tệp nhỏ | `readString`, `writeString`, `readAllBytes`, `write` |
| Mở luồng | `newInputStream`, `newOutputStream`, `newBufferedReader`, `newBufferedWriter` |
| Sao chép | `copy` |
| Di chuyển/đổi tên | `move` |
| Xóa | `delete`, `deleteIfExists` |

`createDirectory` tạo đúng một thư mục và yêu cầu thư mục cha đã tồn tại. `createDirectories` tạo cả các thư mục cha còn thiếu:

~~~java
Path reports = tempDir.resolve("a").resolve("b").resolve("reports");
Files.createDirectories(reports);
~~~

Với sao chép/di chuyển, hành vi phải được nói rõ bằng tùy chọn khi cần:

~~~java
Path copy = tempDir.resolve("note-copy.txt");
Files.copy(note, copy, StandardCopyOption.REPLACE_EXISTING);

Path moved = tempDir.resolve("archive.txt");
Files.move(copy, moved, StandardCopyOption.REPLACE_EXISTING);
~~~

`StandardCopyOption.ATOMIC_MOVE` có thể yêu cầu hệ thống tệp thực hiện việc di chuyển như một bước nguyên tử (atomic): các bên quan sát không nên thấy trạng thái trung gian kiểu “đã chuyển một phần nhưng chưa xong”. Không phải hệ thống tệp nào cũng hỗ trợ khả năng này. Nếu ứng dụng thực sự cần atomic move, phải xử lý `AtomicMoveNotSupportedException` thay vì giả định tùy chọn luôn thành công.

Một vài ngữ nghĩa rất dễ bị hiểu sai:

- `Files.copy(source, target)` mặc định **đi theo symbolic link ở nguồn** và sao chép đích của link. Nếu truyền `LinkOption.NOFOLLOW_LINKS`, chính symbolic link được sao chép thay vì đích.
- `Files.move(...)` di chuyển chính symbolic link nếu nguồn là symlink; nó không “di chuyển đích mà link đang trỏ tới”.
- `Files.copy(...)` với nguồn là thư mục chỉ tạo thư mục đích tương ứng; nó **không sao chép đệ quy toàn bộ cây**. Muốn sao chép cây phải duyệt cây, ví dụ bằng `walkFileTree`.
- `Files.delete(...)` không xóa đệ quy cả cây thư mục; xóa thư mục không rỗng có thể thất bại với `DirectoryNotEmptyException`.
- Khi dùng `ATOMIC_MOVE`, các tùy chọn di chuyển khác bị bỏ qua. Nếu đích đã tồn tại, việc thay thế hay báo lỗi phụ thuộc phần triển khai; vì vậy không nên ghép `ATOMIC_MOVE` với giả định đa nền tảng rằng `REPLACE_EXISTING` chắc chắn được tôn trọng.

`Files` cũng có các phiên bản nạp chồng (overload) nhận `StandardOpenOption`:

~~~java
Files.writeString(
        note,
        System.lineSeparator() + "Dòng mới",
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND
);
~~~

Mô hình ngắn cho các tùy chọn phổ biến:

| Option | Ý nghĩa |
| --- | --- |
| `READ` | Mở để đọc |
| `WRITE` | Mở để ghi |
| `CREATE` | Tạo tệp nếu chưa có |
| `CREATE_NEW` | Chỉ tạo nếu chưa tồn tại; báo lỗi nếu đã có |
| `APPEND` | Ghi tiếp vào cuối tệp |
| `TRUNCATE_EXISTING` | Nếu tệp tồn tại và mở để ghi, cắt độ dài về 0 trước khi ghi |

Điểm quan trọng là tùy chọn mô tả **quy ước mở tệp**, không chỉ là cú pháp. Ví dụ `CREATE_NEW` phù hợp khi ứng dụng cần “tạo mới hoặc báo lỗi”, còn `APPEND` phù hợp khi dữ liệu mới phải nối vào cuối thay vì ghi đè từ đầu.

Khi dữ liệu lớn, `readAllBytes` và `readString` không còn là lựa chọn tốt vì chúng nạp toàn bộ nội dung vào bộ nhớ. Lúc đó nên chuyển sang stream, reader/writer hoặc channel. Chương cuối sẽ gom các tiêu chí lựa chọn này thành một quyết định thống nhất.

## <a id="temporary-files">Tệp và thư mục tạm</a>

Ứng dụng thường cần một nơi lưu **tạm thời** cho dữ liệu trung gian: tệp tải lên đang xử lý, tệp giải nén, kết quả biến đổi, bộ nhớ đệm ngắn hạn hoặc dữ liệu phục vụ kiểm thử. Tự ghép một tên như `tmp.txt` dễ đụng tên giữa nhiều thread/tiến trình và cũng khiến mã phải tự quyết định thư mục nào là nơi tạm phù hợp. `Files.createTempFile(...)` và `Files.createTempDirectory(...)` giải quyết đúng bài toán này bằng cách **tạo thật** một tệp/thư mục mới với tên do provider hệ thống tệp tạo đủ duy nhất trong bối cảnh đó.

```java
Path tempFile = Files.createTempFile("report-", ".txt");
Path tempDir = Files.createTempDirectory("work-");
```

Các phiên bản nạp chồng không truyền thư mục cha dùng **thư mục tạm mặc định** của môi trường chạy. Khi ứng dụng muốn kiểm soát nơi đặt dữ liệu, truyền một thư mục rõ ràng:

```java
Path workspace = Path.of("work");
Files.createDirectories(workspace);

Path tempFile = Files.createTempFile(workspace, "upload-", ".bin");
Path tempDir = Files.createTempDirectory(workspace, "extract-");
```

Tạo `Path` chỉ tạo đối tượng mô tả đường dẫn; ngược lại, `createTempFile` và `createTempDirectory` là **thao tác hệ thống tệp** và tài nguyên tương ứng tồn tại ngay khi phương thức trả về thành công.

Một hiểu lầm phổ biến là “tạm” có nghĩa JVM sẽ tự dọn. **Không có bảo đảm như vậy.** `try-with-resources` đóng stream/channel đang mở nhưng không tự xóa tệp hoặc thư mục mà tài nguyên đó trỏ tới. Nếu mã tạo tài nguyên tạm thì mã cũng cần xác định quyền sở hữu và cách dọn dẹp:

```java
Path temp = Files.createTempFile("job-", ".dat");

try {
    Files.writeString(temp, "intermediate data", StandardCharsets.UTF_8);
    process(temp); // helper của ứng dụng
} finally {
    Files.deleteIfExists(temp);
}
```

Với thư mục tạm, cần xóa nội dung bên trong trước rồi mới xóa thư mục; `Files.delete(tempDir)` không xóa đệ quy. Đây là lý do việc duyệt cây phía sau có liên quan trực tiếp tới dọn dẹp workspace tạm phức tạp.

Một số API mở tệp chấp nhận `StandardOpenOption.DELETE_ON_CLOSE`:

```java
Path temp = Files.createTempFile("session-", ".bin");

try (SeekableByteChannel channel = Files.newByteChannel(
        temp,
        StandardOpenOption.WRITE,
        StandardOpenOption.DELETE_ON_CLOSE)) {
    channel.write(ByteBuffer.wrap(new byte[] {1, 2, 3}));
}
```

Tùy chọn này yêu cầu phần triển khai **cố gắng xóa tốt nhất có thể (best effort)** khi tài nguyên được đóng. Nó hữu ích cho một số tệp tạm gắn chặt với vòng đời của handle đang mở, nhưng không nên được coi là chiến lược dọn dẹp duy nhất cho mọi môi trường: provider/nền tảng có thể khác nhau, việc dừng hoặc gặp sự cố bất thường không phải một giao dịch bảo đảm xóa. Khi dọn dẹp là yêu cầu của ứng dụng, quyền sở hữu rõ ràng kết hợp `finally` hoặc quy trình dọn dẹp vẫn là mô hình an toàn hơn.

## <a id="file-attributes">Thuộc tính và siêu dữ liệu của tệp</a>

Nội dung tệp là dữ liệu bên trong. **Siêu dữ liệu (metadata)** mô tả tệp: kích thước, thời gian sửa đổi, loại tệp, quyền truy cập và các thuộc tính khác do hệ thống tệp cung cấp.

Các kiểm tra đơn giản:

~~~java
System.out.println(Files.exists(note));
System.out.println(Files.isRegularFile(note));
System.out.println(Files.isDirectory(note));
System.out.println(Files.size(note));
System.out.println(Files.getLastModifiedTime(note));
~~~

`Files.exists(path)` và `Files.notExists(path)` **không phải hai phép phủ định tuyệt đối của nhau**. Nếu hệ thống tệp không thể xác định trạng thái, cả hai có thể cùng trả `false`. Vì vậy:

~~~text
exists == true
→ đã xác nhận tồn tại tại thời điểm kiểm tra

notExists == true
→ đã xác nhận không tồn tại tại thời điểm kiểm tra

cả hai false
→ trạng thái chưa xác định được
~~~

Nếu câu hỏi thật sự là “hai `Path` này có trỏ tới cùng một tệp không?”, đừng suy ra chỉ từ chuỗi đường dẫn hoặc `normalize()`. `Files.isSameFile(a, b)` xử lý định danh tệp và các trường hợp như liên kết tượng trưng phù hợp hơn. Có một ngoại lệ quan trọng: nếu hai giá trị `Path` đã bằng nhau, `isSameFile` trả `true` mà không cần kiểm tra tệp có tồn tại hay không, vì vậy **đây không phải API kiểm tra sự tồn tại của tệp**.

Khi cần lấy nhiều thuộc tính cùng lúc, dùng `readAttributes`:

~~~java
BasicFileAttributes attrs =
        Files.readAttributes(note, BasicFileAttributes.class);

System.out.println(attrs.size());
System.out.println(attrs.creationTime());
System.out.println(attrs.lastModifiedTime());
System.out.println(attrs.isRegularFile());
~~~

`BasicFileAttributes` là lớp nền có tính đa nền tảng tương đối cao. Một số hệ thống tệp hỗ trợ nhóm thuộc tính chuyên biệt như `PosixFileAttributes` hoặc thuộc tính DOS. Mã dùng các nhóm này phải chấp nhận rằng hệ thống tệp khác có thể không hỗ trợ.

Symbolic link cũng ảnh hưởng siêu dữ liệu. Nhiều API mặc định đi theo link tới đích. Khi mục tiêu là hỏi về chính mục link, một số API cho phép `LinkOption.NOFOLLOW_LINKS`:

~~~java
boolean followsTarget = Files.exists(note);
boolean pathEntryExists =
        Files.exists(note, LinkOption.NOFOLLOW_LINKS);
~~~

Một lỗi thiết kế phổ biến là:

~~~text
kiểm tra exists
→ giả định trạng thái vẫn giữ nguyên
→ thực hiện thao tác sau đó
~~~

Hệ thống tệp có thể thay đổi giữa hai bước do thread hoặc tiến trình khác. Vì vậy hãy để thao tác thực tế quyết định thành công/thất bại và xử lý ngoại lệ, thay vì coi kiểm tra trước đó là cam kết.

## <a id="filesystem-provider-model">FileSystem, FileSystemProvider và FileStore</a>

`Path` không tồn tại một mình. **Mỗi `Path` thuộc về một `FileSystem` cụ thể**. `FileSystem` mô tả không gian tên và quy tắc đường dẫn mà `Path` đang thuộc về: các gốc nào tồn tại, dấu phân cách nào được dùng, `FileStore` nào khả dụng và những khả năng nào provider phía sau hỗ trợ.

Mô hình nên nhớ:

~~~text
Path
→ thuộc về một FileSystem
→ FileSystem được triển khai/cung cấp bởi FileSystemProvider
→ dữ liệu thực tế nằm trên một hay nhiều FileStore
~~~

Trong hầu hết ứng dụng desktop hoặc máy chủ thông thường, `Path.of(...)` dùng **hệ thống tệp mặc định** của hệ điều hành. Nhưng NIO.2 không giới hạn `Path` vào hệ thống tệp mặc định: Java có thể làm việc với hệ thống tệp khác do provider cung cấp, ví dụ hệ thống tệp ZIP/JAR. Điều này giải thích vì sao `Path` là mức trừu tượng rộng hơn một chuỗi đường dẫn Windows/Linux.

~~~java
Path path = Path.of("data", "note.txt");
FileSystem fs = path.getFileSystem();

System.out.println(fs.provider().getScheme());
System.out.println(fs.getSeparator());
~~~

`FileSystemProvider` là lớp triển khai đứng sau các thao tác hệ thống tệp. Mã ứng dụng thường **không gọi provider trực tiếp**; nó dùng `Path` và `Files`, còn provider thực hiện thao tác theo khả năng của hệ thống tệp đó. Đây là nguyên nhân chung cho nhiều ranh giới về tính đa nền tảng đã gặp trong chương này:

~~~text
ATOMIC_MOVE có thể không hỗ trợ
POSIX permission view có thể không tồn tại
WatchService có thể phân phối sự kiện khác nhau
hành vi symbolic link có thể khác
→ vì khả năng/ngữ nghĩa phụ thuộc hệ thống tệp + provider
~~~

`FileStore` đại diện cho vùng lưu trữ/hệ thống tệp phía sau mà một đường dẫn nằm trên đó. Nó cho phép hỏi thông tin như dung lượng và nhóm thuộc tính mà nơi lưu trữ hỗ trợ:

~~~java
FileStore store = Files.getFileStore(path);

long total = store.getTotalSpace();
long usable = store.getUsableSpace();

boolean posix =
        store.supportsFileAttributeView("posix");
~~~

Các giá trị dung lượng chỉ là ảnh chụp tại thời điểm hỏi và không phải bảo đảm về quota hay giao dịch. Tương tự, việc một `FileStore` báo hỗ trợ một nhóm thuộc tính chỉ nói rằng khả năng đó tồn tại ở mức store/provider; thao tác cụ thể vẫn có thể thất bại vì quyền truy cập, trạng thái tệp hoặc điều kiện tranh chấp.

Một hệ quả quan trọng là **không phải mọi `Path` từ mọi provider đều có thể chuyển sang `java.io.File`**. `Path.toFile()` chỉ được hỗ trợ bởi provider mặc định; với provider khác nó có thể ném `UnsupportedOperationException`. Vì vậy mã hiện đại nên giữ dữ liệu dưới dạng `Path` thay vì chuyển sang `File` chỉ vì thói quen.

Hai `Path` từ provider/hệ thống tệp không tương thích cũng không nên được trộn tùy ý. Những thao tác như `resolve`, `relativize` hoặc sao chép/di chuyển có ranh giới provider riêng; khi thiết kế mã dùng lại được, hãy coi **định danh hệ thống tệp** là một phần của ngữ cảnh chứ không giả định mọi đường dẫn đều cùng loại.

Phần này không yêu cầu người mới tự viết `FileSystemProvider` tùy chỉnh. Mục tiêu là hiểu kiến trúc: `Path` thuộc một `FileSystem`, `Files` chuyển thao tác tới provider, và `FileStore` mô tả vùng lưu trữ cùng khả năng hỗ trợ. Mô hình này nối lại các giới hạn đa nền tảng trong toàn bộ chương.

## <a id="file-permissions-ownership">Nhóm thuộc tính, quyền truy cập và chủ sở hữu</a>

`BasicFileAttributes` cung cấp tập siêu dữ liệu cơ bản có tính đa nền tảng tương đối cao. Nhưng hệ thống tệp còn có những nhóm siêu dữ liệu chuyên biệt. NIO.2 mô hình các nhóm này bằng **góc nhìn thuộc tính tệp (file attribute view)**: một “góc nhìn” cho biết provider hỗ trợ đọc/ghi loại siêu dữ liệu nào.

```text
BasicFileAttributeView
→ kích thước, thời gian, loại tệp, fileKey...

PosixFileAttributeView
→ chủ sở hữu, nhóm và quyền rwx trên hệ thống tệp kiểu POSIX

DosFileAttributeView
→ readonly, hidden, archive, system trên provider hỗ trợ DOS attributes

FileOwnerAttributeView
→ chủ sở hữu dưới dạng UserPrincipal
```

Không nên suy ra khả năng hỗ trợ chỉ từ tên hệ điều hành. Hãy hỏi hệ thống tệp/provider đang thực sự giữ tệp:

```java
FileStore store = Files.getFileStore(note);

boolean posixSupported =
        store.supportsFileAttributeView(PosixFileAttributeView.class);
boolean dosSupported =
        store.supportsFileAttributeView(DosFileAttributeView.class);
```

Nếu POSIX view được hỗ trợ, mã có thể đọc tập `PosixFilePermission`:

```java
if (Files.getFileStore(note)
        .supportsFileAttributeView(PosixFileAttributeView.class)) {

    Set<PosixFilePermission> permissions =
            Files.getPosixFilePermissions(note);

    System.out.println(permissions);
}
```

Các quyền POSIX biểu diễn `READ`, `WRITE`, `EXECUTE` cho **chủ sở hữu / nhóm / người khác**. Khi thật sự cần thay đổi quyền, `Files.setPosixFilePermissions(...)` ghi một tập quyền mới, nhưng thao tác có thể thất bại vì provider không hỗ trợ hoặc tiến trình không có quyền cần thiết. Vì vậy quyền truy cập là sự kết hợp giữa khả năng của hệ thống tệp và quyền của tiến trình, không chỉ là một enum trong Java.

Chủ sở hữu được biểu diễn bằng `UserPrincipal`:

```java
UserPrincipal owner = Files.getOwner(note);
System.out.println(owner.getName());
```

`Files.setOwner(...)` hoặc một `FileOwnerAttributeView` có thể thay chủ sở hữu khi provider hỗ trợ và hệ điều hành cho phép. Đổi chủ sở hữu thường là thao tác nhạy cảm về quyền hệ thống, nên mã không nên giả định bên gọi hiện tại luôn được phép thực hiện.

Symbolic link tiếp tục là một ranh giới quan trọng. Nếu gọi API thuộc tính theo mặc định, nhiều thao tác làm việc với đích của link. Khi muốn xem siêu dữ liệu của **chính mục link**, hãy dùng API/view chấp nhận `LinkOption.NOFOLLOW_LINKS`, ví dụ:

```java
PosixFileAttributeView linkView = Files.getFileAttributeView(
        linkPath,
        PosixFileAttributeView.class,
        LinkOption.NOFOLLOW_LINKS);
```

View có thể là `null` nếu provider không hỗ trợ loại thuộc tính đó. Mã đa nền tảng nên có phương án dự phòng hoặc yêu cầu khả năng rõ ràng thay vì giả định mọi hệ thống tệp đều có ngữ nghĩa POSIX, DOS, ACL hoặc chủ sở hữu giống nhau.

## <a id="directory-stream-walk">Liệt kê và duyệt thư mục</a>

Đọc một thư mục cũng là I/O. Với một cấp con trực tiếp, `Files.list` trả về `Stream<Path>`:

`Stream<Path>` ở đây là **`java.util.stream.Stream`**, tức Stream API để xử lý phần tử `Path`; nó không phải `InputStream`/`OutputStream`. Điểm đặc biệt là stream này được tạo từ một thao tác I/O và giữ tài nguyên thư mục ở bên dưới, nên dù là Stream API nó vẫn có vòng đời phải đóng.

~~~java
try (Stream<Path> entries = Files.list(tempDir)) {
    entries.forEach(System.out::println);
}
~~~

Stream này giữ tài nguyên hệ thống tệp trong quá trình duyệt nên phải đóng. Vì vậy nó nằm trong `try-with-resources`.

Khi muốn duyệt đệ quy:

~~~java
try (Stream<Path> paths = Files.walk(tempDir)) {
    paths
            .filter(Files::isRegularFile)
            .forEach(System.out::println);
}
~~~

`Files.walk` duyệt cả cây thư mục theo độ sâu được yêu cầu. Mặc định nó không đi theo symbolic link. Nếu bật `FileVisitOption.FOLLOW_LINKS`, cần chấp nhận khả năng gặp vòng liên kết và lỗi như `FileSystemLoopException`.

Nếu cần kiểm soát hành vi ở từng thư mục/tệp, xử lý lỗi riêng cho từng nhánh hoặc quyết định có tiếp tục sau lỗi hay không, `Files.walkFileTree` cùng `FileVisitor` phù hợp hơn.

`DirectoryStream<Path>` là một lựa chọn khác để lặp qua từng mục:

~~~java
try (DirectoryStream<Path> stream =
             Files.newDirectoryStream(tempDir, "*.txt")) {
    for (Path entry : stream) {
        System.out.println(entry);
    }
}
~~~

`DirectoryStream` cũng phải đóng. Với cây hoặc thư mục rất lớn, tránh gom mọi `Path` vào một `List` nếu có thể xử lý dần.

## <a id="watch-service-boundary">Theo dõi thay đổi hệ thống tệp với WatchService</a>

Đôi khi ứng dụng không chỉ muốn “đọc trạng thái hiện tại của thư mục” mà còn muốn biết **sau thời điểm đăng ký có mục nào được tạo, sửa hoặc xóa hay không**. `WatchService` là mức trừu tượng NIO.2 để nhận các sự kiện thay đổi hệ thống tệp kiểu này.

Mô hình:

```text
thư mục
   ↓ đăng ký
WatchService
   ↓ hệ thống tệp/provider báo thay đổi
WatchKey được báo hiệu
   ↓ pollEvents()
WatchEvent: tạo / sửa / xóa / tràn sự kiện
   ↓ xử lý xong
đặt lại WatchKey bằng reset()
```

Tạo đối tượng theo dõi và đăng ký một thư mục:

```java
try (WatchService watcher = tempDir.getFileSystem().newWatchService()) {
    tempDir.register(
            watcher,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE);

    WatchKey key = watcher.take(); // chặn cho tới khi có WatchKey được báo hiệu

    for (WatchEvent<?> event : key.pollEvents()) {
        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
            System.out.println("Có thể đã mất một hoặc nhiều sự kiện");
            continue;
        }

        Path relative = (Path) event.context();
        Path changed = tempDir.resolve(relative);
        System.out.println(event.kind().name() + ": " + changed);
    }

    boolean stillValid = key.reset();
    if (!stillValid) {
        System.out.println("Thư mục không còn được theo dõi hợp lệ");
    }
}
```

`take()` **chặn (block)** cho tới khi có một `WatchKey` sẵn sàng. `poll()` không chờ và trả `null` nếu chưa có `WatchKey`; phiên bản nạp chồng `poll(timeout, unit)` cho phép chờ có giới hạn. Đây là các cách phối hợp khác nhau, không phải sự kiện nào “chính xác hơn” sự kiện nào.

`WatchKey` đại diện cho một đăng ký đã được báo hiệu. Sau khi lấy các sự kiện bằng `pollEvents()`, mã phải gọi `reset()` để `WatchKey` có thể quay lại trạng thái chờ và được báo hiệu lần nữa. Nếu `reset()` trả `false`, đăng ký không còn hợp lệ, ví dụ thư mục đã bị xóa hoặc `WatchKey` bị hủy.

`WatchEvent.context()` cho sự kiện thư mục thường là đường dẫn **tương đối với thư mục đã đăng ký**, vì vậy mã ghép nó với thư mục gốc bằng `resolve(...)` khi cần đường dẫn đầy đủ. Một `WatchService` cũng **không tự theo dõi đệ quy**: đăng ký `root` không đồng nghĩa mọi thư mục con hiện tại/tương lai đều được theo dõi; bài toán đệ quy cần quản lý đăng ký cho từng thư mục phù hợp.

`OVERFLOW` là tín hiệu quan trọng: provider báo rằng sự kiện có thể đã bị mất hoặc bỏ qua. Nếu yêu cầu tính đúng đắn đòi hỏi trạng thái cuối phải chính xác, ứng dụng nên **quét lại hệ thống tệp** thay vì giả định danh sách sự kiện nhận được là lịch sử đầy đủ.

`WatchService` là mức trừu tượng trên hệ thống tệp/provider nên chi tiết quan sát **không hoàn toàn giống nhau giữa các nền tảng**. Sự kiện có thể được gộp, số lần `ENTRY_MODIFY` có thể khác nhau, đổi tên có thể xuất hiện như xóa + tạo, độ trễ khác nhau, và hệ thống tệp mạng/từ xa có thể có hành vi riêng. Hãy dùng cơ chế theo dõi này như thông báo “hệ thống tệp có thay đổi cần xử lý”, không như một nhật ký giao dịch tuyệt đối.

`WatchService` là tài nguyên phải đóng. Đóng nó giải phóng đăng ký/tài nguyên bên dưới; thread đang chờ trên `WatchService` có thể nhận `ClosedWatchServiceException`. Quyền sở hữu `WatchService` vì vậy cần rõ giống các tài nguyên I/O khác.

Đến đây `Path` cho ta địa chỉ và `Files` cho ta thao tác. Chương tiếp theo thay đổi góc nhìn: NIO tách **nơi chứa dữ liệu đang xử lý** (`Buffer`) khỏi **đường vận chuyển dữ liệu** (`Channel`).
