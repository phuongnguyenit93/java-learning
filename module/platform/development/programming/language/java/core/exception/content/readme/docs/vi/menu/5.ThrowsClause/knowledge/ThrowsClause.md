<a id="back-to-top"></a>

# Mệnh đề throws

## Menu
- [Cú pháp throws trên phương thức và constructor](#throws-clause-001)
- [Khai báo một hoặc nhiều kiểu ngoại lệ](#throws-clause-002)
- [Quan hệ giữa kiểu được khai báo và lớp con thực sự bị ném](#throws-clause-003)
- [Phân biệt throw với throws](#throws-clause-004)
- [Lan truyền checked exception qua nhiều tầng lời gọi](#throws-clause-005)
- [Khi nào nên catch và khi nào nên tiếp tục throws](#throws-clause-006)
- [Khai báo unchecked exception và vai trò tài liệu hóa](#throws-clause-007)
- [Vì sao throws không có nghĩa phương thức luôn phát sinh lỗi](#throws-clause-008)
- [Hợp đồng ngoại lệ của main và giới hạn khi đẩy lỗi lên trên cùng](#throws-clause-009)

## <a id="throws-clause-001">Cú pháp throws trên phương thức và constructor</a>

<details>
<summary>Xem chi tiết</summary>

# Cú pháp `throws` trên phương thức và constructor trong Java

Mệnh đề **`throws`** nằm trong phần khai báo một **phương thức (method)** hoặc **constructor** để chỉ ra các loại ngoại lệ mà lời gọi có thể truyền ra cho mã gọi. Đây là một phần trong hợp đồng của API, đặc biệt quan trọng với **checked exception**: trình biên dịch buộc mã gọi phải bắt ngoại lệ hoặc tiếp tục khai báo ngoại lệ thích hợp.

Cần phân biệt ba khái niệm ngay từ đầu:

- `new IOException("...")` **tạo một đối tượng ngoại lệ**.
- `throw new IOException("...");` **ném** đối tượng, làm luồng thực thi hiện tại kết thúc bất thường.
- `void read() throws IOException { ... }` **khai báo** khả năng phương thức có thể để ngoại lệ `IOException` thoát ra. Chỉ khai báo `throws` không tạo, ném hay xử lý ngoại lệ.

Bài này hướng dẫn viết đúng vị trí, danh sách và kiểu ngoại lệ trong mệnh đề `throws` của phương thức và constructor trên Java 17+, kèm ví dụ chạy được, các quy tắc liên quan khi gọi và kế thừa, cùng lỗi biên dịch phổ biến.

## 1. Cú pháp của `throws` ở phương thức

Dạng tổng quát của một khai báo phương thức có thân:

~~~java
[modifiers] ReturnType methodName(ParameterType parameter, ...)
        throws ExceptionType1, ExceptionType2 {
    // Nội dung phương thức
}
~~~

Trong cú pháp trên:

- `[modifiers]` biểu diễn các từ khóa tùy chọn như `public`, `protected`, `private`, `static` hoặc `final`, tùy ngữ cảnh. Ngoặc vuông chỉ dùng để chú thích công thức, **không gõ nguyên văn** trong Java.
- `ReturnType` là kiểu trả về, hoặc `void`.
- `methodName(...)` là tên và danh sách tham số.
- `throws` đứng **sau dấu `)` kết thúc danh sách tham số**, trước dấu `{` mở phần thân.
- `ExceptionType1, ExceptionType2` là những kiểu ngoại lệ phân cách bởi dấu phẩy, không phải bởi `|`, `+` hoặc `;`.
- Nếu không có ngoại lệ cần khai báo, **bỏ hẳn `throws`**; không viết `throws` với danh sách rỗng.

Ví dụ một ngoại lệ:

~~~java
import java.io.IOException;

// Đặt trong một lớp.
public static String read(String filename) throws IOException {
    return java.nio.file.Files.readString(java.nio.file.Path.of(filename));
}
~~~

Ví dụ nhiều ngoại lệ:

~~~java
import java.io.IOException;
import java.sql.SQLException;

// Mã minh họa chữ ký và việc ném tương ứng, đặt trong một lớp.
static void execute(boolean databaseError) throws IOException, SQLException {
    if (databaseError) {
        throw new SQLException("Database unavailable");
    }
    throw new IOException("Input unavailable");
}
~~~

`throws IOException, SQLException` tạo ra **danh sách kiểu ngoại lệ trong chữ ký**; không có nghĩa mỗi lần gọi đều ném hai lỗi hoặc Java sẽ tự lần lượt phát sinh chúng. Khi phương thức được gọi, chỉ đường thực thi thực tế quyết định ngoại lệ nào xuất hiện.

Các khai báo interface/abstract method không có thân cũng đặt `throws` ở sau tham số, rồi kết thúc bằng dấu `;`:

~~~java
import java.io.IOException;

interface DocumentReader {
    String read() throws IOException;
}

abstract class AbstractImporter {
    public abstract void importData() throws IOException;
}
~~~

`throws` thuộc **khai báo phương thức**, không phải phần riêng của khối `try` hay một câu lệnh có thể chèn vào giữa phần thân.

### Những cách viết đúng và sai

| Cú pháp | Hợp lệ? | Giải thích |
| --- | --- | --- |
| `void work() throws IOException { ... }` | Có | `throws` sau danh sách tham số. |
| `void work() throws IOException, SQLException { ... }` | Có | Danh sách nhiều kiểu, cách nhau bởi dấu phẩy. |
| `void work() { ... }` | Có | Không bắt buộc ghi `throws` nếu không cần khai báo. |
| `void work() throws { ... }` | Không | Danh sách sau `throws` không được để rỗng. |
| `void work() throws IOException | SQLException { ... }` | Không | `|` thuộc cú pháp multi-catch, không dùng trong `throws`. |
| `throws IOException void work() { ... }` | Không | Đặt `throws` sai vị trí. |

Trong bảng, `...` chỉ là ký hiệu rút gọn để nói tới một phần thân hợp lệ, **không phải đoạn mã biên dịch độc lập**.

## 2. Cú pháp của `throws` ở constructor

Constructor cũng có thể ném checked exception khi khởi tạo đối tượng thất bại, ví dụ không mở được tệp, không tải được cấu hình hoặc việc gọi constructor lớp cha thất bại.

Dạng tổng quát:

~~~java
[modifiers] ClassName(ParameterType parameter, ...)
        throws ExceptionType1, ExceptionType2 {
    // Khởi tạo đối tượng
}
~~~

Các điểm riêng của constructor:

1. Tên constructor phải trùng với tên lớp và **không có kiểu trả về** — không ghi `void` hay kiểu đối tượng ở trước tên constructor.
2. `throws` đứng sau danh sách tham số và trước dấu `{`, giống vị trí ở phương thức.
3. Constructor có thể khai báo nhiều checked exception bằng danh sách phân cách dấu phẩy.
4. Constructor không được kế thừa và không thể `override` giống phương thức. Tuy nhiên, constructor của lớp con **gọi constructor lớp cha**, nên phải tuân thủ quy tắc checked exception của lời gọi đó.
5. Khi biểu thức `new SomeClass(...)` gọi một constructor khai báo checked exception, mã gọi phải bắt hoặc khai báo, hệt như gọi phương thức có `throws`.

Ví dụ cú pháp đơn giản:

~~~java
import java.io.IOException;

class ResourceSession {
    ResourceSession(boolean available) throws IOException {
        if (!available) {
            throw new IOException("Cannot open resource");
        }
    }
}
~~~

`new ResourceSession(false)` có thể phát sinh `IOException`, vì vậy nơi gọi phải có `try/catch` phù hợp hoặc khai báo `throws IOException`. Việc `new` là thao tác tạo đối tượng không làm nó được miễn khỏi quy tắc kiểm tra ngoại lệ.

### Sự khác biệt dễ nhận ra giữa method và constructor

~~~java
// Minh họa hai khai báo đặt trong cùng một lớp có tên Example.
Example() throws java.io.IOException {
    // Constructor: không có kiểu trả về.
}

void run() throws java.io.IOException {
    // Phương thức: có kiểu trả về void.
}
~~~

Nếu viết `void Example() throws IOException` bên trong lớp `Example`, đây là **phương thức có tên Example**, không phải constructor. Nên kiểm tra phần kiểu trả về khi một khai báo tưởng như constructor lại có hành vi khác mong đợi.

## 3. Ví dụ hoàn chỉnh: `throws` ở method và constructor

Tạo tệp `ThrowsSyntaxDemo.java`:

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ThrowsSyntaxDemo {
    static class Configuration {
        private final String content;

        Configuration(Path path) throws IOException {
            content = Files.readString(path);
        }

        String content() {
            return content;
        }
    }

    static Configuration load(Path path) throws IOException {
        return new Configuration(path);
    }

    public static void main(String[] args) throws IOException {
        Path path = Files.createTempFile("throws-demo-", ".txt");
        try {
            Files.writeString(path, "Xin chào");
            Configuration config = load(path);
            System.out.println("Nội dung: " + config.content());
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 ThrowsSyntaxDemo.java
java ThrowsSyntaxDemo
~~~

Stdout chính xác khi thao tác tạo/ghi/đọc/xóa tệp tạm thành công:

~~~text
Nội dung: Xin chào
~~~

Giải thích:

1. `Files.createTempFile` tạo tệp tạm; `Files.writeString` ghi dữ liệu. Các API I/O có thể phát sinh `IOException` nên `main` khai báo `throws IOException`.
2. `Configuration(Path path) throws IOException` khai báo checked exception vì lời gọi `Files.readString(path)` có thể thất bại.
3. `load(Path path) throws IOException` gọi `new Configuration(path)`. Nó không bắt `IOException` nên **khai báo để chuyển trách nhiệm lên người gọi**.
4. `main` gọi `load` và tiếp tục chuyển `IOException` ra ngoài nếu có thất bại; `main` không có `catch` đối với loại lỗi này. Do đó, nếu I/O thật sự lỗi và không được bắt ở tầng khác, chương trình thường kết thúc bất thường với stack trace.
5. `content()` chỉ trả lại dữ liệu đã lưu; nó không khai báo `throws` vì đoạn hiện tại không cần cho checked exception nào thoát ra.
6. Khối `finally` gọi `Files.deleteIfExists` để cố gắng xóa tệp tạm. Chính thao tác dọn dẹp này cũng có thể phát sinh `IOException`. Nếu cả phần `try` và `finally` ném lỗi, lỗi từ `finally` có thể che lỗi trước đó; dùng `try-with-resources` khi quản lý các tài nguyên `AutoCloseable` là cách phổ biến để giữ suppressed exception.

Việc dùng tệp tạm cho thấy `throws` là một **cam kết về khả năng thất bại của API**, không phải công cụ tạo lỗi. Trong trường hợp thành công, chương trình in kết quả bình thường mà không có ngoại lệ nào bị ném.

## 4. Người gọi phương thức hoặc constructor có `throws` phải làm gì?

Với **checked exception**, người gọi có hai lựa chọn phổ biến.

### 4.1. Bắt ở nơi có thể xử lý: `try/catch`

~~~java
import java.io.IOException;

// Đặt trong một lớp đã có Configuration và một phương thức logError.
static void start(Path path) {
    try {
        Configuration config = new Configuration(path);
        System.out.println(config.content());
    } catch (IOException ex) {
        logError(ex);
    }
}
~~~

Đây là **đoạn minh họa**, phụ thuộc các khai báo `Path`, `Configuration`, `logError` bên ngoài. Vì đã bắt `IOException`, `start` không bắt buộc khai báo `throws IOException` chỉ do lời gọi này, miễn không có đường checked exception khác thoát ra.

Nơi bắt nên có hành động xử lý hữu ích: báo lỗi cho người dùng, cung cấp đường dự phòng hợp lệ, thêm ngữ cảnh hoặc chuyển trách nhiệm có chủ đích. Không nên `catch (IOException ignored) { }` rồi cư xử như thể hoạt động thành công.

### 4.2. Tiếp tục khai báo `throws`

~~~java
import java.io.IOException;
import java.nio.file.Path;

// Đặt trong lớp có constructor Configuration(Path) throws IOException.
static Configuration start(Path path) throws IOException {
    return new Configuration(path);
}
~~~

Ở đây `start` không xử lý; `IOException` có thể truyền cho người gọi tiếp theo. Nếu nhiều tầng cùng `throws IOException`, lỗi đi ngược chuỗi lời gọi cho tới khi gặp `catch` phù hợp hoặc không còn người bắt.

`throws IOException` có thể bao quát các lớp con checked như `FileNotFoundException` hay `NoSuchFileException`; không nhất thiết phải liệt kê từng lớp con. Nhưng `throws IOException` **không tự bao quát `SQLException`**: hai loại này khác nhánh. Muốn cả hai có thể thoát ra, khai báo cả hai hoặc kiểu cha thích hợp, đồng thời cân nhắc độ rõ ràng của API.

### 4.3. `main` cũng có thể dùng `throws`

Chữ ký `public static void main(String[] args) throws IOException` là hợp lệ. Tuy nhiên, nó **không bắt** ngoại lệ ở ranh giới ứng dụng. Nếu lỗi thực sự thoát từ `main`, nó có thể dẫn tới chấm dứt bất thường và stack trace theo handler mặc định của luồng.

Nếu muốn thông báo thân thiện và mã thoát có kiểm soát, bắt lỗi tại một điểm thích hợp của ứng dụng. Việc viết `throws` chỉ làm trách nhiệm truyền lên tầng gọi rõ ràng về mặt ngôn ngữ.

## 5. Constructor chaining: `this(...)` và `super(...)`

Trong constructor, Java có cơ chế gọi constructor khác trong cùng lớp bằng `this(...)`, hoặc gọi constructor lớp cha bằng `super(...)`. Lời gọi đó có thể ném checked exception nên constructor đang khai báo cũng phải đáp ứng **catch-or-declare**. Trên Java 17, lời gọi constructor tường minh này phải là câu lệnh đầu tiên trong constructor; không thể bọc `super(...)` bằng `try/catch` ở thân constructor để xử lý tại chỗ theo cách thông thường.

### Ví dụ hoàn chỉnh: constructor lớp con khai báo `throws`

Tạo tệp `ConstructorThrowsDemo.java`:

~~~java
import java.io.IOException;

class Resource {
    Resource(boolean available) throws IOException {
        if (!available) {
            throw new IOException("không có tài nguyên");
        }
    }
}

class ManagedResource extends Resource {
    ManagedResource(boolean available) throws IOException {
        super(available);
    }
}

public class ConstructorThrowsDemo {
    public static void main(String[] args) {
        try {
            new ManagedResource(false);
        } catch (IOException ex) {
            System.out.println("Không khởi tạo: " + ex.getMessage());
        }

        try {
            new ManagedResource(true);
            System.out.println("Khởi tạo thành công");
        } catch (IOException ex) {
            System.out.println("Không khởi tạo: " + ex.getMessage());
        }
    }
}
~~~

Lệnh chạy:

~~~bash
javac -encoding UTF-8 ConstructorThrowsDemo.java
java ConstructorThrowsDemo
~~~

Stdout chính xác:

~~~text
Không khởi tạo: không có tài nguyên
Khởi tạo thành công
~~~

Phân tích:

- Constructor `Resource(boolean)` khai báo `throws IOException`. Nếu `available == false`, nó thực hiện `throw new IOException(...)`.
- Constructor `ManagedResource(boolean)` gọi `super(available)`. Vì lời gọi tới constructor lớp cha có thể phát sinh `IOException`, constructor lớp con khai báo `throws IOException`.
- Khi `new ManagedResource(false)` thất bại, việc khởi tạo đối tượng không hoàn tất bình thường; khối `catch` ở `main` xử lý `IOException`.
- Với `true`, không có ngoại lệ nào được ném; quá trình khởi tạo kết thúc bình thường.

Nếu xóa `throws IOException` khỏi `ManagedResource(boolean)` mà vẫn gọi `super(available)`, trình biên dịch báo lỗi checked exception chưa được bắt hoặc khai báo.

Một quy tắc đáng nhớ: **constructor không tham gia quan hệ override**. Không áp dụng quy tắc “constructor con chỉ được khai báo ngoại lệ hẹp hơn constructor cha”. Constructor con có thể khai báo kiểu checked thích hợp theo thao tác của chính nó, miễn các lời gọi cần thiết được xử lý theo luật Java.

### Constructor mặc định và lời gọi `super()` ngầm định

Nếu một lớp không khai báo constructor, trình biên dịch cung cấp **default constructor** theo các quy tắc của Java. Với lớp con, constructor phải gọi constructor lớp cha. Khi constructor không chỉ định `this(...)` hoặc `super(...)`, Java 17 sẽ cố gắng gọi `super()` ngầm định.

Nếu constructor không tham số của lớp cha khai báo checked exception, một lớp con không thể trông chờ vào default constructor để “tự động thêm `throws`”. Cần tự khai báo constructor phù hợp và nêu `throws`:

~~~java
import java.io.IOException;

class Parent {
    Parent() throws IOException {
        throw new IOException("khởi tạo Parent thất bại");
    }
}

class Child extends Parent {
    Child() throws IOException {
        super();
    }
}
~~~

Đây là **đoạn minh họa gồm hai lớp**, chưa có `main`. Nếu lược bỏ hoàn toàn constructor của `Child`, default constructor được tạo không khai báo `throws IOException` nên chương trình có thể lỗi biên dịch do lời gọi `super()` ngầm định.

Với `this(...)`, lý luận tương tự: constructor ủy quyền phải khai báo checked exception có thể truyền ra từ constructor đích. Nếu khởi tạo không thành công ở một mắt xích, chuỗi khởi tạo cũng kết thúc bất thường.

## 6. Checked và unchecked exception tác động đến `throws` như thế nào?

Quy tắc tĩnh quan trọng nhất:

- **Checked exception** là các kiểu `Throwable` không thuộc nhánh `RuntimeException` hoặc `Error`. Nếu phương thức/constructor có thể để chúng thoát ra, compiler thông thường yêu cầu khai báo kiểu checked thích hợp hoặc xử lý bằng `catch`.
- **Unchecked exception** thuộc nhánh `RuntimeException` hoặc `Error`; compiler không bắt buộc khai báo bằng `throws`.
- Cả hai loại đều **có thể** được liệt kê trong `throws`, nhưng liệt kê unchecked thường mang ý nghĩa tài liệu API hơn là nghĩa vụ bắt của mã gọi.

Ví dụ:

~~~java
// Mã minh họa nằm trong một lớp.
static int parse(String raw) {
    return Integer.parseInt(raw); // Có thể ném NumberFormatException, unchecked.
}

static int parseWithDeclaration(String raw)
        throws NumberFormatException {
    return Integer.parseInt(raw);
}
~~~

Cả hai chữ ký hợp lệ. Gọi `parseWithDeclaration` **không bị compiler yêu cầu** `catch (NumberFormatException)` chỉ vì có mệnh đề `throws`, vì đây là unchecked exception.

Ngược lại:

~~~java
import java.io.IOException;

// Đoạn minh họa đặt trong một lớp.
static void checkedFailure() throws IOException {
    throw new IOException("không đọc được");
}
~~~

Một phương thức gọi `checkedFailure()` mà không xử lý `IOException` hoặc khai báo tương ứng sẽ lỗi biên dịch.

**Quan trọng:** cách phân loại checked/unchecked là tính chất của kiểu ngoại lệ, không phải của chữ `throws`. Không có trường hợp “thêm `throws` vào kiểu runtime rồi biến nó thành checked”.

## 7. Khai báo nhiều loại ngoại lệ và kiểu cha

Cú pháp khai báo nhiều kiểu:

~~~java
import java.io.IOException;
import java.sql.SQLException;

// Mã minh họa thuộc một lớp.
static void synchronize(boolean dbMode) throws IOException, SQLException {
    if (dbMode) {
        throw new SQLException("DB error");
    }
    throw new IOException("Network error");
}
~~~

Người gọi cần bao phủ cả hai tình huống nếu chúng có thể truyền ra:

~~~java
// Đoạn minh họa nằm trong phương thức có synchronized(...) ở phạm vi lớp.
try {
    synchronize(true);
} catch (IOException ex) {
    System.out.println("I/O: " + ex.getMessage());
} catch (SQLException ex) {
    System.out.println("SQL: " + ex.getMessage());
}
~~~

Các đoạn ngắn trong phần này cần `import` tương ứng và được đặt bên trong lớp/phương thức; chúng dùng để minh họa chữ ký, không phải tệp Java độc lập.

Bạn cũng có thể viết `throws Exception`, vì `Exception` là kiểu cha của `IOException` và `SQLException`. Cú pháp này hợp lệ nhưng thường giảm độ chính xác hợp đồng API: người gọi khó xác định những lỗi nào có thể phục hồi và dễ phải bắt quá rộng.

Đối với cùng một họ ngoại lệ, `throws IOException` đã bao gồm các lớp con nên thông thường không cần viết `throws IOException, FileNotFoundException` cùng lúc. Có thể khai báo như vậy nhưng lặp về mặt bao phủ; nên ưu tiên loại đủ cụ thể hoặc kiểu cha có ý nghĩa theo mức trừu tượng của API.

Khi khai báo API ổn định, chú ý việc **thêm checked exception mới vào `throws`** có thể khiến mã nguồn của khách hàng không còn biên dịch nếu họ chưa bắt hay khai báo nó.

## 8. `throws` khi ghi đè phương thức và triển khai interface

Khác với constructor, **phương thức override** phải tuân thủ hợp đồng checked exception của phương thức cha hoặc interface.

~~~java
import java.io.FileNotFoundException;
import java.io.IOException;

interface Reader {
    String read() throws IOException;
}

class FileReaderImpl implements Reader {
    @Override
    public String read() throws FileNotFoundException {
        return "OK";
    }
}

class MemoryReaderImpl implements Reader {
    @Override
    public String read() {
        return "in-memory";
    }
}
~~~

Cả hai phương thức triển khai hợp lệ:

- `FileReaderImpl.read` khai báo `FileNotFoundException`, là lớp con của `IOException`.
- `MemoryReaderImpl.read` không khai báo checked exception nào; override được phép hẹp hơn hoặc loại bỏ ngoại lệ checked.
- Nếu thay khai báo của một override thành `throws Exception` hoặc `throws SQLException`, mã không hợp lệ với hợp đồng chỉ cho phép `IOException` ở interface `Reader`.
- Phương thức override vẫn có thể khai báo các unchecked exception mà không bị ràng buộc bởi danh sách checked exception của phương thức cha.

Một điểm thực dụng: kiểu tham chiếu tĩnh của biến quyết định **hợp đồng mà compiler dùng tại nơi gọi**. Dù đối tượng thực tế là `new MemoryReaderImpl()` không ném checked exception, đoạn `Reader r = new MemoryReaderImpl(); r.read();` vẫn được kiểm tra dựa trên chữ ký `Reader.read() throws IOException`.

Ví dụ:

~~~java
// Đoạn minh họa đặt trong một phương thức có import IOException.
Reader reader = new MemoryReaderImpl();
try {
    System.out.println(reader.read());
} catch (IOException ex) {
    System.out.println("Đọc thất bại");
}
~~~

Nếu thay kiểu biến thành `MemoryReaderImpl`, lời gọi `reader.read()` có chữ ký không khai báo checked exception, nên riêng lời gọi đó không còn tạo nghĩa vụ xử lý `IOException`.

## 9. Vị trí `throws` với generic method, varargs và abstract method

Mệnh đề `throws` luôn nằm **sau toàn bộ danh sách tham số**, kể cả khi phương thức có type parameters, varargs hoặc khai báo trong interface:

~~~java
import java.io.IOException;

class SyntaxExamples {
    public <T> T convert(T input) throws IOException {
        return input;
    }

    public void readAll(String... paths) throws IOException {
        for (String path : paths) {
            java.nio.file.Files.readString(java.nio.file.Path.of(path));
        }
    }
}

interface Operation {
    void run() throws IOException;
}
~~~

Các điểm dễ nhầm:

- `<T>` khai báo type parameter của phương thức. Nó đứng trước kiểu trả về; `throws` vẫn đứng sau dấu `)`.
- `String... paths` là varargs, vẫn nằm **bên trong** danh sách tham số.
- Chữ ký trong interface kết thúc bằng `;`; một phương thức có thân kết thúc phần khai báo `throws` rồi đến `{`.
- `throws` không nằm trong cặp ngoặc tham số, không nằm trong tên lớp ngoại lệ, và không dùng như annotation.

Java cũng hỗ trợ type variable trong `throws` theo các ràng buộc kiểu của Java; đây là trường hợp nâng cao. Khi viết API thông thường, khai báo các kiểu ngoại lệ cụ thể và có ý nghĩa vẫn dễ hiểu hơn.

## 10. Những giới hạn và lưu ý quan trọng

### 10.1. `throws` không yêu cầu một ngoại lệ phải thật sự được ném

Bạn có thể khai báo:

~~~java
// Mã minh họa trong một lớp.
static String version() throws java.io.IOException {
    return "1.0";
}
~~~

Dù đoạn hiện tại không ném `IOException`, cú pháp vẫn hợp lệ. Nhưng người gọi xét theo chữ ký đó vẫn có nghĩa vụ bắt hoặc khai báo checked exception khi biên dịch. Chỉ nên công bố ngoại lệ có ý nghĩa với hợp đồng hoặc khả năng mở rộng dự kiến; `throws` rộng một cách tùy tiện làm API bất tiện.

### 10.2. `throws` không được đặt vào một khối lệnh

Sai:

~~~java
// Minh họa không biên dịch.
static void work() {
    throws java.io.IOException;
}
~~~

Đúng: đặt `throws IOException` vào khai báo phương thức hoặc constructor. Trong phần thân, nếu cần ném, dùng `throw new IOException(...);`.

### 10.3. `throws` không xử lý hậu quả runtime

Ngay cả khi một phương thức khai báo `throws IOException`, thực thi chỉ chuyển sang đường lỗi **nếu thật sự có lời ném ngoại lệ** (do `throw` hay thao tác gọi phát sinh lỗi). Nếu không có `catch` phù hợp trên chuỗi lời gọi, ngoại lệ có thể thoát khỏi entry point và làm luồng kết thúc bất thường.

`throws` cũng không tự động ghi log, không rollback dữ liệu, không đóng tài nguyên, không biến checked exception thành unchecked và không tạo thông báo người dùng. Việc xử lý, dọn dẹp và quyết định khôi phục thuộc về cấu trúc điều khiển và thiết kế ở từng tầng.

### 10.4. `finally` và tài nguyên vẫn cần xử lý đúng

Khi phương thức/constructor khai báo `throws`, những tài nguyên đã được mở trước điểm thất bại cần được dọn dẹp an toàn; chỉ dùng `throws` không giải phóng chúng.

Trong I/O, ưu tiên `try-with-resources` để bảo đảm đóng `AutoCloseable` và bảo toàn thông tin lỗi phụ qua suppressed exceptions. Với đối tượng tạo dở do constructor ném ngoại lệ, Java không coi việc xây dựng đã thành công để trả đối tượng cho nơi gọi; constructor cần có trách nhiệm đối với tài nguyên mà nó đã cấp phát trước khi ném, vì nơi gọi chưa nắm được một tham chiếu hoàn chỉnh để đóng.

### 10.5. Quy tắc đối với constructor tĩnh, field initializer và initializer block

Không có khái niệm **static constructor** trong Java. Các static initializer không có danh sách tham số/chữ ký riêng để gắn `throws`; những quy tắc riêng về checked exception trong initializer không thể được giải quyết bằng cách viết `throws` sau `static { ... }`.

Trong instance initializer hoặc field initializer, ngoại lệ checked chịu quy tắc riêng gắn với các constructor có liên quan. Không nên cố đặt `throws` tại vị trí của field hoặc khối initializer; hãy xem xét thiết kế chuyển thao tác có thể lỗi vào constructor hoặc phương thức thích hợp và công bố ngoại lệ đúng vị trí.

## 11. Bảng lỗi cú pháp và biên dịch thường gặp

| Vấn đề | Vì sao lỗi hoặc bất tiện? | Cách sửa |
| --- | --- | --- |
| `void read() throw IOException` | Thiếu `s`: `throw` là câu lệnh, không phải khai báo danh sách lỗi. | Dùng `void read() throws IOException`. |
| `void read(throws IOException)` | `throws` sai vị trí trong cặp ngoặc tham số. | Đặt sau `)`: `read() throws IOException`. |
| `throws IOException | SQLException` | Dùng nhầm dấu `|` của multi-catch. | Phân cách bằng dấu phẩy. |
| Constructor khai báo `void ClassName()` | Trở thành method, không phải constructor. | Bỏ kiểu trả về. |
| Constructor lớp con gọi `super()` có checked exception nhưng không khai báo | Lời gọi constructor cha có thể để checked exception thoát. | Khai báo `throws` thích hợp ở constructor con hoặc thiết kế lại việc khởi tạo. |
| Phương thức gọi API khai báo `IOException` mà không bắt/khai báo | Compiler báo `unreported exception`. | Bắt đúng loại tại nơi xử lý được hoặc thêm `throws` hợp lý. |
| Override bổ sung `throws SQLException` khi interface chỉ có `IOException` | Vi phạm giới hạn checked exception của override. | Dùng loại checked nằm trong hợp đồng cha hoặc xử lý tại chỗ. |
| Ghi `throws Exception` ở mọi nơi | Che mờ loại thất bại, làm khó người gọi và khó giữ API ổn định. | Dùng kiểu cụ thể theo phạm vi trách nhiệm. |
| Chỉ ghi `throws IOException` rồi mong không cần dọn dẹp tài nguyên | Khai báo không thực hiện cleanup. | Dùng `try-with-resources`/`finally` đúng ngữ cảnh. |
| Ghi `throws RuntimeException` rồi tưởng người gọi buộc phải `catch` | Unchecked exception không thuộc yêu cầu catch-or-declare. | Hiểu đúng loại ngoại lệ; tài liệu hóa các lỗi quan trọng. |

## 12. Câu hỏi tự kiểm tra

1. **`throws` xuất hiện ở đâu trong chữ ký một phương thức?** — Sau dấu `)` của danh sách tham số, trước phần thân `{` hoặc dấu `;` của khai báo không có thân.
2. **Constructor có kiểu trả về không?** — Không. Khai báo `void ClassName()` tạo một phương thức có tên đó, không phải constructor.
3. **Nếu `A()` khai báo `throws IOException`, thì `new A()` ở phương thức gọi phải làm gì?** — Bắt `IOException` hoặc khai báo loại checked thích hợp tại phương thức gọi, trừ khi ngữ cảnh không thể phát sinh.
4. **Có thể viết `throws IOException, SQLException` không?** — Có. Danh sách phân tách bằng dấu phẩy.
5. **`throws IOException` có tự ném ngoại lệ không?** — Không. Đây chỉ là khai báo khả năng để lỗi thoát ra.
6. **Phương thức `throws IllegalArgumentException` có khiến `catch` trở thành bắt buộc không?** — Không. `IllegalArgumentException` là unchecked exception.
7. **Constructor lớp con phải làm gì khi `super()` khai báo `throws IOException`?** — Trên Java 17, constructor lớp con cần khai báo checked exception đó (hoặc kiểu cha phù hợp) nếu lời gọi có thể để nó thoát ra.
8. **Override một phương thức `throws IOException` có thể thêm `throws SQLException` không?** — Không, vì `SQLException` là checked exception khác nhánh, không nằm trong hợp đồng cha.
9. **`public static void main(String[] args) throws Exception` có hợp lệ không?** — Có. Nhưng ngoại lệ thoát khỏi `main` vẫn có thể khiến ứng dụng kết thúc bất thường.
10. **Một constructor chỉ trả về đối tượng hoàn chỉnh khi nào?** — Khi chuỗi khởi tạo thành công và constructor kết thúc bình thường; nếu ném ngoại lệ, thao tác tạo đối tượng không hoàn thành bình thường.

## Tài liệu tham khảo

- [Java Language Specification 17, §8.4.6 — Method Throws](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.6): cú pháp và quy tắc mệnh đề `throws` của phương thức.
- [Java Language Specification 17, §8.8.5 — Constructor Declarations](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.8.5): cú pháp constructor và khai báo ngoại lệ.
- [Java Language Specification 17, §8.8.7.1 — Explicit Constructor Invocations](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.8.7.1): lời gọi `this(...)`, `super(...)` trong constructor.
- [Java Language Specification 17, §11.2 — Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2): nghĩa vụ bắt hoặc khai báo checked exception.
- [Java Language Specification 17, §8.4.8.3 — Requirements in Overriding and Hiding](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.8.3): giới hạn checked exception khi override.
- [Java SE 17 API — Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html): ví dụ các phương thức I/O công bố `IOException`.

**Ghi nhớ:** với method viết `ReturnType name(params) throws ExceptionType { ... }`; với constructor viết `ClassName(params) throws ExceptionType { ... }` và **không có kiểu trả về**. `throws` công bố loại ngoại lệ có thể truyền ra; người gọi checked exception phải bắt hoặc khai báo, còn `throw` mới thực sự kích hoạt luồng ném ngoại lệ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="throws-clause-002">Khai báo một hoặc nhiều kiểu ngoại lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo một hoặc nhiều kiểu ngoại lệ với mệnh đề `throws` trong Java

## 1. Mục tiêu học tập

Một phương thức có thể hoàn thành bình thường hoặc kết thúc bằng ngoại lệ. Trong Java, mệnh đề `throws` của phương thức hoặc constructor cho người đọc và trình biên dịch biết những **kiểu ngoại lệ** mà lời gọi có thể truyền ra ngoài, đặc biệt là các *checked exception*.

Sau bài học, bạn có thể:

- Viết đúng cú pháp `throws` với **một** kiểu hoặc **nhiều** kiểu ngoại lệ, phân cách bằng dấu phẩy.
- Phân biệt mệnh đề khai báo `throws` với câu lệnh ném ngoại lệ `throw`.
- Hiểu cách trình biên dịch kiểm tra checked exception và cách bên gọi dùng `try/catch` hoặc tiếp tục khai báo `throws`.
- Chọn kiểu ngoại lệ cụ thể hay kiểu cha thích hợp, tránh khai báo quá rộng.
- Áp dụng `throws` trong phương thức, constructor, phương thức triển khai interface và phương thức ghi đè.
- Phân biệt danh sách `throws` với cú pháp `multi-catch`, đồng thời nhận diện một số lỗi biên dịch phổ biến.

Các ví dụ dùng Java 17+; nhiều ví dụ là chương trình độc lập có thể chạy bằng `javac` và `java`.

## 2. `throws` dùng để làm gì?

Trong khai báo phương thức, `throws` xuất hiện **sau danh sách tham số, trước thân phương thức**:

```java
public String load() throws java.io.IOException {
    // Thân phương thức.
    return "data";
}
```

Đây là một mảnh mã phương thức. Nó khai báo rằng phương thức **được phép truyền** `IOException` ra bên ngoài. Nó không có nghĩa mỗi lần gọi đều thực sự ném ngoại lệ. Việc không ném lỗi trong phần thân ví dụ trên vẫn hợp lệ.

Cú pháp tổng quát:

```text
[modifiers] ReturnType methodName(parameters)
        throws ExceptionType1, ExceptionType2, ... {
    // statements
}
```

Trong cú pháp trên, `ExceptionType1` và `ExceptionType2` là tên kiểu, không phải đối tượng. Từng kiểu phải là một lớp con của `Throwable` hoặc một biến kiểu có ràng buộc tương thích. Thực tế thường dùng tên lớp con của `Exception`; Java vẫn cho phép khai báo kiểu unchecked như `IllegalArgumentException`.

Ví dụ khai báo **một kiểu**:

```java
void readFile() throws java.io.IOException {
    // Đọc tệp; một lời gọi API bên trong có thể ném IOException.
}
```

Ví dụ khai báo **nhiều kiểu**:

```java
void process() throws java.io.IOException, java.text.ParseException {
    // Có thể thất bại khi đọc dữ liệu hoặc phân tích nội dung.
}
```

Hai đoạn vừa rồi là **các phương thức minh họa**, không phải chương trình `main` độc lập. Chúng cho thấy cách đặt các kiểu sau `throws`, cách nhau bằng dấu **phẩy**. Có thể xuống dòng giữa các kiểu để dễ đọc; xuống dòng không thay đổi ý nghĩa khai báo.

### Danh sách `throws` không phải danh sách các ngoại lệ chắc chắn sẽ phát sinh

- `throws IOException`: phương thức có thể để ngoại lệ tương thích với `IOException` truyền ra.
- `throws IOException, ParseException`: phương thức có thể truyền một ngoại lệ thuộc nhóm I/O hoặc nhóm phân tích dữ liệu (hoặc các ngoại lệ phù hợp với các kiểu đó) trên **những đường thực thi khác nhau**.
- Một lần ném bình thường sẽ truyền **một đối tượng `Throwable`** tại một thời điểm; danh sách `throws` không yêu cầu phương thức ném tất cả kiểu đã liệt kê.
- Không bắt buộc thứ tự ưu tiên giữa các kiểu trong `throws`; thay đổi vị trí tên lớp không thay đổi quy tắc kiểm tra ngoại lệ của trình biên dịch.
- Không có giới hạn bắt buộc phải liệt kê hết mọi `RuntimeException` hay `Error` có thể xảy ra.

## 3. Phân biệt `throw` và `throws`

Đây là hai vai trò khác nhau: `throws IOException, ParseException` **liệt kê hai kiểu lỗi có thể thoát** trong chữ ký, còn `throw new ParseException("invalid", 0);` **ném một đối tượng cụ thể** khi nhánh thực thi đi tới câu lệnh. `throws` không tạo đối tượng, không ghi log và không bắt lỗi.

Điểm quan trọng của bài này là **cú pháp liệt kê**: sau `throws` dùng dấu **phẩy** (`throws IOException, ParseException`); dấu `|` chỉ dùng trong multi-catch (`catch (IOException | ParseException ex)`). Bên gọi phải xử lý các checked exception có thể thoát theo hợp đồng, dù phần lớn lần chạy thành công. Phân biệt sâu hơn hành động ném và hợp đồng khai báo được thực hành riêng ở bài `throw` so với `throws`.

## 4. Vì sao checked exception phải được bắt hoặc khai báo?

Phân cấp liên quan:

```text
Throwable
├── Error                         (unchecked)
│   └── ...
└── Exception
    ├── IOException               (checked)
    │   └── FileNotFoundException (checked)
    ├── ParseException            (checked)
    └── RuntimeException          (unchecked)
        ├── IllegalArgumentException
        └── NullPointerException
```

Đây là sơ đồ rút gọn các lớp tiêu biểu. Một ngoại lệ là **checked** khi kiểu của nó không thuộc nhánh `RuntimeException` hoặc `Error` của `Throwable`.

Quy tắc cơ bản: nếu phần thân phương thức/constructor **có thể phát sinh checked exception** mà không được xử lý tại chỗ, khai báo `throws` của phương thức/constructor phải liệt kê kiểu đó hoặc một **kiểu cha** phù hợp.

Có hai lựa chọn ở bên gọi:

1. **Bắt và xử lý** bằng `try/catch` khi có khả năng phục hồi tại đây.
2. **Tiếp tục khai báo** bằng `throws` để đẩy trách nhiệm xét xử lý lên phương thức gọi tiếp theo.

Các ngoại lệ unchecked có thể được bắt và có thể được khai báo, nhưng trình biên dịch **không buộc** phải khai báo hay bắt chúng.

Về mặt kiểm tra ngoại lệ, lời gọi đến một phương thức được khai báo `throws IOException` được xem là có thể ném checked exception thuộc kiểu đó, dù một lần chạy cụ thể không bao giờ xảy ra lỗi. Vì vậy, mã bên gọi phải thỏa hợp đồng dựa trên **chữ ký được nhìn thấy khi biên dịch**.

## 5. Khai báo một kiểu: chương trình chạy hoàn chỉnh

Tạo tệp **`SingleThrowsDemo.java`**:

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

public class SingleThrowsDemo {
    static String firstLine(Reader source) throws IOException {
        try (BufferedReader reader = new BufferedReader(source)) {
            return reader.readLine();
        }
    }

    public static void main(String[] args) throws IOException {
        String result = firstLine(new StringReader("alpha\nbeta"));
        System.out.println(result);
    }
}
```

Lệnh biên dịch/chạy:

```bash
javac SingleThrowsDemo.java
java SingleThrowsDemo
```

`stdout` chính xác:

```text
alpha
```

Giải thích:

- `BufferedReader.readLine()` khai báo có thể ném `IOException`. Mặc dù nguồn là `StringReader` và ca thử chạy thành công, `firstLine` vẫn phải xử lý hoặc khai báo khả năng xảy ra checked exception từ phương thức API mà nó gọi.
- `firstLine(...) throws IOException` chọn **truyền trách nhiệm** xử lý lên bên gọi.
- `main(...) throws IOException` tiếp tục khai báo nên ví dụ vẫn biên dịch hợp lệ. Nếu checked exception thực sự thoát khỏi `main`, chương trình không có handler trong mã ví dụ này.
- `try-with-resources` đóng `BufferedReader` và nguồn được bọc sau khi sử dụng, kể cả khi quá trình đọc báo lỗi; một lỗi từ `close()` cũng thuộc hợp đồng kiểm tra ngoại lệ tương ứng.

Trường hợp muốn xử lý lỗi thay vì truyền tiếp:

```java
try {
    String line = firstLine(source);
    System.out.println(line);
} catch (java.io.IOException failure) {
    System.err.println("Cannot read data: " + failure.getMessage());
}
```

Đây chỉ là **mảnh minh họa**, giả định biến `source` đã được khai báo hợp lệ và phương thức `firstLine` có sẵn. `catch` nên có hành động hữu ích: trả kết quả thất bại, ghi nhận ngữ cảnh, phục hồi hoặc truyền lại nguyên nhân, tùy ứng dụng.

## 6. Khai báo nhiều kiểu: một phương thức có các đường lỗi khác nhau

Khi việc xử lý có nhiều khả năng thất bại cần phân biệt, danh sách `throws` giữ lại thông tin cụ thể cho người gọi.

Tạo tệp **`MultipleThrowsDemo.java`**:

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.text.ParseException;

public class MultipleThrowsDemo {
    static int readScore(Reader source)
            throws IOException, ParseException {
        try (BufferedReader reader = new BufferedReader(source)) {
            String line = reader.readLine();

            if (line == null) {
                throw new ParseException("missing score", 0);
            }

            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException failure) {
                ParseException parsed =
                        new ParseException("invalid score", 0);
                parsed.initCause(failure);
                throw parsed;
            }
        }
    }

    public static void main(String[] args) {
        try {
            int score = readScore(new StringReader("85"));
            System.out.println("valid=" + score);
        } catch (IOException | ParseException failure) {
            System.out.println("unexpected="
                    + failure.getClass().getSimpleName());
        }

        try {
            readScore(new StringReader("bad"));
        } catch (IOException | ParseException failure) {
            System.out.println("invalid="
                    + failure.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac MultipleThrowsDemo.java
java MultipleThrowsDemo
```

`stdout` chính xác:

```text
valid=85
invalid=ParseException
```

**Vì sao cần hai kiểu checked exception?**

- `reader.readLine()` và thao tác đóng reader có thể báo `IOException`.
- Một dòng trống hoặc chứa dữ liệu không phải số được phương thức này chủ động chuyển thành `ParseException`; trường hợp chuyển đổi thất bại giữ `NumberFormatException` ban đầu ở `getCause()`.
- Cả `IOException` và `ParseException` có thể đi ra khỏi `readScore`, nên khai báo đầy đủ `throws IOException, ParseException`.
- `main` dùng `catch (IOException | ParseException failure)` để bắt hai loại ở cùng một handler. Đây là **multi-catch**; dấu `|` chỉ hợp lệ trong `catch` thích hợp, **không** phải trong danh sách `throws`.

Ví dụ có tính quyết định: `StringReader("85")` cho dữ liệu hợp lệ, còn `StringReader("bad")` tạo `ParseException` như mô tả. Không phụ thuộc file, mạng hay quyền truy cập của máy.

### Khi cần xử lý hai kiểu bằng hai cách khác nhau

Đoạn mã minh họa dùng các phương thức đã khai báo phía trên:

```java
try {
    int score = readScore(source);
    saveScore(score);
} catch (ParseException failure) {
    reportInvalidInput(failure);
} catch (IOException failure) {
    reportReadFailure(failure);
}
```

Đây là **mảnh minh họa**, không phải chương trình chạy độc lập: cần khai báo `source`, `saveScore`, `reportInvalidInput` và `reportReadFailure`. Khi hành động phục hồi khác nhau, các `catch` riêng biệt thường dễ đọc hơn multi-catch.

## 7. Kiểu ngoại lệ khai báo phải bao phủ đường lỗi nào?

Giả sử một phương thức có thể phát sinh `FileNotFoundException`, là lớp con của `IOException`. Cả hai chữ ký sau đều có thể thỏa kiểm tra ngoại lệ:

```java
void loadA() throws java.io.FileNotFoundException {
    // ...
}

void loadB() throws java.io.IOException {
    // ...
}
```

Hai đoạn này là **chữ ký minh họa**. Khai báo kiểu cha bao phủ ngoại lệ con, nhưng cũng mở rộng hợp đồng đối với bên gọi. Cụ thể:

- `throws FileNotFoundException`: người gọi biết loại lỗi checked được khai báo tương đối hẹp.
- `throws IOException`: người gọi phải chuẩn bị cho nhiều loại lỗi thuộc nhóm I/O hơn.
- `throws Exception` hoặc `throws Throwable`: hợp pháp trong những trường hợp thích hợp, nhưng thường quá rộng đối với API ứng dụng vì giảm độ rõ ràng và khiến bên gọi phải xử lý kiểu rộng.
- Khai báo cả `throws FileNotFoundException, IOException` có thể hợp lệ nhưng **dư thừa** về khả năng checked exception vì `IOException` đã bao phủ `FileNotFoundException`.
- Không cần thêm `RuntimeException` vào danh sách chỉ để chương trình biên dịch.

**Quy tắc thiết kế:** ưu tiên danh sách những kiểu checked exception có ý nghĩa độc lập với bên gọi; dùng kiểu cha khi API thật sự muốn cho phép họ lớp con đó. Không thêm các kiểu lỗi chỉ để “phòng hờ” vô căn cứ.

### Danh sách khai báo không đồng nghĩa cơ chế xử lý

`throws` không tự tạo retry, fallback, ghi log hay rollback. Khi checked exception xảy ra, nếu thân phương thức không bắt được nó, việc thực thi hoàn thành đột ngột và lỗi lan lên bên gọi. Nếu không có handler tương thích trong chuỗi gọi, nó có thể đi đến handler lỗi không được bắt của luồng.

Cũng đừng mặc định rằng một kiểu ngoại lệ đã khai báo chắc chắn xuất hiện trong từng lời gọi. Khai báo chỉ là hợp đồng về **khả năng** phát sinh.

## 8. `throws` trong constructor

Constructor cũng liệt kê **một hoặc nhiều loại checked exception**, ngăn bằng dấu phẩy, khi khởi tạo phải đọc hoặc kiểm tra dữ liệu. Ví dụ khai báo một constructor xử lý cả I/O và sai định dạng:

```java
// Đặt trong lớp Config, với java.io.Reader được caller cung cấp.
Config(java.io.Reader source)
        throws java.io.IOException, java.text.ParseException {
    int first = source.read();
    if (first != '{') {
        throw new java.text.ParseException("expected '{'", 0);
    }
}
```

Đây là **mảnh constructor minh họa**, không phải tệp Java độc lập. `source.read()` có thể ném `IOException`; phép kiểm tra định dạng có thể ném `ParseException`. Nơi gọi `new Config(reader)` phải bắt hoặc khai báo **cả hai** kiểu checked exception. Constructor không có kiểu trả về, và `throws` đặt sau danh sách tham số, trước thân. Nếu constructor ném, lời gọi `new` không trả đối tượng đã khởi tạo thành công; phương thức gọi không được tự xem thất bại như một kết quả hợp lệ.

Với constructor phức tạp, quản lý resource và cleanup đúng cách; ví dụ này không đóng `Reader` vì ownership của nguồn đầu vào vẫn thuộc caller.

## 9. `throws` và ghi đè phương thức (`override`)

Nếu interface công bố **nhiều loại checked exception**, implementation có thể khai báo tập hẹp hơn, không được thêm loại checked rộng hơn/không tương thích. Ví dụ:

```java
// Hai khai báo minh họa, cần đặt trong tệp Java thích hợp.
interface DocumentSource {
    String load() throws java.io.IOException, java.text.ParseException;
}

class ParsedSource implements DocumentSource {
    @Override
    public String load() throws java.text.ParseException {
        return "valid"; // Triển khai này không phát sinh lỗi I/O.
    }
}
```

`ParsedSource.load()` bỏ `IOException`, giữ `ParseException`; đây là cách thu hẹp hợp đồng hợp lệ. Thay bằng `throws Exception` sẽ không hợp lệ vì mở rộng phạm vi checked exception. Tuy nhiên, khi lời gọi đi qua biến có kiểu tĩnh `DocumentSource`, compiler vẫn áp dụng **hai** loại checked exception của interface, không dựa vào implementation có thể được chọn lúc chạy.

Điểm riêng của danh sách nhiều kiểu là việc thu hẹp có thể bỏ **một nhánh lỗi** hoặc dùng subtype hợp lệ của nhánh đó. Không phải mọi override đều phải sao chép nguyên danh sách `throws` của interface.

## 10. Mệnh đề `throws` trong các chữ ký API

Mệnh đề này có thể xuất hiện trong:

- Phương thức thường, kể cả `static`.
- Phương thức `abstract` và khai báo phương thức của interface.
- Phương thức `default` của interface.
- Constructor, bao gồm constructor có phạm vi `public`, `protected` hoặc package-private.
- Phương thức có tham số kiểu tổng quát; trong những trường hợp nâng cao, loại ngoại lệ cũng có thể là một **biến kiểu** bị chặn trên bởi `Throwable`.

Ví dụ khai báo hợp đồng:

```java
interface Repository {
    byte[] read(String key) throws java.io.IOException;
}

abstract class Importer {
    abstract void importData() throws java.io.IOException,
            java.text.ParseException;
}
```

Đây là hai khai báo kiểu hợp lệ để minh họa chữ ký; chúng không chứa `main`. Những phương thức không có thân này vẫn dùng `throws` để công bố trách nhiệm xử lý checked exception cho bên triển khai và bên gọi.

### `throws` không thuộc phần phân biệt overload

Không thể tạo hai phương thức chỉ khác danh sách `throws`:

```java
// Không hợp lệ nếu đặt cả hai trong cùng một lớp:
void execute() throws java.io.IOException { }
void execute() throws java.text.ParseException { }
```

Đây là **ví dụ sai có chủ ý**: Java không xem hai chữ ký là hai overload khác nhau chỉ vì mệnh đề `throws` khác nhau. Hãy phân biệt phương thức bằng tên hoặc tham số khi thật sự có hai thao tác khác nhau.

### `throws` không làm thay đổi kiểu trả về

Một phương thức `int parse() throws ParseException` vẫn có kiểu trả về `int` trên đường thành công. Nếu lỗi được ném, không có giá trị `int` trả về cho lời gọi đó; luồng điều khiển đi theo cơ chế ngoại lệ. `throws` không phải kiểu union hoặc một wrapper kiểu `Result`.

## 11. `throws` và biểu thức lambda

Thân lambda cũng phải tương thích với tập checked exception mà **phương thức chức năng đích** cho phép.

Ví dụ hợp lệ bằng interface tự định nghĩa:

```java
import java.io.IOException;

@FunctionalInterface
interface IoAction {
    void run() throws IOException;
}

public class LambdaThrowsDemo {
    public static void main(String[] args) throws IOException {
        IoAction action = () -> {
            System.out.println("action completed");
        };
        action.run();
    }
}
```

Lưu thành **`LambdaThrowsDemo.java`** rồi chạy:

```bash
javac LambdaThrowsDemo.java
java LambdaThrowsDemo
```

`stdout`:

```text
action completed
```

Nếu thân lambda thực sự ném `IOException`, nó hợp lệ với `IoAction` vì phương thức chức năng cho phép `throws IOException`. Nhưng một lambda được gán cho `Runnable` không thể tự ném checked `IOException` ra ngoài thân, vì `Runnable.run()` không khai báo checked exception.

Điều này giải thích vì sao thêm `throws IOException` vào **phương thức bao ngoài** chưa đủ để cho một lambda kiểu `Runnable` ném checked exception không tương thích: quy tắc kiểm tra của thân lambda phụ thuộc kiểu chức năng mà lambda được gán vào.

## 12. Các lỗi cú pháp và thiết kế thường gặp

| Viết sai / quan niệm sai | Vấn đề | Cách đúng |
| --- | --- | --- |
| `void f() throw IOException` | `throw` không dùng để khai báo | `void f() throws IOException` |
| `void f() throws new IOException()` | `throws` cần kiểu, không phải đối tượng | `void f() throws IOException` |
| `void f() throws IOException \| ParseException` | Dùng nhầm dấu `|` của multi-catch | `void f() throws IOException, ParseException` |
| `throw IOException;` | `throw` cần biểu thức có kiểu `Throwable` | `throw new IOException();` hoặc ném lại biến đã bắt |
| Gọi phương thức `throws IOException` mà không bắt/khai báo | Không thỏa checked exception | Dùng `catch (IOException e)` hoặc khai báo `throws IOException` |
| `throws Exception` cho mọi lỗi | Làm hợp đồng rộng và buộc xử lý chung | Ưu tiên các kiểu checked cụ thể có ý nghĩa |
| Chỉ thêm `throws` rồi coi như đã xử lý sự cố | Ngoại lệ vẫn có thể lan ra ngoài | Xác định ranh giới ứng dụng chịu trách nhiệm xử lý |
| Thêm checked exception rộng hơn khi `override` | Có thể vi phạm hợp đồng phương thức cha | Giữ nguyên, thu hẹp hoặc bỏ checked exception |
| Cho rằng danh sách `throws` bảo đảm kiểu lỗi sẽ xuất hiện | Khai báo chỉ mô tả khả năng | Kiểm tra đường thực thi và nguyên nhân thực tế |
| Nghĩ `throws` bắt buộc cho mọi `RuntimeException` | Unchecked exception không bị kiểm tra bắt buộc | Có thể không khai báo, nhưng tài liệu API vẫn nên rõ |

Một trường hợp nữa: trong `catch (FileNotFoundException | IOException e)`, hai lựa chọn nằm cùng cây kế thừa nên multi-catch không hợp lệ vì có quan hệ cha-con. Trong **danh sách `throws`**, việc liệt kê cả hai kiểu lại không mang ý nghĩa khác biệt hữu ích vì `IOException` đã bao phủ `FileNotFoundException`. Cú pháp và quy tắc của `throws` và `catch` cần được xét riêng.

## 13. Cách viết hợp đồng ngoại lệ rõ ràng

### 13.1. Chọn kiểu mô tả đúng nguồn lỗi

Ví dụ một hàm đọc dữ liệu và giải mã định dạng: `throws IOException, ParseException` giúp người gọi phân biệt lỗi truy cập dữ liệu và lỗi phân tích nội dung. Nếu chỉ dùng `throws Exception`, người gọi mất bớt khả năng biết cần bắt loại cụ thể nào mà không phải kiểm tra sâu hơn.

### 13.2. Giữ nguyên nguyên nhân khi chuyển tầng

Nếu bạn cần chuyển `NumberFormatException` thành một checked `ParseException`, hãy lưu ngoại lệ ban đầu dưới dạng `cause` như ví dụ `MultipleThrowsDemo`. Stack trace và nguyên nhân gốc giúp phân tích khi nhiều tầng API cùng truyền ngoại lệ.

### 13.3. Không dùng `throws` để thay kiểm tra đầu vào

Hợp đồng `throws IllegalArgumentException` có thể là tài liệu hữu ích, nhưng vẫn phải kiểm tra đầu vào phù hợp ở chỗ cần thiết. Tự thêm tên kiểu vào chữ ký không tạo ra câu lệnh kiểm tra.

### 13.4. Sử dụng `@throws` trong JavaDoc

`@throws IOException` là **thẻ tài liệu**, giải thích điều kiện lỗi. Nó không thay thế mệnh đề `throws` trong khai báo phương thức khi checked exception cần được khai báo. Khi xây dựng API public, nên dùng cả chữ ký chính xác và tài liệu giải thích điều kiện thất bại.

Ví dụ chữ ký kèm JavaDoc:

```java
/**
 * Đọc cấu hình từ nguồn cung cấp.
 *
 * @param source nguồn dữ liệu
 * @return nội dung cấu hình
 * @throws java.io.IOException nếu thao tác đọc thất bại
 * @throws java.text.ParseException nếu nội dung không hợp lệ
 */
String loadConfig(java.io.Reader source)
        throws java.io.IOException, java.text.ParseException {
    // Phần thân minh họa được giản lược.
    return "";
}
```

Đây là **mảnh phương thức minh họa** về tài liệu API và chữ ký. Trong sản phẩm thật, phần thân phải thực hiện công việc đã cam kết; không dùng bản giả lập để thay logic đọc/parse.

## 14. Câu hỏi tự kiểm tra

1. **Cú pháp một kiểu checked exception là gì?** Ví dụ: `void load() throws IOException`.
2. **Hai kiểu được phân cách thế nào?** Bằng dấu phẩy: `throws IOException, ParseException`.
3. **`throws` có thực sự ném đối tượng ngoại lệ không?** Không. Câu lệnh `throw` hoặc thao tác phát sinh lỗi tại runtime mới thực hiện việc ném.
4. **Phương thức khai báo `throws IOException` nhưng một lần chạy không ném lỗi có hợp lệ không?** Có.
5. **Gọi API có checked exception mà không bắt, bạn phải làm gì?** Khai báo tiếp kiểu được phép truyền lên bằng `throws` hoặc kiểu cha tương thích.
6. **`throws IOException | ParseException` có đúng không?** Không. Dấu `|` dành cho multi-catch; `throws` dùng dấu phẩy.
7. **`RuntimeException` bắt buộc phải khai báo trong `throws`?** Không. Nó là unchecked.
8. **Một phương thức triển khai interface `throws IOException` có thể bỏ `throws` không?** Có, nếu phần thân hợp lệ và không truyền checked exception không được phép.
9. **Có thể overload hai phương thức chỉ khác mệnh đề `throws`?** Không.
10. **`throws IOException, ParseException` nghĩa là cả hai được ném cùng lúc?** Không. Mỗi lần ném truyền một đối tượng ngoại lệ; hai kiểu biểu diễn các khả năng.

## 15. Tài liệu chính thức (Java 17)

- [Java Language Specification §8.4.6 — Method Throws](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.6): cú pháp và quy tắc khai báo kiểu ngoại lệ.
- [Java Language Specification §8.8.5 — Constructor Throws](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.8.5): mệnh đề `throws` trong constructor.
- [Java Language Specification §11.2 — Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2): kiểm tra checked exception.
- [Java Language Specification §8.4.8.3 — Requirements in Overriding and Hiding](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.8.3): giới hạn checked exception khi ghi đè.
- [Java Language Specification §14.20 — The try Statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20): `catch` và multi-catch.
- [Java SE 17 API — IOException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/IOException.html), [ParseException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/ParseException.html): các kiểu checked exception trong ví dụ.

**Điểm cần nhớ:** Sau `throws` là **một hoặc nhiều kiểu ngoại lệ**, ngăn cách bằng dấu phẩy. Mệnh đề này công bố hợp đồng để trình biên dịch và bên gọi xác định trách nhiệm với checked exception; nó không tự tạo, bắt hoặc sửa một lỗi nào.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="throws-clause-003">Quan hệ giữa kiểu được khai báo và lớp con thực sự bị ném</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ giữa kiểu ngoại lệ khai báo trong `throws` và lớp con thực sự được ném trong Java

Một phương thức Java có thể khai báo `throws IOException` nhưng khi chạy lại ném một đối tượng `FileNotFoundException` hoặc `EOFException`. Điều đó hoàn toàn hợp lệ vì các loại này là **lớp con của `IOException`**. Mệnh đề `throws` mô tả **hợp đồng ngoại lệ checked mà người gọi phải tính đến khi biên dịch**, còn đối tượng được ném tại runtime vẫn có **kiểu thực tế** của chính nó.

Ví dụ ngắn:

```java
static void load() throws java.io.IOException {
    throw new java.io.FileNotFoundException("config.txt");
}
```

Đây là một phương thức minh họa, cần đặt trong lớp Java. `FileNotFoundException extends IOException`, nên `throws IOException` đủ để trình biên dịch chấp nhận ngoại lệ checked này được lan ra ngoài. Ở người gọi:

```java
try {
    load();
} catch (java.io.IOException ex) {
    System.out.println(ex.getClass().getSimpleName());
}
```

Kết quả in là `FileNotFoundException`, **không phải `IOException`**. Handler `catch (IOException)` bắt được đối tượng kiểu con, nhưng việc bắt không biến đối tượng thành một `IOException` thuần túy.

Để hiểu đúng cần tách ba lớp thông tin:

1. **Kiểu ngoại lệ được khai báo (`declared exception type`):** kiểu xuất hiện sau `throws` ở chữ ký phương thức.
2. **Kiểu ngoại lệ được ném thực tế (`actual runtime exception type`):** lớp của đối tượng `Throwable` đi ra từ câu lệnh `throw` hoặc API được gọi.
3. **Kiểu ngoại lệ mà caller bắt (`catch parameter type`):** kiểu trong nhánh `catch` của người gọi; handler phù hợp nếu đối tượng thực tế thuộc kiểu đó hoặc một lớp con của nó.

## 1. `throws` là hợp đồng tại thời điểm biên dịch

Một khai báo phương thức có dạng:

```java
static String read(String path) throws java.io.IOException {
    return java.nio.file.Files.readString(
            java.nio.file.Path.of(path));
}
```

Đây là phương thức minh họa, phụ thuộc tệp thật khi chạy. Phần `throws IOException` cho biết phương thức được phép để `IOException` hoặc những lớp con checked của nó truyền ra. Với một lời gọi có thể ném `IOException`, trình biên dịch yêu cầu mã gọi phải **bắt (`catch`) hoặc tiếp tục khai báo (`throws`)** ngoại lệ checked đó.

Ví dụ caller hợp lệ:

```java
static String readAtService(String path)
        throws java.io.IOException {
    return read(path);
}
```

Hoặc caller có handler:

```java
static void printFile(String path) {
    try {
        System.out.println(read(path));
    } catch (java.io.IOException ex) {
        System.out.println("Không đọc được tệp");
    }
}
```

Các đoạn này là phương thức minh họa và phải đặt trong cùng phạm vi có thể gọi `read`.

**Điểm cần nhớ:** khai báo `throws IOException` không tự ném gì cả. Một lời gọi có thể trả về bình thường. Mệnh đề này cũng không hứa rằng ngoại lệ tại runtime phải có **đúng lớp** `IOException`; lớp con như `NoSuchFileException` hay `FileNotFoundException` đều có thể xuất hiện nếu thao tác tương ứng thực sự ném chúng.

Đồng thời, `throws` **không phải danh sách đầy đủ của mọi ngoại lệ có thể xảy ra lúc chạy**: ngoại lệ unchecked như `IllegalArgumentException`, `NullPointerException` và các `Error` có thể thoát ra mà không cần xuất hiện trong mệnh đề `throws`.

## 2. Tại sao khai báo kiểu cha lại bao phủ lớp con?

Trong Java, quan hệ kế thừa cho phép một đối tượng thuộc lớp con được sử dụng ở vị trí yêu cầu lớp cha. Quy tắc này cũng áp dụng với ngoại lệ.

Một phần cây điển hình:

```text
Throwable
├── Error
└── Exception
    ├── IOException
    │   ├── FileNotFoundException
    │   └── EOFException
    └── RuntimeException
        ├── IllegalArgumentException
        │   └── NumberFormatException
        └── IllegalStateException
```

Cây chỉ liệt kê một số lớp; nhiều lớp ngoại lệ khác không được biểu diễn.

Với cây này:

- `throws IOException` có thể bao phủ `FileNotFoundException` và `EOFException`.
- `throws Exception` có thể bao phủ `IOException` và nhiều ngoại lệ checked khác thuộc `Exception`; nó thường **quá rộng** nếu API chỉ có thể phát sinh lỗi I/O có ý nghĩa.
- `throws FileNotFoundException` **không** bao phủ một `IOException` thông thường, vì `IOException` là **kiểu cha**, không phải con của `FileNotFoundException`.
- `throws RuntimeException` là cú pháp hợp lệ, nhưng không cần thiết để thỏa quy tắc checked exception vì `RuntimeException` và các lớp con là unchecked.
- `throws Exception` **không mô tả `Error`**: `Error` là nhánh khác của `Throwable`. Dù vậy `Error` vẫn có thể xảy ra mà không phải khai báo.

### Chiều quan hệ quan trọng

Giả sử `ChildException extends ParentException`:

```java
class ParentException extends Exception {}
class ChildException extends ParentException {}

static void childOperation() throws ParentException {
    throw new ChildException();
}
```

Đoạn mã là các khai báo minh họa cần đặt trong một lớp hoặc tổ chức tệp Java hợp lệ. Nó biên dịch được: phương thức khai báo kiểu cha checked và ném kiểu con.

Chiều ngược lại:

```java
static void invalidOperation() throws ChildException {
    throw new ParentException();
}
```

Đoạn trên **cố ý không biên dịch**: `ParentException` không phải là một `ChildException`, và ngoại lệ checked kiểu cha được ném không được bao phủ bởi khai báo chỉ cho phép kiểu con. Sửa bằng cách đổi khai báo thành `throws ParentException`, hoặc chỉ ném kiểu con, hoặc bắt lỗi trong phương thức.

Nói cách khác, với ngoại lệ checked **được phép thoát khỏi thân phương thức**, kiểu thực tế phát sinh theo các đường chạy phải có khả năng được khai báo/bắt theo một kiểu phù hợp. Việc caller thấy kiểu cha trong hợp đồng **không ép** runtime tạo một đối tượng cha.

## 3. Phân biệt ba thời điểm: khai báo, ném và bắt

| Giai đoạn | Mã liên quan | Điều gì được xác định? |
| --- | --- | --- |
| Khai báo API | `void load() throws IOException` | Loại checked exception mà caller phải chuẩn bị theo kiểu tĩnh của lời gọi |
| Thao tác thực tế | `throw new FileNotFoundException("...")` | Đối tượng lỗi mang kiểu runtime `FileNotFoundException` |
| Xử lý của caller | `catch (IOException ex)` | Handler được chọn vì `FileNotFoundException` là subtype của `IOException` |

Ở thời điểm chạy, Java không thay đối tượng `FileNotFoundException` thành `IOException` chỉ vì chữ ký khai báo kiểu cha. Do đó:

```java
if (ex instanceof java.io.FileNotFoundException) {
    // Đây là một lỗi FileNotFoundException thực sự.
}
```

Có thể dùng `instanceof` khi cần phân loại lỗi trong handler tổng quát. Nếu các kiểu lỗi đòi hỏi hành động khác nhau, các nhánh `catch` cụ thể thường dễ đọc hơn:

```java
try {
    readDocument();
} catch (java.io.FileNotFoundException ex) {
    // Xử lý riêng khi thao tác thực sự ném FileNotFoundException.
} catch (java.io.IOException ex) {
    // Xử lý các IOException còn lại.
}
```

Đây là cấu trúc minh họa: `readDocument()` cần là phương thức có khai báo/lời gọi phù hợp. `FileNotFoundException` phải đứng **trước** `IOException` vì nhánh cha đứng trước sẽ bắt luôn lỗi con, khiến nhánh con phía sau không thể đến được.

## 4. Ví dụ Java 17+ hoàn chỉnh: `throws IOException`, runtime ném nhiều loại

Lưu tệp **`DeclaredVsActualExceptionDemo.java`**:

```java
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;

public class DeclaredVsActualExceptionDemo {

    // Một hợp đồng checked tổng quát cho các thất bại I/O.
    static void load(String scenario) throws IOException {
        switch (scenario) {
            case "missing" ->
                    throw new FileNotFoundException("settings.properties");
            case "short" ->
                    throw new EOFException("incomplete content");
            case "other" ->
                    throw new IOException("read failed");
            case "state" ->
                    throw new IllegalStateException("resource is closed");
            default ->
                    System.out.println("load: success");
        }
    }

    static void caller(String scenario) {
        System.out.println("CASE " + scenario);
        try {
            load(scenario);
            System.out.println("caller: completed");
        } catch (IOException ex) {
            System.out.println("caught as: IOException");
            System.out.println(
                    "actual type: " + ex.getClass().getSimpleName());
            System.out.println("message: " + ex.getMessage());
        } catch (IllegalStateException ex) {
            System.out.println(
                    "unchecked type: " + ex.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        caller("ok");
        caller("missing");
        caller("short");
        caller("other");
        caller("state");
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 DeclaredVsActualExceptionDemo.java
java DeclaredVsActualExceptionDemo
```

**Stdout xác định:**

```text
CASE ok
load: success
caller: completed
CASE missing
caught as: IOException
actual type: FileNotFoundException
message: settings.properties
CASE short
caught as: IOException
actual type: EOFException
message: incomplete content
CASE other
caught as: IOException
actual type: IOException
message: read failed
CASE state
unchecked type: IllegalStateException
```

### Phân tích kết quả

**`missing`:** phương thức khai báo `throws IOException`, nhưng đối tượng do `throw new FileNotFoundException(...)` tạo có lớp thực tế `FileNotFoundException`. Handler khai báo `IOException` bắt được vì quan hệ kế thừa. Phương thức `getClass().getSimpleName()` vẫn trả về `FileNotFoundException`.

**`short`:** cùng chữ ký `throws IOException` nhưng đối tượng thực tế là `EOFException`, một lớp con khác của `IOException`. Caller không cần biết trước **chính xác** một trong hai lớp con sẽ xuất hiện để biên dịch và xử lý mọi lỗi I/O bằng handler `IOException`.

**`other`:** trường hợp này đối tượng thực sự là `IOException`; kiểu khai báo và runtime tình cờ trùng nhau.

**`state`:** `IllegalStateException` không nằm dưới `IOException`; nó là `RuntimeException` unchecked. Java vẫn cho phép ném nó dù `load` chỉ khai báo `throws IOException`. Handler `catch (IOException)` không bắt nó; nhánh `catch (IllegalStateException)` được chọn.

**`ok`:** `load` không ném lỗi và caller in `"completed"`. Khai báo `throws` chỉ mô tả khả năng, không đảm bảo sẽ có ngoại lệ.

Ví dụ mô phỏng bằng các câu `throw` trực tiếp nên không phụ thuộc đường dẫn thực hay lỗi đặc thù của hệ điều hành.

## 5. Caller phải xử lý theo kiểu khai báo, có thể phân nhánh theo kiểu runtime

Khi viết một lời gọi, trình biên dịch xem **kiểu tĩnh của phương thức được gọi** và các ngoại lệ checked được khai báo/được phân tích theo quy tắc ngôn ngữ. Với:

```java
static void load() throws java.io.IOException {
    // Có thể thực hiện đọc dữ liệu và phát sinh lỗi I/O.
}
```

Một caller thực tế phải xử lý hoặc khai báo `IOException`, chẳng hạn:

```java
static void operation() throws java.io.IOException {
    load();
}
```

Nếu bỏ `throws IOException` trong `operation()` mà không thêm `catch`, trình biên dịch báo lỗi ngoại lệ checked chưa được bắt/khai báo. Nó không cần và thường không thể chứng minh trước chính xác lớp con nào sẽ được ném trên mọi lần chạy của `load`.

### Có nên bắt riêng lớp con?

Nếu caller biết cách xử lý khác nhau, có thể đặt handler theo thứ tự con → cha:

```java
try {
    load();
} catch (java.io.FileNotFoundException ex) {
    // Có thể báo thiếu tài nguyên hoặc chọn đường dẫn thay thế.
} catch (java.io.IOException ex) {
    // Xử lý các lỗi I/O khác theo hợp đồng chung.
}
```

Ở đây `load()` được giả định có thể phát sinh `IOException`. Mặc dù chữ ký khai báo `IOException`, một nhánh `FileNotFoundException` riêng vẫn có ý nghĩa vì lớp con đó có thể được ném tại runtime. Thứ tự `catch` được kiểm tra theo quan hệ kế thừa; không đặt `catch (IOException)` trước nhánh `FileNotFoundException`.

Nếu tất cả lỗi I/O đều được xử lý cùng một cách, chỉ một `catch (IOException)` sẽ ngắn gọn hơn. Không cần tạo danh sách handler cho mọi lớp con chỉ vì chúng tồn tại trong JDK.

### Nếu chỉ bắt lớp con thì đã xử lý đủ chưa?

Không, nếu phương thức có thể ném cả các loại `IOException` khác. Ví dụ:

```java
static void tooNarrow() {
    try {
        load();
    } catch (java.io.FileNotFoundException ex) {
        System.out.println("File not found");
    }
}
```

Đây là **ví dụ cố ý không biên dịch** nếu `load()` có khai báo `throws IOException` như trên: một `IOException` khác ngoài `FileNotFoundException` vẫn có thể lan ra, nhưng `tooNarrow()` không bắt hoặc khai báo nó. Để sửa, thêm `catch (IOException)` sau nhánh con hoặc khai báo `throws IOException` trên `tooNarrow`.

## 6. Kiểu khai báo càng rộng, hợp đồng caller càng rộng

Giả sử cùng một phương thức chỉ thật sự cần báo lỗi thiếu tệp:

```java
static void precise() throws java.io.FileNotFoundException {
    throw new java.io.FileNotFoundException("missing.txt");
}
```

Ở đây `throws FileNotFoundException` cho caller hợp đồng checked hẹp. Caller có thể bắt `FileNotFoundException` hoặc khai báo một kiểu cha phù hợp như `IOException`.

Trong khi đó:

```java
static void broad() throws Exception {
    throw new java.io.FileNotFoundException("missing.txt");
}
```

`throws Exception` cũng hợp lệ nhưng làm caller phải xem xét phạm vi checked exception rộng hơn. Nếu caller chỉ có `catch (IOException)`, khai báo `throws Exception` còn có thể khiến mã gọi không biên dịch vì các kiểu checked `Exception` khác vẫn được xem là có thể thoát ra.

| Kiểu khai báo | Loại checked exception được cho phép thoát ra | Tác động tới người gọi |
| --- | --- | --- |
| `throws FileNotFoundException` | `FileNotFoundException` và lớp con của nó | Hợp đồng hẹp, rõ lỗi chuyên biệt |
| `throws IOException` | `IOException` và các lớp con | Caller xử lý nhóm lỗi I/O |
| `throws Exception` | Các checked exception thuộc `Exception` | Rộng, caller khó biết chính xác nhóm sự cố |
| Không khai báo checked exception | Không được cho checked exception thoát ra không được bắt theo cách thông thường | Caller không bị buộc xử lý checked exception của phương thức này |
| `throws IOException, SQLException` | Hai nhánh checked exception độc lập | Caller xem xét cả lỗi I/O và SQL |

Mục tiêu không phải lúc nào cũng khai báo **lớp lá hẹp nhất**. Một API đọc cấu hình có thể ném nhiều lớp con `IOException`; khai báo `throws IOException` thường là mức trừu tượng đúng. Ngược lại, `throws Exception` làm mất nhiều giá trị của hợp đồng nếu phương thức chỉ có lỗi I/O.

Hãy chọn kiểu khai báo theo **nhóm lỗi mà caller có thể xử lý hợp lý**, khả năng thay đổi triển khai và sự ổn định của API.

## 7. Ghi đè (`override`): phương thức con được khai báo checked exception hẹp hơn

Quan hệ cha/con còn ràng buộc **chữ ký của phương thức ghi đè**. Giả sử một interface mô tả nguồn tài liệu, có thể xảy ra lỗi I/O:

```java
interface DocumentSource {
    String fetch(String name) throws java.io.IOException;
}
```

Lớp triển khai có thể chỉ ném ngoại lệ hẹp hơn:

```java
class LocalDocumentSource implements DocumentSource {
    @Override
    public String fetch(String name)
            throws java.io.FileNotFoundException {
        if (name.isBlank()) {
            throw new java.io.FileNotFoundException("name is blank");
        }
        return "document";
    }
}
```

Đây là các khai báo minh họa, phải đặt trong package/tệp Java hợp lệ. `FileNotFoundException` là subtype của `IOException`, nên cách override này được phép.

Quy tắc checked exception khi override:

- Phương thức ghi đè có thể **không khai báo checked exception**, nếu triển khai xử lý mọi checked exception bên trong.
- Có thể khai báo **kiểu checked hẹp hơn** thuộc phạm vi ngoại lệ mà phương thức cha/interface cho phép.
- Không được mở rộng bằng một checked exception mới hoặc rộng hơn mà phương thức cha không cho phép, chẳng hạn từ `throws IOException` sang `throws Exception`.
- Có thể ném **unchecked exception** bất kể việc phương thức cha có khai báo hay không; ràng buộc trên áp dụng cho checked exception.
- Nếu một lớp thực hiện nhiều interface có cùng phương thức nhưng hợp đồng checked exception khác nhau, triển khai phải thỏa đồng thời các hợp đồng liên quan; không tự lấy hợp của tất cả loại checked exception để khai báo tùy ý.

### Ví dụ Java 17+ hoàn chỉnh: interface và lớp triển khai

Lưu tệp **`OverrideThrowsDemo.java`**:

```java
import java.io.FileNotFoundException;
import java.io.IOException;

public class OverrideThrowsDemo {

    interface DocumentSource {
        String fetch(String name) throws IOException;
    }

    static final class LocalDocumentSource implements DocumentSource {
        @Override
        public String fetch(String name)
                throws FileNotFoundException {
            if ("missing".equals(name)) {
                throw new FileNotFoundException(name + ".txt");
            }
            return "loaded:" + name;
        }
    }

    static void useInterface(DocumentSource source) {
        try {
            System.out.println(source.fetch("missing"));
        } catch (IOException ex) {
            System.out.println(
                    "interface caller catches: "
                    + ex.getClass().getSimpleName());
        }
    }

    static void useConcrete(LocalDocumentSource source) {
        try {
            System.out.println(source.fetch("missing"));
        } catch (FileNotFoundException ex) {
            System.out.println(
                    "concrete caller catches: "
                    + ex.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) throws FileNotFoundException {
        LocalDocumentSource source = new LocalDocumentSource();
        useInterface(source);
        useConcrete(source);
        System.out.println(source.fetch("found"));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 OverrideThrowsDemo.java
java OverrideThrowsDemo
```

**Stdout:**

```text
interface caller catches: FileNotFoundException
concrete caller catches: FileNotFoundException
loaded:found
```

**Vì sao hai caller khác hợp đồng nhưng cùng thấy một loại ngoại lệ?**

- `useInterface` nhận biến `DocumentSource`. Theo **kiểu tĩnh của lời gọi**, chữ ký `fetch` của interface có `throws IOException`; vì thế handler `IOException` phù hợp cho kiểm tra biên dịch.
- `useConcrete` nhận biến `LocalDocumentSource`. Trình biên dịch nhìn thấy khai báo override hẹp hơn `throws FileNotFoundException`, nên bắt riêng lớp con là đủ đối với checked exception này.
- Cả hai cùng gọi triển khai `LocalDocumentSource.fetch` ở runtime, và đối tượng lỗi được ném đều là `FileNotFoundException`. Khác nhau ở **thông tin kiểu có sẵn tại thời điểm biên dịch**, không phải runtime ép lỗi thành lớp cha.
- `source.fetch("found")` trả `"loaded:found"` và không ném checked exception trong nhánh này; nhưng chữ ký concrete vẫn báo có khả năng `throws FileNotFoundException`. Trong `main`, lần gọi cuối cũng được trình biên dịch kiểm tra theo chữ ký concrete `throws FileNotFoundException`, vì vậy chính `main` khai báo `throws FileNotFoundException` để chương trình đầy đủ biên dịch được.

### Thứ tự quan hệ override: ví dụ không hợp lệ

```java
interface Reader {
    void read() throws java.io.IOException;
}

class InvalidReader implements Reader {
    @Override
    public void read() throws Exception {
        // ...
    }
}
```

Ví dụ trên **cố ý không biên dịch** vì `throws Exception` rộng hơn `throws IOException` trong hợp đồng cha. Caller nhận `Reader` chỉ được yêu cầu tính đến `IOException`; nếu triển khai được phép thêm mọi checked `Exception` khác, hợp đồng kiểu cha sẽ bị phá vỡ.

Một lớp ghi đè có thể dùng `throws FileNotFoundException` hoặc không có checked `throws`, nhưng không thể tùy ý tăng mức rủi ro checked exception mà kiểu cha đã công bố.

## 8. Chú ý: `throws` không xác định kiểu thực tế của đối tượng trong `catch`

Trong Java, đoạn:

```java
catch (java.io.IOException ex) {
    // ...
}
```

định nghĩa **kiểu tham chiếu** của biến `ex` tại thời điểm biên dịch. Nó không thay đổi lớp của đối tượng. Nếu đối tượng là `FileNotFoundException` thì:

```java
ex instanceof java.io.FileNotFoundException
```

là `true` trong nhánh đó. Các phương thức khả dụng trực tiếp trên biến `ex` vẫn bị giới hạn bởi kiểu tĩnh `IOException` trừ khi dùng pattern matching `instanceof` hoặc ép kiểu an toàn.

Ví dụ Java 17 về kiểm tra kiểu runtime (đặt trong handler):

```java
if (ex instanceof java.io.FileNotFoundException missing) {
    System.out.println("Missing file: " + missing.getMessage());
} else {
    System.out.println("Other I/O: " + ex.getMessage());
}
```

Đây là đoạn minh họa, cần biến `ex` là một `IOException` đã bắt. Pattern matching `instanceof` hỗ trợ ràng buộc biến `missing` khi đối tượng thật thuộc lớp con; tính năng này có sẵn trong Java 17.

Hãy phân nhánh theo subtype khi caller **thật sự có cách xử lý riêng**. Nếu lớp con không làm thay đổi phản ứng, một handler `IOException` có thể đủ. Không nên dùng `ex.getMessage().contains("file")` để suy ra subtype vì thông điệp không phải hợp đồng kiểu.

## 9. Có thể ném một loại ngoại lệ khác kiểu cha khai báo không?

Có, nhưng cần phân biệt **checked** và **unchecked**.

Giả sử:

```java
static void load(String path) throws java.io.IOException {
    if (path == null) {
        throw new IllegalArgumentException("path is required");
    }
    if ("missing".equals(path)) {
        throw new java.io.FileNotFoundException(path);
    }
}
```

Đây là phương thức minh họa:

- `FileNotFoundException` là checked và được bao phủ bởi `throws IOException`.
- `IllegalArgumentException` là unchecked, nên được phép ném mà không phải ghi trong `throws`.
- Một ngoại lệ checked **khác nhánh**, ví dụ `java.sql.SQLException`, sẽ yêu cầu thêm `throws SQLException` hoặc phải được bắt và xử lý trong phương thức. `throws IOException` không bao phủ `SQLException`.
- `Error` cũng không bị buộc khai báo nhưng không nên được xem là loại thất bại ứng dụng có thể phục hồi bình thường.

Vì vậy cụm “phương thức khai báo `throws IOException` nên chỉ có thể ném `IOException`” là **sai**. Diễn đạt chính xác hơn: **những checked exception được phép lan ra trong mã Java bình thường phải thỏa hợp đồng khai báo**, còn unchecked exceptions vẫn có thể xảy ra.

## 10. Nếu bọc (`wrap`) ngoại lệ con thì kiểu ngoài thay đổi

Đôi khi caller ở tầng trên cần lỗi ở mức trừu tượng khác. Ví dụ:

```java
static void loadRequiredConfiguration() {
    try {
        loadFile();
    } catch (java.io.FileNotFoundException cause) {
        throw new IllegalStateException(
                "Required configuration is missing", cause);
    } catch (java.io.IOException cause) {
        throw new IllegalStateException(
                "Required configuration cannot be read", cause);
    }
}
```

Đây là **phương thức minh họa**, giả định `loadFile()` có thể ném `IOException`. Khi bọc:

1. Ngoại lệ gốc có thể là `FileNotFoundException`, thuộc nhánh `IOException`.
2. Tầng hiện tại tạo **một đối tượng mới** `IllegalStateException` và truyền ngoại lệ gốc làm `cause`.
3. Caller phía ngoài nhận **kiểu thực tế của ngoại lệ mới** là `IllegalStateException`, không tự động đi vào `catch (IOException)`.
4. Nếu cần xem lỗi I/O sâu bên trong, caller hoặc hệ thống log kiểm tra `ex.getCause()` và chuỗi nguyên nhân. Không nên coi `catch` tự đi xuyên qua `cause` để tìm kiểu gốc: `catch` chỉ kiểm tra đối tượng đang được ném.

Việc bọc có thể đúng khi tầng ứng dụng chủ động chuyển từ lỗi I/O sang trạng thái không thể khởi tạo dịch vụ. Nhưng đừng bọc ở mọi tầng chỉ để thêm một câu “failed”; nó làm hợp đồng API thay đổi, có thể khiến handler chuyên biệt ở caller không còn bắt được lỗi qua kiểu trực tiếp.

### Ném lại cùng ngoại lệ thì sao?

`throw ex;` ném lại **cùng đối tượng** đã bắt. Thông thường kiểu runtime vẫn là kiểu ban đầu; không tạo wrapper mới. Với checked exceptions, khai báo của phương thức chứa lệnh ném lại vẫn phải thỏa quy tắc kiểm tra của trình biên dịch. Java có cơ chế phân tích kiểu chính xác hơn cho một số trường hợp ném lại biến `catch` không bị gán lại (*precise rethrow*), nhưng không nên suy ra rằng mọi kiểu checked có thể được ném lại mà không cần khai báo.

## 11. Khai báo nhiều ngoại lệ checked và lớp con của chúng

Một phương thức có thể công bố nhiều nhóm lỗi:

```java
static void process()
        throws java.io.IOException, java.sql.SQLException {
    // Có thể truy cập tệp hoặc cơ sở dữ liệu.
}
```

Đây là ví dụ về **chữ ký phương thức**, phần thân chưa triển khai thao tác thật.

- `throws IOException, SQLException` cho biết cả hai nhóm checked exception có thể đi ra ngoài.
- Một `FileNotFoundException` được bao phủ bởi `IOException`.
- Một lớp con của `SQLException` được bao phủ bởi `SQLException`.
- Caller có thể chọn hai `catch` riêng nếu cần hành động khác nhau, hoặc `catch (IOException | SQLException ex)` nếu chính sách xử lý giống nhau.
- `IOException` và `SQLException` không nằm trong quan hệ cha/con trực tiếp nên được dùng chung trong multi-catch.

Không cần liệt kê vừa `throws IOException` vừa `throws FileNotFoundException` để cho phép lớp con; kiểu cha đã bao phủ. Việc thêm tên lớp con đôi khi phục vụ tài liệu hóa nhưng thường thừa trong chữ ký; `@throws` của JavaDoc có thể mô tả chính xác hơn điều kiện khiến ngoại lệ con phát sinh.

## 12. `throws`, đa hình và khả năng thay thế triển khai

Nếu thư viện công khai:

```java
interface Storage {
    byte[] read(String key) throws java.io.IOException;
}
```

nhiều triển khai có thể ném các `IOException` cụ thể khác nhau khi thất bại. Caller dùng kiểu `Storage` vẫn có thể bắt một `IOException` và hoạt động với triển khai mới mà không buộc biên dịch lại logic xử lý từng subclass. Đây là lợi ích của một **hợp đồng ngoại lệ có mức trừu tượng ổn định**.

Tuy nhiên, chọn kiểu cha quá rộng như `throws Exception` chỉ để “linh hoạt” có thể đẩy quá nhiều trách nhiệm sang caller và làm mờ cách phục hồi. Nên thiết kế API quanh các **nhóm lỗi caller có thể xử lý hợp lý**, chứ không phơi toàn bộ chi tiết triển khai.

Cũng không nên hiểu rằng “throws ghi kiểu cha” đồng nghĩa với “caller không bao giờ cần biết kiểu con”. Caller có thể kiểm tra kiểu runtime hoặc bắt subclass khi có một phương án xử lý riêng cho trường hợp ấy.

### `throws` không tạo overload mới

Java không cho phép hai phương thức chỉ khác nhau ở mệnh đề `throws`:

```java
// Cố ý sai nếu đặt cả hai trong cùng một lớp:
void load() throws java.io.IOException {}
void load() throws java.io.FileNotFoundException {}
```

Hai khai báo có cùng tên và cùng danh sách tham số, nên không phải hai overload hợp lệ. Ngoại lệ checked trong `throws` ảnh hưởng kiểm tra hợp đồng và override, nhưng không phân biệt phương thức khi overload.

## 13. Những ngộ nhận và lỗi lập trình thường gặp

| Ngộ nhận | Điều đúng |
| --- | --- |
| `throws IOException` luôn tạo ra `IOException` | `throws` chỉ khai báo khả năng; phương thức có thể chạy thành công hoặc ném subclass |
| Nếu caller bắt `IOException` thì đối tượng `FileNotFoundException` bị biến thành `IOException` | Kiểu runtime không đổi; `getClass()` vẫn là `FileNotFoundException` |
| `throws FileNotFoundException` cho phép ném bất kỳ `IOException` nào | Không; khai báo subtype không bao phủ supertype |
| Có `throws IOException` thì không thể ném `IllegalStateException` | Có thể, vì `IllegalStateException` là unchecked |
| Bắt `FileNotFoundException` là đủ cho mọi phương thức `throws IOException` | Không nếu còn các `IOException` khác; caller phải xử lý/khai báo phần checked còn lại |
| Override có thể mở từ `throws IOException` sang `throws Exception` | Không, vì làm hợp đồng checked exception rộng hơn |
| `catch` tìm theo `getCause()` | Không; chỉ kiểu của đối tượng đang ném được dùng để chọn handler |
| `throws Exception` là cách tốt nhất để khỏi lo loại lỗi | Quá rộng thường làm API khó hiểu và caller bị buộc xử lý nhiều trường hợp |
| Để phát sinh `NumberFormatException` phải khai báo `throws NumberFormatException` | Không bắt buộc; đây là unchecked exception |
| `throws` chỉ có trong lớp triển khai, không tác động tới người gọi qua interface | Caller được kiểm tra theo chữ ký ở **kiểu tĩnh** mà nó gọi |

### Nhầm thứ tự `catch`

Với:

```java
try {
    load();
} catch (java.io.IOException ex) {
    // ...
} catch (java.io.FileNotFoundException ex) {
    // ...
}
```

Đây là **ví dụ cố ý sai** nếu hai nhánh ở cùng một `try`: handler `IOException` đã bắt luôn `FileNotFoundException`, nên handler lớp con bị che khuất và mã không biên dịch. Đảo thứ tự để kiểm tra lớp con trước.

### Nhầm kiểu khai báo với dữ liệu có thể tin cậy ở runtime

Có `throws IOException` không tự cho biết lỗi thực tế đến từ thiếu tệp, quyền truy cập, kết nối hay lỗi đọc. Đừng suy ra nguyên nhân kỹ thuật duy nhất từ kiểu cha. Dùng subtype thực tế, message, cause, stack trace và thông tin về thao tác để chẩn đoán, rồi chỉ báo điều đã được xác minh.

## 14. Bài tập tự kiểm tra

**Bài A.** `static void f() throws IOException { throw new FileNotFoundException(); }` có hợp lệ không?

**Có.** `FileNotFoundException` kế thừa `IOException`; kiểu checked thực tế được bao phủ bởi kiểu khai báo cha.

**Bài B.** `static void f() throws FileNotFoundException { throw new IOException(); }` có hợp lệ không?

**Không.** `IOException` rộng hơn `FileNotFoundException`; muốn ném nó ra ngoài cần khai báo `throws IOException` hoặc một supertype phù hợp, hay bắt và xử lý bên trong.

**Bài C.** Phương thức khai báo `throws IOException` nhưng ném `EOFException`. `catch (IOException)` có xử lý được không?

**Có.** Kiểu runtime là `EOFException`, một subclass của `IOException`. Handler kiểu cha khớp được.

**Bài D.** Một interface khai báo `throws IOException`; lớp triển khai ghi đè khai báo `throws FileNotFoundException`. Có hợp lệ không?

**Có.** Override thu hẹp loại checked exception. Caller qua biến interface vẫn được kiểm tra theo `throws IOException` của interface; caller qua lớp concrete thấy khai báo hẹp hơn.

**Bài E.** Phương thức khai báo `throws IOException` và có lệnh `throw new IllegalArgumentException("bad input")`. Có cần thêm `throws IllegalArgumentException` không?

**Không.** `IllegalArgumentException` là unchecked exception. Nó vẫn có thể bị bắt tại runtime bằng handler thích hợp.

**Bài F.** Caller bắt `IOException` và nhận một `FileNotFoundException`. Gọi `ex.getClass().getSimpleName()` trả về gì?

**`FileNotFoundException`.** Bắt qua lớp cha không đổi kiểu động.

**Bài G.** Tầng giữa bắt `FileNotFoundException` rồi `throw new IllegalStateException("cannot start", ex)`. Tầng ngoài chỉ có `catch (IOException)` có bắt được wrapper không?

**Không.** Ngoại lệ đang ném ra là `IllegalStateException`; `FileNotFoundException` nằm trong `cause`. Cần handler tương thích với kiểu wrapper hoặc thay đổi thiết kế hợp đồng.

## 15. Checklist khi đọc một lời gọi có `throws`

1. **Đọc chữ ký:** các checked exception nào được khai báo? Chúng là kiểu cha hay kiểu cụ thể?
2. **Xem đường thực thi:** câu `throw` nào hoặc API nào có thể ném subtype của kiểu khai báo?
3. **Kiểm tra caller:** đã bắt hoặc khai báo đầy đủ các checked exception theo kiểu tĩnh của lời gọi chưa?
4. **Đọc handler:** nhánh `catch` lớp con đã đứng trước lớp cha chưa? Handler có đang xử lý đúng loại lỗi và đúng chính sách không?
5. **Xét overriding:** lớp triển khai có thu hẹp/giữ nguyên hợp đồng checked exception của kiểu cha/interface không?
6. **Xét runtime:** ngoại lệ thực tế có đúng là subtype dự kiến không? Có unchecked exception hoặc wrapper làm thay đổi kiểu ở tầng ngoài không?
7. **Giữ thông tin chẩn đoán:** nếu phải bọc, chuyển `cause`; nếu không có lý do đổi hợp đồng, cân nhắc để nguyên ngoại lệ đi lên.

**Ghi nhớ:** `throws` cho trình biên dịch và người gọi biết **nhóm checked exception mà phương thức có thể để thoát ra**; `throw`/API tại runtime quyết định **đối tượng ngoại lệ cụ thể**. Một khai báo kiểu cha bao phủ các lớp con checked, còn `catch` được chọn theo kiểu thực tế của đối tượng và quan hệ kế thừa. Trong phương thức ghi đè, hợp đồng checked exception chỉ có thể giữ tương đương hoặc được thu hẹp, không được mở rộng làm phá vỡ kỳ vọng của caller.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="throws-clause-004">Phân biệt throw với throws</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt `throw` với `throws` trong Java

> **Chủ đề:** Java Core → Exception → Mệnh đề `throws`. Hai từ khóa có tên gần giống nhưng đảm nhiệm **hai công việc khác nhau**: `throw` là **câu lệnh thực sự ném một đối tượng ngoại lệ**; `throws` là **mệnh đề trong khai báo phương thức/constructor** cho biết những loại ngoại lệ có thể thoát ra, đặc biệt để đáp ứng quy tắc **checked exception** của compiler. Nắm được khác biệt này giúp đọc đúng API Java, hiểu cách exception lan truyền và thiết kế hợp đồng xử lý lỗi rõ ràng.

## 1. Nhìn ngay sự khác nhau qua cú pháp

```java
// Câu lệnh thực thi trong thân phương thức:
throw new IllegalArgumentException("Giá trị không hợp lệ");

// Mệnh đề trong chữ ký phương thức:
static void read() throws java.io.IOException {
    // Thân phương thức.
}
```

Hai đoạn trên chỉ minh họa cú pháp, chưa phải một chương trình độc lập. Điểm phân biệt:

- **`throw expression;`** đánh giá một biểu thức có kiểu phù hợp với `Throwable` và ném đối tượng đó tại vị trí đang thực thi. Nó làm luồng chạy kết thúc bất thường, trừ khi ngoại lệ được bắt ở phạm vi phù hợp.
- **`throws ExceptionType1, ExceptionType2`** xuất hiện sau danh sách tham số và trước thân phương thức (hoặc dấu `;` đối với khai báo không có thân). Đây là phần **khai báo**, bản thân nó không tạo hay ném đối tượng nào.

Đặc biệt: một method có thể có `throws` nhưng **không bao giờ thực hiện lệnh `throw` trực tiếp**; exception có thể đến từ phương thức con. Ngược lại, một method có thể dùng `throw` với unchecked exception mà **không cần `throws`**.

### 1.1. Bảng so sánh cốt lõi

| Tiêu chí | `throw` | `throws` |
| --- | --- | --- |
| Bản chất | Statement điều khiển luồng | Thành phần chữ ký khai báo |
| Nơi đặt | Trong thân method, constructor, lambda block hoặc ngữ cảnh cho phép statement | Sau danh sách tham số của method/constructor và trước thân hoặc dấu `;` |
| Cú pháp | `throw new IOException("Lỗi");` | `void load() throws IOException, SQLException` |
| Đi theo sau | **Một biểu thức** có kiểu tương thích `Throwable` | **Danh sách một hoặc nhiều kiểu** ngoại lệ là lớp con của `Throwable` |
| Có ném thật không? | Có, khi dòng lệnh được thực thi | Không |
| Ảnh hưởng đến chương trình | Đưa luồng đi theo đường exception; câu lệnh bình thường phía sau điểm ném không chạy | Góp phần xác định nghĩa vụ compile-time của caller với checked exceptions |
| Có phải luôn viết cùng nhau? | Không | Không |
| Checked/unchecked | Có thể ném cả hai | Checked exceptions phải được bắt hoặc khai báo nếu có thể thoát; unchecked không bắt buộc kê khai |

**Cách nhớ:** `throw` trả lời “**ném cái gì, ngay lúc nào?**”; `throws` trả lời “**method có thể để những loại lỗi nào thoát ra cho caller xử lý?**”.

## 2. `throw` hoạt động như thế nào?

Cú pháp tổng quát:

```java
throw expression;
```

`expression` phải có kiểu có thể chuyển thành `java.lang.Throwable` theo quy tắc ngôn ngữ. Ví dụ:

```java
throw new IllegalArgumentException("Số lượng âm");
```

Điều xảy ra:

1. Tạo một đối tượng `IllegalArgumentException`.
2. Đánh giá biểu thức `new ...` được viết sau `throw`.
3. Ném đối tượng đó; đường đi bình thường từ câu lệnh này dừng.
4. Java tìm `catch` tương thích trong các khối `try` đang hoạt động; nếu không có, lỗi lan ra ngoài theo chuỗi gọi.

Không nên nhầm việc **khởi tạo** với việc **ném**:

```java
new IllegalArgumentException("Lỗi");       // Chỉ tạo đối tượng.
throw new IllegalArgumentException("Lỗi"); // Tạo rồi thực sự ném.
```

Đây là các dòng minh họa riêng biệt. Dòng đầu không khiến chương trình đi vào `catch`; nếu không còn tham chiếu, đối tượng có thể được thu gom như những đối tượng khác. Dòng thứ hai thay đổi luồng điều khiển.

### 2.1. Kiểu nào được phép sau `throw`?

```text
java.lang.Throwable
├── java.lang.Exception
│   ├── java.io.IOException
│   └── java.lang.RuntimeException
│       ├── IllegalArgumentException
│       ├── IllegalStateException
│       └── NumberFormatException
└── java.lang.Error
    ├── AssertionError
    └── LinkageError
```

`Throwable` là gốc của hệ ngoại lệ; `Exception` và `Error` đều là lớp con. Lệnh `throw` có thể ném đối tượng ở các nhánh này về mặt ngôn ngữ, nhưng ứng dụng thông thường **không nên sử dụng `Error` như ngoại lệ nghiệp vụ**.

Các biểu thức sau là sai kiểu:

```java
// CÁC VÍ DỤ CỐ Ý KHÔNG BIÊN DỊCH:
throw 42;              // int không phải Throwable.
throw "Có lỗi";        // String không phải Throwable.
throw new Object();    // Object không phải Throwable.
```

Ví dụ đúng:

```java
throw new IllegalArgumentException("Số không hợp lệ");
```

`throw null;` là trường hợp đặc biệt: Java cho phép cú pháp, nhưng nếu đánh giá biểu thức sau `throw` ra `null`, runtime ném `NullPointerException` thay thế. Đây không phải kỹ thuật báo lỗi tốt; nên tạo exception diễn đạt rõ nguyên nhân.

### 2.2. Biến kiểu tĩnh và kiểu đối tượng thực tế

Giả sử:

```java
Object error = new IllegalStateException("Trạng thái sai");
// throw error; // Không hợp lệ vì kiểu tĩnh Object không phải Throwable.
```

Dù đối tượng bên trong là `IllegalStateException`, kiểu của **biểu thức** `error` là `Object` nên compiler không cho `throw error`.

Nếu có một biến khai báo `Throwable error`, `throw error` đúng yêu cầu **kiểu của lệnh throw**, nhưng có thể cần `throws Throwable` hoặc `catch` vì compiler xử lý nó như checked ở kiểu rộng `Throwable`. Phân biệt kiểm tra kiểu và quy tắc checked exception là điểm quan trọng trong các phần tiếp theo.

### 2.3. `throw` không đồng nghĩa với `return`

`return` hoàn tất bình thường và có thể trả giá trị. `throw` kết thúc bất thường, không trả về một giá trị cùng loại với kiểu trả về của method. Ví dụ:

```java
static int divide(int a, int b) {
    if (b == 0) {
        throw new ArithmeticException("Không thể chia cho 0");
    }
    return a / b;
}
```

Đây là phương thức minh họa, cần đặt trong một lớp. Nhánh `b == 0` không thực hiện `return a / b`. Trong Java, đặt một câu lệnh thực thi ngay sau `throw` **vô điều kiện** trong cùng khối sẽ gây lỗi “unreachable statement”.

## 3. `throws` hoạt động như thế nào?

Cú pháp khái quát:

```java
ReturnType methodName(Parameters...) throws ExceptionType1, ExceptionType2 {
    // Nội dung.
}
```

Mệnh đề `throws` được viết trong **khai báo** method hoặc constructor; các tên theo sau là **kiểu Throwable**, không phải đối tượng `new Exception(...)`. Không thể viết `throws new IOException("...")`.

Ví dụ một phương thức đọc:

```java
static String readFirstLine(java.io.BufferedReader reader)
        throws java.io.IOException {
    return reader.readLine();
}
```

Method này **không có câu lệnh `throw`**. Nhưng `reader.readLine()` có thể ném `IOException`, và method cho phép ngoại lệ đó lan ra caller, vì vậy khai báo `throws IOException` là hợp lý. Nếu caller không xử lý hoặc khai báo tiếp checked exception này, compiler sẽ báo lỗi.

### 3.1. `throws` là phần hợp đồng, không phải một lệnh chạy

Nhìn:

```java
static void maybeFail(boolean failed) throws java.io.IOException {
    if (failed) {
        throw new java.io.IOException("Không thể đọc");
    }
}
```

Ở mỗi lời gọi, `throws IOException` vẫn nằm trong chữ ký. Nhưng chỉ khi `failed == true` thì lệnh `throw` mới được thực hiện. Khi `failed == false`, method kết thúc bình thường. **Không có ngoại lệ nào tự phát sinh chỉ vì chữ ký có `throws`.**

### 3.2. `throws` có thể có nhiều kiểu

```java
static void process() throws java.io.IOException,
        java.text.ParseException {
    // Hoạt động có thể ném IOException hoặc ParseException.
}
```

Các kiểu được phân cách bằng dấu phẩy. Điều đó không có nghĩa cả hai ngoại lệ sẽ cùng được ném tại mỗi lần gọi. Một lần kết thúc bất thường thường có một đối tượng Throwable đang lan truyền (nó có thể mang cause hoặc suppressed exceptions khác).

Cũng không cần khai báo `throws` trùng lặp khi kiểu cha đã bao phủ các kiểu con được ném. Ví dụ `throws IOException` đủ cho `FileNotFoundException extends IOException`. Tuy nhiên, khai báo quá rộng như `throws Exception` hay `throws Throwable` thường khiến caller khó biết cách xử lý cụ thể, nên hãy ưu tiên kiểu diễn đạt đúng hợp đồng.

### 3.3. `throws` dùng được trong constructor và khai báo interface

```java
interface DocumentReader {
    String read() throws java.io.IOException;
}

final class ReaderSetup {
    ReaderSetup(String path) throws java.io.IOException {
        if (path == null) {
            throw new java.io.IOException("Thiếu đường dẫn");
        }
    }
}
```

Đây là các khai báo loại, không có `main`. Mệnh đề `throws` phù hợp ở method trong interface và constructor. Ngược lại, `throw` xuất hiện **trong thân** constructor khi thật sự gặp tình huống sai.

## 4. Quan hệ với checked và unchecked exceptions

**Checked exception:** các lớp con của `Throwable` không thuộc nhánh `RuntimeException` hoặc `Error`, ví dụ `IOException` và `ParseException`. Nếu checked exception có thể thoát khỏi một method, method phải bắt hoặc khai báo bằng `throws` đủ bao phủ.

**Unchecked exception:** `RuntimeException`, `Error` và các lớp con. Compiler không yêu cầu bắt hoặc kê khai bằng `throws`, dù có thể ném chúng bằng `throw` và nên tài liệu hóa các trường hợp dự kiến.

| Lệnh ném | Loại | Khi thoát khỏi method có cần `throws`/`catch` không? |
| --- | --- | --- |
| `throw new IOException("Lỗi I/O");` | Checked | Có |
| `throw new ParseException("Sai định dạng", 0);` | Checked | Có |
| `throw new Exception("Lỗi");` | Checked | Có |
| `throw new IllegalArgumentException("Sai");` | Unchecked (`RuntimeException`) | Không bắt buộc |
| `throw new AssertionError("Sai giả định");` | Unchecked (`Error`) | Không bắt buộc, nhưng không nên dùng cho lỗi nghiệp vụ |
| `throw new Throwable("Lỗi");` | Checked | Có, thường phải `throws Throwable` hoặc handler phù hợp |

Lưu ý: **`Exception` không đồng nghĩa với checked exception**: `RuntimeException` là lớp con của `Exception` nhưng thuộc loại unchecked. Cũng không phải mọi `Throwable` thuộc nhánh `Exception`: `Error` là nhánh khác.

### 4.1. Checked exception: hai cách biên dịch hợp lệ

Giả sử cần phát ra `IOException` khi thiếu dữ liệu.

**Cách 1 — để caller chịu trách nhiệm bằng `throws`:**

```java
static void readOrFail(String data) throws java.io.IOException {
    if (data == null) {
        throw new java.io.IOException("Thiếu dữ liệu");
    }
}
```

**Cách 2 — xử lý trong chính method bằng `try/catch`:**

```java
static void readOrReport(String data) {
    try {
        if (data == null) {
            throw new java.io.IOException("Thiếu dữ liệu");
        }
    } catch (java.io.IOException e) {
        System.out.println("Đã nhận lỗi: " + e.getMessage());
    }
}
```

Hai ví dụ là **phương thức**, không phải file chương trình độc lập. Hai cách đều biên dịch khi đặt trong một lớp, nhưng **khác nhau về hợp đồng**: cách 1 đẩy khả năng xử lý lỗi lên caller; cách 2 tự báo lỗi ở đây và kết thúc bình thường sau `catch`. Nếu chỉ in log rồi bỏ qua lỗi làm caller hiểu lầm thao tác thành công, cách 2 không phù hợp.

### 4.2. Unchecked exception: `throw` không cần `throws`

```java
static int requirePositive(int n) {
    if (n <= 0) {
        throw new IllegalArgumentException("Phải lớn hơn 0");
    }
    return n;
}
```

Đây là unchecked exception. Viết thêm `throws IllegalArgumentException` là hợp lệ nhưng không bắt buộc; nó cũng **không ép caller** phải bắt hoặc khai báo. JavaDoc `@throws` thường thích hợp để mô tả các tiền điều kiện sai mà API dự kiến báo ra.

### 4.3. Khai báo `throws` không giới hạn mọi lỗi runtime

Method khai báo `throws IOException` vẫn có thể gặp `NullPointerException`, `IllegalArgumentException` hoặc `Error`. Khai báo này nói về những checked exception mà compiler/caller cần xét theo hợp đồng; nó **không hứa rằng method chỉ có thể ném IOException** hoặc đã kiểm tra toàn bộ lỗi.

## 5. Ví dụ Java 17 đầy đủ: `throw` và `throws` phối hợp

Lưu thành `ThrowVsThrowsDemo.java`:

```java
import java.io.IOException;

public class ThrowVsThrowsDemo {
    static int parsePositive(String text) {
        int number = Integer.parseInt(text);
        if (number <= 0) {
            throw new IllegalArgumentException("Số phải dương");
        }
        return number;
    }

    static int readCount(String text) throws IOException {
        if (text == null) {
            throw new IOException("Nguồn dữ liệu trống");
        }
        return parsePositive(text);
    }

    static void show(String input) {
        try {
            System.out.println("Số lượng: " + readCount(input));
        } catch (IOException e) {
            System.out.println("Lỗi nguồn: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("Lỗi đầu vào: "
                    + e.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        show("7");
        show("0");
        show("abc");
        show(null);
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac -encoding UTF-8 ThrowVsThrowsDemo.java
java ThrowVsThrowsDemo
```

Stdout:

```text
Số lượng: 7
Lỗi đầu vào: IllegalArgumentException
Lỗi đầu vào: NumberFormatException
Lỗi nguồn: Nguồn dữ liệu trống
```

**Tại sao đúng?**

- `parsePositive("7")` trả `7`, `readCount` cũng trả về bình thường.
- `parsePositive("0")` thực thi `throw new IllegalArgumentException(...)`. Nó không có `throws` vì đây là unchecked exception; `show` bắt bằng `catch (IllegalArgumentException)`.
- `Integer.parseInt("abc")` ném `NumberFormatException`. Loại này kế thừa `IllegalArgumentException` nên vẫn bị `catch (IllegalArgumentException)` bắt.
- `readCount(null)` **trực tiếp ném** `new IOException(...)` qua `throw`. Vì `IOException` checked, `readCount` khai báo `throws IOException`; `show` nhận trách nhiệm bắt.
- Các exception không xuất hiện cùng lúc trong một lời gọi. Mỗi lần `show` có thể thành công hoặc rẽ sang một nhánh xử lý lỗi.

**Bài học về trách nhiệm:** method cấp thấp có thể **ném**, method trung gian có thể **khai báo cho lan truyền**, và tầng gần người dùng có thể **bắt để hiển thị kết quả phù hợp**. `throw`, `throws`, `catch` không cạnh tranh nhau: chúng đảm nhiệm các khâu khác nhau trong cùng đường đi của ngoại lệ.

## 6. Exception propagation: có `throws` nhưng không có `throw` tại tầng giữa

Một trong các công dụng phổ biến nhất của `throws` là **truyền tiếp** checked exception từ method bên dưới, thay vì bắt nó ở một tầng chưa có đủ ngữ cảnh để xử lý.

Tạo `ThrowsPropagationDemo.java`:

```java
import java.io.IOException;

public class ThrowsPropagationDemo {
    static void readSource() throws IOException {
        throw new IOException("Không đọc được nguồn");
    }

    static void prepareReport() throws IOException {
        readSource();
    }

    static void run() {
        try {
            prepareReport();
            System.out.println("Báo cáo đã sẵn sàng");
        } catch (IOException e) {
            System.out.println("Báo cáo thất bại: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        run();
        System.out.println("Đã xử lý xong yêu cầu");
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ThrowsPropagationDemo.java
java ThrowsPropagationDemo
```

Stdout:

```text
Báo cáo thất bại: Không đọc được nguồn
Đã xử lý xong yêu cầu
```

Luồng xử lý:

```text
main()
  └── run()
        └── prepareReport() throws IOException
              └── readSource() throws IOException
                    └── throw new IOException(...)
        ◀──── IOException lan ngược qua prepareReport()
  └── catch (IOException) trong run()
```

`readSource` có cả `throw` và `throws`. `prepareReport` chỉ có `throws`, không có `throw` trực tiếp; ngoại lệ phát ra từ `readSource()` lan tiếp qua nó. `run` bắt tại ranh giới có thể báo một kết quả thất bại có ý nghĩa. Dòng “Báo cáo đã sẵn sàng” bị bỏ qua, vì lời gọi `prepareReport()` không trở về bình thường.

**Không phải có `throws` là ngoại lệ sẽ được tự động ném thêm lần nữa.** Runtime truyền cùng ngoại lệ chưa được xử lý qua chuỗi gọi; không tạo một đối tượng mới chỉ vì đi qua chữ ký `throws`.

## 7. `throw e` và `throw new ...`: ném lại, chuyển loại, giữ nguyên nguyên nhân

Sau khi bắt một ngoại lệ, code có thể:

- xử lý và hoàn tất bình thường;
- ném lại chính đối tượng bằng `throw e;`;
- tạo một đối tượng ngoại lệ mới rồi ném bằng `throw new ...`, thường cần giữ `cause`.

Ví dụ mô tả một đoạn method:

```java
try {
    loadData();
} catch (java.io.IOException e) {
    recordFailure(e);
    throw e;
}
```

Mã này phụ thuộc vào `loadData`/`recordFailure` đã được định nghĩa và method chứa nó phải khai báo hoặc bắt checked exception phù hợp. `throw e;` không tạo exception mới. Tùy ngữ cảnh, compiler có thể áp dụng **precise rethrow** cho biến `catch` không gán lại, giúp suy luận những checked exception cụ thể có thể được ném từ khối `try`.

Nếu muốn bổ sung ý nghĩa:

```java
try {
    loadData();
} catch (java.io.IOException e) {
    throw new IllegalStateException("Không nạp được dữ liệu", e);
}
```

Ở đây `IllegalStateException` là unchecked, nguyên nhân `IOException` được giữ qua `getCause()`. **Không nên viết `throw new RuntimeException(e.getMessage())` chỉ để bỏ nghĩa vụ `throws`**, vì cách đó đánh mất đối tượng nguyên nhân và không tự phục hồi chương trình.

### 7.1. Ví dụ chạy độc lập để nhìn thấy wrapper và cause

Tạo `WrapCauseDemo.java`:

```java
import java.io.IOException;

public class WrapCauseDemo {
    static void lowLevel() throws IOException {
        throw new IOException("Lỗi kết nối");
    }

    static void service() {
        try {
            lowLevel();
        } catch (IOException e) {
            throw new IllegalStateException("Tác vụ không thể hoàn tất", e);
        }
    }

    public static void main(String[] args) {
        try {
            service();
        } catch (IllegalStateException e) {
            System.out.println("Loại: "
                    + e.getClass().getSimpleName());
            System.out.println("Thông điệp: " + e.getMessage());
            System.out.println("Nguyên nhân: "
                    + e.getCause().getClass().getSimpleName());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 WrapCauseDemo.java
java WrapCauseDemo
```

Stdout:

```text
Loại: IllegalStateException
Thông điệp: Tác vụ không thể hoàn tất
Nguyên nhân: IOException
```

`service` có `throw new IllegalStateException(...)` nhưng không cần `throws` vì exception mới là unchecked. `lowLevel` có `throw new IOException(...)` và `throws IOException` vì checked. Wrapper không xóa nghĩa vụ **về hành vi**: caller vẫn cần hiểu thất bại và trạng thái sau thất bại; chuyển sang unchecked chỉ thay ràng buộc của compiler.

## 8. `throws` và quy tắc ghi đè phương thức (override)

Mệnh đề `throws` là một phần quan trọng của hợp đồng checked exception trong cơ chế đa hình Java.

Khi một method ở lớp con override method của lớp cha/interface:

- Method override **không được khai báo checked exception mới hoặc rộng hơn** so với những gì phương thức bị override cho phép.
- Method override có thể khai báo checked exception **cụ thể hơn** hoặc ít hơn, kể cả không khai báo checked exception nào.
- Unchecked exceptions không chịu ràng buộc này.
- Quy tắc áp dụng cho **override**, không phải mọi method cùng tên (overload có chữ ký tham số khác là câu chuyện khác).

### 8.1. Ví dụ Java 17 biên dịch được

Tạo `ThrowsOverrideDemo.java`:

```java
import java.io.FileNotFoundException;
import java.io.IOException;

public class ThrowsOverrideDemo {
    interface Source {
        String get() throws IOException;
    }

    static final class MemorySource implements Source {
        @Override
        public String get() {
            return "Bộ nhớ";
        }
    }

    static final class MissingSource implements Source {
        @Override
        public String get() throws FileNotFoundException {
            throw new FileNotFoundException("Không thấy tài liệu");
        }
    }

    public static void main(String[] args) {
        Source[] sources = {
                new MemorySource(),
                new MissingSource()
        };

        for (Source source : sources) {
            try {
                System.out.println("Dữ liệu: " + source.get());
            } catch (IOException e) {
                System.out.println("Lỗi: " + e.getMessage());
            }
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ThrowsOverrideDemo.java
java ThrowsOverrideDemo
```

Stdout:

```text
Dữ liệu: Bộ nhớ
Lỗi: Không thấy tài liệu
```

`Source.get() throws IOException` định nghĩa hợp đồng chung. `MemorySource.get()` bỏ hẳn `throws` vì không phát sinh checked exception. `MissingSource.get() throws FileNotFoundException` hợp lệ vì `FileNotFoundException` là lớp con của `IOException`.

Dù đối tượng runtime có thể là `MemorySource`, biến của vòng lặp có kiểu tĩnh `Source`, nên lời gọi `source.get()` vẫn được compiler xét theo hợp đồng `throws IOException` của interface. Đây là một ví dụ cho thấy **kiểu tham chiếu dùng tại nơi gọi** cũng ảnh hưởng nghĩa vụ xử lý.

### 8.2. Ví dụ override sai

Các dòng sau là **minh họa lỗi biên dịch**, không đặt vào tệp ví dụ chạy:

```java
interface Api {
    void execute() throws java.io.IOException;
}

class BadImplementation implements Api {
    @Override
    public void execute() throws java.sql.SQLException {
        // Không hợp lệ: SQLException không là lớp con của IOException.
    }
}
```

Một caller chỉ biết `Api` không được yêu cầu bất ngờ xử lý checked exception hoàn toàn khác do implementation mở rộng hợp đồng. Java ngăn tình huống này ở compile-time.

Đừng đồng nhất quy tắc override với việc method **không thể** ném một unchecked exception trong lúc chạy. Một override vẫn có thể `throw new IllegalStateException(...)` dù chữ ký interface không khai báo loại đó.

## 9. `throws` với lambda và functional interface

Một điểm dễ gặp khi dùng Java hiện đại: lambda phải tuân theo chữ ký của **functional interface**. Không thể thêm checked exception vào lambda chỉ vì ta muốn viết `throw`.

Ví dụ mảnh code minh họa:

```java
Runnable task = () -> {
    // throw new java.io.IOException("I/O"); // Không hợp lệ ở đây.
    throw new IllegalStateException("Tác vụ không hợp lệ");
};
```

`Runnable.run()` không khai báo `throws IOException`; do đó lambda không thể để checked `IOException` thoát khỏi phần thân mà không tự xử lý. Nhưng unchecked `IllegalStateException` thì được.

Một callback có interface của riêng mình có thể khai báo checked exception:

```java
@FunctionalInterface
interface CheckedJob {
    void run() throws java.io.IOException;
}
```

Khi một lambda được gán cho `CheckedJob`, nó được phép ném `IOException` theo đúng hợp đồng của phương thức trừu tượng. Đây là ví dụ dùng chữ ký `throws` để truyền nghĩa vụ xử lý qua interface, không phải đặc quyền riêng của lambda.

Trong mã Java 17+, đừng đổi sang `throws Exception` cho mọi callback chỉ để “cho qua compiler”; hãy chọn loại exception phù hợp với người sử dụng API.

## 10. `throws` và try-with-resources: lỗi có thể đến từ `close()`

Mệnh đề `throws` không chỉ liên quan đến câu lệnh `throw` bạn tự viết. Nhiều API khai báo checked exceptions do thao tác I/O hoặc dọn dẹp tài nguyên. Với `AutoCloseable`, `close()` có thể khai báo checked exception, và try-with-resources có thể làm exception đó thoát ra khi rời khối.

Ví dụ method minh họa:

```java
static void openAndClose() throws java.io.IOException {
    try (java.io.BufferedReader reader =
                 new java.io.BufferedReader(
                         new java.io.StringReader("abc"))) {
        System.out.println(reader.readLine());
    }
}
```

Method **không có câu lệnh `throw`**. Nhưng `BufferedReader.readLine()` và `BufferedReader.close()` có khai báo `IOException`, nên chữ ký `throws IOException` là phù hợp. Với dữ liệu `StringReader` cụ thể, ví dụ thường chạy thành công, nhưng hợp đồng kiểu không suy luận riêng theo giá trị đầu vào.

Nếu thân khối và `close()` cùng ném exception, try-with-resources giữ lỗi từ thân khối làm ngoại lệ chính và ghi lỗi đóng trong `getSuppressed()` theo quy tắc tương ứng. Đừng nhầm “đã có `throws`” với “đã tự động đóng tài nguyên”, hoặc “đã đóng” với “đã hoàn tác các thay đổi trước lỗi”.

## 11. Những hiểu nhầm thường gặp

| Nhầm lẫn | Sự thật cần nhớ |
| --- | --- |
| “`throw` và `throws` là hai cách viết cùng một chức năng.” | `throw` thực thi thao tác ném; `throws` khai báo khả năng lỗi thoát khỏi method/constructor. |
| “Chỉ cần thêm `throws` thì exception tự phát sinh.” | Không; phải có exception thực sự do lệnh `throw` hoặc thao tác/lời gọi khác tạo ra. |
| “Có lệnh `throw` thì bắt buộc thêm `throws`.” | Chỉ checked exception có thể thoát ra mới buộc bắt hoặc khai báo. Unchecked không cần. |
| “Method có `throws` chắc chắn có dòng `throw`.” | Exception có thể đến từ method con, constructor, resource close, thư viện hoặc JVM. |
| “`throws` dùng với đối tượng `new IOException(...)`.” | `throws` nhận **tên kiểu**, còn `throw` nhận **biểu thức** có kiểu Throwable. |
| “`throws IOException` có nghĩa chỉ IOException được ném.” | Unchecked exceptions và Errors vẫn có thể xảy ra; chữ ký không bảo đảm độc quyền. |
| “`throws Exception` luôn tiện hơn khai báo cụ thể.” | Nó tăng gánh nặng cho caller và che thông tin về loại lỗi có thể xử lý. |
| “`RuntimeException` là unchecked nên không thể `catch`.” | Unchecked nghĩa là không bắt buộc xử lý ở compile-time; vẫn có thể bắt theo kiểu tương thích. |
| “`throw null` sẽ ném một đối tượng null.” | Khi chạy Java tạo/ném `NullPointerException` thay thế. |
| “`throw new Exception("x")` trong method không `throws` luôn được.” | `Exception` là checked; phải bắt hoặc khai báo nếu thoát ra. |
| “Ghi đè method có thể thêm bất kỳ checked `throws` nào.” | Override không được mở rộng checked exception trái với hợp đồng của method cha/interface. |
| “Thêm `throws` giúp tự rollback.” | Java không tự hoàn tác side effect do có exception. |
| “Đổi checked exception thành RuntimeException là đã sửa lỗi.” | Chỉ thay ràng buộc compiler; lỗi gốc, cause, trạng thái và chính sách xử lý vẫn phải quan tâm. |
| “`throw e` tạo exception mới.” | `throw e` ném lại đối tượng mà `e` tham chiếu. |

### 11.1. Các thông báo compiler hay gặp

- **“unreported exception ...; must be caught or declared to be thrown”**: method gọi API có checked exception hoặc thực hiện `throw` với checked exception, nhưng không bắt/khai báo.
- **“incompatible types ... cannot be converted to Throwable”**: biểu thức sau `throw` không thuộc hệ kiểu Throwable.
- **“overridden method does not throw ...”**: method override cố thêm checked exception vượt hợp đồng cha/interface.
- **“exception ... has already been caught”**: handler cụ thể bị một handler rộng hơn ở trước che khuất.

Thông điệp chính xác phụ thuộc compiler/JDK; cần đọc nguyên nhân và sửa hợp đồng hoặc luồng xử lý, không chỉ thêm `throws Exception` tùy tiện.

## 12. Quy trình quyết định khi nào dùng `throw`, khi nào dùng `throws`

### Bước 1 — Xác định điều kiện thất bại

Ví dụ phương thức nhận số lượng và phải bảo đảm `quantity > 0`. Khi phát hiện điều kiện sai, `throw new IllegalArgumentException(...)` diễn đạt vi phạm hợp đồng.

### Bước 2 — Chọn kiểu exception phù hợp

Nếu lỗi do tham số/caller không hợp lệ hoặc trạng thái không cho phép, unchecked exception có thể phù hợp. Nếu API đọc file hoặc giao tiếp với nguồn I/O dùng checked `IOException`, giữ đúng cơ chế khai báo của nó. Không có quy tắc “mọi lỗi người dùng là checked” hoặc “mọi lỗi nghiệp vụ là unchecked”.

### Bước 3 — Xác định tầng có khả năng xử lý

Nếu method cấp thấp không biết nên yêu cầu nhập lại, thử nguồn khác hay hủy thao tác, nó có thể cho checked exception lan lên bằng `throws`. Tầng có ngữ cảnh thực sự mới nên `catch` để phản ứng đúng.

### Bước 4 — Viết chữ ký rõ ràng

Khai báo những checked exceptions dự kiến theo loại có ý nghĩa. Tránh `throws Throwable`, `throws Exception` khi có thể thu hẹp. Với unchecked exceptions quan trọng cho hợp đồng, mô tả bằng Javadoc `@throws` và kiểm thử điều kiện gây lỗi.

### Bước 5 — Kiểm thử cả thành công và lỗi

Đừng chỉ kiểm tra “đúng loại exception”. Kiểm tra input biên, đường lan truyền, trạng thái dữ liệu/tài nguyên sau thất bại, wrapper/cause và vị trí handler thích hợp.

## 13. Javadoc: `@throws` không phải từ khóa `throws`

Một API Java thường có cả mệnh đề `throws` trong chữ ký và thẻ `@throws` trong phần tài liệu. Hai thứ cũng **khác nhau**:

```java
/**
 * Đọc mã từ nguồn dữ liệu.
 *
 * @param input dữ liệu phải khác null
 * @return mã được đọc
 * @throws IllegalArgumentException nếu input sai hợp đồng
 * @throws java.io.IOException nếu nguồn dữ liệu không đọc được
 */
static String readCode(String input) throws java.io.IOException {
    if (input == null) {
        throw new IllegalArgumentException("input không được null");
    }
    return input;
}
```

Đây là một method minh họa. `@throws` trong Javadoc là **mô tả cho người đọc và công cụ sinh tài liệu**, không trực tiếp thay đổi nghĩa vụ của compiler. `throws IOException` trong chữ ký mới là khai báo ngôn ngữ cho checked exception có thể thoát. `throw new IllegalArgumentException(...)` trong thân mới là câu lệnh thực sự ném.

Trong đoạn ví dụ, `IOException` được khai báo để mô tả một biến thể API đọc nguồn; implementation cụ thể trả lại input và không tự phát sinh `IOException`. Compiler Java cho phép một method **khai báo checked exception mà đường thực thi hiện tại chưa ném**; việc ghi hợp đồng quá rộng khi không có lý do thực tế vẫn có thể gây bất tiện cho caller.

## 14. Bài tập tự luyện

### Bài 1 — Phân biệt vai trò từng dòng

Xét đoạn method:

```java
static void verify(boolean valid) throws java.io.IOException {
    if (!valid) {
        throw new java.io.IOException("Không hợp lệ");
    }
}
```

Trả lời:

1. Từ khóa nào có trong **chữ ký**?
2. Dòng nào **thực sự tạo và ném** exception?
3. Gọi `verify(true)` có tự động phát sinh `IOException` vì `throws` hay không?
4. Nếu caller gọi `verify(false)` mà không bắt/khai báo checked exception, compiler báo gì?

**Định hướng:** `throws` đứng ở chữ ký; `throw` đứng trong thân. Với `true`, method kết thúc bình thường; caller vẫn phải thỏa nghĩa vụ compile-time mà chữ ký đã công bố.

### Bài 2 — Sửa lỗi biên dịch

Đoạn sau cố ý sai:

```java
static void reject() {
    throw new java.io.IOException("Đọc thất bại");
}
```

Hãy sửa theo hai phương án: thêm `throws IOException` cho method và cập nhật caller; hoặc dùng `try/catch` trong method để xử lý thật sự. Mô tả sự khác nhau trong trách nhiệm xử lý.

### Bài 3 — Không thấy `throw` mà vẫn cần `throws`

Viết `readFirstLine(BufferedReader reader)` chỉ gọi `reader.readLine()` rồi trả kết quả. Giải thích vì sao phương thức có thể cần `throws IOException` mặc dù không tự gọi `throw`. Sau đó viết `main` bắt `IOException` và hiển thị thông báo có ngữ cảnh.

### Bài 4 — Giữ nguyên cause

Trong `WrapCauseDemo`, thay constructor `new IllegalStateException("...", e)` bằng `new IllegalStateException("...")` rồi chạy lại. `getCause()` khi đó là `null`; mã in `getCause().getClass()` sẽ gây `NullPointerException`. Giải thích tại sao giữ nguyên cause là quan trọng khi bọc lỗi và tại sao không được giả định mọi exception đều có cause.

### Bài 5 — Override và kiểu tham chiếu

Trong `ThrowsOverrideDemo`, thử khai báo `Source source = new MemorySource();`. Mặc dù implementation không khai báo checked exception, vì sao `source.get()` vẫn phải tuân theo hợp đồng `throws IOException` của interface tại điểm biên dịch? Đổi kiểu biến sang `MemorySource` và so sánh nghĩa vụ compile-time.

### Bài 6 — Tự thiết kế API

Tạo hai method: `parseAge(String input)` và `loadAgeFromFile(Path path)`. Method đầu kiểm tra input không âm và ném unchecked exception phù hợp; method thứ hai đọc file, có thể khai báo checked `IOException`, rồi gọi method đầu. Viết Javadoc mô tả từng lỗi; test đường thành công, số âm, chuỗi không phải số, file không tồn tại và dữ liệu sau thất bại.

## 15. Checklist ghi nhớ

- [ ] Viết đúng dạng `throw expression;` và `method(...) throws Type1, Type2`.
- [ ] Biết `throw` là statement thực thi còn `throws` là mệnh đề khai báo.
- [ ] Phân biệt biểu thức đối tượng sau `throw` với **tên kiểu** sau `throws`.
- [ ] Nêu được vì sao `throw "error";` không biên dịch, còn `throw new IOException("error");` đúng kiểu nhưng cần xử lý checked.
- [ ] Giải thích được `throw` unchecked không buộc `throws`.
- [ ] Giải thích được một method có `throws IOException` dù không có lệnh `throw` trực tiếp.
- [ ] Dự đoán chính xác stdout của `ThrowVsThrowsDemo` và `ThrowsPropagationDemo`.
- [ ] Hiểu cách `catch` xử lý sau khi checked exception truyền qua nhiều tầng gọi.
- [ ] Phân biệt `throw e;` với `throw new WrapperException("...", e);` và giữ cause.
- [ ] Nêu được giới hạn của checked exceptions khi override/interface/lambda.
- [ ] Phân biệt Javadoc `@throws` và mệnh đề Java `throws`.
- [ ] Không coi `throws` là cơ chế tự động rollback, tự log hay tự phục hồi.
- [ ] Biết chọn nơi xử lý dựa trên hợp đồng và khả năng khôi phục thật sự.

**Ghi nhớ:** **`throw` làm lỗi xảy ra trên một đường chạy cụ thể; `throws` công bố loại lỗi có thể thoát khỏi phương thức và giúp compiler/caller thực hiện nghĩa vụ với checked exception.** Dùng đúng cả hai giúp đường thành công rõ ràng, đường thất bại có trách nhiệm và các tầng gọi phối hợp dự đoán được.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="throws-clause-005">Lan truyền checked exception qua nhiều tầng lời gọi</a>

<details>
<summary>Xem chi tiết</summary>

# Lan truyền checked exception qua nhiều tầng lời gọi trong Java

Trong Java, một phương thức có thể gọi phương thức khác, phương thức đó lại gọi xuống tầng thấp hơn. Nếu tầng thấp phát sinh **checked exception** mà không xử lý tại chỗ, ngoại lệ có thể **lan truyền ngược lên chuỗi lời gọi**. Để mã biên dịch, mỗi tầng có khả năng để ngoại lệ checked thoát ra phải khai báo kiểu ngoại lệ tương ứng trong mệnh đề `throws`, hoặc dùng `try-catch` để xử lý hay chuyển đổi nó.

Ví dụ, một ứng dụng đọc cấu hình theo đường `Controller → Service → Repository → File API`. `Files.readString(...)` có thể phát sinh `IOException`. Nếu `Repository`, `Service` và `Controller` đều không xử lý, kiểu `IOException` phải xuất hiện trong hợp đồng `throws` của các phương thức liên quan. Tại một ranh giới có đủ ngữ cảnh, chương trình bắt lỗi, quyết định trả thông báo, dùng dữ liệu dự phòng hoặc dừng thao tác.

**Điểm cốt lõi:** `throws` là **khai báo khả năng phát sinh ngoại lệ checked ở thời điểm biên dịch**. Nó không tự ném, tự bắt, in log hay sửa lỗi. Ngoại lệ chỉ thực sự di chuyển khi chương trình đang chạy và một ngoại lệ được ném.

## 1. Nhắc lại: checked exception là gì?

Mọi đối tượng có thể ném bằng `throw` phải thuộc `Throwable`. Java chia ngoại lệ theo quy tắc kiểm tra lúc biên dịch:

| Nhánh lớp | Checked? | Ví dụ | Nghĩa vụ của phương thức gọi |
| --- | --- | --- | --- |
| `Exception` nhưng không thuộc `RuntimeException` | Có | `IOException`, `SQLException`, `ParseException` | Bắt hoặc khai báo `throws` nếu thao tác có thể ném chúng |
| `RuntimeException` và lớp con | Không | `IllegalArgumentException`, `NullPointerException` | Không bắt buộc `catch` hay `throws` |
| `Error` và lớp con | Không | `OutOfMemoryError`, `LinkageError` | Không bắt buộc `catch` hay `throws` |
| Chính `Throwable` | Có | `new Throwable("...")` | Phải xử lý/khai báo nếu có thể thoát ra |

Vì `IOException` là checked, compiler kiểm tra xem phương thức gọi `Files.readString(path)` có bắt hoặc cho phép `IOException` đi ra ngoài không. Cùng quy tắc áp dụng cho từng lời gọi ở tầng trên. Trình biên dịch kiểm tra *kiểu* ngoại lệ có thể phát sinh theo cấu trúc mã; nó không cần chờ ngoại lệ xảy ra khi chương trình chạy.

Một số API khai báo nhiều kiểu checked, chẳng hạn `IOException` và một ngoại lệ nghiệp vụ do thư viện tự định nghĩa. Khi những kiểu đó có thể thoát ra khỏi một phương thức, `throws` phải bao quát tất cả chúng bằng các kiểu phù hợp hoặc một kiểu cha hợp lệ. `throws Exception` về mặt cú pháp bao quát rất rộng, nhưng thường làm hợp đồng API khó sử dụng hơn `throws IOException` hay một loại cụ thể.

## 2. Chuỗi gọi nhiều tầng và hướng lan truyền

Sơ đồ dưới đây mô tả **chiều gọi phương thức** và **chiều ngoại lệ lan truyền**:

```text
Đi xuống khi gọi:
main() ──► controller() ──► service() ──► repository() ──► readFile()
                                                        │
                                                        │ throw IOException
                                                        ▼
Đi lên khi lỗi:
main() ◄── controller() ◄── service() ◄── repository() ◄─┘
  │
  └── catch IOException hoặc khai báo throws tiếp
```

Khi `readFile()` ném `IOException`:

1. Phần mã sau điểm ném trên đường chạy bình thường của `readFile()` không chạy.
2. Nếu `readFile()` không bắt lỗi, lời gọi trong `repository()` hoàn tất đột ngột.
3. Nếu `repository()` không bắt, lỗi tiếp tục lên `service()`; tương tự với `controller()`.
4. Khi gặp `catch` tương thích đầu tiên trên đường tháo chuỗi lời gọi, chương trình thực thi khối xử lý đó. Các `finally` phù hợp được thực hiện theo quy tắc Java trong quá trình truyền ngoại lệ.
5. Nếu không ai bắt, ngoại lệ có thể thoát khỏi điểm vào của thread và được xử lý như ngoại lệ chưa bắt.

**Cần tách hai câu hỏi.** Compiler hỏi: “Có loại checked exception nào *có thể* đi ra khỏi phương thức này?” Runtime hỏi: “Một đối tượng ngoại lệ *đã thực sự* được ném và hiện được xử lý ở đâu?”. Việc khai báo `throws` giải quyết nghĩa vụ compile-time; việc đi lên stack xảy ra ở runtime.

## 3. Ví dụ Java 17+: lan truyền qua bốn tầng rồi bắt ở biên

Lưu thành `CheckedPropagationDemo.java`:

```java
import java.io.IOException;

public class CheckedPropagationDemo {
    static String readFile() throws IOException {
        throw new IOException("config missing");
    }

    static String repository() throws IOException {
        return readFile();
    }

    static String service() throws IOException {
        return repository();
    }

    static String controller() throws IOException {
        return service();
    }

    public static void main(String[] args) {
        try {
            System.out.println(controller());
        } catch (IOException ex) {
            System.out.println("type=" + ex.getClass().getSimpleName());
            System.out.println("message=" + ex.getMessage());
            System.out.println("handled at main");
        }
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 CheckedPropagationDemo.java
java CheckedPropagationDemo
```

Kết quả chính xác:

```text
type=IOException
message=config missing
handled at main
```

Trong ví dụ này:

- `readFile()` là nơi tạo và ném `IOException`.
- `repository()`, `service()` và `controller()` không có `catch`; mỗi phương thức khai báo `throws IOException` để cho phép lỗi đi tiếp.
- `main()` bắt và xử lý bằng `catch (IOException ex)`, nên nó không cần `throws IOException`.
- Lệnh `System.out.println(controller())` không thể in ra kết quả thành công trong lần chạy này: lời gọi `controller()` ném ngoại lệ trước khi có giá trị làm tham số cho `println`.
- **Cùng một đối tượng ngoại lệ** có thể truyền lên nhiều tầng nếu không được thay thế. Mỗi `throws` không tạo ngoại lệ mới.

Để tránh phụ thuộc vào hệ thống tệp hoặc dữ liệu bên ngoài, ví dụ cố ý ném `IOException` trực tiếp tại tầng thấp. Với ứng dụng thật, `readFile()` thường gọi `Files.readString(path)` và để ngoại lệ API đó truyền ra.

## 4. Vì sao mỗi tầng vẫn phải khai báo `throws`?

Xét một phiên bản sai:

```java
import java.io.IOException;

class MissingThrowsExample {
    static String storage() throws IOException {
        throw new IOException("failed");
    }

    static String service() {
        return storage(); // compile error: unreported exception IOException
    }
}
```

Đây là ví dụ **cố ý không biên dịch**. Vì `storage()` được khai báo có thể ném `IOException`, phương thức `service()` phải bắt nó hoặc khai báo `throws IOException`.

Cách sửa cho phép lan truyền:

```java
static String service() throws IOException {
    return storage();
}
```

Câu trên là đoạn sửa **bên trong lớp `MissingThrowsExample`**, thay cho phương thức `service()` cũ. Một cách sửa khác là bao lời gọi trong `try-catch` nếu `service()` có quy tắc xử lý phù hợp.

Nếu sửa `service()` theo hướng `throws IOException`, mã gọi `service()` tại tầng cao hơn cũng phải đáp ứng yêu cầu tương tự. Đây là **chuỗi nghĩa vụ checked exception**, kéo dài cho tới khi một tầng xử lý hoặc một entry point tiếp tục khai báo lỗi.

Một phương thức có thể khai báo `throws IOException` dù trong đường chạy hiện tại nó không thực sự ném ngoại lệ. Khai báo mô tả một khả năng theo hợp đồng, không buộc phương thức ném lỗi mỗi lần được gọi. Trình biên dịch cũng không tự suy ra rằng một phương thức mang tên `loadSafely` chắc chắn an toàn; nó xem chữ ký và quy tắc phân tích ngoại lệ của mã.

## 5. Ba lựa chọn thực tế ở mỗi tầng

| Lựa chọn | Khi nào dùng | Hệ quả cho tầng gọi |
| --- | --- | --- |
| **Lan truyền nguyên ngoại lệ** bằng `throws IOException` | Tầng hiện tại không có đủ ngữ cảnh để giải quyết; kiểu lỗi vẫn có ý nghĩa | Tầng trên phải xử lý/khai báo ngoại lệ checked |
| **Bắt và xử lý** bằng `catch` | Có quy tắc phục hồi đáng tin: dữ liệu dự phòng, thử lại có giới hạn, thông báo kết quả rõ ràng | Nếu xử lý xong và không phát sinh checked exception mới, nghĩa vụ với ngoại lệ đó có thể kết thúc |
| **Chuyển đổi ngoại lệ** bằng `throw new DomainException("...", cause)` | Ranh giới tầng cần hợp đồng riêng; không muốn API nghiệp vụ phụ thuộc trực tiếp vào lỗi hạ tầng | Tầng trên xử lý/khai báo **kiểu mới** nếu nó là checked |

Một tầng không nên tự động “bắt cho hết lỗi” chỉ để tránh viết `throws`. Nếu không thể phục hồi, việc bắt rồi trả dữ liệu giả thành công có thể che giấu hỏng dữ liệu hoặc khiến tầng trên ra quyết định sai.

Ngược lại, **không phải tầng nào cũng phải lan truyền**. Chỉ cần một tầng có khả năng xử lý phù hợp thì chuỗi có thể kết thúc tại đó.

## 6. Ví dụ Java 17+: một tầng xử lý bằng giá trị dự phòng

Lưu thành `HandledAtServiceDemo.java`:

```java
import java.io.IOException;

public class HandledAtServiceDemo {
    static String storage(boolean available) throws IOException {
        if (!available) {
            throw new IOException("storage unavailable");
        }
        return "live-config";
    }

    static String repository(boolean available) throws IOException {
        return storage(available);
    }

    static String service(boolean available) {
        try {
            return repository(available);
        } catch (IOException ex) {
            System.out.println("fallback because: " + ex.getMessage());
            return "default-config";
        }
    }

    static void controller(boolean available) {
        System.out.println("config=" + service(available));
    }

    public static void main(String[] args) {
        controller(false);
        controller(true);
    }
}
```

```bash
javac -encoding UTF-8 HandledAtServiceDemo.java
java HandledAtServiceDemo
```

Kết quả:

```text
fallback because: storage unavailable
config=default-config
config=live-config
```

Ở `service()`, lỗi `IOException` được xử lý bằng fallback **có chủ đích**. Vì `catch` trả về cấu hình mặc định và không ném checked exception mới, `service()` không cần `throws IOException`. Do đó `controller()` cũng không cần khai báo lỗi này. Mã chỉ minh họa cơ chế; trong hệ thống thật, cần xác định fallback có an toàn, có được phép theo nghiệp vụ và có làm mất thông tin chẩn đoán hay không.

Điểm quan trọng: **xử lý thành công ở tầng dưới làm dừng lan truyền ngoại lệ tại tầng đó**. Không có đối tượng ngoại lệ nào bị chuyển tiếp lên `controller()` từ lần gọi thất bại này.

## 7. Chuyển đổi checked exception giữa các tầng và giữ nguyên nhân

Trong hệ thống nhiều tầng, không phải lúc nào lớp API cũng nên biết `IOException`, `SQLException` hoặc loại lỗi cụ thể của thư viện hạ tầng. Một tầng dịch vụ có thể biến chúng thành ngoại lệ phù hợp với ngữ nghĩa nghiệp vụ.

Lưu thành `ExceptionTranslationDemo.java`:

```java
import java.io.IOException;

public class ExceptionTranslationDemo {
    static class ConfigLoadException extends Exception {
        ConfigLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static String storage() throws IOException {
        throw new IOException("disk read failed");
    }

    static String repository() throws IOException {
        return storage();
    }

    static String service() throws ConfigLoadException {
        try {
            return repository();
        } catch (IOException ex) {
            throw new ConfigLoadException("cannot load configuration", ex);
        }
    }

    static void controller() {
        try {
            System.out.println(service());
        } catch (ConfigLoadException ex) {
            System.out.println("type=" + ex.getClass().getSimpleName());
            System.out.println("message=" + ex.getMessage());
            System.out.println("causeType="
                    + ex.getCause().getClass().getSimpleName());
            System.out.println("causeMessage=" + ex.getCause().getMessage());
        }
    }

    public static void main(String[] args) {
        controller();
    }
}
```

```bash
javac -encoding UTF-8 ExceptionTranslationDemo.java
java ExceptionTranslationDemo
```

Kết quả:

```text
type=ConfigLoadException
message=cannot load configuration
causeType=IOException
causeMessage=disk read failed
```

Đây là một ví dụ về **dịch hợp đồng ngoại lệ**:

1. `storage()` và `repository()` dùng kiểu checked `IOException` của tầng I/O.
2. `service()` bắt `IOException` và **tạo một đối tượng mới** `ConfigLoadException`, cũng là checked vì kế thừa `Exception` nhưng không kế thừa `RuntimeException`.
3. `service()` khai báo `throws ConfigLoadException`; tầng trên chỉ cần biết hợp đồng mới.
4. `controller()` bắt `ConfigLoadException` và vẫn có thể truy vết nguyên nhân ban đầu qua `getCause()`.

**Không viết** `throw new ConfigLoadException(ex.getMessage(), null);` hoặc chỉ tạo `new Exception(ex.getMessage())` nếu mục đích là bổ sung ngữ cảnh mà vẫn bảo toàn nguyên nhân. Hãy truyền đối tượng `ex` làm `cause`. Khi log, tránh ghi cùng một stack trace lặp lại ở mọi tầng.

Với lớp ngoại lệ nghiệp vụ, chọn loại checked hay unchecked tùy hợp đồng: một lỗi có thể xử lý có ý nghĩa riêng thường thích hợp với checked; vi phạm bất biến, tham số bất hợp lệ hoặc lỗi lập trình thường dùng unchecked. Không có quy tắc rằng mọi `IOException` nhất thiết phải bọc thành `RuntimeException`.

## 8. Khai báo nhiều kiểu checked exception và quan hệ lớp cha

Một phương thức có thể phát sinh nhiều loại checked độc lập:

```java
import java.io.IOException;
import java.text.ParseException;

class MultipleCheckedExample {
    static String readAndParse(int mode) throws IOException, ParseException {
        if (mode == 1) {
            throw new IOException("I/O failed");
        }
        if (mode == 2) {
            throw new ParseException("Invalid format", 0);
        }
        return "OK";
    }

    static String service(int mode) throws IOException, ParseException {
        return readAndParse(mode);
    }
}
```

Đoạn này biên dịch được (dù không có `main` để chạy độc lập). `IOException` và `ParseException` là hai kiểu checked khác nhau. Vì `service()` không bắt, nó phải khai báo cả hai loại hoặc một kiểu cha phù hợp như `Exception`; khai báo cụ thể giúp người gọi biết các trường hợp cần xử lý.

Trong một `catch`, có thể dùng multi-catch nếu những kiểu không có quan hệ lớp cha/con với nhau:

```java
try {
    MultipleCheckedExample.service(1);
} catch (IOException | ParseException ex) {
    System.out.println("failed: " + ex.getMessage());
}
```

Đây là đoạn minh họa nằm trong một phương thức và có thể truy cập lớp `MultipleCheckedExample`. Nếu bắt kiểu cha `Exception` thay thế, nhiều loại lỗi sẽ bị gom chung, đôi khi làm mất quyết định xử lý riêng.

**Quan hệ kế thừa quyết định phạm vi bao phủ của `throws`:** nếu phương thức con có thể ném `FileNotFoundException` thì `throws IOException` là đủ, vì `FileNotFoundException extends IOException`. Không cần viết đồng thời `throws FileNotFoundException, IOException` trừ khi muốn ghi chú rõ để người đọc; về quy tắc biên dịch, vế sau đã bao quát vế trước.

Tương tự, `throws Exception` bao quát mọi checked exception thuộc nhánh `Exception`, nhưng không có nghĩa bao quát một checked `Throwable` trực tiếp không thuộc `Exception`. `throws Throwable` là loại quá rộng cho API thông thường vì còn bao phủ cả các lỗi ở nhánh `Error` trong hợp đồng.

## 9. `throws` ở phương thức và constructor: giới hạn thiết kế

`throws` được phép trong khai báo phương thức và constructor:

```java
import java.io.IOException;

class ResourceHolder {
    ResourceHolder(String location) throws IOException {
        if (location == null || location.isBlank()) {
            throw new IOException("location missing");
        }
    }
}
```

Nếu gọi `new ResourceHolder("")`, trình biên dịch yêu cầu bên gọi `catch` hoặc khai báo `throws IOException`. Nghĩa vụ tương tự lời gọi phương thức. Đoạn này minh họa lớp hợp lệ nhưng không có `main`.

### Ghi nhớ quy tắc ghi đè phương thức

Khi ghi đè (*override*) một phương thức, phiên bản ghi đè **không được khai báo thêm hoặc mở rộng các checked exceptions** ngoài những gì hợp đồng phương thức cha cho phép. Nó có thể khai báo ít hơn hoặc kiểu con phù hợp.

Ví dụ hợp lệ:

```java
import java.io.FileNotFoundException;
import java.io.IOException;

interface ConfigSource {
    String load() throws IOException;
}

class FileSource implements ConfigSource {
    @Override
    public String load() throws FileNotFoundException {
        throw new FileNotFoundException("missing");
    }
}

class InMemorySource implements ConfigSource {
    @Override
    public String load() {
        return "local";
    }
}
```

`FileNotFoundException` là con của `IOException` nên hợp lệ. `InMemorySource` không khai báo checked exception nào cũng hợp lệ. Nhưng nếu một implementation cố khai báo `throws Exception`, nó mở rộng hợp đồng checked exception so với `IOException` và bị từ chối lúc biên dịch.

Khi biến được khai báo kiểu `ConfigSource`, lời gọi `source.load()` vẫn phải xét hợp đồng `throws IOException` của interface tại compile time, dù đối tượng thực tế là `InMemorySource` không ném checked exception.

**Lưu ý:** Overriding không được thêm checked exception mới; nó vẫn có thể phát sinh unchecked exception. Khả năng compiler kiểm tra là một phần hợp đồng kiểu, không phải cam kết rằng mã runtime hoàn toàn không lỗi.

## 10. `throws` tại `main` có thể đưa ngoại lệ đến đâu?

Java cho phép khai báo:

```java
import java.io.IOException;

class MainThrowsExample {
    public static void main(String[] args) throws IOException {
        perform();
    }

    static void perform() throws IOException {
        throw new IOException("operation failed");
    }
}
```

Đây là chương trình **biên dịch được**, nhưng nếu chạy, ngoại lệ thoát ra khỏi `main` và được xử lý như ngoại lệ chưa bắt của thread khởi chạy. JVM/JDK thông thường in thông tin lỗi và stack trace; nội dung dòng/đường dẫn phụ thuộc môi trường nên không cần cố định stdout.

Khai báo `throws IOException` tại `main` giải quyết vấn đề biên dịch, **không đồng nghĩa đã xử lý sự cố**. Trong chương trình dòng lệnh, có thể cố ý để lỗi chấm dứt chương trình; trong một dịch vụ, API hoặc tác vụ nền, ranh giới thường cần quy tắc ghi nhận, phản hồi và dọn dẹp phù hợp.

Vì ngoại lệ không tự động chạy lại phương thức thất bại, không nên hình dung `throws` như một phép “chuyển việc xử lý cho JVM và sau đó tiếp tục bình thường”. Nếu không có `catch` phù hợp, đường thực thi của lời gọi đó kết thúc đột ngột.

## 11. `try-with-resources` và lỗi phát sinh ở nhiều bước

Trong các ứng dụng I/O, một phương thức có thể lỗi khi thực hiện nghiệp vụ **và** khi đóng tài nguyên. `try-with-resources` quản lý việc đóng `AutoCloseable` theo thứ tự ngược với lúc khởi tạo. Nếu thân `try` ném ngoại lệ và `close()` cũng ném ngoại lệ, lỗi đóng thường được lưu trong `getSuppressed()` của ngoại lệ chính.

Lưu thành `SuppressedPropagationDemo.java`:

```java
import java.io.IOException;

public class SuppressedPropagationDemo {
    static class BrokenResource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    static void storage() throws IOException {
        try (BrokenResource resource = new BrokenResource()) {
            throw new IOException("read failed");
        }
    }

    static void service() throws IOException {
        storage();
    }

    public static void main(String[] args) {
        try {
            service();
        } catch (IOException ex) {
            System.out.println("primary=" + ex.getMessage());
            System.out.println("suppressedCount=" + ex.getSuppressed().length);
            System.out.println("suppressed="
                    + ex.getSuppressed()[0].getMessage());
        }
    }
}
```

```bash
javac -encoding UTF-8 SuppressedPropagationDemo.java
java SuppressedPropagationDemo
```

Kết quả:

```text
primary=read failed
suppressedCount=1
suppressed=close failed
```

Ngoại lệ `IOException("read failed")` lan từ `storage()` lên `service()` rồi `main()`, giống một checked exception thông thường. `IOException("close failed")` **không thay thế lỗi chính**, mà được đính kèm vào `getSuppressed()`. Từ đó có thể truy vết đầy đủ thay vì mất lỗi từ thao tác đọc.

Nếu chỉ có lỗi khi đóng tài nguyên mà không có lỗi trong thân `try`, lỗi đóng có thể trở thành ngoại lệ chính. `try-with-resources` không miễn yêu cầu khai báo checked exception phát sinh từ việc đóng; compiler vẫn kiểm tra các kiểu checked mà việc tự động đóng tài nguyên có thể ném.

## 12. Không đánh đồng `throws` với bất đồng bộ và lambda

Khi dùng callback, lambda hoặc executor, checked exception vẫn phải tuân theo chữ ký của functional interface. Ví dụ, `Supplier<T>.get()` **không khai báo checked exception**. Vì vậy đoạn sau **cố ý không biên dịch**:

```java
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

class InvalidSupplierExample {
    static String read() throws IOException {
        throw new IOException("read failed");
    }

    static void submit() {
        CompletableFuture.supplyAsync(() -> read());
        // Lỗi: lambda của Supplier không thể để IOException checked thoát ra.
    }
}
```

Việc gọi API có chữ `Async` không bỏ qua quy tắc checked exception. Nếu thực sự muốn chuyển lỗi qua `CompletableFuture`, cần thiết kế một kết quả lỗi phù hợp hoặc bọc nguyên nhân trong unchecked wrapper theo hợp đồng của API. Khi `join()` quan sát một tác vụ hoàn thành bất thường, một `CompletionException` có thể xuất hiện với nguyên nhân gốc. Đây là cơ chế truyền thất bại của thư viện đồng thời; không phải cứ có `throws IOException` là lỗi tự động truyền sang thread gọi.

Các ví dụ đồng bộ ở phần đầu bài minh họa rõ cơ chế `throws` qua chuỗi gọi trực tiếp; vấn đề async chỉ là điểm cần lưu ý nếu thiết kế được mở rộng.

## 13. `InterruptedException` là checked exception cần chính sách riêng

Một ví dụ checked exception quan trọng trong chương trình có thread là `InterruptedException`, thường phát sinh từ thao tác chờ như `Thread.sleep(...)`. Tầng trên có thể để lan truyền:

```java
static void waitForWork() throws InterruptedException {
    Thread.sleep(100);
}

static void service() throws InterruptedException {
    waitForWork();
}
```

Nếu tầng này không được phép khai báo thêm `InterruptedException` do hợp đồng API, khi bắt nó thường phải **khôi phục cờ interrupt** rồi kết thúc/hủy thao tác theo chính sách:

```java
static void serviceWithoutCheckedThrows() {
    try {
        Thread.sleep(100);
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
    }
}
```

Cả hai đoạn là phương thức minh họa trong một lớp. Việc gọi `interrupt()` là để báo cho tầng gọi biết thread đang có yêu cầu hủy; trong ứng dụng thật, cần tránh tiếp tục công việc như chưa có gì xảy ra. Không nên áp dụng “nuốt ngoại lệ và tiếp tục” như quy tắc chung cho `InterruptedException`.

## 14. Lỗi thường gặp khi truyền checked exception qua các tầng

| Sai lầm | Vấn đề | Hướng xử lý |
| --- | --- | --- |
| Chỉ khai báo `throws IOException` ở tầng thấp rồi quên khai báo tại tầng gọi kế tiếp. | Compiler báo `unreported exception ... must be caught or declared`. | Bổ sung `throws` ở tầng cần lan truyền hoặc xử lý tại tầng đó. |
| Nghĩ `throws` tự bắt và xử lý. | Ngoại lệ vẫn lan khi runtime có `throw` thực tế. | Đặt `catch` tại ranh giới có đủ thông tin xử lý. |
| Bao mọi phương thức bằng `catch (Exception ex) { }`. | Nuốt lỗi, mất nguyên nhân; tầng cao có thể nhầm là thành công. | Chỉ bắt loại có thể xử lý; truyền hoặc chuyển đổi lỗi nếu cần. |
| Mọi tầng đều `catch`, ghi log rồi `throw ex;`. | Trùng log, phân tán trách nhiệm ghi nhận; mã trở nên dài mà không bổ sung ý nghĩa. | Chọn một tầng ghi log/đưa lỗi ra ngoài phù hợp; chỉ bắt khi cần ngữ cảnh hoặc phục hồi. |
| Chuyển ngoại lệ thành `new RuntimeException(ex.getMessage())`. | Mất `cause` và chuỗi stack trace gốc. | Dùng `new RuntimeException("ngữ cảnh", ex)` khi hợp đồng quyết định dùng unchecked. |
| Khai báo `throws Exception` hoặc `throws Throwable` ở mọi tầng. | Hợp đồng kém chính xác, khó phân biệt nhóm lỗi. | Dùng loại checked cụ thể hoặc một ngoại lệ miền nghiệp vụ. |
| Đưa ngoại lệ checked mới vào phương thức override dù interface không cho phép. | Vi phạm quy tắc ghi đè. | Thiết kế interface từ đầu hoặc dùng quy tắc dịch ngoại lệ thích hợp. |
| Trả giá trị mặc định dù dữ liệu bị lỗi nghiêm trọng. | B che khuất sự cố và có thể dẫn tới trạng thái không nhất quán. | Chỉ dùng fallback có căn cứ nghiệp vụ; nếu không thì lan truyền lỗi. |
| Bỏ qua `getSuppressed()` khi đóng tài nguyên thất bại. | Thiếu thông tin thứ cấp có thể giúp điều tra. | Dùng `try-with-resources`, ghi nhận cả ngoại lệ chính và suppressed khi cần. |

## 15. Cách đọc và thiết kế hợp đồng ngoại lệ của một module

Với từng tầng, hãy trả lời:

1. **Nguồn ngoại lệ:** phương thức nào phát sinh? Đây là checked hay unchecked? API đã khai báo `throws` gì?
2. **Ý nghĩa của tầng:** tầng này có đủ thông tin phục hồi không? Ví dụ thao tác đọc cấu hình lỗi thì có dữ liệu mặc định được phép sử dụng không?
3. **Quyết định:** bắt và xử lý, truyền nguyên loại qua `throws`, hay chuyển đổi sang một checked exception phù hợp ranh giới?
4. **Hợp đồng với tầng gọi:** chữ ký có công bố đúng loại checked exception có thể đi ra không? Nếu override/interface, có vi phạm giới hạn không?
5. **Thông tin chẩn đoán:** khi bọc ngoại lệ đã giữ `cause` chưa? Nếu đóng tài nguyên, `suppressed` có được giữ lại không?
6. **Điểm kết thúc:** tầng nào biến lỗi thành thông báo, kết quả có kiểu, phản hồi API hoặc quyết định kết thúc tác vụ? Đừng để mỗi tầng tự quyết định theo cách mâu thuẫn.

Một sơ đồ thiết kế hợp lý, nếu mỗi tầng có vai trò tách bạch:

```text
Repository: throws IOException
        |
        v
Service: catches IOException
         throws ConfigLoadException (cause = IOException)
        |
        v
Controller: catches ConfigLoadException
            -> tạo phản hồi phù hợp
```

Sơ đồ này không phải khuôn áp dụng cho mọi dự án. Nếu một API của service vốn là API I/O thuần và `IOException` có ý nghĩa cho bên gọi, có thể truyền nguyên loại. Nếu tầng service có thể phục hồi an toàn, nó có thể kết thúc chuỗi ngoại lệ ở chính mình. Quyết định dựa trên hợp đồng và khả năng xử lý, không dựa trên việc muốn ít `throws` hơn bằng mọi giá.

## 16. Câu hỏi tự kiểm tra

**Câu 1.** `repository() throws IOException` được gọi trong `service()`. Nếu `service()` không bắt lỗi thì có phải khai báo `throws IOException` không?  
**Có.** Nếu ngoại lệ checked có thể đi ra khỏi `service()`, compiler đòi hỏi khai báo loại đó hoặc kiểu cha phù hợp.

**Câu 2.** `throws IOException` có tạo một đối tượng `IOException` tại mỗi tầng không?  
**Không.** Nó chỉ khai báo hợp đồng. Không bắt/bọc thì đối tượng ngoại lệ ban đầu có thể lan qua nhiều lời gọi.

**Câu 3.** Nếu `service()` bắt `IOException` và trả về fallback hợp lệ, `controller()` có bắt buộc khai báo `throws IOException` chỉ vì từng có `IOException` ở tầng dưới không?  
**Không.** Nếu lỗi đã được xử lý hoàn toàn và không có đường nào để checked exception đó thoát khỏi `service()`, tầng trên không có nghĩa vụ đó.

**Câu 4.** Nếu `service()` bắt `IOException` và ném `ConfigLoadException extends Exception`, tầng gọi phải tuân thủ hợp đồng nào?  
**ConfigLoadException.** Đây là checked exception mới; lưu `IOException` cũ qua `getCause()` để không mất ngữ cảnh.

**Câu 5.** Khai báo `throws IOException` có bao quát `FileNotFoundException` không?  
**Có.** Vì `FileNotFoundException` là lớp con của `IOException`.

**Câu 6.** Một implementation có thể thêm `throws Exception` khi phương thức interface chỉ khai báo `throws IOException` không?  
**Không.** Phương thức ghi đè không được mở rộng checked exceptions được cho phép bởi phương thức cha.

**Câu 7.** Nếu `main() throws IOException` thì chương trình đã bắt lỗi chưa?  
**Chưa.** Đây vẫn là khai báo lan truyền. Nếu lỗi thực sự thoát `main` mà không có handler phù hợp, nó là ngoại lệ chưa bắt.

**Câu 8.** Một lambda của `Supplier<String>` có thể ném `IOException` checked chỉ vì nó được đưa vào `CompletableFuture.supplyAsync` không?  
**Không.** `Supplier.get()` không khai báo checked exception; phải thiết kế cơ chế bọc/chuyển kết quả phù hợp.

## Tham khảo chuẩn

- [JLS Java SE 21 — §11.1.1 Các lớp checked và unchecked](https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html#jls-11.1.1).
- [JLS Java SE 21 — §11.2 Kiểm tra checked exception lúc biên dịch](https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html#jls-11.2).
- [JLS Java SE 21 — §11.2.3 Exception Checking](https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html#jls-11.2.3).
- [JLS Java SE 21 — §8.4.6 Method Throws](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.4.6).
- [JLS Java SE 21 — §8.4.8.3 Giới hạn throws khi ghi đè](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.4.8.3).
- [JLS Java SE 21 — §14.20 Cách try, catch và finally hoạt động](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20).

**Ghi nhớ:** `throws` cho phép một tầng **công bố** loại checked exception mà nó chưa xử lý, và các tầng trên tiếp tục có trách nhiệm bắt hoặc khai báo. Chuỗi truyền lỗi chỉ dừng khi có xử lý phù hợp hoặc khi ngoại lệ thoát ra ngoài điểm vào. Khi cần chuyển đổi lỗi ở ranh giới tầng, hãy giữ `cause` và khai báo đúng checked exception mới.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="throws-clause-006">Khi nào nên catch và khi nào nên tiếp tục throws</a>

<details>
<summary>Xem chi tiết</summary>

# Khi nào nên `catch` và khi nào nên tiếp tục `throws` trong Java?

Trong Java, khi một phương thức có thể phát sinh ngoại lệ, lập trình viên thường đứng trước hai lựa chọn: **bắt bằng `catch` ở tầng hiện tại** hoặc **để ngoại lệ tiếp tục lan truyền tới bên gọi**, khai báo `throws` khi cần. Đây là một quyết định về *trách nhiệm xử lý lỗi*, không chỉ là cách làm cho trình biên dịch chấp nhận chương trình.

Nguyên tắc nền tảng: **hãy `catch` tại nơi thực sự có thể xử lý có ý nghĩa; nếu tầng hiện tại không thể khắc phục hay quyết định phản hồi phù hợp, hãy để lỗi lan truyền theo hợp đồng**. Một `catch` không phục hồi được mà chỉ trả giá trị giả hoặc bỏ qua ngoại lệ thường nguy hiểm hơn việc để tầng cao hơn nhìn thấy sự cố.

Cũng cần tách biệt ba việc:

1. **Phát hiện và ném lỗi**: thao tác bên trong ném đối tượng ngoại lệ bằng `throw`, hoặc API/JVM tự phát sinh.
2. **Khai báo khả năng lan truyền**: mệnh đề `throws` trong chữ ký phương thức mô tả các *checked exception* có thể thoát ra; nó không tự xử lý hay tự ném lỗi.
3. **Bắt và xử lý**: `try/catch` chuyển luồng thực thi sang một handler tương thích khi exception được ném.

Một phương thức có thể kết hợp: bắt một loại ngoại lệ để phục hồi, ném lại loại khác, và vẫn khai báo `throws` cho những lỗi chưa xử lý được.

## 1. Ví dụ trực quan: cùng một sự cố, hai tầng chịu trách nhiệm khác nhau

Giả sử ứng dụng cần nạp cấu hình từ tệp. Hàm đọc tệp biết *đọc thất bại* nhưng không biết ứng dụng có được phép dùng cấu hình mặc định không. Trong khi đó, tầng khởi động ứng dụng nắm chính sách: nếu thiếu cấu hình bắt buộc thì dừng khởi động; nếu chỉ thiếu thông tin tùy chọn thì dùng giá trị dự phòng.

Một phương thức đọc ở tầng thấp có thể viết:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class SettingsReader {
    static String read(Path file) throws IOException {
        return Files.readString(file);
    }
}
```

Đây là **lớp minh họa không có `main`**, việc đọc có thành công hay không phụ thuộc tệp trên máy. `Files.readString` có thể ném `IOException`, là checked exception. `SettingsReader.read` không có chính sách sử dụng giá trị dự phòng, nên khai báo `throws IOException` để bên gọi quyết định.

Nếu tầng khởi động có phương án dự phòng hợp lệ:

```java
static String resolveOptionalSetting(Path path) {
    try {
        return SettingsReader.read(path);
    } catch (IOException e) {
        return "default";
    }
}
```

Đây là **phương thức minh họa**, giả sử `SettingsReader` đã được khai báo. Nó chỉ đúng nếu yêu cầu nghiệp vụ *cho phép dùng "default" với mọi `IOException` từ thao tác đó*. Nếu chỉ được phép dùng mặc định khi tệp không tồn tại, không nên bắt toàn bộ `IOException` rồi che luôn lỗi quyền truy cập, thiết bị hoặc dữ liệu hỏng. Khi đó phải phân loại cụ thể hơn, chẳng hạn `NoSuchFileException`, và để các tình huống khác lan truyền.

Nếu cấu hình là bắt buộc, tầng khởi động có thể bắt lỗi ở ranh giới cuối để in thông báo phù hợp và quyết định không tiếp tục. Sự khác biệt nằm ở **hợp đồng của từng tầng**, không nằm ở việc exception đã “có thể catch được” hay chưa.

## 2. `catch` giải quyết điều gì, `throws` giải quyết điều gì?

| Góc nhìn | `catch` | `throws` |
| --- | --- | --- |
| Vị trí | Khối xử lý đi kèm `try` | Chữ ký phương thức |
| Công việc | Chọn một hành động khi gặp exception phù hợp | Công bố khả năng exception lan ra caller |
| Có tự ngăn lỗi lan truyền? | Có, **nếu handler xử lý xong bình thường** | Không |
| Có bắt buộc với checked exception? | Một lựa chọn để đáp ứng `catch-or-declare` | Lựa chọn còn lại để đáp ứng `catch-or-declare` |
| Có tự làm thao tác thành công? | Không; handler phải thực sự khôi phục hoặc chuyển sang kết quả được phép | Không |
| Có thể cùng tồn tại? | Có: bắt một loại, rồi ném tiếp loại khác | Có: khai báo loại checked vẫn thoát ra |
| Có cần với unchecked exception? | Không bắt buộc, nhưng có thể chủ động bắt | Không bắt buộc, nhưng có thể khai báo để tài liệu hóa |

Quy tắc **catch-or-declare** áp dụng cho checked exception: nếu từ thân phương thức có thể thoát ra một checked exception, phương thức phải **bắt hoặc khai báo** nó (hay kiểu cha phù hợp) theo các quy tắc phân tích ngoại lệ của Java. `RuntimeException`, `Error` và lớp con là unchecked; bên gọi không bị trình biên dịch buộc bắt hoặc khai báo chúng.

Ví dụ:

```java
import java.io.IOException;

class ChoiceExample {
    static void checkedOperation() throws IOException {
        throw new IOException("Mất kết nối");
    }

    static void propagate() throws IOException {
        checkedOperation(); // Để IOException đi lên caller.
    }

    static void handleLocally() {
        try {
            checkedOperation();
        } catch (IOException e) {
            System.out.println("Đã ghi nhận: " + e.getMessage());
        }
    }
}
```

Đây là lớp hợp lệ không có `main`. `propagate()` buộc khai báo `throws IOException`, còn `handleLocally()` không cần khai báo vì đã bắt lỗi. Tuy nhiên, `handleLocally()` chỉ minh họa cú pháp: in message rồi kết thúc không tự bảo đảm công việc ban đầu đã thành công. Ứng dụng thật phải xác định có được tiếp tục sau thất bại hay không.

## 3. Những dấu hiệu cho thấy nên `catch` ngay tại đây

### 3.1. Tầng hiện tại có phương án khôi phục an toàn

Nếu một phương án thay thế đã được hợp đồng chấp nhận và bạn biết chắc trạng thái vẫn hợp lệ, `catch` là hợp lý. Ví dụ một cấu hình tùy chọn có thể dùng mặc định khi tệp *không tồn tại*.

Quan trọng là nhận diện đủ hẹp để **không che những lỗi khác**. Phải xác định fallback có an toàn, có được phép, và liệu việc mất dữ liệu vừa xảy ra có dẫn đến hành vi sai tiếp theo hay không.

### 3.2. Có thể yêu cầu người dùng điều chỉnh đầu vào

Nếu người dùng nhập `"abc"` vào trường số lượng, `Integer.parseInt` có thể ném `NumberFormatException`. Tầng nhận dữ liệu có thể bắt lỗi và trả thông báo xác thực, vì đây là nơi biết giao diện và hướng xử lý.

```java
static String validateQuantity(String input) {
    try {
        int quantity = Integer.parseInt(input);
        return quantity > 0 ? "Hợp lệ" : "Phải lớn hơn 0";
    } catch (NumberFormatException e) {
        return "Phải nhập số nguyên";
    }
}
```

Đây là **phương thức minh họa**, không có `main`. Việc bắt ở ranh giới nhập liệu phù hợp vì kết quả hợp lệ hoặc lý do từ chối đều là một phần bình thường của hợp đồng. Không nên để lỗi nhập liệu thô tự động đi qua nhiều tầng rồi biến thành “sự cố máy chủ” nếu ranh giới đã đủ thông tin để diễn giải.

### 3.3. Ranh giới ứng dụng phải chuyển lỗi thành phản hồi

Ở một CLI, ranh giới cuối có thể in thông báo và kết thúc với mã thoát phù hợp. Với HTTP, tầng giao tiếp có thể chuyển exception/kết quả ứng dụng thành phản hồi theo hợp đồng API. Với một job nền, tầng điều phối có thể đánh dấu job thất bại và áp dụng chính sách thử lại đã được định nghĩa.

Đây thường là **nơi biết phản ứng cuối**. Ví dụ `400 Bad Request` hoặc `500 Internal Server Error` là lựa chọn giao thức HTTP, không phải hai loại ngoại lệ Java. Không ánh xạ tất cả `IllegalArgumentException` thành 400, vì đôi khi nó biểu thị bug ở dữ liệu nội bộ; phải xét nguồn lỗi và hợp đồng.

### 3.4. Phải thực hiện một hành động có ý nghĩa rồi quyết định lại

Một handler có thể ghi nhận một bước thất bại, hoàn tác thay đổi cục bộ hoặc bổ sung bối cảnh. Sau đó có thể ném lại bằng `throw e` nếu tầng hiện tại chưa khôi phục được. Như vậy, **bắt lỗi không nhất thiết đồng nghĩa nuốt lỗi**.

```java
try {
    operation();
} catch (IOException e) {
    recordFailedAttempt(e);
    throw e;
}
```

Đây là **khung mã minh họa**, giả sử các phương thức phụ trợ đã được định nghĩa và phương thức chứa đoạn mã cho phép lan truyền `IOException`. Công việc của `recordFailedAttempt` phải bản thân nó an toàn, không được âm thầm thay lỗi gốc; nếu nó ném exception mới, caller có thể thấy lỗi mới thay vì lỗi ban đầu.

## 4. Những dấu hiệu cho thấy nên tiếp tục `throws`

### 4.1. Không có thông tin để quyết định cách phục hồi

Thư viện lưu trữ dữ liệu không biết nên hiển thị hộp thoại, chuyển sang tệp dự phòng, trả mã HTTP hay kết thúc chương trình. Nếu không có cách xử lý bảo toàn đúng hợp đồng, hãy để `IOException` hoặc ngoại lệ thích hợp lan ra caller.

Không nên làm việc này:

```java
// Phản ví dụ: trả dữ liệu giả khi đọc thất bại.
static String unsafeRead(Path path) {
    try {
        return Files.readString(path);
    } catch (IOException e) {
        return ""; // Không phân biệt được tệp rỗng với lỗi đọc.
    }
}
```

Đây là phương thức minh họa, cần các import `Files`, `Path` và `IOException`. Chuỗi rỗng chỉ phù hợp nếu nó thực sự được định nghĩa là fallback an toàn trong mọi trường hợp. Nếu không, cách viết này khiến tầng gọi hiểu lầm rằng thao tác đã thành công.

### 4.2. Hợp đồng API vốn đã công bố checked exception

Một phương thức `readFile() throws IOException` có thể giữ nguyên hợp đồng ở các tầng chuyển tiếp. Tầng caller cao hơn biết cách xử lý lỗi I/O, ví dụ đóng phiên làm việc, đổi tệp hoặc hủy chức năng.

Lưu ý `throws` không *thay đổi* exception hay bắt nó. Nó chỉ làm rõ hợp đồng mà Java kiểm tra lúc biên dịch cho checked exception. Nếu exception không có handler phù hợp ở toàn bộ call stack, nó vẫn có thể thoát khỏi luồng đang chạy và làm thao tác thất bại.

### 4.3. Lỗi phát sinh từ bất biến hoặc tiền điều kiện mà tầng hiện tại không thể khắc phục

Nếu một API phát hiện tham số lập trình viên truyền sai, `IllegalArgumentException` có thể phù hợp hơn một fallback giả. Đây là unchecked exception: **không cần viết `throws IllegalArgumentException`**; chỉ để nó lan truyền đã đủ về mặt ngôn ngữ.

Ví dụ `Objects.requireNonNull(value)` có thể ném `NullPointerException` khi đối số là `null`. Tầng nhận giá trị nên bảo đảm hợp đồng; tầng trung gian không nên bắt rồi “tiếp tục như không có gì” khi thiếu dữ liệu bắt buộc.

### 4.4. Có thể bị hỏng trạng thái, cần cấp trên hủy toàn bộ thao tác

Giả sử chức năng lưu đơn đã ghi một phần nhưng bước tiếp theo thất bại. Tầng thấp không được tự tuyên bố “đơn đã tạo thành công”. Có thể cần lan truyền lỗi để tầng quản lý giao dịch quyết định rollback hoặc đánh dấu không nhất quán, tùy cơ chế giao dịch thật sự được triển khai.

**Không suy luận rằng `throws` tự rollback**. Java exceptions không tự hoàn tác thay đổi tệp, dữ liệu hay gọi mạng. Rollback là cơ chế giao dịch hoặc mã bù trừ được hệ thống xây dựng thêm.

## 5. Sơ đồ quyết định nhanh

Với một exception cụ thể, trả lời theo thứ tự:

1. **Đây có phải nhánh nghiệp vụ dự kiến không?** Nếu là “hết hàng” hoặc “không tìm thấy” thường lệ, cân nhắc trả về kiểu kết quả rõ ràng; không nhất thiết dùng exception để điều khiển luồng.
2. **Tầng hiện tại có phương án khôi phục có thể kiểm chứng không?** Nếu có, `catch` đúng loại và thực hiện phương án.
3. **Tầng này có đủ trách nhiệm tạo phản hồi cho người dùng/hệ thống gọi không?** Nếu có, bắt và chuyển sang phản hồi hoặc trạng thái thất bại phù hợp.
4. **Tầng này chỉ biết bổ sung ngữ cảnh, không phục hồi được?** Có thể bắt để log/cleanup rồi `throw e`, hoặc wrap kèm cause nếu hợp đồng tầng mới yêu cầu.
5. **Không làm được gì có ích?** Không tạo `catch` dư thừa; cho exception lan lên caller và khai báo checked exception nếu cần.
6. **Exception thuộc nhánh `Error` nghiêm trọng?** Mã nghiệp vụ thông thường không nên nuốt lỗi để cố vận hành bình thường; ưu tiên điều tra nguyên nhân môi trường/JVM và chính sách vận hành.

Nói ngắn gọn theo ngữ cảnh thiết kế: **catch để quyết định một hành động hợp lệ; throws để giữ nguyên trách nhiệm xử lý cho bên gọi**. Chọn theo khả năng xử lý, không chọn chỉ để hết gạch đỏ trong IDE.

## 6. Chương trình chạy được: `catch` khi có fallback, `throws` khi chưa có

Để không phụ thuộc hệ thống tệp bên ngoài, ví dụ sau mô phỏng một thao tác I/O bằng `IOException`. Lưu toàn bộ thành **`CatchOrThrowsDemo.java`**:

```java
import java.io.IOException;

public class CatchOrThrowsDemo {
    static String loadPrimary(boolean available) throws IOException {
        if (!available) {
            throw new IOException("Nguồn chính không sẵn sàng");
        }
        return "primary-config";
    }

    // Không biết chính sách: giữ IOException cho caller.
    static String loadRequired(boolean available) throws IOException {
        return loadPrimary(available);
    }

    // Biết fallback đã được phép: bắt và dùng dữ liệu dự phòng.
    static String loadOptional(boolean available) {
        try {
            return loadPrimary(available);
        } catch (IOException e) {
            return "default-config";
        }
    }

    public static void main(String[] args) {
        System.out.println("Tùy chọn: " + loadOptional(false));
        System.out.println("Bắt buộc: " + loadOptional(true));

        try {
            System.out.println(loadRequired(false));
        } catch (IOException e) {
            System.out.println("Không thể khởi động: " + e.getMessage());
        }

        System.out.println("Main đã xử lý xong");
    }
}
```

Biên dịch/chạy trên Java 17+:

```bash
javac -encoding UTF-8 --release 17 CatchOrThrowsDemo.java
java CatchOrThrowsDemo
```

**Stdout chính xác** khi hiển thị UTF-8:

```text
Tùy chọn: default-config
Bắt buộc: primary-config
Không thể khởi động: Nguồn chính không sẵn sàng
Main đã xử lý xong
```

Ở `loadOptional(false)`, phương án dự phòng là một phần hợp đồng nên tầng hiện tại chủ động `catch` và trả `"default-config"`. Ở `loadRequired(false)`, tầng trung gian chỉ có nhiệm vụ nạp dữ liệu, nên `throws IOException` và caller tại `main` xử lý.

Tên hai dòng đầu chỉ nhằm minh họa kết quả in: dòng “Bắt buộc: primary-config” dùng lệnh `loadOptional(true)` để thể hiện đường đọc thành công có thể so sánh với fallback. Quyết định thực sự về cấu hình bắt buộc nằm ở lời gọi `loadRequired(false)` sau đó.

Việc mô phỏng bằng `IOException` phục vụ bài học về luồng điều khiển; chương trình không chạm vào tệp hay kết nối thật nên đầu ra có thể kiểm tra chính xác.

## 7. `catch` hẹp để không che các sự cố khác

Một API có thể ném nhiều ngoại lệ trong cùng họ. Ví dụ đọc tệp với `Files.readString` có thể ném `NoSuchFileException` khi tệp không tồn tại, hoặc những `IOException` khác khi thao tác thất bại vì quyền truy cập hay thiết bị.

Nếu chỉ **tệp không tồn tại** được phép chuyển sang fallback, cần xử lý hẹp:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

class OptionalFileConfig {
    static String read(Path path) throws IOException {
        try {
            return Files.readString(path);
        } catch (NoSuchFileException e) {
            return "default";
        }
    }
}
```

Đây là **lớp minh họa không có `main`**. Khi đúng một `NoSuchFileException` phát sinh, tầng này dùng fallback; những `IOException` khác tiếp tục được khai báo bằng `throws IOException`. Điều đó phân biệt nhánh dự kiến với những lỗi kỹ thuật mà mã không đủ thông tin khắc phục.

Lưu ý: một file đang tồn tại vẫn có thể mất giữa thời điểm kiểm tra và đọc; dùng `Files.exists(path)` trước rồi đọc không thay thế xử lý ngoại lệ tại thao tác thực tế. Tránh giả định rằng kiểm tra trước đã loại bỏ mọi lỗi I/O.

### Thứ tự các nhánh `catch`

Khi nhiều handler có quan hệ kế thừa, nhánh cụ thể phải đứng trước:

```java
try {
    readSomething();
} catch (NoSuchFileException e) {
    useFallback();
} catch (IOException e) {
    reportIoFailure(e);
}
```

Đây là khung minh họa. Nếu đảo `IOException` lên trước thì `NoSuchFileException` phía sau sẽ là handler không thể tới vì lớp cha đã bắt mọi instance của lớp con. Java phát hiện điều này tại biên dịch.

## 8. Bắt rồi ném lại: `catch` và `throws` có thể cùng xuất hiện

Một phương thức có thể cần log/ngữ cảnh tại tầng hiện tại nhưng vẫn phải lan truyền checked exception. Ví dụ độc lập, lưu thành **`RethrowDecisionDemo.java`**:

```java
import java.io.IOException;

public class RethrowDecisionDemo {
    static void read() throws IOException {
        throw new IOException("Không đọc được");
    }

    static void service() throws IOException {
        try {
            read();
        } catch (IOException e) {
            System.out.println("Service ghi nhận lỗi");
            throw e;
        }
    }

    public static void main(String[] args) {
        try {
            service();
        } catch (IOException e) {
            System.out.println("Main xử lý: " + e.getMessage());
        }
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 RethrowDecisionDemo.java
java RethrowDecisionDemo
```

**Stdout chính xác:**

```text
Service ghi nhận lỗi
Main xử lý: Không đọc được
```

`service()` có `catch` để ghi nhận lỗi và `throw e` để **ném lại chính đối tượng ngoại lệ**, vì tầng này không phục hồi. Nó phải khai báo `throws IOException` do lỗi checked tiếp tục thoát ra. `main()` là nơi handler cuối có thể quyết định phản hồi.

Nếu `service()` không cần ghi nhận gì, có thể viết ngắn hơn:

```java
static void service() throws IOException {
    read();
}
```

Đây là **phương thức thay thế minh họa**, thuộc cùng lớp với `read()`, không phải đoạn cần nối thêm vào chương trình có sẵn. Bỏ handler vô ích giúp mã rõ hơn.

## 9. Khi nào nên wrap thay vì `throw e`?

Đôi khi API ở một tầng khác không nên công bố các chi tiết của tầng thấp. Ví dụ tầng repository phát sinh `IOException` nhưng tầng dịch vụ muốn công bố “không thể nạp thông tin thanh toán” bằng một loại ngoại lệ ứng dụng rõ ràng. Khi đó có thể **bọc ngoại lệ gốc** trong một exception mới và giữ `cause`.

Ví dụ minh họa:

```java
import java.io.IOException;

class PaymentConfigurationException extends RuntimeException {
    PaymentConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}

class PaymentConfigurationService {
    static String load() {
        try {
            return readFromSource();
        } catch (IOException e) {
            throw new PaymentConfigurationException(
                    "Không thể nạp cấu hình thanh toán", e);
        }
    }

    static String readFromSource() throws IOException {
        throw new IOException("Nguồn cấu hình không truy cập được");
    }
}
```

Đây là lớp hợp lệ **không có `main`**. `load()` không khai báo `throws IOException` vì bắt và chuyển nó thành một unchecked exception mới. Caller nhận `PaymentConfigurationException`, còn `getCause()` là `IOException` ban đầu.

Việc wrapping là **thay đổi hợp đồng**, không phải chỉ là “tắt checked exception”. Chỉ dùng nếu tầng hiện tại có lý do giữ trừu tượng và bên gọi có chính sách xử lý phù hợp. Nếu vẫn cần caller xử lý riêng `IOException`, tiếp tục `throws IOException` hoặc một loại checked exception mới có thể thích hợp hơn.

Không viết `throw new RuntimeException(e.getMessage())` khi cần chẩn đoán nguyên nhân, vì chuỗi message **không chứa** đầy đủ stack trace và cause gốc. Constructor có `cause` như `new RuntimeException("...", e)` giúp giữ dấu vết.

## 10. `throws` không thay thế cleanup

Một phương thức có `throws IOException` vẫn phải bảo vệ tài nguyên đang nắm giữ. Nếu mở file, socket hoặc đối tượng `AutoCloseable` rồi để exception đi lên, tài nguyên không được tự giải phóng chỉ vì có `throws`.

Dùng `try-with-resources` để đóng tài nguyên khi rời phạm vi, cả trên đường hoàn thành bình thường và nhiều đường ném ngoại lệ. Đặc biệt, tài nguyên được đóng theo **thứ tự ngược khai báo**.

Lưu ví dụ độc lập **`ThrowsResourceDemo.java`**:

```java
import java.io.IOException;

public class ThrowsResourceDemo {
    static class Session implements AutoCloseable {
        @Override
        public void close() {
            System.out.println("Đã đóng session");
        }
    }

    static void work() throws IOException {
        try (Session session = new Session()) {
            System.out.println("Đang làm việc");
            throw new IOException("Không hoàn tất");
        }
    }

    public static void main(String[] args) {
        try {
            work();
        } catch (IOException e) {
            System.out.println("Caller: " + e.getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 ThrowsResourceDemo.java
java ThrowsResourceDemo
```

**Stdout chính xác:**

```text
Đang làm việc
Đã đóng session
Caller: Không hoàn tất
```

`work()` không `catch`, nhưng vẫn dọn dẹp bằng `try-with-resources`. Sau khi đóng `session`, exception đi lên caller. Tương tự, nếu `close()` cũng ném exception trong lúc thân `try` đã ném, lỗi khi đóng có thể được đính kèm dạng **suppressed exception** trong exception chính; caller có thể kiểm tra bằng `getSuppressed()`.

`finally` cũng có thể dùng cho việc dọn dẹp, nhưng đừng đặt `return` hoặc ném lỗi mới một cách vô ý bên trong `finally` vì chúng có thể che lỗi chính đang lan truyền. Và không có cleanup nào bảo đảm chạy khi JVM bị chấm dứt cưỡng bức hoặc gặp sự cố nghiêm trọng.

## 11. Checked và unchecked quyết định nghĩa vụ biên dịch, không quyết định khả năng phục hồi

### Checked exception

Ví dụ `IOException` hoặc `SQLException` (khi dùng API có thể ném chúng) là checked exception. Nếu phương thức không xử lý, nó phải khai báo `throws` tương ứng hoặc kiểu cha theo luật Java. Đây là **quy tắc ngôn ngữ**, không phải tuyên bố rằng checked exception luôn có thể phục hồi.

Một `IOException` có thể phát sinh khi tệp cấu hình cốt lõi bị lỗi. Dù đã khai báo `throws IOException`, một ứng dụng không có fallback hợp lệ vẫn phải xem đó là lỗi cần chấm dứt thao tác.

### Unchecked exception

`IllegalArgumentException`, `NullPointerException` và `IllegalStateException` là lớp con của `RuntimeException`, nên caller **không bắt buộc** dùng `catch` hoặc `throws`. Nếu biết cách chuyển một lỗi parse thành thông báo nhập liệu, vẫn có thể `catch (NumberFormatException e)` chủ động. Nếu đó là bug sử dụng API sai, nên sửa nơi gọi hơn là nuốt ngoại lệ.

`Error` và lớp con cũng unchecked, nhưng thường thể hiện sự cố môi trường/JVM hoặc bất biến nghiêm trọng; mã nghiệp vụ thông thường không nên `catch (Throwable e)` chỉ để chặn mọi lỗi rồi tiếp tục.

### Vì sao `throws RuntimeException` hiếm khi cần

Một chữ ký:

```java
static int parse(String text) throws NumberFormatException {
    return Integer.parseInt(text);
}
```

là hợp lệ, nhưng `throws NumberFormatException` **không làm bên gọi phải bắt** loại unchecked này. Nếu muốn tài liệu hóa rõ các tiền điều kiện, có thể dùng JavaDoc `@throws` hoặc mô tả hợp đồng; thêm `throws` vào chữ ký có thể hữu ích để người đọc nhận biết, nhưng không thay quy tắc biên dịch.

Không nên nhầm “không có `throws`” với “không thể có exception”: unchecked exception vẫn có thể xảy ra lúc chạy.

## 12. `throws Exception` và `catch (Exception e)`: vì sao nên cẩn trọng?

Hai cách viết rộng này đều hợp lệ trong một số tình huống, nhưng có thể làm mất ý nghĩa loại lỗi và khiến hệ thống khó xử lý chính xác.

### Khai báo quá rộng

```java
static void loadEverything() throws Exception {
    readConfig();
}
```

Đây là mã minh họa, `readConfig()` cần được định nghĩa. Nếu thực tế chỉ có `IOException` là checked exception thì chữ ký `throws IOException` cho caller thông tin hữu ích hơn. Khi khai báo `throws Exception`, caller phải bắt/khai báo kiểu checked rộng hơn, ngay cả khi thao tác hiện chỉ dùng lỗi I/O.

Trong ứng dụng demo hoặc tại ranh giới `main`, chữ ký rộng đôi khi giúp thử nghiệm nhanh, nhưng API công khai của thư viện nên bộc lộ hợp đồng cụ thể và ổn định.

### Bắt quá rộng

```java
try {
    execute();
} catch (Exception e) {
    return fallback();
}
```

Đây là mã minh họa, chỉ đúng khi API thật sự cho phép fallback với **mọi** lớp con của `Exception` có thể phát sinh. `catch (Exception e)` gồm checked exception và `RuntimeException`, có thể che bug mà tầng hiện tại không biết khắc phục. Nó cũng **không** bắt `Error`.

Mức bắt rộng có thể hợp lý tại một ranh giới cuối, ví dụ framework quản lý request/job và phải ghi nhận mọi thất bại để giữ vòng phục vụ hoạt động. Khi đó việc chọn phản hồi, báo lỗi và trạng thái tiếp theo vẫn phải được thiết kế cụ thể, thay vì âm thầm coi mọi ngoại lệ là thành công.

## 13. Precise rethrow: bắt kiểu rộng nhưng vẫn `throws` hẹp

Từ Java 7, khi tham số `catch` là `final` hoặc **effectively final** (không bị gán lại), Java có thể phân tích chính xác hơn các checked exception mà lệnh `throw e` có thể truyền ra. Do đó, không phải cứ `catch (Exception e)` rồi `throw e` là bắt buộc khai báo `throws Exception`.

Ví dụ chạy được, lưu **`PreciseRethrowChoiceDemo.java`**:

```java
import java.io.IOException;

public class PreciseRethrowChoiceDemo {
    static void read() throws IOException {
        throw new IOException("Lỗi khi đọc");
    }

    static void relay() throws IOException {
        try {
            read();
        } catch (Exception e) {
            System.out.println("Relay ghi nhận lỗi");
            throw e;
        }
    }

    public static void main(String[] args) {
        try {
            relay();
        } catch (IOException e) {
            System.out.println("Caller: " + e.getMessage());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 PreciseRethrowChoiceDemo.java
java PreciseRethrowChoiceDemo
```

**Stdout chính xác:**

```text
Relay ghi nhận lỗi
Caller: Lỗi khi đọc
```

Mặc dù handler khai báo `catch (Exception e)`, phương thức `relay` chỉ cần `throws IOException` vì compiler xác định tập checked exception có thể thoát ra từ `try` trong ví dụ và biến `e` không bị gán lại. Nếu gán lại `e` trong handler, việc thu hẹp kiểu theo quy tắc precise rethrow có thể không còn áp dụng, và `throw e` có thể bị kiểm tra theo kiểu khai báo rộng hơn.

Đây là một tính năng phân tích tại **compile time**; đối tượng exception được ném ở runtime vẫn là cùng đối tượng được `catch` nhận. Không nên dùng `catch (Exception e)` ở khắp nơi chỉ vì precise rethrow tồn tại: bắt loại cụ thể thường vẫn dễ hiểu và tránh xử lý sai phạm vi.

## 14. Cẩn trọng với `InterruptedException`

`InterruptedException` là checked exception xuất hiện trong những thao tác có thể chờ hoặc bị ngắt, ví dụ `Thread.sleep`. Nó còn mang ý nghĩa **tín hiệu hợp tác hủy/chấm dứt công việc**; cách xử lý thường khác với lỗi I/O.

Nếu tầng hiện tại có thể khai báo `throws InterruptedException` và hợp đồng cho phép, **truyền tiếp** thường là cách rõ ràng để caller quyết định việc dừng thao tác.

Nếu một ranh giới không thể khai báo checked exception này và phải bắt, thông thường cần **khôi phục trạng thái interrupt** bằng `Thread.currentThread().interrupt()` trước khi kết thúc nhánh xử lý, để tầng cao hơn còn nhận biết tín hiệu. Trong Java, nhiều phương thức chặn khi ném `InterruptedException` sẽ xóa trạng thái interrupt của thread.

Ví dụ phương thức minh họa:

```java
static boolean pause() {
    try {
        Thread.sleep(1000);
        return true;
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
    }
}
```

Đây là **phương thức minh họa không có `main`**. `false` ở đây là một kết quả đã được hợp đồng giả định để biểu diễn thao tác bị hủy; caller phải hiểu và xử lý. Không sử dụng mẫu này ở mọi API một cách máy móc: có trường hợp nên `throws InterruptedException` thay vì chuyển thành boolean, và có ranh giới cần thực hiện thêm cleanup. Không tự động dùng `catch (Exception e)` để vô tình nuốt tín hiệu interrupt.

## 15. Chuyển lỗi tại ranh giới nhưng giữ nguyên nhân

Mã Java thuần không quy định một exception phải trở thành status HTTP cụ thể. Ở ranh giới giao tiếp hoặc module nghiệp vụ, bạn có thể chuyển một lỗi tầng dưới thành loại lỗi có ý nghĩa hơn mà vẫn giữ `cause`, hoặc trả một đối tượng kết quả khi hợp đồng quy định đây là nhánh dự kiến.

Lưu ý khi chuyển lỗi:

- **Giữ ý nghĩa:** sự cố database là lỗi kỹ thuật; không giả vờ là “không tìm thấy” chỉ vì không đọc được dữ liệu.
- **Giữ nguyên nhân:** constructor `new SomeException("...", cause)` giúp bảo toàn dấu vết và stack trace của lỗi gốc trong chuỗi `getCause()`.
- **Không làm lộ chi tiết nội bộ:** stack trace và bí mật cấu hình không nên đưa thẳng vào thông báo người dùng.
- **Tránh log trùng lặp ở mọi tầng:** chọn nơi có đủ ngữ cảnh; mỗi tầng không bắt buộc in cả stack trace trước khi ném tiếp.
- **Chọn chính sách cuối cùng:** hủy request, ghi job thất bại, thử lại có giới hạn hoặc thông báo thiếu dữ liệu là việc của ranh giới biết trạng thái tổng thể.

Khi đổi checked exception sang unchecked wrapper, bên gọi không còn nghĩa vụ `catch-or-declare` do compiler, nhưng **sự cố vẫn tồn tại** và thiết kế vẫn cần điểm xử lý cuối. Nếu thư viện công bố checked exception như một phần quan trọng của API, thay đổi kiểu ngoại lệ có thể ảnh hưởng caller và kiểm thử; hãy cân nhắc khả năng tương thích.

## 16. Ngoại lệ trong `finally` và lỗi bị che khuất

Một quyết định `catch` hay `throws` tốt vẫn có thể thất bại nếu cleanup tạo lỗi mới và che đi lỗi quan trọng.

Ví dụ khung:

```java
try {
    throw new IllegalArgumentException("Lỗi chính");
} finally {
    throw new IllegalStateException("Lỗi cleanup");
}
```

Nếu đoạn này được đặt trong một phương thức hợp lệ, exception cuối được truyền ra là `IllegalStateException` từ `finally`. Exception chính có thể bị mất khỏi đường chẩn đoán nếu không được lưu riêng. Do đó:

1. Ưu tiên `try-with-resources` với `AutoCloseable`.
2. Đừng viết `return` trong `finally`; nó có thể ghi đè exception hoặc giá trị return đang chờ.
3. Nếu cleanup có thể thất bại, xác định cách giữ nguyên nhân và suppressed exception.
4. Không để lỗi cleanup khiến caller hiểu rằng công việc chính thành công.

Một lựa chọn `throws` chính xác phải đi kèm thực hành quản lý tài nguyên đúng; không thể dùng mệnh đề `throws` để thay cho việc đóng tệp, socket hay trả tài nguyên.

## 17. Kiểm thử cả hai nhánh quyết định

Kiểm thử ngoại lệ không chỉ kiểm tra chương trình “không crash”. Cần xác nhận **hợp đồng thực tế** ở từng tầng.

| Trường hợp cần kiểm tra | Điều mong đợi |
| --- | --- |
| Đường thành công | Phương thức trả dữ liệu đúng, không chạy `catch` |
| Lỗi có fallback được phép | `catch` chọn đúng fallback, dữ liệu/side effect nhất quán |
| Lỗi không có fallback | Checked exception được khai báo và lan tới caller; không có thành công giả |
| Lỗi thuộc loại khác | Handler hẹp không nuốt ngoại lệ ngoài phạm vi |
| Handler chỉ log rồi rethrow | Caller vẫn nhận đúng exception hoặc hợp đồng wrapper có cause |
| Tài nguyên bị lỗi khi đang sử dụng | Tài nguyên được cố đóng; lỗi chính/các suppressed được bảo toàn phù hợp |
| Interrupted operation | Trạng thái interrupt được xử lý đúng hợp đồng |
| Lỗi nghiệp vụ dự kiến | Trả kết quả domain hoặc mã lỗi tương ứng; không nhất thiết ném exception |

Một bài test tốt còn kiểm tra rằng một handler rộng không biến các bug như `NullPointerException` thành giá trị fallback có vẻ hợp lệ. Nếu fallback có thể mất dữ liệu, thay đổi giao dịch hoặc ảnh hưởng bảo mật, cần test các bất biến sau lỗi chứ không chỉ kiểm tra giá trị trả về.

## 18. Các sai lầm thường gặp

**Sai lầm 1: Bắt tất cả rồi bỏ qua.**

```java
try {
    save();
} catch (Exception ignored) {
    // Không làm gì.
}
```

Đây là khung minh họa, `save()` chưa được định nghĩa. Nếu `save()` thất bại, caller có thể tiếp tục như đã lưu thành công. Chỉ bỏ qua exception cụ thể khi hợp đồng bảo đảm việc bỏ qua thật sự an toàn và có lý do rõ ràng.

**Sai lầm 2: Dùng `throws Exception` để khỏi hiểu lỗi.** Chữ ký quá rộng làm caller khó phân biệt I/O, lỗi dữ liệu và các điều kiện khác. Khai báo loại checked phù hợp với hợp đồng khi khả thi.

**Sai lầm 3: Dùng `catch` chỉ để ghi log và ném lại ở mọi tầng.** Các bản ghi trùng nhau khiến hệ thống khó quan sát. Hãy log tại nơi có giá trị chẩn đoán hoặc thêm ngữ cảnh, rồi giữ exception gốc hay wrapper có cause.

**Sai lầm 4: Dùng `catch (Exception e)` làm fallback cho mọi tình huống.** Có thể che những `RuntimeException` do bug; không phải exception nào cũng có thể khôi phục.

**Sai lầm 5: Nghĩ `throws` sẽ tự hiển thị lỗi, retry hay rollback.** `throws` chỉ là khai báo; giao diện, retry và giao dịch cần code/cơ chế riêng.

**Sai lầm 6: Chuyển checked sang unchecked chỉ để compiler không báo lỗi.** Lỗi không biến mất; chỉ nghĩa vụ biên dịch thay đổi. Cần một hợp đồng API thích hợp và giữ `cause`.

**Sai lầm 7: Quên đóng tài nguyên khi để exception lan truyền.** Dùng `try-with-resources` hoặc cleanup phù hợp để tránh rò rỉ tài nguyên.

**Sai lầm 8: Nuốt `InterruptedException`.** Việc chặn tín hiệu ngắt có thể cản khả năng hủy công việc; truyền tiếp hoặc khôi phục interrupt khi phù hợp.

**Sai lầm 9: Coi “không tìm thấy dữ liệu” luôn là exception.** Nếu đây là kết quả dự kiến, `Optional` hoặc kiểu kết quả domain có thể biểu diễn rõ hơn.

**Sai lầm 10: Cố bắt `Error` nghiêm trọng như một lỗi nghiệp vụ.** Các lỗi ở mức JVM/hệ thống như `OutOfMemoryError` thường không cho phép tầng nghiệp vụ tiếp tục an toàn.

## 19. Câu hỏi tự kiểm tra

**1. Hàm đọc tệp ném `IOException`, tầng hiện tại không biết nên dùng cấu hình nào. Nên `catch` hay `throws`?**  
Nên cho exception lan truyền bằng `throws IOException`, để tầng biết chính sách quyết định.

**2. Tầng giao diện bắt được `NumberFormatException` do người dùng nhập chữ vào ô số. Có cần tiếp tục `throws` không?**  
Không nhất thiết. Nếu tầng giao diện có thể trả lỗi xác thực phù hợp, đó là nơi `catch` có giá trị.

**3. Một phương thức có thể ném `IllegalArgumentException` nhưng không có `throws`. Có sai không?**  
Không. `IllegalArgumentException` là unchecked exception nên không bắt buộc khai báo.

**4. `catch (IOException e) { throw e; }` có nghĩa lỗi đã xử lý xong không?**  
Không. Handler chỉ ném lại lỗi; phương thức chứa handler vẫn có thể thoát bất thường và cần khai báo checked exception.

**5. Khi nào fallback là nguy hiểm?**  
Khi nó che tình huống cần cảnh báo, làm mất dữ liệu hoặc khiến bên gọi tưởng thao tác thành công dù trạng thái không hợp lệ.

**6. Có thể vừa `catch` loại A vừa `throws` loại B trong cùng phương thức không?**  
Có. Bạn có thể xử lý một số lỗi và để những lỗi khác tiếp tục lan theo hợp đồng.

**7. Nếu đang dùng `try-with-resources` mà thân `try` ném ngoại lệ, `throws` có ngăn việc gọi `close()` không?**  
Không. Java cố đóng tài nguyên trước khi exception tiếp tục thoát khỏi cấu trúc.

**8. `throws IOException` có tự retry thao tác I/O không?**  
Không. Retry cần chính sách và code riêng, đặc biệt khi thao tác có side effect.

**9. Nếu muốn chuyển `IOException` thành `PaymentConfigurationException`, làm sao giữ dấu vết gốc?**  
Tạo exception mới với nguyên nhân: `new PaymentConfigurationException("...", e)` và để caller truy `getCause()`.

**10. Catch rộng ở ranh giới cuối có luôn sai không?**  
Không. Một ranh giới điều phối có thể cần bắt rộng để ghi nhận thất bại và giữ vòng phục vụ hoạt động; phải có chính sách an toàn và tránh biến lỗi nội bộ thành thành công giả.

## 20. Checklist ra quyết định khi viết API Java

Trước khi viết `catch` hay `throws`, hãy rà soát:

1. **Ý nghĩa lỗi:** exception này biểu thị tình huống kỹ thuật bất thường, vi phạm hợp đồng hay kết quả nghiệp vụ dự kiến?
2. **Nơi có thông tin:** tầng hiện tại có đủ hiểu biết để khắc phục mà vẫn giữ đúng dữ liệu, bất biến và side effect?
3. **Hợp đồng trả về:** caller cần biết kết quả gì, có loại fallback hợp lệ không, có cần kết quả dạng `Optional` hoặc `Result`?
4. **Kiểu lỗi:** checked exception nào cần khai báo; unchecked exception nào nên để lan truyền hoặc xử lý chủ động?
5. **Phạm vi `catch`:** có thể bắt loại cụ thể hơn để không nuốt các sự cố khác?
6. **Nếu ném lại:** có giữ cùng đối tượng bằng `throw e` hay có lý do wrap với `cause`? Có thêm giá trị thực sự trước khi ném?
7. **Tài nguyên:** file/socket/session có được đóng đúng không, exception cleanup có thể che lỗi gốc không?
8. **Tín hiệu hủy:** có `InterruptedException` cần truyền tiếp hoặc restore interrupt không?
9. **Ranh giới cuối:** ai hiển thị thông báo, đánh dấu job thất bại, quyết định retry hoặc rollback?
10. **Kiểm thử:** cả đường thành công, fallback hợp lệ, lan truyền lỗi, cleanup và lỗi ngoài phạm vi có được kiểm chứng?

## 21. Ghi nhớ

**Nên `catch` khi tầng hiện tại có hành động xử lý cụ thể, hợp lệ và giữ được trạng thái hệ thống; nên tiếp tục `throws` khi bên gọi mới có đủ thông tin hoặc chính sách để xử lý.** Bắt để ghi nhận rồi `throw e` vẫn là **lan truyền lỗi**, còn wrap phải giữ `cause` nếu muốn bảo toàn nguyên nhân.

Checked exception buộc mã tuân thủ `catch-or-declare`; unchecked exception không có nghĩa vụ đó nhưng vẫn cần hợp đồng và trách nhiệm xử lý rõ ràng. `throws` không tự sửa lỗi, retry, rollback hay cleanup. Chính việc hiểu nơi phát hiện, nơi phục hồi và nơi quyết định phản hồi giúp luồng exception trở nên chính xác, dễ kiểm thử và dễ bảo trì.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="throws-clause-007">Khai báo unchecked exception và vai trò tài liệu hóa</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo unchecked exception và vai trò tài liệu hóa trong Java

**Unchecked exception** là ngoại lệ thuộc nhánh `RuntimeException` hoặc `Error`, bao gồm cả các lớp con. Java **không bắt buộc** phương thức hoặc constructor phải liệt kê những ngoại lệ này trong mệnh đề `throws`, và cũng **không bắt buộc** mã gọi bắt hoặc khai báo chúng. Tuy vậy, một API vẫn có thể viết `throws IllegalArgumentException`, `throws IllegalStateException` hay tài liệu Javadoc `@throws` để diễn đạt những trường hợp thất bại mà người dùng API cần biết.

Bài học tập trung vào câu hỏi: **khi nào nên khai báo unchecked exception trong `throws`, khi nào nên mô tả bằng Javadoc và hợp đồng API, và những điều compiler thực sự kiểm tra là gì?** Những nội dung quan trọng gồm cách viết chữ ký phương thức/constructor, phân biệt quy tắc ngôn ngữ với khuyến nghị tài liệu hóa, công bố điều kiện gây lỗi, xử lý khi override và những rủi ro về thiết kế API.

## 1. Nhận diện unchecked exception chính xác

Trong Java, `Throwable` có hai nhánh lớn: `Exception` và `Error`. `RuntimeException` là lớp con của `Exception` nhưng được xếp vào nhóm **unchecked**; `Error` cùng các lớp con cũng là unchecked.

~~~text
Throwable
├── Error                                   unchecked
│   ├── AssertionError                      unchecked
│   ├── LinkageError                        unchecked
│   └── ...
└── Exception
    ├── IOException                         checked
    ├── SQLException                        checked
    ├── InterruptedException                checked
    └── RuntimeException                    unchecked
        ├── IllegalArgumentException        unchecked
        │   └── NumberFormatException       unchecked
        ├── IllegalStateException           unchecked
        ├── NullPointerException            unchecked
        ├── IndexOutOfBoundsException       unchecked
        └── ...
~~~

**Điều kiện phân loại:** mọi lớp con của `RuntimeException` và `Error` là unchecked. Các kiểu `Throwable` còn lại là checked. Không phải mọi lớp con của `Exception` đều là checked; `RuntimeException` là ngoại lệ cần nhớ.

Ví dụ về ý nghĩa của các unchecked exception thường thấy:

| Loại | Tình huống thường gặp | Người dùng API nên hiểu |
| --- | --- | --- |
| `IllegalArgumentException` | Đối số vi phạm quy tắc hợp đồng như giá trị âm không hợp lệ | Cần sửa đầu vào trước khi gọi |
| `IllegalStateException` | Đối tượng chưa khởi tạo xong, đã đóng hoặc đang ở trạng thái không cho phép thao tác | Cần kiểm tra vòng đời/trạng thái sử dụng |
| `NullPointerException` | Tham chiếu `null` bị dùng ở nơi cần đối tượng khác `null` | Cần tuân thủ quy ước nullability của API |
| `IndexOutOfBoundsException` | Chỉ số ngoài phạm vi dữ liệu | Cần giới hạn chỉ số hợp lệ |
| `NumberFormatException` | Chuỗi không chuyển được thành số theo cú pháp được yêu cầu | Cần kiểm tra hoặc xử lý dữ liệu đầu vào |
| `UnsupportedOperationException` | Một thao tác được biểu diễn trong API nhưng implementation không hỗ trợ | Cần chọn cách gọi hoặc implementation phù hợp |
| `Error` và các lớp con | Ví dụ một số lỗi nghiêm trọng của môi trường chạy hoặc liên kết lớp | Thông thường không được dùng làm cơ chế báo lỗi nghiệp vụ có thể phục hồi |

Việc phân loại checked/unchecked là thuộc tính **của kiểu ngoại lệ**, không phụ thuộc vào tên ngoại lệ, mức độ nghiêm trọng cụ thể, có `throws` trong chữ ký hay có thực sự phát sinh ở một lần chạy.

## 2. `throws` với unchecked exception hoàn toàn hợp lệ

Cú pháp của mệnh đề `throws` giống nhau với checked và unchecked exception:

~~~java
// Đoạn minh họa là các khai báo đặt trong một lớp.
public static int parseAge(String raw)
        throws NumberFormatException {
    return Integer.parseInt(raw);
}

public static int parseAgeWithoutDeclaration(String raw) {
    return Integer.parseInt(raw);
}
~~~

Hai phương thức đều có thể ném `NumberFormatException`. Chúng chỉ khác ở chỗ phương thức thứ nhất **nêu rõ kiểu ngoại lệ trong chữ ký**. Vì `NumberFormatException` là unchecked, cả hai lời gọi đều có thể được viết **mà không có `try/catch` hoặc `throws` ở phía người gọi**.

~~~java
// Đoạn minh họa bên trong một phương thức.
int a = parseAge("17");
int b = parseAgeWithoutDeclaration("25");
System.out.println(a + b); // 42
~~~

Nếu truyền `"mười bảy"` vào một trong hai lời gọi, `Integer.parseInt` có thể ném `NumberFormatException`. Java vẫn thực hiện cơ chế runtime tìm `catch` gần nhất và lan truyền ngoại lệ như những kiểu `Throwable` khác.

Constructor cũng được phép khai báo unchecked exception:

~~~java
class UserAge {
    private final int value;

    UserAge(int value) throws IllegalArgumentException {
        if (value < 0) {
            throw new IllegalArgumentException("tuổi âm");
        }
        this.value = value;
    }
}
~~~

Lời gọi `new UserAge(-1)` có thể ném `IllegalArgumentException`; tuy nhiên đoạn mã gọi `new UserAge(20)` **không bị compiler bắt buộc bắt** kiểu ngoại lệ này. Khai báo `throws IllegalArgumentException` ở constructor chủ yếu là một tín hiệu cho người đọc và công cụ xem chữ ký API.

Không có yêu cầu phải “đăng ký” mọi unchecked exception có thể phát sinh; Java cũng không tự chèn chúng vào mệnh đề `throws`.

## 3. Khác biệt cốt lõi với checked exception

Xét hai trường hợp:

~~~java
import java.io.IOException;

// Hai phương thức minh họa đặt trong một lớp.
static void readFile() throws IOException {
    throw new IOException("không đọc được");
}

static void validate() throws IllegalArgumentException {
    throw new IllegalArgumentException("dữ liệu sai");
}
~~~

Quy tắc ở nơi gọi:

- **Gọi `readFile()`:** vì `IOException` là checked, lời gọi phải nằm trong đường xử lý `catch` phù hợp hoặc phương thức chứa lời gọi phải khai báo `throws IOException` (hay kiểu cha hợp lệ), trừ khi ngữ cảnh đã xử lý khác theo luật Java.
- **Gọi `validate()`:** vì `IllegalArgumentException` là unchecked, mã gọi không cần `catch` và không cần `throws IllegalArgumentException`, dù phương thức `validate` đã khai báo nó.
- **Lúc runtime:** cả hai loại đều đi qua cùng cơ chế `throw`, tìm `catch`, chạy `finally` và lan truyền nếu chưa được xử lý.

Ví dụ này không tạo ra các nhóm “unchecked nhưng có khai báo sẽ thành checked”. Thêm `throws RuntimeException` không thay đổi cơ chế kiểm tra của compiler.

| Khía cạnh | Checked exception | Unchecked exception |
| --- | --- | --- |
| Ví dụ | `IOException`, `SQLException` | `IllegalArgumentException`, `IllegalStateException` |
| Compiler yêu cầu `catch` hoặc `throws` khi có thể thoát? | Có, theo quy tắc kiểm tra ngoại lệ | Không |
| Có được ghi trong `throws`? | Có | Có |
| Có được mô tả bằng Javadoc `@throws`? | Có | Có |
| Gọi phương thức có `throws` có tự ném lỗi? | Không | Không |
| Có truyền qua ngăn xếp lời gọi nếu thực sự bị ném? | Có | Có |

## 4. Vì sao vẫn cần tài liệu hóa unchecked exception?

Khi một API công bố điều kiện gây ra `IllegalArgumentException` hay `IllegalStateException`, người gọi biết cách **sử dụng API đúng**, phòng tránh lỗi, xử lý đầu vào và định vị lỗi khi chạy.

Ví dụ lời khai báo `void transfer(int amount)` chưa nói rõ giá trị nào được phép. Tài liệu tốt nên làm rõ:

- **Điều kiện kích hoạt:** `amount <= 0` có hợp lệ không? Chuyển tiền khi tài khoản đã đóng thì sao?
- **Loại ngoại lệ:** lỗi đối số trả về `IllegalArgumentException` hay lỗi trạng thái trả về `IllegalStateException`?
- **Trạng thái khi thất bại:** dữ liệu có thay đổi một phần hay thao tác thất bại trước khi thay đổi?
- **Cách sử dụng đúng:** những giá trị, trạng thái hoặc bước chuẩn bị mà người gọi cần bảo đảm.

Mục tiêu của tài liệu hóa là biến những kỳ vọng quan trọng thành **hợp đồng đọc hiểu và kiểm thử được**. Nó không thay thế kiểm tra đầu vào trong code và cũng không khiến compiler xác minh rằng thông điệp Javadoc luôn đúng.

Đối với thư viện dùng bởi bên ngoài, Javadoc mô tả các tình huống lỗi có chủ đích thường quan trọng hơn việc liệt kê một cách máy móc mọi `RuntimeException` từ thư viện phụ thuộc hoặc lỗi bất ngờ do bug. Một API không thể cam kết rằng sẽ không bao giờ có bất kỳ unchecked exception nào khác; không cần tạo một bảng liệt kê vô hạn.

## 5. Hai cách công bố: mệnh đề `throws` và Javadoc `@throws`

Có thể viết **cả hai**, hoặc chỉ viết Javadoc.

### 5.1. Khai báo trong chữ ký

~~~java
public void deposit(int amount)
        throws IllegalArgumentException, IllegalStateException {
    // Triển khai của một API.
}
~~~

Phần `throws` này có thể nhìn thấy qua chữ ký nguồn và metadata phản chiếu (reflection) của phương thức. Nhưng nó **chỉ liệt kê kiểu**, không giải thích tình huống nào gây ra lỗi. Vì đó là unchecked exception, nó không buộc người gọi bắt hoặc khai báo.

### 5.2. Mô tả điều kiện bằng Javadoc

~~~java
/**
 * Nạp số tiền dương vào tài khoản đang hoạt động.
 *
 * @param amount số tiền cần nạp; phải lớn hơn 0
 * @throws IllegalArgumentException nếu amount nhỏ hơn hoặc bằng 0
 * @throws IllegalStateException nếu tài khoản đã bị đóng
 */
public void deposit(int amount) {
    // Triển khai của một API.
}
~~~

**`@throws` là thẻ tài liệu Javadoc**, không phải từ khóa Java trong chữ ký. Nó có thể mô tả unchecked exception **ngay cả khi không xuất hiện mệnh đề `throws`**. Thẻ `@exception` là bí danh lịch sử của `@throws`, nhưng nên dùng `@throws` nhất quán.

Thông thường, khi tài liệu hóa một ngoại lệ unchecked, thông tin có giá trị nhất là **điều kiện cụ thể** khiến ngoại lệ xảy ra. Chỉ viết `@throws IllegalArgumentException có thể ném IllegalArgumentException` gần như không giúp người dùng.

### 5.3. Kết hợp cả hai

~~~java
/**
 * Cập nhật số tiền theo quy tắc của API.
 *
 * @param amount số tiền dương
 * @throws IllegalArgumentException nếu amount không dương
 * @throws IllegalStateException nếu tài khoản đã bị khóa
 */
public void deposit(int amount)
        throws IllegalArgumentException, IllegalStateException {
    // Triển khai của một API.
}
~~~

Sử dụng `throws` trong chữ ký là tùy chọn đối với unchecked exception; chọn có hoặc không dựa trên phong cách nhất quán của API. **Thẻ `@throws` vẫn nên ghi rõ điều kiện lỗi quan trọng**, dù có liệt kê kiểu trong chữ ký hay không.

Chú ý quy tắc ngôn ngữ khác nhau: với checked exception, tài liệu Javadoc `@throws IOException` **không thay cho** khai báo `throws IOException` cần thiết để mã nguồn biên dịch. Javadoc không biến kiểu checked thành unchecked, và mệnh đề `throws` không thay thế phần diễn giải điều kiện trong tài liệu.

## 6. Ví dụ Java 17+ hoàn chỉnh: API có hợp đồng lỗi rõ ràng

Lưu đoạn mã sau thành `UncheckedDocumentationDemo.java`:

~~~java
public class UncheckedDocumentationDemo {
    static final class Counter {
        private int value;
        private boolean closed;

        Counter(int initialValue) {
            if (initialValue < 0) {
                throw new IllegalArgumentException("giá trị đầu âm");
            }
            value = initialValue;
        }

        /**
         * Tăng giá trị khi bộ đếm đang mở.
         *
         * @param delta số nguyên dương cần cộng
         * @throws IllegalArgumentException nếu delta không dương hoặc gây tràn số
         * @throws IllegalStateException nếu bộ đếm đã đóng
         */
        void add(int delta) throws IllegalArgumentException, IllegalStateException {
            if (delta <= 0) {
                throw new IllegalArgumentException("delta phải dương");
            }
            if (closed) {
                throw new IllegalStateException("bộ đếm đã đóng");
            }
            if (value > Integer.MAX_VALUE - delta) {
                throw new IllegalArgumentException("tăng gây tràn số");
            }
            value += delta;
        }

        int value() {
            return value;
        }

        void close() {
            closed = true;
        }
    }

    public static void main(String[] args) {
        Counter counter = new Counter(2);
        counter.add(3);
        System.out.println("Giá trị: " + counter.value());

        try {
            counter.add(0);
        } catch (IllegalArgumentException ex) {
            System.out.println("Đối số: " + ex.getMessage());
        }

        counter.close();
        try {
            counter.add(1);
        } catch (IllegalStateException ex) {
            System.out.println("Trạng thái: " + ex.getMessage());
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 UncheckedDocumentationDemo.java
java UncheckedDocumentationDemo
~~~

Stdout chính xác:

~~~text
Giá trị: 5
Đối số: delta phải dương
Trạng thái: bộ đếm đã đóng
~~~

**Điểm cần hiểu từ chương trình:**

1. Constructor `Counter(int initialValue)` kiểm tra tiền điều kiện về giá trị ban đầu. Nó không viết `throws IllegalArgumentException`, nhưng vẫn được phép ném ngoại lệ unchecked khi đối số sai.
2. `add(int delta)` khai báo hai unchecked exception trong mệnh đề `throws`, đồng thời giải thích từng điều kiện bằng Javadoc `@throws`.
3. `main` gọi `counter.add(3)` **mà không có `try/catch`**; compiler chấp nhận vì các kiểu đã khai báo đều là unchecked.
4. Hai khối `try/catch` tiếp theo là lựa chọn xử lý có chủ đích để trình diễn nhánh thất bại, không phải yêu cầu bắt buộc từ compiler.
5. Kiểm tra đầu vào và trạng thái diễn ra trước khi cập nhật `value`, nên các lỗi được kiểm tra ở đây không làm tăng dở dang giá trị. Đây là tính chất thiết kế hữu ích có thể nêu trong hợp đồng API.
6. `value > Integer.MAX_VALUE - delta` tránh tràn số nguyên trên đường cập nhật sau khi đã xác nhận `delta > 0`; điều kiện tràn được công bố cùng `IllegalArgumentException`.

Để thử riêng trường hợp constructor, có thể viết trong một phương thức:

~~~java
// Minh họa trong mã gọi có quyền truy cập lớp Counter.
try {
    new UncheckedDocumentationDemo.Counter(-1);
} catch (IllegalArgumentException ex) {
    System.out.println(ex.getMessage()); // giá trị đầu âm
}
~~~

Đoạn này cho thấy constructor không cần khai báo `throws IllegalArgumentException` vẫn có thể thực sự ném nó. Trong mã thư viện, constructor cũng nên được tài liệu hóa bằng `@throws` nếu điều kiện đầu vào là phần quan trọng của API.

## 7. Sinh tài liệu từ thẻ `@throws`

Công cụ `javadoc` trong JDK chuyển các chú thích tài liệu `/** ... */` thành tài liệu API. Thẻ `@throws` đóng góp phần mô tả ngoại lệ, thường xuất hiện trong khu vực **Throws** của phương thức/constructor khi phần tử đó được đưa vào tài liệu.

Với tệp từ mục 6, có thể tạo tài liệu bằng:

~~~bash
javadoc -private -encoding UTF-8 -charset UTF-8 -d docs UncheckedDocumentationDemo.java
~~~

Lệnh `-private` giúp đưa cả lớp lồng và thành viên không công khai trong ví dụ vào đầu ra; khi tài liệu hóa thư viện thực tế, hãy chọn phạm vi hiển thị phù hợp với API mà người dùng cần. Tệp HTML được tạo trong thư mục `docs`; kết quả có thể gồm các trang lớp/thành viên và khu vực mô tả ngoại lệ của `add(int)`.

Trong dự án thực, thường dùng các plugin build (Gradle/Maven) để sinh Javadoc nhất quán, nhưng quy tắc viết `@throws` vẫn như nhau. Công cụ `javadoc` **không tự sửa code** để thực thi mọi điều kiện tài liệu; đoạn kiểm tra trong phương thức phải đúng với điều kiện được mô tả.

Các điểm cần đối chiếu trước khi phát hành API:

- Mỗi `@throws` mô tả được **một điều kiện kích hoạt cụ thể**, không chỉ lặp lại tên kiểu.
- Danh sách tài liệu phản ánh các ngoại lệ mà caller có thể dự đoán từ hợp đồng, thay vì mọi lỗi runtime có thể xảy ra do bug hoặc môi trường.
- Nếu điều kiện lỗi thay đổi khi refactor, cập nhật Javadoc và kiểm thử cùng mã triển khai.
- Tài liệu ở interface/lớp cha nên thống nhất với cam kết từ lớp triển khai, tránh mô tả những bảo đảm mà mọi implementation không thể giữ.

## 8. Có cần `throws RuntimeException` hoặc `throws Exception` cho chắc không?

Thông thường **không cần và không hữu ích** nếu mục tiêu chỉ là nói “phương thức có thể gặp một số lỗi runtime”.

~~~java
// Hợp lệ nhưng không mô tả được loại thất bại cụ thể.
static void process(String input) throws RuntimeException {
    // ...
}
~~~

`RuntimeException` là kiểu cha rộng của nhiều ngoại lệ unchecked, nên khai báo như trên không buộc caller xử lý và cũng không tiết lộ điều kiện hợp đồng cụ thể. Thay vào đó, nếu có một quy tắc đầu vào rõ ràng, ghi Javadoc cho ngoại lệ cụ thể sẽ giá trị hơn.

`throws Exception` còn rộng hơn: nó là checked exception trong phân cấp Java, vì vậy nếu API công bố `throws Exception`, lời gọi thường bị kiểm tra theo kiểu checked này. Không nên thêm `throws Exception` chỉ để “bao tất cả unchecked exception” vì nó thay đổi nghĩa vụ biên dịch và làm suy giảm khả năng đọc hợp đồng.

Cũng không nên viết `throws Throwable` hay `throws Error` như một thói quen. Mệnh đề `throws Error` là hợp lệ vì `Error` là unchecked, nhưng `Error` không phải kiểu thích hợp để biểu thị lỗi đầu vào hoặc trạng thái nghiệp vụ thông thường.

**Quy tắc thực hành:** nếu unchecked exception có điều kiện rõ và quan trọng với người sử dụng, hãy ghi loại lỗi cùng điều kiện trong Javadoc; việc lặp tên kiểu ở mệnh đề `throws` là một lựa chọn về phong cách và mức độ nổi bật của chữ ký.

## 9. Override, interface và `throws` unchecked exception

Với **checked exception**, phương thức override không được thêm loại checked mới hoặc rộng hơn so với phương thức cha/interface. Với **unchecked exception**, hạn chế này **không áp dụng**: override có thể khai báo hoặc ném thêm `RuntimeException` và lớp con, ngay cả khi giao diện không ghi chúng.

Chương trình chạy được `UncheckedOverrideDemo.java`:

~~~java
import java.util.Locale;

interface TextTransformer {
    /**
     * Chuyển chữ đầu vào.
     * @throws IllegalArgumentException nếu văn bản trống
     */
    String transform(String raw);
}

class StrictTransformer implements TextTransformer {
    @Override
    public String transform(String raw) throws IllegalArgumentException {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("văn bản trống");
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }
}

public class UncheckedOverrideDemo {
    public static void main(String[] args) {
        TextTransformer transformer = new StrictTransformer();
        System.out.println(transformer.transform("  java  "));
        try {
            transformer.transform(" ");
        } catch (IllegalArgumentException ex) {
            System.out.println("Lỗi: " + ex.getMessage());
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 UncheckedOverrideDemo.java
java UncheckedOverrideDemo
~~~

Stdout chính xác:

~~~text
JAVA
Lỗi: văn bản trống
~~~

`TextTransformer.transform` không khai báo `throws`, nhưng có thẻ `@throws IllegalArgumentException` mô tả tiền điều kiện. `StrictTransformer.transform` thêm `throws IllegalArgumentException` hợp lệ vì đây là unchecked exception. Đối tượng được sử dụng qua kiểu interface `TextTransformer`; compiler vẫn không bắt caller xử lý kiểu này.

Đối với **chính sách API**, việc một override được phép phát sinh unchecked exception **không đồng nghĩa nên tùy tiện đưa ra lỗi không phù hợp hợp đồng**. Một implementation nên tôn trọng ý nghĩa của phương thức cha, tài liệu và kỳ vọng của caller. Nếu một phương thức trong interface chấp nhận mọi chuỗi kể cả rỗng, implementation tự ý từ chối chuỗi rỗng có thể phá vỡ tính thay thế ngay cả khi Java vẫn cho biên dịch.

Nếu cùng một ngoại lệ được công bố ở interface và implementation, có thể dùng cơ chế kế thừa tài liệu Javadoc theo quy tắc của công cụ, nhưng cần kiểm tra trang tài liệu thực tế. Với điều kiện đặc thù implementation, mô tả thêm ở implementation để người dùng biết khác biệt.

## 10. Hợp đồng API: document tiền điều kiện, hậu điều kiện và trạng thái

Đối với unchecked exception, trọng tâm thường là **lỗi sử dụng API hoặc trạng thái không phù hợp**, hơn là một tình huống lỗi bên ngoài bắt buộc caller phục hồi.

Giả sử bạn thiết kế:

~~~java
/**
 * Gửi một yêu cầu khi client còn mở.
 *
 * @param request yêu cầu khác null
 * @throws NullPointerException nếu request là null
 * @throws IllegalStateException nếu client đã đóng
 */
void send(Request request);
~~~

Đây là **chữ ký minh họa** (cần định nghĩa `Request` và bối cảnh lớp). Caller hiểu rằng cần bảo đảm `request != null` và vòng đời client còn hiệu lực. Trong phần triển khai, bạn có thể dùng `Objects.requireNonNull(request, "request")` để chủ động kiểm tra `null` và ném `NullPointerException` rõ ràng:

~~~java
// Đoạn minh họa trong một phương thức có tham số request.
java.util.Objects.requireNonNull(request, "request");
~~~

Đối với input khác `null` nhưng sai miền giá trị, `IllegalArgumentException` thường phù hợp hơn; đối với đối tượng không ở trạng thái cho phép thao tác, `IllegalStateException` thường rõ nghĩa hơn. Tuy nhiên các tên kiểu không thay thế được **quy định rõ điều kiện**. Những API khác nhau có thể chọn kiểu khác nhau miễn nhất quán và được tài liệu hóa.

Một số nguyên tắc viết hợp đồng:

1. Nêu ngắn gọn thao tác và các điều kiện thành công ở `@param` và phần mô tả.
2. Dùng `@throws` cho điều kiện lỗi có chủ đích mà caller thực sự cần tránh hoặc có thể xử lý.
3. Nếu phương thức phải bảo đảm “không thay đổi trạng thái khi đầu vào không hợp lệ”, hãy triển khai để bảo đảm đó đúng và thử với các trường hợp lỗi.
4. Nếu API có thể thất bại do môi trường (I/O, kết nối, quyền truy cập), cân nhắc ngoại lệ checked hoặc mô hình kết quả theo thiết kế API cụ thể; đừng tự động bọc mọi checked exception thành unchecked.
5. Giữ tên thông điệp và loại lỗi nhất quán để người dùng đọc log dễ hơn, nhưng không phụ thuộc vào việc so sánh chuỗi thông điệp để quyết định nhánh xử lý.

## 11. Ghi vào `throws` có giúp reflection hay IDE nhận diện không?

Có. Mệnh đề `throws` là một phần metadata ngoại lệ được khai báo của phương thức/constructor và có thể được đọc bằng các API reflection, ví dụ:

~~~java
// Đoạn minh họa, đặt trong phương thức có throws ReflectiveOperationException.
Class<?>[] declared =
        UncheckedDocumentationDemo.Counter.class
                .getDeclaredMethod("add", int.class)
                .getExceptionTypes();
~~~

Với mã `Counter.add` ở mục 6, danh sách `getExceptionTypes()` bao gồm `IllegalArgumentException` và `IllegalStateException` như chúng được khai báo ở chữ ký. Nó **không đồng nghĩa với danh sách tất cả ngoại lệ runtime có thể xuất hiện**, và **không đọc trực tiếp phần văn bản của thẻ Javadoc `@throws`**.

IDE và công cụ phân tích mã có thể dùng chữ ký cùng tài liệu nguồn để hiển thị hợp đồng. Tuy nhiên, `throws` không cung cấp điều kiện chi tiết, còn Javadoc thường nằm ở mã nguồn hoặc tài liệu sinh ra. Vì vậy hai kênh thông tin bổ trợ nhau:

- **`throws`**: kiểu ngoại lệ được khai báo trong chữ ký, có thể phản chiếu.
- **`@throws`**: mô tả điều kiện cho con người và tài liệu API, không biến thành ràng buộc biên dịch mới.

Nếu API của bạn chỉ ghi `@throws IllegalArgumentException` mà không có mệnh đề `throws`, `getExceptionTypes()` không thể suy ra loại đó từ Javadoc.

## 12. Ảnh hưởng đến tương thích và thay đổi API

Vì unchecked exception không bị ràng buộc bởi catch-or-declare, **việc thêm `throws IllegalStateException` trong chữ ký không làm caller buộc thêm `catch`**. Nhưng thay đổi hành vi thực tế — ví dụ một phiên bản mới bắt đầu từ chối đối số vốn được chấp nhận — vẫn có thể làm hỏng ứng dụng sử dụng thư viện.

Các ví dụ cần thận trọng:

- Trước đây `send(null)` có ý nghĩa “không làm gì”, nhưng phiên bản sau ném `NullPointerException`: đây là thay đổi hợp đồng hành vi dù mã cũ vẫn biên dịch.
- `read()` trước đây cho phép gọi sau `close()`, nay ném `IllegalStateException`: cần đánh giá khả năng tương thích và cập nhật tài liệu/test.
- Một lớp thay `IllegalArgumentException` bằng `IllegalStateException` cho cùng một trường hợp: caller có thể đang bắt riêng một trong hai loại để phục hồi; nên cân nhắc ổn định ngữ nghĩa ngoại lệ.
- Thêm `throws IOException` (checked) vào API thì khác: caller hiện hữu có thể nhận lỗi biên dịch nếu chưa xử lý kiểu checked mới.

Không nên nhầm “không có lỗi biên dịch” với “không có thay đổi đáng kể đối với người dùng API”. Hợp đồng tài liệu hóa là một phần quan trọng của thiết kế công khai.

## 13. Lỗi thường gặp

| Nhầm lẫn/sai sót | Vì sao không đúng | Cách cải thiện |
| --- | --- | --- |
| Nghĩ unchecked exception không được khai báo trong `throws` | Java cho phép khai báo các kiểu con `RuntimeException`/`Error`. | Có thể ghi khi hữu ích; compiler vẫn không yêu cầu caller bắt. |
| Nghĩ chỉ cần thêm `throws IllegalArgumentException` là caller buộc `catch` | Đây là unchecked exception, không áp dụng nghĩa vụ catch-or-declare. | Viết điều kiện `@throws` và xử lý ở caller khi có chiến lược phù hợp. |
| Nghĩ `@throws` bắt buộc tương ứng với mệnh đề `throws` | Đối với unchecked exception, Javadoc có thể tài liệu hóa cả khi chữ ký không khai báo. | Phân biệt Javadoc với cú pháp ngôn ngữ. |
| Ghi `@throws RuntimeException nếu xảy ra lỗi` | Điều kiện quá mơ hồ, khó dùng để viết mã đúng. | Ghi loại cụ thể và tình huống như `delta <= 0`. |
| Liệt kê mọi exception do JVM và thư viện phụ thuộc có thể ném | Tài liệu nhiễu, khó bảo trì, dễ cam kết sai. | Chỉ công bố những ngoại lệ thuộc hợp đồng và có giá trị với caller. |
| Dùng `throws Exception` chỉ để “nhắc rằng có lỗi runtime” | Gây nghĩa vụ xử lý checked `Exception`, làm chữ ký quá rộng. | Bỏ khai báo rộng; dùng `@throws` cụ thể nếu cần. |
| Thêm unchecked exception ở override rồi cho rằng chắc chắn đúng hợp đồng | Ngôn ngữ cho phép nhưng hành vi có thể phá cam kết interface. | Giữ tiền điều kiện và hậu điều kiện hợp lý với hợp đồng đã công bố. |
| Tài liệu ghi một điều kiện, code ném lỗi trong điều kiện khác | Người dùng API không thể dựa vào tài liệu. | Đồng bộ code, Javadoc và bài kiểm thử điều kiện biên. |
| Dùng `Error` như lỗi nghiệp vụ bình thường | `Error` thường biểu thị tình trạng đặc biệt nghiêm trọng, không dành cho yêu cầu nhập liệu. | Ưu tiên exception phù hợp theo ý nghĩa API. |
| Ghi log, nuốt hoặc bọc tùy tiện mọi unchecked exception | Có thể giấu bug hoặc mất chuỗi nguyên nhân. | Xử lý nơi có giải pháp thực sự; khi bọc nên giữ `cause`. |

## 14. Tự kiểm tra

1. **`NumberFormatException` là checked hay unchecked?** — Unchecked vì nó thuộc nhánh `IllegalArgumentException → RuntimeException`.
2. **Gọi `void save() throws IllegalStateException` có bắt buộc thêm `try/catch` không?** — Không; đây là unchecked exception.
3. **Nếu một API không ghi `throws IllegalArgumentException`, nó có được `throw new IllegalArgumentException(...)` không?** — Có.
4. **Javadoc `@throws IllegalArgumentException` có tác động tới kiểm tra ngoại lệ của compiler không?** — Không; nó mô tả hợp đồng API cho người đọc.
5. **Viết `@throws` cho unchecked exception nhưng bỏ nó khỏi chữ ký `throws` có hợp lệ không?** — Có.
6. **Phương thức override có thể thêm `throws IllegalArgumentException` dù interface không khai báo không?** — Có về mặt ngôn ngữ; vẫn cần bảo đảm hợp đồng hành vi.
7. **Vì sao `throws Exception` không phải cách tốt để tài liệu hóa một `IllegalArgumentException`?** — `Exception` là checked và quá rộng, gây nghĩa vụ biên dịch và làm mờ hợp đồng.
8. **Khi nào `@throws IllegalStateException` hữu ích?** — Khi có điều kiện trạng thái cụ thể, như gọi phương thức sau khi đối tượng đã đóng.
9. **Reflection `getExceptionTypes()` có suy ra thẻ `@throws` không?** — Không; nó đọc các kiểu được khai báo trong chữ ký, không phải văn bản Javadoc.
10. **Thêm unchecked exception mới vào hành vi API có thể phá tính tương thích không?** — Có về mặt hành vi dù mã nguồn của caller vẫn có thể biên dịch.

## Tài liệu đối chiếu

- [Java Language Specification 17 — §11.1.1, The Kinds of Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.1.1): phân loại checked và unchecked exception.
- [Java Language Specification 17 — §11.2, Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2): các kiểu ngoại lệ được compiler yêu cầu bắt hoặc khai báo.
- [Java Language Specification 17 — §8.4.6, Method Throws](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.6): mệnh đề `throws` trong phương thức.
- [Java Language Specification 17 — §8.4.8.3, Requirements in Overriding and Hiding](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.8.3): giới hạn checked exception khi override.
- [Javadoc Tool — `@throws` và `@exception` Tags](https://docs.oracle.com/en/java/javase/17/javadoc/javadoc.html): cú pháp tài liệu mô tả ngoại lệ.
- [Java SE 17 API — `Executable.getExceptionTypes()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Executable.html#getExceptionTypes()): kiểu ngoại lệ đã được khai báo trong chữ ký.

**Ghi nhớ:** với unchecked exception, mệnh đề `throws` là tùy chọn theo luật Java; thẻ Javadoc `@throws` giải thích **khi nào và vì sao** lỗi xảy ra. Một API tốt dùng chúng có chủ đích để công bố điều kiện sử dụng, đồng thời thực thi đúng hợp đồng trong mã nguồn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="throws-clause-008">Vì sao throws không có nghĩa phương thức luôn phát sinh lỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao `throws` không có nghĩa phương thức luôn phát sinh lỗi trong Java

Một phương thức Java có thể khai báo `throws IOException` nhưng chạy thành công hàng nghìn lần mà **không ném một ngoại lệ `IOException` nào**. Từ khóa `throws` mô tả **khả năng ngoại lệ truyền ra khỏi phương thức theo hợp đồng kiểu**; nó không phải câu lệnh ném lỗi, không khiến JVM tự tạo ngoại lệ, và không bảo đảm rằng điều kiện gây lỗi sẽ xảy ra.

Ví dụ:

~~~java
static String readSetting(boolean sourceAvailable)
        throws java.io.IOException {
    if (!sourceAvailable) {
        throw new java.io.IOException("Không có nguồn cấu hình");
    }
    return "READY";
}
~~~

Đây là **phương thức minh họa**, cần đặt trong một lớp Java. Khi gọi `readSetting(true)`, nhánh `throw` không chạy và phương thức trả `"READY"` bình thường. Khi gọi `readSetting(false)`, phương thức tạo rồi ném `IOException`. Cả hai khả năng đều phù hợp với cùng một chữ ký `throws IOException`.

## 1. Phân biệt rõ `throws` và `throw`

`throws IOException` là **khai báo khả năng** của phương thức/constructor, còn `throw new IOException("failed");` là **hành động ném** nếu luồng chạy đi tới câu lệnh. Một `try/catch` ở caller chỉ xử lý ngoại lệ **thật sự truyền tới handler**; nó không tự tạo thất bại.

Vì vậy, một phương thức có thể khai báo `throws IOException` dù thân hiện tại chỉ `return`, hoặc có nhánh `throw` nhưng không đi qua nhánh ấy trong một lần gọi cụ thể. Đây là mấu chốt của bài: **khả năng theo chữ ký không đồng nghĩa xác suất hoặc bảo đảm thất bại**. Các ví dụ tiếp theo kiểm tra trực tiếp sự khác biệt bằng nhiều đường chạy.

## 2. Cơ chế: khả năng theo hợp đồng và sự kiện khi chạy

Trình biên dịch xem **chữ ký phương thức** và các biểu thức có khả năng ném ngoại lệ để kiểm tra quy tắc *checked exceptions*. JVM lúc chạy mới thực thi những nhánh lệnh thực sự được chọn.

Đối với phương thức:

~~~java
static String load(boolean available)
        throws java.io.IOException {
    if (!available) {
        throw new java.io.IOException("Nguồn không khả dụng");
    }
    return "Dữ liệu";
}
~~~

Luồng điều khiển có hai đường:

~~~text
                     Gọi load(available)
                               |
                     available == true?
                        /             \
                      Có              Không
                       |                 |
                 return "Dữ liệu"    throw IOException
                       |                 |
                Kết thúc bình thường   Kết thúc bất thường
                       |                 |
                  Caller nhận String  Tìm catch phù hợp
~~~

Khai báo `throws IOException` vẫn giống nhau trên cả hai đường. Nó tồn tại **trước khi có giá trị thực tế của tham số**. Để đánh giá kết quả một lần gọi cụ thể, cần biết giá trị đầu vào, trạng thái tài nguyên, nhánh điều kiện, các lời gọi bên dưới và môi trường thực thi.

Lưu ý: Java không dựa vào `throws` để bảo đảm rằng phương thức *chắc chắn* thực hiện lệnh `throw`. Kể cả khi phần thân hiện tại không có đường ném checked exception, khai báo `throws` vẫn có thể hợp lệ; nó có thể thể hiện hợp đồng API hoặc để ngỏ khả năng triển khai khác.

## 3. Ví dụ Java 17+ độc lập: cùng `throws`, có lần thành công và có lần thất bại

Tạo tệp **`ThrowsPossibilityDemo.java`**:

~~~java
import java.io.IOException;

public class ThrowsPossibilityDemo {
    interface Provider {
        String load() throws IOException;
    }

    static class ConstantProvider implements Provider {
        @Override
        public String load() {
            return "constant";
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("Declared only: " + declaredOnly());
        } catch (IOException e) {
            System.out.println("Declared only failed");
        }

        for (boolean available : new boolean[] {true, false}) {
            try {
                System.out.println(
                        "Available: " + readSetting(available));
            } catch (IOException e) {
                System.out.println("Failure: " + e.getMessage());
            }
        }

        Provider provider = new ConstantProvider();
        try {
            System.out.println("Provider: " + provider.load());
        } catch (IOException e) {
            System.out.println("Provider failed");
        }

        System.out.println("Handled locally: " + loadOrDefault(false));

        try {
            System.out.println("Forwarded: " + forward(true));
        } catch (IOException e) {
            System.out.println("Forwarded failed");
        }

        System.out.println("Unchecked: " + requireText("ok"));
    }

    static String declaredOnly() throws IOException {
        return "constant";
    }

    static String readSetting(boolean available) throws IOException {
        if (!available) {
            throw new IOException("setting unavailable");
        }
        return "ready";
    }

    static String loadOrDefault(boolean available) {
        try {
            return readSetting(available);
        } catch (IOException e) {
            return "default";
        }
    }

    static String forward(boolean available) throws IOException {
        return readSetting(available);
    }

    static String requireText(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        return value;
    }
}
~~~

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

~~~bash
javac ThrowsPossibilityDemo.java
java ThrowsPossibilityDemo
~~~

**Stdout chính xác:**

~~~text
Declared only: constant
Available: ready
Failure: setting unavailable
Provider: constant
Handled locally: default
Forwarded: ready
Unchecked: ok
~~~

### Giải thích theo từng tình huống

**1. Chỉ khai báo, không ném trong thân hàm.** `declaredOnly()` có chữ ký `throws IOException` nhưng chỉ trả chuỗi. Caller phải viết mã hợp lệ với hợp đồng checked exception; trong lần chạy này khối `catch` không hoạt động.

**2. Cùng một phương thức, hai kết quả.** `readSetting(true)` trả `"ready"` và không có ngoại lệ. `readSetting(false)` đi vào nhánh `throw new IOException(...)`. Vì vậy cùng chữ ký `throws IOException` không chỉ ra rằng mọi lần gọi đều thất bại.

**3. Chữ ký interface rộng hơn triển khai cụ thể.** `Provider.load()` cho phép ném `IOException`. `ConstantProvider.load()` ghi đè phương thức mà không khai báo ngoại lệ checked nào. Gọi qua biến kiểu `Provider` vẫn được trình biên dịch đánh giá theo chữ ký `Provider.load()`; đó là lý do khối `try/catch` ở `main` có ý nghĩa dù triển khai đang dùng luôn trả chuỗi.

**4. Phương thức xử lý lỗi bên trong.** `loadOrDefault(false)` gọi phương thức có thể ném `IOException` nhưng tự bắt và trả `"default"`. Bên gọi `loadOrDefault` không phải xử lý `IOException` theo chữ ký của phương thức này, vì ngoại lệ đó không thoát ra trong đường thực thi được thiết kế.

**5. Phương thức trung gian chuyển tiếp.** `forward(true)` khai báo `throws IOException` do gọi `readSetting(...)` mà không bắt. Khi `available` là `true`, phương thức vẫn thành công: quyền *cho phép lan truyền* không phải nghĩa vụ *phải ném*.

**6. Unchecked exception có thể ném mà không khai báo.** `requireText(...)` không có `throws` nhưng vẫn có thể ném `IllegalArgumentException` khi đầu vào là `null`. Đây là ví dụ đối xứng: sự xuất hiện hoặc vắng mặt của `throws` không đủ để khẳng định phương thức sẽ chạy thành công hay thất bại trong mọi trường hợp.

## 4. Tại sao compiler buộc xử lý khi lần gọi thực tế có thể thành công?

Với checked exception, người gọi thường phải **bắt hoặc khai báo** ngoại lệ có thể truyền ra theo hợp đồng phương thức.

Ví dụ:

~~~java
static void caller() throws java.io.IOException {
    String value = readSetting(true);
    System.out.println(value);
}
~~~

Đây là phương thức minh họa trong cùng lớp với `readSetting`. Lời gọi sử dụng giá trị literal `true`, nhưng phương thức được khai báo `throws IOException` nên `caller()` vẫn phải thỏa nghĩa vụ checked exception. Trình biên dịch không biến việc “đầu vào hiện tại có vẻ an toàn” thành cam kết rằng checked exception không thể truyền ra.

Nếu bỏ `throws IOException` trong chữ ký `caller()` mà cũng không có `try/catch`, trình biên dịch báo lỗi vì `IOException` chưa được bắt hoặc khai báo.

Cách xử lý tại caller:

~~~java
static void callerHandled() {
    try {
        String value = readSetting(true);
        System.out.println(value);
    } catch (java.io.IOException e) {
        System.out.println("Không đọc được: " + e.getMessage());
    }
}
~~~

Ở lần gọi với `true`, nhánh `catch` không chạy. Nhưng **mã nguồn vẫn có trách nhiệm cho tình huống lỗi mà hợp đồng cho phép**.

Đối với mã thật, khả năng ném ngoại lệ có thể thay đổi theo hệ thống tệp, mạng, quyền truy cập, trạng thái thread hoặc việc triển khai API. Vì vậy compiler không thể coi một lần thử nghiệm thành công là bằng chứng hợp đồng không bao giờ thất bại.

## 5. Khai báo `throws` không bắt buộc có `throw` trực tiếp

Một phương thức có thể khai báo `throws IOException` dù trong thân không hề viết `throw new IOException(...)`:

~~~java
static String readUtf8(java.nio.file.Path path)
        throws java.io.IOException {
    return java.nio.file.Files.readString(path);
}
~~~

Phương thức này sử dụng `Files.readString`, một API có thể ném `IOException`. Nếu thao tác đọc thành công, `readUtf8` trả kết quả. Nếu API ném `IOException`, ngoại lệ đi qua `readUtf8` vì không có xử lý bên trong.

Vì thế cần phân biệt hai nguồn có thể làm ngoại lệ đi ra khỏi phương thức:

1. **Ném trực tiếp:** thân phương thức thực thi `throw new IOException(...)`.
2. **Lan truyền từ lời gọi bên dưới:** phương thức gọi API khác, API đó ném ngoại lệ và tầng hiện tại không bắt.

Trong cả hai tình huống, `throws IOException` khai báo khả năng truyền ra ngoài, không khẳng định nhánh lỗi sẽ xảy ra.

Nếu người viết bắt và xử lý trọn vẹn mọi `IOException` có thể phát sinh trong thân hàm rồi trả kết quả bình thường, khai báo `throws IOException` có thể không còn cần thiết về mặt biên dịch. Tuy nhiên việc giữ hoặc bỏ nó có thể liên quan đến **hợp đồng công khai** của API và khả năng tương thích với các bên gọi.

## 6. `throws` còn thể hiện hợp đồng ở interface và lớp trừu tượng

Hãy xem khai báo:

~~~java
interface DataSource {
    String fetch() throws java.io.IOException;
}
~~~

Đây là interface minh họa. Nó không chứa phần thân triển khai nên không có lệnh ném nào để quan sát. Nó cho phép các triển khai gặp lỗi I/O theo hợp đồng.

Một triển khai đơn giản có thể luôn thành công:

~~~java
final class MemoryDataSource implements DataSource {
    @Override
    public String fetch() {
        return "cached";
    }
}
~~~

Một triển khai khác có thể cần tài nguyên bên ngoài:

~~~java
final class FileDataSource implements DataSource {
    private final java.nio.file.Path path;

    FileDataSource(java.nio.file.Path path) {
        this.path = path;
    }

    @Override
    public String fetch() throws java.io.IOException {
        return java.nio.file.Files.readString(path);
    }
}
~~~

Đây là các đoạn kiểu Java 17 có thể đặt cùng một tệp với interface trong ngữ cảnh phù hợp. `MemoryDataSource` không cần khai báo `throws IOException` vì bản triển khai không làm phát sinh lỗi đó; `FileDataSource` có thể khai báo và ném khi thao tác đọc thất bại.

Nếu mã gọi thông qua tham chiếu kiểu `DataSource`, trình biên dịch dựa vào chữ ký `DataSource.fetch()` để buộc xử lý hoặc khai báo `IOException`. Lời gọi runtime có thể được điều phối tới bất kỳ implementation hợp lệ nào.

Khi ghi đè phương thức, lớp con **không được mở rộng checked exceptions** vượt quá các loại mà hợp đồng phương thức cha cho phép. Việc không khai báo checked exception, hoặc thu hẹp loại ngoại lệ checked, thường hợp lệ. Quy tắc này giúp caller dùng kiểu cha mà không phải thay đổi hợp đồng checked khi chọn một implementation khác.

## 7. `throws` cũng không liệt kê mọi ngoại lệ runtime có thể xảy ra

Khai báo:

~~~java
static int parse(String text) {
    return Integer.parseInt(text);
}
~~~

không có `throws`, nhưng `parse("abc")` có thể ném `NumberFormatException`, một lớp con của `RuntimeException`. Unchecked exceptions không bắt buộc được liệt kê trong mệnh đề `throws`.

Tương tự, phương thức có `throws IOException` **vẫn có thể** ném `IllegalArgumentException`, `NullPointerException` hoặc lỗi unchecked khác trên đường thực thi. Mệnh đề `throws IOException` không có nghĩa “chỉ có đúng một kiểu lỗi là `IOException`”, cũng không phải một cơ chế bảo đảm an toàn tuyệt đối.

Các ngoại lệ checked thường là những loại mà Java bắt người gọi phải cân nhắc theo hợp đồng kiểu; các ngoại lệ unchecked có thể được nêu trong JavaDoc hoặc trong `throws` để giúp đọc API, nhưng điều đó không biến chúng thành checked.

Ví dụ khai báo hợp lệ:

~~~java
static int positive(String value)
        throws IllegalArgumentException {
    int result = Integer.parseInt(value);
    if (result <= 0) {
        throw new IllegalArgumentException("Giá trị phải dương");
    }
    return result;
}
~~~

Chữ ký trên chứa `throws IllegalArgumentException`, nhưng caller **không bắt buộc** viết `try/catch` vì `IllegalArgumentException` là unchecked. Dù vậy nó vẫn có thể bị ném khi đầu vào sai; `throws` ở đây đóng vai trò tài liệu hợp đồng hơn là áp đặt nghĩa vụ biên dịch.

## 8. Không phải lúc nào phương thức không khai báo `throws` cũng an toàn

Đặt cạnh nhau:

| Khai báo | Điều có thể xảy ra khi chạy | Nghĩa vụ compile-time thông thường |
| --- | --- | --- |
| `String fetch() throws IOException` | Có thể trả chuỗi hoặc ném `IOException` | Caller phải bắt/khai báo `IOException` |
| `String constant() throws IOException` | Trong triển khai đang xét, chỉ trả chuỗi | Caller vẫn xử lý theo chữ ký checked |
| `String safeFetch()` có `catch IOException` bên trong | Có thể dùng fallback và trả chuỗi; tùy logic còn có thể ném lỗi unchecked | Caller không bị buộc bởi `IOException` nếu nó không thoát ra trong hợp đồng |
| `int parse(String s)` không khai báo `throws` | Có thể trả số hoặc ném `NumberFormatException` | Không bắt buộc bắt unchecked exception |
| `void fail()` luôn `throw new IllegalStateException(...)` | Mỗi lần đi tới `throw` thì kết thúc bất thường | Không cần `throws` vì unchecked |

Từ đó rút ra:

- **Có `throws` không suy ra luôn thất bại.**
- **Không có `throws` không suy ra luôn thành công.**
- **Bắt một checked exception không có nghĩa thao tác ban đầu luôn được hoàn thành:** đôi khi chỉ dừng lỗi có kiểm soát hoặc trả fallback.
- **Không được dùng chỉ riêng chữ ký `throws` để tính tỉ lệ lỗi:** muốn biết tỉ lệ, cần dữ liệu vận hành hoặc phân tích điều kiện thất bại.

## 9. `throws` và quan hệ với `return`

Một phương thức khai báo trả về `String` hoặc `int` vẫn có thể ném ngoại lệ. Những đường thực thi **hoàn tất bình thường** phải trả giá trị theo quy tắc kiểu của phương thức; đường `throw` kết thúc bất thường nên không cần tạo giá trị.

~~~java
static int compute(boolean valid)
        throws java.io.IOException {
    if (!valid) {
        throw new java.io.IOException("Không thể tính");
    }
    return 42;
}
~~~

Đây là phương thức minh họa. Với `compute(true)`, caller nhận `42`; với `compute(false)`, caller không nhận một giá trị `int` nào từ lần gọi đó. JVM chuyển điều khiển sang cơ chế tìm handler.

Mệnh đề `throws IOException` **không thay `return`** và không tự “bật” một nhánh lỗi. Nếu mã chỉ có:

~~~java
static int invalid() throws java.io.IOException {
    System.out.println("Chạy");
}
~~~

thì phương thức **không biên dịch** vì có thể kết thúc bình thường mà không trả `int`. Việc thêm `throws IOException` không sửa được lỗi thiếu giá trị trả về. Đường `throw` thực sự mới là đường kết thúc bất thường.

Ngược lại, phương thức sau biên dịch hợp lệ:

~~~java
static int alwaysFail() throws java.io.IOException {
    throw new java.io.IOException("Không có kết quả");
}
~~~

Phương thức chỉ gồm một đường `throw`, nên không có đường nào hoàn tất bình thường để phải trả `int`. Tuy nhiên **điều khiến phương thức luôn ném ở đây là thân hàm**, không phải riêng mệnh đề `throws`. Đừng dùng ví dụ này để suy ra mọi phương thức có `throws` đều hành xử như vậy.

## 10. Caller nên sử dụng `throws` thế nào?

Khi đọc API:

~~~java
public String loadConfiguration() throws java.io.IOException
~~~

hãy hiểu: “Kết quả thông thường là một `String`, nhưng trong những điều kiện được thiết kế hoặc thực thi cho phép, lỗi I/O có thể lan ra. Bên gọi cần có quyết định theo hợp đồng checked exception.”

Caller có hai cách cơ bản.

### Bắt ở tầng biết cách xử lý

~~~java
static String loadOrFallback(java.nio.file.Path path) {
    try {
        return java.nio.file.Files.readString(path);
    } catch (java.io.IOException e) {
        return "default";
    }
}
~~~

Đây là một phương thức minh họa. `catch` chỉ chạy khi việc đọc thất bại bằng `IOException`. Nó **không** chạy khi đọc thành công. Trả chuỗi `"default"` chỉ hợp lệ nếu cấu hình mặc định được phép theo hợp đồng; không nên dùng fallback để che lỗi của một tệp bắt buộc.

### Chuyển tiếp để tầng trên quyết định

~~~java
static String loadRequired(java.nio.file.Path path)
        throws java.io.IOException {
    return java.nio.file.Files.readString(path);
}
~~~

Ở cách này, tầng hiện tại không có thông tin để chọn fallback. Caller bên ngoài có thể quyết định cho người dùng chọn tệp khác, dừng khởi tạo hoặc báo lỗi. Nếu đọc thành công, không có ngoại lệ để chuyển tiếp.

### Khi nào cần khai báo loại cụ thể?

Ưu tiên `throws IOException` nếu thất bại được mô tả chính xác bằng `IOException`. Khai báo quá rộng như `throws Exception` có thể khiến bên gọi phải xử lý nhóm lỗi không cần thiết và làm hợp đồng khó hiểu. Dù vậy các API kế thừa hoặc một số ranh giới framework có thể đã có chữ ký rộng; cần tôn trọng hợp đồng sẵn có.

## 11. Các hiểu lầm thường gặp

### “Có `throws` thì chắc chắn phương thức ném ngoại lệ”

Sai. `throws` khai báo **khả năng**, không thực hiện lệnh ném. Phương thức có thể trả kết quả bình thường vì điều kiện gây lỗi không xảy ra.

### “Có `throws IOException` nghĩa là phương thức phải chứa `throw new IOException`”

Sai. Ngoại lệ có thể phát sinh từ một lời gọi bên dưới và lan truyền. Thậm chí một phương thức chỉ trả hằng số vẫn được phép giữ khai báo `throws IOException`.

### “Caller có `catch` nghĩa là catch sẽ luôn chạy”

Sai. `catch` chỉ được thực thi khi có ngoại lệ phù hợp thực sự truyền đến vùng xử lý đó. Khi `try` hoàn tất bình thường, nhánh `catch` được bỏ qua.

### “Đã có `throws` thì compiler tự bắt lỗi giúp mình”

Sai. `throws` không xử lý lỗi, chỉ chuyển nghĩa vụ checked cho caller (hoặc khai báo khả năng truyền từ phương thức). Khi runtime phát sinh ngoại lệ mà không có handler phù hợp, ngoại lệ vẫn truyền ra khỏi luồng gọi hiện tại.

### “Không có `throws` thì không có ngoại lệ”

Sai. Unchecked exceptions như `IllegalArgumentException`, `NumberFormatException` hoặc `NullPointerException` có thể được ném mà không cần khai báo.

### “Khai báo `throws IOException` nghĩa là xác suất lỗi cao”

Sai. Chữ ký không mã hóa tần suất, độ nghiêm trọng hay khả năng phục hồi. Một API đọc dữ liệu ổn định có thể hầu như luôn thành công, trong khi API khác gặp lỗi rất thường xuyên; cả hai có thể có cùng chữ ký.

### “Lời gọi với tham số an toàn không cần quan tâm checked exception”

Không thể suy ra như vậy chỉ từ một tham số. Trình biên dịch theo hợp đồng của lời gọi và quy tắc ngôn ngữ; nó không thử chạy hoặc chứng minh mọi điều kiện môi trường đều an toàn để tự loại bỏ nghĩa vụ checked.

### “Triển khai nào của interface cũng phải có `throws` y hệt”

Không. Ghi đè một phương thức cho phép checked exception thường có thể bỏ khai báo hoặc thu hẹp ngoại lệ checked. Tuy nhiên nếu caller dùng biến có **kiểu interface**, compiler vẫn dựa vào chữ ký interface.

### “`throws` trả về một đối tượng Exception”

Sai. `throws` là phần của khai báo phương thức, không phải một phần của danh sách tham số hay kiểu giá trị trả về. Giá trị trả về thuộc `return` khi phương thức thành công; đối tượng ngoại lệ do `throw` hoặc lời gọi bên dưới ném khi có lỗi.

### “`throws Exception` là cách chuẩn cho mọi phương thức”

Không. Khai báo rộng khiến người gọi khó biết loại thất bại cần xử lý. Hãy chọn loại cụ thể khi có thể, theo hợp đồng API và kiến trúc chương trình.

## 12. Thực hành: dự đoán kết quả và xác minh bằng Java

Xét phương thức:

~~~java
static String choose(int code)
        throws java.io.IOException {
    if (code == 1) {
        return "ok";
    }
    if (code == 2) {
        throw new java.io.IOException("bad input source");
    }
    return "fallback";
}
~~~

Đây là đoạn phương thức minh họa.

| Lời gọi | Kết quả |
| --- | --- |
| `choose(1)` | Trả `"ok"` |
| `choose(2)` | Ném `IOException("bad input source")` |
| `choose(3)` | Trả `"fallback"` |

Chữ ký `throws IOException` không đổi giữa ba lời gọi. Việc chọn đường xử lý do giá trị `code` quyết định.

### Câu hỏi tự kiểm tra

1. Nếu phương thức `static String name() throws IOException { return "A"; }` không có `throw`, nó có hợp lệ không? Caller phải làm gì khi gọi qua chữ ký đó?
2. Nếu gọi `readSetting(true)` thành công, nhánh `catch (IOException e)` có chạy không?
3. Một phương thức không viết `throw` nhưng gọi `Files.readString` có thể để `IOException` truyền ra ngoài không?
4. Vì sao `MemoryDataSource.fetch()` có thể bỏ `throws IOException` trong khi interface `DataSource.fetch()` có khai báo đó?
5. `static int value() throws IOException { System.out.println("x"); }` có biên dịch không? Vì sao?
6. Một phương thức `throws IOException` có thể ném `IllegalArgumentException` dù không liệt kê nó trong chữ ký không?
7. Trường hợp nào cho thấy một phương thức **luôn ném lỗi**? Bạn cần xem mỗi chữ ký `throws`, hay phải xem các đường thực thi của phần thân?
8. Khi nào nên bắt `IOException` ở tầng hiện tại, và khi nào nên cho phép lan truyền bằng `throws`?
9. Có thể ước lượng xác suất lỗi từ mệnh đề `throws` không?
10. Tại sao việc kiểm thử một lần thành công không chứng minh phương thức không bao giờ ném ngoại lệ?
11. Caller đang có biến kiểu `Provider`. Dù đối tượng runtime là `ConstantProvider`, vì sao lời gọi `provider.load()` vẫn phải tuân thủ `throws IOException`?
12. Nếu phương thức nội bộ bắt hoàn toàn `IOException` và trả một giá trị fallback hợp lệ, caller của phương thức đó có bắt buộc xử lý `IOException` nữa không?

### Đáp án gợi ý

1. Có; caller tuân thủ nghĩa vụ checked exception theo chữ ký, chẳng hạn bắt hoặc khai báo.
2. Không, vì không có ngoại lệ phù hợp truyền tới `catch`.
3. Có, vì ngoại lệ có thể lan truyền từ lời gọi bên dưới.
4. Ghi đè được phép thu hẹp hoặc loại bỏ checked exceptions; caller dùng kiểu interface vẫn dựa vào hợp đồng interface.
5. Không; phương thức có đường hoàn tất bình thường mà không trả `int`, `throws` không thay `return`.
6. Có, `IllegalArgumentException` là unchecked.
7. Phải phân tích luồng điều khiển thực tế; chỉ `throws` không đủ kết luận.
8. Bắt khi tầng đó biết cách xử lý đúng; chuyển tiếp khi tầng trên có trách nhiệm quyết định.
9. Không, mệnh đề chỉ nêu khả năng loại ngoại lệ, không có số liệu xác suất.
10. Lần thử nghiệm chỉ bao phủ một trạng thái và đường thực thi.
11. Compiler nhìn chữ ký `Provider.load()` có `throws IOException`.
12. Nếu phương thức không khai báo `throws IOException` và đã xử lý checked exception bên trong thì caller không bắt buộc xử lý lỗi đó theo hợp đồng.

## 13. Ghi nhớ

**`throws` là thông tin về khả năng ngoại lệ thoát khỏi một phương thức, không phải một hành động phát sinh lỗi và cũng không phải cam kết rằng lỗi chắc chắn xảy ra.** Có thể gọi cùng một phương thức rồi nhận kết quả bình thường hoặc gặp ngoại lệ tùy đầu vào và điều kiện thực thi. Hiểu đúng `throws` giúp đọc hợp đồng API, thiết kế xử lý checked exceptions và dự đoán luồng thực thi mà không nhầm giữa *khả năng thất bại* với *một lần thất bại thực sự*.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="throws-clause-009">Hợp đồng ngoại lệ của main và giới hạn khi đẩy lỗi lên trên cùng</a>

<details>
<summary>Xem chi tiết</summary>

# Hợp đồng ngoại lệ của `main` và giới hạn khi đẩy lỗi lên trên cùng

## 1. Mục tiêu và vấn đề cần giải quyết

Trong ứng dụng Java chạy từ dòng lệnh, `main` là điểm bắt đầu quen thuộc. Khi những phương thức phía dưới có thể ném checked exception, lập trình viên có thể tiếp tục khai báo `throws` dọc theo chuỗi lời gọi:

```text
Java launcher
    |
    v
main(String[] args) throws IOException
    |
    v
loadConfig() throws IOException
    |
    v
readFile() throws IOException
```

Điều này giúp **biên dịch** và truyền ngoại lệ ra khỏi `main`, nhưng tạo ra câu hỏi quan trọng: **Khi ngoại lệ đã đi ra khỏi `main`, ai xử lý nó?** Câu trả lời không phải “JVM sẽ tự phục hồi”. Ngoại lệ không được bắt có thể kết thúc luồng ban đầu, kích hoạt cơ chế thông báo lỗi không bắt và khiến ứng dụng dòng lệnh kết thúc không thành công. Cách JVM launcher trình bày lỗi và mã thoát cụ thể phụ thuộc môi trường thực thi.

Bài này giúp bạn:

- Hiểu cú pháp và tác dụng của `throws` ở phương thức `main`.
- Phân biệt **hợp đồng checked exception tại biên dịch** với **chính sách xử lý lỗi ở ranh giới ứng dụng**.
- Biết chuyện gì xảy ra khi một checked hoặc unchecked exception thoát khỏi `main`.
- Chọn hợp lý giữa `try/catch`, ném tiếp, dọn dẹp, báo thất bại và kết thúc tiến trình.
- Tránh giả định rằng lỗi ở một luồng worker hoặc tác vụ bất đồng bộ sẽ tự lan đến `main`.
- Nhận diện giới hạn của `throws Exception`, `throws Throwable` và cơ chế xử lý ở cấp cao nhất.

Các ví dụ dùng Java 17+; phần có ghi `javac`/`java` là chương trình độc lập chạy được.

## 2. Chữ ký `main` và mệnh đề `throws`

Với chương trình Java SE 17 thông thường, entry point phổ biến là:

```java
public static void main(String[] args) {
    // Nội dung chương trình.
}
```

Đây là **mảnh khai báo phương thức** để minh họa chữ ký; khi chạy thật, phải đặt trong một lớp Java hợp lệ.

Có thể thêm `throws` sau danh sách tham số:

```java
public static void main(String[] args)
        throws java.io.IOException {
    // Những lời gọi có thể truyền IOException ra ngoài.
}
```

Có thể khai báo **nhiều kiểu**:

```java
public static void main(String[] args)
        throws java.io.IOException, java.text.ParseException {
    // Có thể truyền một trong các nhóm checked exception này.
}
```

Và cú pháp `public static void main(String... args)` sử dụng varargs cũng tương ứng với tham số mảng `String[]` khi biên dịch.

Điều cần nhớ:

1. `throws` là **phần khai báo kiểu ngoại lệ**, không phải lệnh ném một đối tượng.
2. Câu lệnh `throw` hoặc thao tác/lời gọi thất bại mới thực sự phát sinh ngoại lệ.
3. `main throws IOException` không có nghĩa mọi lần chạy đều ném `IOException`.
4. `main throws IOException, ParseException` liệt kê **những khả năng khác nhau**, không yêu cầu cùng một lần lỗi ném hai đối tượng.
5. Các kiểu liệt kê phải phù hợp quy tắc ngôn ngữ và được ngăn cách bằng dấu phẩy, không dùng dấu `|` của multi-catch.
6. Không thể dựa vào mệnh đề `throws` để mong hệ điều hành, launcher hay người dùng tự cung cấp phương án phục hồi.

Trong Java 17, phương thức `main` chuẩn có kiểu trả về `void`. Không thể đổi thành `int main(...)` để trực tiếp trả mã trạng thái như một số ngôn ngữ khác.

### Tại sao `main` được khai báo checked exception?

Giả sử `main` gọi phương thức có hợp đồng `throws IOException`. Quy tắc checked exception của Java buộc bên gọi phải **bắt** hoặc **khai báo tiếp** ngoại lệ tương thích. `main` vẫn là phương thức Java thông thường trong việc kiểm tra ngoại lệ, nên lựa chọn `throws IOException` là hợp lệ.

Điểm đặc biệt là phía trên `main` thường không còn **một phương thức ứng dụng do bạn viết** có cơ hội bắt ngoại lệ. Việc khởi động lớp và gọi entry point thuộc cơ chế launcher/runtime. Vì vậy, đẩy checked exception tới `main` giải quyết nghĩa vụ biên dịch của mã nguồn, nhưng **không tự giải quyết cách phản hồi với người dùng hoặc hệ thống giám sát**.

## 3. Ví dụ chạy được: `main throws IOException`

Tạo tệp **`MainThrowsDemo.java`**:

```java
import java.io.IOException;

public class MainThrowsDemo {
    private static String loadConfig(boolean fail) throws IOException {
        if (fail) {
            throw new IOException("demo read failed");
        }
        return "ready";
    }

    public static void main(String[] args) throws IOException {
        boolean fail = args.length > 0 && args[0].equals("fail");
        System.out.println(loadConfig(fail));
        System.out.println("main completed");
    }
}
```

Biên dịch và chạy đường thành công:

```bash
javac MainThrowsDemo.java
java MainThrowsDemo
```

Kết quả `stdout` chính xác:

```text
ready
main completed
```

Trong đường này, `loadConfig(false)` trả về chuỗi `ready`; **không hề có ngoại lệ**, dù `loadConfig` và `main` đều khai báo `throws IOException`.

Thử đường lỗi:

```bash
java MainThrowsDemo fail
```

Luồng thực thi khi đó:

1. `loadConfig(true)` thực thi `throw new IOException("demo read failed")`.
2. `loadConfig` kết thúc đột ngột; ngoại lệ đi lên lời gọi trong `main`.
3. `main` không có `catch` phù hợp, nên không in được dòng `main completed`.
4. Ngoại lệ đi ra khỏi `main` và trở thành ngoại lệ không được ứng dụng bắt tại luồng ban đầu.

Trên launcher Java thông dụng, bạn sẽ thấy thông tin kiểu `java.io.IOException`, thông điệp `demo read failed` và stack trace trên `stderr`. Vị trí file, số dòng, văn bản bổ sung và mã thoát không nên được giả định cố định trong tài liệu hoặc unit test có mục tiêu chạy đa môi trường.

**Không nhầm lẫn:** `throws IOException` trên `main` không có nghĩa JVM gọi lại `loadConfig`, cấp phát thêm tài nguyên, tự tạo fallback hoặc đổi ngoại lệ thành thông báo nghiệp vụ. Nó chỉ cho phép đường checked exception này đi ra khỏi phương thức theo quy tắc Java.

## 4. Khai báo nhiều kiểu trên `main`

Khi một ứng dụng đọc đầu vào và phân tích định dạng, nó có thể phân biệt hai kiểu checked exception. Ví dụ tệp **`MainMultipleThrowsDemo.java`**:

```java
import java.io.IOException;
import java.text.ParseException;

public class MainMultipleThrowsDemo {
    private static int parse(String input)
            throws IOException, ParseException {
        if (input == null) {
            throw new IOException("input unavailable");
        }

        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException failure) {
            ParseException parsed =
                    new ParseException("not an integer", 0);
            parsed.initCause(failure);
            throw parsed;
        }
    }

    public static void main(String[] args)
            throws IOException, ParseException {
        System.out.println("value=" + parse("42"));
    }
}
```

```bash
javac MainMultipleThrowsDemo.java
java MainMultipleThrowsDemo
```

`stdout`:

```text
value=42
```

`main` hợp lệ khi khai báo nhiều kiểu `throws IOException, ParseException`. Nếu đầu vào không hợp lệ và ngoại lệ thực sự đi lên, đường gọi tương ứng sẽ kết thúc đột ngột. Việc giữ `NumberFormatException` làm `cause` trong `ParseException` giúp bảo toàn nguồn lỗi khi ứng dụng hoặc hệ thống giám sát đọc stack trace.

### `throws Exception` và `throws Throwable` có hợp lệ không?

Về mặt cú pháp, bạn có thể viết:

```java
public static void main(String[] args) throws Exception {
    // ...
}
```

Hoặc thậm chí `throws Throwable`. Tuy nhiên, đây là khai báo **rộng**:

- `throws Exception` bao phủ nhiều checked exception thuộc nhánh `Exception` và vẫn không bao phủ `Error` theo quan hệ kế thừa.
- `throws Throwable` bao phủ cả `Exception` và `Error` ở cấp kiểu, nhưng **không làm trạng thái JVM được phục hồi** nếu lỗi nghiêm trọng xuất hiện.
- Trong bài tập nhỏ hoặc công cụ thử nghiệm, `main throws Exception` là cách ngắn gọn để tập trung vào API đang học.
- Trong ứng dụng dòng lệnh cần thông báo lỗi rõ ràng, mã thoát có ý nghĩa, dọn dẹp tài nguyên và khả năng quan sát, chỉ thêm `throws Exception` thường không đủ.

Khai báo cụ thể như `IOException` và `ParseException` hữu ích cho tài liệu và bảo trì; còn **việc ứng dụng quyết định sẽ làm gì khi lỗi xảy ra** nên được thể hiện ở ranh giới thích hợp.

## 5. Khi ngoại lệ không được bắt đi ra khỏi `main`

Với một ngoại lệ thực sự thoát khỏi lời gọi `main` và không được ứng dụng bắt, quá trình xử lý tuân theo cơ chế ngoại lệ không được bắt của luồng. Về mặt hành vi thông thường:

```text
main gọi tác vụ
      |
      v
tác vụ ném Throwable
      |
      v
không có catch phù hợp trong chuỗi gọi
      |
      v
thoát khỏi main
      |
      v
cơ chế uncaught exception của luồng
      |
      v
luồng main kết thúc
```

Điều này cần hiểu chính xác:

- Một lỗi thoát khỏi `main` **không được tự động chuyển thành một giá trị trả về**, vì `main` trả về `void`.
- Trên môi trường launcher Java thông thường, nếu không thiết lập handler riêng, có thể quan sát stack trace trên `stderr`. Nội dung/định dạng của thông báo không phải hợp đồng để mã ứng dụng phụ thuộc tuyệt đối.
- Nếu các luồng non-daemon khác vẫn đang hoạt động, JVM có thể **vẫn tiếp tục chạy** sau khi luồng `main` kết thúc. “Luồng main đã kết thúc” và “tiến trình JVM đã kết thúc” là hai mệnh đề khác nhau.
- Nếu JVM/tiến trình kết thúc, hệ điều hành nhìn thấy một trạng thái hoàn tất tiến trình; quy ước `0` thành công và giá trị khác `0` báo thất bại thường được sử dụng trong CLI, nhưng đừng hứa một **mã lỗi cụ thể** cho mọi launcher/JVM khi một ngoại lệ không được bắt xuất hiện.
- `throws` không tự gửi phản hồi HTTP, tự rollback transaction, hủy tác vụ đang chạy hoặc thông báo lỗi theo định dạng mà người dùng mong đợi.

Trong mã thư viện, đừng cố dựa vào việc “ngoại lệ đã đến `main`” để thiết kế hành vi. Lập trình viên có thể gọi cùng thư viện từ unit test, ứng dụng GUI, dịch vụ, worker thread, scheduler hoặc entry point khác.

### `finally` và đóng tài nguyên

Khi stack bị tháo gỡ theo cơ chế ngoại lệ thông thường, các khối `finally` tương ứng sẽ được thực thi. `try-with-resources` cũng đóng tài nguyên theo quy tắc ngôn ngữ. Nhưng điều này **không bảo đảm** dọn dẹp xong nếu tiến trình/VM bị kết thúc cưỡng bức, gặp sự cố nghiêm trọng hoặc bị gọi `Runtime.halt(...)`.

Vì vậy, hãy đặt dọn dẹp ở đúng phạm vi tài nguyên và không coi `main throws ...` là cơ chế quản lý tài nguyên.

## 6. Bắt lỗi ở ranh giới `main` khi cần chính sách rõ ràng

Khi ứng dụng dòng lệnh cần chuyển lỗi kỹ thuật thành thông điệp dễ hiểu, `main` có thể là nơi xử lý lỗi cuối cùng của **luồng khởi chạy**:

```java
try {
    initialize();
    execute();
} catch (IOException failure) {
    System.err.println("Cannot start: " + failure.getMessage());
}
```

Đây là **đoạn mã minh họa**, cần khai báo `initialize()` và `execute()` trong ứng dụng thực. Mẫu này xử lý được lỗi I/O theo hợp đồng nhưng chưa nói gì về chính sách kết thúc và mã thoát; ứng dụng cần quyết định điều đó.

### Ví dụ chạy được: bắt lỗi rồi thực hiện dọn dẹp

Tạo tệp **`MainCatchDemo.java`**:

```java
import java.io.IOException;

public class MainCatchDemo {
    private static void initialize() throws IOException {
        throw new IOException("config missing");
    }

    public static void main(String[] args) {
        try {
            initialize();
            System.out.println("initialized");
        } catch (IOException failure) {
            System.out.println("startup failed: "
                    + failure.getMessage());
        } finally {
            System.out.println("startup cleanup");
        }

        System.out.println("main reached end");
    }
}
```

```bash
javac MainCatchDemo.java
java MainCatchDemo
```

Kết quả `stdout`:

```text
startup failed: config missing
startup cleanup
main reached end
```

Giải thích:

1. `initialize()` ném `IOException` và bỏ qua lệnh in `initialized`.
2. Handler của `main` chuyển thất bại thành thông tin đầu ra có chủ đích.
3. `finally` chạy để thực hiện thao tác dọn dẹp tương ứng; trong ví dụ là in một dòng.
4. Sau `try/catch/finally`, chương trình vẫn chạy tiếp và in `main reached end`.

Điểm cuối cùng đặc biệt quan trọng: **bắt rồi in một dòng chưa chắc là chính sách thất bại đúng**. Nếu cấu hình bắt buộc không được nạp, ứng dụng thực có thể cần dừng khởi động, báo trạng thái thất bại và tránh tiếp tục chạy như thể đã sẵn sàng.

### Không nuốt lỗi rồi báo thành công

Ví dụ sau dễ gây hiểu lầm đối với hệ thống gọi ứng dụng:

```java
try {
    startRequiredService();
} catch (Exception failure) {
    System.err.println(failure.getMessage());
}
System.out.println("ready"); // Có thể sai: dịch vụ chưa khởi động.
```

Đây là **đoạn mã sai về mặt thiết kế để minh họa**, giả định `startRequiredService()` có tồn tại. Nếu không thể chạy dịch vụ, không nên in `ready`, trả kết quả thành công hoặc tiếp tục các bước phụ thuộc vào khởi tạo đã thất bại.

Một phương thức phụ trợ có thể bắt ngoại lệ **để thêm ngữ cảnh rồi ném tiếp**, nhưng nên giữ `cause`:

```java
try {
    loadSettings();
} catch (IOException failure) {
    throw new IllegalStateException("Startup configuration failed",
            failure);
}
```

Đây là mảnh mã minh họa trong một phương thức, cần có `loadSettings()` khai báo checked exception. Việc bọc lỗi thành `IllegalStateException` giúp biểu đạt bối cảnh mới, đồng thời vẫn giữ lỗi gốc để truy vết. Không bọc lỗi nhiều tầng chỉ để đổi tên mà không thêm thông tin hữu ích.

## 7. Trả kết quả thành công/thất bại và mã thoát của CLI

`public static void main(String[] args)` trả về `void`, vì vậy một ứng dụng dòng lệnh thường tách logic thành một phương thức trả mã trạng thái:

```java
static int run(String[] args) {
    // 0 = thành công; giá trị khác 0 = thất bại theo quy ước của ứng dụng.
    return 0;
}
```

Đây là **mảnh minh họa**, chưa phải chương trình thực thi độc lập. Thiết kế hữu ích là cho `run` trả về trạng thái hoặc ném ngoại lệ theo hợp đồng, còn `main` là nơi quyết định cách kết thúc ứng dụng.

Ví dụ **`CliExitPolicyDemo.java`**, không cần thật sự gọi `System.exit` để học cách ánh xạ lỗi:

```java
import java.io.IOException;

public class CliExitPolicyDemo {
    private static void execute(boolean fail) throws IOException {
        if (fail) {
            throw new IOException("input unavailable");
        }
    }

    private static int run(String[] args) {
        boolean fail = args.length > 0 && args[0].equals("fail");
        try {
            execute(fail);
            return 0;
        } catch (IOException failure) {
            System.out.println("error: " + failure.getMessage());
            return 2;
        }
    }

    public static void main(String[] args) {
        int status = run(args);
        System.out.println("status=" + status);
    }
}
```

```bash
javac CliExitPolicyDemo.java
java CliExitPolicyDemo
java CliExitPolicyDemo fail
```

Hai lần chạy độc lập tạo `stdout` lần lượt:

```text
status=0
```

```text
error: input unavailable
status=2
```

Ở đây mã `2` chỉ là **giá trị trạng thái logic được in ra**, không phải mã thoát thực tế của tiến trình Java. Vì `main` kết thúc bình thường, không thể kết luận hệ điều hành đã nhận exit code `2`.

Trong ứng dụng CLI thật cần báo trạng thái ra hệ điều hành, người viết entry point có thể dùng `System.exit(status)` theo chính sách cụ thể. Tuy nhiên:

- `System.exit` yêu cầu JVM bắt đầu quá trình shutdown; nó ảnh hưởng **toàn bộ tiến trình** và mọi luồng, không chỉ một lời gọi.
- `System.exit` không phải thao tác nên gọi tùy tiện trong thư viện, method nghiệp vụ hoặc unit test.
- Việc gọi `System.exit` bên trong `try` không phải một cách tin cậy để ép `finally` cùng luồng hoàn thành như đường thoát bình thường.
- Không nên lấy một **exit code cố định** từ ví dụ ngoại lệ không bắt được để tạo quy tắc cho tất cả JVM. Hãy định nghĩa và kiểm tra mã thoát tại ranh giới CLI mình quản lý.
- Nếu ứng dụng được framework, IDE, test runner hoặc máy chủ quản lý, cần theo cơ chế lifecycle của môi trường đó; không mặc nhiên gọi `System.exit`.

## 8. `main throws InterruptedException` và vấn đề ngắt luồng

`main` cũng có thể khai báo:

```java
public static void main(String[] args)
        throws InterruptedException {
    Thread.sleep(100);
}
```

Đây là mảnh phương thức hợp lệ trong lớp Java. `Thread.sleep` khai báo `InterruptedException`; `throws InterruptedException` cho phép thông báo ngắt đi ra khỏi `main` mà không phải viết `catch`.

Nhưng trong ứng dụng thật, yêu cầu ngắt có thể là tín hiệu hủy công việc/đóng ứng dụng. Nếu bạn bắt `InterruptedException` mà chưa xử lý trọn việc hủy, thường cần đặt lại cờ interrupt bằng `Thread.currentThread().interrupt()`, rồi kết thúc công việc theo hợp đồng.

Ví dụ mảnh mã:

```java
try {
    Thread.sleep(100);
} catch (InterruptedException failure) {
    Thread.currentThread().interrupt();
    return;
}
```

Đây là đoạn dành cho **phương thức trả về `void`** (chẳng hạn `main`). Không phải mọi handler đều phải đặt lại cờ: nếu chủ động truyền tiếp chính `InterruptedException` ra ngoài bằng `throws`, điều đó đã giữ tín hiệu bằng kiểu ngoại lệ. Chính sách còn phụ thuộc framework và cấu trúc hủy tác vụ.

## 9. Giới hạn quan trọng: ngoại lệ của luồng khác không tự bay lên `main`

Một quan niệm sai phổ biến: “`main throws Exception` sẽ nhận mọi ngoại lệ của chương trình”.

Điều này không đúng. Ngoại lệ lan truyền **theo stack của luồng đang thực thi**. Khi một luồng worker có stack riêng ném ngoại lệ không được bắt, ngoại lệ đó không tự di chuyển ngược sang stack của luồng `main`.

Tạo tệp **`WorkerFailureDemo.java`**:

```java
public class WorkerFailureDemo {
    public static void main(String[] args)
            throws InterruptedException {
        Thread worker = new Thread(
                () -> { throw new IllegalStateException("child failed"); },
                "worker"
        );

        worker.setUncaughtExceptionHandler((thread, failure) ->
                System.out.println("worker error: "
                        + failure.getMessage()));

        worker.start();
        worker.join();
        System.out.println("main completed");
    }
}
```

```bash
javac WorkerFailureDemo.java
java WorkerFailureDemo
```

`stdout`:

```text
worker error: child failed
main completed
```

`Thread.join()` chỉ chờ worker kết thúc; bản thân nó **không ném lại `IllegalStateException` của worker cho `main`**. Ví dụ cài `UncaughtExceptionHandler` trên worker để kiểm soát phần báo lỗi; nhờ có `join()`, thứ tự hai dòng in xác định.

Nếu không có handler cục bộ, cơ chế xử lý ngoại lệ không được bắt của luồng sẽ được áp dụng, thường in stack trace. Điều này không làm `main throws ...` trở thành “handler toàn cục”.

### `setDefaultUncaughtExceptionHandler` có thay thế `try/catch` không?

Không. Handler mặc định được gọi trong tình huống ngoại lệ sắp kết thúc một luồng mà không được bắt. Nó phù hợp để ghi nhận lỗi cuối cùng, nhưng không thể quay lại điểm ném để tiếp tục phần thân phương thức đã thất bại. Nếu bản thân handler cần cấp phát, ghi file hoặc gửi thông báo, những thao tác này cũng có thể thất bại khi môi trường đang gặp sự cố tài nguyên.

Trong sản phẩm thật, giữ nội dung handler tối thiểu, tránh coi handler là cơ chế đảm bảo phục hồi.

## 10. Giới hạn khác: tác vụ qua `ExecutorService` và `Future`

Khác với việc tạo `Thread` trực tiếp, một tác vụ được gửi qua `ExecutorService.submit(...)` thường được bọc để ngoại lệ nằm trong trạng thái hoàn tất của `Future`. Bên gọi có thể quan sát lỗi khi gọi `Future.get()`; lỗi của tác vụ xuất hiện dưới dạng **`ExecutionException`** với nguyên nhân thật ở `getCause()`.

Tạo tệp **`FutureFailureDemo.java`**:

```java
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FutureFailureDemo {
    public static void main(String[] args)
            throws InterruptedException {
        ExecutorService pool = Executors.newSingleThreadExecutor();

        try {
            Callable<String> task = () -> {
                throw new IOException("worker IO failed");
            };

            Future<String> future = pool.submit(task);
            try {
                System.out.println(future.get());
            } catch (ExecutionException failure) {
                System.out.println("task cause: "
                        + failure.getCause().getClass().getSimpleName());
            }
        } finally {
            pool.shutdown();
        }

        System.out.println("main completed");
    }
}
```

```bash
javac FutureFailureDemo.java
java FutureFailureDemo
```

Kết quả:

```text
task cause: IOException
main completed
```

Giải thích:

- `Callable<String>` cho phép tác vụ ném checked exception; ở đây là `IOException`.
- `submit` trả về một `Future<String>`. Lỗi do tác vụ ném không tự động trở thành `IOException` tại dòng `submit` trên luồng `main`.
- `future.get()` báo `ExecutionException` khi tác vụ kết thúc thất bại. `getCause()` chỉ đến `IOException` ban đầu.
- `main` khai báo `throws InterruptedException` cho việc chờ có thể bị ngắt; `ExecutionException` được bắt và xử lý riêng.
- `shutdown()` ngừng nhận thêm tác vụ mới; đây không phải lệnh ép dừng tức thì các tác vụ đang chạy. Trong ví dụ, `future.get()` đã chờ tác vụ cụ thể hoàn tất, nên output xác định.

Muốn đẩy lỗi đến ranh giới ứng dụng, bạn phải **chủ động thu thập và xử lý kết quả Future** hoặc dùng cơ chế phối hợp tác vụ phù hợp. Với `CompletableFuture`, cơ chế báo lỗi khác về wrapper và API (chẳng hạn `CompletionException` hoặc `ExecutionException` tùy cách chờ), nhưng nguyên tắc vẫn là: **ngoại lệ không tự nhảy qua ranh giới luồng**.

## 11. Những lỗi nằm ngoài khả năng xử lý bằng `main throws`

### 11.1. Lỗi trước khi vào thân `main`

Một số lỗi có thể xuất hiện trong quá trình nạp lớp, liên kết hoặc khởi tạo lớp bắt buộc trước lúc JVM thực thi những dòng đầu tiên của `main`. Chẳng hạn, lỗi khởi tạo tĩnh có thể biểu hiện thành `ExceptionInInitializerError`. Vì thân `main` còn chưa bắt đầu, một `try/catch` đặt **bên trong thân `main`** không thể bao quanh và bắt được tất cả lỗi của toàn bộ giai đoạn khởi động bên ngoài nó.

Không nên hứa rằng `main throws Throwable` sẽ thu nhận mọi lỗi khởi động của JVM hoặc launcher.

### 11.2. Lỗi nghiêm trọng thuộc nhánh `Error`

`OutOfMemoryError` hoặc `StackOverflowError` là `Error`, không phải `Exception`. `catch (Exception e)` không bắt các đối tượng này; `throws Exception` cũng không phải cơ chế quản lý chúng.

Ngay cả khi có thể bắt một số `Error` về mặt kiểu, tiếp tục chạy sau lỗi nghiêm trọng có thể nguy hiểm do thiếu tài nguyên hoặc trạng thái hỏng. Không tự tạo lỗi thiếu bộ nhớ hoặc tràn stack để kiểm thử entry point trên ứng dụng thật.

### 11.3. Mất tiến trình và shutdown cưỡng bức

Tín hiệu hệ điều hành, lỗi native, mất điện, `Runtime.halt(...)` hay các sự kiện dừng cưỡng bức có thể ngăn chương trình thực thi trọn đường xử lý ngoại lệ, `finally` và shutdown hook. `throws` không cung cấp bảo đảm “luôn có một handler cuối cùng chạy được”.

### 11.4. Lỗi đã được framework hoặc task runner hấp thụ

Trong một ứng dụng có scheduler, event loop, message consumer hoặc hệ quản lý worker riêng, lỗi có thể được framework bắt, lưu, retry hoặc chuyển đến callback. Không thể suy luận rằng mọi lỗi đều đến `main` chỉ vì `main` là điểm khởi động chương trình.

Mô tả và thử nghiệm chính sách lỗi cần xét **ranh giới thực thi thật**: thread, task, Future, callback, service container hoặc request handler.

## 12. Chọn chiến lược xử lý ở tầng nào?

Không phải lỗi nào cũng nên xử lý tại `main`; cũng không nên ném mọi thứ lên trên cùng.

| Loại sự cố | Tầng thường có đủ ngữ cảnh | Hướng xử lý |
| --- | --- | --- |
| Dữ liệu đầu vào CLI sai | Biên nhận tham số | Báo cách dùng/giá trị sai; mã trạng thái ứng dụng có chủ đích |
| Tệp cấu hình bắt buộc không mở được | Khởi động ứng dụng | Thông báo nguồn lỗi, dừng khởi động, tránh báo “ready” |
| Một bản ghi dữ liệu không hợp lệ nhưng có thể bỏ qua | Tầng xử lý bản ghi | Ghi nhận và tiếp tục nếu hợp đồng cho phép |
| Lỗi hệ thống tạm thời có thể retry an toàn | Tầng I/O có biết idempotency và giới hạn retry | Retry có giới hạn hoặc truyền lỗi lên ranh giới điều phối |
| Lỗi lập trình `NullPointerException` | Nơi tạo trạng thái/tham chiếu sai | Sửa nguyên nhân; không dùng `catch` rộng để che giấu bug |
| Lỗi trong một worker bất đồng bộ | Biên kết quả của task/worker | Quan sát `Future`/callback/handler, quyết định hủy hoặc báo thất bại |
| `OutOfMemoryError`/lỗi runtime nghiêm trọng | Vận hành và quản lý tiến trình | Thu thập bằng chứng nếu khả thi, cách ly và khắc phục nguyên nhân |

Một cách tổ chức hữu ích cho CLI là:

```text
main                -> nhận args, chính sách thông báo lỗi/mã thoát
  |
  +-- application    -> điều phối, quy tắc nghiệp vụ
         |
         +-- IO/API   -> khai báo checked exception theo hợp đồng
```

Lớp dưới ném kiểu ngoại lệ có ý nghĩa. Lớp trên xử lý ở nơi **có thể quyết định hành động đúng**. `main` nên tập trung vào chính sách ranh giới tiến trình; không đặt logic nghiệp vụ và quá nhiều `catch` trùng lặp trong đó.

### Khi nào có thể để `main throws Exception`?

- Ví dụ học tập, chương trình thử nhanh, công cụ nội bộ nhỏ mà stack trace trực tiếp là đủ.
- Khi không có nhu cầu định dạng lỗi, cleanup riêng, mã thoát được quy ước, hay trạng thái phục vụ dài hạn.
- Khi hành vi thất bại mặc định của launcher được chấp nhận và đã được kiểm tra trong môi trường chạy.

### Khi nào nên có handler rõ ràng ở `main`?

- CLI được gọi từ shell script/CI và cần phân loại lỗi ổn định.
- Ứng dụng chạy dài hạn cần quyết định startup đã thành công hay không.
- Ứng dụng có tài nguyên cần đóng theo vòng đời rõ ràng.
- Hệ thống cần log có ngữ cảnh, không in stack trace nhạy cảm cho người dùng cuối.
- Ứng dụng phối hợp nhiều worker và phải đánh dấu job thất bại nếu một task bắt buộc thất bại.

Trong mọi trường hợp, handler cấp cao chỉ có thể quản lý **những ngoại lệ thực sự đi đến nó** và phải xem xét trạng thái tiến trình trước khi quyết định tiếp tục.

## 13. Lỗi hiểu nhầm thường gặp

**“`throws Exception` ở `main` nghĩa là đã xử lý mọi lỗi.”** Sai. Nó chỉ công bố các kiểu checked exception ở mức chữ ký; không tạo handler, phục hồi hay kết quả lỗi có cấu trúc.

**“`throws IOException` bắt JVM phải in IOException nếu lỗi phát sinh.”** Không chính xác. Một handler bên trong, một lớp bọc hoặc runtime khác có thể thay đổi cách báo lỗi. `throws` không kiểm soát định dạng output.

**“Vì `main` là điểm vào, mọi worker exception sẽ tự đi đến nó.”** Sai. Mỗi thread có call stack riêng; `Future` hoặc cơ chế trao đổi lỗi phải được xử lý rõ ràng.

**“`Thread.join()` ném lại lỗi mà worker đã ném.”** Sai. `join()` chờ kết thúc, không phải kênh chuyển ngoại lệ worker. Nó có thể ném `InterruptedException` nếu luồng đang chờ bị ngắt.

**“`System.out.println(status)` làm hệ điều hành nhận đúng exit code đó.”** Sai. In chuỗi và kết thúc tiến trình là hai việc khác nhau.

**“Một `catch (Exception)` ở `main` bao phủ `Error` và lỗi trước khi `main` chạy.”** Sai. `Error` ở nhánh khác, còn lỗi phát sinh trước khi thân `main` bắt đầu thì `catch` bên trong thân không bao quanh được.

**“Có `finally` thì dọn dẹp luôn chạy dù process bị kill.”** Sai. Bảo đảm trong ngữ nghĩa Java có điều kiện về việc VM còn thực thi được đường thoát đó.

**“Nếu lỗi đã bị bắt ở `main` thì tiến trình mặc nhiên được đánh dấu thất bại.”** Sai. Bắt lỗi và tiếp tục kết thúc bình thường có thể được xem là thành công bởi môi trường chạy. Chính sách mã thoát cần được lập trình riêng khi cần.

**“Khai báo càng rộng càng an toàn.”** Sai. `throws Throwable` thường làm mất ý nghĩa API và không cải thiện khả năng phục hồi.

## 14. Câu hỏi tự kiểm tra

1. **`public static void main(String[] args) throws IOException` có hợp lệ với Java 17 không?** Có.
2. **`throws IOException` trong `main` có bảo đảm chương trình luôn ném lỗi?** Không, nó mô tả khả năng phát sinh.
3. **Nếu lời gọi ném checked exception không được bắt trong `main`, trình biên dịch yêu cầu gì?** `main` phải khai báo kiểu phù hợp trong `throws` hoặc bắt nó.
4. **Khi checked exception không được bắt thoát khỏi `main`, JVM có tự retry không?** Không có bảo đảm hay quy tắc retry tự động từ `throws`.
5. **Một worker ném `IllegalStateException`; `main throws Exception` có bắt được lỗi đó không?** Không, vì lỗi không tự lan giữa các thread.
6. **`Future.get()` thường báo lỗi tác vụ bằng loại nào?** `ExecutionException` với nguyên nhân ở `getCause()`.
7. **Mã `return 2;` có hợp lệ trực tiếp trong `public static void main` chuẩn không?** Không, vì `main` trả về `void`.
8. **Bắt lỗi tại `main` rồi in `ready` có thể gây vấn đề gì?** Ứng dụng báo trạng thái thành công dù khởi tạo thực tế thất bại.
9. **Nếu lớp khởi tạo lỗi trước khi vào thân `main`, handler đặt trong `main` có thể bắt nó không?** Không bắt được lỗi xảy ra trước khi handler tồn tại trên đường thực thi.
10. **`main throws Throwable` có khiến JVM luôn chạy `finally` khi mất điện không?** Không.
11. **Khi nào nên để ngoại lệ đi ra khỏi `main`?** Khi chấp nhận cơ chế báo lỗi mặc định của môi trường chạy và không cần một chính sách xử lý/lifecycle khác.
12. **Khi nào nên xử lý tại ranh giới `main`?** Khi cần thông báo, dọn dẹp, mã trạng thái hoặc quyết định dừng/tiếp tục có ngữ nghĩa ứng dụng rõ ràng.

## 15. Tài liệu tham khảo

- [Java Language Specification 17 — §8.4.6 Method Throws](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.6): ý nghĩa mệnh đề `throws`.
- [Java Language Specification 17 — §11.2 Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2): quy tắc checked exception.
- [Java Language Specification 17 — §11.3 Run-Time Handling of an Exception](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.3): xử lý và lan truyền ngoại lệ.
- [Java SE 17 API — Thread.UncaughtExceptionHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.UncaughtExceptionHandler.html): xử lý ngoại lệ không được bắt ở ranh giới luồng.
- [Java SE 17 API — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html) và [ExecutionException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutionException.html): nhận thất bại của tác vụ bất đồng bộ.
- [Java SE 17 API — System.exit](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#exit(int)): yêu cầu kết thúc JVM theo trạng thái.
- [Java SE 17 API — Thread.join](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html#join()): chờ một luồng kết thúc.

**Ghi nhớ:** `throws` trên `main` là hợp đồng ngoại lệ ở mức ngôn ngữ, cho phép checked exception truyền ra biên chương trình. Muốn phản hồi đúng khi thất bại, ứng dụng phải định nghĩa rõ **handler, cách dọn dẹp, ranh giới luồng/tác vụ và chính sách kết thúc**; những điều đó không được sinh ra chỉ bằng cách thêm `throws Exception`.


</details>

- [Quay lại đầu trang](#back-to-top)
