<a id="back-to-top"></a>

# Đối tượng Throwable

## Menu
- [Các constructor với message, cause và cấu hình nâng cao](#throwable-internals-001)
- [getMessage, getLocalizedMessage và toString](#throwable-internals-002)
- [Stack trace được ghi nhận tại thời điểm nào](#throwable-internals-003)
- [printStackTrace và cách biểu diễn đầy đủ một ngoại lệ](#throwable-internals-004)
- [getStackTrace và cấu trúc StackTraceElement](#throwable-internals-005)
- [fillInStackTrace và tác động khi cập nhật dấu vết](#throwable-internals-006)
- [setStackTrace và giới hạn khi tự thay đổi thông tin chẩn đoán](#throwable-internals-007)
- [getCause, initCause và điều kiện khởi tạo nguyên nhân](#throwable-internals-008)
- [addSuppressed và getSuppressed](#throwable-internals-009)
- [Cấu hình enableSuppression và writableStackTrace](#throwable-internals-010)

## <a id="throwable-internals-001">Các constructor với message, cause và cấu hình nâng cao</a>

<details>
<summary>Xem chi tiết</summary>

# Các constructor với message, cause và cấu hình nâng cao của `Throwable` trong Java

`java.lang.Throwable` là lớp gốc của các đối tượng có thể được ném và bắt trong cơ chế ngoại lệ Java. Khi tạo một ngoại lệ, **constructor** quyết định những thông tin chẩn đoán ban đầu được lưu vào đối tượng: **message** (thông điệp), **cause** (nguyên nhân), khả năng lưu **suppressed exceptions** (ngoại lệ phụ bị giữ lại) và khả năng ghi **stack trace** (dấu vết ngăn xếp lời gọi).

Ví dụ:

~~~java
throw new IllegalStateException(
        "Không thể khởi tạo dịch vụ",
        new java.io.IOException("Thiếu cấu hình"));
~~~

Lệnh trên thực hiện hai việc: constructor của `IllegalStateException` tạo đối tượng với message và cause; lệnh `throw` mới **ném** đối tượng khiến luồng điều khiển Java bắt đầu tìm handler phù hợp. Constructor **không tự ném** một ngoại lệ chỉ vì đối tượng được tạo thuộc loại `Throwable`.

Bài học tập trung vào năm dạng constructor của `Throwable`, ý nghĩa của từng tham số, cách lớp con sử dụng `super(...)`, ảnh hưởng tới `getMessage()`, `getCause()`, `getSuppressed()` và `getStackTrace()`, các tình huống thực tế khi bọc lỗi, và những rủi ro khi tắt suppression/stack trace.

## 1. Vị trí của `Throwable` trong hệ thống ngoại lệ

Phân cấp rút gọn:

~~~text
java.lang.Object
└── java.lang.Throwable
    ├── java.lang.Error
    │   └── java.lang.OutOfMemoryError
    └── java.lang.Exception
        ├── java.io.IOException
        └── java.lang.RuntimeException
            ├── java.lang.IllegalArgumentException
            └── java.lang.IllegalStateException
~~~

Tất cả đối tượng `Exception` và `Error` đều là `Throwable`. Tuy nhiên, chỉ vì mọi lớp này kế thừa `Throwable` **không có nghĩa chúng đều cung cấp cùng bộ constructor**.

**Constructor không được kế thừa trong Java.** Nếu một lớp con cần các overload tương ứng với message/cause, lớp đó phải khai báo constructor và gọi `super(...)` phù hợp. Một số lớp chuẩn chỉ cung cấp những overload riêng của chúng.

Một vài khác biệt:

- `Throwable` là nền tảng cho việc ném đối tượng, ghi nhận nguyên nhân và dấu vết lỗi.
- `Exception` thường đại diện các tình huống lỗi mà mã ứng dụng có thể cân nhắc xử lý.
- `RuntimeException` là nhánh **unchecked exception**.
- `Error` cũng thuộc loại unchecked, thường báo vấn đề nghiêm trọng mà mã nghiệp vụ không nên bắt đại trà để tiếp tục.
- Các lớp `Throwable` khác ngoài `RuntimeException` và `Error` cùng các lớp con của chúng chịu quy tắc checked exception của Java.

Việc truyền message hoặc cause vào constructor **không quyết định** đối tượng là checked hay unchecked: điều đó phụ thuộc **quan hệ kế thừa**.

## 2. Năm constructor của `Throwable`

Ở Java 17+, `java.lang.Throwable` cung cấp các dạng constructor sau:

~~~java
public Throwable()
public Throwable(String message)
public Throwable(String message, Throwable cause)
public Throwable(Throwable cause)
protected Throwable(
        String message,
        Throwable cause,
        boolean enableSuppression,
        boolean writableStackTrace)
~~~

Đây là **các chữ ký API minh họa**, không phải một tệp khai báo lại lớp `Throwable`. Constructor cuối cùng là `protected`: mã ứng dụng thông thường không thể gọi trực tiếp `new Throwable(message, cause, true, true)` từ một lớp không có quyền truy cập thích hợp. Nó dành cho **lớp con** gọi qua `super(...)` theo quy tắc truy cập của Java.

| Dạng | Message ban đầu | Cause ban đầu | Suppression | Stack trace |
| --- | --- | --- | --- | --- |
| `Throwable()` | `null` | chưa khởi tạo cause | bật mặc định | ghi mặc định |
| `Throwable(String message)` | chuỗi truyền vào | chưa khởi tạo cause | bật mặc định | ghi mặc định |
| `Throwable(Throwable cause)` | thường là `cause.toString()`, hoặc `null` nếu cause null | cause đã khởi tạo | bật mặc định | ghi mặc định |
| `Throwable(String message, Throwable cause)` | chuỗi truyền vào | cause đã khởi tạo | bật mặc định | ghi mặc định |
| `Throwable(String message, Throwable cause, boolean, boolean)` | chuỗi truyền vào | cause đã khởi tạo | chọn bật/tắt | chọn ghi/không ghi |

**“Cause chưa khởi tạo” khác “cause đã khởi tạo bằng `null`”.** Dù `getCause()` có thể trả `null` trong cả hai tình huống, khả năng gọi `initCause(...)` về sau **khác nhau**; đây là một quy tắc quan trọng ở phần 7.

Các constructor thông thường mặc định bật cả lưu suppressed exceptions và writable stack trace. Constructor bốn tham số cho phép thay đổi hai hành vi ấy lúc khởi tạo và sự lựa chọn có ảnh hưởng lâu dài tới chẩn đoán lỗi.

## 3. Constructor không tham số: `Throwable()`

~~~java
Throwable issue = new Throwable();
System.out.println(issue.getMessage()); // null
System.out.println(issue.getCause());   // null
~~~

Hai dòng in ở ví dụ này đều là `null`. Đối tượng vẫn là một `Throwable` hợp lệ, có thể được ném nếu phương thức đáp ứng quy tắc checked exception, và thông thường có stack trace được ghi nhận khi tạo.

Dạng này thích hợp khi kiểu lớp đã đủ biểu đạt vấn đề hoặc khi lớp con chủ động cung cấp dữ liệu qua cơ chế khác. Tuy nhiên, trong mã ứng dụng, một thông điệp rõ ràng thường giúp đọc log và điều tra tốt hơn.

**Lưu ý:** `new Throwable()` đơn thuần **không** chuyển điều khiển sang `catch`. Phải có `throw issue;` hoặc một lệnh ném đối tượng tương ứng mới có ngoại lệ runtime được lan truyền.

## 4. Constructor nhận message: `Throwable(String message)`

~~~java
IllegalArgumentException issue =
        new IllegalArgumentException("age không được âm");

System.out.println(issue.getMessage());
// age không được âm
~~~

Message nên giúp người đọc hiểu điều kiện thất bại mà không cần đoán ngữ cảnh. Ví dụ:

- `"age phải nằm trong khoảng 0..150"` hữu ích hơn `"Sai"`.
- `"Không tải được cấu hình ứng dụng"` hữu ích hơn `"Có exception"`.
- Thông điệp cho hệ thống nội bộ nên tránh ghi token, mật khẩu, khóa API hoặc dữ liệu cá nhân nhạy cảm.

Không nên điều khiển logic bằng cách so sánh nội dung `getMessage()`. Message có thể đổi theo phiên bản, cách bản địa hóa hoặc chi tiết triển khai. Nếu ứng dụng cần mã lỗi ổn định, hãy định nghĩa kiểu ngoại lệ hoặc trường mã lỗi riêng.

### Message `null` và chuỗi rỗng

~~~java
Throwable a = new Throwable((String) null);
Throwable b = new Throwable("");

System.out.println(a.getMessage() == null);  // true
System.out.println(b.getMessage().isEmpty()); // true
~~~

`null` và `""` là hai giá trị khác nhau. Khi tạo đối tượng mà không đưa thông điệp, `getMessage()` thường là `null`. Một số cách hiển thị ngoại lệ có thể biểu diễn chúng khác nhau.

Constructor `Throwable(String)` **không tự gán cause**. Với một đối tượng được tạo bằng dạng này, có thể thiết lập cause sau đó bằng `initCause(...)` một lần, nếu cần và hợp lệ.

## 5. Constructor nhận cause: `Throwable(Throwable cause)`

~~~java
java.io.IOException root =
        new java.io.IOException("Không đọc được tệp");

Throwable wrapper = new Throwable(root);
System.out.println(wrapper.getCause() == root); // true
~~~

Constructor này đặt **cause** thành chính đối tượng `root`. Message của `wrapper` thường lấy từ `cause.toString()`; với cause là `null`, message là `null`.

Ví dụ riêng để thấy giá trị message:

~~~java
IllegalArgumentException root =
        new IllegalArgumentException("bad input");
Throwable outer = new Throwable(root);

System.out.println(outer.getMessage());
// java.lang.IllegalArgumentException: bad input
~~~

Chú ý: `outer.getMessage()` **không đơn giản bằng `root.getMessage()`**. Với constructor nhận duy nhất cause, nó dùng biểu diễn `toString()` của cause, thường có **tên lớp + message**.

Nếu API ở tầng cao cần một message mô tả thao tác thất bại, constructor nhận **cả message và cause** thường rõ nghĩa hơn.

### Truyền `null` vào constructor cause-only

~~~java
Throwable x = new Throwable((Throwable) null);
System.out.println(x.getMessage()); // null
System.out.println(x.getCause());   // null
~~~

Dù getter trả `null`, constructor nhận cause đã đánh dấu trạng thái cause là **được khởi tạo**. Gọi `x.initCause(...)` về sau không được phép, khác với đối tượng tạo bằng `new Throwable()` hoặc `new Throwable("message")`.

Ép kiểu `(Throwable) null` ở ví dụ giúp biểu đạt rõ ta muốn constructor nhận cause. Với lớp có nhiều overload nhận các kiểu tham chiếu không liên quan, truyền `null` không ép kiểu có thể dẫn tới lỗi overload không rõ ràng.

## 6. Constructor nhận message và cause: `Throwable(String, Throwable)`

Đây là dạng đặc biệt hữu ích khi **chuyển đổi ngoại lệ giữa các tầng ứng dụng**.

~~~java
static String loadConfig(java.nio.file.Path path) {
    try {
        return java.nio.file.Files.readString(path);
    } catch (java.io.IOException cause) {
        throw new IllegalStateException(
                "Không thể nạp cấu hình bắt buộc", cause);
    }
}
~~~

Đây là phương thức Java minh họa có thể đặt trong một lớp. `Files.readString` có thể ném `IOException`. Nếu điều đó xảy ra:

1. `catch` nhận đối tượng `IOException` gốc.
2. Constructor của `IllegalStateException` nhận message cho tầng hiện tại và cause gốc.
3. `throw` ném đối tượng mới, thuộc loại `IllegalStateException`.
4. `getMessage()` của đối tượng mới cho biết thao tác nào thất bại.
5. `getCause()` trỏ tới chính `IOException` đã được bắt.

Ưu điểm: giữ **ngữ cảnh nghiệp vụ** và **nguồn gốc kỹ thuật** trong hai trường riêng, thay vì gộp mọi thứ vào một chuỗi.

### Đừng biến cause thành một phần của message rồi bỏ tham chiếu

Cách chưa tốt:

~~~java
throw new IllegalStateException(
        "Không tải được: " + cause.getMessage());
~~~

Đây là đoạn minh họa trong phạm vi có biến `cause`. Chuỗi mô tả có thể vẫn hữu ích, nhưng đối tượng mới **không giữ liên kết `getCause()`** chỉ vì văn bản chứa message của cause.

Cách nên cân nhắc:

~~~java
throw new IllegalStateException(
        "Không tải được dữ liệu bắt buộc", cause);
~~~

Không cần chuyển mọi ngoại lệ sang `IllegalStateException`; loại ngoại lệ tầng cao phải phù hợp hợp đồng API. Điểm cốt lõi là **truyền cause** khi có lý do bọc lỗi.

### Truyền cause `null` vẫn được xem là đã khởi tạo cause

~~~java
Throwable problem = new Throwable("No cause", null);
System.out.println(problem.getCause()); // null
~~~

Ở đây `getCause()` trả `null`, nhưng `initCause(...)` **không thể dùng lần nữa** cho đối tượng này. Thiết kế constructor `(String, Throwable)` thể hiện rằng quan hệ cause đã được quyết định lúc khởi tạo, ngay cả khi quyết định là “không có cause”.

## 7. `initCause`: bổ sung nguyên nhân khi constructor chưa nhận cause

`Throwable.initCause(root)` cho phép gắn **một lần** nguyên nhân cho đối tượng tạo bằng constructor không tham số hoặc chỉ nhận message. Nếu constructor đã nhận cause, **kể cả `null`**, trạng thái cause đã được khởi tạo và gọi `initCause` tiếp sẽ ném `IllegalStateException`.

~~~java
Throwable error = new Throwable("Không thể nạp cấu hình");
error.initCause(new java.io.IOException("Tệp bị thiếu"));
System.out.println(error.getCause().getClass().getSimpleName());
// IOException
~~~

`getCause() == null` **không** chứng minh `initCause` còn được phép; `initCause(null)` cũng tiêu thụ cơ hội khởi tạo. Không được tự đặt đối tượng làm cause của chính nó (`IllegalArgumentException`). Với API mới, hãy ưu tiên constructor nhận `(String, Throwable)`; quy tắc trạng thái và chuỗi cause được phân tích chuyên sâu trong bài về `getCause`/`initCause`.

## 8. Constructor nâng cao: bốn tham số của `Throwable`

Dạng nâng cao có chữ ký:

~~~java
protected Throwable(
        String message,
        Throwable cause,
        boolean enableSuppression,
        boolean writableStackTrace)
~~~

Hai tham số cuối là:

- `enableSuppression`: có cho phép lưu những ngoại lệ được gắn vào đối tượng bởi `addSuppressed(...)` hay không.
- `writableStackTrace`: có cho phép ghi dấu vết stack trace cho chính đối tượng `Throwable` hay không.

Đây là cấu hình tại thời điểm khởi tạo; bạn không có một setter công khai tương đương để bật/tắt hai tính năng trên một đối tượng đã tạo.

Constructor này là `protected`. Cách dùng điển hình là **lớp ngoại lệ tùy biến** gọi `super(message, cause, enableSuppression, writableStackTrace)`:

~~~java
public class DomainProblem extends Exception {
    public DomainProblem(
            String message,
            Throwable cause,
            boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
~~~

Đây là một lớp hoàn chỉnh nếu lưu trong `DomainProblem.java`. Constructor bốn tham số `protected` của `Exception` cho phép lớp con chuyển tiếp cấu hình tới cơ chế khởi tạo của `Throwable`. Không cần và không nên sử dụng reflection để truy cập constructor `protected` từ mã thông thường.

### Điều gì không thay đổi?

Tắt suppression hoặc stack trace **không**:

- Làm đối tượng thôi là `Throwable`.
- Thay đổi phân loại checked/unchecked của lớp con.
- Thay đổi message hoặc cause đã truyền vào constructor.
- Ngăn `throw`/`catch` hoạt động với đối tượng.
- Tự động tắt logging trong ứng dụng hay ngăn bên gọi tự in một message.

Các cờ này thay đổi **dữ liệu chẩn đoán được ghi và lưu trên chính đối tượng**, không phải bản chất của cơ chế lan truyền ngoại lệ.

## 9. `enableSuppression`: khả năng lưu lỗi bổ sung

Trong `try-with-resources`, nếu thân khối ném A rồi `close()` ném B, Java thường giữ A làm **primary** và gọi cơ chế `addSuppressed(B)` trên A. Nếu **đối tượng A** được tạo với `enableSuppression=false`, danh sách `getSuppressed()` của A vẫn rỗng: lỗi B có thể đã xảy ra nhưng không được lưu trên A.

~~~java
DomainProblem noExtras = new DomainProblem(
        "work failed", null, false, true);
noExtras.addSuppressed(new java.io.IOException("close failed"));
System.out.println(noExtras.getSuppressed().length); // 0
~~~

Đoạn trên dùng lớp `DomainProblem` đã khai báo trong phần constructor nâng cao. **Tắt suppression không tắt cause hoặc stack trace.** Đây là đánh đổi mất chẩn đoán, không phải cách khắc phục lỗi tài nguyên. Cách thêm/đọc lỗi phụ, thứ tự đóng nhiều tài nguyên và các lỗi đối số thuộc bài chuyên sâu `addSuppressed`/`getSuppressed`; bốn tổ hợp cờ thuộc bài cấu hình `Throwable`.

## 10. `writableStackTrace`: khả năng ghi dấu vết của chính Throwable

Constructor thông thường ghi trace khi tạo đối tượng. Với `writableStackTrace=false`, đối tượng vẫn được `throw`/`catch`, có message/cause, nhưng `getStackTrace()` trả mảng rỗng; `fillInStackTrace()` không điền frame và `setStackTrace(...)` không khôi phục khả năng ghi.

~~~java
DomainProblem noFrames = new DomainProblem(
        "operation failed", null, true, false);
System.out.println(noFrames.getStackTrace().length); // 0
~~~

Tắt stack trace **chỉ áp dụng cho đối tượng này**: cause và suppressed exceptions khác vẫn có thể lưu trace của riêng chúng. Không nên dùng cấu hình này chỉ để giảm log dài hoặc tái sử dụng exception; nó làm mất vị trí sự cố. Các bài về thời điểm ghi trace, `fillInStackTrace`, `setStackTrace` và cấu hình hai cờ xử lý các trường hợp sâu hơn.

## 11. Ví dụ Java 17+ hoàn chỉnh: đủ constructor và hai cấu hình nâng cao

Tạo tệp **`ThrowableConstructorsDemo.java`**:

~~~java
import java.io.IOException;
import java.util.Arrays;

public class ThrowableConstructorsDemo {

    static final class Problem extends Exception {
        Problem() {
            super();
        }

        Problem(String message) {
            super(message);
        }

        Problem(Throwable cause) {
            super(cause);
        }

        Problem(String message, Throwable cause) {
            super(message, cause);
        }

        Problem(
                String message,
                Throwable cause,
                boolean enableSuppression,
                boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }

    static final class ClosingResource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close error");
        }
    }

    public static void main(String[] args) {
        System.out.println("== constructors ==");
        Problem empty = new Problem();
        Problem described = new Problem("validation failed");
        IllegalArgumentException root =
                new IllegalArgumentException("bad input");
        Problem causeOnly = new Problem(root);
        Problem wrapped = new Problem("cannot load", root);

        System.out.println("empty.message=" + empty.getMessage());
        System.out.println("message=" + described.getMessage());
        System.out.println(
                "causeOnly.message=" + causeOnly.getMessage());
        System.out.println(
                "causeOnly.causeSame=" + (causeOnly.getCause() == root));
        System.out.println("wrapped.message=" + wrapped.getMessage());
        System.out.println(
                "wrapped.causeType="
                + wrapped.getCause().getClass().getSimpleName());

        System.out.println("== deferred cause ==");
        Problem late = new Problem("later");
        late.initCause(root);
        System.out.println("late.causeSame=" + (late.getCause() == root));

        try {
            late.initCause(new IOException("again"));
        } catch (IllegalStateException e) {
            System.out.println("initCause repeated=IllegalStateException");
        }

        Problem explicitNull = new Problem("null cause", null);
        try {
            explicitNull.initCause(root);
        } catch (IllegalStateException e) {
            System.out.println(
                    "explicitNull replacement=IllegalStateException");
        }

        System.out.println("== advanced ==");
        Problem quiet = new Problem("quiet", root, false, false);
        quiet.addSuppressed(new IOException("ignored"));
        System.out.println("quiet.message=" + quiet.getMessage());
        System.out.println(
                "quiet.causeSame=" + (quiet.getCause() == root));
        System.out.println(
                "quiet.stackFrames=" + quiet.getStackTrace().length);
        System.out.println(
                "quiet.suppressed=" + quiet.getSuppressed().length);

        Problem full = new Problem("full", root, true, true);
        full.addSuppressed(new IOException("resource closed badly"));
        System.out.println(
                "full.stackPresent=" + (full.getStackTrace().length > 0));
        System.out.println(
                "full.suppressed=" + full.getSuppressed().length);
        System.out.println(
                "full.suppressedMessage="
                + full.getSuppressed()[0].getMessage());

        System.out.println("== stack timing ==");
        Problem created = createProblem();
        System.out.println(
                "frameAtCreation=" + containsCreateFrame(created));
        try {
            throw created;
        } catch (Problem e) {
            System.out.println(
                    "frameAfterThrow=" + containsCreateFrame(e));
        }

        System.out.println("== resources ==");
        resourceFailure(false);
        resourceFailure(true);
    }

    static Problem createProblem() {
        return new Problem("created");
    }

    static boolean containsCreateFrame(Throwable error) {
        return Arrays.stream(error.getStackTrace())
                .anyMatch(f -> f.getMethodName().equals("createProblem"));
    }

    static void resourceFailure(boolean enableSuppression) {
        try (ClosingResource ignored = new ClosingResource()) {
            throw new Problem(
                    "operation failed", null, enableSuppression, true);
        } catch (Exception e) {
            String prefix = enableSuppression
                    ? "suppressionOn"
                    : "suppressionOff";
            System.out.println(
                    prefix + ".primary=" + e.getMessage());
            System.out.println(
                    prefix + ".suppressed=" + e.getSuppressed().length);
            if (e.getSuppressed().length > 0) {
                System.out.println(
                        prefix + ".suppressedMessage="
                        + e.getSuppressed()[0].getMessage());
            }
        }
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac ThrowableConstructorsDemo.java
java ThrowableConstructorsDemo
~~~

**Stdout chính xác của chương trình:**

~~~text
== constructors ==
empty.message=null
message=validation failed
causeOnly.message=java.lang.IllegalArgumentException: bad input
causeOnly.causeSame=true
wrapped.message=cannot load
wrapped.causeType=IllegalArgumentException
== deferred cause ==
late.causeSame=true
initCause repeated=IllegalStateException
explicitNull replacement=IllegalStateException
== advanced ==
quiet.message=quiet
quiet.causeSame=true
quiet.stackFrames=0
quiet.suppressed=0
full.stackPresent=true
full.suppressed=1
full.suppressedMessage=resource closed badly
== stack timing ==
frameAtCreation=true
frameAfterThrow=true
== resources ==
suppressionOff.primary=operation failed
suppressionOff.suppressed=0
suppressionOn.primary=operation failed
suppressionOn.suppressed=1
suppressionOn.suppressedMessage=close error
~~~

### Giải thích theo nhóm đầu ra

**Nhóm constructors:** `Problem()` không có message. `Problem("validation failed")` có message. `Problem(root)` dùng `root.toString()` làm message và giữ chính `root` làm cause. `Problem("cannot load", root)` giữ message độc lập và tham chiếu cause.

**Nhóm deferred cause:** `Problem("later")` tạo đối tượng chưa khởi tạo cause, nên `initCause(root)` hợp lệ một lần. Gọi lại ném `IllegalStateException`. Ngược lại, `Problem("null cause", null)` đã khởi tạo cause dù giá trị là `null`, nên không được thay thế sau.

**Nhóm advanced:** `quiet` có suppression và writable stack trace đều bị tắt, nên `getSuppressed()` rỗng và `getStackTrace()` rỗng. Cause và message vẫn được giữ. `full` bật hai tính năng nên có stack trace và lưu ngoại lệ được thêm qua `addSuppressed`.

**Nhóm stack timing:** `createProblem()` tạo đối tượng và tên phương thức này có trong stack trace. Ném lại chính đối tượng không làm mất frame đó; hai phép kiểm tra đều trả `true`.

**Nhóm resources:** Trong cả hai lần, thân try-with-resources ném `Problem("operation failed", ...)` và `ClosingResource.close()` ném `IOException("close error")`. Với suppression bị tắt trên ngoại lệ chính, `getSuppressed()` rỗng. Với suppression được bật, lỗi đóng tài nguyên nằm trong danh sách suppressed. **Lỗi khi đóng không được biến thành cause tự động.**

## 12. Message, cause, suppressed và stack trace là bốn thành phần riêng

Một mô hình hữu ích:

~~~text
Throwable X
├── message: văn bản mô tả chính X
├── cause: Throwable Y hoặc null
│   └── Y có message, cause, suppressed, stack trace riêng
├── suppressed: [Throwable S1, Throwable S2, ...]
└── stack trace: các frame được ghi cho chính X
~~~

Không nên đồng nhất những thành phần này.

| Thông tin | API đọc | Mục đích |
| --- | --- | --- |
| Message | `getMessage()` | Giải thích lỗi ở mức người đọc |
| Message bản địa hóa | `getLocalizedMessage()` | Có thể được lớp con tùy biến; mặc định theo message |
| Cause | `getCause()` | Truy vết nguyên nhân của lỗi bọc |
| Suppressed | `getSuppressed()` | Giữ những lỗi bổ sung mà không thay lỗi chính |
| Stack trace | `getStackTrace()` | Vị trí và đường gọi đã ghi cho đối tượng |
| In dấu vết | `printStackTrace()` | Hiển thị lỗi chính cùng quan hệ cause/suppressed |

**Không phải mọi ngoại lệ có cause đều do một constructor bốn tham số.** Phần lớn ứng dụng chỉ cần constructor `(String, Throwable)` thông thường. Constructor nâng cao phục vụ những yêu cầu riêng về lưu giữ dữ liệu chẩn đoán.

## 13. Constructor của lớp con: vì sao phải gọi `super(...)`?

Ví dụ lớp ngoại lệ miền nghiệp vụ:

~~~java
public final class ConfigurationException extends Exception {
    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(
            String message, Throwable cause) {
        super(message, cause);
    }
}
~~~

Đây là một lớp độc lập nếu lưu vào `ConfigurationException.java`. Các overload của nó gọi constructor tương ứng ở lớp cha, để `Throwable` nhận được message và cause.

Nếu chỉ định nghĩa:

~~~java
public ConfigurationException(String message) {
    // Không gọi super(message).
}
~~~

thì nếu lớp cha có constructor không tham số phù hợp, Java ngầm gọi `super()`. Chuỗi `message` không tự được lưu vào trạng thái của `Throwable`; `getMessage()` có thể vẫn là `null`. Đây là **đoạn minh họa phản ví dụ**, chỉ có ý nghĩa khi đặt trong lớp phù hợp.

Nếu cần tùy chọn `enableSuppression` hoặc `writableStackTrace`, lớp con có thể khai báo một constructor nhận thêm hai boolean và gọi overload `protected` của `Exception`/`RuntimeException` hoặc của `Throwable` theo đúng chuỗi kế thừa. Cần cân nhắc liệu **API công khai có nên để caller quyết định hai cờ nguy hiểm này** hay giữ chính sách cố định trong lớp.

### Lớp lỗi unchecked cũng có thể dùng cấu hình nâng cao

~~~java
public final class LightweightFailure extends RuntimeException {
    public LightweightFailure(String message) {
        super(message, null, false, false);
    }
}
~~~

Đây là lớp độc lập nếu lưu trong `LightweightFailure.java`. Vì kế thừa `RuntimeException` nên nó là unchecked exception; việc tắt suppression và stack trace **không phải nguyên nhân** khiến nó unchecked.

Một lỗi như vậy chỉ phù hợp khi hệ thống chủ động chấp nhận mất thông tin chẩn đoán trên những đối tượng của lớp này. Không nên coi đây là mẫu mặc định cho exception tùy biến.

## 14. Các API stack trace và giới hạn khi tùy chỉnh

`Throwable.fillInStackTrace()` và `Throwable.setStackTrace(StackTraceElement[])` cho phép thao tác với dữ liệu stack trace trong một số trường hợp.

Ví dụ sử dụng thông thường:

~~~java
Throwable problem = new Throwable("example");
StackTraceElement[] frames = problem.getStackTrace();

System.out.println(frames.length > 0);
~~~

Khi tạo đối tượng trong một phương thức đang chạy bình thường, thường in `true`. Tuy nhiên không nên viết kiểm tra ứng dụng dựa vào **chính xác số frame** hoặc tên file/dòng, vì thông tin stack phụ thuộc nơi tạo và môi trường thực thi.

Nếu `writableStackTrace = false`, việc cố gọi `fillInStackTrace()` hoặc `setStackTrace(...)` không khôi phục dấu vết cho chính đối tượng đã bị tắt theo cơ chế constructor.

### Không nên biến stack trace thành hợp đồng nghiệp vụ

Stack trace hữu ích để gỡ lỗi nhưng không ổn định để dùng làm khóa dữ liệu, điều kiện luồng hoặc thông điệp cho người dùng cuối. Ví dụ, không viết logic kiểu:

~~~java
if (error.getStackTrace()[0].getClassName()
        .equals("com.example.Service")) {
    // Quyết định phục hồi dựa trên frame đầu.
}
~~~

Đây là **mã phản ví dụ**, giả định biến `error` có sẵn. Nó còn có thể ném `ArrayIndexOutOfBoundsException` nếu stack trace rỗng (điều rất dễ xảy ra với `writableStackTrace=false`). Hãy dựa trên **kiểu ngoại lệ**, trường mã lỗi ổn định, trạng thái nghiệp vụ hoặc cause có cấu trúc.

## 15. Cấu hình nâng cao và hiệu năng: đánh đổi cần hiểu rõ

Việc tạo ngoại lệ và thu thập stack trace có chi phí. Tắt writable stack trace có thể giảm công việc thu thập dấu vết trong một số trường hợp đặc biệt, nhưng **không nên tối ưu hóa chỉ từ giả định**:

- Ngoại lệ vốn được thiết kế cho các tình huống bất thường; dùng `throw/catch` làm nhánh điều khiển bình thường với tần suất rất cao thường khiến thiết kế khó đọc.
- Tắt stack trace có thể gây thiệt hại lớn khi phải chẩn đoán sự cố production.
- Tắt suppression làm mất thông tin lỗi thứ cấp, nhất là khi đóng tài nguyên.
- Một đối tượng ngoại lệ tái sử dụng có thể mang message/cause/stack trace từ sự cố cũ, gây nhầm lẫn nếu phát sinh nhiều lỗi độc lập.
- Hiệu quả của một thay đổi hiệu năng phải được đánh giá bằng phép đo thực tế trong môi trường mục tiêu.

**Quy tắc thực hành:** dùng constructor chuẩn với message và cause trước; chỉ tạo lớp lỗi có cấu hình đặc biệt khi có lý do kiến trúc, đo đạc hoặc hợp đồng rõ ràng.

## 16. Những hiểu lầm thường gặp

### Hiểu lầm 1 — “Constructor ngoại lệ tự động ném lỗi”

Không. `new IOException("...")` chỉ tạo đối tượng. `throw new IOException("...");` mới phát sinh đường lan truyền ngoại lệ.

### Hiểu lầm 2 — “Mọi lớp con của `Throwable` thừa kế hết các constructor”

Không. Constructor không được kế thừa; mỗi lớp con phải khai báo overload nó muốn cung cấp.

### Hiểu lầm 3 — “`Throwable(cause)` dùng `cause.getMessage()` làm message”

Không chính xác. Constructor cause-only thường tạo message từ `cause.toString()`, có thể bao gồm tên lớp và message.

### Hiểu lầm 4 — “`getCause()==null` nghĩa là luôn được gọi `initCause(...)`”

Sai. Constructor nhận cause đã khởi tạo trạng thái cause ngay cả khi truyền `null`. Chỉ cause thực sự **chưa được khởi tạo** mới có thể dùng `initCause` hợp lệ.

### Hiểu lầm 5 — “Cause có thể được đổi nhiều lần”

Không. `initCause` chỉ dùng một lần trên trạng thái chưa khởi tạo; constructor đã nhận cause thì không thể thay thế bằng `initCause`.

### Hiểu lầm 6 — “`getCause()` và `getSuppressed()` cùng một cơ chế”

Không. Cause thể hiện quan hệ nguyên nhân; suppressed lưu các lỗi phụ được giữ lại, đặc biệt khi đóng tài nguyên.

### Hiểu lầm 7 — “Tắt suppression có nghĩa lỗi khi đóng tài nguyên không xảy ra”

Không. Lỗi vẫn có thể xảy ra; chỉ là đối tượng ngoại lệ chính không giữ danh sách suppressed.

### Hiểu lầm 8 — “`writableStackTrace=false` có nghĩa ngoại lệ không được ném”

Không. Đối tượng vẫn có thể bị `throw` và `catch`; chỉ thiếu dấu vết stack trace của chính nó.

### Hiểu lầm 9 — “Tắt stack trace ở wrapper cũng xóa stack trace của cause”

Không. Mỗi `Throwable` có thông tin chẩn đoán riêng. Cause vẫn có thể giữ stack trace của chính nó.

### Hiểu lầm 10 — “Có thể gọi `fillInStackTrace()` để bật lại stack trace đã bị tắt”

Không. Khi constructor đặt `writableStackTrace=false`, cơ chế stack trace của đối tượng được thiết lập theo cấu hình đó.

### Hiểu lầm 11 — “Message phải là khóa logic ổn định cho ứng dụng”

Không. Message là văn bản giải thích; nếu cần quyết định tự động, thiết kế mã lỗi có cấu trúc hoặc ngoại lệ chuyên biệt.

### Hiểu lầm 12 — “Bắt `Throwable` là cách chuẩn để tránh mọi lỗi”

Không. `Throwable` bao gồm cả `Error`; bắt quá rộng có thể che lỗi nghiêm trọng và làm ứng dụng tiếp tục trong trạng thái không an toàn. Chỉ dùng tại ranh giới hệ thống có chính sách rõ.

### Hiểu lầm 13 — “`printStackTrace()` luôn cho cùng định dạng”

Không nên dựa vào một định dạng byte-for-byte cho logic nghiệp vụ hoặc test cứng, vì stack trace có thể khác theo đường gọi và môi trường chạy.

### Hiểu lầm 14 — “Nếu đã có cause thì không cần message”

Không hẳn. Message cung cấp bối cảnh tầng hiện tại; cause giữ nguồn gốc. Constructor nhận cả hai thường giúp người đọc hiểu hơn.

### Hiểu lầm 15 — “Tắt cả hai cờ luôn là tối ưu hiệu năng tốt”

Không. Đó là đánh đổi giữa chi phí ghi nhận và khả năng chẩn đoán; cần đo đạc và chính sách thiết kế phù hợp.

## 17. Bài tập tự kiểm tra

1. Nêu điểm khác nhau giữa `new IllegalArgumentException("x")` và `throw new IllegalArgumentException("x");`.
2. Liệt kê năm dạng constructor của `Throwable` và chỉ ra overload nào là `protected`.
3. Khi tạo `new Throwable()`, `getMessage()` và `getCause()` trả gì?
4. Constructor `Throwable(Throwable cause)` lấy message từ `cause.getMessage()` hay `cause.toString()`?
5. `new Throwable("error", null)` có cho phép `initCause(new IOException())` về sau không? Vì sao?
6. Khi nào `initCause(...)` hợp lệ và khi nào ném `IllegalStateException`?
7. Nếu truyền chính một đối tượng ngoại lệ làm cause của nó bằng `initCause` thì điều gì xảy ra?
8. Hai cờ `enableSuppression` và `writableStackTrace` điều khiển hai loại dữ liệu nào?
9. Tắt suppression có xóa `getCause()` không?
10. Nếu thân try-with-resources ném một đối tượng có suppression bị tắt và `close()` cũng lỗi, `getSuppressed()` của lỗi chính phản ánh điều gì?
11. `writableStackTrace=false` ảnh hưởng `getStackTrace()` như thế nào?
12. Nếu wrapper tắt writable stack trace nhưng cause có stack trace bình thường, dữ liệu stack trace của cause có bị mất không?
13. Tại sao một lớp ngoại lệ tùy biến cần gọi `super(message, cause)`?
14. Tại sao `catch (Exception e)` không chắc nhận được mọi đối tượng trong nhánh `Throwable`?
15. `Throwable(String, Throwable, boolean, boolean)` có thể được gọi trực tiếp từ bất kỳ lớp nào hay không?
16. Nêu một trường hợp API cần constructor nhận cause thay vì chỉ có message.
17. Việc ném lại cùng một đối tượng ngoại lệ có tự động viết lại vị trí tạo trên stack trace không?
18. Tại sao dùng message hoặc frame của stack trace để quyết định nhánh nghiệp vụ là không ổn định?
19. Nếu `getStackTrace()` trả mảng rỗng, truy cập `[0]` có an toàn không?
20. Trước khi tắt stack trace vì hiệu năng, nên xem xét những yếu tố nào?

### Gợi ý đáp án

1. `new` tạo đối tượng; `throw` mới ném.
2. Không tham số; message; cause; message+cause; message+cause+enableSuppression+writableStackTrace; dạng cuối là `protected`.
3. Thường đều `null`.
4. `cause.toString()` khi cause khác `null`.
5. Không, vì cause đã được khởi tạo bằng constructor dù giá trị là `null`.
6. Hợp lệ một lần trên đối tượng có cause chưa khởi tạo; nếu đã khởi tạo thì `IllegalStateException`.
7. `IllegalArgumentException` vì self-causation không hợp lệ.
8. Danh sách suppressed exceptions và khả năng ghi stack trace.
9. Không.
10. Danh sách rỗng dù lỗi khi đóng thực sự có thể xảy ra.
11. Trả mảng rỗng cho chính đối tượng đó.
12. Không; cause có trạng thái riêng.
13. Để khởi tạo đúng các trường chẩn đoán do `Throwable` quản lý.
14. `Error` không thuộc nhánh `Exception`.
15. Không; overload bốn tham số của `Throwable` là `protected`.
16. Khi chuyển lỗi I/O thành một lỗi nghiệp vụ tầng cao mà vẫn cần giữ nguyên nhân.
17. Không; stack trace mặc định thường được ghi khi tạo, không tự viết lại cho mỗi lần ném.
18. Chúng có thể thay đổi theo ngữ cảnh, phiên bản, môi trường; nên dùng kiểu và mã lỗi có cấu trúc.
19. Không; phải kiểm tra độ dài trước.
20. Cần cân nhắc đo đạc hiệu năng, tần suất tạo lỗi và chi phí mất khả năng điều tra sự cố.

## 18. Ghi nhớ

**Constructor của `Throwable` khởi tạo dữ liệu chẩn đoán; `throw` mới ném đối tượng.** Dùng message để giải thích lỗi, cause để bảo toàn nguyên nhân, suppressed exceptions để giữ các lỗi phụ và stack trace để định vị nơi đối tượng được tạo. Constructor `protected` bốn tham số cho phép lớp con tắt suppression hoặc stack trace, nhưng có thể làm mất thông tin quan trọng khi điều tra sự cố. Với phần lớn mã Java, ưu tiên các constructor chuẩn `(String message)` và `(String message, Throwable cause)`; chỉ sử dụng cấu hình nâng cao khi hiểu rõ và chấp nhận các đánh đổi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="throwable-internals-002">getMessage, getLocalizedMessage và toString</a>

<details>
<summary>Xem chi tiết</summary>

# `getMessage()`, `getLocalizedMessage()` và `toString()` của đối tượng `Throwable` trong Java

Khi một ngoại lệ xuất hiện trong Java, ba phương thức của `Throwable` thường được dùng để giải thích lỗi: `getMessage()`, `getLocalizedMessage()` và `toString()`. Chúng liên quan nhau nhưng **không đồng nghĩa**, và không phương thức nào tự trả về toàn bộ stack trace.

- **`getMessage()`** trả **thông điệp chi tiết** (detail message) của đối tượng ngoại lệ, kiểu `String`; kết quả có thể là `null`.
- **`getLocalizedMessage()`** được thiết kế để hỗ trợ **thông điệp bản địa hóa**. Với triển khai mặc định của `Throwable`, nó chỉ gọi `getMessage()`, nên **không có dịch ngôn ngữ tự động**.
- **`toString()`** trong triển khai mặc định của `Throwable` tạo **mô tả ngắn có tên lớp ngoại lệ**, thường là `tên-lớp: thông-điệp-localized`. Nếu localized message là `null`, nó chỉ trả về tên lớp.

Bài này giải thích chính xác ý nghĩa, giá trị trả về, cách các phương thức tương tác khi kế thừa/override, khác biệt giữa message và cause/stack trace, và cách dùng trong log, ứng dụng nhiều ngôn ngữ và xử lý exception. Các ví dụ hoàn chỉnh ưu tiên Java 17 trở lên.

## 1. `Throwable` là lớp nền của đối tượng ngoại lệ

Hệ phân cấp Java có dạng rút gọn:

```text
java.lang.Throwable
├── java.lang.Error
│   └── ...
└── java.lang.Exception
    ├── java.io.IOException
    └── java.lang.RuntimeException
        ├── java.lang.IllegalArgumentException
        └── ...
```

Mọi ngoại lệ và lỗi có thể ném theo hệ thống exception Java đều thuộc nhánh `Throwable`. Vì vậy ba phương thức đang học xuất hiện trong những lớp con như `IOException`, `IllegalArgumentException` hoặc `IllegalStateException`.

Một đối tượng `Throwable` còn có những dữ liệu và phương thức quan trọng khác:

| Thành phần | Trả lời câu hỏi |
| --- | --- |
| `getClass()` | Đối tượng có loại runtime thực tế nào? |
| `getMessage()` | Thông điệp chi tiết của lỗi là gì? |
| `getLocalizedMessage()` | Có thông điệp bản địa hóa cho lỗi không? |
| `toString()` | Mô tả ngắn gồm loại lỗi và localized message là gì? |
| `getCause()` | Ngoại lệ nguyên nhân được gắn với lỗi hiện tại là gì? |
| `getSuppressed()` | Những ngoại lệ phụ nào được giữ lại, chẳng hạn khi cleanup? |
| `getStackTrace()` | Những stack frame nào đã được ghi cho ngoại lệ? |
| `printStackTrace()` | Làm thế nào in stack trace và các quan hệ cause/suppressed? |

**Một message không phải một exception.** Message chỉ là dữ liệu `String` mô tả lỗi. Đối tượng ngoại lệ còn có kiểu, quan hệ nguyên nhân, stack trace, suppressed exception và các đặc điểm khác.

## 2. `getMessage()`: thông điệp chi tiết

`Throwable.getMessage()` có dạng khái niệm:

```java
public String getMessage()
```

Phương thức trả về chuỗi chi tiết gắn với exception. Nhiều constructor cho phép bạn truyền message:

```java
IllegalArgumentException error =
        new IllegalArgumentException("Số lượng phải dương");

String message = error.getMessage();
// message == "Số lượng phải dương"
```

Đây là **đoạn mã minh họa**, không phải chương trình độc lập. Thông điệp thông thường được giữ tại thời điểm tạo đối tượng, nhưng những lớp con có thể override `getMessage()` với hành vi riêng. Vì vậy không nên coi mọi lớp con đều có cùng cách xây dựng message bên trong.

Message thường nên trả lời: thao tác nào vi phạm hợp đồng, tham số/trạng thái nào không hợp lệ, hoặc chuyện gì thất bại. Ví dụ:

```java
throw new IllegalArgumentException(
        "quantity phải lớn hơn 0: " + quantity);
```

Đây là **câu lệnh minh họa** trong phạm vi có biến `quantity`. Một thông điệp có ngữ cảnh hữu ích hơn các message quá chung như `"error"`, nhưng tuyệt đối tránh ghép mật khẩu, token, dữ liệu cá nhân hoặc nội dung nhạy cảm vào thông điệp.

**Quan trọng:** `getMessage()` có thể trả `null`, đặc biệt khi object được tạo bằng constructor không cung cấp detail message. Kết quả `null` không có nghĩa “không xảy ra lỗi”; đối tượng ngoại lệ vẫn tồn tại.

## 3. `getLocalizedMessage()`: không phải API dịch tự động

`Throwable.getLocalizedMessage()` cũng có kiểu trả về `String`:

```java
public String getLocalizedMessage()
```

Trong triển khai mặc định của `Throwable`, phương thức này **trả về kết quả của `getMessage()`**. Java không tự lấy câu tiếng Anh rồi dịch sang tiếng Việt dựa trên `Locale.getDefault()`.

Ví dụ, nếu tạo:

```java
Exception e = new Exception("Connection failed");
```

thì theo triển khai mặc định của `Throwable`:

- `e.getMessage()` là `"Connection failed"`.
- `e.getLocalizedMessage()` cũng là `"Connection failed"`, bất kể bạn mong giao diện bằng tiếng Việt.
- Muốn trả một chuỗi đã được địa phương hóa theo ngôn ngữ, một **lớp con cần chủ động override** phương thức hoặc một tầng hiển thị cần dịch thông qua cơ chế như `ResourceBundle`.

Điểm tinh tế: khi lớp con override `getMessage()` nhưng không override `getLocalizedMessage()`, triển khai mặc định `getLocalizedMessage()` gọi `getMessage()` theo cơ chế đa hình Java, nên phản ánh giá trị từ override đó. Khi lớp con override riêng `getLocalizedMessage()`, hai phương thức có thể trả hai chuỗi **khác nhau**.

Việc bản địa hóa là **hợp đồng do class/thư viện thiết kế**, không thể suy ra chỉ từ tên `getLocalizedMessage` rằng exception nào cũng được dịch.

## 4. `toString()`: tên lớp kèm thông điệp localized

Theo triển khai chuẩn của `Throwable`, kết quả `toString()` được hình thành từ:

1. **Tên lớp runtime đầy đủ** của đối tượng, tức `getClass().getName()`.
2. Nếu `getLocalizedMessage()` khác `null`, nối `": "` và localized message.
3. Nếu localized message là `null`, chỉ trả tên lớp.

Có thể hình dung logic (đây là **mã minh họa**, không phải đoạn cài đặt thư viện cần sao chép):

```java
String className = getClass().getName();
String localized = getLocalizedMessage();
return localized == null
        ? className
        : className + ": " + localized;
```

Ví dụ `IllegalArgumentException` nằm ở gói `java.lang`:

```java
IllegalArgumentException e =
        new IllegalArgumentException("Sai tham số");

System.out.println(e.getMessage());
// Sai tham số

System.out.println(e.toString());
// java.lang.IllegalArgumentException: Sai tham số
```

Đây là **các câu lệnh minh họa** trong một phương thức, không chứa `main`. Khi truyền trực tiếp một exception vào `System.out.println(e)`, Java gọi cơ chế chuyển object thành chuỗi, thường dùng `toString()`.

**Không nhầm tên lớp trong `toString()` với `getClass().getSimpleName()`.** `Throwable.toString()` dùng tên trả về từ `getClass().getName()` theo triển khai chuẩn, thường gồm package. Với nested class, tên nhị phân có thể có ký tự `$`; với class nằm trong package, tên package cũng xuất hiện.

Nếu **lớp con override `toString()`** thì kết quả có thể khác công thức mặc định. Những ví dụ và quy tắc trong bài nói đến triển khai mặc định của `Throwable` trừ khi ghi rõ override.

## 5. Ví dụ đầy đủ: so sánh ba phương thức và message `null`

Lưu chương trình thành **`ThrowableMessagesDemo.java`**:

```java
public class ThrowableMessagesDemo {
    public static void main(String[] args) {
        Throwable withMessage =
                new IllegalArgumentException("Số lượng phải dương");

        System.out.println("message: " + withMessage.getMessage());
        System.out.println("localized: "
                + withMessage.getLocalizedMessage());
        System.out.println("toString: " + withMessage.toString());

        Throwable noMessage = new IllegalArgumentException();

        System.out.println("null message: "
                + (noMessage.getMessage() == null));
        System.out.println("null localized: "
                + (noMessage.getLocalizedMessage() == null));
        System.out.println("toString null: " + noMessage.toString());
    }
}
```

Biên dịch và chạy với Java 17 trở lên:

```bash
javac -encoding UTF-8 --release 17 ThrowableMessagesDemo.java
java ThrowableMessagesDemo
```

**Stdout chính xác** trên console UTF-8:

```text
message: Số lượng phải dương
localized: Số lượng phải dương
toString: java.lang.IllegalArgumentException: Số lượng phải dương
null message: true
null localized: true
toString null: java.lang.IllegalArgumentException
```

Giải thích: hai object cùng loại runtime `IllegalArgumentException` nhưng khác detail message. Khi có message, `toString()` ghép tên lớp và message; khi message `null`, phiên bản mặc định chỉ trả tên lớp. Cả hai vẫn là đối tượng ngoại lệ thực sự.

Đây cũng là lý do không được mặc định gọi `e.getMessage().length()` mà không xét `null`: chính phương thức quan sát message có thể khiến chương trình gặp một `NullPointerException` mới nếu code dereference kết quả `null`.

## 6. Phân biệt thông điệp `null` và chuỗi rỗng

`null` và `""` là hai giá trị khác nhau:

- **`null`**: không có đối tượng `String` detail message.
- **`""`**: có chuỗi `String` nhưng độ dài bằng 0.

Trong triển khai chuẩn, `Throwable.toString()` kiểm tra **`localizedMessage == null`**, không kiểm tra chuỗi có rỗng hay không. Vì vậy nếu message là `""`, kết quả vẫn chứa dấu `": "` dù không có ký tự nào phía sau.

Lưu **`EmptyMessageDemo.java`**:

```java
public class EmptyMessageDemo {
    public static void main(String[] args) {
        Throwable noMessage = new IllegalStateException();
        Throwable emptyMessage = new IllegalStateException("");

        System.out.println("Null? "
                + (noMessage.getMessage() == null));
        System.out.println("Empty? "
                + emptyMessage.getMessage().isEmpty());
        System.out.println("Null toString: " + noMessage);
        System.out.println("Empty suffix: "
                + emptyMessage.toString().endsWith(": "));
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 EmptyMessageDemo.java
java EmptyMessageDemo
```

**Stdout chính xác:**

```text
Null? true
Empty? true
Null toString: java.lang.IllegalStateException
Empty suffix: true
```

Dòng cuối dùng `endsWith(": ")` thay vì in trực tiếp một dấu cách cuối dòng khó nhìn thấy. Cách đó xác nhận chính xác công thức của `toString()` với chuỗi rỗng.

Khi thiết kế API, nên phân biệt “không có message” với “message được cung cấp nhưng rỗng” nếu hợp đồng cần. Khi hiển thị cho người dùng, cả hai trường hợp thường cần một fallback được định nghĩa rõ.

## 7. Bảng so sánh nhanh

| Câu hỏi | `getMessage()` | `getLocalizedMessage()` | `toString()` |
| --- | --- | --- | --- |
| Kiểu trả về | `String` | `String` | `String` |
| Nội dung mặc định | Detail message | Gọi `getMessage()` | Tên lớp, tùy chọn cộng `": "` và localized message |
| Có thể trả `null`? | Có | Có | Với triển khai chuẩn `Throwable`, không |
| Tự gồm tên lớp exception? | Không theo mặc định | Không theo mặc định | Có |
| Tự cung cấp stack frames? | Không | Không | Không |
| Tự gồm nguyên nhân `getCause()`? | Không nói chung | Không nói chung | Không nói chung |
| Có tự dịch theo `Locale`? | Không | Không theo mặc định | Không; dùng localized message hiện có |
| Có thể thay đổi do subclass override? | Có | Có | Có |
| Phù hợp cho | Message kỹ thuật theo hợp đồng | Message bản địa hóa khi class hỗ trợ | Mô tả ngắn để nhận diện loại và thông điệp |

Bảng mô tả **hành vi mặc định**, không cam kết mọi subclass tự viết lại ba phương thức đều giữ đúng công thức đó. Khi dùng một exception từ thư viện bên thứ ba, hãy đọc tài liệu API nếu cần chính xác tuyệt đối về message.

## 8. Bản địa hóa thật sự: override `getLocalizedMessage()`

Một lớp ngoại lệ tùy chỉnh có thể giữ **thông điệp kỹ thuật ổn định** qua `getMessage()`, đồng thời trả thông điệp tiếng Việt hoặc tiếng Anh qua `getLocalizedMessage()`.

Lưu thành **`LocalizedMessageDemo.java`**. Tệp có một lớp `public` chứa `main` và một lớp package-private cùng tệp:

```java
import java.util.Locale;

class LocalizedProblem extends Exception {
    private final Locale locale;

    LocalizedProblem(Locale locale) {
        super("Missing configuration");
        this.locale = locale;
    }

    @Override
    public String getLocalizedMessage() {
        if ("vi".equals(locale.getLanguage())) {
            return "Thiếu cấu hình";
        }
        return getMessage();
    }
}

public class LocalizedMessageDemo {
    public static void main(String[] args) {
        LocalizedProblem vi =
                new LocalizedProblem(Locale.forLanguageTag("vi-VN"));
        LocalizedProblem en =
                new LocalizedProblem(Locale.ENGLISH);

        System.out.println("VI message: " + vi.getMessage());
        System.out.println("VI localized: " + vi.getLocalizedMessage());
        System.out.println("VI toString: " + vi.toString());
        System.out.println("EN localized: " + en.getLocalizedMessage());
        System.out.println("EN toString: " + en.toString());
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 LocalizedMessageDemo.java
java LocalizedMessageDemo
```

**Stdout chính xác:**

```text
VI message: Missing configuration
VI localized: Thiếu cấu hình
VI toString: LocalizedProblem: Thiếu cấu hình
EN localized: Missing configuration
EN toString: LocalizedProblem: Missing configuration
```

Điểm nổi bật:

- `super("Missing configuration")` thiết lập message kỹ thuật của exception.
- `getMessage()` không bị override nên vẫn trả `"Missing configuration"`.
- `getLocalizedMessage()` được override và chọn thông điệp từ `Locale` được truyền vào constructor.
- **`toString()` mặc định gọi `getLocalizedMessage()`**, nên cùng một lớp có thể tạo mô tả tiếng Việt hoặc tiếng Anh tùy object.
- `LocalizedProblem` nằm ở default package, vì thế tên lớp trong output là `LocalizedProblem`. Nếu đặt vào package `com.example` thì `toString()` mặc định sẽ thêm tên package.

Bài này dùng điều kiện `if` đơn giản để output dễ xác minh. Ứng dụng nhiều ngôn ngữ thực sự thường cần **message key**, `ResourceBundle` hoặc hệ thống quản lý bản dịch, fallback theo ngôn ngữ và chính sách tham số hóa thay vì hard-code các chuỗi trong lớp ngoại lệ.

## 9. Khi nào nên dùng `ResourceBundle`?

`ResourceBundle` hỗ trợ tìm chuỗi theo `Locale` từ tập tài nguyên được quản lý riêng. Một hướng thiết kế có thể là:

```java
// Minh họa ý tưởng trong một phương thức.
String template = bundle.getString("error.config.missing");
String localized = String.format(locale, template, configName);
```

Đây là **đoạn minh họa**, giả định `bundle`, `locale` và `configName` đã được định nghĩa. Không có tệp bản dịch kèm theo nên không coi đây là chương trình chạy độc lập.

Một lựa chọn khác là giữ exception chỉ chứa **mã lỗi ổn định và tham số có cấu trúc**, rồi để tầng giao diện sử dụng `ResourceBundle` để hiển thị theo ngôn ngữ của người dùng. Cách này hữu ích trong web server có nhiều request với ngôn ngữ khác nhau: exception kỹ thuật không buộc phải quyết định ngôn ngữ hiển thị cuối cùng.

Cần chú ý:

1. **Không dựa vào `Locale.getDefault()` toàn cục cho mọi request** nếu mỗi request có locale riêng; thay đổi locale toàn cục có thể làm các request ảnh hưởng nhau.
2. Nếu lưu `Locale` trong exception, hãy cân nhắc exception được tạo ở tầng nào và ai quyết định ngôn ngữ.
3. Thông điệp hiển thị cho người dùng cần được rà soát về tính dễ hiểu và an toàn, không chỉ được dịch.
4. Message kỹ thuật ở log và localized message cho giao diện có thể khác nhau một cách có chủ đích.
5. Không phân tích chương trình bằng cách so khớp văn bản bản dịch vì ngôn ngữ và câu chữ có thể thay đổi.

**`getLocalizedMessage()` là điểm mở rộng có sẵn, không bắt buộc mọi ứng dụng phải override nó.** Nếu API của bạn tách dịch thuật khỏi exception, điều đó vẫn có thể là thiết kế tốt.

## 10. Kế thừa và override: thứ tự lời gọi có ý nghĩa

Theo mã chuẩn của `Throwable`:

```text
getMessage()
      ▲
      │ mặc định
getLocalizedMessage()
      ▲
      │ được gọi trong công thức mặc định
toString()
```

Sơ đồ này mô tả **quan hệ lời gọi trong triển khai mặc định**, không phải quan hệ kế thừa của các lớp.

Các trường hợp:

- **Không override gì**: `getLocalizedMessage()` bằng `getMessage()`; `toString()` ghép tên lớp với localized message nếu khác `null`.
- **Override `getMessage()` nhưng không override `getLocalizedMessage()`**: localized message mặc định thường phản ánh giá trị mới từ `getMessage()`.
- **Override `getLocalizedMessage()`**: `toString()` mặc định dùng localized message mới, kể cả khi nó khác detail message kỹ thuật.
- **Override `toString()`**: định dạng chuỗi có thể thay đổi hoàn toàn; code khác không nên giả định công thức mặc định còn áp dụng.
- **Override `getLocalizedMessage()` trả `null`**: `toString()` mặc định trả tên lớp, ngay cả khi `getMessage()` khác `null`.

Có thể xác minh trường hợp cuối bằng lớp tùy chỉnh, nhưng cần tránh override các phương thức chỉ để che lỗi khi không có lý do thiết kế rõ. Một exception có message kỹ thuật tốt mà `toString()` lại bỏ message có thể khiến log thiếu thông tin.

Một điểm kỹ thuật quan trọng: `toString()` và `getLocalizedMessage()` là phương thức có thể được gọi từ code log hoặc từ nơi xây dựng thông báo lỗi. Vì thế override nên **đơn giản, không ném lỗi mới và không làm I/O nặng**. Nếu quá trình dịch thuật thất bại ngay trong lúc chẩn đoán một exception khác, chương trình có thể mất thêm thông tin quan trọng.

## 11. `getCause()` không nằm tự động trong `getMessage()`

Một exception có thể **bọc một nguyên nhân** (cause). Điều đó không có nghĩa message của exception ngoài sẽ tự kết hợp mọi message trong chuỗi cause. Ví dụ:

```java
IOException original = new IOException("Không đọc được tệp");
IllegalStateException wrapper =
        new IllegalStateException("Không thể khởi tạo", original);
```

Đây là các khai báo minh họa, cần import `java.io.IOException`. Với constructor `IllegalStateException(String, Throwable)`:

- `wrapper.getMessage()` là `"Không thể khởi tạo"`.
- `wrapper.getCause()` trỏ tới `original`.
- `wrapper.toString()` theo mặc định là `"java.lang.IllegalStateException: Không thể khởi tạo"`.
- `original.getMessage()` là `"Không đọc được tệp"`.

Thông điệp của wrapper và nguyên nhân là **hai dữ liệu khác nhau**. Muốn giải thích đầy đủ cần xem `getCause()` hoặc in/thu thập stack trace có cấu trúc. Cũng không nên bọc lỗi bằng `new RuntimeException(e.getMessage())` nếu mục tiêu bảo toàn nguyên nhân; cách đó không truyền object gốc làm cause.

## 12. Ví dụ đầy đủ: cause và message của wrapper

Lưu thành **`CauseAndMessageDemo.java`**:

```java
import java.io.IOException;

public class CauseAndMessageDemo {
    public static void main(String[] args) {
        IOException root =
                new IOException("Không đọc được tệp");
        IllegalStateException wrapper =
                new IllegalStateException("Không thể khởi tạo", root);

        System.out.println("Wrapper message: "
                + wrapper.getMessage());
        System.out.println("Wrapper localized: "
                + wrapper.getLocalizedMessage());
        System.out.println("Wrapper toString: "
                + wrapper.toString());
        System.out.println("Cause type: "
                + wrapper.getCause().getClass().getSimpleName());
        System.out.println("Cause message: "
                + wrapper.getCause().getMessage());

        RuntimeException derived = new RuntimeException(root);
        System.out.println("Cause-only constructor: "
                + derived.getMessage().equals(root.toString()));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 CauseAndMessageDemo.java
java CauseAndMessageDemo
```

**Stdout chính xác:**

```text
Wrapper message: Không thể khởi tạo
Wrapper localized: Không thể khởi tạo
Wrapper toString: java.lang.IllegalStateException: Không thể khởi tạo
Cause type: IOException
Cause message: Không đọc được tệp
Cause-only constructor: true
```

Dòng cuối minh họa một **biến thể constructor quan trọng**: `new RuntimeException(root)` sử dụng `root.toString()` làm detail message của exception mới (khi cause khác `null`). Vì vậy đôi khi message của wrapper **có chứa tên loại và message của cause**, nhưng đó là hành vi constructor cụ thể, **không phải** vì `getMessage()` tự duyệt mọi cause.

So sánh:

| Constructor | Message của wrapper |
| --- | --- |
| `new RuntimeException("Tầng dịch vụ thất bại", cause)` | Chuỗi được truyền: `"Tầng dịch vụ thất bại"` |
| `new RuntimeException(cause)` | `cause.toString()`, nếu cause khác `null` |
| `new RuntimeException()` | Thường là `null` theo constructor mặc định |
| `new RuntimeException("Tầng dịch vụ thất bại")` | Chuỗi được truyền, không có cause tự động |

Khi thiết kế API, thường hữu ích hơn nếu truyền **message ở tầng hiện tại cùng cause** qua constructor `(String, Throwable)`: vừa diễn đạt thao tác đang thất bại, vừa giữ object nguyên nhân cho chẩn đoán.

## 13. `getSuppressed()` là danh sách lỗi phụ, không phải phần của message

Từ Java 7, `Throwable` có thể giữ các **suppressed exceptions**, thường xuất hiện khi một lỗi trong thân `try-with-resources` đã xảy ra, sau đó `close()` cũng thất bại. Những lỗi này không tự động được nối vào `getMessage()` hoặc `toString()` của exception chính.

Ví dụ, lưu **`SuppressedMessageDemo.java`**:

```java
public class SuppressedMessageDemo {
    public static void main(String[] args) {
        IllegalStateException main =
                new IllegalStateException("Lỗi xử lý");
        main.addSuppressed(
                new IllegalArgumentException("Lỗi cleanup"));

        System.out.println("Message: " + main.getMessage());
        System.out.println("toString: " + main.toString());
        System.out.println("Số suppressed: "
                + main.getSuppressed().length);
        System.out.println("Suppressed message: "
                + main.getSuppressed()[0].getMessage());
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 --release 17 SuppressedMessageDemo.java
java SuppressedMessageDemo
```

**Stdout chính xác:**

```text
Message: Lỗi xử lý
toString: java.lang.IllegalStateException: Lỗi xử lý
Số suppressed: 1
Suppressed message: Lỗi cleanup
```

Thông điệp `"Lỗi cleanup"` chỉ xuất hiện khi đọc trực tiếp `getSuppressed()[0].getMessage()`; `main.getMessage()` và `main.toString()` không tự liệt kê nó. Khi viết logging có cấu trúc, đừng chỉ lưu một chuỗi `e.toString()` rồi cho rằng đã giữ đủ cause và suppressed.

Ví dụ cố tình thêm một suppressed exception nên có đúng một phần tử; mã thực cần kiểm tra độ dài mảng, không mặc định mọi exception đều có suppressed.

## 14. `toString()` khác `printStackTrace()` như thế nào?

Đây là nhầm lẫn rất phổ biến:

- **`e.getMessage()`**: một `String` chi tiết, có thể `null`.
- **`e.getLocalizedMessage()`**: một `String` bản địa hóa nếu lớp hỗ trợ, có thể `null`.
- **`e.toString()`**: một `String` mô tả ngắn (theo mặc định: tên lớp và localized message).
- **`e.printStackTrace()`**: **thực hiện thao tác in** thông tin lỗi và stack trace; phương thức trả về `void`, không trả một chuỗi để bạn gán.

Theo mặc định, `printStackTrace()` không đối số in ra **standard error stream** (`System.err`), bao gồm dòng mô tả exception và các stack frames; nó cũng có thể trình bày cause và suppressed exceptions. Số dòng, đường dẫn, định dạng và thứ tự các frame có thể khác theo môi trường, cách build và JVM, nên không gắn một bản stdout cố định cho ví dụ in stack trace.

Ví dụ:

```java
try {
    throw new IllegalStateException("Lỗi xử lý");
} catch (IllegalStateException e) {
    System.out.println(e.toString()); // Một dòng trên stdout.
    e.printStackTrace();              // Nhiều dòng trên stderr.
}
```

Đây là **khối minh họa** có thể đặt trong `main`. Do stdout và stderr có thể được thu gom/hiển thị xen kẽ bởi terminal, thứ tự xuất hiện trên màn hình không phải hợp đồng đáng dùng để kiểm thử khi hai luồng được tách biệt.

Nếu muốn lấy trace thành một chuỗi cho mục đích đặc biệt, có thể in vào `StringWriter` thông qua `PrintWriter`, nhưng thư viện logging hiện đại thường cho phép truyền trực tiếp object `Throwable`, tốt hơn là tự định dạng stack trace bằng tay.

## 15. Vì sao log chỉ có `getMessage()` thường chưa đủ?

Giả sử hai loại exception khác nhau đều có cùng message `"invalid input"`. Nếu log chỉ ghi message, bạn không biết lỗi là `NumberFormatException`, `IllegalArgumentException` hay loại ứng dụng tùy chỉnh.

Ngược lại, log chỉ ghi `toString()` có tên loại nhưng vẫn **không có stack frames hoặc cause/suppressed**. Khi điều tra lỗi kỹ thuật, những dữ liệu đó thường quan trọng hơn một dòng mô tả.

Thông thường cần:

1. **Loại exception** và ngữ cảnh thao tác.
2. **Message** có nội dung đủ hữu ích, không lộ dữ liệu nhạy cảm.
3. **Stack trace** khi lỗi cần chẩn đoán đường gọi.
4. **Cause** khi ngoại lệ được wrapping giữa các tầng.
5. **Suppressed exceptions** nếu cleanup hoặc nhiều tài nguyên cũng thất bại.
6. **Mã tương quan/request ID hoặc mã lỗi ổn định**, thay vì suy diễn từ câu chữ tự do.

Ví dụ khung cho API logging có hỗ trợ `Throwable`:

```java
// Pseudocode, tùy API của thư viện logging đang dùng:
logger.error("Không thể xử lý request {}", requestId, e);
```

Đây là **pseudocode**, không phải ví dụ Java thuần chạy độc lập. Tùy thư viện và overload cụ thể, cách truyền `Throwable` có thể khác nhau; cần đọc API logging sử dụng thực tế.

**Không dùng message làm khóa logic nghiệp vụ**, ví dụ `if (e.getMessage().contains("not found"))`. Một thay đổi wording hoặc ngôn ngữ sẽ phá logic. Hãy dùng loại exception, error code có cấu trúc hoặc kiểu kết quả nghiệp vụ rõ ràng.

## 16. Dùng thông điệp với giao diện người dùng

Một message được viết cho lập trình viên có thể không phù hợp để hiển thị trực tiếp cho người dùng. Ví dụ:

```java
throw new IllegalStateException(
        "DB_CONN_02: connection pool exhausted, host=db.internal");
```

Đây là **câu lệnh minh họa**. Message chứa chi tiết hệ thống; đưa thẳng `e.getMessage()` vào giao diện có thể làm lộ thông tin triển khai. Ngay cả `e.getLocalizedMessage()` cũng không bảo đảm an toàn nếu lớp không được thiết kế cho giao diện người dùng.

Cách thiết kế là tách:

- **Thông tin kỹ thuật** cho quan sát và sửa lỗi: loại exception, message, stack trace, cause, suppressed.
- **Mã lỗi ổn định** để ứng dụng quyết định loại phản hồi, kiểm thử, API và quy trình hỗ trợ.
- **Thông điệp người dùng** được biên soạn và bản địa hóa theo locale của request hoặc người dùng, sau khi kiểm tra tính bảo mật.

Một số exception domain có thể mang `errorCode` và tham số riêng, thay vì bắt giao diện phân tích `toString()`. Khi API cần trả thông điệp cho người dùng, ưu tiên tầng có hiểu biết về locale, quyền hạn và ngữ cảnh thay vì mặc định expose toàn bộ chuỗi exception.

## 17. Trường hợp đặc biệt: message có thể được JVM tạo

Không phải mọi exception đều được tạo bằng constructor có message tường minh trong mã ứng dụng. Ví dụ, `NullPointerException` có thể được JVM phát sinh khi chương trình dereference `null`.

Trong các phiên bản JVM hiện đại, **một số `NullPointerException` do JVM phát sinh** có thể có thông điệp chẩn đoán chi tiết hơn (thường gọi *helpful NullPointerExceptions*) để chỉ ra biểu thức nào có giá trị `null`. Nhưng điều đó **phụ thuộc phiên bản và cấu hình JVM, cách lỗi được tạo**, không phải hợp đồng cố định cho mọi `NullPointerException`.

Vì vậy:

- Không suy luận rằng mọi `NullPointerException` đều có `getMessage() != null`.
- Không so sánh đúng nguyên văn message JVM như một “mã lỗi” ổn định.
- Không phụ thuộc vào tên biến và wording trong message để quyết định phục hồi.
- Khi cần thông điệp đặc thù cho tiền điều kiện API, hãy kiểm tra rõ ràng và đưa ra hợp đồng riêng.

Một lỗi có message `null` vẫn có loại exception và stack trace (tùy cơ chế tạo), nên các phương pháp chẩn đoán khác không phụ thuộc message vẫn có giá trị.

## 18. Override `toString()` có nên không?

Có thể override `toString()` trên một lớp exception tùy chỉnh vì đây là phương thức được kế thừa từ `Object` và `Throwable` đã cung cấp triển khai riêng. Tuy nhiên, việc đổi định dạng nên có lý do rõ ràng:

```java
class TaggedException extends RuntimeException {
    private final String code;

    TaggedException(String code, String message) {
        super(message);
        this.code = code;
    }

    @Override
    public String toString() {
        return code + ": " + getMessage();
    }
}
```

Đây là **lớp minh họa không có `main`**. Nếu tạo `new TaggedException("E100", "Thiếu cấu hình")`, `toString()` của class này trả `"E100: Thiếu cấu hình"` thay vì tên lớp runtime theo công thức của `Throwable`.

Đổi `toString()` có thể làm mất tên lớp trong các log hoặc header của stack trace; các công cụ giám sát có thể dựa vào cấu trúc cũ. Nếu mục tiêu là thêm mã lỗi, thường tốt hơn là giữ mã lỗi trong **field + getter** (`getCode()`) để hệ thống logging/response sử dụng có cấu trúc, còn `toString()` không bị tái định nghĩa quá xa hợp đồng mong đợi.

Tương tự, override `getLocalizedMessage()` không nên thực hiện I/O mạng hay truy cập môi trường có thể thất bại chỉ để dựng một chuỗi chẩn đoán. Nếu formatter ném thêm exception ngay khi đang xử lý lỗi ban đầu, quá trình quan sát sẽ trở nên khó tin cậy.

## 19. Những hiểu lầm phổ biến

| Hiểu lầm | Cách hiểu đúng |
| --- | --- |
| “`getMessage()` luôn trả về chuỗi.” | Nó trả kiểu `String` nhưng **có thể là `null`**. |
| “`getLocalizedMessage()` tự dịch theo ngôn ngữ hệ điều hành.” | Mặc định `Throwable` chỉ gọi `getMessage()`; phải có override/cơ chế bản địa hóa riêng. |
| “`getMessage()` và `toString()` giống nhau.” | Mặc định `toString()` thêm tên lớp runtime và dùng localized message. |
| “`toString()` luôn trả `getMessage()`.” | Với `Throwable` mặc định, `toString()` dùng **`getLocalizedMessage()`**. |
| “`toString()` có đầy đủ stack trace.” | Không; chỉ là mô tả ngắn. |
| “`getMessage()` luôn chứa thông tin cause.” | Không; cause là dữ liệu riêng, trừ các constructor/override cụ thể tự dựng message từ cause. |
| “`getSuppressed()` tự xuất hiện trong `toString()`.” | Không; danh sách suppressed nằm riêng và thường được thể hiện trong `printStackTrace()`. |
| “`printStackTrace()` trả String.” | Không, nó trả `void` và in thông tin ra stream. |
| “Message null nghĩa là exception vô hại.” | Không; loại lỗi và đường thực thi vẫn cần xử lý theo hợp đồng. |
| “Message rỗng giống message null đối với `toString()`.” | Không. Chuỗi rỗng vẫn làm `toString()` thêm `": "`. |
| “Có thể bắt lỗi bằng cách so nội dung message.” | Dễ hỏng do bản địa hóa, wording và phiên bản; dùng loại hoặc mã lỗi có cấu trúc. |
| “`getLocalizedMessage()` chắc chắn dùng được cho người dùng cuối.” | Không; thông điệp có thể chứa chi tiết nội bộ và có thể không được dịch. |
| “Tên lớp trong `toString()` luôn là `getSimpleName()`.” | Triển khai mặc định của `Throwable` dùng `getClass().getName()`. |
| “Một subclass không thể thay đổi các kết quả.” | Các phương thức có thể được override; cần xét hợp đồng lớp cụ thể. |

## 20. Tình huống luyện tập và đáp án

**Câu 1.** `new IllegalArgumentException("Sai tham số").getMessage()` trả gì?  
**Đáp án:** `"Sai tham số"`.

**Câu 2.** Cùng exception trên, `toString()` mặc định trả gì?  
**Đáp án:** `"java.lang.IllegalArgumentException: Sai tham số"`, vì class nằm trong `java.lang` và localized message mặc định bằng message.

**Câu 3.** `new IllegalArgumentException().getMessage()` có luôn là chuỗi rỗng không?  
**Đáp án:** Không. Với constructor mặc định của class này, message là `null`.

**Câu 4.** Với message `null`, `Throwable.toString()` mặc định có thêm dấu `": "` không?  
**Đáp án:** Không. Nó chỉ trả tên lớp.

**Câu 5.** Với message là `""`, `toString()` mặc định có thêm dấu `": "` không?  
**Đáp án:** Có, vì chuỗi rỗng **khác** `null`.

**Câu 6.** Nếu subclass override `getLocalizedMessage()` thành `"Không tìm thấy tệp"` mà không override `toString()`, `toString()` dùng giá trị nào?  
**Đáp án:** Dùng localized message của override đó và tên lớp runtime; không bắt buộc dùng detail message gốc.

**Câu 7.** Vì sao việc đổi `Locale.getDefault()` không làm mọi exception tự dịch message?  
**Đáp án:** Vì triển khai mặc định `getLocalizedMessage()` chỉ gọi `getMessage()`; không có cơ chế dịch tự động.

**Câu 8.** Một wrapper có cause là `IOException`. `getMessage()` có tự in toàn bộ cause không?  
**Đáp án:** Không nói chung. Constructor có `(String, Throwable)` thường giữ message riêng; cause truy qua `getCause()`. Một số constructor chỉ nhận cause đặt message bằng `cause.toString()`, nhưng đó là hành vi cụ thể.

**Câu 9.** `e.toString()` có in `getSuppressed()` không?  
**Đáp án:** Không trong triển khai mặc định của `Throwable`. Dùng `getSuppressed()` hoặc cơ chế in stack trace hỗ trợ suppressed.

**Câu 10.** Vì sao không dùng `e.getMessage().contains("not found")` để quyết định lỗi nào có thể retry?  
**Đáp án:** Message là văn bản có thể thay đổi/được dịch, không phải mã lỗi ổn định. Nên dùng loại exception, error code hoặc hợp đồng trả kết quả.

**Câu 11.** Muốn ghi log để điều tra một lỗi kỹ thuật, chỉ `getMessage()` có đủ không?  
**Đáp án:** Thường không. Cần xét loại lỗi, stack trace, cause và suppressed, cùng ngữ cảnh thích hợp.

**Câu 12.** `Throwable.getLocalizedMessage()` có thể trả `null` khi `getMessage()` không `null` không?  
**Đáp án:** Có nếu subclass chủ động override để trả `null`. Khi đó `Throwable.toString()` mặc định sẽ chỉ trả tên lớp.

**Câu 13.** Nếu subclass override `toString()` thì công thức tên lớp + localized message còn luôn đúng không?  
**Đáp án:** Không; subclass có thể định nghĩa định dạng khác.

**Câu 14.** `System.out.println(e)` với `e` là Throwable có giống `e.printStackTrace()` không?  
**Đáp án:** Không. `println(e)` in biểu diễn chuỗi ngắn, còn `printStackTrace()` in stack frames, có thể kèm cause/suppressed.

## 21. Checklist khi viết và dùng message của exception

Trước khi hoàn tất mã xử lý `Throwable`, kiểm tra:

1. **Có thể `null` không?** Không gọi trực tiếp `e.getMessage().trim()` hoặc `.length()` nếu chưa xét `null`.
2. **Cần thông điệp kỹ thuật hay bản địa hóa?** Không mặc định `getLocalizedMessage()` đã dịch.
3. **Cần biết loại lỗi không?** Chọn `getClass()` hoặc `toString()` tùy mục đích; không parse tên lớp từ văn bản nếu có thể tránh.
4. **Cần nguyên nhân gốc không?** Dùng `getCause()` và giữ cause khi wrapping.
5. **Có cleanup lỗi không?** Kiểm tra `getSuppressed()` khi phù hợp.
6. **Cần stack trace không?** Dùng logger nhận `Throwable` hoặc `getStackTrace()`/`printStackTrace()` theo ngữ cảnh.
7. **Có lộ dữ liệu nội bộ không?** Rà soát message trước khi log và đặc biệt trước khi trả ra giao diện.
8. **Có phụ thuộc văn bản message để ra quyết định không?** Chuyển sang mã lỗi/loại exception có cấu trúc.
9. **Có class tùy chỉnh override `getMessage()`, `getLocalizedMessage()` hoặc `toString()` không?** Kiểm tra tương tác giữa chúng, nhất là `toString()` dùng localized message.
10. **Có kiểm thử các biến thể message `null`, chuỗi rỗng và bản địa hóa không?** Những khác biệt nhỏ này có thể làm hỏng formatter hoặc log.
11. **Có yêu cầu hỗ trợ nhiều locale đồng thời không?** Tránh dựa vào locale toàn cục; cân nhắc dịch ở tầng giao diện.
12. **Code test có so nguyên văn stack trace không?** Tránh phụ thuộc số dòng và định dạng JVM; kiểm tra các thành phần có cấu trúc khi có thể.

## 22. Tổng kết

`getMessage()`, `getLocalizedMessage()` và `toString()` đều trả **chuỗi mô tả ngoại lệ**, nhưng khác hợp đồng:

- **`getMessage()`** là detail message, có thể `null`.
- **`getLocalizedMessage()`** là điểm mở rộng cho nội dung bản địa hóa, mặc định chỉ gọi `getMessage()`.
- **`toString()`** mặc định ghép **tên lớp runtime** với **localized message** nếu khác `null`, nhưng **không** tự chứa stack frames, cause hoặc suppressed.

Khi cần chẩn đoán nguyên nhân, đừng dừng ở chuỗi. Hãy xét **kiểu exception, `getCause()`, `getSuppressed()` và stack trace**. Khi cần phản hồi cho người dùng, hãy dùng thông điệp đã được rà soát, mã lỗi ổn định và cơ chế bản địa hóa phù hợp. Hiểu rõ sự khác nhau giữa ba phương thức giúp xử lý lỗi vừa chính xác về Java vừa hữu ích cho bảo trì và vận hành.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="throwable-internals-003">Stack trace được ghi nhận tại thời điểm nào</a>

<details>
<summary>Xem chi tiết</summary>

# Stack trace được ghi nhận tại thời điểm nào trong Java?

Khi một exception xảy ra trong Java, stack trace giúp trả lời câu hỏi **“đối tượng lỗi này đã được tạo ra trong đường gọi nào?”**. Nhưng một hiểu nhầm phổ biến là mỗi lệnh `throw` đều tự động chụp một stack trace mới tại vị trí ném. Với `Throwable` thông thường, **stack trace được ghi nhận khi đối tượng exception được khởi tạo** (thông qua cơ chế `fillInStackTrace()` trong quá trình khởi tạo), **không phải tự động làm mới ở mỗi lần `throw e`**.

Điều này giải thích tại sao một exception được tạo trong phương thức A, lưu lại rồi mới ném trong phương thức B thường có frame đầu là **A**, không phải B; cũng giải thích vì sao `throw e` trong `catch` thường giữ lại dấu vết lỗi gốc. Tuy nhiên, Java có API chủ động làm mới hoặc thay thế stack trace và JVM có những ngoại lệ/khả năng tối ưu đặc biệt. Vì vậy cần phân biệt **thời điểm ghi nhận**, **thời điểm ném**, **thời điểm in**, và **stack call hiện tại**.

Bài này làm rõ các thời điểm đó, cách kiểm chứng bằng Java 17+, tác động của rethrow, wrapping, `fillInStackTrace()`, `getStackTrace()`, `setStackTrace()`, suppressed exceptions, constructor đặc biệt và các giới hạn thực tế của dữ liệu stack trace.

## 1. Stack trace là gì?

**Stack trace** của một đối tượng `Throwable` là thông tin về các vị trí gọi phương thức liên quan đến thời điểm stack trace được ghi. Một stack trace thường là chuỗi các phần tử `StackTraceElement`:

```text
java.lang.IllegalStateException: Lỗi ví dụ
    at Example.create(Example.java:12)
    at Example.main(Example.java:20)
```

Đây là **hình thức minh họa**, không phải output cố định của chương trình cụ thể: tên tệp và số dòng thay đổi theo mã nguồn, cách biên dịch và môi trường.

Một `StackTraceElement` có thể cung cấp:

- Tên lớp: `getClassName()`.
- Tên phương thức: `getMethodName()`.
- Tên tệp nguồn: `getFileName()` nếu có.
- Số dòng: `getLineNumber()` nếu có, có thể là số âm biểu thị thiếu thông tin phù hợp.
- Trạng thái phương thức native: `isNativeMethod()`.
- Trong Java hiện đại còn có dữ liệu liên quan module và class loader trong một số trường hợp.

Thông thường, phần tử đầu tiên của mảng `getStackTrace()` biểu thị điểm ghi nhận gần nhất ở mức mã người dùng, sau khi JVM xử lý những frame khởi tạo nội bộ. Tuy nhiên, **không nên coi số lượng, thứ tự hay mức độ đầy đủ của mọi frame là bất biến trong mọi JVM, công cụ biên dịch và cấu hình**.

Stack trace không phải lịch sử tất cả câu lệnh đã chạy; nó là **ảnh chụp ngăn xếp lời gọi**, không phải bản ghi tuần tự toàn bộ sự kiện của chương trình.

## 2. Bốn thời điểm phải phân biệt

| Sự kiện | Ý nghĩa | Có mặc nhiên ghi stack trace mới vào cùng exception không? |
| --- | --- | --- |
| `new SomeException(...)` | Tạo object; constructor `Throwable` thông thường ghi thông tin ngăn xếp | **Có**, theo cơ chế khởi tạo thông thường và nếu writable stack trace được bật |
| `throw e;` | Ném object đang được `e` tham chiếu | **Không**; thông thường giữ stack trace đang có |
| `e.fillInStackTrace();` | Yêu cầu lấp lại stack trace theo ngăn xếp hiện tại | **Có**, nếu object cho phép writable stack trace |
| `e.printStackTrace();` hoặc `e.getStackTrace()` | In hoặc đọc dữ liệu stack trace đã ghi | **Không**; chúng không tự chụp lại đường gọi chỉ vì được truy cập |

Ngoài ra:

- `new WrapperException("...", cause)` tạo **object mới** nên wrapper có stack trace của riêng nó, được ghi tại **nơi tạo wrapper**.
- Exception gốc trong `cause` vẫn là object khác và có thể giữ stack trace được tạo trước đó.
- `e.setStackTrace(array)` **thay thế dữ liệu stack trace hiển thị** bằng dữ liệu do mã cung cấp nếu object cho phép; đây không phải thao tác tự chụp current call stack.
- Một số object `Throwable` có thể được tạo **không ghi stack trace** bằng constructor dành cho lớp con, và JVM có thể bỏ qua/giới hạn thông tin theo cơ chế đặc biệt.

Điểm mấu chốt: **ném exception** và **ghi nhận stack trace** là hai hoạt động khác nhau, dù chúng thường xảy ra gần nhau khi bạn viết `throw new ...`.

## 3. Tại sao `throw new ...` khiến ta tưởng stack trace được tạo ở lúc ném?

Xét:

```java
throw new IllegalArgumentException("Sai tham số");
```

Đây là một **câu lệnh minh họa**. Trong cùng biểu thức có **hai việc** diễn ra gần nhau:

1. `new IllegalArgumentException(...)` tạo object; đường khởi tạo `Throwable` thông thường ghi stack trace.
2. `throw` ném chính object vừa tạo.

Vì khởi tạo và ném nằm ở cùng vị trí mã nguồn, stack trace thường chỉ tới đúng dòng có `throw new`. Nhưng nguyên nhân là **đối tượng được tạo tại đó**, không phải vì Java luôn ghi trace tại mọi vị trí có từ khóa `throw`.

Để chứng minh, tách hai việc ra:

```java
IllegalArgumentException problem =
        new IllegalArgumentException("Sai tham số");
// Có thể có những thao tác khác.
throw problem;
```

Đây là **đoạn minh họa trong một phương thức**. Stack trace thông thường được ghi ở dòng tạo `problem`. Lệnh `throw problem` sau đó không tự làm mới trace.

Quan hệ này đặc biệt quan trọng khi exception được khởi tạo từ một helper, được giữ trong field hoặc được chuyển qua nhiều tầng trước khi ném.

## 4. Ví dụ chạy được: tạo ở A, ném ở B

Lưu chương trình sau thành **`CreationVsThrowDemo.java`**:

```java
public class CreationVsThrowDemo {
    static IllegalStateException makeProblem() {
        return new IllegalStateException("Không hợp lệ");
    }

    static void throwLater(IllegalStateException problem) {
        throw problem;
    }

    public static void main(String[] args) {
        IllegalStateException problem = makeProblem();
        System.out.println("Tạo xong exception");

        try {
            throwLater(problem);
        } catch (IllegalStateException e) {
            System.out.println("Bắt tại main");
            System.out.println("Frame đầu: "
                    + e.getStackTrace()[0].getMethodName());
            System.out.println("Cùng object: " + (e == problem));
        }
    }
}
```

Biên dịch/chạy bằng Java 17+:

```bash
javac -encoding UTF-8 --release 17 CreationVsThrowDemo.java
java CreationVsThrowDemo
```

**Stdout chính xác** trong môi trường JVM chuẩn có writable stack trace:

```text
Tạo xong exception
Bắt tại main
Frame đầu: makeProblem
Cùng object: true
```

Frame đầu là `makeProblem`, nơi `new IllegalStateException(...)` thực thi, **không phải `throwLater`**. Mặc dù lệnh `throw` thực sự nằm trong `throwLater`, không có bước chụp lại stack trace tự động chỉ vì một object có sẵn được ném.

Đối tượng bên ngoài handler vẫn là `problem`; phép so sánh `e == problem` là `true`. `getStackTrace()[0]` được dùng trong một ví dụ có trace thông thường; với loại Throwable vô hiệu hóa writable stack trace hoặc tối ưu đặc biệt, mảng có thể rỗng, nên mã chẩn đoán tổng quát phải kiểm tra độ dài.

## 5. Constructor `Throwable` liên quan `fillInStackTrace()` như thế nào?

Các constructor thông thường của `Throwable` ghi nhận thông tin ngăn xếp trong quá trình tạo đối tượng. API `fillInStackTrace()` cung cấp khả năng **lấp lại thông tin stack trace của object theo ngữ cảnh gọi hiện tại**.

Có thể hiểu:

```text
new MyException(...)
    |
    +-- chạy chuỗi constructor Throwable
    |       |
    |       +-- thông thường ghi stack trace hiện tại
    |
    +-- object được sử dụng sau đó
            |
            +-- throw object: không tự chụp lại
            +-- fillInStackTrace(): chủ động chụp lại (nếu writable)
```

Mô hình trên là mô tả **ngữ nghĩa Java**, không phải giả định mọi JVM có cùng mã native nội bộ. Ở mức ứng dụng, điều cần nhớ là thông tin stack trace thông thường gắn với **thời điểm tạo object** và có thể thay đổi khi chủ động gọi `fillInStackTrace()`.

Các lớp con có thể tùy biến việc khởi tạo và quản lý trace. Vì vậy khi xử lý exception do framework, thư viện hoặc JVM tạo, không nên khẳng định chắc chắn mọi object được ghi đúng một lần và không thể thay đổi.

## 6. Ném lại bằng `throw e` có giữ stack trace không?

Thông thường **có**. `throw e` trong handler ném lại **chính object** đã được bắt, nên nó giữ stack trace của object đó nếu không có mã nào sửa trace.

Ví dụ **`RethrowTraceDemo.java`**:

```java
public class RethrowTraceDemo {
    static void origin() {
        throw new IllegalArgumentException("Lỗi tại origin");
    }

    static void middle() {
        try {
            origin();
        } catch (IllegalArgumentException e) {
            System.out.println("Catch giữa: "
                    + e.getStackTrace()[0].getMethodName());
            throw e;
        }
    }

    public static void main(String[] args) {
        try {
            middle();
        } catch (IllegalArgumentException e) {
            System.out.println("Catch ngoài: "
                    + e.getStackTrace()[0].getMethodName());
            System.out.println("Message: " + e.getMessage());
        }
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 RethrowTraceDemo.java
java RethrowTraceDemo
```

**Stdout chính xác** trên Java 17 thông thường:

```text
Catch giữa: origin
Catch ngoài: origin
Message: Lỗi tại origin
```

Khi `origin()` gọi `new IllegalArgumentException(...)`, trace được ghi với frame đầu `origin`. Handler trong `middle` nhận object, đọc trace và thực hiện `throw e`. Handler ngoài nhận cùng object; frame đầu vẫn là `origin`.

**Không hiểu nhầm:** dù trace giữ lại vị trí ban đầu, exception thực sự đã đi qua `middle()`. Stack trace ban đầu đã ghi các frame đang hoạt động lúc khởi tạo; không có nghĩa là trace sẽ tiếp tục dài thêm từng lần rethrow. Muốn ghi nhận một quyết định rethrow quan trọng, có thể log có cấu trúc ở đúng ranh giới hoặc bổ sung exception wrapper với cause theo hợp đồng.

## 7. Chủ động gọi `fillInStackTrace()` sẽ làm thay đổi điểm ghi nhận

`Throwable.fillInStackTrace()` là phương thức public (đồng bộ theo khai báo API) trả về chính `Throwable` sau khi cập nhật thông tin stack trace nếu việc ghi trace được cho phép.

Chương trình **`RefillTraceDemo.java`**:

```java
public class RefillTraceDemo {
    static IllegalStateException createProblem() {
        return new IllegalStateException("Dữ liệu sai");
    }

    static void refreshAndThrow(IllegalStateException problem) {
        System.out.println("Trước refresh: "
                + problem.getStackTrace()[0].getMethodName());
        problem.fillInStackTrace();
        System.out.println("Sau refresh: "
                + problem.getStackTrace()[0].getMethodName());
        throw problem;
    }

    public static void main(String[] args) {
        IllegalStateException problem = createProblem();

        try {
            refreshAndThrow(problem);
        } catch (IllegalStateException e) {
            System.out.println("Caller thấy: "
                    + e.getStackTrace()[0].getMethodName());
            System.out.println("Cùng object: " + (e == problem));
        }
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 RefillTraceDemo.java
java RefillTraceDemo
```

**Stdout chính xác** khi writable trace hoạt động thông thường:

```text
Trước refresh: createProblem
Sau refresh: refreshAndThrow
Caller thấy: refreshAndThrow
Cùng object: true
```

`fillInStackTrace()` **không tạo exception mới**: identity vẫn giữ nguyên. Nhưng nó **thay thế thông tin stack trace gắn với object** bằng thông tin từ vị trí chủ động refresh, nên frame đầu đổi sang `refreshAndThrow`.

Cảnh báo thiết kế: gọi `fillInStackTrace()` có thể khiến vị trí lỗi ban đầu không còn hiển thị như trước, làm chẩn đoán khó hơn. Không nên gọi phương thức này chỉ để làm cho stack trace “trông gần hiện tại hơn” trừ khi có một lý do rõ ràng và thông tin gốc được bảo toàn bằng cơ chế phù hợp.

## 8. `throw e`, `throw new ...` và wrapping có trace khác nhau

Ba lựa chọn cần phân biệt:

| Cách làm | Object ném ra | Vị trí ghi trace thường thấy | Có giữ lỗi gốc ở đâu? |
| --- | --- | --- | --- |
| `throw e;` | Chính object `e` | Thời điểm object `e` được tạo/refresh | Bản thân object đó |
| `throw new IllegalStateException("Lỗi mới");` | Object mới | Nơi tạo object mới | Không giữ object cũ nếu không thiết lập liên kết |
| `throw new IllegalStateException("Lỗi dịch vụ", e);` | Wrapper mới | Nơi tạo wrapper | `wrapper.getCause() == e` |
| `e.fillInStackTrace(); throw e;` | Chính object `e` | Nơi gọi `fillInStackTrace()` | Có thể làm mất trace ban đầu của chính object |

**Wrapping** là cách giữ thêm bối cảnh tầng hiện tại mà không bắt buộc xóa stack trace của exception gốc. Nếu tầng repository ném lỗi I/O, tầng service có thể tạo ngoại lệ mô tả thất bại của service với `cause` là lỗi I/O.

Chữ ký checked/unchecked của wrapper cần tuân thủ hợp đồng Java; việc truyền `cause` không tự biến một checked exception thành unchecked trừ khi **kiểu wrapper** được chọn là unchecked.

## 9. Ví dụ wrapper: hai stack trace ở hai thời điểm

Lưu **`WrapperTraceDemo.java`**:

```java
public class WrapperTraceDemo {
    static void operation() {
        throw new IllegalArgumentException("Giá trị không hợp lệ");
    }

    static void service() {
        try {
            operation();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Dịch vụ thất bại", e);
        }
    }

    public static void main(String[] args) {
        try {
            service();
        } catch (IllegalStateException e) {
            System.out.println("Wrapper frame đầu: "
                    + e.getStackTrace()[0].getMethodName());
            System.out.println("Cause frame đầu: "
                    + e.getCause().getStackTrace()[0].getMethodName());
            System.out.println("Cause message: "
                    + e.getCause().getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 WrapperTraceDemo.java
java WrapperTraceDemo
```

**Stdout chính xác** với writable trace thông thường:

```text
Wrapper frame đầu: service
Cause frame đầu: operation
Cause message: Giá trị không hợp lệ
```

`IllegalArgumentException` được tạo trong `operation`, nên cause giữ trace tại đó. `IllegalStateException` wrapper được tạo trong `service`, nên trace của wrapper có frame đầu `service`. Quan hệ `getCause()` giúp thấy **cả bối cảnh tầng cao lẫn điểm phát sinh gốc**.

Đây là một lý do wrapping với cause thường tốt hơn gọi `fillInStackTrace()` trên exception cũ: wrapper giữ được hai góc nhìn mà không cần ghi đè trace gốc.

## 10. `getStackTrace()` không tự cập nhật theo call stack hiện tại

`getStackTrace()` trả về một mảng `StackTraceElement[]` thể hiện dữ liệu stack trace **đang được lưu trong object**. Gọi phương thức từ caller A, B hoặc C không tự làm cho mảng biến thành ảnh chụp tại A, B hoặc C.

Chẳng hạn:

```java
static String firstFrame(Throwable e) {
    StackTraceElement[] frames = e.getStackTrace();
    return frames.length == 0
            ? "(không có frame)"
            : frames[0].getMethodName();
}
```

Đây là **phương thức minh họa** có thể đặt trong một lớp Java. Nó đọc frame đầu đã có, đồng thời xử lý an toàn trường hợp mảng rỗng. Nó không gọi `fillInStackTrace()`.

Hai lưu ý:

1. **Không chắc chắn mảng luôn có phần tử.** Throwable có thể được tạo với writable trace bị tắt, hoặc trong một số trường hợp JVM có thể không cung cấp các frame mong đợi.
2. **Không nên coi số dòng và tên tệp là khóa dữ liệu ổn định.** Chúng có thể đổi khi sửa source, thiếu debug information, dùng native code, build khác hoặc tối ưu runtime.

Việc gọi `getStackTrace()` ở thời điểm muộn không giúp “quay phim” những call stack đã trôi qua; nó chỉ đọc thông tin object đang lưu.

## 11. `getStackTrace()` trả một mảng có thể chỉnh, nhưng không được sửa nội dung object qua mảng đó

`Throwable.getStackTrace()` trả về **bản sao mảng** (`StackTraceElement[]`), không phải một mảng có thể dùng để sửa trực tiếp trace bên trong object bằng cách thay phần tử.

Ví dụ **`StackTraceCopyDemo.java`**:

```java
public class StackTraceCopyDemo {
    static IllegalStateException create() {
        return new IllegalStateException("Ví dụ");
    }

    public static void main(String[] args) {
        IllegalStateException e = create();

        StackTraceElement[] snapshot = e.getStackTrace();
        String originalMethod = snapshot[0].getMethodName();

        snapshot[0] = new StackTraceElement(
                "FakeClass", "fakeMethod", "Fake.java", 1);

        System.out.println("Snapshot đã sửa: "
                + snapshot[0].getMethodName());
        System.out.println("Object vẫn giữ: "
                + e.getStackTrace()[0].getMethodName());
        System.out.println("Ban đầu: " + originalMethod);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 StackTraceCopyDemo.java
java StackTraceCopyDemo
```

**Stdout chính xác** với trace bình thường:

```text
Snapshot đã sửa: fakeMethod
Object vẫn giữ: create
Ban đầu: create
```

Bạn đã thay phần tử của **mảng local `snapshot`**, không thay thông tin trace trong object. Nếu muốn can thiệp chính thức vào trace của exception, API cung cấp `setStackTrace(...)`; tuy nhiên việc đặt frame giả như vậy không phải hành vi nên làm trong code nghiệp vụ.

`StackTraceElement` là đối tượng biểu diễn một frame; sử dụng chúng cho quan sát/chẩn đoán khác với việc cố sửa lịch sử để làm mất dấu nguồn lỗi.

## 12. `setStackTrace()` thay dữ liệu, không chụp current stack

`Throwable.setStackTrace(StackTraceElement[] stackTrace)` có thể **thay thế** thông tin trace của một Throwable hỗ trợ writable stack trace bằng mảng `StackTraceElement` do bạn cung cấp. Không có bước tự chụp đường gọi hiện tại chỉ vì gọi `setStackTrace`.

Ví dụ minh họa:

```java
Throwable e = new IllegalStateException("Lỗi");
e.setStackTrace(new StackTraceElement[] {
        new StackTraceElement("Example", "custom", "Example.java", 7)
});
```

Đây là **khung minh họa**. Sau khi đặt trace nhân tạo, nếu object cho phép writable stack trace, `e.getStackTrace()[0].getMethodName()` sẽ là `"custom"`; điều đó **không** có nghĩa phương thức Java tên `custom` thực sự đã được gọi tại thời điểm lỗi.

API sẽ kiểm tra tính hợp lệ của mảng được truyền, ví dụ không chấp nhận mảng `null` hoặc phần tử `null`. Nó sao chép mảng để tránh việc người gọi thay đổi dữ liệu đã đặt chỉ bằng cách sửa mảng bên ngoài.

Về thực hành, **đừng làm giả trace để “sửa lỗi”**. Khi cần thêm ngữ cảnh, hãy dùng message, error code, wrapper với cause, hoặc log có cấu trúc; khi cần biết call stack hiện tại, sử dụng API quan sát stack hiện tại thay vì viết frame giả vào Throwable.

## 13. Phân biệt stack trace đã ghi với call stack đang chạy

Đối tượng `Throwable` chứa **dữ liệu chẩn đoán được ghi ở một thời điểm**. Call stack hiện tại của thread là một cấu trúc đang thay đổi liên tục theo lời gọi và lệnh trả về.

Các API quan sát stack hiện tại gồm `Thread.currentThread().getStackTrace()` hoặc `StackWalker` (Java 9+). Chúng phục vụ mục đích khác với việc đọc trace cũ từ exception.

Ví dụ **`LiveVsStoredStackDemo.java`**:

```java
public class LiveVsStoredStackDemo {
    static Exception make() {
        return new Exception("Tạo trong make");
    }

    static void inspect(Exception e) {
        System.out.println("Lỗi được tạo tại: "
                + e.getStackTrace()[0].getMethodName());

        boolean hasInspect = StackWalker.getInstance()
                .walk(stream -> stream.anyMatch(
                        frame -> frame.getMethodName().equals("inspect")));
        System.out.println("Call stack hiện tại có inspect: " + hasInspect);
    }

    public static void main(String[] args) {
        Exception e = make();
        inspect(e);
    }
}
```

Chạy Java 17:

```bash
javac -encoding UTF-8 --release 17 LiveVsStoredStackDemo.java
java LiveVsStoredStackDemo
```

**Stdout chính xác** trên Java 17 thông thường:

```text
Lỗi được tạo tại: make
Call stack hiện tại có inspect: true
```

`e.getStackTrace()` vẫn cho biết `make`, thời điểm tạo exception. `StackWalker` quan sát ngăn xếp **đang hoạt động** khi lời gọi `inspect` diễn ra, nên có frame của `inspect`.

Không nhầm hai kiểu thông tin: nếu muốn biết exception ban đầu phát sinh từ đâu, hãy đọc trace gắn với exception; nếu muốn biết code đang chạy qua những lời gọi nào **ở hiện tại**, hãy dùng API cho current stack.

## 14. `printStackTrace()` diễn ra muộn nhưng không có nghĩa trace được ghi muộn

Có thể tạo exception ở thời điểm T1, ném ở T2, bắt ở T3, rồi in ở T4:

```java
Throwable e = new IllegalStateException("Lỗi ví dụ"); // T1
// ...
throw e; // T2
```

Đây là **mã khung**. Nếu caller bắt được và thực hiện `e.printStackTrace()` tại T4, việc in diễn ra T4, nhưng stack trace được trình bày thường vẫn là dữ liệu đã ghi ở T1, trừ khi có API hoặc cơ chế khác sửa dữ liệu trong khoảng đó.

Phân biệt:

- `getMessage()`: trả thông điệp, không phải trace.
- `toString()`: mô tả ngắn thường gồm tên lớp và message, không phải danh sách frame.
- `getStackTrace()`: trả dữ liệu frame trong object.
- `printStackTrace()`: thực hiện **hành động in** thông tin exception và trace, có thể kèm cause và suppressed.
- `fillInStackTrace()`: **thay đổi dữ liệu trace**, không phải lệnh in.
- `setStackTrace()`: **gán dữ liệu frame do caller cung cấp**.

Theo API chuẩn, `printStackTrace()` không đối số in ra `System.err`, không phải `System.out`. Khi lệnh in stdout và stderr xen kẽ, thứ tự hiển thị thực tế giữa hai stream có thể khác nhau; đừng kiểm thử dựa trên một thứ tự gộp không được đảm bảo.

## 15. Cause có trace riêng; suppressed exceptions cũng có trace riêng

Một exception có thể tham chiếu **cause** và danh sách **suppressed exceptions**:

```java
IllegalArgumentException root =
        new IllegalArgumentException("Lỗi chính");

IllegalStateException wrapped =
        new IllegalStateException("Lỗi dịch vụ", root);

wrapped.addSuppressed(
        new IllegalStateException("Không cleanup được"));
```

Đây là **đoạn mã minh họa trong một phương thức**, không có `main`. Ba `Throwable` là **ba object**. Mỗi object được tạo ở một thời điểm tương ứng nên mỗi object thông thường mang trace riêng. Gắn object A làm cause hoặc suppressed của B **không tự chụp lại stack trace của A**.

Với `try-with-resources`:

1. Công việc chính ném exception A.
2. Khi rời khối, `close()` có thể ném exception B.
3. Java giữ A là **primary** và gắn B vào `A.getSuppressed()` khi thích hợp.
4. A và B có trace riêng: A gắn với nơi A được tạo, B gắn với nơi B được tạo/điền trace.

Ví dụ **`SuppressedFramesDemo.java`**:

```java
public class SuppressedFramesDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() {
            throw new IllegalStateException("Lỗi close");
        }
    }

    static void work() {
        throw new IllegalArgumentException("Lỗi work");
    }

    public static void main(String[] args) {
        try (Resource resource = new Resource()) {
            work();
        } catch (IllegalArgumentException e) {
            System.out.println("Primary: "
                    + e.getStackTrace()[0].getMethodName());
            Throwable closing = e.getSuppressed()[0];
            System.out.println("Suppressed: "
                    + closing.getStackTrace()[0].getMethodName());
        }
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 SuppressedFramesDemo.java
java SuppressedFramesDemo
```

**Stdout chính xác** trên Java 17 thông thường:

```text
Primary: work
Suppressed: close
```

Trace của A bắt đầu ở `work`; trace của B bắt đầu ở `close`. Việc A chứa B trong danh sách suppressed không gộp hai mảng frame thành một ảnh chụp và không làm B đổi thành exception tạo ở `work`.

Trong chương trình tổng quát, phải kiểm tra `getSuppressed().length` trước khi lấy phần tử `[0]`. Ví dụ này cố ý thiết kế resource luôn ném khi đóng và thân luôn ném, nên kết quả có một suppressed exception.

## 16. Constructor có thể tắt writable stack trace

Từ Java 7, `Throwable` có constructor dành cho lớp con:

```java
protected Throwable(String message,
                    Throwable cause,
                    boolean enableSuppression,
                    boolean writableStackTrace)
```

Tham số cuối, `writableStackTrace`, kiểm soát khả năng ghi stack trace của object. Khi đặt `false`, object được tạo theo chế độ **không ghi stack trace**, và các thao tác `fillInStackTrace()` hoặc `setStackTrace()` trên object đó không hoạt động như cách thông thường. `getStackTrace()` trả mảng rỗng.

Ví dụ chạy được **`NoWritableTraceDemo.java`**:

```java
public class NoWritableTraceDemo {
    static class SilentTraceException extends Exception {
        SilentTraceException(String message) {
            super(message, null, true, false);
        }
    }

    public static void main(String[] args) {
        SilentTraceException problem =
                new SilentTraceException("Không ghi stack");

        System.out.println("Ban đầu: "
                + problem.getStackTrace().length);
        problem.fillInStackTrace();
        System.out.println("Sau fill: "
                + problem.getStackTrace().length);

        try {
            throw problem;
        } catch (SilentTraceException e) {
            System.out.println("Sau throw: "
                    + e.getStackTrace().length);
            System.out.println("Message: " + e.getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 NoWritableTraceDemo.java
java NoWritableTraceDemo
```

**Stdout chính xác:**

```text
Ban đầu: 0
Sau fill: 0
Sau throw: 0
Message: Không ghi stack
```

Đây là minh chứng trực tiếp rằng **không phải mọi `Throwable` đều có frame để đọc**, kể cả khi object đã được tạo, ném và bắt. Mã `e.getStackTrace()[0]` không an toàn nếu áp dụng tùy tiện cho mọi exception.

Tắt writable stack trace có thể được sử dụng có chủ đích trong một số exception được tạo rất thường xuyên để giảm overhead, nhưng đánh đổi khả năng chẩn đoán. Đừng tắt stack trace chỉ để che một bug hoặc vì muốn log ngắn đi.

## 17. `fillInStackTrace()` không bắt buộc mọi subclass phải hành xử giống hệt

`Throwable.fillInStackTrace()` có thể được **override** trong lớp con không final. Ngoài constructor có tham số `writableStackTrace`, một subclass có thể điều chỉnh chính sách thu thập dấu vết của nó.

Hệ quả:

- Không giả định mọi exception của thư viện đều có stack trace được ghi tại constructor theo cùng cách.
- Không giả định gọi `fillInStackTrace()` sẽ luôn tạo frame nếu object hoặc subclass đã được thiết kế không giữ trace.
- Không thao tác trực tiếp với các chi tiết nội bộ của `Throwable`; dùng API công khai và kiểm tra khả năng có mảng rỗng.
- Nếu tạo custom exception có hành vi đặc biệt, hãy tài liệu hóa tác động của nó tới logging, debug và các công cụ quan sát lỗi.

Với những ngoại lệ thông thường kế thừa `RuntimeException` hoặc `Exception` mà không override cơ chế này và không tắt writable trace, quy tắc học tập hiệu quả vẫn là: **constructor ghi trace; `throw e` giữ nguyên; `fillInStackTrace()` làm mới**.

## 18. Các trường hợp JVM đặc biệt

Ngoại lệ do JVM tạo ra có thể khác với `new` do mã ứng dụng gọi. Trên một số JVM/phiên bản/cấu hình, có những tối ưu để **bỏ qua hoặc rút gọn stack trace** của một số exception xảy ra lặp đi lặp lại ở vị trí thường gặp. Trong HotSpot có khái niệm tối ưu thường gọi là **fast-throw** đối với một số implicit exceptions; hành vi thực tế phụ thuộc JVM, cờ vận hành và hoàn cảnh.

Bởi vậy:

- Không dựa vào giả định mọi `NullPointerException` do JVM tự ném đều có đầy đủ stack frames.
- Không dùng một lần chạy để suy luận cứng về tối ưu stack trace của tất cả JVM.
- Không phụ thuộc vào số frame hoặc số dòng cố định cho hệ thống giám sát.
- Một exception `new IllegalArgumentException(...)` thông thường và một implicit VM-thrown exception không nhất thiết được tạo/ghi trace theo cùng đường nội bộ.
- Khi cần phân tích hiệu năng hoặc sai khác theo JVM, hãy xem thông tin của phiên bản JVM thật đang chạy và cấu hình logging/profiling, thay vì thay đổi code nghiệp vụ dựa trên giả thuyết.

**Cảnh báo:** “Stack trace thiếu frame” không tự chứng minh exception bị che giấu hoặc giả mạo. Nó có thể xuất phát từ chế độ tạo Throwable, từ tối ưu, thiếu thông tin debug hoặc từ việc có code sửa trace.

## 19. Vì sao không nên tái sử dụng cùng một instance exception?

Về mặt ngôn ngữ, bạn có thể tạo một object exception rồi ném lại nhiều lần:

```java
static final IllegalStateException SHARED =
        new IllegalStateException("Lỗi dùng chung");

static void run() {
    throw SHARED;
}
```

Đây là **lớp/phương thức minh họa chưa đầy đủ**. Vấn đề: `SHARED` được tạo khi class khởi tạo, nên stack trace có thể phản ánh **điểm khởi tạo static field**, không phản ánh lần gọi `run()` cụ thể. Nếu cùng object được ném ở nhiều luồng hoặc nhiều yêu cầu, thông tin lỗi không phân biệt được các lần phát sinh theo call stack mới.

Điều này còn có thể gây rủi ro khi code khác thay đổi suppressed exceptions hoặc stack trace của cùng object và nhiều luồng đọc chung.

Trong đa số ứng dụng:

- **Tạo exception mới cho mỗi lần thất bại thực sự** để có dấu vết phù hợp.
- Để lỗi lan truyền tự nhiên bằng `throw e` khi cần bảo toàn object gốc.
- Nếu cần thêm ngữ cảnh ở tầng khác, cân nhắc wrapper có `cause` thay vì dùng lại một exception static.
- Không biến exception object thành nơi tích lũy trạng thái của nhiều request độc lập.

Một số framework hoặc thư viện tối ưu đặc thù có thể cố ý tái sử dụng exception không stack trace; đó là lựa chọn cần cân nhắc về chẩn đoán và hiệu năng, không phải mẫu mặc định cho code nghiệp vụ.

## 20. Kiểm tra thời điểm ghi trace bằng test ổn định

Nếu viết bài test về stack trace, tránh kiểm tra một chuỗi đầy đủ chứa số dòng và tên tệp, vì chi tiết dễ thay đổi. Thay vào đó, với một exception được tạo chủ động và có writable trace, kiểm tra **tên phương thức đầu tiên** hoặc **identity** của object theo hợp đồng của ví dụ.

Ví dụ **`TraceAssertionsDemo.java`**:

```java
public class TraceAssertionsDemo {
    static IllegalStateException make() {
        return new IllegalStateException("Lỗi");
    }

    static void relay(IllegalStateException e) {
        throw e;
    }

    public static void main(String[] args) {
        IllegalStateException problem = make();

        try {
            relay(problem);
            throw new AssertionError("Đã không ném lỗi");
        } catch (IllegalStateException e) {
            if (e != problem) {
                throw new AssertionError("Đã đổi object");
            }

            StackTraceElement[] frames = e.getStackTrace();
            if (frames.length == 0
                    || !"make".equals(frames[0].getMethodName())) {
                throw new AssertionError("Sai thời điểm ghi trace");
            }

            System.out.println("PASS: tạo ở make, rethrow không làm mới");
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 TraceAssertionsDemo.java
java TraceAssertionsDemo
```

**Stdout chính xác** với Throwable thông thường:

```text
PASS: tạo ở make, rethrow không làm mới
```

Bài test sử dụng `throw new AssertionError(...)` để biểu diễn kết quả sai nên **không phụ thuộc cờ `-ea`** của lệnh `assert`. Việc kiểm tra mảng frame rỗng khiến test thất bại có thông báo rõ ràng khi gặp môi trường không phù hợp giả thiết writable trace của ví dụ.

Trong test tích hợp tổng quát trên nhiều JVM, hãy cân nhắc mức độ kiểm chứng cần thiết: không nên phụ thuộc frame đầu cụ thể của VM-thrown exception hoặc tính ổn định của format `printStackTrace()`.

## 21. Những nhầm lẫn phổ biến

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “Mỗi lần `throw e` đều ghi stack trace ở dòng `throw`.” | Với Throwable thông thường, `throw e` ném object đã có, không tự làm mới trace. |
| “`throw new E(...)` chứng minh `throw` tự chụp trace.” | Trong biểu thức đó, việc `new` tạo Throwable ngay trước `throw` là nguyên nhân thông thường. |
| “`getStackTrace()` ghi lại call stack tại lúc đọc.” | Nó trả dữ liệu trace trong object; không tự gọi `fillInStackTrace()`. |
| “`printStackTrace()` tạo trace mới.” | Nó in thông tin đang có, không phải thao tác làm mới. |
| “`fillInStackTrace()` tạo object mới.” | Nó trả về chính Throwable và có thể cập nhật trace của object hiện tại. |
| “Rethrow làm mất trace gốc.” | `throw e` thông thường giữ trace gốc; wrapper mới có trace riêng và có thể giữ nguyên nhân. |
| “`setStackTrace` là ảnh chụp call stack hiện tại.” | Nó đặt mảng frame do caller cung cấp, không tự chụp current stack. |
| “Sửa phần tử của mảng `getStackTrace()` sửa luôn exception.” | API trả bản sao mảng; muốn đặt trace dùng `setStackTrace`. |
| “Mọi Throwable luôn có ít nhất một frame.” | Có thể rỗng do writableStackTrace=false hoặc hành vi JVM đặc biệt. |
| “Stack trace là lịch sử mọi method từng chạy.” | Nó là dữ liệu tại một thời điểm, không phải event log theo thời gian. |
| “Cause và suppressed dùng chung trace của wrapper.” | Mỗi object có trace riêng, nếu có. |
| “Tên tệp và số dòng trong stack trace luôn cố định.” | Chúng có thể đổi do source, thông tin debug, JVM và các cơ chế khác. |
| “Tạo exception trước để tái sử dụng giúp biết mọi vị trí ném.” | Trace có thể chỉ vị trí **tạo ban đầu**, gây chẩn đoán sai về vị trí ném sau. |
| “Tắt stack trace không ảnh hưởng xử lý lỗi.” | Có thể vẫn ném/bắt object, nhưng mất thông tin chẩn đoán quan trọng. |

## 22. Câu hỏi tự kiểm tra

**Câu 1.** `Exception e = new Exception("x");` ở phương thức A, rồi `throw e` ở B. Stack trace thường bắt đầu từ đâu?  
**Đáp án:** Từ thời điểm object được tạo/điền trace, thường là phương thức A, không tự đổi thành B vì `throw e`.

**Câu 2.** `catch (Exception e) { throw e; }` có tự khởi tạo một exception mới không?  
**Đáp án:** Không. Nó ném lại cùng object nếu `e` không bị gán sang object khác.

**Câu 3.** `e.fillInStackTrace()` khác `e.printStackTrace()` thế nào?  
**Đáp án:** `fillInStackTrace()` yêu cầu cập nhật stack trace của object (nếu hỗ trợ), còn `printStackTrace()` in thông tin trace hiện tại ra stream.

**Câu 4.** Lệnh `new ServiceException("...", cause)` có làm trace của `cause` trở thành trace của wrapper không?  
**Đáp án:** Không. Wrapper là object mới có trace riêng tại nơi tạo; `cause` giữ trace của nó.

**Câu 5.** `getStackTrace()` trả mảng rỗng thì đọc `[0]` được không?  
**Đáp án:** Không. Cần kiểm tra `length > 0`; Throwable có thể không lưu frame.

**Câu 6.** Sau khi lấy `StackTraceElement[] a = e.getStackTrace()`, thay `a[0]` có tự thay đổi object không?  
**Đáp án:** Không. `getStackTrace()` trả bản sao của mảng stack trace.

**Câu 7.** `setStackTrace(...)` có phải cách an toàn để lấy đường gọi hiện tại không?  
**Đáp án:** Không. Nó thay thông tin bằng mảng được truyền vào; để quan sát current stack, có `StackWalker` hoặc API thread phù hợp.

**Câu 8.** Với `try-with-resources`, exception ở `close()` có stack trace riêng không?  
**Đáp án:** Thông thường có nếu là Throwable được tạo tại `close()`. Khi lỗi công việc chính cũng xảy ra, lỗi đóng được giữ ở `getSuppressed()` của primary.

**Câu 9.** Có thể tạo Throwable nhưng không thu stack trace không?  
**Đáp án:** Có. Lớp con có thể dùng constructor với `writableStackTrace=false`; một số cơ chế đặc biệt của JVM cũng có thể làm trace trống hoặc thiếu frame.

**Câu 10.** Nếu cùng một exception instance được ném lại ở hai request, hai lần ném có tự tạo hai dấu vết riêng không?  
**Đáp án:** Không. Nếu không refresh hoặc thay trace, cùng object giữ thông tin cũ. Điều này khiến việc tái sử dụng một exception instance chung thường không thích hợp cho chẩn đoán.

**Câu 11.** Vì sao kiểm thử exact `Exception.java:123` là mong manh?  
**Đáp án:** Số dòng, tên tệp, debug info và cách runtime trình bày trace có thể thay đổi. Hãy xác nhận đặc điểm ổn định hơn theo phạm vi test.

**Câu 12.** `Thread.currentThread().getStackTrace()` có phải chính mảng của một exception đã tạo trước không?  
**Đáp án:** Không. Nó quan sát ngăn xếp thực thi của thread; exception lưu thông tin riêng từ thời điểm capture.

## 23. Checklist thực hành

Khi cần giải thích một stack trace trong dự án Java:

1. Xác định exception được **tạo** tại đâu; phân biệt `throw new ...` với `throw existing`.
2. Kiểm tra có **rethrow** qua `catch` hay các tầng chuyển tiếp không.
3. Tìm xem mã có gọi `fillInStackTrace()` hoặc `setStackTrace()` làm thay đổi dữ liệu chẩn đoán không.
4. Nếu có **wrapper**, kiểm tra `getCause()` để truy vị trí tạo exception gốc.
5. Nếu có **try-with-resources**, kiểm tra `getSuppressed()` để tìm lỗi cleanup và trace riêng của nó.
6. Không bỏ qua khả năng **mảng trace rỗng**, thiếu frame hoặc writable stack trace bị vô hiệu hóa.
7. Không nhầm `printStackTrace()` (thời điểm in) với thời điểm ghi thông tin vào object.
8. Không dựa trên wording message hoặc số dòng làm cơ sở logic nghiệp vụ.
9. Nếu cần thêm ngữ cảnh, ưu tiên logging rõ ràng và wrapper với `cause` thay vì ghi đè trace gốc.
10. Nếu cần current stack, cân nhắc `StackWalker` hoặc API thread thay vì đọc exception được tạo trước.
11. Nếu lỗi do JVM tự phát sinh, thận trọng với khả năng tối ưu và cấu hình của JVM.
12. Trong kiểm thử, khẳng định hành vi có ý nghĩa và cân nhắc độ ổn định giữa các JVM, phiên bản và chế độ chạy.

## 24. Ghi nhớ trọng tâm

**Với `Throwable` thông thường, stack trace được ghi khi đối tượng exception được khởi tạo, không tự được ghi lại mỗi khi có `throw e`.** Lệnh `throw new E(...)` trông như ghi tại chỗ ném vì object được tạo ngay trong biểu thức ném. `throw e` giữ object và trace hiện có; `fillInStackTrace()` có thể chủ động cập nhật trace, còn wrapper mới có trace tại nơi tạo và giữ exception cũ thông qua `cause`.

`getStackTrace()` và `printStackTrace()` là hoạt động **đọc/in**, không phải cơ chế chụp lại stack hiện tại. Khi trace bị vô hiệu hóa, được sửa hoặc chịu tối ưu JVM, nó có thể rỗng hoặc thiếu những frame người lập trình mong đợi.

Để chẩn đoán chính xác, luôn tách ba câu hỏi: **Exception được tạo/ghi trace lúc nào? Object đã được ném hoặc ném lại ở đâu? Thông tin được đọc hoặc in lúc nào?** Trả lời đúng ba câu này giúp nhận diện nguyên nhân gốc mà không nhầm vị trí quan sát với vị trí phát sinh lỗi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="throwable-internals-004">printStackTrace và cách biểu diễn đầy đủ một ngoại lệ</a>

<details>
<summary>Xem chi tiết</summary>

# `printStackTrace` và cách biểu diễn đầy đủ một ngoại lệ trong Java

Khi Java ném ngoại lệ, việc chỉ nhìn một câu thông báo như `"read failed"` thường không đủ để chẩn đoán. Đối tượng **`Throwable`** còn mang loại lỗi, *stack trace* (chuỗi lời gọi), *cause* (nguyên nhân bên dưới) và *suppressed exceptions* (các lỗi phụ bị giữ lại, thường phát sinh lúc đóng tài nguyên). Phương thức **`printStackTrace()`** dùng để in thông tin chẩn đoán từ đối tượng đó.

Điểm quan trọng nhất:

> `getMessage()` chỉ cung cấp thông điệp; `toString()` thường thêm tên lớp; `printStackTrace()` hiển thị thông tin lỗi cùng các khung gọi, chuỗi nguyên nhân và ngoại lệ bị suppressed theo định dạng stack trace. Không nên nhầm ba cách biểu diễn này là tương đương.

Bài học trình bày cách đọc đầy đủ một ngoại lệ, cách lấy hoặc ghi lại stack trace, các giới hạn của nó, và cách dùng an toàn trong chương trình Java 17+.

## 1. Một đối tượng `Throwable` lưu những gì?

Gốc của hệ phân cấp ngoại lệ Java là `java.lang.Throwable`. Hai nhánh trực tiếp quan trọng là `Exception` và `Error`; `RuntimeException` là lớp con của `Exception`. `Throwable` cung cấp các API chung để kiểm tra thông tin lỗi.

| Thành phần | API thông dụng | Thông tin nhận được |
| --- | --- | --- |
| Kiểu ngoại lệ | `ex.getClass()`, `ex.getClass().getName()` | Tên lớp thực tế của đối tượng được ném |
| Thông điệp | `ex.getMessage()` | Chuỗi mô tả lỗi, có thể là `null` |
| Thông điệp bản địa hóa | `ex.getLocalizedMessage()` | Theo mặc định thường giống `getMessage()`, có thể khác nếu lớp ghi đè |
| Tóm tắt lỗi | `ex.toString()` | Thường là tên lớp, kèm thông điệp nếu có |
| Nguyên nhân | `ex.getCause()` | Một `Throwable` khác hoặc `null` |
| Danh sách lỗi phụ | `ex.getSuppressed()` | Mảng `Throwable[]`, có thể rỗng |
| Các khung gọi | `ex.getStackTrace()` | Mảng các `StackTraceElement` mô tả những vị trí ghi nhận stack |
| Hiển thị đầy đủ | `ex.printStackTrace()` | In dạng người đọc được ra `System.err` |
| Hiển thị tới luồng tùy chọn | `ex.printStackTrace(PrintStream/PrintWriter)` | In stack trace tới đích được cung cấp |

Các khái niệm ở bảng không có nghĩa rằng mọi đối tượng lúc nào cũng có đầy đủ message, cause, suppressed hay stack frames. Chẳng hạn, `getMessage()` và `getCause()` có thể trả về `null`, còn `getSuppressed()` có thể là mảng rỗng.

### Cùng một lỗi nhưng các mức biểu diễn khác nhau

Giả sử:

```java
IllegalArgumentException ex =
        new IllegalArgumentException("quantity must be positive");
```

Đây là một câu lệnh minh họa cần nằm trong một phương thức/lớp. Các cách quan sát:

```java
System.out.println(ex.getMessage());
System.out.println(ex.toString());
```

**Stdout:**

```text
quantity must be positive
java.lang.IllegalArgumentException: quantity must be positive
```

Nếu viết `ex.printStackTrace();`, Java thường in thêm những dòng `at ...` ra **stderr**, không phải stdout. Vị trí và số dòng cụ thể phụ thuộc tệp nguồn, đường gọi và runtime; do đó không nên coi toàn bộ bản in là một chuỗi cố định có thể hard-code trong tài liệu.

## 2. `printStackTrace()` thực sự làm gì?

`Throwable` có ba cách gọi phổ biến:

```java
ex.printStackTrace();
ex.printStackTrace(System.err);
ex.printStackTrace(new java.io.PrintWriter(writer));
```

Đây là các **câu lệnh minh họa**, với `ex` là `Throwable` và `writer` là một `java.io.Writer` hợp lệ.

- **`printStackTrace()`:** in stack trace của đối tượng tới **`System.err`** — luồng lỗi chuẩn.
- **`printStackTrace(PrintStream stream)`:** in ra `PrintStream` được truyền, chẳng hạn `System.out` hoặc `System.err`.
- **`printStackTrace(PrintWriter writer)`:** in ra `PrintWriter` do caller quản lý, thuận tiện khi cần ghi vào một buffer hoặc writer.

Cả ba đều **trả về `void`**. `printStackTrace()` không trả lại `String`, không khôi phục chương trình khỏi lỗi, và **không ném lại ngoại lệ** đang được in.

### Phân biệt stdout với stderr

`System.out` dành cho đầu ra chuẩn; `System.err` dành cho đầu ra lỗi. Một lệnh:

```java
System.out.println("before");
ex.printStackTrace();
System.out.println("after");
```

có thể tạo hai dòng stdout và một stack trace trên stderr. Khi console hoặc môi trường thực thi gộp hai luồng, thứ tự hiển thị có thể phụ thuộc cơ chế thu gom luồng. Vì vậy:

- Không dùng sự xen kẽ giữa stdout và stderr làm bằng chứng chắc chắn về thứ tự xử lý.
- Trong ví dụ cần **stdout chính xác**, nên in dữ liệu tóm tắt ổn định thay vì đoán số dòng/đường dẫn trong stack trace.
- `printStackTrace()` phù hợp để điều tra nhanh, nhưng ứng dụng production thường nên dùng logger có khả năng nhận `Throwable` và quản lý nơi lưu, mức độ, ngữ cảnh.

**Một stack trace được in** và **một ngoại lệ chưa được bắt** là hai khái niệm khác nhau. Có thể gọi `printStackTrace()` trong `catch` rồi tiếp tục chương trình; ngược lại, một ngoại lệ có thể không được bắt và cơ chế xử lý mặc định của thread mới là nơi in thông tin lỗi.

## 3. Hình dạng thường gặp của một stack trace

Một ví dụ mang tính **minh họa cấu trúc**, không phải transcript số dòng cố định:

```text
java.lang.IllegalStateException: cannot complete checkout
    at example.CheckoutService.checkout(CheckoutService.java:<line>)
    at example.Main.main(Main.java:<line>)
    Suppressed: java.lang.IllegalArgumentException: close warning
        at example.Resource.close(Resource.java:<line>)
        ... <frames omitted>
Caused by: java.io.IOException: storage offline
    at example.Storage.read(Storage.java:<line>)
    ... <frames omitted>
```

Những mục quan trọng:

1. **Dòng đầu:** `Throwable.toString()` — tên lớp ngoại lệ và thông điệp nếu có.
2. **`at package.Class.method(File.java:line)`:** một vị trí trong stack trace. Các khung thường được liệt kê từ vị trí gần nguồn lỗi nhất tới caller.
3. **`Suppressed: ...`:** một ngoại lệ phụ gắn với ngoại lệ chính, thường từ thao tác đóng tài nguyên.
4. **`Caused by: ...`:** một đối tượng `Throwable` được gắn làm nguyên nhân.
5. **`... N more`:** một số khung cuối trùng với stack của ngoại lệ bao ngoài nên được rút gọn khi in. Đây là **cách nén hiển thị**, không có nghĩa thông tin “bị mất” khỏi đối tượng.

Một số stack trace có nhiều cấp `Caused by` hoặc nhiều mục `Suppressed`, và mỗi ngoại lệ phụ có thể lại mang cause hoặc suppressed riêng. Không phải ngoại lệ nào cũng có đủ các mục trên; chương trình chỉ in những gì đối tượng đang mang.

### Cách đọc nhanh khi có nhiều lớp ngoại lệ

Nếu dòng đầu là `ServiceException` còn cuối phần cause là `SocketTimeoutException`, điều đó thường có nghĩa tầng service đã **bọc** một lỗi sâu hơn từ tầng mạng. Nên đọc cả hai:

- **Ngoại lệ ngoài:** loại lỗi mà tầng gọi hiện tại đang xử lý, cùng ngữ cảnh cấp cao.
- **Nguyên nhân bên trong:** điều gì đã thất bại ở lớp thấp hơn.
- **Vị trí trong stack:** đoạn mã nào tạo/làm lan truyền lỗi theo dấu vết đã ghi.
- **Suppressed:** các vấn đề dọn dẹp/tài nguyên xuất hiện ngoài ngoại lệ chính.

Không nên chỉ lấy dòng `Caused by` cuối cùng làm nguyên nhân chắc chắn trong mọi trường hợp. Các tầng có thể chuyển lỗi sai, làm mất cause, hoặc tạo một lỗi không liên quan; cần đối chiếu ngữ cảnh và hành vi thực tế.

## 4. `StackTraceElement` là gì?

Mỗi frame được biểu diễn bằng một `java.lang.StackTraceElement`. Khi đọc một bản `printStackTrace()`, dòng `at package.Class.method(File.java:line)` thường tương ứng với thông tin của một frame.

| API | Câu hỏi trả lời |
| --- | --- |
| `getClassName()`, `getMethodName()` | Lớp và phương thức nào được ghi? |
| `getFileName()`, `getLineNumber()` | Có tệp nguồn và số dòng nào không? |
| `isNativeMethod()` | Frame có thuộc native method không? |
| `getModuleName()`, `getClassLoaderName()` | Có metadata module/class loader không? |

Tên tệp có thể `null`, số dòng có thể âm, và trace có thể rỗng; không parse chuỗi `File.java:123` như dữ liệu ổn định. `getStackTrace()` trả **mảng frame**, còn `printStackTrace()` là bản hiển thị có cả cause/suppressed. Nếu cần khám phá getter, bản sao phòng vệ và dữ liệu module, bài `getStackTrace`/`StackTraceElement` là nơi giải thích chính.

## 5. Khi nào stack trace được ghi lại?

Trong hành vi thông thường của `Throwable`, stack trace được ghi nhận **khi đối tượng ngoại lệ được tạo/khởi tạo** (thông qua cơ chế như `fillInStackTrace()`), chứ không nhất thiết tại mỗi lần dùng `throw`.

Ví dụ:

```java
static IllegalArgumentException createError() {
    return new IllegalArgumentException("invalid quantity");
}

static void raiseError(IllegalArgumentException ex) {
    throw ex;
}
```

Đây là hai phương thức minh họa. Nếu tạo đối tượng bằng `createError()` rồi ném nó ở `raiseError()`, việc ném lại **cùng đối tượng** không tự động viết lại stack trace thành vị trí `raiseError()`. Điều này giúp bảo toàn nguồn gốc đã ghi nhưng đôi khi làm người đọc tưởng ngoại lệ được ném đúng tại chỗ nó được tạo.

**`Throwable.fillInStackTrace()`** có thể cập nhật stack trace dựa trên vị trí hiện tại, nhưng đây là thao tác thay đổi bằng chứng chẩn đoán. Trừ trường hợp có chủ đích và kiểm thử rõ, nên **ném lại cùng đối tượng** để giữ thông tin gốc, hoặc **bọc bằng ngoại lệ mới có cause** nếu cần một tầng ngữ cảnh mới.

Với một ngoại lệ mới:

```java
throw new IllegalStateException(
        "checkout processing failed", original);
```

Biến `original` phải là một `Throwable` thật đã tồn tại trong phạm vi. Ngoại lệ ngoài có stack trace được ghi ở nơi tạo nó; ngoại lệ gốc giữ stack trace riêng và được nối qua `getCause()`. `printStackTrace` giúp đọc cả hai.

### Re-throw không phải tạo ngoại lệ mới

So sánh:

- **`throw ex;`:** ném lại cùng đối tượng. Kiểu runtime, message, cause và stack trace hiện tại vẫn thuộc đối tượng đó.
- **`throw new SomeException("context", ex);`:** tạo đối tượng mới với stack trace riêng, gắn `ex` làm cause.
- **`throw new SomeException(ex.getMessage());`:** có thể làm mất liên kết tới đối tượng gốc nếu không có cơ chế giữ khác.

Đừng bọc nhiều tầng chỉ để thêm thông điệp `"failed"` giống nhau; stack trace dài hơn không có nghĩa chẩn đoán tốt hơn.

## 6. `getMessage()`, `toString()` và `printStackTrace()` khác nhau ra sao?

Giả sử `IOException ex = new IOException("read failed");`:

| Cách gọi | Ví dụ đầu ra hoặc loại kết quả | Có stack frames? | Có chuỗi cause/suppressed đầy đủ? |
| --- | --- | --- | --- |
| `ex.getMessage()` | `"read failed"` | Không | Không |
| `ex.toString()` | `"java.io.IOException: read failed"` | Không | Không |
| `ex.getStackTrace()` | `StackTraceElement[]` | Có dưới dạng dữ liệu | Không trực tiếp chứa quan hệ cause/suppressed |
| `ex.printStackTrace()` | In thông tin định dạng trên `System.err` | Có | Có nếu ngoại lệ đang mang dữ liệu đó |
| `ex.printStackTrace(writer)` | Ghi vào `PrintWriter` | Có | Có nếu ngoại lệ đang mang dữ liệu đó |

**`toString()`** mặc định của `Throwable` đưa tên lớp và thông điệp được bản địa hóa (`getLocalizedMessage()`) nếu thông điệp không null. Một lớp ngoại lệ tùy chỉnh có thể override các phương thức liên quan nên chuỗi chính xác còn phụ thuộc loại lỗi.

**`getMessage()`** có thể là `null`, vì vậy một lệnh như:

```java
System.out.println(ex.getMessage().length());
```

có thể ném `NullPointerException` trong chính handler. Khi xây dựng thông điệp chẩn đoán, hãy dùng cơ chế xử lý giá trị null phù hợp.

**`printStackTrace()`** là hành động **ghi ra đích đầu ra**, không phải API trả chuỗi. Gọi `String trace = ex.printStackTrace();` là **lỗi biên dịch** vì phương thức trả `void`.

## 7. Ví dụ Java 17+ hoàn chỉnh: biểu diễn ngoại lệ gồm type, cause, suppressed và trace

Chương trình sau tạo một ngoại lệ ngoài, gắn nguyên nhân gốc và một ngoại lệ phụ, rồi **bắt stack trace bằng `StringWriter`**. Thay vì in raw trace chứa số dòng không ổn định, nó in các kiểm tra dạng boolean có kết quả xác định với JDK 17 thông thường.

Lưu tệp **`ThrowablePortraitDemo.java`**:

```java
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class ThrowablePortraitDemo {

    static IllegalStateException buildError() {
        IOException cause = new IOException("storage offline");

        IllegalStateException error =
                new IllegalStateException(
                        "cannot complete checkout", cause);

        error.addSuppressed(
                new IllegalArgumentException("close warning"));
        return error;
    }

    public static void main(String[] args) {
        IllegalStateException error = buildError();

        System.out.println(
                "type: " + error.getClass().getSimpleName());
        System.out.println("message: " + error.getMessage());
        System.out.println("toString: " + error);

        System.out.println(
                "cause: "
                + error.getCause().getClass().getSimpleName());
        System.out.println(
                "suppressed count: "
                + error.getSuppressed().length);

        System.out.println(
                "first method: "
                + error.getStackTrace()[0].getMethodName());

        StringWriter buffer = new StringWriter();
        error.printStackTrace(new PrintWriter(buffer));
        String trace = buffer.toString();

        System.out.println(
                "has header: "
                + trace.startsWith(
                        "java.lang.IllegalStateException: "
                        + "cannot complete checkout"));

        System.out.println(
                "has frame: "
                + trace.contains("ThrowablePortraitDemo.buildError"));

        System.out.println(
                "has suppressed: "
                + trace.contains(
                        "Suppressed: java.lang.IllegalArgumentException: "
                        + "close warning"));

        System.out.println(
                "has cause: "
                + trace.contains(
                        "Caused by: java.io.IOException: storage offline"));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 ThrowablePortraitDemo.java
java ThrowablePortraitDemo
```

**Stdout chính xác** (không phụ thuộc số dòng trong tệp):

```text
type: IllegalStateException
message: cannot complete checkout
toString: java.lang.IllegalStateException: cannot complete checkout
cause: IOException
suppressed count: 1
first method: buildError
has header: true
has frame: true
has suppressed: true
has cause: true
```

### Phân tích từng phần

- `getClass().getSimpleName()` xác định **kiểu thực tế** của ngoại lệ ngoài: `IllegalStateException`.
- `getMessage()` chỉ hiển thị thông điệp của ngoại lệ ngoài.
- `toString()` hiển thị cả tên lớp đầy đủ và message.
- `getCause()` dẫn đến `IOException("storage offline")`. Đây là nguyên nhân do lập trình viên chuyển vào constructor.
- `getSuppressed()` chứa một `IllegalArgumentException("close warning")`, được thêm bằng `addSuppressed`.
- `getStackTrace()[0].getMethodName()` là `buildError`, bởi đối tượng `IllegalStateException` được tạo ở phương thức đó.
- `printStackTrace(new PrintWriter(buffer))` ghi văn bản vào buffer. Các phép `contains` kiểm tra thông tin nguyên nhân, lỗi phụ và khung gọi trong bản hiển thị đầy đủ.

Ví dụ **không tự ném ngoại lệ** ra ngoài `main`. Bạn hoàn toàn có thể dùng `printStackTrace` để phân tích một đối tượng `Throwable` chỉ vừa được tạo hoặc đã được bắt; việc in không đồng nghĩa ngoại lệ đang lan truyền.

## 8. Ví dụ Java 17+ hoàn chỉnh: đọc từng khung của một chuỗi lời gọi

Lưu thành **`TraceOriginDemo.java`**:

```java
public class TraceOriginDemo {

    static void origin() {
        throw new IllegalArgumentException("bad quantity");
    }

    static void middle() {
        origin();
    }

    public static void main(String[] args) {
        try {
            middle();
        } catch (IllegalArgumentException ex) {
            StackTraceElement[] frames = ex.getStackTrace();

            System.out.println(
                    "first method: " + frames[0].getMethodName());
            System.out.println(
                    "second method: " + frames[1].getMethodName());
            System.out.println(
                    "third method: " + frames[2].getMethodName());
            System.out.println(
                    "first file: " + frames[0].getFileName());
            System.out.println(
                    "line positive: "
                    + (frames[0].getLineNumber() > 0));
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 TraceOriginDemo.java
java TraceOriginDemo
```

**Stdout với trình biên dịch Java thông thường có thông tin dòng nguồn:**

```text
first method: origin
second method: middle
third method: main
first file: TraceOriginDemo.java
line positive: true
```

**Giải thích:**

1. `main()` gọi `middle()`.
2. `middle()` gọi `origin()`.
3. `origin()` tạo và ném `IllegalArgumentException`.
4. Handler ở `main()` nhận cùng đối tượng và truy cập mảng `StackTraceElement[]`.
5. Khung đầu nằm ở `origin`; những khung tiếp theo là `middle` rồi `main`. Đây là thứ tự của chuỗi gọi tại thời điểm stack được ghi.

Ví dụ này tập trung vào Java chạy từ các tệp nguồn bằng `javac`. Với bytecode được tối ưu hóa, thiếu thông tin debug hoặc hoàn cảnh runtime đặc biệt, thông tin dòng/khung có thể khác. **Không nên viết kiểm thử ứng dụng phụ thuộc vào số dòng cụ thể** trong stack trace.

## 9. `Caused by`: chuỗi nguyên nhân từ nhiều tầng

Một API tầng thấp có thể ném `IOException`, tầng trên bọc thành `IllegalStateException` để biểu diễn rằng ứng dụng không thể hoàn tất thao tác. Ví dụ:

```java
static void loadRequiredData() {
    try {
        readFromStorage();
    } catch (java.io.IOException cause) {
        throw new IllegalStateException(
                "Required data could not be loaded", cause);
    }
}
```

Đây là **phương thức minh họa**, giả định `readFromStorage()` tồn tại và có thể ném `IOException`.

Nếu error ngoài được in với `printStackTrace()`, người đọc có thể thấy:

```text
java.lang.IllegalStateException: Required data could not be loaded
    at ... <stack of wrapper>
Caused by: java.io.IOException: <underlying problem>
    at ... <stack of original exception>
```

Đây là **mô tả cấu trúc**, không phải stdout cố định. `Throwable.getCause()` trả về đối tượng nguyên nhân trực tiếp; có thể lần theo nhiều cấp khi có wrapper lồng nhau.

### Ném lại và bọc lỗi phải là lựa chọn có chủ đích

- Nếu caller ở tầng ngoài vẫn có thể xử lý `IOException`, truyền nguyên ngoại lệ đó lên đôi khi tốt hơn bọc.
- Nếu tầng service cần trình bày lỗi ở mức trừu tượng cao hơn, tạo wrapper có `cause` giúp giữ dấu vết gốc.
- Nếu chỉ viết `throw new IllegalStateException("failed");`, caller có thể **mất nguyên nhân cụ thể** đã bắt.
- Đừng dựa vào nội dung `getMessage()` để dò nguyên nhân theo chuỗi: người viết thư viện có thể đổi cách diễn đạt và nhiều lỗi có thể chia sẻ thông điệp.

Có thể dùng một hàm đọc cause chain khi thực sự cần:

```java
Throwable current = ex;

while (current.getCause() != null) {
    current = current.getCause();
}

System.out.println(current.getClass().getSimpleName());
```

Đây là đoạn minh họa với `ex` là một đối tượng `Throwable`. Trong những lớp tùy chỉnh hoặc dữ liệu không đáng tin, ứng dụng cần đề phòng chuỗi cause bất thường và tránh giả định “lỗi cuối cùng” luôn là nguyên nhân thực tế duy nhất.

## 10. `Suppressed`: ghi nhận lỗi thứ cấp, đặc biệt trong `try-with-resources`

Trong `try-with-resources`, Java tự đóng những tài nguyên `AutoCloseable` khi rời phạm vi. Có trường hợp:

1. Thân `try` đang ném ngoại lệ **E1**.
2. Trong quá trình đóng, `close()` ném ngoại lệ **E2**.
3. Java giữ **E1 là ngoại lệ chính** và gắn E2 làm **suppressed exception** của E1, thay vì để E2 che mất lỗi đã gây thất bại ban đầu.

Dạng minh họa:

```java
try (AutoCloseable resource = openResource()) {
    performWork(resource);
}
```

Đây là khung minh họa; `openResource`, `performWork` phải được định nghĩa, và chữ ký checked exception của `AutoCloseable.close()` có thể yêu cầu `throws Exception` hoặc handler phù hợp.

Tầng ngoài có thể đọc:

```java
for (Throwable secondary : ex.getSuppressed()) {
    System.out.println("secondary: " + secondary);
}
```

`getSuppressed()` trả về mảng, không phải `null`; có thể rỗng khi không có lỗi phụ được lưu.

### Lưu ý về `addSuppressed()`

`Throwable.addSuppressed(Throwable exception)` cho phép gắn một ngoại lệ phụ bằng tay khi chính sách yêu cầu. Không được truyền `null` hoặc tự suppress chính đối tượng đó. Hãy để Java quản lý trường hợp `try-with-resources` chuẩn, tránh tự tái tạo cơ chế nếu không cần thiết.

**Suppressed khác với cause.** Một ngoại lệ cleanup không nhất thiết là nguyên nhân gây lỗi đọc dữ liệu; vì vậy nó thường là *suppressed*, không phải `cause`. `printStackTrace` hiển thị cả hai quan hệ, nhưng `getCause()` không tự chứa danh sách suppressed.

### Một lớp `Throwable` có thể tắt suppression hoặc stack trace

API có những constructor dành cho subclass cho phép điều chỉnh cơ chế suppression và khả năng ghi stack trace. Do đó, trong ứng dụng dùng các ngoại lệ tùy chỉnh đặc biệt, không nên giả định `getSuppressed()` lúc nào cũng phản ánh mọi lỗi phụ hay `getStackTrace()` luôn chứa một tập khung đầy đủ.

Những tùy chọn này chủ yếu phục vụ thiết kế hiệu năng hoặc hợp đồng đặc biệt; với lỗi nghiệp vụ thông thường nên giữ thông tin chẩn đoán đủ dùng.

## 11. Không phải mọi stack trace đều được in nguyên văn mọi khung

Định dạng phổ biến của `printStackTrace` có thể chứa:

```text
... 3 more
```

Điều này nói rằng một số khung cuối của phần cause/suppressed giống các khung cuối đã được in ở ngoại lệ bao ngoài. Chúng được **rút gọn để tránh lặp**. Không phải Java dừng theo dõi lỗi sau ba lời gọi.

Nếu phải phân tích máy móc, hãy làm việc với API có cấu trúc:

```java
Throwable cause = ex.getCause();
StackTraceElement[] frames = ex.getStackTrace();
Throwable[] suppressed = ex.getSuppressed();
```

thay vì tách chuỗi thô theo các dòng `"Caused by:"`, `"Suppressed:"` hoặc `"at "`. Định dạng có thể khác do JVM, module info, logger và công cụ thu thập lỗi.

Mỗi đối tượng nguyên nhân có mảng `getStackTrace()` riêng. Bạn có thể điều tra từng đối tượng thay vì phụ thuộc số lượng dòng mà `printStackTrace` hiển thị.

## 12. `printStackTrace()` có “bắt” hoặc “xử lý” ngoại lệ không?

Không. Ví dụ:

```java
try {
    doWork();
} catch (IllegalStateException ex) {
    ex.printStackTrace();
    System.out.println("handler finished");
}
```

Đây là khung minh họa. `printStackTrace` **chỉ in thông tin**. Handler sẽ kết thúc bình thường nếu không có lệnh ném lại hoặc một ngoại lệ mới xuất hiện, và code có thể tiếp tục ở sau cấu trúc `try-catch`.

Nếu muốn chuyển ngoại lệ lên caller:

```java
try {
    doWork();
} catch (IllegalStateException ex) {
    ex.printStackTrace();
    throw ex;
}
```

Lệnh `throw ex;` mới thật sự ném lại đối tượng; `printStackTrace` không thực hiện điều đó. Dù đoạn này có thể hữu ích trong một thí nghiệm, ứng dụng thật thường không nên vừa in stack trace ở nhiều tầng vừa ném tiếp vì gây **log trùng lặp**.

### Trường hợp không có handler

Một ngoại lệ thoát khỏi điểm bắt đầu của một thread có thể được chuyển đến `UncaughtExceptionHandler`. Triển khai mặc định phổ biến sẽ báo thông tin lỗi và stack trace ra stderr. Nhưng:

- Handler tùy chỉnh có thể thay đổi cách ghi hoặc không ghi.
- Thread gặp lỗi kết thúc; handler không khiến nó quay lại câu lệnh đã thất bại.
- Lỗi trong một tác vụ `ExecutorService.submit()` có thể được framework lưu trong `Future` và đưa về caller qua `ExecutionException` thay vì thành uncaught exception của worker.

Đừng coi mọi stack trace nhìn thấy trong console là ngoại lệ “không được bắt”; nó cũng có thể do lập trình viên hoặc logger in trong một `catch`.

## 13. Cách lấy stack trace thành chuỗi khi thật sự cần

Vì `printStackTrace()` trả `void`, cách thường dùng để lấy bản biểu diễn dạng chuỗi là:

```java
java.io.StringWriter buffer = new java.io.StringWriter();
java.io.PrintWriter writer = new java.io.PrintWriter(buffer);

ex.printStackTrace(writer);
writer.flush();

String traceText = buffer.toString();
```

Đây là đoạn minh họa với `ex` là `Throwable`. `writer.flush()` làm mục đích rõ ràng khi dùng một writer có bộ đệm; `StringWriter` lưu chuỗi trong bộ nhớ.

Không nên dùng cách này trong mọi request production chỉ để lấy message:

- Stack trace có thể rất dài, tiêu thụ bộ nhớ và lưu trữ.
- Bản văn có thể chứa tên file, lớp nội bộ, SQL, đường dẫn hoặc dữ liệu nhạy cảm trong message.
- Nếu hệ thống log hỗ trợ nhận `Throwable`, đưa thẳng đối tượng lỗi cho logger thường tốt hơn việc tạo chuỗi thủ công.

Ví dụ với Java Util Logging (một **đoạn minh họa**, cần biến `ex` và logger thích hợp):

```java
java.util.logging.Logger logger =
        java.util.logging.Logger.getLogger("application");

logger.log(
        java.util.logging.Level.SEVERE,
        "Order processing failed",
        ex);
```

Ở đây logger nhận **đối tượng Throwable**, giúp cấu hình handler/formatter quyết định cách ghi trace, thay vì chỉ ghi `ex.getMessage()` và mất chuỗi nguyên nhân.

## 14. Giới hạn của trace: nơi tạo ngoại lệ, không phải mọi điều từng xảy ra

Stack trace là thông tin **chẩn đoán về stack đã được ghi**, không phải bản ghi lịch sử đầy đủ.

Một số giới hạn quan trọng:

- `getStackTrace()` thường phản ánh nơi đối tượng được tạo hoặc nơi `fillInStackTrace()` được gọi gần nhất, **không ghi lại mọi lần rethrow**.
- Stack trace của một thread không tự chứa stack của những thread khác.
- Với bất đồng bộ, callback, `CompletableFuture` hoặc executor, chuỗi cause/wrapper có thể quan trọng hơn một stack đơn.
- Khi code được biên dịch khác, số dòng và tên lớp sinh tự động có thể thay đổi.
- Một số frame có thể không có file/line, hoặc stack trace có thể bị rút gọn/giới hạn bởi runtime.
- Không phải tất cả lỗi đều có cause hay suppressed, ngay cả khi người điều tra muốn có chúng.
- Stack trace đầy đủ không chứng minh dữ liệu đã được rollback hay tài nguyên đã được giải phóng thành công.

Vì vậy, để điều tra production cần kết hợp stack trace với **thời điểm**, **tên thao tác**, **request/correlation ID an toàn**, **phiên bản ứng dụng** và các chỉ số vận hành liên quan.

## 15. Các lỗi thường gặp khi biểu diễn ngoại lệ

| Cách làm sai hoặc thiếu | Hậu quả | Cách tốt hơn |
| --- | --- | --- |
| Chỉ log `ex.getMessage()` | Mất vị trí lỗi, cause, suppressed | Truyền `Throwable` cho logger khi cần điều tra |
| Nghĩ `System.out.println(ex)` in stack trace | Thường chỉ gọi `toString()` | Dùng `printStackTrace` hoặc logger nhận Throwable |
| Nghĩ `ex.printStackTrace()` trả về `String` | Lỗi biên dịch: kiểu trả về `void` | Dùng `StringWriter` + `PrintWriter` khi cần |
| Không phân biệt stdout/stderr | Output trộn, thứ tự khó đoán | Đọc/định tuyến luồng đúng mục đích |
| Cắt trace đến dòng `Caused by` đầu tiên | Mất chuỗi nguyên nhân sâu hoặc lỗi suppressed | Xem toàn bộ Throwable/cause/suppressed |
| Bắt lỗi rồi tạo ngoại lệ mới không giữ `cause` | Mất liên hệ với lỗi gốc | Bọc bằng constructor nhận cause khi phù hợp |
| Giả định mọi `StackTraceElement` có số dòng dương | Sai với native/unknown source | Kiểm tra giá trị và không phụ thuộc số dòng |
| Dò business logic bằng `trace.contains("Parser.java:42")` | Dễ hỏng khi sửa mã/phiên bản | Dùng kiểu ngoại lệ, error code hoặc dữ liệu có cấu trúc |
| In cùng trace ở repository, service, controller | Log trùng, khó phân biệt một sự cố | Chọn ranh giới log và chuyển lỗi rõ ràng |
| In mọi exception message ra API response | Có thể lộ bí mật nội bộ | Tách message công khai và log nội bộ được kiểm soát |
| Nghĩ `printStackTrace` phục hồi chương trình | Chỉ là hành động xuất thông tin | Quyết định `catch`, retry, return hay rethrow riêng |
| Nghĩ suppressed là cause | Hiểu sai quan hệ lỗi | Xem `getCause()` và `getSuppressed()` riêng |
| Dùng `setStackTrace` để giấu vị trí lỗi | Chẩn đoán sai lệch, giảm khả năng kiểm toán | Giữ stack gốc, bổ sung ngữ cảnh bằng wrapper/log |

### Vì sao chỉ log message chưa đủ?

Giả sử log ghi:

```text
failed to read data
```

Dòng này không cho biết lỗi xuất hiện ở đoạn nào, là lỗi I/O nào, ai đã gọi thao tác hay có lỗi đóng tài nguyên không.

Ngược lại, nếu logger nhận cả `Throwable`, bản ghi có thể thể hiện stack trace, nguyên nhân và các lỗi phụ. Tuy nhiên, muốn điều tra hiệu quả vẫn cần **ngữ cảnh an toàn và chính xác**; một stack trace dài mà không biết request nào liên quan có thể vẫn khó xử lý.

## 16. Cẩn thận với dữ liệu nhạy cảm và khối lượng log

Stack trace có thể tiết lộ nhiều thông tin nội bộ. Dòng đầu có thể chứa message do ứng dụng tự tạo hoặc do thư viện cung cấp. Nếu code viết:

```java
throw new IllegalArgumentException(
        "invalid token: " + suppliedToken);
```

thì token có nguy cơ xuất hiện trong `toString()`, `printStackTrace()` và các hệ thống log ghi `Throwable`. Đây là **đoạn chống mẫu**, với `suppliedToken` là biến giả định.

Nên thay bằng thông điệp không lộ bí mật:

```java
throw new IllegalArgumentException("invalid authentication token");
```

Các hướng dẫn thực hành:

1. Không ghép password, secret, API key, cookie hay payload người dùng vào message ngoại lệ.
2. Chọn mã yêu cầu/correlation ID không nhạy cảm cho log có cấu trúc.
3. Không trả toàn bộ stack trace cho khách hàng qua HTTP hay giao diện công khai.
4. Quy định một ranh giới ghi log phù hợp để tránh in nhiều lần cùng một lỗi.
5. Kiểm soát mức log, quyền truy cập, thời gian lưu trữ và số lượng sự kiện khi có lỗi lặp.
6. Không nuốt ngoại lệ chỉ để giảm log; hãy xử lý có chủ đích và quan sát ở tầng phù hợp.

`printStackTrace` là công cụ chẩn đoán, **không phải giao thức phản hồi lỗi ổn định hoặc an toàn để công khai mặc định**.

## 17. Bài tập tự kiểm tra

**Bài A.** `System.out.println(ex)` có tương đương `ex.printStackTrace()` không?

**Không.** Câu đầu thường dùng `Throwable.toString()`, in tên lớp và message; câu sau hiển thị các khung stack cùng cause/suppressed và mặc định ra stderr.

**Bài B.** `Throwable.printStackTrace()` trả về gì?

**`void`.** Muốn bản văn trong một chuỗi, truyền `PrintWriter` gắn với `StringWriter` rồi lấy `buffer.toString()`.

**Bài C.** Nếu `new IllegalStateException("outer", cause)`, `getCause()` trả gì?

Trả về đối tượng `cause` đã được truyền vào constructor (nếu hợp lệ), không phải chuỗi message của cause.

**Bài D.** Trong `try-with-resources`, thân `try` và `close()` cùng ném lỗi. Lỗi khi đóng thường nằm ở đâu?

Trong **`getSuppressed()`** của ngoại lệ chính đến từ thân `try`. Không nên mặc định tìm nó bằng `getCause()`.

**Bài E.** Stack trace in `... 2 more` có nghĩa Java đã làm mất hai frame không?

Không. Đây là cách rút gọn các khung trùng phần cuối đã được in ở ngoại lệ bao ngoài. Có thể xem mảng `getStackTrace()` của từng đối tượng nếu cần dữ liệu theo cấu trúc.

**Bài F.** Một đối tượng ngoại lệ tạo trong `createError()` rồi được `throw` ở `process()`. Phần tử đầu của stack trace luôn là `process()` sao?

Không. Stack trace thông thường được ghi khi ngoại lệ được tạo/khởi tạo; rethrow cùng đối tượng không tự làm lại stack. Cần xét nơi đối tượng được tạo và liệu `fillInStackTrace` có được gọi hay không.

**Bài G.** Có an toàn khi cố định kiểm thử rằng stack trace chứa `OrderService.java:137` không?

Thường không. Số dòng có thể thay đổi khi refactor, biên dịch hoặc chạy ở môi trường khác. Nên kiểm thử loại ngoại lệ, cause, thuộc tính có cấu trúc và hành vi xử lý; chỉ kiểm tra stack khi có yêu cầu chẩn đoán cụ thể.

**Bài H.** Nếu `ex.getMessage()` là `null` thì `ex.toString()` có bị `null` theo không?

Không nhất thiết. Theo hành vi mặc định của `Throwable.toString()`, chuỗi vẫn có tên lớp ngay cả khi thông điệp null. Một lớp tùy chỉnh có thể thay đổi cách biểu diễn.

**Bài I.** `printStackTrace` có làm cho `catch` phục hồi sau ngoại lệ không?

Không. Nó chỉ in thông tin. Handler cần quyết định tiếp tục, chuyển kết quả thất bại, ném lại hoặc bọc ngoại lệ theo chính sách.

**Bài J.** Khi production đã có logger nhận `Throwable`, có nên gọi thêm `printStackTrace()` ở mọi tầng không?

Thường không. Làm vậy dễ tạo bản ghi trùng và bỏ qua cơ chế định tuyến, mức log hay kiểm soát nhạy cảm của logger. Hãy chọn ranh giới logging phù hợp.

## 18. Checklist đọc và ghi một ngoại lệ có trách nhiệm

Khi gặp stack trace, hãy lần lượt:

1. **Xác định loại ngoại lệ ngoài:** đọc dòng đầu hoặc `getClass()`.
2. **Đọc message:** hiểu điều kiện thất bại; xác minh có dữ liệu nhạy cảm không.
3. **Xem các frame:** xác định những phương thức liên quan và vị trí gần nơi stack được ghi nhất.
4. **Xem chuỗi `Caused by`:** tìm nguyên nhân từ tầng thấp và liên hệ tới lỗi cấp cao.
5. **Xem `Suppressed`:** tìm lỗi dọn dẹp/đóng tài nguyên có thể bị bỏ quên.
6. **Hiểu `... N more`:** đó là rút gọn khung trùng, không phải toàn bộ nội dung của nguyên nhân.
7. **Tách stdout và stderr:** đừng suy luận thứ tự chính xác từ console đã gộp luồng.
8. **Xác định ranh giới xử lý:** lỗi đã được `catch`/logger ghi lại hay thực sự thoát khỏi thread?
9. **Đọc ngữ cảnh an toàn:** request ID, thao tác, phiên bản và trạng thái hệ thống, không dựa vào trace đơn lẻ.
10. **Phân biệt xử lý với biểu diễn:** `printStackTrace` không quyết định retry, fallback, rollback hay kết thúc.
11. **Kiểm thử có chủ đích:** ưu tiên loại ngoại lệ/cause/suppressed thay vì so sánh toàn bộ bản trace có số dòng.
12. **Giữ bí mật:** không phát tán stack trace hoặc exception message tùy tiện ra nơi công khai.

**Kết luận:** một ngoại lệ Java không chỉ có message. `Throwable` cho phép quan sát **loại lỗi, thông điệp, stack frames, cause và suppressed exceptions**. `printStackTrace()` là cách biểu diễn tổng hợp thuận tiện để điều tra, mặc định in ra **`System.err`**, còn các overload `PrintStream`/`PrintWriter` cho phép chọn đích ghi. Trong ứng dụng thật, nên giữ nguyên chuỗi nguyên nhân, quản lý lỗi đóng tài nguyên đúng cách và đưa `Throwable` cho logger ở ranh giới có trách nhiệm, thay vì chỉ in thông điệp hoặc hard-code định dạng stack trace.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="throwable-internals-005">getStackTrace và cấu trúc StackTraceElement</a>

<details>
<summary>Xem chi tiết</summary>

# `getStackTrace()` và cấu trúc `StackTraceElement` trong Java

> **Chủ đề:** Java Core → Exception → Đối tượng `Throwable`. Phương thức `Throwable.getStackTrace()` cho phép chương trình đọc một mảng `StackTraceElement[]` mô tả những vị trí gọi phương thức gắn với một đối tượng lỗi. Hiểu từng frame, vị trí tạo Throwable, nguồn dữ liệu và giới hạn của stack trace giúp chẩn đoán lỗi chính xác mà không phụ thuộc mù quáng vào số dòng hoặc chuỗi `printStackTrace()`.

## 1. Stack trace là gì?

Khi chương trình Java gọi các phương thức lồng nhau, runtime quản lý các *stack frame* tương ứng với các lần gọi. Một stack trace trên `Throwable` là bản mô tả ngăn xếp liên quan đến thời điểm thông tin trace được **ghi lại**, thường là lúc đối tượng `Throwable` được tạo.

Ví dụ:

```text
main()
  └── runOrder()
        └── parseQuantity()
              └── new NumberFormatException(...)
```

Một bản trace tiêu biểu chứa:

```text
0: OrderApp.parseQuantity(OrderApp.java:18)
1: OrderApp.runOrder(OrderApp.java:10)
2: OrderApp.main(OrderApp.java:29)
```

Đây chỉ là **minh họa**, số dòng thay đổi theo mã nguồn. Frame chỉ số `0` thường gần nhất với điểm ghi nhận trace; các frame sau thể hiện những lời gọi dẫn tới vị trí đó.

Điều cần phân biệt:

- **Call stack đang hoạt động:** ngăn xếp của thread tại một thời điểm.
- **Stack trace của `Throwable`:** thông tin đã được ghi trong đối tượng lỗi. Sau khi ngoại lệ được bắt hoặc ném lại, dữ liệu đó không nhất thiết phản ánh call stack *hiện tại*.
- **Stack trace được in:** dạng văn bản phục vụ con người, ví dụ từ `printStackTrace()`; không giống mảng dữ liệu có cấu trúc lấy bằng `getStackTrace()`.

Stack trace hữu ích cho debugging, ghi log, truy vết tầng gọi và xác định vị trí tạo lỗi, nhưng **không** tự giải thích nguyên nhân nghiệp vụ. Cần kết hợp với thông điệp, `cause`, các suppressed exceptions và ngữ cảnh dữ liệu.

## 2. API cốt lõi: `Throwable.getStackTrace()`

Phương thức công khai:

```java
public StackTraceElement[] getStackTrace()
```

Cách dùng:

```java
try {
    Integer.parseInt("not-a-number");
} catch (NumberFormatException e) {
    StackTraceElement[] frames = e.getStackTrace();
    for (StackTraceElement frame : frames) {
        System.out.println(frame.getClassName() + "."
                + frame.getMethodName());
    }
}
```

Đây là **mảnh code minh họa** đặt trong method. Không nên xem stdout của đoạn này là cố định, vì stack có thể bao gồm frame thư viện JDK, compiler và cấu trúc gọi thực tế.

Các đặc điểm chính:

1. Kết quả là **mảng `StackTraceElement[]`, không phải `String`**.
2. Mỗi phần tử mô tả một frame: lớp, phương thức, tệp nguồn, dòng mã và một số thông tin bổ sung.
3. Frame đầu tiên (chỉ số `0`) là vị trí trên cùng trong trace được lưu, thường gần nhất với nơi `Throwable` ghi nhận ngăn xếp.
4. Mảng trả về **không phải `null`**, nhưng có thể có **độ dài bằng 0** nếu thông tin stack không khả dụng hoặc đối tượng được cấu hình không lưu trace.
5. `getStackTrace()` trả **bản sao của mảng**: thay phần tử trong mảng trả về không tự thay đổi trace nằm trong `Throwable`.
6. Việc gọi phương thức này **không ném lại exception** và không tự cập nhật nó thành stack của caller hiện tại.
7. Trên các JVM, stack trace có thể thiếu một số frame hoặc thông tin; không nên phụ thuộc vào số lượng hay vị trí frame tuyệt đối trong production.

### 2.1. Vì sao trả về mảng thay vì chuỗi?

Mảng giúp code phân tích một frame mà không phải tách chuỗi hiển thị theo dấu ngoặc, dấu hai chấm hoặc khoảng trắng. Bạn có thể lấy:

- `getClassName()` để biết tên lớp.
- `getMethodName()` để biết phương thức.
- `getFileName()` để biết tên file nguồn khi có thông tin.
- `getLineNumber()` để biết số dòng khi có thông tin.
- `isNativeMethod()` để nhận diện frame của native method.
- Thông tin module/class loader trên Java hiện đại nếu có.

Chuỗi từ `StackTraceElement.toString()` phù hợp để hiển thị và log; các phương thức getter phù hợp hơn khi muốn xử lý dữ liệu có cấu trúc.

## 3. Cấu trúc `StackTraceElement`

`java.lang.StackTraceElement` đại diện cho **một vị trí của một lời gọi** trong stack trace. Một đối tượng không phải exception, không có `getCause()` và không phải là nơi người dùng thực hiện `catch`. Nó là một **phần tử mô tả** trong mảng do `Throwable` cung cấp.

Các API đáng nhớ:

| API trên `StackTraceElement` | Ý nghĩa | Lưu ý |
| --- | --- | --- |
| `getClassName()` | Tên nhị phân của lớp khai báo phương thức | Có thể bao gồm package, lớp lồng nhau với ký hiệu `$` |
| `getMethodName()` | Tên phương thức tại frame | Constructor thường có dạng `<init>` trong thông tin trace; tên không phải chữ ký đầy đủ |
| `getFileName()` | Tên file nguồn khi có | Có thể trả về `null` |
| `getLineNumber()` | Dòng của vị trí thực thi | Giá trị âm nghĩa là không có số dòng nguồn; không dựa vào số dòng cố định |
| `isNativeMethod()` | Frame có phải phương thức native không | Có thể có frame native không gắn với số dòng Java |
| `getClassLoaderName()` | Tên class loader khi được ghi trong frame | Java 9+; có thể là `null` |
| `getModuleName()` | Tên module khi được ghi trong frame | Java 9+; có thể là `null` |
| `getModuleVersion()` | Phiên bản module khi được ghi trong frame | Java 9+; có thể là `null` |
| `toString()` | Biểu diễn một frame cho người đọc | Không phải định dạng dữ liệu bền vững để parse |
| `equals()`, `hashCode()` | So sánh các frame theo các trường dữ liệu | Dùng khi cần kiểm tra/cấu trúc tập hợp |

**Quan trọng:** `StackTraceElement` là một đối tượng mang thông tin frame; nó không tự chứa toàn bộ stack. Để đọc một trace đầy đủ, cần duyệt **mảng** các phần tử đó.

### 3.1. Giá trị đặc biệt của file và line

Không nên viết:

```java
// Không an toàn nếu file name không có:
String file = frame.getFileName().toUpperCase();
```

Vì `getFileName()` có thể là `null`. Tương tự, `getLineNumber()` có thể âm. Nếu code cần thông tin vị trí:

```java
String file = frame.getFileName();
int line = frame.getLineNumber();

String location = (file != null && line >= 0)
        ? file + ":" + line
        : "Vị trí nguồn không khả dụng";
```

Đây là đoạn minh họa trong method có biến `frame` thích hợp. `line == -2` thường biểu thị frame native theo biểu diễn Java; **kiểm tra `isNativeMethod()`** rõ nghĩa hơn khi cần phân loại native.

### 3.2. `getClassName()` không phải `Class<?>`

`getClassName()` trả về **`String`**, không phải một đối tượng `Class`. Không nhất thiết nên gọi `Class.forName(frame.getClassName())`: việc tải class có thể thất bại, sử dụng sai class loader, hoặc có side effect không cần thiết. Để log/chẩn đoán, chuỗi tên lớp thường là đủ.

### 3.3. Phương thức có thể bị trùng tên

Hai phương thức overload có cùng `getMethodName()` nhưng khác tham số. Một `StackTraceElement` không chứa đầy đủ danh sách parameter types để tự khôi phục chữ ký overload. Nếu cần tìm chính xác overload gây lỗi, kết hợp tên lớp, tên method, file/dòng và mã nguồn tương ứng, hoặc công cụ debug có dữ liệu phong phú hơn.

## 4. Java 17 — ví dụ đầu tiên: đọc ba frame đầu của lỗi

Tạo file **`StackTraceBasicsDemo.java`**:

```java
public class StackTraceBasicsDemo {
    static void leaf() {
        throw new IllegalArgumentException("Số lượng không hợp lệ");
    }

    static void middle() {
        leaf();
    }

    public static void main(String[] args) {
        try {
            middle();
        } catch (IllegalArgumentException e) {
            StackTraceElement[] frames = e.getStackTrace();
            System.out.println("Loại: "
                    + e.getClass().getSimpleName());
            System.out.println("Frame count >= 3: "
                    + (frames.length >= 3));

            for (int i = 0; i < 3; i++) {
                System.out.println(i + ": "
                        + frames[i].getClassName() + "."
                        + frames[i].getMethodName());
            }
            System.out.println("File đầu: "
                    + frames[0].getFileName());
        }
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 StackTraceBasicsDemo.java
java StackTraceBasicsDemo
```

**Stdout khi chạy trực tiếp `java StackTraceBasicsDemo` như trên:**

```text
Loại: IllegalArgumentException
Frame count >= 3: true
0: StackTraceBasicsDemo.leaf
1: StackTraceBasicsDemo.middle
2: StackTraceBasicsDemo.main
File đầu: StackTraceBasicsDemo.java
```

Giải thích:

1. `leaf()` **tạo** `IllegalArgumentException` khi chạy `new`, nên frame đầu phản ánh `leaf`.
2. `middle()` là caller của `leaf()`, nên thường đứng sau.
3. `main()` gọi `middle()`, nên là frame kế tiếp.
4. `getFileName()` trả tên file nguồn đã được compiler ghi nhận. Nếu biên dịch theo cấu hình debug/source metadata khác hoặc chạy qua framework/hạ tầng bổ sung, thông tin file và frame có thể khác; đừng kiểm tra chuỗi này như điều kiện nghiệp vụ.
5. Code ví dụ cố tình **không in số dòng** vì thay đổi một dòng trong tệp có thể làm số dòng của stack trace thay đổi.

Trong ứng dụng thực tế, không được giả định luôn tồn tại ba frame. Ví dụ học tập có đường gọi cụ thể nên minh họa ba phần tử; với thư viện hoặc ngoại lệ cấu hình khác, cần kiểm tra `frames.length` trước khi truy cập chỉ số.

### 4.1. Nhớ đọc frame đầu theo đúng ngữ cảnh

Một frame đầu thuộc thư viện Java như `Integer.parseInt` không có nghĩa “lỗi nằm trong JDK”. Nó có thể chỉ là nơi thư viện **phát hiện** và ném lỗi vì input do caller truyền vào. Nên đọc tiếp các frame caller của ứng dụng để tìm nơi input sai được đưa vào.

Ví dụ `NumberFormatException` có thể khởi tạo trong logic parse của thư viện, còn vấn đề thực sự là validation ở tầng gọi. Trace là **manh mối vị trí**, không phải kết luận nguyên nhân cuối cùng.

## 5. `getStackTrace()` đọc dấu vết đã có, không tự ghi dấu vết mới

Với `Throwable` thông thường, constructor tạo exception đồng thời ghi stack trace. Ném lại **chính instance đó** bằng `throw error;` không mặc nhiên ghi lại trace tại vị trí ném, và gọi `getStackTrace()` không chụp call stack hiện tại. Do đó một frame đầu thuộc phương thức tạo exception có thể vẫn xuất hiện ngay cả khi lỗi được bắt ở tầng cao.

~~~java
IllegalStateException error =
        new IllegalStateException("created here");
StackTraceElement[] frames = error.getStackTrace();
if (frames.length > 0) {
    System.out.println(frames[0].getMethodName());
}
~~~

Đây là mảnh minh họa bên trong phương thức, không cam kết tên frame cố định. Khi cần xem **thời điểm ghi và sự khác biệt giữa tạo/throw/rethrow**, xem bài về thời điểm ghi stack trace; khi cần **chủ động cập nhật**, xem bài `fillInStackTrace`. Bài này tập trung đọc mảng frame và các trường của `StackTraceElement`.

## 6. `getStackTrace()` trả bản sao của mảng

Lời gọi:

```java
StackTraceElement[] snapshot = error.getStackTrace();
```

trả **mảng mới** chứa các phần tử `StackTraceElement`. Các đối tượng phần tử là kiểu bất biến; việc thay `snapshot[0]` bằng một object mới chỉ thay mảng bạn giữ, không sửa mảng trace bên trong `error`.

Nếu muốn chủ động thay trace của một `Throwable`, Java có API `setStackTrace(StackTraceElement[])`. API đó cũng xử lý bảo vệ dữ liệu bằng sao chép mảng truyền vào. Nó hữu ích trong một số framework/chẩn đoán đặc biệt, nhưng ít cần với code nghiệp vụ thông thường.

### 6.1. Java 17 — minh họa defensive copy và `setStackTrace()`

Tạo **`StackTraceCopyDemo.java`**:

```java
public class StackTraceCopyDemo {
    public static void main(String[] args) {
        Throwable error = new Throwable("demo");

        StackTraceElement[] snapshot = error.getStackTrace();
        System.out.println("Trước sửa mảng: "
                + snapshot[0].getMethodName());

        snapshot[0] = new StackTraceElement(
                "fake.Wrong", "wrong", "Wrong.java", 1);

        System.out.println("Trong Throwable: "
                + error.getStackTrace()[0].getMethodName());

        StackTraceElement[] synthetic = {
                new StackTraceElement(
                        "demo.Service", "execute", "Service.java", 42)
        };
        error.setStackTrace(synthetic);

        synthetic[0] = new StackTraceElement(
                "other.Class", "changed", "Changed.java", 99);

        System.out.println("Đã đặt trace: "
                + error.getStackTrace()[0]);
        System.out.println("Số frame mới: "
                + error.getStackTrace().length);
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 StackTraceCopyDemo.java
java StackTraceCopyDemo
```

Stdout:

```text
Trước sửa mảng: main
Trong Throwable: main
Đã đặt trace: demo.Service.execute(Service.java:42)
Số frame mới: 1
```

Thử nghiệm có hai lớp bảo vệ:

1. Sau khi sửa `snapshot[0]`, trace đọc lại từ `error` **vẫn chứa `main`**.
2. Sau `setStackTrace(synthetic)`, việc sửa chính mảng `synthetic` không ảnh hưởng trace đã được đặt.

**Không nên dùng `setStackTrace()` để giả mạo nguồn gốc lỗi trong hệ thống thật**. Ví dụ này chỉ giải thích API và cách sao chép. Các công cụ log và giám sát thường tin các frame, nên việc tùy ý thay dữ liệu có thể làm sai chẩn đoán.

### 6.2. Các ràng buộc của `setStackTrace()`

- `setStackTrace(null)` không hợp lệ.
- Mảng chứa phần tử `null` không hợp lệ.
- Với Throwable có cấu hình **writable stack trace = false**, lời gọi `setStackTrace(...)` không có tác dụng theo hợp đồng API.
- Không nên giả định mảng tự tăng/giảm theo stack hiện tại: trace là dữ liệu gắn với đối tượng `Throwable`.

`StackTraceElement` có constructor công khai cho phép tạo phần tử từ các trường cụ thể, nhưng dựng frame bằng tay **không tạo một lời gọi phương thức thật**. Đây là dữ liệu mô tả, không phải thay đổi call stack JVM.

## 7. Tạo và khám phá một `StackTraceElement` độc lập

Một `StackTraceElement` có thể được tạo thủ công:

```java
new StackTraceElement(
        "example.InventoryService",
        "reserve",
        "InventoryService.java",
        27);
```

Bốn tham số lần lượt là tên lớp khai báo, tên phương thức, tên file nguồn và dòng nguồn. Có constructor mở rộng trên Java hiện đại chứa thông tin class loader/module, nhưng khi học Java Core thường chỉ cần bốn trường này trước.

### 7.1. Java 17 — khám phá getter của một frame

Tạo **`StackTraceElementFieldsDemo.java`**:

```java
public class StackTraceElementFieldsDemo {
    public static void main(String[] args) {
        StackTraceElement frame = new StackTraceElement(
                "example.InventoryService",
                "reserve",
                "InventoryService.java",
                27);

        System.out.println("Class: " + frame.getClassName());
        System.out.println("Method: " + frame.getMethodName());
        System.out.println("File: " + frame.getFileName());
        System.out.println("Line: " + frame.getLineNumber());
        System.out.println("Native: " + frame.isNativeMethod());
        System.out.println("Formatted: " + frame);

        StackTraceElement nativeFrame = new StackTraceElement(
                "example.NativeBridge", "callNative", null, -2);
        System.out.println("Native frame: "
                + nativeFrame.isNativeMethod());
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 StackTraceElementFieldsDemo.java
java StackTraceElementFieldsDemo
```

Stdout:

```text
Class: example.InventoryService
Method: reserve
File: InventoryService.java
Line: 27
Native: false
Formatted: example.InventoryService.reserve(InventoryService.java:27)
Native frame: true
```

Ví dụ này tự tạo frame để đầu ra không phụ thuộc line number thực tế của file Java. Nó chứng minh cách đọc các trường API, **không khẳng định** một phương thức `InventoryService.reserve()` thật đã chạy.

### 7.2. Native frame và số dòng âm

Trong cấu trúc frame, một số dòng âm có ý nghĩa không có line source bình thường. Trường hợp `lineNumber == -2` được sử dụng để biểu diễn native method; `isNativeMethod()` trả `true`. Khi in `toString()`, native frame có thể được hiển thị theo dạng `Native Method`.

Với số dòng không khả dụng, hãy hiển thị “không rõ” thay vì coi nó là dòng -1 của file nguồn. Nếu dùng dữ liệu trace cho analytics, cần có nhánh xử lý `getFileName() == null` và `getLineNumber() < 0`.

## 8. `printStackTrace()` so với `getStackTrace()`

Cả hai đều liên quan đến stack trace, nhưng mục đích khác nhau.

| API | Kết quả | Khi nên dùng |
| --- | --- | --- |
| `e.printStackTrace()` | In một biểu diễn phục vụ con người ra `System.err` theo mặc định | Chẩn đoán nhanh trong ví dụ nhỏ |
| `e.printStackTrace(PrintStream)` | In ra stream đã chỉ định | Công cụ chuyên dụng, tương thích API cũ |
| `e.printStackTrace(PrintWriter)` | In ra writer đã chỉ định | Tích hợp nguồn xuất khác |
| `e.getStackTrace()` | Trả mảng `StackTraceElement[]` | Duyệt/filter frame, phân tích cấu trúc |
| `e.getCause()` | Trả nguyên nhân nếu có | Chẩn đoán chuỗi exception wrapping |
| `e.getSuppressed()` | Trả mảng exception phụ nếu có | Chẩn đoán lỗi thứ cấp, đặc biệt từ try-with-resources |

`getStackTrace()` chỉ trả stack của **chính Throwable đó**. Nó không tự nối các frame của `getCause()` hoặc suppressed exceptions thành một mảng duy nhất.

Trong khi đó, `printStackTrace()` mặc định hiển thị thêm chuỗi `Caused by` và những ngoại lệ `Suppressed` theo định dạng của nền tảng. Đó là lý do hai thao tác đôi khi cho cảm giác “lượng thông tin khác nhau”.

### 8.1. Chương trình Java 17 — cause và suppressed không nằm trong cùng mảng

Tạo **`ThrowableTraceRelationsDemo.java`**:

```java
import java.io.IOException;

public class ThrowableTraceRelationsDemo {
    public static void main(String[] args) {
        IOException root = new IOException("Nguồn đọc lỗi");
        IllegalStateException wrapper =
                new IllegalStateException("Không hoàn tất", root);
        wrapper.addSuppressed(new IOException("Đóng thất bại"));

        System.out.println("Wrapper: "
                + wrapper.getClass().getSimpleName());
        System.out.println("Wrapper top: "
                + wrapper.getStackTrace()[0].getMethodName());
        System.out.println("Cause: "
                + wrapper.getCause().getClass().getSimpleName());
        System.out.println("Suppressed: "
                + wrapper.getSuppressed()[0]
                        .getClass().getSimpleName());
        System.out.println("Cause là frame trong mảng? "
                + (wrapper.getStackTrace() instanceof StackTraceElement[]));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ThrowableTraceRelationsDemo.java
java ThrowableTraceRelationsDemo
```

Stdout:

```text
Wrapper: IllegalStateException
Wrapper top: main
Cause: IOException
Suppressed: IOException
Cause là frame trong mảng? true
```

Dòng cuối chứng minh kiểu trả về của `getStackTrace()` là mảng frame, **không phải** danh sách ngoại lệ nguyên nhân; tuy nhiên chỉ biểu thị kiểu bằng `instanceof` nên không nên hiểu đó là phép kiểm tra cause thực tế.

Để hiển thị đầy đủ một báo cáo lỗi, nên log **đối tượng `Throwable`** qua logger phù hợp, hoặc duyệt `getCause()` và `getSuppressed()` nếu đang viết công cụ phân tích cấu trúc. Tránh log chỉ `e.getMessage()` rồi đánh mất những quan hệ cần cho debug.

### 8.2. Không nhầm `getStackTrace()` với chuỗi do logger tạo

Logger có thể thêm timestamp, thread name, correlation id, tên ứng dụng, stack-trace shortening và masking dữ liệu. Mảng `StackTraceElement[]` **không** tự chứa toàn bộ trường này. Do đó để truy vết request, phải truyền context qua logging/tracing thích hợp; chỉ một stack trace không thay thế hệ thống quan sát ứng dụng.

## 9. Hiểu stack trace từ exception được bọc

Xét:

```java
try {
    loadFile();
} catch (java.io.IOException original) {
    throw new IllegalStateException("Không tải được cấu hình", original);
}
```

Đây là mảnh minh họa; `loadFile()` phải tồn tại và khai báo/throw `IOException` thích hợp.

- `original.getStackTrace()` mô tả đường gọi dẫn đến **IOException gốc**.
- Đối tượng `IllegalStateException` được tạo trong handler có **trace riêng**, thường bắt đầu ở vị trí tạo wrapper.
- `wrapper.getCause()` liên kết sang exception gốc.
- `wrapper.getStackTrace()` **không tự chứa toàn bộ frame của original**.
- `printStackTrace()` của wrapper thường có phần `Caused by` để giúp người đọc nhìn cả hai trace, đôi khi rút gọn những frame chung.

Trong hệ thống thực tế, dùng constructor nhận `cause` như `new IllegalStateException("...", original)` thường tốt hơn chỉ sao chép message. Nếu bỏ cause, mối liên kết giữa hai exception sẽ không còn sẵn trong dữ liệu Throwable.

### 9.1. Handler `catch` không chọn theo frame

Một `catch (IOException e)` được chọn theo **kiểu runtime của đối tượng exception đang ném**, không theo tên lớp xuất hiện ở bất kỳ `StackTraceElement` nào. Trace có thể chứa frame của `java.io`, nhưng điều đó không biến `IllegalStateException` thành `IOException`.

Tương tự, một wrapper `IllegalStateException` có `cause` là `IOException` sẽ bị `catch(IllegalStateException)` hoặc `catch(Exception)` bắt, không tự chuyển vào `catch(IOException)`.

## 10. Độ tin cậy và giới hạn của stack trace

Stack trace rất có ích, nhưng nó không phải bản ghi hoàn hảo của mọi sự kiện.

### 10.1. Không được giả định có số dòng ổn định

Số dòng thay đổi khi thêm comment, refactor hoặc tối ưu build. Thông tin debug/source có thể vắng mặt. Kiểm thử tốt thường xác nhận **kiểu exception và kết quả hành vi**; nếu cần kiểm tra frame, kiểm tra tên method/class có ý nghĩa ở phạm vi được kiểm soát, không hardcode `StackTraceElement.toString()` với một line number.

### 10.2. Trace có thể thiếu hoặc rút gọn

Một `Throwable` có thể có trace rỗng nếu thông tin không được cung cấp hoặc bị vô hiệu hóa. JVM cũng có quyền tối ưu một số đường exception. Ví dụ, một số JVM có thể lược bớt stack trace của các exception lặp lại nhiều lần ở các tình huống hiệu năng đặc thù.

Không viết code nghiệp vụ dựa trên `getStackTrace()[0]` mà không kiểm tra `length > 0` nếu dữ liệu đến từ exception bất kỳ.

### 10.3. Trace là dữ liệu của Throwable, không phải ảnh chụp thread hiện tại

Nếu một exception được tạo ở thread A rồi truyền qua queue và xử lý ở thread B, `e.getStackTrace()` thường vẫn biểu diễn vị trí **tạo/capture trước đó**, không phải call stack hiện tại của B. Để quan sát stack của thread đang chạy, có thể dùng `Thread.currentThread().getStackTrace()` hoặc `StackWalker` (Java 9+), nhưng đây là API khác với stack của Throwable.

### 10.4. Stack trace có chi phí

Việc điền stack trace lúc tạo Throwable và lấy/định dạng frame có chi phí CPU/bộ nhớ. Không nên tạo exception hàng triệu lần như cơ chế điều khiển luồng thông thường hoặc gọi `printStackTrace()` ở mỗi vòng lặp hiệu năng cao. Nếu thực sự cần exception nhẹ trong một hot path, đó là tối ưu chuyên biệt phải được đo đạc và cân nhắc hậu quả mất chẩn đoán.

### 10.5. Bảo mật và quyền riêng tư

Tên class/method, đường xử lý, đôi khi tên file nguồn/module có thể tiết lộ cấu trúc ứng dụng. Trong hệ thống API công khai, không nên trả toàn bộ stack trace cho người dùng cuối. Hãy log có kiểm soát ở phía server, dùng correlation id cho người dùng và lọc thông tin nhạy cảm theo chính sách logging. Stack trace cũng không bảo đảm chứa giá trị tham số; không được suy đoán input chỉ từ tên frame.

## 11. Thực hành viết bộ lọc frame đúng cách

Giả sử muốn tìm frame đầu tiên thuộc package ứng dụng. **Không** dựa vào vị trí thứ hai hay thứ ba một cách tuyệt đối; duyệt đến khi gặp frame phù hợp.

Ví dụ hàm tiện ích:

```java
static StackTraceElement findAppFrame(
        Throwable error, String packagePrefix) {
    for (StackTraceElement frame : error.getStackTrace()) {
        if (frame.getClassName().startsWith(packagePrefix)) {
            return frame;
        }
    }
    return null;
}
```

Đây là method minh họa phải đặt trong lớp; không chứa `main`. Kết quả có thể là `null` nếu không có frame khớp.

Một số lưu ý:

- Prefix `"com.example"` có thể khớp cả `com.exampleOther`. Nếu muốn giới hạn package chính xác, cân nhắc `"com.example."` cùng xử lý lớp ở package gốc.
- Tên lớp có thể là lớp nội bộ/lambda với tên compiler sinh ra; không đảm bảo naming ổn định giữa build.
- Frame stack có thể thiếu metadata, nhưng `getClassName()` là thông tin cốt lõi của frame.
- Không dùng việc tìm frame làm logic phân quyền, chống gian lận hoặc xác thực caller. Mã có thể thay trace bằng `setStackTrace()` và môi trường runtime còn nhiều yếu tố khác.

### 11.1. Dùng `StackWalker` khi cần stack đang chạy

Kể từ Java 9, `java.lang.StackWalker` cung cấp API truy cập stack **hiện hành** theo luồng xử lý, phù hợp với một số công cụ profiling hoặc tracing. Nó không phải bản thay thế trực tiếp cho `Throwable.getStackTrace()`:

| Nhu cầu | Cách tiếp cận |
| --- | --- |
| Xem vị trí và đường gọi khi lỗi được ghi vào Throwable | `throwable.getStackTrace()` |
| In lỗi cùng cause/suppressed phục vụ debug | Logger ghi `Throwable`, hoặc `printStackTrace()` khi học tập |
| Lấy stack hiện tại của thread | `Thread.currentThread().getStackTrace()` hoặc `StackWalker` |
| Theo dõi luồng request qua nhiều thread/service | Distributed tracing, correlation id, logging context |
| Hiểu nguyên nhân nghiệp vụ của một exception | Kết hợp kiểu, thông điệp, cause, dữ liệu đầu vào, log |

Đừng trộn lẫn một stack trace **đã capture** với call stack **đang hoạt động**.

## 12. Các lỗi lập trình dễ mắc

| Sai lầm | Vì sao sai | Cách sửa |
| --- | --- | --- |
| Gọi `e.getStackTrace().toString()` rồi mong ra các frame | Đây là `toString()` của **mảng**, không tự nối phần tử thành stack trace đọc được | Duyệt từng frame, dùng `Arrays.toString(...)` nếu chỉ cần hiển thị đơn giản, hoặc logger ghi Throwable |
| Luôn truy cập `getStackTrace()[0]` | Mảng có thể rỗng | Kiểm tra độ dài trước |
| Hardcode line number trong test | Dòng thay đổi theo mã nguồn/compiler | Test kiểu/hành vi, hoặc frame method/class cần thiết |
| Cho rằng `getStackTrace()` luôn trả stack của dòng `throw` gần nhất | Trace thường được capture lúc tạo Throwable, không tự refresh mỗi lần rethrow | Hiểu `fillInStackTrace()` và vòng đời đối tượng |
| Sửa `frames[0]` rồi kỳ vọng Throwable thay đổi | Mảng trả về là defensive copy | Chỉ dùng `setStackTrace()` khi thật sự có lý do chẩn đoán đặc biệt |
| Xem `getStackTrace()` là danh sách cause | Trace là mảng **frame**, không phải chuỗi Throwable | Dùng `getCause()` và `getSuppressed()` riêng |
| Bắt exception bằng tên class xuất hiện trong frame | Handler `catch` dựa vào kiểu thực tế của exception đang ném | Dùng loại exception phù hợp |
| Gọi `getFileName().length()` không kiểm tra | File name có thể `null` | Kiểm tra `null` |
| Dùng số dòng âm như vị trí file thực | Âm biểu thị không có số dòng hợp lệ hoặc frame native | Dùng `isNativeMethod()` và fallback |
| Dùng tên method để xác định overload chính xác | Không có parameter signature trong `StackTraceElement` | Kết hợp file/dòng, mã nguồn, debugger |
| Log đầy đủ stack ra HTTP response | Có thể làm lộ cấu trúc nội bộ ứng dụng | Giữ log ở môi trường được kiểm soát |
| Nuốt exception chỉ vì đã in stack | In lỗi không phải phục hồi hay rollback | Truyền failure, xử lý hoặc ném lại theo hợp đồng |

### 12.1. Đừng quá phụ thuộc vào `printStackTrace()`

Trong dự án thật, một logger có hỗ trợ exception thường phù hợp hơn vì đi kèm mức log, timestamp, request id, cấu hình định dạng và vị trí lưu. Nhưng nếu logger chỉ được truyền `e.getMessage()`, bạn có thể **đánh mất stack trace**; cần dùng API logger cho phép truyền Throwable theo đúng framework.

Đây là nguyên tắc thiết kế, không phải lời khuyên dùng một thư viện log cụ thể.

## 13. Các câu hỏi thường gặp

### 13.1. `getStackTrace()` có làm exception bị bắt hoặc ném đi không?

Không. Nó chỉ đọc dữ liệu stack trace đã gắn với đối tượng. Muốn ném, dùng `throw`; muốn bắt, dùng `try/catch`.

### 13.2. `getStackTrace()` trả `null` không?

Theo hợp đồng API, nó trả một mảng `StackTraceElement[]`, có thể là mảng **rỗng** nếu không có thông tin. Vì thế kiểm tra `frames.length` thay vì so sánh `frames == null` trong code Java thông thường.

### 13.3. Vì sao frame đầu là `parseInt` thay vì method ứng dụng?

Bởi một exception được tạo ở nơi thư viện phát hiện lỗi. Đọc **các frame tiếp theo** để tìm lời gọi từ ứng dụng đã cung cấp dữ liệu không hợp lệ.

### 13.4. `Throwable.getStackTrace()` và `Thread.getStackTrace()` khác nhau thế nào?

Một bên lấy trace **đã lưu trong đối tượng Throwable**, một bên lấy stack của thread tương ứng tại lúc yêu cầu. Hai mảng có thể khác về thời điểm, thứ tự và nội dung.

### 13.5. `setStackTrace()` có sửa call stack JVM không?

Không. Nó thay **dữ liệu mô tả** lưu trong Throwable, không đẩy/pop frame của thread. Không dùng nó để điều khiển thực thi.

### 13.6. Một frame có thể không có tên tệp hoặc số dòng không?

Có. `getFileName()` có thể là `null`, `getLineNumber()` có thể âm. Không phụ thuộc rằng mọi frame đều trỏ được về một dòng nguồn trên máy phát triển.

### 13.7. `getStackTrace()` có hiển thị suppressed exception không?

Nó chỉ trả trace của đối tượng hiện tại. Muốn xem suppressed, gọi `getSuppressed()`; muốn xem nguyên nhân, gọi `getCause()`. Dạng in của `printStackTrace()` thường kết hợp các phần này để dễ đọc.

### 13.8. Mỗi lần `throw` một exception cũ, trace có tự thay đổi không?

Không mặc định. `throw e;` giữ cùng đối tượng và dữ liệu đã capture. Nếu tạo wrapper mới có cause là `e`, wrapper có trace riêng.

## 14. Bài tập có đáp án định hướng

### Bài 1 — Dự đoán thứ tự frame

Chạy `StackTraceBasicsDemo`, đổi thành chuỗi `main → outer → middle → leaf`. Dự đoán các frame đầu tiên. **Định hướng:** frame gần điểm tạo exception đứng trước; các caller dần xuất hiện phía sau.

### Bài 2 — Rethrow và capture

Trong `RethrowTraceDemo`, chuyển lời gọi `saved.fillInStackTrace()` sang một phương thức `refresh(saved)` rồi xem `getStackTrace()[0].getMethodName()` là gì. **Định hướng:** khi stack được chụp lại bên trong `refresh`, frame đầu phản ánh vị trí mới, không phải `create`.

### Bài 3 — Bản sao mảng

Trong `StackTraceCopyDemo`, tạo hai mảng bằng hai lần gọi `getStackTrace()`. Thay phần tử `0` trong mảng thứ nhất; mảng thứ hai và giá trị đọc lại có bị thay không? **Không**, vì các mảng là các snapshot riêng về tham chiếu phần tử.

### Bài 4 — Frame không có vị trí nguồn

Tạo `new StackTraceElement("a.B", "run", null, -1)`. In `getFileName()` và `getLineNumber()`. Viết hàm hiển thị fallback “unknown source” khi file hoặc line thiếu; tránh `NullPointerException`.

### Bài 5 — Cause và suppressed

Viết một `IOException original`, một `IllegalStateException wrapper` có `cause` là `original`, rồi thêm một suppressed `IOException` vào wrapper. In class name của mỗi đối tượng qua API phù hợp. **Định hướng:** một trace frame không phải một exception cause.

### Bài 6 — Kiểm thử ổn định

Tạo JUnit test xác nhận `getStackTrace()` có ít nhất một frame trong một tình huống được kiểm soát. Tránh assert giá trị dòng; có thể kiểm tra `getClassName()`/`getMethodName()`. Với exception tùy biến bị tắt writableStackTrace, test cần nhận mảng rỗng theo hợp đồng.

### Bài 7 — Thiết kế log

Một REST API gặp lỗi dữ liệu. So sánh: trả toàn bộ stack trace cho người dùng; trả mã lỗi cùng correlation id và log Throwable phía server. **Định hướng:** giải pháp thứ hai giúp chẩn đoán mà ít lộ cấu trúc nội bộ hơn.

## 15. Checklist sử dụng `getStackTrace()` đúng cách

- [ ] Tôi biết mảng trả về chứa **`StackTraceElement`**, không phải các đối tượng `Throwable` chưa?
- [ ] Tôi hiểu thứ tự frame từ điểm capture đến các caller không?
- [ ] Tôi đã phân biệt thời điểm **tạo/capture** exception với thời điểm `throw`/`catch` chưa?
- [ ] Tôi có kiểm tra `frames.length` trước khi đọc phần tử `0` không?
- [ ] Tôi tránh phụ thuộc tuyệt đối vào file/line/method name giữa các build/runtime chứ?
- [ ] Tôi đã xử lý `getFileName() == null` và `getLineNumber() < 0` chưa?
- [ ] Tôi nhớ `getStackTrace()` trả mảng sao chép, không thể sửa Throwable bằng thay phần tử mảng không?
- [ ] Nếu dùng `setStackTrace()`, tôi có hiểu đó là thay dữ liệu chẩn đoán, không thay call stack JVM không?
- [ ] Tôi đã phân biệt frame, `cause` và suppressed exception chưa?
- [ ] Tôi hiểu `printStackTrace()` phù hợp quan sát trực tiếp, còn `getStackTrace()` phù hợp xử lý dữ liệu có cấu trúc chưa?
- [ ] Tôi không dùng trace làm cơ chế kiểm tra quyền, xác minh caller hay điều kiện thành công của nghiệp vụ chứ?
- [ ] Việc ghi log/hiển thị stack đã phù hợp yêu cầu bảo mật và quyền riêng tư chưa?
- [ ] Tôi có test cả trường hợp trace bình thường và trace rỗng/thiếu metadata nếu API nhận lỗi bên ngoài không?

**Ghi nhớ:** `Throwable.getStackTrace()` trả một mảng các frame `StackTraceElement` mô tả đường gọi được ghi nhận của **chính đối tượng Throwable**. Đọc từng frame bằng `getClassName()`, `getMethodName()`, `getFileName()`, `getLineNumber()` và `isNativeMethod()`; kiểm tra mảng rỗng/metadata thiếu; phân biệt trace của wrapper với `cause`/`suppressed`. Stack trace là công cụ chẩn đoán, không phải hợp đồng nghiệp vụ hay ảnh chụp stack hiện tại của mọi thread.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="throwable-internals-006">fillInStackTrace và tác động khi cập nhật dấu vết</a>

<details>
<summary>Xem chi tiết</summary>

# fillInStackTrace và tác động khi cập nhật dấu vết trong Java

> **Java Core – Exception** · Nhóm: **Đối tượng Throwable** · Ví dụ sử dụng Java 17 trở lên.

## 1. Vấn đề: stack trace của một Throwable biểu diễn thời điểm nào?

Khi Java phát sinh lỗi, đối tượng ngoại lệ thường mang thông tin về **ngăn xếp lời gọi** (*stack trace*), giúp lần theo các phương thức đã thực thi tại một thời điểm. Ví dụ một phần dấu vết có dạng:

~~~text
java.lang.IllegalStateException: service unavailable
    at Example.makeFailure(Example.java:12)
    at Example.main(Example.java:20)
~~~

Các dòng trên chỉ là **minh họa định dạng**, không phải output cố định của chương trình trong bài. Tên file, số dòng, các frame và cách hiển thị thực tế phụ thuộc chương trình, compiler và JVM.

Trong Java, đối tượng `Throwable` thường ghi thông tin stack trace lúc **được tạo ra**, vì phần lớn constructor chuẩn gọi `fillInStackTrace()`. Khi ném lại **cùng một instance** sau đó, lệnh `throw ex;` không tự động ghi lại dấu vết hiện tại của đối tượng này.

Nếu lập trình viên chủ động gọi:

~~~java
ex.fillInStackTrace();
~~~

thì phương thức cố gắng **ghi lại thông tin stack frame của luồng đang chạy tại vị trí gọi hiện tại** vào **chính đối tượng `ex`**, thay cho thông tin trace trước đó, trừ trường hợp khả năng ghi stack trace đã bị vô hiệu hóa hoặc những giới hạn của JVM.

**Điểm cốt lõi:** `fillInStackTrace()` không tạo exception mới, không ném exception đó và không tự bọc cause. Nó **cập nhật vị trí nguồn gốc được thể hiện trong stack trace** của một đối tượng Throwable đang tồn tại.

Sau bài này, bạn có thể:

1. Mô tả điều gì được ghi khi tạo Throwable và điều gì **không** đổi khi chỉ ném lại cùng instance.
2. Giải thích `fillInStackTrace()` ghi lại stack của **thread gọi phương thức**, trả về đối tượng nào và tác động lên dấu vết cũ.
3. Phân biệt `fillInStackTrace()`, `getStackTrace()`, `setStackTrace()`, `printStackTrace()`.
4. Kiểm tra tác động đến `cause`, suppressed exceptions và các ngoại lệ có stack trace không ghi được.
5. Biết khi nào nên giữ stack trace gốc thay vì cập nhật, đặc biệt khi debug và truyền ngoại lệ qua nhiều tầng ứng dụng.

## 2. Định nghĩa API chính thức

Theo [Java SE 17 API – Throwable.fillInStackTrace](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html#fillInStackTrace()), phương thức có chữ ký:

~~~java
public synchronized Throwable fillInStackTrace()
~~~

Phương thức ghi thông tin về trạng thái stack frame hiện tại của **thread đang gọi** vào chính đối tượng `Throwable`. Giá trị trả về là **cùng tham chiếu `this`**. Nếu writable stack trace của Throwable bị tắt, lời gọi **không có tác dụng** lên dấu vết.

Khi một stack trace đã được ghi lại, các thao tác như `getStackTrace()` và `printStackTrace()` phản ánh thông tin mới theo quy tắc tương ứng.

| API | Chức năng | Có thay thế trace bên trong Throwable? |
| --- | --- | --- |
| `fillInStackTrace()` | Ghi lại dấu vết stack tại thread/vị trí gọi hiện tại | **Có**, nếu trace ghi được |
| `getStackTrace()` | Trả về mảng các `StackTraceElement` biểu diễn trace hiện hành | **Không** |
| `setStackTrace(StackTraceElement[])` | Ghi đè các stack-frame element bằng mảng được chỉ định | **Có**, nếu trace ghi được |
| `printStackTrace()` | In đối tượng exception và dấu vết liên quan tới stream mặc định | **Không** |
| `getCause()` | Xem exception nguyên nhân nếu có | **Không** |
| `getSuppressed()` | Xem các exception bị suppressed, thường do cleanup | **Không** |

Ví dụ:

~~~java
Throwable same = ex.fillInStackTrace();
System.out.println(same == ex); // true
~~~

Đây là **đoạn minh họa**; cần đặt trong một phương thức với biến `ex` thuộc kiểu Throwable đã được tạo.

Lưu ý: từ “ghi lại” mô tả kết quả ở cấp API, không phải cam kết về cách lưu trữ nội bộ như native backtrace, cấu trúc JVM hay vị trí bộ nhớ. JVM được phép không cung cấp đầy đủ mọi frame trong một số tình huống.

## 3. Thời điểm capture mặc định và tác động của rethrow

### 3.1. Thông thường trace hình thành khi tạo exception

Ví dụ:

~~~java
static RuntimeException createFailure() {
    return new IllegalStateException("service unavailable");
}
~~~

Đoạn trên là phương thức minh họa. Constructor `IllegalStateException` gọi cơ chế của lớp cha `Throwable` để điền stack trace. Trong một JVM thông thường, frame đầu được quan sát sẽ gắn với nơi **tạo instance**, ở đây là `createFailure`.

Việc exception **được tạo** và việc nó **được ném** là hai hành động khác nhau:

~~~java
RuntimeException failure = createFailure();
// ... có thể làm thêm công việc ...
throw failure;
~~~

Lệnh `throw failure;` sử dụng instance đã có. Bản thân thao tác này **không bắt buộc làm mới trace**; trên những JVM thông dụng, trace tiếp tục chỉ đến `createFailure`, không đột nhiên đổi sang dòng `throw failure;`.

### 3.2. Chủ động gọi fillInStackTrace trước khi ném lại

~~~java
failure.fillInStackTrace();
throw failure;
~~~

Cấu trúc minh họa này khác ở chỗ `fillInStackTrace()` chủ động ghi stack của thread tại nơi gọi. Nếu trace ghi được, frame phía trên có thể đổi sang phương thức đã thực hiện refresh. Sau đó `throw failure;` ném **cùng instance** với trace đã được cập nhật.

**Cảnh báo thiết kế:** Cập nhật trace có thể khiến thông tin vị trí xuất hiện lỗi **ban đầu** bị thay thế bằng nơi rethrow. Trong luồng debug, nơi phát sinh lỗi gốc thường có giá trị hơn nơi chuyển tiếp lỗi. Vì vậy **không nên tự động gọi `fillInStackTrace()` trước mọi lệnh rethrow**.

Nếu cần bổ sung bối cảnh tầng trên mà vẫn giữ nguyên lỗi gốc, thường phù hợp hơn với **exception chaining**:

~~~java
try {
    serviceCall();
} catch (IOException ex) {
    throw new IllegalStateException("Cannot complete request", ex);
}
~~~

Đây là **mã minh họa**, cần có phương thức `serviceCall()` và import `IOException`. Exception mới có trace tại nơi được tạo, còn `ex` được lưu làm `cause` với trace nguồn gốc riêng. Đây là thay đổi **đối tượng và quan hệ nguyên nhân**, khác hoàn toàn với cập nhật trace của instance cũ.

## 4. Chương trình Java 17 hoàn chỉnh

Tạo tệp **`FillInStackTraceDemo.java`**. Chương trình không dùng thư viện ngoài; mỗi ví dụ in giá trị ngắn có thể kiểm chứng, không in số dòng code hoặc toàn bộ stack trace dễ thay đổi.

~~~java
import java.io.IOException;

public class FillInStackTraceDemo {
    private static RuntimeException createFailure() {
        return new IllegalStateException("service unavailable");
    }

    private static String top(Throwable ex) {
        StackTraceElement[] frames = ex.getStackTrace();
        return frames.length == 0 ? "<empty>" : frames[0].getMethodName();
    }

    private static void throwSame(RuntimeException ex) {
        try {
            throw ex;
        } catch (RuntimeException caught) {
            System.out.println("same object=" + (caught == ex));
            System.out.println("top after throw=" + top(caught));
        }
    }

    private static void refreshHere(Throwable ex) {
        Throwable returned = ex.fillInStackTrace();
        System.out.println("fill returns this=" + (returned == ex));
        System.out.println("top after fill=" + top(ex));
    }

    private static final class NoTraceException extends Exception {
        NoTraceException() {
            super("no frames", null, true, false);
        }
    }

    public static void main(String[] args) {
        System.out.println("== creation and rethrow ==");
        RuntimeException original = createFailure();
        System.out.println("top on create=" + top(original));
        throwSame(original);
        refreshHere(original);
        throwSame(original);

        System.out.println("== manual trace ==");
        original.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("Synthetic", "custom", "Fake.java", 123)
        });
        System.out.println("top after set=" + top(original));
        refreshHere(original);

        System.out.println("== cause and suppressed ==");
        IllegalArgumentException cause = new IllegalArgumentException("root");
        IllegalStateException wrapped = new IllegalStateException("wrapper", cause);
        wrapped.addSuppressed(new IOException("close failed"));
        Throwable result = wrapped.fillInStackTrace();
        System.out.println("same after fill=" + (result == wrapped));
        System.out.println("cause same=" + (wrapped.getCause() == cause));
        System.out.println("suppressed=" + wrapped.getSuppressed().length);
        StackTraceElement[] copy = wrapped.getStackTrace();
        copy[0] = new StackTraceElement("Fake", "change", "X.java", 1);
        System.out.println("defensive copy=" + !top(wrapped).equals("change"));

        System.out.println("== disabled stack ==");
        NoTraceException hidden = new NoTraceException();
        System.out.println("initial size=" + hidden.getStackTrace().length);
        System.out.println("fill returns this=" + (hidden.fillInStackTrace() == hidden));
        hidden.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("Fake", "method", "X.java", 1)
        });
        System.out.println("after fill+set size=" + hidden.getStackTrace().length);
    }
}
~~~

Biên dịch theo mức tương thích **Java 17**, rồi chạy:

~~~bash
javac --release 17 -encoding UTF-8 FillInStackTraceDemo.java
java FillInStackTraceDemo
~~~

**Stdout thực tế, được kiểm chứng trên OpenJDK với đầy đủ frame của các phương thức minh họa:**

~~~text
== creation and rethrow ==
top on create=createFailure
same object=true
top after throw=createFailure
fill returns this=true
top after fill=refreshHere
same object=true
top after throw=refreshHere
== manual trace ==
top after set=custom
fill returns this=true
top after fill=refreshHere
== cause and suppressed ==
same after fill=true
cause same=true
suppressed=1
defensive copy=true
== disabled stack ==
initial size=0
fill returns this=true
after fill+set size=0
~~~

**Tính di động:** Javadoc cho phép một số JVM bỏ qua stack frame trong một số trường hợp, thậm chí trả về mảng rỗng. Vì vậy các dòng có tên phương thức đứng đầu như `createFailure` hoặc `refreshHere` là **kết quả của JVM đã được thử nghiệm**, không phải một cam kết bắt buộc cho mọi JVM, cờ tối ưu và môi trường. Các dòng kiểm tra cùng tham chiếu, cause và suppressed thể hiện hợp đồng API ổn định hơn. Trên JVM có dấu vết đầy đủ, kết quả cho thấy rõ thứ tự refresh.

## 5. Phân tích các phần của chương trình

### 5.1. Tạo ở createFailure rồi ném lại ở throwSame

Trong `main`, một instance `IllegalStateException` được tạo bởi `createFailure()`. Chương trình in:

~~~text
top on create=createFailure
~~~

Khi `throwSame(original)` thực thi, nó ném và bắt lại **chính đối tượng `original`**, kiểm tra `caught == ex` là `true`. Không có một constructor exception mới hoặc lời gọi `fillInStackTrace()`, nên trace vẫn chỉ tới nơi tạo:

~~~text
same object=true
top after throw=createFailure
~~~

**Điểm cần nhớ:** catch/rethrow không làm Java tự dựng lại dấu vết cho instance cũ. Đây là lý do log một exception được ném lại nhiều lần có thể hiển thị cùng vị trí nguồn gốc ban đầu.

### 5.2. refreshHere làm thay đổi frame ở đầu

Hàm:

~~~java
private static void refreshHere(Throwable ex) {
    Throwable returned = ex.fillInStackTrace();
    System.out.println("fill returns this=" + (returned == ex));
    System.out.println("top after fill=" + top(ex));
}
~~~

ghi lại dấu vết ở vị trí gọi. `returned == ex` là `true` vì API trả về chính instance được cập nhật. Trên JVM thử nghiệm, frame đầu đổi từ `createFailure` sang `refreshHere`.

Nếu sau đó cùng instance được ném lại, `throwSame` vẫn thấy frame `refreshHere`, không tự thay đổi thành `throwSame`. Thao tác `throw` không phải lệnh refresh ngầm.

### 5.3. setStackTrace khác fillInStackTrace như thế nào?

Trong phần `manual trace`, đoạn:

~~~java
original.setStackTrace(new StackTraceElement[] {
        new StackTraceElement("Synthetic", "custom", "Fake.java", 123)
});
~~~

**Chủ động gán danh sách frame được cung cấp**, thay vì lấy stack hiện tại của thread. Do đó `top(original)` trả về `custom`. Sau một lời gọi `refreshHere(original)`, API `fillInStackTrace()` ghi lại thông tin stack hiện tại, nên trên JVM thử nghiệm `top` đổi về `refreshHere`.

Phân biệt:

| Thao tác | Nguồn dữ liệu frame | Tác dụng |
| --- | --- | --- |
| `new SomeException(...)` thông thường | Stack hiện tại tại lúc tạo | Thiết lập trace ban đầu |
| `throw existingException;` | Không tự capture thêm | Giữ trace cũ |
| `existingException.fillInStackTrace()` | Stack **hiện tại** của thread gọi | Làm mới dấu vết |
| `existingException.setStackTrace(array)` | Các `StackTraceElement` do code cung cấp | Thay thế biểu diễn dấu vết theo mảng |
| `existingException.getStackTrace()` | Dấu vết đang có bên trong exception | Trả bản sao mảng để đọc |

`setStackTrace()` chủ yếu hữu ích cho framework RPC, hệ thống truyền stack trace qua ranh giới tiến trình hoặc công cụ kỹ thuật chuyên sâu. Việc tự ý gán frame giả vào exception nghiệp vụ có thể **làm sai lệch nơi lỗi thực sự bắt đầu**. Không dùng nó để “sửa” lỗi bằng cách chỉ thay thông tin log.

### 5.4. Cause và suppressed không bị xóa bởi fillInStackTrace

Trong nhánh `cause and suppressed`:

~~~java
IllegalArgumentException cause = new IllegalArgumentException("root");
IllegalStateException wrapped = new IllegalStateException("wrapper", cause);
wrapped.addSuppressed(new IOException("close failed"));
Throwable result = wrapped.fillInStackTrace();
~~~

Ta có các quan hệ độc lập:

- `wrapped.getCause() == cause` vẫn là `true`.
- `wrapped.getSuppressed().length` vẫn là `1`.
- `result == wrapped` vẫn là `true`.

`fillInStackTrace()` làm mới **trace của wrapped**, không tạo một cause khác và không xóa danh sách suppressed. Cause cũng là một đối tượng Throwable riêng có dấu vết của nó; phương thức này không tự động cập nhật trace của cause.

Do đó **stack trace**, **exception chaining (cause)** và **suppressed exceptions** là những phần thông tin khác nhau của một đối tượng Throwable. Debug đáng tin phải biết phần nào được giữ và phần nào bị thay.

### 5.5. Mảng getStackTrace là bản sao phòng vệ

API `getStackTrace()` trả về mảng `StackTraceElement[]` để truy cập bằng chương trình. Việc sửa các phần tử trong mảng đã nhận **không làm thay đổi trace được lưu** bên trong Throwable.

Đoạn thử:

~~~java
StackTraceElement[] copy = wrapped.getStackTrace();
copy[0] = new StackTraceElement("Fake", "change", "X.java", 1);
System.out.println("defensive copy=" + !top(wrapped).equals("change"));
~~~

in `defensive copy=true` trên JVM đã kiểm chứng. Nếu muốn thay đổi stack trace hiển thị, cần gọi **`setStackTrace()`**; chỉ sửa một mảng trả về từ getter là không đủ.

Để mã chạy an toàn trên mọi JVM, trước khi truy cập `copy[0]` hãy kiểm tra độ dài mảng vì đặc tả cho phép dấu vết rỗng trong một số hoàn cảnh. Ví dụ đầy đủ ở trên được kiểm chứng trong môi trường có frame thông thường; đây không phải unit test dùng cho mọi cấu hình JVM. Mã sản xuất nên viết:

~~~java
StackTraceElement[] frames = ex.getStackTrace();
if (frames.length > 0) {
    System.out.println(frames[0].getMethodName());
}
~~~

Đoạn này chỉ minh họa xử lý mảng an toàn; `ex` cần là một Throwable đã tồn tại.

### 5.6. Trường hợp writableStackTrace bị vô hiệu hóa

JDK cung cấp constructor bảo vệ:

~~~java
protected Throwable(
        String message,
        Throwable cause,
        boolean enableSuppression,
        boolean writableStackTrace)
~~~

Một lớp exception con có thể chọn `writableStackTrace=false`:

~~~java
private static final class NoTraceException extends Exception {
    NoTraceException() {
        super("no frames", null, true, false);
    }
}
~~~

Trong ví dụ này, tham số thứ ba `true` **cho phép suppression**; tham số thứ tư `false` **vô hiệu hóa việc ghi stack trace**. Theo Javadoc, khi writable stack trace bằng false, constructor không gọi `fillInStackTrace()`, lời gọi `fillInStackTrace()` về sau không cập nhật dấu vết và `setStackTrace(...)` cũng không thiết lập frame; `getStackTrace()` trả về mảng dài 0.

Kết quả thử:

~~~text
initial size=0
fill returns this=true
after fill+set size=0
~~~

Ngay cả khi trace không ghi được, `fillInStackTrace()` vẫn trả về **chính đối tượng**. Không nên nhầm trường hợp này với suppression bị tắt: nếu `enableSuppression=false`, `getSuppressed()` rỗng và `addSuppressed` không ghi thêm; đó là cấu hình **độc lập** với writable stack trace.

## 6. Cập nhật dấu vết ảnh hưởng log và điều tra lỗi như thế nào?

Giả sử một lỗi thực sự xảy ra ở tầng đọc file, sau đó được chuyển qua một service và controller. Nếu giữ cùng instance:

~~~java
try {
    readInput();
} catch (IOException ex) {
    log(ex);
    throw ex;
}
~~~

đây là mã minh họa, cần định nghĩa `readInput()`, `log()` và khai báo/handle `IOException` theo hợp đồng. Exception có thể giữ trace tại điểm lỗi gốc. Nếu thêm `ex.fillInStackTrace();` ngay trước khi ném lại, trace thường bị cập nhật về **tầng service** đang rethrow; một số frame ban đầu ở tầng đọc có thể không còn trong trace mới.

Điều này có thể gây hệ quả:

1. **Mất vị trí tạo exception ban đầu trong phần trace chính.** Log về sau dễ hướng người điều tra vào chỗ chuyển tiếp thay vì nơi lỗi phát sinh.
2. **Thay đổi việc nhóm sự cố trong công cụ quan sát.** Một số hệ thống nhóm lỗi theo lớp, message và stack frame; refresh có thể khiến cùng lỗi được phân nhóm khác.
3. **Tạo thông tin dễ gây nhầm khi exception bị chia sẻ.** Nếu một instance được giữ trong cache hoặc chia sẻ giữa nhiều luồng, cập nhật trace sẽ phản ánh **thread gọi fill** gần nhất, không phải tự động giữ nguyên thread phát sinh lỗi ban đầu.
4. **Có chi phí thực thi.** Thu thập stack frame có thể tốn công sức, đặc biệt trong các đường nóng tạo/cập nhật nhiều Throwable. Không gọi đi gọi lại chỉ để thực hiện logging thông thường.
5. **Không khôi phục các side effect hoặc điều kiện chương trình.** Dấu vết là thông tin chẩn đoán; refresh không chạy lại thao tác đã lỗi, không rollback và không thay đổi kết quả logic vốn đã xảy ra.

Với ứng dụng thông thường, cách làm phù hợp hơn là giữ Throwable gốc và **bọc với cause khi cần bổ sung ngữ cảnh**:

~~~java
try {
    readInput();
} catch (IOException ex) {
    throw new IllegalStateException("Input processing failed", ex);
}
~~~

Kết quả là một wrapper mới tại tầng hiện tại **và** một cause giữ nguồn gốc riêng. Trong stack trace được in, thường có phần `Caused by: ...` cho exception gốc. Không cần phá dấu vết cũ bằng cách gọi `fillInStackTrace()`.

### Có bao giờ nên chủ động gọi fillInStackTrace không?

Có, nhưng nên có lý do kỹ thuật rõ. Ví dụ một framework lưu trữ Throwable đại diện cho sự kiện **mới tại thời điểm hiện tại** và có hợp đồng yêu cầu **đổi điểm chẩn đoán** trên cùng instance. Khi đó gọi `fillInStackTrace()` là chủ ý, không phải mặc định.

Nếu muốn giữ lại vị trí gốc trước khi refresh, đừng mặc định có cơ chế history tự động. Có thể lưu snapshot từ `getStackTrace()` hoặc thiết kế wrapper/cause phù hợp. Tuy nhiên, việc lưu snapshot thủ công và thao tác set lại trace cần chính sách minh bạch, vì trace có thể chứa thông tin nhạy cảm về cấu trúc mã và làm khó chẩn đoán nếu bị thay đổi tùy tiện.

## 7. Sự khác nhau giữa stack trace hiện có và stack hiện tại của thread

Hai khái niệm dễ nhầm:

- **Trace của Throwable:** những frame mà exception đang lưu hoặc hiển thị qua `getStackTrace()`; thường bắt đầu ở nơi Throwable được tạo hoặc lần cuối được gọi `fillInStackTrace()`.
- **Stack hiện tại của thread:** các phương thức đang có mặt trên call stack tại thời điểm quan sát. Có thể xem bằng công cụ debug, `Thread.currentThread().getStackTrace()` hoặc API `StackWalker` trong các tình huống thích hợp.

Gọi `ex.fillInStackTrace()` yêu cầu cập nhật **trace của ex** bằng trạng thái stack **của thread đang gọi**. Nó không tự tìm “thread từng ném ex đầu tiên” và cũng không phải thao tác lấy stack của mọi luồng.

Đặc biệt, nếu một instance ngoại lệ được tái sử dụng trong nhiều thread, kết quả sau khi refresh có thể chuyển sang ngữ cảnh khác hẳn nguồn gốc. Tái sử dụng/cập nhật Throwable ở nhiều thread cũng làm thông tin điều tra thiếu ổn định; thông thường nên tạo exception cho từng lần thất bại và giữ trace ban đầu.

## 8. JLS và JVM cho phép dấu vết không hoàn toàn đầy đủ

Dựa trên [API getStackTrace của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html#getStackTrace()), phần tử tại chỉ số 0 thường là frame ở đầu stack (gần điểm ghi trace nhất). Tuy nhiên, **không được mặc định mảng luôn có phần tử**. Tùy JVM và hoàn cảnh, một số frame có thể bị bỏ qua hoặc mảng có thể rỗng.

Vì vậy khi viết chương trình thực tế:

~~~java
StackTraceElement[] frames = ex.getStackTrace();
if (frames.length == 0) {
    System.out.println("No stack trace available");
} else {
    StackTraceElement first = frames[0];
    System.out.println(first.getClassName());
    System.out.println(first.getMethodName());
}
~~~

Đoạn trên là minh họa, cần có biến `ex` đã khai báo thuộc kiểu Throwable. Tên lớp/phương thức có thể hữu ích khi điều tra, nhưng **file name/line number không phải luôn khả dụng**, nhất là với code không có thông tin debug hoặc được thực thi/tối ưu theo cách khác.

Không nên dùng việc so sánh một chuỗi `StackTraceElement.toString()` chính xác đến dấu cách và số dòng làm tiêu chí nghiệp vụ. Trong bài tập, in tên phương thức giúp nhìn rõ hiệu ứng refresh; trong automated test dùng rộng rãi, hãy kiểm tra định nghĩa API và tránh khẳng định chính xác các frame trên mọi JVM.

## 9. setStackTrace và tính toàn vẹn của dữ liệu chẩn đoán

`setStackTrace(StackTraceElement[])` cho phép gán những frame được cung cấp cho Throwable. Nó không cần gọi stack hiện tại của thread để điền dữ liệu. API cũng **sao chép mảng đầu vào**, nên sửa mảng đầu vào sau khi gọi không tự thay đổi trace đã đặt.

Ví dụ minh họa:

~~~java
StackTraceElement[] frames = {
    new StackTraceElement("RpcService", "call", "RpcService.java", 18)
};
ex.setStackTrace(frames);
frames[0] = new StackTraceElement("Other", "changed", "Other.java", 1);
// Dòng trên không trực tiếp thay trace đã set trong ex.
~~~

Đây là đoạn code minh họa, yêu cầu `ex` là Throwable có writable stack trace. `setStackTrace` phù hợp với một số hệ thống RPC hoặc phục dựng dấu vết từ thông tin truyền qua mạng; thông thường nên tránh dùng để thay thế dấu vết của bug trong ứng dụng.

Khi trace không ghi được (constructor `writableStackTrace=false`), Javadoc nêu `setStackTrace` không có tác dụng **ngoài việc kiểm tra hợp lệ đối số**. Nó không trở thành cách bật lại writable trace. Cần tạo instance mới với cấu hình phù hợp nếu hợp đồng đòi hỏi có dấu vết.

### Có nên override fillInStackTrace để tăng hiệu năng?

Một số exception đặc thù có thể được thiết kế để bỏ thu thập stack trace, nhưng đây là quyết định đánh đổi trực tiếp **khả năng chẩn đoán**. Với các lớp con của Throwable, hãy cân nhắc constructor bảo vệ nhận `writableStackTrace=false` nếu thật sự phù hợp.

Ngoài ra, constructor của Throwable thông thường gọi cơ chế điền stack trace; việc override một phương thức có thể được gọi trong quá trình xây dựng đối tượng cần được thiết kế thận trọng vì trạng thái riêng của subclass có thể chưa khởi tạo đầy đủ. Đừng tự đưa các optimization này vào mọi exception nghiệp vụ nếu chưa đo được chi phí thực tế và chưa chấp nhận việc mất trace.

## 10. Phân biệt các thao tác bằng câu hỏi ngắn

| Tình huống | Cùng một Throwable? | Dấu vết được xử lý như thế nào? |
| --- | --- | --- |
| `throw existing;` | Có | Không tự làm mới trace |
| `existing.fillInStackTrace(); throw existing;` | Có | Refresh trace trước khi throw, nếu ghi được |
| `Throwable other = existing.fillInStackTrace();` | Có, `other == existing` | Cùng instance đã cập nhật |
| `throw new IllegalStateException("context", existing);` | Không, có wrapper mới | Wrapper có trace riêng; existing làm cause |
| `existing.setStackTrace(frames);` | Có | Trace hiển thị được gán bằng các frame cung cấp |
| `existing.getStackTrace();` | Có | Chỉ đọc bản sao mảng, không refresh |
| `existing.printStackTrace();` | Có | In trace hiện có, không refresh |
| Gọi fill khi `writableStackTrace=false` | Có | Không thay đổi trace; thường mảng rỗng |

**Lưu ý riêng:** Nếu biểu thức sau `throw` có giá trị `null`, Java ném một `NullPointerException` do runtime tạo; đó là một tình huống khác với việc gọi `fillInStackTrace()` trên một đối tượng Throwable khác null.

## 11. Các lỗi hiểu sai thường gặp

1. **“Mỗi lần throw là Java ghi lại stack trace.”** Sai: ném lại cùng instance không tự refresh trace.
2. **“fillInStackTrace tạo một exception mới.”** Sai: API trả về chính đối tượng đang gọi.
3. **“fillInStackTrace tự động ném exception.”** Sai: chỉ cập nhật thông tin; muốn truyền lỗi vẫn cần `throw` hoặc cơ chế tương ứng.
4. **“Cập nhật trace cũng cập nhật cause và suppressed.”** Sai: các quan hệ này độc lập.
5. **“getStackTrace trả về mảng có thể sửa để đổi trace nội bộ.”** Sai: mảng getter được sao chép bảo vệ.
6. **“setStackTrace và fillInStackTrace giống nhau.”** Sai: một phương thức dùng frame do code cung cấp; phương thức kia lấy trạng thái stack hiện tại của thread.
7. **“Refresh giúp giữ được dấu vết nguyên gốc.”** Thường là ngược lại: trace cũ có thể bị thay thế.
8. **“Stack trace luôn có đầy đủ mọi frame và số dòng.”** Sai: API cho phép JVM bỏ qua frame hoặc trả mảng rỗng.
9. **“Không có trace là exception chưa thực sự tồn tại.”** Sai: Throwable có thể được tạo với stack trace không ghi được.
10. **“writableStackTrace=false cũng tự tắt suppression.”** Sai: hai cờ độc lập.
11. **“fillInStackTrace được gọi trên thread nào cũng luôn ra stack của thread gây lỗi ban đầu.”** Sai: nó dùng thread **đang thực thi lời gọi refresh**.
12. **“Cứ refresh ở tầng controller để có log đẹp hơn.”** Có thể làm mất frame gốc; cân nhắc exception chaining.
13. **“Cập nhật trace sẽ retry hoặc rollback thao tác lỗi.”** Sai: chỉ thông tin chẩn đoán thay đổi.
14. **“Có thể tin tuyệt đối frame giả do setStackTrace.”** Không: một thư viện/framework có thể chủ động chỉnh nội dung trace, phải đánh giá tính tin cậy của nguồn.

## 12. Bài tập tự kiểm tra

**Câu 1.** Exception tạo ở `createFailure()`, sau đó ném lại ở `controller()` mà không gọi fill: trace phải tự chuyển sang controller không?

*Đáp án:* Không. `throw existing;` không tự làm mới stack trace.

**Câu 2.** `Throwable ref = ex.fillInStackTrace();` có tạo object mới không?

*Đáp án:* Không. `ref == ex` là `true`.

**Câu 3.** Nếu `refreshHere(ex)` gọi fill, khi ném ex ở nơi khác lần nữa, trace sẽ phản ánh bước refresh hay throw lần hai?

*Đáp án:* Thông thường phản ánh vị trí refresh gần nhất, trừ các trường hợp stack trace bị vô hiệu hóa hoặc JVM lược bớt frame.

**Câu 4.** Nếu ex có cause và một suppressed exception, gọi fill có xóa chúng không?

*Đáp án:* Không. Cause và suppressed tồn tại độc lập với thao tác cập nhật stack trace.

**Câu 5.** Nếu sửa `StackTraceElement[] a = ex.getStackTrace(); a[0] = ...;`, trace của ex có tự đổi không?

*Đáp án:* Không. Mảng do getter trả về là bản sao; cần sử dụng API setter thích hợp nếu chủ động thay trace.

**Câu 6.** Vì sao `getStackTrace()[0]` cần được kiểm tra độ dài?

*Đáp án:* Javadoc cho phép một số Throwable/JVM có mảng trace rỗng; truy cập phần tử 0 khi đó ném `ArrayIndexOutOfBoundsException`.

**Câu 7.** Một subclass gọi constructor `super(message, cause, true, false)`; gọi fill nhiều lần có làm mảng trace có frame?

*Đáp án:* Không. Cờ writable stack trace false khiến fill và set không cập nhật trace; getter trả về mảng rỗng.

**Câu 8.** Khi service cần bổ sung bối cảnh nhưng vẫn giữ nguồn lỗi gốc, nên cân nhắc giải pháp nào?

*Đáp án:* Tạo wrapper exception mới với thông điệp và cause là exception ban đầu; không mặc định refresh trace cũ.

## 13. Tài liệu chính thức

- [Java SE 17 API – Throwable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): các constructor và thuộc tính cause/suppression/writable stack trace.
- [Java SE 17 API – fillInStackTrace](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html#fillInStackTrace()): ghi thông tin stack hiện tại của thread và trả về cùng instance.
- [Java SE 17 API – getStackTrace](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html#getStackTrace()): thứ tự các frame và quyền JVM lược bớt trace.
- [Java SE 17 API – setStackTrace](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html#setStackTrace(java.lang.StackTraceElement%5B%5D)): gán mảng stack frames theo API.
- [Java SE 17 API – StackTraceElement](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StackTraceElement.html): cấu trúc từng frame và getter cho tên lớp/phương thức, file, số dòng.
- [Java SE 17 API – Throwable.getSuppressed](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html#getSuppressed()): đọc danh sách exception thứ cấp.
- [Java SE 17 JLS mục 14.18 – throw statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.18): cách ném object Throwable; rethrow không đồng nghĩa refresh.

**Ghi nhớ:** `fillInStackTrace()` **cập nhật dấu vết tại thời điểm gọi trên chính Throwable hiện có**, không tạo Throwable mới và không tự ném lỗi. Sau refresh, nơi bắt đầu trace có thể chuyển từ vị trí gây lỗi gốc sang nơi cập nhật, ảnh hưởng mạnh đến debug. Chỉ dùng khi thật sự muốn thay dấu vết; thông thường nên giữ trace nguyên bản và dùng exception chaining để thêm ngữ cảnh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="throwable-internals-007">setStackTrace và giới hạn khi tự thay đổi thông tin chẩn đoán</a>

<details>
<summary>Xem chi tiết</summary>

# `setStackTrace()` và giới hạn khi tự thay đổi thông tin chẩn đoán trong Java

Một đối tượng `Throwable` không chỉ lưu kiểu và thông điệp lỗi mà còn có thể mang **stack trace** — danh sách những khung gọi phương thức (*stack frames*) giúp lập trình viên hình dung nơi thông tin chẩn đoán được ghi nhận. Java cung cấp `Throwable.getStackTrace()` để đọc và `Throwable.setStackTrace(StackTraceElement[])` để **thay thế danh sách frame được hiển thị** cho một đối tượng ngoại lệ cụ thể.

Khả năng thay thế này hữu ích trong vài tình huống hạ tầng đặc biệt, chẳng hạn công cụ RPC cần dựng lại dấu vết nhận từ máy khác. Nhưng nó cũng có một giới hạn lớn: **stack trace có thể bị sửa không còn phản ánh trung thực đường thực thi**. `setStackTrace()` không sửa stack đang chạy của JVM, không chuyển ngoại lệ về quá khứ, không thay đổi nguyên nhân gây lỗi, và không chứng minh rằng những phương thức được liệt kê đã thực sự được gọi.

Bài này giải thích chính xác hợp đồng API, dữ liệu `StackTraceElement`, bản sao phòng vệ (*defensive copy*), tính khả ghi của stack trace, mối liên hệ với `fillInStackTrace()`, `cause`, `suppressed exceptions`, cùng cách dùng an toàn khi logging và truyền lỗi qua ranh giới hệ thống. Các chương trình độc lập sử dụng **Java 17+**.

## 1. Stack trace trên `Throwable` là gì?

Khi tạo một đối tượng ngoại lệ theo constructor thông thường, Java thường ghi lại thông tin khung gọi hiện tại bằng `fillInStackTrace()`. Mảng được trả về từ `getStackTrace()` thường chứa các frame theo thứ tự:

- **Phần tử đầu** (chỉ số 0): điểm gần nhất với lúc thông tin trace được ghi nhận; thường là nơi tạo đối tượng ngoại lệ.
- **Các phần tử tiếp theo**: những lời gọi ở tầng ngoài.
- **Phần tử cuối**: frame gần đáy chuỗi gọi được ghi nhận.

Ví dụ minh họa sơ đồ:

```text
main()
  └── service()
        └── repository()
              └── new IOException("read failed")

getStackTrace() thông thường:
[0] repository(...)
[1] service(...)
[2] main(...)
```

Đây là **sơ đồ ý niệm**, không phải cam kết về toàn bộ frame và số dòng xuất hiện ở mọi JVM. Đặc tả JDK cho phép VM bỏ qua một hoặc nhiều frame, thậm chí trả mảng rỗng trong những hoàn cảnh đặc biệt. Tối ưu hóa, thông tin debug và cách JVM ghi nhận stack có thể làm đầu ra khác nhau.

Một frame là đối tượng `StackTraceElement`. Nó thường chứa tên lớp, tên phương thức, tên tệp nguồn và số dòng. Tùy JDK, frame còn có thể chứa tên class loader, tên module và phiên bản module.

**Stack trace trên ngoại lệ không phải là live call stack.** Khi phương thức đã hoàn tất hoặc thread đã chuyển sang thực thi nơi khác, đối tượng ngoại lệ vẫn có thể giữ trace cũ. Việc đọc hay ghi thông tin này không làm thay đổi các frame thật đang thực thi.

## 2. Hợp đồng của `setStackTrace()`

Chữ ký phương thức:

```java
public void setStackTrace(StackTraceElement[] stackTrace)
```

Theo Javadoc của Java SE 17, phương thức này:

1. **Gán danh sách frame được trả về bởi `getStackTrace()`** và được thể hiện bởi `printStackTrace()` cùng các phương thức liên quan.
2. **Chấp nhận một mảng `StackTraceElement[]`**, gồm cả mảng rỗng. Mảng rỗng thể hiện không có frame trong trace được công bố.
3. **Sao chép mảng đầu vào** thay vì lưu trực tiếp tham chiếu mảng do bên gọi truyền vào. Sửa mảng đầu vào sau khi phương thức trả về không làm đổi trace đã được lưu.
4. **Kiểm tra `null`**: nếu chính mảng là `null` hoặc một phần tử trong mảng là `null`, nó ném `NullPointerException`.
5. **Không cập nhật trace nếu đối tượng có stack trace không khả ghi**, dù tham số vẫn được kiểm tra hợp lệ.

Javadoc nói phương thức này được thiết kế cho framework RPC và các hệ thống nâng cao cần thay thế trace mặc định, chẳng hạn dấu vết được chuyển qua cơ chế tuần tự hóa. Nó **không phải thao tác chuẩn nên gọi trên mọi ngoại lệ nghiệp vụ**.

### Điều gì thực sự được sửa?

| Thành phần | `setStackTrace()` có thay đổi không? |
| --- | --- |
| Mảng frame do `Throwable.getStackTrace()` cung cấp cho đối tượng này | **Có**, nếu trace khả ghi và mảng hợp lệ |
| Phần frame chính của `printStackTrace()` cho đối tượng này | **Có**, hiển thị theo trace đã đặt |
| Loại lớp ngoại lệ, ví dụ `IOException` hay `IllegalStateException` | **Không** |
| `getMessage()`, mã lỗi nghiệp vụ riêng | **Không** |
| `getCause()` và nguyên nhân lỗi gốc | **Không** |
| Danh sách `getSuppressed()` | **Không** |
| Stack frame thực tế đang hoạt động của JVM/thread | **Không** |
| Những dòng mã đã thật sự chạy trước khi ngoại lệ xuất hiện | **Không** |
| Khả năng `catch` chọn kiểu ngoại lệ tương thích | **Không** |

Hệ quả quan trọng: không có gì ngăn bạn tạo `StackTraceElement` mang tên một lớp hoặc phương thức **không hề được thực thi** rồi gán vào trace. Khi đó log có thể chứa thông tin trông giống thật nhưng do ứng dụng tự cung cấp.

## 3. Chương trình Java 17+: thay thế trace bằng frame tự tạo

Lưu thành `SetStackTraceDemo.java`:

```java
public class SetStackTraceDemo {
    public static void main(String[] args) {
        IllegalStateException failure =
                new IllegalStateException("request failed");

        StackTraceElement remote = new StackTraceElement(
                "remote.Worker", "run", "Worker.java", 120);

        StackTraceElement[] replacement = {remote};
        failure.setStackTrace(replacement);

        System.out.println("frame=" + failure.getStackTrace()[0]);
        System.out.println("message=" + failure.getMessage());

        replacement[0] = new StackTraceElement(
                "changed.Place", "other", "Other.java", 1);
        System.out.println("after source-array change="
                + failure.getStackTrace()[0].getClassName());

        StackTraceElement[] snapshot = failure.getStackTrace();
        snapshot[0] = new StackTraceElement(
                "another.Place", "fake", "Fake.java", 2);
        System.out.println("after returned-array change="
                + failure.getStackTrace()[0].getClassName());
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 SetStackTraceDemo.java
java SetStackTraceDemo
```

Kết quả xác định khi dùng JDK 17+ với tên lớp đơn giản trong demo:

```text
frame=remote.Worker.run(Worker.java:120)
message=request failed
after source-array change=remote.Worker
after returned-array change=remote.Worker
```

Giải thích:

- `remote` là một frame **tổng hợp**, được tạo bằng constructor `StackTraceElement`. Không có lời gọi thật nào tới `remote.Worker.run` trong chương trình này.
- `setStackTrace(replacement)` thay thế trace mặc định của `failure` bằng mảng một phần tử. `getStackTrace()[0]` và phần frame của `printStackTrace()` sẽ sử dụng giá trị tổng hợp ấy.
- Thay `replacement[0]` sau khi gọi `setStackTrace` không làm đổi trace đã lưu, vì phương thức đã sao chép mảng đầu vào.
- Tương tự, thay một phần tử của **mảng được `getStackTrace()` trả về** không cập nhật trace bên trong `Throwable`.
- `getMessage()` vẫn trả `request failed`: can thiệp frame không sửa nội dung message.

**Đừng đưa frame giả vào log sản xuất như thể đó là bằng chứng về đường gọi thật.** Ví dụ có mục tiêu kiểm chứng hợp đồng API; tên `remote.Worker` chỉ là nhãn minh họa.

## 4. `StackTraceElement` chứa những trường nào?

Constructor cơ bản:

```java
new StackTraceElement(
        String declaringClass,
        String methodName,
        String fileName,
        int lineNumber)
```

Các tham số:

| Tham số | Ý nghĩa |
| --- | --- |
| `declaringClass` | Tên lớp được ghi trong frame; không được `null` |
| `methodName` | Tên phương thức; không được `null` |
| `fileName` | Tên tệp nguồn; **có thể `null`** khi không có thông tin |
| `lineNumber` | Số dòng; giá trị âm biểu thị thiếu hoặc dạng thông tin đặc biệt |
| `-2` ở `lineNumber` | Quy ước thể hiện phương thức native khi dùng constructor này |

Từ Java 9 còn có constructor bảy tham số, bổ sung `classLoaderName`, `moduleName` và `moduleVersion` trước bốn tham số kể trên. Những trường mở rộng thường không cần thiết cho việc học `setStackTrace` cơ bản.

Các phương thức đọc thông tin hữu ích gồm:

- `getClassName()`, `getMethodName()`;
- `getFileName()`, `getLineNumber()`;
- `isNativeMethod()`;
- `getModuleName()`, `getModuleVersion()`, `getClassLoaderName()` (nếu có thông tin).

Một frame do chương trình tự dựng **không được JVM kiểm chứng rằng tên lớp, tên phương thức, tệp và số dòng ấy thật sự có liên hệ với nhau**. Điều này làm `StackTraceElement` hữu ích để biểu diễn dấu vết từ xa nhưng cũng khiến trace bị sửa có thể đánh lừa người đọc.

### Ví dụ minh họa cấu trúc frame

```java
StackTraceElement element = new StackTraceElement(
        "example.Service", "execute", "Service.java", 25);

System.out.println(element.getClassName());   // example.Service
System.out.println(element.getMethodName());  // execute
System.out.println(element.getFileName());    // Service.java
System.out.println(element.getLineNumber());  // 25
```

Đoạn trên là **mã đặt trong một phương thức**, không phải tệp chương trình độc lập. Nó minh họa cách đọc dữ liệu đã cung cấp, **không có nghĩa mã nguồn thực tế chứa `Service.java` ở dòng 25**.

## 5. Bản sao phòng vệ: vì sao sửa mảng chưa đủ?

Hai API đều tách mảng mà bên gọi thao tác ra khỏi trạng thái nội bộ:

```text
Mảng đầu vào do ứng dụng tạo
    |
    | setStackTrace(input)     [sao chép]
    v
Dữ liệu stack trace gắn với Throwable
    |
    | getStackTrace()         [trả mảng độc lập]
    v
Mảng mà bên gọi có thể sửa
```

Vì thế mã kiểu sau **không thay đổi trace đang lưu**:

```java
StackTraceElement[] frames = failure.getStackTrace();
frames[0] = new StackTraceElement("x.Y", "z", "Y.java", 10);
```

Đây là đoạn minh họa khi `failure` là một `Throwable` có trace không rỗng; nhớ kiểm tra `frames.length > 0` trước khi truy cập chỉ số 0 trong mã tổng quát.

Nếu cần thay đổi có chủ đích, phải tạo mảng đã sửa và gọi lại:

```java
StackTraceElement[] frames = failure.getStackTrace();
if (frames.length > 0) {
    frames[0] = new StackTraceElement(
            "example.NewOrigin", "process", "NewOrigin.java", 15);
    failure.setStackTrace(frames);
}
```

Đoạn này là **mẫu minh họa cơ chế**, không phải khuyến nghị thay frame thật bằng tên giả trong hệ thống. Phần tử `StackTraceElement` là đối tượng biểu diễn dữ liệu; người dùng không có API setter thay className/fileName trực tiếp trên phần tử đó.

**Giới hạn đồng thời:** bản sao phòng vệ của mảng chỉ ngăn sửa gián tiếp qua cùng tham chiếu mảng. Nó không tự biến việc nhiều thread cùng lúc gọi `setStackTrace()`, `fillInStackTrace()` và ghi log cùng một `Throwable` thành một quy trình chẩn đoán nhất quán theo thời gian. Tránh thay trace khi ngoại lệ đang được chia sẻ và ghi nhận ở các thread khác.

## 6. Trường hợp đầu vào không hợp lệ: mảng `null` và phần tử `null`

Hai lỗi sau làm `setStackTrace()` ném `NullPointerException`:

```java
Throwable ex = new Exception("example");

ex.setStackTrace(null); // Không hợp lệ: mảng null.

ex.setStackTrace(new StackTraceElement[] {
        null
}); // Không hợp lệ: phần tử null.
```

Đây là **đoạn cố ý gây lỗi khi chạy**; lệnh thứ hai sẽ không được thực thi nếu ngoại lệ từ lệnh thứ nhất không được bắt trước. Vì mục đích là minh họa xác thực tham số, đừng dùng nó làm mẫu xử lý lỗi sản xuất.

Mảng rỗng lại **hợp lệ**:

```java
Throwable ex = new Exception("example");
ex.setStackTrace(new StackTraceElement[0]);
System.out.println(ex.getStackTrace().length); // 0
```

Mảng rỗng loại bỏ thông tin frame được công bố của ngoại lệ nếu stack trace của đối tượng khả ghi. Nó **không biến đối tượng ngoại lệ thành không tồn tại**, không đổi loại, message, cause hay danh sách suppressed.

Một điều đặc biệt: với đối tượng có stack trace **không khả ghi**, `setStackTrace()` **vẫn kiểm tra tham số**; mảng chứa `null` vẫn không hợp lệ dù không có thay đổi nào được lưu.

## 7. `writableStackTrace=false`: khi `setStackTrace()` không có tác dụng

Lớp `Throwable` có constructor bảo vệ (*protected*):

```java
protected Throwable(
        String message,
        Throwable cause,
        boolean enableSuppression,
        boolean writableStackTrace)
```

Các lớp ngoại lệ con có thể gọi constructor này để cấu hình việc hỗ trợ suppressed exceptions và khả năng cập nhật stack trace. Khi `writableStackTrace=false`:

- Constructor không tạo stack trace mặc định bằng `fillInStackTrace()`;
- `getStackTrace()` trả mảng rỗng;
- `setStackTrace(...)` không cập nhật trace, dù vẫn xác thực tham số;
- `fillInStackTrace()` cũng không cập nhật trace.

Đây là thiết kế đặc biệt, ví dụ khi một hệ thống cần dùng lại một đối tượng lỗi bất biến theo một chính sách rất cụ thể. Không nên mặc định tắt trace để “tối ưu” mọi lỗi nghiệp vụ, vì điều đó làm khó quan sát nguyên nhân sự cố.

### Chương trình Java 17+: một ngoại lệ có trace không khả ghi

Lưu thành `NonWritableTraceDemo.java`:

```java
public class NonWritableTraceDemo {
    static final class NoTraceException extends Exception {
        NoTraceException(String message) {
            super(message, null, true, false);
        }
    }

    public static void main(String[] args) {
        NoTraceException failure = new NoTraceException("short error");

        System.out.println("initial=" + failure.getStackTrace().length);

        StackTraceElement[] synthetic = {
            new StackTraceElement("remote.Worker",
                                  "run", "Worker.java", 42)
        };
        failure.setStackTrace(synthetic);
        System.out.println("after set=" + failure.getStackTrace().length);

        failure.fillInStackTrace();
        System.out.println("after fill=" + failure.getStackTrace().length);

        try {
            failure.setStackTrace(new StackTraceElement[]{null});
        } catch (NullPointerException ex) {
            System.out.println("invalid input rejected");
        }

        System.out.println("message=" + failure.getMessage());
    }
}
```

```bash
javac -encoding UTF-8 NonWritableTraceDemo.java
java NonWritableTraceDemo
```

Kết quả:

```text
initial=0
after set=0
after fill=0
invalid input rejected
message=short error
```

Phân tích:

1. `NoTraceException` gọi `Exception` constructor tương ứng của `Throwable` với `enableSuppression=true` và `writableStackTrace=false`.
2. Đọc trace ban đầu được mảng rỗng.
3. Truyền mảng hợp lệ vào `setStackTrace` không thêm frame.
4. Gọi `fillInStackTrace` cũng không thêm frame.
5. Mảng chứa phần tử `null` vẫn làm `setStackTrace` ném `NullPointerException`.
6. Message vẫn tồn tại bình thường và có thể được lấy bằng `getMessage()`.

Không nên nhầm `writableStackTrace=false` với việc đặt `setStackTrace(new StackTraceElement[0])`: thao tác sau là **thay nội dung trace hiện tại** của một ngoại lệ khả ghi; một lần gọi `fillInStackTrace()` tương lai có thể ghi lại trace mới.

## 8. `fillInStackTrace()` khác `setStackTrace()` như thế nào?

Hai phương thức có mục tiêu khác nhau:

| Phương thức | Hành động chính | Loại dữ liệu |
| --- | --- | --- |
| `getStackTrace()` | Lấy dữ liệu frame mà `Throwable` đang công bố | Mảng `StackTraceElement[]` |
| `setStackTrace(frames)` | Thay thế bằng mảng frame do ứng dụng cung cấp | **Có thể hoàn toàn tổng hợp** |
| `fillInStackTrace()` | Ghi nhận lại thông tin stack của **thread hiện tại** tại thời điểm gọi | Dữ liệu chẩn đoán do JVM thu thập |
| `printStackTrace()` | In mô tả lỗi, trace, và khi có thì cause/suppressed | Văn bản chẩn đoán ra stream |

Theo Javadoc, khi tạo đối tượng `Throwable` bằng constructor thông thường, `fillInStackTrace()` được dùng để khởi tạo thông tin trace. Việc **ném lại cùng một đối tượng** bằng `throw ex;` thường không tự động ghi mới trace. Nếu tự gọi `fillInStackTrace()`, bạn đang yêu cầu ghi lại một thời điểm khác và có nguy cơ **làm mất vị trí chẩn đoán trước đó**.

### Chương trình Java 17+: ghi lại trace sau khi đã thay

Lưu thành `RefreshTraceDemo.java`:

```java
public class RefreshTraceDemo {
    static void refresh(Throwable failure) {
        failure.fillInStackTrace();
    }

    public static void main(String[] args) {
        IllegalStateException failure =
                new IllegalStateException("example");

        StackTraceElement remote = new StackTraceElement(
                "remote.Worker", "run", "Worker.java", 77);

        failure.setStackTrace(new StackTraceElement[]{remote});
        System.out.println("before="
                + failure.getStackTrace()[0].getClassName());

        refresh(failure);

        StackTraceElement[] captured = failure.getStackTrace();
        boolean noLongerSynthetic =
                captured.length == 0 || !captured[0].equals(remote);
        System.out.println("replaced=" + noLongerSynthetic);
        System.out.println("message=" + failure.getMessage());
    }
}
```

```bash
javac -encoding UTF-8 RefreshTraceDemo.java
java RefreshTraceDemo
```

Kết quả trên Java 17+:

```text
before=remote.Worker
replaced=true
message=example
```

Sau `refresh(failure)`, `fillInStackTrace()` đã làm mới trace từ thread hiện tại, nên frame tổng hợp trước đó không còn là trace hiện hành. Mã **không in ra tên frame JVM cụ thể**, vì Javadoc cho phép các VM khác nhau ghi nhận/loại bỏ một số frame. Phép kiểm tra cho phép mảng rỗng hoặc frame đầu khác frame giả đã đặt, nên giữ ý nghĩa chẩn đoán mà không phụ thuộc số dòng.

**Đừng dùng `fillInStackTrace()` chỉ để làm log “đẹp hơn”**: hành động này có thể đẩy nơi trace bắt đầu sang một tầng xử lý lỗi trung gian thay vì nơi lỗi gốc được tạo. Nếu cần thêm ngữ cảnh, thường nên bọc ngoại lệ mới với `cause` hơn là ghi đè trace cũ.

## 9. Việc sửa trace không sửa `cause` hoặc `suppressed exceptions`

Một ngoại lệ có thể được bọc bởi ngoại lệ tầng cao hơn:

```text
ServiceException("cannot process")
    ├── cause -> IOException("read failed")
    │              └── original stack trace
    └── suppressed -> CleanupException("close failed")
                       └── own stack trace
```

Mỗi đối tượng `Throwable` trong sơ đồ có **trace riêng**. Gọi `setStackTrace()` trên `ServiceException` không tự thay trace của `IOException` làm cause hay của `CleanupException` bị suppress. Gọi trên đối tượng cause cũng không tự thay thuộc tính trace của wrapper.

Lưu thành `TraceGraphDemo.java`:

```java
public class TraceGraphDemo {
    public static void main(String[] args) {
        IllegalStateException cause =
                new IllegalStateException("original");

        Exception wrapper = new Exception("service error", cause);
        wrapper.addSuppressed(
                new IllegalArgumentException("cleanup error"));

        wrapper.setStackTrace(new StackTraceElement[0]);

        System.out.println("wrapperFrames="
                + wrapper.getStackTrace().length);
        System.out.println("message=" + wrapper.getMessage());
        System.out.println("causeMessage="
                + wrapper.getCause().getMessage());
        System.out.println("suppressedCount="
                + wrapper.getSuppressed().length);
        System.out.println("suppressedMessage="
                + wrapper.getSuppressed()[0].getMessage());
    }
}
```

```bash
javac -encoding UTF-8 TraceGraphDemo.java
java TraceGraphDemo
```

Kết quả:

```text
wrapperFrames=0
message=service error
causeMessage=original
suppressedCount=1
suppressedMessage=cleanup error
```

Mặc dù trace của `wrapper` đã được đặt rỗng, thông điệp lỗi, cause và suppressed vẫn được giữ. Việc in `wrapper.printStackTrace()` còn có thể hiển thị những thông tin được giữ trên cause và suppressed vì hai đối tượng ấy quản lý trace riêng.

**Lưu ý:** nội dung được hiển thị bởi `printStackTrace()` không đồng nghĩa chỉ gồm mảng của ngoại lệ ngoài cùng. Đầu ra còn phản ánh cấu trúc liên kết nguyên nhân và lỗi bị suppress. Cắt mảng trên wrapper không làm mất toàn bộ bằng chứng ở các đối tượng khác, nhưng vẫn có thể gây nhầm lẫn về ngữ cảnh nơi wrapper được tạo.

## 10. `printStackTrace()`, logging và sự sai lệch của trace tự đặt

Khi gọi:

```java
failure.printStackTrace();
```

phiên bản không tham số ghi chẩn đoán vào `System.err`, bao gồm thông tin về đối tượng throwable và các frame có thể hiển thị. Còn các overload `printStackTrace(PrintStream)` và `printStackTrace(PrintWriter)` cho phép chọn đích ghi. Hệ thống logging thông thường cũng có thể sử dụng thông tin từ chính `Throwable` để biểu diễn nguyên nhân và stack trace.

**Một frame tổng hợp có thể được in như một frame thật.** Vì vậy:

- Lỗi đọc log sẽ khó hơn nếu một tầng xóa các frame mà không ghi rõ việc đã lọc.
- Nhóm vận hành có thể truy nhầm tệp, nhầm số dòng hoặc nhầm dịch vụ nếu không biết trace đã được tái dựng.
- Công cụ phân nhóm lỗi dựa vào “top frame” có thể phân nhóm sai sau khi trace bị sửa.
- Dấu vết bị viết lại có thể làm mất thông tin xác định tầng đã phát sinh hoặc đã chuyển đổi ngoại lệ.
- Nếu nội dung ngoại lệ đến từ nguồn ngoài đáng tin cậy thấp, không được coi tên lớp/method/file do bên ngoài khai báo là một sự thật đã được JVM xác thực.

Do đó, **đừng tự ý dùng `setStackTrace()` để che giấu nơi lỗi thực tế xảy ra**. Những nhu cầu hợp lệ như giảm thông tin nhạy cảm, giới hạn khối lượng log hoặc tái dựng dấu vết phân tán nên thực hiện theo thiết kế chẩn đoán rõ ràng: có thông tin gốc, đánh dấu frame từ xa/tổng hợp, kiểm soát quyền truy cập log và giữ ngữ cảnh liên quan.

Trong kiểm thử, tránh so sánh nguyên văn toàn bộ `printStackTrace()` vì tên file, số dòng, số frame, mô tả module hoặc cách trình bày có thể thay đổi theo JDK/JVM. Với những khẳng định cốt lõi, so sánh các trường dữ liệu cần thiết hoặc các frame tổng hợp mà bài test tự tạo.

## 11. Khi nào thay trace có thể hợp lý?

Javadoc nhắc tới **framework RPC** và các hệ thống nâng cao. Chẳng hạn một dịch vụ nhận một lỗi đã được tuần tự hóa từ máy từ xa. Dấu vết gốc có thể biểu diễn điểm xử lý của remote service, nhưng máy nhận không có các frame đó trong stack hiện tại.

Một thiết kế có thể:

1. Nhận dữ liệu lỗi có cấu trúc từ phía remote.
2. Xác định dữ liệu đó là **dấu vết từ xa**, không phải những lời gọi JVM ở máy nhận.
3. Lưu hoặc trình bày riêng remote stack; nếu thật sự cần tương thích một API dựa trên `Throwable`, có thể dựng các `StackTraceElement` có nguồn gốc được ghi chú rõ.
4. Giữ cả thông tin lỗi gốc lẫn điểm tiếp nhận ở máy cục bộ qua metadata, sự kiện tracing hoặc cause chain.
5. Hạn chế giả mạo/làm nhiễm log: kiểm tra dữ liệu nhận, giới hạn độ dài, làm sạch chuỗi có ký tự điều khiển nếu dữ liệu sẽ vào log, không coi remote frames là chứng cứ tin cậy tự thân.

**Nên ưu tiên các trường chẩn đoán có cấu trúc** (service, trace ID, span ID, remote error type, timestamps, environment) khi cần mô tả cả hành trình phân tán. `StackTraceElement[]` không tự cung cấp đầy đủ thông tin về các ranh giới mạng, thời gian hoặc mối liên hệ giữa các trace.

Nếu không có yêu cầu tương thích buộc phải thay trace, phương án đơn giản hơn thường là **giữ nguyên `Throwable` và gắn ngữ cảnh trong exception wrapper**:

```java
try {
    callRemote();
} catch (RemoteCallException ex) {
    throw new ServiceException(
            "Remote operation failed; service=inventory", ex);
}
```

Đây là **mẫu minh họa**, giả định `RemoteCallException`, `ServiceException` và `callRemote()` đã được khai báo phù hợp. Phần `cause` giúp giữ đường đi chẩn đoán của ngoại lệ gốc; message thêm ngữ cảnh ở tầng dịch vụ. Không cần ghi đè trace của đối tượng gốc.

## 12. Khác biệt giữa “trace tổng hợp” và “trace bị cắt ngắn”

Có ba thao tác dễ bị đánh đồng:

| Thao tác | Hậu quả |
| --- | --- |
| `setStackTrace(new StackTraceElement[0])` | Trace đang công bố của đối tượng trở thành mảng rỗng (nếu khả ghi) |
| `setStackTrace(Arrays.copyOf(frames, n))` | Giữ một số frame, bỏ các frame còn lại khỏi thông tin công bố |
| `setStackTrace(syntheticFrames)` | Thay bằng dữ liệu có thể do bên gọi tự tạo, không phản ánh lịch sử gọi thật |

Chọn số frame hữu hạn để giảm độ dài log có thể là yêu cầu hợp lệ, nhưng sửa trực tiếp trace của ngoại lệ **thay đổi chính đối tượng** đang được những thành phần khác dùng. Nếu một hệ thống chỉ muốn hiển thị tối đa vài frame, thường an toàn hơn khi **giới hạn cách định dạng log** mà không đụng vào trạng thái `Throwable` ban đầu.

Đặc biệt với chương trình đa luồng, cùng một ngoại lệ có thể được truyền qua các tầng, lưu trong `Future` hoặc được nhiều thành phần tham khảo. Việc thay trace làm thay đổi dữ liệu chẩn đoán mà những người sử dụng sau đó nhìn thấy; không nên coi đây là phép toán thuần không có tác dụng phụ.

## 13. Các lỗi hiểu sai thường gặp

| Hiểu sai | Thực tế |
| --- | --- |
| “`setStackTrace()` thay đổi stack thực của thread.” | Không. Nó chỉ thay thông tin trace của một đối tượng `Throwable`. |
| “Ghi frame giả chứng minh phương thức đó đã chạy.” | Không. Bên gọi tự tạo được `StackTraceElement` với tên tùy chọn. |
| “Thay `getStackTrace()[0]` là sửa trực tiếp ngoại lệ.” | Không. `getStackTrace()` trả mảng không chia sẻ trạng thái nội bộ theo cách đó. |
| “Chỉnh mảng đầu vào sau `setStackTrace()` làm trace cũng đổi.” | Không. `setStackTrace()` sao chép mảng đã nhận. |
| “Mảng `null` hoặc phần tử `null` nghĩa là frame không rõ.” | Không. Đây là đầu vào không hợp lệ và gây `NullPointerException`. Dùng mảng rỗng nếu muốn biểu diễn không có frame. |
| “Nếu `writableStackTrace=false`, `setStackTrace()` sẽ ghi lại frame.” | Không. Nó chỉ xác thực tham số, không thay trace. |
| “`setStackTrace()` đồng thời thay đổi `getMessage()`, `cause` và suppressed.” | Không. Những dữ liệu đó có cơ chế riêng. |
| “Ném lại cùng một đối tượng tự động ghi stack mới tại `throw ex;`.” | Không nên giả định như vậy; trace thường gắn với lần tạo hoặc lần gọi `fillInStackTrace()`. |
| “`fillInStackTrace()` tương đương `setStackTrace()`.” | Không. `fillInStackTrace()` yêu cầu JVM ghi nhận stack hiện tại; `setStackTrace()` nhận mảng do ứng dụng cung cấp. |
| “Mọi JVM đều trả đầy đủ mọi frame, luôn có frame đầu.” | Không. Javadoc cho phép lược bỏ frame, thậm chí trả mảng rỗng trong một số trường hợp. |
| “Gọi `setStackTrace(new StackTraceElement[0])` là cách tốt để bảo mật.” | Không đủ. Message, cause, suppressed và log ở nơi khác vẫn có thể chứa thông tin nhạy cảm; mất trace còn làm giảm khả năng điều tra. |
| “Trace in ra từ một ngoại lệ là bằng chứng không thể bị thay đổi.” | Không. API Java cho phép can thiệp; nguồn log phải được đánh giá theo mức độ tin cậy và chuỗi thu thập dữ liệu. |

## 14. Checklist khi review mã dùng `setStackTrace()`

Khi thấy một đoạn mã gọi `setStackTrace()`, hãy kiểm tra theo thứ tự:

1. **Mục đích:** có thật sự cần sửa đối tượng ngoại lệ, hay chỉ muốn đổi cách hiển thị log?
2. **Nguồn frame:** các frame đến từ JVM, từ hệ thống từ xa, hay do chương trình tạo giả? Có gắn nhãn provenance không?
3. **Bằng chứng gốc:** trace cũ có được giữ nguyên ở nơi thích hợp để điều tra không?
4. **Cause và suppressed:** liệu việc dựng trace mới có khiến người đọc nhầm rằng nguyên nhân và các lỗi phụ đã được cập nhật tương ứng?
5. **Khả ghi:** đối tượng được tạo với `writableStackTrace=false` hay không? API lúc đó không ghi giá trị mới.
6. **Đầu vào hợp lệ:** mảng và mọi phần tử có khác `null` không? Dữ liệu frame có độ dài hợp lý không?
7. **Đa luồng:** một nơi khác có thể đồng thời đọc/in cùng đối tượng ngoại lệ không? Có đang thay dấu vết mà bên khác dùng?
8. **Khả năng quan sát:** công cụ monitoring có phân nhóm theo top frame hay so sánh stack trace để gom lỗi không?
9. **Thông tin nhạy cảm:** message, cause, suppressed, tệp nguồn, tham số log hoặc metadata remote có lộ dữ liệu không?
10. **Giải pháp thay thế:** có thể dùng cause chain, structured logging, tracing phân tán, định dạng có giới hạn frame hoặc message có ngữ cảnh thay cho sửa trace?

Nếu mục tiêu chỉ là thêm ngữ cảnh, ưu tiên:

```java
throw new ServiceException("Cannot complete request", original);
```

Đây là mẫu minh họa yêu cầu `ServiceException` có constructor nhận `Throwable cause`. Ngoại lệ mới có trace riêng tại tầng bọc, còn ngoại lệ `original` giữ trace gốc và có thể truy qua `getCause()`. Điều này thường minh bạch hơn việc dùng `setStackTrace()` trên đối tượng ban đầu.

## 15. Tự kiểm tra kiến thức

**Câu 1.** `setStackTrace()` có thay đổi nơi ngoại lệ được ném lúc runtime không?  
**Không.** Nó thay dữ liệu frame được cung cấp để đọc/in, không thay lịch sử thực thi hoặc cơ chế ném.

**Câu 2.** Nếu gọi `setStackTrace(input)` rồi thay `input[0]`, trace lưu trong ngoại lệ có đổi theo không?  
**Không.** `setStackTrace()` đã sao chép mảng đầu vào.

**Câu 3.** Nếu lấy `frames = ex.getStackTrace()` rồi gán `frames[0]`, có sửa trace nội bộ không?  
**Không.** Phải gọi `ex.setStackTrace(frames)` nếu muốn thực sự cập nhật một ngoại lệ có trace khả ghi.

**Câu 4.** `ex.setStackTrace(new StackTraceElement[]{null})` có được hiểu là “một frame không rõ” không?  
**Không.** Phần tử `null` không hợp lệ và gây `NullPointerException`.

**Câu 5.** Có thể đặt `setStackTrace(new StackTraceElement[0])` không?  
**Có**, mảng rỗng hợp lệ và tạo trace công bố rỗng nếu stack trace khả ghi.

**Câu 6.** Khi `writableStackTrace=false`, gọi `setStackTrace(validFrames)` có làm xuất hiện frame không?  
**Không.** Nó không thay trace của đối tượng; `getStackTrace()` tiếp tục trả mảng rỗng.

**Câu 7.** `fillInStackTrace()` khác `setStackTrace()` ở đâu?  
**`fillInStackTrace()`** yêu cầu ghi nhận lại stack của thread đang gọi; **`setStackTrace()`** sử dụng mảng frame được cung cấp.

**Câu 8.** `setStackTrace()` trên wrapper có làm đổi trace của đối tượng `getCause()` không?  
**Không.** Mỗi đối tượng `Throwable` quản lý trace riêng.

**Câu 9.** Một hệ thống RPC có thể cần trace tổng hợp không?  
**Có thể.** Nhưng nên đánh dấu rõ đó là dấu vết từ xa/tổng hợp và không coi nó là frame thật ở thread cục bộ.

**Câu 10.** Khi chỉ cần giảm số dòng stack trace trong log, có nên sửa trace của `Throwable` dùng chung không?  
**Thông thường không.** Nên giới hạn cách trình bày log, giữ nguyên thông tin gốc để điều tra.

## 16. Tài liệu tham khảo

- [Java SE 21 Javadoc — `Throwable.setStackTrace()`, `getStackTrace()` và `fillInStackTrace()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Throwable.html).
- [Java SE 21 Javadoc — `StackTraceElement`, constructor và các trường frame](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/StackTraceElement.html).
- [JLS Java SE 21 — §11.3, xử lý ngoại lệ ở runtime](https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html#jls-11.3).

**Ghi nhớ:** `setStackTrace()` chỉ thay **danh sách frame chẩn đoán được công bố** trên một `Throwable` có trace khả ghi. Nó không thay đổi luồng thực thi, loại ngoại lệ, thông điệp, cause hay suppressed exceptions. Chỉ sửa trace khi có nhu cầu hệ thống rõ ràng và luôn phân biệt dấu vết gốc với dấu vết do ứng dụng hoặc hệ thống từ xa cung cấp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="throwable-internals-008">getCause, initCause và điều kiện khởi tạo nguyên nhân</a>

<details>
<summary>Xem chi tiết</summary>

# `getCause()`, `initCause()` và điều kiện khởi tạo nguyên nhân của `Throwable` trong Java

Trong Java, một ngoại lệ có thể được tạo **do một ngoại lệ khác gây ra**. Đối tượng gốc được gọi là *cause* (nguyên nhân); đối tượng ngoại lệ bao bên ngoài thường bổ sung ngữ cảnh ở một tầng xử lý cao hơn. `Throwable.getCause()` cho phép truy ngược nguyên nhân, còn `Throwable.initCause(Throwable)` hỗ trợ **gắn nguyên nhân sau khi tạo đối tượng** trong những điều kiện rất cụ thể.

Điểm quan trọng nhất của bài:

> **`getCause() == null` không có nghĩa là chắc chắn được gọi `initCause(...)`.** Java phân biệt trạng thái cause **chưa được khởi tạo** với trạng thái cause **đã được khởi tạo bằng `null`**.

Cùng thấy `getCause()` trả `null`, nhưng hai đối tượng tạo bởi `new Throwable("message")` và `new Throwable("message", null)` lại có quyền gọi `initCause(...)` khác nhau.

Bài này giải thích sự khác biệt đó, quan hệ với các constructor, cách tạo chuỗi nguyên nhân có ý nghĩa, những ngoại lệ do chính `initCause` ném ra, và các tình huống nên dùng constructor thay vì thiết lập cause muộn. Các ví dụ tương thích Java 17+.

## 1. Cause trong ngoại lệ Java là gì?

Ví dụ ứng dụng đang đọc cấu hình từ tệp. Lỗi tầng thấp là `IOException`, nhưng tầng khởi tạo dịch vụ muốn báo rằng **không thể khởi tạo dịch vụ**:

~~~java
try {
    readConfiguration();
} catch (java.io.IOException original) {
    throw new IllegalStateException(
            "Không thể khởi tạo dịch vụ", original);
}
~~~

Đây là **đoạn minh họa** giả định phương thức `readConfiguration()` có thể ném `IOException`.

Trong ví dụ:

- `original` là ngoại lệ I/O ban đầu.
- `new IllegalStateException("...", original)` tạo một đối tượng ngoại lệ **mới** với thông điệp của tầng dịch vụ.
- Constructor nhận cause giữ **tham chiếu đến chính ngoại lệ `original`**.
- Lệnh `throw` ném đối tượng `IllegalStateException` mới.
- Caller nhận lỗi tầng dịch vụ có thể gọi `getCause()` để truy tìm lỗi I/O.

Mô hình:

~~~text
IllegalStateException: Không thể khởi tạo dịch vụ
    |
    +-- getCause() --> IOException: Không đọc được cấu hình
                           |
                           +-- getCause() --> null
~~~

**Cause là một mối liên kết giữa những đối tượng `Throwable`, không phải bản sao chuỗi message.** Nếu một ngoại lệ có cause, Java có thể hiển thị quan hệ `Caused by:` khi in stack trace, hỗ trợ chẩn đoán các lớp nguyên nhân.

Chuỗi nguyên nhân có thể có nhiều tầng:

~~~text
ServiceInitializationException
    -> ConfigurationException
        -> IOException
            -> FileNotFoundException
~~~

Đây là **sơ đồ khái niệm**; kiểu cụ thể trong ứng dụng có thể khác. Việc bọc quá nhiều lớp không hữu ích nếu mỗi lớp không bổ sung ngữ nghĩa hoặc hợp đồng đáng kể.

## 2. `getCause()` đọc gì, trả gì?

Chữ ký API cơ bản:

~~~java
public synchronized Throwable getCause()
~~~

Đây là **chữ ký minh họa API**, không phải một khai báo cần chép vào chương trình. `getCause()` trả về tham chiếu đến đối tượng `Throwable` được lưu làm nguyên nhân trực tiếp; nếu không có nguyên nhân được biểu lộ thì trả `null`.

~~~java
java.io.IOException root =
        new java.io.IOException("Đọc thất bại");

IllegalStateException outer =
        new IllegalStateException("Khởi tạo thất bại", root);

System.out.println(outer.getCause() == root); // true
System.out.println(outer.getCause().getMessage()); // Đọc thất bại
~~~

Trong đoạn trên, toán tử `==` cho thấy cause **đúng là cùng đối tượng** `root`, không phải ngoại lệ được tạo lại.

### Cause trực tiếp khác cause sâu nhất

`getCause()` chỉ trả **một bước nguyên nhân trực tiếp**. Nếu A bọc B và B bọc C:

~~~text
A.getCause() == B
B.getCause() == C
C.getCause() == null
~~~

Muốn tìm lỗi gốc, ứng dụng có thể lần theo chuỗi. Tuy nhiên không nên viết một vòng lặp mù quáng cho hệ thống nhận `Throwable` từ các nguồn không tin cậy: do code tùy biến hoặc cấu trúc bất thường, chuỗi cause **có thể tạo vòng**, nên thuật toán phân tích cần tránh lặp vô hạn. Phần sau có ví dụ xử lý an toàn.

### Giá trị `null` là trạng thái công khai, không phải “cờ chưa khởi tạo”

Người gọi `getCause()` chỉ quan sát được “không có cause được trả về”. Giá trị đó **không cho biết** constructor đã thiết lập cause hay chưa. Trạng thái nội bộ của `Throwable` mới quyết định `initCause(...)` có hợp lệ.

## 3. Tất cả các constructor liên quan tới khả năng gọi `initCause`

`Throwable` cung cấp nhiều constructor. Hai nhóm cần phân biệt:

| Cách tạo `Throwable` | `getCause()` ban đầu | Trạng thái cause cho `initCause` |
| --- | --- | --- |
| `new Throwable()` | `null` | **Chưa khởi tạo**, có thể gắn một lần |
| `new Throwable("message")` | `null` | **Chưa khởi tạo**, có thể gắn một lần |
| `new Throwable((Throwable) null)` | `null` | **Đã khởi tạo bằng null**, không gắn lại |
| `new Throwable(root)` | `root` | **Đã khởi tạo**, không gắn lại |
| `new Throwable("message", null)` | `null` | **Đã khởi tạo bằng null**, không gắn lại |
| `new Throwable("message", root)` | `root` | **Đã khởi tạo**, không gắn lại |
| Constructor `protected` bốn tham số có cause | Tương ứng giá trị cause truyền vào | **Đã khởi tạo**, kể cả khi cause là null |

Chú ý các overload `Throwable(Throwable)` và `Throwable(String, Throwable)`: chúng **khởi tạo trạng thái cause ngay tại constructor**. Điều này vẫn đúng nếu đối số `cause` bằng `null`.

Ví dụ đối chiếu:

~~~java
Throwable a = new Throwable("A");
Throwable b = new Throwable("B", null);

System.out.println(a.getCause() == null); // true
System.out.println(b.getCause() == null); // true

a.initCause(new java.io.IOException("root")); // Hợp lệ.
b.initCause(new java.io.IOException("root")); // Ném IllegalStateException.
~~~

Đoạn này **cố ý chứa lệnh gây lỗi runtime ở cuối** để chỉ ra sự khác biệt. Không chạy nó không có `try/catch` nếu muốn chương trình tiếp tục.

Tương tự, constructor nhận duy nhất `Throwable cause` cũng đánh dấu cause là đã khởi tạo ngay cả khi nhận null:

~~~java
Throwable noCause = new Throwable((Throwable) null);
System.out.println(noCause.getCause()); // null
noCause.initCause(new Exception("later")); // IllegalStateException
~~~

Việc ép kiểu `(Throwable) null` khiến người đọc biết chính xác overload nào được gọi.

## 4. Vì sao tồn tại trạng thái “chưa khởi tạo” dù getter trả null?

Trong cách triển khai chuẩn của `Throwable`, trạng thái cause được biểu diễn bằng một giá trị đánh dấu riêng trước khi người dùng thiết lập cause, cho phép `getCause()` trả `null` ở trạng thái đó và phân biệt nó với “đã gán null”. Không nên viết code phụ thuộc vào trường nội bộ hay cố sửa nó bằng reflection.

Có thể hình dung bằng sơ đồ trạng thái **khái niệm**:

~~~text
Tạo với Throwable() hoặc Throwable(String)
                 |
                 v
       [CAUSE CHƯA KHỞI TẠO]
             |        |
             |        +--> getCause() trả null
             |
             +--> initCause(root) thành công
             |       |
             |       v
             |  [CAUSE ĐÃ KHỞI TẠO: root]
             |
             +--> initCause(null) thành công
                     |
                     v
               [CAUSE ĐÃ KHỞI TẠO: null]

Tạo với Throwable(cause) hoặc Throwable(message, cause)
                 |
                 v
       [CAUSE ĐÃ KHỞI TẠO: cause]
                 |
                 +--> mọi initCause(...) sau đó bị từ chối
~~~

Ngay cả khi người dùng gọi `initCause(null)`, trạng thái đã chuyển thành **đã khởi tạo**. Không có cơ chế công khai để hoàn nguyên về chưa khởi tạo.

### Không suy luận dựa trên `getCause() == null`

Mẫu kiểm tra sau là **sai về ý nghĩa**:

~~~java
if (problem.getCause() == null) {
    problem.initCause(root);
}
~~~

`problem` và `root` ở đây là các biến giả định có sẵn. Nếu `problem` được tạo bằng constructor `(String, Throwable)` với cause `null`, điều kiện `getCause() == null` là đúng nhưng `initCause(root)` vẫn ném `IllegalStateException`.

Thực hành tốt hơn: lựa chọn một trong hai con đường ngay tại nơi tạo đối tượng:

- Truyền cause **trong constructor** khi lớp hỗ trợ overload đó.
- Nếu chỉ có constructor không nhận cause và API cho phép, gọi `initCause` **một lần ngay sau khi tạo**.

Đừng thiết kế luồng “đọc getter rồi đoán xem có được initCause không”.

## 5. `initCause(Throwable cause)` hoạt động thế nào?

Chữ ký API:

~~~java
public synchronized Throwable initCause(Throwable cause)
~~~

Phương thức này thiết lập nguyên nhân cho đối tượng `Throwable` hiện tại **khi trạng thái cause chưa được khởi tạo**.

~~~java
Throwable outer = new Throwable("Không thể tiếp tục");
java.io.IOException root =
        new java.io.IOException("Kết nối hỏng");

Throwable returned = outer.initCause(root);

System.out.println(outer.getCause() == root); // true
System.out.println(returned == outer);        // true
~~~

Hai phép so sánh đều là `true`:

1. `outer.getCause()` trỏ tới đối tượng `root`.
2. `initCause(...)` trả về **chính đối tượng `outer`**, không tạo một đối tượng `Throwable` khác.

Dữ liệu message và stack trace đã có của `outer` không bị tự động thay bằng dữ liệu của `root`. `initCause` chỉ nhằm thiết lập quan hệ nguyên nhân.

### Điều kiện thành công

Một lời gọi `initCause(cause)` hợp lệ khi đáp ứng các yêu cầu của hành vi mặc định `Throwable`:

- Cause của đối tượng nhận **chưa được khởi tạo trước đó**.
- `cause` **không phải chính đối tượng nhận** (`cause != this`).
- Không có ràng buộc khác do lớp con triển khai theo hợp đồng của nó.

Sau thành công, khả năng thiết lập cause theo phương thức này đã được dùng hết. Thêm cause thứ hai không phải một thao tác hợp lệ.

### Khi nào nên dùng?

Mục đích ban đầu của `initCause` là hỗ trợ những lớp ngoại lệ không cung cấp constructor nhận cause, đặc biệt khi cần tương thích với những API có từ trước.

Ví dụ lớp lỗi tùy biến cũ:

~~~java
public class LegacyProblem extends Exception {
    public LegacyProblem(String message) {
        super(message);
    }
}
~~~

Giả sử API lớp này chỉ cung cấp constructor message:

~~~java
LegacyProblem outer =
        new LegacyProblem("Lỗi nghiệp vụ");
outer.initCause(new java.io.IOException("Hỏng kết nối"));
throw outer;
~~~

Đây là đoạn minh họa trong một phương thức có khai báo/bắt checked `LegacyProblem` thích hợp. Vì constructor `LegacyProblem(String)` gọi `super(message)` và không thiết lập cause, `initCause` có thể gắn nguyên nhân một lần.

Với mã mới do bạn kiểm soát, nên cân nhắc bổ sung constructor `LegacyProblem(String, Throwable)` vào lớp hơn là bắt mọi caller dùng `initCause` hai bước.

## 6. `IllegalStateException`: cause đã được khởi tạo

Lỗi phổ biến nhất:

~~~java
Throwable problem = new Throwable(
        "Không đọc được dữ liệu", null);

problem.initCause(new java.io.IOException("I/O"));
// Runtime: IllegalStateException
~~~

Tuy `problem.getCause()` ban đầu là `null`, constructor `Throwable(String, Throwable)` đã khởi tạo trạng thái cause. Vì vậy `initCause` không còn hợp lệ.

Một tình huống khác:

~~~java
Throwable error = new Throwable("Lỗi");
error.initCause(new java.io.IOException("Lỗi lần đầu"));
error.initCause(new java.io.IOException("Lỗi lần hai"));
// Runtime: IllegalStateException
~~~

`initCause` thành công ở lần đầu, nhưng **không thay thế được** nguyên nhân trong lần sau.

### `initCause(null)` cũng chiếm quyền khởi tạo

~~~java
Throwable problem = new Throwable("Lỗi");
problem.initCause(null);
System.out.println(problem.getCause()); // null

problem.initCause(new Exception("Lý do mới"));
// Runtime: IllegalStateException
~~~

Nếu gọi `initCause(null)`, bạn đã đưa ra quyết định “cause là null”. Không thể đảo lại bằng cách gọi `initCause` thêm lần nữa.

Vì vậy, tránh coi `initCause(null)` là câu lệnh “reset”. Nó **không reset trạng thái** về chưa khởi tạo.

## 7. `IllegalArgumentException`: không được tự làm cause của chính mình

Tình huống tự tham chiếu trực tiếp:

~~~java
Throwable problem = new Throwable("Lỗi");
problem.initCause(problem);
// Runtime: IllegalArgumentException
~~~

Java không cho một `Throwable` thiết lập **chính nó** thành nguyên nhân trực tiếp của nó. Tự tham chiếu trực tiếp như vậy không tạo thêm ngữ nghĩa, có thể khiến việc duyệt chuỗi cause rơi vào vòng lặp.

**Điểm khác biệt cần nhớ:**

- Cause đã được khởi tạo trước: lỗi `IllegalStateException`.
- Cause là chính đối tượng nhận: lỗi `IllegalArgumentException` trong tình huống chưa khởi tạo phù hợp.
- Cause bằng `null` trong lần thiết lập đầu: hợp lệ nếu trạng thái chưa khởi tạo; nhưng nó vẫn là một lần khởi tạo.

Không nên viết logic nghiệp vụ phụ thuộc vào **thứ tự ưu tiên giữa nhiều điều kiện sai đồng thời**, trừ khi API của phiên bản và lớp cụ thể cam kết rõ. Hãy luôn tránh cả hai điều kiện.

### Chuỗi cause có thể tạo vòng gián tiếp

Việc cấm `problem.initCause(problem)` chỉ ngăn **tự tham chiếu trực tiếp**. Không nên suy ra mọi vòng tham chiếu gián tiếp đều bị mọi API ngăn tự động:

~~~java
Throwable a = new Throwable("A");
Throwable b = new Throwable("B");

a.initCause(b);
b.initCause(a);
~~~

Đây là **đoạn minh họa không nên áp dụng**: hai đối tượng khác nhau có thể tạo chuỗi A → B → A. Một thuật toán tự viết chỉ lặp `current = current.getCause()` cho tới khi gặp `null` sẽ có nguy cơ không dừng. Các công cụ hiển thị stack trace có thể có bảo vệ riêng; mã duyệt tùy biến vẫn phải được thiết kế an toàn.

## 8. Constructor cause-only và ý nghĩa của message

Các constructor thường gặp của `Throwable`:

~~~java
new Throwable()
new Throwable("message")
new Throwable(cause)
new Throwable("message", cause)
~~~

Dạng `Throwable(Throwable cause)` vừa thiết lập cause vừa tạo message từ `cause.toString()` khi cause khác `null`:

~~~java
Throwable root =
        new IllegalArgumentException("Tuổi không hợp lệ");

Throwable outer = new Throwable(root);

System.out.println(outer.getMessage());
// java.lang.IllegalArgumentException: Tuổi không hợp lệ
~~~

Cần phân biệt:

- `root.getMessage()`: `"Tuổi không hợp lệ"`.
- `root.toString()`: thường là `"java.lang.IllegalArgumentException: Tuổi không hợp lệ"`.
- `outer.getMessage()` từ constructor cause-only: bằng chuỗi tạo từ `root.toString()`.

Với constructor nhận cả message và cause, message độc lập:

~~~java
Throwable outer = new Throwable(
        "Không thể hoàn thành đăng ký", root);

System.out.println(outer.getMessage());
// Không thể hoàn thành đăng ký
~~~

Hai dạng constructor đều khởi tạo cause, nên `initCause(...)` **không thể dùng để thay đổi**.

### Constructor nhận null có overload dễ nhầm

Dùng:

~~~java
Throwable messageNull = new Throwable((String) null);
Throwable causeNull = new Throwable((Throwable) null);
~~~

Sự khác biệt:

- `messageNull` được tạo bằng constructor `(String)` nên trạng thái cause chưa khởi tạo.
- `causeNull` được tạo bằng constructor `(Throwable)` nên trạng thái cause đã khởi tạo bằng `null`.

Cả hai đều trả `null` từ `getMessage()` và `getCause()` ban đầu, nhưng quyền gọi `initCause(...)` khác nhau.

Đặc biệt, câu lệnh `new Throwable(null)` **không biên dịch** do Java không biết chọn overload `Throwable(String)` hay `Throwable(Throwable)` vì hai kiểu này không có quan hệ subtype phù hợp để chọn một overload cụ thể nhất. Sử dụng ép kiểu nếu cần truyền `null` chủ động.

## 9. Constructor bốn tham số và nguyên nhân

`Throwable` còn có constructor `protected`:

~~~java
protected Throwable(
        String message,
        Throwable cause,
        boolean enableSuppression,
        boolean writableStackTrace)
~~~

Nó cho phép lớp con điều khiển khả năng lưu suppressed exceptions và ghi stack trace. Nhưng đối với `initCause`, điểm quan trọng là **constructor này vẫn khởi tạo cause ngay lúc tạo đối tượng**, kể cả khi truyền `null`.

Ví dụ lớp con:

~~~java
public final class DomainProblem extends Exception {
    public DomainProblem(
            String message,
            Throwable cause,
            boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
~~~

Đây là một **lớp độc lập** nếu lưu trong `DomainProblem.java`. `Exception` có overload protected tương ứng để lớp con gọi `super(...)`.

Ví dụ dùng:

~~~java
DomainProblem problem =
        new DomainProblem("Lỗi đặc biệt", null, true, false);

System.out.println(problem.getCause()); // null
System.out.println(problem.getStackTrace().length); // 0

problem.initCause(new java.io.IOException("Nguyên nhân"));
// Runtime: IllegalStateException
~~~

**Cờ `writableStackTrace` không quyết định quyền gắn cause.** Trong ví dụ, cause đã được khởi tạo bằng `null`; stack trace tắt là một lựa chọn cấu hình độc lập.

Tương tự, tắt `enableSuppression` chỉ ảnh hưởng khả năng giữ `getSuppressed()`, không có nghĩa mở lại khả năng `initCause`.

## 10. Khi bọc checked và unchecked exceptions

Cause có ích khi một tầng gọi cần **chuyển cách biểu đạt lỗi** mà vẫn giữ nguồn gốc.

~~~java
static int readPort(String text) {
    try {
        return Integer.parseInt(text);
    } catch (NumberFormatException cause) {
        throw new IllegalArgumentException(
                "Cổng không phải số nguyên: " + text, cause);
    }
}
~~~

Đây là một phương thức Java minh họa. `NumberFormatException` cũng là unchecked; nó được bọc để bổ sung ngữ cảnh cổng mạng và giữ liên kết cause.

Với checked exception:

~~~java
static String loadRequired(java.nio.file.Path path) {
    try {
        return java.nio.file.Files.readString(path);
    } catch (java.io.IOException cause) {
        throw new IllegalStateException(
                "Thiếu cấu hình bắt buộc", cause);
    }
}
~~~

Ở đây, `IOException` là checked, nhưng ngoại lệ mới `IllegalStateException` là unchecked. Quy tắc `catch` và `throws` với caller phụ thuộc **loại ngoại lệ thực tế được ném ra ở tầng ngoài**, không phải chỉ loại của cause. `catch (IOException e)` ở caller **không tự động bắt `IllegalStateException`** vì nó có cause là `IOException`.

Có nên đổi checked thành unchecked hay không là một quyết định API/kiến trúc; đừng bọc mọi ngoại lệ theo thói quen. Đặc biệt không nên tạo hàng loạt wrapper vô nghĩa chỉ làm chuỗi cause sâu hơn mà không bổ sung ngữ cảnh.

## 11. `getCause()` không phải `getSuppressed()`

Hai quan hệ trong `Throwable` có mục đích riêng.

**Cause:** lý do nền tảng khiến một ngoại lệ khác được tạo. Ví dụ `IllegalStateException("Khởi tạo thất bại", ioError)` có `ioError` là cause.

**Suppressed:** các ngoại lệ phụ được lưu **cùng một ngoại lệ chính**, ví dụ lỗi khi đóng tài nguyên trong try-with-resources:

~~~java
Throwable primary = new Throwable("Thao tác thất bại");

primary.addSuppressed(
        new java.io.IOException("Đóng tài nguyên thất bại"));

System.out.println(primary.getCause()); // null
System.out.println(primary.getSuppressed().length); // 1
~~~

Đoạn trên cho thấy suppressed exception **không** trở thành cause tự động. `getCause()` vẫn là `null` dù `getSuppressed().length` là `1`.

Điều ngược lại cũng đúng: thiết lập `initCause(root)` **không tự thêm `root` vào danh sách suppressed**.

Với try-with-resources, khi thân `try` ném ngoại lệ chính rồi `close()` ném lỗi, Java thường gắn lỗi close làm suppressed vào ngoại lệ chính. Trường hợp cause của ngoại lệ chính đã khởi tạo hay chưa là **vấn đề tách biệt**.

### Đừng thay cause bằng suppressed hoặc ngược lại

Nếu `IOException` là nguồn gốc của thất bại nghiệp vụ, hãy giữ nó làm cause của wrapper. Nếu một lỗi xảy ra thêm trong quá trình dọn dẹp khi đã có lỗi chính, suppressed exceptions thường là quan hệ phù hợp. Chọn loại quan hệ đúng giúp đọc stack trace và lập công cụ phân tích chính xác.

## 12. Cause và stack trace không thay thế nhau

`Throwable` thường ghi stack trace **khi đối tượng được khởi tạo**. Stack trace phản ánh các frame gọi đến nơi tạo, còn cause liên kết tới **một ngoại lệ khác**.

~~~java
IOException root = new IOException("Không đọc được");
IllegalStateException wrapper =
        new IllegalStateException("Không tải được", root);
~~~

Đây là một đoạn minh họa trong phạm vi có import hoặc sử dụng tên đầy đủ cho `IOException`. Khi tạo wrapper, Java thông thường ghi stack trace cho wrapper. Nó không xóa stack trace của `root`.

Do đó có thể có:

- Wrapper có message, stack trace và cause.
- Cause có message và stack trace của chính nó.
- Wrapper không có cause, nhưng vẫn có stack trace.
- Wrapper có cause, nhưng stack trace riêng đã bị tắt bằng constructor cấu hình đặc biệt của lớp con.

**Đọc `getCause()` để tìm nguyên nhân**, **đọc stack trace để tìm vị trí/đường gọi**, không dùng một thứ thay thế thứ kia.

### `getLocalizedMessage()` có liên quan gì?

`getLocalizedMessage()` mặc định trả thông điệp theo hành vi của `Throwable`; lớp con có thể tùy biến. Nó không làm thay đổi trạng thái cause. Không nên suy luận thông điệp đã được bản địa hóa là dữ liệu bắt buộc để xác định nguyên nhân chính thức của lỗi.

## 13. Chương trình Java 17+ chạy độc lập

Chương trình sau chứng minh trực tiếp:

1. `getCause()==null` trong **hai trạng thái khác nhau**.
2. `initCause` thành công một lần; lần thứ hai thất bại.
3. Cause được constructor khởi tạo bằng `null` **không thể gắn lại**.
4. Tự làm cause của chính mình gây `IllegalArgumentException`.
5. Constructor nhận cause-only tạo message từ `cause.toString()`.
6. Bọc `NumberFormatException` vẫn giữ nguyên nhân gốc.
7. Suppressed exceptions không phải cause.
8. Duyệt chuỗi cause cần chống vòng tham chiếu.

Tạo tệp **`ThrowableCauseDemo.java`**:

~~~java
import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class ThrowableCauseDemo {

    static final class ConfigException extends Exception {
        ConfigException(String message) {
            super(message);
        }

        ConfigException(String message, Throwable cause) {
            super(message, cause);
        }

        ConfigException(Throwable cause) {
            super(cause);
        }
    }

    public static void main(String[] args) {
        IOException root = new IOException("disk");

        System.out.println("== uninitialized ==");
        Throwable fresh = new Throwable("fresh");
        System.out.println("fresh.before=" + fresh.getCause());
        Throwable returned = fresh.initCause(root);
        System.out.println("fresh.same=" + (fresh.getCause() == root));
        System.out.println("init.returnedSelf=" + (returned == fresh));
        try {
            fresh.initCause(new IOException("later"));
        } catch (IllegalStateException e) {
            System.out.println("fresh.second=IllegalStateException");
        }

        System.out.println("== initialized null ==");
        Throwable explicitNull = new Throwable("empty", null);
        System.out.println("explicitNull.cause=" + explicitNull.getCause());
        try {
            explicitNull.initCause(root);
        } catch (IllegalStateException e) {
            System.out.println(
                    "explicitNull.later=IllegalStateException");
        }

        Throwable byCauseNull = new Throwable((Throwable) null);
        try {
            byCauseNull.initCause(root);
        } catch (IllegalStateException e) {
            System.out.println(
                    "byCauseNull.later=IllegalStateException");
        }

        Throwable nullViaInit = new Throwable("initially free");
        nullViaInit.initCause(null);
        try {
            nullViaInit.initCause(root);
        } catch (IllegalStateException e) {
            System.out.println("nullViaInit.again=IllegalStateException");
        }

        System.out.println("== self cause ==");
        Throwable self = new Throwable("self");
        try {
            self.initCause(self);
        } catch (IllegalArgumentException e) {
            System.out.println("self.error=IllegalArgumentException");
        }

        System.out.println("== constructors ==");
        Throwable onlyCause = new Throwable(root);
        Throwable messageAndCause = new Throwable("load failed", root);
        System.out.println("onlyCause.message=" + onlyCause.getMessage());
        System.out.println(
                "onlyCause.same=" + (onlyCause.getCause() == root));
        System.out.println(
                "messageAndCause.message=" + messageAndCause.getMessage());
        System.out.println(
                "messageAndCause.same="
                        + (messageAndCause.getCause() == root));

        System.out.println("== wrapping ==");
        try {
            parsePort("abc");
        } catch (ConfigException e) {
            System.out.println("outer.message=" + e.getMessage());
            System.out.println(
                    "inner.type=" + e.getCause().getClass().getSimpleName());
            System.out.println(
                    "inner.message=" + e.getCause().getMessage());
        }

        System.out.println("== suppressed ==");
        Throwable primary = new Throwable("primary");
        primary.addSuppressed(new IOException("close"));
        System.out.println("primary.cause=" + primary.getCause());
        System.out.println(
                "primary.suppressed=" + primary.getSuppressed().length);

        System.out.println("== traversal ==");
        Throwable outer = new Throwable("outer", root);
        System.out.println(
                "chain.length=" + countDistinctCauses(outer));

        Throwable a = new Throwable("A");
        Throwable b = new Throwable("B");
        a.initCause(b);
        b.initCause(a);
        System.out.println(
                "cycle.distinct=" + countDistinctCauses(a));
    }

    static int parsePort(String text) throws ConfigException {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException cause) {
            throw new ConfigException(
                    "Invalid port: " + text, cause);
        }
    }

    static int countDistinctCauses(Throwable error) {
        Set<Throwable> seen =
                Collections.newSetFromMap(
                        new IdentityHashMap<Throwable, Boolean>());
        Throwable current = error;

        while (current != null && seen.add(current)) {
            current = current.getCause();
        }
        return seen.size();
    }
}
~~~

Biên dịch, chạy:

~~~bash
javac ThrowableCauseDemo.java
java ThrowableCauseDemo
~~~

**Stdout xác định:**

~~~text
== uninitialized ==
fresh.before=null
fresh.same=true
init.returnedSelf=true
fresh.second=IllegalStateException
== initialized null ==
explicitNull.cause=null
explicitNull.later=IllegalStateException
byCauseNull.later=IllegalStateException
nullViaInit.again=IllegalStateException
== self cause ==
self.error=IllegalArgumentException
== constructors ==
onlyCause.message=java.io.IOException: disk
onlyCause.same=true
messageAndCause.message=load failed
messageAndCause.same=true
== wrapping ==
outer.message=Invalid port: abc
inner.type=NumberFormatException
inner.message=For input string: "abc"
== suppressed ==
primary.cause=null
primary.suppressed=1
== traversal ==
chain.length=2
cycle.distinct=2
~~~

### Phân tích chi tiết kết quả

**Nhóm uninitialized:** `fresh` tạo bằng constructor `(String)` nên cause chưa khởi tạo. `getCause()` trả `null`. `initCause(root)` hợp lệ, trả về chính `fresh`. Khi thử gắn cause thứ hai, Java ném `IllegalStateException`.

**Nhóm initialized null:** `explicitNull` tạo bằng constructor `(String, Throwable)` với cause `null`; `byCauseNull` tạo bằng constructor `(Throwable)` với cause `null`. Cả hai đã khởi tạo trạng thái cause và không cho gọi `initCause`. `nullViaInit` gọi `initCause(null)` thành công, nhưng sau đó quyền khởi tạo cause cũng đã được sử dụng.

**Nhóm self cause:** Java từ chối `self.initCause(self)`; lỗi này thuộc `IllegalArgumentException` khi cause của `self` còn chưa được khởi tạo.

**Nhóm constructors:** Dạng cause-only tạo message từ `IOException.toString()`; dạng message+cause giữ message của wrapper tách biệt. Cả hai giữ tham chiếu nguyên nhân gốc.

**Nhóm wrapping:** `Integer.parseInt("abc")` ném `NumberFormatException`. `parsePort` bọc nó vào `ConfigException`; caller đọc message của lớp ngoài và type/message của cause để thấy cả hai tầng. `NumberFormatException` không biến mất chỉ vì ngoại lệ bên ngoài là checked `ConfigException`.

**Nhóm suppressed:** `primary` có một suppressed exception nhưng `getCause()` vẫn `null`; hai quan hệ không tự chuyển hóa qua lại.

**Nhóm traversal:** `countDistinctCauses` dùng một tập dựa trên **identity** của tham chiếu đối tượng thay vì `equals` để bảo đảm thuật toán dừng khi có vòng. Chuỗi `outer → root → null` có hai đối tượng; chuỗi `a → b → a` cũng có hai đối tượng phân biệt nên hàm trả `2`, không chạy vô hạn.

## 14. Thực hành tốt: ưu tiên constructor có cause

Khi API cung cấp constructor:

~~~java
public SomeException(String message, Throwable cause)
~~~

hãy ưu tiên tạo object hoàn chỉnh trong một bước:

~~~java
throw new IllegalStateException(
        "Không thể tải tài nguyên", original);
~~~

Đây là hai **dạng minh họa**, trong đó `SomeException` là tên tượng trưng và `original` là một cause hiện có.

So với:

~~~java
IllegalStateException outer =
        new IllegalStateException("Không thể tải tài nguyên");
outer.initCause(original);
throw outer;
~~~

cả hai có thể biểu thị message và cause tương tự trong trường hợp `original` hợp lệ. Tuy nhiên, constructor giúp:

- Cause được khởi tạo ngay tại thời điểm tạo đối tượng.
- Mã có ít bước và ít trạng thái trung gian.
- Giảm nguy cơ quên gắn cause.
- Tránh vô tình gọi `initCause` khi constructor đã thiết lập cause.

Không cần chuyển các đoạn mã hiện có chỉ vì thích cú pháp ngắn hơn nếu hợp đồng API có yêu cầu khác; hãy chọn dựa trên kiểu ngoại lệ và hành vi caller cần.

### Không đổi kiểu lỗi chỉ để có constructor cause

Nếu lỗi nguyên thủy `IOException` đã phù hợp hợp đồng, có thể ném lại hoặc khai báo `throws IOException` thay vì bọc thành một loại lỗi khác. Bọc lỗi nên mang lại **thông tin ngữ cảnh hoặc ranh giới API** thực sự có ích.

## 15. Khi lớp ngoại lệ cũ chỉ có constructor message

Một số thư viện hoặc lớp lỗi lịch sử chỉ cung cấp overload:

~~~java
LegacyException(String message)
~~~

Khi cần bảo toàn nguyên nhân, nếu `LegacyException` kế thừa hành vi `Throwable` cho phép `initCause`, có thể dùng:

~~~java
LegacyException error =
        new LegacyException("Thao tác thất bại");
error.initCause(original);
throw error;
~~~

Các đoạn trên là minh họa, không phải API thật của một lớp có tên `LegacyException` trong JDK. **Luôn xem constructor và hợp đồng của lớp cụ thể**:

- Một số lớp ngoại lệ chuyên biệt có cơ chế lưu/đọc cause riêng hoặc ghi đè API liên quan.
- Một số lớp chủ động khóa khả năng thiết lập cause.
- Không suy ra một lớp chỉ từ tên hoặc quan hệ kế thừa mà bỏ qua tài liệu API.

Nếu kiểm soát nguồn mã lớp cũ, có thể bổ sung overload `(String, Throwable)` để mọi caller mới không cần gắn cause muộn.

## 16. Điều gì xảy ra nếu `initCause` bị gọi sau khi đã `throw`?

Về mặt kỹ thuật, `Throwable` là một đối tượng và `initCause` kiểm tra **trạng thái cause**, không có yêu cầu riêng “chỉ được gọi trước lần ném đầu”. Nếu một đối tượng vẫn chưa khởi tạo cause, API mặc định có thể cho phép gắn cause khi đối tượng đã được bắt sau một lần `throw`.

Ví dụ:

~~~java
static void lateLink() {
    Throwable problem = new Throwable("late");
    try {
        throw problem;
    } catch (Throwable e) {
        e.initCause(new java.io.IOException("cause"));
        System.out.println(e.getCause().getMessage());
    }
}
~~~

Đây là phương thức minh họa có thể đặt trong một lớp Java. Nó in `cause`. Tuy nhiên, **không nên xem đây là cách thiết kế mặc định**: cause nên được quyết định khi bạn tạo hoặc bọc ngoại lệ, trước khi nó được công bố qua các tầng xử lý. Thêm cause sau khi đã ném/gửi log có thể khiến các thành phần quan sát được thông tin không nhất quán.

Nếu nhiều luồng cùng có tham chiếu đến ngoại lệ, hành vi đồng bộ cụ thể của `getCause` và `initCause` không thay thế được quy trình thiết kế immutable/error-reporting rõ ràng. Tránh dùng một đối tượng ngoại lệ chung có cause thay đổi theo thời gian để đại diện cho nhiều sự cố độc lập.

## 17. Những lỗi thường gặp

### Lỗi 1 — Thấy `getCause() == null` rồi luôn gọi `initCause`

Sai. Một cause **đã được khởi tạo bằng `null`** vẫn cho getter trả null nhưng không cho gắn lại.

### Lỗi 2 — Tưởng constructor message+cause với cause null tương đương constructor chỉ message

Không tương đương về quyền `initCause`. `new Throwable("x", null)` khóa trạng thái cause; `new Throwable("x")` vẫn để trạng thái chưa khởi tạo.

### Lỗi 3 — Tưởng `initCause(null)` là reset

Không. Nó được tính như một lần khởi tạo cause. Lần sau `initCause(nonNull)` không hợp lệ.

### Lỗi 4 — Gọi `initCause` hai lần để đổi nguyên nhân

Sai. `initCause` không phải setter có thể gọi nhiều lần; lần thứ hai ném `IllegalStateException`.

### Lỗi 5 — Tự làm cause

`error.initCause(error)` không được phép vì tự làm nguyên nhân trực tiếp gây vòng tham chiếu.

### Lỗi 6 — Duyệt cause mà không chống vòng

Không nên mặc định mọi chuỗi tùy biến đều kết thúc bằng `null`; thuật toán xử lý dữ liệu lỗi từ nhiều nguồn cần phát hiện tham chiếu đã gặp.

### Lỗi 7 — Nhầm cause với suppressed exceptions

`getCause()` trả nguyên nhân trực tiếp. `getSuppressed()` trả danh sách lỗi phụ đã giữ lại; việc thêm suppressed không tự điền cause.

### Lỗi 8 — Chỉ chép `cause.getMessage()` vào message mới

Nếu chỉ nối chuỗi mà không lưu cause, sẽ mất liên kết đối tượng và stack trace của nguyên nhân trong cấu trúc lỗi được ném ra.

### Lỗi 9 — Cho rằng `Throwable(Throwable)` dùng nguyên message của cause

Không chính xác. Constructor cause-only dùng `cause.toString()` khi cause khác null, thường gồm cả tên lớp.

### Lỗi 10 — Tưởng lớp con được thừa kế constructor cause

Constructor không được thừa kế; lớp con phải định nghĩa overload hoặc dùng API nó cung cấp.

### Lỗi 11 — Dùng cause để đoán kiểu ngoại lệ được catch

Một `IllegalStateException` có cause là `IOException` **không** được bắt trực tiếp bởi `catch (IOException)`. Handler chọn theo kiểu của ngoại lệ ngoài đang lan truyền, không tự chui vào cause.

### Lỗi 12 — Cho rằng cause là một thuộc tính có thể cập nhật từ nhiều nơi

Thiết kế mặc định hướng tới một lần khởi tạo. Không viết hệ thống điều phối lỗi phụ thuộc vào khả năng “sửa cause” nhiều lần.

### Lỗi 13 — Dùng `printStackTrace()` làm dữ liệu máy đọc ổn định

Stack trace có ích để chẩn đoán nhưng không phải hợp đồng định dạng ổn định. Dùng API đọc có cấu trúc khi cần xây dựng hệ thống quan sát lỗi.

### Lỗi 14 — Ghi dữ liệu bí mật trong message/cause

Thông tin của cause có thể đi kèm trong stack trace, logs và công cụ observability. Tránh đưa token, mật khẩu, dữ liệu cá nhân hoặc nội dung nội bộ nhạy cảm vào thông điệp lỗi.

### Lỗi 15 — Bọc ngoại lệ qua quá nhiều tầng không thêm ngữ cảnh

Một chuỗi dài nhiều wrapper giống hệt nhau làm tăng độ phức tạp khi điều tra. Bọc tại các ranh giới có ý nghĩa, giữ cause và thông điệp đủ cụ thể.

## 18. Câu hỏi tự kiểm tra

1. Cause khác với message như thế nào?
2. `getCause()` trả gì nếu chưa có nguyên nhân công khai?
3. Vì sao `getCause() == null` không đủ để kết luận `initCause(...)` hợp lệ?
4. Với `new Throwable()`, có thể `initCause(root)` một lần không?
5. Với `new Throwable("message", null)`, có thể `initCause(root)` không?
6. Với `new Throwable((Throwable) null)`, `getCause()` bằng bao nhiêu? Cause đã được khởi tạo chưa?
7. `initCause(null)` có cho phép gọi tiếp `initCause(root)` không?
8. `initCause` trả về một đối tượng mới hay chính đối tượng đang gọi?
9. Hai lỗi runtime nào thường gặp khi sử dụng `initCause` sai, và mỗi lỗi tương ứng điều kiện gì?
10. `getCause()` trả ngay ngoại lệ gốc sâu nhất hay chỉ một cấp trực tiếp?
11. Vì sao duyệt cause tùy biến nên dùng tập theo identity?
12. Constructor `Throwable(Throwable cause)` tạo message từ đâu?
13. Việc truyền cause vào constructor có thay đổi checked/unchecked của lớp ngoài không?
14. Suppressed exception có tự động trở thành cause không?
15. Khi nào constructor `(String, Throwable)` tốt hơn `initCause`?
16. Khi nào `initCause` vẫn hữu ích trong mã kế thừa hoặc tương thích API cũ?
17. Tại sao constructor protected bốn tham số với cause null vẫn không cho gọi `initCause` lần nữa?
18. Nếu ngoại lệ bên ngoài đã có cause, có cần đọc `getStackTrace()` của cause nữa không?
19. Việc `throw` cùng một ngoại lệ hai lần có tự làm `initCause` hợp lệ trở lại không?
20. Caller có nên dựa vào `getCause().getMessage()` mà không kiểm tra `getCause() != null` không?

### Đáp án gợi ý

1. Message là văn bản mô tả; cause là tham chiếu tới `Throwable` nguyên nhân.
2. `null`.
3. Null có thể do chưa khởi tạo hoặc đã khởi tạo bằng null.
4. Có, nếu cause chưa được khởi tạo.
5. Không, constructor đã khởi tạo cause bằng null.
6. Getter trả null, nhưng cause đã được khởi tạo.
7. Không; lần gắn null cũng chiếm quyền khởi tạo.
8. Chính đối tượng đang gọi.
9. `IllegalStateException` nếu cause đã được khởi tạo; `IllegalArgumentException` nếu tự làm cause của chính mình.
10. Chỉ một bước cause trực tiếp.
11. Để phát hiện vòng bằng tham chiếu đối tượng chứ không phụ thuộc `equals`.
12. `cause.toString()` khi cause khác null.
13. Không; phân loại của ngoại lệ ngoài phụ thuộc lớp của nó.
14. Không.
15. Khi constructor có sẵn và muốn tạo đối tượng hoàn chỉnh với ngữ cảnh ngay lập tức.
16. Khi lớp ngoại lệ chỉ có overload không nhận cause nhưng cho phép gắn cause một lần.
17. Vì overload bốn tham số đã khởi tạo cause, kể cả với giá trị null.
18. Có thể cần, vì cause có stack trace riêng.
19. Không; `throw` không reset trạng thái cause.
20. Không; phải xử lý trường hợp cause là null.

## 19. Ghi nhớ

**`getCause()` cho biết tham chiếu nguyên nhân trực tiếp, còn `initCause(...)` chỉ thiết lập nguyên nhân khi trạng thái cause của `Throwable` chưa từng được khởi tạo.** Các constructor không nhận cause có thể cho phép gọi `initCause` một lần; các constructor có tham số cause đã quyết định trạng thái ngay cả khi truyền `null`. Lần thiết lập thứ hai gây `IllegalStateException`; tự làm cause của chính mình gây `IllegalArgumentException`. Trong mã Java mới, ưu tiên constructor nhận message và cause; sử dụng `initCause` khi cần tương thích với lớp lỗi không có overload phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="throwable-internals-009">addSuppressed và getSuppressed</a>

<details>
<summary>Xem chi tiết</summary>

# `addSuppressed` và `getSuppressed` trong Java — giữ đầy đủ lỗi khi dọn dẹp

## 1. Mục tiêu bài học

Trong Java, một thao tác có thể **thất bại lần thứ nhất** khi đang xử lý công việc, rồi **thất bại lần thứ hai** lúc đóng file, stream, socket hoặc tài nguyên khác. Nếu chỉ truyền ra một ngoại lệ mà bỏ qua lỗi còn lại, việc chẩn đoán sẽ thiếu dữ kiện. `Throwable.addSuppressed(Throwable)` và `Throwable.getSuppressed()` giải quyết bài toán lưu giữ **ngoại lệ bổ sung** bên cạnh **ngoại lệ chính** (*primary exception*).

Từ Java 7, `try-with-resources` sử dụng cơ chế suppressed exceptions để giữ lỗi đóng tài nguyên mà không che mất lỗi ban đầu của phần thân. Lập trình viên cũng có thể gọi `addSuppressed` trong mã dọn dẹp thủ công khi có lý do rõ ràng.

Bạn sẽ học được:

- Khác biệt giữa **ngoại lệ chính**, **ngoại lệ suppressed** và **nguyên nhân `cause`**.
- Cách gọi `addSuppressed`/`getSuppressed` trên `Throwable` và đọc danh sách ngoại lệ.
- Thứ tự lỗi chính và lỗi suppressed khi một hoặc nhiều resource cùng thất bại.
- Hành vi khi không có suppressed exceptions, khi truyền `null`, khi thêm chính mình, khi thêm nhiều lỗi.
- Cách tắt suppression bằng constructor protected của `Throwable`/`Exception` và tác động tới API.
- Quan hệ với `try-with-resources`, `close()`, ngoại lệ từ khởi tạo resource, `catch`/`finally` và `printStackTrace`.
- Những sai lầm dễ gây mất nguyên nhân gốc và cách bảo toàn thông tin lỗi ở tầng ứng dụng.

Các ví dụ độc lập dùng Java 17+; kết quả `stdout` được ghi cụ thể. Trường hợp cố ý gây lỗi biên dịch hoặc chỉ là mảnh mã sẽ được đánh dấu.

## 2. Ba loại liên kết lỗi: primary, suppressed và cause

Xét một chương trình xử lý tệp:

```text
Mở tài nguyên thành công
       |
       v
Đọc dữ liệu bị lỗi: IOException("read failed")    <-- primary
       |
       v
Đóng tài nguyên cũng lỗi: IOException("close failed")
       |
       v
try-with-resources giữ "read failed" làm primary
và gắn "close failed" vào danh sách suppressed
```

Ngoại lệ chính là đối tượng được truyền ra cho `catch` hoặc caller. Lỗi suppressed được **đính kèm vào đối tượng chính**, chứ không ném thay cho lỗi chính. Có thể quan sát chúng bằng `primary.getSuppressed()`.

Phân biệt các khái niệm:

| Quan hệ | API | Ý nghĩa điển hình |
| --- | --- | --- |
| **Primary exception** | Đối tượng đang được `throw`/`catch` xử lý | Lỗi chính làm thao tác hiện tại thất bại |
| **Cause** | `getCause()`, constructor nhận cause, `initCause(...)` | Ngoại lệ trước đó **dẫn đến** hoặc được bọc bởi lỗi hiện tại |
| **Suppressed** | `addSuppressed(other)`, `getSuppressed()` | Lỗi **bổ sung** cần giữ lại dù không thay thế lỗi chính |

Ví dụ: đọc một cấu hình gây `IOException("read")`; đóng nguồn gây `IOException("close")`. Lỗi đóng thường là suppressed của lỗi đọc. Ngược lại, nếu bạn chủ động chuyển `NumberFormatException` thành `ParseException` vì muốn thông báo lỗi phân tích dữ liệu, `NumberFormatException` thường là **cause** của `ParseException`.

Không có quy tắc “mọi lỗi xảy ra sau đều tự trở thành suppressed”: Java tự thực hiện việc đính kèm ở các cấu trúc có quy định, nổi bật là `try-with-resources`. Một khối `finally` thủ công không mặc nhiên thêm suppressed nếu nó ném lỗi khác.

## 3. Hai phương thức trên `Throwable`

### 3.1. `addSuppressed(Throwable exception)`

Khai báo API:

```java
public final synchronized void addSuppressed(Throwable exception)
```

Phương thức thêm đối tượng `exception` vào danh sách suppressed của **đối tượng đang nhận lời gọi**, nếu suppression được bật cho đối tượng đó.

Ví dụ:

```java
Throwable primary = new IllegalStateException("work failed");
Throwable cleanup = new java.io.IOException("cleanup failed");

primary.addSuppressed(cleanup);
```

Đây là đoạn minh họa chạy được khi đặt trong phương thức Java. Sau lời gọi, `primary` vẫn là `IllegalStateException("work failed")`; `cleanup` nằm trong tập lỗi bổ sung của `primary`.

Hành vi quan trọng:

- Không đổi `getMessage()`, `getCause()` hay kiểu thực tế của đối tượng primary.
- Không tự thực hiện `throw`; phương thức chỉ thay đổi thông tin đính kèm.
- Có thể thêm nhiều đối tượng suppressed, và thứ tự thêm có thể được quan sát khi gọi `getSuppressed()`.
- Không thể thêm chính đối tượng primary vào danh sách suppressed của nó.
- Không thể truyền `null` làm đối số.
- Nếu suppression đã được **vô hiệu hóa** khi xây dựng đối tượng primary, lời gọi hợp lệ không lưu thêm lỗi nào.
- Không cần và không nên bọc suppressed bằng một `RuntimeException` không có ý nghĩa chỉ để tương thích kiểu: tham số là `Throwable`.

### 3.2. `getSuppressed()`

Khai báo:

```java
public final synchronized Throwable[] getSuppressed()
```

Phương thức trả một **mảng** chứa các đối tượng suppressed đã được ghi nhận. Nó không trả `null` khi danh sách trống.

```java
Throwable[] secondaryFailures = primary.getSuppressed();
for (Throwable failure : secondaryFailures) {
    System.out.println(failure.getClass().getSimpleName()
            + ": " + failure.getMessage());
}
```

Đây là mảnh mã giả định `primary` đã được khai báo.

Quy tắc cần nhớ:

- Nếu không có lỗi bổ sung, độ dài mảng là `0`.
- Mảng là **bản sao biểu diễn danh sách tại thời điểm gọi**, không phải quyền truy cập sửa trực tiếp vào danh sách nội bộ.
- Mảng chứa **tham chiếu đến các đối tượng `Throwable`**, không phải bản sao sâu của từng ngoại lệ. Thay đổi nội dung của một đối tượng exception (nếu API cho phép) có thể được thấy qua nhiều tham chiếu.
- Muốn thêm suppressed exception, gọi `addSuppressed(...)`, không sửa một phần tử của mảng do `getSuppressed()` trả về.
- `getSuppressed()` có thể gọi nhiều lần; mỗi lần phản ánh trạng thái đã ghi nhận ở thời điểm tương ứng.

Mọi `Exception` và `Error` đều kế thừa `Throwable` nên có hai phương thức này; không cần ép kiểu thành một subclass cụ thể.

## 4. Ví dụ hoàn chỉnh: tự thêm và đọc lỗi suppressed

Tạo tệp **`ManualSuppressedDemo.java`**:

```java
import java.io.IOException;

public class ManualSuppressedDemo {
    public static void main(String[] args) {
        IllegalStateException primary =
                new IllegalStateException("operation failed");

        primary.addSuppressed(new IOException("close stream"));
        primary.addSuppressed(new IOException("release channel"));

        System.out.println("primary=" + primary.getMessage());
        System.out.println("count=" + primary.getSuppressed().length);

        for (Throwable suppressed : primary.getSuppressed()) {
            System.out.println("suppressed=" + suppressed.getMessage());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac ManualSuppressedDemo.java
java ManualSuppressedDemo
```

`stdout` chính xác:

```text
primary=operation failed
count=2
suppressed=close stream
suppressed=release channel
```

Giải thích:

1. `primary` là lỗi công việc được tạo đầu tiên.
2. Mỗi `addSuppressed` gắn **một đối tượng ngoại lệ khác** vào `primary`.
3. `getSuppressed()` trả về hai phần tử theo thứ tự đã thêm.
4. `primary` vẫn mang kiểu `IllegalStateException` và thông điệp `operation failed`; lỗi phụ không chiếm vị trí lỗi chính.

Trong ứng dụng thật, chỉ gọi `addSuppressed` khi các ngoại lệ thực sự liên quan đến **cùng thao tác thất bại** và việc giữ nguyên lỗi chính là chủ ý có căn cứ.

## 5. `try-with-resources` tự động tạo suppressed exception như thế nào?

Đoạn minh họa:

```java
try (AutoCloseable resource = acquire()) {
    work(); // Ném lỗi chính.
}
```

Đây là **mảnh mã**, cần khai báo `acquire()`, `work()` và bắt/khai báo kiểu checked exception tương ứng. Giả sử phần thân ném ngoại lệ A, rồi `resource.close()` ném ngoại lệ B. `try-with-resources` bảo toàn A làm lỗi chính và thêm B vào `A.getSuppressed()`.

Điều này khác với cách đóng thủ công thiếu cẩn trọng:

```java
try {
    work();
} finally {
    resource.close(); // Nếu cũng ném lỗi, có thể che lỗi work().
}
```

Đây cũng là mảnh minh họa. `finally` vẫn chạy khi khối `try` ném lỗi, nhưng ngoại lệ mới từ `close()` có thể **thay thế** lý do thoát cũ, khiến lỗi A bị che nếu không chủ động xử lý.

TWR thực hiện logic quản lý ngoại lệ đã được quy định trong ngôn ngữ Java: **đảm bảo đóng tài nguyên và giữ lại thông tin về các lỗi đóng** theo mối quan hệ primary/suppressed.

### 5.1. Ví dụ chạy được: lỗi thân và lỗi đóng

Tạo tệp **`BodyAndCloseFailureDemo.java`**:

```java
import java.io.IOException;

public class BodyAndCloseFailureDemo {
    static class FailingResource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            System.out.println("closing resource");
            throw new IOException("close failed");
        }
    }

    public static void main(String[] args) {
        try (FailingResource resource = new FailingResource()) {
            System.out.println("doing work");
            throw new IllegalStateException("body failed");
        } catch (IllegalStateException failure) {
            System.out.println("primary=" + failure.getMessage());

            for (Throwable suppressed : failure.getSuppressed()) {
                System.out.println("suppressed=" + suppressed.getMessage());
            }
        }
    }
}
```

```bash
javac BodyAndCloseFailureDemo.java
java BodyAndCloseFailureDemo
```

Kết quả `stdout`:

```text
doing work
closing resource
primary=body failed
suppressed=close failed
```

Diễn biến:

- Phần thân tạo `IllegalStateException("body failed")`. Đây là lý do làm khối kết thúc đột ngột.
- Khi rời TWR, Java gọi `close()`; dòng `closing resource` được in.
- `close()` ném `IOException("close failed")`. Vì đang có lỗi chính, TWR gắn lỗi đóng vào `getSuppressed()` của `IllegalStateException`.
- Handler nhận **IllegalStateException**, không phải `IOException`.
- Đối tượng IOException vẫn có đầy đủ message, stack trace và cause riêng khi được lưu vào danh sách suppressed.

Không viết `catch (IOException)` để bắt một cách riêng lẻ lỗi đóng trong ví dụ này: lỗi đóng đã trở thành suppressed của primary và cần được kiểm tra thông qua primary.

## 6. Nếu phần thân không lỗi, ngoại lệ từ `close()` là **lỗi chính**

Không phải mọi ngoại lệ từ `close()` đều là suppressed. Nếu phần thân hoàn thành bình thường, còn đóng resource thất bại, lỗi đóng có thể **truyền ra trực tiếp**.

Tạo file **`CloseOnlyFailureDemo.java`**:

```java
import java.io.IOException;

public class CloseOnlyFailureDemo {
    static class BrokenClose implements AutoCloseable {
        @Override
        public void close() throws IOException {
            System.out.println("closing");
            throw new IOException("cannot close");
        }
    }

    public static void main(String[] args) {
        try (BrokenClose resource = new BrokenClose()) {
            System.out.println("work succeeded");
        } catch (IOException failure) {
            System.out.println("primary=" + failure.getMessage());
            System.out.println("suppressed count="
                    + failure.getSuppressed().length);
        }
    }
}
```

```bash
javac CloseOnlyFailureDemo.java
java CloseOnlyFailureDemo
```

`stdout`:

```text
work succeeded
closing
primary=cannot close
suppressed count=0
```

Không có lỗi công việc trước đó nên `IOException("cannot close")` trở thành primary. Vì đây là lỗi duy nhất, `getSuppressed()` trả mảng rỗng.

Đây là lý do khi ghi log TWR không nên giả định rằng **mọi IOException do close đều được lưu dưới suppressed**. Cần xác định đường thực thi và đối tượng exception đã thoát ra.

## 7. Nhiều tài nguyên: thứ tự đóng và danh sách suppressed

TWR đóng resource theo **thứ tự ngược** với thứ tự chúng xuất hiện trong resource specification. Khi nhiều `close()` cùng thất bại, thứ tự trong `getSuppressed()` phản ánh trình tự đính kèm khi đóng.

### 7.1. Thân ném lỗi, cả hai resource đóng đều lỗi

Tạo file **`MultipleSuppressedDemo.java`**:

```java
import java.io.IOException;

public class MultipleSuppressedDemo {
    static class Resource implements AutoCloseable {
        private final String name;

        Resource(String name) {
            this.name = name;
        }

        @Override
        public void close() throws IOException {
            System.out.println("close " + name);
            throw new IOException("close " + name + " failed");
        }
    }

    public static void main(String[] args) {
        try (Resource first = new Resource("first");
             Resource second = new Resource("second")) {

            System.out.println("body");
            throw new IllegalArgumentException("body failed");

        } catch (IllegalArgumentException failure) {
            System.out.println("primary=" + failure.getMessage());

            for (Throwable suppressed : failure.getSuppressed()) {
                System.out.println("suppressed=" + suppressed.getMessage());
            }
        }
    }
}
```

```bash
javac MultipleSuppressedDemo.java
java MultipleSuppressedDemo
```

`stdout`:

```text
body
close second
close first
primary=body failed
suppressed=close second failed
suppressed=close first failed
```

Trình tự chính xác:

1. Tạo `first` rồi `second`.
2. Thân ném `IllegalArgumentException("body failed")` làm primary.
3. Java gọi `second.close()` **trước**, ghi nhận `IOException("close second failed")` thành suppressed.
4. Java gọi `first.close()`, ghi nhận `IOException("close first failed")` thành suppressed tiếp theo.
5. `catch` nhận lỗi thân và đọc danh sách hai lỗi bổ sung theo thứ tự chúng được ghi nhận.

Một tài nguyên khác có thể đóng thành công; khi đó không có suppressed exception bổ sung tương ứng. Thứ tự đóng không phụ thuộc thứ tự gọi resource trong thân chương trình.

### 7.2. Thân thành công nhưng **hai lần đóng** đều thất bại

Tạo file **`CloseOrderPrimaryDemo.java`**:

```java
import java.io.IOException;

public class CloseOrderPrimaryDemo {
    static class Resource implements AutoCloseable {
        private final String name;

        Resource(String name) {
            this.name = name;
        }

        @Override
        public void close() throws IOException {
            System.out.println("close " + name);
            throw new IOException(name + " close failed");
        }
    }

    public static void main(String[] args) {
        try (Resource first = new Resource("first");
             Resource second = new Resource("second")) {

            System.out.println("body succeeded");

        } catch (IOException failure) {
            System.out.println("primary=" + failure.getMessage());
            System.out.println("suppressed count="
                    + failure.getSuppressed().length);

            for (Throwable suppressed : failure.getSuppressed()) {
                System.out.println("suppressed=" + suppressed.getMessage());
            }
        }
    }
}
```

```bash
javac CloseOrderPrimaryDemo.java
java CloseOrderPrimaryDemo
```

`stdout`:

```text
body succeeded
close second
close first
primary=second close failed
suppressed count=1
suppressed=first close failed
```

Lần đóng `second` gặp lỗi đầu tiên; tại thời điểm ấy chưa có lỗi primary từ phần thân. Vì vậy lỗi đóng của `second` trở thành primary, còn lỗi đóng `first` sau đó được đính kèm suppressed. Điều này giải thích vì sao **vị trí suppressed phụ thuộc thứ tự và thời điểm lỗi**, không chỉ phụ thuộc API nào ném exception.

### 7.3. Ngoại lệ khi khởi tạo resource thứ hai

Một tình huống cũng đáng chú ý:

```java
try (Resource first = openFirst();
     Resource second = openSecond()) {
    work();
}
```

Đây là **mảnh mã minh họa**. Nếu `openSecond()` ném lỗi sau khi `first` đã được khởi tạo thành công, TWR sẽ đóng `first`. Nếu `first.close()` cũng ném, lỗi đóng có thể được đính kèm suppressed vào lỗi khởi tạo `second`. Quy tắc này giúp giữ nguyên lỗi gốc **ở giai đoạn khởi tạo**, chứ không chỉ lỗi trong phần thân.

### 7.4. Không đồng nhất suppressed với thứ tự thời gian toàn cục

Một đối tượng `Throwable` có thể có suppressed được thêm thủ công ở bất kỳ giai đoạn thích hợp. Vì thế, danh sách suppressed không phải một timeline bất biến của toàn bộ ứng dụng. Nó chỉ phản ánh các đối tượng đã được ghi nhận thông qua cơ chế tương ứng. Muốn xác định nguyên nhân, cần đọc message, stack trace, cause, ngữ cảnh và lịch sử tài nguyên.

## 8. Lỗi sử dụng API: không được tự suppress hoặc truyền `null`

`Throwable.addSuppressed` có kiểm tra đầu vào nhằm tránh cấu trúc lỗi bất hợp lý.

### 8.1. Tự thêm chính mình: `IllegalArgumentException`

```java
Throwable failure = new IllegalStateException("root");
failure.addSuppressed(failure); // Lỗi: self-suppression.
```

Đây là mảnh mã có lỗi runtime cố ý. Chính ngoại lệ primary không được là suppressed của bản thân nó. Cấu trúc tự tham chiếu sẽ rất khó diễn giải khi in stack trace, và Java chủ động ngăn self-suppression trực tiếp.

### 8.2. Thêm `null`: `NullPointerException`

```java
Throwable failure = new IllegalStateException("root");
failure.addSuppressed(null); // Lỗi: null không phải Throwable hợp lệ.
```

Đây là mảnh mã cố ý gây lỗi runtime. Đối số suppressed phải là một đối tượng `Throwable` không null.

### 8.3. Ví dụ chạy được: kiểm tra hai trường hợp

Tạo file **`SuppressedValidationDemo.java`**:

```java
public class SuppressedValidationDemo {
    public static void main(String[] args) {
        IllegalStateException root =
                new IllegalStateException("root");

        try {
            root.addSuppressed(root);
        } catch (IllegalArgumentException failure) {
            System.out.println("self: "
                    + failure.getClass().getSimpleName());
        }

        try {
            root.addSuppressed(null);
        } catch (NullPointerException failure) {
            System.out.println("null: "
                    + failure.getClass().getSimpleName());
        }

        System.out.println("count=" + root.getSuppressed().length);
    }
}
```

```bash
javac SuppressedValidationDemo.java
java SuppressedValidationDemo
```

`stdout`:

```text
self: IllegalArgumentException
null: NullPointerException
count=0
```

Cả hai lần thêm đều thất bại; danh sách suppressed của `root` vẫn trống. Đừng “khắc phục” bằng cách bắt rồi bỏ qua lỗi API: điều đúng là **không truyền `null`, không tự tham chiếu** ngay từ đầu.

**Điểm tinh tế:** hạn chế là không được thêm **chính đối tượng nhận lời gọi**. Java không buộc mọi đối tượng suppressed phải có kiểu khác hay message khác. Một ngoại lệ có thể mang cùng loại, cùng thông điệp với một lỗi khác mà vẫn là đối tượng hợp lệ.

## 9. `getSuppressed()` trả mảng rỗng và không để caller sửa danh sách bên trong

Để duyệt an toàn:

```java
for (Throwable secondary : failure.getSuppressed()) {
    // Ghi log hoặc phân tích nguyên nhân phụ.
}
```

Đây là mảnh mã minh họa với `failure` là một `Throwable`. Không cần kiểm tra `getSuppressed() != null`; một đối tượng bình thường không có suppressed trả mảng độ dài 0.

### Ví dụ chạy được: sửa mảng trả về không sửa danh sách suppressed

Tạo file **`SuppressedArraySnapshotDemo.java`**:

```java
import java.io.IOException;

public class SuppressedArraySnapshotDemo {
    public static void main(String[] args) {
        Exception primary = new Exception("main");
        primary.addSuppressed(new IOException("original"));

        Throwable[] snapshot = primary.getSuppressed();
        snapshot[0] = new IOException("replacement");

        System.out.println("snapshot="
                + snapshot[0].getMessage());
        System.out.println("stored="
                + primary.getSuppressed()[0].getMessage());
    }
}
```

```bash
javac SuppressedArraySnapshotDemo.java
java SuppressedArraySnapshotDemo
```

`stdout`:

```text
snapshot=replacement
stored=original
```

Mảng kết quả là một snapshot độc lập về **vị trí phần tử**, nên gán vào `snapshot[0]` không làm thay đổi mảng nội bộ của primary. Nhưng các phần tử của snapshot ban đầu vẫn là tham chiếu đến các đối tượng `Throwable` thực tế; nếu bạn dùng `snapshot[0].addSuppressed(...)` trước khi thay phần tử, bạn đang thao tác trên chính đối tượng lỗi đó.

Ngoài ra, không nên lưu mảng snapshot rồi cho rằng nó tự cập nhật nếu primary có suppressed mới. Muốn xem dữ liệu mới, gọi `getSuppressed()` lại.

## 10. Phân biệt `getCause()` và `getSuppressed()`

Hai API phục vụ **hai hướng liên kết** khác nhau. Có thể kết hợp cả hai trên cùng một đối tượng lỗi.

Tạo file **`CauseVsSuppressedDemo.java`**:

```java
import java.io.IOException;

public class CauseVsSuppressedDemo {
    public static void main(String[] args) {
        IllegalArgumentException parseCause =
                new IllegalArgumentException("invalid number");

        IOException failure =
                new IOException("cannot import", parseCause);
        failure.addSuppressed(
                new IllegalStateException("temporary file cleanup failed"));

        System.out.println("primary=" + failure.getMessage());
        System.out.println("cause=" + failure.getCause().getMessage());
        System.out.println("suppressed="
                + failure.getSuppressed()[0].getMessage());
    }
}
```

```bash
javac CauseVsSuppressedDemo.java
java CauseVsSuppressedDemo
```

`stdout`:

```text
primary=cannot import
cause=invalid number
suppressed=temporary file cleanup failed
```

**Diễn giải:**

- `IOException("cannot import", parseCause)` là lỗi hiện tại, có **cause** giải thích thất bại sâu hơn lúc parse.
- `addSuppressed(...)` giữ một lỗi phát sinh thêm khi cleanup.
- `getCause()` trả **một** nguyên nhân (hoặc `null`), trong khi `getSuppressed()` trả **mảng** lỗi bổ sung (có thể rỗng).
- Suppressed không thay thế cause; một suppressed exception lại có thể có cause và suppressed riêng.

Ví dụ cây chẩn đoán:

```text
IOException: cannot import                 [primary]
  Caused by: IllegalArgumentException     [cause]
    invalid number
  Suppressed: IllegalStateException       [suppressed]
    temporary file cleanup failed
```

Đây là **sơ đồ khái niệm**, không phải nội dung `printStackTrace()` chính xác theo một phiên bản JVM. Phần trình bày stack trace thực tế có thể thụt dòng, cắt bớt frame trùng lặp hoặc hiển thị các phần theo bố cục khác.

Không tạo cause chain hoặc suppressed chỉ để có một hình thức stack trace đẹp. Chọn liên kết theo bản chất lỗi.

## 11. Tắt suppression: danh sách rỗng không chứng minh cleanup thành công

Lớp ngoại lệ con có thể gọi constructor `protected` với `enableSuppression=false`. Khi ấy `addSuppressed(validThrowable)` vẫn được gọi hợp lệ nhưng **không lưu** lỗi phụ, và `getSuppressed()` luôn là mảng rỗng trên chính đối tượng đó.

~~~java
class NoSuppressionFailure extends RuntimeException {
    NoSuppressionFailure(String message) {
        super(message, null, false, true);
    }
}
~~~

Trong `try-with-resources`, nếu thân khối ném `NoSuppressionFailure` rồi `close()` cũng lỗi, việc đóng **vẫn có thể thất bại** dù lỗi chính không chứa suppressed. Vì vậy đừng xem `getSuppressed().length == 0` là xác nhận mọi bước cleanup thành công.

Không vô hiệu hóa suppression theo thói quen. Các tổ hợp `enableSuppression`/`writableStackTrace`, cách constructor áp dụng cờ và ảnh hưởng tới cause/stack trace được thực hành đầy đủ ở bài cấu hình `Throwable`; ở đây chỉ cần nhớ ranh giới: API đọc lỗi phụ không thể khôi phục một lỗi phụ mà đối tượng chính **đã chọn không lưu**.

## 12. Nếu resource bị đóng nhiều lần, suppressed có tự loại trùng không?

`addSuppressed` không phải tập hợp duy nhất theo kiểu `Set`. Ngoài trường hợp tự suppress trực tiếp và đối số null, API không tự dùng message, class hay stack trace để lọc trùng theo nội dung.

Ví dụ:

```java
Throwable primary = new IllegalStateException("work");
Throwable cleanup = new java.io.IOException("cleanup");
primary.addSuppressed(cleanup);
primary.addSuppressed(cleanup);
```

Đây là mảnh mã hợp lệ: cùng **một đối tượng cleanup** được thêm hai lần. Danh sách phản ánh hai lần thêm. Do đó:

- Không diễn giải số phần tử suppressed như số tài nguyên duy nhất nếu mã có thể thêm cùng một lỗi nhiều lần.
- Tránh thêm một lỗi nhiều lần bằng tay khi nó đã được TWR tự ghi nhận.
- Không sử dụng `addSuppressed` như thay thế cho cơ chế deduplication theo ngữ nghĩa nghiệp vụ.

Khi thấy hai dòng lỗi cleanup giống nhau trong log, cần xem việc quản lý tài nguyên và nơi gọi `addSuppressed`, không vội kết luận JVM “đóng resource hai lần” nếu chưa có bằng chứng.

## 13. Stack trace hiển thị suppressed exceptions như thế nào?

`Throwable.printStackTrace()` được thiết kế để hiển thị cấu trúc nguyên nhân và suppressed exceptions. Trong stack trace văn bản phổ biến, một suppressed exception xuất hiện như một mục thụt dòng có tiền tố tương tự `Suppressed:` dưới ngoại lệ chứa nó; cause thường xuất hiện cùng nhãn `Caused by:`.

Ví dụ mảnh mã:

```java
try (FailingResource resource = acquire()) {
    performWork();
} catch (Exception failure) {
    failure.printStackTrace();
}
```

Đây là **mảnh minh họa**, cần định nghĩa `FailingResource`, `acquire()` và `performWork()` phù hợp. `printStackTrace()` thường ghi vào `System.err`, vì vậy không nên kiểm tra chính xác toàn bộ chuỗi stack trace như `stdout` cố định của các ví dụ.

Khi debug, nên đọc:

1. Kiểu và thông điệp của **primary**.
2. Những dòng frame cho biết nơi lỗi chính phát sinh.
3. `Caused by` để theo dõi nguyên nhân sâu hơn.
4. `Suppressed` để thấy những lỗi lúc cleanup hoặc hoạt động bổ sung.
5. Đường đi của resource và chính sách mở/đóng liên quan.

Mọi suppressed exception đều là một `Throwable` có thể có stack trace và cause riêng. Không nên chỉ ghi mỗi `failure.getMessage()` nếu ứng dụng cần thông tin pháp y đầy đủ; cách ghi log có cấu trúc hoặc in toàn bộ throwable thường hữu ích hơn, nhưng phải tuân thủ quy định bảo mật, không làm lộ dữ liệu nhạy cảm.

### Không phải “suppressed” nghĩa là lỗi đã được xử lý an toàn

Tên suppressed nói rằng lỗi **không được ném làm primary** trong tình huống đó. Nó không bảo đảm việc cleanup thành công hoặc có thể bỏ qua hậu quả. Đóng một file output không thành công có thể làm dữ liệu chưa được flush đúng; lỗi đó vẫn quan trọng cho vận hành và cần được xem xét.

## 14. Dùng `addSuppressed` thủ công khi nào?

`try-with-resources` đã tự quản lý phần lớn trường hợp resource `AutoCloseable`. Thủ công thường chỉ cần khi một tầng phối hợp thực hiện **nhiều thao tác dọn dẹp không thể biểu diễn phù hợp bằng TWR**, và muốn giữ lỗi chính thay vì cho lỗi dọn dẹp che khuất nó.

Ví dụ mẫu ý tưởng:

```java
try {
    doWork();
} catch (java.io.IOException primary) {
    try {
        cleanup();
    } catch (java.io.IOException cleanupFailure) {
        primary.addSuppressed(cleanupFailure);
    }
    throw primary;
}
```

Đây là **mảnh mã minh họa**, đòi hỏi `doWork()`/`cleanup()` đã được định nghĩa, phương thức bao ngoài bắt/khai báo `IOException` và có quy tắc dọn dẹp cả trên đường thành công. Mục tiêu là cho thấy cách bảo toàn lỗi ban đầu khi **cả hai thao tác đều thất bại**; nó **không** phải template thay thế đầy đủ `finally` hay `try-with-resources` vì đoạn chỉ dọn dẹp trong đường lỗi và không nói đến nhánh hoàn tất bình thường.

Khi tự triển khai cleanup cho nhiều tài nguyên, phải xét các nhánh:

- Công việc thành công, cleanup thành công.
- Công việc thành công, cleanup thất bại (lỗi cleanup cần được truyền ra hoặc xử lý).
- Công việc thất bại, cleanup thành công (giữ lỗi công việc).
- Công việc thất bại, cleanup cũng thất bại (giữ lỗi công việc, đính kèm lỗi cleanup).
- Khởi tạo resource thất bại sau khi một resource khác đã mở.
- Nhiều cleanup cùng thất bại; không dừng tại lần cleanup lỗi đầu nếu hợp đồng cần cố dọn tiếp.

Vì logic dễ sai, **ưu tiên TWR** bất cứ khi nào phạm vi sở hữu resource và kiểu API cho phép.

### Không “suppress” một lỗi không liên quan

`addSuppressed` không có ràng buộc kỹ thuật rằng ngoại lệ bổ sung bắt buộc từ `close()`. Tuy nhiên, nếu đính kèm lỗi hoàn toàn không liên quan, người đọc stack trace sẽ hiểu sai quan hệ nguyên nhân. Chỉ dùng khi các lỗi phát sinh trong cùng đơn vị công việc hoặc cleanup có quan hệ rõ ràng.

### Không dùng `initCause` để thay `addSuppressed` một cách tùy tiện

`initCause`/constructor cause thể hiện một **chuỗi nguyên nhân**: lỗi A dẫn tới lỗi B hoặc bị B bọc lại. Lỗi cleanup phát sinh **song song sau lỗi công việc** không thường là nguyên nhân làm công việc thất bại trước đó. Gắn cleanup làm cause của primary có thể tạo câu chuyện nhân quả sai; suppressed mới là quan hệ phù hợp hơn.

## 15. Tình huống thực tế và cách chọn xử lý

| Tình huống | Lỗi nên là primary | Suppressed nên chứa |
| --- | --- | --- |
| Đọc tệp lỗi, rồi đóng reader lỗi | Lỗi đọc | Lỗi đóng reader |
| Ghi dữ liệu thành công, chỉ flush/close lỗi | Lỗi flush/close | Có thể không có |
| Thân TWR lỗi, hai resource cùng đóng lỗi | Lỗi thân | Hai lỗi đóng theo trình tự |
| Thân TWR thành công, hai resource đóng lỗi | Lỗi đóng đầu tiên theo thứ tự đóng | Lỗi đóng tiếp theo |
| Tạo resource thứ hai lỗi, đóng resource thứ nhất lỗi | Lỗi tạo resource thứ hai | Lỗi đóng resource thứ nhất |
| Biến exception từ parse được bọc thành lỗi nghiệp vụ | Lỗi nghiệp vụ, với parse là **cause** | Chỉ lỗi phụ khi có |
| Resource khai báo kiểu có suppression disabled | Lỗi chính tùy đường chạy | Có thể không lưu được lỗi phụ |
| Cleanup thủ công ném lỗi mới trong `finally` mà không bắt | Lỗi mới có thể thay lỗi cũ | Không tự gắn suppressed như TWR |

Kết quả thực tế vẫn phụ thuộc lỗi nào phát sinh trước, resource nào đã được khởi tạo thành công, kiểu ngoại lệ nào được ném và việc suppression có bật cho **đối tượng primary** hay không.

### Ví dụ ứng dụng: log chính xác nhiều tầng

Một hàm điều phối có thể cần ghi một lỗi thất bại với cả cause và suppressed. Thay vì chỉ:

```java
System.err.println(failure.getMessage());
```

hãy cân nhắc gửi toàn bộ đối tượng throwable tới logging framework thích hợp hoặc chẩn đoán:

```java
failure.printStackTrace(System.err);
```

Đây là hai **mảnh lệnh minh họa** giả định `failure` là `Throwable`. Khi in toàn bộ exception, Java có khả năng trình bày cause/suppressed; khi chỉ in message, phần lớn thông tin lỗi bổ sung không được hiển thị.

Nếu chương trình chạy ở production, có thể dùng logger có cơ chế lưu exception object. Đừng log lặp ở mọi tầng khi cùng ngoại lệ tiếp tục được ném lên trên, vì có thể tạo nhiều bản ghi trùng và khó xác định sự cố chính.

## 16. Lỗi và hiểu nhầm phổ biến

**“Suppressed exception là lỗi bị bỏ qua hoàn toàn.”** Sai. Nó vẫn được lưu dưới đối tượng primary và có thể đọc/in để chẩn đoán; không trở thành primary không có nghĩa được bỏ qua về mặt vận hành.

**“Cứ có `close()` ném lỗi thì lỗi đóng luôn là suppressed.”** Sai. Khi phần thân thành công, lỗi đóng đầu tiên có thể là primary.

**“`getSuppressed()` trả `null` nếu không có lỗi phụ.”** Sai. Nó trả mảng rỗng.

**“`addSuppressed` sẽ ném lỗi phụ thay cho lỗi chính.”** Sai. Phương thức chỉ gắn thông tin vào một đối tượng Throwable; nó không tự thực hiện `throw`.

**“Có thể gọi `root.addSuppressed(root)`.”** Sai. Self-suppression bị từ chối bằng `IllegalArgumentException`.

**“`addSuppressed(null)` chỉ đơn giản bị bỏ qua.”** Sai. `null` bị từ chối bằng `NullPointerException`.

**“Đổi `getSuppressed()[0]` có thể sửa danh sách nội bộ.”** Sai. Mảng được trả là bản sao; sửa vị trí phần tử của nó không thay nội dung danh sách nội bộ.

**“`cause` và `suppressed` đều chỉ lỗi gốc, dùng cái nào cũng như nhau.”** Sai. Cause chỉ chuỗi nguyên nhân; suppressed ghi lỗi bổ sung, đặc biệt là cleanup thất bại.

**“`try-with-resources` chỉ lưu một lỗi đóng dù nhiều resource lỗi.”** Sai. Có thể lưu nhiều lỗi suppressed trong thứ tự ghi nhận, tùy chính sách suppression và đường thoát.

**“Lỗi đóng resource thứ nhất luôn làm primary dù được liệt kê bên trái.”** Sai. Java đóng từ phải sang trái; khi thân không lỗi, resource được đóng đầu tiên và thất bại có thể cung cấp primary.

**“Tắt suppression cũng tắt toàn bộ cơ chế kiểm tra tham số.”** Sai. `null` và self-suppression vẫn không hợp lệ.

**“Throwable nào cũng cho phép lưu suppressed.”** Không nhất thiết. Lớp con có thể tắt suppression lúc khởi tạo.

**“`finally` thủ công tự tạo suppressed y hệt TWR.”** Sai. Nếu `finally` ném lỗi mới, nó có thể che lỗi cũ. Muốn bảo toàn cần thiết kế thủ công hoặc dùng TWR.

**“`getSuppressed().length == 0` chứng minh không hề có lỗi cleanup.”** Không chắc. Có thể không có lỗi, có thể lỗi không được gắn vào primary, hoặc suppression trên primary đã bị vô hiệu hóa.

**“Các phần tử suppressed là bản sao của Throwable nên không còn stack trace thật.”** Sai. Mảng chứa tham chiếu đến các đối tượng Throwable gốc được ghi nhận.

**“Có thể bỏ qua lỗi close vì đã lưu trong suppressed.”** Sai. Một lỗi đóng stream ghi có thể mang hậu quả về dữ liệu, giao dịch hoặc resource leak và phải được xem xét theo yêu cầu ứng dụng.

## 17. Câu hỏi tự kiểm tra

1. **`Throwable.addSuppressed(x)` có thay đổi primary exception thành `x` không?** Không. Nó đính kèm `x` dưới primary.
2. **Khi nào lỗi `close()` là suppressed?** Khi đã có một primary đang được truyền đi và TWR ghi nhận lỗi đóng bổ sung vào nó.
3. **Nếu chỉ `close()` thất bại, không có lỗi thân, kết quả thường thế nào?** Lỗi đóng trở thành primary.
4. **`getSuppressed()` trả kiểu gì?** `Throwable[]`.
5. **Không có suppressed, mảng trả dài bao nhiêu?** `0`.
6. **`addSuppressed(root)` khi gọi trên `root` gây lỗi nào?** `IllegalArgumentException`.
7. **`addSuppressed(null)` gây lỗi nào?** `NullPointerException`.
8. **Hai resource A rồi B trong header TWR sẽ đóng theo thứ tự nào?** B rồi A.
9. **Thân thành công, B đóng lỗi rồi A đóng lỗi: lỗi nào là primary?** Lỗi đóng B, lỗi đóng A là suppressed của lỗi B.
10. **Thân ném E, B/A đều đóng lỗi: primary là gì?** E; các lỗi đóng được gắn suppressed theo quy tắc, nếu suppression bật.
11. **Có thể vừa có `getCause()` vừa có `getSuppressed()` trên một lỗi không?** Có. Hai quan hệ này độc lập về ý nghĩa.
12. **Nếu tắt suppression khi tạo exception, `getSuppressed()` thế nào?** Luôn trả mảng rỗng cho đối tượng đó.
13. **Sửa `Throwable[] result = ex.getSuppressed(); result[0] = ...;` có sửa danh sách bên trong không?** Không.
14. **`printStackTrace()` có thể biểu diễn suppressed không?** Có, nó được thiết kế để hiển thị cùng cause và stack trace.
15. **Nên thay TWR bằng `finally { resource.close(); }` cho đơn giản?** Thường không, vì dễ làm mất lỗi chính khi close cũng lỗi.
16. **Có nên thêm một ngoại lệ giống nhau nhiều lần chỉ để tăng số lượng suppressed?** Không, trừ khi có lý do thực sự và muốn lưu nhiều lần; API không phải hệ thống đếm resource duy nhất.
17. **Nếu suppressed chứa `IOException`, `catch (IOException)` đặt sau TWR có tự bắt lỗi phụ đó khi primary là `IllegalStateException` không?** Không. Handler chọn theo **loại của primary**; muốn đọc IOException phụ thì duyệt `getSuppressed()`.
18. **Lỗi bị suppressed có đồng nghĩa ứng dụng đã xử lý an toàn không?** Không; nó vẫn có thể biểu thị thao tác dọn dẹp thất bại nghiêm trọng.

## 18. Nguồn chuẩn Java 17

- [Java SE 17 API — Throwable.addSuppressed](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html#addSuppressed(java.lang.Throwable)): kiểm tra đối số và lưu suppressed exception.
- [Java SE 17 API — Throwable.getSuppressed](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html#getSuppressed()): danh sách lỗi phụ, mảng kết quả và suppression disabled.
- [Java SE 17 API — Throwable constructors](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): `enableSuppression` và `writableStackTrace`.
- [Java Language Specification 17 — `14.20.3 try-with-resources](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3): ngữ nghĩa primary/suppressed và thứ tự đóng tài nguyên.
- [Java SE 17 API — AutoCloseable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html): hợp đồng `close()` và checked exception.
- [Java SE 17 API — Throwable.printStackTrace](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html#printStackTrace()): in nguyên nhân, stack trace và suppressed.

**Ghi nhớ:** `addSuppressed` gắn một lỗi bổ sung vào **một đối tượng Throwable chính**, còn `getSuppressed()` trả lại các lỗi bổ sung ấy theo dạng mảng. Trong `try-with-resources`, Java thường tự dùng cơ chế này để giữ lỗi công việc làm **primary** và lưu lỗi dọn dẹp thành **suppressed**. Đọc cả `cause` và `suppressed` để chẩn đoán, không nhầm suppression với việc lỗi đã được khắc phục.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="throwable-internals-010">Cấu hình enableSuppression và writableStackTrace</a>

<details>
<summary>Xem chi tiết</summary>

# Cấu hình `enableSuppression` và `writableStackTrace` trong Java `Throwable`

Một đối tượng `Throwable` trong Java mang nhiều loại thông tin để giải thích sự cố: **thông điệp**, **nguyên nhân** (`cause`), **những ngoại lệ phụ** (`suppressed exceptions`) và **stack trace** mô tả vị trí/ngăn xếp lời gọi. Với các lớp ngoại lệ được thiết kế có chủ đích, Java cung cấp hai tùy chọn khi khởi tạo:

- **`enableSuppression`**: có cho phép gắn và lưu các ngoại lệ phụ bằng `addSuppressed()` hay không.
- **`writableStackTrace`**: đối tượng có được xây dựng với stack trace có thể ghi lại và cập nhật hay không.

Hai cờ **độc lập**. Tắt `enableSuppression` không tự động bỏ stack trace; tắt `writableStackTrace` không tự động xóa `cause` hay thông điệp; tắt cả hai cũng **không ngăn ngoại lệ được ném và bắt**. Các lựa chọn này chủ yếu hữu ích khi thiết kế một **lớp ngoại lệ đặc biệt**, vì chúng làm thay đổi thông tin chẩn đoán mà các API, logger và `try-with-resources` có thể lưu giữ.

Trên Java 17+, các cờ được cấu hình tại thời điểm tạo đối tượng, qua constructor bốn tham số dạng `(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace)` mà các lớp `Throwable`/`Exception`/`RuntimeException` cung cấp ở mức truy cập **`protected`**. Vì vậy code sử dụng thường định nghĩa **lớp ngoại lệ con** và chuyển các cờ tới `super(...)`, chứ không gọi constructor bốn tham số của `Throwable` trực tiếp từ mã client không có quyền truy cập.

## 1. Bốn mảnh thông tin của một `Throwable`

Xét cách tạo ngoại lệ tiêu chuẩn:

~~~java
// Đoạn minh họa bên trong một phương thức.
Throwable original = new IllegalArgumentException("đầu vào sai");
IllegalStateException wrapped =
        new IllegalStateException("không xử lý được", original);
~~~

Đối tượng `wrapped` mang những thông tin có ý nghĩa khác nhau:

| Thông tin | API thường dùng | Ý nghĩa |
| --- | --- | --- |
| Thông điệp | `getMessage()` | Mô tả thất bại, ví dụ `không xử lý được` |
| Nguyên nhân | `getCause()` | Ngoại lệ trước dẫn tới ngoại lệ hiện tại, ở đây là `original` |
| Ngoại lệ phụ | `getSuppressed()`, `addSuppressed(Throwable)` | Các lỗi bổ sung xuất hiện trong khi xử lý hoặc dọn dẹp, chẳng hạn `close()` thất bại |
| Stack trace | `getStackTrace()`, `printStackTrace()`, `fillInStackTrace()` | Những frame lời gọi được ghi nhận tại thời điểm capture stack trace |

**`cause` và `suppressed` không đồng nghĩa.** Ví dụ một `IOException` từ bước đọc có thể là ngoại lệ chính, còn lỗi đóng stream được `try-with-resources` đính kèm như suppressed. Khi tầng nghiệp vụ bọc `IOException` thành ngoại lệ khác, `IOException` có thể trở thành `cause` của ngoại lệ bọc.

Theo cách tạo thông thường, stack trace được điền khi một `Throwable` được xây dựng, nhưng một số lớp và constructor có thể chọn không thu thập hoặc thay đổi thông tin đó. Hai cờ của bài học không quyết định một lời gọi có ném ngoại lệ hay không; chúng quyết định **đối tượng giữ lại những thông tin chẩn đoán nào**.

## 2. Cú pháp constructor bốn tham số

Các lớp nền liên quan cung cấp constructor:

~~~java
// Chữ ký minh họa, không phải lời khai báo có thể đặt độc lập ngoài lớp.
protected Throwable(
        String message,
        Throwable cause,
        boolean enableSuppression,
        boolean writableStackTrace)
~~~

`Exception` và `RuntimeException` cũng có constructor bốn tham số với ý nghĩa tương ứng, ở mức `protected`. Để sử dụng an toàn, định nghĩa một lớp ngoại lệ riêng:

~~~java
public class ConfigurableFailure extends RuntimeException {
    public ConfigurableFailure(
            String message,
            Throwable cause,
            boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
~~~

Đây là một **lớp độc lập hợp lệ** nếu lưu trong tệp `ConfigurableFailure.java`. Vì kế thừa `RuntimeException`, nó là **unchecked exception**; nếu cần checked exception, lớp có thể kế thừa `Exception` và dùng constructor tương ứng.

Các tham số:

1. `message`: thông điệp chi tiết, có thể là `null`; trả về từ `getMessage()`.
2. `cause`: nguyên nhân gây ra ngoại lệ hiện tại, có thể `null`; trả về từ `getCause()`.
3. `enableSuppression`: bật/tắt khả năng ghi nhận các ngoại lệ bổ sung.
4. `writableStackTrace`: bật/tắt việc tạo và ghi stack trace trên **chính đối tượng này**.

Ví dụ tạo một ngoại lệ có đầy đủ thông tin:

~~~java
ConfigurableFailure detailed =
        new ConfigurableFailure("lỗi chi tiết", null, true, true);
~~~

Ví dụ tạo ngoại lệ tối giản:

~~~java
ConfigurableFailure minimal =
        new ConfigurableFailure("lỗi tối giản", null, false, false);
~~~

Các đoạn gọi trên giả định lớp `ConfigurableFailure` đã được khai báo. Việc tạo `minimal` không tự ném lỗi; khi cần chuyển sang xử lý ngoại lệ, bạn mới dùng `throw minimal;`.

**Lưu ý phạm vi truy cập:** vì constructor trên `Throwable` là `protected`, đoạn client ngoài phạm vi được phép như `new Throwable("x", null, false, false)` sẽ không biên dịch. Dùng lớp con phù hợp và công bố constructor của lớp con theo ý định thiết kế.

## 3. `enableSuppression=true`: lưu các lỗi phụ

Khi suppression được bật, lời gọi:

~~~java
// Đoạn minh họa trong phương thức có biến Throwable primary.
primary.addSuppressed(new java.io.IOException("lỗi đóng"));
~~~

có thể thêm một `Throwable` phụ vào danh sách của `primary`. Gọi:

~~~java
Throwable[] extra = primary.getSuppressed();
~~~

sẽ trả một mảng các lỗi đã được ghi nhận, theo thứ tự thêm vào. Với một `Throwable` thông thường có bật suppression và chưa có lỗi phụ nào, `getSuppressed()` trả về mảng rỗng.

Cơ chế này được Java dùng trong `try-with-resources` khi thao tác chính ném lỗi, và việc đóng tài nguyên cũng ném lỗi. Lỗi chính vẫn được ưu tiên làm ngoại lệ truyền ra; ngoại lệ đóng được ghi nhận như thông tin phụ.

Ví dụ logic:

~~~text
Đọc dữ liệu ném IOException("lỗi đọc")
    |
    +-- close() ném IOException("lỗi đóng")
    |
    v
Ngoại lệ chính: lỗi đọc
    Suppressed: lỗi đóng
~~~

Lưu ý rằng Java **không tự gắn mọi ngoại lệ thứ hai thành suppressed**. Cơ chế đó có trong các hoàn cảnh cụ thể như `try-with-resources`, hoặc do lập trình viên chủ động gọi `addSuppressed()`. Một `try/finally` tự viết có thể làm ngoại lệ từ `finally` che lỗi trước đó nếu không xử lý đúng.

## 4. `enableSuppression=false`: `addSuppressed()` không ghi nhận lỗi

Khi một đối tượng `Throwable` được xây dựng với `enableSuppression=false`:

- Danh sách suppressed của **đối tượng đó** bị vô hiệu hóa.
- Gọi `getSuppressed()` trả về **mảng rỗng**.
- Lời gọi `addSuppressed(validThrowable)` không lưu ngoại lệ mới vào đối tượng đó.
- Việc tắt suppression **không** làm mất `message`, `cause`, hoặc tự động vô hiệu hóa stack trace.
- Đối tượng vẫn có thể được `throw`, được `catch`, được dùng làm `cause` của đối tượng khác và được in/log như ngoại lệ.

Ví dụ:

~~~java
// Đoạn minh họa trong một phương thức, ConfigurableFailure đã định nghĩa.
ConfigurableFailure failure =
        new ConfigurableFailure("không xử lý được", null, false, true);

failure.addSuppressed(new java.io.IOException("đóng tệp lỗi"));
System.out.println(failure.getSuppressed().length); // 0
~~~

Điều đáng lo nhất xuất hiện với `try-with-resources`: nếu **lỗi chính bị ném trong body là một đối tượng tắt suppression**, Java vẫn có thể gọi `addSuppressed()` để cố gắn lỗi đóng tài nguyên, nhưng thông tin này sẽ **không được lưu trên đối tượng chính**.

Vì vậy, dù tắt suppression có thể có chủ đích trong một lớp ngoại lệ đặc biệt, **không nên tắt nó cho các lỗi dùng trong code cần truy vết thất bại khi đóng tài nguyên**.

Quy tắc API vẫn tồn tại: `addSuppressed(null)` không trở thành một thao tác hợp lệ, và cố tự suppress chính đối tượng cũng là sử dụng sai; đừng xem cờ `false` là cách bỏ qua mọi quy tắc kiểm tra đối số. Trường hợp cụ thể nên kiểm tra theo hợp đồng `Throwable.addSuppressed`.

## 5. `writableStackTrace=true`: stack trace được thu thập theo cách thông thường

Với `writableStackTrace=true`, constructor của `Throwable` thường thu thập stack trace tại thời điểm xây dựng. Đây là các frame cho biết đường gọi gần điểm tạo ngoại lệ, đặc biệt hữu ích khi tìm vị trí lỗi.

~~~java
// Đoạn minh họa khi đối tượng đã tạo.
StackTraceElement[] frames = failure.getStackTrace();
for (StackTraceElement frame : frames) {
    System.out.println(frame.getClassName() + "." + frame.getMethodName());
}
~~~

Các khía cạnh quan trọng:

- `getStackTrace()` trả một mảng `StackTraceElement`; vị trí đầu thường ứng với điểm stack trace được điền.
- `printStackTrace()` có thể hiển thị stack trace cùng thông tin `cause` và `suppressed`.
- `fillInStackTrace()` có thể cập nhật vị trí stack trace theo trạng thái stack hiện tại, khi ghi stack trace được cho phép.
- `setStackTrace(StackTraceElement[])` cho phép đặt một stack trace được cung cấp hợp lệ theo hợp đồng API.
- Việc `throw ex;` một đối tượng đã tồn tại **không tự động đồng nghĩa với chụp stack trace mới** ở dòng `throw`.

Mặc dù stack trace thường có ít nhất một frame đối với ngoại lệ ứng dụng thông thường, Java cho phép các tình huống đặc biệt mà VM hoặc một lớp `Throwable` tùy chỉnh không cung cấp đầy đủ frame. Vì vậy không nên viết ứng dụng phụ thuộc vào vị trí cố định của một frame mà không kiểm tra mảng.

## 6. `writableStackTrace=false`: đối tượng không ghi stack trace

Khi `writableStackTrace=false`, constructor bốn tham số không thu thập stack trace thông thường cho đối tượng; stack trace được để **`null` ở trạng thái nội bộ**. Ở giao diện API công khai:

- `getStackTrace()` trả **mảng rỗng**.
- `fillInStackTrace()` **không cập nhật** stack trace và trả về chính `Throwable` đó.
- `setStackTrace(...)` **không thay đổi** stack trace của đối tượng.
- `printStackTrace()` có thể vẫn in tên lớp và thông điệp, cũng như các nhánh `cause` hoặc `suppressed` nếu có, nhưng không có những frame stack trace thông thường của đối tượng đã bị tắt.
- `throw`/`catch` đối tượng vẫn hoạt động bình thường.
- Nếu `enableSuppression=true`, bạn vẫn có thể lưu các ngoại lệ phụ với **stack trace riêng của chính chúng**, dù đối tượng chính không có frame.

Ví dụ:

~~~java
// Đoạn minh họa trong phương thức, ConfigurableFailure đã định nghĩa.
ConfigurableFailure failure =
        new ConfigurableFailure("không lưu vị trí", null, true, false);

failure.fillInStackTrace();
failure.setStackTrace(new StackTraceElement[] {
        new StackTraceElement("Demo", "run", "Demo.java", 42)
});
System.out.println(failure.getStackTrace().length); // 0
~~~

Không nên nhầm với `setStackTrace(new StackTraceElement[0])` ở đối tượng có `writableStackTrace=true`: hai tình huống có thể cùng cho mảng rỗng hiện tại, nhưng **cấu hình ghi stack trace khác nhau**. Đối tượng bị vô hiệu hóa qua constructor không trở lại trạng thái ghi được chỉ bằng gọi `setStackTrace` hoặc `fillInStackTrace`.

Vì không còn frame, việc xác định nơi tạo hoặc phát sinh lỗi có thể trở nên khó khăn. Chỉ chọn cấu hình này khi bạn có lý do rõ ràng và thông tin chẩn đoán khác đủ dùng.

## 7. Hai cờ độc lập: bốn tổ hợp

| `enableSuppression` | `writableStackTrace` | `addSuppressed()` | `getStackTrace()` |
| --- | --- | --- | --- |
| `true` | `true` | Ghi nhận lỗi phụ | Ghi/đọc stack trace thông thường |
| `true` | `false` | Vẫn ghi nhận lỗi phụ | Không có các frame của đối tượng chính |
| `false` | `true` | Không ghi nhận lỗi phụ | Vẫn có stack trace thông thường |
| `false` | `false` | Không ghi nhận lỗi phụ | Không có stack trace của đối tượng chính |

Không có quy tắc “tắt một cờ bắt buộc tắt cờ còn lại”. Nếu chỉ muốn bớt chi phí ghi stack trace của một loại ngoại lệ đặc biệt, **không bắt buộc tắt suppression**; việc tắt suppression riêng chỉ nên làm khi thật sự chấp nhận mất khả năng đính kèm lỗi phụ.

Cả hai cờ là cấu hình **khi khởi tạo**. API công khai không có một setter đơn giản tên `setEnableSuppression(boolean)` hoặc `setWritableStackTrace(boolean)` để bật lại tùy ý trên một đối tượng đã tạo.

## 8. Ví dụ Java 17+ hoàn chỉnh: thử cả bốn tổ hợp

Lưu tệp `ThrowableFlagsDemo.java`:

~~~java
import java.io.IOException;

public class ThrowableFlagsDemo {
    static class ConfigurableFailure extends RuntimeException {
        ConfigurableFailure(String message, Throwable cause,
                            boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }

    static void show(String name, boolean suppress, boolean trace) {
        ConfigurableFailure ex = new ConfigurableFailure(name, null, suppress, trace);
        ex.addSuppressed(new IOException("đóng tài nguyên"));
        System.out.println(name + ": suppressed=" + ex.getSuppressed().length
                + ", stackAvailable=" + (ex.getStackTrace().length > 0));
    }

    public static void main(String[] args) {
        show("true/true", true, true);
        show("true/false", true, false);
        show("false/true", false, true);
        show("false/false", false, false);

        Throwable root = new IllegalArgumentException("lỗi gốc");
        ConfigurableFailure minimal = new ConfigurableFailure("thất bại", root, false, false);
        minimal.fillInStackTrace();
        minimal.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("Demo", "work", "Demo.java", 5)
        });
        System.out.println("cause=" + minimal.getCause().getMessage());
        System.out.println("stack sau cập nhật=" + minimal.getStackTrace().length);
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 ThrowableFlagsDemo.java
java ThrowableFlagsDemo
~~~

**Đầu ra đã kiểm chứng trên Java 17+ đối với chương trình này:**

~~~text
true/true: suppressed=1, stackAvailable=true
true/false: suppressed=1, stackAvailable=false
false/true: suppressed=0, stackAvailable=true
false/false: suppressed=0, stackAvailable=false
cause=lỗi gốc
stack sau cập nhật=0
~~~

Diễn giải:

1. `show(...)` tạo từng ngoại lệ với bộ cờ riêng. Tên `true/false` ở đầu dòng theo thứ tự **suppression/stack trace**, không phải giá trị của cause hay lỗi được ném.
2. Với suppression bật, một `IOException` phụ được thêm nên `getSuppressed().length == 1`. Với suppression tắt, lời gọi thêm không lưu nên độ dài bằng `0`.
3. Với writable stack trace bật, đối tượng `ConfigurableFailure` được tạo trong một chuỗi lời gọi có stack trace khả dụng; với cờ tắt, `getStackTrace()` trả mảng rỗng.
4. `minimal` là đối tượng bị tắt cả hai tính năng, nhưng vẫn mang cause `IllegalArgumentException("lỗi gốc")`, vì cấu hình này **không tắt quan hệ nguyên nhân**.
5. Các lời gọi `fillInStackTrace()` và `setStackTrace(...)` không biến `minimal` thành đối tượng có stack trace ghi được. Vì thế dòng cuối là `0`.

Dùng `ex.getStackTrace().length > 0` để minh họa khả năng stack trace trong chương trình chạy thông thường; không nên tổng quát hóa rằng mọi đối tượng Java trong mọi hoàn cảnh và mọi JVM bắt buộc có số frame dương.

## 9. Ứng dụng thực tế: lỗi đóng tài nguyên bị mất khỏi `getSuppressed()`

Đây là khác biệt có ảnh hưởng nhất đến việc xử lý lỗi. `try-with-resources` có cơ chế ưu tiên ngoại lệ từ body hoặc initializer làm **lỗi chính**, đồng thời cố giữ lỗi phát sinh khi đóng tài nguyên dưới dạng suppressed.

Nếu chính ngoại lệ chính được tạo với `enableSuppression=false`, quá trình tự động thêm suppressed vẫn có thể được gọi theo ngữ nghĩa của Java, nhưng **không có lỗi phụ nào được lưu lại trên ngoại lệ đó**.

Tệp `SuppressedFlagsDemo.java`:

~~~java
import java.io.IOException;

public class SuppressedFlagsDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            System.out.println("Đóng tài nguyên");
            throw new IOException("lỗi đóng");
        }
    }

    static class TaskFailure extends Exception {
        TaskFailure(boolean suppression) {
            super("lỗi tác vụ", null, suppression, true);
        }
    }

    static void run(boolean suppression) {
        try (Resource resource = new Resource()) {
            throw new TaskFailure(suppression);
        } catch (Exception ex) {
            System.out.println("Lỗi chính: " + ex.getMessage());
            System.out.println("Suppressed: " + ex.getSuppressed().length);
        }
    }

    public static void main(String[] args) {
        System.out.println("Suppression bật");
        run(true);
        System.out.println("Suppression tắt");
        run(false);
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 SuppressedFlagsDemo.java
java SuppressedFlagsDemo
~~~

Stdout chính xác:

~~~text
Suppression bật
Đóng tài nguyên
Lỗi chính: lỗi tác vụ
Suppressed: 1
Suppression tắt
Đóng tài nguyên
Lỗi chính: lỗi tác vụ
Suppressed: 0
~~~

Điểm cần quan sát:

- `TaskFailure` là **checked exception** vì kế thừa `Exception`, nhưng phương thức `run` bắt nó qua `catch (Exception)`; không cần khai báo `throws` để lỗi truyền ra ngoài.
- Cả hai lượt đều chạy `close()`, và `close()` đều ném `IOException`.
- Lượt suppression bật giữ lỗi đóng trong `getSuppressed()`; lượt suppression tắt giữ lỗi `TaskFailure` làm chính nhưng **không giữ thông tin `IOException` phụ** trong danh sách.
- `writableStackTrace` được truyền `true` trong cả hai lượt; kết quả khác nhau là do cờ suppression, không phải stack trace.

Điều này giải thích vì sao **không nên tắt suppression cho exception thông thường của nghiệp vụ hoặc I/O**. Khi dữ liệu chẩn đoán từ quá trình đóng tài nguyên có thể rất quan trọng, việc tắt nó có thể làm điều tra sự cố khó hơn đáng kể.

## 10. `cause` độc lập với cả hai cờ

Constructor bốn tham số nhận **`cause` riêng**. Tắt suppression và writable stack trace của ngoại lệ bao ngoài **không xóa** cause đã được truyền vào:

~~~java
// Đoạn minh họa, ConfigurableFailure đã khai báo.
Throwable root = new java.io.IOException("đọc cấu hình lỗi");
ConfigurableFailure wrapper =
        new ConfigurableFailure("khởi động thất bại", root, false, false);

System.out.println(wrapper.getMessage());      // khởi động thất bại
System.out.println(wrapper.getCause() == root); // true
~~~

Đối tượng `root` có stack trace riêng theo cách nó được tạo. Do đó một ngoại lệ bọc bị tắt stack trace **vẫn có thể chứa một cause có stack trace**; việc in chuỗi nguyên nhân có thể cung cấp dấu vết lỗi gốc. Tuy nhiên, không nên mặc định luôn có cause hoặc nó luôn đủ thông tin: `getCause()` có thể là `null`.

Hãy phân biệt hai công việc khi debug:

- **Cause:** tìm *vì sao một ngoại lệ này được tạo từ một ngoại lệ trước*.
- **Suppressed:** tìm *những ngoại lệ bổ sung xảy ra trong quá trình xử lý/dọn dẹp*.

Một đối tượng có thể có cause nhưng không có suppressed, có suppressed nhưng không có cause, có cả hai, hoặc không có cả hai. Stack trace của đối tượng chính là một kênh thông tin thứ ba, tách biệt.

Có một điểm triển khai constructor cần biết: truyền trực tiếp `cause` vào constructor bốn tham số (kể cả `null`) là cách **khởi tạo trạng thái cause**. Không nên dự định gọi `initCause()` sau đó trên cùng đối tượng chỉ để thay đổi nguyên nhân; `initCause()` được thiết kế cho các ngoại lệ chưa khởi tạo cause. Nếu cần cause, hãy xác định và truyền vào constructor theo thiết kế hợp đồng.

## 11. Cờ cấu hình thuộc `Throwable` hiện tại, không lan truyền tự động

Giả sử:

~~~java
// Đoạn minh họa với ConfigurableFailure đã định nghĩa.
Throwable root = new IllegalArgumentException("đầu vào sai");
ConfigurableFailure wrapper =
        new ConfigurableFailure("không hoàn thành", root, false, false);
~~~

Cờ suppression/trace ở `wrapper` áp dụng trên **wrapper**. Nó **không thay đổi** các đặc tính hay stack trace của `root`. Nếu `root` có suppressed exceptions, chúng vẫn gắn với `root`; nếu wrapper tắt suppression, chỉ danh sách suppressed của wrapper không ghi nhận thêm.

Tương tự:

~~~java
// Minh họa trường hợp wrapper giữ được lỗi phụ dù stack trace wrapper đã tắt.
ConfigurableFailure wrapper =
        new ConfigurableFailure("thất bại", null, true, false);
wrapper.addSuppressed(new IllegalStateException("lỗi phụ"));
~~~

Đối tượng wrapper không có frame stack trace của nó, nhưng `getSuppressed()` vẫn có thể trả về ngoại lệ phụ `IllegalStateException`, và ngoại lệ phụ có thể có stack trace riêng.

Hai cờ **không được truyền theo quan hệ kế thừa động** tới mọi ngoại lệ được tạo về sau; mỗi đối tượng có trạng thái tùy theo constructor và cách nó được tạo. Đây là điểm quan trọng khi đọc `printStackTrace()` của một chuỗi nhiều tầng cause/suppressed.

## 12. `printStackTrace()` thể hiện sự khác biệt thế nào?

Với một `Throwable` thường được tạo có stack trace ghi được, `printStackTrace()` thường in thông tin như:

~~~text
com.example.Failure: thao tác lỗi
    at com.example.Service.run(Service.java:25)
    at com.example.Main.main(Main.java:10)
    Suppressed: java.io.IOException: lỗi đóng
        at ...
Caused by: java.lang.IllegalArgumentException: đầu vào sai
    at ...
~~~

Đây là **sơ đồ minh họa, không phải stdout cố định**: tên lớp, đường dẫn, số dòng và bố cục rút gọn khác nhau theo code/JDK và độ dài stack trace.

Khi `writableStackTrace=false`, phần **frame của ngoại lệ chính** thường không có; nhưng thông điệp vẫn có thể xuất hiện. Khi `enableSuppression=false`, những suppressed exceptions được thêm qua `addSuppressed()` không xuất hiện từ đối tượng chính. Nếu có cause, stack trace của cause vẫn có thể được trình bày.

Việc thiếu frame không đồng nghĩa với việc ngoại lệ không được ném hoặc không có lỗi. Đó có thể là **quyết định cấu hình**, nên đừng kết luận “không có stack trace tức là không có call stack”.

## 13. Khi nào nên cân nhắc tắt `writableStackTrace`?

Các constructor `Throwable` thông thường thu thập stack trace và quá trình này có thể có chi phí. Trong một số hệ thống rất đặc thù, người phát triển có thể cân nhắc một loại ngoại lệ chỉ đóng vai trò **tín hiệu luồng điều khiển nội bộ**, được ném/bắt rất thường xuyên, vị trí ném đã biết rõ và không cần thông tin call stack cho từng lượt.

Nhưng đây là lựa chọn **đánh đổi**, không phải mặc định nên áp dụng:

1. Cần đo bằng **benchmark/profile của chính ứng dụng**, không giả định mọi lần tắt trace đều tăng hiệu năng đáng kể ở mức hệ thống.
2. Xác định mất stack trace có làm quá trình điều tra production khó hơn không. Các lỗi hiếm, khó tái hiện thường cần stack trace.
3. Xem liệu đang dùng ngoại lệ cho một trường hợp luồng điều khiển thông thường hay không; nếu có, **trả về giá trị hoặc kiểu kết quả phù hợp** thường rõ nghĩa hơn việc dùng exception thường xuyên.
4. Nếu phải tối ưu một ngoại lệ chuyên dụng, cân nhắc vẫn bật suppression khi code có thể đi qua `try-with-resources` hoặc cơ chế cần giữ lỗi phụ.
5. Đánh giá logging, metrics và observability. Một số hệ thống coi stack trace là dữ liệu bắt buộc để phân nhóm sự cố.

**Không nên dùng `writableStackTrace=false` như một cách giấu thông tin nhạy cảm.** Việc bảo mật dữ liệu phải dựa vào thiết kế thông điệp, chính sách log, kiểm soát truy cập và lọc thông tin. Một đối tượng lỗi thiếu trace có thể vẫn chứa `message`, `cause` hoặc dữ liệu khác cần bảo vệ.

## 14. Khi nào nên hoặc không nên tắt suppression?

Tắt suppression chỉ thích hợp khi một lớp ngoại lệ **được thiết kế đặc biệt** và người viết có thể khẳng định việc lưu các lỗi phụ không có giá trị hay sẽ không dùng đến. Đây là trường hợp hẹp.

Lý do nên giữ mặc định `true` trong phần lớn code:

- `try-with-resources` dựa vào suppressed exceptions để không mất lỗi đóng tài nguyên khi đã có lỗi chính.
- Một số chiến lược cleanup/audit thủ công cũng dùng `addSuppressed()` để giữ lỗi phụ.
- Công cụ ghi log có thể in suppressed giúp truy ra tất cả bước thất bại.
- Nếu tắt suppression, những lời gọi `addSuppressed()` hợp lệ **không lưu dữ liệu**, dễ khiến code xử lý tưởng đã bảo toàn lỗi trong khi thực tế không có.
- Việc tắt suppression trên một đối tượng không có nghĩa các đối tượng khác sẽ tự tắt; nên tránh trạng thái hỗn hợp khó lý giải nếu chưa có hợp đồng thiết kế.

Nhìn chung, chi phí tiềm năng của việc mất thông tin chẩn đoán thường quan trọng hơn lợi ích giả định khi tắt suppression đại trà. Với exception nghiệp vụ, I/O, cơ sở dữ liệu và thao tác phân tán, nên ưu tiên traceability và khả năng bảo toàn nguyên nhân.

## 15. Các bẫy API và hiểu nhầm phổ biến

| Nhầm lẫn / cách dùng sai | Thực tế và cách sửa |
| --- | --- |
| `enableSuppression=false` có nghĩa ngoại lệ không thể bị `throw` | Sai. Ngoại lệ vẫn được ném/bắt theo kiểu của nó. Chỉ tính năng lưu lỗi phụ bị tắt. |
| `writableStackTrace=false` xóa cả message và cause | Sai. Message/cause vẫn độc lập với stack trace của đối tượng đó. |
| Tắt suppression sẽ làm `try-with-resources` không gọi `close()` | Sai. Tài nguyên vẫn được đóng; chỉ việc ghi nhận lỗi đóng như suppressed trên lỗi chính bị ảnh hưởng. |
| `getSuppressed().length == 0` chứng minh không có lỗi đóng | Không chắc. Suppression có thể đã bị tắt, hoặc đơn giản chưa có lỗi phụ. |
| `getStackTrace().length == 0` chứng minh lỗi không hề phát sinh | Sai. Có thể stack trace đã bị tắt hoặc bị thay đổi; kiểm tra loại lỗi và bối cảnh. |
| `fillInStackTrace()` tự bật lại `writableStackTrace` | Sai. Với cờ `false`, nó không cập nhật stack trace của đối tượng. |
| `setStackTrace(...)` luôn làm đối tượng có stack frame | Sai. Đối tượng được tạo với writable trace bị tắt sẽ không nhận thay đổi. |
| Truyền `cause=null` nghĩa là constructor chưa thiết lập cause | Constructor nhận cause trực tiếp khởi tạo trạng thái cause. Đừng dự tính thay bằng `initCause()` sau đó. |
| Tắt cờ trên wrapper thì cause cũng mất trace/suppressed | Sai. Cờ thuộc từng `Throwable` riêng, không thay đổi đối tượng cause. |
| Gọi `new Throwable(message, cause, false, false)` ở bất kỳ mã client nào | Constructor bốn tham số là `protected`; cần dùng trong lớp con/ngữ cảnh được phép. |
| Dùng cả hai cờ `false` cho mọi lỗi để tăng tốc | Làm mất dữ liệu quan trọng; chỉ tối ưu khi có profile, hợp đồng rõ và chấp nhận đánh đổi. |
| Ghi log mỗi `getMessage()` là đủ | Có thể mất cause, suppressed và stack trace; ưu tiên ghi nguyên đối tượng `Throwable` tại ranh giới phù hợp. |

## 16. Tự kiểm tra kiến thức

**Câu 1.** `enableSuppression=false` có làm `getCause()` trả `null` không?

**Đáp án:** Không. Cause là thông tin độc lập; nếu truyền một cause khác `null` vào constructor, `getCause()` vẫn trả tham chiếu đó.

**Câu 2.** Ngoại lệ bị tắt stack trace có được ném lại bằng `throw ex;` không?

**Đáp án:** Có. `writableStackTrace=false` không thay đổi việc ngoại lệ có thể được ném/bắt.

**Câu 3.** `Throwable` được tạo với `writableStackTrace=false` trả về gì từ `getStackTrace()`?

**Đáp án:** Mảng rỗng, đối với cơ chế constructor bốn tham số được mô tả.

**Câu 4.** Có thể dùng `setStackTrace()` để bật lại việc ghi stack trace đã tắt bằng constructor không?

**Đáp án:** Không. Lời gọi không khôi phục tính năng ghi stack trace của đối tượng đó.

**Câu 5.** Nếu `try-with-resources` có lỗi body và lỗi đóng, nhưng lỗi body tắt suppression, chuyện gì xảy ra?

**Đáp án:** Lỗi body vẫn là ngoại lệ chính; lỗi đóng sẽ không được lưu trong `getSuppressed()` của nó theo cơ chế suppression thông thường.

**Câu 6.** Hai cờ có luôn phải nhận cùng giá trị không?

**Đáp án:** Không. Bốn tổ hợp `true/true`, `true/false`, `false/true`, `false/false` đều có ý nghĩa hợp lệ.

**Câu 7.** Tắt stack trace của wrapper có xóa stack trace của cause không?

**Đáp án:** Không. Cause là đối tượng khác, có trạng thái riêng.

**Câu 8.** Chữ ký bốn tham số trực tiếp của `Throwable` có thể được gọi từ mọi lớp code thông thường không?

**Đáp án:** Không; constructor đó là `protected`. Lớp ngoại lệ con có thể gọi `super(message, cause, enableSuppression, writableStackTrace)`.

**Câu 9.** Nếu một loại exception thường dùng để báo lỗi I/O, có nên mặc định tắt suppression không?

**Đáp án:** Không. Suppressed exception có thể là nơi duy nhất ghi lại lỗi đóng tài nguyên xảy ra cùng lỗi chính.

**Câu 10.** Vì sao hai cờ không phải giải pháp hiệu năng mặc định?

**Đáp án:** Vì chúng có thể làm mất thông tin chẩn đoán, còn lợi ích hiệu năng phụ thuộc cách dùng và cần đo thực tế.

## 17. Tài liệu tham khảo chính thức

- [Java SE 17 API — `Throwable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): constructor bốn tham số, `getCause()`, `addSuppressed()`, `getSuppressed()`, `getStackTrace()`, `setStackTrace()` và `fillInStackTrace()`.
- [Java SE 17 API — `Exception`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Exception.html): constructor `protected` cho checked exception tùy chỉnh với hai cờ.
- [Java SE 17 API — `RuntimeException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/RuntimeException.html): constructor `protected` với suppression và writable stack trace.
- [Java Language Specification 17 — §14.20.3, `try-with-resources`](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3): đóng tài nguyên và cách lưu ngoại lệ phụ khi có lỗi chính.
- [Java Language Specification 17 — §11, Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html): ngoại lệ, lan truyền và xử lý theo ngữ nghĩa Java.

**Kết luận:** `enableSuppression` kiểm soát **lưu lỗi phụ**, còn `writableStackTrace` kiểm soát **ghi stack trace** của chính `Throwable`. Chúng được cấu hình khi tạo đối tượng, không thay đổi khả năng ném/bắt và không tự xóa cause. Phần lớn ứng dụng nên giữ thông tin chẩn đoán đầy đủ; chỉ tắt một hoặc cả hai cờ cho những lớp ngoại lệ chuyên biệt khi đã hiểu và chấp nhận rõ hậu quả.


</details>

- [Quay lại đầu trang](#back-to-top)
