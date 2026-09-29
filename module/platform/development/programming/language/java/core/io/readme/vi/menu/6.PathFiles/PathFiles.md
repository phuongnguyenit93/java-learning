# Path và Files

Sau `java.io.File`, Java hiện đại thường làm việc với filesystem qua hai vai trò tách biệt: `Path` biểu diễn **đường dẫn**, còn `Files` thực hiện **thao tác I/O và metadata** trên đường dẫn đó. Cách tách này giúp ta trả lời hai câu hỏi độc lập: tài nguyên nằm ở đâu, và ta muốn làm gì với tài nguyên ấy?

Trong các ví dụ của chương này, ta dùng một thư mục tạm và một tệp UTF-8:

~~~java
Path tempDir = Files.createTempDirectory("io-demo-");
Path note = tempDir.resolve("note.txt");
String text = "Xin chào Java I/O";
~~~

## <a id="path-model">Mô hình Path</a>

`Path` là mô hình của một đường dẫn trong một filesystem. **Absolute path** bắt đầu từ root/điểm gốc đủ để xác định vị trí độc lập với thư mục làm việc hiện tại, ví dụ `C:\\work\\note.txt` hoặc `/tmp/note.txt`. **Relative path** như `data/note.txt` phải được hiểu tương đối so với một base path, thường là working directory hoặc một `Path` khác mà code chủ động `resolve(...)` vào.

Điểm quan trọng với người mới học là: **tạo một Path không đồng nghĩa với truy cập filesystem**. Đoạn mã sau chỉ tạo một đối tượng mô tả đường dẫn:

~~~java
Path path = Path.of("data", "note.txt");
~~~

Tệp `data/note.txt` có thể tồn tại hoặc chưa tồn tại. Chỉ khi gọi các API như `Files.readString(path)`, `Files.exists(path)` hay `Files.createFile(path)` thì chương trình mới yêu cầu filesystem thực hiện công việc tương ứng.

`Path` cũng không chỉ là một `String`. Nó biết các thành phần đường dẫn:

~~~java
Path path = Path.of("data", "reports", "2026", "summary.txt");

System.out.println(path.getFileName()); // summary.txt
System.out.println(path.getParent());   // data/reports/2026 theo cách hiển thị của filesystem
System.out.println(path.isAbsolute());  // false
~~~

Dấu phân cách và cách hiển thị cụ thể phụ thuộc filesystem. Vì vậy nên xây dựng đường dẫn bằng `Path.of(...)`, `resolve(...)` và các API của `Path` thay vì tự nối chuỗi bằng ký tự phân cách.

Mối quan hệ cần nhớ:

~~~text
Path
→ mô tả vị trí / tên đường dẫn

Files
→ thực hiện thao tác filesystem bằng Path
~~~

Chương trước dùng `File` để thấy mô hình API cũ. `Path`/`Files` là hướng nên ưu tiên cho mã Java hiện đại vì API rõ vai trò hơn, hỗ trợ nhiều thao tác hơn và biểu diễn lỗi bằng exception cụ thể hơn.

## <a id="filesystem-provider-model">FileSystem, FileSystemProvider và FileStore</a>

`Path` không tồn tại một mình. **Mỗi `Path` thuộc về một `FileSystem` cụ thể**. `FileSystem` mô tả namespace và quy tắc đường dẫn mà `Path` đang sống trong đó: root nào tồn tại, separator nào được dùng, file store nào khả dụng và những capability nào provider phía sau hỗ trợ.

Mental model nên nhớ:

~~~text
Path
→ thuộc về một FileSystem
→ FileSystem được triển khai/cung cấp bởi FileSystemProvider
→ dữ liệu thực tế nằm trên một hay nhiều FileStore
~~~

Trong hầu hết chương trình desktop/server thông thường, `Path.of(...)` dùng **default filesystem** của hệ điều hành. Nhưng NIO.2 không giới hạn `Path` vào filesystem mặc định: Java có thể làm việc với filesystem khác được cung cấp bởi provider, ví dụ ZIP/JAR filesystem. Điều này giải thích vì sao `Path` là abstraction rộng hơn một chuỗi đường dẫn Windows/Linux.

~~~java
Path path = Path.of("data", "note.txt");
FileSystem fs = path.getFileSystem();

System.out.println(fs.provider().getScheme());
System.out.println(fs.getSeparator());
~~~

