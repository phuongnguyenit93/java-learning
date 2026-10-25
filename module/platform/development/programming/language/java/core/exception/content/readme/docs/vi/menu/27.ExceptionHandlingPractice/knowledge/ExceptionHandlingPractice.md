<a id="back-to-top"></a>

# Thực hành

## Menu
- [Xây dựng quy trình đọc file có kiểm tra dữ liệu đầu vào](#exception-handling-practice-001)
- [So sánh xử lý tại chỗ với lan truyền bằng throws](#exception-handling-practice-002)
- [Chuyển nhiều khối catch giống nhau thành multi-catch](#exception-handling-practice-003)
- [Tái cấu trúc cleanup thủ công sang try-with-resources](#exception-handling-practice-004)
- [Tạo ngoại lệ nghiệp vụ có cause và dữ liệu ngữ cảnh](#exception-handling-practice-005)
- [Tái hiện lỗi chính kèm nhiều suppressed exception](#exception-handling-practice-006)
- [Thiết kế API giữ trạng thái hợp lệ khi có lỗi](#exception-handling-practice-007)
- [Xử lý lỗi và ngắt trong tác vụ ExecutorService](#exception-handling-practice-008)
- [So sánh phục hồi lỗi bằng exceptionally, handle và whenComplete](#exception-handling-practice-009)
- [Hoàn thiện ví dụ tích hợp với kiểm thử các tình huống thất bại](#exception-handling-practice-010)

## <a id="exception-handling-practice-001">Xây dựng quy trình đọc file có kiểm tra dữ liệu đầu vào</a>

<details>
<summary>Xem chi tiết</summary>

# Xây dựng quy trình đọc file có kiểm tra dữ liệu đầu vào — Java Core Exception

## 1. Mục tiêu và đầu ra thực hành

Bài này xây dựng một quy trình **nhận tên tệp từ người dùng, kiểm tra dữ liệu đầu vào, đọc nội dung văn bản UTF-8 và báo lỗi có ý nghĩa** bằng Java 17+. Trọng tâm là ranh giới giữa **lỗi validation** và **lỗi I/O thực tế**, cách bảo toàn <code>cause</code>, và cách tránh dùng những phép kiểm tra trước như lời bảo đảm sai lầm rằng thao tác đọc chắc chắn thành công.

Hoàn thành bài học, bạn có thể:

- Vẽ luồng từ **input thô → validation → xác định tài nguyên → mở file → đọc/giải mã → kết quả hoặc lỗi**.
- Kiểm tra <code>null</code>, chuỗi rỗng, ký tự không được phép, đuôi file và quy tắc đặt tên.
- Hạn chế tên file tương đối về một thư mục gốc được ứng dụng kiểm soát; hiểu vấn đề path traversal, symbolic link và TOCTOU.
- Phân biệt <code>IllegalArgumentException</code>, <code>InvalidPathException</code>, <code>NoSuchFileException</code>, <code>AccessDeniedException</code>, <code>CharacterCodingException</code>, <code>IOException</code> và lỗi nghiệp vụ đã được chuyển đổi.
- Dùng <code>try-with-resources</code> để đóng stream kể cả khi đọc hoặc giải mã thất bại.
- Áp dụng giới hạn byte được đọc thay vì tin một precheck kích thước file là đủ.
- Báo lỗi theo mã/loại ổn định, giữ cause để chẩn đoán và không gửi message kỹ thuật thô cho người dùng.
- Chạy một ví dụ Java 17 độc lập, có dữ liệu mẫu được tạo tự động, stdout xác định và không cần tệp chuẩn bị sẵn.
- Thiết kế các test cho nhánh thành công, dữ liệu đầu vào sai, lỗi encoding và lỗi I/O bị giả lập.

**Giới hạn bảo mật:** chương trình thực hành dùng một thư mục tạm do chính chương trình tạo và chỉ chấp nhận **tên file đơn**, không có thư mục con. Nó có kiểm tra ký tự và tránh đi theo symbolic link ở thành phần file cuối trên hệ thống file hỗ trợ. Đây là ví dụ học cơ chế exception và kiểm tra đầu vào; **không phải sandbox chống mọi thao tác đồng thời của kẻ tấn công có quyền sửa thư mục gốc**. Các trường hợp thư mục cha bị thay thế, mount point, file provider đặc biệt và đối tượng file bị thay đổi giữa các bước cần thiết kế bảo mật/đồng bộ hóa nâng cao riêng.

## 2. Yêu cầu của quy trình

Giả sử ứng dụng cho phép người dùng chọn tên một tệp ghi chú, chẳng hạn <code>notes.txt</code>, từ một thư mục do ứng dụng quản lý.

Quy tắc của bài thực hành:

1. Người dùng chỉ nhập **một tên file**, không phải đường dẫn đầy đủ và không được chuyển sang thư mục con.
2. Chỉ đọc file đuôi chính xác <code>.txt</code>, phân biệt chữ hoa/chữ thường theo hợp đồng ví dụ.
3. Tên file bắt đầu bằng ký tự chữ hoặc số ASCII; các ký tự tiếp theo trước phần mở rộng chỉ có chữ, số, dấu chấm, gạch dưới hoặc gạch ngang.
4. File phải là tệp thông thường, không phải thư mục hoặc symbolic link ở vị trí đích.
5. Mỗi file được đọc tối đa **1.024 byte** trong ví dụ để giới hạn bộ nhớ dành cho nội dung.
6. Nội dung là UTF-8 **hợp lệ**, không âm thầm thay byte sai bằng ký tự thay thế.
7. Khi có lỗi, caller nhận một exception có <code>code</code> rõ ràng; với lỗi từ hệ thống file/decoder, <code>cause</code> được giữ.
8. Tài nguyên stream phải đóng dù đọc thành công hay thất bại.
9. Phần trình bày public chỉ dùng mã lỗi và thông điệp an toàn, không lấy nguyên đường dẫn/exception message kỹ thuật.

Các quy tắc trên là **quyết định hợp đồng của bài học**, không phải luật chung của Java. Ứng dụng khác có thể cho phép nhiều thư mục con, tên Unicode, kích thước lớn hơn hoặc định dạng file khác, miễn là kiểm tra và chính sách truy cập tương ứng được thiết kế rõ ràng.

## 3. Luồng xử lý tổng thể

~~~text
Input String fileName
        |
        v
Kiểm tra null / blank / đuôi / ký tự được phép
        |
        +---- không hợp lệ ------> ReadFailure(INVALID_NAME / UNSUPPORTED_TYPE)
        |
        v
Xác định target = trustedBaseDir.resolve(fileName)
        |
        v
Kiểm tra sơ bộ loại tệp, không theo symlink của tệp cuối
        |
        +---- là thư mục/symlink -> ReadFailure(NOT_REGULAR_FILE)
        |
        v
Mở stream để đọc, try-with-resources
        |
        +---- không tồn tại -----> ReadFailure(NOT_FOUND, cause)
        +---- không đủ quyền ---> ReadFailure(ACCESS_DENIED, cause)
        +---- lỗi I/O khác -----> ReadFailure(READ_FAILED, cause)
        |
        v
Đọc tối đa MAX_BYTES + 1
        |
        +---- vượt giới hạn ----> ReadFailure(TOO_LARGE)
        |
        v
Giải mã UTF-8 ở chế độ REPORT
        |
        +---- byte sai ----------> ReadFailure(INVALID_UTF8, cause)
        |
        v
Trả về String thành công
~~~

**Vì sao cần tách các giai đoạn?** Lỗi nhập tên file là một tình huống mà code có thể phát hiện **trước I/O**, trong khi file không tồn tại, bị từ chối quyền truy cập hoặc biến mất do race chỉ được xác định đáng tin cậy bằng thao tác thực tế. Mỗi nhóm lỗi cần chính sách báo cáo khác nhau.

## 4. Phân loại lỗi trước khi viết code

| Mã lỗi ví dụ | Nguồn gốc | Ý nghĩa |
| --- | --- | --- |
| <code>INVALID_NAME</code> | Validation | Chuỗi tên file rỗng hoặc chứa đường dẫn/ký tự ngoài quy tắc |
| <code>UNSUPPORTED_TYPE</code> | Validation | Đuôi file không phải <code>.txt</code> |
| <code>NOT_REGULAR_FILE</code> | Chính sách tài nguyên | Đích được biết là thư mục, symbolic link hoặc loại không cho phép |
| <code>NOT_FOUND</code> | File system | Không mở được file vì đường dẫn không tồn tại |
| <code>ACCESS_DENIED</code> | File system | Hệ thống từ chối quyền truy cập |
| <code>TOO_LARGE</code> | Giới hạn đầu vào | Nội dung vượt số byte tối đa được phép đọc |
| <code>INVALID_UTF8</code> | Decoder | Byte trong file không tạo thành chuỗi UTF-8 hợp lệ |
| <code>READ_FAILED</code> | File system/stream | Lỗi I/O khác không nằm trong các trường hợp cụ thể trên |

**Không suy diễn:** một file chưa từng tồn tại và một file vừa bị xóa trước lúc mở có thể cho cùng <code>NoSuchFileException</code>. Ngược lại, một phép kiểm tra tồn tại trước khi mở có thể trả kết quả lỗi thời hoặc không kết luận được. Code phải dựa trên **hợp đồng của thao tác thật**.

Nếu sản phẩm cần biết chính xác tình trạng tài nguyên tại một thời điểm đồng thời, có thể cần cơ chế quản lý phiên bản/file handle/transaction riêng; chỉ phân loại exception không giải quyết vấn đề đó.

## 5. Kiểm tra null, blank và tên file

### 5.1. Null không giống chuỗi rỗng

~~~java
if (fileName == null || fileName.isBlank()) {
    throw new IllegalArgumentException("Tên file không được trống");
}
~~~

Đây là **mã minh họa** trong phương thức có biến <code>fileName</code> kiểu <code>String</code>. Biểu thức <code>||</code> có đánh giá ngắn mạch nên không gọi <code>isBlank()</code> khi <code>fileName == null</code>.

Các đầu vào cần cân nhắc gồm:

- <code>null</code>.
- <code>""</code>, <code>"   "</code> hoặc chuỗi chỉ chứa ký tự whitespace.
- <code>"notes.txt"</code> hợp lệ.
- <code>"../secret.txt"</code> dùng thành phần thư mục cha.
- <code>"sub/notes.txt"</code> chứa đường dẫn tương đối nhiều thành phần.
- <code>"C:\\private\\notes.txt"</code> kiểu đường dẫn Windows.
- <code>"notes.csv"</code> sai loại file theo chính sách bài.
- Chuỗi rất dài, chứa ký tự điều khiển hoặc ký tự bất hợp lệ theo file system.

Ví dụ chọn **allowlist** tên file thay vì cố liệt kê mọi ký tự nguy hiểm. Điều này có thể loại trừ một số tên file hoàn toàn hợp lệ trên hệ điều hành; đó là hệ quả có chủ đích của quy tắc bài thực hành.

### 5.2. Validation không phải biến một đường dẫn bất kỳ thành “an toàn”

Một ý tưởng đơn giản nhưng nguy hiểm:

~~~java
Path target = baseDir.resolve(userInput);
~~~

Đây là **mã minh họa**, yêu cầu import <code>java.nio.file.Path</code>. Nếu <code>userInput</code> chứa <code>..</code> hoặc là đường dẫn tuyệt đối, kết quả có thể không còn nằm trong thư mục mong muốn. Chuẩn hóa và kiểm tra ranh giới đường dẫn cần được thiết kế ngay từ đầu.

Với bài chỉ cần **một tên file**, cho phép ký tự trong một pattern chặt thường đơn giản hơn việc xử lý tùy ý mọi thành phần đường dẫn:

~~~java
if (!fileName.matches("[A-Za-z0-9][A-Za-z0-9._-]*\\.txt")) {
    throw new IllegalArgumentException("Tên file không hợp lệ");
}
~~~

Đây là **mã minh họa**. Quy tắc trên chấp nhận <code>note_01.txt</code> và từ chối dấu gạch chéo, dấu gạch chéo ngược cùng các trường hợp có thành phần thư mục cha. Nếu cần tên file Unicode, hãy xác định rõ bộ ký tự và giới hạn mới, không đơn giản bỏ mọi kiểm tra.

### 5.3. Thứ tự kiểm tra ảnh hưởng mã lỗi

Nếu tên là <code>notes.csv</code>, ví dụ muốn trả <code>UNSUPPORTED_TYPE</code>. Nếu tên là <code>../secret.txt</code>, ví dụ muốn trả <code>INVALID_NAME</code>. Vì vậy, pipeline chọn:

1. Kiểm tra null/blank.
2. Nếu tên không kết thúc bằng <code>.txt</code>, báo unsupported type.
3. Nếu tên tuy có đuôi <code>.txt</code> nhưng sai allowlist, báo invalid name.

Cách phân loại được công bố này giúp test xác định; ứng dụng khác có thể quyết định báo chung <code>INVALID_NAME</code> cho mọi đầu vào không hợp lệ nhằm giảm rò rỉ về chính sách.

## 6. Xử lý Path và InvalidPathException

Java NIO cung cấp <code>Path.of(...)</code> hoặc <code>Paths.get(...)</code>. Với chuỗi có ký tự không hợp lệ theo file system, thao tác tạo đường dẫn có thể ném <code>InvalidPathException</code>, là unchecked exception.

~~~java
try {
    Path supplied = Path.of(rawInput);
} catch (InvalidPathException e) {
    throw new IllegalArgumentException("Đường dẫn không hợp lệ", e);
}
~~~

Đây là **mã minh họa** với import <code>java.nio.file.Path</code> và <code>java.nio.file.InvalidPathException</code>. Không cần catch loại này trong demo có allowlist tên file rất chặt, nhưng trong API cho phép chuỗi path tự do, nên phân loại nó như lỗi input theo hợp đồng thay vì trả nguyên thông báo nội bộ cho người dùng.

### normalize và startsWith

Với ứng dụng cho phép đường dẫn tương đối nhiều thành phần, ví dụ:

~~~java
Path root = baseDir.toAbsolutePath().normalize();
Path supplied = Path.of(rawInput);

if (supplied.isAbsolute()) {
    throw new IllegalArgumentException("Chỉ cho phép đường dẫn tương đối");
}

Path target = root.resolve(supplied).normalize();

if (!target.startsWith(root)) {
    throw new IllegalArgumentException("Đường dẫn vượt thư mục gốc");
}
~~~

Đây là **đoạn minh họa**, giả định <code>baseDir</code> và <code>rawInput</code> đã được cung cấp. <code>Path.startsWith(Path)</code> kiểm tra theo các thành phần đường dẫn, không phải phép so chuỗi ký tự đơn giản.

**Giới hạn quan trọng:** kiểm tra trên đường dẫn đã normalize chỉ xử lý một phần nguy cơ *lexical traversal*. Symbolic link có thể trỏ sang vị trí bên ngoài dù đường dẫn biểu kiến nằm trong root. Hơn nữa, file hoặc symlink có thể bị thay thế giữa thời điểm kiểm tra và lúc mở. Một dịch vụ nhận đường dẫn từ nguồn không tin cậy phải bổ sung cơ chế xử lý symbolic link và race phù hợp môi trường, chứ không tuyên bố <code>normalize()</code> là “sandbox”.

Trong demo bên dưới, filename chỉ có một thành phần nên không chứa slash hoặc <code>..</code> đứng thành phần riêng. Đây là **giảm bề mặt rủi ro nhờ hợp đồng đầu vào**, không phải bằng chứng chống được mọi tấn công trên file system.

## 7. Vì sao không chỉ dùng Files.exists trước khi đọc?

Một đoạn code tưởng như an toàn:

~~~java
if (Files.exists(path)) {
    return Files.readString(path);
}
return "";
~~~

Đây là **mã minh họa**, yêu cầu import <code>java.nio.file.Files</code> và một phương thức xử lý <code>IOException</code> thích hợp. Nó có nhiều vấn đề:

- File có thể bị xóa hoặc đổi tên giữa <code>exists</code> và <code>readString</code>.
- Quyền truy cập có thể thay đổi.
- <code>exists</code> trả false không nhất thiết chứng minh file vĩnh viễn không tồn tại: truy vấn metadata có thể không kết luận được trong một số điều kiện.
- <code>readString</code> có thể thất bại vì encoding, bộ nhớ, thiết bị, permission hoặc lỗi I/O khác ngay cả khi precheck vừa thành công.
- Trả chuỗi rỗng cho “không tồn tại” sẽ lẫn với file thực sự tồn tại và chứa nội dung rỗng.

Phương án phù hợp là xem **thao tác đọc** như nguồn sự thật về thành công/thất bại, bắt các exception cụ thể mà caller cần phân biệt. Precheck như <code>Files.isRegularFile</code> có thể phục vụ trải nghiệm người dùng/chính sách, nhưng **không thay thế** xử lý lỗi khi mở.

~~~java
try {
    return Files.readString(path, StandardCharsets.UTF_8);
} catch (NoSuchFileException e) {
    throw new DocumentNotFoundException("Không tìm thấy tài liệu", e);
} catch (IOException e) {
    throw new DocumentReadException("Không thể đọc tài liệu", e);
}
~~~

Đây là **mã minh họa**; hai loại exception custom cần được định nghĩa trong dự án. Dùng constructor nhận <code>Throwable cause</code> giúp giữ lỗi gốc.

## 8. Kiểm tra loại file và symbolic link

Java NIO có:

- <code>Files.isRegularFile(path)</code>: thông thường theo symbolic link.
- <code>Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)</code>: yêu cầu không theo symbolic link ở đích khi lấy thông tin loại file.
- <code>Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)</code>: phân biệt thư mục theo cách tương ứng.
- <code>Files.isSymbolicLink(path)</code>: kiểm tra entry là symbolic link.
- <code>Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)</code>: trên file system provider hỗ trợ option, tránh đi theo symbolic link ở entry file được mở.

Mỗi phép metadata check ở trên **không phải lock**. Nếu bên khác có quyền sửa file system, entry có thể thay đổi sau lúc check. Ở môi trường chịu tấn công race/symlink, cần đánh giá API mở file một cách ràng buộc, quyền OS, tính an toàn của thư mục gốc và có thể dùng <code>SecureDirectoryStream</code> khi provider hỗ trợ.

Trong chương trình thực hành, thư mục là một temp directory được tạo riêng và không có tác nhân khác sửa, vì vậy kiểm tra loại file trước khi mở chủ yếu dùng để có **mã lỗi dễ hiểu** như <code>NOT_REGULAR_FILE</code>. Code vẫn phải bắt <code>IOException</code> của việc mở thật.

### Symlink không phải thư mục thường

Một symbolic link có thể trỏ đến file thực nằm ngoài root. Không nên gọi <code>Files.readString</code> mặc định trên tên do người dùng điều khiển rồi tin rằng việc check <code>target.startsWith(root)</code> bảo đảm dữ liệu chỉ nằm dưới root.

Trong ví dụ, với filename đơn không có parent do người dùng cung cấp, <code>NOFOLLOW_LINKS</code> được dùng khi mở. Trong dịch vụ cho phép đường dẫn con và thư mục gốc có thể bị thay thế, **chỉ ngăn follow ở entry cuối chưa đủ**: những thành phần đường dẫn cha cũng phải được bảo vệ.

## 9. Đọc UTF-8: mặc định thay thế byte sai hay báo lỗi?

Một tệp đuôi <code>.txt</code> **không bảo đảm** nội dung là UTF-8 hợp lệ. Nếu hệ thống công bố “mọi nội dung phải là UTF-8 hợp lệ”, việc gặp byte sai phải trở thành lỗi dữ liệu, không bị tự động chuyển thành ký tự thay thế và coi như thành công.

Với Java NIO Charset API:

~~~java
CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);

String content = decoder.decode(ByteBuffer.wrap(bytes)).toString();
~~~

Đây là **mã minh họa**; cần các import từ <code>java.nio</code> và <code>java.nio.charset</code>, biến <code>bytes</code> kiểu <code>byte[]</code> đã được cấp. Chế độ <code>REPORT</code> khiến dữ liệu mã hóa sai phát sinh <code>CharacterCodingException</code> (ví dụ <code>MalformedInputException</code>) thay vì âm thầm thay bằng ký tự U+FFFD.

Một số API đọc text cấp cao như <code>Files.readString(path, StandardCharsets.UTF_8)</code> cũng có thể ném <code>IOException</code> khi gặp dữ liệu UTF-8 sai. Bài demo sử dụng decoder tường minh để **làm rõ hợp đồng** và kết hợp với giới hạn số byte đọc.

### Byte limit khác với character limit

UTF-8 dùng từ một đến nhiều byte cho mỗi Unicode code point. Vì vậy:

- File 1.024 byte không nhất thiết chứa 1.024 ký tự Java.
- Số ký tự Java (UTF-16 code unit) cũng không phải số code point.
- Nếu giới hạn theo **byte** để bảo vệ việc đọc/bộ nhớ, kiểm tra **byte**, không chỉ độ dài sau khi giải mã.
- Nếu sản phẩm cần thêm giới hạn ký tự, kiểm tra sau khi đã xác định chính sách ký tự/Unicode.

Demo đặt <code>MAX_BYTES = 1024</code> và đọc tối đa <code>MAX_BYTES + 1</code>. Khi nhận 1.025 byte, kết luận vượt giới hạn. Nó không dựa vào riêng <code>Files.size()</code>, vốn chỉ là snapshot metadata có thể thay đổi.

## 10. Try-with-resources: đóng stream ở mọi đường

Ví dụ:

~~~java
try (InputStream input = Files.newInputStream(path)) {
    byte[] bytes = input.readNBytes(1025);
    return decoder.decode(ByteBuffer.wrap(bytes)).toString();
}
~~~

Đây là **mã minh họa** trong một phương thức có các biến và exception contract phù hợp. Cú pháp <code>try-with-resources</code> gọi <code>close()</code> khi rời khối, bao gồm khi:

- Đọc xong bình thường.
- <code>readNBytes</code> ném <code>IOException</code>.
- Decoder ném <code>CharacterCodingException</code>.
- Code tự ném exception do vượt giới hạn.
- Code xử lý bên trong trả về sớm.

Nếu cả thao tác chính và đóng stream cùng lỗi, Java giữ lỗi đóng ở danh sách <code>getSuppressed()</code> của exception chính theo hợp đồng try-with-resources. Khi chuyển sang exception tầng ứng dụng, nên giữ cause để không vô tình mất thông tin này.

**Không dùng <code>finally</code> có thể ném exception mới không kiểm soát** rồi làm lỗi gốc bị che. Try-with-resources giúp quản lý quan hệ lỗi chính/lỗi đóng đáng tin cậy hơn khi tài nguyên là <code>AutoCloseable</code>.

## 11. Chương trình Java 17+ hoàn chỉnh: ValidatedFileReadDemo

Chương trình này:

1. Tạo một thư mục tạm riêng.
2. Tạo một file UTF-8 hợp lệ, một file rỗng, một file chứa byte UTF-8 sai, một file vượt giới hạn và một thư mục mang tên đuôi <code>.txt</code>.
3. Thử đọc các đầu vào hợp lệ/sai và in mã lỗi rõ ràng.
4. Giữ nguyên cause khi lỗi đến từ hệ thống file hoặc decoder.
5. Xóa dữ liệu thử nghiệm sau khi chạy.

Lưu **toàn bộ** vào file <code>ValidatedFileReadDemo.java</code>:

~~~java
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public class ValidatedFileReadDemo {
    private static final int MAX_BYTES = 1024;

    enum Code {
        INVALID_NAME,
        UNSUPPORTED_TYPE,
        NOT_REGULAR_FILE,
        NOT_FOUND,
        ACCESS_DENIED,
        TOO_LARGE,
        INVALID_UTF8,
        READ_FAILED
    }

    static final class ReadFailure extends Exception {
        private final Code code;

        ReadFailure(Code code, String message) {
            super(message);
            this.code = code;
        }

        ReadFailure(Code code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        Code code() {
            return code;
        }
    }

    static String readText(Path trustedRoot, String name)
            throws ReadFailure {
        if (name == null || name.isBlank()) {
            throw new ReadFailure(
                    Code.INVALID_NAME, "Tên file không được trống");
        }
        if (!name.endsWith(".txt")) {
            throw new ReadFailure(
                    Code.UNSUPPORTED_TYPE, "Chỉ hỗ trợ .txt");
        }
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9._-]*\\.txt")) {
            throw new ReadFailure(
                    Code.INVALID_NAME, "Tên file có ký tự không hợp lệ");
        }

        Path target = trustedRoot.resolve(name);

        // Chỉ là kiểm tra phân loại tiện lợi, không thay cho open().
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(
                        target, LinkOption.NOFOLLOW_LINKS)) {
            throw new ReadFailure(
                    Code.NOT_REGULAR_FILE, "Đích không phải file thường");
        }

        try (InputStream input =
                Files.newInputStream(target, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                throw new ReadFailure(
                        Code.TOO_LARGE, "File vượt giới hạn byte");
            }

            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new ReadFailure(
                    Code.INVALID_UTF8, "File không phải UTF-8 hợp lệ", e);
        } catch (NoSuchFileException e) {
            throw new ReadFailure(
                    Code.NOT_FOUND, "Không tìm thấy file", e);
        } catch (AccessDeniedException e) {
            throw new ReadFailure(
                    Code.ACCESS_DENIED, "Không có quyền đọc file", e);
        } catch (IOException e) {
            throw new ReadFailure(
                    Code.READ_FAILED, "Không thể đọc file", e);
        }
    }

    static void show(Path root, String name) {
        String label = name == null ? "<null>" : "\"" + name + "\"";

        try {
            String content = readText(root, name);
            System.out.println(label + " -> OK ["
                    + content.strip() + "]");
        } catch (ReadFailure error) {
            System.out.println(label + " -> " + error.code());
        }
    }

    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("file-read-practice-");

        try {
            Files.writeString(
                    root.resolve("ok.txt"),
                    "Xin chào\n",
                    StandardCharsets.UTF_8);
            Files.writeString(
                    root.resolve("empty.txt"),
                    "",
                    StandardCharsets.UTF_8);
            Files.write(
                    root.resolve("invalid.txt"),
                    new byte[] {(byte) 0xC3, (byte) 0x28});
            Files.write(
                    root.resolve("big.txt"),
                    new byte[MAX_BYTES + 1]);
            Files.createDirectory(root.resolve("folder.txt"));

            show(root, "ok.txt");
            show(root, "empty.txt");
            show(root, "missing.txt");
            show(root, "folder.txt");
            show(root, "invalid.txt");
            show(root, "big.txt");
            show(root, "note.csv");
            show(root, "../secret.txt");
            show(root, null);
        } finally {
            Files.deleteIfExists(root.resolve("ok.txt"));
            Files.deleteIfExists(root.resolve("empty.txt"));
            Files.deleteIfExists(root.resolve("invalid.txt"));
            Files.deleteIfExists(root.resolve("big.txt"));
            Files.deleteIfExists(root.resolve("folder.txt"));
            Files.deleteIfExists(root);
        }
    }
}
~~~

Biên dịch/chạy với Java 17+:

~~~bash
javac -encoding UTF-8 ValidatedFileReadDemo.java
java ValidatedFileReadDemo
~~~

**Stdout kỳ vọng trên file system thông thường có hỗ trợ các thao tác thử nghiệm:**

~~~text
"ok.txt" -> OK [Xin chào]
"empty.txt" -> OK []
"missing.txt" -> NOT_FOUND
"folder.txt" -> NOT_REGULAR_FILE
"invalid.txt" -> INVALID_UTF8
"big.txt" -> TOO_LARGE
"note.csv" -> UNSUPPORTED_TYPE
"../secret.txt" -> INVALID_NAME
<null> -> INVALID_NAME
~~~

### 11.1. Vì sao output có thứ tự xác định?

Mọi lời gọi <code>show(...)</code> được thực hiện tuần tự từ <code>main</code>, không dùng thread pool hoặc bất đồng bộ. Không có file đầu vào nào phụ thuộc vào thư mục làm việc của người chạy, vì tất cả dữ liệu thử đều được tạo trong temp directory trước khi test.

Với file system thông thường có thể tạo/xóa file, cho phép mở file tạm và hỗ trợ <code>NOFOLLOW_LINKS</code>, các kết quả trên có thể dự đoán được. **Không khẳng định** đây là output khi hệ điều hành từ chối tạo thư mục tạm, ổ đĩa hết dung lượng, provider không hỗ trợ option, hoặc một tiến trình khác cố ý thay đổi thư mục trong lúc chương trình chạy. Những tình huống đó là lỗi hạ tầng ngoài kịch bản kiểm thử thường.

### 11.2. Từng dòng thể hiện điều gì?

- <code>"ok.txt"</code>: tên hợp lệ, file có thể mở, kích thước nhỏ, UTF-8 hợp lệ. <code>strip()</code> ở <code>show</code> chỉ loại whitespace đầu/cuối **khi hiển thị demo**; hàm <code>readText</code> vẫn trả nội dung gốc gồm ký tự newline.
- <code>"empty.txt"</code>: file tồn tại và rỗng vẫn là **đọc thành công**, không bị nhầm với <code>NOT_FOUND</code>.
- <code>"missing.txt"</code>: tên được phép nhưng file không có; mở stream ném <code>NoSuchFileException</code>, được chuyển sang <code>NOT_FOUND</code>.
- <code>"folder.txt"</code>: entry tồn tại nhưng là thư mục; kiểm tra <code>isRegularFile(..., NOFOLLOW_LINKS)</code> dẫn tới <code>NOT_REGULAR_FILE</code>.
- <code>"invalid.txt"</code>: chuỗi byte <code>C3 28</code> không hợp lệ theo UTF-8; decoder ở chế độ REPORT tạo <code>CharacterCodingException</code>, được chuyển sang <code>INVALID_UTF8</code>.
- <code>"big.txt"</code>: có 1.025 byte trong khi giới hạn cho phép là 1.024, nên trả <code>TOO_LARGE</code> trước giải mã.
- <code>"note.csv"</code>: thất bại ở validation loại file, không thử đọc.
- <code>"../secret.txt"</code>: có đuôi .txt nhưng không khớp allowlist, nên thất bại trước khi tạo target.
- <code>null</code>: thất bại ở validation null, không truy cập file system.

### 11.3. Byte giới hạn được kiểm tra sau khi mở

Hàm dùng:

~~~java
byte[] bytes = input.readNBytes(MAX_BYTES + 1);
if (bytes.length > MAX_BYTES) {
    throw new ReadFailure(Code.TOO_LARGE, "File vượt giới hạn byte");
}
~~~

Đây là **đoạn minh họa được trích từ chương trình**. Nó không chỉ đọc metadata <code>Files.size()</code> rồi giả định kích thước sẽ không đổi. Với mục tiêu giới hạn lượng dữ liệu **được tích lũy vào mảng**, việc đọc tối đa thêm một byte cho phép nhận biết trường hợp vượt ngưỡng.

Giới hạn này không phải quota toàn bộ tài nguyên hệ thống: việc mở file, allocation khác, các bộ đệm nội bộ và tải song song vẫn tiêu thụ tài nguyên. Khi xử lý tệp lớn, streaming, timeout, cancellation, quota theo request và quản lý concurrent requests cần được thiết kế bổ sung.

### 11.4. Việc loại symbolic link trong ví dụ có giới hạn

- Allowlist không cho người dùng nhập thư mục con hoặc path traversal bằng dấu gạch chéo.
- Kiểm tra <code>isRegularFile(..., NOFOLLOW_LINKS)</code> có thể từ chối symbolic link ở đích khi check nhìn thấy nó.
- <code>newInputStream(target, NOFOLLOW_LINKS)</code> được dùng để yêu cầu không theo symlink ở đích khi mở, theo mức hỗ trợ của provider.
- Nhưng <code>trustedRoot</code> phải thật sự là thư mục do ứng dụng kiểm soát. Nếu một bên khác có thể thay root hoặc namespace file system, chính sách chống race/symlink phải chặt hơn.

**Không dùng code demo như một sandbox bảo mật hoàn chỉnh** cho dịch vụ có người dùng không tin cậy được phép tạo/sửa symlink trong cấu trúc thư mục.

## 12. Bảo toàn cause và không đưa lỗi kỹ thuật thô ra client

Trong chương trình, khi mở không thấy file:

~~~java
} catch (NoSuchFileException e) {
    throw new ReadFailure(
            Code.NOT_FOUND, "Không tìm thấy file", e);
}
~~~

Đây là **đoạn minh họa trong chuỗi catch**. Vì constructor <code>ReadFailure(..., Throwable cause)</code> gọi <code>super(message, cause)</code>, tầng trên có thể dùng:

~~~java
ReadFailure error = ...;
Throwable cause = error.getCause();
~~~

Đây là **mã minh họa**, biến <code>error</code> phải được lấy từ một khối catch hoặc kết quả test phù hợp. <code>getCause()</code> có thể là null với lỗi validation, và khác null với lỗi đọc được chuyển từ exception của Java NIO.

### Ba nhóm dữ liệu cần phân biệt

| Thành phần | Mục đích | Có thể gửi thẳng tới người dùng? |
| --- | --- | --- |
| <code>ReadFailure.code()</code> | Phân loại ổn định cho caller | Có thể, nếu thuộc hợp đồng public |
| <code>ReadFailure.getMessage()</code> | Diễn đạt lỗi ở tầng ứng dụng | Chỉ khi nội dung đã được duyệt là an toàn |
| <code>ReadFailure.getCause()</code> | Chẩn đoán lỗi kỹ thuật, stack trace | Thường không; phải theo chính sách truy cập/log |
| <code>target Path</code> | File thực trên hệ thống | Không tự động; có thể lộ cấu trúc thư mục |
| <code>requestId</code> | Tra cứu sự cố từ phía hỗ trợ | Có thể nếu định dạng không chứa secret/PII |

Một API thực tế có thể ánh xạ:

~~~json
{
  "code": "FILE_UNAVAILABLE",
  "message": "Không thể đọc tệp lúc này. Vui lòng thử lại sau.",
  "requestId": "RQ-1234"
}
~~~

Đây là **JSON minh họa**, không phải server HTTP có thể chạy từ ví dụ. Thông báo public không đưa đường dẫn OS, hostname, stack trace hoặc token lấy từ exception message gốc.

### Không gộp mọi lỗi vào “Không tìm thấy”

Nếu đọc file bị từ chối quyền truy cập hoặc encoding sai, trả <code>NOT_FOUND</code> có thể khiến người dùng/ứng dụng đưa ra quyết định sai. Việc phân loại chi tiết ở tầng nội bộ không bắt buộc công bố mọi thông tin kỹ thuật, nhưng giúp log và chương trình xử lý đúng.

Có hệ thống cố tình không phân biệt <code>NOT_FOUND</code> và <code>ACCESS_DENIED</code> trong **public response** nhằm tránh tiết lộ thông tin tài nguyên. Đây là quyết định security boundary; trong nội bộ vẫn cần phân biệt nguyên nhân khi phù hợp.

## 13. Kiểm thử không dựa vào file system thật để mô phỏng IOException

Ví dụ ở mục 11 phù hợp cho **smoke test tích hợp** với file system. Nhưng khi cần kiểm tra đúng logic chuyển exception, việc cố thay quyền của một file thật trên máy chạy CI có thể thiếu ổn định:

- Windows, Linux và file system khác nhau về quyền.
- Process test có thể chạy với quyền quản trị và không bị chặn như dự đoán.
- Antivirus, agent backup, race hoặc mount lạ có thể ảnh hưởng kết quả.
- Không nên thao tác lên file thực của người dùng chỉ để làm test.

Thay vào đó, tách thao tác I/O qua một interface có thể giả lập để kiểm tra việc giữ cause và phân loại lỗi.

### Ví dụ Java 17 độc lập: fake reader ném IOException xác định

Lưu tệp **ReadFailureCauseDemo.java**:

~~~java
import java.io.IOException;
import java.nio.file.NoSuchFileException;

public class ReadFailureCauseDemo {
    interface Reader {
        String read(String name) throws IOException;
    }

    enum Code {
        NOT_FOUND,
        READ_FAILED
    }

    static final class LoadFailure extends Exception {
        private final Code code;

        LoadFailure(Code code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        Code code() {
            return code;
        }
    }

    static String load(Reader reader, String name) throws LoadFailure {
        try {
            return reader.read(name);
        } catch (NoSuchFileException e) {
            throw new LoadFailure(
                    Code.NOT_FOUND, "Không tìm thấy tài liệu", e);
        } catch (IOException e) {
            throw new LoadFailure(
                    Code.READ_FAILED, "Không thể đọc tài liệu", e);
        }
    }

    static void require(boolean condition) {
        if (!condition) {
            throw new AssertionError("Exception contract failed");
        }
    }

    public static void main(String[] args) {
        NoSuchFileException original =
                new NoSuchFileException("missing.txt");
        Reader missing = name -> {
            throw original;
        };
        Reader working = name -> "CONTENT";

        try {
            load(missing, "missing.txt");
            throw new AssertionError("Expected LoadFailure");
        } catch (LoadFailure e) {
            require(e.code() == Code.NOT_FOUND);
            require(e.getCause() == original);
            System.out.println("Missing code: " + e.code());
            System.out.println("Same original cause: "
                    + (e.getCause() == original));
        }

        try {
            System.out.println("Working result: "
                    + load(working, "ok.txt"));
        } catch (LoadFailure e) {
            throw new AssertionError("Unexpected failure", e);
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac -encoding UTF-8 ReadFailureCauseDemo.java
java ReadFailureCauseDemo
~~~

**Stdout chính xác:**

~~~text
Missing code: NOT_FOUND
Same original cause: true
Working result: CONTENT
~~~

Giải thích:

- <code>Reader</code> mô tả thao tác đọc có thể ném <code>IOException</code>.
- Fake <code>missing</code> ném **chính đối tượng** <code>NoSuchFileException</code> được tạo sẵn.
- <code>load</code> chuyển nó thành <code>LoadFailure</code> nhưng giữ cause nguyên bản.
- Test dùng <code>==</code> để xác nhận identity; trong JUnit 5, assertion tương ứng là <code>assertSame(original, e.getCause())</code>.
- Fake <code>working</code> minh họa nhánh thành công mà không cần tạo file thực.
- Đây là ví dụ để học **exception translation**, không phải bản thay thế cho bộ kiểm tra tên file và byte/encoding của demo chính.

## 14. Test cases cần có

| Input/tình huống | Kỳ vọng | Mục tiêu test |
| --- | --- | --- |
| <code>null</code> | <code>INVALID_NAME</code> | Guard null chạy trước các thao tác khác |
| Chuỗi rỗng hoặc blank | <code>INVALID_NAME</code> | Không mở I/O với tên vô nghĩa |
| Tên có <code>../</code> | <code>INVALID_NAME</code> | Không chấp nhận traversal qua tên file |
| Tên có slash/backslash | <code>INVALID_NAME</code> nếu đúng đuôi .txt | Không chấp nhận thư mục con theo policy |
| <code>note.csv</code> | <code>UNSUPPORTED_TYPE</code> | Phân biệt đuôi file sai |
| File <code>.txt</code> không tồn tại | <code>NOT_FOUND</code> | Phân loại NoSuchFileException |
| File rỗng | Chuỗi <code>""</code> thành công | Không đánh đồng rỗng với không tồn tại |
| File UTF-8 hợp lệ | Chuỗi đúng, không làm mất newline | Đọc và giải mã đúng |
| File chứa byte UTF-8 sai | <code>INVALID_UTF8</code> | Decoder dùng chế độ REPORT |
| File vượt 1.024 byte | <code>TOO_LARGE</code> | Không tích lũy không giới hạn |
| Một thư mục có tên đuôi .txt | <code>NOT_REGULAR_FILE</code> trong môi trường demo | Không coi directory là text file |
| Symlink ở file đích | Bị từ chối theo policy và provider tương ứng | Không vô tình theo symlink |
| Không đủ quyền | <code>ACCESS_DENIED</code> khi API ném AccessDeniedException | Không báo sai thành NOT_FOUND |
| Lỗi I/O khác | <code>READ_FAILED</code> | Có nhánh fallback giữ cause |
| Lỗi chuyển tầng | Cause identity được giữ | Không chỉ sao chép message |
| Close stream gặp lỗi | Lỗi đóng không bị im lặng mất | Tác dụng của try-with-resources |

### JUnit 5 minh họa

Trong một lớp test JUnit 5 với import và dữ liệu fixture phù hợp:

~~~java
ReadFailureCauseDemo.Reader failing = name -> {
    throw original;
};

ReadFailureCauseDemo.LoadFailure error =
        assertThrows(ReadFailureCauseDemo.LoadFailure.class,
                () -> ReadFailureCauseDemo.load(failing, "missing.txt"));

assertEquals(ReadFailureCauseDemo.Code.NOT_FOUND, error.code());
assertSame(original, error.getCause());
~~~

Đây là **mã minh họa không độc lập**, cần biến <code>original</code> là một <code>NoSuchFileException</code> được tạo trong test. Dùng các assertion JUnit Jupiter tương ứng. Không dựa vào message hệ điều hành hay số dòng stack trace khi chỉ cần kiểm tra code và cause.

Một test validation có thể dùng fake ghi lại số lần đọc để khẳng định input sai **không làm phát sinh I/O**. Nhưng hãy đặt assertion theo hợp đồng của tầng validate, không phụ thuộc chi tiết implementation mà caller không cần.

## 15. Tách logic validation và logic đọc để dễ mở rộng

Khi code lớn hơn, có thể tổ chức:

~~~text
FileNameValidator
    -> ValidatedName
    -> FileRepository (đọc nội dung)
    -> FileReadService (chuyển lỗi/áp hợp đồng)
    -> API hoặc CLI response mapper
~~~

Đây là **sơ đồ thành phần gợi ý**, không bắt buộc tạo đúng các lớp đó cho bài tập nhỏ.

Một cấu trúc rõ trách nhiệm:

1. **Validation:** chỉ biết quy tắc tên/loại file, không biết lỗi disk.
2. **Repository:** nhận tên hợp lệ/Path nội bộ và thực hiện open/read/close, báo exception kỹ thuật.
3. **Service:** áp chính sách kích thước/encoding và chuyển exception theo contract của tính năng.
4. **Adapter/API:** biến mã lỗi thành response an toàn, ghi correlation ID hoặc log có kiểm soát.

Không nhất thiết luôn cần bốn lớp: có thể gộp trong chương trình nhỏ nhưng giữ **ranh giới trách nhiệm trong tư duy**.

### Phân biệt validation lỗi trước I/O và lỗi phát hiện khi đọc

Ví dụ “tên file rỗng” có thể được xác định chỉ từ chuỗi đầu vào. Nhưng “file tồn tại và có quyền đọc” cần thao tác trên file system. API muốn công bố một trạng thái cụ thể khi file được thay đổi đồng thời phải xử lý điều đó như tình huống thực, không ỷ lại các check lúc trước.

## 16. Các quyết định về charset và nội dung

UTF-8 là lựa chọn phổ biến, nhưng phải được **công bố**. Nếu nội dung thực tế do một hệ thống khác ghi bằng charset khác, việc đọc UTF-8 có thể lỗi hoặc cho text sai.

Các tình huống cần xem xét:

- File có hoặc không có BOM.
- Newline kiểu <code>\n</code>, <code>\r\n</code> hoặc <code>\r</code>.
- Chuỗi có dấu tiếng Việt và ký tự Unicode ngoài ASCII.
- Byte invalid theo UTF-8.
- Giới hạn số byte so với giới hạn số ký tự.
- File rất lớn phải đọc theo streaming thay vì một mảng.
- Dữ liệu nhị phân được ngụy trang bằng đuôi <code>.txt</code>.
- Chuỗi có ký tự điều khiển mà domain không chấp nhận dù UTF-8 hợp lệ.

**Ví dụ chính chỉ kiểm tra UTF-8 well-formed**, không xác định một ngữ pháp nội dung cụ thể. Một chuỗi UTF-8 hợp lệ vẫn có thể sai nghiệp vụ, chẳng hạn một file cấu hình thiếu trường bắt buộc. Nếu cần parse CSV/JSON hoặc định dạng dòng, hãy có bước **validation nội dung sau decode** với exception/error code riêng.

### Không tự động trim nội dung gốc

Trong demo, <code>show</code> gọi <code>strip()</code> để in gọn, nhưng <code>readText</code> trả nguyên chuỗi đã decode. Nếu service tự ý trim, có thể làm mất whitespace hoặc newline mang ý nghĩa. Chỉ chuẩn hóa line endings, dấu cách hoặc BOM nếu hợp đồng dữ liệu yêu cầu.

## 17. Khi nào nên dùng Files.readString và khi nào nên streaming?

### Dữ liệu nhỏ, tin cậy và không cần hạn mức byte chặt

~~~java
String text = Files.readString(path, StandardCharsets.UTF_8);
~~~

Đây là **mã minh họa**, có thể đơn giản và dễ đọc nếu size được kiểm soát ở cấp kiến trúc/nguồn tin cậy. Nhưng nó có thể cần phân bổ bộ nhớ tương ứng kích thước file, không phù hợp nếu người dùng có thể đưa một file cực lớn vào đường đọc.

### Dữ liệu có giới hạn tài nguyên rõ

Trong demo, <code>InputStream.readNBytes(MAX_BYTES+1)</code> bảo đảm không tích lũy nội dung vượt ngưỡng bằng thao tác đọc này. Nó phù hợp với file nhỏ cần load toàn bộ vào bộ nhớ.

### File lớn hoặc dữ liệu cần xử lý từng dòng

Có thể dùng <code>BufferedReader</code> hoặc streaming:

~~~java
try (BufferedReader reader =
        Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
    String line;
    while ((line = reader.readLine()) != null) {
        process(line);
    }
}
~~~

Đây là **mã minh họa**, cần import <code>java.io.BufferedReader</code>, <code>Files</code>, <code>StandardCharsets</code> và định nghĩa <code>process</code>. Nếu có giới hạn số byte, số dòng hoặc độ dài một dòng, vẫn phải **chủ động triển khai hạn mức**; chỉ dùng <code>BufferedReader</code> không tự áp mọi quota.

Nếu cần chặn symlink, chọn API mở có hỗ trợ chính sách đó hoặc quản lý file descriptor/đường dẫn bằng cơ chế phù hợp. Không đơn giản chuyển từ <code>newInputStream</code> sang <code>newBufferedReader</code> rồi mặc định mọi quyền/symlink option vẫn y nguyên.

## 18. Logging và thông báo lỗi an toàn

Một quy trình đọc file không nên dùng:

~~~java
catch (IOException e) {
    System.out.println("Lỗi: " + e);
    return "";
}
~~~

Đây là **mã minh họa cố ý không khuyến nghị**:

- Trả <code>""</code> gây nhầm với file thực sự rỗng.
- In exception kỹ thuật trực tiếp có thể làm lộ đường dẫn nội bộ nếu stdout được đưa tới người dùng.
- Caller không còn biết có thao tác thất bại.
- Message từ hệ thống file có thể không ổn định giữa OS/JDK và có thể chứa thông tin nhạy cảm.

Tốt hơn là giữ <code>ReadFailure</code> với code ổn định và cause, rồi một lớp presentation quyết định:

~~~java
String publicMessage(Code code) {
    return switch (code) {
        case INVALID_NAME -> "Tên tệp không hợp lệ.";
        case UNSUPPORTED_TYPE -> "Chỉ hỗ trợ tệp .txt.";
        case TOO_LARGE -> "Tệp vượt dung lượng được phép.";
        case INVALID_UTF8 -> "Tệp không đúng định dạng UTF-8.";
        case NOT_FOUND -> "Không tìm thấy tệp.";
        case NOT_REGULAR_FILE -> "Tài nguyên không phải tệp hợp lệ.";
        case ACCESS_DENIED, READ_FAILED ->
                "Không thể đọc tệp lúc này.";
    };
}
~~~

Đây là **mã minh họa** với enum <code>Code</code> của ví dụ chính. Hệ thống thật có thể cố ý gom một số loại lỗi vào thông báo chung để bảo vệ thông tin tài nguyên. Nếu người dùng sửa được input, một thông báo hữu ích sẽ tốt hơn việc trả một stack trace kỹ thuật.

### Trường nào nên đi vào log?

- Mã lỗi và tên thao tác ổn định.
- Request ID/correlation ID an toàn.
- Thời gian, thành phần, thời lượng.
- Loại Throwable/cause cho kỹ sư có quyền.
- Tên tài nguyên nếu được phép ghi theo chính sách dữ liệu.

Không ghi token, password, dữ liệu riêng tư hoặc raw file content vào log chỉ vì exception đang xảy ra. Đừng lặp cùng stack trace ở mọi tầng nếu không thêm thông tin mới.

## 19. Xử lý input và security boundary thực tế

Các thành phần thường cần xem xét khi xây dựng dịch vụ production:

**Giới hạn đường dẫn.** Tốt nhất, người dùng cung cấp một **file ID** hoặc tên theo allowlist; server ánh xạ ID sang vị trí mà nó quản lý. Không cho client tự truyền đường dẫn tuyệt đối tới những vùng hệ thống nhạy cảm.

**Quyền truy cập.** Một tên file hợp lệ vẫn có thể trỏ tới tài nguyên mà người dùng không được quyền đọc. Validation cú pháp không thay authorization.

**Symlink.** Lexical normalize/startsWith không chặn symlink escape. <code>NOFOLLOW_LINKS</code> ở entry cuối không bảo vệ mọi parent nếu người dùng được phép thao tác trên parent. Môi trường chịu tấn công cần mở file bằng cơ chế chặt hơn, ví dụ kiểm soát descriptor/thư mục và <code>SecureDirectoryStream</code> khi khả dụng.

**TOCTOU.** Metadata check và open là các thao tác khác nhau; attacker có thể thay entry giữa hai thời điểm. Tùy yêu cầu cần chiến lược kiểm tra sau khi mở hoặc cơ chế API/OS hỗ trợ atomicity tương ứng.

**Tài nguyên.** Hạn chế byte, số request đồng thời, tốc độ đọc, thời hạn, memory và không log file vô hạn. File có kích thước nhỏ không nhất thiết là nội dung an toàn để parse, render hoặc thực thi.

**Định dạng.** Đuôi <code>.txt</code> chỉ là chính sách filename. Nếu ứng dụng yêu cầu đúng một nội dung cụ thể, phải kiểm tra nội dung; không dùng đuôi file làm bằng chứng tuyệt đối.

**Lỗi trên file system.** Mã lỗi cụ thể phụ thuộc OS/provider; cùng nguyên nhân nghiệp vụ có thể được biểu diễn khác nhau bởi provider. Một đường xử lý chung <code>READ_FAILED</code> vẫn cần thiết, không chỉ catch một exception con.

**Thông báo cho người dùng.** Không công khai đường dẫn tuyệt đối, exception class, stack trace hoặc file bytes nếu không có quyền. Khi debug, dùng mã tham chiếu an toàn để đối chiếu log.

## 20. Các lỗi thường gặp

1. **Chỉ check <code>fileName != null</code>:** chuỗi blank và path traversal vẫn lọt.
2. **Nối chuỗi đường dẫn trực tiếp:** dễ sai separator và vô tình cho phép truy cập ngoài root.
3. **Tin rằng <code>normalize()</code> chặn mọi symlink:** chỉ xử lý hình thức đường dẫn, không tự bảo đảm file thực.
4. **Dùng <code>Files.exists</code> như bảo đảm open sẽ thành công:** gặp race, permission và lỗi I/O khác.
5. **Trả chuỗi rỗng cho mọi lỗi:** không phân biệt file rỗng, không tồn tại, invalid UTF-8 hoặc access denied.
6. **Bắt <code>Exception</code> rồi trả mặc định:** nuốt bug và có thể che cả các lỗi không nên coi là validation.
7. **Bắt <code>IOException</code> trước mọi subtype cần phân loại:** trong Java, catch subtype phía sau superclass sẽ không hợp lệ/không tới được.
8. **Bỏ cause khi chuyển lỗi:** không thể đối chiếu exception gốc hay chẩn đoán nguồn.
9. **Quên đóng stream nếu decoder ném lỗi:** dùng try-with-resources.
10. **Chỉ dựa vào <code>Files.size()</code>:** metadata có thể thay đổi và không tự giới hạn số byte đã đọc.
11. **Cho rằng UTF-8 luôn hợp lệ vì đuôi .txt:** phải decode theo charset được công bố.
12. **Âm thầm thay byte sai bằng ký tự thay thế:** có thể che dữ liệu hỏng nếu yêu cầu strict UTF-8.
13. **Dùng <code>String.length()</code> làm hạn mức byte:** UTF-8 và UTF-16 có cách đếm khác nhau.
14. **Đọc toàn bộ file không giới hạn vào bộ nhớ:** có nguy cơ làm cạn tài nguyên.
15. **Trim nội dung không theo hợp đồng:** có thể làm mất khoảng trắng/newline quan trọng.
16. **Không phân biệt lỗi input với lỗi hạ tầng:** client có thể retry sai hoặc quy lỗi cho người dùng.
17. **Gửi <code>IOException.getMessage()</code> cho client:** có thể rò đường dẫn, tên máy chủ và chi tiết OS.
18. **Dùng file thật/quyền truy cập thật để unit test mapper:** dễ flaky giữa môi trường.
19. **Không kiểm thử file rỗng:** dễ biến kết quả thành lỗi “not found”.
20. **Không kiểm thử race/symlink trong mô hình threat phù hợp:** validation tên file không thay thế thiết kế bảo mật.
21. **Chỉ test mã lỗi mà không test cause identity:** wrapper có thể sao chép message nhưng mất exception gốc.
22. **Cho rằng mọi FileSystemProvider hỗ trợ option y hệt nhau:** cần kiểm thử trên runtime/file system mục tiêu.
23. **Dùng đường dẫn tương đối theo working directory không kiểm soát:** chương trình có thể đọc nhầm file theo nơi chạy.
24. **Xóa tệp thử nghiệm bên ngoài vùng được tạo:** phải giới hạn cleanup vào temp directory mà chương trình sở hữu.

## 21. Checklist review quy trình đọc file

- Input đã được mô tả là **file ID, filename đơn hay relative path** chưa?
- Có kiểm tra null/blank/ký tự/loại file theo hợp đồng không?
- Nếu cho phép path tương đối nhiều thành phần, có biện pháp kiểm soát root, traversal và symlink phù hợp không?
- Đã phân biệt validation với authorization chưa?
- Có tránh coi metadata precheck là đảm bảo open thành công không?
- Khi mở file, đã bắt các subtype <code>IOException</code> quan trọng trước catch tổng quát chưa?
- Có đóng stream trong mọi nhánh thành công, decode fail hoặc quá lớn không?
- Có giới hạn **byte thực sự đọc** thay vì chỉ đọc kích thước trước không?
- Charset có rõ ràng và hành vi malformed input có được công bố không?
- Có phân biệt file rỗng với file không tồn tại không?
- Lỗi đã có code thích hợp và bảo toàn cause của I/O/decoder chưa?
- Public response có bỏ thông tin nhạy cảm nhưng vẫn có hướng xử lý không?
- Test đã bao phủ tên sai, file rỗng, file missing, directory, oversized, malformed UTF-8 và success chưa?
- Test cause đã dùng fake để bảo đảm cùng đối tượng exception mà không phụ thuộc OS chưa?
- Nếu file system có bên không tin cậy sửa đồng thời, giải pháp có xử lý TOCTOU và symlink theo threat model thực tế không?

## 22. Bài tập mở rộng

### Bài tập A — giới hạn số ký tự sau khi decode

Bổ sung quy tắc “nội dung không vượt 500 ký tự” và xác định “ký tự” ở đây là UTF-16 code unit hay Unicode code point. Kiểm thử với emoji, tiếng Việt và chuỗi chứa ký tự kết hợp. Không thay thế giới hạn byte bằng giới hạn ký tự nếu mục tiêu vẫn là kiểm soát dung lượng đọc.

### Bài tập B — file nhiều dòng

Dùng <code>BufferedReader</code> và xử lý tối đa một số dòng cụ thể. Xác định hành vi với dòng rất dài, newline Windows/Unix, dòng rỗng và file rỗng. Đảm bảo đóng reader nếu parser ném exception.

### Bài tập C — cấu trúc kết quả không dùng checked exception

Thiết kế <code>sealed interface ReadResult</code> với <code>Success(content)</code> và <code>Failure(code, message)</code>, rồi so sánh với kiểu checked exception trong demo. Hãy xác định nơi nào cần giữ Throwable nội bộ, nơi nào chỉ cần mã lỗi để hiển thị.

### Bài tập D — kiểm tra file ID thay vì filename

Cho phép người dùng nhập <code>documentId</code> và ánh xạ nó sang một file do server quản lý. Không để caller quyết định path OS. Viết test chứng minh ID không hợp lệ không mở file và ID hợp lệ chỉ đọc file được cấp quyền.

### Bài tập E — race giữa kiểm tra và open

Vẽ một tình huống bên khác xóa file sau <code>Files.isRegularFile()</code> nhưng trước <code>Files.newInputStream()</code>. Giải thích tại sao <code>NoSuchFileException</code> vẫn là tình huống hợp lệ phải bắt. Đề xuất cách kiểm soát nếu hệ thống yêu cầu trạng thái file nhất quán hơn.

## 23. Tóm tắt

Một quy trình đọc file đáng tin cần kiểm tra **đúng điều kiện ở đúng tầng**:

- **Validation** kiểm tra tên và loại đầu vào mà không phụ thuộc I/O.
- **Path/resource policy** quyết định dữ liệu nào được phép truy cập; allowlist giúp đơn giản hóa nhưng không thay thế bảo mật file system.
- **Open/read** là thao tác có thể thất bại, phải xử lý exception thực tế thay vì dựa vào precheck.
- **Giới hạn byte** bảo vệ tài nguyên trong phạm vi đã định.
- **Giải mã UTF-8 rõ ràng** giúp không âm thầm chấp nhận byte sai.
- **Try-with-resources** bảo đảm đóng stream và giữ lỗi phụ phù hợp.
- **Exception translation** tạo mã lỗi có ý nghĩa và giữ cause gốc để chẩn đoán.
- **Testing** phải bao phủ cả dữ liệu sai, file thực rỗng, file hỏng, file quá lớn và các lỗi hệ thống được giả lập.
- **Security boundary** cần xét path traversal, symlink, TOCTOU, quyền truy cập và giới hạn dữ liệu được công bố.

**Điều quan trọng nhất:** kiểm tra input làm giảm lỗi có thể dự đoán, nhưng không thể “chứng minh” thao tác đọc file sẽ thành công. File system là môi trường có thể thay đổi; chương trình phải vừa **kiểm tra những gì mình kiểm soát**, vừa **xử lý đúng những lỗi xảy ra khi thực hiện I/O**, và giữ ranh giới rõ ràng giữa thông tin chẩn đoán nội bộ với thông báo dành cho người dùng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-practice-002">So sánh xử lý tại chỗ với lan truyền bằng throws</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh xử lý ngoại lệ tại chỗ với lan truyền bằng `throws` trong Java

Trong Java, khi một phương thức gặp lỗi, người viết chương trình thường có hai hướng chính: **bắt và xử lý ngoại lệ ngay tại chỗ** bằng `try`–`catch`, hoặc **để ngoại lệ lan truyền tới tầng gọi** và, với checked exception, khai báo khả năng đó bằng `throws`. Hai cách đều hợp lệ trong ngữ cảnh thích hợp nhưng mang trách nhiệm khác nhau. Chọn sai có thể làm mất thông tin chẩn đoán, che một thất bại thành “thành công”, ép tầng thấp biết về chính sách nghiệp vụ của tầng cao, hoặc khiến tài nguyên không được dọn đúng.

Câu hỏi quan trọng nhất không phải “có viết được `catch` ở đây không?” mà là:

> **Tầng hiện tại có đủ thông tin và quyền quyết định để xử lý lỗi một cách đúng đắn không?**

Nếu có một chiến lược phục hồi được hợp đồng cho phép và trạng thái vẫn hợp lệ, xử lý tại chỗ có thể phù hợp. Nếu chưa, nên **chuyển trách nhiệm** bằng cách để ngoại lệ đi lên hoặc chuyển đổi nó thành một ngoại lệ thích hợp hơn nhưng **giữ `cause`**. `throws` không phải cơ chế “sửa lỗi”; nó là một phần hợp đồng phương thức đối với các ngoại lệ checked có thể thoát ra.

## 1. Hai hướng giải quyết và sơ đồ luồng điều khiển

### Xử lý tại chỗ

Một phương thức bắt ngoại lệ và tự thực hiện chính sách xử lý:

```java
String displayName(String raw) {
    try {
        return normalizeName(raw);
    } catch (InvalidNameException exception) {
        return "Unknown"; // Chỉ đúng nếu fallback này được hợp đồng cho phép.
    }
}
```

Đây là **mã minh họa chưa độc lập**, cần định nghĩa `normalizeName(...)` và `InvalidNameException`. Khi `catch` kết thúc bình thường và trả fallback, phương thức không còn chuyển ngoại lệ vừa bắt tới người gọi. Điều này hữu ích khi `Unknown` thật sự là một kết quả hợp lệ, nhưng nguy hiểm nếu ứng dụng đang xử lý thanh toán, xác thực hoặc cập nhật dữ liệu bắt buộc phải thành công.

### Lan truyền cho tầng gọi

Phương thức để ngoại lệ checked đi ra và công bố trong chữ ký:

```java
String loadConfiguration() throws java.io.IOException {
    return java.nio.file.Files.readString(
            java.nio.file.Path.of("config.properties"));
}
```

Đây là **đoạn phương thức minh họa**, cần đặt trong một lớp Java. Nó không quyết định có dùng cấu hình mặc định, thử nguồn khác, thông báo người dùng hay hủy khởi động. Lựa chọn đó thuộc tầng gọi có đủ ngữ cảnh.

```text
Tầng thấp: load()
       |
       +-- đọc thành công -----------------> trả dữ liệu
       |
       +-- IOException
                |
                +-- A: catch tại đây
                |        |
                |        +-- phục hồi hợp lệ --> trả kết quả bình thường
                |        +-- không phục hồi --> ném lại / chuyển lỗi
                |
                +-- B: throws IOException
                         |
                         v
                   Tầng gọi quyết định
                         |
                  +------+------+
                  |             |
                 catch        throws tiếp
                  |             |
              xử lý tại      tầng cao hơn
              ranh giới
```

Một lời gọi có thể có nhiều tầng `throws` liên tiếp. Ngoại lệ chỉ dừng lan truyền khi có handler thực sự xử lý nó, hoặc khi nó đến ranh giới thực thi thích hợp mà không có handler. Đừng giả định `throws` tạo một `catch` ngầm.

## 2. Bảng so sánh nhanh

| Tiêu chí | `try`–`catch` xử lý tại chỗ | Lan truyền bằng `throws` |
| --- | --- | --- |
| Trách nhiệm chính | Xác định và thực hiện chính sách xử lý tại tầng hiện tại | Chuyển trách nhiệm xử lý cho người gọi |
| Ngoại lệ có ra khỏi phương thức? | Không, **nếu** được xử lý xong và không ném lại | Có thể, nếu đường thực thi phát sinh ngoại lệ đó |
| Ảnh hưởng chữ ký | Không nhất thiết phải khai báo ngoại lệ đã được xử lý hoàn toàn | Checked exception thoát ra thường phải có trong `throws` hoặc kiểu cha phù hợp |
| Cần có fallback/retry? | Chỉ khi đó là chính sách thực sự hợp lệ | Không cần tự quyết định fallback ở tầng thấp |
| Bảo toàn stack/cause | Phải cẩn thận khi log, bọc hoặc ném lại | Ngoại lệ gốc tiếp tục lan lên nếu không thay bằng wrapper |
| Tái sử dụng tầng thấp | Có thể kém nếu tầng thấp tự áp chính sách của UI/API | Thường tốt hơn khi tầng cao quyết định theo bối cảnh |
| Phù hợp với | Lỗi có thể phục hồi ngay, validation tại boundary, cleanup cục bộ | I/O, lỗi tầng hạ tầng hoặc trường hợp tầng hiện tại không đủ quyền xử lý |
| Rủi ro nổi bật | Nuốt lỗi, trả thành công giả, trạng thái thay đổi dở dang | Lộ ngoại lệ tầng thấp ra API không phù hợp hoặc bỏ quên nơi xử lý cuối |

Hai cách **không loại trừ nhau**. Một phương thức có thể bắt ngoại lệ tầng thấp rồi ném **ngoại lệ mới phù hợp với tầng trừu tượng**; hoặc bắt một loại lỗi có thể phục hồi nhưng để loại khác lan lên. Thiết kế tốt thường kết hợp chúng qua các ranh giới rõ ràng.

## 3. `throw` khác `throws` thế nào?

Dễ nhầm lẫn vì tên gần giống nhau:

```java
void read() throws java.io.IOException {
    throw new java.io.IOException("read failed");
}
```

Đây là **phương thức Java minh họa** đặt trong lớp. Trong đoạn này:

- `throw expression;` là **câu lệnh thực thi**, làm luồng điều khiển rời nhánh thông thường bằng một đối tượng `Throwable`.
- `throws IOException` là **mệnh đề trong chữ ký** khai báo checked exception có thể thoát ra khỏi phương thức.
- Viết `throws` không tự tạo hoặc ném lỗi. Phương thức khai báo `throws IOException` vẫn có thể trả kết quả bình thường.
- Viết `throw new IOException(...)` bên trong phương thức thường đòi hỏi bắt lại lỗi, hoặc khai báo `throws IOException` (hoặc kiểu cha được phép) vì `IOException` là checked exception.
- Đối với unchecked exceptions như `IllegalArgumentException`, `IllegalStateException` hay `NullPointerException`, Java **không yêu cầu** khai báo `throws`, mặc dù có thể khai báo để cung cấp thông tin.

`throws` cũng không phải câu lệnh yêu cầu JVM “bỏ qua lỗi”. Ngoại lệ vẫn có thể lan ngược qua các lời gọi, chạy `finally` theo quy tắc Java, rồi tới handler gần nhất tương ứng.

## 4. Checked và unchecked exception: tác động tới cách chọn

### Checked exception

Các lớp kế thừa `Exception` nhưng không kế thừa `RuntimeException` thường là **checked exception**, ví dụ `IOException`, `SQLException`, `InterruptedException`. Trình biên dịch kiểm tra rằng chúng được **bắt** hoặc **khai báo/lan truyền** phù hợp.

```java
void load() throws java.io.IOException {
    readFromDisk();
}
```

Đây là mã minh họa, `readFromDisk()` phải là phương thức được khai báo trong ứng dụng. Nếu không có `try`–`catch` xử lý `IOException`, chữ ký `throws` báo cho người gọi biết rủi ro đó.

### Unchecked exception

`RuntimeException` và các lớp con là ngoại lệ unchecked. Những lỗi này có thể lan lên mà **không cần `throws`** trong chữ ký:

```java
int parsePositive(String input) {
    int value = Integer.parseInt(input);
    if (value <= 0) {
        throw new IllegalArgumentException("value must be positive");
    }
    return value;
}
```

Đây là phương thức minh họa đặt trong lớp. Cả `NumberFormatException` (từ `parseInt`) và `IllegalArgumentException` đều unchecked. Tác giả không bị trình biên dịch buộc thêm `throws`, nhưng người gọi vẫn có thể bắt chúng khi có chính sách xử lý phù hợp.

**Không đồng nhất checked với “phục hồi được” và unchecked với “không được bắt”.** Việc chọn nơi xử lý phụ thuộc **hợp đồng, khả năng khôi phục trạng thái và chính sách nghiệp vụ**, chứ không chỉ loại ngôn ngữ. Chẳng hạn, `NumberFormatException` có thể là lỗi nhập liệu dự kiến tại ranh giới UI; còn `IOException` có thể là sự cố không thể phục hồi tại một tiến trình và phải kết thúc yêu cầu.

## 5. Ví dụ Java 17+: cùng lỗi, ba cách phân chia trách nhiệm

Tạo tệp **`LocalVsThrowsDemo.java`**:

```java
import java.io.IOException;

public class LocalVsThrowsDemo {
    static String fetch(boolean fail) throws IOException {
        if (fail) {
            throw new IOException("storage unavailable");
        }
        return "REAL_DATA";
    }

    static String handleLocally(boolean fail) {
        try {
            return fetch(fail);
        } catch (IOException exception) {
            return "CACHED_DATA"; // Giả định fallback hợp lệ cho màn hình.
        }
    }

    static String propagate(boolean fail) throws IOException {
        return fetch(fail);
    }

    static String translate(boolean fail) {
        try {
            return fetch(fail);
        } catch (IOException original) {
            throw new IllegalStateException("cannot assemble report", original);
        }
    }

    static String handleAtBoundary(boolean fail) {
        try {
            return propagate(fail);
        } catch (IOException exception) {
            return "REQUEST_FAILED:" + exception.getMessage();
        }
    }

    public static void main(String[] args) {
        System.out.println("localSuccess=" + handleLocally(false));
        System.out.println("localFallback=" + handleLocally(true));
        System.out.println("propagateSuccess=" + handleAtBoundary(false));
        System.out.println("boundaryFailure=" + handleAtBoundary(true));

        try {
            translate(true);
        } catch (IllegalStateException exception) {
            System.out.println("wrapper="
                    + exception.getClass().getSimpleName());
            System.out.println("cause="
                    + exception.getCause().getClass().getSimpleName());
            System.out.println("causeMessage="
                    + exception.getCause().getMessage());
        }
    }
}
```

Chạy trên Java 17 trở lên:

```bash
javac --release 17 LocalVsThrowsDemo.java
java LocalVsThrowsDemo
```

Đầu ra:

```text
localSuccess=REAL_DATA
localFallback=CACHED_DATA
propagateSuccess=REAL_DATA
boundaryFailure=REQUEST_FAILED:storage unavailable
wrapper=IllegalStateException
cause=IOException
causeMessage=storage unavailable
```

Phân tích ba chính sách:

1. `handleLocally` bắt `IOException` và trả `CACHED_DATA`. Kết quả là một **hoàn tất bình thường**. Chỉ đúng khi dữ liệu cache được chấp nhận và ứng dụng phân biệt nguồn dữ liệu nếu cần.
2. `propagate` khai báo `throws IOException`, không tự quyết định phản hồi. `handleAtBoundary` là nơi biết cách chuyển lỗi thành trạng thái thất bại của request. Chuỗi `"REQUEST_FAILED:..."` chỉ nhằm dễ quan sát trong demo; hệ thống thật nên dùng kiểu kết quả hoặc mã lỗi an toàn, không dựa vào việc phân tích chuỗi.
3. `translate` bắt `IOException` rồi ném `IllegalStateException` có **cause**. Nó đổi tầng ngữ nghĩa mà không loại bỏ lỗi kỹ thuật gốc. Đây không phải “xử lý xong” theo nghĩa phục hồi: caller vẫn thấy thất bại.

Ví dụ có `boolean fail` để tạo lỗi **xác định**, không phụ thuộc file hay mạng thật. Một test liên quan tới hệ thống lưu trữ thực tế sẽ cần dữ liệu môi trường và cách cô lập riêng.

## 6. Thế nào là xử lý tại chỗ đúng nghĩa?

Một nhánh `catch` không thể coi là phục hồi chỉ vì nó có log:

```java
try {
    savePayment();
} catch (Exception exception) {
    System.out.println(exception.getMessage());
}
// Bên dưới vẫn trả thành công: nguy hiểm.
```

Đây là mã minh họa, không phải chương trình chạy độc lập. Nếu `savePayment()` thất bại mà code tiếp tục gửi thông báo “đã thanh toán”, hệ thống che lỗi thật.

**Xử lý tại chỗ hợp lệ** phải có một hợp đồng rõ. Ví dụ:

- Validation input: từ chối giá trị sai định dạng, giữ trạng thái chưa sửa và trả lỗi cụ thể.
- Dữ liệu tùy chọn: trả giá trị mặc định được cho phép, đồng thời bảo đảm caller hiểu đó là fallback khi cần.
- Retry có kiểm soát: chỉ thử lại lỗi tạm thời, có giới hạn và kiểm tra tác dụng phụ/idempotency.
- Một bản ghi lỗi độc lập trong batch: đánh dấu bản ghi đó thất bại rồi xử lý bản ghi kế, nếu trạng thái các bản ghi được cách ly.

**Không phù hợp để nuốt tại chỗ** nếu phát hiện bất biến nội bộ bị phá vỡ, dữ liệu vừa bị cập nhật dở hoặc một thao tác ngoài hệ thống có thể đã xảy ra nhưng chưa xác định kết quả.

Một bảng quyết định hữu ích:

| Câu hỏi | Nếu “có” | Nếu “không” |
| --- | --- | --- |
| Tầng này hiểu chính xác loại lỗi và tác động? | Có thể phân loại để xử lý | Truyền lên tầng có đủ ngữ cảnh |
| Có trạng thái hợp lệ sau khi phục hồi? | Có thể trả fallback/retry theo hợp đồng | Dừng thao tác và truyền thất bại |
| Có quyền quyết định chính sách nghiệp vụ? | Xử lý tại chỗ có thể hợp lý | Không tự biến lỗi thành kết quả |
| Có bảo đảm không phát sinh tác dụng phụ trùng lặp? | Có thể cân nhắc retry | Không retry tự động |
| Tầng gọi có cần biết việc fallback đã xảy ra? | Trả metadata/kiểu kết quả phù hợp | Có thể trả giá trị thông thường nếu đúng hợp đồng |

Nếu phục hồi tại chỗ, ghi log/metric ở mức phù hợp cho tình huống dự kiến. Đừng gắn một `catch` rộng quanh toàn bộ phương thức chỉ để chương trình “không bao giờ ném exception”.

## 7. Lan truyền bằng `throws` giúp tách chính sách khỏi cơ chế

Một module tầng thấp nên tập trung vào việc **thực thi**: đọc file, gọi database, gửi request hoặc biến đổi dữ liệu. Tầng cao có thể quyết định:

- Khi khởi động ứng dụng: lỗi đọc cấu hình bắt buộc có thể khiến việc khởi động **thất bại**.
- Khi hiển thị tính năng không thiết yếu: lỗi đọc một nguồn tùy chọn có thể dùng cache.
- Khi chạy batch: lỗi của một input độc lập có thể được ghi vào báo cáo lỗi của bản ghi đó.
- Khi xử lý yêu cầu API: lỗi I/O có thể thành phản hồi lỗi ổn định, trong khi stack trace được ghi trong log nội bộ.

Nếu repository luôn bắt `IOException` rồi trả chuỗi rỗng, nó đã **áp chính sách fallback cho mọi tầng** dù service A cần dừng còn service B có thể phục hồi. Để ngoại lệ lan truyền qua `throws` giúp tái sử dụng tầng thấp và đặt chính sách ở nơi sở hữu quyết định.

Tuy nhiên, `throws IOException` từ tầng repository đi xuyên qua mọi API công khai có thể làm lộ **chi tiết công nghệ** ra tầng nghiệp vụ. Trong trường hợp đó, có thể **dịch ngoại lệ** ở ranh giới phù hợp để cân bằng tái sử dụng với ngữ nghĩa API.

## 8. Dịch ngoại lệ: bắt nhưng không phục hồi

Có ba kiểu hành động trong một `catch`:

1. **Recover:** xử lý lỗi thành công và trả kết quả hợp lệ.
2. **Rethrow:** `throw error;` để giữ chính đối tượng exception và chuỗi nguyên nhân đã có.
3. **Translate/wrap:** `throw new DomainException("context", error);` để cung cấp loại lỗi ở tầng cao hơn và giữ cause.

Ví dụ đoạn minh họa trong ứng dụng:

```java
try {
    repository.load(customerId);
} catch (java.io.IOException original) {
    throw new CustomerLoadException(
            "Cannot load customer " + customerId, original);
}
```

Đoạn này yêu cầu `repository`, `customerId` và lớp `CustomerLoadException` có constructor nhận `(String, Throwable)`. Nó là **dịch ngoại lệ**, không phải phục hồi. Caller vẫn phải quyết định báo thất bại hay xử lý loại lỗi mới.

**Tránh cách sai:**

```java
catch (java.io.IOException original) {
    throw new IllegalStateException(original.getMessage());
}
```

Đây chỉ sao chép message; nó không đặt `getCause()` là `original`, nên mất đường truy vết `IOException`, stack gốc, cause sâu hơn và suppressed exceptions.

Khi dịch ngoại lệ, message nên cung cấp **ngữ cảnh thao tác**, không lặp hoặc lộ toàn bộ dữ liệu kỹ thuật. Có thể ghi chính `Throwable` ở ranh giới log thích hợp thay vì chỉ `getMessage()`.

## 9. Kiểm soát checked exception qua nhiều tầng

Giả sử các phương thức đều có trách nhiệm rõ ràng:

```java
void lowLevel() throws java.io.IOException {
    throw new java.io.IOException("read failed");
}

void service() throws java.io.IOException {
    lowLevel();
}

void requestBoundary() {
    try {
        service();
    } catch (java.io.IOException exception) {
        reportFailure(exception);
    }
}
```

Đây là **cụm phương thức minh họa bên trong một lớp**, phụ thuộc `reportFailure(...)`. Khi `lowLevel()` ném `IOException`, `service()` không xử lý nên để lỗi đi lên qua chữ ký `throws`. `requestBoundary()` bắt và báo thất bại theo chính sách của nó. Khai báo `throws` ở `service()` không có nghĩa `service()` đã làm bất cứ điều gì để sửa lỗi.

Điều quan trọng với **checked exception**: nếu một phương thức gọi code có thể ném checked exception và không bắt được hết các trường hợp thoát ra, chữ ký phải cho phép ngoại lệ tương ứng. Trong override/interface, phương thức override **không được mở rộng tập checked exceptions** vượt hợp đồng phương thức cha. Vì vậy API có thể định hướng chỗ nào nên dịch ngoại lệ hoặc dùng một loại ngoại lệ miền nghiệp vụ đã được định nghĩa.

Đối với **unchecked exception**, chữ ký không bắt buộc khai báo. Một phương thức có thể ghi `throws IllegalStateException` để giúp đọc tài liệu, nhưng điều đó không tạo bảo đảm rằng chỉ duy nhất loại này có thể được ném.

## 10. Ví dụ Java 17+: tầng thấp truyền lỗi, tầng ngoài xử lý và giữ tài nguyên

Trong nhiều chương trình, vấn đề không chỉ nằm ở chỗ **bắt hay ném** mà còn ở việc **dọn tài nguyên** khi ngoại lệ lan truyền. `try-with-resources` cho phép dọn resource ngay tại nơi sở hữu dù trách nhiệm xử lý lỗi ở tầng cao hơn.

Tạo tệp **`PropagationAndCleanupDemo.java`**:

```java
import java.io.IOException;

public class PropagationAndCleanupDemo {
    static final class DemoResource implements AutoCloseable {
        private final boolean failClose;
        int closeCount;

        DemoResource(boolean failClose) {
            this.failClose = failClose;
        }

        String read(boolean failRead) throws IOException {
            if (failRead) {
                throw new IOException("read failed");
            }
            return "OK";
        }

        @Override
        public void close() throws IOException {
            closeCount++;
            if (failClose) {
                throw new IOException("close failed");
            }
        }
    }

    static String readAndPropagate(
            DemoResource resource, boolean failRead) throws IOException {
        try (resource) {
            return resource.read(failRead);
        }
    }

    static void observe(String label, boolean failRead, boolean failClose) {
        DemoResource resource = new DemoResource(failClose);
        try {
            String result = readAndPropagate(resource, failRead);
            System.out.println(label + " result=" + result);
        } catch (IOException error) {
            System.out.println(label + " primary=" + error.getMessage());
            System.out.println(label + " suppressed="
                    + error.getSuppressed().length);
            if (error.getSuppressed().length > 0) {
                System.out.println(label + " cleanup="
                        + error.getSuppressed()[0].getMessage());
            }
        }
        System.out.println(label + " closes=" + resource.closeCount);
    }

    public static void main(String[] args) {
        observe("success", false, false);
        observe("readFailure", true, false);
        observe("closeFailure", false, true);
        observe("bothFailure", true, true);
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 PropagationAndCleanupDemo.java
java PropagationAndCleanupDemo
```

Đầu ra:

```text
success result=OK
success closes=1
readFailure primary=read failed
readFailure suppressed=0
readFailure closes=1
closeFailure primary=close failed
closeFailure suppressed=0
closeFailure closes=1
bothFailure primary=read failed
bothFailure suppressed=1
bothFailure cleanup=close failed
bothFailure closes=1
```

Giải thích:

- `readAndPropagate(...)` giữ trách nhiệm **đóng tài nguyên** tại nơi mở phạm vi sử dụng bằng `try (resource)` và để `IOException` lan truyền bằng `throws`. Nó không cần tự quyết định cách hiển thị hoặc xử lý lỗi ở tầng ngoài.
- `observe(...)` đóng vai trò tầng gọi quyết định cách báo lỗi; trong demo nó in ra console. Ứng dụng thật nên biến trạng thái thành kết quả rõ ràng và ghi log có ngữ cảnh.
- Khi thân `try` lỗi nhưng `close()` thành công, lỗi đọc lan ra bình thường.
- Khi chỉ `close()` lỗi, ngoại lệ đóng tài nguyên đi ra như lỗi chính. Đừng đánh giá thao tác “đã thành công” nếu việc đóng resource quan trọng với tính đúng đắn dữ liệu.
- Khi **cả `read()` và `close()` cùng lỗi**, lỗi đọc là ngoại lệ chính và lỗi đóng được giữ trong `getSuppressed()`. Nhờ đó xử lý tại tầng ngoài **không làm mất nguyên nhân ban đầu**.
- `closeCount=1` ở mọi trường hợp cho thấy `try-with-resources` dọn tài nguyên trên cả đường thành công và thất bại.

Mã dùng `try (resource)` với biến resource có giá trị effectively final, được Java 9+ hỗ trợ. Nó là kỹ thuật dọn tài nguyên, **không phải** cơ chế rollback mọi thay đổi nghiệp vụ đã thực hiện trước ngoại lệ.

## 11. Phân biệt `finally`/`try-with-resources` với xử lý lỗi

Khi để exception lan truyền, Java vẫn thực hiện các `finally` phù hợp trên đường thoát. Tuy nhiên, nếu chính `finally` ném một ngoại lệ khác hoặc `return`, nó có thể **thay thế** ngoại lệ đang truyền:

```java
try {
    throw new IllegalArgumentException("original");
} finally {
    throw new IllegalStateException("cleanup failed");
}
```

Đây là đoạn minh họa; caller thường nhận `IllegalStateException`, không phải lỗi gốc. `try/finally` thủ công không tự thêm `original` vào suppressed exceptions. Vì thế, ngay cả khi lựa chọn `throws` đúng, cách viết cleanup sai có thể che mất nguyên nhân.

Khuyến nghị:

- Ưu tiên `try-with-resources` cho `AutoCloseable`, đặc biệt khi `close()` có thể ném.
- Không `return` trong `finally` để tránh nuốt ngoại lệ.
- Nếu dọn dẹp thủ công có thể thất bại, thiết kế cách giữ lỗi chính và báo lỗi cleanup; không tùy tiện bỏ lỗi cleanup nghiêm trọng.
- Không đánh dấu nghiệp vụ thành công trong `finally`, vì `finally` còn chạy khi thao tác thất bại.
- Test cả tình huống lỗi chính và lỗi dọn dẹp cùng xảy ra.

**Lan truyền lỗi** và **giải phóng resource** là hai trách nhiệm khác nhau: một phương thức có thể dọn tài nguyên tại chỗ nhưng tiếp tục chuyển ngoại lệ cho tầng gọi xử lý.

## 12. `InterruptedException`: khi nào nên lan truyền?

`InterruptedException` là checked exception thường báo rằng luồng đang chờ tại điểm hỗ trợ ngắt nhận yêu cầu hủy. Nếu phương thức có thể khai báo `throws InterruptedException`, truyền ngoại lệ lên tầng có trách nhiệm dừng tác vụ thường là cách phù hợp:

```java
String takeNext(java.util.concurrent.BlockingQueue<String> queue)
        throws InterruptedException {
    return queue.take();
}
```

Đây là đoạn phương thức minh họa. Nó không biết có nên hủy vòng xử lý, dừng worker hay thông báo người gọi, nên chuyển trách nhiệm cho tầng cao.

Nếu đang triển khai `Runnable.run()` (không khai báo checked exception) và một lời gọi chờ ném `InterruptedException`, một hướng thường hợp lý là khôi phục cờ ngắt và thoát công việc:

```java
@Override
public void run() {
    try {
        processBlockingWork();
    } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        return;
    }
}
```

Đây là **mảnh code trong lớp triển khai `Runnable`** có phương thức `processBlockingWork()` ném `InterruptedException`. Nó không độc lập. Việc `Thread.currentThread().interrupt()` quan trọng vì nhiều API chờ xóa cờ interrupted status khi ném `InterruptedException`; khôi phục giúp mã quản lý luồng biết tác vụ được yêu cầu ngắt.

Không bắt `InterruptedException` rồi lặp lại vô hạn như chưa có chuyện gì, trừ khi có một hợp đồng đặc biệt quy định rõ việc tiêu thụ yêu cầu ngắt. Khi xử lý ngắt, việc tiếp tục cũng phải bảo đảm tài nguyên/trạng thái nhất quán.

## 13. Bắt ngoại lệ hẹp, không lấy `catch (Exception)` làm công thức chung

Một số tình huống nên bắt nhiều loại ngoại lệ, nhưng hãy chọn theo **chính sách xử lý** thay vì vì tiện viết.

Ví dụ:

```java
try {
    loadOptionalMetadata();
} catch (java.io.FileNotFoundException missing) {
    useOptionalDefault();
}
```

Đoạn này cần `loadOptionalMetadata()` và `useOptionalDefault()`. Nó chỉ phục hồi khi đúng ngoại lệ “không tìm thấy file tùy chọn”. Những `IOException` khác như mất quyền đọc hoặc lỗi thiết bị vẫn có thể lan lên nếu phương thức khai báo `throws` phù hợp.

Ngược lại:

```java
try {
    loadOptionalMetadata();
} catch (Exception ignored) {
    useOptionalDefault();
}
```

Đoạn này bắt quá rộng: `NullPointerException` do bug, lỗi chuyển đổi bất ngờ hoặc lỗi khác đều có thể bị che thành “không có metadata”. Nó làm tín hiệu chẩn đoán khó nhìn thấy.

Với multi-catch, các loại ngoại lệ trong cùng `catch` nên thực sự chia sẻ một chính sách xử lý:

```java
catch (java.net.SocketTimeoutException
        | java.net.ConnectException transientError) {
    recordTransientFailure(transientError);
}
```

Đây là mảnh minh họa trong phương thức có thể gặp các lỗi mạng ấy và đã khai báo `recordTransientFailure`. Đừng mặc định rằng mọi lỗi mạng đều được retry: request đã tạo side effect hay chưa và tính idempotent vẫn là điều phải xem xét.

## 14. Ranh giới API, lỗi nghiệp vụ và trải nghiệm người gọi

Tầng gọi có thể là controller HTTP, command-line interface, scheduled job hoặc một batch processor. Cùng một ngoại lệ hạ tầng có thể dẫn tới những quyết định khác nhau:

| Ranh giới | Chính sách có thể hợp lý | Cần tránh |
| --- | --- | --- |
| HTTP API | Chuyển lỗi sang mã/phản hồi ổn định, log `Throwable` | Trả chuỗi chi tiết kỹ thuật, token hoặc stack cho client |
| CLI | In thông báo có ích, trả exit code thất bại | Trả exit code `0` sau khi thao tác lỗi |
| Batch | Ghi kết quả lỗi cho item độc lập và tiếp tục item khác | Trộn trạng thái item lỗi vào kết quả thành công |
| Startup | Dừng khởi động nếu cấu hình bắt buộc thiếu | Dùng cấu hình mặc định không được hợp đồng cho phép |
| UI dữ liệu tùy chọn | Hiển thị cache/fallback khi cho phép | Coi fallback là dữ liệu đã cập nhật thành công |
| Giao dịch tài chính | Thất bại rõ ràng, rollback/đối soát | Nuốt lỗi và ghi “đã thanh toán” |

Ranh giới cuối có thể **bắt để kết thúc yêu cầu thất bại**, không nhất thiết **phục hồi thành thành công**. Hãy phân biệt hai mục tiêu:

- *Catch to recover*: sửa được tình trạng và có kết quả hợp lệ.
- *Catch to report*: chặn ngoại lệ ở nơi giao tiếp, giữ trạng thái thất bại và chuyển thành báo cáo được phép.

Cả hai đều là xử lý tại chỗ ở một ranh giới, nhưng kết quả nghiệp vụ khác nhau. Một ngoại lệ có thể được lan truyền qua `throws` ở nhiều tầng rồi cuối cùng được bắt để báo lỗi mà không có fallback nào.

## 15. Dữ liệu đã thay đổi trước khi lỗi: `throws` không tự rollback

Ví dụ:

```java
void reserve(Order order) throws java.io.IOException {
    order.markReserved();
    remoteService.confirmReservation(order);
}
```

Đây là đoạn minh họa cần `Order` và `remoteService`; nó **không độc lập**. Nếu `markReserved()` đã thay trạng thái rồi `confirmReservation()` ném `IOException`, việc lan truyền ngoại lệ không tự làm `order` trở về trạng thái trước đó.

Ngược lại, `catch` rồi trả fallback ở đây cũng không tự sửa lỗi; nó có thể che việc remote service đã nhận yêu cầu nhưng client không biết kết quả.

Phải bổ sung chính sách nhất quán:

1. Validate toàn bộ điều kiện có thể kiểm tra trước thay đổi.
2. Xác định transaction boundary cho cập nhật cùng kho dữ liệu.
3. Nếu việc remote có tác dụng phụ, cân nhắc idempotency key, outbox hoặc đối soát thay vì retry mù.
4. Khi không chắc kết quả thật, dùng trạng thái như `PENDING_RECONCILIATION` thay vì `SUCCESS`.
5. Test trạng thái sau lỗi, không chỉ việc ngoại lệ có được ném.

Vì vậy, `throws` **bảo toàn tín hiệu lỗi**, nhưng không bảo toàn trạng thái dữ liệu thay cho một transaction hoặc giao thức nghiệp vụ đúng đắn.

## 16. Lan truyền ngoại lệ trong lambda và API bất đồng bộ

Một điểm cần chú ý trong Java: các functional interfaces thông dụng như `Supplier<T>` và `Runnable` **không khai báo checked exception**. Bởi vậy, không thể tự do truyền `IOException` từ lambda dùng làm `CompletableFuture.supplyAsync(...)` mà không xử lý hoặc bọc.

Ví dụ minh họa:

```java
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    try {
        return readFromStorage();
    } catch (java.io.IOException original) {
        throw new java.util.concurrent.CompletionException(
                "Read failed asynchronously", original);
    }
});
```

Đoạn này cần khai báo/import `CompletableFuture`, định nghĩa `readFromStorage()` trả `String` và ném `IOException`. `CompletionException` là unchecked nên lambda có thể ném nó; constructor có `cause` giữ lỗi gốc.

Phía quan sát:

```java
try {
    String value = future.join();
} catch (java.util.concurrent.CompletionException wrapper) {
    Throwable cause = wrapper.getCause();
    // Phân loại cause, báo thất bại; không giả vờ thành công.
}
```

Đây cũng là mã minh họa cần `future` đã khai báo. `join()` báo lỗi qua lớp bọc bất đồng bộ; nếu cần xác định nguyên nhân, hãy kiểm tra `getCause()` và các lớp wrapper có thể có.

Ở một `ExecutorService`, `Future.get()` có thể ném `ExecutionException` khi công việc thất bại. Checked `InterruptedException` từ **luồng đang chờ** có ý nghĩa khác với ngoại lệ do tác vụ tạo ra. Đừng biến tất cả thành cùng một `RuntimeException("failed")` chỉ để khỏi suy nghĩ về hợp đồng.

## 17. Kiểm thử lựa chọn xử lý tại chỗ và lan truyền

Bộ kiểm thử nên xác nhận **không chỉ loại exception**, mà cả hiệu quả của chính sách:

| Ca kiểm thử | Với xử lý tại chỗ | Với lan truyền |
| --- | --- | --- |
| Công việc thành công | Trả kết quả thực | Trả kết quả thực |
| Lỗi thuộc loại phục hồi được | Trả fallback hợp lệ và ghi nhận nếu cần | Caller nhận lỗi và tự quyết định |
| Lỗi bất ngờ/bug | Không âm thầm biến thành thành công | Lỗi đi lên đúng lớp nguyên nhân |
| Cleanup chạy sau lỗi | Tài nguyên được dọn, trạng thái hợp lệ | Tài nguyên được dọn, lỗi vẫn còn |
| Body và `close()` cùng lỗi | Không che primary; giữ suppressed | Caller thấy primary và suppressed |
| Lỗi được dịch thành wrapper | Caller nhìn thấy wrapper phù hợp và cause còn nguyên | Wrapper tiếp tục lan lên tầng sở hữu xử lý |
| Trạng thái đã thay đổi một phần | Có rollback/compensation được kiểm chứng | Caller biết thất bại và có chính sách xử lý trạng thái |
| Ngắt tác vụ | Bảo toàn hoặc truyền tín hiệu ngắt | `InterruptedException` được truyền đúng hợp đồng |

Ví dụ với JUnit 5 (mã minh họa, cần JUnit dependency và import `org.junit.jupiter.api.Assertions.*`):

```java
@Test
void propagatePreservesIOException() {
    IOException error = assertThrows(IOException.class,
            () -> service.propagate(true));
    assertEquals("storage unavailable", error.getMessage());
}

@Test
void recoverOnlyWithAllowedFallback() {
    assertEquals("CACHED_DATA", service.handleLocally(true));
}

@Test
void translatedErrorKeepsCause() {
    IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> service.translate(true));
    assertInstanceOf(IOException.class, error.getCause());
}
```

Đây là **mảnh test**, không phải chương trình độc lập: cần khai báo `service`, `@Test`, `IOException` và cấu hình JUnit Jupiter. Trên Java 17 thuần JDK, bạn có thể viết một hàm assertion trong `main` hoặc dùng code runnable ở ví dụ trước để quan sát hành vi.

Một test thấy fallback không đủ để chứng minh phục hồi an toàn nếu dữ liệu đã bị cập nhật. Khi có state, phải assert luôn hậu điều kiện: tài nguyên đã đóng, transaction không commit sai, không có sự kiện `SUCCESS` sau lỗi và dữ liệu người gọi nhận khớp hợp đồng.

## 18. Những sai lầm thường gặp

| Sai lầm | Vì sao sai | Cách sửa |
| --- | --- | --- |
| “Khai báo `throws` là đã xử lý lỗi.” | `throws` chỉ công bố checked exception có thể thoát ra | Đặt `catch`/policy ở tầng thực sự sở hữu quyết định |
| Bắt `Exception` rồi trả thành công | Che cả lỗi bất ngờ và trạng thái không hợp lệ | Chỉ bắt loại biết cách phục hồi, nếu không truyền lỗi |
| `catch` chỉ `printStackTrace()` rồi tiếp tục | Ghi log không đồng nghĩa phục hồi | Chuyển thất bại lên hoặc sửa trạng thái hợp lệ |
| Wrapper mới chỉ chứa `error.getMessage()` | Mất cause và stack gốc | Dùng constructor `(message, error)` |
| Bắt lỗi trong repository và trả mặc định cho mọi caller | Tầng thấp áp chính sách sai ngữ cảnh | Lan truyền hoặc dịch exception, để tầng cao quyết định |
| Đưa `IOException` ra toàn bộ API domain không cân nhắc | Rò rỉ chi tiết hạ tầng | Dịch ngoại lệ tại ranh giới domain khi có giá trị |
| Không dọn tài nguyên vì đã `throws` | Rò file/socket/DB connection | Dùng `try-with-resources` hoặc cleanup phù hợp |
| `finally` ném lỗi cleanup làm mất lỗi chính | Chẩn đoán sai nguyên nhân | Bảo toàn primary/suppressed, ưu tiên try-with-resources |
| Retry mọi exception | Dễ tăng tải/side effect, che bug | Chỉ retry có phân loại và idempotency |
| Bắt `InterruptedException` rồi bỏ qua | Mất yêu cầu hủy công việc | Truyền tiếp hoặc khôi phục cờ và kết thúc theo hợp đồng |
| Kiểm thử chỉ “không crash” | Không phát hiện thành công giả | Assert loại lỗi, cause, state và cleanup |
| `throws Exception` tùy tiện ở mọi tầng | Hợp đồng quá rộng và khó biết loại lỗi | Khai báo các checked exception có ý nghĩa hoặc dùng abstraction thích hợp |

## 19. Câu hỏi tự kiểm tra

**Câu 1.** Phương thức `void read() throws IOException` chắc chắn sẽ ném ngoại lệ mỗi khi gọi?  
**Đáp án:** Không. `throws` chỉ khai báo khả năng; phương thức vẫn có thể hoàn tất bình thường.

**Câu 2.** `throw new IOException("failed")` và `throws IOException` khác gì?  
**Đáp án:** `throw` là câu lệnh ném object; `throws` là một phần chữ ký để khai báo checked exception có thể thoát ra.

**Câu 3.** Repository biết thao tác đọc file thất bại nhưng không biết caller có được dùng cache hay không. Nên làm gì?  
**Đáp án:** Thường nên lan truyền `IOException` hoặc dịch lỗi ở ranh giới phù hợp để tầng sở hữu chính sách quyết định.

**Câu 4.** Khi bắt `NumberFormatException` từ đầu vào người dùng, trả trạng thái `INVALID_INPUT` có thể hợp lệ không?  
**Đáp án:** Có, nếu đó là tình huống dự kiến, chưa cập nhật dữ liệu và kết quả phản ánh lỗi thật.

**Câu 5.** `catch (IOException e) { throw new IllegalStateException(e.getMessage()); }` đã giữ cause chưa?  
**Đáp án:** Chưa. Dùng `new IllegalStateException("context", e)` để liên kết ngoại lệ gốc.

**Câu 6.** Để `IOException` lan truyền từ `try-with-resources` có làm `close()` bị bỏ qua không?  
**Đáp án:** Không. Resource vẫn được đóng theo quy tắc của `try-with-resources`; nếu body và close cùng lỗi, lỗi close thường nằm trong `getSuppressed()` của lỗi body.

**Câu 7.** Một phương thức trả `"CACHED_DATA"` từ nhánh catch có nghĩa thao tác nguồn đã thành công chưa?  
**Đáp án:** Không. Đó là phục hồi bằng fallback, chỉ hợp lệ nếu hợp đồng cho phép và caller không bị đánh lừa về nguồn/kết quả.

**Câu 8.** Có phải mọi `RuntimeException` đều cần thêm `throws RuntimeException`?  
**Đáp án:** Không. Ngoại lệ unchecked không bắt buộc phải khai báo.

**Câu 9.** Vì sao `throws` không tự bảo đảm transaction rollback?  
**Đáp án:** Nó chỉ truyền ngoại lệ; dữ liệu đã thay đổi cần chính sách transaction/rollback/compensation riêng.

**Câu 10.** Khi xử lý `InterruptedException` trong `Runnable.run()`, cần chú ý gì?  
**Đáp án:** Không nuốt yêu cầu ngắt; nếu không truyền tiếp được, thường khôi phục interrupted status rồi rời tác vụ theo hợp đồng.

**Câu 11.** Tầng HTTP bắt ngoại lệ để trả phản hồi thất bại có nhất thiết là “recover thành công” không?  
**Đáp án:** Không. Đó có thể chỉ là chuyển một thất bại nội bộ thành phản hồi lỗi an toàn tại ranh giới.

**Câu 12.** Khi nào nên vừa `catch` vừa ném mới?  
**Đáp án:** Khi cần dịch lỗi sang tầng trừu tượng thích hợp, thêm ngữ cảnh có ích và giữ cause, nhưng chưa thể phục hồi kết quả nghiệp vụ.

## 20. Tài liệu tham khảo

- [Java Language Specification §11 — Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html): cơ chế ngoại lệ, checked/unchecked và kiểm tra khả năng ném.
- [Java Language Specification §8.4.6 — Method Throws](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.6): khai báo `throws` trong chữ ký phương thức.
- [Java SE 17 — `Throwable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): `getCause()`, stack trace và suppressed exceptions.
- [Java SE 17 — `IOException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/IOException.html): ngoại lệ checked cho nhiều thao tác I/O.
- [Java SE 17 — `AutoCloseable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html): hợp đồng tài nguyên của `try-with-resources`.

**Tổng kết:** *Bắt tại chỗ* khi tầng hiện tại có thể xử lý đúng và bảo đảm trạng thái hợp lệ. *Lan truyền bằng `throws`* khi tầng hiện tại chưa đủ ngữ cảnh hoặc quyền quyết định; việc đó giữ trách nhiệm ở tầng cao và không tự xử lý lỗi. Nếu cần đổi loại ngoại lệ, hãy dùng wrapper có `cause`. Trong mọi trường hợp, phải dọn tài nguyên đúng, không che lỗi gốc và kiểm thử cả trạng thái sau thất bại.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-practice-003">Chuyển nhiều khối catch giống nhau thành multi-catch</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển nhiều khối catch giống nhau thành multi-catch trong Java

> **Chủ đề:** Java Core → Exception → Thực hành. Bài hướng dẫn refactor nhiều nhánh `catch` xử lý tương tự thành **multi-catch** (`catch (TypeA | TypeB ex)`) từ Java 7, áp dụng trong Java 17+. Mục tiêu không chỉ giảm mã lặp, mà còn **giữ nguyên hành vi, chính sách xử lý, loại ngoại lệ, nguyên nhân gốc và khả năng chẩn đoán**. Có chương trình trước–sau chạy độc lập, bài tập, kiểm thử JUnit 5 và các trường hợp không nên gom.

## 1. Nhận diện bài toán: các catch gần như giống hệt nhau

Một phương thức có thể gặp nhiều exception đã biết nhưng cùng có chính sách xử lý:

~~~java
// Trích đoạn: fetchValue có thể throws IOException, SQLException.
try {
    return fetchValue();
} catch (IOException ex) {
    recordFailure(ex);
    return "fallback";
} catch (SQLException ex) {
    recordFailure(ex);
    return "fallback";
}
~~~

Hai nhánh chỉ khác **loại ngoại lệ**; cả hai gọi cùng hàm ghi nhận và trả cùng kết quả. Sao chép nội dung có thể dẫn đến việc một nhánh được sửa mà nhánh kia bị bỏ quên, ví dụ chỉ một nhánh bổ sung metric, log hoặc cleanup.

Java cho phép gộp bằng toán tử `|` **trong phần khai báo tham số catch**:

~~~java
try {
    return fetchValue();
} catch (IOException | SQLException ex) {
    recordFailure(ex);
    return "fallback";
}
~~~

Khi một trong hai loại được ném từ khối `try`, cùng một thân `catch` chạy. **Đây không phải bitwise OR hay logical OR trên giá trị boolean**: dấu `|` trong ngữ cảnh này kết hợp các kiểu exception thay thế. Khi refactor, chỉ có một `catch` được chọn cho mỗi exception được xử lý; không chạy tuần tự hai nhánh.

**Điều kiện cần:** các loại exception thực sự có **cùng chính sách** tại ranh giới này. Hai thân cùng dài hoặc đều gọi `log.error` chưa đủ để chứng minh semantics giống nhau.

## 2. Khi nào multi-catch phù hợp?

Thường phù hợp khi:

- Hai hay nhiều ngoại lệ có thể được xử lý bằng **cùng một fallback value**.
- Cùng được chuyển thành một exception cấp cao **với cause được giữ**.
- Cùng dẫn tới một trạng thái kết thúc tác vụ, ví dụ fail-fast tại boundary.
- Cùng cần ghi một sự kiện chẩn đoán với operation/request ID, trong khi loại gốc vẫn giữ bằng `ex.getClass()`.
- Cùng được truyền tiếp cho caller theo đúng chữ ký `throws`.
- Cùng hành vi retry/backoff, **đã chứng minh retry an toàn cho cả hai**.
- Thân `catch` không cần truy cập những phương thức chỉ có ở một subtype riêng.

Ví dụ:

~~~java
// Trích đoạn bên trong phương thức của ứng dụng.
try {
    performOperation();
} catch (IOException | SQLException ex) {
    throw new DataAccessFailure("operation failed", ex);
}
~~~

`DataAccessFailure` là lớp do ứng dụng định nghĩa, có constructor nhận `String, Throwable`. `ex` vẫn là **đối tượng exception thực tế** vừa được ném; `new DataAccessFailure(..., ex)` giữ chuỗi nguyên nhân.

**Không nên gom** nếu IOException là lỗi không nên retry nhưng SQLException là lỗi tạm thời nên retry; hoặc IOException cần khôi phục resource còn SQLException cần rollback transaction riêng. Khi policies khác nhau, giữ `catch` riêng giúp người đọc thấy đúng ý định.

## 3. Cú pháp multi-catch chính xác

Cú pháp cơ bản:

~~~java
try {
    riskyOperation();
} catch (IOException | SQLException ex) {
    handle(ex);
}
~~~

Quy tắc:

1. Các alternative cách nhau bằng một dấu `|` bên trong **cùng cặp ngoặc** của `catch`.
2. Chỉ có **một biến** ở cuối danh sách, ở ví dụ là `ex`.
3. Các kiểu phải là kiểu exception phù hợp: subtype của `Throwable`, tuân quy tắc checked/unchecked và khả năng exception thực sự được ném.
4. **Không một alternative nào được là subtype của alternative khác** trong cùng một multi-catch; đây là lỗi biên dịch.
5. Biến `ex` trong multi-catch là **implicitly final**; không thể gán lại một giá trị khác.
6. Các `catch` khác, nếu có, vẫn được xét **theo thứ tự từ trên xuống**, với quy tắc handler rộng đứng sau handler hẹp.
7. Các exception không được bắt vẫn tiếp tục propagate; multi-catch không tự biến exception thành thành công.

Cú pháp sai:

~~~java
// SAI: không phải cú pháp nhiều biến trong một catch.
catch (IOException io | SQLException sql) {
    // ...
}
~~~

Cú pháp đúng:

~~~java
catch (IOException | SQLException ex) {
    // Một tham số ex duy nhất.
}
~~~

Tên `ex` có scope trong thân `catch`; ngoài thân đó nó không tồn tại. Có thể dùng tên khác, ví dụ `failure`, nếu rõ hơn.

## 4. Hai điều kiện biên dịch dễ bị bỏ sót

### 4.1. Các kiểu trong cùng multi-catch không được có quan hệ kế thừa trực tiếp

Ví dụ không hợp lệ:

~~~java
// SAI: FileNotFoundException là con của IOException.
catch (java.io.FileNotFoundException | java.io.IOException ex) {
    // Compile error: alternatives related by subclassing.
}
~~~

Tại sao? `IOException` đã bao gồm `FileNotFoundException`; thêm alternative hẹp tạo dư thừa, làm cho hợp đồng bắt lỗi mơ hồ. Nếu chính sách thực sự giống nhau, chỉ cần:

~~~java
catch (java.io.IOException ex) {
    // Bắt cả FileNotFoundException và các IOException khác.
}
~~~

Nhưng **không nên thay hai ngoại lệ không liên quan bằng một lớp cha quá rộng chỉ cho ngắn**. Ví dụ `catch (Exception ex)` có thể nuốt luôn lỗi runtime không dự kiến và `InterruptedException`. `IOException | SQLException` nói rõ chính xác hai họ lỗi mà bạn chủ động xử lý.

### 4.2. Biến catch của multi-catch không thể gán lại

~~~java
// SAI: ex được coi là final trong multi-catch.
catch (IOException | SQLException ex) {
    ex = new IOException("replacement"); // Compile error.
}
~~~

Hãy tạo biến mới để chuyển đổi, hoặc trực tiếp tạo wrapper:

~~~java
catch (IOException | SQLException ex) {
    RuntimeException wrapped =
            new IllegalStateException("operation failed", ex);
    throw wrapped;
}
~~~

Không nên gán đè exception gốc rồi làm mất `cause`. Việc biến multi-catch là final còn giúp giữ rõ exception nào thực sự được bắt.

## 5. Kiểu của biến ex trong thân multi-catch

Một câu hỏi thường gặp: `ex` là `IOException` hay `SQLException`?

Trong runtime, nó vẫn tham chiếu tới đối tượng cụ thể được ném. Nhưng tại compile time, những method có thể gọi trực tiếp trên `ex` được xác định theo **kiểu chung thích hợp** của các alternative. Với `IOException | SQLException`, các thao tác chung của `Throwable`/`Exception` như sau đều dùng được:

~~~java
catch (IOException | SQLException ex) {
    String type = ex.getClass().getSimpleName();
    String message = ex.getMessage();
    Throwable cause = ex.getCause();
    // Gọi logger truyền Throwable đầy đủ.
}
~~~

Ngược lại, `SQLException.getSQLState()` không phải method của `IOException`, nên không thể gọi `ex.getSQLState()` trực tiếp trên biến union đó.

Nếu policy thực sự cần SQL state, giữ hai nhánh riêng; hoặc thực hiện phân loại có kiểu rõ ràng khi điều đó **chỉ nhằm thêm context, không thay đổi outcome**:

~~~java
catch (IOException | SQLException ex) {
    if (ex instanceof SQLException sql) {
        recordSqlState(sql.getSQLState());
    }
    recordFailure(ex);
    throw new DataAccessFailure("operation failed", ex);
}
~~~

Đoạn này là trích đoạn trong lớp có `recordSqlState`, `recordFailure` và `DataAccessFailure`. Java 17 hỗ trợ pattern matching `instanceof`. Nếu trong `catch` xuất hiện nhiều `instanceof` với các đường xử lý khác nhau, đó là dấu hiệu nên xem lại quyết định gom: hai `catch` riêng có thể rõ hơn.

## 6. Ví dụ Java 17 đầy đủ: trước và sau khi refactor

Chương trình cố ý tạo **hai lỗi checked khác họ**: `IOException` (nguồn file) và `SQLException` (nguồn database). Cả hai phải trả cùng một fallback tại ranh giới đang xét. Nhánh thành công giữ kết quả cũ. Không phụ thuộc file hay database thật, nên stdout xác định.

Lưu vào **`MultiCatchRefactorDemo.java`**:

~~~java
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class MultiCatchRefactorDemo {
    static String fetch(String source) throws IOException, SQLException {
        if ("file".equals(source)) {
            throw new IOException("disk unavailable");
        }
        if ("db".equals(source)) {
            throw new SQLException("database unavailable");
        }
        return "ready";
    }

    static String fallback(Exception failure) {
        return "fallback(" + failure.getClass().getSimpleName()
                + ": " + failure.getMessage() + ")";
    }

    // Trước refactor: hai thân catch giống hệt nhau.
    static String before(String source) {
        try {
            return "value=" + fetch(source);
        } catch (IOException ex) {
            return fallback(ex);
        } catch (SQLException ex) {
            return fallback(ex);
        }
    }

    // Sau refactor: gộp đúng hai họ lỗi có cùng policy.
    static String after(String source) {
        try {
            return "value=" + fetch(source);
        } catch (IOException | SQLException ex) {
            return fallback(ex);
        }
    }

    // Multi-catch không buộc phải nuốt hoặc bọc exception.
    static void relay(String source) throws IOException, SQLException {
        try {
            fetch(source);
        } catch (IOException | SQLException ex) {
            throw ex;
        }
    }

    public static void main(String[] args) {
        for (String source : List.of("ok", "file", "db")) {
            System.out.println(source + ": before=" + before(source)
                    + " | after=" + after(source));
        }

        try {
            relay("file");
        } catch (IOException ex) {
            System.out.println("relay file: "
                    + ex.getClass().getSimpleName()
                    + ": " + ex.getMessage());
        } catch (SQLException ex) {
            throw new AssertionError("Unexpected SQL exception", ex);
        }

        try {
            relay("db");
        } catch (SQLException ex) {
            System.out.println("relay db: "
                    + ex.getClass().getSimpleName()
                    + ": " + ex.getMessage());
        } catch (IOException ex) {
            throw new AssertionError("Unexpected IO exception", ex);
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac MultiCatchRefactorDemo.java
java MultiCatchRefactorDemo
~~~

**Stdout chính xác:**

~~~text
ok: before=value=ready | after=value=ready
file: before=fallback(IOException: disk unavailable) | after=fallback(IOException: disk unavailable)
db: before=fallback(SQLException: database unavailable) | after=fallback(SQLException: database unavailable)
relay file: IOException: disk unavailable
relay db: SQLException: database unavailable
~~~

### Phân tích

1. `fetch("ok")` hoàn tất bình thường và hai phương thức đều trả `"value=ready"`.
2. `fetch("file")` ném `IOException`. `before` chọn `catch IOException`; `after` chọn multi-catch. Cả hai chuyển vào cùng helper `fallback` và giữ cùng kiểu/message.
3. `fetch("db")` ném `SQLException`; chính sách cũng giống trường hợp file.
4. `relay` minh họa **ném lại nguyên exception** từ multi-catch. Caller vẫn bắt riêng `IOException` và `SQLException`.
5. `List.of` giữ đúng thứ tự ba trường hợp. Không có concurrency, timeout hoặc dữ liệu I/O thực nên thứ tự output cố định.
6. Trong ứng dụng thật, `fallback` có thể đồng thời cập nhật một metric và trả một `Result` có cấu trúc; không bắt buộc return chuỗi như ví dụ.
7. Chương trình chứng minh **tính tương đương theo những tình huống đã định nghĩa**, không thay thế test hồi quy đầy đủ cho code production.

**Bài học từ code:** rút gọn một nhánh catch không làm thay đổi runtime class của exception. Việc gộp phù hợp khi chính sách của các lỗi giống nhau, không phải vì chúng đều là checked exception.

## 7. Precise rethrow: ném lại mà vẫn giữ hợp đồng checked exception

Một lợi ích quan trọng của multi-catch là ném lại `ex` **không bắt buộc** phải khai báo `throws Exception` quá rộng. Với ví dụ:

~~~java
// Trích từ một lớp có fetch(String).
static void relay(String source) throws IOException, SQLException {
    try {
        fetch(source);
    } catch (IOException | SQLException ex) {
        throw ex;
    }
}
~~~

Compiler biết `ex` chỉ có thể mang các loại mà handler ấy bắt từ `try`. Phương thức có thể công bố đúng `throws IOException, SQLException`; caller xử lý riêng hai ngoại lệ và không phải bắt mọi `Exception`.

Đây là **precise rethrow** được Java hỗ trợ từ Java 7, liên quan tới phân tích loại exception của compiler, đặc biệt khi tham số catch không bị gán lại. Multi-catch parameter là implicitly final nên không có trường hợp code trong catch gán đè biến đó.

Không nhầm hai dạng:

~~~java
// Giữ nguyên exception object và stack gốc.
throw ex;
~~~

và:

~~~java
// Tạo exception mới, cần gắn cause để giữ dấu vết.
throw new IllegalStateException("request failed", ex);
~~~

Dạng thứ hai đổi abstraction/hợp đồng: caller có thể thấy `IllegalStateException` thay vì `IOException`/`SQLException`. Dùng khi API cố ý chuyển lỗi kỹ thuật thành lỗi domain hoặc unchecked, **không phải bước tự động của refactor multi-catch**.

### Precise rethrow có thể áp dụng cho catch đơn không?

Có. Các cải tiến phân tích checked exceptions của Java 7 không chỉ dành cho cú pháp có dấu `|`. Với một `catch (Exception ex)` **không được gán lại**, compiler đôi khi xác định những checked exception có thể thoát theo ngữ cảnh `try`. Nhưng dùng `catch (Exception)` để xử lý mọi lỗi vẫn có thể tạo nghĩa quá rộng: compiler chấp nhận không có nghĩa chính sách runtime tốt.

## 8. Thứ tự handler và tính reachability

Java thử các `catch` theo thứ tự xuất hiện. Handler rộng đặt trước có thể làm handler phía sau **không thể đến được**, dẫn đến lỗi biên dịch.

Ví dụ sai:

~~~java
// SAI: catch(Exception) đã bao gồm IOException và SQLException.
try {
    fetch(source);
} catch (Exception ex) {
    handle(ex);
} catch (IOException | SQLException ex) {
    handle(ex);
}
~~~

Mã đã được refactor đúng phải đảm bảo:

- Multi-catch của các loại hẹp/đích danh đặt trước handler rộng có ý nghĩa khác.
- Không có alternative nào là subtype của alternative khác trong **cùng** danh sách.
- Các handler còn lại vẫn xử lý các họ exception khác theo đúng policy.
- Mã bên trong `try` có thể ném các checked exceptions tương ứng; không thêm một alternative checked mà compiler biết chắc không có đường ném.
- Không vô tình gộp `RuntimeException` hoặc `Exception` như một cách bắt mọi sự cố.

Ví dụ hợp lệ:

~~~java
try {
    fetch(source);
} catch (IOException | SQLException ex) {
    recordExpectedInfrastructureFailure(ex);
} catch (RuntimeException unexpected) {
    reportProgrammingOrContractFailure(unexpected);
}
~~~

Đây là mã minh họa với các helper do ứng dụng định nghĩa. Không phải mọi `RuntimeException` đều là bug, nhưng handler này được tách vì **chính sách** của nhánh runtime khác hai loại checked đã biết.

## 9. Không gom nếu hai policy thực sự khác nhau

Giả sử hệ thống có logic:

~~~java
try {
    storeOrder();
} catch (IOException ex) {
    // Lỗi I/O trong bước đọc local file: đánh dấu input không khả dụng.
    markInputUnavailable(ex);
} catch (SQLException ex) {
    // Lỗi giao dịch: rollback và chuyển nguyên nhân để retry theo chính sách DB.
    rollbackTransaction();
    throw new RepositoryFailure("database write failed", ex);
}
~~~

Gom thành:

~~~java
catch (IOException | SQLException ex) {
    log.warn("operation failed", ex);
}
~~~

là **thay đổi semantics nguy hiểm**: mất rollback, nuốt lỗi SQL, đảo outcome, có thể để hệ thống ở trạng thái sai. Dù cả hai thân cũ có một số dòng logging giống nhau, phần khác nhau mang tính nghiệp vụ và phải được giữ lại.

Phương án tốt hơn là trích xuất đúng phần chung:

~~~java
try {
    storeOrder();
} catch (IOException ex) {
    recordCommonContext(ex);
    markInputUnavailable(ex);
} catch (SQLException ex) {
    recordCommonContext(ex);
    rollbackTransaction();
    throw new RepositoryFailure("database write failed", ex);
}
~~~

Đây là các đoạn minh họa không chạy độc lập vì còn thiếu class/helper. Khi policies khác nhau, chia sẻ hàm nhỏ (`recordCommonContext`) rõ ràng hơn multi-catch có nhiều `instanceof`.

**Các tín hiệu cần giữ catch riêng:** số lần retry khác nhau, mức log khác nhau, transaction rollback khác nhau, fallback không giống nhau, trạng thái trả về khác, timeout/hủy cần xử lý đặc biệt hoặc subtype-specific API cần đọc.

## 10. InterruptedException cần chính sách riêng

Một sai lầm thường thấy khi gom các loại ngoại lệ cho tiện:

~~~java
// Có thể biên dịch theo ngữ cảnh nhưng rất dễ sai về policy.
try {
    blockingOperation();
    databaseOperation();
} catch (InterruptedException | SQLException ex) {
    log.warn("operation failed", ex);
    return;
}
~~~

Đoạn trên chỉ minh họa, giả định `blockingOperation` ném `InterruptedException`, `databaseOperation` ném `SQLException` và method có thể `return`. Vấn đề: `InterruptedException` là tín hiệu về việc **thread đang chờ** bị ngắt. Với nhiều blocking APIs như `Thread.sleep`, `Object.wait` và `Thread.join`, interrupt flag được xóa khi ném exception.

Nếu method không thể truyền `InterruptedException` lên (chẳng hạn `Runnable.run()`), nó thường cần `Thread.currentThread().interrupt()` rồi thoát. `SQLException` lại có chính sách xử lý DB hoàn toàn khác. Gom hai ngoại lệ vào một handler chung mà không phục hồi cờ có thể làm hỏng giao thức cancellation.

Hãy tách:

~~~java
try {
    blockingOperation();
    databaseOperation();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    return;
} catch (SQLException ex) {
    recordDatabaseFailure(ex);
    return;
}
~~~

Một ngoại lệ chỉ nên vào multi-catch với `InterruptedException` nếu **thật sự** có cùng chính sách interrupt và ứng dụng đã chứng minh điều đó, tình huống hiếm. Tránh chỉ vì cả hai nhánh kết thúc bằng `return` mà bỏ qua trạng thái thread.

## 11. Giữ nguyên cause, stack trace và các lỗi bị suppress

Refactor multi-catch không đòi hỏi tạo exception mới. Nếu cùng một lỗi đi lên đúng abstraction, có thể `throw ex` hoặc để tự propagate. Nếu đổi tầng abstraction, bọc bằng constructor nhận `cause`:

~~~java
catch (IOException | SQLException ex) {
    throw new DataAccessFailure("unable to load order", ex);
}
~~~

Không làm như sau:

~~~java
catch (IOException | SQLException ex) {
    throw new IllegalStateException(ex.getMessage()); // Mất cause.
}
~~~

Dạng thứ hai khiến caller khó tìm stack trace gốc và metadata kỹ thuật. Ngoài `getCause()`, một `Throwable` còn có suppressed exceptions (thường phát sinh từ `try-with-resources`). Multi-catch **không làm mất** những dữ liệu này nếu bạn chuyển nguyên object đúng cách. Đừng chỉ copy message rồi bỏ throwable.

### Try-with-resources vẫn hoạt động

~~~java
// Trích đoạn minh họa, có Connection connection được mở thích hợp.
try (java.sql.Connection c = connection) {
    writeData(c);
} catch (java.sql.SQLException | IllegalStateException ex) {
    reportFailure(ex);
}
~~~

Đây là đoạn không độc lập; `writeData`, `connection`, `reportFailure` do ứng dụng cung cấp. `try-with-resources` chịu trách nhiệm đóng resource theo hợp đồng; nếu thao tác trong try và việc đóng cùng ném lỗi, lỗi đóng có thể được đưa vào `getSuppressed()`. Việc gộp catch **không biến failure thành transaction rollback** và không thay thế trách nhiệm `commit`/`rollback` theo kiến trúc DB.

## 12. Logging: gom handler không có nghĩa spam log ít lần theo mặc định

Một lợi ích code-level của multi-catch là tập trung policy log cho nhiều loại lỗi:

~~~java
catch (IOException | SQLException ex) {
    log.error("event=order_load_failed operation={} orderId={}",
            operation, orderId, ex);
    throw new OrderLoadFailure("load failed", ex);
}
~~~

Đây là ví dụ dùng một API logger kiểu SLF4J; cần dependency logger và các biến/class được định nghĩa trong dự án. Nếu tầng trên cũng ghi cùng stack trace rồi ném tiếp, **vẫn có log trùng**, dù tầng này đã multi-catch. Quy tắc log ownership độc lập với cú pháp catch:

- Tầng chỉ chuyển lỗi: thêm context/cause rồi rethrow; có thể không cần `ERROR` log tại chỗ.
- Tầng sở hữu outcome cuối: ghi một sự kiện failure đủ operation/request ID/business ID/Throwable.
- Retry/fallback/compensation là **sự kiện riêng** có thể cần log hoặc metrics phù hợp.
- Không dùng message của exception như machine-readable error code.
- Phân biệt severity và chính sách cho IOException/SQLException nếu chúng không thật sự giống nhau.

Nếu một handler multi-catch có 10 câu `if (ex instanceof ...)` chỉ để chọn message/log level thì refactor có thể khiến code khó đọc hơn hai catch đơn giản.

## 13. Đừng gộp chỉ vì cách viết thân catch giống nhau

Hai đoạn trông giống hệt:

~~~java
catch (IOException ex) {
    retry(ex);
}
catch (SQLException ex) {
    retry(ex);
}
~~~

nhưng helper `retry(ex)` có thể dựa vào runtime type và thực hiện các policy khác nhau; cần đọc hợp đồng helper trước khi kết luận refactor an toàn. Tính tương đương không chỉ là cùng các dòng text, mà còn gồm:

- Callback/tham số được truyền có cùng nghĩa không?
- Có đọc subtype field (SQLState, file path, status) ở nơi khác không?
- Exception nào cần restore interrupt flag?
- Số lần thử, delay và idempotency có bằng nhau không?
- Có cần rollback/compensation trước khi retry?
- Mức log và public error code có cùng không?
- Caller sẽ thấy loại exception nào sau khi handler kết thúc?

Nếu cùng method `handle(ex)` nhưng bên trong phân nhánh theo class, multi-catch có thể vẫn đúng ở mức cơ chế; tuy nhiên refactor nên được đánh giá theo **độ rõ ràng của hợp đồng** chứ không chỉ số dòng.

## 14. Từng bước refactor an toàn trong IDE

Một quy trình thực hành có thể lặp lại:

1. **Khoanh vùng một khối try:** đọc các nguồn exception có thể xảy ra và mọi catch hiện tại.
2. **Tìm các thân catch giống nhau:** so sánh cả return, throw, finally, logging, cleanup và trạng thái được thay đổi.
3. **Xác nhận cùng policy:** không chỉ cùng câu chữ; cùng outcome, retry, interrupt và transaction semantics.
4. **Kiểm tra quan hệ kiểu:** các exception trong cùng `|` không là subtype của nhau.
5. **Kiểm tra subtype-specific method:** nếu thân catch dùng `getSQLState()`, `getFile()`, v.v., cần đổi cấu trúc hoặc giữ catch riêng.
6. **Thay bằng một multi-catch:** giữ body đúng nguyên nghĩa, không tự ý đổi fallback hay tạo wrapper khác.
7. **Biên dịch:** compiler bắt unreachable catches, type alternatives liên quan nhau và việc gán lại `ex`.
8. **Chạy unit tests:** case cho từng alternative và các lỗi không thuộc danh sách.
9. **Kiểm tra side effect:** nguyên nhân, callback count, metrics, log count, rollback/retry.
10. **Đánh giá readability:** nếu phải thêm nhiều `instanceof`, rollback policy hoặc cast, quay lại catch riêng.
11. **Kiểm tra binary/public contract:** refactor internal không nhất thiết đổi API; nếu đổi `throws`/kiểu wrapper public, cần xem compatibility.
12. **Review diff:** số dòng giảm là kết quả phụ; invariant quan trọng là hành vi không đổi.

IDE có thể đề xuất “Join multiple catch sections” hoặc tương tự, nhưng developer vẫn phải đánh giá chính sách xử lý. Một quick fix hợp cú pháp không thể thay thế việc hiểu nghiệp vụ.

## 15. Bộ test JUnit 5: chứng minh hành vi trước–sau

Các phương thức `before`, `after`, `relay` của chương trình `MultiCatchRefactorDemo` có package-private visibility. Nếu đặt lớp test sau trong **cùng package**, có thể gọi trực tiếp để kiểm tra. Đây là **mã JUnit 5**, cần dependency và test engine, không chạy chỉ bằng `javac` JDK thuần.

~~~java
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class MultiCatchRefactorDemoTest {
    @Test
    void sameResultForSuccessAndEachHandledException() {
        for (String source : new String[] {"ok", "file", "db"}) {
            assertEquals(MultiCatchRefactorDemo.before(source),
                    MultiCatchRefactorDemo.after(source), source);
        }
    }

    @Test
    void ioExceptionIsStillRethrownAsIoException() {
        IOException ex = assertThrows(IOException.class,
                () -> MultiCatchRefactorDemo.relay("file"));
        assertEquals("disk unavailable", ex.getMessage());
    }

    @Test
    void sqlExceptionIsStillRethrownAsSqlException() {
        SQLException ex = assertThrows(SQLException.class,
                () -> MultiCatchRefactorDemo.relay("db"));
        assertEquals("database unavailable", ex.getMessage());
    }

    @Test
    void successRemainsSuccessful() {
        assertDoesNotThrow(() -> MultiCatchRefactorDemo.relay("ok"));
        assertEquals("value=ready",
                MultiCatchRefactorDemo.after("ok"));
    }
}
~~~

Các test cần phân biệt:

- Đường thành công trước/sau **trả đúng cùng giá trị**.
- Từng alternative có ít nhất một test riêng để phát hiện trường hợp một loại không còn được bắt.
- Nếu policy là rethrow, caller vẫn nhận **kiểu gốc** và message/cause theo hợp đồng.
- Nếu policy bọc thành `DomainException`, kiểm tra `getCause()` và error code, không chỉ assert wrapper.
- Nếu có side effect, xác nhận chính sách cleanup/transaction và không ghi log/metric ngoài dự kiến.
- Nếu handler chỉ dùng `fallback`, test nên xác minh lỗi programming khác vẫn propagate, không bị nuốt bởi catch quá rộng.

Bộ JUnit bên trên kiểm tra hành vi theo ví dụ tối giản. Trong dự án thật, có thể dùng fake/mock dependency để gây chính xác từng exception ở cùng điểm, thay vì phải tác động tới file/DB thật.

## 16. Test checked và unchecked exception một cách nhất quán

Một lời gọi trong `try` có thể ném cả checked exception được khai báo và unchecked exception ngoài dự kiến. Nếu trước refactor chỉ bắt `IOException` và `SQLException`, sau refactor cũng chỉ nên bắt **đúng hai họ ấy**.

Ví dụ:

~~~java
// Đoạn minh họa.
try {
    fetch(source);
} catch (IOException | SQLException ex) {
    return fallback(ex);
}
~~~

Nếu `fetch` gặp `NullPointerException` do bug thì nó không tự trở thành fallback. Đây thường là behavior mong muốn: không biến bug thành kết quả thành công giả. Nếu đổi sang:

~~~java
catch (Exception ex) {
    return fallback(ex);
}
~~~

thì chương trình có thể nuốt hàng loạt `RuntimeException` khác và cả những loại checked exception không thuộc policy đã thiết kế. Test nên có một case phun `IllegalStateException` hoặc `NullPointerException` qua fake dependency để chắc chắn nó vẫn propagate.

**Đừng ném unchecked exception giả chỉ để chạm nhánh checked catch.** Test cho `catch (IOException | SQLException ex)` cần tạo đúng `IOException` và đúng `SQLException`; test chỉ tạo `RuntimeException` không kiểm tra được handler này.

## 17. Multi-catch và đa hình: khi nào chỉ cần một catch cha?

Nếu mọi lỗi đều kế thừa một base type đã được thiết kế cho domain:

~~~java
// Trích đoạn với base class DomainFailure.
catch (DomainFailure ex) {
    handleDomainFailure(ex);
}
~~~

có thể rõ hơn một danh sách hàng chục subtype. Nhưng chỉ đúng nếu **mọi subtype của DomainFailure** đều cần chính sách đó, kể cả subtype mới thêm về sau.

Dùng multi-catch:

~~~java
catch (ValidationFailure | ConflictFailure ex) {
    handleExpectedRejection(ex);
}
~~~

phù hợp hơn khi chỉ hai subtype không có quan hệ kế thừa trực tiếp với nhau và không muốn bắt những subtype khác của base class. Các tên class này là minh họa và phải được định nghĩa nếu dùng trong dự án.

Trước khi chọn, hỏi: hệ thống cần policy cho **toàn bộ họ lỗi** hay chỉ **một tập lỗi cụ thể**? Một `catch (DomainFailure)` có thể quá rộng về tương lai khi hệ thống bổ sung subtype báo sự cố hạ tầng. Multi-catch giúp biểu đạt danh sách lựa chọn có chủ đích.

## 18. Tác động tới hiệu năng và bảo trì

Multi-catch chủ yếu là **cải tiến cấu trúc và khả năng bảo trì**. Nó có thể giảm duplicate bytecode/handler code trong vài tình huống, nhưng không nên hứa hẹn tăng tốc đáng kể hoặc giảm chi phí `Throwable` chỉ vì đổi hai catch thành một.

Một ngoại lệ vẫn được tạo/ném/tra handler theo JVM; chi phí stack trace, log hoặc I/O không biến mất nhờ dấu `|`. Nếu exception xuất hiện thường xuyên như **luồng điều khiển bình thường**, cần đánh giá thiết kế result/condition riêng thay vì chỉ tối ưu catch. Nếu lỗi hiếm và catch giống nhau, multi-catch làm code dễ review hơn và giảm nguy cơ sửa lệch policy.

Về khả năng bảo trì:

- Một chỗ để cập nhật handler chung.
- Dễ nhận diện tập exception được cố ý phục hồi.
- Giảm việc copy-paste log/wrap lỗi.
- Tránh việc bổ sung ID hoặc cleanup ở một nhánh mà quên nhánh khác.
- Nhưng có thể **giảm clarity** nếu danh sách `|` quá dài hoặc policy chỉ “na ná” nhau.

Không bắt buộc tất cả catch phải được gom. Code rõ nghĩa và đúng invariant quan trọng hơn một con số lines-of-code thấp.

## 19. Các lỗi phổ biến khi làm bài tập

1. **Gom hai subtype có quan hệ cha–con:** `FileNotFoundException | IOException` không hợp lệ.
2. **Đặt tên biến cho từng alternative:** multi-catch chỉ có một `ex`.
3. **Gán lại tham số `ex`:** biến trong multi-catch là implicitly final.
4. **Đưa handler cha lên trước:** `catch(Exception)` trước handler hẹp làm unreachable code.
5. **Thay danh sách bằng `catch(Exception)` cho tiện:** nuốt lỗi không dự kiến.
6. **Gom theo cách viết nhưng bỏ qua policy:** retry, rollback, fallback, cancel/interrupt có thể khác.
7. **Dùng subtype-specific method trên union parameter:** `getSQLState()` không gọi trực tiếp khi `ex` có thể là `IOException`.
8. **Bọc lại exception nhưng bỏ cause:** `new RuntimeException(ex.getMessage())` mất nguyên nhân gốc.
9. **Log rồi throw ở mọi tầng:** multi-catch không tự loại duplication logs.
10. **Coi `InterruptedException` như lỗi bất kỳ:** có thể làm mất interrupt flag và khiến worker không dừng.
11. **Bắt lỗi không thể được ném từ try:** compiler có thể báo checked exception never thrown.
12. **Thêm `return` để gộp hai catch:** vô tình biến thất bại thành success hoặc bỏ lỡ rollback.
13. **Giả định kiểu compile-time của ex là subtype cụ thể:** `ex` phải phù hợp các alternative.
14. **Ném lại bằng wrapper mới mà không cập nhật hợp đồng API:** caller có thể đổi cách bắt ngoại lệ.
15. **Chỉ test một alternative:** nhánh còn lại có thể bị thay đổi mà không ai thấy.
16. **Cho rằng ít code luôn nhanh hơn:** không có benchmark hoặc bằng chứng hiệu năng.
17. **Quên try-with-resources/suppressed exceptions:** handler cần giữ Throwable đầy đủ khi chẩn đoán.
18. **Lạm dụng `instanceof` trong multi-catch:** nếu phần lớn thân catch rẽ nhánh trở lại, nên giữ catch riêng.

## 20. Bài luyện tập theo mức độ

### Bài A — Refactor đúng hai nhánh giống nhau

Cho code:

~~~java
try {
    export();
} catch (IOException ex) {
    markFailed(ex);
    return false;
} catch (SQLException ex) {
    markFailed(ex);
    return false;
}
~~~

Yêu cầu: viết lại bằng multi-catch và giữ nguyên behavior. Đáp án:

~~~java
try {
    export();
} catch (IOException | SQLException ex) {
    markFailed(ex);
    return false;
}
~~~

Đây là trích đoạn, không phải chương trình đầy đủ. Điều kiện: `export()` có khả năng ném hai loại checked, `markFailed` nhận tham số chung như `Exception`/`Throwable` và được thiết kế dùng chung policy.

### Bài B — Phát hiện code không hợp lệ

~~~java
// SAI: hai alternative có quan hệ cha–con.
catch (java.io.FileNotFoundException | java.io.IOException ex) {
    return "failed";
}
~~~

Sửa: nếu tất cả IOException cùng policy, dùng `catch (IOException ex)`; nếu muốn xử lý riêng FileNotFoundException, tách một catch hẹp **đứng trước** catch IOException và dùng chính sách khác. Không thể đặt hai alternative này trong cùng multi-catch.

### Bài C — Giữ nguyên checked exception cho caller

Từ hai catch có `throw ex` riêng, gộp:

~~~java
static void load() throws IOException, SQLException {
    try {
        readFromSources();
    } catch (IOException | SQLException ex) {
        throw ex;
    }
}
~~~

Đoạn này cần `readFromSources()` được định nghĩa thích hợp. Kiểm tra compiler chấp nhận `throws IOException, SQLException` và test caller vẫn bắt đúng từng subtype.

### Bài D — Không gộp khi chính sách khác

~~~java
catch (IOException ex) {
    useCachedValue();
} catch (SQLException ex) {
    rollbackTransaction();
    throw new StorageFailure("db failed", ex);
}
~~~

Đáp án: **giữ hai nhánh riêng**. Việc cố rút thành một multi-catch sẽ khiến handler phải dùng `instanceof` hoặc làm sai outcome. Có thể trích helper dùng chung nếu thực sự có thao tác giống nhau, nhưng không đánh mất phần khác.

### Bài E — Viết test cho lỗi thứ ba

Tạo fake `fetch` có thể ném `IllegalStateException` bên cạnh IOException/SQLException. Kiểm tra multi-catch không nuốt `IllegalStateException`. Nếu refactor vô tình chuyển sang `catch(Exception)`, test phải phát hiện lỗi thay đổi hợp đồng.

## 21. Checklist trước khi merge thay đổi

- Hai hoặc nhiều catch cũ xử lý **cùng policy**, không chỉ cùng câu log?
- Các exception là alternative hợp lệ, không có quan hệ subtype giữa chúng?
- Những checked exception đó thực sự có thể phát sinh từ `try`?
- Biến `ex` có cần method riêng của từng subtype không?
- Thứ tự các handler hẹp/rộng giữ nguyên?
- `throw ex` hay wrap có giữ đúng checked-exception contract và `cause`?
- Có xử lý đặc biệt cho `InterruptedException`, rollback và cancellation?
- Có log trùng hoặc thay đổi severity/error code ngoài ý muốn?
- Fallback value, return/throw, trạng thái tài nguyên và side effect có giống trước?
- Test bao phủ đường success và **từng** alternative?
- Test có chứng minh lỗi ngoài tập alternative không bị nuốt?
- Code sau refactor có dễ hiểu hơn hai nhánh cũ không?

## 22. Tổng kết

**Multi-catch** trong Java cho phép dùng một thân handler cho nhiều kiểu exception bằng `catch (A | B ex)`. Đây là lựa chọn tốt khi các exception **khác kiểu nhưng thật sự có cùng cách xử lý**. Nó loại trùng lặp và làm rõ tập lỗi được chủ động bắt, trong khi exception runtime vẫn giữ kiểu/cause riêng.

Khi thực hành, hãy giữ năm nguyên tắc: **kiểm tra chính sách**, **tuân quy tắc subtype và thứ tự catch**, **không gán lại tham số multi-catch**, **bảo toàn cause/precise rethrow**, và **test từng loại lỗi trước–sau**. Nếu policies khác nhau hoặc thân handler phải phân nhánh theo kiểu liên tục, các catch riêng thường đúng và dễ bảo trì hơn.

### Tài liệu tham khảo

- [Java Language Specification §14.20 — The try statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20)
- [Java Language Specification §11 — Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html)
- [Oracle — Catching Multiple Exception Types and Rethrowing Exceptions](https://docs.oracle.com/javase/8/docs/technotes/guides/language/catch-multiple.html)
- [Java SE 17 — Throwable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html)
- [Java SE 17 — IOException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/IOException.html)
- [Java SE 17 — SQLException](https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/SQLException.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-practice-004">Tái cấu trúc cleanup thủ công sang try-with-resources</a>

<details>
<summary>Xem chi tiết</summary>

# Tái cấu trúc cleanup thủ công sang try-with-resources

## 1. Mục tiêu của bài thực hành

Khi đọc code Java cũ, chúng ta thường thấy tài nguyên được mở, sử dụng rồi đóng bằng lệnh **close()** viết thủ công. Đoạn code đó có thể hoạt động tốt trên đường thành công, nhưng lỗi xuất hiện giữa chừng có thể khiến lệnh đóng không chạy. Việc chuyển sang **try-with-resources** (TWR) không chỉ là đổi cú pháp; đó là quá trình **xác lập quyền sở hữu tài nguyên, bao phủ mọi đường kết thúc và bảo toàn thông tin exception**.

Bài thực hành này hướng dẫn **tái cấu trúc cleanup thủ công sang try-with-resources** trong Java Core, với các mục tiêu:

1. Xác định chính xác resource nào cần đóng và ai sở hữu resource đó.
2. Nhận diện các phản mẫu: đóng cuối phương thức, đóng trong finally sai phạm vi, đóng nhiều tài nguyên nối tiếp, bỏ qua lỗi khi khởi tạo tài nguyên sau.
3. Đưa các resource **AutoCloseable** vào phạm vi TWR thích hợp mà không đóng sớm hoặc đóng trùng.
4. Hiểu thứ tự close, cách xử lý return và các dạng exception trên đường thành công/thất bại.
5. Giữ lỗi tác vụ làm **primary**, còn lỗi cleanup làm **suppressed** nếu cả hai xảy ra.
6. Thực hiện migration an toàn với các API có checked exceptions, tài nguyên được khai báo trước Java 9+, wrapper và callback.
7. Kiểm thử lại hành vi, trạng thái và quyền sở hữu chứ không chỉ xem code đã biên dịch.

**Định nghĩa ngắn:** Với TWR, Java tự gọi **close()** trên các resource đã mở thành công khi rời phạm vi try. Khi phần thân đã ném Throwable và close tiếp tục thất bại, lỗi close được ghi vào **getSuppressed()** của lỗi chính, miễn là Throwable hỗ trợ suppression.

Không nên suy ra TWR tự thực hiện rollback database, hủy I/O bên ngoài hoặc làm sạch mọi side effect. TWR giải quyết **vòng đời đóng tài nguyên**; tính nguyên tử dữ liệu là một bảo đảm riêng.

## 2. Đọc phản mẫu trước khi sửa

Ví dụ dạng code cũ:

~~~java
// Mã minh họa; cần khai báo các import và API tạo reader phù hợp.
BufferedReader reader = Files.newBufferedReader(path);
String first = reader.readLine();
reader.close();
return first;
~~~

Nếu **readLine()** ném **IOException** hoặc một RuntimeException, lệnh **reader.close()** không chạy. Đây là lỗi **chỉ cleanup ở happy path**.

Một phiên bản có finally:

~~~java
// Mã minh họa theo ngữ cảnh.
BufferedReader reader = Files.newBufferedReader(path);
try {
    return reader.readLine();
} finally {
    reader.close();
}
~~~

Phiên bản này thử close kể cả khi thân try ném exception hoặc return. Nhưng hai điểm vẫn cần xét:

- Nếu **reader.close()** ném exception, nó có thể che exception đọc đã xảy ra trong try.
- Nếu một resource khác được mở trước reader và lỗi xảy ra trước khi đi vào try, phần finally có thể chưa bảo vệ được resource trước đó.

Vì vậy, chuyển sang TWR phải giữ những yêu cầu thực tế của API: đảm bảo đóng đúng resource, đúng phạm vi, đúng thời điểm và truyền đủ lỗi cho caller.

Cách viết hướng tới:

~~~java
// Mã minh họa với imports BufferedReader, Files, Path, IOException.
try (BufferedReader reader = Files.newBufferedReader(path)) {
    return reader.readLine();
}
~~~

Nếu reader mở thành công, TWR chịu trách nhiệm gọi close khi thân try kết thúc bất kể thành công, exception hay return. Nếu mở reader thất bại trước khi có object hợp lệ, không có reader đó để đóng; nếu đã mở những resource TWR trước đó, chúng vẫn được cleanup.

## 3. Trước hết phải xác định ownership

**Ownership** là trách nhiệm quản lý vòng đời: ai được quyền đóng tài nguyên, ai phải đảm bảo close và khi nào quyền đó chuyển sang người khác.

Câu hỏi cần trả lời khi refactor:

| Trường hợp | Ai thường chịu trách nhiệm đóng? | Lưu ý |
| --- | --- | --- |
| Phương thức tự mở file để đọc và chỉ trả dữ liệu | Chính phương thức | TWR ngay trong phương thức thường phù hợp |
| Phương thức nhận InputStream do caller truyền vào | Thường caller, tùy hợp đồng | Không tự đóng bằng TWR nếu không sở hữu |
| Factory trả một InputStream còn mở | Caller nhận ownership | Không TWR quanh resource sẽ trả về |
| Wrapper stream tự đóng delegate khi close | Chủ sở hữu wrapper theo hợp đồng | Tránh đóng trùng delegate không cần thiết |
| Connection do framework quản lý transaction cấp | Framework/transaction boundary | TWR sai phạm vi có thể phá transaction |
| ResultSet/Statement được tạo trong một block ngắn | Phụ thuộc JDBC API và tầng sở hữu | Xác định phạm vi và thứ tự đóng |
| Resource được cache dùng qua nhiều request | Thành phần quản lý cache/application | Không đóng sau mỗi request nếu vẫn được dùng chung |

Nếu mở trong hàm rồi trả về chính resource:

~~~java
// Phản mẫu: phương thức trả về resource đã bị đóng.
InputStream createStream() throws IOException {
    try (InputStream input = Files.newInputStream(path)) {
        return input;
    }
}
~~~

TWR sẽ gọi close **trước khi caller nhận kết quả**. Đây là sai nếu caller muốn tiếp tục đọc stream. Cách đúng là trả stream còn mở và **ghi rõ caller chịu trách nhiệm close**, hoặc đổi API để trả dữ liệu đã đọc hoàn chỉnh trong phạm vi đóng của method.

TWR phù hợp nhất khi **vòng đời resource trùng với phạm vi lexical của try**. Nếu vòng đời dài hơn, đừng ép vào một block quá ngắn.

## 4. Quy trình chuyển đổi an toàn theo từng bước

Một quy trình review/refactor có hệ thống:

**Bước 1 — Đọc đường mở resource.** Tìm chỗ tạo FileInputStream, BufferedReader, Connection, Statement, Socket, ZipFile, Stream hoặc custom AutoCloseable. Nếu mở nhiều resource, ghi thứ tự và điểm từng lần acquire thành công.

**Bước 2 — Phân tích ownership.** Xác định resource hiện thuộc phương thức hay được mượn/chuyển giao. Không “sửa rò rỉ” bằng cách đóng resource của caller trái hợp đồng.

**Bước 3 — Tìm mọi đường thoát.** Ngoại lệ checked, RuntimeException, Error, return sớm, break, continue, các nhánh điều kiện và lỗi khi mở tiếp resource khác.

**Bước 4 — Kiểm tra API close.** Resource có implement AutoCloseable/Closeable không? Close ném checked exception nào? Có đóng delegate bên dưới không? Có yêu cầu đóng ngược thứ tự không?

**Bước 5 — Chọn phạm vi TWR.** Đưa acquisition vào dấu ngoặc của try nếu có thể. Block nên bao toàn bộ thời gian resource được sử dụng nhưng không kéo dài quá lifetime cần thiết.

**Bước 6 — Di chuyển phần xử lý chính.** Giữ logic nghiệp vụ trong thân try, không vô tình gọi lại công việc hoặc thay thứ tự các side effect.

**Bước 7 — Xóa manual close dư.** Khi TWR đã sở hữu resource, bỏ lệnh close cũ tránh đóng hai lần.

**Bước 8 — Giữ hợp đồng exception.** So sánh exception mà caller nhận khi thân try lỗi, close lỗi, cả hai cùng lỗi. Nếu code cũ có bug che primary, cần chủ động cập nhật test theo hành vi đúng mong muốn, không cố bảo tồn bug.

**Bước 9 — Kiểm thử fault injection.** Cho open/work/close lần lượt thất bại; đo đúng số lần close, thứ tự, primary/suppressed và kết quả nghiệp vụ.

**Bước 10 — Xem tác động vận hành.** Theo dõi connection leak, file descriptor, log Throwable đầy đủ, trạng thái transaction và hiệu năng trên các đường dùng thật.

Một refactor được xem là hoàn chỉnh khi **behavioral contract** rõ ràng và test hồi quy PASS; không chỉ khi IDE không còn cảnh báo “resource leak”.

## 5. Phân biệt AutoCloseable và Closeable

Java dùng hai interface liên quan:

~~~java
// Chữ ký khái niệm; đặt trong package chuẩn của JDK.
interface AutoCloseable {
    void close() throws Exception;
}

interface Closeable extends AutoCloseable {
    void close() throws IOException;
}
~~~

**AutoCloseable** là hợp đồng tổng quát cho resource cleanup. **Closeable** thuộc java.io, thu hẹp kiểu checked exception thành IOException. Hai chữ ký trên chỉ minh họa contract, không phải mã dùng để khai báo lại những interface chuẩn.

Nếu biến có kiểu tĩnh **AutoCloseable**, compiler có thể yêu cầu xử lý **Exception** từ close. Nếu biến có kiểu **BufferedReader** hay **InputStream**, close thường được khai báo với IOException. Khi chuyển sang TWR, **đừng bất ngờ mở rộng throws Exception** trên một API public chỉ vì đã đổi kiểu biến sang AutoCloseable quá rộng.

Ví dụ:

~~~java
// Mã minh họa bên trong một phương thức throws IOException.
try (BufferedReader reader = Files.newBufferedReader(path)) {
    return reader.readLine();
}
~~~

Kiểu cụ thể được giữ, nên checked exception có thể được khai báo/điều phối phù hợp với IOException. Trong trường hợp custom resource, nên chọn chữ ký close chặt nhất phản ánh lỗi có thể xảy ra.

**Lưu ý:** Không phải close nào cũng idempotent. AutoCloseable không hứa việc gọi close nhiều lần trên mọi implementation sẽ vô hại. Vì vậy, sau khi chuyển ownership sang TWR, bỏ cleanup cũ thực sự là một bước correctness, không chỉ làm code gọn.

## 6. Ví dụ runnable Java 17+: so sánh before và after

Để không phụ thuộc database hoặc file thật, chương trình sau tạo **Tracked implements AutoCloseable**. Constructor in tên tài nguyên lúc mở; **work()** có thể ném IOException; **close()** in tên khi đóng và cũng có thể ném IOException.

Chương trình gồm:

1. **oldMissingFailureBranch** — resource được mở nhưng không đóng nếu work ném.
2. **oldFinallyMasksWork** — finally đóng resource nhưng che exception công việc.
3. **refactoredWorkFailure** — TWR giữ primary và suppressed.
4. **twoResources** — đóng ngược thứ tự và thử đóng cả hai dù close đều lỗi.
5. **initializationFailure** — resource thứ nhất được đóng nếu mở resource tiếp theo lỗi.
6. **returnWithCleanup** — close chạy trước khi return hoàn tất.
7. **java9ExistingVariable** — từ Java 9, biến local effectively final có thể xuất hiện trong resource specification.

Lưu thành **ResourceRefactorDemo.java**:

~~~java
import java.io.IOException;

public class ResourceRefactorDemo {
    static final class Tracked implements AutoCloseable {
        private final String name;
        private final boolean failClose;
        private boolean closed;

        Tracked(String name, boolean failClose) {
            this.name = name;
            this.failClose = failClose;
            System.out.println("open " + name);
        }

        void work(boolean fail) throws IOException {
            System.out.println("work " + name);
            if (fail) {
                throw new IOException("work " + name + " failed");
            }
        }

        boolean isClosed() {
            return closed;
        }

        @Override
        public void close() throws IOException {
            System.out.println("close " + name);
            closed = true;
            if (failClose) {
                throw new IOException("close " + name + " failed");
            }
        }
    }

    static void oldMissingFailureBranch() throws IOException {
        Tracked resource = new Tracked("legacy", false);
        resource.work(true);
        resource.close();
    }

    static void oldFinallyMasksWork() throws IOException {
        Tracked resource = new Tracked("finally", true);
        try {
            resource.work(true);
        } finally {
            resource.close();
        }
    }

    static void refactoredWorkFailure() throws IOException {
        try (Tracked resource = new Tracked("safe", true)) {
            resource.work(true);
        }
    }

    static void twoResources() throws IOException {
        try (Tracked first = new Tracked("first", true);
             Tracked second = new Tracked("second", true)) {
            throw new IOException("body failed");
        }
    }

    static Tracked openSecondAndFail() throws IOException {
        System.out.println("opening second failed");
        throw new IOException("second open failed");
    }

    static void initializationFailure() throws IOException {
        try (Tracked first = new Tracked("prepared", true);
             Tracked second = openSecondAndFail()) {
            System.out.println("unreachable");
        }
    }

    static String returnWithCleanup() throws IOException {
        try (Tracked resource = new Tracked("return", false)) {
            System.out.println("prepare return");
            return "result";
        }
    }

    static void java9ExistingVariable() throws IOException {
        Tracked existing = new Tracked("existing", false);
        try (existing) {
            existing.work(false);
        }
        System.out.println("existing closed=" + existing.isClosed());
    }

    static void show(IOException problem) {
        System.out.println("primary: " + problem.getMessage());
        for (Throwable secondary : problem.getSuppressed()) {
            System.out.println("suppressed: " + secondary.getMessage());
        }
    }

    public static void main(String[] args) throws IOException {
        System.out.println("== Missing failure branch ==");
        try {
            oldMissingFailureBranch();
        } catch (IOException problem) {
            show(problem);
        }

        System.out.println("== Finally masks original ==");
        try {
            oldFinallyMasksWork();
        } catch (IOException problem) {
            show(problem);
        }

        System.out.println("== Refactored ==");
        try {
            refactoredWorkFailure();
        } catch (IOException problem) {
            show(problem);
        }

        System.out.println("== Reverse closing order ==");
        try {
            twoResources();
        } catch (IOException problem) {
            show(problem);
        }

        System.out.println("== Failure during acquisition ==");
        try {
            initializationFailure();
        } catch (IOException problem) {
            show(problem);
        }

        System.out.println("== Early return ==");
        System.out.println("returned: " + returnWithCleanup());

        System.out.println("== Java 9 existing variable ==");
        java9ExistingVariable();
    }
}
~~~

Biên dịch và chạy JDK 17 hoặc mới hơn:

~~~bash
javac --release 17 ResourceRefactorDemo.java
java ResourceRefactorDemo
~~~

Stdout đã được kiểm chứng khi biên dịch và chạy:

~~~text
== Missing failure branch ==
open legacy
work legacy
primary: work legacy failed
== Finally masks original ==
open finally
work finally
close finally
primary: close finally failed
== Refactored ==
open safe
work safe
close safe
primary: work safe failed
suppressed: close safe failed
== Reverse closing order ==
open first
open second
close second
close first
primary: body failed
suppressed: close second failed
suppressed: close first failed
== Failure during acquisition ==
open prepared
opening second failed
close prepared
primary: second open failed
suppressed: close prepared failed
== Early return ==
open return
prepare return
close return
returned: result
== Java 9 existing variable ==
open existing
work existing
close existing
existing closed=true
~~~

**Đọc kết quả theo chuỗi before → after:** Tài nguyên **legacy** không in close vì work ném trước lệnh close thủ công. Tài nguyên **finally** có in close, nhưng người gọi chỉ nhận **close finally failed** — lỗi work bị che. Khi thay bằng **safe** trong TWR, chương trình in lỗi chính **work safe failed** và suppressed **close safe failed**. Đó là cải tiến quan trọng về **thông tin lỗi và vòng đời**, không chỉ giảm số dòng code.

Ví dụ mô phỏng resource đơn giản để có stdout ổn định. Trong ứng dụng thật, close có thể flush, phát sinh I/O, giải phóng lock hoặc gửi lệnh tới server; phải xác minh thêm hợp đồng loại tài nguyên đó.

## 7. Đọc lỗi chính và suppressed sau refactor

TWR giữ ý nghĩa Throwable theo những tình huống:

| Phần thân | Close | Kết quả quan sát |
| --- | --- | --- |
| Thành công | Thành công | Hoàn tất bình thường |
| Ném A | Thành công | A được truyền ra |
| Thành công | Ném B | B có thể trở thành lỗi chính |
| Ném A | Ném B | A là primary, B được thêm vào A.getSuppressed() |
| Return giá trị | Thành công | Close chạy trước khi return hoàn tất |
| Return giá trị | Ném B | Không hoàn tất return bình thường; B truyền ra |
| Mở resource thứ hai ném C | Đóng resource đầu thành công | C truyền ra |
| Mở resource thứ hai ném C | Đóng resource đầu ném D | C primary, D suppressed |

**getCause() và getSuppressed() khác nhau.** Cause mô tả quan hệ lỗi là nguyên nhân của wrapper; suppressed mô tả lỗi phụ xảy ra khi thao tác khác đang thất bại.

Ví dụ:

~~~java
// Mã minh họa trong catch hợp lệ.
catch (IOException primary) {
    System.out.println(primary.getMessage());
    for (Throwable extra : primary.getSuppressed()) {
        System.out.println(extra.getMessage());
    }
}
~~~

Trong ví dụ runnable, **show(IOException)** làm đúng điều này. Nếu một tầng service tạo **new IllegalStateException("read failed", primary)**, suppressed vẫn nằm trên **primary** ở **wrapper.getCause().getSuppressed()**, không tự động xuất hiện trên **wrapper.getSuppressed()**. Vì vậy logger cần hiển thị **toàn bộ cây Throwable**, không chỉ outer message.

Một số Throwable custom có thể tắt suppression bằng constructor chuyên dụng. Khi suppression không được bật, addSuppressed không lưu thông tin; trong trường hợp đó không thể hứa rằng mọi lỗi close được giữ trong getSuppressed. Với exception JDK thông dụng như IOException, cơ chế trên hoạt động như dự kiến.

## 8. Vì sao đặt acquisition trong resource specification?

Phản mẫu khi mở hai tài nguyên:

~~~java
// Phản mẫu theo ngữ cảnh.
Resource a = openA();
Resource b = openB(); // Nếu lỗi, chưa vào try.
try {
    work(a, b);
} finally {
    b.close();
    a.close();
}
~~~

Nếu openB ném exception, a đã mở nhưng finally không hề bắt đầu, nên a bị bỏ quên.

Mẫu tốt hơn:

~~~java
// Minh họa; Resource implements AutoCloseable.
try (Resource a = openA();
     Resource b = openB()) {
    work(a, b);
}
~~~

Java quản lý từng resource đã được mở thành công. Nếu **openB()** thất bại, a được thử đóng. Nếu đóng a cũng thất bại, lỗi cleanup được suppressed vào lỗi khởi tạo B theo cơ chế TWR.

**Một ranh giới quan trọng:** Nếu **openB()** cấp phát tài nguyên nội bộ rồi thất bại trước khi trả về object B, TWR bên ngoài không thể đóng một object chưa được giao. **Factory/constructor** của B phải tự xử lý exception safety cho phần sở hữu nội bộ của nó. TWR không chữa được mọi kiểu resource leak bên trong thư viện.

## 9. Thứ tự đóng nhiều resource và phụ thuộc vòng đời

Trong resource specification:

~~~java
// Minh họa theo ngữ cảnh.
try (Resource first = openFirst();
     Resource second = openSecond(first)) {
    use(second);
}
~~~

Java đóng **second trước first**. Trong ví dụ runnable, stdout cho thấy **close second**, sau đó **close first** dù cả hai close đều ném IOException.

Đây là thiết kế hợp lý khi second phụ thuộc first, nhưng vẫn cần kiểm tra **ownership**. Một wrapper có thể tự đóng delegate trong close; nếu bạn khai báo wrapper và delegate như hai resource độc lập, delegate có thể bị close hai lần. AutoCloseable không bảo đảm mọi close có thể lặp vô hại.

Ví dụ với BufferedReader quấn quanh một Reader:

~~~java
// Minh họa đọc file.
try (BufferedReader buffered =
         new BufferedReader(Files.newBufferedReader(path))) {
    process(buffered);
}
~~~

Trong trường hợp này, close của BufferedReader đóng reader bên dưới theo hợp đồng. Không cần khai báo thêm một biến Reader độc lập chỉ để đóng lại, trừ khi có yêu cầu ownership cụ thể. Nếu tách resource thành nhiều lớp, đảm bảo thứ tự đóng không phá phụ thuộc của wrapper và tránh close trùng.

## 10. Java 9+: sử dụng biến local final hoặc effectively final

Trong Java 7/8, cú pháp resource specification phổ biến là **try (Type variable = expression)**. Từ Java 9, có thể sử dụng một **biến local hoặc tham số kiểu final/effectively final** đã tồn tại:

~~~java
// Mã minh họa Java 9+.
BufferedReader reader = Files.newBufferedReader(path);
try (reader) {
    System.out.println(reader.readLine());
}
~~~

Cú pháp này chỉ hợp lệ khi biến phù hợp điều kiện final/effectively final. Nếu biến bị gán lại sau khởi tạo, compiler có thể từ chối. Ví dụ chương trình runnable có:

~~~java
// Đoạn trích.
Tracked existing = new Tracked("existing", false);
try (existing) {
    existing.work(false);
}
~~~

Ở đây **existing** không bị gán lại, nên Java 17 biên dịch được. TWR đóng resource khi ra khỏi try; tham chiếu existing vẫn tồn tại trong scope nhưng object đã được đóng. **Có tham chiếu không đồng nghĩa được phép tiếp tục dùng resource**.

### Cạm bẫy khi biến có sẵn được mở bên ngoài

Nếu code làm nhiều thao tác giữa **open** và **try(existing)**, exception trong khoảng đó vẫn có thể làm resource bị bỏ quên:

~~~java
// Phản mẫu theo ngữ cảnh.
Resource existing = open();
prepareOtherState(); // Có thể ném trước try.
try (existing) {
    use(existing);
}
~~~

Khi thực hiện refactor, nên **đưa phạm vi sở hữu bắt đầu càng sát lúc acquisition thành công càng tốt**. Biến có sẵn trong Java 9+ là tiện ích cú pháp, **không bảo đảm cleanup cho những bước thực thi trước khi vào try**.

Một điều khác: Không đặt resource do caller truyền vào trong **try (parameter)** nếu method **không sở hữu** resource đó. Java vẫn gọi close; cú pháp hợp lệ không chứng minh quyết định ownership đúng.

## 11. Refactor với nhiều phương thức return và nhánh điều kiện

Cleanup ở cuối phương thức dễ bị bỏ qua bởi early return:

~~~java
// Phản mẫu theo ngữ cảnh.
Resource resource = openResource();
if (shouldSkip()) {
    return;
}
perform(resource);
resource.close();
~~~

Chuyển thành:

~~~java
// Minh họa.
try (Resource resource = openResource()) {
    if (shouldSkip()) {
        return;
    }
    perform(resource);
}
~~~

TWR đóng resource trước khi phương thức hoàn tất. **return trong thân try** không đồng nghĩa bỏ cleanup. Nhưng **return trong finally** lại là phản mẫu vì có thể che exception/kết quả đang chờ.

Với **break** hoặc **continue** từ một vòng lặp, khi điều khiển rời phạm vi TWR, close cũng được thực hiện. Cần chọn vị trí block theo vòng đời thực tế: resource sống qua toàn bộ vòng lặp hay mỗi iteration mở một resource mới? Nếu cần stream/connection sống qua nhiều iteration, đặt TWR bao quanh vòng lặp; nếu mỗi iteration dùng resource độc lập, đặt TWR bên trong vòng lặp.

Ví dụ minh họa hai chiến lược:

~~~java
// Một resource dùng cho toàn bộ vòng lặp.
try (Resource shared = openResource()) {
    for (Item item : items) {
        process(shared, item);
    }
}

// Resource riêng cho từng item.
for (Item item : items) {
    try (Resource perItem = openResource(item)) {
        process(perItem, item);
    }
}
~~~

Hai đoạn chỉ minh họa thiết kế scope; Resource, Item và các hàm cần được định nghĩa trong project. Cách thứ hai tạo nhiều lần open/close và có thể tốn hơn hoặc đúng hơn tùy contract. Không đổi phạm vi chỉ vì cú pháp dễ viết.

## 12. Khi refactor API không implements AutoCloseable

Không phải mọi tài nguyên đều có close chuẩn. Lock có **unlock()**; một API native có **release(handle)**; transaction có **commit()/rollback()**. Khi đó có thể:

1. Giữ cleanup thủ công với finally đúng phạm vi và chiến lược bảo toàn lỗi.
2. Tạo **adapter AutoCloseable** nếu semantics release phù hợp và ownership rõ ràng.
3. Dùng higher-order helper để bao vòng đời nếu API có những bước commit/rollback đặc biệt.

Ví dụ adapter:

~~~java
// Minh họa theo ngữ cảnh; handle đã acquire thành công.
try (AutoCloseable scope = () -> handle.release()) {
    use(handle);
}
~~~

Do **AutoCloseable.close() throws Exception**, phương thức chứa đoạn này phải khai báo/bắt checked exception phù hợp. Adapter cũng cần xét liệu release có idempotent, release thất bại thì còn trạng thái gì và có cần chuyển ownership sau đó không. Đừng viết adapter chỉ để “đạt chuẩn TWR” nếu nó làm mất semantics transaction.

Một ví dụ với Lock:

~~~java
// Minh họa với java.util.concurrent.locks.Lock.
lock.lock();
try {
    performOperation();
} finally {
    lock.unlock();
}
~~~

Đây vẫn là một cấu trúc phù hợp vì Lock không trực tiếp là AutoCloseable. Đặt lock.lock() trước try bảo đảm unlock chỉ chạy khi việc lấy khóa hoàn tất bình thường. Với **lock.lockInterruptibly()**, nếu InterruptedException xảy ra trước khi nhận khóa, code không được cố unlock. Refactor phải tôn trọng ranh giới acquire thành công.

## 13. Khi chuyển đổi làm thay đổi checked exceptions

Một method cũ có thể không khai báo Exception rộng vì close không được xử lý chính xác hoặc tài nguyên có kiểu cụ thể. Sau refactor, compiler sẽ kiểm tra những checked exceptions mà **close()** trong resource specification có thể ném.

Ví dụ:

~~~java
// Mã minh họa:
static String read(Path path) throws IOException {
    try (BufferedReader reader = Files.newBufferedReader(path)) {
        return reader.readLine();
    }
}
~~~

BufferedReader.close có thể ném IOException, nên chữ ký throws IOException phù hợp.

Nếu bạn thay kiểu khai báo bằng **AutoCloseable**:

~~~java
// Minh họa; cần xử lý checked Exception từ AutoCloseable.close().
static void process(AutoCloseable resource) throws Exception {
    try (resource) {
        performOperation();
    }
}
~~~

Không nên tùy tiện đổi mọi public method thành **throws Exception** chỉ để biên dịch. Kiểm tra kiểu tĩnh cụ thể, overload API, constructor của wrapper và chính sách exception hiện hữu. Có thể sử dụng resource wrapper có **close() throws IOException** hoặc một exception được định nghĩa rõ hơn nếu đúng semantics.

**Quan trọng:** TWR có thể làm một exception **mà code cũ đang vô tình bỏ qua** trở nên quan sát được, chẳng hạn lỗi close sau khi thân work đã thành công. Đây là thay đổi hành vi cần review theo contract. Không nên catch rồi nuốt tất cả lỗi close chỉ để bảo toàn một hành vi sai trước đó.

## 14. Dọn dẹp và transaction là hai bài toán khác nhau

Refactor close của database Connection bằng TWR không thay thế cho chính sách transaction. Trong JDBC:

- **Connection.close()** liên quan vòng đời connection/handle theo API hoặc pool.
- **commit()** xác nhận transaction.
- **rollback()** thử khôi phục thay đổi chưa commit.
- **setAutoCommit(false)** điều khiển cách JDBC kết thúc transaction theo hợp đồng.
- **close() thất bại** có thể đòi đánh dấu handle không đáng tin, tùy provider/pool.

Mẫu minh họa cho transaction do chính phương thức sở hữu:

~~~java
// Mã minh họa trong phương thức khai báo throws SQLException.
try (Connection connection = dataSource.getConnection()) {
    connection.setAutoCommit(false);
    try {
        performSql(connection);
        connection.commit();
    } catch (SQLException workFailure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            workFailure.addSuppressed(rollbackFailure);
        }
        throw workFailure;
    }
}
~~~

Đây là snippet theo ngữ cảnh, không phải một chương trình Java độc lập. Nó chỉ xử lý rollback trong nhánh SQLException bên trong; RuntimeException hoặc Error từ performSql cần được xét riêng nếu contract yêu cầu rollback trong những trường hợp đó. Nếu **setAutoCommit(false)** thất bại trước inner try, nhánh rollback này không chạy dù connection vẫn được TWR thử close.

**Kết luận của mục này:** TWR quản lý close; muốn bảo toàn dữ liệu cần chính sách commit/rollback/compensation riêng. Không dùng refactor tài nguyên để đưa ra lời hứa “mọi lỗi đều không làm thay đổi trạng thái”.

## 15. Xử lý interruption và cancellation trong cleanup

Nếu một luồng đang đợi công việc và ném **InterruptedException**, nó có thể rời thân try sớm. Nếu resource thuộc TWR, Java vẫn chạy close trên đường thoát đó.

~~~java
// Mã minh họa: resource implements AutoCloseable, waitForData
// có thể ném InterruptedException và method có hợp đồng thích hợp.
try (Resource resource = openResource()) {
    waitForData(resource);
}
~~~

Cần phân biệt:

- **InterruptedException**: tín hiệu thao tác chờ của thread bị interrupt.
- **CancellationException**: trạng thái hủy của Future/kết quả.
- **TimeoutException**: một lần chờ quá thời hạn, không tự đóng mọi resource của worker.
- **IOException từ close**: có thể là lỗi phụ khi đường interrupt đã tạo primary exception, hoặc là lỗi chính khi thân try hoàn tất.

TWR không tự cancel một task đang chạy trong thread khác. Nếu caller timeout và đóng resource trong khi worker vẫn sử dụng nó, có thể phát sinh race và lỗi mới. Refactor phải giữ đúng **ownership giữa các thread**.

Khi một phương thức cho phép lan truyền InterruptedException, thường nên truyền lên tầng sở hữu công việc. Nếu không thể truyền checked exception ở Runnable boundary, code có thể cần khôi phục interrupt flag và kết thúc. Chính sách này không được thay bằng catch(Exception) rồi bỏ qua chỉ vì muốn “đảm bảo cleanup”.

## 16. Trường hợp close cũng có side effect

Một số close không chỉ giải phóng bộ nhớ hoặc file descriptor:

- **BufferedWriter.close()** có thể flush dữ liệu được buffer.
- **ZipOutputStream.close()** có thể hoàn tất cấu trúc file nén.
- **Socket.close()** thay đổi khả năng I/O của đầu kết nối.
- **JDBC Connection.close()** có semantics riêng tùy datasource/pool.
- Một custom resource có thể gửi dữ liệu hoặc publish thông tin khi đóng.

Nếu thân try đã trả kết quả nhưng close ném exception, TWR có thể thay kết quả return bằng exception. Điều này thường là chính xác khi hợp đồng coi close là bước bắt buộc để hoàn tất công việc. Nhưng trong thiết kế API, cần xác định rõ **khi nào một thao tác được coi là thành công**.

**Đừng vô điều kiện retry close** sau khi nó ném lỗi: AutoCloseable không bắt buộc mọi implementation hỗ trợ gọi close nhiều lần theo cách idempotent. Hãy đọc contract và xác định trạng thái sau thất bại. Đóng được tài nguyên cũng không có nghĩa byte đã ghi hoặc side effect đã rollback.

Trong code review, tách câu hỏi “close có được thử?” khỏi câu hỏi “thao tác có đạt mục tiêu nghiệp vụ?”. Cả hai phải được xác minh.

## 17. TWR với catch/finally khác vẫn hợp lệ

Một cấu trúc TWR vẫn có thể có catch/finally riêng:

~~~java
// Mã minh họa.
try (Resource resource = openResource()) {
    perform(resource);
} catch (IOException failure) {
    log(failure);
    throw failure;
} finally {
    updateMetrics();
}
~~~

Thứ tự cleanup tự động của resource diễn ra trước khi catch/finally bao ngoài xử lý kết quả lỗi theo cấu trúc này. Catch có thể truy cập primary và suppressed đã được gắn khi close chạy.

Tuy nhiên, **updateMetrics()** trong finally nếu tự ném exception vẫn có thể che lỗi đang được truyền ra. TWR chỉ bảo toàn lỗi từ **close() của các resource trong resource specification** theo cơ chế của nó; nó **không bảo vệ mọi exception khác từ finally do người viết thêm**.

Đừng di chuyển cleanup cũ vào finally bên ngoài TWR nếu không cần. Nếu metrics/logging có thể thất bại, chính sách lỗi của các bước phụ đó cần được xác định: có nên báo lỗi, suppressed, log riêng hay tránh gây hại cho đường lỗi chính?

## 18. Khi có nhiều layer resource và ownership lồng nhau

Ví dụ stream đọc file được bọc qua các layer:

~~~java
// Mã minh họa trong phương thức có imports và throws IOException.
try (InputStream input = Files.newInputStream(path);
     BufferedInputStream buffered = new BufferedInputStream(input);
     DataInputStream data = new DataInputStream(buffered)) {
    return data.readInt();
}
~~~

TWR sẽ đóng **data**, rồi **buffered**, rồi **input**. Nhưng **data.close()** có thể tự đóng buffered, buffered.close() đóng input. Những lần close tiếp theo có thể là redundant. Với nhiều stream JDK, đóng lặp thường được xử lý an toàn theo implementation, nhưng **không suy rộng sang mọi AutoCloseable**.

Một cách gọn hơn khi wrapper sở hữu delegate:

~~~java
// Mã minh họa.
try (DataInputStream data = new DataInputStream(
        new BufferedInputStream(Files.newInputStream(path)))) {
    return data.readInt();
}
~~~

Cách này dựa vào hợp đồng đóng theo chuỗi của wrapper. Nếu constructor của wrapper có thể thất bại sau khi inner resource đã được mở, phải xét exception safety của bước xây wrapper. Khi một factory có khả năng thất bại ở giữa, khai báo riêng resource ở những cấp phù hợp có thể dễ bảo đảm cleanup hơn.

**Không có một quy tắc cơ học “ít dòng hơn luôn an toàn hơn”**. TWR giúp biểu thị sở hữu, nhưng người refactor vẫn cần hiểu close lan truyền qua wrapper như thế nào.

## 19. Khi nào không nên thay toàn bộ manual cleanup bằng TWR?

Một số trường hợp không phù hợp với một block TWR đơn giản:

1. Resource là **borrowed object** mà caller hoặc framework sở hữu.
2. Resource phải tồn tại dài hơn phạm vi phương thức, chẳng hạn trả về stream để caller đọc.
3. Cleanup không phải là close đơn thuần mà cần **commit, rollback hoặc release** theo nhiều trạng thái.
4. Resource được dùng qua các bước bất đồng bộ và kết thúc sau khi method khởi tạo trả về.
5. API close có yêu cầu về thread cụ thể hoặc thứ tự/điều kiện khác TWR.
6. Object không implements AutoCloseable và adapter đơn giản làm sai semantics.
7. Tài nguyên thuộc scope dài hạn như cache, pool hoặc lifecycle của service.

Nếu một resource được tạo trong controller rồi giao cho background worker, đóng nó ngay khi controller return có thể làm worker thất bại. Nếu một connection được transaction manager cấp, đóng trực tiếp có thể phá policy của framework. Các trường hợp này cần thiết kế ownership rõ hơn, không phải chỉ thay cú pháp.

Phần lớn code mở resource để xử lý đồng bộ ngắn hạn vẫn là ứng viên rất tốt cho TWR.

## 20. Regression testing: chứng minh hành vi mới đúng

Chỉ test đường thành công thì refactor có thể trông ổn nhưng vẫn làm mất lỗi khi cleanup thất bại. Cần một resource giả có thể:

- Ghi nhận lần open/close và thứ tự.
- Ném exception trong work.
- Ném exception trong close.
- Tạo lỗi khi mở resource thứ hai.
- Theo dõi trạng thái resource trước và sau return.

Ma trận tối thiểu:

| Acquisition | Work | Close | Assertion quan trọng |
| --- | --- | --- | --- |
| Thành công | Thành công | Thành công | Resource được đóng, kết quả đúng |
| Thành công | Ném A | Thành công | A được truyền, close được gọi |
| Thành công | Thành công | Ném B | B được truyền, không báo thành công giả |
| Thành công | Ném A | Ném B | A primary, B suppressed |
| Mở A thành công, mở B ném C | Không chạy | Close A thành công | C primary, A đã đóng |
| Mở A thành công, mở B ném C | Không chạy | Close A ném D | C primary, D suppressed |
| Mở A và B thành công | Ném C | Close B và A ném D/E | Thứ tự B→A, C primary, suppressed D rồi E |
| Thành công | Return sớm | Thành công | Close trước khi return |
| Thành công | Return sớm | Ném B | Lời gọi ném B thay vì trả kết quả bình thường |
| Thành công | RuntimeException | Thành công | RuntimeException được giữ, close vẫn được thử |

**Một sai lầm của test:** Chỉ viết **assertThrows(IOException.class)** khi cả work lẫn close đều có thể ném IOException. Nếu close che work, test vẫn PASS vì loại IOException không thay đổi. Nên kiểm tra **message do test kiểm soát**, hoặc tốt hơn **object identity của primary**, cùng **getSuppressed()**.

Với JUnit Jupiter có thể sử dụng **assertThrowsExactly**, **assertSame**, **assertEquals** và các asserts về thứ tự event. Đừng cố định số dòng stack trace; số dòng thay đổi theo cách viết source, không phải contract của TWR.

## 21. Test helper để bơm lỗi vào close

Một custom resource phục vụ test có thể có các cờ trạng thái:

~~~java
// Mã minh họa cần đặt trong một class test phù hợp.
final class Probe implements AutoCloseable {
    int closeCalls;
    boolean failClose;

    @Override
    public void close() throws IOException {
        closeCalls++;
        if (failClose) {
            throw new IOException("probe close failed");
        }
    }
}
~~~

Trong test:

~~~java
// Minh họa JUnit; cần static imports JUnit Jupiter tương ứng.
Probe probe = new Probe();
probe.failClose = true;

IOException failure = assertThrowsExactly(IOException.class, () -> {
    try (probe) {
        throw new IOException("work failed");
    }
});

assertEquals("work failed", failure.getMessage());
assertEquals(1, probe.closeCalls);
assertEquals(1, failure.getSuppressed().length);
assertEquals("probe close failed",
             failure.getSuppressed()[0].getMessage());
~~~

Ví dụ này cần JUnit Jupiter trên classpath; nó không phải đoạn Java SE standalone. Cú pháp **try (probe)** hợp lệ từ Java 9 nếu biến local effectively final; việc thay đổi **probe.failClose** không thay đổi reference của biến probe.

Nếu test nhiều tài nguyên, dùng một **List<String> events** để ghi open, work, close. Khi assert, kiểm tra thứ tự sự kiện thay vì chỉ đếm closeCalls. Nếu resource wrapper tự đóng delegate, expectation đếm close phải dựa trên contract cụ thể.

## 22. Những regression về ownership cần kiểm tra

Một migration “đẹp” có thể tạo bug mới:

**Đóng resource của caller:** Hàm nhận InputStream từ caller rồi đặt ngay **try(input)**, khiến stream bị đóng khi caller vẫn muốn sử dụng.

**Đóng trước khi trả resource:** Factory trả resource từ một khối TWR, caller nhận object đã close.

**Đóng double:** TWR tự gọi close nhưng finally cũ vẫn gọi close; custom resource có thể không chịu được lần gọi thứ hai.

**Đóng sai thứ tự:** Resource nền bị đóng trước wrapper còn đang cần flush dữ liệu.

**Mở resource ngoài TWR rồi làm việc trước khi vào try:** Exception trong khoảng đó vẫn gây leak.

**Đổi exception contract vô tình:** Việc TWR bắt đầu truyền lỗi close khiến caller nhận checked exception mới, hoặc đổi lỗi chính so với một bug cũ. Cần document rõ sự thay đổi hợp lý.

**Bỏ rollback tưởng TWR đã làm hết:** Transaction lỗi nhưng dữ liệu có thể vẫn thay đổi dở; phải giữ riêng chính sách transaction.

**Kéo scope dài quá:** Resource giữ file descriptor/connection quá lâu, dù có TWR đúng cú pháp.

**Rút scope ngắn quá:** Resource bị close trước khi async task hoặc lazy stream xử lý xong.

Trước khi merge, hãy xác định **resource lifetime** bằng một ví dụ sử dụng thực tế, không chỉ thông qua khả năng biên dịch.

## 23. Ghi log lỗi sau migration

Một thay đổi dễ thấy là xuất hiện **suppressed exceptions** trong cây lỗi. Ví dụ một IOException từ read được giữ làm primary, trong khi IOException từ close đi theo suppressed. Hãy đảm bảo hệ thống log giữ đầy đủ Throwable:

~~~java
// Mã minh họa với logger hỗ trợ error(String, Throwable).
logger.error("Resource operation failed", failure);
~~~

Nếu chỉ log **failure.getMessage()**, bạn có thể bỏ qua lỗi close hoặc vị trí lỗi gốc. Nếu một service wrap primary thành domain exception, logger phải hiển thị cause chain để còn thấy suppressed trên cause.

Tuy vậy, không nên log ở mọi tầng catch rồi throw lại. Dễ phát sinh nhiều bản log của cùng một sự cố. Ưu tiên giữ Throwable đầy đủ khi truyền lỗi và log một lần tại ranh giới xử lý phù hợp, kèm operation ID, request ID hoặc tên resource không nhạy cảm.

Tránh in đường dẫn nội bộ, token, thông tin xác thực hoặc dữ liệu cá nhân vào message nếu hệ thống log có phạm vi truy cập rộng. Những thông tin này có thể có trong cause/suppressed của resource I/O.

## 24. Checklist cho người review refactor

Trước khi chấp nhận một pull request đổi manual cleanup sang TWR:

1. **Ownership:** Resource thuộc method này hay được caller/framework quản lý?
2. **Acquisition:** Resource được khởi tạo trong resource specification hoặc được bảo vệ ngay sau khi mở thành công?
3. **Lifetime:** TWR đóng đúng lúc, không đóng sớm trước khi caller/worker cần dùng?
4. **Loại API:** Resource implements AutoCloseable/Closeable; chữ ký close và checked exceptions có được xét?
5. **Nhánh thoát:** Exception checked/unchecked, Error, return, break, continue có cleanup đúng?
6. **Lỗi kép:** Work failure vẫn primary và close failure được suppressed?
7. **Thứ tự:** Với nhiều tài nguyên, thứ tự đóng ngược khai báo có phù hợp dependency?
8. **Đóng trùng:** Đã bỏ các finally/close thủ công dư hoặc close trùng delegate?
9. **Khởi tạo thất bại:** Nếu mở resource tiếp theo lỗi, resource đã mở trước có được dọn?
10. **Kết quả:** Nếu close lỗi sau work thành công, caller có thấy thất bại đúng hợp đồng?
11. **Tác dụng phụ:** Transaction/rollback/compensation có còn đầy đủ và tách khỏi close không?
12. **Async:** Resource có đang được dùng bởi một task sống lâu hơn scope TWR không?
13. **Test:** Đã có kịch bản open/work/close failure và kiểm tra primary/suppressed, state, số lần close?
14. **Quan sát:** Logging và monitoring có hiển thị suppressed, cause và không làm mất stack trace không?

Nếu chưa trả lời được câu nào vì hợp đồng API không rõ, đó là tín hiệu cần điều tra trước khi đổi code, không phải lý do vội vàng đặt mọi resource vào một block TWR.

## 25. Các câu hỏi tự kiểm tra

**Hỏi: TWR có luôn đóng resource khi thân try ném exception?**

Có, đối với các resource đã được khởi tạo thành công và thuộc resource specification, theo cơ chế Java thông thường. Nhưng nếu constructor/factory của một resource thất bại trước khi trả object, chính logic bên trong factory phải chịu trách nhiệm giải phóng những thứ nó đã cấp phát nội bộ.

**Hỏi: Nếu body và close cùng ném IOException, caller nhận cái nào?**

Body exception thường là primary; close exception là suppressed khi suppression hoạt động. Không nhầm với finally thủ công có thể khiến close che body.

**Hỏi: Có cần vẫn giữ finally sau khi thay bằng TWR?**

Không cần finally **chỉ để gọi close của chính những resource đã được TWR quản lý**. Có thể vẫn cần finally để cập nhật metric, giải phóng loại resource khác hoặc điều phối nghiệp vụ, nhưng phải xét khả năng phần finally đó tự gây lỗi mới.

**Hỏi: Một resource được mở trước try có dùng try(existing) được không?**

Được trên Java 9+ khi biến final/effectively final. Tuy nhiên, mọi bước thực thi giữa lần mở và lần bắt đầu TWR **không được bảo vệ bởi TWR đó**. Hãy đặt phạm vi gần điểm acquire thành công nhất có thể.

**Hỏi: TWR có tự retry close nếu nó thất bại không?**

Không. Nó gọi close theo quy tắc phạm vi. Retry cleanup là một chính sách riêng và có thể không an toàn nếu close không idempotent.

**Hỏi: Có thể dùng TWR trên Lock hoặc transaction API không?**

Chỉ trực tiếp nếu object implements AutoCloseable với semantics phù hợp; hoặc tạo adapter có kiểm soát. Với Lock/unlock và transaction commit/rollback, không nên dùng một adapter chung khi nó làm mất yêu cầu ownership và phục hồi trạng thái.

**Hỏi: Nếu có hai tài nguyên phụ thuộc nhau, Java đóng theo thứ tự nào?**

Từ resource khai báo cuối sang đầu. Hãy sắp khai báo theo dependency và tránh đóng lặp delegate do wrapper tự sở hữu.

**Hỏi: TWR có giúp operation atomic không?**

Không. Nó bảo đảm cleanup theo phạm vi, nhưng không hoàn tác dữ liệu đã ghi, request đã gửi hoặc trạng thái mutable đã sửa. Cần transaction, idempotency hoặc compensation riêng cho side effect.

## 26. Kết luận thực hành

**Tái cấu trúc cleanup thủ công sang try-with-resources** là cải tiến về **correctness, khả năng bảo trì và thông tin lỗi**. Nó giảm các đường thất bại bị bỏ quên, đóng những resource đã mở thành công, xử lý close ngược thứ tự và giúp giữ lỗi công việc làm primary khi cleanup tiếp tục lỗi.

Nhưng một refactor tốt không chỉ thay **try/finally** bằng dấu ngoặc **try (...)**. Nó phải bảo toàn **quyền sở hữu**, không đóng sớm hoặc đóng trùng, hiểu **checked exceptions**, và có test cho acquisition failure, work failure, close failure, return sớm và lỗi kép. Khi resource gắn với transaction hoặc async, còn phải giữ nguyên contract nghiệp vụ và vòng đời bên ngoài phạm vi method.

**Quy tắc nhớ nhanh:** Nếu method sở hữu một **AutoCloseable** trong một phạm vi ngắn, hãy ưu tiên TWR. Nếu ownership dài hạn, chia sẻ hoặc cleanup mang semantics khác close, hãy thiết kế vòng đời riêng và kiểm thử đầy đủ. Trong cả hai trường hợp, đừng chỉ kiểm tra resource được đóng trên đường thành công — hãy chứng minh tài nguyên và exception được xử lý đúng cả khi mọi thứ thất bại.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-practice-005">Tạo ngoại lệ nghiệp vụ có cause và dữ liệu ngữ cảnh</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo ngoại lệ nghiệp vụ có `cause` và dữ liệu ngữ cảnh trong Java

Khi tầng lưu trữ ném `IOException`, tầng nghiệp vụ có thể cần thông báo cho caller rằng thao tác **“không thể tải hồ sơ”** đã thất bại. Nếu chỉ ném `new RuntimeException("lỗi")`, người đọc mất nguyên nhân kỹ thuật và khó xác định request hoặc đối tượng liên quan. Nếu chỉ truyền nguyên `IOException`, API nghiệp vụ lại buộc caller hiểu chi tiết của kho lưu trữ.

Giải pháp là thiết kế **ngoại lệ nghiệp vụ (business/domain exception)** có hợp đồng rõ ràng: một **mã lỗi ổn định**, một thông điệp mô tả, một **`cause` giữ nguyên lỗi gốc** khi đang bọc lỗi, và **dữ liệu ngữ cảnh** có chọn lọc để hỗ trợ chẩn đoán. Bài thực hành thuộc **Java Core – Exception / Thực hành**, dùng cú pháp Java 17+. Các chương trình độc lập có lệnh `javac`/`java` và kết quả xác định; các ví dụ tích hợp với JUnit/logger sẽ được ghi rõ.

## 1. Vấn đề: lỗi kỹ thuật và lỗi nghiệp vụ không cùng mức trừu tượng

Một luồng xử lý thường có dạng:

```text
HTTP/CLI/Job boundary
       |
       v
ProfileService.loadProfile("P-17")
       |
       v
ProfileRepository.read("P-17")
       |
       +-- IOException("storage offline")
               |
               v
Service thêm ngữ cảnh nghiệp vụ:
    BusinessException(
      code=PROFILE_UNAVAILABLE,
      message="Không tải được hồ sơ",
      cause=<IOException gốc>,
      context={operation=profile.load, profileId=P-17}
    )
               |
               v
Boundary phân loại, ghi log kèm Throwable,
trả mã lỗi công khai hoặc đánh dấu job thất bại
```

Ở repository, `IOException` cho biết **cách phụ thuộc kỹ thuật đã lỗi**. Ở service, `PROFILE_UNAVAILABLE` cho biết **thao tác nghiệp vụ nào không hoàn tất**. Boundary có thể quyết định trả response, hiển thị thông báo, retry hoặc dừng job mà không phụ thuộc toàn bộ chi tiết implementation của repository.

**Không tạo một wrapper nghiệp vụ cho mọi exception một cách mù quáng.** Nếu lỗi là bug (`NullPointerException` do mã sai), bọc thành “không tìm thấy hồ sơ” có thể che nguyên nhân và báo sai loại sự cố. Chỉ chuyển loại exception khi tầng hiện tại có đủ thông tin để xác lập một ý nghĩa nghiệp vụ chính xác.

## 2. Bốn phần quan trọng của ngoại lệ nghiệp vụ

| Thành phần | Vai trò | Ví dụ |
|---|---|---|
| **Loại exception** | Đánh dấu họ lỗi thuộc miền nghiệp vụ | `BusinessException extends RuntimeException` |
| **Error code** | Mã ổn định cho logic xử lý, metric, API contract | `PROFILE_UNAVAILABLE` |
| **Message** | Diễn giải thất bại để đọc log; có thể thay đổi theo ngôn ngữ | `"Không tải được hồ sơ"` |
| **Cause** | Giữ `Throwable` gốc để bảo toàn stack, loại và thông tin lỗi nguồn | `IOException("storage offline")` |
| **Context** | Trường được chọn, có thể xử lý bằng máy | `operation`, `profileId`, `requestId` |

Mã lỗi nên có ý nghĩa **ổn định hơn câu chữ**. Ví dụ controller có thể map `PROFILE_UNAVAILABLE` sang một kết quả API phù hợp, còn message nội bộ có thể được chỉnh sửa mà không phá contract phía client. Context cho phép tìm đúng sự kiện trong log và đối chiếu request, miễn là chỉ chứa dữ liệu được phép ghi.

Một số nguyên tắc thiết kế:

1. **Giữ cause**, đừng chỉ chép `cause.getMessage()` vào chuỗi mới.
2. **Dữ liệu ngữ cảnh là immutable theo góc nhìn người dùng**, tránh bị thay đổi sau khi exception được tạo.
3. **Không nhét thông tin nhạy cảm** như password, token, cookie, key, nội dung cá nhân hoặc payload thô vào message/context.
4. **Không biến exception thành vật chứa mọi trạng thái**: chỉ giữ những trường cần để quyết định lỗi hoặc điều tra.
5. **Không trả nguyên stack trace cho người dùng cuối**. Mã lỗi công khai và thông báo an toàn là một hợp đồng khác với thông tin kỹ thuật nội bộ.
6. Một exception được ném chỉ khi có thất bại theo hợp đồng. Đừng tạo Throwable trên happy path chỉ để dự phòng.

## 3. Chọn `RuntimeException` hay checked `Exception`?

Có hai cách hợp lệ, phụ thuộc thiết kế API:

- `extends RuntimeException`: caller không bị compiler bắt buộc `catch`/`throws`; phù hợp nếu đây là thất bại nghiệp vụ được xử lý tại ranh giới ứng dụng hoặc phần lớn caller không thể phục hồi ngay.
- `extends Exception`: caller phải catch hoặc khai báo; phù hợp nếu API **chủ ý yêu cầu** người gọi xét khả năng thất bại và chọn đường phục hồi.

Không có nguyên tắc rằng *mọi business exception đều phải unchecked* hoặc *mọi business exception đều phải checked*. Quan trọng là API diễn đạt được **khả năng phục hồi, tính dự kiến và trách nhiệm xử lý**.

Ví dụ class unchecked tối thiểu:

```java
public final class ProfileUnavailableException
        extends RuntimeException {
    public ProfileUnavailableException(
            String message, Throwable cause) {
        super(message, cause);
    }
}
```

Đây là **một định nghĩa class độc lập có thể lưu trong** `ProfileUnavailableException.java`. Tuy nhiên nó chỉ giữ message/cause; muốn phân loại nhiều tình huống và đính kèm context, ta sẽ làm đầy đủ hơn ở ví dụ tiếp theo.

Nếu một lỗi **đã là domain exception có mã/context đúng**, tầng bên trên có thể truyền tiếp thay vì bọc thêm loại tương tự. Bọc chồng không thêm ngữ nghĩa tạo stack trace dài, dễ tạo nhiều lớp `Caused by` không cần thiết.

## 4. Bài thực hành chính: exception có mã lỗi, `cause` và context bất biến

Tạo tệp `BusinessExceptionDemo.java`:

```java
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class BusinessExceptionDemo {
    enum ErrorCode {
        PROFILE_UNAVAILABLE
    }

    static final class BusinessException extends RuntimeException {
        private final ErrorCode code;
        private final Map<String, String> context;

        BusinessException(
                ErrorCode code,
                String message,
                Throwable cause,
                Map<String, String> context) {
            super(message, cause);
            this.code = Objects.requireNonNull(code, "code");
            this.context = Map.copyOf(
                    Objects.requireNonNull(context, "context"));
        }

        ErrorCode code() {
            return code;
        }

        Map<String, String> context() {
            return context;
        }
    }

    @FunctionalInterface
    interface ProfileSource {
        String read(String profileId) throws IOException;
    }

    static String loadProfile(
            String profileId, ProfileSource source) {
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(source, "source");

        try {
            return source.read(profileId);
        } catch (IOException cause) {
            throw new BusinessException(
                    ErrorCode.PROFILE_UNAVAILABLE,
                    "Không tải được hồ sơ",
                    cause,
                    Map.of(
                            "operation", "profile.load",
                            "profileId", profileId));
        }
    }

    public static void main(String[] args) {
        String value = loadProfile("P-17", id -> "Lan");
        System.out.println("success = " + value);

        try {
            loadProfile("P-17", id -> {
                throw new IOException("storage offline");
            });
        } catch (BusinessException error) {
            System.out.println("code = " + error.code());
            System.out.println("message = " + error.getMessage());
            System.out.println("cause = "
                    + error.getCause().getClass().getSimpleName()
                    + ": " + error.getCause().getMessage());
            System.out.println("context = "
                    + new TreeMap<>(error.context()));
        }
    }
}
```

Biên dịch/chạy với JDK 17 trở lên:

```bash
javac BusinessExceptionDemo.java
java BusinessExceptionDemo
```

Đầu ra chính xác:

```text
success = Lan
code = PROFILE_UNAVAILABLE
message = Không tải được hồ sơ
cause = IOException: storage offline
context = {operation=profile.load, profileId=P-17}
```

### Giải thích từng thành phần

- `ErrorCode` là enum. `PROFILE_UNAVAILABLE` dùng để phân loại lỗi, không phụ thuộc nội dung message tiếng Việt.
- `BusinessException extends RuntimeException` giữ lại toàn bộ hành vi `Throwable`, gồm stack trace, `getCause()` và `getSuppressed()`.
- `super(message, cause)` là dòng quan trọng nhất cho **exception chaining**. Nó nối lỗi nghiệp vụ với `IOException` gốc.
- `context` là `Map<String,String>`. `Map.copyOf(...)` tạo một map không cho sửa qua API thông thường; ví dụ chỉ dùng các giá trị `String`, vốn immutable.
- `Objects.requireNonNull(...)` từ chối `code` hoặc map context bị null ngay khi khởi tạo; `Map.copyOf` không chấp nhận key/value null.
- `ProfileSource` đóng vai trò dependency có thể thay bằng fake lambda. Không cần database hay mạng để tái hiện đường thành công/thất bại.
- Tầng service mới biết nghiệp vụ `"profile.load"` và `"P-17"`, nên nó thêm đúng ngữ cảnh tại đây.
- `new TreeMap<>(error.context())` dùng **riêng cho việc in ví dụ** nhằm có thứ tự khóa xác định. `Map.copyOf` không cam kết iteration order phù hợp để hard-code output.

Ở đường thành công, không có business exception nào được tạo. Ở đường lỗi, caller nhận thông tin nghiệp vụ, còn `getCause()` vẫn giữ lỗi kỹ thuật. Khi logger in `Throwable` đầy đủ, stack trace có thể hiển thị nhánh `Caused by:` của `IOException`.

### Vì sao không đưa map trực tiếp vào constructor rồi giữ nguyên tham chiếu?

Nếu chỉ làm:

```java
this.context = context;
```

caller còn giữ `HashMap` gốc có thể thay các trường sau khi exception được tạo. Khi đến lúc ghi log, context đã không còn phản ánh tình huống thất bại ban đầu. Với `Map.copyOf`, các cặp key/value được sao chép sang collection không sửa qua map trả về.

Lưu ý: **immutable map không đồng nghĩa deep immutable nếu value là object mutable**. Dùng `Map<String,String>` hoặc những value bất biến có kích thước giới hạn làm việc kiểm soát đơn giản hơn `Map<String,Object>` chứa entity, session, request body hoặc các object có trạng thái có thể đổi.

## 5. Kiểm tra tính bất biến và identity của cause

Một business exception tốt có thể được sử dụng qua nhiều tầng sau khi khởi tạo. Các tầng ấy phải nhìn thấy cùng mã lỗi, cùng ngữ cảnh đã chốt và cùng root cause đã được truyền vào.

Tệp `BusinessExceptionInvariantDemo.java`:

```java
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class BusinessExceptionInvariantDemo {
    static class BusinessException extends RuntimeException {
        private final String code;
        private final Map<String, String> context;

        BusinessException(
                String code, String message,
                Throwable cause, Map<String, String> context) {
            super(message, cause);
            this.code = code;
            this.context = Map.copyOf(context);
        }

        String code() {
            return code;
        }

        Map<String, String> context() {
            return context;
        }
    }

    public static void main(String[] args) {
        IOException original = new IOException("disk error");

        Map<String, String> input = new HashMap<>();
        input.put("operation", "report.export");

        BusinessException error = new BusinessException(
                "REPORT_UNAVAILABLE",
                "Không xuất được báo cáo",
                original,
                input);

        input.put("operation", "changed later");

        System.out.println("code = " + error.code());
        System.out.println("cause identity = "
                + (error.getCause() == original));
        System.out.println("snapshot = "
                + error.context().get("operation"));

        try {
            error.context().put("operation", "tampered");
        } catch (UnsupportedOperationException expected) {
            System.out.println("context modifiable = false");
        }
    }
}
```

Lệnh:

```bash
javac BusinessExceptionInvariantDemo.java
java BusinessExceptionInvariantDemo
```

Kết quả:

```text
code = REPORT_UNAVAILABLE
cause identity = true
snapshot = report.export
context modifiable = false
```

Ý nghĩa:

1. Sau khi tạo exception, việc thay đổi `input` không thay đổi context được lưu.
2. `error.getCause() == original` chứng minh **chính đối tượng nguyên nhân** đã được giữ, không phải tạo một exception mới cùng message.
3. Caller không thể sửa map trả về bằng `put`.
4. Message và mã lỗi cung cấp hai tầng contract khác nhau: diễn giải và phân loại.

Chương trình minh họa dùng constructor tối giản chưa ràng buộc `code != null`. Trong thiết kế production nên thêm kiểm tra cho các tham số bắt buộc và có test tương ứng. Điều này không thay đổi ý nghĩa bài thực hành về snapshot context/cause.

## 6. Trường dữ liệu ngữ cảnh nào là hữu ích?

**Context không phải bản sao toàn bộ request.** Nó nên trả lời một số câu hỏi chẩn đoán thiết yếu:

- **Đang làm gì?** `operation=invoice.export`, `resource=profile`.
- **Liên quan đối tượng nào?** `profileId=P-17`, `orderId=O-42`, với ID đã qua kiểm soát.
- **Có thể nối log với nhau không?** `requestId=R-103`, `traceId` được hệ thống sinh/quản lý.
- **Thất bại ở pha nào?** `phase=repository.read`, `attempt=3`, `dependency=profile-store`.
- **Có error code nào để gom nhóm?** `PROFILE_UNAVAILABLE` nên là trường riêng, không cần nhét vào mỗi message.

Thông tin **không nên** có trong exception context hoặc message: password, JWT, cookie, authorization header, API key, credit card, request body thô, thông tin y tế/cá nhân nhạy cảm, hoặc toàn bộ entity có `toString()` không kiểm soát.

Chỉ sử dụng một danh sách trường **được cho phép** (allowlist). Kể cả ID cũng nên bị giới hạn độ dài và tuân theo chính sách ghi nhận dữ liệu. Dữ liệu từ client chưa được xác thực không nên được đưa thẳng vào log như một ID đáng tin.

### Những lựa chọn biểu diễn

| Cách biểu diễn | Ưu điểm | Điểm cần lưu ý |
|---|---|---|
| `Map<String,String>` | Mở rộng thuận tiện, dùng tốt với structured logging | Phải quản lý key, giá trị, kích thước và tránh key viết sai |
| Field rõ ràng như `orderId`, `operation` | Compiler giúp kiểm soát kiểu và sự bắt buộc | Tăng số constructor/kiểu khi nhiều nhóm lỗi |
| `record ErrorContext(...)` | Immutable theo cấu trúc Java 17, biểu đạt schema | Cần xác định trường nào chung cho mọi lỗi |
| Một chuỗi `details` | Đơn giản ban đầu | Khó tìm kiếm/phân loại và dễ trộn dữ liệu nhạy cảm |
| `Map<String,Object>` tùy ý | Linh hoạt | Khó bảo đảm deep immutability và an toàn serialization |

Nếu một domain có vài loại lỗi chính với trường cố định, một class/record context **mạnh kiểu** có thể tốt hơn map. Nếu cần hệ thống lỗi thống nhất qua nhiều operation, map có allowlist/validation cũng có giá trị.

### Cách giới hạn context trước khi tạo exception

Ví dụ **mảnh mã minh họa**:

```java
static String safeId(String raw) {
    if (raw == null || !raw.matches("[A-Za-z0-9_-]{1,32}")) {
        return "unavailable";
    }
    return raw;
}
```

Phương thức trên minh họa cách hạn chế độ dài và ký tự của **ID kỹ thuật**. Không dùng regex này cho tên người hoặc dữ liệu quốc tế hóa nếu mục đích khác. Trên đường lỗi, tránh tính ngữ cảnh bằng một cuộc gọi DB/HTTP mới chỉ để làm message đẹp hơn; thao tác phụ đó có thể cũng lỗi và che nguyên nhân ban đầu.

## 7. `cause` khác dữ liệu ngữ cảnh

`cause` là một **đối tượng Throwable**, có kiểu, message và stack riêng. Context là **metadata phục vụ hiểu thao tác**, không phải nguyên nhân.

Ví dụ:

```text
BusinessException
  code    = PROFILE_UNAVAILABLE
  message = Không tải được hồ sơ
  context = {profileId=P-17, operation=profile.load}
  cause   = IOException("storage offline")
```

Cần tránh hai sai lầm:

1. Chỉ lưu `context.put("rootCause", e.toString())` mà không truyền `e` vào constructor. Điều này đánh mất type/stack và chuỗi `Caused by` chuẩn.
2. Chỉ truyền cause, không thêm ngữ cảnh ở ranh giới biết operation. Caller sau đó có thể biết `"connection refused"` nhưng không biết nó thuộc thao tác nghiệp vụ nào.

**Không cần bọc nhiều lần nếu ngữ cảnh đã đủ.** Ngoại lệ nghiệp vụ có thể đi qua controller, job runner hoặc mapper; những tầng này thường đọc code/context, không nhất thiết tạo wrapper mới.

Nếu `Throwable` bắt nguồn từ một callback bất đồng bộ, có thể gặp `CompletionException` hoặc `ExecutionException` ở tầng quan sát. Hãy phân loại đúng **lớp lỗi quan sát** và giữ cause thực sự; không tạo wrapper chỉ vì nhìn thấy tên `CompletionException`.

## 8. Checked business exception: lựa chọn khác cho hợp đồng công khai

Một tác vụ xuất báo cáo có thể công khai rằng caller **phải xét đến thất bại nghiệp vụ**. Khi đó dùng `extends Exception` là một lựa chọn có cơ sở.

Tệp `CheckedBusinessExceptionDemo.java`:

```java
import java.io.IOException;

public class CheckedBusinessExceptionDemo {
    static final class ExportException extends Exception {
        private final String code;
        private final String reportId;

        ExportException(
                String code, String reportId,
                String message, Throwable cause) {
            super(message, cause);
            this.code = code;
            this.reportId = reportId;
        }

        String code() {
            return code;
        }

        String reportId() {
            return reportId;
        }
    }

    @FunctionalInterface
    interface ReportSource {
        String read() throws IOException;
    }

    static String export(
            String reportId, ReportSource source)
            throws ExportException {
        try {
            return source.read();
        } catch (IOException cause) {
            throw new ExportException(
                    "REPORT_SOURCE_FAILED",
                    reportId,
                    "Không xuất được báo cáo",
                    cause);
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("success = "
                    + export("R-7", () -> "ready"));

            export("R-7", () -> {
                throw new IOException("source disconnected");
            });
        } catch (ExportException error) {
            System.out.println("code = " + error.code());
            System.out.println("reportId = " + error.reportId());
            System.out.println("cause = "
                    + error.getCause().getClass().getSimpleName());
        }
    }
}
```

Chạy:

```bash
javac CheckedBusinessExceptionDemo.java
java CheckedBusinessExceptionDemo
```

Kết quả:

```text
success = ready
code = REPORT_SOURCE_FAILED
reportId = R-7
cause = IOException
```

`export(...)` khai báo `throws ExportException`. Caller phải catch hoặc propagate checked exception này. `reportId` được biểu diễn là field cụ thể: nếu đây là một trường domain bắt buộc, field rõ ràng giúp API có hợp đồng mạnh hơn một map key tùy ý.

Ví dụ cố tình không có các dependency ngoài JDK. Trong hệ thống thật, cân nhắc constructor kiểm tra `code`/`reportId` null, chính sách giới hạn độ dài ID và bảo vệ thông tin chẩn đoán.

## 9. Có cần constructor không nhận `cause`?

Không phải mọi business exception đều do một exception khác gây ra. Validation như “số lượng phải dương” có thể được phát hiện **trực tiếp**:

```java
if (quantity <= 0) {
    throw new BusinessException(
            ErrorCode.INVALID_QUANTITY,
            "Số lượng phải lớn hơn 0",
            null,
            Map.of("field", "quantity"));
}
```

Đây là **mảnh mã minh họa** dùng cấu trúc class/enum tương tự ví dụ chính; `INVALID_QUANTITY` phải được thêm vào enum tương ứng. Ở trường hợp này **không có Throwable nguồn**, nên `cause == null` là hợp lý. Đừng tạo một `IllegalArgumentException` giả chỉ để constructor luôn có cause.

Nếu class phục vụ cả lỗi trực tiếp lẫn lỗi bọc, có thể cung cấp hai constructor:

```java
BusinessException(
        ErrorCode code, String message,
        Map<String, String> context) {
    this(code, message, null, context);
}

BusinessException(
        ErrorCode code, String message,
        Throwable cause, Map<String, String> context) {
    super(message, cause);
    this.code = Objects.requireNonNull(code);
    this.context = Map.copyOf(context);
}
```

Đây là **các constructor minh họa nằm bên trong class `BusinessException`** với các field `code`, `context`; cần imports `Map`, `Objects`. Chúng không tạo một class độc lập khi chỉ copy hai đoạn.

Các constructor nên có thứ tự tham số thống nhất và được tài liệu hóa; API có quá nhiều tham số giống kiểu `String` rất dễ bị truyền nhầm vị trí. Nếu context nhiều trường, cân nhắc dùng đối tượng `ErrorContext` riêng hoặc factory có tên rõ ràng.

## 10. Đừng để ngoại lệ nghiệp vụ phụ thuộc vào logging framework

Business exception nên là **dữ liệu về lỗi và cơ chế truyền lỗi**, không phải nơi trực tiếp quyết định ghi log, trả HTTP hay retry.

Tránh:

```java
BusinessException(...) {
    logger.error("...", cause);
    // rồi caller phía ngoài lại log thêm cùng lỗi
}
```

Đây là ý tưởng **không khuyến nghị**. Constructor tự log có thể tạo một bản `ERROR` ngay cả khi caller sẽ phục hồi; cũng khiến test và class domain phụ thuộc hạ tầng logging.

Thay vào đó:

```text
Repository: ném lỗi kỹ thuật thích hợp
Service: bổ sung ngữ cảnh/cause nghiệp vụ nếu cần
Controller/Job boundary: quyết định xử lý và log theo một chính sách
Error mapper: tạo response công khai an toàn
```

Nếu lỗi được phục hồi ở service (ví dụ fallback), service có thể báo metric hoặc event phù hợp. Nếu lỗi không được phục hồi và đi tới boundary cuối, một log có đầy đủ Throwable/cause/context thường giá trị hơn nhiều log giống nhau ở từng `catch`.

Một ngoại lệ domain cũng không nên tự mở kết nối mạng, truy vấn kho dữ liệu hoặc làm I/O khi được tạo. Điều đó có thể làm đường xử lý lỗi chậm hơn hoặc phát sinh ngoại lệ mới che lỗi gốc.

## 11. Khi cleanup thất bại: giữ `Suppressed` cùng cause

Một lỗi thực tế có thể chứa **nhiều sự cố**:

1. Thân xử lý ném `IOException("write failed")`.
2. `close()` cũng ném `IOException("close failed")`.
3. Service bọc lỗi chính thành business exception.

Nếu sử dụng `try-with-resources`, lỗi close thường được lưu **ở `getSuppressed()` của lỗi chính**, không tự chuyển thành `getCause()` của business exception. Khi bọc, điều quan trọng là **giữ nguyên Throwable nguồn**, bởi nó chứa cả thông tin suppressed.

Tệp `BusinessExceptionSuppressedDemo.java`:

```java
import java.io.IOException;

public class BusinessExceptionSuppressedDemo {
    static final class FailingResource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    static final class ExportException extends RuntimeException {
        ExportException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static void writeReport() throws IOException {
        try (FailingResource resource = new FailingResource()) {
            throw new IOException("write failed");
        }
    }

    static void exportReport() {
        try {
            writeReport();
        } catch (IOException cause) {
            throw new ExportException(
                    "Không thể ghi báo cáo", cause);
        }
    }

    public static void main(String[] args) {
        try {
            exportReport();
        } catch (ExportException error) {
            Throwable root = error.getCause();
            System.out.println("outer = "
                    + error.getClass().getSimpleName());
            System.out.println("cause = " + root.getMessage());
            System.out.println("suppressed count = "
                    + root.getSuppressed().length);
            System.out.println("suppressed = "
                    + root.getSuppressed()[0].getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac BusinessExceptionSuppressedDemo.java
java BusinessExceptionSuppressedDemo
```

Đầu ra:

```text
outer = ExportException
cause = write failed
suppressed count = 1
suppressed = close failed
```

`BusinessException` ở đây được đơn giản hóa thành `ExportException` chỉ chứa message/cause nhằm làm rõ cơ chế `Suppressed`. Trong mẫu domain exception đầy đủ, hãy bổ sung error code và context như ví dụ chính.

**Đừng ghi đè cause bằng ngoại lệ cleanup.** `Caused by` trả lời mối liên hệ nguyên nhân giữa wrapper và lỗi nguồn, còn `Suppressed` giữ lỗi phụ của cleanup. Một business exception có thể có chính nó một mảng suppressed riêng nếu mã gọi `addSuppressed`; trong ví dụ, suppressed nằm ở **root `IOException`**. Vì thế `error.getSuppressed()` có thể rỗng trong khi `error.getCause().getSuppressed()` có phần tử.

## 12. Ở ranh giới API: tách thông tin nội bộ khỏi response

Một exception nghiệp vụ có thể có dữ liệu điều tra phong phú, nhưng response công khai phải theo một **hợp đồng riêng**.

Ví dụ ý tưởng:

```text
BusinessException nội bộ:
    code    = PROFILE_UNAVAILABLE
    message = Không tải được hồ sơ
    cause   = IOException("storage offline")
    context = {profileId=P-17, operation=profile.load}

Response công khai:
    errorCode = PROFILE_UNAVAILABLE
    message   = Dịch vụ tạm thời không khả dụng
    requestId = R-103
```

Đây là **mô tả dữ liệu**, không phải JSON/API schema chuẩn bắt buộc. Không đưa nguyên stack trace, đường dẫn file, cấu trúc DB, SQL, credential hoặc nội dung exception nguồn vào HTTP body.

Ví dụ Java thuần để minh họa phân loại:

```java
static int httpStatusFor(String code) {
    return switch (code) {
        case "INVALID_QUANTITY" -> 400;
        case "PROFILE_UNAVAILABLE" -> 503;
        default -> 500;
    };
}
```

Đây là **phương thức minh họa** trong class, không phải HTTP server hoàn chỉnh. 400/503 chỉ là chính sách gợi ý cho tình huống tương ứng. Với lỗi không tìm thấy tài nguyên, xung đột trạng thái hoặc quyền truy cập, hãy quyết định status dựa theo contract dịch vụ, không dựa duy nhất vào tên class.

**Cẩn trọng với 4xx/5xx:** một `IllegalArgumentException` có thể bắt nguồn từ bug server, không phải input client. Chỉ map thành 400 khi có bằng chứng lỗi thuộc trách nhiệm request. Đừng phân loại toàn bộ unchecked exception thành “lỗi người dùng”.

### Mảnh ví dụ tích hợp logger

Với logger theo API SLF4J tương thích:

```java
logger.error(
        "operation={} errorCode={} requestId={} context={}",
        operation,
        businessError.code(),
        requestId,
        businessError.context(),
        businessError);
```

Đây là **mảnh mã tích hợp**, cần thư viện/logger và các biến đã khai báo. `businessError` là **đối tượng Throwable ở đối số cuối**; với API hỗ trợ cách gọi này, logger có thể in stack trace, cause và suppressed. Hãy kiểm tra overload/structured-logging API của phiên bản thư viện bạn dùng.

Trong production, đôi khi không cần in nguyên context dưới dạng `toString()`: logger có thể ghi các trường có cấu trúc và áp dụng masking/retention tốt hơn. Tránh ghi cả ở repository, service lẫn global handler nếu chúng đang nói về **cùng một thất bại cuối**.

## 13. Mẫu chuẩn: chuyển lỗi ở ranh giới biết đủ ngữ cảnh

Có thể phân chia trách nhiệm:

```text
Repository
  readProfile(id) throws IOException
      |
      +-- không tự tạo code nghiệp vụ chỉ thuộc service
      |
Service
  loadProfile(id)
      |
      +-- bắt IOException có thể quy về lỗi domain
      +-- tạo BusinessException(P... , cause, context đã kiểm tra)
      |
Boundary
  onFailure(BusinessException)
      |
      +-- phân loại code, metric, log, response/job outcome
```

Câu hỏi review khi thấy `catch`:

- **Tầng này có thật sự hiểu nguyên nhân theo nghiệp vụ không?** Nếu không, truyền tiếp hoặc dùng exception kỹ thuật có ý nghĩa.
- **Đã có business exception tương đương chưa?** Nếu có, thường không cần bọc thêm.
- **Context nào tầng hiện tại biết chắc?** Đừng tạo `requestId` giả hoặc suy diễn transaction ID.
- **Có giữ đúng Throwable gốc không?** Không chỉ giữ message.
- **Người dùng cuối nên thấy gì?** Đây là trách nhiệm boundary/API mapper.
- **Có cần ghi log ngay không?** Nếu sẽ ném tiếp và handler cuối đã log, tránh phát sinh log trùng.

Khi một operation có retry, context có thể thêm `attempt=3` hoặc `retryExhausted=true` dưới dạng trường an toàn. Nhưng đừng log `ERROR` toàn stack cho mọi attempt chưa kết thúc; một lỗi cuối có ngữ cảnh đủ tốt thường phản ánh đúng kết quả.

## 14. Thiết kế context có kiểu mạnh khi nhu cầu cố định

Với vài trường bắt buộc, `record` trong Java 17 cho một lựa chọn rõ ràng:

```java
record ErrorContext(
        String operation,
        String resourceId,
        String correlationId) {
}
```

Đây là **một record có thể đặt trong tệp riêng hoặc cùng package phù hợp**. Bản thân record có các component final và accessor; tuy nhiên nếu component là đối tượng mutable, record không tự deep-copy. Với `String`, dữ liệu là immutable.

Có thể dùng class business exception chứa:

```java
private final ErrorContext context;

BusinessException(
        ErrorCode code,
        String message,
        Throwable cause,
        ErrorContext context) {
    super(message, cause);
    this.code = Objects.requireNonNull(code);
    this.context = Objects.requireNonNull(context);
}
```

Đây là **mảnh constructor minh họa** thuộc class có các field `code`, `context` đã khai báo. Ưu điểm: compiler giúp phát hiện nhầm tên trường, IDE hỗ trợ navigation và các field quan trọng hiện diện ngay trong API. Nhược điểm: schema ít linh hoạt hơn map với nhiều loại operation.

Cần phân biệt `record` cho **context** với việc dùng record làm **exception**: Java record ngầm kế thừa `java.lang.Record`, nên một record **không thể đồng thời extends `RuntimeException`**. Muốn tạo Throwable riêng, dùng `class extends Exception/RuntimeException`.

## 15. Context không thay thế stack trace và message

Một anti-pattern:

```java
throw new IllegalStateException(
        "PROFILE_UNAVAILABLE; "
        + "profileId=" + id
        + "; cause=" + cause.getMessage());
```

Đây là **mảnh mã minh họa** trong `catch`. Nó trộn error code, context và lời mô tả cause thành một chuỗi. Nếu không truyền `cause` cho constructor, caller mất **loại Throwable gốc và stack trace**. Chuỗi message còn khó parse, dễ bị thay đổi do ngôn ngữ hoặc escaping.

Mẫu tốt hơn:

```java
throw new BusinessException(
        ErrorCode.PROFILE_UNAVAILABLE,
        "Không thể tải hồ sơ",
        cause,
        Map.of("profileId", id, "operation", "profile.load"));
```

Đây là **mảnh minh họa** dùng định nghĩa exception trong mục 4. Error code dùng để quyết định, context dùng để lọc log, message cho người đọc, cause cho nguyên nhân kỹ thuật. Các vai trò bổ sung nhau.

Đừng dựa vào `Throwable.toString()` như một định dạng lưu trữ máy cần parse; `toString()` chủ yếu kết hợp tên lớp và `getLocalizedMessage()`. Cần máy đọc, hãy sử dụng field/code riêng.

## 16. Test đầy đủ hợp đồng error code, cause, context và đường thành công

JUnit Jupiter giúp test loại exception và field. Phần dưới là **ví dụ tích hợp JUnit 5**, giả định class `BusinessExceptionDemo` ở mục 4 có thể được truy cập từ test trong cùng package.

Tệp `BusinessExceptionContractTest.java`:

```java
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class BusinessExceptionContractTest {
    @Test
    void returnsProfileOnSuccess() {
        String value = BusinessExceptionDemo.loadProfile(
                "P-17", id -> "Lan");

        assertEquals("Lan", value);
    }

    @Test
    void keepsBusinessCodeCauseAndContextOnFailure() {
        IOException root = new IOException("storage offline");

        BusinessExceptionDemo.BusinessException error =
                assertThrows(
                    BusinessExceptionDemo.BusinessException.class,
                    () -> BusinessExceptionDemo.loadProfile(
                        "P-17", id -> { throw root; }));

        assertEquals(
                BusinessExceptionDemo.ErrorCode.PROFILE_UNAVAILABLE,
                error.code());
        assertSame(root, error.getCause());
        assertEquals("P-17", error.context().get("profileId"));
        assertEquals("profile.load",
                error.context().get("operation"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> error.context().put("other", "value"));
    }
}
```

Test cần dependency `org.junit.jupiter` và test runner của Maven/Gradle/IDE. Không chạy trực tiếp với `java BusinessExceptionContractTest` mà chưa thiết lập môi trường test. Class lồng của ví dụ chính có visibility mặc định package-private, nên đặt test cùng package nếu giữ nguyên mã.

**Vì sao các assertion này hữu ích?**

- Test success chứng minh đường hợp lệ không ném business exception và trả đúng giá trị.
- `assertThrows` khẳng định **đúng loại exception theo hợp đồng**; nó chấp nhận subclass. Nếu contract đòi chính xác class, cân nhắc `assertThrowsExactly` khi JUnit hỗ trợ.
- `assertSame(root, error.getCause())` khẳng định giữ **đúng đối tượng nguyên nhân**, không chỉ tạo mới một Throwable tương tự.
- Hai assertion context khẳng định ngữ cảnh còn chính xác khi lỗi được truyền.
- Thử `put` xác nhận view map context không sửa được.

### Kiểm thử một lỗi validation không có cause

Nếu API tạo business exception **trực tiếp** vì input sai, kỳ vọng có thể là:

- `error.code() == INVALID_QUANTITY`.
- `error.getCause() == null` nếu không có lỗi nguồn.
- Context chỉ chứa tên trường/constraint an toàn.
- Không có side effect trên kho dữ liệu.

Đừng ép mọi exception phải có cause khác null. **Cause bắt buộc khi bọc một lỗi thực sự; cause không nhất thiết tồn tại khi tự phát hiện vi phạm nghiệp vụ.**

## 17. Test thuần JDK không cần framework

Nếu muốn kiểm tra nhanh một hợp đồng không cần JUnit, bạn có thể tự assert. Tệp `BusinessExceptionSmokeTest.java`:

```java
import java.io.IOException;
import java.util.Map;

public class BusinessExceptionSmokeTest {
    static final class DomainFailure extends RuntimeException {
        private final String code;
        private final Map<String, String> context;

        DomainFailure(
                String code, Throwable cause,
                Map<String, String> context) {
            super("read failed", cause);
            this.code = code;
            this.context = Map.copyOf(context);
        }

        String code() {
            return code;
        }

        Map<String, String> context() {
            return context;
        }
    }

    static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Contract violated");
        }
    }

    static String read(boolean fail) {
        if (!fail) {
            return "OK";
        }

        IOException root = new IOException("offline");
        throw new DomainFailure(
                "READ_UNAVAILABLE",
                root,
                Map.of("operation", "data.read"));
    }

    public static void main(String[] args) {
        check("OK".equals(read(false)));
        System.out.println("success tested");

        try {
            read(true);
            throw new AssertionError("Expected DomainFailure");
        } catch (DomainFailure e) {
            check("READ_UNAVAILABLE".equals(e.code()));
            check(e.getCause() instanceof IOException);
            check("data.read".equals(
                    e.context().get("operation")));

            System.out.println("failure tested");
            System.out.println("cause = "
                    + e.getCause().getClass().getSimpleName());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac BusinessExceptionSmokeTest.java
java BusinessExceptionSmokeTest
```

Output:

```text
success tested
failure tested
cause = IOException
```

Đây là ví dụ test một class exception nhỏ độc lập, không phụ thuộc demo đầu tiên. Nếu code bị sửa để **nuốt lỗi** và trả `"OK"`, test đường thất bại ném `AssertionError`. Nếu bỏ cause hoặc mã lỗi, các phép kiểm tra cũng phát hiện. Những assertion tùy biến này minh họa tư duy hợp đồng; trong dự án lớn nên dùng thư viện test để có báo cáo, fixture và quản lý suite tốt hơn.

## 18. Kiểm soát log và khả năng quan sát

Ngoại lệ nghiệp vụ có context không tự mang ý nghĩa rằng phải in log tại mỗi tầng. Quyết định log nên thuộc thành phần biết **kết quả cuối cùng** của request/job.

Một chính sách thực hành:

| Trường hợp | Hành động | Log phù hợp |
|---|---|---|
| Repository ném `IOException`, service sẽ bọc | Ném lỗi lên, giữ cause | Thường không cần `ERROR` trùng |
| Service nhận lỗi có thể phục hồi bằng fallback | Trả dữ liệu dự phòng, báo metric | `INFO`/`WARN` hoặc metric theo tác động |
| BusinessException đến global handler và request thất bại | Map error code/status, log Throwable đầy đủ một lần | `ERROR` nếu là thất bại không dự kiến/cần điều tra |
| Input client sai theo contract | Tạo lỗi validation có code rõ | Có thể `INFO`/metric, không mặc định stack `ERROR` |
| Cancellation/interrupt dự kiến | Hợp tác dừng và cleanup | Thường không phải error nghiệp vụ |
| Có lỗi cleanup gây hậu quả riêng | Giữ suppressed, đánh giá sự cố | Có thể log sự kiện riêng nếu có ý nghĩa độc lập |

Để liên kết log giữa các tầng, dùng request ID hoặc trace ID được tạo ở entry boundary và truyền qua context/telemetry. Nếu dùng `ThreadLocal`/MDC trong tác vụ bất đồng bộ, bảo đảm cơ chế propagation và cleanup đúng; không giả định thread mới tự có context của caller.

Không nên đưa một toàn bộ stack trace vào `context` dưới dạng chuỗi. `Throwable` đã giữ thông tin stack, `Caused by` và `Suppressed`; logger nên được cấu hình để ghi các cấu trúc đó khi cần.

## 19. Các lỗi triển khai thường gặp

### 19.1. Tạo exception mới nhưng không truyền cause

Sai:

```java
catch (IOException e) {
    throw new BusinessException(
            ErrorCode.PROFILE_UNAVAILABLE,
            "Không tải được hồ sơ",
            null,
            Map.of("operation", "profile.load"));
}
```

Mảnh mã trên dùng class ví dụ chính. `null` khiến chuỗi nguyên nhân bị mất dù `e` đang tồn tại. Sửa bằng cách truyền `e` vào tham số cause.

### 19.2. Chỉ giữ nguyên văn `getMessage()` của lỗi kỹ thuật

```java
throw new RuntimeException("Source failed: " + e.getMessage());
```

Mảnh mã minh họa này có thể bỏ mất loại exception và stack gốc; message có thể thay đổi theo thư viện/JDK. Nếu cần bọc, dùng constructor nhận Throwable, rồi để logger in cause.

### 19.3. Biến exception thành nơi ghi log tự động

Constructor `BusinessException` gọi `logger.error` làm mỗi lần khởi tạo tạo log, kể cả khi caller phục hồi. Hãy tách event logging khỏi dữ liệu lỗi, tránh lặp stack trace nhiều tầng.

### 19.4. Cho context tham chiếu đến map mutable

Giữ nguyên tham chiếu `HashMap` đầu vào có thể làm context bị sửa sau khi lỗi xảy ra. Sử dụng `Map.copyOf`, `Collections.unmodifiableMap` trên bản sao hoặc record có value immutable.

### 19.5. Dùng `Map<String,Object>` chứa toàn entity

Object có thể thay đổi, `toString()` đắt hoặc lộ dữ liệu. Chỉ lưu trường an toàn, đã chuẩn hóa, có hạn mức.

### 19.6. Dùng error message làm error code

So sánh `"Không tìm thấy hồ sơ"` trong logic điều khiển rất dễ hỏng khi đổi ngôn ngữ hay chỉnh câu chữ. Sử dụng enum/string code ổn định.

### 19.7. Map mọi `RuntimeException` thành lỗi đầu vào

`NullPointerException` từ bug server không đương nhiên là HTTP 400. Phải xác định lỗi có phải do client vi phạm hợp đồng hay không.

### 19.8. Bọc lại business exception ở từng tầng

Ba tầng cùng làm `throw new BusinessException(..., caught)` gây nhiều lớp wrapper không cần thiết; điều tra trở nên khó và thông tin context dễ trùng. Chỉ thêm wrapper nếu thay đổi **ngữ nghĩa** hoặc hợp đồng của tầng.

### 19.9. Dùng exception chứa dữ liệu nhạy cảm

Đừng nhét token, secret, SQL chứa dữ liệu, địa chỉ riêng hoặc payload người dùng vào context. Log được lưu và truy cập ở những nơi khác với luồng xử lý nghiệp vụ; quyền riêng tư phải là một phần của thiết kế ngoại lệ.

### 19.10. Để hàm tạo message gây lỗi mới

Một message supplier thực hiện I/O hoặc serialize object quá lớn có thể tạo lỗi thứ hai, che lỗi chính. Message và context nên được chuẩn bị từ **thông tin sẵn có, rẻ và đáng tin** trên đường lỗi.

### 19.11. Nhầm cause với suppressed

Một lỗi `close()` xảy ra sau lỗi xử lý chính thường là **suppressed**. Đừng tự thay cause bằng lỗi cleanup; dùng `try-with-resources` hoặc cơ chế bảo toàn cả hai exception.

### 19.12. Test chỉ kiểm tra có `RuntimeException`

Test quá rộng có thể xanh vì một `NullPointerException` bất ngờ. Kiểm tra đúng business exception, code, cause và context; thêm test success để tránh thay đổi hành vi bình thường.

## 20. Quy trình xây dựng business exception trong dự án thực tế

Có thể áp dụng theo thứ tự:

1. **Liệt kê tình huống nghiệp vụ thật sự cần phân loại**: validation sai, tài nguyên không khả dụng, xung đột, phụ thuộc lỗi, thao tác bị từ chối.
2. **Xác định contract với caller**: checked/unchecked, loại exception, mã lỗi công khai, tính có thể retry, thông điệp hiển thị.
3. **Chọn schema context**: ID/operation/correlation ID đã kiểm soát, whitelist và giới hạn kích thước; không lưu thông tin bí mật.
4. **Định nghĩa immutable exception fields**: `final`, getter chỉ đọc, `Map.copyOf` hoặc record context có dữ liệu immutable.
5. **Giữ nguyên cause** khi bọc lỗi kỹ thuật ở ranh giới đủ ngữ nghĩa.
6. **Bảo toàn suppressed** nếu cleanup cũng lỗi; ưu tiên `try-with-resources`.
7. **Chỉ bọc tại tầng phù hợp**, không log-and-throw trùng ở mỗi `catch`.
8. **Mapping ở boundary**: dịch `ErrorCode` thành response/job result an toàn, log Throwable có ngữ cảnh ở nơi chịu trách nhiệm.
9. **Kiểm thử hai đường**: success, failure, cause identity, context snapshot, field bắt buộc, cleanup side effects.
10. **Theo dõi thực tế**: metric theo error code/operation, trace ID, tỷ lệ lỗi, retry outcome và chính sách bảo vệ log.

### Checklist nhanh trước khi merge

- Loại exception có phản ánh đúng hợp đồng và caller có biết cách xử lý?
- `ErrorCode` có ổn định và không phụ thuộc câu chữ thông báo?
- Constructor bọc lỗi có gọi `super(message, cause)`?
- Nếu là validation tự phát hiện, việc cause bằng `null` có đúng?
- Context có immutable, chỉ gồm các trường an toàn, ổn định và vừa đủ?
- Có tránh truyền nguyên entity, request body, token và đối tượng mutable?
- Có bảo toàn cause/suppressed nếu cả xử lý và cleanup đều thất bại?
- Có một tầng chịu trách nhiệm map kết quả/log hay đang ghi lặp ở nhiều tầng?
- Test có xác minh success, loại exception, mã lỗi, `getCause()`, context, và side effects?
- Response công khai có tách khỏi thông tin kỹ thuật nhạy cảm trong Throwable?

**Kết luận:** một ngoại lệ nghiệp vụ tốt là **hợp đồng lỗi có cấu trúc**, không phải một chuỗi message dài. Dùng loại exception phù hợp, **mã lỗi ổn định**, thông điệp có ích, `super(message, cause)` để giữ nguyên nhân và **context bất biến, an toàn** cho chẩn đoán. Chỉ chuyển loại lỗi ở nơi hiểu ngữ nghĩa nghiệp vụ, giữ suppressed khi cleanup thất bại và kiểm thử cả đường thành công lẫn đường lỗi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-practice-006">Tái hiện lỗi chính kèm nhiều suppressed exception</a>

<details>
<summary>Xem chi tiết</summary>

# Tái hiện lỗi chính kèm nhiều suppressed exception

## 1. Mục tiêu thực hành

Một lỗi Java không nhất thiết chỉ gồm một exception. Khi chương trình đang thực hiện công việc thì phát sinh lỗi A, rồi quá trình dọn tài nguyên cũng thất bại với B, C và D, Java phải lựa chọn **lỗi nào đại diện cho thất bại chính** và **giữ những lỗi còn lại ở đâu**. Nếu chọn sai cách cleanup hoặc chỉ log một exception ở ngoài, bạn có thể mất bằng chứng quan trọng.

Trong bài thực hành này, bạn sẽ **chủ động tái hiện một lỗi chính cùng nhiều lỗi bị suppress** bằng `try-with-resources` và một tài nguyên giả triển khai `AutoCloseable`. Không cần làm hỏng file, socket hay database thật.

Sau khi hoàn thành, bạn có thể:

1. Tạo nhiều resource với thứ tự khởi tạo được kiểm soát.
2. Khiến thân `try` ném một `IOException` chính, trong khi nhiều `close()` cũng ném các `IOException` khác.
3. Quan sát Java đóng các resource **theo thứ tự ngược với khai báo** và tiếp tục đóng resource còn lại sau khi một `close()` thất bại.
4. Kiểm tra exception **primary**, số lượng và thứ tự của `getSuppressed()`.
5. Phân biệt lỗi **khởi tạo resource**, lỗi **thân công việc** và lỗi **đóng resource**.
6. Chứng minh một exception được bọc bằng `new IllegalStateException(message, cause)` **không tự chuyển suppressed từ cause sang wrapper**.
7. Viết assertion cho JUnit 5 bằng `assertSame`, `assertEquals` và lịch sử sự kiện thay vì chỉ kiểm tra “có exception”.
8. Hiểu giới hạn của fake: suppressed exception cho biết việc cleanup cũng lỗi, nhưng không chứng minh transaction được rollback hay dữ liệu đã được flush thành công.

### Kiến thức cần có

Bạn nên biết cơ bản về `try`, `catch`, `finally`, checked exception, `IOException` và giao diện `AutoCloseable`. Ví dụ chính dùng cú pháp Java 17 nhưng **không cần thư viện ngoài**, có thể chạy bằng `javac --release 17`. Những đoạn JUnit 5 được đánh dấu rõ là fragment cần một dự án đã cấu hình JUnit.

**Vấn đề trọng tâm:** một thao tác có thể xuất hiện nhiều lỗi cùng lúc, nhưng caller cần một kết cục chính có ý nghĩa. Những lỗi phụ không được phép vô tình xóa hoặc thay thế nguyên nhân chính.

## 2. Primary exception và suppressed exception là gì?

Trong cấu trúc `try-with-resources`, Java đảm nhiệm việc gọi `close()` khi thoát phạm vi. Nếu có nhiều exception, một exception là **primary** và những exception phát sinh trong quá trình cleanup có thể được **đính kèm** qua `Throwable.addSuppressed`.

Ví dụ tình huống:

~~~text
Mở A                       OK
Mở B                       OK
Mở C                       OK
Thực hiện body             IOException("body failed")
Đóng C                     IOException("close C failed")
Đóng B                     IOException("close B failed")
Đóng A                     IOException("close A failed")

Exception được ném ra ngoài:
    primary:    IOException("body failed")
    suppressed: IOException("close C failed")
                IOException("close B failed")
                IOException("close A failed")
~~~

Đây là **sơ đồ mô tả tình huống thực hành**, không phải output chính thức của chương trình. Nó minh họa hai quy tắc:

- Nếu **body đã ném lỗi**, lỗi đó giữ vai trò chính; các lỗi `close()` sau đó được thêm vào `getSuppressed()`.
- Nếu **body hoàn thành bình thường**, lỗi `close()` đầu tiên theo thứ tự đóng ngược trở thành primary; những lỗi đóng tiếp theo được suppress vào nó.

Phần suppressed là **dữ liệu chẩn đoán thật**, không phải “exception đã biến mất và có thể quên”. Nó có thể phản ánh lỗi flush dữ liệu, giải phóng kết nối, trả tài nguyên về pool hoặc đóng một handle khác.

### 2.1. Cause khác suppressed như thế nào?

| Quan hệ | API Java | Ý nghĩa phổ biến |
| --- | --- | --- |
| **Cause** | `getCause()`, constructor chứa `cause` | Ngoại lệ bên trong là nguyên nhân của exception bên ngoài |
| **Suppressed** | `getSuppressed()`, `addSuppressed()` | Ngoại lệ phụ xảy ra cùng lúc với một lỗi chính, thường khi cleanup |
| **Stack trace** | `getStackTrace()` | Những frame được ghi nhận cho từng exception |

Ví dụ `new IllegalStateException("Service failed", ioError)` tạo exception mới có cause là `ioError`. Nếu `ioError` có ba suppressed, ba lỗi này **vẫn thuộc `ioError`**. Không mặc định chúng được chép lên `IllegalStateException`.

Một primary có thể **đồng thời** có cause và suppressed. Khi chẩn đoán, phải đi theo cả hai nhánh thông tin, không chỉ `getCause()`.

## 3. Vì sao cần dùng fake resource để tái hiện?

Để tạo ra lỗi ở đúng thời điểm, đừng phụ thuộc vào việc filesystem hết dung lượng hoặc server đóng kết nối ngẫu nhiên. Điều đó làm test khó lặp lại và nguy hiểm cho dữ liệu. Thay vào đó, tạo một class có thể **chủ động ném exception** theo cờ cấu hình.

Một fake phục vụ mục tiêu này cần:

- Tên resource `A`, `B`, `C` để phân biệt.
- Danh sách `events` ghi thứ tự `open:A`, `close:C`, ...
- `failOpen` để làm constructor ném `IOException`.
- `failClose` để làm `close()` ném `IOException`.
- Thân công việc có thể chủ động ném exception riêng.
- Không dùng thread/sleep/network; trình tự sự kiện **deterministic**.
- Mỗi kịch bản có danh sách sự kiện mới để tránh kết quả bị nhiễm giữa các test.

Cấu hình lỗi tại **constructor** chỉ mô phỏng việc một initializer không hoàn tất. Nó chưa mô phỏng toàn bộ lỗi cấp phát dở bên trong constructor. Trường hợp tài nguyên mở nội bộ rồi constructor lỗi được bàn riêng ở phần sau.

## 4. Chương trình Java 17 hoàn chỉnh

Chương trình `SuppressedPracticeDemo` gồm bốn kịch bản độc lập:

1. `bodyAndThreeClosesFail()` — thân ném lỗi, ba `close()` cũng lỗi.
2. `secondOpenFails()` — A đã mở, B mở lỗi, Java phải đóng A.
3. `onlyClosesFail()` — thân thành công, nhưng ba `close()` cùng lỗi.
4. `wrappedFailure()` — lỗi đọc được wrap; suppressed vẫn nằm trên cause gốc.

Lưu toàn bộ code dưới đây thành file **`SuppressedPracticeDemo.java`**:

~~~java
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SuppressedPracticeDemo {
    static final class TrackedResource implements AutoCloseable {
        private final String name;
        private final List<String> events;
        private final boolean failClose;

        TrackedResource(String name, List<String> events,
                        boolean failOpen, boolean failClose) throws IOException {
            this.name = name;
            this.events = events;
            this.failClose = failClose;
            events.add("open:" + name);
            if (failOpen) {
                throw new IOException("open " + name + " failed");
            }
        }

        @Override
        public void close() throws IOException {
            events.add("close:" + name);
            if (failClose) {
                throw new IOException("close " + name + " failed");
            }
        }
    }

    static String identify(Throwable error) {
        return error.getClass().getSimpleName() + ": " + error.getMessage();
    }

    static void report(String title, List<String> events, Throwable error) {
        System.out.println(title);
        System.out.println("events=" + String.join(", ", events));
        System.out.println("primary=" + identify(error));
        Throwable[] suppressed = error.getSuppressed();
        System.out.println("suppressed-count=" + suppressed.length);
        for (int i = 0; i < suppressed.length; i++) {
            System.out.println("suppressed[" + i + "]=" + identify(suppressed[i]));
        }
    }

    static void bodyAndThreeClosesFail() {
        List<String> events = new ArrayList<>();
        IOException original = new IOException("body failed");
        try (TrackedResource a = new TrackedResource("A", events, false, true);
             TrackedResource b = new TrackedResource("B", events, false, true);
             TrackedResource c = new TrackedResource("C", events, false, true)) {
            events.add("work");
            throw original;
        } catch (IOException error) {
            report("CASE body-and-close", events, error);
            System.out.println("same-primary-object=" + (error == original));
        }
    }

    static void secondOpenFails() {
        List<String> events = new ArrayList<>();
        try (TrackedResource a = new TrackedResource("A", events, false, true);
             TrackedResource b = new TrackedResource("B", events, true, false);
             TrackedResource c = new TrackedResource("C", events, false, true)) {
            events.add("work");
        } catch (IOException error) {
            report("CASE open-failure", events, error);
        }
    }

    static void onlyClosesFail() {
        List<String> events = new ArrayList<>();
        try (TrackedResource a = new TrackedResource("A", events, false, true);
             TrackedResource b = new TrackedResource("B", events, false, true);
             TrackedResource c = new TrackedResource("C", events, false, true)) {
            events.add("work:ok");
        } catch (IOException error) {
            report("CASE close-only", events, error);
        }
    }

    static void wrappedFailure() {
        List<String> events = new ArrayList<>();
        try (TrackedResource a = new TrackedResource("A", events, false, true)) {
            events.add("work");
            throw new IOException("read failed");
        } catch (IOException original) {
            IllegalStateException wrapper =
                    new IllegalStateException("service failed", original);
            report("CASE wrapped", events, wrapper);
            System.out.println("cause=" + identify(wrapper.getCause()));
            System.out.println("cause-suppressed-count="
                    + wrapper.getCause().getSuppressed().length);
        }
    }

    public static void main(String[] args) {
        bodyAndThreeClosesFail();
        secondOpenFails();
        onlyClosesFail();
        wrappedFailure();
    }
}
~~~

### 4.1. Biên dịch và chạy

Cần JDK 17 hoặc mới hơn. Trong thư mục chứa file:

~~~bash
javac --release 17 SuppressedPracticeDemo.java
java SuppressedPracticeDemo
~~~

**Stdout chính xác đã kiểm chứng bằng biên dịch và thực thi thực tế:**

~~~text
CASE body-and-close
events=open:A, open:B, open:C, work, close:C, close:B, close:A
primary=IOException: body failed
suppressed-count=3
suppressed[0]=IOException: close C failed
suppressed[1]=IOException: close B failed
suppressed[2]=IOException: close A failed
same-primary-object=true
CASE open-failure
events=open:A, open:B, close:A
primary=IOException: open B failed
suppressed-count=1
suppressed[0]=IOException: close A failed
CASE close-only
events=open:A, open:B, open:C, work:ok, close:C, close:B, close:A
primary=IOException: close C failed
suppressed-count=2
suppressed[0]=IOException: close B failed
suppressed[1]=IOException: close A failed
CASE wrapped
events=open:A, work, close:A
primary=IllegalStateException: service failed
suppressed-count=0
cause=IOException: read failed
cause-suppressed-count=1
~~~

Đây là output của chính chương trình nêu trên khi chạy độc lập, không dựa vào đường dẫn máy, địa chỉ mạng hay thời điểm scheduler. Nếu bạn đổi tên resource, số lượng resource hoặc chuỗi exception, output thay đổi tương ứng.

### 4.2. Các yếu tố làm ví dụ deterministic

- `events` được ghi trong cùng một thread. Không có executor, không có callback async, không có phụ thuộc scheduling.
- Mỗi phương thức tự tạo `new ArrayList<>()`, do đó lịch sử của kịch bản này không dính vào kịch bản khác.
- Mỗi `TrackedResource.close()` luôn ghi sự kiện **trước khi** ném lỗi (nếu được cấu hình).
- Mỗi exception close có message chứa resource name.
- Thứ tự đóng được **ngôn ngữ Java xác định**: C → B → A.
- `IOException original` ở kịch bản đầu được tạo trước và ném trực tiếp, nên có thể kiểm tra **object identity** bằng `error == original`.

Điều này giúp test chỉ thất bại khi behavior thực sự khác kỳ vọng, không phải vì máy nhanh/chậm.

## 5. Phân tích kịch bản 1: một primary và ba suppressed

Trong `bodyAndThreeClosesFail()`, ba resource đều khởi tạo thành công. Thân `try` ghi `work` rồi ném **chính đối tượng** `original`.

Diễn biến:

~~~text
open A                  thành công
open B                  thành công
open C                  thành công
work                    ghi event
throw original          IOException("body failed")
        |
        v
close C                 ném IOException("close C failed")
        |               -> original.addSuppressed(closeC)
        v
close B                 ném IOException("close B failed")
        |               -> original.addSuppressed(closeB)
        v
close A                 ném IOException("close A failed")
                        -> original.addSuppressed(closeA)
        |
        v
catch (IOException e)
e == original           true
e.getSuppressed()       [closeC, closeB, closeA]
~~~

Quan sát:
- `primary=IOException: body failed` — body failure giữ vai trò chính.
- `suppressed-count=3` — ba lỗi close vẫn được giữ.
- `suppressed[0]` là C, `[1]` là B, `[2]` là A — đúng với thứ tự đóng ngược.
- `same-primary-object=true` — Java không bắt buộc tạo một wrapper mới chỉ vì cleanup lỗi; trong kịch bản này primary chính là object được body ném.

**Điểm học quan trọng:** một exception có thể chứa **nhiều** suppressed, không chỉ một. Nếu code điều tra chỉ gọi `getSuppressed()[0]`, bạn sẽ bỏ sót B và A. Nếu code log chỉ in `error.getMessage()`, thông tin đóng thất bại biến mất khỏi báo cáo.

### 5.1. Điều gì sẽ xảy ra nếu chỉ có hai close lỗi?

Nếu đổi `failClose` của resource B thành `false`, Java vẫn gọi B.close() nhưng nó không ném. Khi đó:
- Event log **vẫn có** `close:B`.
- Primary vẫn là `body failed`.
- Suppressed chỉ chứa close C và close A.
- Thứ tự suppressed là C rồi A; **không có phần tử placeholder** cho B thành công.

Vì vậy không thể dùng độ dài mảng suppressed để suy ra số resource đã đóng. Nó chỉ phản ánh số lỗi phụ được đính kèm, không phải tổng số lần cleanup.

## 6. Phân tích kịch bản 2: lỗi initializer và suppressed từ resource trước

Trong `secondOpenFails()`:
- A mở thành công, nhưng được cấu hình `failClose=true`.
- Constructor B ghi `open:B` rồi ném `IOException("open B failed")`.
- C chưa bao giờ được mở.
- Body không được thực thi.
- Java đóng A; A.close() ném lỗi và được suppress vào lỗi open B.

Sơ đồ:

~~~text
try (A = new Resource(...);
     B = new Resource(...);
     C = new Resource(...)) {
    work();
}

A tạo OK
B constructor throw   <-- primary
C không được tạo
body không chạy
A.close() throw        <-- suppressed
~~~

Vì vậy stdout có:

~~~text
events=open:A, open:B, close:A
primary=IOException: open B failed
suppressed-count=1
suppressed[0]=IOException: close A failed
~~~

**Không xuất hiện `close:B`** vì constructor B không trả về một resource hoàn chỉnh để try-with-resources đăng ký đóng. Không xuất hiện `open:C` hoặc `work` vì Java ngừng đánh giá các initializer tiếp theo khi B ném.

### 6.1. Nếu resource đang mở dở bên trong constructor?

Đây là tình huống khác với A đã mở hoàn chỉnh rồi B thất bại. Ví dụ B constructor mở một file nội bộ, sau đó đọc header bị lỗi. Try-with-resources **không tự gọi `B.close()`** nếu constructor B chưa hoàn thành.

Constructor/factory tạo B phải có **cleanup nội bộ khi partial acquisition thất bại**. Một thiết kế factory có thể sử dụng try/catch để đóng handle mới tạo trước khi ném exception, giữ lỗi init làm chính và thêm lỗi close bằng `addSuppressed()`.

Tuy nhiên cách chuyển quyền sở hữu phải được thiết kế cẩn thận: nếu factory trả về object thành công, object mới chịu trách nhiệm close; nếu factory thất bại, factory vẫn sở hữu và phải dọn những handle đã tạo. Test fake nên tách trường hợp **initializer B không hoàn tất** khỏi trường hợp **resource A đóng bị lỗi sau đó**.

## 7. Phân tích kịch bản 3: body thành công nhưng close thất bại

Trong `onlyClosesFail()`, body chỉ ghi `work:ok` và không ném exception. Nhưng khi thoát scope:

1. C.close() được gọi đầu tiên và ném `IOException("close C failed")`.
2. Java ghi nhận close C là **primary** vì chưa có body exception.
3. B.close() được gọi tiếp, lỗi của nó bị suppress vào primary C.
4. A.close() cũng được gọi và bị suppress.

Output trọng tâm:

~~~text
events=open:A, open:B, open:C, work:ok, close:C, close:B, close:A
primary=IOException: close C failed
suppressed-count=2
suppressed[0]=IOException: close B failed
suppressed[1]=IOException: close A failed
~~~

Đây là sự khác biệt quan trọng với kịch bản 1. **Primary không nhất thiết là exception từ body.** Nó là lỗi đầu tiên có vai trò chính theo luồng thực thi/cleanup của try-with-resources. Khi body thành công và close C lỗi, C trở thành primary.

Nếu body chạy hết mà code lại trả `"SUCCESS"` trong khi `close()` bị catch rỗng, caller có thể nhận thông báo sai. Với một số output stream, lỗi close còn có thể ảnh hưởng đến dữ liệu buffered chưa flush. Nhưng không được suy diễn rằng **mọi** close failure đều làm dữ liệu mất: điều đó tùy hợp đồng resource.

### 7.1. Trường hợp chỉ một close lỗi

Nếu C.close() thành công nhưng B.close() thất bại, B là primary. Nếu A.close() thất bại sau đó, A là suppressed. Nếu cả C/B/A đều thành công, không có exception từ cleanup; kết quả body có thể được trả bình thường.

Bảng kiểm tra nhanh:

| Body | Close C | Close B | Close A | Primary | Suppressed |
| --- | --- | --- | --- | --- | --- |
| Lỗi X | Lỗi C | Lỗi B | Lỗi A | X | C, B, A |
| Lỗi X | OK | Lỗi B | OK | X | B |
| OK | Lỗi C | Lỗi B | Lỗi A | C | B, A |
| OK | OK | Lỗi B | Lỗi A | B | A |
| OK | OK | OK | Lỗi A | A | Không có |
| OK | OK | OK | OK | Không có | Không có |

Thứ tự cột trong bảng là thứ tự **đóng thực tế**. Lưu ý khác thứ tự khai báo resource ở header.

## 8. Phân tích kịch bản 4: wrapper không tự mang suppressed của cause

Trong `wrappedFailure()`:
1. A mở bình thường.
2. Body ném `IOException("read failed")`.
3. A.close() ném `IOException("close A failed")`.
4. Primary gốc `read failed` có **một suppressed** là `close A failed`.
5. `catch` tạo `new IllegalStateException("service failed", original)`.
6. Chương trình in thông tin của **wrapper** và thông tin của **cause**.

Output:

~~~text
CASE wrapped
events=open:A, work, close:A
primary=IllegalStateException: service failed
suppressed-count=0
cause=IOException: read failed
cause-suppressed-count=1
~~~

Điều cần hiểu:
- `wrapper.getSuppressed()` rỗng trong kịch bản này, **không có nghĩa cleanup đã thành công**.
- `wrapper.getCause()` chính là IOException gốc; `wrapper.getCause().getSuppressed()` có một lỗi.
- Java **không tự sao chép** suppressed từ cause lên wrapper mới.
- Một hệ thống log phải in **cause chain và suppressed của từng throwable** nếu muốn đầy đủ bằng chứng.

Đừng sửa bằng cách “copy toàn bộ suppressed lên mọi wrapper” một cách máy móc; điều đó có thể tạo duplicate, nhầm quan hệ nguyên nhân hoặc làm báo cáo khó hiểu. Giữ cause đúng là cách tự nhiên để bảo toàn thông tin.

### 8.1. Cách truy cập đúng khi wrapper có cause

Fragment minh họa:

~~~java
Throwable primary = wrapper.getCause();
if (primary != null) {
    for (Throwable cleanupFailure : primary.getSuppressed()) {
        System.err.println("cleanup: " + cleanupFailure);
    }
}
~~~

`wrapper` giả định đã được tạo từ một exception có cause. Kiểm tra null là cần thiết vì không phải Throwable nào cũng có cause. Với một số exception lồng nhiều tầng, cần đi theo các cause khác nhau và kiểm tra suppressed của **đúng Throwable** chứ không chỉ wrapper ngoài cùng.

## 9. Ma trận kiểm thử nên thực hiện

Đây là những trường hợp quan trọng khi viết test về lifecycle:

| Case | Chương trình mô phỏng | Điều cần assert |
| --- | --- | --- |
| 1 | A/B/C mở thành công, body ném, C/B/A close đều ném | Body là primary, suppressed C/B/A |
| 2 | A mở, B initializer ném, A.close ném | Init B là primary, suppressed A |
| 3 | A/B/C mở thành công, body OK, C/B/A close ném | Close C primary, suppressed B/A |
| 4 | Body ném, close A ném, bọc lỗi vào IllegalStateException | Wrapper cause là body IOException; suppressed ở cause |
| 5 | Body ném, tất cả close OK | Body primary, suppressed rỗng |
| 6 | Body OK, tất cả close OK | Không exception; mọi resource đóng |
| 7 | A init ném ngay | Không mở B/C; body không chạy |
| 8 | C init ném sau A/B mở | B rồi A phải được đóng |
| 9 | C init ném, B/A close đều ném | Init C primary, suppressed B/A |
| 10 | Body OK, chỉ A.close ném | A primary, suppressed rỗng |
| 11 | Body ném, chỉ B.close ném | Body primary, suppressed B |
| 12 | Body có return, close ném | Close exception vẫn có thể ngăn caller nhận return bình thường |

Không cần viết lại toàn bộ chương trình cho từng case. Với fake có cờ failOpen/failClose, bạn đổi cấu hình từng resource và hành vi body. Nhưng hãy tạo **event list mới** và resource mới cho từng test để tránh Throwable/state dùng chung làm test phụ thuộc thứ tự.

### 9.1. Vì sao test chỉ `assertThrows` là chưa đủ?

Một test:

~~~java
assertThrows(IOException.class, () -> process());
~~~

Đây là **fragment JUnit 5**, cần `process()` và JUnit Jupiter. Nó chỉ chứng minh có một IOException thoát ra; nó không cho biết IOException từ body, init hay close, không chứng minh tất cả resource đã được thử đóng và không kiểm tra suppressed.

Một test tốt phải kiểm tra:
- Loại và identity/message của primary.
- Số lượng và thứ tự suppressed.
- Event log khởi tạo, sử dụng, cleanup.
- Không có body event nếu initializer thất bại.
- Resource nào **đã được** và **chưa được** khởi tạo.
- Các hậu điều kiện còn đúng sau lỗi.

## 10. Viết JUnit 5 assertion cho nhiều suppressed

Đoạn sau là **fragment test** giả định `SuppressedPracticeDemo.TrackedResource` nằm cùng package và test đã có JUnit Jupiter. Nó sử dụng `assertSame` để khẳng định đúng object lỗi thân, không chỉ cùng message.

~~~java
@Test
void bodyExceptionStaysPrimaryAndAllCloseFailuresAreSuppressed() {
    List<String> events = new ArrayList<>();
    IOException original = new IOException("body failed");

    IOException thrown = assertThrows(IOException.class, () -> {
        try (var a = new SuppressedPracticeDemo.TrackedResource(
                     "A", events, false, true);
             var b = new SuppressedPracticeDemo.TrackedResource(
                     "B", events, false, true);
             var c = new SuppressedPracticeDemo.TrackedResource(
                     "C", events, false, true)) {
            events.add("work");
            throw original;
        }
    });

    assertSame(original, thrown);
    assertEquals(3, thrown.getSuppressed().length);
    assertEquals("close C failed",
                 thrown.getSuppressed()[0].getMessage());
    assertEquals("close B failed",
                 thrown.getSuppressed()[1].getMessage());
    assertEquals("close A failed",
                 thrown.getSuppressed()[2].getMessage());
    assertIterableEquals(
            List.of("open:A", "open:B", "open:C", "work",
                    "close:C", "close:B", "close:A"),
            events);
}
~~~

Để đưa đoạn test này vào class JUnit 5, cần:
- `import org.junit.jupiter.api.Test;`
- `import java.util.List;`, `import java.util.ArrayList;`, `import java.io.IOException;`
- Static imports `assertThrows`, `assertSame`, `assertEquals`, `assertIterableEquals` từ `org.junit.jupiter.api.Assertions`.
- Class `SuppressedPracticeDemo` và nested `TrackedResource` có thể truy cập trong test (ví dụ cùng package).

### 10.1. Tại sao `assertSame` đáng giá?

Nếu chỉ dùng:

~~~java
assertEquals("body failed", thrown.getMessage());
~~~

bạn có thể đang kiểm tra **một wrapper mới có cùng message**. `assertSame(original, thrown)` chứng minh try-with-resources giữ đúng đối tượng exception được body ném, và các lỗi close được gắn trực tiếp lên đối tượng đó.

Điều này phù hợp cho **bài thực hành về Java exception mechanics**. Trong test nghiệp vụ thông thường, không phải API nào cũng cam kết caller nhận đúng cùng object Throwable; có thể có wrapper hợp lệ. Hãy assert identity khi chính contract cần điều đó.

## 11. Test lỗi khởi tạo resource B

Fragment JUnit 5:

~~~java
@Test
void failedSecondInitializerClosesFirstResource() {
    List<String> events = new ArrayList<>();

    IOException thrown = assertThrows(IOException.class, () -> {
        try (var a = new SuppressedPracticeDemo.TrackedResource(
                     "A", events, false, true);
             var b = new SuppressedPracticeDemo.TrackedResource(
                     "B", events, true, false);
             var c = new SuppressedPracticeDemo.TrackedResource(
                     "C", events, false, true)) {
            events.add("work");
        }
    });

    assertEquals("open B failed", thrown.getMessage());
    assertEquals(1, thrown.getSuppressed().length);
    assertEquals("close A failed",
                 thrown.getSuppressed()[0].getMessage());
    assertIterableEquals(
            List.of("open:A", "open:B", "close:A"), events);
}
~~~

Test này đặc biệt có ích vì nó phân biệt hai trách nhiệm:
- Java tự đóng những resource **đã được khởi tạo thành công trước đó**.
- Code khởi tạo của B chịu trách nhiệm xử lý **những tài nguyên nội bộ của B được cấp phát dở**, nếu có.

Không nên kỳ vọng `close:B` trong event log. Constructor B đã ném trước khi một instance hợp lệ được gán cho biến resource của try-with-resources.

### 11.1. Biến thể C init lỗi

Chuyển `failOpen=true` sang C, cho A/B `failClose=true`. Expected:
- Events: `open:A`, `open:B`, `open:C`, `close:B`, `close:A`.
- Primary: `open C failed`.
- Suppressed: `close B failed` rồi `close A failed`.
- Không có `work`; không có `close:C`.

Đây là bài tập mở rộng có thể dự đoán từ quy tắc, không cần đoán runtime.

## 12. Test lỗi chỉ xuất hiện ở `close()`

Fragment JUnit 5:

~~~java
@Test
void firstCloseFailureBecomesPrimaryWhenBodySucceeds() {
    List<String> events = new ArrayList<>();

    IOException thrown = assertThrows(IOException.class, () -> {
        try (var a = new SuppressedPracticeDemo.TrackedResource(
                     "A", events, false, true);
             var b = new SuppressedPracticeDemo.TrackedResource(
                     "B", events, false, true);
             var c = new SuppressedPracticeDemo.TrackedResource(
                     "C", events, false, true)) {
            events.add("work:ok");
        }
    });

    assertEquals("close C failed", thrown.getMessage());
    assertEquals(2, thrown.getSuppressed().length);
    assertEquals("close B failed",
                 thrown.getSuppressed()[0].getMessage());
    assertEquals("close A failed",
                 thrown.getSuppressed()[1].getMessage());
    assertIterableEquals(
            List.of("open:A", "open:B", "open:C", "work:ok",
                    "close:C", "close:B", "close:A"),
            events);
}
~~~

**Điểm cần nhấn:** mặc dù body đã hoàn thành, toàn bộ lời gọi `try-with-resources` vẫn có thể kết thúc bằng exception do `close()`. Không phải một ngoại lệ chỉ xảy ra “sau nghiệp vụ” thì mặc định có thể nuốt; với output stream, chính lúc đóng có thể phát hiện việc ghi chưa hoàn tất.

Ngược lại, không được kết luận close exception chắc chắn đã undo những thao tác trước đó. Để kiểm chứng dữ liệu, cần test với implementation thật hoặc mô hình trạng thái phù hợp.

## 13. Thử một lỗi giả “quan sát” nhưng không làm hỏng test

Khi nghiên cứu suppressed, hãy tạo ngoại lệ có thông điệp phân biệt:

~~~java
IOException body = new IOException("body failed");
IOException closeA = new IOException("close A failed");
IOException closeB = new IOException("close B failed");
~~~

Đây là fragment Java. Đừng tái sử dụng **cùng một đối tượng exception** cho body và close. Khi compiler sinh cleanup, nó có thể gọi `primary.addSuppressed(closeFailure)`. Nếu `closeFailure == primary`, `Throwable.addSuppressed` cấm self-suppression và có thể ném `IllegalArgumentException`, làm bài thử lệch khỏi tình huống intended.

Để kiểm tra **identity** của suppressed, có thể inject exception riêng cho từng resource:

~~~java
IOException errorB = new IOException("close B failed");
IOException errorA = new IOException("close A failed");
~~~

Fake hỗ trợ constructor nhận hai object này hoặc callback supplier phù hợp, và khi `close()` ném đúng object đã inject, test có thể `assertSame(errorB, suppressed[0])`. Tuy nhiên không cần phức tạp hóa ví dụ chính: kiểm tra type/message/order cùng event log đã diễn đạt hầu hết hành vi.

### 13.1. Vì sao không dùng một singleton Throwable cho cả suite?

`Throwable` là object **có trạng thái**: nó lưu stack trace, cause/suppressed và các thông tin liên quan. Tái sử dụng một exception instance giữa nhiều test có thể giữ các suppressed từ test trước, làm kết quả phụ thuộc thứ tự. Tạo instance mới cho từng case, đặc biệt khi đang dạy `getSuppressed()`.

## 14. Cách Java bảo toàn exception trong try-with-resources

Có thể hình dung Java biên dịch try-with-resources thành logic cleanup có tính chất sau:

~~~java
Throwable primary = null;
try {
    work();
} catch (Throwable thrown) {
    primary = thrown;
    throw thrown;
} finally {
    if (resource != null) {
        if (primary != null) {
            try {
                resource.close();
            } catch (Throwable closeFailure) {
                primary.addSuppressed(closeFailure);
            }
        } else {
            resource.close();
        }
    }
}
~~~

Đây là **pseudocode gần Java để giải thích một resource**, không phải bản dịch bytecode chính xác của mọi compiler/JDK và không phải đoạn code nên tự chép vào production. Với nhiều resource, compiler tạo cleanup lồng nhau để đóng theo thứ tự ngược. Ý nghĩa chính là:

- Nếu đã có primary từ body/initializer, close failure được gắn vào primary.
- Nếu chưa có primary, close failure có thể trở thành exception được ném ra.
- Các resource còn lại vẫn được cleanup theo cấu trúc lồng nhau.
- Compiler lo đúng quy tắc, tránh nhiều lỗi phổ biến của `finally` viết tay.

**Không nên dùng `catch (Throwable)` trong business code chỉ vì pseudocode ở đây làm vậy.** Đó là mô tả cách ngôn ngữ giữ tín hiệu ngoại lệ, không phải hướng dẫn catch mọi `Error` rồi tiếp tục chạy.

### 14.1. Mã `finally` tự viết thường che lỗi chính

Sai lầm quen thuộc:

~~~java
try {
    throw new IOException("body failed");
} finally {
    throw new IOException("close failed");
}
~~~

Đây là **fragment trong phương thức khai báo `throws IOException`**. Exception ném từ `finally` có thể thay thế exception đang lan ra từ body. Nếu tự viết cleanup nhiều resource:

~~~java
try {
    work();
} finally {
    b.close();
    a.close();
}
~~~

Đây là fragment với các biến/hàm chưa định nghĩa. Nếu B.close() ném, lời gọi A.close() có thể không bao giờ diễn ra; hơn nữa lỗi body có thể bị che. Với `AutoCloseable`, ưu tiên try-with-resources trừ khi có yêu cầu lifecycle đặc biệt và đã kiểm thử đủ nhánh.

## 15. Suppressed và cause nằm ở đâu khi log?

Trong ví dụ `wrappedFailure`, có hai object:

~~~text
IllegalStateException("service failed")         <-- wrapper ngoài
    suppressed: []                               <-- rỗng
    cause ->
        IOException("read failed")               <-- exception gốc
            suppressed:
                IOException("close A failed")
~~~

Nếu log chỉ in `wrapper.getMessage()`, bạn mất cả cause và suppressed. Nếu code đọc `wrapper.getSuppressed()` rồi kết luận “không có lỗi close”, kết luận đó **sai đối tượng**.

Khi logger có khả năng in Throwable đầy đủ, truyền exception object (không chỉ message) để nó có cơ hội hiển thị stack trace, cause chain và suppressed. Với ứng dụng nhiều tầng, nên chọn nơi logging chịu trách nhiệm để tránh log lặp lại một lỗi ở mọi catch.

### 15.1. `printStackTrace()` hiển thị các quan hệ

Fragment:

~~~java
try {
    runOperation();
} catch (IOException e) {
    e.printStackTrace();
}
~~~

Đây là fragment với `runOperation` giả định. Trong trường hợp exception có suppressed, output thường có phần `Suppressed: ...` kèm các frame của lỗi phụ và có thể có `... N more` cho những frame chung. Đừng xem `... N more` là lỗi đã mất; đó thường là ký pháp rút gọn stack frames trùng nhau.

Trong production, cân nhắc logging framework và bảo vệ dữ liệu nhạy cảm thay vì in tự do trên stderr. Bài thực hành dùng `report` in định dạng ngắn để stdout tất định, nhưng **không thay thế full stack trace** khi cần điều tra một lỗi thật.

## 16. Kết quả sau lỗi đóng: cleanup không đồng nghĩa rollback

Một trình tự thực tế:

~~~text
Mở output stream
Ghi 1000 byte
Thân công việc kết thúc
close() -> IOException
~~~

Lỗi đóng có thể phản ánh flush buffer thất bại. Nhưng muốn biết 1000 byte đã tồn tại/bền vững bao nhiêu, bạn phải dựa vào contract `OutputStream` cụ thể, filesystem và kiểm thử tích hợp. Một fake ném exception tại close **không thể chứng minh nội dung file thật đã rollback**.

Với JDBC cũng tương tự:
- Connection.close() không phải lệnh thay thế cho transaction rollback theo mọi driver/pool.
- Nếu commit đã xảy ra trước close, lỗi close không tự undo commit.
- Nếu mất kết nối tại commit, trạng thái có thể chưa xác định; cần đối soát.

Khi exception chính báo thao tác thất bại, không tự kết luận “chưa có tác động ngoài hệ thống”. Khi nhiều suppressed liên quan resource khác nhau, log phải giữ đủ ngữ cảnh để biết tài nguyên nào cần cleanup/đối soát bổ sung.

### 16.1. Có nên retry close khi close ném?

Không có một đáp án đúng cho mọi `AutoCloseable`. Một close có thể đã giải phóng tài nguyên rồi mới ném do flush lỗi; retry close có thể không có ích hoặc thậm chí gây side effect mới. Một số implementation bảo đảm close idempotent, số khác không.

Test fake có thể mô phỏng closeCount và trạng thái đóng nếu hợp đồng sản phẩm yêu cầu. Đừng áp đặt “lỗi close = chắc chắn còn mở” hoặc “gọi lần hai luôn an toàn” chỉ từ interface `AutoCloseable`.

## 17. Thêm close counter và cờ trạng thái nếu cần

Ví dụ mở rộng class fake:

~~~java
private int closeCount;
private boolean closed;

@Override
public void close() throws IOException {
    closeCount++;
    closed = true;
    events.add("close:" + name);
    if (failClose) {
        throw new IOException("close " + name + " failed");
    }
}
~~~

Đây là **fragment để thêm vào fake**, không phải một class hoàn chỉnh. Nếu đánh dấu `closed=true` **trước khi ném**, fake đang mô phỏng resource trở thành closed dù close báo lỗi. Nếu đánh dấu sau bước có thể ném, fake mô phỏng không chuyển trạng thái khi close lỗi. Cả hai là **giả định mô hình**; chọn cái khớp contract của resource thật trong test.

Trong một test đơn luồng, một `int` đếm close là đủ. Với code chạy đồng thời, dùng đồng bộ hóa hoặc `AtomicInteger` và đọc sau khi worker thật sự hoàn tất. `Future.isCancelled()` không tự chứng minh close đã chạy xong.

## 18. Test lỗi khi khởi tạo dở bên trong resource

Giả sử cần mở một input stream rồi đọc header trước khi trả object:

~~~java
static ParsedFile openParsedFile(Path path) throws IOException {
    InputStream in = Files.newInputStream(path);
    try {
        Header header = readHeader(in);
        return new ParsedFile(in, header);
    } catch (IOException | RuntimeException failure) {
        try {
            in.close();
        } catch (IOException cleanupError) {
            failure.addSuppressed(cleanupError);
        }
        throw failure;
    }
}
~~~

Đây là **fragment thiết kế**, cần định nghĩa `ParsedFile`, `Header`, `readHeader` và các imports `Files`/`Path`/`InputStream`/`IOException`. Ý tưởng:
1. Factory sở hữu stream sau khi `Files.newInputStream` thành công.
2. Nếu parsing hoặc construction lỗi trước khi ownership được chuyển, factory đóng stream.
3. Nếu close stream cũng lỗi, giữ parsing/construction exception làm primary và thêm close error làm suppressed.
4. Nếu trả object thành công, `ParsedFile` nhận trách nhiệm đóng stream trong `close()`.

Cần xác định thời điểm chuyển quyền sở hữu rất rõ. Nếu constructor `ParsedFile` ném **sau khi đã nhận stream**, code factory phải vẫn xử lý cleanup khi chưa có kết quả trả về. Trường hợp khởi tạo dở là lý do test resource initializer khác với test body failure.

### 18.1. Bài tập nhỏ về khởi tạo dở

Tạo `FakeInputStream` ghi sự kiện:
- `open:stream`;
- `readHeader` ném `IOException("invalid header")`;
- `close:stream` ném `IOException("stream close failed")`.

Expected: lỗi `invalid header` là primary, lỗi `stream close failed` là suppressed. Đồng thời hãy chắc chắn không có object `ParsedFile` được trả về. Đây là một test **factory cleanup**, không phải bằng chứng TWR sẽ close một object chưa khởi tạo xong.

## 19. Đừng tự gộp mọi exception vào một kết quả “thành công”

Nếu code:

~~~java
try (Resource a = openA();
     Resource b = openB()) {
    process(a, b);
} catch (IOException ignored) {
}
return true;
~~~

Đây là fragment giả định các type/hàm đã tồn tại. Nó có thể bỏ qua cả lỗi body và lỗi close/suppressed, rồi trả `true`. Caller mất khả năng phân biệt success với partial failure.

Nếu tầng hiện tại có quyền xử lý, có thể:
- Giữ và truyền exception cùng cause/suppressed.
- Chuyển sang kết quả có cấu trúc `Failure(code, cause)` nếu API sử dụng kiểu kết quả.
- Thực hiện cleanup/rollback hoặc compensation có kiểm soát rồi ghi trạng thái thực.
- Trả `UNKNOWN`/`PENDING` khi side effect bên ngoài chưa thể xác minh.

Không nên chuyển `IOException` thành `Optional.empty()` nếu empty nghĩa là không có dữ liệu; cũng không nên coi close failure là không quan trọng chỉ vì body đã chạy hết.

## 20. Thử nhiều resource mà không bỏ quên lỗi phụ

Một flow có ba resource có thể dùng thông tin:
- Resource ID: `A`/`B`/`C`.
- Loại lỗi: open/body/close.
- Vị trí lỗi chính.
- Danh sách suppressed với thứ tự và resource.
- Thời điểm resource đã hoàn thành init.
- Trạng thái side effect nếu có.
- Correlation ID/job ID trong test hệ thống nhiều request.

Với test đơn luồng và một event list, chỉ cần resource ID và message. Với hệ thống production, message kiểu `"close A failed"` có thể không đủ vì không biết A thuộc request nào. Đừng ghi token hoặc dữ liệu riêng tư chỉ để định danh resource; dùng ID kỹ thuật an toàn.

Một số Throwable ngoài đời có stack trace rút gọn do cấu hình runtime, nhưng `getSuppressed()` là API trực tiếp để đọc lỗi kèm theo. Nếu exception bị wrap nhiều tầng, hãy tìm đúng object primary ở nơi suppressed được gắn.

## 21. Bài tập nâng cao: nhiều suppressed nhưng không phải do close

`Throwable.addSuppressed` là API chung; dù try-with-resources là nguồn sử dụng quen thuộc nhất, ứng dụng cũng có thể chủ động bổ sung những lỗi thứ cấp vào một primary.

Ví dụ tổng hợp ba bước cleanup riêng:

~~~java
IOException primary = new IOException("operation failed");
try {
    cleanupCache();
} catch (IOException e) {
    primary.addSuppressed(e);
}
try {
    cleanupTempFile();
} catch (IOException e) {
    primary.addSuppressed(e);
}
throw primary;
~~~

Đây là **fragment** trong phương thức `throws IOException`, với hai hàm cleanup được giả định. Mẫu chỉ thích hợp nếu đã xác định:
- Primary là lỗi quan trọng nhất cần truyền.
- Hai cleanup không phụ thuộc nhau hoặc được phép thực hiện tiếp khi một bước lỗi.
- Mỗi exception là một object hợp lệ, không trùng primary.
- Không phải chuyển lifecycle của resource sang try-with-resources đơn giản hơn.
- Caller/logging nhận và xử lý lỗi được giữ, không catch rỗng ở tầng ngoài.

Tránh thêm vào primary chính object đó: `primary.addSuppressed(primary)` không hợp lệ. Tránh truyền null. Không giả định mọi Throwable đều bật suppression nếu có constructor tùy chỉnh vô hiệu hóa tính năng này.

## 22. Test bất đồng bộ khi exception bị bọc bởi Future

Nếu flow dùng fake chạy trong một `Callable` qua `ExecutorService.submit`, exception từ task có thể được truyền tới caller bằng `ExecutionException`. Lúc đó:
- `ExecutionException` là wrapper của việc quan sát thất bại qua Future.
- `wrapper.getCause()` là lỗi task ném ra.
- Suppressed của lỗi đóng thường nằm trên **cause**, không tự nằm trên `ExecutionException`.

Fragment JUnit 5:

~~~java
Future<?> future = executor.submit(() -> {
    try (Resource resource = openResource()) {
        doWork(resource);
    }
    return null;
});

ExecutionException wrapper = assertThrows(
        ExecutionException.class, future::get);

Throwable primary = wrapper.getCause();
Throwable[] cleanupFailures = primary.getSuppressed();
~~~

Đây là **fragment**, cần Java concurrency imports, JUnit Jupiter và các loại/hàm `Resource`, `openResource`, `doWork` đã định nghĩa. Trong test thật, nhớ shutdown executor trong cleanup hợp lệ. Không coi `Future.isDone() == true` là task thành công; nó có thể xong do failure/cancellation.

Nếu dùng `CompletableFuture.join()`, wrapper thường là `CompletionException` thay vì `ExecutionException`. Khi cần tìm suppressed, tiếp tục kiểm tra cause gốc. Một callback `whenComplete` ném exception mới có quy tắc ưu tiên riêng; nó không có nghĩa những lỗi observer tự động thành suppressed trên source.

### 22.1. Race với cancellation

`Future.cancel(true)` có thể yêu cầu interrupt nhưng không bảo đảm worker đã thực sự rời `try` và gọi `close()`. Muốn kiểm tra cleanup hoàn tất, dùng latch ở `finally` hoặc một kênh completion riêng. Đọc event log ngay sau khi `cancel()` trả `true` có thể quá sớm, gây test chập chờn. Đây không phải hạn chế của suppressed exceptions mà là vấn đề **vòng đời bất đồng bộ**.

## 23. Những lỗi phổ biến khi tự tái hiện suppressed exception

### 23.1. Chỉ tạo một lỗi đóng nhưng nghĩ có nhiều suppressed

Nếu body không ném và B.close() là close đầu tiên bị lỗi, nó sẽ là primary, **không phải suppressed**. Muốn nhiều suppressed, cần có body/initializer primary trước đó hoặc ít nhất một close failure primary rồi thêm các close failure tiếp theo.

### 23.2. Đóng theo thứ tự A rồi B rồi C

Try-with-resources với resource khai báo A/B/C đóng **C rồi B rồi A**. Nếu event log có thứ tự khác, hãy xem resource đã khai báo theo thứ tự nào, có code đóng thủ công hay không.

### 23.3. Kỳ vọng B.close chạy khi constructor B ném

Initializer không trả object thành công thì TWR không quản lý việc đóng chính B. Nó chỉ đóng các resource đã tạo thành công trước đó.

### 23.4. Chỉ kiểm tra exception ngoài cùng sau khi wrap

Nếu wrapper giữ cause, hãy xem `wrapper.getCause().getSuppressed()`. `wrapper.getSuppressed().length == 0` không chứng minh cleanup không lỗi.

### 23.5. Catch exception rồi ném wrapper không cause

~~~java
catch (IOException e) {
    throw new IllegalStateException("service failed");
}
~~~

Đây là fragment. Nó làm mất đường dẫn cause thông thường tới primary và suppressed, gây khó chẩn đoán. Dùng `new IllegalStateException("service failed", e)` khi bọc.

### 23.6. Catch và chỉ in `getMessage()`

`getMessage()` chỉ là message của một Throwable. Nó không tự liệt kê suppressed/cause/stack. Dùng logger có hỗ trợ Throwable đầy đủ hoặc đọc API trực tiếp.

### 23.7. Giả định mọi resource close lỗi đều chưa giải phóng

`close()` có thể ném trước hoặc sau khi giải phóng một phần tài nguyên. Trạng thái resource thực tùy contract; fake phải thể hiện đúng tình huống đang test.

### 23.8. Dùng cùng một exception ở hai vị trí ném

Làm vậy có thể kích hoạt self-suppression hoặc khiến suppressed từ test trước tồn tại trong test sau. Tạo exception riêng cho body và từng close.

### 23.9. Test chỉ dựa vào số dòng stack trace

Số dòng có thể đổi khi source được format/refactor. Bài thực hành cần assert semantics: primary identity/type, suppressed và event order, thay vì hardcode `Foo.java:47`.

### 23.10. Test không dọn executor hoặc tài nguyên thật

Khi test ngoài ví dụ đồng bộ, luôn quản lý vòng đời executor, temp directory và resource thật. Đừng cố gây lỗi đóng file bằng cách thay đổi permission/dung lượng một cách không kiểm soát trên hệ thống dùng chung.

## 24. Checklist hoàn thành bài thực hành

- [ ] Tạo fake implement `AutoCloseable`, có ID và lịch sử `open/close`.
- [ ] Bố trí ba tài nguyên A/B/C trong một try-with-resources.
- [ ] Body ném IOException có tên riêng.
- [ ] C/B/A close đều ném các IOException **khác object**.
- [ ] Xác nhận body failure là primary.
- [ ] Xác nhận `getSuppressed().length == 3`.
- [ ] Xác nhận suppressed đúng thứ tự C, B, A.
- [ ] Xác nhận `close:C`, `close:B`, `close:A` đều xuất hiện.
- [ ] Dùng `assertSame` để kiểm tra primary object nếu bài test cần identity.
- [ ] Mô phỏng B initializer thất bại, kiểm tra A vẫn đóng, C/body không chạy.
- [ ] Mô phỏng body thành công nhưng ba close fail, kiểm tra close C là primary.
- [ ] Tạo wrapper có cause và kiểm tra suppressed ở cause.
- [ ] Không viết cleanup tự làm mất primary hoặc bỏ quên resource.
- [ ] Không dùng exception singleton tái sử dụng giữa các case.
- [ ] Có ít nhất một test happy path để bảo đảm body và close bình thường.
- [ ] Phân biệt lỗi close với việc rollback/đảm bảo ghi dữ liệu.
- [ ] Chỉ kết luận test chạy đúng khi stdout/assertions phản ánh contract đã định.

## 25. Bài tập mở rộng có đáp án định hướng

### Bài A — Đổi resource B thành close thành công

Trong `bodyAndThreeClosesFail`, sửa B thành `failClose=false`, giữ body fail và A/C close fail. Trước khi chạy, dự đoán:
- Event log vẫn có `close:B`.
- Primary vẫn là `body failed`.
- Suppressed chỉ có `close C failed` và `close A failed`.
- Độ dài suppressed còn 2, đúng thứ tự C rồi A.

### Bài B — Thử C constructor lỗi sau A và B

A và B init thành công, C `failOpen=true`, A/B `failClose=true`. Dự đoán:
- Primary: `open C failed`.
- Suppressed: `close B failed`, `close A failed`.
- Event: `open:A` → `open:B` → `open:C` → `close:B` → `close:A`.
- Không có `close:C` và không có body event.

### Bài C — Không có body exception, chỉ B và A close lỗi

Cho C.close thành công, B/A.close ném. Dự đoán:
- Primary: `close B failed`.
- Suppressed: `close A failed`.
- Đầy đủ event close: C rồi B rồi A.

### Bài D — Một wrapper bên ngoài có nhiều cấp cause

Bọc IOException gốc bằng một `IllegalStateException`, rồi bọc tiếp bằng một `RuntimeException` khác có cause. Dùng `getCause()` hai lần để tìm original; sau đó đọc `getSuppressed()`. Không mong wrapper ngoài tự chứa các suppressed của original. Ghi một báo cáo ngắn phân biệt quan hệ cause và suppressed.

### Bài E — Mô phỏng side effect trước khi close lỗi

Fake có bộ đệm dữ liệu. Trong body ghi một record, sau đó close ném. Hãy xác định liệu record đã ghi vào state giả trước khi close hay chưa. Nếu yêu cầu “không thấy dữ liệu khi lỗi”, thêm staging/commit logic được kiểm thử rõ ràng. **Đừng giả định exception tự rollback state.**

## 26. Tổng kết

Để tái hiện chính xác **một lỗi chính kèm nhiều suppressed exception** trong Java, hãy tạo một fake `AutoCloseable` có thể ném lỗi khi đóng, đưa nhiều resource vào `try-with-resources` rồi chủ động ném exception từ thân hoặc một initializer.

Năm điều quan trọng nhất:

1. **Body/initializer đã ném lỗi:** lỗi đó là primary; close failures phía sau được lưu trong `getSuppressed()`.
2. **Body thành công:** lỗi close đầu tiên theo thứ tự đóng ngược là primary; các close failures tiếp theo là suppressed.
3. **Resource đóng theo thứ tự ngược:** với A/B/C được khai báo, Java đóng C/B/A; một close thất bại không ngăn compiler-generated cleanup thử đóng resource còn lại.
4. **Initializer thất bại:** resource đã mở thành công trước đó được cleanup; object chưa khởi tạo xong không tự được TWR gọi close.
5. **Wrapper có cause không tự sao chép suppressed:** muốn điều tra đủ lỗi, đọc cả `getCause()` lẫn `getSuppressed()` ở đúng đối tượng.

Một test tốt không dừng ở việc “có IOException”: nó kiểm tra **primary identity, số lượng và thứ tự suppressed, event log cùng trạng thái sau lỗi**. Đó là cách biến cơ chế exception handling của Java thành những bảo đảm có thể kiểm chứng, đồng thời tránh hứa quá mức về rollback hoặc thành công của tài nguyên bên ngoài.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-practice-007">Thiết kế API giữ trạng thái hợp lệ khi có lỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế API giữ trạng thái hợp lệ khi có lỗi

Một API Java không được xem là đúng chỉ vì nó ném ra exception có tên phù hợp. Sau khi một phương thức thất bại, đối tượng mà phương thức đang sửa có thể vẫn tồn tại, được gọi lại và được quan sát bởi những thành phần khác. Nếu ngoại lệ xuất hiện sau một phần thao tác cập nhật, API có nguy cơ để lại **trạng thái nửa chừng**, phá vỡ invariant, rò rỉ tài nguyên hoặc báo cho caller một kết quả không phản ánh điều đã xảy ra.

**Mục tiêu của thiết kế exception-safe API** là xác định và thực thi một hợp đồng rõ ràng: khi phương thức trả về hoặc ném exception, đối tượng còn ở những trạng thái hợp lệ nào; phần thay đổi nào đã được commit; tài nguyên nào đã được giải phóng; caller có thể thử lại, đọc tiếp hay buộc phải tạo đối tượng mới?

Ví dụ, một API quản lý tồn kho phải tránh trạng thái `availableStock = -3` nếu business invariant yêu cầu không âm. API chuyển tiền phải tránh trạng thái nguồn đã trừ tiền nhưng đích chưa cộng tiền **khi hợp đồng hứa tính nguyên tử**. Một service có thể thất bại khi gửi thông báo sau khi ghi dữ liệu: lúc đó việc ghi có thể đã thành công, nên không được tùy tiện nói rằng toàn bộ nghiệp vụ đã rollback.

Bài thực hành này dùng Java 17+ để thiết kế và kiểm tra ranh giới *validate → prepare → commit*, mức bảo đảm exception, quản lý tài nguyên, cập nhật nhiều bước, publication và concurrency. Các ví dụ độc lập có lệnh biên dịch/chạy và stdout xác định; các đoạn cần framework/database ngoài JDK được đánh dấu là minh họa.

## 1. Trạng thái hợp lệ là một hợp đồng, không chỉ là vài field

Trước khi viết method, hãy ghi rõ **invariant**: điều kiện phải đúng tại các thời điểm mà caller được phép quan sát đối tượng.

Ví dụ với kho đơn giản:

- `available >= 0`.
- Mỗi reservation có định danh không rỗng và số lượng dương.
- Một reservation ID không được tạo trùng nếu contract yêu cầu duy nhất.
- Tất cả reservation đã commit nhất quán với số lượng hàng còn lại.
- Khi gọi method thất bại theo strong guarantee, snapshot trạng thái sau phải bằng snapshot trước.
- Khi kết thúc thao tác, resource được method sở hữu không còn bị bỏ quên.
- Nếu object ở trạng thái `CLOSED`, các thao tác đòi `OPEN` phải bị từ chối rõ ràng.

Các invariant là **giả định thiết kế của API cụ thể**. Một hệ thống cho phép backorder có thể cho tồn kho âm có kiểm soát; khi đó yêu cầu `available >= 0` sẽ không còn đúng. Đừng copy invariant từ ví dụ rồi áp đặt lên một domain khác.

Đặt câu hỏi cho mỗi phương thức public:

| Câu hỏi | Ví dụ quyết định hợp đồng |
| --- | --- |
| Precondition là gì? | `quantity > 0`, `orderId` không blank, object chưa closed. |
| State nào được thay đổi? | Tồn kho, danh sách reservation, version và event pending. |
| Khi nào commit? | Sau khi validate và dựng candidate hoàn chỉnh. |
| Nếu validate lỗi? | Ném `IllegalArgumentException`, state không đổi. |
| Nếu thiếu tồn kho? | Ném domain exception, state không đổi. |
| Nếu tạo candidate lỗi? | Không publish candidate; state cũ giữ nguyên. |
| Nếu dependency bên ngoài lỗi? | Theo transaction/operation ID; có thể phải báo UNKNOWN. |
| Ai sở hữu resource? | Scope tạo resource đóng đúng resource đó. |
| Caller làm gì sau lỗi? | Có thể dùng object tiếp, retry có điều kiện hoặc chuyển sang FAILED. |

Nếu những câu hỏi này chưa có câu trả lời, thêm `catch` để “xử lý exception” thường chỉ che giấu vấn đề.

## 2. Ba mức exception safety cần phân biệt

Những khái niệm sau rất hữu ích khi mô tả hợp đồng API, dù Java không cung cấp annotation chuẩn để tự động thực thi chúng.

| Mức | Điều phải đúng sau exception | Ví dụ |
| --- | --- | --- |
| **Strong guarantee** | Thất bại không làm thay đổi trạng thái quan sát được trong phạm vi đã cam kết. | `reserve` từ chối yêu cầu vượt tồn kho mà không thêm reservation và không trừ stock. |
| **Basic guarantee** | Invariant vẫn đúng, tài nguyên không rò rỉ, nhưng trạng thái có thể đã có tiến độ một phần được cho phép. | `importBatch` giữ 7 record thành công, lỗi ở record thứ 8, và báo checkpoint để tiếp tục. |
| **No-throw guarantee** | Một thao tác được thiết kế không ném trong phạm vi hợp đồng; thường áp dụng cho bước cleanup đơn giản, đáng tin cậy. | Cập nhật cờ `closed` in-memory không làm I/O và không ném. |

### Strong không có nghĩa mọi thứ trên thế giới đều rollback

Nếu API chỉ cam kết trạng thái **in-memory của object**, một file/HTTP request đã gửi trước lỗi không tự được thu hồi. Cần ghi ranh giới của strong guarantee rõ ràng: object state, một transaction DB, hay cả một workflow có cơ chế bù.

### Basic không có nghĩa object được phép hỏng

Một batch import có thể giữ record đã ghi thành công, nhưng index/counter/list phải nhất quán. Caller cần biết phần đã hoàn thành và phần chưa hoàn thành. Trạng thái nửa khởi tạo, số lượng âm sai invariant hoặc resource leak không phải là “basic guarantee”.

### No-throw phải dựa trên implementation thật

`AutoCloseable.close()` có thể ném exception. Một thao tác ghi file, flush buffer hoặc đóng network connection không tự động là no-throw. Chỉ công bố mức không ném khi có cơ sở và đã xét những tình huống cần xử lý lỗi tài nguyên.

## 3. Anti-pattern: thay đổi trạng thái trước khi xác minh

Một lỗi cơ bản:

```java
void reserve(int quantity) {
    available -= quantity;
    if (available < 0) {
        throw new IllegalStateException("insufficient stock");
    }
}
```

Đây là đoạn minh họa, giả định `available` là field. Nếu có 5 sản phẩm và caller đặt 7, method ném đúng exception nhưng để `available=-2`.

Một lỗi tinh vi hơn:

```java
void reserve(String id, int quantity) {
    reservations.add(id);
    validateQuantity(quantity);
    available -= quantity;
}
```

Đây là snippet chưa độc lập. Nếu `validateQuantity` ném, danh sách reservation đã bị sửa. Nếu `available -= quantity` thực thi sau nhiều lời gọi phụ có thể lỗi, trạng thái càng khó đối soát.

**Quy tắc thực hành:** kiểm tra tất cả precondition và trạng thái nghiệp vụ có thể kiểm tra trước khi mutate; chuẩn bị dữ liệu mới ngoài state chính; chỉ commit sau khi chuẩn bị thành công. Nhưng cũng phải xét exception đến từ **bước sau commit**: logging, callback, persistence hoặc event publication có thể phá giả định “nếu ném thì không đổi”.

## 4. Khuôn thiết kế validate → prepare → commit

Mô hình:

```text
Trạng thái S0
    |
    +--> VALIDATE (đọc, kiểm tra precondition/invariant)
    |       \-- lỗi: throw, S0 không đổi
    |
    +--> PREPARE (tạo các cấu trúc/value object mới)
    |       \-- lỗi: bỏ candidate, S0 không đổi
    |
    +--> COMMIT (publish state mới trong điểm được kiểm soát)
    |       \-- đã commit: caller phải biết semantics sau commit
    |
    +--> AFTER-COMMIT (outbox/notification theo contract)
            \-- lỗi: không tùy tiện tuyên bố transaction rollback
```

Khi giữ toàn bộ mutable state trong một immutable value object, commit có thể là **một lần thay reference**. Với state cập nhật đồng thời, thao tác commit còn cần lock/CAS/transaction phù hợp. Một reference assignment tự nó không xử lý race khi nhiều thread cùng đọc S0.

Một ví dụ sketch:

```java
State original = state;
validate(input, original);
State candidate = calculateNewState(original, input);
state = candidate; // Commit sau khi prepare thành công.
```

Đây là đoạn minh họa; `State`, `input`, `validate` và `calculateNewState` được định nghĩa ở nơi khác. Nếu `calculateNewState` chỉ dựng dữ liệu local và không tác động bên ngoài, thất bại trước gán giữ state gốc. Nếu hàm prepare lại tự gọi HTTP để thực hiện thanh toán, nó **không còn side-effect-free**, và strong guarantee của cả workflow không còn được bảo đảm chỉ bằng câu lệnh gán cuối.

## 5. Ví dụ Java 17 hoàn chỉnh: thiết kế Inventory exception-safe

Chương trình sau dùng `record` bất biến làm state, danh sách reservation không chỉnh sửa được và `synchronized` để serialize lời gọi vào cùng Inventory. Nó mô phỏng ba trường hợp: không đủ hàng, lỗi được chèn **trước commit**, và thao tác thành công. Mọi exception xảy ra **trước commit** giữ snapshot cũ.

Tạo file `ExceptionSafeInventoryDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ExceptionSafeInventoryDemo {

    private record State(int available, List<String> reservations) {
        State {
            if (available < 0) {
                throw new IllegalArgumentException("negative stock");
            }
            reservations = List.copyOf(reservations);
        }
    }

    private static final class Inventory {
        private State state = new State(5, List.of());

        synchronized State snapshot() {
            return state;
        }

        synchronized void reserve(
                String orderId, int quantity, boolean failBeforeCommit) {
            Objects.requireNonNull(orderId, "orderId");

            if (orderId.isBlank() || quantity <= 0) {
                throw new IllegalArgumentException("invalid reservation");
            }

            State old = state;
            if (quantity > old.available()) {
                throw new IllegalStateException("insufficient stock");
            }

            List<String> candidateReservations =
                    new ArrayList<>(old.reservations());
            candidateReservations.add(orderId + ":" + quantity);

            State candidate = new State(
                    old.available() - quantity, candidateReservations);

            if (failBeforeCommit) {
                throw new IllegalStateException("injected before commit");
            }

            state = candidate;
        }
    }

    public static void main(String[] args) {
        Inventory inventory = new Inventory();
        State initial = inventory.snapshot();

        try {
            inventory.reserve("order-A", 7, false);
        } catch (IllegalStateException ex) {
            System.out.println("rejected: " + ex.getMessage());
        }
        System.out.println("unchanged after rejection: "
                + initial.equals(inventory.snapshot()));

        try {
            inventory.reserve("order-B", 2, true);
        } catch (IllegalStateException ex) {
            System.out.println("injected: " + ex.getMessage());
        }
        System.out.println("unchanged after injection: "
                + initial.equals(inventory.snapshot()));

        inventory.reserve("order-C", 2, false);
        System.out.println("after success: " + inventory.snapshot());
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 -encoding UTF-8 ExceptionSafeInventoryDemo.java
java ExceptionSafeInventoryDemo
```

Stdout xác định:

```text
rejected: insufficient stock
unchanged after rejection: true
injected: injected before commit
unchanged after injection: true
after success: State[available=3, reservations=[order-C:2]]
```

**Vì sao ví dụ giữ state đúng?**

1. Kiểm tra `orderId`, số lượng và tồn kho diễn ra **trước** khi gán `state`.
2. `candidateReservations` là danh sách mới, không phải list nội bộ đang dùng.
3. Constructor của `State` bảo đảm invariant tồn kho không âm và copy danh sách; lỗi trong bước dựng candidate chưa thay đổi state hiện tại.
4. Nhánh `failBeforeCommit` cố ý ném sau prepare và trước gán. Do đó snapshot không đổi.
5. Gán `state = candidate` là điểm commit in-memory. Sau đó method kết thúc ngay, không gọi một callback có thể ném làm mơ hồ nghĩa exception.
6. `synchronized` trên `reserve` và `snapshot` tránh hai lời gọi của **cùng object** đọc và ghi state đè nhau theo đoạn mã ví dụ.

**Giới hạn:** ví dụ chưa kiểm tra duplicate order ID, chưa có DB, event hoặc retry. Nếu domain yêu cầu ID duy nhất, phải kiểm tra hoặc mã hóa invariant ấy trước commit. `List.copyOf` chỉ copy nông, nhưng phần tử ở đây là `String` bất biến. Với nested mutable objects, cần snapshot sâu/value type thích hợp.

`synchronized` bảo vệ các method đang đồng bộ trên cùng monitor của Inventory; nó không tạo transaction xuyên nhiều JVM và không bảo vệ bất cứ biến shared nào nằm ngoài lock.

## 6. Khi nào một lần thay reference là đủ?

Với state in-memory hoàn toàn bất biến và mọi computation trước commit không có side effect, thay reference tại một điểm rõ có thể giúp thực hiện strong guarantee **đối với failure trước commit**.

Tuy nhiên, phải phân biệt ba loại atomicity:

- **Atomic reference publication:** caller đồng bộ nhìn state trước hoặc sau thay reference, không nhìn bản ghi nửa cập nhật.
- **Atomic read-modify-write:** hai lời gọi đồng thời không ghi đè cập nhật của nhau; cần lock, CAS loop hoặc cơ chế tương đương.
- **Atomic persistence/side effects:** database, cache, queue và dịch vụ bên ngoài nhất quán theo một transaction/hợp đồng. Không thể đạt bằng gán field Java.

Nếu API cho phép đọc từ thread khác mà không đồng bộ, chỉ gán một reference bình thường chưa đủ bảo đảm **visibility** theo Java Memory Model. Có thể dùng `synchronized`, `volatile` hoặc `AtomicReference` thích hợp, nhưng phải lựa chọn sao cho cả logic đọc-sửa-ghi đúng.

Đối với nhiều object cùng tham gia một operation, đôi khi lock chung, transaction hoặc coordinator là điều kiện cần. Đừng nhầm một immutable state đẹp với bảo đảm toàn workflow không thể thấy trạng thái tạm.

## 7. Thiết kế trước commit và sau commit tách biệt

Một phản mẫu:

```java
void createOrder(Order order) {
    database.insert(order);
    emailService.sendConfirmation(order); // Có thể ném IOException.
}
```

Đây là đoạn minh họa với các dependency giả định. Khi gửi email ném, database có thể đã lưu đơn. Nếu caller nhận exception rồi gọi lại `createOrder`, đơn hàng/email có thể trùng.

Giải pháp phải dựa trên **hợp đồng nghiệp vụ**:

- Nếu muốn **đơn hàng và nhiệm vụ gửi email** được ghi nguyên tử, có thể đưa **outbox event** vào cùng transaction DB với đơn hàng.
- Worker khác xử lý outbox, retry phát event với idempotency/dedup; việc gửi email thực tế là một lifecycle riêng.
- Nếu API trả về ngay sau commit DB, nó nên nêu rõ thông báo có thể đang **pending**, không biến lỗi gửi mail thành “đơn chưa được tạo” sai sự thật.
- Nếu dependency có thể commit rồi mất response, cần operation ID và đối soát; không suy ra “không đổi” từ `IOException` của caller.

Với transaction DB thật, trạng thái được commit khi database xác nhận theo contract. Một JVM crash giữa các bước không được giải quyết bằng `finally` hay by-value record in-memory.

### Side effect nên được thực hiện lúc nào?

Không có quy tắc chung rằng mọi side effect phải đặt sau commit. Ví dụ cần authorization trước commit; một số bước read-only gọi dependency phải hoàn thành trước quyết định. Điều quan trọng là biết **bước nào có side effect, bước nào có thể lỗi và điểm commit nào là ranh giới của hợp đồng**.

Với event publication sau commit, test phải kiểm tra “đơn đã commit nhưng event còn pending” theo design; không ép rollback vô điều kiện chỉ vì callback downstream ném.

## 8. Ví dụ Java 17 hoàn chỉnh: ghi business state và outbox cùng một snapshot

Ví dụ in-memory sau biểu diễn nguyên lý *outbox*: một lần commit đưa cả order và event vào cùng immutable state. Lỗi chèn trước commit giữ nguyên cả hai; sau commit, event ở trạng thái chờ gửi, không phải đã được giao cho broker.

Tạo file `OrderOutboxStateDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class OrderOutboxStateDemo {

    private record State(List<String> orders, List<String> pendingEvents) {
        State {
            orders = List.copyOf(orders);
            pendingEvents = List.copyOf(pendingEvents);
        }
    }

    private static final class OrderService {
        private State state = new State(List.of(), List.of());

        synchronized State snapshot() {
            return state;
        }

        synchronized void create(String orderId, boolean failBeforeCommit) {
            if (orderId == null || orderId.isBlank()) {
                throw new IllegalArgumentException("invalid orderId");
            }

            State old = state;
            if (old.orders().contains(orderId)) {
                throw new IllegalStateException("duplicate order");
            }

            List<String> nextOrders = new ArrayList<>(old.orders());
            nextOrders.add(orderId);

            List<String> nextEvents =
                    new ArrayList<>(old.pendingEvents());
            nextEvents.add("OrderCreated:" + orderId);

            State candidate = new State(nextOrders, nextEvents);

            if (failBeforeCommit) {
                throw new IllegalStateException("injected before commit");
            }

            state = candidate;
        }
    }

    public static void main(String[] args) {
        OrderService service = new OrderService();
        State before = service.snapshot();

        try {
            service.create("A", true);
        } catch (IllegalStateException ex) {
            System.out.println("failed: " + ex.getMessage());
        }

        System.out.println("unchanged: " + before.equals(service.snapshot()));

        service.create("A", false);
        System.out.println("orders: " + service.snapshot().orders());
        System.out.println("outbox: " + service.snapshot().pendingEvents());

        try {
            service.create("A", false);
        } catch (IllegalStateException ex) {
            System.out.println("duplicate: " + ex.getMessage());
        }
        System.out.println("still one order: "
                + service.snapshot().orders().size());
    }
}
```

Chạy:

```bash
javac --release 17 -encoding UTF-8 OrderOutboxStateDemo.java
java OrderOutboxStateDemo
```

Stdout:

```text
failed: injected before commit
unchanged: true
orders: [A]
outbox: [OrderCreated:A]
duplicate: duplicate order
still one order: 1
```

Trong chương trình:

- Trạng thái ban đầu không có order/event.
- Failure trước commit không publish `candidate`, vì thế order/event đều giữ nguyên.
- Lần tạo thành công đưa `A` và event `OrderCreated:A` vào **cùng snapshot**.
- Một lần tạo trùng bị từ chối trước commit, không nhân đôi order.
- Event còn trong `pendingEvents`, **chưa giả vờ đã gửi**.

**Hạn chế rất quan trọng:** đây là ví dụ minh họa ranh giới commit **trong một JVM và một object**; không có DB transaction, crash safety, nhiều node, worker gửi event, persistence hoặc acknowledgment. Production transactional outbox cần ghi order và outbox row **trong cùng giao dịch bền vững**, worker phát event theo cơ chế ít nhất một lần khi thích hợp, và consumer/delivery phải xử lý duplicate.

Không dùng `List<String>` in-memory này làm “outbox bảo đảm dữ liệu” cho nghiệp vụ tiền thật.

## 9. Nguyên tắc validate trước khi mutate có giới hạn

Đối với input không hợp lệ, **validate first** thường giúp state không đổi:

```java
void setQuantity(int quantity) {
    if (quantity <= 0) {
        throw new IllegalArgumentException("quantity <= 0");
    }
    this.quantity = quantity;
}
```

Đây là snippet trong một class giả định; `quantity` là field. Với update chỉ một primitive và không có side effect nào sau đó, đây là cấu trúc đơn giản.

Nhưng nếu method có thêm:

```java
this.quantity = quantity;
notifyListeners(); // Có thể ném exception.
```

thì exception ở `notifyListeners` xảy ra **sau mutation**. Nếu API vẫn hứa strong guarantee, phải thiết kế lại ranh giới:

- Callback có thể được chuẩn bị/kiểm tra trước mà không side effect không?
- Có thể tách update và notification thành hai operation/hợp đồng riêng không?
- Có thể ghi durable event/outbox thay vì gọi callback trực tiếp dưới transaction không?
- Có thể rollback mutation và cả tác động callback đã xảy ra không? Nếu không, không nên hứa rollback toàn bộ.
- Caller có thể phân biệt “update thành công, notification chưa được giao” qua result/status không?

**Đặt validation trước mutation là cần thiết nhưng chưa đủ** cho method có nhiều bước có thể ném.

## 10. Hợp đồng method khi lỗi: throw, return hay trạng thái miền ứng dụng?

Một API có thể chọn cách báo lỗi:

**Throw exception** khi precondition sai, invariant bị vi phạm hoặc lỗi bất ngờ theo hợp đồng.

```java
void reserve(String orderId, int quantity);
```

Nếu dùng `IllegalStateException` khi thiếu hàng, cần tài liệu hóa: sau khi ném, tồn kho và reservation có giữ nguyên không? Caller có thể thử lại khi bổ sung hàng không?

**Trả một result type** khi “không thực hiện được” là kết quả thường gặp và caller cần phân biệt nhiều trường hợp có kiểm soát.

```java
sealed interface ReserveResult
        permits Reserved, Rejected, Pending {}

record Reserved(String reservationId) implements ReserveResult {}
record Rejected(String reasonCode) implements ReserveResult {}
record Pending(String operationId) implements ReserveResult {}
```

Đây là snippet minh họa dùng sealed interface/record hợp lệ về mặt cú pháp Java 17; cần đặt các declaration trong file hoặc class thích hợp. `Pending` biểu diễn trường hợp chưa hoàn tất/chưa chắc kết quả, không phải một exception được nuốt để biến thành thành công.

**Không biến mọi exception thành `true`/`false` hoặc `null`** nếu caller cần nguyên nhân, trạng thái giao dịch và hướng phục hồi. Một result type chỉ tốt khi semantics, invariant và các trạng thái trả về được định nghĩa rõ.

### Checked và unchecked không tự nói trạng thái đã rollback

`IOException`, `SQLException` và `RuntimeException` thuộc các nhóm khác nhau ở compile time, nhưng **không loại nào tự bảo đảm object không đổi**. Tài liệu contract cần chỉ ra điều gì xảy ra trên mỗi loại lỗi.

## 11. Chọn đúng ranh giới transaction hoặc unit of work

Nếu method sửa hai tài khoản:

```java
from.debit(amount);
to.credit(amount);
```

đây là đoạn minh họa nguy hiểm nếu `to.credit` có thể ném sau khi `from.debit` đã thành công. Nếu contract hứa một giao dịch nguyên tử, cần:

- Xác minh số dư/điều kiện trước khi commit.
- Dựng candidate cho **cả hai account**.
- Commit dưới một lock/transaction bao trùm đúng các đối tượng.
- Hoặc giao toàn bộ thao tác cho database transaction thực với isolation/constraints.
- Khi có ngoại tác mạng, sử dụng operation ID và giao thức phối hợp thích hợp.

Nếu domain cho phép tiến độ một phần, phải báo explicit trạng thái thay vì giả vờ transfer nguyên tử. Với luồng tiền thật, không chỉ dựa vào `synchronized` trên hai object Java: nhiều process/node/database transaction và sự cố mất response đều phải được xét.

### Đừng chỉ rollback field thứ nhất trong catch

Phản mẫu:

```java
try {
    from.debit(amount);
    to.credit(amount);
} catch (Exception ex) {
    from.credit(amount);
    throw ex;
}
```

Đây là snippet minh họa anti-pattern. Lệnh rollback cũng có thể ném hoặc tạo side effect mới; `to.credit` có thể đã thành công trước khi ném; đồng thời code có thể đang chạy cạnh tranh với thread khác. Cách này không tự trở thành transaction an toàn.

Với in-memory state nhỏ, staged immutable state là lựa chọn tốt. Với DB, transaction là boundary. Với nhiều hệ thống, có thể cần workflow/Saga/compensation và trạng thái trung gian rõ ràng.

## 12. Cleanup thuộc cùng hợp đồng trạng thái sau thất bại

Một API có thể **giữ nguyên dữ liệu nghiệp vụ** sau lỗi nhưng vẫn để rò rỉ connection, semaphore permit hoặc listener. Như vậy, lời hứa “state hợp lệ sau exception” chưa đủ nếu hợp đồng còn quy định lifecycle tài nguyên.

Khi một method acquire resource A rồi B, hãy ghi rõ: A đã được cấp thành công thì phải được giải phóng nếu bước B thất bại. Với resource do chính phạm vi sở hữu, dùng:

~~~java
try (Resource first = acquireFirst();
     Resource second = acquireSecond(first)) {
    performWork(first, second);
}
~~~

Đây là snippet minh họa, với `Resource extends AutoCloseable` và các factory tự định nghĩa. Nếu `acquireSecond` thất bại, Java cố đóng `first`. Nếu cả hai thành công, chúng được đóng **ngược thứ tự**. Trong test, đừng chỉ assert loại exception: hãy đo `closeCalls` và trạng thái object sau failure.

**Ownership phải chính xác:** không đóng stream mà API chỉ mượn từ caller; không shutdown cả executor dùng chung chỉ vì một job lỗi. Với `Semaphore.acquire()`, `Lock.lockInterruptibly()` hoặc một token đăng ký có thể thất bại trước khi acquire, chỉ release/unlock/unsubscribe khi đã sở hữu thực sự. Lỗi cleanup cũng không tự hoàn tác những dữ liệu đã commit.

Phần kế tiếp dùng fake resource để kiểm chứng rõ **lỗi khi acquire B, A vẫn được đóng**, thay vì phụ thuộc vào file hay connection thật.

## 13. Ví dụ Java 17 hoàn chỉnh: cleanup khi acquire thất bại

Ví dụ dùng một `AutoCloseable` mô phỏng resource, ghi thứ tự các bước vào danh sách. Tình huống thứ hai không mở được resource B, vì vậy A phải được đóng trước khi exception tới caller.

Tạo file `PartialAcquisitionDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class PartialAcquisitionDemo {

    private static final class Lease implements AutoCloseable {
        private final String name;
        private final List<String> events;

        Lease(String name, List<String> events) {
            this.name = name;
            this.events = events;
            events.add("open " + name);
        }

        @Override
        public void close() {
            events.add("close " + name);
        }
    }

    private static Lease acquireSecond(
            List<String> events, boolean fail) {
        if (fail) {
            throw new IllegalStateException("second unavailable");
        }
        return new Lease("B", events);
    }

    private static void execute(List<String> events, boolean failSecond) {
        try (Lease first = new Lease("A", events);
             Lease second = acquireSecond(events, failSecond)) {
            events.add("work");
        }
    }

    public static void main(String[] args) {
        List<String> failed = new ArrayList<>();
        try {
            execute(failed, true);
        } catch (IllegalStateException ex) {
            failed.add("caught " + ex.getMessage());
        }
        failed.forEach(System.out::println);

        System.out.println("---");

        List<String> success = new ArrayList<>();
        execute(success, false);
        success.forEach(System.out::println);
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 -encoding UTF-8 PartialAcquisitionDemo.java
java PartialAcquisitionDemo
```

Stdout:

```text
open A
close A
caught second unavailable
---
open A
open B
work
close B
close A
```

Quan sát:

- Lỗi `acquireSecond` xảy ra **trước** khi B được tạo; không có `close B`.
- Vì A được tạo thành công, try-with-resources đóng A khi thoát do exception.
- Với đường thành công, B đóng trước A do thứ tự ngược khai báo.
- `Lease.close()` trong demo không ném và chỉ ghi sự kiện. Resource thực có thể cần cleanup khác hoặc close failure, phải có test riêng.

Tránh làm cleanup phụ thuộc vào việc “sau khi catch rồi sẽ tự nhớ đóng”: đặt resource scope gần nơi acquire là cách bền vững hơn.

## 14. Cleanup lỗi kép: kiểm tra thêm bất biến sau thất bại

Nếu phần thân đã ném ngoại lệ A và `close()` lại ném B, `try-with-resources` giữ A là **primary**, B ở `A.getSuppressed()` khi suppression được bật. Đây là cơ chế bảo toàn thông tin lỗi, **không phải cơ chế rollback dữ liệu**.

Đối với API exception-safe, kết quả kiểm thử cần ghép **hai nhóm assertion**:

| Thông tin lỗi | Trạng thái sau lỗi |
| --- | --- |
| Cause / primary vẫn mô tả đúng thất bại của operation | Snapshot business data còn đúng invariant; nếu hứa strong guarantee thì không đổi |
| `getSuppressed()` giữ thông tin lỗi close | Resource sở hữu đã được *thử* close đúng cách, dù close có thể thất bại |
| Lỗi close-only được báo khi thân try thành công | Caller không nhầm kết quả trước close là thành công hoàn tất |
| Nếu cleanup/undo bị lỗi tiếp | Lifecycle có thể là `FAILED`/không thể tái sử dụng; không thông báo rollback đã thành công |

Ví dụ chương trình `PartialAcquisitionDemo` ở mục 13 chỉ ghi nhận close thành công; khi mở rộng fake để close ném exception, hãy assert primary/suppressed **và** state machine của resource. Các kịch bản ba resource và kiểm thử thứ tự suppressed chuyên sâu thuộc bài riêng về nhiều suppressed exceptions; ở đây trọng tâm là **hợp đồng hậu điều kiện của API**.

## 15. Phân biệt lỗi trước commit, trong commit và sau commit

Một failure có thể xảy ra ở ba giai đoạn khác nhau:

| Thời điểm | Ví dụ | Kết luận hợp lý |
| --- | --- | --- |
| **Trước commit** | Validation fail; candidate state không dựng được | Nếu prepare không side effect, state đã publish có thể giữ nguyên. |
| **Trong commit** | DB commit trả lỗi kết nối; driver không biết kết quả | Có thể là outcome unknown; cần kiểm tra contract và đối soát. |
| **Sau commit** | Gửi email/event hay log callback thất bại | Business state có thể đã commit; không tuyên bố rollback nếu không có cơ chế. |

Trên JVM in-memory, một phép gán field không ném checked exception và được JVM thực hiện theo semantics của nó; nhưng việc **chỉ có một phép gán** không giải quyết side effect bên ngoài, visibility hoặc race. API phải chia phase đúng ranh giới quan sát.

Một thiết kế tốt thường không cố catch bất cứ exception nào rồi gọi `rollback()` vô điều kiện. Nó ghi trạng thái đã commit, trạng thái cần retry/compensate và định danh operation để trả caller kết quả thật.

## 16. Không để callback làm hỏng tính nguyên tử của state

Giả sử method gọi observer trong khi state đang dở:

```java
state = new State(...);
listener.onChanged(state); // callback ngoài quyền kiểm soát
commitOtherPart();
```

Đây là snippet minh họa. Listener có thể ném hoặc reenter API; trong lúc đó những thành phần khác đã nhìn thấy một phần state mới. Đặc biệt không nên gọi callback không tin cậy khi đang giữ lock nếu có nguy cơ deadlock hoặc lộ trạng thái giữa chừng.

Hướng cải thiện:

1. Validate và dựng snapshot mới.
2. Commit toàn bộ state thuộc một invariant một cách có kiểm soát.
3. Ghi nhận event hoặc callback trong **giai đoạn sau commit** với semantics riêng.
4. Nếu callback fail, có policy retry/durable event hoặc trạng thái notification failed; không biến thành “state chưa đổi”.
5. Bảo đảm listener không có quyền mutate trực tiếp collection nội bộ.

Nếu cần notification **cùng transaction** với business update, dùng cơ chế outbox/transaction; một callback in-memory không thể thay thế transaction phân tán.

## 17. API có vòng đời: OPEN, FAILED, CLOSED

Không phải lỗi nào cũng cho phép object hoạt động như cũ. Nếu một connection/session bị phá đến mức không thể phục hồi an toàn, object có thể phải chuyển sang `FAILED` để từ chối thao tác tiếp theo.

```text
NEW → OPEN → CLOSED
        |
        +→ FAILED → CLOSED
```

Khi method thất bại, contract nên xác định:

- Lỗi validation thông thường: object vẫn `OPEN` và có thể dùng tiếp.
- Lỗi giao thức nghiêm trọng làm mất đồng bộ: object `FAILED` và các lệnh tiếp theo bị từ chối.
- Lỗi trong `close()`: object có thể ở `CLOSING`/`CLOSED_WITH_ERRORS` hoặc trạng thái khác được định nghĩa; không tự báo `CLOSED` sạch nếu resource chưa giải phóng.
- Lệnh `close()` gọi lại: có idempotent không, có ném không?
- Caller nên dùng `reset()`, tạo object mới, hay đóng bỏ?

Đừng viết API khiến state `ACTIVE` xuất hiện khi các resource cần thiết cho ACTIVE chưa tạo xong. Cũng đừng bắt mọi lỗi rồi lặng lẽ đặt `OPEN` dù invariant có thể đã bị phá.

Nếu object được chia sẻ, lifecycle transition phải đồng bộ với thread đang xử lý, không chỉ cập nhật enum thiếu memory visibility.

## 18. Khi method có partial progress được phép

Strong guarantee không phải lúc nào cũng rẻ hoặc khả thi. Một API batch có thể công khai **basic guarantee**:

```java
BatchResult importRecords(List<Record> records);
```

Đây là chữ ký minh họa, `BatchResult` và `Record` cần được định nghĩa. Hợp đồng có thể cho phép xử lý thành công N record, rồi dừng khi record tiếp theo lỗi. Khi đó:

- N record đã commit phải nhất quán, không trùng.
- Vị trí resume/checkpoint phải chính xác.
- Record lỗi không được để lại nửa cấu trúc sai.
- Tài nguyên owned phải cleanup.
- Caller nhận số lượng thành công, danh sách lỗi hoặc trạng thái cho phép phục hồi.
- Retry/resume không lặp side effect của các record đã ghi.

Nếu contract là “atomic batch”, giữ lại 7/10 record sau exception lại **vi phạm** hợp đồng. Hãy chọn guarantee theo nhu cầu domain, tài liệu hóa và kiểm thử cụ thể.

Một result type báo “PARTIAL_SUCCESS” có thể rõ hơn ném một exception chung rồi buộc caller tự suy đoán bao nhiêu item đã được ghi.

## 19. Cách dùng try/catch cho rollback có giới hạn

Một số code thực sự cần thao tác bù khi failed:

```java
boolean registered = false;
try {
    registerListener(listener);
    registered = true;
    activate();
} catch (RuntimeException failure) {
    if (registered) {
        try {
            unregisterListener(listener);
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }
    throw failure;
}
```

Đây là snippet minh họa; `registerListener`, `activate`, `unregisterListener`, `listener` chưa được định nghĩa. Nó cho thấy **chỉ cleanup cái đã acquire thành công**, và không để lỗi cleanup che lỗi chính.

Nhưng có hai vấn đề API phải tự xử lý:

- `registerListener` có thể đã đăng ký thành công rồi **ném sau khi side effect xảy ra**, nên `registered=false` không chắc đồng nghĩa không cần cleanup. Tốt nhất registration trả một handle có contract rõ, hoặc thao tác có idempotency/trạng thái đối soát.
- `activate` có thể tác động ngoài đăng ký listener. Cleanup listener không rollback side effect khác, vì vậy không được vội hứa strong guarantee toàn workflow.

Tốt hơn, thiết kế các bước acquire/registration để chúng hoặc trả handle rõ ràng, hoặc có cơ chế an toàn xác định nếu lỗi xảy ra giữa chừng.

## 20. Thiết kế contract lỗi dễ hiểu cho caller

Javadoc hoặc mô tả API nên nêu:

**Precondition:** input và lifecycle yêu cầu gì?

**Failure types:** lỗi validation, thiếu tài nguyên, lỗi dependency, cancellation và illegal state được biểu diễn như thế nào?

**State guarantee:** phần nào không đổi khi thất bại? Có partial commit không?

**Resource ownership:** API tự đóng cái gì, cái gì caller phải đóng?

**Retry semantics:** lỗi nào có thể retry, thao tác có idempotent không, có operation ID không?

**Concurrency:** method thread-safe không, snapshot nhất quán đến mức nào?

**Unknown outcome:** nếu mất response lúc commit, caller tra cứu bằng cách nào?

Ví dụ Javadoc minh họa:

```java
/**
 * Giữ chỗ số lượng hàng cho một order.
 *
 * @throws IllegalArgumentException nếu id blank hoặc quantity không dương
 * @throws IllegalStateException nếu không đủ hàng
 *
 * Exception safety: nếu validation hoặc kiểm tra tồn kho thất bại,
 * trạng thái inventory giữ nguyên. Phương thức thread-safe trong
 * phạm vi một Inventory instance.
 */
void reserve(String orderId, int quantity);
```

Đây là chữ ký/tài liệu minh họa, chưa định nghĩa body. `@throws` mô tả điều kiện xảy ra lỗi; phần “Exception safety” mô tả hậu quả đối với object. Hai phần bổ trợ nhau.

Không dùng `throws Exception` rộng chỉ vì API có nhiều bước. Kiểu lỗi rõ có ích, nhưng ngay cả nhiều kiểu exception cụ thể cũng không thay cho mô tả state guarantee.

## 21. Kiểm thử: sau exception state còn hợp lệ không?

Một bộ test chỉ có:

```java
assertThrows(IllegalStateException.class, () -> inventory.reserve("x", 99));
```

là chưa đủ. Đây là snippet JUnit 5 cần dependency ngoài JDK. Nó chứng minh một exception thuộc họ `IllegalStateException` được ném, không chứng minh state còn đúng.

Một test tốt nên:

1. Chuẩn bị object ở state hợp lệ.
2. Lấy snapshot độc lập trước lời gọi.
3. Gây một lỗi xác định.
4. Assert exception type/code/cause khi contract yêu cầu.
5. Assert snapshot sau bằng trước cho strong guarantee, hoặc thỏa basic guarantee đã mô tả.
6. Assert invariant của object.
7. Assert không có side effect ngoài phạm vi hoặc trạng thái unknown đúng như hợp đồng.
8. Assert resource đã cleanup.
9. Thử một thao tác **thành công sau khi lỗi** nếu object được hứa vẫn usable.
10. Với concurrency, kiểm tra trạng thái và thứ tự commit dưới interleaving được kiểm soát.

### Snapshot không được trỏ vào collection mutable

Sai:

```java
List<Item> before = inventory.items();
```

Nếu `items()` trả list mutable nội bộ, `before` và `inventory.items()` có thể là cùng object; mutation sau thất bại làm snapshot thay đổi theo, test false pass.

Đúng hơn:

```java
List<ItemView> before = snapshotItems(inventory);
```

Đây là snippet minh họa, với `ItemView` bất biến và `snapshotItems` tạo bản sao sâu/semantic phù hợp. `List.copyOf` chỉ tạo shallow copy; nếu phần tử còn mutable, cần bản snapshot an toàn hơn.

## 22. Kiểm thử lỗi ở nhiều mốc

Đừng chỉ test validation failure. Một API gồm acquire, transform, commit và publish có thể lỗi ở bất kỳ bước nào:

| Điểm phát lỗi | Kiểm tra |
| --- | --- |
| Đầu vào null/blank/sai khoảng | Không mutate state, không gọi side effect. |
| Không thỏa invariant nghiệp vụ | Trạng thái giữ nguyên theo contract. |
| Acquire resource A thành công, B lỗi | A được cleanup, B không bị close nếu chưa tồn tại. |
| Tạo candidate collection lỗi | State đang publish không đổi. |
| Trước commit | Candidate không bị publish. |
| Trong DB transaction | Rollback/UNKNOWN theo hợp đồng DB, không đoán. |
| Sau commit khi gửi event lỗi | State đã commit, event pending hoặc failure được ghi có nghĩa. |
| Close cũng lỗi | Primary/cause/suppressed và resource state được bảo toàn. |
| Caller bị interrupt khi đang chờ | Không nhầm với worker thất bại; cleanup thuộc đúng owner. |
| Duplicate operation ID | Không tạo side effect trùng khi domain đòi idempotency. |

Có thể dùng fake adapter có nút “fail at step N” hoặc JUnit mock có hành vi xác định. Nhưng unit test không chứng minh transaction của database thật; cần integration test với hệ thống thật/giả lập phù hợp.

## 23. Kiểm thử lỗi đã làm object hỏng ngầm

Một đối tượng có thể giữ nguyên những field nhìn thấy nhưng bị kẹt vì:

- Cờ `busy=true` không được reset.
- Semaphore permit/lock chưa release.
- Listener còn đăng ký nên nhận callback không mong muốn.
- Stream đã đóng nhưng field vẫn báo `OPEN`.
- Cache version và DB version không đồng nhất.
- Task async vẫn ghi state sau khi API báo canceled.
- Một thread bị interrupt nhưng vòng lặp vẫn tiếp tục tác động.

Ngoài snapshot, nên thực hiện **operation hợp lệ ngay sau failure**. Nếu contract nói object vẫn hoạt động, method tiếp theo phải không bị kẹt do resource/lifecycle bị bỏ sót. Nếu contract nói phải chuyển `FAILED`, lời gọi tiếp theo cần báo rõ `IllegalStateException`/domain error chứ không sử dụng state có thể không an toàn.

Với cleanup async, phải đợi acknowledgement/latch theo contract trước khi assert “đã đóng”; `Future.isCancelled()` không nhất thiết đồng nghĩa `finally` của worker đã hoàn tất.

## 24. Concurrency: exception safety khác thread safety

Đoạn:

```java
State old = state;
State next = compute(old);
state = next;
```

có thể đáp ứng strong guarantee với một thread, nhưng **hai thread cùng đọc `old` và commit riêng có thể mất cập nhật**. Vì vậy một API được chia sẻ phải xét:

- Atomic read-modify-write bằng lock/CAS/transaction.
- Visibility: `volatile` hoặc synchronization cho publication.
- Invariant gồm nhiều field cần được quan sát nhất quán.
- Callback không chạy trên state nửa commit.
- Nếu CAS thất bại, thử tính candidate mới không được lặp side effect vô ý.
- Cancellation xảy ra giữa prepare và commit phải có chính sách rõ.

Nếu dùng CAS loop, `compute(old)` phải không có side effect không idempotent, vì CAS có thể fail và buộc tính lại. Đừng gọi thanh toán, gửi email hoặc tăng counter bên ngoài trước mỗi lần CAS retry.

**Một lần gán reference không tự làm toàn bộ workflow thread-safe.** Nó chỉ là một thành phần kỹ thuật trong thiết kế đồng bộ.

## 25. Exception safety và cancellation/interruption

Một operation có thể nhận `InterruptedException` ở bước blocking. Cách xử lý tùy ranh giới:

- Nếu API có thể truyền `InterruptedException` lên caller, truyền tín hiệu để lifecycle owner quyết định.
- Nếu chuyển sang unchecked exception và dừng, thường khôi phục interrupt flag bằng `Thread.currentThread().interrupt()`.
- Đặt cleanup owned resources trong `finally`/`try-with-resources`.
- Không release lock/permit khi chưa acquire.
- Nếu cancel xảy ra sau side effect, trạng thái chưa chắc rollback.
- Nếu cleanup cần thao tác blocking, cân nhắc khi nào phục hồi flag để cleanup không tự bị ngắt lập tức; đặt deadline hợp lý.

Ví dụ:

```java
try (OwnedHandle handle = acquireOwnedHandle()) {
    performInterruptibleWork(handle);
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    throw new OperationInterruptedException("interrupted", ex);
}
```

Đây là snippet minh họa với các kiểu/method do ứng dụng cung cấp. Đặt catch **bên ngoài** try-with-resources nghĩa là handle được đóng trước khi catch khôi phục flag, có thể hữu ích khi cleanup nhạy với interruption. Nếu `close` có checked exception khác, chữ ký/catch thực tế phải xử lý theo đúng loại đó.

Không coi interrupt như lỗi tạm thời để retry vô hạn; nó thường là yêu cầu dừng.

## 26. Những lỗi thiết kế API thường gặp

**1. Ném đúng exception nhưng mutate state trước khi kiểm tra.** Test type vẫn pass trong khi invariant sai. Validate trước và chuẩn bị candidate trước commit.

**2. Trả collection mutable nội bộ ra ngoài.** Caller có thể tự thay đổi state bỏ qua invariant/validation. Trả immutable view hoặc snapshot theo contract.

**3. Chỉ copy reference để snapshot.** Dữ liệu lồng mutable đổi theo bản gốc, tạo test false pass. Dùng immutable value objects hoặc deep snapshot.

**4. Tưởng một gán field là transaction DB.** Nó không rollback filesystem, queue, external API hay state của process khác.

**5. Catch mọi exception rồi rollback thủ công vô điều kiện.** Rollback cũng có thể lỗi và side effect có thể đã commit; cần transaction/compensation/status rõ.

**6. Xem lỗi ở after-commit callback là bằng chứng chưa commit.** Có thể tạo duplicate khi caller retry. Phân biệt business commit và notification.

**7. Đóng shared resource khi một request lỗi.** Ownership sai gây gián đoạn request khác. Chỉ đóng handle do scope sở hữu.

**8. Release permit hoặc unlock chưa acquire.** Interruption có thể đến trước lúc acquire thành công; kiểm tra đúng điểm sở hữu.

**9. Đặt tất cả exception thành một thông điệp “failed”.** Làm mất cause và trạng thái rollback/unknown cần thiết.

**10. Nuốt cleanup exception.** Có thể mất dữ liệu khi flush/close; giữ cause/suppressed và trạng thái lỗi.

**11. Hứa strong guarantee nhưng cho phép partial progress không được báo.** Phải thống nhất contract và test; nếu basic, expose checkpoint.

**12. Dùng `synchronized` ở một method nhưng đọc state từ method khác không đồng bộ.** Dễ có visibility/race. Bảo vệ toàn bộ contract đọc/ghi.

**13. Gọi observer trong lúc object ở state tạm.** Callback có thể ném hoặc reenter; publish state nhất quán trước và chọn semantics notification.

**14. Chỉ kiểm tra exception class bằng `assertThrows`.** Không kiểm tra state, invariant, cleanup và khả năng object còn usable.

**15. Retry toàn operation sau timeout dù side effect có thể đã xảy ra.** Cần idempotency/operation ID và cơ chế đối soát.

## 27. Bài tập thực hành

**Bài 1 – Kho hàng:** `reserve(7)` từ tồn 5 ném lỗi nhưng thêm một reservation. Hãy mô tả invariant bị vi phạm và thay đổi thứ tự xử lý.

*Gợi ý:* kiểm tra quantity/tồn và duplicate ID trước; chuẩn bị danh sách mới; commit một lần. Test cả state và exception.

**Bài 2 – Account:** `transfer(30)` trừ nguồn rồi lỗi khi cộng đích. Nếu contract hứa atomic, tại sao `catch { source += 30; }` chưa đủ?

*Gợi ý:* rollback cũng có thể thất bại, đích có thể đã cộng trước khi ném, và concurrency có thể xen vào. Cần ranh giới atomic hơn.

**Bài 3 – Listener:** `register` A thành công, B thất bại; A vẫn còn subscribed. Nên cleanup ở đâu?

*Gợi ý:* dùng registration handle thuộc scope hoặc `finally` chỉ hủy registration đã acquire. Test lần khởi tạo lỗi và callback sau cleanup.

**Bài 4 – DB + email:** DB đã commit nhưng gửi email lỗi. Có được khẳng định tạo đơn hàng thất bại hoàn toàn không?

*Gợi ý:* không nếu DB đã commit; dùng pending event/outbox hoặc báo trạng thái thích hợp.

**Bài 5 – Concurrency:** hai thread cùng đọc tồn kho 5, cả hai muốn giữ 4, mỗi thread tự tính `next=1` rồi gán. Invariant `available >= 0` có thể vẫn đúng nhưng đã xảy ra lỗi gì?

*Gợi ý:* lost update/đặt quá số lượng thực tế. Cần đồng bộ/CAS/transaction và invariant bao gồm tổng reservation.

**Bài 6 – Cleanup:** thân `try` ném `IllegalStateException`, `close` ném `IOException`. Test nên kiểm tra gì?

*Gợi ý:* primary exception, suppressed close exception, tài nguyên đã cố đóng, trạng thái cleanup theo contract.

**Bài 7 – Interruption:** một luồng bị ngắt trước khi `Semaphore.acquire()` nhận permit. Tại sao `finally { semaphore.release(); }` luôn chạy là sai?

*Gợi ý:* chưa sở hữu permit nên release làm sai bộ đếm; thiết kế scope theo điểm acquire thành công.

## 28. Checklist trước khi công bố một API exception-safe

- [ ] Mọi public method có precondition, postcondition và invariant rõ.
- [ ] Đã chọn strong/basic/no-throw hoặc mô tả guarantee riêng theo thao tác.
- [ ] Lỗi validation không mutate state đã publish.
- [ ] Candidate dữ liệu được chuẩn bị độc lập, không vô tình sửa collection đang chia sẻ.
- [ ] Commit nằm ở một ranh giới có thể xác định/kiểm soát.
- [ ] Các thao tác có thể lỗi sau commit đã có semantics riêng, không hứa rollback giả.
- [ ] DB/outbox/event/network có transaction, idempotency hoặc đối soát thích hợp.
- [ ] Resource ownership rõ: acquire thành công mới release/close.
- [ ] try-with-resources/finally bao phủ đường lỗi và cleanup failure được xét.
- [ ] Loại exception, cause và trạng thái UNKNOWN/PENDING được công bố đủ.
- [ ] API không lộ mutable state để caller phá invariant bên ngoài.
- [ ] Concurrency/read-write visibility được bảo vệ theo hợp đồng.
- [ ] Có test fault injection trước commit, trong cleanup và after-commit.
- [ ] Có snapshot state độc lập và assertion invariant sau thất bại.
- [ ] Có test operation thành công sau exception hoặc chuyển đúng FAILED lifecycle.
- [ ] Có integration test cho transaction/side effect ngoài JVM khi cần.
- [ ] Không retry bừa khi response mất mà kết quả server có thể đã commit.

## 29. Tổng kết

Thiết kế API giữ trạng thái hợp lệ khi có lỗi đòi hỏi **nêu rõ trạng thái nào được phép sau exception**, không chỉ quyết định sẽ ném exception gì. Với in-memory state, mô hình **validate → prepare → commit** cùng immutable value objects và synchronization thích hợp giúp giảm mutation một phần; với resource, cần ownership và cleanup có cấu trúc; với DB/network/event, cần transaction, outbox, idempotency hoặc đối soát theo ranh giới thực tế.

Một API đáng tin có thể nói chính xác: **lỗi này xảy ra trước commit nên state không đổi; lỗi kia xảy ra sau commit nên business state vẫn thành công nhưng notification còn pending; lỗi nghiêm trọng này khiến object chuyển FAILED và không còn dùng tiếp được.** Khi những hợp đồng ấy được thể hiện trong thiết kế và test, exception không còn là dấu hiệu mơ hồ mà trở thành một phần kiểm soát được của tính đúng đắn hệ thống.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-practice-008">Xử lý lỗi và ngắt trong tác vụ ExecutorService</a>

<details>
<summary>Xem chi tiết</summary>

# Xử lý lỗi và ngắt trong tác vụ ExecutorService (Java Core)

## 1. Mục tiêu thực hành

`ExecutorService` giúp thực thi tác vụ qua pool thread, nhưng mỗi lời gọi như `execute()`, `submit()`, `Future.get()`, `cancel(true)` và `shutdownNow()` có **hợp đồng xử lý exception và interruption khác nhau**. Nếu nhầm những hợp đồng đó, chương trình có thể mất dấu lỗi, không dừng được worker hoặc báo thành công giả sau khi tác vụ đã thất bại.

Sau bài này, bạn cần tự thực hiện được các việc:

- Chọn `execute()` hay `submit()` dựa trên cách caller cần quan sát lỗi và kết quả.
- Phân biệt **lỗi của task** với **lỗi của thread đang chờ kết quả**.
- Quan sát chính xác `ExecutionException`, `CancellationException`, `InterruptedException` và `RejectedExecutionException`.
- Thiết kế tác vụ biết hợp tác hủy qua `interrupt()`, tránh nuốt `InterruptedException` rồi tiếp tục.
- Hủy Future đúng hợp đồng và không nhầm trạng thái “future đã cancelled” với “mọi side effect đã dừng”.
- Đóng pool bằng `shutdown()`, `awaitTermination()` và `shutdownNow()` theo thứ tự có giới hạn.
- Viết test có latch/timeout để xác minh đường thất bại và đường hủy mà không dựa hoàn toàn vào `Thread.sleep`.

**Nguyên tắc chung:** ExecutorService quản lý việc xếp lịch và thực thi, **không tự biến exception thành kết quả nghiệp vụ hợp lệ, không tự rollback và không cưỡng chế mọi tác vụ dừng ngay sau interrupt**. Tác vụ, caller và owner của pool phải có trách nhiệm rõ ràng.

## 2. Bức tranh vòng đời tác vụ

Một luồng xử lý phổ biến:

```text
Caller thread
   |
   +-- executor.execute(runnable) --------> worker thread
   |                                          |
   |                                          +-- normal return
   |                                          |
   |                                          +-- throws unchecked exception
   |                                               (worker exits abnormally;
   |                                                uncaught handler may observe)
   |
   +-- Future<T> f = executor.submit(task) -> worker thread
                                              |
                                              +-- normal return
                                              |     -> Future result
                                              |
                                              +-- throws exception
                                              |     -> Future exceptional outcome
                                              |
                                              +-- interrupted/cancelled
                                                    -> cancellation contract
   |
   +-- f.get() -> value / ExecutionException /
                  InterruptedException / CancellationException
```

Hai điều dễ nhầm:

1. **Task chạy trong call stack của worker**. `try/catch` quanh câu `submit()` ở caller không tự bắt lỗi xảy ra về sau trong worker.
2. Một `Future` là **kênh quan sát hoàn tất**, không nhất thiết là bộ điều khiển dừng thực thi cưỡng chế. Caller phải quan sát `get()`, `isCancelled()`, policy hoặc trạng thái riêng; không suy từ một Boolean rằng mọi công việc đã hoàn tác.

Một pool có thể tái sử dụng luồng sau task thành công. Nếu một task được chạy bằng `execute` ném unchecked exception và thoát không được bắt, worker có thể kết thúc bất thường và executor sẽ xử lý vòng đời worker theo implementation; không nên thiết kế logic dựa vào tên hoặc danh tính thread còn giữ nguyên.

## 3. `execute()` và `submit()` báo lỗi khác nhau

### `execute(Runnable)`

`execute()` có chữ ký trả về `void`. Nếu `Runnable.run()` ném unchecked exception không được task tự xử lý, lỗi có thể đi tới **uncaught exception handler** của thread thực thi (với ThreadPoolExecutor thông thường), hoặc cơ chế báo lỗi khác do executor implementation quyết định.

```java
executor.execute(() -> {
    throw new IllegalStateException("work failed");
});
```

Đây là mảnh code có biến `executor` kiểu `ExecutorService` đã khai báo. Một handler ở caller như sau **không bắt** lỗi phát sinh bất đồng bộ trong task:

```java
try {
    executor.execute(() -> doWork());
} catch (RuntimeException ex) {
    // Có thể bắt lỗi nộp tác vụ ngay lúc gọi,
    // không phải tự động bắt mọi lỗi về sau trong doWork().
}
```

Đây là mảnh code có `doWork()` do ứng dụng định nghĩa. Nếu pool đã shutdown hoặc từ chối task, `execute()` có thể ném `RejectedExecutionException` ở luồng **nộp**. Điều đó khác với exception ném khi worker đã bắt đầu chạy.

### `submit(Runnable/Callable)`

`submit()` trả về `Future`. Với những executor phổ biến được tạo từ `Executors` hoặc `ThreadPoolExecutor`, exception phát sinh bên trong task được ghi nhận trong **Future**. Nó không nhất thiết đi qua uncaught exception handler của thread theo cách giống `execute()`.

```java
Future<Integer> future = executor.submit(() -> {
    throw new IllegalArgumentException("bad computation");
});
```

Đây là mảnh code cần import `java.util.concurrent.Future`. Khi caller gọi `future.get()`, ngoại lệ của task được thể hiện bằng `ExecutionException` với `getCause()` chứa lỗi gốc:

```java
try {
    Integer result = future.get();
    use(result);
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    return;
} catch (ExecutionException ex) {
    handleTaskFailure(ex.getCause());
}
```

Đây là mảnh code trong một phương thức `void` có `future`, `use`, `handleTaskFailure` được định nghĩa, import `java.util.concurrent.ExecutionException`. **Không bỏ qua Future được trả về** khi nghiệp vụ phải biết tác vụ đã thành công hay thất bại; nếu không ai đọc/quan sát kết quả, lỗi có thể trở nên rất khó phát hiện.

`submit(Runnable)` trả `Future<?>` có `get()` nhận `null` nếu task kết thúc bình thường. `submit(Runnable, result)` dùng giá trị `result` do caller truyền khi Runnable hoàn tất bình thường. `submit(Callable<T>)` cho phép trả giá trị hoặc ném checked exception theo `Callable.call()`.

## 4. Bảng phân biệt các tín hiệu lỗi quan trọng

| Loại | Xuất hiện ở đâu | Ý nghĩa |
| --- | --- | --- |
| `IllegalStateException`/`IOException` bên trong task | Call stack worker | Lỗi nghiệp vụ, trạng thái hoặc dependency của task |
| `ExecutionException` | Luồng đang gọi `Future.get()` | Task đã hoàn tất bất thường; xem `getCause()` |
| `InterruptedException` từ `Future.get()` | **Thread đang chờ**, không phải tự động từ task | Caller bị yêu cầu ngắt trong lúc chờ |
| `CancellationException` từ `Future.get()` | Luồng đọc Future | Future đã bị hủy; không bảo đảm side effect đã rollback |
| `RejectedExecutionException` ở `execute/submit` | Thread nộp tác vụ | Executor không chấp nhận task theo chính sách |
| `TimeoutException` từ `get(timeout, unit)` | Thread đang chờ | Chờ quá hạn; task có thể vẫn đang chạy |
| `InterruptedException` từ `sleep/take/await` trong worker | Worker task | Worker nhận yêu cầu ngắt và cần quyết định dừng |
| `CompletionException` từ `CompletableFuture.join()` | Thread quan sát CompletableFuture | Wrapper cho exceptional completion của pipeline CompletableFuture |

Cần lưu ý `Future.get()` và `CompletableFuture.join()` khác nhau về wrapper và interruption. Không nên cho mọi thứ đi qua một `catch (Exception)` rồi log và tiếp tục như thành công.

## 5. Ví dụ Java 17+ hoàn chỉnh, chạy được

Ví dụ sau cố ý tách thành bốn kịch bản. Mọi pool đều được đóng trong `finally`, các điểm chờ đều có giới hạn trong phần điều phối, và `CountDownLatch` giúp xác định thứ tự mà không phải đoán thời điểm thread được scheduler chạy.

- **executeFailure:** task `execute` ném unchecked exception, `ThreadFactory` cấu hình uncaught handler để quan sát.
- **submitFailure:** task `submit` ném exception, caller đọc nguyên nhân từ `ExecutionException`.
- **cancelCooperatively:** caller gọi `Future.cancel(true)`; worker đang `sleep` bắt `InterruptedException`, khôi phục cờ và kết thúc.
- **interruptWaiter:** một thread khác chờ `Future.get()` rồi bị interrupt; task trong pool vẫn chờ riêng và tiếp tục được sau khi điều kiện chờ được giải phóng.

Lưu file **`ExecutorErrorInterruptDemo.java`**:

```java
import java.util.concurrent.*;

public class ExecutorErrorInterruptDemo {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void stop(ExecutorService pool) throws InterruptedException {
        pool.shutdown();
        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
            pool.shutdownNow();
            check(pool.awaitTermination(5, TimeUnit.SECONDS), "shutdown timeout");
        }
    }

    static void executeFailure() throws InterruptedException {
        CountDownLatch observed = new CountDownLatch(1);
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "execute-worker");
            thread.setUncaughtExceptionHandler((t, error) -> {
                System.out.println("EXECUTE_HANDLER: "
                        + error.getClass().getSimpleName());
                observed.countDown();
            });
            return thread;
        };
        ExecutorService pool = Executors.newSingleThreadExecutor(factory);
        try {
            pool.execute(() -> {
                throw new IllegalStateException("execute failed");
            });
            check(observed.await(5, TimeUnit.SECONDS), "handler did not run");
            System.out.println("EXECUTE_OBSERVED: true");
        } finally {
            stop(pool);
        }
    }

    static void submitFailure() throws InterruptedException {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> future = pool.submit(() -> {
                throw new IllegalArgumentException("submit failed");
            });
            try {
                future.get();
                throw new AssertionError("expected failure");
            } catch (ExecutionException ex) {
                System.out.println("SUBMIT_CAUSE: "
                        + ex.getCause().getClass().getSimpleName());
            }
        } finally {
            stop(pool);
        }
    }

    static void cancelCooperatively() throws InterruptedException {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        try {
            Future<?> future = pool.submit(() -> {
                started.countDown();
                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException ex) {
                    boolean cleared = !Thread.currentThread().isInterrupted();
                    Thread.currentThread().interrupt();
                    System.out.println("CANCEL_WORKER: cleared=" + cleared
                            + " restored="
                            + Thread.currentThread().isInterrupted());
                } finally {
                    finished.countDown();
                }
            });
            check(started.await(5, TimeUnit.SECONDS), "worker not started");
            boolean cancelled = future.cancel(true);
            check(finished.await(5, TimeUnit.SECONDS), "worker did not finish");
            System.out.println("CANCEL_REQUESTED: " + cancelled);
            try {
                future.get();
                throw new AssertionError("expected cancellation");
            } catch (CancellationException expected) {
                System.out.println("CANCEL_GET: CancellationException");
            } catch (ExecutionException unexpected) {
                throw new AssertionError(unexpected);
            }
        } finally {
            stop(pool);
        }
    }

    static void interruptWaiter() throws InterruptedException {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch waiterStarted = new CountDownLatch(1);
        try {
            Future<String> future = pool.submit(() -> {
                workerStarted.countDown();
                release.await();
                return "result";
            });
            check(workerStarted.await(5, TimeUnit.SECONDS),
                    "worker not started");
            Thread waiter = new Thread(() -> {
                waiterStarted.countDown();
                try {
                    future.get();
                    throw new AssertionError("expected waiter interruption");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    System.out.println("WAITER: restored="
                            + Thread.currentThread().isInterrupted()
                            + " taskDone=" + future.isDone());
                } catch (ExecutionException ex) {
                    throw new AssertionError(ex);
                }
            }, "waiter");
            waiter.start();
            check(waiterStarted.await(5, TimeUnit.SECONDS), "waiter not ready");
            waiter.interrupt();
            waiter.join(5_000);
            check(!waiter.isAlive(), "waiter did not stop");
            System.out.println("TASK_NOT_CANCELLED: " + !future.isCancelled());
            release.countDown();
            try {
                System.out.println("TASK_RESULT: " + future.get());
            } catch (ExecutionException ex) {
                throw new AssertionError(ex);
            }
        } finally {
            release.countDown();
            stop(pool);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        executeFailure();
        submitFailure();
        cancelCooperatively();
        interruptWaiter();
        System.out.println("DEMO: PASS");
    }
}
```

Chạy bằng JDK 17 trở lên từ thư mục chứa file:

```bash
javac -encoding UTF-8 ExecutorErrorInterruptDemo.java
java ExecutorErrorInterruptDemo
```

**Standard output đã kiểm chứng:**

```text
EXECUTE_HANDLER: IllegalStateException
EXECUTE_OBSERVED: true
SUBMIT_CAUSE: IllegalArgumentException
CANCEL_WORKER: cleared=true restored=true
CANCEL_REQUESTED: true
CANCEL_GET: CancellationException
WAITER: restored=true taskDone=false
TASK_NOT_CANCELLED: true
TASK_RESULT: result
DEMO: PASS
```

### Cách đọc từng trường hợp

1. `EXECUTE_HANDLER` chứng minh unchecked exception thoát khỏi `Runnable` dùng `execute()` và được custom handler quan sát. Không cần in stack trace mặc định nên output ổn định.
2. `SUBMIT_CAUSE` cho thấy `Future.get()` không ném trực tiếp `IllegalArgumentException`, mà dùng `ExecutionException` với cause.
3. `CANCEL_WORKER` xác nhận worker đang ngủ phản ứng với interrupt. `Thread.sleep` ném `InterruptedException` và xóa cờ; code kiểm tra `cleared=true` rồi `interrupt()` để đặt cờ lại.
4. `CANCEL_REQUESTED: true` cho biết yêu cầu hủy được chấp nhận cho Future đang chạy trong tình huống demo. **Đó không phải cam kết rằng mọi `cancel(true)` đều trả true hoặc mọi task sẽ dừng.**
5. `CANCEL_GET` nhận `CancellationException` trực tiếp sau khi Future đã hủy; không có kết quả thành công để đọc.
6. `WAITER` cho biết thread đang chờ `get()` đã bị interrupt và khôi phục flag; tại lúc đó, Future của worker vẫn chưa hoàn tất.
7. `TASK_NOT_CANCELLED: true` và `TASK_RESULT: result` chứng minh **ngắt một thread đang chờ Future không tự cancel task nguồn**.
8. `DEMO: PASS` xuất hiện sau khi cả bốn phần đã đi hết đường thành công của phép kiểm chứng và các pool được yêu cầu đóng.

Ví dụ dùng timeout 5 giây để phát hiện các tình huống không hoạt động như dự kiến. Trên máy quá tải hoặc môi trường bị can thiệp khác thường, test thời gian thực vẫn có thể cần timeout rộng hơn; không nên coi chính con số 5 giây là một bảo đảm của Java. Chương trình không truy cập mạng/database và không dùng dữ liệu nghiệp vụ thật.

## 6. `ThreadFactory` và `UncaughtExceptionHandler`

Với `execute()` và worker không bắt exception, một `Thread.UncaughtExceptionHandler` do `ThreadFactory` cấu hình có thể giúp ghi nhận thất bại:

```java
ThreadFactory factory = runnable -> {
    Thread thread = new Thread(runnable, "worker");
    thread.setUncaughtExceptionHandler((t, error) -> {
        reportUnexpectedFailure(error);
    });
    return thread;
};
```

Đây là mảnh code cần import `ThreadFactory` và định nghĩa `reportUnexpectedFailure`. Nó phù hợp khi tác vụ chạy theo kênh `execute` và executor implementation cho phép unchecked exception thoát khỏi worker.

**Không thể suy luận rằng uncaught handler sẽ ghi nhận mọi lỗi từ mọi task `submit()`.** Các implementation thường đóng gói task nộp qua `submit` vào `FutureTask` và giữ Throwable làm kết quả exceptional. Nếu chỉ dựa vào handler, bạn có thể bỏ sót lỗi trong Future.

Một số framework executor có quy trình báo lỗi riêng. Đừng ép một mô hình ThreadFactory lên mọi `ExecutorService` do thư viện cung cấp mà không đọc contract của chính implementation ấy.

Đừng để handler log nguyên exception từ dependency chứa password/token/PII hoặc tự ném exception làm mất thêm thông tin vận hành. Chọn event code, operation, correlation ID an toàn và nơi ghi có phân quyền.

## 7. Quan sát kết quả `Future.get()` đúng cách

Mẫu xử lý ở caller:

```java
try {
    Result value = future.get();
    use(value);
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    return;
} catch (CancellationException ex) {
    recordCancelled();
} catch (ExecutionException ex) {
    Throwable cause = ex.getCause();
    handleTaskFailure(cause);
}
```

Đây là **mảnh code trong một phương thức trả `void`**, cần `Result` và các hàm ứng dụng cùng import từ `java.util.concurrent`.

- `InterruptedException`: **luồng chờ** đã nhận yêu cầu interrupt. Bắt rồi bỏ qua và gọi `get()` lần nữa có thể phá deadline/shutdown.
- `CancellationException`: Future đã bị hủy. Caller phải báo tình trạng CANCELLED thay vì tự coi nó là thành công hoặc lỗi tính toán thông thường.
- `ExecutionException`: task kết thúc bất thường do cause. Tùy loại cause, chính sách có thể là validation, dependency failure, bug hoặc retryable failure.
- Kết quả `get()` bình thường: Future đã hoàn tất thành công theo contract của task, nhưng điều đó **không** tự chứng minh mọi side effect bên ngoài đã được commit an toàn nếu task viết thiếu kiểm tra.

Với `future.get(timeout, TimeUnit.SECONDS)`, còn phải xử lý `TimeoutException`. Timeout của việc **chờ** không tự hủy Future; nếu muốn yêu cầu hủy task, caller phải quyết định gọi `cancel(true)` hoặc cơ chế hủy khác phù hợp, cùng chiến lược đối chiếu side effect.

### Không quan sát Future là một dạng mất dấu lỗi

```java
executor.submit(() -> sendReport());
```

Đây là mảnh code có `sendReport` được định nghĩa. Nếu không ai giữ hoặc đọc Future, việc task lỗi có thể khó nhìn thấy, nhất là khi không có logging riêng trong task. Cân nhắc `Future.get()`, `ExecutorCompletionService`, callback giám sát hoặc cơ chế job result khi hệ thống cần xác nhận trạng thái.

Nếu ứng dụng cố tình làm *fire-and-forget*, nó vẫn phải có chính sách quan sát thất bại và không được nhầm “đã nộp task” với “đã hoàn thành task”.

## 8. Xử lý `InterruptedException` ở trong worker

Interruption là cơ chế **yêu cầu hợp tác**. Khi caller gọi `thread.interrupt()` hoặc `future.cancel(true)`, worker có thể nhận tín hiệu qua cờ interrupt hoặc qua exception từ API blocking.

Một số blocking API như `Thread.sleep`, `BlockingQueue.take`, `CountDownLatch.await` hoặc `Semaphore.acquire` có thể ném `InterruptedException`. Nhiều API loại này **xóa cờ interrupt khi ném**. Vì vậy handler dưới đây nguy hiểm:

```java
while (true) {
    try {
        Job job = queue.take();
        process(job);
    } catch (InterruptedException ignored) {
        // Sai: coi như chưa hề có yêu cầu dừng.
    }
}
```

Đây là mảnh code minh họa lỗi, với `Job`, `queue` và `process` do ứng dụng định nghĩa. Sau khi cờ bị xóa, vòng loop có thể quay lại `take()` và block tiếp, khiến pool không thể dừng theo mong muốn.

Nếu phương thức có thể khai báo checked exception:

```java
Job receive(BlockingQueue<Job> queue) throws InterruptedException {
    return queue.take();
}
```

Đây là mảnh code yêu cầu import `BlockingQueue` và định nghĩa `Job`. **Truyền tiếp** giữ tín hiệu cho tầng quản lý.

Trong `Runnable.run()` không được khai báo checked exception:

```java
@Override
public void run() {
    try {
        while (!Thread.currentThread().isInterrupted()) {
            process(queue.take());
        }
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
    } finally {
        releaseResources();
    }
}
```

Đây là mảnh class có `queue`, `process`, `releaseResources`. Nó **khôi phục flag và rời task**, còn cleanup luôn được thử.

**Khôi phục cờ mà tiếp tục vòng `while(true)` không có điều kiện thoát** cũng sai: lệnh blocking tiếp theo có thể lập tức ném và xóa cờ, handler lại đặt cờ, tạo *busy loop*. Luôn kết hợp chính sách interrupt với điều khiển vòng đời.

Không bắt buộc mọi nơi phải restore cờ nếu `InterruptedException` được **truyền tiếp đúng contract** hoặc tầng đó thực sự sở hữu và xử lý xong yêu cầu hủy. Vấn đề là nuốt tín hiệu một cách vô thức.

## 9. `Thread.interrupted()` và `isInterrupted()` khác nhau

Khi viết worker loop:

- `Thread.currentThread().isInterrupted()` đọc trạng thái luồng hiện tại **không xóa cờ**.
- `Thread.interrupted()` là phương thức static đọc trạng thái **luồng hiện tại và xóa cờ**.

Đoạn này có thể vô tình xóa mất tín hiệu:

```java
if (Thread.interrupted()) {
    logSomething();
}
// Một đoạn mã sau đó có thể không còn thấy cờ interrupted.
```

Đây là mảnh code có `logSomething` do ứng dụng định nghĩa. Nếu mục tiêu là kiểm tra mà vẫn giữ yêu cầu hủy, dùng `isInterrupted()`.

Tuy nhiên, cờ interrupt **không phải lệnh kill**. Nếu task chỉ tính CPU không gọi API interruptible, nó cần kiểm tra cờ định kỳ ở những điểm phù hợp. Ví dụ:

```java
while (hasMoreWork()) {
    if (Thread.currentThread().isInterrupted()) {
        return;
    }
    processNextChunk();
}
```

Đây là mảnh code trong phương thức có `hasMoreWork` và `processNextChunk`. Kiểm tra đủ thường xuyên để đáp ứng cancellation nhưng không cần làm ở từng phép toán nhỏ.

Nếu một tác vụ có I/O không phản ứng nhanh với interrupt, phải dùng timeout, close/cancel API của tài nguyên hoặc contract phụ thuộc thích hợp. Không chỉ dựa vào `interrupt()` để hứa một deadline cứng.

## 10. `Future.cancel(true)` là yêu cầu, không phải phép dừng cưỡng chế

`Future.cancel(boolean mayInterruptIfRunning)` yêu cầu hủy tác vụ nếu chưa hoàn tất. Giá trị `true` cho phép implementation cố ngắt thread đang chạy; `false` không yêu cầu interrupt task đang chạy. Kết quả thực tế phụ thuộc thời điểm và implementation.

Những điểm cần hiểu:

1. Nếu Future đã hoàn tất, `cancel(...)` thường không thay đổi trạng thái.
2. Với task còn đang chạy, `cancel(true)` có thể đặt trạng thái Future là cancelled và gửi interrupt cho worker.
3. **`Future.isCancelled() == true` không chứng minh worker đã dừng mọi lệnh tại thời điểm đó.**
4. Task nuốt `InterruptedException` có thể tiếp tục chạy sau khi Future đã bị cancel.
5. Một side effect đã được ghi vào DB hoặc gửi qua mạng **không tự rollback** khi cancel.
6. `future.get()` sau cancellation ném `CancellationException` trực tiếp; nó không phải luôn được bọc trong `ExecutionException`.

Mẫu:

```java
boolean accepted = future.cancel(true);
if (accepted) {
    recordCancellationRequested();
}
```

Đây là mảnh code với `future`, `recordCancellationRequested`. Tên hàm cố tình dùng **Requested**: caller nên tách yêu cầu hủy khỏi xác nhận rằng tác vụ đã đạt trạng thái cuối an toàn.

Với tác vụ thanh toán, upload, gửi email, ghi file hoặc commit database, cần chính sách idempotency/transaction/compensation riêng. Đừng tự động retry toàn bộ task sau khi Future bị cancel mà không biết thao tác có thực hiện một phần hay không.

## 11. Ngắt caller đang đợi không tự hủy worker

Một trường hợp thường bị nhầm:

```java
Future<String> future = executor.submit(this::load);
String result = future.get();
```

Đây là mảnh code trong một class có `load()` phù hợp. Nếu **luồng caller** bị interrupt trong khi `get()` đang block, `get()` có thể ném `InterruptedException`. Nhưng điều đó **không tự gọi `future.cancel(true)`** và không tự khiến worker biết caller không còn muốn kết quả.

Đường đi thực tế:

```text
worker: đang chờ dependency ............... -> hoàn tất về sau
caller: gọi future.get()
another thread: interrupt(caller)
caller: InterruptedException -> dừng chờ
worker: không tự bị interrupt bởi sự kiện đó
```

Trong demo, caller-waiter kết thúc với `taskDone=false` nhưng Future nguồn vẫn có thể trả `result` sau khi main giải phóng latch.

Nếu hủy tác vụ nguồn là chính sách mong muốn, caller có thể **cân nhắc**:

```java
try {
    return future.get();
} catch (InterruptedException ex) {
    future.cancel(true);
    Thread.currentThread().interrupt();
    throw ex;
}
```

Đây là **mảnh phương thức** khai báo `throws InterruptedException` và phù hợp kiểu return. Việc gọi `cancel(true)` có ý nghĩa khi task thực sự thuộc phạm vi hủy của caller và implementation cho phép interrupt. Trong một hệ thống có công việc dùng chung giữa nhiều request, tự cancel future nguồn khi một caller dừng chờ **có thể ảnh hưởng người dùng khác**; cần quyết định quyền sở hữu.

## 12. `shutdown()`, `shutdownNow()` và `awaitTermination()`

Ba API phục vụ lifecycle của executor:

| API | Hành vi cơ bản |
| --- | --- |
| `shutdown()` | Không nhận tác vụ mới, cho những tác vụ đã nộp được tiếp tục theo contract |
| `shutdownNow()` | Cố dừng các tác vụ đang chạy, thường bằng interrupt, và trả danh sách tác vụ chưa bắt đầu |
| `awaitTermination(timeout, unit)` | **Thread đang gọi** chờ executor chấm dứt hoặc hết hạn, có thể ném `InterruptedException` |

`shutdown()` không chờ cho mọi task hoàn thành. `shutdownNow()` là **best effort**; task bỏ qua interrupt có thể vẫn chạy. Danh sách trả về từ `shutdownNow()` không phải “mọi task đã thất bại”; nó chủ yếu mô tả những tác vụ chưa bắt đầu trong hàng chờ theo contract.

Mẫu lifecycle phổ biến (mảnh code trong phương thức sở hữu executor):

```java
executor.shutdown();
try {
    if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
        executor.shutdownNow();
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            reportPoolDidNotTerminate();
        }
    }
} catch (InterruptedException ex) {
    executor.shutdownNow();
    Thread.currentThread().interrupt();
}
```

Đây là đoạn có `executor`, `reportPoolDidNotTerminate` và import `TimeUnit`. Hai số 30 giây chỉ minh họa chính sách; chọn thời hạn theo SLA và đặc tính task.

Quan trọng:

- `shutdownNow()` không bảo đảm tất cả worker đã kết thúc.
- `awaitTermination()` bị interrupt là tín hiệu dành cho **caller đang quản lý shutdown**; cần restore cờ nếu handler không truyền checked exception tiếp.
- Với một pool không được dừng, phải báo rõ trạng thái thay vì in “shutdown complete”.
- Chỉ shutdown pool khi thành phần hiện tại **sở hữu** pool; đừng đóng shared executor của framework tại mỗi request.

## 13. Cấu trúc quản lý pool bằng try/finally

Một scope ngắn có thể:

```java
ExecutorService executor = Executors.newFixedThreadPool(4);
try {
    Future<Result> first = executor.submit(this::firstJob);
    Future<Result> second = executor.submit(this::secondJob);
    return combine(first.get(), second.get());
} finally {
    executor.shutdown();
}
```

Đây là **mảnh code** trong phương thức có hợp đồng checked exception thích hợp và các hàm/kiểu ứng dụng. Nó đảm bảo **gửi yêu cầu shutdown** dù có exception. Tuy nhiên, `shutdown()` **không đợi** các worker hoàn tất. Nếu bắt buộc đợi termination hoặc cancel task còn chạy, cần mẫu `awaitTermination`/`shutdownNow` bổ sung.

Một số thiết kế có thể dùng executor làm trường lâu dài, khởi tạo ở startup và shutdown trong lifecycle của ứng dụng; không tạo pool mới cho mỗi đơn hàng rồi bỏ quên không đóng. Với shared executor, ownership và ranh giới đóng càng quan trọng.

Với JDK mới hơn có thể có tiện ích lifecycle bổ sung, nhưng ví dụ ở đây chỉ dùng API bảo đảm có trong **Java 17**. Không dựa vào `ExecutorService.close()` như phương thức phổ quát trên Java 17.

## 14. `RejectedExecutionException` không phải lỗi do body task

Nếu pool đã shutdown hoặc hàng chờ/chính sách từ chối không cho nhận thêm task:

```java
try {
    Future<?> future = executor.submit(task);
    track(future);
} catch (RejectedExecutionException ex) {
    recordNotScheduled();
}
```

Đây là mảnh code có `task`, `track`, `recordNotScheduled` và import tương ứng.

`RejectedExecutionException` xảy ra ở **lúc nộp tác vụ**, không chứng minh logic trong task đã chạy hay thất bại. Caller nên phân biệt:

- **Not accepted:** chưa được executor tiếp nhận; có thể chuyển qua hàng đợi khác theo policy.
- **Accepted but failed:** task đã được nộp và hoàn tất bất thường; xem Future.
- **Accepted then cancelled:** Future bị hủy; task có thể đã bắt đầu và có side effect.
- **Still running:** chưa biết kết quả cuối cùng.

Nếu cấu hình `RejectedExecutionHandler` đặc biệt như `CallerRunsPolicy`, một task có thể được thực thi trong **thread của caller** khi bị từ chối đưa vào pool (trong các điều kiện của policy). Khi ấy giả định “mọi task đều chạy ở worker thread riêng” cũng sai. Luôn hiểu policy và cấu hình executor đang dùng.

Không retry `submit()` vô hạn khi executor từ chối: có thể gây vòng lặp nóng, tăng tải hoặc tạo backpressure ngược. Giải pháp cần ngân sách retry và ranh giới chịu trách nhiệm rõ.

## 15. `invokeAll` và `invokeAny`: quan sát nhiều tác vụ

Ngoài `submit()`, `ExecutorService` còn có các API thực thi theo nhóm.

### `invokeAll(...)`

`invokeAll(tasks)` nộp nhiều task và chờ tất cả hoàn tất theo hợp đồng của overload. Nó trả danh sách `Future`. **Một Future có thể hoàn tất exceptional trong khi `invokeAll` vẫn trả danh sách.** Caller muốn phát hiện lỗi task phải đọc `get()` trên từng Future:

```java
List<Future<String>> futures = executor.invokeAll(tasks);
for (Future<String> f : futures) {
    try {
        consume(f.get());
    } catch (ExecutionException ex) {
        recordTaskFailure(ex.getCause());
    }
}
```

Đây là mảnh code có `tasks` là collection `Callable<String>`, các hàm ứng dụng và imports từ `java.util.concurrent` cùng `java.util.List`; phương thức bao quanh khai báo/giải quyết `InterruptedException`. Với overload có timeout, những task chưa hoàn tất khi hết thời gian có thể bị cancel theo contract; đừng đọc từng Future như thể tất cả đều thành công.

### `invokeAny(...)`

`invokeAny(tasks)` tìm **một tác vụ hoàn tất thành công** và có thể hủy những task còn lại theo contract. Nếu không có tác vụ nào thành công, caller có thể nhận `ExecutionException`; `InterruptedException` từ caller cũng cần được xử lý. Overload có timeout có thể ném `TimeoutException`.

Không suy ra task bị yêu cầu hủy đã rollback. Nếu những Callable tạo side effect, việc chạy nhiều bản song song rồi lấy kết quả thành công đầu tiên cần thiết kế idempotency, không chỉ dùng `invokeAny`.

## 16. `ExecutorCompletionService` để thu kết quả theo thứ tự hoàn tất

Khi có nhiều task hoàn tất ở các thời điểm khác nhau, có thể dùng `ExecutorCompletionService<T>`:

```java
ExecutorCompletionService<String> completion =
        new ExecutorCompletionService<>(executor);
completion.submit(this::jobA);
completion.submit(this::jobB);

for (int i = 0; i < 2; i++) {
    Future<String> done = completion.take();
    try {
        use(done.get());
    } catch (ExecutionException ex) {
        handleTaskFailure(ex.getCause());
    }
}
```

Đây là mảnh code với `jobA`, `jobB`, `use`, `handleTaskFailure` do ứng dụng định nghĩa. Method phải xử lý hoặc khai báo `InterruptedException`. `take()` chờ một Future đã hoàn tất; `get()` của Future đó mới tiết lộ kết quả hoặc lỗi. Đừng quên đếm/giữ các task đã nộp để tránh chờ nhiều lượt hơn số task có thật.

Nếu `take()` bị interrupt, đó là tình huống của thread đang lấy kết quả; policy hủy những task nguồn cần quyết định riêng. Việc Future trở thành done không có nghĩa chắc chắn thành công, vì trạng thái done gồm kết quả, lỗi và hủy.

## 17. Bắt exception trong task: khi nào nên làm?

Không phải lúc nào cũng để task ném exception. Có thể xử lý tại chính task nếu nó **có chính sách phục hồi hợp lệ**, chẳng hạn retry một thao tác mạng có giới hạn và không gây side effect trùng, chuyển một lỗi đọc cache thành giá trị cache hợp lệ hoặc cleanup tài nguyên:

```java
Callable<Result> task = () -> {
    try {
        return remoteCall();
    } catch (KnownTemporaryFailure ex) {
        return validFallback();
    }
};
```

Đây là mảnh code có `Result`, `KnownTemporaryFailure`, `remoteCall`, `validFallback` tự định nghĩa. Nếu fallback thật sự hợp lệ theo contract, Future thành công với giá trị fallback là có chủ đích. Nếu fallback chỉ để giấu lỗi, đây là **success giả**.

Không nên:

```java
Callable<Boolean> task = () -> {
    try {
        chargeAccount();
        return true;
    } catch (Exception ignored) {
        return false;
    }
};
```

Đây là mảnh code có `chargeAccount` tự định nghĩa, cố tình minh họa sai lầm. Nếu thanh toán đã commit rồi mất phản hồi, trả `false` không chứng minh chưa trừ tiền. Caller retry có thể tạo giao dịch trùng.

Nếu task có thể ném checked exception, `Callable.call()` đã cho phép điều đó. Không cần bọc mọi exception thành `RuntimeException` chỉ để vừa chữ ký như `Runnable.run()`.

## 18. Truyền lỗi qua Future không hoàn tác side effect

Một task có thể thực hiện:

```java
Callable<String> task = () -> {
    database.commit();
    notification.send();
    return "DONE";
};
```

Đây là mảnh minh họa cần định nghĩa `database`, `notification` và contract của chúng. Nếu `commit()` thành công nhưng `notification.send()` ném exception, `Future.get()` báo thất bại, nhưng database **có thể đã commit**.

Khi gặp lỗi sau một thao tác có side effect:

1. Biết bước nào hoàn tất chắc chắn, bước nào chưa xác định.
2. Dùng transaction khi cùng một tài nguyên hỗ trợ; không coi Future cancellation là rollback.
3. Với hai hệ thống, cân nhắc outbox/compensation hoặc trạng thái trung gian có thể đối chiếu.
4. Retry có hạn và có idempotency key khi phù hợp.
5. Ghi nhận error code và correlation ID không nhạy cảm để điều tra.
6. Không coi `ExecutionException` là bằng chứng “task chưa làm gì”.

Đây là lý do xử lý exception/cancel phải gắn với hợp đồng nghiệp vụ, không chỉ với type của Throwable.

## 19. `ThreadPoolExecutor.afterExecute`: điểm quan sát chuyên biệt

Trong trường hợp cần theo dõi nhiều task ở mức executor, `ThreadPoolExecutor` có hook `afterExecute(Runnable r, Throwable t)`. Tuy nhiên, **không được mặc định `t` luôn khác null khi một task `submit()` thất bại**: với `FutureTask`, exception có thể đã được giữ trong Future.

Mẫu khái niệm:

```java
@Override
protected void afterExecute(Runnable runnable, Throwable error) {
    super.afterExecute(runnable, error);
    if (error == null && runnable instanceof Future<?> future
            && future.isDone()) {
        try {
            future.get();
        } catch (CancellationException cancelled) {
            recordCancelled();
        } catch (ExecutionException failed) {
            recordTaskFailure(failed.getCause());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    } else if (error != null) {
        recordTaskFailure(error);
    }
}
```

Đây là **mảnh bên trong subclass `ThreadPoolExecutor`**, không phải chương trình độc lập; cần imports và các hàm ghi nhận lỗi. Hook phải ngắn, không chặn vô hạn, không thêm side effect không kiểm soát. Đặt `future.isDone()` giúp tránh gọi `get()` trên Future chưa hoàn tất theo những triển khai không chuẩn; trong trường hợp bình thường task vừa hoàn tất.

Đây là kỹ thuật nâng cao. Đối với ứng dụng nhỏ, **giữ và quan sát Future** thường đơn giản hơn việc kế thừa executor. Cũng phải kiểm soát logging lỗi ở hook để không ghi trùng với caller hoặc làm lộ message nhạy cảm của dependency.

## 20. Test đường lỗi và ngắt một cách xác định

Để kiểm thử executor, dùng `CountDownLatch`, `Future`, thread-safe counters, `AtomicBoolean` và timeout hơn là `Thread.sleep(100)` rồi đoán thread đã làm gì. Một test có thể đặt hai latch:

- `started`: worker đã đạt vị trí chờ/có thể bị interrupt.
- `finished`: worker đã đi qua cleanup và sắp kết thúc.

Ví dụ tối giản:

```java
CountDownLatch started = new CountDownLatch(1);
CountDownLatch finished = new CountDownLatch(1);

Future<?> task = executor.submit(() -> {
    started.countDown();
    try {
        Thread.sleep(60_000);
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
    } finally {
        finished.countDown();
    }
});

if (!started.await(5, TimeUnit.SECONDS)) {
    throw new AssertionError("task did not start");
}
task.cancel(true);
if (!finished.await(5, TimeUnit.SECONDS)) {
    throw new AssertionError("task did not finish");
}
```

Đây là **mảnh code** trong method có `executor` và imports tương ứng, khai báo/giải quyết `InterruptedException`. Không cần thực tế ngủ một phút: `cancel(true)` làm worker phản ứng với interrupt trong demo. Tuy nhiên, nếu một executor implementation/task khác không hợp tác, thời gian chờ sẽ hết và test báo lỗi.

**Thử nghiệm không nên dựa vào `Future.isDone()` duy nhất để khẳng định worker đã thoát:** sau cancellation, Future có thể báo done sớm hơn việc worker dừng. Dùng latch hoặc signal từ worker khi thật sự cần chứng minh cleanup đã chạy.

## 21. Ma trận kiểm thử thực hành

| Trường hợp | Điều cần xác minh |
| --- | --- |
| `execute` với Runnable ném unchecked exception | Uncaught handler/giám sát của executor nhận lỗi theo contract |
| `submit` với Callable ném exception | `get()` ném `ExecutionException`, cause là lỗi task |
| `submit` thành công | `get()` trả đúng giá trị |
| `submit(Runnable)` thành công | `get()` thường trả `null` |
| Task bị `cancel(true)` khi đang `sleep` | Task được yêu cầu interrupt; worker hợp tác và có cleanup |
| Worker nuốt `InterruptedException` | Test phát hiện worker vẫn chạy trái ý định |
| Caller `get()` bị interrupt | Caller xử lý interruption, Future nguồn không tự bị cancel |
| Future bị cancel | `get()` ném `CancellationException` |
| Caller `get(timeout)` hết hạn | Ném `TimeoutException`, task không tự bị hủy |
| Executor đã shutdown rồi `submit` | Caller nhận rejection theo policy |
| `shutdownNow()` với worker không hợp tác | Không giả định pool dừng ngay; kiểm tra termination timeout |
| `invokeAll` có một task lỗi | Duyệt các Future và xem cause, không chỉ xem list được trả |
| Task đã commit rồi bị lỗi sau đó | Không retry mù; trạng thái side effect đúng contract |
| Cleanup tự ném exception | Cause/suppressed và trạng thái được ghi nhận đúng |
| `afterExecute` khi submit thất bại | Không phụ thuộc `Throwable t` khác null |
| Shared executor bị shutdown nhầm | Test ownership/lifecycle, không đóng pool của thành phần khác |

Những test này phải có **giới hạn thời gian** và cleanup. Một worker bị lập trình sai có thể từ chối dừng; không được để toàn bộ bộ kiểm thử treo vô hạn. Với thread không hợp tác, cân nhắc cách ly test và cơ chế giới hạn ở cấp process.

## 22. Những lỗi phổ biến trong code thực tế

| Anti-pattern | Hậu quả | Cách sửa |
| --- | --- | --- |
| Gọi `submit()` rồi bỏ Future | Task lỗi nhưng không ai quan sát | Giữ Future hoặc hệ thống báo kết quả |
| `catch (Exception ignored)` trong task | Che validation, bug và interrupt | Phân loại, chỉ xử lý lỗi có policy rõ |
| `catch (InterruptedException ex) {}` | Mất tín hiệu cancel | Truyền tiếp hoặc restore và thoát |
| Restore flag rồi quay lại `while(true)` | Loop nóng, task không dừng | `return`/`break` hoặc kiểm tra flag |
| `cancel(true)` rồi khẳng định “đã rollback” | Side effect có thể đã xảy ra | Transaction/idempotency/đối chiếu |
| Bị interrupt lúc `get()` rồi giả định task cũng bị hủy | Worker có thể vẫn chạy | Quyết định cancel nguồn theo ownership |
| Chỉ gọi `shutdown()` và in “all tasks done” | `shutdown()` không chờ termination | `awaitTermination` có timeout |
| `shutdownNow()` rồi kỳ vọng luôn chấm dứt | Best effort; task có thể không hợp tác | Kiểm tra termination và báo thất bại |
| Retry mọi `ExecutionException` | Bug và lỗi vĩnh viễn cũng bị retry | Phân loại `getCause()` |
| Dùng `get(timeout)` rồi không quản lý task | Timeout caller nhưng task tiếp tục | Cancel/deadline policy phù hợp |
| Chỉ dựa uncaught handler để giám sát `submit` | `FutureTask` giữ lỗi, handler không thấy | Quan sát Future |
| Mỗi tầng log Throwable đầy đủ | Trùng log, nguy cơ lộ secret | Một boundary quan sát và log an toàn |
| Tắt shared executor ở một request | Request khác bị rejection | Xác định owner lifecycle |

## 23. Checklist triển khai

1. Xác định executor thuộc thành phần nào và **ai có quyền shutdown**.
2. Chọn `execute` hay `submit` theo kênh giám sát lỗi/kết quả mong muốn.
3. Với `submit`, luôn có cơ chế **quan sát Future** nếu kết quả task quan trọng.
4. Phân biệt rejection ngay khi nộp với failure xảy ra trong worker.
5. Ở `Future.get()`, xử lý riêng `InterruptedException`, `ExecutionException`, `CancellationException` và timeout nếu có.
6. Task hỗ trợ cancellation phải có điểm check interrupt, hoặc sử dụng API interruptible.
7. Khi bắt `InterruptedException` trong `Runnable`, nếu dừng task, restore flag và **rời task**; không nuốt rồi chạy tiếp.
8. Không xem `cancel(true)`, `isDone()` hay `shutdownNow()` là bằng chứng cleanup/side effect hoàn tất.
9. Dùng `try-with-resources`/`finally` cho tài nguyên của từng task, transaction/idempotency cho trạng thái nghiệp vụ.
10. Shutdown có thời hạn và báo rõ khi pool không chấm dứt.
11. Trong test, dùng latch/signal xác định thay cho timing đoán mò.
12. Logging exception an toàn, tránh raw token, request body, PII và những bản ghi lặp.
13. Retry phải có điều kiện, giới hạn và policy không gây tác dụng phụ trùng.
14. Xác nhận cả đường success, failure, rejection, timeout, interrupt, cancel và cleanup.

**Kết luận:** khi thực hành với `ExecutorService`, đừng chỉ chứng minh “task được chạy”. Cần chứng minh **lỗi được quan sát đúng kênh**, **ngắt được xử lý đúng luồng**, **cancellation có tác dụng hợp tác**, và **executor kết thúc theo chính sách**. `execute()`, `submit()`, `Future.get()` và `cancel(true)` cung cấp những cơ chế khác nhau; hiểu rõ chúng là chìa khóa tránh nuốt lỗi, treo worker hoặc báo thành công giả.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-practice-009">So sánh phục hồi lỗi bằng exceptionally, handle và whenComplete</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh phục hồi lỗi bằng `exceptionally`, `handle` và `whenComplete` trong Java

Một `CompletableFuture` có thể kết thúc **bình thường** với một giá trị hoặc **bất thường** (*exceptional completion*) vì một lỗi. Khi xây dựng pipeline bất đồng bộ, ba phương thức `exceptionally`, `handle` và `whenComplete` thường xuất hiện cạnh nhau. Tất cả đều cho phép phản ứng với lỗi, nhưng **không có cùng mục đích**:

- **`exceptionally`**: chỉ chạy khi stage trước thất bại; có thể **phục hồi bằng một giá trị** cùng kiểu kết quả.
- **`handle`**: chạy khi stage trước thành công **hoặc** thất bại; có thể **chuyển cả hai trường hợp thành một kết quả mới**, thậm chí khác kiểu.
- **`whenComplete`**: chạy khi stage trước thành công **hoặc** thất bại để **quan sát/ghi nhận**; bình thường giữ kết quả hoặc lỗi của stage trước, chứ không phải API tạo giá trị dự phòng.

Điểm dễ nhầm nhất là viết `whenComplete((value, error) -> logger.error(...))` rồi tưởng lỗi đã được xử lý xong. **Ghi log không đồng nghĩa phục hồi.** Nếu stage trước bị lỗi, stage sau `whenComplete` thường vẫn bị lỗi, và `join()`/`get()` vẫn báo thất bại.

Bài thực hành này dùng Java 17+, các chương trình có thể biên dịch và chạy độc lập, để phân biệt cách truyền trạng thái, giá trị dự phòng, callback tự ném ngoại lệ và tác động đến pipeline nhiều bước.

## 1. Mô hình: một stage hoàn tất theo một trong hai nhánh

Một `CompletableFuture<T>` đại diện cho một kết quả hoàn tất:

```text
CompletableFuture<T>
       |
       +-- Thành công ----------------> value: T
       |                                  |
       |                         thenApply / thenAccept
       |
       +-- Thất bại ------------------> error: Throwable
                                          |
                          exceptionally / handle / whenComplete
```

Không nên dùng `isDone() == true` để kết luận công việc thành công: một future hoàn tất do lỗi hoặc hủy cũng được xem là đã hoàn tất. Khi cần chẩn đoán, `join()` có thể ném `CompletionException` bọc nguyên nhân; `get()` có thể ném `ExecutionException`. Một future bị hủy trực tiếp có thể ném `CancellationException`.

Giả sử:

```java
CompletableFuture<Integer> success =
        CompletableFuture.completedFuture(7);
CompletableFuture<Integer> failure =
        CompletableFuture.failedFuture(
                new IllegalArgumentException("bad input"));
```

Đây là hai câu lệnh minh họa trong phạm vi có `import java.util.concurrent.CompletableFuture;`. `completedFuture` và `failedFuture` có từ Java 9; trên Java 17 chúng dùng được. Khi gọi các callback bên dưới, không cần giả lập thread hay đợi tác vụ mạng: cả hai future đã có trạng thái cuối, giúp bài thực hành có kết quả xác định.

**Stage nguồn và stage mới là hai đối tượng khác nhau** đối với các phương thức trong bài. Ví dụ `CompletableFuture<Integer> recovered = failure.exceptionally(e -> -1)` không sửa trạng thái lỗi của `failure`; nó tạo một stage phụ thuộc mới có thể hoàn tất bình thường. Đừng hiểu nhầm việc stage sau phục hồi là tác vụ gốc đã thành công.

## 2. Bảng đối chiếu chính xác ba phương thức

| Tiêu chí | `exceptionally` | `handle` | `whenComplete` |
| --- | --- | --- | --- |
| Chạy khi stage trước thành công? | **Không** | **Có** | **Có** |
| Chạy khi stage trước thất bại? | **Có** | **Có** | **Có** |
| Dữ liệu callback nhận | `Throwable error` | `T value`, `Throwable error` | `T value`, `Throwable error` |
| Callback trả gì? | Giá trị `T` thay thế | Giá trị mới kiểu `U` | `void` |
| Có thể biến thất bại thành thành công? | **Có**, khi callback trả bình thường | **Có**, khi callback trả bình thường | **Không bằng cách trả một giá trị** |
| Khi callback thành công và nguồn đã lỗi | Stage mới nhận fallback | Stage mới nhận giá trị đã xử lý | Stage mới vẫn truyền lỗi cũ |
| Khi callback tự ném lỗi | Stage mới thất bại với lỗi callback | Stage mới thất bại với lỗi callback | Nếu nguồn thành công: lỗi callback làm stage mới thất bại; nếu nguồn đã thất bại: lỗi nguồn được ưu tiên |
| Đổi kiểu kết quả từ `T` sang `U`? | Không (cùng `T`) | Có | Không (giữ `T`) |
| Mục đích tiêu biểu | Phục hồi lỗi bằng fallback | Tổng hợp success/failure thành một kiểu kết quả | Logging, metrics, cleanup/quan sát |
| Đối tượng trả về | Stage mới | Stage mới | Stage mới |

**Ghi chú về lỗi trong `whenComplete`:** theo hợp đồng `CompletionStage`, nếu callback tự ném lỗi, stage trả về hoàn tất lỗi với ngoại lệ callback **trừ khi stage nguồn đã hoàn tất lỗi**. Trong tình huống cả hai đều lỗi, nguồn vẫn là lỗi quyết định trạng thái của stage trả về. Không dựa vào việc lỗi callback tự động được thêm vào `getSuppressed()` của lỗi nguồn; đó không phải một bảo đảm của API.

## 3. Chữ ký và ý nghĩa của giá trị trả về

Các chữ ký được lược bỏ một số chi tiết generic:

```java
CompletableFuture<T> exceptionally(
        Function<Throwable, ? extends T> fn);

<U> CompletableFuture<U> handle(
        BiFunction<? super T, Throwable, ? extends U> fn);

CompletableFuture<T> whenComplete(
        BiConsumer<? super T, ? super Throwable> action);
```

Đây là **mô tả API**, không phải chương trình Java độc lập; chữ ký thực tế dùng các kiểu trong `java.util.function`.

- `exceptionally` nhận một `Function` từ lỗi sang giá trị `T`. Khi nguồn thành công, callback này không chạy, và giá trị thành công tiếp tục đi qua stage mới.
- `handle` nhận `BiFunction`: với thành công thông thường, `error == null`; với thất bại, `value` thường là `null`. Nó trả một giá trị kiểu `U`, cho phép chuyển `CompletableFuture<Integer>` thành `CompletableFuture<String>` hoặc một kiểu kết quả có nhãn.
- `whenComplete` nhận `BiConsumer`: thực hiện hành động, **không có giá trị trả về** để thay kết quả stage. Dù callback có ghi log hoặc đo metric thành công, lỗi nguồn vẫn phải được xử lý ở ranh giới khác nếu ứng dụng cần phục hồi.

Cần cẩn thận với `null`: một `CompletableFuture<String>` **có thể hoàn tất thành công bằng `null`**. Do đó không dùng `value == null` làm tiêu chuẩn duy nhất để xác định thất bại trong `handle` hoặc `whenComplete`. Hãy dùng `error != null` để phân biệt nhánh lỗi theo hợp đồng callback.

## 4. Ví dụ 1 có thể chạy: cùng nguồn nhưng ba cách xử lý

Tạo tệp **`RecoveryMethodsDemo.java`**:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class RecoveryMethodsDemo {
    static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException
                || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public static void main(String[] args) {
        CompletableFuture<Integer> success =
                CompletableFuture.completedFuture(7);
        CompletableFuture<Integer> failure =
                CompletableFuture.failedFuture(
                        new IllegalArgumentException("bad input"));

        int successExceptionally =
                success.exceptionally(error -> -1).join();
        System.out.println("success.exceptionally=" + successExceptionally);

        int successHandle = success.handle(
                (value, error) -> error == null ? value * 2 : -2).join();
        System.out.println("success.handle=" + successHandle);

        int successObserved = success.whenComplete(
                (value, error) -> {}).join();
        System.out.println("success.whenComplete=" + successObserved);

        CompletableFuture<Integer> recovered = failure.exceptionally(
                error -> {
                    System.out.println("exceptionally.error="
                            + unwrap(error).getClass().getSimpleName());
                    return -1;
                });
        System.out.println("failure.exceptionally=" + recovered.join());

        int failureHandled = failure.handle((value, error) -> {
            System.out.println("handle.hasError=" + (error != null));
            return error == null ? value * 2 : -2;
        }).join();
        System.out.println("failure.handle=" + failureHandled);

        CompletableFuture<Integer> observed = failure.whenComplete(
                (value, error) -> System.out.println(
                        "whenComplete.error="
                                + unwrap(error).getClass().getSimpleName()));

        try {
            observed.join();
        } catch (CompletionException error) {
            System.out.println("failure.whenComplete.root="
                    + unwrap(error).getClass().getSimpleName());
        }

        System.out.println("source.exceptional="
                + failure.isCompletedExceptionally());
        System.out.println("recovered.exceptional="
                + recovered.isCompletedExceptionally());
    }
}
```

Biên dịch/chạy với Java 17:

```bash
javac --release 17 RecoveryMethodsDemo.java
java RecoveryMethodsDemo
```

Kết quả:

```text
success.exceptionally=7
success.handle=14
success.whenComplete=7
exceptionally.error=IllegalArgumentException
failure.exceptionally=-1
handle.hasError=true
failure.handle=-2
whenComplete.error=IllegalArgumentException
failure.whenComplete.root=IllegalArgumentException
source.exceptional=true
recovered.exceptional=false
```

**Giải thích từng nhánh:**

1. `success` đã hoàn tất bình thường bằng `7`. Callback `exceptionally` **không chạy**, nên giá trị vẫn là `7`.
2. `handle` chạy trong trường hợp thành công, nhân `7` thành `14` và tạo kết quả mới.
3. `whenComplete` ở nhánh thành công không thay `7` khi action không ném.
4. `failure` được hoàn tất bất thường bằng `IllegalArgumentException`. `exceptionally` nhận lỗi, ghi loại lỗi, rồi trả `-1`; stage `recovered` hoàn tất **bình thường** bằng `-1`.
5. `handle` cũng thấy lỗi và trả `-2`; đây là **một stage phục hồi khác**, không can thiệp vào stage `recovered`.
6. `whenComplete` quan sát lỗi và in loại lỗi nhưng không tạo fallback. Khi `observed.join()` được gọi, stage vẫn thất bại và nguyên nhân có ý nghĩa là `IllegalArgumentException`.
7. Nguồn `failure.isCompletedExceptionally()` tiếp tục là `true`, trong khi nhánh `recovered.isCompletedExceptionally()` là `false`. Hồi phục ở stage phụ thuộc **không xóa lịch sử thất bại** của stage nguồn.

Ví dụ sử dụng các future đã hoàn tất sẵn để thứ tự stdout dễ kiểm chứng; không nên dựa vào thứ tự callback khi gắn chúng lên những công việc thực sự chạy đồng thời mà không có cơ chế phối hợp.

## 5. `exceptionally`: phục hồi riêng đường lỗi

Một ứng dụng hiển thị tên người dùng có thể cho phép cache thay thế khi dịch vụ hồ sơ tạm thời không sẵn sàng:

```java
CompletableFuture<String> displayName =
        loadProfileNameAsync().exceptionally(error -> "Tên tạm thời");
```

Đây là **mã minh họa**, yêu cầu hàm `loadProfileNameAsync()` trả `CompletableFuture<String>`. Nếu nguồn thành công, callback không chạy và tên thật được giữ. Nếu nguồn lỗi, callback được gọi và trả fallback `"Tên tạm thời"`.

**Lưu ý:** mẫu trên phục hồi **mọi** lỗi. Trong ứng dụng thật, nên phân loại nếu không muốn `NullPointerException` do bug, lỗi phân quyền hoặc lỗi cấu hình cũng bị biến thành fallback bình thường.

Ví dụ lọc riêng timeout trong một `CompletableFuture<String> source` đã có:

```java
CompletableFuture<String> maybeCached = source.exceptionally(error -> {
    Throwable current = error;
    while (current instanceof CompletionException
            && current.getCause() != null) {
        current = current.getCause();
    }

    if (current instanceof java.util.concurrent.TimeoutException) {
        return "CACHED";
    }
    throw new CompletionException(current);
});
```

Đây là **đoạn minh họa trong một chương trình có `source` và import thích hợp**. Nếu lỗi thuộc timeout, stage mới nhận fallback. Nếu không, callback ném lại một `CompletionException` để tiếp tục báo thất bại. Việc bọc lại sẽ tạo đường đi lỗi bổ sung; một triển khai production có thể chọn cách chuyển tiếp giữ cause phù hợp với hợp đồng logging, không nhất thiết thêm wrapper cho mọi lỗi.

### Khi `exceptionally` không phù hợp

- Nếu cần biến cả giá trị thành công lẫn lỗi thành một kiểu kết quả khác, dùng `handle`.
- Nếu chỉ muốn ghi log mà **không** biến lỗi thành thành công, dùng `whenComplete`.
- Nếu fallback không thực sự hợp lệ với thao tác (ví dụ “thanh toán thành công”), không nên phục hồi bằng một giá trị giả.
- Nếu yêu cầu người gọi nhận biết có fallback, trả một kiểu kết quả mang thông tin như `Result<T>` hoặc một record, thay vì âm thầm đổi lỗi thành một giá trị không phân biệt được.

`exceptionally` không **thực hiện lại** thao tác đã thất bại. Nó là phép **chuyển kết quả lỗi sang một giá trị**. Nếu muốn thử lại bất đồng bộ, cần một cơ chế thực thi lại có giới hạn, hoặc API kết hợp stage thích hợp (`exceptionallyCompose` trên các JDK hỗ trợ), cùng chính sách retry an toàn.

## 6. `handle`: hợp nhất thành công/thất bại và chuyển kiểu

`handle` phù hợp khi tầng gọi không muốn nhận một ngoại lệ unchecked mà muốn một kết quả **có cấu trúc**, phân biệt thành công với thất bại:

```java
record Outcome<T>(T value, String errorCode) {
    boolean succeeded() {
        return errorCode == null;
    }
}

CompletableFuture<Outcome<Integer>> outcome =
        source.handle((value, error) -> {
            if (error != null) {
                return new Outcome<Integer>(null, "SERVICE_UNAVAILABLE");
            }
            return new Outcome<Integer>(value, null);
        });
```

Đây là **đoạn mã minh họa**, cần đặt record ở vị trí khai báo kiểu Java hợp lệ và có `CompletableFuture<Integer> source`. `Outcome` là một kiểu kết quả ví dụ; khi dùng trong API thật, nên tránh cho phép trạng thái mâu thuẫn (vừa không có value vừa không có error, hoặc cả hai có giá trị) và có thể dùng sealed types nếu cần hợp đồng chặt hơn.

Điểm nổi bật là `handle` trả `U`: stage nguồn kiểu `Integer`, stage mới kiểu `Outcome<Integer>`. Nó vẫn chạy nếu stage nguồn thành công, vì vậy phù hợp cho chuẩn hóa kết quả thành một cấu trúc chung.

### Không vô tình che cả bug

Nếu callback chỉ viết:

```java
source.handle((value, error) -> error == null ? value : 0);
```

thì mọi lỗi đều chuyển thành giá trị `0`, kể cả lỗi logic nội bộ. Chỉ dùng khi `0` là giá trị dự phòng được hợp đồng cho phép và không thể bị hiểu nhầm là kết quả thật. Với lỗi không phục hồi được, `handle` có thể ném một ngoại lệ phù hợp để stage mới tiếp tục hoàn tất lỗi.

### `null` không nhất thiết có nghĩa là thất bại

Nếu nguồn là `CompletableFuture.completedFuture(null)`, một `handle((value, error) -> ...)` có thể nhận cả `value == null` **và** `error == null`. Đó vẫn là hoàn tất thành công. Không xử lý bằng `if (value == null) { fallback; }` nếu giá trị `null` được hợp đồng cho phép.

## 7. `whenComplete`: quan sát mà không đổi kết quả

Mẫu phổ biến để ghi log ở ranh giới:

```java
CompletableFuture<Order> observed =
        source.whenComplete((order, error) -> {
            if (error != null) {
                logger.error("Order computation failed", error);
            }
        });
```

Đây là **đoạn mã minh họa** cần kiểu `Order`, `source` có kiểu `CompletableFuture<Order>` và `logger` (chẳng hạn SLF4J). Chuyển đối tượng `Throwable` cho logger giúp formatter có thể ghi stack/cause, thay vì chỉ log `error.getMessage()`.

Khi nguồn thành công và action không ném lỗi, `observed` nhận cùng giá trị. Khi nguồn thất bại và action không ném lỗi, `observed` vẫn hoàn tất thất bại. **Nhìn thấy ngoại lệ trong callback không tự nghĩa là nó đã được tiêu thụ.**

Một cách dùng khác là đo metric:

```java
CompletableFuture<String> monitored =
        source.whenComplete((value, error) -> {
            if (error == null) {
                metrics.recordSuccess();
            } else {
                metrics.recordFailure();
            }
        });
```

Đây cũng là đoạn minh họa cần `source` và `metrics`. Nếu thao tác metric tự ném ngoại lệ khi nguồn thành công, stage mới có thể chuyển từ thành công sang thất bại. Do đó callback quan sát nên **nhẹ, đáng tin cậy và không làm thay đổi nghiệp vụ**.

### `whenComplete` không phải `finally` tuyệt đối

Nó giống một hook quan sát cuối stage, nhưng không phải bản sao toàn bộ ngữ nghĩa `finally` của khối lệnh đồng bộ:

- Nó chạy khi stage nguồn **hoàn tất**; nếu stage không bao giờ hoàn tất, callback cũng có thể không chạy.
- Nó không tự đóng tài nguyên thuộc supplier đang chạy; `CompletableFuture` đã hoàn tất do timeout/hủy không đảm bảo công việc vật lý đã dừng.
- Nó không thay thế `try-with-resources` ở nơi tài nguyên được mở.
- Callback có thể chạy trên những thread khác nhau tùy cách hoàn tất và biến thể `Async`; không dùng nó để giả định một thread-local context đã được giữ nguyên.

Dùng `whenComplete` để quan sát và phát metric/log ở ranh giới thích hợp, không làm nơi thực hiện cleanup không an toàn đối với resource của worker.

## 8. Callback tự ném lỗi: hành vi khác nhau như thế nào?

Các callback có thể tự thất bại do lỗi lập trình hoặc do thao tác phụ bên trong. Đừng coi `exceptionally`, `handle`, `whenComplete` là “hàng rào không thể lỗi”.

- Với `exceptionally`: nếu recovery function ném lỗi, stage mới hoàn tất thất bại. Lỗi hồi phục không tự biến thành fallback.
- Với `handle`: nếu function ném lỗi ở nhánh thành công hoặc thất bại, stage mới hoàn tất thất bại với lỗi function.
- Với `whenComplete`: nếu nguồn **thành công** và action ném lỗi, stage mới thất bại do action. Nếu nguồn **đã thất bại** và action cũng ném lỗi, lỗi nguồn được ưu tiên theo hợp đồng; không giả định có một danh sách suppressed tự động cho lỗi action.

Để kiểm chứng, tạo tệp **`CallbackFailureDemo.java`**:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class CallbackFailureDemo {
    static String rootType(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName();
    }

    public static void main(String[] args) {
        CompletableFuture<Integer> good =
                CompletableFuture.completedFuture(5);
        CompletableFuture<Integer> bad =
                CompletableFuture.failedFuture(
                        new IllegalArgumentException("origin failed"));

        try {
            good.whenComplete((value, error) -> {
                throw new IllegalStateException("observer failed");
            }).join();
        } catch (CompletionException error) {
            System.out.println("observerOnSuccess=" + rootType(error));
        }

        try {
            bad.whenComplete((value, error) -> {
                throw new IllegalStateException("observer failed");
            }).join();
        } catch (CompletionException error) {
            System.out.println("observerOnFailure=" + rootType(error));
        }

        try {
            bad.exceptionally(error -> {
                throw new IllegalStateException("recovery failed");
            }).join();
        } catch (CompletionException error) {
            System.out.println("failedRecovery=" + rootType(error));
        }

        try {
            good.handle((value, error) -> {
                throw new IllegalStateException("handler failed");
            }).join();
        } catch (CompletionException error) {
            System.out.println("failedHandle=" + rootType(error));
        }

        CompletableFuture<Integer> recovered =
                bad.exceptionally(error -> 100);
        System.out.println("recoveredThenApply="
                + recovered.thenApply(value -> value + 1).join());
        System.out.println("originalStillExceptional="
                + bad.isCompletedExceptionally());
    }
}
```

Chạy:

```bash
javac --release 17 CallbackFailureDemo.java
java CallbackFailureDemo
```

Đầu ra:

```text
observerOnSuccess=IllegalStateException
observerOnFailure=IllegalArgumentException
failedRecovery=IllegalStateException
failedHandle=IllegalStateException
recoveredThenApply=101
originalStillExceptional=true
```

Trong hai dòng đầu, callback `whenComplete` đều ném `IllegalStateException`; nhưng khi nguồn đã lỗi sẵn, stage mới vẫn giữ lỗi `IllegalArgumentException` từ nguồn. Dòng thứ ba và thứ tư cho thấy bản thân callback phục hồi/xử lý có thể là nguồn lỗi mới. Hai dòng cuối nhắc rằng một stage được khôi phục bằng `exceptionally` sẽ cho phép `thenApply` chạy tiếp, còn **stage gốc vẫn thất bại**.

Đây là ví dụ dùng future đã hoàn tất để minh họa ngữ nghĩa và đầu ra ổn định; trong pipeline bất đồng bộ thật, thứ tự callback không được suy ra chỉ từ thứ tự gắn chúng.

## 9. Khác biệt quan trọng giữa phục hồi và chỉ quan sát

Giả sử `CompletableFuture<Integer> failed` đã hoàn tất lỗi.

```java
CompletableFuture<Integer> withFallback =
        failed.exceptionally(error -> 0);

CompletableFuture<Integer> handled =
        failed.handle((value, error) -> error == null ? value : 0);

CompletableFuture<Integer> observed =
        failed.whenComplete((value, error) -> logError(error));
```

Đây là **đoạn minh họa** cần `failed` và `logError(Throwable)` được khai báo. Nếu callback đều chạy bình thường, hai stage đầu **phục hồi bằng `0`**, còn stage `observed` **tiếp tục thất bại**. Không được viết code phía sau giả định `observed.join()` sẽ trả `0`.

Một sơ đồ phụ thuộc:

```text
                       failed (exceptional)
                        /       |         \
                       /        |          \
             exceptionally    handle     whenComplete
                   |             |             |
               result = 0    result = 0   vẫn exceptional
                   |             |             |
                thenApply     thenApply      join() báo lỗi
                  chạy          chạy
```

Cần hiểu “phục hồi” là **stage mới hoàn tất bình thường** khi callback trả giá trị. Nó không sửa dữ liệu đã thay đổi trước lúc lỗi xảy ra, không rollback DB, không dừng một tác vụ I/O đang chạy và không làm stage nguồn thành công.

Nếu người gọi cần biết rằng kết quả là fallback, hãy đưa metadata vào kiểu kết quả, ví dụ `Outcome<T>` có trạng thái `REAL`/`FALLBACK`, thay vì chỉ trả một số có thể trùng giá trị thành công thực tế.

## 10. Thứ tự ghép stage làm thay đổi chính sách lỗi

Ba phương thức là thành phần của pipeline. **Đặt chúng trước hoặc sau một stage tính toán** có thể tạo kết quả khác.

### Phục hồi trước rồi mới biến đổi

```java
CompletableFuture<Integer> result =
        failed.exceptionally(error -> 10)
              .thenApply(value -> value * 2);
```

Đây là đoạn minh họa với `CompletableFuture<Integer> failed`. Nếu `failed` lỗi, callback trả `10` và `thenApply` nhận giá trị `10`, tạo ra `20`. Bước tính tiếp theo **không biết** `10` là fallback nếu kiểu dữ liệu không mang cờ phân biệt.

### Biến đổi trước rồi phục hồi

```java
CompletableFuture<Integer> result =
        source.thenApply(value -> transform(value))
              .exceptionally(error -> 10);
```

Đoạn này yêu cầu `source` và `transform(...)`. `exceptionally` đặt sau `thenApply` có thể phục hồi cả lỗi từ **nguồn** lẫn lỗi do `transform` ném. Nếu ứng dụng chỉ muốn fallback cho lỗi đọc nguồn mà **không** che lỗi lập trình trong bước chuyển đổi, cần điều chỉnh vị trí hoặc phân loại lỗi.

### Quan sát sau khi phục hồi

```java
CompletableFuture<Integer> observed =
        failed.exceptionally(error -> 10)
              .whenComplete((value, error) -> {
                  System.out.println("error=" + error);
              });
```

Đây là đoạn minh họa. `whenComplete` nhìn thấy kết quả của **stage ngay trước nó**; nếu `exceptionally` đã phục hồi thì `error == null`. Muốn log nguyên nhân gốc, có thể quan sát **trước khi phục hồi**:

```java
CompletableFuture<Integer> observedAndRecovered =
        failed.whenComplete((value, error) -> {
                  if (error != null) {
                      report(error);
                  }
              })
              .exceptionally(error -> 10);
```

Đoạn này cần hàm `report(Throwable)` của ứng dụng. `whenComplete` giữ lỗi, vì thế `exceptionally` phía sau vẫn nhìn được đường lỗi để xử lý. Nếu `report` ném lỗi, phải hiểu quy tắc lỗi của `whenComplete` và thiết kế logger/metric đáng tin cậy.

## 11. `CompletionException`, `ExecutionException` và cause gốc

Khi một stage thất bại, loại `Throwable` được callback nhìn thấy có thể là ngoại lệ gốc hoặc một lớp bọc kỹ thuật do pipeline tạo ra. Không phụ thuộc cứng vào việc `error.getClass()` luôn là lỗi nghiệp vụ gốc.

- `join()` có thể ném `CompletionException` (unchecked) với `getCause()` trỏ đến lỗi gốc.
- `get()` có thể ném `ExecutionException` (checked) với cause của thất bại.
- `whenComplete`, `handle`, `exceptionally` nhận đối tượng `Throwable` đại diện cho trạng thái lỗi của **stage đầu vào**. Tùy đường đi, một trong các lớp bọc có thể xuất hiện.

Ví dụ gỡ các wrapper kỹ thuật quen thuộc:

```java
Throwable meaningful(Throwable error) {
    Throwable current = error;
    while ((current instanceof java.util.concurrent.CompletionException
            || current instanceof java.util.concurrent.ExecutionException)
            && current.getCause() != null) {
        current = current.getCause();
    }
    return current;
}
```

Đây là **phương thức minh họa trong một lớp Java**, không phải chương trình độc lập. Nó bỏ qua hai loại lớp bọc cụ thể để lấy ngoại lệ có ý nghĩa gần nhất, **không** tự động bỏ mọi wrapper nghiệp vụ. Nếu dùng dữ liệu lỗi từ nguồn không kiểm soát được, tiện ích tổng quát còn cần cân nhắc chuỗi cause bất thường và giới hạn độ sâu để tránh vòng lặp ngoài ý muốn.

**Ghi log cả `Throwable`**, không chỉ `getMessage()`, nếu cần stack trace, cause và suppressed exceptions. Chỉ lấy exception sâu nhất rồi bỏ qua ngữ cảnh tầng cao có thể khiến khó tìm nơi chuyển đổi lỗi.

## 12. Hủy và timeout: không tự biến lỗi thành thành công

Một future bị hủy hoặc bị timeout cũng có thể dẫn đến callback xử lý lỗi:

- `CompletableFuture.cancel(...)` có thể hoàn tất future bằng trạng thái hủy. Khi xử lý lỗi ở stage phụ thuộc, callback có thể nhận `CancellationException` hoặc một lớp bọc của nó tùy đường đi.
- `orTimeout(...)` hoàn tất future đang chờ **bất thường** với `TimeoutException` nếu deadline đến trước.
- `completeOnTimeout(fallback, ...)` hoàn tất future đang chờ **bình thường** với fallback nếu deadline đến trước; khi đó `exceptionally` phía sau sẽ không được gọi vì stage đã thành công.

Các phương thức xử lý lỗi không tự **dừng công việc thực tế**. Nếu một supplier đang ghi file hoặc gọi dịch vụ, `exceptionally(error -> defaultValue)` không hủy I/O, không bù trừ tác dụng phụ và không bảo đảm supplier đã kết thúc. Không dùng “recovered future” như chứng cứ giao dịch gốc đã hoàn tất thành công.

Chỉ trả fallback khi an toàn: timeout của một widget hiển thị có thể được phép dùng dữ liệu cache, còn timeout của giao dịch thanh toán cần trạng thái rõ ràng và có thể phải đối soát.

## 13. Synchronous và `Async` variants: callback chạy ở đâu?

Ba API có các biến thể bất đồng bộ hữu ích trong nhiều phiên bản JDK, gồm `handleAsync`, `whenCompleteAsync` và `exceptionallyAsync` trên Java 17. Với phiên bản không có hậu tố `Async`, các dependent actions **có thể chạy bởi luồng hoàn tất stage hoặc một luồng khác đang thực hiện thao tác hoàn tất**, không có bảo đảm chúng luôn chạy trên cùng worker đã chạy supplier.

Ví dụ minh họa:

```java
CompletableFuture<String> result =
        source.handleAsync((value, error) -> {
            return error == null ? value : "fallback";
        }, applicationExecutor);
```

Đoạn mã yêu cầu `source` có kiểu `CompletableFuture<String>` và `applicationExecutor` là một `Executor`. Việc cung cấp executor rõ ràng giúp chọn nơi chạy callback; vẫn phải xử lý trường hợp executor từ chối lên lịch hoặc ứng dụng shutdown.

Không thực hiện I/O chặn lâu hoặc xử lý CPU nặng trong `whenComplete` đồng bộ chỉ vì nó trông giống “callback logging”. Một callback chậm có thể làm luồng hoàn tất bị chiếm giữ; nếu dùng logging bất đồng bộ, xác định rõ tương quan lỗi và yêu cầu không làm mất dữ liệu chẩn đoán.

Dù chạy đồng bộ hay bất đồng bộ, **ngữ nghĩa phục hồi** của ba họ API vẫn theo hợp đồng: `exceptionally` có thể trả fallback khi lỗi, `handle` chuyển cả hai nhánh, `whenComplete` chủ yếu quan sát.

## 14. Test chiến lược phục hồi, không chỉ test callback đã chạy

Với hệ thống thật, test tốt phải xác nhận cả **stage nguồn** và **stage nhận kết quả**, đồng thời gây lỗi ở nhiều vị trí:

| Kịch bản | `exceptionally` | `handle` | `whenComplete` |
| --- | --- | --- | --- |
| Nguồn thành công `7` | Không chạy recovery; nhận `7` | Callback chạy, có thể nhận `14` | Callback chạy, vẫn nhận `7` |
| Nguồn lỗi | Callback chạy; có thể trả fallback | Callback chạy; có thể trả fallback/`Outcome` | Quan sát lỗi; future sau thường vẫn lỗi |
| Callback tự ném khi nguồn thành công | Không chạy callback | Stage mới lỗi | Stage mới lỗi |
| Callback tự ném khi nguồn đã lỗi | Stage mới lỗi bởi callback | Stage mới lỗi bởi callback | Lỗi nguồn được ưu tiên |
| Nguồn thành công bằng `null` | Không recovery | `error == null`, `value == null` vẫn là thành công | Tương tự, chỉ quan sát |
| Có fallback rồi `thenApply` | `thenApply` chạy với fallback | `thenApply` chạy nếu handle thành công | `thenApply` chỉ chạy khi stage được quan sát vẫn thành công |
| Có lỗi timeout/hủy | Cần phân loại trước khi fallback | Cần phân loại trước khi fallback | Quan sát và tiếp tục truyền lỗi nếu không ném |

Test không nên chỉ assert `isDone()` vì future lỗi/hủy cũng là done. Hãy kiểm tra `join()`, `isCompletedExceptionally()`, loại cause, giá trị fallback, tác dụng phụ callback và điều kiện phục hồi. Nếu một lỗi bị che thành thành công, test cần bắt được.

### Ví dụ JUnit 5 cho nguồn thất bại

Đây là **đoạn kiểm thử minh họa**, yêu cầu dependency JUnit Jupiter, import `@Test` và `Assertions.*`:

```java
@Test
void exceptionallyRecoversButSourceStaysFailed() {
    CompletableFuture<Integer> source =
            CompletableFuture.failedFuture(
                    new IllegalArgumentException("bad input"));

    CompletableFuture<Integer> recovered =
            source.exceptionally(error -> -1);

    assertEquals(-1, recovered.join());
    assertFalse(recovered.isCompletedExceptionally());
    assertTrue(source.isCompletedExceptionally());
}

@Test
void whenCompleteDoesNotConsumeFailure() {
    CompletableFuture<Integer> source =
            CompletableFuture.failedFuture(
                    new IllegalArgumentException("bad input"));

    CompletableFuture<Integer> observed =
            source.whenComplete((value, error) -> {});

    CompletionException exception =
            assertThrows(CompletionException.class, observed::join);
    assertInstanceOf(IllegalArgumentException.class,
            exception.getCause());
}
```

Đoạn test cần import các lớp concurrency; nó không chạy độc lập chỉ với JDK. Test đầu tiên kiểm tra phục hồi đúng nhưng không làm nguồn thành công. Test thứ hai kiểm tra `whenComplete` **không tự tiêu thụ** lỗi.

### Test callback ném lỗi

Nên có các ca tương ứng với `CallbackFailureDemo`:

- `whenComplete` ném trong khi nguồn thành công → stage quan sát mới thất bại.
- `whenComplete` ném trong khi nguồn đã thất bại → lỗi nguồn vẫn là lỗi được stage sau báo ra.
- `exceptionally` ném trong recovery → stage mới vẫn lỗi.
- `handle` ném khi xử lý → stage mới vẫn lỗi.
- Gắn `exceptionally` sau `thenApply` → cần kiểm tra liệu nó có vô tình che lỗi chuyển đổi không.

Khi callback chứa logic điều chỉnh trạng thái, còn phải assert DB/side effect/fallback hợp lệ, không chỉ loại `Throwable`.

## 15. Lỗi thường gặp và cách tránh

| Hiểu nhầm | Hậu quả | Cách khắc phục |
| --- | --- | --- |
| “`whenComplete` đã log là lỗi đã được xử lý.” | Stage sau vẫn thất bại, `join()` vẫn ném | Dùng `exceptionally`/`handle` nếu thực sự cần phục hồi |
| “`exceptionally` luôn chạy sau mọi stage.” | Callback không chạy ở nhánh thành công | Gắn logic chung vào `handle` hoặc `whenComplete` |
| “`handle` chỉ nhận giá trị thành công.” | Không xử lý đúng `error` | Luôn kiểm tra `error != null`; value có thể `null` |
| “`value == null` nghĩa là lỗi.” | Nhầm nguồn thành công bằng `null` | Dùng trường `error` để phân biệt |
| “`whenComplete` không bao giờ thay trạng thái.” | Callback tự ném có thể biến thành công thành lỗi | Đảm bảo callback quan sát đáng tin cậy |
| “Callback action lỗi sẽ luôn thay lỗi nguồn.” | Hiểu sai ưu tiên của `whenComplete` | Nếu nguồn đã lỗi, lỗi nguồn được ưu tiên |
| “Phục hồi bằng fallback làm future gốc hết lỗi.” | Giám sát sai trạng thái | Phân biệt source stage và dependent stage |
| “Dùng `exceptionally(e -> 0)` là an toàn cho mọi lỗi.” | Che bug, lỗi phân quyền hoặc dữ liệu sai | Phân loại lỗi, chỉ fallback khi hợp đồng cho phép |
| “`whenComplete` là nơi có thể đóng mọi resource.” | Có thể đóng resource khi supplier vẫn hoạt động | Đặt cleanup trong phạm vi sở hữu tài nguyên |
| “`isDone()` nghĩa là thao tác nghiệp vụ thành công.” | Ghi nhận thành công giả | Kiểm tra trạng thái hoàn tất và hậu điều kiện |
| “Các callback luôn chạy theo thứ tự gắn.” | Race hoặc kết quả log khác kỳ vọng | Dùng quan hệ stage và đồng bộ khi cần |
| “`join()` trả lỗi gốc trực tiếp trong mọi trường hợp.” | Bắt sai exception và bỏ cause | Xét `CompletionException`, `CancellationException`, cause |
| “`get()` và `join()` giống hệt nhau.” | Bỏ sót checked exceptions ở `get()` | Phân biệt `ExecutionException`, `InterruptedException` |
| “Một `handle` trả `null` là chắc chắn lỗi.” | Nhầm giá trị null hợp lệ | Có thể hoàn tất bình thường bằng null |
| “Callback bị lỗi thì có thể bỏ qua.” | Stage mới thất bại không được quan sát | Gắn kết quả vào pipeline và có ranh giới xử lý |

## 16. Quy trình chọn API theo mục tiêu

1. **Chỉ muốn ghi log, đo metric hoặc quan sát một stage?** Chọn `whenComplete`. Đừng giả định lỗi được phục hồi.
2. **Chỉ muốn thay lỗi đã được phân loại bằng một giá trị dự phòng cùng kiểu?** Chọn `exceptionally`. Kiểm tra fallback có hợp lệ và đủ metadata không.
3. **Muốn xử lý cả hai nhánh thành công/lỗi thành kiểu kết quả mới?** Chọn `handle`. Nó hữu ích để tạo `Outcome<T>`, một mã trạng thái hoặc bản ghi kết quả chung.
4. **Muốn retry bằng một tác vụ bất đồng bộ khác?** Cần một luồng retry có giới hạn và API kết hợp stage; không coi `exceptionally` trả một `CompletableFuture` là tương đương flat-map trong mọi trường hợp.
5. **Muốn giữ lỗi để tầng khác quyết định?** Đừng ép fallback. Dùng `whenComplete` quan sát (nếu cần) rồi để exception lan truyền.
6. **Muốn chắc chắn công việc thực tế dừng?** Thiết kế cancellation/deadline ở executor/I/O; ba callback này không cung cấp bảo đảm dừng vật lý.

Đặt callback **ở đúng ranh giới**: logging trước phục hồi nếu cần thấy lỗi gốc; fallback sau bước được phép thất bại; tránh `handle` bao trùm nhiều giai đoạn khi một bug của giai đoạn cuối không nên bị che.

## 17. Bài tập thực hành

**Bài 1 — kiểm chứng fallback.** Tạo `CompletableFuture.failedFuture(new IllegalArgumentException("x"))`, gắn `exceptionally(error -> -1)`. So sánh `isCompletedExceptionally()` của nguồn và stage mới. **Kỳ vọng:** nguồn `true`, stage mới `false`, `join()` của stage mới trả `-1`.

**Bài 2 — thành công vẫn qua `handle`.** Tạo future hoàn tất với `5`, gắn `handle((value, error) -> error == null ? "OK:" + value : "FAIL")`. **Kỳ vọng:** stage mới có kiểu `CompletableFuture<String>`, `join()` trả `"OK:5"`.

**Bài 3 — quan sát không phục hồi.** Tạo future thất bại, gắn `whenComplete` chỉ ghi log. Gọi `join()` trên stage trả về. **Kỳ vọng:** vẫn nhận lỗi (thường dưới `CompletionException`).

**Bài 4 — callback tự lỗi.** Dựa trên `CallbackFailureDemo`, thay callback `whenComplete` bằng action ném `IllegalStateException` ở cả future thành công và thất bại. **Kỳ vọng:** nếu nguồn thành công, stage mới báo `IllegalStateException`; nếu nguồn đã lỗi, lỗi nguồn được ưu tiên.

**Bài 5 — giá trị thành công `null`.** Tạo `CompletableFuture.completedFuture(null)` rồi gắn `handle((value, error) -> error == null ? "SUCCESS" : "FAIL")`. **Kỳ vọng:** `"SUCCESS"` dù `value == null`.

**Bài 6 — thứ tự stage.** Một `thenApply` cố tình ném `ArithmeticException`. Gắn `exceptionally` trước hoặc sau `thenApply` rồi so sánh kết quả. **Kỳ vọng:** fallback đặt trước không nhất thiết phục hồi lỗi phát sinh ở `thenApply`; fallback đặt sau có thể nhìn thấy và phục hồi lỗi đó.

**Bài 7 — tránh che bug.** Trong `exceptionally`, chỉ phục hồi `TimeoutException`; với `NullPointerException` thì tiếp tục ném lỗi. **Kỳ vọng:** lỗi timeout nhận fallback, bug nội bộ vẫn thất bại.

**Bài 8 — outcome có metadata.** Dùng `handle` để trả một record chứa thành công thật hay thất bại cùng mã lỗi. **Kỳ vọng:** caller không phải đoán việc fallback từ một giá trị `0` hoặc chuỗi rỗng.

## 18. Tài liệu tham khảo

- [Java SE 17 — `CompletableFuture`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): `exceptionally`, `handle`, `whenComplete`, các biến thể `Async` và trạng thái hoàn tất.
- [Java SE 17 — `CompletionStage`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletionStage.html): hợp đồng kết hợp stage, truyền exception và ưu tiên lỗi của callback.
- [Java SE 17 — `CompletionException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletionException.html): lớp bọc lỗi khi quan sát bất đồng bộ.
- [Java SE 17 — `ExecutionException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutionException.html): lỗi checked nhận từ `Future.get()`.
- [Java SE 17 — `TimeoutException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/TimeoutException.html): phân loại thời hạn chờ và phục hồi theo timeout.

**Ghi nhớ:** `exceptionally` là **phục hồi riêng đường lỗi**; `handle` là **biến đổi cả thành công lẫn thất bại**; `whenComplete` là **quan sát mà bình thường không thay kết quả**. Việc callback trả fallback làm stage mới thành công, **không làm stage gốc hết lỗi**, không xóa tác dụng phụ và không bảo đảm công việc nền đã dừng. Viết test cho nhánh thành công, nhánh lỗi, callback tự ném và thứ tự ghép stage trước khi đưa chính sách phục hồi vào production.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-practice-010">Hoàn thiện ví dụ tích hợp với kiểm thử các tình huống thất bại</a>

<details>
<summary>Xem chi tiết</summary>

# Hoàn thiện ví dụ tích hợp với kiểm thử các tình huống thất bại — Java Core Exception

## 1. Bài thực hành tổng hợp: từ ngoại lệ đến hợp đồng nghiệp vụ

Một chương trình Java có thể biên dịch và chạy đúng ở đường thành công nhưng vẫn sai nghiêm trọng khi dependency lỗi, tài nguyên đóng thất bại, tác vụ bị hủy hoặc dữ liệu chỉ được ghi một phần. Vì vậy, khi hoàn thiện ví dụ tích hợp, không nên dừng ở việc bắt được một `Exception`. Cần kiểm tra cả **loại lỗi, nguyên nhân, ranh giới tài nguyên, tác dụng phụ và trạng thái mà caller thực sự nhận được**.

Bài này xây dựng một workflow tạo đơn hàng tối giản bằng Java 17+:

1. Nhận yêu cầu gồm mã hàng và số lượng.
2. Kiểm tra đầu vào **trước khi truy cập dependency**.
3. Mở session tới kho dữ liệu qua `AutoCloseable`.
4. Đọc đơn giá; phân biệt không tìm thấy, dữ liệu nguồn sai và sự cố I/O.
5. Tạo receipt có tổng tiền và ghi vào kho.
6. Gửi thông báo sau khi lưu.
7. Đóng session dù thành công hay lỗi.
8. Quan sát kết quả khi tác vụ được chạy qua `ExecutorService`.
9. Kiểm tra cancellation và các exception có thể phát sinh bất đồng bộ.

Các dependency được thay bằng **fake trong bộ nhớ**, không dùng database, mạng hoặc secret thật. Bộ kiểm tra vẫn bao gồm nhiều tình huống không thuận lợi nhằm xác minh **hợp đồng từng ranh giới**. Đây là chương trình có thể tự chạy với `javac`/`java`; sau đó ta đối chiếu với cách tổ chức JUnit 5 trong dự án.

**Kết quả mong muốn:** đường thành công trả đúng receipt; từng loại lỗi có mã khác nhau; nguồn I/O vẫn nằm trong `getCause()`; `try-with-resources` bảo toàn lỗi close; cancellation không bị biến thành thành công; đặc biệt, một thao tác đã lưu trước khi notifier lỗi **không được giả định là chưa lưu**.

## 2. Phạm vi và hợp đồng của ví dụ

Để bài tập có thể lặp lại và không phụ thuộc môi trường ngoài, ta dùng ba nhóm:

| Thành phần | Trách nhiệm | Loại trạng thái/lỗi |
| --- | --- | --- |
| `OrderService` | Validation, phối hợp đọc–tính–lưu–thông báo, dịch lỗi | `DomainFailure` có `Code` |
| `FakeStore` và `Session` | Mở session, đọc đơn giá, lưu receipt, đóng session | `IOException` ở những chế độ giả lập |
| `FakeNotifier` | Gửi thông báo sau khi receipt đã được lưu | `IOException` trong tình huống notify fail |
| `scenario(...)` | Chạy từng ca, assert code, số lần close và trạng thái đã lưu | Ném `AssertionError` nếu sai |
| `asyncFailure()` | Gọi workflow qua `submit()`, xác minh `ExecutionException.getCause()` | Cause là `DomainFailure` |
| `asyncCancellation()` | Chạy một tác vụ chờ có thể ngắt rồi gọi `cancel(true)` | `CancellationException` khi đọc Future |

Yêu cầu có một số **giả định cố ý**:

- `Session.save` đánh dấu `persisted=true` **trước** khi notifier được gọi. Fake không có transaction hoặc rollback.
- Đơn giá thành công là `25`, số lượng demo là `2`, nên tổng đúng là `50`.
- `NOTIFY_FAIL` xảy ra sau khi lưu thành công: đây là **thất bại có side effect**, không phải lỗi đồng nghĩa “chưa thực hiện”.
- `CLOSE_FAIL` xảy ra khi `close()` ném sau khi các thao tác khác đã hoàn tất. Lỗi đóng không tự undo trạng thái `persisted`.
- `READ_AND_CLOSE_FAIL` làm cả thao tác đọc và đóng lỗi, để kiểm tra primary/suppressed exception.
- Mỗi `scenario` tạo một fake mới; test không dùng chung trạng thái đã thay đổi giữa các case.
- Tác vụ hủy trong phần cuối chỉ chờ latch, không ghi dữ liệu nghiệp vụ, để minh họa interrupt mà không gây side effect bất định.

Không được xem fake này là một triển khai kho dữ liệu production. Đặc biệt, một DB thật có transaction, commit/rollback và connection pool; một notifier thật có thể gửi thành công nhưng mất ACK. Các phần tiếp theo nêu rõ những điểm phải kiểm thử thêm khi thay fake bằng dependency thật.

## 3. Sơ đồ đường thành công và đường thất bại

```text
Request(id, quantity)
        |
        v
Validate request
        | invalid ------------------> INVALID_REQUEST (chưa mở session)
        v
Store.open() -> Session
        |
        v
Session.read(id)
        | IOException -------------> DEPENDENCY_UNAVAILABLE
        | null --------------------> NOT_FOUND
        | unitPrice <= 0 ----------> INVALID_DATA
        v
Calculate Receipt(total)
        |
        v
Session.save(receipt)
        | IOException -------------> DEPENDENCY_UNAVAILABLE
        v
persisted = true
        |
        v
Notifier.send(receipt)
        | IOException -------------> NOTIFICATION_FAILED
        |                            persisted vẫn có thể true
        v
Return receipt
        |
        v
Session.close()                 (luôn được thử sau khi mở thành công)
        | IOException -------------> CLOSE_FAILED nếu không có lỗi trước
        | lỗi trước đã tồn tại ----> close error có thể được suppressed
        v
Caller nhận receipt / DomainFailure
```

Chú ý `Session.close()` diễn ra khi rời khối `try-with-resources`, **trước khi method hoàn tất trả về**. Vì thế code có câu `return receipt` trong thân `try` vẫn có thể **không trả receipt cho caller** nếu `close()` ném. Đây là lý do `CLOSE_FAIL` vẫn báo lỗi mặc dù receipt đã được lưu và thông báo đã gửi trong fake.

Tương tự, lỗi khi đọc dữ liệu có thể là primary, còn `close()` tạo lỗi thứ cấp. Nếu chỉ xem message của lỗi đóng mà bỏ qua `getSuppressed()` hoặc chỉ assert một mã lỗi, bài test có thể đánh giá nhầm nguồn sự cố.

## 4. Chọn các loại lỗi và mã lỗi có ý nghĩa

Ví dụ dùng enum:

```java
enum Code {
    INVALID_REQUEST, NOT_FOUND, INVALID_DATA,
    DEPENDENCY_UNAVAILABLE, NOTIFICATION_FAILED, CLOSE_FAILED
}
```

Đây là mảnh của chương trình đầy đủ ở phần sau.

| Mã | Điều kiện | Ý nghĩa |
| --- | --- | --- |
| `INVALID_REQUEST` | Mã hàng trống/null, số lượng không dương | Caller cung cấp input sai; chưa động tới repository |
| `NOT_FOUND` | Repository trả không có sản phẩm | Đã truy vấn được, nhưng không có dữ liệu |
| `INVALID_DATA` | Đơn giá từ nguồn không hợp lệ | Dependency trả được dữ liệu nhưng vi phạm invariant |
| `DEPENDENCY_UNAVAILABLE` | `read` hoặc `save` ném `IOException` | Không hoàn thành thao tác cần thiết với dependency |
| `NOTIFICATION_FAILED` | `send` ném sau khi `save` thành công | Lỗi sau bước lưu; không được tự kết luận chưa lưu |
| `CLOSE_FAILED` | Không có lỗi trước, nhưng `close()` ném `IOException` | Thao tác dọn dẹp/đóng tài nguyên thất bại |

Mã lỗi là hợp đồng giữa các tầng, **không phải** nguyên văn thông điệp từ hệ thống ngoài. `DomainFailure` sẽ giữ một `Code` và `Throwable cause` khi có lỗi I/O. Nhờ vậy, tầng trên có thể phân biệt lỗi input, nghiệp vụ và dependency mà không phải parse chuỗi message tự do.

Ở hệ thống thật, cần quyết định khi nào thông báo cho client là HTTP 400, 404, 409, 422, 503 hoặc trạng thái bất định. Đừng mặc định mọi `IOException` phải trả cùng mã 503: nó có thể đại diện cho nhiều điều kiện tùy contract và thời điểm xảy ra side effect.

## 5. Chương trình Java 17+ hoàn chỉnh

Tạo file **`OrderIntegrationDemo.java`** với nội dung sau. Chỉ dùng Java Standard Library và chạy được bằng JDK 17 trở lên.

```java
import java.io.IOException;
import java.util.concurrent.*;

public class OrderIntegrationDemo {
    enum Mode {
        OK, MISSING, READ_FAIL, BAD_PRICE, SAVE_FAIL,
        NOTIFY_FAIL, CLOSE_FAIL, READ_AND_CLOSE_FAIL
    }
    enum Code {
        INVALID_REQUEST, NOT_FOUND, INVALID_DATA,
        DEPENDENCY_UNAVAILABLE, NOTIFICATION_FAILED, CLOSE_FAILED
    }
    record Request(String id, int quantity) {}
    record Product(int unitPrice) {}
    record Receipt(String id, long total) {}

    static final class DomainFailure extends RuntimeException {
        final Code code;
        DomainFailure(Code code, Throwable cause) {
            super(code.name(), cause);
            this.code = code;
        }
    }

    static final class FakeStore {
        final Mode mode;
        int opens, reads, writes, closes;
        boolean persisted;

        FakeStore(Mode mode) { this.mode = mode; }

        Session open() {
            opens++;
            return new Session();
        }

        final class Session implements AutoCloseable {
            Product read(String id) throws IOException {
                reads++;
                if (mode == Mode.READ_FAIL || mode == Mode.READ_AND_CLOSE_FAIL) {
                    throw new IOException("read failed");
                }
                if (mode == Mode.MISSING) return null;
                return new Product(mode == Mode.BAD_PRICE ? -1 : 25);
            }

            void save(Receipt receipt) throws IOException {
                writes++;
                if (mode == Mode.SAVE_FAIL) {
                    throw new IOException("save failed");
                }
                persisted = true;
            }

            @Override
            public void close() throws IOException {
                closes++;
                if (mode == Mode.CLOSE_FAIL ||
                        mode == Mode.READ_AND_CLOSE_FAIL) {
                    throw new IOException("close failed");
                }
            }
        }
    }

    static final class FakeNotifier {
        final Mode mode;
        int sends;
        FakeNotifier(Mode mode) { this.mode = mode; }

        void send(Receipt receipt) throws IOException {
            sends++;
            if (mode == Mode.NOTIFY_FAIL) {
                throw new IOException("notify failed");
            }
        }
    }

    static final class OrderService {
        Receipt create(Request request, FakeStore store, FakeNotifier notifier) {
            if (request == null || request.id() == null ||
                    request.id().isBlank() || request.quantity() <= 0) {
                throw new DomainFailure(Code.INVALID_REQUEST, null);
            }
            try (FakeStore.Session session = store.open()) {
                Product product;
                try {
                    product = session.read(request.id());
                } catch (IOException ex) {
                    throw new DomainFailure(Code.DEPENDENCY_UNAVAILABLE, ex);
                }
                if (product == null) {
                    throw new DomainFailure(Code.NOT_FOUND, null);
                }
                if (product.unitPrice() <= 0) {
                    throw new DomainFailure(Code.INVALID_DATA, null);
                }
                Receipt receipt = new Receipt(request.id(),
                        (long) product.unitPrice() * request.quantity());
                try {
                    session.save(receipt);
                } catch (IOException ex) {
                    throw new DomainFailure(Code.DEPENDENCY_UNAVAILABLE, ex);
                }
                try {
                    notifier.send(receipt);
                } catch (IOException ex) {
                    throw new DomainFailure(Code.NOTIFICATION_FAILED, ex);
                }
                return receipt;
            } catch (IOException closeFailure) {
                throw new DomainFailure(Code.CLOSE_FAILED, closeFailure);
            }
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void scenario(String name, Mode mode, Request request,
            Code expectedCode, boolean expectedPersisted,
            int expectedSuppressed) {
        FakeStore store = new FakeStore(mode);
        FakeNotifier notifier = new FakeNotifier(mode);
        try {
            Receipt receipt = new OrderService().create(request, store, notifier);
            check(expectedCode == null, name + " unexpectedly succeeded");
            check(receipt.total() == 50, "wrong total");
            check(store.persisted && store.closes == 1 && notifier.sends == 1,
                    "wrong success side effects");
            System.out.println(name + ": SUCCESS total=" + receipt.total());
        } catch (DomainFailure ex) {
            check(ex.code == expectedCode, name + " wrong error code");
            check(store.persisted == expectedPersisted, "wrong persistence");
            check(ex.getSuppressed().length == expectedSuppressed,
                    name + " wrong suppressed count");
            if (ex.code == Code.INVALID_REQUEST) {
                check(store.opens == 0 && store.closes == 0,
                        "validation must happen before dependency");
            } else {
                check(store.opens == 1 && store.closes == 1,
                        "session must close exactly once");
            }
            if (ex.code == Code.DEPENDENCY_UNAVAILABLE ||
                    ex.code == Code.NOTIFICATION_FAILED ||
                    ex.code == Code.CLOSE_FAILED) {
                check(ex.getCause() instanceof IOException,
                        "IO cause not preserved");
            }
            System.out.println(name + ": " + ex.code
                    + " persisted=" + store.persisted
                    + " suppressed=" + ex.getSuppressed().length);
        }
    }

    static void asyncFailure() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = executor.submit(() ->
                    new OrderService().create(new Request("A", 2),
                            new FakeStore(Mode.READ_FAIL),
                            new FakeNotifier(Mode.READ_FAIL)));
            try {
                future.get(5, TimeUnit.SECONDS);
                throw new AssertionError("expected execution failure");
            } catch (ExecutionException ex) {
                check(ex.getCause() instanceof DomainFailure,
                        "wrong async cause");
                DomainFailure failure = (DomainFailure) ex.getCause();
                check(failure.code == Code.DEPENDENCY_UNAVAILABLE,
                        "wrong async code");
                System.out.println("ASYNC: " + failure.code);
            }
        } finally {
            executor.shutdownNow();
            check(executor.awaitTermination(5, TimeUnit.SECONDS),
                    "executor did not stop");
        }
    }

    static void asyncCancellation() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        try {
            Future<?> future = executor.submit(() -> {
                started.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });
            check(started.await(5, TimeUnit.SECONDS), "task did not start");
            check(future.cancel(true), "cancellation was not accepted");
            check(finished.await(5, TimeUnit.SECONDS), "task did not stop");
            try {
                future.get();
                throw new AssertionError("expected cancellation");
            } catch (CancellationException expected) {
                System.out.println("CANCEL: CancellationException");
            }
        } finally {
            executor.shutdownNow();
            check(executor.awaitTermination(5, TimeUnit.SECONDS),
                    "executor did not stop");
        }
    }

    public static void main(String[] args) throws Exception {
        scenario("SUCCESS", Mode.OK, new Request("A", 2), null, true, 0);
        scenario("INVALID", Mode.OK, new Request("", 2),
                Code.INVALID_REQUEST, false, 0);
        scenario("MISSING", Mode.MISSING, new Request("A", 2),
                Code.NOT_FOUND, false, 0);
        scenario("READ_FAIL", Mode.READ_FAIL, new Request("A", 2),
                Code.DEPENDENCY_UNAVAILABLE, false, 0);
        scenario("BAD_PRICE", Mode.BAD_PRICE, new Request("A", 2),
                Code.INVALID_DATA, false, 0);
        scenario("SAVE_FAIL", Mode.SAVE_FAIL, new Request("A", 2),
                Code.DEPENDENCY_UNAVAILABLE, false, 0);
        scenario("NOTIFY_FAIL", Mode.NOTIFY_FAIL, new Request("A", 2),
                Code.NOTIFICATION_FAILED, true, 0);
        scenario("CLOSE_FAIL", Mode.CLOSE_FAIL, new Request("A", 2),
                Code.CLOSE_FAILED, true, 0);
        scenario("READ_CLOSE", Mode.READ_AND_CLOSE_FAIL, new Request("A", 2),
                Code.DEPENDENCY_UNAVAILABLE, false, 1);
        asyncFailure();
        asyncCancellation();
        System.out.println("INTEGRATION_CHECKS: PASS");
    }
}
```

### Biên dịch và chạy

Tại thư mục chứa file:

```bash
javac -encoding UTF-8 OrderIntegrationDemo.java
java OrderIntegrationDemo
```

Chương trình in kết quả dưới đây khi các điều kiện kiểm tra đều đạt. **Standard output đã được kiểm chứng bằng javac/java**:

```text
SUCCESS: SUCCESS total=50
INVALID: INVALID_REQUEST persisted=false suppressed=0
MISSING: NOT_FOUND persisted=false suppressed=0
READ_FAIL: DEPENDENCY_UNAVAILABLE persisted=false suppressed=0
BAD_PRICE: INVALID_DATA persisted=false suppressed=0
SAVE_FAIL: DEPENDENCY_UNAVAILABLE persisted=false suppressed=0
NOTIFY_FAIL: NOTIFICATION_FAILED persisted=true suppressed=0
CLOSE_FAIL: CLOSE_FAILED persisted=true suppressed=0
READ_CLOSE: DEPENDENCY_UNAVAILABLE persisted=false suppressed=1
ASYNC: DEPENDENCY_UNAVAILABLE
CANCEL: CancellationException
INTEGRATION_CHECKS: PASS
```

**Quan trọng:** các output trên đến từ một demo giả lập với trạng thái được kiểm soát; chúng không là lời hứa về thứ tự completion trên mọi executor thực. Demo async chỉ in sau những điều kiện chờ bằng latch/Future; các thao tác thực tế có thể cần timeout khác theo môi trường.

## 6. Vì sao cấu trúc này là một ví dụ tích hợp?

Một unit test đơn lẻ có thể kiểm tra `validate(id)` hoặc một mapper riêng, nhưng bài tập trên kiểm tra **sự tương tác giữa nhiều ranh giới**:

- Validation phải chạy trước mở dependency.
- Đọc dữ liệu có thể trả “không có kết quả” hoặc ném lỗi I/O.
- Dữ liệu đọc thành công vẫn có thể vi phạm invariant.
- Ghi dữ liệu có thể thất bại trước khi đánh dấu persist.
- Gửi thông báo có thể thất bại **sau khi đã lưu**.
- Resource close có thể tự ném exception, kể cả khi thân đã thành công.
- Khi thân và close cùng lỗi, primary và suppressed phải được giữ.
- Khi chạy cùng workflow qua `ExecutorService`, exception xuất hiện tại `Future.get()` ở caller thay vì ném qua stack của thread nộp.
- Cancellation cần tín hiệu và kiểm tra kết thúc worker riêng.

Những khía cạnh này phù hợp với **component/integration-style test sử dụng fake ports**. Tuy nhiên, vì không dùng database thật hay server thật, không được gọi đó là integration test chứng minh toàn bộ môi trường production. Một test tích hợp thực sự với PostgreSQL, broker, HTTP client hoặc transaction manager còn phải kiểm tra semantics của những dependency đó.

Tên “tích hợp” ở đây biểu thị **workflow end-to-end trong phạm vi các thành phần được nối với nhau**, không khẳng định đã test mọi phụ thuộc bên ngoài.

## 7. Kiểm tra đầu vào phải xảy ra trước side effect

Ở đầu `OrderService.create`:

```java
if (request == null || request.id() == null ||
        request.id().isBlank() || request.quantity() <= 0) {
    throw new DomainFailure(Code.INVALID_REQUEST, null);
}
```

Đây là mảnh của chương trình hoàn chỉnh. Điều kiện kiểm tra diễn ra **trước `store.open()`**.

Trong case `INVALID`, bộ kiểm thử xác nhận:

- `DomainFailure.code == INVALID_REQUEST`.
- `store.opens == 0`.
- `store.closes == 0`.
- `store.persisted == false`.
- Không có nỗ lực lưu hay thông báo.

Mục tiêu không chỉ là “có `IllegalArgumentException`”. Nếu một bug khiến code mở session trước khi validation, bài test phải phát hiện hành động không cần thiết đó. Tương tự, khi một hệ thống xử lý thanh toán, validation và authorization cần diễn ra trước side effect theo contract.

Một số validation có thể cần truy vấn DB để kiểm tra sự tồn tại hoặc quan hệ. Trong trường hợp đó không thể khẳng định mọi validation đều “zero I/O”; hãy định nghĩa rõ bước nào được phép đọc, bước nào không được phép ghi, và assert theo hợp đồng.

## 8. Tách “không tìm thấy” khỏi “phụ thuộc không sẵn sàng”

Giả sử repository có hợp đồng:

```java
Product read(String id) throws IOException;
```

Đây là chữ ký của fake ở ví dụ. Nó có hai khả năng không trả một `Product` hợp lệ:

1. **Trả `null`**: truy vấn đã diễn ra nhưng không có product → `NOT_FOUND`.
2. **Ném `IOException`**: không lấy được kết quả do lỗi dependency → `DEPENDENCY_UNAVAILABLE`.

Hai trường hợp có ý nghĩa khác nhau. Nếu mọi `IOException` bị biến thành `null` hoặc `Optional.empty()`, caller có thể trả 404 cho một sản phẩm thực ra có tồn tại nhưng DB đang lỗi. Nếu một mã sản phẩm không tồn tại bị diễn giải là downtime, caller sẽ retry vô ích.

Bài test:

```text
MISSING: NOT_FOUND persisted=false suppressed=0
READ_FAIL: DEPENDENCY_UNAVAILABLE persisted=false suppressed=0
```

cho thấy hai kết quả khác nhau dù đều không có receipt thành công. Khi xây API thật, nên có contract không mơ hồ cho “not found”; có thể dùng `Optional<Product>` thay vì `null`, nhưng vẫn giữ exception cho lỗi I/O.

## 9. Dữ liệu nguồn sai không giống tham số caller sai

Ở case `BAD_PRICE`, `FakeStore.Session.read` trả `Product(-1)`. Đây là **giá trị không hợp lệ từ dependency**, không phải request với quantity sai. `OrderService` phát hiện `unitPrice <= 0` và ném `INVALID_DATA` trước khi save.

Kiểm thử xác nhận:

- Request đầu vào hợp lệ.
- Session đã mở và sẽ được đóng.
- Product đã đọc được, không có lỗi I/O.
- Đơn giá vi phạm invariant nên không được tính lưu.
- `store.persisted == false`.
- Không nên thực hiện notification.

Sự phân biệt này quan trọng khi xử lý dữ liệu từ database, JSON, message queue hay SDK. Nếu một DTO hợp lệ về cú pháp nhưng sai business invariant, không nên ném bừa `NullPointerException` hoặc trả một receipt có tổng âm.

Ví dụ tính tiền bằng `long` trong demo để tránh overflow khi nhân hai `int` dương vào kiểu `int`. Trong ứng dụng thật, cần biểu diễn tiền tệ bằng kiểu và đơn vị phù hợp (chẳng hạn amount nguyên theo đơn vị nhỏ nhất hoặc `BigDecimal` với rounding policy), kiểm tra miền giá trị, currency và overflow. Không nên dùng số thập phân dấu phẩy động một cách tùy tiện cho phép tính tài chính.

## 10. Phân loại lỗi I/O ở đúng adapter/service boundary

Hai vị trí `read` và `save` đều có thể ném `IOException`, nhưng cùng được map thành `DEPENDENCY_UNAVAILABLE` trong hợp đồng demo:

```java
try {
    product = session.read(request.id());
} catch (IOException ex) {
    throw new DomainFailure(Code.DEPENDENCY_UNAVAILABLE, ex);
}
```

và:

```java
try {
    session.save(receipt);
} catch (IOException ex) {
    throw new DomainFailure(Code.DEPENDENCY_UNAVAILABLE, ex);
}
```

Đây là các đoạn được lấy từ chương trình. Vì handler sử dụng `new DomainFailure(code, ex)`, Throwable gốc vẫn truy cập được qua `getCause()`.

Bài test không chỉ assert code mà còn xác nhận `cause instanceof IOException`. Nếu bỏ cause, log vận hành khó biết lỗi I/O xảy ra ở đâu; nếu luôn in nguyên cause vào HTTP response, có nguy cơ lộ thông tin nội bộ. Vì vậy nên **giữ cause cho chẩn đoán nội bộ** nhưng **trả mã lỗi an toàn cho client**.

Một ứng dụng thật có thể có thêm lỗi permission, conflict, unique-constraint violation hay dữ liệu tạm khóa, cần map hẹp hơn thay vì coi tất cả như downtime. Bài tập lựa chọn `IOException` như một lớp lỗi fake để tập trung vào **ràng buộc exception translation và kiểm thử failure path**.

## 11. Lỗi thông báo sau khi đã lưu: ví dụ quan trọng nhất

Trong `OrderService.create`, thứ tự là:

```text
Session.save(receipt)
       ↓
persisted = true
       ↓
Notifier.send(receipt)
       ↓
IOException nếu notifier lỗi
       ↓
DomainFailure(NOTIFICATION_FAILED)
```

Vì notify xảy ra **sau save**, case `NOTIFY_FAIL` có kết quả:

```text
NOTIFY_FAIL: NOTIFICATION_FAILED persisted=true suppressed=0
```

Điều này chứng minh một nguyên tắc quan trọng: **kết quả là exception không có nghĩa chưa có side effect**. Nếu caller thấy `NOTIFICATION_FAILED` rồi retry toàn bộ lệnh tạo đơn mà không kiểm tra, có thể tạo đơn trùng hoặc gửi thông báo trùng.

Trong một hệ thống production, nên thiết kế một trong các chính sách có hợp đồng rõ:

- **Transactional outbox:** lưu dữ liệu nghiệp vụ và bản ghi “cần phát thông báo” trong cùng transaction DB; worker chuyên trách gửi thông báo và retry có kiểm soát.
- **Idempotency key:** mọi yêu cầu tạo đơn có ID ổn định, để retry không làm phát sinh bản ghi mới khi cùng giao dịch đã được xử lý.
- **Trạng thái phân bước:** `PERSISTED_NOTIFICATION_PENDING` hoặc trạng thái tương đương, cho phép theo dõi và đối chiếu.
- **Compensation:** nếu workflow phân tán không thể dùng một transaction, định nghĩa thao tác bù có thể thất bại và cần được theo dõi.
- **Đối chiếu:** khi notifier trả timeout hoặc mất ACK, không kết luận “chưa gửi”; có thể phải truy vấn/tracking ở hệ thống nhận.

Ví dụ cố tình **không tự rollback** trong fake vì không có transaction. Đó là bài học thiết kế, không phải tính năng thiếu có thể che bằng `catch (Exception)`.

## 12. Lỗi khi close sau thân thành công

`try-with-resources` sẽ gọi `close()` khi rời khối, bao gồm sau câu `return receipt` ở thân try. Với `Mode.CLOSE_FAIL`, các bước đọc, lưu và thông báo có thể đã hoàn tất; sau đó `close()` ném `IOException` và được handler ngoài cùng dịch thành `DomainFailure(CLOSE_FAILED, cause)`.

Đầu ra:

```text
CLOSE_FAIL: CLOSE_FAILED persisted=true suppressed=0
```

Vì vậy một exception khi close **không chứng minh thao tác lưu đã thất bại**. Trong hệ thống thật, một `close()` có thể flush, commit, trả connection về pool hoặc giải phóng file handle tùy contract. Tình trạng sau lỗi close phải được xác minh theo dependency cụ thể, không suy ra từ exception type đơn thuần.

Hợp đồng của fake ở đây: `save` đặt `persisted=true` ngay lập tức, `close` không hoàn tác điều đó. Bài test sử dụng giả định này một cách tường minh.

Đừng bắt `IOException` từ close rồi âm thầm bỏ qua nếu lỗi đóng có thể dẫn đến mất dữ liệu, file chưa flush hoặc tài nguyên rò rỉ. Tùy yêu cầu, có thể cần báo trạng thái **chưa chắc chắn** thay vì retry ngay.

## 13. Lỗi chính và suppressed exception khi read + close cùng thất bại

Case `READ_AND_CLOSE_FAIL` tạo hai exception:

1. `session.read` ném `IOException("read failed")`.
2. Service dịch thành `DomainFailure(DEPENDENCY_UNAVAILABLE, cause)`.
3. Khi rời `try-with-resources`, `session.close()` ném `IOException("close failed")`.
4. Java giữ `DomainFailure` làm **exception chính**, rồi gắn lỗi close vào mảng suppressed.

Kết quả:

```text
READ_CLOSE: DEPENDENCY_UNAVAILABLE persisted=false suppressed=1
```

Cần phân biệt:

- `getCause()` trả nguyên nhân của DomainFailure, ở đây là lỗi I/O khi đọc.
- `getSuppressed()` chứa Throwable từ việc đóng tài nguyên.
- Lỗi close không thay thế lỗi đọc trong case có primary trước đó.
- Không vì chỉ thấy `DEPENDENCY_UNAVAILABLE` mà kết luận cleanup đã hoàn toàn thành công.

Để test chặt hơn, ngoài số lượng suppressed, có thể kiểm tra:

```java
DomainFailure failure = /* failure from OrderService.create(...) */;
Throwable[] suppressed = failure.getSuppressed();
if (suppressed.length != 1 ||
        !(suppressed[0] instanceof IOException) ||
        !"close failed".equals(suppressed[0].getMessage())) {
    throw new AssertionError("Close failure was not retained");
}
```

Đây là **mảnh assert bổ sung**, không phải đoạn độc lập để biên dịch: biến `failure` phải được lấy từ case `READ_AND_CLOSE_FAIL` và `DomainFailure` ở trong class của chương trình. Với unit test thật, dùng JUnit `assertEquals`, `assertInstanceOf` nếu thích hợp.

Nếu có nhiều tài nguyên `AutoCloseable` trong một resource header, Java đóng theo thứ tự ngược thứ tự khởi tạo. Các lỗi close thứ cấp có thể được thêm suppressed theo thứ tự xảy ra; nên test cả event order, không chỉ `length`, khi thứ tự đóng có ý nghĩa.

## 14. Kiểm thử khác nhau giữa lỗi Arrange, Act và Assert

Ngay cả trong một ví dụ tích hợp, test không nên tạo false positive bằng cách kỳ vọng exception trên quá nhiều bước:

```java
assertThrows(DomainFailure.class, () -> {
    FakeStore store = createStoreFromExternalConfig();
    FakeNotifier notifier = setupNotifier();
    new OrderService().create(request, store, notifier);
});
```

Đây là **mảnh JUnit 5 sai về phạm vi** khi các helper setup có thể ném cùng loại DomainFailure. Nếu setup lỗi trước khi gọi `create`, test vẫn có thể xanh mà không kiểm tra SUT.

Mẫu tốt hơn:

```java
FakeStore store = new FakeStore(Mode.READ_FAIL);
FakeNotifier notifier = new FakeNotifier(Mode.READ_FAIL);
OrderService service = new OrderService();
Request request = new Request("A", 2);

DomainFailure failure = assertThrows(
        DomainFailure.class,
        () -> service.create(request, store, notifier));

assertEquals(Code.DEPENDENCY_UNAVAILABLE, failure.code);
assertInstanceOf(IOException.class, failure.getCause());
assertFalse(store.persisted);
assertEquals(1, store.closes);
```

Đây là mảnh **JUnit Jupiter**, cần import assertion và các class ứng dụng, không phải chương trình Java SE độc lập. `Arrange` tạo fake; `Act` là **duy nhất** `service.create` bên trong `assertThrows`; `Assert` kiểm tra code, cause và side effects.

Nếu công cụ tạo fixture ném lỗi, test phải **fail ở Arrange** thay vì được coi là một kết quả exception mong đợi của service. Điều này đặc biệt quan trọng khi tích hợp test database/container phức tạp.

## 15. Vì sao có các bộ đếm opens/reads/writes/closes?

Chỉ assert mã lỗi có thể bỏ qua một bug về resource management. Ví dụ service trả `INVALID_REQUEST` đúng nhưng vẫn mở session hoặc gọi notifier trước validation: response đúng mà side effect đã sai.

Fake ghi các bộ đếm:

```java
int opens, reads, writes, closes;
boolean persisted;
```

Trong `scenario(...)`:

- Với `INVALID_REQUEST`, kỳ vọng `opens == 0` và `closes == 0`.
- Với những case đã mở session, kỳ vọng `opens == 1`, `closes == 1`.
- Với lỗi trước save, kỳ vọng `persisted == false`.
- Với `NOTIFY_FAIL` hoặc `CLOSE_FAIL`, kỳ vọng `persisted == true`.
- Với đường thành công, kỳ vọng tổng tiền đúng và notifier đã được gọi.

Khi chuyển thành test framework, có thể assert thêm `reads`, `writes`, `sends` chính xác để tăng độ bao phủ. Nhưng đừng assert các chi tiết nội bộ không thuộc hợp đồng: ví dụ nếu refactor gộp hai lần đọc thành một query khác mà hành vi công khai không đổi, test quá gắn vào triển khai có thể trở nên giòn.

**Bộ đếm chỉ hợp lý khi chính số lần gọi là yếu tố của hợp đồng**, ví dụ không được gửi thông báo trước khi lưu hoặc không được ghi khi input sai.

## 16. Một ma trận failure scenario cần có

| Tình huống | Error code | Persisted | Close | Điều cần chứng minh |
| --- | --- | --- | --- | --- |
| Đầu vào đúng, dependency hoạt động | Không lỗi | true | 1 | Receipt tổng `50`, notifier được gọi |
| Mã hàng rỗng | `INVALID_REQUEST` | false | 0 | Validation chặn I/O ngay từ đầu |
| Hàng không tồn tại | `NOT_FOUND` | false | 1 | Not found khác unavailable |
| Đọc lỗi `IOException` | `DEPENDENCY_UNAVAILABLE` | false | 1 | Cause I/O được giữ |
| Đơn giá không hợp lệ | `INVALID_DATA` | false | 1 | Không lưu dữ liệu sai |
| Lưu lỗi trước commit | `DEPENDENCY_UNAVAILABLE` | false | 1 | Không nhận thành công giả |
| Notifier lỗi sau save | `NOTIFICATION_FAILED` | true | 1 | Failure sau side effect |
| Close lỗi sau thân thành công | `CLOSE_FAILED` | true | 1 | Return của thân chưa phải return cuối |
| Đọc lỗi + close lỗi | `DEPENDENCY_UNAVAILABLE` | false | 1 | Lỗi close được suppressed |
| Task qua Executor lỗi | Cause `DomainFailure` | tùy case | theo fake | `get()` ném `ExecutionException` |
| Worker đang chờ bị cancel | Future canceled | không áp dụng | pool dừng | `get()` ném `CancellationException` |

Một bộ kiểm thử đầy đủ hơn với dependency thật cần bổ sung: mất ACK sau commit, timeout, retry trùng yêu cầu, transaction rollback thất bại, notifier gửi trùng, connection pool hết kết nối, đóng tài nguyên timeout, cạnh tranh nhiều request và quyền truy cập dữ liệu.

Mỗi case nên có **một mục tiêu rõ**: loại lỗi, tác dụng phụ hay lifecycle. Không kiểm tra “có exception nào đó” mà bỏ qua trạng thái cuối.

## 17. Java Future: lỗi trong worker không chạy vào catch quanh submit

Ví dụ chương trình gửi `OrderService.create` vào một executor:

```java
Future<?> future = executor.submit(() ->
        new OrderService().create(new Request("A", 2),
                new FakeStore(Mode.READ_FAIL),
                new FakeNotifier(Mode.READ_FAIL)));
```

Đây là mảnh từ `asyncFailure` của chương trình. Lỗi từ `create` xảy ra trong worker. Nó không tự đi vào một `catch` bao quanh `submit()` ở caller; thay vào đó Future hoàn tất bất thường.

Khi gọi:

```java
try {
    future.get(5, TimeUnit.SECONDS);
} catch (ExecutionException ex) {
    Throwable cause = ex.getCause();
    // Kiểm tra cause là DomainFailure có code DEPENDENCY_UNAVAILABLE.
}
```

đây là mảnh trong method đã quản lý checked exceptions. `Future.get()` ném `ExecutionException` với nguyên nhân là `DomainFailure`. Task đã lỗi theo hợp đồng, không phải lỗi mạng của thread caller.

Phân biệt:

- `ExecutionException`: tác vụ hoàn tất bất thường, xem cause.
- `InterruptedException`: thread đang **đợi** Future bị interrupt.
- `TimeoutException`: caller chờ quá thời hạn, không chứng minh task đã bị hủy.
- `CancellationException`: Future đã bị cancel.

Không dùng `catch (Exception)` rồi đổi mọi thứ thành lỗi dependency; một worker bug, timeout caller và cancellation là các tình trạng khác nhau.

## 18. Kiểm thử `cancel(true)` và interruption hợp tác

Phần `asyncCancellation()` tạo worker chờ trên `CountDownLatch.await()`. Caller đợi `started` rồi gọi:

```java
future.cancel(true);
```

Task đang chờ nên có thể phản ứng với interrupt bằng `InterruptedException`. Trong worker:

```java
try {
    new CountDownLatch(1).await();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
} finally {
    finished.countDown();
}
```

Đây là phần trích của chương trình đầy đủ. Handler khôi phục cờ và để task kết thúc, `finally` gửi tín hiệu `finished` để test xác nhận worker đã đi qua điểm cleanup.

Bài test xác nhận:

1. Worker **đã bắt đầu** trước khi gọi cancel.
2. `future.cancel(true)` trả true trong kịch bản xác định này.
3. Worker báo `finished` trong thời hạn test.
4. `future.get()` ném `CancellationException`.
5. Pool được yêu cầu shutdown và `awaitTermination` xác nhận chấm dứt.

**Quan trọng:** `Future.cancel(true)` là yêu cầu hủy. Với tác vụ không hợp tác hoặc I/O không interruptible, nó không cưỡng chế toàn bộ công việc dừng. Future có thể báo cancelled trong khi một số side effect còn tiếp diễn. Vì thế test dùng latch để xác minh worker đi qua cleanup thay vì chỉ dựa vào `future.isDone()`.

Nếu task đang thực hiện giao dịch thật, cần chính sách riêng về idempotency và trạng thái sau hủy; ví dụ demo không thực hiện giao dịch trong kịch bản cancellation để tránh kết quả không xác định.

## 19. Quản lý pool và tránh treo bài kiểm thử

Trong hai hàm async, executor được tạo bằng `Executors.newSingleThreadExecutor()`. Khi rời hàm:

```java
executor.shutdownNow();
check(executor.awaitTermination(5, TimeUnit.SECONDS),
        "executor did not stop");
```

Đây là mảnh của chương trình. `shutdownNow()` cố yêu cầu dừng các tác vụ còn chạy, thường qua interruption. `awaitTermination()` đợi executor kết thúc; nếu không chấm dứt trong hạn, bài kiểm tra phát hiện thất bại thay vì im lặng.

Trong code production thông thường:

- `shutdown()` là yêu cầu ngừng nhận task mới, cho tác vụ đã nộp hoàn tất theo chính sách.
- `awaitTermination(timeout, unit)` dùng để đợi có hạn.
- `shutdownNow()` là **best effort**, không bảo đảm mọi worker lập tức dừng.
- Nếu thread quản lý pool bị interrupt trong lúc chờ, nó cần tuân thủ chính sách hủy và thường restore interrupt flag nếu không truyền exception tiếp.
- Không shutdown shared executor mà framework hoặc module khác đang sở hữu.

Test có thể dùng timeout rộng hơn 5 giây trên CI quá tải, nhưng không nên dùng `Thread.sleep` tùy ý để đoán thread đã xong. `CountDownLatch`, `Future.get(timeout)` và `awaitTermination` cho tín hiệu rõ hơn.

## 20. Từ chương trình self-check sang JUnit 5

Chương trình dùng `check(...)` để có thể chạy độc lập, không cần build tool. Trong dự án thật, nên tổ chức thành các test JUnit Jupiter. Một ví dụ:

```java
@Test
void notificationFailureDoesNotUndoPersistedOrder() {
    FakeStore store = new FakeStore(Mode.NOTIFY_FAIL);
    FakeNotifier notifier = new FakeNotifier(Mode.NOTIFY_FAIL);
    OrderService service = new OrderService();

    DomainFailure failure = assertThrows(
            DomainFailure.class,
            () -> service.create(new Request("A", 2), store, notifier));

    assertEquals(Code.NOTIFICATION_FAILED, failure.code);
    assertInstanceOf(IOException.class, failure.getCause());
    assertTrue(store.persisted);
    assertEquals(1, store.closes);
    assertEquals(1, notifier.sends);
}
```

Đây là **mảnh JUnit 5**, cần đặt trong test class có imports/khả năng truy cập các kiểu bên trong `OrderIntegrationDemo`. Nó không phải chương trình chạy độc lập bằng `javac` nếu chưa thêm JUnit và điều chỉnh package/static imports.

Một test khác để kiểm tra suppression:

```java
@Test
void readFailureKeepsCloseFailureSuppressed() {
    FakeStore store = new FakeStore(Mode.READ_AND_CLOSE_FAIL);
    FakeNotifier notifier = new FakeNotifier(Mode.READ_AND_CLOSE_FAIL);

    DomainFailure failure = assertThrows(DomainFailure.class,
            () -> new OrderService().create(new Request("A", 2),
                    store, notifier));

    assertEquals(Code.DEPENDENCY_UNAVAILABLE, failure.code);
    assertInstanceOf(IOException.class, failure.getCause());
    assertEquals(1, failure.getSuppressed().length);
    assertEquals("close failed", failure.getSuppressed()[0].getMessage());
    assertFalse(store.persisted);
    assertEquals(1, store.closes);
}
```

Các assertion cần import `org.junit.jupiter.api.Test` và các static methods thuộc `org.junit.jupiter.api.Assertions`. Khi tách các class thành source files riêng, nên inject repository/notifier qua interface thay vì để service phụ thuộc trực tiếp vào fake. Đó là bước refactor tự nhiên khi chuyển demo học tập thành kiến trúc production.

## 21. Refactor sang ports/adapters để kiểm thử dễ bảo trì

Ở demo, `OrderService.create` nhận `FakeStore` và `FakeNotifier` trực tiếp để nội dung nằm trong một file có thể chạy. Trong dự án thật, nên chuyển dependency sang interface:

```java
interface OrderStore {
    Session open() throws IOException;
    interface Session extends AutoCloseable {
        Product read(String id) throws IOException;
        void save(Receipt receipt) throws IOException;
        @Override void close() throws IOException;
    }
}

interface Notifier {
    void send(Receipt receipt) throws IOException;
}
```

Đây là **mảnh thiết kế API**, với `Product`/`Receipt` thuộc domain đã định nghĩa trong ứng dụng. Nếu `open()` cũng có thể ném IOException, service cần map riêng lỗi khởi tạo session; không được nhầm với close failure.

Những lợi ích:

- Unit test dùng fake in-memory, kiểm soát rõ failure modes.
- Integration test dùng adapter thật kết nối DB/message broker.
- Service chỉ biết hợp đồng đọc/lưu/gửi, không cần biết JDBC/HTTP implementation.
- Dễ gắn retry, timeout, circuit breaker tại adapter với trách nhiệm rõ.
- Có thể kiểm thử API mapping và transaction boundary độc lập.

Tuy nhiên đừng tạo interface chỉ vì “mọi class đều cần interface”. Dùng abstraction khi nó giúp thay dependency, kiểm soát tác dụng phụ hoặc cải thiện testability thực tế.

## 22. Mở rộng kiểm thử với tình huống lỗi khi mở session

Demo giả lập lỗi ở `read`, `save` và `close`, nhưng `FakeStore.open()` luôn thành công. Một repository thật có thể lỗi ngay khi lấy connection.

Nếu thay chữ ký:

```java
OrderStore.Session open() throws IOException;
```

thì service phải phân biệt **mở không thành công** với **đóng thất bại**. Không nên có một `catch (IOException)` duy nhất đặt sau toàn bộ try-with-resources rồi mặc định mọi I/O error là close failure.

Ví dụ pattern:

```java
OrderStore.Session session;
try {
    session = store.open();
} catch (IOException ex) {
    throw new DomainFailure(Code.DEPENDENCY_UNAVAILABLE, ex);
}

try (session) {
    // Read, validate, save, notify.
} catch (IOException ex) {
    // IOException từ close nếu các thao tác trong body
    // đã được bắt/dịch sang domain error đúng chỗ.
    throw new DomainFailure(Code.CLOSE_FAILED, ex);
}
```

Đây là **mảnh kiến trúc**, không phải chương trình độc lập; `OrderStore` và `DomainFailure` phải được định nghĩa. Mục tiêu là tránh phân loại sai nguồn exception. Nếu các thao tác body còn có IOException chưa được dịch, catch bên ngoài vẫn có thể nhận chúng; do đó không được khẳng định mọi IOException tới catch cuối chắc chắn từ `close` nếu contract body không bảo đảm.

Test mở session thất bại cần xác nhận không gọi read/save/notify và không gọi close trên session chưa được khởi tạo thành công.

## 23. Những lỗi tích hợp không thể giả lập bằng một exception đơn

Một lỗi thực tế thường có nhiều trạng thái:

**Timeout sau lệnh ghi:** Caller đợi quá hạn, nhưng DB có thể đã commit. Retry phải dùng idempotency hoặc đối chiếu, không chỉ dựa vào `TimeoutException`.

**Mất ACK từ broker:** Thông báo có thể đã được gửi nhưng ứng dụng không nhận phản hồi. Không giả định `IOException` nghĩa là notification chưa được phát.

**Deadlock hoặc lock timeout:** Transaction có thể bị rollback bởi database, hoặc chỉ bị hủy một statement. Cần contract cụ thể của driver/DB.

**Bị interrupt trong I/O:** Một số API phản ứng với interrupt, số khác cần đóng socket/stream hoặc đặt timeout; không hứa mọi `cancel(true)` dừng ngay.

**Close lỗi sau thao tác thành công:** Có thể dữ liệu đã lưu một phần, flush chưa chắc thành công hoặc connection pool đang gặp vấn đề. Không tự suy ra “đã/không commit” nếu chưa biết semantics.

**Concurrent requests cùng key:** Một fake single-thread test không chứng minh uniqueness, transaction isolation, race freedom hoặc idempotency dưới tải.

**Lỗi trong bước test setup:** Không nhầm exception do tạo database/container/fixture với lỗi của SUT. Đặt Arrange ngoài lambda của `assertThrows`.

Bởi vậy một suite tốt thường có **unit tests cho mapping và nhánh điều khiển**, **component tests cho workflow**, **integration tests với dependency thật hoặc môi trường gần thật**, và một số **end-to-end tests** kiểm tra giao thức công khai. Mỗi tầng kiểm thử có mục đích riêng, không cần lặp toàn bộ trường hợp ở mọi tầng.

## 24. Thiết kế xử lý lỗi và phản hồi an toàn

`DomainFailure.code` có thể được map sang một response ở API boundary. Ví dụ thiết kế:

| Domain code | Response gợi ý | Lưu ý |
| --- | --- | --- |
| `INVALID_REQUEST` | 400 / `INVALID_REQUEST` | Không lộ raw input nếu chứa dữ liệu riêng tư |
| `NOT_FOUND` | 404 / `NOT_FOUND` | Phụ thuộc quyền truy cập và chính sách tránh enumeration |
| `INVALID_DATA` | 502 hoặc 500 tùy boundary | Dữ liệu nguồn sai khác với input caller sai |
| `DEPENDENCY_UNAVAILABLE` | 503 khi thực sự unavailable | Giữ cause ở log nội bộ được phân quyền |
| `NOTIFICATION_FAILED` | Trạng thái partial/pending có nghĩa nghiệp vụ | Không gợi ý retry toàn workflow một cách mù quáng |
| `CLOSE_FAILED` | Lỗi hoặc trạng thái chưa xác định | Không khẳng định save chưa diễn ra |

Đây là **gợi ý kiến trúc**, không phải mapping HTTP cố định cho mọi hệ thống. `INVALID_DATA` có thể là dữ liệu từ bên ngoài hay do chính ứng dụng tạo; cách gắn status phụ thuộc hợp đồng.

Phản hồi công khai nên có code ổn định, message an toàn và correlation ID không nhạy cảm. Không đưa nguyên `IOException.getMessage()`, stack trace, bearer token hoặc request body vào response. Các lỗi từ dependency có thể chứa dữ liệu không được phép xuất.

Trong logging, ghi operation, code, elapsed time, request ID và trạng thái có side effect theo schema. Không ghi cùng một Throwable ở mọi tầng chỉ vì đã có catch; xác định một nơi chịu trách nhiệm quan sát lỗi cuối cùng.

## 25. Checklist review trước khi coi bài thực hành hoàn chỉnh

1. **Đúng phạm vi:** Workflow có validation, đọc, tính, ghi, notification, đóng resource và caller async.
2. **Code runnable:** File/class name khớp, dùng API Java 17+, import đầy đủ, có lệnh `javac`/`java`.
3. **Đường thành công:** Có receipt chính xác, resource được đóng và dependency cần thiết đã được gọi.
4. **Validation:** Input sai không được mở session hay tạo side effect.
5. **Not found và I/O:** Không trộn “không có dữ liệu” với “không thể truy vấn”.
6. **Dữ liệu nguồn sai:** Không lưu output dựa trên đơn giá bất hợp lệ.
7. **Dependency cause:** Exception translation giữ `getCause()` để chẩn đoán.
8. **Partial success:** Notifier/close lỗi sau save không khiến test tự kết luận chưa lưu.
9. **Suppressed:** Khi body và close cùng lỗi, primary và `getSuppressed()` đúng.
10. **Executor:** `Future.get()` phân biệt `ExecutionException` với cancellation/interrupt/timeout.
11. **Cancellation:** Task hợp tác interrupt, test có signal xác nhận cleanup và đóng pool.
12. **Isolation:** Mỗi scenario có fake mới; không phụ thuộc thứ tự test hoặc dữ liệu từ case trước.
13. **Determinism:** Dùng latch/timeout có ý nghĩa, không dựa vào sleep ngắn ngẫu nhiên.
14. **Cleanup:** Pool và session có đường đóng, lỗi cleanup không bị che.
15. **Thông báo lỗi:** Client không nhận raw cause hoặc dữ liệu nhạy cảm.
16. **Retry:** Không tự retry thao tác đã có thể commit nếu thiếu idempotency và đối chiếu.
17. **Test boundaries:** Fixture setup, hành động SUT và assertion được phân tách.
18. **Khả năng mở rộng:** Ports/adapters cho phép chuyển từ fake sang dependency thật với integration tests phù hợp.

**Kết luận:** ví dụ Java chỉ thực sự hoàn thiện khi **đường thất bại được đặc tả và kiểm thử** như đường thành công. Hãy kiểm tra mã lỗi, cause, suppressed exceptions, số lần gọi dependency, trạng thái đã lưu, cách hủy task và việc đóng tài nguyên. Một exception được bắt đúng class nhưng làm mất thông tin hoặc che side effect vẫn là một lỗi thiết kế — nhất là khi tích hợp nhiều dependency và thực thi bất đồng bộ.


</details>

- [Quay lại đầu trang](#back-to-top)
