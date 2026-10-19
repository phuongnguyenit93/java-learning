<a id="back-to-top"></a>

# Ngoại lệ API

## Menu
- [IOException, FileNotFoundException và lỗi đọc ghi](#api-exceptions-001)
- [NoSuchFileException, AccessDeniedException và các lỗi NIO](#api-exceptions-002)
- [EOFException và xử lý dữ liệu kết thúc sớm](#api-exceptions-003)
- [SocketException, SocketTimeoutException và lỗi mạng](#api-exceptions-004)
- [SQLException: cause, SQLState và chuỗi nextException](#api-exceptions-005)
- [ParseException, DateTimeParseException và ngữ cảnh phân tích dữ liệu](#api-exceptions-006)
- [ReflectiveOperationException và các lỗi tra cứu bằng reflection](#api-exceptions-007)
- [InvocationTargetException và ngoại lệ thật của phương thức được gọi](#api-exceptions-008)
- [ClassNotFoundException khác NoClassDefFoundError](#api-exceptions-009)
- [UndeclaredThrowableException khi dùng dynamic proxy](#api-exceptions-010)
- [UncheckedIOException khi API không khai báo checked IOException](#api-exceptions-011)

## <a id="api-exceptions-001">IOException, FileNotFoundException và lỗi đọc ghi</a>

<details>
<summary>Xem chi tiết</summary>

# IOException, FileNotFoundException và lỗi đọc ghi trong Java

## 1. Bức tranh tổng quan

Đọc và ghi dữ liệu là những tác vụ thường xuyên phải làm việc với **tài nguyên bên ngoài**: tệp, thiết bị, socket, hệ thống tệp mạng, luồng dữ liệu hoặc các API cung cấp input/output. Những thao tác ấy có thể thất bại bởi các nguyên nhân không hoàn toàn do chương trình kiểm soát: tệp không tồn tại, mất quyền truy cập, đường dẫn không hợp lệ, thiết bị ngắt kết nối, hết dung lượng lưu trữ, lỗi giải mã, hoặc lỗi ngay cả khi đóng tài nguyên.

Trong Java, nhóm lỗi được đại diện nổi bật bằng **`java.io.IOException`**, là **checked exception**. Một lớp con quen thuộc là **`java.io.FileNotFoundException`**, thường xuất hiện khi mã dùng các API I/O cổ điển để mở tệp thất bại. Trong API hiện đại **NIO.2** (`java.nio.file.Files`, `Path`), lỗi cụ thể thường được diễn đạt bởi các lớp khác như `NoSuchFileException`, `AccessDeniedException` hoặc `FileAlreadyExistsException`, cũng nằm trong nhánh `IOException`.

Mục tiêu sau bài học:

- Phân biệt `IOException`, `FileNotFoundException` và một số ngoại lệ liên quan tới tệp trong `java.nio.file`.
- Hiểu **checked exception**: lời gọi phương thức phải `catch` hoặc khai báo `throws` khi cần.
- Biết vì sao `FileNotFoundException` không phải lời giải thích duy nhất cho mọi sự cố I/O và vì sao Java NIO.2 có thể ném `NoSuchFileException`.
- Phân biệt **kết thúc dữ liệu bình thường (EOF)** với một **lỗi đọc thực sự**.
- Mở, đọc, ghi, đóng file an toàn bằng `Files`, `InputStream`, `OutputStream`, `Reader`, `Writer` và `try-with-resources`.
- Giữ được lỗi chính, lỗi dọn dẹp và `cause` khi có nhiều thất bại.
- Biết những tình huống cần kiểm tra đường dẫn, charset, quyền sở hữu tài nguyên, chế độ ghi và chiến lược phục hồi.
- Viết thông báo lỗi/log rõ ngữ cảnh nhưng không suy đoán sai nguyên nhân.

Các ví dụ đầy đủ dùng **Java 17+**, có tên tệp, lệnh `javac`/`java` và `stdout` xác định. Một số mã minh họa không độc lập được ghi rõ.

## 2. Cây kế thừa của những ngoại lệ I/O thường gặp

```text
java.lang.Throwable
└── java.lang.Exception
    └── java.io.IOException                 [checked]
        ├── java.io.FileNotFoundException
        ├── java.io.EOFException
        ├── java.io.UTFDataFormatException
        ├── java.io.InterruptedIOException
        ├── java.io.CharConversionException
        ├── java.io.UnsupportedEncodingException
        └── java.nio.file.FileSystemException
            ├── java.nio.file.NoSuchFileException
            ├── java.nio.file.AccessDeniedException
            ├── java.nio.file.FileAlreadyExistsException
            └── java.nio.file.DirectoryNotEmptyException
```

Sơ đồ chỉ liệt kê **một phần** cây exception, không phải toàn bộ các subtype của `IOException`. Trong thực tế, mỗi API có thể chọn kiểu lỗi cụ thể phù hợp với ngữ nghĩa của nó.

| Loại ngoại lệ | Ý nghĩa thông thường | Ví dụ ngữ cảnh |
| --- | --- | --- |
| `IOException` | Lỗi chung của thao tác input/output | Đọc, ghi, đóng hoặc truyền dữ liệu thất bại |
| `FileNotFoundException` | Không mở được file theo API I/O cổ điển; không nhất thiết chỉ vì “không tồn tại” | `new FileInputStream(file)` |
| `NoSuchFileException` | Một thao tác NIO báo không có tệp/đường dẫn mục tiêu | `Files.readString(path)` với file không tồn tại |
| `AccessDeniedException` | Hệ thống tệp báo không đủ quyền truy cập | Mở hoặc ghi một path bị từ chối |
| `FileAlreadyExistsException` | API yêu cầu tạo mới nhưng mục tiêu đã tồn tại | `Files.createFile(existingPath)` |
| `DirectoryNotEmptyException` | Không thể xóa thư mục đang chứa mục | `Files.delete(nonemptyDir)` |
| `EOFException` | Một số API cần đọc đủ dữ liệu có cấu trúc nhưng gặp hết dữ liệu quá sớm | `DataInputStream.readInt()` khi thiếu byte |
| `MalformedInputException` | Dữ liệu byte không hợp lệ theo charset đang giải mã | Đọc dữ liệu UTF-8 sai cấu trúc |

**Điểm quan trọng:** `MalformedInputException` là subtype của `CharacterCodingException`, rồi tới `IOException`; sơ đồ rút gọn ở trên không liệt kê mọi nhánh trung gian. Khi cần xử lý chính xác, hãy tra cây kế thừa trong API cụ thể.

### `IOException` là checked exception

`IOException` kế thừa `Exception` nhưng **không** thuộc nhánh `RuntimeException`, do đó compiler kiểm tra đường truyền checked exception. Một phương thức gọi API có thể ném `IOException` phải:

1. **Bắt** bằng `try/catch` và xử lý thích hợp; hoặc
2. **Khai báo** `throws IOException` (hoặc kiểu checked rộng tương thích) để caller chịu trách nhiệm.

Khác với `ArithmeticException` hay `IllegalArgumentException`, `IOException` **không phải unchecked exception**. Không cần cố chuyển nó thành `RuntimeException` chỉ để bỏ nghĩa vụ thiết kế xử lý lỗi.

## 3. `IOException` và `FileNotFoundException`: quan hệ cha–con

`FileNotFoundException extends IOException`, nên mọi handler `catch (IOException e)` có thể bắt cả `FileNotFoundException`. Nhưng điều ngược lại không đúng: handler `catch (FileNotFoundException e)` **không** bắt một `IOException` bất kỳ.

Ví dụ **mảnh mã**:

```java
try {
    java.io.InputStream input =
            new java.io.FileInputStream("data.txt");
    input.close();
} catch (java.io.FileNotFoundException notFound) {
    System.out.println("Cannot open the file");
} catch (java.io.IOException otherIo) {
    System.out.println("Another I/O failure");
}
```

Đoạn trên có một rủi ro quản lý tài nguyên: nếu xảy ra lỗi giữa mở và đóng, việc đóng có thể bị bỏ qua. Mục tiêu ở đây chỉ minh họa **thứ tự catch**. Với mã thực, hãy dùng `try-with-resources` thay vì mở rồi đóng thủ công như trên.

Handler phải đặt **kiểu con trước kiểu cha**. Nếu `catch (IOException)` đặt trước `catch (FileNotFoundException)`, nhánh riêng phía sau sẽ không thể nhận một ngoại lệ nào mà nhánh cha chưa bắt và javac có thể báo handler không thể truy cập.

### Chương trình độc lập: quan hệ kế thừa

Tạo file **`IOExceptionHierarchyDemo.java`**:

```java
import java.io.FileNotFoundException;
import java.io.IOException;

public class IOExceptionHierarchyDemo {
    static void simulate() throws IOException {
        throw new FileNotFoundException("report.csv");
    }

    public static void main(String[] args) {
        try {
            simulate();
        } catch (FileNotFoundException failure) {
            System.out.println("specific="
                    + failure.getClass().getSimpleName());
            System.out.println("also IOException="
                    + (failure instanceof IOException));
        } catch (IOException failure) {
            System.out.println("other IO error");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac IOExceptionHierarchyDemo.java
java IOExceptionHierarchyDemo
```

`stdout`:

```text
specific=FileNotFoundException
also IOException=true
```

Trong chương trình:

- `simulate()` công bố kiểu rộng `throws IOException` nhưng thực sự ném `FileNotFoundException`.
- `catch (FileNotFoundException)` xử lý lỗi cụ thể trước.
- `instanceof IOException` trả `true` vì quan hệ kế thừa.
- Nhánh `catch (IOException)` dự phòng cho những `IOException` không thuộc kiểu cụ thể trước.

Khi một API công bố `throws IOException`, nó có thể ném **nhiều subclass khác nhau**, không chỉ `FileNotFoundException`.

## 4. `FileNotFoundException` không có nghĩa chính xác “tệp không tồn tại”

Tên class dễ dẫn đến hiểu nhầm. Với `FileInputStream` hoặc `FileOutputStream` kiểu cổ điển, một `FileNotFoundException` có thể xuất hiện khi **không thể mở file** vì đường dẫn không tồn tại, file không thể truy cập, trỏ tới thư mục, hoặc một vấn đề mở file phụ thuộc hệ điều hành.

Ví dụ:

```java
try (java.io.FileInputStream input =
        new java.io.FileInputStream("orders.csv")) {
    int firstByte = input.read();
}
```

Đây là **mảnh mã trong phương thức xử lý/khai báo `IOException`**. Nếu không mở được tệp, constructor có thể ném `FileNotFoundException` ngay trước khi vào thân `try`.

Những nguyên nhân có thể gồm:

- File không tồn tại tại **đường dẫn đang được chương trình dùng**.
- Đường dẫn tương đối được giải theo thư mục làm việc khác với dự đoán.
- File nằm trong một thư mục không truy cập được.
- Hệ điều hành từ chối quyền mở theo chế độ yêu cầu.
- Path trỏ đến đối tượng không phù hợp với API (ví dụ directory thay vì regular file).
- Tệp đang bị khóa hoặc chia sẻ không phù hợp trên một số nền tảng.

Không được mặc định rằng **mọi `FileNotFoundException` có thể sửa bằng cách tự tạo file mới**. Nếu nguyên nhân là thiếu quyền hoặc chế độ mở không phù hợp, tự tạo/tự ghi đè có thể khiến dữ liệu xấu hơn.

## 5. `FileNotFoundException` cổ điển và `NoSuchFileException` của NIO.2

Java hỗ trợ nhiều nhóm API:

**I/O cổ điển (`java.io`):**

```java
new java.io.FileInputStream("missing.txt");
```

**NIO.2 (`java.nio.file.Files`):**

```java
java.nio.file.Files.readString(
        java.nio.file.Path.of("missing.txt"));
```

Hai biểu thức trên là **mảnh minh họa**. Với tệp thực sự không tồn tại, `FileInputStream` thường ném `FileNotFoundException`; `Files.readString` trên hệ thống tệp mặc định thường ném `NoSuchFileException`. Cả hai nằm dưới `IOException`.

Điểm khác nhau là **API và phân loại lỗi**: bạn không nên mặc định mọi cách mở file đều phát ra cùng subclass.

### Chương trình chạy được: cùng file thiếu nhưng khác subclass

Tạo file **`MissingFileApisDemo.java`**:

```java
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public class MissingFileApisDemo {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("io-example-");
        Path missing = directory.resolve("not-created.txt");

        try {
            try (FileInputStream input =
                    new FileInputStream(missing.toFile())) {
                System.out.println(input.read());
            } catch (FileNotFoundException failure) {
                System.out.println("classic="
                        + failure.getClass().getSimpleName());
            }

            try {
                Files.readString(missing);
            } catch (NoSuchFileException failure) {
                System.out.println("nio="
                        + failure.getClass().getSimpleName());
            }
        } finally {
            Files.delete(directory);
        }
    }
}
```

```bash
javac MissingFileApisDemo.java
java MissingFileApisDemo
```

`stdout` trong một môi trường chạy bình thường với hệ thống tệp mặc định hỗ trợ thư mục tạm:

```text
classic=FileNotFoundException
nio=NoSuchFileException
```

Chương trình tạo **một thư mục tạm mới** để đảm bảo file `not-created.txt` không nằm ở đó. Vì vậy kết quả không phụ thuộc có sẵn một file tên `missing.txt` trong working directory hay không.

Chú ý:

- `Files.createTempDirectory` có thể tự ném `IOException` do vấn đề môi trường. Đây không phải tình huống “bài chạy chắc chắn thành công bất kể quyền của máy”.
- `Files.delete(directory)` có thể ném `IOException` nếu môi trường can thiệp; ví dụ dùng tài nguyên tạm trong môi trường cho phép.
- `NoSuchFileException` kế thừa `FileSystemException`, rồi `IOException`; **không phải subclass của `FileNotFoundException`**.
- Để phục vụ nhiều API khác nhau, `catch (IOException)` thường là ranh giới chung; phân loại subclass khi thật sự cần hành vi phục hồi khác biệt.

## 6. `NoSuchFileException`, `AccessDeniedException` và các lỗi NIO khác

NIO.2 cung cấp các subclass cho thông tin về hệ thống tệp chi tiết hơn so với `FileNotFoundException` tổng quát.

Ví dụ chữ ký minh họa:

```java
static String load(java.nio.file.Path path)
        throws java.io.IOException {
    return java.nio.file.Files.readString(path);
}
```

Caller có thể tổ chức `catch` theo **kiểu con trước, kiểu cha sau**:

```java
try {
    String content = load(path);
    System.out.println(content);
} catch (java.nio.file.NoSuchFileException failure) {
    System.out.println("File missing");
} catch (java.nio.file.AccessDeniedException failure) {
    System.out.println("Access denied");
} catch (java.io.IOException failure) {
    System.out.println("Other I/O failure");
}
```

Đây là **mảnh minh họa** có biến `path` kiểu `Path` và phương thức `load` từ đoạn trước. Nó không thay thế việc phân quyền phía hệ điều hành, log/giám sát hay kiểm tra input.

### Không đoán loại IOException từ text của `getMessage()`

Thông điệp hệ thống có thể chứa đường dẫn, mã lỗi và cách diễn đạt khác nhau giữa hệ điều hành, provider, locale và JDK. **Không dùng `failure.getMessage().contains("No such file")` làm hệ thống phân loại lỗi**, vì thông điệp không phải hợp đồng ổn định.

Nếu cần phân biệt trường hợp file bị từ chối, dùng `instanceof AccessDeniedException` hoặc nhánh `catch` cụ thể. Nhưng cũng cần chấp nhận rằng provider không bắt buộc phân biệt mọi tình huống theo cùng một subclass trong mọi môi trường.

### `FileSystemException` có thông tin gì?

`FileSystemException` có thể cung cấp `getFile()`, `getOtherFile()` và `getReason()`. Chúng hữu ích để chẩn đoán **tệp nguồn, tệp đích và lý do** ở các hoạt động như `Files.move` hoặc `Files.copy`.

Hãy cân nhắc bảo mật khi ghi đường dẫn vào log; đường dẫn người dùng và tên tệp có thể chứa dữ liệu riêng tư. Cần phân biệt log nội bộ và thông báo hiển thị ra bên ngoài.

## 7. Đọc/ghi thành công bằng `Files` và UTF-8

Đối với file văn bản, `Files.writeString`, `Files.readString` hoặc `newBufferedReader`/`newBufferedWriter` thường rõ ràng hơn việc quản lý mảng byte thủ công. Chọn charset rõ ràng để tránh phụ thuộc mã hóa mặc định.

### Chương trình hoàn chỉnh: tạo, ghi, đọc, xóa file tạm

Tạo file **`SuccessfulFileIODemo.java`**:

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class SuccessfulFileIODemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("java-io-", ".txt");

        try {
            Files.writeString(
                    file, "alpha\nbeta\n", StandardCharsets.UTF_8);

            try (BufferedReader reader = Files.newBufferedReader(
                    file, StandardCharsets.UTF_8)) {
                System.out.println("line1=" + reader.readLine());
                System.out.println("line2=" + reader.readLine());
                System.out.println("end=" + reader.readLine());
            }

            System.out.println("full="
                    + Files.readString(file, StandardCharsets.UTF_8)
                            .replace("\n", "|"));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

```bash
javac SuccessfulFileIODemo.java
java SuccessfulFileIODemo
```

`stdout`:

```text
line1=alpha
line2=beta
end=null
full=alpha|beta|
```

Vì file được tạo mới và ghi chuỗi xác định, dữ liệu không phụ thuộc file sẵn có. `StandardCharsets.UTF_8` được chỉ định khi đọc và ghi.

Các ý cần nhớ:

- `Files.createTempFile` và `Files.writeString` đều có thể ném `IOException`; `main throws IOException` là một chính sách truyền lỗi phù hợp cho **bài chạy thử**, không tự giải quyết lỗi.
- `BufferedReader.readLine()` trả `null` khi hết dòng — đó là **điều kiện kết thúc dữ liệu bình thường**, không phải ngoại lệ.
- `try-with-resources` tự đóng reader ngay cả khi `readLine()` thất bại.
- `Files.deleteIfExists` ở `finally` có thể thất bại; nếu cleanup rất quan trọng, cần dùng một phạm vi quản lý/ghi nhận lỗi phù hợp để không mất lỗi chính.
- Trong ứng dụng thực, thường không xóa file vừa tạo ngay: ví dụ xóa để không để lại dữ liệu tạm sau khi chạy.

### Khi nào dùng stream/reader?

- `InputStream` / `OutputStream`: làm việc với **byte**; phù hợp file nhị phân, giao thức, ảnh, dữ liệu nén.
- `Reader` / `Writer`: làm việc với **ký tự** và charset ở lớp chuyển đổi.
- `BufferedReader`: tiện đọc từng dòng văn bản.
- `Files.readString` / `Files.writeString`: tiện thao tác văn bản ở mức cao khi kích thước dữ liệu phù hợp.
- `Files.newInputStream` / `newOutputStream`: làm việc trực tiếp với luồng byte của hệ thống tệp.

Đừng dùng `Reader` để xử lý file nhị phân tùy ý: giải mã ký tự sai có thể làm biến dạng byte hoặc phát sinh lỗi giải mã.

## 8. EOF bình thường **không** phải `IOException`

Một trong những nhầm lẫn phổ biến nhất là: “`read()` không còn dữ liệu thì ném ngoại lệ.” Với nhiều API stream cơ bản, đó là **sai**.

Ví dụ `InputStream.read()`:

- Trả giá trị `0..255` nếu đọc được một byte.
- Trả `-1` nếu đã đến cuối stream bình thường.
- Có thể **ném `IOException`** nếu thao tác đọc thực sự thất bại.

Tương tự `Reader.read()` thường trả một mã ký tự hoặc `-1` khi EOF; `BufferedReader.readLine()` trả `null` khi đã hết dòng. Đây là **giá trị sentinel**, không phải checked exception.

### Chương trình chạy được: EOF và lỗi đọc thực

Tạo file **`EofVersusIOExceptionDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class EofVersusIOExceptionDemo {
    static class BrokenInput extends InputStream {
        @Override
        public int read() throws IOException {
            throw new IOException("device disconnected");
        }
    }

    public static void main(String[] args) throws IOException {
        try (InputStream input = new ByteArrayInputStream(
                "A".getBytes(StandardCharsets.US_ASCII))) {
            System.out.println("first=" + input.read());
            System.out.println("second=" + input.read());
        }

        try (InputStream broken = new BrokenInput()) {
            System.out.println(broken.read());
        } catch (IOException failure) {
            System.out.println("error=" + failure.getMessage());
        }
    }
}
```

```bash
javac EofVersusIOExceptionDemo.java
java EofVersusIOExceptionDemo
```

`stdout`:

```text
first=65
second=-1
error=device disconnected
```

Phân tích:

1. ASCII `A` có byte value 65. Lần đọc đầu trả 65.
2. Lần đọc thứ hai trả `-1`, báo **đã đọc hết** chứ không báo lỗi.
3. `BrokenInput` chủ động ném `IOException("device disconnected")` để mô phỏng lỗi thiết bị. Handler bắt đúng ngoại lệ.
4. `ByteArrayInputStream` là một resource trong bộ nhớ để demo EOF ổn định; `BrokenInput` là resource mô phỏng lỗi, không cần thiết bị thật.

### `EOFException` có mâu thuẫn không?

Không. `EOFException` là một subclass của `IOException` xuất hiện từ **những API đòi hỏi đủ một cấu trúc dữ liệu**, ví dụ `DataInputStream.readInt()` cần đọc đúng 4 byte. Nếu stream hết sau 1 byte, nó không thể trả một `int` hợp lệ nên ném `EOFException`.

Cùng là hết dữ liệu, **hợp đồng phương thức khác nhau**:

| API | Hết dữ liệu bình thường/thiếu dữ liệu | Cách báo |
| --- | --- | --- |
| `InputStream.read()` | Không còn byte | Trả `-1` |
| `Reader.read()` | Không còn ký tự | Trả `-1` |
| `BufferedReader.readLine()` | Không còn dòng | Trả `null` |
| `DataInputStream.readInt()` | Không đủ 4 byte để giải mã `int` | Ném `EOFException` |
| `InputStream.readNBytes(int)` | Có thể trả ít byte hơn yêu cầu khi EOF | Trả mảng ngắn hơn |
| `DataInputStream.readFully(byte[])` | Không đủ byte để lấp đầy buffer | Ném `EOFException` |

Vì vậy, đừng viết một handler `catch (EOFException)` cho `BufferedReader.readLine()` rồi kỳ vọng nó hoạt động khi hết file bình thường.

## 9. Chương trình độc lập: `DataInputStream` và `EOFException`

Tạo file **`EofExceptionDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;

public class EofExceptionDemo {
    public static void main(String[] args) throws IOException {
        byte[] incomplete = {1, 2};

        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(incomplete))) {
            System.out.println("reading int");
            System.out.println(input.readInt());
        } catch (EOFException failure) {
            System.out.println("not enough bytes for int");
        }
    }
}
```

```bash
javac EofExceptionDemo.java
java EofExceptionDemo
```

`stdout`:

```text
reading int
not enough bytes for int
```

`DataInputStream.readInt()` cần đọc đúng bốn byte theo định dạng của nó. Ví dụ cố ý chỉ cung cấp hai byte, nên method không thể trả một `int` và ném `EOFException`.

Đừng dùng `EOFException` chung cho mọi trường hợp thiếu file. `FileNotFoundException`/`NoSuchFileException` liên quan **mở hoặc truy cập file**, trong khi `EOFException` của ví dụ liên quan **dữ liệu bên trong một stream đã có** nhưng không đủ cho thao tác cần thực hiện.

## 10. Lỗi ghi: `IOException` có thể xảy ra muộn

Với `OutputStream`, một lệnh `write` có thể thất bại ngay lúc ghi. Với buffered writer/stream, lỗi có thể chỉ hiện ra ở thời điểm `flush()` hoặc `close()`, tùy cấu trúc buffer và implementation. Đừng mặc định chỉ có `write` mới cần xử lý exception.

### Chương trình chạy được: mô phỏng lỗi `write()`

Tạo file **`WriteIOExceptionDemo.java`**:

```java
import java.io.IOException;
import java.io.OutputStream;

public class WriteIOExceptionDemo {
    static class BrokenOutput extends OutputStream {
        @Override
        public void write(int value) throws IOException {
            throw new IOException("write failed");
        }
    }

    public static void main(String[] args) {
        try (OutputStream output = new BrokenOutput()) {
            output.write(65);
            System.out.println("unexpected success");
        } catch (IOException failure) {
            System.out.println("error=" + failure.getMessage());
        }
    }
}
```

```bash
javac WriteIOExceptionDemo.java
java WriteIOExceptionDemo
```

`stdout`:

```text
error=write failed
```

`BrokenOutput` chủ động ném lỗi khi ghi một byte, nên `unexpected success` không chạy. Đây là lỗi **ghi**, không phải EOF và không phải tự động là `FileNotFoundException`.

### Buffered output và ý nghĩa của `flush()`

Một buffered output stream có thể tích lũy dữ liệu trong RAM trước khi chuyển đến delegate hoặc hệ điều hành. Vì thế:

- `write(...)` có thể chỉ ghi vào buffer.
- `flush()` yêu cầu đẩy những dữ liệu còn đệm xuống tầng bên dưới, nhưng **không đồng nghĩa** bảo đảm dữ liệu đã được ghi bền vững lên đĩa trong mọi hệ thống.
- `close()` thường xử lý dọn dẹp và có thể flush trước khi đóng, tùy API.
- Lỗi có thể nảy sinh khi **flush/close** dù những lời gọi `write` trước đó không báo lỗi.
- Với tính bền vững của dữ liệu tệp, API như `FileChannel.force(...)` hoặc thiết kế commit/rename có thể cần được xem xét theo yêu cầu; không suy ra “không có IOException” nghĩa là có transaction an toàn.

Khi điều khiển dữ liệu quan trọng, hãy hoàn tất đóng/flush theo đúng hợp đồng trước khi thông báo thao tác ghi thành công.

### `PrintStream` và `PrintWriter` là trường hợp đặc biệt

Không phải mọi API ghi đều **ném checked IOException ra caller**. `PrintStream` và `PrintWriter` thường **không ném IOException trực tiếp từ những phương thức in thông thường**; chúng có cơ chế trạng thái lỗi như `checkError()`.

Do đó, `System.out.println(...)` có thể không báo checked `IOException` giống một `OutputStream.write(...)` thông thường. Khi chọn API output, cần hiểu hợp đồng của class cụ thể, không xem mọi thao tác “ghi” có hành vi ngoại lệ giống nhau.

## 11. Lỗi mã hóa và giải mã: vẫn thuộc nhóm I/O

Đọc văn bản nghĩa là chuyển **byte** thành **ký tự** theo một charset. Nếu file không chứa byte hợp lệ cho charset được chọn, các API giải mã với hành vi báo lỗi có thể ném ngoại lệ thuộc họ `IOException`, ví dụ `MalformedInputException`.

Một chương trình có thể mở file thành công nhưng vẫn lỗi **khi bắt đầu đọc và giải mã**. Vì vậy “mở được file” chưa có nghĩa “file đúng định dạng”.

### Chương trình chạy được: byte không hợp lệ với UTF-8

Tạo file **`MalformedUtf8Demo.java`**:

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class MalformedUtf8Demo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("invalid-utf8-", ".txt");

        try {
            Files.write(file, new byte[] {(byte) 0xC3, (byte) 0x28});

            try (BufferedReader reader = Files.newBufferedReader(
                    file, StandardCharsets.UTF_8)) {
                System.out.println(reader.readLine());
            } catch (MalformedInputException failure) {
                System.out.println("invalid UTF-8");
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

```bash
javac MalformedUtf8Demo.java
java MalformedUtf8Demo
```

`stdout`:

```text
invalid UTF-8
```

`0xC3 0x28` là cặp byte không tạo thành chuỗi UTF-8 hợp lệ: sau leading byte `0xC3` phải là continuation byte hợp lệ, trong khi `0x28` không có dạng đó. `Files.newBufferedReader(path, UTF_8)` dùng một decoder được cấu hình để **báo lỗi** thay vì tùy tiện thay thế byte sai bằng ký tự khác.

Điểm cần phân biệt:

- `FileNotFoundException`/`NoSuchFileException`: thường liên quan **tồn tại/mở file**.
- `MalformedInputException`: file có thể tồn tại và mở được, nhưng **nội dung byte không hợp lệ** theo charset.
- Các API giải mã khác có thể được cấu hình `CodingErrorAction.REPLACE` để thay ký tự lỗi, thay vì ném. Không suy ra mọi cách đọc text đều phát sinh cùng exception.

Nếu file đến từ nguồn không tin cậy, cần chọn charset có chủ ý, xử lý dữ liệu không hợp lệ đúng chính sách và không dùng default charset tùy môi trường một cách vô thức.

## 12. `try-with-resources`: đóng tài nguyên ngay cả khi đọc/ghi lỗi

Resource I/O thường triển khai `Closeable` (một subtype của `AutoCloseable`). Khi `close()` được gọi sai thời điểm hoặc không được gọi, có thể xảy ra leak handle, giữ khóa file, socket không giải phóng, hoặc dữ liệu buffered không hoàn tất.

Mẫu khuyến nghị:

```java
try (java.io.InputStream input =
        java.nio.file.Files.newInputStream(path)) {
    // Đọc dữ liệu.
}
```

Đây là **mảnh mã** với biến `path` và ngữ cảnh xử lý/khai báo `IOException`. TWR tự gọi `input.close()` khi khối hoàn thành bình thường hoặc có ngoại lệ.

### Khi close cũng thất bại: `getSuppressed()`

Nếu thân `try` ném `IOException("read failed")` và `close()` ném `IOException("close failed")`, Java giữ lỗi đọc là **primary** và gắn lỗi close là **suppressed exception**, quan sát bằng `getSuppressed()`.

### Chương trình chạy được: giữ cả lỗi xử lý và lỗi đóng

Tạo file **`IoSuppressedDemo.java`**:

```java
import java.io.IOException;

public class IoSuppressedDemo {
    static class BrokenResource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            System.out.println("closing");
            throw new IOException("close failed");
        }
    }

    public static void main(String[] args) {
        try (BrokenResource resource = new BrokenResource()) {
            throw new IOException("read failed");
        } catch (IOException failure) {
            System.out.println("primary=" + failure.getMessage());
            System.out.println("suppressed count="
                    + failure.getSuppressed().length);
            System.out.println("suppressed="
                    + failure.getSuppressed()[0].getMessage());
        }
    }
}
```

```bash
javac IoSuppressedDemo.java
java IoSuppressedDemo
```

`stdout`:

```text
closing
primary=read failed
suppressed count=1
suppressed=close failed
```

Nếu thân `try` hoàn thành **bình thường** nhưng `close()` thất bại, lỗi close thường trở thành primary, chứ không nhất thiết là suppressed.

**Quan trọng:** không viết `finally { resource.close(); }` thêm lần nữa cho một resource đã được TWR quản lý. Đóng lặp không được bảo đảm an toàn cho mọi `AutoCloseable`.

### Chỉ đóng resource **mình sở hữu**

Nếu hàm nhận một `InputStream` **mượn từ caller**, việc viết `try (input)` (Java 9+) sẽ đóng đúng stream của caller, có thể phá vỡ các thao tác sau. TWR tự động đóng theo cú pháp, **không tự biết ai có quyền đóng**. Hãy công bố rõ ownership của phương thức và đặt TWR ở scope chịu trách nhiệm giải phóng resource.

## 13. Không dùng `Files.exists` để “bảo đảm không có FileNotFoundException”

Mã dễ bị hiểu sai:

```java
if (java.nio.file.Files.exists(path)) {
    return java.nio.file.Files.readString(path);
}
return "";
```

Đây là mảnh phương thức minh họa. Nó có vấn đề **time-of-check to time-of-use (TOCTOU)**: file có thể bị xóa, đổi quyền hoặc thay thế **giữa lúc kiểm tra và lúc đọc**. `Files.exists(path)` cũng không phải bằng chứng chắc chắn file có thể mở/đọc.

Bởi vậy:

- Nếu thao tác yêu cầu đọc file, hãy **thực hiện thao tác** và xử lý `IOException` từ chính lời gọi đó.
- Kiểm tra trước có thể hữu ích cho UX hoặc validation, nhưng **không thay thế** việc xử lý lỗi tại thời điểm I/O.
- Không thiết kế retry vô hạn nếu file không tồn tại thật; cần giới hạn và chính sách lỗi.
- Cần phòng rủi ro đường dẫn không đáng tin, symlink/path traversal nếu path nhận từ người dùng.

Ví dụ một pattern đơn giản:

```java
try {
    String text = java.nio.file.Files.readString(path);
    consume(text);
} catch (java.nio.file.NoSuchFileException failure) {
    reportMissingFile();
} catch (java.io.IOException failure) {
    reportIoFailure(failure);
}
```

Đây là **mảnh minh họa**, cần định nghĩa `consume`, `reportMissingFile` và `reportIoFailure`. Phân loại lỗi theo kiểu exception giúp code đáng tin hơn dựa trên việc parse `getMessage()`.

## 14. Đường dẫn, thư mục làm việc và quyền truy cập

Nhiều lỗi `FileNotFoundException` xuất phát từ **đường dẫn tương đối**. Ví dụ `Path.of("config.txt")` không tự tìm file trong thư mục chứa source code; nó được giải theo **working directory** của tiến trình trong hệ thống tệp mặc định.

```java
java.nio.file.Path path =
        java.nio.file.Path.of("config.txt").toAbsolutePath();
System.out.println(path);
```

Đây là **mảnh minh họa**, output phụ thuộc máy nên không ghi một đường dẫn cụ thể. Dùng `toAbsolutePath()` để hỗ trợ chẩn đoán, nhưng không dùng nó để tự tạo một path an toàn nếu đầu vào không đáng tin.

### Phân biệt “có file” với “có quyền đọc/ghi”

Một file có thể tồn tại nhưng thao tác vẫn thất bại:

- Caller thiếu quyền đọc hoặc ghi.
- Resource thuộc một mount có chính sách riêng.
- Path không phải regular file như API mong đợi.
- Hệ thống tệp có lỗi, không còn sẵn sàng, hoặc quota/dung lượng không đủ.
- Khả năng share/lock khác nhau theo platform.
- File được thay đổi bởi tiến trình khác trong khi thao tác đang chạy.

Các phương thức `Files.isReadable`, `isWritable` hoặc `exists` chỉ là **kiểm tra tại một thời điểm**, có thể phụ thuộc provider và quyền truy cập; không phải chứng chỉ bảo đảm thao tác tiếp theo sẽ thành công. Hãy xử lý exception từ operation thực.

### Không gắn cứng một loại lỗi quyền truy cập cho mọi hệ điều hành

`AccessDeniedException` của NIO được dùng khi provider báo cụ thể kiểu lỗi đó, nhưng cùng sự cố quyền truy cập có thể được biểu diễn khác tùy API/provider. Với `FileInputStream` cổ điển, một số tình huống quyền có thể xuất hiện dưới dạng `FileNotFoundException`. Đừng thiết kế logic chỉ hoạt động khi mọi hệ thống đều ném đúng một subtype cho mỗi nguyên nhân.

## 15. Mở file để ghi: ghi đè, nối thêm và tạo mới

Lỗi dữ liệu nghiêm trọng có thể xuất phát không phải từ exception, mà từ **chọn sai chế độ mở file**.

Với `Files.newOutputStream` và `Files.writeString`, các tùy chọn `StandardOpenOption` điều khiển việc tạo và ghi:

- `CREATE`: tạo nếu chưa tồn tại, nếu được phép.
- `CREATE_NEW`: chỉ tạo mới; nếu đã tồn tại, phát sinh lỗi thay vì ghi đè.
- `TRUNCATE_EXISTING`: khi mở để ghi, có thể làm file hiện có rỗng trước khi ghi nội dung mới.
- `APPEND`: ghi thêm vào cuối file theo semantics/provider.
- `WRITE`: mở phục vụ ghi.

Cần đọc hợp đồng riêng của từng API về **mặc định khi không truyền options**. Chẳng hạn `Files.writeString` theo mặc định tạo file nếu chưa có và ghi đè nội dung hiện có (truncation), không phải mặc định append.

### Mảnh minh họa: chỉ tạo file nếu chưa có

```java
java.nio.file.Files.writeString(
        path,
        text,
        java.nio.charset.StandardCharsets.UTF_8,
        java.nio.file.StandardOpenOption.CREATE_NEW,
        java.nio.file.StandardOpenOption.WRITE);
```

Mảnh này giả định có `Path path` và `String text` trong phương thức khai báo/handle `IOException`. Nếu file đã tồn tại, `CREATE_NEW` được thiết kế để **không âm thầm ghi đè**.

### Mảnh minh họa: thêm nội dung

```java
java.nio.file.Files.writeString(
        path,
        additionalText,
        java.nio.charset.StandardCharsets.UTF_8,
        java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.APPEND);
```

Đây là mảnh mã, không phải chương trình độc lập. Phương thức `writeString` vẫn có thể ném `IOException` do mở file, ghi dữ liệu hoặc đóng luồng.

Không cố dùng `catch (FileNotFoundException)` để sửa một thao tác ghi đè nhầm: nếu bạn chọn chế độ mở sai và thao tác thành công thì **không có exception nào cứu dữ liệu cũ**.

## 16. Sao chép stream: lỗi đọc, ghi và đóng đều có thể xuất hiện

Khi sao chép dữ liệu, có ít nhất ba giai đoạn:

1. Mở nguồn và đích.
2. Đọc từ nguồn, ghi vào đích.
3. Đóng/flush tài nguyên và hoàn tất thao tác.

Có thể gặp `IOException` ở **bất cứ giai đoạn nào**. Một số ví dụ:

- Mở source: `NoSuchFileException` hoặc lỗi quyền.
- Mở target: lỗi đường dẫn, quyền hoặc chế độ tạo.
- `read`: nguồn I/O lỗi giữa chừng.
- `write`: thiết bị đích hỏng, hết dung lượng hoặc kết nối ngắt.
- `close`: lỗi cleanup/flush.
- Cleanup file đích nửa chừng: cũng có thể thất bại.

### Chương trình chạy được: sao chép bằng `InputStream.transferTo`

Tạo tệp **`CopyStreamsDemo.java`**:

```java
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class CopyStreamsDemo {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("copy-demo-");
        Path source = directory.resolve("source.txt");
        Path target = directory.resolve("target.txt");

        try {
            Files.writeString(source, "hello", StandardCharsets.UTF_8);

            long copied;
            try (InputStream input = Files.newInputStream(source);
                 OutputStream output = Files.newOutputStream(target)) {
                copied = input.transferTo(output);
            }

            System.out.println("bytes=" + copied);
            System.out.println("target="
                    + Files.readString(target, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(source);
            Files.delete(directory);
        }
    }
}
```

```bash
javac CopyStreamsDemo.java
java CopyStreamsDemo
```

`stdout` khi môi trường cấp tệp tạm bình thường:

```text
bytes=5
target=hello
```

`"hello"` trong UTF-8 chứa 5 byte. `InputStream.transferTo(OutputStream)` có thể ném `IOException` từ việc đọc source hoặc ghi target. TWR đóng cả `output` và `input` theo thứ tự ngược của header. Đọc lại file target là một bước kiểm chứng nội dung, không phải cơ chế transaction.

**Giới hạn:** nếu ghi thất bại giữa chừng, target có thể đã có **một phần dữ liệu**. Xử lý ngoại lệ không tự rollback nội dung đã ghi. Với dữ liệu quan trọng, hãy cân nhắc chiến lược ghi vào file tạm, xác nhận thành công rồi chuyển/rename theo điều kiện hệ thống tệp; không giả định mọi lần move đều atomic trên mọi provider.

## 17. Bọc `IOException` nhưng giữ nguyên nguyên nhân

Ở tầng ứng dụng, bạn có thể muốn chuyển `IOException` thành một ngoại lệ nghiệp vụ có tên/ý nghĩa rõ hơn. Khi đó, **không đánh mất throwable gốc**.

### Mảnh mã đúng

```java
try {
    loadConfiguration();
} catch (java.io.IOException original) {
    throw new IllegalStateException(
            "Application configuration cannot be loaded", original);
}
```

Đây là mảnh minh họa, cần định nghĩa `loadConfiguration()`. `IllegalStateException` là unchecked, nên caller không bị buộc catch/throws ở compile time, nhưng lỗi I/O vẫn **còn trong `getCause()`**.

### Mảnh mã sai về khả năng chẩn đoán

```java
try {
    loadConfiguration();
} catch (java.io.IOException original) {
    throw new IllegalStateException(original.getMessage());
}
```

Đây là mảnh cố ý thể hiện anti-pattern: chỉ sao chép message. Lỗi `IOException` gốc, stack trace và suppressed exceptions của nó không được liên kết vào wrapper.

### Chương trình chạy được: giữ cause

Tạo file **`IOExceptionCauseDemo.java`**:

```java
import java.io.IOException;

public class IOExceptionCauseDemo {
    static class ImportException extends Exception {
        ImportException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static void readData() throws IOException {
        throw new IOException("storage unavailable");
    }

    static void importData() throws ImportException {
        try {
            readData();
        } catch (IOException original) {
            throw new ImportException("import failed", original);
        }
    }

    public static void main(String[] args) {
        try {
            importData();
        } catch (ImportException failure) {
            System.out.println("outer=" + failure.getMessage());
            System.out.println("cause="
                    + failure.getCause().getClass().getSimpleName());
            System.out.println("cause message="
                    + failure.getCause().getMessage());
        }
    }
}
```

```bash
javac IOExceptionCauseDemo.java
java IOExceptionCauseDemo
```

`stdout`:

```text
outer=import failed
cause=IOException
cause message=storage unavailable
```

`super(message, cause)` trong custom exception bảo đảm công cụ logging và caller có thể lần theo `getCause()`. Không cần tạo lại một `IOException` mới chỉ để “giữ message”.

### Khi nào không nên bọc?

Nếu hợp đồng phương thức đã là `throws IOException` và tầng hiện tại **không thêm thông tin hữu ích**, có thể để IOException lan truyền nguyên dạng. Bọc ở mọi lớp mà không có ranh giới trừu tượng khiến chuỗi lỗi dài và khó đọc.

## 18. Retry: không phải `IOException` nào cũng nên thử lại

Một lỗi I/O có thể **tạm thời** hoặc **mang tính cấu hình/dữ liệu cố định**. Không thể chọn chính sách retry chỉ từ việc đối tượng là `IOException`.

Ví dụ:

- Mạng mất kết nối ngắn hạn: một số thao tác **có thể** phục hồi sau retry, nếu request an toàn để thử lại.
- File chưa được tạo do một tiến trình khác đang chuẩn bị: có thể chờ trong giới hạn nếu ứng dụng có hợp đồng đồng bộ rõ ràng.
- File path sai, dữ liệu UTF-8 hỏng, không có quyền truy cập: retry cùng input/cấu hình không thay đổi thường không giải quyết được.
- Ghi dữ liệu thành công một phần rồi exception: retry mù quáng có thể **ghi trùng**, nhất là với append hoặc request không idempotent.
- Đích hết dung lượng: có thể yêu cầu can thiệp, không nên retry liên tục.

Nếu muốn retry, hãy xác định:

1. **Phân loại lỗi** từ ngữ cảnh và subtype khi đáng tin.
2. **Tính idempotent** của thao tác (đọc, ghi đè có điều kiện, append, giao dịch).
3. **Số lần giới hạn**, timeout và backoff phù hợp.
4. **Trạng thái còn lại sau lỗi**, nhất là file/transaction đích đã bị ghi một phần.
5. Khi nào cần trả thông tin lỗi gốc về caller thay vì tiếp tục che lỗi.

Ví dụ không nên thiết kế:

```java
while (true) {
    try {
        writeImportantData();
        break;
    } catch (java.io.IOException ignored) {
        // Không retry vô hạn hoặc nuốt mọi IOException.
    }
}
```

Đây là mảnh mã **cố ý minh họa anti-pattern**. Nó có thể kẹt vô hạn, che lỗi quyền/đĩa và ghi trùng trong một số trường hợp. Chỉ triển khai retry với chính sách rõ ràng và khả năng xác định kết quả thao tác trước đó.

## 19. Phân biệt ngoại lệ của API với các lỗi lập trình khác

Không phải mọi lỗi khi làm việc với đường dẫn, stream và file đều là `IOException`:

- `NullPointerException`: truyền `null` vào một API không chấp nhận.
- `IllegalArgumentException`: truyền tham số không hợp lệ.
- `InvalidPathException`: chuỗi tạo `Path` không hợp lệ, là unchecked exception.
- `SecurityException`: có thể phát sinh nếu bộ kiểm soát truy cập áp hạn chế trong những môi trường liên quan.
- `UnsupportedOperationException`: yêu cầu thao tác không được provider/API hỗ trợ.
- `ClosedChannelException`: một ví dụ **checked IOException** khi truy cập channel đã đóng, khác với những unchecked exception kể trên.

Vì vậy `catch (IOException)` không phải handler cho **mọi sự cố có liên quan file**. Đừng bắt `Exception` quá rộng rồi tuyên bố tất cả đều là lỗi đọc/ghi; nhiều lỗi phản ánh bug hoặc cấu hình sai cần xử lý khác.

### Tên file, đường dẫn và dữ liệu bên ngoài

Khi lấy path từ người dùng:

- Chọn thư mục gốc được phép và chuẩn hóa/kiểm soát path phù hợp với mô hình bảo mật.
- Không tin hoàn toàn vào việc nối chuỗi path thủ công.
- Không nhúng trực tiếp thông tin nhạy cảm trong message hoặc log nếu không cần.
- Dùng charset và format dữ liệu đã thống nhất.
- Không biến một `IOException` thành thông báo “không tìm thấy file” khi chưa xác định nguyên nhân.

## 20. Một ví dụ lớp tiện ích: bắt lỗi có ngữ cảnh, dùng TWR đúng scope

Tạo file **`SafeTextLoaderDemo.java`**:

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

public class SafeTextLoaderDemo {
    static String readFirstLine(BufferedReader reader)
            throws IOException {
        // Reader được mượn từ caller, KHÔNG tự đóng ở đây.
        return reader.readLine();
    }

    static String loadOwnedText(String data) throws IOException {
        try (BufferedReader reader =
                new BufferedReader(new StringReader(data))) {
            return readFirstLine(reader);
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("result="
                    + loadOwnedText("first\nsecond"));
        } catch (IOException failure) {
            System.out.println("IO failure="
                    + failure.getMessage());
        }
    }
}
```

```bash
javac SafeTextLoaderDemo.java
java SafeTextLoaderDemo
```

`stdout`:

```text
result=first
```

Điểm đáng chú ý là **quyền sở hữu tài nguyên**:

- `readFirstLine` nhận `BufferedReader` do caller cung cấp; nó chỉ **mượn** reader để đọc và để caller quyết định khi nào đóng.
- `loadOwnedText` **tự tạo** reader của mình và sử dụng TWR trong scope sở hữu.
- Hai method đều khai báo `throws IOException` để giữ checked exception đúng hợp đồng, dù `StringReader` trong ví dụ không cần hệ thống tệp.

Nếu helper tự dùng `try (reader)` (Java 9+) trên reader **mượn**, nó sẽ đóng resource của caller trước thời điểm caller mong muốn. TWR không tự suy luận quyền sở hữu.

## 21. Bảng xử lý nhanh

| Tình huống | Dấu hiệu/API | Cách xử lý thường phù hợp |
| --- | --- | --- |
| File không tồn tại với `FileInputStream` | `FileNotFoundException` | Kiểm tra cấu hình/path; phân biệt với lỗi quyền |
| File không tồn tại với `Files.readString` | `NoSuchFileException` | Phân loại missing file, không dùng message để đoán |
| File có nhưng không có quyền | `AccessDeniedException` hoặc dạng `IOException` khác | Sửa quyền/cấu hình, không retry vô hạn |
| Đọc `InputStream.read()` được `-1` | EOF bình thường | Kết thúc vòng đọc, không ném lỗi giả |
| `DataInputStream.readInt()` thiếu byte | `EOFException` | Xác nhận dữ liệu có cấu trúc đầy đủ |
| `write`/`flush`/`close` thất bại | `IOException` | Báo thất bại, bảo toàn primary và suppressed |
| Mở file làm text nhưng byte sai charset | `MalformedInputException` | Dùng charset đúng, chính sách decode rõ ràng |
| `CREATE_NEW` mà file đã có | `FileAlreadyExistsException` (NIO) | Quyết định có được phép ghi đè không |
| Ghi một phần rồi mất kết nối | `IOException`, target có thể đã thay đổi | Kiểm tra idempotency trước khi retry |
| Bọc lỗi cho tầng nghiệp vụ | `new DomainException(context, original)` | Giữ cause, đừng chỉ copy message |
| Resource bị đóng trong helper dùng borrowed stream | Lỗi vòng đời có thể xảy ra về sau | Đóng ở scope owner, không ở helper |
| Đọc/ghi từ source không tin cậy | Nhiều lỗi khác nhau | Kiểm tra path, encoding, quyền, lỗi và chính sách bảo mật |

Bảng chỉ nêu các khả năng **thường gặp**. Provider hệ thống tệp hoặc thư viện có thể ném subtype khác; đọc hợp đồng phương thức cụ thể vẫn là điều bắt buộc.

## 22. Sai lầm và nhầm lẫn phổ biến

**“`IOException` là unchecked nên không cần `throws`.”** Sai. Nó là checked exception.

**“`FileNotFoundException` không phải `IOException`.”** Sai. Nó là subtype trực tiếp của `IOException`.

**“Hễ thiếu file thì mọi API Java đều ném `FileNotFoundException`.”** Sai. NIO.2 thường dùng `NoSuchFileException`.

**“`FileNotFoundException` chỉ xuất hiện nếu file thật sự không tồn tại.”** Sai. Mở file có thể thất bại vì quyền, path không hợp lệ cho thao tác hoặc hạn chế nền tảng.

**“Đặt `catch (IOException)` trước `catch (FileNotFoundException)` là tốt hơn.”** Sai. Kiểu con phải trước kiểu cha nếu muốn phân loại riêng.

**“EOF luôn tạo `EOFException`.”** Sai. `InputStream.read()` thường trả `-1`, `BufferedReader.readLine()` trả `null` khi hết dữ liệu bình thường.

**“`DataInputStream.readInt()` thiếu byte thì trả `-1`.”** Sai. Nó ném `EOFException` nếu không đọc đủ bốn byte.

**“Đọc file thành công một lần bảo đảm lần sau không có `IOException`.”** Sai. File có thể bị thay đổi và hệ thống tệp có thể thất bại giữa các lần thao tác.

**“`Files.exists(path)` trả true thì `Files.readString(path)` chắc chắn thành công.”** Sai. Kiểm tra trước không loại bỏ race condition hoặc lỗi quyền.

**“Có thể dựa trên `getMessage()` để phân biệt lỗi quyền với lỗi thiếu file.”** Không an toàn; văn bản message thay đổi theo OS/provider.

**“Không có lỗi ở `write()` nghĩa là ghi đã hoàn tất và bền vững.”** Sai. Lỗi có thể xảy ra khi flush/close; tính bền vững còn cần hợp đồng riêng.

**“`PrintWriter` và `OutputStream` ném checked IOException giống nhau ở mọi phương thức.”** Sai. `PrintWriter` có cách báo lỗi khác, như `checkError()`.

**“Đọc text byte không hợp lệ luôn tự thay bằng ký tự thay thế.”** Không hẳn. Decoder có thể chọn báo `MalformedInputException`.

**“Đã dùng TWR thì lỗi đóng không quan trọng.”** Sai. Nó có thể là lỗi chính hoặc suppressed; dữ liệu vẫn có thể chưa được ghi đúng.

**“TWR trên một tham số stream mượn không đóng stream của caller.”** Sai. Nó đóng chính đối tượng đã đưa vào header, trừ khi một adapter có semantics đặc biệt.

**“Bọc `IOException` bằng `new RuntimeException(original.getMessage())` đã giữ lỗi gốc.”** Sai. Hãy truyền `original` như cause.

**“IOException nào cũng có thể retry ngay nhiều lần.”** Sai. Một số lỗi cố định, một số thao tác có side effect không idempotent.

**“Ghi thất bại sẽ tự rollback file đích.”** Sai. File đích có thể đã bị ghi một phần.

**“`InvalidPathException` là một subclass của `IOException`.”** Sai. Nó là unchecked exception.

**“Để chắc file luôn được đóng, cứ gọi `close()` hai lần.”** Sai. Một số `AutoCloseable` không bảo đảm đóng lặp không có side effect.

## 23. Bài tập tự kiểm tra

1. **`IOException` checked hay unchecked?** Checked.
2. **`FileNotFoundException` có được `catch (IOException)` bắt không?** Có.
3. **`NoSuchFileException` có phải subclass của `FileNotFoundException` không?** Không; cả hai thuộc họ `IOException` qua những nhánh khác nhau.
4. **`FileInputStream` mở file không tồn tại thường ném gì?** `FileNotFoundException`.
5. **`Files.readString` không tìm thấy file thường ném gì?** `NoSuchFileException` trên provider mặc định.
6. **`InputStream.read()` trả `-1` nghĩa là gì?** EOF bình thường.
7. **`BufferedReader.readLine()` trả `null` nghĩa là gì?** Hết dòng, không phải tự động lỗi I/O.
8. **`DataInputStream.readInt()` khi chỉ còn hai byte ném gì?** `EOFException`.
9. **Khi một thao tác ghi đã cập nhật nửa file rồi báo IOException, nên retry mù quáng?** Không; cần đánh giá dữ liệu đích và idempotency.
10. **`try-with-resources` đóng reader khi `readLine()` ném lỗi không?** Có, theo ngữ nghĩa TWR khi resource đã khởi tạo thành công.
11. **`close()` cũng ném trong khi phần thân đã ném: xem lỗi cleanup ở đâu?** Thường trong `getSuppressed()` của primary.
12. **Nếu chỉ `close()` lỗi mà phần thân thành công, lỗi close có luôn là suppressed không?** Không; nó có thể là primary.
13. **Có thể tìm nguyên nhân `IOException` chỉ bằng `getMessage()` và bỏ stack trace?** Không nên; cần xem Throwable và cause/suppressed khi chẩn đoán.
14. **Bọc lỗi từ tầng dưới bằng `new ImportException("...", original)` có tác dụng gì?** Thêm ngữ cảnh và giữ original làm cause.
15. **`Files.exists` có thay thế `catch (IOException)` không?** Không.
16. **Chọn sai chế độ `TRUNCATE_EXISTING` nhưng thao tác thành công có phát sinh IOException để cứu nội dung cũ không?** Không.
17. **Tại sao cần chỉ định UTF-8 khi đọc/ghi văn bản?** Để khớp mã hóa dữ liệu, tránh giả định charset không rõ ràng.
18. **Lỗi byte không hợp lệ với UTF-8 có thể gây ngoại lệ loại gì?** `MalformedInputException` nếu decoder được cấu hình báo lỗi.
19. **Nếu helper nhận input stream từ caller nhưng caller còn cần dùng, helper có nên `try (stream)` không?** Không, nếu ownership vẫn ở caller.
20. **Khi NIO báo AccessDeniedException, có nên luôn tạo file mới để sửa?** Không; phải giải quyết quyền/truy cập theo hợp đồng.
21. **`PrintWriter.println` có nhất thiết ném IOException trực tiếp giống `OutputStream.write` không?** Không; đọc hợp đồng `PrintWriter` và kiểm tra trạng thái lỗi khi cần.
22. **`IOException` có thể xuất hiện trong `close()` mà không phải ở `read()`/`write()` không?** Có.
23. **Lỗi từ `Files.createTempFile` có thể xảy ra trước khi vào `try` đóng reader không?** Có; phải xét chính xác thời điểm resource được tạo và scope chịu trách nhiệm.
24. **Vì sao `catch (Exception)` chung không phải lựa chọn mặc định tốt cho mọi lỗi I/O?** Nó gom cả các loại lỗi không liên quan, có thể che bug và làm mất chính sách phục hồi theo từng loại.

## 24. Tài liệu Java 17 chính thức

- [Java SE 17 — IOException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/IOException.html): checked exception chung của lỗi I/O.
- [Java SE 17 — FileNotFoundException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/FileNotFoundException.html): lỗi mở file của API cổ điển.
- [Java SE 17 — Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html): các thao tác NIO.2 đọc, ghi, tạo, xóa và sao chép.
- [Java SE 17 — NoSuchFileException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/NoSuchFileException.html): kiểu thông báo tệp không tồn tại trong NIO.
- [Java SE 17 — AccessDeniedException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/AccessDeniedException.html): lỗi quyền truy cập tệp.
- [Java SE 17 — FileSystemException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileSystemException.html): file/reason của lỗi hệ thống tệp.
- [Java SE 17 — InputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html): `read()`, EOF bằng `-1` và `transferTo`.
- [Java SE 17 — BufferedReader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedReader.html): `readLine()` và kết quả `null` ở EOF.
- [Java SE 17 — DataInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataInputStream.html): `readInt()`, `readFully()` và `EOFException`.
- [Java SE 17 — OutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/OutputStream.html): `write`, `flush`, `close`.
- [Java SE 17 — PrintWriter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/PrintWriter.html) và [PrintStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/PrintStream.html): `checkError()` và contract ngoại lệ.
- [Java SE 17 — MalformedInputException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/MalformedInputException.html): dữ liệu byte sai charset.
- [Java Language Specification 17 — `11 Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html): checked/unchecked exception và các quy tắc catch/throws.
- [Java Language Specification 17 — `14.20.3 Try-with-resources](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3): đóng tài nguyên tự động và suppressed exception.

**Ghi nhớ:** `IOException` là **checked exception** cho những thất bại I/O. `FileNotFoundException` là một subtype thường gặp khi **mở file bằng API cổ điển**, còn NIO.2 có thể trả `NoSuchFileException` hoặc `AccessDeniedException` cụ thể hơn. Đừng nhầm **EOF bình thường** (`read() == -1`, `readLine() == null`) với **lỗi I/O**, và đừng coi mở file thành công là bảo đảm đọc/ghi/đóng sẽ thành công. Hãy chọn API/charset/chế độ mở phù hợp, dùng `try-with-resources` khi sở hữu tài nguyên, giữ cause/suppressed để chẩn đoán, và xây chiến lược retry/cleanup dựa trên hợp đồng thực tế.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="api-exceptions-002">NoSuchFileException, AccessDeniedException và các lỗi NIO</a>

<details>
<summary>Xem chi tiết</summary>

# `NoSuchFileException`, `AccessDeniedException` và các lỗi NIO trong Java

Khi làm việc với file, thư mục hoặc hệ thống tệp bằng API `java.nio.file`, một lệnh như `Files.readString(path)`, `Files.copy(source, target)` hay `Files.move(source, target)` có thể thất bại vì **đường dẫn không tồn tại**, **quyền truy cập bị từ chối**, **đích đã tồn tại**, **thư mục chưa rỗng**, **không hỗ trợ di chuyển nguyên tử**, hoặc nhiều điều kiện khác.

Java NIO.2 (có từ Java 7, được mở rộng ở các phiên bản sau) cung cấp những lớp ngoại lệ cụ thể để phân biệt tình huống. Hai lớp đáng chú ý nhất là:

- **`NoSuchFileException`**: thao tác cố truy cập một file hoặc đường dẫn không tồn tại trong điều kiện hệ thống tệp đang xét.
- **`AccessDeniedException`**: thao tác bị từ chối truy cập đến file hoặc đường dẫn trong ngữ cảnh của hệ thống tệp.

Cả hai đều là **checked exceptions**, kế thừa `FileSystemException` → `IOException`. Có thể bắt riêng từng loại để triển khai chính sách phù hợp, nhưng **không được mặc định rằng mọi hệ điều hành và mọi file-system provider đều luôn phát ra cùng một subclass cho cùng một lỗi cấp thấp**.

**Nguyên tắc chính:** chọn handler theo *loại ngoại lệ và ngữ nghĩa thao tác*, xem thông tin path/reason có cấu trúc, giữ `cause` khi chuyển lỗi và luôn có phương án cho các `IOException` chưa phân loại.

## 1. API NIO.2 và các lớp nền tảng

Trong Java, các API file thường dùng nằm ở:

| Kiểu/API | Package | Vai trò |
| --- | --- | --- |
| `Path` | `java.nio.file` | Biểu diễn đường dẫn trong hệ thống file; không tự chứng minh file tồn tại |
| `Files` | `java.nio.file` | Thao tác mở/đọc/ghi/copy/move/delete, thuộc tính và duyệt cây thư mục |
| `FileSystem`, `FileSystems` | `java.nio.file` | Trừu tượng file-system provider và các file system đang dùng |
| `OpenOption`, `StandardOpenOption` | `java.nio.file` | Quy định cách mở/tạo/ghi file |
| `CopyOption`, `StandardCopyOption` | `java.nio.file` | Điều khiển sao chép hoặc di chuyển |
| `LinkOption` | `java.nio.file` | Quy định có theo symbolic link ở những API hỗ trợ hay không |
| `BasicFileAttributes` | `java.nio.file.attribute` | Thông tin thuộc tính file |
| `IOException` | `java.io` | Lớp checked chung cho nhiều thất bại I/O |
| `FileSystemException` | `java.nio.file` | Checked exception gắn với file, file khác và lý do lỗi |
| `NoSuchFileException`, `AccessDeniedException` | `java.nio.file` | Hai phân loại file-system error cụ thể |

`Path.of("data", "input.csv")` **chỉ tạo đối tượng biểu diễn đường dẫn**. Nó không đọc đĩa để xác minh path thật sự tồn tại. Vì vậy, lỗi “không tìm thấy file” thường xuất hiện lúc **thực hiện thao tác I/O**, không phải chỉ vì đã tạo `Path`.

Ví dụ:

```java
Path path = Path.of("data", "input.csv");
String content = Files.readString(path);
```

Đây là **đoạn minh họa** cần import `java.nio.file.Path`, `java.nio.file.Files` và xử lý checked `IOException`. Lệnh `readString` có thể ném lỗi vì path thiếu, quyền không cho phép, file không phù hợp thao tác hoặc lỗi I/O khác.

## 2. Quan hệ kế thừa của các ngoại lệ NIO

Trích đoạn phân cấp thường gặp:

```text
java.lang.Throwable
└── java.lang.Exception
    └── java.io.IOException
        ├── java.io.FileNotFoundException
        └── java.nio.file.FileSystemException
            ├── NoSuchFileException
            ├── AccessDeniedException
            ├── FileAlreadyExistsException
            ├── DirectoryNotEmptyException
            ├── NotDirectoryException
            ├── FileSystemLoopException
            └── AtomicMoveNotSupportedException
```

Đây là **sơ đồ rút gọn**, không liệt kê mọi subtype, không có nghĩa tất cả lỗi NIO đều nằm dưới `FileSystemException`.

Mỗi lớp trong nhánh `FileSystemException` nêu một tình huống khác nhau. Vì tất cả đều thuộc `IOException`, một `catch (IOException ex)` có thể bắt chúng, nhưng dùng chỉ một handler chung sẽ bỏ lỡ cơ hội phục hồi có chủ đích.

Ngoài checked exception, một số thao tác NIO có thể ném **unchecked exceptions** như `InvalidPathException`, `ClosedFileSystemException`, `FileSystemNotFoundException`, `ProviderNotFoundException`, `UnsupportedOperationException` hoặc `SecurityException` trong những điều kiện và môi trường tương ứng. Không được coi `catch (IOException)` là “bắt mọi lỗi của NIO”.

### Vì sao `FileNotFoundException` và `NoSuchFileException` khác nhau?

`java.io.FileNotFoundException` là một checked subtype của `IOException` thường gặp từ các API I/O truyền thống như `FileInputStream`. `java.nio.file.NoSuchFileException` là subtype của **`FileSystemException`**, hướng tới lỗi file-system trong NIO. **Hai lớp không phải cha/con trực tiếp của nhau**.

Không nên luôn chuyển từ `FileInputStream` sang `Files.newInputStream` rồi giả định loại ngoại lệ chính xác không thay đổi. Caller cần đọc hợp đồng API cụ thể và giữ một handler `IOException` phù hợp.

## 3. `NoSuchFileException`: file hoặc một thành phần đường dẫn không tồn tại

`NoSuchFileException` biểu diễn việc thao tác thất bại vì file/đường dẫn được yêu cầu không tồn tại trong tình huống hiện tại.

Ví dụ điển hình:

```java
Path path = Path.of("data", "never-created.txt");
String text = Files.readString(path);
```

Nếu path thực sự không tồn tại, file-system provider mặc định thường ném `NoSuchFileException`. Tuy nhiên, đừng dùng ví dụ này để khẳng định **mọi provider** phải luôn cung cấp cùng subtype trong mọi hoàn cảnh; lỗi I/O cụ thể có thể phụ thuộc provider và trạng thái hệ thống.

Các nguyên nhân phổ biến:

- Sai tên file, đường dẫn tương đối được hiểu khác dự kiến, sai chữ hoa/thường trên file system phân biệt case.
- File từng tồn tại nhưng đã bị xóa hoặc di chuyển giữa lúc kiểm tra và lúc mở.
- Một thư mục cha không tồn tại.
- Một symbolic link trỏ đến đích đã bị xóa, tùy cách thao tác theo link.
- Đường dẫn được tạo từ một cấu hình không đúng, hoặc process đang chạy ở working directory khác dự kiến.

### Đường dẫn tương đối và working directory

`Path.of("config", "app.properties")` là đường dẫn tương đối. Nó được giải nghĩa trong ngữ cảnh file system và working directory của process khi API thực hiện thao tác.

Không nên suy đoán rằng nó luôn trỏ đến thư mục chứa tệp `.java` hay thư mục của IDE project. Với một chương trình đóng gói và chạy như service, working directory có thể khác đáng kể.

Có thể hỗ trợ chẩn đoán bằng:

```java
Path requested = Path.of("config", "app.properties");
System.out.println(requested.toAbsolutePath().normalize());
```

Đây là **mã minh họa**; `toAbsolutePath()` không có nghĩa xác thực file tồn tại. Nó giúp hiển thị đường dẫn được suy ra, không thay thế quyền truy cập hoặc phép mở.

### Khi file thiếu là kết quả bình thường

Không phải mọi trường hợp thiếu file đều là sự cố. Ví dụ một cấu hình tùy chọn chưa có thể được xử lý bằng default được xác định rõ. Khi đó có thể bắt **riêng `NoSuchFileException`** và trả một kết quả mô tả “không có cấu hình tùy chọn”.

Ngược lại, với file dữ liệu bắt buộc, nên báo rõ thất bại và không coi missing như dữ liệu rỗng thành công giả.

## 4. `AccessDeniedException`: bị từ chối quyền hoặc truy cập

`AccessDeniedException` thường xuất hiện khi file-system provider từ chối một thao tác truy cập vào file hoặc một thành phần đường dẫn.

Ví dụ:

```java
Path path = Path.of("private", "credentials.txt");
String text = Files.readString(path);
```

Đây là **đoạn minh họa**, không đảm bảo sẽ ném `AccessDeniedException` trên máy đang chạy. Quyền thực tế phụ thuộc tài khoản người dùng, ACL, quyền thư mục cha, mount, cơ chế bảo mật, file-system provider và môi trường.

Những tình huống thường gặp:

- User/process không được phép đọc file.
- Thư mục cha không cho phép đi qua/duyệt theo quy tắc quyền của hệ điều hành.
- Process không được phép ghi hoặc xóa file theo ACL/chính sách của hệ thống.
- Một số thao tác bị từ chối bởi provider hoặc chính sách truy cập nơi lưu trữ.

### Không trộn lẫn với `SecurityException`

`AccessDeniedException` là **checked `IOException`**, còn `SecurityException` là **unchecked `RuntimeException`**. `SecurityException` có thể liên quan kiểm tra bảo mật cấp Java, chẳng hạn trong môi trường có cơ chế security manager hỗ trợ; nó không phải tên đồng nghĩa với mọi lỗi quyền của hệ điều hành.

Một API có thể báo lỗi quyền bằng một `IOException` khác hoặc bằng exception cụ thể tùy provider. Vì vậy:

- `catch (AccessDeniedException ex)` chỉ bắt chính subclass này.
- `catch (IOException ex)` còn bắt các lỗi I/O checked khác.
- `catch (IOException ex)` **không** bắt `SecurityException`.

Trong mã thông thường không nên `catch (Throwable)` chỉ để ép mọi lỗi quyền và lỗi lập trình thành một trạng thái “không có file”.

### Tại sao tự đổi quyền file trong demo không đảm bảo gây lỗi?

Một bài học có thể thử `Files.setPosixFilePermissions` trên POSIX, nhưng API đó có thể không được hỗ trợ trên Windows hoặc một provider khác. Kể cả thay quyền thành chỉ đọc, process có quyền cao, ACL đặc biệt hoặc policy của hệ thống vẫn có thể làm kết quả khác dự kiến.

Do đó, các ví dụ bên dưới sẽ **không phụ thuộc vào việc cưỡng ép lỗi quyền thật trên máy học**. Thay vào đó, một chương trình dựng các đối tượng ngoại lệ để học cách phân loại, và một chương trình thao tác file thực trong thư mục tạm để học API.

## 5. `FileSystemException` giữ thông tin lỗi có cấu trúc nào?

`FileSystemException` cung cấp:

| Phương thức | Kiểu trả về | Ý nghĩa |
| --- | --- | --- |
| `getFile()` | `String` | File/path chính có liên quan đến lỗi, có thể `null` |
| `getOtherFile()` | `String` | File/path thứ hai trong thao tác như copy/move, có thể `null` |
| `getReason()` | `String` | Lý do bổ sung, có thể `null` |
| `getMessage()` | `String` | Chuỗi chẩn đoán do exception cung cấp |
| `getCause()` | `Throwable` | Nguyên nhân nếu được gắn, có thể `null` |

Cách dùng:

```java
catch (java.nio.file.FileSystemException ex) {
    String file = ex.getFile();
    String other = ex.getOtherFile();
    String reason = ex.getReason();
    // Ghi log nội bộ an toàn hoặc ánh xạ mã lỗi.
}
```

Đây là **mảnh handler minh họa**, cần nằm trong cấu trúc `try` thích hợp.

**Điểm tinh tế:** `getFile()` và `getOtherFile()` trả **chuỗi**, không trả `Path`. Không nên tin rằng chuỗi đó luôn tương đương một path đã được chuẩn hóa/canonical hóa hoặc luôn an toàn để gửi cho client.

### Cần phân biệt dữ liệu và message

Với một `AccessDeniedException`, ứng dụng có thể ghi nhận:

- Loại: `AccessDeniedException`.
- File chính: `getFile()`.
- File thứ hai: `getOtherFile()` nếu có.
- Lý do: `getReason()` nếu provider cung cấp.

Đừng phân nhánh chỉ bằng `ex.getMessage().contains("Permission denied")`. Câu chữ/locale khác nhau, `reason` có thể không tồn tại, và message có thể lộ đường dẫn.

Trong API công khai nên ánh xạ **loại lỗi → mã lỗi do ứng dụng định nghĩa**, rồi tạo thông điệp an toàn theo nhu cầu người dùng. Thông tin path đầy đủ chỉ nên đi vào hệ thống log có quyền truy cập thích hợp.

## 6. Các lỗi NIO khác cần phân biệt

| Ngoại lệ | Trường hợp thường gặp | Hướng xử lý tùy ngữ cảnh |
| --- | --- | --- |
| `NoSuchFileException` | File/thành phần path được yêu cầu không tồn tại | Kiểm tra path, missing là optional hay bắt buộc |
| `AccessDeniedException` | Truy cập bị từ chối | Kiểm tra quyền process/ACL/provider; không mặc định retry |
| `FileAlreadyExistsException` | Tạo file/dir mới hoặc copy vào đích đã tồn tại | Chọn tên khác, từ chối ghi đè, hoặc xử lý overwrite theo hợp đồng |
| `DirectoryNotEmptyException` | Xóa thư mục khi còn phần tử | Không tự xóa đệ quy nếu chưa được phép |
| `NotDirectoryException` | Một thành phần path được mong đợi là thư mục nhưng không phải | Kiểm tra cấu trúc path và dữ liệu đầu vào |
| `FileSystemLoopException` | Gặp vòng lặp khi duyệt theo symbolic links | Kiểm tra link policy và cách walk tree |
| `AtomicMoveNotSupportedException` | Yêu cầu `ATOMIC_MOVE` nhưng provider không hỗ trợ | Chọn thất bại an toàn hoặc fallback không nguyên tử có chủ đích |
| `IOException` khác | Lỗi thiết bị, hỏng dữ liệu, mạng, quota, provider... | Không suy diễn là missing hoặc denied |

**Chú ý:** phân loại này là **các trường hợp thường gặp**, không đảm bảo duy nhất một ngoại lệ cho mỗi sự cố vật lý. Một lỗi disk, mount hoặc provider có thể xuất hiện dưới loại rộng hơn.

### `FileAlreadyExistsException` không tự động có nghĩa nên ghi đè

Ví dụ `Files.createFile(target)` cố tạo file mới, nhưng file đã tồn tại. Đây có thể là **tín hiệu hợp đồng đúng**: không được làm mất dữ liệu cũ. Không nên tự đổi thành `Files.writeString(target, ...)` theo tùy chọn mặc định vì thao tác ghi có thể **truncate** dữ liệu hiện có.

Nếu muốn tạo file mới **chỉ khi chưa tồn tại**, ưu tiên thao tác hỗ trợ `CREATE_NEW` hoặc `Files.createFile` thay vì làm `if (!Files.exists(path))` rồi mới tạo.

### `DirectoryNotEmptyException` không cho phép tự dọn cả cây

Một handler kiểu:

```java
catch (DirectoryNotEmptyException ex) {
    // ĐỪNG tự động xóa mọi file con nếu hợp đồng không cho phép.
}
```

nên xem đây là tín hiệu cần chính sách rõ: từ chối xóa, yêu cầu xác nhận, di chuyển sang vùng lưu trữ, hoặc thực hiện xóa đệ quy chỉ khi được thiết kế và cấp quyền.

Lệnh `Files.delete(path)` không mặc định xóa cả cây thư mục.

## 7. Ví dụ Java 17+ hoàn chỉnh: phân loại exception không phụ thuộc OS

Để kiểm chứng các loại lỗi và thông tin `FileSystemException` với **stdout ổn định**, chương trình dưới đây chủ động **tạo các đối tượng exception** chứ không giả định rằng hệ điều hành sẽ từ chối quyền theo cách nhất định.

Lưu tệp **`NioExceptionCatalogDemo.java`**:

```java
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;

public class NioExceptionCatalogDemo {

    static String classify(IOException error) {
        if (error instanceof NoSuchFileException) {
            return "MISSING";
        }
        if (error instanceof AccessDeniedException) {
            return "DENIED";
        }
        if (error instanceof FileAlreadyExistsException) {
            return "ALREADY_EXISTS";
        }
        if (error instanceof DirectoryNotEmptyException) {
            return "NOT_EMPTY";
        }
        if (error instanceof NotDirectoryException) {
            return "NOT_DIRECTORY";
        }
        if (error instanceof FileSystemLoopException) {
            return "LINK_LOOP";
        }
        if (error instanceof AtomicMoveNotSupportedException) {
            return "NO_ATOMIC_MOVE";
        }
        return "IO_OTHER";
    }

    static String display(String value) {
        return value == null ? "-" : value;
    }

    static void show(IOException error) {
        System.out.println(
                classify(error)
                + " | " + error.getClass().getSimpleName());

        if (error instanceof FileSystemException fs) {
            System.out.println(
                    "file=" + display(fs.getFile())
                    + "; other=" + display(fs.getOtherFile())
                    + "; reason=" + display(fs.getReason()));
        }
    }

    public static void main(String[] args) {
        show(new NoSuchFileException("data/missing.csv"));

        show(new AccessDeniedException(
                "private/report.txt", null, "permission denied"));

        show(new FileAlreadyExistsException("output/result.csv"));

        show(new DirectoryNotEmptyException("archive/old"));

        show(new NotDirectoryException("data/items"));

        show(new FileSystemLoopException("links/current"));

        show(new AtomicMoveNotSupportedException(
                "diskA/input.csv",
                "diskB/input.csv",
                "atomic move unsupported"));

        show(new IOException("general I/O problem"));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 NioExceptionCatalogDemo.java
java NioExceptionCatalogDemo
```

**Stdout chính xác:**

```text
MISSING | NoSuchFileException
file=data/missing.csv; other=-; reason=-
DENIED | AccessDeniedException
file=private/report.txt; other=-; reason=permission denied
ALREADY_EXISTS | FileAlreadyExistsException
file=output/result.csv; other=-; reason=-
NOT_EMPTY | DirectoryNotEmptyException
file=archive/old; other=-; reason=-
NOT_DIRECTORY | NotDirectoryException
file=data/items; other=-; reason=-
LINK_LOOP | FileSystemLoopException
file=links/current; other=-; reason=-
NO_ATOMIC_MOVE | AtomicMoveNotSupportedException
file=diskA/input.csv; other=diskB/input.csv; reason=atomic move unsupported
IO_OTHER | IOException
```

### Vì sao đây là demo phân loại đáng tin?

- Nó không phụ thuộc tài khoản hiện hành có quyền root/administrator hay không.
- Nó không cần tạo file rồi thay quyền theo đặc thù Linux/Windows.
- Mỗi constructor cung cấp dữ liệu file/reason cụ thể; những trường không được truyền có thể là `null` và được in thành dấu `-`.
- Handler nhận kiểu chung `IOException` nhưng phân nhánh bằng `instanceof` của exception, **không parse text message**.
- Ví dụ cho thấy `FileSystemException` chứa thông tin đường dẫn phụ và lý do, **không chỉ message một dòng**.

**Giới hạn cần hiểu:** chương trình tạo exception để học cách phân loại; nó **không chứng minh** một thao tác file thực trong mọi provider sẽ ném đúng subclass giống tên ở mỗi dòng.

## 8. Bắt ngoại lệ theo thứ tự đúng

Khi xử lý NIO, các subclass nên xuất hiện **trước** handler cho superclass:

```java
try {
    String text = Files.readString(path);
    useText(text);

} catch (NoSuchFileException ex) {
    handleMissing(ex);

} catch (AccessDeniedException ex) {
    handleDenied(ex);

} catch (IOException ex) {
    handleOtherIo(ex);
}
```

Đây là **khung minh họa**, với import và các phương thức `useText`/`handleMissing`/`handleDenied`/`handleOtherIo` hợp lệ. Cách viết này cho phép chính sách riêng từng lỗi, sau đó xử lý fallback cho mọi `IOException` khác.

### Vì sao không bắt `IOException` trước?

Đoạn sau không hợp lệ:

```java
// Cố ý KHÔNG BIÊN DỊCH.
try {
    Files.readString(path);
} catch (IOException ex) {
    handleOtherIo(ex);
} catch (NoSuchFileException ex) {
    handleMissing(ex);
}
```

Vì `NoSuchFileException` là subclass của `IOException`, nhánh `catch (NoSuchFileException)` phía sau **không thể tới được**: nhánh cha trước đó đã bắt tất cả những đối tượng thuộc subclass này.

### Có thể dùng multi-catch không?

Nếu hai lỗi có cùng chính sách:

```java
catch (NoSuchFileException | AccessDeniedException ex) {
    reportUnavailable(ex);
}
```

Đây là handler minh họa. `NoSuchFileException` và `AccessDeniedException` là hai sibling trong cây kiểu nên multi-catch hợp lệ.

Tuy vậy, **đừng gộp chỉ vì chúng đều thất bại**. Nếu thiếu file là optional còn bị từ chối quyền là lỗi vận hành cần báo động, chúng **phải có hai chính sách khác nhau**.

## 9. Ví dụ Java 17+ hoàn chỉnh: thao tác NIO thật trong thư mục tạm

Ví dụ tiếp theo thực hiện tạo/đọc/copy file, sau đó mô phỏng các trường hợp đích đã có và file thiếu bằng thao tác NIO thật. Không dùng tên file cố định trong project để tránh ghi đè dữ liệu của người học.

Lưu tệp **`NioFileWorkflowDemo.java`**:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class NioFileWorkflowDemo {

    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("nio-lesson-");
        Path source = directory.resolve("source.txt");
        Path target = directory.resolve("copy.txt");
        Path missing = directory.resolve("missing.txt");

        try {
            Files.writeString(
                    source,
                    "alpha",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);

            System.out.println(
                    "source="
                    + Files.readString(source, StandardCharsets.UTF_8));

            Files.copy(source, target);

            System.out.println(
                    "copy="
                    + Files.readString(target, StandardCharsets.UTF_8));

            try {
                Files.copy(source, target);
                System.out.println("unexpected overwrite");
            } catch (FileAlreadyExistsException ex) {
                System.out.println("copy=ALREADY_EXISTS");
            }

            try {
                Files.readString(missing, StandardCharsets.UTF_8);
                System.out.println("unexpected missing read");
            } catch (NoSuchFileException ex) {
                System.out.println("read=MISSING");
            }

        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(source);
            Files.deleteIfExists(missing);
            Files.deleteIfExists(directory);
        }
    }
}
```

Chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 NioFileWorkflowDemo.java
java NioFileWorkflowDemo
```

**Stdout trên file system mặc định thông thường**, khi tạo/xóa file được cấp quyền và provider trả subclass cụ thể như trong ví dụ:

```text
source=alpha
copy=alpha
copy=ALREADY_EXISTS
read=MISSING
```

**Không khẳng định stdout này tuyệt đối trong mọi môi trường.** Chương trình dùng API file thực; quyền, provider, bộ lọc antivirus, tình trạng ổ đĩa và race với tiến trình khác có thể gây `IOException` khác, hoặc cách phân loại không giống file system thông thường. Trong trường hợp đó chương trình để ngoại lệ chưa xử lý lên caller/JVM (qua `main throws IOException`) thay vì che bằng giá trị thành công giả.

### Vì sao dùng `CREATE_NEW` ở file nguồn?

`Files.writeString` khi không khai báo option thường dùng hành vi tạo nếu chưa có và truncate file hiện có. Ở đây dùng `StandardOpenOption.CREATE_NEW` để **chỉ tạo file mới**, tránh vô tình ghi đè.

Với `CREATE_NEW`, kiểm tra tồn tại và tạo file được thực hiện như một thao tác nguyên tử đối với việc tạo mới theo hợp đồng API, thay vì `if (!Files.exists(...))` rồi `Files.writeString(...)` có cửa sổ race.

### Vì sao không dùng `REPLACE_EXISTING` khi copy lần hai?

`Files.copy(source, target)` theo mặc định không tự động thay thế đích hiện hữu trong trường hợp thông thường. Lần copy thứ hai nhằm minh họa rằng **“đích đã tồn tại” là một loại thất bại có ý nghĩa**, không phải lệnh nên tự overwrite ngay.

`StandardCopyOption.REPLACE_EXISTING` có thể được dùng khi hợp đồng **chủ động cho phép thay thế**, sau khi xét an toàn dữ liệu. Không nên tự thêm option này chỉ để khỏi thấy `FileAlreadyExistsException`.

### Vì sao dùng `finally` để dọn thư mục tạm?

Ví dụ mở/ghi nhiều file rồi cố ý gây ngoại lệ được bắt. `finally` thử xóa các file và thư mục đã tạo dù có lỗi trong thân. Tuy nhiên, **xóa cũng có thể ném `IOException`**, và một ngoại lệ phát sinh trong `finally` có thể che một lỗi từ thân `try`. Với tài nguyên `AutoCloseable` như stream/channel, thường nên dùng `try-with-resources`; với xóa tệp trong ví dụ ngắn, `finally` cho thấy mục đích cleanup, không phải công thức rollback tổng quát.

Trong production, nếu cleanup có thể thất bại đồng thời với thao tác chính, cần một chiến lược giữ được cả hai lỗi, ví dụ lưu ngoại lệ chính và thêm lỗi phụ bằng `addSuppressed` khi phù hợp.

## 10. Thiếu file có thể xử lý bằng kết quả tùy chọn; lỗi quyền thì không nên

Một file cấu hình **không bắt buộc** có thể có hợp đồng:

- Có file và đọc được: trả nội dung.
- Không có file: trả kết quả “vắng mặt”, caller dùng default.
- Bị từ chối truy cập hoặc lỗi I/O khác: **báo lỗi**, không giả làm “vắng mặt”.

Ví dụ Java 17+ dưới dạng phương thức:

```java
static java.util.Optional<String> readOptionalConfig(
        java.nio.file.Path path) throws java.io.IOException {

    try {
        return java.util.Optional.of(
                java.nio.file.Files.readString(path));

    } catch (java.nio.file.NoSuchFileException missing) {
        return java.util.Optional.empty();
    }
}
```

Đây là **phương thức minh họa chạy được khi đặt trong một lớp**. Nó chỉ chuyển `NoSuchFileException` sang `Optional.empty()`. Nếu `AccessDeniedException` hoặc một `IOException` khác xuất hiện, phương thức **vẫn ném ra** cho caller xử lý.

**Lưu ý hợp đồng:** `Optional.empty()` ở đây thể hiện thiếu file theo chính sách của chính API này, không khẳng định path luôn mất trong mọi thời điểm tiếp theo. Một thread/process khác có thể tạo file ngay sau khi thao tác thất bại.

### Trường hợp file là bắt buộc

Với file import bắt buộc:

```java
static String readRequired(
        java.nio.file.Path path) throws java.io.IOException {

    return java.nio.file.Files.readString(path);
}
```

Không cần bắt `NoSuchFileException` chỉ để ném lại nếu không bổ sung ngữ cảnh. Nếu tầng service phải chuyển từ lỗi filesystem sang lỗi nghiệp vụ, hãy bọc **đúng ngữ nghĩa và giữ cause**:

```java
try {
    return Files.readString(path);
} catch (NoSuchFileException cause) {
    throw new RequiredInputMissingException(
            "Required import source is missing", cause);
}
```

Đoạn này là **khung minh họa** với một custom exception `RequiredInputMissingException` do ứng dụng định nghĩa, có constructor nhận `Throwable cause`. Không dùng `new RuntimeException(cause.getMessage())` nếu muốn giữ stack/cause của lỗi gốc.

## 11. Đừng dùng `Files.exists` làm bằng chứng đủ cho thao tác kế tiếp

Một mẫu code phổ biến:

```java
if (Files.exists(path)) {
    return Files.readString(path);
}
return "";
```

Đoạn này có hai vấn đề:

1. **Time-of-check-to-time-of-use (TOCTOU):** file có thể bị xóa/thay thế hoặc quyền có thể thay đổi giữa `exists` và `readString`.
2. **`Files.exists` không chứng minh đọc được file:** path có thể có nhưng bị từ chối quyền, là thư mục hoặc file-system provider bị lỗi.

Ngay cả khi `Files.exists(path)` trả `false`, điều đó **không luôn chứng minh file không tồn tại**; một số trường hợp trạng thái tồn tại không xác định được. Theo hợp đồng của `Files.exists`/`Files.notExists`, có thể cả hai đều trả `false` nếu không xác định được một cách chắc chắn.

Vì vậy, để đọc file theo chính sách:

- **Thực hiện thao tác `Files.readString` hoặc mở file trực tiếp**.
- Bắt những subclass ngoại lệ mà caller có chính sách rõ.
- Để `IOException` khác tiếp tục đi lên hoặc được chuyển có cause.
- Không dựa vào chuỗi kiểm tra trước làm thay thế cho việc xử lý lỗi thực tế.

### `Files.isReadable` cũng không phải một giấy phép vĩnh viễn

`Files.isReadable(path)` có thể hữu ích cho UI hoặc kiểm tra sơ bộ, nhưng một kết quả `true` không bảo đảm lần mở sau sẽ thành công. Trạng thái quyền và hệ thống có thể thay đổi; một số provider có cách biểu diễn quyền khác nhau.

Không viết:

```java
// Chống mẫu: giả định isReadable loại bỏ mọi IOException.
if (Files.isReadable(path)) {
    return Files.readString(path);
}
```

mà không có hợp đồng xử lý lỗi của thao tác đọc.

## 12. Path normalization, symbolic link và rủi ro bảo mật

`Path.normalize()` loại bỏ những thành phần thừa như `.` và `..` bằng **quy tắc cú pháp**; nó không nhất thiết truy cập file system hay phân giải symbolic link.

`Path.toRealPath(...)` có thể yêu cầu file thực tồn tại để phân giải path, liên quan tới I/O và có thể ném `IOException`. Việc kiểm tra path có nằm trong một thư mục cho phép hay không chỉ bằng `normalize` và `startsWith` có thể không đủ trong môi trường có symbolic link hoặc đối tượng bị thay đổi đồng thời.

Ví dụ:

```java
Path root = Path.of("uploads");
Path requested = root.resolve(userSuppliedName).normalize();
```

Đây là khung minh họa; `userSuppliedName` đến từ nguồn không đáng tin. Kiểm tra lexical path là **một bước**, không phải toàn bộ giải pháp sandbox:

- Chuỗi `..` có thể khiến path rời khỏi thư mục dự kiến.
- Symbolic link có thể trỏ tới vị trí ngoài root.
- Một thread/process khác có thể thay đổi entry giữa bước kiểm tra và bước mở.
- Quyền truy cập ở hệ điều hành hoặc container vẫn là lớp bảo vệ riêng.

Với thao tác bảo mật nhạy cảm, cần thiết kế quyền của process, chính sách link, và cơ chế mở/duyệt thư mục chống race phù hợp, có thể cân nhắc `SecureDirectoryStream` ở provider hỗ trợ. Đừng tuyên bố `normalize()` một mình chặn hết path traversal.

### `NOFOLLOW_LINKS` thay đổi một số phép kiểm tra

Một số API cho phép `LinkOption.NOFOLLOW_LINKS` để kiểm tra **bản thân symbolic link** thay vì đi theo đích. Ví dụ:

```java
boolean isLink = Files.isSymbolicLink(path);
boolean presentWithoutFollowing =
        Files.exists(path, LinkOption.NOFOLLOW_LINKS);
```

Đây là đoạn minh họa. Một dangling symlink có thể tồn tại dưới dạng link ngay cả khi đích của nó không tồn tại; bởi vậy việc hỏi “file có tồn tại không?” cần nói rõ đang hỏi **link** hay **đích của link**.

Việc dùng `NOFOLLOW_LINKS` không phải phép đảm bảo an toàn duy nhất chống race. Cần hiểu từng API và provider có hỗ trợ option đó ở thao tác đang xét không.

## 13. `FileSystemLoopException` và duyệt cây thư mục

`Files.walkFileTree` cho phép duyệt cây thư mục với các callback như `visitFile` và `visitFileFailed`. Khi yêu cầu theo symbolic links (ví dụ với `FileVisitOption.FOLLOW_LINKS`), một chuỗi link có thể tạo **vòng lặp**.

Trong trường hợp phát hiện loop, `FileSystemLoopException` có thể được đưa vào `visitFileFailed` để ứng dụng quyết định báo lỗi hay bỏ qua theo chính sách.

Khung minh họa:

```java
Files.walkFileTree(
        root,
        java.util.EnumSet.of(
                java.nio.file.FileVisitOption.FOLLOW_LINKS),
        Integer.MAX_VALUE,
        new java.nio.file.SimpleFileVisitor<Path>() {

            @Override
            public java.nio.file.FileVisitResult visitFileFailed(
                    Path file, IOException ex) throws IOException {

                if (ex instanceof
                        java.nio.file.FileSystemLoopException) {
                    throw ex; // Thất bại có chủ đích.
                }
                throw ex;
            }
        });
```

Đây là **đoạn minh họa**, cần import `Path`, `Files`, `IOException` và biến `root` hợp lệ. Handler có thể gộp thành `throw ex;` nếu chính sách là fail-fast với mọi lỗi; việc xét `FileSystemLoopException` chỉ để làm rõ tình huống.

Không nên tự động bỏ mọi `visitFileFailed` khi quét file bảo mật hoặc backup, bởi kết quả duyệt sẽ thiếu dữ liệu nhưng vẫn có thể bị báo nhầm “thành công”.

### Duyệt tất cả file có đảm bảo không lỗi race?

Không. Trong lúc duyệt, file có thể được tạo/xóa/đổi quyền bởi tiến trình khác. `visitFileFailed` cần có chính sách rõ. Nếu một ứng dụng yêu cầu snapshot nhất quán, chỉ dùng `Files.walkFileTree` không tự tạo transaction hoặc snapshot của toàn hệ thống tệp.

## 14. `AtomicMoveNotSupportedException` khi yêu cầu di chuyển nguyên tử

`Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)` yêu cầu thao tác chuyển tên/file theo khả năng **atomic move** của provider:

```java
Files.move(
        source,
        target,
        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
```

Đoạn này là mã minh họa. Nếu không thể thực hiện như một thao tác nguyên tử, provider có thể ném **`AtomicMoveNotSupportedException`**.

### Fallback từ atomic sang non-atomic: không phải lúc nào cũng an toàn

Một số ứng dụng chọn:

```java
try {
    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
} catch (AtomicMoveNotSupportedException ex) {
    Files.move(source, target);
}
```

Đây là đoạn minh họa với import và biến path phù hợp. Chính sách này chỉ hợp lý khi **ứng dụng cho phép di chuyển không nguyên tử** và đã xét các hệ quả nếu process crash giữa chừng.

Nếu ứng dụng cần đảm bảo file đầu ra được công bố nguyên vẹn hoặc dữ liệu không rơi vào trạng thái dở dang, fallback không nguyên tử có thể **phá vỡ tính đúng đắn**. Khi đó nên truyền lỗi cho caller và báo rằng provider không đáp ứng yêu cầu.

Cũng cần lưu ý: cách xử lý **đích đã tồn tại** khi `ATOMIC_MOVE` có thể mang quy định đặc thù implementation. Đừng mặc định mọi option copy/move khác đều giữ nguyên cùng ý nghĩa khi kết hợp `ATOMIC_MOVE`.

## 15. Phân loại loại lỗi cho API/service thay vì phân tích `getMessage()`

Một service có thể muốn chuyển lỗi NIO thành mã công khai. Ví dụ:

| Loại exception gốc | Mã ứng dụng gợi ý | Cần kiểm tra ngữ cảnh trước khi trả client |
| --- | --- | --- |
| `NoSuchFileException` | `RESOURCE_NOT_FOUND` | File thiếu có thật sự là tài nguyên client được phép biết? |
| `AccessDeniedException` | `STORAGE_ACCESS_DENIED` | Không tiết lộ path nội bộ hoặc cấu hình quyền |
| `FileAlreadyExistsException` | `DESTINATION_EXISTS` | Có phải thao tác tạo mới và xung đột hợp đồng? |
| `DirectoryNotEmptyException` | `DIRECTORY_NOT_EMPTY` | Có cho phép xóa cả cây không? |
| `AtomicMoveNotSupportedException` | `ATOMIC_MOVE_UNAVAILABLE` | Có cần đảm bảo atomicity không? |
| `IOException` còn lại | `STORAGE_IO_ERROR` | Cần log kỹ thuật và chính sách retry riêng |

Các mã chỉ là **ví dụ do ứng dụng định nghĩa**, không phải các constant chuẩn của NIO.

Không thể tự động suy ra `NoSuchFileException` luôn phải là HTTP 404 hay `AccessDeniedException` luôn phải là HTTP 403. Lỗi file nội bộ có thể là **lỗi server**, không phải lỗi của client. Mapping HTTP cần dựa vào hợp đồng API và các thông tin được phép công bố.

### Không biến permission error thành missing một cách tùy tiện

Trong hệ thống bảo mật, đôi khi API chủ động trả một thông điệp “không tìm thấy” thay vì xác nhận có tài nguyên nhưng bị cấm, để tránh lộ sự tồn tại của tài nguyên. Nếu có lựa chọn đó, nó phải thuộc **chính sách bảo mật ở ranh giới API**, không phải logic generic mặc định che mọi `AccessDeniedException` thành `NoSuchFileException` ở tầng file.

Ghi log nội bộ nên vẫn giữ **phân loại lỗi gốc** để vận hành biết vấn đề quyền thực sự xảy ra.

## 16. Retry và race: lỗi nào có thể thử lại?

Không có quy tắc “mọi `IOException` đều retry” hoặc “mọi `NoSuchFileException` là tạm thời”.

- **NoSuchFileException:** nếu file do một producer bất đồng bộ sẽ tạo trong tương lai theo hợp đồng, retry có giới hạn đôi khi hợp lý. Nếu path sai vì cấu hình, retry vô hạn vô ích.
- **AccessDeniedException:** thường cần sửa permission/credentials/policy, retry tức thời không giúp; nhưng môi trường xác thực hoặc mount động có thể có trường hợp khác.
- **FileAlreadyExistsException:** đôi khi là race bình thường giữa hai tác vụ tạo cùng tên; cần chính sách idempotency hoặc tên duy nhất, không overwrite mù.
- **AtomicMoveNotSupportedException:** không thể “thử lại nhiều lần” để tự nhiên biến provider thành hỗ trợ atomic move, trừ khi môi trường đích thay đổi có chủ đích.
- **IOException khác:** cần biết lỗi cụ thể, loại thao tác, an toàn khi lặp lại và trạng thái tác động trước đó.

Đặc biệt với **ghi hoặc di chuyển**, khi exception xảy ra không phải lúc nào caller cũng biết chắc **chưa có side effect nào**. Một lần retry có thể tạo bản sao, ghi đè hoặc làm hỏng tính nhất quán nếu không có cơ chế idempotency.

Không đặt vòng retry chung quanh tất cả thao tác Files mà không có timeout, retry limit và tiêu chí phục hồi.

## 17. Dùng `try-with-resources` với file streams và channels

Một số `Files` API trả về các đối tượng `AutoCloseable` như `InputStream`, `OutputStream`, `BufferedReader`, `Stream<Path>` trong trường hợp `Files.list`, hoặc `Stream<String>` với `Files.lines`. Khi scope hiện tại sở hữu tài nguyên, nên đóng đúng lúc:

```java
static long countLines(Path path) throws IOException {
    try (java.util.stream.Stream<String> lines =
                 Files.lines(path)) {
        return lines.count();
    }
}
```

Đây là **phương thức minh họa**, cần import `Path`, `Files` và `IOException`.

Để phân tích đầy đủ lỗi:

1. **Mở nguồn** có thể ném checked `IOException`, bao gồm subclass NIO.
2. **Đọc trong pipeline** có thể xuất hiện `UncheckedIOException` theo hợp đồng `Files.lines` khi I/O trong lúc duyệt lỗi.
3. **Đóng nguồn** vẫn chạy khi rời `try-with-resources`; nếu body và close cùng lỗi, một lỗi có thể được gắn **suppressed** dưới ngoại lệ chính.
4. `getCause()` và `getSuppressed()` giúp điều tra mối liên hệ, không chỉ `getMessage()` ngoài cùng.

**Không trả một `Stream` sống ra khỏi scope đã đóng** rồi mong caller tiếp tục duyệt. Nếu API chuyển giao stream cho caller, phải công bố ai có trách nhiệm đóng và khi nào lỗi có thể phát sinh.

## 18. Thao tác file có thể ném nhiều loại lỗi ở các thời điểm khác nhau

Một lời gọi `Files.copy` hoặc `Files.move` có thể liên quan:

- Đường dẫn nguồn.
- Đường dẫn đích.
- Quyền đọc/ghi/truy cập directory.
- Tình trạng file đích tồn tại.
- Hành vi theo symbolic link.
- Tùy chọn copy/move được provider hỗ trợ.
- Lỗi I/O sau khi thao tác đã bắt đầu.

Không nên chỉ đọc tên method rồi đoán “nếu lỗi thì chắc là `AccessDeniedException`”. Mỗi thao tác có các điều kiện lỗi khác nhau; cần dùng javadoc API đang gọi khi triển khai chính sách đặc thù.

Ví dụ `Files.copy` có thể ném `FileAlreadyExistsException` nếu đích đã có và không chọn policy ghi đè, nhưng cũng có thể ném `NoSuchFileException` nếu nguồn bị xóa ngay trước lúc thực hiện, hoặc `IOException` khác vì thiết bị/network.

### Tùy chọn tạo mới và ghi đè

`Files.writeString(path, content)` theo cấu hình mặc định có thể tạo file nếu chưa tồn tại và ghi/truncate file hiện có. Nếu chỉ được phép tạo mới, thêm `StandardOpenOption.CREATE_NEW` phù hợp.

`Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)` diễn đạt yêu cầu thay thế đích nếu được phép, nhưng không phải bảo đảm một giao dịch nguyên tử tổng quát. Đừng gộp khái niệm **cho phép overwrite** với **khả năng khôi phục nếu copy thất bại**.

## 19. Phân tích stack trace và message sao cho đúng

Ví dụ minh họa định dạng:

```text
java.nio.file.NoSuchFileException: data/missing.csv
    at ... <provider frame>
    at ... <application frame>
```

Hoặc:

```text
java.nio.file.AccessDeniedException: private/report.txt
    at ... <provider frame>
    at ... <application frame>
```

Đây **không phải bản stdout cố định**; số dòng, lớp nội bộ, provider và thông điệp có thể khác. Trên một số provider, reason có thể được thêm vào thông điệp. Đừng dựa vào exact text của stack trace để ra quyết định.

Quy trình chẩn đoán tốt:

1. **Đọc loại exception thực tế**.
2. **Xác định thao tác `Files` đang chạy** và vai trò source/target.
3. **Đọc `getFile()`, `getOtherFile()`, `getReason()`** nếu có `FileSystemException`.
4. **Kiểm tra đường dẫn tương đối/tuyệt đối**, file system, mount, quyền và sự tồn tại tại thời điểm cần điều tra; không mặc định trạng thái hiện tại giống lúc lỗi.
5. **Xem cause và suppressed** ở những tầng wrapper khi cần.
6. **Phân biệt lỗi kỹ thuật với chính sách nghiệp vụ**: missing optional khác missing required; denied có thể cần hành động vận hành.
7. **Bảo vệ dữ liệu nhạy cảm** khi ghi log và trả thông báo.

Không nên chỉ:

```java
System.out.println(ex.getMessage());
```

rồi coi đó là toàn bộ chẩn đoán. Khi logger hỗ trợ đối tượng `Throwable`, truyền cả exception ở ranh giới phù hợp để giữ stack/cause/suppressed, nhưng tránh log cùng sự cố nhiều lần ở mọi tầng.

## 20. Các tình huống thực tế để chọn handler

### Tình huống A: đọc cấu hình tùy chọn

**Quy tắc:** thiếu cấu hình thì dùng default; bị từ chối quyền phải báo lỗi.

Gợi ý: catch `NoSuchFileException` và trả `Optional.empty()`, để `IOException` khác đi lên. Chỉ dùng default khi missing thật sự là điều kiện nghiệp vụ hợp lệ.

### Tình huống B: upload file với tên không được trùng

**Quy tắc:** nếu tên đã tồn tại, từ chối thao tác thay vì ghi đè.

Gợi ý: dùng tạo file với `CREATE_NEW`; catch `FileAlreadyExistsException` và trả lỗi xung đột có cấu trúc. Không dựa vào `Files.exists` trước khi tạo vì race.

### Tình huống C: deploy file cấu hình phải được công bố nguyên tử

**Quy tắc:** chỉ xuất bản bản hoàn chỉnh.

Gợi ý: viết file tạm trong đúng vùng phù hợp, kiểm tra hoàn tất, sau đó cân nhắc `Files.move(..., ATOMIC_MOVE)`. Nếu nhận `AtomicMoveNotSupportedException`, phải **đánh giá lại yêu cầu**, không fallback không nguyên tử một cách mù quáng.

### Tình huống D: xóa thư mục chứa dữ liệu người dùng

**Quy tắc:** xóa một thư mục chỉ khi nó rỗng hoặc có xác nhận/chính sách riêng cho xóa đệ quy.

Gợi ý: `Files.delete`; nếu `DirectoryNotEmptyException`, từ chối hoặc chuyển sang quy trình được ủy quyền. Không tự chạy recursive delete chỉ để “fix exception”.

### Tình huống E: thống kê cây thư mục có symbolic links

**Quy tắc:** báo chính xác rằng có phần tử chưa duyệt được.

Gợi ý: `Files.walkFileTree` với chính sách `FOLLOW_LINKS` rõ, phân biệt `FileSystemLoopException` trong `visitFileFailed` khi cần, không nuốt mọi `IOException` rồi báo đã quét đủ.

## 21. Những hiểu lầm và sai sót thường gặp

| Sai lầm hoặc nhầm lẫn | Vì sao nguy hiểm | Cách sửa |
| --- | --- | --- |
| “`NoSuchFileException` và `FileNotFoundException` là cùng một lớp” | Chúng là hai subtype khác nhau của `IOException` | Xem hierarchy và API nguồn |
| “Mọi file thiếu luôn báo `NoSuchFileException`” | Provider và ngữ cảnh có thể khác | Catch subtype hữu ích và fallback `IOException` |
| “`AccessDeniedException` là một `SecurityException`” | Một bên checked, một bên unchecked | Phân biệt chính sách hệ điều hành/provider và kiểm tra Java |
| “`Files.exists` trả false chứng minh file chắc chắn không tồn tại” | Có thể không xác định được trạng thái | Dựa vào thao tác thật và exception |
| “`isReadable` true thì `readString` không thể lỗi” | TOCTOU, quyền thay đổi, lỗi I/O khác | Xử lý lỗi tại thao tác thực |
| “`Path.of` tự kiểm tra file tồn tại” | Chỉ tạo đối tượng path | Kiểm tra khi thực hiện I/O |
| “Catch `IOException` trước subclass để tiện” | Nhánh subclass sau có thể unreachable | Bắt subtype trước |
| “Lỗi denied thì trả `Optional.empty()`” | Che sự cố vận hành/quyền | Chỉ dùng empty cho missing hợp lệ |
| “Thiếu file tức nên tạo file rỗng” | Có thể làm sai hợp đồng và che cấu hình hỏng | Phân biệt optional/required |
| “Đích tồn tại thì luôn cho `REPLACE_EXISTING`” | Có thể mất dữ liệu hoặc vi phạm idempotency | Yêu cầu overwrite rõ |
| “Không hỗ trợ atomic move thì luôn fallback” | Có thể phá yêu cầu nhất quán | Chỉ fallback khi chính sách cho phép |
| “`normalize()` đủ chặn mọi path traversal” | Symbolic links/race vẫn tồn tại | Có cơ chế kiểm soát path và quyền |
| “`Files.copy` lỗi nghĩa là chưa thay đổi gì” | Copy/di chuyển có thể có side effect một phần | Thiết kế phục hồi, kiểm tra trạng thái |
| “`getReason()` luôn có text” | Có thể null | Kiểm tra null và log có cấu trúc |
| “`FileSystemException.getFile()` trả `Path`” | Nó trả `String` | Không giả định canonical path |
| “`IOException` bắt tất cả lỗi NIO” | `InvalidPathException` và các lỗi unchecked không được bắt | Hiểu contract từng API |
| “Retry mọi `IOException` là an toàn” | Ghi file có thể không idempotent | Phân loại và giới hạn retry |
| “`Files.lines` tự đóng sau `count()`” | Terminal operation không tự đảm bảo `close` | Dùng `try-with-resources` |
| “Có message là đủ” | Mất loại lỗi, path phụ, cause và suppressed | Ghi log Throwable có ngữ cảnh an toàn |

## 22. Bài tập tự kiểm tra

**Bài A — Phân loại ngoại lệ**

`NoSuchFileException` và `AccessDeniedException` có phải checked exception không?

**Có.** Cả hai kế thừa `FileSystemException`, rồi `IOException`, nên compiler yêu cầu bắt hoặc khai báo khi lời gọi có thể để chúng thoát dưới một checked contract.

**Bài B — Catch theo thứ tự**

Có thể đặt `catch (IOException)` trước `catch (NoSuchFileException)` không?

**Không.** Handler cho subclass phía sau là unreachable vì handler cha đã bắt toàn bộ subclass.

**Bài C — Hai thuộc tính path**

Muốn lấy file chính và file thứ hai từ `FileSystemException`, gọi gì?

**`getFile()` và `getOtherFile()`**, với kết quả là chuỗi có thể null. `getReason()` cung cấp lý do bổ sung nếu có.

**Bài D — Optional config**

`NoSuchFileException` khi đọc một file cấu hình tùy chọn nên luôn làm ứng dụng crash không?

**Không nhất thiết.** Nếu hợp đồng cho phép missing, có thể dùng default hoặc `Optional.empty()`. Nhưng đừng áp dụng cùng chính sách cho `AccessDeniedException`.

**Bài E — Đếm quyền truy cập**

Nếu `Files.exists(path)` trả `true`, có được bỏ xử lý `IOException` ở `Files.readString(path)` không?

**Không.** File có thể bị xóa, thay đổi quyền hoặc gặp lỗi I/O giữa kiểm tra và đọc.

**Bài F — Đích đã tồn tại**

Khi `Files.createFile(path)` ném `FileAlreadyExistsException`, có nên tự động overwrite?

**Không mặc định.** Cần hợp đồng tạo mới, tránh mất dữ liệu và xử lý race bằng primitive phù hợp.

**Bài G — Không có atomic move**

Nếu `Files.move(..., ATOMIC_MOVE)` ném `AtomicMoveNotSupportedException`, fallback thường sang `Files.move(...)` có thể làm thay đổi điều gì?

**Mất bảo đảm nguyên tử của yêu cầu ban đầu.** Chỉ fallback nếu nghiệp vụ cho phép di chuyển không nguyên tử.

**Bài H — `getReason()` null**

Có thể nối `getReason().toLowerCase()` mà không kiểm tra không?

**Không.** `getReason()` có thể null; hơn nữa không nên phân loại lỗi bằng văn bản của reason.

**Bài I — File thiếu qua đường dẫn tương đối**

`Path.of("config/app.txt")` có đảm bảo tham chiếu thư mục chứa source code không?

**Không.** Nó được giải nghĩa theo ngữ cảnh file system/working directory của process.

**Bài J — Một lỗi đọc có thể phát sinh muộn**

`Files.lines(path)` mở thành công. Terminal `toList()` có thể gặp lỗi I/O trong lúc duyệt không?

**Có.** Một số lỗi trong quá trình duyệt stream có thể biểu hiện thành `UncheckedIOException`. Việc mở thành công không bảo đảm đọc toàn bộ thành công.

**Bài K — File-system provider không giống nhau**

Một demo cố thay quyền POSIX rồi kỳ vọng `AccessDeniedException` có đáng tin trên mọi Windows/Linux không?

**Không.** Hệ quyền, provider và đặc quyền process khác nhau. Muốn dạy phân loại, có thể dựng exception có cấu trúc; muốn test integration, phải có môi trường quyền được kiểm soát rõ.

**Bài L — Xóa thư mục**

`DirectoryNotEmptyException` có nghĩa `Files.delete` sẽ tự xóa các file con không?

**Không.** Nó báo rằng yêu cầu xóa thư mục không rỗng không thể được thực hiện theo điều kiện hiện tại; dọn cả cây là quyết định riêng.

## 23. Checklist áp dụng trong mã nguồn thực tế

Trước khi xử lý một thao tác NIO có thể thất bại:

1. **Biết hợp đồng của thao tác:** `Files.readString`, `copy`, `move`, `delete`, `walkFileTree` hay API nào?
2. **Biết file cần thiết hay tùy chọn:** missing là lỗi hay kết quả vắng mặt hợp lệ?
3. **Xử lý subtype trước:** `NoSuchFileException`, `AccessDeniedException`, `FileAlreadyExistsException`... khi chính sách thực sự khác nhau.
4. **Có fallback cho `IOException` còn lại:** không đoán mọi thất bại thuộc hai loại quen thuộc.
5. **Không nhầm checked và unchecked:** `InvalidPathException`/`SecurityException` không bị bắt bởi `IOException`.
6. **Giữ nguyên nhân gốc:** nếu bọc lỗi ở tầng service, truyền object `cause` thay vì chỉ sao chép message.
7. **Đọc thông tin path có cấu trúc:** `getFile()`, `getOtherFile()`, `getReason()`, kiểm tra null.
8. **Không lộ path nội bộ:** log và response phải theo phân quyền; không in secret/đường dẫn nhạy cảm ra client.
9. **Tránh kiểm tra trước rồi tin tuyệt đối:** `exists`/`isReadable` không ngăn race.
10. **Dùng option tạo mới/ghi đè rõ:** `CREATE_NEW` khác `CREATE` và `REPLACE_EXISTING`.
11. **Kiểm soát overwrite và delete:** không tự động xóa/ghi đè dữ liệu ngoài hợp đồng.
12. **Cân nhắc symbolic links và traversal:** xác định chính sách `NOFOLLOW_LINKS` và phạm vi path.
13. **Với atomic move, đừng fallback tùy tiện:** giữ đúng yêu cầu nhất quán.
14. **Đóng tài nguyên đúng scope:** dùng `try-with-resources` cho streams/channels/`Files.lines`/`Files.list`.
15. **Kiểm tra trạng thái sau lỗi copy/move:** có thể xuất hiện tác động một phần, tùy thao tác.
16. **Đánh giá retry và idempotency:** chỉ thử lại khi có căn cứ và giới hạn.
17. **Kiểm thử trên provider mục tiêu:** không suy ra hành vi quyền Linux cho Windows hoặc provider zip/cloud.
18. **Không lấy message làm giao thức:** loại exception và mã ứng dụng ổn định mới là cơ sở phân nhánh.

## Kết luận

`NoSuchFileException`, `AccessDeniedException` và các subclass của `FileSystemException` giúp Java NIO phân biệt **đường dẫn thiếu**, **quyền bị từ chối** và những thất bại filesystem khác. Phân biệt đúng tạo điều kiện cho ứng dụng xử lý **missing optional**, **permission failure**, **xung đột đích đã tồn tại**, **không hỗ trợ atomic move** hoặc **thư mục chưa rỗng** theo những chính sách khác nhau.

Dù vậy, **loại lỗi cụ thể còn phụ thuộc thao tác và file-system provider**; không có một quy tắc “mọi lỗi thiếu/denied đều luôn biểu diễn duy nhất một subtype” trên mọi môi trường. Hãy bắt subtype khi có chính sách rõ ràng, giữ handler `IOException` tổng quát, đọc `getFile()/getOtherFile()/getReason()` có kiểm tra null, bảo toàn cause và xử lý tài nguyên/race/permissions một cách có trách nhiệm. Một chương trình quản lý file đáng tin phải xem **lỗi là một phần của hợp đồng I/O**, không phải sự cố để nuốt rồi tiếp tục như đã thành công.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="api-exceptions-003">EOFException và xử lý dữ liệu kết thúc sớm</a>

<details>
<summary>Xem chi tiết</summary>

# EOFException và xử lý dữ liệu kết thúc sớm trong Java

> **Java Core – Exception** · Nhóm: **Ngoại lệ API** · Trọng tâm: phân biệt kết thúc stream bình thường với dữ liệu bị cắt giữa cấu trúc/bản ghi, đọc chính xác số byte cần thiết và giữ nguyên lỗi khi xử lý tệp hoặc luồng nhị phân. Ví dụ hoàn chỉnh tương thích **Java 17+**.

## 1. Hiểu đúng EOF và EOFException

**EOF** là viết tắt của *end of file*, nói rộng hơn là **đã tới cuối nguồn dữ liệu đầu vào**. Một nguồn có thể là tệp, bộ nhớ, socket hoặc luồng được bọc qua API khác.

`java.io.EOFException` là một **checked exception**, kế thừa `IOException`. Nó thường được các API đọc dữ liệu có cấu trúc phát sinh khi **số byte còn lại không đủ để hoàn tất thao tác đọc được yêu cầu**.

~~~text
java.lang.Throwable
└── java.lang.Exception
    └── java.io.IOException
        └── java.io.EOFException
~~~

Điều dễ nhầm nhất là **“đến cuối stream” không phải lúc nào cũng là exception**.

~~~java
InputStream in = new ByteArrayInputStream(new byte[] {65});
System.out.println(in.read()); // 65
System.out.println(in.read()); // -1: đã tới EOF, không tự ném EOFException.
~~~

Đây là đoạn minh họa cần import `InputStream` và `ByteArrayInputStream`, có thể đặt trong method khai báo/handle `IOException`. Với `InputStream.read()`, **`-1` là giá trị báo EOF**, còn byte thực tế được trả về dưới dạng số nguyên trong khoảng 0–255.

Trái lại:

~~~java
DataInputStream in =
        new DataInputStream(new ByteArrayInputStream(new byte[] {1, 2}));
int value = in.readInt(); // EOFException: readInt cần 4 byte.
~~~

Đây là đoạn minh họa cần import `DataInputStream`, `ByteArrayInputStream` và handle IOException. `readInt()` yêu cầu **đủ bốn byte** để tạo một giá trị `int`. Nguồn chỉ có hai byte nên việc kết thúc sớm là **vi phạm yêu cầu của chính thao tác đọc**.

**Quy tắc nhớ:** Nếu API đang hỏi “có thêm byte nào không?”, EOF có thể được báo bằng `-1` hoặc một sentinel khác. Nếu API đang yêu cầu **một giá trị/bản ghi hoàn chỉnh với kích thước xác định**, hết dữ liệu **giữa chừng** thường phải là lỗi và có thể xuất hiện dưới dạng `EOFException`.

## 2. Phân biệt những API báo cuối dữ liệu theo cách khác nhau

Không được suy rằng tất cả method tên `read` đều có cùng hợp đồng EOF.

| API | Khi tới cuối dữ liệu | Ý nghĩa thực tế |
| --- | --- | --- |
| `InputStream.read()` | Trả `-1` | Không còn byte đơn tiếp theo |
| `InputStream.read(byte[])` | Có thể trả `-1`; khi chưa EOF có thể trả ít byte hơn buffer | Một lần đọc không đảm bảo lấp đầy buffer |
| `Reader.read()` | Trả `-1` | Không còn ký tự tiếp theo |
| `BufferedReader.readLine()` | Trả `null` nếu không còn dòng nào | Một dòng cuối không có ký tự xuống dòng vẫn có thể được trả bình thường |
| `DataInputStream.readInt()` | Ném `EOFException` nếu không đủ 4 byte | Cần đủ một giá trị `int` nhị phân |
| `DataInputStream.readUnsignedByte()` | Ném `EOFException` nếu không còn byte | Cần đúng 1 byte |
| `DataInputStream.readFully(byte[])` | Ném `EOFException` nếu không đọc đủ kích thước mảng | Cần đủ toàn bộ buffer |
| `DataInputStream.readUTF()` | Có thể ném `EOFException` nếu dữ liệu UTF theo định dạng này bị cắt | Cần header độ dài và đủ phần nội dung |
| `Scanner.nextInt()` | Có thể ném `NoSuchElementException` khi không còn token phù hợp | API phân tích token, không dùng EOFException làm quy tắc chung |

**Quan trọng:** `InputStream.read(byte[])` có thể trả một số dương **nhỏ hơn kích thước buffer mà chưa tới EOF**. Một lần đọc “thiếu byte” chưa chứng minh dữ liệu đã kết thúc; API có thể trả phần còn lại trong lần đọc sau.

Một số API khác, như `InputStream.readNBytes(len)`, có hợp đồng trả **mảng ngắn hơn độ dài mong muốn khi tới EOF** chứ không nhất thiết ném EOFException. Hãy đọc đúng **Javadoc của method đang sử dụng**.

## 3. Khi nào EOF là bình thường, khi nào là lỗi?

Tình huống phụ thuộc **giao thức dữ liệu (data format/protocol)** chứ không chỉ loại stream.

### 3.1. EOF bình thường ở ranh giới hợp lệ

Giả sử tệp chứa dãy byte tùy ý và nhiệm vụ là đọc tới cuối:

~~~java
try (InputStream in = Files.newInputStream(path)) {
    int next;
    while ((next = in.read()) != -1) {
        process(next);
    }
}
~~~

Đây là đoạn minh họa cần import `InputStream`, `Files`, `Path`; `path` và `process` phải được định nghĩa, method phải handle/declare IOException. Trong bài toán này, `read() == -1` là điều kiện **kết thúc vòng lặp bình thường**, không phải dữ liệu hỏng.

### 3.2. EOF bất thường giữa một giá trị bắt buộc

Một tệp được định nghĩa luôn chứa **một mã int 4 byte**, nhưng thực tế chỉ còn 2 byte:

~~~java
try (DataInputStream in = new DataInputStream(
        new ByteArrayInputStream(new byte[] {0, 1}))) {
    in.readInt(); // Ném EOFException.
}
~~~

Đây là đoạn minh họa cần import các lớp tương ứng và handle IOException. Vì format **yêu cầu một giá trị đầy đủ**, không thể lấy 2 byte hiện có rồi tự coi là một `int` hợp lệ.

### 3.3. EOF ở đầu bản ghi khác EOF ở giữa bản ghi

Xét giao thức bản ghi:

~~~text
Mỗi record = [4 byte độ dài payload] [payload đúng số byte đã công bố]
Các record nối tiếp nhau; không có record-count ở đầu stream.
~~~

Có ba trường hợp:

- **Không còn byte nào ngay trước header của record tiếp theo:** có thể coi là **clean EOF**, nghĩa là đã hết các bản ghi trọn vẹn.
- **Đã đọc byte đầu của header nhưng còn thiếu byte để đủ 4:** **truncated header**. Đây là dữ liệu lỗi.
- **Header hợp lệ báo payload dài N byte nhưng nguồn hết khi chưa đọc đủ N:** **truncated payload**. Đây là dữ liệu lỗi.

Nếu format có **record count bắt buộc**, ví dụ đã công bố 10 bản ghi mà mới nhận 9, thì EOF ngay ở ranh giới sau bản ghi thứ 9 vẫn là **lỗi so với giao thức ấy**. Clean EOF hay unexpected EOF **không thể quyết định chỉ bằng việc thấy stream đã hết**.

## 4. Hợp đồng DataInputStream và các phương thức thường ném EOFException

`DataInputStream` cung cấp các phương thức đọc **kiểu primitive nhị phân** và dữ liệu theo format cụ thể. Đối với mỗi method, số byte cần đọc phụ thuộc định dạng.

| Method | Yêu cầu dữ liệu | Khi thiếu byte |
| --- | --- | --- |
| `readBoolean()` | 1 byte | `EOFException` |
| `readByte()` / `readUnsignedByte()` | 1 byte | `EOFException` |
| `readShort()` / `readUnsignedShort()` | 2 byte | `EOFException` |
| `readChar()` | 2 byte | `EOFException` |
| `readInt()` / `readFloat()` | 4 byte | `EOFException` |
| `readLong()` / `readDouble()` | 8 byte | `EOFException` |
| `readFully(buffer)` | Đủ số byte mà buffer yêu cầu | `EOFException` |
| `readUTF()` | Header độ dài và dữ liệu theo modified UTF-8 | `EOFException` nếu chưa đủ; `UTFDataFormatException` với mã hóa không hợp lệ theo định dạng |

Lưu ý: đây là các thao tác đọc theo **format DataInput**, không phải việc đọc các chuỗi chữ số dưới dạng text. Tệp chứa ký tự `'1'`, `'2'`, `'3'`, `'4'` không tự động có nghĩa là số nguyên 1234 khi gọi `readInt()`. Method đọc bốn byte theo biểu diễn nhị phân big-endian của DataInput.

### 4.1. readInt đọc đủ bốn byte, không chấp nhận giá trị “nửa int”

~~~java
byte[] twoBytes = {1, 2};
try (DataInputStream in = new DataInputStream(
        new ByteArrayInputStream(twoBytes))) {
    in.readInt();
} catch (EOFException ex) {
    System.out.println("Truncated integer");
}
~~~

Đây là đoạn minh họa cần import `DataInputStream`, `ByteArrayInputStream`, `EOFException` và handle/declare IOException phù hợp. Khi gọi `readInt()`, hai byte đã có **không đủ** để tạo int.

**Không nên dùng** catch EOFException và tự thay bằng 0, vì dữ liệu 2 byte chưa thể hiện ý nghĩa số 0 trong giao thức. Nếu format cho phép giá trị thiếu, phải có quy tắc biểu diễn giá trị thiếu tường minh.

### 4.2. readFully khác read(byte[])

~~~java
byte[] data = new byte[8];
in.readFully(data);
~~~

Đây là đoạn minh họa cần `in` thuộc kiểu DataInputStream và xử lý IOException. `readFully(data)` yêu cầu **đủ 8 byte**, lặp việc đọc khi cần; nếu EOF xảy ra trước đó, method ném EOFException.

Trong khi đó:

~~~java
int n = in.read(data);
~~~

một lần đọc qua InputStream/DataInputStream có thể trả số lượng **ít hơn 8 nhưng lớn hơn 0**, dù vẫn còn dữ liệu về sau. Vì thế, để đọc một payload có độ dài xác định, `readFully` tránh lỗi phổ biến “gọi read một lần rồi mặc định buffer đã đầy”.

**Thận trọng:** Khi `readFully` đã đọc được một phần rồi gặp EOFException, buffer có thể **đã bị ghi một phần**. Không nên tiếp tục dùng nó như một payload hoàn chỉnh sau catch.

## 5. Chương trình Java 17 hoàn chỉnh: phân biệt clean EOF và truncated data

Tạo tệp **`EarlyEOFExceptionDemo.java`**. Chương trình dùng các byte array trong bộ nhớ để tái tạo **bảy tình huống** không phụ thuộc hệ thống tệp hoặc mạng:

1. Nhiều bản ghi nhị phân hoàn chỉnh rồi tới clean EOF.
2. Stream rỗng: clean EOF trước bản ghi đầu.
3. Payload bị cắt ngắn.
4. Header độ dài bị cắt ngắn.
5. Header có length vượt giới hạn format.
6. Khác biệt `readInt`, `read()` và `readFully` khi hết byte.
7. `readUTF` thành công và đọc chuỗi UTF bị cắt.

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class EarlyEOFExceptionDemo {
    private static final int MAX_PAYLOAD = 1024;

    private static byte[] frame(String value) throws IOException {
        byte[] payload = value.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeInt(payload.length);
            out.write(payload);
        }
        return buffer.toByteArray();
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static String readRecord(DataInputStream in) throws IOException {
        int first = in.read();
        if (first == -1) {
            return null; // Clean EOF: no header byte consumed.
        }
        // Once the first header byte exists, remaining 3 bytes are required.
        int length = ((first & 0xFF) << 24)
                | (in.readUnsignedByte() << 16)
                | (in.readUnsignedByte() << 8)
                | in.readUnsignedByte();
        if (length < 0 || length > MAX_PAYLOAD) {
            throw new IOException("invalid frame length: " + length);
        }
        byte[] payload = new byte[length];
        in.readFully(payload); // Throws EOFException for truncated payload.
        return new String(payload, StandardCharsets.UTF_8);
    }

    private static void consume(String label, byte[] input) {
        System.out.println("== " + label + " ==");
        try (DataInputStream in =
                     new DataInputStream(new ByteArrayInputStream(input))) {
            int count = 0;
            while (true) {
                String record = readRecord(in);
                if (record == null) {
                    System.out.println("clean-eof after " + count + " record(s)");
                    break;
                }
                System.out.println("record[" + count + "]=" + record);
                count++;
            }
        } catch (EOFException ex) {
            System.out.println("truncated=" + ex.getClass().getSimpleName());
        } catch (IOException ex) {
            System.out.println("io-error=" + ex.getMessage());
        }
    }

    private static void primitiveAndRawRead() throws IOException {
        System.out.println("== primitive and raw read ==");
        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(new byte[] {1, 2}))) {
            in.readInt();
        } catch (EOFException ex) {
            System.out.println("readInt=" + ex.getClass().getSimpleName());
        }

        try (ByteArrayInputStream in =
                     new ByteArrayInputStream(new byte[] {65})) {
            System.out.println("read-first=" + in.read());
            System.out.println("read-end=" + in.read());
        }

        byte[] target = new byte[4];
        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(new byte[] {65, 66}))) {
            in.readFully(target);
        } catch (EOFException ex) {
            System.out.println("readFully=" + ex.getClass().getSimpleName());
            System.out.println("partial-buffer=" + Arrays.toString(target));
        }
    }

    private static void utfSample() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("Hello");
        }
        byte[] encoded = bytes.toByteArray();
        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(encoded))) {
            System.out.println("readUTF-ok=" + in.readUTF());
        }
        byte[] truncated = Arrays.copyOf(encoded, encoded.length - 1);
        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(truncated))) {
            in.readUTF();
        } catch (EOFException ex) {
            System.out.println("readUTF-short=" + ex.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) throws IOException {
        consume("complete records", concat(frame("A"), frame("BC")));
        consume("empty stream", new byte[0]);
        byte[] original = frame("HELLO");
        consume("truncated payload",
                Arrays.copyOf(original, original.length - 2));
        consume("truncated header", new byte[] {0, 0});
        consume("invalid length", new byte[] {0, 0, 8, 0});
        primitiveAndRawRead();
        utfSample();
    }
}
~~~

### Cách biên dịch và chạy

~~~bash
javac --release 17 -encoding UTF-8 EarlyEOFExceptionDemo.java
java EarlyEOFExceptionDemo
~~~

**Stdout chính xác đã được kiểm chứng bằng việc biên dịch và thực thi:**

~~~text
== complete records ==
record[0]=A
record[1]=BC
clean-eof after 2 record(s)
== empty stream ==
clean-eof after 0 record(s)
== truncated payload ==
truncated=EOFException
== truncated header ==
truncated=EOFException
== invalid length ==
io-error=invalid frame length: 2048
== primitive and raw read ==
readInt=EOFException
read-first=65
read-end=-1
readFully=EOFException
partial-buffer=[65, 66, 0, 0]
readUTF-ok=Hello
readUTF-short=EOFException
~~~

Chương trình không cần tệp thật, không mở socket và không dựa trên message của EOFException do JVM tạo ra. Do đó stdout minh họa các nhánh lỗi một cách nhất quán. Nếu chạy trên hệ thống có các cờ JVM hoặc phiên bản khác, cú pháp và hợp đồng API vẫn cần đối chiếu Javadoc tương ứng; ví dụ đã được kiểm chứng cụ thể với biên dịch `--release 17`.

## 6. Cách đọc record có length-prefix mà không nuốt dữ liệu bị cắt

Tâm điểm của chương trình là:

~~~java
private static String readRecord(DataInputStream in) throws IOException {
    int first = in.read();
    if (first == -1) {
        return null; // Chưa tiêu thụ byte header nào: clean EOF.
    }

    int length = ((first & 0xFF) << 24)
            | (in.readUnsignedByte() << 16)
            | (in.readUnsignedByte() << 8)
            | in.readUnsignedByte();

    if (length < 0 || length > MAX_PAYLOAD) {
        throw new IOException("invalid frame length: " + length);
    }

    byte[] payload = new byte[length];
    in.readFully(payload);
    return new String(payload, StandardCharsets.UTF_8);
}
~~~

Đây là **phương thức trích từ chương trình đầy đủ**, cần các import và hằng `MAX_PAYLOAD` tương ứng.

### 6.1. Đọc byte đầu riêng để phân biệt clean EOF

Tại thời điểm chuẩn bị đọc một record mới, `in.read()` trả `-1` nghĩa là **chưa có byte nào của header được tiêu thụ**. Với giao thức “nhiều record cho đến hết stream” trong ví dụ, đó là ranh giới hợp lệ để dừng vòng lặp.

Phương thức trả `null` để biểu thị **không có record tiếp theo**. Vì payload được chuyển thành String bằng `new String(...)`, một record có payload độ dài 0 sẽ trả chuỗi rỗng `""`, **khác null** và không bị nhầm thành EOF.

Nếu format cho phép giá trị payload là null theo cách khác, nên sử dụng kiểu kết quả có trạng thái rõ ràng (ví dụ một wrapper có `present`, sealed result hoặc một dạng sentinel không xung đột), thay vì đánh đồng null dữ liệu với null kết thúc.

### 6.2. Sau byte đầu, các byte header còn lại là bắt buộc

Khi `first != -1`, một bản ghi **đã bắt đầu**. Ba byte độ dài còn lại được đọc bằng `readUnsignedByte()`; method này ném EOFException nếu không lấy được byte bắt buộc. Vì vậy input `{0, 0}` cho kết quả:

~~~text
== truncated header ==
truncated=EOFException
~~~

Nếu đơn giản gọi `readInt()` cho cả header rồi bắt EOFException và “ngừng đọc bình thường”, bạn sẽ không phân biệt được **stream rỗng/clean EOF** với **header đã bắt đầu nhưng bị cắt**. Phân biệt ấy là lý do phải đọc byte đầu trước.

Lưu ý về biểu thức ghép độ dài: `read()` trả một giá trị nguyên 0–255, nên code dùng `first & 0xFF` và dịch bit theo định dạng **big-endian** tương ứng với `DataOutputStream.writeInt`. Với bit dấu cao nhất, kết quả `int` có thể âm; bộ kiểm tra giới hạn sẽ từ chối.

### 6.3. Giới hạn độ dài trước khi cấp phát buffer

Header có bốn byte là một số nguyên 32-bit. Nếu tin tưởng mù quáng mọi length từ nguồn không đáng tin, chương trình có thể cố tạo một mảng rất lớn, gây **cạn bộ nhớ** hoặc từ chối dịch vụ.

Ví dụ bảo vệ:

~~~java
if (length < 0 || length > MAX_PAYLOAD) {
    throw new IOException("invalid frame length: " + length);
}
~~~

Giao thức minh họa đặt giới hạn `MAX_PAYLOAD = 1024`. Header `{0,0,8,0}` đại diện độ dài 2048, nên in:

~~~text
io-error=invalid frame length: 2048
~~~

Đây là **length không hợp lệ**, chưa phải EOFException: parser từ chối ngay trước khi đọc payload. Trong hệ thống thực tế, giá trị giới hạn cần dựa vào format, ngân sách bộ nhớ và rủi ro an ninh, không dùng mặc định 1024 cho mọi ứng dụng.

### 6.4. readFully giúp phát hiện payload thiếu byte

Sau khi kiểm tra length, code tạo buffer đúng kích thước và gọi:

~~~java
byte[] payload = new byte[length];
in.readFully(payload);
~~~

Nếu header nói payload phải dài 5 byte nhưng stream chỉ còn 3 byte, `readFully` ném EOFException. Ví dụ đã tạo record `"HELLO"` rồi cắt mất hai byte cuối để kiểm tra đúng điều kiện này.

**Không được** tự dùng 3 byte đã có làm record hợp lệ, vì như vậy vi phạm format. Nếu ứng dụng cần khôi phục dữ liệu phần nào, hãy thiết kế **chế độ recovery riêng**, với logging và giới hạn rõ, thay vì tráo đường lỗi thành thành công.

## 7. ReadFully có thể đã ghi một phần vào buffer trước khi ném lỗi

Trong chương trình:

~~~java
byte[] target = new byte[4];
try (DataInputStream in = new DataInputStream(
        new ByteArrayInputStream(new byte[] {65, 66}))) {
    in.readFully(target);
} catch (EOFException ex) {
    System.out.println("partial-buffer=" + Arrays.toString(target));
}
~~~

Đây là đoạn trích từ phương thức runnable. Buffer được tạo có bốn phần tử ban đầu bằng 0. Hai byte `65` và `66` được copy trước khi gặp EOF. Kết quả trên JVM đã chạy:

~~~text
readFully=EOFException
partial-buffer=[65, 66, 0, 0]
~~~

**Ý nghĩa:** Có exception không đồng nghĩa buffer được **rollback** về trạng thái ban đầu. Nó có thể chứa dữ liệu một phần, kể cả khi `readFully` đã báo thất bại.

Nguyên tắc xử lý:

1. Chỉ công bố hoặc commit dữ liệu khi **toàn bộ record đã được đọc và xác thực**.
2. Nếu đọc lỗi, đánh dấu buffer/record hiện tại là **không hoàn chỉnh**, không tái sử dụng như giá trị đúng.
3. Khi cần retry, phải biết **vị trí stream đã tiêu thụ bao nhiêu byte**; một lần đọc lỗi có thể đã làm con trỏ di chuyển.
4. Không mặc định có thể retry lại cùng phương thức trên cùng stream mà dữ liệu tự quay về đầu record.
5. Nếu cần phục hồi, phải có format cho phép xác định ranh giới record, offset, checksum hoặc thao tác seek/reopen phù hợp.

Một lời gọi `readFully` giúp bảo đảm **hoặc đọc đủ, hoặc báo lỗi**; nó **không bảo đảm tính nguyên tử của side effect lên mảng hoặc trạng thái stream**.

## 8. readUTF và EOFException: không nhầm với UTF-8 thông thường

`DataOutputStream.writeUTF(String)` và `DataInputStream.readUTF()` dùng **modified UTF-8 với một header độ dài unsigned short**, khác với việc đơn giản ghi các byte `String.getBytes(StandardCharsets.UTF_8)`.

Trong chương trình:

~~~java
ByteArrayOutputStream bytes = new ByteArrayOutputStream();
try (DataOutputStream out = new DataOutputStream(bytes)) {
    out.writeUTF("Hello");
}
byte[] encoded = bytes.toByteArray();
~~~

Sau đó `readUTF()` trên toàn bộ encoded data trả `"Hello"`. Nếu bỏ đi một byte cuối, `readUTF()` không còn đủ phần nội dung được khai báo và ném `EOFException`:

~~~text
readUTF-ok=Hello
readUTF-short=EOFException
~~~

### Trường hợp đọc UTF hỏng nhưng không phải thiếu byte

Nếu dữ liệu có đủ số byte mà **mã hóa modified UTF-8 không hợp lệ**, API có thể ném `UTFDataFormatException`, cũng là một dạng `IOException`. Đây là lỗi **format encoding**, cần phân biệt với thiếu byte.

Ngoài ra, header độ dài của `writeUTF/readUTF` giới hạn số byte theo format (tối đa 65535 byte encoded), không phải một cơ chế để đọc file UTF-8 kích thước tùy ý.

**Khi xây dựng giao thức riêng**, nếu muốn dùng UTF-8 tiêu chuẩn, có thể thiết kế length-prefix theo byte và dùng `StandardCharsets.UTF_8` như ví dụ `readRecord`. Phía ghi/đọc phải thống nhất đúng format. Không ghi bằng `writeUTF` rồi giả định phía đọc dùng `new String(input, UTF_8)` trên toàn bộ byte stream là tương đương.

## 9. Đọc text: BufferedReader.readLine trả null, không mặc định ném EOFException

Với tệp văn bản phân dòng:

~~~java
try (BufferedReader reader =
             Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
    String line;
    while ((line = reader.readLine()) != null) {
        processLine(line);
    }
}
~~~

Đây là mã minh họa cần import `BufferedReader`, `Files`, `Path`, `StandardCharsets`, và khai báo/handle IOException. `processLine` phải có định nghĩa.

Theo hợp đồng `readLine()`:

- Khi có dòng, trả một String (có thể rỗng).
- Nếu stream đã hết và **không còn ký tự nào để tạo dòng mới**, trả `null`.
- Một dòng cuối không có ký tự xuống dòng **vẫn được trả là một dòng**, không bị tự loại bỏ.
- Một lỗi I/O thực sự vẫn có thể ném IOException, không phải lúc nào cũng được biểu diễn bằng null.

Nếu format yêu cầu chính xác 10 dòng mà chỉ nhận 9, `readLine() == null` ở dòng thứ 10 là một **vi phạm format**, dù API báo EOF bằng null thay vì tự ném EOFException. Caller phải quyết định xử lý, ví dụ:

~~~java
String header = reader.readLine();
if (header == null) {
    throw new EOFException("missing required header line");
}
~~~

Đây là đoạn minh họa với biến `reader` phù hợp và import `EOFException`. Ở đây **ứng dụng chủ động ném EOFException** để diễn đạt “thiếu dòng bắt buộc”, không phải `readLine()` tự ném EOFException.

## 10. InputStream.read(byte[]) đọc thiếu không nhất thiết do EOF

Một nhầm lẫn phổ biến:

~~~java
byte[] buffer = new byte[1024];
int n = input.read(buffer);
if (n < buffer.length) {
    // Sai nếu mặc định dữ liệu đã hết / bị hỏng.
}
~~~

Đây là đoạn minh họa cần biến `input` kiểu InputStream và xử lý IOException. Một lần `read(buffer)` có thể trả **ít byte hơn yêu cầu**, nhất là trên luồng mạng hoặc luồng được bọc, trong khi lần đọc tiếp theo vẫn có byte.

Vì vậy:

- `n > 0`: đã đọc được n byte, **chưa kết luận** hết stream chỉ vì n nhỏ hơn buffer.
- `n == -1`: báo EOF theo API của InputStream.
- `n == 0`: với một số overload và trường hợp đặc biệt (như len = 0), có thể không đọc byte nào; xử lý theo Javadoc chính xác.
- Nếu cần đúng N byte, dùng cơ chế **đọc lặp đến đủ N**, như `DataInputStream.readFully`.
- Nếu format cho phép tới EOF thì xử lý sentinel; nếu format yêu cầu N byte, tới EOF trước N là lỗi.

Đừng dựa vào `InputStream.available()` làm “tổng số byte còn lại trong file/stream”. Theo API, đó chỉ là **ước lượng số byte có thể đọc hoặc skip mà không block** trong điều kiện được mô tả; đặc biệt với network stream nó không nói dữ liệu tương lai có tới hay không.

## 11. Đọc từ socket: close, EOF và timeout có nghĩa khác nhau

Trên một stream mạng, việc đọc có thể có những kết quả cần phân biệt:

- **`read() == -1`**: không còn dữ liệu từ đầu vào theo trạng thái stream; trong giao thức TCP, có thể phản ánh bên gửi đã đóng phần gửi dữ liệu một cách bình thường. Nhưng nếu giao thức đang chờ phần còn lại của một message, đó vẫn là **unexpected end** ở tầng protocol.
- **`SocketTimeoutException`**: một thao tác I/O vượt quá thời hạn chờ đã cấu hình. Đây **không đồng nghĩa** bên gửi chắc chắn đóng stream hoặc dữ liệu đã bị cắt vĩnh viễn.
- **`EOFException`**: thường do API cấp cao yêu cầu đọc cấu trúc/giá trị đầy đủ nhưng stream đã hết trước khi đáp ứng yêu cầu.
- **`SocketException`/IOException khác**: lỗi kết nối, reset, vấn đề I/O; không nên tự coi tất cả là EOF.

Ví dụ một peer gửi header độ dài 100 byte, payload mới được 60 byte rồi kết thúc kết nối. Nếu parser dùng `readFully(100 bytes)`, nó báo EOFException vì message **không hoàn chỉnh**. Nếu code chỉ kiểm tra `read() == -1` và coi là kết thúc bình thường, nó có thể vô tình chấp nhận một record bị thiếu.

**Hành động đúng phụ thuộc giao thức**: ghi log packet/frame id, từ chối record, đóng kết nối bị lỗi, cân nhắc retry request **nếu thao tác an toàn/idempotent**, hoặc bắt đầu lại từ checkpoint khi format hỗ trợ. Không retry vô hạn chỉ vì thấy EOF.

## 12. Những trạng thái parser nên phân biệt rõ

Một parser record thường có những trạng thái:

| Trạng thái | Dữ liệu đã nhận | Kết quả phù hợp |
| --- | --- | --- |
| Chưa bắt đầu record; stream báo EOF | Không có byte header mới | Clean EOF nếu format cho phép |
| Header đọc chưa đủ | Một phần header | Lỗi truncated header |
| Header hoàn tất nhưng length âm/vượt giới hạn | Đủ header nhưng giá trị sai | Lỗi format/validation |
| Payload chưa đủ so với length | Một phần payload | Lỗi truncated payload |
| Payload đủ nhưng encoding sai | Đủ byte, decode không hợp lệ | Lỗi format encoding |
| Payload hợp lệ | Đủ header và đủ nội dung | Commit/emit một record |
| Stream hết sau record hoàn chỉnh | Đang ở ranh giới tiếp theo | Clean EOF nếu đủ số record yêu cầu |

Bảng nêu **một mô hình parser ứng dụng**; không khẳng định rằng Java có sẵn đúng những class exception riêng cho mọi hàng. Một số hệ thống tạo custom exception như `TruncatedRecordException`, `InvalidLengthException` và giữ cause là EOFException/IOException gốc để caller có chính sách khác nhau.

### 12.1. Không áp dụng một chính sách lỗi cho mọi hoàn cảnh

Tùy ứng dụng:

- **Tệp cấu hình/bản ghi nghiệp vụ:** dữ liệu thiếu nên fail fast, không trả một object chưa được kiểm chứng.
- **Luồng streaming liên tục:** clean EOF ở ranh giới gói có thể hoàn toàn bình thường.
- **Network request-response:** payload thiếu so với content length có thể là lỗi giao thức cần retry có kiểm soát.
- **Tệp phục hồi/forensic:** có thể lưu phần record hoàn chỉnh cuối cùng, ghi cảnh báo về record bị cắt; không âm thầm xem toàn bộ file là hợp lệ.
- **Tệp append-only khi writer còn hoạt động:** thấy EOF khi đọc chưa chắc writer đã hoàn tất; cần cơ chế phối hợp/tailing và quy tắc commit record, không tự gán kết luận từ API read.

Thiết kế format cần nói rõ **khi nào EOF hợp lệ**. Nếu không có mô tả đó, catch EOFException có nguy cơ che mất hỏng dữ liệu.

## 13. Cách catch EOFException đúng vị trí

Vì `EOFException extends IOException`, cần đặt handler cụ thể **trước handler tổng quát** khi có cả hai:

~~~java
try {
    readExpectedRecord();
} catch (EOFException ex) {
    reportTruncatedData(ex);
} catch (IOException ex) {
    reportOtherIoFailure(ex);
}
~~~

Đây là mẫu minh họa, các hàm phải được định nghĩa. Nếu đặt `catch (IOException)` trước `catch (EOFException)` trong cùng chuỗi, nhánh EOFException bị bao phủ và **compiler sẽ từ chối** do handler không thể tới được.

Một anti-pattern nguy hiểm:

~~~java
while (true) {
    try {
        processNextRecord();
    } catch (EOFException ex) {
        break; // Sai nếu record bị cắt giữa chừng cũng đi qua đây.
    }
}
~~~

Đây là mã minh họa, không phải chương trình độc lập. Nó chỉ hợp lệ khi **hợp đồng của processNextRecord() bảo đảm EOFException chính là sentinel kết thúc giữa hai record**, không thể có dữ liệu cắt ngang. Với DataInputStream.readInt(), EOFException một mình **không thể** phân biệt hết byte ngay trước giá trị và hết byte giữa giá trị.

Cách tốt hơn khi protocol có length-prefix không kèm record count là:
1. Kiểm tra clean EOF tại **điểm bắt đầu header**, chưa tiêu thụ byte.
2. Sau khi record đã bắt đầu, để EOFException lan như một lỗi truncation.
3. Catch EOFException ở **boundary xử lý file/connection**, đánh dấu input bị lỗi, không vô tình tiếp tục record tiếp theo.

Trong chương trình mẫu, `readRecord` trả `null` cho clean EOF, còn ném EOFException khi header/payload bị cắt. Đây là cách tách tình trạng **bình thường** và **bất thường** bằng contract của chính parser.

## 14. EOFException không phải một cách “chữa” dữ liệu thiếu

Một số lựa chọn sai và hậu quả:

~~~java
try {
    return in.readInt();
} catch (EOFException ex) {
    return 0; // Không có nghĩa hai byte thiếu tương đương số 0.
}
~~~

Đây là anti-pattern trong một phương thức trả int, với biến `in` và phần khai báo IOException cần phù hợp. Nếu format quy định đúng 4 byte nhưng chỉ nhận 2, không đủ bằng chứng rằng giá trị là 0.

Mẫu tương tự với chuỗi:

~~~java
byte[] payload = new byte[size];
try {
    in.readFully(payload);
} catch (EOFException ex) {
    return new String(payload, StandardCharsets.UTF_8);
}
~~~

Đây là anti-pattern minh họa với phương thức trả String và các biến/import tương ứng. Buffer có thể còn phần chưa đọc, mặc định zero; convert thành String lúc ấy biến **truncated/corrupted input** thành kết quả nhìn như hợp lệ.

Cách xử lý tốt hơn:

- Ném tiếp exception với bối cảnh (offset, record index, expected/actual bytes nếu biết).
- Chỉ xuất bản **record đã đọc đủ và qua validation**.
- Nếu ứng dụng cho phép khôi phục một phần, cung cấp kết quả recovery **được đánh dấu rõ là không hoàn chỉnh**.
- Khi cần lưu trạng thái, dùng transaction/checkpoint: dữ liệu phần record chưa hoàn thành không được tự động commit.
- Không dùng `EOFException` để thay cho việc thiếu dữ liệu nghiệp vụ có thể xử lý bình thường nếu API đó đã có một kiểu kết quả phù hợp.

## 15. Thiết kế exception có bối cảnh để dễ debug

`EOFException` có thể không mang message đủ chi tiết. Khi gặp dữ liệu bị cắt, caller thường muốn biết:
- Đang đọc **file/stream** nào, từ **offset** nào?
- Đang ở **bản ghi thứ mấy**, phần **header hay payload**?
- Số byte dự kiến là bao nhiêu?
- Đã đọc được bao nhiêu byte trước khi lỗi, nếu có thể biết?
- Có thể retry hoặc resume an toàn không?

Một cách gắn bối cảnh mà vẫn giữ lỗi gốc:

~~~java
try {
    in.readFully(payload);
} catch (EOFException ex) {
    throw new IOException(
            "Truncated payload in record " + recordIndex
                    + ", expected " + payload.length + " bytes",
            ex);
}
~~~

Đây là mã minh họa; cần có các biến và method xử lý IOException phù hợp. Wrapper mới là `IOException` có **cause = EOFException gốc**. Caller có thể log stack trace nguyên nhân cùng thông điệp cấp ứng dụng.

Nếu caller cần bắt riêng **truncated data** như một nhánh hợp đồng ổn định, có thể tạo subclass `IOException` riêng với các trường dữ liệu có cấu trúc. Không nên dựa vào việc so sánh chuỗi message như `"Unexpected EOF"` vì thông điệp có thể thay đổi.

Một nguyên tắc tương tự áp dụng cho `try-with-resources`: nếu thao tác đọc đã thất bại và `close()` cũng thất bại, exception từ close có thể nằm trong `getSuppressed()`. Ghi log toàn bộ Throwable, không chỉ `ex.getMessage()`, giúp giữ cả cause và lỗi cleanup.

## 16. DataInputStream, readUTF và ObjectInputStream: không trộn format

`DataInputStream` và `DataOutputStream` là cặp phù hợp để đọc/ghi primitive binary theo cùng encoding. `ObjectInputStream` và `ObjectOutputStream` dùng một **object serialization stream format** khác, có marker và metadata riêng.

Một số trường hợp đọc object stream đến cuối có thể nhận EOFException. Nhưng không nên suy:

- Cứ `ObjectInputStream.readObject()` ném EOFException là hết một danh sách object hợp lệ.
- Mọi exception khi đọc object đều là EOFException.
- File serialization bị cắt chỉ cần catch EOFException rồi coi là đọc xong.

Ngoài EOFException, object serialization có thể ném `StreamCorruptedException`, `OptionalDataException`, `ClassNotFoundException` hoặc các loại lỗi khác tùy API và dữ liệu. Nếu format không cung cấp **số object, sentinel kết thúc hoặc khung bao rõ ràng**, chỉ dựa vào EOFException để chấp nhận file có thể che trường hợp truncation.

Đối với dữ liệu lâu dài hoặc gửi qua mạng, serialization native của Java có thêm các mối quan tâm về tương thích phiên bản và an toàn. Hãy chọn format/giao thức rõ ràng, có size limit, kiểm tra schema và ranh giới record phù hợp.

### Không đánh đồng UTF-8 và modified UTF-8

Cặp `writeUTF/readUTF` phù hợp với nhau theo Javadoc của DataOutput/DataInput. Nhưng một format tự thiết kế dùng `writeInt(length) + UTF-8 bytes` phải được đọc theo đúng logic length-prefix + UTF-8; không thể tráo `readUTF()` vào đó vì header và kiểu encoding khác.

## 17. Hiệu năng, bộ nhớ và dữ liệu không tin cậy

Khi đọc length-prefix từ nguồn bên ngoài:

1. **Giới hạn kích thước** trước khi cấp phát mảng; tránh length âm hoặc quá lớn.
2. **Giới hạn số bản ghi** và tổng số byte khi cần, không chỉ một record.
3. **Không giữ mọi record trong bộ nhớ** nếu có thể xử lý từng record hoàn chỉnh.
4. **Không cấp phát dựa trên available()** như thể đó là độ dài thật.
5. **Không coi read() trả ít byte hơn yêu cầu là dữ liệu hỏng** khi API cho phép partial read; chỉ xác nhận truncation sau khi thật sự gặp EOF trước độ dài cần.
6. **Kiểm tra encoding** khi đầu vào cần UTF-8 hợp lệ. `new String(bytes, StandardCharsets.UTF_8)` thông thường có thể thay chuỗi byte lỗi bằng ký tự thay thế; nếu format yêu cầu strict UTF-8, dùng `CharsetDecoder` cấu hình `CodingErrorAction.REPORT`.
7. **Tránh infinite retry** trên stream đã EOF. Đọc lại cùng stream có thể tiếp tục trả -1; muốn retry phải có nguồn mới, seek/reopen hoặc cơ chế resume được thiết kế.
8. **Xử lý cancellation/timeout** riêng với lỗi EOF, đặc biệt trong môi trường network.
9. **Đóng stream đúng vòng đời** bằng try-with-resources và bảo toàn exception cleanup/suppressed.
10. **Test hỏng dữ liệu có chủ đích**, không chỉ happy path.

Một header cho phép đọc dữ liệu khổng lồ mà không giới hạn có thể gây lỗi tài nguyên ngay trước khi parser có cơ hội phát hiện payload bị cắt. Do đó validation length và giới hạn tổng là một phần thiết yếu của parser an toàn.

## 18. Những lỗi hiểu sai thường gặp

1. **“Đến EOF luôn ném EOFException.”** Sai: InputStream.read thường trả -1; Reader.readLine trả null.
2. **“EOFException là unchecked.”** Sai: nó kế thừa IOException nên là checked exception.
3. **“Bắt EOFException rồi break mọi vòng lặp đọc record là an toàn.”** Sai nếu thiếu byte giữa header/payload cũng ném EOFException.
4. **“readInt chỉ đọc tới lúc có dữ liệu, có 2 byte cũng ra một số.”** Sai: cần đủ 4 byte.
5. **“readFully ném lỗi nên buffer chắc chắn chưa bị thay đổi.”** Sai: có thể đã đọc một phần.
6. **“Gọi read(byte[]) một lần phải đầy buffer.”** Sai: API có thể trả ít byte hơn độ dài yêu cầu.
7. **“Một lần partial read chứng minh EOF.”** Sai: có thể có byte trong lần đọc tiếp theo.
8. **“available() cho tổng số byte còn lại của stream.”** Sai: không có cam kết đó trong contract tổng quát.
9. **“Cắt header là clean EOF vì vẫn đọc được một phần.”** Sai: nếu record đã bắt đầu thì header thiếu là lỗi.
10. **“Kết thúc ngay sau record hoàn chỉnh luôn là lỗi.”** Không: phụ thuộc giao thức, có thể là ranh giới clean EOF.
11. **“Record-count khai báo 10 nhưng nhận 9 rồi EOF vẫn được coi là đủ.”** Sai theo format có count bắt buộc.
12. **“Có length N, nhận N-1 byte vẫn có thể emit record bình thường.”** Sai nếu format quy định phải đủ N.
13. **“Header length lớn chỉ cần cấp phát rồi đọc thử.”** Nguy hiểm: phải validate giới hạn trước.
14. **“UTFDataFormatException và EOFException là một.”** Sai: một trường hợp sai encoding, trường hợp kia thiếu byte (tùy method và ngữ cảnh).
15. **“readUTF hiểu mọi dạng UTF-8 file.”** Sai: dùng modified UTF-8 và header đặc thù.
16. **“writeInt(length) và writeUTF có cùng header.”** Sai: writeInt dùng 4 byte; writeUTF có header độ dài 2 byte.
17. **“SocketTimeoutException chắc chắn nghĩa là peer đã đóng.”** Sai: timeout là hạn chờ, không đồng nghĩa EOF.
18. **“Catch IOException trước EOFException vẫn bắt EOF riêng được.”** Sai: nhánh cụ thể phía sau sẽ unreachable.
19. **“Nên mặc định trả 0 khi thiếu byte cho int.”** Sai: đang hợp thức hóa dữ liệu hỏng.
20. **“Close stream làm dữ liệu đang đọc dở trở nên hợp lệ.”** Sai: cleanup không khôi phục format.
21. **“EOFException có message luôn cố định.”** Sai: đừng phụ thuộc thông điệp JVM/API.
22. **“Nếu readObject ném EOFException thì chắc chắn toàn bộ object stream hợp lệ.”** Sai: format và độ hoàn chỉnh phải được xác định bằng framing/metadata phù hợp.
23. **“Nếu đã catch EOFException thì không cần catch IOException.”** Sai: parser còn có thể gặp lỗi I/O, format hoặc length khác.
24. **“EOF do socket và EOF do đọc tệp nhị phân đều cần cùng cách retry.”** Không: policy phụ thuộc nguồn, side effect và giao thức.
25. **“Truncated record có thể tự coi như record rỗng.”** Sai nếu không có biểu diễn đó trong format.

## 19. Bài tập tự kiểm tra

**Câu 1.** `InputStream in` có một byte, gọi `in.read()` hai lần. Kết quả?

*Đáp án:* Lần đầu trả giá trị byte 0–255, lần thứ hai trả -1 khi EOF; riêng tình huống này không tự ném EOFException.

**Câu 2.** `DataInputStream.readInt()` cần mấy byte, khi chỉ còn hai byte thì sao?

*Đáp án:* Cần bốn byte theo format DataInput; khi thiếu ném EOFException.

**Câu 3.** Giao thức có header 4 byte, stream trống trước khi đọc header: có thể là kết thúc bình thường không?

*Đáp án:* Có, nếu protocol cho phép chuỗi nhiều record kết thúc tại ranh giới chưa bắt đầu record nào. Nếu format bắt buộc phải có ít nhất một record hoặc có record-count chưa đáp ứng thì vẫn là lỗi giao thức.

**Câu 4.** Đã đọc 1 byte header, nhưng stream hết. Có nên dừng vòng lặp bình thường?

*Đáp án:* Không; header đã bắt đầu nhưng chưa hoàn thành, nên phải coi là truncated header.

**Câu 5.** Header báo payload 100 byte, thực tế còn 60 byte. `readFully(payload)` làm gì?

*Đáp án:* Cố đọc đủ rồi ném EOFException khi không đủ; buffer có thể đã chứa 60 byte và không được xem là record hoàn chỉnh.

**Câu 6.** `InputStream.read(buffer)` trả 60 với buffer 100 byte. Có kết luận chắc chắn bị cắt không?

*Đáp án:* Chưa. Một lần đọc có thể trả thiếu khi chưa EOF; cần tiếp tục đọc theo contract để xác định có nhận đủ 100 hay không.

**Câu 7.** Có nên dùng `available() == 0` để kết luận file/socket đã EOF?

*Đáp án:* Không. available() không phải dấu hiệu kết thúc tổng quát cho mọi stream.

**Câu 8.** `readUTF()` khác `new String(bytes, UTF_8)` ở điểm nào?

*Đáp án:* readUTF kỳ vọng header độ dài cùng modified UTF-8 theo DataInput. new String giải mã bytes theo UTF-8 tiêu chuẩn và không tự xử lý header đó.

**Câu 9.** Một dòng text cuối không có ký tự xuống dòng, `readLine()` có trả dòng này không?

*Đáp án:* Có. Chỉ khi không còn ký tự nào cho dòng tiếp theo thì readLine trả null.

**Câu 10.** Chương trình có `catch (IOException)` trước `catch (EOFException)` trong cùng try: kết quả?

*Đáp án:* Lỗi compile-time do catch EOFException bị catch IOException trước bao phủ.

**Câu 11.** Header độ dài bị đọc thành 2048 nhưng format giới hạn 1024: EOFException hay lỗi validation?

*Đáp án:* Lỗi validation length. Ví dụ chính ném IOException với message invalid frame length, trước khi cố đọc payload.

**Câu 12.** Vì sao không nên catch EOFException rồi trả payload thiếu byte như đã đọc đủ?

*Đáp án:* Vì record chưa đáp ứng kích thước bắt buộc; buffer có thể chứa dữ liệu một phần, còn thiếu dữ liệu và chưa qua validation.

**Câu 13.** Peer không phản hồi và `read()` bị timeout có đồng nghĩa peer đóng kết nối?

*Đáp án:* Không. Timeout là điều kiện chờ hết thời hạn, khác với EOF được báo bằng -1 hoặc thiếu byte đã xác định.

**Câu 14.** Khi muốn gửi nguyên vẹn một record chứa nhiều byte UTF-8, nên ghi độ dài theo số ký tự hay số byte?

*Đáp án:* Với giao thức đã quy định payload là mảng UTF-8 bytes, length cần tính bằng **số byte encoded**, không phải số ký tự Unicode trong String.

**Câu 15.** Một parser đã nhận đủ header và payload, sau đó gặp EOF trước header record mới. Có cần ném EOFException?

*Đáp án:* Không nhất thiết. Nếu format cho phép nhiều record nối tiếp đến EOF, đây chính là clean EOF. Nếu còn record bắt buộc theo count, nó là lỗi format.

## 20. Checklist triển khai parser chống EOF ngoài dự kiến

Trước khi đưa một parser vào ứng dụng:

1. **Mô tả format byte-level:** header, byte order, length, encoding, checksum, record count, sentinel kết thúc.
2. **Định nghĩa clean EOF tại ranh giới nào:** trước header hay sau sentinel, hay khi đã đủ record-count?
3. **Dùng API đọc đúng số byte cần:** `readFully` hoặc vòng lặp tương đương, không một lần read may rủi.
4. **Kiểm tra length và giới hạn tài nguyên** trước cấp phát.
5. **Phân biệt lỗi header, payload, encoding, checksum và IO** theo nhu cầu xử lý.
6. **Không emit record một phần** nếu format không cho phép.
7. **Chỉ commit tác động nghiệp vụ sau khi validate record hoàn chỉnh.**
8. **Giữ cause/stack trace và context** khi bọc exception.
9. **Cẩn thận trạng thái stream sau lỗi:** có thể đã tiêu thụ một phần, không tự rewind.
10. **Test empty input, complete input, thiếu 1/2/3 byte header, thiếu payload, invalid length, length 0, nhiều records, trailing bytes, encoding sai, và IO error.**
11. **Test cả đường clean EOF lẫn truncated input**, bảo đảm không đi vào cùng nhánh thành công.
12. **Kiểm tra thời gian chờ và hủy** nếu là stream mạng; không gọi retry vô hạn.
13. **Đóng tài nguyên an toàn** bằng try-with-resources và xem suppressed nếu close cũng lỗi.
14. **Không dựa vào message mặc định** của EOFException để phân loại hành vi.

## 21. Tài liệu Java 17 chính thức

- [Java SE 17 API – EOFException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/EOFException.html): ngoại lệ biểu thị kết thúc input trước khi đọc đủ dữ liệu cần thiết.
- [Java SE 17 API – IOException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/IOException.html): lớp cha checked của EOFException và nhiều lỗi I/O khác.
- [Java SE 17 API – InputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html): `read()` trả -1 tại EOF, các overload có thể đọc ít byte hơn yêu cầu.
- [Java SE 17 API – DataInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataInputStream.html): primitive reads, `readFully`, `readUTF`.
- [Java SE 17 API – DataInput](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataInput.html): mô tả định dạng dữ liệu, `readFully` và các trường hợp EOF.
- [Java SE 17 API – DataOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataOutputStream.html): `writeInt`, `writeUTF` tương thích với DataInput.
- [Java SE 17 API – BufferedReader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedReader.html): `readLine()` trả null tại cuối input.
- [Java SE 17 API – UTFDataFormatException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/UTFDataFormatException.html): lỗi không đúng định dạng modified UTF-8.
- [Java SE 17 API – Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): để phân biệt lỗi chờ tác vụ bất đồng bộ với EOF trong dữ liệu, khi bài toán có concurrency.
- [JLS Java SE 17 chương 11 – Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html): cơ sở checked/unchecked exception.

**Kết luận:** `EOFException` là **checked IOException**, thường báo rằng một API đang yêu cầu một giá trị nhị phân hoàn chỉnh nhưng stream kết thúc trước khi đọc đủ byte. Trong khi đó, `InputStream.read() == -1` hoặc `BufferedReader.readLine() == null` có thể chỉ là EOF **bình thường**. Hãy định nghĩa ranh giới bản ghi, nhận biết **đã bắt đầu header/payload hay chưa**, đọc đúng kích thước bắt buộc, kiểm tra length và chỉ chấp nhận record đã hoàn tất. Không catch EOFException rồi mặc nhiên coi mọi dữ liệu kết thúc sớm là hợp lệ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="api-exceptions-004">SocketException, SocketTimeoutException và lỗi mạng</a>

<details>
<summary>Xem chi tiết</summary>

# SocketException, SocketTimeoutException và lỗi mạng trong Java

Lập trình socket trong Java buộc chương trình đối mặt với nhiều trạng thái không hoàn toàn nằm trong quyền kiểm soát của mã ứng dụng: địa chỉ không phân giải được, server chưa lắng nghe, kết nối bị đóng, kết nối bị reset, không có dữ liệu đến trong khoảng thời gian cho phép, mạng không có đường đi, hoặc thao tác gửi đã được thực hiện nhưng phản hồi không tới. **`SocketException` và `SocketTimeoutException` là hai tên quan trọng, nhưng chúng không cùng một nhánh kế thừa trực tiếp**.

Những điểm cần nhớ ngay:

- **`java.net.SocketException`** kế thừa **`java.io.IOException`**; nó thường biểu diễn lỗi khi tạo, truy cập hoặc dùng socket. Các lớp con đáng biết gồm `ConnectException`, `BindException`, `NoRouteToHostException` và `PortUnreachableException`.
- **`java.net.SocketTimeoutException`** kế thừa **`java.io.InterruptedIOException`**, rồi mới tới **`IOException`**. Nó **không** kế thừa `SocketException`. Nó báo một thao tác socket đã vượt **thời hạn chờ** do API hỗ trợ, chẳng hạn kết nối, đọc dữ liệu, `ServerSocket.accept()` hoặc `DatagramSocket.receive()`.
- Cả hai là **checked exceptions** theo nghĩa Java: chúng nằm trong `IOException`, không thuộc `RuntimeException`. Phương thức gọi phải xử lý hoặc khai báo một loại checked exception đủ bao phủ chúng.
- **Timeout không đồng nghĩa với lỗi kết nối vĩnh viễn**, **EOF không đồng nghĩa với timeout**, **DNS failure không đồng nghĩa với connection refused**, và **socket closed không đồng nghĩa với mạng bị mất**.
- Cần phân biệt **lỗi kỹ thuật**, **mức retry có thể chấp nhận**, **idempotency của thao tác**, **trạng thái giao dịch** và **cách bảo toàn cause/stack trace**. Chỉ dựa vào message như `"Connection reset"` để quyết định retry là rất dễ sai.

Bài giải thích hợp đồng API, phân cấp, những tình huống thực tế, ví dụ Java 17 tự chạy bằng loopback không cần Internet, mẫu xử lý lỗi, giới hạn của timeout, kiểm thử và checklist khắc phục.

## 1. Sơ đồ kế thừa: phân biệt trước khi viết catch

Sơ đồ rút gọn các loại checked exception liên quan:

```text
java.lang.Throwable
└── java.lang.Exception
    └── java.io.IOException
        ├── java.net.SocketException
        │   ├── java.net.BindException
        │   ├── java.net.ConnectException
        │   ├── java.net.NoRouteToHostException
        │   └── java.net.PortUnreachableException
        ├── java.io.InterruptedIOException
        │   └── java.net.SocketTimeoutException
        ├── java.net.UnknownHostException
        ├── java.io.EOFException
        └── ... các lớp IOException khác
```

**Điểm thường bị nhầm nhất:** `SocketTimeoutException` không nằm dưới `SocketException`. Hai loại có thể đều phát sinh trong thao tác socket, nhưng thuộc **hai nhánh khác nhau** của `IOException`.

Hệ quả khi viết handler:

```java
try {
    communicate();
} catch (SocketTimeoutException e) {
    // Chính sách timeout.
} catch (SocketException e) {
    // Các lỗi socket khác.
} catch (IOException e) {
    // Các lỗi I/O còn lại.
}
```

Đây là **khối minh họa**, với method `communicate() throws IOException` trong phạm vi và các import `java.net`/`java.io` phù hợp. Cấu trúc này hợp lệ vì `IOException` đứng cuối, còn hai loại trước đều là nhánh con của nó.

Nếu chỉ viết `catch (SocketException e)` thì **không** bắt được `SocketTimeoutException`. Nếu dùng `catch (IOException e)` ở đầu, các handler subtype sau nó sẽ **không thể tới được** và compiler từ chối.

## 2. Ví dụ Java 17: kiểm chứng quan hệ kiểu bằng mã

Lưu **`SocketExceptionHierarchyDemo.java`**:

```java
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class SocketExceptionHierarchyDemo {
    public static void main(String[] args) {
        System.out.println("SocketException <: IOException: "
                + IOException.class.isAssignableFrom(
                        SocketException.class));

        System.out.println("Timeout <: IOException: "
                + IOException.class.isAssignableFrom(
                        SocketTimeoutException.class));

        System.out.println("Timeout <: InterruptedIOException: "
                + InterruptedIOException.class.isAssignableFrom(
                        SocketTimeoutException.class));

        System.out.println("Timeout <: SocketException: "
                + SocketException.class.isAssignableFrom(
                        SocketTimeoutException.class));

        System.out.println("ConnectException <: SocketException: "
                + SocketException.class.isAssignableFrom(
                        ConnectException.class));

        System.out.println("UnknownHostException <: SocketException: "
                + SocketException.class.isAssignableFrom(
                        UnknownHostException.class));
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 SocketExceptionHierarchyDemo.java
java SocketExceptionHierarchyDemo
```

**Stdout chính xác:**

```text
SocketException <: IOException: true
Timeout <: IOException: true
Timeout <: InterruptedIOException: true
Timeout <: SocketException: false
ConnectException <: SocketException: true
UnknownHostException <: SocketException: false
```

`Class.isAssignableFrom` kiểm tra quan hệ gán kiểu. Kết quả khẳng định một lỗi timeout có thể được bắt bằng `IOException` hoặc `InterruptedIOException` nhưng **không** bằng `SocketException`.

Nếu không muốn bắt nhiều loại riêng, `catch (IOException e)` là tầng dự phòng chung. Tuy nhiên, **bắt quá rộng không có nghĩa mọi lỗi I/O có cùng chính sách retry hoặc phục hồi**.

## 3. `SocketException` mô tả những dạng thất bại nào?

`SocketException` là checked exception được sử dụng trong các thao tác socket để phản ánh tình trạng I/O hoặc sử dụng socket không thành công. Những ví dụ ngoài thực tế có thể là:

- Socket đã đóng trong khi chương trình cố dùng nó.
- Một đầu TCP kết nối bị reset theo hành vi của hệ thống mạng.
- Có sự cố ở tầng socket khiến thao tác đọc/ghi/kết nối thất bại.
- Hệ điều hành hoặc stack mạng từ chối một thao tác liên quan đến socket.
- Một subclass báo tình huống cụ thể như **`ConnectException`** (kết nối bị từ chối hoặc không thiết lập được theo cơ chế phù hợp) hay **`BindException`** (không thể bind endpoint).

Nhưng **không nên áp dụng suy luận quá mạnh**:

- `SocketException` không luôn có nghĩa máy chủ đã tắt.
- `SocketException` không luôn có nghĩa phần cứng mạng hỏng.
- `SocketException` không cho biết chắc thao tác gửi ở phía server đã thực hiện hay chưa.
- Cùng một nguyên nhân vật lý có thể xuất hiện dưới loại hoặc message khác nhau tùy JVM, hệ điều hành, giao thức và bước đang thực hiện.
- Message như `"Connection reset"`, `"Broken pipe"` hay `"Socket closed"` là dấu hiệu hỗ trợ chẩn đoán, **không phải mã lỗi Java được tiêu chuẩn hóa trên mọi nền tảng**.

Cần quan sát **loại exception, thao tác đang thực hiện, socket lifecycle, địa chỉ, timeout cấu hình, thời điểm và kết quả nghiệp vụ**, chứ không chỉ đọc một đoạn message.

## 4. `SocketTimeoutException` mang nghĩa gì?

`SocketTimeoutException` được ném khi một thao tác socket có **thời hạn chờ** và thời hạn đó bị vượt trước khi thao tác đạt điều kiện hoàn tất do API xác định.

Các ngữ cảnh tiêu biểu:

1. **Kết nối:** `Socket.connect(endpoint, timeout)` chưa thiết lập kết nối kịp trong giới hạn.
2. **Đọc TCP:** `Socket.setSoTimeout(milliseconds)` đặt read timeout cho những thao tác đọc blocking thích hợp trên `SocketInputStream`.
3. **Chờ client:** `ServerSocket.setSoTimeout(milliseconds)` giới hạn thời gian `ServerSocket.accept()` chờ một kết nối mới.
4. **Nhận datagram:** `DatagramSocket.setSoTimeout(milliseconds)` giới hạn thời gian `receive(...)` chờ gói phù hợp.

Không nên nói “timeout = 2 giây thì toàn bộ request phải hoàn tất trong 2 giây” nếu chỉ đặt **read timeout**: đây là thời hạn của thao tác đọc chờ dữ liệu, không tự tạo một **deadline tổng** cho một quy trình gồm nhiều lần đọc, retry, parse và xử lý.

Khi timeout xảy ra, API trả exception; **không đồng nghĩa tài nguyên chắc chắn đã hỏng vĩnh viễn**. Với blocking socket read bị `SO_TIMEOUT`, tài liệu `Socket` nói socket vẫn hợp lệ sau `SocketTimeoutException`, nhưng tầng ứng dụng phải xác định còn có thể tiếp tục đọc không, message framing có nhất quán không và có rủi ro dữ liệu dở dang không.

## 5. Checked exception và nghĩa vụ catch-or-declare

Cả `SocketException` và `SocketTimeoutException` là lớp con của `IOException`, do đó thuộc nhóm **checked**. Ví dụ:

```java
static int receiveOneByte(Socket socket) throws IOException {
    return socket.getInputStream().read();
}
```

Đây là **phương thức minh họa**; cần import `java.net.Socket` và `java.io.IOException`. Vì `getInputStream` và `read` có thể ném checked `IOException`, method công bố `throws IOException` là một cách hợp lệ để cho caller chịu trách nhiệm.

Một caller có thể:

- `catch (SocketTimeoutException e)` để áp chính sách timeout.
- `catch (SocketException e)` cho lỗi socket cụ thể.
- `catch (IOException e)` cho lỗi I/O khác.
- Hoặc công bố `throws IOException` để đẩy lên tầng có trách nhiệm xử lý.

Không có `throws SocketTimeoutException` trong chữ ký không có nghĩa nó không thể xảy ra: một method có `throws IOException` vẫn cho phép các lớp con thích hợp. Ngược lại, viết `throws SocketException` **không bao phủ** `SocketTimeoutException` do chúng khác nhánh.

## 6. Các loại lỗi mạng cần phân biệt

| Loại hoặc tín hiệu | Tình huống thường gặp | Điều không nên kết luận |
| --- | --- | --- |
| `UnknownHostException` | Không phân giải được tên host theo cơ chế DNS/địa chỉ | Không phải `SocketException`; không đồng nghĩa server đã từ chối kết nối |
| `ConnectException` | Kết nối TCP không thiết lập được, thường gặp khi endpoint từ chối | Không có bằng chứng request nghiệp vụ đã được thực thi |
| `NoRouteToHostException` | Không tìm được đường mạng tới host hoặc lý do phù hợp ở tầng IP | Không giống timeout ứng dụng |
| `BindException` | Không bind được địa chỉ/cổng cục bộ | Không phải lỗi đọc dữ liệu của kết nối đã mở |
| `SocketTimeoutException` | Thao tác socket chờ quá giới hạn | Không tự chứng minh remote server đã chết |
| `SocketException` | Lỗi socket tổng quát hoặc trạng thái socket không dùng được | Không có một chính sách retry chung cho mọi message |
| `EOF` từ `InputStream.read() == -1` | Đầu gửi đóng output sạch sẽ và không còn byte để đọc | Không phải exception timeout |
| `EOFException` | Một API đọc dữ liệu có cấu trúc kỳ vọng thêm byte nhưng gặp EOF | Khác với giá trị `-1` của `InputStream.read()` |
| `SSLException` | Vấn đề thuộc tầng TLS/SSL khi dùng API tương ứng | Không phải mọi TLS failure đều là `SocketException` |
| `HttpTimeoutException` | Timeout theo hợp đồng `java.net.http.HttpClient` | Không phải subclass của `SocketTimeoutException` |

Tên exception là **tín hiệu bước đầu**. Muốn xử lý chính xác phải biết gọi API nào, thất bại ở bước nào và điều gì có thể đã xảy ra phía đối tác.

## 7. Timeout kết nối: `Socket.connect(endpoint, timeout)`

Một cách đặt timeout kết nối tường minh:

```java
try (Socket socket = new Socket()) {
    socket.connect(
            new InetSocketAddress(host, port),
            1500);
    // Đã kết nối thì mới sử dụng input/output stream.
}
```

Đây là **mã minh họa**, cần import `java.net.Socket` và `java.net.InetSocketAddress`, cùng `host`, `port` trong phạm vi. Phương thức chứa phải bắt hoặc khai báo `IOException` tương ứng.

Giá trị `1500` là **1.500 milliseconds**, tức 1,5 giây về mặt tham số. Nó là thời hạn chờ ở thao tác `connect`, không phải một cam kết tổng thời gian method không bao giờ vượt quá 1,5 giây trong mọi cấu hình DNS, scheduler và mạng.

Các kết quả có thể xảy ra:

- Kết nối thành công: `connect` trả bình thường.
- Timeout: có thể ném `SocketTimeoutException`.
- Endpoint từ chối: thường có thể ném `ConnectException`.
- Không có đường mạng: có thể ném `NoRouteToHostException`.
- Vấn đề socket hoặc I/O khác: có thể ném `SocketException` hoặc `IOException` phù hợp.

**Lưu ý về resolution:** tạo `InetSocketAddress(host, port)`, tra DNS và thực hiện kết nối là các khâu cần quan sát riêng; API socket này không nên được nhầm với một “deadline toàn ứng dụng bao gồm DNS, TLS, request và response”.

## 8. Ví dụ Java 17: mã phân loại lỗi kết nối không phụ thuộc Internet

Để có output xác định khi dạy về **nhánh catch**, ví dụ sau *mô phỏng* từng loại exception bằng cách khởi tạo chúng; nó **không tạo lỗi mạng thật**.

Lưu **`NetworkExceptionClassificationDemo.java`**:

```java
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class NetworkExceptionClassificationDemo {
    static void classify(IOException error) {
        try {
            throw error;
        } catch (SocketTimeoutException e) {
            System.out.println("TIMEOUT");
        } catch (ConnectException e) {
            System.out.println("CONNECT_REFUSED");
        } catch (NoRouteToHostException e) {
            System.out.println("NO_ROUTE");
        } catch (SocketException e) {
            System.out.println("SOCKET_IO");
        } catch (UnknownHostException e) {
            System.out.println("DNS");
        } catch (IOException e) {
            System.out.println("OTHER_IO");
        }
    }

    public static void main(String[] args) {
        classify(new SocketTimeoutException("simulated"));
        classify(new ConnectException("simulated"));
        classify(new NoRouteToHostException("simulated"));
        classify(new SocketException("simulated"));
        classify(new UnknownHostException("simulated"));
        classify(new IOException("simulated"));
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 NetworkExceptionClassificationDemo.java
java NetworkExceptionClassificationDemo
```

**Stdout chính xác:**

```text
TIMEOUT
CONNECT_REFUSED
NO_ROUTE
SOCKET_IO
DNS
OTHER_IO
```

Điểm học tập:

- `ConnectException` và `NoRouteToHostException` là lớp con của `SocketException` nên handler cụ thể **phải đứng trước** `catch (SocketException)`.
- `SocketTimeoutException` và `UnknownHostException` **không** thuộc nhánh `SocketException`.
- `IOException` cuối cùng xử lý các trường hợp còn lại.
- Có thể đổi thứ tự giữa các sibling catch nếu không gây che phủ kế thừa; quan trọng nhất là **subclass trước superclass**.

Không dựa vào `error.getMessage()` để phân loại. Khi ứng dụng cần mã lỗi có cấu trúc, nên ánh xạ dựa trên loại và **ngữ cảnh thao tác**, không chỉ subtype.

## 9. `Socket.setSoTimeout(...)` dùng cho **đọc**, không phải kết nối

Với `java.net.Socket`, `setSoTimeout(int timeout)` cấu hình giới hạn chờ đọc trên socket blocking. Ví dụ:

```java
socket.setSoTimeout(500);
int next = socket.getInputStream().read();
```

Đây là **mã minh họa**. Nếu không có dữ liệu hay EOF phù hợp trước giới hạn chờ, thao tác đọc có thể ném `SocketTimeoutException`.

Những lưu ý:

- Giá trị tính bằng **milliseconds**.
- `0` có ý nghĩa **timeout vô hạn** cho thao tác chờ liên quan (không chủ động giới hạn thời gian bằng SO_TIMEOUT).
- Giá trị âm không hợp lệ và `setSoTimeout` ném `IllegalArgumentException`.
- `setSoTimeout` phải được áp trước thao tác blocking cần giới hạn.
- `setSoTimeout` **không thay thế** `Socket.connect(endpoint, connectTimeout)`.
- Nó không tạo một deadline cho toàn bộ vòng đời socket hoặc mọi lần đọc nối tiếp.
- Với một lần `read()` bị timeout, socket thông thường vẫn hợp lệ, nhưng **không có bảo đảm giao thức ứng dụng đang ở trạng thái sạch** sau khi một request đã gửi nhưng chưa nhận đủ phản hồi.

Không dùng `InputStream.available() == 0` để khẳng định remote đã đóng hoặc không còn dữ liệu; `available()` chỉ là một ước lượng số byte có thể đọc **không blocking**, không phải tín hiệu EOF hay tình trạng kết nối.

## 10. Ví dụ tự chạy: read timeout thực sự trên loopback

Ví dụ này dùng **loopback** (`127.0.0.1` hoặc địa chỉ loopback tương đương của hệ thống), không cần server bên ngoài hay Internet. Nó chủ động kết nối hai đầu socket trong **cùng một tiến trình**, giữ đầu server không gửi byte trong lần đọc đầu, rồi gửi một byte để kiểm chứng client có thể đọc tiếp sau timeout.

Lưu **`SocketReadTimeoutDemo.java`**:

```java
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class SocketReadTimeoutDemo {
    public static void main(String[] args) throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();

        try (ServerSocket server =
                     new ServerSocket(0, 50, loopback);
             Socket client =
                     new Socket(loopback, server.getLocalPort());
             Socket accepted = server.accept()) {

            client.setSoTimeout(150);
            InputStream input = client.getInputStream();

            try {
                input.read();
                throw new AssertionError("Mong đợi read timeout");
            } catch (SocketTimeoutException e) {
                System.out.println("Read timed out");
            }

            accepted.getOutputStream().write(65);
            accepted.getOutputStream().flush();

            System.out.println("Byte sau timeout: " + input.read());
            System.out.println("Client vẫn mở: " + !client.isClosed());
        }
    }
}
```

Biên dịch/chạy với Java 17:

```bash
javac -encoding UTF-8 --release 17 SocketReadTimeoutDemo.java
java SocketReadTimeoutDemo
```

**Stdout mong đợi trên hệ thống cho phép TCP loopback**, khi không có can thiệp mạng ngoài vào cổng ngẫu nhiên:

```text
Read timed out
Byte sau timeout: 65
Client vẫn mở: true
```

Giải thích:

1. `new ServerSocket(0, 50, loopback)` mở cổng **ephemeral** do hệ điều hành chọn, bind riêng vào loopback.
2. `client` kết nối cổng đó; `server.accept()` trả socket phía server.
3. `client.setSoTimeout(150)` giới hạn mỗi thao tác đọc blocking tương ứng.
4. Vì `accepted` chưa gửi dữ liệu và chưa đóng output, lần `input.read()` đầu hết thời gian chờ rồi ném `SocketTimeoutException`.
5. Sau đó phía server gửi **giá trị byte 65**; lần `read()` tiếp theo trả `65`.
6. Timeout đọc **không tự động `close()` socket** trong ví dụ này.

**Đây là minh họa hành vi socket thật**, không phải output cứng đảm bảo trên môi trường chặn loopback hoặc JVM/hệ điều hành có lỗi. Không nên assert số milliseconds thực tế; scheduler và tải máy có thể ảnh hưởng thời điểm exception xuất hiện. Cổng được chọn động để tránh hard-code cổng.

## 11. Timeout đọc không có nghĩa EOF

Với một `InputStream` lấy từ TCP socket, một lời gọi đọc một byte thường có ba nhóm kết quả:

1. **Một byte được đọc thành công:** `read()` trả số `0..255`.
2. **Luồng kết thúc sạch và không còn dữ liệu:** `read()` trả `-1`.
3. **Thất bại I/O hoặc chờ quá giới hạn:** ném một `IOException` thích hợp, có thể là `SocketTimeoutException`.

Nếu đọc một byte mà nhận `-1`, đó **không phải `SocketTimeoutException`** và không nên retry cùng logic như timeout. Nó biểu thị đã nhận EOF từ luồng; với TCP thường liên quan peer đóng phía gửi output một cách có trật tự.

Đối với `read(byte[])`, kết quả còn phụ thuộc độ dài bộ đệm (yêu cầu đọc 0 byte có thể trả 0 ngay). Khi thiết kế giao thức, phải đọc đúng số byte theo framing và phân biệt đoạn thiếu dữ liệu với EOF.

Một exception như `EOFException` có thể xuất hiện trong các API đọc cấu trúc như `DataInputStream.readFully(...)` khi yêu cầu số byte cụ thể nhưng luồng kết thúc sớm. **Giá trị -1 của InputStream.read() không phải object `EOFException`.**

## 12. Ví dụ Java 17: EOF sạch không ném timeout

Lưu **`SocketEndOfStreamDemo.java`**:

```java
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketEndOfStreamDemo {
    public static void main(String[] args) throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();

        try (ServerSocket server =
                     new ServerSocket(0, 50, loopback);
             Socket client =
                     new Socket(loopback, server.getLocalPort());
             Socket accepted = server.accept()) {

            client.setSoTimeout(1000);

            accepted.shutdownOutput();
            InputStream input = client.getInputStream();

            System.out.println("read sau shutdownOutput: "
                    + input.read());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 SocketEndOfStreamDemo.java
java SocketEndOfStreamDemo
```

**Stdout mong đợi khi loopback hoạt động bình thường:**

```text
read sau shutdownOutput: -1
```

`accepted.shutdownOutput()` thực hiện half-close hướng **gửi** từ phía server. Khi phía client đọc và không có dữ liệu còn chờ, nó nhận EOF (`-1`). Đây là **kết thúc có thứ tự** thay vì chờ tới timeout.

Trong giao thức thực tế, EOF có thể là **kết thúc bình thường** hoặc **phản hồi thiếu dữ liệu** tùy framing. Ví dụ giao thức yêu cầu 100 byte mà peer đóng sau 20 byte thì đó là lỗi dữ liệu không đầy đủ ở tầng ứng dụng, dù dấu hiệu cấp stream là EOF.

## 13. `ServerSocket.accept()` cũng có thể timeout

`ServerSocket` là đầu socket lắng nghe kết nối TCP. Nếu cần server không đứng chờ mãi trong `accept()`, dùng:

```java
serverSocket.setSoTimeout(500);
Socket peer = serverSocket.accept();
```

Đây là **mã khung minh họa**. Timeout của `ServerSocket` áp vào **thao tác chờ kết nối mới**; nó **không** tự quy định read timeout trên `Socket` mà `accept()` trả về.

Khi `accept()` vượt thời gian chờ, nó ném `SocketTimeoutException`. Theo contract `ServerSocket`, **ServerSocket vẫn hợp lệ** sau timeout; chương trình có thể tiếp tục chờ lần khác, ngừng phục vụ hoặc kiểm tra cờ dừng.

**Phải tự đóng socket phía client được accept** khi công việc với client kết thúc, thường bằng try-with-resources hoặc cơ chế quản lý kết nối tương ứng. Timeout của listener và timeout của kết nối đã accept là hai chính sách tách biệt.

## 14. Ví dụ Java 17: listener hết thời gian chờ nhưng vẫn mở

Lưu **`ServerAcceptTimeoutDemo.java`**:

```java
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class ServerAcceptTimeoutDemo {
    public static void main(String[] args) throws Exception {
        try (ServerSocket server = new ServerSocket(
                0, 50, InetAddress.getLoopbackAddress())) {

            server.setSoTimeout(120);

            try (Socket ignored = server.accept()) {
                System.out.println("Có kết nối bất ngờ");
            } catch (SocketTimeoutException e) {
                System.out.println("Accept timed out");
            }

            System.out.println("Server vẫn mở: "
                    + !server.isClosed());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 ServerAcceptTimeoutDemo.java
java ServerAcceptTimeoutDemo
```

**Stdout mong đợi trong môi trường không có client kết nối tới cổng loopback vừa cấp:**

```text
Accept timed out
Server vẫn mở: true
```

Lỗi xảy ra tại `server.accept()`, không phải vì ServerSocket đã bị đóng. Hệ thống có thể tiếp tục gọi `accept()` nếu đó là chính sách vận hành.

Ví dụ dùng cổng ephemeral, không khởi tạo thread hay client, nên rất thích hợp để kiểm chứng hành vi chờ. Một môi trường không cho phép mở listener cục bộ có thể ném lỗi bind/quyền khác ngay trước bước timeout; đó không phải kết quả mong đợi của ví dụ.

## 15. `DatagramSocket.receive()` và UDP timeout

UDP khác TCP: không có kết nối TCP đảm bảo giao hàng và thứ tự byte stream theo cùng cách. `DatagramSocket.receive(packet)` chờ datagram tới socket; có thể cấu hình:

```java
datagramSocket.setSoTimeout(500);
datagramSocket.receive(packet);
```

Đây là **mã minh họa**; cần các biến `DatagramSocket` và `DatagramPacket`. Nếu không có datagram phù hợp trước thời hạn, có thể ném `SocketTimeoutException`.

Cần lưu ý:

- **UDP timeout không chứng minh gói đã bị mất ở đâu**: có thể sender không gửi, packet bị rớt, định tuyến có vấn đề hoặc thời hạn chờ quá ngắn.
- `DatagramSocket` có thể được kết nối logic đến một peer nhưng UDP vẫn **không** có phiên TCP như `Socket`.
- `PortUnreachableException` là một subclass của `SocketException`; trên UDP có thể liên quan ICMP port unreachable nhưng **không được đảm bảo xuất hiện ở mọi OS, mọi trường hợp**.
- Không suy rằng `send` UDP thành công đồng nghĩa peer đã nhận/xử lý dữ liệu.
- Muốn độ tin cậy, thứ tự và retry, giao thức ứng dụng phải chủ động thiết kế.

`DatagramSocket.setSoTimeout` chỉ điều chỉnh thao tác nhận chờ dữ liệu; nó không là một **deadline trọn vẹn** cho cơ chế retransmission tự xây.

## 16. Ví dụ Java 17: UDP receive timeout trên loopback

Lưu **`DatagramTimeoutDemo.java`**:

```java
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class DatagramTimeoutDemo {
    public static void main(String[] args) throws Exception {
        try (DatagramSocket receiver =
                new DatagramSocket(0, InetAddress.getLoopbackAddress())) {

            receiver.setSoTimeout(120);

            byte[] data = new byte[32];
            DatagramPacket packet = new DatagramPacket(data, data.length);

            try {
                receiver.receive(packet);
                System.out.println("Có gói dữ liệu");
            } catch (SocketTimeoutException e) {
                System.out.println("UDP receive timed out");
            }
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 DatagramTimeoutDemo.java
java DatagramTimeoutDemo
```

**Stdout mong đợi** khi không có tác nhân nào gửi datagram đến cổng mới được cấp:

```text
UDP receive timed out
```

Không có code gửi UDP trong chương trình, do đó `receive` chờ rồi timeout. Nếu môi trường cấm bind loopback hoặc có gói bất ngờ tới cổng đó, nhánh kết quả có thể khác; test thực tế cần điều kiện mạng ổn định.

Điểm chính là **cùng loại `SocketTimeoutException` có thể xuất hiện ở TCP read, ServerSocket accept và UDP receive**, nhưng mỗi nơi có ý nghĩa thao tác khác nhau.

## 17. `ConnectException`: kết nối thất bại, thường gặp connection refused

`ConnectException extends SocketException` và thường gặp khi client cố thiết lập kết nối đến cổng không có dịch vụ lắng nghe hoặc endpoint từ chối kết nối theo cơ chế OS.

Ví dụ code khung:

```java
try (Socket socket = new Socket()) {
    socket.connect(
            new InetSocketAddress("127.0.0.1", 65000),
            500);
} catch (ConnectException e) {
    // Không thiết lập được kết nối, thường do endpoint từ chối.
}
```

Đây là **mã minh họa**, **không có stdout cố định** và **không phải test xác định**: cổng 65000 vẫn có thể được sử dụng trên một máy cụ thể. Cần import `Socket`, `InetSocketAddress`, `ConnectException`; còn các `IOException` khác cũng cần xử lý hoặc khai báo.

Đừng chỉ nhìn message kiểu `"Connection refused"` rồi chắc chắn nguyên nhân là server process chưa chạy. Còn cần xét địa chỉ IP/port, listener bind trên giao diện nào, firewall, container/VM network namespace và chính sách bảo mật mạng.

Nếu muốn test thật việc từ chối kết nối, phải kiểm soát được endpoint và không dựa vào một port “chắc chắn trống” bằng suy đoán.

## 18. `BindException`: không gắn được endpoint cục bộ

Một server phải bind vào địa chỉ/cổng địa phương trước khi chấp nhận kết nối. Nếu cổng đã có listener khác hoặc địa chỉ bind không khả dụng, `new ServerSocket(port)` hay thao tác bind có thể ném **`BindException`**, một subclass của `SocketException`.

Các điểm dễ nhầm:

- **Bind failure** xảy ra khi chuẩn bị endpoint của **phía local**, không phải khi client đọc phản hồi.
- `BindException` có thể do cổng đã bị chiếm, địa chỉ không có trên host, quyền hệ thống hoặc chính sách mạng.
- Không nên giải thích mọi `BindException` bằng câu “server bên kia đã chết”.
- Khi server bind port `0`, OS chọn một cổng sẵn có; có thể đọc `serverSocket.getLocalPort()` sau khi bind thành công.

Trong ví dụ dùng loopback của bài, chọn port `0` để **hạn chế va chạm cổng** và tránh tự hứa rằng một port cố định không bị chiếm trên máy học viên.

## 19. `UnknownHostException`: lỗi phân giải tên, không phải kết nối đã bị từ chối

`UnknownHostException extends IOException`, **không thuộc `SocketException`**. Nó thường báo host name không phân giải được thành địa chỉ phù hợp theo cơ chế đang dùng.

Ví dụ:

```java
InetAddress resolved = InetAddress.getByName(hostName);
```

Đây là **mã minh họa** với `hostName` trong phạm vi và import `java.net.InetAddress`. Nếu resolution không thực hiện được, API có thể ném `UnknownHostException`.

Cần phân biệt các giai đoạn:

1. **DNS/name resolution**: xác định địa chỉ từ tên.
2. **Connect**: cố thiết lập kết nối tới địa chỉ/cổng.
3. **I/O**: gửi và nhận dữ liệu qua kết nối.
4. **Protocol/application**: nhận phản hồi đúng cú pháp và ý nghĩa.

Một lỗi ở bước 1 không chứng minh host đã nhận và từ chối kết nối ở bước 2. Một kết nối thành công ở bước 2 không chứng minh response nghiệp vụ sẽ tới ở bước 4.

**Đừng dựa vào hostname giả để tạo test DNS lỗi có stdout cố định**: DNS có cấu hình nội bộ, search domain, resolver hoặc môi trường mạng khác nhau. Nếu cần test logic phân loại, hãy tạo một `UnknownHostException` nhân tạo trong unit test như ở `NetworkExceptionClassificationDemo`.

## 20. Socket closed, reset và broken pipe

Những thông điệp thường gặp khi nghiên cứu lỗi socket:

- `Socket closed`: ứng dụng cố thao tác trên một socket đã bị đóng, hoặc tài nguyên đã được đóng trong khi thao tác đang chờ, tùy API/cách thực thi.
- `Connection reset`: kết nối TCP bị reset, có thể xảy ra do peer hoặc thành phần mạng.
- `Broken pipe`: thường liên quan thao tác ghi khi đầu bên kia không còn tiếp nhận theo tình trạng hệ điều hành.

Chú ý:

- **Thông điệp không phải enum/contract Java độc lập.** Wording có thể khác theo OS/JDK và bước thao tác.
- Một socket đã được đóng bởi chính code ứng dụng **không phải bằng chứng có lỗi hạ tầng mạng**.
- Việc `Socket.isClosed()` là `false` không cam kết peer còn sống hay lần I/O tiếp theo sẽ thành công.
- `Socket.isConnected()` phản ánh socket **đã từng kết nối thành công**, không phải một health check liên tục xác nhận peer đang trực tuyến.
- Một lời gọi `write` thành công ở phía client không tự chứng minh server đã xử lý yêu cầu ở tầng ứng dụng.
- Khi có reset/broken pipe, phải đánh giá **thao tác có thể đã được thực hiện một phần không**, trước khi retry.

Không nên thay thế mọi message lỗi bằng `SocketTimeoutException` hoặc tự ép chúng thành một trạng thái “timeout” duy nhất.

## 21. Timeout khác thế nào với server phản hồi chậm?

Nếu thao tác `read()` không nhận được dữ liệu trong thời hạn `SO_TIMEOUT`, Java có thể ném `SocketTimeoutException`. Nhưng nhiều nguyên nhân có thể dẫn tới cùng tín hiệu:

- Server đang thực hiện truy vấn chậm.
- Server đã nhận request nhưng chờ một phụ thuộc khác.
- Server không gửi response vì đang chờ bạn gửi thêm dữ liệu theo giao thức.
- Gói tin bị trì hoãn hoặc mất trên đường mạng.
- Peer đã gặp lỗi nhưng TCP chưa có tín hiệu đóng/reset mà bên nhận quan sát được.
- Timeout cấu hình quá ngắn so với độ trễ bình thường.

Vì vậy câu “SocketTimeoutException chứng minh server không xử lý request” là **sai**. Timeout chỉ xác nhận **điều kiện chờ của bên gọi bị vượt**. Trạng thái xử lý ở peer có thể **chưa được biết**.

Đối với thao tác có side effect như đặt hàng hoặc chuyển tiền, timeout chờ response không cho phép bạn kết luận thao tác chưa thực hiện. Phải có ID yêu cầu, idempotency, tra cứu trạng thái hoặc cơ chế bù trừ theo thiết kế.

## 22. Retry không đồng nghĩa với giải pháp cho mọi IOException

Khi có `SocketTimeoutException`, `ConnectException` hoặc `SocketException`, một thiết kế retry tốt phải trả lời:

1. **Lỗi xảy ra ở bước nào?** Trước khi gửi request, trong khi gửi, sau khi gửi, hay khi đọc response?
2. **Thao tác có idempotent không?** Đọc dữ liệu, PUT cập nhật trạng thái đã thiết kế, hay POST tạo giao dịch mới?
3. **Kết quả phía server có thể đã được commit chưa?**
4. **Có thời gian tối đa cho toàn bộ retry không?**
5. **Có giới hạn số lần thử và backoff/jitter không?**
6. **Có token/idempotency key để xử lý yêu cầu lặp lại không?**
7. **Có nguy cơ tạo retry storm làm hệ thống đang quá tải càng quá tải?**
8. **Có chính sách phân biệt lỗi DNS cấu hình sai, địa chỉ sai với lỗi tạm thời?**

Ví dụ an toàn hơn:

- Một request **đọc trạng thái** có thể thử lại có giới hạn trong chính sách đã thiết kế.
- Một lệnh **tạo giao dịch** có thể cần idempotency key và kiểm tra trạng thái giao dịch trước khi thử lại.
- `UnknownHostException` do tên miền cấu hình sai có thể không được khắc phục bằng retry liên tục.
- `BindException` vì server trùng port thường cần sửa cấu hình hoặc giải phóng port, không phải gửi cùng request đi nhiều lần.

Không dùng mã giả `catch (IOException e) { retryForever(); }` làm chiến lược mặc định.

## 23. Read timeout không phải deadline toàn request

Giả sử một ứng dụng đọc một response dài theo nhiều lần `read()`, mỗi lần đều có `SO_TIMEOUT = 2 giây`. Nếu peer gửi một lượng nhỏ dữ liệu trước mỗi khoảng hai giây, ứng dụng có thể tiếp tục hoạt động **lâu hơn hai giây tổng cộng**.

Do đó cần phân biệt:

- **Connect timeout**: giới hạn thời gian thiết lập kết nối trong API kết nối.
- **Read timeout / SO_TIMEOUT**: giới hạn chờ của một lần đọc blocking phù hợp.
- **Accept timeout**: giới hạn chờ kết nối mới cho listener.
- **Request deadline**: giới hạn tổng thời gian thực hiện tác vụ, do tầng ứng dụng/framework thiết kế.
- **Idle timeout**: giới hạn thời gian không có hoạt động, có thể được triển khai ở tầng server/proxy/framework.
- **TCP keepalive**: cơ chế phát hiện peer chết sau những khoảng thời gian lớn tùy cấu hình; **không phải** request timeout ngắn.

Đặt `setSoTimeout(2000)` **không** thay thế deadline tổng. Nếu có yêu cầu “response phải hoàn tất trong 5 giây”, hệ thống cần chính sách deadline theo đồng hồ đơn điệu, ngân sách cho các bước và cơ chế hủy/đóng khi hết hạn tương ứng.

## 24. `SocketTimeoutException` và `InterruptedIOException` không giống `InterruptedException`

Một nhầm lẫn tinh tế: `SocketTimeoutException extends InterruptedIOException`, nhưng điều đó **không** có nghĩa mỗi timeout là do thread bị interrupt.

- `InterruptedException` là checked exception thường liên quan tác vụ chờ bị tín hiệu interrupt, chẳng hạn `Thread.sleep()`.
- `InterruptedIOException` biểu thị thao tác I/O bị gián đoạn, có field `bytesTransferred` cho một số ngữ cảnh.
- `SocketTimeoutException` là một subclass của `InterruptedIOException` dùng để báo thao tác socket **hết thời hạn chờ**.
- **Không tự gọi `Thread.currentThread().interrupt()` chỉ vì bắt `SocketTimeoutException`.** Timeout không tự chứng minh thread đã nhận yêu cầu hủy.

Trong một số API I/O và channel khác, thread interruption có thể dẫn tới những exception chuyên biệt, chẳng hạn `java.nio.channels.ClosedByInterruptException`. Đừng ép tất cả thành `SocketTimeoutException`; hãy xử lý theo contract của API dùng thực tế.

Field `InterruptedIOException.bytesTransferred` không nên được coi là “tổng byte của toàn request đã được gửi thành công tới server” trong mọi tình huống. Nếu giao thức cần xác nhận chính xác, dùng cơ chế acknowledgment và state ở tầng ứng dụng.

## 25. Khung client TCP có timeout và đóng tài nguyên

Đây là một **phương thức minh họa**, đủ cú pháp Java nhưng không kèm server/`main`, nên không gắn stdout cố định. Nó minh họa cách cấu hình **connect timeout** riêng với **read timeout**, và quản lý socket bằng try-with-resources:

```java
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

class SimpleTcpClient {
    static int readOneByte(String host, int port) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(host, port),
                    1500);
            socket.setSoTimeout(2000);
            return socket.getInputStream().read();
        }
    }
}
```

Ý nghĩa:

1. `new Socket()` tạo socket chưa kết nối.
2. `connect(..., 1500)` áp thời hạn chờ riêng cho thiết lập TCP.
3. `setSoTimeout(2000)` giới hạn chờ lần đọc blocking sau đó.
4. `read()` trả byte hoặc `-1` khi EOF, hoặc ném lỗi I/O/timeout.
5. `try-with-resources` tự gọi `close()` khi thoát bình thường hoặc do exception.

**Không dùng nó như client giao thức hoàn chỉnh**: nó không tạo request, không xác nhận framing, không đọc response dài, không có deadline toàn tác vụ và chưa có retry policy.

Nếu phương thức thực tế cần trả về “không có dữ liệu”, trả `-1` có thể giữ nguyên hợp đồng InputStream; tầng caller phải hiểu `-1` khác với timeout.

## 26. Xử lý exception theo loại tại ranh giới có trách nhiệm

Một pattern phục vụ kiểm soát lỗi có thể là:

```java
try {
    performNetworkOperation();
} catch (SocketTimeoutException e) {
    handleTimeout(e);
} catch (UnknownHostException e) {
    handleNameResolutionFailure(e);
} catch (ConnectException e) {
    handleConnectFailure(e);
} catch (SocketException e) {
    handleOtherSocketFailure(e);
} catch (IOException e) {
    handleOtherIoFailure(e);
}
```

Đây là **mã khung minh họa** với các method xử lý cần được định nghĩa, import `java.io.*` và `java.net.*` tương ứng. Thứ tự quan trọng:

- `ConnectException` phải đứng trước `SocketException`.
- `SocketTimeoutException` và `UnknownHostException` không nằm dưới `SocketException`.
- `IOException` đứng cuối do là lớp cha chung.

Nhưng **không phải mọi tầng đều nên catch đủ sáu nhánh**. Nếu tầng repository không có cách phục hồi, nó có thể khai báo `throws IOException`; tầng API/job có thông tin request, policy và khả năng phản hồi sẽ xử lý.

Handler phải phân biệt **đã phục hồi thật sự** và **đang chuyển lỗi thành phản hồi thất bại**. Catch để ghi log rồi trả dữ liệu giả là một bug nếu caller nghĩ thao tác thành công.

## 27. Log có cấu trúc: lưu ngoại lệ chứ không chỉ message

Với lỗi mạng, chỉ in `e.getMessage()` thường không đủ vì:

- Message có thể khác theo JVM/OS.
- Nó không cho biết loại exception chính xác.
- Nó không luôn cho biết cause hoặc suppressed exceptions.
- Không có stack trace khi chỉ lưu một `String`.
- Không có ngữ cảnh thao tác: connect, read, write, accept hay DNS.
- Không có request ID và thông tin thời hạn chờ đang dùng.

Một log hữu ích nên bao gồm **địa chỉ/endpoint ở mức đã được phép lưu, tên thao tác, timeout cấu hình, attempt number, request correlation ID, loại exception và Throwable gốc**.

Pseudocode dành cho thư viện logging có overload nhận Throwable:

```java
// Pseudocode; chữ ký phụ thuộc logging framework đang dùng:
logger.error("Network operation failed: requestId={}, phase={}",
             requestId, phase, exception);
```

Đây **không** phải code Java chuẩn tự biên dịch nếu chưa có logger và các biến. Mục đích là nhấn mạnh: truyền **object exception** khi framework hỗ trợ, để giữ stack trace/cause/suppressed.

Không đưa access token, cookie, password, chuỗi truy vấn nhạy cảm hoặc payload cá nhân vào log chỉ để có thêm ngữ cảnh.

## 28. Bọc lỗi bằng cause khi đổi abstraction

Có thể một API nghiệp vụ không muốn phơi trực tiếp `IOException` mà dùng một custom exception:

```java
class NetworkOperationException extends RuntimeException {
    NetworkOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Ví dụ gọi:

```java
try {
    sendRequest();
} catch (IOException e) {
    throw new NetworkOperationException(
            "Không thực hiện được yêu cầu", e);
}
```

Cả hai là **đoạn minh họa**, `sendRequest()` và `IOException` cần có trong phạm vi, không gắn stdout.

Khi bọc:

- Exception wrapper mang **loại và thông điệp của tầng mới**.
- `wrapper.getCause()` giữ object `IOException`, kể cả `SocketTimeoutException` hay `ConnectException`.
- Stack trace của wrapper được ghi tại nơi tạo wrapper; cause giữ trace của nó.
- Mọi suppressed exceptions đang nằm trên cause **không tự chuyển** lên `wrapper.getSuppressed()`.
- API caller giờ thấy unchecked `NetworkOperationException`, khác với checked `IOException`.

**Không bọc chỉ bằng `new RuntimeException(e.getMessage())` nếu cần giữ nguyên nhân**. Lựa chọn checked hay unchecked cho wrapper phải là quyết định hợp đồng, không chỉ để tránh compiler bắt viết `throws`.

## 29. Ví dụ Java 17: bảo toàn cause từ timeout

Ví dụ này **mô phỏng timeout** để đầu ra không phụ thuộc môi trường mạng; nó tập trung vào wrapping/cause.

Lưu **`NetworkCauseDemo.java`**:

```java
import java.io.IOException;
import java.net.SocketTimeoutException;

public class NetworkCauseDemo {
    static class ServiceNetworkException extends RuntimeException {
        ServiceNetworkException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static void lowLevelRead() throws IOException {
        throw new SocketTimeoutException("read timed out");
    }

    static void fetch() {
        try {
            lowLevelRead();
        } catch (IOException e) {
            throw new ServiceNetworkException("Fetch thất bại", e);
        }
    }

    public static void main(String[] args) {
        try {
            fetch();
        } catch (ServiceNetworkException e) {
            System.out.println("Wrapper: " + e.getMessage());
            System.out.println("Cause class: "
                    + e.getCause().getClass().getSimpleName());
            System.out.println("Cause message: "
                    + e.getCause().getMessage());
            System.out.println("Cause là IOException: "
                    + (e.getCause() instanceof IOException));
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 NetworkCauseDemo.java
java NetworkCauseDemo
```

**Stdout chính xác:**

```text
Wrapper: Fetch thất bại
Cause class: SocketTimeoutException
Cause message: read timed out
Cause là IOException: true
```

`ServiceNetworkException` là một loại exception ứng dụng mới. `getCause()` vẫn giữ `SocketTimeoutException` và thông điệp gốc. Trong hệ thống thực, việc quyết định có retry hay không cần **ngữ cảnh**, không chỉ kiểm tra cause có `SocketTimeoutException`.

Nếu một lỗi network đã được bọc nhiều lần, tầng xử lý có thể cần duyệt cause chain cẩn thận (tránh vòng lặp do cấu trúc lỗi tùy chỉnh không chuẩn).

## 30. Try-with-resources và lỗi khi đóng socket

Cả `Socket` và `ServerSocket` đều hỗ trợ `AutoCloseable`, nên có thể dùng:

```java
try (Socket socket = new Socket()) {
    // connect, read, write...
}
```

Đây là **mã khung minh họa**. Khi rời scope, Java cố đóng socket. Nếu thao tác chính đã ném exception và `close()` cũng ném exception, Java có thể ghi lỗi đóng vào **suppressed exceptions** của lỗi chính, theo cơ chế try-with-resources.

Hệ quả:

- Khi chẩn đoán, có thể cần xem `primary.getSuppressed()` để biết cleanup cũng thất bại.
- `getCause()` và `getSuppressed()` không đồng nghĩa.
- Không tự nuốt lỗi cleanup chỉ để log trông sạch hơn.
- Khi socket hết khả năng sử dụng, đóng tài nguyên và tạo kết nối mới theo hợp đồng có thể đúng hơn cố giữ object cũ.
- `SocketTimeoutException` do **read SO_TIMEOUT** không tự bảo đảm socket đã hỏng hoặc đã đóng, nhưng tầng ứng dụng vẫn phải quản lý các điều kiện từ giao thức.

Không suy rằng chỉ vì socket được đặt trong try-with-resources thì mọi dữ liệu ứng dụng đã được server xác nhận hoặc mọi side effect đã rollback.

## 31. Những điều không thể suy từ trạng thái `Socket`

Một số method có tên dễ khiến người đọc lầm tưởng là kiểm tra “sức khỏe kết nối”:

- `socket.isConnected()`: cho biết socket đã từng kết nối thành công, **không** là probe liên tục xác nhận peer còn sống.
- `socket.isClosed()`: kiểm tra đối tượng socket đã bị đóng ở phía local hay chưa.
- `socket.isInputShutdown()` / `isOutputShutdown()`: trạng thái shutdown các hướng local.
- `input.available()`: ước lượng byte có thể đọc không blocking, **không** dùng để xác định EOF hoặc remote đã gửi xong.
- `socket.getSoTimeout()`: đọc cấu hình read timeout, **không** cho biết deadline tổng hoặc thời gian còn lại của một request.

Nếu cần **health check**, nên có cơ chế ở tầng protocol: ping/pong phù hợp, request kiểm tra sức khỏe, heartbeat, hoặc xác thực kết quả I/O thực tế; vẫn phải chấp nhận mạng có độ trễ và lỗi.

Không có API đồng bộ nào có thể cam kết trạng thái peer **sẽ vẫn đúng** tại thời điểm thao tác tiếp theo trong một hệ thống phân tán.

## 32. Phân biệt với Java NIO và Java HTTP Client

### 32.1. Java NIO channels

`java.nio.channels.SocketChannel` và các channel khác có API I/O riêng; tùy tình huống có thể xuất hiện những loại exception như:

- `ClosedChannelException`,
- `AsynchronousCloseException`,
- `ClosedByInterruptException`,
- các `IOException` thích hợp.

**Đừng mặc định rằng `Socket.setSoTimeout` điều khiển mọi thao tác NIO non-blocking hoặc selector**. Trong thiết kế NIO, timeout thường được quản lý thông qua selector, deadline hoặc API tầng cao phù hợp. Xem hợp đồng cụ thể của channel và chế độ blocking/non-blocking.

### 32.2. `java.net.http.HttpClient`

Java 11+ có HTTP Client chuẩn `java.net.http.HttpClient`. Timeout theo API này có exception chuyên biệt:

- `java.net.http.HttpTimeoutException`: thuộc nhánh `IOException`.
- `java.net.http.HttpConnectTimeoutException`: là lớp con của `HttpTimeoutException`, dùng trong ngữ cảnh connect timeout của HTTP Client.

Các loại này **không phải `SocketTimeoutException`**. Nếu đang dùng `HttpClient`, phải dựa vào **API và loại exception của HTTP Client**, không áp mã catch dành riêng cho classic `Socket` lên tất cả lời gọi HTTP.

Một số thư viện HTTP khác có cơ chế wrapper, retry, async exception riêng; tài liệu của thư viện luôn quan trọng hơn suy luận từ các lớp socket thô.

## 33. Lỗi TLS/SSL là một lớp khác

Khi dùng `SSLSocket` hoặc HTTPS, có thêm bước **TLS handshake**, xác minh chứng chỉ và giao thức mã hóa. Những lỗi có thể xuất hiện bao gồm `javax.net.ssl.SSLException` và các lớp con, cùng một số lỗi socket/I/O thấp hơn.

Không được kết luận:

- Có `SSLException` nghĩa là DNS sai.
- Có `SocketTimeoutException` nghĩa là chứng chỉ TLS không hợp lệ.
- TCP connect thành công là TLS handshake chắc chắn thành công.
- Timeout TCP read tương đương timeout toàn bộ handshake và HTTP response trong mọi API.

Trong hệ thống thật, mỗi tầng cần ghi ngữ cảnh: **resolve → connect → TLS handshake → write request → read response → parse**. Điều này giúp phân biệt lỗi cấu hình, lỗi mạng và lỗi protocol.

Nếu mục tiêu chỉ là hiểu `SocketException`/`SocketTimeoutException` ở Java core, hãy coi TLS/HTTP là **tầng bổ sung**, không nhầm các exception của chúng với toàn bộ các dạng socket errors.

## 34. Không cố dựa vào message hệ điều hành để viết test

Một test kiểu:

```java
assertEquals("Connection refused", exception.getMessage());
```

là **đoạn minh họa phản ví dụ** (giả định framework assert). Message có thể khác giữa Linux, Windows, JDK và ngữ cảnh. Những kiểm tra ổn định hơn:

- Loại exception hoặc superclass phù hợp.
- Mã lỗi ở **tầng domain** nếu ứng dụng tự định nghĩa.
- Cause của wrapper khi chuyển abstraction.
- Số lần retry hay chính sách fallback được thực hiện.
- Trạng thái tài nguyên sau failure.
- Giới hạn timeout ở mức hợp đồng, tránh assert con số thời gian chính xác tuyệt đối.

Khi muốn test phân loại lỗi, có thể **tạo exception nhân tạo** như `new ConnectException("simulated")`. Khi cần integration test socket, dùng **loopback và cổng ephemeral** để giảm phụ thuộc mạng, nhưng vẫn phải đánh dấu những giả định môi trường.

## 35. Test timeout và tránh flaky test

Timeout test dễ không ổn định nếu phụ thuộc scheduler hoặc tải máy. Những cách giảm rủi ro:

1. Tách **unit test logic** khỏi **integration test thực sự tạo socket**.
2. Unit test bằng exception nhân tạo nếu đang kiểm tra chính sách phân loại/retry.
3. Với socket thật, dùng **loopback** và `ServerSocket(0)` thay vì dịch vụ Internet hoặc port cố định.
4. Không kiểm tra `elapsedMillis == configuredTimeout`; thời gian thực có overhead.
5. Không ngủ mù quáng để chờ một thread chưa chắc đã bắt đầu; dùng `CountDownLatch` hoặc cơ chế đồng bộ khi cần threads.
6. Không biến một timeout test thành deadlock vì client/server cùng chờ nhau theo giao thức sai.
7. Đảm bảo đóng resource trong `try-with-resources`, kể cả khi test thất bại.
8. Tránh “port chắc chắn không ai dùng”; các port luôn chịu race và cấu hình môi trường.
9. Nếu cần kiểm thử retry, dùng server giả có điều khiển request ID và hành vi phản hồi, không phụ thuộc lỗi Internet ngẫu nhiên.
10. Dành thời hạn hợp lý cho CI và ghi rõ test cần khả năng bind/connect loopback.

**Test socket thực phản ánh runtime/network behavior; unit test nhân tạo phản ánh code phân loại.** Cả hai có giá trị nhưng không nên thay thế khái niệm của nhau.

## 36. Những sai lầm phổ biến

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “`SocketTimeoutException extends SocketException`.” | Sai. Nó extends `InterruptedIOException`, còn `SocketException` extends `IOException` trực tiếp. |
| “Bắt `SocketException` sẽ bắt mọi timeout.” | Sai. Cần bắt `SocketTimeoutException` hoặc `IOException` phù hợp. |
| “`ConnectException` là nhánh riêng ngoài SocketException.” | Sai. `ConnectException` là subclass của `SocketException`. |
| “`UnknownHostException` là subclass SocketException.” | Sai. Nó thuộc `IOException`, không nằm dưới SocketException. |
| “Timeout nghĩa là server chắc chắn không xử lý request.” | Sai. Thao tác có thể đã được xử lý nhưng phản hồi chưa tới. |
| “Read timeout là deadline cho toàn bộ request.” | Sai. SO_TIMEOUT liên quan thao tác đọc blocking, không tự giới hạn toàn workflow. |
| “`setSoTimeout` đặt timeout của `connect`.” | Sai. Dùng `connect(endpoint, timeout)` cho kết nối. |
| “`SocketTimeoutException` luôn khiến socket đóng.” | Sai. Read timeout trên socket blocking không tự đóng socket. |
| “`Socket.isConnected() == true` nghĩa là peer đang online.” | Không. Nó không phải health check realtime. |
| “`available() == 0` nghĩa là đã EOF.” | Sai. Nó không chứng minh peer đóng hay stream kết thúc. |
| “`read() == -1` nghĩa là timeout.” | Sai. `-1` là EOF, khác exception timeout. |
| “Socket reset và socket closed đều là lỗi DNS.” | Sai. Phải xem thời điểm, loại lỗi và thao tác. |
| “UDP send thành công bảo đảm dữ liệu đã nhận.” | Sai. UDP không có đảm bảo ấy theo mặc định. |
| “Mọi timeout nên retry ngay mãi mãi.” | Sai. Cần giới hạn, backoff, idempotency và deadline tổng. |
| “Read timeout có nghĩa phải gọi `Thread.interrupt()`.” | Sai. Timeout không phải InterruptedException của thread. |
| “Message `Connection reset` có thể dùng làm mã lỗi ổn định.” | Không. Message phụ thuộc runtime/hệ điều hành và tình huống. |
| “Java HTTP Client timeout luôn ném SocketTimeoutException.” | Không. `java.net.http` có `HttpTimeoutException`/`HttpConnectTimeoutException`. |
| “Chỉ log message đã đủ để điều tra socket error.” | Thường không; cần Throwable, cause và ngữ cảnh. |
| “Catch IOException rồi trả thành công giúp ứng dụng bền hơn.” | Sai nếu chưa phục hồi đúng; đó có thể là thành công giả. |
| “Có try-with-resources là server đã xử lý giao dịch an toàn.” | Không. Nó quản lý đóng resource, không xác nhận side effect ở peer. |

## 37. Câu hỏi tự kiểm tra

**1.** `SocketException` có checked không?  
**Đáp án:** Có, vì kế thừa `IOException`.

**2.** `SocketTimeoutException` có phải subclass của `SocketException`?  
**Đáp án:** Không. Nó là subclass của `InterruptedIOException`, cũng thuộc `IOException`.

**3.** Catch `SocketException` có bắt được `ConnectException` không?  
**Đáp án:** Có, vì `ConnectException extends SocketException`.

**4.** Catch `SocketException` có bắt được `UnknownHostException` không?  
**Đáp án:** Không. `UnknownHostException` nằm dưới `IOException` theo nhánh khác.

**5.** Dùng API nào để đặt timeout kết nối classic `Socket`?  
**Đáp án:** `socket.connect(endpoint, timeoutMillis)`.

**6.** Dùng API nào để giới hạn thời gian chờ `Socket.getInputStream().read()`?  
**Đáp án:** `socket.setSoTimeout(timeoutMillis)` trước thao tác đọc blocking phù hợp.

**7.** `ServerSocket.setSoTimeout` ảnh hưởng thao tác nào?  
**Đáp án:** Thời gian `ServerSocket.accept()` chờ kết nối mới.

**8.** `DatagramSocket.setSoTimeout` được dùng với thao tác nào?  
**Đáp án:** `DatagramSocket.receive(...)`.

**9.** Sau một read timeout, socket có chắc đã bị đóng không?  
**Đáp án:** Không. Timeout đọc không tự buộc đóng socket; ứng dụng phải quyết định tiếp tục hay đóng theo protocol.

**10.** `InputStream.read()` trả `-1` khác gì `SocketTimeoutException`?  
**Đáp án:** `-1` là EOF; timeout là exception do hết thời gian chờ thích hợp.

**11.** `ConnectException` thường gắn với giai đoạn nào?  
**Đáp án:** Thiết lập kết nối, thường gặp khi endpoint từ chối hoặc không thể thiết lập kết nối theo cơ chế OS.

**12.** `BindException` xảy ra ở bên client đọc dữ liệu hay lúc chuẩn bị endpoint local?  
**Đáp án:** Chủ yếu ở thao tác bind endpoint local.

**13.** DNS/name resolution failure có nhất thiết là `SocketException`?  
**Đáp án:** Không. `UnknownHostException` là một subclass riêng của `IOException`.

**14.** `setSoTimeout(0)` có nghĩa “ngay lập tức timeout” không?  
**Đáp án:** Không. `0` có nghĩa không đặt giới hạn chờ SO_TIMEOUT cho thao tác liên quan.

**15.** Nếu server đã xử lý một lệnh tạo giao dịch nhưng client timeout khi chờ response, có thể retry vô điều kiện không?  
**Đáp án:** Không. Cần idempotency key, kiểm tra trạng thái và chính sách retry có giới hạn.

**16.** `Socket.isConnected()` có là health check peer hiện tại không?  
**Đáp án:** Không. Nó phản ánh trạng thái đã từng kết nối, không xác nhận peer còn phục vụ.

**17.** Tại sao không dùng `InputStream.available() == 0` để kết luận EOF?  
**Đáp án:** Nó chỉ báo số byte có thể đọc không blocking theo một ước lượng; có thể chưa có dữ liệu nhưng stream chưa kết thúc.

**18.** `SocketTimeoutException` kế thừa `InterruptedIOException` có nghĩa thread phải bị interrupt không?  
**Đáp án:** Không. Timeout là điều kiện chờ, không tự chứng minh thread đã nhận tín hiệu interrupt.

**19.** Khi Java HTTP Client có timeout, những loại nào có thể liên quan?  
**Đáp án:** `HttpTimeoutException` và `HttpConnectTimeoutException` theo hợp đồng `java.net.http`, không mặc định là SocketTimeoutException.

**20.** Vì sao cần dùng `getCause()` khi bọc lỗi network thành domain exception?  
**Đáp án:** Để giữ ngoại lệ gốc, loại lỗi và trace, tránh mất thông tin khi đổi abstraction.

**21.** Hai lần gọi `read()` lần lượt nhận dữ liệu trước mỗi read timeout, nhưng tổng thời gian vượt deadline nghiệp vụ. SO_TIMEOUT có tự dừng request không?  
**Đáp án:** Không nhất thiết. Cần deadline tổng ở tầng ứng dụng.

**22.** Khi debug lỗi mạng, chỉ biết message `"Broken pipe"` đã đủ chưa?  
**Đáp án:** Không. Cần thao tác đang gọi, trạng thái connection, cause, OS/JDK và kết quả nghiệp vụ.

**23.** Vì sao test kết nối tới port “chắc chắn trống” không đáng tin?  
**Đáp án:** Một tiến trình khác có thể sử dụng port, tạo race và sự khác biệt giữa các môi trường.

**24.** Có thể dùng loopback để tự học timeout mà không cần Internet không?  
**Đáp án:** Có, với `ServerSocket(0)` và `Socket` cùng tiến trình, nếu hệ thống cho phép TCP loopback.

## 38. Checklist xử lý lỗi mạng

Trước khi thiết kế hoặc review một lời gọi Java networking:

1. **Xác định API đang dùng:** classic `Socket`, `ServerSocket`, `DatagramSocket`, NIO hay HTTP Client.
2. **Nắm phân cấp exception:** `SocketTimeoutException` không nằm trong `SocketException`.
3. **Ghi rõ giai đoạn:** resolve, bind, connect, TLS, write, read, accept hay receive.
4. **Phân biệt timeout và EOF:** `read() == -1` không phải SocketTimeoutException.
5. **Đặt connect timeout nếu cần:** qua `connect(endpoint, timeout)`.
6. **Đặt read timeout riêng:** qua `Socket.setSoTimeout` khi dùng classic blocking socket.
7. **Đặt accept timeout riêng cho listener:** qua `ServerSocket.setSoTimeout` khi phù hợp.
8. **Nhớ timeout không tự là deadline tổng:** thiết kế request budget/deadline nếu cần.
9. **Không dùng `isConnected()` hay `available()` như health check đáng tin tuyệt đối.**
10. **Đánh giá idempotency trước retry:** request có thể đã được thực thi bên server.
11. **Giới hạn retry và dùng backoff/jitter hợp lý.**
12. **Giữ request ID/correlation ID và trạng thái server** để phân biệt kết quả chưa rõ.
13. **Không nuốt exception rồi trả thành công giả.**
14. **Khi wrapping, truyền cause:** `new XxxException(message, cause)`.
15. **Sử dụng try-with-resources** để đóng socket đúng vòng đời.
16. **Quan sát suppressed exception** nếu thao tác chính và cleanup cùng thất bại.
17. **Không phân loại chỉ bằng message OS/JDK.**
18. **Tránh log credential, token, query/payload nhạy cảm.**
19. **Phân biệt UDP với TCP:** không giả định UDP có xác nhận giao hàng.
20. **Xử lý thread interruption riêng** với timeout socket.
21. **Viết unit test loại lỗi không phụ thuộc mạng**, integration test loopback riêng.
22. **Không khẳng định thời gian chờ chính xác tuyệt đối trong test.**
23. **Nếu dùng HttpClient/NIO, đọc contract riêng**, không áp quy tắc Socket cũ máy móc.
24. **Rà tình huống đồng thời:** socket có thể bị đóng bởi thread khác; kiểm tra trạng thái không loại bỏ race.
25. **Công bố thất bại ở đúng ranh giới**: API cần xử lý hoặc khai báo checked exception theo trách nhiệm của từng tầng.

## 39. Kết luận

`SocketException` và `SocketTimeoutException` cùng thuộc họ checked `IOException` nhưng **khác nhánh và khác ý nghĩa**. `SocketException` biểu thị một nhóm lỗi socket nói chung và có những subclass như `ConnectException`, `BindException`, `NoRouteToHostException`. `SocketTimeoutException` kế thừa `InterruptedIOException` và báo một thao tác socket chờ quá giới hạn thời gian cấu hình; **`catch (SocketException)` không bắt được nó**.

Khi lập trình TCP/UDP thực tế, hãy đặt timeout đúng tầng: **connect timeout** cho kết nối, **SO_TIMEOUT** cho đọc hoặc nhận/accept thích hợp; phân biệt **EOF (`-1`)** với timeout và không coi message hệ điều hành là mã lỗi ổn định. Đọc/ghi socket còn phụ thuộc vào giao thức, trạng thái tài nguyên và khả năng request đã có side effect phía server.

Thiết kế xử lý tốt không dừng ở việc catch đúng loại: cần **deadline tổng, idempotency, retry có giới hạn, logging đủ ngữ cảnh, bảo toàn cause, quản lý resource và kiểm thử tách biệt logic lỗi khỏi môi trường mạng**. Đây là nền tảng để dùng Java networking an toàn và chẩn đoán sự cố đáng tin.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="api-exceptions-005">SQLException: cause, SQLState và chuỗi nextException</a>

<details>
<summary>Xem chi tiết</summary>

# `SQLException`: cause, SQLState và chuỗi `nextException` trong Java JDBC

`java.sql.SQLException` là ngoại lệ **checked** mà JDBC dùng để báo nhiều loại thất bại trong thao tác với cơ sở dữ liệu: lỗi kết nối, thực thi SQL, ràng buộc dữ liệu, transaction hoặc sự cố do JDBC driver báo. Khác với một `Exception` thông thường chỉ có message/cause, `SQLException` còn có **SQLState**, **vendor error code** và một **chuỗi `SQLException` bổ sung** truy cập qua `getNextException()`.

Điểm mấu chốt của bài học:

> Một `SQLException` có thể đồng thời có **cause**, **nextException** và **suppressed exceptions**. Đây là **ba quan hệ khác nhau**. Đọc riêng `getCause()` thường chưa đủ để hiểu mọi lỗi JDBC mà driver hoặc thao tác dọn dẹp đã báo.

Ví dụ:

~~~java
SQLException error = new SQLException(
        "Không thể hoàn tất giao dịch",
        "40001",
        1205,
        new java.io.IOException("socket read failed"));

error.setNextException(
        new SQLException("duplicate key", "23505", 1062));
~~~

Đây là một mảnh Java minh họa trong phạm vi có import `java.sql.SQLException`. `error` có:

- `getMessage()`: thông điệp của lỗi chính, `"Không thể hoàn tất giao dịch"`.
- `getSQLState()`: chuỗi `"40001"`, mô tả nhóm/điều kiện theo hệ thống mã SQLState.
- `getErrorCode()`: số `1205`, là **mã được định nghĩa theo vendor**, không phải mã lỗi Java chung.
- `getCause()`: đối tượng `IOException` có message `"socket read failed"`.
- `getNextException()`: một **`SQLException` khác** mang SQLState `23505`, thông điệp và mã riêng.

Những mã trên là dữ liệu **giả lập cho mục đích học tập**; không thể từ một ví dụ tự tạo suy ra mọi JDBC driver đều trả cùng mã vendor hoặc cùng cấu trúc chuỗi.

## 1. Vị trí của `SQLException` trong phân cấp ngoại lệ

~~~text
java.lang.Object
└── java.lang.Throwable
    └── java.lang.Exception
        └── java.sql.SQLException
            ├── java.sql.SQLTransientException
            │   ├── java.sql.SQLTimeoutException
            │   ├── java.sql.SQLTransientConnectionException
            │   └── java.sql.SQLTransactionRollbackException
            ├── java.sql.SQLNonTransientException
            │   ├── java.sql.SQLIntegrityConstraintViolationException
            │   └── java.sql.SQLSyntaxErrorException
            ├── java.sql.SQLRecoverableException
            ├── java.sql.BatchUpdateException
            └── java.sql.SQLWarning
~~~

Đây là cây **rút gọn** các nhánh tiêu biểu, không phải danh sách đầy đủ.

`SQLException extends Exception` và không kế thừa `RuntimeException`, nên nó là **checked exception**. Nếu một phương thức gọi API JDBC có thể ném `SQLException`, code phải bắt hoặc khai báo đúng quy tắc checked exception:

~~~java
static int executeUpdate(java.sql.Statement statement, String sql)
        throws SQLException {
    return statement.executeUpdate(sql);
}
~~~

Đoạn trên là phương thức Java hợp lệ khi import `SQLException`. Việc khai báo `throws SQLException` chỉ mô tả khả năng lỗi, **không có nghĩa mọi lần gọi đều thất bại**.

`SQLException` còn triển khai `Iterable<Throwable>`, cho phép duyệt thông tin JDBC có liên quan bằng vòng `for-each`. Điểm này được trình bày riêng sau khi làm rõ hai kiểu liên kết cause và next.

### Các lớp con nói lên điều gì?

- `SQLTransientException`: nhóm lỗi mà tác vụ **có thể** thành công nếu thực hiện lại trong điều kiện phù hợp; không phải bảo đảm retry luôn an toàn.
- `SQLNonTransientException`: nhóm lỗi mà retry y hệt không được kỳ vọng thành công nếu không sửa nguyên nhân.
- `SQLRecoverableException`: nhóm lỗi có thể cần một thao tác khôi phục, ví dụ tái tạo kết nối, trước khi thử lại.
- `SQLIntegrityConstraintViolationException`: lỗi vi phạm ràng buộc toàn vẹn.
- `SQLSyntaxErrorException`: lỗi cú pháp SQL hoặc truy cập không hợp lệ được API/driver biểu diễn bằng subclass.
- `SQLTimeoutException`: một subtype của `SQLTransientException` báo timeout theo hợp đồng JDBC tương ứng.

Không phải driver nào cũng phân loại mọi sự cố giống nhau. **Đọc cả subtype, SQLState, vendor code và tài liệu driver** trước khi tự động quyết định khôi phục hoặc retry.

## 2. Bốn thông tin chẩn đoán chính trên một `SQLException`

Xét constructor:

~~~java
SQLException e = new SQLException(
        "update failed", "23505", 1062);
~~~

Các accessor:

| API | Kiểu trả về | Ý nghĩa |
| --- | --- | --- |
| `e.getMessage()` | `String` | Thông điệp (*reason*) của ngoại lệ |
| `e.getSQLState()` | `String` | Mã SQLState nếu có |
| `e.getErrorCode()` | `int` | Mã lỗi riêng của database vendor |
| `e.getCause()` | `Throwable` | Nguyên nhân trực tiếp nếu có |
| `e.getNextException()` | `SQLException` | `SQLException` kế tiếp trong chuỗi nếu có |
| `e.getSuppressed()` | `Throwable[]` | Lỗi phụ được giữ, thường khi dọn dẹp tài nguyên |

**`getMessage()` không phải SQLState.** Một message như `"duplicate key"` là văn bản dành cho người đọc; `23505` là một mã có ý nghĩa theo quy ước SQLState của môi trường cụ thể. Vendor code `1062` lại thuộc hệ mã khác. Không trộn ba giá trị này thành một “mã lỗi chung”.

### Giá trị có thể thiếu

~~~java
SQLException e = new SQLException("unknown failure");

System.out.println(e.getSQLState()); // null
System.out.println(e.getErrorCode()); // 0
System.out.println(e.getNextException()); // null
~~~

Các giá trị mặc định ở ví dụ là thuộc tính của constructor được dùng, **không chứng minh** rằng mọi `SQLException` có vendor code `0` đều không có sự cố. Một driver có thể dùng `0` theo quy ước riêng; SQLState có thể là null khi không được cung cấp.

Đừng gọi `e.getSQLState().substring(0, 2)` trước khi kiểm tra null và độ dài.

## 3. `getCause()`: quan hệ nguyên nhân kiểu `Throwable`

`SQLException` kế thừa cơ chế cause từ `Throwable`:

~~~java
java.io.IOException root =
        new java.io.IOException("TCP connection reset");

SQLException sqlError = new SQLException(
        "database request failed",
        "08006",
        0,
        root);

System.out.println(sqlError.getCause() == root);
// true
~~~

Cause ở đây là **chính đối tượng `IOException`**, không phải chỉ bản sao chuỗi `getMessage()`. Cause có thể là bất kỳ subtype nào của `Throwable`, không nhất thiết là `SQLException`.

Mối quan hệ:

~~~text
SQLException: database request failed
    └── cause -> IOException: TCP connection reset
~~~

Nó giải thích “thất bại JDBC này bắt nguồn từ điều gì?”. Khi wrapper đi qua nhiều tầng:

~~~java
IllegalStateException serviceError =
        new IllegalStateException(
                "Không thể khởi động dịch vụ", sqlError);
~~~

chuỗi cause sẽ là:

~~~text
IllegalStateException
    └── cause -> SQLException
                   └── cause -> IOException
~~~

Constructor nhận `cause` là lựa chọn được ưu tiên khi lớp hỗ trợ. Chỉ sao chép `sqlError.getMessage()` vào message wrapper **không** bảo toàn tham chiếu và stack trace của nguyên nhân.

### `getCause()` chỉ truy cập một mắt xích

Nếu muốn biết lỗi gốc sâu nhất, phải lần lượt đọc cause. Tuy nhiên đừng mặc định một thuật toán duyệt các `Throwable` tùy biến sẽ không có vòng gián tiếp; tiện ích phân tích lỗi tổng quát nên có bảo vệ vòng lặp bằng **object identity**.

**Không đồng nhất “cause sâu nhất” với “lỗi duy nhất cần xử lý”.** Một `SQLException` ở giữa chuỗi còn giữ SQLState và vendor code, những dữ liệu mà `IOException` sâu nhất không có.

## 4. `getNextException()`: chuỗi riêng của JDBC

API:

~~~java
public SQLException getNextException()
public void setNextException(SQLException ex)
~~~

Đây là chữ ký minh họa. `getNextException()` trả `SQLException` kế tiếp đã được liên kết; nếu không có, trả `null`. `setNextException(ex)` **thêm ngoại lệ vào cuối chuỗi**.

Ví dụ:

~~~java
SQLException first =
        new SQLException("batch failed", "40001", 10);
SQLException second =
        new SQLException("row rejected", "23505", 11);
SQLException third =
        new SQLException("statement timeout", "HYT00", 12);

first.setNextException(second);
first.setNextException(third);
~~~

Sau đó:

~~~text
first
  └── nextException -> second
                          └── nextException -> third
                                                  └── null
~~~

Thao tác `first.setNextException(third)` **không thay thế** `second` đã gắn trước. Nó thêm `third` vào **cuối** chuỗi.

### Vì sao JDBC cần chuỗi này?

Một thao tác database có thể sinh nhiều thông tin lỗi, chẳng hạn:

- Lệnh đầu tiên thất bại và driver trả thêm chẩn đoán từ server.
- Một batch gồm nhiều statement gặp nhiều vấn đề cần được trình bày cùng một lỗi tổng.
- Driver và database cung cấp một tập hợp lỗi với SQLState/vendor code khác nhau.

Chuỗi `nextException` cho phép một lỗi JDBC tổng **chứa các `SQLException` khác**, mỗi lỗi có message, SQLState, vendor code và cause riêng. Nhưng **không phải mọi driver hay mọi thao tác đều tạo nhiều phần tử**. Chuỗi rỗng là tình huống bình thường.

### Đặc điểm khác cause

`getNextException()` yêu cầu một `SQLException` khác; `getCause()` có thể là `IOException`, `IllegalStateException` hoặc `Throwable` khác. Một đối tượng SQLException có thể có **cả hai** mà không có mối liên hệ bắt buộc giữa chúng.

## 5. Cause, nextException và suppressed khác nhau như thế nào?

Đây là ba cấu trúc không nên gộp.

~~~text
SQLException main: transaction failed
├── cause -> IOException: socket read failed
├── nextException -> SQLException: duplicate key
│                     └── nextException -> SQLException: timeout
└── suppressed[0] -> IOException: resource close failed
~~~

| Quan hệ | Kiểu dữ liệu | Hình thành khi nào? | Ý nghĩa chính |
| --- | --- | --- | --- |
| Cause | Một `Throwable` hoặc null | Constructor có cause, hoặc `initCause` hợp lệ | Nguyên nhân gốc/được bọc |
| Next exception | Một `SQLException` hoặc null mỗi mắt xích | Driver hoặc code gọi `setNextException` | Các chẩn đoán JDBC liên kết |
| Suppressed | Mảng `Throwable[]` | `addSuppressed`, hoặc cơ chế try-with-resources | Lỗi phụ không nên thay lỗi chính |

Giả sử bạn viết:

~~~java
SQLException primary =
        new SQLException("operation failed", "42000");
primary.setNextException(
        new SQLException("detail A", "23505"));
primary.addSuppressed(
        new java.io.IOException("close failed"));
~~~

Lúc này `primary.getCause()` vẫn có thể là `null` dù có next và suppressed. Việc thêm next **không** tự tạo cause; việc thêm suppressed **không** tự tạo next.

**Cần nhớ:** nếu chỉ log `e.getMessage()`, bạn có thể bỏ lỡ mọi thông tin còn lại; nếu chỉ đọc `e.getCause()`, vẫn có thể bỏ qua các lỗi JDBC nằm trong `getNextException()`.

## 6. SQLState là gì?

**SQLState** là một chuỗi mã dùng để phân loại điều kiện SQL theo những quy ước như X/Open và tiêu chuẩn SQL. Dạng chuẩn thường là **năm ký tự**: hai ký tự đầu mô tả **class** (nhóm điều kiện), ba ký tự sau mô tả **subclass** (điều kiện cụ thể).

~~~text
SQLState: 23505
          ^^---
          || └── subclass: 505
          └└── class: 23
~~~

Cách ghi hình trên chỉ là minh họa chia chuỗi; đọc chính xác nên lấy:

~~~java
String state = "23505";
String sqlClass = state.substring(0, 2);    // "23"
String subclass = state.substring(2);       // "505"
~~~

Một số nhóm thường gặp:

| Hai ký tự đầu | Nhóm điều kiện tham khảo |
| --- | --- |
| `00` | Successful completion |
| `01` | Warning |
| `02` | No data |
| `08` | Connection exception |
| `22` | Data exception |
| `23` | Integrity constraint violation |
| `28` | Invalid authorization specification |
| `40` | Transaction rollback |
| `42` | Syntax error or access rule violation |

Những nhóm này hữu ích để **định hướng**. Giá trị cụ thể và cách phân loại của driver phải được đối chiếu với tài liệu database và JDBC driver. Một số mã như `HYT00` mang tính quy ước driver/ODBC và không nên bị ép hiểu như mã tiêu chuẩn của mọi database.

### Ví dụ mã thường thấy

- `23505` được một số hệ quản trị dùng cho vi phạm ràng buộc unique.
- `40001` thường gắn với thất bại transaction do serialization hoặc dạng rollback tương ứng.
- `08001` thường mô tả client không thể thiết lập kết nối SQL theo quy ước liên quan.
- `42...` có thể gợi ý vấn đề cú pháp hoặc access rule.

**Không hard-code một cặp “SQLState → hành động” cho mọi vendor** nếu chưa kiểm tra driver. Chẳng hạn lỗi kết nối có thể xuất hiện **sau** khi server đã thực hiện thao tác ghi; retry mù quáng có thể tạo tác dụng phụ trùng lặp.

## 7. `getErrorCode()` là mã vendor, không phải SQLState

~~~java
SQLException error =
        new SQLException("unique violation", "23505", 1062);

System.out.println(error.getSQLState());   // 23505
System.out.println(error.getErrorCode());  // 1062
~~~

`getErrorCode()` trả `int` do vendor quyết định. Ý nghĩa của `1062` trong một hệ quản trị nhất định **không thể suy ra chỉ bằng Java API**. Không chuyển mọi vendor code thành một enum chung nếu ứng dụng hỗ trợ nhiều database mà chưa thiết kế mapping riêng.

Mã `0` thường xuất hiện khi không cung cấp vendor code:

~~~java
SQLException e = new SQLException("message", "08001");
System.out.println(e.getErrorCode()); // 0
~~~

`0` không phải một bằng chứng chắc chắn rằng sự cố không có mã lỗi nội bộ ở database; nó chỉ là số nguyên được API trả về trong tình huống cụ thể.

### Thông điệp và mã lỗi có mức ổn định khác nhau

- `getMessage()`: văn bản dành cho người đọc, có thể khác theo ngôn ngữ, phiên bản và cấu hình driver.
- `getSQLState()`: mã theo hệ SQLState, có thể giúp phân nhóm.
- `getErrorCode()`: mã vendor, chỉ diễn giải chính xác trong bối cảnh driver/database phù hợp.
- Subclass `SQLException`: một tín hiệu bổ sung về tính chất transient, integrity, timeout.

Khi xây monitoring hoặc retry policy, cần ghi **đúng các trường**, không trộn chúng thành một chuỗi rồi parse ngược.

## 8. Constructor của `SQLException` và dữ liệu được khởi tạo

Các overload quan trọng trong Java 17:

~~~java
new SQLException()
new SQLException(String reason)
new SQLException(String reason, String sqlState)
new SQLException(String reason, String sqlState, int vendorCode)
new SQLException(Throwable cause)
new SQLException(String reason, Throwable cause)
new SQLException(String reason, String sqlState, Throwable cause)
new SQLException(
        String reason,
        String sqlState,
        int vendorCode,
        Throwable cause)
~~~

Các dòng trên là **chữ ký minh họa**; không đặt nguyên văn tất cả vào một phương thức như biểu thức thực thi vì `reason`/`cause` chỉ là tên loại tham số trong cách ghi API.

**Constructor nhận cause** giữ tham chiếu nguyên nhân ngay lúc tạo. Với overload không nhận cause, trạng thái cause thường chưa được khởi tạo và `initCause(...)` có thể dùng một lần trong điều kiện hợp lệ. Tuy nhiên, nếu đã chọn một overload **có tham số cause**, trạng thái cause được khởi tạo ngay cả khi truyền `null`: không thể gọi `initCause` để thay thế.

Ví dụ:

~~~java
SQLException a = new SQLException("operation failed");
a.initCause(new java.io.IOException("network failed"));
// Thành công: constructor không khởi tạo cause.

SQLException b = new SQLException(
        "operation failed", "08001", 0, null);
b.initCause(new java.io.IOException("later"));
// Ném IllegalStateException: cause đã khởi tạo bằng null.
~~~

Đây là mảnh **phản ví dụ gây lỗi runtime ở cuối**; không chạy mà không bắt exception nếu muốn tiếp tục.

### Constructor nhận duy nhất cause

~~~java
SQLException e = new SQLException(
        new java.io.IOException("socket closed"));
~~~

Ở dạng cause-only, reason thường được tạo từ `cause.toString()`, tức có thể chứa cả tên lớp lẫn message của cause. Nếu đang chuyển đổi lỗi ở tầng JDBC, overload nhận **reason + SQLState + vendor code + cause** thường biểu đạt nhiều ngữ cảnh rõ hơn.

## 9. Duyệt tất cả `nextException` bằng vòng lặp

Cách duyệt dễ hiểu:

~~~java
SQLException current = primary;
while (current != null) {
    System.out.println(
            current.getMessage() + " / " + current.getSQLState());
    current = current.getNextException();
}
~~~

Đoạn minh họa giả định `primary` có sẵn. Nó đọc lần lượt **lỗi chính và tất cả next exceptions**. Mỗi mắt xích có thể mang SQLState/vendor code khác nhau.

Nếu viết công cụ logging tổng quát nhận dữ liệu từ nhiều nguồn, hãy phòng trường hợp cấu trúc next bị tạo sai hoặc tạo vòng gián tiếp. Một giải pháp đơn giản là dùng tập tham chiếu:

~~~java
java.util.Set<SQLException> seen =
        java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<SQLException, Boolean>());

for (SQLException current = primary;
        current != null && seen.add(current);
        current = current.getNextException()) {
    System.out.println(current.getMessage());
}
~~~

Đây là mảnh minh họa với `primary` đã khai báo. `IdentityHashMap` phân biệt bằng **đồng nhất đối tượng**, không dùng `equals()`, thích hợp khi câu hỏi là “đã gặp chính object này chưa?”. Với chuỗi do JDBC driver tạo thông thường, bạn không cần cố dựng chuỗi vòng; việc phát hiện vòng là biện pháp bảo vệ cho tiện ích chẩn đoán.

### `setNextException` có nên được gọi trong code nghiệp vụ?

Thông thường, **driver hoặc thư viện JDBC** tạo chuỗi chẩn đoán. Mã ứng dụng có thể dùng `setNextException` khi tự tổ chức thông tin lỗi JDBC liên quan, nhưng không nên biến nó thành danh sách lỗi nghiệp vụ không liên quan hoặc dùng thay cho cause trong exception chaining thông thường.

Nếu bạn bọc `SQLException` ở tầng service:

~~~java
throw new IllegalStateException(
        "Không thể lưu đơn hàng", sqlException);
~~~

thì hãy **giữ cả đối tượng `sqlException` làm cause**. Nhờ đó `getNextException()` của chính object JDBC vẫn có thể được đọc sau khi truy cập cause.

## 10. `SQLException implements Iterable<Throwable>`

Một tiện ích ít được biết: có thể duyệt một `SQLException` bằng enhanced for:

~~~java
for (Throwable detail : sqlException) {
    System.out.println(
            detail.getClass().getSimpleName()
                    + ": " + detail.getMessage());
}
~~~

Đây là mảnh minh họa có biến `sqlException` kiểu `SQLException`.

Iterator của `SQLException` được thiết kế để duyệt các `SQLException` được nối và **các nguyên nhân tương ứng** nếu có, không chỉ mỗi exception đầu tiên. Vì kiểu phần tử là `Throwable`, một phần tử có thể **không phải `SQLException`** (ví dụ `IOException` làm cause). Muốn đọc `getSQLState()`:

~~~java
for (Throwable detail : sqlException) {
    if (detail instanceof SQLException sql) {
        System.out.println(sql.getSQLState());
    }
}
~~~

Đừng ép trực tiếp mọi phần tử iterator sang `SQLException` vì cause có thể không thuộc lớp đó.

### Iterator khác `getNextException()` ở phạm vi

- Vòng `current = current.getNextException()` cho bạn **chỉ các đối tượng `SQLException` trong next chain**.
- `for (Throwable detail : sqlException)` có thể gồm **cả các cause** liên quan, theo hợp đồng iterator.
- `getSuppressed()` vẫn là một quan hệ khác; không nên mặc định suppressed được bao gồm trong iterator.

Khi viết hệ thống ghi log với nhiều nhánh, hãy tránh **in lặp** cùng một lỗi nếu đã dùng iterator rồi lại tự duyệt cause riêng. Chọn một chiến lược rõ ràng để giữ đầy đủ thông tin mà không nhân bản mọi message.

## 11. Ví dụ Java 17+ đầy đủ, không cần database hoặc JDBC driver

Chương trình sau **tự tạo** đối tượng `SQLException`, `SQLTimeoutException` và `IOException`. Nó không kết nối database, nên đầu ra có thể kiểm chứng ổn định và không phụ thuộc SQL dialect.

Lưu tệp **`SQLExceptionDiagnosticsDemo.java`**:

~~~java
import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class SQLExceptionDiagnosticsDemo {

    public static void main(String[] args) {
        SQLException main = sampleFailure();

        System.out.println("== primary ==");
        System.out.println("type="
                + main.getClass().getSimpleName());
        System.out.println("message=" + main.getMessage());
        System.out.println("SQLState=" + main.getSQLState());
        System.out.println("vendorCode=" + main.getErrorCode());
        System.out.println("category=" + category(main));

        System.out.println("== next chain ==");
        Set<SQLException> seen =
                Collections.newSetFromMap(
                        new IdentityHashMap<SQLException, Boolean>());
        SQLException current = main;
        int index = 0;
        while (current != null && seen.add(current)) {
            System.out.println(
                    "item" + index + "="
                            + current.getClass().getSimpleName()
                            + "|" + current.getSQLState()
                            + "|" + current.getErrorCode()
                            + "|" + current.getMessage());
            index++;
            current = current.getNextException();
        }
        System.out.println("chainCount=" + index);

        System.out.println("== distinct relationships ==");
        Throwable root = main.getCause();
        System.out.println("cause="
                + root.getClass().getSimpleName()
                + ": " + root.getMessage());
        System.out.println(
                "suppressedCount=" + main.getSuppressed().length);
        System.out.println(
                "suppressed0="
                        + main.getSuppressed()[0]
                                .getClass().getSimpleName()
                        + ": " + main.getSuppressed()[0].getMessage());
        System.out.println(
                "nextIsCause=" + (main.getNextException() == root));

        System.out.println("== classifications ==");
        System.out.println("firstNextCategory="
                + category(main.getNextException()));
        System.out.println("noStateCategory="
                + category(new SQLException("unknown")));
        System.out.println(
                "timeoutIsTransient="
                        + (main.getNextException()
                                   .getNextException()
                                instanceof SQLTransientException));

        System.out.println("== wrapped exception ==");
        IllegalStateException wrapped =
                new IllegalStateException(
                        "cannot save order", main);
        System.out.println(
                "wrapperCauseSame=" + (wrapped.getCause() == main));
        SQLException recovered = (SQLException) wrapped.getCause();
        System.out.println(
                "recoveredNextMessage="
                        + recovered.getNextException().getMessage());

        System.out.println("== try-with-resources ==");
        resourceFailure();
    }

    static SQLException sampleFailure() {
        IOException network =
                new IOException("socket read failed");

        SQLException primary = new SQLException(
                "transaction failed", "40001", 1205, network);

        primary.setNextException(new SQLException(
                "duplicate key", "23505", 1062));

        primary.setNextException(new SQLTimeoutException(
                "statement timed out", "HYT00", 0));

        primary.addSuppressed(
                new IOException("resource close failed"));

        return primary;
    }

    static String category(SQLException failure) {
        String state = failure.getSQLState();
        if (state == null || state.length() < 2) {
            return "unknown";
        }
        return switch (state.substring(0, 2)) {
            case "08" -> "connection";
            case "23" -> "integrity";
            case "40" -> "transaction";
            default -> "other";
        };
    }

    static final class FailingResource implements AutoCloseable {
        @Override
        public void close() throws SQLException {
            throw new SQLException("close failed", "HY000", 99);
        }
    }

    static void resourceFailure() {
        try (FailingResource ignored = new FailingResource()) {
            throw new SQLException("execute failed", "42000", 7);
        } catch (SQLException failure) {
            System.out.println("primaryMessage="
                    + failure.getMessage());
            System.out.println("primaryState="
                    + failure.getSQLState());
            System.out.println("suppressedLength="
                    + failure.getSuppressed().length);
            System.out.println("suppressedMessage="
                    + failure.getSuppressed()[0].getMessage());
            System.out.println("next="
                    + failure.getNextException());
        }
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac SQLExceptionDiagnosticsDemo.java
java SQLExceptionDiagnosticsDemo
~~~

**Stdout dự kiến chính xác:**

~~~text
== primary ==
type=SQLException
message=transaction failed
SQLState=40001
vendorCode=1205
category=transaction
== next chain ==
item0=SQLException|40001|1205|transaction failed
item1=SQLException|23505|1062|duplicate key
item2=SQLTimeoutException|HYT00|0|statement timed out
chainCount=3
== distinct relationships ==
cause=IOException: socket read failed
suppressedCount=1
suppressed0=IOException: resource close failed
nextIsCause=false
== classifications ==
firstNextCategory=integrity
noStateCategory=unknown
timeoutIsTransient=true
== wrapped exception ==
wrapperCauseSame=true
recoveredNextMessage=duplicate key
== try-with-resources ==
primaryMessage=execute failed
primaryState=42000
suppressedLength=1
suppressedMessage=close failed
next=null
~~~

### Phân tích kết quả phần primary

`sampleFailure()` tạo `SQLException` đầu tiên với đầy đủ reason, SQLState, vendor code và cause. Vì đối tượng là chính `SQLException`, `getClass().getSimpleName()` trả `"SQLException"`. Bộ phân loại `category` chỉ kiểm tra **hai ký tự đầu** của SQLState, nên `"40001"` trả `"transaction"`.

Hàm `category` trong ví dụ là **một tiện ích nhận diện nhóm**, không phải quy trình chuẩn để quyết định retry. Nó không kiểm tra vendor, trạng thái connection hoặc side effect của giao dịch.

### Phân tích chuỗi next

Hai lệnh `primary.setNextException(...)` tạo thứ tự:

1. Lỗi chính `transaction failed`, SQLState `40001`, vendor code `1205`.
2. Lỗi tiếp `duplicate key`, SQLState `23505`, vendor code `1062`.
3. Lỗi cuối `statement timed out`, SQLState `HYT00`, vendor code `0`.

Vòng duyệt bắt đầu từ chính `primary`, nên `chainCount=3`. Kiểm tra identity bằng `seen.add(current)` là lớp bảo vệ cho trường hợp một chuỗi bị xây dựng bất thường; ví dụ bình thường không có vòng.

### Phân tích cause và suppressed

`main.getCause()` trả `IOException("socket read failed")`. Mục `nextException` đầu tiên là một `SQLException` **khác**, nên `nextIsCause=false`. `main.getSuppressed()[0]` lại là một `IOException("resource close failed")` thứ ba, không trở thành cause hoặc next.

### Phân tích loại transient

`SQLTimeoutException` là subtype của `SQLTransientException` trong JDK 17, nên phép kiểm tra `instanceof SQLTransientException` trả `true`. Điều này **không tự động có nghĩa thao tác ghi có thể retry an toàn**: code còn phải xét idempotency, transaction state, timeout và hợp đồng của database.

### Phân tích wrapping

`IllegalStateException("cannot save order", main)` giữ chính `SQLException` làm cause, nên toàn bộ dữ liệu trên `main`, kể cả next chain, có thể truy xuất sau khi cast với kiểu đã xác nhận. Nếu chỉ tạo `IllegalStateException(main.getMessage())`, caller sẽ không còn liên kết đối tượng tới SQLState và chuỗi next qua `getCause()`.

### Phân tích try-with-resources

Thân `try` ném `SQLException("execute failed", "42000", 7)`. Khi rời scope, `FailingResource.close()` ném một `SQLException("close failed", "HY000", 99)` khác. Java giữ lỗi đóng làm **suppressed exception** trên lỗi chính.

Lưu ý: **lỗi đóng ở đây không được tự thêm vào `nextException`**. `getNextException()` của lỗi chính vẫn `null`. Đây là sự khác biệt quan trọng giữa cơ chế ngôn ngữ try-with-resources và chuỗi lỗi JDBC do driver/API chủ động thiết lập.

## 12. Ví dụ thực tế: log đầy đủ một ngoại lệ JDBC

Với JDBC thật, ta thường bắt `SQLException` ở ranh giới quản lý database:

~~~java
try {
    statement.executeUpdate(sql);
} catch (SQLException e) {
    logSQLException(e);
    throw e;
}
~~~

Đây là mảnh minh họa trong một phương thức khai báo/bắt checked `SQLException` phù hợp; `statement` và `sql` đã có sẵn. Không nên log ở mọi tầng nếu lỗi còn được truyền và log lại nhiều lần.

Một logger tối thiểu:

~~~java
static void logSQLException(SQLException first) {
    for (SQLException e = first;
            e != null;
            e = e.getNextException()) {
        System.err.println(
                "SQLState=" + e.getSQLState()
                + ", vendorCode=" + e.getErrorCode()
                + ", message=" + e.getMessage());
        if (e.getCause() != null) {
            System.err.println(
                    "cause=" + e.getCause().getClass().getName());
        }
    }
}
~~~

Đây là phương thức minh họa, có thể đặt trong một lớp với import `SQLException`. Logger production nên dùng thư viện logging đã thiết lập, cấu trúc dữ liệu phù hợp, chính sách redaction và giới hạn log thay vì phụ thuộc `System.err` trong mọi trường hợp.

**Bảo mật:** reason, cause hoặc SQL do driver báo có thể chứa tên bảng, khóa, giá trị input, chuỗi kết nối hoặc dữ liệu nhạy cảm. Đừng gửi nguyên toàn bộ thông điệp, SQL text hay stack trace ra response công khai. Có thể hiển thị mã lỗi ứng dụng an toàn và gắn correlation ID để tra cứu log nội bộ.

## 13. Khi bọc `SQLException` trong một exception của tầng service

Nếu module nghiệp vụ muốn che công nghệ JDBC khỏi API công khai, có thể định nghĩa custom exception:

~~~java
public final class RepositoryFailure extends Exception {
    public RepositoryFailure(
            String message, SQLException cause) {
        super(message, cause);
    }
}
~~~

Đây là class Java độc lập nếu lưu trong `RepositoryFailure.java`. Caller không cần phụ thuộc trực tiếp vào `SQLException` ở chữ ký phương thức; nhân viên vận hành vẫn có thể tìm SQLState, vendor code và next chain từ cause.

Một method ví dụ:

~~~java
static void saveOrder(java.sql.Connection connection)
        throws RepositoryFailure {
    try (java.sql.Statement statement =
                 connection.createStatement()) {
        statement.executeUpdate(
                "UPDATE orders SET processed = 1 WHERE id = 42");
    } catch (SQLException cause) {
        throw new RepositoryFailure(
                "Không thể cập nhật trạng thái đơn hàng", cause);
    }
}
~~~

Đây là phương thức Java hợp lệ khi đã định nghĩa/import `RepositoryFailure` và `SQLException` tương ứng. SQL trong ví dụ chỉ để thể hiện cấu trúc JDBC; không có bảo đảm bảng `orders` tồn tại trong một database thực tế.

**Tại sao giữ `cause` nguyên vẹn?** Khi một `SQLException` gốc có next chain, bạn không nên chỉ sao chép `cause.getMessage()` vào custom exception. Hãy giữ **chính đối tượng cause** để các lớp chẩn đoán có thể truy xuất `getSQLState()` và `getNextException()` khi cần.

### Có nên bọc luôn toàn bộ next chain thành các cause lồng nhau?

Thông thường **không cần**. `nextException` là quan hệ JDBC riêng; đừng tự làm phẳng thành chuỗi cause rồi đánh mất khác biệt. Một custom wrapper giữ `SQLException` gốc đã bảo toàn cả chuỗi.

## 14. `SQLWarning`: ngoại lệ JDBC liên quan nhưng ý nghĩa khác

`java.sql.SQLWarning extends SQLException`, nên nó cùng mang reason, SQLState, vendor code và chuỗi warning. Tuy nhiên, theo JDBC, warning thường được **gắn với `Connection`, `Statement` hoặc `ResultSet`** và có thể đọc qua `getWarnings()` thay vì luôn được ném ra như thất bại của một thao tác.

Ví dụ minh họa:

~~~java
SQLWarning warning = statement.getWarnings();
for (SQLWarning current = warning;
        current != null;
        current = current.getNextWarning()) {
    System.out.println(current.getMessage());
}
~~~

Đây là mảnh mã trong phạm vi có `statement` kiểu `Statement` và import `SQLWarning`. `getNextWarning()` là API chuyên cho chuỗi warning.

**Đừng suy ra mọi `SQLException` là một warning** hoặc cứ có SQLState class `01` thì JDBC nhất thiết ném lỗi. Mã warning và cơ chế báo warning của driver cần đọc theo API cụ thể.

## 15. `BatchUpdateException` và các thông tin bổ sung

Khi chạy batch JDBC, một thao tác có thể thất bại và API báo `BatchUpdateException`, là subclass của `SQLException`. Ngoài các trường chẩn đoán chung, nó có dữ liệu về những kết quả update của batch:

~~~java
catch (java.sql.BatchUpdateException e) {
    int[] counts = e.getUpdateCounts();
    System.out.println(counts.length);
}
~~~

Đây là đoạn `catch` minh họa cần nằm trong một khối `try` có thể ném `BatchUpdateException`. Có API `getLargeUpdateCounts()` khi cần giá trị `long`.

Điểm quan trọng:

- `getUpdateCounts()` không thay `getNextException()`.
- Một `BatchUpdateException` có thể giữ các SQLException tiếp theo cung cấp thêm chẩn đoán theo driver.
- Nội dung mảng update counts phụ thuộc hành vi JDBC driver: driver có thể dừng sau lỗi hoặc tiếp tục xử lý một phần batch theo hợp đồng.
- **Không khôi phục transaction chỉ bằng cách nhìn một count**, vì có thể đã có một phần dữ liệu được ghi.

Nếu xử lý batch có nhiều side effect, phải xem xét transaction boundary, rollback, autocommit và chính sách idempotency của ứng dụng.

## 16. SQLException, transaction và quyết định retry

Một lỗi có SQLState class `40`, hoặc subtype `SQLTransientException`, có thể gợi ý điều kiện có khả năng phục hồi. Nhưng việc retry cần **nhiều điều kiện ngoài loại exception**.

Trước khi retry, xác minh:

1. **Thao tác có idempotent không?** Gửi lại câu lệnh có thể gây side effect trùng không?
2. **Trạng thái transaction là gì?** Đã rollback, đã commit, hay kết quả commit chưa biết?
3. **Connection có còn hợp lệ không?** Có cần lấy connection mới từ pool hoặc thiết lập lại session?
4. **Driver và database có khuyến nghị retry cho mã này không?** SQLState/vendor code chỉ là một phần tín hiệu.
5. **Có giới hạn retry/backoff không?** Tránh tạo vòng lặp lỗi gây tải lớn hơn.
6. **Có transaction spanning multiple calls không?** Retry từng statement có thể sai nếu toàn bộ transaction phải được chạy lại.
7. **Có idempotency key hoặc deduplication không?** Đặc biệt quan trọng với thao tác ghi có thể đã thành công trước khi timeout.
8. **Có yêu cầu phân biệt lỗi validation, authentication và constraint violation không?** Những lỗi này thường không được sửa bằng retry y hệt.

Phản ví dụ thiết kế:

~~~java
catch (SQLException e) {
    while (true) {
        executeAgain();
    }
}
~~~

Đây là mảnh pseudocode Java minh họa với `executeAgain()` giả định: retry vô hạn không phân loại lỗi có thể khiến hệ thống quá tải hoặc nhân đôi tác dụng phụ. Không dùng làm thực hành production.

**Nguyên tắc:** loại exception và SQLState hỗ trợ **phân loại**, còn quyết định retry là một phần của **hợp đồng giao dịch và tính đúng đắn nghiệp vụ**.

## 17. Dọn dẹp tài nguyên JDBC và suppressed exceptions

JDBC objects như `Connection`, `Statement` và `ResultSet` triển khai/đi theo `AutoCloseable` trong API hiện đại. Nên dùng try-with-resources khi sở hữu tài nguyên đó:

~~~java
try (java.sql.Connection connection = dataSource.getConnection();
     java.sql.PreparedStatement statement =
             connection.prepareStatement(
                     "SELECT name FROM users WHERE id = ?")) {
    statement.setLong(1, userId);
    try (java.sql.ResultSet results = statement.executeQuery()) {
        if (results.next()) {
            System.out.println(results.getString(1));
        }
    }
} catch (SQLException e) {
    // Xử lý/log/lan truyền có chính sách.
}
~~~

Đây là **mảnh minh họa**: cần `dataSource`, `userId`, thư viện JDBC và một database thực. Không phải chương trình độc lập để chạy khi chưa cấu hình driver.

Khi thân `try` đã ném một `SQLException` và `close()` của một tài nguyên cũng ném lỗi, Java thường gắn lỗi đóng vào **`getSuppressed()` của ngoại lệ chính**. Mỗi suppressed throwable có thể bản thân là `SQLException` với SQLState/vendor code của nó.

Điều này **khác** với `setNextException`: JVM không tự thêm lỗi từ `close()` vào JDBC next chain.

### Một lỗi `close()` có thể là primary

Nếu thân `try` **hoàn tất bình thường** nhưng `close()` ném `SQLException`, ngoại lệ đóng có thể là **lỗi chính được ném ra**, không phải suppressed. Vì thế khi nhìn thấy `SQLException` có liên quan đóng kết nối, không được mặc định nó luôn chỉ là lỗi phụ.

Đọc cấu trúc thực tế: `getCause()`, `getSuppressed()` và `getNextException()`.

## 18. Lỗi thiết kế thường gặp với `SQLException`

### 18.1. Chỉ in `getMessage()` rồi bỏ qua SQLState

Message có thể không đủ để phân loại, đặc biệt khi driver có các mã khác nhau cho kết nối, constraint hay transaction.

### 18.2. Nhầm `getNextException()` với `getCause()`

Next chain là danh sách chẩn đoán JDBC nối các `SQLException`. Cause là liên kết nguyên nhân `Throwable` và có thể là `IOException` hoặc lỗi khác.

### 18.3. Tưởng `getSuppressed()` chính là next chain

Sai. Suppressed thường đến từ quá trình dọn dẹp; chúng không tự trở thành `nextException`.

### 18.4. Chỉ kiểm tra `getNextException()` đầu tiên

Một chain có thể có nhiều mắt xích. Dùng vòng lặp hoặc iterator theo hợp đồng khi cần thông tin đầy đủ.

### 18.5. Cho rằng mọi `SQLException` đều có SQLState

Không. `getSQLState()` có thể trả null; code phân loại phải xử lý null và chuỗi không chuẩn.

### 18.6. Cho rằng vendor code là mã SQLState

Sai. `getErrorCode()` là số nguyên theo vendor; `getSQLState()` là String theo quy ước SQLState.

### 18.7. Dùng nguyên văn reason làm mã máy

Không nên parse `"duplicate key"` hay các message được dịch/thay đổi theo version. Ưu tiên SQLState/subtype/vendor code có kiểm chứng khi cần phân nhánh.

### 18.8. Retry mọi `SQLTransientException`

Ngay cả khi lỗi transient, một thao tác ghi không idempotent có thể đã chạy ở server. Kiểm tra transaction, side effects và chính sách driver trước retry.

### 18.9. Retry mọi lỗi class `23`

Lỗi ràng buộc toàn vẹn không nhất thiết biến mất khi thử lại. Có thể cần sửa dữ liệu đầu vào hoặc xử lý conflict.

### 18.10. Chuyển checked `SQLException` thành unchecked mà bỏ cause

Nếu module cần bọc lỗi, truyền nguyên `SQLException` vào constructor wrapper để giữ cause và next chain.

### 18.11. Bắt `Exception` rồi mặc định mọi exception là lỗi database

Handler quá rộng có thể nuốt lỗi lập trình. `catch(SQLException)` khi xử lý JDBC thường thể hiện rõ trách nhiệm hơn.

### 18.12. Nhầm `SQLWarning` với lỗi làm thao tác thất bại

Warning có thể được báo qua `getWarnings()`. Không nhất thiết mọi warning đều ném khỏi lời gọi JDBC.

### 18.13. In toàn bộ SQL statement và tham số người dùng ra log

Log có thể làm lộ dữ liệu cá nhân, mã truy cập, token hoặc giá trị nhạy cảm. Cần redaction và quyền truy cập log phù hợp.

### 18.14. Bỏ qua `BatchUpdateException.getUpdateCounts()`

Đối với batch, thông tin thành công/thất bại từng phần có thể rất quan trọng để quyết định rollback, phục hồi hoặc ghi nhận kết quả.

### 18.15. Đoán tên database chỉ từ một vendor code

Một con số riêng lẻ không đủ khi hệ thống có nhiều driver. Phải biết database, driver version và mapping chính thức.

### 18.16. Tự tạo vòng next chain

`setNextException` được thiết kế để thêm vào cuối chuỗi. Không nên nối các object theo cách tạo vòng hoặc tái sử dụng chúng cho nhiều sự cố không liên quan, vì có thể khiến tiện ích duyệt lỗi gặp vấn đề.

## 19. Checklist chẩn đoán lỗi JDBC

Khi nhận `SQLException e`, hãy lần lượt:

1. **Ghi nhận loại exception**: `e.getClass().getName()`, các subtype hữu ích.
2. **Lấy reason/message** an toàn, không hiển thị thông tin nhạy cảm ra client.
3. **Đọc SQLState** và phân loại nhóm khi mã hợp lệ.
4. **Đọc vendor code** với thông tin database/driver tương ứng.
5. **Duyệt toàn bộ next chain** khi cần chẩn đoán nhiều lỗi.
6. **Duyệt cause** để tìm ngoại lệ thấp tầng như I/O/network.
7. **Kiểm tra suppressed exceptions**, nhất là lỗi đóng tài nguyên.
8. **Nếu là batch**, đọc counts và trạng thái giao dịch.
9. **Nếu có warning**, kiểm tra `getWarnings()` theo API phù hợp.
10. **Kiểm tra transaction state/connection state** trước khi quyết định retry.
11. **Dùng log có cấu trúc** và correlation ID; tránh ghi lại cùng lỗi ở mọi tầng.
12. **Không coi stack trace hay message là giao thức machine-readable ổn định**.
13. **Định nghĩa phản hồi người dùng tách biệt** với chi tiết SQL nội bộ.
14. **Đọc tài liệu JDBC driver và database** cho các mã cụ thể thay vì đoán.

Không nhất thiết mọi application flow đều tự thực hiện đủ mười bốn bước. Đây là danh sách dành cho tầng chẩn đoán/observability hoặc khi phân tích một sự cố khó.

## 20. Bài tập tự kiểm tra

1. `SQLException` là checked hay unchecked? Vì sao?
2. Nó nằm dưới `Exception` hay `RuntimeException`?
3. `getMessage()` và `getSQLState()` khác nhau thế nào?
4. `getErrorCode()` có phải SQLState dạng số không?
5. `getSQLState()` có thể là `null` không?
6. Hai ký tự đầu của SQLState thường biểu diễn gì?
7. SQLState class `08` gợi ý nhóm điều kiện nào?
8. SQLState class `23` gợi ý nhóm điều kiện nào?
9. `getCause()` trả một `SQLException` hay luôn có thể là loại `Throwable` khác?
10. `getNextException()` có thể trả loại `IOException` trực tiếp không?
11. Gọi `setNextException` hai lần trên exception đầu sẽ thay thế phần tử trước hay nối thêm?
12. Vì sao driver có thể tạo nhiều next exceptions?
13. `getSuppressed()` liên quan thế nào tới try-with-resources?
14. Lỗi từ `close()` có tự chuyển thành `nextException` không?
15. `SQLException implements Iterable<Throwable>` hữu ích thế nào?
16. Vì sao không được ép mọi phần tử của iterator thành `SQLException`?
17. Có thể giữ cả cause và next chain khi bọc lỗi trong `IllegalStateException` không?
18. Nếu wrapper chỉ chép message mà không truyền cause, điều gì bị mất?
19. `SQLTimeoutException` có thuộc `SQLTransientException` không?
20. Có thể retry thao tác ghi ngay khi gặp `SQLTransientException` không?
21. Vì sao `23505` không được giả định có ý nghĩa y hệt ở mọi driver?
22. `getErrorCode()==0` có chứng minh không có vendor error nào không?
23. `SQLWarning` thường có thể được lấy từ API nào?
24. `BatchUpdateException` cung cấp thêm thông tin nào?
25. Khi có lỗi ở lần close, vì sao cần phân biệt primary và suppressed?
26. Có nên trả stack trace SQL/JDBC nguyên vẹn cho HTTP client không?
27. Vì sao một lỗi class `40` cần kiểm tra transaction state trước retry?
28. Một tiện ích duyệt next chain tổng quát nên phòng tình huống bất thường nào?
29. Khi xử lý đa database, cần nguồn nào để hiểu vendor code?
30. Ba quan hệ chẩn đoán dễ nhầm của `SQLException` là gì?

### Đáp án gợi ý

1. Checked; kế thừa `Exception`, không thuộc `RuntimeException`.
2. `Exception`.
3. Message là văn bản; SQLState là mã điều kiện SQL.
4. Không; nó là mã integer riêng của vendor.
5. Có.
6. Class/nhóm điều kiện SQL.
7. Vấn đề kết nối.
8. Vi phạm ràng buộc toàn vẹn.
9. `Throwable` bất kỳ hoặc null.
10. Không; giá trị next thuộc kiểu `SQLException`.
11. Nối thêm vào cuối chuỗi.
12. Để lưu thêm các thông báo chẩn đoán JDBC có mã và message riêng.
13. Lỗi đóng tài nguyên có thể được gắn làm suppressed lên lỗi chính.
14. Không tự động.
15. Duyệt các SQLException liên kết và cause theo hợp đồng iterator.
16. Cause có thể là `IOException` hoặc loại `Throwable` khác.
17. Có, nếu truyền chính SQLException làm cause của wrapper.
18. Mất liên kết tới object gốc, SQLState/vendor code và next chain qua cause.
19. Có.
20. Không tự động; phải xét idempotency, transaction và hợp đồng driver.
21. Vendor và database có thể dùng/mở rộng quy ước mã khác nhau.
22. Không; phải xét quy ước driver cụ thể.
23. `Connection.getWarnings()`, `Statement.getWarnings()`, `ResultSet.getWarnings()`.
24. Counts của batch qua `getUpdateCounts()`/`getLargeUpdateCounts()`.
25. Lỗi thân try và lỗi close có vai trò khác nhau; close có thể được suppressed.
26. Không; có thể lộ thông tin kỹ thuật và dữ liệu nhạy cảm.
27. Vì thao tác ghi có thể đã có một phần tác dụng hoặc transaction cần rollback.
28. Vòng liên kết hoặc cấu trúc dữ liệu lỗi bất thường.
29. Tài liệu chính thức của database và JDBC driver theo phiên bản.
30. `cause`, `nextException` và `suppressed exceptions`.

## 21. Ghi nhớ và tham khảo API

**`SQLException` cung cấp nhiều lớp thông tin hơn một `Exception` thông thường.** Dùng `getMessage()` cho reason, `getSQLState()` cho mã điều kiện SQL, `getErrorCode()` cho mã vendor; dùng `getCause()` để truy tìm nguyên nhân, `getNextException()` để đọc chuỗi chẩn đoán JDBC, và `getSuppressed()` để phát hiện lỗi phụ khi dọn dẹp. Chúng không thay thế nhau. `SQLException` là checked exception; việc bọc nó sang lỗi tầng cao nên **giữ nguyên đối tượng cause**. Không quyết định retry hoặc lỗi nghiệp vụ chỉ dựa trên message hay một SQLState mà chưa xét driver, connection, transaction và idempotency.

Tài liệu chuẩn Java 17:

- `SQLException`: https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/SQLException.html
- `SQLTransientException`: https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/SQLTransientException.html
- `SQLTimeoutException`: https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/SQLTimeoutException.html
- `SQLWarning`: https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/SQLWarning.html
- `BatchUpdateException`: https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/BatchUpdateException.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="api-exceptions-006">ParseException, DateTimeParseException và ngữ cảnh phân tích dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# ParseException, DateTimeParseException và ngữ cảnh phân tích dữ liệu trong Java

Dữ liệu đi vào chương trình Java thường là **văn bản chưa được xác thực**: ngày sinh từ biểu mẫu, ngày giao hàng trong CSV, thời gian trong URL, số có dấu phân nhóm theo locale hoặc thông tin từ hệ thống cũ. Việc chuyển văn bản thành đối tượng có kiểu được gọi là **parsing**. Khi chuỗi không khớp định dạng hoặc không tạo ra được giá trị hợp lệ, API parser báo lỗi. Hai tên ngoại lệ rất dễ nhầm là **java.text.ParseException** và **java.time.format.DateTimeParseException**.

Điều cốt lõi:

- **ParseException** thuộc **java.text**, là **checked exception** (kế thừa trực tiếp Exception). Nhiều API cũ như **SimpleDateFormat.parse(String)** và **NumberFormat.parse(String)** công bố nó trong chữ ký **throws ParseException**.
- **DateTimeParseException** thuộc **java.time.format**, là **unchecked exception** (kế thừa DateTimeException → RuntimeException). Các API hiện đại như **LocalDate.parse** và **DateTimeFormatter.parse** có thể ném nó khi không đọc hoặc không giải quyết được dữ liệu thời gian.
- Cả hai có thể gắn thông tin về **vị trí lỗi**, nhưng **ParseException.getErrorOffset()** và **DateTimeParseException.getErrorIndex()** không phải những “mã lỗi nghiệp vụ” giống nhau. DateTimeParseException còn giữ chuỗi được parse qua **getParsedString()**.
- **Đúng mẫu ký tự chưa đủ** để bảo đảm ngày tồn tại, toàn bộ chuỗi được tiêu thụ, giá trị đúng múi giờ hoặc đáp ứng yêu cầu của một trường đầu vào.
- Khi API chuyển đổi có chính sách checked/unchecked khác nhau, tầng xử lý cần bắt đúng ngoại lệ, thêm **ngữ cảnh trường dữ liệu**, giữ **cause** nếu bọc lỗi và không làm lộ văn bản nhạy cảm trong thông báo người dùng.

Bài viết dành cho **Java 17+**, trình bày cơ chế, lựa chọn API, strict/smart/lenient, vị trí lỗi, ParsePosition, thời gian và locale, cùng những ví dụ độc lập có lệnh biên dịch và kết quả stdout.

## 1. Hệ phân cấp và khác biệt checked/unchecked

Sơ đồ:

~~~text
java.lang.Throwable
  └── java.lang.Exception
       ├── java.text.ParseException                  [checked]
       └── java.lang.RuntimeException
            └── java.time.DateTimeException
                 └── java.time.format.DateTimeParseException [unchecked]
~~~

Hai ngoại lệ không phải quan hệ cha–con trực tiếp và không thể thay thế bằng một nhánh catch cụ thể duy nhất.

| Tiêu chí | ParseException | DateTimeParseException |
| --- | --- | --- |
| Package | java.text | java.time.format |
| Checked hay unchecked? | Checked | Unchecked |
| Lớp cha trực tiếp | Exception | DateTimeException |
| API tiêu biểu | SimpleDateFormat.parse(String), NumberFormat.parse(String) | LocalDate.parse, LocalDateTime.parse, DateTimeFormatter.parse |
| Thông tin vị trí | getErrorOffset() | getErrorIndex() |
| Giữ văn bản đầu vào nguyên dạng? | Không có getter chuyên biệt để lấy toàn chuỗi | getParsedString() |
| Cách xử lý tại nơi gọi | Bắt hoặc khai báo throws ParseException | Không bắt buộc theo compiler nhưng nên xử lý ở ranh giới input |
| Đối tượng kết quả thường gặp | java.util.Date, Number | LocalDate, LocalDateTime, OffsetDateTime, ZonedDateTime, TemporalAccessor |

**Không đồng nhất ngoại lệ của tất cả parser.** Integer.parseInt("bad") ném **NumberFormatException**, cũng là unchecked, chứ không ném ParseException. Các thư viện JSON, XML hoặc CSV có thể có ngoại lệ riêng. Phải xem **chữ ký phương thức thực tế** thay vì suy đoán từ việc API “đang parse chuỗi”.

### Tại sao sự phân biệt này quan trọng?

Với một phương thức dùng API java.text:

~~~java
static java.util.Date readLegacy(String text)
        throws java.text.ParseException {
    java.text.SimpleDateFormat format =
            new java.text.SimpleDateFormat("dd/MM/yyyy");
    return format.parse(text);
}
~~~

Đây là phương thức minh họa, không phải chương trình có main. Vì ParseException checked, caller phải bắt hoặc khai báo phù hợp.

Với java.time:

~~~java
static java.time.LocalDate readModern(String text) {
    return java.time.LocalDate.parse(text);
}
~~~

Phương thức này không cần khai báo throws DateTimeParseException. Tuy nhiên, nếu đọc text từ người dùng, ứng dụng vẫn phải quyết định cách trả về lỗi đầu vào thay vì để unchecked exception làm hỏng request không được xử lý.

## 2. ParseException trong java.text biểu diễn điều gì?

Chữ ký khởi tạo của java.text.ParseException:

~~~java
public ParseException(String message, int errorOffset)
~~~

Thông tin hữu ích:

- **getMessage()**: chuỗi mô tả do parser hoặc bên tạo ngoại lệ cung cấp; không có cấu trúc mã lỗi thống nhất giữa mọi parser.
- **getErrorOffset()**: vị trí báo lỗi do bên phát hiện xác định. Không nên giả định mọi parser luôn cung cấp chỉ số chính xác tuyệt đối cho nguyên nhân sâu xa.
- **getCause()**: cơ chế Throwable thông thường nếu có liên kết nguyên nhân; constructor ParseException không có overload (String, Throwable) chuyên dụng, nhưng có thể dùng **initCause** khi thật sự cần liên kết một cause và cause chưa khởi tạo.

Ví dụ một trình phân tích nội bộ tự tạo ParseException:

~~~java
throw new java.text.ParseException("Invalid token", 4);
~~~

Đây là đoạn mã có thể nằm trong phương thức khai báo throws ParseException. Khi được bắt, **getErrorOffset()** trả 4 vì chính chương trình đã thiết lập offset này.

Đừng mặc định **getErrorOffset()** là số dòng trong file hoặc số cột trong giao diện. Nó là vị trí do parser cung cấp trong ngữ cảnh văn bản của nó. Khi dữ liệu là CSV nhiều dòng, ứng dụng cần tự theo dõi **số dòng**, **tên cột**, **giá trị gốc** và quy tắc chuyển từ vị trí ký tự sang vị trí người dùng có thể hiểu.

## 3. Chương trình Java 17+: bắt checked ParseException

Lưu thành **LegacyParseDemo.java**:

~~~java
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LegacyParseDemo {
    static Date parseLegacy(String text) throws ParseException {
        SimpleDateFormat format =
                new SimpleDateFormat("dd/MM/yyyy", Locale.ROOT);
        format.setLenient(false);
        return format.parse(text);
    }

    public static void main(String[] args) {
        try {
            Date date = parseLegacy("19/10/2026");
            SimpleDateFormat output =
                    new SimpleDateFormat("dd/MM/yyyy", Locale.ROOT);
            output.setLenient(false);
            System.out.println("valid=" + output.format(date));
        } catch (ParseException ex) {
            System.out.println("unexpected parse failure");
        }

        try {
            parseLegacy("31/02/2026");
        } catch (ParseException ex) {
            System.out.println("invalid="
                    + ex.getClass().getSimpleName());
            System.out.println("offsetNonNegative="
                    + (ex.getErrorOffset() >= 0));
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 LegacyParseDemo.java
java LegacyParseDemo
~~~

Kết quả trong môi trường Java 17+ thông thường:

~~~text
valid=19/10/2026
invalid=ParseException
offsetNonNegative=true
~~~

Điểm cần hiểu:

1. **setLenient(false)** giúp SimpleDateFormat từ chối ngày không tồn tại như ngày 31 tháng 2. Chỉ khớp số chữ số với pattern không thể biến ngày ấy thành giá trị lịch hợp lệ.
2. **parseLegacy** khai báo **throws ParseException**. Chương trình main bắt lỗi checked này.
3. Khi lỗi xảy ra, đoạn mã chỉ in việc offset là không âm, tránh phụ thuộc một giá trị index cụ thể của parser cho dữ liệu sai khác nhau.
4. Để kết quả ngày dễ kiểm chứng, chương trình format lại Date về cùng pattern, không in Date.toString() vốn chịu ảnh hưởng múi giờ khi hiển thị.

**Giới hạn lớn:** setLenient(false) kiểm soát việc giải thích các trường ngày, nhưng **không tự bảo đảm** mọi ký tự của đầu vào đã được sử dụng.

## 4. Bẫy parse tiền tố ở API cũ

Các parser trong java.text thường cho phép parse **một phần đầu hợp lệ** của chuỗi. Ví dụ, **SimpleDateFormat.parse(String)** có thể chấp nhận chuỗi ngày ở đầu và bỏ lại hậu tố không thuộc pattern. Tương tự, NumberFormat.parse(String) có thể tạo một Number từ một tiền tố hợp lệ.

Để kiểm soát mức tiêu thụ đầu vào, dùng **java.text.ParsePosition**:

~~~java
ParsePosition position = new ParsePosition(0);
Date date = format.parse(raw, position);

boolean full = date != null
        && position.getIndex() == raw.length();
~~~

Đoạn minh họa trên nằm trong một phương thức với **format** và **raw** hợp lệ. Khác với **parse(String)** ném checked ParseException, overload **parse(String, ParsePosition)** của SimpleDateFormat báo lỗi bằng giá trị **null** và thông tin vị trí.

### Chương trình Java 17+: phát hiện hậu tố thừa

Lưu thành **LegacyPositionDemo.java**:

~~~java
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LegacyPositionDemo {
    static SimpleDateFormat formatter() {
        SimpleDateFormat result =
                new SimpleDateFormat("dd/MM/yyyy", Locale.ROOT);
        result.setLenient(false);
        return result;
    }

    public static void main(String[] args) {
        SimpleDateFormat format = formatter();
        String raw = "19/10/2026tail";

        ParsePosition position = new ParsePosition(0);
        Date parsed = format.parse(raw, position);

        System.out.println("parsed=" + (parsed != null));
        System.out.println("consumed=" + position.getIndex());
        System.out.println("whole="
                + (parsed != null
                   && position.getIndex() == raw.length()));

        ParsePosition invalidPosition = new ParsePosition(0);
        Date invalid = format.parse("31/02/2026", invalidPosition);
        System.out.println("invalid=" + (invalid == null));
        System.out.println("hasErrorIndex="
                + (invalidPosition.getErrorIndex() >= 0));
    }
}
~~~

~~~bash
javac -encoding UTF-8 LegacyPositionDemo.java
java LegacyPositionDemo
~~~

Kết quả:

~~~text
parsed=true
consumed=10
whole=false
invalid=true
hasErrorIndex=true
~~~

Chuỗi **19/10/2026tail** có mười ký tự tạo ngày ở đầu. Parser nhận được một Date, nhưng **position.getIndex() == 10**, nhỏ hơn độ dài toàn chuỗi. Vì thế trường đầu vào theo hợp đồng **phải khớp toàn chuỗi** nên bị từ chối.

ParsePosition có hai trường trạng thái cần phân biệt:

- **getIndex()**: vị trí sau ký tự cuối cùng được parse thành công.
- **getErrorIndex()**: vị trí parser báo lỗi khi thất bại, thường là -1 nếu chưa có lỗi được ghi nhận.

Đừng dùng kiểm tra **parsed != null** như bằng chứng toàn bộ đầu vào hợp lệ. Không nên tự coi chỉ số ParsePosition là số dòng hay số cột; nếu file nguồn có nhiều dòng, ứng dụng cần bổ sung tọa độ dữ liệu.

## 5. DateTimeParseException trong java.time

JDK hiện đại cung cấp thư viện **java.time**, với các loại dữ liệu tách biệt rõ:

- **LocalDate**: ngày theo lịch ISO, không có giờ và múi giờ.
- **LocalTime**: giờ trong ngày, không có ngày/múi giờ.
- **LocalDateTime**: ngày + giờ địa phương, chưa xác định offset/múi giờ.
- **OffsetDateTime**: ngày-giờ với offset cụ thể.
- **ZonedDateTime**: ngày-giờ gắn với vùng múi giờ theo ZoneId.
- **Instant**: một thời điểm trên trục thời gian.

**DateTimeParseException** được ném khi một thao tác parse ngày-giờ không xử lý được chuỗi đầu vào theo formatter và kiểu kết quả yêu cầu. Ngoại lệ này kế thừa DateTimeException nên thuộc nhóm unchecked.

API đọc thông tin chẩn đoán:

~~~java
String originalText = ex.getParsedString();
int errorAt = ex.getErrorIndex();
String description = ex.getMessage();
~~~

Đây là các biểu thức minh họa khi **ex** có kiểu DateTimeParseException. **getParsedString()** có thể chứa nguyên văn dữ liệu đầu vào; khi chuỗi có giá trị nhạy cảm, không ghi toàn bộ vào log công khai. **getErrorIndex()** hỗ trợ xác định vị trí nhưng không phải một mã nghiệp vụ hoặc một cam kết về ký tự gây lỗi sâu xa trong mọi giai đoạn phân tích.

### Chương trình Java 17+: kết quả DateTimeParseException

Lưu thành **ModernDateParseDemo.java**:

~~~java
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class ModernDateParseDemo {
    static void inspect(String raw) {
        try {
            LocalDate date = LocalDate.parse(raw);
            System.out.println("ok=" + date);
        } catch (DateTimeParseException ex) {
            System.out.println("bad=" + ex.getParsedString());
            System.out.println("type="
                    + ex.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        inspect("2026-10-19");
        inspect("2026-02-30");
    }
}
~~~

~~~bash
javac -encoding UTF-8 ModernDateParseDemo.java
java ModernDateParseDemo
~~~

Kết quả:

~~~text
ok=2026-10-19
bad=2026-02-30
type=DateTimeParseException
~~~

**2026-02-30** có các thành phần ký tự giống định dạng ISO yyyy-MM-dd, nhưng ngày 30 tháng 2 không tồn tại. **DateTimeFormatter.ISO_LOCAL_DATE** dùng kiểu giải quyết nghiêm ngặt; việc không tạo được LocalDate hợp lệ dẫn tới DateTimeParseException.

Không nên in trước giá trị giả định của **getErrorIndex()** cho lỗi lịch không hợp lệ: index ở giai đoạn xử lý trường ngày có thể khác index của lỗi ký tự thuần túy. Ở đây in lại parsedString để kiểm chứng mối liên hệ với đầu vào; trong môi trường sản xuất chỉ log giá trị này khi chính sách dữ liệu cho phép.

## 6. Lỗi cú pháp và lỗi giải quyết ngày là hai giai đoạn khác nhau

DateTimeFormatter mô tả quy trình parse thành hai bước lớn:

1. **Text parsing**: đọc ký tự theo pattern/formatter để thu các giá trị trường như YEAR, MONTH_OF_YEAR và DAY_OF_MONTH.
2. **Resolving**: kết hợp những giá trị trường thành ngày/giờ có ý nghĩa, kiểm tra tính nhất quán và các quy tắc lịch.

Vì vậy:

| Chuỗi / lỗi | Vấn đề |
| --- | --- |
| "2026/10/19" với ISO_LOCAL_DATE | Sai ký tự phân tách ở giai đoạn đọc text |
| "2026-13-19" | Tháng nằm ngoài phạm vi cho phép |
| "2026-02-30" | Cú pháp trông hợp lệ nhưng ngày không tồn tại |
| "2026-10-19extra" | Tiền tố hợp lệ nhưng dư hậu tố; phương thức parse toàn chuỗi từ chối |
| "2026-10-19" | Ngày ISO hợp lệ |
| "2024-02-29" | Ngày nhuận hợp lệ |

Thông điệp lỗi của DateTimeParseException thường phân biệt “could not be parsed at index...” với “could not be parsed: ...”, nhưng **ứng dụng không nên parse ngược chuỗi message** để quyết định lỗi nào. Hãy quyết định chính sách hợp lệ bằng formatter, kiểu đích và validation riêng.

### Chương trình Java 17+: kiểm tra vị trí ký tự dư

Lưu thành **TrailingTextDemo.java**:

~~~java
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class TrailingTextDemo {
    public static void main(String[] args) {
        String raw = "2026-10-19extra";

        try {
            LocalDate.parse(raw);
        } catch (DateTimeParseException ex) {
            System.out.println("sameText="
                    + raw.equals(ex.getParsedString()));
            System.out.println("errorAt="
                    + ex.getErrorIndex());
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 TrailingTextDemo.java
java TrailingTextDemo
~~~

Kết quả:

~~~text
sameText=true
errorAt=10
~~~

Với mười ký tự tạo ngày ISO ở đầu, ký tự đầu tiên không được tiêu thụ là chỉ số 10 theo vị trí chuỗi Java bắt đầu từ 0. Đây là trường hợp lỗi cú pháp cụ thể với kết quả xác định; không suy rộng rằng lỗi ngày không nhuận hoặc lỗi resolver cũng luôn có cùng chỉ số.

Các parser mặc định như **LocalDate.parse(text)** mong đợi **toàn bộ text** phù hợp định dạng. Nếu muốn chủ ý phân tích một phân đoạn văn bản lớn, java.time có overload với ParsePosition dành cho xử lý nâng cao, nhưng cần quản lý vị trí và ranh giới riêng.

## 7. ResolverStyle: STRICT, SMART, LENIENT

**java.time.format.ResolverStyle** quyết định cách xử lý những trường ngày/giờ đã đọc:

| Kiểu | Ý nghĩa | Với ngày không tồn tại |
| --- | --- | --- |
| STRICT | Yêu cầu các trường hợp lệ theo lịch/kiểu dữ liệu | Từ chối |
| SMART | Điều chỉnh một số giá trị có thể suy diễn hợp lý | Có thể điều chỉnh ngày cuối tháng |
| LENIENT | Cho phép giải quyết linh hoạt, có thể vượt tháng/ngày | Có thể cuộn sang tháng/ngày khác |

**DateTimeFormatter.ofPattern(...)** mặc định dùng **ResolverStyle.SMART**, trong khi các formatter ISO định sẵn như **ISO_LOCAL_DATE** sử dụng quy tắc chặt chẽ phù hợp. Nếu dữ liệu phải tuân thủ lịch tuyệt đối, hãy **chọn STRICT tường minh**.

Một điểm dễ nhầm là ký hiệu năm:

- **u / uuuu**: proleptic year; phù hợp khi parse LocalDate nghiêm ngặt trong lịch ISO.
- **y / yyyy**: year-of-era. Trong strict parsing, chỉ có year-of-era mà không có era có thể không đủ để tạo LocalDate.

Vì vậy khi tự định nghĩa formatter strict cho **dd/MM/yyyy** theo ý nghĩa năm lịch ISO thông thường, sử dụng **dd/MM/uuuu** thường rõ ràng hơn.

### Chương trình Java 17+: SMART có thể điều chỉnh ngày, STRICT từ chối

Lưu thành **ResolverStyleDemo.java**:

~~~java
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

public class ResolverStyleDemo {
    public static void main(String[] args) {
        DateTimeFormatter smart =
                DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT);
        DateTimeFormatter strict =
                smart.withResolverStyle(ResolverStyle.STRICT);

        System.out.println("smart="
                + LocalDate.parse("29/02/2023", smart));
        System.out.println("leap="
                + LocalDate.parse("29/02/2024", strict));

        try {
            LocalDate.parse("29/02/2023", strict);
        } catch (DateTimeParseException ex) {
            System.out.println("strict=invalid-date");
        }

        DateTimeFormatter strictYearOfEra =
                DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
                        .withResolverStyle(ResolverStyle.STRICT);
        try {
            LocalDate.parse("19/10/2026", strictYearOfEra);
        } catch (DateTimeParseException ex) {
            System.out.println("year-of-era=unresolved");
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ResolverStyleDemo.java
java ResolverStyleDemo
~~~

Kết quả:

~~~text
smart=2023-02-28
leap=2024-02-29
strict=invalid-date
year-of-era=unresolved
~~~

Điểm quan trọng: với formatter SMART, **29/02/2023** có thể được giải quyết thành **2023-02-28**. Nếu ứng dụng nhận ngày sinh, hạn thanh toán hoặc mốc kiểm toán, việc điều chỉnh âm thầm như vậy có thể không phù hợp. Chọn **ResolverStyle.STRICT** và kiểm thử các ngày cuối tháng, năm nhuận.

Không nên lầm tưởng đổi từ SimpleDateFormat sang DateTimeFormatter có nghĩa **mọi formatter java.time luôn strict**. Phải đọc resolver style thực tế của formatter.

## 8. So sánh lenient của API cũ và resolver style của API mới

**SimpleDateFormat** có **setLenient(boolean)**, điều khiển Calendar được dùng để giải quyết các trường ngày. Mặc định cơ chế cũ thường lenient: một giá trị không tồn tại có thể được chuẩn hóa sang ngày khác. Khi cần ngày hợp lệ, hãy gọi **setLenient(false)**.

**DateTimeFormatter** là immutable và cấu hình bằng **withResolverStyle(ResolverStyle.STRICT)** để tạo formatter mới, không sửa formatter cũ. Với lịch ISO, STRICT bảo đảm các trường không tạo ra ngày không tồn tại; SMART có thể điều chỉnh ngày cuối tháng.

Nhưng hai khái niệm **không đồng nhất hoàn toàn**. Cú pháp pattern, cách phân tích số, hỗ trợ locale và xử lý thời gian ở hai API khác nhau. Không nên chuyển máy móc một pattern từ SimpleDateFormat sang DateTimeFormatter rồi kỳ vọng mọi ký tự pattern có cùng ý nghĩa.

Ví dụ khác biệt đặc biệt quan trọng:

| Nội dung | java.text.SimpleDateFormat | java.time.DateTimeFormatter |
| --- | --- | --- |
| Cấu hình nghiêm ngặt | setLenient(false) | withResolverStyle(STRICT) |
| Tính mutability | Mutable | Immutable |
| Dùng chung giữa thread | Không nên chia sẻ một instance mutable nếu không đồng bộ | Formatter được thiết kế immutable và thread-safe |
| Kiểu kết quả | java.util.Date | Thường LocalDate/LocalDateTime/TemporalAccessor |
| Lỗi parse(String) thông thường | Checked ParseException | Unchecked DateTimeParseException |
| Toàn bộ chuỗi | parse(String) có thể chấp nhận tiền tố | LocalDate.parse/DateTimeFormatter.parse thông thường yêu cầu khớp toàn chuỗi |
| Vị trí parse nâng cao | ParsePosition, thường trả null khi lỗi | API ParsePosition có hành vi lỗi riêng tùy overload |

Khi bảo trì hệ thống cũ, có thể giữ SimpleDateFormat ở ranh giới tương thích; với API mới, java.time thường dễ mô hình hóa ngày/giờ hơn và tránh chia sẻ đối tượng mutable không an toàn giữa các thread.

## 9. Locale và ký hiệu tháng bằng chữ

Định dạng ngày theo từ viết tắt của tháng phụ thuộc Locale. Với DateTimeFormatter, việc chỉ định Locale tường minh tránh phụ thuộc ngôn ngữ mặc định trên máy chạy.

Lưu thành **LocaleDateDemo.java**:

~~~java
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

public class LocaleDateDemo {
    public static void main(String[] args) {
        DateTimeFormatter english =
                DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH)
                        .withResolverStyle(ResolverStyle.STRICT);

        LocalDate date = LocalDate.parse("19 Oct 2026", english);
        System.out.println("date=" + date);

        try {
            LocalDate.parse("19 Foo 2026", english);
        } catch (DateTimeParseException ex) {
            System.out.println("unknown-month=invalid");
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 LocaleDateDemo.java
java LocaleDateDemo
~~~

Kết quả:

~~~text
date=2026-10-19
unknown-month=invalid
~~~

Với dữ liệu chuẩn trao đổi giữa hệ thống, định dạng ISO như **2026-10-19** thường thuận tiện hơn chuỗi tháng bằng chữ phụ thuộc locale. Nếu yêu cầu UI theo ngôn ngữ người dùng, dùng Locale đã xác định và test với những biểu diễn địa phương thực tế.

Cũng cần phân biệt **Locale** với **ZoneId**. Locale chủ yếu tác động tới quy ước ngôn ngữ/định dạng; nó **không tự gắn múi giờ** cho LocalDate hoặc LocalDateTime. Nếu chuỗi có offset hoặc múi giờ, dùng kiểu đích phù hợp.

## 10. Chọn đúng kiểu đích: ngày, giờ địa phương hay thời điểm

Một chuỗi **2026-10-19** chỉ mô tả ngày, không cho biết thời gian trong ngày hoặc múi giờ. Vì vậy **LocalDate.parse** thích hợp.

Ngược lại:

- **2026-10-19T14:30:00** có ngày và giờ, không có offset: thường là **LocalDateTime**.
- **2026-10-19T14:30:00+07:00** có offset: thường là **OffsetDateTime**.
- **2026-10-19T07:30:00Z** biểu diễn thời điểm UTC và có thể được phân tích bằng **Instant.parse**.
- Chuỗi có vùng múi giờ như **[Asia/Ho_Chi_Minh]** cần formatter và kiểu hỗ trợ zone nếu định dạng có phần đó.

Không nên lấy LocalDateTime rồi coi nó là một **Instant** nếu chưa có thông tin offset/zone đúng theo nghiệp vụ. Việc thiếu bối cảnh thời gian có thể dẫn tới **ngày giờ đúng cú pháp nhưng sai thời điểm thực tế**, dù không xuất hiện DateTimeParseException.

### Chương trình Java 17+: cùng thời điểm, hai cách biểu diễn

Lưu thành **TemporalTypeDemo.java**:

~~~java
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public class TemporalTypeDemo {
    public static void main(String[] args) {
        System.out.println("date="
                + LocalDate.parse("2026-10-19"));
        System.out.println("local="
                + LocalDateTime.parse("2026-10-19T14:30:00"));
        System.out.println("offset="
                + OffsetDateTime.parse(
                        "2026-10-19T14:30:00+07:00").toInstant());
        System.out.println("instant="
                + Instant.parse("2026-10-19T07:30:00Z"));
    }
}
~~~

~~~bash
javac -encoding UTF-8 TemporalTypeDemo.java
java TemporalTypeDemo
~~~

Kết quả:

~~~text
date=2026-10-19
local=2026-10-19T14:30
offset=2026-10-19T07:30:00Z
instant=2026-10-19T07:30:00Z
~~~

LocalDateTime.toString() thường bỏ phần giây khi bằng 0 trong trường hợp ví dụ, nên **local=2026-10-19T14:30**. Hai dòng cuối diễn tả cùng Instant: offset +07:00 tại 14:30 tương ứng 07:30 UTC. Không biến **LocalDateTime** thành thời điểm toàn cục mà thiếu thông tin zone.

## 11. NumberFormat.parse(String) cũng dùng checked ParseException

ParseException không chỉ xuất hiện khi đọc ngày. Các parser số có hỗ trợ locale như **NumberFormat.parse(String)** cũng khai báo checked ParseException. Đây là khác biệt đáng nhớ với **Integer.parseInt(String)** ném unchecked NumberFormatException.

Ví dụ dùng Locale.GERMANY để đọc chuỗi **1.234,5** theo quy ước dấu phân nhóm và dấu thập phân:

Lưu thành **LegacyNumberParseDemo.java**:

~~~java
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Locale;

public class LegacyNumberParseDemo {
    public static void main(String[] args) {
        NumberFormat formatter =
                NumberFormat.getNumberInstance(Locale.GERMANY);

        try {
            Number value = formatter.parse("1.234,5");
            System.out.println("number=" + value.doubleValue());
        } catch (ParseException ex) {
            System.out.println("unexpected-invalid");
        }

        try {
            formatter.parse("not-a-number");
        } catch (ParseException ex) {
            System.out.println("invalid=ParseException");
        }

        String partial = "1.234,5tail";
        ParsePosition position = new ParsePosition(0);
        Number parsed = formatter.parse(partial, position);
        System.out.println("hasNumber=" + (parsed != null));
        System.out.println("consumed=" + position.getIndex());
        System.out.println("whole="
                + (parsed != null
                   && position.getIndex() == partial.length()));
    }
}
~~~

~~~bash
javac -encoding UTF-8 LegacyNumberParseDemo.java
java LegacyNumberParseDemo
~~~

Kết quả với dữ liệu locale tiêu chuẩn:

~~~text
number=1234.5
invalid=ParseException
hasNumber=true
consumed=7
whole=false
~~~

**NumberFormat.parse(String)** có thể nhận một tiền tố hợp lệ, vì vậy muốn kiểm tra toàn chuỗi thì dùng ParsePosition hoặc một lớp xác thực chặt hơn. Với dữ liệu tiền cần chính xác decimal, cân nhắc DecimalFormat cấu hình **setParseBigDecimal(true)** và chính sách scale phù hợp thay vì mặc định chuyển về double.

**Sự khác nhau không phải do checked exception “nghiêm ngặt hơn” theo nghĩa nghiệp vụ.** Checked/unchecked là chính sách ngôn ngữ tại vị trí gọi; một parser vẫn có thể dễ dãi với tiền tố hoặc giá trị kể cả khi nó khai báo checked exception.

## 12. ParsePosition giữa java.text và java.time không hoàn toàn giống nhau

**java.text.Format** có nhiều overload parse với ParsePosition. Chẳng hạn **SimpleDateFormat.parse(String, ParsePosition)** thường trả **null** khi parser không đọc được và cập nhật **getErrorIndex()**. Caller cần tự kiểm tra null và vị trí tiêu thụ.

Ở **DateTimeFormatter.parse(CharSequence, ParsePosition)**, tài liệu JDK mô tả **DateTimeParseException** có thể được ném khi có lỗi parse, thay vì mọi lỗi đều được mã hóa dưới dạng null như một số java.text formatter. Có thêm phương thức **parseUnresolved(...)** dành cho xử lý giai đoạn phân tích trường chưa giải quyết, với cách sử dụng và kiểm tra lỗi riêng.

Vì vậy không nên khái quát:

~~~java
// KHÔNG ÁP DỤNG MÙ QUÁNG CHO MỌI PARSER:
Object result = parser.parse(text, position);
if (result == null) {
    // assume all parsing errors arrive here
}
~~~

Đây chỉ là **mã giả minh họa**. Hãy đọc hợp đồng của overload cụ thể: kiểu trả về, checked/unchecked exception, cập nhật ParsePosition và có cần kiểm tra toàn chuỗi hay không.

Với phần lớn ứng dụng cần LocalDate đầy đủ, nên dùng **LocalDate.parse(text, formatter)** và xác thực đầu vào trước/sau parse thay vì tự thao tác parseUnresolved.

## 13. Chuyển ngoại lệ sang lỗi có ngữ cảnh tại ranh giới API

Một lớp API không nên lộ nguyên message của parser cho người dùng nếu đầu vào có thể chứa token, dữ liệu cá nhân, nội dung rất dài hoặc biểu diễn không phù hợp. Tầng ứng dụng biết **tên trường**, **định dạng yêu cầu** và **mã lỗi**. Nó có thể chuyển ngoại lệ thư viện sang ngoại lệ/đối tượng lỗi của miền ứng dụng.

Ví dụ một quy trình nhập hóa đơn muốn trường **invoiceDate** theo ISO. Dùng kiểu lỗi riêng không checked để báo validation, nhưng **giữ cause** để logger kỹ thuật vẫn truy được DateTimeParseException.

Lưu thành **InputContextDemo.java**:

~~~java
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class InputContextDemo {
    static class InvalidFieldException extends IllegalArgumentException {
        private final String field;

        InvalidFieldException(
                String field, String message, Throwable cause) {
            super(message, cause);
            this.field = field;
        }

        String field() {
            return field;
        }
    }

    static LocalDate invoiceDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidFieldException(
                    "invoiceDate", "date is required", null);
        }

        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new InvalidFieldException(
                    "invoiceDate", "date must be yyyy-MM-dd", ex);
        }
    }

    public static void main(String[] args) {
        System.out.println("valid="
                + invoiceDate("2026-10-19"));

        try {
            invoiceDate("19/10/2026");
        } catch (InvalidFieldException ex) {
            System.out.println("field=" + ex.field());
            System.out.println("message=" + ex.getMessage());
            System.out.println("causeType="
                    + ex.getCause().getClass().getSimpleName());
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 InputContextDemo.java
java InputContextDemo
~~~

Kết quả:

~~~text
valid=2026-10-19
field=invoiceDate
message=date must be yyyy-MM-dd
causeType=DateTimeParseException
~~~

Trong ví dụ, **LocalDate.parse** được giữ trong một vùng try nhỏ: một DateTimeParseException khác từ logic không liên quan ở ngoài sẽ không bị gán nhầm thành lỗi trường invoiceDate. Wrapper có thể mang thuộc tính **field** có kiểu rõ ràng; frontend không cần parse message để xác định trường sai.

Nếu sử dụng ParseException checked ở tầng legacy, có thể bọc thành một ngoại lệ checked domain riêng **extends Exception**, hoặc chuyển tại API boundary khi đó là chính sách hợp đồng của module. Không nên bọc mọi checked exception thành RuntimeException mà không phân biệt khả năng phục hồi.

## 14. Checked ParseException và cách giữ cause khi chuyển sang domain exception

Một lớp ngoại lệ nghiệp vụ có thể là checked:

~~~java
class ImportDateException extends Exception {
    ImportDateException(String message, Throwable cause) {
        super(message, cause);
    }
}
~~~

Đây là khai báo lớp hợp lệ không có main. Nếu một bước import legacy ném ParseException:

~~~java
try {
    return legacyFormatter.parse(raw);
} catch (java.text.ParseException ex) {
    throw new ImportDateException(
            "Invalid import date in column invoiceDate", ex);
}
~~~

Đây là đoạn minh họa trong một phương thức có trả về **java.util.Date**, khai báo **throws ImportDateException**, với **legacyFormatter** và **raw** đã định nghĩa.

Thông tin còn lại sau chuyển đổi:

- **ImportDateException.getMessage()** biểu diễn ngữ cảnh của bước import.
- **getCause()** giữ chính đối tượng ParseException cũ; có thể kiểm tra **getErrorOffset()** sau khi ép kiểu cause phù hợp.
- Trace của cause vẫn giúp tìm nơi parser legacy phát hiện lỗi.
- Wrapper có stack trace riêng ở điểm chuyển đổi; không cần gọi **setStackTrace()** để “ghép” các frame.

**Không chỉ viết** một ngoại lệ mới với message của lỗi gốc. Thao tác đó cắt chuỗi nguyên nhân và khó chẩn đoán. Đồng thời, không được giả định **getCause()** luôn khác null trong tất cả tình huống; hãy kiểm tra trước khi truy cập nếu wrapper có thể được tạo mà không có cause.

## 15. Nhập nhiều bản ghi: lưu trạng thái lỗi theo từng dòng

Một file CSV gồm nhiều ngày có thể yêu cầu phản hồi mọi dòng sai thay vì dừng ở lỗi đầu tiên. Khi từng dòng độc lập, có thể chuyển ngoại lệ parse thành một đối tượng kết quả có mã và ngữ cảnh.

Lưu thành **DateBatchDemo.java**:

~~~java
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

public class DateBatchDemo {
    record RowResult(int row, LocalDate value, String error) {
        boolean valid() {
            return error == null;
        }
    }

    static RowResult parseRow(int row, String raw) {
        try {
            return new RowResult(row, LocalDate.parse(raw), null);
        } catch (DateTimeParseException ex) {
            return new RowResult(row, null, "INVALID_DATE");
        }
    }

    public static void main(String[] args) {
        List<String> inputs = List.of(
                "2026-10-19", "2026-02-30", "2024-02-29");

        for (int i = 0; i < inputs.size(); i++) {
            RowResult result = parseRow(i + 1, inputs.get(i));
            if (result.valid()) {
                System.out.println("ok=" + result.row()
                        + ":" + result.value());
            } else {
                System.out.println("error=" + result.row()
                        + ":" + result.error());
            }
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 DateBatchDemo.java
java DateBatchDemo
~~~

Kết quả:

~~~text
ok=1:2026-10-19
error=2:INVALID_DATE
ok=3:2024-02-29
~~~

Record là tính năng chuẩn của Java 17. Ví dụ chỉ lưu mã lỗi vì mục tiêu là thông báo theo dòng. Một thiết kế production có thể lưu **error index** (khi có ý nghĩa), tên cột và đối tượng cause trong kết quả nội bộ nếu cần truy vết, đồng thời tránh đưa toàn bộ chuỗi thô vào log công khai.

Đừng tự động chọn chính sách “tiếp tục” cho một giao dịch phải nguyên tử: nếu tất cả dòng cần nhập thành công hoặc cùng rollback, dừng/tổng hợp lỗi phải tuân thủ hợp đồng đó.

## 16. Những lỗi không thuộc ParseException hoặc DateTimeParseException

Những API xử lý văn bản có thể ném loại khác:

| Tình huống | Ngoại lệ hoặc cách báo lỗi điển hình |
| --- | --- |
| Integer.parseInt("bad") | NumberFormatException (unchecked) |
| LocalDate.parse("not-date") | DateTimeParseException (unchecked) |
| SimpleDateFormat.parse("bad") | ParseException (checked) |
| NumberFormat.parse("bad") | ParseException (checked) |
| DateTimeFormatter.ofPattern("pattern invalid") | IllegalArgumentException khi pattern không hợp lệ |
| Gọi một số API parse với đối số null | Có thể NullPointerException, tùy API |
| Đọc file trước khi parse | IOException hoặc ngoại lệ I/O tương ứng |
| Chọn ngày hợp lệ nhưng vượt giới hạn nghiệp vụ | Lỗi validation do ứng dụng định nghĩa |
| Chuyển số lớn xuống kiểu hẹp không chính xác | ArithmeticException nếu dùng toIntExact/intValueExact |
| Parse dữ liệu JSON/XML bằng thư viện khác | Ngoại lệ riêng theo thư viện, không mặc định là hai loại trong bài |

Không có một **ParseException chung cho mọi parser Java**. Đừng đặt một **catch(ParseException)** rồi kỳ vọng nó bắt luôn DateTimeParseException hoặc NumberFormatException. Nếu cần quy ước lỗi thống nhất ở tầng application, xây một mô hình **error code / field / message / cause** và map từng loại parser tại ranh giới tương ứng.

## 17. Các bẫy dữ liệu và bảo mật

1. **Giá trị gốc trong exception:** DateTimeParseException.getParsedString có thể chứa toàn bộ văn bản bị từ chối. Không log nguyên văn khi có token, thông tin người dùng hoặc chuỗi rất dài.
2. **Thông điệp của parser:** getMessage có thể gắn giá trị input và các chi tiết triển khai. Đừng gửi toàn bộ cho client như một hợp đồng API.
3. **Vị trí lỗi:** error index là chỉ số phục vụ chẩn đoán, không nhất thiết là số cột trong UI. Java dùng chỉ số trên chuỗi; các ký tự Unicode đặc biệt có thể khiến cách đếm “ký tự nhìn thấy” khác cách đếm offset.
4. **Khoảng trắng:** LocalDate.parse theo ISO không mặc định cho phép ký tự thừa; nếu giao diện cho phép khoảng trắng đầu/cuối, chỉ dùng strip/trim khi đó là chính sách được công bố.
5. **Độ dài đầu vào:** giới hạn số ký tự và số bản ghi trước khi thực hiện các phân tích phức tạp, nhất là khi nhận dữ liệu từ nguồn không tin cậy.
6. **Múi giờ:** parse LocalDateTime thành công không xác định được Instant nếu thiếu zone/offset. Không được biến việc parse không lỗi thành bằng chứng dữ liệu thời điểm chính xác.
7. **Mặc định hệ thống:** SimpleDateFormat có trạng thái timezone/locale, DateTimeFormatter.ofPattern không chỉ rõ locale có thể phụ thuộc cài đặt. Hãy đặt cấu hình tường minh tại ranh giới dữ liệu.
8. **Thread safety:** không chia sẻ cùng SimpleDateFormat mutable qua nhiều thread mà không kiểm soát. DateTimeFormatter immutable và có thể tái sử dụng an toàn hơn.
9. **Dữ liệu không tồn tại theo lịch:** strict resolver giúp ngăn chỉnh ngày âm thầm; kiểm tra ngày nhuận và ngày cuối tháng.
10. **Không parse text message của ngoại lệ:** để quyết định code/loại lỗi, hãy dùng loại exception, vị trí và cấu trúc dữ liệu của ứng dụng, không dùng nội dung getMessage thay cho contract.

## 18. Sai lầm thường gặp và cách sửa

| Sai lầm | Hậu quả | Hướng sửa |
| --- | --- | --- |
| Nghĩ ParseException và DateTimeParseException đều checked | Chữ ký xử lý lỗi sai | Nhớ java.text checked, java.time unchecked |
| Catch ParseException để bắt lỗi LocalDate.parse | Không bắt được DateTimeParseException | Bắt DateTimeParseException khi thích hợp |
| Catch DateTimeParseException để xử lý SimpleDateFormat.parse | Không giải quyết checked ParseException | Bắt/khai báo ParseException |
| setLenient(false) rồi mặc định parser đọc hết chuỗi | Có thể bỏ hậu tố sai | Dùng ParsePosition kiểm tra getIndex == text.length |
| Dùng ofPattern("dd/MM/yyyy") + STRICT và nghĩ giống uuuu | Year-of-era có thể không resolve khi thiếu era | Dùng uuuu cho ISO proleptic year |
| Mặc định ofPattern luôn STRICT | ofPattern mặc định SMART | Đặt withResolverStyle(STRICT) khi cần |
| Chấp nhận ngày 29/02/2023 vì pattern đúng | Ngày không tồn tại | Resolver strict và kiểm tra lịch |
| In java.util.Date.toString rồi so với chuỗi ngày | Hiển thị phụ thuộc múi giờ | Format có timezone/locale rõ hoặc dùng LocalDate |
| Log ex.getParsedString cho mọi trường | Có thể lộ dữ liệu | Chỉ ghi dữ liệu được phép, ưu tiên field/code |
| Tin error index luôn chỉ đúng ký tự lỗi nghiệp vụ | Parser có hai giai đoạn; chỉ số có ngữ cảnh | Dùng index hỗ trợ chẩn đoán, không dùng làm truth duy nhất |
| Parse LocalDateTime rồi suy ra UTC | Thiếu zone/offset | Dùng OffsetDateTime, ZonedDateTime hoặc Instant đúng dữ liệu |
| Dùng chung SimpleDateFormat mutable giữa thread | Có nguy cơ sai kết quả/data race | Tạo instance riêng hoặc dùng DateTimeFormatter |
| Parse String ngày theo tên tháng mà không chỉ định Locale | Tùy máy, có thể lỗi | Khai báo Locale |
| Catch Exception quá rộng rồi trả null | Che lỗi không liên quan và mất nguyên nhân | Bắt đúng loại và giữ cause |
| Chuyển parser error chỉ thành chuỗi | Mất stack trace, lỗi gốc | Wrapper (message, cause) hoặc Result có mã rõ |
| Không kiểm tra null/rỗng | Có thể phát sinh lỗi kỹ thuật thay vì validation | Kiểm tra trước và báo lỗi trường |

## 19. Checklist triển khai parser đáng tin

Trước khi viết hoặc review một đoạn parse dữ liệu:

1. **Xác định kiểu dữ liệu đích:** Date, LocalDate, LocalDateTime, OffsetDateTime hay Instant? Có cần timezone không?
2. **Xác định format:** ISO chuẩn, dd/MM/uuuu, tháng bằng chữ hay định dạng theo locale?
3. **Đặt resolver style:** STRICT nếu ngày phải tồn tại chính xác, không chỉnh sửa ngầm.
4. **Quyết định chính sách whitespace:** có bỏ đầu/cuối không; có chấp nhận hậu tố không?
5. **Đọc hợp đồng ngoại lệ:** checked ParseException, unchecked DateTimeParseException, NumberFormatException hay loại khác?
6. **Kiểm tra toàn chuỗi:** nhất là với java.text.ParsePosition.
7. **Xử lý giá trị thiếu:** null/rỗng có thông báo riêng hay không?
8. **Giữ ngữ cảnh:** tên trường, dòng CSV, format yêu cầu, mã lỗi.
9. **Bảo toàn cause:** nếu chuyển sang domain exception, dùng constructor nhận cause.
10. **Bảo vệ thông tin:** tránh log parsedString/message nguyên văn nếu nhạy cảm.
11. **Xem thread-safety:** formatter mutable hay immutable; có bị dùng chung nhiều thread?
12. **Viết test:** năm nhuận, cuối tháng, sai separator, chuỗi dư, locale, offset/zone, null, input quá dài.
13. **Tầng xử lý:** nếu có thể sửa ở ranh giới input thì phản hồi validation; nếu không, để exception được ghi nhận và lan truyền đúng hợp đồng.
14. **Không đồng nhất exception với validation:** parser thành công vẫn có thể không thỏa mãn quy tắc nghiệp vụ.

## 20. Câu hỏi tự kiểm tra

**1.** ParseException và DateTimeParseException có cùng tính checked không?  
**Không.** ParseException checked; DateTimeParseException unchecked.

**2.** SimpleDateFormat.parse(String) với dữ liệu không hợp lệ thường công bố loại lỗi nào?  
**ParseException**, nên caller cần catch hoặc throws theo hợp đồng checked.

**3.** LocalDate.parse("2026-02-30") có tạo được ngày không?  
**Không.** ISO strict từ chối ngày không tồn tại bằng DateTimeParseException.

**4.** SimpleDateFormat.setLenient(false) có bảo đảm parser đọc toàn bộ chuỗi không?  
**Không.** Cần kiểm tra ParsePosition hoặc một cơ chế tương đương.

**5.** DateTimeParseException có những getter riêng nào hữu ích?  
**getParsedString()** và **getErrorIndex()**.

**6.** ParseException có getter chuyên dụng lấy lại toàn bộ chuỗi đầu vào không?  
**Không.** Có **getErrorOffset()**; ứng dụng phải tự giữ ngữ cảnh đầu vào nếu cần.

**7.** DateTimeFormatter.ofPattern("dd/MM/uuuu") mặc định resolver style nào?  
**SMART**. Dùng **withResolverStyle(STRICT)** nếu muốn ngày hợp lệ tuyệt đối.

**8.** Vì sao dd/MM/yyyy + STRICT có thể không resolve thành LocalDate?  
Vì yyyy là **year-of-era**; thiếu era có thể không đủ để xác định proleptic year. Dùng **uuuu** khi phù hợp lịch ISO.

**9.** NumberFormat.parse(String) ném ParseException hay NumberFormatException trong API tiêu chuẩn?  
**ParseException checked**; khác với Integer.parseInt.

**10.** Locale và ZoneId có tương đương không?  
**Không.** Locale phục vụ ngôn ngữ/định dạng; ZoneId định nghĩa vùng múi giờ.

**11.** Bọc lỗi bằng new InvalidFieldException("date", message, cause) giúp gì?  
Giữ **thông điệp có ngữ cảnh**, **tên trường** và **cause** của lỗi parse ban đầu.

**12.** DateTimeParseException.getErrorIndex() có thể dùng làm số cột CSV trực tiếp không?  
**Không nên.** Đó là vị trí parser báo trong chuỗi được parse; cần chuyển sang tọa độ record/field.

**13.** DateTimeFormatter.parse với ParsePosition có luôn giống hành vi null-khi-sai của java.text không?  
**Không.** Cần đọc hợp đồng overload; DateTimeFormatter có thể ném DateTimeParseException.

**14.** Parse ngày hợp lệ theo pattern đã đủ để tạo đúng Instant chưa?  
**Chưa.** Nếu thiếu offset hoặc zone, thời điểm toàn cục chưa được xác định.

## Tài liệu tham khảo chuẩn

- [Java SE 21 — java.text.ParseException](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/text/ParseException.html).
- [Java SE 21 — java.text.ParsePosition](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/text/ParsePosition.html).
- [Java SE 21 — java.text.SimpleDateFormat](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/text/SimpleDateFormat.html).
- [Java SE 21 — java.text.NumberFormat](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/text/NumberFormat.html).
- [Java SE 21 — java.time.format.DateTimeParseException](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/format/DateTimeParseException.html).
- [Java SE 21 — java.time.format.DateTimeFormatter](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/format/DateTimeFormatter.html).
- [Java SE 21 — java.time.format.ResolverStyle](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/format/ResolverStyle.html).
- [Java SE 21 — java.time.LocalDate](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/LocalDate.html).
- [JLS 21 — §11.1.1 Checked và unchecked exceptions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html#jls-11.1.1).

**Ghi nhớ:** ParseException là checked exception điển hình của **java.text**, còn DateTimeParseException là unchecked exception của **java.time**. Để parse đáng tin, hãy chọn đúng kiểu đích, formatter, locale và resolver style, kiểm tra **toàn chuỗi** khi cần, bảo toàn nguyên nhân lỗi và luôn bổ sung ngữ cảnh dữ liệu đầu vào thay vì để message/offset của parser làm hợp đồng nghiệp vụ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="api-exceptions-007">ReflectiveOperationException và các lỗi tra cứu bằng reflection</a>

<details>
<summary>Xem chi tiết</summary>

# ReflectiveOperationException và các lỗi tra cứu bằng reflection trong Java

## 1. Mục tiêu bài học

**Reflection** cho phép chương trình Java kiểm tra và thao tác với cấu trúc lớp khi đang chạy: tìm lớp theo tên, tra cứu phương thức, trường, constructor, tạo đối tượng, đọc/ghi trường và gọi phương thức động. Nó rất hữu ích trong framework dependency injection, serialization, plugin loading, ORM, test framework và công cụ phát triển.

Tuy nhiên, reflection chuyển một phần lỗi vốn thường được kiểm tra lúc biên dịch thành **lỗi phát sinh ở runtime**. Ví dụ, phương thức `getMethod("find", String.class)` có thể không tìm thấy method đúng chữ ký; `Class.forName("example.Plugin")` có thể không nạp được class; `Method.invoke(...)` có thể không được phép truy cập hoặc chính phương thức đích ném exception.

Java gom nhiều **checked exceptions liên quan thao tác phản chiếu** dưới `java.lang.ReflectiveOperationException`. Nắm cấu trúc này giúp bạn phân biệt:

- **Lỗi tra cứu metadata**: không tìm thấy class, method hoặc field.
- **Lỗi truy cập/thực thể hóa**: không được truy cập, không thể khởi tạo loại class được chọn.
- **Lỗi do code được gọi thực sự ném**: được bọc trong `InvocationTargetException`.
- **Lỗi runtime khác không thuộc nhánh `ReflectiveOperationException`**: đối số sai, module không cho phép mở quyền, lỗi khởi tạo class, security hoặc linkage.
- **Lỗi hợp đồng API**: dùng `getMethod` thay `getDeclaredMethod`, nhầm `int.class` với `Integer.class`, tra cứu sai tên package hoặc overload.

Bài sử dụng **Java 17+**. Các ví dụ hoàn chỉnh đi kèm tên file, lệnh `javac`/`java` và `stdout` dự kiến; mảnh code hoặc ví dụ sai được ghi rõ. Không cần thêm thư viện ngoài JDK.

## 2. Reflection hoạt động ở hai giai đoạn: *tra cứu* và *sử dụng*

Hãy xem hai bước khác nhau:

```text
Tên class / method / field
          |
          v
[1] TRA CỨU bằng reflection
    Class.forName(...)
    type.getMethod(...)
    type.getDeclaredField(...)
    type.getConstructor(...)
          |
          | trả về Class / Method / Field / Constructor
          v
[2] SỬ DỤNG đối tượng phản chiếu
    method.invoke(target, arguments)
    field.get(target), field.set(...)
    constructor.newInstance(arguments)
          |
          v
    Kết quả hoặc exception ở runtime
```

Lỗi ở giai đoạn **tra cứu** không nhất thiết giống lỗi ở giai đoạn **sử dụng**. `getDeclaredMethod(...)` trả về `Method` khi tìm thấy thành viên private, nhưng việc tìm thấy không tự động đồng nghĩa caller có quyền `invoke(...)`. Ngược lại, có thể tra cứu và truy cập thành công, nhưng **phương thức đích** ném `IllegalArgumentException`, `IOException` hay loại Throwable khác trong thân; Java bọc lỗi từ phương thức đích vào `InvocationTargetException` khi gọi qua `Method.invoke`.

### Hai đoạn minh họa

```java
Class<?> type = Class.forName("java.lang.String");
java.lang.reflect.Method method =
        type.getMethod("substring", int.class);
```

`Class.forName` có thể ném `ClassNotFoundException`; `getMethod` có thể ném `NoSuchMethodException`.

```java
String value = (String) method.invoke("abcdef", 2);
```

Đây là **mảnh mã** nối tiếp đoạn trước, bên trong method xử lý/khai báo checked exception. `invoke` có thể ném `IllegalAccessException` và `InvocationTargetException`, trong khi truyền sai target hoặc đối số có thể gây `IllegalArgumentException` hay ngoại lệ runtime khác.

**Thông điệp chính:** đừng bắt `NoSuchMethodException` ở giai đoạn `invoke` rồi tin rằng nó giải quyết được mọi lỗi. Đó là hai thao tác với **hai hợp đồng ngoại lệ** khác nhau.

## 3. Hệ phân cấp `ReflectiveOperationException`

Cây kiểu rút gọn trong Java 17:

```text
Throwable
└── Exception
    └── ReflectiveOperationException               [checked]
        ├── ClassNotFoundException
        ├── IllegalAccessException
        ├── InstantiationException
        ├── NoSuchFieldException
        ├── NoSuchMethodException
        └── java.lang.reflect.InvocationTargetException
```

Mọi subtype vừa liệt kê là **checked exception**, vì `ReflectiveOperationException` thuộc `Exception` nhưng không phải `RuntimeException`. Caller phải `catch` hoặc khai báo `throws` khi API có thể truyền chúng ra ngoài.

| Ngoại lệ | Giai đoạn thường gặp | Điểm cốt lõi |
| --- | --- | --- |
| `ClassNotFoundException` | Nạp class bằng tên | Loader không tìm được class yêu cầu |
| `NoSuchMethodException` | `getMethod`, `getDeclaredMethod`, tra cứu constructor | Không có chữ ký phù hợp theo API tra cứu |
| `NoSuchFieldException` | `getField`, `getDeclaredField` | Không có field đúng tên trong phạm vi tìm kiếm |
| `IllegalAccessException` | `Method.invoke`, `Field.get/set`, `Constructor.newInstance` | Caller không có quyền truy cập thành viên tương ứng |
| `InstantiationException` | Khởi tạo phản chiếu một class không thể instantiate, chẳng hạn abstract class | Loại đích không thể tạo instance theo hợp đồng |
| `InvocationTargetException` | `Method.invoke`, `Constructor.newInstance` | Thân method/constructor được gọi đã ném một Throwable |

Không phải mọi lỗi reflection đều kế thừa lớp này. Ví dụ:

- `IllegalArgumentException` là unchecked: truyền sai số lượng/kiểu tham số, receiver không phù hợp.
- `InaccessibleObjectException` là unchecked: cố mở quyền truy cập bị giới hạn bởi module.
- `SecurityException` là unchecked: chính sách bảo mật không cho thao tác.
- `ExceptionInInitializerError` và `LinkageError` là lỗi kiểu `Error`: sự cố khởi tạo/liên kết class, không phải `ReflectiveOperationException`.
- `NullPointerException` có thể xảy ra với receiver `null` khi gọi instance method qua reflection.
- `ClassCastException` có thể xảy ra khi **caller ép kiểu sai** kết quả của `invoke`.

Do đó `catch (ReflectiveOperationException e)` rất hữu ích để gom **một nhóm checked reflection failure**, nhưng không được diễn giải là “bắt được tất cả lỗi reflection”.

## 4. Checked exception trong reflection và hợp đồng `throws`

Một helper dùng nhiều API reflection có thể khai báo kiểu cha:

```java
static Object createByName(String className)
        throws ReflectiveOperationException {
    Class<?> type = Class.forName(className);
    return type.getDeclaredConstructor().newInstance();
}
```

Đây là **phương thức minh họa**. `Class.forName` có thể ném `ClassNotFoundException`. `getDeclaredConstructor()` có thể ném `NoSuchMethodException`. `newInstance()` có thể ném `InstantiationException`, `IllegalAccessException` và `InvocationTargetException`. Tất cả nằm dưới `ReflectiveOperationException`.

**Ưu điểm:** `throws ReflectiveOperationException` giúp caller có một hợp đồng checked chung cho quá trình reflection.

**Đánh đổi:** nếu caller cần quyết định khác nhau giữa “class không tồn tại” và “constructor đích ném lỗi”, chỉ bắt kiểu cha có thể làm mất mức phân loại tại chỗ. Bạn vẫn có thể kiểm tra `instanceof` hoặc đặt các `catch` cụ thể trước `catch (ReflectiveOperationException)`.

```java
try {
    Object instance = createByName("sample.Plugin");
} catch (ClassNotFoundException missingClass) {
    // Xử lý cấu hình tên class không có.
} catch (NoSuchMethodException missingConstructor) {
    // Không tìm thấy constructor không tham số.
} catch (ReflectiveOperationException otherReflectionFailure) {
    // Những checked reflection failure còn lại.
}
```

Đây là **mảnh minh họa**, không phải chương trình độc lập. Cần phân biệt `ClassNotFoundException` với các runtime errors, kể cả khi đã có handler chung.

### So với gọi method Java thông thường

Một lời gọi `service.doWork()` được javac kiểm tra chữ ký của method ở compile time. Khi dùng `type.getMethod("doWork").invoke(service)`, phần tìm kiếm theo chuỗi và danh sách `Class<?>` được thực hiện ở runtime. Compiler không thể chứng minh mọi class/overload/visibility đều đúng trong mọi lần chạy. Reflection mang theo hợp đồng lỗi động tương ứng.

## 5. `ClassNotFoundException`: không tìm thấy class theo tên

API phổ biến:

```java
Class<?> type = Class.forName("com.example.Plugin");
```

Đây là mảnh minh họa. Dạng `Class.forName(String)` thực hiện nạp và **khởi tạo** class theo quy tắc API; nếu không tìm được class đích, nó có thể ném `ClassNotFoundException`.

Các lý do phổ biến:

- Tên class đầy đủ sai package hoặc sai chữ hoa/chữ thường.
- JAR chứa class không nằm trong classpath/module path phù hợp.
- Class loader đang dùng không thấy class.
- Cấu hình plugin tham chiếu class đã đổi tên, chưa được triển khai hoặc không còn tồn tại.
- Tên binary name không đúng: nested class có thể có dạng `Outer$Inner` thay vì `Outer.Inner` khi dùng chuỗi tên runtime.

**Không nhầm** `ClassNotFoundException` với `NoClassDefFoundError`. Lỗi thứ hai thuộc `LinkageError` và có thể xuất hiện trong quá trình tải/liên kết một class vốn từng được tìm thấy theo tên nhưng không thể có định nghĩa cần thiết ở runtime, hoặc do lỗi khởi tạo class trước đó. Hai tình huống có quan hệ vận hành nhưng **không cùng nhánh exception**.

### Chương trình chạy được: nạp một class có sẵn và bắt lỗi thiếu class

Tạo file **`ClassLookupDemo.java`**:

```java
public class ClassLookupDemo {
    public static void main(String[] args) {
        try {
            Class<?> found = Class.forName("java.lang.String");
            System.out.println("found=" + found.getName());
        } catch (ClassNotFoundException failure) {
            System.out.println("unexpected");
        }

        try {
            Class.forName("training.example.DefinitelyMissingClass");
        } catch (ClassNotFoundException failure) {
            System.out.println("missing="
                    + failure.getClass().getSimpleName());
        }
    }
}
```

```bash
javac ClassLookupDemo.java
java ClassLookupDemo
```

`stdout`:

```text
found=java.lang.String
missing=ClassNotFoundException
```

Chương trình cố ý tìm tên không tồn tại `training.example.DefinitelyMissingClass`. Bài chạy giả định tên này chưa được ứng dụng cung cấp trên classpath/module path, điều đúng với môi trường Java 17 thông thường.

### `Class.forName` và `ClassLoader.loadClass`

`Class.forName(String)` có hành vi khởi tạo class; `ClassLoader.loadClass(String)` thường chỉ nạp class chứ không khởi tạo ngay. Dạng `Class.forName(name, false, loader)` cho phép yêu cầu **không khởi tạo ngay**. Điều này quan trọng vì class có static initializer có thể gây side effect hoặc thất bại trong lúc load/initialize.

Không coi việc “nạp class” là thao tác thuần kiểm tra tên không có hậu quả trong mọi API. Khi class đến từ plugin không tin cậy hoặc cấu hình người dùng, cần đánh giá chính sách loader và quá trình khởi tạo.

## 6. `NoSuchMethodException`: tìm không đúng chữ ký method

`Class.getMethod(name, parameterTypes...)` tìm **public method** theo tên và danh sách kiểu tham số, kể cả các public method được thừa kế phù hợp. `Class.getDeclaredMethod(name, parameterTypes...)` chỉ tìm các method **được khai báo trực tiếp trong class** đang xét, không tự tìm ở superclass; nó có thể tìm private/protected/package-private.

Ví dụ:

```java
java.lang.reflect.Method method =
        String.class.getMethod("substring", int.class);
```

Đây là mảnh code hợp lệ trong method `throws NoSuchMethodException` hoặc `try/catch`. `String.substring(int)` có public method; kết quả là một đối tượng `Method` mô tả phương thức đó.

Sai tên hoặc sai tham số:

```java
String.class.getMethod("substring", String.class);
```

Đây là mảnh code được phép biên dịch nhưng **ném `NoSuchMethodException` ở runtime**, vì không có public `substring(String)` tương ứng.

### Kiểu tham số phải chính xác

Tra cứu method bằng reflection dựa trên **kiểu tham số được đưa vào** qua những token `Class<?>`:

- `int.class` **khác** `Integer.class`.
- `String.class` khác `Object.class`.
- Các overload cùng tên nhưng khác kiểu tham số là những method khác nhau.
- Với mảng, dùng loại array phù hợp, ví dụ `String[].class`.
- `getMethod` không tự đoán overload dựa trên giá trị bạn dự định truyền sau đó vào `invoke`.
- Với generic, type erasure có thể khiến tham số runtime khác với cách viết type parameter trong source.
- Kiểu trả về **không** nằm trong danh sách đối số tra cứu của `getMethod`.

Ví dụ method `sum(int,int)` phải tra bằng `getMethod("sum", int.class, int.class)`. Truyền `Integer.class` sẽ không tự được unbox trong **giai đoạn tra cứu**.

### Chương trình chạy được: public, private và chữ ký sai

Tạo file **`MethodLookupDemo.java`**:

```java
import java.lang.reflect.Method;

public class MethodLookupDemo {
    public static class Greeting {
        public String greet(String name) {
            return "Hello " + name;
        }

        private int code() {
            return 42;
        }
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        Greeting target = new Greeting();

        Method publicMethod =
                Greeting.class.getMethod("greet", String.class);
        System.out.println("public="
                + publicMethod.invoke(target, "Java"));

        try {
            Greeting.class.getMethod("code");
        } catch (NoSuchMethodException failure) {
            System.out.println("getMethod private=not found");
        }

        Method privateMethod =
                Greeting.class.getDeclaredMethod("code");
        System.out.println("declared=" + privateMethod.getName());

        try {
            Greeting.class.getMethod("greet", int.class);
        } catch (NoSuchMethodException failure) {
            System.out.println("wrong signature=not found");
        }
    }
}
```

```bash
javac MethodLookupDemo.java
java MethodLookupDemo
```

`stdout`:

```text
public=Hello Java
getMethod private=not found
declared=code
wrong signature=not found
```

Trong chương trình:

1. `getMethod("greet", String.class)` thành công vì có public method khớp chữ ký.
2. `getMethod("code")` thất bại vì `code()` là private, không thuộc tập public method mà API tìm.
3. `getDeclaredMethod("code")` tìm được method private **được khai báo trực tiếp**, nhưng tìm được không có nghĩa mọi caller đều được gọi nó.
4. `getMethod("greet", int.class)` thất bại vì `Greeting` không có overload `greet(int)`.

Không nên thay `NoSuchMethodException` bằng `ClassNotFoundException`: ở giai đoạn này class đã có, nhưng **member không đúng tên/chữ ký**.

## 7. `getMethod` khác `getDeclaredMethod` khi kế thừa

Với class con kế thừa một public method:

- `Child.class.getMethod("inherited")` có thể thành công.
- `Child.class.getDeclaredMethod("inherited")` không tìm thấy nếu class con **không tự khai báo** method đó.
- `getDeclaredMethod` có thể tìm private method của chính class con mà `getMethod` không thấy.
- `getMethods()` và `getDeclaredMethods()` có phạm vi tương tự dạng số nhiều; danh sách trả về không nên bị dựa vào thứ tự mặc định để suy ra API ổn định.

### Chương trình độc lập: phương thức kế thừa

Tạo file **`InheritedMethodLookupDemo.java`**:

```java
public class InheritedMethodLookupDemo {
    public static class Parent {
        public void inherited() {
        }
    }

    public static class Child extends Parent {
        private void local() {
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("public inherited="
                    + Child.class.getMethod("inherited").getName());
        } catch (NoSuchMethodException failure) {
            System.out.println("unexpected");
        }

        try {
            Child.class.getDeclaredMethod("inherited");
        } catch (NoSuchMethodException failure) {
            System.out.println("declared inherited=missing");
        }

        try {
            System.out.println("declared private="
                    + Child.class.getDeclaredMethod("local").getName());
        } catch (NoSuchMethodException failure) {
            System.out.println("unexpected");
        }
    }
}
```

```bash
javac InheritedMethodLookupDemo.java
java InheritedMethodLookupDemo
```

`stdout`:

```text
public inherited=inherited
declared inherited=missing
declared private=local
```

Trong các ví dụ về class hierarchy, hãy hỏi: *method được khai báo ở lớp nào?* và *API đang yêu cầu public/inherited hay declared/direct?* Điều này thường giải thích `NoSuchMethodException` nhanh hơn việc kiểm tra lại cùng một tên nhiều lần.

### Lưu ý overload, bridge method và varargs

Với generic, compiler có thể sinh **bridge methods** để bảo toàn overriding sau type erasure. `getDeclaredMethods()` có thể hiển thị cả các member synthetic/bridge. Không viết resolver riêng chỉ dựa vào thứ tự của mảng reflection. Nếu cần phân biệt, `Method.isBridge()` và `Method.isSynthetic()` là các API hữu ích.

Varargs được thể hiện bằng **một tham số mảng** ở mức signature. Ví dụ một phương thức `log(String... parts)` dùng `String[].class` khi tra cứu, không phải một số `String.class` tùy ý.

## 8. `NoSuchFieldException`: không tìm thấy trường đúng phạm vi

`Class.getField(name)` tìm **public field** theo quy tắc tra cứu của API, kể cả những public field được thừa kế. `Class.getDeclaredField(name)` tìm field **được khai báo trực tiếp** trong class, bất kể modifier, nhưng không tìm ở superclass.

Các lỗi thường gặp:

- Nhầm field với getter/setter (JavaBean property không nhất thiết là field).
- Tên field viết sai hoặc khác chữ hoa.
- Field là private nên `getField` không thấy.
- Field ở superclass nên `getDeclaredField` của subclass không thấy.
- Nhầm static field với instance field ở bước `Field.get`/`Field.set`.

### Chương trình chạy được: field công khai và field private

Tạo file **`FieldLookupDemo.java`**:

```java
import java.lang.reflect.Field;

public class FieldLookupDemo {
    public static class Box {
        public int count = 3;
        private String token = "secret";
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        Box box = new Box();

        Field visible = Box.class.getField("count");
        System.out.println("count=" + visible.getInt(box));

        try {
            Box.class.getField("token");
        } catch (NoSuchFieldException failure) {
            System.out.println("public token=missing");
        }

        Field declared = Box.class.getDeclaredField("token");
        System.out.println("declared token=" + declared.getName());

        try {
            Box.class.getDeclaredField("unknown");
        } catch (NoSuchFieldException failure) {
            System.out.println("unknown=missing");
        }
    }
}
```

```bash
javac FieldLookupDemo.java
java FieldLookupDemo
```

`stdout`:

```text
count=3
public token=missing
declared token=token
unknown=missing
```

Lỗi tra cứu field là `NoSuchFieldException`; việc tìm thấy `Field` rồi gọi `getInt`/`set` có thể gặp `IllegalAccessException` hoặc `IllegalArgumentException` tùy quyền và receiver/type.

### `getField` và `getDeclaredField` không trả về giá trị field

Chúng trả về **đối tượng metadata `Field`**, không phải giá trị trực tiếp. Phải dùng `field.get(receiver)`, `field.getInt(receiver)` hay API thích hợp để truy cập dữ liệu. Đối với instance field, receiver phải có kiểu phù hợp. Đối với static field, API cho phép receiver `null` ở những trường hợp tương ứng.

Nếu class có getter công khai, đôi khi gọi getter qua interface bình thường sẽ an toàn, dễ bảo trì và type-safe hơn truy cập private field bằng reflection.

## 9. Không tìm thấy constructor: vẫn là `NoSuchMethodException`

Reflection constructor dùng `Class.getConstructor(parameterTypes...)` và `Class.getDeclaredConstructor(parameterTypes...)`.

- `getConstructor(...)` chỉ tìm **public constructor** phù hợp trong class đích.
- `getDeclaredConstructor(...)` tìm constructor được khai báo trong class đó, kể cả private.
- Constructor **không được kế thừa**, nên không có “constructor ở superclass kế thừa cho subclass”.
- Không tìm thấy constructor khớp danh sách tham số → `NoSuchMethodException`, **không** có lớp `NoSuchConstructorException` trong API chuẩn này.
- `newInstance(arguments...)` trên đối tượng `Constructor` mới là bước **thực sự tạo object**.

### Chương trình chạy được: hai constructor khác nhau

Tạo file **`ConstructorLookupDemo.java`**:

```java
import java.lang.reflect.Constructor;

public class ConstructorLookupDemo {
    public static class Config {
        private final String mode;

        public Config(String mode) {
            this.mode = mode;
        }

        private Config() {
            this.mode = "internal";
        }

        public String mode() {
            return mode;
        }
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        Constructor<Config> publicConstructor =
                Config.class.getConstructor(String.class);
        Config created = publicConstructor.newInstance("production");
        System.out.println("public=" + created.mode());

        try {
            Config.class.getConstructor();
        } catch (NoSuchMethodException failure) {
            System.out.println("public empty=missing");
        }

        Constructor<Config> privateConstructor =
                Config.class.getDeclaredConstructor();
        System.out.println("declared empty="
                + privateConstructor.getParameterCount());
    }
}
```

```bash
javac ConstructorLookupDemo.java
java ConstructorLookupDemo
```

`stdout`:

```text
public=production
public empty=missing
declared empty=0
```

`getDeclaredConstructor()` **tìm được** constructor private không tham số. Ví dụ không tự cố gọi nó ở đây để tránh trộn lỗi *lookup* với vấn đề *access*. Khi có lý do hợp lệ để gọi một constructor non-public, cần kiểm tra chính sách quyền truy cập/module như các mục tiếp theo.

### Tránh `Class.newInstance()`

`Class.newInstance()` đã bị đánh dấu deprecated trong Java hiện đại. Ưu tiên:

```java
Object value = type.getDeclaredConstructor().newInstance();
```

Đây là **mảnh code** với `type` kiểu `Class<?>`. Mẫu dùng `Constructor.newInstance` biểu diễn rõ thao tác tra cứu, quyền truy cập và wrapper `InvocationTargetException`, thay vì đặc điểm nguy hiểm của `Class.newInstance()` khi truyền exception từ constructor ra ngoài theo cách khó kiểm soát checked exception.

## 10. `IllegalAccessException`: tìm được không có nghĩa gọi được

Reflection vẫn phải tôn trọng kiểm tra quyền truy cập Java, trừ khi có cơ chế phù hợp được cho phép. Một object `Method`, `Field` hoặc `Constructor` có thể được tra cứu thành công nhưng vẫn **không được phép sử dụng** từ caller.

`IllegalAccessException` là **checked exception** có thể xuất hiện khi:

- `Method.invoke(...)` cố gọi method mà caller không được truy cập.
- `Constructor.newInstance(...)` cố gọi constructor không được phép.
- `Field.get(...)`/`Field.set(...)` truy cập field không đủ quyền.

### Chương trình chạy được: tra cứu private rồi thử truy cập

Tạo file **`ReflectionAccessDemo.java`**:

```java
import java.lang.reflect.Method;

class PrivateVault {
    private String secret() {
        return "vault-value";
    }
}

public class ReflectionAccessDemo {
    public static void main(String[] args)
            throws ReflectiveOperationException {
        PrivateVault vault = new PrivateVault();
        Method secret =
                PrivateVault.class.getDeclaredMethod("secret");

        try {
            secret.invoke(vault);
        } catch (IllegalAccessException failure) {
            System.out.println("before=IllegalAccessException");
        }

        boolean opened = secret.trySetAccessible();
        System.out.println("trySetAccessible=" + opened);

        if (opened) {
            System.out.println("after=" + secret.invoke(vault));
        }
    }
}
```

```bash
javac ReflectionAccessDemo.java
java ReflectionAccessDemo
```

`stdout` trong một ứng dụng Java 17 thông thường chạy **trên classpath, ở unnamed module**:

```text
before=IllegalAccessException
trySetAccessible=true
after=vault-value
```

Điểm tinh tế: `PrivateVault` là class top-level riêng, **không phải nested class cùng nest** với `ReflectionAccessDemo`. Vì vậy code caller không có quyền trực tiếp gọi method private `secret()` trước khi mở accessibility. Trong cùng unnamed module ở ví dụ, `trySetAccessible()` có thể bật quyền truy cập.

**Không tổng quát hóa output này sang mọi module.** Trong một named module mà package không được `opens` cho module đang gọi, `trySetAccessible()` có thể trả `false`. `setAccessible(true)` có thể ném `InaccessibleObjectException` (unchecked). Với `SecurityManager`/chính sách bảo mật ở môi trường phù hợp, có thể còn gặp `SecurityException`.

### `setAccessible(true)` không phải chìa khóa vượt mọi quyền

Từ Java 9 trở đi, **Java Platform Module System (JPMS)** áp dụng strong encapsulation. Có những trường hợp không được mở quyền truy cập sâu cho private member của module khác, kể cả khi class và method đã được tìm thấy.

- `getDeclaredMethod` chỉ tra cứu metadata.
- `trySetAccessible` yêu cầu nới quyền và trả `true` nếu thành công; có thể trả `false` khi không thể.
- `setAccessible(true)` có thể ném `InaccessibleObjectException` khi không được phép mở.
- Module cung cấp API public nên `exports` package phù hợp nếu cần người dùng bên ngoài.
- Deep reflection vào thành viên non-public giữa modules thường đòi hỏi package được `opens` theo thiết kế hoặc cấu hình chạy phù hợp.

Trong thư viện ứng dụng, **ưu tiên public API và interface** thay vì dựa vào mở private member; không hướng dẫn lạm dụng `--add-opens` chỉ để tránh sửa một thiết kế access đang sai.

## 11. `InvocationTargetException`: phương thức hoặc constructor đích đã ném lỗi

Đây là một điểm rất dễ nhầm.

Giả sử method đích có mã:

```java
public void work() {
    throw new IllegalStateException("business failed");
}
```

Nếu gọi bình thường, caller nhận trực tiếp `IllegalStateException`. Nhưng khi gọi qua:

```java
method.invoke(target);
```

thì **lỗi được method đích ném** được `Method.invoke` bọc trong `InvocationTargetException`. Caller cần đọc `getCause()` hoặc `getTargetException()` của wrapper để biết lỗi đích thực sự là gì.

Không nên ghi “`InvocationTargetException` luôn có nghĩa lookup sai”. Thường lookup đã thành công; **target invocation** đã xảy ra và code đích thất bại.

### Chương trình chạy được: lỗi RuntimeException và checked IOException

Tạo file **`InvocationTargetDemo.java`**:

```java
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class InvocationTargetDemo {
    public static class Worker {
        public void failRuntime() {
            throw new IllegalStateException("invalid state");
        }

        public void failChecked() throws IOException {
            throw new IOException("disk failed");
        }
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        Worker target = new Worker();

        Method runtime =
                Worker.class.getMethod("failRuntime");
        try {
            runtime.invoke(target);
        } catch (InvocationTargetException wrapper) {
            Throwable cause = wrapper.getCause();
            System.out.println("runtime wrapper="
                    + wrapper.getClass().getSimpleName());
            System.out.println("runtime cause="
                    + cause.getClass().getSimpleName());
            System.out.println("runtime message=" + cause.getMessage());
        }

        Method checked =
                Worker.class.getMethod("failChecked");
        try {
            checked.invoke(target);
        } catch (InvocationTargetException wrapper) {
            Throwable cause = wrapper.getTargetException();
            System.out.println("checked cause="
                    + cause.getClass().getSimpleName());
            System.out.println("checked message=" + cause.getMessage());
        }
    }
}
```

```bash
javac InvocationTargetDemo.java
java InvocationTargetDemo
```

`stdout`:

```text
runtime wrapper=InvocationTargetException
runtime cause=IllegalStateException
runtime message=invalid state
checked cause=IOException
checked message=disk failed
```

Giải thích:

1. `getMethod(...)` tìm thấy public method; không có `NoSuchMethodException` trong đường thành công.
2. `invoke(...)` thực sự chạy body method.
3. Body ném `IllegalStateException` ở lần thứ nhất, `IOException` ở lần thứ hai.
4. `Method.invoke` gói Throwable đích trong `InvocationTargetException`.
5. `getCause()` và `getTargetException()` cho phép lấy **đối tượng Throwable gốc** được target ném.

Nếu constructor được gọi qua `Constructor.newInstance(...)` và **thân constructor** ném exception, nó cũng có thể được bọc trong `InvocationTargetException`. Nhưng nếu class là abstract và không thể instantiate, có thể nhận `InstantiationException` trước khi body constructor có cơ hội chạy.

### Không tự động xem cause là “unchecked”

Lỗi trong `InvocationTargetException#getCause()` có thể là checked, unchecked hoặc `Error`. Việc phản chiếu không xóa class runtime của throwable đích. Khi chuyển lỗi thành exception miền nghiệp vụ, giữ đúng `cause` nếu phải bọc tiếp.

## 12. `InstantiationException`: lớp không thể tạo instance

`InstantiationException` là checked exception trong nhánh `ReflectiveOperationException`. Một tình huống quen thuộc là cố khởi tạo **abstract class** bằng reflection.

- `AbstractType.class.getDeclaredConstructor()` có thể **tìm được constructor**.
- Nhưng `constructor.newInstance()` không thể tạo một instance trực tiếp của abstract class → `InstantiationException`.
- Đây là thất bại **ở bước tạo object**, không phải `NoSuchMethodException` của bước lookup.

### Chương trình chạy được: abstract class

Tạo file **`InstantiationReflectionDemo.java`**:

```java
import java.lang.reflect.Constructor;

public class InstantiationReflectionDemo {
    public static abstract class AbstractTask {
        public AbstractTask() {
        }
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        Constructor<AbstractTask> constructor =
                AbstractTask.class.getDeclaredConstructor();

        System.out.println("constructor found");

        try {
            constructor.newInstance();
        } catch (InstantiationException failure) {
            System.out.println("instantiate="
                    + failure.getClass().getSimpleName());
        }
    }
}
```

```bash
javac InstantiationReflectionDemo.java
java InstantiationReflectionDemo
```

`stdout`:

```text
constructor found
instantiate=InstantiationException
```

Nếu class có constructor không tham số public và là class cụ thể phù hợp, `Constructor.newInstance()` có thể tạo instance. Nhưng constructor có thể thực thi logic nghiệp vụ và thất bại, trong trường hợp đó lỗi body được bọc thành `InvocationTargetException`.

**Đừng chỉ kiểm tra “có constructor” để suy ra “tạo instance chắc thành công”.** Còn phải xét class abstract, quyền truy cập, đối số, initialization và điều kiện môi trường.

## 13. `IllegalArgumentException`: sai target hoặc danh sách đối số

Phần lớn lỗi do truyền receiver/argument không đúng khi **invoke** được mô tả bằng ngoại lệ **unchecked**, không phải `ReflectiveOperationException`. Ví dụ:

- Đưa `String` vào một method instance thuộc class `Worker`.
- Gọi method cần hai tham số nhưng chỉ truyền một.
- Truyền `null` cho tham số primitive bắt buộc.
- Truyền kiểu không chuyển đổi được theo quy tắc `Method.invoke`.
- Gọi constructor với số lượng đối số không phù hợp.

Khi lookup:

```java
Method method = Calculator.class.getMethod(
        "add", int.class, int.class);
```

Sau đó invocation:

```java
method.invoke(new Calculator(), "bad", 2);
```

Đây là **hai mảnh code minh họa** giả định có class `Calculator` với public `add(int,int)`. Lookup có thể **thành công**, nhưng invocation với `"bad"` không phù hợp `int` và gây `IllegalArgumentException` — không phải `NoSuchMethodException`.

### Chương trình chạy được: không đánh đồng `invoke` sai và lookup sai

Tạo file **`InvocationArgumentsDemo.java`**:

```java
import java.lang.reflect.Method;

public class InvocationArgumentsDemo {
    public static class Calculator {
        public int add(int a, int b) {
            return a + b;
        }
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        Method add = Calculator.class.getMethod(
                "add", int.class, int.class);

        System.out.println("correct="
                + add.invoke(new Calculator(), 3, 4));

        try {
            add.invoke(new Calculator(), "wrong", 4);
        } catch (IllegalArgumentException failure) {
            System.out.println("bad args=IllegalArgumentException");
        }

        try {
            Calculator.class.getMethod(
                    "add", Integer.class, Integer.class);
        } catch (NoSuchMethodException failure) {
            System.out.println("wrong lookup=NoSuchMethodException");
        }
    }
}
```

```bash
javac InvocationArgumentsDemo.java
java InvocationArgumentsDemo
```

`stdout`:

```text
correct=7
bad args=IllegalArgumentException
wrong lookup=NoSuchMethodException
```

Tại invocation đúng, hai đối số `Integer` từ autoboxing có thể được dùng cho tham số `int` theo quy tắc Method.invoke về unwrapping/widening. Nhưng **getMethod không làm autoboxing khi tìm chữ ký**: `int.class` khác `Integer.class`.

**Nhớ phân biệt:** sai signature ở `getMethod` → `NoSuchMethodException`; method đã tìm thấy nhưng truyền argument không đúng cho `invoke` → thường `IllegalArgumentException`.

## 14. Class initialization và linkage errors không được bao phủ hết

Ngay cả `Class.forName` đã nhận một tên đúng, quá trình **khởi tạo class** có thể thất bại ở static initializer. Điều đó có thể ném `ExceptionInInitializerError`, thuộc nhánh `Error`, chứ không phải `ReflectiveOperationException`.

Một số kiểu cần nhận diện:

| Tình huống | Loại kết quả/lỗi thường gặp | Thuộc `ReflectiveOperationException`? |
| --- | --- | --- |
| Class loader không tìm được class trong lời gọi `Class.forName` | `ClassNotFoundException` | Có |
| Một class cần thiết không thể liên kết/định nghĩa | `NoClassDefFoundError` hoặc `LinkageError` phù hợp | Không |
| Static initializer của class ném lỗi chưa được xử lý | `ExceptionInInitializerError` hoặc lỗi khởi tạo tương ứng | Không |
| Method/constructor được gọi bằng reflection tự ném | `InvocationTargetException` bao lấy lỗi target | Có wrapper |
| `setAccessible(true)` bị module từ chối | `InaccessibleObjectException` | Không |
| Sai argument tại `Method.invoke` | `IllegalArgumentException` | Không |

**Không gộp tất cả thành `ClassNotFoundException`** hoặc bắt `ReflectiveOperationException` rồi chắc chắn chương trình không còn lỗi runtime nào.

### `Class.forName(name, false, loader)` giúp gì?

Nó cho phép yêu cầu **không initialize class ngay**. Nhưng thao tác load/link vẫn có thể thất bại theo hợp đồng loader. Khi sau đó class được initialize lúc dùng, lỗi initializer vẫn có thể xảy ra. Đây là công cụ kiểm soát thời điểm khởi tạo, không phải “vô hiệu hóa mọi lỗi của class”.

Nếu tạo plugin từ tên class do người dùng nhập, cần xem xét *class loader nào được dùng*, class đó có implements interface mong đợi không, và thời điểm nào cho phép thực thi code của plugin.

## 15. Thiết kế factory an toàn kiểu với `Class<T>`

Reflection thường trả `Object`, dễ dẫn tới ép kiểu không an toàn. Nếu API cần tạo object thuộc interface hoặc supertype cụ thể, có thể dùng `Class<? extends T>` hoặc kiểm tra `Class.asSubclass(...)` để gắn hợp đồng kiểu.

Ví dụ phương thức factory đơn giản:

```java
static <T> T instantiate(Class<? extends T> type)
        throws ReflectiveOperationException {
    return type.getDeclaredConstructor().newInstance();
}
```

Đây là **mảnh method hợp lệ**. Nó chỉ hoạt động với class có constructor không tham số **mà caller được phép truy cập**, không phải mọi class tùy ý. Nó có thể ném `NoSuchMethodException`, `IllegalAccessException`, `InstantiationException` và `InvocationTargetException` theo tình huống.

### Chương trình chạy được: factory typed

Tạo file **`TypedReflectionFactoryDemo.java`**:

```java
public class TypedReflectionFactoryDemo {
    public interface Job {
        String run();
    }

    public static class GreetingJob implements Job {
        public GreetingJob() {
        }

        @Override
        public String run() {
            return "job completed";
        }
    }

    static <T> T instantiate(Class<? extends T> type)
            throws ReflectiveOperationException {
        return type.getDeclaredConstructor().newInstance();
    }

    public static void main(String[] args) {
        try {
            Job job = instantiate(GreetingJob.class);
            System.out.println("result=" + job.run());

            Class<?> loaded = Class.forName(
                    TypedReflectionFactoryDemo.GreetingJob.class.getName());
            Class<? extends Job> jobType =
                    loaded.asSubclass(Job.class);
            Job another = instantiate(jobType);
            System.out.println("dynamic=" + another.run());
        } catch (ReflectiveOperationException failure) {
            System.out.println("reflection failed");
        }
    }
}
```

```bash
javac TypedReflectionFactoryDemo.java
java TypedReflectionFactoryDemo
```

`stdout`:

```text
result=job completed
dynamic=job completed
```

Giải thích:

1. `instantiate(GreetingJob.class)` giữ hợp đồng trả về một `Job`.
2. `Class.forName(...)` biểu diễn bước nạp class theo tên ở runtime.
3. `loaded.asSubclass(Job.class)` kiểm tra class thực sự là subtype của `Job`.
4. `instantiate(jobType)` tạo object sau khi đã kiểm tra subtype.
5. Nếu class được nạp không phải một `Job`, `asSubclass` có thể ném **`ClassCastException` (unchecked)**; generic không tự xóa mọi lỗi runtime.

Một factory sản phẩm còn cần kiểm soát **danh sách loại class được phép nạp**, constructor parameters, module access, vòng đời tài nguyên và việc code constructor có thể chạy side effect.

### Không nạp class tùy ý chỉ từ dữ liệu không tin cậy

Tên class từ cấu hình hoặc request không đáng tin có thể làm chương trình tải và thực thi code không mong muốn trong classpath đã triển khai. Dù `Class.forName` có thể tìm class thành công, điều đó **không chứng minh class an toàn để khởi tạo**. Nên dùng allowlist, interface cụ thể, class loader phù hợp và phân quyền ở ranh giới plugin.

## 16. Bọc exception đúng cách khi đi qua tầng trừu tượng

Ở tầng API nội bộ, việc dùng `throws ReflectiveOperationException` có thể hợp lý. Nhưng tại ranh giới nghiệp vụ, caller thường không cần biết framework đang dùng reflection.

Ví dụ chuyển lỗi thành một ngoại lệ miền:

```java
try {
    return instantiate(type);
} catch (ReflectiveOperationException original) {
    throw new IllegalStateException(
            "Cannot create configured service", original);
}
```

Đây là **mảnh mã** giả định `instantiate` và `type` được khai báo. Constructor `(message, cause)` giữ `original` làm `cause`.

Nếu `original` là `InvocationTargetException`, nguyên nhân thực sự của method/constructor đích có thể nằm tại:

```java
original.getCause()
```

Nhưng không được mặc định mọi `ReflectiveOperationException` đều có cause. Ví dụ `NoSuchMethodException` do lookup sai có thể không có cause. Một công cụ chẩn đoán tốt cần đọc cả **loại exception, message, cause và suppressed**, chứ không chỉ gọi `getCause().getMessage()` mà không kiểm tra null.

### Khi xử lý `InvocationTargetException` nên làm gì?

Có ba chính sách hợp lệ tùy tầng:

1. **Giữ nguyên wrapper** làm cause của lỗi miền: bảo toàn đầy đủ ngữ cảnh gọi phản chiếu và nguyên nhân target.
2. **Lấy cause** khi thật sự muốn truyền lỗi nghiệp vụ gốc ra ngoài: phải xét nó có thể là `Exception`, `RuntimeException` hoặc `Error`, đồng thời giữ stack trace và suppressed.
3. **Phân loại cụ thể** theo loại target throwable để quyết định retry, dừng hay phục hồi.

Không tùy tiện `throw (Exception) wrapper.getCause()`: cast này có thể thất bại nếu cause là `Error`. Cũng không thay tất cả lỗi bằng `new RuntimeException(wrapper.getMessage())`, vì làm mất cause target.

## 17. Chương trình tổng hợp: phân loại lookup failure và target failure

Tạo file **`ReflectionFailureDecisionDemo.java`**:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectionFailureDecisionDemo {
    public static class Handler {
        public void run() {
            throw new IllegalArgumentException("invalid request");
        }
    }

    static void call(String methodName)
            throws ReflectiveOperationException {
        Method method =
                Handler.class.getMethod(methodName);
        method.invoke(new Handler());
    }

    public static void main(String[] args) {
        for (String name : new String[] {"missing", "run"}) {
            try {
                call(name);
            } catch (NoSuchMethodException failure) {
                System.out.println(name + ": method not found");
            } catch (InvocationTargetException failure) {
                System.out.println(name + ": target threw "
                        + failure.getCause()
                                .getClass().getSimpleName());
            } catch (ReflectiveOperationException failure) {
                System.out.println(name + ": other reflection failure");
            }
        }
    }
}
```

```bash
javac ReflectionFailureDecisionDemo.java
java ReflectionFailureDecisionDemo
```

`stdout`:

```text
missing: method not found
run: target threw IllegalArgumentException
```

`NoSuchMethodException` ở vòng lặp đầu xảy ra **trước khi invoke**. Ở vòng sau, lookup thành công; `Handler.run()` ném `IllegalArgumentException` và `Method.invoke` bọc nó trong `InvocationTargetException`. Hai thất bại cần chẩn đoán khác nhau.

**Không đảo thứ tự catch**: `catch (ReflectiveOperationException)` phải đứng **sau** các subtype handler nếu bạn muốn phân loại riêng. Nếu đặt trước, các nhánh subtype không còn khả năng được chọn và javac có thể báo catch không thể truy cập.

## 18. Checklist xử lý reflection failure trong ứng dụng

| Câu hỏi | Nên kiểm tra |
| --- | --- |
| Không tìm thấy class? | Tên binary name, package, classpath/module path, class loader |
| Không tìm thấy method? | `getMethod` hay `getDeclaredMethod`, tên, overload, `int.class`/`Integer.class`, type erasure |
| Không tìm thấy field? | `getField` hay `getDeclaredField`, field hay JavaBean property, lớp khai báo |
| Không tìm thấy constructor? | Public/declared, tham số chính xác, class có constructor phù hợp |
| Tìm được nhưng không gọi được? | Java access modifiers, receiver, named module, `exports`/`opens` |
| `Method.invoke` ném `InvocationTargetException`? | Xem `getCause()`/`getTargetException()`; lỗi ở code đích |
| `Constructor.newInstance` ném `InstantiationException`? | Class abstract/không instantiate được |
| `invoke` ném `IllegalArgumentException`? | Sai receiver, số lượng/kiểu args, primitive conversion |
| `Class.forName` gây `ExceptionInInitializerError`? | Static initializer và điều kiện runtime, không chỉ class lookup |
| Chỉ catch `ReflectiveOperationException` đã đủ? | Chưa; xác định chính sách cho unchecked exception và `Error` |
| Ghi log thế nào? | Log Throwable đầy đủ, cause/suppressed, thêm ngữ cảnh an toàn |
| Cấu hình class đến từ nguồn nào? | Xác thực, allowlist, giới hạn quyền thực thi/khởi tạo |
| Có thể tránh reflection không? | Interface, factory tường minh, `ServiceLoader`, public API |

**Không lạm dụng catch rộng rồi trả thành công giả.** Lỗi cấu hình method/constructor là dấu hiệu quan trọng của incompatibility giữa các phiên bản hoặc API contract bị phá vỡ. Cần báo thất bại với đủ ngữ cảnh, không chỉ in stack trace rồi bỏ qua kết quả.

## 19. Những nhầm lẫn phổ biến

**“`ReflectiveOperationException` là unchecked.”** Sai. Nó kế thừa `Exception` và là checked exception.

**“`catch (ReflectiveOperationException)` bắt được tất cả lỗi reflection.”** Sai. `IllegalArgumentException`, `InaccessibleObjectException`, `ExceptionInInitializerError` và `LinkageError` nằm ngoài nhánh này.

**“`ClassNotFoundException` và `NoClassDefFoundError` là cùng một lớp lỗi.”** Sai. Một cái là checked subtype của `ReflectiveOperationException`, một cái là `Error`.

**“`getDeclaredMethod` tìm cả method kế thừa.”** Sai. Nó tìm member khai báo trực tiếp trong class đang xét.

**“`getMethod` có thể tìm private method nếu biết đúng tên.”** Sai. Nó tìm public methods theo phạm vi API.

**“`getDeclaredMethod` tìm thấy private thì chắc chắn gọi được ngay.”** Sai. Tra cứu metadata và quyền invoke là hai bước.

**“`getMethod("sum", Integer.class)` tìm được `sum(int)` nhờ autoboxing.”** Sai. Reflection method lookup yêu cầu token kiểu tham số phù hợp chính xác.

**“Không có constructor không tham số thì sẽ ném `NoSuchFieldException`.”** Sai. Dùng `getDeclaredConstructor()` và gặp `NoSuchMethodException`.

**“Có một lớp `NoSuchConstructorException` chuyên biệt.”** Sai trong API reflection chuẩn dùng ở bài. Constructor lookup thất bại bằng `NoSuchMethodException`.

**“`InvocationTargetException` nghĩa là method không tồn tại.”** Sai. Method thường đã được tìm và target code thực sự ném lỗi.

**“`InvocationTargetException#getCause()` luôn là `RuntimeException`.”** Sai. Target có thể ném checked exception hoặc `Error`.

**“`Method.invoke` với sai arguments sẽ ném `NoSuchMethodException`.”** Sai. Khi `Method` đã được lấy đúng, arguments không hợp lệ thường gây `IllegalArgumentException`.

**“`InstantiationException` đồng nghĩa không tìm thấy constructor.”** Sai. Có thể tìm thấy constructor nhưng class abstract không thể tạo instance.

**“`setAccessible(true)` luôn vượt qua mọi rào cản trong Java 17.”** Sai. Named modules có strong encapsulation.

**“`getDeclaredField("name")` trả giá trị field.”** Sai. Nó trả metadata `Field`; cần dùng `get`/`getInt` với receiver phù hợp.

**“`Class.newInstance()` vẫn là cách ưu tiên trong Java mới.”** Sai. Nó deprecated; ưu tiên `getDeclaredConstructor().newInstance()`.

**“Cứ bọc lỗi reflection bằng thông điệp mới là giữ cause.”** Sai. Phải truyền đối tượng exception gốc qua constructor có cause.

**“Class.forName chỉ tra cứu metadata, không thể kích hoạt code.”** Sai với overload mặc định: class initialization có thể thực thi static initializer.

**“Một tên class nạp thành công có nghĩa plugin chắc chắn an toàn.”** Sai. Việc tạo object và chạy code có thể gây side effect; phải xác minh loại và quyền được phép.

**“Một catch chung rồi return null giúp hệ thống luôn hoạt động.”** Có thể che lỗi cấu hình quan trọng và gây `NullPointerException` muộn hơn. Cần chính sách phục hồi tường minh.

## 20. Bài tập tự kiểm tra

1. **`ReflectiveOperationException` checked hay unchecked?** Checked.
2. **API nào thường ném `ClassNotFoundException`?** `Class.forName(...)` hoặc `ClassLoader.loadClass(...)` trong điều kiện không tìm được class.
3. **Không tìm được public method tên `run` bằng `getMethod` gây gì?** `NoSuchMethodException`.
4. **`getDeclaredMethod` của subclass có tự tìm method ở superclass không?** Không.
5. **Tra cứu private method qua `getMethod` có tìm được không?** Không, nếu không có một public method khớp khác.
6. **`getDeclaredMethod` tìm private có đồng nghĩa gọi được không?** Không, còn điều kiện quyền truy cập.
7. **Không tìm thấy constructor phù hợp bằng `getConstructor` ném gì?** `NoSuchMethodException`.
8. **`getField("x")` và `getDeclaredField("x")` có cùng phạm vi không?** Không, public/inherited khác declared/direct.
9. **Method.invoke gọi target ném `IOException`: caller nhận wrapper gì?** `InvocationTargetException`, với cause là `IOException` target.
10. **Lấy Throwable gốc từ `InvocationTargetException` bằng cách nào?** `getCause()` hoặc `getTargetException()`.
11. **Một abstract class có constructor được tìm thấy thì `newInstance` luôn thành công?** Không, có thể ném `InstantiationException`.
12. **Gọi method có tham số `int`, tra cứu bằng `Integer.class` có tự match không?** Không.
13. **`Method.invoke` với target sai kiểu thường gặp checked NoSuchMethodException hay unchecked IllegalArgumentException?** Thường là unchecked `IllegalArgumentException`.
14. **`trySetAccessible()` có luôn trả true ở named module bị đóng không?** Không; có thể trả false.
15. **`setAccessible(true)` bị strong encapsulation từ chối có thể ném gì?** `InaccessibleObjectException`, unchecked.
16. **`ExceptionInInitializerError` có thuộc `ReflectiveOperationException` không?** Không; nó thuộc nhánh `Error`.
17. **Chỉ catch `ReflectiveOperationException` có bắt `ClassCastException` do cast kết quả sai không?** Không.
18. **Tại sao `Class.forName("...")` cần cân nhắc static initialization?** Overload một tham số có thể khởi tạo class và chạy initializer.
19. **Phương thức `getDeclaredMethods()` có đảm bảo thứ tự mảng cho resolver của bạn không?** Không nên giả định thứ tự.
20. **Generic factory với `Class<? extends T>` giải quyết vấn đề gì?** Tăng an toàn kiểu khi tạo subtype, nhưng vẫn phải xử lý lookup/access/invocation failures.
21. **Muốn báo lỗi nghiệp vụ từ `ReflectiveOperationException` mà vẫn chẩn đoán lỗi gốc?** Bọc với cause: `new DomainException("context", original)`.
22. **Lỗi target bên trong `InvocationTargetException` có thể mang suppressed exceptions riêng không?** Có, vì nó vẫn là đối tượng `Throwable` gốc.
23. **Nếu interface/public factory đủ dùng, reflection có phải mặc định tốt hơn không?** Không; ưu tiên API tĩnh khi phù hợp.
24. **Thông tin quan trọng nhất khi debug `NoSuchMethodException`?** Tên class, tên method, chính xác danh sách kiểu tham số và phạm vi tra cứu.
25. **Nếu plugin class tồn tại nhưng không implements interface yêu cầu thì `Class.asSubclass` có thể gây gì?** `ClassCastException`, unchecked.

## 21. Tài liệu Java 17 chính thức

- [Java SE 17 — ReflectiveOperationException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ReflectiveOperationException.html): supertype checked của các lỗi reflection phổ biến.
- [Java SE 17 — Class](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `forName`, `getMethod`, `getDeclaredMethod`, `getField`, `getDeclaredConstructor`, `asSubclass`.
- [Java SE 17 — ClassNotFoundException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassNotFoundException.html): lỗi nạp class theo tên.
- [Java SE 17 — NoSuchMethodException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/NoSuchMethodException.html): lỗi tra cứu method/constructor.
- [Java SE 17 — NoSuchFieldException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/NoSuchFieldException.html): lỗi tra cứu field.
- [Java SE 17 — IllegalAccessException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/IllegalAccessException.html): quyền truy cập reflective operation.
- [Java SE 17 — InstantiationException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/InstantiationException.html): lỗi không thể tạo đối tượng.
- [Java SE 17 — Method](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html): `invoke`, `isBridge`, `isSynthetic`.
- [Java SE 17 — Field](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html): `get`, `set`, access control.
- [Java SE 17 — Constructor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Constructor.html): constructor lookup và `newInstance`.
- [Java SE 17 — InvocationTargetException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationTargetException.html): wrapper của throwable từ target invocation.
- [Java SE 17 — AccessibleObject](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html): `trySetAccessible`, `setAccessible`, strong encapsulation.
- [Java SE 17 — InaccessibleObjectException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InaccessibleObjectException.html): lỗi runtime khi module không cho mở truy cập.
- [Java Language Specification 17 — `11 Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html): checked/unchecked exceptions.
- [Java Language Specification 17 — `12 Execution](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html): loading, linking, initialization và lỗi liên quan.

**Ghi nhớ:** `ReflectiveOperationException` là **checked superclass** cho nhiều lỗi tra cứu và sử dụng reflection. `ClassNotFoundException` là lỗi tìm class; `NoSuchMethodException`/`NoSuchFieldException` là lỗi tìm member; `IllegalAccessException` là lỗi quyền dùng member; `InstantiationException` là lỗi tạo object; `InvocationTargetException` bọc lỗi **do target method/constructor thực sự ném**. Hãy phân biệt lookup với invocation, dùng đúng `getMethod`/`getDeclaredMethod` và các token kiểu tham số, tôn trọng access/module của Java 17, đồng thời nhớ rằng runtime errors như `InaccessibleObjectException` hoặc `IllegalArgumentException` **không** được `catch (ReflectiveOperationException)` tự động bao phủ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="api-exceptions-008">InvocationTargetException và ngoại lệ thật của phương thức được gọi</a>

<details>
<summary>Xem chi tiết</summary>

# `InvocationTargetException` và ngoại lệ thật của phương thức được gọi trong Java

> **Chủ đề:** Java Core → Exception → Ngoại lệ API. Khi gọi một phương thức bằng **Java Reflection**, `java.lang.reflect.Method.invoke(...)` không truyền trực tiếp mọi exception do phương thức đích ném đến caller. Nếu phần mã được gọi (**target method**) hoàn tất bất thường bằng cách ném một `Throwable`, lời gọi phản chiếu thông thường báo một **`InvocationTargetException`**. Ngoại lệ thực tế nằm trong `getCause()` hoặc `getTargetException()` của wrapper. Nắm vững hai tầng này giúp chẩn đoán nguyên nhân, giữ stack trace, thiết kế xử lý lỗi và không nhầm vấn đề của Reflection với lỗi nghiệp vụ bên trong method.

## 1. Bản chất: một lời gọi, hai tầng ngoại lệ

Xét lời gọi trực tiếp:

```java
service.load();
```

Nếu `load()` ném `IOException`, code gọi trực tiếp có thể nhận `IOException`.

Khi gọi qua Reflection:

```java
java.lang.reflect.Method method =
        Service.class.getMethod("load");

method.invoke(service);
```

nếu **chính `load()`** ném `IOException`, code gọi `invoke` sẽ thấy `InvocationTargetException` (wrapper). Để biết lỗi thực sự:

```java
try {
    method.invoke(service);
} catch (java.lang.reflect.InvocationTargetException wrapper) {
    Throwable targetError = wrapper.getCause();
    System.out.println(targetError.getClass().getName());
}
```

Những đoạn code trên là **minh họa**, giả định `Service`, `service` và các ngoại lệ checked của lookup/invoke được giải quyết trong method thích hợp.

Sơ đồ quan hệ:

```text
Caller
  |
  | Method.invoke(target, args...)
  v
Reflection API
  |
  | Gọi phương thức thật: Service.load()
  v
Service.load()
  |
  | throw new IOException("Không đọc được file")
  v
IOException  <----------------------------+
  |                                        |
  | Reflection bao lỗi khi method ném       |
  v                                        |
InvocationTargetException                 |
  └── cause / targetException ------------+
  |
  v
Caller bắt wrapper và truy nguyên lỗi gốc
```

**Trọng tâm:** `InvocationTargetException` không có nghĩa tên phương thức bị sai, và cũng không đồng nghĩa phương thức đích ném loại exception mang tên đó. Đây là một **tầng biểu diễn lỗi** do Reflection tạo khi mã được gọi ném exception.

## 2. Phân loại `InvocationTargetException` trong cây `Throwable`

Lớp:

```text
java.lang.Throwable
└── java.lang.Exception
    └── java.lang.ReflectiveOperationException
        └── java.lang.reflect.InvocationTargetException
```

Vì vậy:

- `InvocationTargetException` là **checked exception**; nếu gọi `Method.invoke(...)` thì code Java thường phải **bắt** hoặc **khai báo `throws`** nó.
- Đây là subtype của `ReflectiveOperationException` và `Exception`; nó **không** phải `RuntimeException`.
- Target error bên trong có thể là checked `IOException`, unchecked `IllegalArgumentException`, `NullPointerException`, một custom exception hoặc thậm chí `Error`.
- Việc wrapper là checked **không biến target exception thành checked hay ngược lại**; hai đối tượng có kiểu riêng, stack trace riêng và nghĩa khác nhau.
- API `Constructor.newInstance(...)` cũng có thể tạo wrapper này khi constructor đích ném exception.
- Đừng suy rằng **mọi API Reflection** đều tạo `InvocationTargetException`. Nhiều thao tác như lookup field/method, kiểm tra truy cập và khởi tạo class có hợp đồng ngoại lệ riêng.

### 2.1. `getCause()` và `getTargetException()`

Hai phương thức thường dùng:

```java
Throwable getCause()
Throwable getTargetException()
```

Với một `InvocationTargetException` tạo bởi `Method.invoke` khi target thực sự ném lỗi, cả hai cung cấp **cùng ngoại lệ gốc được bọc**. `getTargetException()` là API đặc thù của lớp này; `getCause()` theo cơ chế **exception chaining** chung của `Throwable`, thuận tiện cho code xử lý chuỗi nguyên nhân.

Ví dụ ở điểm bắt:

```java
Throwable cause = wrapper.getCause();
Throwable target = wrapper.getTargetException();
boolean sameObject = cause == target;
```

`sameObject` sẽ là `true` cho wrapper tiêu chuẩn phản ánh cùng target failure.

**Không nhầm:** `wrapper.getStackTrace()` mô tả stack liên quan đến wrapper, trong khi `wrapper.getCause().getStackTrace()` mô tả stack của exception mà method đích đã ném.

### 2.2. Wrapper khác với message

`getMessage()` trên wrapper không phải một cách đáng tin để lấy message của target. Đọc thẳng:

```java
Throwable target = wrapper.getCause();
String targetMessage =
        target == null ? null : target.getMessage();
```

Thông thường wrapper do Reflection tạo sẽ có cause, nhưng khi viết tiện ích nhận các đối tượng `InvocationTargetException` do bên khác tạo thủ công, nên kiểm tra null trước khi truy cập cause.

## 3. Ba loại thất bại khác nhau phải tách biệt

Khi dùng Reflection, có ít nhất ba nhóm sự kiện:

| Nhóm | Ví dụ | Loại exception quan sát |
| --- | --- | --- |
| **Lookup metadata** | Không tìm thấy method tên `load` hoặc sai danh sách tham số trong `getMethod` | `NoSuchMethodException`, không phải lỗi của body method |
| **Gọi Reflection không hợp lệ** | `invoke` sai đối tượng nhận, sai số/kiểu tham số hoặc không được quyền truy cập | Có thể là `IllegalArgumentException`, `NullPointerException`, `IllegalAccessException`... do API gọi phản chiếu |
| **Mã đích ném Throwable** | Method body thực thi rồi ném `IOException`, `IllegalStateException`... | `InvocationTargetException` bọc `Throwable` thực tế |

Một số tình huống khác như thất bại khi khởi tạo lớp, lỗi linkage hoặc vấn đề môi trường module cần xét hợp đồng của từng lời gọi. Không ép mọi lỗi Reflection vào một nhánh.

**Phân biệt then chốt:** `Method.invoke(...)` tự ném `IllegalArgumentException` nếu truyền sai tham số thường **không** phải là `InvocationTargetException`. Nhưng nếu chính **method đích** ném `IllegalArgumentException`, bạn thường nhận **`InvocationTargetException` có cause là `IllegalArgumentException`**.

### 3.1. Có phải method body luôn đã chạy khi có `InvocationTargetException`?

Khi wrapper báo target đã ném, việc thực thi code được gọi đã được kích hoạt và kết thúc bất thường. Tuy nhiên không được suy rằng toàn bộ các dòng trong body đã chạy: method có thể ném ngay đầu, constructor có thể ném trước khi hoàn tất, hoặc target có thể gọi method khác phát sinh lỗi. Ngoài ra khởi tạo lớp liên quan đến target có quy tắc riêng. Muốn biết chính xác, xem stack trace của **cause** và các điều kiện điều khiển.

## 4. Java 17 — ví dụ 1: `Method.invoke` bọc checked `IOException`

Tạo **`ReflectiveMethodFailureDemo.java`**:

```java
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectiveMethodFailureDemo {
    public static final class Service {
        public String load(String key) throws IOException {
            if ("bad".equals(key)) {
                throw new IOException("Không đọc được bad");
            }
            return "loaded:" + key;
        }
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        Service service = new Service();
        Method method =
                Service.class.getMethod("load", String.class);

        Object goodResult = method.invoke(service, "good");
        System.out.println("Good: " + goodResult);

        try {
            method.invoke(service, "bad");
        } catch (InvocationTargetException wrapper) {
            Throwable target = wrapper.getTargetException();
            System.out.println("Wrapper: "
                    + wrapper.getClass().getSimpleName());
            System.out.println("Target: "
                    + target.getClass().getSimpleName());
            System.out.println("Message: " + target.getMessage());
            System.out.println("Same cause: "
                    + (wrapper.getCause() == target));
        }
    }
}
```

Biên dịch/chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 ReflectiveMethodFailureDemo.java
java ReflectiveMethodFailureDemo
```

**Stdout chính xác:**

```text
Good: loaded:good
Wrapper: InvocationTargetException
Target: IOException
Message: Không đọc được bad
Same cause: true
```

Giải thích lần lượt:

1. `Service.class.getMethod("load", String.class)` tìm một public method theo tên và **kiểu tham số**, chưa chạy method.
2. `method.invoke(service, "good")` gọi `load("good")`; method trả chuỗi nên `invoke` trả một `Object` chứa chuỗi đó.
3. `method.invoke(service, "bad")` gọi vào body của `load`; body ném checked `IOException`.
4. Caller của Reflection không nhận `IOException` trực tiếp từ `Method.invoke`; nó nhận `InvocationTargetException`.
5. `getTargetException()` và `getCause()` trỏ đến cùng đối tượng `IOException` thực tế.
6. `main` khai báo `throws ReflectiveOperationException` để giải quyết checked exception từ API Reflection có thể còn thoát, còn nhánh `catch(InvocationTargetException)` chủ động diễn giải target failure ở ca xấu.
7. Nếu gọi `service.load("bad")` trực tiếp, compiler sẽ đòi bắt/khai báo `IOException`; khi gọi qua `Method.invoke`, compiler biết **`InvocationTargetException`**, không “đọc” `throws IOException` của method được mô tả bằng đối tượng `Method`.

**Đừng dùng `method.invoke(service, "bad")` rồi viết `catch(IOException)` đơn độc** để chờ lỗi I/O từ thân method; chữ ký checked của Reflection không công bố một IOException trực tiếp như vậy.

## 5. Java Reflection không tự động ném lỗi target trực tiếp

Chữ ký công khai thường gặp (lược bớt annotation và chi tiết):

```java
public Object invoke(Object obj, Object... args)
        throws IllegalAccessException,
               IllegalArgumentException,
               InvocationTargetException
```

Đây là chữ ký của `java.lang.reflect.Method.invoke` ở mức API cần học. `IllegalArgumentException` là unchecked nên dù có xuất hiện trong phần `throws`, compiler không bắt buộc phải xử lý nó.

Điều quan trọng về biên hợp đồng:

- `Method` có thể đại diện cho method được khai báo `throws IOException`, `throws SQLException` hoặc không checked throws.
- Nhưng **`Method.invoke` có chữ ký riêng**, không tham số hóa kiểu checked exception của target.
- Java không suy luận checked exceptions của target khi method được tìm/chọn động qua reflection.
- `InvocationTargetException` tạo tầng bọc thống nhất để API có thể báo “mã được gọi đã ném lỗi” bất kể target là loại method nào.
- Để xử lý loại target cụ thể, bạn phải **unwrap** rồi áp chính sách, hoặc dùng thao tác gọi trực tiếp nếu có thể biết method tại compile-time.

### 5.1. `throws` của target vẫn có ý nghĩa

Nếu target khai báo `throws IOException`, lời gọi Java trực tiếp chịu hợp đồng đó. Khi call site dùng reflection, compiler áp chữ ký `Method.invoke`; điều đó **không biến unchecked thành checked hay loại bỏ lỗi I/O**. Lỗi I/O vẫn nằm trong đối tượng cause, và tầng gọi phải ra quyết định về cách báo/khôi phục.

Một target có thể ném unchecked exception dù chữ ký của chính nó không khai báo `throws`, và Reflection vẫn bọc target exception ấy.

## 6. Java 17 — ví dụ 2: unchecked exception do **method đích** ném

Tạo **`ReflectiveRuntimeFailureDemo.java`**:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectiveRuntimeFailureDemo {
    public static final class Calculator {
        public int divide(int numerator, int denominator) {
            return numerator / denominator;
        }
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        Method divide = Calculator.class.getMethod(
                "divide", int.class, int.class);
        Calculator calculator = new Calculator();

        Object ok = divide.invoke(calculator, 12, 3);
        System.out.println("Result: " + ok);

        try {
            divide.invoke(calculator, 12, 0);
        } catch (InvocationTargetException wrapper) {
            Throwable actual = wrapper.getCause();
            System.out.println("Actual: "
                    + actual.getClass().getSimpleName());
            System.out.println("Message: " + actual.getMessage());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ReflectiveRuntimeFailureDemo.java
java ReflectiveRuntimeFailureDemo
```

Stdout:

```text
Result: 4
Actual: ArithmeticException
Message: / by zero
```

**Vì sao có wrapper nếu `ArithmeticException` là unchecked?**

`InvocationTargetException` không chỉ dùng để bọc **checked exception**. `Method.invoke` bọc Throwable mà method đích ném. Khi phép chia trong `Calculator.divide` gây `ArithmeticException`, Reflection vẫn tạo wrapper để caller phân biệt với lỗi do chính API `invoke` (như truyền tham số sai).

Một `catch(ArithmeticException)` đặt trực tiếp quanh `divide.invoke(...)` **không tự bắt** target exception ở ví dụ này; caller sẽ thấy wrapper. Nếu ứng dụng muốn xử lý loại `ArithmeticException` cụ thể, phải unwrap và kiểm tra `getCause()`.

### 6.1. Có thể target ném `Error` không?

Có. Method đích có thể ném `AssertionError` hoặc một `Error` khác. `Method.invoke` thường báo target-thrown `Throwable` qua `InvocationTargetException`. Nhưng có những lỗi của quá trình runtime khác (như lỗi khởi tạo class) với hợp đồng riêng, nên không nói “bất kỳ Error nào xảy ra quanh Reflection đều chắc chắn bị bọc”.

Không bắt `Error` rộng chỉ để “phục hồi mọi lỗi JVM”. Một framework chuyên biệt có thể cần giải phóng tài nguyên/log rồi để failure lan theo hợp đồng; rất nhiều `Error` không an toàn để coi là lỗi nghiệp vụ dự kiến.

## 7. `Constructor.newInstance` cũng có thể bọc lỗi target

Reflection không chỉ gọi method; `java.lang.reflect.Constructor<T>.newInstance(Object... initargs)` cho phép tạo object bằng constructor được mô tả. Nếu **constructor body** ném exception, API có thể báo `InvocationTargetException`.

Ví dụ constructor tự từ chối tham số âm:

```java
public Account(int amount) {
    if (amount < 0) {
        throw new IllegalArgumentException("amount âm");
    }
    this.amount = amount;
}
```

Nếu dùng `new Account(-1)` trực tiếp, caller nhận `IllegalArgumentException`. Nếu dùng `constructor.newInstance(-1)`, caller sẽ thường nhận **`InvocationTargetException`**, và cause là `IllegalArgumentException` từ constructor.

### 7.1. Java 17 — ví dụ 3: constructor exception

Tạo **`ReflectiveConstructorFailureDemo.java`**:

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class ReflectiveConstructorFailureDemo {
    public static final class Account {
        private final int amount;

        public Account(int amount) {
            if (amount < 0) {
                throw new IllegalArgumentException("amount âm");
            }
            this.amount = amount;
        }

        public int amount() {
            return amount;
        }
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        Constructor<Account> constructor =
                Account.class.getConstructor(int.class);

        Account valid = constructor.newInstance(5);
        System.out.println("Created: " + valid.amount());

        try {
            constructor.newInstance(-1);
        } catch (InvocationTargetException wrapper) {
            Throwable target = wrapper.getCause();
            System.out.println("Wrapper: "
                    + wrapper.getClass().getSimpleName());
            System.out.println("Target: "
                    + target.getClass().getSimpleName());
            System.out.println("Message: " + target.getMessage());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ReflectiveConstructorFailureDemo.java
java ReflectiveConstructorFailureDemo
```

Stdout:

```text
Created: 5
Wrapper: InvocationTargetException
Target: IllegalArgumentException
Message: amount âm
```

Đây là constructor **public**, nên `getConstructor(int.class)` tìm thấy nó. Constructor không hoàn tất trong trường hợp `-1` và caller không nhận một object `Account` thành công. Wrapper không làm constructor tiếp tục hoặc rollback side effect đã xảy ra trước khi ném lỗi.

### 7.2. Không nhầm với `Class.getDeclaredConstructor`

- `getConstructor(...)` tìm constructor **public** phù hợp.
- `getDeclaredConstructor(...)` có thể tìm constructor được **khai báo trong lớp**, kể cả private; nhưng tìm thấy metadata không đồng nghĩa được phép gọi nó.
- Quyền truy cập Java và giới hạn module có thể khiến lời gọi thất bại trước khi constructor body được chạy.
- Khi không tìm thấy constructor phù hợp, `NoSuchMethodException` thuộc pha lookup, không phải lỗi trong constructor body.
- Việc truy cập private constructor bằng `setAccessible(true)`/`trySetAccessible` có những giới hạn bảo mật/encapsulation và module; đừng dùng để bỏ qua hợp đồng API không được cho phép.

## 8. Java 17 — ví dụ 4: lỗi của Reflection **không** phải target exception

Mục tiêu của ví dụ là phân biệt ba tình huống:

1. Tên method không tồn tại.
2. Cung cấp sai kiểu tham số cho `invoke`.
3. Tham số đúng, method body ném unchecked exception.

Tạo **`ReflectionVsTargetDemo.java`**:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectionVsTargetDemo {
    public static final class Operations {
        public void process(String value) {
            if ("boom".equals(value)) {
                throw new IllegalStateException("Lỗi từ method đích");
            }
            System.out.println("Processed: " + value);
        }
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        Operations target = new Operations();

        try {
            Operations.class.getMethod("missing");
        } catch (NoSuchMethodException e) {
            System.out.println("Lookup: NoSuchMethodException");
        }

        Method method =
                Operations.class.getMethod("process", String.class);

        try {
            method.invoke(target, 123);
        } catch (IllegalArgumentException e) {
            System.out.println("Argument: IllegalArgumentException");
        }

        try {
            method.invoke(target, "boom");
        } catch (InvocationTargetException wrapper) {
            System.out.println("Invocation: "
                    + wrapper.getClass().getSimpleName());
            System.out.println("Target: "
                    + wrapper.getCause().getClass().getSimpleName());
        }

        method.invoke(target, "ok");
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 ReflectionVsTargetDemo.java
java ReflectionVsTargetDemo
```

Stdout:

```text
Lookup: NoSuchMethodException
Argument: IllegalArgumentException
Invocation: InvocationTargetException
Target: IllegalStateException
Processed: ok
```

Giải thích:

- `getMethod("missing")` không tìm thấy member; **không chạy** body nào, nên báo `NoSuchMethodException`.
- `method.invoke(target, 123)` truyền `Integer` cho tham số `String`; Reflection từ chối việc truyền argument sai bằng `IllegalArgumentException`.
- `method.invoke(target, "boom")` có argument hợp lệ, body thực thi và ném `IllegalStateException`; Reflection bọc nó vào `InvocationTargetException`.
- `method.invoke(target, "ok")` hoàn tất thành công và in `Processed: ok`.

Một ví dụ đơn giản nhưng rất quan trọng: **cùng từ “ArgumentException” có thể nằm ở hai tầng khác nhau**. Hãy nhìn xem bạn bắt exception trực tiếp từ API invoke, hay lấy một exception qua `wrapper.getCause()`.

### 8.1. `IllegalAccessException` xảy ra lúc nào?

Nếu caller không được quyền gọi member theo cơ chế truy cập của Java, `Method.invoke` có thể ném checked `IllegalAccessException`. Đây là vấn đề **quyền truy cập phương thức** chứ không phải exception mà body method đã ném. Đừng unwrap `IllegalAccessException` như thể chắc chắn có `InvocationTargetException` đi kèm.

Một lookup thành công không bảo đảm invoke thành công: metadata có thể được tìm thấy bằng `getDeclaredMethod`, nhưng điều đó không tự cấp quyền gọi private member.

### 8.2. `NullPointerException` có thể đến từ hai nguồn

Nếu gọi một **instance method** qua `Method.invoke` mà truyền null receiver, API có thể ném `NullPointerException` do không có đối tượng đích. Nhưng nếu receiver hợp lệ và **body của target** ném `NullPointerException`, caller sẽ thấy wrapper `InvocationTargetException` mang cause `NullPointerException`.

Bởi vậy khi xử lý log, đừng chỉ nhìn tên loại ngoại lệ; cần biết **tầng phát sinh**.

## 9. Khi nào `Method.invoke` trả giá trị, và trả gì?

Với method trả về object hoặc primitive, `Method.invoke` trả kiểu `Object`. Nếu method gốc trả primitive như `int`, Reflection **boxing** nó vào wrapper object như `Integer`. Với method trả `void`, kết quả `invoke` là `null`.

Khi target ném exception, `invoke` **không có giá trị trả về thành công**; exception (wrapper) làm thay đổi luồng điều khiển.

### 9.1. Java 17 — ví dụ 5: method trả primitive, void và static

Tạo **`ReflectiveReturnValuesDemo.java`**:

```java
import java.lang.reflect.Method;

public class ReflectiveReturnValuesDemo {
    public static final class Calculator {
        public int add(int a, int b) {
            return a + b;
        }

        public void ping() {
            System.out.println("Ping");
        }

        public static String version() {
            return "v1";
        }
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        Calculator target = new Calculator();

        Method add = Calculator.class.getMethod(
                "add", int.class, int.class);
        Object sum = add.invoke(target, 2, 3);
        System.out.println("Sum: " + sum);
        System.out.println("Boxed: "
                + sum.getClass().getSimpleName());

        Method ping = Calculator.class.getMethod("ping");
        Object voidResult = ping.invoke(target);
        System.out.println("Void result null: "
                + (voidResult == null));

        Method version = Calculator.class.getMethod("version");
        Object staticResult = version.invoke(null);
        System.out.println("Static: " + staticResult);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ReflectiveReturnValuesDemo.java
java ReflectiveReturnValuesDemo
```

Stdout:

```text
Sum: 5
Boxed: Integer
Ping
Void result null: true
Static: v1
```

Bài học:

- `getMethod("add", int.class, int.class)` dùng **kiểu tham số trong khai báo**, không dùng kiểu argument instance `Integer.class`.
- Kết quả của `int add(...)` được boxing thành `Integer`.
- `void ping()` làm `invoke` trả `null`, không có nghĩa body không chạy.
- Với `static version()`, có thể truyền `null` receiver cho `invoke`, do method static không dùng instance receiver.
- Đối với instance method, receiver phải phù hợp với lớp khai báo và quyền gọi.

Các điều này là **hợp đồng gọi reflection**, độc lập với phân biệt wrapper và target exception, nhưng giúp xác định nơi lỗi có thể xảy ra.

## 10. Chọn handler cho `InvocationTargetException` và nguyên nhân

Khi ở tầng có Reflection, handler nên trả lời:

1. **Lời gọi phản chiếu có thất bại khi chuẩn bị không?** Ví dụ lookup/permission/argument.
2. **Nếu target đã ném, loại thực tế là gì?** Đọc `wrapper.getCause()`.
3. **Tầng hiện tại có thể khôi phục không?** Có nên bỏ qua thao tác, trả failure có cấu trúc, ném lại checked cụ thể hay chuyển thành exception nghiệp vụ?
4. **Đối tượng cause có cần được giữ nguyên?** Tránh tạo một exception mới chỉ chứa message rồi bỏ trace gốc.
5. **Nếu có side effect trước khi ném**, phải đánh giá trạng thái; wrapping không rollback.

Một handler minh họa:

```java
try {
    method.invoke(target, argument);
} catch (InvocationTargetException wrapper) {
    Throwable cause = wrapper.getCause();
    if (cause instanceof java.io.IOException io) {
        reportIoFailure(io);
    } else if (cause instanceof RuntimeException re) {
        throw re;
    } else if (cause instanceof Error er) {
        throw er;
    } else {
        throw new IllegalStateException(
                "Method đích thất bại", cause);
    }
}
```

Đây là **mảnh code** cần định nghĩa `method`, `target`, `argument`, `reportIoFailure`, và xử lý các checked exceptions khác của `Method.invoke`. Nó diễn đạt một chính sách mẫu, không phải chiến lược duy nhất.

### 10.1. Vì sao không catch Exception rồi cast bừa?

Không nên:

```java
try {
    method.invoke(target);
} catch (Exception e) {
    throw (RuntimeException) e.getCause();
}
```

Đây là **phản ví dụ**. `e` có thể là `NoSuchMethodException` hay `IllegalAccessException` với cause `null`; target có thể ném một checked `IOException` hoặc `Error`; cast không an toàn có thể gây `ClassCastException`/`NullPointerException` và che nguyên nhân gốc.

Nếu cần unwrap, **chỉ áp dụng với `InvocationTargetException`**, rồi kiểm tra kiểu target `Throwable` thực tế.

## 11. Java 17 — ví dụ 6: ném lại `IOException` thật và giữ object gốc

Đôi khi một abstraction biết rõ target method có thể ném `IOException` và muốn public API tiếp tục có checked contract `throws IOException`, không biến lỗi thành generic runtime wrapper.

Tạo **`RethrowTargetIOExceptionDemo.java`**:

```java
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class RethrowTargetIOExceptionDemo {
    public static final class Source {
        public void fetch() throws IOException {
            throw new IOException("Kết nối bị ngắt");
        }
    }

    static void runReflectively()
            throws IOException, ReflectiveOperationException {
        Method method = Source.class.getMethod("fetch");
        try {
            method.invoke(new Source());
        } catch (InvocationTargetException wrapper) {
            Throwable target = wrapper.getCause();

            if (target instanceof IOException io) {
                throw io;
            }
            if (target instanceof RuntimeException re) {
                throw re;
            }
            if (target instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(
                    "Checked exception không mong đợi", target);
        }
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        try {
            runReflectively();
        } catch (IOException e) {
            System.out.println("Caught: "
                    + e.getClass().getSimpleName());
            System.out.println("Message: " + e.getMessage());
            System.out.println("Top frame: "
                    + e.getStackTrace()[0].getMethodName());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 RethrowTargetIOExceptionDemo.java
java RethrowTargetIOExceptionDemo
```

Stdout khi chạy trực tiếp chương trình này trên JDK 17:

```text
Caught: IOException
Message: Kết nối bị ngắt
Top frame: fetch
```

Giải thích:

- `runReflectively` bắt checked wrapper do `Method.invoke` ném.
- Khi cause là `IOException`, code **ném lại chính đối tượng cause**, không tạo bản sao.
- `main` nhận `IOException` theo hợp đồng checked của method `runReflectively`.
- Vì dùng lại chính cause, stack trace thường vẫn có frame đầu là vị trí `IOException` được tạo trong `fetch()`, không bị thay bằng điểm unwrap.
- `getStackTrace()[0]` ở đây dùng trong ví dụ kiểm soát stack bình thường. Với code xử lý Throwable bất kỳ, mảng stack có thể rỗng hoặc bị tùy biến; không giả định luôn có frame.
- Với checked Throwable ngoài tập được phép, ví dụ chọn wrapper `IllegalStateException` có cause. Một API thực phải thiết kế chính sách này rõ theo domain, không giấu lỗi thành công.

### 11.1. Có cần khai báo `throws InvocationTargetException` ở API public?

Không bắt buộc nếu tầng Reflection đã bắt và diễn giải wrapper. Một thư viện có thể chọn **ẩn chi tiết Reflection** khỏi API bên ngoài, chỉ công bố exception nghiệp vụ hoặc checked exception phù hợp. Nếu caller thực sự có trách nhiệm quản lý Reflection, việc công bố `ReflectiveOperationException` cũng có thể đúng.

Điểm thiết kế là đừng để mọi tầng ứng dụng phải biết `InvocationTargetException` chỉ vì tầng triển khai bên dưới dùng Reflection.

## 12. Cẩn trọng với `Throwable` có cause là `Error`

Target có thể ném một `Error`, ví dụ `AssertionError`. Khi unwrap:

```java
Throwable cause = wrapper.getCause();
if (cause instanceof Error error) {
    throw error;
}
```

về mặt cú pháp là hợp lệ. Nhưng đừng chủ động bắt rộng `Error` và cố “sửa” mọi trạng thái. Với nhiều `Error`, môi trường hoặc invariant có thể đã không còn đáng tin để tiếp tục.

Nếu framework phải log rồi đưa error lên caller, nhớ phân biệt các failure xảy ra **bên trong target** với các lỗi runtime xảy ra trước khi target được gọi. Chọn chính sách riêng cho lỗi có tính fatal hoặc không thể phục hồi, không chỉ một handler chung.

## 13. Chuỗi `cause`: wrapper Reflection có thể chỉ là lớp ngoài cùng

Giả sử target method đã bọc lỗi I/O:

```java
public void process() {
    try {
        readConfiguration();
    } catch (IOException e) {
        throw new IllegalStateException(
                "Cấu hình không khả dụng", e);
    }
}
```

Đây là method minh họa, cần định nghĩa `readConfiguration()` phù hợp. Khi invoke reflection và target ném `IllegalStateException`, chuỗi có dạng:

```text
InvocationTargetException       ← do Method.invoke tạo
  └── cause: IllegalStateException("Cấu hình không khả dụng")
        └── cause: IOException("Lỗi đọc cấu hình")
```

Một lần gọi `wrapper.getCause()` chỉ trả **cause trực tiếp** là `IllegalStateException`, không trả ngay `IOException` sâu nhất. Muốn xem root cause, bạn có thể đi theo từng liên kết `getCause()`. Nhưng đừng luôn bỏ qua lớp trung gian: nó có thể mang ý nghĩa nghiệp vụ và thông điệp hữu ích.

### 13.1. Java 17 — ví dụ 7: hai tầng cause rõ ràng

Tạo **`ReflectionCauseChainDemo.java`**:

```java
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectionCauseChainDemo {
    public static final class Workflow {
        public void execute() {
            IOException source =
                    new IOException("Đọc dữ liệu lỗi");
            throw new IllegalStateException(
                    "Không hoàn tất workflow", source);
        }
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        Method method =
                Workflow.class.getMethod("execute");

        try {
            method.invoke(new Workflow());
        } catch (InvocationTargetException wrapper) {
            Throwable directCause = wrapper.getCause();
            Throwable rootCause = directCause.getCause();

            System.out.println("Wrapper: "
                    + wrapper.getClass().getSimpleName());
            System.out.println("Direct cause: "
                    + directCause.getClass().getSimpleName());
            System.out.println("Root cause: "
                    + rootCause.getClass().getSimpleName());
            System.out.println("Root message: "
                    + rootCause.getMessage());
            System.out.println("Suppressed on wrapper: "
                    + wrapper.getSuppressed().length);
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ReflectionCauseChainDemo.java
java ReflectionCauseChainDemo
```

Stdout:

```text
Wrapper: InvocationTargetException
Direct cause: IllegalStateException
Root cause: IOException
Root message: Đọc dữ liệu lỗi
Suppressed on wrapper: 0
```

Điều cần hiểu:

- Wrapper do Reflection tạo nằm ngoài một `IllegalStateException` do code ứng dụng tạo.
- `IllegalStateException` lại có `IOException` làm cause.
- `getSuppressed()` là cơ chế khác với cause; ví dụ này không thêm lỗi suppressed nên bằng 0.
- Nếu target dùng try-with-resources rồi gặp lỗi ở `close()`, suppressed exceptions có thể nằm trên **target exception** hoặc một Throwable sâu hơn trong cause chain, không tự nằm trên wrapper Reflection.
- Vì thế một logger chỉ ghi `wrapper.getMessage()` có thể làm mất nhiều thông tin. Hãy ghi **Throwable đầy đủ**, hoặc phân tích chuỗi quan hệ khi có nhu cầu xử lý có cấu trúc.

## 14. Stack trace của wrapper và của method đích

Thông thường hai đối tượng Throwable có hai stack trace:

- `InvocationTargetException`: được tạo ở tầng gọi phản chiếu; trace của nó thường bắt đầu trong các lớp Reflection/JDK.
- Target exception: được tạo hoặc ném trong method thực tế; trace của nó thường dẫn tới dòng trong code đích và các caller bên trong.

Ví dụ output log kiểu:

```text
java.lang.reflect.InvocationTargetException
    at ... Method.invoke(...)
    at ... ReflectiveCaller.main(...)
Caused by: java.io.IOException: Không đọc được bad
    at ... Service.load(...)
    ... N more
```

Đây là **sơ đồ stack trace**, không phải stderr cố định. Tên lớp nội bộ Reflection, số dòng, frame rút gọn và thứ tự có thể khác giữa các bản JDK, chế độ thực thi và logger.

**Để debug:** thường đọc từ `InvocationTargetException` xuống phần **`Caused by:`**. Bên dưới đó là loại và trace của lỗi thực sự do target tạo/lan ra. Không chỉ dừng ở dòng `Method.invoke` rồi kết luận bug trong Java Reflection.

### 14.1. `getTargetException()` có lưu stack như thế nào?

Nó trả **đối tượng Throwable gốc** được wrapper giữ, không tự tạo một exception mới mỗi lần gọi. Đọc `target.getStackTrace()` để xem frame gắn với target. Cần bảo vệ các trường hợp mảng trace rỗng trong công cụ tổng quát, và không dùng dòng nguồn làm hợp đồng máy ổn định.

### 14.2. Có nên thay trace bằng `fillInStackTrace()`?

Thông thường **không**. Khi unwrap rồi ném lại target exception, giữ nguyên trace giúp thấy nguồn phát sinh gốc. Tự gọi `fillInStackTrace()` sẽ thay thông tin vị trí đã ghi, có thể khiến việc chẩn đoán mất dấu target ban đầu. Nếu cần bổ sung ngữ cảnh, nên dùng wrapper mới có `cause` hoặc logging context phù hợp hơn.

## 15. Nếu target body tự ném `InvocationTargetException` thì sao?

Vì đây là một lớp checked exception công khai, một method đích về mặt Java **cũng có thể chủ động ném một `InvocationTargetException`**. Khi gọi method ấy qua `Method.invoke`, Reflection bọc Throwable của target vào **một wrapper khác**; vì thế có thể xuất hiện hai tầng cùng loại:

```text
InvocationTargetException  ← wrapper ngoài do Method.invoke tạo
  └── cause: InvocationTargetException ← đối tượng target đã ném
        └── cause: IOException
```

Điều này thường không cần trong code thông thường, nhưng hữu ích để hiểu vì sao một tiện ích “unwrap tất cả `InvocationTargetException` liên tục” có thể làm mất ranh giới tầng API. Hãy xác định bạn muốn loại bỏ **wrapper do bước invoke hiện tại tạo** hay muốn đi đến exception nguyên nhân sâu nhất theo một chính sách khác.

Không có quy tắc chung bắt buộc “một lời gọi Reflection chỉ có đúng một Throwable trong chuỗi”. Mỗi tầng có thể tạo wrapper của riêng nó.

## 16. `Method.invoke` với `static`, instance, access và signature

Để phân biệt lỗi wrapper/target chính xác, cần biết một số quy tắc gọi Reflection:

| Thao tác | Điều cần đúng | Lỗi sai có thể là |
| --- | --- | --- |
| `getMethod(name, parameterTypes)` | Có public member phù hợp | `NoSuchMethodException` |
| `getDeclaredMethod(...)` | Có member được khai báo trực tiếp | `NoSuchMethodException` |
| `invoke(receiver, args...)` trên instance method | Receiver khác null và phù hợp với declaring class | `NullPointerException` hoặc `IllegalArgumentException` do API |
| `invoke(null, args...)` trên static method | `null` receiver được chấp nhận | Body method vẫn có thể ném wrapper khi lỗi |
| Truyền arguments | Số/kiểu tương thích với chữ ký, tính đến boxing/widening hợp lệ | `IllegalArgumentException` do Reflection |
| Gọi member bị hạn chế truy cập | Có quyền truy cập theo luật Java/module | `IllegalAccessException` hoặc vấn đề encapsulation |
| Body target thực thi rồi ném | Mã đích phát sinh Throwable | `InvocationTargetException` với cause |

**Lưu ý varargs:** chữ ký Java `Object... args` của `Method.invoke` có quy tắc varargs tại **call site của chính Reflection**, khác với tham số varargs của method đích. Với method nhận một tham số là mảng, có thể cần cast `(Object) array` để truyền một đối tượng mảng thay vì bị trình biên dịch trải thành varargs của `invoke`. Đây là vấn đề truyền đối số, không phải cách chữa lỗi target.

### 16.1. Metadata trả về không có nghĩa target chắc chắn được gọi

Method lookup có thể thành công nhưng invoke bị từ chối vì access. Với generic bridge methods, inherited method, visibility hoặc module encapsulation, đôi khi cần hiểu method thực được chọn và context class loader. Không gán mọi lỗi thất bại trước target cho exception của target.

## 17. `Class.newInstance()` và cách dùng nên tránh

API `Class.newInstance()` cũ đã được **deprecated** từ Java 9. Một điểm gây khó hiểu là API này có những hành vi liên quan đến exception của constructor không giống cơ chế bọc nhất quán của `Constructor.newInstance(...)`, và có thể truyền ngoại lệ checked của constructor ra ngoài theo cách gây bất ngờ cho hệ thống type checking.

Trong code Java 17 mới, nên ưu tiên:

```java
Constructor<MyType> constructor =
        MyType.class.getDeclaredConstructor();

MyType instance = constructor.newInstance();
```

Đây là **mảnh code minh họa**; cần import `Constructor`, lớp `MyType` phù hợp và xử lý checked reflection exceptions. Nếu constructor ném lỗi, `Constructor.newInstance` báo `InvocationTargetException`; caller có thể unwrap `getCause()`.

Đừng viết hướng dẫn dựa trên `Class.newInstance()` như API chuẩn được khuyến nghị khi có lựa chọn constructor reflection rõ hơn.

## 18. `MethodHandles` có cơ chế khác với `Method.invoke`

Reflection `Method.invoke` và API `java.lang.invoke.MethodHandle` có hình thức, kiểm tra kiểu và hợp đồng ngoại lệ khác nhau. Khi gọi một method handle qua `invokeExact`/`invoke`/`invokeWithArguments`, ngoại lệ do target ném **có thể lan trực tiếp** theo hợp đồng method-handle invocation, không bắt buộc biến thành `InvocationTargetException` như `Method.invoke`.

Không nên suy:

> “Hễ gọi phương thức động là sẽ có `InvocationTargetException`.”

Chỉ đúng đối với các API cụ thể đã định nghĩa cơ chế bọc, điển hình là `Method.invoke` và `Constructor.newInstance`.

### 18.1. Java 17 — ví dụ 8: MethodHandle cho IOException đi trực tiếp

Tạo **`MethodHandleVsReflectionDemo.java`**:

```java
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class MethodHandleVsReflectionDemo {
    public static void fail() throws IOException {
        throw new IOException("Lỗi gốc");
    }

    public static void main(String[] args) throws Throwable {
        Method method =
                MethodHandleVsReflectionDemo.class.getMethod("fail");

        try {
            method.invoke(null);
        } catch (InvocationTargetException e) {
            System.out.println("Reflection: "
                    + e.getClass().getSimpleName());
            System.out.println("Reflection cause: "
                    + e.getCause().getClass().getSimpleName());
        }

        MethodHandle handle = MethodHandles.lookup()
                .findStatic(MethodHandleVsReflectionDemo.class,
                        "fail", MethodType.methodType(void.class));

        try {
            handle.invokeWithArguments();
        } catch (IOException e) {
            System.out.println("MethodHandle: "
                    + e.getClass().getSimpleName());
            System.out.println("Message: " + e.getMessage());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 MethodHandleVsReflectionDemo.java
java MethodHandleVsReflectionDemo
```

Stdout:

```text
Reflection: InvocationTargetException
Reflection cause: IOException
MethodHandle: IOException
Message: Lỗi gốc
```

**Giải thích:** cùng một static method `fail()` ném `IOException`. `Method.invoke(null)` bọc lỗi target trong `InvocationTargetException`, còn `MethodHandle.invokeWithArguments()` trong ví dụ cho `IOException` đi trực tiếp đến handler. Đây là đối chiếu **API contract**, không phải bằng chứng method handle luôn nhanh hơn hay thay được reflection ở mọi trường hợp.

Việc `main` khai báo `throws Throwable` chỉ để minh họa đầy đủ các checked `Throwable` mà API method handle có thể công bố; trong một API production nên chọn boundary, kiểu exception và chính sách phù hợp.

## 19. Dynamic proxy: có thể có `UndeclaredThrowableException` thay vì wrapper này

`java.lang.reflect.Proxy` sử dụng `InvocationHandler.invoke(...)`, có hợp đồng riêng. Khi handler ném một **checked Throwable không nằm trong các kiểu được interface method cho phép**, lời gọi qua proxy có thể ném `java.lang.reflect.UndeclaredThrowableException` (unchecked), giữ lỗi gốc qua `getUndeclaredThrowable()`/`getCause()`.

Điều này **khác** `InvocationTargetException` được `Method.invoke` tạo khi method đích ném lỗi.

Ví dụ hàm ý:

```text
Proxy interface method: void run()      (không throws IOException)
InvocationHandler: throw new IOException("network")
Caller của proxy: UndeclaredThrowableException
    └── cause: IOException
```

Đừng nhầm hai lớp chỉ vì cùng ở package `java.lang.reflect` và đều liên quan đến “gọi động”.

### 19.1. Khi một `InvocationHandler` tự dùng `Method.invoke`

Nếu handler nhận một `Method method` rồi gọi `method.invoke(realTarget, args)`, nó có thể nhận `InvocationTargetException` từ Reflection. Nếu handler **ném lại nguyên wrapper checked** mà interface proxy method không khai báo loại đó, proxy có thể bọc tiếp thành `UndeclaredThrowableException`. Do đó chuỗi có thể thành:

```text
UndeclaredThrowableException
  └── cause: InvocationTargetException
        └── cause: IOException
```

Một handler thiết kế tốt cần có chính sách **unwrap Reflection wrapper phù hợp** và quyết định cách chuyển lỗi ra hợp đồng của interface proxy. Đừng chuyển tất cả target failures thành một wrapper không liên quan rồi làm caller không thể hiểu.

### 19.2. Không áp logic unwrap Reflection cho mọi proxy error

Proxy có thể ném các lỗi của chính proxy hoặc handler trực tiếp. Nếu không có `InvocationTargetException` thật sự trong chain, không nên ép cast. Xem từng API, rồi đọc cause nếu cần.

## 20. Java 17 — ví dụ 9: `UndeclaredThrowableException` ở proxy

Tạo **`ProxyUndeclaredExceptionDemo.java`**:

```java
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;

public class ProxyUndeclaredExceptionDemo {
    public interface Task {
        void run();
    }

    public static void main(String[] args) {
        Task task = (Task) Proxy.newProxyInstance(
                Task.class.getClassLoader(),
                new Class<?>[] {Task.class},
                (proxy, method, values) -> {
                    throw new IOException("I/O trong handler");
                });

        try {
            task.run();
        } catch (UndeclaredThrowableException e) {
            System.out.println("Proxy wrapper: "
                    + e.getClass().getSimpleName());
            System.out.println("Undeclared: "
                    + e.getUndeclaredThrowable()
                            .getClass().getSimpleName());
            System.out.println("Cause message: "
                    + e.getCause().getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 ProxyUndeclaredExceptionDemo.java
java ProxyUndeclaredExceptionDemo
```

Stdout:

```text
Proxy wrapper: UndeclaredThrowableException
Undeclared: IOException
Cause message: I/O trong handler
```

**Ý nghĩa:** `Task.run()` không có checked `throws` nhưng handler ném checked `IOException`. Proxy buộc báo lỗi theo một wrapper unchecked phù hợp với hợp đồng method của interface. Đây không phải `Method.invoke`; không có `InvocationTargetException` trong ví dụ này.

Khi thiết kế proxy thật, hãy tách riêng lỗi từ business implementation, lỗi của handler và lỗi từ Reflection do handler có thể dùng.

## 21. Những ngộ nhận cần sửa ngay

| Nhầm lẫn | Thực tế |
| --- | --- |
| “`InvocationTargetException` chính là lỗi nghiệp vụ.” | Thường là wrapper do Reflection tạo; target error ở `getCause()`. |
| “Chỉ checked exception từ target mới bị bọc.” | Target có thể ném unchecked exception hoặc Error, và `Method.invoke` vẫn dùng wrapper cho target-thrown Throwable. |
| “`getTargetException()` trả một chuỗi message.” | Trả đối tượng `Throwable`. |
| “`getCause()` và `getTargetException()` luôn khác nhau.” | Trong wrapper Reflection tiêu chuẩn chúng trỏ tới cùng target throwable. |
| “`method.invoke` ném `IOException` trực tiếp vì method gốc `throws IOException`.” | Caller Reflection thấy checked `InvocationTargetException`; unwrap để lấy IOException. |
| “`IllegalArgumentException` quanh invoke luôn từ target.” | Sai tham số có thể làm chính API invoke ném trực tiếp IllegalArgumentException. |
| “Không tìm thấy method thì phải unwrap target.” | `NoSuchMethodException` xảy ra trong lookup, chưa có target invocation. |
| “`IllegalAccessException` là exception từ body.” | Thường là lỗi kiểm tra quyền truy cập của Reflection. |
| “Đã có wrapper thì toàn bộ body method chạy xong.” | Target đã ném và kết thúc bất thường; body có thể dừng rất sớm. |
| “Nếu target body ném NullPointerException thì catch NullPointerException quanh invoke sẽ bắt.” | Thông thường caller thấy `InvocationTargetException` bọc NPE. |
| “Stack trace của wrapper là đủ tìm dòng code gốc.” | Cần xem `Caused by`/stack của target. |
| “`wrapper.getMessage()` luôn giống message lỗi gốc.” | Phải đọc message từ cause thực tế. |
| “Bắt `Exception` rồi cast cause thành RuntimeException được.” | Có thể null/sai kiểu/checked Error; phải phân loại an toàn. |
| “Wrapper tự rollback side effect.” | Exception chaining chỉ giữ thông tin, không hoàn tác trạng thái. |
| “`Constructor.newInstance` không dùng InvocationTargetException.” | Constructor target ném lỗi có thể bị bọc. |
| “`Class.newInstance()` là lựa chọn hiện đại.” | API cũ đã deprecated; ưu tiên constructor reflection. |
| “MethodHandle cũng luôn bọc target vào InvocationTargetException.” | MethodHandle invocation có hợp đồng khác, có thể truyền trực tiếp target Throwable. |
| “Proxy và Reflection đều luôn dùng cùng wrapper.” | Dynamic proxy có thể dùng `UndeclaredThrowableException`. |
| “Chỉ cần log wrapper class name để biết root cause.” | Log cả Throwable/cause chain và thông tin ngữ cảnh an toàn. |

## 22. Cách debug trong một framework dùng Reflection

Nhiều framework gọi method của ứng dụng gián tiếp: test runner, plugin system, dependency injection, command dispatcher, annotation processor runtime hay một bộ đăng ký callback động. Khi log thấy `InvocationTargetException`:

1. **Đọc cấu trúc cause:** `wrapper.getCause()` có loại gì, message gì?
2. **Kiểm tra stack trace của cause** để tìm vị trí trong code method đích.
3. Xác định method/constructor được gọi, receiver và số/kiểu tham số.
4. Nếu gặp `NoSuchMethodException`/`IllegalAccessException` ở ngoài wrapper, hãy sửa lookup/quyền truy cập thay vì debug body.
5. Nếu target checked exception (ví dụ IOException), hỏi tầng nào có chính sách retry/fallback hoặc phải báo failure.
6. Nếu target unchecked exception, kiểm tra input, invariant và trạng thái trước khi ném.
7. Nếu có nhiều wrapper, duyệt chain một cách có mục đích, không chỉ lấy `cause.getCause()` một cách máy móc.
8. Nếu target sử dụng resource, xem `getSuppressed()` trên target Throwable và các cause phù hợp khi có lỗi dọn dẹp.
9. Đánh giá side effect trước thất bại: tác vụ đã ghi dữ liệu, gọi mạng hay sửa cache chưa?
10. Đặt log/monitoring ở ranh giới phù hợp, không in raw stack/cause ra client công khai.

### 22.1. Ví dụ log có ý nghĩa

Thay vì chỉ:

```text
ERROR InvocationTargetException
```

một báo cáo hữu ích hơn có thể lưu:

```text
Operation: Service.load
Wrapper: InvocationTargetException
Cause type: IOException
Cause message: Không đọc được bad
Correlation ID: REQ-42
```

Đây là **định dạng minh họa**, không phải API logger cụ thể. Với logger thực, tốt hơn là truyền chính đối tượng Throwable làm tham số lỗi để giữ stack/cause/suppressed, đồng thời ghi ngữ cảnh theo chính sách dữ liệu.

## 23. Bảo toàn stack và nguyên nhân khi chuyển tầng

Một adapter có thể muốn chuyển lỗi do target sang exception nghiệp vụ:

```java
try {
    method.invoke(target);
} catch (InvocationTargetException wrapper) {
    throw new ServiceOperationException(
            "Không hoàn tất thao tác", wrapper.getCause());
}
```

Đây là **mảnh code** giả định `ServiceOperationException` có constructor nhận cause và các checked exception Reflection khác được xử lý.

Bạn có hai lựa chọn có ý nghĩa khác nhau:

- **Giữ chính cause target làm cause của wrapper nghiệp vụ:** thuận tiện cho caller phân loại nguyên nhân và giảm lớp Reflection trong cause chain nếu tầng ngoài không cần chi tiết Reflection.
- **Giữ nguyên `InvocationTargetException` làm cause:** hữu ích khi cần cho thấy chính tầng Reflection thất bại, nhưng có thể để lộ chi tiết implementation cho nhiều tầng.

Không có quy tắc bắt buộc mọi nơi phải xóa wrapper Reflection. Quan trọng là **giữ đường nguyên nhân**, không làm mất target exception hoặc ép tất cả trường hợp về một loại lỗi không phù hợp.

### 23.1. Không tự động xóa thông tin về suppressed

Nếu target throwable có suppressed exceptions, việc truyền **cùng object target** làm cause thường giữ lại suppressed ở đúng đối tượng đó. Nếu tạo một exception mới chỉ với `getMessage()` và không truyền cause, thông tin suppressed có thể bị mất khỏi chuỗi báo cáo.

Điều này đặc biệt quan trọng khi target dùng try-with-resources: body ném một lỗi, `close()` ném thêm lỗi phụ, và Reflection lại bọc primary thành `InvocationTargetException`.

## 24. Khi nào nên dùng Reflection thay vì lời gọi trực tiếp?

Nếu kiểu target và tên method đã biết tại compile-time, lời gọi trực tiếp thường:

- Đơn giản, có type checking và kiểm tra checked exception rõ.
- Dễ refactor, được IDE hỗ trợ.
- Không cần lookup hoặc unwrap wrapper Reflection.
- Dễ đọc và test hơn.

Reflection phù hợp khi **động** thật sự là yêu cầu: framework nạp plugin, tìm handler theo annotation, tạo object theo metadata, gọi method được chọn lúc chạy, test runner, dependency injection hoặc mô hình extension.

**Quy tắc thiết kế:** không dùng Reflection chỉ để có thể gọi một method đã biết rõ. Nếu phải dùng, nên đóng gói logic lookup/invoke/error translation vào một tầng, không lan `InvocationTargetException` khắp mã nghiệp vụ nếu điều đó không cần thiết.

## 25. Bài tập thực hành

### Bài 1 — Phân biệt wrapper và target

Chạy `ReflectiveMethodFailureDemo`. Thay lỗi của `load("bad")` thành `IllegalStateException("input")`. Dự đoán `wrapper.getClass()` và `wrapper.getCause().getClass()`. Có cần `catch(IOException)` quanh `Method.invoke` không? Giải thích bằng chữ ký API.

### Bài 2 — Sai argument khác với target-throw

Trong `ReflectionVsTargetDemo`, đổi lời gọi `method.invoke(target, 123)` thành `method.invoke(target, "boom")` rồi quan sát loại ngoại lệ tại caller. Dự đoán vì sao một trường hợp là `IllegalArgumentException` trực tiếp của Reflection, còn trường hợp kia là wrapper có cause `IllegalStateException`.

### Bài 3 — Constructor target

Thêm một điều kiện `if (amount == 0) throw new IllegalStateException("zero");` vào `Account`. Gọi constructor reflection với 0 và -1; xác định wrapper có giống nhau không, và cause khác nhau thế nào.

### Bài 4 — Đọc cause chain

Chạy `ReflectionCauseChainDemo`. Tại handler, in message của wrapper, direct cause, root cause. Tại sao không nên phụ thuộc wrapper message để lấy error nghiệp vụ? Dùng getter nào để xem `IOException`?

### Bài 5 — Ném lại checked exception

Chạy `RethrowTargetIOExceptionDemo`. Đổi `runReflectively` để bọc IOException vào `IllegalStateException("...", cause)` thay vì ném trực tiếp. Caller phải thay handler ra sao? So sánh stack của wrapper mới và cause gốc.

### Bài 6 — So sánh method handle

Chạy `MethodHandleVsReflectionDemo`. Nêu tên API nào tạo `InvocationTargetException` khi `fail` ném IOException và API nào cho IOException đi trực tiếp trong ví dụ. Không dùng kết quả này để suy đoán hiệu năng.

### Bài 7 — Proxy

Chạy `ProxyUndeclaredExceptionDemo`. Thêm `throws IOException` vào khai báo `Task.run()`. Dự đoán khác biệt về khả năng checked exception đi qua proxy method khi handler ném IOException. Giải thích vì sao không nên cố bắt `InvocationTargetException` trong code chỉ gọi proxy trực tiếp.

### Bài 8 — Trả `void` và static

Chạy `ReflectiveReturnValuesDemo`. Vì sao `ping.invoke(target)` trả null, còn `version.invoke(null)` trả `"v1"`? Nếu gọi một **instance method** qua `invoke(null)`, lỗi thuộc nhóm target failure hay lỗi chuẩn bị invocation?

### Bài 9 — Thiết kế API

Một plugin framework chọn method theo tên rồi gọi qua `Method.invoke`. Hãy thiết kế API trả lỗi: caller có cần thấy `InvocationTargetException` không, hay nên nhận `PluginExecutionException` với target throwable làm cause? Nêu ưu/nhược của hai lựa chọn.

### Bài 10 — Root cause và suppressed

Giả sử target dùng try-with-resources, body ném `IOException("read")`, close ném `IOException("close")`, rồi Reflection bọc lỗi. Vẽ cấu trúc wrapper → target primary → suppressed. Phương thức getter nào truy cập từng tầng? Vì sao `wrapper.getSuppressed().length` có thể bằng 0 nhưng target vẫn có lỗi suppressed?

### Bài 11 — Lỗi từ lookup

Tạo một method public `hello(int)`. Gọi `getMethod("hello", String.class)`. Compiler có phát hiện sai không? Exception thực tế là gì? Method body có chạy không? Vì sao đây **không phải** `InvocationTargetException`?

### Bài 12 — Kiểm thử ổn định

Viết JUnit 5 test dùng `assertThrows(InvocationTargetException.class, () -> method.invoke(target))` khi body target chủ động ném `IllegalArgumentException`. Xác nhận cause type/message mà không so sánh stack trace text hoặc số dòng JDK.

## 26. Checklist hiểu và xử lý đúng

- [ ] Tôi biết `InvocationTargetException` là checked subtype của `ReflectiveOperationException` chưa?
- [ ] Tôi phân biệt rõ exception do `Method.invoke`/`Constructor.newInstance` **bọc** và target error **thật** chưa?
- [ ] Tôi biết `getCause()`/`getTargetException()` trả Throwable gốc, không phải message chưa?
- [ ] Tôi hiểu lỗi target là checked, unchecked hoặc Error đều có thể được bọc chưa?
- [ ] Tôi không bắt `IOException` đơn độc quanh `Method.invoke` rồi kỳ vọng nhận checked IOException từ body chứ?
- [ ] Tôi đã phân biệt lookup failure (`NoSuchMethodException`) với invocation failure chưa?
- [ ] Tôi không nhầm lỗi sai argument/access/null receiver do Reflection với lỗi từ body target chưa?
- [ ] Tôi hiểu `getStackTrace()` của wrapper khác với stack trace của cause chưa?
- [ ] Khi unwrap, tôi có phân loại `IOException`, `RuntimeException`, `Error` và checked khác theo hợp đồng chưa?
- [ ] Tôi không cast thẳng mọi cause sang RuntimeException chưa?
- [ ] Nếu đổi exception ở tầng cao, tôi giữ target Throwable làm cause khi phù hợp chưa?
- [ ] Tôi nhớ wrapper không rollback side effect của method/constructor đã chạy trước khi lỗi chưa?
- [ ] Với constructor Reflection, tôi biết tìm thấy metadata không tự cấp quyền gọi chưa?
- [ ] Tôi biết `Class.newInstance()` cũ đã deprecated và có cơ chế exception khác chưa?
- [ ] Tôi không mặc định `MethodHandle` hay proxy đều dùng InvocationTargetException chưa?
- [ ] Tôi phân biệt `UndeclaredThrowableException` của proxy với `InvocationTargetException` của Method.invoke chưa?
- [ ] Khi có nhiều tầng wrapping, tôi đọc cause chain có mục đích và không mất thông tin suppressed chưa?
- [ ] Tôi log target failure đầy đủ nhưng không phơi stack trace nội bộ cho client công khai chưa?
- [ ] Tôi cân nhắc lời gọi trực tiếp khi method được biết ở compile-time thay vì Reflection không cần thiết chưa?
- [ ] Tôi có test ít nhất một ca thành công, lỗi target, lỗi lookup và lỗi argument chưa?

**Kết luận:** `InvocationTargetException` là **lớp bọc do các API Reflection như `Method.invoke` và `Constructor.newInstance` báo khi mã được gọi ném lỗi**. Để biết ngoại lệ thực tế, dùng **`getCause()` hoặc `getTargetException()`**. Đừng đồng nhất lỗi của API Reflection trước khi chạy body với lỗi từ target, đừng suy target exception từ wrapper message, và hãy giữ cause/stack khi chuyển lỗi sang hợp đồng bên ngoài. Đọc đúng tầng ngoại lệ giúp chẩn đoán hệ thống dùng Reflection rõ ràng hơn và tránh xử lý sai loại failure.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="api-exceptions-009">ClassNotFoundException khác NoClassDefFoundError</a>

<details>
<summary>Xem chi tiết</summary>

# `ClassNotFoundException` khác `NoClassDefFoundError` trong Java

Hai lỗi này đều có thể xuất hiện khi JVM hoặc thư viện không thể sử dụng một lớp Java, nhưng **không có cùng bản chất, không thuộc cùng nhóm ngoại lệ và không nên xử lý theo một cách máy móc**.

- **`ClassNotFoundException`** là **checked exception**, thường phát sinh từ API yêu cầu **tìm hoặc nạp một lớp theo tên một cách tường minh**, như `Class.forName("com.example.Plugin")` hoặc `ClassLoader.loadClass("com.example.Plugin")`. Nó nói rằng cơ chế nạp lớp được yêu cầu không tìm được định nghĩa tương ứng theo phạm vi của class loader.
- **`NoClassDefFoundError`** là **unchecked `Error` thuộc nhóm `LinkageError`**, xảy ra khi JVM cần một định nghĩa lớp ở runtime nhưng **không có hoặc không sử dụng được định nghĩa cần thiết**, dù bytecode đang chạy đã có tham chiếu tới lớp đó; nó cũng có thể xuất hiện khi một lớp **đã thất bại ở giai đoạn khởi tạo tĩnh** và được dùng lại.

Một câu dễ nhớ nhưng cần hiểu đúng:

> `ClassNotFoundException` thường là lỗi **nạp lớp theo tên bằng API có checked contract**; `NoClassDefFoundError` là **lỗi liên kết/sử dụng lớp ở runtime**, trong đó có trường hợp lớp cần thiết bị thiếu hoặc lớp đã ở trạng thái khởi tạo lỗi.

Đừng rút gọn thành “một lỗi xảy ra khi compile, một lỗi xảy ra khi chạy”: **cả hai đều là hiện tượng có thể quan sát tại runtime**. Vấn đề compile-time tương ứng là một nhóm lỗi khác, ví dụ trình biên dịch báo `cannot find symbol` hoặc `package ... does not exist`.

## 1. Vị trí của hai loại trong hệ phân cấp

```text
java.lang.Throwable
├── java.lang.Exception
│   └── java.lang.ReflectiveOperationException
│       └── java.lang.ClassNotFoundException
└── java.lang.Error
    └── java.lang.LinkageError
        ├── java.lang.NoClassDefFoundError
        ├── java.lang.ExceptionInInitializerError
        ├── java.lang.NoSuchMethodError
        ├── java.lang.NoSuchFieldError
        └── java.lang.IncompatibleClassChangeError
```

Sơ đồ là **trích đoạn**, không phải toàn bộ hệ phân cấp Java. `NoSuchMethodError` và `NoSuchFieldError` thực tế là những subtype nằm dưới `IncompatibleClassChangeError`; để đọc chính xác quan hệ, có thể biểu diễn chi tiết hơn:

```text
LinkageError
├── NoClassDefFoundError
├── ExceptionInInitializerError
└── IncompatibleClassChangeError
    ├── NoSuchMethodError
    └── NoSuchFieldError
```

Cả `ClassNotFoundException` và `NoClassDefFoundError` đều nằm trong `java.lang`, không cần import.

| Tiêu chí | `ClassNotFoundException` | `NoClassDefFoundError` |
| --- | --- | --- |
| Kiểu gốc | `Exception` qua `ReflectiveOperationException` | `Error` qua `LinkageError` |
| Checked? | **Có** | **Không** |
| Compiler bắt buộc bắt/khai báo khi API có `throws`? | Có | Không |
| Tình huống điển hình | Tìm lớp theo tên với `Class.forName`/`loadClass` | Bytecode cần lớp runtime thiếu hoặc không khởi tạo được |
| Đối tượng cần điều tra | Tên lớp được yêu cầu, class loader, khả năng nhìn thấy lớp | Runtime classpath/module path, dependency, class loader, liên kết và khởi tạo |
| Có thể liên quan cùng một tên lớp? | Có | Có, nhưng khác đường đi và ý nghĩa |
| Bắt trong code nghiệp vụ thường xuyên? | Có thể, nếu tên lớp là cấu hình tùy chọn | Thường không; ưu tiên sửa đóng gói/cấu hình hoặc lỗi khởi tạo |
| Thuộc nhóm `LinkageError`? | Không | Có |
| Hướng xử lý chính | Chính sách tìm plugin/driver/lớp tùy chọn | Chẩn đoán runtime deployment, class loader hoặc static initializer |

### Tại sao checked và Error lại quan trọng?

Với:

```java
Class<?> type = Class.forName("com.example.Plugin");
```

trình biên dịch yêu cầu lời gọi nằm trong handler cho `ClassNotFoundException` hoặc phương thức chứa khai báo checked exception phù hợp.

Ngược lại:

```java
PluginApi api = new PluginImpl();
```

đây là **câu lệnh minh họa**, các kiểu phải tồn tại khi biên dịch. Nếu bytecode thực thi tại runtime cần `PluginImpl` hoặc dependency của nó nhưng không tìm/khởi tạo được, có thể gặp `NoClassDefFoundError` mà không có `throws NoClassDefFoundError` trên constructor.

**Không nên thiết kế luồng nghiệp vụ thông thường bằng cách bắt mọi `Error`.** `Error` báo những vấn đề JVM/linkage/hệ thống thường không được kỳ vọng phục hồi trong một lời gọi nghiệp vụ. Riêng việc bắt `NoClassDefFoundError` trong ví dụ học hoặc tại một ranh giới plugin cách ly có thể hợp lý để quan sát, nhưng không thay thế việc sửa classpath hoặc dependency.

## 2. Tải lớp, liên kết và khởi tạo là các giai đoạn khác nhau

JVM không xem “có lớp” là một thao tác duy nhất. Về khái niệm, việc một lớp sẵn sàng sử dụng liên quan ba giai đoạn lớn:

1. **Loading (nạp):** class loader tìm/định nghĩa biểu diễn lớp, thường từ bytecode.
2. **Linking (liên kết):** gồm xác minh (*verification*), chuẩn bị (*preparation*) và phân giải (*resolution*) các tham chiếu khi cần. Chi tiết thời điểm có thể do JVM thực hiện theo đặc tả.
3. **Initialization (khởi tạo):** thực hiện các static initializer và phép gán static field có khởi tạo không phải compile-time constant, theo quy tắc khởi tạo của Java.

Một lớp có thể **được nạp** nhưng **chưa khởi tạo**. Một lớp có thể **tồn tại trong jar** nhưng **không dùng được** vì dependency, class loader, lỗi liên kết hoặc quá trình khởi tạo.

### Hình dung luồng thông thường

```text
Một lời gọi yêu cầu lớp
           |
           v
     Tìm/định nghĩa lớp
           |
           +-- Không tìm được qua API nạp theo tên
           |       -> ClassNotFoundException (checked)
           |
           +-- JVM đang cần định nghĩa cho tham chiếu bytecode
           |       nhưng không tìm/không dùng được
           |       -> có thể NoClassDefFoundError
           |
           v
     Liên kết, phân giải
           |
           +-- Lỗi liên kết, phụ thuộc, định dạng, tương thích...
           |       -> LinkageError subtype thích hợp
           |
           v
     Khởi tạo tĩnh khi cần
           |
           +-- Static initialization thất bại
                   -> có thể ExceptionInInitializerError lần đầu
                   -> lần sử dụng sau có thể NoClassDefFoundError
```

Đây là sơ đồ sư phạm, **không phải cam kết một exception duy nhất cho mọi trạng thái**. Ví dụ, một subclass `LinkageError` khác có thể là loại báo lỗi chính xác hơn trong một tình huống nhất định.

### “Lớp có trong JAR” chưa đủ

Một JAR có `com/example/PluginImpl.class` không bảo đảm chương trình dùng được lớp ấy. Còn phải xét:

- JAR có thực sự nằm trong **runtime classpath/module path** của process hiện tại hay không?
- Class loader đang dùng có **nhìn thấy** JAR đó không?
- Lớp có cần các dependency khác không?
- Tên package/class thực có khớp với tên JVM đang tìm không?
- Bytecode được biên dịch cho phiên bản JDK phù hợp chưa?
- Module có được resolve, quan hệ `requires` và phạm vi truy cập có phù hợp không?
- Static initialization có chạy thành công không?

Vì vậy không chỉ dựa vào việc “đã thêm dependency vào file build” để kết luận môi trường runtime đã đủ.

## 3. `ClassNotFoundException`: yêu cầu lớp theo tên không thành công

`Class.forName(String)` là API phổ biến trong reflection và cơ chế plugin:

```java
try {
    Class<?> type = Class.forName("demo.plugins.ReportProvider");
    System.out.println(type.getName());

} catch (ClassNotFoundException ex) {
    System.out.println("Plugin class is unavailable");
}
```

Đây là **đoạn minh họa**. Nếu loader liên quan không tìm được class binary name được yêu cầu, API có thể ném checked `ClassNotFoundException`.

Một số trường hợp:

- Tên lớp cấu hình sai, sai package hoặc sai chữ hoa/thường.
- Plugin/driver là dependency tùy chọn và chưa được cài.
- Dependency chỉ có ở compile/test nhưng vắng trong runtime.
- Class loader đang tìm không nhìn thấy JAR hoặc module chứa lớp.
- Cấu hình dùng tên lớp không còn tồn tại sau refactor/đổi package.
- Ứng dụng kỳ vọng nạp một class trong module chưa được resolved vào layer tương ứng.

### Phân biệt tên lớp với đường dẫn JAR

`Class.forName("com.example.MyClass")` dùng **binary name** của lớp, thường có dấu chấm `.`, không phải đường dẫn tệp `com/example/MyClass.class`. Class loader quyết định ánh xạ tên đó đến bytecode nào.

Với nested class dạng member có thể cần tên binary chứa dấu `$`, ví dụ `"com.example.Outer$Inner"`; `Class.forName("com.example.Outer.Inner")` không tự động là tên tương đương cho mọi nested class.

Không phải generic type đều có `Class` runtime tương ứng: `"java.util.List<java.lang.String>"` **không phải** binary name của một lớp `List<String>` độc lập. Type erasure và reflection generics là vấn đề khác.

### `ClassLoader.loadClass` cũng có checked contract

```java
ClassLoader loader = Thread.currentThread()
        .getContextClassLoader();

Class<?> type = loader.loadClass(
        "demo.plugins.ReportProvider");
```

Đây là đoạn minh họa; **thread context class loader có thể `null`** trong những môi trường nhất định, cần kiểm tra hoặc chọn loader khác theo chính sách.

`ClassLoader.loadClass(String)` khai báo `throws ClassNotFoundException`. Thông thường nó tìm/nạp lớp nhưng **không khởi tạo lớp** như một hệ quả trực tiếp của lệnh này. Một ứng dụng plugin thường cần thêm bước xác minh interface và tạo instance theo hợp đồng.

**Không bắt `ClassNotFoundException` rồi mặc nhiên kết luận “JVM hoàn toàn không có class này”.** Có thể lớp nằm ở một class loader khác nhưng loader hiện tại không nhìn thấy nó.

## 4. `Class.forName` có thể khởi tạo lớp — điểm khác `loadClass`

Phương thức:

```java
Class<?> type = Class.forName(
        "demo.plugins.ReportProvider");
```

thường tương đương yêu cầu nạp và **khởi tạo** lớp khi cần với loader thích hợp cho lời gọi. Vì vậy nó có thể báo lỗi **khởi tạo tĩnh**, không chỉ `ClassNotFoundException`.

Overload cho phép chọn:

```java
Class<?> type = Class.forName(
        binaryName,
        false,
        selectedLoader);
```

Đây là đoạn minh họa với `binaryName` và `selectedLoader` hợp lệ; `false` yêu cầu **không khởi tạo lớp ở bước này**. Nếu đặt `true`, việc khởi tạo diễn ra nếu chưa được thực hiện.

Điểm khác biệt:

| Cách yêu cầu | Có khai báo checked `ClassNotFoundException`? | Có chủ đích khởi tạo lớp ngay? |
| --- | --- | --- |
| `Class.forName(name)` | Có | Có |
| `Class.forName(name, true, loader)` | Có | Có |
| `Class.forName(name, false, loader)` | Có | Không |
| `loader.loadClass(name)` | Có | Không tự khởi tạo theo hợp đồng thông thường |
| `SomeType.class` | Không vì bản thân class literal | Không phải một yêu cầu khởi tạo `SomeType` |

“Không khởi tạo ở đây” **không có nghĩa** tất cả dependency luôn sẵn sàng hoặc mọi tác dụng của class loader hoàn toàn không có side effect. Loader có thể là implementation tùy chỉnh và các thao tác linkage khác vẫn có thể xảy ra.

## 5. `NoClassDefFoundError`: runtime cần một định nghĩa lớp nhưng không sử dụng được

Hình dung hai file đã được biên dịch:

```java
// Dependency.java
public class Dependency {
    public static String text() {
        return "ready";
    }
}
```

```java
// MissingDefinitionDemo.java
public class MissingDefinitionDemo {
    public static void main(String[] args) {
        try {
            System.out.println(
                    "dependency=" + Dependency.text());
        } catch (NoClassDefFoundError ex) {
            System.out.println(
                    "runtime=" + ex.getClass().getSimpleName());
        }
    }
}
```

Khi biên dịch, trình biên dịch đã nhìn thấy `Dependency`, nên ghi một tham chiếu tới lớp đó trong bytecode của `MissingDefinitionDemo`. Nếu runtime chạy với `MissingDefinitionDemo.class` nhưng **không có `Dependency.class` trên đường tìm lớp của loader liên quan**, JVM có thể ném `NoClassDefFoundError` khi thực thi tham chiếu.

Quan trọng: **source code chưa từng gọi `Class.forName("Dependency")`**. Đây là một tham chiếu loại trực tiếp từ bytecode, không phải lời gọi API reflection có checked contract.

### Không chỉ do thiếu một file `.class`

`NoClassDefFoundError` còn có thể xảy ra khi:

- Một phụ thuộc **gián tiếp** của lớp bạn muốn dùng bị thiếu khi JVM cần tới.
- Một lớp bị nạp theo class loader không nhìn thấy dependency, dù JAR có trong process ở một phạm vi khác.
- Một lớp đã **thất bại trong khởi tạo tĩnh** và JVM không thử chạy lại initializer của chính lớp đó trong cùng class loader.
- Có vấn đề với cấu hình runtime khiến lớp đã được biên dịch nhưng không thể định nghĩa/resolve đúng lúc cần.

Nó không có nghĩa `ClassNotFoundException` là “sai” hoặc “đã biến thành” `NoClassDefFoundError`. Có thể cả hai cùng liên quan **một thiếu hụt class**, nhưng phát sinh qua cơ chế và hợp đồng khác nhau.

## 6. Ví dụ Java 17+ hoàn chỉnh: thiếu dependency sau khi biên dịch

Đây là thí nghiệm quan trọng nhất để phân biệt **biên dịch thành công** với **runtime thiếu lớp**. Sử dụng hai tệp đã nêu, đầy đủ mã như sau.

Tệp **`Dependency.java`**:

```java
public class Dependency {
    public static String text() {
        return "ready";
    }
}
```

Tệp **`MissingDefinitionDemo.java`**:

```java
public class MissingDefinitionDemo {
    public static void main(String[] args) {
        try {
            System.out.println(
                    "dependency=" + Dependency.text());
        } catch (NoClassDefFoundError ex) {
            System.out.println(
                    "runtime=" + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch cả hai vào một thư mục riêng:

```bash
javac -encoding UTF-8 -d build Dependency.java MissingDefinitionDemo.java
java -cp build MissingDefinitionDemo
```

**Stdout khi đủ cả hai class:**

```text
dependency=ready
```

Tiếp theo, chỉ **xóa tệp bytecode `Dependency.class` trong thư mục thí nghiệm `build`** — không xóa source, không đụng tới bất kỳ file ứng dụng thật nào. Nếu cần giữ tệp, thay vào đó có thể sao chép `MissingDefinitionDemo.class` sang một thư mục runtime mới hoàn toàn riêng mà **không có** `Dependency.class`.

Ví dụ lệnh **POSIX shell**:

```bash
rm build/Dependency.class
java -cp build MissingDefinitionDemo
```

Ví dụ lệnh **PowerShell trên Windows** thay cho `rm`:

```powershell
Remove-Item -LiteralPath build\Dependency.class
java -cp build MissingDefinitionDemo
```

**Stdout khi runtime chỉ còn class của chương trình chính:**

```text
runtime=NoClassDefFoundError
```

### Phân tích chính xác

1. `javac` đã thành công vì lúc biên dịch nó thấy `Dependency.java`.
2. `MissingDefinitionDemo.class` được tạo và có tham chiếu tới `Dependency.text()`.
3. Khi chạy lần đầu, loader tìm được `Dependency.class`, phương thức trả `"ready"`.
4. Sau khi tệp dependency bị bỏ khỏi **runtime classpath**, lúc chạy một process JVM mới, phép gọi `Dependency.text()` làm JVM cần định nghĩa `Dependency`, nhưng không tìm được.
5. Handler trong ví dụ bắt **đúng `NoClassDefFoundError`** và chỉ in tên lớp ngoại lệ để có stdout ổn định giữa môi trường.

**Chỉ bắt `NoClassDefFoundError` trong demo để quan sát.** Trong ứng dụng production, nếu một dependency bắt buộc biến mất, giải pháp không phải “catch rồi tiếp tục như đã thành công” mà là sửa package/deployment/classpath và xác nhận phiên bản dependency.

### Tại sao không nhận `ClassNotFoundException` trong ví dụ này?

Mã gọi `Dependency.text()` trực tiếp. Không có API `Class.forName` hoặc `ClassLoader.loadClass` do ứng dụng gọi để chủ động tìm class theo tên và bắt checked exception. JVM đang **liên kết/sử dụng một tham chiếu bytecode**, nên lỗi được báo theo nhóm linkage là phù hợp.

Một số bản message của `NoClassDefFoundError` có thể chứa tên lớp dạng `Dependency` hoặc `com/example/Dependency`, và có thể có cause, nhưng **đừng dùng nguyên chuỗi message làm phép kiểm tra portable**.

## 7. `ExceptionInInitializerError` và `NoClassDefFoundError` sau khi khởi tạo thất bại

Đây là điểm thường khiến người học nhầm: có lúc **file `.class` rõ ràng tồn tại**, nhưng JVM vẫn báo `NoClassDefFoundError` với ý nghĩa lớp không thể khởi tạo.

Khi static initialization của một lớp thất bại:

- Nếu một ngoại lệ **không phải `Error`** thoát khỏi static initialization, JVM thường bọc nó bằng **`ExceptionInInitializerError`** ở lần khởi tạo thất bại đầu tiên.
- Lớp được đánh dấu ở **trạng thái khởi tạo lỗi** đối với defining class loader.
- Những lần dùng chủ động tiếp theo có thể báo **`NoClassDefFoundError`** thay vì chạy lại static initializer của lớp đó.
- Điều này **không có nghĩa file class đã bị xóa**.
- Tình trạng và thông điệp cụ thể của phần cause/stack phụ thuộc chuỗi khởi tạo và JVM; phải đọc cả lỗi xuất hiện đầu tiên.

### Ví dụ Java 17+ hoàn chỉnh: ba kiểu lỗi trong một chương trình

Lưu tệp **`ClassLoadingComparisonDemo.java`**:

```java
public class ClassLoadingComparisonDemo {

    static class BrokenInit {
        static final int STATE = fail();

        static int fail() {
            throw new IllegalStateException(
                    "bad configuration");
        }
    }

    public static void main(String[] args) throws Exception {
        try {
            Class.forName("missing.demo.DoesNotExist");

        } catch (ClassNotFoundException ex) {
            System.out.println(
                    "forName missing=" + ex.getClass().getSimpleName());
        }

        Class<?> token = BrokenInit.class;

        System.out.println(
                "class literal=" + token.getSimpleName());

        Class<?> loaded = ClassLoadingComparisonDemo.class
                .getClassLoader()
                .loadClass(token.getName());

        System.out.println(
                "loadClass=" + loaded.getSimpleName());

        try {
            Class.forName(
                    token.getName(),
                    true,
                    token.getClassLoader());

        } catch (ExceptionInInitializerError ex) {
            System.out.println(
                    "initialize first=" + ex.getClass().getSimpleName());
            System.out.println(
                    "root cause="
                    + ex.getCause().getClass().getSimpleName());
        }

        try {
            Class.forName(
                    token.getName(),
                    true,
                    token.getClassLoader());

        } catch (NoClassDefFoundError ex) {
            System.out.println(
                    "initialize again=" + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 ClassLoadingComparisonDemo.java
java ClassLoadingComparisonDemo
```

**Stdout chính xác trong chương trình này:**

```text
forName missing=ClassNotFoundException
class literal=BrokenInit
loadClass=BrokenInit
initialize first=ExceptionInInitializerError
root cause=IllegalStateException
initialize again=NoClassDefFoundError
```

### Phân tích từng dòng

- **`Class.forName("missing.demo.DoesNotExist")`:** yêu cầu tìm một tên lớp cố ý không tồn tại trong thí nghiệm, nên nhận `ClassNotFoundException`.
- **`BrokenInit.class`:** lấy `Class` token cho nested class; bản thân biểu thức class literal **không làm chạy static initializer** của `BrokenInit`.
- **`loadClass(token.getName())`:** loader tìm class mà chưa khởi tạo nó; do đó việc lấy `getSimpleName()` trong ví dụ vẫn thực hiện được.
- **`Class.forName(name, true, loader)` lần đầu:** yêu cầu khởi tạo. `STATE = fail()` ném `IllegalStateException`; JVM báo **`ExceptionInInitializerError`** với nguyên nhân là lỗi đó.
- **Lần thứ hai:** lớp đã ở trạng thái khởi tạo lỗi, nên JVM báo **`NoClassDefFoundError`** khi cố khởi tạo/sử dụng lại lớp trong cùng loader.
- **Không có thao tác xóa JAR/class file** nào ở ví dụ này. Đây là `NoClassDefFoundError` do trạng thái lớp, khác tình huống dependency runtime vắng mặt ở phần trước.

### Đừng chỉ sửa lỗi xuất hiện ở lần thứ hai

Nếu log chỉ còn:

```text
java.lang.NoClassDefFoundError: Could not initialize class ...
```

nguyên nhân cần điều tra thường nằm trong **lần khởi tạo đầu**: một `ExceptionInInitializerError`, một lỗi cấu hình, quyền, missing resource hoặc thao tác static initializer. Hãy tìm sự kiện gốc trong log/trace thay vì chỉ tìm JAR bị thiếu.

Không phải mọi lỗi static initialization đều được bọc `ExceptionInInitializerError`: nếu static initialization ném một `Error`, quy tắc lan truyền có khác biệt. Ví dụ được thiết kế rõ với `IllegalStateException` để kết quả chắc chắn.

## 8. Class loader: vì sao JAR có mà ứng dụng vẫn không tìm thấy?

Java có mô hình **class loader hierarchy** và khả năng định nghĩa loader tùy chỉnh. Class loader quyết định tập class nào nó có thể nhìn thấy, cách tìm, cách ủy quyền và cách định nghĩa class.

Một số tình huống:

- **Ứng dụng command-line** đặt JAR dependency ở thư mục dự kiến nhưng không thêm vào `-cp`.
- **Application server/container** cách ly ứng dụng bằng loader riêng.
- **Plugin architecture** nạp plugin bằng loader của plugin nhưng lại tìm lớp qua loader của core.
- **Test runtime** có dependency nhờ cấu hình test, trong khi bản triển khai production không có.
- **Shading/relocation** đổi package của dependency nhưng mã cấu hình vẫn tìm tên cũ.
- **Module layer** giới hạn lớp có thể tìm/resolve, dù bytecode tồn tại nơi khác.

### “Cùng một tên lớp” không đủ

Theo mô hình JVM, danh tính kiểu runtime liên quan tới **binary name và defining class loader**. Hai class loader khác nhau có thể định nghĩa các lớp cùng tên nhưng chúng không nhất thiết là **cùng một kiểu runtime**.

Trong một hệ thống plugin, điều này có thể tạo những vấn đề khác như `ClassCastException` khi ép object qua hai loader không tương thích. Nó cũng giải thích vì sao một loader có thể không nhìn thấy lớp mà một loader khác đã nạp.

### Thread context class loader (TCCL)

Một số framework dùng:

```java
ClassLoader loader =
        Thread.currentThread().getContextClassLoader();
```

Đây là mã minh họa. TCCL có thể khác loader định nghĩa lớp đang thực thi và có thể là `null`. Điều đó hữu ích để tìm implementation do caller/plugin cung cấp, nhưng phải hiểu thiết kế framework thay vì đổi loader một cách ngẫu nhiên rồi coi lỗi đã được giải quyết.

Đừng tự động chuyển sang `ClassLoader.getSystemClassLoader()` hoặc loader của một class bất kỳ: nó có thể tình cờ hoạt động trong IDE nhưng phá tính cách ly module/plugin ở production.

## 9. Classpath và module path: nguyên nhân triển khai hay gặp

Với ứng dụng chạy theo **classpath**:

```bash
java -cp "app.jar:lib/*" com.example.Main
```

Lệnh này là **mẫu POSIX**; trên Windows, dấu phân cách giữa các phần tử classpath là `;` thay vì `:`. Ví dụ:

```powershell
java -cp "app.jar;lib/*" com.example.Main
```

Hai câu chỉ minh họa sự khác nhau của ký tự phân cách; tên JAR và main class cần thay bằng giá trị thực trong ứng dụng.

### Compile classpath khác runtime classpath

Thử nghiệm `MissingDefinitionDemo` phía trên đã chứng minh:

- Biên dịch dùng `javac -d build Dependency.java MissingDefinitionDemo.java` nên có cả hai class.
- Runtime `java -cp build ...` chỉ thấy những file còn ở thư mục build.
- Class bị loại sau compile vẫn có thể làm ứng dụng ném `NoClassDefFoundError`.

Trong Maven/Gradle, cấu hình dependency có **scope/kiểu cấu hình runtime** khác compile hoặc test. Nếu dependency chỉ có khi biên dịch nhưng vắng lúc chạy, bạn có thể gặp lỗi linkage. Ngược lại, một class được tìm động trong plugin cũng có thể gây `ClassNotFoundException` nếu loader không thấy runtime artifact.

### Với Java Platform Module System (JPMS)

Từ Java 9, module path và module graph có thể ảnh hưởng quá trình tìm/nạp lớp. Các khái niệm liên quan là:

- `module-info.java` và khai báo `requires`.
- Module có thực sự nằm trong **resolved module graph** hay không.
- Class cần dùng có ở module layer và loader liên quan không.
- `exports` và `opens` ảnh hưởng quyền truy cập/reflective access theo ngữ cảnh.
- Cách ứng dụng được chạy qua `--module-path`/`--module` so với `--class-path`.

**Đừng gom tất cả lỗi module vào `NoClassDefFoundError`.** Vi phạm access hoặc reflection có thể dẫn tới các exception khác, ví dụ `IllegalAccessException` hoặc `InaccessibleObjectException` trong những trường hợp tương ứng. Luôn đọc loại exception thực được báo.

### Phiên bản bytecode khác với “class missing”

Nếu bytecode được tạo bằng JDK mới hơn runtime hỗ trợ, bạn có thể gặp `UnsupportedClassVersionError`, cũng là lỗi thuộc họ linkage/class format, **không phải mặc định `ClassNotFoundException`**. Vì vậy khi deploy, hãy kiểm tra cả **phiên bản Java runtime**, không chỉ danh sách JAR.

## 10. Phân biệt với các lỗi linkage gần nhau

| Lỗi | Nhóm | Ý nghĩa thường gặp |
| --- | --- | --- |
| `ClassNotFoundException` | Checked `Exception` | API tìm/nạp theo tên không tìm được class |
| `NoClassDefFoundError` | `LinkageError` | Định nghĩa class cần khi runtime không có/không dùng được hoặc class ở trạng thái khởi tạo lỗi |
| `ExceptionInInitializerError` | `LinkageError` | Khởi tạo tĩnh thất bại và exception thông thường được bọc ở lần đầu |
| `NoSuchMethodError` | `IncompatibleClassChangeError` | Bytecode cần một method signature nhưng binary runtime không cung cấp theo kỳ vọng |
| `NoSuchFieldError` | `IncompatibleClassChangeError` | Bytecode cần field nhưng binary runtime không phù hợp |
| `IncompatibleClassChangeError` | `LinkageError` | Thay đổi binary làm một tham chiếu không còn phù hợp |
| `UnsupportedClassVersionError` | `ClassFormatError` → `LinkageError` | File class có phiên bản runtime không hỗ trợ |
| `ClassCastException` | `RuntimeException` | Object runtime không phù hợp phép cast reference |
| `IllegalAccessException` | Checked reflection exception | Truy cập không được cho phép qua cơ chế reflection trong điều kiện tương ứng |
| `InaccessibleObjectException` | Unchecked `RuntimeException` | Thao tác reflection cố vượt giới hạn truy cập module |

Một số lỗi trong bảng không xuất hiện trong mọi ứng dụng, nhưng phân biệt chúng giúp tránh sửa sai vấn đề. Đặc biệt, nếu báo `NoSuchMethodError`, việc chỉ tìm JAR “bị thiếu” là chưa đủ; rất có thể JAR **đã có nhưng sai phiên bản binary**.

### Cùng một dependency có thể tạo nhiều kiểu lỗi

Ví dụ ứng dụng được biên dịch với thư viện phiên bản mới, nhưng chạy với phiên bản cũ:

- Một class mới hoàn toàn không có: có thể gặp `NoClassDefFoundError` hoặc `ClassNotFoundException` tùy cách dùng.
- Class có nhưng method mới không có: có thể gặp `NoSuchMethodError`.
- Class tồn tại nhưng initializer phụ thuộc một tài nguyên bị thiếu và ném exception: lần đầu có thể `ExceptionInInitializerError`, lần sau `NoClassDefFoundError`.

Do đó phải so khớp **runtime artifact và binary API thực tế**, không chỉ tên class ở dòng đầu trace.

## 11. Bắt ngoại lệ theo đúng hợp đồng

### Khi class là tùy chọn và được tìm theo tên

Nếu ứng dụng cho phép plugin không được cài:

```java
static java.util.Optional<Class<?>> findOptionalPlugin(
        String binaryName) {

    try {
        return java.util.Optional.of(
                Class.forName(binaryName, false,
                        Thread.currentThread()
                                .getContextClassLoader()));

    } catch (ClassNotFoundException missing) {
        return java.util.Optional.empty();
    }
}
```

Đây là **phương thức minh họa**, giả định hợp đồng loader cho phép dùng context class loader (có thể null) và chỉ định nghĩa optional nghĩa là **không tìm được class theo tên**. Nên có một chính sách loader nhất quán thay vì sao chép đoạn này tùy tiện.

Một số giới hạn:

- `Class.forName(..., false, ...)` không khởi tạo class ở bước này; **không có nghĩa plugin chắc chắn tạo instance được**.
- `Optional.empty()` chỉ diễn đạt thiếu class theo loader đã dùng, **không chứng minh không có class ở bất kỳ loader khác**.
- Nếu class bắt buộc, chuyển `ClassNotFoundException` thành một lỗi cấu hình có nghĩa và giữ cause, thay vì fallback im lặng.
- Nếu `LinkageError` xuất hiện, nó không bị bắt bởi `catch (ClassNotFoundException)`, và ứng dụng không nên mặc định coi nó là cùng một kết quả “plugin không có”.

### Với dependency bắt buộc ở bytecode

Trường hợp runtime mất `Dependency.class` không nên xử lý bằng:

```java
try {
    return Dependency.text();
} catch (NoClassDefFoundError ignored) {
    return "default";
}
```

Đây là **chống mẫu minh họa**, cần ngữ cảnh phương thức phù hợp. Nếu dependency là bắt buộc, việc quay về default có thể che một deployment hỏng và làm hệ thống báo thành công giả.

Thay vào đó, bảo đảm đóng gói đúng JAR, classpath/module path, scope dependency, image/container runtime và compatibility test trước khi triển khai.

### Tại sao không dùng `catch (Exception)` cho cả hai?

`NoClassDefFoundError` extends `Error`, **không** extends `Exception`. Vì vậy:

```java
try {
    useDependency();
} catch (Exception ex) {
    // Không bắt NoClassDefFoundError.
}
```

Đây là khung minh họa, `useDependency()` cần được định nghĩa. `catch (Exception)` có thể bắt `ClassNotFoundException` nếu nó là loại checked được ném ở vùng try, nhưng **không bắt `NoClassDefFoundError`**.

Không nên vì thế thay mọi handler bằng `catch (Throwable)`. Điều đó còn bắt các `Error` nghiêm trọng khác và có thể làm mờ sự cố runtime không thể phục hồi.

## 12. Khi nào có thể phục hồi `NoClassDefFoundError`?

Với phần lớn ứng dụng, nó là **sự cố môi trường hoặc thiết kế** cần sửa trước khi chạy ổn định. Tuy nhiên, trong một số bộ nạp plugin hoặc công cụ chẩn đoán cô lập, tầng giám sát có thể:

- Cố nạp một plugin trong **class loader độc lập**.
- Bắt **một `LinkageError` cụ thể** tại ranh giới plugin để đánh dấu plugin không hoạt động.
- Ghi sự cố đầy đủ và không dùng các object/class từ loader lỗi.
- Chuyển sang plugin khác **đã được xác minh tương thích**, nếu hợp đồng cho phép.
- Không tiếp tục dùng một lớp đang ở trạng thái initialization failure trong cùng loader như thể chỉ cần retry thông thường.

Đây là **chính sách đặc thù của plugin container**, không phải khuyến nghị catch `NoClassDefFoundError` ở mọi repository, controller hay service.

### Retry đơn giản thường không giúp

Nếu JAR bắt buộc bị thiếu trên classpath, lặp lại cùng lời gọi trong process mà không thay đổi class loader/đường tìm lớp không làm class xuất hiện. Nếu class đã thất bại khởi tạo tĩnh, việc gọi lại cùng class trong cùng defining loader có thể chỉ nhận tiếp `NoClassDefFoundError`.

Khôi phục thực tế có thể yêu cầu:

- Sửa cấu hình/mount/runtime classpath và **khởi động lại process**.
- Tạo class loader mới theo thiết kế plugin, sau khi sửa dependency/tài nguyên.
- Sửa lỗi static initializer; đảm bảo config/resource có từ trước lúc khởi tạo.

Đừng thay việc điều tra bằng một vòng `retry` vô hạn.

## 13. Đọc cause chain và stack trace thay vì chỉ dòng message

Ví dụ một stack trace **minh họa cấu trúc**:

```text
java.lang.NoClassDefFoundError: com/example/Dependency
    at example.Worker.execute(Worker.java:<line>)
Caused by: java.lang.ClassNotFoundException: com.example.Dependency
    at ... <class-loader frames>
```

Một JVM/loader có thể đưa `ClassNotFoundException` vào cause của lỗi liên kết, nhưng **không được giả định mọi `NoClassDefFoundError` đều có `ClassNotFoundException` làm cause**.

Một ví dụ khác:

```text
java.lang.NoClassDefFoundError: Could not initialize class ...
    at example.Worker.execute(Worker.java:<line>)
Caused by: java.lang.ExceptionInInitializerError: ...
    at ... <initializer frames>
```

Đây cũng chỉ là ví dụ, không phải dạng cause bắt buộc hoặc thứ tự thông điệp ổn định.

### Quy trình điều tra

1. **Đọc loại ngoại lệ thực**: `ClassNotFoundException`, `NoClassDefFoundError` hay `ExceptionInInitializerError`?
2. **Đọc operation và nơi gọi**: `Class.forName`, `loadClass` hay tham chiếu tới class/method bình thường?
3. **Ghi chính xác binary name** của class trong sự cố (chỉ ở log nội bộ), nhưng không tự suy luận mọi ký tự của message là hợp đồng API.
4. **Xem `getCause()` và các phần `Caused by`**: lỗi gốc đôi khi thuộc class loader hoặc static initializer.
5. **Kiểm tra khởi tạo tĩnh trước đó** nếu message có dấu hiệu `Could not initialize class`.
6. **Đối chiếu runtime classpath/module path/JAR** đúng môi trường thất bại.
7. **Tìm dependency gián tiếp**: lớp ngoài có mặt nhưng superclass, interface hoặc một class được tham chiếu lại thiếu.
8. **Xác định class loader** đang được dùng khi tìm class; plugin/app server có thể cách ly.
9. **Kiểm tra compatibility phiên bản JDK và thư viện** nếu có nhiều `LinkageError`.
10. **Phân loại theo hợp đồng nghiệp vụ**: plugin tùy chọn thiếu khác một dependency bắt buộc bị mất.

### Đừng so sánh chuỗi message để quyết định

Không nên dùng:

```java
if (error.getMessage().contains(
        "Could not initialize class")) {
    // ...
}
```

Đây là chống mẫu: message có thể `null`, định dạng có thể khác theo JVM/phiên bản và chính sách chẩn đoán. Trong một công cụ điều tra, chuỗi vẫn hữu ích **cho người đọc**, nhưng logic chương trình cần dựa vào loại lỗi, cause chain, danh tính class và hợp đồng loader.

## 14. Kiểm tra lớp nào có trong JAR và runtime nào đang dùng

Những kiểm tra thực hành có ích khi debug môi trường thực:

### A. Nhìn cấu hình JVM chạy thực tế

```bash
java -version
```

Xác định runtime/JDK version. Không suy luận nó trùng với JDK dùng bởi IDE hoặc CI khi biên dịch.

Với tiến trình đã chạy, cách quan sát command line phụ thuộc hệ điều hành, container/runtime. Chỉ sửa cấu hình sau khi xác nhận process nào thật sự chạy ứng dụng.

### B. Kiểm tra JAR chứa class mong đợi

```bash
jar tf example-library.jar
```

Lệnh in các entry của JAR. Muốn biết JAR có class, tìm entry dạng:

```text
com/example/Dependency.class
```

Lệnh `jar tf` **không chứng minh** JAR đó có nằm trong loader runtime hoặc class dùng được khi khởi tạo.

### C. Quan sát việc nạp lớp

JDK 17 hỗ trợ logging của JVM, ví dụ:

```bash
java -Xlog:class+load=info -cp build MissingDefinitionDemo
```

Lệnh này có thể in nhiều thông tin về class loading ra console/log, hữu ích khi điều tra. Cấu hình và số lượng event cụ thể phụ thuộc JVM; không nên ghi stdout “chính xác” cố định cho lệnh chẩn đoán này.

### D. Xem tham chiếu bytecode

```bash
javap -c -classpath build MissingDefinitionDemo
```

Lệnh có thể cho thấy các instruction và tham chiếu symbolic tới `Dependency`. Đây là bằng chứng rằng class chính cần một dependency tại runtime, dù source đã được biên dịch thành bytecode.

### E. Kiểm tra dependency của ứng dụng

Với dự án có build tool, xem **dependency graph và runtime packaging** của phiên bản triển khai, không chỉ file cấu hình nguồn. Phải xác định artifact nào thực sự vào container/image hoặc đường tìm class của process.

Các công cụ Maven/Gradle có lệnh xem dependency nhưng cú pháp và configuration cụ thể phụ thuộc project/plugin; hãy dùng cấu hình thật của dự án thay vì đoán một lệnh chung cho mọi hệ thống.

## 15. Các tình huống thực tế và cách suy nghĩ

| Tình huống | Loại lỗi có thể thấy | Nên kiểm tra |
| --- | --- | --- |
| `Class.forName("missing.Name")` với class không tồn tại | `ClassNotFoundException` | Binary name và loader |
| `ClassLoader.loadClass(name)` tìm plugin tùy chọn nhưng không thấy | `ClassNotFoundException` | Scope của loader, JAR/plugin có được cài không |
| Ứng dụng compile với dependency nhưng chạy không có class tương ứng | `NoClassDefFoundError` | Runtime classpath, package, scope, image/JAR |
| Lớp có mặt nhưng thiếu một superclass hoặc interface được JVM cần resolve | `NoClassDefFoundError` hoặc linkage failure thích hợp | Dependency gián tiếp và loader |
| Static initializer ném `IllegalStateException` lần đầu | `ExceptionInInitializerError` | Lỗi bên trong initializer, cấu hình và tài nguyên |
| Lớp đã khởi tạo thất bại rồi được dùng lại | `NoClassDefFoundError` | Log của lần thất bại đầu, restart/reload hợp lệ |
| Thư viện có class nhưng method runtime đã đổi/xóa | `NoSuchMethodError` | Binary version mismatch |
| File class biên dịch cho JDK quá mới | `UnsupportedClassVersionError` | Phiên bản bytecode/JVM |
| Reflection gặp hạn chế quyền module | Có thể `InaccessibleObjectException` hoặc lỗi access khác | `opens`/`exports`/API đang gọi |
| Hai plugin nạp lớp cùng tên bằng loaders khác nhau | Có thể `ClassCastException`/identity mismatch | Defining loader và API class chung |

Bảng là **bản đồ điều tra**, không phải quy tắc một-một của mọi JVM. Ví dụ JVM có thể trì hoãn resolution; lỗi chỉ xuất hiện khi một branch cụ thể của ứng dụng lần đầu thực sự sử dụng dependency ấy.

## 16. Vì sao lỗi có thể chỉ xuất hiện sau nhiều giờ chạy?

Không phải JVM phải resolve và initialize **mọi** lớp của ứng dụng ngay khi process bắt đầu. Một số tham chiếu chỉ cần thiết khi:

- Một endpoint ít được gọi lần đầu thực hiện thao tác.
- Một nhánh xử lý exception đặc biệt được kích hoạt.
- Một job scheduler chạy theo lịch.
- Người dùng kích hoạt plugin hoặc tính năng tùy chọn.
- Một test chạy được trong môi trường khác runtime production.
- Class loader mới được tạo khi deploy plugin.

Vì vậy ứng dụng khởi động thành công **không chứng minh** mọi dependency runtime đều đúng. Nên có kiểm thử integration/packaging cho các tính năng quan trọng và kiểm tra plugin theo hợp đồng.

Tuy nhiên, cũng không nên cố khởi tạo mọi class bằng reflection ở startup chỉ để “đảm bảo đủ dependency”: static initializer có thể có side effect và tiêu tốn tài nguyên; một số plugin được chủ đích nạp lười. Hãy chọn chiến lược validation theo ứng dụng.

## 17. Checklist phòng tránh trước khi triển khai

1. **Khóa phiên bản dependency** theo bản build và deploy được kiểm soát.
2. **Đảm bảo runtime dependency được đóng gói/triển khai** đúng môi trường thực.
3. **Kiểm tra compile vs runtime classpath**: không trộn test-only hoặc provided-only dependency vào giả định runtime.
4. **Xác nhận main class và module graph** phù hợp cách khởi động.
5. **Kiểm tra binary compatibility** giữa những JAR liên quan khi nâng cấp hoặc rollback.
6. **Kiểm tra phiên bản Java runtime** so với class file và thư viện.
7. **Hiểu class-loader isolation** khi dùng plugin, app server hoặc OSGi-like containers.
8. **Tránh làm việc nặng trong static initializer**, đặc biệt truy cập network, đọc secret hay tạo singleton với side effect phức tạp.
9. **Nếu phải có static initialization**, báo lỗi rõ và bảo đảm config/resource đã sẵn sàng.
10. **Xử lý plugin tùy chọn đúng ranh giới**, không nuốt linkage error của core dependency.
11. **Giữ log của lỗi đầu tiên**, đặc biệt `ExceptionInInitializerError` trước `NoClassDefFoundError`.
12. **Test artifact được triển khai**, không chỉ chương trình chạy từ IDE.
13. **Kiểm tra classpath/module path thật**, không chỉ dấu vết của bản build.
14. **Không parse message JVM thành logic**; dùng loại lỗi/cause và hợp đồng loader.
15. **Có cơ chế fail-fast khởi động** cho dependency bắt buộc nếu hệ thống cần chắc chắn về tính sẵn sàng.
16. **Đừng retry vô hạn** một lớp đã lỗi khởi tạo trong cùng loader.

## 18. Những nhầm lẫn thường gặp

| Nhầm lẫn | Vì sao không đúng | Cách hiểu đúng |
| --- | --- | --- |
| “`ClassNotFoundException` xảy ra lúc compile” | Đây là exception runtime được API công bố | Compile-time missing symbol là lỗi khác |
| “`NoClassDefFoundError` chỉ có nghĩa không có file `.class`” | Còn có trường hợp failed initialization | Đọc message, cause và lỗi trước đó |
| “Hai lớp exception này cùng extends `Exception`” | `NoClassDefFoundError` extends `Error` | Đọc hierarchy |
| “`catch (Exception)` bắt cả hai” | `Error` không nằm dưới `Exception` | Không bắt hết Error bằng thói quen |
| “Class có trong jar nghĩa là không thể lỗi” | Runtime loader/module/dependency/init có thể sai | Kiểm tra môi trường chạy thực |
| “`Class.forName` chỉ load, không init” | Overload một tham số yêu cầu initialization | Dùng overload boolean khi cần kiểm soát |
| “`loadClass` luôn khởi tạo class” | Mặc định không thực hiện initialization | Tách load/link/init |
| “`SomeClass.class` luôn chạy static initializer” | Class literal không phải yêu cầu active initialization | Phân biệt class token với active use |
| “Lần đầu lỗi init rồi lần hai sẽ thử lại” | Lớp có thể ở erroneous state | Sửa nguyên nhân, dùng loader/process thích hợp |
| “Cứ thêm `throws ClassNotFoundException` là sửa `NoClassDefFoundError`” | Checked signature không khắc phục runtime linkage | Sửa dependency/deployment |
| “Thay `catch (Exception)` bằng `catch (Throwable)`” | Có thể che lỗi JVM nghiêm trọng | Xử lý nhóm lỗi có ý nghĩa |
| “Class loader nào cũng nhìn thấy mọi class” | Loader có phạm vi riêng | Chọn loader đúng hợp đồng |
| “Chỉ còn `NoClassDefFoundError` thì lỗi gốc luôn là `ClassNotFoundException`” | Có thể do init failure/linkage khác | Theo cause chain và log đầu |
| “Một classpath đúng trong IDE sẽ đúng ở production” | Runtime packaging có thể khác | Test artifact runtime thực |
| “Cứ đổi sang JDK mới hơn sẽ sửa mọi lỗi lớp” | Có thể là binary mismatch, loader, init | Đối chiếu loại lỗi và dependency |
| “Dùng `Class.forName` để test mọi class ở startup luôn an toàn” | Có thể gây static side effects | Kiểm tra có chủ đích và đúng thời điểm |

## 19. Bài tập kiểm tra

**Bài A.** `Class.forName("vendor.optional.Driver")` ném `ClassNotFoundException`. Đây là checked hay unchecked?

**Đáp án:** checked exception; caller phải bắt hoặc khai báo theo hợp đồng API. Nếu driver là optional, có thể có chính sách fallback; nếu bắt buộc, cần báo lỗi cấu hình và giữ nguyên nhân.

**Bài B.** Ứng dụng compile với `Dependency.class` nhưng runtime chạy chỉ còn class của chương trình chính. Lúc dùng `Dependency.text()` xảy ra lỗi gì trong thí nghiệm?

**Đáp án:** `NoClassDefFoundError`, vì bytecode cần một định nghĩa lớp mà runtime không tìm được.

**Bài C.** Vì sao không thể nói `NoClassDefFoundError` là một checked `Exception`?

**Đáp án:** nó extends `LinkageError` → `Error`, không phải nhánh `Exception`. Trình biên dịch không yêu cầu xử lý checked.

**Bài D.** `Class.forName(name, false, loader)` có chủ đích chạy static initializer ngay không?

**Đáp án:** không, đối số `false` yêu cầu không khởi tạo class ở bước đó. Tuy vậy, load/link vẫn có thể gặp lỗi khác.

**Bài E.** `Class.forName(name)` chỉ có thể ném `ClassNotFoundException` hay có thể ném lỗi initialization?

**Đáp án:** có thể ném lỗi khác, bao gồm `ExceptionInInitializerError` hoặc `LinkageError` trong tình huống phù hợp. Đừng coi catch CNFE là handler cho mọi lỗi nạp lớp.

**Bài F.** Static initializer ném `IllegalStateException` lần đầu. Khi cùng lớp được dùng lại trong cùng loader, có thể gặp gì?

**Đáp án:** lần đầu thường `ExceptionInInitializerError`, lần sau có thể `NoClassDefFoundError` vì lớp đã ở trạng thái khởi tạo lỗi.

**Bài G.** Class nằm trong một JAR trên máy nhưng class loader của plugin không tìm thấy. Có mâu thuẫn không?

**Đáp án:** không. Phạm vi **visible to loader** khác với “file nằm đâu đó trên đĩa”.

**Bài H.** `catch (Exception ex)` có bắt được `NoClassDefFoundError` không?

**Đáp án:** không. `NoClassDefFoundError` là `Error`. Cần sửa runtime/deployment thay vì dựa vào handler chung.

**Bài I.** Một method biến mất ở bản runtime nhưng class vẫn còn. Lỗi nào nên xem xét?

**Đáp án:** `NoSuchMethodError`, một `LinkageError` liên quan binary compatibility, thay vì quy mọi trường hợp về `NoClassDefFoundError`.

**Bài J.** Nếu message có `"Could not initialize class"`, cách điều tra nào hợp lý nhất?

**Đáp án:** tìm lỗi khởi tạo **đầu tiên** của lớp trong log, xem `ExceptionInInitializerError` và cause; không vội kết luận thiếu JAR.

**Bài K.** `MyType.class` có tự khởi tạo static fields của `MyType` không?

**Đáp án:** bản thân class literal không kích hoạt khởi tạo `MyType` như việc gọi static method/đọc static field không phải constant; do đó ví dụ có thể lấy `Class` token trước lần khởi tạo thất bại.

**Bài L.** `ClassLoader.loadClass` trả về `Class<?>`; có bảo đảm static initializer đã chạy và instance tạo được chưa?

**Đáp án:** chưa. Load class và initialize/instantiate là các bước khác nhau. Một plugin có thể bị lỗi ở giai đoạn sau.

**Bài M.** Build pipeline thành công và test trong IDE xanh. Có bảo đảm production không gặp hai loại lỗi?

**Đáp án:** không. Runtime classpath, loader, Java version và artifact thực có thể khác. Cần kiểm tra bản phân phối đúng mục tiêu.

**Bài N.** Có nên phân loại lỗi chỉ bằng `ex.getMessage().contains("com/example")` không?

**Đáp án:** không. Message không phải hợp đồng API ổn định. Hãy dùng subtype, cause, loader/class name và thông tin build/runtime chính xác.

## 20. Bài tập mở rộng có thể tự chạy

**Bài 1 — đổi tên trong `Class.forName`.** Thay `"missing.demo.DoesNotExist"` thành `"java.lang.String"`. Dự đoán handler `ClassNotFoundException` có chạy không. Kiểm chứng bằng chương trình thử và giải thích vì sao `String` luôn có sẵn trong runtime Java chuẩn.

**Bài 2 — đổi `true` thành `false`.** Trong `ClassLoadingComparisonDemo`, thay lần gọi `Class.forName(token.getName(), true, loader)` đầu tiên thành `false`. Dự đoán khi nào `BrokenInit.STATE` mới được chạy và vì sao việc chỉ tìm class không có nghĩa class đã sẵn sàng sử dụng.

**Bài 3 — sửa lỗi initializer.** Thay thân `fail()` thành `return 42;`. Dự đoán `ExceptionInInitializerError` và `NoClassDefFoundError` có còn xuất hiện không khi khởi tạo class thành công trong process mới.

**Bài 4 — thí nghiệm classpath.** Chạy `MissingDefinitionDemo` với đủ hai class, rồi chạy với `Dependency.class` không có trên runtime classpath. Dự đoán exception ở mỗi lần. Nhớ chỉ thao tác trong thư mục `build` của thí nghiệm.

**Bài 5 — dùng một JAR.** Đóng gói `Dependency.class` riêng thành một JAR, đặt nó có hoặc không trên runtime classpath của `MissingDefinitionDemo`. So sánh kết quả và giải thích tại sao “JAR có ở thư mục” nhưng không ở classpath không đủ.

**Bài 6 — điều tra plugin tùy chọn.** Giả lập một cấu hình tên class sai. Thiết kế một phương thức tìm plugin trả `Optional<Class<?>>` khi class không tồn tại, nhưng để nguyên `LinkageError` nếu plugin phụ thuộc một class bị thiếu. Giải thích vì sao không gộp cả hai vào `Optional.empty()`.

## Kết luận

`ClassNotFoundException` và `NoClassDefFoundError` đều có thể báo rằng ứng dụng chưa sử dụng được một lớp, nhưng **khác hợp đồng và nguyên nhân điển hình**. `ClassNotFoundException` là **checked exception** từ việc tìm/nạp lớp theo tên qua API như `Class.forName` hoặc `ClassLoader.loadClass`. `NoClassDefFoundError` là **`LinkageError` unchecked**, gặp khi JVM cần một định nghĩa class ở runtime mà không dùng được hoặc lớp đã rơi vào trạng thái khởi tạo tĩnh thất bại.

Muốn xử lý đúng, phải phân biệt **loading, linking và initialization**, **classpath biên dịch với runtime**, cùng **class loader và module graph**. Trong log, hãy đọc **exception gốc của lần lỗi đầu**, đặc biệt với `ExceptionInInitializerError`, thay vì chỉ nhìn dòng `NoClassDefFoundError` xuất hiện sau. Với class/plugin tùy chọn, có thể có chính sách xử lý `ClassNotFoundException` cụ thể; với dependency bắt buộc và linkage failure, hãy ưu tiên **sửa bản đóng gói, cấu hình, phiên bản thư viện hoặc static initializer**, không nuốt `Error` rồi giả vờ tác vụ thành công.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="api-exceptions-010">UndeclaredThrowableException khi dùng dynamic proxy</a>

<details>
<summary>Xem chi tiết</summary>

# UndeclaredThrowableException khi dùng dynamic proxy trong Java

> **Java Core – Exception** · Nhóm: **Ngoại lệ API** · Trọng tâm: vì sao một `InvocationHandler` có thể ném checked exception nhưng lời gọi qua JDK dynamic proxy lại nhận `UndeclaredThrowableException`; cách giữ nguyên cause, mở lớp `InvocationTargetException` khi ủy quyền bằng reflection, và thiết kế hợp đồng `throws` đúng. Ví dụ tương thích **Java 17+**.

## 1. Hiện tượng: method không khai báo IOException nhưng proxy lại gặp lỗi I/O

Giả sử một interface công khai có phương thức:

~~~java
interface Service {
    String find();
}
~~~

Đoạn trên là khai báo interface độc lập hợp lệ. Vì phương thức `find()` **không khai báo checked exception**, caller bình thường có thể gọi mà không phải catch/declare `IOException`.

Nhưng một JDK dynamic proxy được tạo cho interface ấy có thể được điều khiển bởi `InvocationHandler`, và handler lại ném:

~~~java
throw new java.io.IOException("remote service offline");
~~~

Dòng này là ví dụ bên trong một handler hoặc method được phép ném IOException. Khi caller gọi `proxy.find()`, nó thường **không nhận trực tiếp IOException**. Theo cơ chế JDK dynamic proxy, một checked exception từ `InvocationHandler.invoke(...)` **không phù hợp với throws clause của method được gọi** sẽ bị bọc trong:

~~~text
java.lang.reflect.UndeclaredThrowableException
    └── java.io.IOException: remote service offline
~~~

**Tên đầy đủ:** `java.lang.reflect.UndeclaredThrowableException`, thường viết tắt **UTE**.

UTE kế thừa `RuntimeException`, tức **unchecked exception**. Exception gốc có thể truy xuất từ:

~~~java
Throwable original = ex.getUndeclaredThrowable();
// Hoặc, theo cơ chế exception chaining phổ biến:
Throwable cause = ex.getCause();
~~~

Đây là đoạn minh họa đặt trong handler có biến `ex` kiểu UndeclaredThrowableException. API UTE Java 17 nêu rõ `getUndeclaredThrowable()` và `getCause()` thể hiện cùng exception đã được bọc theo cơ chế chaining của lớp này.

**Điểm cần nhớ:** UTE không nhất thiết chỉ ra một bug trong chính class proxy. Nó thường báo rằng **exception thoát khỏi invocation handler không khớp hợp đồng checked exception của interface mà proxy đang triển khai**.

## 2. Ba thành phần cần phân biệt

### 2.1. Interface: hợp đồng mà caller thấy

~~~java
interface Repository {
    String load() throws java.io.IOException;
}
~~~

Hợp đồng `load()` cho phép caller nhận `IOException` (hoặc subtype checked thích hợp). Nếu implementation động không thể hoàn thành thao tác, nó có thể truyền `IOException` gốc theo con đường được cho phép.

Trái lại:

~~~java
interface Service {
    String load();
}
~~~

Hợp đồng của `Service.load()` **không cho checked IOException thoát ra ở compile time**. Người dùng interface không bắt buộc (và trong một số cách viết còn không thể hợp lệ) đặt `catch (IOException)` cho lời gọi đơn lẻ này nếu không có nguồn checked IOException nào khác trong try.

### 2.2. InvocationHandler: bộ định tuyến lời gọi động

JDK cung cấp interface:

~~~java
@FunctionalInterface
public interface InvocationHandler {
    Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable;
}
~~~

Đây là **chữ ký rút gọn mang tính minh họa**: `Method` là `java.lang.reflect.Method`; trong Java SE 17, `InvocationHandler` có một abstract method `invoke` với nội dung tương ứng. Cách khai báo ở trên không nhằm tạo mới class/interface trùng tên trong package chuẩn.

Ba tham số:

- `proxy`: đối tượng proxy nhận lời gọi.
- `method`: metadata `Method` tương ứng lời gọi interface.
- `args`: các đối số được truyền, hoặc `null` khi không có đối số.

Kiểu trả về `Object` cho phép proxy chuyển/ép giá trị về kiểu trả về của method tương ứng. Nếu method interface trả primitive mà handler trả null hoặc kiểu wrapper không tương thích, proxy có thể báo NPE hoặc ClassCastException; đó là **cơ chế kết quả**, khác với UTE.

Điểm dễ gây hiểu nhầm nhất là chữ ký:

~~~java
Object invoke(...) throws Throwable
~~~

Nó cho phép **mã của handler** viết `throw new IOException(...)`, `throw new SQLException(...)` hay ném một checked exception khác về mặt kiểm tra compiler tại chính handler. **Nhưng chữ ký rộng của handler không hề mở rộng mệnh đề `throws` của các method interface do proxy cung cấp cho caller.**

### 2.3. Proxy object: nơi kiểm tra hợp đồng thực tế

Một proxy được tạo bằng:

~~~java
Service service = (Service) Proxy.newProxyInstance(
        Service.class.getClassLoader(),
        new Class<?>[] { Service.class },
        handler);
~~~

Đây là đoạn minh họa: cần import `java.lang.reflect.Proxy` và định nghĩa `Service`, `handler`. Đối tượng sinh ra triển khai một hoặc nhiều **interface được chỉ định**, và phần lớn lời gọi method interface được chuyển tới handler.

Khi handler ném Throwable, proxy tuân quy tắc:

1. Nếu là **RuntimeException** hoặc subtype → truyền ra trực tiếp, không tự bọc UTE vì unchecked.
2. Nếu là **Error** hoặc subtype → truyền ra trực tiếp, không tự bọc UTE.
3. Nếu là **checked Throwable** có kiểu tương thích với mệnh đề `throws` của method proxy → được phép truyền trực tiếp.
4. Nếu là **checked Throwable không tương thích** → proxy tạo/ném một `UndeclaredThrowableException` chứa Throwable ấy.

Những điểm trên là **hợp đồng JDK Proxy/InvocationHandler**, không phải quy tắc mọi loại proxy của mọi framework bên ngoài đều cài đặt y hệt.

## 3. Sơ đồ cơ chế dẫn tới UTE

~~~text
Caller
  |
  | gọi service.find()
  v
JDK dynamic proxy (method do interface khai báo)
  |
  | chuyển tới InvocationHandler.invoke(...)
  v
Handler thực hiện logic
  |
  +-- trả giá trị bình thường --> proxy trả về caller
  |
  +-- ném RuntimeException/Error ---------> truyền ra caller
  |
  +-- ném checked tương thích throws ------> truyền ra caller
  |
  +-- ném checked không tương thích throws
          |
          v
    UndeclaredThrowableException
          |
          +-- getUndeclaredThrowable() --> Throwable gốc
          +-- getCause() --------------> Throwable gốc
          |
          v
       caller nhận UTE
~~~

Ví dụ `interface Service { String find(); }` không có throws. Nếu handler ném `IOException` thì caller nhận UTE. Nếu đổi hợp đồng thành `String find() throws IOException;`, cùng loại IOException có thể đi ra trực tiếp.

**Giới hạn:** Proxy được phép bọc checked exception **do handler ném không khai báo tương thích**. Nếu một đoạn code thông thường tự `throw new UndeclaredThrowableException(...)` thì bạn cũng sẽ gặp class UTE, nhưng đó không phải bằng chứng chắc chắn lỗi đến từ proxy. Hãy xem stack trace và chain trước khi kết luận.

## 4. Chương trình Java 17 hoàn chỉnh: các loại exception qua proxy

Lưu toàn bộ mã thành **`UndeclaredThrowableProxyDemo.java`**. Chương trình trình diễn:

- IOException **được method interface khai báo** → truyền trực tiếp.
- IOException **không được method interface khai báo** → UTE.
- IllegalStateException (**unchecked**) → truyền trực tiếp.
- AssertionError (**Error**) → truyền trực tiếp.
- Handler dùng `Method.invoke` nhưng **quên mở InvocationTargetException** → UTE bọc InvocationTargetException.
- Handler biết mở `InvocationTargetException` → IOException gốc lại đi ra trực tiếp, đúng hợp đồng interface.

~~~java
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;

public class UndeclaredThrowableProxyDemo {
    public interface Service {
        String declared() throws IOException;
        String undeclared();
        String unchecked();
        String serious();
    }

    private static Service proxyFor(InvocationHandler handler) {
        return (Service) Proxy.newProxyInstance(
                Service.class.getClassLoader(),
                new Class<?>[] {Service.class},
                handler);
    }

    private static void show(String label, Runnable call) {
        System.out.println("== " + label + " ==");
        try {
            call.run();
        } catch (UndeclaredThrowableException ex) {
            Throwable root = ex.getUndeclaredThrowable();
            System.out.println("outer=" + ex.getClass().getSimpleName());
            System.out.println("wrapped=" + root.getClass().getSimpleName()
                    + ": " + root.getMessage());
            System.out.println("same-cause=" + (ex.getCause() == root));
        } catch (RuntimeException ex) {
            System.out.println("runtime=" + ex.getClass().getSimpleName()
                    + ": " + ex.getMessage());
        } catch (AssertionError ex) {
            System.out.println("error=" + ex.getClass().getSimpleName()
                    + ": " + ex.getMessage());
        }
    }

    public interface Repository {
        String load() throws IOException;
    }

    public static final class RepositoryImpl implements Repository {
        @Override
        public String load() throws IOException {
            throw new IOException("disk offline");
        }
    }

    private static Repository reflected(Repository target, boolean unwrap) {
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException ex) {
                if (unwrap) {
                    throw ex.getCause();
                }
                throw ex;
            }
        };
        return (Repository) Proxy.newProxyInstance(
                Repository.class.getClassLoader(),
                new Class<?>[] {Repository.class},
                handler);
    }

    public static void main(String[] args) {
        Service service = proxyFor((proxy, method, arguments) -> {
            switch (method.getName()) {
                case "declared":
                    throw new IOException("declared checked");
                case "undeclared":
                    throw new IOException("not in interface");
                case "unchecked":
                    throw new IllegalStateException("business state");
                case "serious":
                    throw new AssertionError("fatal condition");
                default:
                    throw new IllegalArgumentException("unknown method");
            }
        });

        System.out.println("== declared checked ==");
        try {
            service.declared();
        } catch (IOException ex) {
            System.out.println("checked=" + ex.getClass().getSimpleName()
                    + ": " + ex.getMessage());
        }

        show("undeclared checked", service::undeclared);
        show("unchecked", service::unchecked);
        show("error", service::serious);

        Repository target = new RepositoryImpl();
        System.out.println("== reflection without unwrap ==");
        try {
            reflected(target, false).load();
        } catch (UndeclaredThrowableException ex) {
            Throwable wrapper = ex.getUndeclaredThrowable();
            System.out.println("outer=" + ex.getClass().getSimpleName());
            System.out.println("wrapped=" + wrapper.getClass().getSimpleName());
            System.out.println("target=" + ((InvocationTargetException) wrapper)
                    .getTargetException().getMessage());
        } catch (IOException ex) {
            System.out.println("unexpected=" + ex);
        }

        System.out.println("== reflection with unwrap ==");
        try {
            reflected(target, true).load();
        } catch (IOException ex) {
            System.out.println("checked=" + ex.getClass().getSimpleName()
                    + ": " + ex.getMessage());
        }
    }
}
~~~

### Biên dịch và chạy

~~~bash
javac --release 17 -encoding UTF-8 UndeclaredThrowableProxyDemo.java
java UndeclaredThrowableProxyDemo
~~~

**Stdout đã được kiểm chứng bằng cách biên dịch và chạy thực tế:**

~~~text
== declared checked ==
checked=IOException: declared checked
== undeclared checked ==
outer=UndeclaredThrowableException
wrapped=IOException: not in interface
same-cause=true
== unchecked ==
runtime=IllegalStateException: business state
== error ==
error=AssertionError: fatal condition
== reflection without unwrap ==
outer=UndeclaredThrowableException
wrapped=InvocationTargetException
target=disk offline
== reflection with unwrap ==
checked=IOException: disk offline
~~~

Chương trình chạy với JDK hỗ trợ Java 17 ở mức `javac --release 17`. Các exception được tạo có chủ ý, không có mạng/tệp bên ngoài, và stdout không phụ thuộc số dòng stack trace.

**Lưu ý về helper `show`:** Nó dùng `Runnable` và method reference để thể hiện các lời gọi **không khai báo checked exception**. Riêng phương thức `service.declared()` được gọi trong `try/catch (IOException)` riêng, bởi chữ ký của nó là checked. Đây cũng là một cách thấy sự khác biệt compile time giữa các phương thức trên cùng interface.

## 5. Phân tích từng nhánh của ví dụ

### 5.1. Declared checked: IOException đi ra trực tiếp

Interface định nghĩa:

~~~java
String declared() throws IOException;
~~~

Handler ném:

~~~java
throw new IOException("declared checked");
~~~

Tại proxy, kiểu thực tế `IOException` phù hợp với `throws IOException`. Vì vậy caller bắt **trực tiếp** `IOException` và in:

~~~text
checked=IOException: declared checked
~~~

Không có UTE ở đây. `InvocationHandler.invoke() throws Throwable` không làm mọi checked exception bị bọc: proxy **chỉ bọc nếu checked type không tương thích với hợp đồng method**.

Subtype cũng được chấp nhận. Ví dụ nếu method công bố `throws IOException`, handler có thể ném `FileNotFoundException`, vốn kế thừa IOException, và proxy sẽ truyền trực tiếp loại lỗi con đó.

### 5.2. Undeclared checked: IOException bị bọc thành UTE

Interface định nghĩa:

~~~java
String undeclared();
~~~

Handler vẫn ném IOException:

~~~java
throw new IOException("not in interface");
~~~

Lời gọi từ caller qua method không có checked throws, nên proxy ném:

~~~text
outer=UndeclaredThrowableException
wrapped=IOException: not in interface
same-cause=true
~~~

Kiểm tra:

~~~java
ex.getUndeclaredThrowable() == ex.getCause()
~~~

là true trong chương trình; exception được bọc chính là IOException do handler đã ném.

**Quan trọng:** IOException gốc không biến mất. UTE là **wrapper**. Khi chẩn đoán, đừng chỉ log `ex.getMessage()` mà phải giữ object Throwable và đọc cause/stack trace.

### 5.3. Unchecked exception đi qua không cần được khai báo

Handler ném:

~~~java
throw new IllegalStateException("business state");
~~~

`IllegalStateException extends RuntimeException`, là unchecked. Proxy truyền ra cùng kiểu lỗi, caller in:

~~~text
runtime=IllegalStateException: business state
~~~

Đây không phải UTE. Quy tắc tương tự áp dụng cho nhiều RuntimeException khác, kể cả `IllegalArgumentException` và `NullPointerException`.

**Không suy rằng mọi unchecked đều an toàn để tiếp tục nghiệp vụ.** Việc proxy cho ngoại lệ đi qua không miễn trừ yêu cầu thiết kế xử lý lỗi, rollback, logging và dừng tác vụ ở boundary thích hợp.

### 5.4. Error cũng không được proxy tự chuyển thành UTE

Handler ném:

~~~java
throw new AssertionError("fatal condition");
~~~

`AssertionError` nằm trong nhánh `Error`, thuộc nhóm unchecked. Output:

~~~text
error=AssertionError: fatal condition
~~~

Ở đây ví dụ **bắt AssertionError chỉ để minh họa** rằng loại Error không bị UTE bọc. Trong ứng dụng thật, đừng catch mọi Error để coi như lỗi nghiệp vụ bình thường; Error thường liên quan tới những tình huống nghiêm trọng hoặc invariant không thể tiếp tục đáng tin cậy.

### 5.5. Catch order cũng áp dụng với UTE

Vì UTE kế thừa `RuntimeException`, nếu cùng chuỗi catch có cả hai thì handler UTE phải đứng trước handler RuntimeException:

~~~java
try {
    service.undeclared();
} catch (UndeclaredThrowableException ex) {
    handleProxyMismatch(ex);
} catch (RuntimeException ex) {
    handleOtherRuntimeFailure(ex);
}
~~~

Đây là đoạn minh họa; các hàm handler cần định nghĩa. Nếu đặt `catch (RuntimeException)` lên trước, handler UTE sau đó bị che và compiler từ chối vì unreachable catch.

Ngược lại, `catch (IOException)` **không thể tự nhận IOException ẩn bên trong UTE**. Catch chọn theo kiểu Throwable **đang thực sự được ném ở tầng hiện tại**. Muốn truy nguyên IOException thì đọc cause và kiểm tra kiểu gốc, hoặc sửa hợp đồng/handler để truyền trực tiếp nếu phù hợp.

## 6. Bẫy lớn nhất: Method.invoke tạo InvocationTargetException

Một dynamic proxy thường được dùng để thêm logging, metrics, retry hoặc kiểm soát truy cập trước/sau khi gọi một object thật.

Cách ủy quyền bằng reflection:

~~~java
InvocationHandler handler = (proxy, method, args) -> method.invoke(target, args);
~~~

Đây là đoạn minh họa, cần biến `target` với method tương ứng.

**Vấn đề:** Khi phương thức **được gọi qua reflection** ném exception, `Method.invoke(...)` dùng một wrapper checked riêng:

~~~text
InvocationTargetException
    └── exception thật do target method ném
~~~

Ví dụ method `Repository.load() throws IOException` của target ném `IOException("disk offline")`. `method.invoke(target, args)` không truyền trực tiếp IOException đó lên handler, mà ném `InvocationTargetException`.

`InvocationTargetException` là checked exception thuộc package `java.lang.reflect` và **không phải subtype của IOException**.

Nếu handler chỉ viết:

~~~java
return method.invoke(target, args);
~~~

thì `InvocationTargetException` có thể thoát khỏi `InvocationHandler.invoke()` (vì handler khai báo `throws Throwable`). Nhưng method interface `Repository.load()` chỉ cho phép checked IOException. Proxy xét **loại checked thực sự thoát khỏi handler**, thấy `InvocationTargetException` không phù hợp, rồi **bọc thêm UTE**:

~~~text
UndeclaredThrowableException
    └── InvocationTargetException
          └── IOException("disk offline")
~~~

Đây chính là hiện tượng **double wrapping** thường gặp trong AOP hoặc framework proxy tự viết.

### 6.1. Phần output chứng minh double wrapping

Trong chương trình đầy đủ, `reflected(target, false)` không unwrap `InvocationTargetException`. Output:

~~~text
== reflection without unwrap ==
outer=UndeclaredThrowableException
wrapped=InvocationTargetException
target=disk offline
~~~

Điểm quan trọng: mặc dù method interface **đã khai báo `throws IOException`**, caller **vẫn nhận UTE** vì đối tượng checked mà handler ném ra là **InvocationTargetException**, chứ không phải IOException ban đầu.

Đây là lý do không thể chỉ nhìn tên exception gốc hoặc chữ ký interface mà kết luận proxy sẽ tự truyền trực tiếp. Cần kiểm tra **lớp wrapper nào thoát khỏi handler**.

### 6.2. Cách sửa: mở InvocationTargetException ngay trong handler

Phương thức trích từ ví dụ:

~~~java
InvocationHandler handler = (proxy, method, args) -> {
    try {
        return method.invoke(target, args);
    } catch (InvocationTargetException ex) {
        throw ex.getCause();
    }
};
~~~

Mã minh họa trong một method có biến `target` hợp lệ, và import `InvocationHandler`, `InvocationTargetException`.

`ex.getCause()` hoặc `ex.getTargetException()` truy xuất Throwable thực sự mà target đã ném. Handler ném lại **exception gốc**:

- Nếu gốc là IOException và interface cho phép IOException → proxy truyền trực tiếp.
- Nếu gốc là RuntimeException/Error → proxy truyền trực tiếp theo nhánh unchecked.
- Nếu gốc là checked exception **không thuộc hợp đồng interface** → proxy **vẫn** bọc UTE. Unwrap không làm cho exception trái hợp đồng trở thành hợp lệ.

Output phần đã sửa:

~~~text
== reflection with unwrap ==
checked=IOException: disk offline
~~~

**Vì sao nên unwrap ở handler thay vì catch UTE tại mọi caller?** Handler là tầng đã tạo ra wrapper reflection. Sửa tại đây bảo toàn đúng hợp đồng public API, tránh buộc mọi caller hiểu chi tiết implementation và làm cho code sử dụng interface đơn giản, rõ ràng.

### 6.3. Các ngoại lệ khác từ Method.invoke

`Method.invoke` có thể ném `IllegalAccessException` nếu truy cập không được phép, `IllegalArgumentException` khi đối số/receiver không phù hợp và các vấn đề reflection khác. Không được bắt tất cả `Exception` rồi tự cho rằng đó đều là “business IOException từ target”.

Nếu vấn đề là **reflection configuration**, có thể phù hợp hơn với một unchecked exception có thông điệp và cause để báo lỗi framework/implementation. Đừng cố biến `IllegalAccessException` thành `IOException` chỉ để vừa với một chữ ký interface khi thực chất hạ tầng proxy cấu hình sai.

## 7. Cách đọc UTE và truy vết nguyên nhân

Khi nhận UTE, bắt đầu bằng:

~~~java
catch (UndeclaredThrowableException ex) {
    Throwable original = ex.getUndeclaredThrowable();
    // Ghi log đầy đủ ex cùng original/cause theo chính sách.
}
~~~

Đây là handler minh họa nằm trong cấu trúc try/catch phù hợp.

Hai API thường dùng:

| API | Giá trị |
| --- | --- |
| `ex.getUndeclaredThrowable()` | Throwable được bọc, theo hợp đồng chuyên biệt của UTE; có thể null với một số constructor |
| `ex.getCause()` | Cause theo cơ chế chaining chung của Throwable; đối với UTE tạo với wrapped exception thì tham chiếu tới cùng Throwable |
| `ex.printStackTrace()` | In UTE, cause và stack trace, giúp thấy các lớp bọc |
| `ex.getSuppressed()` | Các lỗi phụ được gắn vào chính UTE, nếu có; không tự đồng nghĩa lỗi proxy |

Đừng chỉ log:

~~~java
System.err.println(ex.getMessage());
~~~

Message của UTE có thể ít thông tin, hoặc chứa một biểu diễn của lớp wrapped thay vì bối cảnh nghiệp vụ đủ rõ. Hãy log **object exception**, giữ stack trace và cause.

### 7.1. Có nên unwrap mọi loại exception theo vòng lặp?

Khi bạn là **tầng quan sát/diagnostics**, có thể cần đi qua chain UTE → InvocationTargetException → IOException để tìm nguyên nhân.

Nhưng **không tự động ném lại “root cause sâu nhất” ở mọi nơi**: một wrapper có thể biểu thị bối cảnh hay hợp đồng public API quan trọng; bỏ hết lớp bọc có thể khiến caller nhận một checked exception không còn phù hợp với chữ ký. Cần phân biệt:

- **Unwrap khi ủy quyền reflection trong handler** để không rò rỉ wrapper kỹ thuật `InvocationTargetException`.
- **Traverse chain để log/diagnose** khi cần.
- **Translate exception** ở ranh giới API theo hợp đồng, giữ cause thay vì phá vỡ hoặc làm mất thông tin.

### 7.2. Nếu cause null hoặc không đúng loại thì sao?

Java cho phép tạo UTE thủ công bằng constructor nhận Throwable; API nêu `getUndeclaredThrowable()` **có thể trả null**. Vì vậy với code thư viện hoặc UTE không biết nguồn, hãy kiểm tra null trước khi gọi `getClass()`.

Ví dụ an toàn:

~~~java
Throwable cause = ex.getUndeclaredThrowable();
if (cause == null) {
    System.err.println("Undeclared throwable unavailable");
} else {
    System.err.println("Original type: " + cause.getClass().getName());
}
~~~

Đây là đoạn minh họa, biến `ex` cần là UTE. Không ép kiểu thẳng sang IOException chỉ vì *một lần* gặp IOException trong log trước đó.

## 8. Ví dụ nâng cao: hai interface có cùng method nhưng throws khác nhau

Một trường hợp ít gặp hơn nhưng quan trọng khi tự viết dynamic proxy là **duplicate interface methods**: proxy triển khai nhiều interface có method **cùng tên và chữ ký**, nhưng các interface công khai **những checked exception khác nhau**.

Ví dụ:

~~~java
interface FileAction {
    void execute() throws java.io.IOException;
}

interface DbAction {
    void execute() throws java.sql.SQLException;
}
~~~

Cùng một proxy object có thể được gọi qua `FileAction.execute()` hoặc `DbAction.execute()`.

Mọi checked exception thoát khỏi handler phải **tương thích với throws clause của tất cả các method interface trùng chữ ký** có thể được gọi qua proxy. Vì `IOException` và `SQLException` là hai nhánh checked **không kế thừa nhau**, việc ném IOException **không hợp lệ với DbAction.execute()**. Trong trường hợp này, proxy không thể cho IOException thoát ra trực tiếp, dù caller đang cast proxy về `FileAction`.

Một điểm rất dễ đánh lừa người đọc: đối tượng `Method` truyền vào handler có thể đến từ **interface xuất hiện đầu tiên trong danh sách proxy interfaces**, nên `method.getExceptionTypes()` **không nhất thiết** biểu diễn đầy đủ ràng buộc checked exception của **mọi interface trùng method**.

### 8.1. Chương trình riêng kiểm chứng trường hợp method trùng

Lưu thành file **`DuplicateProxyThrowsDemo.java`**:

~~~java
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import java.sql.SQLException;

public class DuplicateProxyThrowsDemo {
    interface FileAction { void execute() throws IOException; }
    interface DbAction { void execute() throws SQLException; }

    public static void main(String[] args) {
        Object proxy = Proxy.newProxyInstance(
                DuplicateProxyThrowsDemo.class.getClassLoader(),
                new Class<?>[] {FileAction.class, DbAction.class},
                (obj, method, arguments) -> {
                    System.out.println("handler-declaring="
                            + method.getDeclaringClass().getSimpleName());
                    throw new IOException("read failed");
                });

        try {
            ((FileAction) proxy).execute();
        } catch (UndeclaredThrowableException ex) {
            System.out.println("file-call=" + ex.getClass().getSimpleName());
            System.out.println("cause="
                    + ex.getCause().getClass().getSimpleName());
        } catch (IOException ex) {
            System.out.println("direct-io=" + ex.getMessage());
        }

        try {
            ((DbAction) proxy).execute();
        } catch (UndeclaredThrowableException ex) {
            System.out.println("db-call=" + ex.getClass().getSimpleName());
        } catch (SQLException ex) {
            System.out.println("direct-sql=" + ex.getMessage());
        }
    }
}
~~~

Biên dịch/chạy độc lập:

~~~bash
javac --release 17 -encoding UTF-8 DuplicateProxyThrowsDemo.java
java DuplicateProxyThrowsDemo
~~~

**Stdout đã kiểm chứng:**

~~~text
handler-declaring=FileAction
file-call=UndeclaredThrowableException
cause=IOException
handler-declaring=FileAction
db-call=UndeclaredThrowableException
~~~

Điều cần rút ra:

1. Proxy được khai báo thứ tự interface `FileAction` trước `DbAction`, nên metadata Method trong ví dụ được nhận diện là `FileAction`.
2. Handler ném IOException, nhưng method tương ứng của `DbAction` không khai báo IOException.
3. UTE xuất hiện cả khi caller gọi qua `FileAction` và khi gọi qua `DbAction`.
4. Chỉ dựa trên `Method.getExceptionTypes()` của object đưa cho handler là **chưa đủ** để chứng minh ngoại lệ checked sẽ đi qua khi có method trùng giữa nhiều interfaces.

Theo Javadoc `Proxy` Java 17, trường hợp duplicate methods phải tuân điều kiện tương thích cho tất cả những interface methods mà proxy có thể được gọi qua. Một thiết kế đơn giản hơn có thể tránh tình trạng này bằng cách tách API hoặc thống nhất hợp đồng checked exception giữa các interface.

## 9. Khi nào proxy ném UTE và khi nào không?

| Throwable thoát khỏi `InvocationHandler.invoke` | Interface method | Kết quả thường thấy |
| --- | --- | --- |
| `IOException` | `throws IOException` | IOException trực tiếp |
| `FileNotFoundException` | `throws IOException` | FileNotFoundException trực tiếp |
| `IOException` | Không `throws` checked | UTE bọc IOException |
| `SQLException` | `throws IOException` | UTE bọc SQLException |
| `IllegalStateException` | Không `throws` | IllegalStateException trực tiếp |
| `NullPointerException` | `throws IOException` | NullPointerException trực tiếp |
| `AssertionError` | Không `throws` | AssertionError trực tiếp |
| `InvocationTargetException` | `throws IOException` | UTE bọc InvocationTargetException |
| `IOException` sau khi unwrap `InvocationTargetException` | `throws IOException` | IOException trực tiếp |
| `IOException` với hai method trùng chữ ký, một interface chỉ cho SQLException | Cả hai interface phải được xét | UTE bọc IOException |

Bảng nói về **JDK dynamic proxy** do `java.lang.reflect.Proxy` tạo, không nên áp dụng máy móc cho tất cả proxy của framework hoặc proxy theo subclass.

## 10. Checked exception ở compile time và runtime không mâu thuẫn

Một câu hỏi thường gặp: “Nếu method `String undeclared()` không khai báo IOException, tại sao handler biên dịch được khi ném IOException?”

Phải phân biệt **hai vị trí compiler kiểm tra**:

**Tại nơi cài đặt handler:**

~~~java
InvocationHandler handler = (proxy, method, args) -> {
    throw new IOException("network failed");
};
~~~

Mã hợp lệ vì `InvocationHandler.invoke` khai báo `throws Throwable`.

**Tại nơi gọi method interface:**

~~~java
Service service = ...;
service.undeclared();
~~~

Caller nhìn thấy `Service.undeclared()` không có checked IOException. Compiler không buộc xử lý IOException ở đây. Tại runtime, proxy nhận checked IOException từ handler rồi tạo UTE unchecked để truyền kết quả thất bại mà vẫn tuân giới hạn checked throws của method.

Không có mâu thuẫn giữa compile time và runtime; **proxy cung cấp một cơ chế runtime wrapping** cho tình huống handler đã được phép ném rộng hơn chữ ký interface.

### Vì sao không viết catch IOException trực tiếp?

Nếu đoạn `try` chỉ chứa lời gọi phương thức không khai báo checked IOException (và không có nguồn checked IOException khác), câu lệnh:

~~~java
// CỐ Ý KHÔNG BIÊN DỊCH TRONG NGỮ CẢNH NÀY:
try {
    service.undeclared();
} catch (IOException ex) {
    System.out.println("Could not load");
}
~~~

bị compiler báo IOException không được ném theo phân tích compile-time của try. Việc handler ở runtime có thể ném IOException trước khi bị proxy chuyển thành UTE **không thay đổi kết quả phân tích compile-time ở caller**.

Nếu service công khai một hợp đồng có thể lỗi I/O mà caller cần xử lý riêng, hãy cân nhắc khai báo `throws IOException` trên interface từ đầu, hoặc dùng một custom runtime exception hợp đồng có cause là IOException khi policy API không muốn checked exception.

## 11. Cách sửa đúng theo tầng gây vấn đề

### 11.1. Sửa interface khi checked IOException là một phần hợp đồng

Nếu API công khai cho phép lỗi I/O và caller cần biết loại này:

~~~java
interface RemoteReader {
    byte[] read() throws IOException;
}
~~~

Đây là interface minh họa, cần import IOException. Handler có thể ném IOException hoặc subtype phù hợp. Caller bắt/khai báo IOException theo cách Java thông thường. Đây là lựa chọn rõ ràng khi **checked exception được kỳ vọng và có ý nghĩa với caller**.

Không tự đổi `throws` của một public interface đang được sử dụng rộng rãi mà chưa xét tương thích nguồn/binary và chính sách phiên bản; đây là thay đổi hợp đồng.

### 11.2. Dùng runtime wrapper có cause nếu API không muốn checked throws

Nếu interface cố ý không khai báo checked exception, thay vì để raw IOException thoát khỏi handler và tự nhận UTE chung chung, bạn có thể chuyển sang một unchecked exception phù hợp:

~~~java
try {
    return readFromDisk();
} catch (IOException ex) {
    throw new java.io.UncheckedIOException("Cannot load data", ex);
}
~~~

Đây là mã minh họa bên trong handler hoặc method phù hợp, cần định nghĩa `readFromDisk()`. `UncheckedIOException` chuyên dùng để bọc IOException, giữ cause. Nếu là lỗi thuộc một domain khác, chọn một custom runtime exception phù hợp với **hợp đồng caller**, không mặc định dùng UncheckedIOException cho mọi checked Throwable.

Ưu điểm là caller nhận một lỗi **được đặt tên có chủ đích**, không phải wrapper kỹ thuật UTE phát sinh bất ngờ. Nhưng đổi checked sang unchecked cũng phải dựa vào chính sách API; nó không tự giải quyết khả năng phục hồi lỗi hoặc retry.

### 11.3. Unwrap InvocationTargetException trong handler phản chiếu

Nếu handler delegate bằng `Method.invoke`, ưu tiên:

~~~java
try {
    return method.invoke(target, args);
} catch (InvocationTargetException ex) {
    throw ex.getCause();
}
~~~

Mẫu trích từ chương trình chính. Đừng đưa nguyên `InvocationTargetException` sang proxy nếu public interface chỉ khai báo checked exception gốc.

### 11.4. Xử lý lỗi setup reflection riêng

Những tình huống `IllegalAccessException`, cấu hình quyền truy cập, sai target, sai tham số hoặc sai method là vấn đề framework/implementation. Hãy phân loại và báo lỗi kỹ thuật rõ, không bọc hết chúng thành `IOException` để “lách” hợp đồng.

### 11.5. Ở caller: log cause và áp dụng fallback an toàn

Nếu caller không sở hữu interface/handler (ví dụ dùng thư viện bên thứ ba), có thể bắt UTE **ở ranh giới tích hợp** và phân tích cause để đưa ra hành động được tài liệu hóa.

Ví dụ:

~~~java
try {
    invokeThirdPartyService();
} catch (UndeclaredThrowableException ex) {
    Throwable cause = ex.getUndeclaredThrowable();
    logUnexpectedProxyFailure(ex, cause);
    throw ex; // Hoặc chuyển sang lỗi hợp đồng rõ ràng ở boundary.
}
~~~

Đây là đoạn minh họa: cần định nghĩa các hàm và biến phù hợp. Không bắt UTE rồi bỏ qua để tiếp tục như thể thao tác đã thành công. Nếu quyết định dịch sang domain exception, giữ cause, ngữ cảnh và không dựa vào message của wrapper.

## 12. Proxy chỉ áp dụng cho interface, không đồng nhất với subclass proxy

JDK dynamic proxy được tạo qua `java.lang.reflect.Proxy` triển khai **interface**. Nó không biến một class bất kỳ thành subclass động theo cùng API.

Trong các framework, có thể tồn tại nhiều cơ chế proxy:

- **JDK interface-based dynamic proxy:** sử dụng `Proxy` và `InvocationHandler`, tuân quy tắc UTE đang học.
- **Subclass-based proxy hoặc bytecode enhancement:** có thể sử dụng cơ chế khác để tạo/intercept method; hành vi exception phụ thuộc framework và hợp đồng method thực tế.
- **Remote proxy (RPC):** có thể chuyển lỗi từ tiến trình khác thành exception domain/transport; không đồng nghĩa luôn dùng `java.lang.reflect.Proxy`.

Nếu thấy UTE trong ứng dụng dùng framework, hãy xem stack trace và cấu hình proxy để xác nhận proxy thực sự là JDK proxy. **Không kết luận mọi proxy đều bọc checked exception theo một cách giống nhau** chỉ dựa vào từ “proxy”.

## 13. Các method đặc biệt: equals, hashCode, toString

Một proxy JDK thường điều hướng cả lời gọi các method public của `Object` như `equals`, `hashCode` và `toString` đến `InvocationHandler`.

Nếu handler chỉ giả định mọi method đều là business API (ví dụ `load`, `save`), việc logger hoặc debugger gọi `proxy.toString()` có thể dẫn tới lỗi ngoài dự kiến.

Một chiến lược đơn giản:

~~~java
if (method.getDeclaringClass() == Object.class) {
    return switch (method.getName()) {
        case "toString" -> "ServiceProxy";
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" -> proxy == args[0];
        default -> throw new UnsupportedOperationException(method.getName());
    };
}
~~~

Đây là **đoạn minh họa bên trong handler**, cần tham số `method`, `proxy`, `args`, và nếu triển khai thực tế phải xét chính sách equality/hashCode phù hợp.

Một lỗi thiết kế nguy hiểm là xử lý method của `Object` bằng cách gọi trực tiếp `method.invoke(proxy, args)` ngay trong handler: lời gọi phản chiếu trên chính proxy có thể đi vào handler trở lại, gây **đệ quy vô hạn**. Phải ủy quyền đến object thật phù hợp hoặc có chính sách riêng.

`equals/hashCode/toString` không khai báo checked exception. Vì vậy nếu handler cố ném checked IOException từ những lời gọi này, quy tắc UTE cũng có thể xuất hiện. Nhưng đây chỉ là một phần của bức tranh rộng hơn về hợp đồng proxy.

## 14. Method trùng giữa interface và Object: không nên chỉ nhìn Method metadata

Với các method `equals`, `hashCode` và `toString`, JDK proxy có quy tắc xử lý đặc biệt về `Method` đưa vào invocation handler. Với các duplicate methods giữa nhiều interfaces, metadata `Method` có thể gắn với interface xuất hiện sớm hơn.

Điều này tạo hai khuyến nghị:

1. **Không xem `method.getDeclaringClass()` là bằng chứng duy nhất** về mọi hợp đồng throws của các interface được proxy triển khai.
2. Khi handler phụ thuộc vào chữ ký method, cần phân biệt method của Object, method interface trùng và các interface chuyên biệt. Đặc biệt chú ý trường hợp có nhiều throws clause không tương thích.

Với proxy phức tạp, nên xem danh sách interface của proxy và thiết kế một **mapping explicit** cho method/exception policy, thay vì chỉ dùng `Method.getExceptionTypes()` rồi giả định đủ.

## 15. UTE có giống InvocationTargetException không?

**Không.** Hai lớp này khác vai trò và vị trí xuất hiện.

| Đặc điểm | `UndeclaredThrowableException` | `InvocationTargetException` |
| --- | --- | --- |
| Package | `java.lang.reflect` | `java.lang.reflect` |
| Nhóm | **Unchecked** (extends RuntimeException) | **Checked** (extends ReflectiveOperationException) |
| Thường do đâu? | JDK proxy gặp checked Throwable không hợp đồng | `Method.invoke`/reflection báo exception do target method ném |
| Truy xuất Throwable bọc | `getUndeclaredThrowable()`, `getCause()` | `getTargetException()`, `getCause()` |
| Cần khai báo/catch khi method có thể ném? | Compiler không bắt buộc vì unchecked | Có nếu không xử lý trong handler/method |
| Cách phòng ngừa phổ biến | Giữ hợp đồng checked throws hoặc dịch ngoại lệ có chủ đích | Unwrap exception mục tiêu ngay khi delegate reflection |

Hai lớp **có thể lồng nhau**: UTE bọc InvocationTargetException nếu handler để wrapper checked reflection thoát ra qua một interface không khai báo loại checked đó.

## 16. Cách debug khi stack trace hiển thị UndeclaredThrowableException

Hãy làm theo trình tự:

1. **Xác nhận nguồn proxy:** đó có phải object được tạo từ `Proxy.newProxyInstance` không? Dùng stack trace và thông tin class/framework; có thể kiểm tra `Proxy.isProxyClass(instance.getClass())` nếu phù hợp.
2. **Xác định method interface mà caller vừa gọi**, đặc biệt throws clause công khai.
3. **Đọc wrapped throwable của UTE:** `getUndeclaredThrowable()` hoặc `getCause()`.
4. Nếu wrapped là `InvocationTargetException`, **tiếp tục xem `getTargetException()`/`getCause()`** để tìm lỗi target.
5. Xác định **handler đã ném checked exception nào ra ngoài**, khác với thông điệp hoặc loại sâu nhất trong chain.
6. **So kiểu checked thực tế với throws clause** của method interface, bao gồm cả các duplicate methods nếu có nhiều interface.
7. **Kiểm tra nơi delegate:** nếu có `Method.invoke`, handler đã catch `InvocationTargetException` chưa?
8. **Kiểm tra logic translate exception:** có bọc mọi exception vào checked type không được công bố không?
9. **Sửa tại tầng sở hữu hợp đồng hoặc handler** nếu có quyền. Không buộc mọi caller xử lý UTE bằng cách cast kiểu tùy tiện.
10. **Ghi log đầy đủ** cả wrapper lẫn cause, có đủ ngữ cảnh nhưng không lộ thông tin nhạy cảm.

Một chuỗi điển hình để đọc:

~~~text
UndeclaredThrowableException
  Caused by: InvocationTargetException
    Caused by: IOException("disk offline")
~~~

Đây là sơ đồ nguyên nhân, không phải nguyên văn của mọi `printStackTrace()`; tên frame, số dòng và định dạng tùy JVM.

## 17. Những lỗi thiết kế thường gặp

1. **“InvocationHandler.invoke throws Throwable nên public interface cũng cho throws Throwable.”** Sai: hai hợp đồng khác nhau.
2. **“UTE là checked exception.”** Sai: UTE kế thừa RuntimeException.
3. **“UTE làm mất exception gốc.”** Sai: thường có thể truy qua `getUndeclaredThrowable()`/`getCause()`.
4. **“Mọi exception handler ném đều bị UTE bọc.”** Sai: RuntimeException/Error đi qua; checked tương thích cũng đi qua.
5. **“Method interface có throws IOException thì IOException từ target qua Method.invoke chắc chắn đi thẳng ra.”** Sai nếu handler để InvocationTargetException thoát ra.
6. **“InvocationTargetException là subtype IOException.”** Sai: đó là checked reflection exception khác.
7. **“Chỉ cần đổi catch ở caller là chữa được proxy.”** Không nhất thiết: nên sửa handler/contract khi có quyền.
8. **“Catch IOException quanh một method không khai báo checked IOException lúc nào cũng hợp lệ.”** Không: compiler kiểm tra nguồn checked exception trong try.
9. **“Có thể tự chuyển mọi checked exception sang IOException để vừa throws.”** Sai nếu ý nghĩa lỗi không phù hợp.
10. **“Unwrap mọi Throwable tới tầng sâu nhất rồi ném là luôn tốt.”** Sai: có thể phá hợp đồng ở ranh giới API và mất context.
11. **“getUndeclaredThrowable luôn khác null.”** Không tuyệt đối với UTE được tạo thủ công.
12. **“Nếu wrapped exception là IOException thì nguyên nhân chắc chắn là lỗi file.”** Không: IOException còn dùng cho nhiều thao tác I/O khác, cần đọc ngữ cảnh.
13. **“Proxy có hai interface trùng method thì checked throws là hợp các loại được khai báo.”** Sai: phải phù hợp với tất cả hợp đồng method liên quan.
14. **“Method.getExceptionTypes() của đối tượng Method handler nhận luôn đủ toàn bộ ràng buộc.”** Sai trong trường hợp duplicate methods.
15. **“Gọi method.invoke(proxy,args) trong handler là cách delegate an toàn.”** Có thể gây proxy gọi lại handler và đệ quy.
16. **“equals/hashCode/toString không liên quan handler.”** Sai với JDK dynamic proxy.
17. **“Mọi thư viện AOP đều sử dụng Proxy của JDK.”** Sai: có nhiều cơ chế proxy.
18. **“Tạo runtime wrapper tự động giải quyết retry/transaction.”** Sai: wrapper chỉ thay đổi cách biểu diễn/truyền lỗi, không tự phục hồi logic.
19. **“Chỉ log UTE.getMessage() là đủ.”** Thường không: cần cause và stack trace.
20. **“Thêm throws ở implementation proxy có thể giải quyết mọi trường hợp.”** Với JDK-generated proxy, hợp đồng public interface và các duplicate methods mới là yếu tố then chốt.

## 18. Bài tập tự kiểm tra

**Câu 1.** Interface có `void run();`, handler ném `IOException`. Caller sẽ thường nhận kiểu gì?

*Đáp án:* `UndeclaredThrowableException` bọc IOException, vì checked IOException không được interface method khai báo.

**Câu 2.** Đổi interface thành `void run() throws IOException;` và handler vẫn ném IOException. Kết quả?

*Đáp án:* IOException có thể được truyền trực tiếp, không cần UTE.

**Câu 3.** Handler ném `IllegalArgumentException` dù method interface không có throws. Proxy xử lý thế nào?

*Đáp án:* Đây là RuntimeException unchecked nên đi ra trực tiếp.

**Câu 4.** Handler ném `AssertionError`, proxy có tạo UTE không?

*Đáp án:* Không, Error thuộc nhóm unchecked nên truyền trực tiếp.

**Câu 5.** Interface có `throws IOException`, nhưng handler thực hiện `method.invoke(target, args)` và không catch InvocationTargetException. Target ném IOException. Caller có thể nhận gì?

*Đáp án:* UTE bọc InvocationTargetException, bên trong là IOException của target.

**Câu 6.** Sửa handler như thế nào để truyền lại IOException gốc đúng hợp đồng?

*Đáp án:* Catch InvocationTargetException và `throw ex.getCause()`. Nếu cause là IOException phù hợp throws, proxy truyền trực tiếp.

**Câu 7.** UTE có hai getter nào giúp xem wrapped exception?

*Đáp án:* `getUndeclaredThrowable()` và `getCause()`.

**Câu 8.** Nếu wrapped là InvocationTargetException, cần xem tiếp API nào?

*Đáp án:* `getTargetException()` hoặc `getCause()` để thấy Throwable do target thực sự ném.

**Câu 9.** Vì sao handler được viết `throw new SQLException(...)` dù interface không khai báo SQLException?

*Đáp án:* Bởi `InvocationHandler.invoke()` khai báo `throws Throwable`, nhưng proxy vẫn kiểm tra hợp đồng interface tại runtime và có thể bọc nó trong UTE.

**Câu 10.** Hai interface cùng method `execute()`, một khai báo IOException, một khai báo SQLException. Handler ném IOException. Có được truyền trực tiếp qua mọi caller không?

*Đáp án:* Không. Vì IOException không tương thích với throws clause của interface kia, proxy có thể tạo UTE; ví dụ Java 17 ở trên cho kết quả UTE ở cả hai cách gọi.

**Câu 11.** Có nên dùng UTE làm custom exception nghiệp vụ cho mọi API không?

*Đáp án:* Không. Đây chủ yếu là wrapper phản ánh mismatch tại JDK dynamic proxy. Nếu API cần phản ứng nghiệp vụ rõ, dùng hợp đồng exception riêng có ý nghĩa hơn.

**Câu 12.** Bắt UTE rồi chỉ in message và return success có an toàn không?

*Đáp án:* Không. Lỗi gốc còn tồn tại; bỏ qua có thể làm chương trình trả kết quả giả và che bug.

**Câu 13.** `Method.invoke(proxy,args)` ngay trên chính proxy trong handler có nguy cơ gì?

*Đáp án:* Nó có thể đi vào cùng handler trở lại và gây đệ quy không kiểm soát.

**Câu 14.** Vì sao `method.getDeclaringClass()` không luôn phản ánh toàn bộ hợp đồng checked exception khi proxy có nhiều interface?

*Đáp án:* Với method trùng chữ ký, handler có thể nhận Method từ một interface đã chọn, nhưng quy tắc throws vẫn chịu ràng buộc của các interface có method tương ứng.

## 19. Checklist thiết kế InvocationHandler

Trước khi đưa một JDK dynamic proxy vào production:

- **Interface contract:** đã công bố checked exceptions đúng nhu cầu của caller chưa?
- **Handler throwing policy:** những checked Throwable nào có thể thoát ra từ handler?
- **Reflection delegation:** có unwrap `InvocationTargetException` khi gọi target qua `Method.invoke` không?
- **Cause preservation:** có giữ lỗi gốc khi dịch thành runtime/domain exception không?
- **Multiple interfaces:** có duplicate methods với throws khác nhau không?
- **Object methods:** đã có chính sách hợp lý cho `equals`, `hashCode`, `toString` chưa?
- **Return values:** kiểu `Object` mà handler trả có tương thích kiểu return của method, kể cả primitive không?
- **Access/arguments:** đã phân biệt lỗi cấu hình reflection với lỗi nghiệp vụ do target ném chưa?
- **Logging:** có giữ đầy đủ chain UTE → InvocationTargetException → lỗi gốc, cùng bối cảnh và dữ liệu không nhạy cảm không?
- **Tests:** đã test checked declared, checked undeclared, unchecked, Error, reflection wrapper và duplicate interfaces nếu phù hợp chưa?
- **Caller policy:** có đang bắt/nuốt UTE rồi báo thành công giả không?
- **Ownership:** nên sửa hợp đồng interface/handler hay cần một adapter ở boundary do thư viện bên ngoài không thể chỉnh sửa?
- **Maintenance:** nếu nâng Java hoặc đổi cơ chế proxy/framework, đã test lại contract exception chưa?

## 20. Tài liệu Java 17 chính thức

- [Java SE 17 API – UndeclaredThrowableException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/UndeclaredThrowableException.html): điều kiện proxy tạo wrapper, getter và cơ chế cause.
- [Java SE 17 API – InvocationHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationHandler.html): `invoke(Object,Method,Object[]) throws Throwable` và quy tắc unchecked/checked.
- [Java SE 17 API – Proxy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Proxy.html): tạo proxy interface, duplicate methods, checked exception constraints.
- [Java SE 17 API – InvocationTargetException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationTargetException.html): wrapper checked từ reflection và cách truy xuất target exception.
- [Java SE 17 API – Method](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html): `Method.invoke` và ngoại lệ phát sinh.
- [Java SE 17 API – RuntimeException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/RuntimeException.html): nhánh unchecked trong phân cấp Throwable.
- [Java SE 17 API – Throwable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): `getCause()`, message, stack trace, suppressed exceptions.
- [JLS Java SE 17 – chương 11, Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html): checked/unchecked exceptions và phân tích compiler.
- [JLS Java SE 17 – mục 8.4.8.3](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.8.3): giới hạn checked throws khi override thông thường.

**Kết luận:** `UndeclaredThrowableException` xuất hiện trong JDK dynamic proxy khi `InvocationHandler.invoke` làm thoát ra **checked exception không tương thích với `throws` của method interface**. RuntimeException và Error không bị wrapper theo cơ chế này; checked exception được khai báo hợp lệ cũng đi ra trực tiếp. Khi delegate bằng reflection, **hãy unwrap `InvocationTargetException`** để giữ exception gốc và hợp đồng caller, đặc biệt nếu target đã ném IOException được interface cho phép. Luôn xem cả cause, xét duplicate interface methods và đừng dùng việc catch UTE để thay cho thiết kế hợp đồng API đúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="api-exceptions-011">UncheckedIOException khi API không khai báo checked IOException</a>

<details>
<summary>Xem chi tiết</summary>

# UncheckedIOException khi API không khai báo checked IOException

Trong Java, `java.io.IOException` là **checked exception**: nếu một lời gọi có thể ném nó, phương thức bao quanh phải bắt hoặc khai báo `throws` phù hợp. Tuy nhiên, nhiều callback quan trọng của Java như `Function.apply`, `Consumer.accept`, `Supplier.get`, `Predicate.test` và `Runnable.run` **không khai báo checked exception**. Điều gì xảy ra khi logic đặt bên trong callback vẫn phải đọc file, ghi dữ liệu hay làm I/O?

`java.io.UncheckedIOException` cung cấp một cách **bọc IOException thành unchecked exception** có ý nghĩa rõ ràng. Nó kế thừa `RuntimeException`, giữ `IOException` gốc làm **cause** và có thể lan truyền qua những API không cho checked `throws`. Bên xử lý cuối vẫn đọc được nguyên nhân thật thông qua `getCause()`.

**Quy tắc cần nhớ:** dùng `UncheckedIOException` khi **thao tác thực sự thất bại vì IOException**, nhưng **ranh giới callback/API không cho phép truyền trực tiếp checked IOException**, và **chính sách hợp đồng cho phép chuyển thất bại đó thành unchecked**. Đây không phải mẹo bỏ qua lỗi: chương trình vẫn thất bại nếu không được xử lý, việc chuyển đổi phải có chủ đích, và gọi lại thao tác chưa chắc an toàn.

Bài này giải thích phân cấp, cách dùng constructor, các ví dụ Java 17 có thể chạy được, lambda và method reference, Stream API, `Files.lines`/`BufferedReader.lines`, adapter tái sử dụng, catch và unwrap, tài nguyên, exception propagation, rủi ro retry, kiểm thử, và những trường hợp không nên dùng wrapper.

## 1. `UncheckedIOException` nằm ở đâu trong hệ phân cấp?

Sơ đồ rút gọn:

```text
java.lang.Throwable
├── java.lang.Error
└── java.lang.Exception
    ├── java.io.IOException                (checked)
    │   ├── java.io.FileNotFoundException
    │   ├── java.nio.file.FileSystemException
    │   └── ...
    └── java.lang.RuntimeException         (unchecked)
        ├── java.io.UncheckedIOException
        ├── java.lang.IllegalArgumentException
        ├── java.lang.IllegalStateException
        └── ...
```

Những điểm quyết định:

- `IOException` và các subclass thông thường của nó là **checked**.
- `UncheckedIOException extends RuntimeException` nên **unchecked**.
- `UncheckedIOException` **không kế thừa `IOException`**. Một `catch (IOException e)` không bắt trực tiếp object `UncheckedIOException`.
- `UncheckedIOException` **chứa một `IOException`** làm nguyên nhân (`cause`). Đây là quan hệ **chứa/chuỗi nguyên nhân**, không phải quan hệ subtype.
- Constructor của `UncheckedIOException` yêu cầu nguyên nhân là một `IOException`; không phải mọi `Exception` đều có thể trực tiếp làm cause trong constructor chuyên biệt ấy.
- `UncheckedIOException` xuất hiện trong Java từ Java 8, phù hợp với giai đoạn sử dụng nhiều Stream API/lambda.

Ví dụ kiểu:

```java
IOException original = new IOException("Không đọc được tệp");
UncheckedIOException wrapped = new UncheckedIOException(
        "Lỗi I/O trong callback", original);

System.out.println(wrapped instanceof RuntimeException);
System.out.println(wrapped.getCause() == original);
```

Đây là **đoạn minh họa** cần import `java.io.IOException` và `java.io.UncheckedIOException`, có thể đặt trong một phương thức. Hai phép kiểm tra in `true`. Ngược lại, không được viết `wrapped instanceof IOException`: ở đây hai lớp không có quan hệ kiểu cho phép kiểm tra đó và compiler Java có thể từ chối phép kiểm tra `instanceof` bất khả thi giữa các kiểu tĩnh không liên quan.

## 2. Checked exception không tương thích với mọi functional interface

Java sử dụng **single abstract method** (SAM) của functional interface để kiểm tra một lambda hay method reference.

Một số SAM thông dụng:

| Functional interface | SAM | Checked `throws` được công bố? |
| --- | --- | --- |
| `Runnable` | `void run()` | Không |
| `Supplier<T>` | `T get()` | Không |
| `Consumer<T>` | `void accept(T)` | Không |
| `Function<T,R>` | `R apply(T)` | Không |
| `Predicate<T>` | `boolean test(T)` | Không |
| `BiFunction<T,U,R>` | `R apply(T,U)` | Không |
| `Callable<V>` | `V call() throws Exception` | Có |
| Custom `IoFunction<T,R>` | `R apply(T) throws IOException` | Có nếu bạn định nghĩa |

Ví dụ method đọc dữ liệu:

```java
static String read(String path) throws IOException {
    return Files.readString(Path.of(path));
}
```

Đây là **method minh họa**, cần import `IOException`, `Files` và `Path`. Nếu cố gán:

```java
Function<String, String> fn = Example::read;
```

thì **không biên dịch** (giả định method thuộc lớp `Example`), vì `Function.apply()` không khai báo `throws IOException`.

Dùng method reference `::` không làm exception tự biến thành unchecked. Tương tự:

```java
Function<String, String> fn = x -> Files.readString(Path.of(x));
```

là **phản ví dụ không biên dịch**: thân lambda có thể ném checked `IOException`. Việc khai báo `throws IOException` ở phương thức bao ngoài **không sửa được** hợp đồng `Function.apply`, vì lỗi nằm ở biểu thức dùng để tạo callback.

## 3. Vì sao không thể “thêm throws” cho `Function.apply`?

Interface chuẩn có chữ ký `R apply(T t)`, không phải `R apply(T t) throws IOException`. Khi triển khai hoặc sử dụng biểu thức lambda theo `Function`, implementation không được khai báo checked exception rộng hơn hợp đồng của SAM.

Ví dụ **cố ý không hợp lệ**:

```java
class FileMapper implements Function<String, String> {
    @Override
    public String apply(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
```

Đây là **đoạn phản ví dụ** cần import tương ứng. Compiler báo lỗi vì method override thêm checked `IOException` không có trong `Function.apply`.

Có bốn hướng xử lý chính:

1. **Không dùng `Function`** khi kiểm soát API: định nghĩa custom SAM có `throws IOException` và truyền checked exception một cách tường minh.
2. **Bắt và phục hồi thật sự** bên trong callback: chọn fallback phù hợp hoặc trả một result type.
3. **Bắt `IOException` rồi bọc trong `UncheckedIOException`** nếu muốn exception vẫn lan ra nhưng qua một hợp đồng unchecked.
4. **Thay đổi cấu trúc**: đưa thao tác I/O ra ngoài callback/pipeline, nơi có thể khai báo `throws IOException`.

`UncheckedIOException` tương ứng với hướng 3, không thay thế các hướng còn lại.

## 4. Hai constructor thông dụng

Hai constructor được sử dụng:

```java
new UncheckedIOException(ioException);

new UncheckedIOException("Mô tả ngữ cảnh", ioException);
```

Đây là **các biểu thức minh họa**; biến `ioException` có kiểu `IOException`. Constructor có message rất hữu ích để bổ sung ngữ cảnh như “không đọc được tập cấu hình” hoặc “thất bại khi biến đổi phần tử trong stream”.

Ví dụ:

```java
try {
    saveReport();
} catch (IOException e) {
    throw new UncheckedIOException(
            "Ghi báo cáo thất bại", e);
}
```

Đây là **đoạn minh họa** với `saveReport() throws IOException` được định nghĩa ở nơi khác. Kết quả:

- Exception ngoài là `UncheckedIOException`.
- `getMessage()` chứa mô tả do bạn cung cấp.
- `getCause()` trả về object `IOException` gốc.
- Cause giữ stack trace và thông tin của lỗi thấp hơn.
- Caller không bị compiler buộc catch/declare `UncheckedIOException` nhưng chương trình vẫn có thể bị ngắt khi callback thực thi.

**Constructor yêu cầu cause khác null.** Đừng tạo wrapper với cause giả hoặc `null` để biểu diễn một tình trạng “không có file” không liên quan lỗi I/O thực tế.

## 5. Ví dụ Java 17 độc lập: bọc và bắt đúng loại

Lưu **`UncheckedIOExceptionBasicsDemo.java`**:

```java
import java.io.IOException;
import java.io.UncheckedIOException;

public class UncheckedIOExceptionBasicsDemo {
    static void load() {
        try {
            simulateRead();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Không tải được cấu hình", e);
        }
    }

    static void simulateRead() throws IOException {
        throw new IOException("Thiết bị đọc thất bại");
    }

    public static void main(String[] args) {
        try {
            load();
        } catch (UncheckedIOException e) {
            System.out.println("Wrapper: "
                    + e.getClass().getSimpleName());
            System.out.println("Message: " + e.getMessage());
            System.out.println("Cause: "
                    + e.getCause().getClass().getSimpleName());
            System.out.println("Cause message: "
                    + e.getCause().getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 UncheckedIOExceptionBasicsDemo.java
java UncheckedIOExceptionBasicsDemo
```

**Stdout chính xác:**

```text
Wrapper: UncheckedIOException
Message: Không tải được cấu hình
Cause: IOException
Cause message: Thiết bị đọc thất bại
```

Chương trình **chủ động mô phỏng lỗi bằng `IOException`**, không phụ thuộc ổ đĩa hay mạng thực. Điều này thích hợp cho unit test hành vi wrapping.

Lưu ý lỗi được chuyển loại **ở ranh giới `load()`**. Nếu API bên ngoài có thể cho `throws IOException` thì việc bọc chưa chắc đem lại giá trị; phải cân nhắc trách nhiệm caller.

## 6. `getCause()` có kiểu trả về đặc biệt

Trong `Throwable`, `getCause()` trả `Throwable`. Riêng `UncheckedIOException` override `getCause()` theo kiểu trả về hẹp hơn: **`IOException`**.

Vì vậy có thể viết:

```java
UncheckedIOException failure =
        new UncheckedIOException(new IOException("I/O"));

IOException original = failure.getCause();
System.out.println(original.getClass().getSimpleName());
```

Đây là **mã minh họa** cần import hai lớp. Nó in `IOException` và không cần ép kiểu thủ công.

Điều này khác `new RuntimeException(ioException)`:

```java
RuntimeException generic = new RuntimeException(
        new IOException("I/O"));
// IOException original = generic.getCause(); // Không biên dịch.
// getCause() ở đây có kiểu tĩnh Throwable.
```

Không nên hiểu rằng `UncheckedIOException` làm lỗi thấp hơn biến mất. Đây là wrapper **chuyên biệt cho I/O**: loại của cause và ý nghĩa nguyên nhân được giữ.

## 7. Khi nào nên sử dụng `UncheckedIOException`?

Nó thích hợp khi ba điều kiện đồng thời có ý nghĩa:

1. **Có `IOException` thật** từ một thao tác I/O hoặc API có hợp đồng I/O.
2. **Ranh giới đang dùng không cho checked `throws IOException`**; ví dụ `Function` trong `Stream.map`, `Consumer` trong `forEach`, `Runnable` trong executor.
3. **Chính sách cho phép thất bại lan ra theo unchecked exception** để tầng có trách nhiệm xử lý, báo lỗi hoặc hủy thao tác.

Một số ví dụ:

- Ánh xạ danh sách `Path` sang nội dung file bằng `Stream.map`.
- Đọc dữ liệu trong `forEach` của một API chỉ nhận `Consumer`.
- Một adapter để truyền function có I/O sang `Function<T,R>`.
- Thực thi `Runnable` cần I/O nhưng framework không có checked `throws`.
- Một stream đọc dữ liệu lười (*lazy*) mà tài liệu API quy định exception đọc phát sinh lúc terminal operation được chuyển thành `UncheckedIOException`.

**Không nên bọc đơn thuần để “làm compiler hết báo”**. Nếu một library công khai yêu cầu caller chọn cách phục hồi I/O, giữ checked `IOException` hoặc thiết kế callback có `throws` vẫn có thể đúng hơn.

## 8. `Stream.map`: vì sao lỗi compile xuất hiện?

`Stream.map` nhận `Function<? super T, ? extends R>`. SAM `Function.apply` không khai báo checked exception, vì vậy biểu thức sau **cố ý không biên dịch**:

```java
Stream<Path> paths = Stream.of(Path.of("a.txt"));
Stream<String> contents = paths.map(Files::readString);
```

Giả định import `java.util.stream.Stream` và `java.nio.file.Files, Path`. Phương thức `Files.readString(Path)` có `throws IOException`; method reference không tương thích `Function<Path,String>`.

Đoạn lambda tương đương:

```java
Stream<String> contents = paths.map(
        path -> Files.readString(path));
```

cũng không biên dịch với cùng lý do.

**Một `try/catch` bao lấy dòng tạo pipeline không tự sửa vấn đề**, vì checked exception thuộc phần triển khai của SAM. Bạn phải thay đổi **bên trong biểu thức lambda**, hoặc thay target interface/cấu trúc.

Đối với stream, cũng cần nhớ nhiều thao tác là **lazy**: tạo pipeline không nhất thiết gọi mapper ngay, nên exception wrapper có thể chỉ xuất hiện khi terminal operation tiêu thụ phần tử.

## 9. Ví dụ chạy được: chuyển `IOException` sang `UncheckedIOException` trong `map`

Lưu **`StreamUncheckedIOExceptionDemo.java`**:

```java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

public class StreamUncheckedIOExceptionDemo {
    static String read(String key) throws IOException {
        if ("missing".equals(key)) {
            throw new IOException("Không tìm thấy dữ liệu: " + key);
        }
        return "Nội dung " + key;
    }

    public static void main(String[] args) {
        List<String> keys = List.of("one", "missing", "three");

        try {
            List<String> results = keys.stream()
                    .map(key -> {
                        try {
                            return read(key);
                        } catch (IOException e) {
                            throw new UncheckedIOException(
                                    "Đọc phần tử thất bại", e);
                        }
                    })
                    .toList();

            System.out.println("Kết quả: " + results);
        } catch (UncheckedIOException e) {
            System.out.println("Wrapper: " + e.getMessage());
            System.out.println("Cause: " + e.getCause().getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 StreamUncheckedIOExceptionDemo.java
java StreamUncheckedIOExceptionDemo
```

**Stdout chính xác:**

```text
Wrapper: Đọc phần tử thất bại
Cause: Không tìm thấy dữ liệu: missing
```

Các điểm cần hiểu:

1. `read(key) throws IOException` là method mô phỏng thao tác I/O.
2. `Stream.map` không cho checked exception của mapper.
3. Lambda **bắt tại chỗ** và ném `UncheckedIOException`.
4. `toList()` tiêu thụ stream; khi gặp `"missing"`, mapper phát sinh exception.
5. Vì terminal operation không hoàn tất, biến `results` không nhận một danh sách kết quả cuối cùng và dòng in `Kết quả` không chạy.
6. Handler bên ngoài bắt exception đã chuyển loại, lấy cause để chẩn đoán.

**Không đồng nghĩa mọi phần tử sau `"missing"` đã được xử lý**. Với stream tuần tự ví dụ này, phép tính dừng ở lỗi; với parallel stream, điều kiện lỗi và side effect phức tạp hơn, không nên giả định thứ tự fail-fast hoặc rollback tự động.

## 10. Điều quan trọng: stream lazy và thời điểm phát sinh lỗi

Một stream pipeline có thể được tạo mà chưa đọc file hay gọi mapper. Chỉ khi terminal operation như `toList()`, `count()`, `forEach()`, `reduce()` hoặc một thao tác tiêu thụ khác chạy, callback mới có thể được gọi.

Ví dụ Java 17 **`StreamLazyIoDemo.java`**:

```java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Stream;

public class StreamLazyIoDemo {
    static String read(String name) throws IOException {
        System.out.println("Đang đọc: " + name);
        if ("bad".equals(name)) {
            throw new IOException("Không đọc được bad");
        }
        return name.toUpperCase();
    }

    public static void main(String[] args) {
        Stream<String> pipeline = Stream.of("ok", "bad")
                .map(name -> {
                    try {
                        return read(name);
                    } catch (IOException e) {
                        throw new UncheckedIOException(
                                "Mapper gặp lỗi", e);
                    }
                });

        System.out.println("Đã tạo pipeline");

        try {
            pipeline.forEach(System.out::println);
        } catch (UncheckedIOException e) {
            System.out.println("Bắt: " + e.getCause().getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 StreamLazyIoDemo.java
java StreamLazyIoDemo
```

**Stdout chính xác:**

```text
Đã tạo pipeline
Đang đọc: ok
OK
Đang đọc: bad
Bắt: Không đọc được bad
```

`pipeline` được tạo trước, nhưng phần đọc bắt đầu khi `forEach` là terminal operation chạy. Khi I/O thất bại, exception bọc được ném ra ngoài `forEach`.

**Không được suy rằng mọi terminal operation đều thực thi mọi mapper**. Ví dụ một số tối ưu hóa stream hoặc short-circuit operation có thể tránh chạy những stage nhất định. Đối với code I/O có side effect, lựa chọn Stream API cần tính tới tính lazy, tối ưu hóa và chính sách đóng tài nguyên.

## 11. `Consumer.accept` và `forEach` cũng không khai báo checked exception

`Consumer<T>` nhận một giá trị và không trả kết quả; `accept(T)` không khai báo checked exception. Vì vậy:

```java
items.forEach(item -> writeToDisk(item));
```

là **phản ví dụ cố ý không biên dịch** nếu `writeToDisk` khai báo `throws IOException` (và `items` có kiểu phù hợp).

Cách chuyển loại có chủ đích:

```java
items.forEach(item -> {
    try {
        writeToDisk(item);
    } catch (IOException e) {
        throw new UncheckedIOException(
                "Ghi phần tử thất bại", e);
    }
});
```

Đây là **mã minh họa** cần biến `items`, method `writeToDisk` và import các lớp. Bất kỳ lỗi nào tại callback có thể thoát thành unchecked exception; `forEach` không tự catch hoặc tự retry.

**Nguy cơ lớn hơn với ghi dữ liệu:** một số phần tử trước đó có thể đã được ghi thành công. Khi gặp lỗi giữa vòng lặp, không có rollback tự động. Nếu việc ghi nhiều mục phải nguyên tử, cần cơ chế transaction, staging hoặc compensating action theo hệ thống đích.

## 12. Khi dùng `Runnable` hoặc job executor

`Runnable.run()` không khai báo checked exception. Nếu công việc phải gọi API ném `IOException`, bạn có thể bắt và bọc:

```java
Runnable job = () -> {
    try {
        doIoWork();
    } catch (IOException e) {
        throw new UncheckedIOException(
                "Job thất bại vì I/O", e);
    }
};
```

Đây là **mã minh họa**, `doIoWork() throws IOException` phải được định nghĩa.

Sự khác biệt:

- `job.run()` trực tiếp có thể ném `UncheckedIOException` cho caller.
- Nếu `job` được thực thi bởi `ExecutorService.submit`, lỗi có thể được quan sát qua `Future.get()` dưới dạng `ExecutionException` theo hợp đồng executor.
- Với `CompletableFuture`, lỗi của task có thể được bọc bởi `CompletionException` khi `join()` hoặc API tương ứng truyền thất bại.
- Các wrapper do framework tạo là **tầng khác** với `UncheckedIOException` mà code của bạn ném. Muốn phân loại nguyên nhân sâu hơn, có thể phải xem cause chain.

**Không nhầm** “unchecked” với “đã được thread pool xử lý thành công”. Exception có thể nằm trong kết quả Future và không tự động được in ra console theo cách bạn tưởng.

## 13. `Callable` có thể là lựa chọn giữ checked exception

`Callable<V>.call() throws Exception` **cho phép checked exception**. Nếu bạn kiểm soát interface callback cần dùng, không nhất thiết phải bọc.

Ví dụ Java 17 **`CallableCheckedDemo.java`**:

```java
import java.io.IOException;
import java.util.concurrent.Callable;

public class CallableCheckedDemo {
    static String read() throws IOException {
        throw new IOException("Nguồn chưa sẵn sàng");
    }

    public static void main(String[] args) {
        Callable<String> task = CallableCheckedDemo::read;

        try {
            task.call();
        } catch (Exception e) {
            System.out.println("Loại: "
                    + e.getClass().getSimpleName());
            System.out.println("Message: " + e.getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 CallableCheckedDemo.java
java CallableCheckedDemo
```

**Stdout chính xác:**

```text
Loại: IOException
Message: Nguồn chưa sẵn sàng
```

Phương thức `read() throws IOException` tương thích với `Callable.call() throws Exception`. Ở điểm gọi qua biến `Callable`, compiler buộc caller catch/declare `Exception` theo chữ ký rộng của SAM.

**Đừng đổi sang `UncheckedIOException` chỉ vì đã quen cách viết adapter**, nếu hợp đồng hiện tại đã cho checked I/O và caller có trách nhiệm xử lý nó.

## 14. Thiết kế functional interface riêng có `throws IOException`

Nếu tạo API của mình, có thể mô tả khả năng I/O trực tiếp:

```java
@FunctionalInterface
interface IoFunction<T, R> {
    R apply(T input) throws IOException;
}
```

Đây là **interface minh họa**, cần import `java.io.IOException`. Method reference `Files::readString` có thể thích hợp với `IoFunction<Path,String>` do tham số, kiểu trả về và checked exception khớp.

Ví dụ độc lập **`IoFunctionCheckedDemo.java`**:

```java
import java.io.IOException;

public class IoFunctionCheckedDemo {
    @FunctionalInterface
    interface IoFunction<T, R> {
        R apply(T input) throws IOException;
    }

    static String read(String name) throws IOException {
        if (name.isBlank()) {
            throw new IOException("Tên nguồn rỗng");
        }
        return "DATA:" + name;
    }

    static <T, R> R execute(IoFunction<T, R> task, T input)
            throws IOException {
        return task.apply(input);
    }

    public static void main(String[] args) {
        IoFunction<String, String> task = IoFunctionCheckedDemo::read;

        try {
            System.out.println(execute(task, "orders"));
            System.out.println(execute(task, ""));
        } catch (IOException e) {
            System.out.println("Đã bắt IOException: "
                    + e.getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 IoFunctionCheckedDemo.java
java IoFunctionCheckedDemo
```

**Stdout chính xác:**

```text
DATA:orders
Đã bắt IOException: Tên nguồn rỗng
```

Ở đây không cần `UncheckedIOException`, vì **toàn bộ chuỗi callback và phương thức gọi** cho phép checked `IOException`.

Thiết kế này hữu ích khi người dùng API thực sự cần phân biệt I/O failure theo compile-time contract. Ngược lại, các framework chuẩn nhận `Function` thì không thể tự sửa chữ ký `Function.apply`, lúc đó adapter là một lựa chọn.

## 15. Adapter chuyển `IoFunction` thành `Function`

Để tránh lặp `try/catch` trong mọi lambda, có thể tạo một adapter tái sử dụng. Trọng tâm của adapter là **thay đổi hợp đồng lỗi tại một điểm rõ ràng**.

Lưu **`IoFunctionAdapterDemo.java`**:

```java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class IoFunctionAdapterDemo {
    @FunctionalInterface
    interface IoFunction<T, R> {
        R apply(T input) throws IOException;
    }

    static <T, R> Function<T, R> unchecked(
            IoFunction<T, R> function) {
        Objects.requireNonNull(function, "function");
        return input -> {
            try {
                return function.apply(input);
            } catch (IOException e) {
                throw new UncheckedIOException(
                        "I/O trong Function.apply", e);
            }
        };
    }

    static String read(String key) throws IOException {
        if ("bad".equals(key)) {
            throw new IOException("Không đọc được bad");
        }
        return "OK-" + key;
    }

    public static void main(String[] args) {
        Function<String, String> mapper =
                unchecked(IoFunctionAdapterDemo::read);

        System.out.println(mapper.apply("good"));

        try {
            List<String> values = List.of("a", "bad", "c");
            System.out.println(values.stream()
                    .map(mapper)
                    .toList());
        } catch (UncheckedIOException e) {
            System.out.println("Wrapper: " + e.getMessage());
            System.out.println("Cause: " + e.getCause().getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 IoFunctionAdapterDemo.java
java IoFunctionAdapterDemo
```

**Stdout chính xác:**

```text
OK-good
Wrapper: I/O trong Function.apply
Cause: Không đọc được bad
```

Những ưu điểm:

1. Method reference `IoFunctionAdapterDemo::read` được target type `IoFunction` chấp nhận vì SAM có `throws IOException`.
2. Adapter trả `Function` dùng được trong `Stream.map`.
3. I/O failure vẫn đi ra dưới dạng `UncheckedIOException`, giữ cause.
4. Mọi nơi gọi adapter có **một chính sách chuyển lỗi nhất quán**.
5. `Objects.requireNonNull` làm rõ tiền điều kiện của adapter.

Nhược điểm: chữ ký `Function` đã bỏ ràng buộc checked exception; caller cần đọc tài liệu hợp đồng adapter và biết nơi nhận lỗi nếu pipeline thất bại.

## 16. Adapter cho `Consumer` và `Runnable`

Tương tự `IoFunction`, có thể xây các interface checked:

```java
@FunctionalInterface
interface IoConsumer<T> {
    void accept(T value) throws IOException;
}

@FunctionalInterface
interface IoRunnable {
    void run() throws IOException;
}
```

Đây là **hai interface minh họa** cần import `IOException`. Những adapter đơn giản:

```java
static <T> Consumer<T> uncheckedConsumer(IoConsumer<T> io) {
    return value -> {
        try {
            io.accept(value);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Consumer I/O thất bại", e);
        }
    };
}

static Runnable uncheckedRunnable(IoRunnable io) {
    return () -> {
        try {
            io.run();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Runnable I/O thất bại", e);
        }
    };
}
```

Đây là **các method minh họa**, giả định hai interface trên nằm trong phạm vi, đã import `Consumer`, `IOException` và `UncheckedIOException`.

Trước khi đưa adapter vào thư viện chung, nên xác định:

- Có thật cần cả ba kiểu `IoFunction`, `IoConsumer`, `IoRunnable` không?
- Khi I/O thất bại, handler cuối nằm ở đâu?
- Có cần message gắn tên tác vụ hoặc ID phần tử không?
- Có khả năng một tác vụ đã ghi **một phần** rồi lỗi không?
- API bên nhận `Consumer`/`Runnable` có chính sách riêng cho unchecked exception không?

Không tạo adapter “nuốt IOException và tiếp tục” nếu hợp đồng yêu cầu báo thất bại.

## 17. `Files.readString` vẫn khai báo checked `IOException`

Một lỗi hiểu biết thường gặp: vì đã có `UncheckedIOException` nên mọi API đọc file của JDK sẽ tự ném unchecked. **Không đúng.**

Ví dụ `Files.readString(Path)` khai báo `throws IOException`. Khi gọi trực tiếp:

```java
String text = Files.readString(Path.of("config.txt"));
```

đây là **mã minh họa**, method chứa cần catch/declare `IOException`. Java **không tự bọc thành `UncheckedIOException`** tại đây.

Nếu đưa nó vào `Stream.map`, bạn có thể chọn bọc trong lambda:

```java
Function<Path, String> readText = path -> {
    try {
        return Files.readString(path);
    } catch (IOException e) {
        throw new UncheckedIOException(
                "Không đọc được: " + path.getFileName(), e);
    }
};
```

Đây là **đoạn minh họa**, cần import `Path`, `Files`, `Function`, `IOException` và `UncheckedIOException`.

Nguyên tắc: **hãy đọc JavaDoc của method cụ thể**. Một số JDK API tự bọc lỗi I/O ở giai đoạn lazy processing, trong khi các API khác tiếp tục dùng checked `IOException` trực tiếp.

## 18. `Files.lines`: lỗi khi mở stream và lỗi khi tiêu thụ stream

`Files.lines(Path)` tạo một `Stream<String>` đọc các dòng của file. Hợp đồng lỗi có hai thời điểm cần phân biệt:

1. **Mở nguồn / tạo stream:** lời gọi `Files.lines(path)` có thể ném **checked `IOException`**. Caller phải catch/declare.
2. **Đọc thêm dòng trong quá trình tiêu thụ stream:** nếu xảy ra lỗi I/O trong quá trình xử lý lười, hành vi của API có thể là ném **`UncheckedIOException`**.

Ví dụ cấu trúc:

```java
try (Stream<String> lines = Files.lines(path)) {
    lines.forEach(System.out::println);
}
```

Đây là **mã minh họa** với `path` được định nghĩa, import `Stream` và `Files`; method bao ngoài phải xử lý/khai báo `IOException` từ bước mở.

**Điểm quan trọng:** `try-with-resources` đóng stream, giải phóng file handle. Việc stream bị đóng không tự bảo đảm lỗi đọc sẽ được xử lý; cần catch ở ranh giới thích hợp. Với pipeline có tác dụng phụ, những dòng trước khi lỗi phát sinh có thể đã được xử lý.

Nếu bạn chỉ catch `UncheckedIOException` mà bỏ qua checked `IOException` khi mở file, code có thể không biên dịch. Ngược lại, chỉ catch `IOException` quanh một pipeline không đủ để bắt wrapper unchecked từ giai đoạn đọc lười.

## 19. Ví dụ Java 17: đọc file theo dòng và đóng stream

Ví dụ này chạy được bằng một file tạm do chính chương trình tạo, **không phụ thuộc đường dẫn file có sẵn**. Nó minh họa thành công, không cố ép lỗi I/O không xác định.

Lưu **`FilesLinesDemo.java`**:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class FilesLinesDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("line-demo-", ".txt");

        try {
            Files.writeString(file, "alpha\nbeta\n",
                    StandardCharsets.UTF_8);

            try (Stream<String> lines =
                         Files.lines(file, StandardCharsets.UTF_8)) {
                lines.forEach(line ->
                        System.out.println("Dòng: " + line));
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 FilesLinesDemo.java
java FilesLinesDemo
```

**Stdout chính xác khi filesystem cho phép tạo/đọc/xóa file tạm:**

```text
Dòng: alpha
Dòng: beta
```

Tại đây không có `UncheckedIOException` vì luồng đọc thành công. Điều quan trọng là **chữ ký `main throws IOException`** bao phủ các thao tác checked như tạo, ghi, mở và xóa file.

Ví dụ tạo/xóa file tạm trên máy học viên. Nếu sandbox cấm ghi filesystem, chương trình có thể ném `IOException` trước khi in stdout; đây là điều kiện môi trường, không phải hành vi exception wrapper.

## 20. `BufferedReader.lines()` và `UncheckedIOException`

`BufferedReader.lines()` trả về một stream các dòng. Vì `Stream` không có checked `throws IOException` trong những operation thông thường, **lỗi I/O xảy ra trong quá trình đọc các dòng bằng stream** được báo theo hợp đồng của `BufferedReader.lines()` bằng **`UncheckedIOException`**.

Ví dụ ý tưởng:

```java
try (BufferedReader reader = Files.newBufferedReader(path)) {
    reader.lines().forEach(System.out::println);
}
```

Đây là **mã minh họa** với `path`, các import cần thiết và method bao ngoài xử lý checked `IOException` khi mở/đóng reader.

Chú ý:

- `Files.newBufferedReader(path)` có thể ném **checked IOException**.
- Việc tiêu thụ `reader.lines()` có thể ném **UncheckedIOException** nếu reader gặp lỗi I/O.
- `reader.lines()` không tự sở hữu/đóng reader độc lập; ở mẫu này, **try-with-resources bao reader**.
- Không đọc reader theo cách đồng thời ngoài stream khi stream đang được tiêu thụ; JavaDoc quy định không nên thao tác reader trong lúc stream đang vận hành vì kết quả có thể không xác định.
- Đừng suy rằng `BufferedReader.readLine()` (gọi trực tiếp) tự chuyển thành `UncheckedIOException`; nó vẫn khai báo checked `IOException`.

Sự khác biệt này là một ví dụ rõ: **cùng đối tượng dữ liệu, API trực tiếp và API stream có thể công bố exception khác nhau**.

## 21. Mẫu phân loại lỗi ở ranh giới đọc file

Một handler có thể phân biệt:

```java
try (Stream<String> lines = Files.lines(path)) {
    lines.forEach(this::consumeLine);
} catch (UncheckedIOException e) {
    handleReadFailure(e.getCause());
} catch (IOException e) {
    handleOpenOrCloseFailure(e);
}
```

Đây là **mã khung minh họa**: cần `this::consumeLine` tương thích với `Consumer<String>`, không ném checked exception, và các method xử lý tồn tại. Thứ tự hai handler không chịu quan hệ subtype vì `UncheckedIOException` không kế thừa `IOException`.

**Không coi việc bắt riêng hai loại là bằng chứng chắc chắn lỗi xảy ra đúng một bước.** Ví dụ `IOException` cũng có thể phát sinh từ cleanup; nếu wrapper được ném trong thân, lỗi cleanup có thể là suppressed. Phân tích cần ngữ cảnh thao tác và cause.

Nếu một handler thật sự có thể phục hồi, hãy làm rõ việc xử lý tiếp theo. Nếu không, nên ném tiếp exception hoặc chuyển lỗi tới tầng có trách nhiệm, không in một dòng log rồi âm thầm trả thành công.

## 22. `Files.list`, `Files.walk` và thao tác thư mục

Các API liên quan filesystem cũng có thể dùng Stream, ví dụ:

- `Files.list(Path)`: tạo stream các entry trực tiếp trong thư mục, có thể ném checked `IOException` lúc mở.
- `Files.walk(Path, ...)`: tạo stream duyệt cây file, có thể ném checked `IOException` lúc thiết lập.
- Các lỗi I/O phát sinh trong giai đoạn lazy traversal có thể được chuyển thành `UncheckedIOException` theo hợp đồng API tương ứng.

Cần đặc biệt lưu ý:

1. **Đóng stream** bằng try-with-resources, vì stream có thể nắm tài nguyên hệ điều hành.
2. Một phần cây thư mục đã được duyệt trước khi lỗi xảy ra; không có rollback tự động.
3. Cây file có thể thay đổi trong khi duyệt; “file vừa kiểm tra tồn tại” không bảo đảm thao tác tiếp theo thành công.
4. Quyền truy cập, symbolic links, file bị xóa, mounted filesystem và race có thể gây I/O errors.
5. Không ném `UncheckedIOException` chỉ để diễn đạt việc một entry không thỏa filter; filter loại entry là **nhánh bình thường**, không phải lỗi I/O.

Một giải pháp nhận diện vấn đề là **chia rõ giai đoạn mở nguồn, xử lý stream, đóng nguồn**, thay vì catch `Exception` chung và mất ngữ cảnh.

## 23. `UncheckedIOException` không phải lỗi “không tìm thấy dữ liệu” theo nghĩa nghiệp vụ

Tên lớp mô tả **một IOException đã được wrap**, không mang ngữ nghĩa domain như `CustomerNotFoundException`.

Ví dụ:

- Một file cấu hình bắt buộc bị thiếu → `NoSuchFileException` khi truy cập file có thể là cause.
- Một key không tồn tại trong `Map` → thường không liên quan I/O; dùng kết quả rỗng hoặc exception contract khác.
- Một record không có trong database → có thể là kết quả nghiệp vụ bình thường, không được mặc định tạo `UncheckedIOException`.
- Một filesystem từ chối đọc do quyền truy cập → IOException phù hợp, có thể bọc nếu API không khai báo checked.
- Một response HTTP hợp lệ báo 404 → không đồng nghĩa đã xảy ra `IOException`; xử lý theo contract HTTP/domain.

**Không tự tạo `new IOException("not found")` chỉ để được dùng `UncheckedIOException`** khi tình huống thực chất không phải thất bại I/O. Lựa chọn exception phải phản ánh **nguyên nhân thật** và trách nhiệm caller.

## 24. Vì sao không dùng `RuntimeException` chung cho mọi lỗi?

Bạn có thể viết:

```java
throw new RuntimeException("I/O thất bại", e);
```

khi `e` là `IOException`. Nó cũng là unchecked và giữ cause. Nhưng `UncheckedIOException` có một số lợi ích về hợp đồng:

- **Tên lớp diễn đạt rõ đây là lỗi I/O được chuyển sang unchecked**, không phải một runtime failure bất kỳ.
- Constructor chuyên biệt bắt buộc cause kiểu `IOException`.
- `getCause()` trả `IOException`, giúp caller phân loại và lấy thông tin I/O rõ ràng.
- Handler có thể `catch (UncheckedIOException e)` thay vì catch rộng `RuntimeException`.
- Code review dễ nhìn thấy ranh giới thay đổi chính sách lỗi.

Mặt khác, nếu tầng mới muốn biểu diễn **một domain abstraction khác**, chẳng hạn `ReportGenerationException` với mã lỗi riêng, custom exception có thể phù hợp hơn. Không có quy tắc cứ cause là IOException thì **luôn** phải dùng `UncheckedIOException`.

Chọn wrapper theo **hợp đồng API bạn muốn công bố**, không chỉ theo sự tiện lợi.

## 25. Không được bọc mọi checked exception bằng `UncheckedIOException`

Constructor của `UncheckedIOException` nhận `IOException`, không phải `Throwable` tùy ý. Những checked exception khác cần chính sách riêng:

- `SQLException`: không phải subclass của `IOException`.
- `ClassNotFoundException`: không phải subclass của `IOException`.
- `InterruptedException`: không phải subclass của `IOException`.
- Domain checked exception tự định nghĩa có thể không liên quan I/O.

Phản ví dụ **không biên dịch**:

```java
SQLException sqlError = new SQLException("DB lỗi");
throw new UncheckedIOException(sqlError);
```

Đây là **đoạn minh họa không hợp lệ**, nếu đã import `SQLException` và `UncheckedIOException`. Compiler không tìm thấy constructor chấp nhận `SQLException`.

Nếu muốn chuyển checked exception tổng quát sang unchecked, cần chọn **wrapper đúng ý nghĩa** hoặc API cung cấp kiểu lỗi riêng. Đừng cố `new IOException(sqlError)` chỉ để ép một lỗi SQL vào loại I/O, trừ khi thực sự đang thiết kế abstraction I/O có lý do rõ ràng.

Đặc biệt, `InterruptedException` mang ý nghĩa hủy/interrupt; không được tự coi nó là một IOException để bỏ qua ngữ nghĩa ngắt.

## 26. `UncheckedIOException` và `InterruptedIOException`

`InterruptedIOException` kế thừa `IOException`. Vì vậy có thể làm cause của `UncheckedIOException` theo kiểu Java. Nhưng **`InterruptedIOException` không đồng nghĩa chắc chắn thread đã bị interrupt** trong mọi trường hợp.

Ví dụ `SocketTimeoutException extends InterruptedIOException` được dùng để báo thời hạn chờ socket đã hết. **Bắt một `SocketTimeoutException` và gọi `Thread.currentThread().interrupt()` vô điều kiện là sai** nếu chưa có dấu hiệu thread thật sự nhận interrupt.

Nếu một thao tác I/O bị ngắt vì thread cancellation, cần tuân theo contract của API đó (có thể cần khôi phục cờ interrupt hoặc lan truyền dấu hiệu hủy). Việc bọc vào `UncheckedIOException` không tự giải quyết chính sách cancellation.

Một thiết kế tốt tách:

- **Timeout**: hết ngân sách chờ thao tác.
- **Interruption**: tín hiệu luồng muốn dừng.
- **I/O failure**: thao tác đọc/ghi không thành công.
- **Domain failure**: yêu cầu không đạt điều kiện nghiệp vụ.

Các hiện tượng có thể tương tác, nhưng không được gộp thành một loại thành công/thất bại duy nhất thiếu ngữ cảnh.

## 27. `UncheckedIOException` khác `CompletionException` và `ExecutionException`

Một callback có thể ném `UncheckedIOException`, nhưng khi framework chạy callback **bất đồng bộ**, caller đôi khi nhận thêm một wrapper của framework.

Ví dụ cấu trúc:

```java
CompletableFuture<String> future =
        CompletableFuture.supplyAsync(
                () -> readUnchecked("some-file"));

String value = future.join();
```

Đây là **mã minh họa**, cần định nghĩa `readUnchecked` và import `CompletableFuture`. Nếu callback ném `UncheckedIOException`:

- `join()` có thể báo lỗi bằng `CompletionException`, với cause là lỗi từ callback theo contract của future.
- `Future.get()` trên một task đã thất bại báo `ExecutionException`.
- Lớp wrapper ngoài **không phải bằng chứng cause gốc đã mất**.
- Bên xử lý cần xét **nhiều tầng cause** nếu chính sách phân loại thất bại nằm ở ranh giới bất đồng bộ.
- Không nên giả định mọi framework dùng cùng một wrapper, thứ tự cause hay cách ghi log.

Không catch `CompletionException` rồi trả giá trị mặc định mà không kiểm tra cause và chính sách phục hồi.

## 28. Ví dụ Java 17: exception được bọc qua `CompletableFuture.join`

Lưu **`AsyncUncheckedIOExceptionDemo.java`**:

```java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class AsyncUncheckedIOExceptionDemo {
    static String read() {
        try {
            throw new IOException("Không đọc được payload");
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Callback I/O thất bại", e);
        }
    }

    public static void main(String[] args) {
        CompletableFuture<String> future =
                CompletableFuture.supplyAsync(
                        AsyncUncheckedIOExceptionDemo::read);

        try {
            future.join();
        } catch (CompletionException e) {
            Throwable wrapper = e.getCause();
            System.out.println("Async cause type: "
                    + wrapper.getClass().getSimpleName());

            if (wrapper instanceof UncheckedIOException io) {
                System.out.println("I/O cause: "
                        + io.getCause().getMessage());
            }
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 AsyncUncheckedIOExceptionDemo.java
java AsyncUncheckedIOExceptionDemo
```

**Stdout chính xác** trên Java 17 đối với task có hành vi đã khai báo:

```text
Async cause type: UncheckedIOException
I/O cause: Không đọc được payload
```

Ví dụ không phụ thuộc truy cập file thực hay thời điểm task hoàn tất; `join()` chờ kết quả. Nó minh họa hai tầng wrapping: `CompletionException -> UncheckedIOException -> IOException`.

Trong code production, cần chính sách cancellation, timeout và đóng executor khi tự quản lý executor. Ví dụ dùng common pool mặc định chỉ để minh họa cách quan sát lỗi.

## 29. Catch đúng tầng: không phải chỉ catch `IOException`

Khi một API truyền ra `UncheckedIOException`, handler:

```java
try {
    runPipeline();
} catch (IOException e) {
    handleIoError(e);
}
```

đây là **mã phản ví dụ về kỳ vọng sai** (các hàm phụ chưa định nghĩa). Vì `UncheckedIOException` không extends `IOException`, handler trên **không** bắt wrapper ấy. Ngoài ra, nếu trong `try` không có lời gọi nào được khai báo có thể ném checked `IOException`, Java có thể báo **catch checked IOException không hợp lệ**.

Dạng thích hợp khi code có thể truyền cả hai:

```java
try {
    runOperation();
} catch (UncheckedIOException e) {
    handleIoError(e.getCause());
} catch (IOException e) {
    handleIoError(e);
}
```

Đây là **mã khung minh họa**, `runOperation` cần có chữ ký cho phép checked IOException nếu muốn nhánh thứ hai hợp lệ. Việc bắt cả hai lớp là tùy contract của đường xử lý.

Quan trọng hơn: **không ép tầng nào cũng catch cả hai**. Nếu API công bố một loại nhất quán, hãy xử lý loại đó tại ranh giới có trách nhiệm.

## 30. Không bắt `UncheckedIOException` để trả thành công giả

Một anti-pattern:

```java
try {
    return files.stream().map(uncheckedReader).toList();
} catch (UncheckedIOException ignored) {
    return List.of();
}
```

Đây là **mã khung minh họa**, chưa có biến/hàm đầy đủ. Nếu yêu cầu đọc các file bắt buộc, lỗi đọc file thứ hai không thể biến thành “danh sách đầu ra rỗng” mà caller tin là thành công.

Các lựa chọn tốt hơn:

1. **Fail whole operation** nếu tất cả mục là bắt buộc.
2. **Thu thập kết quả từng phần tử** theo kiểu success/error có cấu trúc nếu hệ thống cho phép partial success.
3. **Retry có giới hạn** ở đúng tầng khi an toàn và nguyên nhân có thể phục hồi.
4. **Fallback** chỉ khi có dữ liệu dự phòng thực sự hợp lệ và contract công bố rõ.
5. **Ghi nhận và báo thất bại** ở tầng giao tiếp thay vì im lặng nuốt.

Đặc biệt với Stream pipeline có side effect, các phần tử trước lỗi có thể đã được xử lý. Việc catch exception không hủy những thay đổi đã xảy ra.

## 31. Retry phải xem thao tác I/O và idempotency

Không phải cứ `UncheckedIOException` là retry ngay. Loại này chỉ nói **một IOException đã được chuyển thành unchecked**, không nói nguyên nhân chắc chắn tạm thời.

Cần xác định:

- Lỗi đến từ đọc file, ghi file, HTTP, socket, storage hay mount từ xa?
- Đã ghi hoặc gửi **một phần** dữ liệu chưa?
- Request phía server có thể đã commit nhưng response bị mất không?
- Thao tác có **idempotent** hay có ID yêu cầu chống thực thi lặp?
- Có tài nguyên cần đóng hoặc trạng thái pipeline không thể tái sử dụng?
- Giới hạn số lần retry, backoff và deadline tổng là gì?
- Lỗi do đường dẫn sai/quyền truy cập/cấu hình, hay do gián đoạn tạm thời?

Ví dụ:

- Đọc một file bất biến có thể retry có kiểm soát nếu hệ thống và dữ liệu cho phép.
- Ghi tiền hoặc tạo đơn hàng qua mạng cần **idempotency key/tra cứu trạng thái** trước khi retry.
- File không có thật do người dùng cung cấp sai đường dẫn cần phản hồi phù hợp, không phải retry vô hạn.
- IOException vì disk full thường cần can thiệp tài nguyên, không khắc phục bằng lặp lại nhanh.

**Wrapper không mã hóa chính sách retry.** Caller không nên suy quá nhiều từ riêng loại `UncheckedIOException`.

## 32. Try-with-resources, primary và suppressed exceptions

Một callback có I/O có thể dùng tài nguyên:

```java
try (Reader reader = openReader()) {
    consume(reader);
} catch (IOException e) {
    throw new UncheckedIOException(
            "Đọc hoặc đóng Reader thất bại", e);
}
```

Đây là **mã minh họa**, cần `openReader()`, `consume`, `Reader` và import tương ứng.

Nếu `consume` ném `IOException`, rồi `reader.close()` cũng ném, try-with-resources có thể gắn lỗi đóng vào **`e.getSuppressed()`** của lỗi chính. Khi bọc trong `UncheckedIOException`, suppressed exceptions **vẫn nằm trên cause** trừ khi bạn chủ động tạo/chuyển quan hệ khác.

Phân biệt:

- `wrapper.getCause()` là IOException đã bị bọc.
- `wrapper.getSuppressed()` là danh sách suppressed gắn **trực tiếp** trên wrapper; có thể rỗng.
- `wrapper.getCause().getSuppressed()` có thể chứa lỗi cleanup từ try-with-resources trước khi wrapping.

Không chuyển mọi suppressed exception thành một lỗi chính mới mà làm mất nguyên nhân ban đầu. Khi ghi log, ưu tiên logger nhận object exception để hiển thị toàn bộ cause/suppressed/stack trace nếu được hỗ trợ.

## 33. Ví dụ Java 17: kiểm tra suppressed exception trên cause

Lưu **`SuppressedIoWrapperDemo.java`**:

```java
import java.io.IOException;
import java.io.UncheckedIOException;

public class SuppressedIoWrapperDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("Lỗi đóng tài nguyên");
        }
    }

    static void perform() {
        try (Resource resource = new Resource()) {
            throw new IOException("Lỗi xử lý chính");
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Xử lý tài nguyên thất bại", e);
        }
    }

    public static void main(String[] args) {
        try {
            perform();
        } catch (UncheckedIOException e) {
            System.out.println("Cause: "
                    + e.getCause().getMessage());
            System.out.println("Suppressed on cause: "
                    + e.getCause().getSuppressed().length);
            System.out.println("Suppressed message: "
                    + e.getCause().getSuppressed()[0].getMessage());
            System.out.println("Suppressed on wrapper: "
                    + e.getSuppressed().length);
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 SuppressedIoWrapperDemo.java
java SuppressedIoWrapperDemo
```

**Stdout chính xác:**

```text
Cause: Lỗi xử lý chính
Suppressed on cause: 1
Suppressed message: Lỗi đóng tài nguyên
Suppressed on wrapper: 0
```

Điểm mấu chốt: **giữ cause** giúp giữ lại cả danh sách suppressed có trên exception gốc. Nhưng suppressed không tự “di chuyển” sang wrapper, vì hai object khác nhau.

Mẫu này dùng `AutoCloseable` tùy chỉnh chỉ để chứng minh cấu trúc exception, không phụ thuộc filesystem hay networking.

## 34. Không bọc lỗi đã là unchecked lần nữa nếu không có giá trị

Nếu `Function.apply` bên trong ném `UncheckedIOException`, một adapter chỉ bắt `IOException` sẽ **không bắt lại** wrapper ấy:

```java
try {
    return function.apply(value);
} catch (IOException e) {
    throw new UncheckedIOException(e);
}
```

Đây là **mã khung**, với `function.apply` khai báo checked IOException. Một `UncheckedIOException` thoát từ bên trong `function.apply` sẽ đi xuyên qua handler `catch (IOException)` mà không bị bọc thêm, vì nó là unchecked sibling branch.

Nếu bắt rộng `catch (Exception e)` rồi `new RuntimeException(e)`, bạn có thể tạo chuỗi wrapper lồng nhau vô ích và vô tình bắt cả lỗi lập trình (`NullPointerException`, `IllegalStateException`). Nên giới hạn catch **đúng checked IOException** mà adapter cần chuyển loại.

Đây cũng là lý do đặt các handler cụ thể ở ranh giới phù hợp: tránh mất thông tin và tránh bọc lặp làm khó phân tích stack trace.

## 35. Method reference vẫn bị ràng buộc bởi `throws`

Một method reference tới `Files.readString` **không** tương thích trực tiếp với `Function<Path,String>`. Chuyển cú pháp:

```java
Function<Path, String> direct = Files::readString;
```

là **phản ví dụ không biên dịch**. Không có conversion tự động chỉ vì dùng `::`.

Ngược lại:

```java
IoFunction<Path, String> checked = Files::readString;
Function<Path, String> adapted = unchecked(checked);
```

đây là **đoạn minh họa** giả định `IoFunction` và adapter `unchecked` như đã định nghĩa trước. Method reference hợp lệ ở bước thứ nhất vì target SAM có `throws IOException`; bước thứ hai đổi hợp đồng sang `Function` một cách có chủ đích.

Nếu một người viết thêm `throws IOException` trên method chứa dòng `Function<Path,String> direct = Files::readString;`, compiler vẫn từ chối bởi SAM `Function.apply` không thay đổi.

## 36. Có thể chuyển lỗi thành kết quả thay vì exception

Không phải mọi pipeline có I/O đều nên dừng toàn bộ khi một phần tử thất bại. Với tác vụ xử lý danh sách file, đôi khi yêu cầu là **giữ cả thành công lẫn lỗi từng file**.

Có thể mô hình hóa bằng record hoặc sealed hierarchy, chẳng hạn:

```java
sealed interface ReadResult permits ReadSuccess, ReadFailure {
}

record ReadSuccess(String file, String content)
        implements ReadResult {
}

record ReadFailure(String file, String reason)
        implements ReadResult {
}
```

Đây là **các kiểu Java 17 minh họa**, không có `main`. Method đọc có thể bắt `IOException` và trả `ReadFailure` thay vì ném wrapper, khi điều đó là **nhánh nghiệp vụ được chấp nhận**.

Ưu điểm:

- Pipeline có thể hoàn tất và thu thập lỗi **mỗi phần tử**.
- Caller không cần catch một exception chung để biết file nào thất bại.
- Có thể báo cáo partial success minh bạch.
- Kết quả có cấu trúc không phụ thuộc message exception làm mã phân loại.

Nhược điểm: code phải xử lý nhiều dạng kết quả; nếu toàn bộ thao tác phải thất bại ngay khi một file bắt buộc không đọc được, wrapper vẫn có thể đơn giản và đúng hơn.

**Chọn theo ngữ nghĩa tác vụ**, không theo mong muốn giữ pipeline “đẹp” bằng mọi giá.

## 37. So sánh bốn cách biểu diễn lỗi I/O

| Cách | Hợp đồng Java | Khi nào phù hợp | Rủi ro |
| --- | --- | --- | --- |
| `throws IOException` | Checked, caller catch/declare | API sở hữu cho phép checked I/O | Có thể khó dùng với `Function`/`Consumer` chuẩn |
| `UncheckedIOException` | Unchecked nhưng cause phải là IOException | Callback không cho checked `throws`, cần truyền lỗi I/O rõ | Caller có thể bỏ qua nếu không biết hợp đồng |
| Custom domain exception | Checked hoặc unchecked theo superclass chọn | Muốn báo lỗi theo nghiệp vụ/tầng trừu tượng | Dễ bọc lặp hoặc làm mất cause nếu thiết kế kém |
| Result type (`Success/Failure`) | Dữ liệu trả về có cấu trúc | Lỗi là nhánh dự kiến, cần partial success | Caller phải kiểm tra/định tuyến trạng thái kết quả |

Một số quyết định thực hành:

- API thư viện công bố dữ liệu từ file và muốn caller tự fallback → có thể giữ checked `IOException`.
- Mapper trong stream hiện có hợp đồng `Function` → dùng adapter `UncheckedIOException` nếu fail-fast hợp lý.
- Batch xử lý mọi file dù có file lỗi → kiểu `ReadResult` có thể phù hợp.
- API domain không muốn lộ chi tiết filesystem → custom domain exception có `cause` rõ ràng.

Không có cách duy nhất tốt nhất cho mọi dự án.

## 38. Kiểm thử `UncheckedIOException` đúng cách

Một bài test nên xác minh không chỉ “có exception” mà còn:

1. **Wrapper type đúng:** `UncheckedIOException`.
2. **Cause đúng object hoặc đúng subtype IOException:** không làm mất exception gốc.
3. **Message/context phù hợp:** không lộ thông tin nhạy cảm, đủ để chẩn đoán.
4. **Lỗi không bị nuốt:** tác vụ thất bại theo chính sách.
5. **Stream lazy:** ngoại lệ xảy ra khi callback thật sự chạy, không phải mặc định lúc tạo pipeline.
6. **Kết quả từng phần/side effect:** không giả định rollback.
7. **Nguồn tài nguyên được đóng** dù callback ném lỗi.
8. **Không retry vô hạn** hoặc thực thi trùng với thao tác có side effect.
9. **Các loại runtime khác không bị bọc sai**: ví dụ `IllegalArgumentException` từ validation không nên biến thành I/O.
10. **Ranh giới bất đồng bộ giữ được cause chain** nếu chạy trong future/executor.

Nên dùng test nhân tạo ném `IOException` để kiểm thử adapter. Test trực tiếp quyền file hay mạng có thể flaky; integration test riêng kiểm tra tương tác thực và tài nguyên.

## 39. Ví dụ Java 17: tự kiểm tra giữ object cause và runtime exception khác

Lưu **`UncheckedIOExceptionAssertionsDemo.java`**:

```java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;

public class UncheckedIOExceptionAssertionsDemo {
    @FunctionalInterface
    interface IoFunction<T, R> {
        R apply(T input) throws IOException;
    }

    static <T, R> Function<T, R> unchecked(
            IoFunction<T, R> fn) {
        return value -> {
            try {
                return fn.apply(value);
            } catch (IOException e) {
                throw new UncheckedIOException("I/O failed", e);
            }
        };
    }

    public static void main(String[] args) {
        IOException source = new IOException("root");
        Function<String, String> failIo =
                unchecked(value -> { throw source; });

        try {
            failIo.apply("x");
            throw new AssertionError("Thiếu wrapper");
        } catch (UncheckedIOException e) {
            if (e.getCause() != source) {
                throw new AssertionError("Cause không còn cùng object");
            }
            System.out.println("PASS same cause");
        }

        Function<String, String> failValidation =
                unchecked(value -> {
                    throw new IllegalArgumentException("bad argument");
                });

        try {
            failValidation.apply("x");
            throw new AssertionError("Thiếu validation error");
        } catch (IllegalArgumentException e) {
            System.out.println("PASS runtime unchanged");
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 UncheckedIOExceptionAssertionsDemo.java
java UncheckedIOExceptionAssertionsDemo
```

**Stdout chính xác:**

```text
PASS same cause
PASS runtime unchanged
```

Chương trình chứng minh:

- Adapter giữ chính object `IOException` gốc trong `getCause()`.
- Handler chỉ bắt `IOException` nên **không biến `IllegalArgumentException` thành lỗi I/O**.
- Test dùng `AssertionError` để thất bại chủ động; không phụ thuộc cờ `-ea`.

Đây là hai kiểm tra quan trọng nếu bạn định đóng gói adapter thành tiện ích dùng chung.

## 40. Các hiểu nhầm thường gặp

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “`UncheckedIOException` là subclass `IOException`.” | Sai. Nó là subclass `RuntimeException` và chứa `IOException` làm cause. |
| “Đã có `UncheckedIOException` thì mọi `Files.readString` đều unchecked.” | Sai. `Files.readString` vẫn công bố checked `IOException`. |
| “`Function.apply` tự hiểu và chuyển IOException.” | Không. Callback phải bắt/chuyển hoặc dùng hợp đồng khác. |
| “Bọc code tạo Stream trong catch IOException sẽ sửa method reference không tương thích.” | Không. Checked exception nằm trong SAM; phải đổi callback. |
| “`UncheckedIOException` đã xử lý được lỗi.” | Sai. Nó chỉ đổi hình thức lan truyền exception. |
| “`catch (IOException)` bắt luôn `UncheckedIOException`.” | Không; hai loại ở hai nhánh khác nhau. |
| “`UncheckedIOException` nhận cause là SQLException.” | Không. Constructor chuyên biệt cần IOException. |
| “Mọi lỗi I/O đều có thể retry an toàn.” | Không. Có thể đã có side effect hoặc lỗi cấu hình vĩnh viễn. |
| “Viết `throws IOException` ở method chứa lambda làm Function.apply cho checked IOException.” | Sai. SAM của Function không thay đổi. |
| “`Files.lines` chỉ có unchecked lỗi.” | Sai. Lúc mở stream có thể ném checked IOException; lúc xử lý lazy có thể có UncheckedIOException. |
| “Stream pipeline chạy toàn bộ mapper ngay lúc được tạo.” | Không. Các intermediate operations thường lazy. |
| “Dữ liệu đã ghi trước lỗi sẽ được Stream rollback.” | Không. Stream không cung cấp transaction/rollback cho side effect. |
| “Sau `UncheckedIOException` có thể an toàn tiếp tục dùng mọi resource.” | Không thể suy vậy; phải xem giao thức và trạng thái tài nguyên. |
| “Bắt `Exception` rồi bọc thành RuntimeException là tương đương.” | Nó có thể bắt cả lỗi lập trình, làm mất ngữ nghĩa I/O và tạo wrapper dư thừa. |
| “`getCause()` của UncheckedIOException chỉ là Throwable chung.” | Java khai báo override trả `IOException`. |
| “Mọi wrapper đều tự mang suppressed của cause lên chính wrapper.” | Không. Suppressed thuộc từng Throwable object, cần kiểm tra đúng tầng. |
| “Vì `SocketTimeoutException extends InterruptedIOException` nên phải đặt interrupt flag.” | Không chỉ vì timeout; cần biết contract cancellation thật. |
| “`join()` trả trực tiếp UncheckedIOException trong mọi Future.” | Không. CompletableFuture có thể báo `CompletionException` với cause tương ứng. |
| “Nếu dùng `UncheckedIOException` thì không cần JavaDoc.” | Sai. Caller vẫn cần biết hợp đồng, thời điểm lỗi và chính sách phục hồi. |
| “Mọi nhánh không tìm thấy dữ liệu đều nên ném UncheckedIOException.” | Không. Thiếu dữ liệu nghiệp vụ có thể không phải lỗi I/O. |

## 41. Câu hỏi tự kiểm tra

**1. `UncheckedIOException` là checked hay unchecked?**  
Unchecked, vì kế thừa `RuntimeException`.

**2. `UncheckedIOException extends IOException` có đúng không?**  
Không. Nó chứa một `IOException` làm `cause`.

**3. Vì sao `Function<Path,String> f = Files::readString` không biên dịch?**  
`Files.readString` khai báo checked `IOException` nhưng `Function.apply` không cho checked `throws`.

**4. Bọc lambda bằng `try/catch(IOException)` rồi ném `UncheckedIOException` làm thay đổi điều gì?**  
Checked IOException không còn thoát khỏi SAM; thay vào đó là unchecked wrapper khi I/O thất bại.

**5. `UncheckedIOException.getCause()` có kiểu trả về tĩnh là gì?**  
`IOException`.

**6. Có thể truyền `SQLException` trực tiếp cho constructor `UncheckedIOException` không?**  
Không, vì `SQLException` không là `IOException`.

**7. API nào nên cân nhắc nếu muốn callback **giữ checked IOException**?**  
Custom SAM có `throws IOException`, hoặc một API có sẵn cho phép checked exception như `Callable.call`.

**8. Có phải `Stream.map` luôn thực thi ngay khi viết `stream.map(fn)`?**  
Không. Intermediate operations thường lazy.

**9. `Files.lines` có thể ném loại lỗi nào khi mở file?**  
Checked `IOException`.

**10. Trong lúc tiêu thụ lazy Stream từ `Files.lines`, lỗi đọc I/O có thể xuất hiện dưới loại nào?**  
`UncheckedIOException`.

**11. Khi xử lý `BufferedReader.readLine()` trực tiếp, Java có tự bọc thành `UncheckedIOException` không?**  
Không. `readLine` trực tiếp khai báo checked `IOException`.

**12. Vì sao phải đóng stream từ `Files.lines`?**  
Nó có thể nắm tài nguyên file; dùng try-with-resources theo contract.

**13. Nếu stream đã xử lý ba phần tử và phần tử thứ tư ném wrapper, các side effect trước có tự rollback không?**  
Không.

**14. Vì sao không retry một lệnh tạo giao dịch chỉ dựa trên loại `UncheckedIOException`?**  
Vì lệnh có thể đã thực thi phía server hoặc đã ghi một phần; cần idempotency, kiểm tra trạng thái và chính sách retry.

**15. Trong `CompletableFuture.join()`, wrapper nào có thể bao ngoài lỗi từ callback?**  
`CompletionException`.

**16. Có cần giữ object IOException gốc ở cause khi chuyển lỗi?**  
Có, để giữ loại lỗi, message, stack trace và dữ liệu suppressed của cause.

**17. Sau khi wrap lỗi chính đã có suppressed, nên tra chúng ở đâu?**  
Thường ở `wrapped.getCause().getSuppressed()`, vì suppressed không tự chuyển lên wrapper.

**18. Nếu lambda ném `IllegalArgumentException`, adapter chỉ catch `IOException` có đổi nó thành `UncheckedIOException` không?**  
Không, vì `IllegalArgumentException` không thuộc `IOException`.

**19. Dùng `UncheckedIOException` có tự tạo deadline hoặc timeout cho đọc file không?**  
Không. Nó không liên quan thiết lập deadline.

**20. Nếu một phần tử không tồn tại theo nghiệp vụ, có bắt buộc dùng `UncheckedIOException` không?**  
Không. Nó chỉ phù hợp khi có lỗi I/O thực và hợp đồng muốn truyền unchecked.

**21. Có thể chọn `ReadResult` success/failure thay vì exception trong batch không?**  
Có, khi cần giữ kết quả từng phần tử hoặc thất bại là nhánh dự kiến.

**22. `Callable.call() throws Exception` có thể tham chiếu method `throws IOException` không?**  
Có, vì `IOException extends Exception`.

**23. `UncheckedIOException` được tạo bằng constructor nào để bổ sung context?**  
`new UncheckedIOException("Mô tả", ioException)`.

**24. Vì sao không nên catch rộng RuntimeException rồi coi mọi lỗi là “I/O”?**  
Vì đó có thể là lỗi đối số, trạng thái, NPE hoặc bug khác.

## 42. Checklist khi review code sử dụng `UncheckedIOException`

Trước khi đưa vào production:

1. **Xác nhận lỗi gốc thật sự là IOException**, không phải lỗi nghiệp vụ hay checked exception khác.
2. **Xác định SAM/API đang giới hạn checked throws**: Function, Consumer, Supplier, Runnable, Stream?
3. **Có thể thay bằng custom SAM có `throws IOException` không?** Nếu sở hữu API, cân nhắc lựa chọn checked.
4. **Có cần fail-fast hay giữ partial results?** Cân nhắc result type nếu cần.
5. **Bắt đúng phạm vi IOException**, không bắt `Exception` rồi gán nhầm ngữ nghĩa.
6. **Dùng constructor giữ cause**: `new UncheckedIOException(message, e)`.
7. **Message bổ sung ngữ cảnh có ích** mà không lộ dữ liệu nhạy cảm.
8. **Biết exception xảy ra ở đâu**: lúc tạo nguồn, tạo pipeline hay terminal operation?
9. **Bắt wrapper tại đúng ranh giới**; `catch(IOException)` không bắt được wrapper.
10. **Nếu dùng Files.lines/list/walk**, có quản lý stream/resource bằng try-with-resources không?
11. **Nếu dùng BufferedReader.lines**, ai sở hữu và đóng Reader?
12. **Nếu callback có side effect**, đã xét dữ liệu ghi một phần và rollback chưa?
13. **Không retry mù quáng** chỉ vì lỗi mang từ “I/O”.
14. **Đối với request có side effect**, có idempotency key hoặc trạng thái xác nhận chưa?
15. **Nếu chạy bất đồng bộ**, có xử lý wrapper của Future/CompletableFuture đúng hợp đồng không?
16. **Có log Throwable cùng cause/suppressed**, thay vì chỉ lưu message không?
17. **Có test cause giữ nguyên object**, subtype đúng và runtime exception khác không bị bắt nhầm không?
18. **Có test đúng thời điểm xảy ra lỗi lazy stream không?**
19. **Có tài nguyên chưa đóng sau exception không?** Kiểm tra try-with-resources và cleanup.
20. **Có nguy cơ ném wrapper lồng nhiều tầng không cần thiết không?**
21. **Có chính sách cancellation/interrupt riêng** với timeout I/O không?
22. **Đã tài liệu hóa unchecked exception** trong API/adapter chưa?
23. **Đã tránh biến thất bại thành danh sách rỗng hoặc thành công giả chưa?**
24. **Nếu đổi hợp đồng public API từ checked sang unchecked**, đã xét caller/catch/tests và tương thích chưa?

## 43. Kết luận

`UncheckedIOException` là **cầu nối giữa checked `IOException` và những API Java không cho callback ném checked exception**. Nó là subclass của `RuntimeException`, giữ nguyên nhân kiểu `IOException` ở `getCause()`, và đặc biệt hữu ích với lambda, method reference adapter, `Function`, `Consumer`, Stream API hoặc các API đọc dữ liệu lazily như `Files.lines` và `BufferedReader.lines`.

Điều cần phân biệt là **truyền lỗi** và **xử lý lỗi**. Bọc `IOException` bằng `UncheckedIOException` giúp mã đi qua một chữ ký callback không có checked `throws`; nó **không phục hồi I/O, không tạo retry an toàn, không đóng tài nguyên thay bạn và không rollback side effect**. Lỗi vẫn phải được báo hoặc xử lý ở tầng có trách nhiệm.

Khi API của bạn có thể công bố checked `IOException` và caller cần quyền xử lý tường minh, giữ checked exception hoặc dùng custom SAM có `throws IOException` có thể tốt hơn. Nếu một thất bại là nhánh nghiệp vụ dự kiến hoặc cần partial success, kiểu kết quả có trạng thái có thể phù hợp hơn. Chỉ chuyển sang unchecked **có chủ đích, giữ cause, chọn ranh giới bắt hợp lý và kiểm thử chính sách thất bại**.


</details>

- [Quay lại đầu trang](#back-to-top)