`FileSystemProvider` là lớp implementation đứng sau các thao tác filesystem. Code ứng dụng thường **không gọi provider trực tiếp**; nó dùng `Path` và `Files`, còn provider thực hiện operation theo khả năng của filesystem đó. Đây là nguyên nhân chung cho nhiều portability boundary đã gặp trong chương này:

~~~text
ATOMIC_MOVE có thể không hỗ trợ
POSIX permission view có thể không tồn tại
WatchService có thể có delivery semantics khác nhau
symbolic-link behavior có thể khác
→ vì capability/semantics phụ thuộc filesystem + provider
~~~

`FileStore` đại diện cho backing storage/filesystem volume mà một path nằm trên đó. Nó cho phép hỏi thông tin kiểu dung lượng và attribute view mà storage hỗ trợ:

~~~java
FileStore store = Files.getFileStore(path);

long total = store.getTotalSpace();
long usable = store.getUsableSpace();

boolean posix =
        store.supportsFileAttributeView("posix");
~~~

Các giá trị dung lượng chỉ là snapshot tại thời điểm hỏi và không phải quota/transaction guarantee. Tương tự, việc một `FileStore` báo hỗ trợ một attribute view nói rằng capability tồn tại ở mức store/provider; operation cụ thể vẫn có thể thất bại vì quyền truy cập, trạng thái file hoặc race.

Một consequence quan trọng là **không phải mọi `Path` từ mọi provider đều có thể chuyển sang `java.io.File`**. `Path.toFile()` chỉ được hỗ trợ bởi default provider; với provider khác nó có thể ném `UnsupportedOperationException`. Vì vậy modern code nên giữ dữ liệu dưới dạng `Path` càng lâu càng tốt thay vì convert sang `File` chỉ vì thói quen.

Hai `Path` từ provider/filesystem không tương thích cũng không nên được trộn tùy ý. Những operation như `resolve`, `relativize` hoặc copy/move có contract/provider boundary riêng; khi thiết kế code reusable, hãy coi **filesystem identity** là một phần của context chứ không giả định mọi path đều cùng loại.

Phần này không yêu cầu người mới tự viết custom `FileSystemProvider`. Mục tiêu là hiểu kiến trúc: `Path` có owner (`FileSystem`), `Files` dispatch operation qua provider, và `FileStore` mô tả backing storage/capability. Mental model này nối lại các portability caveat trong toàn bộ chapter.

## <a id="resolve-normalize">resolve, normalize và relativize</a>

Ứng dụng thường có một thư mục gốc rồi cần tạo đường dẫn con. `resolve` diễn đạt đúng ý này:

~~~java
Path root = Path.of("workspace");
Path file = root.resolve("notes").resolve("today.txt");
~~~

Nếu đối số của `resolve` là một đường dẫn tuyệt đối, kết quả là chính đường dẫn tuyệt đối đó. Đây là hành vi cần nhớ khi một phần đường dẫn đến từ cấu hình hoặc input.

`normalize()` xử lý cấu trúc đường dẫn theo cú pháp, ví dụ loại `.` và các cặp `name/..` có thể rút gọn:

~~~java
Path raw = Path.of("data", ".", "reports", "..", "note.txt");
Path normalized = raw.normalize();

System.out.println(normalized); // data/note.txt
~~~

`normalize()` **không kiểm tra filesystem** và không giải quyết liên kết tượng trưng (symbolic link, thường gọi là symlink). Symbolic link là một entry trong filesystem dùng để trỏ tới một đường dẫn/target khác thay vì tự chứa nội dung của target. Đây là lý do `normalize()` khác `toRealPath()`: `toRealPath()` truy cập filesystem, yêu cầu đường dẫn tồn tại và trả về đường dẫn thực theo filesystem sau khi xử lý link theo tùy chọn.

Với symbolic link, một đường dẫn có thành phần `..` có thể mang ý nghĩa runtime khác với đường dẫn đã rút gọn thuần cú pháp. Vì vậy không nên xem `normalize()` là bằng chứng rằng hai đường dẫn chắc chắn trỏ cùng một tệp.

`relativize()` trả lời câu hỏi ngược lại: “từ A cần đi tương đối thế nào để tới B?”:

~~~java
Path base = Path.of("workspace", "docs");
Path target = Path.of("workspace", "images", "logo.png");

Path relative = base.relativize(target);
System.out.println(relative);
~~~

