<a id="back-to-top"></a>

# Liên kết tệp

## Menu
- [Symbolic link và hard link](#file-links-001)
- [Tạo, đọc và nhận biết symbolic link](#file-links-002)
- [Đường dẫn đích tương đối của symbolic link](#file-links-003)
- [Liên kết hỏng và khác biệt khi theo hoặc không theo link](#file-links-004)
- [NOFOLLOW_LINKS trong đọc thuộc tính và kiểm tra tệp](#file-links-005)
- [Files.isSameFile khác Path.equals](#file-links-006)
- [Thao tác copy, move, delete tác động vào link hay đích](#file-links-007)
- [Vòng liên kết và giới hạn khi duyệt cây](#file-links-008)
- [Quyền và tính hỗ trợ theo hệ điều hành](#file-links-009)

## <a id="file-links-001">Symbolic link và hard link</a>

<details>
<summary>Xem chi tiết</summary>

# Symbolic link và hard link trong Java NIO.2

## 1. Mục tiêu

Trong hệ thống tệp, **tên/đường dẫn của tệp** và **đối tượng dữ liệu được tên đó tham chiếu** là hai khái niệm cần phân biệt. Symbolic link và hard link cho phép nhiều đường dẫn liên hệ với một tệp, nhưng chúng làm điều đó theo hai cơ chế khác nhau:

- **Hard link (liên kết cứng)**: thêm một tên mới cho **cùng một đối tượng tệp**. Hai tên cùng dùng dữ liệu và metadata của đối tượng đó.
- **Symbolic link (symlink, liên kết tượng trưng)**: một đối tượng liên kết lưu **đường dẫn của mục tiêu**. Khi truy cập có theo liên kết, hệ điều hành/filesystem giải quyết đường dẫn này để tìm mục tiêu.

Java hỗ trợ các thao tác này qua `java.nio.file.Path`, `Files` và `LinkOption`. API xuất hiện từ Java 7; các ví dụ dùng Java 17+. Khả năng tạo liên kết thực tế còn phụ thuộc hệ điều hành, hệ thống tệp và quyền.

```text
Hard link:
  data.txt -----------+
                      +---- [cùng một đối tượng tệp, cùng dữ liệu]
  alias-hard.txt -----+

Symbolic link:
  alias-symbolic.txt --(chứa đường dẫn "data.txt")--> data.txt --> [đối tượng tệp]
```

Symlink không đồng nghĩa với tệp shortcut `.lnk` của Windows: đây là một cơ chế giải quyết đường dẫn của filesystem.

## 2. So sánh

| Tiêu chí | Hard link | Symbolic link |
|---|---|---|
| Bản chất | Một tên khác của cùng đối tượng tệp | Một đối tượng link chứa đường dẫn mục tiêu |
| Mục tiêu phải tồn tại khi tạo? | Có | Không nhất thiết; có thể tạo link bị đứt |
| Khi xóa tên mục tiêu ban đầu | Hard link khác vẫn truy cập được nội dung | Symlink có thể bị đứt vì tên mục tiêu biến mất |
| Khi ghi/sửa nội dung tệp tại chỗ | Mọi hard link thấy cùng nội dung mới | Symlink thấy nội dung mới nếu vẫn giải quyết tới tệp đó |
| Khi thay **đối tượng tệp** tại tên mục tiêu | Hard link cũ vẫn chỉ tới đối tượng cũ | Symlink thường theo tên tới đối tượng mới |
| Qua filesystem/volume khác | Thường không | Có thể nếu nền tảng hỗ trợ |
| Liên kết thư mục | Thường không cho phép tạo hard link thư mục | Thường có hỗ trợ, phụ thuộc quyền/hệ thống |
| Java tạo | `Files.createLink` | `Files.createSymbolicLink` |
| Java quan sát | `Files.isSameFile` kiểm tra hai path cùng tệp | `Files.isSymbolicLink` kiểm tra mục nhập link |

Trên Unix, mô hình thường được mô tả bằng **inode**: hai hard link cùng tham chiếu một inode. Đây là cách hiểu hữu ích, nhưng Java NIO.2 không bắt buộc mọi filesystem dùng inode. Khi đã tạo hard link, “tên gốc” và “tên mới” là **hai tên ngang hàng**.

## 3. Hard link: tên khác, cùng đối tượng tệp

Giả sử có `a.txt` và tạo `b.txt` là hard link tới `a.txt`:

1. Filesystem tạo mục nhập tên `b.txt` trỏ tới **cùng đối tượng** mà `a.txt` đang tham chiếu; không copy toàn bộ dữ liệu.
2. Đọc `a.txt` hoặc `b.txt` đều thấy nội dung như nhau. Nếu ghi nội dung mới **vào cùng đối tượng** thông qua một tên, nội dung đọc qua tên còn lại cũng thay đổi.
3. Xóa `a.txt` chỉ bỏ tên đó. `b.txt` vẫn tham chiếu đối tượng tệp và đọc được dữ liệu.
4. Trên filesystem kiểu POSIX, đối tượng thường được thu hồi khi không còn tên liên kết cứng và không còn handle đang mở giữ nó; chi tiết phụ thuộc hệ thống.

**Phân biệt sửa nội dung với thay tệp:** một chương trình có thể tạo tệp tạm rồi dùng `Files.move(temp, a, REPLACE_EXISTING)` để thay **đối tượng được tên `a.txt` trỏ tới**. Khi đó `b.txt` vẫn có thể tham chiếu đối tượng cũ. Vì vậy hard link **không** phải cách sao lưu độc lập hoặc đồng bộ mọi kiểu cập nhật tệp.

### API tạo hard link

```java
Path existing = Path.of("data.txt");
Path alias = Path.of("data-hard.txt");

Files.createLink(alias, existing);
boolean sameObject = Files.isSameFile(existing, alias);
```

Đây là **đoạn minh họa**, chưa phải chương trình độc lập. Giả định `data.txt` đã tồn tại, `data-hard.txt` chưa tồn tại, hai tên nằm trên cùng filesystem và filesystem cho phép hard link. Khi tạo thành công, `sameObject` là `true`. Phương thức `Files.isSameFile` kiểm tra hai đường dẫn cùng giải quyết tới một tệp, **không chuyên dùng để phát hiện hard link**: một symlink theo tới cùng tệp cũng có thể cho kết quả `true`.

## 4. Symbolic link: một đường dẫn tham chiếu đường dẫn khác

Symlink chứa một đường dẫn mục tiêu. Đường dẫn này có thể **tuyệt đối** hoặc **tương đối**.

```java
Path link = Path.of("logs", "current.log");
Path target = Path.of("2026-10.log");

Files.createSymbolicLink(link, target);
Path storedTarget = Files.readSymbolicLink(link);
```

Đoạn trên giả định thư mục `logs` tồn tại và hệ thống cho phép tạo symlink. Với target tương đối, `2026-10.log` được giải quyết **từ thư mục chứa symlink**, ở đây là `logs`, chứ không phải từ working directory khi chương trình truy cập. `readSymbolicLink` trả chính path lưu trong link (ở ví dụ là `2026-10.log`), không tự biến nó thành absolute/canonical path.

Symlink thường có thể trỏ tới **tệp** hoặc **thư mục**. Các thao tác đọc nội dung như `Files.readString(link)` theo symlink tới mục tiêu. Nếu đường dẫn mục tiêu không còn giải quyết được (bị xóa, đổi tên, di chuyển sai quan hệ thư mục), symlink trở thành **dangling/broken symlink**: liên kết vẫn là một mục nhập filesystem nhưng không đọc được mục tiêu.

Đường dẫn tương đối thuận tiện khi cần di chuyển cả cây thư mục mà vẫn giữ mối quan hệ giữa link và target. Đường dẫn tuyệt đối dễ hiểu tại một vị trí cố định, nhưng có thể đứt khi di chuyển dự án sang thư mục/máy khác.

### Phân biệt symlink tồn tại và mục tiêu tồn tại

```java
Files.isSymbolicLink(link);                    // Kiểm tra bản thân link
Files.exists(link);                            // Mặc định theo link tới mục tiêu
Files.exists(link, LinkOption.NOFOLLOW_LINKS); // Không theo link
Files.readSymbolicLink(link);                  // Lấy target path đã lưu
```

Với một symlink bị đứt, `Files.exists(link)` thường trả `false` nhưng `Files.exists(link, NOFOLLOW_LINKS)` trả `true`. Tuy nhiên `Files.exists` cũng có thể trả `false` khi không xác định được sự tồn tại do vấn đề truy cập; đây không phải phép kiểm tra đảm bảo thao tác I/O sau đó sẽ thành công.

## 5. Chương trình Java 17+ chạy được

Chương trình sau tạo thư mục tạm, ghi tệp, tạo hard link, thử tạo symlink rồi xóa một tên tệp. Nó minh họa sự khác biệt giữa **dữ liệu còn được hard link tham chiếu** và **mục tiêu của symlink đã biến mất**.

Lưu đúng tên tệp `FileLinksDemo.java`:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public class FileLinksDemo {
    public static void main(String[] args) throws IOException {
        Path folder = Files.createTempDirectory("file-links-");
        Path original = folder.resolve("original.txt");
        Path hard = folder.resolve("alias-hard.txt");
        Path symbolic = folder.resolve("alias-symbolic.txt");

        try {
            Files.writeString(original, "v1", StandardCharsets.UTF_8);
            Files.createLink(hard, original);
            System.out.println("Cùng tệp: "
                    + Files.isSameFile(original, hard));

            Files.writeString(hard, "v2", StandardCharsets.UTF_8);
            System.out.println("Đọc tên gốc: "
                    + Files.readString(original, StandardCharsets.UTF_8));

            boolean symbolicCreated = false;
            try {
                // Target tương đối: tính từ thư mục chứa symlink.
                Files.createSymbolicLink(symbolic, Path.of("original.txt"));
                symbolicCreated = true;
                System.out.println("Là symlink: "
                        + Files.isSymbolicLink(symbolic));
                System.out.println("Path lưu trong symlink: "
                        + Files.readSymbolicLink(symbolic));
            } catch (IOException | UnsupportedOperationException | SecurityException ex) {
                System.out.println("Không tạo được symbolic link trên hệ thống này.");
            }

            // Xóa tên original.txt, không xóa đối tượng mà hard link giữ.
            Files.delete(original);
            System.out.println("Đọc qua hard link: "
                    + Files.readString(hard, StandardCharsets.UTF_8));

            if (symbolicCreated) {
                System.out.println("Mục nhập symlink còn: "
                        + Files.exists(symbolic, LinkOption.NOFOLLOW_LINKS));
                System.out.println("Mục tiêu symlink còn: "
                        + Files.exists(symbolic));
            }
        } finally {
            Files.deleteIfExists(symbolic);
            Files.deleteIfExists(hard);
            Files.deleteIfExists(original);
            Files.deleteIfExists(folder);
        }
    }
}
```

Biên dịch và chạy ở thư mục chứa tệp:

```bash
javac FileLinksDemo.java
java FileLinksDemo
```

Nếu filesystem cho phép tạo hard link và symbolic link, stdout là:

```text
Cùng tệp: true
Đọc tên gốc: v2
Là symlink: true
Path lưu trong symlink: original.txt
Đọc qua hard link: v2
Mục nhập symlink còn: true
Mục tiêu symlink còn: false
```

Giải thích theo thứ tự:

- `Files.createLink` không nhân bản dữ liệu; `isSameFile` xác nhận hai tên giải quyết tới cùng tệp.
- Ghi `"v2"` qua `alias-hard.txt` làm thay đổi nội dung nhìn thấy qua `original.txt`.
- Symlink lưu path tương đối `original.txt`; `readSymbolicLink` cho ra đúng path này.
- Xóa `original.txt` chỉ xóa tên đó, nên `alias-hard.txt` vẫn đọc được `v2`.
- Symlink vẫn tồn tại như một mục nhập, nhưng target `original.txt` không còn, nên kiểm tra theo link thất bại.

Nếu không được phép tạo symbolic link, chương trình in dòng `Không tạo được symbolic link trên hệ thống này.` thay cho ba dòng thông tin lúc tạo symlink và hai dòng kiểm tra symlink cuối. Nếu `Files.createLink` không được hỗ trợ hoặc thiếu quyền, chương trình ném ngoại lệ trước phần symlink; điều đó cho thấy điều kiện môi trường phải được xử lý trong ứng dụng thực. Ví dụ dùng một thư mục tạm chung để tránh lỗi hard link qua nhiều filesystem.

## 6. Bảng API quan trọng

| API | Công dụng | Điểm cần nhớ |
|---|---|---|
| `Files.createLink(link, existing)` | Tạo hard link | `existing` cần tồn tại; link mới chưa tồn tại; thường cùng filesystem |
| `Files.createSymbolicLink(link, target)` | Tạo symlink lưu `target` | `target` có thể chưa tồn tại, nhưng hệ thống phải hỗ trợ |
| `Files.readSymbolicLink(link)` | Đọc path nằm trong symlink | Trả path được lưu, không tự giải quyết thành absolute path |
| `Files.isSymbolicLink(path)` | Kiểm tra mục nhập là symlink | Không đòi hỏi target còn tồn tại |
| `Files.isSameFile(a, b)` | Kiểm tra hai đường dẫn chỉ cùng tệp | Theo symlink khi giải quyết; có thể ném `IOException` |
| `Files.exists(path, NOFOLLOW_LINKS)` | Kiểm tra mục nhập không theo symlink | Hữu ích với dangling symlink |
| `Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW_LINKS)` | Đọc thuộc tính của bản thân symlink | Có thể xem `isSymbolicLink()` |
| `path.toRealPath()` | Đường dẫn thực, mặc định theo symlink | Yêu cầu giải quyết được đường dẫn, có thể lỗi I/O |
| `path.toRealPath(NOFOLLOW_LINKS)` | Đường dẫn thực không theo symlink | Khác `toAbsolutePath()`, vốn chủ yếu biến đổi cú pháp |

`NOFOLLOW_LINKS` ở đây là `LinkOption.NOFOLLOW_LINKS`. Muốn dùng `BasicFileAttributes`, import `java.nio.file.attribute.BasicFileAttributes`.

### Đọc metadata của bản thân link

```java
Path link = Path.of("alias-symbolic.txt");

// Mặc định theo symlink: thuộc tính của mục tiêu.
var targetInfo = Files.readAttributes(
        link, java.nio.file.attribute.BasicFileAttributes.class);

// Không theo symlink: thuộc tính của chính mục nhập symlink.
var linkInfo = Files.readAttributes(
        link, java.nio.file.attribute.BasicFileAttributes.class,
        LinkOption.NOFOLLOW_LINKS);
```

Đây là đoạn minh họa và cần link tồn tại trong môi trường chạy. Nếu mục tiêu đã biến mất, lời gọi đọc `targetInfo` có thể ném `NoSuchFileException`, còn lời gọi đọc `linkInfo` vẫn có thể thành công. Metadata cụ thể thay đổi theo filesystem/provider.

## 7. Copy, move, delete và duyệt cây thư mục

**Delete:** `Files.delete(symbolic)` xóa **mục nhập symlink**, không xóa mục tiêu được trỏ đến; `deleteIfExists` có cùng nguyên tắc. Khi xóa một hard link, chỉ tên đó biến mất; đối tượng vẫn còn nếu có tên/handle khác giữ.

**Move:** `Files.move(symbolic, dest)` di chuyển bản thân symlink. Nếu target lưu tương đối, thư mục mới của symlink có thể thay đổi ý nghĩa đường dẫn, khiến link bị đứt.

**Copy:** `Files.copy(symbolic, dest)` theo symlink để copy mục tiêu theo mặc định; thêm `LinkOption.NOFOLLOW_LINKS` nếu muốn copy chính đối tượng symlink (nếu provider hỗ trợ). Copy thông thường **không** có nghĩa là copy đệ quy toàn bộ cây thư mục. `Files.copy(hard, dest)` thường tạo một tệp mới độc lập có cùng dữ liệu, không tạo thêm hard link.

Ví dụ copy bản thân symlink (minh họa; nguồn và điều kiện filesystem phải hợp lệ):

```java
Files.copy(
        Path.of("alias-symbolic.txt"),
        Path.of("alias-symbolic-copy.txt"),
        LinkOption.NOFOLLOW_LINKS
);
```

**Duyệt thư mục:** `Files.walk(root)` và `Files.walkFileTree(root, visitor)` không đi sâu theo symbolic link tới thư mục theo mặc định. Với `FileVisitOption.FOLLOW_LINKS`, việc duyệt có thể gặp vòng lặp symlink; cần xử lý `FileSystemLoopException` hoặc lỗi I/O tương ứng. Stream trả về bởi `Files.walk` phải được đóng (thường qua try-with-resources).

## 8. Khi nào dùng loại nào?

- Chọn **symbolic link** khi muốn một đường dẫn bí danh linh hoạt, trỏ tới thư mục, hoặc chuyển đổi mục tiêu theo tên (ví dụ tên `current` dẫn đến một thư mục phát hành). Hãy cân nhắc target tương đối/tuyệt đối và rủi ro link đứt.
- Chọn **hard link** khi cần nhiều tên cho cùng **đối tượng tệp** trong một filesystem và muốn dữ liệu vẫn truy cập qua tên còn lại sau khi xóa một tên. Hãy nhớ cập nhật nội dung tại chỗ tác động tới cùng dữ liệu.
- Chọn **bản sao độc lập** khi cần hai dữ liệu có vòng đời riêng; hard link và symbolic link **không thay thế** backup độc lập.

## 9. Giới hạn hệ thống, lỗi thường gặp và an toàn

1. **Cross-filesystem:** hard link thường không thể vượt volume/filesystem. Trên Windows, hard link phổ biến với tệp trên NTFS trong cùng volume; hành vi phụ thuộc filesystem.
2. **Quyền symbolic link:** Windows có thể cần quyền/chế độ hệ thống phù hợp (chẳng hạn Developer Mode), còn Unix cần quyền tại thư mục liên quan. Không giả định một ví dụ link sẽ chạy trên mọi máy.
3. **Link đã tồn tại:** `createLink` / `createSymbolicLink` không tự ghi đè tên cũ; có thể ném `FileAlreadyExistsException`. Không xóa tên có sẵn chỉ để thử lại khi chưa kiểm tra quyền sở hữu dữ liệu.
4. **Đích chưa tồn tại:** hard link cần đối tượng đích hiện hữu; symlink có thể trỏ tới path chưa có, rồi trở thành dangling link. Đọc nội dung theo link đứt thường gây lỗi I/O.
5. **Nhầm cơ sở đường dẫn:** target tương đối của symlink được tính từ **thư mục chứa link**, không từ current working directory của ứng dụng đọc nó.
6. **Chu trình symlink:** link thư mục có thể quay về thư mục tổ tiên, hoặc A → B → A. Theo link khi duyệt cây cần xử lý chu trình để tránh lỗi/vòng lặp.
7. **Race condition/TOCTOU:** giữa `Files.exists`, `isSymbolicLink` và thao tác `read/write/delete`, một tiến trình khác có thể thay link hoặc target. Một lần kiểm tra tồn tại **không đảm bảo** thao tác sau đó an toàn. Đối với path không tin cậy, cần kiểm soát thư mục được phép truy cập, quyền và API thao tác phù hợp.
8. **Ngoại lệ:** `UnsupportedOperationException`, `IOException` (ví dụ `AccessDeniedException`, `NoSuchFileException` hoặc `FileSystemException`) và `SecurityException` có thể xuất hiện tùy API và môi trường. Phải xử lý phù hợp với yêu cầu ứng dụng, không im lặng nuốt mọi lỗi.

Ba câu hỏi dễ nhầm: `isSymbolicLink(path)` hỏi **bản thân tên này có phải symlink không**; `exists(path)` thường hỏi **đường dẫn này giải quyết tới mục tiêu tồn tại không**; `isSameFile(a,b)` hỏi **hai đường dẫn cùng giải quyết tới một tệp không**. Chúng có thể cho các kết quả khác nhau đối với cùng một link.

## 10. Bài tập tư duy và lời giải

**A.** Tạo hard link `b.txt` tới `a.txt` rồi xóa `a.txt`. `b.txt` có đọc được không? **Có**, khi tạo hard link thành công; xóa một tên không xóa đối tượng còn được tên kia tham chiếu.

**B.** Tạo symbolic link `b.txt` chứa path `a.txt` rồi xóa `a.txt`. Vì sao `Files.exists(b)` có thể là `false` còn `Files.exists(b, NOFOLLOW_LINKS)` là `true`? **Vì** cách thứ nhất theo tới mục tiêu đã mất, cách thứ hai kiểm tra bản thân mục nhập link.

**C.** Dùng một tệp tạm để **thay thế đối tượng** tại `a.txt`. Hard link `b.txt` vốn cùng đối tượng với `a.txt` có nhất thiết thấy đối tượng mới không? **Không**; hard link vẫn gắn với đối tượng cũ. Symlink lưu đường dẫn `a.txt` thường theo đến đối tượng mới tại đường dẫn đó.

**D.** Muốn `app/current` là symlink tới `app/releases/v3` và dùng target tương đối, lưu path nào? **`releases/v3`**, vì target được giải quyết từ thư mục chứa symlink, tức `app`.

**E.** Hard link có phải backup không? **Không**, vì các hard link cùng chia sẻ đối tượng dữ liệu và sửa nội dung qua một tên có thể ảnh hưởng toàn bộ tên kia. Backup cần bản sao/snapshot có vòng đời thích hợp.

## 11. Ghi nhớ

**Hard link = tên khác của cùng một đối tượng tệp. Symbolic link = đối tượng chứa đường dẫn tới mục tiêu.** Trong Java NIO.2, phân biệt `createLink`, `createSymbolicLink`, `readSymbolicLink`, `isSymbolicLink`, `isSameFile` và `NOFOLLOW_LINKS`; luôn tính đến quyền truy cập, filesystem và khả năng mục tiêu thay đổi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-links-002">Tạo, đọc và nhận biết symbolic link</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo, đọc và nhận biết symbolic link trong Java NIO.2

## 1. Symbolic link là gì?

**Symbolic link (liên kết tượng trưng, symlink)** là một mục trong hệ thống tệp chứa đường dẫn tham chiếu đến một tệp hoặc thư mục khác. Khi chương trình mở liên kết để đọc nội dung, hệ điều hành thường phân giải liên kết và truy cập đối tượng đích. Một liên kết có thể trỏ tới tệp, thư mục hoặc tới một liên kết khác.

Ví dụ, thư mục `docs` chứa `guide.txt` và `latest.txt` là symlink có nội dung đích `guide.txt`:

~~~text
docs/
├── guide.txt
└── latest.txt -> guide.txt
~~~

Ở ví dụ này, `latest.txt` vẫn là một mục riêng trong hệ thống tệp. Nó không chứa bản sao nội dung của `guide.txt`. Xóa `latest.txt` thông thường chỉ xóa liên kết; đổi tên hoặc xóa `guide.txt` có thể khiến liên kết không còn tìm thấy đích.

Phân biệt ngắn gọn:

| Loại | Đặc điểm |
| --- | --- |
| Tệp thông thường | Chứa dữ liệu thực tế của tệp. |
| Symbolic link | Chứa đường dẫn đến đối tượng đích; có thể trỏ qua thư mục hoặc tới đích chưa tồn tại. |
| Hard link | Là một tên khác tham chiếu cùng đối tượng tệp trên hệ thống tệp hỗ trợ; không lưu một đường dẫn đích theo cách symlink làm. |

Các API hiện đại để làm việc với symbolic link thuộc `java.nio.file`, chủ yếu là `Path`, `Files` và `LinkOption`.

## 2. Ba thao tác cốt lõi

| Mục đích | API | Kết quả và điểm cần nhớ |
| --- | --- | --- |
| Tạo liên kết | `Files.createSymbolicLink(link, target)` | Tạo mục `link` chứa đường dẫn `target`. Không tạo bản sao dữ liệu. |
| Đọc đường dẫn đích được lưu | `Files.readSymbolicLink(link)` | Trả về một `Path`; không đọc nội dung tệp đích và không tự chuyển thành đường dẫn tuyệt đối. |
| Nhận biết một đường dẫn có phải symlink | `Files.isSymbolicLink(path)` | Trả về `true` khi chính mục được chỉ ra là symbolic link, kể cả khi đích đã mất. |

Các tham số `link` và `target` đều là `Path`. Với đường dẫn đích tương đối, việc phân giải được hiểu **tương đối từ thư mục cha của chính liên kết**, không phải nhất thiết từ thư mục làm việc hiện tại.

Ví dụ:

~~~java
Path link = Path.of("/app/aliases/current.txt");
Path target = Path.of("../data/report.txt");
Files.createSymbolicLink(link, target);
~~~

Liên kết tại `/app/aliases/current.txt` tham chiếu `/app/data/report.txt` khi được phân giải. Ví dụ đường dẫn kiểu Unix này minh họa quy tắc; trên Windows dùng đường dẫn phù hợp với hệ thống tệp đang chạy.

**Quan trọng:** `Files.createSymbolicLink` không bắt buộc đối tượng đích đã tồn tại tại thời điểm tạo. Vì vậy, tạo thành công chưa bảo đảm đọc nội dung qua liên kết sẽ thành công.

## 3. Ví dụ hoàn chỉnh: tạo, đọc và nhận biết liên kết

Lưu mã dưới đây thành `SymbolicLinkDemo.java`. Chương trình tạo thư mục tạm, ghi tệp thật, tạo liên kết tương đối, kiểm tra trạng thái rồi xóa tệp thật để quan sát một **dangling/broken symbolic link**. Khối `finally` dọn các mục được ví dụ tạo ra.

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public class SymbolicLinkDemo {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("symbolic-link-demo-");
        Path realFile = dir.resolve("data.txt");
        Path link = dir.resolve("shortcut.txt");

        try {
            Files.writeString(realFile, "Java NIO");

            // "data.txt" được phân giải từ thư mục chứa shortcut.txt.
            Files.createSymbolicLink(link, Path.of("data.txt"));

            System.out.println("isSymbolicLink=" + Files.isSymbolicLink(link));
            System.out.println("storedTarget=" + Files.readSymbolicLink(link));
            System.out.println("content=" + Files.readString(link));
            System.out.println("existsFollow=" + Files.exists(link));
            System.out.println("existsNoFollow=" +
                    Files.exists(link, LinkOption.NOFOLLOW_LINKS));

            // Xóa đích, nhưng vẫn giữ mục symbolic link.
            Files.delete(realFile);
            System.out.println("afterDeleteIsLink=" + Files.isSymbolicLink(link));
            System.out.println("afterDeleteFollow=" + Files.exists(link));
            System.out.println("afterDeleteNoFollow=" +
                    Files.exists(link, LinkOption.NOFOLLOW_LINKS));
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(realFile);
            Files.deleteIfExists(dir);
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac SymbolicLinkDemo.java
java SymbolicLinkDemo
~~~

Khi hệ thống tệp và quyền người dùng cho phép tạo symbolic link, kết quả là:

~~~text
isSymbolicLink=true
storedTarget=data.txt
content=Java NIO
existsFollow=true
existsNoFollow=true
afterDeleteIsLink=true
afterDeleteFollow=false
afterDeleteNoFollow=true
~~~

Giải thích từng nhóm kết quả:

1. `isSymbolicLink=true`: bản thân `shortcut.txt` là liên kết tượng trưng.
2. `storedTarget=data.txt`: `readSymbolicLink` trả về đường dẫn tương đối **đã lưu**. Nó không trả về nội dung `Java NIO`.
3. `content=Java NIO`: `Files.readString(link)` mặc định đi theo liên kết để đọc dữ liệu của `data.txt`.
4. Trước khi xóa đích, kiểm tra `Files.exists` cả khi đi theo và không đi theo liên kết đều trả về `true`.
5. Sau khi xóa đích, liên kết vẫn tồn tại nên `isSymbolicLink` và `exists(..., NOFOLLOW_LINKS)` vẫn là `true`. Ngược lại, `exists(link)` mặc định đi theo đích, không tìm thấy nó và trả về `false`.

Nếu môi trường không hỗ trợ symbolic link hoặc không có quyền tạo, lệnh `createSymbolicLink` có thể ném ngoại lệ; đó là hạn chế của môi trường, không phải một biến thể của kết quả hiển thị ở trên.

## 4. Đường dẫn tương đối, tuyệt đối và đường dẫn thực

`Files.readSymbolicLink(link)` đọc **đường dẫn đích được ghi trong liên kết**, có thể là tương đối hoặc tuyệt đối. Nếu cần xác định đường dẫn mà một target tương đối ám chỉ theo vị trí liên kết, hãy xử lý rõ ràng:

~~~java
Path storedTarget = Files.readSymbolicLink(link);
Path referencedPath = storedTarget.isAbsolute()
        ? storedTarget
        : link.getParent().resolve(storedTarget).normalize();
~~~

Đây là phép **xây dựng/chuẩn hóa đường dẫn theo cú pháp**. `normalize()` xử lý các thành phần như `.` và `..`; nó không bảo đảm đối tượng tồn tại và không thay thế việc kiểm tra liên kết hoặc quyền truy cập thực tế.

Nếu muốn phân giải đến một đối tượng hiện có:

~~~java
Path actualPath = link.toRealPath();
~~~

`toRealPath()` mặc định phân giải symbolic link và yêu cầu đường dẫn có thể được truy cập, nên có thể ném `IOException` khi liên kết hỏng, mục đích không tồn tại hoặc quyền truy cập không cho phép. Truyền `LinkOption.NOFOLLOW_LINKS` làm thay đổi việc theo liên kết; không dùng tùy tiện nếu mục đích là tìm tệp đích thực sự.

Nên chọn target tương đối khi một nhóm tệp sẽ được di chuyển cùng nhau mà vẫn muốn quan hệ tham chiếu giữ nguyên. Target tuyệt đối thuận tiện khi vị trí đích được cố định, nhưng dễ hỏng khi di chuyển dự án sang máy khác.

## 5. Đi theo liên kết hay kiểm tra chính liên kết?

Đây là khác biệt dễ gây lỗi nhất trong các thao tác I/O.

| Biểu thức | Khi `link` trỏ tới tệp đang tồn tại | Khi `link` là symlink nhưng đích đã mất |
| --- | --- | --- |
| `Files.isSymbolicLink(link)` | `true` | `true` |
| `Files.exists(link)` | `true` | `false` trong trường hợp thông thường |
| `Files.exists(link, NOFOLLOW_LINKS)` | `true` | `true` nếu chính liên kết vẫn tồn tại và có thể kiểm tra |
| `Files.isRegularFile(link)` | `true` nếu đích là tệp thường | `false` |
| `Files.isRegularFile(link, NOFOLLOW_LINKS)` | `false` vì mục được kiểm tra là liên kết | `false` |
| `Files.readSymbolicLink(link)` | Trả đường dẫn được lưu | Vẫn trả đường dẫn được lưu |
| `Files.readString(link)` | Đọc nội dung đích | Thường ném `NoSuchFileException` |

`LinkOption.NOFOLLOW_LINKS` có nghĩa là thao tác hỗ trợ tùy chọn này sẽ xem chính liên kết thay vì tự đi theo nó. Nó không phải một cấu hình toàn cục của `Files`; từng phương thức có tập tham số và hành vi riêng.

Khi cần đọc metadata về bản thân liên kết:

~~~java
import java.nio.file.attribute.BasicFileAttributes;

BasicFileAttributes attrs = Files.readAttributes(
        link, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
System.out.println(attrs.isSymbolicLink());
~~~

Đoạn mã này là **mảnh minh họa**, sử dụng biến `link` đã có trong chương trình; nếu liên kết không tồn tại hoặc không thể đọc metadata, `readAttributes` có thể ném `IOException`.

Chú ý: `Files.exists(path) == false` **không tự chứng minh** mục hoàn toàn không có; một số lỗi I/O hoặc quyền truy cập cũng khiến API không xác định được sự tồn tại. Khi cần xử lý lỗi chính xác, thực hiện thao tác mong muốn và bắt ngoại lệ phù hợp thay vì phụ thuộc hoàn toàn vào một phép kiểm tra tồn tại trước đó.

## 6. Lỗi thường gặp và cách xử lý

- **Dùng đường dẫn tương đối sai gốc:** `Path.of("data.txt")` lưu target tương đối với thư mục chứa link. Nếu link nằm trong thư mục khác, phải chọn số lượng `..` hoặc cấu trúc thư mục phù hợp.
- **Nhầm đọc symlink với đọc nội dung:** `Files.readSymbolicLink` trả `Path` được lưu; `Files.readString`, `Files.newInputStream` và các thao tác mở tệp thông thường có thể đi theo liên kết.
- **Dùng `Files.exists(link)` để khẳng định link không còn:** một broken link vẫn còn mục liên kết. Kiểm tra `Files.isSymbolicLink(link)` hoặc `Files.exists(link, NOFOLLOW_LINKS)` khi muốn hỏi về chính mục đó.
- **Tạo link tại tên đã tồn tại:** `Files.createSymbolicLink` không ghi đè đường dẫn `link` có sẵn; thường ném `FileAlreadyExistsException`. Hãy chọn tên khác hoặc xử lý trạng thái hiện có có chủ đích.
- **Giả định mọi môi trường hỗ trợ:** hệ thống tệp, quyền truy cập, cấu hình Windows và chính sách bảo mật có thể chặn tạo liên kết; `UnsupportedOperationException` hoặc `IOException` (ví dụ `FileSystemException`) có thể xảy ra. Trên một số cấu hình Windows, tạo symlink cần quyền thích hợp hoặc Developer Mode; hãy kiểm tra chính sách máy thực tế.
- **Gọi `readSymbolicLink` trên tệp thường:** phương thức có thể ném `NotLinkException`. Kiểm tra loại mục trước chỉ giúp giao diện rõ ràng hơn, nhưng không loại bỏ khả năng thay đổi đồng thời giữa kiểm tra và thao tác.
- **Vô tình xóa hoặc sao chép sai đối tượng:** `Files.delete(link)` xóa chính mục symlink, không xóa đích; `Files.copy(link, destination)` mặc định đi theo link để sao chép đối tượng đích, còn dùng `NOFOLLOW_LINKS` thì yêu cầu sao chép chính liên kết (nếu provider hỗ trợ). `Files.move` khi nguồn là symlink di chuyển mục liên kết.
- **Quên xử lý liên kết vòng:** khi duyệt cây bằng `Files.walkFileTree`, mặc định không đi theo symlink; bật `FileVisitOption.FOLLOW_LINKS` có thể gặp vòng liên kết và `FileSystemLoopException`. Chỉ bật khi thực sự cần duyệt theo đích.

## 7. Lưu ý an toàn khi thao tác trên đường dẫn không tin cậy

Symbolic link có thể hướng một thao tác ghi/đọc đến vị trí ngoài thư mục bạn dự định xử lý. Nếu ứng dụng nhận tên tệp từ người dùng hoặc làm việc trong thư mục có thể bị tiến trình khác sửa, cần thiết kế quy tắc đường dẫn và quyền truy cập rõ ràng. Chỉ ghép chuỗi đường dẫn hoặc gọi `normalize()` không đủ để bảo đảm đường dẫn cuối vẫn nằm trong vùng cho phép, vì symlink có thể thay đổi điểm đến thực tế.

Đặc biệt, chuỗi thao tác “kiểm tra không phải symlink → mở/ghi tệp” có thể gặp **race condition** nếu một tác nhân khác thay mục đó giữa hai bước (TOCTOU). Với dữ liệu nhạy cảm, cân nhắc các thao tác gắn với thư mục an toàn như `SecureDirectoryStream` khi provider hỗ trợ, phân quyền hệ thống tệp và cơ chế mở tệp phù hợp. Không coi một lần gọi `isSymbolicLink` là biện pháp bảo vệ hoàn chỉnh.

## 8. Tự kiểm tra kiến thức

1. Nếu `docs/current.txt` lưu target `../backup/report.txt`, đích được diễn giải tương đối từ thư mục nào? **Từ thư mục `docs` chứa `current.txt`**, nên đường dẫn tương ứng theo cú pháp là `backup/report.txt` ở cùng cấp với `docs`.
2. Một symlink còn trên đĩa nhưng đích đã bị xóa: API nào nhận biết vẫn có liên kết? **`Files.isSymbolicLink(link)`** hoặc **`Files.exists(link, LinkOption.NOFOLLOW_LINKS)`** khi có thể truy cập metadata.
3. `Files.readSymbolicLink(link)` có đọc nội dung tệp đích không? **Không.** Nó trả về `Path` của target được lưu trong liên kết.
4. Muốn đọc nội dung qua link đang hợp lệ thì dùng gì? **Một thao tác đọc nội dung như `Files.readString(link)`**; theo mặc định thao tác này sẽ đi theo liên kết đến đích.
5. Tại sao cùng một link, `Files.exists(link)` có thể là `false` nhưng `Files.exists(link, NOFOLLOW_LINKS)` là `true`? **Vì phép kiểm tra thứ nhất đi theo đích đã mất, còn phép thứ hai kiểm tra bản thân mục liên kết còn tồn tại.**

**Ghi nhớ:** `createSymbolicLink` tạo đường dẫn tham chiếu, `readSymbolicLink` đọc target được lưu, `isSymbolicLink` nhận diện mục liên kết. Khi kiểm tra sự tồn tại hoặc metadata, hãy quyết định rõ bạn đang quan tâm **bản thân liên kết** hay **đối tượng đích**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-links-003">Đường dẫn đích tương đối của symbolic link</a>

<details>
<summary>Xem chi tiết</summary>

# Đường dẫn đích tương đối của symbolic link trong Java NIO.2

## 1. Mục tiêu và bối cảnh

**Symbolic link (liên kết tượng trưng, hay symlink)** là một mục trong hệ thống tệp chứa đường dẫn trỏ đến tệp hoặc thư mục khác. Khi chương trình mở symlink để đọc nội dung, hệ điều hành thường lần theo đường dẫn đó đến đối tượng đích.

Một symlink có thể lưu **đường dẫn tuyệt đối** hoặc **đường dẫn tương đối**. Bài này tập trung vào câu hỏi: *Nếu đích là một đường dẫn tương đối, nó được hiểu tương đối với đâu, và Java NIO.2 tạo/đọc/xử lý đường dẫn đó như thế nào?*

Sau bài học, bạn có thể:

- Xác định đúng đích của một symlink chứa `..` hoặc các thành phần đường dẫn tương đối.
- Tạo và kiểm tra symlink bằng `java.nio.file.Files` và `Path`.
- Phân biệt đường dẫn **được lưu trong symlink**, đường dẫn **đã ghép với thư mục chứa symlink**, và đường dẫn **thực** trên hệ thống tệp.
- Hiểu vì sao di chuyển symlink có thể làm nó mất đích, và xử lý các trường hợp symlink bị đứt.

## 2. Quy tắc cốt lõi: tương đối với thư mục chứa chính symlink

Giả sử có cây thư mục:

~~~text
workspace/
├── data/
│   └── report.txt
└── links/
    └── latest.txt -> ../data/report.txt
~~~

`latest.txt` là symlink nằm trong `workspace/links/`. Bên trong nó lưu chuỗi đường dẫn tương đối `../data/report.txt`.

Hệ điều hành diễn giải mục tiêu theo quy tắc:

~~~text
thư_mục_chứa_symlink / đường_dẫn_đích_đã_lưu
= workspace/links/../data/report.txt
= workspace/data/report.txt
~~~

**Điểm quyết định:** Đích tương đối được giải quyết dựa trên **thư mục cha của symlink** (`workspace/links/`), **không dựa trên thư mục làm việc hiện tại** (`user.dir`) của chương trình Java. Đích không được diễn giải tương đối với chính `latest.txt` như thể symlink là một thư mục.

Cần phân biệt hai đường dẫn độc lập:

| Thành phần | Ví dụ | Vai trò |
| --- | --- | --- |
| Đường dẫn đến symlink | `workspace/links/latest.txt` | Xác định *mục liên kết* được tạo hoặc truy cập |
| Đường dẫn đích ghi trong symlink | `../data/report.txt` | Xác định *đối tượng đích* tương đối với thư mục chứa liên kết |
| Đường dẫn sau khi ghép và chuẩn hóa | `workspace/data/report.txt` | Vị trí dự kiến sau khi xử lý `..`, chưa đồng nghĩa với việc tệp chắc chắn tồn tại |

Đường dẫn **đến symlink** có thể là tuyệt đối dù **đường dẫn đích** vẫn tương đối. Một symlink cũng có thể trỏ đến thư mục thay vì tệp; cùng quy tắc về thư mục cha được áp dụng.

## 3. Tạo symlink với đường dẫn đích tương đối bằng Java 17+

Trong Java NIO.2, lời gọi chính là:

~~~java
Path link = linksDirectory.resolve("latest.txt");
Path relativeTarget = Path.of("..", "data", "report.txt");
Files.createSymbolicLink(link, relativeTarget);
~~~

- Tham số `link` là nơi tạo **mục symlink**.
- Tham số `relativeTarget` là đường dẫn **được lưu làm đích**. Vì `relativeTarget.isAbsolute()` bằng `false`, đích sẽ được hiểu tương đối với thư mục chứa `link`.
- `Path.of("..", "data", "report.txt")` dùng cách ghép tên theo hệ thống tệp hiện hành, tránh tự viết dấu phân cách theo một hệ điều hành cố định.
- `Files.createSymbolicLink` không tự sửa đường dẫn tương đối thành tuyệt đối. Nó có thể tạo liên kết đến đích chưa tồn tại trên các hệ thống hỗ trợ; không được dùng việc tạo thành công để kết luận rằng đích tồn tại.

### Ví dụ hoàn chỉnh, chạy độc lập

Lưu mã dưới tên `RelativeSymlinkDemo.java`:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileSystems;

public class RelativeSymlinkDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("relative-symlink-");
        Path dataDir = root.resolve("data");
        Path linksDir = root.resolve("links");
        Path targetFile = dataDir.resolve("report.txt");
        Path link = linksDir.resolve("latest.txt");

        try {
            Files.createDirectories(dataDir);
            Files.createDirectories(linksDir);
            Files.writeString(targetFile, "Xin chào NIO.2", StandardCharsets.UTF_8);

            // Đường dẫn đích được lưu tương đối với linksDir.
            Path relativeTarget = Path.of("..", "data", "report.txt");
            Files.createSymbolicLink(link, relativeTarget);

            // Đọc đúng đường dẫn đích đã lưu, chưa lần theo symlink.
            Path storedTarget = Files.readSymbolicLink(link);

            // Ghép với thư mục cha của symlink để suy ra vị trí đích.
            Path resolved = link.getParent().resolve(storedTarget).normalize();

            String portableTarget = storedTarget.toString()
                    .replace(FileSystems.getDefault().getSeparator(), "/");

            System.out.println("Stored target: " + portableTarget);
            System.out.println("Target is relative: " + !storedTarget.isAbsolute());
            System.out.println("Resolved matches file: " + resolved.equals(targetFile));
            System.out.println("Content through link: "
                    + Files.readString(link, StandardCharsets.UTF_8));
            System.out.println("Is symbolic link: " + Files.isSymbolicLink(link));
        } finally {
            // Xóa liên kết, rồi tệp đích và các thư mục đã tạo.
            Files.deleteIfExists(link);
            Files.deleteIfExists(targetFile);
            Files.deleteIfExists(linksDir);
            Files.deleteIfExists(dataDir);
            Files.deleteIfExists(root);
        }
    }
}
~~~

Biên dịch và chạy tại thư mục chứa tệp Java:

~~~bash
javac -encoding UTF-8 RelativeSymlinkDemo.java
java RelativeSymlinkDemo
~~~

Với hệ thống tệp hỗ trợ symlink và tài khoản có quyền tạo symlink, stdout là:

~~~text
Stored target: ../data/report.txt
Target is relative: true
Resolved matches file: true
Content through link: Xin chào NIO.2
Is symbolic link: true
~~~

**Giải thích kết quả:**

1. `Files.readSymbolicLink(link)` trả về `Path` biểu diễn đích được lưu (`../data/report.txt`), không đọc nội dung `report.txt` và không tự chuyển thành đường dẫn tuyệt đối.
2. `link.getParent().resolve(storedTarget).normalize()` ghép đích tương đối với `linksDir` và rút gọn `links/..` về `data/report.txt`. Ví dụ dùng một cây tạm đã biết nên phép so sánh `equals` với `targetFile` cho `true`.
3. `Files.readString(link, UTF_8)` truy cập nội dung qua symlink; hệ thống tệp lần theo liên kết tới `report.txt`.
4. `Files.isSymbolicLink(link)` kiểm tra bản thân mục `latest.txt` có phải symlink, không xét file đích thuộc loại nào.
5. Khối `finally` xóa riêng liên kết trước khi xóa file đích. `Files.deleteIfExists(link)` xóa symlink, **không xóa nội dung file đích** mà liên kết chỉ tới.

Ví dụ không giả định tên thư mục tạm, vì `Files.createTempDirectory` sinh tên khác nhau mỗi lần chạy. Chạy trên một số bản Windows cần bật Developer Mode hoặc quyền phù hợp để tạo symlink; hệ thống tệp không hỗ trợ chức năng này cũng có thể từ chối lời gọi `createSymbolicLink`.

## 4. Đọc đường dẫn đích và xác định vị trí được tham chiếu

Có ba thao tác dễ bị nhầm:

~~~java
Path stored = Files.readSymbolicLink(link);
Path lexicalDestination = link.getParent().resolve(stored).normalize();
Path realDestination = link.toRealPath();
~~~

| Thao tác | Kết quả | Có cần đích tồn tại? |
| --- | --- | --- |
| `Files.readSymbolicLink(link)` | Đường dẫn được **lưu trong symlink**, có thể tương đối | Không cần đích tồn tại, nhưng bản thân symlink phải tồn tại |
| `link.getParent().resolve(stored).normalize()` | Đường dẫn suy ra bằng phép ghép và rút gọn từ vựng `.`/`..` | Không |
| `link.toRealPath()` | Đường dẫn thực được phân giải theo hệ thống tệp, mặc định lần theo các symlink | Có, nên liên kết bị đứt sẽ gây lỗi |

`normalize()` xử lý thành phần tên đường dẫn theo cú pháp, **không truy cập hệ thống tệp**; nó không xác nhận đối tượng tồn tại, không tự giải quyết các symlink trung gian và không chứng minh hai đường dẫn trỏ cùng đối tượng. Khi cần xác định hai đường dẫn thực sự cùng file đang tồn tại, có thể dùng `Files.isSameFile(path1, path2)`; phương thức này truy cập hệ thống tệp và có thể phát sinh `IOException`.

**Lưu ý cho đường dẫn `link` tương đối:** `link.getParent()` có thể trả `null`, ví dụ `Path.of("alias.txt")`. Khi đó cần xác định vị trí tuyệt đối của liên kết trước khi lấy thư mục cha:

~~~java
Path absoluteLink = link.toAbsolutePath().normalize();
Path stored = Files.readSymbolicLink(link);
Path destination = absoluteLink.getParent().resolve(stored).normalize();
~~~

Ở đây `toAbsolutePath()` dùng thư mục làm việc để xác định **vị trí của chính symlink tương đối**. Sau khi đã xác định thư mục cha đó, **đường dẫn đích được lưu** vẫn được diễn giải tương đối với thư mục cha của symlink. `toAbsolutePath()` cũng không xác minh đích tồn tại.

## 5. Symlink bị đứt (dangling/broken symlink)

Một symlink bị đứt khi mục liên kết vẫn tồn tại nhưng đích được tham chiếu không thể tìm thấy (ví dụ file đích bị xóa, đổi tên hoặc liên kết bị chuyển sang thư mục khác).

Ví dụ, tại `workspace/links/latest.txt` vẫn lưu `../data/report.txt` nhưng `workspace/data/report.txt` đã bị xóa:

- `Files.isSymbolicLink(link)` → `true`: symlink còn tồn tại.
- `Files.readSymbolicLink(link)` → đường dẫn tương đối đã lưu: vẫn đọc được.
- `Files.exists(link)` → thông thường `false`: mặc định phép kiểm tra đi theo symlink để tìm đích.
- `Files.exists(link, LinkOption.NOFOLLOW_LINKS)` → `true`: kiểm tra chính mục symlink, không lần theo đích.
- `Files.readString(link)` hoặc `link.toRealPath()` → phát sinh `IOException`, thường là `NoSuchFileException`, nếu đích không tồn tại.

Trường hợp quyền truy cập bị từ chối hoặc trạng thái không thể xác định, `Files.exists` cũng có thể trả `false`; không nên hiểu `false` luôn chắc chắn là “không có file”.

Khi cần kiểm tra chính liên kết, sử dụng `LinkOption.NOFOLLOW_LINKS`. Ví dụ:

~~~java
import java.nio.file.LinkOption;

// link trỏ đến symlink cần kiểm tra.
boolean linkEntryExists = Files.exists(link, LinkOption.NOFOLLOW_LINKS);
boolean destinationAccessible = Files.exists(link);
boolean isLink = Files.isSymbolicLink(link);
~~~

Đây là **đoạn minh họa bổ sung**, dùng biến `link` và import `Files`, `Path` từ ví dụ đầy đủ phía trên; không phải chương trình Java độc lập.

## 6. Di chuyển thư mục: vì sao đích tương đối có thể còn đúng hoặc bị sai?

Đường dẫn đích tương đối đặc biệt hữu ích khi đóng gói hoặc sao chép **cả cấu trúc thư mục** mà giữ nguyên quan hệ giữa symlink và đích.

Ví dụ ban đầu:

~~~text
app/
├── data/report.txt
└── links/latest.txt -> ../data/report.txt
~~~

Nếu chuyển nguyên vẹn cây `app/` sang một nơi khác và **giữ symlink dưới dạng liên kết**, quan hệ `links/../data` không đổi, nên liên kết thường vẫn hoạt động mà không cần lưu đường dẫn tuyệt đối của máy cũ.

Ngược lại, nếu chỉ chuyển `app/links/latest.txt` vào `app/links/archive/latest.txt` mà giữ nguyên đích đã lưu `../data/report.txt`, vị trí được suy ra sẽ là:

~~~text
app/links/archive/../data/report.txt
= app/links/data/report.txt
~~~

Đó không còn là `app/data/report.txt`. Do vậy **di chuyển riêng symlink** có thể làm liên kết bị đứt. Muốn symlink tại `app/links/archive/latest.txt` trỏ về `app/data/report.txt` thì đường dẫn tương đối phù hợp là `../../data/report.txt`.

Khi sao chép bằng Java NIO.2, hành vi cũng cần được chỉ định rõ:

- `Files.copy(sourceLink, destination)` mặc định **theo symlink nguồn** và sao chép đối tượng được trỏ tới, nếu không có tùy chọn khác.
- `Files.copy(sourceLink, destination, LinkOption.NOFOLLOW_LINKS)` sao chép **bản thân liên kết** theo cơ chế được nhà cung cấp hệ thống tệp hỗ trợ; đường dẫn đích tương đối được giữ theo liên kết nên phải kiểm tra cấu trúc đích sau khi copy.
- `Files.move(sourceLink, destination)` di chuyển **mục liên kết**, không tự viết lại đường dẫn đích để giữ nguyên đối tượng mà symlink từng trỏ tới.

Đừng suy đoán rằng sao chép thư mục luôn bảo tồn symlink; điều đó phụ thuộc công cụ, tùy chọn và hệ thống tệp.

## 7. Các lỗi phổ biến và cách tránh

**Lỗi 1 — Hiểu đích tương đối theo `user.dir`.** Chương trình được chạy từ thư mục nào không quyết định cách hệ điều hành lần theo đích tương đối đã lưu. Hãy dùng thư mục chứa **symlink** làm điểm gốc để tính đích.

**Lỗi 2 — Ghép đích với bản thân đường dẫn symlink.** Viết `link.resolve(stored)` coi tên symlink như một thư mục và thường tạo ra đường dẫn sai. Cần dùng `link.getParent().resolve(stored)` (sau khi xử lý trường hợp `getParent() == null`).

**Lỗi 3 — Nhầm `normalize()` với giải quyết đường dẫn thực.** `normalize()` chỉ bỏ `.`/`..` về mặt cú pháp; muốn xác minh đường dẫn tồn tại và giải quyết symlink thực tế thì xét `toRealPath()`, `Files.isSameFile` và các lỗi I/O có thể xảy ra.

**Lỗi 4 — Dùng `Files.exists(link)` để quyết định có mục symlink hay không.** Một liên kết bị đứt thường cho `false`. Dùng `Files.exists(link, LinkOption.NOFOLLOW_LINKS)` để kiểm tra mục liên kết và `Files.isSymbolicLink(link)` để nhận biết loại mục.

**Lỗi 5 — Cho rằng tạo symlink đồng nghĩa đích tồn tại.** Một liên kết có thể được tạo trước file đích hoặc trở nên bị đứt sau đó. Đọc qua symlink vẫn có thể thất bại.

**Lỗi 6 — Cho rằng di chuyển symlink vẫn giữ đích cũ.** Đích tương đối không được tự động tính lại khi symlink đổi vị trí. Phải kiểm tra quan hệ thư mục và tạo lại liên kết với đích tương đối mới nếu cần.

**Lỗi 7 — Bỏ qua quyền và khác biệt hệ điều hành.** `Files.createSymbolicLink` có thể ném `UnsupportedOperationException` nếu provider không hỗ trợ, `FileAlreadyExistsException` nếu tên symlink đã tồn tại, hoặc `IOException` khi quyền/hệ thống tệp không cho phép. Một số cấu hình Windows yêu cầu Developer Mode hoặc đặc quyền phù hợp. Hãy kiểm tra môi trường và xử lý lỗi, thay vì giả định mọi máy đều cho phép tạo symlink.

**Lỗi 8 — Tin tưởng đường dẫn từ nguồn không đáng tin.** Đích tương đối có thể chứa `..` để đi ra ngoài thư mục dự kiến; các thành phần trung gian cũng có thể là symlink. Kiểm tra từ vựng với `normalize()` một mình **không đủ** để bảo đảm đường dẫn thực nằm trong thư mục an toàn. Khi làm việc với dữ liệu do người dùng kiểm soát, cần xác minh đối tượng thực, giới hạn quyền truy cập và xét cả rủi ro thay đổi hệ thống tệp giữa lúc kiểm tra và lúc sử dụng (TOCTOU).

## 8. Khi nào nên dùng đường dẫn đích tương đối?

Nên cân nhắc đích tương đối khi liên kết và tài nguyên đích thuộc cùng một cây triển khai, ví dụ `app/links/latest.txt -> ../data/report.txt`; cấu trúc có thể được chuyển sang máy hoặc đường dẫn gốc khác mà quan hệ nội bộ vẫn giữ nguyên.

Ngược lại, nếu symlink trỏ tới tài nguyên cố định nằm ngoài cây triển khai, đích tương đối có thể khiến cấu hình khó hiểu hoặc dễ đứt khi di chuyển các thư mục. Chọn đường dẫn tuyệt đối hoặc một cơ chế cấu hình phù hợp khi đó; cũng cần hiểu rằng đường dẫn tuyệt đối thường khó di chuyển giữa môi trường.

## 9. Câu hỏi tự kiểm tra

1. `project/bin/current -> ../config/settings.json` trỏ tới đâu? **Đáp án:** `project/config/settings.json`, vì điểm gốc là `project/bin/`.
2. Nếu chạy Java từ `/tmp` nhưng symlink vẫn ở `project/bin/`, đích tương đối có đổi không? **Không**; chỉ cách tìm symlink bằng đường dẫn tương đối trong chương trình có thể chịu ảnh hưởng của thư mục làm việc.
3. Vì sao `Files.readSymbolicLink(link)` có thể thành công trong khi `Files.readString(link)` thất bại? **Vì** thao tác đầu đọc đường dẫn được lưu trong symlink, còn thao tác sau phải truy cập đối tượng đích.
4. Sau khi chuyển symlink từ `project/bin/current` sang `project/bin/archive/current`, giữ nguyên `../config/settings.json` có chắc đúng không? **Không**; lúc này nó được hiểu từ `project/bin/archive/` và sẽ tìm `project/bin/config/settings.json`, trừ khi cấu trúc thư mục khác làm đường dẫn này hợp lệ.

**Ghi nhớ:** Với đích tương đối, hãy bắt đầu từ **thư mục chứa symlink**, ghép với **đường dẫn đã lưu**, và phân biệt phép tính đường dẫn theo cú pháp với việc hệ thống tệp thực sự lần theo liên kết.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-links-004">Liên kết hỏng và khác biệt khi theo hoặc không theo link</a>

<details>
<summary>Xem chi tiết</summary>

# Liên kết hỏng và khác biệt giữa theo link / không theo link trong Java NIO.2

## 1. Mục tiêu và phạm vi

Trong Java Core I/O, một đường dẫn (`Path`) có thể chỉ trực tiếp tới tệp/thư mục hoặc chỉ tới **symbolic link (symlink, liên kết tượng trưng)**. Symlink là một mục trong hệ thống tệp lưu đường dẫn đến mục khác (đích). Khi API **follow link**, nó truy cập mục đích; khi dùng `LinkOption.NOFOLLOW_LINKS`, những API hỗ trợ tùy chọn này thao tác/đọc thuộc tính của chính mục symlink.

Sau bài này, bạn cần phân biệt được ba câu hỏi độc lập: (1) mục symlink có tồn tại không, (2) đích của symlink có truy cập được không, và (3) lệnh đang tác động tới symlink hay tới đích. Hiểu rõ ba câu hỏi này sẽ giúp tránh báo nhầm “tệp không tồn tại”, sao chép nhầm dữ liệu, hoặc xử lý sai khi dọn tệp.

## 2. Symlink hợp lệ, symlink hỏng và đường dẫn thường

Ví dụ trên hệ thống tệp hỗ trợ symbolic link:

```text
project/
├── data.txt                (tệp thật)
├── alias.txt -> data.txt   (symlink hợp lệ, đích có tồn tại)
└── stale.txt -> gone.txt   (symlink hỏng, đích không tồn tại)
```

- **Symlink hợp lệ**: bản thân mục liên kết tồn tại, và chuỗi đích mà nó trỏ tới được phân giải đến một mục có thể truy cập.
- **Symlink hỏng (broken/dangling symlink)**: bản thân mục liên kết vẫn tồn tại nhưng đường dẫn đích không phân giải đến mục tồn tại; ví dụ đích bị xóa, di chuyển, hoặc một liên kết trong chuỗi trỏ tới đích thiếu.
- **Đường dẫn không tồn tại**: không có mục nào tại vị trí đó; khác với symlink hỏng vì symlink hỏng vẫn có mục liên kết để đọc, di chuyển hoặc xóa.
- **Hard link** khác symlink: hard link là tên khác cho cùng đối tượng tệp trên hệ thống tệp hỗ trợ; xóa một tên không khiến các tên hard link còn lại trở thành symlink hỏng.

Một `Path` chỉ biểu diễn đường dẫn; chỉ gọi `Path.of(...)` hoặc `resolve(...)` không tự động kiểm tra tồn tại và không đảm bảo đường dẫn đó là tệp thật.

## 3. “Follow” và “NOFOLLOW” thực chất thay đổi điều gì?

Java NIO.2 dùng `java.nio.file.LinkOption.NOFOLLOW_LINKS` cho **những phương thức có hỗ trợ tùy chọn này**. Không có cài đặt toàn cục áp dụng cho mọi thao tác `Files`. Mỗi API có quy tắc riêng.

Xét `alias.txt -> data.txt`:

- `Files.exists(alias)`: mặc định **theo liên kết**, hỏi xem đường dẫn phân giải tới đích tồn tại không.
- `Files.exists(alias, LinkOption.NOFOLLOW_LINKS)`: **không theo liên kết ở mục được kiểm tra**, hỏi mục symlink có tồn tại không.
- `Files.readAttributes(alias, BasicFileAttributes.class)`: mặc định lấy thuộc tính của đích.
- `Files.readAttributes(alias, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)`: lấy thuộc tính của chính symlink, chẳng hạn `isSymbolicLink() == true`.
- `Files.isSymbolicLink(alias)`: nhận diện bản thân mục liên kết; vẫn trả `true` cho symlink hỏng nếu mục đó còn tồn tại và có thể được nhận diện.

**Lưu ý:** `NOFOLLOW_LINKS` trên một phép kiểm tra không có nghĩa mọi thành phần cha trong toàn bộ đường dẫn đều không thể được phân giải qua liên kết, cũng không tạo ra bảo đảm an toàn chống việc đường dẫn bị thay đổi đồng thời.

## 4. Bảng so sánh quan trọng nhất: `exists`, `notExists`, `isSymbolicLink`

Trong điều kiện có quyền truy cập, hệ thống tệp ổn định và không có lỗi I/O:

| Trạng thái tại `p` | `Files.exists(p)` | `Files.exists(p, NOFOLLOW_LINKS)` | `Files.isSymbolicLink(p)` |
| --- | --- | --- | --- |
| Tệp thường đang tồn tại | `true` | `true` | `false` |
| Symlink đến đích tồn tại | `true` | `true` | `true` |
| Symlink hỏng (đích thiếu) | `false` | `true` | `true` |
| Mục tại `p` hoàn toàn không tồn tại | `false` | `false` | `false` |

Cặp kiểm tra `exists(p) == false` và `exists(p, NOFOLLOW_LINKS) == true` là **dấu hiệu hữu ích** của link không phân giải được. Tuy nhiên, tuyệt đối không suy luận chắc chắn “đích đã bị xóa”: thiếu quyền, lỗi hệ thống tệp, liên kết vòng, hoặc thay đổi đồng thời cũng có thể làm một phép kiểm tra thất bại.

`Files.notExists(p)` **không phải** phủ định logic của `Files.exists(p)`. Nếu Java không xác định được sự tồn tại, cả `exists` và `notExists` đều có thể trả `false`. `Files.notExists(p, NOFOLLOW_LINKS)` kiểm tra sự vắng mặt của chính mục liên kết, không chỉ của đích.

Với symlink hỏng mà đích được xác định chắc chắn là không tồn tại, thông thường:

```java
Files.exists(link);                            // false
Files.notExists(link);                         // true
Files.exists(link, LinkOption.NOFOLLOW_LINKS);  // true
Files.notExists(link, LinkOption.NOFOLLOW_LINKS);// false
Files.isSymbolicLink(link);                    // true
```

Các dòng trên là **biểu thức minh họa**, giả định biến `link` đã khai báo và có đường dẫn thích hợp; chúng không phải chương trình Java độc lập.

## 5. Ví dụ Java 17+: tạo link hợp lệ, làm hỏng rồi kiểm tra

Tạo file `DanglingLinkDemo.java`:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public class DanglingLinkDemo {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("java-link-");
        Path target = dir.resolve("data.txt");
        Path link = dir.resolve("shortcut.txt");

        try {
            Files.writeString(target, "Java NIO");
            // Lưu đích tương đối so với thư mục chứa symlink:
            Files.createSymbolicLink(link, target.getFileName());

            System.out.println("target before = " + Files.exists(target));
            System.out.println("follow before = " + Files.exists(link));
            System.out.println("nofollow before = "
                    + Files.exists(link, LinkOption.NOFOLLOW_LINKS));

            Files.delete(target); // Chỉ xóa đích; symlink vẫn còn

            System.out.println("target after = " + Files.exists(target));
            System.out.println("follow after = " + Files.exists(link));
            System.out.println("nofollow after = "
                    + Files.exists(link, LinkOption.NOFOLLOW_LINKS));
            System.out.println("is symlink = " + Files.isSymbolicLink(link));
            System.out.println("stored target = " + Files.readSymbolicLink(link));
        } finally {
            // Xóa liên kết, không truy theo liên kết để xóa đích.
            Files.deleteIfExists(link);
            Files.deleteIfExists(target);
            Files.deleteIfExists(dir);
        }
    }
}
```

Chạy trong thư mục có file nguồn, bằng JDK 17+:

```bash
javac DanglingLinkDemo.java
java DanglingLinkDemo
```

Kết quả khi hệ thống tệp hỗ trợ symlink và có quyền tạo symlink:

```text
target before = true
follow before = true
nofollow before = true
target after = false
follow after = false
nofollow after = true
is symlink = true
stored target = data.txt
```

**Diễn giải:** `Files.createSymbolicLink(link, target.getFileName())` lưu chuỗi `data.txt` trong mục liên kết. Lúc đầu cả link và đích đều tồn tại. Sau `Files.delete(target)`, mục `shortcut.txt` vẫn tồn tại, nhưng kiểm tra có theo link không còn tìm được tệp đích. `readSymbolicLink` vẫn đọc được đường dẫn đích đã lưu.

Chương trình này cần hệ thống tệp hỗ trợ symlink và quyền tạo nó. Trên một số cấu hình Windows, việc tạo symlink có thể cần quyền thích hợp hoặc Developer Mode; trên file system/provider khác có thể xuất hiện `UnsupportedOperationException` hay `IOException`. Nếu tạo link thất bại, không kỳ vọng stdout phía trên.

## 6. Đọc chính xác thuộc tính: đích hay mục liên kết?

`BasicFileAttributes` hỗ trợ `isRegularFile()`, `isDirectory()`, `isSymbolicLink()`, `size()` và nhiều thuộc tính khác. Cùng một đường dẫn symlink có thể trả các thuộc tính khác nhau tùy có theo link.

```java
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

// Các dòng dưới nằm trong phương thức có thể throws IOException.
// Giả sử link là symlink đến một tệp thường còn tồn tại.
Path link = Path.of("shortcut.txt");

BasicFileAttributes ofTarget =
        Files.readAttributes(link, BasicFileAttributes.class);
BasicFileAttributes ofLink =
        Files.readAttributes(link, BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);

System.out.println(ofTarget.isRegularFile()); // true nếu đích là file thường
System.out.println(ofTarget.isSymbolicLink()); // false khi đọc thuộc tính đích
System.out.println(ofLink.isSymbolicLink());   // true khi đọc chính symlink
```

Đây là **đoạn minh họa đặt trong phương thức**, không phải tệp Java độc lập. Nếu symlink đã hỏng, lệnh đọc thuộc tính **đích** thường ném `NoSuchFileException`; đọc thuộc tính **link** với `NOFOLLOW_LINKS` vẫn có thể thành công. Đừng suy đoán độ dài nội dung của tệp thật từ `size()` lấy trên chính link: kích thước link và cách báo cáo phụ thuộc nhà cung cấp hệ thống tệp.

Các phép thử `Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)` và `Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)` thông thường là `false` đối với một symlink, ngay cả khi đích của nó là file hoặc thư mục tương ứng. Mặc định, những phép thử này thường theo link.

## 7. Đọc giá trị link và phân giải đích tương đối

`Files.readSymbolicLink(link)` trả về **đường dẫn được lưu bên trong symlink**, không tự động mở đích, không tự đảm bảo đích tồn tại, và không buộc trả về đường dẫn tuyệt đối.

Ví dụ, mục `/work/reports/latest.txt` chứa đích `../archive/report.txt`. Đường dẫn được lưu là `../archive/report.txt`, cần giải thích tương đối so với **thư mục chứa link**, chứ không phải thư mục làm việc hiện tại.

```java
Path link = Path.of("/work/reports/latest.txt");
Path storedTarget = Files.readSymbolicLink(link);
Path candidate = link.toAbsolutePath().getParent()
        .resolve(storedTarget)
        .normalize();
```

Đoạn trên minh họa phân giải từ thư mục cha của link; cần nằm trong phương thức xử lý `IOException` (và chỉ chạy với một symlink thực sự tồn tại). `normalize()` chỉ xử lý thành phần từ vựng như `..`, không chứng minh đích thực sự tồn tại, không theo link thêm và không bảo đảm đường dẫn nằm trong thư mục tin cậy. Nếu giá trị lưu sẵn là đường dẫn tuyệt đối, `resolve` theo quy tắc provider sẽ sử dụng đường dẫn tuyệt đối đó trên hệ thống tệp mặc định.

`link.toRealPath()` thường yêu cầu đích truy cập được và phân giải liên kết. `link.toRealPath(LinkOption.NOFOLLOW_LINKS)` xử lý các liên kết theo chế độ không theo link của API, với yêu cầu tồn tại và khác biệt theo provider; không thay thế `readSymbolicLink` khi cần đọc **chuỗi đích gốc**.

## 8. Copy, move, delete: mỗi thao tác có quy tắc riêng

| Thao tác | Với symlink hợp lệ | Khi symlink hỏng |
| --- | --- | --- |
| `Files.copy(link, dst)` | Mặc định theo link nguồn, sao chép **đích** (nếu đích là file: nội dung file) | Thường thất bại vì không thể đọc đích |
| `Files.copy(link, dst, LinkOption.NOFOLLOW_LINKS)` | Sao chép **bản thân symlink** nếu được hỗ trợ | Có thể sao chép link hỏng như một link; chuỗi đích vẫn có thể không hợp lệ ở vị trí mới |
| `Files.move(link, dst)` | Di chuyển/đổi tên **bản thân symlink**, không chuyển dữ liệu đích | Vẫn có thể di chuyển mục symlink hỏng |
| `Files.delete(link)` / `Files.deleteIfExists(link)` | Xóa **mục symlink**, không xóa file/thư mục đích | Xóa được mục symlink hỏng nếu quyền và điều kiện cho phép |
| `Files.readSymbolicLink(link)` | Trả đường dẫn đích được lưu | Vẫn có thể đọc giá trị link đã lưu |

Lưu ý khi di chuyển hoặc sao chép một symlink có **đích tương đối** sang thư mục mới: giá trị đích được lưu không nhất thiết thay đổi theo, vì thế link mới có thể hỏng dù link gốc hợp lệ. Không thêm `NOFOLLOW_LINKS` một cách máy móc vào mọi thao tác: `move` và `delete` đã có quy tắc làm việc với chính symlink; một số API không nhận tùy chọn này.

Đối với `Files.copy`, mục đích **ở nguồn** quyết định theo hay không theo liên kết; nếu vị trí đích đã tồn tại dưới dạng symlink, `REPLACE_EXISTING` tác động tới **mục liên kết đích**, không tự động xóa tệp mà nó trỏ tới. Sao chép không nhất thiết là thao tác nguyên tử; khi phát sinh `IOException`, mục đích mới có thể chưa hoàn chỉnh.

## 9. Duyệt cây thư mục: không theo mặc định, chỉ theo khi chủ động yêu cầu

- `Files.walk(root)` và `Files.walkFileTree(root, visitor)` mặc định **không theo symbolic link** khi duyệt cây.
- `Files.walk(root, FileVisitOption.FOLLOW_LINKS)` hoặc `Files.walkFileTree(root, Set.of(FileVisitOption.FOLLOW_LINKS), maxDepth, visitor)` chủ động theo liên kết thư mục.
- Theo link có thể đưa chương trình ra ngoài nhánh thư mục dự tính, lặp qua cùng vùng dữ liệu, hoặc gặp vòng lặp. Trong `walkFileTree`, vòng lặp có thể được báo bằng `FileSystemLoopException` ở `visitFileFailed`.
- Khi cho phép theo link, phải xác định chính sách đối với đích nằm ngoài cây, đích hỏng, quyền truy cập, độ sâu tối đa và lỗi I/O; với bộ quét hoặc thao tác xóa, không nên bật `FOLLOW_LINKS` một cách vô điều kiện.

Đặc biệt, `LinkOption.NOFOLLOW_LINKS` (trong thao tác kiểm tra thuộc tính, copy...) **khác enum** với `FileVisitOption.FOLLOW_LINKS` (trong duyệt cây). Hai loại tùy chọn được thiết kế cho những phương thức và hành vi riêng.

## 10. Chẩn đoán một đường dẫn nghi là link hỏng

Một quy trình điều tra có kiểm soát:

1. Đọc thuộc tính **mục** với `Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)`. Nếu ném `NoSuchFileException`, tại thời điểm kiểm tra mục không tìm được; nếu ném `AccessDeniedException` hoặc lỗi I/O khác, giữ nguyên thông tin lỗi, không đổi thành kết luận “không tồn tại”.
2. Nếu `attrs.isSymbolicLink()` là `false`, đây không phải symbolic link ở vị trí cuối của đường dẫn; tiếp tục xử lý theo loại tệp tương ứng.
3. Nếu đúng là symlink, đọc chuỗi đích bằng `Files.readSymbolicLink(path)` để phục vụ chẩn đoán.
4. Đọc thuộc tính **đích** bằng `Files.readAttributes(path, BasicFileAttributes.class)`. Thành công chứng tỏ đường dẫn phân giải được tại lúc đọc. `NoSuchFileException` là bằng chứng cho đích/chuỗi phân giải thiếu ở thời điểm đó; `AccessDeniedException`, `FileSystemLoopException` hoặc `IOException` khác yêu cầu xử lý riêng.
5. Quyết định xử lý theo yêu cầu nghiệp vụ: giữ nguyên link, báo cáo lỗi, cập nhật link bằng thao tác có kiểm soát, hoặc xóa **chính link** nếu chính sách cho phép.

Ví dụ đoạn xử lý cốt lõi, đặt trong phương thức khai báo `throws IOException`:

```java
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

Path path = Path.of("shortcut.txt");
BasicFileAttributes linkAttrs = Files.readAttributes(
        path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

if (linkAttrs.isSymbolicLink()) {
    System.out.println("Stored: " + Files.readSymbolicLink(path));
    try {
        BasicFileAttributes targetAttrs =
                Files.readAttributes(path, BasicFileAttributes.class);
        System.out.println("Resolved target; directory = "
                + targetAttrs.isDirectory());
    } catch (NoSuchFileException e) {
        System.out.println("Target path missing at inspection time");
    } catch (AccessDeniedException e) {
        System.out.println("Cannot read target due to access restrictions");
    } catch (FileSystemLoopException e) {
        System.out.println("Detected symbolic-link cycle");
    } catch (IOException e) {
        System.out.println("Other resolution/I-O error: " + e.getMessage());
    }
}
```

**Giới hạn:** các ngoại lệ cụ thể tùy hệ điều hành/provider, `FileSystemLoopException` không được bảo đảm xuất hiện cho mọi vòng lặp, và mục có thể bị thay đổi giữa hai lần gọi. Cần bắt lỗi thích hợp ngay tại thao tác thực tế. Ví dụ này không tự xóa hoặc “sửa” link.

## 11. Lỗi phổ biến và cách sửa

| Sai lầm | Vì sao sai | Cách xử lý |
| --- | --- | --- |
| `!Files.exists(link)` nghĩa là bản thân link đã biến mất | Mặc định theo đích, link hỏng vẫn tồn tại | Thêm phép kiểm `Files.exists(link, NOFOLLOW_LINKS)`; đọc thuộc tính link để xác nhận |
| `!Files.exists(path)` tương đương `Files.notExists(path)` | Không xác định được trạng thái có thể khiến cả hai đều `false` | Phân biệt “không tồn tại” và “không xác định”; kiểm tra lỗi khi thao tác |
| `Files.isRegularFile(link, NOFOLLOW_LINKS)` phải là `true` nếu link đến file | NOFOLLOW xem chính symlink, không phải loại của đích | Dùng phép đọc thuộc tính mặc định nếu thực sự cần loại đích |
| `readSymbolicLink` trả đường dẫn chuẩn tuyệt đối hoặc xác nhận đích hợp lệ | API trả chuỗi đường dẫn được lưu, có thể tương đối hoặc đã hỏng | Phân giải theo thư mục chứa link và thử truy cập đích riêng |
| Thêm `NOFOLLOW_LINKS` vào mọi `Files.*` | Không phải API nào cũng nhận tùy chọn này | Xem chữ ký và hợp đồng của từng API |
| `Files.move(link, dst)` sẽ chuyển tệp thật đằng sau | Move xử lý chính mục symlink | Chuyển tệp thật qua đường dẫn đích nếu đó là chủ ý |
| Xóa link sẽ xóa dữ liệu đích | `Files.delete(link)` xóa mục link | Chỉ xóa đích khi thực sự được chỉ định, có kiểm soát |
| Sao chép hoặc di chuyển link tương đối vẫn luôn giữ đích đúng | Link mới có thư mục gốc tương đối khác | Kiểm tra lại đích sau sao chép/di chuyển |
| Kiểm tra `exists` rồi tin kết quả mãi mãi | Có race giữa kiểm tra và sử dụng (TOCTOU) | Thực hiện thao tác cần thiết và xử lý `IOException`; dùng API an toàn hơn khi có yêu cầu bảo mật |

## 12. Vấn đề bảo mật và tính di động

- **TOCTOU (time of check to time of use):** symlink hoặc đích có thể đổi giữa `exists` và `readAttributes`, `copy`, `open`, `delete`. Trạng thái đọc chỉ đúng tại thời điểm quan sát; đừng dùng `exists` làm điều kiện bảo mật duy nhất.
- **Không tự coi `normalize()` là an toàn:** nó chỉ chuẩn hóa cú pháp; không ngăn symlink đưa đường dẫn ra ngoài thư mục được cho phép. Với vùng tệp do người dùng kiểm soát, xác minh ranh giới truy cập và cân nhắc API chống race phù hợp như `SecureDirectoryStream` nếu provider hỗ trợ.
- **Hệ điều hành/provider:** tạo hoặc sao chép symlink có thể không được hỗ trợ hoặc bị giới hạn quyền; nhiều file system có quy tắc riêng. Trên Windows, symbolic link, junction và shortcut `.lnk` không phải ba khái niệm đồng nhất. Kiểm tra bằng NIO.2, không suy diễn từ phần mở rộng file.
- **Xử lý lỗi đúng nghĩa:** phân biệt `NoSuchFileException`, `AccessDeniedException`, vòng lặp liên kết, và lỗi I/O khác; một phép kiểm boolean thường không đủ để tìm nguyên nhân.
- **Không follow khi duyệt/xóa dữ liệu không tin cậy** trừ khi đặc tả yêu cầu và đã có giới hạn rõ ràng.

## 13. Tình huống áp dụng và tự kiểm tra

**Tình huống A — dọn shortcut đã hỏng.** Đọc chính mục link bằng `NOFOLLOW_LINKS`, xác minh `isSymbolicLink()`, thử phân giải đích và phân biệt đích thiếu với lỗi truy cập. Khi được phép dọn, gọi `Files.delete(link)`; không xóa nhầm file đích.

**Tình huống B — sao lưu cây thư mục giữ nguyên cấu trúc link.** Duyệt cây không theo link, và tại nơi cần sao chép symlink dùng `Files.copy(source, destination, LinkOption.NOFOLLOW_LINKS)` nếu provider hỗ trợ. Sau sao chép, chú ý liên kết tương đối có thể đổi ý nghĩa ở vị trí mới.

**Tình huống C — thống kê nội dung thư mục theo đích thật.** Nếu chủ động muốn theo liên kết đến thư mục, sử dụng `FOLLOW_LINKS` khi duyệt, giới hạn độ sâu, xử lý chu trình và lỗi truy cập; chấp nhận rằng một tệp có thể được nhìn thấy qua nhiều đường dẫn.

Tự trả lời:

1. `Files.exists(brokenLink)` có thể là `false` dù `Files.isSymbolicLink(brokenLink)` là `true` không? **Có**, vì một phép hỏi về đích, phép còn lại hỏi về mục liên kết.
2. `Files.notExists(path)` luôn bằng `!Files.exists(path)`? **Không**, vì trạng thái không thể xác định.
3. Muốn lấy metadata của link hỏng? **Đọc `BasicFileAttributes` với `NOFOLLOW_LINKS`**.
4. Muốn biết nguyên văn symlink trỏ tới gì? **`Files.readSymbolicLink(path)`**, rồi phân giải tương đối nếu cần; không coi đó là xác nhận đích tồn tại.
5. `Files.delete(symlink)` xóa gì? **Mục symlink**, không xóa nội dung mà nó trỏ tới.

## Tài liệu đối chiếu

- Java SE 17 `java.nio.file.Files`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html
- Java SE 17 `java.nio.file.LinkOption`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/LinkOption.html
- Java SE 21 `java.nio.file.FileVisitOption`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/FileVisitOption.html
- Oracle/dev.java, Walking the File Tree: https://dev.java/learn/api/io/java-io/file-system/walking-tree/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-links-005">NOFOLLOW_LINKS trong đọc thuộc tính và kiểm tra tệp</a>

<details>
<summary>Xem chi tiết</summary>

# NOFOLLOW_LINKS trong đọc thuộc tính và kiểm tra tệp (Java NIO.2)

## 1. Mục tiêu và bối cảnh

Khi chương trình Java kiểm tra một đường dẫn bằng `java.nio.file.Files`, đường dẫn đó có thể trỏ tới **tệp thật**, **thư mục**, hoặc một **symbolic link** (liên kết tượng trưng, gọi tắt là symlink). Symlink là một mục trong hệ thống tệp chứa đường dẫn tham chiếu tới một mục khác. Ví dụ:

```text
project/
├── data.txt             (tệp thường)
├── shortcut.txt -> data.txt
└── missing.txt -> gone.txt   (liên kết đứt: đích không tồn tại)
```

Khi hỏi “`shortcut.txt` có phải tệp thường không?”, ta cần xác định đang hỏi về **đích `data.txt`** hay **bản thân mục symlink `shortcut.txt`**. Hai cách hiểu cho kết quả khác nhau. `LinkOption.NOFOLLOW_LINKS` giúp chọn cách thứ hai trong **những API hỗ trợ tùy chọn này**.

Sau bài học, bạn cần phân biệt việc theo liên kết với việc đọc chính liên kết, dự đoán kết quả của `Files.exists` / `Files.isRegularFile` / `Files.readAttributes` khi có symlink hoặc liên kết đứt, và tránh nhầm kiểm tra metadata với bảo đảm an toàn khi mở tệp.

## 2. NOFOLLOW_LINKS thực sự có ý nghĩa gì?

`java.nio.file.LinkOption` là enum có hằng `NOFOLLOW_LINKS`. Với các phương thức `Files` nhận tham số `LinkOption... options`:

- **Mặc định (không truyền `NOFOLLOW_LINKS`):** thường đi theo symlink cuối đường dẫn và làm việc với metadata của **đích cuối cùng**.
- **Có `LinkOption.NOFOLLOW_LINKS`:** không đi theo symlink cuối đường dẫn; đọc hoặc kiểm tra **chính mục liên kết**.
- Nếu đường dẫn không phải symlink, hai cách thường cho cùng kết quả khi đối tượng không thay đổi và việc truy cập hợp lệ.

Ví dụ với `alias.txt -> original.txt`, trong đó `original.txt` là tệp thường:

| Câu hỏi | Mặc định | Với `NOFOLLOW_LINKS` |
|---|---|---|
| `isRegularFile(alias)` | `true`, xét tệp đích | `false`, vì mục alias là symlink |
| `isDirectory(alias)` | `false`, xét tệp đích | `false`, vì alias không phải thư mục |
| `exists(alias)` | `true` nếu đọc được đích | `true` nếu xác minh được chính liên kết |
| `readAttributes(alias, BasicFileAttributes.class).isRegularFile()` | `true` | `false` |
| `readAttributes(alias, BasicFileAttributes.class).isSymbolicLink()` | `false` trong ví dụ này | `true` |

Nếu alias trỏ tới **thư mục**, `Files.isDirectory(alias)` thường là `true`, còn `Files.isDirectory(alias, NOFOLLOW_LINKS)` là `false`. `NOFOLLOW_LINKS` không biến symlink thành tệp thường và không thay đổi loại thực của nó.

**Giới hạn cần nhớ:** tùy chọn được áp dụng cho **lời gọi cụ thể**, không phải một chế độ của JVM hay `Path`. Nó không mặc nhiên chặn symlink ở các thành phần thư mục cha của đường dẫn. Không nên nhầm symlink với *hard link*: hard link là tên khác của cùng mục tệp và không được nhận diện bằng `BasicFileAttributes.isSymbolicLink()`.

## 3. Đọc thuộc tính theo hai góc nhìn

### 3.1. `Files.readAttributes` với `BasicFileAttributes`

Đây là cách rõ ràng để lấy nhiều thông tin cùng lúc:

```java
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

// path là một Path đã được xác định trong chương trình.
BasicFileAttributes targetAttributes =
        Files.readAttributes(path, BasicFileAttributes.class);

BasicFileAttributes entryAttributes =
        Files.readAttributes(
                path,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );

System.out.println(targetAttributes.isRegularFile());
System.out.println(entryAttributes.isSymbolicLink());
```

*Đây là đoạn mã minh họa, cần khai báo `path` và xử lý `IOException` trong chương trình chứa nó.* Với symlink trỏ tới tệp thường còn tồn tại, hai dòng in tương ứng là `true` và `true`. Dòng thứ nhất mô tả **đích**, dòng thứ hai mô tả **mục liên kết**. Khi không theo liên kết, `isRegularFile()` và `isDirectory()` trên metadata của symlink đều thường là `false`, còn `isSymbolicLink()` là `true`.

`BasicFileAttributes` cung cấp các phép hỏi loại mục (`isRegularFile`, `isDirectory`, `isSymbolicLink`, `isOther`), kích thước (`size`), thời gian (`creationTime`, `lastModifiedTime`, `lastAccessTime`) và `fileKey`. Khi dùng `NOFOLLOW_LINKS`, các thuộc tính ấy nói về **liên kết** nếu đường dẫn cuối là symlink. **Không suy diễn `size()` của symlink là kích thước nội dung tệp đích**; giá trị và độ chính xác của nhiều thuộc tính phụ thuộc hệ thống tệp/provider. Việc đọc nhiều thuộc tính cùng một lần cũng không được mọi provider bảo đảm là snapshot nguyên tử trước thay đổi đồng thời.

### 3.2. Đọc thuộc tính theo tên

Hai overload sau cũng chấp nhận `NOFOLLOW_LINKS`:

```java
Object kind = Files.getAttribute(
        path, "basic:isSymbolicLink", LinkOption.NOFOLLOW_LINKS);

var attributes = Files.readAttributes(
        path,
        "basic:size,lastModifiedTime,isSymbolicLink",
        LinkOption.NOFOLLOW_LINKS
);
```

Đây là đoạn minh họa, với `path` có sẵn và `IOException` được xử lý bên ngoài. `getAttribute` trả về `Object`; nên ép kiểu theo thuộc tính cần đọc, ví dụ giá trị của `"basic:isSymbolicLink"` là `Boolean`. `readAttributes` dạng chuỗi trả về `Map<String,Object>`, không phải `BasicFileAttributes`. Tên `basic` là attribute view mặc định; nhóm thuộc tính khác như `posix:*` chỉ dùng khi provider hỗ trợ.

Một số API liên quan cũng nhận `LinkOption...` như `Files.getLastModifiedTime(path, NOFOLLOW_LINKS)`, `Files.getPosixFilePermissions(path, NOFOLLOW_LINKS)`, `Files.getOwner(path, NOFOLLOW_LINKS)` và `Files.getFileAttributeView(path, BasicFileAttributeView.class, NOFOLLOW_LINKS)`. Hãy đọc chữ ký phương thức: **`Files.size(path)` không nhận `LinkOption`**, nên không thể viết `Files.size(path, NOFOLLOW_LINKS)`; muốn hỏi kích thước metadata của chính symlink, dùng `readAttributes` với tùy chọn tương ứng.

### 3.3. Lấy đích được ghi trong symlink

`Files.readSymbolicLink(link)` đọc `Path` **được lưu trong liên kết**, chứ không đọc nội dung của tệp đích và không bảo đảm đích tồn tại. Nếu liên kết được tạo với đích tương đối như `data.txt`, đường dẫn trả về cũng có thể là đường dẫn tương đối. Không đánh đồng kết quả này với `link.toRealPath()`: phương thức thứ hai xử lý đường dẫn thực trong hệ thống tệp và có quy tắc theo liên kết riêng.

## 4. Kiểm tra sự tồn tại và loại mục

Các phương thức kiểm tra thường gặp:

| API | Có nhận `LinkOption...`? | Điều cần nhớ |
|---|---|---|
| `Files.exists(path, options)` | Có | Hỏi mục tồn tại; mặc định theo symlink |
| `Files.notExists(path, options)` | Có | Chỉ `true` nếu xác minh được là không tồn tại |
| `Files.isRegularFile(path, options)` | Có | Symlink đến tệp thường là “tệp thường” theo mặc định, nhưng không phải khi `NOFOLLOW_LINKS` |
| `Files.isDirectory(path, options)` | Có | Tương tự khi symlink trỏ tới thư mục |
| `Files.isSymbolicLink(path)` | Không | Kiểm tra chính mục symlink; không cần truyền `NOFOLLOW_LINKS` |
| `Files.isReadable(path)`, `isWritable(path)`, `isExecutable(path)` | Không | Không có overload nhận `LinkOption`; không suy ra “không theo link” |
| `Files.readString(path)`, `readAllBytes(path)` | Không | Đọc nội dung, không phải API đọc metadata với `LinkOption` |

**Điểm dễ nhầm:** `Files.isSymbolicLink(path)` có ích khi nhận diện liên kết, nhưng trả về `false` cũng có thể là do không xác định được trạng thái. Nếu ứng dụng cần phân biệt lỗi I/O với “không phải liên kết”, dùng `Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW_LINKS)` và xử lý ngoại lệ.

### 4.1. Trường hợp symlink bị đứt (dangling symlink)

Giả sử `broken.txt -> missing.txt` và `missing.txt` thực sự không tồn tại, còn mục `broken.txt` vẫn tồn tại:

| Biểu thức | Kết quả trong điều kiện kiểm tra thành công | Lý do |
|---|---|---|
| `Files.exists(broken)` | `false` | Theo link tới đích không tồn tại |
| `Files.exists(broken, NOFOLLOW_LINKS)` | `true` | Mục liên kết vẫn hiện diện |
| `Files.notExists(broken)` | `true` | Xác nhận không có đích |
| `Files.notExists(broken, NOFOLLOW_LINKS)` | `false` | Mục liên kết vẫn tồn tại |
| `Files.isSymbolicLink(broken)` | `true` | Kiểm tra bản thân liên kết |
| `Files.isRegularFile(broken, NOFOLLOW_LINKS)` | `false` | Liên kết không phải tệp thường |
| `Files.readAttributes(broken, BasicFileAttributes.class)` | Có thể ném `NoSuchFileException` | Không đọc được thuộc tính của đích |
| `Files.readAttributes(broken, BasicFileAttributes.class, NOFOLLOW_LINKS)` | Đọc được thuộc tính của link nếu provider cho phép | Không cần đích tồn tại |

**Không được dùng `!Files.exists(path)` làm bằng chứng chắc chắn rằng không có mục nào ở đó.** Nếu quyền truy cập hoặc I/O không cho phép xác định, cả `exists` và `notExists` đều có thể trả `false`. Các hàm kiểu boolean như `isDirectory` và `isRegularFile` cũng gộp trường hợp “không xác định” vào `false`. Khi ứng dụng cần biết *vì sao* thất bại, hãy đọc thuộc tính bằng API có ném `IOException` và xử lý cụ thể.

## 5. Ví dụ Java 17+ chạy được: liên kết hợp lệ rồi trở thành liên kết đứt

Chương trình sau tự tạo thư mục tạm, tệp đích và symlink có đích tương đối. Nó so sánh việc đọc thuộc tính/kiểm tra trước và sau khi xóa tệp đích; `finally` dọn tài nguyên đã tạo.

**Tệp:** `NoFollowLinksDemo.java`

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

public class NoFollowLinksDemo {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("nofollow-demo-");
        Path target = directory.resolve("data.txt");
        Path link = directory.resolve("shortcut.txt");

        try {
            Files.writeString(target, "Hello Java");

            try {
                // Đích tương đối được giải thích từ thư mục chứa link.
                Files.createSymbolicLink(link, target.getFileName());
            } catch (IOException | UnsupportedOperationException
                     | SecurityException e) {
                System.out.println("Cannot create symbolic link");
                return;
            }

            BasicFileAttributes followed = Files.readAttributes(
                    link, BasicFileAttributes.class);
            BasicFileAttributes linkItself = Files.readAttributes(
                    link, BasicFileAttributes.class, NOFOLLOW_LINKS);

            System.out.println("target regular: " + followed.isRegularFile());
            System.out.println("entry symbolic: " + linkItself.isSymbolicLink());
            System.out.println("regular nofollow: "
                    + Files.isRegularFile(link, NOFOLLOW_LINKS));
            System.out.println("exists follow: " + Files.exists(link));
            System.out.println("exists nofollow: "
                    + Files.exists(link, NOFOLLOW_LINKS));

            Files.delete(target); // Link vẫn còn, nhưng đích biến mất.
            System.out.println("broken exists follow: " + Files.exists(link));
            System.out.println("broken exists nofollow: "
                    + Files.exists(link, NOFOLLOW_LINKS));
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(target);
            Files.deleteIfExists(directory);
        }
    }
}
```

Biên dịch và chạy tại thư mục chứa tệp nguồn:

```bash
javac NoFollowLinksDemo.java
java NoFollowLinksDemo
```

Khi hệ thống hỗ trợ tạo symlink và không có thay đổi đồng thời ngoài chương trình, stdout dự kiến:

```text
target regular: true
entry symbolic: true
regular nofollow: false
exists follow: true
exists nofollow: true
broken exists follow: false
broken exists nofollow: true
```

Một số môi trường Windows cần quyền hoặc thiết lập cho phép tạo symlink; provider/hệ thống tệp khác cũng có thể không hỗ trợ. Khi không tạo được liên kết, chương trình in duy nhất `Cannot create symbolic link` và chạy phần `finally`. Dòng `Files.delete(target)` xóa tệp đích **chứ không xóa liên kết**; vì vậy hai kết quả `exists` ở cuối khác nhau. Mã không phụ thuộc đường dẫn tạm cụ thể, và đường dẫn đích tương đối tránh gắn ví dụ với một hệ điều hành.

## 6. Áp dụng trong ứng dụng thực tế

### Tình huống A: công cụ liệt kê thư mục cần phân biệt liên kết

Giả sử trình quản lý tệp muốn gắn nhãn “File”, “Directory”, “Symbolic link” cho mỗi mục mà nó nhìn thấy. Đọc metadata với `NOFOLLOW_LINKS` để không biến một symlink trỏ tới thư mục thành chính thư mục ấy:

```java
BasicFileAttributes attrs = Files.readAttributes(
        entry, BasicFileAttributes.class, NOFOLLOW_LINKS);

String kind = attrs.isSymbolicLink() ? "Symbolic link"
        : attrs.isDirectory() ? "Directory"
        : attrs.isRegularFile() ? "File"
        : "Other";
```

Đoạn minh họa này giả định `entry` là `Path`, `IOException` được xử lý ở caller. Nếu muốn hiển thị cả loại đích, thực hiện **lần đọc khác không có `NOFOLLOW_LINKS`**, và chấp nhận có thể thất bại với link đứt. Tránh trình bày metadata mục symlink như thể là metadata của đích.

### Tình huống B: xử lý thư mục upload có symlink

Nếu nghiệp vụ chỉ cho phép **đối tượng được chỉ định trực tiếp là tệp thường**, `Files.isRegularFile(candidate, NOFOLLOW_LINKS)` là một bước kiểm tra rõ nghĩa: nó từ chối mục cuối là symlink kể cả khi symlink dẫn đến tệp thường. Nếu nghiệp vụ cho phép symlink thì cần chính sách riêng về đích hợp lệ và đường dẫn cha.

Tuy nhiên, **kiểm tra xong rồi mở tệp là hai thao tác riêng**. Giữa chúng, kẻ khác hoặc một luồng khác có thể thay mục hoặc đường dẫn (race condition/TOCTOU). `NOFOLLOW_LINKS` trong kiểm tra metadata **không tự bảo vệ** một lời gọi `Files.newInputStream`, `Files.readString`, hay thao tác mở sau đó. Để xử lý đường dẫn không đáng tin cậy, cần chính sách thư mục gốc, quyền hệ thống tệp, mở tệp với tùy chọn phù hợp và khi khả dụng dùng `SecureDirectoryStream` cho thao tác tương đối theo một thư mục đã mở. Không dựa riêng vào `exists` hoặc `toRealPath` trước khi mở để khẳng định mọi race đã được loại bỏ.

### Tình huống C: theo dõi timestamp của liên kết

Một công cụ chẩn đoán muốn biết “bản thân symlink được sửa lần cuối khi nào?” có thể gọi:

```java
var targetTime = Files.getLastModifiedTime(link);
var linkTime = Files.getLastModifiedTime(link, NOFOLLOW_LINKS);
```

Đây là đoạn minh họa với `link` được khai báo và `IOException` được xử lý bên ngoài. `targetTime` mô tả tệp đích (nếu truy cập được), còn `linkTime` mô tả liên kết (nếu hệ thống tệp hỗ trợ). Hai thời gian có thể khác nhau. Đừng giả định mọi hệ thống tệp đều lưu thời gian hoặc độ chính xác theo cùng một cách.

## 7. Phân biệt với các API lân cận

- **`Path.toRealPath()`:** thường giải quyết các symlink để lấy đường dẫn thực; `toRealPath(NOFOLLOW_LINKS)` có quy tắc không theo link trong quá trình xác định đường dẫn. Không phải API đọc `BasicFileAttributes`.
- **`Files.readSymbolicLink(link)`:** trả đường dẫn mà symlink chứa; không dereference rồi đọc nội dung.
- **`Files.walkFileTree` / `Files.walk`:** mặc định không đi xuống các symlink trong quá trình duyệt cây; việc bật `FileVisitOption.FOLLOW_LINKS` là cơ chế riêng, không phải truyền `LinkOption.NOFOLLOW_LINKS` cho tất cả thao tác.
- **`Files.copy`:** có `LinkOption.NOFOLLOW_LINKS` thông qua tham số `CopyOption...`; khi sao chép link với tùy chọn này, mục liên kết được sao chép thay vì theo đích, theo khả năng/provider của hệ thống tệp. Đừng suy diễn từ đó rằng mọi overload của `Files` đều nhận tùy chọn.
- **`Files.isSymbolicLink(path)`:** bản thân phép thử đã hỏi về liên kết; không viết `Files.isSymbolicLink(path, NOFOLLOW_LINKS)` vì không có chữ ký như vậy.

## 8. Ngoại lệ, giới hạn môi trường và lỗi phổ biến

1. **Gọi nhầm API không có tham số tùy chọn.** `Files.size(path, NOFOLLOW_LINKS)` và `Files.isSymbolicLink(path, NOFOLLOW_LINKS)` không biên dịch. Hãy chọn overload được Java API định nghĩa.
2. **Nhầm `NOFOLLOW_LINKS` thành “đường dẫn không chứa symlink”.** Nó kiểm tra mục cuối theo ngữ nghĩa của lời gọi, không chứng minh rằng mọi thành phần đường dẫn cha đều không phải symlink.
3. **Nhầm `exists == false` thành không tồn tại chắc chắn.** Có trường hợp thiếu quyền hoặc I/O khiến trạng thái không xác định; `notExists` cũng trả `false` khi không chắc.
4. **Bỏ qua liên kết đứt.** Theo link thì không lấy được thuộc tính đích; không theo link vẫn có thể đọc metadata của chính mục liên kết.
5. **Xem `isRegularFile == false` là symlink.** Kết quả đó còn có thể là thư mục, loại tệp đặc biệt, tệp không tồn tại hoặc lỗi xác định. Để phân loại, dùng `BasicFileAttributes` và xử lý ngoại lệ.
6. **Giả định thuộc tính của link là nội dung của đích.** `size`, `lastModifiedTime`, `fileKey` phụ thuộc mục đang đọc và provider; không gán ý nghĩa sai khi hiển thị.
7. **Bỏ qua khả năng của hệ điều hành.** Tạo symlink có thể bị từ chối; các view như POSIX không hiện diện trên mọi hệ thống; một số thao tác với chính thuộc tính symlink có thể không được hỗ trợ.
8. **Dùng kiểm tra tệp như khóa an toàn.** Kiểm tra và sử dụng có thể bị thay đổi giữa hai thời điểm. Khi cần bảo mật, thiết kế thao tác truy cập an toàn ngay tại thời điểm mở/sử dụng.

Khi đọc thuộc tính dạng `readAttributes` / `getAttribute`, hãy chuẩn bị xử lý `IOException` (đặc biệt `NoSuchFileException` nếu đích bị xóa), `UnsupportedOperationException` cho view không được hỗ trợ và `SecurityException` khi môi trường chặn truy cập. Tránh bắt mọi ngoại lệ rồi âm thầm giả định đối tượng là “không tồn tại”.

## 9. Tự kiểm tra

1. Symlink `docs -> actual-directory`: `Files.isDirectory(docs)` và `Files.isDirectory(docs, NOFOLLOW_LINKS)` cho kết quả gì nếu đích còn tồn tại? **Dự kiến:** `true` và `false`.
2. Liên kết `bad -> removed.txt` vẫn còn, nhưng đích đã bị xóa. `Files.exists(bad, NOFOLLOW_LINKS)` là gì? **Dự kiến:** `true` nếu có thể kiểm tra chính mục liên kết.
3. Muốn hỏi symlink là một liên kết hay không và phân biệt lỗi đọc metadata, bạn chọn gì? **Gợi ý:** `Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW_LINKS).isSymbolicLink()` trong khối xử lý `IOException`.
4. Vì sao `Files.isRegularFile(path, NOFOLLOW_LINKS)` trước `Files.readString(path)` chưa bảo đảm đọc đúng đối tượng vừa kiểm tra? **Vì:** có thể thay đổi đường dẫn/mục giữa hai lời gọi; phương thức đọc nội dung không nhận cùng `LinkOption`.

## 10. Ghi nhớ và tài liệu chính thức

**Quy tắc cốt lõi:** *Muốn biết đối tượng ở cuối liên kết là gì, dùng mặc định; muốn biết chính mục liên kết là gì, dùng `NOFOLLOW_LINKS` ở API có hỗ trợ.* Đối với kiểm tra sự tồn tại, nhớ trường hợp “không xác định”; đối với bảo mật, nhớ ranh giới giữa đọc metadata và mở/sử dụng tệp.

- Java SE 21 — `Files`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html
- Java SE 21 — `LinkOption`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/LinkOption.html
- Java SE 21 — `BasicFileAttributes`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/attribute/BasicFileAttributes.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-links-006">Files.isSameFile khác Path.equals</a>

<details>
<summary>Xem chi tiết</summary>

# `Files.isSameFile` khác `Path.equals` trong Java NIO.2

## 1. Hai câu hỏi khác nhau

Khi làm việc với tệp trong Java, **đường dẫn** và **tệp thực tế** không phải cùng một khái niệm:

- `path1.equals(path2)` hỏi: **Hai đối tượng `Path` có biểu diễn đường dẫn bằng nhau theo quy tắc của `FileSystem` không?**
- `Files.isSameFile(path1, path2)` hỏi: **Hai đường dẫn có cùng trỏ tới một tệp/thực thể trên hệ thống tệp không?**

Một tệp có thể được truy cập bằng nhiều đường dẫn khác nhau: đường dẫn chứa `.`/`..`, đường dẫn tương đối và tuyệt đối, symbolic link hoặc hard link. Vì vậy, `Path.equals` có thể là `false` trong khi `Files.isSameFile` là `true`.

Đây là hai phép kiểm tra có **mục đích, chi phí và cách xử lý lỗi khác nhau**. Đừng thay thế chúng cho nhau chỉ vì cả hai trả về `boolean`.

## 2. `Path.equals(Object)`: so sánh biểu diễn đường dẫn

`Path` thuộc `java.nio.file`. Lệnh:

~~~java
boolean equalPaths = path1.equals(path2);
~~~

có các đặc điểm:

1. **Không truy cập hệ thống tệp** để tìm tệp; đường dẫn không cần tồn tại.
2. Việc so sánh phụ thuộc cách triển khai `FileSystem`: có hệ thống phân biệt chữ hoa/chữ thường, có hệ thống không. Không nên tự giả định mọi hệ điều hành có cùng quy tắc.
3. Đối tượng khác kiểu `Path` hoặc `Path` thuộc `FileSystem` khác thì kết quả là `false`.
4. Những thành phần như `.` và `..` **không mặc nhiên được chuẩn hóa** bởi lời gọi `equals`; đường dẫn tuyệt đối và tương đối cũng không tự biến đổi cho giống nhau.
5. Vì tuân theo hợp đồng `equals`/`hashCode`, phép so sánh này phù hợp khi dùng `Path` làm khóa `HashMap`, phần tử `HashSet` hoặc so cấu trúc đường dẫn đã được xử lý theo một quy ước thống nhất.

Ví dụ mang tính biểu diễn:

~~~java
Path a = Path.of("docs", "notes.txt");
Path b = Path.of("docs", ".", "notes.txt");

System.out.println(a.equals(b));             // thường là false
System.out.println(a.equals(b.normalize())); // thường là true
~~~

`normalize()` chỉ loại bỏ thành phần thừa **theo cú pháp**, không cần biết tệp nào đang tồn tại. Khi có symbolic link, loại bỏ `..` có thể làm thay đổi tệp đích thực tế; vì vậy không được coi `normalize()` là phép xác minh hai đường dẫn trỏ tới cùng tệp.

## 3. `Files.isSameFile(Path, Path)`: so sánh danh tính tệp

Chữ ký API:

~~~java
public static boolean isSameFile(Path path, Path path2) throws IOException
~~~

`Files.isSameFile` xác định hai đường dẫn có định vị cùng một tệp hay không. Tùy nhà cung cấp hệ thống tệp, việc này có thể phải truy cập hệ thống tệp và đọc thông tin liên quan đến tệp. Nó có thể nhận ra **cùng tệp dưới hai tên đường dẫn khác nhau**, kể cả khi được hỗ trợ thông qua symbolic link hoặc hard link.

Các quy tắc quan trọng từ hợp đồng API Java 17:

- **Nếu `path.equals(path2)` đã là `true`**, `isSameFile` trả `true` ngay, **không kiểm tra tệp có tồn tại hay không**.
- Nếu hai `Path` thuộc **hai file-system provider khác nhau**, trả `false`.
- Trong các trường hợp còn lại, API kiểm tra hai đường dẫn có định vị cùng tệp hay không; có thể phát sinh `IOException` khi kiểm tra thất bại.
- Khi hệ thống tệp không đổi trong quá trình gọi, quan hệ “cùng tệp” có tính phản xạ, đối xứng và bắc cầu. Nếu hệ thống tệp thay đổi đồng thời, kết quả không có bảo đảm đó.
- Phương thức không có tham số `LinkOption.NOFOLLOW_LINKS` và thường nhận diện đích của symbolic link khi link được phân giải bình thường.

**Điểm dễ nhầm:** `Files.isSameFile(missing, missing)` là `true` dù `missing` không tồn tại, do nhánh tối ưu hai `Path` bằng nhau. Trái lại, hai đường dẫn *khác nhau* mà không thể truy cập để xác định tệp có thể gây `NoSuchFileException` hoặc một `IOException` khác. Không dùng `isSameFile` như cách kiểm tra tồn tại tệp; dùng `Files.exists`, `Files.notExists` hoặc thao tác mở/đọc thích hợp, đồng thời nhớ rằng trạng thái tệp có thể đổi ngay sau khi kiểm tra.

## 4. Bảng đối chiếu

| Tiêu chí | `Path.equals` | `Files.isSameFile` |
|---|---|---|
| So sánh cái gì? | Biểu diễn `Path` theo quy tắc `FileSystem` | Tệp mà hai `Path` cùng định vị |
| Truy cập đĩa / I/O? | Không | Có thể có |
| Đường dẫn cần tồn tại? | Không | Thông thường cần kiểm tra được; ngoại lệ khi hai `Path` đã bằng nhau |
| `.`, `..`, đường dẫn khác cách viết | Không tự chuẩn hóa trước khi so | Có thể cùng tệp nếu hai đường dẫn phân giải về cùng đích |
| Symbolic link, hard link | Không kiểm tra quan hệ liên kết | Có thể nhận diện cùng tệp |
| `IOException`? | Không có checked `IOException` | Có, cần khai báo hoặc xử lý |
| Sử dụng chủ yếu | So sánh cấu trúc/khóa `Path` | Kiểm tra danh tính của tệp |

**Không nên suy luận** “hai tệp có cùng nội dung ⇒ `isSameFile` trả `true`”. Hai tệp độc lập chứa cùng số byte vẫn có danh tính khác nhau.

## 5. Ví dụ Java 17+ chạy được

Chương trình bên dưới dùng thư mục tạm và tạo thư mục con thực sự để `sub/../data.txt` có thể được hệ thống tệp phân giải. Nó không phụ thuộc vào quyền tạo symbolic link hoặc hard link.

**Tệp: `SameFileVsPathEquals.java`**

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class SameFileVsPathEquals {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("same-file-demo-");

        try {
            Files.createDirectory(root.resolve("sub"));

            Path first = Files.writeString(
                    root.resolve("data.txt"), "hello", StandardCharsets.UTF_8);
            Path alias = root.resolve("sub").resolve("..").resolve("data.txt");
            Path other = Files.writeString(
                    root.resolve("other.txt"), "hello", StandardCharsets.UTF_8);
            Path missing = root.resolve("missing.txt");

            System.out.println(first.equals(alias));
            System.out.println(Files.isSameFile(first, alias));
            System.out.println(first.equals(alias.normalize()));
            System.out.println(Files.isSameFile(first, other));
            System.out.println(Files.isSameFile(missing, missing));
        } finally {
            Files.deleteIfExists(root.resolve("other.txt"));
            Files.deleteIfExists(root.resolve("data.txt"));
            Files.deleteIfExists(root.resolve("sub"));
            Files.deleteIfExists(root);
        }
    }
}
~~~

Biên dịch và chạy tại thư mục chứa tệp nguồn:

~~~bash
javac SameFileVsPathEquals.java
java SameFileVsPathEquals
~~~

**Kết quả:**

~~~text
false
true
true
false
true
~~~

Giải thích theo thứ tự:

1. `first.equals(alias)` → `false`: hai `Path` có chuỗi thành phần khác nhau.
2. `Files.isSameFile(first, alias)` → `true`: vì thư mục `sub` tồn tại nên đi qua `sub/..` quay lại thư mục cha rồi đến chính `data.txt`.
3. `first.equals(alias.normalize())` → `true`: chuẩn hóa cú pháp bỏ cặp `sub/..` trong ví dụ cụ thể này.
4. `Files.isSameFile(first, other)` → `false`: hai tệp độc lập dù đều chứa `hello`.
5. `Files.isSameFile(missing, missing)` → `true`: hai đối số `Path` bằng nhau nên không kiểm tra tồn tại.

Chương trình dùng `finally` để xóa tài nguyên tạm; một lỗi xóa tệp vẫn có thể phát sinh `IOException` trên hệ thống đang khóa tệp. Chương trình không dựa vào đường dẫn thư mục tạm cụ thể nên không in đường dẫn ngẫu nhiên.

## 6. Cùng tệp qua symbolic link và hard link

Hãy hình dung các đường dẫn sau (mang tính minh họa, phụ thuộc hỗ trợ của hệ thống tệp):

~~~text
/work/report.csv          ← tệp gốc
/work/latest.csv          ← symbolic link tới report.csv
/work/report-copy-name    ← hard link tới cùng tệp
~~~

- `Path.of("/work/report.csv").equals(Path.of("/work/latest.csv"))` thường là `false` do biểu diễn đường dẫn khác nhau.
- `Files.isSameFile(original, symbolicLink)` thường là `true` khi symbolic link hợp lệ, phân giải về chính tệp gốc.
- `Files.isSameFile(original, hardLink)` là `true` khi hard link thực sự trỏ tới cùng tệp.
- Sao chép `report.csv` thành một tệp mới có nội dung giống hệt không làm chúng thành “cùng tệp”; `isSameFile` khi kiểm tra thành công sẽ trả `false`.

Tạo link bằng `Files.createSymbolicLink` hoặc `Files.createLink` là thao tác tùy hỗ trợ của nhà cung cấp và quyền hệ điều hành, có thể ném ngoại lệ. Đừng viết unit test *bắt buộc* hệ thống Windows/Linux bất kỳ tạo được symbolic link nếu môi trường CI chưa được chuẩn bị.

## 7. Phân biệt với `normalize()`, `toAbsolutePath()`, `toRealPath()`

- **`path.normalize()`**: chuẩn hóa cú pháp (`.` và phần `..` có thể rút gọn), không xác minh có tệp thật. Không bảo đảm bảo toàn tệp đích khi xuất hiện symbolic link.
- **`path.toAbsolutePath()`**: biến đường dẫn thành dạng tuyệt đối theo ngữ cảnh của file system; **không** xác nhận nó tồn tại hay là đường dẫn duy nhất của tệp.
- **`path.toRealPath()`**: mặc định truy cập hệ thống tệp, tạo đường dẫn thực/chuẩn tắc theo provider, thường phân giải symbolic link và yêu cầu mục tiêu tồn tại; có thể ném `IOException`. `NOFOLLOW_LINKS` thay đổi cách xử lý liên kết.
- **`Files.isSameFile(a, b)`**: trả lời trực tiếp câu hỏi liệu hai đường dẫn có cùng định vị một tệp. Không yêu cầu tự chuyển cả hai thành chuỗi hoặc so `toString()`.

Ví dụ cần kiểm tra danh tính tệp thì `Files.isSameFile` thể hiện ý định rõ hơn việc so sánh `toRealPath().equals(...)`. Trái lại, nếu cần tạo khóa `Path` theo một quy ước chuẩn hóa riêng cho ứng dụng thì `equals` có thể phù hợp hơn. Hai hướng xử lý giải quyết hai bài toán khác nhau.

## 8. Chọn API trong tình huống thực tế

**Dùng `Path.equals` khi:**

- Muốn biết hai giá trị đường dẫn đã lưu có bằng nhau theo quy tắc của `FileSystem`.
- Muốn tra cứu cấu trúc dữ liệu dựa vào `equals`/`hashCode` mà không thực hiện I/O.
- Đang xử lý các đường dẫn chưa tồn tại (ví dụ kế hoạch tạo file mới).

**Dùng `Files.isSameFile` khi:**

- Muốn biết hai đầu vào tệp khác tên có trỏ tới cùng tệp hiện tại không.
- Muốn phát hiện alias qua symbolic link/hard link trong logic xử lý tệp.
- Cần tránh thao tác lặp vào cùng một tệp khi hai đường dẫn có cách viết khác nhau.

Ví dụ xử lý ngoại lệ, nếu yêu cầu chỉ hoạt động với hai tệp đã xác minh tồn tại:

~~~java
try {
    if (Files.isSameFile(source, destination)) {
        throw new IllegalArgumentException(
                "Source và destination cùng trỏ tới một tệp");
    }
    // Tiếp tục logic phù hợp với ứng dụng.
} catch (IOException ex) {
    // Không thể kết luận danh tính tệp: báo lỗi hoặc xử lý theo chính sách ứng dụng.
    throw new IllegalStateException("Không kiểm tra được danh tính tệp", ex);
}
~~~

Đoạn trên là **mã minh họa bên trong một phương thức** đã khai báo hai biến `Path source` và `Path destination`; không phải chương trình Java chạy độc lập.

Lưu ý: ngay cả khi `isSameFile` vừa trả kết quả, tác vụ khác có thể xóa/đổi tên/thay liên kết tệp trước thao tác kế tiếp (**race/TOCTOU**). Không coi kết quả kiểm tra tại một thời điểm là bảo đảm an toàn cho chuỗi thao tác nhạy cảm. Nếu cần tính nguyên tử hoặc chống thay đổi đồng thời, ưu tiên API thao tác tệp/handle và cơ chế kiểm soát truy cập thích hợp cho bài toán.

## 9. Lỗi thường gặp và cách nhớ

1. **Dùng `equals` để nhận diện file vật lý:** bỏ sót symbolic link, hard link và các đường dẫn khác nhau cùng trỏ về một tệp.
2. **Dùng `isSameFile` như `Files.exists`:** hai đường dẫn `Path` bằng nhau cho kết quả `true` ngay cả khi file không tồn tại.
3. **Cho rằng `isSameFile` không ném ngoại lệ:** phải tính đến `IOException`, thiếu quyền và trạng thái tệp thay đổi.
4. **Coi `normalize()` là canonicalization của hệ thống tệp:** nó xử lý cú pháp, có thể sai về danh tính tệp khi có symbolic link.
5. **So chuỗi `path.toString()`:** dễ sai do khác biệt cú pháp, quy tắc hệ thống tệp và phân biệt chữ hoa/thường.
6. **Đồng nhất nội dung với danh tính:** hai bản sao có checksum giống nhau vẫn là hai tệp khác nhau.

Ghi nhớ ngắn gọn:

~~~text
Path.equals(a, b)       → Hai biểu diễn Path có bằng nhau?
Files.isSameFile(a, b)  → Hai Path có đang chỉ tới cùng một tệp?
~~~

### Tài liệu chính thức

- [Java 17: Path.equals](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html#equals(java.lang.Object))
- [Java 17: Files.isSameFile](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#isSameFile(java.nio.file.Path,java.nio.file.Path))
- [Java 17: Path.toRealPath](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html#toRealPath(java.nio.file.LinkOption...))


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-links-007">Thao tác copy, move, delete tác động vào link hay đích</a>

<details>
<summary>Xem chi tiết</summary>

# Copy, move, delete tác động vào symbolic link hay tệp đích?

> **Phạm vi:** Java Core I/O, API `java.nio.file` (Java 17+). Bài học tập trung vào **symbolic link (liên kết tượng trưng)**, đồng thời phân biệt với **hard link (liên kết cứng)** để tránh suy luận sai khi sao chép, di chuyển và xóa tệp.

## 1. Hiểu đúng “link” và “đích”

Giả sử có cấu trúc:

~~~text
work/
├── report.txt            # Tệp thật, nội dung: hello
└── shortcut.txt          # Symbolic link -> report.txt
~~~

`report.txt` là **đối tượng đích** (target/referent); `shortcut.txt` là **một đối tượng hệ thống tệp riêng** chứa đường dẫn tham chiếu đến đích. Khi chương trình truy cập `shortcut.txt`, hệ thống tệp có thể đi theo liên kết để đọc dữ liệu từ `report.txt`. Nhưng không phải API nào cũng đi theo liên kết.

Cần phân biệt ba vai trò:

- **Source (đường dẫn nguồn):** tham số đầu tiên của `Files.copy(source, destination)` hoặc `Files.move(source, destination)`. Nó có thể là đường dẫn đến chính symbolic link.
- **Destination (đường dẫn nhận):** nơi đặt bản sao hoặc nơi chuyển tới; tại đó cũng có thể đã tồn tại một symbolic link khác.
- **Referent/target của link:** tệp hoặc thư mục được một symbolic link trỏ đến; khác với tham số tên là `target` trong `Files.copy`/`Files.move`.

Ví dụ: `Files.copy(shortcut, backup)` có **source là shortcut** và **destination là backup**, nhưng dữ liệu thực được đọc mặc định lại từ **report.txt**. Vì vậy câu hỏi “thao tác vào link hay đích?” phải xét cả **loại thao tác**, **vị trí link (nguồn/đích)** và **tùy chọn API**.

### Symbolic link và hard link khác nhau thế nào?

- **Symbolic link:** có nội dung tham chiếu là một đường dẫn; có thể trỏ đến thư mục hoặc tệp, có thể bị *dangling/broken* khi đích mất hoặc đường dẫn không còn hợp lệ. Có thể kiểm tra bằng `Files.isSymbolicLink(path)` và đọc đường dẫn tham chiếu bằng `Files.readSymbolicLink(path)`.
- **Hard link:** hai tên đường dẫn cùng tham chiếu đến **một đối tượng tệp** trên hệ thống tệp. Không tồn tại một “tệp shortcut chứa đường dẫn đích” để đi theo như symbolic link. Sửa nội dung qua tên nào cũng là sửa nội dung chung; xóa một tên không tự động xóa tên còn lại. Java có `Files.createLink(newLink, existingFile)` nếu hệ thống tệp hỗ trợ.
- **Giới hạn:** hard link thường không được tạo cho thư mục và không vượt qua các file store; tính khả dụng tùy hệ điều hành và hệ thống tệp. Những ví dụ chính dưới đây sử dụng symbolic link.

## 2. Bảng quy tắc cần nhớ

Bảng này giả sử thao tác trên `Path` trực tiếp, hệ thống tệp hỗ trợ symbolic link, và không có lỗi quyền hoặc I/O.

| Thao tác | Khi **source/path là symbolic link** | Tệp/thư mục mà link trỏ tới |
|---|---|---|
| `Files.copy(link, newPath)` | **Mặc định đi theo link**, sao chép dữ liệu/đối tượng đích sang `newPath`; bản mới thường là tệp/thư mục thường | Không bị sửa/xóa |
| `Files.copy(link, newPath, LinkOption.NOFOLLOW_LINKS)` | Sao chép **chính symbolic link** sang tên mới (giữ tham chiếu dạng đường dẫn) | Không bị sửa/xóa |
| `Files.move(link, newPath)` | Di chuyển/đổi tên **chính link** | Không bị di chuyển |
| `Files.delete(link)` | Xóa **chính link** | Không bị xóa |
| `Files.deleteIfExists(link)` | Xóa **chính link** nếu tồn tại | Không bị xóa |
| `Files.readString(link)` hoặc `Files.newInputStream(link)` | Thông thường đi theo link để đọc nội dung | Được đọc qua link |
| `Files.isSymbolicLink(link)` | Kiểm tra chính đối tượng link | Không cần đi theo đến đích |

**Mẹo ghi nhớ:** *copy* mặc định lấy **nội dung đích**; *move* và *delete* tác động lên **bản thân symbolic link**. `NOFOLLOW_LINKS` thay đổi cách **copy nguồn**, không phải là tùy chọn bắt buộc khi move/delete.

Lưu ý rằng `Files.copy` **không tự sao chép đệ quy** một thư mục: với source là thư mục, mặc định chỉ tạo thư mục mới rỗng. Nếu source là link trỏ đến thư mục, việc copy mặc định theo link cũng không đồng nghĩa sao chép toàn bộ cây thư mục.

## 3. Sao chép: tách riêng symbolic link nguồn và link ở đường dẫn nhận

### 3.1. Nguồn là symbolic link

~~~java
Path link = Path.of("work/shortcut.txt");
Path contentCopy = Path.of("work/content-copy.txt");
Path linkCopy = Path.of("work/link-copy.txt");

Files.copy(link, contentCopy);                        // Đi theo link nguồn
Files.copy(link, linkCopy, LinkOption.NOFOLLOW_LINKS); // Copy chính link nguồn
~~~

Đây là **đoạn minh họa**, giả sử `work/shortcut.txt` đã là symbolic link hợp lệ và hai đường dẫn đích chưa tồn tại; mã hoàn chỉnh có thể chạy nằm ở mục 6.

Sau lệnh thứ nhất, `content-copy.txt` có **dữ liệu độc lập** với `report.txt`. Nếu sửa `report.txt` sau đó, bản copy không tự đổi theo. Sau lệnh thứ hai, `link-copy.txt` là một **symbolic link khác**. Khi `report.txt` đổi nội dung, đọc qua `link-copy.txt` sẽ thấy nội dung mới, miễn liên kết còn trỏ đúng đích.

**Đường dẫn tương đối có thể làm bản link sao chép bị hỏng:** nếu `shortcut.txt -> report.txt` thì chuỗi tham chiếu bên trong là `report.txt`, được phân giải **tương đối với thư mục chứa link**. Copy link sang thư mục khác bằng `NOFOLLOW_LINKS` giữ nguyên tham chiếu đó; nó có thể trỏ đến tệp khác hoặc trở thành dangling link. Java không tự chuyển một đường dẫn tương đối thành đường dẫn tuyệt đối để duy trì ý nghĩa ban đầu.

Nếu source là một **dangling symbolic link**, `Files.copy(link, newPath)` theo mặc định thường thất bại khi không mở được đích; `Files.copy(link, newPath, NOFOLLOW_LINKS)` có thể sao chép chính đường dẫn liên kết dù đích không tồn tại (nếu provider cho phép tạo link).

### 3.2. Đường dẫn nhận đã là symbolic link

Đừng nhầm hành vi của **link nguồn** với **link nằm ở destination**:

~~~text
work/
├── important.txt          # Đích của existing-link.txt
└── existing-link.txt -> important.txt
~~~

- `Files.copy(source, existingLink)` **không có** `REPLACE_EXISTING`: thất bại vì đường dẫn nhận đã có một symbolic link.
- `Files.copy(source, existingLink, StandardCopyOption.REPLACE_EXISTING)`: **thay thế entry symbolic link** mang tên `existing-link.txt` bằng bản copy. Tệp `important.txt` mà link cũ trỏ đến **không bị ghi đè qua link**.
- Nếu `source` cũng là symbolic link, cách lấy nội dung nguồn vẫn theo quy tắc riêng: mặc định theo link; có `NOFOLLOW_LINKS` thì copy bản thân link. `REPLACE_EXISTING` không có nghĩa là “không theo link nguồn”.
- Nếu destination là **thư mục không rỗng**, `REPLACE_EXISTING` không cho phép thay thế tùy tiện; thao tác có thể báo `DirectoryNotEmptyException`.

`COPY_ATTRIBUTES` yêu cầu cố gắng sao chép thuộc tính được hỗ trợ (ví dụ thời gian sửa đổi). Khi copy **chính symbolic link** với `NOFOLLOW_LINKS`, khả năng sao chép thuộc tính của link phụ thuộc provider, và `COPY_ATTRIBUTES` có thể bị bỏ qua.

## 4. Di chuyển/đổi tên: di chuyển link, không dời referent

~~~java
Path link = Path.of("work/shortcut.txt");       // -> report.txt
Path renamed = Path.of("work/renamed-link.txt");

Files.move(link, renamed);                       // Đổi tên entry symbolic link
~~~

Kết quả: `shortcut.txt` không còn; `renamed-link.txt` vẫn là symbolic link; `report.txt` vẫn ở nguyên vị trí. Nếu `renamed-link.txt` là link tương đối và vẫn nằm trong cùng thư mục, nó thường tiếp tục trỏ đúng `report.txt`.

Nếu chuyển link từ `work/shortcut.txt -> report.txt` sang `archive/shortcut.txt`, đường dẫn tham chiếu `report.txt` được diễn giải trong thư mục `archive`. **Link sau move có thể bị broken**, mặc dù `report.txt` trong `work` vẫn tồn tại. Cần tự quyết định có tạo lại link với đích phù hợp hay không.

Khi đường dẫn nhận đã tồn tại:

- Mặc định `Files.move(source, destination)` thường thất bại nếu destination đã tồn tại.
- Với `StandardCopyOption.REPLACE_EXISTING`, nếu destination là symbolic link, **chính destination link bị thay thế**, không phải referent của nó. Không thể thay thế tùy tiện một thư mục không rỗng.
- Với `StandardCopyOption.ATOMIC_MOVE`, yêu cầu thao tác nguyên tử; nếu không hỗ trợ sẽ ném `AtomicMoveNotSupportedException`. Khi sử dụng tùy chọn này, các tùy chọn khác bị bỏ qua, và hành vi khi destination đã tồn tại phụ thuộc triển khai. Không mặc định cho rằng có thể move nguyên tử qua file store khác.

**Move bản thân tệp đích khác hoàn toàn với move link.** Nếu di chuyển `report.txt` sang tên khác mà không điều chỉnh tham chiếu `shortcut.txt -> report.txt`, link cũ có thể bị broken. Đó là kết quả của việc đổi vị trí referent, không phải thao tác `Files.move(link, ...)`.

## 5. Xóa: delete link không xóa đích

~~~java
Path link = Path.of("work/shortcut.txt");
Files.delete(link);                     // Xóa shortcut.txt
// report.txt vẫn còn nếu trước đó tồn tại.
~~~

- `Files.delete(link)` xóa entry của symbolic link ngay cả khi link trỏ đến thư mục **có dữ liệu**. Nó không đi vào thư mục đích để xóa các tệp bên trong.
- `Files.deleteIfExists(link)` tương tự, nhưng trả về `false` khi bản thân entry không tồn tại; `Files.delete(link)` có thể ném `NoSuchFileException` trong trường hợp đó.
- Nếu xóa **chính đường dẫn tệp thật** `Files.delete(targetPath)`, tệp thật bị xóa. Những symbolic link còn trỏ tới đường dẫn đó sẽ thành dangling link (trừ khi xuất hiện đối tượng khác tại đường dẫn này).
- Nếu mục tiêu của chương trình là xóa cả cây thư mục, `Files.delete(dir)` chỉ xóa được thư mục rỗng. Cần duyệt cây một cách có kiểm soát, thường bằng `Files.walkFileTree`, và xác định rõ chính sách theo link.

**Cẩn trọng với thao tác “xóa đích của link”:** `Files.delete(link)` *không* thực hiện việc này. Nếu thật sự muốn xóa referent, phải **chủ động xác định** đường dẫn đích (ví dụ `link.toRealPath()` khi đích tồn tại) rồi xóa đường dẫn đó. Việc phân giải và xóa là hai thao tác riêng, nên trong môi trường có thay đổi đồng thời vẫn phải cân nhắc race condition và quyền truy cập. Tuyệt đối không dùng một cách mù quáng cho đầu vào không tin cậy.

## 6. Chương trình Java 17+ chạy được

Chương trình tạo một thư mục tạm, một tệp thật và một **symbolic link tương đối**, sau đó chứng minh riêng từng thao tác. Nó dọn những entry do chính chương trình tạo trong khối `finally`.

Lưu tệp là **`SymbolicLinkOperationsDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public class SymbolicLinkOperationsDemo {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("symlink-ops-");
        Path target = dir.resolve("data.txt");
        Path link = dir.resolve("alias.txt");
        Path contentCopy = dir.resolve("content-copy.txt");
        Path linkCopy = dir.resolve("link-copy.txt");
        Path movedLink = dir.resolve("moved-alias.txt");

        try {
            Files.writeString(target, "hello");
            // data.txt được phân giải tương đối với thư mục chứa alias.txt.
            Files.createSymbolicLink(link, Path.of("data.txt"));

            Files.copy(link, contentCopy);
            Files.copy(link, linkCopy, LinkOption.NOFOLLOW_LINKS);
            System.out.println("copy default: file="
                    + Files.isRegularFile(contentCopy)
                    + ", data=" + Files.readString(contentCopy));
            System.out.println("copy nofollow: link="
                    + Files.isSymbolicLink(linkCopy)
                    + ", points=" + Files.readSymbolicLink(linkCopy));

            Files.move(link, movedLink);
            System.out.println("move: oldLinkExists="
                    + Files.exists(link, LinkOption.NOFOLLOW_LINKS)
                    + ", newLink=" + Files.isSymbolicLink(movedLink)
                    + ", targetExists=" + Files.exists(target));

            Files.delete(movedLink);
            System.out.println("delete: movedLinkExists="
                    + Files.exists(movedLink, LinkOption.NOFOLLOW_LINKS)
                    + ", targetExists=" + Files.exists(target));
        } finally {
            Files.deleteIfExists(movedLink);
            Files.deleteIfExists(link);
            Files.deleteIfExists(linkCopy);
            Files.deleteIfExists(contentCopy);
            Files.deleteIfExists(target);
            Files.deleteIfExists(dir);
        }
    }
}
~~~

Biên dịch và chạy trong thư mục chứa source:

~~~bash
javac SymbolicLinkOperationsDemo.java
java SymbolicLinkOperationsDemo
~~~

**Stdout khi hệ thống hỗ trợ tạo symbolic link và các thao tác thành công:**

~~~text
copy default: file=true, data=hello
copy nofollow: link=true, points=data.txt
move: oldLinkExists=false, newLink=true, targetExists=true
delete: movedLinkExists=false, targetExists=true
~~~

Giải thích từng dòng:

1. Copy mặc định cho ra **regular file** chứa `hello`, bởi Java đã theo `alias.txt` đến `data.txt`.
2. Copy với `NOFOLLOW_LINKS` cho ra **một symbolic link mới**, còn ghi nhận tham chiếu tương đối `data.txt`.
3. Move làm biến mất tên link cũ, tạo tên link mới, trong khi tệp `data.txt` không chuyển.
4. Delete xóa tên link vừa move; `data.txt` vẫn còn.

**Tính khả chuyển:** trên một số máy Windows, tạo symbolic link có thể cần quyền/Developer Mode; một số file system không hỗ trợ symbolic link. Khi đó `Files.createSymbolicLink` có thể ném `UnsupportedOperationException`, `IOException` hoặc lỗi quyền; chương trình không cho ra đúng stdout trên. Chạy trên hệ thống/tài khoản cho phép symbolic link để quan sát đủ thí nghiệm. Chương trình có thể cần đóng các chương trình đang giữ tệp trên hệ điều hành có hạn chế xóa tệp đang mở.

## 7. Trường hợp riêng: hard link

Giả sử tạo:

~~~java
Path original = Path.of("work/report.txt");
Path hardLink = Path.of("work/also-report.txt");
Files.createLink(hardLink, original);  // Hai tên cùng trỏ tới một đối tượng tệp
~~~

Đây là đoạn minh họa, yêu cầu `original` tồn tại và provider hỗ trợ hard link.

- **Copy:** `Files.copy(hardLink, anotherPath)` bình thường tạo **bản sao dữ liệu tệp độc lập**, không tự tạo thêm một hard link. Muốn tạo thêm hard link, dùng `Files.createLink(...)`.
- **Move:** `Files.move(hardLink, newPath)` đổi vị trí/tên entry `hardLink`; tên `original` vẫn truy cập được dữ liệu chung.
- **Delete:** `Files.delete(hardLink)` xóa **một tên**; `original` vẫn truy cập được. Trên hệ thống tệp kiểu Unix, dữ liệu thường chỉ được giải phóng khi không còn hard link (và không còn handle mở giữ đối tượng tệp), nhưng chi tiết phụ thuộc hệ thống tệp/hệ điều hành.
- **Nhận diện:** `Files.isSymbolicLink(hardLink)` trả về `false` vì hard link không phải symbolic link. Khi được hỗ trợ và cả hai tồn tại, `Files.isSameFile(original, hardLink)` có thể xác nhận hai đường dẫn chỉ cùng một đối tượng tệp.

Không nhầm `Files.createLink` với `Files.createSymbolicLink`: chúng biểu diễn hai quan hệ hoàn toàn khác nhau.

## 8. Broken link, duyệt cây và các lỗi dễ mắc

### 8.1. `exists` mặc định thường theo link

Nếu `alias.txt -> missing.txt` và `missing.txt` không tồn tại:

~~~java
Files.isSymbolicLink(link);                         // true
Files.exists(link);                                 // false: đích không có
Files.exists(link, LinkOption.NOFOLLOW_LINKS);      // true: entry link còn
Files.readSymbolicLink(link);                       // trả về đường dẫn tham chiếu
Files.deleteIfExists(link);                         // xóa link dangling
~~~

Đây là đoạn minh họa, giả định `link` là dangling symbolic link. Vì vậy, **đừng dùng duy nhất `Files.exists(link)` để quyết định link có cần xóa hay không**. Kết quả `exists` còn có thể bị ảnh hưởng bởi thiếu quyền hoặc lỗi I/O, nên khi cần biết nguyên nhân chính xác hãy dùng API ném ngoại lệ thích hợp.

### 8.2. Duyệt thư mục để copy/delete đệ quy

- `Files.walkFileTree(root, visitor)` **mặc định không đi theo symbolic link**. Khi gặp link, visitor thường xử lý bản thân entry; nếu xóa entry này, referent ở ngoài/ở trong cây không bị xóa qua link.
- Bật `FileVisitOption.FOLLOW_LINKS` nghĩa là cho phép đi theo symbolic link khi duyệt; điều này có thể đưa chương trình vào thư mục ngoài phạm vi mong muốn, hoặc gặp chu trình liên kết và `FileSystemLoopException`. Phải xác định rõ ranh giới và xử lý lỗi, nhất là với đường dẫn do người dùng cung cấp.
- `Files.walk(root)` cũng không đi theo symbolic link theo mặc định; chỉ đi theo khi cấp `FOLLOW_LINKS`. Không coi thao tác duyệt + xóa là nguyên tử; nội dung thư mục có thể đổi giữa lúc kiểm tra và lúc thực hiện.

### 8.3. Những sai lầm cần tránh

| Ngộ nhận | Thực tế |
|---|---|
| “`Files.copy(link, x)` luôn tạo một link mới” | Mặc định sao chép **referent**; muốn sao chép link phải chỉ định `NOFOLLOW_LINKS`. |
| “`NOFOLLOW_LINKS` là tùy chọn dành cho `move`/`delete`” | Hai thao tác này **vốn đã tác động lên symbolic link** khi đường dẫn đầu vào là link. |
| “`REPLACE_EXISTING` ghi đè tệp mà destination link trỏ đến” | Nó thay **entry destination link**, không ghi đè referent của link ấy. |
| “Move link sang thư mục khác vẫn luôn hoạt động như cũ” | Link tương đối có thể trỏ sai sau khi đổi thư mục chứa. |
| “Xóa link đến một thư mục sẽ xóa cây thư mục đích” | `Files.delete(link)` chỉ xóa link; cây thư mục thật còn nguyên. |
| “`Files.exists(link) == false` chắc chắn không có entry link” | Dangling symbolic link có thể vẫn tồn tại; dùng `NOFOLLOW_LINKS` khi cần kiểm tra entry. |
| “Hard link giống file shortcut” | Hard link là tên bổ sung của cùng một đối tượng tệp, không phải một đối tượng chứa chuỗi đường dẫn đích. |

## 9. Cách chọn thao tác cho đúng yêu cầu

1. **Muốn một bản dữ liệu độc lập:** `Files.copy(link, copy)` nếu muốn theo referent, và đảm bảo đó là đúng hành vi cần thiết.
2. **Muốn nhân bản một symbolic link:** `Files.copy(link, newLink, LinkOption.NOFOLLOW_LINKS)`; sau đó xác minh `Files.readSymbolicLink(newLink)` và đường dẫn tương đối.
3. **Muốn đổi tên hoặc chuyển nơi chứa shortcut:** `Files.move(link, destination)`; kiểm tra lại referent nếu chuyển thư mục.
4. **Muốn gỡ shortcut mà giữ tệp gốc:** `Files.delete(link)` hoặc `Files.deleteIfExists(link)`.
5. **Muốn xóa tệp gốc:** chỉ định đúng **đường dẫn của tệp gốc** sau khi xác minh quyền, phạm vi và danh tính đích; chấp nhận rằng các symbolic link trỏ đến nó có thể bị dangling.
6. **Muốn xóa/copy toàn cây thư mục:** thiết kế một lượt duyệt rõ ràng và chỉ bật `FOLLOW_LINKS` nếu thật sự cần, với biện pháp tránh chu trình và vượt phạm vi.

**Câu hỏi tự kiểm tra:**

- `alias -> data.txt`. Sau `Files.copy(alias, snapshot)` rồi sửa `data.txt`, `snapshot` có đổi theo không? **Không**: bản copy mặc định là dữ liệu độc lập.
- Sau `Files.copy(alias, alias2, NOFOLLOW_LINKS)` rồi sửa `data.txt`, đọc qua `alias2` có thể thấy gì? **Nội dung mới của data.txt**, nếu `alias2` còn trỏ đúng.
- Sau `Files.move(alias, archive/alias)`, `data.txt` có tự đi vào `archive` không? **Không**. Tham chiếu tương đối của link có thể bị broken.
- Sau `Files.delete(alias)`, `data.txt` có bị xóa không? **Không**.
- `destination` đang là symbolic link trỏ tới tệp quan trọng. `Files.copy(source, destination, REPLACE_EXISTING)` tác động đến cái gì? **Thay entry link tại destination**; không theo destination link để ghi đè tệp quan trọng.

### Tài liệu tham khảo

- [Java SE 17 — `Files` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html): đặc tả `copy`, `move`, `delete`, `createLink`, `createSymbolicLink`, `walkFileTree`.
- [Dev.java — Manipulating Files and Directories](https://dev.java/learn/api/io/java-io/file-system/move-copy-delete/): thao tác sao chép, di chuyển và xóa tệp/thư mục.
- [Java SE 17 — `LinkOption.NOFOLLOW_LINKS`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/LinkOption.html): ý nghĩa không theo symbolic link.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-links-008">Vòng liên kết và giới hạn khi duyệt cây</a>

<details>
<summary>Xem chi tiết</summary>

# Vòng liên kết và giới hạn khi duyệt cây tệp trong Java NIO

Khi duyệt một cây thư mục, mỗi thư mục có vẻ như dẫn tới các thư mục con. Tuy nhiên, **symbolic link** (liên kết tượng trưng, gọi tắt là symlink) có thể trỏ tới một thư mục đã đi qua, tới tổ tiên của nó hoặc tới một nhánh bên ngoài thư mục bắt đầu. Nếu chương trình đi theo các liên kết mà không kiểm soát, nó có thể duyệt lặp, xử lý trùng dữ liệu, đi ra ngoài phạm vi mong muốn hoặc tốn quá nhiều tài nguyên.

Java NIO.2 cung cấp hai cơ chế quan trọng: **chọn có đi theo symbolic link hay không** và **giới hạn độ sâu duyệt**. Với API `Files.walkFileTree`, Java còn phát hiện một số vòng liên kết để báo cho `FileVisitor` xử lý.

## 1. Hiểu liên kết và vòng liên kết

Một symbolic link là một mục trong hệ thống tệp chứa tham chiếu tới đường dẫn đích. Nó có thể trỏ tới tệp, thư mục, đích chưa tồn tại hoặc đường dẫn bên ngoài cây đang duyệt. Ví dụ:

```text
project/
├── docs/
│   ├── guide.txt
│   └── back -> ../        # symlink trỏ về project/
└── output/
```

Nếu đi theo `docs/back`, từ `project/docs` ta trở lại `project`, rồi lại có thể đi vào `docs/back`. Đây là **vòng liên kết (link cycle)**.

Cần phân biệt ba tình huống:

- **Vòng liên kết:** Đường đi đang duyệt dẫn lại tới một thư mục *tổ tiên trên chính nhánh duyệt hiện tại*. Ví dụ `docs/back -> ../`. Khi bật theo symlink, `walkFileTree` phát hiện vòng này và chuyển lỗi `FileSystemLoopException` cho `visitFileFailed`.
- **Duyệt trùng nhưng không tạo vòng trên nhánh hiện tại:** Hai symlink khác nhau cùng trỏ tới một thư mục. Cơ chế phát hiện vòng của `walkFileTree` không hứa loại bỏ mọi lần thăm trùng; một thư mục vẫn có thể được xử lý nhiều lần qua các nhánh khác nhau.
- **Liên kết vượt ra ngoài cây bắt đầu:** Ví dụ `project/external -> /other/data`. Đây không nhất thiết là vòng, nhưng có thể vượt ranh giới nghiệp vụ hoặc bảo mật nếu bật theo symlink.

**Hard link** là tên đường dẫn khác cùng tham chiếu tới một đối tượng tệp; nó khác symlink. Trên các hệ thống tệp thông dụng, hard link tới thư mục thường bị cấm, nên bài toán vòng khi duyệt thư mục chủ yếu liên quan tới symbolic link, mount point và những cách hệ thống tệp biểu diễn lại một vị trí. Không nên coi việc tránh symlink là cơ chế loại trừ mọi tệp trùng nhau.

## 2. Hành vi mặc định của Java NIO

`Files.walkFileTree(start, visitor)` duyệt từ `start` với hành vi mặc định **không theo symbolic link** và độ sâu tối đa về mặt API là `Integer.MAX_VALUE`. `Files.walk(start)` cũng không theo symlink nếu không truyền `FileVisitOption.FOLLOW_LINKS`.

| Cấu hình | Gặp symlink tới thư mục | Hệ quả |
| --- | --- | --- |
| Không có `FOLLOW_LINKS` (mặc định) | Xem symlink như một mục tệp được thăm qua `visitFile`, không đi sâu vào đích | Tránh vòng gây ra bởi việc đi theo symlink đó |
| Có `FOLLOW_LINKS` | Cố đọc thuộc tính và duyệt thư mục đích | Có thể duyệt trùng, ra khỏi gốc; vòng trên nhánh hiện tại được báo bằng `FileSystemLoopException` |
| Có hoặc không theo link, kèm `maxDepth` | Chỉ xuống tới độ sâu đã định | Giới hạn mức lồng nhau, không giới hạn số mục ở mỗi mức |

Ví dụ hai cách gọi tương ứng:

```java
// Không theo symbolic link, giới hạn đến độ sâu 3.
Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 3, visitor);

// Theo symbolic link, cũng chỉ xuống đến độ sâu 3.
Files.walkFileTree(root, EnumSet.of(FileVisitOption.FOLLOW_LINKS), 3, visitor);
```

Đây là những **đoạn lệnh minh họa**, cần đặt trong chương trình có `root` kiểu `Path`, `visitor` kiểu `FileVisitor<Path>` và phần import thích hợp. Chương trình đầy đủ nằm ở phần 5.

Lưu ý: `Files.isSymbolicLink(path)` kiểm tra chính mục liên kết. Trong khi đó, `attrs.isSymbolicLink()` phản ánh bộ thuộc tính mà thao tác duyệt đã đọc; khi bật `FOLLOW_LINKS`, bộ thuộc tính có thể thuộc **đối tượng đích**, vì vậy hai kiểm tra không phải lúc nào cũng cho cùng kết quả.

## 3. Giới hạn độ sâu `maxDepth` hoạt động thế nào?

Độ sâu được tính từ đường dẫn `start`:

- **Độ sâu 0:** Chính `start`.
- **Độ sâu 1:** Các mục con trực tiếp.
- **Độ sâu 2:** Các mục cháu; tiếp tục tương tự.

Với cây `project/docs/chapters/intro.md`, nếu `project` là gốc thì `docs` ở độ sâu 1, `chapters` ở độ sâu 2, `intro.md` ở độ sâu 3. Khi `maxDepth = 2`, Java không duyệt tới `intro.md`.

Chi tiết dễ nhầm của `walkFileTree`: **một thư mục nằm đúng mức `maxDepth` được đưa tới `visitFile`, chứ không gọi `preVisitDirectory` rồi mở các mục con của nó**. Tương tự, `maxDepth = 0` đưa chính gốc tới `visitFile`, kể cả khi gốc là thư mục. Vì vậy mã trong `visitFile` không nên giả định mọi đường dẫn nhận được đều là tệp thông thường.

Tham số `maxDepth` phải không âm. Giá trị nhỏ giúp tránh đi quá sâu, nhưng không chặn một thư mục ở độ sâu 1 chứa hàng triệu mục. Nó cũng **không phải** hạn mức thời gian, tổng số tệp, dung lượng đọc hay công cụ ngăn theo symlink đi ra ngoài gốc.

## 4. Đường đi vòng được báo và xử lý ra sao?

Khi có `FOLLOW_LINKS`, `walkFileTree` kiểm tra một thư mục trước khi đi sâu để nhận diện việc nó trùng với một tổ tiên trên nhánh hiện tại. Java có thể dùng `fileKey` từ thuộc tính tệp hoặc so sánh tính đồng nhất của tệp khi cần. Nếu phát hiện vòng, API **không tiếp tục đệ quy vô hạn qua liên kết đó**; nó gọi:

```java
visitFileFailed(Path file, IOException exc)
```

với `exc` thuộc loại `FileSystemLoopException`. Đây là nơi chương trình quyết định ghi log và tiếp tục, hay dừng.

Một số kết quả của `FileVisitResult`:

| Kết quả | Ý nghĩa trong `FileVisitor` |
| --- | --- |
| `CONTINUE` | Tiếp tục duyệt theo quy tắc hiện hành |
| `SKIP_SUBTREE` | Khi trả từ `preVisitDirectory`, bỏ qua các mục con của thư mục đó |
| `SKIP_SIBLINGS` | Bỏ qua các mục anh em còn lại tại mức liên quan |
| `TERMINATE` | Kết thúc toàn bộ cuộc duyệt |

Chỉ nên chủ động bỏ qua lỗi `FileSystemLoopException` nếu chính sách ứng dụng cho phép. Không nên viết `visitFileFailed(...){ return CONTINUE; }` cho **mọi** `IOException`: lỗi phân quyền, I/O hoặc thư mục biến mất cần được báo cáo hay xử lý có chủ đích, nếu không dữ liệu kết quả có thể thiếu mà người dùng không biết.

Với symbolic link hỏng (đích không tồn tại) hoặc đích không thể truy cập, hành vi cần xử lý cẩn thận: khi đang bật theo link mà không đọc được thuộc tính của đích, API có thể thử lấy thuộc tính của **chính link** và gọi `visitFile`; không nên giả định mọi link hỏng đều đi vào `visitFileFailed`. Nếu cần phân biệt rõ, kiểm tra `Files.isSymbolicLink(path)` và chính sách tồn tại/truy cập đích tại thời điểm xử lý.

## 5. Chương trình Java 17+ có thể biên dịch và chạy

Chương trình dưới đây cho người chạy chọn giới hạn độ sâu và bật/tắt theo symbolic link. Nó in nhãn `DIR`, `DIR_LIMIT`, `FILE`, `LINK` và `LOOP` để dễ quan sát hành vi. `FileSystemLoopException` được ghi nhận rồi bỏ qua đường vòng; các lỗi I/O khác được ném lại để người chạy biết cuộc duyệt không đầy đủ.

Tạo tệp **`TreeLinkWalk.java`**:

```java
import java.io.IOException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

public final class TreeLinkWalk {
    private TreeLinkWalk() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 3) {
            System.out.println("Usage: java TreeLinkWalk <root> [maxDepth] [followLinks]");
            return;
        }

        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        int maxDepth = args.length >= 2 ? Integer.parseInt(args[1]) : 3;
        if (maxDepth < 0) {
            throw new IllegalArgumentException("maxDepth must be >= 0");
        }
        if (args.length == 3
                && !args[2].equalsIgnoreCase("true")
                && !args[2].equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("followLinks must be true or false");
        }

        boolean followLinks = args.length == 3 && Boolean.parseBoolean(args[2]);
        EnumSet<FileVisitOption> options = followLinks
                ? EnumSet.of(FileVisitOption.FOLLOW_LINKS)
                : EnumSet.noneOf(FileVisitOption.class);

        Files.walkFileTree(root, options, maxDepth,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(
                            Path dir, BasicFileAttributes attrs) {
                        System.out.println("DIR       " + relative(root, dir));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(
                            Path file, BasicFileAttributes attrs) {
                        String kind = Files.isSymbolicLink(file) ? "LINK"
                                : attrs.isDirectory() ? "DIR_LIMIT" : "FILE";
                        System.out.printf("%-9s %s%n", kind, relative(root, file));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(
                            Path file, IOException exc) throws IOException {
                        if (exc instanceof FileSystemLoopException) {
                            System.out.println("LOOP      " + relative(root, file));
                            return FileVisitResult.CONTINUE;
                        }
                        throw exc;
                    }
                });
    }

    private static String relative(Path root, Path item) {
        String name = root.relativize(item).toString();
        return name.isEmpty() ? "." : name;
    }
}
```

Biên dịch rồi chạy, thay `project` bằng thư mục thực tế:

```bash
javac TreeLinkWalk.java
java TreeLinkWalk project 3 false
java TreeLinkWalk project 3 true
java TreeLinkWalk project 1 false
```

Trên Windows, có thể dùng đường dẫn đầy đủ đặt trong dấu nháy, chẳng hạn `java TreeLinkWalk "C:\data\project" 3 false`. Mã dùng API thuộc Java 17+ và không tự tạo, xóa hoặc ghi các tệp đang duyệt.

Với cây ở phần 1, nếu hệ thống hỗ trợ symlink và liên kết `back` tồn tại, kết quả **minh họa** khi giới hạn đủ sâu có thể chứa:

```text
# followLinks=false:
DIR       .
DIR       docs
FILE      docs/guide.txt
LINK      docs/back
DIR       output

# followLinks=true:
DIR       .
DIR       docs
FILE      docs/guide.txt
LOOP      docs/back
DIR       output
```

Đây là ví dụ về các dòng sẽ thấy, **không đảm bảo thứ tự**: hệ thống tệp không bắt buộc trả các mục con theo thứ tự hiển thị; dấu phân cách đường dẫn cũng khác nhau giữa Windows và Unix. Nếu thư mục nằm tại biên `maxDepth`, chương trình có thể in `DIR_LIMIT` thay cho `DIR`. Nếu không tạo được symlink vì quyền hệ thống hay hệ thống tệp không hỗ trợ, đừng giả định sẽ có dòng `LINK` hoặc `LOOP`.

Trong ví dụ, `visitFile` dùng `Files.isSymbolicLink(file)` để nhận diện *đường dẫn* là một link kể cả khi đang theo link; việc Java có gọi `preVisitDirectory`, `visitFile` hay `visitFileFailed` vẫn phụ thuộc loại đích, độ sâu và lỗi phát sinh.

## 6. Giữ cuộc duyệt trong vùng được phép

Giới hạn độ sâu **không bảo đảm** cuộc duyệt nằm bên trong thư mục gốc. Một symlink ở độ sâu 1 có thể dẫn ngay tới thư mục ngoài phạm vi. Khi ứng dụng chỉ cần quản lý tệp vật lý dưới gốc, lựa chọn đơn giản và an toàn hơn là **không bật `FOLLOW_LINKS`**.

Nếu nghiệp vụ buộc phải theo symlink nhưng không muốn xử lý mục nằm ngoài gốc, có thể kiểm tra đường dẫn thực trước khi xử lý:

```java
Path allowedRoot = root.toRealPath(); // Tính một lần trước khi walk.
Path realDirectory = dir.toRealPath();
if (!realDirectory.startsWith(allowedRoot)) {
    return FileVisitResult.SKIP_SUBTREE; // Trong preVisitDirectory.
}
```

Đây là **đoạn minh họa đặt trong `preVisitDirectory`**, với `root` và `dir` đã được khai báo. Với tệp thường trong `visitFile`, cần kiểm tra tương tự trước khi đọc nội dung; chỉ chặn thư mục sẽ bỏ sót symlink trỏ tới **tệp** ngoài gốc. `Path.startsWith(Path)` so sánh theo thành phần đường dẫn, không thay bằng `path.toString().startsWith(prefix)` vì tiền tố chuỗi như `/data/root-else` có thể bị nhận nhầm thành `/data/root`.

Kiểm tra `toRealPath()` có thể ném `IOException` nếu đích biến mất, bị từ chối truy cập hoặc không còn giải quyết được; cần xử lý theo chính sách lỗi. Hơn nữa, giữa lúc kiểm tra và lúc mở tệp, liên kết có thể bị thay đổi (**TOCTOU**). Do đó kiểu kiểm tra trên phù hợp làm hàng rào logic cho một cây tương đối ổn định, **không phải sandbox bảo mật tuyệt đối** trước tác nhân có khả năng sửa hệ thống tệp đồng thời. Với dữ liệu không tin cậy, ưu tiên không theo link, hạn chế quyền truy cập hệ điều hành và thiết kế cách mở tệp chống thay đổi đường dẫn phù hợp nền tảng.

## 7. Tránh nhầm giữa API duyệt và giới hạn tài nguyên

`Files.walk` trả về `Stream<Path>` và cần đóng:

```java
try (var paths = Files.walk(root, 2)) {
    paths.forEach(System.out::println);
}
```

Đây là đoạn minh họa trong phương thức có xử lý `IOException`. Theo mặc định stream không theo symlink. Khi dùng biến thể có `FOLLOW_LINKS`, lỗi I/O lúc lấy phần tử trong stream (kể cả vòng liên kết) có thể được bọc trong `UncheckedIOException`; mô hình callback `FileVisitor.visitFileFailed` chỉ thuộc `walkFileTree`. Dùng `walkFileTree` khi cần chính sách riêng cho từng thư mục và từng lỗi.

Một chương trình duyệt cây trong thực tế có thể cần thêm các giới hạn **độc lập**:

- **Số mục đã thăm:** Tăng bộ đếm; trả `TERMINATE` khi vượt ngưỡng.
- **Thời gian chạy:** So sánh thời điểm bắt đầu với deadline, kiểm tra trong callback; `maxDepth` không giới hạn thời gian.
- **Tổng dung lượng hoặc số byte đọc:** Kiểm tra trước và trong quá trình xử lý nội dung; việc duyệt đường dẫn chưa đồng nghĩa với đọc nội dung tệp.
- **Xử lý trùng:** Nếu không được xử lý cùng tệp/thư mục nhiều lần, quản lý tập đối tượng đã thấy theo danh tính tệp (`fileKey` khi đáng tin, hoặc phép so sánh `Files.isSameFile` khi thích hợp). Không dùng mỗi chuỗi đường dẫn làm định danh vật lý; hai đường dẫn khác nhau có thể tới cùng một đối tượng. `fileKey` cũng có thể `null`, và chi phí của các phép so sánh cần được cân nhắc.

Nếu muốn đếm hoặc giới hạn số mục bằng callback, nhớ rằng một mục ở biên độ sâu có thể được báo trong `visitFile`, ngay cả khi nó là thư mục.

## 8. Lỗi thường gặp và cách kiểm tra

| Lỗi thường gặp | Vì sao sai / cách khắc phục |
| --- | --- |
| Bật `FOLLOW_LINKS` nhưng nghĩ rằng mọi thư mục chỉ thăm một lần | Java phát hiện vòng với tổ tiên của nhánh hiện tại; các liên kết khác vẫn có thể khiến duyệt trùng. Cần chính sách chống trùng riêng nếu nghiệp vụ yêu cầu. |
| Nghĩ `maxDepth = 2` cho phép đi qua hai thư mục *con* rồi đọc mọi tệp trong đó | Gốc đã là độ sâu 0; mục ở độ sâu 2 là biên và không được mở thêm. |
| Trong `visitFile` luôn coi `attrs` là tệp thông thường | Ở mức độ sâu tối đa, callback có thể nhận đường dẫn thư mục; khi không theo link có thể nhận symlink. |
| Bỏ qua mọi `IOException` bằng `CONTINUE` | Cuộc duyệt có thể thiếu dữ liệu do từ chối truy cập hoặc I/O lỗi. Chỉ bỏ qua lỗi đã chấp nhận và ghi nhận đầy đủ. |
| Dùng `maxDepth` để ngăn đi ra ngoài gốc | Một liên kết nông có thể trỏ ra ngoài. Không theo link hoặc áp dụng chính sách đường dẫn thực và quyền truy cập. |
| Dùng `Files.walk` mà không đóng stream | Stream giữ tài nguyên của hệ thống tệp. Dùng `try-with-resources`. |

Có thể tự kiểm chứng trên một thư mục thử nghiệm riêng:

1. Tạo một cây ít nhất ba mức, rồi chạy `maxDepth` lần lượt bằng 0, 1, 2 và 3 để quan sát `DIR_LIMIT`.
2. Nếu hệ thống cho phép, tạo symlink từ thư mục con về thư mục tổ tiên; so sánh `followLinks=false` và `true`.
3. Thử một symlink trỏ tới thư mục bên ngoài gốc và một symlink có đích không tồn tại; quan sát sự khác nhau giữa vòng, vượt gốc và link hỏng.
4. Thử thư mục không có quyền truy cập nếu điều kiện môi trường cho phép; xác nhận lỗi không bị âm thầm bỏ qua.

**Quy tắc chọn mặc định:** Đối với bài toán tìm kiếm, thống kê hay xử lý tệp thông thường, hãy bắt đầu bằng **không theo symbolic link**, đặt một `maxDepth` phù hợp và xử lý tường minh lỗi I/O. Chỉ bật `FOLLOW_LINKS` khi thực sự cần duyệt cả đối tượng đích, đồng thời xác định cách xử lý vòng, duyệt trùng và giới hạn phạm vi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-links-009">Quyền và tính hỗ trợ theo hệ điều hành</a>

<details>
<summary>Xem chi tiết</summary>

# Quyền và tính hỗ trợ theo hệ điều hành khi liên kết tệp (Java NIO.2)

Liên kết tệp (file link) cho phép nhiều đường dẫn tham chiếu tới cùng dữ liệu hoặc cho phép một đường dẫn chuyển hướng sang đường dẫn khác. Trong Java, API `java.nio.file.Files` hỗ trợ **symbolic link** (liên kết mềm) và **hard link** (liên kết cứng), nhưng thao tác thành công hay không phụ thuộc vào **hệ điều hành, hệ thống tệp, quyền của tiến trình và chính sách bảo mật tại thời điểm chạy**. Việc biên dịch thành công trên Java 17+ không bảo đảm có thể tạo mọi loại liên kết trên mọi máy.

## 1. Hiểu đúng đối tượng được cấp quyền

| Tiêu chí | Symbolic link (symlink) | Hard link |
| --- | --- | --- |
| Cơ chế | Một mục hệ thống tệp lưu **đường dẫn đích**; hệ điều hành phân giải đích khi truy cập. | Một tên/mục thư mục khác cùng trỏ đến **đối tượng tệp hiện có** (thường cùng định danh tệp). |
| Đích khi tạo | Có thể chưa tồn tại: tạo ra *dangling symlink*. | Đối tượng hiện có phải tồn tại; thông thường tệp thường, không phải thư mục. |
| Tác động khi đổi/xóa tên đường dẫn gốc | Link có thể bị gãy nếu đường dẫn lưu trong link không còn hợp lệ. | Hard link khác vẫn truy cập được dữ liệu dù xóa tên gốc, miễn còn một liên kết/tệp đang được hệ thống giữ. |
| Hệ thống tệp khác | Có thể chứa đường dẫn tới nơi khác; truy cập chỉ thành công khi đích tới được. | Thường bắt buộc cùng một hệ thống tệp/volume. |
| Quyền truy cập | Quyền khi mở nội dung áp dụng theo đích thực tế và các thư mục trên đường đi. | Các tên chia sẻ cùng đối tượng nên quyền trên đối tượng tệp thường chung; quyền đổi tên/xóa mục thư mục vẫn phụ thuộc thư mục cha. |
| API Java | `Files.createSymbolicLink(link, target)` | `Files.createLink(link, existing)` |

Ví dụ: `/data/report.txt` là tệp thật; `/work/report-link.txt` có thể là symlink lưu `/data/report.txt`; còn `/data/report-copy-name.txt` là hard link tới đúng đối tượng tệp của `/data/report.txt`. **Hard link không tự sao chép bytes**. Tên gọi “shortcut” của Windows (`.lnk`) là định dạng tệp của Shell và **khác** symbolic link của hệ thống tệp: `Files.createSymbolicLink` không tạo `.lnk`.

Một **đường dẫn** (`Path`) là tên để định vị; một **đối tượng tệp** là tài nguyên được hệ thống tệp quản lý. Phân biệt hai thứ này giúp lý giải việc xóa link, quyền truy cập và thay đổi nội dung.

## 2. Những API Java cần dùng và quy tắc theo link

| API | Ý nghĩa, điểm cần nhớ |
| --- | --- |
| `Files.createSymbolicLink(link, target)` | Tạo symlink; `target` có thể là đường dẫn tương đối hoặc tuyệt đối. Đây là thao tác *tùy chọn* theo provider và quyền. |
| `Files.createLink(link, existing)` | Tạo hard link tới tệp hiện có; có thể bị từ chối do khác file store hoặc chính sách hệ thống. |
| `Files.isSymbolicLink(path)` | Kiểm tra bản thân mục `path` có phải symlink; không cần mở nội dung đích. |
| `Files.readSymbolicLink(path)` | Lấy **đường dẫn được lưu** trong symlink; không tự chuyển thành absolute hay kiểm tra đích tồn tại. |
| `Files.exists(path)` | Mặc định đi theo symlink; nếu đích không tồn tại có thể trả `false`. Cũng trả `false` khi không thể xác định sự tồn tại. |
| `Files.exists(path, LinkOption.NOFOLLOW_LINKS)` | Kiểm tra mục link, kể cả khi đích của nó bị gãy. |
| `Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)` | Xem metadata của link thay vì của đối tượng đích. |
| `path.toRealPath()` | Trả đường dẫn thật sau phân giải liên kết (theo mặc định), yêu cầu đường dẫn tồn tại. `toRealPath(NOFOLLOW_LINKS)` áp dụng chính sách không theo symlink. |
| `Files.isSameFile(a, b)` | So sánh hai đường dẫn có định danh cùng tệp hay không, có thể phát sinh `IOException`; hữu ích để kiểm chứng hard link. |
| `Files.delete(path)` | Nếu `path` là symlink, xóa **mục symlink**, không xóa tệp đích. Nếu xóa một hard link, xóa một tên của tệp. |
| `Files.walkFileTree(root, visitor)` | Mặc định **không** theo symlink tới thư mục. Bật `FileVisitOption.FOLLOW_LINKS` chỉ khi thật sự cần và xử lý vòng lặp. |

Với symlink tương đối, `target` được diễn giải **tương đối từ thư mục chứa link**, không phải working directory của Java. Ví dụ tạo `/app/links/log` trỏ tới `../logs/current.log` sẽ phân giải đến `/app/logs/current.log`. Nếu di chuyển chính link sang thư mục khác, ý nghĩa của target tương đối có thể thay đổi. `Path.normalize()` chỉ chuẩn hóa cú pháp dấu `.`/`..`; nó **không** mở link để xác nhận đích thực.

Một số khác biệt dễ bỏ sót:

- `Files.readString(link)` và nhiều thao tác đọc nội dung thường đi theo symlink; nhưng các API có tham số `LinkOption` cho phép chọn hành vi khác.
- `Files.copy(sourceLink, target)` mặc định sao chép **nội dung đích** của symlink nguồn; `Files.copy(sourceLink, target, LinkOption.NOFOLLOW_LINKS)` yêu cầu sao chép chính link khi được hỗ trợ. Không mặc định rằng thao tác sao chép/move nào cũng dereference giống nhau.
- `Files.move(sourceLink, dest)` di chuyển mục symlink nguồn, không đổi đường dẫn lưu trong link thành một đích “mới” cho bạn; hãy kiểm tra lại link tương đối sau khi di chuyển.
- `Files.walk` hoặc `walkFileTree` khi được bật `FOLLOW_LINKS` có thể gặp chu trình; `FileSystemLoopException` hoặc lỗi I/O liên quan phải được xử lý. Với cây dữ liệu người dùng cung cấp, mặc định không theo link thường an toàn hơn.

## 3. Điều kiện quyền: tạo link khác với truy cập tệp đích

Khi dùng `Files.createSymbolicLink(link, target)` hoặc `Files.createLink(link, existing)`, cần tách ba câu hỏi:

1. **Có quyền tạo mục mới tại thư mục chứa `link` không?** Trên Unix/POSIX, thường cần quyền ghi (`w`) và đi xuyên/truy cập (`x`) thư mục cha; còn ACL, sticky bit, mount hoặc chính sách bảo mật cũng có thể ảnh hưởng. Trên Windows, quyền này liên quan ACL/permission của thư mục và token tiến trình.
2. **Có quyền tạo loại link đó không?** Symlink/hard link có ràng buộc riêng của hệ điều hành, driver và file store. Ví dụ Windows có cơ chế đặc quyền tạo symlink; một số filesystem không hỗ trợ kiểu link đang yêu cầu.
3. **Có quyền thao tác với đích khi sử dụng link không?** Một symlink tạo được vẫn có thể trỏ đến tệp mà người gọi không có quyền đọc. Đổi tên hay xóa mục link có thể thành công trong khi đọc nội dung đích thất bại, và ngược lại.

Đối với Unix/POSIX, **9 bit `rwx`** gắn với owner/group/others. Với *tệp*, `r` cho phép đọc nội dung, `w` cho phép sửa nội dung, `x` cho phép thực thi theo cơ chế OS. Với *thư mục*, `r` hỗ trợ liệt kê tên, `w` hỗ trợ sửa mục con, `x` cho phép tìm kiếm/đi xuyên đường dẫn; cần kết hợp quyền hợp lệ trên các thư mục cha. Quyền của chính symlink trên nhiều hệ Unix không đóng vai trò kiểm soát đọc dữ liệu đích; quyền đích và đường đi mới quyết định. Không giả định rằng `chmod` trên symlink sẽ điều khiển quyền tệp đích theo cách mong muốn.

Với hard link, quyền trên nội dung tệp thường áp dụng cho **cùng đối tượng** qua tất cả tên. Việc xóa một tên thường xét quyền của thư mục chứa tên đó, không đơn thuần quyền `w` của tệp. Các hệ thống có thể hạn chế tạo hard link tới tệp không thuộc sở hữu người gọi, cấm hard link tới thư mục hoặc ngăn các kiểu truy cập có rủi ro.

`Files.isReadable(path)`, `Files.isWritable(path)` và `Files.isExecutable(path)` chỉ cho một **kiểm tra tại thời điểm gọi**. Kết quả có thể không phản ánh đầy đủ ACL, trạng thái tệp mở, sandbox, mount và thay đổi đồng thời; chúng **không cấp quyền**, cũng không thay thế xử lý `AccessDeniedException`/`IOException` trong thao tác thực tế. Không tạo chuỗi bảo mật `if (Files.isWritable(p)) Files.writeString(p, ...)` rồi xem điều kiện đầu là bảo đảm.

## 4. Tính hỗ trợ theo hệ điều hành và hệ thống tệp

| Môi trường | Symbolic link | Hard link | Cấp quyền và thuộc tính |
| --- | --- | --- | --- |
| **Linux / Unix** với hệ thống tệp hỗ trợ (ví dụ ext4) | Thường hỗ trợ link tới tệp/thư mục, kể cả đích tương đối và đích chưa tồn tại. | Thường hỗ trợ các tên cùng file store; hard link tới thư mục thường bị hạn chế/cấm. | Mode POSIX `rwx`, owner/group, ACL nếu filesystem cấu hình hỗ trợ; mount/chính sách bảo mật có thể giới hạn thêm. |
| **macOS** với APFS và cấu hình thông thường | Hỗ trợ symlink; link tương đối và dangling link có ý nghĩa tương tự Unix. | Hỗ trợ hard link cho tệp trong giới hạn filesystem; không suy rộng sang thư mục. | POSIX permissions, owner/group và cơ chế ACL/phân quyền bổ sung. |
| **Windows** với NTFS | Hỗ trợ symbolic link kiểu filesystem; quyền tạo có thể phụ thuộc đặc quyền, Developer Mode, chính sách máy và cách JDK/provider gọi API. | NTFS có hard link cho tệp; yêu cầu cùng volume và các điều kiện của OS. | ACL Windows là cơ chế chính; `DosFileAttributeView` thể hiện thuộc tính như read-only/hidden, **không tương đương** POSIX `rwx`. |
| **FAT/exFAT, ổ mạng, container, filesystem ảo/provider bên thứ ba** | Khả năng hỗ trợ tùy filesystem, server, mount và provider; có thể hoàn toàn không tạo được. | Tương tự; qua các filesystem/volume thường không được. | Attribute views và chính sách quyền có thể khác hệ điều hành host. |

Các ví dụ trong bảng mô tả **cấu hình thường gặp**, không phải hợp đồng bảo đảm của Java. `System.getProperty("os.name")` chỉ cho biết tên hệ điều hành; không cho biết ổ chứa `Path` có hỗ trợ symlink, hard link, POSIX, ACL hay không. Ứng dụng cần kiểm tra tại **file store thực** và thử thao tác được phép, xử lý lỗi cụ thể.

Trên Windows, tài liệu Microsoft mô tả việc tạo symbolic link bằng Win32 có thể yêu cầu quyền tương ứng; cờ `SYMBOLIC_LINK_FLAG_ALLOW_UNPRIVILEGED_CREATE` hoạt động trong điều kiện Developer Mode đã bật. **Không suy ra rằng chỉ bật Developer Mode thì mọi bản JDK/provider và mọi volume đều chắc chắn tạo được symlink**; Java chỉ cam kết báo lỗi khi không thực hiện được. Máy cơ quan có thể áp chính sách riêng. Việc bật Developer Mode là thay đổi cấu hình máy và không phải cách xử lý duy nhất hay tự động của chương trình Java.

Trong Docker/Kubernetes, CI, máy chủ dùng filesystem ảo hoặc shared folder, một đoạn code thành công ở máy lập trình vẫn có thể thất bại do user ID, read-only mount, symlink policy, quyền volume hoặc driver lưu trữ. Lựa chọn fallback phải dựa trên yêu cầu nghiệp vụ: **sao chép bytes không bảo toàn đặc tính đồng bộ của hard link**, còn ghi một đường dẫn trong cấu hình không tạo ra symlink thật.

## 5. Kiểm tra attribute views đúng cách

Java NIO.2 cung cấp nhiều view thuộc tính:

- `BasicFileAttributeView`: kích thước, thời điểm, loại tệp, định danh nếu provider có; khả dụng qua NIO.2 cho các tệp thông thường.
- `PosixFileAttributeView`: owner, group và chín bit quyền (`PosixFilePermission`). Dùng `Files.getPosixFilePermissions` và `Files.setPosixFilePermissions` **chỉ khi hỗ trợ**.
- `AclFileAttributeView`: ACL gồm các mục `AclEntry` và principal khi provider hỗ trợ. ACL phức tạp hơn ba nhóm owner/group/others, không thể quy đổi tổng quát thành `chmod 755`.
- `DosFileAttributeView`: các thuộc tính DOS như read-only, hidden, system, archive. Thuộc tính read-only **không phải mô hình đầy đủ của quyền truy cập**.
- `FileStore.supportsFileAttributeView("posix")` (hoặc `"acl"`, `"dos"`): nhận diện khả năng view của **file store**, **không** phải API kiểm tra khả năng tạo symlink/hard link hay quyền hiện tại của user. Một provider có thể hỗ trợ nhiều view đồng thời, kể cả view `dos` trên môi trường không phải Windows.

Khi cần xem chính link, truyền `LinkOption.NOFOLLOW_LINKS` vào phương thức phù hợp; khi cần quyền của dữ liệu đích, để mặc định theo link nhưng phải dự liệu đường dẫn đích bị gãy hoặc bị từ chối. Một số thao tác sửa thuộc tính của symlink bản thân nó không được provider hỗ trợ; tránh dùng `Files.setPosixFilePermissions(link, ...)` rồi cho rằng đã thiết lập quyền của “link” độc lập.

## 6. Ví dụ chạy được: thử hai loại liên kết, xử lý tính không hỗ trợ

Tạo tệp `LinkSupportDemo.java`:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class LinkSupportDemo {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("nio-links-");
        Path original = dir.resolve("source.txt");
        Path symlink = dir.resolve("shortcut.txt");
        Path hardlink = dir.resolve("second-name.txt");
        try {
            Files.writeString(original, "hello", StandardCharsets.UTF_8);
            try {
                // Relative target được tính từ thư mục chứa shortcut.txt.
                Files.createSymbolicLink(symlink, Path.of("source.txt"));
                System.out.println("symbolic=" + Files.isSymbolicLink(symlink));
                System.out.println("raw-target=" + Files.readSymbolicLink(symlink));
                System.out.println("through-link=" + Files.readString(symlink));
            } catch (IOException | UnsupportedOperationException | SecurityException ex) {
                System.out.println("symbolic-unavailable=" + ex.getClass().getSimpleName());
            }
            try {
                Files.createLink(hardlink, original);
                System.out.println("same-file=" + Files.isSameFile(original, hardlink));
                Files.writeString(hardlink, "updated", StandardCharsets.UTF_8);
                System.out.println("through-original=" + Files.readString(original));
            } catch (IOException | UnsupportedOperationException | SecurityException ex) {
                System.out.println("hard-unavailable=" + ex.getClass().getSimpleName());
            }
            System.out.println("posix-view=" +
                Files.getFileStore(original).supportsFileAttributeView("posix"));
        } finally {
            // Xóa các tên trước, sau đó xóa file và thư mục tạm.
            Files.deleteIfExists(symlink);
            Files.deleteIfExists(hardlink);
            Files.deleteIfExists(original);
            Files.deleteIfExists(dir);
        }
    }
}
```

Chạy bằng Java 17+:

```bash
javac --release 17 LinkSupportDemo.java
java LinkSupportDemo
```

Một lần chạy trên Linux với filesystem hỗ trợ cả hai loại link cho output:

```text
symbolic=true
raw-target=source.txt
through-link=hello
same-file=true
through-original=updated
posix-view=true
```

Giải thích: symlink lưu đường dẫn tương đối `source.txt`; đọc qua symlink trả nội dung `hello`. Hard link cung cấp tên thứ hai cho **cùng đối tượng tệp**, nên ghi `updated` qua `second-name.txt` khiến đọc từ `source.txt` thấy nội dung mới. Trên máy không hỗ trợ, có thể xuất hiện `symbolic-unavailable=...` hoặc `hard-unavailable=...` với tên exception cụ thể; `posix-view` cũng phụ thuộc file store. Đây là **kết quả có điều kiện**, không phải sáu dòng chắc chắn trên mọi OS.

Ví dụ dùng thư mục riêng được tạo qua `Files.createTempDirectory` và dọn trong `finally`; để đơn giản, nếu có tiến trình khác cố ý sửa thư mục tạm trong lúc chạy, thao tác dọn vẫn có thể ném `IOException`. Trong ứng dụng thực, log exception cùng path/file store và thực hiện cleanup phù hợp quyền sở hữu tài nguyên.

## 7. Ví dụ chạy được: quan sát và đặt quyền POSIX khi có hỗ trợ

Tạo `PermissionProbe.java`:

```java
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.Set;

public class PermissionProbe {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("nio-permission-", ".txt");
        try {
            FileStore store = Files.getFileStore(file);
            System.out.println("posix=" + store.supportsFileAttributeView("posix"));
            System.out.println("acl=" + store.supportsFileAttributeView("acl"));
            System.out.println("dos=" + store.supportsFileAttributeView("dos"));

            if (store.supportsFileAttributeView("posix")) {
                Set<PosixFilePermission> previous = Files.getPosixFilePermissions(file);
                try {
                    Files.setPosixFilePermissions(file,
                        PosixFilePermissions.fromString("rw-------"));
                    System.out.println("permissions=" + PosixFilePermissions.toString(
                        Files.getPosixFilePermissions(file)));
                } finally {
                    Files.setPosixFilePermissions(file, previous);
                }
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

```bash
javac --release 17 PermissionProbe.java
java PermissionProbe
```

Ví dụ trên một hệ Linux/JDK cụ thể:

```text
posix=true
acl=false
dos=true
permissions=rw-------
```

Ba dòng đầu **phụ thuộc Java provider và file store**, không phản ánh trực tiếp OS là gì; ví dụ `dos=true` có thể xuất hiện cả trên Linux. Dòng `permissions=rw-------` chỉ có khi POSIX view hỗ trợ và thao tác thay quyền thành công: owner có đọc/ghi, group và others không có bit `rwx` nào. Ví dụ khôi phục mode cũ trong `finally` trước khi xóa file tạm; ACL, effective access và chính sách OS vẫn có thể làm quyền thực tế phức tạp hơn chín bit này. Nếu bị từ chối thay quyền, `IOException` sẽ báo lỗi: không nên nuốt lỗi rồi giả định đã thiết lập bảo mật.

Đối với Windows ACL, dùng `AclFileAttributeView` sau khi kiểm tra khả năng hỗ trợ và hiểu đúng `UserPrincipal` / `AclEntry`. Đừng cố dùng `setPosixFilePermissions` thay thế ACL. Nếu mục tiêu chỉ là mở file để đọc/ghi, thường nên thực hiện thao tác NIO thật và xử lý lỗi thay vì cố thay quyền hệ thống.

## 8. Các tình huống lỗi và cách xử lý có chủ đích

| Hiện tượng / exception | Nguyên nhân cần kiểm tra | Phản ứng phù hợp |
| --- | --- | --- |
| `UnsupportedOperationException` từ tạo link | Provider không hỗ trợ thao tác hoặc thuộc tính yêu cầu. | Thông báo feature không hỗ trợ; chỉ chuyển sang giải pháp khác nếu hợp đồng ứng dụng cho phép. |
| `AccessDeniedException` / `FileSystemException` | Quyền thư mục, đặc quyền tạo symlink, chính sách Windows/Unix, mount, filesystem hoặc tệp đang dùng. | Ghi nhận thao tác, path, loại filesystem và thông điệp lỗi; kiểm tra quyền/config theo môi trường, không tự động nâng quyền. |
| `FileAlreadyExistsException` | Tên `link` đã có mục, có thể là symlink gãy. | Dùng tên mới hoặc chính sách xử lý xung đột rõ ràng; **không** xóa dữ liệu hiện có một cách mù quáng. |
| `NoSuchFileException` khi đọc qua link | Link bị gãy hoặc một thành phần của đường dẫn không tồn tại. | Phân biệt sự tồn tại của link và của đích bằng `NOFOLLOW_LINKS`. |
| Lỗi tạo hard link trên volume khác | Hard link không thể nối hai đối tượng ở hai file store khác nhau trong nhiều filesystem. | Đặt link cùng file store hoặc thay thiết kế lưu trữ, hiểu rằng copy không đồng nghĩa hard link. |
| `FileSystemLoopException` khi duyệt theo link | Một symlink quay về thư mục tổ tiên gây chu trình. | Tắt `FOLLOW_LINKS` nếu không cần; nếu cần thì bắt lỗi, giới hạn độ sâu và định nghĩa chính sách xử lý vòng lặp. |
| `SecurityException` | Truy cập bị chặn bởi chính sách bảo mật/runtime phù hợp cấu hình đang dùng. | Xem đúng cơ chế chặn; không đồng nhất với “filesystem không hỗ trợ”. |

**Không kiểm tra `Files.exists(link)` để quyết định chắc chắn có thể tạo link mới.** Symlink gãy có thể làm `exists(link) == false` dù mục link vẫn chiếm tên. Ngay cả `exists(link, NOFOLLOW_LINKS)` cũng chỉ là ảnh chụp tức thời, dễ bị race; thao tác tạo `createSymbolicLink`/`createLink` vẫn phải tự xử lý `FileAlreadyExistsException`.

Ví dụ nhanh để hiểu dangling symlink (chỉ chạy ở nơi được phép tạo symlink):

```java
Path dangling = dir.resolve("broken-link"); // dir đã tồn tại
Files.createSymbolicLink(dangling, Path.of("missing.txt"));
System.out.println(Files.exists(dangling)); // thường false: đích chưa có
System.out.println(Files.exists(dangling, LinkOption.NOFOLLOW_LINKS)); // true: link có
Files.delete(dangling); // chỉ xóa link
```

Đoạn trên là **minh họa phụ thuộc biến `dir` đã tạo trước**, không phải chương trình Java độc lập. Kết quả giả định việc tạo symlink thành công và không có tác nhân khác sửa đường dẫn.

## 9. An toàn và tính đúng đắn khi đường dẫn đến từ người dùng

- **Tránh symlink traversal:** nếu ứng dụng cho người dùng chọn tên file trong thư mục được kiểm soát, hãy đề phòng người khác tạo symlink dẫn tới file bên ngoài. `base.resolve(userInput).normalize().startsWith(base)` chỉ kiểm tra đường dẫn về mặt chữ, **không xác nhận** thực thể mà symlink dẫn tới.
- **Không tin vào check-then-use:** giữa `toRealPath`/`exists` và `Files.newInputStream` hay `Files.delete`, một tiến trình khác có thể đổi link. Dùng `LinkOption.NOFOLLOW_LINKS` nơi API cho phép khi muốn thao tác trên mục cuối; khi phải truy cập cây thư mục không tin cậy, xem xét `SecureDirectoryStream` (nếu provider hỗ trợ) hoặc cơ chế mở file an toàn do nền tảng cung cấp.
- **Rõ chính sách follow:** khi quét folder, backup, xóa cây, unpack archive hoặc đồng bộ dữ liệu, quyết định có theo symlink hay không. Theo link bừa bãi có thể lặp vô hạn, vượt ranh giới thư mục hoặc đụng tài nguyên không thuộc phạm vi.
- **Không “sửa lỗi quyền” bằng `chmod 777` / chạy Administrator mặc định:** chỉ cấp đúng quyền cần thiết cho tài khoản dịch vụ, thư mục và đối tượng. Kiểm tra ACL, user/group, quyền mount và policy trên môi trường triển khai.
- **Dự tính tính di động:** Java có API thống nhất nhưng không chuẩn hóa mọi semantics cấp OS. Một tính năng bắt buộc tạo symlink/hard link phải được kiểm tra trên filesystem mục tiêu trong môi trường thực; fallback và thông báo lỗi phải được xác định trước.

## 10. Checklist vận dụng

1. Xác định nghiệp vụ cần **đường dẫn chuyển hướng** (symlink), **nhiều tên chung một đối tượng** (hard link), hay **bản sao dữ liệu độc lập** (`Files.copy`): ba mục tiêu không thay thế nhau.
2. Xác định `Path` nguồn, vị trí tạo link, tồn tại đích và quan hệ volume/file store. Với symlink tương đối, tính target từ **thư mục chứa link**.
3. Kiểm tra view theo `FileStore` khi cần metadata/quyền; phân biệt POSIX, ACL và DOS; không dùng tên OS thay cho phép đo khả năng thực tế.
4. Thực hiện API tạo/đọc/xóa thật trong `try`/`catch`, xử lý lỗi quyền, unsupported, target gãy và xung đột tên, không dựa vào `exists` như khóa đồng bộ.
5. Quyết định rõ khi nào theo symlink, khi nào dùng `NOFOLLOW_LINKS`; kiểm tra đường dẫn từ nguồn không tin cậy và vấn đề race.
6. Thử trên Linux/macOS/Windows hoặc môi trường triển khai dự kiến **với đúng filesystem, user và mount**; không chỉ thử trên máy developer.

**Tự kiểm tra kiến thức**

- Vì sao xóa `shortcut.txt` (symlink) không xóa `source.txt`, còn sửa nội dung qua hard link thường làm thay đổi dữ liệu nhìn thấy ở mọi tên?
- Vì sao `Files.exists(brokenLink)` có thể trả `false` trong khi `Files.exists(brokenLink, NOFOLLOW_LINKS)` trả `true`?
- Nếu `FileStore.supportsFileAttributeView("posix")` trả `true`, đã có thể kết luận tạo symbolic link thành công chưa? **Chưa:** attribute view và khả năng/quyền tạo link là hai vấn đề khác nhau.
- Khi chạy cùng code trên Windows NTFS và ổ FAT/exFAT hoặc ổ mạng, yếu tố nào có thể khác? **Loại link được hỗ trợ, yêu cầu đặc quyền, ACL, thuộc tính, mount/driver và các lỗi I/O.**
- Tại sao một đường dẫn chuẩn hóa bằng `normalize()` vẫn có thể vượt ra ngoài thư mục gốc thông qua symlink? Vì `normalize()` không phân giải link trên filesystem.

## Tài liệu chính thức

- Java SE 21: [`Files` — `createSymbolicLink`, `createLink`, `readSymbolicLink`, `walkFileTree`, `NOFOLLOW_LINKS`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html).
- Java SE 21: [`PosixFileAttributeView` — quyền, owner, group và mode bit](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/attribute/PosixFileAttributeView.html).
- Microsoft Learn: [`CreateSymbolicLinkW` — đặc quyền và cờ unprivileged create](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-createsymboliclinkw); [Developer Mode trên Windows](https://learn.microsoft.com/en-us/windows/advanced-settings/developer-mode).

Các mô tả ở đây áp dụng cho API chuẩn Java NIO.2 (Java 17+); khả năng thực tế luôn phụ thuộc provider/file store và cấu hình hệ điều hành.

</details>

- [Quay lại đầu trang](#back-to-top)