Hai đường dẫn phải tương thích, chẳng hạn cùng kiểu absolute/relative và cùng filesystem phù hợp. `relativize()` hữu ích khi lưu liên kết tương đối hoặc hiển thị đường dẫn ngắn, nhưng nó vẫn là phép toán trên đường dẫn, chưa đọc hay ghi dữ liệu.

## <a id="files-operations">Các thao tác với Files</a>

Khi đã có `Path`, lớp tiện ích `Files` cung cấp các thao tác filesystem phổ biến. Với tệp nhỏ và có giới hạn kích thước rõ ràng, Java 21 cho phép đọc/ghi rất trực tiếp:

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

Với copy/move, hành vi phải được nói rõ bằng option khi cần:

~~~java
Path copy = tempDir.resolve("note-copy.txt");
Files.copy(note, copy, StandardCopyOption.REPLACE_EXISTING);

Path moved = tempDir.resolve("archive.txt");
Files.move(copy, moved, StandardCopyOption.REPLACE_EXISTING);
~~~

`StandardCopyOption.ATOMIC_MOVE` có thể yêu cầu filesystem thực hiện move như một bước nguyên tử (atomic): các bên quan sát không nên thấy trạng thái trung gian kiểu “đã chuyển một phần nhưng chưa xong”. Không phải filesystem nào cũng hỗ trợ khả năng này. Nếu ứng dụng thực sự cần atomic move, phải xử lý `AtomicMoveNotSupportedException` thay vì giả định option luôn thành công.

Một vài semantics rất dễ bị hiểu sai:

- `Files.copy(source, target)` mặc định **đi theo symbolic link ở source** và copy target của link. Nếu truyền `LinkOption.NOFOLLOW_LINKS`, chính symbolic link được copy thay vì target.
- `Files.move(...)` di chuyển chính symbolic link nếu source là symlink; nó không “move target mà link đang trỏ tới”.
- `Files.copy(...)` với source là directory chỉ tạo directory đích tương ứng; nó **không recursive copy toàn bộ cây**. Muốn copy tree phải duyệt tree, ví dụ bằng `walkFileTree`.
- `Files.delete(...)` không xóa recursive directory tree; xóa directory không rỗng có thể thất bại với `DirectoryNotEmptyException`.
- Khi dùng `ATOMIC_MOVE`, các move option khác bị bỏ qua. Nếu target đã tồn tại, việc replace hay fail là implementation-specific; vì vậy không nên ghép `ATOMIC_MOVE` với giả định portable rằng `REPLACE_EXISTING` chắc chắn được tôn trọng.

`Files` cũng có các overload nhận `StandardOpenOption`:

~~~java
Files.writeString(
        note,
        System.lineSeparator() + "Dòng mới",
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND
);
~~~

Mental model ngắn cho các option phổ biến:

| Option | Ý nghĩa |
| --- | --- |
| `READ` | Mở để đọc |
| `WRITE` | Mở để ghi |
| `CREATE` | Tạo file nếu chưa có |
| `CREATE_NEW` | Chỉ tạo nếu chưa tồn tại; fail nếu đã có |
| `APPEND` | Ghi tiếp vào cuối file |
| `TRUNCATE_EXISTING` | Nếu file tồn tại và mở để ghi, cắt độ dài về 0 trước khi ghi |

Điểm quan trọng là option mô tả **contract mở file**, không chỉ là syntax. Ví dụ `CREATE_NEW` phù hợp khi ứng dụng cần “tạo mới hoặc fail”, còn `APPEND` phù hợp khi dữ liệu mới phải nối vào cuối thay vì ghi đè từ đầu.

Khi dữ liệu lớn, `readAllBytes` và `readString` không còn là lựa chọn tốt vì chúng nạp toàn bộ nội dung vào bộ nhớ. Lúc đó nên chuyển sang stream, reader/writer hoặc channel. Chương cuối sẽ gom các tiêu chí lựa chọn này thành một quyết định thống nhất.

## <a id="temporary-files">File và directory tạm</a>

Ứng dụng thường cần một nơi lưu **tạm thời** cho dữ liệu trung gian: upload đang xử lý, file giải nén, kết quả transform, cache ngắn hạn hoặc fixture của test. Tự ghép một tên như `tmp.txt` dễ đụng tên giữa nhiều thread/process và cũng khiến code phải tự quyết định thư mục nào là nơi tạm phù hợp. `Files.createTempFile(...)` và `Files.createTempDirectory(...)` giải quyết đúng bài toán này bằng cách **tạo thật** một file/directory mới với tên do filesystem provider làm cho đủ duy nhất trong bối cảnh tạo.

```java
Path tempFile = Files.createTempFile("report-", ".txt");
Path tempDir = Files.createTempDirectory("work-");
```

Các overload không truyền parent dùng **default temporary-file directory** của môi trường chạy. Khi ứng dụng muốn kiểm soát nơi đặt dữ liệu, truyền một directory rõ ràng:

```java
Path workspace = Path.of("work");
Files.createDirectories(workspace);

Path tempFile = Files.createTempFile(workspace, "upload-", ".bin");
Path tempDir = Files.createTempDirectory(workspace, "extract-");
```

Tạo `Path` chỉ tạo object mô tả đường dẫn; ngược lại, `createTempFile` và `createTempDirectory` là **filesystem operations** và resource tương ứng tồn tại ngay khi method trả về thành công.

Một hiểu lầm phổ biến là “temp” có nghĩa JVM sẽ tự dọn. **Không có bảo đảm như vậy.** `try-with-resources` đóng stream/channel đang mở nhưng không tự xóa file hoặc directory mà stream đó trỏ tới. Nếu code tạo temp resource thì code cũng cần xác định ownership và cleanup:

```java
Path temp = Files.createTempFile("job-", ".dat");

try {
    Files.writeString(temp, "intermediate data", StandardCharsets.UTF_8);
    process(temp); // helper của ứng dụng
} finally {
    Files.deleteIfExists(temp);
}
```

Với temporary directory, cần xóa nội dung bên trong trước rồi mới xóa directory; `Files.delete(tempDir)` không recursive. Đây là lý do tree-walking phía sau có liên quan trực tiếp tới cleanup của workspace tạm phức tạp.

Một số API mở file chấp nhận `StandardOpenOption.DELETE_ON_CLOSE`:

```java
Path temp = Files.createTempFile("session-", ".bin");

try (SeekableByteChannel channel = Files.newByteChannel(
        temp,
        StandardOpenOption.WRITE,
        StandardOpenOption.DELETE_ON_CLOSE)) {
    channel.write(ByteBuffer.wrap(new byte[] {1, 2, 3}));
}
```

Option này yêu cầu implementation thực hiện **best-effort delete** khi resource được đóng. Nó hữu ích cho một số file tạm gắn chặt với lifecycle của handle đang mở, nhưng không nên được coi là chiến lược cleanup duy nhất cho mọi môi trường: provider/platform có thể khác nhau, shutdown/crash bất thường không phải tình huống để ứng dụng dựa vào như một transaction bảo đảm. Khi cleanup là yêu cầu nghiệp vụ, ownership + `finally`/workflow dọn dẹp rõ ràng vẫn là mental model an toàn hơn.

## <a id="file-attributes">Thuộc tính và metadata của tệp</a>

Nội dung tệp là dữ liệu bên trong. **Metadata** mô tả tệp: kích thước, thời gian sửa đổi, loại tệp, quyền truy cập và các thuộc tính khác do filesystem cung cấp.

Các kiểm tra đơn giản:

~~~java
System.out.println(Files.exists(note));
System.out.println(Files.isRegularFile(note));
System.out.println(Files.isDirectory(note));
System.out.println(Files.size(note));
System.out.println(Files.getLastModifiedTime(note));
~~~

`Files.exists(path)` và `Files.notExists(path)` **không phải hai phép phủ định tuyệt đối của nhau**. Nếu filesystem không thể xác định trạng thái, cả hai có thể cùng trả `false`. Vì vậy:

~~~text
exists == true
→ đã xác nhận tồn tại tại thời điểm kiểm tra

notExists == true
→ đã xác nhận không tồn tại tại thời điểm kiểm tra

cả hai false
→ trạng thái chưa xác định được
~~~

Nếu câu hỏi thật sự là “hai `Path` này có trỏ tới cùng một file không?”, đừng suy ra chỉ từ chuỗi path hoặc `normalize()`. `Files.isSameFile(a, b)` hỏi filesystem về file identity và xử lý các trường hợp như symbolic link phù hợp hơn.

Khi cần lấy nhiều thuộc tính cùng lúc, dùng `readAttributes`:

~~~java
BasicFileAttributes attrs =
        Files.readAttributes(note, BasicFileAttributes.class);

System.out.println(attrs.size());
System.out.println(attrs.creationTime());
System.out.println(attrs.lastModifiedTime());
System.out.println(attrs.isRegularFile());
~~~

`BasicFileAttributes` là lớp nền có tính portable tương đối cao. Một số filesystem hỗ trợ view chuyên biệt như `PosixFileAttributes` hoặc DOS attributes. Mã dùng các view này phải chấp nhận rằng filesystem khác có thể không hỗ trợ.

Symbolic link cũng ảnh hưởng metadata. Nhiều API mặc định đi theo link tới target. Khi mục tiêu là hỏi về chính entry link, một số API cho phép `LinkOption.NOFOLLOW_LINKS`:

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

Filesystem có thể thay đổi giữa hai bước do thread hoặc process khác. Vì vậy hãy để thao tác thực tế quyết định thành công/thất bại và xử lý exception, thay vì coi kiểm tra trước đó là cam kết.

## <a id="file-permissions-ownership">Attribute views, quyền truy cập và owner</a>

`BasicFileAttributes` cố gắng cung cấp tập metadata cơ bản có tính portable tương đối cao. Nhưng filesystem còn có những nhóm metadata chuyên biệt. NIO.2 mô hình các nhóm này bằng **file attribute view**: một “góc nhìn” cho biết provider hỗ trợ đọc/ghi loại metadata nào.

```text
BasicFileAttributeView
→ size, timestamps, file type, fileKey...

PosixFileAttributeView
→ owner, group, rwx permissions trên filesystem kiểu POSIX

DosFileAttributeView
→ readonly, hidden, archive, system trên provider hỗ trợ DOS attributes

FileOwnerAttributeView
→ owner dưới dạng UserPrincipal
```

Không nên suy ra support chỉ từ tên hệ điều hành. Hãy hỏi filesystem/provider đang thực sự giữ file:

```java
FileStore store = Files.getFileStore(note);

boolean posixSupported =
        store.supportsFileAttributeView(PosixFileAttributeView.class);
boolean dosSupported =
        store.supportsFileAttributeView(DosFileAttributeView.class);
```

Nếu POSIX view được hỗ trợ, code có thể đọc tập `PosixFilePermission`:

```java
if (Files.getFileStore(note)
        .supportsFileAttributeView(PosixFileAttributeView.class)) {

    Set<PosixFilePermission> permissions =
            Files.getPosixFilePermissions(note);

    System.out.println(permissions);
}
```

Các permission POSIX biểu diễn quyền `READ`, `WRITE`, `EXECUTE` cho **owner / group / others**. Khi thật sự cần thay đổi quyền, `Files.setPosixFilePermissions(...)` ghi một tập permission mới, nhưng thao tác có thể thất bại vì provider không hỗ trợ hoặc process không có quyền cần thiết. Vì vậy permission là capability của filesystem + quyền của process, không chỉ là một enum trong Java.

Owner được biểu diễn bằng `UserPrincipal`:

```java
UserPrincipal owner = Files.getOwner(note);
System.out.println(owner.getName());
```

`Files.setOwner(...)` hoặc một `FileOwnerAttributeView` có thể thay owner khi provider hỗ trợ và hệ điều hành cho phép. Đổi owner thường là thao tác nhạy cảm về quyền hệ thống, nên code không nên giả định caller hiện tại luôn được phép thực hiện.

Symbolic link tiếp tục là một boundary quan trọng. Nếu gọi attribute API theo mặc định, nhiều operation làm việc với target của link. Khi muốn xem metadata của **chính link entry**, hãy dùng API/view chấp nhận `LinkOption.NOFOLLOW_LINKS`, ví dụ:

```java
PosixFileAttributeView linkView = Files.getFileAttributeView(
        linkPath,
        PosixFileAttributeView.class,
        LinkOption.NOFOLLOW_LINKS);
```

View có thể là `null` nếu provider không hỗ trợ loại attribute đó. Code portable nên có fallback hoặc giới hạn capability rõ ràng thay vì giả định mọi filesystem đều có POSIX, DOS, ACL hoặc owner semantics giống nhau.

## <a id="directory-stream-walk">Liệt kê và duyệt thư mục</a>

Đọc một thư mục cũng là I/O. Với một cấp con trực tiếp, `Files.list` trả về `Stream<Path>`:

`Stream<Path>` ở đây là **`java.util.stream.Stream`**, tức Stream API để xử lý phần tử `Path`; nó không phải `InputStream`/`OutputStream`. Điểm đặc biệt là stream này được tạo từ một I/O operation và giữ directory resource ở bên dưới, nên dù là Stream API nó vẫn có lifecycle phải đóng.

~~~java
try (Stream<Path> entries = Files.list(tempDir)) {
    entries.forEach(System.out::println);
}
~~~

Stream này giữ tài nguyên filesystem trong quá trình duyệt nên phải đóng. Vì vậy nó nằm trong `try-with-resources`.

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

`DirectoryStream<Path>` là một lựa chọn khác cho việc lặp từng entry:

~~~java
try (DirectoryStream<Path> stream =
             Files.newDirectoryStream(tempDir, "*.txt")) {
    for (Path entry : stream) {
        System.out.println(entry);
    }
}
~~~

`DirectoryStream` cũng phải đóng. Với cây hoặc thư mục rất lớn, tránh gom mọi `Path` vào một `List` nếu có thể xử lý dần.

## <a id="watch-service-boundary">Theo dõi thay đổi filesystem với WatchService</a>

Đôi khi ứng dụng không chỉ muốn “đọc trạng thái hiện tại của directory” mà còn muốn biết **sau thời điểm đăng ký có entry nào được tạo, sửa hoặc xóa hay không**. `WatchService` là abstraction NIO.2 cho việc nhận các filesystem change event kiểu này.

Mental model:

```text
directory
   ↓ register
WatchService
   ↓ filesystem/provider báo thay đổi
WatchKey được signal
   ↓ pollEvents()
WatchEvent: create / modify / delete / overflow
   ↓ xử lý xong
reset WatchKey
```

Tạo watcher và đăng ký một directory:

```java
try (WatchService watcher = tempDir.getFileSystem().newWatchService()) {
    tempDir.register(
            watcher,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE);

    WatchKey key = watcher.take(); // block cho tới khi có key được signal

    for (WatchEvent<?> event : key.pollEvents()) {
        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
            System.out.println("Có thể đã mất một hoặc nhiều event");
            continue;
        }

        Path relative = (Path) event.context();
        Path changed = tempDir.resolve(relative);
        System.out.println(event.kind().name() + ": " + changed);
    }

    boolean stillValid = key.reset();
    if (!stillValid) {
        System.out.println("Directory không còn được watch hợp lệ");
    }
}
```

`take()` **block** cho tới khi có một `WatchKey` sẵn sàng. `poll()` không chờ và trả `null` nếu chưa có key; overload `poll(timeout, unit)` cho phép chờ có giới hạn. Đây là lựa chọn coordination khác nhau, không phải event nào “chính xác hơn” event nào.

`WatchKey` đại diện cho một registration đã được signal. Sau khi lấy các event bằng `pollEvents()`, code phải gọi `reset()` để key có thể quay lại trạng thái chờ và được signal lần nữa. Nếu `reset()` trả `false`, registration không còn hợp lệ, ví dụ directory đã bị xóa hoặc key bị cancel.

`WatchEvent.context()` cho event directory thường là path **tương đối với directory đã register**, vì vậy code resolve nó với directory gốc khi cần đường dẫn đầy đủ. Một watcher cũng **không tự recursive**: đăng ký `root` không đồng nghĩa mọi subdirectory hiện tại/tương lai đều được watch; bài toán recursive cần quản lý registration cho từng directory phù hợp.

`OVERFLOW` là tín hiệu quan trọng: provider báo rằng event có thể đã bị mất hoặc bỏ qua. Nếu correctness yêu cầu trạng thái cuối phải chính xác, ứng dụng nên **rescan filesystem** thay vì giả định danh sách event nhận được là lịch sử đầy đủ.

`WatchService` là abstraction trên filesystem/provider nên chi tiết quan sát **không hoàn toàn portable**. Event có thể được gộp, số lần `ENTRY_MODIFY` có thể khác nhau, rename có thể xuất hiện như delete + create, độ trễ khác nhau, và network/remote filesystem có thể có hành vi riêng. Hãy dùng watcher như thông báo “filesystem có thay đổi cần xử lý”, không như một transaction log tuyệt đối.

`WatchService` là resource phải đóng. Đóng watcher giải phóng registration/resource bên dưới; thread đang chờ trên watcher có thể nhận `ClosedWatchServiceException`. Ownership của watcher vì vậy cần rõ giống các I/O resource khác.

Đến đây `Path` cho ta địa chỉ và `Files` cho ta thao tác. Chương tiếp theo thay đổi góc nhìn: NIO tách **nơi chứa dữ liệu đang xử lý** (`Buffer`) khỏi **đường vận chuyển dữ liệu** (`Channel`).
